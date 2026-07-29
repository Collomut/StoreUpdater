package com.stockmanager.db;

import com.stockmanager.model.Product;
import com.stockmanager.model.Sale;
import com.stockmanager.model.SaleItem;
import com.stockmanager.model.Shop;
import com.stockmanager.model.User;
import com.stockmanager.util.PasswordUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import com.stockmanager.util.DataCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DatabaseManager {

    private static DatabaseManager instance;
    private HikariDataSource dataSource;

    private DatabaseManager() {
        try {
            Properties props = loadConfig();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(props.getProperty("db.url"));
            config.setUsername(props.getProperty("db.user"));
            config.setPassword(props.getProperty("db.password"));
            config.setMinimumIdle(2);           // keep 2 warm connections always
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(15000);
            config.setIdleTimeout(60000);
            config.setMaxLifetime(300000);
            config.setKeepaliveTime(30000);
            config.setConnectionTestQuery("SELECT 1");
            config.setAutoCommit(true);
            dataSource = new HikariDataSource(config);
            try (Connection conn = getConn()) {
                createTables(conn);
                seedDefaultData(conn);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    private Connection getConn() throws SQLException {
        return dataSource.getConnection();
    }

    private Properties loadConfig() throws Exception {
        Properties props = new Properties();
        File external = new File(System.getProperty("user.dir"), "config.properties");
        if (external.exists()) {
            try (FileInputStream fis = new FileInputStream(external)) {
                props.load(fis); return props;
            }
        }
        try (InputStream is = getClass().getResourceAsStream("/config.properties")) {
            if (is != null) { props.load(is); return props; }
        }
        throw new RuntimeException("config.properties not found. Place it next to the JAR file.");
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    // ─── Schema ──────────────────────────────────────────────────────────────

    private void createTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS shops (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                description TEXT
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id SERIAL PRIMARY KEY,
                shop_id INTEGER NOT NULL REFERENCES shops(id),
                name VARCHAR(200) NOT NULL,
                category VARCHAR(100),
                sku VARCHAR(50),
                unit VARCHAR(50),
                quantity INTEGER NOT NULL DEFAULT 0,
                reorder_level INTEGER NOT NULL DEFAULT 5,
                cost_price DOUBLE PRECISION NOT NULL DEFAULT 0,
                selling_price DOUBLE PRECISION NOT NULL DEFAULT 0
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sales (
                id SERIAL PRIMARY KEY,
                shop_id INTEGER NOT NULL REFERENCES shops(id),
                sale_date DATE NOT NULL,
                total_amount DOUBLE PRECISION NOT NULL,
                receipt_number VARCHAR(50)
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sale_items (
                id SERIAL PRIMARY KEY,
                sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL REFERENCES products(id),
                quantity_sold INTEGER NOT NULL,
                unit_price DOUBLE PRECISION NOT NULL
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS settings (
                key VARCHAR(100) PRIMARY KEY,
                value TEXT NOT NULL
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                username VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'WORKER',
                shop_id INTEGER REFERENCES shops(id)
            )
        """);
    }

    private void seedDefaultData(Connection conn) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM shops");
        rs.next();
        if (rs.getInt(1) == 0) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO shops (name, description) VALUES (?, ?)");
            ps.setString(1, "Shoes Shop");        ps.setString(2, "Footwear & accessories");           ps.execute();
            ps.setString(1, "Curios Shop");       ps.setString(2, "Souvenirs & curio items");          ps.execute();
            ps.setString(1, "Beads & Hats Shop"); ps.setString(2, "Beads, hats & fashion accessories"); ps.execute();
        }
        setDefaultSetting(conn, "usd_rate", "1380");
        setDefaultSetting(conn, "receipt_counter", "1");

        ResultSet ur = conn.createStatement().executeQuery("SELECT COUNT(*) FROM users");
        ur.next();
        if (ur.getInt(1) == 0) {
            ResultSet shops = conn.createStatement().executeQuery("SELECT id FROM shops ORDER BY id");
            List<Integer> ids = new ArrayList<>();
            while (shops.next()) ids.add(shops.getInt(1));
            createUserIfAbsent(conn, "admin",  "admin123",  "ADMIN",  null);
            if (ids.size() >= 1) createUserIfAbsent(conn, "shop1", "shop1pass", "WORKER", ids.get(0));
            if (ids.size() >= 2) createUserIfAbsent(conn, "shop2", "shop2pass", "WORKER", ids.get(1));
            if (ids.size() >= 3) createUserIfAbsent(conn, "shop3", "shop3pass", "WORKER", ids.get(2));
        }
    }

    private void setDefaultSetting(Connection conn, String key, String value) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING");
        ps.setString(1, key); ps.setString(2, value); ps.execute();
    }

    private void createUserIfAbsent(Connection conn, String username, String password, String role, Integer shopId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO users (username, password_hash, role, shop_id) VALUES (?, ?, ?, ?) ON CONFLICT (username) DO NOTHING");
        ps.setString(1, username);
        ps.setString(2, PasswordUtil.hash(password));
        ps.setString(3, role);
        if (shopId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, shopId);
        ps.execute();
    }

    // ─── Settings ────────────────────────────────────────────────────────────

    public String getSetting(String key) {
        return DataCache.getSetting(key, () -> {
            try (Connection conn = getConn();
                 PreparedStatement ps = conn.prepareStatement("SELECT value FROM settings WHERE key=?")) {
                ps.setString(1, key);
                ResultSet rs = ps.executeQuery();
                return rs.next() ? rs.getString("value") : null;
            } catch (SQLException e) { return null; }
        });
    }

    public void setSetting(String key, String value) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value")) {
            ps.setString(1, key); ps.setString(2, value); ps.execute();
            DataCache.invalidateSetting(key);  // keep cache fresh
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ─── Users ───────────────────────────────────────────────────────────────

    public User authenticate(String username, String password) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT id, username, password_hash, role, shop_id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (PasswordUtil.verify(password, rs.getString("password_hash"))) {
                    User u = new User();
                    u.setId(rs.getInt("id")); u.setUsername(rs.getString("username"));
                    u.setRole(rs.getString("role"));
                    int sid = rs.getInt("shop_id");
                    u.setShopId(rs.wasNull() ? null : sid);
                    return u;
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        try (Connection conn = getConn();
             ResultSet rs = conn.createStatement()
                .executeQuery("SELECT id, username, role, shop_id FROM users ORDER BY role, username")) {
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id")); u.setUsername(rs.getString("username"));
                u.setRole(rs.getString("role"));
                int sid = rs.getInt("shop_id");
                u.setShopId(rs.wasNull() ? null : sid);
                list.add(u);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean addUser(String username, String password, String role, Integer shopId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, password_hash, role, shop_id) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, username); ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, role);
            if (shopId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, shopId);
            ps.execute(); return true;
        } catch (SQLException e) { return false; }
    }

    public boolean changePassword(int userId, String newPassword) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, PasswordUtil.hash(newPassword)); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean deleteUser(int userId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, userId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // ─── Shops ───────────────────────────────────────────────────────────────

    public List<Shop> getAllShops() {
        return DataCache.getShops(() -> {
            List<Shop> list = new ArrayList<>();
            try (Connection conn = getConn();
                 ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT id, name, description FROM shops ORDER BY id")) {
                while (rs.next())
                    list.add(new Shop(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        });
    }

    public boolean addShop(String name, String description) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO shops (name, description) VALUES (?, ?)")) {
            ps.setString(1, name); ps.setString(2, description); ps.execute();
            DataCache.invalidateShops(); return true;
        } catch (SQLException e) { return false; }
    }

    public boolean updateShop(int id, String name, String description) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE shops SET name=?, description=? WHERE id=?")) {
            ps.setString(1, name); ps.setString(2, description); ps.setInt(3, id);
            DataCache.invalidateShops();
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean deleteShop(int id) {
        if (shopHasData(id)) return false;   // guard against FK violations
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM shops WHERE id=?")) {
            ps.setInt(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    /** Force-deletes a shop including all its products and sales (cascade). */
    public boolean deleteShopCascade(int shopId) {
        try (Connection conn = getConn()) {
            conn.setAutoCommit(false);
            // Delete sale_items for all sales of this shop
            conn.createStatement().execute(
                "DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE shop_id = " + shopId + ")");
            // Delete sales
            PreparedStatement ps1 = conn.prepareStatement("DELETE FROM sales WHERE shop_id=?");
            ps1.setInt(1, shopId); ps1.execute();
            // Delete products
            PreparedStatement ps2 = conn.prepareStatement("DELETE FROM products WHERE shop_id=?");
            ps2.setInt(1, shopId); ps2.execute();
            // Delete shop
            PreparedStatement ps3 = conn.prepareStatement("DELETE FROM shops WHERE id=?");
            ps3.setInt(1, shopId); ps3.execute();
            conn.commit();
            conn.setAutoCommit(true);
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    /** Returns true if the shop still has products or sales */
    public boolean shopHasData(int shopId) {
        try (Connection conn = getConn()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM products WHERE shop_id=?) + " +
                "       (SELECT COUNT(*) FROM sales    WHERE shop_id=?) AS total");
            ps.setInt(1, shopId); ps.setInt(2, shopId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("total") > 0;
        } catch (SQLException e) { return true; } // safe default
    }

    // ─── Flat sale rows for unified history table ─────────────────────────────
    /** Single JOIN query — returns one row per sale item (date/receipt repeat per item) */
    public List<FlatSaleRow> getFlatSaleRows(int shopId, LocalDate from, LocalDate to) {
        List<FlatSaleRow> list = new ArrayList<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT s.sale_date, s.receipt_number, s.total_amount, " +
                "       p.name AS product_name, si.quantity_sold, si.unit_price " +
                "FROM sales s " +
                "JOIN sale_items si ON si.sale_id = s.id " +
                "JOIN products p   ON p.id = si.product_id " +
                "WHERE s.shop_id = ? AND s.sale_date BETWEEN ? AND ? " +
                "ORDER BY s.sale_date DESC, s.id, p.name")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new FlatSaleRow(
                    rs.getDate("sale_date").toLocalDate(),
                    rs.getString("receipt_number"),
                    rs.getDouble("total_amount"),
                    rs.getString("product_name"),
                    rs.getInt("quantity_sold"),
                    rs.getDouble("unit_price")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    /** DTO for one line in the unified history table */
    public static class FlatSaleRow {
        public final LocalDate date;
        public final String receipt;
        public final double saleTotal;
        public final String product;
        public final int qty;
        public final double unitPrice;
        public FlatSaleRow(LocalDate d, String r, double t, String p, int q, double u) {
            date=d; receipt=r; saleTotal=t; product=p; qty=q; unitPrice=u;
        }
        public double getSubtotal() { return qty * unitPrice; }
    }

    // ─── Multi-shop totals for admin overview ─────────────────────────────────
    public double[] getShopDayMonthTotals(int shopId) {
        try (Connection conn = getConn()) {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            PreparedStatement ps = conn.prepareStatement(
                "SELECT " +
                "  COALESCE(SUM(CASE WHEN sale_date = ? THEN total_amount ELSE 0 END), 0) AS day_total," +
                "  COALESCE(SUM(CASE WHEN sale_date >= ? THEN total_amount ELSE 0 END), 0) AS month_total " +
                "FROM sales WHERE shop_id = ?");
            ps.setDate(1, Date.valueOf(today));
            ps.setDate(2, Date.valueOf(monthStart));
            ps.setInt(3, shopId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new double[]{rs.getDouble("day_total"), rs.getDouble("month_total")};
        } catch (SQLException e) { e.printStackTrace(); }
        return new double[]{0, 0};
    }


    // ─── Products ────────────────────────────────────────────────────────────

    public List<Product> getProducts(int shopId) {
        return DataCache.getProducts(shopId, () -> {
            List<Product> list = new ArrayList<>();
            try (Connection conn = getConn();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM products WHERE shop_id=? ORDER BY name")) {
                ps.setInt(1, shopId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(mapProduct(rs));
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        });
    }

    public boolean addProduct(Product p) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO products (shop_id,name,category,sku,unit,quantity,reorder_level,cost_price,selling_price) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, p.getShopId()); ps.setString(2, p.getName());
            ps.setString(3, p.getCategory()); ps.setString(4, p.getSku());
            ps.setString(5, p.getUnit()); ps.setInt(6, p.getQuantity());
            ps.setInt(7, p.getReorderLevel()); ps.setDouble(8, p.getCostPrice());
            ps.setDouble(9, p.getSellingPrice());
            ps.execute();
            DataCache.invalidateProducts(p.getShopId());  // keep cache fresh
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean updateProduct(Product p) {
        // Safety net: if still carrying retired sentinel but qty is restored, unretire
        int reorderLevel = (p.getReorderLevel() < 0 && p.getQuantity() > 0)
            ? 5 : p.getReorderLevel();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET name=?,category=?,sku=?,unit=?,quantity=?,reorder_level=?,cost_price=?,selling_price=? WHERE id=?")) {
            ps.setString(1, p.getName()); ps.setString(2, p.getCategory());
            ps.setString(3, p.getSku()); ps.setString(4, p.getUnit());
            ps.setInt(5, p.getQuantity()); ps.setInt(6, reorderLevel);
            ps.setDouble(7, p.getCostPrice()); ps.setDouble(8, p.getSellingPrice());
            ps.setInt(9, p.getId());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateProducts(p.getShopId());
            return ok;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }


    public boolean deleteProduct(int id) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE id=?")) {
            ps.setInt(1, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateAllProducts();
            return ok;
        } catch (SQLException e) { return false; }
    }

    /** Returns true if any sale_items reference this product (FK would block deletion). */
    public boolean productHasSales(int productId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM sale_items WHERE product_id = ?")) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { return false; }
    }

    /** Retire a product: qty=0 and reorder_level=-1 (sentinel for "retired"). */
    public boolean retireProduct(int productId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET quantity = 0, reorder_level = -1 WHERE id = ?")) {
            ps.setInt(1, productId);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateAllProducts();
            return ok;
        } catch (SQLException e) { return false; }
    }

    /** Derived in-memory from product cache — zero extra DB round-trip */
    public int getLowStockCount(int shopId) {
        return (int) getProducts(shopId).stream().filter(Product::isLowStock).count();
    }

    /** Derived in-memory from product cache — zero extra DB round-trip */
    public List<Product> getLowStockProducts(int shopId) {
        return getProducts(shopId).stream()
            .filter(Product::isLowStock)
            .sorted(java.util.Comparator.comparingInt(Product::getQuantity))
            .collect(java.util.stream.Collectors.toList());
    }

    /** Derived in-memory from product cache — zero extra DB round-trip */
    public double getTotalStockValue(int shopId) {
        return getProducts(shopId).stream()
            .mapToDouble(p -> p.getQuantity() * p.getSellingPrice())
            .sum();
    }


    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id")); p.setShopId(rs.getInt("shop_id"));
        p.setName(rs.getString("name")); p.setCategory(rs.getString("category"));
        p.setSku(rs.getString("sku")); p.setUnit(rs.getString("unit"));
        p.setQuantity(rs.getInt("quantity")); p.setReorderLevel(rs.getInt("reorder_level"));
        p.setCostPrice(rs.getDouble("cost_price")); p.setSellingPrice(rs.getDouble("selling_price"));
        return p;
    }

    // ─── Sales ───────────────────────────────────────────────────────────────

    public int recordSale(Sale sale) {
        try (Connection conn = getConn()) {
            String counterStr = getSetting("receipt_counter");
            int counter = counterStr != null ? Integer.parseInt(counterStr) : 1;
            setSetting("receipt_counter", String.valueOf(counter + 1));
            String receiptNo = String.format("RCP-%04d", counter);

            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO sales (shop_id, sale_date, total_amount, receipt_number) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, sale.getShopId());
            ps.setDate(2, Date.valueOf(sale.getSaleDate()));
            ps.setDouble(3, sale.getTotalAmount());
            ps.setString(4, receiptNo);
            ps.execute();
            sale.setReceiptNumber(receiptNo);

            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) return -1;
            int saleId = keys.getInt(1);

            PreparedStatement iPs = conn.prepareStatement(
                "INSERT INTO sale_items (sale_id, product_id, quantity_sold, unit_price) VALUES (?,?,?,?)");
            for (SaleItem item : sale.getItems()) {
                iPs.setInt(1, saleId); iPs.setInt(2, item.getProductId());
                iPs.setInt(3, item.getQuantitySold()); iPs.setDouble(4, item.getUnitPrice());
                iPs.execute();
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE products SET quantity = quantity - ? WHERE id = ?");
                upd.setInt(1, item.getQuantitySold()); upd.setInt(2, item.getProductId());
                upd.execute();
            }
            return saleId;
        } catch (SQLException e) { e.printStackTrace(); return -1; }
    }

    public List<Sale> getSales(int shopId, LocalDate from, LocalDate to) {
        List<Sale> list = new ArrayList<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM sales WHERE shop_id=? AND sale_date BETWEEN ? AND ? ORDER BY sale_date DESC")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Sale s = new Sale();
                s.setId(rs.getInt("id")); s.setShopId(rs.getInt("shop_id"));
                s.setSaleDate(rs.getDate("sale_date").toLocalDate());
                s.setTotalAmount(rs.getDouble("total_amount"));
                s.setReceiptNumber(rs.getString("receipt_number"));
                list.add(s);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<SaleItem> getSaleItems(int saleId) {
        List<SaleItem> list = new ArrayList<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT si.*, p.name as product_name FROM sale_items si " +
                "JOIN products p ON si.product_id = p.id WHERE si.sale_id=?")) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SaleItem si = new SaleItem();
                si.setId(rs.getInt("id")); si.setSaleId(saleId);
                si.setProductId(rs.getInt("product_id"));
                si.setQuantitySold(rs.getInt("quantity_sold"));
                si.setUnitPrice(rs.getDouble("unit_price"));
                si.setProductName(rs.getString("product_name"));
                list.add(si);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public double getTotalSales(int shopId, LocalDate from, LocalDate to) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE shop_id=? AND sale_date BETWEEN ? AND ?")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    public double getDailySales(int shopId, LocalDate date) { return getTotalSales(shopId, date, date); }

    public double getMonthlySales(int shopId, int year, int month) {
        return getTotalSales(shopId, LocalDate.of(year, month, 1), YearMonth.of(year, month).atEndOfMonth());
    }

    public double getAnnualSales(int shopId, int year) {
        return getTotalSales(shopId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public List<Object[]> getTopProducts(int shopId, LocalDate from, LocalDate to, int limit) {
        List<Object[]> list = new ArrayList<>();
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT p.name, SUM(si.quantity_sold * si.unit_price) as revenue " +
                "FROM sale_items si JOIN products p ON si.product_id=p.id " +
                "JOIN sales s ON si.sale_id=s.id " +
                "WHERE s.shop_id=? AND s.sale_date BETWEEN ? AND ? " +
                "GROUP BY p.name ORDER BY revenue DESC LIMIT ?")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to)); ps.setInt(4, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new Object[]{rs.getString(1), rs.getDouble(2)});
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public String getSaleReceiptNumber(int saleId) {
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT receipt_number FROM sales WHERE id=?")) {
            ps.setInt(1, saleId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : "";
        } catch (SQLException e) { return ""; }
    }

    // ─── Backward-compatible aliases for existing controllers ────────────────

    public List<Product> getProductsByShop(int shopId) { return getProducts(shopId); }

    public double getTodaySales(int shopId) { return getDailySales(shopId, LocalDate.now()); }

    public List<Sale> getSalesByShop(int shopId, LocalDate from, LocalDate to) {
        return getSales(shopId, from, to);
    }

    public int saveSale(Sale sale) { return recordSale(sale); }

    // ─── Batch dashboard stats (7 queries → 2) ───────────────────────────────

    /**
     * Returns all dashboard numbers in exactly 2 round-trips:
     *  result[0] = today's sales
     *  result[1] = week's sales
     *  result[2] = month's sales
     *  result[3] = stock value
     *  result[4] = low stock count  (as double, cast to int)
     */
    public double[] getDashboardStats(int shopId) {
        double[] r = new double[5];
        try (Connection conn = getConn()) {
            // Query 1: sales aggregation (today + week + month in one pass)
            PreparedStatement ps1 = conn.prepareStatement(
                "SELECT " +
                "  COALESCE(SUM(CASE WHEN sale_date = CURRENT_DATE THEN total_amount END), 0)              AS today_sales," +
                "  COALESCE(SUM(CASE WHEN sale_date >= date_trunc('week',  CURRENT_DATE) THEN total_amount END), 0) AS week_sales," +
                "  COALESCE(SUM(CASE WHEN sale_date >= date_trunc('month', CURRENT_DATE) THEN total_amount END), 0) AS month_sales " +
                "FROM sales WHERE shop_id = ?");
            ps1.setInt(1, shopId);
            ResultSet rs1 = ps1.executeQuery();
            if (rs1.next()) {
                r[0] = rs1.getDouble("today_sales");
                r[1] = rs1.getDouble("week_sales");
                r[2] = rs1.getDouble("month_sales");
            }

            // Query 2: product aggregation (stock value + low stock count in one pass)
            PreparedStatement ps2 = conn.prepareStatement(
                "SELECT " +
                "  COALESCE(SUM(quantity * selling_price), 0)               AS stock_value," +
                "  COUNT(CASE WHEN quantity < 5 THEN 1 END)                 AS low_count " +
                "FROM products WHERE shop_id = ?");
            ps2.setInt(1, shopId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                r[3] = rs2.getDouble("stock_value");
                r[4] = rs2.getDouble("low_count");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return r;
    }

    // ─── Batch overview stats (12+ queries → 1) ──────────────────────────────

    /**
     * Single query across all shops — returns one OverviewRow per shop.
     */
    public List<OverviewRow> getOverviewStats() {
        List<OverviewRow> list = new ArrayList<>();
        try (Connection conn = getConn();
             ResultSet rs = conn.createStatement().executeQuery(
                "SELECT sh.id, sh.name," +
                "  COALESCE(SUM(CASE WHEN s.sale_date = CURRENT_DATE THEN s.total_amount END), 0)               AS today_sales," +
                "  COALESCE(SUM(CASE WHEN s.sale_date >= date_trunc('month', CURRENT_DATE) THEN s.total_amount END), 0) AS month_sales," +
                "  COALESCE((SELECT SUM(p.quantity * p.selling_price) FROM products p WHERE p.shop_id = sh.id), 0) AS stock_value," +
                "  COALESCE((SELECT COUNT(*) FROM products p WHERE p.shop_id = sh.id AND p.quantity < 5), 0)    AS low_stock," +
                "  COALESCE((SELECT COUNT(*) FROM products p WHERE p.shop_id = sh.id), 0)                       AS total_products " +
                "FROM shops sh " +
                "LEFT JOIN sales s ON s.shop_id = sh.id " +
                "GROUP BY sh.id, sh.name ORDER BY sh.id")) {
            while (rs.next()) {
                list.add(new OverviewRow(
                    rs.getString("name"),
                    rs.getDouble("today_sales"),
                    rs.getDouble("month_sales"),
                    rs.getDouble("stock_value"),
                    rs.getInt("low_stock"),
                    rs.getInt("total_products")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public static class OverviewRow {
        public final String name;
        public final double today, month, stockValue;
        public final int lowStock, products;
        public OverviewRow(String n, double t, double m, double sv, int ls, int p) {
            name=n; today=t; month=m; stockValue=sv; lowStock=ls; products=p;
        }
    }
}


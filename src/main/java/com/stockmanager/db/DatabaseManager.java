package com.stockmanager.db;

import com.stockmanager.model.Product;
import com.stockmanager.model.Sale;
import com.stockmanager.model.SaleItem;
import com.stockmanager.model.Shop;
import com.stockmanager.model.User;
import com.stockmanager.util.PasswordUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class DatabaseManager {

    private static DatabaseManager instance;
    private HikariDataSource dataSource;

    private final SettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;

    private DatabaseManager() {
        try {
            Properties props = loadConfig();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(props.getProperty("db.url"));
            config.setUsername(props.getProperty("db.user"));
            config.setPassword(props.getProperty("db.password"));
            config.setMinimumIdle(2);
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(15000);
            config.setIdleTimeout(60000);
            config.setMaxLifetime(300000);
            config.setKeepaliveTime(30000);
            config.setConnectionTestQuery("SELECT 1");
            config.setAutoCommit(true);
            dataSource = new HikariDataSource(config);

            // Instantiate repositories
            settingsRepository = new SettingsRepository(this);
            userRepository = new UserRepository(this);
            shopRepository = new ShopRepository(this);
            productRepository = new ProductRepository(this);
            saleRepository = new SaleRepository(this);

            try (Connection conn = getConn()) {
                createTables(conn);
                seedDefaultData(conn);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to database: " + e.getMessage(), e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    Connection getConn() throws SQLException {
        return dataSource.getConnection();
    }

    private Properties loadConfig() throws Exception {
        Properties props = new Properties();
        File external = null;
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null) {
            external = new File(new File(appPath).getParent(), "config.properties");
        } else {
            external = new File(System.getProperty("user.dir"), "config.properties");
        }

        if (external != null && external.exists()) {
            try (FileInputStream fis = new FileInputStream(external)) {
                props.load(fis); return props;
            }
        }
        try (InputStream is = getClass().getResourceAsStream("/config.properties")) {
            if (is != null) { props.load(is); return props; }
        }
        throw new RuntimeException("config.properties not found. Place it next to the JAR/EXE file.");
    }

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
                cost_price NUMERIC(12,2) NOT NULL DEFAULT 0,
                selling_price NUMERIC(12,2) NOT NULL DEFAULT 0
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sales (
                id SERIAL PRIMARY KEY,
                shop_id INTEGER NOT NULL REFERENCES shops(id),
                sale_date DATE NOT NULL,
                total_amount NUMERIC(12,2) NOT NULL,
                receipt_number VARCHAR(50)
            )
        """);
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS sale_items (
                id SERIAL PRIMARY KEY,
                sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL REFERENCES products(id),
                quantity_sold INTEGER NOT NULL,
                unit_price NUMERIC(12,2) NOT NULL
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
                shop_id INTEGER REFERENCES shops(id),
                must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
                failed_attempts INTEGER NOT NULL DEFAULT 0,
                locked_until TIMESTAMP
            )
        """);

        // ─── Schema Auto-Migrations ──────────────────────────────────────────
        try {
            stmt.execute("ALTER TABLE products ALTER COLUMN cost_price TYPE NUMERIC(12,2)");
            stmt.execute("ALTER TABLE products ALTER COLUMN selling_price TYPE NUMERIC(12,2)");
            stmt.execute("ALTER TABLE sales ALTER COLUMN total_amount TYPE NUMERIC(12,2)");
            stmt.execute("ALTER TABLE sale_items ALTER COLUMN unit_price TYPE NUMERIC(12,2)");
        } catch (SQLException e) {
            System.out.println("Column type migrations completed or skipped: " + e.getMessage());
        }

        try {
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0");
            stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP");
        } catch (SQLException e) {
            System.out.println("User table schema columns migrations completed or skipped: " + e.getMessage());
        }
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
        settingsRepository.setDefaultSetting(conn, "usd_rate", "1380");
        settingsRepository.setDefaultSetting(conn, "receipt_counter", "1");

        ResultSet ur = conn.createStatement().executeQuery("SELECT COUNT(*) FROM users");
        ur.next();
        if (ur.getInt(1) == 0) {
            ResultSet shops = conn.createStatement().executeQuery("SELECT id FROM shops ORDER BY id");
            List<Integer> ids = new ArrayList<>();
            while (shops.next()) ids.add(shops.getInt(1));
            userRepository.createUserIfAbsent(conn, "admin",  "admin123",  "ADMIN",  null, true);
            if (ids.size() >= 1) userRepository.createUserIfAbsent(conn, "shop1", "shop1pass", "WORKER", ids.get(0), true);
            if (ids.size() >= 2) userRepository.createUserIfAbsent(conn, "shop2", "shop2pass", "WORKER", ids.get(1), true);
            if (ids.size() >= 3) userRepository.createUserIfAbsent(conn, "shop3", "shop3pass", "WORKER", ids.get(2), true);
        }
    }

    // ─── Getters for Repository Sub-classes ─────────────────────────────────

    public SettingsRepository getSettingsRepository() { return settingsRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public ShopRepository getShopRepository() { return shopRepository; }
    public ProductRepository getProductRepository() { return productRepository; }
    public SaleRepository getSaleRepository() { return saleRepository; }

    // ─── Backward-compatible Delegation Facade ────────────────────────────────

    public String getSetting(String key) { return settingsRepository.getSetting(key); }
    public void setSetting(String key, String value) { settingsRepository.setSetting(key, value); }

    public User authenticate(String username, String password) { return userRepository.authenticate(username, password); }
    public List<User> getAllUsers() { return userRepository.getAllUsers(); }
    public boolean addUser(String username, String password, String role, Integer shopId) { return userRepository.addUser(username, password, role, shopId); }
    public boolean changePassword(int userId, String newPassword) { return userRepository.changePassword(userId, newPassword); }
    public boolean resetPassword(int userId, String newPassword) { return userRepository.resetPassword(userId, newPassword); }
    public boolean deleteUser(int userId) { return userRepository.deleteUser(userId); }

    public List<Shop> getAllShops() { return shopRepository.getAllShops(); }
    public boolean addShop(String name, String description) { return shopRepository.addShop(name, description); }
    public boolean updateShop(int id, String name, String description) { return shopRepository.updateShop(id, name, description); }
    public boolean deleteShop(int id) { return shopRepository.deleteShop(id); }
    public boolean deleteShopCascade(int shopId) { return shopRepository.deleteShopCascade(shopId); }
    public boolean shopHasData(int shopId) { return shopRepository.shopHasData(shopId); }

    public List<Product> getProducts(int shopId) { return productRepository.getProducts(shopId); }
    public boolean addProduct(Product p) { return productRepository.addProduct(p); }
    public boolean updateProduct(Product p) { return productRepository.updateProduct(p); }
    public boolean deleteProduct(int id) { return productRepository.deleteProduct(id); }
    public boolean productHasSales(int productId) { return productRepository.productHasSales(productId); }
    public boolean retireProduct(int productId) { return productRepository.retireProduct(productId); }
    public int getLowStockCount(int shopId) { return productRepository.getLowStockCount(shopId); }
    public List<Product> getLowStockProducts(int shopId) { return productRepository.getLowStockProducts(shopId); }
    public BigDecimal getTotalStockValue(int shopId) { return productRepository.getTotalStockValue(shopId); }
    public List<Product> getProductsByShop(int shopId) { return productRepository.getProducts(shopId); }

    public int recordSale(Sale sale) { return saleRepository.recordSale(sale); }
    public List<Sale> getSales(int shopId, LocalDate from, LocalDate to) { return saleRepository.getSales(shopId, from, to); }
    public List<SaleItem> getSaleItems(int saleId) { return saleRepository.getSaleItems(saleId); }
    public BigDecimal getTotalSales(int shopId, LocalDate from, LocalDate to) { return saleRepository.getTotalSales(shopId, from, to); }
    public BigDecimal getDailySales(int shopId, LocalDate date) { return saleRepository.getDailySales(shopId, date); }
    public BigDecimal getMonthlySales(int shopId, int year, int month) { return saleRepository.getMonthlySales(shopId, year, month); }
    public BigDecimal getAnnualSales(int shopId, int year) { return saleRepository.getAnnualSales(shopId, year); }
    public List<Object[]> getTopProducts(int shopId, LocalDate from, LocalDate to, int limit) { return saleRepository.getTopProducts(shopId, from, to, limit); }
    public String getSaleReceiptNumber(int saleId) { return saleRepository.getSaleReceiptNumber(saleId); }
    public BigDecimal getTodaySales(int shopId) { return saleRepository.getTodaySales(shopId); }
    public List<Sale> getSalesByShop(int shopId, LocalDate from, LocalDate to) { return saleRepository.getSales(shopId, from, to); }
    public int saveSale(Sale sale) { return saleRepository.recordSale(sale); }
    public List<FlatSaleRow> getFlatSaleRows(int shopId, LocalDate from, LocalDate to) { return saleRepository.getFlatSaleRows(shopId, from, to); }
    public BigDecimal[] getShopDayMonthTotals(int shopId) { return saleRepository.getShopDayMonthTotals(shopId); }
    public BigDecimal[] getDashboardStats(int shopId) { return saleRepository.getDashboardStats(shopId); }
    public List<OverviewRow> getOverviewStats() { return saleRepository.getOverviewStats(); }

    // ─── DTO Inner Classes ──────────────────────────────────────────────────

    public static class FlatSaleRow {
        public final LocalDate date;
        public final String receipt;
        public final BigDecimal saleTotal;
        public final String product;
        public final int qty;
        public final BigDecimal unitPrice;
        public FlatSaleRow(LocalDate d, String r, BigDecimal t, String p, int q, BigDecimal u) {
            date=d; receipt=r; saleTotal=t; product=p; qty=q; unitPrice=u;
        }
        public BigDecimal getSubtotal() { return BigDecimal.valueOf(qty).multiply(unitPrice); }
    }

    public static class OverviewRow {
        public final String name;
        public final BigDecimal today, month, stockValue;
        public final int lowStock, products;
        public OverviewRow(String n, BigDecimal t, BigDecimal m, BigDecimal sv, int ls, int p) {
            name=n; today=t; month=m; stockValue=sv; lowStock=ls; products=p;
        }
    }
}

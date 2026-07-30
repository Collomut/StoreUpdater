package com.stockmanager.db;

import com.stockmanager.model.Sale;
import com.stockmanager.model.SaleItem;
import com.stockmanager.db.DatabaseManager.FlatSaleRow;
import com.stockmanager.db.DatabaseManager.OverviewRow;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SaleRepository {
    private final DatabaseManager db;

    public SaleRepository(DatabaseManager db) {
        this.db = db;
    }

    public int recordSale(Sale sale) {
        try (Connection conn = db.getConn()) {
            conn.setAutoCommit(false); // Start Transaction
            try {
                String counterStr = db.getSetting("receipt_counter");
                int counter = counterStr != null ? Integer.parseInt(counterStr) : 1;
                db.setSetting("receipt_counter", String.valueOf(counter + 1));
                String receiptNo = String.format("RCP-%04d", counter);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sales (shop_id, sale_date, total_amount, receipt_number) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, sale.getShopId());
                    ps.setDate(2, Date.valueOf(sale.getSaleDate()));
                    ps.setBigDecimal(3, sale.getTotalAmount());
                    ps.setString(4, receiptNo);
                    ps.execute();
                    sale.setReceiptNumber(receiptNo);

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            conn.rollback();
                            return -1;
                        }
                        int saleId = keys.getInt(1);

                        try (PreparedStatement iPs = conn.prepareStatement(
                                "INSERT INTO sale_items (sale_id, product_id, quantity_sold, unit_price) VALUES (?,?,?,?)");
                             PreparedStatement upd = conn.prepareStatement(
                                "UPDATE products SET quantity = quantity - ? WHERE id = ?")) {
                            for (SaleItem item : sale.getItems()) {
                                iPs.setInt(1, saleId); iPs.setInt(2, item.getProductId());
                                iPs.setInt(3, item.getQuantitySold()); iPs.setBigDecimal(4, item.getUnitPrice());
                                iPs.execute();

                                upd.setInt(1, item.getQuantitySold()); upd.setInt(2, item.getProductId());
                                upd.execute();
                            }
                        }
                        conn.commit(); // Commit Transaction
                        return saleId;
                    }
                }
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public List<Sale> getSales(int shopId, LocalDate from, LocalDate to) {
        List<Sale> list = new ArrayList<>();
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM sales WHERE shop_id=? AND sale_date BETWEEN ? AND ? ORDER BY sale_date DESC")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Sale s = new Sale();
                    s.setId(rs.getInt("id")); s.setShopId(rs.getInt("shop_id"));
                    s.setSaleDate(rs.getDate("sale_date").toLocalDate());
                    s.setTotalAmount(rs.getBigDecimal("total_amount"));
                    s.setReceiptNumber(rs.getString("receipt_number"));
                    list.add(s);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public List<SaleItem> getSaleItems(int saleId) {
        List<SaleItem> list = new ArrayList<>();
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT si.*, p.name as product_name FROM sale_items si " +
                "JOIN products p ON si.product_id = p.id WHERE si.sale_id=?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaleItem si = new SaleItem();
                    si.setId(rs.getInt("id")); si.setSaleId(saleId);
                    si.setProductId(rs.getInt("product_id"));
                    si.setQuantitySold(rs.getInt("quantity_sold"));
                    si.setUnitPrice(rs.getBigDecimal("unit_price"));
                    si.setProductName(rs.getString("product_name"));
                    list.add(si);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public BigDecimal getTotalSales(int shopId, LocalDate from, LocalDate to) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE shop_id=? AND sale_date BETWEEN ? AND ?")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal val = rs.getBigDecimal(1);
                    return val != null ? val : BigDecimal.ZERO;
                }
                return BigDecimal.ZERO;
            }
        } catch (SQLException e) { return BigDecimal.ZERO; }
    }

    public BigDecimal getDailySales(int shopId, LocalDate date) { return getTotalSales(shopId, date, date); }

    public BigDecimal getMonthlySales(int shopId, int year, int month) {
        return getTotalSales(shopId, LocalDate.of(year, month, 1), YearMonth.of(year, month).atEndOfMonth());
    }

    public BigDecimal getAnnualSales(int shopId, int year) {
        return getTotalSales(shopId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public List<Object[]> getTopProducts(int shopId, LocalDate from, LocalDate to, int limit) {
        List<Object[]> list = new ArrayList<>();
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT p.name, SUM(si.quantity_sold * si.unit_price) as revenue " +
                "FROM sale_items si JOIN products p ON si.product_id=p.id " +
                "JOIN sales s ON si.sale_id=s.id " +
                "WHERE s.shop_id=? AND s.sale_date BETWEEN ? AND ? " +
                "GROUP BY p.name ORDER BY revenue DESC LIMIT ?")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to)); ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Object[]{rs.getString(1), rs.getBigDecimal(2)});
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public String getSaleReceiptNumber(int saleId) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT receipt_number FROM sales WHERE id=?")) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        } catch (SQLException e) { return ""; }
    }

    public List<FlatSaleRow> getFlatSaleRows(int shopId, LocalDate from, LocalDate to) {
        List<FlatSaleRow> list = new ArrayList<>();
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT s.sale_date, s.receipt_number, s.total_amount, " +
                "       p.name AS product_name, p.category AS product_category, p.unit AS product_unit, " +
                "       si.quantity_sold, si.unit_price, sh.name as shop_name " +
                "FROM sale_items si " +
                "JOIN sales s ON si.sale_id = s.id " +
                "JOIN products p ON si.product_id = p.id " +
                "JOIN shops sh ON s.shop_id = sh.id " +
                "WHERE s.shop_id=? AND s.sale_date BETWEEN ? AND ? " +
                "ORDER BY s.sale_date DESC, s.id DESC")) {
            ps.setInt(1, shopId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pName = rs.getString("product_name");
                    String pCat = rs.getString("product_category");
                    String pUnit = rs.getString("product_unit");
                    String shopName = rs.getString("shop_name");
                    boolean isNyabugogo = shopName != null && shopName.toLowerCase().contains("nyabugogo");

                    String fullProductName;
                    if (isNyabugogo) {
                        String color = (pCat != null && !pCat.isBlank()) ? pCat : "";
                        String size  = (pUnit != null && !pUnit.isBlank()) ? pUnit : "";
                        fullProductName = pName
                            + (color.isEmpty() ? "" : " — " + color)
                            + (size.isEmpty()  ? "" : " (" + size + ")");
                    } else {
                        fullProductName = pName;
                    }

                    list.add(new FlatSaleRow(
                        rs.getDate("sale_date").toLocalDate(),
                        rs.getString("receipt_number"),
                        rs.getBigDecimal("total_amount"),
                        fullProductName,
                        rs.getInt("quantity_sold"),
                        rs.getBigDecimal("unit_price")
                    ));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public BigDecimal[] getShopDayMonthTotals(int shopId) {
        try (Connection conn = db.getConn()) {
            LocalDate today = LocalDate.now();
            LocalDate monthStart = today.withDayOfMonth(1);
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT " +
                "  COALESCE(SUM(CASE WHEN sale_date = ? THEN total_amount ELSE 0 END), 0) AS day_total," +
                "  COALESCE(SUM(CASE WHEN sale_date >= ? THEN total_amount ELSE 0 END), 0) AS month_total " +
                "FROM sales WHERE shop_id = ?")) {
                ps.setDate(1, Date.valueOf(today));
                ps.setDate(2, Date.valueOf(monthStart));
                ps.setInt(3, shopId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal d = rs.getBigDecimal("day_total");
                        BigDecimal m = rs.getBigDecimal("month_total");
                        return new BigDecimal[]{
                            d != null ? d : BigDecimal.ZERO,
                            m != null ? m : BigDecimal.ZERO
                        };
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    public BigDecimal[] getDashboardStats(int shopId) {
        BigDecimal[] r = new BigDecimal[5];
        Arrays.fill(r, BigDecimal.ZERO);
        try (Connection conn = db.getConn()) {
            try (PreparedStatement ps1 = conn.prepareStatement(
                "SELECT " +
                "  COALESCE(SUM(CASE WHEN sale_date = CURRENT_DATE THEN total_amount END), 0)              AS today_sales," +
                "  COALESCE(SUM(CASE WHEN sale_date >= date_trunc('week',  CURRENT_DATE) THEN total_amount END), 0) AS week_sales," +
                "  COALESCE(SUM(CASE WHEN sale_date >= date_trunc('month', CURRENT_DATE) THEN total_amount END), 0) AS month_sales " +
                "FROM sales WHERE shop_id = ?")) {
                ps1.setInt(1, shopId);
                try (ResultSet rs1 = ps1.executeQuery()) {
                    if (rs1.next()) {
                        r[0] = rs1.getBigDecimal("today_sales");
                        r[1] = rs1.getBigDecimal("week_sales");
                        r[2] = rs1.getBigDecimal("month_sales");
                    }
                }
            }

            try (PreparedStatement ps2 = conn.prepareStatement(
                "SELECT " +
                "  COALESCE(SUM(quantity * selling_price), 0)               AS stock_value," +
                "  COUNT(CASE WHEN quantity < 5 THEN 1 END)                 AS low_count " +
                "FROM products WHERE shop_id = ?")) {
                ps2.setInt(1, shopId);
                try (ResultSet rs2 = ps2.executeQuery()) {
                    if (rs2.next()) {
                        r[3] = rs2.getBigDecimal("stock_value");
                        r[4] = BigDecimal.valueOf(rs2.getInt("low_count"));
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        for (int i = 0; i < r.length; i++) {
            if (r[i] == null) r[i] = BigDecimal.ZERO;
        }
        return r;
    }

    public List<OverviewRow> getOverviewStats() {
        List<OverviewRow> list = new ArrayList<>();
        try (Connection conn = db.getConn();
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
                BigDecimal today = rs.getBigDecimal("today_sales");
                BigDecimal month = rs.getBigDecimal("month_sales");
                BigDecimal stockValue = rs.getBigDecimal("stock_value");
                list.add(new OverviewRow(
                    rs.getString("name"),
                    today != null ? today : BigDecimal.ZERO,
                    month != null ? month : BigDecimal.ZERO,
                    stockValue != null ? stockValue : BigDecimal.ZERO,
                    rs.getInt("low_stock"),
                    rs.getInt("total_products")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public BigDecimal getTodaySales(int shopId) {
        return getDailySales(shopId, LocalDate.now());
    }
}

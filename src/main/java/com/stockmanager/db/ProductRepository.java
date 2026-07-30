package com.stockmanager.db;

import com.stockmanager.model.Product;
import com.stockmanager.util.DataCache;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private final DatabaseManager db;

    public ProductRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Product> getProducts(int shopId) {
        return DataCache.getProducts(shopId, () -> {
            List<Product> list = new ArrayList<>();
            try (Connection conn = db.getConn();
                 PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM products WHERE shop_id=? ORDER BY name")) {
                ps.setInt(1, shopId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapProduct(rs));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        });
    }

    public boolean addProduct(Product p) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO products (shop_id,name,category,sku,unit,quantity,reorder_level,cost_price,selling_price) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, p.getShopId()); ps.setString(2, p.getName());
            ps.setString(3, p.getCategory()); ps.setString(4, p.getSku());
            ps.setString(5, p.getUnit()); ps.setInt(6, p.getQuantity());
            ps.setInt(7, p.getReorderLevel());
            ps.setBigDecimal(8, p.getCostPrice());
            ps.setBigDecimal(9, p.getSellingPrice());
            ps.execute();
            DataCache.invalidateProducts(p.getShopId());  // keep cache fresh
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean updateProduct(Product p) {
        int reorderLevel = (p.getReorderLevel() < 0 && p.getQuantity() > 0)
            ? 5 : p.getReorderLevel();
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET name=?,category=?,sku=?,unit=?,quantity=?,reorder_level=?,cost_price=?,selling_price=? WHERE id=?")) {
            ps.setString(1, p.getName()); ps.setString(2, p.getCategory());
            ps.setString(3, p.getSku()); ps.setString(4, p.getUnit());
            ps.setInt(5, p.getQuantity()); ps.setInt(6, reorderLevel);
            ps.setBigDecimal(7, p.getCostPrice()); ps.setBigDecimal(8, p.getSellingPrice());
            ps.setInt(9, p.getId());
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateProducts(p.getShopId());
            return ok;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean deleteProduct(int id) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE id=?")) {
            ps.setInt(1, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateAllProducts();
            return ok;
        } catch (SQLException e) { return false; }
    }

    public boolean productHasSales(int productId) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM sale_items WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) { return false; }
    }

    public boolean retireProduct(int productId) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET quantity = 0, reorder_level = -1 WHERE id = ?")) {
            ps.setInt(1, productId);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateAllProducts();
            return ok;
        } catch (SQLException e) { return false; }
    }

    public int getLowStockCount(int shopId) {
        return (int) getProducts(shopId).stream().filter(Product::isLowStock).count();
    }

    public List<Product> getLowStockProducts(int shopId) {
        return getProducts(shopId).stream()
            .filter(Product::isLowStock)
            .sorted(java.util.Comparator.comparingInt(Product::getQuantity))
            .collect(java.util.stream.Collectors.toList());
    }

    public BigDecimal getTotalStockValue(int shopId) {
        return getProducts(shopId).stream()
            .map(p -> p.getSellingPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id")); p.setShopId(rs.getInt("shop_id"));
        p.setName(rs.getString("name")); p.setCategory(rs.getString("category"));
        p.setSku(rs.getString("sku")); p.setUnit(rs.getString("unit"));
        p.setQuantity(rs.getInt("quantity")); p.setReorderLevel(rs.getInt("reorder_level"));
        p.setCostPrice(rs.getBigDecimal("cost_price")); p.setSellingPrice(rs.getBigDecimal("selling_price"));
        return p;
    }
}

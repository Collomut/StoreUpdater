package com.stockmanager.db;

import com.stockmanager.model.Shop;
import com.stockmanager.util.DataCache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ShopRepository {
    private final DatabaseManager db;

    public ShopRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Shop> getAllShops() {
        return DataCache.getShops(() -> {
            List<Shop> list = new ArrayList<>();
            try (Connection conn = db.getConn();
                 ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM shops ORDER BY name")) {
                while (rs.next()) {
                    list.add(new Shop(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        });
    }

    public boolean addShop(String name, String description) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO shops (name, description) VALUES (?, ?)")) {
            ps.setString(1, name); ps.setString(2, description); ps.execute();
            DataCache.invalidateShops(); return true;
        } catch (SQLException e) { return false; }
    }

    public boolean updateShop(int id, String name, String description) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE shops SET name=?, description=? WHERE id=?")) {
            ps.setString(1, name); ps.setString(2, description); ps.setInt(3, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateShops();
            return ok;
        } catch (SQLException e) { return false; }
    }

    public boolean deleteShop(int id) {
        if (shopHasData(id)) return false;   // guard against FK violations
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM shops WHERE id=?")) {
            ps.setInt(1, id);
            boolean ok = ps.executeUpdate() > 0;
            if (ok) DataCache.invalidateShops();
            return ok;
        } catch (SQLException e) { return false; }
    }

    /** Force-deletes a shop including all its products and sales (cascade). */
    public boolean deleteShopCascade(int shopId) {
        try (Connection conn = db.getConn()) {
            conn.setAutoCommit(false);
            try {
                // Delete sale_items for all sales of this shop
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM sale_items WHERE sale_id IN (SELECT id FROM sales WHERE shop_id = " + shopId + ")");
                }
                try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM sales WHERE shop_id=?")) {
                    ps1.setInt(1, shopId); ps1.execute();
                }
                try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM products WHERE shop_id=?")) {
                    ps2.setInt(1, shopId); ps2.execute();
                }
                try (PreparedStatement ps3 = conn.prepareStatement("DELETE FROM shops WHERE id=?")) {
                    ps3.setInt(1, shopId); ps3.execute();
                }
                conn.commit();
                DataCache.invalidateShops();
                DataCache.invalidateAllProducts();
                return true;
            } catch (SQLException e) {
                conn.rollback(); throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace(); return false;
        }
    }

    /** Returns true if the shop still has products or sales */
    public boolean shopHasData(int shopId) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM products WHERE shop_id=?) + " +
                "       (SELECT COUNT(*) FROM sales    WHERE shop_id=?) AS total")) {
            ps.setInt(1, shopId); ps.setInt(2, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("total") > 0;
            }
        } catch (SQLException e) { return false; }
    }
}

package com.stockmanager.db;

import com.stockmanager.util.DataCache;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SettingsRepository {
    private final DatabaseManager db;

    public SettingsRepository(DatabaseManager db) {
        this.db = db;
    }

    public String getSetting(String key) {
        return DataCache.getSetting(key, () -> {
            try (Connection conn = db.getConn();
                 PreparedStatement ps = conn.prepareStatement("SELECT value FROM settings WHERE key=?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString("value") : null;
                }
            } catch (SQLException e) { return null; }
        });
    }

    public void setSetting(String key, String value) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value")) {
            ps.setString(1, key); ps.setString(2, value); ps.execute();
            DataCache.invalidateSetting(key);  // keep cache fresh
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void setDefaultSetting(Connection conn, String key, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT (key) DO NOTHING")) {
            ps.setString(1, key); ps.setString(2, value); ps.execute();
        }
    }
}

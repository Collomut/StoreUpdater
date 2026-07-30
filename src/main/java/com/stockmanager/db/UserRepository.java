package com.stockmanager.db;

import com.stockmanager.model.User;
import com.stockmanager.util.PasswordUtil;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final DatabaseManager db;

    public UserRepository(DatabaseManager db) {
        this.db = db;
    }

    public User authenticate(String username, String password) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT id, username, password_hash, role, shop_id, must_change_password, failed_attempts, locked_until FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    int failedAttempts = rs.getInt("failed_attempts");
                    Timestamp lockedUntil = rs.getTimestamp("locked_until");

                    // Check if locked
                    if (lockedUntil != null && lockedUntil.getTime() > System.currentTimeMillis()) {
                        long secs = (lockedUntil.getTime() - System.currentTimeMillis()) / 1000;
                        throw new RuntimeException("Account is locked. Try again in " + secs + " seconds.");
                    }

                    if (PasswordUtil.verify(password, rs.getString("password_hash"))) {
                        // Success: reset attempts
                        try (PreparedStatement resetPs = conn.prepareStatement(
                                "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
                            resetPs.setInt(1, id);
                            resetPs.executeUpdate();
                        }

                        User u = new User();
                        u.setId(id);
                        u.setUsername(rs.getString("username"));
                        u.setRole(rs.getString("role"));
                        int sid = rs.getInt("shop_id");
                        u.setShopId(rs.wasNull() ? null : sid);
                        u.setMustChangePassword(rs.getBoolean("must_change_password"));
                        return u;
                    } else {
                        // Failure: increment attempts
                        failedAttempts++;
                        Timestamp newLock = null;
                        String lockMsg = "";

                        if (failedAttempts >= 3) {
                            long lockMillis = 0;
                            if (failedAttempts == 3) {
                                lockMillis = 30 * 1000; // 30 seconds
                                lockMsg = " Account locked for 30 seconds.";
                            } else if (failedAttempts == 4) {
                                lockMillis = 2 * 60 * 1000; // 2 minutes
                                lockMsg = " Account locked for 2 minutes.";
                            } else if (failedAttempts == 5) {
                                lockMillis = 10 * 60 * 1000; // 10 minutes
                                lockMsg = " Account locked for 10 minutes.";
                            } else {
                                lockMillis = 60 * 60 * 1000; // 1 hour
                                lockMsg = " Account locked for 1 hour.";
                            }
                            newLock = new Timestamp(System.currentTimeMillis() + lockMillis);
                        }

                        try (PreparedStatement updatePs = conn.prepareStatement(
                                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
                            updatePs.setInt(1, failedAttempts);
                            updatePs.setTimestamp(2, newLock);
                            updatePs.setInt(3, id);
                            updatePs.executeUpdate();
                        }

                        throw new RuntimeException("Incorrect username or password." + lockMsg);
                    }
                } else {
                    throw new RuntimeException("Incorrect username or password.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Database error during authentication.", e);
        }
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        try (Connection conn = db.getConn();
             ResultSet rs = conn.createStatement()
                .executeQuery("SELECT id, username, role, shop_id, must_change_password FROM users ORDER BY role, username")) {
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id")); u.setUsername(rs.getString("username"));
                u.setRole(rs.getString("role"));
                int sid = rs.getInt("shop_id");
                u.setShopId(rs.wasNull() ? null : sid);
                u.setMustChangePassword(rs.getBoolean("must_change_password"));
                list.add(u);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean addUser(String username, String password, String role, Integer shopId) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES (?, ?, ?, ?, TRUE)")) {
            ps.setString(1, username); ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, role);
            if (shopId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, shopId);
            ps.execute(); return true;
        } catch (SQLException e) { return false; }
    }

    public boolean changePassword(int userId, String newPassword) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET password_hash = ?, must_change_password = FALSE WHERE id = ?")) {
            ps.setString(1, PasswordUtil.hash(newPassword)); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean resetPassword(int userId, String newPassword) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET password_hash = ?, must_change_password = TRUE WHERE id = ?")) {
            ps.setString(1, PasswordUtil.hash(newPassword)); ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean deleteUser(int userId) {
        try (Connection conn = db.getConn();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, userId); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public void createUserIfAbsent(Connection conn, String username, String password, String role, Integer shopId, boolean mustChangePassword) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO users (username, password_hash, role, shop_id, must_change_password) VALUES (?, ?, ?, ?, ?) ON CONFLICT (username) DO NOTHING")) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(password));
            ps.setString(3, role);
            if (shopId == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, shopId);
            ps.setBoolean(5, mustChangePassword);
            ps.execute();
        }
    }
}

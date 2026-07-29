package com.stockmanager.model;

public class User {
    private int id;
    private String username;
    private String role;      // "ADMIN" or "WORKER"
    private Integer shopId;   // null for admin (sees all shops)

    public User() {}

    public User(int id, String username, String role, Integer shopId) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.shopId = shopId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getShopId() { return shopId; }
    public void setShopId(Integer shopId) { this.shopId = shopId; }

    public boolean isAdmin() { return "ADMIN".equals(role); }

    @Override
    public String toString() { return username; }
}

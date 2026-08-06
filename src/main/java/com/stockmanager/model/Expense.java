package com.stockmanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Expense {
    private int id;
    private int shopId;
    private Integer userId;
    private String username;
    private LocalDate expenseDate;
    private BigDecimal amount;
    private String category;
    private String paymentMethod = "CASH";
    private String notes;

    public Expense() {}

    public Expense(int id, int shopId, Integer userId, String username, LocalDate expenseDate, BigDecimal amount, String category, String paymentMethod, String notes) {
        this.id = id;
        this.shopId = shopId;
        this.userId = userId;
        this.username = username;
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.category = category;
        this.paymentMethod = paymentMethod != null ? paymentMethod : "CASH";
        this.notes = notes;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getShopId() { return shopId; }
    public void setShopId(int shopId) { this.shopId = shopId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

package com.stockmanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Sale {
    private int id;
    private int shopId;
    private LocalDate saleDate;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String notes;
    private String receiptNumber;
    private List<SaleItem> items = new ArrayList<>();

    public Sale() {}

    public Sale(int id, int shopId, LocalDate saleDate, BigDecimal totalAmount,
                String notes, String receiptNumber) {
        this.id = id; this.shopId = shopId; this.saleDate = saleDate;
        this.totalAmount = totalAmount; this.notes = notes;
        this.receiptNumber = receiptNumber;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getShopId() { return shopId; }
    public void setShopId(int shopId) { this.shopId = shopId; }
    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }
    public List<SaleItem> getItems() { return items; }
    public void setItems(List<SaleItem> items) { this.items = items; }
    public void addItem(SaleItem item) { this.items.add(item); }
}

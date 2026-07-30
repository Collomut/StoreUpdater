package com.stockmanager.model;

import java.math.BigDecimal;

public class SaleItem {
    private int id;
    private int saleId;
    private int productId;
    private String productName;
    private int quantitySold;
    private BigDecimal unitPrice = BigDecimal.ZERO;

    public SaleItem() {}

    public SaleItem(int saleId, int productId, String productName,
                    int quantitySold, BigDecimal unitPrice) {
        this.saleId = saleId; this.productId = productId;
        this.productName = productName; this.quantitySold = quantitySold;
        this.unitPrice = unitPrice;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSaleId() { return saleId; }
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getQuantitySold() { return quantitySold; }
    public void setQuantitySold(int quantitySold) { this.quantitySold = quantitySold; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getSubtotal() { return BigDecimal.valueOf(quantitySold).multiply(unitPrice); }
}

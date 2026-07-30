package com.stockmanager.model;

import javafx.beans.property.*;
import java.math.BigDecimal;

public class Product {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final IntegerProperty shopId = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty category = new SimpleStringProperty();
    private final StringProperty sku = new SimpleStringProperty();
    private final StringProperty unit = new SimpleStringProperty();
    private final IntegerProperty quantity = new SimpleIntegerProperty();
    private final IntegerProperty reorderLevel = new SimpleIntegerProperty();
    private final ObjectProperty<BigDecimal> costPrice = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> sellingPrice = new SimpleObjectProperty<>(BigDecimal.ZERO);

    public Product() {}

    public Product(int id, int shopId, String name, String category, String sku,
                   String unit, int quantity, int reorderLevel,
                   BigDecimal costPrice, BigDecimal sellingPrice) {
        setId(id); setShopId(shopId); setName(name); setCategory(category);
        setSku(sku); setUnit(unit); setQuantity(quantity);
        setReorderLevel(reorderLevel); setCostPrice(costPrice); setSellingPrice(sellingPrice);
    }

    // ID
    public int getId() { return id.get(); }
    public void setId(int v) { id.set(v); }
    public IntegerProperty idProperty() { return id; }

    // Shop ID
    public int getShopId() { return shopId.get(); }
    public void setShopId(int v) { shopId.set(v); }
    public IntegerProperty shopIdProperty() { return shopId; }

    // Name
    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }
    public StringProperty nameProperty() { return name; }

    // Category
    public String getCategory() { return category.get(); }
    public void setCategory(String v) { category.set(v); }
    public StringProperty categoryProperty() { return category; }

    // SKU
    public String getSku() { return sku.get(); }
    public void setSku(String v) { sku.set(v); }
    public StringProperty skuProperty() { return sku; }

    // Unit
    public String getUnit() { return unit.get(); }
    public void setUnit(String v) { unit.set(v); }
    public StringProperty unitProperty() { return unit; }

    // Quantity
    public int getQuantity() { return quantity.get(); }
    public void setQuantity(int v) { quantity.set(v); }
    public IntegerProperty quantityProperty() { return quantity; }

    // Reorder Level
    public int getReorderLevel() { return reorderLevel.get(); }
    public void setReorderLevel(int v) { reorderLevel.set(v); }
    public IntegerProperty reorderLevelProperty() { return reorderLevel; }

    // Cost Price
    public BigDecimal getCostPrice() { return costPrice.get(); }
    public void setCostPrice(BigDecimal v) { costPrice.set(v); }
    public ObjectProperty<BigDecimal> costPriceProperty() { return costPrice; }

    // Selling Price
    public BigDecimal getSellingPrice() { return sellingPrice.get(); }
    public void setSellingPrice(BigDecimal v) { sellingPrice.set(v); }
    public ObjectProperty<BigDecimal> sellingPriceProperty() { return sellingPrice; }

    /** A product is retired when reorderLevel is set to -1 (no schema change needed). */
    public boolean isRetired()  { return getReorderLevel() < 0; }

    /** Low stock only applies to active (non-retired) products. */
    public boolean isLowStock() { return !isRetired() && getQuantity() < 5; }

    @Override
    public String toString() { return getName(); }
}

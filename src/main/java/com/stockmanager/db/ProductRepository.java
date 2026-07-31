package com.stockmanager.db;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stockmanager.model.Product;
import com.stockmanager.util.DataCache;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ProductRepository {
    private final DatabaseManager db;
    private final Gson gson = new Gson();

    public ProductRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Product> getProducts(int shopId) {
        return DataCache.getProducts(shopId, () -> {
            List<Product> list = new ArrayList<>();
            try {
                HttpResponse<String> response = HttpDatabaseClient.get("/api/products?shopId=" + shopId);
                if (response.statusCode() == 200) {
                    JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        Product p = new Product();
                        p.setId(obj.get("id").getAsInt());
                        p.setShopId(obj.get("shop_id").getAsInt());
                        p.setName(obj.get("name").getAsString());
                        
                        JsonElement catVal = obj.get("category");
                        p.setCategory(catVal == null || catVal.isJsonNull() ? "" : catVal.getAsString());
                        
                        JsonElement skuVal = obj.get("sku");
                        p.setSku(skuVal == null || skuVal.isJsonNull() ? "" : skuVal.getAsString());
                        
                        JsonElement unitVal = obj.get("unit");
                        p.setUnit(unitVal == null || unitVal.isJsonNull() ? "" : unitVal.getAsString());
                        
                        p.setQuantity(obj.get("quantity").getAsInt());
                        p.setReorderLevel(obj.get("reorder_level").getAsInt());
                        p.setCostPrice(BigDecimal.valueOf(obj.get("cost_price").getAsDouble()));
                        p.setSellingPrice(BigDecimal.valueOf(obj.get("selling_price").getAsDouble()));
                        list.add(p);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        });
    }

    public boolean addProduct(Product p) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("shopId", p.getShopId());
            body.addProperty("name", p.getName());
            body.addProperty("category", p.getCategory());
            body.addProperty("sku", p.getSku());
            body.addProperty("unit", p.getUnit());
            body.addProperty("quantity", p.getQuantity());
            body.addProperty("reorderLevel", p.getReorderLevel());
            body.addProperty("costPrice", p.getCostPrice().doubleValue());
            body.addProperty("sellingPrice", p.getSellingPrice().doubleValue());

            HttpResponse<String> response = HttpDatabaseClient.post("/api/products", body);
            if (response.statusCode() == 200) {
                DataCache.invalidateProducts(p.getShopId());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateProduct(Product p) {
        int reorderLevel = (p.getReorderLevel() < 0 && p.getQuantity() > 0) ? 5 : p.getReorderLevel();
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", p.getName());
            body.addProperty("category", p.getCategory());
            body.addProperty("sku", p.getSku());
            body.addProperty("unit", p.getUnit());
            body.addProperty("quantity", p.getQuantity());
            body.addProperty("reorderLevel", reorderLevel);
            body.addProperty("costPrice", p.getCostPrice().doubleValue());
            body.addProperty("sellingPrice", p.getSellingPrice().doubleValue());

            HttpResponse<String> response = HttpDatabaseClient.put("/api/products/" + p.getId(), body);
            if (response.statusCode() == 200) {
                DataCache.invalidateProducts(p.getShopId());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteProduct(int id) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.delete("/api/products/" + id);
            if (response.statusCode() == 200) {
                DataCache.invalidateAllProducts();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean productHasSales(int productId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/products/has-sales/" + productId);
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                return obj.get("hasSales").getAsBoolean();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean retireProduct(int productId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.post("/api/products/" + productId + "/retire", new JsonObject());
            if (response.statusCode() == 200) {
                DataCache.invalidateAllProducts();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
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
}

package com.stockmanager.db;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stockmanager.model.Shop;
import com.stockmanager.util.DataCache;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ShopRepository {
    private final DatabaseManager db;
    private final Gson gson = new Gson();

    public ShopRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Shop> getAllShops() {
        return DataCache.getShops(() -> {
            List<Shop> list = new ArrayList<>();
            try {
                HttpResponse<String> response = HttpDatabaseClient.get("/api/shops");
                if (response.statusCode() == 200) {
                    JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        list.add(new Shop(
                            obj.get("id").getAsInt(),
                            obj.get("name").getAsString(),
                            obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : ""
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        });
    }

    public boolean addShop(String name, String description) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            body.addProperty("description", description);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/shops", body);
            if (response.statusCode() == 200) {
                DataCache.invalidateShops();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateShop(int id, String name, String description) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("name", name);
            body.addProperty("description", description);

            HttpResponse<String> response = HttpDatabaseClient.put("/api/shops/" + id, body);
            if (response.statusCode() == 200) {
                DataCache.invalidateShops();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteShop(int id) {
        if (shopHasData(id)) return false;
        try {
            HttpResponse<String> response = HttpDatabaseClient.delete("/api/shops/" + id);
            if (response.statusCode() == 200) {
                DataCache.invalidateShops();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean deleteShopCascade(int shopId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.delete("/api/shops/" + shopId + "/cascade");
            if (response.statusCode() == 200) {
                DataCache.invalidateShops();
                DataCache.invalidateAllProducts();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean shopHasData(int shopId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/shops/has-data/" + shopId);
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                return obj.get("hasData").getAsBoolean();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

package com.stockmanager.db;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stockmanager.util.DataCache;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;

public class SettingsRepository {
    private final DatabaseManager db;

    public SettingsRepository(DatabaseManager db) {
        this.db = db;
    }

    public String getSetting(String key) {
        return DataCache.getSetting(key, () -> {
            try {
                HttpResponse<String> response = HttpDatabaseClient.get("/api/settings/" + key);
                if (response.statusCode() == 200) {
                    JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (obj.has("value") && !obj.get("value").isJsonNull()) {
                        return obj.get("value").getAsString();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public void setSetting(String key, String value) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("key", key);
            body.addProperty("value", value);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/settings", body);
            if (response.statusCode() == 200) {
                DataCache.invalidateSetting(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Retained for backward-compatibility but handled by backend server during init
    public void setDefaultSetting(Connection conn, String key, String value) throws SQLException {
        setSetting(key, value);
    }
}

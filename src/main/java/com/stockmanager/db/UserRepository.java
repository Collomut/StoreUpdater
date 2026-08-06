package com.stockmanager.db;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stockmanager.model.User;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private final DatabaseManager db;
    private final Gson gson = new Gson();

    public UserRepository(DatabaseManager db) {
        this.db = db;
    }

    public User authenticate(String username, String password) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("password", password);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/auth/login", body);

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String token = json.get("token").getAsString();
                HttpDatabaseClient.setJwtToken(token); // Save token for authorization in subsequent requests

                JsonObject uJson = json.getAsJsonObject("user");
                User u = new User();
                u.setId(uJson.get("id").getAsInt());
                u.setUsername(uJson.get("username").getAsString());
                u.setRole(uJson.get("role").getAsString());
                
                JsonElement shopVal = uJson.get("shopId");
                u.setShopId(shopVal == null || shopVal.isJsonNull() ? null : shopVal.getAsInt());
                u.setMustChangePassword(uJson.get("mustChangePassword").getAsBoolean());
                return u;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Incorrect username or password.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error during login authentication.", e);
        }
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/users");
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    User u = new User();
                    u.setId(obj.get("id").getAsInt());
                    u.setUsername(obj.get("username").getAsString());
                    u.setRole(obj.get("role").getAsString());
                    JsonElement shopVal = obj.get("shopId");
                    u.setShopId(shopVal == null || shopVal.isJsonNull() ? null : shopVal.getAsInt());
                    u.setMustChangePassword(obj.get("mustChangePassword").getAsBoolean());
                    list.add(u);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean addUser(String username, String password, String role, Integer shopId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("password", password);
            body.addProperty("role", role);
            if (shopId != null) {
                body.addProperty("shopId", shopId);
            }

            HttpResponse<String> response = HttpDatabaseClient.post("/api/users", body);
            if (response.statusCode() == 200) {
                return true;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Failed to add user.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error creating user.", e);
        }
    }

    public boolean changePassword(int userId, String newPassword) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("userId", userId);
            body.addProperty("newPassword", newPassword);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/users/change-password", body);
            if (response.statusCode() == 200) {
                return true;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Failed to change password.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error changing password.", e);
        }
    }

    public boolean resetPassword(int userId, String newPassword) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("userId", userId);
            body.addProperty("newPassword", newPassword);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/users/reset-password", body);
            if (response.statusCode() == 200) {
                return true;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Failed to reset password.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error resetting password.", e);
        }
    }

    public boolean deleteUser(int userId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.delete("/api/users/" + userId);
            if (response.statusCode() == 200) {
                return true;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Failed to delete user.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error deleting user.", e);
        }
    }
}

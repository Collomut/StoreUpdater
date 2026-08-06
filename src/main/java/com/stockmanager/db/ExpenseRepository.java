package com.stockmanager.db;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stockmanager.model.Expense;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExpenseRepository {
    private final DatabaseManager db;
    private final Gson gson = new Gson();

    public ExpenseRepository(DatabaseManager db) {
        this.db = db;
    }

    public List<Expense> getExpenses(int shopId, LocalDate from, LocalDate to) {
        List<Expense> list = new ArrayList<>();
        try {
            String path = "/api/expenses?shopId=" + shopId;
            if (from != null && to != null) {
                path += "&from=" + from.toString() + "&to=" + to.toString();
            }
            HttpResponse<String> response = HttpDatabaseClient.get(path);
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    Expense e = new Expense();
                    e.setId(obj.get("id").getAsInt());
                    e.setShopId(obj.get("shopId").getAsInt());
                    
                    JsonElement uEl = obj.get("userId");
                    e.setUserId(uEl == null || uEl.isJsonNull() ? null : uEl.getAsInt());
                    
                    JsonElement unEl = obj.get("username");
                    e.setUsername(unEl == null || unEl.isJsonNull() ? "System" : unEl.getAsString());
                    
                    e.setExpenseDate(LocalDate.parse(obj.get("expenseDate").getAsString().substring(0, 10)));
                    e.setAmount(BigDecimal.valueOf(obj.get("amount").getAsDouble()));
                    e.setCategory(obj.get("category").getAsString());
                    
                    JsonElement pmEl = obj.get("paymentMethod");
                    e.setPaymentMethod(pmEl == null || pmEl.isJsonNull() ? "CASH" : pmEl.getAsString());
                    
                    JsonElement nEl = obj.get("notes");
                    e.setNotes(nEl == null || nEl.isJsonNull() ? "" : nEl.getAsString());

                    list.add(e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean addExpense(int shopId, LocalDate expenseDate, BigDecimal amount, String category, String paymentMethod, String notes) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("shopId", shopId);
            body.addProperty("expenseDate", expenseDate != null ? expenseDate.toString() : LocalDate.now().toString());
            body.addProperty("amount", amount.doubleValue());
            body.addProperty("category", category);
            body.addProperty("paymentMethod", paymentMethod != null ? paymentMethod : "CASH");
            if (notes != null) body.addProperty("notes", notes);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/expenses", body);
            if (response.statusCode() == 200) {
                return true;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Failed to record expense.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error recording expense.", e);
        }
    }

    public boolean deleteExpense(int expenseId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.delete("/api/expenses/" + expenseId);
            if (response.statusCode() == 200) {
                return true;
            } else {
                String errorMsg = HttpDatabaseClient.getErrorMessage(response.body(), "Failed to delete expense.");
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("API error deleting expense.", e);
        }
    }
}

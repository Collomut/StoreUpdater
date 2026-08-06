package com.stockmanager.db;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stockmanager.model.Sale;
import com.stockmanager.model.SaleItem;
import com.stockmanager.db.DatabaseManager.FlatSaleRow;
import com.stockmanager.db.DatabaseManager.OverviewRow;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SaleRepository {
    private final DatabaseManager db;
    private final Gson gson = new Gson();

    public SaleRepository(DatabaseManager db) {
        this.db = db;
    }

    public int recordSale(Sale sale) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("shopId", sale.getShopId());
            payload.addProperty("saleDate", sale.getSaleDate().toString());
            payload.addProperty("totalAmount", sale.getTotalAmount().doubleValue());
            payload.addProperty("paymentMethod", sale.getPaymentMethod());

            JsonArray itemArray = new JsonArray();
            for (SaleItem item : sale.getItems()) {
                JsonObject iObj = new JsonObject();
                iObj.addProperty("productId", item.getProductId());
                iObj.addProperty("quantitySold", item.getQuantitySold());
                iObj.addProperty("unitPrice", item.getUnitPrice().doubleValue());
                itemArray.add(iObj);
            }
            payload.add("items", itemArray);

            HttpResponse<String> response = HttpDatabaseClient.post("/api/sales", payload);
            if (response.statusCode() == 200) {
                JsonObject res = JsonParser.parseString(response.body()).getAsJsonObject();
                sale.setReceiptNumber(res.get("receiptNumber").getAsString());
                return res.get("saleId").getAsInt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<Sale> getSales(int shopId, LocalDate from, LocalDate to) {
        List<Sale> list = new ArrayList<>();
        try {
            HttpResponse<String> response = HttpDatabaseClient.get(
                "/api/sales?shopId=" + shopId + "&from=" + from + "&to=" + to
            );
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    Sale s = new Sale();
                    s.setId(obj.get("id").getAsInt());
                    s.setShopId(obj.get("shop_id").getAsInt());
                    s.setSaleDate(LocalDate.parse(obj.get("sale_date").getAsString()));
                    s.setTotalAmount(BigDecimal.valueOf(obj.get("total_amount").getAsDouble()));
                    s.setReceiptNumber(obj.get("receipt_number").getAsString());
                    JsonElement pmEl = obj.get("payment_method");
                    s.setPaymentMethod(pmEl != null && !pmEl.isJsonNull() ? pmEl.getAsString() : "CASH");
                    list.add(s);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<SaleItem> getSaleItems(int saleId) {
        List<SaleItem> list = new ArrayList<>();
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/sales/items/" + saleId);
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    SaleItem si = new SaleItem();
                    si.setId(obj.get("id").getAsInt());
                    si.setSaleId(obj.get("sale_id").getAsInt());
                    si.setProductId(obj.get("product_id").getAsInt());
                    si.setQuantitySold(obj.get("quantity_sold").getAsInt());
                    si.setUnitPrice(BigDecimal.valueOf(obj.get("unit_price").getAsDouble()));
                    si.setProductName(obj.get("product_name").getAsString());
                    list.add(si);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public BigDecimal getTotalSales(int shopId, LocalDate from, LocalDate to) {
        List<Sale> salesList = getSales(shopId, from, to);
        return salesList.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getDailySales(int shopId, LocalDate date) {
        return getTotalSales(shopId, date, date);
    }

    public BigDecimal getMonthlySales(int shopId, int year, int month) {
        return getTotalSales(shopId, LocalDate.of(year, month, 1), YearMonth.of(year, month).atEndOfMonth());
    }

    public BigDecimal getAnnualSales(int shopId, int year) {
        return getTotalSales(shopId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    public List<Object[]> getTopProducts(int shopId, LocalDate from, LocalDate to, int limit) {
        List<Object[]> list = new ArrayList<>();
        try {
            HttpResponse<String> response = HttpDatabaseClient.get(
                "/api/sales/top-products?shopId=" + shopId + "&from=" + from + "&to=" + to + "&limit=" + limit
            );
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonArray row = el.getAsJsonArray();
                    list.add(new Object[]{
                        row.get(0).getAsString(),
                        BigDecimal.valueOf(row.get(1).getAsDouble())
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public String getSaleReceiptNumber(int saleId) {
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/sales/items/" + saleId);
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                if (arr.size() > 0) {
                    return arr.get(0).getAsJsonObject().get("receipt_number").getAsString();
                }
            }
        } catch (Exception e) {}
        return "";
    }

    public List<FlatSaleRow> getFlatSaleRows(int shopId, LocalDate from, LocalDate to) {
        List<FlatSaleRow> list = new ArrayList<>();
        try {
            HttpResponse<String> response = HttpDatabaseClient.get(
                "/api/sales/flat-rows?shopId=" + shopId + "&from=" + from + "&to=" + to
            );
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    
                    String pName = obj.get("product_name").getAsString();
                    
                    JsonElement catEl = obj.get("product_category");
                    String pCat = catEl == null || catEl.isJsonNull() ? "" : catEl.getAsString();
                    
                    JsonElement unitEl = obj.get("product_unit");
                    String pUnit = unitEl == null || unitEl.isJsonNull() ? "" : unitEl.getAsString();
                    
                    JsonElement shopNameEl = obj.get("shop_name");
                    String shopName = shopNameEl == null || shopNameEl.isJsonNull() ? "" : shopNameEl.getAsString();
                    
                    boolean isNyabugogo = !shopName.isEmpty() && shopName.toLowerCase().contains("nyabugogo");

                    String fullProductName;
                    if (isNyabugogo) {
                        String color = !pCat.isEmpty() ? pCat : "";
                        String size  = !pUnit.isEmpty() ? pUnit : "";
                        fullProductName = pName
                            + (color.isEmpty() ? "" : " — " + color)
                            + (size.isEmpty()  ? "" : " (" + size + ")");
                    } else {
                        fullProductName = pName;
                    }

                    JsonElement pmEl = obj.get("payment_method");
                    String payMethod = pmEl != null && !pmEl.isJsonNull() ? pmEl.getAsString() : "CASH";

                    list.add(new FlatSaleRow(
                        LocalDate.parse(obj.get("sale_date").getAsString()),
                        obj.get("receipt_number").getAsString(),
                        BigDecimal.valueOf(obj.get("total_amount").getAsDouble()),
                        fullProductName,
                        obj.get("quantity_sold").getAsInt(),
                        BigDecimal.valueOf(obj.get("unit_price").getAsDouble()),
                        payMethod
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public BigDecimal[] getShopDayMonthTotals(int shopId) {
        try {
            BigDecimal[] stats = getDashboardStats(shopId);
            return new BigDecimal[]{ stats[0], stats[2] }; // [today, month]
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
    }

    public BigDecimal[] getDashboardStats(int shopId) {
        BigDecimal[] r = new BigDecimal[7];
        Arrays.fill(r, BigDecimal.ZERO);
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/sales/dashboard-stats?shopId=" + shopId);
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (int i = 0; i < Math.min(arr.size(), 7); i++) {
                    r[i] = BigDecimal.valueOf(arr.get(i).getAsDouble());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return r;
    }

    public List<OverviewRow> getOverviewStats() {
        List<OverviewRow> list = new ArrayList<>();
        try {
            HttpResponse<String> response = HttpDatabaseClient.get("/api/sales/overview-stats");
            if (response.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    list.add(new OverviewRow(
                        obj.get("name").getAsString(),
                        BigDecimal.valueOf(obj.get("today_sales").getAsDouble()),
                        BigDecimal.valueOf(obj.get("month_sales").getAsDouble()),
                        BigDecimal.valueOf(obj.get("stock_value").getAsDouble()),
                        obj.get("low_stock").getAsInt(),
                        obj.get("total_products").getAsInt()
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public BigDecimal getTodaySales(int shopId) {
        return getDailySales(shopId, LocalDate.now());
    }
}

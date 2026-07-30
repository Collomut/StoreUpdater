package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.Product;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;


public class DashboardController {

    @FXML private Label lblTodaySales, lblTodaySalesUsd;
    @FXML private Label lblStockValue, lblStockValueUsd;
    @FXML private Label lblLowStockCount;
    @FXML private Label lblWeekSales, lblMonthSales;
    @FXML private VBox lowStockList;
    @FXML private Label lblShopName;
    @FXML private Button btnQuickInventory;  // relabelled for workers

    private MainController mainController;
    private int shopId;

    public void setMainController(MainController mc) {
        this.mainController = mc;
        // Issue 10: workers see 'View Inventory' instead of 'Add Stock'
        if (btnQuickInventory != null && !com.stockmanager.util.Session.isAdmin()) {
            btnQuickInventory.setText("View Inventory");
        }
    }

    public void refresh(int shopId) {
        this.shopId = shopId;

        Task<DashboardData> task = new Task<>() {
            @Override protected DashboardData call() {
                DatabaseManager db = DatabaseManager.getInstance();
                DashboardData d = new DashboardData();
                java.math.BigDecimal[] stats = db.getDashboardStats(shopId);
                d.today    = stats[0].doubleValue();
                d.week     = stats[1].doubleValue();
                d.month    = stats[2].doubleValue();
                d.stockVal = stats[3].doubleValue();
                d.lowCount = stats[4].intValue();
                // 1 more round-trip for the list itself
                d.lowProducts = db.getLowStockProducts(shopId);
                // USD rate comes from cache (no extra round-trip after first load)
                String rate = db.getSetting("usd_rate");
                d.usdRate = rate != null ? Double.parseDouble(rate) : 1380;
                return d;
            }
        };


        task.setOnSucceeded(e -> {
            DashboardData d = task.getValue();
            lblTodaySales.setText(formatRwf(d.today));
            lblTodaySalesUsd.setText("≈ " + formatUsd(d.today / d.usdRate));
            lblStockValue.setText(formatRwf(d.stockVal));
            lblStockValueUsd.setText("≈ " + formatUsd(d.stockVal / d.usdRate));
            lblLowStockCount.setText(String.valueOf(d.lowCount));
            lblLowStockCount.setStyle(d.lowCount > 0
                ? "-fx-text-fill: #CC0000; -fx-font-weight: bold;" : "-fx-text-fill: #000000;");
            lblWeekSales.setText(formatRwf(d.week));
            lblMonthSales.setText(formatRwf(d.month));
            renderLowStockList(d.lowProducts);
        });

        task.setOnFailed(e -> System.err.println("Dashboard load failed: " + task.getException()));

        new Thread(task, "dashboard-loader").start();
    }

    private void renderLowStockList(List<Product> lowStock) {
        lowStockList.getChildren().clear();
        if (lowStock.isEmpty()) {
            Label ok = new Label("All products are well-stocked");
            ok.getStyleClass().add("low-stock-ok");
            lowStockList.getChildren().add(ok);
            return;
        }
        for (Product p : lowStock) {
            HBox row = new HBox(10);
            row.getStyleClass().add("low-stock-row");
            Label nameLabel = new Label(p.getName());
            nameLabel.getStyleClass().add("low-stock-name");
            Label qtyLabel = new Label("Qty: " + p.getQuantity());
            qtyLabel.getStyleClass().add("low-stock-qty");
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Label catLabel = new Label(p.getCategory() != null ? p.getCategory() : "");
            catLabel.getStyleClass().add("low-stock-cat");
            row.getChildren().addAll(nameLabel, spacer, qtyLabel, catLabel);
            lowStockList.getChildren().add(row);
        }
    }

    private double getUsdRate(DatabaseManager db) {
        String rate = db.getSetting("usd_rate");
        return rate != null ? Double.parseDouble(rate) : 1380;
    }

    private String formatRwf(double amount) { return String.format("RWF %,.0f", amount); }
    private String formatUsd(double amount)  { return String.format("$ %.2f", amount); }

    @FXML private void handleRefresh()      { refresh(shopId); }
    @FXML private void handleGoInventory()  { mainController.navigateTo("inventory"); }
    @FXML private void handleGoSales()      { mainController.navigateTo("sales"); }

    private static class DashboardData {
        double usdRate, today, stockVal, week, month;
        int lowCount;
        List<Product> lowProducts;
    }
}

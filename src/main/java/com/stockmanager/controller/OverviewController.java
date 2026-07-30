package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.Shop;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;

public class OverviewController {

    @FXML private TableView<ShopRow> overviewTable;
    @FXML private TableColumn<ShopRow, String> colShop, colToday, colMonth, colStock, colLowStock, colProducts;
    @FXML private Label lblAllToday, lblAllMonth;

    private MainController mainController;

    public void setMainController(MainController mc) { this.mainController = mc; }

    @FXML
    public void initialize() {
        colShop.setCellValueFactory(d     -> new SimpleStringProperty(d.getValue().name));
        colToday.setCellValueFactory(d    -> new SimpleStringProperty(fmt(d.getValue().today)));
        colMonth.setCellValueFactory(d    -> new SimpleStringProperty(fmt(d.getValue().month)));
        colStock.setCellValueFactory(d    -> new SimpleStringProperty(fmt(d.getValue().stockValue)));
        colLowStock.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().lowStock)));
        colProducts.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().products)));
    }

    public void refresh() { loadAsync(); }

    @FXML private void handleRefresh() { loadAsync(); }

    private void loadAsync() {
        Task<List<OverviewController.ShopRow>> task = new Task<>() {
            protected List<OverviewController.ShopRow> call() {
                // Single query for all shops — 1 round-trip instead of 12+
                List<DatabaseManager.OverviewRow> raw = DatabaseManager.getInstance().getOverviewStats();
                List<OverviewController.ShopRow> rows = new java.util.ArrayList<>();
                for (DatabaseManager.OverviewRow r : raw) {
                    rows.add(new ShopRow(r.name, r.today.doubleValue(), r.month.doubleValue(), r.stockValue.doubleValue(), r.lowStock, r.products));
                }
                return rows;
            }
        };

        task.setOnSucceeded(e -> {
            List<ShopRow> rows = task.getValue();
            overviewTable.setItems(FXCollections.observableArrayList(rows));
            double totalToday = rows.stream().mapToDouble(r -> r.today).sum();
            double totalMonth = rows.stream().mapToDouble(r -> r.month).sum();
            lblAllToday.setText("All shops today: " + fmt(totalToday));
            lblAllMonth.setText("All shops this month: " + fmt(totalMonth));
        });
        new Thread(task, "overview-loader").start();
    }

    private String fmt(double v) { return String.format("RWF %,.0f", v); }

    public static class ShopRow {
        final String name;
        final double today, month, stockValue;
        final int lowStock, products;
        ShopRow(String n, double t, double m, double sv, int ls, int p) {
            name=n; today=t; month=m; stockValue=sv; lowStock=ls; products=p;
        }
    }
}

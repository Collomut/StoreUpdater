package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.db.DatabaseManager.FlatSaleRow;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.List;

public class HistoryController {

    @FXML private TableView<FlatSaleRow> salesTable;
    @FXML private TableColumn<FlatSaleRow, String>  colDate, colReceipt, colTotal, colProduct, colUnitPrice, colSubtotal;
    @FXML private TableColumn<FlatSaleRow, Integer> colQty;
    @FXML private DatePicker fromDate, toDate;
    @FXML private Label lblGrandTotal;

    private MainController mainController;
    private int shopId;

    public void setMainController(MainController mc) { this.mainController = mc; }

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().date.toString()));
        colReceipt.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().receipt));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(
            String.format("RWF %,.0f", d.getValue().saleTotal)));
        colProduct.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().product));
        colQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().qty).asObject());
        colUnitPrice.setCellValueFactory(d -> new SimpleStringProperty(
            String.format("RWF %,.0f", d.getValue().unitPrice)));
        colSubtotal.setCellValueFactory(d -> new SimpleStringProperty(
            String.format("RWF %,.0f", d.getValue().getSubtotal())));

        fromDate.setValue(LocalDate.now().withDayOfMonth(1));
        toDate.setValue(LocalDate.now());
    }

    public void refresh(int shopId) { this.shopId = shopId; loadSales(); }

    @FXML private void handleFilter() { loadSales(); }

    private void loadSales() {
        LocalDate from = fromDate.getValue() != null ? fromDate.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate to   = toDate.getValue()   != null ? toDate.getValue()   : LocalDate.now();

        Task<List<FlatSaleRow>> task = new Task<>() {
            protected List<FlatSaleRow> call() {
                return DatabaseManager.getInstance().getFlatSaleRows(shopId, from, to);
            }
        };
        task.setOnSucceeded(e -> {
            List<FlatSaleRow> rows = task.getValue();
            salesTable.setItems(FXCollections.observableArrayList(rows));
            // Grand total = sum of distinct sale totals (not sum of subtotals to avoid duplicates)
            double total = rows.stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.receipt, r -> r.saleTotal, (a, b) -> a))
                .values().stream().mapToDouble(java.math.BigDecimal::doubleValue).sum();
            double usdRate = getUsdRate();
            lblGrandTotal.setText(String.format("Period Total: RWF %,.0f  (USD %.2f)", total, total / usdRate));
        });
        new Thread(task, "history-loader").start();
    }

    @FXML private void handleExportCsv() {
        LocalDate from = fromDate.getValue() != null ? fromDate.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate to   = toDate.getValue()   != null ? toDate.getValue()   : LocalDate.now();
        List<com.stockmanager.model.Sale> sales = DatabaseManager.getInstance().getSalesByShop(shopId, from, to);
        com.stockmanager.util.CsvExporter.exportSales(sales, shopId);
    }

    private double getUsdRate() {
        String r = DatabaseManager.getInstance().getSetting("usd_rate");
        return r != null ? Double.parseDouble(r) : 1380;
    }
}

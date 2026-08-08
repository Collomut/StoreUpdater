package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.db.DatabaseManager.FlatSaleRow;
import com.stockmanager.util.Session;
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
    @FXML private TableColumn<FlatSaleRow, String>  colDate, colReceipt, colTotal, colProduct, colUnitPrice, colSubtotal, colPayment;
    @FXML private TableColumn<FlatSaleRow, Integer> colQty;
    @FXML private TableColumn<FlatSaleRow, String>  colActions;
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
        colPayment.setCellValueFactory(d -> new SimpleStringProperty(
            "PHONE".equals(d.getValue().paymentMethod) ? "Phone" : "Cash"));

        // Delete column — admin only
        if (colActions != null) {
            boolean isAdmin = Session.isAdmin();
            colActions.setVisible(isAdmin);

            colActions.setCellFactory(col -> new TableCell<>() {
                private final Button btnDelete = new Button("Delete Sale");
                {
                    btnDelete.setStyle(
                        "-fx-background-color:#FFF0F0;-fx-text-fill:#CC0000;" +
                        "-fx-background-radius:4;-fx-border-color:#CC0000;" +
                        "-fx-border-radius:4;-fx-border-width:1;-fx-cursor:hand;-fx-font-size:11px;");
                    btnDelete.setOnAction(e -> {
                        FlatSaleRow row = getTableView().getItems().get(getIndex());
                        if (row.saleId <= 0) {
                            new Alert(Alert.AlertType.WARNING,
                                "Cannot delete: sale ID not available. Please refresh the page.",
                                ButtonType.OK).showAndWait();
                            return;
                        }
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Delete sale " + row.receipt + "?\n\n" +
                            "This will REMOVE the entire sale and RESTORE the product quantities to inventory.\n" +
                            "This action cannot be undone.",
                            ButtonType.YES, ButtonType.NO);
                        confirm.setTitle("Confirm Sale Deletion");
                        confirm.setHeaderText("Delete Sale & Restore Inventory?");
                        confirm.showAndWait().ifPresent(bt -> {
                            if (bt == ButtonType.YES) {
                                boolean ok = DatabaseManager.getInstance().deleteSale(row.saleId);
                                if (ok) {
                                    new Alert(Alert.AlertType.INFORMATION,
                                        "Sale " + row.receipt + " deleted. Inventory has been restored.",
                                        ButtonType.OK).showAndWait();
                                    loadSales();
                                } else {
                                    new Alert(Alert.AlertType.ERROR,
                                        "Failed to delete sale. Please check your connection and try again.",
                                        ButtonType.OK).showAndWait();
                                }
                            }
                        });
                    });
                }
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : btnDelete);
                }
            });
        }

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

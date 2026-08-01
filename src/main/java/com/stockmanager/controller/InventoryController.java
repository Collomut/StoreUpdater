package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.Product;
import com.stockmanager.util.Session;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.List;
import java.util.stream.Collectors;

public class InventoryController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, String>  colName, colCategory, colUnit, colStatus;
    @FXML private TableColumn<Product, Integer> colQty;
    @FXML private TableColumn<Product, java.math.BigDecimal>  colPrice;
    @FXML private TextField searchField;
    @FXML private Label lblTotal, lblLowStock;

    // Fix 3: buttons hidden for workers
    @FXML private Button btnAdd, btnEdit, btnDelete;

    private MainController mainController;
    private int     shopId;
    private boolean isNyabugogo = false;
    private List<Product> allProducts = List.of();

    public void setMainController(MainController mc) { this.mainController = mc; }

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unit"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("sellingPrice"));

        colPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(java.math.BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("RWF %,.0f", item.doubleValue()));
            }
        });

        colStatus.setCellValueFactory(cd -> {
            Product p = cd.getValue();
            String status = p.isRetired() ? "RETIRED" : p.isLowStock() ? "LOW STOCK" : "OK";
            return new javafx.beans.property.SimpleStringProperty(status);
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "RETIRED"   -> "-fx-text-fill: #999999; -fx-font-style: italic;";
                    case "LOW STOCK" -> "-fx-text-fill: #CC0000; -fx-font-weight: bold;";
                    default          -> "-fx-text-fill: #007700;";
                });
            }
        });

        productTable.setRowFactory(tv -> new TableRow<>() {
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setStyle(""); return; }
                if (p.isRetired())       setStyle("-fx-background-color: #F5F5F5; -fx-opacity: 0.75;");
                else if (p.isLowStock()) setStyle("-fx-background-color: #FFF0F0;");
                else                     setStyle("");
            }
        });


        searchField.textProperty().addListener((obs, o, n) -> applySearch(n));

        // Fix 3: Hide write buttons for workers
        boolean isAdmin = Session.isAdmin();
        if (btnAdd    != null) { btnAdd.setVisible(isAdmin);    btnAdd.setManaged(isAdmin); }
        if (btnEdit   != null) { btnEdit.setVisible(isAdmin);   btnEdit.setManaged(isAdmin); }
        if (btnDelete != null) { btnDelete.setVisible(isAdmin); btnDelete.setManaged(isAdmin); }
    }

    // M-4: shopName is now passed from MainController's cached shop list
    // to avoid making an extra getAllShops() HTTP call on every tab switch/auto-refresh.
    public void refresh(int shopId, String shopName) {
        this.shopId = shopId;
        isNyabugogo = shopName != null && shopName.toLowerCase().contains("nyabugogo");
        applyColumnModes();
        loadProductsAsync();
    }

    /**
     * For Nyabugogo: rename columns and show full product specification in the Name column.
     * Pattern: "Regular Beads — Blue (Size 10)" / "Crystal Tec (Size 0.6mm)" / "Hat — Original"
     * For all other shops: restore standard column headers and plain name.
     */
    private void applyColumnModes() {
        if (isNyabugogo) {
            colCategory.setText("Color / Variant");
            colUnit.setText("Size / Spec");
            colName.setCellValueFactory(cd -> {
                Product p = cd.getValue();
                String color = (p.getCategory() != null && !p.getCategory().isBlank())
                    ? p.getCategory() : "";
                String size  = (p.getUnit() != null && !p.getUnit().isBlank())
                    ? p.getUnit() : "";
                String spec = p.getName()
                    + (color.isEmpty() ? "" : " \u2014 " + color)
                    + (size.isEmpty()  ? "" : " (Size " + size + ")");
                return new javafx.beans.property.SimpleStringProperty(spec);
            });
        } else {
            colCategory.setText("Category");
            colUnit.setText("Unit");
            colName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        }
    }

    /** True when a product's unit is a plain integer (legacy bead size convention). */
    private boolean isBeadProduct(Product p) {
        return p.getUnit() != null && p.getUnit().matches("\\d+");
    }


    private void loadProductsAsync() {
        Task<List<Product>> task = new Task<>() {
            protected List<Product> call() { return DatabaseManager.getInstance().getProductsByShop(shopId); }
        };
        task.setOnSucceeded(e -> {
            allProducts = task.getValue();
            productTable.setItems(FXCollections.observableArrayList(allProducts));
            updateStats();
        });
        task.setOnFailed(e -> System.err.println("Inventory load failed: " + task.getException()));
        new Thread(task, "inventory-loader").start();
    }

    private void applySearch(String query) {
        if (query == null || query.isBlank()) {
            productTable.setItems(FXCollections.observableArrayList(allProducts)); return;
        }
        String q = query.toLowerCase();
        productTable.setItems(FXCollections.observableArrayList(
            allProducts.stream().filter(p -> {
                if (p.getName().toLowerCase().contains(q)) return true;
                if (p.getCategory() != null && p.getCategory().toLowerCase().contains(q)) return true;
                // Also match size/unit (especially useful for beads)
                if (p.getUnit() != null && p.getUnit().toLowerCase().contains(q)) return true;
                return false;
            }).collect(java.util.stream.Collectors.toList())));
    }

    private void updateStats() {
        lblTotal.setText("Total products: " + allProducts.size());
        long low = allProducts.stream().filter(Product::isLowStock).count();
        lblLowStock.setText("Low stock: " + low);
        lblLowStock.setStyle(low > 0
            ? "-fx-text-fill: #CC0000; -fx-font-weight: bold;" : "-fx-text-fill: #000000;");
    }

    @FXML private void handleAdd() {
        String shopName = DatabaseManager.getInstance().getAllShops().stream()
            .filter(s -> s.getId() == shopId).map(s -> s.getName()).findFirst().orElse("");
        ProductDialog dialog = new ProductDialog(null, shopId, shopName);
        dialog.showAndWait().ifPresent(p -> { DatabaseManager.getInstance().addProduct(p); loadProductsAsync(); });
    }

    @FXML private void handleEdit() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Please select a product to edit."); return; }
        String shopName = DatabaseManager.getInstance().getAllShops().stream()
            .filter(s -> s.getId() == shopId).map(s -> s.getName()).findFirst().orElse("");
        ProductDialog dialog = new ProductDialog(selected, shopId, shopName);
        dialog.showAndWait().ifPresent(p -> { DatabaseManager.getInstance().updateProduct(p); loadProductsAsync(); });
    }

    @FXML private void handleDelete() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showAlert("Please select a product to delete."); return; }

        DatabaseManager db = DatabaseManager.getInstance();

        if (db.productHasSales(selected.getId())) {
            // FK would block deletion — offer Retire instead
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Product Has Sales History");
            alert.setHeaderText("\"" + selected.getName() + "\" cannot be fully deleted");
            alert.setContentText(
                "This product appears in sales records.\n" +
                "Deleting it would break your sales history and reports.\n\n" +
                "Instead, you can RETIRE it — this sets the quantity to 0\n" +
                "so it no longer shows up for sale, but history is preserved.");
            javafx.scene.control.ButtonType btnRetire =
                new javafx.scene.control.ButtonType("Retire (Set Qty to 0)");
            javafx.scene.control.ButtonType btnCancel =
                new javafx.scene.control.ButtonType("Cancel",
                    javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(btnRetire, btnCancel);
            alert.showAndWait().ifPresent(bt -> {
                if (bt == btnRetire) { db.retireProduct(selected.getId()); loadProductsAsync(); }
            });
        } else {
            // No sales history — safe to hard-delete
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\"? This cannot be undone.",
                ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirm Delete"); confirm.setHeaderText(null);
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) { db.deleteProduct(selected.getId()); loadProductsAsync(); }
            });
        }
    }


    @FXML private void handleExportCsv() { com.stockmanager.util.CsvExporter.exportProducts(allProducts, shopId); }

    private void showAlert(String msg) { new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK).showAndWait(); }
}

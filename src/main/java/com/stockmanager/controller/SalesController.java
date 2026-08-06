package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.util.List;


public class SalesController {

    @FXML private ComboBox<Product> productCombo;
    @FXML private ComboBox<String>  sizeFilterCombo;
    @FXML private HBox              sizeFilterRow;
    @FXML private TextField         productSearchField;
    @FXML private TextField         qtyField;
    @FXML private javafx.scene.layout.VBox priceOverrideRow;
    @FXML private TextField         salePriceField;
    @FXML private Label             lblGuidePrice;
    @FXML private TableView<SaleItem> basketTable;
    @FXML private TableColumn<SaleItem, String>  colProduct;
    @FXML private TableColumn<SaleItem, Integer> colQty;
    @FXML private TableColumn<SaleItem, java.math.BigDecimal>  colUnitPrice, colSubtotal;
    @FXML private Label lblTotal, lblTotalUsd;
    @FXML private TextField notesField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> paymentMethodCombo;

    private MainController mainController;
    private int    shopId;
    private String shopName     = "";
    private boolean isDowntown  = false;
    private final ObservableList<SaleItem> basketItems  = FXCollections.observableArrayList();
    private       List<Product>            shopProducts = new java.util.ArrayList<>();

    public void setMainController(MainController mc) { this.mainController = mc; }

    @FXML
    public void initialize() {
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));
        colUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colSubtotal.setCellValueFactory(cd ->
            new javafx.beans.property.SimpleObjectProperty<>(cd.getValue().getSubtotal()));

        colUnitPrice.setCellFactory(c -> new TableCell<>() {
            protected void updateItem(java.math.BigDecimal v, boolean e) {
                super.updateItem(v, e); setText(e || v == null ? null : String.format("RWF %,.0f", v.doubleValue()));
            }
        });
        colSubtotal.setCellFactory(c -> new TableCell<>() {
            protected void updateItem(java.math.BigDecimal v, boolean e) {
                super.updateItem(v, e); setText(e || v == null ? null : String.format("RWF %,.0f", v.doubleValue()));
            }
        });

        // Combo cell: shows color+size for beads, plain name+price for others
        productCombo.setCellFactory(lv -> new ListCell<>() {
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : buildComboLabel(p));
            }
        });
        productCombo.setButtonCell(new ListCell<>() {
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : buildShortLabel(p));
            }
        });

        basketTable.setItems(basketItems);
        datePicker.setValue(LocalDate.now());
        qtyField.setText("1");
        paymentMethodCombo.setItems(FXCollections.observableArrayList("Cash", "Phone"));
        paymentMethodCombo.setValue("Cash");

        // Register size-filter listener once here — NOT inside setupSizeFilter()
        // so it doesn't get re-added on every shop refresh
        sizeFilterCombo.setOnAction(ev -> applyFilters());

        // Live search
        productSearchField.textProperty().addListener((obs, o, n) -> applyFilters());

        // When product is selected, auto-fill the bargain price field (Downtown only)
        productCombo.valueProperty().addListener((obs, old, selected) -> {
            if (isDowntown && selected != null) {
                salePriceField.setText(String.valueOf(selected.getSellingPrice().intValue()));
                lblGuidePrice.setText(String.format("(guide: RWF %,.0f)",
                    selected.getSellingPrice().doubleValue()));
            } else if (isDowntown) {
                salePriceField.clear();
                lblGuidePrice.setText("(guide price shown)");
            }
        });
    }

    public void refresh(int shopId) {
        boolean shopChanged = (this.shopId != shopId);
        this.shopId = shopId;
        this.shopName = DatabaseManager.getInstance().getAllShops().stream()
            .filter(s -> s.getId() == shopId)
            .map(com.stockmanager.model.Shop::getName)
            .findFirst().orElse("");
        this.isDowntown = shopName.toLowerCase().contains("downtown");

        // Show / hide the bargain price row
        priceOverrideRow.setVisible(isDowntown);
        priceOverrideRow.setManaged(isDowntown);
        if (!isDowntown) salePriceField.clear();

        Task<List<Product>> task = new Task<>() {
            protected List<Product> call() { return DatabaseManager.getInstance().getProductsByShop(shopId); }
        };
        task.setOnSucceeded(e -> {
            shopProducts = task.getValue();
            if (isNyabugogo()) {
                setupSizeFilter();   // builds size combo then calls applyFilters()
            } else {
                sizeFilterRow.setVisible(false);
                sizeFilterRow.setManaged(false);
                applyFilters();      // excludes retired products consistently
            }
            productCombo.setPromptText("Select a product...");
        });
        new Thread(task, "sales-products-loader").start();

        // Only clear the basket when the shop actually changes
        // (not on auto-refresh — that would wipe the basket mid-sale)
        if (shopChanged) {
            basketItems.clear();
            updateTotal();
        }
    }

    /** Build size/spec filter from all products' actual units — no hardcoded values. */
    private void setupSizeFilter() {
        // Collect every distinct non-empty unit across non-retired products
        java.util.LinkedHashSet<String> unitSet = new java.util.LinkedHashSet<>();
        for (Product p : shopProducts) {
            if (!p.isRetired() && p.getUnit() != null && !p.getUnit().isBlank()) {
                unitSet.add(p.getUnit());
            }
        }

        // Sort: try numeric value first, then alphabetic
        List<String> sorted = unitSet.stream()
            .sorted((a, b) -> {
                try {
                    double da = Double.parseDouble(a.replaceAll("[^0-9.]", ""));
                    double db = Double.parseDouble(b.replaceAll("[^0-9.]", ""));
                    return Double.compare(da, db);
                } catch (NumberFormatException e) { return a.compareTo(b); }
            })
            .collect(java.util.stream.Collectors.toList());

        ObservableList<String> options = FXCollections.observableArrayList();
        options.add("All Sizes / Types");
        options.addAll(sorted);

        sizeFilterCombo.setItems(options);
        sizeFilterCombo.setValue("All Sizes / Types");
        sizeFilterRow.setVisible(true);
        sizeFilterRow.setManaged(true);

        // setOnAction already registered in initialize() — don't add again here
        applyFilters();
    }

    /**
     * Unified filter: size/spec filter + search text, applied together.
     * Size filter: applies to any product that HAS a unit value.
     * Products with no unit (hats, crosses etc.) always pass the size filter.
     */
    private void applyFilters() {
        // Remember what the user already selected — we'll restore it if it still matches
        Product previousSelection = productCombo.getValue();

        String sizeSelected = isNyabugogo() ? sizeFilterCombo.getValue() : null;
        String searchText   = productSearchField.getText();
        String search       = (searchText == null) ? "" : searchText.trim().toLowerCase();
        boolean sizeActive  = sizeSelected != null && !sizeSelected.equals("All Sizes / Types");

        List<Product> filtered = shopProducts.stream()
            .filter(p -> {
                if (p.isRetired()) return false;
                // Size/spec filter — only applies to products that have a unit
                if (sizeActive) {
                    String unit = p.getUnit();
                    if (unit != null && !unit.isBlank()) {
                        if (!sizeSelected.equals(unit)) return false;
                    }
                    // Products with no unit (hats, crosses) always show through
                }
                // Text search — matches name, colour (category) or unit
                if (!search.isEmpty()) {
                    boolean matchName = p.getName().toLowerCase().contains(search);
                    boolean matchCat  = p.getCategory() != null &&
                                       p.getCategory().toLowerCase().contains(search);
                    boolean matchUnit = p.getUnit() != null &&
                                       p.getUnit().toLowerCase().contains(search);
                    if (!matchName && !matchCat && !matchUnit) return false;
                }
                return true;
            })
            .collect(java.util.stream.Collectors.toList());

        productCombo.setItems(FXCollections.observableArrayList(filtered));
        productCombo.setPromptText(filtered.isEmpty() ? "No products match" : "Select a product...");

        // Restore the previous selection only if it still passes the current filters.
        // Never force-clear the combo — let the user keep their selection while they
        // are refining the filter, which prevents the frustrating mid-flow resets.
        if (previousSelection != null && filtered.contains(previousSelection)) {
            productCombo.setValue(previousSelection);
        }
        // If previousSelection is not in filtered list, JavaFX clears it automatically
        // when setItems() is called — no explicit setValue(null) needed.
    }



    @FXML
    private void handleAddToBasket() {
        Product selected = productCombo.getValue();
        if (selected == null) { showAlert("Please select a product."); return; }
        int qty;
        try { qty = Integer.parseInt(qtyField.getText().trim()); }
        catch (NumberFormatException e) { showAlert("Enter a valid quantity."); return; }
        if (qty <= 0) { showAlert("Quantity must be greater than 0."); return; }
        if (qty > selected.getQuantity()) {
            showAlert("Not enough stock. Available: " + selected.getQuantity() + " " + selected.getUnit()); return;
        }
        for (SaleItem item : basketItems) {
            if (item.getProductId() == selected.getId()) {
                // For Downtown, the same product CAN be added again at a different price
                // so only merge if it's NOT downtown
                if (!isDowntown) {
                    item.setQuantitySold(item.getQuantitySold() + qty);
                    basketTable.refresh(); updateTotal(); return;
                }
            }
        }

        // Determine the price: Downtown uses the editable field, others use product price
        java.math.BigDecimal unitPrice;
        if (isDowntown) {
            try {
                double val = Double.parseDouble(salePriceField.getText().trim());
                if (val <= 0) throw new NumberFormatException();
                unitPrice = java.math.BigDecimal.valueOf(val);
            } catch (NumberFormatException ex) {
                showAlert("Please enter a valid sale price."); return;
            }
        } else {
            unitPrice = selected.getSellingPrice();
        }

        String productName = buildShortLabel(selected);
        basketItems.add(new SaleItem(0, selected.getId(), productName, qty, unitPrice));
        updateTotal();
        productCombo.setValue(null);
        qtyField.setText("1");
        if (isDowntown) salePriceField.clear();
    }

    // ── Helpers: bead-aware label builders ────────────────────────────────────

    private boolean isNyabugogo() {
        return shopName.toLowerCase().contains("nyabugogo");
    }

    /** Returns true if this product looks like a bead (unit is a plain number) */
    private boolean isBead(Product p) {
        return p.getUnit() != null && p.getUnit().matches("\\d+");
    }

    /** Full combo-dropdown label: shows color and size for ALL Nyabugogo products */
    private String buildComboLabel(Product p) {
        if (isNyabugogo()) {
            String color = (p.getCategory() != null && !p.getCategory().isBlank())
                ? p.getCategory() : "";
            String size  = (p.getUnit() != null && !p.getUnit().isBlank())
                ? p.getUnit() : "";
            return p.getName()
                + (color.isEmpty() ? "" : " — " + color)
                + (size.isEmpty()  ? "" : " (" + size + ")")
                + "  |  RWF " + String.format("%,.0f", p.getSellingPrice().doubleValue())
                + "  (stock: " + p.getQuantity() + ")";
        }
        return p.getName()
            + "  —  RWF " + String.format("%,.0f", p.getSellingPrice().doubleValue())
            + "  (stock: " + p.getQuantity() + ")";
    }

    /** Short label used in basket column and stored as productName in SaleItem */
    private String buildShortLabel(Product p) {
        if (isNyabugogo()) {
            String color = (p.getCategory() != null && !p.getCategory().isBlank())
                ? p.getCategory() : "";
            String size  = (p.getUnit() != null && !p.getUnit().isBlank())
                ? p.getUnit() : "";
            return p.getName()
                + (color.isEmpty() ? "" : " — " + color)
                + (size.isEmpty()  ? "" : " (" + size + ")");
        }
        return p.getName();
    }

    @FXML private void handleRemoveFromBasket() {
        SaleItem sel = basketTable.getSelectionModel().getSelectedItem();
        if (sel != null) { basketItems.remove(sel); updateTotal(); }
    }

    @FXML
    private void handleConfirmSale() {
        if (basketItems.isEmpty()) { showAlert("Your basket is empty."); return; }
        Sale sale = new Sale();
        sale.setShopId(shopId);
        sale.setSaleDate(datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now());
        sale.setNotes(notesField.getText().trim());
        String selectedMethod = paymentMethodCombo.getValue();
        sale.setPaymentMethod("Phone".equals(selectedMethod) ? "PHONE" : "CASH");
        basketItems.forEach(sale::addItem);
        sale.setTotalAmount(basketItems.stream()
            .map(SaleItem::getSubtotal)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));

        int saleId = DatabaseManager.getInstance().saveSale(sale);
        if (saleId > 0) {
            // Fix 7: no receipt printing
            showSuccess("Sale saved! Receipt: " + sale.getReceiptNumber());
            basketItems.clear();
            notesField.clear();
            datePicker.setValue(LocalDate.now());
            updateTotal();
            // Refresh product list and re-apply filters (keeps retired products hidden)
            shopProducts = DatabaseManager.getInstance().getProductsByShop(shopId);
            applyFilters();
        } else {
            showAlert("Failed to save sale. Please try again.");
        }
    }

    @FXML private void handleClearBasket() { basketItems.clear(); updateTotal(); }

    private void updateTotal() {
        double total = basketItems.stream().mapToDouble(item -> item.getSubtotal().doubleValue()).sum();
        double usdRate = getUsdRate();
        lblTotal.setText(String.format("Total: RWF %,.0f", total));
        lblTotalUsd.setText(String.format("  USD %.2f", total / usdRate));
    }

    private double getUsdRate() {
        String r = DatabaseManager.getInstance().getSetting("usd_rate");
        return r != null ? Double.parseDouble(r) : 1380;
    }

    private void showAlert(String msg)   { new Alert(Alert.AlertType.WARNING,     msg, ButtonType.OK).showAndWait(); }
    private void showSuccess(String msg) { new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait(); }
}

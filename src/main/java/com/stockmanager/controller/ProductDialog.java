package com.stockmanager.controller;

import com.stockmanager.model.Product;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Product add/edit dialog.
 *
 * ── Non-Nyabugogo shops ──────────────────────────────────────────────────────
 *   Simple form: Name | Category | Unit | Qty | Price
 *
 * ── Nyabugogo shop ───────────────────────────────────────────────────────────
 *   Category-driven cascading form:
 *     Category  → [Beads | Strings | Rosary Bracelets | Hats | Crosses]
 *     Type      → changes based on category
 *     Color     → preset dropdown (editable) — shown when applicable
 *     Size      → preset dropdown (editable) — shown when applicable
 *     Qty | Price
 */
public class ProductDialog extends Dialog<Product> {

    // ── Simple (non-Nyabugogo) fields ─────────────────────────────────────────
    private final TextField sfName     = new TextField();
    private final TextField sfCategory = new TextField();
    private final TextField sfUnit     = new TextField();
    private final TextField sfQty      = new TextField();
    private final TextField sfPrice    = new TextField();

    // ── Nyabugogo structured fields ───────────────────────────────────────────
    private final ComboBox<String> nyCatCombo     = new ComboBox<>();
    private final ComboBox<String> nySubTypeCombo = new ComboBox<>();
    private final ComboBox<String> nyColorCombo   = new ComboBox<>();
    private final ComboBox<String> nySizeCombo    = new ComboBox<>();
    private final TextField        nyQtyField     = new TextField();
    private final TextField        nyPriceField   = new TextField();
    private HBox nyColorRow, nySizeRow;
    /** True while pre-filling the form — suppresses cascade listeners. */
    private boolean populatingFields = false;

    // ── Catalogue data ────────────────────────────────────────────────────────
    private static final LinkedHashMap<String, List<String>> CATEGORIES    = new LinkedHashMap<>();
    private static final HashMap<String,  List<String>>      PRESET_COLORS = new HashMap<>();
    private static final HashMap<String,  List<String>>      PRESET_SIZES  = new HashMap<>();
    private static final Set<String> NO_COLOR_TYPES;
    private static final Set<String> HAS_SIZE_TYPES;

    static {
        // ── Main categories and their sub-types ───────────────────────────────
        CATEGORIES.put("Beads", Arrays.asList(
            "Regular Beads", "Measured Beads", "Bag Beads", "Wood Beads",
            "Rosary Beads (With Cross)", "Rosary Beads (Without Cross)",
            "Diamond Beads", "Alphabet Beads"));
        CATEGORIES.put("Strings", Arrays.asList(
            "Strong & Stretchy", "Crystal Tec", "Fishing Line", "Fishing Twine", "24 Gauge"));
        CATEGORIES.put("Rosary Bracelets", Collections.singletonList("Rosary Bracelet"));
        CATEGORIES.put("Hats",             Collections.singletonList("Hat"));
        CATEGORIES.put("Crosses",          Collections.singletonList("Cross"));
        CATEGORIES.put("Needles",          Arrays.asList("Needle"));

        // ── Preset colors / variants per sub-type ─────────────────────────────
        // (Regular Beads and Alphabet Beads: editable, no fixed presets)
        PRESET_COLORS.put("Bag Beads",
            Arrays.asList("Brown", "Black", "White", "Maroon"));
        PRESET_COLORS.put("Wood Beads",
            Arrays.asList("Brown", "Black"));
        PRESET_COLORS.put("Rosary Beads (With Cross)",
            Arrays.asList("Blue", "Brown", "White",
                "White with Black Cross", "White with Silver Cross", "Multicolor"));
        PRESET_COLORS.put("Rosary Beads (Without Cross)",
            Arrays.asList("Blue", "Green", "White", "Brown", "Red"));
        PRESET_COLORS.put("Diamond Beads",
            Arrays.asList("Black", "Brown"));
        PRESET_COLORS.put("Strong & Stretchy",
            Arrays.asList("Black", "Brown"));
        PRESET_COLORS.put("Fishing Twine",
            Arrays.asList("White", "Brown"));
        PRESET_COLORS.put("Rosary Bracelet",
            Arrays.asList("Black", "Brown", "Silver"));
        PRESET_COLORS.put("Hat",
            Arrays.asList("Original", "Local"));
        PRESET_COLORS.put("Cross",
            Arrays.asList("Brown", "Metal"));

        // ── Preset sizes per sub-type ─────────────────────────────────────────
        PRESET_SIZES.put("Regular Beads",
            Arrays.asList("1", "2", "3", "6", "8", "10", "18", "20"));
        PRESET_SIZES.put("Crystal Tec",
            Arrays.asList("0.6mm", "0.7mm", "1.0mm"));
        PRESET_SIZES.put("Fishing Line",
            Arrays.asList("0.3mm", "0.4mm", "0.6mm", "0.7mm"));
        // Fishing Twine sizes depend on colour
        PRESET_SIZES.put("Fishing Twine_White",  Arrays.asList("2mm", "3mm"));
        PRESET_SIZES.put("Fishing Twine_Brown",  Arrays.asList("24mm", "36mm"));
        // Measured Beads — sold by weight (grams)
        PRESET_SIZES.put("Measured Beads",       Arrays.asList("50g", "100g"));
        // Needle sizes
        PRESET_SIZES.put("Needle",               Arrays.asList("58", "78"));

        // No colour field for these (size is the only distinguisher)
        NO_COLOR_TYPES = Set.of("Crystal Tec", "Fishing Line", "24 Gauge", "Needle");

        // These sub-types have a size field
        HAS_SIZE_TYPES = Set.of("Regular Beads", "Measured Beads", "Crystal Tec", "Fishing Line", "Fishing Twine", "Needle");
    }

    private final Product existingProduct;
    private final int     shopId;
    private final boolean isNyabugogo;

    // ─────────────────────────────────────────────────────────────────────────
    public ProductDialog(Product existing, int shopId, String shopName) {
        this.existingProduct = existing;
        this.shopId          = shopId;
        this.isNyabugogo     = shopName != null && shopName.toLowerCase().contains("nyabugogo");

        setTitle(existing == null ? "Add Product" : "Edit Product");
        setHeaderText(null);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));

        if (isNyabugogo) buildNyabugogoUI(root, existing);
        else             buildSimpleUI(root, existing);

        // Wrap in a scroll pane so fields are never cut off on small screens
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        getDialogPane().setContent(scroll);
        getDialogPane().setPrefWidth(500);
        getDialogPane().setPrefHeight(480);
        getDialogPane().setMinHeight(380);
        try {
            getDialogPane().getStylesheets().add(
                getClass().getResource("/css/styles.css").toExternalForm());
        } catch (Exception ignored) {}

        // ── Result converter ─────────────────────────────────────────────────
        setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            try {
                Product p = existingProduct != null ? existingProduct : new Product();
                if (existingProduct == null) {
                    p.setShopId(shopId);
                    p.setCostPrice(BigDecimal.ZERO);
                    p.setReorderLevel(5);
                }

                if (isNyabugogo) {
                    // ── Nyabugogo structured form ─────────────────────────────
                    String subType = nySubTypeCombo.getValue();
                    if (subType == null || subType.isBlank())
                        throw new IllegalArgumentException("Please select a product type.");
                    p.setName(subType);
                    // For editable combos, read the editor text — captures typed
                    // values even when the user didn't press Enter to commit
                    String color = "";
                    if (nyColorRow != null && nyColorRow.isVisible()) {
                        color = nyColorCombo.getEditor() != null
                            ? nyColorCombo.getEditor().getText().trim()
                            : (nyColorCombo.getValue() != null ? nyColorCombo.getValue().trim() : "");
                    }
                    String size = "";
                    if (nySizeRow != null && nySizeRow.isVisible()) {
                        size = nySizeCombo.getEditor() != null
                            ? nySizeCombo.getEditor().getText().trim()
                            : (nySizeCombo.getValue() != null ? nySizeCombo.getValue().trim() : "");
                    }
                    p.setCategory(color);
                    p.setUnit(size);
                    p.setQuantity(Integer.parseInt(nyQtyField.getText().trim()));
                    p.setSellingPrice(new java.math.BigDecimal(nyPriceField.getText().trim()));

                } else {
                    // ── Simple form ────────────────────────────────────────────
                    String name = sfName.getText().trim();
                    if (name.isEmpty()) throw new IllegalArgumentException("Product name is required.");
                    p.setName(name);
                    p.setCategory(sfCategory.getText().trim());
                    String u = sfUnit.getText().trim();
                    p.setUnit(u.isEmpty() ? "pcs" : u);
                    p.setQuantity(Integer.parseInt(sfQty.getText().trim()));
                    p.setSellingPrice(new java.math.BigDecimal(sfPrice.getText().trim()));
                }

                // Auto-unretire: if qty restored, reset reorder_level sentinel
                if (p.getReorderLevel() < 0 && p.getQuantity() > 0) {
                    p.setReorderLevel(5);
                }

                return p;

            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR,
                    "Please enter valid numbers for Quantity and Price.",
                    ButtonType.OK).showAndWait();
            } catch (IllegalArgumentException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage(),
                    ButtonType.OK).showAndWait();
            }
            return null;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Simple dialog (all non-Nyabugogo shops)
    // ─────────────────────────────────────────────────────────────────────────
    private void buildSimpleUI(VBox root, Product existing) {
        sfName.setPromptText("e.g. Nike Air Max");
        sfCategory.setPromptText("e.g. Sneakers  (optional)");
        sfUnit.setPromptText("e.g. pairs  (optional)");
        sfQty.setPromptText("0");
        sfPrice.setPromptText("0");

        if (existing != null) {
            sfName.setText(existing.getName());
            sfCategory.setText(nvl(existing.getCategory()));
            sfUnit.setText(nvl(existing.getUnit()));
            sfQty.setText(String.valueOf(existing.getQuantity()));
            sfPrice.setText(String.valueOf(existing.getSellingPrice().intValue()));

            if (existing.isRetired()) root.getChildren().add(0, retiredBanner());
        }

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        addRow(g, 0, "Product Name *",        sfName);
        addRow(g, 1, "Category",              sfCategory);
        addRow(g, 2, "Unit",                  sfUnit);
        addRow(g, 3, "Quantity *",            sfQty);
        addRow(g, 4, "Selling Price (RWF) *", sfPrice);
        root.getChildren().add(g);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nyabugogo category-driven dialog
    // ─────────────────────────────────────────────────────────────────────────
    private void buildNyabugogoUI(VBox root, Product existing) {
        if (existing != null && existing.isRetired())
            root.getChildren().add(retiredBanner());

        // ── Category ─────────────────────────────────────────────────────────
        nyCatCombo.getItems().addAll(CATEGORIES.keySet());
        nyCatCombo.setPromptText("Select category...");
        nyCatCombo.setMaxWidth(Double.MAX_VALUE);

        // ── Sub-type ──────────────────────────────────────────────────────────
        nySubTypeCombo.setPromptText("Select type...");
        nySubTypeCombo.setMaxWidth(Double.MAX_VALUE);

        // ── Color (editable) ──────────────────────────────────────────────────
        nyColorCombo.setEditable(true);
        nyColorCombo.setPromptText("Select or type color / variant...");
        nyColorCombo.setMaxWidth(Double.MAX_VALUE);

        // ── Size (editable) ───────────────────────────────────────────────────
        nySizeCombo.setEditable(true);
        nySizeCombo.setPromptText("Select or type size...");
        nySizeCombo.setMaxWidth(Double.MAX_VALUE);

        nyQtyField.setPromptText("0");
        nyPriceField.setPromptText("0");

        // Build rows (color/size start hidden)
        nyColorRow = buildRow("Color / Variant", nyColorCombo);
        nySizeRow  = buildRow("Size",            nySizeCombo);
        showRow(nyColorRow, false);
        showRow(nySizeRow,  false);

        root.getChildren().addAll(
            buildRow("Category *",            nyCatCombo),
            buildRow("Type *",                nySubTypeCombo),
            nyColorRow,
            nySizeRow,
            buildRow("Quantity *",            nyQtyField),
            buildRow("Selling Price (RWF) *", nyPriceField)
        );

        // ── Cascading listeners ───────────────────────────────────────────────
        nyCatCombo.setOnAction(e -> {
            if (populatingFields) return;  // suppressed during pre-fill
            String cat = nyCatCombo.getValue();
            List<String> types = CATEGORIES.getOrDefault(cat, Collections.emptyList());
            nySubTypeCombo.getItems().setAll(types);
            nySubTypeCombo.setValue(types.size() == 1 ? types.get(0) : null);
            refreshOptionalRows();
        });

        nySubTypeCombo.setOnAction(e -> {
            if (populatingFields) return;  // suppressed during pre-fill
            refreshOptionalRows();
        });

        // Fishing Twine: colour determines available sizes
        nyColorCombo.setOnAction(e -> {
            if ("Fishing Twine".equals(nySubTypeCombo.getValue()))
                updateFishingTwineSizes();
        });

        // ── Pre-fill when editing ─────────────────────────────────────────────
        if (existing != null) populateNyaFields(existing);
    }

    /** Show/hide colour and size rows based on selected sub-type. */
    private void refreshOptionalRows() {
        String subType = nySubTypeCombo.getValue();
        if (subType == null) {
            showRow(nyColorRow, false);
            showRow(nySizeRow,  false);
            return;
        }

        boolean hasColor = !NO_COLOR_TYPES.contains(subType);
        boolean hasSize  = HAS_SIZE_TYPES.contains(subType);

        showRow(nyColorRow, hasColor);
        showRow(nySizeRow,  hasSize);

        if (hasColor) {
            List<String> presets = PRESET_COLORS.getOrDefault(subType, Collections.emptyList());
            String prev = nyColorCombo.getValue();
            nyColorCombo.getItems().setAll(presets);
            // Keep value if still valid
            if (prev != null && (presets.contains(prev) || presets.isEmpty())) {
                nyColorCombo.setValue(prev);
            }
        }

        if (hasSize) {
            if ("Fishing Twine".equals(subType)) {
                updateFishingTwineSizes();
            } else {
                List<String> sizes = PRESET_SIZES.getOrDefault(subType, Collections.emptyList());
                nySizeCombo.getItems().setAll(sizes);
            }
        }
    }

    /** Update size options for Fishing Twine based on selected colour. */
    private void updateFishingTwineSizes() {
        String color  = nyColorCombo.getValue();
        String key    = "Fishing Twine_" + (color != null ? color : "");
        List<String> sizes = PRESET_SIZES.getOrDefault(key, Collections.emptyList());
        nySizeCombo.getItems().setAll(sizes);
        nySizeCombo.setValue(null);
        showRow(nySizeRow, !sizes.isEmpty());
    }

    /**
     * Pre-fill the Nyabugogo form when editing an existing product.
     * Blocks all cascade listeners during fill to prevent race conditions
     * where refreshOptionalRows() hides rows before values are set.
     */
    private void populateNyaFields(Product existing) {
        populatingFields = true;
        try {
            String name = existing.getName();

            // 1. Find and set the main category
            for (Map.Entry<String, List<String>> entry : CATEGORIES.entrySet()) {
                if (entry.getValue().contains(name)) {
                    String cat = entry.getKey();
                    nyCatCombo.setValue(cat);
                    // Manually populate subtype list (listener is blocked)
                    List<String> types = CATEGORIES.getOrDefault(cat, Collections.emptyList());
                    nySubTypeCombo.getItems().setAll(types);
                    break;
                }
            }

            // 2. Set the sub-type
            nySubTypeCombo.setValue(name);

        } finally {
            populatingFields = false;
        }

        // 3. With listeners unblocked, call refreshOptionalRows() ONCE cleanly
        //    so colour/size rows are shown based on the correct sub-type
        refreshOptionalRows();

        // 4. Set colour and size — also write into editor so text is always visible
        if (nyColorRow.isVisible()) {
            String cv = nvl(existing.getCategory());
            nyColorCombo.setValue(cv);
            if (nyColorCombo.getEditor() != null) nyColorCombo.getEditor().setText(cv);
        }
        if (nySizeRow.isVisible()) {
            String sv = nvl(existing.getUnit());
            nySizeCombo.setValue(sv);
            if (nySizeCombo.getEditor() != null) nySizeCombo.getEditor().setText(sv);
        }

        nyQtyField.setText(String.valueOf(existing.getQuantity()));
        nyPriceField.setText(String.valueOf(existing.getSellingPrice().intValue()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** One-line row: Label (fixed width) + Control (grows). */
    private HBox buildRow(String labelText, Control control) {
        Label lbl = new Label(labelText);
        lbl.setMinWidth(175);
        lbl.setStyle("-fx-font-size:13px; -fx-text-fill:#111111;");
        control.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(12, lbl, control);
        HBox.setHgrow(control, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void showRow(HBox row, boolean show) {
        row.setVisible(show);
        row.setManaged(show);
    }

    /** Yellow retired warning banner. */
    private Label retiredBanner() {
        Label lbl = new Label(
            "⚠  This product is retired (qty = 0). " +
            "Enter a quantity > 0 and save to restore it.");
        lbl.setWrapText(true);
        lbl.setStyle(
            "-fx-background-color:#FFF3CD; -fx-text-fill:#856404;" +
            "-fx-padding:8 12; -fx-border-color:#FFECB5;" +
            "-fx-border-radius:4; -fx-background-radius:4; -fx-font-size:12px;");
        return lbl;
    }

    private void addRow(GridPane grid, int row, String label, TextField field) {
        Label lbl = new Label(label);
        lbl.setMinWidth(155);
        lbl.setStyle("-fx-font-size:13px; -fx-text-fill:#111111;");
        field.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
    }

    private String nvl(String s) { return s != null ? s : ""; }
}

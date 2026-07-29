package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.Product;
import com.stockmanager.model.Shop;
import com.stockmanager.model.User;
import com.stockmanager.util.PasswordUtil;
import com.stockmanager.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

public class SettingsController {

    // Change password
    @FXML private PasswordField fldCurrentPwd, fldNewPwd, fldConfirmPwd;

    // USD rate
    @FXML private TextField fldUsdRate;

    // Shops
    @FXML private TextField fldShopName, fldShopDesc;
    @FXML private VBox shopsContainer;

    // Users
    @FXML private VBox userMgmtSection;
    @FXML private TextField fldNewUsername;
    @FXML private PasswordField fldNewUserPwd;
    @FXML private ComboBox<String> cmbUserRole;
    @FXML private ComboBox<Shop> cmbUserShop;
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> colUsername, colRole, colShop, colActions;

    @FXML private Label lblStatus;

    private MainController mainController;
    private DatabaseManager db = DatabaseManager.getInstance();

    public void setMainController(MainController mc) { this.mainController = mc; }

    @FXML
    public void initialize() {
        // Only admins see user management
        if (userMgmtSection != null) {
            userMgmtSection.setVisible(Session.isAdmin());
            userMgmtSection.setManaged(Session.isAdmin());
        }

        // Role combo
        cmbUserRole.getItems().addAll("ADMIN", "WORKER");
        cmbUserRole.setValue("WORKER");

        // Shop combo for user assignment
        cmbUserRole.valueProperty().addListener((obs, o, n) -> {
            cmbUserShop.setDisable("ADMIN".equals(n));
        });

        // Users table columns
        colUsername.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        colRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole()));
        colShop.setCellValueFactory(d -> {
            Integer sid = d.getValue().getShopId();
            if (sid == null) return new SimpleStringProperty("All shops");
            return new SimpleStringProperty(db.getAllShops().stream()
                .filter(s -> s.getId() == sid).map(Shop::getName).findFirst().orElse("?"));
        });
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("Delete");
            private final Button btnReset  = new Button("Reset Pwd");
            private final HBox box = new HBox(8, btnReset, btnDelete);
            {
                btnDelete.setStyle("-fx-background-color:#FFF0F0;-fx-text-fill:#CC0000;-fx-background-radius:4;-fx-border-color:#CC0000;-fx-border-radius:4;-fx-border-width:1;-fx-cursor:hand;");
                btnReset.setStyle("-fx-background-color:#F8F8F8;-fx-text-fill:#000000;-fx-background-radius:4;-fx-border-color:#999;-fx-border-radius:4;-fx-border-width:1;-fx-cursor:hand;");
                btnDelete.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    if (u.getUsername().equals(Session.getUser().getUsername())) {
                        showStatus("You cannot delete your own account."); return;
                    }
                    if (db.deleteUser(u.getId())) { refresh(); showStatus("User deleted."); }
                });
                btnReset.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    TextInputDialog dlg = new TextInputDialog();
                    dlg.setTitle("Reset Password");
                    dlg.setHeaderText("Set new password for: " + u.getUsername());
                    dlg.setContentText("New password:");
                    dlg.showAndWait().ifPresent(pwd -> {
                        if (!pwd.isBlank()) {
                            db.changePassword(u.getId(), pwd);
                            showStatus("Password reset for " + u.getUsername());
                        }
                    });
                });
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    public void refresh() {
        String rate = db.getSetting("usd_rate");
        if (rate != null) fldUsdRate.setText(rate);

        // Load shops once — reuse across UI + colShop cell factory
        List<Shop> shops = db.getAllShops();
        loadShopsUI(shops);
        cmbUserShop.getItems().setAll(shops);
        if (!shops.isEmpty()) cmbUserShop.setValue(shops.get(0));

        if (Session.isAdmin()) {
            // Pass shops snapshot into the table for fast lookups
            refreshUsersTable(shops);
        }
    }

    private void refreshUsersTable(List<Shop> shops) {
        colShop.setCellValueFactory(d -> {
            Integer sid = d.getValue().getShopId();
            if (sid == null) return new SimpleStringProperty("All shops");
            return new SimpleStringProperty(shops.stream()
                .filter(s -> s.getId() == sid).map(Shop::getName).findFirst().orElse("?"));
        });
        usersTable.getItems().setAll(db.getAllUsers());
    }

    private void loadShopsUI(List<Shop> shops) {
        shopsContainer.getChildren().clear();
        for (Shop s : shops) {
            HBox row = new HBox(10);
            row.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 4 0;");
            TextField tName = new TextField(s.getName());
            tName.setStyle("-fx-background-color:#FFFFFF;-fx-border-color:#BBBBBB;-fx-border-radius:4;-fx-background-radius:4;-fx-border-width:1;-fx-text-fill:#000000;-fx-padding:6 10;");
            tName.setPrefWidth(180);
            TextField tDesc = new TextField(s.getDescription() != null ? s.getDescription() : "");
            tDesc.setStyle(tName.getStyle());
            HBox.setHgrow(tDesc, Priority.ALWAYS);
            Button btnSave = new Button("Save");
            btnSave.setStyle("-fx-background-color:#000000;-fx-text-fill:#FFFFFF;-fx-background-radius:4;-fx-padding:6 14;-fx-cursor:hand;");
            Button btnDel = new Button("Delete");
            btnDel.setStyle("-fx-background-color:#FFF0F0;-fx-text-fill:#CC0000;-fx-background-radius:4;-fx-border-color:#CC0000;-fx-border-radius:4;-fx-border-width:1;-fx-padding:6 14;-fx-cursor:hand;");
            btnSave.setOnAction(e -> {
                db.updateShop(s.getId(), tName.getText().trim(), tDesc.getText().trim());
                showStatus("Shop updated."); refresh(); mainController.reloadShops();
            });
            btnDel.setOnAction(e -> {
                if (db.shopHasData(s.getId())) {
                    Alert warn = new Alert(Alert.AlertType.WARNING,
                        "'" + s.getName() + "' still has products and/or sales.\n\n" +
                        "Deleting it will permanently erase ALL its products, sales history and data.\n" +
                        "This CANNOT be undone.\n\nAre you sure?",
                        ButtonType.YES, ButtonType.NO);
                    warn.setTitle("Delete All Data?"); warn.setHeaderText(null);
                    warn.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            if (db.deleteShopCascade(s.getId())) {
                                showStatus("Shop '" + s.getName() + "' and all its data deleted.");
                                refresh(); mainController.reloadShops();
                            } else { showStatus("Delete failed. Please try again."); }
                        }
                    });
                } else {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete shop '" + s.getName() + "'?", ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText(null);
                    confirm.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.YES) {
                            db.deleteShop(s.getId());
                            showStatus("Shop deleted."); refresh(); mainController.reloadShops();
                        }
                    });
                }
            });
            row.getChildren().addAll(tName, tDesc, btnSave, btnDel);
            shopsContainer.getChildren().add(row);
        }
    }

    @FXML
    private void handleChangePassword() {
        String current = fldCurrentPwd.getText();
        String newPwd  = fldNewPwd.getText();
        String confirm = fldConfirmPwd.getText();

        if (current.isBlank() || newPwd.isBlank() || confirm.isBlank()) {
            showStatus("Please fill in all password fields."); return;
        }
        if (!newPwd.equals(confirm)) {
            showStatus("New passwords do not match."); return;
        }
        if (newPwd.length() < 6) {
            showStatus("Password must be at least 6 characters."); return;
        }
        // Verify current password
        User u = db.authenticate(Session.getUser().getUsername(), current);
        if (u == null) { showStatus("Current password is incorrect."); return; }

        if (db.changePassword(Session.getUser().getId(), newPwd)) {
            showStatus("Password changed successfully.");
            fldCurrentPwd.clear(); fldNewPwd.clear(); fldConfirmPwd.clear();
        } else {
            showStatus("Failed to change password.");
        }
    }

    @FXML
    private void handleSaveUsdRate() {
        String rate = fldUsdRate.getText().trim();
        try {
            double r = Double.parseDouble(rate);
            if (r <= 0) throw new NumberFormatException();
            db.setSetting("usd_rate", rate);
            showStatus("USD rate saved: 1 USD = " + rate + " RWF");
        } catch (NumberFormatException e) {
            showStatus("Invalid rate. Enter a positive number.");
        }
    }

    @FXML
    private void handleAddShop() {
        String name = fldShopName.getText().trim();
        if (name.isBlank()) { showStatus("Shop name is required."); return; }
        if (db.addShop(name, fldShopDesc.getText().trim())) {
            showStatus("Shop added: " + name);
            fldShopName.clear(); fldShopDesc.clear();
            refresh(); mainController.reloadShops();
        } else {
            showStatus("Failed to add shop.");
        }
    }

    @FXML
    private void handleAddUser() {
        String username = fldNewUsername.getText().trim();
        String password = fldNewUserPwd.getText();
        String role     = cmbUserRole.getValue();
        Shop shop       = cmbUserShop.getValue();

        if (username.isBlank() || password.isBlank()) {
            showStatus("Username and password are required."); return;
        }
        if (password.length() < 6) {
            showStatus("Password must be at least 6 characters."); return;
        }
        Integer shopId = "ADMIN".equals(role) ? null : (shop != null ? shop.getId() : null);
        if ("WORKER".equals(role) && shopId == null) {
            showStatus("Please assign a shop to the worker."); return;
        }

        if (db.addUser(username, password, role, shopId)) {
            showStatus("User added: " + username);
            fldNewUsername.clear(); fldNewUserPwd.clear();
            usersTable.getItems().setAll(db.getAllUsers());
        } else {
            showStatus("Failed to add user. Username may already exist.");
        }
    }

    @FXML
    private void handleBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Backup");
        fc.setInitialFileName("stockmanager_backup_" + LocalDate.now() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fc.showSaveDialog(lblStatus.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            List<Shop> shops = db.getAllShops();

            // Products section
            pw.println("=== INVENTORY ===");
            pw.println("Shop,Product,Category,Unit,Quantity,Selling Price (RWF),Status");
            for (Shop s : shops) {
                List<Product> products = db.getProducts(s.getId());
                for (Product p : products) {
                    pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,%.0f,\"%s\"%n",
                        s.getName(), p.getName(), p.getCategory() != null ? p.getCategory() : "",
                        p.getUnit() != null ? p.getUnit() : "", p.getQuantity(),
                        p.getSellingPrice(), p.isLowStock() ? "LOW" : "OK");
                }
            }

            // Sales section (current month)
            pw.println();
            pw.println("=== SALES (This Month) ===");
            pw.println("Shop,Date,Receipt,Product,Qty,Unit Price (RWF),Subtotal (RWF)");
            LocalDate from  = LocalDate.now().withDayOfMonth(1);
            LocalDate today = LocalDate.now();
            for (Shop s : shops) {
                List<DatabaseManager.FlatSaleRow> rows = db.getFlatSaleRows(s.getId(), from, today);
                for (DatabaseManager.FlatSaleRow r : rows) {
                    pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,%.0f,%.0f%n",
                        s.getName(), r.date, r.receipt, r.product, r.qty, r.unitPrice, r.getSubtotal());
                }
            }

            showStatus("Backup saved to: " + file.getName());
        } catch (Exception e) {
            showStatus("Backup failed: " + e.getMessage());
        }
    }

    private void showStatus(String msg) { lblStatus.setText(msg); }
}

package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.Shop;
import com.stockmanager.util.Session;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;


public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Label shopNameLabel;
    @FXML private ComboBox<Shop> shopComboBox;
    @FXML private HBox shopSelectorBox;
    @FXML private ToggleButton btnDashboard;
    @FXML private ToggleButton btnInventory;
    @FXML private ToggleButton btnSales;
    @FXML private ToggleButton btnHistory;
    @FXML private ToggleButton btnExpenses;
    @FXML private ToggleButton btnReports;
    @FXML private ToggleButton btnOverview;
    @FXML private ToggleButton btnSettings;
    @FXML private Label lblCurrentUser;

    private ToggleGroup navGroup;
    private int    currentShopId   = 1;
    private String currentShopName = "";
    // M-4: cache shops locally so controllers don't make extra getAllShops() HTTP calls
    private List<Shop> cachedShops = List.of();

    private DashboardController dashboardController;
    private InventoryController inventoryController;
    private SalesController salesController;
    private HistoryController historyController;
    private ExpensesController expensesController;
    private ReportsController reportsController;
    private OverviewController overviewController;
    private SettingsController settingsController;

    @FXML private Parent dashboardPane, inventoryPane, salesPane, historyPane,
                         expensesPane, reportsPane, overviewPane, settingsPane;

    private final Map<Parent, Long> lastRefreshedAt = new java.util.HashMap<>();
    private static final long SKIP_AUTO_REFRESH_MS = 15_000L; // skip if loaded < 15s ago

    @FXML
    public void initialize() {
        navGroup = new ToggleGroup();
        btnDashboard.setToggleGroup(navGroup);
        btnInventory.setToggleGroup(navGroup);
        btnSales.setToggleGroup(navGroup);
        btnHistory.setToggleGroup(navGroup);
        if (btnExpenses != null) btnExpenses.setToggleGroup(navGroup);
        btnReports.setToggleGroup(navGroup);
        if (btnOverview != null) btnOverview.setToggleGroup(navGroup);
        btnSettings.setToggleGroup(navGroup);

        // Show current user in sidebar
        if (lblCurrentUser != null && Session.getUser() != null) {
            lblCurrentUser.setText("Logged in: " + Session.getUser().getUsername()
                    + " (" + Session.getUser().getRole() + ")");
        }

        applyRoleRestrictions();
        loadShops();
        loadAllPanes();

        shopComboBox.setOnAction(e -> {
            Shop selected = shopComboBox.getValue();
            if (selected != null) {
                currentShopId   = selected.getId();
                currentShopName = selected.getName();
                shopNameLabel.setText(currentShopName);
                refreshCurrentPane();
            }
        });

        btnDashboard.setOnAction(e -> showPane(dashboardPane));
        btnInventory.setOnAction(e -> showPane(inventoryPane));
        btnSales.setOnAction(e ->     showPane(salesPane));
        btnHistory.setOnAction(e ->   showPane(historyPane));
        if (btnExpenses != null) btnExpenses.setOnAction(e -> showPane(expensesPane));
        btnReports.setOnAction(e ->   showPane(reportsPane));
        if (btnOverview != null) btnOverview.setOnAction(e -> showPane(overviewPane));
        btnSettings.setOnAction(e ->  showPane(settingsPane));

        btnDashboard.setSelected(true);
        showPane(dashboardPane);

        // Auto-refresh every 20 seconds — keeps data in sync across PCs
        // Skips if the current pane was already refreshed < 15s ago
        Timeline autoRefresh = new Timeline(new KeyFrame(Duration.seconds(20), e -> autoRefreshCurrentPane()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
    }

    private void applyRoleRestrictions() {
        boolean isAdmin = Session.isAdmin();
        btnSettings.setVisible(isAdmin); btnSettings.setManaged(isAdmin);
        shopSelectorBox.setVisible(isAdmin); shopSelectorBox.setManaged(isAdmin);
        // Overview only for admin
        if (btnOverview != null) { btnOverview.setVisible(isAdmin); btnOverview.setManaged(isAdmin); }
    }

    private void loadShops() {
        cachedShops = DatabaseManager.getInstance().getAllShops(); // M-4: populate cache
        List<Shop> shops = cachedShops;
        if (Session.isAdmin()) {
            // Admin sees all shops
            shopComboBox.getItems().setAll(shops);
            if (!shops.isEmpty()) {
                shopComboBox.setValue(shops.get(0));
                currentShopId   = shops.get(0).getId();
                currentShopName = shops.get(0).getName();
                shopNameLabel.setText(currentShopName);
            }
        } else {
            // Worker is locked to their shop
            Integer lockedId = Session.getUser().getShopId();
            shops.stream()
                .filter(s -> s.getId() == lockedId)
                .findFirst()
                .ifPresent(s -> {
                    currentShopId   = s.getId();
                    currentShopName = s.getName();
                    shopNameLabel.setText(currentShopName);
                });
        }
    }

    private void loadAllPanes() {
        try {
            FXMLLoader dl = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml"));
            dashboardPane = dl.load(); dashboardController = dl.getController();
            dashboardController.setMainController(this);

            FXMLLoader il = new FXMLLoader(getClass().getResource("/fxml/inventory.fxml"));
            inventoryPane = il.load(); inventoryController = il.getController();
            inventoryController.setMainController(this);

            FXMLLoader sl = new FXMLLoader(getClass().getResource("/fxml/sales.fxml"));
            salesPane = sl.load(); salesController = sl.getController();
            salesController.setMainController(this);

            FXMLLoader hl = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            historyPane = hl.load(); historyController = hl.getController();
            historyController.setMainController(this);

            FXMLLoader el = new FXMLLoader(getClass().getResource("/fxml/expenses.fxml"));
            expensesPane = el.load(); expensesController = el.getController();
            expensesController.setMainController(this);

            FXMLLoader rl = new FXMLLoader(getClass().getResource("/fxml/reports.fxml"));
            reportsPane = rl.load(); reportsController = rl.getController();
            reportsController.setMainController(this);

            FXMLLoader ol = new FXMLLoader(getClass().getResource("/fxml/overview.fxml"));
            overviewPane = ol.load(); overviewController = ol.getController();
            overviewController.setMainController(this);

            FXMLLoader stl = new FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            settingsPane = stl.load(); settingsController = stl.getController();
            settingsController.setMainController(this);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load UI panes: " + e.getMessage());
        }
    }

    private void showPane(Parent pane) {
        contentArea.getChildren().setAll(pane);
        refreshCurrentPane();  // explicit navigation — always refresh
    }

    /** Called by the 20-second timer — skips if the pane was recently loaded */
    private void autoRefreshCurrentPane() {
        if (contentArea.getChildren().isEmpty()) return;
        Parent current = (Parent) contentArea.getChildren().get(0);
        Long last = lastRefreshedAt.get(current);
        if (last != null && System.currentTimeMillis() - last < SKIP_AUTO_REFRESH_MS) return;
        refreshCurrentPane();
    }

    public void refreshCurrentPane() {
        if (contentArea.getChildren().isEmpty()) return;
        Parent current = (Parent) contentArea.getChildren().get(0);
        lastRefreshedAt.put(current, System.currentTimeMillis());
        if (current == dashboardPane && dashboardController != null)
            dashboardController.refresh(currentShopId, currentShopName);
        else if (current == inventoryPane && inventoryController != null)
            // M-4: pass cached shop name to avoid extra getAllShops() HTTP call per refresh
            inventoryController.refresh(currentShopId, currentShopName);
        else if (current == salesPane && salesController != null)
            salesController.refresh(currentShopId);
        else if (current == historyPane && historyController != null)
            historyController.refresh(currentShopId);
        else if (current == expensesPane && expensesController != null)
            expensesController.refresh(currentShopId, currentShopName);
        else if (current == reportsPane && reportsController != null)
            reportsController.refresh(currentShopId);
        else if (current == overviewPane && overviewController != null)
            overviewController.refresh();
        else if (current == settingsPane && settingsController != null)
            settingsController.refresh();
    }

    public int getCurrentShopId() { return currentShopId; }

    public void reloadShops() { loadShops(); }

    public void navigateTo(String pane) {
        switch (pane) {
            case "inventory" -> { btnInventory.setSelected(true); showPane(inventoryPane); }
            case "sales"     -> { btnSales.setSelected(true);     showPane(salesPane); }
            case "dashboard" -> { btnDashboard.setSelected(true); showPane(dashboardPane); }
            case "expenses"  -> { if (btnExpenses != null) btnExpenses.setSelected(true); showPane(expensesPane); }
        }
    }
}

package com.stockmanager.db;

import com.stockmanager.model.Product;
import com.stockmanager.model.Sale;
import com.stockmanager.model.SaleItem;
import com.stockmanager.model.Shop;
import com.stockmanager.model.User;
import com.stockmanager.util.PasswordUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class DatabaseManager {

    private static DatabaseManager instance;

    private final SettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;

    private DatabaseManager() {
        try {
            Properties props = loadConfig();
            
            // ─── Dynamic Config Migration from GitHub ───────────────────────
            String apiUrl = props.getProperty("api.url");
            
            boolean isLocal = apiUrl != null && (apiUrl.contains("localhost") || apiUrl.contains("127.0.0.1"));
            
            if (!isLocal) {
                try {
                    // Fetch the latest version.json from GitHub to locate the backend URL dynamically
                    String json = com.stockmanager.util.AutoUpdater.fetchUrl(
                        "https://raw.githubusercontent.com/Collomut/StoreUpdater/main/version.json", 
                        3000
                    );
                    if (json != null) {
                        String onlineApiUrl = com.stockmanager.util.AutoUpdater.extractField(json, "api_url");
                        if (onlineApiUrl != null && !onlineApiUrl.isBlank()) {
                            // If api.url is missing, or is different, or we still have old credentials:
                            if (apiUrl == null || !apiUrl.equals(onlineApiUrl) || props.containsKey("db.url")) {
                                apiUrl = onlineApiUrl;
                                props.setProperty("api.url", apiUrl);
                                
                                // Delete old insecure database credentials
                                props.remove("db.url");
                                props.remove("db.user");
                                props.remove("db.password");
                                
                                // Save updated configuration back to disk
                                saveConfig(props);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // If offline or network timeout, proceed with whatever is already in local config
                }
            }

            if (apiUrl == null || apiUrl.isBlank()) {
                apiUrl = "http://localhost:3000"; // default fallback
            }
            HttpDatabaseClient.setBackendUrl(apiUrl);

            // Instantiate repositories
            settingsRepository = new SettingsRepository(this);
            userRepository = new UserRepository(this);
            shopRepository = new ShopRepository(this);
            productRepository = new ProductRepository(this);
            saleRepository = new SaleRepository(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize backend API connection: " + e.getMessage(), e);
        }
    }

    private void saveConfig(Properties props) {
        File external = null;
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null) {
            external = new File(new File(appPath).getParent(), "config.properties");
        } else {
            external = new File(System.getProperty("user.dir"), "config.properties");
        }
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(external)) {
            props.store(fos, "Stock Manager Configurations - Auto-Migrated");
        } catch (Exception e) {
            System.err.println("Could not auto-save config migration: " + e.getMessage());
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private Properties loadConfig() throws Exception {
        Properties props = new Properties();
        File external = null;
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null) {
            external = new File(new File(appPath).getParent(), "config.properties");
        } else {
            external = new File(System.getProperty("user.dir"), "config.properties");
        }

        if (external != null && external.exists()) {
            try (FileInputStream fis = new FileInputStream(external)) {
                props.load(fis); return props;
            }
        }
        try (InputStream is = getClass().getResourceAsStream("/config.properties")) {
            if (is != null) { props.load(is); return props; }
        }
        return props; // return empty properties, will fall back to default localhost URL
    }

    // ─── Getters for Repository Sub-classes ─────────────────────────────────

    public SettingsRepository getSettingsRepository() { return settingsRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public ShopRepository getShopRepository() { return shopRepository; }
    public ProductRepository getProductRepository() { return productRepository; }
    public SaleRepository getSaleRepository() { return saleRepository; }

    // ─── Backward-compatible Delegation Facade ────────────────────────────────

    public String getSetting(String key) { return settingsRepository.getSetting(key); }
    public void setSetting(String key, String value) { settingsRepository.setSetting(key, value); }

    public User authenticate(String username, String password) { return userRepository.authenticate(username, password); }
    public List<User> getAllUsers() { return userRepository.getAllUsers(); }
    public boolean addUser(String username, String password, String role, Integer shopId) { return userRepository.addUser(username, password, role, shopId); }
    public boolean changePassword(int userId, String newPassword) { return userRepository.changePassword(userId, newPassword); }
    public boolean resetPassword(int userId, String newPassword) { return userRepository.resetPassword(userId, newPassword); }
    public boolean deleteUser(int userId) { return userRepository.deleteUser(userId); }
    public boolean updateUserShop(int userId, String role, Integer shopId) { return userRepository.updateUserShop(userId, role, shopId); }

    public List<Shop> getAllShops() { return shopRepository.getAllShops(); }
    public boolean addShop(String name, String description) { return shopRepository.addShop(name, description); }
    public boolean updateShop(int id, String name, String description) { return shopRepository.updateShop(id, name, description); }
    public boolean deleteShop(int id) { return shopRepository.deleteShop(id); }
    public boolean deleteShopCascade(int shopId) { return shopRepository.deleteShopCascade(shopId); }
    public boolean shopHasData(int shopId) { return shopRepository.shopHasData(shopId); }

    public List<Product> getProducts(int shopId) { return productRepository.getProducts(shopId); }
    public boolean addProduct(Product p) { return productRepository.addProduct(p); }
    public boolean updateProduct(Product p) { return productRepository.updateProduct(p); }
    public boolean deleteProduct(int id) { return productRepository.deleteProduct(id); }
    public boolean productHasSales(int productId) { return productRepository.productHasSales(productId); }
    public boolean retireProduct(int productId) { return productRepository.retireProduct(productId); }
    public int getLowStockCount(int shopId) { return productRepository.getLowStockCount(shopId); }
    public List<Product> getLowStockProducts(int shopId) { return productRepository.getLowStockProducts(shopId); }
    public BigDecimal getTotalStockValue(int shopId) { return productRepository.getTotalStockValue(shopId); }
    public List<Product> getProductsByShop(int shopId) { return productRepository.getProducts(shopId); }

    public int recordSale(Sale sale) { return saleRepository.recordSale(sale); }
    public List<Sale> getSales(int shopId, LocalDate from, LocalDate to) { return saleRepository.getSales(shopId, from, to); }
    public List<SaleItem> getSaleItems(int saleId) { return saleRepository.getSaleItems(saleId); }
    public BigDecimal getTotalSales(int shopId, LocalDate from, LocalDate to) { return saleRepository.getTotalSales(shopId, from, to); }
    public BigDecimal getDailySales(int shopId, LocalDate date) { return saleRepository.getDailySales(shopId, date); }
    public BigDecimal getMonthlySales(int shopId, int year, int month) { return saleRepository.getMonthlySales(shopId, year, month); }
    public BigDecimal getAnnualSales(int shopId, int year) { return saleRepository.getAnnualSales(shopId, year); }
    public List<Object[]> getTopProducts(int shopId, LocalDate from, LocalDate to, int limit) { return saleRepository.getTopProducts(shopId, from, to, limit); }
    public String getSaleReceiptNumber(int saleId) { return saleRepository.getSaleReceiptNumber(saleId); }
    public BigDecimal getTodaySales(int shopId) { return saleRepository.getTodaySales(shopId); }
    public List<Sale> getSalesByShop(int shopId, LocalDate from, LocalDate to) { return saleRepository.getSales(shopId, from, to); }
    public int saveSale(Sale sale) { return saleRepository.recordSale(sale); }
    public List<FlatSaleRow> getFlatSaleRows(int shopId, LocalDate from, LocalDate to) { return saleRepository.getFlatSaleRows(shopId, from, to); }
    public BigDecimal[] getShopDayMonthTotals(int shopId) { return saleRepository.getShopDayMonthTotals(shopId); }
    public BigDecimal[] getDashboardStats(int shopId) { return saleRepository.getDashboardStats(shopId); }
    public List<OverviewRow> getOverviewStats() { return saleRepository.getOverviewStats(); }

    // ─── DTO Inner Classes ──────────────────────────────────────────────────

    public static class FlatSaleRow {
        public final LocalDate date;
        public final String receipt;
        public final BigDecimal saleTotal;
        public final String product;
        public final int qty;
        public final BigDecimal unitPrice;
        public final String paymentMethod;
        public FlatSaleRow(LocalDate d, String r, BigDecimal t, String p, int q, BigDecimal u) {
            date=d; receipt=r; saleTotal=t; product=p; qty=q; unitPrice=u; paymentMethod="CASH";
        }
        public FlatSaleRow(LocalDate d, String r, BigDecimal t, String p, int q, BigDecimal u, String pm) {
            date=d; receipt=r; saleTotal=t; product=p; qty=q; unitPrice=u; paymentMethod=pm != null ? pm : "CASH";
        }
        public BigDecimal getSubtotal() { return BigDecimal.valueOf(qty).multiply(unitPrice); }
    }

    public static class OverviewRow {
        public final String name;
        public final BigDecimal today, month, stockValue;
        public final int lowStock, products;
        public OverviewRow(String n, BigDecimal t, BigDecimal m, BigDecimal sv, int ls, int p) {
            name=n; today=t; month=m; stockValue=sv; lowStock=ls; products=p;
        }
    }
}

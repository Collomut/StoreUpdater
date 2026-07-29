package com.stockmanager.util;

import com.stockmanager.model.Shop;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lightweight in-memory TTL cache for data that changes rarely:
 *  - Shop list          (refreshes every 5 min or on write)
 *  - Settings values    (refreshes every 5 min or on write)
 *
 * This avoids round-trips to Supabase for data that is read frequently
 * but almost never changes during a session.
 */
public final class DataCache {

    private static final long SHOP_TTL    = 5 * 60_000L;  // 5 minutes
    private static final long SETTING_TTL = 5 * 60_000L;  // 5 minutes

    // ── Shop cache ────────────────────────────────────────────────────────────
    private static volatile List<Shop> cachedShops;
    private static volatile long shopsTs = 0;

    public static List<Shop> getShops(Supplier<List<Shop>> fetcher) {
        if (cachedShops == null || elapsed(shopsTs) > SHOP_TTL) {
            cachedShops = fetcher.get();
            shopsTs = now();
        }
        return cachedShops;
    }

    public static void invalidateShops() {
        cachedShops = null;
        shopsTs = 0;
    }

    // ── Settings cache ────────────────────────────────────────────────────────
    private static final Map<String, String> settingValues = new ConcurrentHashMap<>();
    private static final Map<String, Long>   settingTs     = new ConcurrentHashMap<>();

    public static String getSetting(String key, Supplier<String> fetcher) {
        Long ts = settingTs.get(key);
        if (ts == null || elapsed(ts) > SETTING_TTL) {
            String val = fetcher.get();
            settingValues.put(key, val != null ? val : "");
            settingTs.put(key, now());
            return val;
        }
        String cached = settingValues.get(key);
        return (cached != null && !cached.isEmpty()) ? cached : null;
    }

    public static void invalidateSetting(String key) {
        settingTs.remove(key);
        settingValues.remove(key);
    }

    // ── Product cache (per shop, 30-second TTL) ───────────────────────────────
    private static final long PRODUCT_TTL = 30_000L;
    private static final Map<Integer, List<com.stockmanager.model.Product>> productCache
        = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> productTs = new ConcurrentHashMap<>();

    public static List<com.stockmanager.model.Product> getProducts(
            int shopId, Supplier<List<com.stockmanager.model.Product>> fetcher) {
        Long ts = productTs.get(shopId);
        if (ts == null || elapsed(ts) > PRODUCT_TTL) {
            List<com.stockmanager.model.Product> fresh = fetcher.get();
            productCache.put(shopId, fresh);
            productTs.put(shopId, now());
            return fresh;
        }
        return productCache.get(shopId);
    }

    public static void invalidateProducts(int shopId) {
        productCache.remove(shopId);
        productTs.remove(shopId);
    }

    public static void invalidateAllProducts() {
        productCache.clear();
        productTs.clear();
    }


    // ── Helpers ───────────────────────────────────────────────────────────────
    private static long now()            { return System.currentTimeMillis(); }
    private static long elapsed(long ts) { return now() - ts; }

    private DataCache() {}
}

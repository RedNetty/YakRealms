package com.rednetty.server.mechanics.drops;

import com.rednetty.server.mechanics.drops.types.EliteDropConfig;
import com.rednetty.server.mechanics.drops.types.ItemTypeConfig;
import com.rednetty.server.mechanics.drops.types.RarityConfig;
import com.rednetty.server.mechanics.drops.types.TierConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Enhanced configuration data holder for the drop system with improved thread safety and performance
 */
public class DropConfig {
    private static final Logger LOGGER = Logger.getLogger(DropConfig.class.getName());

    // Thread-safe configuration storage with concurrent access
    private static final Map<String, ItemTypeConfig> itemTypeConfigs = new ConcurrentHashMap<>();
    private static final Map<String, EliteDropConfig> eliteDropConfigs = new ConcurrentHashMap<>();
    private static final Map<Integer, TierConfig> tierConfigs = new ConcurrentHashMap<>();
    private static final Map<Integer, RarityConfig> rarityConfigs = new ConcurrentHashMap<>();

    // Default values constants
    private static final int DEFAULT_DROP_RATE = 50;
    private static final int DEFAULT_ELITE_DROP_RATE = 55;
    private static final int DEFAULT_CRATE_DROP_RATE = 5;

    // Cache for frequently accessed configurations
    private static final Map<String, Object> configCache = new ConcurrentHashMap<>();
    private static final String CACHE_PREFIX_TIER = "tier_";
    private static final String CACHE_PREFIX_RARITY = "rarity_";

    /**
     * Private constructor to prevent instantiation
     */
    private DropConfig() {
        // Utility class should not be instantiated
    }

    /**
     * Gets item type configuration by ID with Optional return for better null handling
     *
     * @param typeId The item type ID
     * @return Optional containing the item type configuration
     */
    public static Optional<ItemTypeConfig> getItemTypeConfigOptional(int typeId) {
        return Optional.ofNullable(itemTypeConfigs.get(String.valueOf(typeId)));
    }

    /**
     * Gets item type configuration by ID (legacy method for compatibility)
     *
     * @param typeId The item type ID
     * @return The item type configuration or null if not found
     */
    public static ItemTypeConfig getItemTypeConfig(int typeId) {
        return itemTypeConfigs.get(String.valueOf(typeId));
    }

    /**
     * Gets elite drop configuration by mob type with enhanced caching
     *
     * @param mobType The mob type
     * @return The elite drop configuration or null if not found
     */
    public static EliteDropConfig getEliteDropConfig(String mobType) {
        if (mobType == null) return null;

        String normalizedKey = mobType.toLowerCase();
        return eliteDropConfigs.get(normalizedKey);
    }

    /**
     * Gets tier configuration by tier level with caching
     *
     * @param tier The tier level
     * @return The tier configuration or null if not found
     */
    public static TierConfig getTierConfig(int tier) {
        String cacheKey = CACHE_PREFIX_TIER + tier;
        return (TierConfig) configCache.computeIfAbsent(cacheKey, k -> tierConfigs.get(tier));
    }

    /**
     * Gets rarity configuration by rarity level with caching
     *
     * @param rarity The rarity level
     * @return The rarity configuration or null if not found
     */
    public static RarityConfig getRarityConfig(int rarity) {
        String cacheKey = CACHE_PREFIX_RARITY + rarity;
        return (RarityConfig) configCache.computeIfAbsent(cacheKey, k -> rarityConfigs.get(rarity));
    }

    /**
     * Gets the map of all item type configurations (immutable view)
     *
     * @return Unmodifiable view of item type configurations map
     */
    public static Map<String, ItemTypeConfig> getItemTypeConfigs() {
        return Map.copyOf(itemTypeConfigs);
    }

    /**
     * Sets the item type configurations map with cache invalidation
     *
     * @param configs The item type configurations
     */
    public static void setItemTypeConfigs(Map<String, ItemTypeConfig> configs) {
        itemTypeConfigs.clear();
        if (configs != null) {
            itemTypeConfigs.putAll(configs);
        }
        invalidateCache();
        LOGGER.info("Updated " + itemTypeConfigs.size() + " item type configurations");
    }

    /**
     * Gets the map of all elite drop configurations (immutable view)
     *
     * @return Unmodifiable view of elite drop configurations map
     */
    public static Map<String, EliteDropConfig> getEliteDropConfigs() {
        return Map.copyOf(eliteDropConfigs);
    }

    /**
     * Sets the elite drop configurations map with cache invalidation
     *
     * @param configs The elite drop configurations
     */
    public static void setEliteDropConfigs(Map<String, EliteDropConfig> configs) {
        eliteDropConfigs.clear();
        if (configs != null) {
            eliteDropConfigs.putAll(configs);
        }
        invalidateCache();
        LOGGER.info("Updated " + eliteDropConfigs.size() + " elite drop configurations");
    }

    /**
     * Gets the map of all tier configurations (immutable view)
     *
     * @return Unmodifiable view of tier configurations map
     */
    public static Map<Integer, TierConfig> getTierConfigs() {
        return Map.copyOf(tierConfigs);
    }

    /**
     * Sets the tier configurations map with cache invalidation
     *
     * @param configs The tier configurations
     */
    public static void setTierConfigs(Map<Integer, TierConfig> configs) {
        tierConfigs.clear();
        if (configs != null) {
            tierConfigs.putAll(configs);
        }
        invalidateCache();
        LOGGER.info("Updated " + tierConfigs.size() + " tier configurations");
    }

    /**
     * Gets the map of all rarity configurations (immutable view)
     *
     * @return Unmodifiable view of rarity configurations map
     */
    public static Map<Integer, RarityConfig> getRarityConfigs() {
        return Map.copyOf(rarityConfigs);
    }

    /**
     * Sets the rarity configurations map with cache invalidation
     *
     * @param configs The rarity configurations
     */
    public static void setRarityConfigs(Map<Integer, RarityConfig> configs) {
        rarityConfigs.clear();
        if (configs != null) {
            rarityConfigs.putAll(configs);
        }
        invalidateCache();
        LOGGER.info("Updated " + rarityConfigs.size() + " rarity configurations");
    }

    /**
     * Gets drop rate for a specific tier with improved error handling
     *
     * @param tier The tier level
     * @return The drop rate or default value if not found
     */
    public static int getDropRate(int tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getDropRate() : DEFAULT_DROP_RATE;
    }

    /**
     * Gets elite drop rate for a specific tier with improved error handling
     *
     * @param tier The tier level
     * @return The elite drop rate or default value if not found
     */
    public static int getEliteDropRate(int tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getEliteDropRate() : DEFAULT_ELITE_DROP_RATE;
    }

    /**
     * Gets crate drop rate for a specific tier with improved error handling
     *
     * @param tier The tier level
     * @return The crate drop rate or default value if not found
     */
    public static int getCrateDropRate(int tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getCrateDropRate() : DEFAULT_CRATE_DROP_RATE;
    }

    /**
     * Validates if a tier exists in the configuration
     *
     * @param tier The tier to check
     * @return true if the tier exists
     */
    public static boolean isValidTier(int tier) {
        return tierConfigs.containsKey(tier);
    }

    /**
     * Validates if a rarity exists in the configuration
     *
     * @param rarity The rarity to check
     * @return true if the rarity exists
     */
    public static boolean isValidRarity(int rarity) {
        return rarityConfigs.containsKey(rarity);
    }

    /**
     * Gets the maximum configured tier
     *
     * @return The highest tier number, or 1 if no tiers configured
     */
    public static int getMaxTier() {
        return tierConfigs.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    /**
     * Gets the maximum configured rarity
     *
     * @return The highest rarity number, or 1 if no rarities configured
     */
    public static int getMaxRarity() {
        return rarityConfigs.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    /**
     * Invalidates the configuration cache
     */
    private static void invalidateCache() {
        configCache.clear();
        LOGGER.fine("Configuration cache invalidated");
    }

    /**
     * Gets configuration statistics for debugging
     *
     * @return A map containing configuration statistics
     */
    public static Map<String, Integer> getConfigurationStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("itemTypes", itemTypeConfigs.size());
        stats.put("eliteDrops", eliteDropConfigs.size());
        stats.put("tiers", tierConfigs.size());
        stats.put("rarities", rarityConfigs.size());
        stats.put("cacheSize", configCache.size());
        return stats;
    }

    /**
     * Adds a single elite drop configuration
     *
     * @param mobType The mob type
     * @param config The elite drop configuration
     */
    public static void addEliteDropConfig(String mobType, EliteDropConfig config) {
        if (mobType != null && config != null) {
            eliteDropConfigs.put(mobType.toLowerCase(), config);
            invalidateCache();
        }
    }

    /**
     * Removes an elite drop configuration
     *
     * @param mobType The mob type to remove
     * @return true if the configuration was removed
     */
    public static boolean removeEliteDropConfig(String mobType) {
        if (mobType != null) {
            boolean removed = eliteDropConfigs.remove(mobType.toLowerCase()) != null;
            if (removed) {
                invalidateCache();
            }
            return removed;
        }
        return false;
    }
}
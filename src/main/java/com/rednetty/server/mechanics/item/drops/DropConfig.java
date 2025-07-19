package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.types.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 *  configuration data holder for the drop system with proper YAML loading
 *  Now properly loads and implements all named elites from elite_drops.yml
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

    // YAML file reference
    private static File eliteDropsFile;
    private static FileConfiguration eliteDropsConfig;

    /**
     * Private constructor to prevent instantiation
     */
    private DropConfig() {
        // Utility class should not be instantiated
    }

    /**
     * CRITICAL FIX: Initialize the drop configuration system with proper YAML loading
     */
    public static void initialize() {
        try {
            loadDefaultConfigurations();
            loadEliteDropsFromYAML();
            LOGGER.info("§a[DropConfig] §7Successfully loaded drop configurations with " +
                    eliteDropConfigs.size() + " named elites");
        } catch (Exception e) {
            LOGGER.severe("§c[DropConfig] Failed to initialize drop configurations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load default tier, rarity, and item type configurations
     */
    private static void loadDefaultConfigurations() {
        // Load default tier configurations
        tierConfigs.clear();
        tierConfigs.put(1, new TierConfig(1, "LEATHER", "WOODEN", 25, 30, 3));
        tierConfigs.put(2, new TierConfig(2, "CHAINMAIL", "STONE", 35, 40, 4));
        tierConfigs.put(3, new TierConfig(3, "IRON", "IRON", 45, 50, 5));
        tierConfigs.put(4, new TierConfig(4, "DIAMOND", "DIAMOND", 55, 60, 6));
        tierConfigs.put(5, new TierConfig(5, "GOLDEN", "GOLDEN", 65, 70, 7));
        tierConfigs.put(6, new TierConfig(6, "NETHERITE", "NETHERITE", 75, 80, 8));

        // Load default rarity configurations
        rarityConfigs.clear();
        rarityConfigs.put(1, new RarityConfig(1, "Common", ChatColor.GRAY));
        rarityConfigs.put(2, new RarityConfig(2, "Uncommon", ChatColor.GREEN));
        rarityConfigs.put(3, new RarityConfig(3, "Rare", ChatColor.AQUA));
        rarityConfigs.put(4, new RarityConfig(4, "Unique", ChatColor.YELLOW));

        // Load default item type configurations -  Changed SPADE to SHOVEL for spears
        itemTypeConfigs.clear();
        itemTypeConfigs.put("1", new ItemTypeConfig(1, "HOE", true));     // Staff
        itemTypeConfigs.put("2", new ItemTypeConfig(2, "SHOVEL", true));  // Spear - FIXED from SPADE
        itemTypeConfigs.put("3", new ItemTypeConfig(3, "SWORD", true));   // Sword
        itemTypeConfigs.put("4", new ItemTypeConfig(4, "AXE", true));     // Axe
        itemTypeConfigs.put("5", new ItemTypeConfig(5, "HELMET", false)); // Helmet
        itemTypeConfigs.put("6", new ItemTypeConfig(6, "CHESTPLATE", false)); // Chestplate
        itemTypeConfigs.put("7", new ItemTypeConfig(7, "LEGGINGS", false)); // Leggings
        itemTypeConfigs.put("8", new ItemTypeConfig(8, "BOOTS", false));  // Boots
    }

    /**
     * CRITICAL FIX: Load elite drops from the YAML file
     */
    private static void loadEliteDropsFromYAML() {
        try {
            YakRealms plugin = YakRealms.getInstance();
            eliteDropsFile = new File(plugin.getDataFolder(), "elite_drops.yml");

            if (!eliteDropsFile.exists()) {
                plugin.saveResource("elite_drops.yml", false);
                LOGGER.info("§6[DropConfig] §7Created default elite_drops.yml file");
            }

            eliteDropsConfig = YamlConfiguration.loadConfiguration(eliteDropsFile);
            eliteDropConfigs.clear();

            // Load each elite configuration from the YAML
            for (String eliteName : eliteDropsConfig.getKeys(false)) {
                try {
                    EliteDropConfig config = loadEliteFromSection(eliteName, eliteDropsConfig.getConfigurationSection(eliteName));
                    if (config != null) {
                        eliteDropConfigs.put(eliteName.toLowerCase(), config);
                        LOGGER.fine("§a[DropConfig] §7Loaded elite: " + eliteName + " (T" + config.getTier() + ")");
                    }
                } catch (Exception e) {
                    LOGGER.warning("§c[DropConfig] Failed to load elite " + eliteName + ": " + e.getMessage());
                }
            }

            LOGGER.info("§a[DropConfig] §7Successfully loaded " + eliteDropConfigs.size() + " elite configurations");

        } catch (Exception e) {
            LOGGER.severe("§c[DropConfig] Failed to load elite_drops.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load a single elite configuration from a YAML section
     */
    private static EliteDropConfig loadEliteFromSection(String eliteName, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        try {
            int tier = section.getInt("tier", 1);
            int rarity = section.getInt("rarity", 2);

            EliteDropConfig config = new EliteDropConfig(eliteName, tier, rarity);

            // Load stats
            ConfigurationSection statsSection = section.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String statName : statsSection.getKeys(false)) {
                    ConfigurationSection statSection = statsSection.getConfigurationSection(statName);
                    if (statSection != null) {
                        int min = statSection.getInt("min", 0);
                        int max = statSection.getInt("max", 0);
                        config.addStatRange(statName, new StatRange(min, max));
                    }
                }
            }

            // Load weapon stats
            ConfigurationSection weaponStatsSection = section.getConfigurationSection("weaponStats");
            if (weaponStatsSection != null) {
                for (String statName : weaponStatsSection.getKeys(false)) {
                    ConfigurationSection statSection = weaponStatsSection.getConfigurationSection(statName);
                    if (statSection != null) {
                        int min = statSection.getInt("min", 0);
                        int max = statSection.getInt("max", 0);
                        config.addWeaponStatRange(statName, new StatRange(min, max));
                    }
                }
            }

            // Load armor stats
            ConfigurationSection armorStatsSection = section.getConfigurationSection("armorStats");
            if (armorStatsSection != null) {
                for (String statName : armorStatsSection.getKeys(false)) {
                    if (statName.equals("hp")) {
                        // Special handling for HP stats with armor pieces
                        ConfigurationSection hpSection = armorStatsSection.getConfigurationSection("hp");
                        if (hpSection != null) {
                            Map<String, StatRange> hpMap = new HashMap<>();
                            for (String armorPiece : hpSection.getKeys(false)) {
                                ConfigurationSection pieceSection = hpSection.getConfigurationSection(armorPiece);
                                if (pieceSection != null) {
                                    int min = pieceSection.getInt("min", 0);
                                    int max = pieceSection.getInt("max", 0);
                                    hpMap.put(armorPiece, new StatRange(min, max));
                                }
                            }
                            config.addArmorTypeStat("hp", hpMap);
                        }
                    } else {
                        ConfigurationSection statSection = armorStatsSection.getConfigurationSection(statName);
                        if (statSection != null) {
                            int min = statSection.getInt("min", 0);
                            int max = statSection.getInt("max", 0);
                            config.addArmorStatRange(statName, new StatRange(min, max));
                        }
                    }
                }
            }

            // Load items
            ConfigurationSection itemsSection = section.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemTypeStr : itemsSection.getKeys(false)) {
                    try {
                        int itemType = Integer.parseInt(itemTypeStr);
                        ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemTypeStr);
                        if (itemSection != null) {
                            String name = itemSection.getString("name", "Elite Item");
                            String materialName = itemSection.getString("material", "STONE");
                            String lore = itemSection.getString("lore", "An elite item");

                            Material material;
                            try {
                                material = Material.valueOf(materialName);
                            } catch (IllegalArgumentException e) {
                                material = Material.STONE;
                                LOGGER.warning("§c[DropConfig] Invalid material " + materialName + " for " + eliteName + " item " + itemType);
                            }

                            ItemDetails itemDetails = new ItemDetails(name, material, lore);
                            config.addItemDetails(itemType, itemDetails);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warning("§c[DropConfig] Invalid item type " + itemTypeStr + " for elite " + eliteName);
                    }
                }
            }

            return config;

        } catch (Exception e) {
            LOGGER.warning("§c[DropConfig] Error loading elite " + eliteName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets item type configuration by ID with Optional return for better null handling
     */
    public static Optional<ItemTypeConfig> getItemTypeConfigOptional(int typeId) {
        return Optional.ofNullable(itemTypeConfigs.get(String.valueOf(typeId)));
    }

    /**
     * Gets item type configuration by ID (legacy method for compatibility)
     */
    public static ItemTypeConfig getItemTypeConfig(int typeId) {
        return itemTypeConfigs.get(String.valueOf(typeId));
    }

    /**
     *  Gets elite drop configuration by mob type with  caching and better lookup
     */
    public static EliteDropConfig getEliteDropConfig(String mobType) {
        if (mobType == null || mobType.trim().isEmpty()) {
            return null;
        }

        String normalizedKey = mobType.toLowerCase().trim();

        // Direct lookup first
        EliteDropConfig config = eliteDropConfigs.get(normalizedKey);
        if (config != null) {
            return config;
        }

        // Try alternate name formats for compatibility
        String[] alternateNames = {
                normalizedKey.replace("_", ""),
                normalizedKey.replace(" ", ""),
                normalizedKey.replace("-", "")
        };

        for (String alternateName : alternateNames) {
            config = eliteDropConfigs.get(alternateName);
            if (config != null) {
                // Cache the alternate name for future lookups
                eliteDropConfigs.put(normalizedKey, config);
                return config;
            }
        }

        return null;
    }

    /**
     * Gets tier configuration by tier level with caching
     */
    public static TierConfig getTierConfig(int tier) {
        String cacheKey = CACHE_PREFIX_TIER + tier;
        return (TierConfig) configCache.computeIfAbsent(cacheKey, k -> tierConfigs.get(tier));
    }

    /**
     * Gets rarity configuration by rarity level with caching
     */
    public static RarityConfig getRarityConfig(int rarity) {
        String cacheKey = CACHE_PREFIX_RARITY + rarity;
        return (RarityConfig) configCache.computeIfAbsent(cacheKey, k -> rarityConfigs.get(rarity));
    }

    /**
     * Gets the map of all item type configurations (immutable view)
     */
    public static Map<String, ItemTypeConfig> getItemTypeConfigs() {
        return Map.copyOf(itemTypeConfigs);
    }

    /**
     * Sets the item type configurations map with cache invalidation
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
     */
    public static Map<String, EliteDropConfig> getEliteDropConfigs() {
        return Map.copyOf(eliteDropConfigs);
    }

    /**
     * Sets the elite drop configurations map with cache invalidation
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
     */
    public static Map<Integer, TierConfig> getTierConfigs() {
        return Map.copyOf(tierConfigs);
    }

    /**
     * Sets the tier configurations map with cache invalidation
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
     */
    public static Map<Integer, RarityConfig> getRarityConfigs() {
        return Map.copyOf(rarityConfigs);
    }

    /**
     * Sets the rarity configurations map with cache invalidation
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
     */
    public static int getDropRate(int tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getDropRate() : DEFAULT_DROP_RATE;
    }

    /**
     * Gets elite drop rate for a specific tier with improved error handling
     */
    public static int getEliteDropRate(int tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getEliteDropRate() : DEFAULT_ELITE_DROP_RATE;
    }

    /**
     * Gets crate drop rate for a specific tier with improved error handling
     */
    public static int getCrateDropRate(int tier) {
        TierConfig config = getTierConfig(tier);
        return config != null ? config.getCrateDropRate() : DEFAULT_CRATE_DROP_RATE;
    }

    /**
     * Validates if a tier exists in the configuration
     */
    public static boolean isValidTier(int tier) {
        return tierConfigs.containsKey(tier);
    }

    /**
     * Validates if a rarity exists in the configuration
     */
    public static boolean isValidRarity(int rarity) {
        return rarityConfigs.containsKey(rarity);
    }

    /**
     * Check if a mob type is a configured named elite
     */
    public static boolean isNamedElite(String mobType) {
        return getEliteDropConfig(mobType) != null;
    }

    /**
     * Gets the maximum configured tier
     */
    public static int getMaxTier() {
        return tierConfigs.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    /**
     * Gets the maximum configured rarity
     */
    public static int getMaxRarity() {
        return rarityConfigs.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    /**
     * Reloads the elite drops configuration from the YAML file
     */
    public static void reloadEliteDrops() {
        try {
            loadEliteDropsFromYAML();
            invalidateCache();
            LOGGER.info("§a[DropConfig] §7Reloaded elite drops configuration");
        } catch (Exception e) {
            LOGGER.severe("§c[DropConfig] Failed to reload elite drops: " + e.getMessage());
        }
    }

    /**
     * Saves the current elite drops configuration back to the YAML file
     */
    public static void saveEliteDrops() {
        try {
            if (eliteDropsFile != null && eliteDropsConfig != null) {
                eliteDropsConfig.save(eliteDropsFile);
                LOGGER.info("§a[DropConfig] §7Saved elite drops configuration");
            }
        } catch (IOException e) {
            LOGGER.severe("§c[DropConfig] Failed to save elite drops: " + e.getMessage());
        }
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
     */
    public static void addEliteDropConfig(String mobType, EliteDropConfig config) {
        if (mobType != null && config != null) {
            eliteDropConfigs.put(mobType.toLowerCase(), config);
            invalidateCache();
        }
    }

    /**
     * Removes an elite drop configuration
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

    /**
     * Gets all configured elite names for debugging
     */
    public static String[] getAllEliteNames() {
        return eliteDropConfigs.keySet().toArray(new String[0]);
    }

    /**
     * Debug method to print all loaded elite configurations
     */
    public static void printEliteConfigurations() {
        LOGGER.info("§6[DropConfig] §7=== LOADED ELITE CONFIGURATIONS ===");
        for (Map.Entry<String, EliteDropConfig> entry : eliteDropConfigs.entrySet()) {
            EliteDropConfig config = entry.getValue();
            LOGGER.info("§6[DropConfig] §7" + entry.getKey() + " -> T" + config.getTier() +
                    " R" + config.getRarity() + " (" + config.getItemDetailsCount() + " items)");
        }
        LOGGER.info("§6[DropConfig] §7=== END ELITE CONFIGURATIONS ===");
    }
}
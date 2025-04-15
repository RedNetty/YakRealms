package com.rednetty.server.mechanics.drops;

import com.rednetty.server.mechanics.drops.types.EliteDropConfig;
import com.rednetty.server.mechanics.drops.types.ItemTypeConfig;
import com.rednetty.server.mechanics.drops.types.RarityConfig;
import com.rednetty.server.mechanics.drops.types.TierConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Holds configuration data for the drop system
 */
public class DropConfig {
    private static final Logger LOGGER = Logger.getLogger(DropConfig.class.getName());

    // Configuration storage
    private static Map<String, ItemTypeConfig> itemTypeConfigs = new HashMap<>();
    private static Map<String, EliteDropConfig> eliteDropConfigs = new HashMap<>();
    private static Map<Integer, TierConfig> tierConfigs = new HashMap<>();
    private static Map<Integer, RarityConfig> rarityConfigs = new HashMap<>();

    /**
     * Private constructor to prevent instantiation
     */
    private DropConfig() {
        // This is a utility class, it should not be instantiated
    }

    /**
     * Gets item type configuration by ID
     *
     * @param typeId The item type ID
     * @return The item type configuration or null if not found
     */
    public static ItemTypeConfig getItemTypeConfig(int typeId) {
        return itemTypeConfigs.get(String.valueOf(typeId));
    }

    /**
     * Gets elite drop configuration by mob type
     *
     * @param mobType The mob type
     * @return The elite drop configuration or null if not found
     */
    public static EliteDropConfig getEliteDropConfig(String mobType) {
        return eliteDropConfigs.get(mobType.toLowerCase());
    }

    /**
     * Gets tier configuration by tier level
     *
     * @param tier The tier level
     * @return The tier configuration or null if not found
     */
    public static TierConfig getTierConfig(int tier) {
        return tierConfigs.get(tier);
    }

    /**
     * Gets rarity configuration by rarity level
     *
     * @param rarity The rarity level
     * @return The rarity configuration or null if not found
     */
    public static RarityConfig getRarityConfig(int rarity) {
        return rarityConfigs.get(rarity);
    }

    /**
     * Gets the map of all item type configurations
     *
     * @return The item type configurations map
     */
    public static Map<String, ItemTypeConfig> getItemTypeConfigs() {
        return itemTypeConfigs;
    }

    /**
     * Sets the item type configurations map
     *
     * @param configs The item type configurations
     */
    public static void setItemTypeConfigs(Map<String, ItemTypeConfig> configs) {
        itemTypeConfigs = configs;
    }

    /**
     * Gets the map of all elite drop configurations
     *
     * @return The elite drop configurations map
     */
    public static Map<String, EliteDropConfig> getEliteDropConfigs() {
        return eliteDropConfigs;
    }

    /**
     * Sets the elite drop configurations map
     *
     * @param configs The elite drop configurations
     */
    public static void setEliteDropConfigs(Map<String, EliteDropConfig> configs) {
        eliteDropConfigs = configs;
    }

    /**
     * Gets the map of all tier configurations
     *
     * @return The tier configurations map
     */
    public static Map<Integer, TierConfig> getTierConfigs() {
        return tierConfigs;
    }

    /**
     * Sets the tier configurations map
     *
     * @param configs The tier configurations
     */
    public static void setTierConfigs(Map<Integer, TierConfig> configs) {
        tierConfigs = configs;
    }

    /**
     * Gets the map of all rarity configurations
     *
     * @return The rarity configurations map
     */
    public static Map<Integer, RarityConfig> getRarityConfigs() {
        return rarityConfigs;
    }

    /**
     * Sets the rarity configurations map
     *
     * @param configs The rarity configurations
     */
    public static void setRarityConfigs(Map<Integer, RarityConfig> configs) {
        rarityConfigs = configs;
    }

    /**
     * Gets drop rate for a specific tier
     *
     * @param tier The tier level
     * @return The drop rate or default value if not found
     */
    public static int getDropRate(int tier) {
        TierConfig config = tierConfigs.get(tier);
        return config != null ? config.getDropRate() : 50;
    }

    /**
     * Gets elite drop rate for a specific tier
     *
     * @param tier The tier level
     * @return The elite drop rate or default value if not found
     */
    public static int getEliteDropRate(int tier) {
        TierConfig config = tierConfigs.get(tier);
        return config != null ? config.getEliteDropRate() : 55;
    }

    /**
     * Gets crate drop rate for a specific tier
     *
     * @param tier The tier level
     * @return The crate drop rate or default value if not found
     */
    public static int getCrateDropRate(int tier) {
        TierConfig config = tierConfigs.get(tier);
        return config != null ? config.getCrateDropRate() : 5;
    }


}
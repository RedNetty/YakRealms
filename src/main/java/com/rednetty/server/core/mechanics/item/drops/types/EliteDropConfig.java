package com.rednetty.server.core.mechanics.item.drops.types;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for elite mob drops loaded from elite_drops.yml
 * CRITICAL: Stores all the elite mob drop data from the YAML file
 * Better handling of stat ranges and damage variation
 */
public class EliteDropConfig {

    private final String mobName;
    private final int tier;
    private final int rarity;

    // Stats that apply to all items
    private final Map<String, StatRange> commonStats = new HashMap<>();

    // Stats specific to weapons
    private final Map<String, StatRange> weaponStats = new HashMap<>();

    // Stats specific to armor
    private final Map<String, StatRange> armorStats = new HashMap<>();

    // Armor type specific stats (e.g., HP values for helmet vs chestplate)
    private final Map<String, Map<String, StatRange>> armorTypeStats = new HashMap<>();

    // Item details for each item type (1-8)
    private final Map<Integer, ItemDetails> itemDetails = new HashMap<>();

    /**
     * Constructor for elite drop configuration
     */
    public EliteDropConfig(String mobName, int tier, int rarity) {
        this.mobName = mobName;
        this.tier = tier;
        this.rarity = rarity;
    }

    // ===== GETTERS =====

    public String getMobName() {
        return mobName;
    }

    public int getTier() {
        return tier;
    }

    public int getRarity() {
        return rarity;
    }

    // ===== STAT MANAGEMENT =====

    /**
     * Add a common stat range that applies to all items
     */
    public void addStatRange(String statName, StatRange range) {
        commonStats.put(statName, range);
    }

    /**
     * Get a common stat range
     */
    public StatRange getStatRange(String statName) {
        return commonStats.get(statName);
    }

    /**
     * Add a weapon-specific stat range
     */
    public void addWeaponStatRange(String statName, StatRange range) {
        weaponStats.put(statName, range);
    }

    /**
     * Get a weapon-specific stat range
     */
    public StatRange getWeaponStatRange(String statName) {
        return weaponStats.get(statName);
    }

    /**
     * Add an armor-specific stat range
     */
    public void addArmorStatRange(String statName, StatRange range) {
        armorStats.put(statName, range);
    }

    /**
     * Get an armor-specific stat range
     */
    public StatRange getArmorStatRange(String statName) {
        return armorStats.get(statName);
    }

    /**
     * Add armor type specific stats (e.g., HP for different armor pieces)
     */
    public void addArmorTypeStat(String statName, Map<String, StatRange> typeMap) {
        armorTypeStats.put(statName, new HashMap<>(typeMap));
    }

    /**
     * Get armor type specific stats
     */
    public Map<String, Map<String, StatRange>> getArmorTypeStats() {
        return armorTypeStats;
    }

    /**
     * Get all weapon stats
     */
    public Map<String, StatRange> getWeaponStats() {
        return new HashMap<>(weaponStats);
    }

    /**
     * Get all armor stats
     */
    public Map<String, StatRange> getArmorStats() {
        return new HashMap<>(armorStats);
    }

    /**
     * Get all common stats
     */
    public Map<String, StatRange> getCommonStats() {
        return new HashMap<>(commonStats);
    }

    // ===== ITEM DETAILS MANAGEMENT =====

    /**
     * Add item details for a specific item type
     */
    public void addItemDetails(int itemType, ItemDetails details) {
        itemDetails.put(itemType, details);
    }

    /**
     * Get item details for a specific item type
     */
    public ItemDetails getItemDetailsForType(int itemType) {
        return itemDetails.get(itemType);
    }

    /**
     * Get all item details
     */
    public Map<Integer, ItemDetails> getAllItemDetails() {
        return new HashMap<>(itemDetails);
    }

    /**
     * Get the count of configured item details
     */
    public int getItemDetailsCount() {
        return itemDetails.size();
    }

    /**
     * Check if item details exist for a specific type
     */
    public boolean hasItemDetailsForType(int itemType) {
        return itemDetails.containsKey(itemType);
    }

    // ===== UTILITY METHODS =====

    /**
     * Get a formatted string representation of this configuration
     */
    @Override
    public String toString() {
        return String.format("EliteDropConfig{mobName='%s', tier=%d, rarity=%d, items=%d, weaponStats=%d, armorStats=%d}",
                mobName, tier, rarity, itemDetails.size(), weaponStats.size(), armorStats.size());
    }

    /**
     * Check if this configuration has any weapon stats defined
     */
    public boolean hasWeaponStats() {
        return !weaponStats.isEmpty();
    }

    /**
     * Check if this configuration has any armor stats defined
     */
    public boolean hasArmorStats() {
        return !armorStats.isEmpty();
    }

    /**
     * Check if this configuration has any common stats defined
     */
    public boolean hasCommonStats() {
        return !commonStats.isEmpty();
    }

    /**
     * Get all configured stat names
     */
    public java.util.Set<String> getAllStatNames() {
        java.util.Set<String> allStats = new java.util.HashSet<>();
        allStats.addAll(commonStats.keySet());
        allStats.addAll(weaponStats.keySet());
        allStats.addAll(armorStats.keySet());
        return allStats;
    }

    /**
     * Validate that this configuration has the minimum required data
     */
    public boolean isValid() {
        return mobName != null && !mobName.trim().isEmpty() &&
                tier >= 1 && tier <= 6 &&
                rarity >= 1 && rarity <= 4 &&
                !itemDetails.isEmpty();
    }

    /**
     * Get debug information about this configuration
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Elite Drop Config Debug for ").append(mobName).append(":\n");
        info.append("  Tier: ").append(tier).append("\n");
        info.append("  Rarity: ").append(rarity).append("\n");
        info.append("  Item Details: ").append(itemDetails.size()).append("\n");

        if (!commonStats.isEmpty()) {
            info.append("  Common Stats: ");
            commonStats.forEach((stat, range) -> info.append(stat).append("(").append(range).append(") "));
            info.append("\n");
        }

        if (!weaponStats.isEmpty()) {
            info.append("  Weapon Stats: ");
            weaponStats.forEach((stat, range) -> info.append(stat).append("(").append(range).append(") "));
            info.append("\n");
        }

        if (!armorStats.isEmpty()) {
            info.append("  Armor Stats: ");
            armorStats.forEach((stat, range) -> info.append(stat).append("(").append(range).append(") "));
            info.append("\n");
        }

        info.append("  Items: ");
        itemDetails.forEach((type, details) -> info.append("T").append(type).append("(").append(details.getName()).append(") "));

        return info.toString();
    }

    /**
     *  method to get stat range with fallback logic
     */
    public StatRange getStatRangeWithFallback(String statName, boolean isWeapon, int itemType) {
        StatRange range = null;

        // Check specific stats first
        if (isWeapon) {
            range = weaponStats.get(statName);
        } else {
            range = armorStats.get(statName);
        }

        // Fallback to common stats
        if (range == null) {
            range = commonStats.get(statName);
        }

        return range;
    }

    /**
     * Get all stat ranges for debugging
     */
    public Map<String, StatRange> getAllStatRanges() {
        Map<String, StatRange> allRanges = new HashMap<>();
        allRanges.putAll(commonStats);
        allRanges.putAll(weaponStats);
        allRanges.putAll(armorStats);
        return allRanges;
    }

    /**
     *  method to check if this elite has any configured stats
     */
    public boolean hasAnyStats() {
        return !commonStats.isEmpty() || !weaponStats.isEmpty() || !armorStats.isEmpty();
    }

    /**
     * Get the total number of configured stats
     */
    public int getTotalStatCount() {
        return commonStats.size() + weaponStats.size() + armorStats.size();
    }

    /**
     * Clear all stats (for reconfiguration)
     */
    public void clearAllStats() {
        commonStats.clear();
        weaponStats.clear();
        armorStats.clear();
        armorTypeStats.clear();
    }

    /**
     *  validation with detailed error reporting
     */
    public java.util.List<String> getValidationErrors() {
        java.util.List<String> errors = new java.util.ArrayList<>();

        if (mobName == null || mobName.trim().isEmpty()) {
            errors.add("Mob name cannot be null or empty");
        }

        if (tier < 1 || tier > 6) {
            errors.add("Tier must be between 1 and 6, got: " + tier);
        }

        if (rarity < 1 || rarity > 4) {
            errors.add("Rarity must be between 1 and 4, got: " + rarity);
        }

        if (itemDetails.isEmpty()) {
            errors.add("At least one item detail must be configured");
        }

        // Validate stat ranges
        for (Map.Entry<String, StatRange> entry : getAllStatRanges().entrySet()) {
            if (entry.getValue() == null) {
                errors.add("Stat range for " + entry.getKey() + " is null");
            } else if (!entry.getValue().isValid()) {
                errors.add("Stat range for " + entry.getKey() + " is invalid");
            }
        }

        return errors;
    }

    /**
     * Check if this configuration is for a special/boss elite
     */
    public boolean isSpecialElite() {
        return tier >= 5 && rarity >= 3;
    }

    /**
     * Get the expected power level of this elite
     */
    public int getPowerLevel() {
        return (tier * 10) + (rarity * 5);
    }
}
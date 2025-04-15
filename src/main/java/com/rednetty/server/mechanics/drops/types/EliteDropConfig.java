package com.rednetty.server.mechanics.drops.types;

import java.util.HashMap;
import java.util.Map;

public class EliteDropConfig {
    private String mobType;
    private int tier;
    private int rarity;
    private Map<String, StatRange> statRanges = new HashMap<>();
    private Map<String, StatRange> weaponStatRanges = new HashMap<>();
    private Map<String, StatRange> armorStatRanges = new HashMap<>();
    private Map<String, Map<String, StatRange>> armorTypeStats = new HashMap<>(); // Added new field for nested stats
    private Map<Integer, ItemDetails> itemDetails = new HashMap<>();

    public String getMobType() {
        return mobType;
    }

    public void setMobType(String mobType) {
        this.mobType = mobType;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int getRarity() {
        return rarity;
    }

    public void setRarity(int rarity) {
        this.rarity = rarity;
    }

    public Map<String, StatRange> getStatRanges() {
        return statRanges;
    }

    public void setStatRanges(Map<String, StatRange> statRanges) {
        this.statRanges = statRanges;
    }

    public Map<String, StatRange> getWeaponStatRanges() {
        return weaponStatRanges;
    }

    public void setWeaponStatRanges(Map<String, StatRange> weaponStatRanges) {
        this.weaponStatRanges = weaponStatRanges;
    }

    public Map<String, StatRange> getArmorStatRanges() {
        return armorStatRanges;
    }

    public void setArmorStatRanges(Map<String, StatRange> armorStatRanges) {
        this.armorStatRanges = armorStatRanges;
    }

    public Map<String, Map<String, StatRange>> getArmorTypeStats() {
        return armorTypeStats;
    }

    public void setArmorTypeStats(Map<String, Map<String, StatRange>> armorTypeStats) {
        this.armorTypeStats = armorTypeStats;
    }

    public Map<Integer, ItemDetails> getItemDetails() {
        return itemDetails;
    }

    public void setItemDetails(Map<Integer, ItemDetails> itemDetails) {
        this.itemDetails = itemDetails;
    }

    /**
     * Get item details for a specific item type
     */
    public ItemDetails getItemDetailsForType(int itemType) {
        return itemDetails.get(itemType);
    }

    /**
     * Get stat range for a specific stat
     */
    public StatRange getStatRange(String statName) {
        return statRanges.get(statName);
    }

    /**
     * Get weapon stat range for a specific stat
     */
    public StatRange getWeaponStatRange(String statName) {
        return weaponStatRanges.get(statName);
    }

    /**
     * Get armor stat range for a specific stat
     */
    public StatRange getArmorStatRange(String statName) {
        return armorStatRanges.get(statName);
    }

    /**
     * Get armor-specific stat range for a specific item type
     *
     * @param statName The name of the stat (e.g., "hp")
     * @param itemType The type of item (5=helmet, 6=chestplate, 7=leggings, 8=boots)
     * @return The stat range specific to this armor type, or null if not found
     */
    public StatRange getArmorTypeStatRange(String statName, int itemType) {
        Map<String, StatRange> armorTypeMap = armorTypeStats.get(statName);
        if (armorTypeMap == null) {
            return null;
        }

        String typeName;
        switch (itemType) {
            case 5:
                typeName = "helmet";
                break;
            case 6:
                typeName = "chestplate";
                break;
            case 7:
                typeName = "leggings";
                break;
            case 8:
                typeName = "boots";
                break;
            default:
                return null;
        }

        return armorTypeMap.get(typeName);
    }
}
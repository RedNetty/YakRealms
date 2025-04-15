package com.rednetty.server.mechanics.drops.types;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

public class TierConfig {
    private int tier;
    private ChatColor color;
    private int dropRate;
    private int eliteDropRate;
    private int crateDropRate;
    private Map<String, String> materials = new HashMap<>();

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public int getDropRate() {
        return dropRate;
    }

    public void setDropRate(int dropRate) {
        this.dropRate = dropRate;
    }

    public int getEliteDropRate() {
        return eliteDropRate;
    }

    public void setEliteDropRate(int eliteDropRate) {
        this.eliteDropRate = eliteDropRate;
    }

    public int getCrateDropRate() {
        return crateDropRate;
    }

    public void setCrateDropRate(int crateDropRate) {
        this.crateDropRate = crateDropRate;
    }

    public Map<String, String> getMaterials() {
        return materials;
    }

    public void setMaterials(Map<String, String> materials) {
        this.materials = materials;
    }

    /**
     * Get material prefix for weapons or armor
     */
    public String getMaterialPrefix(boolean isWeapon) {
        return isWeapon ? materials.get("weapon") : materials.get("armor");
    }
}

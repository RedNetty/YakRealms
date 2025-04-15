package com.rednetty.server.mechanics.drops.types;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

public class RarityConfig {
    private int rarity;
    private String name;
    private ChatColor color;
    private int dropChance;
    private Map<String, Double> statMultipliers = new HashMap<>();

    public int getRarity() {
        return rarity;
    }

    public void setRarity(int rarity) {
        this.rarity = rarity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public int getDropChance() {
        return dropChance;
    }

    public void setDropChance(int dropChance) {
        this.dropChance = dropChance;
    }

    public Map<String, Double> getStatMultipliers() {
        return statMultipliers;
    }

    public void setStatMultipliers(Map<String, Double> statMultipliers) {
        this.statMultipliers = statMultipliers;
    }

    /**
     * Get multiplier for a specific stat
     */
    public double getMultiplier(String statName) {
        return statMultipliers.getOrDefault(statName, 1.0);
    }

    /**
     * Get formatted rarity string
     */
    public String getFormattedName() {
        return color + name;
    }

}

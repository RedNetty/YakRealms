package com.rednetty.server.mechanics.item.drops.types;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;


public class ItemDetails {
    private String name;
    private Material material;
    private String lore;
    private Map<String, StatRange> statOverrides = new HashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getLore() {
        return lore;
    }

    public void setLore(String lore) {
        this.lore = lore;
    }

    public Map<String, StatRange> getStatOverrides() {
        return statOverrides;
    }

    public void setStatOverrides(Map<String, StatRange> statOverrides) {
        this.statOverrides = statOverrides;
    }

    /**
     * Get stat override for a specific stat
     */
    public StatRange getStatOverride(String statName) {
        return statOverrides.get(statName);
    }

}

package com.rednetty.server.mechanics.item.drops.types;

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

    // Default tier configurations with Netherite integration
    public static TierConfig createDefaultTierConfig(int tier) {
        TierConfig config = new TierConfig();
        config.setTier(tier);

        switch (tier) {
            case 1:
                config.setColor(ChatColor.WHITE);
                config.setDropRate(50);
                config.setEliteDropRate(55);
                config.setCrateDropRate(5);
                config.getMaterials().put("weapon", "WOODEN");
                config.getMaterials().put("armor", "LEATHER");
                break;
            case 2:
                config.setColor(ChatColor.GREEN);
                config.setDropRate(45);
                config.setEliteDropRate(50);
                config.setCrateDropRate(4);
                config.getMaterials().put("weapon", "STONE");
                config.getMaterials().put("armor", "CHAINMAIL");
                break;
            case 3:
                config.setColor(ChatColor.AQUA);
                config.setDropRate(40);
                config.setEliteDropRate(45);
                config.setCrateDropRate(3);
                config.getMaterials().put("weapon", "IRON");
                config.getMaterials().put("armor", "IRON");
                break;
            case 4:
                config.setColor(ChatColor.LIGHT_PURPLE);
                config.setDropRate(35);
                config.setEliteDropRate(40);
                config.setCrateDropRate(2);
                config.getMaterials().put("weapon", "DIAMOND");
                config.getMaterials().put("armor", "DIAMOND");
                break;
            case 5:
                config.setColor(ChatColor.YELLOW);
                config.setDropRate(30);
                config.setEliteDropRate(35);
                config.setCrateDropRate(2);
                config.getMaterials().put("weapon", "GOLDEN");
                config.getMaterials().put("armor", "GOLDEN");
                break;
            case 6:
                config.setColor(ChatColor.DARK_PURPLE);
                config.setDropRate(25);
                config.setEliteDropRate(30);
                config.setCrateDropRate(1);
                config.getMaterials().put("weapon", "NETHERITE");
                config.getMaterials().put("armor", "NETHERITE");
                break;
        }

        return config;
    }

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
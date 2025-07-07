// === ChestTier.java ===
package com.rednetty.server.mechanics.world.lootchests.types;

import org.bukkit.ChatColor;

/**
 * Represents the different tiers of loot chests
 */
public enum ChestTier {
    WOODEN(1, ChatColor.WHITE, "Wooden"),
    STONE(2, ChatColor.GREEN, "Stone"),
    IRON(3, ChatColor.AQUA, "Iron"),
    DIAMOND(4, ChatColor.LIGHT_PURPLE, "Diamond"),
    GOLDEN(5, ChatColor.YELLOW, "Golden"),
    LEGENDARY(6, ChatColor.BLUE, "Legendary");

    private final int level;
    private final ChatColor color;
    private final String displayName;

    ChestTier(int level, ChatColor color, String displayName) {
        this.level = level;
        this.color = color;
        this.displayName = displayName;
    }

    public int getLevel() { return level; }
    public ChatColor getColor() { return color; }
    public String getDisplayName() { return displayName; }

    public static ChestTier fromLevel(int level) {
        for (ChestTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return WOODEN; // Default fallback
    }

    @Override
    public String toString() {
        return color + displayName + " (T" + level + ")";
    }
}



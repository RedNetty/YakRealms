package com.rednetty.server.mechanics.world.lootchests.types;

import org.bukkit.ChatColor;

/**
 * Represents the different tiers of loot chests
 * Each tier has different loot quality, respawn times, and visual effects
 */
public enum ChestTier {
    WOODEN(1, ChatColor.WHITE, "Novice"),
    STONE(2, ChatColor.GREEN, "Old"),
    IRON(3, ChatColor.AQUA, "Traveler's"),
    DIAMOND(4, ChatColor.LIGHT_PURPLE, "Knight's"),
    LEGENDARY(5, ChatColor.YELLOW, "War"),
    NETHER_FORGED(6, ChatColor.GOLD, "Fabled");

    private final int level;
    private final ChatColor color;
    private final String displayName;

    /**
     * Creates a chest tier with the specified properties
     */
    ChestTier(int level, ChatColor color, String displayName) {
        this.level = level;
        this.color = color;
        this.displayName = displayName;
    }

    /**
     * Gets the numeric level of this tier (1-6)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Gets the color associated with this tier
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Gets the display name of this tier
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a tier by its numeric level
     * @param level The level (1-6)
     * @return The corresponding tier, or null if invalid
     */
    public static ChestTier fromLevel(int level) {
        if (level < 1 || level > 6) {
            return null;
        }
        for (ChestTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Gets a tier by its name (case-insensitive)
     * @param name The tier name
     * @return The corresponding tier, or null if not found
     */
    public static ChestTier fromName(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try matching by display name
            for (ChestTier tier : values()) {
                if (tier.displayName.equalsIgnoreCase(name)) {
                    return tier;
                }
            }
            return null;
        }
    }

    /**
     * Gets the next higher tier, or null if this is the highest
     */
    public ChestTier getNextTier() {
        return fromLevel(level + 1);
    }

    /**
     * Gets the previous lower tier, or null if this is the lowest
     */
    public ChestTier getPreviousTier() {
        return fromLevel(level - 1);
    }

    /**
     * Checks if this tier is higher than another tier
     */
    public boolean isHigherThan(ChestTier other) {
        return other != null && this.level > other.level;
    }

    /**
     * Checks if this tier is lower than another tier
     */
    public boolean isLowerThan(ChestTier other) {
        return other != null && this.level < other.level;
    }

    /**
     * Gets the relative difficulty/rarity of this tier as a percentage
     * WOODEN = 10%, NETHER_FORGED = 100%
     */
    public double getDifficultyPercentage() {
        return (level / 6.0) * 100.0;
    }

    /**
     * Gets the expected loot quality multiplier for this tier
     */
    public double getLootQualityMultiplier() {
        return switch (this) {
            case WOODEN -> 1.0;
            case STONE -> 1.2;
            case IRON -> 1.5;
            case DIAMOND -> 2.0;
            case LEGENDARY -> 2.5;
            case NETHER_FORGED -> 3.0;
        };
    }

    /**
     * Gets the recommended minimum player level for this tier
     */
    public int getRecommendedPlayerLevel() {
        return switch (this) {
            case WOODEN -> 1;
            case STONE -> 10;
            case IRON -> 20;
            case DIAMOND -> 35;
            case LEGENDARY -> 50;
            case NETHER_FORGED -> 75;
        };
    }

    /**
     * Gets all tiers at or below this tier
     */
    public ChestTier[] getTiersUpTo() {
        ChestTier[] result = new ChestTier[level];
        ChestTier[] allTiers = values();
        System.arraycopy(allTiers, 0, result, 0, level);
        return result;
    }

    /**
     * Gets all tiers at or above this tier
     */
    public ChestTier[] getTiersFrom() {
        ChestTier[] allTiers = values();
        ChestTier[] result = new ChestTier[allTiers.length - level + 1];
        System.arraycopy(allTiers, level - 1, result, 0, result.length);
        return result;
    }

    /**
     * Returns a colored string representation
     */
    @Override
    public String toString() {
        return color + displayName + " (T" + level + ")" + ChatColor.RESET;
    }

    /**
     * Returns a plain string representation without color codes
     */
    public String toPlainString() {
        return displayName + " (T" + level + ")";
    }

    /**
     * Returns a short string representation (just the tier level)
     */
    public String toShortString() {
        return "T" + level;
    }

    /**
     * Returns a detailed string with all information
     */
    public String toDetailedString() {
        return String.format("%s %s (Level %d, Quality: %.1fx, Min Level: %d)",
                color, displayName, level, getLootQualityMultiplier(), getRecommendedPlayerLevel());
    }

    /**
     * Gets a random tier weighted by rarity (lower tiers more common)
     */
    public static ChestTier getRandomWeighted() {
        double random = Math.random();

        // Weighted probabilities (higher tiers are rarer)
        if (random < 0.40) return WOODEN;      // 40%
        if (random < 0.65) return STONE;       // 25%
        if (random < 0.80) return IRON;        // 15%
        if (random < 0.92) return DIAMOND;     // 12%
        if (random < 0.98) return LEGENDARY;      // 6%
        return NETHER_FORGED;                      // 2%
    }

    /**
     * Gets a random tier with equal probability
     */
    public static ChestTier getRandomUniform() {
        ChestTier[] tiers = values();
        return tiers[(int) (Math.random() * tiers.length)];
    }

    /**
     * Validates if a tier level is valid
     */
    public static boolean isValidLevel(int level) {
        return level >= 1 && level <= 6;
    }

    /**
     * Gets the minimum tier
     */
    public static ChestTier getMinTier() {
        return WOODEN;
    }

    /**
     * Gets the maximum tier
     */
    public static ChestTier getMaxTier() {
        return NETHER_FORGED;
    }

    /**
     * Gets the total number of tiers
     */
    public static int getTierCount() {
        return values().length;
    }
}
package com.rednetty.server.core.mechanics.item.drops.types;

import com.rednetty.server.utils.ui.GradientColors;
import org.bukkit.ChatColor;

/**
 * Configuration class for rarity-specific drop settings
 * Defines colors, names, and multipliers for different item rarities
 */
public class RarityConfig {

    private final int rarity;
    private final String name;
    private final ChatColor color;
    private final double statMultiplier;
    private final int enchantmentLevel;

    /**
     * Constructor for rarity configuration
     *
     * @param rarity           The rarity level (1-4: Common, Uncommon, Rare, Unique)
     * @param name             The display name of the rarity
     * @param color            The chat color associated with this rarity
     * @param statMultiplier   The multiplier applied to stats for this rarity
     * @param enchantmentLevel The enchantment level for items of this rarity
     */
    public RarityConfig(int rarity, String name, ChatColor color, double statMultiplier, int enchantmentLevel) {
        this.rarity = rarity;
        this.name = name != null ? name : "Unknown";
        this.color = color != null ? color : ChatColor.GRAY;
        this.statMultiplier = Math.max(0.1, statMultiplier);
        this.enchantmentLevel = Math.max(0, enchantmentLevel);
    }

    /**
     * Constructor with default stat multiplier and enchantment level
     */
    public RarityConfig(int rarity, String name, ChatColor color) {
        this(rarity, name, color, getDefaultStatMultiplier(rarity), getDefaultEnchantmentLevel(rarity));
    }

    // ===== GETTERS =====

    /**
     * Get the default stat multiplier for a rarity level
     */
    private static double getDefaultStatMultiplier(int rarity) {
        switch (rarity) {
            case 1:
                return 1.0;  // Common - 100%
            case 2:
                return 1.15; // Uncommon - 115%
            case 3:
                return 1.25; // Rare - 125%
            case 4:
                return 1.4;  // Unique - 140%
            default:
                return 1.0;
        }
    }

    /**
     * Get the default enchantment level for a rarity level
     */
    private static int getDefaultEnchantmentLevel(int rarity) {
        switch (rarity) {
            case 1:
                return 0; // Common - no enchantments
            case 2:
                return 1; // Uncommon - level 1
            case 3:
                return 2; // Rare - level 2
            case 4:
                return 3; // Unique - level 3
            default:
                return 0;
        }
    }

    /**
     * Get the default color for a rarity level
     */
    public static ChatColor getDefaultColor(int rarity) {
        switch (rarity) {
            case 1:
                return ChatColor.GRAY;         // Common
            case 2:
                return ChatColor.GREEN;        // Uncommon
            case 3:
                return ChatColor.AQUA;         // Rare
            case 4:
                return ChatColor.YELLOW;       // Unique
            default:
                return ChatColor.GRAY;
        }
    }

    /**
     * Get the default name for a rarity level
     */
    public static String getDefaultName(int rarity) {
        switch (rarity) {
            case 1:
                return "Common";
            case 2:
                return "Uncommon";
            case 3:
                return "Rare";
            case 4:
                return "Unique";
            default:
                return "Unknown";
        }
    }

    /**
     * Create a default rarity configuration for the given rarity level
     *
     * @param rarity The rarity level (1-4)
     * @return A default RarityConfig for that rarity
     */
    public static RarityConfig createDefault(int rarity) {
        return new RarityConfig(rarity, getDefaultName(rarity), getDefaultColor(rarity));
    }

    /**
     * Create a custom rarity configuration
     *
     * @param rarity         The rarity level
     * @param name           The custom name
     * @param color          The custom color
     * @param statMultiplier The custom stat multiplier
     * @return A custom RarityConfig
     */
    public static RarityConfig custom(int rarity, String name, ChatColor color, double statMultiplier) {
        return new RarityConfig(rarity, name, color, statMultiplier, getDefaultEnchantmentLevel(rarity));
    }

    /**
     * Create a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // ===== UTILITY METHODS =====

    /**
     * Create all default rarity configurations
     *
     * @return An array of all default rarity configs (indices 0-3 = rarities 1-4)
     */
    public static RarityConfig[] createAllDefaults() {
        return new RarityConfig[]{
                createDefault(1), // Common
                createDefault(2), // Uncommon
                createDefault(3), // Rare
                createDefault(4)  // Unique
        };
    }

    /**
     * Get the rarity level
     */
    public int getRarity() {
        return rarity;
    }

    /**
     * Get the display name of the rarity
     */
    public String getName() {
        return name;
    }

    /**
     * Get the chat color for this rarity
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Get the stat multiplier for this rarity
     */
    public double getStatMultiplier() {
        return statMultiplier;
    }

    /**
     * Get the enchantment level for this rarity
     */
    public int getEnchantmentLevel() {
        return enchantmentLevel;
    }

    /**
     * Get the formatted name with color
     * Unique rarity gets gradient treatment for special feel
     */
    public String getFormattedName() {
        if (rarity == 4) { // Unique
            return ChatColor.ITALIC + GradientColors.getUniqueGradient(name);
        }
        return color + "" + ChatColor.ITALIC + name;
    }

    /**
     * Get the formatted name with color and bold
     * Unique rarity gets gradient treatment for special feel
     */
    public String getFormattedNameBold() {
        if (rarity == 4) { // Unique
            return ChatColor.BOLD + "" + ChatColor.ITALIC + GradientColors.getUniqueGradient(name);
        }
        return color + "" + ChatColor.BOLD + ChatColor.ITALIC + name;
    }
    
    /**
     * Get the rarity colored text with gradient support
     * Unique returns beautiful gradient, others return standard colors
     */
    public String getRarityColoredText(String text) {
        if (rarity == 4) { // Unique
            return GradientColors.getUniqueGradient(text);
        }
        return color + text;
    }
    
    /**
     * Get formatted rarity name with appropriate coloring
     * Unique gets gradient treatment for that special unique feel
     */
    public String getFormattedRarityName() {
        return getRarityColoredText(name);
    }
    
    /**
     * Get formatted rarity symbol with appropriate coloring
     */
    public String getFormattedSymbol() {
        return getRarityColoredText(getSymbol());
    }

    /**
     * Check if this rarity configuration is valid
     */
    public boolean isValid() {
        return rarity >= 1 && rarity <= 4 &&
                name != null && !name.trim().isEmpty() &&
                color != null &&
                statMultiplier > 0;
    }

    // ===== OBJECT METHODS =====

    /**
     * Get the rarity drop chance (for random generation)
     */
    public double getDropChance() {
        switch (rarity) {
            case 1:
                return 0.62; // Common - 62%
            case 2:
                return 0.26; // Uncommon - 26%
            case 3:
                return 0.10; // Rare - 10%
            case 4:
                return 0.02; // Unique - 2%
            default:
                return 0.62;
        }
    }

    /**
     * Get the rarity description
     */
    public String getDescription() {
        switch (rarity) {
            case 1:
                return "Common items with basic stats";
            case 2:
                return "Uncommon items with improved stats";
            case 3:
                return "Rare items with  stats and special properties";
            case 4:
                return "Unique items with exceptional stats and powerful abilities";
            default:
                return "Unknown rarity level";
        }
    }

    /**
     * Get the rarity symbol for display
     */
    public String getSymbol() {
        switch (rarity) {
            case 1:
                return "◦"; // Common
            case 2:
                return "◉"; // Uncommon
            case 3:
                return "★"; // Rare
            case 4:
                return "✦"; // Unique
            default:
                return "?";
        }
    }

    // ===== STATIC UTILITY METHODS =====

    /**
     * Get the number of stats this rarity should have
     */
    public int getExpectedStatCount() {
        switch (rarity) {
            case 1:
                return 1; // Common - 1 stat
            case 2:
                return 2; // Uncommon - 2 stats
            case 3:
                return 3; // Rare - 3 stats
            case 4:
                return 4; // Unique - 4+ stats
            default:
                return 1;
        }
    }

    /**
     * Check if this rarity should have special effects
     */
    public boolean hasSpecialEffects() {
        return rarity >= 3; // Rare and Unique have special effects
    }

    /**
     * Check if this rarity should have enchantments
     */
    public boolean hasEnchantments() {
        return enchantmentLevel > 0;
    }

    /**
     * Apply the rarity multiplier to a stat value
     *
     * @param baseValue The base stat value
     * @return The value multiplied by the rarity multiplier
     */
    public int applyMultiplier(int baseValue) {
        return (int) (baseValue * statMultiplier);
    }

    // ===== STATIC FACTORY METHODS =====

    /**
     * Apply the rarity multiplier to a stat value (double)
     *
     * @param baseValue The base stat value
     * @return The value multiplied by the rarity multiplier
     */
    public double applyMultiplier(double baseValue) {
        return baseValue * statMultiplier;
    }

    /**
     * Get a string representation of this rarity configuration
     */
    @Override
    public String toString() {
        return String.format("RarityConfig{rarity=%d, name='%s', color=%s, multiplier=%.2f, enchant=%d}",
                rarity, name, color, statMultiplier, enchantmentLevel);
    }

    /**
     * Check if two RarityConfig objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        RarityConfig that = (RarityConfig) obj;
        return rarity == that.rarity &&
                Double.compare(that.statMultiplier, statMultiplier) == 0 &&
                enchantmentLevel == that.enchantmentLevel &&
                name.equals(that.name) &&
                color == that.color;
    }

    /**
     * Get hash code for this object
     */
    @Override
    public int hashCode() {
        int result = rarity;
        result = 31 * result + name.hashCode();
        result = 31 * result + color.hashCode();
        long temp = Double.doubleToLongBits(statMultiplier);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + enchantmentLevel;
        return result;
    }

    /**
     * Builder pattern for creating RarityConfig
     */
    public static class Builder {
        private int rarity = 1;
        private String name = "Common";
        private ChatColor color = ChatColor.GRAY;
        private double statMultiplier = 1.0;
        private int enchantmentLevel = 0;

        public Builder setRarity(int rarity) {
            this.rarity = rarity;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setColor(ChatColor color) {
            this.color = color;
            return this;
        }

        public Builder setStatMultiplier(double statMultiplier) {
            this.statMultiplier = statMultiplier;
            return this;
        }

        public Builder setEnchantmentLevel(int enchantmentLevel) {
            this.enchantmentLevel = enchantmentLevel;
            return this;
        }

        public RarityConfig build() {
            return new RarityConfig(rarity, name, color, statMultiplier, enchantmentLevel);
        }
    }
}
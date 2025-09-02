package com.rednetty.server.core.mechanics.item.drops.types;

import com.rednetty.server.utils.ui.GradientColors;

/**
 * Configuration class for tier-specific drop settings
 * Defines material prefixes, drop rates, and other tier-specific properties
 */
public class TierConfig {

    private final int tier;
    private final String armorMaterialPrefix;
    private final String weaponMaterialPrefix;
    private final int dropRate;
    private final int eliteDropRate;
    private final int crateDropRate;

    /**
     * Constructor for tier configuration
     *
     * @param tier                 The tier number (1-6)
     * @param armorMaterialPrefix  The material prefix for armor (e.g., "LEATHER", "IRON")
     * @param weaponMaterialPrefix The material prefix for weapons (e.g., "WOODEN", "IRON")
     * @param dropRate             The base drop rate percentage for this tier
     * @param eliteDropRate        The elite drop rate percentage for this tier
     * @param crateDropRate        The crate drop rate percentage for this tier
     */
    public TierConfig(int tier, String armorMaterialPrefix, String weaponMaterialPrefix,
                      int dropRate, int eliteDropRate, int crateDropRate) {
        this.tier = tier;
        this.armorMaterialPrefix = armorMaterialPrefix != null ? armorMaterialPrefix : "LEATHER";
        this.weaponMaterialPrefix = weaponMaterialPrefix != null ? weaponMaterialPrefix : "WOODEN";
        this.dropRate = Math.max(0, Math.min(100, dropRate));
        this.eliteDropRate = Math.max(0, Math.min(100, eliteDropRate));
        this.crateDropRate = Math.max(0, Math.min(100, crateDropRate));
    }

    /**
     * Constructor with default crate drop rate
     */
    public TierConfig(int tier, String armorMaterialPrefix, String weaponMaterialPrefix,
                      int dropRate, int eliteDropRate) {
        this(tier, armorMaterialPrefix, weaponMaterialPrefix, dropRate, eliteDropRate, 5);
    }

    // ===== GETTERS =====

    /**
     * Create a default tier configuration for the given tier
     *
     * @param tier The tier number (1-6)
     * @return A default TierConfig for that tier
     */
    public static TierConfig createDefault(int tier) {
        switch (tier) {
            case 1:
                return new TierConfig(1, "LEATHER", "WOODEN", 25, 30, 3);
            case 2:
                return new TierConfig(2, "CHAINMAIL", "STONE", 35, 40, 4);
            case 3:
                return new TierConfig(3, "IRON", "IRON", 45, 50, 5);
            case 4:
                return new TierConfig(4, "DIAMOND", "DIAMOND", 55, 60, 6);
            case 5:
                return new TierConfig(5, "GOLDEN", "GOLDEN", 65, 70, 7);
            case 6:
                return new TierConfig(6, "NETHERITE", "NETHERITE", 75, 80, 8);
            default:
                return new TierConfig(1, "LEATHER", "WOODEN", 25, 30, 3);
        }
    }

    /**
     * Create a tier configuration with custom drop rates
     *
     * @param tier          The tier number
     * @param dropRate      The base drop rate
     * @param eliteDropRate The elite drop rate
     * @return A TierConfig with the specified rates
     */
    public static TierConfig withCustomRates(int tier, int dropRate, int eliteDropRate) {
        TierConfig defaultConfig = createDefault(tier);
        return new TierConfig(tier, defaultConfig.armorMaterialPrefix, defaultConfig.weaponMaterialPrefix,
                dropRate, eliteDropRate, defaultConfig.crateDropRate);
    }

    /**
     * Create a tier configuration with custom materials
     *
     * @param tier         The tier number
     * @param armorPrefix  The armor material prefix
     * @param weaponPrefix The weapon material prefix
     * @return A TierConfig with the specified materials
     */
    public static TierConfig withCustomMaterials(int tier, String armorPrefix, String weaponPrefix) {
        TierConfig defaultConfig = createDefault(tier);
        return new TierConfig(tier, armorPrefix, weaponPrefix,
                defaultConfig.dropRate, defaultConfig.eliteDropRate, defaultConfig.crateDropRate);
    }

    /**
     * Create a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the tier number
     */
    public int getTier() {
        return tier;
    }

    /**
     * Get the armor material prefix
     */
    public String getArmorMaterialPrefix() {
        return armorMaterialPrefix;
    }

    /**
     * Get the weapon material prefix
     */
    public String getWeaponMaterialPrefix() {
        return weaponMaterialPrefix;
    }

    // ===== UTILITY METHODS =====

    /**
     * Get the appropriate material prefix based on whether it's a weapon or armor
     *
     * @param isWeapon true if the item is a weapon, false if armor
     * @return The appropriate material prefix
     */
    public String getMaterialPrefix(boolean isWeapon) {
        return isWeapon ? weaponMaterialPrefix : armorMaterialPrefix;
    }

    /**
     * Get the base drop rate for this tier
     */
    public int getDropRate() {
        return dropRate;
    }

    /**
     * Get the elite drop rate for this tier
     */
    public int getEliteDropRate() {
        return eliteDropRate;
    }

    /**
     * Get the crate drop rate for this tier
     */
    public int getCrateDropRate() {
        return crateDropRate;
    }

    /**
     * Check if this tier configuration is valid
     */
    public boolean isValid() {
        return tier >= 1 && tier <= 6 &&
                armorMaterialPrefix != null && !armorMaterialPrefix.trim().isEmpty() &&
                weaponMaterialPrefix != null && !weaponMaterialPrefix.trim().isEmpty() &&
                dropRate >= 0 && dropRate <= 100 &&
                eliteDropRate >= 0 && eliteDropRate <= 100 &&
                crateDropRate >= 0 && crateDropRate <= 100;
    }

    /**
     * Get the tier name as a string (useful for display)
     */
    public String getTierName() {
        switch (tier) {
            case 1:
                return "Novice";
            case 2:
                return "Apprentice";
            case 3:
                return "Adept";
            case 4:
                return "Expert";
            case 5:
                return "Master";
            case 6:
                return "Legendary";
            default:
                return "Unknown";
        }
    }

    // ===== OBJECT METHODS =====

    /**
     * Get the tier color code for display
     * T6 uses gradient colors for premium feel
     */
    public String getTierColorCode() {
        switch (tier) {
            case 1:
                return "§f"; // White
            case 2:
                return "§a"; // Green
            case 3:
                return "§b"; // Aqua
            case 4:
                return "§d"; // Light Purple
            case 5:
                return "§e"; // Yellow
            case 6:
                return "§6"; // Gold (fallback)
            default:
                return "§7"; // Gray
        }
    }
    
    /**
     * Get the tier color for text with gradient support
     * T6 returns beautiful gradient, others return standard colors
     */
    public String getTierColoredText(String text) {
        switch (tier) {
            case 6:
                return GradientColors.getT6Gradient(text);
            default:
                return getTierColorCode() + text;
        }
    }
    
    /**
     * Get formatted tier name with appropriate coloring
     * T6 gets gradient treatment for that legendary feel
     */
    public String getFormattedTierName() {
        return getTierColoredText(getTierName());
    }
    
    /**
     * Get formatted tier Roman numeral with appropriate coloring
     */
    public String getFormattedTierRoman() {
        return getTierColoredText(getTierRoman());
    }

    /**
     * Get the tier Roman numeral representation
     */
    public String getTierRoman() {
        switch (tier) {
            case 1:
                return "I";
            case 2:
                return "II";
            case 3:
                return "III";
            case 4:
                return "IV";
            case 5:
                return "V";
            case 6:
                return "VI";
            default:
                return "?";
        }
    }

    /**
     * Calculate the effective drop rate with buffs applied
     *
     * @param isElite        true if calculating for elite drops
     * @param buffPercentage the buff percentage to apply (0-100)
     * @return the effective drop rate
     */
    public int getEffectiveDropRate(boolean isElite, int buffPercentage) {
        int baseRate = isElite ? eliteDropRate : dropRate;
        int buffedRate = baseRate + (baseRate * buffPercentage / 100);
        return Math.min(100, buffedRate);
    }

    // ===== STATIC FACTORY METHODS =====

    /**
     * Get a description of this tier configuration
     */
    public String getDescription() {
        return String.format("Tier %d (%s): Armor=%s, Weapon=%s, Drops=%d%%, Elite=%d%%, Crates=%d%%",
                tier, getTierName(), armorMaterialPrefix, weaponMaterialPrefix,
                dropRate, eliteDropRate, crateDropRate);
    }

    /**
     * Get a string representation of this tier configuration
     */
    @Override
    public String toString() {
        return String.format("TierConfig{tier=%d, armor='%s', weapon='%s', drops=%d%%, elite=%d%%, crates=%d%%}",
                tier, armorMaterialPrefix, weaponMaterialPrefix, dropRate, eliteDropRate, crateDropRate);
    }

    /**
     * Check if two TierConfig objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        TierConfig that = (TierConfig) obj;
        return tier == that.tier &&
                dropRate == that.dropRate &&
                eliteDropRate == that.eliteDropRate &&
                crateDropRate == that.crateDropRate &&
                armorMaterialPrefix.equals(that.armorMaterialPrefix) &&
                weaponMaterialPrefix.equals(that.weaponMaterialPrefix);
    }

    /**
     * Get hash code for this object
     */
    @Override
    public int hashCode() {
        int result = tier;
        result = 31 * result + armorMaterialPrefix.hashCode();
        result = 31 * result + weaponMaterialPrefix.hashCode();
        result = 31 * result + dropRate;
        result = 31 * result + eliteDropRate;
        result = 31 * result + crateDropRate;
        return result;
    }

    /**
     * Builder pattern for creating TierConfig
     */
    public static class Builder {
        private int tier = 1;
        private String armorMaterialPrefix = "LEATHER";
        private String weaponMaterialPrefix = "WOODEN";
        private int dropRate = 25;
        private int eliteDropRate = 30;
        private int crateDropRate = 3;

        public Builder setTier(int tier) {
            this.tier = tier;
            return this;
        }

        public Builder setArmorMaterialPrefix(String armorMaterialPrefix) {
            this.armorMaterialPrefix = armorMaterialPrefix;
            return this;
        }

        public Builder setWeaponMaterialPrefix(String weaponMaterialPrefix) {
            this.weaponMaterialPrefix = weaponMaterialPrefix;
            return this;
        }

        public Builder setDropRate(int dropRate) {
            this.dropRate = dropRate;
            return this;
        }

        public Builder setEliteDropRate(int eliteDropRate) {
            this.eliteDropRate = eliteDropRate;
            return this;
        }

        public Builder setCrateDropRate(int crateDropRate) {
            this.crateDropRate = crateDropRate;
            return this;
        }

        public TierConfig build() {
            return new TierConfig(tier, armorMaterialPrefix, weaponMaterialPrefix,
                    dropRate, eliteDropRate, crateDropRate);
        }
    }
}
package com.rednetty.server.mechanics.mobs.spawners;

/**
 * Class representing a specific mob type entry in a spawner configuration
 */
public class MobEntry {
    private final String mobType;
    private final int tier;
    private final boolean elite;
    private final int amount;

    /**
     * Constructor
     *
     * @param mobType The mob type ID
     * @param tier    The tier level (1-6)
     * @param elite   Whether this is an elite mob
     * @param amount  How many of this mob type should be active
     */
    public MobEntry(String mobType, int tier, boolean elite, int amount) {
        this.mobType = mobType.toLowerCase(); // Ensure lowercase for consistency
        this.tier = validateTier(tier);
        this.elite = elite;
        this.amount = validateAmount(amount);
    }

    /**
     * Validate tier is within allowed range
     *
     * @param tier The tier to validate
     * @return Valid tier (1-6)
     */
    private int validateTier(int tier) {
        return Math.max(1, Math.min(6, tier));
    }

    /**
     * Validate amount is reasonable
     *
     * @param amount The amount to validate
     * @return Valid amount (1-20)
     */
    private int validateAmount(int amount) {
        return Math.max(1, Math.min(20, amount));
    }

    /**
     * Get the mob type
     *
     * @return Mob type ID
     */
    public String getMobType() {
        return mobType;
    }

    /**
     * Get the tier
     *
     * @return Tier level (1-6)
     */
    public int getTier() {
        return tier;
    }

    /**
     * Check if this is an elite mob
     *
     * @return true if elite
     */
    public boolean isElite() {
        return elite;
    }

    /**
     * Get the desired amount
     *
     * @return Amount of this mob type
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Convert to spawner data format string
     *
     * @return String representation (format: mobType:tier@elite#amount)
     */
    @Override
    public String toString() {
        return mobType + ":" + tier + "@" + elite + "#" + amount;
    }

    /**
     * Create a copy with a different amount
     *
     * @param newAmount The new amount value
     * @return A new MobEntry instance
     */
    public MobEntry withAmount(int newAmount) {
        return new MobEntry(mobType, tier, elite, newAmount);
    }

    /**
     * Get a key that uniquely identifies this mob type (without amount)
     *
     * @return Unique key string
     */
    public String getKey() {
        return mobType + ":" + tier + "@" + elite;
    }

    /**
     * Get a formatted display name for this mob entry
     *
     * @return Formatted display string
     */
    public String getDisplayName() {
        String formattedType = formatMobType(mobType);
        return formattedType + " T" + tier + (elite ? "+" : "") + " ×" + amount;
    }

    /**
     * Format mob type for display
     *
     * @param type Mob type string
     * @return Formatted mob type
     */
    private String formatMobType(String type) {
        if (type == null || type.isEmpty()) {
            return "Unknown";
        }

        // Capitalize first letter
        String formatted = type.substring(0, 1).toUpperCase() + type.substring(1);

        // Handle special cases
        if (formatted.equals("Witherskeleton")) {
            return "Wither Skeleton";
        }

        return formatted.replace("_", " ");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MobEntry other = (MobEntry) o;
        return tier == other.tier &&
                elite == other.elite &&
                mobType.equals(other.mobType);
    }

    @Override
    public int hashCode() {
        int result = mobType.hashCode();
        result = 31 * result + tier;
        result = 31 * result + (elite ? 1 : 0);
        return result;
    }
}
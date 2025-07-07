package com.rednetty.server.mechanics.world.mobs.spawners;

import com.rednetty.server.mechanics.world.mobs.core.MobType;

/**
 * FIXED: Enhanced MobEntry with better validation and utility methods
 */
public class MobEntry {
    private final String mobType;
    private final int tier;
    private final boolean elite;
    private final int amount;

    /**
     * Constructor with validation
     *
     * @param mobType The mob type ID
     * @param tier    The tier level (1-6)
     * @param elite   Whether this is an elite mob
     * @param amount  How many of this mob type should be active
     */
    public MobEntry(String mobType, int tier, boolean elite, int amount) {
        this.mobType = validateAndNormalizeMobType(mobType);
        this.tier = validateTier(tier);
        this.elite = elite;
        this.amount = validateAmount(amount);
    }

    /**
     * FIXED: Validate and normalize mob type
     *
     * @param mobType The mob type to validate
     * @return Normalized mob type
     * @throws IllegalArgumentException if invalid
     */
    private String validateAndNormalizeMobType(String mobType) {
        if (mobType == null || mobType.trim().isEmpty()) {
            throw new IllegalArgumentException("Mob type cannot be null or empty");
        }

        String normalized = mobType.toLowerCase().trim();

        // Handle common variations
        if (normalized.equals("wither_skeleton")) {
            normalized = "witherskeleton";
        }

        // Validate with MobType system
        if (!MobType.isValidType(normalized)) {
            throw new IllegalArgumentException("Invalid mob type: " + mobType);
        }

        return normalized;
    }

    /**
     * Validate tier is within allowed range
     *
     * @param tier The tier to validate
     * @return Valid tier (1-6)
     * @throws IllegalArgumentException if invalid
     */
    private int validateTier(int tier) {
        if (tier < 1 || tier > 6) {
            throw new IllegalArgumentException("Tier must be between 1 and 6, got: " + tier);
        }
        return tier;
    }

    /**
     * Validate amount is reasonable
     *
     * @param amount The amount to validate
     * @return Valid amount (1-50)
     * @throws IllegalArgumentException if invalid
     */
    private int validateAmount(int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Amount must be at least 1, got: " + amount);
        }
        if (amount > 50) {
            throw new IllegalArgumentException("Amount cannot exceed 50, got: " + amount);
        }
        return amount;
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
     * FIXED: Get the MobType object for this entry
     *
     * @return MobType instance or null if not found
     */
    public MobType getMobTypeObject() {
        return MobType.getById(mobType);
    }

    /**
     * FIXED: Check if this mob entry is valid for the current server configuration
     *
     * @return true if valid
     */
    public boolean isValidForCurrentConfig() {
        MobType type = getMobTypeObject();
        if (type == null) {
            return false;
        }

        // Check tier validity for this mob type
        if (!type.isValidTier(tier)) {
            return false;
        }

        // Check T6 availability
        if (tier > 5 && !com.rednetty.server.YakRealms.isT6Enabled()) {
            return false;
        }

        return true;
    }

    /**
     * FIXED: Get a human-readable description of this mob entry
     *
     * @return Formatted description
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();

        // Add tier color
        String tierColor = getTierColorCode(tier);
        desc.append(tierColor);

        // Add elite marker
        if (elite) {
            desc.append("§l"); // Bold for elite
        }

        // Add formatted mob name
        desc.append(getFormattedMobName());

        // Add tier and amount info
        desc.append(" §r§7T").append(tier);
        if (elite) {
            desc.append("§7+");
        }
        desc.append(" §7×").append(amount);

        return desc.toString();
    }

    /**
     * Get tier color code
     *
     * @param tier The tier level
     * @return Color code string
     */
    private String getTierColorCode(int tier) {
        switch (tier) {
            case 1: return "§f"; // White
            case 2: return "§a"; // Green
            case 3: return "§b"; // Aqua
            case 4: return "§d"; // Light Purple
            case 5: return "§e"; // Yellow
            case 6: return "§9"; // Blue
            default: return "§7"; // Gray
        }
    }

    /**
     * Get formatted mob name for display
     *
     * @return Formatted name
     */
    private String getFormattedMobName() {
        if (mobType == null || mobType.isEmpty()) {
            return "Unknown";
        }

        // Capitalize first letter and handle special cases
        String formatted = mobType.substring(0, 1).toUpperCase() + mobType.substring(1);

        switch (mobType.toLowerCase()) {
            case "witherskeleton":
                return "Wither Skeleton";
            case "cavespider":
                return "Cave Spider";
            case "magmacube":
                return "Magma Cube";
            case "zombifiedpiglin":
                return "Zombified Piglin";
            default:
                return formatted.replace("_", " ");
        }
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
     * FIXED: Parse a MobEntry from a data string
     *
     * @param data The data string to parse
     * @return MobEntry instance
     * @throws IllegalArgumentException if parsing fails
     */
    public static MobEntry fromString(String data) {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Data string cannot be null or empty");
        }

        try {
            String[] parts = data.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid format: missing ':'");
            }

            String mobType = parts[0].trim();
            String[] tierEliteAmount = parts[1].split("@");
            if (tierEliteAmount.length != 2) {
                throw new IllegalArgumentException("Invalid format: missing '@'");
            }

            int tier = Integer.parseInt(tierEliteAmount[0].trim());
            String[] eliteAmount = tierEliteAmount[1].split("#");
            if (eliteAmount.length != 2) {
                throw new IllegalArgumentException("Invalid format: missing '#'");
            }

            boolean elite = Boolean.parseBoolean(eliteAmount[0].trim());
            int amount = Integer.parseInt(eliteAmount[1].trim());

            return new MobEntry(mobType, tier, elite, amount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in: " + data, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse mob entry: " + data, e);
        }
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
     * Create a copy with a different tier
     *
     * @param newTier The new tier value
     * @return A new MobEntry instance
     */
    public MobEntry withTier(int newTier) {
        return new MobEntry(mobType, newTier, elite, amount);
    }

    /**
     * Create a copy with different elite status
     *
     * @param newElite The new elite status
     * @return A new MobEntry instance
     */
    public MobEntry withElite(boolean newElite) {
        return new MobEntry(mobType, tier, newElite, amount);
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
     * Get a simple key for grouping (type and tier only)
     *
     * @return Simple key string
     */
    public String getSimpleKey() {
        return mobType + ":" + tier;
    }

    /**
     * Get a formatted display name for this mob entry
     *
     * @return Formatted display string
     */
    public String getDisplayName() {
        return getFormattedMobName() + " T" + tier + (elite ? "+" : "") + " ×" + amount;
    }

    /**
     * Check if this entry matches another entry (excluding amount)
     *
     * @param other The other entry to compare
     * @return true if they represent the same mob type
     */
    public boolean matches(MobEntry other) {
        if (other == null) return false;
        return mobType.equals(other.mobType) &&
                tier == other.tier &&
                elite == other.elite;
    }

    /**
     * Check if this entry matches a spawned mob
     *
     * @param spawnedMob The spawned mob to compare
     * @return true if they match
     */
    public boolean matches(SpawnedMob spawnedMob) {
        if (spawnedMob == null) return false;
        return mobType.equals(spawnedMob.getMobType()) &&
                tier == spawnedMob.getTier() &&
                elite == spawnedMob.isElite();
    }
    /**
     * Check if this entry matches a spawned mob
     *
     * @param spawnedMob The spawned mob to compare
     * @return true if they match
     */
    public boolean matchesEntry(Spawner.RespawnEntry spawnedMob) {
        if (spawnedMob == null) return false;
        return mobType.equals(spawnedMob.getMobType()) &&
                tier == spawnedMob.getTier() &&
                elite == spawnedMob.isElite();
    }
    /**
     * Get the expected respawn delay for this mob type
     *
     * @return Respawn delay in milliseconds
     */
    public long getExpectedRespawnDelay() {
        // Base delay of 3 minutes
        long baseDelay = 180000L;

        // Tier factor
        double tierFactor = 1.0 + ((tier - 1) * 0.2);

        // Elite multiplier
        double eliteMultiplier = elite ? 1.5 : 1.0;

        long calculatedDelay = (long) (baseDelay * tierFactor * eliteMultiplier);

        // Clamp between 3 and 15 minutes
        return Math.min(Math.max(calculatedDelay, 180000L), 900000L);
    }

    /**
     * Compare entries by priority (tier descending, then elite status, then amount)
     *
     * @param other The other entry to compare
     * @return Comparison result
     */
    public int compareTo(MobEntry other) {
        if (other == null) return 1;

        // Higher tier first
        int tierCompare = Integer.compare(other.tier, this.tier);
        if (tierCompare != 0) return tierCompare;

        // Elite first
        int eliteCompare = Boolean.compare(other.elite, this.elite);
        if (eliteCompare != 0) return eliteCompare;

        // Higher amount first
        int amountCompare = Integer.compare(other.amount, this.amount);
        if (amountCompare != 0) return amountCompare;

        // Finally by mob type name
        return this.mobType.compareTo(other.mobType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MobEntry other = (MobEntry) o;
        return tier == other.tier &&
                elite == other.elite &&
                amount == other.amount &&
                mobType.equals(other.mobType);
    }

    @Override
    public int hashCode() {
        int result = mobType.hashCode();
        result = 31 * result + tier;
        result = 31 * result + (elite ? 1 : 0);
        result = 31 * result + amount;
        return result;
    }
}
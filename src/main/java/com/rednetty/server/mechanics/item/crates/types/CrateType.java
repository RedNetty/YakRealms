package com.rednetty.server.mechanics.item.crates.types;

/**
 * Enumeration of all crate types with  functionality
 */
public enum CrateType {
    // Regular crate types
    BASIC(1, "Basic", false),
    MEDIUM(2, "Medium", false),
    WAR(3, "War", false),
    ANCIENT(4, "Ancient", false),
    LEGENDARY(5, "Legendary", false),
    FROZEN(6, "Frozen", false),

    // Halloween variants
    BASIC_HALLOWEEN(1, "Basic", true),
    MEDIUM_HALLOWEEN(2, "Medium", true),
    WAR_HALLOWEEN(3, "War", true),
    ANCIENT_HALLOWEEN(4, "Ancient", true),
    LEGENDARY_HALLOWEEN(5, "Legendary", true),
    FROZEN_HALLOWEEN(6, "Frozen", true);

    private final int tier;
    private final String displayName;
    private final boolean isHalloween;

    /**
     * Constructor for CrateType
     *
     * @param tier        The tier level (1-6)
     * @param displayName The display name
     * @param isHalloween Whether this is a Halloween variant
     */
    CrateType(int tier, String displayName, boolean isHalloween) {
        this.tier = tier;
        this.displayName = displayName;
        this.isHalloween = isHalloween;
    }

    /**
     * Gets the tier level of this crate type
     *
     * @return The tier level (1-6)
     */
    public int getTier() {
        return tier;
    }

    /**
     * Gets the display name of this crate type
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this is a Halloween variant
     *
     * @return true if this is a Halloween crate
     */
    public boolean isHalloween() {
        return isHalloween;
    }

    /**
     * Gets the regular (non-Halloween) variant of this crate type
     *
     * @return The regular variant, or this if already regular
     */
    public CrateType getRegularVariant() {
        if (!isHalloween) {
            return this;
        }

        return switch (this) {
            case BASIC_HALLOWEEN -> BASIC;
            case MEDIUM_HALLOWEEN -> MEDIUM;
            case WAR_HALLOWEEN -> WAR;
            case ANCIENT_HALLOWEEN -> ANCIENT;
            case LEGENDARY_HALLOWEEN -> LEGENDARY;
            case FROZEN_HALLOWEEN -> FROZEN;
            default -> this;
        };
    }

    /**
     * Gets the Halloween variant of this crate type
     *
     * @return The Halloween variant, or null if this is already Halloween
     */
    public CrateType getHalloweenVariant() {
        if (isHalloween) {
            return this;
        }

        return switch (this) {
            case BASIC -> BASIC_HALLOWEEN;
            case MEDIUM -> MEDIUM_HALLOWEEN;
            case WAR -> WAR_HALLOWEEN;
            case ANCIENT -> ANCIENT_HALLOWEEN;
            case LEGENDARY -> LEGENDARY_HALLOWEEN;
            case FROZEN -> FROZEN_HALLOWEEN;
            default -> null;
        };
    }

    /**
     * Gets a crate type by tier and Halloween status
     *
     * @param tier        The tier level
     * @param isHalloween Whether to get Halloween variant
     * @return The matching CrateType, or null if not found
     */
    public static CrateType getByTier(int tier, boolean isHalloween) {
        for (CrateType type : values()) {
            if (type.tier == tier && type.isHalloween == isHalloween) {
                return type;
            }
        }
        return null;
    }

    /**
     * Gets all crate types of a specific tier
     *
     * @param tier The tier to filter by
     * @return Array of matching crate types
     */
    public static CrateType[] getByTier(int tier) {
        return java.util.Arrays.stream(values())
                .filter(type -> type.tier == tier)
                .toArray(CrateType[]::new);
    }

    /**
     * Gets the Halloween variant for a given regular crate type
     *
     * @param regularType The regular crate type
     * @return The Halloween variant, or null if not found
     */
    public static CrateType getHalloweenVariant(CrateType regularType) {
        if (regularType == null || regularType.isHalloween) {
            return null;
        }
        return regularType.getHalloweenVariant();
    }

    /**
     * Checks if a tier is valid
     *
     * @param tier The tier to check
     * @return true if the tier is valid (1-6)
     */
    public static boolean isValidTier(int tier) {
        return tier >= 1 && tier <= 6;
    }

    /**
     * Gets all regular (non-Halloween) crate types
     *
     * @return Array of regular crate types
     */
    public static CrateType[] getRegularTypes() {
        return java.util.Arrays.stream(values())
                .filter(type -> !type.isHalloween)
                .toArray(CrateType[]::new);
    }

    /**
     * Gets all Halloween crate types
     *
     * @return Array of Halloween crate types
     */
    public static CrateType[] getHalloweenTypes() {
        return java.util.Arrays.stream(values())
                .filter(CrateType::isHalloween)
                .toArray(CrateType[]::new);
    }
}

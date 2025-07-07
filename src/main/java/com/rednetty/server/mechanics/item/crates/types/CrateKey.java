package com.rednetty.server.mechanics.item.crates.types;

/**
 * Enumeration of crate key types and their properties
 */
public enum CrateKey {
    UNIVERSAL("Universal Crate Key", "Opens any crate", true),
    BASIC("Basic Crate Key", "Opens Basic tier crates", false),
    MEDIUM("Medium Crate Key", "Opens Medium tier crates", false),
    WAR("War Crate Key", "Opens War tier crates", false),
    ANCIENT("Ancient Crate Key", "Opens Ancient tier crates", false),
    LEGENDARY("Legendary Crate Key", "Opens Legendary tier crates", false),
    FROZEN("Frozen Crate Key", "Opens Frozen tier crates", false);

    private final String displayName;
    private final String description;
    private final boolean universal;

    /**
     * Constructor for CrateKey
     *
     * @param displayName The display name of the key
     * @param description The description of what it opens
     * @param universal   Whether this key opens all crates
     */
    CrateKey(String displayName, String description, boolean universal) {
        this.displayName = displayName;
        this.description = description;
        this.universal = universal;
    }

    /**
     * Gets the display name of this key type
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description of this key type
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this is a universal key
     *
     * @return true if this key opens all crates
     */
    public boolean isUniversal() {
        return universal;
    }

    /**
     * Checks if this key can open a specific crate type
     *
     * @param crateType The crate type to check
     * @return true if this key can open the crate
     */
    public boolean canOpen(CrateType crateType) {
        if (universal) {
            return true;
        }

        // Get the regular variant for comparison
        CrateType regularType = crateType.getRegularVariant();

        return switch (this) {
            case BASIC -> regularType == CrateType.BASIC;
            case MEDIUM -> regularType == CrateType.MEDIUM;
            case WAR -> regularType == CrateType.WAR;
            case ANCIENT -> regularType == CrateType.ANCIENT;
            case LEGENDARY -> regularType == CrateType.LEGENDARY;
            case FROZEN -> regularType == CrateType.FROZEN;
            default -> false;
        };
    }

    /**
     * Gets the appropriate key type for a crate type
     *
     * @param crateType The crate type
     * @return The matching key type, or UNIVERSAL if no specific match
     */
    public static CrateKey getForCrateType(CrateType crateType) {
        CrateType regularType = crateType.getRegularVariant();

        return switch (regularType) {
            case BASIC -> BASIC;
            case MEDIUM -> MEDIUM;
            case WAR -> WAR;
            case ANCIENT -> ANCIENT;
            case LEGENDARY -> LEGENDARY;
            case FROZEN -> FROZEN;
            default -> UNIVERSAL;
        };
    }
}

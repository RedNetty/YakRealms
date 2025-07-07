package com.rednetty.server.mechanics.item.stattrak.types;

/**
 * Enumeration of stat types that can be tracked
 */
public enum StatType {
    PLAYER_KILLS("Player Kills", "pk"),
    MOB_KILLS("Mob Kills", "mk"),
    NORMAL_ORBS_USED("Normal Orbs Used", "orb"),
    LEGENDARY_ORBS_USED("Legendary Orbs Used", "legorb"),
    ORES_MINED("Ores Mined", "ores"),
    GEMS_FOUND("Gems Found", "gems");

    private final String displayName;
    private final String identifier;

    StatType(String displayName, String identifier) {
        this.displayName = displayName;
        this.identifier = identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * Gets a StatType by its identifier
     *
     * @param identifier The identifier to search for
     * @return The matching StatType, or null if not found
     */
    public static StatType getByIdentifier(String identifier) {
        for (StatType type : values()) {
            if (type.identifier.equals(identifier)) {
                return type;
            }
        }
        return null;
    }
}

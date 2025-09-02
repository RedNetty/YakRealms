package com.rednetty.server.core.mechanics.item.stattrak.types;

/**
 * Enumeration of StatTrak item types
 */
public enum StatTrakItem {
    WEAPON_TRACKER("Weapon Stat Tracker"),
    PICKAXE_TRACKER("Pickaxe Stat Tracker");

    private final String displayName;

    StatTrakItem(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

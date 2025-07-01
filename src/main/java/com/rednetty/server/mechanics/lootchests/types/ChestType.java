// === ChestType.java ===
package com.rednetty.server.mechanics.lootchests.types;

/**
 * Represents the different types of loot chests
 */
public enum ChestType {
    NORMAL("Normal loot chest"),
    CARE_PACKAGE("High-tier care package"),
    SPECIAL("Special limited-time chest");

    private final String description;

    ChestType(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}



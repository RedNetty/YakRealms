// === ChestState.java ===
package com.rednetty.server.mechanics.world.lootchests.types;

/**
 * Represents the current state of a loot chest
 */
public enum ChestState {
    AVAILABLE("Ready to be opened"),
    OPENED("Currently opened by a player"),
    RESPAWNING("Waiting to respawn"),
    EXPIRED("Expired and ready for removal");

    private final String description;

    ChestState(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
package com.rednetty.server.mechanics.world.teleport;

/**
 * Interface for items that can be consumed during teleportation
 */
public interface TeleportConsumable {
    /**
     * Consumes the item
     */
    void consume();
}
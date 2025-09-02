package com.rednetty.server.core.mechanics.player.mounts.type;

import org.bukkit.entity.Player;

/**
 * Interface for all mount types
 */
public interface Mount {
    /**
     * Gets the mount type
     *
     * @return The mount type
     */
    MountType getType();

    /**
     * Summons the mount for a player
     *
     * @param player The player
     * @return True if the summon was initiated
     */
    boolean summonMount(Player player);

    /**
     * Dismounts a player
     *
     * @param player      The player
     * @param sendMessage Whether to send a message
     * @return True if the player was dismounted
     */
    boolean dismount(Player player, boolean sendMessage);

    /**
     * Checks if the mount is in a summoning state
     *
     * @param player The player
     * @return True if the mount is being summoned
     */
    boolean isSummoning(Player player);

    /**
     * Cancels the summoning process
     *
     * @param player The player
     * @param reason The reason for cancellation
     */
    void cancelSummoning(Player player, String reason);

    /**
     * Gets the tier level of this mount
     *
     * @return The mount tier
     */
    default int getTier() {
        return getType().getTier();
    }

    /**
     * Checks if this mount has a custom name
     *
     * @return True if the mount has a custom name
     */
    default boolean hasCustomName() {
        return getCustomName() != null && !getCustomName().isEmpty();
    }

    /**
     * Gets the custom name of this mount
     *
     * @return The custom name, or null if none
     */
    default String getCustomName() {
        return null;
    }
}
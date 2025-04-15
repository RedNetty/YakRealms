package com.rednetty.server.mechanics.teleport;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Represents an active teleport session
 */
public class TeleportSession {
    private final Player player;
    private final TeleportDestination destination;
    private final Location startLocation;
    private final TeleportConsumable consumable;
    private final TeleportEffectType effectType;

    private int remainingSeconds;
    private boolean completed = false;
    private boolean cancelled = false;

    /**
     * Creates a new teleport session
     *
     * @param player      The player being teleported
     * @param destination The destination
     * @param castingTime The casting time in seconds
     * @param consumable  The consumable item (or null)
     * @param effectType  The effect type to use
     */
    public TeleportSession(Player player, TeleportDestination destination,
                           int castingTime, TeleportConsumable consumable,
                           TeleportEffectType effectType) {
        this.player = player;
        this.destination = destination;
        this.startLocation = player.getLocation().clone();
        this.remainingSeconds = castingTime;
        this.consumable = consumable;
        this.effectType = effectType;
    }

    /**
     * Starts the teleport session
     */
    public void start() {
        // Apply initial effects
        TeleportEffects.applyCastingStartEffects(player, effectType);

        // Send initial message
        String message = String.format(
                "%sTELEPORTING%s - %s%s%s ... %ds",
                ChatColor.BOLD, ChatColor.WHITE,
                ChatColor.AQUA, destination.getDisplayName(),
                ChatColor.WHITE, remainingSeconds
        );

        player.sendMessage(message);
    }

    /**
     * Advances the teleport session by one tick
     *
     * @return True if the session is complete
     */
    public boolean tick() {
        if (completed || cancelled) {
            return true;
        }

        // Check if player moved too far
        if (player.getLocation().distanceSquared(startLocation) >= 2.0) {
            cancel("You moved too far");
            return true;
        }

        // Check if player is offline
        if (!player.isOnline()) {
            cancel("Player disconnected");
            return true;
        }

        // Apply tick effects
        TeleportEffects.applyCastingTickEffects(player, effectType);

        // Decrement timer
        remainingSeconds--;

        if (remainingSeconds <= 0) {
            // Complete the teleport
            complete();
            return true;
        } else {
            // Send progress message
            String message = String.format(
                    "%sTELEPORTING%s ... %ds",
                    ChatColor.BOLD, ChatColor.WHITE, remainingSeconds
            );

            player.sendMessage(message);
            return false;
        }
    }

    /**
     * Completes the teleport session
     */
    private void complete() {
        completed = true;

        // Consume item if required
        if (consumable != null) {
            consumable.consume();
        }

        // Apply departure effects
        TeleportEffects.applyDepartureEffects(player, effectType);

        // Teleport the player
        player.teleport(destination.getLocation());

        // Apply arrival effects
        TeleportEffects.applyArrivalEffects(player, effectType);
    }

    /**
     * Cancels the teleport session
     *
     * @param reason The reason for cancellation
     */
    public void cancel(String reason) {
        if (completed || cancelled) {
            return;
        }

        cancelled = true;

        // Apply cancel effects
        TeleportEffects.applyCastingCancelEffects(player, effectType);

        // Send cancel message
        player.sendMessage(ChatColor.RED + "Teleportation - " + ChatColor.BOLD + "CANCELLED" +
                (reason != null ? ChatColor.RED + " (" + reason + ")" : ""));
    }

    /**
     * Gets the player
     *
     * @return The player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the destination
     *
     * @return The destination
     */
    public TeleportDestination getDestination() {
        return destination;
    }

    /**
     * Gets the remaining seconds
     *
     * @return The remaining seconds
     */
    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    /**
     * Checks if the session is completed
     *
     * @return True if completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Checks if the session is cancelled
     *
     * @return True if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
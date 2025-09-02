package com.rednetty.server.core.mechanics.world.teleport;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Represents an active teleport session
 */
public class TeleportSession {
    private static final double MAX_MOVEMENT_DISTANCE_SQUARED = 4.0; // 2 blocks squared

    private final Player player;
    private final TeleportDestination destination;
    private final Location startLocation;
    private final TeleportConsumable consumable;
    private final TeleportEffectType effectType;
    private final String customDisplayName; // Custom display name for the teleport

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
        this(player, destination, castingTime, consumable, effectType, null);
    }

    /**
     * Creates a new teleport session with custom display name
     *
     * @param player            The player being teleported
     * @param destination       The destination
     * @param castingTime       The casting time in seconds
     * @param consumable        The consumable item (or null)
     * @param effectType        The effect type to use
     * @param customDisplayName Custom display name for messages (or null to use destination name)
     */
    public TeleportSession(Player player, TeleportDestination destination,
                           int castingTime, TeleportConsumable consumable,
                           TeleportEffectType effectType, String customDisplayName) {
        this.player = player;
        this.destination = destination;
        this.startLocation = player != null ? player.getLocation().clone() : null;
        this.remainingSeconds = Math.max(1, castingTime); // Ensure at least 1 second
        this.consumable = consumable;
        this.effectType = effectType != null ? effectType : TeleportEffectType.SCROLL;
        this.customDisplayName = customDisplayName;
    }

    /**
     * Gets the display name to use for messages
     *
     * @return The display name (custom if provided, otherwise destination name)
     */
    private String getDisplayName() {
        if (customDisplayName != null && !customDisplayName.trim().isEmpty()) {
            return ChatColor.stripColor(customDisplayName);
        }
        return destination != null ? destination.getDisplayName() : "Unknown";
    }

    /**
     * Starts the teleport session
     */
    public void start() {
        if (player == null || destination == null) {
            cancel("Invalid session parameters");
            return;
        }

        try {
            // Apply initial effects
            TeleportEffects.applyCastingStartEffects(player, effectType);

            // Send initial message using custom display name if available
            String message = String.format(
                    "%sTELEPORTING%s - %s%s%s ... %ds",
                    ChatColor.BOLD, ChatColor.WHITE,
                    ChatColor.AQUA, getDisplayName(),
                    ChatColor.WHITE, remainingSeconds
            );

            player.sendMessage(message);
        } catch (Exception e) {
            cancel("Error starting teleport");
        }
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

        try {
            // Validate player state
            if (!isPlayerValid()) {
                cancel("Player is no longer valid");
                return true;
            }

            // Check if player moved too far
            if (hasPlayerMovedTooFar()) {
                cancel("You moved too far");
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
                sendProgressMessage();
                return false;
            }
        } catch (Exception e) {
            cancel("Error during teleport");
            return true;
        }
    }

    /**
     * Validates that the player is still valid for teleportation
     *
     * @return True if player is valid
     */
    private boolean isPlayerValid() {
        if (player == null) {
            return false;
        }

        // Check if player is online
        if (!player.isOnline()) {
            return false;
        }

        // Check if player still exists in the world
        if (player.getLocation() == null || player.getLocation().getWorld() == null) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the player has moved too far from the start location
     *
     * @return True if player moved too far
     */
    private boolean hasPlayerMovedTooFar() {
        if (startLocation == null || player == null) {
            return true;
        }

        Location currentLocation = player.getLocation();
        if (currentLocation == null || currentLocation.getWorld() == null) {
            return true;
        }

        // Check if player changed worlds
        if (!startLocation.getWorld().equals(currentLocation.getWorld())) {
            return true;
        }

        // Check distance
        return currentLocation.distanceSquared(startLocation) >= MAX_MOVEMENT_DISTANCE_SQUARED;
    }

    /**
     * Sends a progress message to the player
     */
    private void sendProgressMessage() {
        if (player == null) {
            return;
        }

        try {
            String message = String.format(
                    "%sTELEPORTING%s ... %ds",
                    ChatColor.BOLD, ChatColor.WHITE, remainingSeconds
            );

            player.sendMessage(message);
        } catch (Exception e) {
            // Ignore message errors, don't cancel teleport for this
        }
    }

    /**
     * Completes the teleport session
     */
    private void complete() {
        if (completed || cancelled) {
            return;
        }

        completed = true;

        try {
            // Validate destination before teleporting
            if (destination.getLocation() == null || destination.getLocation().getWorld() == null) {
                cancel("Destination is invalid");
                return;
            }

            // Consume item if required
            if (consumable != null) {
                consumable.consume();
            }

            // Apply departure effects
            TeleportEffects.applyDepartureEffects(player, effectType);

            // Teleport the player
            boolean teleportSuccess = player.teleport(destination.getLocation());

            if (!teleportSuccess) {
                // Teleport failed, but item was already consumed
                if (player != null) {
                    player.sendMessage(ChatColor.RED + "Teleportation failed - destination may be unsafe.");
                }
                return;
            }

            // Apply arrival effects
            TeleportEffects.applyArrivalEffects(player, effectType);

        } catch (Exception e) {
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Teleportation failed due to an error.");
            }
        }
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

        try {
            // Apply cancel effects only if player is valid
            if (player != null && player.isOnline()) {
                TeleportEffects.applyCastingCancelEffects(player, effectType);

                // Send cancel message
                String message = ChatColor.RED + "Teleportation - " + ChatColor.BOLD + "CANCELLED";
                if (reason != null && !reason.isEmpty()) {
                    message += ChatColor.RED + " (" + reason + ")";
                }
                player.sendMessage(message);
            }
        } catch (Exception e) {
            // Ignore errors during cancellation
        }
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
     * Gets the start location
     *
     * @return The start location
     */
    public Location getStartLocation() {
        return startLocation != null ? startLocation.clone() : null;
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

    /**
     * Gets the effect type
     *
     * @return The effect type
     */
    public TeleportEffectType getEffectType() {
        return effectType;
    }

    /**
     * Gets the consumable
     *
     * @return The consumable or null
     */
    public TeleportConsumable getConsumable() {
        return consumable;
    }

    /**
     * Gets the custom display name
     *
     * @return The custom display name or null
     */
    public String getCustomDisplayName() {
        return customDisplayName;
    }

    /**
     * Gets the maximum movement distance squared
     *
     * @return The maximum movement distance squared
     */
    public static double getMaxMovementDistanceSquared() {
        return MAX_MOVEMENT_DISTANCE_SQUARED;
    }

    /**
     * Checks if the session is active (not completed and not cancelled)
     *
     * @return True if active
     */
    public boolean isActive() {
        return !completed && !cancelled;
    }

    /**
     * Gets the elapsed time since the session started
     *
     * @return The elapsed time in seconds
     */
    public int getElapsedSeconds() {
        // This assumes the session was created with the original casting time
        // We can't track the original time without storing it, so this is an approximation
        return Math.max(0, remainingSeconds > 0 ? (10 - remainingSeconds) : 10);
    }

    @Override
    public String toString() {
        return "TeleportSession{" +
                "player=" + (player != null ? player.getName() : "null") +
                ", destination=" + (destination != null ? destination.getId() : "null") +
                ", remainingSeconds=" + remainingSeconds +
                ", completed=" + completed +
                ", cancelled=" + cancelled +
                ", effectType=" + effectType +
                ", customDisplayName='" + customDisplayName + '\'' +
                '}';
    }
}
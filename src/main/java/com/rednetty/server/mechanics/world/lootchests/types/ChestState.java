package com.rednetty.server.mechanics.world.lootchests.types;

import org.bukkit.ChatColor;

/**
 * Represents the current state of a loot chest
 * Controls what interactions are possible and how the chest behaves
 */
public enum ChestState {
    /**
     * Chest is available for interaction - can be opened or broken
     */
    AVAILABLE("Available", ChatColor.GREEN, "Ready to be opened or broken"),

    /**
     * Chest is currently opened by a player - inventory is being viewed
     */
    OPENED("Opened", ChatColor.YELLOW, "Currently opened by a player"),

    /**
     * Chest is waiting to respawn after being broken/emptied
     */
    RESPAWNING("Respawning", ChatColor.RED, "Waiting to respawn"),

    /**
     * Chest has expired and should be removed (for special/temporary chests)
     */
    EXPIRED("Expired", ChatColor.DARK_GRAY, "Expired and ready for removal");

    private final String displayName;
    private final ChatColor color;
    private final String description;

    /**
     * Creates a chest state with the specified properties
     */
    ChestState(String displayName, ChatColor color, String description) {
        this.displayName = displayName;
        this.color = color;
        this.description = description;
    }

    /**
     * Gets the display name of this state
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the color associated with this state
     */
    public ChatColor getColor() {
        return color;
    }

    /**
     * Gets the description of this state
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets a state by its name (case-insensitive)
     * @param name The state name
     * @return The corresponding state, or null if not found
     */
    public static ChestState fromName(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try matching by display name
            for (ChestState state : values()) {
                if (state.displayName.equalsIgnoreCase(name)) {
                    return state;
                }
            }
            return null;
        }
    }

    /**
     * Checks if this state allows player interaction (opening/breaking)
     */
    public boolean allowsInteraction() {
        return this == AVAILABLE;
    }

    /**
     * Checks if this state allows opening the chest
     */
    public boolean allowsOpening() {
        return this == AVAILABLE;
    }

    /**
     * Checks if this state allows breaking the chest
     */
    public boolean allowsBreaking() {
        return this == AVAILABLE;
    }

    /**
     * Checks if this state indicates the chest is currently in use
     */
    public boolean isInUse() {
        return this == OPENED;
    }

    /**
     * Checks if this state indicates the chest is temporarily unavailable
     */
    public boolean isTemporarilyUnavailable() {
        return this == RESPAWNING;
    }

    /**
     * Checks if this state indicates the chest should be removed
     */
    public boolean shouldBeRemoved() {
        return this == EXPIRED;
    }

    /**
     * Checks if this state is considered "active" (visible and potentially interactable)
     */
    public boolean isActive() {
        return this == AVAILABLE || this == OPENED;
    }

    /**
     * Checks if this state is considered "inactive" (not visible or interactable)
     */
    public boolean isInactive() {
        return this == RESPAWNING || this == EXPIRED;
    }

    /**
     * Gets the next logical state based on an action
     */
    public ChestState getNextState(ChestAction action) {
        return switch (this) {
            case AVAILABLE -> switch (action) {
                case OPEN -> OPENED;
                case BREAK -> RESPAWNING;
                case EXPIRE -> EXPIRED;
                default -> this;
            };
            case OPENED -> switch (action) {
                case CLOSE_EMPTY -> RESPAWNING;
                case CLOSE_WITH_ITEMS -> AVAILABLE;
                case BREAK -> RESPAWNING;
                case EXPIRE -> EXPIRED;
                default -> this;
            };
            case RESPAWNING -> switch (action) {
                case RESPAWN -> AVAILABLE;
                case EXPIRE -> EXPIRED;
                default -> this;
            };
            case EXPIRED -> this; // Terminal state
        };
    }

    /**
     * Validates if a state transition is allowed
     */
    public boolean canTransitionTo(ChestState newState) {
        if (newState == null || newState == this) {
            return newState != null; // null transitions not allowed, same state is fine
        }

        return switch (this) {
            case AVAILABLE -> newState == OPENED || newState == RESPAWNING || newState == EXPIRED;
            case OPENED -> newState == AVAILABLE || newState == RESPAWNING || newState == EXPIRED;
            case RESPAWNING -> newState == AVAILABLE || newState == EXPIRED;
            case EXPIRED -> newState == AVAILABLE; // Allow resurrection from expired
        };
    }

    /**
     * Gets all states that this state can transition to
     */
    public ChestState[] getPossibleTransitions() {
        return switch (this) {
            case AVAILABLE -> new ChestState[]{OPENED, RESPAWNING, EXPIRED};
            case OPENED -> new ChestState[]{AVAILABLE, RESPAWNING, EXPIRED};
            case RESPAWNING -> new ChestState[]{AVAILABLE, EXPIRED};
            case EXPIRED -> new ChestState[]{AVAILABLE};
        };
    }

    /**
     * Gets a user-friendly status message
     */
    public String getStatusMessage() {
        return switch (this) {
            case AVAILABLE -> color + "✓ " + displayName + " - Ready for interaction";
            case OPENED -> color + "◐ " + displayName + " - Being accessed";
            case RESPAWNING -> color + "⟳ " + displayName + " - Will return soon";
            case EXPIRED -> color + "✗ " + displayName + " - No longer available";
        };
    }

    /**
     * Gets a short status indicator
     */
    public String getStatusIndicator() {
        return switch (this) {
            case AVAILABLE -> color + "✓";
            case OPENED -> color + "◐";
            case RESPAWNING -> color + "⟳";
            case EXPIRED -> color + "✗";
        };
    }

    /**
     * Returns a colored string representation
     */
    @Override
    public String toString() {
        return color + displayName + ChatColor.RESET;
    }

    /**
     * Returns a plain string representation without color codes
     */
    public String toPlainString() {
        return displayName;
    }

    /**
     * Returns a detailed string with description
     */
    public String toDetailedString() {
        return color + displayName + ChatColor.GRAY + " - " + description + ChatColor.RESET;
    }

    /**
     * Gets the priority of this state for sorting (lower = higher priority)
     */
    public int getPriority() {
        return switch (this) {
            case AVAILABLE -> 1;   // Highest priority
            case OPENED -> 2;
            case RESPAWNING -> 3;
            case EXPIRED -> 4;     // Lowest priority
        };
    }


    /**
     * Actions that can trigger state transitions
     */
    public enum ChestAction {
        OPEN,              // Player opens the chest
        CLOSE_EMPTY,       // Player closes chest after taking all items
        CLOSE_WITH_ITEMS,  // Player closes chest with items remaining
        BREAK,             // Player breaks the chest
        RESPAWN,           // Chest respawns after timer
        EXPIRE,            // Chest expires (for temporary chests)
        FORCE_REMOVE       // Administrative removal
    }
}
package com.rednetty.server.mechanics.world.lootchests.core;

import com.rednetty.server.mechanics.world.lootchests.types.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.*;

/**
 * Core entity representing a loot chest with simple state management
 */
public class Chest {
    private final ChestLocation location;
    private final ChestTier tier;
    private final ChestType type;
    private final long creationTime;

    private ChestState state;
    private long respawnTime;
    private long lastInteraction;
    private final Set<UUID> interactedPlayers;
    private final Map<String, Object> metadata;
    private Inventory lootInventory; // NEW: Stores the generated loot inventory

    /**
     * Creates a new chest with default state
     */
    public Chest(ChestLocation location, ChestTier tier, ChestType type) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Tier cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        this.location = location;
        this.tier = tier;
        this.type = type;
        this.creationTime = System.currentTimeMillis();
        this.state = ChestState.AVAILABLE;
        this.respawnTime = 0;
        this.lastInteraction = creationTime;
        this.interactedPlayers = new HashSet<>();
        this.metadata = new HashMap<>();
        this.lootInventory = null; // Initialize lootInventory as null
    }

    /**
     * Creates a chest from loaded data (for persistence)
     */
    public Chest(ChestLocation location, ChestTier tier, ChestType type, long creationTime,
                 ChestState state, long respawnTime, long lastInteraction, Set<UUID> interactions) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Tier cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        this.location = location;
        this.tier = tier;
        this.type = type;
        this.creationTime = creationTime;
        this.state = state;
        this.respawnTime = respawnTime;
        this.lastInteraction = lastInteraction;
        this.interactedPlayers = new HashSet<>(interactions != null ? interactions : new HashSet<>());
        this.metadata = new HashMap<>();
        this.lootInventory = null; // Initialize lootInventory as null (will be set during loading if applicable)
    }

    // === State Management ===

    /**
     * Changes the chest state and updates interaction time
     */
    public void setState(ChestState newState) {
        if (newState == null) {
            throw new IllegalArgumentException("New state cannot be null");
        }
        this.state = newState;
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Records a player interaction
     */
    public void addInteraction(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        interactedPlayers.add(player.getUniqueId());
        lastInteraction = System.currentTimeMillis();
    }

    /**
     * Sets the respawn time for this chest
     */
    public void setRespawnTime(long respawnTime) {
        if (respawnTime < 0) {
            throw new IllegalArgumentException("Respawn time cannot be negative");
        }
        this.respawnTime = respawnTime;
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Resets respawn time to 0
     */
    public void resetRespawnTime() {
        this.respawnTime = 0;
        this.lastInteraction = System.currentTimeMillis();
    }

    // === Loot Inventory Management ===

    /**
     * Sets the loot inventory for this chest
     * NEW: Added to store the generated inventory to prevent double loot
     */
    public void setLootInventory(Inventory inventory) {
        this.lootInventory = inventory;
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Gets the loot inventory for this chest
     * NEW: Added to retrieve the stored inventory
     */
    public Inventory getLootInventory() {
        return lootInventory;
    }

    /**
     * Clears the loot inventory
     * NEW: Added to clear inventory when chest respawns or is removed
     */
    public void clearLootInventory() {
        this.lootInventory = null;
        this.lastInteraction = System.currentTimeMillis();
    }

    // === State Checks ===

    /**
     * Checks if the chest is ready to respawn
     */
    public boolean isReadyToRespawn() {
        return state == ChestState.RESPAWNING &&
                respawnTime > 0 &&
                System.currentTimeMillis() >= respawnTime;
    }

    /**
     * Checks if the chest has expired based on type
     */
    public boolean isExpired() {
        long age = System.currentTimeMillis() - creationTime;
        return switch (type) {
            case SPECIAL -> age > 300000; // 5 minutes
            case CARE_PACKAGE -> age > 600000; // 10 minutes
            default -> false; // Normal chests don't expire
        };
    }

    /**
     * Checks if the chest is available for interaction
     */
    public boolean isAvailable() {
        return state == ChestState.AVAILABLE;
    }

    /**
     * Checks if the chest is currently opened
     */
    public boolean isOpened() {
        return state == ChestState.OPENED;
    }

    /**
     * Checks if the chest is respawning
     */
    public boolean isRespawning() {
        return state == ChestState.RESPAWNING;
    }

    // === Time Utilities ===

    /**
     * Gets the age of the chest in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Gets time since last interaction
     */
    public long getTimeSinceLastInteraction() {
        return System.currentTimeMillis() - lastInteraction;
    }

    /**
     * Gets remaining time until respawn in milliseconds
     */
    public long getRespawnTimeRemaining() {
        if (state != ChestState.RESPAWNING || respawnTime <= 0) {
            return 0;
        }
        return Math.max(0, respawnTime - System.currentTimeMillis());
    }

    /**
     * Gets formatted respawn time remaining
     */
    public String getRespawnTimeRemainingFormatted() {
        long remaining = getRespawnTimeRemaining();
        if (remaining <= 0) {
            return "Ready";
        }

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // === Metadata Management ===

    /**
     * Sets metadata value
     */
    public void setMetadata(String key, Object value) {
        if (key != null) {
            if (value == null) {
                metadata.remove(key);
            } else {
                metadata.put(key, value);
            }
            lastInteraction = System.currentTimeMillis();
        }
    }

    /**
     * Gets metadata value
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Checks if metadata key exists
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    // === Display Methods ===

    /**
     * Gets display name with tier color
     */
    public String getDisplayName() {
        return tier.getColor() + tier.getDisplayName() + " " + type.getDescription();
    }

    /**
     * Gets status string with color coding
     */
    public String getStatusString() {
        return switch (state) {
            case AVAILABLE -> tier.getColor() + "Available";
            case OPENED -> "§eOpened";
            case RESPAWNING -> "§cRespawning (" + getRespawnTimeRemainingFormatted() + ")";
            case EXPIRED -> "§8Expired";
        };
    }

    /**
     * Gets detailed status with interaction count
     */
    public String getDetailedStatusString() {
        StringBuilder status = new StringBuilder();
        status.append(getStatusString());

        int interactionCount = interactedPlayers.size();
        if (interactionCount > 0) {
            status.append(" §7(").append(interactionCount).append(" interactions)");
        }

        return status.toString();
    }

    // === Getters ===

    public ChestLocation getLocation() {
        return location;
    }

    public ChestTier getTier() {
        return tier;
    }

    public ChestType getType() {
        return type;
    }

    public ChestState getState() {
        return state;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getRespawnTime() {
        return respawnTime;
    }

    public long getLastInteraction() {
        return lastInteraction;
    }

    /**
     * Returns a copy of interacted players to prevent external modification
     */
    public Set<UUID> getInteractedPlayers() {
        return new HashSet<>(interactedPlayers);
    }

    /**
     * Returns a copy of metadata to prevent external modification
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Gets the number of unique player interactions
     */
    public int getInteractionCount() {
        return interactedPlayers.size();
    }

    // === Object Methods ===

    @Override
    public String toString() {
        return "Chest{" +
                "location=" + location +
                ", tier=" + tier +
                ", type=" + type +
                ", state=" + state +
                ", interactions=" + interactedPlayers.size() +
                ", age=" + (getAge() / 1000) + "s" +
                ", hasLootInventory=" + (lootInventory != null) + // NEW: Indicate if inventory exists
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Chest chest = (Chest) obj;
        return Objects.equals(location, chest.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }
}
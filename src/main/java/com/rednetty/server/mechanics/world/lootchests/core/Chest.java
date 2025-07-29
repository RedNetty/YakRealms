package com.rednetty.server.mechanics.world.lootchests.core;

import com.rednetty.server.mechanics.world.lootchests.types.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.*;

/**
 * Core entity representing a loot chest with state management and loot inventory storage.
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
    private Inventory lootInventory; // Stores the generated loot inventory to prevent regeneration

    /**
     * Creates a new chest with default state and no loot inventory.
     * Loot inventory should be generated separately when needed.
     *
     * @param location The location of the chest.
     * @param tier The tier of the chest.
     * @param type The type of the chest.
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
        this.lootInventory = null; // Loot inventory generated on demand
    }

    /**
     * Creates a chest from loaded data (for persistence).
     * Loot inventory is not loaded here; it should be set separately if persisted.
     *
     * @param location The location of the chest.
     * @param tier The tier of the chest.
     * @param type The type of the chest.
     * @param creationTime The creation time of the chest.
     * @param state The state of the chest.
     * @param respawnTime The respawn time of the chest.
     * @param lastInteraction The last interaction time.
     * @param interactions The set of interacted player UUIDs.
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
        this.lootInventory = null; // Loot inventory set separately if loaded
    }

    // === State Management ===

    /**
     * Changes the chest state and updates the last interaction time.
     *
     * @param newState The new state to set.
     */
    public void setState(ChestState newState) {
        if (newState == null) {
            throw new IllegalArgumentException("New state cannot be null");
        }
        this.state = newState;
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Records a player interaction with the chest.
     *
     * @param player The player interacting.
     */
    public void addInteraction(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        interactedPlayers.add(player.getUniqueId());
        lastInteraction = System.currentTimeMillis();
    }

    /**
     * Sets the respawn time for this chest.
     *
     * @param respawnTime The respawn time in milliseconds.
     */
    public void setRespawnTime(long respawnTime) {
        if (respawnTime < 0) {
            throw new IllegalArgumentException("Respawn time cannot be negative");
        }
        this.respawnTime = respawnTime;
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Resets the respawn time to 0.
     */
    public void resetRespawnTime() {
        this.respawnTime = 0;
        this.lastInteraction = System.currentTimeMillis();
    }

    // === Loot Inventory Management ===

    /**
     * Sets the loot inventory for this chest and updates the last interaction time.
     *
     * @param inventory The inventory to set.
     */
    public void setLootInventory(Inventory inventory) {
        this.lootInventory = inventory;
        this.lastInteraction = System.currentTimeMillis();
    }

    /**
     * Gets the loot inventory for this chest.
     *
     * @return The loot inventory, or null if not set.
     */
    public Inventory getLootInventory() {
        return lootInventory;
    }

    /**
     * Clears the loot inventory and updates the last interaction time.
     */
    public void clearLootInventory() {
        this.lootInventory = null;
        this.lastInteraction = System.currentTimeMillis();
    }

    // === State Checks ===

    /**
     * Checks if the chest is ready to respawn.
     *
     * @return True if ready to respawn, false otherwise.
     */
    public boolean isReadyToRespawn() {
        return state == ChestState.RESPAWNING &&
                respawnTime > 0 &&
                System.currentTimeMillis() >= respawnTime;
    }

    /**
     * Checks if the chest has expired based on its type.
     *
     * @return True if expired, false otherwise.
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
     * Checks if the chest is available for interaction.
     *
     * @return True if available, false otherwise.
     */
    public boolean isAvailable() {
        return state == ChestState.AVAILABLE;
    }

    /**
     * Checks if the chest is currently opened.
     *
     * @return True if opened, false otherwise.
     */
    public boolean isOpened() {
        return state == ChestState.OPENED;
    }

    /**
     * Checks if the chest is respawning.
     *
     * @return True if respawning, false otherwise.
     */
    public boolean isRespawning() {
        return state == ChestState.RESPAWNING;
    }

    // === Time Utilities ===

    /**
     * Gets the age of the chest in milliseconds.
     *
     * @return The age in milliseconds.
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Gets the time since the last interaction in milliseconds.
     *
     * @return The time since last interaction.
     */
    public long getTimeSinceLastInteraction() {
        return System.currentTimeMillis() - lastInteraction;
    }

    /**
     * Gets the remaining time until respawn in milliseconds.
     *
     * @return The remaining respawn time, or 0 if not respawning.
     */
    public long getRespawnTimeRemaining() {
        if (state != ChestState.RESPAWNING || respawnTime <= 0) {
            return 0;
        }
        return Math.max(0, respawnTime - System.currentTimeMillis());
    }

    /**
     * Gets a formatted string of the remaining respawn time.
     *
     * @return The formatted remaining time.
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
     * Sets a metadata value for the chest.
     *
     * @param key The metadata key.
     * @param value The metadata value.
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
     * Gets a metadata value for the chest.
     *
     * @param key The metadata key.
     * @return The metadata value, or null if not set.
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Checks if a metadata key exists.
     *
     * @param key The metadata key.
     * @return True if the key exists, false otherwise.
     */
    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    // === Display Methods ===

    /**
     * Gets the display name of the chest with tier color.
     *
     * @return The display name.
     */
    public String getDisplayName() {
        return tier.getColor() + tier.getDisplayName() + " " + type.getDescription();
    }

    /**
     * Gets a color-coded status string for the chest.
     *
     * @return The status string.
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
     * Gets a detailed status string including interaction count.
     *
     * @return The detailed status string.
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
     * Returns a copy of the interacted players set to prevent external modification.
     *
     * @return A copy of the interacted players.
     */
    public Set<UUID> getInteractedPlayers() {
        return new HashSet<>(interactedPlayers);
    }

    /**
     * Returns a copy of the metadata map to prevent external modification.
     *
     * @return A copy of the metadata.
     */
    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Gets the number of unique player interactions.
     *
     * @return The interaction count.
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
                ", hasLootInventory=" + (lootInventory != null) +
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
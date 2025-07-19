package com.rednetty.server.mechanics.world.lootchests.core;

import com.rednetty.server.mechanics.world.lootchests.types.ChestLocation;
import com.rednetty.server.mechanics.world.lootchests.types.ChestState;
import com.rednetty.server.mechanics.world.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.world.lootchests.types.ChestType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Registry for tracking active loot chests in memory
 * Thread-safe implementation using ConcurrentHashMap
 */
public class ChestRegistry {
    private final Map<ChestLocation, Chest> chests = new ConcurrentHashMap<>();

    /**
     * Adds a chest to the registry
     * @param chest The chest to add
     * @throws IllegalArgumentException if chest is null
     */
    public void addChest(Chest chest) {
        if (chest == null) {
            throw new IllegalArgumentException("Cannot add null chest to registry");
        }

        chests.put(chest.getLocation(), chest);
    }

    /**
     * Removes a chest from the registry
     * @param location The location of the chest to remove
     * @return The removed chest, or null if not found
     */
    public Chest removeChest(ChestLocation location) {
        if (location == null) {
            return null;
        }

        return chests.remove(location);
    }

    /**
     * Gets a chest by its location
     * @param location The location to look up
     * @return The chest at that location, or null if not found
     */
    public Chest getChest(ChestLocation location) {
        if (location == null) {
            return null;
        }

        return chests.get(location);
    }

    /**
     * Checks if a chest exists at the specified location
     * @param location The location to check
     * @return true if a chest exists at that location, false otherwise
     */
    public boolean hasChest(ChestLocation location) {
        if (location == null) {
            return false;
        }

        return chests.containsKey(location);
    }

    /**
     * Gets all chests in the registry
     * @return A copy of all chests to prevent external modification
     */
    public Map<ChestLocation, Chest> getAllChests() {
        return new ConcurrentHashMap<>(chests);
    }

    /**
     * Gets all chests as a collection
     * @return Collection of all chests
     */
    public Collection<Chest> getChests() {
        return chests.values();
    }

    /**
     * Gets the number of chests in the registry
     * @return The total number of chests
     */
    public int size() {
        return chests.size();
    }

    /**
     * Checks if the registry is empty
     * @return true if no chests are registered, false otherwise
     */
    public boolean isEmpty() {
        return chests.isEmpty();
    }

    /**
     * Clears all chests from the registry
     */
    public void clear() {
        chests.clear();
    }

    // === Filtered Queries ===

    /**
     * Gets all chests with the specified state
     * @param state The state to filter by
     * @return Collection of chests with the specified state
     */
    public Collection<Chest> getChestsByState(ChestState state) {
        if (state == null) {
            return java.util.Collections.emptyList();
        }

        return chests.values().stream()
                .filter(chest -> chest.getState() == state)
                .collect(Collectors.toList());
    }

    /**
     * Gets all chests with the specified tier
     * @param tier The tier to filter by
     * @return Collection of chests with the specified tier
     */
    public Collection<Chest> getChestsByTier(ChestTier tier) {
        if (tier == null) {
            return java.util.Collections.emptyList();
        }

        return chests.values().stream()
                .filter(chest -> chest.getTier() == tier)
                .collect(Collectors.toList());
    }

    /**
     * Gets all chests with the specified type
     * @param type The type to filter by
     * @return Collection of chests with the specified type
     */
    public Collection<Chest> getChestsByType(ChestType type) {
        if (type == null) {
            return java.util.Collections.emptyList();
        }

        return chests.values().stream()
                .filter(chest -> chest.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Gets all available chests (state = AVAILABLE)
     * @return Collection of available chests
     */
    public Collection<Chest> getAvailableChests() {
        return getChestsByState(ChestState.AVAILABLE);
    }

    /**
     * Gets all respawning chests (state = RESPAWNING)
     * @return Collection of respawning chests
     */
    public Collection<Chest> getRespawningChests() {
        return getChestsByState(ChestState.RESPAWNING);
    }

    /**
     * Gets all opened chests (state = OPENED)
     * @return Collection of opened chests
     */
    public Collection<Chest> getOpenedChests() {
        return getChestsByState(ChestState.OPENED);
    }

    /**
     * Gets all normal chests (type = NORMAL)
     * @return Collection of normal chests
     */
    public Collection<Chest> getNormalChests() {
        return getChestsByType(ChestType.NORMAL);
    }

    /**
     * Gets all care packages (type = CARE_PACKAGE)
     * @return Collection of care packages
     */
    public Collection<Chest> getCarePackages() {
        return getChestsByType(ChestType.CARE_PACKAGE);
    }

    /**
     * Gets all special chests (type = SPECIAL)
     * @return Collection of special chests
     */
    public Collection<Chest> getSpecialChests() {
        return getChestsByType(ChestType.SPECIAL);
    }

    /**
     * Gets all chests ready to respawn
     * @return Collection of chests ready to respawn
     */
    public Collection<Chest> getChestsReadyToRespawn() {
        return chests.values().stream()
                .filter(Chest::isReadyToRespawn)
                .collect(Collectors.toList());
    }

    /**
     * Gets all expired chests
     * @return Collection of expired chests
     */
    public Collection<Chest> getExpiredChests() {
        return chests.values().stream()
                .filter(Chest::isExpired)
                .collect(Collectors.toList());
    }

    // === Statistics ===

    /**
     * Gets count statistics for the registry
     * @return Map of statistics
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new java.util.HashMap<>();

        stats.put("total", size());

        // Count by state
        for (ChestState state : ChestState.values()) {
            int count = (int) chests.values().stream()
                    .filter(chest -> chest.getState() == state)
                    .count();
            stats.put("state_" + state.name().toLowerCase(), count);
        }

        // Count by tier
        for (ChestTier tier : ChestTier.values()) {
            int count = (int) chests.values().stream()
                    .filter(chest -> chest.getTier() == tier)
                    .count();
            stats.put("tier_" + tier.name().toLowerCase(), count);
        }

        // Count by type
        for (ChestType type : ChestType.values()) {
            int count = (int) chests.values().stream()
                    .filter(chest -> chest.getType() == type)
                    .count();
            stats.put("type_" + type.name().toLowerCase(), count);
        }

        // Special counts
        stats.put("ready_to_respawn", getChestsReadyToRespawn().size());
        stats.put("expired", getExpiredChests().size());

        return stats;
    }

    /**
     * Gets a summary string of the registry contents
     * @return Human-readable summary
     */
    public String getSummary() {
        if (isEmpty()) {
            return "Registry is empty";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Chest Registry Summary:\n");
        summary.append("Total chests: ").append(size()).append("\n");

        // State breakdown
        summary.append("By State:\n");
        for (ChestState state : ChestState.values()) {
            long count = chests.values().stream()
                    .filter(chest -> chest.getState() == state)
                    .count();
            if (count > 0) {
                summary.append("  ").append(state).append(": ").append(count).append("\n");
            }
        }

        // Type breakdown
        summary.append("By Type:\n");
        for (ChestType type : ChestType.values()) {
            long count = chests.values().stream()
                    .filter(chest -> chest.getType() == type)
                    .count();
            if (count > 0) {
                summary.append("  ").append(type).append(": ").append(count).append("\n");
            }
        }

        return summary.toString();
    }

    /**
     * Validates the integrity of all chests in the registry
     * @return true if all chests are valid, false otherwise
     */
    public boolean validateIntegrity() {
        for (Chest chest : chests.values()) {
            if (chest == null || chest.getLocation() == null) {
                return false;
            }

            // Check that the location key matches the chest's location
            ChestLocation registryLocation = null;
            for (Map.Entry<ChestLocation, Chest> entry : chests.entrySet()) {
                if (entry.getValue() == chest) {
                    registryLocation = entry.getKey();
                    break;
                }
            }

            if (registryLocation == null || !registryLocation.equals(chest.getLocation())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "ChestRegistry{size=" + size() + ", available=" + getAvailableChests().size() +
                ", respawning=" + getRespawningChests().size() + "}";
    }
}
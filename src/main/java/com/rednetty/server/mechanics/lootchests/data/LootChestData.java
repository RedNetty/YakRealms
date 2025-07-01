// === LootChestData.java ===
package com.rednetty.server.mechanics.lootchests.data;

import com.rednetty.server.mechanics.lootchests.types.ChestState;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import com.rednetty.server.mechanics.lootchests.types.LootChestLocation;

import java.util.*;

/**
 * Contains all data for a loot chest
 */
public class LootChestData {
    private final com.rednetty.server.mechanics.lootchests.types.LootChestLocation location;
    private final ChestTier tier;
    private final ChestType type;
    private final long creationTime;

    private ChestState state;
    private long respawnTime;
    private long lastInteractionTime;
    private final Set<UUID> playersWhoInteracted;
    private final Map<String, Object> metadata;

    public LootChestData(com.rednetty.server.mechanics.lootchests.types.LootChestLocation location, ChestTier tier, ChestType type) {
        this.location = location;
        this.tier = tier;
        this.type = type;
        this.creationTime = System.currentTimeMillis();
        this.state = ChestState.AVAILABLE;
        this.respawnTime = 0;
        this.lastInteractionTime = 0;
        this.playersWhoInteracted = new HashSet<>();
        this.metadata = new HashMap<>();
    }

    // === Getters ===
    public LootChestLocation getLocation() { return location; }
    public ChestTier getTier() { return tier; }
    public ChestType getType() { return type; }
    public long getCreationTime() { return creationTime; }
    public ChestState getState() { return state; }
    public long getRespawnTime() { return respawnTime; }
    public long getLastInteractionTime() { return lastInteractionTime; }
    public Set<UUID> getPlayersWhoInteracted() { return new HashSet<>(playersWhoInteracted); }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    // === Setters ===
    public void setState(ChestState state) {
        this.state = state;
        updateLastInteractionTime();
    }

    public void setRespawnTime(long respawnTime) {
        this.respawnTime = respawnTime;
    }

    public void resetRespawnTime() {
        this.respawnTime = 0;
    }

    private void updateLastInteractionTime() {
        this.lastInteractionTime = System.currentTimeMillis();
    }

    // === Player Interaction Tracking ===
    public void addInteraction(UUID playerUuid) {
        playersWhoInteracted.add(playerUuid);
        updateLastInteractionTime();
    }

    public boolean hasPlayerInteracted(UUID playerUuid) {
        return playersWhoInteracted.contains(playerUuid);
    }

    public int getInteractionCount() {
        return playersWhoInteracted.size();
    }

    // === Metadata Management ===
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    public boolean hasMetadata(String key) {
        return metadata.containsKey(key);
    }

    public void removeMetadata(String key) {
        metadata.remove(key);
    }

    // === State Checks ===
    public boolean isReadyToRespawn() {
        return state == ChestState.RESPAWNING &&
                respawnTime > 0 &&
                System.currentTimeMillis() >= respawnTime;
    }

    public boolean isExpired() {
        if (type == ChestType.SPECIAL) {
            // Special chests expire after 5 minutes if not interacted with
            return System.currentTimeMillis() - creationTime > 300000 &&
                    playersWhoInteracted.isEmpty();
        }
        return false;
    }

    public boolean isAvailable() {
        return state == ChestState.AVAILABLE;
    }

    public boolean isOpened() {
        return state == ChestState.OPENED;
    }

    public boolean isRespawning() {
        return state == ChestState.RESPAWNING;
    }

    // === Time Utilities ===
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    public long getTimeSinceLastInteraction() {
        return lastInteractionTime > 0 ?
                System.currentTimeMillis() - lastInteractionTime :
                getAge();
    }

    public long getRespawnTimeRemaining() {
        if (state != ChestState.RESPAWNING || respawnTime <= 0) {
            return 0;
        }
        return Math.max(0, respawnTime - System.currentTimeMillis());
    }

    // === Display Methods ===
    public String getDisplayName() {
        return tier.getColor() + tier.getDisplayName() + " " + type.getDescription();
    }

    public String getStatusString() {
        return switch (state) {
            case AVAILABLE -> tier.getColor() + "Available";
            case OPENED -> "§eOpened";
            case RESPAWNING -> "§cRespawning (" + (getRespawnTimeRemaining() / 1000) + "s)";
            case EXPIRED -> "§8Expired";
        };
    }

    @Override
    public String toString() {
        return "LootChestData{" +
                "location=" + location +
                ", tier=" + tier +
                ", type=" + type +
                ", state=" + state +
                ", interactions=" + playersWhoInteracted.size() +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        LootChestData that = (LootChestData) obj;
        return Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(location);
    }
}
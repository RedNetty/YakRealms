// === LootChestData.java ===
package com.rednetty.server.mechanics.lootchests.data;

import com.rednetty.server.mechanics.lootchests.types.ChestState;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import com.rednetty.server.mechanics.lootchests.types.LootChestLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains all data for a loot chest with thread-safe operations
 */
public class LootChestData {
    private final LootChestLocation location;
    private final ChestTier tier;
    private final ChestType type;
    private final long creationTime;

    // Volatile for thread safety
    private volatile ChestState state;
    private volatile long respawnTime;
    private volatile long lastInteractionTime;
    private volatile long lastStateChangeTime;

    // Thread-safe collections
    private final Set<UUID> playersWhoInteracted;
    private final Map<String, Object> metadata;

    public LootChestData(LootChestLocation location, ChestTier tier, ChestType type) {
        this.location = location;
        this.tier = tier;
        this.type = type;
        this.creationTime = System.currentTimeMillis();
        this.state = ChestState.AVAILABLE;
        this.respawnTime = 0;
        this.lastInteractionTime = 0;
        this.lastStateChangeTime = this.creationTime;
        this.playersWhoInteracted = ConcurrentHashMap.newKeySet();
        this.metadata = new ConcurrentHashMap<>();
    }

    // === Getters ===
    public LootChestLocation getLocation() { return location; }
    public ChestTier getTier() { return tier; }
    public ChestType getType() { return type; }
    public long getCreationTime() { return creationTime; }
    public ChestState getState() { return state; }
    public long getRespawnTime() { return respawnTime; }
    public long getLastInteractionTime() { return lastInteractionTime; }
    public long getLastStateChangeTime() { return lastStateChangeTime; }
    public Set<UUID> getPlayersWhoInteracted() { return new HashSet<>(playersWhoInteracted); }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    // === Setters ===
    public synchronized void setState(ChestState newState) {
        if (this.state != newState) {
            this.state = newState;
            this.lastStateChangeTime = System.currentTimeMillis();
            updateLastInteractionTime();
        }
    }

    public synchronized void setRespawnTime(long respawnTime) {
        this.respawnTime = respawnTime;
    }

    public synchronized void resetRespawnTime() {
        this.respawnTime = 0;
    }

    private synchronized void updateLastInteractionTime() {
        this.lastInteractionTime = System.currentTimeMillis();
    }

    // === Player Interaction Tracking ===
    public synchronized void addInteraction(UUID playerUuid) {
        playersWhoInteracted.add(playerUuid);
        updateLastInteractionTime();
    }

    public boolean hasPlayerInteracted(UUID playerUuid) {
        return playersWhoInteracted.contains(playerUuid);
    }

    public int getInteractionCount() {
        return playersWhoInteracted.size();
    }

    public synchronized void clearInteractions() {
        playersWhoInteracted.clear();
    }

    // === Metadata Management ===
    public void setMetadata(String key, Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
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

        // Care packages expire after 10 minutes regardless
        if (type == ChestType.CARE_PACKAGE) {
            return System.currentTimeMillis() - creationTime > 600000;
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

    public boolean isStuck() {
        // Consider a chest "stuck" if it's been in the same state for over 30 minutes
        // This helps identify chests that might have issues
        return System.currentTimeMillis() - lastStateChangeTime > 1800000; // 30 minutes
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

    public long getTimeSinceLastStateChange() {
        return System.currentTimeMillis() - lastStateChangeTime;
    }

    public long getRespawnTimeRemaining() {
        if (state != ChestState.RESPAWNING || respawnTime <= 0) {
            return 0;
        }
        return Math.max(0, respawnTime - System.currentTimeMillis());
    }

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

    // === Display Methods ===
    public String getDisplayName() {
        return tier.getColor() + tier.getDisplayName() + " " + type.getDescription();
    }

    public String getStatusString() {
        return switch (state) {
            case AVAILABLE -> tier.getColor() + "Available";
            case OPENED -> "§eOpened";
            case RESPAWNING -> "§cRespawning (" + getRespawnTimeRemainingFormatted() + ")";
            case EXPIRED -> "§8Expired";
        };
    }

    public String getDetailedStatusString() {
        StringBuilder status = new StringBuilder();
        status.append(getStatusString());

        if (isStuck()) {
            status.append(" §4[STUCK]");
        }

        if (playersWhoInteracted.size() > 0) {
            status.append(" §7(").append(playersWhoInteracted.size()).append(" interactions)");
        }

        return status.toString();
    }

    // === Debugging Methods ===
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debug = new HashMap<>();
        debug.put("location", location.toString());
        debug.put("tier", tier.name());
        debug.put("type", type.name());
        debug.put("state", state.name());
        debug.put("age", getAge() / 1000 + "s");
        debug.put("timeSinceLastInteraction", getTimeSinceLastInteraction() / 1000 + "s");
        debug.put("timeSinceLastStateChange", getTimeSinceLastStateChange() / 1000 + "s");
        debug.put("interactionCount", playersWhoInteracted.size());
        debug.put("respawnTimeRemaining", getRespawnTimeRemainingFormatted());
        debug.put("isExpired", isExpired());
        debug.put("isStuck", isStuck());
        debug.put("metadataKeys", new ArrayList<>(metadata.keySet()));
        return debug;
    }

    // === Reset Methods ===
    public synchronized void reset() {
        setState(ChestState.AVAILABLE);
        resetRespawnTime();
        clearInteractions();
        metadata.clear();
    }

    public synchronized void softReset() {
        setState(ChestState.AVAILABLE);
        resetRespawnTime();
        // Keep interactions and metadata
    }

    @Override
    public String toString() {
        return "LootChestData{" +
                "location=" + location +
                ", tier=" + tier +
                ", type=" + type +
                ", state=" + state +
                ", interactions=" + playersWhoInteracted.size() +
                ", age=" + (getAge() / 1000) + "s" +
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
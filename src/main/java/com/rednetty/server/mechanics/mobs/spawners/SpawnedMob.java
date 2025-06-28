package com.rednetty.server.mechanics.mobs.spawners;

import java.util.UUID;

/**
 * FIXED: Simplified SpawnedMob for synchronous respawn tracking
 * No complex state management, just simple timing
 */
public class SpawnedMob {
    private final UUID entityId;
    private final String mobType;
    private final int tier;
    private final boolean elite;
    private long respawnTime;

    /**
     * Constructor for a newly spawned mob
     *
     * @param entityId UUID of the entity
     * @param mobType  The mob type ID
     * @param tier     The tier level
     * @param elite    Whether it's an elite mob
     */
    public SpawnedMob(UUID entityId, String mobType, int tier, boolean elite) {
        this.entityId = entityId;
        this.mobType = mobType;
        this.tier = tier;
        this.elite = elite;
        this.respawnTime = -1; // Not set until death
    }

    /**
     * Get the entity UUID
     *
     * @return Entity UUID
     */
    public UUID getEntityId() {
        return entityId;
    }

    /**
     * Get the mob type
     *
     * @return Mob type ID
     */
    public String getMobType() {
        return mobType;
    }

    /**
     * Get the tier
     *
     * @return Tier level
     */
    public int getTier() {
        return tier;
    }

    /**
     * Check if this is an elite mob
     *
     * @return true if elite
     */
    public boolean isElite() {
        return elite;
    }

    /**
     * Get the respawn time
     *
     * @return Time in milliseconds when this mob should respawn
     */
    public long getRespawnTime() {
        return respawnTime;
    }

    /**
     * Set the respawn time
     *
     * @param respawnTime Time in milliseconds when this mob should respawn
     */
    public void setRespawnTime(long respawnTime) {
        this.respawnTime = respawnTime;
    }

    /**
     * Check if this mob is ready to respawn
     *
     * @param currentTime Current time in milliseconds
     * @return true if ready to respawn
     */
    public boolean isReadyToRespawn(long currentTime) {
        return respawnTime > 0 && respawnTime <= currentTime;
    }

    /**
     * Get time until respawn in milliseconds
     *
     * @param currentTime Current time in milliseconds
     * @return Time until respawn, or 0 if ready, or -1 if no valid respawn time
     */
    public long getTimeUntilRespawn(long currentTime) {
        if (respawnTime <= 0) {
            return -1; // Invalid respawn time
        }

        if (respawnTime <= currentTime) {
            return 0; // Ready to respawn
        }

        return respawnTime - currentTime;
    }

    /**
     * Check if this mob has a valid respawn time set
     *
     * @return true if respawn time is set
     */
    public boolean hasRespawnTime() {
        return respawnTime > 0;
    }

    /**
     * Get time until respawn in seconds
     *
     * @param currentTime Current time in milliseconds
     * @return Seconds until respawn
     */
    public long getSecondsUntilRespawn(long currentTime) {
        long millis = getTimeUntilRespawn(currentTime);
        if (millis < 0) return -1;
        return millis / 1000;
    }

    /**
     * Format this spawned mob as a string
     *
     * @return String representation
     */
    @Override
    public String toString() {
        return mobType + " T" + tier + (elite ? "+" : "") +
                " (ID: " + entityId.toString().substring(0, 8) +
                ", Respawn: " + (respawnTime > 0 ? respawnTime : "Not set") + ")";
    }

    /**
     * Get a key that uniquely identifies this mob type
     *
     * @return Unique key string
     */
    public String getKey() {
        return mobType + ":" + tier + "@" + elite;
    }

    /**
     * Convert to MobEntry
     *
     * @return A new MobEntry with amount 1
     */
    public MobEntry toMobEntry() {
        return new MobEntry(mobType, tier, elite, 1);
    }

    /**
     * Check if this spawned mob matches a mob entry
     *
     * @param entry The mob entry to compare
     * @return true if they match
     */
    public boolean matches(MobEntry entry) {
        return entry != null &&
                mobType.equals(entry.getMobType()) &&
                tier == entry.getTier() &&
                elite == entry.isElite();
    }

    /**
     * Create a copy of this spawned mob with a new entity ID
     *
     * @param newEntityId The new entity ID
     * @return A new SpawnedMob instance
     */
    public SpawnedMob withNewEntityId(UUID newEntityId) {
        SpawnedMob copy = new SpawnedMob(newEntityId, mobType, tier, elite);
        copy.respawnTime = this.respawnTime;
        return copy;
    }

    /**
     * Reset the respawn time (mark as not set)
     */
    public void clearRespawnTime() {
        this.respawnTime = -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpawnedMob that = (SpawnedMob) o;
        return tier == that.tier &&
                elite == that.elite &&
                entityId.equals(that.entityId) &&
                mobType.equals(that.mobType);
    }

    @Override
    public int hashCode() {
        int result = entityId.hashCode();
        result = 31 * result + mobType.hashCode();
        result = 31 * result + tier;
        result = 31 * result + (elite ? 1 : 0);
        return result;
    }
}
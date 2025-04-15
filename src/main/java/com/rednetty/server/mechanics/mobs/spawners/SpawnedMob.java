package com.rednetty.server.mechanics.mobs.spawners;

import java.util.UUID;

/**
 * Class representing a mob that was spawned from a spawner
 */
public class SpawnedMob {
    private final UUID entityId;
    private final String mobType;
    private final int tier;
    private final boolean elite;
    // Set to -1 to indicate no respawn timer is set until death occurs
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
        // Do not start the timer until the mob dies
        this.respawnTime = -1;
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
        // Only consider respawnable if a valid respawn time was set (> 0)
        // and enough time has passed (respawnTime <= currentTime)
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
}
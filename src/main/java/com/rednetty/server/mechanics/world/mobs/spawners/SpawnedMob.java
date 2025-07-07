package com.rednetty.server.mechanics.world.mobs.spawners;

import java.util.UUID;

/**
 * FIXED: Simplified SpawnedMob data class.
 * It only represents an active mob. Respawn state is handled by the Spawner.
 */
public class SpawnedMob {
    private final UUID entityId;
    private final String mobType;
    private final int tier;
    private final boolean elite;

    /**
     * Constructor for a newly spawned mob
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
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getMobType() {
        return mobType;
    }

    public int getTier() {
        return tier;
    }

    public boolean isElite() {
        return elite;
    }

    /**
     * Check if this spawned mob matches a mob entry's type definition.
     * @param entry The mob entry to compare
     * @return true if they match
     */
    public boolean matches(MobEntry entry) {
        return entry != null &&
                mobType.equals(entry.getMobType()) &&
                tier == entry.getTier() &&
                elite == entry.isElite();
    }

    @Override
    public String toString() {
        return "SpawnedMob[type=" + mobType + ", T" + tier + (elite ? "+" : "") + ", id=" + entityId + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpawnedMob that = (SpawnedMob) o;
        return entityId.equals(that.entityId);
    }

    @Override
    public int hashCode() {
        return entityId.hashCode();
    }
}
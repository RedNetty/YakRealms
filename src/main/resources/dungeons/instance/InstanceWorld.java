package com.rednetty.server.mechanics.dungeons.instance;

import org.bukkit.World;

import java.io.File;
import java.util.UUID;

/**
 * Instance World Representation
 *
 * Represents a single instanced world for a dungeon, containing all
 * necessary information for management and cleanup.
 *
 * Features:
 * - World reference management
 * - Lifecycle tracking
 * - Cleanup state management
 * - Validation methods
 * - Resource tracking
 */
public class InstanceWorld {

    // ================ CORE PROPERTIES ================

    private final UUID dungeonId;
    private final World world;
    private final InstanceManager.WorldTemplate template;
    private final File worldDirectory;
    private final long creationTime;

    // ================ STATE MANAGEMENT ================

    private boolean valid = true;
    private boolean cleanedUp = false;
    private long lastAccessTime;

    // ================ CONSTRUCTOR ================

    /**
     * Create a new instance world
     */
    public InstanceWorld(UUID dungeonId, World world, InstanceManager.WorldTemplate template, File worldDirectory) {
        this.dungeonId = dungeonId;
        this.world = world;
        this.template = template;
        this.worldDirectory = worldDirectory;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessTime = creationTime;
    }

    // ================ LIFECYCLE MANAGEMENT ================

    /**
     * Mark this instance as accessed
     */
    public void updateLastAccess() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    /**
     * Check if this instance is expired
     */
    public boolean isExpired(long currentTime, long timeoutMillis) {
        return (currentTime - lastAccessTime) > timeoutMillis;
    }

    /**
     * Check if this instance is valid
     */
    public boolean isValid() {
        return valid && !cleanedUp && world != null;
    }

    /**
     * Mark this instance as invalid
     */
    public void invalidate() {
        this.valid = false;
    }

    /**
     * Clean up this instance
     */
    public void cleanup() {
        this.cleanedUp = true;
        this.valid = false;
    }

    // ================ GETTERS ================

    public UUID getDungeonId() { return dungeonId; }
    public World getWorld() { return world; }
    public InstanceManager.WorldTemplate getTemplate() { return template; }
    public File getWorldDirectory() { return worldDirectory; }
    public long getCreationTime() { return creationTime; }
    public long getLastAccessTime() { return lastAccessTime; }
    public boolean isCleanedUp() { return cleanedUp; }

    // ================ UTILITY METHODS ================

    /**
     * Get the age of this instance in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Get time since last access in milliseconds
     */
    public long getTimeSinceLastAccess() {
        return System.currentTimeMillis() - lastAccessTime;
    }

    /**
     * Get world name
     */
    public String getWorldName() {
        return world != null ? world.getName() : "null";
    }

    @Override
    public String toString() {
        return String.format("InstanceWorld[dungeon=%s, world=%s, valid=%s, age=%dms]",
                dungeonId.toString().substring(0, 8),
                getWorldName(),
                isValid(),
                getAge());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        InstanceWorld that = (InstanceWorld) obj;
        return dungeonId.equals(that.dungeonId);
    }

    @Override
    public int hashCode() {
        return dungeonId.hashCode();
    }
}
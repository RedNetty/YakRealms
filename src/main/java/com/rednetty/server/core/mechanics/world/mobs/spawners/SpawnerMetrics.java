package com.rednetty.server.core.mechanics.world.mobs.spawners;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Class to track performance metrics for a spawner
 */
public class SpawnerMetrics {
    private int totalSpawned = 0;
    private int totalKilled = 0;
    private long lastSpawnTime = 0;
    private long averageRespawnTime = 0;
    private long creationTime;
    private int failedSpawnAttempts = 0;
    private final Map<Integer, Integer> tierSpawnCounts = new HashMap<>();
    private final Map<String, Integer> mobTypeSpawnCounts = new HashMap<>();

    /**
     * Constructor
     */
    public SpawnerMetrics() {
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Record a successful spawn
     *
     * @param count Number of mobs spawned
     */
    public void recordSpawn(int count) {
        totalSpawned += count;
        lastSpawnTime = System.currentTimeMillis();
    }

    /**
     * Record a spawn with detailed type information
     *
     * @param count   Number of mobs spawned
     * @param tier    The tier level
     * @param mobType The mob type
     */
    public void recordSpawnByType(int count, int tier, String mobType) {
        totalSpawned += count;
        lastSpawnTime = System.currentTimeMillis();

        // Track tier statistics
        tierSpawnCounts.put(tier, tierSpawnCounts.getOrDefault(tier, 0) + count);

        // Track mob type statistics
        mobTypeSpawnCounts.put(mobType, mobTypeSpawnCounts.getOrDefault(mobType, 0) + count);
    }

    /**
     * Record a mob kill
     */
    public void recordKill() {
        totalKilled++;
    }

    /**
     * Record multiple kills at once
     *
     * @param count Number of kills to record
     */
    public void recordKills(int count) {
        if (count > 0) {
            totalKilled += count;
        }
    }

    /**
     * Update average respawn time
     *
     * @param respawnTime The respawn time for a mob
     */
    public void updateRespawnTime(long respawnTime) {
        if (averageRespawnTime == 0) {
            averageRespawnTime = respawnTime;
        } else {
            // Moving average calculation
            averageRespawnTime = (averageRespawnTime * 3 + respawnTime) / 4;
        }
    }

    /**
     * Record a failed spawn attempt
     */
    public void recordFailedSpawn() {
        failedSpawnAttempts++;
    }

    /**
     * Get total spawned mob count
     *
     * @return Total number of mobs spawned
     */
    public int getTotalSpawned() {
        return totalSpawned;
    }

    /**
     * Get total killed mob count
     *
     * @return Total number of mobs killed
     */
    public int getTotalKilled() {
        return totalKilled;
    }

    /**
     * Get the last spawn time
     *
     * @return Timestamp of last spawn
     */
    public long getLastSpawnTime() {
        return lastSpawnTime;
    }

    /**
     * Get time since last spawn in seconds
     *
     * @return Seconds since last spawn, or -1 if never spawned
     */
    public long getSecondsSinceLastSpawn() {
        if (lastSpawnTime == 0) {
            return -1;
        }

        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastSpawnTime);
    }

    /**
     * Get the average respawn time
     *
     * @return Average respawn time in milliseconds
     */
    public long getAverageRespawnTime() {
        return averageRespawnTime;
    }

    /**
     * Get average respawn time in seconds
     *
     * @return Average respawn time in seconds
     */
    public long getAverageRespawnTimeSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(averageRespawnTime);
    }

    /**
     * Get the number of failed spawn attempts
     *
     * @return Failed attempts count
     */
    public int getFailedSpawnAttempts() {
        return failedSpawnAttempts;
    }

    /**
     * Get the creation time of this spawner
     *
     * @return Creation timestamp
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Get the uptime of this spawner in seconds
     *
     * @return Uptime in seconds
     */
    public long getUptimeSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - creationTime);
    }

    /**
     * Get uptime as a formatted string (days, hours, minutes)
     *
     * @return Formatted uptime string
     */
    public String getFormattedUptime() {
        long uptimeSeconds = getUptimeSeconds();

        long days = uptimeSeconds / 86400;
        uptimeSeconds %= 86400;

        long hours = uptimeSeconds / 3600;
        uptimeSeconds %= 3600;

        long minutes = uptimeSeconds / 60;

        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            result.append(hours).append("h ");
        }
        result.append(minutes).append("m");

        return result.toString();
    }

    /**
     * Get spawn counts by tier
     *
     * @return Map of tier to spawn count
     */
    public Map<Integer, Integer> getTierSpawnCounts() {
        return new HashMap<>(tierSpawnCounts);
    }

    /**
     * Get spawn counts by mob type
     *
     * @return Map of mob type to spawn count
     */
    public Map<String, Integer> getMobTypeSpawnCounts() {
        return new HashMap<>(mobTypeSpawnCounts);
    }

    /**
     * Calculate spawner efficiency as percentage of successful spawns
     *
     * @return Efficiency percentage
     */
    public double getEfficiency() {
        int attempts = totalSpawned + failedSpawnAttempts;
        if (attempts == 0) return 100.0;
        return (double) totalSpawned / attempts * 100.0;
    }

    /**
     * Get spawn rate (spawns per hour)
     *
     * @return Spawn rate
     */
    public double getSpawnRate() {
        long uptime = System.currentTimeMillis() - creationTime;
        if (uptime <= 0) return 0;

        // Calculate spawns per hour
        return (double) totalSpawned / (uptime / 3600000.0);
    }

    /**
     * Get kill rate (kills per hour)
     *
     * @return Kill rate
     */
    public double getKillRate() {
        long uptime = System.currentTimeMillis() - creationTime;
        if (uptime <= 0) return 0;

        // Calculate kills per hour
        return (double) totalKilled / (uptime / 3600000.0);
    }

    /**
     * Get survival rate (percentage of mobs that haven't been killed)
     *
     * @return Survival rate percentage
     */
    public double getSurvivalRate() {
        if (totalSpawned == 0) return 0;
        return Math.max(0, (double) (totalSpawned - totalKilled) / totalSpawned * 100.0);
    }

    /**
     * Get kill-to-spawn ratio
     *
     * @return Ratio of kills to spawns
     */
    public double getKillSpawnRatio() {
        if (totalSpawned == 0) return 0;
        return (double) totalKilled / totalSpawned;
    }

    /**
     * Get the most commonly spawned mob type
     *
     * @return Most common mob type
     */
    public String getMostCommonMobType() {
        if (mobTypeSpawnCounts.isEmpty()) return "None";

        return mobTypeSpawnCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    /**
     * Get the most common tier
     *
     * @return Most common tier
     */
    public int getMostCommonTier() {
        if (tierSpawnCounts.isEmpty()) return 0;

        return tierSpawnCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    /**
     * Reset all metrics
     */
    public void reset() {
        totalSpawned = 0;
        totalKilled = 0;
        lastSpawnTime = 0;
        averageRespawnTime = 0;
        failedSpawnAttempts = 0;
        tierSpawnCounts.clear();
        mobTypeSpawnCounts.clear();
        creationTime = System.currentTimeMillis();
    }
}
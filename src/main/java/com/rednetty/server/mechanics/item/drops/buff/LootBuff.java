package com.rednetty.server.mechanics.item.drops.buff;

import java.util.UUID;

/**
 *: Represents an active loot buff that increases drop rates with improved validation and thread safety
 *
 * Key improvements:
 * - Thread-safe state management
 * - Better validation and error handling
 * - Enhanced time tracking and calculations
 * - Improved debugging and monitoring capabilities
 */
public class LootBuff {
    private final String ownerName;
    private final UUID ownerId;
    private final int buffRate;
    private final int durationSeconds;
    private volatile int elapsedSeconds = 0;
    private final long creationTime;

    /**
     * Creates a new loot buff with validation
     *
     * @param ownerName       Player name who activated the buff
     * @param ownerId         Player UUID who activated the buff
     * @param buffRate        Percentage increase for drop rates (must be > 0)
     * @param durationSeconds Duration in seconds (must be > 0)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public LootBuff(String ownerName, UUID ownerId, int buffRate, int durationSeconds) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner name cannot be null or empty");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner UUID cannot be null");
        }
        if (buffRate <= 0) {
            throw new IllegalArgumentException("Buff rate must be greater than 0, got: " + buffRate);
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0, got: " + durationSeconds);
        }

        this.ownerName = ownerName;
        this.ownerId = ownerId;
        this.buffRate = buffRate;
        this.durationSeconds = durationSeconds;
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Creates a new loot buff with duration in minutes
     *
     * @param ownerName       Player name who activated the buff
     * @param ownerId         Player UUID who activated the buff
     * @param buffRate        Percentage increase for drop rates
     * @param durationMinutes Duration in minutes
     * @return The new loot buff
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static LootBuff createWithMinutes(String ownerName, UUID ownerId, int buffRate, int durationMinutes) {
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration in minutes must be greater than 0, got: " + durationMinutes);
        }
        return new LootBuff(ownerName, ownerId, buffRate, durationMinutes * 60);
    }

    /**
     * Get the player name who activated the buff
     *
     * @return The owner name (never null)
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Get the UUID of the player who activated the buff
     *
     * @return The owner UUID (never null)
     */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * Get the buff rate (percentage increase)
     *
     * @return The buff rate (always > 0)
     */
    public int getBuffRate() {
        return buffRate;
    }

    /**
     * ADDED: Get the elite buff rate (typically half of normal rate)
     *
     * @return The elite buff rate
     */
    public int getEliteBuffRate() {
        return Math.max(1, buffRate / 2);
    }

    /**
     * Get the total duration in seconds
     *
     * @return The duration in seconds (always > 0)
     */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Get the total duration in minutes
     *
     * @return The duration in minutes
     */
    public int getDurationMinutes() {
        return durationSeconds / 60;
    }

    /**
     * Get the elapsed time in seconds (thread-safe)
     *
     * @return The elapsed seconds
     */
    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    /**
     * Get the remaining time in seconds (thread-safe)
     *
     * @return The remaining seconds (never negative)
     */
    public int getRemainingSeconds() {
        return Math.max(0, durationSeconds - elapsedSeconds);
    }

    /**
     * Check if the buff has expired (thread-safe)
     *
     * @return true if the buff has expired
     */
    public boolean expired() {
        return elapsedSeconds >= durationSeconds;
    }

    /**
     * Update the buff timers (called once per second) - thread-safe
     */
    public synchronized void update() {
        if (!expired()) {
            elapsedSeconds++;
        }
    }

    /**
     * Get the percentage of time remaining
     *
     * @return Percentage from 0.0 to 1.0
     */
    public double getTimeRemainingPercentage() {
        if (durationSeconds <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) getRemainingSeconds() / durationSeconds));
    }

    /**
     * ADDED: Get the percentage of time elapsed
     *
     * @return Percentage from 0.0 to 1.0
     */
    public double getTimeElapsedPercentage() {
        if (durationSeconds <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, (double) elapsedSeconds / durationSeconds));
    }

    /**
     * ADDED: Get the creation time of this buff
     *
     * @return Creation timestamp in milliseconds
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * ADDED: Get the age of this buff in milliseconds
     *
     * @return Age in milliseconds
     */
    public long getAgeMillis() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * ADDED: Check if this buff is still active (not expired and has time remaining)
     *
     * @return true if the buff is active
     */
    public boolean isActive() {
        return !expired() && getRemainingSeconds() > 0;
    }

    /**
     * ADDED: Get a formatted time string for the remaining time
     *
     * @return Formatted time string (e.g., "5m 30s", "45s")
     */
    public String getFormattedRemainingTime() {
        int remaining = getRemainingSeconds();
        if (remaining <= 0) {
            return "Expired";
        }

        int minutes = remaining / 60;
        int seconds = remaining % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * ADDED: Get a formatted time string for the elapsed time
     *
     * @return Formatted time string (e.g., "2m 15s", "30s")
     */
    public String getFormattedElapsedTime() {
        int elapsed = getElapsedSeconds();
        int minutes = elapsed / 60;
        int seconds = elapsed % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * ADDED: Check if the buff is in its final minutes
     *
     * @param thresholdMinutes The threshold for "final minutes"
     * @return true if remaining time is less than threshold
     */
    public boolean isInFinalMinutes(int thresholdMinutes) {
        return getRemainingSeconds() <= (thresholdMinutes * 60);
    }

    /**
     * ADDED: Get buff effectiveness based on time remaining
     * This could be used for buffs that become more/less effective over time
     *
     * @return Effectiveness multiplier (1.0 = full effectiveness)
     */
    public double getEffectiveness() {
        // For now, buffs maintain full effectiveness throughout their duration
        // This could be modified for special buff types that decay over time
        return isActive() ? 1.0 : 0.0;
    }

    /**
     * ADDED: Calculate the actual buff rate with effectiveness
     *
     * @return The effective buff rate
     */
    public int getEffectiveBuffRate() {
        return (int) Math.round(buffRate * getEffectiveness());
    }

    /**
     * ADDED: Calculate the actual elite buff rate with effectiveness
     *
     * @return The effective elite buff rate
     */
    public int getEffectiveEliteBuffRate() {
        return (int) Math.round(getEliteBuffRate() * getEffectiveness());
    }

    /**
     * ADDED: Check if two buffs are from the same player
     *
     * @param other The other buff to compare
     * @return true if both buffs are from the same player
     */
    public boolean isSameOwner(LootBuff other) {
        if (other == null) return false;
        return this.ownerId.equals(other.ownerId);
    }

    /**
     * ADDED: Compare buff strength (higher rate = stronger)
     *
     * @param other The other buff to compare
     * @return positive if this buff is stronger, negative if weaker, 0 if equal
     */
    public int compareStrength(LootBuff other) {
        if (other == null) return 1;
        return Integer.compare(this.buffRate, other.buffRate);
    }

    /**
     * ADDED: Get detailed buff information for debugging
     *
     * @return Map of buff information
     */
    public java.util.Map<String, Object> getDebugInfo() {
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("ownerName", ownerName);
        info.put("ownerId", ownerId.toString());
        info.put("buffRate", buffRate);
        info.put("eliteBuffRate", getEliteBuffRate());
        info.put("durationSeconds", durationSeconds);
        info.put("durationMinutes", getDurationMinutes());
        info.put("elapsedSeconds", elapsedSeconds);
        info.put("remainingSeconds", getRemainingSeconds());
        info.put("expired", expired());
        info.put("active", isActive());
        info.put("timeRemainingPercentage", getTimeRemainingPercentage());
        info.put("timeElapsedPercentage", getTimeElapsedPercentage());
        info.put("formattedRemainingTime", getFormattedRemainingTime());
        info.put("formattedElapsedTime", getFormattedElapsedTime());
        info.put("creationTime", creationTime);
        info.put("ageMillis", getAgeMillis());
        info.put("effectiveness", getEffectiveness());
        info.put("effectiveBuffRate", getEffectiveBuffRate());
        info.put("effectiveEliteBuffRate", getEffectiveEliteBuffRate());
        return info;
    }

    /**
     * toString for debugging
     */
    @Override
    public String toString() {
        return String.format("LootBuff{owner='%s', rate=%d%%, remaining=%s, active=%s}",
                ownerName, buffRate, getFormattedRemainingTime(), isActive());
    }

    /**
     * equals for proper comparison
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        LootBuff lootBuff = (LootBuff) obj;
        return buffRate == lootBuff.buffRate &&
                durationSeconds == lootBuff.durationSeconds &&
                elapsedSeconds == lootBuff.elapsedSeconds &&
                ownerName.equals(lootBuff.ownerName) &&
                ownerId.equals(lootBuff.ownerId);
    }

    /**
     * hashCode for proper hashing
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(ownerName, ownerId, buffRate, durationSeconds, elapsedSeconds);
    }
}
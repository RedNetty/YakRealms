package com.rednetty.server.mechanics.drops.buff;

import java.util.UUID;

/**
 * Represents an active loot buff that increases drop rates
 */
public class LootBuff {
    private final String ownerName;
    private final UUID ownerId;
    private final int buffRate;
    private final int durationSeconds;
    private int elapsedSeconds = 0;

    /**
     * Creates a new loot buff
     *
     * @param ownerName       Player name who activated the buff
     * @param ownerId         Player UUID who activated the buff
     * @param buffRate        Percentage increase for drop rates
     * @param durationSeconds Duration in seconds
     */
    public LootBuff(String ownerName, UUID ownerId, int buffRate, int durationSeconds) {
        this.ownerName = ownerName;
        this.ownerId = ownerId;
        this.buffRate = buffRate;
        this.durationSeconds = durationSeconds;
    }

    /**
     * Creates a new loot buff with duration in minutes
     *
     * @param ownerName       Player name who activated the buff
     * @param ownerId         Player UUID who activated the buff
     * @param buffRate        Percentage increase for drop rates
     * @param durationMinutes Duration in minutes
     * @return The new loot buff
     */
    public static LootBuff createWithMinutes(String ownerName, UUID ownerId, int buffRate, int durationMinutes) {
        return new LootBuff(ownerName, ownerId, buffRate, durationMinutes * 60);
    }

    /**
     * Get the player name who activated the buff
     *
     * @return The owner name
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Get the UUID of the player who activated the buff
     *
     * @return The owner UUID
     */
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * Get the buff rate (percentage increase)
     *
     * @return The buff rate
     */
    public int getBuffRate() {
        return buffRate;
    }

    /**
     * Get the total duration in seconds
     *
     * @return The duration in seconds
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
     * Get the elapsed time in seconds
     *
     * @return The elapsed seconds
     */
    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    /**
     * Get the remaining time in seconds
     *
     * @return The remaining seconds
     */
    public int getRemainingSeconds() {
        return Math.max(0, durationSeconds - elapsedSeconds);
    }

    /**
     * Check if the buff has expired
     *
     * @return true if the buff has expired
     */
    public boolean expired() {
        return elapsedSeconds >= durationSeconds;
    }

    /**
     * Update the buff timers (called once per second)
     */
    public void update() {
        elapsedSeconds++;
    }

    /**
     * Get the percentage of time remaining
     *
     * @return Percentage from 0.0 to 1.0
     */
    public double getTimeRemainingPercentage() {
        if (durationSeconds <= 0) return 0;
        return Math.max(0, Math.min(1.0, (double) getRemainingSeconds() / durationSeconds));
    }
}
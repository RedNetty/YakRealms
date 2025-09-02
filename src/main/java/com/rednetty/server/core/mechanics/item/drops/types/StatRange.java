package com.rednetty.server.core.mechanics.item.drops.types;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a range of values for a stat with minimum and maximum bounds
 * Used for generating random values within a specified range for elite drops
 */
public class StatRange {

    private final int min;
    private final int max;

    /**
     * Constructor for a stat range
     *
     * @param min The minimum value (inclusive)
     * @param max The maximum value (inclusive)
     */
    public StatRange(int min, int max) {
        if (min > max) {
            // Swap if min is greater than max
            this.min = max;
            this.max = min;
        } else {
            this.min = min;
            this.max = max;
        }
    }

    /**
     * Constructor for a single value (min == max)
     *
     * @param value The  value
     */
    public StatRange(int value) {
        this.min = value;
        this.max = value;
    }

    // ===== GETTERS =====

    /**
     * Create a range from a single value
     *
     * @param value The value
     * @return A StatRange with min == max == value
     */
    public static StatRange of(int value) {
        return new StatRange(value);
    }

    /**
     * Create a range from min and max values
     *
     * @param min The minimum value
     * @param max The maximum value
     * @return A new StatRange
     */
    public static StatRange of(int min, int max) {
        return new StatRange(min, max);
    }

    /**
     * Create an empty range (0-0)
     *
     * @return A StatRange with min == max == 0
     */
    public static StatRange empty() {
        return new StatRange(0);
    }

    /**
     * Parse a string representation of a range
     * Supports formats: "5", "5-10", "5 to 10", "5..10"
     *
     * @param rangeString The string to parse
     * @return A StatRange parsed from the string
     * @throws IllegalArgumentException if the string format is invalid
     */
    public static StatRange parse(String rangeString) throws IllegalArgumentException {
        if (rangeString == null || rangeString.trim().isEmpty()) {
            throw new IllegalArgumentException("Range string cannot be null or empty");
        }

        String cleaned = rangeString.trim().toLowerCase();

        try {
            // Handle single value
            if (!cleaned.contains("-") && !cleaned.contains("to") && !cleaned.contains("..")) {
                int value = Integer.parseInt(cleaned);
                return new StatRange(value);
            }

            // Handle range formats
            String[] parts;
            if (cleaned.contains(" to ")) {
                parts = cleaned.split(" to ");
            } else if (cleaned.contains("..")) {
                parts = cleaned.split("\\.\\.");
            } else {
                parts = cleaned.split("-");
            }

            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid range format: " + rangeString);
            }

            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());

            return new StatRange(min, max);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in range: " + rangeString, e);
        }
    }

    // ===== VALUE GENERATION =====

    /**
     * Create a percentage range (0-100)
     *
     * @return A StatRange from 0 to 100
     */
    public static StatRange percentage() {
        return new StatRange(0, 100);
    }

    /**
     * Create a damage range based on tier
     *
     * @param tier The tier (1-6)
     * @return An appropriate damage range for the tier
     */
    public static StatRange damageForTier(int tier) {
        switch (tier) {
            case 1:
                return new StatRange(5, 15);
            case 2:
                return new StatRange(15, 30);
            case 3:
                return new StatRange(30, 60);
            case 4:
                return new StatRange(60, 120);
            case 5:
                return new StatRange(120, 240);
            case 6:
                return new StatRange(240, 480);
            default:
                return new StatRange(5, 15);
        }
    }

    /**
     * Create an HP range based on tier
     *
     * @param tier The tier (1-6)
     * @return An appropriate HP range for the tier
     */
    public static StatRange hpForTier(int tier) {
        switch (tier) {
            case 1:
                return new StatRange(50, 150);
            case 2:
                return new StatRange(150, 400);
            case 3:
                return new StatRange(400, 800);
            case 4:
                return new StatRange(800, 1600);
            case 5:
                return new StatRange(1600, 3200);
            case 6:
                return new StatRange(3200, 6400);
            default:
                return new StatRange(50, 150);
        }
    }

    /**
     * Get the minimum value
     */
    public int getMin() {
        return min;
    }

    // ===== VALIDATION =====

    /**
     * Get the maximum value
     */
    public int getMax() {
        return max;
    }

    /**
     * Get the range (max - min)
     */
    public int getRange() {
        return max - min;
    }

    /**
     * Get the average value
     */
    public double getAverage() {
        return (min + max) / 2.0;
    }

    /**
     * Generate a random value within this range (inclusive)
     *
     * @return A random integer between min and max (inclusive)
     */
    public int getRandomValue() {
        if (min == max) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    // ===== UTILITY METHODS =====

    /**
     * Generate a random value with a bias towards the minimum
     *
     * @param bias The bias factor (0.0 = no bias, 1.0 = always minimum)
     * @return A biased random value
     */
    public int getRandomValueBiasedToMin(double bias) {
        if (min == max) {
            return min;
        }

        bias = Math.max(0.0, Math.min(1.0, bias)); // Clamp bias between 0 and 1
        double random = ThreadLocalRandom.current().nextDouble();

        // Apply bias towards minimum
        double biasedRandom = Math.pow(random, 1.0 / (1.0 - bias + 0.001)); // Add small value to avoid division by zero

        return min + (int) (biasedRandom * (max - min + 1));
    }

    /**
     * Generate a random value with a bias towards the maximum
     *
     * @param bias The bias factor (0.0 = no bias, 1.0 = always maximum)
     * @return A biased random value
     */
    public int getRandomValueBiasedToMax(double bias) {
        if (min == max) {
            return min;
        }

        bias = Math.max(0.0, Math.min(1.0, bias)); // Clamp bias between 0 and 1
        double random = ThreadLocalRandom.current().nextDouble();

        // Apply bias towards maximum
        double biasedRandom = 1.0 - Math.pow(1.0 - random, 1.0 / (1.0 - bias + 0.001));

        return min + (int) (biasedRandom * (max - min + 1));
    }

    /**
     * Generate multiple random values and return the average
     *
     * @param samples The number of samples to take
     * @return The average of the random samples
     */
    public int getAverageRandomValue(int samples) {
        if (samples <= 0) {
            return getRandomValue();
        }

        long sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += getRandomValue();
        }

        return (int) (sum / samples);
    }

    /**
     * Check if a value is within this range
     *
     * @param value The value to check
     * @return true if the value is within the range (inclusive)
     */
    public boolean contains(int value) {
        return value >= min && value <= max;
    }

    /**
     * Check if this range is valid (min <= max)
     */
    public boolean isValid() {
        return min <= max;
    }

    /**
     * Check if this is a single value range (min == max)
     */
    public boolean isSingleValue() {
        return min == max;
    }

    /**
     * Check if this range is empty (both min and max are 0 or negative)
     */
    public boolean isEmpty() {
        return max <= 0;
    }

    // ===== OBJECT METHODS =====

    /**
     * Clamp a value to this range
     *
     * @param value The value to clamp
     * @return The value clamped to this range
     */
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Scale this range by a factor
     *
     * @param factor The scaling factor
     * @return A new StatRange scaled by the factor
     */
    public StatRange scale(double factor) {
        int newMin = (int) (min * factor);
        int newMax = (int) (max * factor);
        return new StatRange(newMin, newMax);
    }

    /**
     * Add a value to both min and max
     *
     * @param value The value to add
     * @return A new StatRange with the value added
     */
    public StatRange add(int value) {
        return new StatRange(min + value, max + value);
    }

    // ===== STATIC FACTORY METHODS =====

    /**
     * Subtract a value from both min and max
     *
     * @param value The value to subtract
     * @return A new StatRange with the value subtracted
     */
    public StatRange subtract(int value) {
        return new StatRange(min - value, max - value);
    }

    /**
     * Create a new range that represents the overlap between this range and another
     *
     * @param other The other range
     * @return A new StatRange representing the overlap, or null if no overlap
     */
    public StatRange intersect(StatRange other) {
        if (other == null) {
            return null;
        }

        int overlapMin = Math.max(this.min, other.min);
        int overlapMax = Math.min(this.max, other.max);

        if (overlapMin <= overlapMax) {
            return new StatRange(overlapMin, overlapMax);
        }

        return null; // No overlap
    }

    /**
     * Create a new range that represents the union of this range and another
     *
     * @param other The other range
     * @return A new StatRange representing the union
     */
    public StatRange union(StatRange other) {
        if (other == null) {
            return new StatRange(this.min, this.max);
        }

        int unionMin = Math.min(this.min, other.min);
        int unionMax = Math.max(this.max, other.max);

        return new StatRange(unionMin, unionMax);
    }

    /**
     * Check if this range overlaps with another range
     *
     * @param other The other range to check
     * @return true if the ranges overlap
     */
    public boolean overlaps(StatRange other) {
        if (other == null) {
            return false;
        }

        return !(this.max < other.min || this.min > other.max);
    }

    /**
     * Get a string representation of this range
     */
    @Override
    public String toString() {
        if (min == max) {
            return String.valueOf(min);
        } else {
            return min + "-" + max;
        }
    }

    /**
     * Check if two StatRange objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        StatRange statRange = (StatRange) obj;
        return min == statRange.min && max == statRange.max;
    }

    /**
     * Get hash code for this object
     */
    @Override
    public int hashCode() {
        int result = min;
        result = 31 * result + max;
        return result;
    }
}
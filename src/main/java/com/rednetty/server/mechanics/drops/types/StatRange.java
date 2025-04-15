package com.rednetty.server.mechanics.drops.types;

import java.util.concurrent.ThreadLocalRandom;

public class StatRange {
    private int min;
    private int max;

    public StatRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    /**
     * Get a random value within the range
     *
     * @return Random value between min and max (inclusive)
     */
    /**
     * Gets a random value within the range
     *
     * @return Random value between min and max (inclusive)
     */
    public int getRandomValue() {
        // Ensure max is at least min + 1 to avoid IllegalArgumentException
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Get a random value with probability skewed toward min value
     *
     * @return Random value with higher chance of being closer to min
     */
    public int getSkewedLowValue() {
        if (min >= max) return min;

        // Calculate a range that's 25% of the full range
        int range = max - min;
        int quarterRange = Math.max(1, range / 4);

        // Higher chance of lower values
        return min + ThreadLocalRandom.current().nextInt(quarterRange + 1);
    }

    /**
     * Get a random value with probability skewed toward max value
     *
     * @return Random value with higher chance of being closer to max
     */
    public int getSkewedHighValue() {
        if (min >= max) return max;

        // Calculate a range that starts at 75% of the way from min to max
        int range = max - min;
        int threeQuarters = min + (range * 3 / 4);

        // Higher chance of higher values
        return threeQuarters + ThreadLocalRandom.current().nextInt(max - threeQuarters + 1);
    }
}
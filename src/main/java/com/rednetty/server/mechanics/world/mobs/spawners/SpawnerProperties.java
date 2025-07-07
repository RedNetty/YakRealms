package com.rednetty.server.mechanics.world.mobs;

import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Class representing configurable properties for a spawner
 */
public class SpawnerProperties {
    private boolean timeRestricted = false;
    private int startHour = 0;
    private int endHour = 24;
    private boolean weatherRestricted = false;
    private boolean spawnInClear = true;
    private boolean spawnInRain = true;
    private boolean spawnInThunder = true;
    private String spawnerGroup = "";
    private int priority = 0;
    private double spawnRadiusX = 3.0;
    private double spawnRadiusY = 1.0;
    private double spawnRadiusZ = 3.0;
    private int maxMobOverride = -1;
    private double playerDetectionRangeOverride = -1;
    private String displayName = "";

    /**
     * Default constructor
     */
    public SpawnerProperties() {
        // Initialize with defaults
    }

    /**
     * Check if the spawner can spawn based on time restrictions
     *
     * @param world The world to check
     * @return true if can spawn
     */
    public boolean canSpawnByTime(World world) {
        if (!timeRestricted) return true;

        // Get current time
        long worldTime = world.getTime();
        int hour = (int) ((worldTime / 1000 + 6) % 24); // Convert to hours, Minecraft day starts at 6am

        // Check if current time is within allowed range
        if (startHour <= endHour) {
            return hour >= startHour && hour < endHour;
        } else {
            // Handles overnight ranges (e.g., 22-6)
            return hour >= startHour || hour < endHour;
        }
    }

    /**
     * Check if the spawner can spawn based on weather restrictions
     *
     * @param world The world to check
     * @return true if can spawn
     */
    public boolean canSpawnByWeather(World world) {
        if (!weatherRestricted) return true;

        boolean isThundering = world.isThundering();
        boolean isRaining = world.hasStorm();

        if (isThundering) {
            return spawnInThunder;
        } else if (isRaining) {
            return spawnInRain;
        } else {
            return spawnInClear;
        }
    }

    /**
     * Save properties to a configuration section
     *
     * @param config The configuration section
     */
    public void saveToConfig(ConfigurationSection config) {
        config.set("timeRestricted", timeRestricted);
        config.set("startHour", startHour);
        config.set("endHour", endHour);
        config.set("weatherRestricted", weatherRestricted);
        config.set("spawnInClear", spawnInClear);
        config.set("spawnInRain", spawnInRain);
        config.set("spawnInThunder", spawnInThunder);
        config.set("spawnerGroup", spawnerGroup);
        config.set("priority", priority);
        config.set("spawnRadiusX", spawnRadiusX);
        config.set("spawnRadiusY", spawnRadiusY);
        config.set("spawnRadiusZ", spawnRadiusZ);
        config.set("maxMobOverride", maxMobOverride);
        config.set("playerDetectionRangeOverride", playerDetectionRangeOverride);
        config.set("displayName", displayName);
    }

    /**
     * Load properties from a configuration section
     *
     * @param config The configuration section
     * @return The loaded properties
     */
    public static SpawnerProperties loadFromConfig(ConfigurationSection config) {
        SpawnerProperties props = new SpawnerProperties();

        if (config == null) return props;

        props.timeRestricted = config.getBoolean("timeRestricted", false);
        props.startHour = config.getInt("startHour", 0);
        props.endHour = config.getInt("endHour", 24);
        props.weatherRestricted = config.getBoolean("weatherRestricted", false);
        props.spawnInClear = config.getBoolean("spawnInClear", true);
        props.spawnInRain = config.getBoolean("spawnInRain", true);
        props.spawnInThunder = config.getBoolean("spawnInThunder", true);
        props.spawnerGroup = config.getString("spawnerGroup", "");
        props.priority = config.getInt("priority", 0);
        props.spawnRadiusX = config.getDouble("spawnRadiusX", 3.0);
        props.spawnRadiusY = config.getDouble("spawnRadiusY", 1.0);
        props.spawnRadiusZ = config.getDouble("spawnRadiusZ", 3.0);
        props.maxMobOverride = config.getInt("maxMobOverride", -1);
        props.playerDetectionRangeOverride = config.getDouble("playerDetectionRangeOverride", -1);
        props.displayName = config.getString("displayName", "");

        return props;
    }

    /**
     * Clone these properties
     *
     * @return A new SpawnerProperties instance with the same values
     */
    public SpawnerProperties clone() {
        SpawnerProperties clone = new SpawnerProperties();
        clone.timeRestricted = this.timeRestricted;
        clone.startHour = this.startHour;
        clone.endHour = this.endHour;
        clone.weatherRestricted = this.weatherRestricted;
        clone.spawnInClear = this.spawnInClear;
        clone.spawnInRain = this.spawnInRain;
        clone.spawnInThunder = this.spawnInThunder;
        clone.spawnerGroup = this.spawnerGroup;
        clone.priority = this.priority;
        clone.spawnRadiusX = this.spawnRadiusX;
        clone.spawnRadiusY = this.spawnRadiusY;
        clone.spawnRadiusZ = this.spawnRadiusZ;
        clone.maxMobOverride = this.maxMobOverride;
        clone.playerDetectionRangeOverride = this.playerDetectionRangeOverride;
        clone.displayName = this.displayName;
        return clone;
    }

    // Getters and setters

    public boolean isTimeRestricted() {
        return timeRestricted;
    }

    public void setTimeRestricted(boolean timeRestricted) {
        this.timeRestricted = timeRestricted;
    }

    public int getStartHour() {
        return startHour;
    }

    public void setStartHour(int startHour) {
        this.startHour = Math.max(0, Math.min(23, startHour));
    }

    public int getEndHour() {
        return endHour;
    }

    public void setEndHour(int endHour) {
        this.endHour = Math.max(0, Math.min(24, endHour));
    }

    public boolean isWeatherRestricted() {
        return weatherRestricted;
    }

    public void setWeatherRestricted(boolean weatherRestricted) {
        this.weatherRestricted = weatherRestricted;
    }

    public boolean canSpawnInClear() {
        return spawnInClear;
    }

    public void setSpawnInClear(boolean spawnInClear) {
        this.spawnInClear = spawnInClear;
    }

    public boolean canSpawnInRain() {
        return spawnInRain;
    }

    public void setSpawnInRain(boolean spawnInRain) {
        this.spawnInRain = spawnInRain;
    }

    public boolean canSpawnInThunder() {
        return spawnInThunder;
    }

    public void setSpawnInThunder(boolean spawnInThunder) {
        this.spawnInThunder = spawnInThunder;
    }

    public String getSpawnerGroup() {
        return spawnerGroup;
    }

    public void setSpawnerGroup(String spawnerGroup) {
        this.spawnerGroup = spawnerGroup != null ? spawnerGroup : "";
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public double getSpawnRadiusX() {
        return spawnRadiusX;
    }

    public void setSpawnRadiusX(double spawnRadiusX) {
        this.spawnRadiusX = Math.max(1.0, Math.min(10.0, spawnRadiusX));
    }

    public double getSpawnRadiusY() {
        return spawnRadiusY;
    }

    public void setSpawnRadiusY(double spawnRadiusY) {
        this.spawnRadiusY = Math.max(1.0, Math.min(5.0, spawnRadiusY));
    }

    public double getSpawnRadiusZ() {
        return spawnRadiusZ;
    }

    public void setSpawnRadiusZ(double spawnRadiusZ) {
        this.spawnRadiusZ = Math.max(1.0, Math.min(10.0, spawnRadiusZ));
    }

    public int getMaxMobOverride() {
        return maxMobOverride;
    }

    public void setMaxMobOverride(int maxMobOverride) {
        this.maxMobOverride = maxMobOverride;
    }

    public double getPlayerDetectionRangeOverride() {
        return playerDetectionRangeOverride;
    }

    public void setPlayerDetectionRangeOverride(double playerDetectionRangeOverride) {
        this.playerDetectionRangeOverride = playerDetectionRangeOverride;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : "";
    }
}
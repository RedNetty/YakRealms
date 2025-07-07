package com.rednetty.server.mechanics.player.mounts;

import com.rednetty.server.YakRealms;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the mount system
 */
public class MountConfig {
    private final YakRealms plugin;
    private File configFile;
    private FileConfiguration config;

    // Horse configuration
    private Map<Integer, HorseStats> horseTiers = new HashMap<>();
    private int horseSummonTime = 5;
    private int chaoticHorseSummonTime = 8;
    private boolean instantMountInSafeZone = true;

    // Elytra configuration
    private int elytraSummonTime = 5;
    private int elytraDuration = 30;
    private Map<String, Double> elytraHeightLimits = new HashMap<>();
    private boolean disableDamageWhileGliding = true;

    /**
     * Constructs a new MountConfig
     *
     * @param plugin The plugin instance
     */
    public MountConfig(YakRealms plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "mounts.yml");
    }

    /**
     * Loads the configuration
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("mounts.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Load horse configuration
        horseSummonTime = config.getInt("horse.summon_time", 5);
        chaoticHorseSummonTime = config.getInt("horse.chaotic_summon_time", 8);
        instantMountInSafeZone = config.getBoolean("horse.instant_mount_in_safe_zone", true);

        ConfigurationSection tierSection = config.getConfigurationSection("horse.tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    ConfigurationSection stats = tierSection.getConfigurationSection(key);
                    if (stats != null) {
                        double speed = stats.getDouble("speed", 0.3);
                        double jump = stats.getDouble("jump", 0.5);
                        String name = stats.getString("name", "Horse Mount");
                        String color = stats.getString("color", "GREEN");
                        String description = stats.getString("description", "A standard horse mount.");
                        int price = stats.getInt("price", 1000);
                        String armorType = stats.getString("armor", "NONE");

                        horseTiers.put(tier, new HorseStats(tier, speed, jump, name, color, description, price, armorType));
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid tier in mount config: " + key);
                }
            }
        }

        // Load elytra configuration
        elytraSummonTime = config.getInt("elytra.summon_time", 5);
        elytraDuration = config.getInt("elytra.duration", 30);
        disableDamageWhileGliding = config.getBoolean("elytra.disable_damage", true);

        ConfigurationSection heightSection = config.getConfigurationSection("elytra.height_limits");
        if (heightSection != null) {
            for (String key : heightSection.getKeys(false)) {
                double height = heightSection.getDouble(key, 100.0);
                elytraHeightLimits.put(key, height);
            }
        }

        // Set default height limits if not configured
        if (!elytraHeightLimits.containsKey("frostfall")) {
            elytraHeightLimits.put("frostfall", 195.0);
        }
        if (!elytraHeightLimits.containsKey("deadpeaks")) {
            elytraHeightLimits.put("deadpeaks", 70.0);
        }
        if (!elytraHeightLimits.containsKey("avalon")) {
            elytraHeightLimits.put("avalon", 130.0);
        }
        if (!elytraHeightLimits.containsKey("default")) {
            elytraHeightLimits.put("default", 100.0);
        }
    }

    /**
     * Saves the configuration
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save mount config: " + e.getMessage());
        }
    }

    /**
     * Gets the horse summon time
     *
     * @return The horse summon time in seconds
     */
    public int getHorseSummonTime() {
        return horseSummonTime;
    }

    /**
     * Gets the chaotic horse summon time
     *
     * @return The chaotic horse summon time in seconds
     */
    public int getChaoticHorseSummonTime() {
        return chaoticHorseSummonTime;
    }

    /**
     * Checks if instant mounting in safe zones is enabled
     *
     * @return True if instant mounting is enabled
     */
    public boolean isInstantMountInSafeZone() {
        return instantMountInSafeZone;
    }

    /**
     * Gets the horse stats for a tier
     *
     * @param tier The tier
     * @return The horse stats
     */
    public HorseStats getHorseStats(int tier) {
        return horseTiers.getOrDefault(tier, new HorseStats(tier, 0.3, 0.5, "Horse Mount", "GREEN", "A standard horse mount.", 1000, "NONE"));
    }

    /**
     * Gets the elytra summon time
     *
     * @return The elytra summon time in seconds
     */
    public int getElytraSummonTime() {
        return elytraSummonTime;
    }

    /**
     * Gets the elytra duration
     *
     * @return The elytra duration in seconds
     */
    public int getElytraDuration() {
        return elytraDuration;
    }

    /**
     * Gets the elytra height limit for a region
     *
     * @param region The region name
     * @return The height limit
     */
    public double getElytraHeightLimit(String region) {
        return elytraHeightLimits.getOrDefault(region, 100.0);
    }

    /**
     * Checks if damage should be disabled while gliding
     *
     * @return True if damage should be disabled
     */
    public boolean isDisableDamageWhileGliding() {
        return disableDamageWhileGliding;
    }

    /**
     * Class to hold horse stats
     */
    public static class HorseStats {
        private final int tier;
        private final double speed;
        private final double jump;
        private final String name;
        private final String color;
        private final String description;
        private final int price;
        private final String armorType;

        public HorseStats(int tier, double speed, double jump, String name, String color, String description, int price, String armorType) {
            this.tier = tier;
            this.speed = speed;
            this.jump = jump;
            this.name = name;
            this.color = color;
            this.description = description;
            this.price = price;
            this.armorType = armorType;
        }

        public int getTier() {
            return tier;
        }

        public double getSpeed() {
            return speed;
        }

        public double getJump() {
            return jump;
        }

        public String getName() {
            return name;
        }

        public String getColor() {
            return color;
        }

        public String getDescription() {
            return description;
        }

        public int getPrice() {
            return price;
        }

        public String getArmorType() {
            return armorType;
        }
    }
}
package com.rednetty.server.mechanics.world.lootchests.config;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.lootchests.types.ChestTier;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Simple configuration management for the loot chest system
 * Provides sensible defaults with optional customization
 */
public class ChestConfig {
    private final Logger logger;
    private final YakRealms plugin;

    // Default respawn times in seconds
    private static final Map<ChestTier, Integer> DEFAULT_RESPAWN_TIMES = Map.of(
            ChestTier.WOODEN, 300,     // 5 minutes
            ChestTier.STONE, 450,      // 7.5 minutes
            ChestTier.IRON, 600,       // 10 minutes
            ChestTier.DIAMOND, 900,    // 15 minutes
            ChestTier.LEGENDARY, 1200,    // 20 minutes
            ChestTier.NETHER_FORGED, 1800  // 30 minutes
    );

    // Configuration values
    private final Map<ChestTier, Integer> respawnTimes;
    private boolean particleEffectsEnabled = true;
    private boolean soundEffectsEnabled = true;
    private boolean mobCheckEnabled = true;
    private int maxChestsPerChunk = 3;
    private int carePackageAnnounceRadius = 100;

    public ChestConfig() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.respawnTimes = new java.util.HashMap<>(DEFAULT_RESPAWN_TIMES);
    }

    /**
     * Initializes the configuration system
     */
    public void initialize() {
        try {
            loadConfiguration();
            logger.info("Chest configuration initialized successfully");
        } catch (Exception e) {
            logger.warning("Failed to load configuration, using defaults: " + e.getMessage());
        }
    }

    /**
     * Loads configuration from the plugin config file
     */
    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();

        // Load effect settings
        particleEffectsEnabled = config.getBoolean("lootchests.effects.particles", true);
        soundEffectsEnabled = config.getBoolean("lootchests.effects.sounds", true);
        mobCheckEnabled = config.getBoolean("lootchests.mechanics.mob-check", true);

        // Load spawn settings
        maxChestsPerChunk = config.getInt("lootchests.spawn.max-per-chunk", 3);
        carePackageAnnounceRadius = config.getInt("lootchests.care-package.announce-radius", 100);

        // Load respawn times
        for (ChestTier tier : ChestTier.values()) {
            String configPath = "lootchests.respawn-times." + tier.name().toLowerCase();
            int defaultTime = DEFAULT_RESPAWN_TIMES.get(tier);
            int configTime = config.getInt(configPath, defaultTime);

            // Validate respawn time (minimum 30 seconds, maximum 2 hours)
            if (configTime < 30 || configTime > 7200) {
                logger.warning("Invalid respawn time for " + tier + ": " + configTime + ", using default: " + defaultTime);
                respawnTimes.put(tier, defaultTime);
            } else {
                respawnTimes.put(tier, configTime);
            }
        }

        logger.info("Configuration loaded successfully");
    }

    /**
     * Saves current configuration to file
     */
    public void saveConfiguration() {
        try {
            FileConfiguration config = plugin.getConfig();

            // Save effect settings
            config.set("lootchests.effects.particles", particleEffectsEnabled);
            config.set("lootchests.effects.sounds", soundEffectsEnabled);
            config.set("lootchests.mechanics.mob-check", mobCheckEnabled);

            // Save spawn settings
            config.set("lootchests.spawn.max-per-chunk", maxChestsPerChunk);
            config.set("lootchests.care-package.announce-radius", carePackageAnnounceRadius);

            // Save respawn times
            for (Map.Entry<ChestTier, Integer> entry : respawnTimes.entrySet()) {
                String configPath = "lootchests.respawn-times." + entry.getKey().name().toLowerCase();
                config.set(configPath, entry.getValue());
            }

            plugin.saveConfig();
            logger.info("Configuration saved successfully");

        } catch (Exception e) {
            logger.severe("Failed to save configuration: " + e.getMessage());
        }
    }

    /**
     * Reloads configuration from file
     */
    public void reloadConfiguration() {
        try {
            plugin.reloadConfig();
            loadConfiguration();
            logger.info("Configuration reloaded successfully");
        } catch (Exception e) {
            logger.severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    // === Getters ===

    /**
     * Gets the respawn time for a specific chest tier
     */
    public int getRespawnTime(ChestTier tier) {
        return respawnTimes.getOrDefault(tier, DEFAULT_RESPAWN_TIMES.getOrDefault(tier, 600));
    }

    /**
     * Gets the respawn time in milliseconds
     */
    public long getRespawnTimeMillis(ChestTier tier) {
        return getRespawnTime(tier) * 1000L;
    }

    /**
     * Checks if particle effects are enabled
     */
    public boolean isParticleEffectsEnabled() {
        return particleEffectsEnabled;
    }

    /**
     * Checks if sound effects are enabled
     */
    public boolean isSoundEffectsEnabled() {
        return soundEffectsEnabled;
    }

    /**
     * Checks if mob checking is enabled
     */
    public boolean isMobCheckEnabled() {
        return mobCheckEnabled;
    }

    /**
     * Gets the maximum number of chests allowed per chunk
     */
    public int getMaxChestsPerChunk() {
        return maxChestsPerChunk;
    }

    /**
     * Gets the care package announcement radius
     */
    public int getCarePackageAnnounceRadius() {
        return carePackageAnnounceRadius;
    }

    // === Setters ===

    /**
     * Sets the respawn time for a specific tier
     */
    public void setRespawnTime(ChestTier tier, int seconds) {
        if (seconds < 30 || seconds > 7200) {
            throw new IllegalArgumentException("Respawn time must be between 30 and 7200 seconds");
        }
        respawnTimes.put(tier, seconds);
    }

    /**
     * Sets whether particle effects are enabled
     */
    public void setParticleEffectsEnabled(boolean enabled) {
        this.particleEffectsEnabled = enabled;
    }

    /**
     * Sets whether sound effects are enabled
     */
    public void setSoundEffectsEnabled(boolean enabled) {
        this.soundEffectsEnabled = enabled;
    }

    /**
     * Sets whether mob checking is enabled
     */
    public void setMobCheckEnabled(boolean enabled) {
        this.mobCheckEnabled = enabled;
    }

    /**
     * Sets the maximum number of chests allowed per chunk
     */
    public void setMaxChestsPerChunk(int max) {
        if (max < 1 || max > 10) {
            throw new IllegalArgumentException("Max chests per chunk must be between 1 and 10");
        }
        this.maxChestsPerChunk = max;
    }

    /**
     * Sets the care package announcement radius
     */
    public void setCarePackageAnnounceRadius(int radius) {
        if (radius < 10 || radius > 1000) {
            throw new IllegalArgumentException("Care package announce radius must be between 10 and 1000");
        }
        this.carePackageAnnounceRadius = radius;
    }

    // === Utility Methods ===

    /**
     * Gets all current configuration values
     */
    public Map<String, Object> getAllSettings() {
        Map<String, Object> settings = new java.util.HashMap<>();

        settings.put("particleEffectsEnabled", particleEffectsEnabled);
        settings.put("soundEffectsEnabled", soundEffectsEnabled);
        settings.put("mobCheckEnabled", mobCheckEnabled);
        settings.put("maxChestsPerChunk", maxChestsPerChunk);
        settings.put("carePackageAnnounceRadius", carePackageAnnounceRadius);

        // Add respawn times
        for (Map.Entry<ChestTier, Integer> entry : respawnTimes.entrySet()) {
            settings.put("respawnTime" + entry.getKey().name(), entry.getValue());
        }

        return settings;
    }

    /**
     * Resets all settings to defaults
     */
    public void resetToDefaults() {
        particleEffectsEnabled = true;
        soundEffectsEnabled = true;
        mobCheckEnabled = true;
        maxChestsPerChunk = 3;
        carePackageAnnounceRadius = 100;

        respawnTimes.clear();
        respawnTimes.putAll(DEFAULT_RESPAWN_TIMES);

        logger.info("Configuration reset to defaults");
    }

    /**
     * Validates the current configuration
     */
    public boolean validateConfiguration() {
        try {
            // Validate respawn times
            for (Map.Entry<ChestTier, Integer> entry : respawnTimes.entrySet()) {
                int time = entry.getValue();
                if (time < 30 || time > 7200) {
                    logger.warning("Invalid respawn time for " + entry.getKey() + ": " + time);
                    return false;
                }
            }

            // Validate other settings
            if (maxChestsPerChunk < 1 || maxChestsPerChunk > 10) {
                logger.warning("Invalid max chests per chunk: " + maxChestsPerChunk);
                return false;
            }

            if (carePackageAnnounceRadius < 10 || carePackageAnnounceRadius > 1000) {
                logger.warning("Invalid care package announce radius: " + carePackageAnnounceRadius);
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("Configuration validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets a formatted display of respawn times
     */
    public String getFormattedRespawnTimes() {
        StringBuilder sb = new StringBuilder();
        sb.append("Chest Respawn Times:\n");

        for (ChestTier tier : ChestTier.values()) {
            int seconds = getRespawnTime(tier);
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;

            sb.append(String.format("  %s: %dm %ds (%d seconds)\n",
                    tier.getDisplayName(), minutes, remainingSeconds, seconds));
        }

        return sb.toString();
    }

    /**
     * Gets configuration status for debugging
     */
    public Map<String, Object> getConfigStatus() {
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("isValid", validateConfiguration());
        status.put("totalSettings", getAllSettings().size());
        status.put("defaultRespawnTimes", DEFAULT_RESPAWN_TIMES.size());
        status.put("currentRespawnTimes", respawnTimes.size());
        return status;
    }

    @Override
    public String toString() {
        return "ChestConfig{" +
                "particles=" + particleEffectsEnabled +
                ", sounds=" + soundEffectsEnabled +
                ", mobCheck=" + mobCheckEnabled +
                ", maxPerChunk=" + maxChestsPerChunk +
                ", announceRadius=" + carePackageAnnounceRadius +
                "}";
    }
}
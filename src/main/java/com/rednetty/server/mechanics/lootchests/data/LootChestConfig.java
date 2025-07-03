// === LootChestConfig.java ===
package com.rednetty.server.mechanics.lootchests.data;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration manager for loot chest system
 */
public class LootChestConfig {
    private final YakRealms plugin;
    private final Logger logger;
    private File configFile;
    private YamlConfiguration config;

    // Default respawn times (in seconds)
    private final Map<ChestTier, Integer> defaultRespawnTimes = Map.of(
            ChestTier.WOODEN, 300,    // 5 minutes
            ChestTier.STONE, 450,     // 7.5 minutes
            ChestTier.IRON, 600,      // 10 minutes
            ChestTier.DIAMOND, 900,   // 15 minutes
            ChestTier.GOLDEN, 1200,   // 20 minutes
            ChestTier.LEGENDARY, 1800 // 30 minutes
    );

    private Map<ChestTier, Integer> respawnTimes = new HashMap<>();
    private boolean particleEffectsEnabled = true;
    private boolean soundEffectsEnabled = true;
    private boolean mobCheckEnabled = true;
    private int maxChestsPerChunk = 3;
    private int carePackageAnnounceRadius = 100;

    public LootChestConfig() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        configFile = new File(plugin.getDataFolder(), "lootchests-config.yml");

        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        loadConfig();
        logger.info("LootChestConfig initialized");
    }

    private void createDefaultConfig() {
        config = new YamlConfiguration();

        // Set default values
        config.set("settings.particle-effects-enabled", particleEffectsEnabled);
        config.set("settings.sound-effects-enabled", soundEffectsEnabled);
        config.set("settings.mob-check-enabled", mobCheckEnabled);
        config.set("settings.max-chests-per-chunk", maxChestsPerChunk);
        config.set("settings.care-package-announce-radius", carePackageAnnounceRadius);

        // Set default respawn times
        for (Map.Entry<ChestTier, Integer> entry : defaultRespawnTimes.entrySet()) {
            config.set("respawn-times." + entry.getKey().name().toLowerCase(), entry.getValue());
        }

        // Save the default config
        try {
            config.save(configFile);
            logger.info("Created default loot chest configuration");
        } catch (IOException e) {
            logger.severe("Failed to create default loot chest configuration: " + e.getMessage());
        }
    }

    private void loadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);

        // Load settings
        particleEffectsEnabled = config.getBoolean("settings.particle-effects-enabled", true);
        soundEffectsEnabled = config.getBoolean("settings.sound-effects-enabled", true);
        mobCheckEnabled = config.getBoolean("settings.mob-check-enabled", true);
        maxChestsPerChunk = config.getInt("settings.max-chests-per-chunk", 3);
        carePackageAnnounceRadius = config.getInt("settings.care-package-announce-radius", 100);

        // Load respawn times
        respawnTimes.clear();
        for (ChestTier tier : ChestTier.values()) {
            String key = "respawn-times." + tier.name().toLowerCase();
            int defaultTime = defaultRespawnTimes.getOrDefault(tier, 600);
            int respawnTime = config.getInt(key, defaultTime);
            respawnTimes.put(tier, respawnTime);
        }

        logger.info("Loaded loot chest configuration");
    }

    public void saveConfig() {
        try {
            config.save(configFile);
            logger.info("Saved loot chest configuration");
        } catch (IOException e) {
            logger.severe("Failed to save loot chest configuration: " + e.getMessage());
        }
    }

    public void reloadConfig() {
        loadConfig();
        logger.info("Reloaded loot chest configuration");
    }

    // === Getters ===

    public int getRespawnTime(ChestTier tier) {
        return respawnTimes.getOrDefault(tier, defaultRespawnTimes.getOrDefault(tier, 600));
    }

    public boolean isParticleEffectsEnabled() {
        return particleEffectsEnabled;
    }

    public boolean isSoundEffectsEnabled() {
        return soundEffectsEnabled;
    }

    public boolean isMobCheckEnabled() {
        return mobCheckEnabled;
    }

    public int getMaxChestsPerChunk() {
        return maxChestsPerChunk;
    }

    public int getCarePackageAnnounceRadius() {
        return carePackageAnnounceRadius;
    }

    // === Setters ===

    public void setRespawnTime(ChestTier tier, int seconds) {
        respawnTimes.put(tier, seconds);
        config.set("respawn-times." + tier.name().toLowerCase(), seconds);
    }

    public void setParticleEffectsEnabled(boolean enabled) {
        this.particleEffectsEnabled = enabled;
        config.set("settings.particle-effects-enabled", enabled);
    }

    public void setSoundEffectsEnabled(boolean enabled) {
        this.soundEffectsEnabled = enabled;
        config.set("settings.sound-effects-enabled", enabled);
    }

    public void setMobCheckEnabled(boolean enabled) {
        this.mobCheckEnabled = enabled;
        config.set("settings.mob-check-enabled", enabled);
    }

    public void setMaxChestsPerChunk(int max) {
        this.maxChestsPerChunk = max;
        config.set("settings.max-chests-per-chunk", max);
    }

    public void setCarePackageAnnounceRadius(int radius) {
        this.carePackageAnnounceRadius = radius;
        config.set("settings.care-package-announce-radius", radius);
    }

    // === Utility Methods ===

    public Map<String, Object> getAllSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("particleEffectsEnabled", particleEffectsEnabled);
        settings.put("soundEffectsEnabled", soundEffectsEnabled);
        settings.put("mobCheckEnabled", mobCheckEnabled);
        settings.put("maxChestsPerChunk", maxChestsPerChunk);
        settings.put("carePackageAnnounceRadius", carePackageAnnounceRadius);

        for (Map.Entry<ChestTier, Integer> entry : respawnTimes.entrySet()) {
            settings.put("respawnTime" + entry.getKey().name(), entry.getValue());
        }

        return settings;
    }
}
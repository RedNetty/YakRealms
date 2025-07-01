
// === LootChestConfig.java ===
package com.rednetty.server.mechanics.lootchests.data;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration management for loot chests
 */
public class LootChestConfig {
    private final YakRealms plugin;
    private final Logger logger;
    private File configFile;
    private FileConfiguration config;

    // Default respawn times (in seconds) for each tier
    private final Map<ChestTier, Integer> defaultRespawnTimes = Map.of(
            ChestTier.WOODEN, 60,
            ChestTier.STONE, 120,
            ChestTier.IRON, 180,
            ChestTier.DIAMOND, 240,
            ChestTier.GOLDEN, 300,
            ChestTier.LEGENDARY, 360
    );

    public LootChestConfig() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        createConfigFile();
        loadConfiguration();
        saveDefaultConfiguration();
    }

    private void createConfigFile() {
        configFile = new File(plugin.getDataFolder(), "lootchests.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                logger.severe("Could not create lootchests config file: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadConfiguration() {
        try {
            config.load(configFile);
        } catch (Exception e) {
            logger.severe("Could not load lootchests config: " + e.getMessage());
        }
    }

    private void saveDefaultConfiguration() {
        // Set default values if not present
        for (ChestTier tier : ChestTier.values()) {
            String path = "respawn-times." + tier.name().toLowerCase();
            if (!config.contains(path)) {
                config.set(path, defaultRespawnTimes.get(tier));
            }
        }

        // Other default settings
        if (!config.contains("particle-effects.enabled")) {
            config.set("particle-effects.enabled", true);
        }
        if (!config.contains("sound-effects.enabled")) {
            config.set("sound-effects.enabled", true);
        }
        if (!config.contains("notifications.care-package-broadcast")) {
            config.set("notifications.care-package-broadcast", true);
        }

        saveConfiguration();
    }

    public void saveConfiguration() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.severe("Could not save lootchests config: " + e.getMessage());
        }
    }

    public int getRespawnTime(ChestTier tier) {
        String path = "respawn-times." + tier.name().toLowerCase();
        return config.getInt(path, defaultRespawnTimes.get(tier));
    }

    public void setRespawnTime(ChestTier tier, int seconds) {
        String path = "respawn-times." + tier.name().toLowerCase();
        config.set(path, seconds);
        saveConfiguration();
    }

    public boolean isParticleEffectsEnabled() {
        return config.getBoolean("particle-effects.enabled", true);
    }

    public boolean isSoundEffectsEnabled() {
        return config.getBoolean("sound-effects.enabled", true);
    }

    public boolean isCarePackageBroadcastEnabled() {
        return config.getBoolean("notifications.care-package-broadcast", true);
    }
}


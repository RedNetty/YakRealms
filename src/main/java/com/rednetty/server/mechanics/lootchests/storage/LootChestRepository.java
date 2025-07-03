// === LootChestRepository.java ===
package com.rednetty.server.mechanics.lootchests.storage;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.lootchests.data.LootChestData;
import com.rednetty.server.mechanics.lootchests.types.LootChestLocation;
import com.rednetty.server.mechanics.lootchests.types.ChestState;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles persistent storage of loot chest data with improved error handling
 */
public class LootChestRepository {
    private final YakRealms plugin;
    private final Logger logger;
    private File storageFile;
    private File backupFile;

    public LootChestRepository() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        // Create data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        storageFile = new File(plugin.getDataFolder(), "lootchests-data.yml");
        backupFile = new File(plugin.getDataFolder(), "lootchests-data-backup.yml");

        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile();
                logger.info("Created new loot chest storage file");
            } catch (IOException e) {
                logger.severe("Could not create loot chest storage file: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        // Create a final backup on shutdown
        try {
            createBackup();
        } catch (Exception e) {
            logger.warning("Failed to create backup on shutdown: " + e.getMessage());
        }
    }

    public Map<LootChestLocation, LootChestData> loadAllChests() {
        Map<LootChestLocation, LootChestData> chests = new HashMap<>();

        if (!storageFile.exists()) {
            return chests;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
            int loadedCount = 0;
            int errorCount = 0;

            for (String key : config.getKeys(false)) {
                try {
                    LootChestData chestData = loadSingleChest(config, key);
                    if (chestData != null) {
                        chests.put(chestData.getLocation(), chestData);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    logger.warning("Failed to load chest data for key " + key + ": " + e.getMessage());
                    errorCount++;
                }
            }

            logger.info("Loaded " + loadedCount + " chests from storage" +
                    (errorCount > 0 ? " (" + errorCount + " errors)" : ""));

        } catch (Exception e) {
            logger.severe("Failed to load chest data: " + e.getMessage());

            // Try to load from backup
            if (backupFile.exists()) {
                logger.info("Attempting to load from backup file...");
                try {
                    return loadFromBackup();
                } catch (Exception backupE) {
                    logger.severe("Backup file also corrupted: " + backupE.getMessage());
                }
            }
        }

        return chests;
    }

    private LootChestData loadSingleChest(YamlConfiguration config, String key) {
        try {
            // Parse location from key
            LootChestLocation location = LootChestLocation.fromString(key);

            // Load basic chest data
            String tierName = config.getString(key + ".tier", "IRON");
            String typeName = config.getString(key + ".type", "NORMAL");
            String stateName = config.getString(key + ".state", "AVAILABLE");

            // Load timing data
            long creationTime = config.getLong(key + ".creation-time", System.currentTimeMillis());
            long respawnTime = config.getLong(key + ".respawn-time", 0);
            long lastInteractionTime = config.getLong(key + ".last-interaction-time", 0);
            long lastStateChangeTime = config.getLong(key + ".last-state-change-time", creationTime);

            // Validate and create enums
            ChestTier tier;
            try {
                tier = ChestTier.valueOf(tierName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid tier '" + tierName + "' for chest " + key + ", defaulting to IRON");
                tier = ChestTier.IRON;
            }

            ChestType type;
            try {
                type = ChestType.valueOf(typeName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid type '" + typeName + "' for chest " + key + ", defaulting to NORMAL");
                type = ChestType.NORMAL;
            }

            ChestState state;
            try {
                state = ChestState.valueOf(stateName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid state '" + stateName + "' for chest " + key + ", defaulting to AVAILABLE");
                state = ChestState.AVAILABLE;
            }

            // Create chest data
            LootChestData chestData = new LootChestData(location, tier, type);
            chestData.setState(state);
            chestData.setRespawnTime(respawnTime);

            // Load player interactions
            if (config.contains(key + ".interactions")) {
                List<String> interactions = config.getStringList(key + ".interactions");
                for (String playerUuidStr : interactions) {
                    try {
                        UUID playerUuid = UUID.fromString(playerUuidStr);
                        chestData.addInteraction(playerUuid);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID '" + playerUuidStr + "' in interactions for chest " + key);
                    }
                }
            }

            // Load metadata
            if (config.contains(key + ".metadata")) {
                try {
                    Map<String, Object> metadata = config.getConfigurationSection(key + ".metadata").getValues(false);
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        chestData.setMetadata(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    logger.warning("Failed to load metadata for chest " + key + ": " + e.getMessage());
                }
            }

            return chestData;

        } catch (Exception e) {
            logger.warning("Failed to parse chest data for key '" + key + "': " + e.getMessage());
            return null;
        }
    }

    public void saveAllChests(Map<LootChestLocation, LootChestData> chests) {
        try {
            // Create backup first
            createBackup();

            YamlConfiguration config = new YamlConfiguration();

            for (Map.Entry<LootChestLocation, LootChestData> entry : chests.entrySet()) {
                LootChestLocation location = entry.getKey();
                LootChestData data = entry.getValue();

                String key = location.toString();

                // Save basic data
                config.set(key + ".tier", data.getTier().name());
                config.set(key + ".type", data.getType().name());
                config.set(key + ".state", data.getState().name());

                // Save timing data
                config.set(key + ".creation-time", data.getCreationTime());
                config.set(key + ".respawn-time", data.getRespawnTime());
                config.set(key + ".last-interaction-time", data.getLastInteractionTime());
                config.set(key + ".last-state-change-time", data.getLastStateChangeTime());

                // Save player interactions
                if (!data.getPlayersWhoInteracted().isEmpty()) {
                    List<String> interactions = data.getPlayersWhoInteracted().stream()
                            .map(UUID::toString)
                            .toList();
                    config.set(key + ".interactions", interactions);
                }

                // Save metadata
                Map<String, Object> metadata = data.getMetadata();
                if (!metadata.isEmpty()) {
                    for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
                        config.set(key + ".metadata." + metaEntry.getKey(), metaEntry.getValue());
                    }
                }
            }

            config.save(storageFile);
            logger.fine("Saved " + chests.size() + " chests to storage");

        } catch (IOException e) {
            logger.severe("Failed to save loot chest data: " + e.getMessage());
        }
    }

    public void saveChest(LootChestLocation location, LootChestData data) {
        try {
            // Load existing data first
            Map<LootChestLocation, LootChestData> allChests = loadAllChests();

            // Update or add the specific chest
            allChests.put(location, data);

            // Save all data
            saveAllChests(allChests);

        } catch (Exception e) {
            logger.severe("Failed to save individual chest " + location + ": " + e.getMessage());
        }
    }

    public void deleteChest(LootChestLocation location) {
        try {
            Map<LootChestLocation, LootChestData> allChests = loadAllChests();
            allChests.remove(location);
            saveAllChests(allChests);
            logger.fine("Deleted chest " + location + " from storage");
        } catch (Exception e) {
            logger.severe("Failed to delete chest " + location + ": " + e.getMessage());
        }
    }

    private void createBackup() {
        if (storageFile.exists()) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
                config.save(backupFile);
                logger.fine("Created backup of loot chest data");
            } catch (Exception e) {
                logger.warning("Failed to create backup: " + e.getMessage());
            }
        }
    }

    private Map<LootChestLocation, LootChestData> loadFromBackup() {
        Map<LootChestLocation, LootChestData> chests = new HashMap<>();

        if (!backupFile.exists()) {
            return chests;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(backupFile);

        for (String key : config.getKeys(false)) {
            try {
                LootChestData chestData = loadSingleChest(config, key);
                if (chestData != null) {
                    chests.put(chestData.getLocation(), chestData);
                }
            } catch (Exception e) {
                logger.warning("Failed to load chest from backup for key " + key + ": " + e.getMessage());
            }
        }

        logger.info("Loaded " + chests.size() + " chests from backup file");
        return chests;
    }

    public boolean hasValidStorage() {
        return storageFile != null && storageFile.exists();
    }

    public long getStorageFileSize() {
        return storageFile != null && storageFile.exists() ? storageFile.length() : 0;
    }

    public boolean hasBackup() {
        return backupFile != null && backupFile.exists();
    }

    public void repairStorage() {
        try {
            logger.info("Attempting to repair loot chest storage...");

            // Load all data we can
            Map<LootChestLocation, LootChestData> chests = new HashMap<>();

            // Try main file first
            try {
                chests = loadAllChests();
            } catch (Exception e) {
                logger.warning("Main storage file corrupted: " + e.getMessage());
            }

            // If main file failed or has no data, try backup
            if (chests.isEmpty() && hasBackup()) {
                try {
                    chests = loadFromBackup();
                } catch (Exception e) {
                    logger.warning("Backup file also corrupted: " + e.getMessage());
                }
            }

            // Clean up any invalid data
            chests.entrySet().removeIf(entry -> {
                try {
                    LootChestData data = entry.getValue();
                    return data == null || data.getLocation() == null || data.getTier() == null || data.getType() == null;
                } catch (Exception e) {
                    return true; // Remove invalid entries
                }
            });

            // Save the cleaned data
            saveAllChests(chests);

            logger.info("Storage repair completed. Recovered " + chests.size() + " chests.");

        } catch (Exception e) {
            logger.severe("Failed to repair storage: " + e.getMessage());
        }
    }
}
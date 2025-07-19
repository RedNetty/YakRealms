package com.rednetty.server.mechanics.world.lootchests.persistence;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.lootchests.core.Chest;
import com.rednetty.server.mechanics.world.lootchests.types.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles persistence of loot chest data using YAML files
 * Simple and reliable approach with immediate saves for critical operations
 */
public class ChestRepository {
    private final Logger logger;
    private final File dataFile;
    private final File backupFile;

    // Performance tracking
    private long lastSaveTime = 0;
    private int saveCount = 0;

    public ChestRepository() {
        this.logger = YakRealms.getInstance().getLogger();
        File dataFolder = YakRealms.getInstance().getDataFolder();
        this.dataFile = new File(dataFolder, "lootchests.yml");
        this.backupFile = new File(dataFolder, "lootchests_backup.yml");
    }

    /**
     * Initializes the repository and creates necessary files
     */
    public void initialize() {
        try {
            // Create data folder if it doesn't exist
            File dataFolder = dataFile.getParentFile();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IOException("Could not create data folder: " + dataFolder.getAbsolutePath());
            }

            // Create data file if it doesn't exist
            if (!dataFile.exists()) {
                if (dataFile.createNewFile()) {
                    logger.info("Created new chest data file: " + dataFile.getName());
                } else {
                    throw new IOException("Could not create data file: " + dataFile.getAbsolutePath());
                }
            }

            // Validate existing file
            if (dataFile.exists() && dataFile.length() > 0) {
                try {
                    YamlConfiguration.loadConfiguration(dataFile);
                    logger.info("Existing chest data file validated successfully");
                } catch (Exception e) {
                    logger.warning("Existing data file appears corrupted, attempting backup restore");
                    restoreFromBackup();
                }
            }

            logger.info("Chest repository initialized successfully");

        } catch (Exception e) {
            logger.severe("Failed to initialize chest repository: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Repository initialization failed", e);
        }
    }

    /**
     * Saves a single chest immediately
     * @param chest The chest to save
     */
    public void saveChest(Chest chest) {
        if (chest == null) {
            logger.warning("Attempted to save null chest");
            return;
        }

        try {
            // Load existing configuration
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

            // Save chest data
            saveChestToConfig(config, chest);

            // Save to file
            config.save(dataFile);

            lastSaveTime = System.currentTimeMillis();
            saveCount++;

            logger.fine("Saved chest: " + chest.getLocation());

        } catch (Exception e) {
            logger.severe("Failed to save chest " + chest.getLocation() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves all chests to storage
     * @param chests Map of all chests to save
     */
    public void saveAllChests(Map<ChestLocation, Chest> chests) {
        if (chests == null) {
            logger.warning("Attempted to save null chest map");
            return;
        }

        try {
            logger.fine("Saving " + chests.size() + " chests to storage...");

            // Create backup before major save operation
            createBackup();

            // Create new configuration
            YamlConfiguration config = new YamlConfiguration();

            // Add header
            config.options().header(
                    "Loot Chest System Data\n" +
                            "Generated on: " + new Date() + "\n" +
                            "Total chests: " + chests.size()
            );

            // Save all chests
            for (Chest chest : chests.values()) {
                if (chest != null) {
                    saveChestToConfig(config, chest);
                }
            }

            // Save to file
            config.save(dataFile);

            lastSaveTime = System.currentTimeMillis();
            saveCount++;

            logger.info("Successfully saved " + chests.size() + " chests to storage");

        } catch (Exception e) {
            logger.severe("Failed to save all chests: " + e.getMessage());
            e.printStackTrace();

            // Attempt to restore from backup if save failed
            try {
                restoreFromBackup();
                logger.info("Restored from backup after save failure");
            } catch (Exception backupError) {
                logger.severe("Backup restoration also failed: " + backupError.getMessage());
            }
        }
    }

    /**
     * Loads all chests from storage
     * @return Map of loaded chests
     */
    public Map<ChestLocation, Chest> loadAllChests() {
        Map<ChestLocation, Chest> chests = new HashMap<>();

        try {
            if (!dataFile.exists() || dataFile.length() == 0) {
                logger.info("No chest data file found or file is empty, starting fresh");
                return chests;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            Set<String> keys = config.getKeys(false);

            logger.info("Loading " + keys.size() + " chests from storage...");

            int loadedCount = 0;
            int errorCount = 0;

            for (String key : keys) {
                try {
                    Chest chest = loadChestFromConfig(config, key);
                    if (chest != null) {
                        chests.put(chest.getLocation(), chest);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    logger.warning("Failed to load chest '" + key + "': " + e.getMessage());
                    errorCount++;
                }
            }

            logger.info("Successfully loaded " + loadedCount + " chests from storage" +
                    (errorCount > 0 ? " (" + errorCount + " errors)" : ""));

        } catch (Exception e) {
            logger.severe("Failed to load chests from storage: " + e.getMessage());
            e.printStackTrace();

            // Attempt to load from backup
            try {
                logger.info("Attempting to load from backup...");
                chests = loadFromBackup();
                logger.info("Successfully loaded " + chests.size() + " chests from backup");
            } catch (Exception backupError) {
                logger.severe("Backup loading also failed: " + backupError.getMessage());
                backupError.printStackTrace();
            }
        }

        return chests;
    }

    /**
     * Deletes a chest from storage
     * @param location The location of the chest to delete
     */
    public void deleteChest(ChestLocation location) {
        if (location == null) {
            logger.warning("Attempted to delete chest with null location");
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            String key = location.toString();

            if (config.contains(key)) {
                config.set(key, null);
                config.save(dataFile);

                lastSaveTime = System.currentTimeMillis();
                saveCount++;

                logger.fine("Deleted chest: " + location);
            } else {
                logger.warning("Attempted to delete non-existent chest: " + location);
            }

        } catch (Exception e) {
            logger.severe("Failed to delete chest " + location + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // === Private Helper Methods ===

    /**
     * Saves a chest to a YAML configuration
     */
    private void saveChestToConfig(YamlConfiguration config, Chest chest) {
        String key = chest.getLocation().toString();

        config.set(key + ".tier", chest.getTier().name());
        config.set(key + ".type", chest.getType().name());
        config.set(key + ".state", chest.getState().name());
        config.set(key + ".creation-time", chest.getCreationTime());
        config.set(key + ".respawn-time", chest.getRespawnTime());
        config.set(key + ".last-interaction", chest.getLastInteraction());

        // Save player interactions
        Set<UUID> interactions = chest.getInteractedPlayers();
        if (interactions != null && !interactions.isEmpty()) {
            List<String> uuidStrings = new ArrayList<>();
            for (UUID uuid : interactions) {
                if (uuid != null) {
                    uuidStrings.add(uuid.toString());
                }
            }
            config.set(key + ".interactions", uuidStrings);
        }

        // Save metadata if present
        Map<String, Object> metadata = chest.getMetadata();
        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String metaKey = entry.getKey();
                Object metaValue = entry.getValue();
                if (metaKey != null && metaValue != null) {
                    config.set(key + ".metadata." + metaKey, metaValue);
                }
            }
        }
    }

    /**
     * Loads a chest from a YAML configuration
     */
    private Chest loadChestFromConfig(YamlConfiguration config, String key) {
        try {
            // Parse location from key
            ChestLocation location = ChestLocation.fromString(key);

            // Load basic data with safe defaults
            String tierName = config.getString(key + ".tier", "IRON");
            String typeName = config.getString(key + ".type", "NORMAL");
            String stateName = config.getString(key + ".state", "AVAILABLE");

            // Parse enums with fallbacks
            ChestTier tier = parseEnum(ChestTier.class, tierName, ChestTier.IRON);
            ChestType type = parseEnum(ChestType.class, typeName, ChestType.NORMAL);
            ChestState state = parseEnum(ChestState.class, stateName, ChestState.AVAILABLE);

            // Load timing data
            long creationTime = config.getLong(key + ".creation-time", System.currentTimeMillis());
            long respawnTime = config.getLong(key + ".respawn-time", 0);
            long lastInteraction = config.getLong(key + ".last-interaction", creationTime);

            // Load player interactions
            Set<UUID> interactions = new HashSet<>();
            if (config.contains(key + ".interactions")) {
                List<String> uuidStrings = config.getStringList(key + ".interactions");
                for (String uuidString : uuidStrings) {
                    try {
                        if (uuidString != null && !uuidString.trim().isEmpty()) {
                            UUID uuid = UUID.fromString(uuidString.trim());
                            interactions.add(uuid);
                        }
                    } catch (Exception e) {
                        logger.warning("Invalid UUID in interactions for chest " + key + ": " + uuidString);
                    }
                }
            }

            // Create chest with loaded data
            Chest chest = new Chest(location, tier, type, creationTime, state, respawnTime, lastInteraction, interactions);

            // Load metadata if present
            if (config.isConfigurationSection(key + ".metadata")) {
                var metadataSection = config.getConfigurationSection(key + ".metadata");
                if (metadataSection != null) {
                    Map<String, Object> metadata = metadataSection.getValues(false);
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            chest.setMetadata(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            return chest;

        } catch (Exception e) {
            logger.warning("Failed to parse chest data for key '" + key + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Safely parses enum values with fallbacks
     */
    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T fallback) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (Exception e) {
            logger.warning("Invalid " + enumClass.getSimpleName() + " value: " + value + ", using " + fallback);
            return fallback;
        }
    }

    /**
     * Creates a backup of the current data file
     */
    private void createBackup() {
        try {
            if (dataFile.exists() && dataFile.length() > 0) {
                java.nio.file.Files.copy(dataFile.toPath(), backupFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.fine("Created backup of chest data");
            }
        } catch (Exception e) {
            logger.warning("Failed to create backup: " + e.getMessage());
        }
    }

    /**
     * Restores data from backup file
     */
    private void restoreFromBackup() throws IOException {
        if (!backupFile.exists()) {
            throw new IOException("No backup file available");
        }

        // Validate backup file
        try {
            YamlConfiguration.loadConfiguration(backupFile);
        } catch (Exception e) {
            throw new IOException("Backup file is also corrupted: " + e.getMessage());
        }

        // Copy backup to main file
        java.nio.file.Files.copy(backupFile.toPath(), dataFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        logger.info("Restored data file from backup");
    }

    /**
     * Loads chests from backup file
     */
    private Map<ChestLocation, Chest> loadFromBackup() throws Exception {
        if (!backupFile.exists()) {
            throw new Exception("No backup file available");
        }

        Map<ChestLocation, Chest> chests = new HashMap<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(backupFile);

        for (String key : config.getKeys(false)) {
            try {
                Chest chest = loadChestFromConfig(config, key);
                if (chest != null) {
                    chests.put(chest.getLocation(), chest);
                }
            } catch (Exception e) {
                logger.warning("Failed to load chest from backup '" + key + "': " + e.getMessage());
            }
        }

        return chests;
    }

    // === Public Utility Methods ===

    /**
     * Gets repository statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("dataFileExists", dataFile.exists());
        stats.put("dataFileSize", dataFile.exists() ? dataFile.length() : 0);
        stats.put("backupFileExists", backupFile.exists());
        stats.put("backupFileSize", backupFile.exists() ? backupFile.length() : 0);
        stats.put("lastSaveTime", lastSaveTime);
        stats.put("saveCount", saveCount);

        return stats;
    }

    /**
     * Validates the data file integrity
     */
    public boolean validateDataFile() {
        try {
            if (!dataFile.exists()) {
                return false;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            config.getKeys(false); // This will throw if file is corrupted

            return true;
        } catch (Exception e) {
            logger.warning("Data file validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Repairs the data file if possible
     */
    public boolean repairDataFile() {
        try {
            logger.info("Attempting to repair data file...");

            if (backupFile.exists() && validateBackupFile()) {
                restoreFromBackup();
                logger.info("Data file repaired from backup");
                return true;
            } else {
                // Create new empty file
                if (dataFile.exists()) {
                    dataFile.delete();
                }
                dataFile.createNewFile();
                logger.info("Created new empty data file");
                return true;
            }
        } catch (Exception e) {
            logger.severe("Failed to repair data file: " + e.getMessage());
            return false;
        }
    }

    /**
     * Validates the backup file integrity
     */
    private boolean validateBackupFile() {
        try {
            if (!backupFile.exists()) {
                return false;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(backupFile);
            config.getKeys(false);

            return true;
        } catch (Exception e) {
            logger.warning("Backup file validation failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String toString() {
        return "ChestRepository{dataFile=" + dataFile.getName() +
                ", lastSave=" + new Date(lastSaveTime) +
                ", saves=" + saveCount + "}";
    }
}
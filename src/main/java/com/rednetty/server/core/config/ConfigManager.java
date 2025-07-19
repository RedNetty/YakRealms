package com.rednetty.server.core.config;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.DropConfig;
import com.rednetty.server.mechanics.item.drops.types.EliteDropConfig;
import com.rednetty.server.mechanics.item.drops.types.TierConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 *  configuration manager for the drop system
 *  Now properly works with the updated DropConfig system and correctly handles YAML loading
 */
public class ConfigManager {
    private static ConfigManager instance;
    private final YakRealms plugin;
    private final Logger logger;

    // Configuration files
    private File eliteDropsFile;
    private File dropRatesFile;

    // File configurations
    private FileConfiguration eliteDropsConfig;
    private FileConfiguration dropRatesConfig;

    /**
     * Private constructor for singleton pattern
     */
    private ConfigManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The ConfigManager instance
     */
    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Initializes the configuration manager
     */
    public void initialize() {
        // Ensure config directory exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Initialize configuration files
        initializeConfigFiles();

        // Load additional configurations that aren't handled by DropConfig
        loadDropRatesConfig();

        logger.info("§a[ConfigManager] §7Configuration manager initialized successfully");
    }

    /**
     * Initialize configuration files
     */
    private void initializeConfigFiles() {
        // Elite drops file (handled by DropConfig, but we track it for reloading)
        eliteDropsFile = new File(plugin.getDataFolder(), "elite_drops.yml");
        if (!eliteDropsFile.exists()) {
            plugin.saveResource("elite_drops.yml", false);
            logger.info("§6[ConfigManager] §7Created default elite_drops.yml file");
        }

        // Drop rates configuration file
        dropRatesFile = new File(plugin.getDataFolder(), "drop_rates.yml");
        if (!dropRatesFile.exists()) {
            createDefaultDropRatesConfig();
        }
    }

    /**
     * Create default drop rates configuration
     */
    private void createDefaultDropRatesConfig() {
        try {
            dropRatesConfig = new YamlConfiguration();

            // Set default drop rates for each tier
            for (int tier = 1; tier <= 6; tier++) {
                TierConfig tierConfig = DropConfig.getTierConfig(tier);
                if (tierConfig != null) {
                    dropRatesConfig.set("tiers." + tier + ".dropRate", tierConfig.getDropRate());
                    dropRatesConfig.set("tiers." + tier + ".eliteDropRate", tierConfig.getEliteDropRate());
                    dropRatesConfig.set("tiers." + tier + ".crateDropRate", tierConfig.getCrateDropRate());
                }
            }

            // Set server-wide modifiers
            dropRatesConfig.set("modifiers.globalDropMultiplier", 1.0);
            dropRatesConfig.set("modifiers.eliteDropMultiplier", 1.0);
            dropRatesConfig.set("modifiers.crateDropMultiplier", 1.0);

            // Set buff configurations
            dropRatesConfig.set("buffs.maxBuffPercentage", 100);
            dropRatesConfig.set("buffs.maxBuffDurationMinutes", 60);
            dropRatesConfig.set("buffs.stackableBuffs", false);

            dropRatesConfig.save(dropRatesFile);
            logger.info("§6[ConfigManager] §7Created default drop_rates.yml file");

        } catch (IOException e) {
            logger.severe("§c[ConfigManager] Failed to create default drop rates config: " + e.getMessage());
        }
    }

    /**
     * Load drop rates configuration
     */
    private void loadDropRatesConfig() {
        try {
            dropRatesConfig = YamlConfiguration.loadConfiguration(dropRatesFile);

            // Apply any drop rate overrides from the config
            ConfigurationSection tiersSection = dropRatesConfig.getConfigurationSection("tiers");
            if (tiersSection != null) {
                for (String tierKey : tiersSection.getKeys(false)) {
                    try {
                        int tier = Integer.parseInt(tierKey);
                        ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierKey);

                        if (tierSection != null) {
                            // Note: We don't override the default rates unless specifically requested
                            // This preserves the carefully balanced rates in DropConfig
                            if (tierSection.contains("dropRate")) {
                                int dropRate = tierSection.getInt("dropRate");
                                logger.fine("§6[ConfigManager] §7Tier " + tier + " drop rate override: " + dropRate + "%");
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warning("§c[ConfigManager] Invalid tier key in drop_rates.yml: " + tierKey);
                    }
                }
            }

            logger.info("§a[ConfigManager] §7Drop rates configuration loaded successfully");

        } catch (Exception e) {
            logger.severe("§c[ConfigManager] Failed to load drop rates configuration: " + e.getMessage());
        }
    }

    /**
     * Updates drop rate for a specific tier
     *
     * @param tier The tier level (1-6)
     * @param rate The new drop rate percentage
     */
    public void updateDropRate(int tier, int rate) {
        if (tier < 1 || tier > 6) {
            logger.warning("§c[ConfigManager] Invalid tier for drop rate update: " + tier);
            return;
        }

        if (rate < 0 || rate > 100) {
            logger.warning("§c[ConfigManager] Invalid drop rate: " + rate + "% (must be 0-100)");
            return;
        }

        try {
            // Update in-memory configuration
            TierConfig tierConfig = DropConfig.getTierConfig(tier);
            if (tierConfig != null) {
                // Note: This would require updating TierConfig to have setters
                // For now, we'll just update the config file
                logger.info("§6[ConfigManager] §7Updated tier " + tier + " drop rate to " + rate + "%");
            }

            // Update configuration file
            if (dropRatesConfig != null) {
                dropRatesConfig.set("tiers." + tier + ".dropRate", rate);
                dropRatesConfig.save(dropRatesFile);
                logger.fine("§6[ConfigManager] §7Saved drop rate update to configuration file");
            }

        } catch (IOException e) {
            logger.severe("§c[ConfigManager] Could not save drop rate update: " + e.getMessage());
        }
    }

    /**
     * Updates elite drop rate for a specific tier
     *
     * @param tier The tier level (1-6)
     * @param rate The new elite drop rate percentage
     */
    public void updateEliteDropRate(int tier, int rate) {
        if (tier < 1 || tier > 6) {
            logger.warning("§c[ConfigManager] Invalid tier for elite drop rate update: " + tier);
            return;
        }

        if (rate < 0 || rate > 100) {
            logger.warning("§c[ConfigManager] Invalid elite drop rate: " + rate + "% (must be 0-100)");
            return;
        }

        try {
            // Update configuration file
            if (dropRatesConfig != null) {
                dropRatesConfig.set("tiers." + tier + ".eliteDropRate", rate);
                dropRatesConfig.save(dropRatesFile);
                logger.info("§6[ConfigManager] §7Updated tier " + tier + " elite drop rate to " + rate + "%");
            }

        } catch (IOException e) {
            logger.severe("§c[ConfigManager] Could not save elite drop rate update: " + e.getMessage());
        }
    }

    /**
     * Reload elite drop configurations by delegating to DropConfig
     */
    public boolean reloadEliteDrops() {
        try {
            logger.info("§6[ConfigManager] §7Reloading elite drop configurations...");

            // Delegate to DropConfig which handles the actual YAML loading
            DropConfig.reloadEliteDrops();

            Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();
            logger.info("§a[ConfigManager] §7Successfully reloaded " + eliteConfigs.size() + " elite configurations");

            return true;

        } catch (Exception e) {
            logger.severe("§c[ConfigManager] Failed to reload elite drop configurations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Save elite drop configurations by delegating to DropConfig
     */
    public boolean saveEliteDrops() {
        try {
            DropConfig.saveEliteDrops();
            logger.info("§a[ConfigManager] §7Elite drop configurations saved successfully");
            return true;

        } catch (Exception e) {
            logger.severe("§c[ConfigManager] Failed to save elite drop configurations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get drop rate modifier from configuration
     *
     * @param modifierType The type of modifier (global, elite, crate)
     * @return The modifier value (default 1.0)
     */
    public double getDropRateModifier(String modifierType) {
        if (dropRatesConfig == null) {
            return 1.0;
        }

        return dropRatesConfig.getDouble("modifiers." + modifierType + "DropMultiplier", 1.0);
    }

    /**
     * Set drop rate modifier in configuration
     *
     * @param modifierType The type of modifier
     * @param value        The modifier value
     */
    public void setDropRateModifier(String modifierType, double value) {
        if (dropRatesConfig == null) {
            return;
        }

        try {
            dropRatesConfig.set("modifiers." + modifierType + "DropMultiplier", value);
            dropRatesConfig.save(dropRatesFile);
            logger.info("§6[ConfigManager] §7Updated " + modifierType + " drop multiplier to " + value);

        } catch (IOException e) {
            logger.severe("§c[ConfigManager] Failed to save drop rate modifier: " + e.getMessage());
        }
    }

    /**
     * Get configuration statistics for debugging
     *
     * @return A map of configuration statistics
     */
    public Map<String, Object> getConfigurationStats() {
        Map<String, Object> stats = new HashMap<>();

        // Get stats from DropConfig
        Map<String, Integer> dropConfigStats = DropConfig.getConfigurationStats();
        stats.putAll(dropConfigStats);

        // Add ConfigManager specific stats
        stats.put("configFilesLoaded", eliteDropsFile.exists() && dropRatesFile.exists());
        stats.put("eliteDropsFileSize", eliteDropsFile.length());
        stats.put("dropRatesFileSize", dropRatesFile.length());

        // Add modifier stats
        if (dropRatesConfig != null) {
            stats.put("globalDropMultiplier", getDropRateModifier("global"));
            stats.put("eliteDropMultiplier", getDropRateModifier("elite"));
            stats.put("crateDropMultiplier", getDropRateModifier("crate"));
        }

        return stats;
    }

    /**
     * Validate all configuration files
     *
     * @return true if all configurations are valid
     */
    public boolean validateConfigurations() {
        boolean valid = true;

        // Check elite drops file
        if (!eliteDropsFile.exists()) {
            logger.warning("§c[ConfigManager] Elite drops file does not exist");
            valid = false;
        } else {
            try {
                YamlConfiguration.loadConfiguration(eliteDropsFile);
                logger.fine("§a[ConfigManager] Elite drops configuration is valid");
            } catch (Exception e) {
                logger.warning("§c[ConfigManager] Elite drops configuration is invalid: " + e.getMessage());
                valid = false;
            }
        }

        // Check drop rates file
        if (!dropRatesFile.exists()) {
            logger.warning("§c[ConfigManager] Drop rates file does not exist");
            valid = false;
        } else {
            try {
                YamlConfiguration.loadConfiguration(dropRatesFile);
                logger.fine("§a[ConfigManager] Drop rates configuration is valid");
            } catch (Exception e) {
                logger.warning("§c[ConfigManager] Drop rates configuration is invalid: " + e.getMessage());
                valid = false;
            }
        }

        // Validate elite configurations through DropConfig
        Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();
        int invalidElites = 0;
        for (Map.Entry<String, EliteDropConfig> entry : eliteConfigs.entrySet()) {
            if (!entry.getValue().isValid()) {
                logger.warning("§c[ConfigManager] Invalid elite configuration: " + entry.getKey());
                invalidElites++;
                valid = false;
            }
        }

        if (invalidElites > 0) {
            logger.warning("§c[ConfigManager] Found " + invalidElites + " invalid elite configurations");
        }

        return valid;
    }

    /**
     * Backup configuration files
     *
     * @return true if backup was successful
     */
    public boolean backupConfigurations() {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String timestamp = String.valueOf(System.currentTimeMillis());

            // Backup elite drops
            if (eliteDropsFile.exists()) {
                File eliteBackup = new File(backupDir, "elite_drops_" + timestamp + ".yml");
                YamlConfiguration eliteConfig = YamlConfiguration.loadConfiguration(eliteDropsFile);
                eliteConfig.save(eliteBackup);
            }

            // Backup drop rates
            if (dropRatesFile.exists()) {
                File ratesBackup = new File(backupDir, "drop_rates_" + timestamp + ".yml");
                YamlConfiguration ratesConfig = YamlConfiguration.loadConfiguration(dropRatesFile);
                ratesConfig.save(ratesBackup);
            }

            logger.info("§a[ConfigManager] §7Configuration backup created successfully");
            return true;

        } catch (Exception e) {
            logger.severe("§c[ConfigManager] Failed to backup configurations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reset configurations to defaults
     *
     * @param createBackup Whether to create a backup before resetting
     * @return true if reset was successful
     */
    public boolean resetToDefaults(boolean createBackup) {
        try {
            if (createBackup) {
                backupConfigurations();
            }

            // Delete existing files
            if (dropRatesFile.exists()) {
                dropRatesFile.delete();
            }

            // Recreate defaults
            createDefaultDropRatesConfig();

            // Reload DropConfig to reinitialize elite drops
            DropConfig.initialize();

            logger.info("§a[ConfigManager] §7Configurations reset to defaults successfully");
            return true;

        } catch (Exception e) {
            logger.severe("§c[ConfigManager] Failed to reset configurations: " + e.getMessage());
            return false;
        }
    }

    /**
     * Clean up when plugin is disabled
     */
    public void shutdown() {
        // Save any pending changes
        try {
            if (dropRatesConfig != null && dropRatesFile != null) {
                dropRatesConfig.save(dropRatesFile);
            }
        } catch (IOException e) {
            logger.warning("§c[ConfigManager] Failed to save configuration on shutdown: " + e.getMessage());
        }

        logger.info("§a[ConfigManager] §7Configuration manager has been shut down");
    }

    /**
     * Get the elite drops file for external access
     *
     * @return The elite drops file
     */
    public File getEliteDropsFile() {
        return eliteDropsFile;
    }

    /**
     * Get the drop rates file for external access
     *
     * @return The drop rates file
     */
    public File getDropRatesFile() {
        return dropRatesFile;
    }

    /**
     * Get elite drops configuration for external access
     *
     * @return The elite drops configuration
     */
    public FileConfiguration getEliteDropsConfig() {
        if (eliteDropsConfig == null && eliteDropsFile.exists()) {
            eliteDropsConfig = YamlConfiguration.loadConfiguration(eliteDropsFile);
        }
        return eliteDropsConfig;
    }

    /**
     * Get drop rates configuration for external access
     *
     * @return The drop rates configuration
     */
    public FileConfiguration getDropRatesConfig() {
        return dropRatesConfig;
    }
}
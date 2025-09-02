package com.rednetty.server.core.config;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.drops.DropConfig;
import com.rednetty.server.core.mechanics.item.drops.types.EliteDropConfig;
import com.rednetty.server.core.mechanics.item.drops.types.TierConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration manager for the drop system.
 * Manages drop rates, elite configurations, and system modifiers.
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

    private ConfigManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void initialize() {
        // Ensure config directory exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Initialize configuration files
        initializeConfigFiles();

        // Load additional configurations that aren't handled by DropConfig
        loadDropRatesConfig();

        logger.info("Configuration manager initialized successfully");
    }

    private void initializeConfigFiles() {
        // Elite drops file (handled by DropConfig, but we track it for reloading)
        eliteDropsFile = new File(plugin.getDataFolder(), "elite_drops.yml");
        if (!eliteDropsFile.exists()) {
            plugin.saveResource("elite_drops.yml", false);
            logger.info("Created default elite_drops.yml file");
        }

        // Drop rates configuration file
        dropRatesFile = new File(plugin.getDataFolder(), "drop_rates.yml");
        if (!dropRatesFile.exists()) {
            createDefaultDropRatesConfig();
        }
    }

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
            logger.info("Created default drop_rates.yml file");

        } catch (IOException e) {
            logger.severe("Failed to create default drop rates config: " + e.getMessage());
        }
    }

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
                                logger.fine("Tier " + tier + " drop rate override: " + dropRate + "%");
                            }
                        }
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid tier key in drop_rates.yml: " + tierKey);
                    }
                }
            }

            // Drop rates configuration loaded

        } catch (Exception e) {
            logger.severe("Failed to load drop rates configuration: " + e.getMessage());
        }
    }

    public void updateDropRate(int tier, int rate) {
        if (tier < 1 || tier > 6) {
            logger.warning("Invalid tier for drop rate update: " + tier);
            return;
        }

        if (rate < 0 || rate > 100) {
            logger.warning("Invalid drop rate: " + rate + "% (must be 0-100)");
            return;
        }

        try {
            // Update in-memory configuration
            TierConfig tierConfig = DropConfig.getTierConfig(tier);
            if (tierConfig != null) {
                // Note: This would require updating TierConfig to have setters
                // For now, we'll just update the config file
                logger.info("Updated tier " + tier + " drop rate to " + rate + "%");
            }

            // Update configuration file
            if (dropRatesConfig != null) {
                dropRatesConfig.set("tiers." + tier + ".dropRate", rate);
                dropRatesConfig.save(dropRatesFile);
                logger.fine("Saved drop rate update to configuration file");
            }

        } catch (IOException e) {
            logger.severe("Could not save drop rate update: " + e.getMessage());
        }
    }

    public void updateEliteDropRate(int tier, int rate) {
        if (tier < 1 || tier > 6) {
            logger.warning("Invalid tier for elite drop rate update: " + tier);
            return;
        }

        if (rate < 0 || rate > 100) {
            logger.warning("Invalid elite drop rate: " + rate + "% (must be 0-100)");
            return;
        }

        try {
            // Update configuration file
            if (dropRatesConfig != null) {
                dropRatesConfig.set("tiers." + tier + ".eliteDropRate", rate);
                dropRatesConfig.save(dropRatesFile);
                logger.info("Updated tier " + tier + " elite drop rate to " + rate + "%");
            }

        } catch (IOException e) {
            logger.severe("Could not save elite drop rate update: " + e.getMessage());
        }
    }

    public boolean reloadEliteDrops() {
        try {
            logger.info("Reloading elite drop configurations...");

            // Delegate to DropConfig which handles the actual YAML loading
            DropConfig.reloadEliteDrops();

            Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();
            logger.info("Successfully reloaded " + eliteConfigs.size() + " elite configurations");

            return true;

        } catch (Exception e) {
            logger.severe("Failed to reload elite drop configurations: " + e.getMessage());
            return false;
        }
    }

    public boolean saveEliteDrops() {
        try {
            DropConfig.saveEliteDrops();
            logger.info("Elite drop configurations saved successfully");
            return true;

        } catch (Exception e) {
            logger.severe("Failed to save elite drop configurations: " + e.getMessage());
            return false;
        }
    }

    public double getDropRateModifier(String modifierType) {
        if (dropRatesConfig == null) {
            return 1.0;
        }

        return dropRatesConfig.getDouble("modifiers." + modifierType + "DropMultiplier", 1.0);
    }

    public void setDropRateModifier(String modifierType, double value) {
        if (dropRatesConfig == null) {
            return;
        }

        try {
            dropRatesConfig.set("modifiers." + modifierType + "DropMultiplier", value);
            dropRatesConfig.save(dropRatesFile);
            logger.info("Updated " + modifierType + " drop multiplier to " + value);

        } catch (IOException e) {
            logger.severe("Failed to save drop rate modifier: " + e.getMessage());
        }
    }

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

    public boolean validateConfigurations() {
        boolean valid = true;

        // Check elite drops file
        if (!eliteDropsFile.exists()) {
            logger.warning("Elite drops file does not exist");
            valid = false;
        } else {
            try {
                YamlConfiguration.loadConfiguration(eliteDropsFile);
                logger.fine("Elite drops configuration is valid");
            } catch (Exception e) {
                logger.warning("Elite drops configuration is invalid: " + e.getMessage());
                valid = false;
            }
        }

        // Check drop rates file
        if (!dropRatesFile.exists()) {
            logger.warning("Drop rates file does not exist");
            valid = false;
        } else {
            try {
                YamlConfiguration.loadConfiguration(dropRatesFile);
                logger.fine("Drop rates configuration is valid");
            } catch (Exception e) {
                logger.warning("Drop rates configuration is invalid: " + e.getMessage());
                valid = false;
            }
        }

        // Validate elite configurations through DropConfig
        Map<String, EliteDropConfig> eliteConfigs = DropConfig.getEliteDropConfigs();
        int invalidElites = 0;
        for (Map.Entry<String, EliteDropConfig> entry : eliteConfigs.entrySet()) {
            if (!entry.getValue().isValid()) {
                logger.warning("Invalid elite configuration: " + entry.getKey());
                invalidElites++;
                valid = false;
            }
        }

        if (invalidElites > 0) {
            logger.warning("Found " + invalidElites + " invalid elite configurations");
        }

        return valid;
    }

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

            logger.info("Configuration backup created successfully");
            return true;

        } catch (Exception e) {
            logger.severe("Failed to backup configurations: " + e.getMessage());
            return false;
        }
    }

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

            logger.info("Configurations reset to defaults successfully");
            return true;

        } catch (Exception e) {
            logger.severe("Failed to reset configurations: " + e.getMessage());
            return false;
        }
    }

    public void shutdown() {
        // Save any pending changes
        try {
            if (dropRatesConfig != null && dropRatesFile != null) {
                dropRatesConfig.save(dropRatesFile);
            }
        } catch (IOException e) {
            logger.warning("Failed to save configuration on shutdown: " + e.getMessage());
        }

        logger.info("Configuration manager has been shut down");
    }

    public File getEliteDropsFile() {
        return eliteDropsFile;
    }

    public File getDropRatesFile() {
        return dropRatesFile;
    }

    public FileConfiguration getEliteDropsConfig() {
        if (eliteDropsConfig == null && eliteDropsFile.exists()) {
            eliteDropsConfig = YamlConfiguration.loadConfiguration(eliteDropsFile);
        }
        return eliteDropsConfig;
    }

    public FileConfiguration getDropRatesConfig() {
        return dropRatesConfig;
    }
}
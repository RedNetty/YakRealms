package com.rednetty.server.core.config;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles loading, saving, and accessing YAML configuration files
 */
public class YAMLConfig {
    private static final Logger LOGGER = Logger.getLogger(YAMLConfig.class.getName());

    private final YakRealms plugin;
    private final String fileName;
    private final String directory;
    private File configFile;
    private FileConfiguration fileConfiguration;

    /**
     * Constructor
     *
     * @param plugin    The plugin instance
     * @param fileName  The name of the configuration file
     * @param directory The directory path relative to the plugin data folder
     */
    public YAMLConfig(YakRealms plugin, String fileName, String directory) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.directory = directory;
        this.configFile = new File(plugin.getDataFolder() + File.separator + directory, fileName);
        saveDefaultConfig();
        reloadConfig();
    }

    /**
     * Constructor without a subdirectory
     *
     * @param plugin   The plugin instance
     * @param fileName The name of the configuration file
     */
    public YAMLConfig(YakRealms plugin, String fileName) {
        this(plugin, fileName, "");
    }

    /**
     * Gets the file configuration
     *
     * @return The file configuration
     */
    public FileConfiguration getConfig() {
        if (fileConfiguration == null) {
            reloadConfig();
        }
        return fileConfiguration;
    }

    /**
     * Reloads the configuration from file
     */
    public void reloadConfig() {
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);

        // Look for defaults in the jar
        InputStream defaultStream = plugin.getResource(directory + File.separator + fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            fileConfiguration.setDefaults(defaultConfig);
        }
    }

    /**
     * Saves the configuration to file
     */
    public void saveConfig() {
        if (fileConfiguration == null || configFile == null) {
            return;
        }

        try {
            getConfig().save(configFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, ChatColor.RED + "Could not save config to " + configFile, e);
        }
    }

    /**
     * Saves the default configuration file if it doesn't exist
     */
    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            // Create parent directories if needed
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Try to copy default from jar
            InputStream in = plugin.getResource(directory + File.separator + fileName);
            if (in != null) {
                try {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Created default configuration file: " + fileName);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not save default config to " + configFile, e);
                }
            } else {
                // Create an empty file if no default exists
                try {
                    configFile.createNewFile();
                    LOGGER.warning("Created empty configuration file (no default found): " + fileName);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not create empty config file: " + fileName, e);
                }
            }
        }
    }

    /**
     * Gets the configuration file
     *
     * @return The configuration file
     */
    public File getConfigFile() {
        return configFile;
    }
}
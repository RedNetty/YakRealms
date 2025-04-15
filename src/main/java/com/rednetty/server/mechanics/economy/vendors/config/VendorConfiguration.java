package com.rednetty.server.mechanics.economy.vendors.config;

import com.rednetty.server.YakRealms;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Configuration manager for the vendor system
 */
public class VendorConfiguration {

    private static VendorConfiguration instance;

    private final YakRealms plugin;
    private File configFile;
    private FileConfiguration config;

    // Default configuration values
    private static final Map<String, Object> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("debug-mode", false);
        DEFAULTS.put("auto-fix-behaviors", true);
        DEFAULTS.put("periodic-validation", true);
        DEFAULTS.put("validation-interval-minutes", 30);
        DEFAULTS.put("auto-backup", true);
        DEFAULTS.put("backup-interval-hours", 12);
        DEFAULTS.put("max-backups", 10);
        DEFAULTS.put("hologram-height", 2.8);
        DEFAULTS.put("hologram-line-spacing", 0.3);
        DEFAULTS.put("default-behavior-class", "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior");
        DEFAULTS.put("citizen-skin-feature", "SLIM_ARMS");
        DEFAULTS.put("citizen-spawn-command", "npc skin %name%");
        DEFAULTS.put("default-hologram-text", Arrays.asList(
                "&6&oVendor",
                "&7Click to interact"
        ));
    }

    private VendorConfiguration(YakRealms plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Get the configuration instance
     */
    public static VendorConfiguration getInstance(YakRealms plugin) {
        if (instance == null) {
            instance = new VendorConfiguration(plugin);
        }
        return instance;
    }

    /**
     * Reload the configuration
     */
    public void reload() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "vendor-config.yml");
        }

        // Create config if it doesn't exist
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();

                // Load defaults
                config = YamlConfiguration.loadConfiguration(configFile);
                setDefaults();

                config.save(configFile);
                plugin.getLogger().info("Created default vendor configuration");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create vendor configuration file", e);
            }
        } else {
            config = YamlConfiguration.loadConfiguration(configFile);

            // Check for missing settings
            boolean modified = false;
            for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
                if (!config.contains(entry.getKey())) {
                    config.set(entry.getKey(), entry.getValue());
                    modified = true;
                }
            }

            // Save if modified
            if (modified) {
                try {
                    config.save(configFile);
                    plugin.getLogger().info("Updated vendor configuration with new defaults");
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save vendor configuration", e);
                }
            }
        }
    }

    /**
     * Set default configuration values
     */
    private void setDefaults() {
        for (Map.Entry<String, Object> entry : DEFAULTS.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Save the configuration
     */
    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save vendor configuration", e);
        }
    }

    /**
     * Get a boolean configuration value
     */
    public boolean getBoolean(String path) {
        return config.getBoolean(path, (Boolean) DEFAULTS.getOrDefault(path, false));
    }

    /**
     * Get an integer configuration value
     */
    public int getInt(String path) {
        return config.getInt(path, (Integer) DEFAULTS.getOrDefault(path, 0));
    }

    /**
     * Get a double configuration value
     */
    public double getDouble(String path) {
        return config.getDouble(path, (Double) DEFAULTS.getOrDefault(path, 0.0));
    }

    /**
     * Get a string configuration value
     */
    public String getString(String path) {
        return config.getString(path, (String) DEFAULTS.getOrDefault(path, ""));
    }

    /**
     * Get a string list configuration value
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    /**
     * Set a configuration value
     */
    public void set(String path, Object value) {
        config.set(path, value);
    }
}
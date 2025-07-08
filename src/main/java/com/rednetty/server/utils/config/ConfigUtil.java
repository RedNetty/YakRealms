package com.rednetty.server.utils.config;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Utility for easier configuration file management
 * Provides advanced config operations and safe value handling
 */
public class ConfigUtil {

    private static JavaPlugin plugin;
    private static final Map<String, YamlConfiguration> loadedConfigs = new HashMap<>();

    public static void init(JavaPlugin plugin) {
        ConfigUtil.plugin = plugin;
    }

    /**
     * Load or reload a config file
     * @param fileName Name of the config file (without .yml extension)
     * @return Loaded configuration
     */
    public static YamlConfiguration loadConfig(String fileName) {
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }

        File configFile = new File(plugin.getDataFolder(), fileName);

        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Copy default config from resources if file doesn't exist
        if (!configFile.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                // File doesn't exist in resources, create empty file
                try {
                    configFile.createNewFile();
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create config file: " + fileName, ex);
                }
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        loadedConfigs.put(fileName, config);
        return config;
    }

    /**
     * Get a loaded config or load it if not already loaded
     */
    public static YamlConfiguration getConfig(String fileName) {
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }

        return loadedConfigs.computeIfAbsent(fileName, ConfigUtil::loadConfig);
    }

    /**
     * Save a config file
     * @param fileName Name of the config file
     * @param config Configuration to save
     * @return True if saved successfully
     */
    public static boolean saveConfig(String fileName, YamlConfiguration config) {
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }

        try {
            File configFile = new File(plugin.getDataFolder(), fileName);
            config.save(configFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config file: " + fileName, e);
            return false;
        }
    }

    /**
     * Save a loaded config
     */
    public static boolean saveConfig(String fileName) {
        YamlConfiguration config = loadedConfigs.get(fileName.endsWith(".yml") ? fileName : fileName + ".yml");
        if (config != null) {
            return saveConfig(fileName, config);
        }
        return false;
    }

    /**
     * Create a backup of a config file
     * @param fileName Name of the config file
     * @return True if backup created successfully
     */
    public static boolean backupConfig(String fileName) {
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }

        try {
            File originalFile = new File(plugin.getDataFolder(), fileName);
            if (!originalFile.exists()) return false;

            String backupName = fileName.replace(".yml", "_backup_" + System.currentTimeMillis() + ".yml");
            File backupFile = new File(plugin.getDataFolder(), backupName);

            Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not backup config file: " + fileName, e);
            return false;
        }
    }

    /**
     * Get string with default value
     * @param config Configuration
     * @param path Config path
     * @param defaultValue Default value
     * @return String value or default
     */
    public static String getString(FileConfiguration config, String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    /**
     * Get integer with default value
     */
    public static int getInt(FileConfiguration config, String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * Get double with default value
     */
    public static double getDouble(FileConfiguration config, String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
    }

    /**
     * Get boolean with default value
     */
    public static boolean getBoolean(FileConfiguration config, String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    /**
     * Get string list with default value
     */
    public static List<String> getStringList(FileConfiguration config, String path, List<String> defaultValue) {
        List<String> list = config.getStringList(path);
        return list.isEmpty() && defaultValue != null ? defaultValue : list;
    }

    /**
     * Get integer list with default value
     */
    public static List<Integer> getIntegerList(FileConfiguration config, String path, List<Integer> defaultValue) {
        List<Integer> list = config.getIntegerList(path);
        return list.isEmpty() && defaultValue != null ? defaultValue : list;
    }

    /**
     * Get location from config
     * @param config Configuration
     * @param path Config path
     * @return Location or null if invalid
     */
    public static Location getLocation(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;

        try {
            String worldName = section.getString("world");
            if (worldName == null) return null;

            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            float yaw = (float) section.getDouble("yaw", 0);
            float pitch = (float) section.getDouble("pitch", 0);

            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Set location in config
     * @param config Configuration
     * @param path Config path
     * @param location Location to save
     */
    public static void setLocation(FileConfiguration config, String path, Location location) {
        if (location == null) {
            config.set(path, null);
            return;
        }

        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    /**
     * Get color from config (RGB format)
     * @param config Configuration
     * @param path Config path
     * @return Color or null if invalid
     */
    public static Color getColor(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return null;

        try {
            int red = section.getInt("red", 0);
            int green = section.getInt("green", 0);
            int blue = section.getInt("blue", 0);

            return Color.fromRGB(
                    Math.max(0, Math.min(255, red)),
                    Math.max(0, Math.min(255, green)),
                    Math.max(0, Math.min(255, blue))
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Set color in config
     */
    public static void setColor(FileConfiguration config, String path, Color color) {
        if (color == null) {
            config.set(path, null);
            return;
        }

        config.set(path + ".red", color.getRed());
        config.set(path + ".green", color.getGreen());
        config.set(path + ".blue", color.getBlue());
    }

    /**
     * Get enum value from config
     * @param config Configuration
     * @param path Config path
     * @param enumClass Enum class
     * @param defaultValue Default value
     * @return Enum value or default
     */
    public static <T extends Enum<T>> T getEnum(FileConfiguration config, String path, Class<T> enumClass, T defaultValue) {
        String value = config.getString(path);
        if (value == null) return defaultValue;

        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * Set enum value in config
     */
    public static <T extends Enum<T>> void setEnum(FileConfiguration config, String path, T enumValue) {
        config.set(path, enumValue != null ? enumValue.name() : null);
    }

    /**
     * Check if path exists and has a value
     */
    public static boolean hasValue(FileConfiguration config, String path) {
        return config.contains(path) && config.get(path) != null;
    }

    /**
     * Get all keys in a section
     * @param config Configuration
     * @param path Section path
     * @param deep Whether to get keys recursively
     * @return Set of keys
     */
    public static Set<String> getKeys(FileConfiguration config, String path, boolean deep) {
        ConfigurationSection section = path.isEmpty() ? config : config.getConfigurationSection(path);
        if (section == null) return new HashSet<>();

        return section.getKeys(deep);
    }

    /**
     * Copy section from one config to another
     * @param source Source configuration
     * @param target Target configuration
     * @param sourcePath Source section path
     * @param targetPath Target section path
     */
    public static void copySection(FileConfiguration source, FileConfiguration target, String sourcePath, String targetPath) {
        ConfigurationSection sourceSection = source.getConfigurationSection(sourcePath);
        if (sourceSection == null) return;

        for (String key : sourceSection.getKeys(true)) {
            Object value = sourceSection.get(key);
            target.set(targetPath + "." + key, value);
        }
    }

    /**
     * Merge two configurations (target gets priority)
     * @param base Base configuration
     * @param override Override configuration
     * @return Merged configuration
     */
    public static YamlConfiguration mergeConfigs(FileConfiguration base, FileConfiguration override) {
        YamlConfiguration merged = new YamlConfiguration();

        // Copy all from base
        for (String key : base.getKeys(true)) {
            merged.set(key, base.get(key));
        }

        // Override with values from override config
        for (String key : override.getKeys(true)) {
            merged.set(key, override.get(key));
        }

        return merged;
    }

    /**
     * Validate config against required keys
     * @param config Configuration to validate
     * @param requiredKeys Required keys
     * @return List of missing keys
     */
    public static List<String> validateConfig(FileConfiguration config, String... requiredKeys) {
        List<String> missing = new ArrayList<>();

        for (String key : requiredKeys) {
            if (!hasValue(config, key)) {
                missing.add(key);
            }
        }

        return missing;
    }

    /**
     * Set default values if they don't exist
     * @param config Configuration
     * @param defaults Map of default values
     */
    public static void setDefaults(FileConfiguration config, Map<String, Object> defaults) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!hasValue(config, entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Remove null or empty values from config
     * @param config Configuration to clean
     */
    public static void cleanConfig(FileConfiguration config) {
        Set<String> keysToRemove = new HashSet<>();

        for (String key : config.getKeys(true)) {
            Object value = config.get(key);
            if (value == null ||
                    (value instanceof String && ((String) value).isEmpty()) ||
                    (value instanceof List && ((List<?>) value).isEmpty())) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            config.set(key, null);
        }
    }

    /**
     * Get config value as specific type with type checking
     * @param config Configuration
     * @param path Config path
     * @param type Expected type
     * @param defaultValue Default value
     * @return Typed value or default
     */
    @SuppressWarnings("unchecked")
    public static <T> T getTyped(FileConfiguration config, String path, Class<T> type, T defaultValue) {
        Object value = config.get(path);
        if (value == null) return defaultValue;

        if (type.isInstance(value)) {
            return (T) value;
        }

        // Type conversion attempts
        if (type == String.class) {
            return (T) value.toString();
        } else if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        } else if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        } else if (type == Boolean.class) {
            if (value instanceof String) {
                return (T) Boolean.valueOf(Boolean.parseBoolean((String) value));
            }
        }

        return defaultValue;
    }

    /**
     * Get config section as map
     * @param config Configuration
     * @param path Section path
     * @return Map representation of section
     */
    public static Map<String, Object> sectionToMap(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return new HashMap<>();

        Map<String, Object> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            map.put(key, section.get(key));
        }

        return map;
    }

    /**
     * Set map as config section
     */
    public static void mapToSection(FileConfiguration config, String path, Map<String, Object> map) {
        config.set(path, null); // Clear existing section
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            config.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get all config files in plugin folder
     * @return List of config file names
     */
    public static List<String> getConfigFiles() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) return new ArrayList<>();

        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return new ArrayList<>();

        return Arrays.stream(files)
                .map(File::getName)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * Reload all loaded configs
     */
    public static void reloadAllConfigs() {
        Set<String> configNames = new HashSet<>(loadedConfigs.keySet());
        loadedConfigs.clear();

        for (String configName : configNames) {
            loadConfig(configName);
        }
    }
}
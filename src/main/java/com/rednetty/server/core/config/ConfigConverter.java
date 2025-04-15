package com.rednetty.server.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.rednetty.server.YakRealms;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for converting between YAML and JSON configuration formats
 */
public class ConfigConverter {
    private static final Logger LOGGER = Logger.getLogger(ConfigConverter.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final YakRealms plugin;

    /**
     * Constructor
     */
    public ConfigConverter() {
        this.plugin = YakRealms.getInstance();
    }

    /**
     * Convert YAML configuration files to JSON
     *
     * @param directory The directory containing the YAML files
     */
    public void convertYamlToJson(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.warning("Directory does not exist: " + directory.getPath());
            return;
        }

        // Find all YAML files in the directory
        for (File file : directory.listFiles()) {
            if (file.isFile() && (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml"))) {
                String outputName = file.getName().replaceAll("\\.(yml|yaml)$", ".json");
                File outputFile = new File(directory, outputName);

                try {
                    // Load YAML file
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

                    // Convert to JSON
                    JsonObject jsonObject = yamlToJson(config);

                    // Save JSON file
                    try (FileWriter writer = new FileWriter(outputFile)) {
                        GSON.toJson(jsonObject, writer);
                    }

                    LOGGER.info("Converted " + file.getName() + " to " + outputFile.getName());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to convert " + file.getName() + " to JSON", e);
                }
            }
        }
    }

    /**
     * Convert JSON configuration files to YAML
     *
     * @param directory The directory containing the JSON files
     */
    public void convertJsonToYaml(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.warning("Directory does not exist: " + directory.getPath());
            return;
        }

        // Find all JSON files in the directory
        for (File file : directory.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".json")) {
                String outputName = file.getName().replace(".json", ".yml");
                File outputFile = new File(directory, outputName);

                try {
                    // Load JSON file
                    JsonObject jsonObject;
                    try (FileReader reader = new FileReader(file)) {
                        jsonObject = GSON.fromJson(reader, JsonObject.class);
                    }

                    // Convert to YAML
                    YamlConfiguration config = new YamlConfiguration();
                    jsonToYaml(jsonObject, config, "");

                    // Save YAML file
                    config.save(outputFile);

                    LOGGER.info("Converted " + file.getName() + " to " + outputFile.getName());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to convert " + file.getName() + " to YAML", e);
                }
            }
        }
    }

    /**
     * Convert YAML configuration to JSON object
     *
     * @param config The YAML configuration
     * @return The JSON object
     */
    private JsonObject yamlToJson(YamlConfiguration config) {
        JsonObject jsonObject = new JsonObject();

        for (String key : config.getKeys(false)) {
            if (config.isConfigurationSection(key)) {
                // Recursively convert configuration sections
                jsonObject.add(key, yamlToJson(config.getConfigurationSection(key)));
            } else {
                // Convert values based on type
                Object value = config.get(key);
                if (value instanceof String) {
                    jsonObject.addProperty(key, (String) value);
                } else if (value instanceof Number) {
                    if (value instanceof Integer) {
                        jsonObject.addProperty(key, (Integer) value);
                    } else if (value instanceof Double) {
                        jsonObject.addProperty(key, (Double) value);
                    } else if (value instanceof Float) {
                        jsonObject.addProperty(key, (Float) value);
                    } else if (value instanceof Long) {
                        jsonObject.addProperty(key, (Long) value);
                    }
                } else if (value instanceof Boolean) {
                    jsonObject.addProperty(key, (Boolean) value);
                }
            }
        }

        return jsonObject;
    }

    /**
     * Convert YAML configuration section to JSON object
     *
     * @param config The YAML configuration section
     * @return The JSON object
     */
    private JsonObject yamlToJson(ConfigurationSection config) {
        JsonObject jsonObject = new JsonObject();

        for (String key : config.getKeys(false)) {
            if (config.isConfigurationSection(key)) {
                // Recursively convert configuration sections
                jsonObject.add(key, yamlToJson(config.getConfigurationSection(key)));
            } else {
                // Convert values based on type
                Object value = config.get(key);
                if (value instanceof String) {
                    jsonObject.addProperty(key, (String) value);
                } else if (value instanceof Number) {
                    if (value instanceof Integer) {
                        jsonObject.addProperty(key, (Integer) value);
                    } else if (value instanceof Double) {
                        jsonObject.addProperty(key, (Double) value);
                    } else if (value instanceof Float) {
                        jsonObject.addProperty(key, (Float) value);
                    } else if (value instanceof Long) {
                        jsonObject.addProperty(key, (Long) value);
                    }
                } else if (value instanceof Boolean) {
                    jsonObject.addProperty(key, (Boolean) value);
                }
            }
        }

        return jsonObject;
    }

    /**
     * Convert JSON object to YAML configuration
     *
     * @param jsonObject The JSON object
     * @param config     The YAML configuration
     * @param path       The current path
     */
    private void jsonToYaml(JsonObject jsonObject, YamlConfiguration config, String path) {
        for (String key : jsonObject.keySet()) {
            String currentPath = path.isEmpty() ? key : path + "." + key;

            if (jsonObject.get(key).isJsonObject()) {
                // Recursively convert JSON objects
                jsonToYaml(jsonObject.getAsJsonObject(key), config, currentPath);
            } else if (jsonObject.get(key).isJsonPrimitive()) {
                // Convert primitive types
                if (jsonObject.get(key).getAsJsonPrimitive().isString()) {
                    config.set(currentPath, jsonObject.get(key).getAsString());
                } else if (jsonObject.get(key).getAsJsonPrimitive().isNumber()) {
                    config.set(currentPath, jsonObject.get(key).getAsNumber());
                } else if (jsonObject.get(key).getAsJsonPrimitive().isBoolean()) {
                    config.set(currentPath, jsonObject.get(key).getAsBoolean());
                }
            }
        }
    }
}
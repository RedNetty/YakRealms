package com.rednetty.server.core.config;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.DropConfig;
import com.rednetty.server.mechanics.item.drops.types.*;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the configuration files for the drop system
 */
public class ConfigManager {
    private static ConfigManager instance;
    private final YakRealms plugin;
    private final Logger logger;

    private File itemTypesFile;
    private File eliteDropsFile;
    private File rarityConfigFile;
    private File tierConfigFile;

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
            plugin.getDataFolder().mkdir();
        }

        // Copy default configs if they don't exist
        createDefaultConfigs();

        // Load configs
        loadDropConfigs();

        logger.info("[ConfigManager] Configuration has been loaded");
    }

    /**
     * Creates default config files if they don't exist
     */
    private void createDefaultConfigs() {
        itemTypesFile = new File(plugin.getDataFolder(), "item_types.yml");
        eliteDropsFile = new File(plugin.getDataFolder(), "elite_drops.yml");
        rarityConfigFile = new File(plugin.getDataFolder(), "rarity_config.yml");
        tierConfigFile = new File(plugin.getDataFolder(), "tier_config.yml");

        if (!itemTypesFile.exists()) {
            plugin.saveResource("item_types.yml", false);
        }

        if (!eliteDropsFile.exists()) {
            plugin.saveResource("elite_drops.yml", false);
        }

        if (!rarityConfigFile.exists()) {
            plugin.saveResource("rarity_config.yml", false);
        }

        if (!tierConfigFile.exists()) {
            plugin.saveResource("tier_config.yml", false);
        }
    }

    /**
     * Load all drop configuration files
     */
    private void loadDropConfigs() {
        loadItemTypeConfigs();
        loadRarityConfigs();
        loadTierConfigs();
        loadEliteDropConfigs();
    }

    /**
     * Loads item type configurations
     */
    private void loadItemTypeConfigs() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(itemTypesFile);
        Map<String, ItemTypeConfig> itemTypeConfigs = new HashMap<>();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) {
                int typeId = Integer.parseInt(key);
                String name = section.getString("name", "Unknown");
                String materialSuffix = section.getString("materialSuffix", "SWORD");
                boolean isWeapon = section.getBoolean("isWeapon", true);

                ItemTypeConfig itemTypeConfig = new ItemTypeConfig(typeId, name, materialSuffix, isWeapon);
                itemTypeConfigs.put(key, itemTypeConfig);
            }
        }

        DropConfig.setItemTypeConfigs(itemTypeConfigs);
        logger.info("Loaded " + itemTypeConfigs.size() + " item type configurations");
    }

    /**
     * Loads rarity configurations
     */
    private void loadRarityConfigs() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(rarityConfigFile);
        Map<Integer, RarityConfig> rarityConfigs = new HashMap<>();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) {
                int rarity = Integer.parseInt(key);
                String name = section.getString("name", "Unknown");
                ChatColor color = ChatColor.valueOf(section.getString("color", "WHITE"));
                int dropChance = section.getInt("dropChance", 50);

                RarityConfig rarityConfig = new RarityConfig();
                rarityConfig.setRarity(rarity);
                rarityConfig.setName(name);
                rarityConfig.setColor(color);
                rarityConfig.setDropChance(dropChance);

                // Load stat multipliers
                ConfigurationSection multSection = section.getConfigurationSection("statMultipliers");
                if (multSection != null) {
                    Map<String, Double> multipliers = new HashMap<>();
                    for (String stat : multSection.getKeys(false)) {
                        multipliers.put(stat, multSection.getDouble(stat, 1.0));
                    }
                    rarityConfig.setStatMultipliers(multipliers);
                }

                rarityConfigs.put(rarity, rarityConfig);
            }
        }

        DropConfig.setRarityConfigs(rarityConfigs);
        logger.info("Loaded " + rarityConfigs.size() + " rarity configurations");
    }

    /**
     * Loads tier configurations
     */
    private void loadTierConfigs() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(tierConfigFile);
        Map<Integer, TierConfig> tierConfigs = new HashMap<>();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) {
                int tier = Integer.parseInt(key);
                ChatColor color = ChatColor.valueOf(section.getString("color", "WHITE"));
                int dropRate = section.getInt("dropRate", 50);
                int eliteDropRate = section.getInt("eliteDropRate", 55);
                int crateDropRate = section.getInt("crateDropRate", 5);

                TierConfig tierConfig = new TierConfig();
                tierConfig.setTier(tier);
                tierConfig.setColor(color);
                tierConfig.setDropRate(dropRate);
                tierConfig.setEliteDropRate(eliteDropRate);
                tierConfig.setCrateDropRate(crateDropRate);

                // Load material prefixes
                ConfigurationSection materialSection = section.getConfigurationSection("materials");
                if (materialSection != null) {
                    Map<String, String> materials = new HashMap<>();
                    for (String type : materialSection.getKeys(false)) {
                        materials.put(type, materialSection.getString(type, ""));
                    }
                    tierConfig.setMaterials(materials);
                }

                tierConfigs.put(tier, tierConfig);
            }
        }

        DropConfig.setTierConfigs(tierConfigs);
        logger.info("Loaded " + tierConfigs.size() + " tier configurations");
    }

    /**
     * Loads elite drop configurations
     */
    private void loadEliteDropConfigs() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(eliteDropsFile);
        Map<String, EliteDropConfig> eliteDropConfigs = new HashMap<>();

        for (String mobType : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(mobType);
            if (section != null) {
                EliteDropConfig eliteConfig = new EliteDropConfig();
                eliteConfig.setMobType(mobType);
                eliteConfig.setTier(section.getInt("tier", 1));
                eliteConfig.setRarity(section.getInt("rarity", 1));

                // Load common stats
                loadStatRanges(eliteConfig.getStatRanges(), section.getConfigurationSection("stats"));

                // Load weapon stats
                loadStatRanges(eliteConfig.getWeaponStatRanges(), section.getConfigurationSection("weaponStats"));

                // Load armor stats
                loadStatRanges(eliteConfig.getArmorStatRanges(), section.getConfigurationSection("armorStats"));

                // Process armor type-specific stats (like HP for different armor pieces)
                processArmorTypeStats(eliteConfig, section);

                // Load item details
                loadItemDetails(eliteConfig, section.getConfigurationSection("items"));

                eliteDropConfigs.put(mobType.toLowerCase(), eliteConfig);
            }
        }

        DropConfig.setEliteDropConfigs(eliteDropConfigs);
        logger.info("Loaded " + eliteDropConfigs.size() + " elite drop configurations");
    }

    /**
     * Process armor type-specific stats from configuration
     *
     * @param config  The elite drop configuration to populate
     * @param section The configuration section
     */
    private void processArmorTypeStats(EliteDropConfig config, ConfigurationSection section) {
        if (section.contains("armorStats.hp")) {
            ConfigurationSection hpSection = section.getConfigurationSection("armorStats.hp");
            if (hpSection != null) {
                Map<String, StatRange> armorTypeMap = new HashMap<>();

                // Process each armor type
                for (String armorType : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                    ConfigurationSection armorSection = hpSection.getConfigurationSection(armorType);
                    if (armorSection != null) {
                        int min = armorSection.getInt("min", 0);
                        int max = armorSection.getInt("max", 0);
                        if (min > 0 || max > 0) {
                            armorTypeMap.put(armorType, new StatRange(min, max));
                        }
                    }
                }

                if (!armorTypeMap.isEmpty()) {
                    config.getArmorTypeStats().put("hp", armorTypeMap);
                }
            }
        }
    }

    /**
     * Loads stat ranges from configuration section
     *
     * @param statRanges The map to populate
     * @param section    The configuration section
     */
    private void loadStatRanges(Map<String, StatRange> statRanges, ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String statName : section.getKeys(false)) {
            ConfigurationSection statSection = section.getConfigurationSection(statName);
            if (statSection != null && !(statName.equals("hp") && section.getName().equals("armorStats"))) {
                int min = statSection.getInt("min", 0);
                int max = statSection.getInt("max", 0);
                statRanges.put(statName, new StatRange(min, max));
            }
        }
    }

    /**
     * Loads item details from configuration section
     *
     * @param eliteConfig The elite drop configuration to populate
     * @param section     The configuration section
     */
    private void loadItemDetails(EliteDropConfig eliteConfig, ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String itemTypeStr : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(itemTypeStr);
            if (itemSection != null) {
                int itemType = Integer.parseInt(itemTypeStr);
                ItemDetails details = new ItemDetails();

                // Set name and lore
                details.setName(itemSection.getString("name", "Unknown Item"));
                details.setLore(itemSection.getString("lore", ""));

                // Set material
                String materialStr = itemSection.getString("material", "STONE");
                try {
                    details.setMaterial(Material.valueOf(materialStr));
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid material: " + materialStr + " for mob type: " + eliteConfig.getMobType() + ", item type: " + itemType);
                    details.setMaterial(Material.STONE);
                }

                // Load stat overrides
                ConfigurationSection overridesSection = itemSection.getConfigurationSection("statOverrides");
                if (overridesSection != null) {
                    for (String statName : overridesSection.getKeys(false)) {
                        ConfigurationSection statSection = overridesSection.getConfigurationSection(statName);
                        if (statSection != null) {
                            int min = statSection.getInt("min", 0);
                            int max = statSection.getInt("max", 0);
                            details.getStatOverrides().put(statName, new StatRange(min, max));
                        }
                    }
                }

                eliteConfig.getItemDetails().put(itemType, details);
            }
        }
    }

    /**
     * Updates drop rate for a specific tier
     *
     * @param tier The tier level
     * @param rate The new drop rate
     */
    public void updateDropRate(int tier, int rate) {
        if (tier < 1 || tier > 6) {
            logger.warning("Invalid tier for drop rate update: " + tier);
            return;
        }

        // Update in memory
        TierConfig config = DropConfig.getTierConfig(tier);
        if (config != null) {
            config.setDropRate(rate);
        }

        // Update configuration file
        try {
            FileConfiguration fileConfig = YamlConfiguration.loadConfiguration(tierConfigFile);
            fileConfig.set(tier + ".dropRate", rate);
            fileConfig.save(tierConfigFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save drop rate update to tier config", e);
        }
    }

    /**
     * Clean up when plugin is disabled
     */
    public void shutdown() {
        // Currently no cleanup needed
        logger.info("[ConfigManager] has been shut down");
    }
}
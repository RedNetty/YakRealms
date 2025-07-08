package com.rednetty.server.mechanics.economy.merchant;

import com.rednetty.server.YakRealms;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration management for the Merchant system
 * Handles loading, saving, and providing default values for merchant settings
 */
public class MerchantConfig {

    private static MerchantConfig instance;
    private final Logger logger;
    private YamlConfiguration config;
    private File configFile;

    // Configuration values with defaults
    private boolean enabled = true;
    private boolean debugMode = true; // Temporarily enabled for testing
    private double donorMultiplier = 1.2;
    private double staffMultiplier = 1.2;
    private int updateIntervalTicks = 20; // Changed to 20 ticks (1 second) instead of 5
    private boolean allowOres = true;
    private boolean allowWeapons = true;
    private boolean allowArmor = true;
    private boolean allowOrbs = true;
    private double baseOreValue = 3.0; // Reduced from 20.0 to 3.0
    private double oreMultiplier = 1.23;
    private int orbValue = 100; // Reduced from 500 to 100
    private double weaponArmorBaseMultiplier = 1.5; // Reduced from 5.0 to 1.5
    private double rarityBonusReduction = 0.25;
    private double randomVariationMin = 0.05; // Reduced from 0.1 to 0.05 (5%)
    private double randomVariationMax = 0.15; // Reduced from 0.3 to 0.15 (15%)

    // Ore tier values
    private final Map<String, Integer> oreTiers = new HashMap<>();

    // Material tier multipliers
    private final Map<String, Integer> materialTiers = new HashMap<>();

    // Rarity multipliers
    private final Map<String, Double> rarityMultipliers = new HashMap<>();

    /**
     * Private constructor for singleton pattern
     */
    private MerchantConfig() {
        this.logger = YakRealms.getInstance().getLogger();
        initializeDefaults();
    }

    /**
     * Get the singleton instance
     */
    public static MerchantConfig getInstance() {
        if (instance == null) {
            instance = new MerchantConfig();
        }
        return instance;
    }

    /**
     * Initialize default values
     */
    private void initializeDefaults() {
        // Ore tiers
        oreTiers.put("COAL_ORE", 1);
        oreTiers.put("DEEPSLATE_COAL_ORE", 1);
        oreTiers.put("COPPER_ORE", 2);
        oreTiers.put("DEEPSLATE_COPPER_ORE", 2);
        oreTiers.put("IRON_ORE", 3);
        oreTiers.put("DEEPSLATE_IRON_ORE", 3);
        oreTiers.put("GOLD_ORE", 4);
        oreTiers.put("DEEPSLATE_GOLD_ORE", 4);
        oreTiers.put("NETHER_GOLD_ORE", 4);
        oreTiers.put("REDSTONE_ORE", 5);
        oreTiers.put("DEEPSLATE_REDSTONE_ORE", 5);
        oreTiers.put("LAPIS_ORE", 6);
        oreTiers.put("DEEPSLATE_LAPIS_ORE", 6);
        oreTiers.put("NETHER_QUARTZ_ORE", 6);
        oreTiers.put("DIAMOND_ORE", 7);
        oreTiers.put("DEEPSLATE_DIAMOND_ORE", 7);
        oreTiers.put("EMERALD_ORE", 8);
        oreTiers.put("DEEPSLATE_EMERALD_ORE", 8);
        oreTiers.put("ANCIENT_DEBRIS", 10);

        // Material tiers
        materialTiers.put("WOOD", 10);
        materialTiers.put("LEATHER", 10);
        materialTiers.put("STONE", 15);
        materialTiers.put("GOLD", 20);
        materialTiers.put("IRON", 25);
        materialTiers.put("CHAINMAIL", 25);
        materialTiers.put("DIAMOND", 35);
        materialTiers.put("NETHERITE", 50);

        // Rarity multipliers
        rarityMultipliers.put("COMMON", 1.0);
        rarityMultipliers.put("UNCOMMON", 1.25);
        rarityMultipliers.put("RARE", 1.5);
        rarityMultipliers.put("EPIC", 2.0);
        rarityMultipliers.put("LEGENDARY", 3.0);
        rarityMultipliers.put("MYTHIC", 5.0);
    }

    /**
     * Load configuration from file
     */
    public void loadConfig() {
        try {
            configFile = new File(YakRealms.getInstance().getDataFolder(), "merchant.yml");

            if (!configFile.exists()) {
                createDefaultConfig();
            }

            config = YamlConfiguration.loadConfiguration(configFile);
            loadValues();

            logger.info("Merchant configuration loaded successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load merchant configuration", e);
            logger.info("Using default merchant configuration values");
        }
    }

    /**
     * Create default configuration file
     */
    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();

            config = new YamlConfiguration();
            setDefaultValues();
            saveConfig();

            logger.info("Created default merchant configuration file");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create default merchant configuration", e);
        }
    }

    /**
     * Set default values in the configuration
     */
    private void setDefaultValues() {
        // General settings
        config.set("general.enabled", enabled);
        config.set("general.debug-mode", debugMode);
        config.set("general.update-interval-ticks", updateIntervalTicks);

        // Multipliers
        config.set("multipliers.donor-bonus", donorMultiplier);
        config.set("multipliers.staff-bonus", staffMultiplier);
        config.set("multipliers.ore-base-value", baseOreValue);
        config.set("multipliers.ore-multiplier", oreMultiplier);
        config.set("multipliers.weapon-armor-base", weaponArmorBaseMultiplier);
        config.set("multipliers.rarity-bonus-reduction", rarityBonusReduction);
        config.set("multipliers.random-variation-min", randomVariationMin);
        config.set("multipliers.random-variation-max", randomVariationMax);

        // Item types
        config.set("item-types.allow-ores", allowOres);
        config.set("item-types.allow-weapons", allowWeapons);
        config.set("item-types.allow-armor", allowArmor);
        config.set("item-types.allow-orbs", allowOrbs);
        config.set("item-types.orb-value", orbValue);

        // Ore tiers
        ConfigurationSection oreSection = config.createSection("ore-tiers");
        for (Map.Entry<String, Integer> entry : oreTiers.entrySet()) {
            oreSection.set(entry.getKey(), entry.getValue());
        }

        // Material tiers
        ConfigurationSection materialSection = config.createSection("material-tiers");
        for (Map.Entry<String, Integer> entry : materialTiers.entrySet()) {
            materialSection.set(entry.getKey(), entry.getValue());
        }

        // Rarity multipliers
        ConfigurationSection raritySection = config.createSection("rarity-multipliers");
        for (Map.Entry<String, Double> entry : rarityMultipliers.entrySet()) {
            raritySection.set(entry.getKey(), entry.getValue());
        }

        // Add comments
        config.setComments("general", java.util.Arrays.asList(
                "General merchant system settings",
                "enabled: Whether the merchant system is active",
                "debug-mode: Enable debug logging for troubleshooting",
                "update-interval-ticks: How often to update the value display (20 = 1 second)"
        ));

        config.setComments("multipliers", java.util.Arrays.asList(
                "Value calculation multipliers",
                "donor-bonus: Multiplier for donors (1.2 = 20% bonus)",
                "staff-bonus: Multiplier for staff members",
                "ore-base-value: Base value for ore calculations (linear: base * tier)",
                "ore-multiplier: Additional multiplier applied to ore values (UNUSED in current version)",
                "weapon-armor-base: Base multiplier for weapons and armor (linear: base * tier)",
                "rarity-bonus-reduction: How much to reduce rarity bonus (0.25 = 25% reduction)",
                "random-variation-min/max: Random variation range for item values (deterministic per item)"
        ));

        config.setComments("item-types", java.util.Arrays.asList(
                "Control which item types can be traded",
                "Set to false to disable trading of specific item categories"
        ));
    }

    /**
     * Load values from configuration
     */
    private void loadValues() {
        // General settings
        enabled = config.getBoolean("general.enabled", enabled);
        debugMode = config.getBoolean("general.debug-mode", debugMode);
        updateIntervalTicks = config.getInt("general.update-interval-ticks", updateIntervalTicks);

        // Multipliers
        donorMultiplier = config.getDouble("multipliers.donor-bonus", donorMultiplier);
        staffMultiplier = config.getDouble("multipliers.staff-bonus", staffMultiplier);
        baseOreValue = config.getDouble("multipliers.ore-base-value", baseOreValue);
        oreMultiplier = config.getDouble("multipliers.ore-multiplier", oreMultiplier);
        weaponArmorBaseMultiplier = config.getDouble("multipliers.weapon-armor-base", weaponArmorBaseMultiplier);
        rarityBonusReduction = config.getDouble("multipliers.rarity-bonus-reduction", rarityBonusReduction);
        randomVariationMin = config.getDouble("multipliers.random-variation-min", randomVariationMin);
        randomVariationMax = config.getDouble("multipliers.random-variation-max", randomVariationMax);

        // Item types
        allowOres = config.getBoolean("item-types.allow-ores", allowOres);
        allowWeapons = config.getBoolean("item-types.allow-weapons", allowWeapons);
        allowArmor = config.getBoolean("item-types.allow-armor", allowArmor);
        allowOrbs = config.getBoolean("item-types.allow-orbs", allowOrbs);
        orbValue = config.getInt("item-types.orb-value", orbValue);

        // Load ore tiers
        ConfigurationSection oreSection = config.getConfigurationSection("ore-tiers");
        if (oreSection != null) {
            oreTiers.clear();
            for (String key : oreSection.getKeys(false)) {
                oreTiers.put(key, oreSection.getInt(key));
            }
        }

        // Load material tiers
        ConfigurationSection materialSection = config.getConfigurationSection("material-tiers");
        if (materialSection != null) {
            materialTiers.clear();
            for (String key : materialSection.getKeys(false)) {
                materialTiers.put(key, materialSection.getInt(key));
            }
        }

        // Load rarity multipliers
        ConfigurationSection raritySection = config.getConfigurationSection("rarity-multipliers");
        if (raritySection != null) {
            rarityMultipliers.clear();
            for (String key : raritySection.getKeys(false)) {
                rarityMultipliers.put(key, raritySection.getDouble(key));
            }
        }

        if (debugMode) {
            logger.info("Merchant config loaded with debug mode enabled");
            logger.info("Donor multiplier: " + donorMultiplier);
            logger.info("Staff multiplier: " + staffMultiplier);
            logger.info("Update interval: " + updateIntervalTicks + " ticks");
            logger.info("Ore base value: " + baseOreValue + " (per tier)");
            logger.info("Weapon/Armor base: " + weaponArmorBaseMultiplier + " (per tier)");
            logger.info("Price ranges - Ores: " + (int)(baseOreValue * 1) + "-" + (int)(baseOreValue * 10) + " gems per ore");
            logger.info("Price ranges - Items: " + (int)(weaponArmorBaseMultiplier * 10) + "-" + (int)(weaponArmorBaseMultiplier * 60) + " gems (before rarity/variation)");
            logger.info("Loaded " + oreTiers.size() + " ore tiers");
            logger.info("Loaded " + materialTiers.size() + " material tiers");
            logger.info("Loaded " + rarityMultipliers.size() + " rarity multipliers");
        }
    }

    /**
     * Save configuration to file
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save merchant configuration", e);
        }
    }

    /**
     * Reload configuration from file
     */
    public void reloadConfig() {
        loadConfig();
    }

    // Getter methods for configuration values

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public double getDonorMultiplier() {
        return donorMultiplier;
    }

    public double getStaffMultiplier() {
        return staffMultiplier;
    }

    public int getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public boolean isOresAllowed() {
        return allowOres;
    }

    public boolean isWeaponsAllowed() {
        return allowWeapons;
    }

    public boolean isArmorAllowed() {
        return allowArmor;
    }

    public boolean isOrbsAllowed() {
        return allowOrbs;
    }

    public double getBaseOreValue() {
        return baseOreValue;
    }

    public double getOreMultiplier() {
        return oreMultiplier;
    }

    public int getOrbValue() {
        return orbValue;
    }

    public double getWeaponArmorBaseMultiplier() {
        return weaponArmorBaseMultiplier;
    }

    public double getRarityBonusReduction() {
        return rarityBonusReduction;
    }

    public double getRandomVariationMin() {
        return randomVariationMin;
    }

    public double getRandomVariationMax() {
        return randomVariationMax;
    }

    public int getOreTier(String materialName) {
        return oreTiers.getOrDefault(materialName, 0);
    }

    public int getMaterialTier(String materialType) {
        return materialTiers.getOrDefault(materialType, 10);
    }

    public double getRarityMultiplier(String rarity) {
        return rarityMultipliers.getOrDefault(rarity.toUpperCase(), 1.0);
    }

    // Setter methods for runtime configuration changes

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        config.set("general.enabled", enabled);
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        config.set("general.debug-mode", debugMode);
    }

    public void setDonorMultiplier(double multiplier) {
        this.donorMultiplier = multiplier;
        config.set("multipliers.donor-bonus", multiplier);
    }

    public void setStaffMultiplier(double multiplier) {
        this.staffMultiplier = multiplier;
        config.set("multipliers.staff-bonus", multiplier);
    }

    public void setOrbValue(int value) {
        this.orbValue = value;
        config.set("item-types.orb-value", value);
    }

    /**
     * Get a formatted string of current configuration values
     */
    public String getConfigSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Merchant Configuration Summary:\n");
        summary.append("  Enabled: ").append(enabled).append("\n");
        summary.append("  Debug Mode: ").append(debugMode).append("\n");
        summary.append("  Donor Multiplier: ").append(donorMultiplier).append("\n");
        summary.append("  Staff Multiplier: ").append(staffMultiplier).append("\n");
        summary.append("  Update Interval: ").append(updateIntervalTicks).append(" ticks\n");
        summary.append("  Allow Ores: ").append(allowOres).append("\n");
        summary.append("  Allow Weapons: ").append(allowWeapons).append("\n");
        summary.append("  Allow Armor: ").append(allowArmor).append("\n");
        summary.append("  Allow Orbs: ").append(allowOrbs).append("\n");
        summary.append("  Orb Value: ").append(orbValue).append(" gems\n");
        summary.append("  Loaded Ore Tiers: ").append(oreTiers.size()).append("\n");
        summary.append("  Loaded Material Tiers: ").append(materialTiers.size()).append("\n");
        summary.append("  Loaded Rarity Multipliers: ").append(rarityMultipliers.size());

        return summary.toString();
    }

    /**
     * Validate configuration values and fix any invalid ones
     */
    public void validateAndFixConfig() {
        boolean needsSave = false;

        // Ensure multipliers are reasonable
        if (donorMultiplier < 1.0 || donorMultiplier > 5.0) {
            logger.warning("Invalid donor multiplier: " + donorMultiplier + ", resetting to 1.2");
            donorMultiplier = 1.2;
            needsSave = true;
        }

        if (staffMultiplier < 1.0 || staffMultiplier > 5.0) {
            logger.warning("Invalid staff multiplier: " + staffMultiplier + ", resetting to 1.2");
            staffMultiplier = 1.2;
            needsSave = true;
        }

        if (updateIntervalTicks < 10 || updateIntervalTicks > 200) {
            logger.warning("Invalid update interval: " + updateIntervalTicks + ", resetting to 20");
            updateIntervalTicks = 20;
            needsSave = true;
        }

        if (orbValue < 0) {
            logger.warning("Invalid orb value: " + orbValue + ", resetting to 500");
            orbValue = 500;
            needsSave = true;
        }

        // Validate random variation ranges
        if (randomVariationMin < 0.0 || randomVariationMin > 0.5) {
            logger.warning("Invalid random variation min: " + randomVariationMin + ", resetting to 0.05");
            randomVariationMin = 0.05;
            needsSave = true;
        }

        if (randomVariationMax < randomVariationMin || randomVariationMax > 1.0) {
            logger.warning("Invalid random variation max: " + randomVariationMax + ", resetting to 0.15");
            randomVariationMax = 0.15;
            needsSave = true;
        }

        if (needsSave) {
            setDefaultValues();
            saveConfig();
            logger.info("Fixed invalid configuration values");
        }
    }
}
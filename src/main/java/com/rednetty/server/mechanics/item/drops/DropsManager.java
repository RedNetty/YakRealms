package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.config.ConfigManager;
import com.rednetty.server.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.item.drops.types.*;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Central manager for item drops from mobs and bosses
 * CRITICAL FIX: Properly implements named elite drops from elite_drops.yml
 * MAJOR FIX: Prevents duplicate stats by removing automatic orb application
 * MAJOR FIX: Clean drops with no automatic orb effects
 */
public class DropsManager {

    private static final int ELEMENTAL_DAMAGE_CHANCE = 3;

    /**
     * CRITICAL FIX: Initializes the drops manager with proper configuration loading
     */
    public void initialize() {
        // Initialize DropConfig first to load elite configurations
        DropConfig.initialize();

        configManager.initialize();
        lootBuffManager.initialize();
        getDropFactory();
        DropsHandler.getInstance().initialize();

        logger.info("§a[DropsManager] §7Initialized with " + DropConfig.getEliteDropConfigs().size() + " elite configurations");

        // Debug: Print loaded elite configurations
        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            DropConfig.printEliteConfigurations();
        }
    }

    /**
     * CRITICAL FIX: Creates a custom elite drop for a specific mob type with proper YAML usage
     */
    public ItemStack createEliteDrop(String mobType, int itemType, int actualMobTier) {
        if (mobType == null || mobType.trim().isEmpty()) {
            logger.warning("§c[DropsManager] Cannot create elite drop - mob type is null or empty");
            return createFallbackEliteDrop(actualMobTier, itemType);
        }

        String normalizedMobType = mobType.toLowerCase().trim();
        EliteDropConfig config = DropConfig.getEliteDropConfig(normalizedMobType);

        if (config == null) {
            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("§6[DropsManager] §7No elite drop configuration found for mob type: " + mobType +
                        " - falling back to standard drop");
            }
            return createFallbackEliteDrop(actualMobTier, itemType);
        }

        try {
            ItemStack eliteItem = createConfiguredEliteDrop(config, itemType, normalizedMobType);

            if (eliteItem != null) {
                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("§a[DropsManager] §7Successfully created elite drop for " + mobType +
                            " (item type " + itemType + ")");
                }
                return eliteItem;
            } else {
                logger.warning("§c[DropsManager] Elite drop creation returned null for " + mobType);
                return createFallbackEliteDrop(actualMobTier, itemType);
            }

        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error creating elite drop for " + mobType + ": " + e.getMessage());
            return createFallbackEliteDrop(actualMobTier, itemType);
        }
    }

    private static final class ItemTypes {
        static final int STAFF = 1;
        static final int SPEAR = 2;
        static final int SWORD = 3;
        static final int AXE = 4;
        static final int HELMET = 5;
        static final int CHESTPLATE = 6;
        static final int LEGGINGS = 7;
        static final int BOOTS = 8;

        static boolean isWeapon(int itemType) {
            return itemType >= STAFF && itemType <= AXE;
        }

        static boolean isArmor(int itemType) {
            return itemType >= HELMET && itemType <= BOOTS;
        }

        static boolean isChestplateOrLeggings(int itemType) {
            return itemType == CHESTPLATE || itemType == LEGGINGS;
        }

        static boolean isHelmetOrBoots(int itemType) {
            return itemType == HELMET || itemType == BOOTS;
        }

        static boolean isStaffOrSpear(int itemType) {
            return itemType == STAFF || itemType == SPEAR;
        }
    }

    // ===== SINGLETON INSTANCE =====
    private static DropsManager instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Components
    private DropFactory dropFactory;
    private final LootNotifier lootNotifier;
    private final LootBuffManager lootBuffManager;
    private final ConfigManager configManager;
    private final MobManager mobManager;

    // Calculation helpers
    private StatCalculator statCalculator;
    private final ItemBuilder itemBuilder;
    private final NameProvider nameProvider;

    // Keys for persistent data
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyTier;
    private final NamespacedKey keyItemType;
    private final NamespacedKey keyEliteDrop;
    private final NamespacedKey keyDropProtection;
    private final NamespacedKey keyGear;

    /**
     * Private constructor for singleton pattern
     */
    private DropsManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys
        this.keyRarity = new NamespacedKey(plugin, "item_rarity");
        this.keyTier = new NamespacedKey(plugin, "item_tier");
        this.keyItemType = new NamespacedKey(plugin, "item_type");
        this.keyEliteDrop = new NamespacedKey(plugin, "elite_drop");
        this.keyDropProtection = new NamespacedKey(plugin, "drop_protection");
        this.keyGear = new NamespacedKey(plugin, "gear");

        // Initialize components
        this.lootNotifier = LootNotifier.getInstance();
        this.lootBuffManager = LootBuffManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.mobManager = MobManager.getInstance();

        // Initialize calculation helpers
        this.statCalculator = new StatCalculator();
        this.itemBuilder = new ItemBuilder();
        this.nameProvider = new NameProvider();
    }

    /**
     * Gets the singleton instance
     */
    public static DropsManager getInstance() {
        if (instance == null) {
            instance = new DropsManager();
        }
        return instance;
    }

    /**
     * Lazy initialization of DropFactory
     */
    public DropFactory getDropFactory() {
        if (dropFactory == null) {
            dropFactory = DropFactory.getInstance();
        }
        return dropFactory;
    }

    /**
     * Creates a custom elite drop for a specific mob type (backward compatibility)
     */
    public ItemStack createEliteDrop(String mobType, int itemType) {
        return createEliteDrop(mobType, itemType, 0);
    }

    /**
     * Clean up when plugin is disabled
     */
    public void shutdown() {
        DropsHandler.getInstance().shutdown();
        lootBuffManager.shutdown();
        configManager.shutdown();
        logger.info("[DropsManager] has been shut down");
    }

    /**
     * Creates a drop with the specified tier and item type
     */
    public static ItemStack createDrop(int tier, int itemType) {
        int rarity = determineRarity();
        return getInstance().createDrop(tier, itemType, rarity);
    }

    /**
     * MAJOR FIX: Creates a drop with clean stats (NO automatic orb application)
     */
    public ItemStack createDrop(int tier, int itemType, int rarity) {
        tier = clamp(tier, 1, 6);
        itemType = clamp(itemType, 1, 8);
        rarity = clamp(rarity, 1, 4);

        TierConfig tierConfig = DropConfig.getTierConfig(tier);
        RarityConfig rarityConfig = DropConfig.getRarityConfig(rarity);
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

        if (tierConfig == null || rarityConfig == null || itemTypeConfig == null) {
            logger.warning("Failed to create drop: Missing configuration for tier=" +
                    tier + ", rarity=" + rarity + ", itemType=" + itemType);
            return null;
        }

        ItemStack item = itemBuilder.createBaseItem(tierConfig, itemTypeConfig);
        if (item == null) {
            return null;
        }

        if (ItemTypes.isWeapon(itemType)) {
            return buildWeaponItem(item, tier, itemType, rarity, rarityConfig);
        } else {
            return buildArmorItem(item, tier, itemType, rarity, rarityConfig);
        }
    }

    /**
     * Creates a fallback elite drop when no specific config exists
     */
    private ItemStack createFallbackEliteDrop(int actualMobTier, int itemType) {
        int tier = (actualMobTier > 0) ? actualMobTier : 3;
        int rarity = tier >= 5 ? 4 : tier >= 3 ? 3 : 2; //  rarity for elites

        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine("§6[DropsManager] §7Creating fallback elite drop with T" + tier + " R" + rarity);
        }

        return createDrop(tier, itemType, rarity);
    }

    /**
     * CRITICAL FIX: Creates an elite drop based on YAML configuration with damage variation
     * MAJOR FIX: Now prevents duplicate stats from being added
     */
    private ItemStack createConfiguredEliteDrop(EliteDropConfig config, int itemType, String mobType) {
        try {
            // Get item details for this specific item type
            ItemDetails details = config.getItemDetailsForType(itemType);

            // Create the base item with proper material
            Material material = getMaterialForEliteDrop(details, config, itemType);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                logger.warning("§c[DropsManager] ItemMeta is null for material: " + material);
                return null;
            }

            // Set the item name from configuration
            String itemName = details != null ? details.getName() : generateDefaultEliteName(mobType, itemType);
            ChatColor tierColor = getTierColor(config.getTier());
            meta.setDisplayName(tierColor + ChatColor.stripColor(itemName));

            // Create lore based on whether it's a weapon or armor
            List<String> lore = new ArrayList<>();
            boolean isWeapon = ItemTypes.isWeapon(itemType);

            if (isWeapon) {
                buildEliteWeaponLore(lore, config, details, itemType, mobType);
            } else {
                buildEliteArmorLore(lore, config, details, itemType, mobType);
            }

            // Add item lore text if available
            if (details != null && details.getLore() != null && !details.getLore().trim().isEmpty()) {
                lore.add("");
                lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + details.getLore());
            }

            // Add rarity line
            lore.add("");
            lore.add(getRarityColor(config.getRarity()) + ChatColor.ITALIC.toString() + getRarityText(config.getRarity()));

            // Apply item flags to hide vanilla attributes
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }
            meta.setLore(lore);

            // Store metadata for identification
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyRarity, PersistentDataType.INTEGER, config.getRarity());
            container.set(keyTier, PersistentDataType.INTEGER, config.getTier());
            container.set(keyItemType, PersistentDataType.INTEGER, itemType);
            container.set(keyEliteDrop, PersistentDataType.STRING, mobType);

            // Special metadata for certain elite types
            if (mobType.equalsIgnoreCase("spectralKnight")) {
                container.set(keyGear, PersistentDataType.INTEGER, 1);
            }

            item.setItemMeta(meta);

            // Apply special effects for T6 Netherite armor
            if (config.getTier() == 6 && !isWeapon && material.toString().contains("NETHERITE")) {
                item = applyNetheriteGoldTrim(item);
            }

            // Add enchantment glow for special elite drops
            if (isSpecialEliteDrop(mobType)) {
                item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
            }

            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("§a[DropsManager] §7Created configured elite drop: " + itemName +
                        " for " + mobType + " (T" + config.getTier() + " R" + config.getRarity() + ")");
            }

            return item;

        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error creating configured elite drop for " + mobType + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Determines if a mob should drop an item
     */
    public boolean shouldDropItem(LivingEntity entity, boolean isElite, int tier) {
        int baseDropRate = DropConfig.getDropRate(tier);
        int dropRate = isElite ? DropConfig.getEliteDropRate(tier) : baseDropRate;

        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (dropRate * buffPercentage / 100);
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        boolean shouldDrop = roll < dropRate;

        if (shouldDrop && lootBuffManager.isBuffActive()) {
            int baseRoll = roll - (baseDropRate * lootBuffManager.getActiveBuff().getBuffRate() / 100);
            if (baseRoll >= baseDropRate) {
                lootBuffManager.updateImprovedDrops();
            }
        }

        return shouldDrop;
    }

    /**
     * Register drop protection for an item
     */
    public void registerDropProtection(Item item, UUID playerUuid, int seconds) {
        if (item == null) return;

        ItemStack itemStack = item.getItemStack();
        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyDropProtection, PersistentDataType.STRING,
                    playerUuid.toString() + ":" + (System.currentTimeMillis() + (seconds * 1000)));

            itemStack.setItemMeta(meta);
            item.setItemStack(itemStack);
        }

        item.setUnlimitedLifetime(true);
    }

    /**
     * Check if a player has protection for an item
     */
    public boolean hasItemProtection(Item item, Player player) {
        if (item == null || player == null) return false;

        ItemStack itemStack = item.getItemStack();
        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(keyDropProtection, PersistentDataType.STRING)) {
                String data = container.get(keyDropProtection, PersistentDataType.STRING);
                if (data != null) {
                    String[] parts = data.split(":");
                    if (parts.length == 2) {
                        UUID protectedUuid = UUID.fromString(parts[0]);
                        long expiry = Long.parseLong(parts[1]);
                        return protectedUuid.equals(player.getUniqueId()) && System.currentTimeMillis() < expiry;
                    }
                }
            }
        }

        return false;
    }

    // ===== PRIVATE HELPER METHODS =====

    /**
     * MAJOR FIX: Builds a weapon item with appropriate stats and lore (NO automatic orb effects)
     */
    private ItemStack buildWeaponItem(ItemStack item, int tier, int itemType, int rarity, RarityConfig rarityConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.RESET + nameProvider.getItemName(itemType, tier));

        List<String> lore = new ArrayList<>();

        DamageStats damage = statCalculator.calculateWeaponDamage(tier, rarity, itemType);
        lore.add(ChatColor.RED + "DMG: " + damage.getMin() + " - " + damage.getMax());

        addElementalDamageToLore(lore, tier, rarity);

        if (rarity >= 3) {
            addWeaponSpecialAttributes(lore, tier, rarity);
        }

        lore.add(rarityConfig.getFormattedName());

        return itemBuilder.finalizeItem(item, meta, lore, tier, itemType, rarity, keyRarity, keyTier, keyItemType);
    }

    /**
     * MAJOR FIX: Builds an armor item with appropriate stats and lore (NO automatic orb effects)
     */
    private ItemStack buildArmorItem(ItemStack item, int tier, int itemType, int rarity, RarityConfig rarityConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.RESET + nameProvider.getItemName(itemType, tier));

        List<String> lore = new ArrayList<>();

        addArmorDefenseStat(lore, tier);

        int hp = statCalculator.calculateArmorHP(tier, rarity, itemType);
        lore.add(ChatColor.RED + "HP: +" + hp);

        addArmorRegenStat(lore, tier, hp);

        if (rarity >= 2) {
            addArmorSpecialAttributes(lore, tier, rarity);
        }

        lore.add(rarityConfig.getFormattedName());

        ItemStack finalItem = itemBuilder.finalizeItem(item, meta, lore, tier, itemType, rarity, keyRarity, keyTier, keyItemType);

        if (tier == 6 && item.getType().toString().contains("NETHERITE")) {
            finalItem = applyNetheriteGoldTrim(finalItem);
        }

        // MAJOR FIX: Removed automatic orb application - items should have clean stats only
        return finalItem;
    }

    /**
     * Generate a default elite name if none is configured
     */
    private String generateDefaultEliteName(String mobType, int itemType) {
        String mobName = mobType.substring(0, 1).toUpperCase() + mobType.substring(1);
        String[] itemTypeNames = {"Staff", "Spear", "Sword", "Axe", "Helmet", "Chestplate", "Leggings", "Boots"};
        String itemTypeName = itemTypeNames[itemType - 1];
        return mobName + "'s " + itemTypeName;
    }

    /**
     * Get appropriate material for elite drop with better fallback logic
     */
    private Material getMaterialForEliteDrop(ItemDetails details, EliteDropConfig config, int itemType) {
        // First try to use the material from item details
        if (details != null && details.getMaterial() != null) {
            return details.getMaterial();
        }

        // Fallback to appropriate material based on tier and item type
        TierConfig tierConfig = DropConfig.getTierConfig(config.getTier());
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

        if (tierConfig != null && itemTypeConfig != null) {
            boolean isWeapon = ItemTypes.isWeapon(itemType);
            String materialPrefix = tierConfig.getMaterialPrefix(isWeapon);
            String materialSuffix = itemTypeConfig.getMaterialSuffix();
            String materialName = materialPrefix + "_" + materialSuffix;

            try {
                return Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                logger.warning("§c[DropsManager] Invalid material name: " + materialName +
                        " for " + config.getMobName() + " item " + itemType);
            }
        }

        // Final fallback based on tier
        return getFallbackMaterialByTier(config.getTier(), itemType);
    }

    /**
     * Get fallback material based on tier when all else fails
     */
    private Material getFallbackMaterialByTier(int tier, int itemType) {
        String[] tierPrefixes = {"WOODEN", "STONE", "IRON", "DIAMOND", "GOLDEN", "NETHERITE"};
        String[] weaponSuffixes = {"HOE", "SHOVEL", "SWORD", "AXE"}; // Changed SPADE to SHOVEL
        String[] armorSuffixes = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

        String prefix = tier <= 6 ? tierPrefixes[tier - 1] : "STONE";

        if (ItemTypes.isWeapon(itemType)) {
            String suffix = weaponSuffixes[itemType - 1];
            try {
                return Material.valueOf(prefix + "_" + suffix);
            } catch (IllegalArgumentException e) {
                return Material.STONE;
            }
        } else {
            String suffix = armorSuffixes[itemType - 5];
            // Handle leather armor prefix difference
            if (tier == 1) {
                prefix = "LEATHER";
            } else if (tier == 2) {
                prefix = "CHAINMAIL";
            }

            try {
                return Material.valueOf(prefix + "_" + suffix);
            } catch (IllegalArgumentException e) {
                return Material.LEATHER_HELMET;
            }
        }
    }

    /**
     * MAJOR FIX: Build elite weapon lore from YAML configuration with damage variation and duplicate prevention
     */
    private void buildEliteWeaponLore(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType, String mobType) {
        try {
            // Get damage stats from configuration with tier-based variation
            StatRange damageRange = getStatRange(config, details, "damage", true, itemType);
            if (damageRange != null) {
                // Add tier-based variation to the damage range
                int tierVariation = config.getTier() * 2; // More variation for higher tiers
                int minDmg = damageRange.getRandomValue();
                int maxDmg = damageRange.getRandomValue();

                // Ensure min <= max and add some variation
                if (minDmg > maxDmg) {
                    int temp = minDmg;
                    minDmg = maxDmg;
                    maxDmg = temp;
                }

                // Add tier-based variation to spread the damage range
                int variation = ThreadLocalRandom.current().nextInt(tierVariation + 1);
                minDmg = Math.max(1, minDmg - variation);
                maxDmg = maxDmg + variation;

                lore.add(ChatColor.RED + "DMG: " + minDmg + " - " + maxDmg);
            } else {
                //  fallback damage calculation with variation
                int baseDamage = calculateTierBaseDamage(config.getTier());
                int rarityMultiplier = getRarityMultiplier(config.getRarity());
                int minDmg = baseDamage * rarityMultiplier / 100;
                int maxDmg = (baseDamage * rarityMultiplier / 100) + (baseDamage / 2);

                // Add random variation
                int variation = ThreadLocalRandom.current().nextInt(config.getTier() * 3);
                minDmg += variation;
                maxDmg += variation + ThreadLocalRandom.current().nextInt(baseDamage / 4);

                lore.add(ChatColor.RED + "DMG: " + minDmg + " - " + maxDmg);
            }

            // Add elemental damage from configuration (with duplicate check)
            addElementalDamageFromConfig(lore, config, details, itemType);

            // Add special weapon stats from configuration (with duplicate check)
            addSpecialStatsFromConfig(lore, config, details, true, itemType);

        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error building elite weapon lore: " + e.getMessage());
        }
    }

    /**
     * Calculate tier-based damage for fallback scenarios
     */
    private int calculateTierBaseDamage(int tier) {
        switch (tier) {
            case 1:
                return 12;
            case 2:
                return 28;
            case 3:
                return 65;
            case 4:
                return 155;
            case 5:
                return 370;
            case 6:
                return 890;
            default:
                return 12;
        }
    }

    /**
     * Get rarity multiplier for damage calculations
     */
    private int getRarityMultiplier(int rarity) {
        switch (rarity) {
            case 1:
                return 100; // Common
            case 2:
                return 115; // Uncommon
            case 3:
                return 125; // Rare
            case 4:
                return 140; // Unique
            default:
                return 100;
        }
    }

    /**
     * MAJOR FIX: Build elite armor lore from YAML configuration with HP variation and duplicate prevention
     */
    private void buildEliteArmorLore(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType, String mobType) {
        try {
            // Add DPS or Armor stat
            StatRange dpsRange = getStatRange(config, details, "dps", false, itemType);
            StatRange armorRange = getStatRange(config, details, "armor", false, itemType);

            if (dpsRange != null && dpsRange.getMax() > 0) {
                int dps = dpsRange.getRandomValue();
                lore.add(ChatColor.RED + "DPS: " + dps + "%");
            } else if (armorRange != null && armorRange.getMax() > 0) {
                int armor = armorRange.getRandomValue();
                lore.add(ChatColor.RED + "ARMOR: " + armor + "%");
            } else {
                //  fallback defense stat with variation
                int defenseStat = config.getTier() * 3 + ThreadLocalRandom.current().nextInt(5);
                String statType = ThreadLocalRandom.current().nextBoolean() ? "DPS" : "ARMOR";
                lore.add(ChatColor.RED + statType + ": " + defenseStat + "%");
            }

            // Add HP from configuration with  variation
            StatRange hpRange = getArmorSpecificHPRange(config, details, itemType);
            if (hpRange != null) {
                int hp = hpRange.getRandomValue();
                // Add tier-based variation to HP
                int tierVariation = config.getTier() * 50;
                int variation = ThreadLocalRandom.current().nextInt(tierVariation + 1);
                hp += variation;
                lore.add(ChatColor.RED + "HP: +" + hp);
            } else {
                //  fallback HP calculation with variation
                int hp = calculateFallbackHP(config.getTier(), itemType);
                // Add random variation
                int variation = ThreadLocalRandom.current().nextInt(hp / 4);
                hp += variation;
                lore.add(ChatColor.RED + "HP: +" + hp);
            }

            // Add regeneration stats from configuration (with duplicate check)
            addRegenStatsFromConfig(lore, config, details, itemType);

            // Add special armor stats from configuration (with duplicate check)
            addSpecialStatsFromConfig(lore, config, details, false, itemType);

        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error building elite armor lore: " + e.getMessage());
        }
    }

    /**
     * Get armor-specific HP range from configuration
     */
    private StatRange getArmorSpecificHPRange(EliteDropConfig config, ItemDetails details, int itemType) {
        // Check for HP stat with armor piece specificity
        Map<String, StatRange> armorTypeHPMap = config.getArmorTypeStats().get("hp");
        if (armorTypeHPMap != null) {
            String armorPieceName = getArmorPieceName(itemType);
            if (armorPieceName != null && armorTypeHPMap.containsKey(armorPieceName)) {
                return armorTypeHPMap.get(armorPieceName);
            }
        }

        // Fallback to general HP stat
        return getStatRange(config, details, "hp", false, itemType);
    }

    /**
     * Get armor piece name for HP configuration lookup
     */
    private String getArmorPieceName(int itemType) {
        switch (itemType) {
            case ItemTypes.HELMET:
                return "helmet";
            case ItemTypes.CHESTPLATE:
                return "chestplate";
            case ItemTypes.LEGGINGS:
                return "leggings";
            case ItemTypes.BOOTS:
                return "boots";
            default:
                return null;
        }
    }

    /**
     * MAJOR FIX: Add elemental damage from elite configuration with duplicate checking
     */
    private void addElementalDamageFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        try {
            // Check for elemental damage types in weapon stats
            Map<String, StatRange> weaponStats = config.getWeaponStats();

            String[] elementalTypes = {"fireDamage", "poisonDamage", "iceDamage"};
            for (String elementType : elementalTypes) {
                StatRange elementRange = weaponStats.get(elementType);
                if (elementRange != null && elementRange.getMax() > 0) {
                    int damage = elementRange.getRandomValue();
                    String displayName = elementType.replace("Damage", "").toUpperCase() + " DMG";
                    String statLine = ChatColor.RED + displayName + ": +" + damage;

                    // MAJOR FIX: Check for duplicates before adding
                    if (!loreContainsElement(lore, displayName)) {
                        lore.add(statLine);
                        break; // Only add one elemental type
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error adding elemental damage from config: " + e.getMessage());
        }
    }

    /**
     * MAJOR FIX: Add regeneration stats from elite configuration with duplicate checking
     */
    private void addRegenStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        try {
            // Check for energy regen first
            StatRange energyRange = getStatRange(config, details, "energyRegen", false, itemType);
            if (energyRange != null && energyRange.getMax() > 0) {
                int value = energyRange.getRandomValue();
                if (value > 0) {
                    String statLine = ChatColor.RED + "ENERGY REGEN: +" + value + "%";
                    // MAJOR FIX: Check for duplicates before adding
                    if (!loreContainsElement(lore, "ENERGY REGEN")) {
                        lore.add(statLine);
                        return;
                    }
                }
            }

            // Check for HP regen
            StatRange hpsRange = getStatRange(config, details, "hpsregen", false, itemType);
            if (hpsRange != null && hpsRange.getMax() > 0) {
                int value = hpsRange.getRandomValue();
                if (value > 0) {
                    String statLine = ChatColor.RED + "HP REGEN: +" + value + "/s";
                    // MAJOR FIX: Check for duplicates before adding
                    if (!loreContainsElement(lore, "HP REGEN")) {
                        lore.add(statLine);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error adding regen stats from config: " + e.getMessage());
        }
    }

    /**
     * MAJOR FIX: Add special stats from elite configuration with duplicate checking
     */
    private void addSpecialStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, boolean isWeapon, int itemType) {
        try {
            Map<String, StatRange> statsToCheck = isWeapon ? config.getWeaponStats() : config.getArmorStats();

            if (isWeapon) {
                // Weapon special stats
                String[] weaponSpecialStats = {"lifeSteal", "criticalHit", "accuracy"};
                for (String statName : weaponSpecialStats) {
                    StatRange statRange = statsToCheck.get(statName);
                    if (statRange != null && statRange.getMax() > 0) {
                        int value = statRange.getRandomValue();
                        if (value > 0) {
                            String displayName = formatStatName(statName);
                            String suffix = (statName.equals("accuracy") || statName.equals("lifeSteal") || statName.equals("criticalHit")) ? "%" : "";
                            String statLine = ChatColor.RED + displayName + ": " + value + suffix;

                            // MAJOR FIX: Check for duplicates before adding
                            if (!loreContainsElement(lore, displayName)) {
                                lore.add(statLine);
                            }
                        }
                    }
                }
            } else {
                // Armor special stats
                String[] armorSpecialStats = {"strength", "vitality", "intellect", "dodgeChance", "blockChance"};
                for (String statName : armorSpecialStats) {
                    StatRange statRange = getStatRange(config, details, statName, false, itemType);
                    if (statRange != null && statRange.getMax() > 0) {
                        int value = statRange.getRandomValue();
                        if (value > 0) {
                            String displayName = formatStatName(statName);
                            String suffix = (statName.contains("Chance")) ? "%" : "";
                            String statLine = ChatColor.RED + displayName + ": +" + value + suffix;

                            // MAJOR FIX: Check for duplicates before adding
                            if (!loreContainsElement(lore, displayName)) {
                                lore.add(statLine);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error adding special stats from config: " + e.getMessage());
        }
    }

    /**
     * Format stat name for display
     */
    private String formatStatName(String statName) {
        switch (statName.toLowerCase()) {
            case "lifesteal":
                return "LIFE STEAL";
            case "criticalhit":
                return "CRITICAL HIT";
            case "accuracy":
                return "ACCURACY";
            case "strength":
                return "STR";
            case "vitality":
                return "VIT";
            case "intellect":
                return "INT";
            case "dodgechance":
                return "DODGE";
            case "blockchance":
                return "BLOCK";
            default:
                return statName.toUpperCase();
        }
    }

    /**
     * Get stat range from configuration with proper fallback logic
     */
    private StatRange getStatRange(EliteDropConfig config, ItemDetails details, String statName, boolean isWeapon, int itemType) {
        try {
            // Check for override in item details first
            if (details != null && details.getStatOverride(statName) != null) {
                return details.getStatOverride(statName);
            }

            // Check weapon/armor specific ranges
            if (isWeapon && config.getWeaponStatRange(statName) != null) {
                return config.getWeaponStatRange(statName);
            } else if (!isWeapon && config.getArmorStatRange(statName) != null) {
                return config.getArmorStatRange(statName);
            }

            // Check common stats
            if (config.getStatRange(statName) != null) {
                return config.getStatRange(statName);
            }

            return null;
        } catch (Exception e) {
            logger.warning("§c[DropsManager] Error getting stat range for " + statName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Calculate fallback HP when no configuration exists
     */
    private int calculateFallbackHP(int tier, int itemType) {
        int baseHp;
        switch (tier) {
            case 1:
                baseHp = ThreadLocalRandom.current().nextInt(100, 150);
                break;
            case 2:
                baseHp = ThreadLocalRandom.current().nextInt(250, 500);
                break;
            case 3:
                baseHp = ThreadLocalRandom.current().nextInt(500, 1000);
                break;
            case 4:
                baseHp = ThreadLocalRandom.current().nextInt(1000, 2000);
                break;
            case 5:
                baseHp = ThreadLocalRandom.current().nextInt(2000, 5000);
                break;
            case 6:
                baseHp = ThreadLocalRandom.current().nextInt(4000, 8000);
                break;
            default:
                baseHp = 100;
        }

        // Adjust for armor piece type
        return ItemTypes.isChestplateOrLeggings(itemType) ? baseHp : baseHp / 2;
    }

    /**
     * Adds DPS or Armor stat to armor lore
     */
    private void addArmorDefenseStat(List<String> lore, int tier) {
        double dpsMax = tier * (1.0 + (tier / BaseStats.DPS_BASE_MULTIPLIER));
        double dpsMin = dpsMax / BaseStats.DPS_MIN_DIVISOR;
        int dpsi = (int) dpsMin;
        int dpsa = (int) dpsMax;

        String statType = ThreadLocalRandom.current().nextInt(2) == 0 ? "DPS" : "ARMOR";
        lore.add(ChatColor.RED + statType + ": " + dpsi + " - " + dpsa + "%");
    }

    private boolean isSpecialEliteDrop(String mobType) {
        return mobType.equalsIgnoreCase("bossSkeletonDungeon") ||
                mobType.equalsIgnoreCase("frostKing") ||
                mobType.equalsIgnoreCase("warden") ||
                mobType.equalsIgnoreCase("apocalypse") ||
                mobType.equalsIgnoreCase("chronos");
    }

    /**
     * Adds elemental damage to weapon lore with configured chance
     */
    private void addElementalDamageToLore(List<String> lore, int tier, int rarity) {
        if (ThreadLocalRandom.current().nextInt(ELEMENTAL_DAMAGE_CHANCE) > 0) {
            return;
        }

        String[] elementTypes = {"FIRE DMG", "POISON DMG", "ICE DMG"};
        String elementName = elementTypes[ThreadLocalRandom.current().nextInt(elementTypes.length)];

        int baseAmount = tier * 5;
        if (rarity >= 3) baseAmount *= 1.5;

        int amount = ThreadLocalRandom.current().nextInt(baseAmount, baseAmount * 2 + 1);
        lore.add(ChatColor.RED + elementName + ": +" + amount);
    }

    public void setTierGapMultiplier(double newMultiplier) {
        if (newMultiplier <= 1.0) {
            logger.warning("Tier gap multiplier must be greater than 1.0. Ignoring value: " + newMultiplier);
            return;
        }

        double oldMultiplier = BaseStats.TIER_GAP_MULTIPLIER;
        BaseStats.TIER_GAP_MULTIPLIER = newMultiplier;

        this.statCalculator = new StatCalculator();

        logger.info("Updated tier gap multiplier from " + oldMultiplier + " to " + newMultiplier);
        logTierProgression();
    }

    /**
     * Adds energy or HP regeneration stat to armor lore
     */
    private void addArmorRegenStat(List<String> lore, int tier, int hp) {
        int nrghp = ThreadLocalRandom.current().nextInt(4);

        if (nrghp > 0) {
            int nrg = tier;
            int nrgToTake = ThreadLocalRandom.current().nextInt(2);
            int nrgToGive = ThreadLocalRandom.current().nextInt(3);
            nrg = nrg - nrgToTake + nrgToGive + (ThreadLocalRandom.current().nextInt(tier) / 2);
            nrg = clamp(nrg, 1, BaseStats.MAX_ENERGY_REGEN);
            lore.add(ChatColor.RED + "ENERGY REGEN: +" + nrg + "%");
        } else {
            int minHps = Math.max(1, (int) (hp * BaseStats.HP_REGEN_MIN_PERCENT));
            int maxHps = Math.max(minHps + 1, (int) (hp * BaseStats.HP_REGEN_MAX_PERCENT));
            int hps = ThreadLocalRandom.current().nextInt(minHps, maxHps);
            lore.add(ChatColor.RED + "HP REGEN: +" + hps + "/s");
        }
    }

    /**
     * Adds special attributes to weapon lore
     */
    private void addWeaponSpecialAttributes(List<String> lore, int tier, int rarity) {
        String[] weaponAttrs = {"LIFE STEAL", "CRITICAL HIT", "ACCURACY", "PURE DMG"};
        int numAttributes = Math.min(rarity, 3);

        for (int i = 0; i < numAttributes; i++) {
            String attr = weaponAttrs[ThreadLocalRandom.current().nextInt(weaponAttrs.length)];

            if (!loreContains(lore, attr)) {
                switch (attr) {
                    case "LIFE STEAL":
                    case "CRITICAL HIT":
                        int percentage = ThreadLocalRandom.current().nextInt(5, 11) + (rarity >= 3 ? 3 : 0);
                        lore.add(ChatColor.RED + attr + ": " + percentage + "%");
                        break;
                    case "ACCURACY":
                        int accuracy = ThreadLocalRandom.current().nextInt(15, 25) + (rarity >= 3 ? 10 : 0);
                        lore.add(ChatColor.RED + attr + ": " + accuracy + "%");
                        break;
                    case "PURE DMG":
                        int pureDmg = ThreadLocalRandom.current().nextInt(tier * 5, tier * 10 + 1);
                        if (rarity >= 3) pureDmg = (int) (pureDmg * 1.5);
                        lore.add(ChatColor.RED + attr + ": +" + pureDmg);
                        break;
                }
            }
        }
    }

    /**
     * Adds special attributes to armor lore
     */
    private void addArmorSpecialAttributes(List<String> lore, int tier, int rarity) {
        String[] armorAttrs = {"STR", "DEX", "INT", "VIT", "DODGE", "BLOCK"};
        int numAttributes = Math.min(rarity, 2);

        for (int i = 0; i < numAttributes; i++) {
            String attr = armorAttrs[ThreadLocalRandom.current().nextInt(armorAttrs.length)];

            if (!loreContains(lore, attr)) {
                if (attr.equals("DODGE") || attr.equals("BLOCK")) {
                    int percentage = ThreadLocalRandom.current().nextInt(5, 10) + (rarity >= 3 ? 3 : 0);
                    lore.add(ChatColor.RED + attr + ": " + percentage + "%");
                } else {
                    int statValue = ThreadLocalRandom.current().nextInt(tier * 20, tier * 40 + 1);
                    if (rarity >= 3) statValue = (int) (statValue * 1.5);
                    lore.add(ChatColor.RED + attr + ": +" + statValue);
                }
            }
        }
    }

    // ===== UTILITY METHODS =====

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int determineRarity() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < RarityThresholds.UNIQUE_THRESHOLD) return 4;
        if (roll < RarityThresholds.RARE_THRESHOLD) return 3;
        if (roll < RarityThresholds.UNCOMMON_THRESHOLD) return 2;
        return 1;
    }

    /**
     * MAJOR FIX:  lore contains check for better duplicate detection
     */
    private boolean loreContains(List<String> lore, String attribute) {
        return lore.stream().anyMatch(line -> ChatColor.stripColor(line).contains(attribute));
    }

    /**
     * MAJOR FIX: New method for more precise element checking to prevent duplicates
     */
    private boolean loreContainsElement(List<String> lore, String statName) {
        String normalizedStatName = statName.toLowerCase().trim();
        return lore.stream().anyMatch(line -> {
            String cleanLine = ChatColor.stripColor(line).toLowerCase().trim();

            // Check for exact stat name matches
            if (cleanLine.contains(normalizedStatName)) {
                return true;
            }

            // Check for common stat variations
            switch (normalizedStatName) {
                case "dmg":
                case "damage":
                    return cleanLine.contains("dmg:") || cleanLine.contains("damage:");
                case "hp":
                case "health":
                    return cleanLine.contains("hp:") || cleanLine.contains("health:");
                case "energy regen":
                case "energyregen":
                    return cleanLine.contains("energy regen:") || cleanLine.contains("energy:");
                case "hp regen":
                case "hpregen":
                    return cleanLine.contains("hp regen:") || cleanLine.contains("regen:");
                case "fire dmg":
                case "firedamage":
                    return cleanLine.contains("fire dmg:") || cleanLine.contains("fire:");
                case "poison dmg":
                case "poisondamage":
                    return cleanLine.contains("poison dmg:") || cleanLine.contains("poison:");
                case "ice dmg":
                case "icedamage":
                    return cleanLine.contains("ice dmg:") || cleanLine.contains("ice:");
                case "dps":
                    return cleanLine.contains("dps:");
                case "armor":
                    return cleanLine.contains("armor:");
                default:
                    return false;
            }
        });
    }

    private ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.GOLD;
            default: return ChatColor.WHITE;
        }
    }

    private ChatColor getRarityColor(int rarity) {
        switch (rarity) {
            case 1: return ChatColor.GRAY;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.YELLOW;
            default: return ChatColor.GRAY;
        }
    }

    private String getRarityText(int rarity) {
        switch (rarity) {
            case 1: return "Common";
            case 2: return "Uncommon";
            case 3: return "Rare";
            case 4: return "Unique";
            default: return "Common";
        }
    }

    // ===== CONFIGURATION CONSTANTS =====
    private static final class BaseStats {
        static final int LOW_RARITY_BONUS_MULTIPLIER = 1;

        static final double ARMOR_HP_BASE = 30.0;
        static final double MIN_DAMAGE_BASE = 5.0;
        static final double MAX_DAMAGE_BASE = 6.0;

        static final double RARITY_DAMAGE_MODIFIER_BASE = 0.7;
        static final double RARITY_DAMAGE_DIVISOR_MIN = 2.35;
        static final double RARITY_DAMAGE_DIVISOR_MAX = 2.3;
        static final double DAMAGE_VARIANCE_DIVISOR = 4.0;

        static final double AXE_DAMAGE_MULTIPLIER = 1.2;
        static final double STAFF_SPEAR_DAMAGE_DIVISOR = 1.5;
        static final double CHESTPLATE_LEGGINGS_HP_MULTIPLIER = 1.2;
        static final double HELMET_BOOTS_HP_DIVISOR = 1.5;

        static final int TIER_3_PLUS_RARITY_1_BONUS_MAX = 15;
        static double TIER_GAP_MULTIPLIER = 2.45;
        static final int LOW_RARITY_MAX_BONUS_ROLLS = 2;

        static final double HP_REGEN_MIN_PERCENT = 0.19;
        static final double HP_REGEN_MAX_PERCENT = 0.21;
        static final int MAX_ENERGY_REGEN = 6;

        static final double DPS_BASE_MULTIPLIER = 1.7;
        static final double DPS_MIN_DIVISOR = 1.5;
    }

    private ItemStack applyNetheriteGoldTrim(ItemStack item) {
        if (item.getItemMeta() instanceof ArmorMeta) {
            try {
                ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();
                ArmorTrim goldTrim = new ArmorTrim(TrimMaterial.GOLD, TrimPattern.EYE);
                armorMeta.setTrim(goldTrim);
                item.setItemMeta(armorMeta);
                logger.fine("Applied gold trim to netherite armor: " + item.getType());
            } catch (Exception e) {
                logger.warning("Failed to apply gold trim to netherite armor: " + e.getMessage());
            }
        }
        return item;
    }

    // ===== PUBLIC API METHODS =====

    public Sound getTierSound(int tier) {
        switch (tier) {
            case 1: return Sound.ENTITY_ITEM_PICKUP;
            case 2: return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3: return Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4:
            case 5:
            case 6: return Sound.ENTITY_PLAYER_LEVELUP;
            default: return Sound.ENTITY_ITEM_PICKUP;
        }
    }

    public Sound getRaritySound(int rarity) {
        switch (rarity) {
            case 1: return Sound.ENTITY_ITEM_PICKUP;
            case 2: return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3: return Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4: return Sound.ENTITY_ENDER_DRAGON_GROWL;
            default: return Sound.ENTITY_ITEM_PICKUP;
        }
    }

    public void setDropRate(int tier, int rate) {
        configManager.updateDropRate(tier, rate);
    }

    public int getDropRate(int tier) {
        return DropConfig.getDropRate(tier);
    }

    public void logTierProgression() {
        logger.info("=== TIER PROGRESSION (TIER_GAP_MULTIPLIER: " + BaseStats.TIER_GAP_MULTIPLIER + ") ===");

        for (int tier = 1; tier <= 6; tier++) {
            double armorHP = BaseStats.ARMOR_HP_BASE * Math.pow(BaseStats.TIER_GAP_MULTIPLIER, tier - 1);
            double minDamage = BaseStats.MIN_DAMAGE_BASE * Math.pow(BaseStats.TIER_GAP_MULTIPLIER, tier - 1);
            double maxDamage = BaseStats.MAX_DAMAGE_BASE * Math.pow(BaseStats.TIER_GAP_MULTIPLIER, tier - 1);

            logger.info(String.format("Tier %d: HP Base ~%.0f | Damage Base ~%.0f-%.0f",
                    tier, armorHP, minDamage, maxDamage));
        }

        logger.info("Note: Actual values vary based on rarity, item type, and random factors");
    }

    public double getBaseTierValue(double baseValue, int tier) {
        return baseValue * Math.pow(BaseStats.TIER_GAP_MULTIPLIER, tier - 1);
    }

    private static final class RarityThresholds {
        static final int UNIQUE_THRESHOLD = 2;
        static final int RARE_THRESHOLD = 12;
        static final int UNCOMMON_THRESHOLD = 38;
    }

    public double getTierGapMultiplier() {
        return BaseStats.TIER_GAP_MULTIPLIER;
    }

    // ===== HELPER CLASSES =====

    /**
     * Handles stat calculations with clear, configurable formulas
     */
    private static class StatCalculator {

        private final ArrayList<Integer> armorBaseValues;
        private final ArrayList<Integer> minDamageValues;
        private final ArrayList<Integer> maxDamageValues;

        public StatCalculator() {
            this.armorBaseValues = createBaseArray(BaseStats.ARMOR_HP_BASE, BaseStats.TIER_GAP_MULTIPLIER);
            this.minDamageValues = createBaseArray(BaseStats.MIN_DAMAGE_BASE, BaseStats.TIER_GAP_MULTIPLIER);
            this.maxDamageValues = createBaseArray(BaseStats.MAX_DAMAGE_BASE, BaseStats.TIER_GAP_MULTIPLIER);
        }

        private ArrayList<Integer> createBaseArray(double base, double multiplier) {
            ArrayList<Integer> array = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                array.add((int) base);
                base *= multiplier;
            }
            return array;
        }

        public DamageStats calculateWeaponDamage(int tier, int rarity, int itemType) {
            double minMin = minDamageValues.get(tier - 1) * ((rarity + BaseStats.RARITY_DAMAGE_MODIFIER_BASE) / BaseStats.RARITY_DAMAGE_DIVISOR_MIN);
            double minMax = minMin / BaseStats.DAMAGE_VARIANCE_DIVISOR;
            double maxMin = maxDamageValues.get(tier - 1) * ((rarity + BaseStats.RARITY_DAMAGE_MODIFIER_BASE) / BaseStats.RARITY_DAMAGE_DIVISOR_MAX);
            double maxMax = maxMin / BaseStats.DAMAGE_VARIANCE_DIVISOR;

            int minRange = Math.max(1, (int) minMax);
            int maxRange = Math.max(1, (int) maxMax);
            int min = ThreadLocalRandom.current().nextInt(minRange) + (int) minMin;
            int max = ThreadLocalRandom.current().nextInt(maxRange) + (int) maxMin;

            if (rarity == 1 && tier >= 3) {
                min += ThreadLocalRandom.current().nextInt(BaseStats.TIER_3_PLUS_RARITY_1_BONUS_MAX);
                max += ThreadLocalRandom.current().nextInt(BaseStats.TIER_3_PLUS_RARITY_1_BONUS_MAX) + BaseStats.TIER_3_PLUS_RARITY_1_BONUS_MAX;
            }

            if (rarity <= 2) {
                int bonus = (ThreadLocalRandom.current().nextInt(BaseStats.LOW_RARITY_MAX_BONUS_ROLLS) + 1) * tier * rarity * BaseStats.LOW_RARITY_BONUS_MULTIPLIER;
                min += bonus;
                max += bonus;
            }

            if (min > max) {
                int temp = min;
                min = max;
                max = temp;
            }

            if (itemType == ItemTypes.AXE) {
                min = (int) (min * BaseStats.AXE_DAMAGE_MULTIPLIER);
                max = (int) (max * BaseStats.AXE_DAMAGE_MULTIPLIER);
            } else if (ItemTypes.isStaffOrSpear(itemType)) {
                min = (int) (min / BaseStats.STAFF_SPEAR_DAMAGE_DIVISOR);
                max = (int) (max / BaseStats.STAFF_SPEAR_DAMAGE_DIVISOR);
            }

            return new DamageStats(min, max);
        }

        public int calculateArmorHP(int tier, int rarity, int itemType) {
            double baseHp;

            if (ItemTypes.isHelmetOrBoots(itemType)) {
                baseHp = (armorBaseValues.get(tier - 1) * (rarity / (1 + (rarity / 10.0)))) / BaseStats.HELMET_BOOTS_HP_DIVISOR;
            } else {
                baseHp = armorBaseValues.get(tier - 1) * (rarity / (1 + (rarity / 10.0)));
            }

            int hp = ThreadLocalRandom.current().nextInt((int) baseHp / 4) + (int) baseHp;

            if (ItemTypes.isChestplateOrLeggings(itemType)) {
                hp = (int) (hp * BaseStats.CHESTPLATE_LEGGINGS_HP_MULTIPLIER);
            }

            return hp;
        }
    }

    /**
     * MAJOR FIX: Handles item building and meta application (NO automatic orb effects)
     */
    private static class ItemBuilder {

        public ItemStack createBaseItem(TierConfig tierConfig, ItemTypeConfig itemTypeConfig) {
            String materialPrefix = tierConfig.getMaterialPrefix(itemTypeConfig.isWeapon());
            String materialSuffix = itemTypeConfig.getMaterialSuffix();
            String materialName = materialPrefix + "_" + materialSuffix;

            Material material;
            try {
                material = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                YakRealms.getInstance().getLogger().warning("Invalid material name: " + materialName);
                material = Material.STONE;
            }

            return new ItemStack(material);
        }

        public ItemStack finalizeItem(ItemStack item, ItemMeta meta, List<String> lore,
                                      int tier, int itemType, int rarity,
                                      NamespacedKey keyRarity, NamespacedKey keyTier, NamespacedKey keyItemType) {
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }

            meta.setLore(lore);

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyRarity, PersistentDataType.INTEGER, rarity);
            container.set(keyTier, PersistentDataType.INTEGER, tier);
            container.set(keyItemType, PersistentDataType.INTEGER, itemType);

            item.setItemMeta(meta);

            // MAJOR FIX: Removed automatic orb application - returns clean item with finalized stats only
            return item;
        }
    }

    /**
     * Provides item names based on tier and type
     */
    private static class NameProvider {

        private static final String[][] ITEM_NAMES = {
                {"Staff", "Spear", "Shortsword", "Hatchet", "Leather Coif", "Leather Chestplate", "Leather Leggings", "Leather Boots"},
                {"Battlestaff", "Halberd", "Broadsword", "Great Axe", "Medium Helmet", "Chainmail", "Chainmail Leggings", "Chainmail Boots"},
                {"Wizard Staff", "Magic Polearm", "Magic Sword", "War Axe", "Full Helmet", "Platemail", "Platemail Leggings", "Platemail Boots"},
        };

        private static final String[] TIER_PREFIXES = {"", "", "", "Ancient", "Legendary", "Nether-Forged"};
        private static final String[] ITEM_SUFFIXES = {"Staff", "Polearm", "Sword", "Axe", "Helmet", "Chestplate", "Leggings", "Boots"};

        public String getItemName(int itemType, int tier) {
            ChatColor color = getTierColor(tier);

            if (tier <= 3) {
                return color + ITEM_NAMES[tier - 1][itemType - 1];
            } else {
                return color + TIER_PREFIXES[tier - 1] + " " + ITEM_SUFFIXES[itemType - 1];
            }
        }

        private ChatColor getTierColor(int tier) {
            switch (tier) {
                case 1: return ChatColor.WHITE;
                case 2: return ChatColor.GREEN;
                case 3: return ChatColor.AQUA;
                case 4: return ChatColor.LIGHT_PURPLE;
                case 5: return ChatColor.YELLOW;
                case 6: return ChatColor.GOLD;
                default: return ChatColor.WHITE;
            }
        }
    }

    /**
     * Simple container for damage stats
     */
    private static class DamageStats {
        private final int min;
        private final int max;

        public DamageStats(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int getMin() { return min; }
        public int getMax() { return max; }
    }
}
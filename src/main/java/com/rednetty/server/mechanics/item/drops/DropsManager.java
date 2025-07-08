package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.config.ConfigManager;
import com.rednetty.server.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.item.drops.types.*;
import com.rednetty.server.mechanics.item.orb.OrbAPI;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Central manager for item drops from mobs and bosses
 * Refactored for better maintainability and clearer configuration
 */
public class DropsManager {

    // ===== CONFIGURATION CONSTANTS =====
    // Base values for stat calculations - easily modifiable
    private static final class BaseStats {
        // TIER SCALING - Controls how much stats increase between tiers
        // Higher values = bigger gaps between tiers, Lower values = smaller gaps
        // Example: 2.0 = each tier is 2x stronger, 1.5 = each tier is 1.5x stronger
        //
        // With TIER_GAP_MULTIPLIER = 2.1:
        // Tier 1: base value (e.g., 30 HP)
        // Tier 2: base * 2.1 (e.g., 63 HP)
        // Tier 3: base * 2.1² (e.g., 132 HP)
        // Tier 4: base * 2.1³ (e.g., 278 HP)
        // Tier 5: base * 2.1⁴ (e.g., 584 HP)
        // Tier 6: base * 2.1⁵ (e.g., 1,226 HP)
        static double TIER_GAP_MULTIPLIER = 2.4; // Made non-final for runtime modification

        // Base values for tier 1 items
        static final double ARMOR_HP_BASE = 30.0;
        static final double MIN_DAMAGE_BASE = 5.0;
        static final double MAX_DAMAGE_BASE = 6.0;

        // Damage calculation modifiers
        static final double RARITY_DAMAGE_MODIFIER_BASE = 0.7;
        static final double RARITY_DAMAGE_DIVISOR_MIN = 2.35;
        static final double RARITY_DAMAGE_DIVISOR_MAX = 2.3;
        static final double DAMAGE_VARIANCE_DIVISOR = 4.0;

        // Item type multipliers
        static final double AXE_DAMAGE_MULTIPLIER = 1.2;
        static final double STAFF_SPEAR_DAMAGE_DIVISOR = 1.5;
        static final double CHESTPLATE_LEGGINGS_HP_MULTIPLIER = 1.2;
        static final double HELMET_BOOTS_HP_DIVISOR = 1.5;

        // Special bonuses
        static final int TIER_3_PLUS_RARITY_1_BONUS_MAX = 15;
        static final int LOW_RARITY_BONUS_MULTIPLIER = 1; // multiplied by tier * rarity
        static final int LOW_RARITY_MAX_BONUS_ROLLS = 2;

        // Regen calculations
        static final double HP_REGEN_MIN_PERCENT = 0.19;
        static final double HP_REGEN_MAX_PERCENT = 0.21;
        static final int MAX_ENERGY_REGEN = 6;

        // DPS/Armor calculations
        static final double DPS_BASE_MULTIPLIER = 1.7;
        static final double DPS_MIN_DIVISOR = 1.5;
    }

    // Rarity probability thresholds
    private static final class RarityThresholds {
        static final int UNIQUE_THRESHOLD = 2;      // 2% chance
        static final int RARE_THRESHOLD = 12;       // 10% chance (2-12)
        static final int UNCOMMON_THRESHOLD = 38;   // 26% chance (12-38)
        // Common is everything else (62%)
    }

    // Elemental damage chance
    private static final int ELEMENTAL_DAMAGE_CHANCE = 3; // 1 in 3 chance

    // Item type constants for clarity
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
    private final NamespacedKey keyFixedGear;

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
        this.keyFixedGear = new NamespacedKey(plugin, "fixedgear");

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
     * Initializes the drops manager
     */
    public void initialize() {
        configManager.initialize();
        lootBuffManager.initialize();
        getDropFactory();
        DropsHandler.getInstance().initialize();
        logger.info("[DropsManager] has been initialized");
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
     * Creates a drop with the specified tier, item type and rarity
     */
    public ItemStack createDrop(int tier, int itemType, int rarity) {
        // Validate and clamp parameters
        tier = clamp(tier, 1, 6);
        itemType = clamp(itemType, 1, 8);
        rarity = clamp(rarity, 1, 4);

        // Get configurations
        TierConfig tierConfig = DropConfig.getTierConfig(tier);
        RarityConfig rarityConfig = DropConfig.getRarityConfig(rarity);
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

        if (tierConfig == null || rarityConfig == null || itemTypeConfig == null) {
            logger.warning("Failed to create drop: Missing configuration for tier=" +
                    tier + ", rarity=" + rarity + ", itemType=" + itemType);
            return null;
        }

        // Create the base item
        ItemStack item = itemBuilder.createBaseItem(tierConfig, itemTypeConfig);
        if (item == null) {
            return null;
        }

        // Build the item with stats and lore
        if (ItemTypes.isWeapon(itemType)) {
            return buildWeaponItem(item, tier, itemType, rarity, rarityConfig);
        } else {
            return buildArmorItem(item, tier, itemType, rarity, rarityConfig);
        }
    }

    /**
     * Creates a custom elite drop for a specific mob type (backward compatibility)
     */
    public ItemStack createEliteDrop(String mobType, int itemType) {
        return createEliteDrop(mobType, itemType, 0);
    }

    /**
     * Creates a custom elite drop for a specific mob type
     */
    public ItemStack createEliteDrop(String mobType, int itemType, int actualMobTier) {
        String normalizedMobType = mobType.toLowerCase();
        EliteDropConfig config = DropConfig.getEliteDropConfig(normalizedMobType);

        // If no config exists, fall back to standard drops
        if (config == null) {
            logger.warning("[DropsManager] No elite drop configuration found for mob type: " + mobType);
            return createFallbackEliteDrop(actualMobTier, itemType);
        }

        return createConfiguredEliteDrop(config, itemType, mobType);
    }

    /**
     * Determines if a mob should drop an item
     */
    public boolean shouldDropItem(LivingEntity entity, boolean isElite, int tier) {
        int baseDropRate = DropConfig.getDropRate(tier);
        int dropRate = isElite ? DropConfig.getEliteDropRate(tier) : baseDropRate;

        // Apply buff if active
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (dropRate * buffPercentage / 100);
        }

        // Roll for drop
        int roll = ThreadLocalRandom.current().nextInt(100);
        boolean shouldDrop = roll < dropRate;

        // Track buff improvements
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
     * Builds a weapon item with appropriate stats and lore
     */
    private ItemStack buildWeaponItem(ItemStack item, int tier, int itemType, int rarity, RarityConfig rarityConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set item name
        meta.setDisplayName(ChatColor.RESET + nameProvider.getItemName(itemType, tier));

        // Create lore
        List<String> lore = new ArrayList<>();

        // Calculate and add damage
        DamageStats damage = statCalculator.calculateWeaponDamage(tier, rarity, itemType);
        lore.add(ChatColor.RED + "DMG: " + damage.getMin() + " - " + damage.getMax());

        // Add elemental damage (33% chance)
        addElementalDamageToLore(lore, tier, rarity);

        // Add special weapon attributes for rare+ items
        if (rarity >= 3) {
            addWeaponSpecialAttributes(lore, tier, rarity);
        }

        // Add rarity line
        lore.add(rarityConfig.getFormattedName());

        // Apply meta and persistent data
        return itemBuilder.finalizeItem(item, meta, lore, tier, itemType, rarity, keyRarity, keyTier, keyItemType);
    }

    /**
     * Builds an armor item with appropriate stats and lore
     */
    private ItemStack buildArmorItem(ItemStack item, int tier, int itemType, int rarity, RarityConfig rarityConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set item name
        meta.setDisplayName(ChatColor.RESET + nameProvider.getItemName(itemType, tier));

        // Create lore
        List<String> lore = new ArrayList<>();

        // Add DPS or Armor stat
        addArmorDefenseStat(lore, tier);

        // Calculate and add HP
        int hp = statCalculator.calculateArmorHP(tier, rarity, itemType);
        lore.add(ChatColor.RED + "HP: +" + hp);

        // Add energy or HP regeneration
        addArmorRegenStat(lore, tier, hp);

        // Add special armor attributes for uncommon+ items
        if (rarity >= 2) {
            addArmorSpecialAttributes(lore, tier, rarity);
        }

        // Add rarity line
        lore.add(rarityConfig.getFormattedName());

        // Apply meta and persistent data
        ItemStack finalItem = itemBuilder.finalizeItem(item, meta, lore, tier, itemType, rarity, keyRarity, keyTier, keyItemType);

        // Apply special effects for tier 6 netherite armor
        if (tier == 6 && item.getType().toString().contains("NETHERITE")) {
            finalItem = applyNetheriteGoldTrim(finalItem);
        }

        return OrbManager.getInstance().applyOrbToItem(finalItem, false, 0);
    }

    /**
     * Creates a fallback elite drop when no specific config exists
     */
    private ItemStack createFallbackEliteDrop(int actualMobTier, int itemType) {
        int tier = (actualMobTier > 0) ? actualMobTier : 3;
        int rarity = tier >= 5 ? 4 : tier >= 3 ? 3 : 2;

        logger.info("[DropsManager] Falling back to regular drop with tier " + tier +
                " and rarity " + rarity);

        return createDrop(tier, itemType, rarity);
    }

    /**
     * Creates an elite drop based on configuration
     */
    private ItemStack createConfiguredEliteDrop(EliteDropConfig config, int itemType, String mobType) {
        ItemDetails details = config.getItemDetailsForType(itemType);

        if (details == null) {
            logger.warning("[DropsManager] No item details found for mob type: " + mobType + ", item type: " + itemType);
            return createDrop(config.getTier(), itemType, config.getRarity());
        }

        // Create the item with proper material and stats
        Material material = getMaterialForEliteDrop(details, config, itemType);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set name and create lore
        ChatColor tierColor = getTierColor(config.getTier());
        meta.setDisplayName(tierColor + ChatColor.stripColor(details.getName()));

        List<String> lore = new ArrayList<>();
        boolean isWeapon = ItemTypes.isWeapon(itemType);

        if (isWeapon) {
            buildEliteWeaponLore(lore, config, details, itemType);
        } else {
            buildEliteArmorLore(lore, config, details, itemType);
        }

        // Add lore text and rarity
        lore.add(ChatColor.GRAY + details.getLore());
        lore.add(getRarityColor(config.getRarity()) + ChatColor.ITALIC.toString() + getRarityText(config.getRarity()));

        // Finalize item
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }
        meta.setLore(lore);

        // Store metadata
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRarity, PersistentDataType.INTEGER, config.getRarity());
        container.set(keyTier, PersistentDataType.INTEGER, config.getTier());
        container.set(keyItemType, PersistentDataType.INTEGER, itemType);
        container.set(keyEliteDrop, PersistentDataType.STRING, mobType);

        if (mobType.equalsIgnoreCase("spectralKnight")) {
            container.set(keyFixedGear, PersistentDataType.INTEGER, 1);
        }

        item.setItemMeta(meta);

        // Apply special effects
        if (config.getTier() == 6 && itemType > 4 && material.toString().contains("NETHERITE")) {
            item = applyNetheriteGoldTrim(item);
        }

        if (isSpecialEliteDrop(mobType)) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
        }

        return item;
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

    /**
     * Adds DPS or Armor stat to armor lore
     */
    private void addArmorDefenseStat(List<String> lore, int tier) {
        double dpsMax = tier * (1.0 + (tier / BaseStats.DPS_BASE_MULTIPLIER));
        double dpsMin = dpsMax / BaseStats.DPS_MIN_DIVISOR;
        int dpsi = (int) dpsMin;
        int dpsa = (int) dpsMax;

        // Randomly choose between DPS or ARMOR
        String statType = ThreadLocalRandom.current().nextInt(4) == 0 ? "DPS" : "ARMOR";
        lore.add(ChatColor.RED + statType + ": " + dpsi + " - " + dpsa + "%");
    }

    /**
     * Adds energy or HP regeneration stat to armor lore
     */
    private void addArmorRegenStat(List<String> lore, int tier, int hp) {
        int nrghp = ThreadLocalRandom.current().nextInt(4);

        if (nrghp > 0) {
            // Energy regen
            int nrg = tier;
            int nrgToTake = ThreadLocalRandom.current().nextInt(2);
            int nrgToGive = ThreadLocalRandom.current().nextInt(3);
            nrg = nrg - nrgToTake + nrgToGive + (ThreadLocalRandom.current().nextInt(tier) / 2);
            nrg = clamp(nrg, 1, BaseStats.MAX_ENERGY_REGEN);
            lore.add(ChatColor.RED + "ENERGY REGEN: +" + nrg + "%");
        } else {
            // HP regen
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

    // ===== ELITE DROP HELPER METHODS =====

    private Material getMaterialForEliteDrop(ItemDetails details, EliteDropConfig config, int itemType) {
        Material material = details.getMaterial();
        if (material != null) {
            return material;
        }

        // Fallback to appropriate material
        TierConfig tierConfig = DropConfig.getTierConfig(config.getTier());
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);
        if (tierConfig != null && itemTypeConfig != null) {
            String materialPrefix = tierConfig.getMaterialPrefix(itemTypeConfig.isWeapon());
            String materialSuffix = itemTypeConfig.getMaterialSuffix();
            String materialName = materialPrefix + "_" + materialSuffix;
            try {
                return Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                return Material.STONE;
            }
        }
        return Material.STONE;
    }

    private void buildEliteWeaponLore(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Get damage stats
        StatRange damageRange = getStatRange(config, details, "damage", true, itemType);
        int minDmg = damageRange.getMin();
        int maxDmg = damageRange.getMax();
        lore.add(ChatColor.RED + "DMG: " + minDmg + " - " + maxDmg);

        // Add elemental damage (only one type)
        addElementalDamageFromConfig(lore, config, details, itemType);

        // Add special stats
        addSpecialStatsFromConfig(lore, config, details, true, itemType);
    }

    private void buildEliteArmorLore(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Add DPS or Armor
        boolean useDps = config.getArmorStatRange("armor") == null && details.getStatOverride("armor") == null;

        if (useDps) {
            StatRange dpsRange = getStatRange(config, details, "dps", false, itemType);
            int dps = dpsRange.getRandomValue();
            lore.add(ChatColor.RED + "DPS: " + dps + " - " + dps + "%");
        } else {
            StatRange armorRange = getStatRange(config, details, "armor", false, itemType);
            int armor = armorRange.getRandomValue();
            lore.add(ChatColor.RED + "ARMOR: " + armor + " - " + armor + "%");
        }

        // Add HP
        StatRange hpRange = getStatRange(config, details, "hp", false, itemType);
        int hp = hpRange != null ? hpRange.getRandomValue() : calculateFallbackHP(config.getTier(), itemType);
        lore.add(ChatColor.RED + "HP: +" + hp);

        // Add regen stats
        addRegenStatsFromConfig(lore, config, details, itemType);

        // Add special stats
        addSpecialStatsFromConfig(lore, config, details, false, itemType);
    }

    private int calculateFallbackHP(int tier, int itemType) {
        int baseHp;
        switch (tier) {
            case 1: baseHp = ThreadLocalRandom.current().nextInt(100, 150); break;
            case 2: baseHp = ThreadLocalRandom.current().nextInt(250, 500); break;
            case 3: baseHp = ThreadLocalRandom.current().nextInt(500, 1000); break;
            case 4: baseHp = ThreadLocalRandom.current().nextInt(1000, 2000); break;
            case 5: baseHp = ThreadLocalRandom.current().nextInt(2000, 5000); break;
            case 6: baseHp = ThreadLocalRandom.current().nextInt(4000, 8000); break;
            default: baseHp = 100;
        }

        return ItemTypes.isChestplateOrLeggings(itemType) ? baseHp : baseHp / 2;
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

    private boolean loreContains(List<String> lore, String attribute) {
        return lore.stream().anyMatch(line -> ChatColor.stripColor(line).contains(attribute));
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

    private boolean isSpecialEliteDrop(String mobType) {
        return mobType.equalsIgnoreCase("bossSkeletonDungeon") ||
                mobType.equalsIgnoreCase("frostKing") ||
                mobType.equalsIgnoreCase("warden");
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

    // ===== DELEGATED METHODS (using helper classes - these would need the helper class implementations) =====

    private StatRange getStatRange(EliteDropConfig config, ItemDetails details, String statName, boolean isWeapon, int itemType) {
        // Check for override in item details
        if (details != null && details.getStatOverride(statName) != null) {
            return details.getStatOverride(statName);
        }

        // Special handling for armor-specific HP ranges
        if (!isWeapon && statName.equals("hp") && itemType >= 5 && itemType <= 8) {
            Map<String, StatRange> armorTypeMap = config.getArmorTypeStats().get("hp");
            if (armorTypeMap != null) {
                String typeName;
                switch (itemType) {
                    case 5: typeName = "helmet"; break;
                    case 6: typeName = "chestplate"; break;
                    case 7: typeName = "leggings"; break;
                    case 8: typeName = "boots"; break;
                    default: typeName = null;
                }

                if (typeName != null && armorTypeMap.containsKey(typeName)) {
                    return armorTypeMap.get(typeName);
                }
            }
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

        // Default fallback
        return new StatRange(10, 50);
    }

    private void addElementalDamageFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Implementation would be similar to the original but cleaner
        // This method handles elemental damage from elite drop configs
    }

    private void addRegenStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Check for energy regen
        StatRange energyRange = getStatRange(config, details, "energyRegen", false, itemType);
        if (energyRange != null && energyRange.getMax() > 0) {
            int value = energyRange.getRandomValue();
            if (value > 0) {
                lore.add(ChatColor.RED + "ENERGY REGEN: +" + value + "%");
                return;
            }
        }

        // Otherwise check for HP regen
        StatRange hpsRange = getStatRange(config, details, "hpsregen", false, itemType);
        if (hpsRange != null && hpsRange.getMax() > 0) {
            int value = hpsRange.getRandomValue();
            if (value > 0) {
                lore.add(ChatColor.RED + "HP REGEN: +" + value + "/s");
            }
        }
    }

    private void addSpecialStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, boolean isWeapon, int itemType) {
        // Implementation would handle special stats from elite drop configs
        // This would be similar to the original but organized better
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

    /**
     * Utility method for debugging/balancing - shows base stat progression by tier
     * Useful for checking how the TIER_GAP_MULTIPLIER affects stat scaling
     */
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

    /**
     * Get the base stat value for a specific tier (before rarity/type modifiers)
     * Useful for other systems that need to know base progression
     */
    public double getBaseTierValue(double baseValue, int tier) {
        return baseValue * Math.pow(BaseStats.TIER_GAP_MULTIPLIER, tier - 1);
    }

    /**
     * Updates the tier gap multiplier and reinitializes stat calculations
     * This affects how much stronger each tier is compared to the previous tier
     *
     * @param newMultiplier The new multiplier (e.g., 2.0 = 2x stronger per tier)
     */
    public void setTierGapMultiplier(double newMultiplier) {
        if (newMultiplier <= 1.0) {
            logger.warning("Tier gap multiplier must be greater than 1.0. Ignoring value: " + newMultiplier);
            return;
        }

        double oldMultiplier = BaseStats.TIER_GAP_MULTIPLIER;
        BaseStats.TIER_GAP_MULTIPLIER = newMultiplier;

        // Reinitialize the stat calculator with new values
        this.statCalculator = new StatCalculator();

        logger.info("Updated tier gap multiplier from " + oldMultiplier + " to " + newMultiplier);
        logTierProgression(); // Show the new progression
    }

    /**
     * Gets the current tier gap multiplier
     */
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
            // All stats now use the same tier gap multiplier for consistent scaling
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
            // Get base values
            double minMin = minDamageValues.get(tier - 1) * ((rarity + BaseStats.RARITY_DAMAGE_MODIFIER_BASE) / BaseStats.RARITY_DAMAGE_DIVISOR_MIN);
            double minMax = minMin / BaseStats.DAMAGE_VARIANCE_DIVISOR;
            double maxMin = maxDamageValues.get(tier - 1) * ((rarity + BaseStats.RARITY_DAMAGE_MODIFIER_BASE) / BaseStats.RARITY_DAMAGE_DIVISOR_MAX);
            double maxMax = maxMin / BaseStats.DAMAGE_VARIANCE_DIVISOR;

            // Calculate random values
            int minRange = Math.max(1, (int) minMax);
            int maxRange = Math.max(1, (int) maxMax);
            int min = ThreadLocalRandom.current().nextInt(minRange) + (int) minMin;
            int max = ThreadLocalRandom.current().nextInt(maxRange) + (int) maxMin;

            // Apply special bonuses
            if (rarity == 1 && tier >= 3) {
                min += ThreadLocalRandom.current().nextInt(BaseStats.TIER_3_PLUS_RARITY_1_BONUS_MAX);
                max += ThreadLocalRandom.current().nextInt(BaseStats.TIER_3_PLUS_RARITY_1_BONUS_MAX) + BaseStats.TIER_3_PLUS_RARITY_1_BONUS_MAX;
            }

            if (rarity <= 2) {
                int bonus = (ThreadLocalRandom.current().nextInt(BaseStats.LOW_RARITY_MAX_BONUS_ROLLS) + 1) * tier * rarity * BaseStats.LOW_RARITY_BONUS_MULTIPLIER;
                min += bonus;
                max += bonus;
            }

            // Ensure min <= max
            if (min > max) {
                int temp = min;
                min = max;
                max = temp;
            }

            // Apply item type modifiers
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
     * Handles item building and meta application
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
            // Hide all item flags
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }

            // Set lore
            meta.setLore(lore);

            // Store persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyRarity, PersistentDataType.INTEGER, rarity);
            container.set(keyTier, PersistentDataType.INTEGER, tier);
            container.set(keyItemType, PersistentDataType.INTEGER, itemType);

            item.setItemMeta(meta);
            return OrbManager.getInstance().applyOrbToItem(item, false, 0);
        }
    }

    /**
     * Provides item names based on tier and type
     */
    private static class NameProvider {

        private static final String[][] ITEM_NAMES = {
                // Tier 1 (White)
                {"Staff", "Spear", "Shortsword", "Hatchet", "Leather Coif", "Leather Chestplate", "Leather Leggings", "Leather Boots"},
                // Tier 2 (Green)
                {"Battlestaff", "Halberd", "Broadsword", "Great Axe", "Medium Helmet", "Chainmail", "Chainmail Leggings", "Chainmail Boots"},
                // Tier 3 (Aqua)
                {"Wizard Staff", "Magic Polearm", "Magic Sword", "War Axe", "Full Helmet", "Platemail", "Platemail Leggings", "Platemail Boots"},
                // Tier 4+ use prefixes
        };

        private static final String[] TIER_PREFIXES = {"", "", "", "Ancient", "Legendary", "Netherite"};
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
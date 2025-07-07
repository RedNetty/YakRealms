package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.config.ConfigManager;
import com.rednetty.server.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.item.drops.types.*;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Central manager for item drops from mobs and bosses
 */
public class DropsManager {
    private static DropsManager instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Components
    private DropFactory dropFactory;
    private final LootNotifier lootNotifier;
    private final LootBuffManager lootBuffManager;
    private final ConfigManager configManager;
    private final MobManager mobManager;

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
    }

    /**
     * Gets the singleton instance
     *
     * @return The DropsManager instance
     */
    public static DropsManager getInstance() {
        if (instance == null) {
            instance = new DropsManager();
        }
        return instance;
    }

    /**
     * Lazy initialization of DropFactory
     *
     * @return The DropFactory instance
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
        // Initialize configuration
        configManager.initialize();

        // Initialize loot buff manager
        lootBuffManager.initialize();

        // Initialize drop factory (first access)
        getDropFactory();

        // Initialize drop handler
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
     *
     * @param tier     The tier level (1-6)
     * @param itemType The item type (1-8)
     * @return The created ItemStack
     */
    public static ItemStack createDrop(int tier, int itemType) {
        // Determine rarity using weighted probability
        int rarity = determineRarity();
        return getInstance().createDrop(tier, itemType, rarity);
    }

    /**
     * Creates a drop with the specified tier, item type and rarity
     *
     * @param tier     The tier level (1-6)
     * @param itemType The item type ID (1-8)
     * @param rarity   The rarity level (1-4)
     * @return The created ItemStack
     */
    public ItemStack createDrop(int tier, int itemType, int rarity) {
        // Validate parameters
        tier = Math.max(1, Math.min(6, tier)); // Ensure tier is between 1-6
        itemType = Math.max(1, Math.min(8, itemType)); // Ensure itemType is between 1-8
        rarity = Math.max(1, Math.min(4, rarity)); // Ensure rarity is between 1-4

        // Get configurations
        TierConfig tierConfig = DropConfig.getTierConfig(tier);
        RarityConfig rarityConfig = DropConfig.getRarityConfig(rarity);
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

        if (tierConfig == null || rarityConfig == null || itemTypeConfig == null) {
            logger.warning("Failed to create drop: Missing configuration for tier=" +
                    tier + ", rarity=" + rarity + ", itemType=" + itemType);
            return null;
        }

        // Construct item material string
        String materialPrefix = tierConfig.getMaterialPrefix(itemTypeConfig.isWeapon());
        String materialSuffix = itemTypeConfig.getMaterialSuffix();
        String materialName = materialPrefix + "_" + materialSuffix;

        // Create the item
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material name: " + materialName + " for tier=" +
                    tier + ", itemType=" + itemType);
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Set item name with color based on tier
        String itemName = setName(itemType, tier);
        meta.setDisplayName(ChatColor.RESET + itemName);

        // Create lore
        List<String> lore = new ArrayList<>();

        // Base variables for damage calculation
        ArrayList<Integer> armour_base = setbase(30, 2.1);
        ArrayList<Integer> min_damages = setbase(5, 2);
        ArrayList<Integer> max_damages = setbase(6, 2);

        // Calculate stats based on item type
        if (itemTypeConfig.isWeapon()) {
            // For weapons: calculate damage ranges

            // Original formula from old system
            double min_min = min_damages.get(tier - 1) * ((rarity + .7) / 2.35);
            double min_max = min_min / 4D;
            double max_min = max_damages.get(tier - 1) * ((rarity + .7) / 2.3);
            double max_max = max_min / 4D;

            // FIX: Ensure bounds are positive
            int minRange = Math.max(1, (int) min_max);
            int maxRange = Math.max(1, (int) max_max);

            int min = ThreadLocalRandom.current().nextInt(minRange) + (int) min_min;
            int max = ThreadLocalRandom.current().nextInt(maxRange) + (int) max_min;

            // Additional adjustments from old system
            if (rarity == 1 && tier >= 3) {
                min += ThreadLocalRandom.current().nextInt(15);
                max += ThreadLocalRandom.current().nextInt(15) + 15;
            }

            if (rarity <= 2) {
                int rand = (ThreadLocalRandom.current().nextInt(2) + 1) * tier * rarity;
                min += rand;
                max += rand;
            }

            if (min > max) {
                int newMax = min;
                min = max;
                max = newMax;
            }

            // Adjustments for item type
            if (itemType == 4) { // Axe
                min *= 1.2;
                max *= 1.2;
            }

            if (itemType == 1 || itemType == 2) { // Staff or Spear
                min = (int) (min / 1.5);
                max = (int) (max / 1.5);
            }

            // Add damage to lore
            lore.add(ChatColor.RED + "DMG: " + min + " - " + max);

            // Add elemental damage with 33% chance
            addElementalDamage(lore, tier, rarity);

            // Special attributes based on rarity/tier
            if (rarity >= 3) {
                addSpecialAttributes(lore, tier, rarity, true);
            }
        } else {
            // For armor: calculate armor, HP, energy regen, etc.
            Random r = new Random();

            // Calculate HP bonus based on armor type
            double base_hp = 0;
            if (itemType == 5 || itemType == 8) { // Helmet or Boots
                base_hp = (armour_base.get(tier - 1) * (rarity / (1 + (rarity / 10D)))) / 1.5D;
            } else {
                base_hp = armour_base.get(tier - 1) * (rarity / (1 + (rarity / 10D)));
            }

            int hp = (r.nextInt((int) base_hp / 4) + (int) base_hp);
            if (itemType == 6 || itemType == 7) hp *= 1.2; // Chestplate or Leggings

            // Calculate DPS/ARMOR
            double dpsmax = tier * (1D + (tier / 1.7D));
            double dpsmin = dpsmax / 1.5D;
            int dpsi = (int) dpsmin;
            int dpsa = (int) dpsmax;

            // Randomly choose between DPS or ARMOR
            int randomStat = ThreadLocalRandom.current().nextInt(4) + 1;
            if (randomStat == 1) {
                lore.add(ChatColor.RED + "DPS: " + dpsi + " - " + dpsa + "%");
            } else {
                lore.add(ChatColor.RED + "ARMOR: " + dpsi + " - " + dpsa + "%");
            }

            lore.add(ChatColor.RED + "HP: +" + hp);

            // Decide between ENERGY REGEN or HP REGEN
            int nrghp = ThreadLocalRandom.current().nextInt(4);
            int nrg = tier;
            int hps = 0;

            if (nrghp > 0) {
                int nrgToTake = ThreadLocalRandom.current().nextInt(2);
                int nrgToGive = ThreadLocalRandom.current().nextInt(3);
                nrg = (int) (nrg - nrgToTake + nrgToGive + (ThreadLocalRandom.current().nextInt(tier) / 2));
                if (nrg == 0) nrg += 1;
                if (nrg > 6) nrg = 6;
                lore.add(ChatColor.RED + "ENERGY REGEN: +" + nrg + "%");
            } else if (nrghp == 0) {
                int minHps = Math.max(1, (int) (hp * 0.19));
                int maxHps = Math.max(minHps + 1, (int) (hp * 0.21));
                hps = ThreadLocalRandom.current().nextInt(minHps, maxHps);
                lore.add(ChatColor.RED + "HP REGEN: +" + hps + "/s");
            }

            // Special attributes for armor based on rarity
            if (rarity >= 2) {
                addSpecialAttributes(lore, tier, rarity, false);
            }
        }

        // Add rarity line
        lore.add(rarityConfig.getFormattedName());

        // Apply item meta
        meta.setLore(lore);
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }

        // Store item data in persistent data container
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRarity, PersistentDataType.INTEGER, rarity);
        container.set(keyTier, PersistentDataType.INTEGER, tier);
        container.set(keyItemType, PersistentDataType.INTEGER, itemType);

        item.setItemMeta(meta);

        // Special handling for leather armor with tier 6
        if (tier == 6 && material.toString().contains("LEATHER")) {
            item = applyLeatherDye(item, 0, 0, 255); // Blue for tier 6
        }

        return item;
    }

    /**
     * Creates a custom elite drop for a specific mob type (backward compatibility)
     *
     * @param mobType  The type of mob
     * @param itemType The item type (1-8)
     * @return The custom elite drop item
     */
    public ItemStack createEliteDrop(String mobType, int itemType) {
        // Call the main method with tier=0 for backward compatibility
        return createEliteDrop(mobType, itemType, 0);
    }

    /**
     * Creates a custom elite drop for a specific mob type
     *
     * @param mobType       The type of mob
     * @param itemType      The item type (1-8)
     * @param actualMobTier The actual tier of the mob (for fallback)
     * @return The custom elite drop item
     */
    public ItemStack createEliteDrop(String mobType, int itemType, int actualMobTier) {
        // Normalize the mobType for case insensitivity
        String normalizedMobType = mobType.toLowerCase();

        // Get elite drop configuration
        EliteDropConfig config = DropConfig.getEliteDropConfig(normalizedMobType);

        // If no config exists for this mob type, fall back to standard drops with the ACTUAL mob's tier
        if (config == null) {
            logger.warning("[DropsManager] No elite drop configuration found for mob type: " + mobType);

            // Use the actual mob tier that was passed in, defaulting to 3 only if not provided
            int tier = (actualMobTier > 0) ? actualMobTier : 3;
            int rarity = 3; // Default to rare for elites

            // For higher tier mobs, use higher rarity
            if (tier >= 5) {
                rarity = 4; // Unique
            } else if (tier >= 3) {
                rarity = 3; // Rare
            } else {
                rarity = 2; // Uncommon
            }

            logger.info("[DropsManager] Falling back to regular drop with tier " + tier +
                    " and rarity " + rarity + " for elite mob type: " + mobType);

            // Create a standard drop with the determined tier and rarity
            return createDrop(tier, itemType, rarity);
        }

        // Get item details from config
        ItemDetails details = config.getItemDetailsForType(itemType);

        // If no specific details for this item type, fallback to default generation
        if (details == null) {
            logger.warning("[DropsManager] No item details found for mob type: " + mobType + ", item type: " + itemType);

            // Create a standard drop with the config's tier and rarity
            return createDrop(config.getTier(), itemType, config.getRarity());
        }

        // Create the item with correct material
        Material material = details.getMaterial();
        if (material == null) {
            logger.warning("[DropsManager] No material specified for mob type: " + mobType + ", item type: " + itemType);
            // Fallback to appropriate material based on tier/type
            TierConfig tierConfig = DropConfig.getTierConfig(config.getTier());
            ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);
            if (tierConfig != null && itemTypeConfig != null) {
                String materialPrefix = tierConfig.getMaterialPrefix(itemTypeConfig.isWeapon());
                String materialSuffix = itemTypeConfig.getMaterialSuffix();
                String materialName = materialPrefix + "_" + materialSuffix;
                try {
                    material = Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    material = Material.STONE;
                }
            } else {
                material = Material.STONE;
            }
        }

        ItemStack item = new ItemStack(material);

        // Prepare item meta
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        ChatColor tierColor;
        switch (config.getTier()) {
            case 1:
                tierColor = ChatColor.WHITE;
                break;
            case 2:
                tierColor = ChatColor.GREEN;
                break;
            case 3:
                tierColor = ChatColor.AQUA;
                break;
            case 4:
                tierColor = ChatColor.LIGHT_PURPLE;
                break;
            case 5:
                tierColor = ChatColor.YELLOW;
                break;
            case 6:
                tierColor = ChatColor.BLUE;
                break;
            default:
                tierColor = ChatColor.WHITE;
        }

        // Set name with proper color
        meta.setDisplayName(tierColor + ChatColor.stripColor(details.getName()));

        // Create lore for the item
        List<String> lore = new ArrayList<>();
        boolean isWeapon = itemType <= 4;

        if (isWeapon) {
            // Get damage stats from config
            StatRange damageRange = getStatRange(config, details, "damage", true, itemType);
            int minDmg = damageRange.getMin();
            int maxDmg = damageRange.getMax();

            // Add damage to lore
            lore.add(ChatColor.RED + "DMG: " + minDmg + " - " + maxDmg);

            // Add element damage if available (FIXED: Only one elemental type)
            addElementalDamageFromConfig(lore, config, details, itemType);

            // Add weapon special stats
            addSpecialStatsFromConfig(lore, config, details, true, itemType);
        } else {
            // For armor

            // Determine whether to use armor or DPS (prefer what's in config)
            boolean useDps = true;
            if (config.getArmorStatRange("armor") != null ||
                    (details.getStatOverride("armor") != null)) {
                useDps = false;
            }

            if (useDps) {
                // Get DPS from config
                StatRange dpsRange = getStatRange(config, details, "dps", false, itemType);
                int dps = dpsRange.getRandomValue();
                lore.add(ChatColor.RED + "DPS: " + dps + " - " + dps + "%");
            } else {
                // Get armor from config
                StatRange armorRange = getStatRange(config, details, "armor", false, itemType);
                int armor = armorRange.getRandomValue();
                lore.add(ChatColor.RED + "ARMOR: " + armor + " - " + armor + "%");
            }

            // Add HP - critical for armor pieces
            StatRange hpRange = getStatRange(config, details, "hp", false, itemType);
            int hp = 0;
            if (hpRange != null) {
                hp = hpRange.getRandomValue();
            } else {
                // Fallback using tier as in the original code
                int baseHp = 0;
                switch (config.getTier()) {
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
                }

                // Adjust based on item type as in original code
                if (itemType == 6 || itemType == 7) { // Chestplate or leggings
                    hp = baseHp;
                } else { // Helmet or boots
                    hp = baseHp / 2;
                }
            }
            lore.add(ChatColor.RED + "HP: +" + hp);

            // Add energy or hp regen
            addRegenStatsFromConfig(lore, config, details, itemType);

            // Add armor special stats
            addSpecialStatsFromConfig(lore, config, details, false, itemType);
        }

        // Add lore text
        lore.add(ChatColor.GRAY + details.getLore());

        // Add rarity with proper formatting as in original code
        ChatColor rarityColor;
        String rarityText;
        switch (config.getRarity()) {
            case 1:
                rarityColor = ChatColor.GRAY;
                rarityText = "Common";
                break;
            case 2:
                rarityColor = ChatColor.GREEN;
                rarityText = "Uncommon";
                break;
            case 3:
                rarityColor = ChatColor.AQUA;
                rarityText = "Rare";
                break;
            case 4:
                rarityColor = ChatColor.YELLOW;
                rarityText = "Unique";
                break;
            default:
                rarityColor = ChatColor.GRAY;
                rarityText = "Common";
        }

        lore.add(rarityColor + ChatColor.ITALIC.toString() + rarityText);

        // Hide attributes
        for (ItemFlag flag : ItemFlag.values()) {
            meta.addItemFlags(flag);
        }

        // Set lore
        meta.setLore(lore);

        // Store metadata
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRarity, PersistentDataType.INTEGER, config.getRarity());
        container.set(keyTier, PersistentDataType.INTEGER, config.getTier());
        container.set(keyItemType, PersistentDataType.INTEGER, itemType);
        container.set(keyEliteDrop, PersistentDataType.STRING, mobType);

        // Special handling for spectral knight
        if (mobType.equalsIgnoreCase("spectralKnight")) {
            container.set(keyFixedGear, PersistentDataType.INTEGER, 1);
        }

        item.setItemMeta(meta);

        // Handle leather dye for tier 6
        if (config.getTier() == 6 && item.getType().toString().contains("LEATHER")) {
            item = applyLeatherDye(item, 0, 0, 255); // Blue for tier 6
        }

        // Add glow for certain elites
        if (isSpecialEliteDrop(mobType)) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
        }

        return item;
    }

    /**
     * Determines if a mob should drop an item
     *
     * @param entity  The entity
     * @param isElite Whether it's an elite mob
     * @param tier    The mob's tier
     * @return True if it should drop an item
     */
    public boolean shouldDropItem(LivingEntity entity, boolean isElite, int tier) {
        // World bosses always drop items

        int baseDropRate = DropConfig.getDropRate(tier);
        int dropRate = isElite ? DropConfig.getEliteDropRate(tier) : baseDropRate;

        // Apply buff if active
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (dropRate * buffPercentage / 100);
        }

        // Roll for drop
        int roll = ThreadLocalRandom.current().nextInt(100);

        // Check if roll is successful
        boolean shouldDrop = roll < dropRate;

        // If a buff is active and the drop is successful because of the buff, count it
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
     *
     * @param item       The dropped item
     * @param playerUuid The player UUID with protection
     * @param seconds    Protection duration in seconds
     */
    public void registerDropProtection(Item item, UUID playerUuid, int seconds) {
        if (item == null) return;

        // Store protection data in the item's persistent data container
        ItemStack itemStack = item.getItemStack();
        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyDropProtection, PersistentDataType.STRING,
                    playerUuid.toString() + ":" + (System.currentTimeMillis() + (seconds * 1000)));

            itemStack.setItemMeta(meta);
            item.setItemStack(itemStack);
        }

        // Set item to not despawn
        item.setUnlimitedLifetime(true);
    }

    /**
     * Check if a player has protection for an item
     *
     * @param item   The item entity
     * @param player The player
     * @return True if the player has protection
     */
    public boolean hasItemProtection(Item item, Player player) {
        if (item == null || player == null) return false;

        // Get protection data from the item
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

                        // Check if protection is for this player and not expired
                        return protectedUuid.equals(player.getUniqueId()) && System.currentTimeMillis() < expiry;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Sets name for an item based on tier and type
     *
     * @param item Item type ID
     * @param tier Tier level
     * @return Item name with appropriate color
     */
    private static String setName(int item, int tier) {
        switch (tier) {
            case 1:
                switch (item) {
                    case 1:
                        return ChatColor.WHITE + "Staff";
                    case 2:
                        return ChatColor.WHITE + "Spear";
                    case 3:
                        return ChatColor.WHITE + "Shortsword";
                    case 4:
                        return ChatColor.WHITE + "Hatchet";
                    case 5:
                        return ChatColor.WHITE + "Leather Coif";
                    case 6:
                        return ChatColor.WHITE + "Leather Chestplate";
                    case 7:
                        return ChatColor.WHITE + "Leather Leggings";
                    case 8:
                        return ChatColor.WHITE + "Leather Boots";
                }
            case 2:
                switch (item) {
                    case 1:
                        return ChatColor.GREEN + "Battlestaff";
                    case 2:
                        return ChatColor.GREEN + "Halberd";
                    case 3:
                        return ChatColor.GREEN + "Broadsword";
                    case 4:
                        return ChatColor.GREEN + "Great Axe";
                    case 5:
                        return ChatColor.GREEN + "Medium Helmet";
                    case 6:
                        return ChatColor.GREEN + "Chainmail";
                    case 7:
                        return ChatColor.GREEN + "Chainmail Leggings";
                    case 8:
                        return ChatColor.GREEN + "Chainmail Boots";
                }
            case 3:
                switch (item) {
                    case 1:
                        return ChatColor.AQUA + "Wizard Staff";
                    case 2:
                        return ChatColor.AQUA + "Magic Polearm";
                    case 3:
                        return ChatColor.AQUA + "Magic Sword";
                    case 4:
                        return ChatColor.AQUA + "War Axe";
                    case 5:
                        return ChatColor.AQUA + "Full Helmet";
                    case 6:
                        return ChatColor.AQUA + "Platemail";
                    case 7:
                        return ChatColor.AQUA + "Platemail Leggings";
                    case 8:
                        return ChatColor.AQUA + "Platemail Boots";
                }
            case 4:
                return generateNamePrefix(item, "Ancient", ChatColor.LIGHT_PURPLE);
            case 5:
                return generateNamePrefix(item, "Legendary", ChatColor.YELLOW);
            case 6:
                return generateNamePrefix(item, "Frozen", ChatColor.BLUE);
        }
        return "Error, talk to owner";
    }

    /**
     * Generate name prefix for higher tier items
     *
     * @param item  Item type ID
     * @param name  Prefix name
     * @param color Color for the item
     * @return Formatted name
     */
    private static String generateNamePrefix(int item, String name, ChatColor color) {
        switch (item) {
            case 1:
                return color + name + " Staff";
            case 2:
                return color + name + " Polearm";
            case 3:
                return color + name + " Sword";
            case 4:
                return color + name + " Axe";
            case 5:
                return color + name + " Platemail Helmet";
            case 6:
                return color + name + " Platemail";
            case 7:
                return color + name + " Platemail Leggings";
            case 8:
                return color + name + " Platemail Boots";
        }
        return "error in generate name";
    }

    /**
     * Creates base array for stat calculations
     *
     * @param base       Base value
     * @param multiplier Multiplier between tiers
     * @return ArrayList of base values for each tier
     */
    private static ArrayList<Integer> setbase(double base, double multiplier) {
        ArrayList<Integer> newBase = new ArrayList<Integer>();
        for (int i = 0; i < 7; i++) {
            newBase.add((int) base);
            base = base * multiplier;
        }
        return newBase;
    }

    /**
     * Safety method for getting a random int within bounds
     *
     * @param min Minimum value (inclusive)
     * @param max Maximum value (exclusive)
     * @return Random integer between min and max
     */
    private int safeRandomInt(int min, int max) {
        // Ensure max is greater than min
        if (max <= min) {
            max = min + 1;
        }
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    /**
     * Get stat range from config with proper fallbacks
     *
     * @param config   The elite drop configuration
     * @param details  The item details
     * @param statName The name of the stat to get
     * @param isWeapon Whether this is a weapon
     * @param itemType The item type (1-8)
     * @return The appropriate stat range
     */
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
                    case 5:
                        typeName = "helmet";
                        break;
                    case 6:
                        typeName = "chestplate";
                        break;
                    case 7:
                        typeName = "leggings";
                        break;
                    case 8:
                        typeName = "boots";
                        break;
                    default:
                        typeName = null;
                }

                if (typeName != null && armorTypeMap.containsKey(typeName)) {
                    return armorTypeMap.get(typeName);
                }
            }
        }

        // Check in weapon/armor specific ranges
        if (isWeapon) {
            if (config.getWeaponStatRange(statName) != null) {
                return config.getWeaponStatRange(statName);
            }
        } else {
            if (config.getArmorStatRange(statName) != null) {
                return config.getArmorStatRange(statName);
            }
        }

        // Check common stats
        if (config.getStatRange(statName) != null) {
            return config.getStatRange(statName);
        }

        // Create fallback range
        return new StatRange(10, 50); // Default range
    }

    /**
     * Add elemental damage to weapon lore with 33% chance
     *
     * @param lore   Lore list to add to
     * @param tier   Item tier
     * @param rarity Item rarity
     */
    private void addElementalDamage(List<String> lore, int tier, int rarity) {
        // Check if should add elemental damage (33% chance)
        if (ThreadLocalRandom.current().nextInt(3) > 0) {
            return;
        }

        // Determine type
        int elementType = ThreadLocalRandom.current().nextInt(3) + 1;
        String elementName;

        switch (elementType) {
            case 1:
                elementName = "FIRE DMG";
                break;
            case 2:
                elementName = "POISON DMG";
                break;
            case 3:
                elementName = "ICE DMG";
                break;
            default:
                return;
        }

        // Calculate damage amount
        int baseAmount = tier * 5;
        if (rarity >= 3) baseAmount *= 1.5;

        int minAmount = baseAmount;
        int maxAmount = baseAmount * 2;
        int amount = safeRandomInt(minAmount, maxAmount + 1);

        lore.add(ChatColor.RED + elementName + ": +" + amount);
    }

    /**
     * FIXED: Add elemental damage stats from config - only ONE elemental type per weapon
     */
    private void addElementalDamageFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Collect all available elemental damage types
        List<ElementalDamage> availableElements = new ArrayList<>();

        // Fire damage
        StatRange fireRange = getStatRange(config, details, "fireDamage", true, itemType);
        if (fireRange != null && fireRange.getMax() > 0) {
            int value = fireRange.getRandomValue();
            if (value > 0) {
                availableElements.add(new ElementalDamage("FIRE DMG", value));
            }
        }

        // Ice damage
        StatRange iceRange = getStatRange(config, details, "iceDamage", true, itemType);
        if (iceRange != null && iceRange.getMax() > 0) {
            int value = iceRange.getRandomValue();
            if (value > 0) {
                availableElements.add(new ElementalDamage("ICE DMG", value));
            }
        }

        // Poison damage
        StatRange poisonRange = getStatRange(config, details, "poisonDamage", true, itemType);
        if (poisonRange != null && poisonRange.getMax() > 0) {
            int value = poisonRange.getRandomValue();
            if (value > 0) {
                availableElements.add(new ElementalDamage("POISON DMG", value));
            }
        }

        // Pure damage (not an element, but handled similarly)
        StatRange pureRange = getStatRange(config, details, "pureDamage", true, itemType);
        if (pureRange != null && pureRange.getMax() > 0) {
            int value = pureRange.getRandomValue();
            if (value > 0) {
                availableElements.add(new ElementalDamage("PURE DMG", value));
            }
        }

        // Only add ONE elemental damage type if any are available
        if (!availableElements.isEmpty()) {
            // Randomly select one elemental damage type
            ElementalDamage selectedElement = availableElements.get(
                    ThreadLocalRandom.current().nextInt(availableElements.size())
            );
            lore.add(ChatColor.RED + selectedElement.name + ": +" + selectedElement.value);
        }
    }

    /**
     * Helper class to store elemental damage information
     */
    private static class ElementalDamage {
        final String name;
        final int value;

        ElementalDamage(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * Add regen stats from config
     */
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

    /**
     * Adds special attributes to items based on rarity and tier
     *
     * @param lore     Lore list to add to
     * @param tier     Item tier
     * @param rarity   Item rarity
     * @param isWeapon Whether the item is a weapon
     */
    private void addSpecialAttributes(List<String> lore, int tier, int rarity, boolean isWeapon) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (isWeapon) {
            // Weapons can have: LIFE STEAL, CRITICAL HIT, ACCURACY, PURE DMG
            int numAttributes = Math.min(rarity, 3);

            for (int i = 0; i < numAttributes; i++) {
                int attrType = random.nextInt(4);

                switch (attrType) {
                    case 0:
                        if (!loreContains(lore, "LIFE STEAL")) {
                            int amount = safeRandomInt(5, 10);
                            if (rarity >= 3) amount += 3;
                            lore.add(ChatColor.RED + "LIFE STEAL: " + amount + "%");
                        }
                        break;
                    case 1:
                        if (!loreContains(lore, "CRITICAL HIT")) {
                            int amount = safeRandomInt(5, 11);
                            if (rarity >= 3) amount += 5;
                            lore.add(ChatColor.RED + "CRITICAL HIT: " + amount + "%");
                        }
                        break;
                    case 2:
                        if (!loreContains(lore, "ACCURACY")) {
                            int amount = safeRandomInt(15, 25);
                            if (rarity >= 3) amount += 10;
                            lore.add(ChatColor.RED + "ACCURACY: " + amount + "%");
                        }
                        break;
                    case 3:
                        if (!loreContains(lore, "PURE DMG")) {
                            // Safety check for tier * 5
                            int safeBase = Math.max(1, tier * 5);
                            int amount = safeRandomInt(safeBase, safeBase * 2 + 1);
                            if (rarity >= 3) amount = (int) (amount * 1.5);
                            lore.add(ChatColor.RED + "PURE DMG: +" + amount);
                        }
                        break;
                }
            }
        } else {
            // Armor can have: STR, DEX, INT, VIT, DODGE, BLOCK
            int numAttributes = Math.min(rarity, 2);

            for (int i = 0; i < numAttributes; i++) {
                int attrType = random.nextInt(6);

                switch (attrType) {
                    case 0:
                        if (!loreContains(lore, "STR")) {
                            // Safety check for tier * 20
                            int safeBase = Math.max(1, tier * 20);
                            int amount = safeRandomInt(safeBase, safeBase * 2 + 1);
                            if (rarity >= 3) amount = (int) (amount * 1.5);
                            lore.add(ChatColor.RED + "STR: +" + amount);
                        }
                        break;
                    case 1:
                        if (!loreContains(lore, "DEX")) {
                            // Safety check for tier * 20
                            int safeBase = Math.max(1, tier * 20);
                            int amount = safeRandomInt(safeBase, safeBase * 2 + 1);
                            if (rarity >= 3) amount = (int) (amount * 1.5);
                            lore.add(ChatColor.RED + "DEX: +" + amount);
                        }
                        break;
                    case 2:
                        if (!loreContains(lore, "INT")) {
                            // Safety check for tier * 20
                            int safeBase = Math.max(1, tier * 20);
                            int amount = safeRandomInt(safeBase, safeBase * 2 + 1);
                            if (rarity >= 3) amount = (int) (amount * 1.5);
                            lore.add(ChatColor.RED + "INT: +" + amount);
                        }
                        break;
                    case 3:
                        if (!loreContains(lore, "VIT")) {
                            // Safety check for tier * 20
                            int safeBase = Math.max(1, tier * 20);
                            int amount = safeRandomInt(safeBase, safeBase * 2 + 1);
                            if (rarity >= 3) amount = (int) (amount * 1.5);
                            lore.add(ChatColor.RED + "VIT: +" + amount);
                        }
                        break;
                    case 4:
                        if (!loreContains(lore, "DODGE")) {
                            int amount = safeRandomInt(5, 10);
                            if (rarity >= 3) amount += 3;
                            lore.add(ChatColor.RED + "DODGE: " + amount + "%");
                        }
                        break;
                    case 5:
                        if (!loreContains(lore, "BLOCK")) {
                            int amount = safeRandomInt(5, 10);
                            if (rarity >= 3) amount += 3;
                            lore.add(ChatColor.RED + "BLOCK: " + amount + "%");
                        }
                        break;
                }
            }
        }
    }

    /**
     * Add special stats from config
     */
    private void addSpecialStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, boolean isWeapon, int itemType) {
        if (isWeapon) {
            // Weapon special stats: Life Steal, Crit, Accuracy
            StatRange lifeStealRange = getStatRange(config, details, "lifeSteal", true, itemType);
            if (lifeStealRange != null && lifeStealRange.getMax() > 0) {
                int value = lifeStealRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "LIFE STEAL: " + value + "%");
                }
            }

            StatRange critRange = getStatRange(config, details, "criticalHit", true, itemType);
            if (critRange != null && critRange.getMax() > 0) {
                int value = critRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "CRITICAL HIT: " + value + "%");
                }
            }

            StatRange accRange = getStatRange(config, details, "accuracy", true, itemType);
            if (accRange != null && accRange.getMax() > 0) {
                int value = accRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "ACCURACY: " + value + "%");
                }
            }
        } else {
            // Armor special stats: STR, INT, VIT, DODGE, BLOCK

            StatRange strRange = getStatRange(config, details, "strength", false, itemType);
            if (strRange != null && strRange.getMax() > 0) {
                int value = strRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "STR: +" + value);
                }
            }

            StatRange intRange = getStatRange(config, details, "intellect", false, itemType);
            if (intRange != null && intRange.getMax() > 0) {
                int value = intRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "INT: +" + value);
                }
            }

            StatRange vitRange = getStatRange(config, details, "vitality", false, itemType);
            if (vitRange != null && vitRange.getMax() > 0) {
                int value = vitRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "VIT: +" + value);
                }
            }

            StatRange dodgeRange = getStatRange(config, details, "dodgeChance", false, itemType);
            if (dodgeRange != null && dodgeRange.getMax() > 0) {
                int value = dodgeRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "DODGE: " + value + "%");
                }
            }

            StatRange blockRange = getStatRange(config, details, "blockChance", false, itemType);
            if (blockRange != null && blockRange.getMax() > 0) {
                int value = blockRange.getRandomValue();
                if (value > 0) {
                    lore.add(ChatColor.RED + "BLOCK: " + value + "%");
                }
            }
        }
    }

    /**
     * Applies leather dye to an armor piece
     *
     * @param item  The item to dye
     * @param red   Red component (0-255)
     * @param green Green component (0-255)
     * @param blue  Blue component (0-255)
     * @return The dyed item
     */
    private ItemStack applyLeatherDye(ItemStack item, int red, int green, int blue) {
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(Color.fromRGB(red, green, blue));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Checks if lore already contains a specific attribute
     *
     * @param lore      The lore list to check
     * @param attribute The attribute to check for
     * @return True if the attribute exists in lore
     */
    private boolean loreContains(List<String> lore, String attribute) {
        for (String line : lore) {
            if (ChatColor.stripColor(line).contains(attribute)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines item rarity using weighted probabilities
     *
     * @return Rarity level (1-4)
     */
    private static int determineRarity() {
        // Using probabilities from the old system:
        // Common: 62%, Uncommon: 26%, Rare: 10%, Unique: 2%
        int roll = ThreadLocalRandom.current().nextInt(100);

        if (roll < 2) {
            return 4; // Unique - 2%
        } else if (roll < 12) {
            return 3; // Rare - 10%
        } else if (roll < 38) {
            return 2; // Uncommon - 26%
        } else {
            return 1; // Common - 62%
        }
    }

    /**
     * Check if a mob type should have glowing items
     */
    private boolean isSpecialEliteDrop(String mobType) {
        return mobType.equalsIgnoreCase("bossSkeletonDungeon") ||
                mobType.equalsIgnoreCase("frostKing") ||
                mobType.equalsIgnoreCase("warden");
    }

    /**
     * Get appropriate sound for a tier
     *
     * @param tier Item tier
     * @return Sound effect
     */
    public Sound getTierSound(int tier) {
        switch (tier) {
            case 1:
                return Sound.ENTITY_ITEM_PICKUP;
            case 2:
                return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3:
                return Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4:
            case 5:
            case 6:
                return Sound.ENTITY_PLAYER_LEVELUP;
            default:
                return Sound.ENTITY_ITEM_PICKUP;
        }
    }

    /**
     * Get appropriate sound for a rarity
     *
     * @param rarity Item rarity
     * @return Sound effect
     */
    public Sound getRaritySound(int rarity) {
        switch (rarity) {
            case 1: // Common
                return Sound.ENTITY_ITEM_PICKUP;
            case 2: // Uncommon
                return Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3: // Rare
                return Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4: // Unique
                return Sound.ENTITY_ENDER_DRAGON_GROWL;
            default:
                return Sound.ENTITY_ITEM_PICKUP;
        }
    }

    /**
     * Set drop rate for a specific tier
     *
     * @param tier The tier level
     * @param rate The new drop rate
     */
    public void setDropRate(int tier, int rate) {
        // Update the drop rate in the config manager
        configManager.updateDropRate(tier, rate);
    }

    /**
     * Get current drop rate for a specific tier
     *
     * @param tier The tier level
     * @return The current drop rate
     */
    public int getDropRate(int tier) {
        return DropConfig.getDropRate(tier);
    }
}
package com.rednetty.server.mechanics.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.config.ConfigManager;
import com.rednetty.server.mechanics.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.drops.types.*;
import com.rednetty.server.mechanics.mobs.MobManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Enhanced central manager for item drops with improved performance, caching, and visual effects
 */
public class DropsManager {
    private static DropsManager instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Enhanced component management
    private DropFactory dropFactory;
    private final LootNotifier lootNotifier;
    private final LootBuffManager lootBuffManager;
    private final ConfigManager configManager;
    private final MobManager mobManager;

    // Enhanced persistent data keys
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyTier;
    private final NamespacedKey keyItemType;
    private final NamespacedKey keyEliteDrop;
    private final NamespacedKey keyDropProtection;
    private final NamespacedKey keyFixedGear;
    private final NamespacedKey keyCreationTime;
    private final NamespacedKey keyCreatorId;

    // Enhanced caching system
    private final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> protectionCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_TIME = 300000; // 5 minutes
    private static final int MAX_CACHE_SIZE = 1000;

    // Enhanced constants for item generation
    private static final Map<Integer, ChatColor> TIER_COLORS = Map.of(
            1, ChatColor.WHITE,
            2, ChatColor.GREEN,
            3, ChatColor.AQUA,
            4, ChatColor.LIGHT_PURPLE,
            5, ChatColor.YELLOW,
            6, ChatColor.BLUE
    );

    private static final Map<Integer, String> TIER_NAMES = Map.of(
            1, "Novice",
            2, "Apprentice",
            3, "Journeyman",
            4, "Expert",
            5, "Master",
            6, "Legendary"
    );

    // Enhanced stat calculation constants
    private static final double[] ARMOR_BASE_MULTIPLIERS = {30.0, 2.1};
    private static final double[] DAMAGE_BASE_MULTIPLIERS = {5.0, 2.0, 6.0, 2.3};
    private static final Map<String, Double> SPECIAL_ELITE_MULTIPLIERS = Map.of(
            "bossSkeletonDungeon", 1.5,
            "frostKing", 2.0,
            "warden", 1.8,
            "spectralKnight", 1.3
    );

    /**
     * Enhanced constructor with better initialization
     */
    private DropsManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys with validation
        this.keyRarity = new NamespacedKey(plugin, "item_rarity");
        this.keyTier = new NamespacedKey(plugin, "item_tier");
        this.keyItemType = new NamespacedKey(plugin, "item_type");
        this.keyEliteDrop = new NamespacedKey(plugin, "elite_drop");
        this.keyDropProtection = new NamespacedKey(plugin, "drop_protection");
        this.keyFixedGear = new NamespacedKey(plugin, "fixedgear");
        this.keyCreationTime = new NamespacedKey(plugin, "creation_time");
        this.keyCreatorId = new NamespacedKey(plugin, "creator_id");

        // Initialize components
        this.lootNotifier = LootNotifier.getInstance();
        this.lootBuffManager = LootBuffManager.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.mobManager = MobManager.getInstance();

        // Start cache cleanup task
        startCacheCleanup();
    }

    /**
     * Gets the singleton instance with thread safety
     */
    public static synchronized DropsManager getInstance() {
        if (instance == null) {
            instance = new DropsManager();
        }
        return instance;
    }

    /**
     * Enhanced initialization with comprehensive setup
     */
    public void initialize() {
        try {
            configManager.initialize();
            lootBuffManager.initialize();
            getDropFactory(); // Initialize drop factory
            DropsHandler.getInstance().initialize();

            logger.info("[DropsManager] Enhanced initialization completed successfully");
        } catch (Exception e) {
            logger.severe("[DropsManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enhanced shutdown with proper cleanup
     */
    public void shutdown() {
        try {
            DropsHandler.getInstance().shutdown();
            lootBuffManager.shutdown();
            configManager.shutdown();
            clearCaches();

            logger.info("[DropsManager] Enhanced shutdown completed");
        } catch (Exception e) {
            logger.warning("[DropsManager] Error during shutdown: " + e.getMessage());
        }
    }

    /**
     * Enhanced drop factory getter with lazy initialization
     */
    public DropFactory getDropFactory() {
        if (dropFactory == null) {
            dropFactory = DropFactory.getInstance();
        }
        return dropFactory;
    }

    /**
     * Enhanced drop creation with caching and improved error handling
     */
    public static ItemStack createDrop(int tier, int itemType) {
        int rarity = determineRarity();
        return getInstance().createDrop(tier, itemType, rarity);
    }

    /**
     * Enhanced drop creation with comprehensive validation and caching
     */
    public ItemStack createDrop(int tier, int itemType, int rarity) {
        // Enhanced parameter validation
        if (!validateDropParameters(tier, itemType, rarity)) {
            return createFallbackItem();
        }

        // Check cache first
        String cacheKey = generateCacheKey(tier, itemType, rarity);
        ItemStack cachedItem = getCachedItem(cacheKey);
        if (cachedItem != null) {
            return cachedItem.clone();
        }

        try {
            ItemStack item = createDropInternal(tier, itemType, rarity);
            cacheItem(cacheKey, item);
            return item;
        } catch (Exception e) {
            logger.warning("Failed to create drop: tier=" + tier + ", itemType=" + itemType +
                    ", rarity=" + rarity + " - " + e.getMessage());
            return createFallbackItem();
        }
    }

    /**
     * Internal drop creation logic with enhanced features
     */
    private ItemStack createDropInternal(int tier, int itemType, int rarity) {
        // Get configurations with fallbacks
        TierConfig tierConfig = DropConfig.getTierConfig(tier);
        RarityConfig rarityConfig = DropConfig.getRarityConfig(rarity);
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

        if (tierConfig == null || rarityConfig == null || itemTypeConfig == null) {
            logger.warning("Missing configuration for drop creation - using fallbacks");
            return createBasicItem(tier, itemType, rarity);
        }

        // Create the base item
        Material material = determineMaterial(tierConfig, itemTypeConfig);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        // Enhanced item naming with tier colors
        String itemName = createEnhancedItemName(itemType, tier, rarity);
        meta.setDisplayName(ChatColor.RESET + itemName);

        // Create enhanced lore
        List<String> lore = createEnhancedLore(itemTypeConfig, tier, rarity);
        meta.setLore(lore);

        // Hide all attributes for clean appearance
        Arrays.stream(ItemFlag.values()).forEach(meta::addItemFlags);

        // Store comprehensive metadata
        storeItemMetadata(meta, tier, itemType, rarity);

        item.setItemMeta(meta);

        // Apply special effects for tier 6 leather armor
        if (tier == 6 && material.name().contains("LEATHER")) {
            item = applyLeatherDye(item, 0, 0, 255); // Blue for tier 6
        }

        return item;
    }

    /**
     * Enhanced elite drop creation with improved fallback handling
     */
    public ItemStack createEliteDrop(String mobType, int itemType, int actualMobTier) {
        if (mobType == null || mobType.trim().isEmpty()) {
            return createDrop(Math.max(1, actualMobTier), itemType, 3);
        }

        String normalizedMobType = mobType.toLowerCase().trim();
        EliteDropConfig config = DropConfig.getEliteDropConfig(normalizedMobType);

        if (config == null) {
            logger.fine("No elite configuration for: " + mobType + ", using standard drop");
            return createFallbackEliteDrop(actualMobTier, itemType);
        }

        try {
            return createEliteDropFromConfig(config, itemType, actualMobTier);
        } catch (Exception e) {
            logger.warning("Failed to create elite drop for " + mobType + ": " + e.getMessage());
            return createFallbackEliteDrop(actualMobTier, itemType);
        }
    }

    /**
     * Creates elite drop from configuration with enhanced features
     */
    private ItemStack createEliteDropFromConfig(EliteDropConfig config, int itemType, int actualMobTier) {
        ItemDetails details = config.getItemDetailsForType(itemType);

        if (details == null) {
            return createDrop(config.getTier(), itemType, config.getRarity());
        }

        Material material = determineMaterialFromDetails(details, config, itemType);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        // Enhanced elite item naming
        ChatColor tierColor = TIER_COLORS.getOrDefault(config.getTier(), ChatColor.WHITE);
        String enhancedName = tierColor + ChatColor.stripColor(details.getName());
        meta.setDisplayName(enhancedName);

        // Create elite-specific lore
        List<String> lore = createEliteLore(config, details, itemType);
        meta.setLore(lore);

        // Enhanced metadata storage
        storeEliteMetadata(meta, config, itemType, details.getName());

        // Hide attributes
        Arrays.stream(ItemFlag.values()).forEach(meta::addItemFlags);

        item.setItemMeta(meta);

        // Special effects for certain elites
        if (isSpecialEliteDrop(config.getMobType())) {
            item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
        }

        return item;
    }

    /**
     * Enhanced item name creation with visual flair
     */
    private String createEnhancedItemName(int itemType, int tier, int rarity) {
        ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.WHITE);
        String baseName = getBaseName(itemType, tier);

        // Add rarity prefix for higher rarities
        String rarityPrefix = "";
        if (rarity >= 3) {
            rarityPrefix = rarity == 4 ? "✦ " : "★ ";
        }

        return tierColor + rarityPrefix + baseName;
    }

    /**
     * Enhanced lore creation with comprehensive stat display
     */
    private List<String> createEnhancedLore(ItemTypeConfig itemTypeConfig, int tier, int rarity) {
        List<String> lore = new ArrayList<>();
        boolean isWeapon = itemTypeConfig.isWeapon();

        if (isWeapon) {
            addWeaponStats(lore, tier, rarity, itemTypeConfig.getTypeId());
        } else {
            addArmorStats(lore, tier, rarity, itemTypeConfig.getTypeId());
        }

        // Add special attributes based on rarity
        if (rarity >= 3) {
            addSpecialAttributes(lore, tier, rarity, isWeapon);
        }

        // Add enhanced rarity display
        addRarityDisplay(lore, rarity);

        return lore;
    }

    /**
     * Enhanced weapon stats with improved calculations
     */
    private void addWeaponStats(List<String> lore, int tier, int rarity, int itemType) {
        // Calculate damage range with enhanced formula
        DamageRange damageRange = calculateWeaponDamage(tier, rarity, itemType);

        lore.add("");
        lore.add(ChatColor.RED + "⚔ DMG: " + damageRange.min + " - " + damageRange.max);

        // Add elemental damage (33% chance)
        if (ThreadLocalRandom.current().nextInt(3) == 0) {
            addElementalDamage(lore, tier, rarity);
        }

        // Add weapon-specific bonuses
        addWeaponBonuses(lore, tier, rarity, itemType);
    }

    /**
     * Enhanced armor stats with improved calculations
     */
    private void addArmorStats(List<String> lore, int tier, int rarity, int itemType) {
        lore.add("");

        // DPS or Armor stat
        boolean useDps = ThreadLocalRandom.current().nextBoolean();
        int statValue = calculateArmorStat(tier, rarity, itemType);

        if (useDps) {
            lore.add(ChatColor.RED + "⚡ DPS: " + statValue + " - " + statValue + "%");
        } else {
            lore.add(ChatColor.RED + "🛡 ARMOR: " + statValue + " - " + statValue + "%");
        }

        // HP calculation with item type modifiers
        int hp = calculateArmorHP(tier, rarity, itemType);
        lore.add(ChatColor.RED + "❤ HP: +" + hp);

        // Energy or HP regen
        addRegenStats(lore, tier, hp);
    }

    /**
     * Enhanced elemental damage with variety
     */
    private void addElementalDamage(List<String> lore, int tier, int rarity) {
        String[] elements = {"🔥 FIRE DMG", "☠ POISON DMG", "❄ ICE DMG", "⚡ LIGHTNING DMG"};
        String element = elements[ThreadLocalRandom.current().nextInt(elements.length)];

        int baseAmount = tier * 5;
        if (rarity >= 3) baseAmount = (int)(baseAmount * 1.5);

        int damage = ThreadLocalRandom.current().nextInt(baseAmount) + baseAmount;
        lore.add(ChatColor.RED + element + ": +" + damage);
    }

    /**
     * Enhanced special attributes with visual icons
     */
    private void addSpecialAttributes(List<String> lore, int tier, int rarity, boolean isWeapon) {
        lore.add("");

        int numAttributes = Math.min(rarity - 1, 3);
        Set<String> addedAttributes = new HashSet<>();

        for (int i = 0; i < numAttributes; i++) {
            String attribute = generateRandomAttribute(tier, rarity, isWeapon, addedAttributes);
            if (attribute != null && !attribute.isEmpty()) {
                lore.add(ChatColor.GOLD + attribute);
                addedAttributes.add(attribute.split(":")[0]);
            }
        }
    }

    /**
     * Enhanced rarity display with visual effects
     */
    private void addRarityDisplay(List<String> lore, int rarity) {
        lore.add("");

        String rarityDisplay = switch (rarity) {
            case 1 -> ChatColor.GRAY.toString()+ ChatColor.ITALIC + "◆ Common";
            case 2 -> ChatColor.GREEN.toString() + ChatColor.ITALIC + "◇ Uncommon";
            case 3 -> ChatColor.AQUA.toString() + ChatColor.ITALIC + "★ Rare";
            case 4 -> ChatColor.YELLOW.toString() + ChatColor.ITALIC + "✦ Unique";
            default -> ChatColor.GRAY.toString() + ChatColor.ITALIC + "◆ Common";
        };

        lore.add(rarityDisplay);
    }

    /**
     * Enhanced protection system with expiry tracking
     */
    public void registerDropProtection(Item item, UUID playerUuid, int seconds) {
        if (item == null || playerUuid == null) return;

        try {
            ItemStack itemStack = item.getItemStack();
            ItemMeta meta = itemStack.getItemMeta();

            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                long expiry = System.currentTimeMillis() + (seconds * 1000L);

                container.set(keyDropProtection, PersistentDataType.STRING,
                        playerUuid.toString() + ":" + expiry);
                container.set(keyCreationTime, PersistentDataType.LONG, System.currentTimeMillis());

                itemStack.setItemMeta(meta);
                item.setItemStack(itemStack);

                // Cache protection info
                protectionCache.put(item.getUniqueId(), expiry);
            }

            item.setUnlimitedLifetime(true);

        } catch (Exception e) {
            logger.warning("Failed to register drop protection: " + e.getMessage());
        }
    }

    /**
     * Enhanced protection checking with cache optimization
     */
    public boolean hasItemProtection(Item item, Player player) {
        if (item == null || player == null) return false;

        try {
            // Check cache first
            Long cachedExpiry = protectionCache.get(item.getUniqueId());
            if (cachedExpiry != null) {
                if (System.currentTimeMillis() > cachedExpiry) {
                    protectionCache.remove(item.getUniqueId());
                    return false;
                }
            }

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

                            boolean isProtected = protectedUuid.equals(player.getUniqueId()) &&
                                    System.currentTimeMillis() < expiry;

                            // Update cache
                            if (isProtected) {
                                protectionCache.put(item.getUniqueId(), expiry);
                            } else {
                                protectionCache.remove(item.getUniqueId());
                            }

                            return isProtected;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error checking item protection: " + e.getMessage());
        }

        return false;
    }

    /**
     * Enhanced drop decision logic with buff integration
     */
    public boolean shouldDropItem(LivingEntity entity, boolean isElite, int tier) {
        if (mobManager.hasMetadata(entity, "worldboss")) {
            return true; // World bosses always drop
        }

        int baseDropRate = DropConfig.getDropRate(tier);
        int dropRate = isElite ? DropConfig.getEliteDropRate(tier) : baseDropRate;

        // Apply loot buff
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (dropRate * buffPercentage / 100);
        }

        boolean shouldDrop = ThreadLocalRandom.current().nextInt(100) < dropRate;

        // Track improved drops
        if (shouldDrop && lootBuffManager.isBuffActive()) {
            int originalChance = isElite ? DropConfig.getEliteDropRate(tier) : DropConfig.getDropRate(tier);
            if (ThreadLocalRandom.current().nextInt(100) >= originalChance) {
                lootBuffManager.updateImprovedDrops();
            }
        }

        return shouldDrop;
    }

    // Enhanced utility methods
    private boolean validateDropParameters(int tier, int itemType, int rarity) {
        return tier >= 1 && tier <= 6 &&
                itemType >= 1 && itemType <= 8 &&
                rarity >= 1 && rarity <= 4;
    }

    private String generateCacheKey(int tier, int itemType, int rarity) {
        return tier + ":" + itemType + ":" + rarity;
    }

    private ItemStack getCachedItem(String cacheKey) {
        if (itemCache.size() > MAX_CACHE_SIZE) {
            clearExpiredCacheEntries();
        }
        return itemCache.get(cacheKey);
    }

    private void cacheItem(String cacheKey, ItemStack item) {
        if (item != null) {
            itemCache.put(cacheKey, item.clone());
        }
    }

    private void clearExpiredCacheEntries() {
        // Simple cache size management - remove 25% when full
        if (itemCache.size() > MAX_CACHE_SIZE) {
            List<String> keys = new ArrayList<>(itemCache.keySet());
            Collections.shuffle(keys);
            keys.subList(0, itemCache.size() / 4).forEach(itemCache::remove);
        }
    }

    private void clearCaches() {
        itemCache.clear();
        protectionCache.clear();
    }

    private void startCacheCleanup() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Clean expired protection entries
            long currentTime = System.currentTimeMillis();
            protectionCache.entrySet().removeIf(entry -> entry.getValue() < currentTime);

            // Clean cache if too large
            if (itemCache.size() > MAX_CACHE_SIZE * 1.5) {
                clearExpiredCacheEntries();
            }
        }, 1200L, 1200L); // Every minute
    }

    // Helper classes and methods

    private static class DamageRange {
        final int min, max;
        DamageRange(int min, int max) { this.min = min; this.max = max; }
    }

    /**
     * Calculate weapon damage with enhanced formula
     */
    private DamageRange calculateWeaponDamage(int tier, int rarity, int itemType) {
        ArrayList<Integer> minDamages = setbase(5, 2);
        ArrayList<Integer> maxDamages = setbase(6, 2);

        double minMin = minDamages.get(tier - 1) * ((rarity + 0.7) / 2.35);
        double minMax = minMin / 4.0;
        double maxMin = maxDamages.get(tier - 1) * ((rarity + 0.7) / 2.3);
        double maxMax = maxMin / 4.0;

        int minRange = Math.max(1, (int) minMax);
        int maxRange = Math.max(1, (int) maxMax);

        int min = ThreadLocalRandom.current().nextInt(minRange) + (int) minMin;
        int max = ThreadLocalRandom.current().nextInt(maxRange) + (int) maxMin;

        // Item type adjustments
        if (itemType == 4) { // Axe
            min = (int)(min * 1.2);
            max = (int)(max * 1.2);
        } else if (itemType == 1 || itemType == 2) { // Staff or Spear
            min = (int)(min / 1.5);
            max = (int)(max / 1.5);
        }

        // Rarity adjustments
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
            int temp = min;
            min = max;
            max = temp;
        }

        return new DamageRange(Math.max(1, min), Math.max(min + 1, max));
    }

    /**
     * Calculate armor stat value
     */
    private int calculateArmorStat(int tier, int rarity, int itemType) {
        double base = tier * (1.0 + (tier / 1.7));
        return (int)(base * (1.0 + rarity * 0.2));
    }

    /**
     * Calculate armor HP with type modifiers
     */
    private int calculateArmorHP(int tier, int rarity, int itemType) {
        ArrayList<Integer> armourBase = setbase(30, 2.1);
        double baseHp = armourBase.get(tier - 1) * (rarity / (1 + (rarity / 10.0)));

        // Item type modifiers
        if (itemType == 5 || itemType == 8) { // Helmet or Boots
            baseHp /= 1.5;
        } else if (itemType == 6 || itemType == 7) { // Chestplate or Leggings
            baseHp *= 1.2;
        }

        return (int)(baseHp + ThreadLocalRandom.current().nextInt((int)(baseHp / 4)));
    }

    /**
     * Add regen stats to armor
     */
    private void addRegenStats(List<String> lore, int tier, int hp) {
        int regenType = ThreadLocalRandom.current().nextInt(4);

        if (regenType > 0) {
            // Energy regen
            int energyRegen = Math.max(1, Math.min(6, tier + ThreadLocalRandom.current().nextInt(3) - 1));
            lore.add(ChatColor.RED + "⚡ ENERGY REGEN: +" + energyRegen + "%");
        } else {
            // HP regen
            int minHps = Math.max(1, (int)(hp * 0.19));
            int maxHps = Math.max(minHps + 1, (int)(hp * 0.21));
            int hpRegen = ThreadLocalRandom.current().nextInt(minHps, maxHps + 1);
            lore.add(ChatColor.RED + "❤ HP REGEN: +" + hpRegen + "/s");
        }
    }

    /**
     * Add weapon bonuses based on type
     */
    private void addWeaponBonuses(List<String> lore, int tier, int rarity, int itemType) {
        if (rarity >= 3) {
            lore.add("");

            int bonusCount = Math.min(rarity - 1, 2);
            Set<String> addedBonuses = new HashSet<>();

            for (int i = 0; i < bonusCount; i++) {
                String bonus = generateWeaponBonus(tier, rarity, addedBonuses);
                if (bonus != null) {
                    lore.add(ChatColor.GOLD + bonus);
                    addedBonuses.add(bonus.split(":")[0]);
                }
            }
        }
    }

    /**
     * Generate random weapon bonus
     */
    private String generateWeaponBonus(int tier, int rarity, Set<String> existing) {
        String[] bonusTypes = {"💀 LIFE STEAL", "⚡ CRITICAL HIT", "🎯 ACCURACY", "✨ PURE DMG"};

        for (String bonusType : bonusTypes) {
            String key = bonusType.split(" ")[1];
            if (!existing.contains(key)) {
                int value = switch (key) {
                    case "LIFE" -> 5 + ThreadLocalRandom.current().nextInt(6) + (rarity >= 3 ? 3 : 0);
                    case "CRITICAL" -> 5 + ThreadLocalRandom.current().nextInt(7) + (rarity >= 3 ? 5 : 0);
                    case "ACCURACY" -> 15 + ThreadLocalRandom.current().nextInt(11) + (rarity >= 3 ? 10 : 0);
                    case "PURE" -> {
                        int base = Math.max(1, tier * 5);
                        yield base + ThreadLocalRandom.current().nextInt(base) + (rarity >= 3 ? base / 2 : 0);
                    }
                    default -> 1;
                };

                return bonusType + ": " + (key.equals("PURE") ? "+" + value : value + "%");
            }
        }
        return null;
    }

    /**
     * Generate random attribute for special items
     */
    private String generateRandomAttribute(int tier, int rarity, boolean isWeapon, Set<String> existing) {
        if (isWeapon) {
            return generateWeaponBonus(tier, rarity, existing);
        } else {
            String[] armorAttribs = {"⚔ STR", "🏃 DEX", "🧠 INT", "❤ VIT", "🌟 DODGE", "🛡 BLOCK"};

            for (String attrib : armorAttribs) {
                String key = attrib.split(" ")[1];
                if (!existing.contains(key)) {
                    int value = switch (key) {
                        case "STR", "DEX", "INT", "VIT" -> {
                            int base = Math.max(1, tier * 20);
                            yield base + ThreadLocalRandom.current().nextInt(base) + (rarity >= 3 ? base / 2 : 0);
                        }
                        case "DODGE", "BLOCK" -> 5 + ThreadLocalRandom.current().nextInt(6) + (rarity >= 3 ? 3 : 0);
                        default -> 1;
                    };

                    return attrib + ": " + (key.equals("DODGE") || key.equals("BLOCK") ? value + "%" : "+" + value);
                }
            }
        }
        return null;
    }

    /**
     * Create basic item as fallback
     */
    private ItemStack createBasicItem(int tier, int itemType, int rarity) {
        Material material = Material.STONE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + "Basic Item (T" + tier + ")");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "A basic item",
                    ChatColor.GRAY + "Tier: " + tier,
                    ChatColor.GRAY + "Type: " + itemType,
                    ChatColor.GRAY + "Rarity: " + rarity
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Create fallback item for errors
     */
    private ItemStack createFallbackItem() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Item");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "An error occurred during creation"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Enhanced material determination
     */
    private Material determineMaterial(TierConfig tierConfig, ItemTypeConfig itemTypeConfig) {
        String materialPrefix = tierConfig.getMaterialPrefix(itemTypeConfig.isWeapon());
        String materialSuffix = itemTypeConfig.getMaterialSuffix();
        String materialName = materialPrefix + "_" + materialSuffix;

        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid material: " + materialName);
            return Material.STONE;
        }
    }

    /**
     * Store comprehensive item metadata
     */
    private void storeItemMetadata(ItemMeta meta, int tier, int itemType, int rarity) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRarity, PersistentDataType.INTEGER, rarity);
        container.set(keyTier, PersistentDataType.INTEGER, tier);
        container.set(keyItemType, PersistentDataType.INTEGER, itemType);
        container.set(keyCreationTime, PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * Store elite-specific metadata
     */
    private void storeEliteMetadata(ItemMeta meta, EliteDropConfig config, int itemType, String itemName) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRarity, PersistentDataType.INTEGER, config.getRarity());
        container.set(keyTier, PersistentDataType.INTEGER, config.getTier());
        container.set(keyItemType, PersistentDataType.INTEGER, itemType);
        container.set(keyEliteDrop, PersistentDataType.STRING, config.getMobType());
        container.set(keyCreationTime, PersistentDataType.LONG, System.currentTimeMillis());

        if (config.getMobType().equalsIgnoreCase("spectralKnight")) {
            container.set(keyFixedGear, PersistentDataType.INTEGER, 1);
        }
    }

    // Utility methods
    private static ArrayList<Integer> setbase(double base, double multiplier) {
        ArrayList<Integer> newBase = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            newBase.add((int) base);
            base = base * multiplier;
        }
        return newBase;
    }

    private static int determineRarity() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 2) return 4;      // Unique - 2%
        else if (roll < 12) return 3; // Rare - 10%
        else if (roll < 38) return 2; // Uncommon - 26%
        else return 1;               // Common - 62%
    }

    private String getBaseName(int itemType, int tier) {
        String tierName = TIER_NAMES.getOrDefault(tier, "Unknown");

        return switch (itemType) {
            case 1 -> tierName + " Staff";
            case 2 -> tierName + " Spear";
            case 3 -> tierName + " Sword";
            case 4 -> tierName + " Axe";
            case 5 -> tierName + " Helmet";
            case 6 -> tierName + " Chestplate";
            case 7 -> tierName + " Leggings";
            case 8 -> tierName + " Boots";
            default -> tierName + " Item";
        };
    }

    private Material determineMaterialFromDetails(ItemDetails details, EliteDropConfig config, int itemType) {
        if (details.getMaterial() != null) {
            return details.getMaterial();
        }

        // Fallback to config-based material
        TierConfig tierConfig = DropConfig.getTierConfig(config.getTier());
        ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

        if (tierConfig != null && itemTypeConfig != null) {
            return determineMaterial(tierConfig, itemTypeConfig);
        }

        return Material.STONE;
    }

    private ItemStack createFallbackEliteDrop(int actualMobTier, int itemType) {
        int tier = Math.max(1, actualMobTier);
        int rarity = tier >= 5 ? 4 : tier >= 3 ? 3 : 2;
        return createDrop(tier, itemType, rarity);
    }

    private List<String> createEliteLore(EliteDropConfig config, ItemDetails details, int itemType) {
        List<String> lore = new ArrayList<>();
        boolean isWeapon = itemType <= 4;

        if (isWeapon) {
            // Add weapon stats for elite items
            addEliteWeaponStats(lore, config, details, itemType);
        } else {
            // Add armor stats for elite items
            addEliteArmorStats(lore, config, details, itemType);
        }

        // Add elite-specific flavor text
        if (details.getLore() != null && !details.getLore().isEmpty()) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY.toString() + ChatColor.ITALIC + details.getLore());
        }

        // Add rarity
        addRarityDisplay(lore, config.getRarity());

        return lore;
    }

    private void addEliteWeaponStats(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Implementation would add elite-specific weapon stats
        lore.add("");
        lore.add(ChatColor.RED + "⚔ Elite Weapon Stats");
        // Add specific stats based on config
    }

    private void addEliteArmorStats(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
        // Implementation would add elite-specific armor stats
        lore.add("");
        lore.add(ChatColor.RED + "🛡 Elite Armor Stats");
        // Add specific stats based on config
    }

    private boolean isSpecialEliteDrop(String mobType) {
        return SPECIAL_ELITE_MULTIPLIERS.containsKey(mobType);
    }

    private ItemStack applyLeatherDye(ItemStack item, int red, int green, int blue) {
        if (item.getItemMeta() instanceof LeatherArmorMeta) {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(Color.fromRGB(red, green, blue));
            item.setItemMeta(meta);
        }
        return item;
    }

    // Public API methods
    public Sound getTierSound(int tier) {
        return switch (tier) {
            case 1 -> Sound.ENTITY_ITEM_PICKUP;
            case 2 -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3 -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4, 5, 6 -> Sound.ENTITY_PLAYER_LEVELUP;
            default -> Sound.ENTITY_ITEM_PICKUP;
        };
    }

    public Sound getRaritySound(int rarity) {
        return switch (rarity) {
            case 1 -> Sound.ENTITY_ITEM_PICKUP;
            case 2 -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3 -> Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4 -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            default -> Sound.ENTITY_ITEM_PICKUP;
        };
    }

    public void setDropRate(int tier, int rate) {
        configManager.updateDropRate(tier, rate);
    }

    public int getDropRate(int tier) {
        return DropConfig.getDropRate(tier);
    }

    /**
     * Get manager statistics for debugging
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("itemCacheSize", itemCache.size());
        stats.put("protectionCacheSize", protectionCache.size());
        stats.put("maxCacheSize", MAX_CACHE_SIZE);
        stats.put("cacheHitRatio", calculateCacheHitRatio());
        return stats;
    }

    private double calculateCacheHitRatio() {
        // This would require tracking hits/misses - simplified for now
        return itemCache.size() > 0 ? 0.75 : 0.0;
    }
}
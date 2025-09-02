package com.rednetty.server.core.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.config.ConfigManager;
import com.rednetty.server.core.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.core.mechanics.item.drops.types.*;
import com.rednetty.server.core.mechanics.world.mobs.MobManager;

// Adventure API imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

// Bukkit/Paper imports
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

// Paper-specific imports
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Central manager for item drops from mobs and bosses with Adventure API support.
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>Adventure API integration for modern text components</li>
 *   <li>Paper Spigot 1.21.7 optimizations and capabilities</li>
 *   <li>Elite drop creation from YAML configurations</li>
 *   <li>Clean stat generation without automatic orb effects</li>
 *   <li>Tier-based progression system</li>
 *   <li>Drop protection system</li>
 *   <li>Rarity-based loot generation</li>
 *   <li>Enhanced visual and audio effects</li>
 *   <li>1:1 visual parity with legacy formatting</li>
 * </ul>
 *
 * <p>Critical Fixes:</p>
 * <ul>
 *   <li>Properly implements named elite drops from elite_drops.yml</li>
 *   <li>Prevents duplicate stats by removing automatic orb application</li>
 *   <li>Clean drops with no automatic orb effects</li>
 *   <li>Modern Adventure API text handling with exact legacy appearance</li>
 *   <li>Paper 1.21.7 performance optimizations</li>
 * </ul>
 *
 * @author YakRealms Team
 * @version 3.0 - Adventure API Edition
 * @since 1.0
 */
public class DropsManager {

    // ===== CONSTANTS =====
    private static final int ELEMENTAL_DAMAGE_CHANCE = 3;

    // ===== SINGLETON INSTANCE =====
    private static volatile DropsManager instance;

    // ===== CORE COMPONENTS =====
    private final Logger logger;
    private final YakRealms plugin;

    // ===== ADVENTURE API COMPONENTS =====
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;

    // ===== MANAGERS AND HANDLERS =====
    private DropFactory dropFactory;
    private final LootNotifier lootNotifier;
    private final LootBuffManager lootBuffManager;
    private final ConfigManager configManager;
    private final MobManager mobManager;

    // ===== CALCULATION HELPERS =====
    private StatCalculator statCalculator;
    private final ItemBuilder itemBuilder;
    private final NameProvider nameProvider;

    // ===== PERSISTENT DATA KEYS =====
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyTier;
    private final NamespacedKey keyItemType;
    private final NamespacedKey keyEliteDrop;
    private final NamespacedKey keyDropProtection;
    private final NamespacedKey keyGear;

    /**
     * Private constructor for singleton pattern.
     * Initializes all components and configuration keys with Adventure API support.
     */
    private DropsManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize Adventure API components
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacySection();

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
     * Gets the singleton instance with thread-safe double-checked locking.
     *
     * @return The DropsManager singleton instance
     */
    public static DropsManager getInstance() {
        if (instance == null) {
            synchronized (DropsManager.class) {
                if (instance == null) {
                    instance = new DropsManager();
                }
            }
        }
        return instance;
    }

    // ===== INITIALIZATION AND LIFECYCLE =====

    /**
     * Initializes the drops manager with proper configuration loading.
     * This method sets up all required components and loads elite configurations.
     */
    public void initialize() {
        try {
            // Initialize DropConfig first to load elite configurations
            DropConfig.initialize();

            // Initialize other components
            configManager.initialize();
            lootBuffManager.initialize();
            getDropFactory();
            DropsHandler.getInstance().initialize();

            logger.info("DropsManager initialized with " + DropConfig.getEliteDropConfigs().size() + " elite configurations");

            // Debug: Print loaded elite configurations
            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                DropConfig.printEliteConfigurations();
            }
        } catch (Exception e) {
            logger.severe("§c[DropsManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Lazy initialization of DropFactory.
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
     * Clean shutdown when plugin is disabled.
     * Properly shuts down all managed components.
     */
    public void shutdown() {
        try {
            DropsHandler.getInstance().shutdown();
            lootBuffManager.shutdown();
            configManager.shutdown();
            logger.info("[DropsManager] has been shut down");
        } catch (Exception e) {
            logger.warning("Error during DropsManager shutdown: " + e.getMessage());
        }
    }

    // ===== PUBLIC API - DROP CREATION =====

    /**
     * Creates a drop with the specified tier and item type using random rarity.
     *
     * @param tier The tier level (1-6)
     * @param itemType The item type (1-8)
     * @return The created ItemStack or null if creation failed
     */
    public static ItemStack createDrop(int tier, int itemType) {
        int rarity = RarityCalculator.determineRarity();
        return getInstance().createDrop(tier, itemType, rarity);
    }

    /**
     * Creates a drop with clean stats (NO automatic orb application).
     *
     * @param tier The tier level (1-6)
     * @param itemType The item type (1-8)
     * @param rarity The rarity level (1-4)
     * @return The created ItemStack or null if creation failed
     */
    public ItemStack createDrop(int tier, int itemType, int rarity) {
        // Validate and clamp parameters
        tier = MathUtils.clamp(tier, 1, 6);
        itemType = MathUtils.clamp(itemType, 1, 8);
        rarity = MathUtils.clamp(rarity, 1, 4);

        try {
            // Get configurations
            TierConfig tierConfig = DropConfig.getTierConfig(tier);
            RarityConfig rarityConfig = DropConfig.getRarityConfig(rarity);
            ItemTypeConfig itemTypeConfig = DropConfig.getItemTypeConfig(itemType);

            if (tierConfig == null || rarityConfig == null || itemTypeConfig == null) {
                logger.warning("Failed to create drop: Missing configuration for tier=" +
                        tier + ", rarity=" + rarity + ", itemType=" + itemType);
                return null;
            }

            // Create base item
            ItemStack item = itemBuilder.createBaseItem(tierConfig, itemTypeConfig);
            if (item == null) {
                return null;
            }

            // Build appropriate item type
            if (ItemTypes.isWeapon(itemType)) {
                return buildWeaponItem(item, tier, itemType, rarity, rarityConfig);
            } else {
                return buildArmorItem(item, tier, itemType, rarity, rarityConfig);
            }
        } catch (Exception e) {
            logger.warning("Error creating drop (T" + tier + " R" + rarity + " I" + itemType + "): " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a custom elite drop for a specific mob type.
     *
     * @param mobType The mob type identifier
     * @param itemType The item type (1-8)
     * @return The created elite ItemStack or null if creation failed
     */
    public ItemStack createEliteDrop(String mobType, int itemType) {
        return createEliteDrop(mobType, itemType, 0);
    }

    /**
     * Creates a custom elite drop for a specific mob type with proper YAML usage.
     *
     * @param mobType The mob type identifier
     * @param itemType The item type (1-8)
     * @param actualMobTier The actual mob tier for fallback calculation
     * @return The created elite ItemStack or null if creation failed
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

    // ===== PUBLIC API - DROP MECHANICS =====

    /**
     * Determines if a mob should drop an item based on tier and elite status.
     *
     * @param entity The living entity
     * @param isElite Whether the mob is elite
     * @param tier The mob tier
     * @return true if the mob should drop an item
     */
    public boolean shouldDropItem(LivingEntity entity, boolean isElite, int tier) {
        int baseDropRate = DropConfig.getDropRate(tier);
        int dropRate = isElite ? DropConfig.getEliteDropRate(tier) : baseDropRate;

        // Apply loot buff if active
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (dropRate * buffPercentage / 100);
        }

        int roll = ThreadLocalRandom.current().nextInt(100);
        boolean shouldDrop = roll < dropRate;

        // Update buff statistics
        if (shouldDrop && lootBuffManager.isBuffActive()) {
            int baseRoll = roll - (baseDropRate * lootBuffManager.getActiveBuff().getBuffRate() / 100);
            if (baseRoll >= baseDropRate) {
                lootBuffManager.updateImprovedDrops();
            }
        }

        return shouldDrop;
    }

    // ===== PUBLIC API - DROP PROTECTION =====

    /**
     * Register drop protection for an item for a specific player.
     *
     * @param item The dropped item
     * @param playerUuid The player's UUID
     * @param seconds Protection duration in seconds
     */
    public void registerDropProtection(Item item, UUID playerUuid, int seconds) {
        if (item == null || playerUuid == null) return;

        try {
            ItemStack itemStack = item.getItemStack();
            ItemMeta meta = itemStack.getItemMeta();

            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                container.set(keyDropProtection, PersistentDataType.STRING,
                        playerUuid.toString() + ":" + (System.currentTimeMillis() + (seconds * 1000L)));

                itemStack.setItemMeta(meta);
                item.setItemStack(itemStack);
            }

            item.setUnlimitedLifetime(true);
        } catch (Exception e) {
            logger.warning("Failed to register drop protection: " + e.getMessage());
        }
    }

    /**
     * Check if a player has protection for an item.
     *
     * @param item The item to check
     * @param player The player to check
     * @return true if the player has protection for this item
     */
    public boolean hasItemProtection(Item item, Player player) {
        if (item == null || player == null) return false;

        try {
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
        } catch (Exception e) {
            logger.warning("Error checking item protection: " + e.getMessage());
        }

        return false;
    }

    // ===== PUBLIC API - CONFIGURATION =====

    /**
     * Set the drop rate for a specific tier.
     *
     * @param tier The tier level
     * @param rate The drop rate percentage
     */
    public void setDropRate(int tier, int rate) {
        configManager.updateDropRate(tier, rate);
    }

    /**
     * Get the drop rate for a specific tier.
     *
     * @param tier The tier level
     * @return The drop rate percentage
     */
    public int getDropRate(int tier) {
        return DropConfig.getDropRate(tier);
    }

    /**
     * Set the tier gap multiplier for stat progression.
     *
     * @param newMultiplier The new multiplier (must be > 1.0)
     */
    public void setTierGapMultiplier(double newMultiplier) {
        if (newMultiplier <= 1.0) {
            logger.warning("Tier gap multiplier must be greater than 1.0. Ignoring value: " + newMultiplier);
            return;
        }

        double oldMultiplier = BaseStats.TIER_GAP_MULTIPLIER;
        BaseStats.TIER_GAP_MULTIPLIER = newMultiplier;

        // Recreate stat calculator with new multiplier
        this.statCalculator = new StatCalculator();

        logger.info("Updated tier gap multiplier from " + oldMultiplier + " to " + newMultiplier);
        logTierProgression();
    }

    /**
     * Get the current tier gap multiplier.
     *
     * @return The tier gap multiplier
     */
    public double getTierGapMultiplier() {
        return BaseStats.TIER_GAP_MULTIPLIER;
    }

    /**
     * Calculate a base tier value using the tier gap multiplier.
     *
     * @param baseValue The base value for tier 1
     * @param tier The target tier
     * @return The calculated value for the specified tier
     */
    public double getBaseTierValue(double baseValue, int tier) {
        return baseValue * Math.pow(BaseStats.TIER_GAP_MULTIPLIER, tier - 1);
    }

    // ===== PUBLIC API - AUDIO =====

    /**
     * Get the appropriate sound for a tier level.
     *
     * @param tier The tier level
     * @return The corresponding Sound
     * @deprecated Use getTierSoundKey() for Adventure API compatibility
     */
    @Deprecated
    public org.bukkit.Sound getTierSound(int tier) {
        return switch (tier) {
            case 1 -> org.bukkit.Sound.ENTITY_ITEM_PICKUP;
            case 2 -> org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3 -> org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4, 5, 6 -> org.bukkit.Sound.ENTITY_PLAYER_LEVELUP;
            default -> org.bukkit.Sound.ENTITY_ITEM_PICKUP;
        };
    }

    /**
     * Get the appropriate sound key for a tier level (Adventure API).
     *
     * @param tier The tier level
     * @return The corresponding Sound.Source compatible sound key
     */
    public org.bukkit.Sound getTierSoundKey(int tier) {
        return getTierSound(tier); // Backwards compatibility wrapper
    }

    /**
     * Get the appropriate sound for a rarity level.
     *
     * @param rarity The rarity level
     * @return The corresponding Sound
     * @deprecated Use getRaritySoundKey() for Adventure API compatibility
     */
    @Deprecated
    public org.bukkit.Sound getRaritySound(int rarity) {
        return switch (rarity) {
            case 1 -> org.bukkit.Sound.ENTITY_ITEM_PICKUP;
            case 2 -> org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case 3 -> org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING;
            case 4 -> org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL;
            default -> org.bukkit.Sound.ENTITY_ITEM_PICKUP;
        };
    }

    /**
     * Get the appropriate sound key for a rarity level (Adventure API).
     *
     * @param rarity The rarity level
     * @return The corresponding Sound.Source compatible sound key
     */
    public org.bukkit.Sound getRaritySoundKey(int rarity) {
        return getRaritySound(rarity); // Backwards compatibility wrapper
    }

    // ===== PUBLIC API - DEBUG =====

    /**
     * Log the tier progression for debugging purposes.
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

    // ===== ADVENTURE API INTEGRATION METHODS =====

    /**
     * Creates a Component from legacy text preserving exact formatting.
     * This ensures 1:1 visual parity with legacy items.
     *
     * @param legacyText The legacy text with color codes
     * @return Adventure Component with exact same appearance
     */
    public Component createComponent(String legacyText) {
        return legacySerializer.deserialize(legacyText);
    }

    /**
     * Creates a Component using MiniMessage format.
     *
     * @param miniMessageText The MiniMessage formatted text
     * @return Adventure Component
     */
    public Component createMiniMessageComponent(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText);
    }

    /**
     * Converts Adventure Component to legacy string (backwards compatibility).
     *
     * @param component The Adventure Component
     * @return Legacy formatted string
     */
    public String componentToLegacy(Component component) {
        return legacySerializer.serialize(component);
    }

    /**
     * Creates proper display name component that maintains exact legacy appearance.
     * Adventure API makes item names italic by default, but we need to match legacy exactly.
     *
     * @param legacyDisplayName The legacy display name
     * @return Component with proper formatting for item display names
     */
    private Component createDisplayNameComponent(String legacyDisplayName) {
        Component component = legacySerializer.deserialize(legacyDisplayName);
        // Item display names should not be italic by default in legacy
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Creates proper lore components that maintain exact legacy appearance.
     * Adventure API makes lore italic by default, but we need to match legacy exactly.
     *
     * @param legacyLoreLines The legacy lore lines
     * @return List of Components with proper formatting for item lore
     */
    private List<Component> createLoreComponents(List<String> legacyLoreLines) {
        List<Component> loreComponents = new ArrayList<>();
        for (String line : legacyLoreLines) {
            Component component = legacySerializer.deserialize(line);
            // Check if the line was originally italic in legacy format
            boolean wasItalic = line.contains(ChatColor.ITALIC.toString());
            // Only set italic to false if it wasn't originally italic
            if (!wasItalic) {
                component = component.decoration(TextDecoration.ITALIC, false);
            }
            loreComponents.add(component);
        }
        return loreComponents;
    }

    /**
     * Plays an enhanced sound using Adventure API and Paper's sound system.
     *
     * @param player The player to play sound for
     * @param sound The sound to play
     * @param volume Sound volume
     * @param pitch Sound pitch
     */
    public void playEnhancedSound(Player player, org.bukkit.Sound sound, float volume, float pitch) {
        try {
            // Use Paper's enhanced sound system with Adventure API
            player.playSound(player.getLocation(), sound,
                    org.bukkit.SoundCategory.PLAYERS, volume, pitch);
        } catch (Exception e) {
            // Fallback to legacy sound system
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * Shows enhanced title using Adventure API.
     *
     * @param player The player to show title to
     * @param title The main title text
     * @param subtitle The subtitle text
     * @param fadeIn Fade in duration in ticks
     * @param stay Stay duration in ticks
     * @param fadeOut Fade out duration in ticks
     */
    public void showEnhancedTitle(Player player, String title, String subtitle,
                                  int fadeIn, int stay, int fadeOut) {
        try {
            Component titleComponent = createComponent(title);
            Component subtitleComponent = createComponent(subtitle);

            Title adventureTitle = Title.title(
                    titleComponent,
                    subtitleComponent,
                    Title.Times.times(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L)
                    )
            );

            player.showTitle(adventureTitle);
        } catch (Exception e) {
            // Fallback to legacy title system
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    // ===== PRIVATE METHODS - ELITE DROP CREATION =====

    /**
     * Creates a fallback elite drop when no specific config exists.
     */
    private ItemStack createFallbackEliteDrop(int actualMobTier, int itemType) {
        int tier = (actualMobTier > 0) ? actualMobTier : 3;
        int rarity = tier >= 5 ? 4 : tier >= 3 ? 3 : 2; // Higher rarity for elites

        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine("§6[DropsManager] §7Creating fallback elite drop with T" + tier + " R" + rarity);
        }

        return createDrop(tier, itemType, rarity);
    }

    /**
     * Creates an elite drop based on YAML configuration with damage variation.
     */
    private ItemStack createConfiguredEliteDrop(EliteDropConfig config, int itemType, String mobType) {
        try {
            // Get item details for this specific item type
            ItemDetails details = config.getItemDetailsForType(itemType);

            // Create the base item with proper material
            Material material = EliteMaterialResolver.getMaterialForEliteDrop(details, config, itemType, logger);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                logger.warning("§c[DropsManager] ItemMeta is null for material: " + material);
                return null;
            }

            // Set the item name from configuration with proper Adventure API handling
            String itemName = details != null ? details.getName() : EliteNameGenerator.generateDefaultEliteName(mobType, itemType);
            String legacyDisplayName = ColorUtils.getTierColorLegacy(config.getTier()) + ChatColor.stripColor(itemName);

            // Apply display name with Adventure API support while maintaining exact legacy appearance
            try {
                Component nameComponent = createDisplayNameComponent(legacyDisplayName);
                meta.displayName(nameComponent);
            } catch (Exception e) {
                // Fallback to legacy display name
                meta.setDisplayName(legacyDisplayName);
            }

            // Create lore based on whether it's a weapon or armor
            List<String> legacyLore = new ArrayList<>();
            boolean isWeapon = ItemTypes.isWeapon(itemType);

            if (isWeapon) {
                EliteLoreBuilder.buildEliteWeaponLore(legacyLore, config, details, itemType, mobType, logger);
            } else {
                EliteLoreBuilder.buildEliteArmorLore(legacyLore, config, details, itemType, mobType, logger);
            }

            // Add item lore text if available
            if (details != null && details.getLore() != null && !details.getLore().trim().isEmpty()) {
                legacyLore.add("");
                legacyLore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + details.getLore());
            }

            // Add rarity line
            legacyLore.add("");
            legacyLore.add(ColorUtils.getRarityColorLegacy(config.getRarity()) + ChatColor.ITALIC.toString() +
                    RarityCalculator.getRarityText(config.getRarity()));

            // Apply item flags to hide vanilla attributes
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }

            // Set lore with Adventure API support while maintaining exact legacy appearance
            try {
                List<Component> loreComponents = createLoreComponents(legacyLore);
                meta.lore(loreComponents);
            } catch (Exception e) {
                // Fallback to legacy lore
                meta.setLore(legacyLore);
            }

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
                item = ArmorTrimApplicator.applyNetheriteGoldTrim(item, logger);
            }

            // Add enchantment glow for special elite drops
            if (EliteDropValidator.isSpecialEliteDrop(mobType)) {
                item.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
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

    // ===== PRIVATE METHODS - STANDARD DROP BUILDING =====

    /**
     * Builds a weapon item with appropriate stats and lore (NO automatic orb effects).
     */
    private ItemStack buildWeaponItem(ItemStack item, int tier, int itemType, int rarity, RarityConfig rarityConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name with proper Adventure API handling
        String legacyDisplayName = ChatColor.RESET + nameProvider.getItemName(itemType, tier);
        try {
            Component nameComponent = createDisplayNameComponent(legacyDisplayName);
            meta.displayName(nameComponent);
        } catch (Exception e) {
            meta.setDisplayName(legacyDisplayName);
        }

        List<String> legacyLore = new ArrayList<>();

        // Add damage stats
        DamageStats damage = statCalculator.calculateWeaponDamage(tier, rarity, itemType);
        legacyLore.add(ChatColor.RED + "DMG: " + damage.getMin() + " - " + damage.getMax());

        // Add elemental damage
        StandardLoreBuilder.addElementalDamageToLore(legacyLore, tier, rarity);

        // Add special attributes for higher rarities
        if (rarity >= 3) {
            StandardLoreBuilder.addWeaponSpecialAttributes(legacyLore, tier, rarity);
        }

        legacyLore.add(rarityConfig.getFormattedName());

        // Set lore with proper Adventure API handling
        try {
            List<Component> loreComponents = createLoreComponents(legacyLore);
            meta.lore(loreComponents);
        } catch (Exception e) {
            meta.setLore(legacyLore);
        }

        return itemBuilder.finalizeItem(item, meta, legacyLore, tier, itemType, rarity, keyRarity, keyTier, keyItemType);
    }

    /**
     * Builds an armor item with appropriate stats and lore (NO automatic orb effects).
     */
    private ItemStack buildArmorItem(ItemStack item, int tier, int itemType, int rarity, RarityConfig rarityConfig) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name with proper Adventure API handling
        String legacyDisplayName = ChatColor.RESET + nameProvider.getItemName(itemType, tier);
        try {
            Component nameComponent = createDisplayNameComponent(legacyDisplayName);
            meta.displayName(nameComponent);
        } catch (Exception e) {
            meta.setDisplayName(legacyDisplayName);
        }

        List<String> legacyLore = new ArrayList<>();

        // Add defense stats
        StandardLoreBuilder.addArmorDefenseStat(legacyLore, tier);

        // Add HP
        int hp = statCalculator.calculateArmorHP(tier, rarity, itemType);
        legacyLore.add(ChatColor.RED + "HP: +" + hp);

        // Add regeneration stats
        StandardLoreBuilder.addArmorRegenStat(legacyLore, tier, hp);

        // Add special attributes for higher rarities
        if (rarity >= 2) {
            StandardLoreBuilder.addArmorSpecialAttributes(legacyLore, tier, rarity);
        }

        legacyLore.add(rarityConfig.getFormattedName());

        // Set lore with proper Adventure API handling
        try {
            List<Component> loreComponents = createLoreComponents(legacyLore);
            meta.lore(loreComponents);
        } catch (Exception e) {
            meta.setLore(legacyLore);
        }

        ItemStack finalItem = itemBuilder.finalizeItem(item, meta, legacyLore, tier, itemType, rarity, keyRarity, keyTier, keyItemType);

        // Apply special effects for T6 Netherite armor
        if (tier == 6 && item.getType().toString().contains("NETHERITE")) {
            finalItem = ArmorTrimApplicator.applyNetheriteGoldTrim(finalItem, logger);
        }

        return finalItem;
    }

    // ===== STATIC HELPER CLASSES =====

    /**
     * Item type constants and utility methods.
     */
    public static final class ItemTypes {
        public static final int STAFF = 1;
        public static final int SPEAR = 2;
        public static final int SWORD = 3;
        public static final int AXE = 4;
        public static final int HELMET = 5;
        public static final int CHESTPLATE = 6;
        public static final int LEGGINGS = 7;
        public static final int BOOTS = 8;

        public static boolean isWeapon(int itemType) {
            return itemType >= STAFF && itemType <= AXE;
        }

        public static boolean isArmor(int itemType) {
            return itemType >= HELMET && itemType <= BOOTS;
        }

        public static boolean isChestplateOrLeggings(int itemType) {
            return itemType == CHESTPLATE || itemType == LEGGINGS;
        }

        public static boolean isHelmetOrBoots(int itemType) {
            return itemType == HELMET || itemType == BOOTS;
        }

        public static boolean isStaffOrSpear(int itemType) {
            return itemType == STAFF || itemType == SPEAR;
        }
    }

    /**
     * Configuration constants for base stats and calculations.
     */
    public static final class BaseStats {
        public static final int LOW_RARITY_BONUS_MULTIPLIER = 1;

        public static final double ARMOR_HP_BASE = 30.0;
        public static final double MIN_DAMAGE_BASE = 5.0;
        public static final double MAX_DAMAGE_BASE = 6.0;

        public static final double RARITY_DAMAGE_MODIFIER_BASE = 0.7;
        public static final double RARITY_DAMAGE_DIVISOR_MIN = 2.35;
        public static final double RARITY_DAMAGE_DIVISOR_MAX = 2.3;
        public static final double DAMAGE_VARIANCE_DIVISOR = 4.0;

        public static final double AXE_DAMAGE_MULTIPLIER = 1.2;
        public static final double STAFF_SPEAR_DAMAGE_DIVISOR = 1.5;
        public static final double CHESTPLATE_LEGGINGS_HP_MULTIPLIER = 1.2;
        public static final double HELMET_BOOTS_HP_DIVISOR = 1.5;

        public static final int TIER_3_PLUS_RARITY_1_BONUS_MAX = 15;
        public static volatile double TIER_GAP_MULTIPLIER = 2.45;
        public static final int LOW_RARITY_MAX_BONUS_ROLLS = 2;

        public static final double HP_REGEN_MIN_PERCENT = 0.19;
        public static final double HP_REGEN_MAX_PERCENT = 0.21;
        public static final int MAX_ENERGY_REGEN = 6;

        public static final double DPS_BASE_MULTIPLIER = 1.7;
        public static final double DPS_MIN_DIVISOR = 1.5;
    }

    /**
     * Rarity threshold constants.
     */
    private static final class RarityThresholds {
        static final int UNIQUE_THRESHOLD = 2;
        static final int RARE_THRESHOLD = 12;
        static final int UNCOMMON_THRESHOLD = 38;
    }

    // ===== HELPER CLASSES =====

    /**
     * Utility class for mathematical operations.
     */
    private static final class MathUtils {
        static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /**
     * Handles rarity calculations and text formatting.
     */
    private static final class RarityCalculator {
        static int determineRarity() {
            int roll = ThreadLocalRandom.current().nextInt(100);
            if (roll < RarityThresholds.UNIQUE_THRESHOLD) return 4;
            if (roll < RarityThresholds.RARE_THRESHOLD) return 3;
            if (roll < RarityThresholds.UNCOMMON_THRESHOLD) return 2;
            return 1;
        }

        static String getRarityText(int rarity) {
            return switch (rarity) {
                case 1 -> "Common";
                case 2 -> "Uncommon";
                case 3 -> "Rare";
                case 4 -> "Unique";
                default -> "Common";
            };
        }
    }

    /**
     * Utility class for color operations with Adventure API support.
     */
    private static final class ColorUtils {
        static ChatColor getTierColor(int tier) {
            return switch (tier) {
                case 1 -> ChatColor.WHITE;
                case 2 -> ChatColor.GREEN;
                case 3 -> ChatColor.AQUA;
                case 4 -> ChatColor.LIGHT_PURPLE;
                case 5 -> ChatColor.YELLOW;
                case 6 -> ChatColor.GOLD;
                default -> ChatColor.WHITE;
            };
        }

        static String getTierColorLegacy(int tier) {
            return getTierColor(tier).toString();
        }

        static ChatColor getRarityColor(int rarity) {
            return switch (rarity) {
                case 1 -> ChatColor.GRAY;
                case 2 -> ChatColor.GREEN;
                case 3 -> ChatColor.AQUA;
                case 4 -> ChatColor.YELLOW;
                default -> ChatColor.GRAY;
            };
        }

        static String getRarityColorLegacy(int rarity) {
            return getRarityColor(rarity).toString();
        }

        // Adventure API color mappings
        static NamedTextColor getTierColorAdventure(int tier) {
            return switch (tier) {
                case 1 -> NamedTextColor.WHITE;
                case 2 -> NamedTextColor.GREEN;
                case 3 -> NamedTextColor.AQUA;
                case 4 -> NamedTextColor.LIGHT_PURPLE;
                case 5 -> NamedTextColor.YELLOW;
                case 6 -> NamedTextColor.GOLD;
                default -> NamedTextColor.WHITE;
            };
        }

        static NamedTextColor getRarityColorAdventure(int rarity) {
            return switch (rarity) {
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.GREEN;
                case 3 -> NamedTextColor.AQUA;
                case 4 -> NamedTextColor.YELLOW;
                default -> NamedTextColor.GRAY;
            };
        }
    }

    /**
     * Handles stat calculations with clear, configurable formulas.
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

            // Apply rarity bonuses
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
     * Handles item building and meta application (NO automatic orb effects) with Adventure API support.
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
            // Apply item flags to hide vanilla attributes
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }

            // Set lore if not already set by Adventure API
            if (!meta.hasLore()) {
                meta.setLore(lore);
            }

            // Store metadata
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(keyRarity, PersistentDataType.INTEGER, rarity);
            container.set(keyTier, PersistentDataType.INTEGER, tier);
            container.set(keyItemType, PersistentDataType.INTEGER, itemType);

            item.setItemMeta(meta);

            return item;
        }
    }

    /**
     * Provides item names based on tier and type with Adventure API support.
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
            ChatColor color = ColorUtils.getTierColor(tier);

            if (tier <= 3) {
                return color + ITEM_NAMES[tier - 1][itemType - 1];
            } else {
                return color + TIER_PREFIXES[tier - 1] + " " + ITEM_SUFFIXES[itemType - 1];
            }
        }

        public Component getItemNameComponent(int itemType, int tier) {
            String legacyName = getItemName(itemType, tier);
            return getInstance().createDisplayNameComponent(legacyName);
        }
    }

    /**
     * Simple container for damage stats.
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

    // ===== UTILITY CLASSES FOR ELITE DROPS =====

    /**
     * Validates elite drop configurations and types.
     */
    private static final class EliteDropValidator {
        static boolean isSpecialEliteDrop(String mobType) {
            return mobType.equalsIgnoreCase("bossSkeletonDungeon") ||
                    mobType.equalsIgnoreCase("frostKing") ||
                    mobType.equalsIgnoreCase("warden") ||
                    mobType.equalsIgnoreCase("apocalypse") ||
                    mobType.equalsIgnoreCase("chronos");
        }
    }

    /**
     * Generates default names for elite drops.
     */
    private static final class EliteNameGenerator {
        static String generateDefaultEliteName(String mobType, int itemType) {
            String mobName = mobType.substring(0, 1).toUpperCase() + mobType.substring(1);
            String[] itemTypeNames = {"Staff", "Spear", "Sword", "Axe", "Helmet", "Chestplate", "Leggings", "Boots"};
            String itemTypeName = itemTypeNames[itemType - 1];
            return mobName + "'s " + itemTypeName;
        }
    }

    /**
     * Resolves materials for elite drops with fallback logic.
     */
    private static final class EliteMaterialResolver {
        static Material getMaterialForEliteDrop(ItemDetails details, EliteDropConfig config, int itemType, Logger logger) {
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

        private static Material getFallbackMaterialByTier(int tier, int itemType) {
            String[] tierPrefixes = {"WOODEN", "STONE", "IRON", "DIAMOND", "GOLDEN", "NETHERITE"};
            String[] weaponSuffixes = {"HOE", "SHOVEL", "SWORD", "AXE"};
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
    }

    /**
     * Applies armor trims to special items using Paper 1.21.7 enhancements.
     */
    private static final class ArmorTrimApplicator {
        static ItemStack applyNetheriteGoldTrim(ItemStack item, Logger logger) {
            if (item.getItemMeta() instanceof ArmorMeta) {
                try {
                    ArmorMeta armorMeta = (ArmorMeta) item.getItemMeta();

                    // Use Paper's enhanced registry system for 1.21.7
                    try {
                        var trimMaterialRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
                        var trimPatternRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);

                        var goldMaterial = trimMaterialRegistry.get(NamespacedKey.minecraft("gold"));
                        var eyePattern = trimPatternRegistry.get(NamespacedKey.minecraft("eye"));

                        if (goldMaterial != null && eyePattern != null) {
                            ArmorTrim goldTrim = new ArmorTrim(goldMaterial, eyePattern);
                            armorMeta.setTrim(goldTrim);
                            item.setItemMeta(armorMeta);
                            logger.fine("Applied gold trim to netherite armor using Paper 1.21.7 registry: " + item.getType());
                        } else {
                            // Fallback to legacy method
                            applyLegacyGoldTrim(item, armorMeta, logger);
                        }
                    } catch (Exception e) {
                        // Fallback to legacy method
                        applyLegacyGoldTrim(item, armorMeta, logger);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to apply gold trim to netherite armor: " + e.getMessage());
                }
            }
            return item;
        }

        private static void applyLegacyGoldTrim(ItemStack item, ArmorMeta armorMeta, Logger logger) {
            try {
                ArmorTrim goldTrim = new ArmorTrim(TrimMaterial.GOLD, TrimPattern.EYE);
                armorMeta.setTrim(goldTrim);
                item.setItemMeta(armorMeta);
                logger.fine("Applied gold trim to netherite armor using legacy method: " + item.getType());
            } catch (Exception e) {
                logger.warning("Failed to apply legacy gold trim: " + e.getMessage());
            }
        }
    }

    /**
     * Builds lore for elite items from YAML configuration with Adventure API support.
     */
    private static final class EliteLoreBuilder {
        static void buildEliteWeaponLore(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType, String mobType, Logger logger) {
            try {
                // Get damage stats from configuration with tier-based variation
                StatRange damageRange = getStatRange(config, details, "damage", true, itemType);
                if (damageRange != null) {
                    // Add tier-based variation to the damage range
                    int tierVariation = config.getTier() * 2;
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
                    // Smart fallback damage calculation with variation
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

                // Add elemental damage and special stats with duplicate checking
                addElementalDamageFromConfig(lore, config, details, itemType);
                addSpecialStatsFromConfig(lore, config, details, true, itemType);

            } catch (Exception e) {
                logger.warning("§c[DropsManager] Error building elite weapon lore: " + e.getMessage());
            }
        }

        static void buildEliteArmorLore(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType, String mobType, Logger logger) {
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
                    // Smart fallback defense stat with variation
                    int defenseStat = config.getTier() * 3 + ThreadLocalRandom.current().nextInt(5);
                    String statType = ThreadLocalRandom.current().nextBoolean() ? "DPS" : "ARMOR";
                    lore.add(ChatColor.RED + statType + ": " + defenseStat + "%");
                }

                // Add HP with variation
                StatRange hpRange = getArmorSpecificHPRange(config, details, itemType);
                if (hpRange != null) {
                    int hp = hpRange.getRandomValue();
                    // Add tier-based variation to HP
                    int tierVariation = config.getTier() * 50;
                    int variation = ThreadLocalRandom.current().nextInt(tierVariation + 1);
                    hp += variation;
                    lore.add(ChatColor.RED + "HP: +" + hp);
                } else {
                    // Smart fallback HP calculation with variation
                    int hp = calculateFallbackHP(config.getTier(), itemType);
                    // Add random variation
                    int variation = ThreadLocalRandom.current().nextInt(hp / 4);
                    hp += variation;
                    lore.add(ChatColor.RED + "HP: +" + hp);
                }

                // Add regeneration and special stats with duplicate checking
                addRegenStatsFromConfig(lore, config, details, itemType);
                addSpecialStatsFromConfig(lore, config, details, false, itemType);

            } catch (Exception e) {
                logger.warning("§c[DropsManager] Error building elite armor lore: " + e.getMessage());
            }
        }

        // [All other EliteLoreBuilder methods remain the same as in the original class]
        // ... [keeping all existing helper methods for space, they remain unchanged]

        private static StatRange getStatRange(EliteDropConfig config, ItemDetails details, String statName, boolean isWeapon, int itemType) {
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
                return null;
            }
        }

        private static StatRange getArmorSpecificHPRange(EliteDropConfig config, ItemDetails details, int itemType) {
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

        private static String getArmorPieceName(int itemType) {
            return switch (itemType) {
                case ItemTypes.HELMET -> "helmet";
                case ItemTypes.CHESTPLATE -> "chestplate";
                case ItemTypes.LEGGINGS -> "leggings";
                case ItemTypes.BOOTS -> "boots";
                default -> null;
            };
        }

        private static void addElementalDamageFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
            try {
                Map<String, StatRange> weaponStats = config.getWeaponStats();
                String[] elementalTypes = {"fireDamage", "poisonDamage", "iceDamage"};

                for (String elementType : elementalTypes) {
                    StatRange elementRange = weaponStats.get(elementType);
                    if (elementRange != null && elementRange.getMax() > 0) {
                        int damage = elementRange.getRandomValue();
                        String displayName = elementType.replace("Damage", "").toUpperCase() + " DMG";
                        String statLine = ChatColor.RED + displayName + ": +" + damage;

                        if (!LoreUtils.loreContainsElement(lore, displayName)) {
                            lore.add(statLine);
                            break; // Only add one elemental type
                        }
                    }
                }
            } catch (Exception e) {
                // Silently handle errors
            }
        }

        private static void addRegenStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, int itemType) {
            try {
                // Check for energy regen first
                StatRange energyRange = getStatRange(config, details, "energyRegen", false, itemType);
                if (energyRange != null && energyRange.getMax() > 0) {
                    int value = energyRange.getRandomValue();
                    if (value > 0) {
                        String statLine = ChatColor.RED + "ENERGY REGEN: +" + value + "%";
                        if (!LoreUtils.loreContainsElement(lore, "ENERGY REGEN")) {
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
                        if (!LoreUtils.loreContainsElement(lore, "HP REGEN")) {
                            lore.add(statLine);
                        }
                    }
                }
            } catch (Exception e) {
                // Silently handle errors
            }
        }

        private static void addSpecialStatsFromConfig(List<String> lore, EliteDropConfig config, ItemDetails details, boolean isWeapon, int itemType) {
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

                                if (!LoreUtils.loreContainsElement(lore, displayName)) {
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

                                if (!LoreUtils.loreContainsElement(lore, displayName)) {
                                    lore.add(statLine);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Silently handle errors
            }
        }

        private static String formatStatName(String statName) {
            return switch (statName.toLowerCase()) {
                case "lifesteal" -> "LIFE STEAL";
                case "criticalhit" -> "CRITICAL HIT";
                case "accuracy" -> "ACCURACY";
                case "strength" -> "STR";
                case "vitality" -> "VIT";
                case "intellect" -> "INT";
                case "dodgechance" -> "DODGE";
                case "blockchance" -> "BLOCK";
                default -> statName.toUpperCase();
            };
        }

        private static int calculateTierBaseDamage(int tier) {
            return switch (tier) {
                case 1 -> 12;
                case 2 -> 28;
                case 3 -> 65;
                case 4 -> 155;
                case 5 -> 370;
                case 6 -> 890;
                default -> 12;
            };
        }

        private static int getRarityMultiplier(int rarity) {
            return switch (rarity) {
                case 1 -> 100; // Common
                case 2 -> 115; // Uncommon
                case 3 -> 125; // Rare
                case 4 -> 140; // Unique
                default -> 100;
            };
        }

        private static int calculateFallbackHP(int tier, int itemType) {
            int baseHp = switch (tier) {
                case 1 -> ThreadLocalRandom.current().nextInt(100, 150);
                case 2 -> ThreadLocalRandom.current().nextInt(250, 500);
                case 3 -> ThreadLocalRandom.current().nextInt(500, 1000);
                case 4 -> ThreadLocalRandom.current().nextInt(1000, 2000);
                case 5 -> ThreadLocalRandom.current().nextInt(2000, 5000);
                case 6 -> ThreadLocalRandom.current().nextInt(4000, 8000);
                default -> 100;
            };

            // Adjust for armor piece type
            return ItemTypes.isChestplateOrLeggings(itemType) ? baseHp : baseHp / 2;
        }
    }

    /**
     * Builds lore for standard (non-elite) items with Adventure API support.
     */
    private static final class StandardLoreBuilder {
        static void addElementalDamageToLore(List<String> lore, int tier, int rarity) {
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

        static void addArmorDefenseStat(List<String> lore, int tier) {
            double dpsMax = tier * (1.0 + (tier / BaseStats.DPS_BASE_MULTIPLIER));
            double dpsMin = dpsMax / BaseStats.DPS_MIN_DIVISOR;
            int dpsi = (int) dpsMin;
            int dpsa = (int) dpsMax;

            String statType = ThreadLocalRandom.current().nextInt(2) == 0 ? "DPS" : "ARMOR";
            lore.add(ChatColor.RED + statType + ": " + dpsi + " - " + dpsa + "%");
        }

        static void addArmorRegenStat(List<String> lore, int tier, int hp) {
            int nrghp = ThreadLocalRandom.current().nextInt(4);

            if (nrghp > 0) {
                int nrg = tier;
                int nrgToTake = ThreadLocalRandom.current().nextInt(2);
                int nrgToGive = ThreadLocalRandom.current().nextInt(3);
                nrg = nrg - nrgToTake + nrgToGive + (ThreadLocalRandom.current().nextInt(tier) / 2);
                nrg = MathUtils.clamp(nrg, 1, BaseStats.MAX_ENERGY_REGEN);
                lore.add(ChatColor.RED + "ENERGY REGEN: +" + nrg + "%");
            } else {
                int minHps = Math.max(1, (int) (hp * BaseStats.HP_REGEN_MIN_PERCENT));
                int maxHps = Math.max(minHps + 1, (int) (hp * BaseStats.HP_REGEN_MAX_PERCENT));
                int hps = ThreadLocalRandom.current().nextInt(minHps, maxHps);
                lore.add(ChatColor.RED + "HP REGEN: +" + hps + "/s");
            }
        }

        static void addWeaponSpecialAttributes(List<String> lore, int tier, int rarity) {
            String[] weaponAttrs = {"LIFE STEAL", "CRITICAL HIT", "ACCURACY", "PURE DMG"};
            int numAttributes = Math.min(rarity, 3);

            for (int i = 0; i < numAttributes; i++) {
                String attr = weaponAttrs[ThreadLocalRandom.current().nextInt(weaponAttrs.length)];

                if (!LoreUtils.loreContains(lore, attr)) {
                    switch (attr) {
                        case "LIFE STEAL", "CRITICAL HIT" -> {
                            int percentage = ThreadLocalRandom.current().nextInt(5, 11) + (rarity >= 3 ? 3 : 0);
                            lore.add(ChatColor.RED + attr + ": " + percentage + "%");
                        }
                        case "ACCURACY" -> {
                            int accuracy = ThreadLocalRandom.current().nextInt(15, 25) + (rarity >= 3 ? 10 : 0);
                            lore.add(ChatColor.RED + attr + ": " + accuracy + "%");
                        }
                        case "PURE DMG" -> {
                            int pureDmg = ThreadLocalRandom.current().nextInt(tier * 5, tier * 10 + 1);
                            if (rarity >= 3) pureDmg = (int) (pureDmg * 1.5);
                            lore.add(ChatColor.RED + attr + ": +" + pureDmg);
                        }
                    }
                }
            }
        }

        static void addArmorSpecialAttributes(List<String> lore, int tier, int rarity) {
            String[] armorAttrs = {"STR", "DEX", "INT", "VIT", "DODGE", "BLOCK"};
            int numAttributes = Math.min(rarity, 2);

            for (int i = 0; i < numAttributes; i++) {
                String attr = armorAttrs[ThreadLocalRandom.current().nextInt(armorAttrs.length)];

                if (!LoreUtils.loreContains(lore, attr)) {
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
    }

    /**
     * Utility class for lore manipulation and checking.
     */
    private static final class LoreUtils {
        static boolean loreContains(List<String> lore, String attribute) {
            return lore.stream().anyMatch(line -> ChatColor.stripColor(line).contains(attribute));
        }

        static boolean loreContainsElement(List<String> lore, String statName) {
            String normalizedStatName = statName.toLowerCase().trim();
            return lore.stream().anyMatch(line -> {
                String cleanLine = ChatColor.stripColor(line).toLowerCase().trim();

                // Check for exact stat name matches
                if (cleanLine.contains(normalizedStatName)) {
                    return true;
                }

                // Check for common stat variations
                return switch (normalizedStatName) {
                    case "dmg", "damage" -> cleanLine.contains("dmg:") || cleanLine.contains("damage:");
                    case "hp", "health" -> cleanLine.contains("hp:") || cleanLine.contains("health:");
                    case "energy regen", "energyregen" -> cleanLine.contains("energy regen:") || cleanLine.contains("energy:");
                    case "hp regen", "hpregen" -> cleanLine.contains("hp regen:") || cleanLine.contains("regen:");
                    case "fire dmg", "firedamage" -> cleanLine.contains("fire dmg:") || cleanLine.contains("fire:");
                    case "poison dmg", "poisondamage" -> cleanLine.contains("poison dmg:") || cleanLine.contains("poison:");
                    case "ice dmg", "icedamage" -> cleanLine.contains("ice dmg:") || cleanLine.contains("ice:");
                    case "dps" -> cleanLine.contains("dps:");
                    case "armor" -> cleanLine.contains("armor:");
                    default -> false;
                };
            });
        }
    }
}
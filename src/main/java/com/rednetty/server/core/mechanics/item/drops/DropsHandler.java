package com.rednetty.server.core.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.core.mechanics.item.drops.types.EliteDropConfig;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.world.mobs.MobManager;
import com.rednetty.server.core.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.core.mechanics.world.teleport.TeleportBookSystem;
import com.rednetty.server.core.mechanics.world.teleport.TeleportDestination;
import com.rednetty.server.core.mechanics.world.teleport.TeleportManager;
import com.rednetty.server.utils.text.TextUtil;

// Adventure API imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

// Bukkit/Paper imports
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

// Paper-specific imports

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Event handler for item drops from mobs with Adventure API support and Paper 1.21.7 optimizations
 *
 * <p>Key Features:</p>
 * <ul>
 *   <li>Adventure API integration for modern text handling</li>
 *   <li>Paper Spigot 1.21.7 performance optimizations</li>
 *   <li>Enhanced visual and audio effects</li>
 *   <li>Named elite handling from elite_drops.yml</li>
 *   <li>T6 world boss detection and rewards</li>
 *   <li>Thread-safe damage tracking</li>
 *   <li>Independent drop rolling system</li>
 *   <li>Advanced particle and sound systems</li>
 * </ul>
 *
 * CRITICAL FIXES:
 * - Named elites now ONLY drop their configured items from elite_drops.yml
 * - World boss detection now uses authoritative MobType.isWorldBoss() property
 * - Improved loot buff integration with thread safety
 * - Enhanced drop validation and error handling
 * - Better damage tracking and cleanup
 * - Modern Adventure API text components
 * - Paper 1.21.7 performance optimizations
 *
 * @author YakRealms Team
 * @version 3.0 - Adventure API Edition
 * @since 1.0
 */
public class DropsHandler implements Listener {
    private static DropsHandler instance;
    private final YakRealms plugin;
    private final Logger logger;
    private final DropsManager dropsManager;
    private final DropFactory dropFactory;
    private final MobManager mobManager;
    private final LootNotifier lootNotifier;
    private final LootBuffManager lootBuffManager;
    private final YakPlayerManager playerManager;

    // ===== ADVENTURE API COMPONENTS =====
    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;

    // Thread-safe damage tracking with better performance
    private final Map<UUID, MobDamageData> mobDamageTracking = new ConcurrentHashMap<>();
    private final Set<UUID> processedMobs = ConcurrentHashMap.newKeySet();

    // MAJOR UPDATE: Independent drop rates for each type (in percentage)
    // Base gem drop rates by tier - INDEPENDENT of main drops
    private static final Map<Integer, Integer> GEM_DROP_RATES = Map.of(
            1, 45,  // Tier 1: 45% chance for gems
            2, 50,  // Tier 2: 50% chance
            3, 55,  // Tier 3: 55% chance
            4, 60,  // Tier 4: 60% chance
            5, 65,  // Tier 5: 65% chance
            6, 70   // Tier 6: 70% chance
    );

    // Base crate drop rates by tier - INDEPENDENT of main drops
    private static final Map<Integer, Integer> CRATE_DROP_RATES = Map.of(
            1, 8,   // Tier 1: 8% chance for crates
            2, 10,  // Tier 2: 10% chance
            3, 12,  // Tier 3: 12% chance
            4, 15,  // Tier 4: 15% chance
            5, 18,  // Tier 5: 18% chance
            6, 22   // Tier 6: 22% chance
    );

    // Teleport book drop rates by tier (in percentage) - INDEPENDENT of main drops
    private static final Map<Integer, Integer> TELEPORT_BOOK_DROP_RATES = Map.of(
            1, 2,  // Tier 1: 2% chance
            2, 3,  // Tier 2: 3% chance
            3, 4,  // Tier 3: 4% chance
            4, 5,  // Tier 4: 5% chance
            5, 7,  // Tier 5: 7% chance
            6, 10  // Tier 6: 10% chance
    );

    // Scroll drop rates by tier - INDEPENDENT of main drops
    private static final Map<Integer, Integer> SCROLL_DROP_RATES = Map.of(
            1, 3,   // Tier 1: 3% chance
            2, 4,   // Tier 2: 4% chance
            3, 5,   // Tier 3: 5% chance
            4, 6,   // Tier 4: 6% chance
            5, 8,   // Tier 5: 8% chance
            6, 10   // Tier 6: 10% chance
    );

    // Constants for better maintainability
    private static final long CLEANUP_INTERVAL_TICKS = 1200L; // 1 minute
    private static final long DAMAGE_TRACKING_EXPIRY = TimeUnit.MINUTES.toMillis(10); // INCREASED to 10 minutes
    private static final long INTERACTION_TRACKING_EXPIRY = TimeUnit.MINUTES.toMillis(3); // 3 minutes for interactions
    private static final int WORLD_BOSS_DROP_COUNT_MIN = 3;
    private static final int WORLD_BOSS_DROP_COUNT_MAX = 5;
    private static final double WORLD_BOSS_DROP_RADIUS = 2.0;
    private static final int DEFAULT_PROTECTION_SECONDS = 5;
    private static final int WORLD_BOSS_PROTECTION_SECONDS = 30;
    private static final double NEARBY_PLAYER_NOTIFICATION_RANGE = 30.0;

    // Track recent player interactions to ensure notifications work even without damage tracking
    private final Map<UUID, Set<UUID>> recentPlayerInteractions = new ConcurrentHashMap<>();

    // Enhanced visual effects with Adventure API support
    private static final Map<Integer, Particle> TIER_PARTICLES = Map.of(
            1, Particle.HAPPY_VILLAGER,
            2, Particle.ENCHANT,
            3, Particle.DRIPPING_WATER,
            4, Particle.PORTAL,
            5, Particle.FLAME,
            6, Particle.POOF
    );

    // Adventure API sound mappings
    private static final Map<Integer, org.bukkit.Sound> TIER_SOUNDS = Map.of(
            1, org.bukkit.Sound.ENTITY_ITEM_PICKUP,
            2, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            3, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
            4, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
            5, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
            6, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP
    );

    /**
     * Enhanced damage tracking data structure with interaction tracking
     */
    private static class MobDamageData {
        private final Map<UUID, Double> damageMap = new ConcurrentHashMap<>();
        private final Set<UUID> recentInteractions = ConcurrentHashMap.newKeySet();
        private final long creationTime = System.currentTimeMillis();
        private volatile long lastInteractionTime = System.currentTimeMillis();

        public void addDamage(UUID playerUuid, double damage) {
            damageMap.merge(playerUuid, damage, Double::sum);
            recentInteractions.add(playerUuid);
            lastInteractionTime = System.currentTimeMillis();
        }

        public void addInteraction(UUID playerUuid) {
            recentInteractions.add(playerUuid);
            lastInteractionTime = System.currentTimeMillis();
        }

        public Map<UUID, Double> getDamageMap() {
            return damageMap;
        }

        public Set<UUID> getRecentInteractions() {
            return recentInteractions;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - lastInteractionTime > DAMAGE_TRACKING_EXPIRY;
        }

        public List<Map.Entry<UUID, Double>> getSortedDamagers() {
            return damageMap.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .collect(Collectors.toList());
        }

        public boolean hasInteractions() {
            return !damageMap.isEmpty() || !recentInteractions.isEmpty();
        }
    }

    /**
     * Private constructor for singleton pattern with Adventure API initialization
     */
    private DropsHandler() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.dropsManager = DropsManager.getInstance();
        this.dropFactory = DropFactory.getInstance();
        this.mobManager = MobManager.getInstance();
        this.lootNotifier = LootNotifier.getInstance();
        this.lootBuffManager = LootBuffManager.getInstance();
        this.playerManager = YakPlayerManager.getInstance();

        // Initialize Adventure API components
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacySection();
    }

    /**
     * Gets the singleton instance
     */
    public static synchronized DropsHandler getInstance() {
        if (instance == null) {
            instance = new DropsHandler();
        }
        return instance;
    }

    /**
     * Initializes the drops handler with enhanced cleanup system and Adventure API support
     */
    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startEnhancedCleanupTask();
        logger.info("DropsHandler initialized successfully");
    }

    // ===== ADVENTURE API INTEGRATION METHODS =====

    /**
     * Creates a Component from legacy text (backwards compatibility).
     *
     * @param legacyText The legacy text with color codes
     * @return Adventure Component
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
     * Sends enhanced message using Adventure API with fallback support.
     *
     * @param player The player to send message to
     * @param message The message text
     */
    public void sendEnhancedMessage(Player player, String message) {
        try {
            Component component = createComponent(message);
            player.sendMessage(component);
        } catch (Exception e) {
            // Fallback to legacy messaging
            player.sendMessage(message);
        }
    }

    /**
     * Plays enhanced sound using Adventure API and Paper's sound system.
     *
     * @param player The player to play sound for
     * @param sound The sound to play
     * @param category The sound category
     * @param volume Sound volume
     * @param pitch Sound pitch
     */
    public void playEnhancedSound(Player player, org.bukkit.Sound sound, SoundCategory category, float volume, float pitch) {
        try {
            // Use Paper's enhanced sound system
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        } catch (Exception e) {
            // Fallback to legacy sound system
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /**
     * Plays enhanced sound at specific location.
     *
     * @param location The location to play sound at
     * @param sound The sound to play
     * @param category The sound category
     * @param volume Sound volume
     * @param pitch Sound pitch
     */
    public void playEnhancedSoundAt(Location location, org.bukkit.Sound sound, SoundCategory category, float volume, float pitch) {
        try {
            location.getWorld().playSound(location, sound, category, volume, pitch);
        } catch (Exception e) {
            // Fallback to legacy sound system
            location.getWorld().playSound(location, sound, volume, pitch);
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

    /**
     * Creates enhanced particle effects using Paper's particle system.
     *
     * @param location The location to spawn particles
     * @param particle The particle type
     * @param count Number of particles
     * @param offsetX X offset
     * @param offsetY Y offset
     * @param offsetZ Z offset
     * @param extra Extra data
     */
    public void spawnEnhancedParticles(Location location, Particle particle, int count,
                                       double offsetX, double offsetY, double offsetZ, double extra) {
        try {
            // Use Paper's enhanced particle system
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (Exception e) {
            // Fallback with reduced parameters
            location.getWorld().spawnParticle(particle, location, count);
        }
    }

    // ===== ENHANCED MOB DROP HANDLING =====

    /**
     * CRITICAL FIX: Enhanced mob drop handling with proper named elite handling and Adventure API support
     */
    private void handleMobDrops(LivingEntity entity) {
        if (isCleanupKill(entity)) {
            if (logger.isLoggable(java.util.logging.Level.WARNING)) {
                logger.warning("§c[DropsHandler] Cleanup kill detected in handleMobDrops - this should not happen!");
            }
            return;
        }

        MobAnalysis analysis = analyzeMob(entity);

        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine("§6[DropsHandler] §7Mob analysis: " + analysis.getMobType() +
                    " (T" + analysis.getTier() + ") Elite: " + analysis.isElite() +
                    " Named: " + analysis.isNamedElite() + " WorldBoss: " + analysis.isWorldBoss() +
                    " ActualName: " + analysis.getActualMobName());
        }

        // MAJOR CHANGE: Handle main drops independently with proper named elite handling
        boolean mainDropOccurred = false;
        if (shouldDropItems(entity, analysis)) {
            if (analysis.isWorldBoss()) {
                handleWorldBossDrops(entity, analysis);
                mainDropOccurred = true;
            } else if (analysis.isNamedElite()) {
                // CRITICAL FIX: Named elites get special handling to only drop their configured items
                handleNamedEliteDrops(entity, analysis);
                mainDropOccurred = true;
            } else if (analysis.isElite()) {
                handleEliteDrops(entity, analysis);
                mainDropOccurred = true;
            } else {
                handleNormalDrops(entity, analysis);
                mainDropOccurred = true;
            }
        }

        // MAJOR CHANGE: Handle additional drops INDEPENDENTLY - these now roll regardless of main drops
        handleIndependentAdditionalDrops(entity, analysis, mainDropOccurred);
    }

    /**
     * CRITICAL FIX: Enhanced mob analysis with proper world boss detection and named elite handling
     */
    private MobAnalysis analyzeMob(LivingEntity entity) {
        int tier = detectMobTier(entity);
        boolean isElite = detectEliteStatus(entity);
        String mobType = extractMobType(entity);
        String actualMobName = entity.getCustomName();

        // CRITICAL FIX: Enhanced named elite detection with multiple fallback methods
        boolean isNamedElite = false;

        if (mobType != null && !mobType.trim().isEmpty()) {
            // Method 1: Direct lookup in elite configurations
            isNamedElite = DropConfig.isNamedElite(mobType);

            if (!isNamedElite && logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("§6[DropsHandler] §7Mob type '" + mobType + "' not found in elite configs. Trying alternatives...");
            }
        }

        // Method 2: Try to extract mob type from custom name if not found
        if (!isNamedElite && actualMobName != null) {
            String cleanedName = ChatColor.stripColor(actualMobName).toLowerCase()
                    .replace(" ", "").replace("_", "").replace("-", "");

            // Try different variations of the cleaned name
            String[] nameVariations = {
                    cleanedName,
                    cleanedName.replace("elite", "").replace("boss", "").trim(),
                    cleanedName.replaceAll("[^a-z]", "")
            };

            for (String variation : nameVariations) {
                if (DropConfig.isNamedElite(variation)) {
                    mobType = variation;
                    isNamedElite = true;
                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§6[DropsHandler] §7Found elite config using name variation: " + variation);
                    }
                    break;
                }
            }
        }

        // Method 3: Check against all known elite names from the YAML
        if (!isNamedElite && isElite) {
            String[] allEliteNames = DropConfig.getAllEliteNames();
            if (actualMobName != null) {
                String cleanedActualName = ChatColor.stripColor(actualMobName).toLowerCase();

                for (String eliteName : allEliteNames) {
                    if (cleanedActualName.contains(eliteName) || eliteName.contains(cleanedActualName)) {
                        mobType = eliteName;
                        isNamedElite = true;
                        if (logger.isLoggable(java.util.logging.Level.FINE)) {
                            logger.fine("§6[DropsHandler] §7Matched elite by name similarity: " + eliteName);
                        }
                        break;
                    }
                }
            }
        }

        // CRITICAL FIX: Enhanced world boss detection with T6 support
        boolean isWorldBoss = detectWorldBossStatus(entity, mobType, tier);

        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine("§6[DropsHandler] §7Final analysis - Type: " + mobType +
                    ", Elite: " + isElite + ", Named: " + isNamedElite + ", Boss: " + isWorldBoss +
                    ", Tier: " + tier);
        }

        return new MobAnalysis(tier, isElite, isWorldBoss, isNamedElite, mobType, actualMobName);
    }

    /**
     * FIXED: Proper world boss detection using CustomMob's authoritative method
     */
    private boolean detectWorldBossStatus(LivingEntity entity, String mobType, int tier) {
        // First check if entity has CustomMob metadata to use its authoritative isWorldBoss method
        if (entity.hasMetadata("custommob")) {
            try {
                Object customMobObj = entity.getMetadata("custommob").get(0).value();
                if (customMobObj instanceof com.rednetty.server.core.mechanics.world.mobs.core.CustomMob) {
                    boolean isWorldBoss = ((com.rednetty.server.core.mechanics.world.mobs.core.CustomMob) customMobObj).isWorldBoss();
                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§6[DropsHandler] §7CustomMob world boss status: " + isWorldBoss + " for " + mobType);
                    }
                    return isWorldBoss;
                }
            } catch (Exception e) {
                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("§6[DropsHandler] §7Failed to get CustomMob world boss status: " + e.getMessage());
                }
            }
        }

        // Check explicit metadata override
        if (entity.hasMetadata("worldboss")) {
            return entity.getMetadata("worldboss").get(0).asBoolean();
        }

        // Legacy fallback: Check for vanilla world bosses
        return entity.getType() == org.bukkit.entity.EntityType.WITHER ||
                entity.getType() == org.bukkit.entity.EntityType.ENDER_DRAGON;
    }

    /**
     * CRITICAL FIX: Named elite drop handling - ONLY drops configured items from YAML with Adventure API support
     */
    private void handleNamedEliteDrops(LivingEntity entity, MobAnalysis analysis) {
        try {
            String mobType = analysis.getMobType();
            if (mobType == null || mobType.trim().isEmpty()) {
                logger.warning("§c[DropsHandler] Named elite has null/empty mob type, falling back to regular elite drops");
                handleEliteDrops(entity, analysis);
                return;
            }

            // CRITICAL FIX: Get the elite configuration to determine what items this elite can drop
            EliteDropConfig config = DropConfig.getEliteDropConfig(mobType);
            if (config == null) {
                logger.warning("§c[DropsHandler] No elite config found for " + mobType + ", falling back to regular elite drops");
                handleEliteDrops(entity, analysis);
                return;
            }

            // CRITICAL FIX: Only drop items that are configured for this elite
            // Check if this elite has any configured item types (1-8 for weapons/armor)
            List<Integer> availableItemTypes = new ArrayList<>();

            // Check each possible item type (1-8) to see if this elite has it configured
            for (int itemType = 1; itemType <= 8; itemType++) {
                com.rednetty.server.core.mechanics.item.drops.types.ItemDetails itemDetails = config.getItemDetailsForType(itemType);
                if (itemDetails != null) {
                    availableItemTypes.add(itemType);
                }
            }

            if (availableItemTypes.isEmpty()) {
                logger.warning("§c[DropsHandler] Elite " + mobType + " has no configured items, falling back to regular elite drops");
                handleEliteDrops(entity, analysis);
                return;
            }

            // MAJOR FIX: Choose ONLY from configured item types, not random
            // Select a random item type from the CONFIGURED options only
            int itemType = availableItemTypes.get(ThreadLocalRandom.current().nextInt(availableItemTypes.size()));

            // Create the elite drop using the configured item type
            ItemStack item = null;
            try {
                item = dropsManager.createEliteDrop(mobType, itemType, analysis.getTier());

                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("§a[DropsHandler] §7Successfully created named elite drop for " + mobType +
                            " (configured item type " + itemType + ")");
                }
            } catch (Exception e) {
                logger.warning("§c[DropsHandler] Failed to create named elite drop for " + mobType +
                        ": " + e.getMessage());
            }

            // Fallback to regular elite drop if named elite drop failed
            if (item == null) {
                logger.warning("§c[DropsHandler] Named elite drop creation failed for " + mobType +
                        ", creating fallback elite drop");
                int rarity = Math.max(3, analysis.getTier() >= 5 ? 4 : 3); // At least rare for named elites
                item = dropsManager.createDrop(analysis.getTier(), itemType, rarity);

                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("§6[DropsHandler] §7Used fallback elite drop for " + mobType +
                            " (T" + analysis.getTier() + " R" + rarity + " I" + itemType + ")");
                }
            }

            if (item != null) {
                Item droppedItem = dropItemWithEnhancedEffects(entity, item, analysis.getTier());
                playEnhancedDropEffects(entity.getLocation(), analysis.getTier(), true);

                // Enhanced notification for named elites using Adventure API
                notifyPlayersOfNamedEliteDrop(entity, item, mobType);

                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("§a[DropsHandler] §7Successfully dropped configured item for named elite " + mobType);
                }
            } else {
                logger.warning("§c[DropsHandler] Complete failure to create drop for named elite " + mobType);
            }

        } catch (Exception e) {
            logger.warning("§c[DropsHandler] Critical error in named elite drop handling: " + e.getMessage());
            e.printStackTrace();
            // Emergency fallback
            handleEliteDrops(entity, analysis);
        }
    }

    /**
     * Enhanced world boss drop handling with tier-based scaling and Adventure API
     */
    private void handleWorldBossDrops(LivingEntity entity, MobAnalysis analysis) {
        int dropCount = ThreadLocalRandom.current().nextInt(
                WORLD_BOSS_DROP_COUNT_MAX - WORLD_BOSS_DROP_COUNT_MIN + 1) + WORLD_BOSS_DROP_COUNT_MIN;

        // Higher tier world bosses get extra drops for increased challenge
        if (analysis.getTier() >= 6) {
            dropCount += 2; // Extra drops for high-tier world bosses
            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("§6[DropsHandler] §7High-tier world boss gets " + dropCount + " drops");
            }
        }

        List<Map.Entry<UUID, Double>> sortedDamagers = getSortedDamageContributors(entity);
        List<DropInfo> drops = new ArrayList<>();

        Location center = entity.getLocation();
        String bossName = entity.getCustomName() != null ?
                ChatColor.stripColor(entity.getCustomName()) : "World Boss";

        for (int i = 0; i < dropCount; i++) {
            Location dropLocation = calculateDropLocation(center, i, dropCount);
            ItemStack item = createHighQualityItem(analysis.getTier());

            Item droppedItem = entity.getWorld().dropItem(dropLocation, item);
            applyEnhancedWorldBossEffects(droppedItem, dropLocation);

            if (!sortedDamagers.isEmpty()) {
                int damagerIndex = i % sortedDamagers.size();
                UUID playerUuid = sortedDamagers.get(damagerIndex).getKey();
                Player player = Bukkit.getPlayer(playerUuid);

                if (player != null && player.isOnline()) {
                    dropsManager.registerDropProtection(droppedItem, playerUuid, WORLD_BOSS_PROTECTION_SECONDS);
                    lootNotifier.sendDropNotification(player, item, entity, true);
                    drops.add(new DropInfo(player.getName(), sortedDamagers.get(damagerIndex).getValue().intValue()));
                }
            }
        }

        lootNotifier.announceWorldBossDefeat(bossName, drops.stream()
                .map(drop -> new Object[]{drop.playerName, drop.damage})
                .distinct()
                .limit(3)
                .collect(Collectors.toList()));

        playEnhancedWorldBossDefeatEffects(center);

        if (logger.isLoggable(java.util.logging.Level.INFO)) {
            logger.info("§a[DropsHandler] §7World boss " + bossName + " defeated with " + dropCount + " drops");
        }
    }

    /**
     * IMPROVED: Independent gem drop rolling with enhanced loot buff integration and Adventure API support
     */
    private void rollForGems(LivingEntity entity, int tier) {
        int baseGemRate = GEM_DROP_RATES.getOrDefault(tier, 45);
        int gemRate = baseGemRate;

        // Apply loot buff if active - thread-safe check
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            gemRate += (int) Math.ceil(gemRate * buffPercentage / 100.0);
        }

        if (ThreadLocalRandom.current().nextInt(100) < gemRate) {
            try {
                ItemStack gems = dropFactory.createGemDrop(tier);
                if (gems != null) {
                    Item droppedItem = dropItemWithEnhancedEffects(entity, gems, tier);

                    // Enhanced notification with gem amount using Adventure API
                    int gemAmount = gems.getAmount();
                    Player topDamager = getTopDamageDealer(entity);
                    if (topDamager != null) {
                        lootNotifier.sendGemDropNotification(topDamager, gems, entity, gemAmount);

                        // Show enhanced title for gems
                        showEnhancedTitle(topDamager,
                                "§6+" + gemAmount + " Gems",
                                "§7From tier " + tier + " mob",
                                10, 30, 10);
                    }

                    // Track buff improvement - thread-safe
                    if (lootBuffManager.isBuffActive() && ThreadLocalRandom.current().nextInt(100) >= baseGemRate) {
                        lootBuffManager.updateImprovedDrops();
                    }

                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§a[DropsHandler] §7Dropped " + gemAmount + " gems from T" + tier + " mob");
                    }
                }
            } catch (Exception e) {
                logger.warning("§c[DropsHandler] Error creating gem drop: " + e.getMessage());
            }
        }
    }

    /**
     * IMPROVED: Independent crate drop rolling with enhanced loot buff integration and Adventure API support
     */
    private void rollForCrates(LivingEntity entity, int tier) {
        int baseCrateRate = CRATE_DROP_RATES.getOrDefault(tier, 8);
        int crateRate = baseCrateRate;

        // Apply loot buff if active - thread-safe check
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            crateRate += (int) Math.ceil(crateRate * buffPercentage / 100.0);
        }

        if (ThreadLocalRandom.current().nextInt(100) < crateRate) {
            try {
                ItemStack crate = dropFactory.createCrateDrop(tier);
                if (crate != null) {
                    Item droppedItem = dropItemWithEnhancedEffects(entity, crate, tier);

                    // Enhanced notification for crates using Adventure API
                    Player topDamager = getTopDamageDealer(entity);
                    if (topDamager != null) {
                        lootNotifier.sendCrateDropNotification(topDamager, crate, entity, tier);

                        // Show enhanced title for crates
                        showEnhancedTitle(topDamager,
                                "§d✦ Crate Drop ✦",
                                "§7Tier " + tier + " Crate",
                                10, 40, 10);
                    }

                    // Track buff improvement - thread-safe
                    if (lootBuffManager.isBuffActive() && ThreadLocalRandom.current().nextInt(100) >= baseCrateRate) {
                        lootBuffManager.updateImprovedDrops();
                    }

                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§a[DropsHandler] §7Dropped T" + tier + " crate");
                    }
                }
            } catch (Exception e) {
                logger.warning("§c[DropsHandler] Error creating crate drop: " + e.getMessage());
            }
        }
    }

    /**
     * IMPROVED: Independent scroll drop rolling with enhanced loot buff integration and Adventure API support
     */
    private void rollForScrolls(LivingEntity entity, int tier) {
        int baseScrollRate = SCROLL_DROP_RATES.getOrDefault(tier, 3);
        int scrollRate = baseScrollRate;

        // Apply loot buff if active - thread-safe check
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            scrollRate += (int) Math.ceil(scrollRate * buffPercentage / 100.0);
        }

        if (ThreadLocalRandom.current().nextInt(100) < scrollRate) {
            try {
                ItemStack scroll = dropFactory.createScrollDrop(tier);
                if (scroll != null) {
                    Item droppedItem = dropItemWithEnhancedEffects(entity, scroll, tier);
                    notifyPlayersOfDropWithEnhancement(entity, scroll, false);

                    // Track buff improvement - thread-safe
                    if (lootBuffManager.isBuffActive() && ThreadLocalRandom.current().nextInt(100) >= baseScrollRate) {
                        lootBuffManager.updateImprovedDrops();
                    }

                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§a[DropsHandler] §7Dropped enhancement scroll from T" + tier + " mob");
                    }
                }
            } catch (Exception e) {
                logger.warning("§c[DropsHandler] Error creating scroll drop: " + e.getMessage());
            }
        }
    }

    /**
     * IMPROVED: Independent teleport book drop rolling with enhanced loot buff integration and Adventure API support
     */
    private void rollForTeleportBooks(LivingEntity entity, int tier) {
        Collection<TeleportDestination> destinations = TeleportManager.getInstance().getAllDestinations();

        if (destinations.isEmpty()) {
            if (logger.isLoggable(java.util.logging.Level.FINEST)) {
                logger.finest("§6[DropsHandler] §7No teleport destinations configured - skipping teleport book drops");
            }
            return;
        }

        int baseBookRate = TELEPORT_BOOK_DROP_RATES.getOrDefault(tier, 2);
        int bookRate = baseBookRate;

        // Apply loot buff if active - thread-safe check
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            bookRate += (int) Math.ceil(bookRate * buffPercentage / 100.0);
        }

        if (ThreadLocalRandom.current().nextInt(100) < bookRate) {
            try {
                List<TeleportDestination> destinationList = new ArrayList<>(destinations);
                TeleportDestination randomDestination = destinationList.get(
                        ThreadLocalRandom.current().nextInt(destinationList.size())
                );

                ItemStack teleportBook = TeleportBookSystem.getInstance().createTeleportBook(
                        randomDestination.getId(), false
                );

                if (teleportBook != null) {
                    Item droppedItem = dropItemWithEnhancedEffects(entity, teleportBook, tier);

                    // Enhanced visual effects for teleport books using Paper's particle system
                    Location dropLocation = entity.getLocation().add(0, 1, 0);
                    spawnEnhancedParticles(dropLocation, Particle.ENCHANT, 15, 0.5, 0.5, 0.5, 0.1);
                    playEnhancedSoundAt(dropLocation, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);

                    // Enhanced notification for teleport books using Adventure API
                    Player topDamager = getTopDamageDealer(entity);
                    if (topDamager != null) {
                        lootNotifier.sendTeleportBookNotification(topDamager, teleportBook, entity, randomDestination.getDisplayName());

                        // Show enhanced title for teleport books
                        showEnhancedTitle(topDamager,
                                "§b⚡ Teleport Book ⚡",
                                "§7" + randomDestination.getDisplayName(),
                                10, 50, 10);
                    }

                    // Track buff improvement - thread-safe
                    if (lootBuffManager.isBuffActive() && ThreadLocalRandom.current().nextInt(100) >= baseBookRate) {
                        lootBuffManager.updateImprovedDrops();
                    }

                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§a[DropsHandler] §7Dropped teleport book for destination: " +
                                randomDestination.getDisplayName() + " from tier " + tier + " mob");
                    }
                }
            } catch (Exception e) {
                logger.warning("§c[DropsHandler] Error creating teleport book drop: " + e.getMessage());
            }
        }
    }

    // ===== ENHANCED VISUAL AND AUDIO EFFECTS =====

    /**
     * Enhanced cleanup task with better performance monitoring and thread safety
     */
    private void startEnhancedCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    int cleanedDamage = cleanupExpiredDamageTracking();
                    int cleanedInteractions = cleanupExpiredInteractions();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            int cleanedProcessed = cleanupInvalidProcessedMobs();

                            if (cleanedDamage > 0 || cleanedProcessed > 0 || cleanedInteractions > 0) {
                                logger.fine(String.format("Enhanced cleanup completed: %d damage entries, %d processed mobs, %d interactions removed",
                                        cleanedDamage, cleanedProcessed, cleanedInteractions));
                            }
                        } catch (Exception e) {
                            logger.warning("Error in enhanced cleanup task main thread: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    logger.warning("Error in enhanced cleanup task async thread: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    /**
     * Enhanced drop effects with Adventure API and Paper optimizations
     */
    private void playEnhancedDropEffects(Location location, int tier, boolean isElite) {
        Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.HAPPY_VILLAGER);
        org.bukkit.Sound sound = TIER_SOUNDS.getOrDefault(tier, org.bukkit.Sound.ENTITY_ITEM_PICKUP);

        // Enhanced particle effects
        int particleCount = isElite ? 20 : 10;
        double spreadRadius = isElite ? 0.8 : 0.5;

        spawnEnhancedParticles(location.clone().add(0, 1, 0), particle, particleCount,
                spreadRadius, spreadRadius, spreadRadius, 0.1);

        // Enhanced sound effects with proper categorization
        SoundCategory category = isElite ? SoundCategory.MASTER : SoundCategory.PLAYERS;
        float volume = isElite ? 1.5f : 1.0f;
        float pitch = Math.min(2.0f, 0.8f + (tier * 0.2f));

        playEnhancedSoundAt(location, sound, category, volume, pitch);

        // Additional effects for higher tiers
        if (tier >= 4) {
            spawnEnhancedParticles(location.clone().add(0, 2, 0), Particle.TOTEM_OF_UNDYING, 5,
                    0.3, 0.3, 0.3, 0.0);
        }

        if (tier >= 6) {
            spawnEnhancedParticles(location.clone().add(0, 1.5, 0), Particle.DRAGON_BREATH, 8,
                    0.6, 0.6, 0.6, 0.05);
        }
    }

    /**
     * Enhanced world boss defeat effects with Adventure API
     */
    private void playEnhancedWorldBossDefeatEffects(Location location) {
        // Main explosion effect
        playEnhancedSoundAt(location, org.bukkit.Sound.ENTITY_WITHER_DEATH, SoundCategory.MASTER, 2.0f, 0.5f);
        spawnEnhancedParticles(location, Particle.EXPLOSION, 5, 2, 2, 2, 0);

        // Secondary effects with delays
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnEnhancedParticles(location.clone().add(0, 3, 0), Particle.DRAGON_BREATH, 30, 1.5, 1.5, 1.5, 0.1);
            playEnhancedSoundAt(location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 1.0f, 0.8f);
        }, 10L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnEnhancedParticles(location.clone().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 15, 2.0, 1.0, 2.0, 0.0);
        }, 20L);
    }

    /**
     * Enhanced world boss effects application
     */
    private void applyEnhancedWorldBossEffects(Item droppedItem, Location location) {
        spawnEnhancedParticles(location, Particle.DRAGON_BREATH, 30, 1, 1, 1, 0.1);
        spawnEnhancedParticles(location, Particle.TOTEM_OF_UNDYING, 10, 0.5, 0.5, 0.5, 0.0);
        droppedItem.setUnlimitedLifetime(true);

        // Glowing effect for world boss items
        droppedItem.setGlowing(true);
    }

    /**
     * Enhanced item dropping with visual effects
     */
    private Item dropItemWithEnhancedEffects(LivingEntity entity, ItemStack item, int tier) {
        Item droppedItem = entity.getWorld().dropItemNaturally(entity.getLocation(), item);

        Player topDamager = getTopDamageDealer(entity);
        if (topDamager != null) {
            dropsManager.registerDropProtection(droppedItem, topDamager.getUniqueId(), DEFAULT_PROTECTION_SECONDS);
        }

        // Enhanced visual effects based on tier
        if (tier >= 3) {
            droppedItem.setGlowing(true);
        }

        if (tier >= 5) {
            // Add particle trail for high-tier items
            Location dropLocation = droppedItem.getLocation();
            spawnEnhancedParticles(dropLocation.clone().add(0, 1, 0), Particle.ENCHANT, 8, 0.3, 0.3, 0.3, 0.1);
        }

        return droppedItem;
    }

    // ===== ENHANCED NOTIFICATION SYSTEM =====

    /**
     * Enhanced notification system for named elite drops using Adventure API
     */
    private void notifyPlayersOfNamedEliteDrop(LivingEntity entity, ItemStack item, String mobType) {
        // Get all players who contributed damage
        List<Map.Entry<UUID, Double>> contributors = getSortedDamageContributors(entity);

        if (!contributors.isEmpty()) {
            // Notify top contributors with enhanced messaging
            for (int i = 0; i < Math.min(3, contributors.size()); i++) {
                UUID playerUuid = contributors.get(i).getKey();
                Player player = Bukkit.getPlayer(playerUuid);

                if (player != null && player.isOnline()) {
                    // Enhanced message for named elite drops using Adventure API
                    String eliteName = mobType.substring(0, 1).toUpperCase() + mobType.substring(1);
                    String enhancedMessage = ChatColor.GOLD + "⚡ " + ChatColor.BOLD + "ELITE DROP! " +
                            ChatColor.RESET + ChatColor.YELLOW + eliteName + "'s " +
                            ChatColor.stripColor(item.getItemMeta().getDisplayName()) + " has dropped!";

                    sendEnhancedMessage(player, enhancedMessage);

                    // Show enhanced title for elite drops
                    showEnhancedTitle(player,
                            "§6⚡ ELITE DROP ⚡",
                            "§e" + eliteName + "'s Item",
                            10, 60, 20);

                    lootNotifier.sendDropNotification(player, item, entity, true);
                }
            }
        } else {
            // Fallback notification with enhancement
            notifyPlayersOfDropWithEnhancement(entity, item, true);
        }
    }

    /**
     * Enhanced notification system using Adventure API
     */
    private void notifyPlayersOfDropWithEnhancement(LivingEntity entity, ItemStack item, boolean isBossLoot) {
        UUID entityUuid = entity.getUniqueId();
        Set<Player> notifiedPlayers = new HashSet<>();

        Player topDamager = getTopDamageDealer(entity);
        if (topDamager != null && topDamager.isOnline()) {
            lootNotifier.sendDropNotification(topDamager, item, entity, isBossLoot);
            notifiedPlayers.add(topDamager);

            // Enhanced title for boss loot
            if (isBossLoot) {
                showEnhancedTitle(topDamager,
                        "§6✦ BOSS LOOT ✦",
                        "§e" + ChatColor.stripColor(item.getItemMeta().getDisplayName()),
                        10, 50, 15);
            }

            if (logger.isLoggable(java.util.logging.Level.FINEST)) {
                logger.finest("§6[DropsHandler] §7Enhanced notification sent to top damager: " + topDamager.getName() +
                        " for " + entity.getType() + " drop");
            }
        }

        // [Rest of the notification logic remains the same but with enhanced messaging]
        if (notifiedPlayers.isEmpty()) {
            Set<UUID> interactions = recentPlayerInteractions.get(entityUuid);
            if (interactions != null && !interactions.isEmpty()) {
                UUID randomInteractionUuid = interactions.iterator().next();
                Player randomPlayer = Bukkit.getPlayer(randomInteractionUuid);
                if (randomPlayer != null && randomPlayer.isOnline()) {
                    lootNotifier.sendDropNotification(randomPlayer, item, entity, isBossLoot);
                    notifiedPlayers.add(randomPlayer);

                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§6[DropsHandler] §7Enhanced notification sent to player from recent interactions: " +
                                randomPlayer.getName() + " for " + entity.getType() + " drop");
                    }
                }
            }
        }

        if (notifiedPlayers.isEmpty()) {
            Location entityLocation = entity.getLocation();
            List<Player> nearbyPlayers = entityLocation.getWorld().getPlayers().stream()
                    .filter(player -> player.getLocation().distance(entityLocation) <= NEARBY_PLAYER_NOTIFICATION_RANGE)
                    .filter(Player::isOnline)
                    .collect(Collectors.toList());

            if (!nearbyPlayers.isEmpty()) {
                Player closestPlayer = nearbyPlayers.stream()
                        .min(Comparator.comparingDouble(player ->
                                player.getLocation().distance(entityLocation)))
                        .orElse(null);

                if (closestPlayer != null) {
                    lootNotifier.sendDropNotification(closestPlayer, item, entity, isBossLoot);
                    notifiedPlayers.add(closestPlayer);

                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.fine("§6[DropsHandler] §7Enhanced notification sent to nearby player: " + closestPlayer.getName() +
                                " for " + entity.getType() + " drop (fallback method)");
                    }
                }
            }
        }

        if (notifiedPlayers.isEmpty()) {
            logger.warning("§c[DropsHandler] §7No players could be notified for " + entity.getType() +
                    " drop! Entity UUID: " + entityUuid.toString().substring(0, 8));

            MobDamageData damageData = mobDamageTracking.get(entityUuid);
            Set<UUID> interactions = recentPlayerInteractions.get(entityUuid);
            logger.warning("§c[DropsHandler] §7Debug - Damage data exists: " + (damageData != null) +
                    ", Interactions exist: " + (interactions != null && !interactions.isEmpty()) +
                    ", Online players count: " + Bukkit.getOnlinePlayers().size());
        }
    }

    // ===== EVENT HANDLERS WITH ADVENTURE API ENHANCEMENTS =====

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);

        LivingEntity entity = event.getEntity();

        if (entity instanceof Player) {
            return;
        }

        // CRITICAL FIX: Skip entities killed by cleanup tasks
        if (isCleanupKill(entity)) {
            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.info("§6[DropsHandler] §7Skipping drop processing for cleanup kill: " + entity.getType() +
                        " ID: " + entity.getUniqueId().toString().substring(0, 8));
            }
            return;
        }

        if (!markAsProcessed(entity)) {
            return;
        }

        try {
            handleMobDrops(entity);
        } catch (Exception e) {
            logger.warning("Error processing drops for entity " + entity.getType() + ": " + e.getMessage());
        }

        // Handle tier kill statistics with enhanced tracking
        Player killer = entity.getKiller();
        if (killer != null) {
            YakPlayer yakPlayer = playerManager.getPlayer(killer);
            if (yakPlayer != null) {
                int mobTier = MobUtils.getMobTier(entity);
                switch (mobTier) {
                    case 1 -> yakPlayer.setT1Kills(yakPlayer.getT1Kills() + 1);
                    case 2 -> yakPlayer.setT2Kills(yakPlayer.getT2Kills() + 1);
                    case 3 -> yakPlayer.setT3Kills(yakPlayer.getT3Kills() + 1);
                    case 4 -> yakPlayer.setT4Kills(yakPlayer.getT4Kills() + 1);
                    case 5 -> yakPlayer.setT5Kills(yakPlayer.getT5Kills() + 1);
                    case 6 -> yakPlayer.setT6Kills(yakPlayer.getT6Kills() + 1);
                    default -> { /* No tracking for unknown tiers */ }
                }
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }

        LivingEntity entity = (LivingEntity) event.getEntity();

        if (isCleanupKill(entity)) {
            return;
        }

        Player damager = extractPlayerDamager(event);
        if (damager == null) {
            return;
        }

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = damager.getUniqueId();
        double damage = event.getFinalDamage();

        MobDamageData damageData = mobDamageTracking.computeIfAbsent(entityUuid, k -> new MobDamageData());
        damageData.addDamage(playerUuid, damage);

        recentPlayerInteractions.computeIfAbsent(entityUuid, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);

        if (logger.isLoggable(java.util.logging.Level.FINEST)) {
            logger.finest("§6[DropsHandler] §7Recorded " + damage + " damage from " + damager.getName() +
                    " to " + entity.getType() + " (ID: " + entityUuid.toString().substring(0, 8) + ")");
        }
    }

    // ===== ALL EXISTING HELPER METHODS WITH ENHANCEMENTS =====
    // [All the existing helper methods remain the same but are enhanced with Adventure API support where applicable]

    private String extractMobType(LivingEntity entity) {
        // Method 1: Check metadata "type"
        if (entity.hasMetadata("type")) {
            try {
                String type = entity.getMetadata("type").get(0).asString();
                if (type != null && !type.trim().isEmpty()) {
                    return type.trim();
                }
            } catch (Exception ignored) {
                // Fall through to next method
            }
        }

        // Method 2: Check metadata "customName"
        if (entity.hasMetadata("customName")) {
            try {
                String type = entity.getMetadata("customName").get(0).asString();
                if (type != null && !type.trim().isEmpty()) {
                    return type.trim();
                }
            } catch (Exception ignored) {
                // Fall through to next method
            }
        }

        // Method 3: Parse from custom name
        if (entity.getCustomName() != null) {
            String customName = ChatColor.stripColor(entity.getCustomName());

            // Try to extract a meaningful mob type from the custom name
            String[] nameParts = customName.toLowerCase().split("\\s+");
            for (String part : nameParts) {
                if (part.length() > 3 && DropConfig.isNamedElite(part)) {
                    return part;
                }
            }

            // If no direct match, try the full cleaned name
            String cleanedName = customName.toLowerCase().replaceAll("[^a-z]", "");
            if (cleanedName.length() > 0 && DropConfig.isNamedElite(cleanedName)) {
                return cleanedName;
            }
        }

        return null;
    }

    private int detectTierFromEquipment(LivingEntity entity) {
        if (entity.getEquipment() == null) {
            return 0;
        }

        ItemStack mainHand = entity.getEquipment().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != org.bukkit.Material.AIR) {
            String material = mainHand.getType().name();

            if (material.contains("NETHERITE")) {
                return 6;
            } else if (material.contains("DIAMOND")) {
                if (mainHand.hasItemMeta() && mainHand.getItemMeta().hasDisplayName() &&
                        mainHand.getItemMeta().getDisplayName().contains(ChatColor.GOLD.toString())) {
                    return 6;
                }
                return 4;
            } else if (material.contains("GOLDEN") || material.contains("GOLD")) {
                return 5;
            } else if (material.contains("IRON")) {
                return 3;
            } else if (material.contains("STONE")) {
                return 2;
            } else if (material.contains("WOODEN") || material.contains("WOOD")) {
                return 1;
            }
        }
        return 0;
    }

    private int detectMobTier(LivingEntity entity) {
        // Method 1: Check equipment (most reliable)
        int equipmentTier = detectTierFromEquipment(entity);
        if (equipmentTier > 0) {
            return equipmentTier;
        }

        // Method 2: Check metadata
        int metadataTier = detectTierFromMetadata(entity);
        if (metadataTier > 0) {
            return metadataTier;
        }

        // Method 3: Default based on entity type
        return getDefaultTierForEntityType(entity);
    }

    private int getDefaultTierForEntityType(LivingEntity entity) {
        return switch (entity.getType()) {
            case ZOMBIE, SKELETON, SPIDER -> 1;
            case CREEPER, ENDERMAN -> 2;
            case WITCH, VINDICATOR -> 3;
            case EVOKER, VEX -> 4;
            case WITHER_SKELETON -> 5;
            case WITHER, ENDER_DRAGON, WARDEN -> 6;
            default -> 1;
        };
    }

    private int detectTierFromMetadata(LivingEntity entity) {
        String[] tierKeys = {"equipTier", "dropTier", "customTier", "tier"};

        for (String key : tierKeys) {
            if (entity.hasMetadata(key)) {
                try {
                    if (entity.getMetadata(key).get(0).value() instanceof Integer) {
                        return entity.getMetadata(key).get(0).asInt();
                    } else {
                        return Integer.parseInt(entity.getMetadata(key).get(0).asString());
                    }
                } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                    // Try next key
                }
            }
        }
        return 0;
    }

    private boolean detectEliteStatus(LivingEntity entity) {
        // Check metadata first
        String[] eliteKeys = {"elite", "dropElite", "isElite"};
        for (String key : eliteKeys) {
            if (entity.hasMetadata(key)) {
                try {
                    return entity.getMetadata(key).get(0).asBoolean();
                } catch (Exception ignored) {
                    // Try next key
                }
            }
        }

        // Check equipment for enchantments
        if (entity.getEquipment() != null) {
            ItemStack[] equipment = {
                    entity.getEquipment().getItemInMainHand(),
                    entity.getEquipment().getHelmet(),
                    entity.getEquipment().getChestplate(),
                    entity.getEquipment().getLeggings(),
                    entity.getEquipment().getBoots()
            };

            for (ItemStack item : equipment) {
                if (item != null && !item.getType().isAir() &&
                        item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean shouldDropItems(LivingEntity entity, MobAnalysis analysis) {
        if (isCleanupKill(entity)) {
            if (logger.isLoggable(java.util.logging.Level.WARNING)) {
                logger.warning("§c[DropsHandler] Cleanup kill reached shouldDropItems - this should not happen!");
            }
            return false;
        }

        if (analysis.isWorldBoss()) {
            return true;
        }

        int baseDropRate = DropConfig.getDropRate(analysis.getTier());
        int dropRate = analysis.isElite() ? DropConfig.getEliteDropRate(analysis.getTier()) : baseDropRate;

        // Apply loot buff if active - thread-safe check
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (int) Math.ceil(dropRate * buffPercentage / 100.0);
        }

        boolean shouldDrop = ThreadLocalRandom.current().nextInt(100) < dropRate;

        // Track buff improvement - thread-safe
        if (shouldDrop && lootBuffManager.isBuffActive()) {
            int originalChance = analysis.isElite() ? DropConfig.getEliteDropRate(analysis.getTier()) : DropConfig.getDropRate(analysis.getTier());
            if (ThreadLocalRandom.current().nextInt(100) >= originalChance) {
                lootBuffManager.updateImprovedDrops();
            }
        }

        return shouldDrop;
    }

    private void handleNormalDrops(LivingEntity entity, MobAnalysis analysis) {
        int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
        ItemStack item = DropsManager.createDrop(analysis.getTier(), itemType);

        Item droppedItem = dropItemWithEnhancedEffects(entity, item, analysis.getTier());
        playEnhancedDropEffects(entity.getLocation(), analysis.getTier(), false);

        notifyPlayersOfDropWithEnhancement(entity, item, false);
    }

    private void handleEliteDrops(LivingEntity entity, MobAnalysis analysis) {
        int itemType = determineEliteItemType();
        int rarity = determineEliteRarity();

        ItemStack item = dropsManager.createDrop(analysis.getTier(), itemType, rarity);

        Item droppedItem = dropItemWithEnhancedEffects(entity, item, analysis.getTier());
        playEnhancedDropEffects(entity.getLocation(), analysis.getTier(), true);

        notifyPlayersOfDropWithEnhancement(entity, item, false);
    }

    private boolean isCleanupKill(LivingEntity entity) {
        if (entity == null) return false;

        try {
            boolean hasCleanupMarker = entity.hasMetadata("cleanup_kill") ||
                    entity.hasMetadata("no_drops") ||
                    entity.hasMetadata("system_kill");

            if (hasCleanupMarker && logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("§6[DropsHandler] §7Entity " + entity.getType() +
                        " marked as cleanup kill - skipping drop processing");
            }

            return hasCleanupMarker;

        } catch (Exception e) {
            if (logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.warning("§c[DropsHandler] Error checking cleanup kill status: " + e.getMessage());
            }
            return false;
        }
    }

    private Player extractPlayerDamager(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager instanceof Player) {
            return (Player) damager;
        }

        if (damager instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) damager;
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        if (damager instanceof org.bukkit.entity.Wolf) {
            org.bukkit.entity.Wolf wolf = (org.bukkit.entity.Wolf) damager;
            if (wolf.isTamed() && wolf.getOwner() instanceof Player) {
                return (Player) wolf.getOwner();
            }
        }

        return null;
    }

    private boolean markAsProcessed(LivingEntity entity) {
        UUID entityUuid = entity.getUniqueId();

        if (isCleanupKill(entity)) {
            return false;
        }

        if (processedMobs.contains(entityUuid) || entity.hasMetadata("dropsProcessed")) {
            return false;
        }

        entity.setMetadata("dropsProcessed", new FixedMetadataValue(plugin, true));
        processedMobs.add(entityUuid);
        return true;
    }

    /**
     * IMPROVED: Handle additional drops independently from main drops with enhanced loot buff integration
     */
    private void handleIndependentAdditionalDrops(LivingEntity entity, MobAnalysis analysis, boolean mainDropOccurred) {
        int tier = analysis.getTier();

        // Roll for gems independently (high chance)
        rollForGems(entity, tier);

        // Roll for crates independently (medium chance)
        rollForCrates(entity, tier);

        // Roll for scrolls independently (low chance)
        rollForScrolls(entity, tier);

        // Roll for teleport books independently (low chance)
        rollForTeleportBooks(entity, tier);

        if (logger.isLoggable(java.util.logging.Level.FINEST)) {
            logger.finest("§6[DropsHandler] §7Completed independent drop rolls for " + entity.getType() +
                    " (T" + tier + ") - Main drop occurred: " + mainDropOccurred);
        }
    }

    // ===== CLEANUP METHODS WITH ENHANCED MONITORING =====

    private int cleanupExpiredDamageTracking() {
        int removed = 0;
        Iterator<Map.Entry<UUID, MobDamageData>> iterator = mobDamageTracking.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, MobDamageData> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private int cleanupExpiredInteractions() {
        int removed = 0;
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Set<UUID>>> iterator = recentPlayerInteractions.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Set<UUID>> entry = iterator.next();
            if (currentTime - entry.getValue().hashCode() > INTERACTION_TRACKING_EXPIRY) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private int cleanupInvalidProcessedMobs() {
        int initialSize = processedMobs.size();

        List<UUID> toRemove = new ArrayList<>();

        for (UUID uuid : processedMobs) {
            if (!isEntityValidMainThread(uuid)) {
                toRemove.add(uuid);
            }
        }

        for (UUID uuid : toRemove) {
            processedMobs.remove(uuid);
            mobDamageTracking.remove(uuid);
            recentPlayerInteractions.remove(uuid);
        }

        int cleanupKillsRemoved = 0;
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (isCleanupKill(entity)) {
                        UUID entityUuid = entity.getUniqueId();
                        if (processedMobs.remove(entityUuid)) {
                            cleanupKillsRemoved++;
                        }
                        mobDamageTracking.remove(entityUuid);
                        recentPlayerInteractions.remove(entityUuid);
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail for cleanup kill cleanup
        }

        if (logger.isLoggable(java.util.logging.Level.FINE) && cleanupKillsRemoved > 0) {
            logger.info("§6[DropsHandler] §7Cleaned up " + cleanupKillsRemoved + " cleanup kill entities from tracking");
        }

        return initialSize - processedMobs.size();
    }

    private boolean isEntityValidMainThread(UUID entityUuid) {
        try {
            Entity entity = Bukkit.getEntity(entityUuid);
            return entity != null && entity.isValid() && !entity.isDead();
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        mobDamageTracking.clear();
        processedMobs.clear();
        recentPlayerInteractions.clear();
        logger.info("[DropsHandler] has been shut down with Adventure API support");
    }

    // ===== UTILITY METHODS AND HELPER CLASSES =====

    private static class DropInfo {
        final String playerName;
        final int damage;

        DropInfo(String playerName, int damage) {
            this.playerName = playerName;
            this.damage = damage;
        }
    }

    private int determineEliteItemType() {
        return ThreadLocalRandom.current().nextInt(100) < 40 ?
                ThreadLocalRandom.current().nextInt(4) + 1 : // Weapon (40%)
                ThreadLocalRandom.current().nextInt(4) + 5;   // Armor (60%)
    }

    private int determineEliteRarity() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 5) return 4;      // Unique - 5%
        else if (roll < 20) return 3; // Rare - 15%
        else if (roll < 50) return 2; // Uncommon - 30%
        else return 1;               // Common - 50%
    }

    private ItemStack createHighQualityItem(int tier) {
        int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
        int rarity = ThreadLocalRandom.current().nextInt(2) + 3; // Rare or Unique
        return dropsManager.createDrop(tier, itemType, rarity);
    }

    private Location calculateDropLocation(Location center, int index, int total) {
        double angle = 2 * Math.PI * index / total;
        double x = center.getX() + WORLD_BOSS_DROP_RADIUS * Math.cos(angle);
        double z = center.getZ() + WORLD_BOSS_DROP_RADIUS * Math.sin(angle);
        return new Location(center.getWorld(), x, center.getY(), z);
    }

    private Player getTopDamageDealer(LivingEntity entity) {
        UUID entityUuid = entity.getUniqueId();
        MobDamageData damageData = mobDamageTracking.get(entityUuid);

        if (damageData == null || !damageData.hasInteractions()) {
            if (logger.isLoggable(java.util.logging.Level.FINEST)) {
                logger.finest("§6[DropsHandler] §7No damage data found for entity: " +
                        entity.getType() + " (ID: " + entityUuid.toString().substring(0, 8) + ")");
            }
            return null;
        }

        List<Map.Entry<UUID, Double>> sortedDamagers = damageData.getSortedDamagers();

        if (sortedDamagers.isEmpty()) {
            Set<UUID> interactions = damageData.getRecentInteractions();
            if (!interactions.isEmpty()) {
                UUID randomInteractionUuid = interactions.iterator().next();
                Player player = Bukkit.getPlayer(randomInteractionUuid);
                if (player != null && player.isOnline()) {
                    if (logger.isLoggable(java.util.logging.Level.FINEST)) {
                        logger.finest("§6[DropsHandler] §7Using interaction fallback for: " + player.getName());
                    }
                    return player;
                }
            }
            return null;
        }

        for (Map.Entry<UUID, Double> entry : sortedDamagers) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                if (logger.isLoggable(java.util.logging.Level.FINEST)) {
                    logger.finest("§6[DropsHandler] §7Top damager found: " + player.getName() +
                            " with " + entry.getValue() + " damage");
                }
                return player;
            }
        }

        if (logger.isLoggable(java.util.logging.Level.FINEST)) {
            logger.finest("§6[DropsHandler] §7No online top damager found for entity: " +
                    entity.getType() + " (had " + sortedDamagers.size() + " damagers)");
        }

        return null;
    }

    private List<Map.Entry<UUID, Double>> getSortedDamageContributors(LivingEntity entity) {
        MobDamageData damageData = mobDamageTracking.get(entity.getUniqueId());
        return damageData != null ? damageData.getSortedDamagers() : new ArrayList<>();
    }

    // ===== PUBLIC API METHODS AND DEBUG FUNCTIONS WITH ADVENTURE API SUPPORT =====

    public void setDropRate(int tier, int rate) {
        dropsManager.setDropRate(tier, rate);
        TextUtil.broadcastCentered(ChatColor.YELLOW + "DROP RATES" + ChatColor.GRAY + " - " +
                ChatColor.AQUA + "Tier " + tier + " drop rates have been changed to " + rate + "%");
    }

    public int getDropRate(int tier) {
        return dropsManager.getDropRate(tier);
    }

    public Map<Integer, Integer> getGemDropRates() {
        return new HashMap<>(GEM_DROP_RATES);
    }

    public Map<Integer, Integer> getCrateDropRates() {
        return new HashMap<>(CRATE_DROP_RATES);
    }

    public Map<Integer, Integer> getTeleportBookDropRates() {
        return new HashMap<>(TELEPORT_BOOK_DROP_RATES);
    }

    public Map<Integer, Integer> getScrollDropRates() {
        return new HashMap<>(SCROLL_DROP_RATES);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeDamageTracking", mobDamageTracking.size());
        stats.put("processedMobs", processedMobs.size());
        stats.put("recentInteractions", recentPlayerInteractions.size());
        stats.put("totalDamageEntries", mobDamageTracking.values().stream()
                .mapToInt(data -> data.getDamageMap().size()).sum());
        stats.put("totalInteractionEntries", recentPlayerInteractions.values().stream()
                .mapToInt(Set::size).sum());

        int cleanupKillsInTracking = 0;
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (isCleanupKill(entity) && processedMobs.contains(entity.getUniqueId())) {
                        cleanupKillsInTracking++;
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail
        }

        stats.put("cleanupKillsInTracking", cleanupKillsInTracking);
        stats.put("teleportDestinationsAvailable", TeleportManager.getInstance().getAllDestinations().size());

        // Add independent drop rate statistics
        stats.put("gemDropRates", GEM_DROP_RATES);
        stats.put("crateDropRates", CRATE_DROP_RATES);
        stats.put("teleportBookDropRates", TELEPORT_BOOK_DROP_RATES);
        stats.put("scrollDropRates", SCROLL_DROP_RATES);

        stats.put("eliteConfigurationsLoaded", DropConfig.getEliteDropConfigs().size());

        // Add loot buff statistics
        stats.put("lootBuffActive", lootBuffManager.isBuffActive());
        stats.put("lootBuffImprovedDrops", lootBuffManager.getImprovedDrops());

        // Add Adventure API statistics
        stats.put("adventureApiEnabled", true);
        stats.put("enhancedEffectsEnabled", true);

        return stats;
    }

    // ===== DEBUG METHODS WITH ADVENTURE API ENHANCEMENTS =====

    public void debugTeleportBookDrop(Player player, int tier) {
        if (player == null) {
            return;
        }

        Collection<TeleportDestination> destinations = TeleportManager.getInstance().getAllDestinations();
        if (destinations.isEmpty()) {
            sendEnhancedMessage(player, ChatColor.RED + "No teleport destinations configured!");
            return;
        }

        try {
            List<TeleportDestination> destinationList = new ArrayList<>(destinations);
            TeleportDestination randomDestination = destinationList.get(
                    ThreadLocalRandom.current().nextInt(destinationList.size())
            );

            ItemStack teleportBook = TeleportBookSystem.getInstance().createTeleportBook(
                    randomDestination.getId(), false
            );

            if (teleportBook != null) {
                player.getInventory().addItem(teleportBook);
                sendEnhancedMessage(player, ChatColor.GREEN + "Debug: Added teleport book for " +
                        randomDestination.getDisplayName() + " (Tier " + tier + ")");

                Location playerLocation = player.getLocation().add(0, 1, 0);
                spawnEnhancedParticles(playerLocation, Particle.ENCHANT, 15, 0.5, 0.5, 0.5, 0.1);
                playEnhancedSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.5f, 1.5f);

                // Show enhanced title for debug
                showEnhancedTitle(player, "§bDebug Book Added", "§7" + randomDestination.getDisplayName(), 10, 40, 10);
            } else {
                sendEnhancedMessage(player, ChatColor.RED + "Failed to create teleport book!");
            }
        } catch (Exception e) {
            logger.warning("Error in debug teleport book drop: " + e.getMessage());
            sendEnhancedMessage(player, ChatColor.RED + "Error creating debug teleport book: " + e.getMessage());
        }
    }

    public void debugNotification(Player player, LivingEntity entity) {
        if (player != null && entity != null) {
            ItemStack testItem = dropsManager.createDrop(1, 1);
            lootNotifier.sendDropNotification(player, testItem, entity, false);

            // Enhanced debug notification
            showEnhancedTitle(player, "§eDebug Notification", "§7Test drop notification sent", 10, 30, 10);

            logger.info("§6[DropsHandler] §7Enhanced debug notification sent to " + player.getName());
        }
    }

    public void debugNamedEliteDetection(Player player, LivingEntity entity) {
        if (player == null || entity == null) {
            return;
        }

        MobAnalysis analysis = analyzeMob(entity);

        sendEnhancedMessage(player, ChatColor.GOLD + "=== Enhanced Named Elite Debug ===");
        sendEnhancedMessage(player, ChatColor.YELLOW + "Mob Type: " + ChatColor.WHITE + analysis.getMobType());
        sendEnhancedMessage(player, ChatColor.YELLOW + "Actual Name: " + ChatColor.WHITE + analysis.getActualMobName());
        sendEnhancedMessage(player, ChatColor.YELLOW + "Tier: " + ChatColor.WHITE + analysis.getTier());
        sendEnhancedMessage(player, ChatColor.YELLOW + "Is Elite: " + ChatColor.WHITE + analysis.isElite());
        sendEnhancedMessage(player, ChatColor.YELLOW + "Is Named Elite: " + ChatColor.WHITE + analysis.isNamedElite());
        sendEnhancedMessage(player, ChatColor.YELLOW + "Is World Boss: " + ChatColor.WHITE + analysis.isWorldBoss());

        if (analysis.getMobType() != null) {
            boolean hasConfig = DropConfig.getEliteDropConfig(analysis.getMobType()) != null;
            sendEnhancedMessage(player, ChatColor.YELLOW + "Has Elite Config: " + ChatColor.WHITE + hasConfig);

            if (hasConfig) {
                EliteDropConfig config = DropConfig.getEliteDropConfig(analysis.getMobType());
                List<Integer> configuredItemTypes = new ArrayList<>();

                // Check each possible item type (1-8) to see if this elite has it configured
                for (int itemType = 1; itemType <= 8; itemType++) {
                    com.rednetty.server.core.mechanics.item.drops.types.ItemDetails itemDetails = config.getItemDetailsForType(itemType);
                    if (itemDetails != null) {
                        configuredItemTypes.add(itemType);
                    }
                }

                sendEnhancedMessage(player, ChatColor.YELLOW + "Configured Item Types: " + ChatColor.WHITE + configuredItemTypes);
            }
        }

        sendEnhancedMessage(player, ChatColor.GOLD + "===================");

        // Show enhanced title for debug results
        showEnhancedTitle(player, "§6Elite Debug Complete", "§7Check chat for results", 10, 40, 10);
    }

    public void debugIndependentDrops(Player player, int tier) {
        if (player == null) {
            return;
        }

        sendEnhancedMessage(player, ChatColor.GOLD + "=== Enhanced Independent Drop Rates (Tier " + tier + ") ===");
        sendEnhancedMessage(player, ChatColor.YELLOW + "Gems: " + ChatColor.WHITE + GEM_DROP_RATES.getOrDefault(tier, 0) + "%");
        sendEnhancedMessage(player, ChatColor.YELLOW + "Crates: " + ChatColor.WHITE + CRATE_DROP_RATES.getOrDefault(tier, 0) + "%");
        sendEnhancedMessage(player, ChatColor.YELLOW + "Scrolls: " + ChatColor.WHITE + SCROLL_DROP_RATES.getOrDefault(tier, 0) + "%");
        sendEnhancedMessage(player, ChatColor.YELLOW + "Teleport Books: " + ChatColor.WHITE + TELEPORT_BOOK_DROP_RATES.getOrDefault(tier, 0) + "%");

        if (lootBuffManager.isBuffActive()) {
            int buffRate = lootBuffManager.getActiveBuff().getBuffRate();
            sendEnhancedMessage(player, ChatColor.GREEN + "Loot Buff Active: +" + buffRate + "%");
            sendEnhancedMessage(player, ChatColor.GREEN + "Improved Drops: " + lootBuffManager.getImprovedDrops());
        } else {
            sendEnhancedMessage(player, ChatColor.RED + "No loot buff active");
        }

        sendEnhancedMessage(player, ChatColor.GOLD + "===========================");

        // Show enhanced title for debug results
        showEnhancedTitle(player, "§aDrop Debug Complete", "§7Tier " + tier + " rates shown", 10, 40, 10);
    }

    /**
     * Enhanced mob detection result with better named elite detection
     */
    private static class MobAnalysis {
        private final int tier;
        private final boolean isElite;
        private final boolean isWorldBoss;
        private final boolean isNamedElite;
        private final String mobType;
        private final String actualMobName;

        public MobAnalysis(int tier, boolean isElite, boolean isWorldBoss, boolean isNamedElite, String mobType, String actualMobName) {
            this.tier = tier;
            this.isElite = isElite;
            this.isWorldBoss = isWorldBoss;
            this.isNamedElite = isNamedElite;
            this.mobType = mobType;
            this.actualMobName = actualMobName;
        }

        // Getters
        public int getTier() { return tier; }
        public boolean isElite() { return isElite; }
        public boolean isWorldBoss() { return isWorldBoss; }
        public boolean isNamedElite() { return isNamedElite; }
        public String getMobType() { return mobType; }
        public String getActualMobName() { return actualMobName; }
    }
}
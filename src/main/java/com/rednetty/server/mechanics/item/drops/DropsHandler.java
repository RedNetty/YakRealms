package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced event handler for item drops from mobs with improved performance and visual effects
 * Fixed async entity access issues by moving validation to main thread
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

    // Enhanced damage tracking with better performance
    private final Map<UUID, MobDamageData> mobDamageTracking = new ConcurrentHashMap<>();
    private final Set<UUID> processedMobs = ConcurrentHashMap.newKeySet();

    // Constants for better maintainability
    private static final long CLEANUP_INTERVAL_TICKS = 1200L; // 1 minute
    private static final long DAMAGE_TRACKING_EXPIRY = TimeUnit.MINUTES.toMillis(5); // 5 minutes
    private static final int WORLD_BOSS_DROP_COUNT_MIN = 3;
    private static final int WORLD_BOSS_DROP_COUNT_MAX = 5;
    private static final double WORLD_BOSS_DROP_RADIUS = 2.0;
    private static final int DEFAULT_PROTECTION_SECONDS = 5;
    private static final int WORLD_BOSS_PROTECTION_SECONDS = 30;

    // Enhanced visual effects
    private static final Map<Integer, Particle> TIER_PARTICLES = Map.of(
            1, Particle.VILLAGER_HAPPY,
            2, Particle.ENCHANTMENT_TABLE,
            3, Particle.DRIP_WATER,
            4, Particle.PORTAL,
            5, Particle.FLAME,
            6, Particle.SNOWBALL
    );

    /**
     * Enhanced damage tracking data structure
     */
    private static class MobDamageData {
        private final Map<UUID, Double> damageMap = new ConcurrentHashMap<>();
        private final long creationTime = System.currentTimeMillis();

        public void addDamage(UUID playerUuid, double damage) {
            damageMap.merge(playerUuid, damage, Double::sum);
        }

        public Map<UUID, Double> getDamageMap() {
            return damageMap;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - creationTime > DAMAGE_TRACKING_EXPIRY;
        }

        public List<Map.Entry<UUID, Double>> getSortedDamagers() {
            return damageMap.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Enhanced mob detection result
     */
    private static class MobAnalysis {
        private final int tier;
        private final boolean isElite;
        private final boolean isWorldBoss;
        private final boolean isNamedElite;
        private final String mobType;

        public MobAnalysis(int tier, boolean isElite, boolean isWorldBoss, boolean isNamedElite, String mobType) {
            this.tier = tier;
            this.isElite = isElite;
            this.isWorldBoss = isWorldBoss;
            this.isNamedElite = isNamedElite;
            this.mobType = mobType;
        }

        // Getters
        public int getTier() { return tier; }
        public boolean isElite() { return isElite; }
        public boolean isWorldBoss() { return isWorldBoss; }
        public boolean isNamedElite() { return isNamedElite; }
        public String getMobType() { return mobType; }
    }
    /**
     * Enhanced tier detection with multiple fallback methods
     */
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

    /**
     * Detect tier from equipment with enhanced logic for Tier 6 Netherite
     */
    private int detectTierFromEquipment(LivingEntity entity) {
        if (entity.getEquipment() == null) {
            return 0;
        }

        ItemStack mainHand = entity.getEquipment().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != org.bukkit.Material.AIR) {
            String material = mainHand.getType().name();

            // Enhanced material-based tier detection with Netherite support
            if (material.contains("NETHERITE")) {
                return 6; // Tier 6 - Netherite
            } else if (material.contains("DIAMOND")) {
                // Check for special dark purple display name for enhanced Netherite items
                if (mainHand.hasItemMeta() && mainHand.getItemMeta().hasDisplayName() &&
                        mainHand.getItemMeta().getDisplayName().contains(ChatColor.DARK_PURPLE.toString())) {
                    return 6; // Enhanced Netherite
                }
                return 4; // Regular Diamond
            } else if (material.contains("GOLDEN") || material.contains("GOLD")) {
                return 5; // Tier 5 - Gold
            } else if (material.contains("IRON")) {
                return 3; // Tier 3 - Iron
            } else if (material.contains("STONE")) {
                return 2; // Tier 2 - Stone
            } else if (material.contains("WOODEN") || material.contains("WOOD")) {
                return 1; // Tier 1 - Wood
            }
        }
        return 0;
    }

    /**
     * Detect tier from metadata with multiple key checking
     */
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

    /**
     * Get default tier based on entity type with enhanced mappings
     */
    private int getDefaultTierForEntityType(LivingEntity entity) {
        return switch (entity.getType()) {
            case ZOMBIE, SKELETON, SPIDER -> 1;
            case CREEPER, ENDERMAN -> 2;
            case WITCH, VINDICATOR -> 3;
            case EVOKER, VEX -> 4;
            case WITHER_SKELETON -> 5;
            case WITHER, ENDER_DRAGON -> 6; // Bosses get Tier 6
            default -> 1;
        };
    }
    /**
     * Private constructor for singleton pattern
     */
    private DropsHandler() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.dropsManager = DropsManager.getInstance();
        this.dropFactory = DropFactory.getInstance();
        this.mobManager = MobManager.getInstance();
        this.lootNotifier = LootNotifier.getInstance();
        this.lootBuffManager = LootBuffManager.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The DropsHandler instance
     */
    public static synchronized DropsHandler getInstance() {
        if (instance == null) {
            instance = new DropsHandler();
        }
        return instance;
    }

    /**
     * Initializes the drops handler with enhanced cleanup system
     */
    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startEnhancedCleanupTask();
        logger.info("[DropsHandler] has been initialized with enhanced features");
    }

    /**
     * Enhanced cleanup task with better performance monitoring and thread safety
     */
    private void startEnhancedCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int cleanedDamage = cleanupExpiredDamageTracking();


                Bukkit.getScheduler().runTask(plugin, () -> {
                    int cleanedProcessed = cleanupInvalidProcessedMobs();

                    if (cleanedDamage > 0 || cleanedProcessed > 0) {
                        logger.fine(String.format("Cleanup completed: %d damage entries, %d processed mobs removed",
                                cleanedDamage, cleanedProcessed));
                    }
                });
            }
        }.runTaskTimerAsynchronously(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    /**
     * Cleanup expired damage tracking entries
     */
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

    /**
     * Cleanup processed mobs that are no longer valid
     * FIXED: This method must run on main thread to avoid async chunk access
     */
    private int cleanupInvalidProcessedMobs() {
        int initialSize = processedMobs.size();

        // Create a list of UUIDs to check and remove invalid ones
        List<UUID> toRemove = new ArrayList<>();

        for (UUID uuid : processedMobs) {
            if (!isEntityValidMainThread(uuid)) {
                toRemove.add(uuid);
            }
        }

        // Remove invalid UUIDs
        for (UUID uuid : toRemove) {
            processedMobs.remove(uuid);
            // Also clean up damage tracking for invalid entities
            mobDamageTracking.remove(uuid);
        }

        return initialSize - processedMobs.size();
    }

    /**
     * FIXED: Check if entity is still valid - must run on main thread
     */
    private boolean isEntityValidMainThread(UUID entityUuid) {
        try {
            Entity entity = Bukkit.getEntity(entityUuid);
            return entity != null && entity.isValid() && !entity.isDead();
        } catch (Exception e) {
            // If any error occurs, consider entity invalid
            return false;
        }
    }

    /**
     * Shuts down the drops handler
     */
    public void shutdown() {
        mobDamageTracking.clear();
        processedMobs.clear();
        logger.info("[DropsHandler] has been shut down");
    }

    /**
     * Enhanced mob death handler with better organization
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobDeath(EntityDeathEvent event) {
        // Clear default drops immediately
        event.getDrops().clear();
        event.setDroppedExp(0);

        LivingEntity entity = event.getEntity();

        // Skip players
        if (entity instanceof Player) {
            return;
        }

        // Prevent double processing
        if (!markAsProcessed(entity)) {
            return;
        }

        // Process drops asynchronously to avoid blocking
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    handleEnhancedMobDrops(entity);
                } catch (Exception e) {
                    logger.warning("Error processing drops for entity " + entity.getType() + ": " + e.getMessage());
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Enhanced damage tracking with better performance
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }

        Player damager = extractPlayerDamager(event);
        if (damager == null) {
            return;
        }

        UUID entityUuid = event.getEntity().getUniqueId();
        UUID playerUuid = damager.getUniqueId();
        double damage = event.getFinalDamage();

        // Use computeIfAbsent for thread-safe initialization
        mobDamageTracking.computeIfAbsent(entityUuid, k -> new MobDamageData())
                .addDamage(playerUuid, damage);
    }

    /**
     * Enhanced player damager extraction
     */
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

        return null;
    }

    /**
     * Marks entity as processed to prevent double drops
     */
    private boolean markAsProcessed(LivingEntity entity) {
        UUID entityUuid = entity.getUniqueId();

        if (processedMobs.contains(entityUuid) || entity.hasMetadata("dropsProcessed")) {
            return false;
        }

        entity.setMetadata("dropsProcessed", new FixedMetadataValue(plugin, true));
        processedMobs.add(entityUuid);
        return true;
    }

    /**
     * Enhanced mob drop handling with better organization
     */
    private void handleEnhancedMobDrops(LivingEntity entity) {
        MobAnalysis analysis = analyzeMob(entity);

        // Check if this entity should drop items
        if (!shouldDropItems(entity, analysis)) {
            return;
        }

        // Handle different mob types with enhanced logic
        if (analysis.isWorldBoss()) {
            handleWorldBossDrops(entity, analysis);
        } else if (analysis.isNamedElite()) {
            handleNamedEliteDrops(entity, analysis);
        } else if (analysis.isElite()) {
            handleEliteDrops(entity, analysis);
        } else {
            handleNormalDrops(entity, analysis);
        }

        // Handle additional drops (gems, scrolls, crates) for all mob types
        handleAdditionalDrops(entity, analysis);
    }

    /**
     * Enhanced mob analysis with comprehensive detection
     */
    private MobAnalysis analyzeMob(LivingEntity entity) {
        int tier = detectMobTier(entity);
        boolean isElite = detectEliteStatus(entity);
        String mobType = extractMobType(entity);
        boolean isNamedElite = isElite && mobType != null && DropConfig.getEliteDropConfig(mobType.toLowerCase()) != null;

        return new MobAnalysis(tier, isElite, false, isNamedElite, mobType);
    }

    /**
     * Enhanced elite status detection
     */
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

    /**
     * Extract mob type from metadata
     */
    private String extractMobType(LivingEntity entity) {
        if (entity.hasMetadata("type")) {
            try {
                return entity.getMetadata("type").get(0).asString();
            } catch (Exception ignored) {
                // Fall through
            }
        }
        return null;
    }

    /**
     * Enhanced drop decision logic
     */
    private boolean shouldDropItems(LivingEntity entity, MobAnalysis analysis) {
        if (analysis.isWorldBoss()) {
            return true; // World bosses always drop
        }

        int baseDropRate = DropConfig.getDropRate(analysis.getTier());
        int dropRate = analysis.isElite() ? DropConfig.getEliteDropRate(analysis.getTier()) : baseDropRate;

        // Apply loot buff if active
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            dropRate += (dropRate * buffPercentage / 100);
        }

        boolean shouldDrop = ThreadLocalRandom.current().nextInt(100) < dropRate;

        // Track improved drops from buff
        if (shouldDrop && lootBuffManager.isBuffActive()) {
            int originalChance = analysis.isElite() ? DropConfig.getEliteDropRate(analysis.getTier()) : DropConfig.getDropRate(analysis.getTier());
            if (ThreadLocalRandom.current().nextInt(100) >= originalChance) {
                lootBuffManager.updateImprovedDrops();
            }
        }

        return shouldDrop;
    }

    /**
     * Enhanced world boss drop handling
     */
    private void handleWorldBossDrops(LivingEntity entity, MobAnalysis analysis) {
        int dropCount = ThreadLocalRandom.current().nextInt(
                WORLD_BOSS_DROP_COUNT_MAX - WORLD_BOSS_DROP_COUNT_MIN + 1) + WORLD_BOSS_DROP_COUNT_MIN;

        List<Map.Entry<UUID, Double>> sortedDamagers = getSortedDamageContributors(entity);
        List<DropInfo> drops = new ArrayList<>();

        Location center = entity.getLocation();
        String bossName = entity.getCustomName() != null ?
                ChatColor.stripColor(entity.getCustomName()) : "World Boss";

        for (int i = 0; i < dropCount; i++) {
            Location dropLocation = calculateDropLocation(center, i, dropCount);
            ItemStack item = createHighQualityItem(analysis.getTier());

            Item droppedItem = entity.getWorld().dropItem(dropLocation, item);
            applyWorldBossEffects(droppedItem, dropLocation);

            // Assign protection to top damagers
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

        // Enhanced world boss defeat announcement
        lootNotifier.announceWorldBossDefeat(bossName, drops.stream()
                .map(drop -> new Object[]{drop.playerName, drop.damage})
                .distinct()
                .limit(3)
                .collect(Collectors.toList()));

        // Enhanced sound and particle effects
        playWorldBossDefeatEffects(center);
    }

    /**
     * Enhanced normal mob drop handling
     */
    private void handleNormalDrops(LivingEntity entity, MobAnalysis analysis) {
        int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
        ItemStack item = dropsManager.createDrop(analysis.getTier(), itemType);

        Item droppedItem = dropItemWithEnhancedEffects(entity, item, analysis.getTier());
        playDropEffects(entity.getLocation(), analysis.getTier(), false);

        Player topDamager = getTopDamageDealer(entity);
        if (topDamager != null) {
            lootNotifier.sendDropNotification(topDamager, item, entity, false);
        }
    }

    /**
     * Enhanced elite mob drop handling
     */
    private void handleEliteDrops(LivingEntity entity, MobAnalysis analysis) {
        int itemType = determineEliteItemType();
        int rarity = determineEliteRarity();

        ItemStack item = dropsManager.createDrop(analysis.getTier(), itemType, rarity);

        Item droppedItem = dropItemWithEnhancedEffects(entity, item, analysis.getTier());
        playDropEffects(entity.getLocation(), analysis.getTier(), true);

        Player topDamager = getTopDamageDealer(entity);
        if (topDamager != null) {
            lootNotifier.sendDropNotification(topDamager, item, entity, false);
        }
    }

    /**
     * Enhanced named elite drop handling
     */
    private void handleNamedEliteDrops(LivingEntity entity, MobAnalysis analysis) {
        int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
        ItemStack item = dropsManager.createEliteDrop(analysis.getMobType(), itemType, analysis.getTier());

        Item droppedItem = dropItemWithEnhancedEffects(entity, item, analysis.getTier());
        playDropEffects(entity.getLocation(), analysis.getTier(), true);

        // Notify nearby players for special elites
        entity.getNearbyEntities(30, 30, 30).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .forEach(player -> lootNotifier.sendDropNotification(player, item, entity, false));
    }

    /**
     * Handle additional drops (gems, scrolls, crates)
     */
    private void handleAdditionalDrops(LivingEntity entity, MobAnalysis analysis) {
        // Gems (50% chance)
        if (ThreadLocalRandom.current().nextBoolean()) {
            dropGems(entity, analysis.getTier());
        }

        // Scrolls (5% chance)
        if (ThreadLocalRandom.current().nextInt(100) < 5) {
            dropScrolls(entity, analysis.getTier());
        }

        // Crates (tier-based chance)
        dropCrates(entity, analysis.getTier());
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

        return droppedItem;
    }

    /**
     * Enhanced drop effects with tier-based particles and sounds
     */
    private void playDropEffects(Location location, int tier, boolean isElite) {
        // Particle effects
        Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.VILLAGER_HAPPY);
        location.getWorld().spawnParticle(particle, location.add(0, 1, 0),
                isElite ? 20 : 10, 0.5, 0.5, 0.5, 0.1);

        // Sound effects
        Sound sound = isElite ? dropsManager.getRaritySound(3) : dropsManager.getTierSound(tier);
        location.getWorld().playSound(location, sound, 1.0f, 1.0f);
    }

    // Helper classes and methods
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

    private void applyWorldBossEffects(Item droppedItem, Location location) {
        location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location, 30, 1, 1, 1, 0.1);
        droppedItem.setUnlimitedLifetime(true);
    }

    private void playWorldBossDefeatEffects(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.5f);
        location.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, location, 5, 2, 2, 2, 0);
    }

    private void dropGems(LivingEntity entity, int tier) {
        ItemStack gemsItem = dropFactory.createGemDrop(tier);
        dropItemWithEnhancedEffects(entity, gemsItem, tier);
    }

    private void dropScrolls(LivingEntity entity, int tier) {
        try {
            ItemStack scroll = dropFactory.createScrollDrop(tier);
            if (scroll != null) {
                Item droppedItem = dropItemWithEnhancedEffects(entity, scroll, tier);
                Player topDamager = getTopDamageDealer(entity);
                if (topDamager != null) {
                    lootNotifier.sendDropNotification(topDamager, scroll, entity, false);
                }
            }
        } catch (Exception e) {
            logger.warning("Error creating teleport scroll drop: " + e.getMessage());
        }
    }

    private void dropCrates(LivingEntity entity, int tier) {
        int crateRate = DropConfig.getCrateDropRate(tier);

        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            crateRate += (crateRate * buffPercentage / 100);
        }

        if (ThreadLocalRandom.current().nextInt(100) < crateRate) {
            ItemStack crate = dropFactory.createCrateDrop(tier);
            Item droppedItem = dropItemWithEnhancedEffects(entity, crate, tier);

            Player topDamager = getTopDamageDealer(entity);
            if (topDamager != null) {
                lootNotifier.sendDropNotification(topDamager, crate, entity, false);
            }

            if (lootBuffManager.isBuffActive()) {
                lootBuffManager.updateImprovedDrops();
            }
        }
    }

    private Player getTopDamageDealer(LivingEntity entity) {
        MobDamageData damageData = mobDamageTracking.get(entity.getUniqueId());
        if (damageData == null) {
            return null;
        }

        return damageData.getSortedDamagers().stream()
                .findFirst()
                .map(Map.Entry::getKey)
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .orElse(null);
    }

    private List<Map.Entry<UUID, Double>> getSortedDamageContributors(LivingEntity entity) {
        MobDamageData damageData = mobDamageTracking.get(entity.getUniqueId());
        return damageData != null ? damageData.getSortedDamagers() : new ArrayList<>();
    }

    // Public API methods
    public void setDropRate(int tier, int rate) {
        dropsManager.setDropRate(tier, rate);
        TextUtil.broadcastCentered(ChatColor.YELLOW + "DROP RATES" + ChatColor.GRAY + " - " +
                ChatColor.AQUA + "Tier " + tier + " drop rates have been changed to " + rate + "%");
    }

    public int getDropRate(int tier) {
        return dropsManager.getDropRate(tier);
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeDamageTracking", mobDamageTracking.size());
        stats.put("processedMobs", processedMobs.size());
        stats.put("totalDamageEntries", mobDamageTracking.values().stream()
                .mapToInt(data -> data.getDamageMap().size()).sum());
        return stats;
    }
}
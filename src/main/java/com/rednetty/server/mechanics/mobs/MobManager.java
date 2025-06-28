package com.rednetty.server.mechanics.mobs;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.mobs.core.CustomMob;
import com.rednetty.server.mechanics.mobs.core.EliteMob;
import com.rednetty.server.mechanics.mobs.core.MobType;
import com.rednetty.server.mechanics.mobs.core.WorldBoss;
import com.rednetty.server.mechanics.mobs.spawners.MobSpawner;
import com.rednetty.server.mechanics.mobs.spawners.SpawnerMetrics;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * FIXED: Completely overhauled MobManager with reliable spawning and proper synchronous operations
 */
public class MobManager implements Listener {

    // ================ CONSTANTS ================
    private static final long MIN_RESPAWN_DELAY = 180000L; // 3 minutes
    private static final long MAX_RESPAWN_DELAY = 900000L; // 15 minutes
    private static final double MAX_WANDERING_DISTANCE = 25.0;
    private static final long POSITION_CHECK_INTERVAL = 100L; // 5 seconds
    private static final long NAME_VISIBILITY_TIMEOUT = 6500L; // 6.5 seconds
    private static final long CRITICAL_STATE_INTERVAL = 8L; // 8 ticks
    private static final long HEALTH_BAR_TIMEOUT = 6500L; // 6.5 seconds
    private static final long CRITICAL_SOUND_INTERVAL = 2000L; // 2 seconds
    private static final long ENTITY_VALIDATION_INTERVAL = 5000L; // 5 seconds

    // ================ SINGLETON ================
    private static volatile MobManager instance;

    // ================ CORE MAPS WITH THREAD SAFETY ================
    private final Map<UUID, CustomMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<LivingEntity, Integer> critMobs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> soundTimes = new ConcurrentHashMap<>();
    private final Map<UUID, NameTrackingData> nameTrackingData = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> damageContributions = new ConcurrentHashMap<>();
    private final Map<Entity, Player> mobTargets = new ConcurrentHashMap<>();
    private final Set<UUID> processedEntities = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> lastSafespotCheck = new ConcurrentHashMap<>();
    private final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();

    // ================ NAME TRACKING ================
    private static class NameTrackingData {
        private final String originalName;
        private final long lastDamageTime;
        private final boolean isInCriticalState;
        private volatile boolean nameVisible;
        private volatile boolean isHealthBarActive;

        public NameTrackingData(String originalName, long lastDamageTime, boolean isInCriticalState) {
            this.originalName = originalName;
            this.lastDamageTime = lastDamageTime;
            this.isInCriticalState = isInCriticalState;
            this.nameVisible = true;
            this.isHealthBarActive = true;
        }

        public String getOriginalName() { return originalName; }
        public long getLastDamageTime() { return lastDamageTime; }
        public boolean isInCriticalState() { return isInCriticalState; }
        public boolean isNameVisible() { return nameVisible; }
        public void setNameVisible(boolean visible) { this.nameVisible = visible; }
        public boolean isHealthBarActive() { return isHealthBarActive; }
        public void setHealthBarActive(boolean active) { this.isHealthBarActive = active; }
    }

    // ================ THREAD SAFETY ================
    private final ReentrantReadWriteLock mobLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock nameLock = new ReentrantReadWriteLock();

    // ================ RESPAWN TRACKING - SIMPLIFIED ================
    private final Map<String, Long> respawnTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> mobTypeLastDeath = new ConcurrentHashMap<>();
    private final Map<UUID, Location> mobSpawnerLocations = new ConcurrentHashMap<>();

    // ================ COMPONENTS ================
    private final MobSpawner spawner;
    private final Logger logger;
    private final YakRealms plugin;
    private WorldBoss activeWorldBoss;

    // ================ CONFIGURATION ================
    private volatile boolean spawnersEnabled = true;
    private volatile boolean debug = false;
    private volatile int maxMobsPerSpawner = 10;
    private volatile double playerDetectionRange = 40.0;
    private volatile double mobRespawnDistanceCheck = 25.0;

    // ================ TASKS - SIMPLIFIED ================
    private BukkitTask mainTask;
    private BukkitTask cleanupTask;

    /**
     * Constructor - simplified initialization
     */
    private MobManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.spawner = MobSpawner.getInstance();
        this.debug = plugin.isDebugMode();
        loadConfiguration();
    }

    public static MobManager getInstance() {
        if (instance == null) {
            synchronized (MobManager.class) {
                if (instance == null) {
                    instance = new MobManager();
                }
            }
        }
        return instance;
    }

    private void loadConfiguration() {
        maxMobsPerSpawner = plugin.getConfig().getInt("mechanics.mobs.max-mobs-per-spawner", 10);
        playerDetectionRange = plugin.getConfig().getDouble("mechanics.mobs.player-detection-range", 40.0);
        mobRespawnDistanceCheck = plugin.getConfig().getDouble("mechanics.mobs.mob-respawn-distance-check", 25.0);

        // Validate ranges
        maxMobsPerSpawner = Math.max(1, Math.min(50, maxMobsPerSpawner));
        playerDetectionRange = Math.max(10.0, Math.min(100.0, playerDetectionRange));
        mobRespawnDistanceCheck = Math.max(5.0, Math.min(50.0, mobRespawnDistanceCheck));
    }

    public void initialize() {
        try {
            spawner.initialize();
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startTasks();

            logger.info(String.format("§a[MobManager] §7Initialized successfully with §e%d §7spawners",
                    spawner.getAllSpawners().size()));

            if (debug) {
                logger.info("§6[MobManager] §7Debug mode enabled with enhanced tracking");
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Simplified task management - only essential tasks
     */
    private void startTasks() {
        logger.info("§6[MobManager] §7Starting essential tasks...");

        // Main processing task - handles all mob logic synchronously
        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    processCriticalState();
                    updateNameVisibility();
                    updateActiveMobs();
                    checkMobPositions();
                    validateEntityTracking();
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Main task error: " + e.getMessage());
                    if (debug) e.printStackTrace();
                }
            }
        }.runTaskTimer(plugin, 20L, 8L); // Every 8 ticks for responsiveness

        // Cleanup task - less frequent
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    performCleanup();
                } catch (Exception e) {
                    logger.warning("§c[MobManager] Cleanup task error: " + e.getMessage());
                }
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Every minute

        logger.info("§a[MobManager] §7Essential tasks started successfully");
    }

    // ================ FIXED: SIMPLIFIED SPAWNER MOB SPAWNING ================

    /**
     * FIXED: Completely rewritten spawner mob spawning - reliable and simple
     * This method MUST work for spawners to function properly
     */
    public LivingEntity spawnMobFromSpawner(Location location, String type, int tier, boolean elite) {
        if (!isValidSpawnRequest(location, type, tier)) {
            if (debug) {
                logger.warning("§c[MobManager] Invalid spawn request: " + type + " T" + tier);
            }
            return null;
        }

        try {
            // FIXED: Direct entity creation without complicated validation
            Location spawnLoc = getSafeSpawnLocation(location);
            LivingEntity entity = createEntityDirectly(spawnLoc, type, tier, elite);

            if (entity != null) {
                // Apply all mob properties
                setupMobProperties(entity, type, tier, elite);

                // Register with tracking systems
                registerSpawnedMob(entity, location);

                if (debug) {
                    logger.info("§a[MobManager] §7Successfully spawned " + type + " T" + tier + (elite ? "+" : "") +
                            " at " + formatLocation(spawnLoc));
                }

                return entity;
            } else {
                logger.warning("§c[MobManager] Failed to create entity for " + type);
            }
        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical error spawning mob: " + e.getMessage());
            if (debug) e.printStackTrace();
        }

        return null;
    }

    /**
     * FIXED: Direct entity creation without CustomMob complexity for spawners
     */
    private LivingEntity createEntityDirectly(Location location, String type, int tier, boolean elite) {
        try {
            MobType mobType = MobType.getById(type);
            if (mobType == null) {
                logger.warning("§c[MobManager] Invalid mob type: " + type);
                return null;
            }

            // Create the entity
            EntityType entityType = mobType.getEntityType();
            Entity spawnedEntity = location.getWorld().spawnEntity(location, entityType);

            if (!(spawnedEntity instanceof LivingEntity)) {
                spawnedEntity.remove();
                logger.warning("§c[MobManager] Spawned entity is not a LivingEntity: " + entityType);
                return null;
            }

            LivingEntity entity = (LivingEntity) spawnedEntity;

            // Prevent removal
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);

            return entity;

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error creating entity: " + e.getMessage());
            return null;
        }
    }

    /**
     * FIXED: Setup all mob properties reliably
     */
    private void setupMobProperties(LivingEntity entity, String type, int tier, boolean elite) {
        try {
            // Set essential metadata for identification
            setEntityMetadata(entity, type, tier, elite);

            // Configure appearance and name
            configureEntityAppearance(entity, type, tier, elite);

            // Apply equipment
            applyEquipment(entity, tier, elite);

            // Set health
            setEntityHealth(entity, tier, elite);

            // Apply movement effects
            applyMovementEffects(entity, tier, elite);

            // Entity-specific setup
            applyEntityTypeSetup(entity, type);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error setting up mob properties: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }

    /**
     * Set essential metadata for mob identification
     */
    private void setEntityMetadata(LivingEntity entity, String type, int tier, boolean elite) {
        entity.setMetadata("type", new FixedMetadataValue(plugin, type));
        entity.setMetadata("tier", new FixedMetadataValue(plugin, String.valueOf(tier)));
        entity.setMetadata("customTier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));
        entity.setMetadata("customName", new FixedMetadataValue(plugin, type));
        entity.setMetadata("dropTier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("dropElite", new FixedMetadataValue(plugin, elite));
    }

    /**
     * Configure entity appearance and name
     */
    private void configureEntityAppearance(LivingEntity entity, String type, int tier, boolean elite) {
        MobType mobType = MobType.getById(type);
        String tierSpecificName = mobType != null ? mobType.getTierSpecificName(tier) : formatMobName(type);
        ChatColor tierColor = getTierColor(tier);

        String formattedName = elite ?
                tierColor.toString() + ChatColor.BOLD + tierSpecificName :
                tierColor + tierSpecificName;

        entity.setCustomName(formattedName);
        entity.setCustomNameVisible(true);
        entity.setMetadata("name", new FixedMetadataValue(plugin, formattedName));
    }

    /**
     * Apply equipment to the mob
     */
    private void applyEquipment(LivingEntity entity, int tier, boolean elite) {
        try {
            if (entity.getEquipment() == null) return;

            entity.getEquipment().clear();

            // Create weapon
            ItemStack weapon = createWeaponForTier(tier, elite);
            if (weapon != null) {
                entity.getEquipment().setItemInMainHand(weapon);
                entity.getEquipment().setItemInMainHandDropChance(0);
            }

            // Create armor
            ItemStack[] armor = createArmorForTier(tier, elite);
            if (armor != null) {
                entity.getEquipment().setArmorContents(armor);
                entity.getEquipment().setHelmetDropChance(0);
                entity.getEquipment().setChestplateDropChance(0);
                entity.getEquipment().setLeggingsDropChance(0);
                entity.getEquipment().setBootsDropChance(0);
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error applying equipment: " + e.getMessage());
        }
    }

    /**
     * FIXED: Simple weapon creation based on tier
     */
    private ItemStack createWeaponForTier(int tier, boolean elite) {
        Material weaponMaterial;

        switch (tier) {
            case 1:
                weaponMaterial = Material.WOODEN_SWORD;
                break;
            case 2:
                weaponMaterial = Material.STONE_SWORD;
                break;
            case 3:
                weaponMaterial = Material.IRON_SWORD;
                break;
            case 4:
                weaponMaterial = Material.DIAMOND_SWORD;
                break;
            case 5:
                weaponMaterial = Material.GOLDEN_SWORD;
                break;
            case 6:
                weaponMaterial = Material.DIAMOND_SWORD; // Special T6 handling
                break;
            default:
                weaponMaterial = Material.WOODEN_SWORD;
        }

        ItemStack weapon = new ItemStack(weaponMaterial);

        if (elite) {
            weapon.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS, 1);
        }

        return weapon;
    }

    /**
     * FIXED: Simple armor creation based on tier
     */
    private ItemStack[] createArmorForTier(int tier, boolean elite) {
        Material armorMaterial;

        switch (tier) {
            case 1:
                armorMaterial = Material.LEATHER_HELMET; // Will be used as base for all pieces
                break;
            case 2:
                armorMaterial = Material.CHAINMAIL_HELMET;
                break;
            case 3:
                armorMaterial = Material.IRON_HELMET;
                break;
            case 4:
                armorMaterial = Material.DIAMOND_HELMET;
                break;
            case 5:
                armorMaterial = Material.GOLDEN_HELMET;
                break;
            case 6:
                armorMaterial = Material.LEATHER_HELMET; // Special blue leather for T6
                break;
            default:
                armorMaterial = Material.LEATHER_HELMET;
        }

        // Create armor pieces
        ItemStack[] armor = new ItemStack[4];
        String baseName = armorMaterial.name().replace("_HELMET", "");

        try {
            armor[0] = new ItemStack(Material.valueOf(baseName + "_HELMET"));   // Helmet
            armor[1] = new ItemStack(Material.valueOf(baseName + "_CHESTPLATE")); // Chestplate
            armor[2] = new ItemStack(Material.valueOf(baseName + "_LEGGINGS"));  // Leggings
            armor[3] = new ItemStack(Material.valueOf(baseName + "_BOOTS"));     // Boots

            // Special handling for T6 blue leather
            if (tier == 6) {
                for (ItemStack piece : armor) {
                    if (piece != null && piece.hasItemMeta()) {
                        piece.getItemMeta().setDisplayName(ChatColor.BLUE + "T6 Armor Piece");
                    }
                }
            }

            // Add enchantments for elite mobs
            if (elite) {
                for (ItemStack piece : armor) {
                    if (piece != null) {
                        piece.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_MOBS, 1);
                    }
                }
            }

        } catch (IllegalArgumentException e) {
            // Fallback to leather if material doesn't exist
            armor[0] = new ItemStack(Material.LEATHER_HELMET);
            armor[1] = new ItemStack(Material.LEATHER_CHESTPLATE);
            armor[2] = new ItemStack(Material.LEATHER_LEGGINGS);
            armor[3] = new ItemStack(Material.LEATHER_BOOTS);
        }

        return armor;
    }

    /**
     * Set entity health based on tier and elite status
     */
    private void setEntityHealth(LivingEntity entity, int tier, boolean elite) {
        try {
            double baseHealth = 20.0; // Default Minecraft mob health
            double healthMultiplier = calculateHealthMultiplier(tier, elite);
            double finalHealth = baseHealth * healthMultiplier;

            // Clamp health to reasonable values
            finalHealth = Math.max(20, Math.min(200000, finalHealth));

            entity.setMaxHealth(finalHealth);
            entity.setHealth(finalHealth);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error setting entity health: " + e.getMessage());
            entity.setMaxHealth(20);
            entity.setHealth(20);
        }
    }

    /**
     * Calculate health multiplier based on tier and elite status
     */
    private double calculateHealthMultiplier(int tier, boolean elite) {
        double multiplier = 1.0;

        // Base tier multiplier
        switch (tier) {
            case 1: multiplier = elite ? 3.0 : 1.0; break;
            case 2: multiplier = elite ? 6.0 : 2.0; break;
            case 3: multiplier = elite ? 12.0 : 4.0; break;
            case 4: multiplier = elite ? 25.0 : 8.0; break;
            case 5: multiplier = elite ? 50.0 : 15.0; break;
            case 6: multiplier = elite ? 100.0 : 30.0; break;
            default: multiplier = 1.0;
        }

        return multiplier;
    }

    /**
     * Apply movement effects based on tier and elite status
     */
    private void applyMovementEffects(LivingEntity entity, int tier, boolean elite) {
        try {
            // Fire resistance for all custom mobs
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

            // Elite movement bonus
            if (elite && tier >= 3) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            }

            // High tier movement bonus
            if (tier >= 4 && !elite) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error applying movement effects: " + e.getMessage());
        }
    }

    /**
     * Apply entity type-specific setup
     */
    private void applyEntityTypeSetup(LivingEntity entity, String type) {
        try {
            if (entity instanceof Zombie zombie) {
                zombie.setBaby(false);
            } else if (entity instanceof PigZombie pigZombie) {
                pigZombie.setAngry(true);
                pigZombie.setBaby(type.equals("imp") || type.equals("spectralguard"));
            } else if (entity instanceof MagmaCube magmaCube) {
                magmaCube.setSize(3);
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error applying entity type setup: " + e.getMessage());
        }
    }

    /**
     * Register spawned mob with tracking systems
     */
    private void registerSpawnedMob(LivingEntity entity, Location spawnerLocation) {
        try {
            UUID entityId = entity.getUniqueId();

            // Find and link to spawner
            Location nearestSpawner = findNearestSpawner(spawnerLocation, 5.0);
            if (nearestSpawner != null) {
                mobSpawnerLocations.put(entityId, nearestSpawner);
                String spawnerId = generateSpawnerId(nearestSpawner);
                entity.setMetadata("spawner", new FixedMetadataValue(plugin, spawnerId));
                entityToSpawner.put(entityId, spawnerId);
            }

            // Store original name for tracking
            String originalName = entity.getCustomName();
            if (originalName != null) {
                nameLock.writeLock().lock();
                try {
                    nameTrackingData.put(entityId, new NameTrackingData(originalName, 0, false));
                } finally {
                    nameLock.writeLock().unlock();
                }
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error registering spawned mob: " + e.getMessage());
        }
    }

    /**
     * Get a safe spawn location near the target location
     */
    private Location getSafeSpawnLocation(Location target) {
        // Try the target location first
        if (isSafeSpawnLocation(target)) {
            return target.clone().add(0.5, 0, 0.5);
        }

        // Try nearby locations
        for (int attempts = 0; attempts < 10; attempts++) {
            double x = target.getX() + (Math.random() * 6 - 3);
            double z = target.getZ() + (Math.random() * 6 - 3);
            Location candidate = new Location(target.getWorld(), x, target.getY() + 1, z);

            if (isSafeSpawnLocation(candidate)) {
                return candidate;
            }
        }

        // Fallback to target location with offset
        return target.clone().add(0.5, 2, 0.5);
    }

    /**
     * Check if a location is safe for spawning
     */
    private boolean isSafeSpawnLocation(Location location) {
        try {
            if (location.getBlock().getType().isSolid()) {
                return false;
            }

            if (location.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidSpawnRequest(Location location, String type, int tier) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (type == null || type.isEmpty()) {
            return false;
        }

        if (tier < 1 || tier > 6) {
            return false;
        }

        if (!MobType.isValidType(type)) {
            return false;
        }

        return true;
    }

    /**
     * FIXED: Simple check for spawner mob spawning capability
     */
    public boolean canSpawnerSpawnMob(String type, int tier, boolean elite) {
        try {
            // Basic validation only - spawners manage their own timing
            MobType mobType = MobType.getById(type);
            if (mobType == null) {
                return false;
            }

            // Check tier validity
            if (tier < mobType.getMinTier() || tier > mobType.getMaxTier()) {
                return false;
            }

            // Check T6 availability
            if (tier > 5 && !YakRealms.isT6Enabled()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("§c[MobManager] Error checking spawner mob validity: " + e.getMessage());
            return false;
        }
    }

    // ================ UTILITY METHODS ================

    private ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }

    private String formatMobName(String type) {
        if (type == null || type.isEmpty()) return "Unknown";

        String name = type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();

        switch (type.toLowerCase()) {
            case "witherskeleton":
                return "Wither Skeleton";
            case "cavespider":
                return "Cave Spider";
            case "magmacube":
                return "Magma Cube";
            default:
                return name.replace("_", " ");
        }
    }

    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Unknown";
        }
        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    // ================ ENHANCED ENTITY VALIDATION ================

    private void validateEntityTracking() {
        try {
            mobLock.readLock().lock();
            Set<UUID> invalidEntities = new HashSet<>();

            for (Map.Entry<UUID, CustomMob> entry : activeMobs.entrySet()) {
                UUID entityId = entry.getKey();
                CustomMob mob = entry.getValue();

                if (!isEntityValidAndTracked(mob.getEntity())) {
                    invalidEntities.add(entityId);
                }
            }

            if (!invalidEntities.isEmpty()) {
                mobLock.readLock().unlock();
                mobLock.writeLock().lock();
                try {
                    for (UUID invalidId : invalidEntities) {
                        activeMobs.remove(invalidId);
                    }
                } finally {
                    mobLock.readLock().lock();
                    mobLock.writeLock().unlock();
                }
            }
        } finally {
            mobLock.readLock().unlock();
        }
    }

    private boolean isEntityValidAndTracked(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return false;
        }

        try {
            Entity foundEntity = Bukkit.getEntity(entity.getUniqueId());
            if (foundEntity == null || !foundEntity.equals(entity)) {
                return false;
            }

            World world = entity.getWorld();
            if (world == null || !entity.isInWorld()) {
                return false;
            }

            Location loc = entity.getLocation();
            if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================ NAME VISIBILITY SYSTEM ================

    public void updateNameVisibility() {
        try {
            long currentTime = System.currentTimeMillis();
            nameLock.writeLock().lock();

            Set<UUID> toRemove = new HashSet<>();

            for (Map.Entry<UUID, NameTrackingData> entry : nameTrackingData.entrySet()) {
                UUID entityId = entry.getKey();
                NameTrackingData trackingData = entry.getValue();

                Entity entity = Bukkit.getEntity(entityId);
                if (!(entity instanceof LivingEntity livingEntity) || !isEntityValidAndTracked(livingEntity)) {
                    toRemove.add(entityId);
                    continue;
                }

                if (livingEntity instanceof Player) {
                    continue;
                }

                long timeSinceLastDamage = currentTime - trackingData.getLastDamageTime();
                boolean shouldShowHealthBar = timeSinceLastDamage < HEALTH_BAR_TIMEOUT || trackingData.isInCriticalState();

                if (shouldShowHealthBar && trackingData.isNameVisible()) {
                    if (!trackingData.isHealthBarActive()) {
                        updateEntityHealthBar(livingEntity);
                        trackingData.setHealthBarActive(true);
                    }
                } else if (!shouldShowHealthBar && trackingData.isNameVisible()) {
                    restoreOriginalName(livingEntity, trackingData);
                    trackingData.setNameVisible(false);
                    trackingData.setHealthBarActive(false);
                    toRemove.add(entityId);
                }
            }

            for (UUID entityId : toRemove) {
                nameTrackingData.remove(entityId);
            }

            mobLock.readLock().lock();
            try {
                for (CustomMob mob : activeMobs.values()) {
                    if (mob.isValid()) {
                        mob.updateNameVisibility();
                    }
                }
            } finally {
                mobLock.readLock().unlock();
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Name visibility update error: " + e.getMessage());
        } finally {
            nameLock.writeLock().unlock();
        }
    }

    public void updateEntityHealthBar(LivingEntity entity) {
        if (!isEntityValidAndTracked(entity) || entity instanceof Player) {
            return;
        }

        try {
            storeOriginalNameIfNeeded(entity);

            int tier = getMobTier(entity);
            boolean inCritical = isInCriticalState(entity);

            String healthBar = MobUtils.generateHealthBar(entity, entity.getHealth(), entity.getMaxHealth(), tier, inCritical);

            entity.setCustomName(healthBar);
            entity.setCustomNameVisible(true);

            UUID entityId = entity.getUniqueId();
            nameLock.writeLock().lock();
            try {
                NameTrackingData existing = nameTrackingData.get(entityId);
                if (existing != null) {
                    existing.setHealthBarActive(true);
                    existing.setNameVisible(true);
                } else {
                    String originalName = getStoredOriginalName(entity);
                    nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), inCritical));
                }
            } finally {
                nameLock.writeLock().unlock();
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Health bar update failed: " + e.getMessage());
        }
    }

    private void storeOriginalNameIfNeeded(LivingEntity entity) {
        if (!isEntityValidAndTracked(entity)) return;

        UUID entityId = entity.getUniqueId();

        nameLock.readLock().lock();
        try {
            if (nameTrackingData.containsKey(entityId)) {
                return;
            }
        } finally {
            nameLock.readLock().unlock();
        }

        String currentName = entity.getCustomName();
        if (currentName != null && !isHealthBar(currentName)) {
            nameLock.writeLock().lock();
            try {
                if (!nameTrackingData.containsKey(entityId)) {
                    nameTrackingData.put(entityId, new NameTrackingData(currentName, System.currentTimeMillis(), false));
                }
            } finally {
                nameLock.writeLock().unlock();
            }
        }
    }

    private void restoreOriginalName(LivingEntity entity, NameTrackingData trackingData) {
        if (!isEntityValidAndTracked(entity) || isInCriticalState(entity)) {
            return;
        }

        try {
            String nameToRestore = trackingData.getOriginalName();

            if (nameToRestore == null || nameToRestore.isEmpty()) {
                nameToRestore = generateDefaultName(entity);
            }

            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                String restoredName = applyTierColorsToName(entity, nameToRestore);

                entity.setCustomName(restoredName);
                entity.setCustomNameVisible(true);
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Name restoration failed: " + e.getMessage());
        }
    }

    private String getStoredOriginalName(LivingEntity entity) {
        UUID entityId = entity.getUniqueId();

        nameLock.readLock().lock();
        try {
            NameTrackingData data = nameTrackingData.get(entityId);
            if (data != null && data.getOriginalName() != null) {
                return data.getOriginalName();
            }
        } finally {
            nameLock.readLock().unlock();
        }

        if (entity.hasMetadata("name")) {
            return entity.getMetadata("name").get(0).asString();
        }

        return generateDefaultName(entity);
    }

    private boolean isHealthBar(String name) {
        return name != null && (name.contains(ChatColor.GREEN + "|") || name.contains(ChatColor.GRAY + "|"));
    }

    private String applyTierColorsToName(LivingEntity entity, String baseName) {
        if (baseName.contains("§")) {
            return baseName;
        }

        String cleanName = ChatColor.stripColor(baseName);
        int tier = getMobTier(entity);
        boolean elite = isElite(entity);
        ChatColor tierColor = getTierColor(tier);

        return elite ?
                tierColor.toString() + ChatColor.BOLD + cleanName :
                tierColor + cleanName;
    }

    private String generateDefaultName(LivingEntity entity) {
        if (entity == null) return "";

        String typeId = getEntityTypeId(entity);
        int tier = getMobTier(entity);
        boolean isElite = isElite(entity);

        MobType mobType = MobType.getById(typeId);
        if (mobType != null) {
            String tierName = mobType.getTierSpecificName(tier);
            ChatColor color = getTierColor(tier);
            return isElite ? color.toString() + ChatColor.BOLD + tierName : color + tierName;
        }

        ChatColor color = getTierColor(tier);
        String typeName = formatMobName(entity.getType().name());
        return isElite ? color.toString() + ChatColor.BOLD + typeName : color + typeName;
    }

    private String getEntityTypeId(LivingEntity entity) {
        if (entity.hasMetadata("type")) {
            return entity.getMetadata("type").get(0).asString();
        }
        return "unknown";
    }

    // ================ CRITICAL STATE PROCESSING ================

    private void processCriticalState() {
        try {
            processCustomMobCriticals();
            processLegacyMobCriticals();
        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical state processing error: " + e.getMessage());
        }
    }

    private void processCustomMobCriticals() {
        mobLock.readLock().lock();
        try {
            activeMobs.values().parallelStream()
                    .filter(Objects::nonNull)
                    .filter(CustomMob::isInCriticalState)
                    .forEach(this::processCustomMobCritical);
        } finally {
            mobLock.readLock().unlock();
        }
    }

    private void processCustomMobCritical(CustomMob mob) {
        try {
            LivingEntity entity = mob.getEntity();
            if (!isEntityValidAndTracked(entity)) return;

            boolean wasReadyState = mob.getCriticalStateDuration() < 0;
            boolean criticalEnded = mob.processCriticalStateTick();

            if (criticalEnded) {
                if (mob instanceof EliteMob) {
                    ((EliteMob) mob).executeCriticalAttack();
                } else if (!wasReadyState) {
                    entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
                }
            }

            if (mob.getCriticalStateDuration() > 0) {
                org.bukkit.Particle particleType = mob.isElite() ? org.bukkit.Particle.SPELL_WITCH : org.bukkit.Particle.CRIT;
                entity.getWorld().spawnParticle(particleType,
                        entity.getLocation().clone().add(0.0, 1.0, 0.0),
                        5, 0.3, 0.3, 0.3, 0.05);
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error processing custom mob critical: " + e.getMessage());
        }
    }

    private void processLegacyMobCriticals() {
        Iterator<Map.Entry<LivingEntity, Integer>> iterator = critMobs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Integer> entry = iterator.next();
            LivingEntity entity = entry.getKey();
            int step = entry.getValue();

            if (!isEntityValidAndTracked(entity)) {
                iterator.remove();
                continue;
            }

            if (step < 0) {
                if (entity.getWorld().getTime() % 20 == 0) {
                    entity.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_ANGRY,
                            entity.getLocation().clone().add(0.0, 1.0, 0.0),
                            1, 0.1, 0.1, 0.1, 0.0);
                }
                continue;
            }

            if (MobUtils.isElite(entity) && !MobUtils.isGolemBoss(entity)) {
                processEliteCritical(entity, step);
            } else {
                processRegularMobCritical(entity, step);
            }
        }
    }

    private void processEliteCritical(LivingEntity entity, int step) {
        try {
            critMobs.put(entity, step - 1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                    entity.getLocation().clone().add(0, 1, 0),
                    5, 0.3, 0.3, 0.3, 0.3f);

            if (step - 1 <= 0) {
                executeEliteCriticalAttack(entity);
            }
        } catch (Exception e) {
            logger.warning("§c[MobManager] Elite critical processing error: " + e.getMessage());
        }
    }

    private void processRegularMobCritical(LivingEntity entity, int step) {
        if (step > 0) {
            critMobs.put(entity, step - 1);

            UUID entityId = entity.getUniqueId();
            long currentTime = System.currentTimeMillis();
            Long lastSoundTime = soundTimes.get(entityId);

            if (lastSoundTime == null || (currentTime - lastSoundTime) >= CRITICAL_SOUND_INTERVAL) {
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
                soundTimes.put(entityId, currentTime);
            }

            entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    10, 0.3, 0.3, 0.3, 0.1);
        }

        if (step == 1) {
            critMobs.put(entity, -1);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    15, 0.3, 0.3, 0.3, 0.1);

            UUID entityId = entity.getUniqueId();
            nameLock.writeLock().lock();
            try {
                NameTrackingData existing = nameTrackingData.get(entityId);
                if (existing != null) {
                    nameTrackingData.put(entityId, new NameTrackingData(existing.getOriginalName(), System.currentTimeMillis(), true));
                } else {
                    String originalName = getStoredOriginalName(entity);
                    nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), true));
                }
            } finally {
                nameLock.writeLock().unlock();
            }

            updateEntityHealthBar(entity);
        }
    }

    private void executeEliteCriticalAttack(LivingEntity entity) {
        try {
            critMobs.remove(entity);
            soundTimes.remove(entity.getUniqueId());

            List<Integer> damageRange = MobUtils.getDamageRange(entity.getEquipment().getItemInMainHand());
            int min = damageRange.get(0);
            int max = damageRange.get(1);

            int damage = (ThreadLocalRandom.current().nextInt(max - min + 1) + min) * 3;
            int playersHit = 0;

            for (Entity nearby : entity.getNearbyEntities(7.0, 7.0, 7.0)) {
                if (nearby instanceof Player player) {
                    playersHit++;

                    boolean hadCrit = critMobs.containsKey(entity);
                    critMobs.remove(entity);

                    player.damage(damage, entity);

                    if (hadCrit) {
                        critMobs.put(entity, 0);
                    }

                    Vector knockback = player.getLocation().clone().toVector()
                            .subtract(entity.getLocation().toVector());

                    if (knockback.length() > 0) {
                        knockback.normalize();
                        if (MobUtils.isFrozenBoss(entity)) {
                            player.setVelocity(knockback.multiply(-3));
                        } else {
                            player.setVelocity(knockback.multiply(3));
                        }
                    }
                }
            }

            Location loc = entity.getLocation();
            entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, loc.clone().add(0, 1, 0), 10, 0, 0, 0, 1.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc.clone().add(0.0, 0.5, 0.0), 40, 1.0, 0.2, 1.0, 0.1);

            resetElitePotionEffects(entity);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Elite critical attack error: " + e.getMessage());
        }
    }

    private void resetElitePotionEffects(LivingEntity entity) {
        try {
            if (entity.hasPotionEffect(PotionEffectType.SLOW)) {
                entity.removePotionEffect(PotionEffectType.SLOW);
            }
            if (entity.hasPotionEffect(PotionEffectType.JUMP)) {
                entity.removePotionEffect(PotionEffectType.JUMP);
            }
            if (entity.hasPotionEffect(PotionEffectType.GLOWING)) {
                entity.removePotionEffect(PotionEffectType.GLOWING);
            }

            ItemStack weapon = entity.getEquipment().getItemInMainHand();
            if (weapon != null && weapon.getType().name().contains("_HOE")) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 1), true);
            }

            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1), true);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Error resetting elite effects: " + e.getMessage());
        }
    }

    // ================ ACTIVE MOBS MANAGEMENT ================

    private void updateActiveMobs() {
        if (activeWorldBoss != null && activeWorldBoss.isValid()) {
            activeWorldBoss.update();
            activeWorldBoss.processPhaseTransitions();
        }

        mobLock.writeLock().lock();
        try {
            activeMobs.entrySet().removeIf(entry -> {
                CustomMob mob = entry.getValue();
                return mob == null || !mob.isValid();
            });
        } finally {
            mobLock.writeLock().unlock();
        }
    }

    // ================ POSITION MONITORING ================

    private void checkMobPositions() {
        try {
            int teleported = 0;

            mobLock.readLock().lock();
            List<CustomMob> mobsToCheck;
            try {
                mobsToCheck = new ArrayList<>(activeMobs.values());
            } finally {
                mobLock.readLock().unlock();
            }

            for (CustomMob mob : mobsToCheck) {
                if (mob.isValid() && handleMobPositionCheck(mob)) {
                    teleported++;
                }
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Position check error: " + e.getMessage());
        }
    }

    private boolean handleMobPositionCheck(CustomMob mob) {
        LivingEntity entity = mob.getEntity();
        if (!isEntityValidAndTracked(entity)) return false;

        Location spawnerLoc = getSpawnerLocation(entity);
        if (spawnerLoc == null) return false;

        if (!entity.getLocation().getWorld().equals(spawnerLoc.getWorld())) {
            teleportMobToSpawner(entity, spawnerLoc, "changed worlds");
            return true;
        }

        double distanceSquared = entity.getLocation().distanceSquared(spawnerLoc);
        if (distanceSquared > (MAX_WANDERING_DISTANCE * MAX_WANDERING_DISTANCE)) {
            teleportMobToSpawner(entity, spawnerLoc, "wandered too far");
            return true;
        }

        if (AlignmentMechanics.isSafeZone(entity.getLocation())) {
            teleportMobToSpawner(entity, spawnerLoc, "entered safezone");
            return true;
        }

        return false;
    }

    private Location getSpawnerLocation(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        if (mobSpawnerLocations.containsKey(entityId)) {
            return mobSpawnerLocations.get(entityId);
        }

        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            Location spawnerLoc = findSpawnerById(spawnerId);
            if (spawnerLoc != null) {
                mobSpawnerLocations.put(entityId, spawnerLoc);
                return spawnerLoc;
            }
        }

        Location nearestSpawner = findNearestSpawner(entity.getLocation(), mobRespawnDistanceCheck);
        if (nearestSpawner != null) {
            mobSpawnerLocations.put(entityId, nearestSpawner);
        }

        return nearestSpawner;
    }

    private Location findSpawnerById(String spawnerId) {
        return spawner.getAllSpawners().entrySet().stream()
                .filter(entry -> generateSpawnerId(entry.getKey()).equals(spawnerId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void teleportMobToSpawner(LivingEntity entity, Location spawnerLoc, String reason) {
        if (entity == null || spawnerLoc == null) return;

        try {
            Location safeLoc = spawnerLoc.clone().add(
                    (Math.random() * 4 - 2),
                    1.0,
                    (Math.random() * 4 - 2)
            );

            safeLoc.setX(safeLoc.getBlockX() + 0.5);
            safeLoc.setZ(safeLoc.getBlockZ() + 0.5);

            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, entity.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                boolean wasAware = mob.isAware();
                mob.setAware(false);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isEntityValidAndTracked(entity)) {
                        mob.setAware(wasAware);
                    }
                }, 10L);
            }

            entity.teleport(safeLoc);

            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, safeLoc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            entity.getWorld().playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        } catch (Exception e) {
            logger.warning("§c[MobManager] Teleport error: " + e.getMessage());
        }
    }

    // ================ CRITICAL HIT SYSTEM ================

    public void rollForCriticalHit(LivingEntity entity, double damage) {
        try {
            if (isInCriticalState(entity)) return;

            int tier = getMobTier(entity);
            storeOriginalNameIfNeeded(entity);

            int critChance = getCriticalChance(tier);

            if (MobUtils.isGolemBoss(entity) && MobUtils.getMetadataInt(entity, "stage", 0) == 3) {
                critChance = 0;
            }

            int roll = ThreadLocalRandom.current().nextInt(200) + 1;

            if (roll <= critChance) {
                applyCriticalHit(entity, tier);
            }

        } catch (Exception e) {
            logger.severe("§c[MobManager] Critical roll error: " + e.getMessage());
        }
    }

    private int getCriticalChance(int tier) {
        switch (tier) {
            case 1: return 5;   // 2.5%
            case 2: return 7;   // 3.5%
            case 3: return 10;  // 5%
            case 4: return 13;  // 6.5%
            default: return 20; // 10% for tier 5+
        }
    }

    private void applyCriticalHit(LivingEntity entity, int tier) {
        CustomMob mob = getCustomMob(entity);
        if (mob != null) {
            mob.setCriticalState(mob.isElite() ? 12 : 6);
            mob.getEntity().setMetadata("criticalState", new FixedMetadataValue(plugin, true));
            mob.updateHealthBar();
        } else {
            boolean isElite = isElite(entity);

            if (isElite && !MobUtils.isGolemBoss(entity)) {
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 4.0f);

                if (!MobUtils.isFrozenBoss(entity)) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 10), true);
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 127), true);
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0), true);
                }

                critMobs.put(entity, 12);
            } else {
                entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
                entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT, entity.getLocation().clone().add(0.0, 1.0, 0.0), 20, 0.3, 0.3, 0.3, 0.5);
                critMobs.put(entity, 6);
            }

            entity.setMetadata("criticalState", new FixedMetadataValue(plugin, true));
            updateEntityHealthBar(entity);
        }

        UUID entityId = entity.getUniqueId();
        nameLock.writeLock().lock();
        try {
            NameTrackingData existing = nameTrackingData.get(entityId);
            if (existing != null) {
                nameTrackingData.put(entityId, new NameTrackingData(existing.getOriginalName(), System.currentTimeMillis(), true));
            } else {
                String originalName = getStoredOriginalName(entity);
                nameTrackingData.put(entityId, new NameTrackingData(originalName, System.currentTimeMillis(), true));
            }
        } finally {
            nameLock.writeLock().unlock();
        }

        entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME, entity.getLocation().clone().add(0.0, 1.5, 0.0), 30, 0.3, 0.3, 0.3, 0.05);
    }

    // ================ MOB REGISTRATION ================

    public void registerMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();

        mobLock.writeLock().lock();
        try {
            activeMobs.put(entity.getUniqueId(), mob);
        } finally {
            mobLock.writeLock().unlock();
        }

        if (mob instanceof WorldBoss) {
            activeWorldBoss = (WorldBoss) mob;
        }

        if (entity.getCustomName() != null) {
            UUID entityId = entity.getUniqueId();
            nameLock.writeLock().lock();
            try {
                if (!nameTrackingData.containsKey(entityId)) {
                    nameTrackingData.put(entityId, new NameTrackingData(entity.getCustomName(), 0, false));
                }
            } finally {
                nameLock.writeLock().unlock();
            }
        }
    }

    public void unregisterMob(CustomMob mob) {
        if (mob == null || mob.getEntity() == null) return;

        LivingEntity entity = mob.getEntity();
        UUID entityId = entity.getUniqueId();

        mobLock.writeLock().lock();
        try {
            activeMobs.remove(entityId);
        } finally {
            mobLock.writeLock().unlock();
        }

        nameLock.writeLock().lock();
        try {
            nameTrackingData.remove(entityId);
        } finally {
            nameLock.writeLock().unlock();
        }

        mobSpawnerLocations.remove(entityId);
        soundTimes.remove(entityId);

        if (mob instanceof WorldBoss) {
            activeWorldBoss = null;
        }
    }

    // ================ DAMAGE PROCESSING ================

    public void trackDamage(LivingEntity entity, Player player, double damage) {
        if (entity == null || player == null) return;

        UUID entityUuid = entity.getUniqueId();
        UUID playerUuid = player.getUniqueId();

        damageContributions.computeIfAbsent(entityUuid, k -> new ConcurrentHashMap<>())
                .merge(playerUuid, damage, Double::sum);
    }

    public Player getTopDamageDealer(LivingEntity entity) {
        if (entity == null) return null;

        Map<UUID, Double> damageMap = damageContributions.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) return null;

        return damageMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .map(Bukkit::getPlayer)
                .orElse(null);
    }

    // ================ CLEANUP OPERATIONS ================

    private void performCleanup() {
        try {
            cleanupDamageTracking();
            cleanupEntityMappings();
            cleanupMobLocations();
            cleanupNameTracking();
            processedEntities.clear();

        } catch (Exception e) {
            logger.severe("§c[MobManager] Cleanup error: " + e.getMessage());
        }
    }

    private void cleanupDamageTracking() {
        Set<UUID> invalidEntities = damageContributions.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidEntities.forEach(damageContributions::remove);
    }

    private void cleanupEntityMappings() {
        Set<UUID> invalidIds = entityToSpawner.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(entityToSpawner::remove);
    }

    private void cleanupMobLocations() {
        Set<UUID> invalidIds = mobSpawnerLocations.keySet().stream()
                .filter(id -> !isEntityValid(id))
                .collect(HashSet::new, Set::add, Set::addAll);

        invalidIds.forEach(mobSpawnerLocations::remove);
    }

    private void cleanupNameTracking() {
        nameLock.writeLock().lock();
        try {
            Set<UUID> invalidIds = nameTrackingData.keySet().stream()
                    .filter(id -> !isEntityValid(id))
                    .collect(HashSet::new, Set::add, Set::addAll);

            invalidIds.forEach(nameTrackingData::remove);
        } finally {
            nameLock.writeLock().unlock();
        }
    }

    private boolean isEntityValid(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid() && !entity.isDead();
    }

    // ================ RESPAWN MANAGEMENT ================

    public long calculateRespawnDelay(int tier, boolean elite) {
        double tierFactor = 1.0 + ((tier - 1) * 0.2);
        double eliteMultiplier = elite ? 1.5 : 1.0;

        long calculatedDelay = (long) (MIN_RESPAWN_DELAY * tierFactor * eliteMultiplier);
        double randomFactor = 0.9 + (Math.random() * 0.2);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        return Math.min(Math.max(calculatedDelay, MIN_RESPAWN_DELAY), MAX_RESPAWN_DELAY);
    }

    public long recordMobDeath(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        long currentTime = System.currentTimeMillis();
        long respawnDelay = calculateRespawnDelay(tier, elite);
        long respawnTime = currentTime + respawnDelay;

        respawnTimes.put(key, respawnTime);
        mobTypeLastDeath.put(key, currentTime);

        return respawnTime;
    }

    public boolean canRespawn(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return true;

        String key = getResponKey(mobType, tier, elite);
        Long respawnTime = respawnTimes.get(key);

        if (respawnTime == null) return true;

        return System.currentTimeMillis() >= respawnTime;
    }

    public String getResponKey(String mobType, int tier, boolean elite) {
        return String.format("%s:%d:%s", mobType, tier, elite ? "elite" : "normal");
    }

    public long getLastDeathTime(String mobType, int tier, boolean elite) {
        if (mobType == null || mobType.isEmpty()) return 0;

        String key = getResponKey(mobType, tier, elite);
        return mobTypeLastDeath.getOrDefault(key, 0L);
    }

    // ================ UTILITY METHODS ================

    public int getMobTier(LivingEntity entity) {
        CustomMob mob = getCustomMob(entity);
        return mob != null ? mob.getTier() : MobUtils.getMobTier(entity);
    }

    public boolean isElite(LivingEntity entity) {
        CustomMob mob = getCustomMob(entity);
        return mob != null ? mob.isElite() : MobUtils.isElite(entity);
    }

    public CustomMob getCustomMob(LivingEntity entity) {
        if (entity == null) return null;

        mobLock.readLock().lock();
        try {
            return activeMobs.get(entity.getUniqueId());
        } finally {
            mobLock.readLock().unlock();
        }
    }

    private boolean isInCriticalState(LivingEntity entity) {
        return critMobs.containsKey(entity) || entity.hasMetadata("criticalState");
    }

    public Location findNearestSpawner(Location location, double maxDistance) {
        return spawner.findNearestSpawner(location, maxDistance);
    }

    // ================ SPAWNER DELEGATION ================

    public boolean setSpawnerVisibility(Location location, boolean visible) {
        return location != null && spawner.setSpawnerVisibility(location, visible);
    }

    public int getActiveMobCount(Location location) {
        return spawner.getActiveMobCount(location);
    }

    public boolean isSpawnerVisible(Location location) {
        return location != null && spawner.isSpawnerVisible(location);
    }

    public void updateSpawnerHologram(Location location) {
        if (location != null) spawner.createOrUpdateSpawnerHologram(location);
    }

    public void removeSpawnerHologram(Location location) {
        if (location != null) spawner.removeSpawnerHologram(location);
    }

    public MobSpawner getSpawner() {
        return spawner;
    }

    public SpawnerMetrics getSpawnerMetrics(Location location) {
        return location != null ? spawner.getSpawnerMetrics(location) : null;
    }

    public boolean resetSpawner(Location location) {
        return location != null && spawner.resetSpawner(location);
    }

    public boolean addSpawner(Location location, String data) {
        return spawner.addSpawner(location, data);
    }

    public boolean removeSpawner(Location location) {
        return spawner.removeSpawner(location);
    }

    public Map<Location, String> getAllSpawners() {
        return spawner.getAllSpawners();
    }

    public void sendSpawnerInfo(Player player, Location location) {
        if (player != null && location != null) {
            spawner.sendSpawnerInfo(player, location);
        }
    }

    public void resetAllSpawners() {
        spawner.resetAllSpawners();
        logger.info("§a[MobManager] §7All spawners reset");
    }

    // ================ EVENT HANDLERS ================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        try {
            if (!(event.getEntity() instanceof LivingEntity entity) || event.getEntity() instanceof Player) {
                return;
            }

            Player damager = extractPlayerDamager(event);
            if (damager == null) return;

            double damage = event.getFinalDamage();

            storeOriginalNameIfNeeded(entity);
            rollForCriticalHit(entity, damage);
            trackDamage(entity, damager, damage);

            updateEntityHealthBar(entity);

        } catch (Exception e) {
            logger.severe("§c[MobManager] Entity damage event error: " + e.getMessage());
        }
    }

    private Player extractPlayerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobHitMob(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity damager &&
                !(event.getDamager() instanceof Player) &&
                !(event.getEntity() instanceof Player)) {

            if (damager.isCustomNameVisible() &&
                    !damager.getCustomName().equalsIgnoreCase("Celestial Ally")) {
                event.setCancelled(true);
                event.setDamage(0.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            UUID entityId = entity.getUniqueId();

            if (entity instanceof Player || processedEntities.contains(entityId)) {
                return;
            }

            processedEntities.add(entityId);

            if (entity.hasMetadata("type")) {
                handleMobDeath(entity);
            }

            mobSpawnerLocations.remove(entityId);
            soundTimes.remove(entityId);

            nameLock.writeLock().lock();
            try {
                nameTrackingData.remove(entityId);
            } finally {
                nameLock.writeLock().unlock();
            }

        } catch (Exception e) {
            logger.warning("§c[MobManager] Entity death error: " + e.getMessage());
        }
    }

    private void handleMobDeath(LivingEntity entity) {
        String mobType = entity.getMetadata("type").get(0).asString();
        int tier = getMobTier(entity);
        boolean elite = isElite(entity);

        recordMobDeath(mobType, tier, elite);

        Location spawnerLoc = findSpawnerForMob(entity);
        if (spawnerLoc != null) {
            spawner.registerMobDeath(spawnerLoc, entity.getUniqueId());
        }
    }

    private Location findSpawnerForMob(LivingEntity entity) {
        if (entity == null) return null;

        UUID entityId = entity.getUniqueId();

        if (mobSpawnerLocations.containsKey(entityId)) {
            return mobSpawnerLocations.get(entityId);
        }

        if (entity.hasMetadata("spawner")) {
            String spawnerId = entity.getMetadata("spawner").get(0).asString();
            Location spawnerLoc = findSpawnerById(spawnerId);
            if (spawnerLoc != null) return spawnerLoc;
        }

        return spawner.findNearestSpawner(entity.getLocation(), mobRespawnDistanceCheck);
    }

    // ================ CONFIGURATION GETTERS ================

    public boolean isDebugMode() { return debug; }
    public void setDebugMode(boolean debug) {
        this.debug = debug;
        spawner.setDebugMode(debug);
    }
    public int getMaxMobsPerSpawner() { return maxMobsPerSpawner; }
    public double getPlayerDetectionRange() { return playerDetectionRange; }
    public double getMobRespawnDistanceCheck() { return mobRespawnDistanceCheck; }
    public void setSpawnersEnabled(boolean enabled) {
        spawnersEnabled = enabled;
        spawner.setSpawnersEnabled(enabled);
    }
    public boolean areSpawnersEnabled() { return spawnersEnabled; }

    // ================ SHUTDOWN ================

    public void shutdown() {
        try {
            spawner.saveSpawners();

            if (mainTask != null) mainTask.cancel();
            if (cleanupTask != null) cleanupTask.cancel();

            spawner.shutdown();

            mobLock.readLock().lock();
            try {
                activeMobs.values().forEach(CustomMob::remove);
            } finally {
                mobLock.readLock().unlock();
            }

            clearAllCollections();
            activeWorldBoss = null;

            logger.info("§a[MobManager] §7Shutdown completed successfully");
        } catch (Exception e) {
            logger.severe("§c[MobManager] Shutdown error: " + e.getMessage());
        }
    }

    private void clearAllCollections() {
        mobLock.writeLock().lock();
        try {
            activeMobs.clear();
        } finally {
            mobLock.writeLock().unlock();
        }

        critMobs.clear();
        soundTimes.clear();

        nameLock.writeLock().lock();
        try {
            nameTrackingData.clear();
        } finally {
            nameLock.writeLock().unlock();
        }

        damageContributions.clear();
        mobTargets.clear();
        processedEntities.clear();
        lastSafespotCheck.clear();
        respawnTimes.clear();
        mobTypeLastDeath.clear();
        mobSpawnerLocations.clear();
        entityToSpawner.clear();
    }
}
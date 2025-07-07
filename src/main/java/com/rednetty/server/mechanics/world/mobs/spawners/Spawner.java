package com.rednetty.server.mechanics.world.mobs.spawners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.SpawnerProperties;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FIXED: Complete Spawner class with enhanced reliability, error handling, and MAIN THREAD SAFETY
 * - Fixed entity creation validation and spawn failure handling
 * - Enhanced mob type validation with comprehensive error reporting
 * - Improved spawn success rates with better location finding
 * - Added detailed logging for debugging spawn failures
 * - Fixed respawn queue management and mob counting issues
 * - ALL entity operations happen on MAIN THREAD ONLY
 */
public class Spawner {
    private final Location location;
    private final List<MobEntry> mobEntries = new ArrayList<>();
    private final Map<UUID, SpawnedMob> activeMobs = new ConcurrentHashMap<>();
    private final Map<UUID, RespawnEntry> respawnQueue = new ConcurrentHashMap<>();

    private boolean visible;
    private SpawnerProperties properties;
    private int displayMode = 2;
    private SpawnerMetrics metrics;

    private final Logger logger;
    private final MobManager mobManager;
    private final String uniqueId;

    // Enhanced timing controls
    private long lastProcessTime = 0;
    private long lastHologramUpdate = 0;
    private boolean needsHologramUpdate = true;
    private long lastPlayerCheck = 0;
    private long lastValidationTime = 0;

    // Enhanced timing constants
    private static final long PROCESS_INTERVAL = 1000; // 1 second between processes
    private static final long HOLOGRAM_UPDATE_INTERVAL = 5000; // 5 seconds between hologram updates
    private static final long MIN_RESPAWN_TIME = 30000; // 30 seconds minimum respawn time
    private static final long PLAYER_CHECK_INTERVAL = 2000; // 2 seconds between player checks
    private static final long VALIDATION_INTERVAL = 10000; // 10 seconds between validations

    // Enhanced performance tracking
    private int totalSpawnAttempts = 0;
    private int successfulSpawns = 0;
    private int failedSpawns = 0;
    private long lastPerformanceReset = System.currentTimeMillis();

    // Enhanced failure tracking
    private final Map<String, Integer> mobTypeFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFailureTime = new ConcurrentHashMap<>();
    private final Set<String> temporarilyDisabledTypes = new HashSet<>();

    /**
     * Tracks what type of mob should respawn and when.
     * This is a lightweight object specifically for the respawn queue.
     */
    static class RespawnEntry {
        private final String mobType;
        private final int tier;
        private final boolean elite;
        private final long respawnTime;
        private final long creationTime;

        public RespawnEntry(String mobType, int tier, boolean elite, long respawnTime) {
            this.mobType = mobType;
            this.tier = tier;
            this.elite = elite;
            this.respawnTime = respawnTime;
            this.creationTime = System.currentTimeMillis();
        }

        public String getMobType() { return mobType; }
        public int getTier() { return tier; }
        public boolean isElite() { return elite; }
        public long getRespawnTime() { return respawnTime; }
        public long getCreationTime() { return creationTime; }

        public boolean matches(MobEntry entry) {
            return mobType.equals(entry.getMobType()) &&
                    tier == entry.getTier() &&
                    elite == entry.isElite();
        }

        public boolean matchesEntry(RespawnEntry spawnedMob) {
            if (spawnedMob == null) return false;
            return mobType.equals(spawnedMob.getMobType()) &&
                    tier == spawnedMob.getTier() &&
                    elite == spawnedMob.isElite();
        }

        public boolean isExpired() {
            // Consider respawn entry expired if it's been in queue for more than 30 minutes
            return System.currentTimeMillis() - creationTime > 1800000L;
        }
    }

    /**
     * Constructor for a new spawner
     */
    public Spawner(Location location, String data, boolean visible) {
        this.location = location.clone();
        this.visible = visible;
        this.properties = new SpawnerProperties();
        this.metrics = new SpawnerMetrics();
        this.mobManager = MobManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
        this.uniqueId = generateSpawnerId(location);

        if (data != null && !data.isEmpty()) {
            parseSpawnerData(data);
        }

        this.lastProcessTime = System.currentTimeMillis();
        this.needsHologramUpdate = true;
    }

    /**
     * Generate a unique ID for this spawner
     */
    private String generateSpawnerId(Location location) {
        return location.getWorld().getName() + "_" +
                location.getBlockX() + "_" +
                location.getBlockY() + "_" +
                location.getBlockZ();
    }

    // ================ ENHANCED DATA PARSING ================

    /**
     * Parse mob data from string format with enhanced validation
     */
    public void parseSpawnerData(String data) {
        mobEntries.clear();

        if (data == null || data.trim().isEmpty()) {
            logger.warning("[Spawner] Empty spawner data for " + formatLocation());
            return;
        }

        try {
            String[] entries = data.split(",");
            int validEntries = 0;
            int invalidEntries = 0;

            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                try {
                    MobEntry mobEntry = MobEntry.fromString(entry);

                    // Enhanced validation
                    if (validateMobEntry(mobEntry)) {
                        mobEntries.add(mobEntry);
                        validEntries++;
                    } else {
                        invalidEntries++;
                        logger.warning("[Spawner] Invalid mob entry rejected: " + entry + " at " + formatLocation());
                    }
                } catch (Exception e) {
                    invalidEntries++;
                    logger.warning("[Spawner] Failed to parse mob entry '" + entry + "' at " + formatLocation() + ": " + e.getMessage());
                }
            }

            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info(String.format("[Spawner] Parsed %d valid entries (%d invalid) for %s",
                        validEntries, invalidEntries, formatLocation()));
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Error parsing spawner data at " + formatLocation() + ": " + e.getMessage());
            mobEntries.clear();
        }
    }

    /**
     * Enhanced mob entry validation
     */
    private boolean validateMobEntry(MobEntry entry) {
        if (entry == null) return false;

        try {
            // Check if mob type is temporarily disabled due to failures
            if (temporarilyDisabledTypes.contains(entry.getMobType())) {
                return false;
            }

            // Check if this mob type can be spawned by the manager
            if (!mobManager.canSpawnerSpawnMob(entry.getMobType(), entry.getTier(), entry.isElite())) {
                return false;
            }

            // Check failure rate for this mob type
            String mobKey = getMobTypeKey(entry);
            int failures = mobTypeFailures.getOrDefault(mobKey, 0);
            if (failures > 10) {
                // Too many recent failures
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("[Spawner] Error validating mob entry: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get spawner data as a string
     */
    public String getDataString() {
        return mobEntries.stream()
                .map(MobEntry::toString)
                .collect(Collectors.joining(","));
    }

    // ================ ENHANCED MAIN PROCESSING ================

    /**
     * FIXED: Main tick processing method with MAIN THREAD SAFETY
     * Called every second from MobSpawner - always on main thread
     */
    public void tick() {
        // CRITICAL: Verify we're on the main thread for entity operations
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: tick() called from async thread! Location: " + formatLocation());
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Don't process too frequently
        if (currentTime - lastProcessTime < PROCESS_INTERVAL) {
            return;
        }
        lastProcessTime = currentTime;

        try {
            // Step 1: Validate and clean up invalid mobs - MAIN THREAD
            if (currentTime - lastValidationTime > VALIDATION_INTERVAL) {
                validateAndCleanupMobs();
                cleanupFailureTracking(currentTime);
                lastValidationTime = currentTime;
            }

            // Only proceed if spawn conditions are met
            if (canSpawnHere(currentTime)) {
                // Step 2: Process the respawn queue first - MAIN THREAD
                processRespawnQueue(currentTime);

                // Step 3: Spawn any additional missing mobs if not at capacity - MAIN THREAD
                spawnMissingMobs();
            }

            // Step 4: Update hologram if needed
            updateHologramIfNeeded(currentTime);

            // Step 5: Update metrics
            updatePerformanceMetrics(currentTime);

        } catch (Exception e) {
            logger.warning("[Spawner Tick Error] " + formatLocation() + ": " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
        }
    }

    /**
     * FIXED: Enhanced mob validation and cleanup with MAIN THREAD SAFETY
     */
    private void validateAndCleanupMobs() {
        // CRITICAL: Verify we're on the main thread for entity operations
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: validateAndCleanupMobs called from async thread!");
            return;
        }

        if (activeMobs.isEmpty()) return;

        boolean changed = false;
        Iterator<Map.Entry<UUID, SpawnedMob>> iterator = activeMobs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnedMob> entry = iterator.next();
            UUID uuid = entry.getKey();

            // Entity lookup - MAIN THREAD
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid() || entity.isDead()) {
                iterator.remove();
                changed = true;

                if (isDebugMode()) {
                    logger.info("[Spawner] Removed invalid mob: " + uuid.toString().substring(0, 8));
                }
            }
        }

        // Clean up expired respawn entries
        respawnQueue.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Removed expired respawn entry: " + entry.getValue().getMobType());
                }
                return true;
            }
            return false;
        });

        if (changed) {
            needsHologramUpdate = true;
        }
    }

    /**
     * Clean up failure tracking data
     */
    private void cleanupFailureTracking(long currentTime) {
        try {
            // Re-enable temporarily disabled types after 5 minutes
            Iterator<String> disabledIterator = temporarilyDisabledTypes.iterator();
            while (disabledIterator.hasNext()) {
                String mobType = disabledIterator.next();
                Long lastFailure = lastFailureTime.get(mobType);
                if (lastFailure != null && (currentTime - lastFailure) > 300000L) { // 5 minutes
                    disabledIterator.remove();
                    mobTypeFailures.put(mobType, 0); // Reset failure count
                    if (isDebugMode()) {
                        logger.info("[Spawner] Re-enabled mob type: " + mobType);
                    }
                }
            }

            // Reset failure counts that are old
            mobTypeFailures.entrySet().removeIf(entry -> {
                String mobType = entry.getKey();
                Long lastFailure = lastFailureTime.get(mobType);
                return lastFailure != null && (currentTime - lastFailure) > 600000L; // 10 minutes
            });

        } catch (Exception e) {
            logger.warning("[Spawner] Error cleaning up failure tracking: " + e.getMessage());
        }
    }

    /**
     * FIXED: Process mobs waiting in the respawn queue with MAIN THREAD SAFETY
     */
    private void processRespawnQueue(long currentTime) {
        // CRITICAL: Verify we're on the main thread for entity spawning
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: processRespawnQueue called from async thread!");
            return;
        }

        if (respawnQueue.isEmpty()) return;

        List<UUID> readyToRespawn = new ArrayList<>();
        respawnQueue.forEach((queueId, respawnEntry) -> {
            if (currentTime >= respawnEntry.getRespawnTime()) {
                readyToRespawn.add(queueId);
            }
        });

        if (readyToRespawn.isEmpty()) return;

        int processedCount = 0;
        for (UUID queueId : readyToRespawn) {
            RespawnEntry respawnEntry = respawnQueue.remove(queueId);
            if (respawnEntry == null) continue;

            // Check if we still need a mob of this type and are not at global capacity
            if (!isAtCapacity() && spawnSingleMobEnhanced(respawnEntry.getMobType(), respawnEntry.getTier(), respawnEntry.isElite())) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Respawned: " + respawnEntry.getMobType() + " T" + respawnEntry.getTier() +
                            (respawnEntry.isElite() ? "+" : ""));
                }
                successfulSpawns++;
                processedCount++;
            } else {
                // Spawning failed, check if we should retry or give up
                String mobKey = getMobTypeKey(respawnEntry.getMobType(), respawnEntry.getTier(), respawnEntry.isElite());
                int failures = mobTypeFailures.getOrDefault(mobKey, 0);

                if (failures < 5) {
                    // Put it back in the queue for a short delay
                    respawnQueue.put(UUID.randomUUID(), new RespawnEntry(
                            respawnEntry.getMobType(),
                            respawnEntry.getTier(),
                            respawnEntry.isElite(),
                            currentTime + 10000 // 10 second delay
                    ));
                }
                failedSpawns++;
            }

            // Limit processing per tick to avoid lag
            if (processedCount >= 3) break;
        }

        if (processedCount > 0) {
            needsHologramUpdate = true;
        }
    }

    /**
     * FIXED: Spawn mobs to meet the configured amounts with MAIN THREAD SAFETY
     */
    private void spawnMissingMobs() {
        // CRITICAL: Verify we're on the main thread for entity spawning
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: spawnMissingMobs called from async thread!");
            return;
        }

        if (isAtCapacity()) return;

        for (MobEntry entry : mobEntries) {
            int needed = entry.getAmount() - getTotalCountForEntry(entry);

            for (int i = 0; i < needed; i++) {
                if (isAtCapacity()) return; // Stop if we hit capacity mid-spawn

                totalSpawnAttempts++;
                if (spawnSingleMobEnhanced(entry.getMobType(), entry.getTier(), entry.isElite())) {
                    metrics.recordSpawn(1);
                    successfulSpawns++;
                    if (isDebugMode()) {
                        logger.info("[Spawner] Spawned missing mob: " + entry.getMobType() + " T" + entry.getTier() +
                                (entry.isElite() ? "+" : ""));
                    }
                } else {
                    failedSpawns++;
                    // Stop trying for this entry if a spawn fails
                    break;
                }
            }
        }
    }

    /**
     * FIXED: Enhanced mob spawning with MAIN THREAD SAFETY
     */
    private boolean spawnSingleMobEnhanced(String mobType, int tier, boolean elite) {
        // CRITICAL: Verify we're on the main thread for entity operations
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: spawnSingleMobEnhanced called from async thread!");
            return false;
        }

        String mobKey = getMobTypeKey(mobType, tier, elite);

        try {
            // Check if this mob type is temporarily disabled
            if (temporarilyDisabledTypes.contains(mobType)) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Skipping temporarily disabled mob type: " + mobType);
                }
                return false;
            }

            // Get a safe spawn location with enhanced validation
            Location spawnLoc = getRandomSpawnLocationEnhanced();
            if (spawnLoc == null) {
                recordSpawnFailure(mobKey, "No safe spawn location found");
                return false;
            }

            // Validate mob type before attempting spawn
            if (!validateMobTypeForSpawning(mobType, tier, elite)) {
                recordSpawnFailure(mobKey, "Mob type validation failed");
                return false;
            }

            // Attempt to spawn the mob using MobManager - MAIN THREAD
            LivingEntity entity = mobManager.spawnMobFromSpawner(spawnLoc, mobType, tier, elite);

            if (entity != null && entity.isValid()) {
                // Success! Track this new mob
                activeMobs.put(entity.getUniqueId(), new SpawnedMob(entity.getUniqueId(), mobType, tier, elite));

                // Add metadata for tracking - MAIN THREAD
                entity.setMetadata("spawner", new FixedMetadataValue(YakRealms.getInstance(), uniqueId));
                if (hasSpawnerGroup()) {
                    entity.setMetadata("spawnerGroup", new FixedMetadataValue(YakRealms.getInstance(), properties.getSpawnerGroup()));
                }

                needsHologramUpdate = true;

                // Reset failure count on success
                mobTypeFailures.put(mobKey, 0);

                if (isDebugMode()) {
                    logger.info(String.format("[Spawner] Successfully spawned %s T%d%s at %s (ID: %s)",
                            mobType, tier, elite ? "+" : "", formatLocation(),
                            entity.getUniqueId().toString().substring(0, 8)));
                }

                return true;
            } else {
                recordSpawnFailure(mobKey, "MobManager returned null or invalid entity");
                return false;
            }
        } catch (Exception e) {
            recordSpawnFailure(mobKey, "Exception during spawn: " + e.getMessage());
            logger.warning("[Spawner] Failed to spawn mob '" + mobType + "' T" + tier + (elite ? "+" : "") +
                    " at " + formatLocation() + ": " + e.getMessage());
            if (isDebugMode()) {
                e.printStackTrace();
            }
            metrics.recordFailedSpawn();
            return false;
        }
    }

    /**
     * Record spawn failure and update failure tracking
     */
    private void recordSpawnFailure(String mobKey, String reason) {
        int failures = mobTypeFailures.getOrDefault(mobKey, 0) + 1;
        mobTypeFailures.put(mobKey, failures);
        lastFailureTime.put(mobKey, System.currentTimeMillis());

        if (failures >= 10) {
            // Temporarily disable this mob type
            String mobType = mobKey.split(":")[0];
            temporarilyDisabledTypes.add(mobType);
            logger.warning(String.format("[Spawner] Temporarily disabled mob type '%s' after %d failures. Reason: %s",
                    mobType, failures, reason));
        } else if (isDebugMode()) {
            logger.info(String.format("[Spawner] Spawn failure #%d for %s: %s", failures, mobKey, reason));
        }
    }

    /**
     * Get mob type key for failure tracking
     */
    private String getMobTypeKey(String mobType, int tier, boolean elite) {
        return mobType + ":" + tier + ":" + (elite ? "elite" : "normal");
    }

    private String getMobTypeKey(MobEntry entry) {
        return getMobTypeKey(entry.getMobType(), entry.getTier(), entry.isElite());
    }

    /**
     * Enhanced mob type validation
     */
    private boolean validateMobTypeForSpawning(String mobType, int tier, boolean elite) {
        try {
            // Check with MobManager
            if (!mobManager.canSpawnerSpawnMob(mobType, tier, elite)) {
                return false;
            }

            // Additional validation checks
            if (mobType == null || mobType.trim().isEmpty()) {
                return false;
            }

            if (tier < 1 || tier > 6) {
                return false;
            }

            // Check T6 availability
            if (tier > 5 && !YakRealms.isT6Enabled()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warning("[Spawner] Error validating mob type: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the total count for a specific mob type, including active AND queued mobs.
     * THIS IS THE KEY FIX.
     */
    private int getTotalCountForEntry(MobEntry entry) {
        long activeCount = activeMobs.values().stream().filter(entry::matches).count();
        long queuedCount = respawnQueue.values().stream().filter(entry::matchesEntry).count();
        return (int) (activeCount + queuedCount);
    }

    /**
     * Get the total number of mobs this spawner is responsible for (active + queued).
     */
    private int getTotalMobCount() {
        return activeMobs.size() + respawnQueue.size();
    }

    /**
     * FIXED: Enhanced random spawn location generation with better validation
     */
    private Location getRandomSpawnLocationEnhanced() {
        double radiusX = properties.getSpawnRadiusX();
        double radiusY = properties.getSpawnRadiusY();
        double radiusZ = properties.getSpawnRadiusZ();

        // Try multiple locations to find a safe one with expanding search
        for (int attempts = 0; attempts < 15; attempts++) {
            // Expand search radius with attempts
            double expansionFactor = 1.0 + (attempts * 0.2);

            double offsetX = (Math.random() * 2 - 1) * radiusX * expansionFactor;
            double offsetY = (Math.random() * 2 - 1) * radiusY;
            double offsetZ = (Math.random() * 2 - 1) * radiusZ * expansionFactor;

            Location spawnLoc = location.clone().add(offsetX, offsetY, offsetZ);

            // Enhanced safety check
            if (isSafeSpawnLocationEnhanced(spawnLoc)) {
                return spawnLoc;
            }
        }

        // Try some fallback locations
        Location[] fallbacks = {
                location.clone().add(0, 2, 0),
                location.clone().add(1, 2, 1),
                location.clone().add(-1, 2, -1),
                location.clone().add(0, 3, 0)
        };

        for (Location fallback : fallbacks) {
            if (isSafeSpawnLocationEnhanced(fallback)) {
                if (isDebugMode()) {
                    logger.info("[Spawner] Using fallback location: " + formatLocation());
                }
                return fallback;
            }
        }

        // Final fallback
        return location.clone().add(0.5, 3, 0.5);
    }

    /**
     * Enhanced safe spawn location check with detailed validation
     */
    private boolean isSafeSpawnLocationEnhanced(Location loc) {
        try {
            if (loc == null || loc.getWorld() == null) {
                return false;
            }

            // Check Y bounds
            if (loc.getY() < 0 || loc.getY() > loc.getWorld().getMaxHeight() - 3) {
                return false;
            }

            // Check if chunk is loaded
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                return false;
            }

            // Check current block
            if (loc.getBlock().getType().isSolid()) {
                return false;
            }

            // Check block above
            if (loc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
                return false;
            }

            // Check for dangerous blocks
            String blockName = loc.getBlock().getType().name();
            if (blockName.contains("LAVA") || blockName.contains("FIRE") ||
                    blockName.contains("MAGMA") || blockName.contains("CACTUS")) {
                return false;
            }

            return true;
        } catch (Exception e) {
            if (isDebugMode()) {
                logger.warning("[Spawner] Error checking spawn location: " + e.getMessage());
            }
            return false;
        }
    }

    // ================ SPAWN CONDITIONS ================

    /**
     * Enhanced spawn condition checking with caching
     */
    private boolean canSpawnHere(long currentTime) {
        // Cache player checks to reduce performance impact
        if (currentTime - lastPlayerCheck > PLAYER_CHECK_INTERVAL) {
            lastPlayerCheck = currentTime;
            // Update cached player nearby status here if needed
        }

        return isWorldAndChunkLoaded() &&
                canSpawnByTimeAndWeather() &&
                isPlayerNearby() &&
                !isSpawnerDisabled() &&
                hasValidMobEntries();
    }

    /**
     * Check if spawner has any valid mob entries
     */
    private boolean hasValidMobEntries() {
        if (mobEntries.isEmpty()) {
            return false;
        }

        // Check if any mob entries are not temporarily disabled
        for (MobEntry entry : mobEntries) {
            if (!temporarilyDisabledTypes.contains(entry.getMobType())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if at capacity
     */
    private boolean isAtCapacity() {
        return getTotalMobCount() >= getMaxMobsAllowed();
    }

    /**
     * Check if this spawner has a group assigned
     */
    private boolean hasSpawnerGroup() {
        return properties.getSpawnerGroup() != null && !properties.getSpawnerGroup().isEmpty();
    }

    /**
     * Check if this spawner is in a loaded chunk
     */
    private boolean isWorldAndChunkLoaded() {
        return location.getWorld() != null &&
                location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Check time and weather restrictions
     */
    private boolean canSpawnByTimeAndWeather() {
        World world = location.getWorld();
        if (world == null) return false;

        if (properties.isTimeRestricted() && !properties.canSpawnByTime(world)) {
            return false;
        }

        if (properties.isWeatherRestricted() && !properties.canSpawnByWeather(world)) {
            return false;
        }

        return true;
    }

    /**
     * Check if a player is nearby
     */
    private boolean isPlayerNearby() {
        double range = properties.getPlayerDetectionRangeOverride() > 0 ?
                properties.getPlayerDetectionRangeOverride() :
                mobManager.getPlayerDetectionRange();

        return MobUtils.isPlayerNearby(location, range);
    }

    /**
     * Check if spawner is disabled
     */
    private boolean isSpawnerDisabled() {
        return !mobManager.areSpawnersEnabled();
    }

    // ================ MOB DEATH HANDLING ================

    /**
     * Register a mob death and add to respawn queue
     */
    public void registerMobDeath(UUID entityId) {
        SpawnedMob deadMob = activeMobs.remove(entityId);

        if (deadMob != null) {
            metrics.recordKill();

            long respawnDelay = calculateRespawnDelay(deadMob.getTier(), deadMob.isElite());
            long respawnTime = System.currentTimeMillis() + respawnDelay;

            // Create a new lightweight entry for the queue
            RespawnEntry respawnEntry = new RespawnEntry(
                    deadMob.getMobType(),
                    deadMob.getTier(),
                    deadMob.isElite(),
                    respawnTime
            );

            respawnQueue.put(UUID.randomUUID(), respawnEntry);
            needsHologramUpdate = true;

            if (isDebugMode()) {
                logger.info("[Spawner] Mob death registered: " + deadMob.getMobType() +
                        " T" + deadMob.getTier() + (deadMob.isElite() ? "+" : "") +
                        " will respawn in " + (respawnDelay / 1000) + " seconds");
            }
        } else if (isDebugMode()) {
            logger.warning("[Spawner] Attempted to register death for unknown mob: " + entityId.toString().substring(0, 8));
        }
    }

    /**
     * Enhanced respawn delay calculation
     */
    private long calculateRespawnDelay(int tier, boolean elite) {
        long baseDelay = MIN_RESPAWN_TIME;

        // Enhanced tier-based scaling
        double tierFactor = 1.0 + ((tier - 1) * 0.3);
        double eliteMultiplier = elite ? 1.8 : 1.0;

        // Apply spawner-specific multiplier if set
        double spawnerMultiplier = 1;
        if (spawnerMultiplier <= 0) spawnerMultiplier = 1.0;

        long calculatedDelay = (long) (baseDelay * tierFactor * eliteMultiplier * spawnerMultiplier);

        // Add some randomization (±20%)
        double randomFactor = 0.8 + (Math.random() * 0.4);
        calculatedDelay = (long) (calculatedDelay * randomFactor);

        // Clamp between 30 seconds and 10 minutes
        return Math.min(Math.max(calculatedDelay, MIN_RESPAWN_TIME), 600000L);
    }

    // ================ HOLOGRAM SYSTEM ================

    /**
     * Update the hologram for this spawner
     */
    public void updateHologram() {
        try {
            if (!visible) {
                removeHologram();
                return;
            }

            List<String> lines = generateHologramLines();
            String holoId = "spawner_" + uniqueId;

            double yOffset = switch (displayMode) {
                case 0 -> 1.5;
                case 1 -> 2.5;
                case 2 -> 3.5;
                default -> 2.0;
            };

            Location holoLocation = location.clone().add(0.5, yOffset, 0.5);
            HologramManager.createOrUpdateHologram(holoId, holoLocation, lines, 0.25);

        } catch (Exception e) {
            logger.warning("[Spawner] Hologram update error: " + e.getMessage());
        }
    }

    /**
     * Update hologram only when needed and with proper timing
     */
    private void updateHologramIfNeeded(long currentTime) {
        if (needsHologramUpdate && visible && (currentTime - lastHologramUpdate) >= HOLOGRAM_UPDATE_INTERVAL) {
            updateHologram();
            needsHologramUpdate = false;
            lastHologramUpdate = currentTime;
        }
    }

    /**
     * Enhanced hologram line generation with better formatting and information
     */
    private List<String> generateHologramLines() {
        List<String> lines = new ArrayList<>();

        try {
            // Title
            String displayName = properties.getDisplayName();
            lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + (displayName != null && !displayName.isEmpty() ? displayName : "Mob Spawner"));

            // Group info
            if (hasSpawnerGroup()) {
                lines.add(ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
            }

            // Status indicator with enhanced status
            String statusColor;
            String statusText;

            if (isSpawnerDisabled()) {
                statusColor = ChatColor.RED.toString();
                statusText = "DISABLED";
            } else if (!hasValidMobEntries()) {
                statusColor = ChatColor.DARK_RED.toString();
                statusText = "NO VALID MOBS";
            } else if (isAtCapacity()) {
                statusColor = ChatColor.YELLOW.toString();
                statusText = "FULL";
            } else {
                statusColor = ChatColor.GREEN.toString();
                statusText = "ACTIVE";
            }

            lines.add(statusColor + "● " + statusText + " ●");

            // Mob types
            lines.add(ChatColor.YELLOW + formatSpawnerData());

            // Status (Uses corrected counts)
            int active = getActiveMobCount();
            int respawning = respawnQueue.size();
            int desired = getDesiredMobCount();
            lines.add(ChatColor.GREEN + "Active: " + ChatColor.WHITE + active +
                    ChatColor.GRAY + " | " + ChatColor.YELLOW + "Queued: " + ChatColor.WHITE + respawning +
                    ChatColor.GRAY + " | " + ChatColor.AQUA + "Target: " + ChatColor.WHITE + desired);

            // Enhanced failure tracking info
            if (displayMode >= 1) {
                // Performance info
                if (totalSpawnAttempts > 0) {
                    double successRate = (double) successfulSpawns / totalSpawnAttempts * 100;
                    lines.add(ChatColor.GRAY + "Success Rate: " + ChatColor.WHITE + String.format("%.1f%%", successRate));
                }

                // Show temporarily disabled types
                if (!temporarilyDisabledTypes.isEmpty()) {
                    lines.add(ChatColor.RED + "Disabled: " + ChatColor.WHITE +
                            String.join(", ", temporarilyDisabledTypes));
                }

                if (!respawnQueue.isEmpty()) {
                    long nextRespawn = respawnQueue.values().stream()
                            .mapToLong(RespawnEntry::getRespawnTime)
                            .min()
                            .orElse(0);
                    long timeLeft = nextRespawn - System.currentTimeMillis();
                    if (timeLeft > 0) {
                        lines.add(ChatColor.YELLOW + "Next respawn: " +
                                ChatColor.WHITE + formatTime(timeLeft));
                    } else {
                        lines.add(ChatColor.GREEN + "Respawning now!");
                    }
                }

                // Conditions
                if (!canSpawnByTimeAndWeather()) {
                    lines.add(ChatColor.RED + "Waiting for conditions...");
                } else if (!isPlayerNearby()) {
                    lines.add(ChatColor.GRAY + "No players nearby");
                }
            }

            // Admin info for display mode 2
            if (displayMode >= 2) {
                lines.add(ChatColor.DARK_GRAY + formatLocation());
                lines.add(ChatColor.DARK_GRAY + "Capacity: " +
                        getTotalMobCount() + "/" + getMaxMobsAllowed());

                if (metrics.getTotalSpawned() > 0) {
                    lines.add(ChatColor.DARK_GRAY + "Total: " + metrics.getTotalSpawned() +
                            " spawned, " + metrics.getTotalKilled() + " killed");
                }

                // Enhanced failure tracking
                int totalFailures = mobTypeFailures.values().stream().mapToInt(Integer::intValue).sum();
                if (totalFailures > 0) {
                    lines.add(ChatColor.DARK_GRAY + "Failures: " + totalFailures);
                }

                // Uptime
                lines.add(ChatColor.DARK_GRAY + "Uptime: " + metrics.getFormattedUptime());
            }

        } catch (Exception e) {
            logger.warning("[Spawner] Hologram generation error: " + e.getMessage());
            lines.clear();
            lines.add(ChatColor.RED + "Error Generating Hologram");
        }

        return lines;
    }

    /**
     * Format mob entries for display using proper tier-specific names
     */
    private String formatSpawnerData() {
        if (mobEntries.isEmpty()) {
            return ChatColor.GRAY + "Empty";
        }

        StringBuilder result = new StringBuilder();
        int maxDisplay = Math.min(mobEntries.size(), 3);

        for (int i = 0; i < maxDisplay; i++) {
            MobEntry entry = mobEntries.get(i);
            if (i > 0) result.append(ChatColor.GRAY).append(", ");

            String mobName = MobUtils.getDisplayName(entry.getMobType());
            ChatColor tierColor = getTierColor(entry.getTier());

            // Check if this mob type is disabled
            if (temporarilyDisabledTypes.contains(entry.getMobType())) {
                result.append(ChatColor.RED).append("✗").append(mobName);
            } else {
                result.append(tierColor).append(mobName);
            }

            result.append(" T").append(entry.getTier())
                    .append(entry.isElite() ? "+" : "")
                    .append("×").append(entry.getAmount());
        }

        if (mobEntries.size() > 3) {
            result.append(ChatColor.GRAY).append(" +").append(mobEntries.size() - 3).append(" more");
        }

        return result.toString();
    }

    /**
     * Get tier color
     */
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

    /**
     * Format time remaining
     */
    private String formatTime(long millis) {
        if (millis < 0) return "N/A";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Remove hologram for this spawner
     */
    public void removeHologram() {
        try {
            String holoId = "spawner_" + uniqueId;
            HologramManager.removeHologram(holoId);
        } catch (Exception e) {
            logger.warning("[Spawner] Hologram removal error: " + e.getMessage());
        }
    }

    // ================ PERFORMANCE MONITORING ================

    /**
     * Update performance metrics
     */
    private void updatePerformanceMetrics(long currentTime) {
        // Reset performance counters every 5 minutes
        if (currentTime - lastPerformanceReset > 300000L) {
            totalSpawnAttempts = 0;
            successfulSpawns = 0;
            failedSpawns = 0;
            lastPerformanceReset = currentTime;
        }
    }

    /**
     * Get spawner performance info
     */
    public String getPerformanceInfo() {
        if (totalSpawnAttempts == 0) {
            return "No spawn attempts recorded";
        }

        double successRate = (double) successfulSpawns / totalSpawnAttempts * 100;
        int totalFailures = mobTypeFailures.values().stream().mapToInt(Integer::intValue).sum();

        return String.format("Performance: %d/%d spawns (%.1f%% success), %d total failures, %d disabled types",
                successfulSpawns, totalSpawnAttempts, successRate, totalFailures, temporarilyDisabledTypes.size());
    }

    // ================ MANAGEMENT METHODS ================

    /**
     * FIXED: Enhanced spawner reset with MAIN THREAD SAFETY
     */
    public void reset() {
        // CRITICAL: Verify we're on the main thread for entity operations
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: reset called from async thread!");

            // Schedule on main thread
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                reset();
            });
            return;
        }

        // Remove all active mobs - MAIN THREAD
        for (UUID mobId : new HashSet<>(activeMobs.keySet())) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && entity.isValid()) {
                entity.remove(); // Safe - on main thread
            }
        }

        activeMobs.clear();
        respawnQueue.clear();
        metrics.reset();

        // Reset failure tracking
        mobTypeFailures.clear();
        lastFailureTime.clear();
        temporarilyDisabledTypes.clear();

        // Reset performance counters
        totalSpawnAttempts = 0;
        successfulSpawns = 0;
        failedSpawns = 0;
        lastPerformanceReset = System.currentTimeMillis();

        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0;
            updateHologram();
        }

        if (isDebugMode()) {
            logger.info("[Spawner] Reset at " + formatLocation());
        }
    }

    /**
     * Enhanced spawner info display
     */
    public void sendInfoTo(Player player) {
        player.sendMessage(ChatColor.GOLD + "╔════ " + ChatColor.YELLOW + "Spawner Information" + ChatColor.GOLD + " ════╗");

        String displayName = properties.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Name: " + ChatColor.WHITE + displayName);
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Location: " + ChatColor.WHITE + formatLocation());
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "ID: " + ChatColor.WHITE + uniqueId);

        if (hasSpawnerGroup()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Group: " + ChatColor.WHITE + properties.getSpawnerGroup());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Mobs: " + ChatColor.WHITE + formatSpawnerData());

        // Enhanced status with more detail
        String status;
        if (isSpawnerDisabled()) {
            status = ChatColor.RED + "DISABLED";
        } else if (!hasValidMobEntries()) {
            status = ChatColor.DARK_RED + "NO VALID MOBS";
        } else if (isAtCapacity()) {
            status = ChatColor.YELLOW + "FULL";
        } else {
            status = ChatColor.GREEN + "ACTIVE";
        }
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Status: " + status);

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Counts: " +
                ChatColor.WHITE + getActiveMobCount() + " active, " + respawnQueue.size() + " respawning, " + getDesiredMobCount() + " target");

        // Enhanced failure information
        if (!temporarilyDisabledTypes.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "Disabled Types: " +
                    ChatColor.WHITE + String.join(", ", temporarilyDisabledTypes));
        }

        if (!respawnQueue.isEmpty()) {
            long nextRespawn = respawnQueue.values().stream()
                    .mapToLong(RespawnEntry::getRespawnTime)
                    .min()
                    .orElse(0);
            long timeLeft = nextRespawn - System.currentTimeMillis();
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Next respawn: " +
                    ChatColor.WHITE + formatTime(Math.max(0, timeLeft)));
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Visible: " +
                (visible ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));

        // Conditions
        boolean timeOk = !properties.isTimeRestricted() || properties.canSpawnByTime(location.getWorld());
        boolean weatherOk = !properties.isWeatherRestricted() || properties.canSpawnByWeather(location.getWorld());
        boolean playersOk = isPlayerNearby();
        boolean chunkOk = isWorldAndChunkLoaded();
        boolean validMobs = hasValidMobEntries();

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Conditions: " +
                (timeOk ? ChatColor.GREEN + "T" : ChatColor.RED + "T") + ChatColor.GRAY + " | " +
                (weatherOk ? ChatColor.GREEN + "W" : ChatColor.RED + "W") + ChatColor.GRAY + " | " +
                (playersOk ? ChatColor.GREEN + "P" : ChatColor.RED + "P") + ChatColor.GRAY + " | " +
                (chunkOk ? ChatColor.GREEN + "C" : ChatColor.RED + "C") + ChatColor.GRAY + " | " +
                (validMobs ? ChatColor.GREEN + "M" : ChatColor.RED + "M"));

        if (properties.isTimeRestricted()) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Time: " +
                    ChatColor.WHITE + properties.getStartHour() + "h - " + properties.getEndHour() + "h");
        }

        if (properties.isWeatherRestricted()) {
            String weather = "";
            if (properties.canSpawnInClear()) weather += "Clear ";
            if (properties.canSpawnInRain()) weather += "Rain ";
            if (properties.canSpawnInThunder()) weather += "Thunder";
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Weather: " + ChatColor.WHITE + weather.trim());
        }

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Radius: " +
                ChatColor.WHITE + String.format("%.1f", properties.getSpawnRadiusX()) + "x" +
                String.format("%.1f", properties.getSpawnRadiusY()) + "x" +
                String.format("%.1f", properties.getSpawnRadiusZ()));

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Capacity: " +
                ChatColor.WHITE + getTotalMobCount() + "/" + getMaxMobsAllowed());

        if (metrics.getTotalSpawned() > 0) {
            player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Statistics: " +
                    ChatColor.WHITE + metrics.getTotalSpawned() + " spawned, " +
                    metrics.getTotalKilled() + " killed");
        }

        // Enhanced performance info
        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Performance: " +
                ChatColor.WHITE + getPerformanceInfo());

        player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Uptime: " + ChatColor.WHITE + metrics.getFormattedUptime());

        player.sendMessage(ChatColor.GOLD + "╚════════════════════════════╝");
    }

    // ================ UTILITY METHODS ================

    /**
     * Format location for display
     */
    private String formatLocation() {
        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    /**
     * Check if debug mode is enabled
     */
    private boolean isDebugMode() {
        return YakRealms.getInstance().isDebugMode();
    }

    /**
     * Enhanced debug information
     */
    public String getDebugInfo() {
        StringBuilder debug = new StringBuilder();
        debug.append("Spawner Debug Info:\n");
        debug.append("Location: ").append(formatLocation()).append("\n");
        debug.append("ID: ").append(uniqueId).append("\n");
        debug.append("Visible: ").append(visible).append("\n");
        debug.append("Display Mode: ").append(displayMode).append("\n");
        debug.append("Mob Entries: ").append(mobEntries.size()).append("\n");

        for (int i = 0; i < mobEntries.size(); i++) {
            MobEntry entry = mobEntries.get(i);
            boolean disabled = temporarilyDisabledTypes.contains(entry.getMobType());
            debug.append("  [").append(i).append("] ").append(entry.toString())
                    .append(disabled ? " (DISABLED)" : "").append("\n");
        }

        debug.append("Active Mobs: ").append(getActiveMobCount()).append("\n");
        debug.append("Respawn Queue: ").append(respawnQueue.size()).append("\n");

        // Debug respawn queue contents
        for (Map.Entry<UUID, RespawnEntry> entry : respawnQueue.entrySet()) {
            RespawnEntry re = entry.getValue();
            long timeLeft = re.getRespawnTime() - System.currentTimeMillis();
            debug.append("  Queue: ").append(re.getMobType())
                    .append(" T").append(re.getTier())
                    .append(re.isElite() ? "+" : "")
                    .append(" in ").append(formatTime(Math.max(0, timeLeft))).append("\n");
        }

        debug.append("Total Spawned: ").append(metrics.getTotalSpawned()).append("\n");
        debug.append("Total Killed: ").append(metrics.getTotalKilled()).append("\n");
        debug.append("Total Capacity: ").append(getTotalMobCount()).append("/").append(getMaxMobsAllowed()).append("\n");
        debug.append("Performance: ").append(getPerformanceInfo()).append("\n");
        debug.append("Failure Tracking:\n");

        for (Map.Entry<String, Integer> entry : mobTypeFailures.entrySet()) {
            if (entry.getValue() > 0) {
                debug.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" failures\n");
            }
        }

        if (!temporarilyDisabledTypes.isEmpty()) {
            debug.append("Disabled Types: ").append(String.join(", ", temporarilyDisabledTypes)).append("\n");
        }

        debug.append("Conditions: Time=").append(canSpawnByTimeAndWeather())
                .append(", Players=").append(isPlayerNearby())
                .append(", Chunk=").append(isWorldAndChunkLoaded())
                .append(", ValidMobs=").append(hasValidMobEntries()).append("\n");

        return debug.toString();
    }

    /**
     * FIXED: Force an immediate spawn attempt with MAIN THREAD SAFETY
     */
    public boolean forceSpawn() {
        // CRITICAL: Verify we're on the main thread for entity operations
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: forceSpawn called from async thread!");

            // Schedule on main thread
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                forceSpawn();
            });
            return false;
        }

        if (mobEntries.isEmpty()) {
            if (isDebugMode()) {
                logger.warning("[Spawner] Cannot force spawn - no mob entries configured");
            }
            return false;
        }

        try {
            MobEntry entry = mobEntries.get(0);
            totalSpawnAttempts++;
            boolean success = spawnSingleMobEnhanced(entry.getMobType(), entry.getTier(), entry.isElite());

            if (success) {
                successfulSpawns++;
            } else {
                failedSpawns++;
            }

            if (isDebugMode()) {
                logger.info("[Spawner] Force spawn " + (success ? "succeeded" : "failed") +
                        " for " + entry.getMobType() + " T" + entry.getTier() +
                        (entry.isElite() ? "+" : ""));
            }

            return success;
        } catch (Exception e) {
            logger.warning("[Spawner] Force spawn error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get status summary for this spawner
     */
    public String getStatusSummary() {
        int active = getActiveMobCount();
        int queued = respawnQueue.size();
        int desired = getDesiredMobCount();
        int capacity = getMaxMobsAllowed();

        String status;
        if (isSpawnerDisabled()) {
            status = "DISABLED";
        } else if (!hasValidMobEntries()) {
            status = "NO VALID MOBS";
        } else if (isAtCapacity()) {
            status = "FULL";
        } else {
            status = "ACTIVE";
        }

        return String.format("Status: %s | %d/%d active, %d queued, %d/%d capacity, %d disabled types",
                status, active, desired, queued, getTotalMobCount(), capacity, temporarilyDisabledTypes.size());
    }

    // ================ GETTERS AND SETTERS ================

    public Location getLocation() { return location.clone(); }
    public boolean isVisible() { return visible; }
    public SpawnerProperties getProperties() { return properties; }
    public int getDisplayMode() { return displayMode; }
    public SpawnerMetrics getMetrics() { return metrics; }
    public List<MobEntry> getMobEntries() { return new ArrayList<>(mobEntries); }
    public String getUniqueId() { return uniqueId; }
    public Collection<SpawnedMob> getActiveMobs() { return new ArrayList<>(activeMobs.values()); }
    public int getQueuedCount() { return respawnQueue.size(); }
    public int getActiveMobCount() { return activeMobs.size(); }
    public int getDesiredMobCount() { return mobEntries.stream().mapToInt(MobEntry::getAmount).sum(); }

    public int getMaxMobsAllowed() {
        return properties.getMaxMobOverride() > 0 ?
                properties.getMaxMobOverride() :
                mobManager.getMaxMobsPerSpawner();
    }

    public boolean ownsMob(UUID entityId) { return activeMobs.containsKey(entityId); }

    public void setVisible(boolean visible) {
        this.visible = visible;
        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0; // Force immediate update
            updateHologram();
        } else {
            removeHologram();
        }
    }

    public void setProperties(SpawnerProperties properties) {
        this.properties = properties;
        needsHologramUpdate = true;
    }

    public void setDisplayMode(int displayMode) {
        this.displayMode = Math.max(0, Math.min(2, displayMode));
        needsHologramUpdate = true;
        if (visible) {
            lastHologramUpdate = 0; // Force immediate update
            updateHologram();
        }
    }

    // ================ THREAD SAFETY HELPERS ================

    /**
     * THREAD SAFETY HELPER: Check if we're on the main thread
     */
    private boolean ensureMainThread(String methodName) {
        if (!Bukkit.isPrimaryThread()) {
            logger.severe("[Spawner] CRITICAL: " + methodName + " called from async thread!");
            return false;
        }
        return true;
    }

    /**
     * THREAD SAFETY HELPER: Schedule task on main thread if needed
     */
    private void scheduleOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), task);
        }
    }

    // ================ DIAGNOSTIC METHODS ================

    /**
     * Get detailed diagnostic information for troubleshooting
     */
    public Map<String, Object> getDetailedDiagnostics() {
        Map<String, Object> diagnostics = new HashMap<>();

        diagnostics.put("location", formatLocation());
        diagnostics.put("uniqueId", uniqueId);
        diagnostics.put("visible", visible);
        diagnostics.put("mobEntries", mobEntries.size());
        diagnostics.put("activeMobs", activeMobs.size());
        diagnostics.put("respawnQueue", respawnQueue.size());
        diagnostics.put("totalSpawnAttempts", totalSpawnAttempts);
        diagnostics.put("successfulSpawns", successfulSpawns);
        diagnostics.put("failedSpawns", failedSpawns);
        diagnostics.put("mobTypeFailures", new HashMap<>(mobTypeFailures));
        diagnostics.put("temporarilyDisabledTypes", new HashSet<>(temporarilyDisabledTypes));
        diagnostics.put("canSpawn", canSpawnHere(System.currentTimeMillis()));
        diagnostics.put("hasValidMobs", hasValidMobEntries());
        diagnostics.put("isAtCapacity", isAtCapacity());
        diagnostics.put("playerNearby", isPlayerNearby());
        diagnostics.put("chunkLoaded", isWorldAndChunkLoaded());

        return diagnostics;
    }
}
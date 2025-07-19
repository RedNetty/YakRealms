package com.rednetty.server.mechanics.world.holograms;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * ENHANCED ROBUST HOLOGRAM MANAGER
 *
 * FIXES:
 * - Comprehensive cleanup system with multiple validation layers
 * - Periodic orphan detection and removal tasks
 * - Thread-safe hologram lifecycle management
 * - Enhanced session validation and tracking
 * - Automatic error recovery and hologram healing
 * - Detailed logging and diagnostics
 * - Force cleanup capabilities for stuck holograms
 * - Hologram state tracking and validation
 */
public class HologramManager {

    // ================ CONSTANTS ================
    private static final long CLEANUP_INTERVAL = 600L; // 30 seconds (reduced for more frequent cleanup)
    private static final long ORPHAN_SCAN_INTERVAL = 1200L; // 1 minute
    private static final long HEALTH_CHECK_INTERVAL = 200L; // 10 seconds
    private static final double MOVEMENT_THRESHOLD = 0.05; // More sensitive movement detection
    private static final int MAX_HOLOGRAM_AGE_TICKS = 72000; // 1 hour max age
    private static final String HOLOGRAM_METADATA_KEY = "yak_hologram";
    private static final String SESSION_METADATA_KEY = "yak_session";
    private static final String CREATION_TIME_KEY = "yak_created";
    private static final String HOLOGRAM_TYPE_KEY = "yak_holo_type";

    // ================ SYSTEM STATE ================
    private static final Logger logger = YakRealms.getInstance().getLogger();
    private static final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private static final Map<UUID, String> entityToHologramId = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingRemovals = ConcurrentHashMap.newKeySet();
    private static final ReentrantReadWriteLock hologramLock = new ReentrantReadWriteLock();

    // ================ CLEANUP TASKS ================
    private static BukkitTask cleanupTask;
    private static BukkitTask orphanScanTask;
    private static BukkitTask healthCheckTask;
    private static boolean systemInitialized = false;
    private static long sessionId = System.currentTimeMillis();

    // ================ STATISTICS ================
    private static long totalHologramsCreated = 0;
    private static long totalHologramsRemoved = 0;
    private static long totalOrphansRemoved = 0;
    private static long lastCleanupTime = 0;

    /**
     * Enhanced Hologram class with comprehensive state tracking
     */
    public static class Hologram {
        private final List<ArmorStand> armorStands = new ArrayList<>();
        private Location baseLocation;
        private final double lineSpacing;
        private final String hologramId;
        private List<String> currentLines = new ArrayList<>();
        private final long creationTime;
        private long lastUpdateTime;
        private volatile boolean isValid = true;
        private volatile boolean isRemoving = false;
        private final String hologramType;
        private int failedUpdateCount = 0;

        /**
         * Enhanced constructor with type tracking
         */
        public Hologram(Location baseLocation, List<String> lines, double lineSpacing, String hologramId, String type) {
            this.baseLocation = baseLocation.clone();
            this.lineSpacing = lineSpacing;
            this.hologramId = hologramId;
            this.currentLines = new ArrayList<>(lines);
            this.creationTime = System.currentTimeMillis();
            this.lastUpdateTime = creationTime;
            this.hologramType = type != null ? type : "unknown";

            if (!systemInitialized) {
                initializeCleanupSystem();
            }

            spawn(lines);
            totalHologramsCreated++;
        }

        /**
         * ENHANCED: Move hologram with comprehensive validation and error recovery
         */
        public boolean moveToLocation(Location newLocation) {
            if (isRemoving || !isValid) {
                return false;
            }

            if (newLocation == null || newLocation.getWorld() == null) {
                logger.warning("[HologramManager] Invalid location for hologram " + hologramId);
                return false;
            }

            // Verify all ArmorStands are still valid
            if (!validateArmorStands()) {
                logger.warning("[HologramManager] ArmorStands validation failed for " + hologramId + ", attempting recovery");
                if (!attemptRecovery()) {
                    markInvalid();
                    return false;
                }
            }

            // Check if movement is significant enough
            if (baseLocation.getWorld().equals(newLocation.getWorld()) &&
                    baseLocation.distance(newLocation) < MOVEMENT_THRESHOLD) {
                return false;
            }

            try {
                // Calculate movement delta
                double deltaX = newLocation.getX() - baseLocation.getX();
                double deltaY = newLocation.getY() - baseLocation.getY();
                double deltaZ = newLocation.getZ() - baseLocation.getZ();

                // Update base location
                baseLocation = newLocation.clone();
                lastUpdateTime = System.currentTimeMillis();

                // Move all ArmorStands with validation
                List<ArmorStand> invalidStands = new ArrayList<>();
                for (ArmorStand armorStand : armorStands) {
                    if (armorStand != null && !armorStand.isDead()) {
                        try {
                            Location currentLoc = armorStand.getLocation();
                            Location newLoc = currentLoc.add(deltaX, deltaY, deltaZ);
                            armorStand.teleport(newLoc);
                        } catch (Exception e) {
                            logger.warning("[HologramManager] Failed to move ArmorStand for " + hologramId + ": " + e.getMessage());
                            invalidStands.add(armorStand);
                        }
                    } else {
                        invalidStands.add(armorStand);
                    }
                }

                // Remove invalid ArmorStands
                if (!invalidStands.isEmpty()) {
                    armorStands.removeAll(invalidStands);
                    if (armorStands.isEmpty()) {
                        markInvalid();
                        return false;
                    }
                }

                return true;

            } catch (Exception e) {
                logger.severe("[HologramManager] Critical error moving hologram " + hologramId + ": " + e.getMessage());
                failedUpdateCount++;
                if (failedUpdateCount > 3) {
                    markInvalid();
                }
                return false;
            }
        }

        /**
         * ENHANCED: Update text with validation and recovery
         */
        public boolean updateTextOnly(List<String> newLines) {
            if (isRemoving || !isValid) {
                return false;
            }

            if (newLines == null || newLines.equals(currentLines)) {
                return false;
            }

            // Validate ArmorStands before updating
            if (!validateArmorStands()) {
                logger.warning("[HologramManager] ArmorStands validation failed during text update for " + hologramId);
                if (!attemptRecovery()) {
                    markInvalid();
                    return false;
                }
            }

            try {
                // If line count changed, recreate hologram
                if (newLines.size() != armorStands.size()) {
                    updateLines(newLines);
                    return true;
                }

                // Update existing ArmorStands
                List<ArmorStand> invalidStands = new ArrayList<>();
                for (int i = 0; i < Math.min(newLines.size(), armorStands.size()); i++) {
                    ArmorStand armorStand = armorStands.get(i);
                    if (armorStand != null && !armorStand.isDead()) {
                        try {
                            armorStand.setCustomName(newLines.get(i));
                        } catch (Exception e) {
                            logger.warning("[HologramManager] Failed to update text for ArmorStand in " + hologramId + ": " + e.getMessage());
                            invalidStands.add(armorStand);
                        }
                    } else {
                        invalidStands.add(armorStand);
                    }
                }

                // Remove invalid ArmorStands
                if (!invalidStands.isEmpty()) {
                    armorStands.removeAll(invalidStands);
                    if (armorStands.isEmpty()) {
                        markInvalid();
                        return false;
                    }
                }

                currentLines = new ArrayList<>(newLines);
                lastUpdateTime = System.currentTimeMillis();
                return true;

            } catch (Exception e) {
                logger.severe("[HologramManager] Critical error updating text for " + hologramId + ": " + e.getMessage());
                failedUpdateCount++;
                if (failedUpdateCount > 3) {
                    markInvalid();
                }
                return false;
            }
        }

        /**
         * ENHANCED: Combined move and update with comprehensive error handling
         */
        public boolean moveAndUpdate(Location newLocation, List<String> newLines) {
            if (isRemoving || !isValid) {
                return false;
            }

            boolean moved = false;
            boolean updated = false;

            try {
                // Validate before any operations
                if (!validateArmorStands()) {
                    if (!attemptRecovery()) {
                        markInvalid();
                        return false;
                    }
                }

                // Move if needed
                if (newLocation != null && newLocation.getWorld() != null) {
                    moved = moveToLocation(newLocation);
                }

                // Update text if needed
                if (newLines != null) {
                    updated = updateTextOnly(newLines);
                }

                if (moved || updated) {
                    lastUpdateTime = System.currentTimeMillis();
                }

                return moved || updated;

            } catch (Exception e) {
                logger.severe("[HologramManager] Critical error in moveAndUpdate for " + hologramId + ": " + e.getMessage());
                markInvalid();
                return false;
            }
        }

        /**
         * ENHANCED: Validate all ArmorStands are still valid
         */
        public boolean validateArmorStands() {
            if (armorStands.isEmpty()) {
                return false;
            }

            int validCount = 0;
            for (ArmorStand armorStand : armorStands) {
                if (armorStand != null && !armorStand.isDead() && armorStand.isValid()) {
                    if (validateSessionId(armorStand)) {
                        validCount++;
                    }
                }
            }

            // At least 50% of ArmorStands must be valid
            return validCount >= (armorStands.size() * 0.5);
        }

        /**
         * ENHANCED: Attempt to recover from corrupted state
         */
        public boolean attemptRecovery() {
            try {
                logger.info("[HologramManager] Attempting recovery for hologram " + hologramId);

                // Remove invalid ArmorStands
                Iterator<ArmorStand> iterator = armorStands.iterator();
                while (iterator.hasNext()) {
                    ArmorStand armorStand = iterator.next();
                    if (armorStand == null || armorStand.isDead() || !armorStand.isValid()) {
                        iterator.remove();
                    }
                }

                // If we lost all ArmorStands, recreate them
                if (armorStands.isEmpty() && !currentLines.isEmpty()) {
                    spawn(currentLines);
                    return !armorStands.isEmpty();
                }

                return true;

            } catch (Exception e) {
                logger.severe("[HologramManager] Recovery failed for " + hologramId + ": " + e.getMessage());
                return false;
            }
        }

        /**
         * Enhanced session validation
         */
        public boolean validateSessionId(ArmorStand armorStand) {
            if (!armorStand.hasMetadata(SESSION_METADATA_KEY)) {
                return false;
            }

            try {
                List<MetadataValue> metadataValues = armorStand.getMetadata(SESSION_METADATA_KEY);
                for (MetadataValue meta : metadataValues) {
                    if (meta.asLong() == sessionId) {
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.warning("[HologramManager] Session validation error for " + hologramId + ": " + e.getMessage());
            }

            return false;
        }

        /**
         * ENHANCED: Spawn ArmorStands with comprehensive metadata and error handling
         */
        private void spawn(List<String> lines) {
            if (baseLocation == null || baseLocation.getWorld() == null) {
                logger.warning("[HologramManager] Cannot spawn hologram " + hologramId + ": invalid location");
                markInvalid();
                return;
            }

            if (isRemoving) {
                return;
            }

            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    Location spawnLocation = baseLocation.clone();
                    List<ArmorStand> newArmorStands = new ArrayList<>();

                    for (String line : lines) {
                        try {
                            // Ensure chunk is loaded
                            if (!spawnLocation.getWorld().isChunkLoaded(spawnLocation.getBlockX() >> 4, spawnLocation.getBlockZ() >> 4)) {
                                spawnLocation.getWorld().loadChunk(spawnLocation.getBlockX() >> 4, spawnLocation.getBlockZ() >> 4);
                            }

                            ArmorStand armorStand = (ArmorStand) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);

                            // Configure ArmorStand
                            armorStand.setVisible(false);
                            armorStand.setGravity(false);
                            armorStand.setMarker(true);
                            armorStand.setInvulnerable(true);
                            armorStand.setSmall(true);
                            armorStand.setCanPickupItems(false);
                            armorStand.setCollidable(false);
                            armorStand.setCustomName(line);
                            armorStand.setCustomNameVisible(true);

                            // Enhanced metadata
                            armorStand.setMetadata(SESSION_METADATA_KEY, new FixedMetadataValue(YakRealms.getInstance(), sessionId));
                            armorStand.setMetadata(HOLOGRAM_METADATA_KEY, new FixedMetadataValue(YakRealms.getInstance(), hologramId));
                            armorStand.setMetadata(CREATION_TIME_KEY, new FixedMetadataValue(YakRealms.getInstance(), creationTime));
                            armorStand.setMetadata(HOLOGRAM_TYPE_KEY, new FixedMetadataValue(YakRealms.getInstance(), hologramType));

                            newArmorStands.add(armorStand);
                            entityToHologramId.put(armorStand.getUniqueId(), hologramId);

                        } catch (Exception e) {
                            logger.severe("[HologramManager] Failed to spawn ArmorStand for line '" + line + "' in hologram " + hologramId + ": " + e.getMessage());
                        }

                        spawnLocation.subtract(0, lineSpacing, 0);
                    }

                    if (newArmorStands.isEmpty()) {
                        logger.severe("[HologramManager] Failed to spawn any ArmorStands for hologram " + hologramId);
                        markInvalid();
                    } else {
                        armorStands.addAll(newArmorStands);
                        logger.info("[HologramManager] Successfully spawned " + newArmorStands.size() + " ArmorStands for hologram " + hologramId);
                    }

                } catch (Exception e) {
                    logger.severe("[HologramManager] Critical error spawning hologram " + hologramId + ": " + e.getMessage());
                    markInvalid();
                }
            });
        }

        /**
         * ENHANCED: Update lines with better error handling
         */
        public void updateLines(List<String> newLines) {
            if (newLines == null || isRemoving) {
                return;
            }

            try {
                remove();
                currentLines = new ArrayList<>(newLines);
                if (isValid) {
                    spawn(newLines);
                }
            } catch (Exception e) {
                logger.severe("[HologramManager] Failed to update lines for " + hologramId + ": " + e.getMessage());
                markInvalid();
            }
        }

        /**
         * ENHANCED: Remove with comprehensive cleanup
         */
        public void remove() {
            if (isRemoving) {
                return;
            }

            isRemoving = true;
            isValid = false;

            try {
                List<ArmorStand> armorStandsCopy = new ArrayList<>(armorStands);
                armorStands.clear();

                for (ArmorStand armorStand : armorStandsCopy) {
                    if (armorStand != null && !armorStand.isDead()) {
                        try {
                            // Remove from entity tracking
                            entityToHologramId.remove(armorStand.getUniqueId());

                            // Remove the entity
                            armorStand.remove();

                        } catch (Exception e) {
                            logger.warning("[HologramManager] Error removing ArmorStand for " + hologramId + ": " + e.getMessage());
                        }
                    }
                }

                currentLines.clear();
                totalHologramsRemoved++;

                logger.info("[HologramManager] Successfully removed hologram " + hologramId);

            } catch (Exception e) {
                logger.severe("[HologramManager] Critical error removing hologram " + hologramId + ": " + e.getMessage());
            }
        }

        /**
         * Mark hologram as invalid
         */
        private void markInvalid() {
            isValid = false;
            pendingRemovals.add(UUID.fromString(hologramId.length() > 36 ? hologramId.substring(0, 36) : hologramId + "00000000-0000-0000-0000-000000000000".substring(hologramId.length())));
        }

        // ================ GETTERS ================
        public Location getBaseLocation() { return baseLocation != null ? baseLocation.clone() : null; }
        public int getLineCount() { return armorStands.size(); }
        public List<String> getCurrentLines() { return new ArrayList<>(currentLines); }
        public boolean isValid() { return isValid && !isRemoving && !armorStands.isEmpty(); }
        public long getCreationTime() { return creationTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public String getHologramId() { return hologramId; }
        public String getHologramType() { return hologramType; }
        public int getFailedUpdateCount() { return failedUpdateCount; }
        public long getAge() { return System.currentTimeMillis() - creationTime; }
    }

    // ================ INITIALIZATION ================

    /**
     * Initialize the comprehensive cleanup system
     */
    private static void initializeCleanupSystem() {
        if (systemInitialized) {
            return;
        }

        logger.info("[HologramManager] Initializing enhanced cleanup system...");

        // Perform initial cleanup
        performInitialCleanup();

        // Start periodic cleanup task
        cleanupTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                performPeriodicCleanup();
            } catch (Exception e) {
                logger.severe("[HologramManager] Error in cleanup task: " + e.getMessage());
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);

        // Start orphan scan task
        orphanScanTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                performOrphanScan();
            } catch (Exception e) {
                logger.severe("[HologramManager] Error in orphan scan task: " + e.getMessage());
            }
        }, ORPHAN_SCAN_INTERVAL, ORPHAN_SCAN_INTERVAL);

        // Start health check task
        healthCheckTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                logger.severe("[HologramManager] Error in health check task: " + e.getMessage());
            }
        }, HEALTH_CHECK_INTERVAL, HEALTH_CHECK_INTERVAL);

        systemInitialized = true;
        logger.info("[HologramManager] Enhanced cleanup system initialized successfully");
    }

    // ================ CLEANUP OPERATIONS ================

    /**
     * Perform initial cleanup on system start
     */
    private static void performInitialCleanup() {
        logger.info("[HologramManager] Performing initial hologram cleanup...");

        int removedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            try {
                List<Entity> entities = new ArrayList<>(world.getEntitiesByClass(ArmorStand.class));
                for (Entity entity : entities) {
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        ArmorStand armorStand = (ArmorStand) entity;

                        // Remove old holograms with different session IDs
                        if (armorStand.hasMetadata(SESSION_METADATA_KEY) ||
                                armorStand.hasMetadata("id") ||
                                armorStand.hasMetadata(HOLOGRAM_METADATA_KEY)) {

                            boolean shouldRemove = false;

                            // Check session ID
                            if (armorStand.hasMetadata(SESSION_METADATA_KEY)) {
                                try {
                                    long entitySessionId = armorStand.getMetadata(SESSION_METADATA_KEY).get(0).asLong();
                                    if (entitySessionId != sessionId) {
                                        shouldRemove = true;
                                    }
                                } catch (Exception e) {
                                    shouldRemove = true;
                                }
                            } else {
                                shouldRemove = true; // No session ID = old system
                            }

                            // Check age
                            if (armorStand.hasMetadata(CREATION_TIME_KEY)) {
                                try {
                                    long creationTime = armorStand.getMetadata(CREATION_TIME_KEY).get(0).asLong();
                                    long age = System.currentTimeMillis() - creationTime;
                                    if (age > MAX_HOLOGRAM_AGE_TICKS * 50) { // Convert ticks to ms
                                        shouldRemove = true;
                                    }
                                } catch (Exception e) {
                                    shouldRemove = true;
                                }
                            }

                            if (shouldRemove) {
                                try {
                                    armorStand.remove();
                                    removedCount++;
                                } catch (Exception e) {
                                    logger.warning("[HologramManager] Failed to remove old hologram ArmorStand: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[HologramManager] Error cleaning world " + world.getName() + ": " + e.getMessage());
            }
        }

        if (removedCount > 0) {
            logger.info("[HologramManager] Initial cleanup removed " + removedCount + " old hologram entities");
        }

        totalOrphansRemoved += removedCount;
        lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * Perform periodic cleanup of invalid holograms
     */
    private static void performPeriodicCleanup() {
        hologramLock.writeLock().lock();
        try {
            List<String> toRemove = new ArrayList<>();
            int invalidCount = 0;
            int recoveredCount = 0;

            for (Map.Entry<String, Hologram> entry : holograms.entrySet()) {
                String id = entry.getKey();
                Hologram hologram = entry.getValue();

                if (hologram == null) {
                    toRemove.add(id);
                    continue;
                }

                // Check if hologram is valid
                if (!hologram.isValid()) {
                    toRemove.add(id);
                    invalidCount++;
                    continue;
                }

                // Attempt to validate and recover if needed
                if (!hologram.validateArmorStands()) {
                    logger.info("[HologramManager] Attempting recovery for hologram " + id);
                    if (hologram.attemptRecovery()) {
                        recoveredCount++;
                    } else {
                        toRemove.add(id);
                        invalidCount++;
                    }
                }

                // Check age
                if (hologram.getAge() > MAX_HOLOGRAM_AGE_TICKS * 50) {
                    logger.info("[HologramManager] Removing aged hologram " + id);
                    toRemove.add(id);
                }
            }

            // Remove invalid holograms
            for (String id : toRemove) {
                Hologram hologram = holograms.remove(id);
                if (hologram != null) {
                    hologram.remove();
                }
            }

            // Clean up pending removals
            pendingRemovals.clear();

            lastCleanupTime = System.currentTimeMillis();

            if (invalidCount > 0 || recoveredCount > 0) {
                logger.info("[HologramManager] Periodic cleanup: removed " + invalidCount +
                        " invalid holograms, recovered " + recoveredCount + " holograms");
            }

        } finally {
            hologramLock.writeLock().unlock();
        }
    }

    /**
     * Perform comprehensive orphan scan across all worlds
     */
    private static void performOrphanScan() {
        int orphansFound = 0;

        for (World world : Bukkit.getWorlds()) {
            try {
                List<Entity> entities = new ArrayList<>(world.getEntitiesByClass(ArmorStand.class));
                for (Entity entity : entities) {
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        ArmorStand armorStand = (ArmorStand) entity;

                        boolean isOrphan = false;

                        // Check if it's a hologram ArmorStand
                        if (armorStand.hasMetadata(HOLOGRAM_METADATA_KEY) ||
                                armorStand.hasMetadata("id") ||
                                armorStand.hasMetadata("hologramId")) {

                            String hologramId = null;

                            // Try to get hologram ID
                            if (armorStand.hasMetadata(HOLOGRAM_METADATA_KEY)) {
                                try {
                                    hologramId = armorStand.getMetadata(HOLOGRAM_METADATA_KEY).get(0).asString();
                                } catch (Exception e) {
                                    isOrphan = true;
                                }
                            }

                            // Check if hologram still exists in our system
                            if (hologramId != null && !holograms.containsKey(hologramId)) {
                                isOrphan = true;
                            }

                            // Check session ID
                            if (armorStand.hasMetadata(SESSION_METADATA_KEY)) {
                                try {
                                    long entitySessionId = armorStand.getMetadata(SESSION_METADATA_KEY).get(0).asLong();
                                    if (entitySessionId != sessionId) {
                                        isOrphan = true;
                                    }
                                } catch (Exception e) {
                                    isOrphan = true;
                                }
                            } else if (armorStand.hasMetadata("id")) {
                                // Old system ArmorStand
                                isOrphan = true;
                            }

                            // Check if entity is tracked
                            if (!entityToHologramId.containsKey(armorStand.getUniqueId())) {
                                isOrphan = true;
                            }

                            if (isOrphan) {
                                try {
                                    entityToHologramId.remove(armorStand.getUniqueId());
                                    armorStand.remove();
                                    orphansFound++;
                                } catch (Exception e) {
                                    logger.warning("[HologramManager] Failed to remove orphan ArmorStand: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[HologramManager] Error scanning world " + world.getName() + " for orphans: " + e.getMessage());
            }
        }

        if (orphansFound > 0) {
            logger.info("[HologramManager] Orphan scan removed " + orphansFound + " orphaned hologram entities");
            totalOrphansRemoved += orphansFound;
        }
    }

    /**
     * Perform health check on all active holograms
     */
    private static void performHealthCheck() {
        hologramLock.readLock().lock();
        try {
            int healthyCount = 0;
            int unhealthyCount = 0;

            for (Hologram hologram : holograms.values()) {
                if (hologram != null && hologram.isValid()) {
                    if (hologram.validateArmorStands()) {
                        healthyCount++;
                    } else {
                        unhealthyCount++;
                        logger.warning("[HologramManager] Unhealthy hologram detected: " + hologram.getHologramId());
                    }
                }
            }

            // Log health status occasionally
            if (unhealthyCount > 0) {
                logger.info("[HologramManager] Health check: " + healthyCount + " healthy, " + unhealthyCount + " unhealthy holograms");
            }

        } finally {
            hologramLock.readLock().unlock();
        }
    }

    // ================ PUBLIC API ================

    /**
     * ENHANCED: Efficient update method with comprehensive error handling
     */
    public static synchronized boolean updateHologramEfficiently(String id, Location location, List<String> lines, double lineSpacing) {
        return updateHologramEfficiently(id, location, lines, lineSpacing, "mob");
    }

    /**
     * ENHANCED: Efficient update method with type specification
     */
    public static synchronized boolean updateHologramEfficiently(String id, Location location, List<String> lines, double lineSpacing, String type) {
        if (id == null || id.trim().isEmpty()) {
            logger.warning("[HologramManager] Cannot update hologram: ID is null or empty");
            return false;
        }

        if (location == null || location.getWorld() == null) {
            logger.warning("[HologramManager] Cannot update hologram: invalid location for ID " + id);
            return false;
        }

        if (lines == null || lines.isEmpty()) {
            logger.warning("[HologramManager] Cannot update hologram: no lines provided for ID " + id);
            return false;
        }

        hologramLock.writeLock().lock();
        try {
            Hologram existingHologram = holograms.get(id);

            if (existingHologram != null && existingHologram.isValid()) {
                // Try to update existing hologram
                boolean updated = existingHologram.moveAndUpdate(location, lines);
                if (updated) {
                    return true;
                }

                // If update failed, try recovery
                if (!existingHologram.attemptRecovery()) {
                    logger.warning("[HologramManager] Failed to recover hologram " + id + ", recreating");
                    removeHologram(id);
                } else {
                    return existingHologram.moveAndUpdate(location, lines);
                }
            } else {
                // Remove any invalid hologram
                if (existingHologram != null) {
                    removeHologram(id);
                }
            }

            // Create new hologram
            try {
                Hologram hologram = new Hologram(location, lines, lineSpacing, id, type);
                holograms.put(id, hologram);
                return true;
            } catch (Exception e) {
                logger.severe("[HologramManager] Failed to create hologram " + id + ": " + e.getMessage());
                return false;
            }

        } finally {
            hologramLock.writeLock().unlock();
        }
    }

    /**
     * Default efficient update method
     */
    public static synchronized boolean updateHologramEfficiently(String id, Location location, List<String> lines) {
        return updateHologramEfficiently(id, location, lines, 0.25, "mob");
    }

    /**
     * Remove hologram with enhanced cleanup
     */
    public static synchronized void removeHologram(String id) {
        if (id == null) {
            return;
        }

        hologramLock.writeLock().lock();
        try {
            Hologram hologram = holograms.remove(id);
            if (hologram != null) {
                hologram.remove();
                logger.info("[HologramManager] Removed hologram: " + id);
            }
        } finally {
            hologramLock.writeLock().unlock();
        }
    }

    /**
     * Force remove all holograms matching a pattern
     */
    public static synchronized int forceRemoveHologramsByPattern(String pattern) {
        int removedCount = 0;

        hologramLock.writeLock().lock();
        try {
            List<String> toRemove = new ArrayList<>();

            for (String id : holograms.keySet()) {
                if (id.contains(pattern)) {
                    toRemove.add(id);
                }
            }

            for (String id : toRemove) {
                removeHologram(id);
                removedCount++;
            }

        } finally {
            hologramLock.writeLock().unlock();
        }

        logger.info("[HologramManager] Force removed " + removedCount + " holograms matching pattern: " + pattern);
        return removedCount;
    }

    /**
     * Force cleanup all holograms
     */
    public static synchronized void forceCleanupAll() {
        logger.info("[HologramManager] Force cleanup requested - removing all holograms");

        hologramLock.writeLock().lock();
        try {
            // Remove all tracked holograms
            List<String> hologramIds = new ArrayList<>(holograms.keySet());
            for (String id : hologramIds) {
                removeHologram(id);
            }

            // Clear tracking maps
            entityToHologramId.clear();
            pendingRemovals.clear();

        } finally {
            hologramLock.writeLock().unlock();
        }

        // Scan all worlds for any remaining hologram entities
        int totalRemoved = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                List<Entity> entities = new ArrayList<>(world.getEntitiesByClass(ArmorStand.class));
                for (Entity entity : entities) {
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        ArmorStand armorStand = (ArmorStand) entity;

                        // Check if it looks like a hologram
                        if (armorStand.hasMetadata(HOLOGRAM_METADATA_KEY) ||
                                armorStand.hasMetadata("id") ||
                                armorStand.hasMetadata("hologramId") ||
                                (!armorStand.isVisible() && armorStand.isCustomNameVisible())) {

                            try {
                                armorStand.remove();
                                totalRemoved++;
                            } catch (Exception e) {
                                logger.warning("[HologramManager] Failed to remove hologram entity: " + e.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[HologramManager] Error force cleaning world " + world.getName() + ": " + e.getMessage());
            }
        }

        logger.info("[HologramManager] Force cleanup completed: removed " + totalRemoved + " hologram entities");
        totalOrphansRemoved += totalRemoved;
    }

    /**
     * Enhanced cleanup for shutdown
     */
    public static synchronized void cleanup() {
        logger.info("[HologramManager] Starting enhanced shutdown cleanup...");

        // Cancel all tasks
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (orphanScanTask != null) {
            orphanScanTask.cancel();
            orphanScanTask = null;
        }
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }

        // Force cleanup all holograms
        forceCleanupAll();

        // Reset system state
        systemInitialized = false;
        lastCleanupTime = 0;

        logger.info("[HologramManager] Enhanced shutdown cleanup completed");
        logger.info("[HologramManager] Final statistics - Created: " + totalHologramsCreated +
                ", Removed: " + totalHologramsRemoved + ", Orphans: " + totalOrphansRemoved);
    }

    // ================ UTILITY METHODS ================

    /**
     * Check if hologram exists and is valid
     */
    public static synchronized boolean hologramExists(String id) {
        if (id == null) return false;

        hologramLock.readLock().lock();
        try {
            Hologram hologram = holograms.get(id);
            return hologram != null && hologram.isValid();
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * Get hologram count
     */
    public static synchronized int getHologramCount() {
        hologramLock.readLock().lock();
        try {
            return holograms.size();
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * Get detailed statistics
     */
    public static synchronized String getDetailedStatistics() {
        hologramLock.readLock().lock();
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("=== Enhanced Hologram Manager Statistics ===\n");
            stats.append("Active Holograms: ").append(holograms.size()).append("\n");
            stats.append("Total Created: ").append(totalHologramsCreated).append("\n");
            stats.append("Total Removed: ").append(totalHologramsRemoved).append("\n");
            stats.append("Total Orphans Removed: ").append(totalOrphansRemoved).append("\n");
            stats.append("Tracked Entities: ").append(entityToHologramId.size()).append("\n");
            stats.append("Pending Removals: ").append(pendingRemovals.size()).append("\n");
            stats.append("Last Cleanup: ").append(new Date(lastCleanupTime)).append("\n");
            stats.append("System Initialized: ").append(systemInitialized).append("\n");
            stats.append("Session ID: ").append(sessionId).append("\n");

            // Hologram type breakdown
            Map<String, Integer> typeBreakdown = new HashMap<>();
            for (Hologram hologram : holograms.values()) {
                if (hologram != null) {
                    typeBreakdown.merge(hologram.getHologramType(), 1, Integer::sum);
                }
            }

            stats.append("\nHologram Types:\n");
            for (Map.Entry<String, Integer> entry : typeBreakdown.entrySet()) {
                stats.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

            return stats.toString();
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * Perform immediate health check and return results
     */
    public static synchronized String performImmediateHealthCheck() {
        hologramLock.readLock().lock();
        try {
            int totalHolograms = holograms.size();
            int validHolograms = 0;
            int invalidHolograms = 0;
            int healthyHolograms = 0;
            int unhealthyHolograms = 0;

            for (Hologram hologram : holograms.values()) {
                if (hologram != null) {
                    if (hologram.isValid()) {
                        validHolograms++;
                        if (hologram.validateArmorStands()) {
                            healthyHolograms++;
                        } else {
                            unhealthyHolograms++;
                        }
                    } else {
                        invalidHolograms++;
                    }
                }
            }

            return String.format("Health Check Results:\nTotal: %d | Valid: %d | Invalid: %d | Healthy: %d | Unhealthy: %d",
                    totalHolograms, validHolograms, invalidHolograms, healthyHolograms, unhealthyHolograms);

        } finally {
            hologramLock.readLock().unlock();
        }
    }

    // ================ LEGACY COMPATIBILITY ================

    /**
     * Legacy method - redirects to enhanced version
     */
    public static synchronized void createOrUpdateHologram(String id, Location location, List<String> lines, double lineSpacing) {
        updateHologramEfficiently(id, location, lines, lineSpacing, "legacy");
    }

    /**
     * Legacy method - redirects to enhanced version
     */
    public static synchronized void createOrUpdateHologram(String id, Location location, List<String> lines) {
        updateHologramEfficiently(id, location, lines, 0.25, "legacy");
    }

    /**
     * Legacy method - basic text update
     */
    public static synchronized void updateHologram(String id, List<String> newLines) {
        hologramLock.readLock().lock();
        try {
            Hologram hologram = holograms.get(id);
            if (hologram != null && hologram.isValid()) {
                hologram.updateTextOnly(newLines);
            }
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * Legacy method - basic move
     */
    public static synchronized boolean moveHologram(String id, Location newLocation) {
        hologramLock.readLock().lock();
        try {
            Hologram hologram = holograms.get(id);
            if (hologram != null && hologram.isValid()) {
                return hologram.moveToLocation(newLocation);
            }
            return false;
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * Get all hologram IDs
     */
    public static synchronized List<String> getAllHologramIds() {
        hologramLock.readLock().lock();
        try {
            return new ArrayList<>(holograms.keySet());
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * Maintenance method for external calls
     */
    public static synchronized void performMaintenance() {
        performPeriodicCleanup();
        performOrphanScan();
    }
}
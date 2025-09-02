package com.rednetty.server.core.mechanics.world.holograms;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 *  Hologram Manager with robust tracking and cleanup
 * Key Fixes:
 * - Eliminated duplicate hologram creation
 * - Improved mob-hologram binding
 * -  cleanup and orphan detection
 * - Better thread safety and state management
 * - Aggressive dead hologram removal
 */
public class HologramManager {

    // ================ CONSTANTS ================
    private static final long CLEANUP_INTERVAL = 100L; // 5 seconds - more frequent
    private static final long ORPHAN_SCAN_INTERVAL = 200L; // 10 seconds
    private static final long HEALTH_CHECK_INTERVAL = 60L; // 3 seconds - very frequent
    private static final double MOVEMENT_THRESHOLD = 0.01; // Tighter movement detection
    private static final long MAX_HOLOGRAM_AGE_MS = 300000L; // 5 minutes max age
    private static final String HOLOGRAM_METADATA_KEY = "yak_hologram_v2";
    private static final String SESSION_METADATA_KEY = "yak_session_v2";
    private static final String CREATION_TIME_KEY = "yak_created_v2";
    private static final String HOLOGRAM_TYPE_KEY = "yak_holo_type_v2";
    private static final String MOB_UUID_KEY = "yak_mob_uuid";
    private static final String HOLOGRAM_VERSION_KEY = "yak_holo_version";

    // ================ SYSTEM STATE ================
    private static final Logger logger = YakRealms.getInstance().getLogger();
    private static final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private static final Map<UUID, String> entityToHologramId = new ConcurrentHashMap<>();
    private static final Map<String, String> mobToHologramId = new ConcurrentHashMap<>(); // NEW: mob UUID -> hologram ID
    private static final Set<String> activeCreations = ConcurrentHashMap.newKeySet(); // NEW: prevent duplicates
    private static final Set<String> pendingRemovals = ConcurrentHashMap.newKeySet();
    private static final ReentrantReadWriteLock hologramLock = new ReentrantReadWriteLock();

    // ================ CLEANUP TASKS ================
    private static BukkitTask cleanupTask;
    private static BukkitTask orphanScanTask;
    private static BukkitTask healthCheckTask;
    private static BukkitTask emergencyCleanupTask; // NEW: emergency cleanup
    private static final AtomicBoolean systemInitialized = new AtomicBoolean(false);
    private static final AtomicLong sessionId = new AtomicLong(System.currentTimeMillis());
    private static final AtomicLong hologramVersion = new AtomicLong(1); // NEW: version tracking

    // ================ STATISTICS ================
    private static final AtomicLong totalHologramsCreated = new AtomicLong(0);
    private static final AtomicLong totalHologramsRemoved = new AtomicLong(0);
    private static final AtomicLong totalOrphansRemoved = new AtomicLong(0);
    private static final AtomicLong totalDuplicatesPrevented = new AtomicLong(0);
    private static volatile long lastCleanupTime = 0;

    /**
     *  Hologram class with strict mob binding and duplicate prevention
     */
    public static class Hologram {
        private final List<ArmorStand> armorStands = Collections.synchronizedList(new ArrayList<>());
        private volatile Location baseLocation;
        private final double lineSpacing;
        private final String hologramId;
        private final String mobUuid; // NEW: bound to specific mob
        private List<String> currentLines = Collections.synchronizedList(new ArrayList<>());
        private final long creationTime;
        private volatile long lastUpdateTime;
        private final AtomicBoolean isValid = new AtomicBoolean(true);
        private final AtomicBoolean isRemoving = new AtomicBoolean(false);
        private final AtomicBoolean isCreating = new AtomicBoolean(false); // NEW: creation lock
        private final String hologramType;
        private final AtomicLong version = new AtomicLong(hologramVersion.incrementAndGet());
        private volatile int failedUpdateCount = 0;
        private volatile long lastValidationTime = 0;

        public Hologram(Location baseLocation, List<String> lines, double lineSpacing, String hologramId, String type, String mobUuid) {
            this.baseLocation = baseLocation.clone();
            this.lineSpacing = lineSpacing;
            this.hologramId = hologramId;
            this.mobUuid = mobUuid;
            this.currentLines = Collections.synchronizedList(new ArrayList<>(lines));
            this.creationTime = System.currentTimeMillis();
            this.lastUpdateTime = creationTime;
            this.lastValidationTime = creationTime;
            this.hologramType = type != null ? type : "unknown";

            if (!systemInitialized.get()) {
                initializeCleanupSystem();
            }

            spawn(lines);
            totalHologramsCreated.incrementAndGet();
        }

        /**
         * Thread-safe movement with duplicate prevention
         */
        public boolean moveToLocation(Location newLocation) {
            if (isRemoving.get() || !isValid.get() || isCreating.get()) {
                return false;
            }

            if (newLocation == null || newLocation.getWorld() == null) {
                return false;
            }

            // Check if movement is significant enough
            if (baseLocation.getWorld().equals(newLocation.getWorld()) &&
                    baseLocation.distance(newLocation) < MOVEMENT_THRESHOLD) {
                return false;
            }

            synchronized (armorStands) {
                if (!validateArmorStands()) {
                    if (!attemptRecovery()) {
                        markInvalid();
                        return false;
                    }
                }

                try {
                    baseLocation = newLocation.clone();
                    lastUpdateTime = System.currentTimeMillis();

                    // Move all ArmorStands
                    List<ArmorStand> invalidStands = new ArrayList<>();
                    for (int i = 0; i < armorStands.size(); i++) {
                        ArmorStand armorStand = armorStands.get(i);
                        if (armorStand != null && !armorStand.isDead() && armorStand.isValid()) {
                            try {
                                Location standLocation = baseLocation.clone().subtract(0, lineSpacing * i, 0);
                                armorStand.teleport(standLocation);
                            } catch (Exception e) {
                                invalidStands.add(armorStand);
                            }
                        } else {
                            invalidStands.add(armorStand);
                        }
                    }

                    // Remove invalid ArmorStands
                    if (!invalidStands.isEmpty()) {
                        armorStands.removeAll(invalidStands);
                        for (ArmorStand invalid : invalidStands) {
                            if (invalid != null && !invalid.isDead()) {
                                try {
                                    entityToHologramId.remove(invalid.getUniqueId());
                                    invalid.remove();
                                } catch (Exception e) {
                                    // Silent cleanup
                                }
                            }
                        }

                        if (armorStands.isEmpty()) {
                            markInvalid();
                            return false;
                        }
                    }

                    failedUpdateCount = 0; // Reset on success
                    return true;

                } catch (Exception e) {
                    logger.warning("[HologramManager] Movement error for " + hologramId + ": " + e.getMessage());
                    failedUpdateCount++;
                    if (failedUpdateCount > 3) {
                        markInvalid();
                    }
                    return false;
                }
            }
        }

        /**
         * Thread-safe text update with validation
         */
        public boolean updateTextOnly(List<String> newLines) {
            if (isRemoving.get() || !isValid.get() || isCreating.get()) {
                return false;
            }

            if (newLines == null || newLines.equals(currentLines)) {
                return false; // No change needed
            }

            synchronized (armorStands) {
                if (!validateArmorStands()) {
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

                    // Update existing ArmorStands efficiently
                    List<ArmorStand> invalidStands = new ArrayList<>();
                    for (int i = 0; i < Math.min(newLines.size(), armorStands.size()); i++) {
                        ArmorStand armorStand = armorStands.get(i);
                        if (armorStand != null && !armorStand.isDead() && armorStand.isValid()) {
                            try {
                                String newLine = newLines.get(i);
                                String currentLine = i < currentLines.size() ? currentLines.get(i) : "";

                                // Only update if text actually changed
                                if (!newLine.equals(currentLine)) {
                                    armorStand.setCustomName(newLine);
                                }
                            } catch (Exception e) {
                                invalidStands.add(armorStand);
                            }
                        } else {
                            invalidStands.add(armorStand);
                        }
                    }

                    // Remove invalid ArmorStands
                    if (!invalidStands.isEmpty()) {
                        armorStands.removeAll(invalidStands);
                        for (ArmorStand invalid : invalidStands) {
                            if (invalid != null && !invalid.isDead()) {
                                try {
                                    entityToHologramId.remove(invalid.getUniqueId());
                                    invalid.remove();
                                } catch (Exception e) {
                                    // Silent cleanup
                                }
                            }
                        }

                        if (armorStands.isEmpty()) {
                            markInvalid();
                            return false;
                        }
                    }

                    // Update cache
                    currentLines.clear();
                    currentLines.addAll(newLines);
                    lastUpdateTime = System.currentTimeMillis();
                    failedUpdateCount = 0;
                    return true;

                } catch (Exception e) {
                    logger.warning("[HologramManager] Text update failed for " + hologramId + ": " + e.getMessage());
                    failedUpdateCount++;
                    if (failedUpdateCount > 3) {
                        markInvalid();
                    }
                    return false;
                }
            }
        }

        /**
         * Combined move and update with atomic operations
         */
        public boolean moveAndUpdate(Location newLocation, List<String> newLines) {
            if (isRemoving.get() || !isValid.get() || isCreating.get()) {
                return false;
            }

            boolean moved = false;
            boolean updated = false;

            synchronized (armorStands) {
                try {
                    if (!validateArmorStands()) {
                        if (!attemptRecovery()) {
                            markInvalid();
                            return false;
                        }
                    }

                    // Check if we actually need to do anything
                    boolean locationChanged = newLocation != null && newLocation.getWorld() != null &&
                            baseLocation.distance(newLocation) >= MOVEMENT_THRESHOLD;
                    boolean textChanged = newLines != null && !newLines.equals(currentLines);

                    if (!locationChanged && !textChanged) {
                        return false; // No changes needed
                    }

                    // Update text first if needed
                    if (textChanged) {
                        updated = updateTextOnly(newLines);
                    }

                    // Then handle movement if needed
                    if (locationChanged) {
                        moved = moveToLocation(newLocation);
                    }

                    if (moved || updated) {
                        lastUpdateTime = System.currentTimeMillis();
                        failedUpdateCount = 0; // Reset on success
                    }

                    return moved || updated;

                } catch (Exception e) {
                    logger.warning("[HologramManager] Combined update failed for " + hologramId + ": " + e.getMessage());
                    markInvalid();
                    return false;
                }
            }
        }

        /**
         *  validation with comprehensive checks
         */
        // In HologramManager.java (inside Hologram inner class)
        // In HologramManager.java (inside Hologram inner class), update validateArmorStands()
        public boolean validateArmorStands() {
            long currentTime = System.currentTimeMillis();

            // Don't validate too frequently
            if (currentTime - lastValidationTime < 1000) { // 1 second minimum between validations
                return !armorStands.isEmpty();
            }

            lastValidationTime = currentTime;

            synchronized (armorStands) {
                // Add mob existence check
                if (mobUuid != null) {
                    try {
                        UUID mobId = UUID.fromString(mobUuid);  // mobUuid is now entity UUID string
                        Entity mobEntity = Bukkit.getEntity(mobId);
                        if (mobEntity == null || mobEntity.isDead() || !mobEntity.isValid()) {
                            return false;  // Invalid if bound mob doesn't exist
                        }
                    } catch (Exception e) {
                        return false;  // Invalid UUID or error
                    }
                }

                if (armorStands.isEmpty()) {
                    return false;
                }

                int validCount = 0;
                List<ArmorStand> invalidStands = new ArrayList<>();

                for (ArmorStand armorStand : armorStands) {
                    if (armorStand != null && !armorStand.isDead() && armorStand.isValid()) {
                        // Check if it still has proper metadata
                        if (validateArmorStandMetadata(armorStand)) {
                            validCount++;
                        } else {
                            invalidStands.add(armorStand);
                        }
                    } else {
                        invalidStands.add(armorStand);
                    }
                }

                // Remove invalid stands immediately
                if (!invalidStands.isEmpty()) {
                    armorStands.removeAll(invalidStands);
                    for (ArmorStand invalid : invalidStands) {
                        if (invalid != null && !invalid.isDead()) {
                            try {
                                entityToHologramId.remove(invalid.getUniqueId());
                                invalid.remove();
                            } catch (Exception e) {
                                // Silent cleanup
                            }
                        }
                    }
                }

                // At least 70% of ArmorStands must be valid
                return validCount >= Math.max(1, (currentLines.size() * 0.7));
            }
        }

        /**
         * NEW: Validate ArmorStand metadata
         */
        private boolean validateArmorStandMetadata(ArmorStand armorStand) {
            try {
                // Check session ID
                if (!armorStand.hasMetadata(SESSION_METADATA_KEY)) {
                    return false;
                }

                long entitySessionId = armorStand.getMetadata(SESSION_METADATA_KEY).get(0).asLong();
                if (entitySessionId != sessionId.get()) {
                    return false;
                }

                // Check hologram ID
                if (!armorStand.hasMetadata(HOLOGRAM_METADATA_KEY)) {
                    return false;
                }

                String entityHologramId = armorStand.getMetadata(HOLOGRAM_METADATA_KEY).get(0).asString();
                if (!hologramId.equals(entityHologramId)) {
                    return false;
                }

                // Check version
                if (armorStand.hasMetadata(HOLOGRAM_VERSION_KEY)) {
                    long entityVersion = armorStand.getMetadata(HOLOGRAM_VERSION_KEY).get(0).asLong();
                    if (entityVersion != version.get()) {
                        return false;
                    }
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Improved recovery system with better error handling
         */
        public boolean attemptRecovery() {
            if (isRemoving.get() || isCreating.get()) {
                return false;
            }

            synchronized (armorStands) {
                try {
                    // Remove invalid ArmorStands
                    List<ArmorStand> validStands = new ArrayList<>();
                    for (ArmorStand armorStand : armorStands) {
                        if (armorStand != null && !armorStand.isDead() && armorStand.isValid()) {
                            if (validateArmorStandMetadata(armorStand)) {
                                validStands.add(armorStand);
                            } else {
                                // Clean up invalid stand
                                try {
                                    entityToHologramId.remove(armorStand.getUniqueId());
                                    armorStand.remove();
                                } catch (Exception e) {
                                    // Silent cleanup
                                }
                            }
                        }
                    }

                    armorStands.clear();
                    armorStands.addAll(validStands);

                    // If we lost too many ArmorStands, recreate them
                    if (armorStands.size() < currentLines.size() * 0.5 && !currentLines.isEmpty()) {
                        // Remove remaining stands and recreate all
                        for (ArmorStand stand : armorStands) {
                            if (stand != null && !stand.isDead()) {
                                try {
                                    entityToHologramId.remove(stand.getUniqueId());
                                    stand.remove();
                                } catch (Exception e) {
                                    // Silent cleanup
                                }
                            }
                        }
                        armorStands.clear();
                        spawn(currentLines);
                        return !armorStands.isEmpty();
                    }

                    return !armorStands.isEmpty();

                } catch (Exception e) {
                    logger.warning("[HologramManager] Recovery failed for " + hologramId + ": " + e.getMessage());
                    return false;
                }
            }
        }

        /**
         * Optimized spawning with better error handling and duplicate prevention
         */
        private void spawn(List<String> lines) {
            if (baseLocation == null || baseLocation.getWorld() == null) {
                markInvalid();
                return;
            }

            if (isRemoving.get() || !isCreating.compareAndSet(false, true)) {
                return; // Already creating or removing
            }

            try {
                // Schedule on main thread
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                    try {
                        Location spawnLocation = baseLocation.clone();
                        List<ArmorStand> newArmorStands = new ArrayList<>();

                        for (String line : lines) {
                            try {
                                // Ensure chunk is loaded
                                int chunkX = spawnLocation.getBlockX() >> 4;
                                int chunkZ = spawnLocation.getBlockZ() >> 4;
                                if (!spawnLocation.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                                    spawnLocation.getWorld().loadChunk(chunkX, chunkZ);
                                }

                                ArmorStand armorStand = (ArmorStand) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);

                                // Configure ArmorStand for optimal performance
                                armorStand.setVisible(false);
                                armorStand.setGravity(false);
                                armorStand.setMarker(true);
                                armorStand.setInvulnerable(true);
                                armorStand.setSmall(true);
                                armorStand.setCanPickupItems(false);
                                armorStand.setCollidable(false);
                                armorStand.setCustomName(line);
                                armorStand.setCustomNameVisible(true);

                                // Set comprehensive metadata for tracking
                                armorStand.setMetadata(SESSION_METADATA_KEY, new FixedMetadataValue(YakRealms.getInstance(), sessionId.get()));
                                armorStand.setMetadata(HOLOGRAM_METADATA_KEY, new FixedMetadataValue(YakRealms.getInstance(), hologramId));
                                armorStand.setMetadata(CREATION_TIME_KEY, new FixedMetadataValue(YakRealms.getInstance(), creationTime));
                                armorStand.setMetadata(HOLOGRAM_TYPE_KEY, new FixedMetadataValue(YakRealms.getInstance(), hologramType));
                                armorStand.setMetadata(HOLOGRAM_VERSION_KEY, new FixedMetadataValue(YakRealms.getInstance(), version.get()));

                                if (mobUuid != null) {
                                    armorStand.setMetadata(MOB_UUID_KEY, new FixedMetadataValue(YakRealms.getInstance(), mobUuid));
                                }

                                newArmorStands.add(armorStand);
                                entityToHologramId.put(armorStand.getUniqueId(), hologramId);

                            } catch (Exception e) {
                                logger.warning("[HologramManager] Failed to spawn ArmorStand for line '" + line + "': " + e.getMessage());
                            }

                            spawnLocation.subtract(0, lineSpacing, 0);
                        }

                        synchronized (armorStands) {
                            if (newArmorStands.isEmpty()) {
                                markInvalid();
                            } else {
                                armorStands.addAll(newArmorStands);
                            }
                        }

                    } catch (Exception e) {
                        logger.severe("[HologramManager] Critical error spawning hologram " + hologramId + ": " + e.getMessage());
                        markInvalid();
                    } finally {
                        isCreating.set(false);
                    }
                });

            } catch (Exception e) {
                logger.severe("[HologramManager] Failed to schedule hologram spawn: " + e.getMessage());
                isCreating.set(false);
                markInvalid();
            }
        }

        public void updateLines(List<String> newLines) {
            if (newLines == null || isRemoving.get() || isCreating.get()) {
                return;
            }

            try {
                remove();
                currentLines.clear();
                currentLines.addAll(newLines);
                if (isValid.get()) {
                    spawn(newLines);
                }
            } catch (Exception e) {
                logger.warning("[HologramManager] Failed to update lines for " + hologramId + ": " + e.getMessage());
                markInvalid();
            }
        }

        /**
         *  removal with comprehensive cleanup
         */
        public void remove() {
            if (!isRemoving.compareAndSet(false, true)) {
                return; // Already removing
            }

            isValid.set(false);

            try {
                synchronized (armorStands) {
                    List<ArmorStand> armorStandsCopy = new ArrayList<>(armorStands);
                    armorStands.clear();

                    for (ArmorStand armorStand : armorStandsCopy) {
                        if (armorStand != null && !armorStand.isDead()) {
                            try {
                                entityToHologramId.remove(armorStand.getUniqueId());
                                armorStand.remove();
                            } catch (Exception e) {
                                // Silent fail for individual removal
                            }
                        }
                    }
                }

                currentLines.clear();

                // Clean up tracking maps
                if (mobUuid != null) {
                    mobToHologramId.remove(mobUuid);
                }

                totalHologramsRemoved.incrementAndGet();

            } catch (Exception e) {
                logger.warning("[HologramManager] Error removing hologram " + hologramId + ": " + e.getMessage());
            }
        }

        private void markInvalid() {
            isValid.set(false);
            pendingRemovals.add(hologramId);
        }

        // ================ GETTERS ================
        public Location getBaseLocation() { return baseLocation != null ? baseLocation.clone() : null; }
        public int getLineCount() { synchronized (armorStands) { return armorStands.size(); } }
        public List<String> getCurrentLines() { return new ArrayList<>(currentLines); }
        public boolean isValid() { return isValid.get() && !isRemoving.get() && !armorStands.isEmpty(); }
        public long getCreationTime() { return creationTime; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public String getHologramId() { return hologramId; }
        public String getMobUuid() { return mobUuid; }
        public String getHologramType() { return hologramType; }
        public int getFailedUpdateCount() { return failedUpdateCount; }
        public long getAge() { return System.currentTimeMillis() - creationTime; }
        public long getVersion() { return version.get(); }
    }

    // ================ INITIALIZATION ================

    private static void initializeCleanupSystem() {
        if (!systemInitialized.compareAndSet(false, true)) {
            return;
        }

        // Generate new session ID
        sessionId.set(System.currentTimeMillis());
        hologramVersion.set(1);

        performInitialCleanup();

        // Start periodic cleanup task - more frequent
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

        // Start health check task - very frequent
        healthCheckTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                logger.severe("[HologramManager] Error in health check task: " + e.getMessage());
            }
        }, HEALTH_CHECK_INTERVAL, HEALTH_CHECK_INTERVAL);

        // NEW: Emergency cleanup task for dead holograms
        emergencyCleanupTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                performEmergencyCleanup();
            } catch (Exception e) {
                logger.severe("[HologramManager] Error in emergency cleanup task: " + e.getMessage());
            }
        }, 20L, 20L); // Every second

        logger.info("HologramManager enabled successfully");
    }

    // ================ CLEANUP OPERATIONS ================

    private static void performInitialCleanup() {
        int removedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            try {
                List<Entity> entities = new ArrayList<>(world.getEntitiesByClass(ArmorStand.class));
                for (Entity entity : entities) {
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        ArmorStand armorStand = (ArmorStand) entity;

                        boolean shouldRemove = false;

                        // Check for any hologram metadata
                        if (armorStand.hasMetadata(SESSION_METADATA_KEY) ||
                                armorStand.hasMetadata("yak_session") ||
                                armorStand.hasMetadata(HOLOGRAM_METADATA_KEY) ||
                                armorStand.hasMetadata("id") ||
                                armorStand.hasMetadata("hologramId")) {
                            shouldRemove = true;
                        }

                        // Check for invisible, non-colliding armor stands (likely holograms)
                        if (!armorStand.isVisible() && armorStand.isCustomNameVisible() && !armorStand.isCollidable()) {
                            shouldRemove = true;
                        }

                        if (shouldRemove) {
                            try {
                                armorStand.remove();
                                removedCount++;
                            } catch (Exception e) {
                                // Silent fail for cleanup
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

        totalOrphansRemoved.addAndGet(removedCount);
        lastCleanupTime = System.currentTimeMillis();
    }

    private static void performPeriodicCleanup() {
        hologramLock.writeLock().lock();
        try {
            List<String> toRemove = new ArrayList<>();
            int invalidCount = 0;
            int recoveredCount = 0;
            long currentTime = System.currentTimeMillis();

            for (Map.Entry<String, Hologram> entry : holograms.entrySet()) {
                String id = entry.getKey();
                Hologram hologram = entry.getValue();

                if (hologram == null) {
                    toRemove.add(id);
                    continue;
                }

                // Check age
                if (hologram.getAge() > MAX_HOLOGRAM_AGE_MS) {
                    toRemove.add(id);
                    invalidCount++;
                    continue;
                }

                // Check validity
                if (!hologram.isValid()) {
                    toRemove.add(id);
                    invalidCount++;
                    continue;
                }

                // Validate and attempt recovery
                if (!hologram.validateArmorStands()) {
                    if (hologram.attemptRecovery()) {
                        recoveredCount++;
                    } else {
                        toRemove.add(id);
                        invalidCount++;
                    }
                }
            }

            // Remove invalid holograms
            for (String id : toRemove) {
                Hologram hologram = holograms.remove(id);
                if (hologram != null) {
                    hologram.remove();
                }
                activeCreations.remove(id);
            }

            // Process pending removals
            for (String id : pendingRemovals) {
                Hologram hologram = holograms.remove(id);
                if (hologram != null) {
                    hologram.remove();
                }
                activeCreations.remove(id);
            }
            pendingRemovals.clear();

            lastCleanupTime = currentTime;

            if (invalidCount > 0 || recoveredCount > 0) {
                logger.info("[HologramManager] Cleanup: removed " + invalidCount +
                        " invalid holograms, recovered " + recoveredCount + " holograms");
            }

        } finally {
            hologramLock.writeLock().unlock();
        }
    }

    private static void performOrphanScan() {
        int orphansFound = 0;
        long currentSessionId = sessionId.get();

        for (World world : Bukkit.getWorlds()) {
            try {
                List<Entity> entities = new ArrayList<>(world.getEntitiesByClass(ArmorStand.class));
                for (Entity entity : entities) {
                    if (entity instanceof ArmorStand && !entity.isDead()) {
                        ArmorStand armorStand = (ArmorStand) entity;

                        boolean isOrphan = false;

                        // Check for hologram metadata
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

                            // Check if hologram exists
                            if (hologramId != null && !holograms.containsKey(hologramId)) {
                                isOrphan = true;
                            }

                            // Check session ID
                            if (armorStand.hasMetadata(SESSION_METADATA_KEY)) {
                                try {
                                    long entitySessionId = armorStand.getMetadata(SESSION_METADATA_KEY).get(0).asLong();
                                    if (entitySessionId != currentSessionId) {
                                        isOrphan = true;
                                    }
                                } catch (Exception e) {
                                    isOrphan = true;
                                }
                            } else {
                                isOrphan = true;
                            }

                            // Check if entity is tracked
                            if (!entityToHologramId.containsKey(armorStand.getUniqueId())) {
                                isOrphan = true;
                            }

                            // Check age
                            if (armorStand.hasMetadata(CREATION_TIME_KEY)) {
                                try {
                                    long creationTime = armorStand.getMetadata(CREATION_TIME_KEY).get(0).asLong();
                                    long age = System.currentTimeMillis() - creationTime;
                                    if (age > MAX_HOLOGRAM_AGE_MS) {
                                        isOrphan = true;
                                    }
                                } catch (Exception e) {
                                    isOrphan = true;
                                }
                            }

                            if (isOrphan) {
                                try {
                                    entityToHologramId.remove(armorStand.getUniqueId());
                                    armorStand.remove();
                                    orphansFound++;
                                } catch (Exception e) {
                                    // Silent fail for orphan removal
                                }
                            }
                        }
                        // NEW: Also check for unmarked holograms (invisible, non-colliding armor stands)
                        else if (!armorStand.isVisible() && armorStand.isCustomNameVisible() &&
                                !armorStand.isCollidable() && armorStand.isMarker()) {
                            // This looks like a hologram but has no metadata - likely orphaned
                            try {
                                armorStand.remove();
                                orphansFound++;
                            } catch (Exception e) {
                                // Silent fail
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
            totalOrphansRemoved.addAndGet(orphansFound);
        }
    }

    private static void performHealthCheck() {
        hologramLock.readLock().lock();
        try {
            int healthyCount = 0;
            int unhealthyCount = 0;
            List<String> toRemove = new ArrayList<>();

            for (Map.Entry<String, Hologram> entry : holograms.entrySet()) {
                Hologram hologram = entry.getValue();
                if (hologram != null && hologram.isValid()) {
                    if (hologram.validateArmorStands()) {
                        healthyCount++;
                    } else {
                        unhealthyCount++;
                        // Mark for removal if consistently unhealthy
                        if (hologram.getFailedUpdateCount() > 5) {
                            toRemove.add(entry.getKey());
                        }
                    }
                } else {
                    toRemove.add(entry.getKey());
                }
            }

            if (!toRemove.isEmpty()) {
                hologramLock.readLock().unlock();
                hologramLock.writeLock().lock();
                try {
                    for (String id : toRemove) {
                        Hologram hologram = holograms.remove(id);
                        if (hologram != null) {
                            hologram.remove();
                        }
                        activeCreations.remove(id);
                    }
                } finally {
                    hologramLock.readLock().lock();
                    hologramLock.writeLock().unlock();
                }
            }

            if (unhealthyCount > 0) {
                logger.fine("[HologramManager] Health check: " + healthyCount + " healthy, " +
                        unhealthyCount + " unhealthy holograms, removed " + toRemove.size());
            }

        } finally {
            hologramLock.readLock().unlock();
        }
    }

    /**
     * NEW: Emergency cleanup for completely dead holograms
     */
    private static void performEmergencyCleanup() {
        hologramLock.readLock().lock();
        try {
            List<String> emergencyRemove = new ArrayList<>();

            for (Map.Entry<String, Hologram> entry : holograms.entrySet()) {
                Hologram hologram = entry.getValue();
                if (hologram == null || (!hologram.isValid() && hologram.getFailedUpdateCount() > 10)) {
                    emergencyRemove.add(entry.getKey());
                }
            }

            if (!emergencyRemove.isEmpty()) {
                hologramLock.readLock().unlock();
                hologramLock.writeLock().lock();
                try {
                    for (String id : emergencyRemove) {
                        Hologram hologram = holograms.remove(id);
                        if (hologram != null) {
                            hologram.remove();
                        }
                        activeCreations.remove(id);
                    }

                    if (emergencyRemove.size() > 0) {
                        logger.info("[HologramManager] Emergency cleanup removed " + emergencyRemove.size() + " dead holograms");
                    }
                } finally {
                    hologramLock.readLock().lock();
                    hologramLock.writeLock().unlock();
                }
            }

        } finally {
            hologramLock.readLock().unlock();
        }
    }

    // ================ PUBLIC API ================

    /**
     *  update method with duplicate prevention and mob binding
     */
    // In HologramManager.java, update updateHologramEfficiently
    public static synchronized boolean updateHologramEfficiently(String id, Location location, List<String> lines, double lineSpacing, String mobUuid) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (lines == null || lines.isEmpty()) {
            return false;
        }

        // Prevent duplicate creation
        if (activeCreations.contains(id)) {
            totalDuplicatesPrevented.incrementAndGet();
            return false;
        }

        hologramLock.writeLock().lock();
        try {
            // Check for existing mob binding
            if (mobUuid != null && mobToHologramId.containsKey(mobUuid)) {
                String existingHologramId = mobToHologramId.get(mobUuid);
                if (!existingHologramId.equals(id)) {
                    // Remove old hologram for this mob
                    removeHologram(existingHologramId);
                }
            }

            Hologram existingHologram = holograms.get(id);

            if (existingHologram != null && existingHologram.isValid()) {
                // Try to update existing hologram
                boolean updated = existingHologram.moveAndUpdate(location, lines);
                if (updated) {
                    return true;
                }

                // If update failed, try recovery
                if (!existingHologram.attemptRecovery()) {
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

            // Add mob validity check before creating
            if (mobUuid != null) {
                try {
                    UUID mobId = UUID.fromString(mobUuid);  // mobUuid is now entity UUID string
                    Entity mobEntity = Bukkit.getEntity(mobId);
                    if (mobEntity == null || mobEntity.isDead() || !mobEntity.isValid()) {
                        removeHologramByMob(mobUuid);  // Clean up any stale
                        return false;  // Don't create for dead mob
                    }
                } catch (Exception e) {
                    removeHologramByMob(mobUuid);
                    return false;
                }
            }

            // Create new hologram
            try {
                activeCreations.add(id);
                Hologram hologram = new Hologram(location, lines, lineSpacing, id, "mob", mobUuid);
                holograms.put(id, hologram);

                // Bind mob to hologram
                if (mobUuid != null) {
                    mobToHologramId.put(mobUuid, id);
                }

                return true;
            } catch (Exception e) {
                logger.warning("[HologramManager] Failed to create hologram " + id + ": " + e.getMessage());
                return false;
            } finally {
                activeCreations.remove(id);
            }

        } finally {
            hologramLock.writeLock().unlock();
        }
    }

    public static synchronized boolean updateHologramEfficiently(String id, Location location, List<String> lines, double lineSpacing) {
        return updateHologramEfficiently(id, location, lines, lineSpacing, null);
    }

    public static synchronized boolean updateHologramEfficiently(String id, Location location, List<String> lines) {
        return updateHologramEfficiently(id, location, lines, 0.25, null);
    }

    public static synchronized void removeHologram(String id) {
        if (id == null) {
            return;
        }

        hologramLock.writeLock().lock();
        try {
            Hologram hologram = holograms.remove(id);
            if (hologram != null) {
                hologram.remove();
                // Clean up mob binding
                if (hologram.getMobUuid() != null) {
                    mobToHologramId.remove(hologram.getMobUuid());
                }
            }
            activeCreations.remove(id);
            pendingRemovals.remove(id);
        } finally {
            hologramLock.writeLock().unlock();
        }
    }

    /**
     * NEW: Remove hologram by mob UUID
     */
    public static synchronized void removeHologramByMob(String mobUuid) {
        if (mobUuid == null) {
            return;
        }

        String hologramId = mobToHologramId.get(mobUuid);
        if (hologramId != null) {
            removeHologram(hologramId);
        }
    }

    public static synchronized void forceCleanupAll() {
        hologramLock.writeLock().lock();
        try {
            List<String> hologramIds = new ArrayList<>(holograms.keySet());
            for (String id : hologramIds) {
                removeHologram(id);
            }

            entityToHologramId.clear();
            mobToHologramId.clear();
            activeCreations.clear();
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

                        // Remove any invisible, non-colliding armor stands (likely holograms)
                        if (!armorStand.isVisible() && !armorStand.isCollidable()) {
                            try {
                                armorStand.remove();
                                totalRemoved++;
                            } catch (Exception e) {
                                // Silent fail for force cleanup
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("[HologramManager] Error force cleaning world " + world.getName() + ": " + e.getMessage());
            }
        }

        // HologramManager cleanup completed
        totalOrphansRemoved.addAndGet(totalRemoved);
    }

    public static synchronized void cleanup() {
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
        if (emergencyCleanupTask != null) {
            emergencyCleanupTask.cancel();
            emergencyCleanupTask = null;
        }

        // Force cleanup all holograms
        forceCleanupAll();

        // Reset system state
        systemInitialized.set(false);
        lastCleanupTime = 0;

        logger.info("[HologramManager]  shutdown cleanup completed");
        logger.info("[HologramManager] Final statistics - Created: " + totalHologramsCreated.get() +
                ", Removed: " + totalHologramsRemoved.get() + ", Orphans: " + totalOrphansRemoved.get() +
                ", Duplicates Prevented: " + totalDuplicatesPrevented.get());
    }

    // ================ UTILITY METHODS ================

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

    public static synchronized int getHologramCount() {
        hologramLock.readLock().lock();
        try {
            return holograms.size();
        } finally {
            hologramLock.readLock().unlock();
        }
    }

    public static synchronized String getDetailedStatistics() {
        hologramLock.readLock().lock();
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("===  Hologram Manager Statistics ===\n");
            stats.append("Active Holograms: ").append(holograms.size()).append("\n");
            stats.append("Total Created: ").append(totalHologramsCreated.get()).append("\n");
            stats.append("Total Removed: ").append(totalHologramsRemoved.get()).append("\n");
            stats.append("Total Orphans Removed: ").append(totalOrphansRemoved.get()).append("\n");
            stats.append("Duplicates Prevented: ").append(totalDuplicatesPrevented.get()).append("\n");
            stats.append("Tracked Entities: ").append(entityToHologramId.size()).append("\n");
            stats.append("Mob Bindings: ").append(mobToHologramId.size()).append("\n");
            stats.append("Active Creations: ").append(activeCreations.size()).append("\n");
            stats.append("Pending Removals: ").append(pendingRemovals.size()).append("\n");
            stats.append("Last Cleanup: ").append(new Date(lastCleanupTime)).append("\n");
            stats.append("System Initialized: ").append(systemInitialized.get()).append("\n");
            stats.append("Session ID: ").append(sessionId.get()).append("\n");
            stats.append("Current Version: ").append(hologramVersion.get()).append("\n");

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

    // ================ LEGACY COMPATIBILITY ================

    public static synchronized void createOrUpdateHologram(String id, Location location, List<String> lines, double lineSpacing) {
        updateHologramEfficiently(id, location, lines, lineSpacing, null);
    }

    public static synchronized void createOrUpdateHologram(String id, Location location, List<String> lines) {
        updateHologramEfficiently(id, location, lines, 0.25, null);
    }

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

    public static synchronized void performMaintenance() {
        performPeriodicCleanup();
        performOrphanScan();
        performEmergencyCleanup();
    }
}
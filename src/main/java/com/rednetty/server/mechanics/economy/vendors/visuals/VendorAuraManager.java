package com.rednetty.server.mechanics.economy.vendors.visuals;

import com.rednetty.server.mechanics.economy.vendors.Vendor;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.mechanics.economy.vendors.visuals.animations.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Enhanced VendorAuraManager with improved performance, memory management, and error handling.
 * Manages vendor visual effects using Minecraft 1.20.2+ Display Entities with optimized
 * rendering, intelligent culling, and comprehensive resource cleanup.
 */
public class VendorAuraManager {
    private final JavaPlugin plugin;
    private final VendorManager vendorManager;
    private final NamespacedKey vendorEntityKey;
    private BukkitTask mainAuraTask;

    // Enhanced tracking with thread-safe collections
    private final Map<String, Set<Entity>> vendorEntities = new ConcurrentHashMap<>();
    private final Map<String, VendorAnimation> activeAnimations = new ConcurrentHashMap<>();
    private final Set<String> pendingRefresh = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastPlayerCheckTime = new ConcurrentHashMap<>();
    private final Map<String, Location> lastVendorLocation = new ConcurrentHashMap<>();

    // Performance tracking
    private final AtomicInteger totalDisplayEntities = new AtomicInteger(0);
    private final AtomicInteger activeVendorCount = new AtomicInteger(0);
    private final Map<String, Long> performanceMetrics = new ConcurrentHashMap<>();
    private long lastPerformanceLog = 0;

    // Configuration with improved defaults
    private int renderDistance = 48;
    private int maxDisplayEntitiesPerVendor = 12;
    private boolean enableParticles = true;
    private boolean enableDisplays = true;
    private boolean enableSounds = true;
    private int effectDensity = 2; // 1=low, 2=medium, 3=high
    private int updateFrequency = 1; // ticks between updates
    private boolean enablePerformanceOptimizations = true;
    private int playerCheckInterval = 40; // ticks between player proximity checks

    // Error tracking
    private final Set<String> problemVendors = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public VendorAuraManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
        loadConfiguration();
        plugin.getLogger().info("Enhanced VendorAuraManager initialized with performance optimizations");
    }

    /**
     * Enhanced configuration loading with validation
     */
    private void loadConfiguration() {
        try {
            renderDistance = Math.max(16, Math.min(128, plugin.getConfig().getInt("vendors.aura-render-distance", 48)));
            maxDisplayEntitiesPerVendor = Math.max(1, Math.min(20, plugin.getConfig().getInt("vendors.max-display-entities-per-vendor", 12)));
            enableParticles = plugin.getConfig().getBoolean("vendors.enable-particle-effects", true);
            enableDisplays = plugin.getConfig().getBoolean("vendors.enable-display-entities", true);
            enableSounds = plugin.getConfig().getBoolean("vendors.enable-sound-effects", true);
            effectDensity = Math.max(1, Math.min(3, plugin.getConfig().getInt("vendors.effect-density", 2)));
            updateFrequency = Math.max(1, Math.min(20, plugin.getConfig().getInt("vendors.update-frequency", 1)));
            enablePerformanceOptimizations = plugin.getConfig().getBoolean("vendors.performance-optimizations", true);
            playerCheckInterval = Math.max(20, plugin.getConfig().getInt("vendors.player-check-interval", 40));

            plugin.getLogger().info("Vendor aura configuration loaded - Render Distance: " + renderDistance +
                    ", Update Frequency: " + updateFrequency + ", Performance Optimizations: " + enablePerformanceOptimizations);
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading aura configuration, using defaults: " + e.getMessage());
        }
    }

    /**
     * Enhanced startup with staggered initialization to prevent lag spikes
     */
    public void startAllAuras() {
        stopAllAuras(); // Prevent duplicates

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (mainAuraTask != null) return;

            int vendorCount = vendorManager.getVendors().size();
            plugin.getLogger().info("Starting aura effects for " + vendorCount + " vendors with " +
                    (enablePerformanceOptimizations ? "performance optimizations" : "standard processing"));

            // Staggered initialization to prevent lag
            if (enablePerformanceOptimizations && vendorCount > 10) {
                startStaggeredInitialization();
            } else {
                startNormalUpdate();
            }
        }, 40); // 2-second delay after server start
    }

    /**
     * Staggered initialization for many vendors
     */
    private void startStaggeredInitialization() {
        List<String> vendorIds = new ArrayList<>(vendorManager.getVendors().keySet());
        AtomicInteger index = new AtomicInteger(0);

        BukkitTask initTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long startTime = System.currentTimeMillis();
            int processed = 0;

            // Process vendors in batches
            while (processed < 3 && index.get() < vendorIds.size()) {
                String vendorId = vendorIds.get(index.getAndIncrement());
                Vendor vendor = vendorManager.getVendor(vendorId);

                if (vendor != null) {
                    Location loc = getVendorLocation(vendor);
                    if (loc != null && hasPlayersNearby(loc, renderDistance)) {
                        try {
                            setupOrUpdateVendorAnimation(vendor, loc);
                            processed++;
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error initializing vendor " + vendorId + ": " + e.getMessage());
                            problemVendors.add(vendorId);
                        }
                    }
                }
            }

            // Start normal updates once initialization is complete
            if (index.get() >= vendorIds.size()) {
                startNormalUpdate();
                Bukkit.getScheduler().cancelTask(((BukkitTask) this).getTaskId());
            }

            recordPerformanceMetric("staggeredInit", System.currentTimeMillis() - startTime);
        }, 0, 10); // Every 0.5 seconds
    }

    /**
     * Normal update loop with optimizations
     */
    private void startNormalUpdate() {
        mainAuraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllAuras, 20, updateFrequency);
    }

    /**
     * Enhanced main update method with performance optimizations
     */
    private void updateAllAuras() {
        long startTime = System.currentTimeMillis();

        try {
            // Process pending refreshes first
            if (!pendingRefresh.isEmpty()) {
                processDisplayRefreshes();
            }

            // Performance optimization: limit concurrent processing
            int processedCount = 0;
            int maxProcessPerTick = enablePerformanceOptimizations ?
                    Math.max(3, vendorManager.getVendors().size() / 10) :
                    Integer.MAX_VALUE;

            Map<String, Vendor> vendors = vendorManager.getVendors();
            activeVendorCount.set(vendors.size());

            for (Vendor vendor : vendors.values()) {
                if (processedCount >= maxProcessPerTick) {
                    break; // Spread work across multiple ticks
                }

                String vendorId = vendor.getVendorId();

                // Skip problematic vendors temporarily
                if (problemVendors.contains(vendorId)) {
                    continue;
                }

                try {
                    Location loc = getVendorLocation(vendor);
                    if (loc == null) continue;

                    // Optimized player proximity check
                    if (!shouldCheckPlayers(vendorId) || !hasPlayersNearby(loc, renderDistance)) {
                        if (vendorEntities.containsKey(vendorId)) {
                            removeVendorDisplays(vendorId);
                        }
                        continue;
                    }

                    // Location change detection for optimization
                    Location lastLoc = lastVendorLocation.get(vendorId);
                    if (lastLoc != null && loc.distanceSquared(lastLoc) < 0.01) {
                        // Location hasn't changed significantly, update animations only
                        updateExistingAnimation(vendor, loc);
                    } else {
                        // Location changed or first time, full setup
                        setupOrUpdateVendorAnimation(vendor, loc);
                        lastVendorLocation.put(vendorId, loc.clone());
                    }

                    processedCount++;

                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing vendor " + vendorId + ": " + e.getMessage());
                    handleVendorError(vendorId);
                }
            }

            // Cleanup orphaned entities periodically
            if (System.currentTimeMillis() - lastPerformanceLog > 300000) { // Every 5 minutes
                cleanupOrphanedEntities();
                logPerformanceMetrics();
                lastPerformanceLog = System.currentTimeMillis();
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error in vendor aura system update: " + e.getMessage(), e);
        }

        recordPerformanceMetric("updateCycle", System.currentTimeMillis() - startTime);
    }

    /**
     * Optimized player proximity checking
     */
    private boolean shouldCheckPlayers(String vendorId) {
        if (!enablePerformanceOptimizations) return true;

        long lastCheck = lastPlayerCheckTime.getOrDefault(vendorId, 0L);
        long now = System.currentTimeMillis();

        if (now - lastCheck > (playerCheckInterval * 50)) { // Convert ticks to ms
            lastPlayerCheckTime.put(vendorId, now);
            return true;
        }

        return false;
    }

    /**
     * Update existing animation without recreation
     */
    private void updateExistingAnimation(Vendor vendor, Location loc) {
        String vendorId = vendor.getVendorId();
        VendorAnimation animation = activeAnimations.get(vendorId);
        Set<Entity> entities = vendorEntities.get(vendorId);

        if (animation != null && entities != null && !entities.isEmpty()) {
            // Update display animations
            if (enableDisplays) {
                animation.updateDisplayAnimations(entities, loc);
            }

            // Apply effects based on density
            if (enableParticles && animation.shouldApplyParticles(effectDensity)) {
                animation.applyParticleEffects(loc);
            }

            // Play sounds occasionally
            if (enableSounds && animation.shouldPlaySound()) {
                animation.playAmbientSound(loc);
            }

            // Process special effects
            animation.processSpecialEffects(loc);
        }
    }

    /**
     * Enhanced vendor animation setup with error handling
     */
    private void setupOrUpdateVendorAnimation(Vendor vendor, Location loc) {
        String vendorId = vendor.getVendorId();
        String vendorType = vendor.getVendorType().toLowerCase();

        try {
            // Create animation if needed
            VendorAnimation animation = activeAnimations.get(vendorId);
            if (animation == null) {
                animation = createAnimationForType(vendorType, vendor, loc);
                if (animation != null) {
                    activeAnimations.put(vendorId, animation);

                    // Create initial displays with entity limit
                    if (enableDisplays) {
                        Set<Entity> entities = animation.createDisplayEntities(loc, vendorEntityKey);
                        if (!entities.isEmpty()) {
                            // Limit entities per vendor for performance
                            if (entities.size() > maxDisplayEntitiesPerVendor) {
                                plugin.getLogger().warning("Vendor " + vendorId + " exceeded max display entities (" +
                                        entities.size() + " > " + maxDisplayEntitiesPerVendor + "), limiting...");

                                Set<Entity> limitedEntities = new HashSet<>();
                                int count = 0;
                                for (Entity entity : entities) {
                                    if (count++ >= maxDisplayEntitiesPerVendor) {
                                        entity.remove();
                                    } else {
                                        limitedEntities.add(entity);
                                    }
                                }
                                entities = limitedEntities;
                            }

                            vendorEntities.put(vendorId, entities);
                            totalDisplayEntities.addAndGet(entities.size());
                        }
                    }
                }
            }

            // Update existing animation
            updateExistingAnimation(vendor, loc);

            // Reset error tracking on success
            problemVendors.remove(vendorId);
            retryAttempts.remove(vendorId);

        } catch (Exception e) {
            plugin.getLogger().warning("Error setting up animation for vendor " + vendorId + ": " + e.getMessage());
            handleVendorError(vendorId);
        }
    }

    /**
     * Enhanced error handling for problematic vendors
     */
    private void handleVendorError(String vendorId) {
        int attempts = retryAttempts.getOrDefault(vendorId, 0) + 1;
        retryAttempts.put(vendorId, attempts);

        if (attempts >= MAX_RETRY_ATTEMPTS) {
            problemVendors.add(vendorId);
            plugin.getLogger().warning("Vendor " + vendorId + " marked as problematic after " + attempts + " failed attempts");

            // Cleanup any entities for this vendor
            removeVendorDisplays(vendorId);

            // Remove animation
            VendorAnimation animation = activeAnimations.remove(vendorId);
            if (animation != null) {
                try {
                    animation.cleanup();
                } catch (Exception e) {
                    plugin.getLogger().warning("Error cleaning up animation for vendor " + vendorId + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Enhanced animation factory with better error handling
     */
    private VendorAnimation createAnimationForType(String vendorType, Vendor vendor, Location loc) {
        try {
            AnimationOptions options = new AnimationOptions(
                    plugin,
                    vendor.getVendorId(),
                    enableParticles,
                    enableSounds,
                    effectDensity
            );

            switch (vendorType) {
                case "gambler":
                    return new GamblerAnimation(options);
                case "fisherman":
                    return new FishermanAnimation(options);
                case "book":
                    return new BookVendorAnimation(options);
                case "banker":
                    return new BankerAnimation(options);
                case "upgrade":
                    return new UpgradeVendorAnimation(options);
                case "medic":
                    return new MedicAnimation(options);
                case "item":
                case "shop":
                    return new ItemVendorAnimation(options);
                default:
                    return new DefaultVendorAnimation(options);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error creating animation for vendor type " + vendorType + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Enhanced display refresh processing
     */
    private void processDisplayRefreshes() {
        long startTime = System.currentTimeMillis();

        Set<String> toProcess = new HashSet<>(pendingRefresh);
        pendingRefresh.clear();

        for (String vendorId : toProcess) {
            try {
                Vendor vendor = vendorManager.getVendor(vendorId);
                if (vendor != null) {
                    // Remove existing displays
                    removeVendorDisplays(vendorId);

                    // Stop and cleanup the animation
                    VendorAnimation animation = activeAnimations.remove(vendorId);
                    if (animation != null) {
                        animation.cleanup();
                    }

                    // Reset error state
                    problemVendors.remove(vendorId);
                    retryAttempts.remove(vendorId);

                    // Location check
                    Location loc = getVendorLocation(vendor);
                    if (loc != null && hasPlayersNearby(loc, renderDistance)) {
                        setupOrUpdateVendorAnimation(vendor, loc);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error refreshing vendor " + vendorId + ": " + e.getMessage());
                handleVendorError(vendorId);
            }
        }

        recordPerformanceMetric("refreshDisplays", System.currentTimeMillis() - startTime);
    }

    /**
     * Enhanced cleanup with better error handling
     */
    public void stopAllAuras() {
        long startTime = System.currentTimeMillis();

        try {
            // Cancel the main task
            if (mainAuraTask != null) {
                mainAuraTask.cancel();
                mainAuraTask = null;
            }

            // Clean up animations with error handling
            for (Map.Entry<String, VendorAnimation> entry : activeAnimations.entrySet()) {
                try {
                    entry.getValue().cleanup();
                } catch (Exception e) {
                    plugin.getLogger().warning("Error cleaning up animation for " + entry.getKey() + ": " + e.getMessage());
                }
            }
            activeAnimations.clear();

            // Remove all display entities
            removeAllDisplayEntities();

            // Clear tracking data
            lastPlayerCheckTime.clear();
            lastVendorLocation.clear();
            problemVendors.clear();
            retryAttempts.clear();

            plugin.getLogger().info("Stopped all vendor aura effects in " +
                    (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            plugin.getLogger().warning("Error stopping aura effects: " + e.getMessage());
        }
    }

    /**
     * Enhanced display entity removal with cleanup validation
     */
    private void removeAllDisplayEntities() {
        int removedCount = 0;

        for (String vendorId : new ArrayList<>(vendorEntities.keySet())) {
            removedCount += removeVendorDisplays(vendorId);
        }

        vendorEntities.clear();
        totalDisplayEntities.set(0);

        if (removedCount > 0) {
            plugin.getLogger().info("Removed " + removedCount + " display entities");
        }
    }

    /**
     * Enhanced vendor display removal with count tracking
     */
    private int removeVendorDisplays(String vendorId) {
        Set<Entity> displays = vendorEntities.remove(vendorId);
        int removedCount = 0;

        if (displays != null) {
            for (Entity entity : new HashSet<>(displays)) {
                try {
                    if (entity.isValid()) {
                        entity.remove();
                        removedCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error removing entity for vendor " + vendorId + ": " + e.getMessage());
                }
            }
            totalDisplayEntities.addAndGet(-removedCount);
        }

        return removedCount;
    }

    /**
     * Cleanup orphaned entities that may have lost their metadata
     */
    private void cleanupOrphanedEntities() {
        try {
            int cleanedCount = 0;

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Display) {
                        String meta = entity.getPersistentDataContainer().get(
                                vendorEntityKey,
                                org.bukkit.persistence.PersistentDataType.STRING
                        );

                        if (meta != null && meta.contains(":")) {
                            String vendorId = meta.split(":")[0];
                            if (!vendorManager.getVendors().containsKey(vendorId)) {
                                entity.remove();
                                cleanedCount++;
                            }
                        }
                    }
                }
            }

            if (cleanedCount > 0) {
                plugin.getLogger().info("Cleaned up " + cleanedCount + " orphaned display entities");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error during orphaned entity cleanup: " + e.getMessage());
        }
    }

    /**
     * Enhanced vendor location retrieval with caching
     */
    private Location getVendorLocation(Vendor vendor) {
        try {
            // First try to get location from NPC if spawned
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            if (npc != null && npc.isSpawned()) {
                return npc.getEntity().getLocation();
            }
        } catch (Exception ignored) {}

        // Fall back to stored location
        Location loc = vendor.getLocation();
        return (loc != null && loc.getWorld() != null && isValidLocation(loc)) ? loc : null;
    }

    /**
     * Enhanced player proximity check with performance optimization
     */
    private boolean hasPlayersNearby(Location loc, double distance) {
        if (loc == null || loc.getWorld() == null) return false;

        World world = loc.getWorld();
        Collection<? extends Player> players = world.getPlayers();

        if (players.isEmpty()) return false;

        double distanceSquared = distance * distance;

        // Use stream for cleaner code and potential parallel processing
        return players.stream()
                .anyMatch(player -> player.getLocation().distanceSquared(loc) <= distanceSquared);
    }

    /**
     * Enhanced location validation
     */
    private boolean isValidLocation(Location loc) {
        return loc.getY() > -100 && loc.getY() < 1000 &&
                Math.abs(loc.getX()) < 30000000 &&
                Math.abs(loc.getZ()) < 30000000;
    }

    /**
     * Performance metrics recording
     */
    private void recordPerformanceMetric(String operation, long timeMs) {
        if (enablePerformanceOptimizations) {
            performanceMetrics.put(operation, timeMs);
        }
    }

    /**
     * Enhanced performance logging
     */
    private void logPerformanceMetrics() {
        if (performanceMetrics.isEmpty()) return;

        StringBuilder metrics = new StringBuilder("VendorAuraManager Performance Report:\n");

        performanceMetrics.forEach((operation, time) -> {
            metrics.append("  ").append(operation).append(": ").append(time).append("ms\n");
        });

        metrics.append("Status: ")
                .append("Active Vendors: ").append(activeVendorCount.get())
                .append(", Display Entities: ").append(totalDisplayEntities.get())
                .append(", Problem Vendors: ").append(problemVendors.size())
                .append(", Render Distance: ").append(renderDistance)
                .append(", Update Frequency: ").append(updateFrequency);

        plugin.getLogger().info(metrics.toString());
        performanceMetrics.clear();
    }

    /**
     * Enhanced statistics with performance data
     */
    public Map<String, Object> getAuraStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("activeVendorAuras", activeAnimations.size());
        stats.put("totalDisplayEntities", totalDisplayEntities.get());
        stats.put("activeVendorCount", activeVendorCount.get());
        stats.put("problemVendors", problemVendors.size());
        stats.put("pendingRefresh", pendingRefresh.size());
        stats.put("renderDistance", renderDistance);
        stats.put("maxDisplayEntitiesPerVendor", maxDisplayEntitiesPerVendor);
        stats.put("particlesEnabled", enableParticles);
        stats.put("displayEntitiesEnabled", enableDisplays);
        stats.put("soundsEnabled", enableSounds);
        stats.put("effectDensity", effectDensity);
        stats.put("updateFrequency", updateFrequency);
        stats.put("performanceOptimizations", enablePerformanceOptimizations);

        return stats;
    }

    // ================== ENHANCED PUBLIC API ==================

    public void updateVendorAura(Vendor vendor) {
        if (vendor != null) {
            pendingRefresh.add(vendor.getVendorId());
        }
    }

    public void retryProblematicVendor(String vendorId) {
        problemVendors.remove(vendorId);
        retryAttempts.remove(vendorId);
        Vendor vendor = vendorManager.getVendor(vendorId);
        if (vendor != null) {
            pendingRefresh.add(vendorId);
        }
    }

    public Set<String> getProblematicVendors() {
        return new HashSet<>(problemVendors);
    }

    // ================== ENHANCED CONFIGURATION SETTERS ==================

    public void setEffectDensity(int level) {
        if (level >= 1 && level <= 3) {
            this.effectDensity = level;
            plugin.getConfig().set("vendors.effect-density", level);
            plugin.saveConfig();
        }
    }

    public void setParticlesEnabled(boolean enabled) {
        this.enableParticles = enabled;
        plugin.getConfig().set("vendors.enable-particle-effects", enabled);
        plugin.saveConfig();
    }

    public void setDisplayEntitiesEnabled(boolean enabled) {
        this.enableDisplays = enabled;
        plugin.getConfig().set("vendors.enable-display-entities", enabled);
        plugin.saveConfig();

        if (!enabled) {
            removeAllDisplayEntities();
        } else {
            // Refresh all vendors to recreate displays
            pendingRefresh.addAll(activeAnimations.keySet());
        }
    }

    public void setRenderDistance(int distance) {
        if (distance > 0 && distance <= 128) {
            this.renderDistance = distance;
            plugin.getConfig().set("vendors.aura-render-distance", distance);
            plugin.saveConfig();
        }
    }

    public void setSoundsEnabled(boolean enabled) {
        this.enableSounds = enabled;
        plugin.getConfig().set("vendors.enable-sound-effects", enabled);
        plugin.saveConfig();
    }

    public void setUpdateFrequency(int frequency) {
        if (frequency >= 1 && frequency <= 20) {
            this.updateFrequency = frequency;
            plugin.getConfig().set("vendors.update-frequency", frequency);
            plugin.saveConfig();

            // Restart task with new frequency
            if (mainAuraTask != null) {
                mainAuraTask.cancel();
                startNormalUpdate();
            }
        }
    }

    public void setPerformanceOptimizations(boolean enabled) {
        this.enablePerformanceOptimizations = enabled;
        plugin.getConfig().set("vendors.performance-optimizations", enabled);
        plugin.saveConfig();
    }
}
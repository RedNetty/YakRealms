package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.visuals.VendorAuraManager;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import com.rednetty.server.mechanics.economy.vendors.config.VendorConfiguration;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Enhanced VendorManager with improved thread safety, error handling, and performance.
 * Manages the loading, saving, and runtime control of Vendors with comprehensive
 * validation, auto-repair, backup capabilities, and performance monitoring.
 */
public class VendorManager implements Listener {

    private static volatile VendorManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    private final JavaPlugin plugin;
    private final File configFile;
    private volatile FileConfiguration config;

    // Thread-safe vendor storage with read-write lock for performance
    private final Map<String, Vendor> vendors = new ConcurrentHashMap<>();
    private final ReadWriteLock vendorLock = new ReentrantReadWriteLock();

    private volatile boolean citizensAvailable = false;
    private volatile boolean isShuttingDown = false;

    // Configuration data
    private VendorConfiguration vendorConfig;

    // Enhanced metrics and debugging with atomic counters
    private final AtomicInteger vendorsLoaded = new AtomicInteger(0);
    private final AtomicInteger vendorsFixed = new AtomicInteger(0);
    private final AtomicInteger totalSaves = new AtomicInteger(0);
    private volatile long lastSaveTime = 0;
    private final List<String> recentErrors = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_RECENT_ERRORS = 50;

    // Performance monitoring
    private final Map<String, Long> operationTimings = new ConcurrentHashMap<>();
    private BukkitTask performanceMonitorTask;

    // Enhanced error recovery
    private final Set<String> failedVendors = ConcurrentHashMap.newKeySet();
    private BukkitTask retryTask;

    private VendorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "vendors.yml");
    }

    /**
     * Get the singleton VendorManager instance with thread-safe double-checked locking.
     */
    public static VendorManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            synchronized (INSTANCE_LOCK) {
                if (instance == null) {
                    instance = new VendorManager(plugin);
                }
            }
        }
        return instance;
    }

    /**
     * Check if Citizens API is available and functioning with enhanced validation
     */
    private boolean isCitizensAvailable() {
        try {
            Plugin citizensPlugin = Bukkit.getPluginManager().getPlugin("Citizens");
            if (citizensPlugin == null || !citizensPlugin.isEnabled()) {
                return false;
            }

            // Enhanced Citizens API validation
            CitizensAPI.getNPCRegistry();
            CitizensAPI.getTraitFactory();
            return true;
        } catch (Exception e) {
            if (isDebugMode()) {
                plugin.getLogger().warning("Citizens API validation failed: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Enhanced initialization with better error handling and performance monitoring
     */
    public void initialize() {
        long startTime = System.currentTimeMillis();

        try {
            // Load vendor configuration
            vendorConfig = VendorConfiguration.getInstance(YakRealms.getInstance());

            // Check Citizens availability
            citizensAvailable = isCitizensAvailable();

            if (!citizensAvailable) {
                plugin.getLogger().warning("Citizens API not available. Vendor NPCs will not be functional.");
            }

            loadConfigFile();

            if (citizensAvailable) {
                loadVendorsFromConfig();

                // Run auto validation if enabled
                if (vendorConfig.getBoolean("auto-fix-behaviors")) {
                    int issuesFixed = validateAndFixVendors();
                    if (issuesFixed > 0) {
                        plugin.getLogger().info("Auto-repaired " + issuesFixed + " vendor issues on startup");
                    }
                }
            } else {
                plugin.getLogger().info("Deferring vendor loading until Citizens is available");
                scheduleRetryTask();
            }

            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            new VendorInteractionHandler(plugin);

            // Start performance monitoring
            startPerformanceMonitoring();

            operationTimings.put("initialization", System.currentTimeMillis() - startTime);
            plugin.getLogger().info("VendorManager initialized successfully in " +
                    (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            plugin.getLogger().severe("Critical error during VendorManager initialization: " + e.getMessage());
            e.printStackTrace();
            addError("Initialization failed: " + e.getMessage());
        }
    }

    /**
     * Enhanced retry mechanism for Citizens availability
     */
    private void scheduleRetryTask() {
        if (retryTask != null) {
            retryTask.cancel();
        }

        retryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!citizensAvailable && isCitizensAvailable()) {
                citizensAvailable = true;
                plugin.getLogger().info("Citizens API now available. Loading vendors...");

                try {
                    loadVendorsFromConfig();

                    if (vendorConfig.getBoolean("auto-fix-behaviors")) {
                        int issuesFixed = validateAndFixVendors();
                        if (issuesFixed > 0) {
                            plugin.getLogger().info("Fixed " + issuesFixed + " vendor issues during retry initialization");
                        }
                    }

                    if (retryTask != null) {
                        retryTask.cancel();
                        retryTask = null;
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error during retry initialization: " + e.getMessage());
                    addError("Retry init error: " + e.getMessage());
                }
            }
        }, 100L, 100L); // Check every 5 seconds
    }

    /**
     * Performance monitoring for optimization
     */
    private void startPerformanceMonitoring() {
        if (performanceMonitorTask != null) {
            performanceMonitorTask.cancel();
        }

        performanceMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (isDebugMode()) {
                logPerformanceMetrics();
            }
            cleanupOldErrors();
        }, 6000L, 6000L); // Every 5 minutes
    }

    /**
     * Enhanced shutdown with proper cleanup
     */
    public void shutdown() {
        isShuttingDown = true;

        try {
            // Cancel all tasks
            if (performanceMonitorTask != null) {
                performanceMonitorTask.cancel();
                performanceMonitorTask = null;
            }

            if (retryTask != null) {
                retryTask.cancel();
                retryTask = null;
            }

            // Save vendors
            saveVendorsToConfig();

            // Clean up holograms
            HologramManager.cleanup();

            // Clear vendors with lock
            vendorLock.writeLock().lock();
            try {
                vendors.clear();
            } finally {
                vendorLock.writeLock().unlock();
            }

            plugin.getLogger().info("Vendor system shutdown complete");
        } catch (Exception e) {
            plugin.getLogger().severe("Error during vendor system shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enhanced reload with better error recovery
     */
    public void reload() {
        long startTime = System.currentTimeMillis();

        try {
            // Clear existing vendors
            vendorLock.writeLock().lock();
            try {
                vendors.clear();
                failedVendors.clear();
            } finally {
                vendorLock.writeLock().unlock();
            }

            loadConfigFile();
            loadVendorsFromConfig();

            // Validate vendors after loading
            if (vendorConfig.getBoolean("auto-fix-behaviors")) {
                int issuesFixed = validateAndFixVendors();
                if (issuesFixed > 0) {
                    plugin.getLogger().info("Fixed " + issuesFixed + " vendor issues during reload");
                }
            }

            // Restart aura effects
            VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
            if (auraManager != null) {
                auraManager.stopAllAuras();
                auraManager.startAllAuras();
            }

            operationTimings.put("reload", System.currentTimeMillis() - startTime);
            plugin.getLogger().info("Vendor system reloaded successfully in " +
                    (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading vendor system: " + e.getMessage());
            e.printStackTrace();
            addError("Reload error: " + e.getMessage());
        }
    }

    // ================== ENHANCED CITIZENS EVENTS ==================

    @EventHandler
    public void onNPCSpawn(NPCSpawnEvent event) {
        if (!citizensAvailable || isShuttingDown) return;

        try {
            NPC npc = event.getNPC();
            Vendor vendor = getVendorByNpcId(npc.getId());

            if (vendor != null) {
                // Remove from failed vendors set
                failedVendors.remove(vendor.getVendorId());

                // Update vendor location
                vendor.setLocation(npc.getStoredLocation());

                // Create/update hologram
                createOrUpdateHologram(vendor);

                // Restart aura if needed
                VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
                if (auraManager != null) {
                    auraManager.updateVendorAura(vendor);
                }

                if (isDebugMode()) {
                    plugin.getLogger().info("NPC spawned for vendor " + vendor.getVendorId());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in NPC spawn event: " + e.getMessage());
            addError("NPC spawn error: " + e.getMessage());
        }
    }

    @EventHandler
    public void onNPCRemove(NPCRemoveEvent event) {
        if (!citizensAvailable || isShuttingDown) return;

        try {
            NPC npc = event.getNPC();
            Vendor vendor = getVendorByNpcId(npc.getId());

            if (vendor != null) {
                HologramManager.removeHologram(vendor.getVendorId());

                if (isDebugMode()) {
                    plugin.getLogger().info("NPC removed for vendor " + vendor.getVendorId());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in NPC remove event: " + e.getMessage());
            addError("NPC remove error: " + e.getMessage());
        }
    }

    // ================== ENHANCED HOLOGRAM UTILS ==================

    private void createOrUpdateHologram(Vendor vendor) {
        if (!citizensAvailable || isShuttingDown) return;

        try {
            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            Location hologramLocation = null;

            if (npc != null && npc.isSpawned()) {
                double hologramHeight = vendorConfig.getDouble("hologram-height");
                if (hologramHeight <= 0) hologramHeight = 2.8;
                hologramLocation = npc.getStoredLocation().clone().add(0, hologramHeight, 0);
            } else {
                // Fallback to stored location
                Location fallback = vendor.getLocation();
                if (fallback != null) {
                    double hologramHeight = vendorConfig.getDouble("hologram-height");
                    if (hologramHeight <= 0) hologramHeight = 2.8;
                    hologramLocation = fallback.clone().add(0, hologramHeight, 0);
                }
            }

            if (hologramLocation != null) {
                double lineSpacing = vendorConfig.getDouble("hologram-line-spacing");
                if (lineSpacing <= 0) lineSpacing = 0.3;

                HologramManager.createOrUpdateHologram(
                        vendor.getVendorId(),
                        hologramLocation,
                        vendor.getHologramLines(),
                        lineSpacing
                );
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create/update hologram for vendor: " + vendor.getVendorId() + " - " + e.getMessage());
            addError("Hologram error for " + vendor.getVendorId() + ": " + e.getMessage());
            failedVendors.add(vendor.getVendorId());
        }
    }

    // ================== ENHANCED PUBLIC API ==================

    /**
     * Thread-safe vendor registration
     */
    public void registerVendor(Vendor vendor) {
        if (vendor == null || !vendor.isValid()) {
            throw new IllegalArgumentException("Cannot register invalid vendor");
        }

        vendorLock.writeLock().lock();
        try {
            vendors.put(vendor.getVendorId(), vendor);
            failedVendors.remove(vendor.getVendorId());
        } finally {
            vendorLock.writeLock().unlock();
        }
    }

    /**
     * Enhanced vendor creation with better error handling
     */
    public Vendor createVendor(String vendorId,
                               String worldName,
                               double x, double y, double z,
                               float yaw, float pitch,
                               List<String> hologramLines,
                               String behaviorClass) {

        long startTime = System.currentTimeMillis();

        try {
            if (!citizensAvailable) {
                plugin.getLogger().warning("Cannot create vendor: Citizens API not available");
                return null;
            }

            // Check for existing vendor
            vendorLock.readLock().lock();
            try {
                if (vendors.containsKey(vendorId)) {
                    throw new IllegalArgumentException("A vendor with ID '" + vendorId + "' already exists!");
                }
            } finally {
                vendorLock.readLock().unlock();
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new IllegalArgumentException("World not found: " + worldName);
            }

            // Validate behavior class
            if (behaviorClass == null || behaviorClass.isEmpty()) {
                behaviorClass = vendorConfig.getString("default-behavior-class");
                if (behaviorClass.isEmpty()) {
                    behaviorClass = "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior";
                }
            }

            // Use default hologram lines if none provided
            if (hologramLines == null || hologramLines.isEmpty()) {
                hologramLines = vendorConfig.getStringList("default-hologram-text").stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
            }

            // Spawn Citizens NPC
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                    org.bukkit.entity.EntityType.PLAYER,
                    vendorId
            );
            Location loc = new Location(world, x, y, z, yaw, pitch);
            npc.spawn(loc);

            int npcId = npc.getId();
            Vendor vendor = new Vendor(vendorId, npcId, loc, hologramLines, behaviorClass);

            // Register vendor
            registerVendor(vendor);

            // Create hologram immediately
            createOrUpdateHologram(vendor);

            // Save to config
            saveVendorsToConfig();

            operationTimings.put("createVendor", System.currentTimeMillis() - startTime);
            return vendor;

        } catch (Exception e) {
            plugin.getLogger().severe("Error creating vendor " + vendorId + ": " + e.getMessage());
            e.printStackTrace();
            addError("Create vendor error: " + e.getMessage());
            failedVendors.add(vendorId);
            return null;
        }
    }

    /**
     * Enhanced vendor deletion with proper cleanup
     */
    public boolean deleteVendor(String vendorId) {
        long startTime = System.currentTimeMillis();

        try {
            // Stop aura effects first
            VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();

            Vendor vendor;
            vendorLock.writeLock().lock();
            try {
                vendor = vendors.remove(vendorId);
                failedVendors.remove(vendorId);
            } finally {
                vendorLock.writeLock().unlock();
            }

            if (vendor == null) {
                return false;
            }

            // Remove hologram
            HologramManager.removeHologram(vendorId);

            // Remove from Citizens if available
            if (citizensAvailable) {
                try {
                    NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
                    if (npc != null) {
                        npc.despawn();
                        npc.destroy();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error removing Citizens NPC for vendor " + vendorId + ": " + e.getMessage());
                    addError("Delete vendor NPC error: " + e.getMessage());
                }
            }

            // Save changes
            saveVendorsToConfig();

            operationTimings.put("deleteVendor", System.currentTimeMillis() - startTime);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error deleting vendor " + vendorId + ": " + e.getMessage());
            addError("Delete vendor error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Enhanced vendor behavior update
     */
    public void updateVendorBehavior(String vendorId, String behaviorClass) {
        try {
            Vendor vendor = getVendor(vendorId);
            if (vendor == null) {
                return;
            }

            String oldType = vendor.getVendorType();
            vendor.setBehaviorClass(behaviorClass);

            // Check if type changed
            if (!oldType.equals(vendor.getVendorType())) {
                VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
                if (auraManager != null) {
                    auraManager.updateVendorAura(vendor);
                }
            }

            saveVendorsToConfig();

        } catch (Exception e) {
            plugin.getLogger().warning("Error updating vendor behavior for " + vendorId + ": " + e.getMessage());
            addError("Update behavior error: " + e.getMessage());
        }
    }

    /**
     * Thread-safe vendor retrieval
     */
    public Vendor getVendor(String vendorId) {
        vendorLock.readLock().lock();
        try {
            return vendors.get(vendorId);
        } finally {
            vendorLock.readLock().unlock();
        }
    }

    /**
     * Thread-safe vendor retrieval by NPC ID
     */
    public Vendor getVendorByNpcId(int npcId) {
        vendorLock.readLock().lock();
        try {
            for (Vendor v : vendors.values()) {
                if (v.getNpcId() == npcId) return v;
            }
            return null;
        } finally {
            vendorLock.readLock().unlock();
        }
    }

    /**
     * Thread-safe vendors access
     */
    public Map<String, Vendor> getVendors() {
        vendorLock.readLock().lock();
        try {
            return new HashMap<>(vendors);
        } finally {
            vendorLock.readLock().unlock();
        }
    }

    /**
     * Enhanced validation with better error tracking
     */
    public int validateAndFixVendors() {
        long startTime = System.currentTimeMillis();
        int issuesFixed = 0;

        vendorLock.readLock().lock();
        Map<String, Vendor> vendorsCopy;
        try {
            vendorsCopy = new HashMap<>(vendors);
        } finally {
            vendorLock.readLock().unlock();
        }

        for (Map.Entry<String, Vendor> entry : vendorsCopy.entrySet()) {
            Vendor vendor = entry.getValue();
            boolean modified = false;

            try {
                // Check behavior class
                String behaviorClass = vendor.getBehaviorClass();
                if (behaviorClass == null || behaviorClass.isEmpty()) {
                    String vendorType = vendor.getVendorType();
                    String newBehaviorClass = getDefaultBehaviorForType(vendorType);
                    vendor.setBehaviorClass(newBehaviorClass);
                    plugin.getLogger().warning("Fixed missing behavior class for vendor " + vendor.getVendorId() +
                            ". Set to " + newBehaviorClass);
                    modified = true;
                    issuesFixed++;
                } else {
                    // Verify behavior class exists
                    try {
                        Class.forName(behaviorClass);
                    } catch (ClassNotFoundException e) {
                        String vendorType = vendor.getVendorType();
                        String newBehaviorClass = getDefaultBehaviorForType(vendorType);
                        vendor.setBehaviorClass(newBehaviorClass);
                        plugin.getLogger().warning("Fixed invalid behavior class for vendor " + vendor.getVendorId() +
                                ". Changed from " + behaviorClass + " to " + newBehaviorClass);
                        modified = true;
                        issuesFixed++;
                    }
                }

                // Check hologram lines
                if (vendor.getHologramLines() == null || vendor.getHologramLines().isEmpty()) {
                    List<String> defaultLines = vendorConfig.getStringList("default-hologram-text").stream()
                            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                            .collect(Collectors.toList());

                    vendor.setHologramLines(defaultLines);
                    plugin.getLogger().warning("Fixed missing hologram lines for vendor " + vendor.getVendorId());
                    modified = true;
                    issuesFixed++;
                }

                if (modified) {
                    failedVendors.remove(vendor.getVendorId());
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error validating vendor " + vendor.getVendorId() + ": " + e.getMessage());
                addError("Validation error for " + vendor.getVendorId() + ": " + e.getMessage());
                failedVendors.add(vendor.getVendorId());
            }
        }

        if (issuesFixed > 0) {
            saveVendorsToConfig();
            vendorsFixed.addAndGet(issuesFixed);
        }

        operationTimings.put("validateAndFix", System.currentTimeMillis() - startTime);
        return issuesFixed;
    }

    /**
     * Enhanced configuration save with atomic operations
     */
    public void saveVendorsToConfig() {
        if (isShuttingDown) return;

        long startTime = System.currentTimeMillis();

        try {
            config.set("vendors", null); // Clear old data

            vendorLock.readLock().lock();
            try {
                for (Vendor vendor : vendors.values()) {
                    String path = "vendors." + vendor.getVendorId();
                    config.set(path + ".npcId", vendor.getNpcId());

                    Location loc = vendor.getLocation();
                    if (loc != null && loc.getWorld() != null) {
                        config.set(path + ".world", loc.getWorld().getName());
                        config.set(path + ".x", loc.getX());
                        config.set(path + ".y", loc.getY());
                        config.set(path + ".z", loc.getZ());
                        config.set(path + ".yaw", loc.getYaw());
                        config.set(path + ".pitch", loc.getPitch());
                    }
                    config.set(path + ".lines", vendor.getHologramLines());
                    config.set(path + ".behaviorClass", vendor.getBehaviorClass());
                    config.set(path + ".lastUpdated", vendor.getLastUpdated());
                }
            } finally {
                vendorLock.readLock().unlock();
            }

            config.save(configFile);
            totalSaves.incrementAndGet();
            lastSaveTime = System.currentTimeMillis();

            operationTimings.put("saveConfig", System.currentTimeMillis() - startTime);

            if (isDebugMode()) {
                plugin.getLogger().info("Saved " + vendors.size() + " vendors to config in " +
                        (System.currentTimeMillis() - startTime) + "ms");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save vendors.yml: " + e.getMessage());
            addError("Save config error: " + e.getMessage());
        }
    }

    // ================== ENHANCED UTILITY METHODS ==================

    /**
     * Enhanced error tracking with size limits
     */
    private void addError(String error) {
        recentErrors.add(new SimpleDateFormat("HH:mm:ss").format(new Date()) + " - " + error);

        // Limit error list size
        while (recentErrors.size() > MAX_RECENT_ERRORS) {
            recentErrors.remove(0);
        }
    }

    /**
     * Clean up old errors periodically
     */
    private void cleanupOldErrors() {
        if (recentErrors.size() > MAX_RECENT_ERRORS / 2) {
            synchronized (recentErrors) {
                while (recentErrors.size() > MAX_RECENT_ERRORS / 4) {
                    recentErrors.remove(0);
                }
            }
        }
    }

    /**
     * Performance metrics logging
     */
    private void logPerformanceMetrics() {
        if (operationTimings.isEmpty()) return;

        StringBuilder metrics = new StringBuilder("VendorManager Performance Metrics:\n");
        operationTimings.forEach((operation, time) -> {
            metrics.append("  ").append(operation).append(": ").append(time).append("ms\n");
        });

        metrics.append("Vendors: ").append(vendors.size())
                .append(", Failed: ").append(failedVendors.size())
                .append(", Errors: ").append(recentErrors.size());

        plugin.getLogger().info(metrics.toString());

        // Clear old timings
        operationTimings.clear();
    }

    /**
     * Enhanced metrics with performance data
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        vendorLock.readLock().lock();
        try {
            metrics.put("vendorsCount", vendors.size());
        } finally {
            vendorLock.readLock().unlock();
        }

        metrics.put("vendorsLoaded", vendorsLoaded.get());
        metrics.put("vendorsFixed", vendorsFixed.get());
        metrics.put("totalSaves", totalSaves.get());
        metrics.put("lastSaveTime", lastSaveTime);
        metrics.put("errorCount", recentErrors.size());
        metrics.put("citizensAvailable", citizensAvailable);
        metrics.put("failedVendorsCount", failedVendors.size());
        metrics.put("isShuttingDown", isShuttingDown);

        // Get aura stats if available
        VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
        if (auraManager != null) {
            metrics.putAll(auraManager.getAuraStats());
        }

        return metrics;
    }

    public List<String> getRecentErrors() {
        synchronized (recentErrors) {
            return new ArrayList<>(recentErrors);
        }
    }

    public void clearRecentErrors() {
        recentErrors.clear();
    }

    public Set<String> getFailedVendors() {
        return new HashSet<>(failedVendors);
    }

    // ================== ENHANCED INTERNALS ==================

    private void loadConfigFile() {
        if (!configFile.exists()) {
            try {
                plugin.saveResource("vendors.yml", false);
            } catch (Exception e) {
                try {
                    plugin.getDataFolder().mkdirs();
                    configFile.createNewFile();
                } catch (IOException ex) {
                    plugin.getLogger().severe("Failed to create vendors.yml: " + ex.getMessage());
                    addError("Config creation error: " + ex.getMessage());
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Enhanced vendor loading with better error recovery
     */
    private void loadVendorsFromConfig() {
        if (!citizensAvailable) {
            plugin.getLogger().warning("Skipping vendor loading because Citizens is not available");
            return;
        }

        if (!config.contains("vendors")) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int loadedCount = 0;

        try {
            Set<String> vendorIds = config.getConfigurationSection("vendors").getKeys(false);

            for (String vendorId : vendorIds) {
                try {
                    if (loadSingleVendor(vendorId)) {
                        loadedCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load vendor " + vendorId + ": " + e.getMessage());
                    addError("Load vendor error for " + vendorId + ": " + e.getMessage());
                    failedVendors.add(vendorId);
                }
            }

            vendorsLoaded.set(loadedCount);
            operationTimings.put("loadVendors", System.currentTimeMillis() - startTime);
            plugin.getLogger().info("Loaded " + loadedCount + " vendors from config in " +
                    (System.currentTimeMillis() - startTime) + "ms");

        } catch (Exception e) {
            plugin.getLogger().severe("Error loading vendors from config: " + e.getMessage());
            addError("Vendor loading error: " + e.getMessage());
        }
    }

    /**
     * Load a single vendor with enhanced error handling
     */
    private boolean loadSingleVendor(String vendorId) {
        String path = "vendors." + vendorId;

        int npcId = config.getInt(path + ".npcId", -1);
        String worldName = config.getString(path + ".world", "world");
        double x = config.getDouble(path + ".x", 0.0);
        double y = config.getDouble(path + ".y", 64.0);
        double z = config.getDouble(path + ".z", 0.0);
        float yaw = (float) config.getDouble(path + ".yaw", 0.0);
        float pitch = (float) config.getDouble(path + ".pitch", 0.0);
        List<String> lines = config.getStringList(path + ".lines");
        String behaviorClass = config.getString(path + ".behaviorClass");

        if (behaviorClass == null || behaviorClass.isEmpty()) {
            plugin.getLogger().warning("No behavior class found for vendor " + vendorId + ". Using default ShopBehavior.");
            behaviorClass = "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior";
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (!Bukkit.getWorlds().isEmpty()) {
                world = Bukkit.getWorlds().get(0);
                plugin.getLogger().warning("World '" + worldName + "' not found for vendor " + vendorId +
                        ". Using " + world.getName() + " instead.");
            } else {
                plugin.getLogger().warning("No valid world for vendor " + vendorId + ". Skipping.");
                return false;
            }
        }

        Location loc = new Location(world, x, y, z, yaw, pitch);
        Vendor vendor = new Vendor(vendorId, npcId, loc, lines, behaviorClass);

        vendorLock.writeLock().lock();
        try {
            vendors.put(vendorId, vendor);
        } finally {
            vendorLock.writeLock().unlock();
        }

        // Spawn NPC if needed
        try {
            NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null && !npc.isSpawned()) {
                npc.spawn(loc);
            }
            if (npc != null && npc.isSpawned()) {
                createOrUpdateHologram(vendor);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error spawning NPC for vendor: " + vendorId + " - " + e.getMessage());
            addError("NPC spawn error for " + vendorId + ": " + e.getMessage());
            failedVendors.add(vendorId);
        }

        return true;
    }

    // ================== HELPER METHODS ==================

    private boolean isDebugMode() {
        return vendorConfig != null && vendorConfig.getBoolean("debug-mode");
    }

    public String getDefaultBehaviorForType(String vendorType) {
        String basePath = "com.rednetty.server.mechanics.economy.vendors.behaviors.";

        switch (vendorType.toLowerCase()) {
            case "item":
                return basePath + "ItemVendorBehavior";
            case "fisherman":
                return basePath + "FishermanBehavior";
            case "book":
                return basePath + "BookVendorBehavior";
            case "upgrade":
                return basePath + "UpgradeVendorBehavior";
            case "banker":
                return basePath + "BankerBehavior";
            case "medic":
                return basePath + "MedicBehavior";
            case "gambler":
                return basePath + "GamblerBehavior";
            default:
                return basePath + "ShopBehavior";
        }
    }
}
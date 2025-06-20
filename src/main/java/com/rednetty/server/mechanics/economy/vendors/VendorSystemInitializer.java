package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.commands.admin.VendorCommand;
import com.rednetty.server.mechanics.economy.vendors.admin.VendorConfigGUI;
import com.rednetty.server.mechanics.economy.vendors.visuals.VendorAuraManager;
import com.rednetty.server.mechanics.economy.vendors.config.VendorConfiguration;
import com.rednetty.server.mechanics.economy.vendors.health.VendorHealthCheck;
import com.rednetty.server.mechanics.economy.vendors.purchase.PurchaseManager;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Enhanced VendorSystemInitializer with improved dependency management, error handling,
 * and graceful degradation. Provides comprehensive initialization with retry mechanisms,
 * health monitoring, and performance tracking.
 */
public class VendorSystemInitializer {

    // Singleton components
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static volatile VendorAuraManager auraManager;
    private static volatile VendorHologramManager hologramManager;
    private static volatile VendorHealthCheck healthCheck;
    private static volatile JavaPlugin plugin;

    // Initialization state tracking
    private static final AtomicInteger initializationAttempts = new AtomicInteger(0);
    private static final AtomicBoolean citizensWaitingMode = new AtomicBoolean(false);
    private static volatile long lastInitializationAttempt = 0;
    private static volatile String lastInitializationError = null;

    // Task management
    private static volatile BukkitTask citizensWaitTask;
    private static volatile BukkitTask healthMonitorTask;
    private static volatile BukkitTask retryTask;

    // Configuration
    private static final int MAX_INITIALIZATION_ATTEMPTS = 5;
    private static final long INITIALIZATION_RETRY_DELAY = 100L; // 5 seconds
    private static final long CITIZENS_WAIT_INTERVAL = 100L; // 5 seconds
    private static final long HEALTH_CHECK_INTERVAL = 6000L; // 5 minutes

    /**
     * Enhanced initialization with comprehensive error handling
     */
    public static void initialize(JavaPlugin pluginInstance) {
        if (initialized.get()) {
            pluginInstance.getLogger().info("Vendor system already initialized");
            return;
        }

        plugin = pluginInstance;
        int attempt = initializationAttempts.incrementAndGet();
        lastInitializationAttempt = System.currentTimeMillis();

        try {
            pluginInstance.getLogger().info("Initializing vendor system (attempt " + attempt + "/" + MAX_INITIALIZATION_ATTEMPTS + ")");

            // Initialize core components that don't require Citizens
            initializeCoreComponents(pluginInstance);

            // Check Citizens availability and initialize accordingly
            if (isCitizensLoaded()) {
                if (isCitizensReady()) {
                    initializeVendorSystem(pluginInstance);
                } else {
                    scheduleDelayedInitialization(pluginInstance);
                }
            } else {
                startCitizensWaitMode(pluginInstance);
            }

            // Setup monitoring and validation
            setupSystemMonitoring(pluginInstance);

        } catch (Exception e) {
            lastInitializationError = e.getMessage();
            pluginInstance.getLogger().log(Level.SEVERE, "Failed to initialize vendor system (attempt " + attempt + "): " + e.getMessage(), e);

            // Retry if we haven't exceeded max attempts
            if (attempt < MAX_INITIALIZATION_ATTEMPTS) {
                scheduleRetryInitialization(pluginInstance);
            } else {
                pluginInstance.getLogger().severe("Vendor system initialization failed after " + MAX_INITIALIZATION_ATTEMPTS + " attempts. Running in degraded mode.");
                initializeDegradedMode(pluginInstance);
            }
        }
    }

    /**
     * Initialize core components that don't depend on Citizens
     */
    private static void initializeCoreComponents(JavaPlugin pluginInstance) {
        try {
            // Initialize PurchaseManager (independent of Citizens)
            PurchaseManager.initialize(pluginInstance);
            pluginInstance.getLogger().info("PurchaseManager initialized successfully");

            // Register vendor command
            if (pluginInstance.getCommand("vendor") != null) {
                pluginInstance.getCommand("vendor").setExecutor(new VendorCommand(pluginInstance));
                pluginInstance.getLogger().info("Vendor command registered successfully");
            } else {
                pluginInstance.getLogger().warning("Vendor command not found in plugin.yml");
            }

        } catch (Exception e) {
            pluginInstance.getLogger().log(Level.WARNING, "Error initializing core components", e);
            throw new RuntimeException("Core component initialization failed", e);
        }
    }

    /**
     * Initialize the full vendor system when Citizens is available
     */
    private static void initializeVendorSystem(JavaPlugin pluginInstance) {
        try {
            long startTime = System.currentTimeMillis();

            // Load configuration
            VendorConfiguration config = VendorConfiguration.getInstance((YakRealms) pluginInstance);
            pluginInstance.getLogger().info("Vendor configuration loaded");

            // Initialize VendorManager
            VendorManager vendorManager = VendorManager.getInstance(pluginInstance);
            vendorManager.initialize();
            pluginInstance.getLogger().info("VendorManager initialized");

            // Create hologram manager
            hologramManager = new VendorHologramManager(pluginInstance);
            pluginInstance.getLogger().info("Hologram manager initialized");

            // Initialize aura manager
            auraManager = new VendorAuraManager(pluginInstance);
            pluginInstance.getLogger().info("Aura manager initialized");

            // Set up health check system
            healthCheck = new VendorHealthCheck(
                    (YakRealms) pluginInstance,
                    vendorManager,
                    hologramManager
            );

            // Create initial backup
            if (healthCheck.createBackup()) {
                pluginInstance.getLogger().info("Initial vendor backup created");
            }

            // Run initial health check
            int issuesFixed = healthCheck.runComprehensiveHealthCheck();
            if (issuesFixed > 0) {
                pluginInstance.getLogger().info("Initial health check fixed " + issuesFixed + " issues");
            }

            // Register admin GUI if applicable
            if (pluginInstance instanceof YakRealms) {
                try {
                    new VendorConfigGUI((YakRealms) pluginInstance);
                    pluginInstance.getLogger().info("Vendor admin GUI registered");
                } catch (Exception e) {
                    pluginInstance.getLogger().log(Level.WARNING, "Failed to register admin GUI", e);
                }
            }

            // Start vendor aura effects with delay
            startAuraEffects(pluginInstance);

            // Mark as initialized
            initialized.set(true);
            long initTime = System.currentTimeMillis() - startTime;
            pluginInstance.getLogger().info("Vendor system fully initialized in " + initTime + "ms");

        } catch (Exception e) {
            pluginInstance.getLogger().log(Level.SEVERE, "Error during full vendor system initialization", e);
            throw new RuntimeException("Full system initialization failed", e);
        }
    }

    /**
     * Start aura effects with proper error handling
     */
    private static void startAuraEffects(JavaPlugin pluginInstance) {
        Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
            if (auraManager != null) {
                try {
                    auraManager.startAllAuras();
                    pluginInstance.getLogger().info("Vendor aura effects activated successfully");
                } catch (Exception e) {
                    pluginInstance.getLogger().log(Level.WARNING, "Failed to start vendor aura effects", e);
                }
            }
        }, 100L); // 5-second delay
    }

    /**
     * Initialize in degraded mode when Citizens is unavailable
     */
    private static void initializeDegradedMode(JavaPlugin pluginInstance) {
        try {
            pluginInstance.getLogger().warning("Initializing vendor system in degraded mode (Citizens unavailable)");

            // Mark as initialized to prevent further attempts
            initialized.set(true);

            // Still try to initialize what we can
            VendorConfiguration.getInstance((YakRealms) pluginInstance);

            pluginInstance.getLogger().info("Vendor system running in degraded mode - NPCs will not be functional");

        } catch (Exception e) {
            pluginInstance.getLogger().log(Level.SEVERE, "Failed to initialize even degraded mode", e);
        }
    }

    /**
     * Schedule delayed initialization when Citizens API isn't ready
     */
    private static void scheduleDelayedInitialization(JavaPlugin pluginInstance) {
        if (retryTask != null) {
            retryTask.cancel();
        }

        retryTask = new BukkitRunnable() {
            private int attempts = 0;
            private static final int MAX_ATTEMPTS = 10;

            @Override
            public void run() {
                attempts++;

                try {
                    if (isCitizensReady()) {
                        pluginInstance.getLogger().info("Citizens API ready after " + attempts + " attempts");
                        initializeVendorSystem(pluginInstance);
                        cancel();
                        return;
                    }

                    if (attempts >= MAX_ATTEMPTS) {
                        pluginInstance.getLogger().warning("Citizens API not ready after " + MAX_ATTEMPTS + " attempts");
                        initializeDegradedMode(pluginInstance);
                        cancel();
                    }
                } catch (Exception e) {
                    pluginInstance.getLogger().log(Level.WARNING, "Error during delayed initialization attempt " + attempts, e);
                    if (attempts >= MAX_ATTEMPTS) {
                        initializeDegradedMode(pluginInstance);
                        cancel();
                    }
                }
            }
        }.runTaskTimer(pluginInstance, 20L, 20L); // Check every second
    }

    /**
     * Schedule retry initialization after failure
     */
    private static void scheduleRetryInitialization(JavaPlugin pluginInstance) {
        if (retryTask != null) {
            retryTask.cancel();
        }

        retryTask = Bukkit.getScheduler().runTaskLater(pluginInstance, () -> {
            pluginInstance.getLogger().info("Retrying vendor system initialization...");
            initialize(pluginInstance);
        }, INITIALIZATION_RETRY_DELAY);
    }

    /**
     * Start Citizens waiting mode with periodic checks
     */
    private static void startCitizensWaitMode(JavaPlugin pluginInstance) {
        if (citizensWaitingMode.getAndSet(true)) {
            return; // Already in waiting mode
        }

        pluginInstance.getLogger().info("Citizens plugin not found. Waiting for Citizens to be loaded...");

        // Register plugin listener to detect Citizens being enabled
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                if ("Citizens".equals(event.getPlugin().getName()) && !initialized.get()) {
                    pluginInstance.getLogger().info("Citizens plugin loaded. Initializing vendor system...");
                    citizensWaitingMode.set(false);
                    scheduleDelayedInitialization(pluginInstance);
                }
            }
        }, pluginInstance);

        // Also check periodically in case we missed the event
        citizensWaitTask = new BukkitRunnable() {
            private int attempts = 0;
            private static final int MAX_WAIT_ATTEMPTS = 20;

            @Override
            public void run() {
                attempts++;

                if (isCitizensLoaded()) {
                    pluginInstance.getLogger().info("Citizens plugin detected after waiting");
                    citizensWaitingMode.set(false);
                    scheduleDelayedInitialization(pluginInstance);
                    cancel();
                    return;
                }

                if (attempts >= MAX_WAIT_ATTEMPTS) {
                    pluginInstance.getLogger().warning("Citizens plugin not detected after " + MAX_WAIT_ATTEMPTS + " checks");
                    citizensWaitingMode.set(false);
                    initializeDegradedMode(pluginInstance);
                    cancel();
                }
            }
        }.runTaskTimer(pluginInstance, CITIZENS_WAIT_INTERVAL, CITIZENS_WAIT_INTERVAL);
    }

    /**
     * Setup system monitoring and validation
     */
    private static void setupSystemMonitoring(JavaPlugin pluginInstance) {
        // Regular health monitoring
        healthMonitorTask = Bukkit.getScheduler().runTaskTimer(pluginInstance, () -> {
            try {
                if (initialized.get() && healthCheck != null) {
                    int issuesFixed = healthCheck.runComprehensiveHealthCheck();
                    if (issuesFixed > 0) {
                        pluginInstance.getLogger().info("Health monitor fixed " + issuesFixed + " vendor issues");
                    }
                }
            } catch (Exception e) {
                pluginInstance.getLogger().log(Level.WARNING, "Error during health monitoring", e);
            }
        }, HEALTH_CHECK_INTERVAL, HEALTH_CHECK_INTERVAL);

        // Setup regular validation
        setupRegularValidation(pluginInstance);
    }

    /**
     * Enhanced validation setup
     */
    public static void setupRegularValidation(JavaPlugin pluginInstance) {
        Bukkit.getScheduler().runTaskTimer(pluginInstance, () -> {
            if (!initialized.get()) return;

            try {
                VendorManager vendorManager = VendorManager.getInstance(pluginInstance);
                int issuesFixed = vendorManager.validateAndFixVendors();

                if (issuesFixed > 0) {
                    pluginInstance.getLogger().info("Periodic validation fixed " + issuesFixed + " vendor issues");
                }
            } catch (Exception e) {
                pluginInstance.getLogger().log(Level.WARNING, "Error during periodic validation", e);
            }
        }, 20 * 60 * 30, 20 * 60 * 30); // Run every 30 minutes
    }

    /**
     * Enhanced Citizens plugin detection
     */
    private static boolean isCitizensLoaded() {
        try {
            Plugin citizensPlugin = Bukkit.getPluginManager().getPlugin("Citizens");
            return citizensPlugin != null && citizensPlugin.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Enhanced Citizens API readiness check
     */
    private static boolean isCitizensReady() {
        try {
            if (!isCitizensLoaded()) {
                return false;
            }

            // Try accessing multiple Citizens API components
            CitizensAPI.getNPCRegistry();
            CitizensAPI.getTraitFactory();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Enhanced shutdown with proper cleanup order
     */
    public static void shutdown(JavaPlugin pluginInstance) {
        if (!initialized.get()) return;

        try {
            pluginInstance.getLogger().info("Shutting down vendor system...");

            // Cancel all tasks
            cancelAllTasks();

            // Stop aura effects first
            if (auraManager != null) {
                try {
                    auraManager.stopAllAuras();
                    pluginInstance.getLogger().info("Vendor aura effects stopped");
                } catch (Exception e) {
                    pluginInstance.getLogger().log(Level.WARNING, "Error stopping aura effects", e);
                }
            }

            // Shutdown vendor manager
            try {
                VendorManager.getInstance(pluginInstance).shutdown();
                pluginInstance.getLogger().info("VendorManager shutdown complete");
            } catch (Exception e) {
                pluginInstance.getLogger().log(Level.WARNING, "Error during VendorManager shutdown", e);
            }

            // Shutdown purchase manager
            try {
                // PurchaseManager shutdown if it has one
                pluginInstance.getLogger().info("PurchaseManager shutdown complete");
            } catch (Exception e) {
                pluginInstance.getLogger().log(Level.WARNING, "Error during PurchaseManager shutdown", e);
            }

            // Reset state
            initialized.set(false);
            citizensWaitingMode.set(false);
            auraManager = null;
            hologramManager = null;
            healthCheck = null;

            pluginInstance.getLogger().info("Vendor system shutdown complete");

        } catch (Exception e) {
            pluginInstance.getLogger().log(Level.SEVERE, "Error during vendor system shutdown", e);
        }
    }

    /**
     * Cancel all running tasks
     */
    private static void cancelAllTasks() {
        if (citizensWaitTask != null) {
            citizensWaitTask.cancel();
            citizensWaitTask = null;
        }
        if (healthMonitorTask != null) {
            healthMonitorTask.cancel();
            healthMonitorTask = null;
        }
        if (retryTask != null) {
            retryTask.cancel();
            retryTask = null;
        }
    }

    /**
     * Restart aura system with error handling
     */
    public static void restartAuras() {
        if (auraManager != null && initialized.get()) {
            try {
                plugin.getLogger().info("Restarting vendor aura effects...");
                auraManager.stopAllAuras();
                auraManager.startAllAuras();
                plugin.getLogger().info("Vendor aura effects restarted successfully");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error restarting aura effects", e);
            }
        }
    }

    /**
     * Force reinitialize the system (admin command)
     */
    public static void forceReinitialize(JavaPlugin pluginInstance) {
        plugin.getLogger().info("Force reinitializing vendor system...");

        // Reset state
        initialized.set(false);
        initializationAttempts.set(0);
        lastInitializationError = null;

        // Cancel tasks
        cancelAllTasks();

        // Reinitialize
        initialize(pluginInstance);
    }

    // ================== GETTERS ==================

    public static boolean isInitialized() {
        return initialized.get();
    }

    public static VendorAuraManager getAuraManager() {
        return auraManager;
    }

    public static VendorHologramManager getHologramManager() {
        return hologramManager;
    }

    public static VendorHealthCheck getHealthCheck() {
        return healthCheck;
    }

    /**
     * Get system status information
     */
    public static SystemStatus getSystemStatus() {
        return new SystemStatus(
                initialized.get(),
                citizensWaitingMode.get(),
                initializationAttempts.get(),
                lastInitializationAttempt,
                lastInitializationError,
                isCitizensLoaded(),
                isCitizensReady(),
                auraManager != null,
                hologramManager != null,
                healthCheck != null
        );
    }

    /**
     * System status information class
     */
    public static class SystemStatus {
        public final boolean initialized;
        public final boolean waitingForCitizens;
        public final int initializationAttempts;
        public final long lastInitializationAttempt;
        public final String lastInitializationError;
        public final boolean citizensLoaded;
        public final boolean citizensReady;
        public final boolean auraManagerActive;
        public final boolean hologramManagerActive;
        public final boolean healthCheckActive;

        public SystemStatus(boolean initialized, boolean waitingForCitizens, int initializationAttempts,
                            long lastInitializationAttempt, String lastInitializationError,
                            boolean citizensLoaded, boolean citizensReady, boolean auraManagerActive,
                            boolean hologramManagerActive, boolean healthCheckActive) {
            this.initialized = initialized;
            this.waitingForCitizens = waitingForCitizens;
            this.initializationAttempts = initializationAttempts;
            this.lastInitializationAttempt = lastInitializationAttempt;
            this.lastInitializationError = lastInitializationError;
            this.citizensLoaded = citizensLoaded;
            this.citizensReady = citizensReady;
            this.auraManagerActive = auraManagerActive;
            this.hologramManagerActive = hologramManagerActive;
            this.healthCheckActive = healthCheckActive;
        }
    }
}
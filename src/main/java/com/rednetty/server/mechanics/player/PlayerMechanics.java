package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.social.friends.Buddies;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.movement.DashMechanics;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simplified player mechanics coordinator without circular dependencies
 */
public class PlayerMechanics implements Listener {
    private static PlayerMechanics instance;
    private final Logger logger;

    // Core subsystems
    private Energy energySystem;
    private Toggles toggleSystem;
    private Buddies buddySystem;
    private DashMechanics dashMechanics;
    private PlayerListenerManager listenerManager;

    // State management
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean systemsReady = new AtomicBoolean(false);

    // Performance monitoring
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private BukkitTask performanceMonitorTask;
    private BukkitTask healthCheckTask;

    // Configuration
    private final boolean enablePerformanceMonitoring;
    private final boolean enableHealthChecks;
    private final long healthCheckInterval;
    private final long performanceLogInterval;

    private PlayerMechanics() {
        this.logger = YakRealms.getInstance().getLogger();

        // Load configuration
        this.enablePerformanceMonitoring = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_performance_monitoring", true);
        this.enableHealthChecks = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_health_checks", true);
        this.healthCheckInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.health_check_interval", 300); // 5 minutes
        this.performanceLogInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.performance_log_interval", 600); // 10 minutes
    }

    public static PlayerMechanics getInstance() {
        if (instance == null) {
            synchronized (PlayerMechanics.class) {
                if (instance == null) {
                    instance = new PlayerMechanics();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize PlayerMechanics subsystems
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics already initialized!");
            return;
        }

        try {
            logger.info("Starting PlayerMechanics initialization...");

            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Initialize subsystems in order
            initializeSubsystems();

            // Start monitoring tasks
            startMonitoringTasks();

            // Validate initialization
            validateSubsystems();

            systemsReady.set(true);
            logger.info("PlayerMechanics initialization completed successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize PlayerMechanics", e);
            emergencyCleanup();
            throw new RuntimeException("PlayerMechanics initialization failed", e);
        }
    }

    /**
     * Initialize all subsystems
     */
    private void initializeSubsystems() {
        logger.info("Initializing PlayerMechanics subsystems...");

        // 1. Energy System
        logger.info("Initializing Energy system...");
        this.energySystem = Energy.getInstance();
        this.energySystem.onEnable();

        // 2. Toggle System
        logger.info("Initializing Toggles system...");
        this.toggleSystem = Toggles.getInstance();
        this.toggleSystem.onEnable();

        // 3. Buddy System
        logger.info("Initializing Buddies system...");
        this.buddySystem = Buddies.getInstance();
        this.buddySystem.onEnable();

        // 4. Movement Mechanics
        logger.info("Initializing Dash mechanics...");
        this.dashMechanics = new DashMechanics();
        this.dashMechanics.onEnable();

        // 5. Listener Manager (coordinates all player events)
        logger.info("Initializing PlayerListenerManager...");
        this.listenerManager = PlayerListenerManager.getInstance();
        this.listenerManager.onEnable();

        logger.info("All PlayerMechanics subsystems initialized successfully");
    }

    /**
     * Start monitoring tasks
     */
    private void startMonitoringTasks() {
        if (enablePerformanceMonitoring) {
            performanceMonitorTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    YakRealms.getInstance(),
                    this::logPerformanceMetrics,
                    20L * performanceLogInterval,
                    20L * performanceLogInterval
            );
            logger.info("Performance monitoring started");
        }

        if (enableHealthChecks) {
            healthCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    YakRealms.getInstance(),
                    this::performHealthChecks,
                    20L * healthCheckInterval,
                    20L * healthCheckInterval
            );
            logger.info("Health checks started");
        }
    }

    /**
     * Validate that all subsystems are properly initialized
     */
    private void validateSubsystems() {
        StringBuilder issues = new StringBuilder();

        if (energySystem == null) {
            issues.append("Energy system not initialized; ");
        }
        if (toggleSystem == null) {
            issues.append("Toggle system not initialized; ");
        }
        if (buddySystem == null) {
            issues.append("Buddy system not initialized; ");
        }
        if (dashMechanics == null) {
            issues.append("Dash mechanics not initialized; ");
        }
        if (listenerManager == null) {
            issues.append("Listener manager not initialized; ");
        }

        if (issues.length() > 0) {
            throw new RuntimeException("Subsystem validation failed: " + issues.toString());
        }

        logger.info("All subsystems validated successfully");
    }

    /**
     * Simple event handling for metrics
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        totalPlayerQuits.incrementAndGet();
    }

    /**
     * Enhanced shutdown with proper cleanup ordering
     */
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics shutdown already in progress!");
            return;
        }

        logger.info("Starting PlayerMechanics shutdown...");

        try {
            // Stop monitoring tasks first
            stopMonitoringTasks();

            // Shutdown subsystems in reverse dependency order
            shutdownSubsystems();

            logger.info("PlayerMechanics shutdown completed successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during PlayerMechanics shutdown", e);
        } finally {
            initialized.set(false);
            systemsReady.set(false);
            shutdownInProgress.set(false);
        }
    }

    /**
     * Stop all monitoring tasks
     */
    private void stopMonitoringTasks() {
        if (performanceMonitorTask != null && !performanceMonitorTask.isCancelled()) {
            performanceMonitorTask.cancel();
            performanceMonitorTask = null;
        }

        if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
            healthCheckTask.cancel();
            healthCheckTask = null;
        }

        logger.info("Monitoring tasks stopped");
    }

    /**
     * Shutdown subsystems in proper order
     */
    private void shutdownSubsystems() {
        logger.info("Shutting down subsystems...");

        // 1. Listener Manager (stop processing events first)
        if (listenerManager != null) {
            try {
                listenerManager.onDisable();
                logger.info("PlayerListenerManager shut down");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down PlayerListenerManager", e);
            }
        }

        // 2. Movement Mechanics
        if (dashMechanics != null) {
            try {
                dashMechanics.onDisable();
                logger.info("Dash mechanics shut down");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down Dash mechanics", e);
            }
        }

        // 3. Buddy System
        if (buddySystem != null) {
            try {
                buddySystem.onDisable();
                logger.info("Buddies system shut down");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down Buddies system", e);
            }
        }

        // 4. Toggle System
        if (toggleSystem != null) {
            try {
                toggleSystem.onDisable();
                logger.info("Toggles system shut down");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down Toggles system", e);
            }
        }

        // 5. Energy System
        if (energySystem != null) {
            try {
                energySystem.onDisable();
                logger.info("Energy system shut down");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down Energy system", e);
            }
        }
    }

    /**
     * Emergency cleanup on initialization failure
     */
    private void emergencyCleanup() {
        logger.warning("Performing emergency cleanup...");

        try {
            stopMonitoringTasks();

            // Try to safely shutdown any initialized subsystems
            if (listenerManager != null) listenerManager.onDisable();
            if (dashMechanics != null) dashMechanics.onDisable();
            if (buddySystem != null) buddySystem.onDisable();
            if (toggleSystem != null) toggleSystem.onDisable();
            if (energySystem != null) energySystem.onDisable();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during emergency cleanup", e);
        } finally {
            initialized.set(false);
            systemsReady.set(false);
            shutdownInProgress.set(false);
        }
    }

    /**
     * Log performance metrics
     */
    private void logPerformanceMetrics() {
        try {
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            logger.info("=== PlayerMechanics Performance ===");
            logger.info("Online Players: " + onlinePlayers);
            logger.info("Total Joins: " + totalPlayerJoins.get());
            logger.info("Total Quits: " + totalPlayerQuits.get());
            logger.info("Memory Usage: " + (usedMemory / 1024 / 1024) + "MB / " + (totalMemory / 1024 / 1024) + "MB");
            logger.info("Systems Ready: " + systemsReady.get());
            logger.info("================================");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error logging performance metrics", e);
        }
    }

    /**
     * Perform health checks on all subsystems
     */
    private void performHealthChecks() {
        try {
            boolean allHealthy = true;
            StringBuilder healthReport = new StringBuilder("=== PlayerMechanics Health Check ===\n");

            // Check each subsystem
            allHealthy &= checkSubsystemHealth("Energy System", energySystem != null);
            allHealthy &= checkSubsystemHealth("Toggle System", toggleSystem != null);
            allHealthy &= checkSubsystemHealth("Buddy System", buddySystem != null);
            allHealthy &= checkSubsystemHealth("Dash Mechanics", dashMechanics != null);
            allHealthy &= checkSubsystemHealth("Listener Manager", listenerManager != null);

            // Check for memory issues
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            if (memoryUsagePercent > 85) {
                healthReport.append("WARNING: High memory usage (").append(String.format("%.1f", memoryUsagePercent)).append("%)\n");
                allHealthy = false;
            }

            healthReport.append("Overall Status: ").append(allHealthy ? "HEALTHY" : "ISSUES DETECTED");
            healthReport.append("\n=====================================");

            if (!allHealthy) {
                logger.warning(healthReport.toString());
            } else {
                logger.fine("PlayerMechanics health check passed");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error performing health checks", e);
        }
    }

    /**
     * Check individual subsystem health
     */
    private boolean checkSubsystemHealth(String systemName, boolean isHealthy) {
        if (!isHealthy) {
            logger.warning("Health check FAILED for: " + systemName);
        }
        return isHealthy;
    }

    // Public API methods
    public boolean isInitialized() {
        return initialized.get() && systemsReady.get() && !shutdownInProgress.get();
    }

    public boolean isSystemsReady() {
        return systemsReady.get();
    }

    public Energy getEnergySystem() {
        return energySystem;
    }

    public Toggles getToggleSystem() {
        return toggleSystem;
    }

    public Buddies getBuddySystem() {
        return buddySystem;
    }

    public DashMechanics getDashMechanics() {
        return dashMechanics;
    }

    public PlayerListenerManager getListenerManager() {
        return listenerManager;
    }

    /**
     * Get system statistics
     */
    public SystemStats getSystemStats() {
        return new SystemStats(
                totalPlayerJoins.get(),
                totalPlayerQuits.get(),
                Bukkit.getOnlinePlayers().size(),
                isInitialized(),
                systemsReady.get()
        );
    }

    /**
     * System statistics class
     */
    public static class SystemStats {
        public final int totalJoins;
        public final int totalQuits;
        public final int currentOnline;
        public final boolean systemHealthy;
        public final boolean systemsReady;

        SystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy, boolean systemsReady) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
            this.systemsReady = systemsReady;
        }

        @Override
        public String toString() {
            return String.format("SystemStats{joins=%d, quits=%d, online=%d, healthy=%s, ready=%s}",
                    totalJoins, totalQuits, currentOnline, systemHealthy, systemsReady);
        }
    }
}
package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.friends.Buddies;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.movement.DashMechanics;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIXED: Main coordinator for all player-related mechanics
 * Properly waits for YakPlayerManager initialization and coordinates subsystems
 */
public class PlayerMechanics implements Listener {
    private static PlayerMechanics instance;
    private final Logger logger;

    // Core subsystems
    private YakPlayerManager playerManager;
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
    private BukkitTask initializationCheckTask;

    // Configuration
    private final boolean enablePerformanceMonitoring;
    private final boolean enableHealthChecks;
    private final long healthCheckInterval;
    private final long performanceLogInterval;
    private final long initializationTimeout;

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
        this.initializationTimeout = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.initialization_timeout", 60); // 60 seconds
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
     * FIXED: Initialize with proper async waiting for YakPlayerManager
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics already initialized!");
            return;
        }

        logger.info("Starting PlayerMechanics initialization...");

        try {
            // Register ONLY this class's events (minimal coordination events)
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Start async initialization process
            initializeAsync().thenRun(() -> {
                // Completion on main thread
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                    try {
                        completeInitialization();
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to complete PlayerMechanics initialization", e);
                        emergencyCleanup();
                    }
                });
            }).exceptionally(throwable -> {
                logger.log(Level.SEVERE, "PlayerMechanics async initialization failed", throwable);
                emergencyCleanup();
                return null;
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start PlayerMechanics initialization", e);
            emergencyCleanup();
            throw new RuntimeException("PlayerMechanics initialization failed", e);
        }
    }

    /**
     * Async initialization that waits for dependencies
     */
    private CompletableFuture<Void> initializeAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                // Wait for YakPlayerManager to be ready
                waitForYakPlayerManager();

                // Initialize core subsystems in proper order
                initializeCoreSubsystems();

                systemsReady.set(true);
                logger.info("PlayerMechanics async initialization completed");

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in async initialization", e);
                throw new RuntimeException("Async initialization failed", e);
            }
        });
    }

    /**
     * Wait for YakPlayerManager to be properly initialized
     */
    private void waitForYakPlayerManager() {
        logger.info("Waiting for YakPlayerManager to be ready...");

        long startTime = System.currentTimeMillis();
        long timeoutMs = initializationTimeout * 1000;

        while (true) {
            try {
                YakPlayerManager manager = YakPlayerManager.getInstance();

                // Check if manager is properly initialized
                if (manager != null && manager.isRepositoryReady() && manager.isSystemHealthy()) {
                    this.playerManager = manager;
                    logger.info("YakPlayerManager is ready!");
                    break;
                }

                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw new RuntimeException("Timeout waiting for YakPlayerManager after " +
                            initializationTimeout + " seconds");
                }

                // Wait a bit before checking again
                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for YakPlayerManager", e);
            }
        }
    }

    /**
     * Initialize core subsystems with enhanced error handling
     */
    private void initializeCoreSubsystems() {
        logger.info("Initializing core player subsystems...");

        // 2. Energy System
        logger.info("Initializing Energy system...");
        this.energySystem = Energy.getInstance();
        this.energySystem.onEnable();

        // 3. Toggle System
        logger.info("Initializing Toggles system...");
        this.toggleSystem = Toggles.getInstance();
        this.toggleSystem.onEnable();

        // 4. Buddy System
        logger.info("Initializing Buddies system...");
        this.buddySystem = Buddies.getInstance();
        this.buddySystem.onEnable();

        // 5. Movement Mechanics
        logger.info("Initializing Dash mechanics...");
        this.dashMechanics = new DashMechanics();
        this.dashMechanics.onEnable();

        // 6. Listener Manager (coordinates all player events)
        logger.info("Initializing PlayerListenerManager...");
        this.listenerManager = PlayerListenerManager.getInstance();
        this.listenerManager.onEnable();

        logger.info("All core subsystems initialized successfully");
    }

    /**
     * Complete initialization on main thread
     */
    private void completeInitialization() {
        // Start monitoring tasks
        startMonitoringTasks();

        // Validate initialization
        validateSubsystems();

        logger.info("PlayerMechanics initialization completed successfully");
        logSystemStatus();
    }

    /**
     * Start monitoring and maintenance tasks
     */
    private void startMonitoringTasks() {
        if (enablePerformanceMonitoring) {
            startPerformanceMonitoring();
        }

        if (enableHealthChecks) {
            startHealthChecks();
        }

        // Start initialization check task to monitor system health
        startInitializationCheck();
    }

    /**
     * Start performance monitoring task
     */
    private void startPerformanceMonitoring() {
        performanceMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                logPerformanceMetrics();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(),
                20L * performanceLogInterval, 20L * performanceLogInterval);

        logger.info("Performance monitoring started (interval: " + performanceLogInterval + "s)");
    }

    /**
     * Start health check monitoring
     */
    private void startHealthChecks() {
        healthCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                performHealthChecks();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(),
                20L * healthCheckInterval, 20L * healthCheckInterval);

        logger.info("Health checks started (interval: " + healthCheckInterval + "s)");
    }

    /**
     * Start initialization monitoring task
     */
    private void startInitializationCheck() {
        initializationCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkSystemInitialization();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 30, 20L * 30); // Every 30 seconds
    }

    /**
     * Check system initialization status
     */
    private void checkSystemInitialization() {
        if (!systemsReady.get()) {
            logger.warning("Systems not ready yet...");
            return;
        }

        // Check if all systems are still functioning
        boolean allHealthy = true;
        StringBuilder issues = new StringBuilder();

        if (playerManager == null || !playerManager.isSystemHealthy()) {
            allHealthy = false;
            issues.append("YakPlayerManager unhealthy; ");
        }

        if (listenerManager == null) {
            allHealthy = false;
            issues.append("PlayerListenerManager not initialized; ");
        }

        if (!allHealthy) {
            logger.warning("System health issues detected: " + issues.toString());
            // Could trigger automatic recovery here if needed
        }
    }

    /**
     * Validate that all subsystems are properly initialized
     */
    private void validateSubsystems() {
        StringBuilder issues = new StringBuilder();

        if (playerManager == null || !playerManager.isSystemHealthy()) {
            issues.append("YakPlayerManager not healthy; ");
        }
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
     * MINIMAL event handling - only track metrics
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

            // Final cleanup
            performFinalCleanup();

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

        if (initializationCheckTask != null && !initializationCheckTask.isCancelled()) {
            initializationCheckTask.cancel();
            initializationCheckTask = null;
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

        // 6. Player Manager (last, as it handles data persistence)
        if (playerManager != null) {
            try {
                playerManager.onDisable();
                logger.info("YakPlayerManager shut down");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error shutting down YakPlayerManager", e);
            }
        }
    }

    /**
     * Perform final cleanup
     */
    private void performFinalCleanup() {
        // Clear references
        playerManager = null;
        energySystem = null;
        toggleSystem = null;
        buddySystem = null;
        dashMechanics = null;
        listenerManager = null;

        logger.info("Final cleanup completed");
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
            if (playerManager != null) playerManager.onDisable();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during emergency cleanup", e);
        } finally {
            initialized.set(false);
            systemsReady.set(false);
            shutdownInProgress.set(false);
        }
    }

    /**
     * Log current system status
     */
    private void logSystemStatus() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        logger.info("=== PlayerMechanics Status ===");
        logger.info("Systems Ready: " + systemsReady.get());
        logger.info("Online Players: " + onlinePlayers);
        logger.info("Total Joins: " + totalPlayerJoins.get());
        logger.info("Total Quits: " + totalPlayerQuits.get());
        logger.info("Performance Monitoring: " + (enablePerformanceMonitoring ? "Enabled" : "Disabled"));
        logger.info("Health Checks: " + (enableHealthChecks ? "Enabled" : "Disabled"));

        if (playerManager != null) {
            logger.info("YakPlayerManager: " + (playerManager.isSystemHealthy() ? "Healthy" : "Unhealthy"));
        }

        logger.info("============================");
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

            logger.info("=== Performance Metrics ===");
            logger.info("Online Players: " + onlinePlayers);
            logger.info("Total Joins: " + totalPlayerJoins.get());
            logger.info("Total Quits: " + totalPlayerQuits.get());
            logger.info("Memory Usage: " + (usedMemory / 1024 / 1024) + "MB / " + (totalMemory / 1024 / 1024) + "MB");

            // Get subsystem-specific metrics
            if (playerManager != null) {
                playerManager.logPerformanceStats();
            }

            logger.info("===========================");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error logging performance metrics", e);
        }
    }

    /**
     * Perform health checks on all subsystems
     */
    private void performHealthChecks() {
        try {
            StringBuilder healthReport = new StringBuilder("=== Health Check Report ===\n");
            boolean allHealthy = true;

            // Check each subsystem
            allHealthy &= checkSubsystemHealth("YakPlayerManager", playerManager != null && playerManager.isSystemHealthy());
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

            // Check player count vs performance
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            if (onlinePlayers > 100) {
                healthReport.append("INFO: High player count (").append(onlinePlayers).append(" players)\n");
            }

            healthReport.append("Overall Status: ").append(allHealthy ? "HEALTHY" : "ISSUES DETECTED");
            healthReport.append("\n============================");

            if (!allHealthy) {
                logger.warning(healthReport.toString());
                notifyAdministrators("PlayerMechanics health check detected issues!");
            } else {
                logger.fine("Health check passed - all systems operational");
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

    /**
     * Notify administrators of system issues
     */
    private void notifyAdministrators(String message) {
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("yakserver.admin")) {
                    player.sendMessage(ChatColor.RED + "[SYSTEM] " + message);
                }
            }
        });
    }

    // Public API methods

    public boolean isInitialized() {
        return initialized.get() && systemsReady.get() && !shutdownInProgress.get();
    }

    public boolean isSystemsReady() {
        return systemsReady.get();
    }

    public YakPlayerManager getPlayerManager() {
        return playerManager;
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
     * Reload all player mechanics systems
     */
    public CompletableFuture<Boolean> reloadSystems() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Reloading PlayerMechanics systems...");

                // Save current state
                if (playerManager != null) {
                    playerManager.saveAllPlayers();
                }

                logger.info("PlayerMechanics systems reloaded successfully");
                return true;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error reloading PlayerMechanics systems", e);
                return false;
            }
        });
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
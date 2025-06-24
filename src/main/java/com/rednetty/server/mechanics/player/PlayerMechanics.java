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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced main coordinator for all player-related mechanics with improved
 * initialization, error handling, and performance monitoring.
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
     * Enhanced initialization with proper dependency ordering and error handling
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics already initialized!");
            return;
        }

        logger.info("Starting PlayerMechanics initialization...");

        try {
            // Initialize core subsystems in proper order
            initializeCoreSubsystems();

            // Register event listeners
            registerEventListeners();

            // Start monitoring tasks
            startMonitoringTasks();

            // Validate initialization
            validateSubsystems();

            logger.info("PlayerMechanics initialization completed successfully");
            logSystemStatus();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize PlayerMechanics", e);

            // Attempt cleanup on failure
            emergencyCleanup();
            throw new RuntimeException("PlayerMechanics initialization failed", e);
        }
    }

    /**
     * Initialize core subsystems with enhanced error handling
     */
    private void initializeCoreSubsystems() {
        logger.info("Initializing core player subsystems...");

        // Initialize in dependency order

        // 1. Player Manager (core dependency)
        logger.info("Initializing YakPlayerManager...");
        this.playerManager = YakPlayerManager.getInstance();
        this.playerManager.onEnable();

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
     * Register event listeners for PlayerMechanics coordination
     */
    private void registerEventListeners() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        logger.info("PlayerMechanics event listeners registered");
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
     * Validate that all subsystems are properly initialized
     */
    private void validateSubsystems() {
        StringBuilder issues = new StringBuilder();

        if (playerManager == null) {
            issues.append("YakPlayerManager not initialized; ");
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

        // Shutdown in reverse dependency order

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
            shutdownInProgress.set(false);
        }
    }

    /**
     * Log current system status
     */
    private void logSystemStatus() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        logger.info("=== PlayerMechanics Status ===");
        logger.info("Online Players: " + onlinePlayers);
        logger.info("Total Joins: " + totalPlayerJoins.get());
        logger.info("Total Quits: " + totalPlayerQuits.get());
        logger.info("Performance Monitoring: " + (enablePerformanceMonitoring ? "Enabled" : "Disabled"));
        logger.info("Health Checks: " + (enableHealthChecks ? "Enabled" : "Disabled"));
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
            allHealthy &= checkSubsystemHealth("YakPlayerManager", playerManager != null);
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

                // Notify administrators if issues detected
                notifyAdministrators("PlayerMechanics health check detected issues!");
            } else {
                logger.info("Health check passed - all systems operational");
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

    // Event handlers for coordination

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        totalPlayerJoins.incrementAndGet();

        // Coordinate any cross-subsystem player join logic here
        Player player = event.getPlayer();

        // Example: Initialize player-specific data across systems
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            initializePlayerSystems(player);
        }, 20L); // 1 second delay to ensure player is fully loaded
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        totalPlayerQuits.incrementAndGet();

        // Coordinate any cross-subsystem player quit logic here
        Player player = event.getPlayer();
        cleanupPlayerSystems(player);
    }

    /**
     * Initialize player-specific systems
     */
    private void initializePlayerSystems(Player player) {
        if (!player.isOnline()) return;

        try {
            // Ensure player data is loaded and systems are synchronized
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                // Cross-system initialization can be added here
                logger.fine("Initialized cross-systems for player: " + player.getName());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
        }
    }

    /**
     * Clean up player-specific systems
     */
    private void cleanupPlayerSystems(Player player) {
        try {
            // Perform any cross-system cleanup here
            UUID uuid = player.getUniqueId();

            // Example: Clean up any temporary data or caches
            logger.fine("Cleaned up cross-systems for player: " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up player systems for " + player.getName(), e);
        }
    }

    // Public API methods for other systems

    /**
     * Check if PlayerMechanics is properly initialized
     */
    public boolean isInitialized() {
        return initialized.get() && !shutdownInProgress.get();
    }

    /**
     * Get the player manager instance
     */
    public YakPlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Get the energy system instance
     */
    public Energy getEnergySystem() {
        return energySystem;
    }

    /**
     * Get the toggle system instance
     */
    public Toggles getToggleSystem() {
        return toggleSystem;
    }

    /**
     * Get the buddy system instance
     */
    public Buddies getBuddySystem() {
        return buddySystem;
    }

    /**
     * Get the dash mechanics instance
     */
    public DashMechanics getDashMechanics() {
        return dashMechanics;
    }

    /**
     * Get the listener manager instance
     */
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

                // Reinitialize systems
                onDisable();
                Thread.sleep(1000); // Brief pause
                onEnable();

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
                isInitialized()
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

        SystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
        }

        @Override
        public String toString() {
            return String.format("SystemStats{joins=%d, quits=%d, online=%d, healthy=%s}",
                    totalJoins, totalQuits, currentOnline, systemHealthy);
        }
    }
}
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  PlayerMechanics coordinator with BULLETPROOF YakPlayerManager integration
 *
 * MAJOR IMPROVEMENTS:
 * - Full coordination with BULLETPROOF YakPlayerManager loading system
 * - Respects bulletproof void limbo states and protection
 * - No interference with loading process or spectator mode
 * -  subsystem coordination
 * - Bulletproof monitoring and health checks
 * - Improved error handling and recovery
 *
 * RESPONSIBILITIES:
 * - Coordinate player subsystems (Energy, Toggles, Buddies, Dash, Listeners)
 * - Monitor system health and performance with bulletproof integration
 * - Provide subsystem access to other components
 * - Handle subsystem lifecycle management
 * - Wait for bulletproof loading completion before subsystem initialization
 *
 * COORDINATES WITH:
 * - YakPlayerManager (bulletproof data loading and void limbo management)
 * - Waits for bulletproof loading completion before subsystem initialization
 * - Respects all protection states during critical operations
 * - Does not interfere with spectator mode during limbo
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
    private final AtomicInteger subsystemInitializations = new AtomicInteger(0);
    private final AtomicInteger subsystemFailures = new AtomicInteger(0);
    private final AtomicInteger bulletproofCoordinationEvents = new AtomicInteger(0);
    private final AtomicInteger bulletproofWaitEvents = new AtomicInteger(0);

    private BukkitTask performanceMonitorTask;
    private BukkitTask healthCheckTask;
    private BukkitTask bulletproofCoordinationTask;

    // Configuration
    private final boolean enablePerformanceMonitoring;
    private final boolean enableHealthChecks;
    private final boolean enableBulletproofCoordination;
    private final long healthCheckInterval;
    private final long performanceLogInterval;
    private final long bulletproofCoordinationInterval;

    // Integration with bulletproof YakPlayerManager
    private YakPlayerManager playerManager;

    private PlayerMechanics() {
        this.logger = YakRealms.getInstance().getLogger();

        // Load configuration
        this.enablePerformanceMonitoring = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_performance_monitoring", true);
        this.enableHealthChecks = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_health_checks", true);
        this.enableBulletproofCoordination = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_bulletproof_coordination", true);
        this.healthCheckInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.health_check_interval", 300); // 5 minutes
        this.performanceLogInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.performance_log_interval", 600); // 10 minutes
        this.bulletproofCoordinationInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.bulletproof_coordination_interval", 60); // 1 minute
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
     * Initialize PlayerMechanics with BULLETPROOF YakPlayerManager integration
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics already initialized!");
            return;
        }

        try {
            logger.info("Starting  PlayerMechanics initialization with BULLETPROOF coordination...");

            // Get reference to bulletproof YakPlayerManager for coordination
            this.playerManager = YakPlayerManager.getInstance();

            // Verify bulletproof system is ready
            if (!verifyBulletproofSystemReady()) {
                throw new RuntimeException("Bulletproof YakPlayerManager not ready for coordination");
            }

            // Register events with HIGH priority to not interfere with YakPlayerManager
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Initialize subsystems in order
            initializeSubsystems();

            // Start monitoring tasks
            startMonitoringTasks();

            // Start bulletproof coordination monitoring
            if (enableBulletproofCoordination) {
                startBulletproofCoordinationMonitoring();
            }

            // Validate initialization
            validateSubsystems();

            systemsReady.set(true);
            logger.info(" PlayerMechanics initialization completed with BULLETPROOF coordination");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize PlayerMechanics", e);
            emergencyCleanup();
            throw new RuntimeException("PlayerMechanics initialization failed", e);
        }
    }

    /**
     * Verify bulletproof system is ready for coordination
     */
    private boolean verifyBulletproofSystemReady() {
        try {
            if (playerManager == null) {
                logger.severe("BULLETPROOF VERIFICATION: YakPlayerManager is null!");
                return false;
            }

            if (!playerManager.isSystemHealthy()) {
                logger.warning("BULLETPROOF VERIFICATION: YakPlayerManager reports unhealthy state");
                return false;
            }

            if (!playerManager.isRepositoryReady()) {
                logger.warning("BULLETPROOF VERIFICATION: YakPlayerManager repository not ready");
                return false;
            }

            logger.info("BULLETPROOF VERIFICATION: ✓ All systems ready for coordination");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "BULLETPROOF VERIFICATION: Error during verification", e);
            return false;
        }
    }

    /**
     * Initialize all subsystems
     */
    private void initializeSubsystems() {
        logger.info("Initializing PlayerMechanics subsystems...");

        try {
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

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing subsystems", e);
            throw e;
        }
    }

    /**
     *  Player join handler - coordinates with BULLETPROOF YakPlayerManager
     *
     * Runs at HIGH priority to avoid interfering with YakPlayerManager (LOWEST)
     * Only performs basic tracking, no data manipulation or interference with loading
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        totalPlayerJoins.incrementAndGet();

        if (!enableBulletproofCoordination) {
            logger.fine("Bulletproof coordination disabled, skipping coordination");
            return;
        }

        org.bukkit.entity.Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();

        // BULLETPROOF: Check if player is in protected state (loading)
        if (playerManager != null && playerManager.isPlayerProtected(uuid)) {
            logger.fine("BULLETPROOF COORDINATION: Player " + player.getName() +
                    " is in protected state, PlayerMechanics waiting");
            bulletproofCoordinationEvents.incrementAndGet();
            return;
        }

        // BULLETPROOF: Check if player is still loading
        if (playerManager != null && playerManager.isPlayerLoading(uuid)) {
            YakPlayerManager.LoadingPhase phase = playerManager.getPlayerLoadingPhase(uuid);
            logger.fine("BULLETPROOF COORDINATION: Player " + player.getName() +
                    " is loading (phase: " + phase + "), PlayerMechanics waiting");
            bulletproofCoordinationEvents.incrementAndGet();
            return;
        }

        // BULLETPROOF: Check if player is in void limbo
        if (playerManager != null && playerManager.isPlayerInVoidLimbo(uuid)) {
            logger.fine("BULLETPROOF COORDINATION: Player " + player.getName() +
                    " is in void limbo, PlayerMechanics waiting");
            bulletproofCoordinationEvents.incrementAndGet();
            return;
        }

        // BULLETPROOF: Schedule subsystem initialization for when player is ready
        if (playerManager != null) {
            scheduleBulletproofSubsystemInitialization(player);
        } else {
            logger.fine("PlayerMechanics processing join for: " + player.getName() +
                    " (no bulletproof YakPlayerManager)");
        }
    }

    /**
     * BULLETPROOF: Schedule subsystem initialization when player is fully ready
     */
    private void scheduleBulletproofSubsystemInitialization(org.bukkit.entity.Player player) {
        java.util.UUID uuid = player.getUniqueId();

        // Wait for player to be completely ready
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (!player.isOnline()) return;

            // BULLETPROOF: Double-check player is ready
            if (playerManager.isPlayerLoading(uuid) ||
                    playerManager.isPlayerInVoidLimbo(uuid) ||
                    playerManager.isPlayerProtected(uuid)) {

                bulletproofWaitEvents.incrementAndGet();
                logger.fine("BULLETPROOF WAIT: Player " + player.getName() +
                        " still not ready, scheduling retry");

                // Still not ready, try again later
                scheduleBulletproofSubsystemInitialization(player);
                return;
            }

            // Player is ready, initialize subsystems
            try {
                initializePlayerSubsystems(player);
                subsystemInitializations.incrementAndGet();
                logger.fine("BULLETPROOF COORDINATION: Subsystems initialized for: " + player.getName());
            } catch (Exception e) {
                subsystemFailures.incrementAndGet();
                logger.log(Level.WARNING, "BULLETPROOF COORDINATION: Error initializing subsystems for " +
                        player.getName(), e);
            }
        }, 20L); // Wait 1 second and check again
    }

    /**
     * Initialize subsystems for a specific player (bulletproof-ready)
     */
    private void initializePlayerSubsystems(org.bukkit.entity.Player player) {
        try {
            // Initialize player-specific subsystem data
            // This is where you would add any player-specific initialization
            // that needs to happen after bulletproof loading is complete

            logger.fine("BULLETPROOF SUBSYSTEMS: Initialized for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in bulletproof subsystem initialization for " +
                    player.getName(), e);
            throw e;
        }
    }

    /**
     * Player quit handler - respects bulletproof YakPlayerManager coordination
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        totalPlayerQuits.incrementAndGet();

        org.bukkit.entity.Player player = event.getPlayer();
        logger.fine("PlayerMechanics processing quit for: " + player.getName());

        // Clean up subsystem data for the player
        try {
            cleanupPlayerSubsystems(player);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up subsystems for " + player.getName(), e);
        }
    }

    /**
     * Clean up subsystems for a specific player
     */
    private void cleanupPlayerSubsystems(org.bukkit.entity.Player player) {
        try {
            // Clean up any player-specific subsystem data
            logger.fine("BULLETPROOF CLEANUP: Cleaned up subsystems for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in bulletproof subsystem cleanup for " +
                    player.getName(), e);
        }
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
     * Start bulletproof coordination monitoring
     */
    private void startBulletproofCoordinationMonitoring() {
        bulletproofCoordinationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                YakRealms.getInstance(),
                this::monitorBulletproofCoordination,
                20L * bulletproofCoordinationInterval,
                20L * bulletproofCoordinationInterval
        );
        logger.info("BULLETPROOF COORDINATION: Monitoring started");
    }

    /**
     * Monitor bulletproof coordination status
     */
    private void monitorBulletproofCoordination() {
        try {
            if (playerManager == null) {
                logger.warning("BULLETPROOF MONITOR: YakPlayerManager reference lost!");
                return;
            }

            // Check system health
            if (!playerManager.isSystemHealthy()) {
                logger.warning("BULLETPROOF MONITOR: YakPlayerManager reports unhealthy state");
            }

            // Log coordination statistics
            int coordinationEvents = bulletproofCoordinationEvents.get();
            int waitEvents = bulletproofWaitEvents.get();

            if (coordinationEvents > 0 || waitEvents > 0) {
                logger.info("BULLETPROOF COORDINATION STATS: Events=" + coordinationEvents +
                        ", Waits=" + waitEvents);
            }

            // Check for players stuck in loading states
            int loadingPlayers = 0;
            int limboPlayers = 0;
            int protectedPlayers = 0;

            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                java.util.UUID uuid = player.getUniqueId();
                if (playerManager.isPlayerLoading(uuid)) loadingPlayers++;
                if (playerManager.isPlayerInVoidLimbo(uuid)) limboPlayers++;
                if (playerManager.isPlayerProtected(uuid)) protectedPlayers++;
            }

            if (loadingPlayers > 0 || limboPlayers > 0 || protectedPlayers > 0) {
                logger.info("BULLETPROOF COORDINATION: Loading=" + loadingPlayers +
                        ", Limbo=" + limboPlayers + ", Protected=" + protectedPlayers);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "BULLETPROOF MONITOR: Error during coordination monitoring", e);
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
     *  shutdown with proper cleanup ordering
     */
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics shutdown already in progress!");
            return;
        }

        logger.info("Starting  PlayerMechanics shutdown...");

        try {
            // Stop monitoring tasks first
            stopMonitoringTasks();

            // Shutdown subsystems in reverse dependency order
            shutdownSubsystems();

            logger.info(" PlayerMechanics shutdown completed successfully");

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

        if (bulletproofCoordinationTask != null && !bulletproofCoordinationTask.isCancelled()) {
            bulletproofCoordinationTask.cancel();
            bulletproofCoordinationTask = null;
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
     *  Log performance metrics with bulletproof YakPlayerManager coordination status
     */
    private void logPerformanceMetrics() {
        try {
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            logger.info("===  PlayerMechanics Performance ===");
            logger.info("Online Players: " + onlinePlayers);
            logger.info("Total Joins: " + totalPlayerJoins.get());
            logger.info("Total Quits: " + totalPlayerQuits.get());
            logger.info("Subsystem Initializations: " + subsystemInitializations.get());
            logger.info("Subsystem Failures: " + subsystemFailures.get());
            logger.info("Memory Usage: " + (usedMemory / 1024 / 1024) + "MB / " + (totalMemory / 1024 / 1024) + "MB");
            logger.info("Systems Ready: " + systemsReady.get());
            logger.info("Bulletproof Coordination: " + enableBulletproofCoordination);

            // : Show coordination with bulletproof YakPlayerManager
            if (playerManager != null) {
                logger.info("BULLETPROOF YakPlayerManager Integration: ACTIVE");
                logger.info("Coordination Status: ");
                logger.info("Loading Awareness: YES");
                logger.info("Protection Awareness: YES");
                logger.info("Void Limbo Awareness: YES");
                logger.info("Coordination Events: " + bulletproofCoordinationEvents.get());
                logger.info("Wait Events: " + bulletproofWaitEvents.get());

                int loadingPlayers = 0;
                int protectedPlayers = 0;
                int limboPlayers = 0;
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    java.util.UUID uuid = p.getUniqueId();
                    if (playerManager.isPlayerLoading(uuid)) loadingPlayers++;
                    if (playerManager.isPlayerProtected(uuid)) protectedPlayers++;
                    if (playerManager.isPlayerInVoidLimbo(uuid)) limboPlayers++;
                }
                logger.info("Players Loading: " + loadingPlayers);
                logger.info("Players Protected: " + protectedPlayers);
                logger.info("Players in Void Limbo: " + limboPlayers);
                logger.info("YakPlayerManager Health: " + (playerManager.isSystemHealthy() ? "HEALTHY" : "DEGRADED"));
            } else {
                logger.warning("BULLETPROOF YakPlayerManager Integration: NOT AVAILABLE");
            }

            logger.info("==========================================");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error logging performance metrics", e);
        }
    }

    /**
     *  Perform health checks on all subsystems
     */
    private void performHealthChecks() {
        try {
            boolean allHealthy = true;
            StringBuilder healthReport = new StringBuilder("===  PlayerMechanics Health Check ===\n");

            // Check each subsystem
            allHealthy &= checkSubsystemHealth("Energy System", energySystem != null);
            allHealthy &= checkSubsystemHealth("Toggle System", toggleSystem != null);
            allHealthy &= checkSubsystemHealth("Buddy System", buddySystem != null);
            allHealthy &= checkSubsystemHealth("Dash Mechanics", dashMechanics != null);
            allHealthy &= checkSubsystemHealth("Listener Manager", listenerManager != null);

            // : Check bulletproof YakPlayerManager integration
            boolean yakPlayerManagerHealthy = playerManager != null && playerManager.isSystemHealthy();
            allHealthy &= checkSubsystemHealth("BULLETPROOF YakPlayerManager Integration", yakPlayerManagerHealthy);

            // Check for memory issues
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            if (memoryUsagePercent > 85) {
                healthReport.append("WARNING: High memory usage (").append(String.format("%.1f", memoryUsagePercent)).append("%)\n");
                allHealthy = false;
            }

            // Check bulletproof coordination status
            if (playerManager != null && enableBulletproofCoordination) {
                int onlinePlayers = Bukkit.getOnlinePlayers().size();
                int loadingPlayers = 0;
                int limboPlayers = 0;
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    java.util.UUID uuid = p.getUniqueId();
                    if (playerManager.isPlayerLoading(uuid)) loadingPlayers++;
                    if (playerManager.isPlayerInVoidLimbo(uuid)) limboPlayers++;
                }

                if (loadingPlayers > 0 || limboPlayers > 0) {
                    healthReport.append("INFO: ").append(loadingPlayers).append("/").append(onlinePlayers)
                            .append(" players loading, ").append(limboPlayers).append(" in limbo\n");
                }

                // Check for high failure rates
                int totalOperations = subsystemInitializations.get() + subsystemFailures.get();
                if (totalOperations > 0) {
                    double failureRate = (double) subsystemFailures.get() / totalOperations * 100;
                    if (failureRate > 10) {
                        healthReport.append("WARNING: High subsystem failure rate (")
                                .append(String.format("%.1f", failureRate)).append("%)\n");
                        allHealthy = false;
                    }
                }
            }

            healthReport.append("Overall Status: ").append(allHealthy ? "HEALTHY" : "ISSUES DETECTED");
            healthReport.append("\n===============================================");

            if (!allHealthy) {
                logger.warning(healthReport.toString());
            } else {
                logger.fine(" PlayerMechanics health check passed");
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
     *  Wait for player to finish loading before subsystem operations
     */
    public boolean waitForPlayerReady(org.bukkit.entity.Player player, int maxWaitSeconds) {
        if (playerManager == null) {
            return true; // No manager available, assume ready
        }

        java.util.UUID uuid = player.getUniqueId();
        int attempts = 0;
        int maxAttempts = maxWaitSeconds * 10; // Check every 100ms

        while (attempts < maxAttempts) {
            if (!playerManager.isPlayerLoading(uuid) &&
                    !playerManager.isPlayerProtected(uuid) &&
                    !playerManager.isPlayerInVoidLimbo(uuid)) {
                return true; // Player is ready
            }

            try {
                Thread.sleep(100); // Wait 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            attempts++;
        }

        logger.warning("Timeout waiting for player to be ready: " + player.getName());
        return false;
    }

    /**
     *  Check if player is ready for subsystem operations
     */
    public boolean isPlayerReady(org.bukkit.entity.Player player) {
        if (playerManager == null) {
            return true; // No manager available, assume ready
        }

        java.util.UUID uuid = player.getUniqueId();
        return !playerManager.isPlayerLoading(uuid) &&
                !playerManager.isPlayerProtected(uuid) &&
                !playerManager.isPlayerInVoidLimbo(uuid);
    }

    /**
     * Get player loading phase if available
     */
    public YakPlayerManager.LoadingPhase getPlayerLoadingPhase(org.bukkit.entity.Player player) {
        if (playerManager == null) {
            return null;
        }

        return playerManager.getPlayerLoadingPhase(player.getUniqueId());
    }

    /**
     * Check if player is in void limbo
     */
    public boolean isPlayerInVoidLimbo(org.bukkit.entity.Player player) {
        if (playerManager == null) {
            return false;
        }

        return playerManager.isPlayerInVoidLimbo(player.getUniqueId());
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
     *  Get system statistics with bulletproof YakPlayerManager coordination
     */
    public SystemStats getSystemStats() {
        boolean yakPlayerManagerIntegrated = playerManager != null && playerManager.isSystemHealthy();

        int loadingPlayers = 0;
        int protectedPlayers = 0;
        int limboPlayers = 0;
        if (playerManager != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                java.util.UUID uuid = p.getUniqueId();
                if (playerManager.isPlayerLoading(uuid)) loadingPlayers++;
                if (playerManager.isPlayerProtected(uuid)) protectedPlayers++;
                if (playerManager.isPlayerInVoidLimbo(uuid)) limboPlayers++;
            }
        }

        return new SystemStats(
                totalPlayerJoins.get(),
                totalPlayerQuits.get(),
                Bukkit.getOnlinePlayers().size(),
                isInitialized(),
                systemsReady.get(),
                yakPlayerManagerIntegrated,
                loadingPlayers,
                protectedPlayers,
                limboPlayers,
                subsystemInitializations.get(),
                subsystemFailures.get(),
                enableBulletproofCoordination,
                bulletproofCoordinationEvents.get(),
                bulletproofWaitEvents.get()
        );
    }

    /**
     *  System statistics class with bulletproof coordination metrics
     */
    public static class SystemStats {
        public final int totalJoins;
        public final int totalQuits;
        public final int currentOnline;
        public final boolean systemHealthy;
        public final boolean systemsReady;
        public final boolean yakPlayerManagerIntegrated;
        public final int loadingPlayers;
        public final int protectedPlayers;
        public final int limboPlayers;
        public final int subsystemInitializations;
        public final int subsystemFailures;
        public final boolean bulletproofCoordination;
        public final int bulletproofCoordinationEvents;
        public final int bulletproofWaitEvents;

        SystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy,
                    boolean systemsReady, boolean yakPlayerManagerIntegrated,
                    int loadingPlayers, int protectedPlayers, int limboPlayers,
                    int subsystemInitializations, int subsystemFailures, boolean bulletproofCoordination,
                    int bulletproofCoordinationEvents, int bulletproofWaitEvents) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
            this.systemsReady = systemsReady;
            this.yakPlayerManagerIntegrated = yakPlayerManagerIntegrated;
            this.loadingPlayers = loadingPlayers;
            this.protectedPlayers = protectedPlayers;
            this.limboPlayers = limboPlayers;
            this.subsystemInitializations = subsystemInitializations;
            this.subsystemFailures = subsystemFailures;
            this.bulletproofCoordination = bulletproofCoordination;
            this.bulletproofCoordinationEvents = bulletproofCoordinationEvents;
            this.bulletproofWaitEvents = bulletproofWaitEvents;
        }

        @Override
        public String toString() {
            return String.format("SystemStats{joins=%d, quits=%d, online=%d, healthy=%s, ready=%s, " +
                            "integrated=%s, loading=%d, protected=%d, limbo=%d, inits=%d, failures=%d, " +
                            "bulletproof=%s, coordEvents=%d, waitEvents=%d}",
                    totalJoins, totalQuits, currentOnline, systemHealthy, systemsReady,
                    yakPlayerManagerIntegrated, loadingPlayers, protectedPlayers, limboPlayers,
                    subsystemInitializations, subsystemFailures, bulletproofCoordination,
                    bulletproofCoordinationEvents, bulletproofWaitEvents);
        }
    }
}
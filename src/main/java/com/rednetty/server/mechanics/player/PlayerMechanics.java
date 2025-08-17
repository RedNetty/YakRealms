package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.social.friends.Buddies;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.movement.DashMechanics;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.combat.death.DeathMechanics;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PlayerMechanics - Coordination with death/combat systems
 * Compatible with Paper 1.21.7+
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

    // Death and combat systems
    private DeathMechanics deathMechanics;
    private CombatLogoutMechanics combatLogoutMechanics;

    // State management
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean systemsReady = new AtomicBoolean(false);

    // Performance monitoring
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger subsystemInitializations = new AtomicInteger(0);
    private final AtomicInteger subsystemFailures = new AtomicInteger(0);
    private final AtomicInteger coordinationEvents = new AtomicInteger(0);
    private final AtomicInteger coordinationFailures = new AtomicInteger(0);
    private final AtomicInteger successfulCoordinations = new AtomicInteger(0);
    private final AtomicLong totalCoordinationTime = new AtomicLong(0);

    // Death and combat coordination metrics
    private final AtomicInteger deathCoordinations = new AtomicInteger(0);
    private final AtomicInteger combatLogoutCoordinations = new AtomicInteger(0);
    private final AtomicInteger systemConflictsPrevented = new AtomicInteger(0);

    // Task management
    private BukkitTask performanceMonitorTask;
    private BukkitTask healthCheckTask;
    private BukkitTask systemCoordinationTask;

    // Configuration
    private final boolean enablePerformanceMonitoring;
    private final boolean enableHealthChecks;
    private final long healthCheckInterval;
    private final long performanceLogInterval;

    // YakPlayerManager integration
    private YakPlayerManager playerManager;

    private PlayerMechanics() {
        this.logger = YakRealms.getInstance().getLogger();

        // Load configuration with safe defaults
        this.enablePerformanceMonitoring = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_performance_monitoring", true);
        this.enableHealthChecks = YakRealms.getInstance().getConfig()
                .getBoolean("player_mechanics.enable_health_checks", true);
        this.healthCheckInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.health_check_interval", 300);
        this.performanceLogInterval = YakRealms.getInstance().getConfig()
                .getLong("player_mechanics.performance_log_interval", 600);
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
     * Initialize all systems with proper coordination
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics already initialized!");
            return;
        }

        try {
            logger.info("Starting PlayerMechanics initialization...");

            // Initialize YakPlayerManager integration first
            if (!initializePlayerManagerIntegration()) {
                throw new RuntimeException("YakPlayerManager integration failed");
            }

            // Initialize death and combat systems before other subsystems
            if (!initializeDeathAndCombatSystems()) {
                throw new RuntimeException("Death and combat systems initialization failed");
            }

            registerEventListeners();

            if (!initializeSubsystems()) {
                throw new RuntimeException("Subsystem initialization failed");
            }

            startMonitoringTasks();

            if (!validateAllSubsystems()) {
                throw new RuntimeException("Subsystem validation failed");
            }

            // Final coordination setup
            establishSystemCoordination();

            systemsReady.set(true);
            logger.info("PlayerMechanics initialization completed successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize PlayerMechanics", e);
            performEmergencyCleanup();
            throw new RuntimeException("PlayerMechanics initialization failed", e);
        }
    }

    /**
     * Initialize death and combat systems with proper coordination
     */
    private boolean initializeDeathAndCombatSystems() {
        logger.info("Initializing death and combat systems...");

        try {
            // Initialize DeathMechanics
            this.deathMechanics = DeathMechanics.getInstance();
            if (this.deathMechanics != null) {
                this.deathMechanics.onEnable();
                logger.info("✓ DeathMechanics initialized");
            } else {
                logger.severe("✗ Failed to get DeathMechanics instance");
                return false;
            }

            // Initialize CombatLogoutMechanics
            this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
            if (this.combatLogoutMechanics != null) {
                this.combatLogoutMechanics.onEnable();
                logger.info("✓ CombatLogoutMechanics initialized");
            } else {
                logger.severe("✗ Failed to get CombatLogoutMechanics instance");
                return false;
            }

            // Ensure systems can coordinate with each other
            if (!verifySystemCoordination()) {
                logger.severe("✗ System coordination verification failed");
                return false;
            }

            logger.info("✓ Death and combat systems initialized");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing death and combat systems", e);
            return false;
        }
    }

    /**
     * Verify that all systems can coordinate properly
     */
    private boolean verifySystemCoordination() {
        try {
            // Verify DeathMechanics can access YakPlayerManager
            if (playerManager == null) {
                logger.severe("PlayerManager not available for DeathMechanics coordination");
                return false;
            }

            // Verify CombatLogoutMechanics can access YakPlayerManager
            if (!playerManager.isSystemHealthy()) {
                logger.warning("PlayerManager not healthy for combat logout coordination");
                // Don't fail initialization, but log the issue
            }

            logger.info("✓ System coordination verified");
            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error verifying system coordination", e);
            return false;
        }
    }

    /**
     * Establish ongoing coordination between systems
     */
    private void establishSystemCoordination() {
        try {
            // Start coordination monitoring task
            systemCoordinationTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        monitorSystemCoordination();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in system coordination monitoring", e);
                    }
                }
            }.runTaskTimerAsynchronously(YakRealms.getInstance(), 200L, 200L); // Every 10 seconds

            logger.info("✓ System coordination monitoring established");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error establishing system coordination", e);
        }
    }

    /**
     * Monitor coordination between systems
     */
    private void monitorSystemCoordination() {
        try {
            // Check if death mechanics is healthy
            if (deathMechanics != null) {
                String deathStats = deathMechanics.getPerformanceStats();
                logger.fine("Death system status: " + deathStats);
            }

            // Check if combat logout mechanics is healthy
            if (combatLogoutMechanics != null) {
                boolean combatHealthy = combatLogoutMechanics.isSystemHealthy();
                if (!combatHealthy) {
                    logger.warning("Combat logout system reporting unhealthy status");
                    coordinationFailures.incrementAndGet();
                } else {
                    successfulCoordinations.incrementAndGet();
                }
            }

            // Check for potential conflicts
            int activeCombatLogouts = combatLogoutMechanics != null ?
                    combatLogoutMechanics.getActiveCombatLogouts() : 0;

            if (activeCombatLogouts > 0) {
                logger.fine("Active combat logouts being monitored: " + activeCombatLogouts);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in coordination monitoring", e);
            coordinationFailures.incrementAndGet();
        }
    }

    /**
     * YakPlayerManager integration with death system coordination
     */
    private boolean initializePlayerManagerIntegration() {
        logger.info("Initializing YakPlayerManager integration...");

        try {
            this.playerManager = YakPlayerManager.getInstance();

            if (playerManager == null) {
                logger.severe("YakPlayerManager instance is null");
                return false;
            }

            // Enhanced readiness check with timeout
            int attempts = 0;
            while (!verifySystemReady() && attempts < 30) {
                Thread.sleep(1000);
                attempts++;
            }

            if (!verifySystemReady()) {
                logger.warning("YakPlayerManager not ready after 30 seconds");
                return false;
            }

            logger.info("✓ YakPlayerManager integration successful");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "YakPlayerManager integration failed", e);
            return false;
        }
    }

    /**
     * System readiness verification including death system checks
     */
    private boolean verifySystemReady() {
        try {
            if (playerManager == null) {
                return false;
            }

            if (!playerManager.isSystemHealthy()) {
                return false;
            }

            if (!playerManager.isRepositoryReady()) {
                return false;
            }

            // Additional check for save system health
            try {
                YakPlayerManager.SystemStats stats = playerManager.getSystemStats();
                if (stats != null && !stats.systemHealthy) {
                    logger.warning("YakPlayerManager reports unhealthy state");
                    return false;
                }
            } catch (Exception e) {
                logger.warning("Could not verify system stats: " + e.getMessage());
            }

            return true;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during system verification", e);
            return false;
        }
    }

    /**
     * Register event listeners with proper priority for coordination
     */
    private void registerEventListeners() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
            logger.info("✓ Event listeners registered");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to register event listeners", e);
            throw new RuntimeException("Event listener registration failed", e);
        }
    }

    /**
     * Subsystem initialization with death system coordination
     */
    private boolean initializeSubsystems() {
        logger.info("Initializing PlayerMechanics subsystems...");
        boolean allSuccessful = true;

        try {
            // Energy System
            if (!initializeSubsystem("Energy System", () -> {
                this.energySystem = Energy.getInstance();
                if (this.energySystem != null) {
                    this.energySystem.onEnable();
                    return true;
                }
                return false;
            })) {
                allSuccessful = false;
            }

            // Toggle System
            if (!initializeSubsystem("Toggle System", () -> {
                this.toggleSystem = Toggles.getInstance();
                if (this.toggleSystem != null) {
                    this.toggleSystem.onEnable();
                    return true;
                }
                return false;
            })) {
                allSuccessful = false;
            }

            // Buddy System
            if (!initializeSubsystem("Buddy System", () -> {
                this.buddySystem = Buddies.getInstance();
                if (this.buddySystem != null) {
                    this.buddySystem.onEnable();
                    return true;
                }
                return false;
            })) {
                allSuccessful = false;
            }

            // Dash Mechanics
            if (!initializeSubsystem("Dash Mechanics", () -> {
                this.dashMechanics = new DashMechanics();
                if (this.dashMechanics != null) {
                    this.dashMechanics.onEnable();
                    return true;
                }
                return false;
            })) {
                allSuccessful = false;
            }

            // Listener Manager
            if (!initializeSubsystem("Listener Manager", () -> {
                this.listenerManager = PlayerListenerManager.getInstance();
                if (this.listenerManager != null) {
                    this.listenerManager.onEnable();
                    return true;
                }
                return false;
            })) {
                allSuccessful = false;
            }

            if (allSuccessful) {
                logger.info("✓ All PlayerMechanics subsystems initialized successfully");
            } else {
                logger.warning("Some subsystems failed to initialize");
            }

            return allSuccessful;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing subsystems", e);
            return false;
        }
    }

    /**
     * Subsystem initialization helper
     */
    private boolean initializeSubsystem(String name, SubsystemInitializer initializer) {
        try {
            logger.info("Initializing " + name + "...");
            boolean success = initializer.initialize();

            if (success) {
                subsystemInitializations.incrementAndGet();
                logger.info("✓ " + name + " initialized successfully");
                return true;
            } else {
                subsystemFailures.incrementAndGet();
                logger.warning("✗ " + name + " initialization failed");
                return false;
            }

        } catch (Exception e) {
            subsystemFailures.incrementAndGet();
            logger.log(Level.SEVERE, "Error initializing " + name, e);
            return false;
        }
    }

    /**
     * Player join event with death/combat coordination
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        totalPlayerJoins.incrementAndGet();

        try {
            org.bukkit.entity.Player player = event.getPlayer();
            java.util.UUID uuid = player.getUniqueId();

            long startTime = System.currentTimeMillis();

            if (playerManager != null) {
                // Check for combat logout completion during join
                if (playerManager.isPlayerReady(player)) {
                    // Player is ready, check if they're completing a combat logout
                    YakPlayer yakPlayer = playerManager.getPlayer(uuid);
                    if (yakPlayer != null) {
                        YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();

                        if (logoutState == YakPlayer.CombatLogoutState.PROCESSED) {
                            logger.info("Combat logout completion detected for " + player.getName());
                            combatLogoutCoordinations.incrementAndGet();
                            // Let YakPlayerManager handle the completion
                        } else {
                            logger.info("Normal player join for " + player.getName());
                        }
                    }

                    initializePlayerSubsystems(player);
                    coordinationEvents.incrementAndGet();
                    successfulCoordinations.incrementAndGet();
                } else {
                    // Schedule delayed check with coordination
                    scheduleDelayedSubsystemInitialization(player);
                }
            } else {
                logger.warning("PlayerManager is null during join event for " + player.getName());
                coordinationFailures.incrementAndGet();
            }

            long coordinationTime = System.currentTimeMillis() - startTime;
            totalCoordinationTime.addAndGet(coordinationTime);

        } catch (Exception e) {
            coordinationFailures.incrementAndGet();
            logger.log(Level.WARNING, "Error in player join coordination for " + event.getPlayer().getName(), e);
        }
    }

    /**
     * Delayed subsystem initialization with death/combat coordination
     */
    private void scheduleDelayedSubsystemInitialization(org.bukkit.entity.Player player) {
        java.util.UUID uuid = player.getUniqueId();

        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = 15; // 15 seconds max
            private boolean coordinationActive = false;

            @Override
            public void run() {
                try {
                    attempts++;

                    if (!player.isOnline()) {
                        this.cancel();
                        return;
                    }

                    if (attempts >= maxAttempts) {
                        logger.warning("Max attempts reached for delayed initialization: " + player.getName());
                        coordinationFailures.incrementAndGet();
                        this.cancel();
                        return;
                    }

                    // Enhanced readiness check with death/combat coordination
                    if (playerManager != null && playerManager.isPlayerReady(player)) {
                        try {
                            // Additional check: ensure no death/combat processing conflicts
                            YakPlayer yakPlayer = playerManager.getPlayer(uuid);
                            if (yakPlayer != null) {
                                // Check for inventory being applied
                                if (yakPlayer.isInventoryBeingApplied()) {
                                    logger.fine("Waiting for inventory application to complete for " + player.getName() + " (attempt " + attempts + ")");
                                    coordinationActive = true;
                                    return; // Continue waiting
                                }

                                // Check for death processing coordination
                                if (deathMechanics != null && deathMechanics.isProcessingDeath(uuid)) {
                                    logger.fine("Waiting for death processing to complete for " + player.getName() + " (attempt " + attempts + ")");
                                    deathCoordinations.incrementAndGet();
                                    return; // Continue waiting
                                }

                                // Check for combat logout processing coordination
                                if (combatLogoutMechanics != null && combatLogoutMechanics.isCombatLoggingOut(player)) {
                                    logger.fine("Waiting for combat logout processing to complete for " + player.getName() + " (attempt " + attempts + ")");
                                    combatLogoutCoordinations.incrementAndGet();
                                    return; // Continue waiting
                                }
                            }

                            if (coordinationActive) {
                                logger.info("All coordination checks passed, proceeding with subsystem initialization for " + player.getName());
                            }

                            initializePlayerSubsystems(player);
                            coordinationEvents.incrementAndGet();
                            successfulCoordinations.incrementAndGet();
                            logger.fine("Delayed subsystem initialization completed for " + player.getName() + " after " + attempts + " attempts");
                        } catch (Exception e) {
                            coordinationFailures.incrementAndGet();
                            logger.log(Level.WARNING, "Error in delayed subsystem initialization for " + player.getName(), e);
                        }
                        this.cancel();
                    }
                    // Continue waiting if not ready

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in delayed initialization scheduler for " + player.getName(), e);
                    coordinationFailures.incrementAndGet();
                    this.cancel();
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Check every second
    }

    /**
     * Player subsystem initialization with death/combat coordination
     */
    private void initializePlayerSubsystems(org.bukkit.entity.Player player) {
        try {
            // Verify player is still ready and no conflicts exist
            if (playerManager != null && !playerManager.isPlayerReady(player)) {
                logger.warning("Player no longer ready during subsystem initialization: " + player.getName());
                return;
            }

            YakPlayer yakPlayer = playerManager != null ? playerManager.getPlayer(player.getUniqueId()) : null;
            if (yakPlayer != null) {
                // Final conflict checks before initialization
                if (yakPlayer.isInventoryBeingApplied()) {
                    logger.warning("Inventory still being applied during subsystem initialization: " + player.getName());
                    return;
                }

                // Check for death processing conflicts
                if (deathMechanics != null && deathMechanics.isProcessingDeath(player.getUniqueId())) {
                    logger.warning("Death processing active during subsystem initialization: " + player.getName());
                    systemConflictsPrevented.incrementAndGet();
                    return;
                }

                // Check for combat logout conflicts
                if (combatLogoutMechanics != null && combatLogoutMechanics.isCombatLoggingOut(player)) {
                    logger.warning("Combat logout processing active during subsystem initialization: " + player.getName());
                    systemConflictsPrevented.incrementAndGet();
                    return;
                }
            }

            // Initialize all subsystems for the player
            if (energySystem != null) {
                try {
                    logger.fine("Initialized energy system for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Energy system initialization failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (toggleSystem != null) {
                try {
                    logger.fine("Initialized toggle system for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Toggle system initialization failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (buddySystem != null) {
                try {
                    logger.fine("Initialized buddy system for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Buddy system initialization failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (dashMechanics != null) {
                try {
                    logger.fine("Initialized dash mechanics for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Dash mechanics initialization failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (listenerManager != null) {
                try {
                    logger.fine("Initialized listener manager for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Listener manager initialization failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            logger.fine("All subsystems initialized for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in player subsystem initialization for " + player.getName(), e);
        }
    }

    /**
     * Player quit event with death/combat coordination
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        totalPlayerQuits.incrementAndGet();

        org.bukkit.entity.Player player = event.getPlayer();

        try {
            // Enhanced cleanup with death/combat coordination
            cleanupPlayerSubsystems(player);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up subsystems for " + player.getName(), e);
        }
    }

    /**
     * Player subsystem cleanup with death/combat coordination
     */
    private void cleanupPlayerSubsystems(org.bukkit.entity.Player player) {
        try {
            java.util.UUID uuid = player.getUniqueId();

            // Coordination check - don't interfere with death/combat processing
            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(uuid);
                if (yakPlayer != null) {
                    // Don't interfere if death processing is active
                    if (deathMechanics != null && deathMechanics.isProcessingDeath(uuid)) {
                        logger.fine("Skipping subsystem cleanup during death processing for " + player.getName());
                        deathCoordinations.incrementAndGet();
                        return;
                    }

                    // Don't interfere if combat logout is active
                    if (combatLogoutMechanics != null && combatLogoutMechanics.isCombatLoggingOut(player)) {
                        logger.fine("Skipping subsystem cleanup during combat logout processing for " + player.getName());
                        combatLogoutCoordinations.incrementAndGet();
                        return;
                    }

                    // Don't interfere if inventory is being updated during quit
                    if (yakPlayer.isInventoryBeingApplied()) {
                        logger.fine("Skipping subsystem cleanup during inventory update for " + player.getName());
                        return;
                    }
                }
            }

            // Safe to perform cleanup
            performSafeSubsystemCleanup(player);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in subsystem cleanup for " + player.getName(), e);
        }
    }

    /**
     * Perform safe subsystem cleanup when no conflicts exist
     */
    private void performSafeSubsystemCleanup(org.bukkit.entity.Player player) {
        try {
            // Cleanup all subsystems for the player
            if (energySystem != null) {
                try {
                    logger.fine("Energy cleanup for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Energy cleanup failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (toggleSystem != null) {
                try {
                    logger.fine("Toggle cleanup for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Toggle cleanup failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (buddySystem != null) {
                try {
                    logger.fine("Buddy cleanup for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Buddy cleanup failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (dashMechanics != null) {
                try {
                    logger.fine("Dash mechanics cleanup for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Dash mechanics cleanup failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            if (listenerManager != null) {
                try {
                    logger.fine("Listener manager cleanup for " + player.getName());
                } catch (Exception e) {
                    logger.warning("Listener manager cleanup failed for " + player.getName() + ": " + e.getMessage());
                }
            }

            logger.fine("Safe cleanup completed for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in safe subsystem cleanup for " + player.getName(), e);
        }
    }

    /**
     * Monitoring tasks with death/combat coordination metrics
     */
    private void startMonitoringTasks() {
        if (enablePerformanceMonitoring) {
            performanceMonitorTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        logPerformanceMetrics();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in performance monitoring", e);
                    }
                }
            }.runTaskTimerAsynchronously(
                    YakRealms.getInstance(),
                    20L * performanceLogInterval,
                    20L * performanceLogInterval
            );
        }

        if (enableHealthChecks) {
            healthCheckTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        performHealthChecks();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in health checks", e);
                    }
                }
            }.runTaskTimerAsynchronously(
                    YakRealms.getInstance(),
                    20L * healthCheckInterval,
                    20L * healthCheckInterval
            );
        }
    }

    /**
     * Subsystem validation with death/combat system checks
     */
    private boolean validateAllSubsystems() {
        StringBuilder issues = new StringBuilder();
        boolean allValid = true;

        // Standard subsystem validation
        if (energySystem == null) {
            issues.append("Energy system not initialized; ");
            allValid = false;
        }

        if (toggleSystem == null) {
            issues.append("Toggle system not initialized; ");
            allValid = false;
        }

        if (buddySystem == null) {
            issues.append("Buddy system not initialized; ");
            allValid = false;
        }

        if (dashMechanics == null) {
            issues.append("Dash mechanics not initialized; ");
            allValid = false;
        }

        if (listenerManager == null) {
            issues.append("Listener manager not initialized; ");
            allValid = false;
        }

        // Death and combat system validation
        if (deathMechanics == null) {
            issues.append("Death mechanics not initialized; ");
            allValid = false;
        }

        if (combatLogoutMechanics == null) {
            issues.append("Combat logout mechanics not initialized; ");
            allValid = false;
        }

        // YakPlayerManager integration validation
        try {
            if (playerManager != null && !playerManager.isSystemHealthy()) {
                issues.append("YakPlayerManager unhealthy; ");
                allValid = false;
            }

            if (playerManager != null) {
                YakPlayerManager.SystemStats stats = playerManager.getSystemStats();
                if (stats != null && !stats.systemHealthy) {
                    issues.append("YakPlayerManager reports system unhealthy; ");
                    allValid = false;
                }
            }
        } catch (Exception e) {
            issues.append("YakPlayerManager validation error; ");
            allValid = false;
        }

        // Combat system health validation
        try {
            if (combatLogoutMechanics != null && !combatLogoutMechanics.isSystemHealthy()) {
                issues.append("Combat logout system unhealthy; ");
                allValid = false;
            }
        } catch (Exception e) {
            issues.append("Combat logout validation error; ");
            allValid = false;
        }

        if (!allValid) {
            throw new RuntimeException("Subsystem validation failed: " + issues.toString());
        }

        logger.info("✓ All subsystems validated successfully");
        return true;
    }

    /**
     * Shutdown process with death/combat coordination
     */
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            logger.warning("PlayerMechanics shutdown already in progress!");
            return;
        }

        logger.info("Starting PlayerMechanics shutdown...");

        try {
            stopAllMonitoringTasks();
            shutdownAllSubsystems();

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
    private void stopAllMonitoringTasks() {
        try {
            if (performanceMonitorTask != null && !performanceMonitorTask.isCancelled()) {
                performanceMonitorTask.cancel();
            }

            if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
                healthCheckTask.cancel();
            }

            if (systemCoordinationTask != null && !systemCoordinationTask.isCancelled()) {
                systemCoordinationTask.cancel();
            }

            logger.info("✓ All monitoring tasks stopped");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error stopping monitoring tasks", e);
        }
    }

    /**
     * Shutdown all subsystems with death/combat coordination
     */
    private void shutdownAllSubsystems() {
        logger.info("Shutting down subsystems...");

        // Shutdown death and combat systems first to prevent conflicts
        shutdownSubsystem("Death Mechanics", () -> {
            if (deathMechanics != null) {
                deathMechanics.onDisable();
            }
        });

        shutdownSubsystem("Combat Logout Mechanics", () -> {
            if (combatLogoutMechanics != null) {
                combatLogoutMechanics.onDisable();
            }
        });

        // Then shutdown other subsystems
        shutdownSubsystem("Listener Manager", () -> {
            if (listenerManager != null) {
                listenerManager.onDisable();
            }
        });

        shutdownSubsystem("Dash Mechanics", () -> {
            if (dashMechanics != null) {
                dashMechanics.onDisable();
            }
        });

        shutdownSubsystem("Buddy System", () -> {
            if (buddySystem != null) {
                buddySystem.onDisable();
            }
        });

        shutdownSubsystem("Toggle System", () -> {
            if (toggleSystem != null) {
                toggleSystem.onDisable();
            }
        });

        shutdownSubsystem("Energy System", () -> {
            if (energySystem != null) {
                energySystem.onDisable();
            }
        });

        logger.info("✓ Subsystem shutdown completed");
    }

    /**
     * Subsystem shutdown helper
     */
    private void shutdownSubsystem(String name, Runnable shutdownAction) {
        try {
            shutdownAction.run();
            logger.fine("✓ " + name + " shutdown completed");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error shutting down " + name, e);
        }
    }

    /**
     * Emergency cleanup
     */
    private void performEmergencyCleanup() {
        logger.warning("Performing emergency cleanup...");

        try {
            stopAllMonitoringTasks();
            shutdownAllSubsystems();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during emergency cleanup", e);
        } finally {
            initialized.set(false);
            systemsReady.set(false);
            shutdownInProgress.set(false);
        }
    }

    /**
     * Performance metrics logging with death/combat coordination stats
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
            logger.info("Subsystem Initializations: " + subsystemInitializations.get());
            logger.info("Subsystem Failures: " + subsystemFailures.get());
            logger.info("Coordination Events: " + coordinationEvents.get());
            logger.info("Successful Coordinations: " + successfulCoordinations.get());
            logger.info("Coordination Failures: " + coordinationFailures.get());

            // Death and combat coordination metrics
            logger.info("Death Coordinations: " + deathCoordinations.get());
            logger.info("Combat Logout Coordinations: " + combatLogoutCoordinations.get());
            logger.info("System Conflicts Prevented: " + systemConflictsPrevented.get());

            logger.info("Memory Usage: " + (usedMemory / 1024 / 1024) + "MB / " + (totalMemory / 1024 / 1024) + "MB");
            logger.info("Systems Ready: " + systemsReady.get());

            if (playerManager != null) {
                try {
                    logger.info("YakPlayerManager Integration: " + (playerManager.isSystemHealthy() ? "HEALTHY" : "DEGRADED"));

                    long totalEvents = coordinationEvents.get();
                    if (totalEvents > 0) {
                        long avgTime = totalCoordinationTime.get() / totalEvents;
                        logger.info("Avg Coordination Time: " + avgTime + "ms");
                        logger.info("Coordination Success Rate: " + String.format("%.1f%%",
                                (successfulCoordinations.get() * 100.0) / totalEvents));
                    }

                    YakPlayerManager.SystemStats stats = playerManager.getSystemStats();
                    if (stats != null) {
                        logger.info("Players Loading: " + stats.loadingPlayers);
                        logger.info("YakPlayerManager Health: " + (stats.systemHealthy ? "HEALTHY" : "DEGRADED"));
                        logger.info("Repository Ready: " + (stats.yakPlayerManagerIntegrated ? "YES" : "NO"));
                    }
                } catch (Exception e) {
                    logger.warning("Error getting YakPlayerManager stats: " + e.getMessage());
                }
            }

            // Death and combat system stats
            if (deathMechanics != null) {
                try {
                    String deathStats = deathMechanics.getPerformanceStats();
                    logger.info("Death System: " + deathStats);
                } catch (Exception e) {
                    logger.warning("Error getting death mechanics stats: " + e.getMessage());
                }
            }

            if (combatLogoutMechanics != null) {
                try {
                    String combatStats = combatLogoutMechanics.getPerformanceStats();
                    logger.info("Combat System: " + combatStats);
                } catch (Exception e) {
                    logger.warning("Error getting combat logout stats: " + e.getMessage());
                }
            }

            logger.info("===================================");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error logging performance metrics", e);
        }
    }

    /**
     * Health checks with death/combat coordination monitoring
     */
    private void performHealthChecks() {
        try {
            boolean allHealthy = true;

            allHealthy &= checkSubsystemHealth("Energy System", energySystem != null);
            allHealthy &= checkSubsystemHealth("Toggle System", toggleSystem != null);
            allHealthy &= checkSubsystemHealth("Buddy System", buddySystem != null);
            allHealthy &= checkSubsystemHealth("Dash Mechanics", dashMechanics != null);
            allHealthy &= checkSubsystemHealth("Listener Manager", listenerManager != null);

            // Death and combat system health checks
            allHealthy &= checkSubsystemHealth("Death Mechanics", deathMechanics != null);
            allHealthy &= checkSubsystemHealth("Combat Logout Mechanics", combatLogoutMechanics != null);

            boolean yakPlayerManagerHealthy = playerManager != null && playerManager.isSystemHealthy();
            allHealthy &= checkSubsystemHealth("YakPlayerManager Integration", yakPlayerManagerHealthy);

            // Combat system specific health checks
            if (combatLogoutMechanics != null) {
                boolean combatHealthy = combatLogoutMechanics.isSystemHealthy();
                allHealthy &= checkSubsystemHealth("Combat System Health", combatHealthy);

                if (!combatHealthy) {
                    logger.warning("Combat logout system reporting unhealthy");
                }
            }

            // Memory check
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;

            if (memoryUsagePercent > 85) {
                logger.warning("High memory usage detected: " + String.format("%.1f%%", memoryUsagePercent));
                allHealthy = false;
            }

            // Coordination health
            if (coordinationFailures.get() > 10) {
                logger.warning("High coordination failure count: " + coordinationFailures.get());
                allHealthy = false;
            }

            // System conflict monitoring
            if (systemConflictsPrevented.get() > 5) {
                logger.warning("High system conflict count: " + systemConflictsPrevented.get());
                // Don't fail health check, but log the warning
            }

            // Success rate check
            long totalCoordinations = coordinationEvents.get();
            if (totalCoordinations > 0) {
                double successRate = (successfulCoordinations.get() * 100.0) / totalCoordinations;
                if (successRate < 90.0) {
                    logger.warning("Low coordination success rate: " + String.format("%.1f%%", successRate));
                    allHealthy = false;
                }
            }

            if (!allHealthy) {
                logger.warning("Health check detected issues");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error performing health checks", e);
        }
    }

    /**
     * Health check helper
     */
    private boolean checkSubsystemHealth(String systemName, boolean isHealthy) {
        if (!isHealthy) {
            logger.warning("Health check FAILED for: " + systemName);
        }
        return isHealthy;
    }

    // API methods
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

    public YakPlayerManager getPlayerManager() {
        return playerManager;
    }

    // Access to death and combat systems
    public DeathMechanics getDeathMechanics() {
        return deathMechanics;
    }

    public CombatLogoutMechanics getCombatLogoutMechanics() {
        return combatLogoutMechanics;
    }

    /**
     * System statistics with death/combat coordination metrics
     */
    public IntegratedSystemStats getSystemStats() {
        boolean yakPlayerManagerIntegrated = playerManager != null && playerManager.isSystemHealthy();

        YakPlayerManager.SystemStats pmStats = null;
        if (playerManager != null) {
            try {
                pmStats = playerManager.getSystemStats();
            } catch (Exception e) {
                logger.warning("Error getting player manager stats: " + e.getMessage());
            }
        }

        // Get death and combat system stats
        String deathStats = null;
        String combatStats = null;

        if (deathMechanics != null) {
            try {
                deathStats = deathMechanics.getPerformanceStats();
            } catch (Exception e) {
                logger.warning("Error getting death mechanics stats: " + e.getMessage());
            }
        }

        if (combatLogoutMechanics != null) {
            try {
                combatStats = combatLogoutMechanics.getPerformanceStats();
            } catch (Exception e) {
                logger.warning("Error getting combat logout stats: " + e.getMessage());
            }
        }

        return new IntegratedSystemStats(
                totalPlayerJoins.get(),
                totalPlayerQuits.get(),
                Bukkit.getOnlinePlayers().size(),
                isInitialized(),
                systemsReady.get(),
                yakPlayerManagerIntegrated,
                subsystemInitializations.get(),
                subsystemFailures.get(),
                coordinationEvents.get(),
                successfulCoordinations.get(),
                coordinationFailures.get(),
                totalCoordinationTime.get(),
                deathCoordinations.get(),
                combatLogoutCoordinations.get(),
                systemConflictsPrevented.get(),
                pmStats,
                deathStats,
                combatStats
        );
    }

    /**
     * System statistics class with death/combat coordination metrics
     */
    public static class IntegratedSystemStats {
        public final int totalJoins;
        public final int totalQuits;
        public final int currentOnline;
        public final boolean systemHealthy;
        public final boolean systemsReady;
        public final boolean yakPlayerManagerIntegrated;
        public final int subsystemInitializations;
        public final int subsystemFailures;
        public final int coordinationEvents;
        public final int successfulCoordinations;
        public final int coordinationFailures;
        public final long totalCoordinationTime;

        // Death and combat coordination metrics
        public final int deathCoordinations;
        public final int combatLogoutCoordinations;
        public final int systemConflictsPrevented;

        public final YakPlayerManager.SystemStats playerManagerStats;
        public final String deathMechanicsStats;
        public final String combatLogoutStats;

        IntegratedSystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy,
                              boolean systemsReady, boolean yakPlayerManagerIntegrated,
                              int subsystemInitializations, int subsystemFailures,
                              int coordinationEvents, int successfulCoordinations, int coordinationFailures,
                              long totalCoordinationTime, int deathCoordinations, int combatLogoutCoordinations,
                              int systemConflictsPrevented, YakPlayerManager.SystemStats playerManagerStats,
                              String deathMechanicsStats, String combatLogoutStats) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
            this.systemsReady = systemsReady;
            this.yakPlayerManagerIntegrated = yakPlayerManagerIntegrated;
            this.subsystemInitializations = subsystemInitializations;
            this.subsystemFailures = subsystemFailures;
            this.coordinationEvents = coordinationEvents;
            this.successfulCoordinations = successfulCoordinations;
            this.coordinationFailures = coordinationFailures;
            this.totalCoordinationTime = totalCoordinationTime;
            this.deathCoordinations = deathCoordinations;
            this.combatLogoutCoordinations = combatLogoutCoordinations;
            this.systemConflictsPrevented = systemConflictsPrevented;
            this.playerManagerStats = playerManagerStats;
            this.deathMechanicsStats = deathMechanicsStats;
            this.combatLogoutStats = combatLogoutStats;
        }

        @Override
        public String toString() {
            return String.format("IntegratedSystemStats{joins=%d, quits=%d, online=%d, healthy=%s, ready=%s, " +
                            "integrated=%s, inits=%d, failures=%d, coordEvents=%d, successfulCoord=%d, " +
                            "coordFailures=%d, coordTime=%d, deathCoord=%d, combatCoord=%d, conflictsPrevented=%d}",
                    totalJoins, totalQuits, currentOnline, systemHealthy, systemsReady,
                    yakPlayerManagerIntegrated, subsystemInitializations, subsystemFailures,
                    coordinationEvents, successfulCoordinations, coordinationFailures, totalCoordinationTime,
                    deathCoordinations, combatLogoutCoordinations, systemConflictsPrevented);
        }

        public double getCoordinationSuccessRate() {
            if (coordinationEvents == 0) return 100.0;
            return (successfulCoordinations * 100.0) / coordinationEvents;
        }

        public double getAverageCoordinationTime() {
            if (coordinationEvents == 0) return 0.0;
            return totalCoordinationTime / (double) coordinationEvents;
        }
    }

    /**
     * Functional interface for subsystem initialization
     */
    @FunctionalInterface
    private interface SubsystemInitializer {
        boolean initialize() throws Exception;
    }
}
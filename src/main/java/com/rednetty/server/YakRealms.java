package com.rednetty.server;

import com.rednetty.server.core.commands.economy.*;
import com.rednetty.server.core.commands.party.*;
import com.rednetty.server.core.commands.player.*;
import com.rednetty.server.core.commands.staff.*;
import com.rednetty.server.core.commands.staff.admin.*;
import com.rednetty.server.core.commands.utility.InvseeCommand;
import com.rednetty.server.core.commands.utility.ItemCommand;
import com.rednetty.server.core.commands.utility.OrbCommand;
import com.rednetty.server.core.commands.utility.TeleportCommand;
import com.rednetty.server.core.commands.world.NodeMapCommand;
import com.rednetty.server.core.commands.world.TrailCommand;
import com.rednetty.server.core.database.MongoDBManager;
import com.rednetty.server.core.mechanics.chat.ChatMechanics;
import com.rednetty.server.core.mechanics.combat.CombatMechanics;
import com.rednetty.server.core.mechanics.combat.MagicStaff;
import com.rednetty.server.core.mechanics.combat.death.DeathMechanics;
import com.rednetty.server.core.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.core.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.core.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.core.mechanics.economy.BankManager;
import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.economy.GemPouchManager;
import com.rednetty.server.core.mechanics.economy.market.MarketManager;
import com.rednetty.server.core.mechanics.economy.merchant.MerchantSystem;
import com.rednetty.server.core.mechanics.economy.vendors.VendorManager;
import com.rednetty.server.core.mechanics.economy.vendors.VendorSystemManager;
import com.rednetty.server.core.mechanics.item.Journal;
import com.rednetty.server.core.mechanics.item.MenuItemManager;
import com.rednetty.server.core.mechanics.item.MenuSystemInitializer;
import com.rednetty.server.core.mechanics.item.crates.CrateManager;
import com.rednetty.server.core.mechanics.item.drops.DropsHandler;
import com.rednetty.server.core.mechanics.item.drops.DropsManager;
import com.rednetty.server.core.mechanics.item.drops.buff.LootBuffManager;
import com.rednetty.server.core.mechanics.item.drops.glowing.GlowingDropsInitializer;
import com.rednetty.server.core.mechanics.item.orb.OrbManager;
import com.rednetty.server.core.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationSystemManager;
import com.rednetty.server.core.mechanics.player.limbo.PlayerLimboSystem;
import com.rednetty.server.core.mechanics.player.PlayerMechanics;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.items.SpeedfishMechanics;
import com.rednetty.server.core.mechanics.player.listeners.TradeListener;
import com.rednetty.server.core.mechanics.player.mounts.MountManager;
import com.rednetty.server.core.mechanics.player.movement.DashMechanics;
import com.rednetty.server.core.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.core.mechanics.player.social.trade.TradeManager;
import com.rednetty.server.core.mechanics.ui.TabPluginIntegration;
import com.rednetty.server.core.mechanics.world.holograms.HologramManager;
import com.rednetty.server.core.mechanics.world.lootchests.LootChestManager;
import com.rednetty.server.core.mechanics.world.mobs.MobManager;
import com.rednetty.server.core.mechanics.world.mobs.tasks.SpawnerHologramUpdater;
import com.rednetty.server.core.mechanics.world.teleport.HearthstoneSystem;
import com.rednetty.server.core.mechanics.world.teleport.PortalSystem;
import com.rednetty.server.core.mechanics.world.teleport.TeleportBookSystem;
import com.rednetty.server.core.mechanics.world.teleport.TeleportManager;
import com.rednetty.server.core.mechanics.world.trail.TrailSystem;
import com.rednetty.server.core.mechanics.world.trail.pathing.ParticleSystem;
import com.rednetty.server.core.mechanics.world.trail.pathing.PathManager;
import com.rednetty.server.core.mechanics.world.trail.pathing.nodes.AdvancedNodeMapGenerator;
import com.rednetty.server.core.mechanics.world.trail.pathing.nodes.NavNode;
import com.rednetty.server.utils.input.ChatInputHandler;
import com.rednetty.server.utils.ui.ActionBarUtil;
import com.rednetty.server.utils.monitoring.PerformanceMonitor;
import com.rednetty.server.utils.monitoring.SystemHealthChecker;
import com.rednetty.server.utils.recovery.ErrorRecoveryManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * YakRealms Main Plugin Class
 * 
 * Core system initialization and management for the YakRealms server plugin.
 * Features modular architecture, enhanced logging, and comprehensive system monitoring.
 * 
 * @version 2.0.0
 * @author YakRealms Development Team
 */
public class YakRealms extends JavaPlugin {

    private static YakRealms instance;

    // Logging and Monitoring
    private EnhancedLogger enhancedLogger;
    private PerformanceMonitor performanceMonitor;
    private SystemHealthChecker healthChecker;
    private ErrorRecoveryManager errorRecoveryManager;

    // Game Settings
    private static boolean patchLockdown = false;
    private static int sessionID = 0;
    private static boolean t6Enabled = false;
    private static final String PLUGIN_VERSION = "2.0.0";

    // Initialization Tracking
    private final AtomicBoolean initializationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean fullyInitialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicInteger systemsInitialized = new AtomicInteger(0);
    private final AtomicInteger systemsFailed = new AtomicInteger(0);
    private final AtomicLong startupTime = new AtomicLong(0);

    // Dependency tracking
    private final Map<String, SystemStatus> systemStatuses = new ConcurrentHashMap<>();
    private final Map<String, Long> systemInitTimes = new ConcurrentHashMap<>();
    private final Set<String> criticalSystems = Set.of(
            "Database", "Player Manager", "Player Limbo System", "Moderation Mechanics", 
            "Modern Moderation System", "Combat Logout Mechanics"
    );

    // Health monitoring
    private BukkitTask healthCheckTask;
    private BukkitTask performanceMonitorTask;
    private BukkitTask memoryMonitorTask;

    // Core Systems
    private MongoDBManager mongoDBManager;
    private YakPlayerManager playerManager;
    private PlayerMechanics playerMechanics;
    private ModerationMechanics moderationMechanics;
    
    // Modern Moderation System
    private ModerationSystemManager moderationSystemManager;
    private PlayerLimboSystem playerLimboSystem;

    // Combat Systems
    private CombatLogoutMechanics combatLogoutMechanics;
    private AlignmentMechanics alignmentMechanics;
    private DeathMechanics deathMechanics;
    private CombatMechanics combatMechanics;
    private MagicStaff magicStaff;
    private DeathRemnantManager deathRemnantManager;

    // Social Systems
    private ChatMechanics chatMechanics;
    private PartyMechanics partyMechanics;
    private TradeManager tradeManager;
    private TradeListener tradeListener;

    // Movement Systems
    private DashMechanics dashMechanics;
    private SpeedfishMechanics speedfishMechanics;
    private MountManager mountManager;

    // Item Systems
    private ScrollManager scrollManager;
    private OrbManager orbManager;
    private Journal journalSystem;
    private MenuItemManager menuItemManager;

    // Economy Systems
    private EconomyManager economyManager;
    private BankManager bankManager;
    private GemPouchManager gemPouchManager;
    private VendorManager vendorManager;
    private MarketManager marketManager;
    private MerchantSystem merchantSystem;

    // World Systems
    private MobManager mobManager;
    private DropsManager dropsManager;
    private DropsHandler dropsHandler;
    private LootBuffManager lootBuffManager;
    private TeleportManager teleportManager;
    private TeleportBookSystem teleportBookSystem;
    private HearthstoneSystem hearthstoneSystem;
    private PortalSystem portalSystem;
    private TrailSystem trailSystem;
    private ParticleSystem particleSystem;
    private PathManager pathManager;
    private CrateManager crateManager;
    private LootChestManager lootChestManager;

    // Integration Systems
    private TabPluginIntegration tabPluginIntegration;

    // Commands and Utilities
    private SpawnerCommand spawnerCommand;
    private boolean mobsEnabled = true;
    private boolean spawnerVisibilityDefault = false;

    // System Status
    public enum SystemStatus {
        NOT_INITIALIZED, INITIALIZING, INITIALIZED, FAILED, RECOVERING, DISABLED
    }

    @Override
    public void onLoad() {
        instance = this;

        // Initialize enhanced logging system first
        try {
            enhancedLogger = new EnhancedLogger(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize enhanced logging: " + e.getMessage());
        }
    }

    public static YakRealms getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        if (!initializationInProgress.compareAndSet(false, true)) {
            getLogger().severe("Plugin initialization already in progress!");
            return;
        }

        try {
            startupTime.set(System.currentTimeMillis());

            enhancedLogger.info("YakRealms v" + PLUGIN_VERSION + " - Starting initialization...");

            // Initialize monitoring systems
            if (!initializeMonitoringSystems()) {
                throw new RuntimeException("Monitoring systems initialization failed");
            }

            // Phase 0: Pre-initialization
            if (!preInitialization()) {
                throw new RuntimeException("Pre-initialization failed");
            }

            // Phase 1: Core Systems
            if (!initializeCorePhase()) {
                throw new RuntimeException("Core systems initialization failed");
            }

            // Phase 2: Combat Systems
            if (!initializeCombatPhase()) {
                throw new RuntimeException("Combat systems initialization failed");
            }

            // Phase 3: Social Systems
            if (!initializeSocialPhase()) {
                throw new RuntimeException("Social systems initialization failed");
            }

            // Phase 4: Movement Systems
            if (!initializeMovementPhase()) {
                throw new RuntimeException("Movement systems initialization failed");
            }

            // Phase 5: Item Systems
            if (!initializeItemPhase()) {
                throw new RuntimeException("Item systems initialization failed");
            }

            // Phase 6: Economy Systems
            if (!initializeEconomyPhase()) {
                throw new RuntimeException("Economy systems initialization failed");
            }

            // Phase 7: World Systems
            if (!initializeWorldPhase()) {
                throw new RuntimeException("World systems initialization failed");
            }

            // Phase 8: Commands
            if (!initializeCommands()) {
                enhancedLogger.warn("Some commands failed to register - continuing startup");
            }

            // Phase 9: Integration Systems
            if (!initializeIntegrationPhase()) {
                enhancedLogger.warn("Integration systems failed - continuing startup");
            }

            // Phase 10: Finalization
            finalizationPhase();

            // Start monitoring tasks
            startMonitoringTasks();

            long totalTime = System.currentTimeMillis() - startupTime.get();
            fullyInitialized.set(true);

            // Professional startup completion message
            String status = systemsFailed.get() > 0 ? 
                String.format("YakRealms v%s enabled (%dms) - %d/%d systems initialized", 
                    PLUGIN_VERSION, totalTime, systemsInitialized.get(), 
                    systemsInitialized.get() + systemsFailed.get()) :
                String.format("YakRealms v%s enabled (%dms) - All systems operational", 
                    PLUGIN_VERSION, totalTime);
            
            if (systemsFailed.get() > 0) {
                enhancedLogger.warn(status);
            } else {
                enhancedLogger.info(status);
            }

        } catch (Exception e) {
            enhancedLogger.error("CRITICAL: YakRealms startup failed!", e);

            // Emergency cleanup
            try {
                emergencyShutdown();
            } catch (Exception cleanup) {
                enhancedLogger.error("Emergency cleanup failed", cleanup);
            }

            getServer().getPluginManager().disablePlugin(this);
        } finally {
            initializationInProgress.set(false);
        }
    }

    // Monitoring Implementation

    private boolean initializeMonitoringSystems() {
        try {
            // Performance monitor
            performanceMonitor = new PerformanceMonitor();

            // Health checker
            healthChecker = new SystemHealthChecker(performanceMonitor);
            
            // Error recovery manager
            errorRecoveryManager = new ErrorRecoveryManager();
            
            return true;
        } catch (Exception e) {
            enhancedLogger.error("Failed to initialize monitoring systems", e);
            return false;
        }
    }

    private void startMonitoringTasks() {
        try {
            // Health check task - every 30 seconds
            healthCheckTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        performHealthCheck();
                    } catch (Exception e) {
                        enhancedLogger.error("Health check failed", e);
                    }
                }
            }.runTaskTimerAsynchronously(this, 600L, 600L);

            // Performance monitor task - every 5 minutes
            performanceMonitorTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        performanceMonitor.collectMetrics();
                        if (isDebugMode()) {
                            enhancedLogger.debug("Performance metrics collected");
                        }
                    } catch (Exception e) {
                        enhancedLogger.error("Performance monitoring failed", e);
                    }
                }
            }.runTaskTimerAsynchronously(this, 6000L, 6000L);

            // Memory monitor task - every 2 minutes
            memoryMonitorTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        checkMemoryUsage();
                    } catch (Exception e) {
                        enhancedLogger.error("Memory monitoring failed", e);
                    }
                }
            }.runTaskTimerAsynchronously(this, 2400L, 2400L);

            enhancedLogger.info("✓ Monitoring tasks started");
        } catch (Exception e) {
            enhancedLogger.error("Failed to start monitoring tasks", e);
        }
    }

    private void performHealthCheck() {
        try {
            // Perform comprehensive health check using enhanced SystemHealthChecker
            SystemHealthChecker.HealthReport healthReport = healthChecker.performCompleteHealthCheck();
            
            // Log health status based on overall score
            double healthScore = healthReport.getOverallScore();
            if (healthScore < 50.0) {
                enhancedLogger.error("System health critical: " + String.format("%.1f", healthScore) + "/100");
                enhancedLogger.debug("Health report: " + healthReport.getCriticalIssues().size() + " critical issues found");
            } else if (healthScore < 80.0) {
                enhancedLogger.warn("System health degraded: " + String.format("%.1f", healthScore) + "/100");
            } else {
                if (isDebugMode()) {
                    enhancedLogger.debug("System health good: " + String.format("%.1f", healthScore) + "/100");
                }
            }
            
            // Check for failed systems that might be recoverable
            for (Map.Entry<String, SystemStatus> entry : systemStatuses.entrySet()) {
                if (entry.getValue() == SystemStatus.FAILED && criticalSystems.contains(entry.getKey())) {
                    enhancedLogger.warn("Critical system " + entry.getKey() + " is in failed state - attempting recovery");
                    attemptSystemRecovery(entry.getKey());
                }
            }
            
        } catch (Exception e) {
            enhancedLogger.error("Health check execution failed", e);
        }
    }

    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        double usagePercent = (double) usedMemory / totalMemory * 100;

        if (usagePercent > 85.0) {
            enhancedLogger.warn("High memory usage detected: " + String.format("%.1f%%", usagePercent));

            if (usagePercent > 95.0) {
                enhancedLogger.warn("Critical memory usage - forcing garbage collection");
                System.gc();

                // Trigger emergency cleanup in extreme cases
                if (usagePercent > 98.0) {
                    enhancedLogger.error("Emergency memory situation - triggering cleanup procedures");
                    performEmergencyCleanup();
                }
            }
        }
    }

    private void attemptSystemRecovery(String systemName) {
        try {
            systemStatuses.put(systemName, SystemStatus.RECOVERING);
            enhancedLogger.info("Attempting recovery for system: " + systemName);

            boolean recovered = false;

            switch (systemName) {
                case "Database":
                    recovered = recoverDatabaseConnection();
                    break;
                case "Player Manager":
                    recovered = recoverPlayerManager();
                    break;
                case "Moderation Mechanics":
                    recovered = recoverModerationMechanics();
                    break;
                case "Combat Logout Mechanics":
                    recovered = recoverCombatLogoutMechanics();
                    break;
            }

            if (recovered) {
                systemStatuses.put(systemName, SystemStatus.INITIALIZED);
                enhancedLogger.info("✓ Successfully recovered system: " + systemName);
            } else {
                systemStatuses.put(systemName, SystemStatus.FAILED);
                enhancedLogger.error("✗ Failed to recover system: " + systemName);
            }

        } catch (Exception e) {
            systemStatuses.put(systemName, SystemStatus.FAILED);
            enhancedLogger.error("Error during system recovery for " + systemName, e);
        }
    }

    // System Recovery

    private boolean recoverDatabaseConnection() {
        try {
            enhancedLogger.info("Attempting database reconnection...");
            
            if (mongoDBManager != null) {
                try {
                    mongoDBManager.disconnect();
                } catch (Exception e) {
                    enhancedLogger.warn("Error during database disconnect: " + e.getMessage());
                }
            }
            
            mongoDBManager = MongoDBManager.initialize(getConfig(), this);
            boolean connected = mongoDBManager.connect();
            
            if (connected) {
                enhancedLogger.info("Database connection restored successfully");
            } else {
                enhancedLogger.error("Database connection failed - invalid credentials or server unavailable");
            }
            
            return connected;
        } catch (Exception e) {
            enhancedLogger.error("Database recovery failed", e);
            return false;
        }
    }

    private boolean recoverPlayerManager() {
        try {
            enhancedLogger.info("Attempting Player Manager recovery...");
            
            if (playerManager != null) {
                try {
                    playerManager.onDisable();
                } catch (Exception e) {
                    enhancedLogger.warn("Error during Player Manager shutdown: " + e.getMessage());
                }
            }
            
            playerManager = YakPlayerManager.getInstance();
            playerManager.onEnable();
            
            enhancedLogger.info("Player Manager recovered successfully");
            return true;
        } catch (Exception e) {
            enhancedLogger.error("Player Manager recovery failed", e);
            return false;
        }
    }

    private boolean recoverModerationMechanics() {
        try {
            enhancedLogger.info("Attempting Moderation Mechanics recovery...");
            
            if (moderationMechanics != null) {
                try {
                    moderationMechanics.onDisable();
                } catch (Exception e) {
                    enhancedLogger.warn("Error during Moderation Mechanics shutdown: " + e.getMessage());
                }
            }
            
            moderationMechanics = ModerationMechanics.getInstance();
            enhancedLogger.info("Moderation Mechanics recovered successfully");
            return true;
        } catch (Exception e) {
            enhancedLogger.error("Moderation Mechanics recovery failed", e);
            return false;
        }
    }

    private boolean recoverCombatLogoutMechanics() {
        try {
            enhancedLogger.info("Attempting Combat Logout Mechanics recovery...");
            
            if (combatLogoutMechanics != null) {
                try {
                    combatLogoutMechanics.onDisable();
                } catch (Exception e) {
                    enhancedLogger.warn("Error during Combat Logout shutdown: " + e.getMessage());
                }
            }
            
            combatLogoutMechanics = CombatLogoutMechanics.getInstance();
            combatLogoutMechanics.onEnable();
            getServer().getPluginManager().registerEvents(combatLogoutMechanics, this);
            
            enhancedLogger.info("Combat Logout Mechanics recovered successfully");
            return true;
        } catch (Exception e) {
            enhancedLogger.error("Combat Logout Mechanics recovery failed", e);
            return false;
        }
    }

    private void performEmergencyCleanup() {
        try {
            enhancedLogger.warn("Performing emergency cleanup procedures...");

            // Clear caches if available
            if (menuItemManager != null) {
                // Clear menu caches
            }

            if (mobManager != null) {
                // Clear mob caches
            }

            // Force garbage collection
            System.gc();

            enhancedLogger.info("Emergency cleanup completed");
        } catch (Exception e) {
            enhancedLogger.error("Emergency cleanup failed", e);
        }
    }

    // Initialization Phases

    private boolean preInitialization() {
        try {

            // Validate server version
            if (!validateServerVersion()) {
                throw new RuntimeException("Unsupported server version");
            }

            // Ensure data folder exists
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            // Validate and load configuration
            if (!validateAndLoadConfig()) {
                throw new RuntimeException("Configuration validation failed");
            }

            // Generate session ID
            sessionID = ThreadLocalRandom.current().nextInt();

            // Load game settings
            loadGameSettings();

            // Check for plugin dependencies
            checkPluginDependencies();

            // Initialize Chat Input Handler (required for moderation system)
            safeInitialize("Chat Input Handler", () -> {
                ChatInputHandler.getInstance(); // Initialize singleton instance
                enhancedLogger.info("✓ Chat Input Handler initialized for interactive moderation");
                return true;
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Pre-initialization failed", e);
            return false;
        }
    }

    private boolean validateServerVersion() {
        String version = getServer().getVersion();
        enhancedLogger.debug("Server version: " + version);

        // Add version validation logic here
        if (version.contains("1.20.4") || version.contains("1.20.5") || version.contains("1.20.6")) {
            enhancedLogger.info("✓ Server version validated: " + version);
            return true;
        } else {
            enhancedLogger.warn("⚠ Untested server version: " + version + " - proceed with caution");
            return true; // Allow but warn
        }
    }

    private boolean validateAndLoadConfig() {
        try {
            // Create backup of existing config
            File configFile = new File(getDataFolder(), "config.yml");
            if (configFile.exists()) {
                File backupFile = new File(getDataFolder(), "config_backup_" + System.currentTimeMillis() + ".yml");
                Files.copy(configFile.toPath(), backupFile.toPath());
                enhancedLogger.debug("Configuration backup created: " + backupFile.getName());
            }

            // Save default config and reload
            saveDefaultConfig();
            reloadConfig();

            // Validate critical configuration values
            FileConfiguration config = getConfig();
            if (!config.contains("database")) {
                enhancedLogger.warn("Missing database configuration - using defaults");
            }

            enhancedLogger.info("✓ Configuration validated and loaded");
            return true;
        } catch (Exception e) {
            enhancedLogger.error("Configuration validation failed", e);
            return false;
        }
    }

    private void checkPluginDependencies() {
        Map<String, Boolean> dependencies = new HashMap<>();

        // Optional dependencies
        dependencies.put("TAB", Bukkit.getPluginManager().isPluginEnabled("TAB"));
        dependencies.put("PlaceholderAPI", Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
        dependencies.put("Vault", Bukkit.getPluginManager().isPluginEnabled("Vault"));

        // Only log missing critical dependencies
        dependencies.forEach((plugin, enabled) -> {
            if (!enabled) {
                enhancedLogger.debug(plugin + " not found (optional)");
            }
        });
    }

    private boolean initializeCorePhase() {
        try {

            // Database first with retry logic
            safeInitializeWithRetry("Database", () -> {
                mongoDBManager = MongoDBManager.initialize(getConfig(), this);
                return mongoDBManager.connect();
            }, 3, 5000);

            // Player limbo system - initialize early to freeze players during data loading
            safeInitialize("Player Limbo System", () -> {
                playerLimboSystem = PlayerLimboSystem.getInstance();
                playerLimboSystem.initialize();
                getServer().getPluginManager().registerEvents(playerLimboSystem, this);
                enhancedLogger.info("✓ Player Limbo System initialized - players will be frozen during data loading");
                return true;
            });

            // Player management systems
            safeInitialize("Player Manager", () -> {
                playerManager = YakPlayerManager.getInstance();
                playerManager.onEnable();
                Thread.sleep(500); // Brief pause for stability
                return true;
            });

            safeInitialize("Player Mechanics", () -> {
                playerMechanics = PlayerMechanics.getInstance();
                playerMechanics.onEnable();
                return true;
            });

            // Legacy moderation mechanics (keep for compatibility)
            safeInitialize("Moderation Mechanics", () -> {
                moderationMechanics = ModerationMechanics.getInstance();
                return true;
            });

            // Modern moderation system - initialize after core systems are ready
            safeInitialize("Modern Moderation System", () -> {
                moderationSystemManager = ModerationSystemManager.getInstance();
                moderationSystemManager.initialize();
                moderationSystemManager.enable();
                enhancedLogger.info("✓ Modern Moderation System initialized successfully");
                enhancedLogger.info("  - Automatic escalation enabled");
                enhancedLogger.info("  - Real-time dashboard ready");
                enhancedLogger.info("  - Appeal system operational");
                return true;
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Core phase failed", e);
            return false;
        }
    }

    private boolean initializeCombatPhase() {
        try {

            // CRITICAL: Initialize CombatLogoutMechanics FIRST!
            safeInitialize("Combat Logout Mechanics", () -> {
                combatLogoutMechanics = CombatLogoutMechanics.getInstance();
                combatLogoutMechanics.onEnable();
                getServer().getPluginManager().registerEvents(combatLogoutMechanics, this);
                return true;
            });

            // Now other combat systems that depend on it
            safeInitialize("Alignment Mechanics", () -> {
                alignmentMechanics = AlignmentMechanics.getInstance();
                alignmentMechanics.onEnable();
                return true;
            });

            safeInitialize("Death Mechanics", () -> {
                deathMechanics = DeathMechanics.getInstance();
                deathMechanics.onEnable();
                return true;
            });

            safeInitialize("Combat Mechanics", () -> {
                combatMechanics = new CombatMechanics();
                combatMechanics.onEnable();
                CombatTestCommand combatTestCommand = new CombatTestCommand();
                // Move combattest registration to standard registration system for consistency
                // this.getCommand("combattest").setExecutor(combatTestCommand);
                // this.getCommand("combattest").setTabCompleter(combatTestCommand);
                // Will be registered in registerStaffCommands() method instead

                return true;
            });

            safeInitialize("Magic Staff", () -> {
                magicStaff = new MagicStaff();
                magicStaff.onEnable();
                return true;
            });

            safeInitialize("Death Remnant Manager", () -> {
                deathRemnantManager = new DeathRemnantManager(this);
                return true;
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Combat phase failed", e);
            return false;
        }
    }

    private boolean initializeSocialPhase() {
        try {

            safeInitialize("Chat Mechanics", () -> {
                chatMechanics = ChatMechanics.getInstance();
                chatMechanics.onEnable();
                return true;
            });

            safeInitialize("Party Mechanics", () -> {
                partyMechanics = PartyMechanics.getInstance();
                return true;
            });

            safeInitialize("Trade System", () -> {
                tradeManager = new TradeManager(this);
                tradeListener = new TradeListener(this);
                getServer().getPluginManager().registerEvents(tradeListener, this);

                // Link them together with proper error handling
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (tradeListener != null && tradeManager != null) {
                        tradeListener.setTradeManager(tradeManager);
                        enhancedLogger.debug("Trade system components linked successfully");
                    }
                }, 5L);

                return true;
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Social phase failed", e);
            return false;
        }
    }

    private boolean initializeMovementPhase() {
        try {

            safeInitialize("Dash Mechanics", () -> {
                dashMechanics = new DashMechanics();
                dashMechanics.onEnable();
                return true;
            });

            safeInitialize("Speedfish Mechanics", () -> {
                speedfishMechanics = new SpeedfishMechanics();
                speedfishMechanics.onEnable();
                return true;
            });

            safeInitialize("Mount Manager", () -> {
                mountManager = MountManager.getInstance();
                mountManager.onEnable();
                return true;
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Movement phase failed", e);
            return false;
        }
    }

    private boolean initializeItemPhase() {
        try {

            // Basic item systems
            safeInitialize("Scroll Manager", () -> {
                scrollManager = ScrollManager.getInstance();
                scrollManager.initialize();
                return true;
            });

            safeInitialize("Orb Manager", () -> {
                orbManager = OrbManager.getInstance();
                orbManager.initialize();
                return true;
            });

            safeInitialize("Journal System", () -> {
                journalSystem = new Journal();
                return true;
            });

            safeInitialize("Menu Item Manager", () -> {
                menuItemManager = MenuItemManager.getInstance();
                menuItemManager.initialize();

                // Initialize menu system with delay and error handling
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        MenuSystemInitializer.initialize();
                        enhancedLogger.info("✓ Menu system initialized");
                    } catch (Exception e) {
                        enhancedLogger.error("Menu system initialization failed", e);
                        systemStatuses.put("Menu System", SystemStatus.FAILED);
                    }
                }, 20L);

                return true;
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Item phase failed", e);
            return false;
        }
    }

    private boolean initializeEconomyPhase() {
        try {

            safeInitialize("Economy Manager", () -> {
                economyManager = EconomyManager.getInstance();
                economyManager.onEnable();
                return true;
            });

            safeInitialize("Bank Manager", () -> {
                bankManager = BankManager.getInstance();
                bankManager.onEnable();
                return true;
            });

            safeInitialize("Gem Pouch Manager", () -> {
                gemPouchManager = GemPouchManager.getInstance();
                gemPouchManager.onEnable();
                return true;
            });

            safeInitialize("Vendor System", () -> {
                // Legacy vendor manager for backward compatibility
                VendorManager.initialize(this);
                vendorManager = VendorManager.getInstance();
                
                // New enhanced vendor system
                VendorSystemManager.initialize(this, economyManager, playerManager);
                
                enhancedLogger.info("✓ Enhanced Vendor System initialized with registry support");
                return true;
            });

            safeInitialize("Market Manager", () -> {
                marketManager = MarketManager.getInstance();
                marketManager.onEnable();
                return true;
            });

            safeInitialize("Merchant System", () -> {
                merchantSystem = MerchantSystem.getInstance();
                if (merchantSystem.validateDependencies()) {
                    merchantSystem.initialize();
                    return true;
                } else {
                    enhancedLogger.warn("Merchant system dependencies not satisfied - skipping");
                    return false;
                }
            });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Economy phase failed", e);
            return false;
        }
    }

    private boolean initializeWorldPhase() {
        try {

            safeInitialize("Mob Manager", () -> {
                mobManager = MobManager.getInstance();
                mobManager.initialize();
                mobManager.setSpawnersEnabled(mobsEnabled);
                return true;
            });

            safeInitialize("Drops System", () -> {
                dropsHandler = DropsHandler.getInstance();
                dropsHandler.initialize();

                lootBuffManager = LootBuffManager.getInstance();
                lootBuffManager.initialize();

                dropsManager = DropsManager.getInstance();
                dropsManager.initialize();

                GlowingDropsInitializer.initialize();
                return true;
            });

            safeInitialize("Teleport Systems", () -> {
                teleportManager = TeleportManager.getInstance();
                teleportManager.onEnable();

                teleportBookSystem = TeleportBookSystem.getInstance();
                hearthstoneSystem = HearthstoneSystem.getInstance();
                portalSystem = PortalSystem.getInstance();
                return true;
            });

            safeInitialize("Trail System", () -> {
                trailSystem = new TrailSystem(this);
                particleSystem = new ParticleSystem(this);

                // Path manager with safe initialization
                try {
                    List<World> worlds = getServer().getWorlds();
                    if (!worlds.isEmpty()) {
                        World mainWorld = worlds.get(0);
                        AdvancedNodeMapGenerator nodeGenerator = new AdvancedNodeMapGenerator();
                        File nodeMapFile = new File(getDataFolder(), mainWorld.getName() + "_advanced_navgraph.dat");
                        List<NavNode> nodes = nodeGenerator.getOrGenerateNodeMap(mainWorld, nodeMapFile);
                        pathManager = new PathManager(this, particleSystem);
                        enhancedLogger.info("Path manager initialized with " + nodes.size() + " nodes");
                    }
                } catch (Exception e) {
                    enhancedLogger.error("PathManager initialization failed", e);
                }
                return true;
            });

            safeInitialize("Crate System", () -> {
                crateManager = CrateManager.getInstance();
                crateManager.initialize();
                return true;
            });

            safeInitialize("Loot Chest Manager", () -> {
                lootChestManager = new LootChestManager(this);
                return true;
            });

            // TODO: Implement Dungeon System
            // safeInitialize("Dungeon System", () -> {
            //     DungeonManager dungeonManager = DungeonManager.getInstance();
            //     dungeonManager.initialize();
            //     return true;
            // });

            return true;
        } catch (Exception e) {
            enhancedLogger.error("World phase failed", e);
            return false;
        }
    }

    private boolean initializeIntegrationPhase() {
        try {

            // TAB Plugin Integration (optional)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    if (Bukkit.getPluginManager().isPluginEnabled("TAB")) {
                        tabPluginIntegration = TabPluginIntegration.getInstance();
                        tabPluginIntegration.initialize();
                        systemStatuses.put("TAB Integration", SystemStatus.INITIALIZED);
                        enhancedLogger.info("✓ TAB Plugin Integration enabled");
                    } else {
                        systemStatuses.put("TAB Integration", SystemStatus.DISABLED);
                        enhancedLogger.debug("TAB plugin not found - player stats tablist disabled");
                    }
                } catch (Exception e) {
                    systemStatuses.put("TAB Integration", SystemStatus.FAILED);
                    enhancedLogger.error("TAB integration failed", e);
                }
            }, 60L);

            return true;
        } catch (Exception e) {
            enhancedLogger.error("Integration phase failed", e);
            return false;
        }
    }

    private void finalizationPhase() {
        try {

            // Start background tasks
            if (spawnerCommand != null) {
                SpawnerHologramUpdater.startTask();
            }

            // Initialize utilities
            ActionBarUtil.init(this);

            // Start configuration hot-reload watcher
            startConfigWatcher();

        } catch (Exception e) {
            enhancedLogger.error("Finalization phase had issues", e);
        }
    }

    private void startConfigWatcher() {
        new BukkitRunnable() {
            private long lastModified = new File(getDataFolder(), "config.yml").lastModified();

            @Override
            public void run() {
                try {
                    File configFile = new File(getDataFolder(), "config.yml");
                    long currentModified = configFile.lastModified();

                    if (currentModified > lastModified) {
                        lastModified = currentModified;
                        enhancedLogger.info("Configuration file changed - reloading...");

                        reloadConfig();
                        loadGameSettings();

                        enhancedLogger.info("✓ Configuration reloaded successfully");
                    }
                } catch (Exception e) {
                    enhancedLogger.error("Configuration hot-reload failed", e);
                }
            }
        }.runTaskTimerAsynchronously(this, 200L, 200L); // Check every 10 seconds
    }

    // Initialization Utilities

    private void safeInitializeWithRetry(String systemName, InitializationTask task, int maxRetries, long retryDelay) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                attempts++;
                systemStatuses.put(systemName, SystemStatus.INITIALIZING);
                long startTime = System.currentTimeMillis();

                if (task.initialize()) {
                    long initTime = System.currentTimeMillis() - startTime;
                    systemInitTimes.put(systemName, initTime);
                    systemStatuses.put(systemName, SystemStatus.INITIALIZED);
                    systemsInitialized.incrementAndGet();

                    // Only log if slow or required multiple attempts
                    if (initTime > 2000 || attempts > 1) {
                        String message = systemName + " initialized (" + initTime + "ms)";
                        if (attempts > 1) message += " [attempt " + attempts + "]";
                        enhancedLogger.info(message);
                    }
                    return;
                } else {
                    throw new RuntimeException("Initialization returned false");
                }
            } catch (Exception e) {
                lastException = e;
                enhancedLogger.warn("✗ " + systemName + " failed (attempt " + attempts + "/" + maxRetries + "): " + e.getMessage());

                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // All retries failed
        systemStatuses.put(systemName, SystemStatus.FAILED);
        systemsFailed.incrementAndGet();
        enhancedLogger.error("✗ " + systemName + " failed after " + maxRetries + " attempts", lastException);
    }

    private void safeInitialize(String systemName, InitializationTask task) {
        try {
            systemStatuses.put(systemName, SystemStatus.INITIALIZING);
            long startTime = System.currentTimeMillis();

            if (task.initialize()) {
                long initTime = System.currentTimeMillis() - startTime;
                systemInitTimes.put(systemName, initTime);
                systemStatuses.put(systemName, SystemStatus.INITIALIZED);
                systemsInitialized.incrementAndGet();

                // Only log slow systems and failures to reduce startup spam
                if (initTime > 2000) {
                    enhancedLogger.warn(systemName + " took " + initTime + "ms to initialize");
                }
            } else {
                systemStatuses.put(systemName, SystemStatus.FAILED);
                systemsFailed.incrementAndGet();
                enhancedLogger.warn("✗ " + systemName + " failed");
            }
        } catch (Exception e) {
            systemStatuses.put(systemName, SystemStatus.FAILED);
            systemsFailed.incrementAndGet();
            enhancedLogger.error("✗ " + systemName + " error: " + e.getMessage(), e);
        }
    }

    private void logSystemStatusSummary() {
        enhancedLogger.info("System Status Summary:");

        Map<SystemStatus, Integer> statusCounts = new HashMap<>();
        for (SystemStatus status : SystemStatus.values()) {
            statusCounts.put(status, 0);
        }

        for (SystemStatus status : systemStatuses.values()) {
            statusCounts.put(status, statusCounts.get(status) + 1);
        }

        statusCounts.forEach((status, count) -> {
            if (count > 0) {
                enhancedLogger.info("  " + status + ": " + count);
            }
        });

        // Log slowest systems
        List<Map.Entry<String, Long>> slowSystems = systemInitTimes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .toList();

        if (!slowSystems.isEmpty()) {
            enhancedLogger.info("Slowest Systems:");
            slowSystems.forEach(entry ->
                    enhancedLogger.info("  " + entry.getKey() + ": " + entry.getValue() + "ms")
            );
        }
    }

    // Command Registration

    private boolean initializeCommands() {
        try {
            Map<String, Boolean> commandResults = new HashMap<>();

            registerPlayerCommands(commandResults);
            registerEconomyCommands(commandResults);
            registerSystemCommands(commandResults);
            registerMobCommands(commandResults);
            registerChatCommands(commandResults);
            registerModerationCommands(commandResults);
            registerAdminCommands(commandResults);
            registerNavigationCommands(commandResults);
            registerPartyCommands(commandResults);

            return evaluateCommandResults(commandResults);
        } catch (Exception e) {
            enhancedLogger.error("Command registration failed", e);
            return false;
        }
    }

    private void registerPlayerCommands(Map<String, Boolean> results) {
        results.put("logout", registerCommand("logout", new com.rednetty.server.core.commands.player.LogoutCommand(),
                new com.rednetty.server.core.commands.player.LogoutCommand()));
        results.put("toggles", registerCommand("toggles", new TogglesCommand()));
        results.put("alignment", registerCommand("alignment", new AlignmentCommand(alignmentMechanics)));
        results.put("invsee", registerCommand("invsee", new InvseeCommand()));
        results.put("mount", registerCommand("mount", new MountCommand()));
        results.put("journal", registerCommand("journal", new JournalCommand()));
        results.put("speedfish", registerCommand("speedfish", new SpeedfishCommand(speedfishMechanics)));
    }

    private void registerEconomyCommands(Map<String, Boolean> results) {
        results.put("balance", registerCommand("balance", new BalanceCommand(economyManager)));
        results.put("pay", registerCommand("pay", new PayCommand(economyManager)));
        results.put("bank", registerCommand("bank", new BankCommand(bankManager)));
        results.put("gems", registerCommand("gems", new GemsCommand(economyManager)));
        results.put("gempouch", registerCommand("gempouch", new GemPouchCommand(gemPouchManager)));
        results.put("eco", registerCommand("eco", new EcoCommand(economyManager)));
        results.put("vendor", registerCommand("vendor", new VendorCommand(this)));
        
        if (marketManager != null) {
            MarketCommand marketCommand = new MarketCommand();
            results.put("market", registerCommand("market", marketCommand, marketCommand));
        }
    }

    private void registerSystemCommands(Map<String, Boolean> results) {
        results.put("inventorydiag", registerCommand("inventorydiag", getPlayerManager()));
        results.put("playerstate", registerCommand("playerstate", getPlayerManager()));
        results.put("coordinationdiag", registerCommand("coordinationdiag", getPlayerManager()));
        results.put("teleport", registerCommand("teleport", new TeleportCommand()));
        results.put("teleportbook", registerCommand("teleportbook", new TeleportBookCommand()));
        results.put("item", registerCommand("item", new ItemCommand(this)));
        results.put("scroll", registerCommand("scroll", new ScrollCommand(scrollManager)));
        results.put("orb", registerCommand("orb", new OrbCommand(orbManager)));

        if (menuItemManager != null) {
            MenuCommand menuCommand = new MenuCommand();
            results.put("menu", registerCommand("menu", menuCommand, menuCommand));
        }

        if (crateManager != null) {
            CrateCommand crateCommand = new CrateCommand();
            results.put("crate", registerCommand("crate", crateCommand, crateCommand));
        }

        if (lootChestManager != null) {
            VaultCommand vaultCommand = new VaultCommand(this);
            results.put("vault", registerCommand("vault", vaultCommand, vaultCommand));
            results.put("lootchest", registerCommand("lootchest", vaultCommand, vaultCommand));
        }
    }

    private void registerMobCommands(Map<String, Boolean> results) {
        if (mobManager != null) {
            spawnerCommand = new SpawnerCommand(mobManager);
            results.put("spawner", registerCommand("spawner", spawnerCommand, spawnerCommand));
            results.put("spawnmob", registerCommand("spawnmob", new SpawnMobCommand(mobManager)));
            results.put("mobinfo", registerCommand("mobinfo", new MobInfoCommand(mobManager)));
            results.put("togglespawners", registerCommand("togglespawners", new ToggleSpawnersCommand(mobManager)));
            results.put("boss", registerCommand("boss", new BossCommand(mobManager)));
            
            com.rednetty.server.core.commands.staff.admin.EliteBehaviorTestCommand eliteTestCommand = 
                    new com.rednetty.server.core.commands.staff.admin.EliteBehaviorTestCommand();
            results.put("elitetest", registerCommand("elitetest", eliteTestCommand, eliteTestCommand));
        }

        if (dropsManager != null) {
            results.put("droprate", registerCommand("droprate", new DropRateCommand(dropsManager)));
            results.put("lootbuff", registerCommand("lootbuff", new LootBuffCommand(lootBuffManager)));
            results.put("elitedrop", registerCommand("elitedrop", new EliteDropsCommand()));
        }
    }

    private void registerChatCommands(Map<String, Boolean> results) {
        results.put("buddy", registerCommand("buddy", new BuddiesCommand()));
        results.put("msg", registerCommand("msg", new MessageCommand()));
        results.put("r", registerCommand("r", new ReplyCommand()));
        results.put("global", registerCommand("global", new GlobalChatCommand()));
        results.put("staffchat", registerCommand("staffchat", new StaffChatCommand()));
        results.put("chattag", registerCommand("chattag", new ChatTagCommand()));
    }

    private void registerModerationCommands(Map<String, Boolean> results) {
        results.put("kick", registerCommand("kick", new KickCommand()));
        results.put("ban", registerCommand("ban", new BanCommand(moderationMechanics)));
        results.put("unban", registerCommand("unban", new UnbanCommand(moderationMechanics)));
        results.put("mute", registerCommand("mute", new MuteCommand(moderationMechanics)));
        results.put("unmute", registerCommand("unmute", new UnmuteCommand(moderationMechanics)));
        results.put("vanish", registerCommand("vanish", new VanishCommand(this)));
        results.put("setrank", registerCommand("setrank", new SetRankCommand(moderationMechanics)));
        
        // Register combattest command properly in moderation section
        CombatTestCommand combatTestCommand = new CombatTestCommand();
        results.put("combattest", registerCommand("combattest", combatTestCommand, combatTestCommand));
        
        WarnCommand warnCommand = new WarnCommand();
        results.put("warn", registerCommand("warn", warnCommand, warnCommand));
        
        ModerationDashboardCommand dashboardCommand = new ModerationDashboardCommand();
        results.put("moderationdashboard", registerCommand("moderationdashboard", dashboardCommand, dashboardCommand));
        results.put("moddash", registerCommand("moddash", dashboardCommand, dashboardCommand));
        
        ModerationHistoryCommand historyCommand = new ModerationHistoryCommand();
        results.put("moderationhistory", registerCommand("moderationhistory", historyCommand, historyCommand));
        
        PlayerWipeCommand wipeCommand = new PlayerWipeCommand();
        results.put("wipe", registerCommand("wipe", wipeCommand, wipeCommand));
        
        ModerationMenuCommand menuCommand = new ModerationMenuCommand();
        results.put("modmenu", registerCommand("modmenu", menuCommand, menuCommand));
    }

    private void registerAdminCommands(Map<String, Boolean> results) {
        results.put("shutdown", registerCommand("shutdown", new com.rednetty.server.core.commands.staff.admin.ShutdownCommand(),
                new com.rednetty.server.core.commands.staff.admin.ShutdownCommand()));
        
        com.rednetty.server.core.commands.staff.admin.SystemReportCommand systemReportCommand = 
            new com.rednetty.server.core.commands.staff.admin.SystemReportCommand();
        results.put("systemreport", registerCommand("systemreport", systemReportCommand, systemReportCommand));

        // Register missing commands identified in audit
        TabMenuCommand tabMenuCommand = new TabMenuCommand();
        results.put("tabmenu", registerCommand("tabmenu", tabMenuCommand, tabMenuCommand));
        
        // Register GlowingDropCommand (already has getInstance() and initialize())
        GlowingDropCommand glowingCommand = GlowingDropCommand.getInstance();
        glowingCommand.initialize();
        results.put("glowingdrops", registerCommand("glowingdrops", glowingCommand, glowingCommand));
        
        // Register ChestAnimationCommand (chest command from plugin.yml)
        ChestAnimationCommand chestCommand = new ChestAnimationCommand();
        results.put("chest", registerCommand("chest", chestCommand, chestCommand));
    }

    private void registerNavigationCommands(Map<String, Boolean> results) {
        if (pathManager != null) {
            results.put("trail", registerCommand("trail", new TrailCommand(this, pathManager)));
        }
        results.put("nodemap", registerCommand("nodemap", new NodeMapCommand(this)));
    }

    private void registerPartyCommands(Map<String, Boolean> results) {
        results.put("p", registerCommand("p", new PartyCommand()));
        results.put("paccept", registerCommand("paccept", new PAcceptCommand()));
        results.put("pdecline", registerCommand("pdecline", new PDeclineCommand()));
        results.put("pinvite", registerCommand("pinvite", new PInviteCommand()));
        results.put("pkick", registerCommand("pkick", new PKickCommand()));
        results.put("pquit", registerCommand("pquit", new PQuitCommand()));
    }

    private boolean evaluateCommandResults(Map<String, Boolean> commandResults) {
        int commandCount = 0;
        int failedCount = 0;

        for (Map.Entry<String, Boolean> entry : commandResults.entrySet()) {
            if (entry.getValue()) {
                commandCount++;
            } else {
                failedCount++;
                enhancedLogger.warn("Failed to register command: " + entry.getKey());
            }
        }

        if (failedCount > 0) {
            enhancedLogger.warn("Commands: " + commandCount + " registered, " + failedCount + " failed");
        }
        return failedCount == 0;
    }

    private boolean registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        return registerCommand(name, executor, null);
    }

    private boolean registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                    org.bukkit.command.TabCompleter tabCompleter) {
        try {
            org.bukkit.command.PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(executor);
                if (tabCompleter != null) {
                    command.setTabCompleter(tabCompleter);
                }
                enhancedLogger.debug("Registered command: " + name);
                return true;
            } else {
                enhancedLogger.warn("Command not found in plugin.yml: " + name);
                return false;
            }
        } catch (Exception e) {
            enhancedLogger.error("Failed to register command: " + name, e);
            return false;
        }
    }

    // Shutdown System

    @Override
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            enhancedLogger.warn("Shutdown already in progress");
            return;
        }

        try {
            enhancedLogger.info("========================================");
            enhancedLogger.info("    YakRealms v" + PLUGIN_VERSION + " Shutting Down");
            enhancedLogger.info("========================================");

            long startTime = System.currentTimeMillis();

            // Stop monitoring tasks first
            stopMonitoringTasks();

            // Perform clean shutdown
            performCleanShutdown();

            long totalTime = System.currentTimeMillis() - startTime;
            enhancedLogger.info("✓ YakRealms shutdown completed (" + totalTime + "ms)");
            enhancedLogger.info("✓ Uptime: " + getUptimeString());
            enhancedLogger.info("========================================");

            // Close enhanced logger last
            if (enhancedLogger != null) {
                enhancedLogger.close();
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during shutdown", e);
        }
    }

    private void stopMonitoringTasks() {
        try {
            if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
                healthCheckTask.cancel();
            }
            if (performanceMonitorTask != null && !performanceMonitorTask.isCancelled()) {
                performanceMonitorTask.cancel();
            }
            if (memoryMonitorTask != null && !memoryMonitorTask.isCancelled()) {
                memoryMonitorTask.cancel();
            }
            
            // Shutdown enhanced health checker
            if (healthChecker != null) {
                healthChecker.shutdown();
            }
            
            // Shutdown error recovery manager
            if (errorRecoveryManager != null) {
                errorRecoveryManager.shutdown();
            }
            
            enhancedLogger.info("✓ Monitoring tasks stopped");
        } catch (Exception e) {
            enhancedLogger.error("Error stopping monitoring tasks", e);
        }
    }

    private void performCleanShutdown() {
        // Integration systems first
        shutdownSafely("TAB Plugin Integration", () -> {
            if (tabPluginIntegration != null) {
                tabPluginIntegration.shutdown();
            }
        });

        // Trade system (to clean up active trades)
        shutdownSafely("Trade System", () -> {
            if (tradeManager != null) {
                tradeManager.clearAllTrades();
            }
            if (tradeListener != null) {
                tradeListener.cleanup();
            }
        });

        // Menu system
        shutdownSafely("Menu System", () -> {
            if (MenuSystemInitializer.isInitialized()) {
                MenuSystemInitializer.shutdown();
            }
        });

        // World systems
        shutdownSafely("Loot Chest Manager", () -> {
            if (lootChestManager != null) {
                lootChestManager.shutdown();
            }
        });

        shutdownSafely("Crate System", () -> {
            if (crateManager != null) {
                crateManager.shutdown();
            }
        });

        shutdownSafely("Merchant System", () -> {
            if (merchantSystem != null) {
                merchantSystem.shutdown();
            }
        });

        // Combat systems
        shutdownSafely("Death Mechanics", () -> {
            if (deathMechanics != null) deathMechanics.onDisable();
        });

        shutdownSafely("Alignment Mechanics", () -> {
            if (alignmentMechanics != null) alignmentMechanics.onDisable();
        });

        shutdownSafely("Combat Logout Mechanics", () -> {
            if (combatLogoutMechanics != null) combatLogoutMechanics.onDisable();
        });

        // Core systems
        shutdownSafely("Modern Moderation System", () -> {
            if (moderationSystemManager != null) {
                moderationSystemManager.disable();
            }
        });

        shutdownSafely("Player Limbo System", () -> {
            if (playerLimboSystem != null) {
                playerLimboSystem.shutdown();
            }
        });

        shutdownSafely("Moderation Mechanics", () -> {
            if (moderationMechanics != null) {
                moderationMechanics.onDisable();
            }
        });

        shutdownSafely("Player Mechanics", () -> {
            if (playerMechanics != null) {
                playerMechanics.onDisable();
            }
        });

        shutdownSafely("Player Manager", () -> {
            if (playerManager != null) {
                playerManager.onDisable();
            }
        });

        // Teleport book system
        shutdownSafely("Teleport Book System", () -> {
            if (teleportBookSystem != null) {
                teleportBookSystem.onDisable();
            }
        });

        // Vendor System
        shutdownSafely("Vendor System", () -> {
            VendorSystemManager vendorSystemManager = VendorSystemManager.getInstance();
            if (vendorSystemManager != null) {
                vendorSystemManager.shutdown();
            }
        });

        // Holograms
        shutdownSafely("Hologram Manager", () -> {
            HologramManager.cleanup();
        });

        // Database last
        shutdownSafely("Database", () -> {
            if (mongoDBManager != null) {
                mongoDBManager.disconnect();
            }
        });
    }

    private void shutdownSafely(String systemName, Runnable shutdownTask) {
        try {
            long startTime = System.currentTimeMillis();
            shutdownTask.run();
            long shutdownTime = System.currentTimeMillis() - startTime;

            systemStatuses.put(systemName, SystemStatus.DISABLED);
            enhancedLogger.info("✓ " + systemName + " shutdown (" + shutdownTime + "ms)");
        } catch (Exception e) {
            enhancedLogger.error("Error shutting down " + systemName, e);
        }
    }

    private void emergencyShutdown() {
        enhancedLogger.error("Performing emergency shutdown...");
        try {
            if (playerManager != null) playerManager.onDisable();
            if (mongoDBManager != null) mongoDBManager.disconnect();
            enhancedLogger.info("Emergency shutdown completed");
        } catch (Exception e) {
            enhancedLogger.error("Emergency shutdown failed", e);
        }
    }

    // Utility Methods
    
    /**
     * Generate comprehensive system status report for troubleshooting
     */
    public String generateSystemReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== YakRealms System Status Report ===\n");
        report.append("Plugin Version: ").append(PLUGIN_VERSION).append("\n");
        report.append("Uptime: ").append(getUptimeString()).append("\n");
        report.append("Systems Initialized: ").append(systemsInitialized.get()).append("\n");
        report.append("Systems Failed: ").append(systemsFailed.get()).append("\n");
        report.append("Fully Initialized: ").append(fullyInitialized.get()).append("\n");
        
        report.append("\n=== System Status ===\n");
        for (Map.Entry<String, SystemStatus> entry : systemStatuses.entrySet()) {
            String status = entry.getValue().name();
            String time = systemInitTimes.get(entry.getKey()) != null ? 
                "(" + systemInitTimes.get(entry.getKey()) + "ms)" : "";
            report.append(String.format("%-25s: %s %s\n", entry.getKey(), status, time));
        }
        
        report.append("\n=== Memory Status ===\n");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        report.append(String.format("Used: %.1f MB / %.1f MB (%.1f%%)\n", 
            usedMemory / 1024.0 / 1024.0,
            totalMemory / 1024.0 / 1024.0,
            (double) usedMemory / totalMemory * 100));
        report.append(String.format("Max Available: %.1f MB\n", maxMemory / 1024.0 / 1024.0));
        
        report.append("\n=== Database Status ===\n");
        if (mongoDBManager != null) {
            report.append("MongoDB: CONNECTED\n");
            report.append("Connection Pool: Active\n");
        } else {
            report.append("MongoDB: DISCONNECTED\n");
        }
        
        report.append("\n=== Player Status ===\n");
        report.append("Online Players: ").append(getServer().getOnlinePlayers().size()).append("\n");
        
        return report.toString();
    }

    private void loadGameSettings() {
        FileConfiguration config = getConfig();
        t6Enabled = config.getBoolean("game.t6-enabled", false);
        mobsEnabled = config.getBoolean("mechanics.mobs.enabled", true);
        spawnerVisibilityDefault = config.getBoolean("mechanics.mobs.spawner-default-visibility", false);

        enhancedLogger.debug("Game settings loaded: T6=" + t6Enabled + ", Mobs=" + mobsEnabled);
    }

    private String getBuildInfo() {
        // This would typically read from a build.properties file
        return "SNAPSHOT-" + sessionID;
    }

    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        return String.format("%.1f/%.1fMB (%.1f%%)",
                usedMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0,
                (double) usedMemory / maxMemory * 100);
    }

    private String getUptimeString() {
        long uptime = System.currentTimeMillis() - startupTime.get();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // Static Utility Methods

    public static void log(String message) {
        if (instance != null && instance.enhancedLogger != null) {
            if (instance.isDebugMode()) {
                instance.enhancedLogger.info(message);
            }
        } else if (instance != null) {
            instance.getLogger().info(message);
        }
    }

    public static void warn(String message) {
        if (instance != null && instance.enhancedLogger != null) {
            instance.enhancedLogger.warn(message);
        } else if (instance != null) {
            instance.getLogger().warning(message);
        }
    }

    public static void error(String message, Exception e) {
        if (instance != null && instance.enhancedLogger != null) {
            instance.enhancedLogger.error(message, e);
        } else if (instance != null) {
            instance.getLogger().log(Level.SEVERE, message, e);
        }
    }

    public static void debug(String message) {
        if (instance != null && instance.enhancedLogger != null && instance.isDebugMode()) {
            instance.enhancedLogger.debug(message);
        }
    }

    // System Access Methods

    // Static getters for safe system access
    public static boolean isPatchLockdown() { return patchLockdown; }
    public static void setPatchLockdown(boolean patchLockdown) { YakRealms.patchLockdown = patchLockdown; }
    public static int getSessionID() { return sessionID; }
    public static boolean isT6Enabled() { return t6Enabled; }
    public static void setT6Enabled(boolean enabled) {
        t6Enabled = enabled;
        if (instance != null) {
            instance.getConfig().set("game.t6-enabled", enabled);
            instance.saveConfig();
        }
    }

    // Enhanced system getters with status checks
    public static TabPluginIntegration getTabPluginIntegrationSafe() {
        if (instance == null || instance.tabPluginIntegration == null) {
            throw new IllegalStateException("TAB Plugin Integration not available");
        }
        return instance.tabPluginIntegration;
    }

    public static boolean isTabPluginIntegrationAvailable() {
        return instance != null && instance.tabPluginIntegration != null &&
                instance.systemStatuses.get("TAB Integration") == SystemStatus.INITIALIZED;
    }

    public static CrateManager getCrateManagerSafe() {
        if (instance == null || instance.crateManager == null) {
            throw new IllegalStateException("Crate manager not available");
        }
        return instance.crateManager;
    }

    public static boolean isCrateSystemAvailable() {
        return instance != null && instance.crateManager != null &&
                instance.systemStatuses.get("Crate System") == SystemStatus.INITIALIZED;
    }

    public static MenuItemManager getMenuItemManagerSafe() {
        if (instance == null || instance.menuItemManager == null) {
            throw new IllegalStateException("Menu item manager not available");
        }
        return instance.menuItemManager;
    }

    public static boolean isMenuItemSystemAvailable() {
        return instance != null && instance.menuItemManager != null &&
                instance.systemStatuses.get("Menu Item Manager") == SystemStatus.INITIALIZED &&
                MenuSystemInitializer.isInitialized();
    }

    // System status and health methods
    public SystemStatus getSystemStatus(String systemName) {
        return systemStatuses.getOrDefault(systemName, SystemStatus.NOT_INITIALIZED);
    }

    public Map<String, SystemStatus> getAllSystemStatuses() {
        return new HashMap<>(systemStatuses);
    }

    public boolean isSystemHealthy(String systemName) {
        SystemStatus status = systemStatuses.get(systemName);
        return status == SystemStatus.INITIALIZED;
    }

    public CompletableFuture<Boolean> performSystemHealthCheck() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                performHealthCheck();
                return true;
            } catch (Exception e) {
                enhancedLogger.error("System health check failed", e);
                return false;
            }
        });
    }

    // Enhanced getters with null safety
    public TabPluginIntegration getTabPluginIntegration() { return tabPluginIntegration; }
    public MongoDBManager getMongoDBManager() { return mongoDBManager; }
    public YakPlayerManager getPlayerManager() { return playerManager; }
    public PlayerMechanics getPlayerMechanics() { return playerMechanics; }
    public ModerationMechanics getModerationMechanics() { return moderationMechanics; }
    public ModerationSystemManager getModerationSystemManager() { return moderationSystemManager; }
    public PlayerLimboSystem getPlayerLimboSystem() { return playerLimboSystem; }
    public PartyMechanics getPartyMechanics() { return partyMechanics; }
    public DashMechanics getDashMechanics() { return dashMechanics; }
    public SpeedfishMechanics getSpeedfishMechanics() { return speedfishMechanics; }
    public MountManager getMountManager() { return mountManager; }
    public ScrollManager getScrollManager() { return scrollManager; }
    public OrbManager getOrbManager() { return orbManager; }
    public Journal getJournalSystem() { return journalSystem; }
    public MenuItemManager getMenuItemManager() { return menuItemManager; }
    public TradeManager getTradeManager() { return tradeManager; }
    public TradeListener getTradeListener() { return tradeListener; }
    public AlignmentMechanics getAlignmentMechanics() { return alignmentMechanics; }
    public DeathRemnantManager getDeathRemnantManager() { return deathRemnantManager; }
    public ChatMechanics getChatMechanics() { return chatMechanics; }
    public CombatMechanics getCombatMechanics() { return combatMechanics; }
    public MagicStaff getMagicStaff() { return magicStaff; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public BankManager getBankManager() { return bankManager; }
    public GemPouchManager getGemPouchManager() { return gemPouchManager; }
    public VendorManager getVendorManager() { return vendorManager; }
    public MarketManager getMarketManager() { return marketManager; }
    public MobManager getMobManager() { return mobManager; }
    public SpawnerCommand getSpawnerCommand() { return spawnerCommand; }
    public DropsManager getDropsManager() { return dropsManager; }
    public DropsHandler getDropsHandler() { return dropsHandler; }
    public LootBuffManager getLootBuffManager() { return lootBuffManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public TeleportBookSystem getTeleportBookSystem() { return teleportBookSystem; }
    public HearthstoneSystem getHearthstoneSystem() { return hearthstoneSystem; }
    public PortalSystem getPortalSystem() { return portalSystem; }
    public TrailSystem getTrailSystem() { return trailSystem; }
    public ParticleSystem getParticleSystem() { return particleSystem; }
    public PathManager getPathManager() { return pathManager; }
    public CrateManager getCrateManager() { return crateManager; }
    public LootChestManager getLootChestManager() { return lootChestManager; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public SystemHealthChecker getSystemHealthChecker() { return healthChecker; }
    public ErrorRecoveryManager getErrorRecoveryManager() { return errorRecoveryManager; }
    
    public long getStartupTime() { return startupTime.get(); }

    public boolean isDebugMode() {
        return getConfig().getBoolean("debug", false);
    }

    public boolean isMobsEnabled() {
        return mobsEnabled;
    }

    public boolean isFullyInitialized() {
        return fullyInitialized.get();
    }

    public boolean isShuttingDown() {
        return shutdownInProgress.get();
    }

    public EnhancedLogger getEnhancedLogger() {
        return enhancedLogger;
    }


    @FunctionalInterface
    private interface InitializationTask {
        boolean initialize() throws Exception;
    }

    // Enhanced Logger Implementation

    public static class EnhancedLogger {
        private final JavaPlugin plugin;
        private final File logDirectory;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        private PrintWriter currentLogWriter;
        private String currentLogDate;
        private final Object logLock = new Object();

        // Log level settings
        private boolean debugEnabled;
        private boolean fileLoggingEnabled;
        private int maxLogFiles = 30;
        private long maxLogSizeBytes = 50 * 1024 * 1024; // 50MB

        public EnhancedLogger(JavaPlugin plugin) throws IOException {
            this.plugin = plugin;
            this.logDirectory = new File(plugin.getDataFolder(), "logs");
            this.debugEnabled = plugin.getConfig().getBoolean("logging.debug", false);
            this.fileLoggingEnabled = plugin.getConfig().getBoolean("logging.file-enabled", true);
            this.maxLogFiles = plugin.getConfig().getInt("logging.max-files", 30);
            this.maxLogSizeBytes = plugin.getConfig().getLong("logging.max-size-mb", 50) * 1024 * 1024;

            if (fileLoggingEnabled) {
                initializeFileLogging();
                cleanupOldLogs();
            }
        }

        private void initializeFileLogging() throws IOException {
            if (!logDirectory.exists()) {
                logDirectory.mkdirs();
            }

            currentLogDate = dateFormat.format(new Date());
            File logFile = new File(logDirectory, "yakrealms-" + currentLogDate + ".log");

            // Check if current log file is too large
            if (logFile.exists() && logFile.length() > maxLogSizeBytes) {
                // Create numbered log file
                int fileNumber = 1;
                do {
                    logFile = new File(logDirectory, "yakrealms-" + currentLogDate + "-" + fileNumber + ".log");
                    fileNumber++;
                } while (logFile.exists() && logFile.length() > maxLogSizeBytes);
            }

            currentLogWriter = new PrintWriter(new FileWriter(logFile, true));
            info(" logging initialized - " + logFile.getName());
        }

        private void cleanupOldLogs() {
            File[] logFiles = logDirectory.listFiles((dir, name) -> name.startsWith("yakrealms-") && name.endsWith(".log"));
            if (logFiles != null && logFiles.length > maxLogFiles) {
                Arrays.sort(logFiles, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

                for (int i = 0; i < logFiles.length - maxLogFiles; i++) {
                    if (logFiles[i].delete()) {
                        plugin.getLogger().info("Deleted old log file: " + logFiles[i].getName());
                    }
                }
            }
        }

        private void writeToFile(String level, String message, Exception exception) {
            if (!fileLoggingEnabled || currentLogWriter == null) return;

            synchronized (logLock) {
                try {
                    // Check if we need to rotate to a new day
                    String today = dateFormat.format(new Date());
                    if (!today.equals(currentLogDate)) {
                        currentLogWriter.close();
                        initializeFileLogging();
                    }

                    String timestamp = timeFormat.format(new Date());
                    String logLine = String.format("[%s] [%s] %s", timestamp, level, message);

                    currentLogWriter.println(logLine);

                    if (exception != null) {
                        exception.printStackTrace(currentLogWriter);
                    }

                    currentLogWriter.flush();

                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to write to log file: " + e.getMessage());
                }
            }
        }

        public void info(String message) {
            plugin.getLogger().info(message);
            writeToFile("INFO", message, null);
        }

        public void warn(String message) {
            plugin.getLogger().warning(message);
            writeToFile("WARN", message, null);
        }

        public void error(String message) {
            plugin.getLogger().severe(message);
            writeToFile("ERROR", message, null);
        }

        public void error(String message, Exception exception) {
            plugin.getLogger().log(Level.SEVERE, message, exception);
            writeToFile("ERROR", message, exception);
        }

        public void debug(String message) {
            if (debugEnabled) {
                plugin.getLogger().info("[DEBUG] " + message);
                writeToFile("DEBUG", message, null);
            }
        }

        public void close() {
            synchronized (logLock) {
                if (currentLogWriter != null) {
                    info(" logging shutdown");
                    currentLogWriter.close();
                    currentLogWriter = null;
                }
            }
        }
    }

}
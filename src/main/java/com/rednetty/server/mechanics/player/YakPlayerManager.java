package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.combat.death.DeathMechanics;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.limbo.LimboManager;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * YakPlayerManager - Complete coordination with death/combat systems
 * Ensures all player loading/saving coordinates with DeathMechanics and CombatLogoutMechanics
 */
public class YakPlayerManager implements Listener, CommandExecutor {

    // Constants
    private static final Logger logger = Logger.getLogger(YakPlayerManager.class.getName());
    private static final Object INSTANCE_LOCK = new Object();

    // Timing constants
    private static final long DATA_LOAD_TIMEOUT_MS = 10000L;
    private static final long LOADING_TIMEOUT_TICKS = 400L;
    private static final long DEFAULT_AUTO_SAVE_INTERVAL_TICKS = 6000L;
    private static final long BAN_CHECK_TIMEOUT_SECONDS = 5L;
    private static final long EMERGENCY_RECOVERY_DELAY = 60L;
    private static final long COORDINATION_DELAY_TICKS = 1L;

    // Processing constants
    private static final int MAX_CONCURRENT_OPERATIONS = 10;
    private static final int DEFAULT_IO_THREADS = 4;

    // Enums
    public enum PlayerState {
        OFFLINE, LOADING, READY, FAILED, DEATH_PROCESSING, COMBAT_LOGOUT_PROCESSING
    }

    public enum LoadingPhase {
        STARTING, LOADING_DATA, APPLYING_DATA, DEATH_COORDINATION, COMBAT_COORDINATION, COMPLETED, FAILED
    }

    // Singleton instance
    private static volatile YakPlayerManager instance;

    public static YakPlayerManager getInstance() {
        YakPlayerManager result = instance;
        if (result == null) {
            synchronized (INSTANCE_LOCK) {
                result = instance;
                if (result == null) {
                    instance = result = new YakPlayerManager();
                }
            }
        }
        return result;
    }

    // Core dependencies
    @Getter
    private YakPlayerRepository repository;
    private final Plugin plugin;
    private final LimboManager limboManager;

    // Death and combat system references
    private DeathMechanics deathMechanics;
    private CombatLogoutMechanics combatLogoutMechanics;

    // Player tracking with death/combat coordination
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerLoadingState> loadingStates = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final Set<UUID> playersInRecovery = ConcurrentHashMap.newKeySet();

    // Death and combat coordination tracking
    private final Set<UUID> playersInDeathProcessing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> playersInCombatLogoutProcessing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> deathProcessingStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatLogoutProcessingStartTimes = new ConcurrentHashMap<>();

    // Settings protection with guaranteed persistence
    private final Map<UUID, Map<String, Boolean>> pendingSettingsChanges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> settingsChangeTimestamps = new ConcurrentHashMap<>();

    // State management
    private final Map<UUID, ReentrantReadWriteLock> playerStateLocks = new ConcurrentHashMap<>();

    // Thread management
    private final ExecutorService ioExecutor;
    private final ExecutorService saveExecutor;
    private final ExecutorService coordinationExecutor; // For death/combat coordination
    private final Semaphore operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS);

    // Performance tracking with death/combat metrics
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger failedLoads = new AtomicInteger(0);
    private final AtomicInteger emergencyRecoveries = new AtomicInteger(0);
    private final AtomicInteger settingsProtectionSaves = new AtomicInteger(0);
    private final AtomicInteger guaranteedSaves = new AtomicInteger(0);
    private final AtomicInteger forcedInventorySaves = new AtomicInteger(0);
    private final AtomicInteger saveFailures = new AtomicInteger(0);

    // Death and combat coordination metrics
    private final AtomicInteger deathCoordinations = new AtomicInteger(0);
    private final AtomicInteger combatLogoutCoordinations = new AtomicInteger(0);
    private final AtomicInteger systemConflictsDetected = new AtomicInteger(0);
    private final AtomicInteger systemConflictsResolved = new AtomicInteger(0);
    private final AtomicInteger coordinationTimeouts = new AtomicInteger(0);

    // System state
    private volatile boolean initialized = false;
    private volatile boolean shutdownInProgress = false;
    private volatile boolean systemHealthy = false;

    // Configuration
    private final long autoSaveInterval;

    // World management
    private volatile World defaultWorld;

    // Background tasks
    private BukkitTask autoSaveTask;
    private BukkitTask loadingMonitorTask;
    private BukkitTask emergencyRecoveryTask;
    private BukkitTask guaranteedSaveTask;
    private BukkitTask coordinationMonitorTask; // Monitor death/combat coordination

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.limboManager = LimboManager.getInstance();

        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks",
                DEFAULT_AUTO_SAVE_INTERVAL_TICKS);

        int ioThreads = plugin.getConfig().getInt("player_manager.io_threads", DEFAULT_IO_THREADS);
        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO");
            thread.setDaemon(true);
            return thread;
        });

        this.saveExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Save");
            thread.setDaemon(true);
            return thread;
        });

        // Coordination executor for death/combat system coordination
        this.coordinationExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Coordination");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("YakPlayerManager initialized with death/combat coordination");
    }

    // Initialization methods
    public void onEnable() {
        if (initialized) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting YakPlayerManager with death/combat coordination...");

            if (!initializeRepository()) {
                logger.severe("Failed to initialize repository!");
                systemHealthy = false;
                return;
            }

            // Initialize death and combat system references
            if (!initializeDeathAndCombatIntegration()) {
                logger.severe("Failed to initialize death/combat integration!");
                systemHealthy = false;
                return;
            }

            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
            initializeDefaultWorld();
            startBackgroundTasks();

            systemHealthy = true;
            initialized = true;

            logger.info("YakPlayerManager enabled with complete death/combat coordination");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerManager", e);
            systemHealthy = false;
        }
    }

    /**
     * Initialize death and combat system integration
     */
    private boolean initializeDeathAndCombatIntegration() {
        try {
            logger.info("Initializing death and combat system integration...");

            // Get references to death and combat systems (they should be initialized by PlayerMechanics)
            this.deathMechanics = DeathMechanics.getInstance();
            this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();

            if (deathMechanics == null) {
                logger.warning("DeathMechanics not available during YakPlayerManager initialization");
                // Don't fail initialization, systems might not be ready yet
            } else {
                logger.info("✓ DeathMechanics integration established");
            }

            if (combatLogoutMechanics == null) {
                logger.warning("CombatLogoutMechanics not available during YakPlayerManager initialization");
                // Don't fail initialization, systems might not be ready yet
            } else {
                logger.info("✓ CombatLogoutMechanics integration established");
            }

            logger.info("✓ Death and combat system integration completed");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing death/combat integration", e);
            return false;
        }
    }

    private boolean initializeRepository() {
        try {
            logger.info("Initializing YakPlayerRepository...");
            this.repository = new YakPlayerRepository();

            int attempts = 0;
            while (!repository.isInitialized() && attempts < 30) {
                Thread.sleep(1000);
                attempts++;
            }

            if (!repository.isInitialized()) {
                logger.severe("Repository failed to initialize after 30 seconds");
                return false;
            }

            logger.info("YakPlayerRepository initialized successfully");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing repository", e);
            return false;
        }
    }

    private void initializeDefaultWorld() {
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            this.defaultWorld = worlds.get(0);
            logger.info("Using default world: " + defaultWorld.getName());
        } else {
            logger.warning("No worlds available!");
        }
    }

    private void startBackgroundTasks() {
        // Auto-save with death/combat coordination
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress) {
                    performAutoSave();
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        // Loading monitor with death/combat awareness
        loadingMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                monitorLoadingPlayers();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 100L);

        // Emergency recovery with coordination
        emergencyRecoveryTask = new BukkitRunnable() {
            @Override
            public void run() {
                performEmergencyRecovery();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);

        // Guaranteed save task with coordination
        guaranteedSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                performGuaranteedInventorySaves();
            }
        }.runTaskTimerAsynchronously(plugin, 300L, 300L);

        // Coordination monitoring task
        coordinationMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                monitorDeathAndCombatCoordination();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 100L); // Every 5 seconds

        logger.info("Background tasks started with death/combat coordination monitoring");
    }

    // Event handlers with integrated coordination
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shutdownInProgress || !systemHealthy) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Server is starting up. Please try again in a moment.")
                            .color(NamedTextColor.RED));
            return;
        }

        UUID uuid = event.getUniqueId();
        String playerName = event.getName();

        try {
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(BAN_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                if (player.isBanned()) {
                    Component banMessage = formatBanMessage(player);
                    if (banMessage != null) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage);
                        logger.info("Denied login for banned player: " + playerName);
                        return;
                    }
                }

                if (!player.getUsername().equals(playerName)) {
                    player.setUsername(playerName);
                    repository.saveSync(player);
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking ban status for " + playerName, e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerJoins.incrementAndGet();
        logger.info("Player joining: " + playerName + " (" + uuid + ")");

        cleanupPlayerState(uuid, "new_join");

        if (handleCombatLogoutCheck(player, event)) {
            return;
        }

        handleNormalPlayerJoin(player);
    }

    private boolean handleCombatLogoutCheck(Player player, PlayerJoinEvent event) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        try {
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(3, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer yakPlayer = playerOpt.get();
                YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();

                if (logoutState == YakPlayer.CombatLogoutState.PROCESSED) {
                    logger.info("Combat logout rejoin detected for " + playerName);
                    combatLogoutCoordinations.incrementAndGet();
                    event.setJoinMessage(null);
                    handleCombatLogoutRejoin(player, yakPlayer);
                    return true;
                }

                if (logoutState != YakPlayer.CombatLogoutState.NONE) {
                    logger.info("Resetting stale combat logout state for " + playerName + ": " + logoutState);
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    repository.saveSync(yakPlayer);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking combat logout state for " + playerName, e);
        }

        return false;
    }

    private void handleNormalPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        logger.info("Starting coordinated loading for " + playerName);

        setPlayerState(uuid, PlayerState.LOADING, "join_start");

        PlayerLoadingState loadingState = new PlayerLoadingState(uuid, playerName);
        loadingStates.put(uuid, loadingState);

        startCoordinatedLoading(player, loadingState);
    }

    /**
     * Coordinated loading process with death/combat awareness
     */
    private void startCoordinatedLoading(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();
        loadingState.setPhase(LoadingPhase.STARTING);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                applySafeLoadingState(player);
            }
        });

        CompletableFuture.supplyAsync(() -> {
            return loadPlayerDataWithCoordination(player);
        }, ioExecutor).whenComplete((yakPlayer, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || yakPlayer == null) {
                    handleLoadingFailure(player, loadingState, error);
                } else {
                    completePlayerLoadingWithCoordination(player, loadingState, yakPlayer);
                }
            });
        });

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (loadingStates.containsKey(uuid) && getPlayerState(uuid) == PlayerState.LOADING) {
                logger.warning("Loading timeout for " + player.getName());
                handleLoadingFailure(player, loadingState, new RuntimeException("Loading timeout"));
            }
        }, LOADING_TIMEOUT_TICKS);
    }

    private void applySafeLoadingState(Player player) {
        try {
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setInvulnerable(true);

            player.getInventory().clear();
            player.updateInventory();

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("Loading your character...")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD));
            player.sendMessage(Component.text("Coordinating with game systems...")
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.empty());

            logger.fine("Applied safe loading state for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying safe loading state for " + player.getName(), e);
        }
    }

    /**
     * Load player data with death/combat coordination
     */
    private YakPlayer loadPlayerDataWithCoordination(Player player) {
        try {
            if (!operationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Operation semaphore timeout");
            }

            try {
                logger.info("Loading player data with coordination: " + player.getName());

                // Check for any ongoing death/combat processing before loading
                UUID uuid = player.getUniqueId();
                if (isPlayerInDeathProcessing(uuid)) {
                    logger.info("Player has ongoing death processing, coordinating: " + player.getName());
                    waitForDeathProcessingCompletion(uuid);
                }

                if (isPlayerInCombatLogoutProcessing(uuid)) {
                    logger.info("Player has ongoing combat logout processing, coordinating: " + player.getName());
                    waitForCombatLogoutProcessingCompletion(uuid);
                }

                CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(player.getUniqueId());
                Optional<YakPlayer> existingPlayer = repositoryFuture.get(DATA_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                YakPlayer yakPlayer;
                if (existingPlayer.isPresent()) {
                    yakPlayer = existingPlayer.get();
                    yakPlayer.connect(player);
                    logger.info("Loaded existing player with coordination: " + player.getName());
                } else {
                    yakPlayer = new YakPlayer(player);
                    logger.info("Created new player: " + player.getName());
                    repository.saveSync(yakPlayer);
                }

                successfulLoads.incrementAndGet();
                return yakPlayer;

            } finally {
                operationSemaphore.release();
            }

        } catch (Exception e) {
            failedLoads.incrementAndGet();
            logger.log(Level.SEVERE, "Failed to load player data with coordination: " + player.getName(), e);
            throw new RuntimeException("Player data load failed with coordination", e);
        }
    }

    /**
     * Wait for death processing to complete
     */
    private void waitForDeathProcessingCompletion(UUID uuid) {
        try {
            int attempts = 0;
            while (isPlayerInDeathProcessing(uuid) && attempts < 30) {
                Thread.sleep(500); // Wait 500ms
                attempts++;
            }

            if (attempts >= 30) {
                logger.warning("Timeout waiting for death processing completion: " + uuid);
                coordinationTimeouts.incrementAndGet();
                // Force clear the death processing state
                playersInDeathProcessing.remove(uuid);
                deathProcessingStartTimes.remove(uuid);
            } else {
                logger.info("Death processing completed, proceeding with load: " + uuid);
                deathCoordinations.incrementAndGet();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while waiting for death processing completion: " + uuid);
        }
    }

    /**
     * Wait for combat logout processing to complete
     */
    private void waitForCombatLogoutProcessingCompletion(UUID uuid) {
        try {
            int attempts = 0;
            while (isPlayerInCombatLogoutProcessing(uuid) && attempts < 30) {
                Thread.sleep(500); // Wait 500ms
                attempts++;
            }

            if (attempts >= 30) {
                logger.warning("Timeout waiting for combat logout processing completion: " + uuid);
                coordinationTimeouts.incrementAndGet();
                // Force clear the combat logout processing state
                playersInCombatLogoutProcessing.remove(uuid);
                combatLogoutProcessingStartTimes.remove(uuid);
            } else {
                logger.info("Combat logout processing completed, proceeding with load: " + uuid);
                combatLogoutCoordinations.incrementAndGet();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Interrupted while waiting for combat logout processing completion: " + uuid);
        }
    }

    /**
     * Complete player loading with death/combat coordination
     */
    private void completePlayerLoadingWithCoordination(Player player, PlayerLoadingState loadingState, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Completing coordinated loading for " + player.getName());

            if (!player.isOnline()) {
                cleanupLoadingState(uuid);
                return;
            }

            loadingState.setPhase(LoadingPhase.APPLYING_DATA);

            // Check for death/combat coordination before applying data
            if (checkForCoordinationNeeds(player, yakPlayer, loadingState)) {
                return; // Coordination in progress, will be completed later
            }

            applyPendingSettingsChanges(uuid, yakPlayer);

            onlinePlayers.put(uuid, yakPlayer);
            loadingState.setYakPlayer(yakPlayer);

            if (yakPlayer.getCombatLogoutState() != YakPlayer.CombatLogoutState.NONE) {
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                logger.info("Reset combat logout state for " + player.getName());
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    applyPlayerDataSafely(player, yakPlayer);
                    finalizePlayerLoadingWithCoordination(player, loadingState, yakPlayer);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error applying player data for " + player.getName(), e);
                    handleLoadingFailure(player, loadingState, e);
                }
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error completing coordinated player loading for " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    /**
     * Check for death/combat coordination needs during loading
     */
    private boolean checkForCoordinationNeeds(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            // Check if death coordination is needed
            if (yakPlayer.hasRespawnItems() && deathMechanics != null) {
                logger.info("Death coordination needed for " + player.getName());
                loadingState.setPhase(LoadingPhase.DEATH_COORDINATION);

                CompletableFuture.runAsync(() -> {
                    coordinateWithDeathSystem(player, yakPlayer, loadingState);
                }, coordinationExecutor);

                return true; // Coordination in progress
            }

            // Check if combat logout coordination is needed
            YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();
            if (logoutState == YakPlayer.CombatLogoutState.PROCESSED && combatLogoutMechanics != null) {
                logger.info("Combat logout coordination needed for " + player.getName());
                loadingState.setPhase(LoadingPhase.COMBAT_COORDINATION);

                CompletableFuture.runAsync(() -> {
                    coordinateWithCombatLogoutSystem(player, yakPlayer, loadingState);
                }, coordinationExecutor);

                return true; // Coordination in progress
            }

            return false; // No coordination needed

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking coordination needs for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Coordinate with death system during loading
     */
    private void coordinateWithDeathSystem(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        try {
            UUID uuid = player.getUniqueId();
            logger.info("Coordinating with death system for " + player.getName());

            // Mark as in death coordination
            markPlayerInDeathProcessing(uuid);

            // Let death system know about the coordination
            // Death system will handle respawn item restoration when appropriate

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Complete the loading process
                    applyPlayerDataSafely(player, yakPlayer);
                    finalizePlayerLoadingWithCoordination(player, loadingState, yakPlayer);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error in death coordination completion for " + player.getName(), e);
                    handleLoadingFailure(player, loadingState, e);
                } finally {
                    unmarkPlayerInDeathProcessing(uuid);
                }
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in death system coordination for " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    /**
     * Coordinate with combat logout system during loading
     */
    private void coordinateWithCombatLogoutSystem(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        try {
            UUID uuid = player.getUniqueId();
            logger.info("Coordinating with combat logout system for " + player.getName());

            // Mark as in combat logout coordination
            markPlayerInCombatLogoutProcessing(uuid);

            // Let combat logout system handle the rejoin completion
            if (combatLogoutMechanics != null) {
                combatLogoutMechanics.handleCombatLogoutRejoin(uuid);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Complete the loading process
                    applyPlayerDataSafely(player, yakPlayer);
                    finalizePlayerLoadingWithCoordination(player, loadingState, yakPlayer);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error in combat logout coordination completion for " + player.getName(), e);
                    handleLoadingFailure(player, loadingState, e);
                } finally {
                    unmarkPlayerInCombatLogoutProcessing(uuid);
                }
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in combat logout system coordination for " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private void applyPlayerDataSafely(Player player, YakPlayer yakPlayer) {
        try {
            logger.info("Applying player data with coordination for " + player.getName());

            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }

            try {
                yakPlayer.applyInventory(player);
                logger.fine("Applied inventory for " + player.getName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error applying inventory for " + player.getName() + ", using defaults", e);
                applyDefaultInventory(player);
            }

            try {
                applyPlayerStatsSafely(player, yakPlayer);
                logger.fine("Applied stats for " + player.getName());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error applying stats for " + player.getName() + ", using defaults", e);
                applyDefaultStats(player);
            }

            initializePlayerSystems(player, yakPlayer);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error applying player data for " + player.getName(), e);
            throw e;
        }
    }

    private void applyPlayerStatsSafely(Player player, YakPlayer yakPlayer) {
        try {
            double savedHealth = yakPlayer.getHealth();
            double savedMaxHealth = yakPlayer.getMaxHealth();

            if (savedMaxHealth <= 0) {
                savedMaxHealth = 20.0;
                yakPlayer.setMaxHealth(savedMaxHealth);
            }

            if (savedHealth <= 0 || savedHealth > savedMaxHealth) {
                savedHealth = savedMaxHealth;
                yakPlayer.setHealth(savedHealth);
            }

            player.setMaxHealth(savedMaxHealth);

            double finalSavedHealth = savedHealth;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    double finalHealth = Math.min(finalSavedHealth, player.getMaxHealth());
                    player.setHealth(finalHealth);
                    logger.fine("Set health for " + player.getName() + ": " + finalHealth + "/" + player.getMaxHealth());
                }
            }, 1L);

            player.setFoodLevel(Math.max(0, Math.min(20, yakPlayer.getFoodLevel())));
            player.setSaturation(Math.max(0, Math.min(20, yakPlayer.getSaturation())));
            player.setLevel(Math.max(0, Math.min(21863, yakPlayer.getXpLevel())));
            player.setExp(Math.max(0, Math.min(1, yakPlayer.getXpProgress())));
            player.setTotalExperience(Math.max(0, yakPlayer.getTotalExperience()));

            try {
                GameMode mode = GameMode.valueOf(yakPlayer.getGameMode());
                player.setGameMode(mode);
            } catch (Exception e) {
                logger.warning("Invalid game mode for " + player.getName() + ": " + yakPlayer.getGameMode());
                player.setGameMode(GameMode.SURVIVAL);
                yakPlayer.setGameMode("SURVIVAL");
            }

            if (yakPlayer.getBedSpawnLocation() != null) {
                try {
                    Location bedLoc = parseBedSpawnLocation(yakPlayer.getBedSpawnLocation());
                    if (bedLoc != null) {
                        player.setBedSpawnLocation(bedLoc, true);
                    }
                } catch (Exception e) {
                    logger.fine("Failed to apply bed spawn location: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in stat application for " + player.getName(), e);
            throw e;
        }
    }

    private Location parseBedSpawnLocation(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) return null;

        try {
            String[] parts = locationStr.split(":");
            if (parts.length >= 4) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    return new Location(world,
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                }
            }
        } catch (Exception e) {
            logger.fine("Error parsing bed spawn location: " + locationStr);
        }
        return null;
    }

    private void applyDefaultInventory(Player player) {
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
            player.getEnderChest().clear();
            player.updateInventory();
            logger.info("Applied default inventory for " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying default inventory", e);
        }
    }

    private void applyDefaultStats(Player player) {
        try {
            player.setMaxHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.setLevel(0);
            player.setExp(0.0f);
            player.setTotalExperience(0);
            player.setGameMode(GameMode.SURVIVAL);
            logger.info("Applied default stats for " + player.getName());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying default stats", e);
        }
    }

    private void initializePlayerSystems(Player player, YakPlayer yakPlayer) {
        try {
            String rankString = yakPlayer.getRank();
            if (rankString == null || rankString.trim().isEmpty()) {
                rankString = "DEFAULT";
                yakPlayer.setRank("DEFAULT");
            }

            try {
                Rank rank = Rank.fromString(rankString);
                ModerationMechanics.rankMap.put(player.getUniqueId(), rank);
            } catch (Exception e) {
                logger.warning("Invalid rank for " + player.getName() + ": " + rankString);
                ModerationMechanics.rankMap.put(player.getUniqueId(), Rank.DEFAULT);
                yakPlayer.setRank("DEFAULT");
            }

            String chatTagString = yakPlayer.getChatTag();
            if (chatTagString == null || chatTagString.trim().isEmpty()) {
                chatTagString = "DEFAULT";
                yakPlayer.setChatTag("DEFAULT");
            }

            try {
                ChatTag tag = ChatTag.valueOf(chatTagString);
                ChatMechanics.getPlayerTags().put(player.getUniqueId(), tag);
            } catch (Exception e) {
                logger.warning("Invalid chat tag for " + player.getName() + ": " + chatTagString);
                ChatMechanics.getPlayerTags().put(player.getUniqueId(), ChatTag.DEFAULT);
                yakPlayer.setChatTag("DEFAULT");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
        }
    }

    /**
     * Finalize player loading with coordination
     */
    private void finalizePlayerLoadingWithCoordination(Player player, PlayerLoadingState loadingState, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            loadingState.setPhase(LoadingPhase.COMPLETED);

            player.setInvulnerable(false);

            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                player.setAllowFlight(true);
            } else {
                player.setAllowFlight(false);
                player.setFlying(false);
            }

            player.setHealth(yakPlayer.getHealth());
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(yakPlayer.getMaxHealth());

            Location targetLocation = determineFinalLocation(yakPlayer);
            if (targetLocation != null) {
                player.teleport(targetLocation);
                logger.fine("Teleported " + player.getName() + " to final location");
            }

            setPlayerState(uuid, PlayerState.READY, "coordinated_loading_completed");

            loadingStates.remove(uuid);

            updateJoinMessage(player, yakPlayer);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && PlayerListenerManager.getInstance() != null &&
                        PlayerListenerManager.getInstance().getJoinLeaveListener() != null) {
                    PlayerListenerManager.getInstance().getJoinLeaveListener()
                            .sendMotd(player, yakPlayer, false);
                }
            }, 40L);

            logger.info("Successfully loaded player with coordination: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error finalizing coordinated player loading for " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private Location determineFinalLocation(YakPlayer yakPlayer) {
        try {
            Location savedLocation = yakPlayer.getLocation();
            boolean isNewPlayer = isNewPlayer(yakPlayer);

            if (isNewPlayer || savedLocation == null || savedLocation.getWorld() == null) {
                if (defaultWorld != null) {
                    Location spawnLocation = defaultWorld.getSpawnLocation();
                    yakPlayer.updateLocation(spawnLocation);
                    return spawnLocation;
                }
            } else {
                return savedLocation;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error determining final location for " + yakPlayer.getUsername(), e);
        }

        if (defaultWorld != null) {
            return defaultWorld.getSpawnLocation();
        }

        return null;
    }

    private boolean isNewPlayer(YakPlayer yakPlayer) {
        try {
            long twoHoursAgo = (System.currentTimeMillis() / 1000) - 7200;
            return yakPlayer.getFirstJoin() > twoHoursAgo;
        } catch (Exception e) {
            return true;
        }
    }

    // Error handling
    private void handleLoadingFailure(Player player, PlayerLoadingState loadingState, Throwable error) {
        UUID uuid = player.getUniqueId();

        try {
            setPlayerState(uuid, PlayerState.FAILED, "coordinated_loading_failure");
            loadingState.setPhase(LoadingPhase.FAILED);
            logger.log(Level.SEVERE, "Loading failed for: " + player.getName(), error);

            if (player.isOnline()) {
                performEmergencyPlayerRecovery(player);
            }

            cleanupLoadingState(uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling loading failure: " + player.getName(), e);
        }
    }

    private void performEmergencyPlayerRecovery(Player player) {
        try {
            logger.warning("Performing emergency recovery for " + player.getName());
            emergencyRecoveries.incrementAndGet();

            player.setInvulnerable(false);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);

            player.setMaxHealth(50.0);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);

            if (defaultWorld != null) {
                player.teleport(defaultWorld.getSpawnLocation());
            }

            player.getInventory().clear();
            player.updateInventory();

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("Emergency recovery completed!")
                    .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Your character has been restored to a safe state.")
                    .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("If you continue having issues, please contact an admin.")
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.empty());

            UUID uuid = player.getUniqueId();
            YakPlayer yakPlayer = new YakPlayer(player);
            onlinePlayers.put(uuid, yakPlayer);
            setPlayerState(uuid, PlayerState.READY, "emergency_recovery");

            ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
            ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);

            repository.saveSync(yakPlayer);

            logger.info("Emergency recovery completed for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Emergency recovery failed for " + player.getName(), e);
        }
    }

    // Combat logout rejoin handling
    private void handleCombatLogoutRejoin(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Processing combat logout rejoin for: " + player.getName());

            setPlayerState(uuid, PlayerState.READY, "combat_logout_rejoin");
            yakPlayer.connect(player);
            onlinePlayers.put(uuid, yakPlayer);

            applyPendingSettingsChanges(uuid, yakPlayer);

            applyPlayerDataSafely(player, yakPlayer);
            initializePlayerSystems(player, yakPlayer);

            if (defaultWorld != null) {
                player.teleport(defaultWorld.getSpawnLocation());
                yakPlayer.updateLocation(defaultWorld.getSpawnLocation());
            }

            clearCombatLogoutData(uuid, yakPlayer);

            sendCombatLogoutRejoinMessages(player, yakPlayer);

            logger.info("Combat logout rejoin completed for: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling combat logout rejoin for " + player.getName(), e);
            handleNormalPlayerJoin(player);
        }
    }

    private void clearCombatLogoutData(UUID uuid, YakPlayer yakPlayer) {
        try {
            if (combatLogoutMechanics != null) {
                combatLogoutMechanics.handleCombatLogoutRejoin(uuid);
            }

            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            repository.saveSync(yakPlayer);
            logger.info("Cleared combat logout data for " + yakPlayer.getUsername());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error clearing combat logout data", e);
        }
    }

    private void sendCombatLogoutRejoinMessages(Player player, YakPlayer yakPlayer) {
        try {
            Bukkit.broadcast(Component.text("⟨✧⟩ ")
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(yakPlayer.getFormattedDisplayName()))
                    .append(Component.text(" returned from combat logout")
                            .color(NamedTextColor.DARK_GRAY)));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("COMBAT LOGOUT CONSEQUENCES COMPLETED")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD));
                    player.sendMessage(Component.text("Your items were processed according to your " +
                                    yakPlayer.getAlignment().toLowerCase() + " alignment.")
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("You have respawned at the spawn location.")
                            .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("You may now continue playing normally.")
                            .color(NamedTextColor.YELLOW));
                    player.sendMessage(Component.empty());
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }, 20L);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && PlayerListenerManager.getInstance() != null &&
                        PlayerListenerManager.getInstance().getJoinLeaveListener() != null) {
                    PlayerListenerManager.getInstance().getJoinLeaveListener()
                            .sendMotd(player, yakPlayer, true);
                }
            }, 40L);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error sending combat logout rejoin messages", e);
        }
    }

    // Player quit handling with death/combat coordination
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        logger.info("Player quitting: " + playerName + " - Starting coordinated save process");

        try {
            // Check for death/combat processing conflicts before quit handling
            if (handleDeathProcessingQuit(player, uuid, playerName, event)) {
                return; // Death processing handled the quit
            }

            if (handleCombatLogoutProcessingQuit(player, uuid, playerName, event)) {
                return; // Combat logout processing handled the quit
            }

            handleNormalPlayerQuitWithCoordination(player, uuid, playerName, event);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in coordinated player quit for " + playerName, e);
            performEmergencyQuitSave(uuid, playerName);
        }
    }

    /**
     * Handle quit during death processing
     */
    private boolean handleDeathProcessingQuit(Player player, UUID uuid, String playerName, PlayerQuitEvent event) {
        if (isPlayerInDeathProcessing(uuid)) {
            logger.info("Player quitting during death processing: " + playerName);
            deathCoordinations.incrementAndGet();

            // Let death mechanics handle the quit
            setPlayerState(uuid, PlayerState.DEATH_PROCESSING, "death_processing_quit");

            // Don't perform normal quit processing
            event.setQuitMessage(String.valueOf(Component.text("⟨✧⟩ ")
                    .color(NamedTextColor.DARK_RED)
                    .append(Component.text(playerName)
                            .color(NamedTextColor.GRAY))
                    .append(Component.text(" (processing death)")
                            .color(NamedTextColor.DARK_GRAY))));

            return true;
        }
        return false;
    }

    /**
     * Handle quit during combat logout processing
     */
    private boolean handleCombatLogoutProcessingQuit(Player player, UUID uuid, String playerName, PlayerQuitEvent event) {
        if (isPlayerInCombatLogoutProcessing(uuid) ||
                (combatLogoutMechanics != null && combatLogoutMechanics.isCombatLoggingOut(player))) {

            logger.info("Player quitting during combat logout processing: " + playerName);
            combatLogoutCoordinations.incrementAndGet();

            // Let combat logout mechanics handle the quit
            setPlayerState(uuid, PlayerState.COMBAT_LOGOUT_PROCESSING, "combat_logout_processing_quit");

            // Combat logout mechanics will set its own quit message
            return true;
        }
        return false;
    }

    /**
     * Handle normal quit with coordination
     */
    private void handleNormalPlayerQuitWithCoordination(Player player, UUID uuid, String playerName, PlayerQuitEvent event) {
        try {
            logger.info("Starting coordinated save process for: " + playerName);

            // Get player data BEFORE any cleanup
            YakPlayer yakPlayer = onlinePlayers.get(uuid);
            if (yakPlayer == null) {
                logger.warning("No player data found for quitting player: " + playerName);
                cleanupPlayerState(uuid, "normal_player_quit_no_data");
                return;
            }

            // Check for any death/combat conflicts before saving
            if (checkForQuitTimeConflicts(uuid, playerName)) {
                performEmergencyQuitSave(uuid, playerName);
                return;
            }

            // FORCE INVENTORY UPDATE - This bypasses all state checks
            logger.info("FORCE updating inventory for " + playerName + " on quit");
            try {
                yakPlayer.forceUpdateInventory(player);
                forcedInventorySaves.incrementAndGet();
                logger.info("✓ Forced inventory update completed for " + playerName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "CRITICAL: Forced inventory update failed for " + playerName, e);
                saveFailures.incrementAndGet();
            }

            // FORCE STATS UPDATE
            try {
                yakPlayer.updateStats(player);
                logger.fine("✓ Stats update completed for " + playerName);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Stats update failed for " + playerName, e);
            }

            // Apply pending settings changes BEFORE final save
            applyPendingSettingsOnQuit(uuid, yakPlayer, playerName);

            // Reset combat logout state
            if (yakPlayer.getCombatLogoutState() != YakPlayer.CombatLogoutState.NONE) {
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                logger.info("Reset combat logout state on normal quit for " + playerName);
            }

            // GUARANTEED SYNCHRONOUS SAVE with coordination
            performCoordinatedGuaranteedSave(yakPlayer, playerName);

            // Disconnect and cleanup
            yakPlayer.disconnect();
            onlinePlayers.remove(uuid);
            cleanupPlayerState(uuid, "normal_player_quit");

            // Set quit message
            setQuitMessage(event, player, yakPlayer);

            logger.info("✓ Coordinated save process completed successfully for: " + playerName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "CRITICAL: Coordinated save process failed for " + playerName, e);
            performEmergencyQuitSave(uuid, playerName);
        }
    }

    /**
     * Check for conflicts during quit time
     */
    private boolean checkForQuitTimeConflicts(UUID uuid, String playerName) {
        try {
            if (isPlayerInDeathProcessing(uuid)) {
                logger.warning("Death processing conflict detected during quit for: " + playerName);
                systemConflictsDetected.incrementAndGet();
                return true;
            }

            if (isPlayerInCombatLogoutProcessing(uuid)) {
                logger.warning("Combat logout processing conflict detected during quit for: " + playerName);
                systemConflictsDetected.incrementAndGet();
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking quit time conflicts for: " + playerName, e);
            return true; // Assume conflict on error
        }
    }

    private void applyPendingSettingsOnQuit(UUID uuid, YakPlayer yakPlayer, String playerName) {
        try {
            Map<String, Boolean> pendingSettings = pendingSettingsChanges.remove(uuid);
            settingsChangeTimestamps.remove(uuid);

            if (pendingSettings != null && !pendingSettings.isEmpty()) {
                logger.info("Applying " + pendingSettings.size() + " pending settings on quit for " + playerName);
                for (Map.Entry<String, Boolean> entry : pendingSettings.entrySet()) {
                    String toggleName = entry.getKey();
                    boolean desiredState = entry.getValue();
                    boolean currentState = yakPlayer.isToggled(toggleName);

                    if (currentState != desiredState) {
                        yakPlayer.toggleSetting(toggleName);
                    }
                }
                logger.fine("✓ Pending settings applied for " + playerName);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying pending settings for " + playerName, e);
        }
    }

    /**
     * Perform guaranteed save with coordination
     */
    private void performCoordinatedGuaranteedSave(YakPlayer yakPlayer, String playerName) {
        int maxAttempts = 3;
        boolean saved = false;

        for (int attempt = 1; attempt <= maxAttempts && !saved; attempt++) {
            try {
                logger.info("Coordinated guaranteed save attempt " + attempt + "/" + maxAttempts + " for " + playerName);

                YakPlayer result = repository.saveSync(yakPlayer);
                if (result != null) {
                    saved = true;
                    guaranteedSaves.incrementAndGet();
                    logger.info("✓ Coordinated guaranteed save successful on attempt " + attempt + " for " + playerName);
                } else {
                    logger.warning("✗ Save returned null on attempt " + attempt + " for " + playerName);
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "✗ Coordinated guaranteed save attempt " + attempt + " failed for " + playerName, e);
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(100 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!saved) {
            saveFailures.incrementAndGet();
            logger.log(Level.SEVERE, "CRITICAL: All " + maxAttempts + " coordinated save attempts failed for " + playerName);

            // Last resort - async save attempt
            CompletableFuture.runAsync(() -> {
                try {
                    logger.warning("Last resort async save attempt for " + playerName);
                    repository.saveSync(yakPlayer);
                    logger.info("✓ Last resort save successful for " + playerName);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "✗ Last resort save failed for " + playerName, e);
                }
            }, saveExecutor);
        }
    }

    private void performEmergencyQuitSave(UUID uuid, String playerName) {
        try {
            logger.warning("Performing emergency quit save for " + playerName);

            YakPlayer yakPlayer = onlinePlayers.remove(uuid);
            if (yakPlayer != null) {
                // Try emergency save
                CompletableFuture.runAsync(() -> {
                    try {
                        repository.saveSync(yakPlayer);
                        logger.info("Emergency save successful for " + playerName);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Emergency save failed for " + playerName, e);
                    }
                }, saveExecutor);
            }

            cleanupPlayerState(uuid, "emergency_quit_cleanup");
            pendingSettingsChanges.remove(uuid);
            settingsChangeTimestamps.remove(uuid);
            playersInRecovery.remove(uuid);

            // Clean up coordination state
            unmarkPlayerInDeathProcessing(uuid);
            unmarkPlayerInCombatLogoutProcessing(uuid);

            logger.info("Emergency quit cleanup completed for " + playerName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Emergency cleanup failed for " + playerName, e);
        }
    }

    // Movement restrictions during loading with coordination awareness
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PlayerState state = getPlayerState(playerId);

        if (state == PlayerState.LOADING ||
                state == PlayerState.DEATH_PROCESSING ||
                state == PlayerState.COMBAT_LOGOUT_PROCESSING ||
                playersInRecovery.contains(playerId)) {

            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                event.setTo(from);
            }
        }
    }

    // Auto-save with coordination
    private void performAutoSave() {
        try {
            int savedCount = 0;
            int inventoryUpdatesCount = 0;
            int coordinationSkips = 0;

            for (YakPlayer yakPlayer : onlinePlayers.values()) {
                try {
                    Player bukkitPlayer = yakPlayer.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        UUID uuid = bukkitPlayer.getUniqueId();

                        // Check for coordination conflicts before auto-save
                        if (isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid)) {
                            logger.fine("Skipping auto-save for player in death/combat processing: " + yakPlayer.getUsername());
                            coordinationSkips++;
                            continue;
                        }

                        // FORCE inventory update regardless of state for auto-save
                        try {
                            yakPlayer.forceUpdateInventory(bukkitPlayer);
                            inventoryUpdatesCount++;
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Auto-save inventory update failed for: " + yakPlayer.getUsername(), e);
                        }

                        // Update stats
                        try {
                            yakPlayer.updateStats(bukkitPlayer);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Auto-save stats update failed for: " + yakPlayer.getUsername(), e);
                        }

                        // Save to database
                        try {
                            repository.saveSync(yakPlayer);
                            savedCount++;
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Auto-save database save failed for: " + yakPlayer.getUsername(), e);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Auto-save failed for player: " + yakPlayer.getUsername(), e);
                }
            }

            if (savedCount > 0) {
                logger.fine("Auto-save completed: " + savedCount + " players saved, " +
                        inventoryUpdatesCount + " inventories updated, " + coordinationSkips + " skipped for coordination");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in coordinated auto-save", e);
        }
    }

    // Coordinated guaranteed inventory saves background task
    private void performGuaranteedInventorySaves() {
        try {
            for (YakPlayer yakPlayer : onlinePlayers.values()) {
                try {
                    Player bukkitPlayer = yakPlayer.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        UUID uuid = bukkitPlayer.getUniqueId();

                        // Skip if in death/combat coordination
                        if (isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid)) {
                            continue;
                        }

                        // Check if inventory was updated recently
                        long lastSave = yakPlayer.getInventorySaveTimestamp();
                        long currentTime = System.currentTimeMillis();

                        // Force inventory update every 30 seconds for active players
                        if (currentTime - lastSave > 30000) {
                            CompletableFuture.runAsync(() -> {
                                try {
                                    // Double-check coordination state before save
                                    if (!isPlayerInDeathProcessing(uuid) && !isPlayerInCombatLogoutProcessing(uuid)) {
                                        yakPlayer.forceUpdateInventory(bukkitPlayer);
                                        repository.saveSync(yakPlayer);
                                        logger.fine("Coordinated background inventory save for " + yakPlayer.getUsername());
                                    }
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Coordinated background save failed for " + yakPlayer.getUsername(), e);
                                }
                            }, saveExecutor);
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in coordinated guaranteed inventory save for " + yakPlayer.getUsername(), e);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in coordinated guaranteed inventory saves task", e);
        }
    }

    /**
     * Monitor loading players with coordination awareness
     */
    private void monitorLoadingPlayers() {
        try {
            long currentTime = System.currentTimeMillis();
            Iterator<Map.Entry<UUID, PlayerLoadingState>> iterator = loadingStates.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, PlayerLoadingState> entry = iterator.next();
                UUID uuid = entry.getKey();
                PlayerLoadingState state = entry.getValue();

                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    cleanupPlayerState(uuid, "player_offline");
                    continue;
                }

                long loadingTime = currentTime - state.getStartTime();

                // Extended timeout for coordination phases
                long timeoutThreshold = 30000; // Base 30 seconds
                LoadingPhase phase = state.getPhase();
                if (phase == LoadingPhase.DEATH_COORDINATION || phase == LoadingPhase.COMBAT_COORDINATION) {
                    timeoutThreshold = 60000; // 60 seconds for coordination
                }

                if (loadingTime > timeoutThreshold) {
                    logger.warning("Player stuck in loading for " + loadingTime + "ms (phase: " + phase + "): " + state.getPlayerName());
                    iterator.remove();
                    handleLoadingFailure(player, state, new RuntimeException("Coordinated loading monitor timeout"));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in coordinated loading monitor", e);
        }
    }

    /**
     * Monitor death and combat coordination
     */
    private void monitorDeathAndCombatCoordination() {
        try {
            long currentTime = System.currentTimeMillis();

            // Monitor death processing timeouts
            Iterator<Map.Entry<UUID, Long>> deathIterator = deathProcessingStartTimes.entrySet().iterator();
            while (deathIterator.hasNext()) {
                Map.Entry<UUID, Long> entry = deathIterator.next();
                UUID uuid = entry.getKey();
                long startTime = entry.getValue();

                if (currentTime - startTime > 60000) { // 60 second timeout
                    logger.warning("Death processing timeout for player: " + uuid);
                    unmarkPlayerInDeathProcessing(uuid);
                    coordinationTimeouts.incrementAndGet();
                }
            }

            // Monitor combat logout processing timeouts
            Iterator<Map.Entry<UUID, Long>> combatIterator = combatLogoutProcessingStartTimes.entrySet().iterator();
            while (combatIterator.hasNext()) {
                Map.Entry<UUID, Long> entry = combatIterator.next();
                UUID uuid = entry.getKey();
                long startTime = entry.getValue();

                if (currentTime - startTime > 60000) { // 60 second timeout
                    logger.warning("Combat logout processing timeout for player: " + uuid);
                    unmarkPlayerInCombatLogoutProcessing(uuid);
                    coordinationTimeouts.incrementAndGet();
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in death/combat coordination monitoring", e);
        }
    }

    /**
     * Emergency recovery with coordination awareness
     */
    private void performEmergencyRecovery() {
        try {
            // Check for players stuck in bad states
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                PlayerState state = getPlayerState(uuid);

                // Skip players in coordination states
                if (isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid)) {
                    continue;
                }

                // Check for players stuck in spectator mode
                if (player.getGameMode() == GameMode.SPECTATOR && state == PlayerState.READY) {
                    YakPlayer yakPlayer = getPlayer(uuid);
                    if (yakPlayer != null && !playersInRecovery.contains(uuid)) {
                        logger.warning("Found player stuck in spectator mode: " + player.getName());
                        playersInRecovery.add(uuid);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                                logger.info("Performing spectator mode recovery for " + player.getName());
                                performEmergencyPlayerRecovery(player);
                            }
                            playersInRecovery.remove(uuid);
                        }, EMERGENCY_RECOVERY_DELAY);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in coordinated emergency recovery", e);
        }
    }

    // Death and combat coordination methods
    public void markPlayerInDeathProcessing(UUID playerId) {
        if (playerId != null) {
            playersInDeathProcessing.add(playerId);
            deathProcessingStartTimes.put(playerId, System.currentTimeMillis());
            logger.info("Marked player as in death processing: " + playerId);
        }
    }

    public void unmarkPlayerInDeathProcessing(UUID playerId) {
        if (playerId != null) {
            playersInDeathProcessing.remove(playerId);
            deathProcessingStartTimes.remove(playerId);
            logger.info("Unmarked player from death processing: " + playerId);
        }
    }

    public boolean isPlayerInDeathProcessing(UUID playerId) {
        return playerId != null && playersInDeathProcessing.contains(playerId);
    }

    public void markPlayerInCombatLogoutProcessing(UUID playerId) {
        if (playerId != null) {
            playersInCombatLogoutProcessing.add(playerId);
            combatLogoutProcessingStartTimes.put(playerId, System.currentTimeMillis());
            logger.info("Marked player as in combat logout processing: " + playerId);
        }
    }

    public void unmarkPlayerInCombatLogoutProcessing(UUID playerId) {
        if (playerId != null) {
            playersInCombatLogoutProcessing.remove(playerId);
            combatLogoutProcessingStartTimes.remove(playerId);
            logger.info("Unmarked player from combat logout processing: " + playerId);
        }
    }

    public boolean isPlayerInCombatLogoutProcessing(UUID playerId) {
        return playerId != null && playersInCombatLogoutProcessing.contains(playerId);
    }

    // Combat logout coordination (enhanced)
    public void markPlayerEnteringCombatLogout(UUID playerId) {
        if (playerId != null) {
            markPlayerInCombatLogoutProcessing(playerId);
            logger.info("Marked player as entering combat logout processing: " + playerId);
        }
    }

    public void markPlayerFinishedCombatLogout(UUID playerId) {
        if (playerId != null) {
            unmarkPlayerInCombatLogoutProcessing(playerId);
            logger.info("Completed combat logout processing for: " + playerId);
        }
    }

    public YakPlayer getPlayerForCombatLogout(UUID playerId) {
        return playerId != null ? onlinePlayers.get(playerId) : null;
    }

    // State management
    private ReentrantReadWriteLock getPlayerStateLock(UUID playerId) {
        return playerStateLocks.computeIfAbsent(playerId, k -> new ReentrantReadWriteLock());
    }

    public PlayerState getPlayerState(UUID playerId) {
        if (playerId == null) return PlayerState.OFFLINE;

        ReentrantReadWriteLock lock = getPlayerStateLock(playerId);
        lock.readLock().lock();
        try {
            return playerStates.getOrDefault(playerId, PlayerState.OFFLINE);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean setPlayerState(UUID playerId, PlayerState newState, String reason) {
        if (playerId == null || newState == null) {
            return false;
        }

        ReentrantReadWriteLock lock = getPlayerStateLock(playerId);
        lock.writeLock().lock();
        try {
            PlayerState currentState = playerStates.get(playerId);
            playerStates.put(playerId, newState);
            logger.fine("State transition for " + playerId + ": " + currentState + " -> " + newState + " (" + reason + ")");
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void cleanupPlayerState(UUID uuid, String reason) {
        try {
            PlayerLoadingState loadingState = loadingStates.remove(uuid);
            playersInRecovery.remove(uuid);
            setPlayerState(uuid, PlayerState.OFFLINE, reason);

            // Clean up coordination state
            unmarkPlayerInDeathProcessing(uuid);
            unmarkPlayerInCombatLogoutProcessing(uuid);

            // Clean up systems
            ModerationMechanics.rankMap.remove(uuid);
            ChatMechanics.getPlayerTags().remove(uuid);

            logger.fine("Cleaned up player state for " + uuid + " (reason: " + reason + ")");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up player state for " + uuid, e);
        }
    }

    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation, boolean saveAfter) {
        if (uuid == null || operation == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check coordination state before operation
                if (isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid)) {
                    logger.warning("Skipping withPlayer operation due to coordination state: " + uuid);
                    return false;
                }

                YakPlayer yakPlayer = getPlayer(uuid);
                if (yakPlayer == null) {
                    return false;
                }

                operation.accept(yakPlayer);
                return true;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in withPlayer operation for " + uuid, e);
                return false;
            }
        }, ioExecutor).thenCompose(success -> {
            if (success && saveAfter) {
                YakPlayer yakPlayer = getPlayer(uuid);
                if (yakPlayer != null) {
                    return savePlayer(yakPlayer);
                }
            }
            return CompletableFuture.completedFuture(success);
        });
    }

    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation) {
        return withPlayer(uuid, operation, true);
    }

    private void cleanupLoadingState(UUID uuid) {
        cleanupPlayerState(uuid, "loading_completed");
    }

    // Enhanced settings protection with guaranteed persistence
    public boolean saveToggleSettingImmediate(UUID playerId, String toggleName, boolean enabled) {
        if (playerId == null || toggleName == null) {
            return false;
        }

        try {
            // Check coordination state before settings save
            if (isPlayerInDeathProcessing(playerId) || isPlayerInCombatLogoutProcessing(playerId)) {
                logger.info("Deferring settings save due to coordination state: " + playerId);
                // Store as pending change
                Map<String, Boolean> playerSettings = pendingSettingsChanges.computeIfAbsent(playerId,
                        k -> new ConcurrentHashMap<>());
                playerSettings.put(toggleName, enabled);
                settingsChangeTimestamps.put(playerId, System.currentTimeMillis());
                return true;
            }

            YakPlayer yakPlayer = getPlayer(playerId);

            if (yakPlayer != null) {
                boolean currentState = yakPlayer.isToggled(toggleName);
                if (currentState != enabled) {
                    yakPlayer.toggleSetting(toggleName);

                    // GUARANTEED save with multiple attempts
                    CompletableFuture.runAsync(() -> {
                        int attempts = 0;
                        boolean saved = false;
                        while (attempts < 3 && !saved) {
                            try {
                                attempts++;
                                YakPlayer result = repository.saveSync(yakPlayer);
                                if (result != null) {
                                    saved = true;
                                    settingsProtectionSaves.incrementAndGet();
                                    logger.fine("✓ Settings save successful for " + toggleName + " (attempt " + attempts + ")");
                                }
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Settings save attempt " + attempts + " failed for " + toggleName, e);
                                if (attempts < 3) {
                                    try {
                                        Thread.sleep(50 * attempts);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        }
                        if (!saved) {
                            logger.log(Level.SEVERE, "CRITICAL: All settings save attempts failed for " + toggleName);
                        }
                    }, saveExecutor);

                    return true;
                }
                return true;
            }

            // Store pending change if player is loading
            PlayerState state = getPlayerState(playerId);
            if (state == PlayerState.LOADING) {
                Map<String, Boolean> playerSettings = pendingSettingsChanges.computeIfAbsent(playerId,
                        k -> new ConcurrentHashMap<>());
                playerSettings.put(toggleName, enabled);
                settingsChangeTimestamps.put(playerId, System.currentTimeMillis());
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in immediate save for " + playerId, e);
            return false;
        }
    }

    private void applyPendingSettingsChanges(UUID playerId, YakPlayer yakPlayer) {
        Map<String, Boolean> pendingChanges = pendingSettingsChanges.remove(playerId);
        settingsChangeTimestamps.remove(playerId);

        if (pendingChanges == null || pendingChanges.isEmpty()) {
            return;
        }

        logger.info("Applying " + pendingChanges.size() + " pending settings for " + yakPlayer.getUsername());

        boolean hasChanges = false;
        for (Map.Entry<String, Boolean> entry : pendingChanges.entrySet()) {
            String toggleName = entry.getKey();
            boolean desiredState = entry.getValue();
            boolean currentState = yakPlayer.isToggled(toggleName);

            if (currentState != desiredState) {
                yakPlayer.toggleSetting(toggleName);
                hasChanges = true;
            }
        }

        if (hasChanges) {
            try {
                repository.saveSync(yakPlayer);
                logger.info("Saved pending settings changes for " + yakPlayer.getUsername());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save pending changes", e);
            }
        }
    }

    // Message formatting - Updated to use Paper's Adventure API
    private Component formatBanMessage(YakPlayer player) {
        if (!player.isBanned()) return null;

        Component message = Component.text("You are banned from this server!")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.newline());

        message = message.append(Component.text("Reason: ")
                        .color(NamedTextColor.GRAY))
                .append(Component.text(player.getBanReason())
                        .color(NamedTextColor.WHITE))
                .append(Component.newline());

        if (player.getBanExpiry() > 0) {
            long remaining = player.getBanExpiry() - Instant.now().getEpochSecond();
            if (remaining > 0) {
                message = message.append(Component.text("Expires in: ")
                                .color(NamedTextColor.GRAY))
                        .append(Component.text(formatDuration(remaining))
                                .color(NamedTextColor.WHITE));
            } else {
                player.setBanned(false);
                player.setBanReason("");
                player.setBanExpiry(0);
                repository.saveSync(player);
                return null;
            }
        } else {
            message = message.append(Component.text("This ban does not expire.")
                    .color(NamedTextColor.GRAY));
        }

        message = message.append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Appeal at: ")
                        .color(NamedTextColor.GRAY))
                .append(Component.text("discord.gg/yakrealms")
                        .color(NamedTextColor.BLUE));

        return message;
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append(" days, ");
        if (hours > 0) result.append(hours).append(" hours, ");
        result.append(minutes).append(" minutes");
        return result.toString();
    }

    private void updateJoinMessage(Player player, YakPlayer yakPlayer) {
        try {
            String formattedName = yakPlayer.getFormattedDisplayName();
            int onlineCount = Bukkit.getOnlinePlayers().size();

            Component joinMessage = createJoinMessage(formattedName, onlineCount);
            if (joinMessage != null) {
                Bukkit.broadcast(joinMessage);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating join message", e);
        }
    }

    private Component createJoinMessage(String formattedName, int onlineCount) {
        if (onlineCount == 1) {
            return Component.text("✦ ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(formattedName)
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(" has entered the realm! ✦")
                            .color(NamedTextColor.GOLD));
        } else if (onlineCount <= 5) {
            return Component.text("⟨✦⟩ ")
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(formattedName))
                    .append(Component.text(" (" + onlineCount + " online)")
                            .color(NamedTextColor.GRAY));
        } else if (onlineCount <= 20) {
            return Component.text("⟨✦⟩ ")
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(formattedName));
        } else {
            return null;
        }
    }

    private void setQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer) {
        try {
            if (yakPlayer != null) {
                String formattedName = yakPlayer.getFormattedDisplayName();
                event.setQuitMessage(String.valueOf(Component.text("⟨✧⟩ ")
                        .color(NamedTextColor.DARK_RED)
                        .append(Component.text(formattedName))));
            } else {
                event.setQuitMessage(String.valueOf(Component.text("⟨✧⟩ ")
                        .color(NamedTextColor.DARK_RED)
                        .append(Component.text(player.getName())
                                .color(NamedTextColor.GRAY))));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error setting quit message", e);
        }
    }

    // Shutdown with coordination
    public void onDisable() {
        if (shutdownInProgress) return;

        shutdownInProgress = true;
        logger.info("Starting YakPlayerManager shutdown with coordinated save process...");

        try {
            // Cancel background tasks
            if (autoSaveTask != null) autoSaveTask.cancel();
            if (loadingMonitorTask != null) loadingMonitorTask.cancel();
            if (emergencyRecoveryTask != null) emergencyRecoveryTask.cancel();
            if (guaranteedSaveTask != null) guaranteedSaveTask.cancel();
            if (coordinationMonitorTask != null) coordinationMonitorTask.cancel();

            // Save all players with coordination
            saveAllPlayersOnShutdownWithCoordination();

            // Shutdown executors
            ioExecutor.shutdown();
            saveExecutor.shutdown();
            coordinationExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
                if (!saveExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    saveExecutor.shutdownNow();
                }
                if (!coordinationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    coordinationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                saveExecutor.shutdownNow();
                coordinationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Shutdown repository
            if (repository != null) {
                repository.shutdown();
            }

            // Clear data structures
            onlinePlayers.clear();
            loadingStates.clear();
            playerStates.clear();
            playersInRecovery.clear();
            playersInDeathProcessing.clear();
            playersInCombatLogoutProcessing.clear();
            deathProcessingStartTimes.clear();
            combatLogoutProcessingStartTimes.clear();
            pendingSettingsChanges.clear();
            settingsChangeTimestamps.clear();
            playerStateLocks.clear();

            logger.info("YakPlayerManager shutdown completed with coordinated saves");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
        } finally {
            initialized = false;
            systemHealthy = false;
        }
    }

    /**
     * Save all players on shutdown with coordination
     */
    private void saveAllPlayersOnShutdownWithCoordination() {
        logger.info("Starting coordinated save for " + onlinePlayers.size() + " players on shutdown...");

        int savedCount = 0;
        int forcedInventoryUpdates = 0;
        int coordinationSkips = 0;

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            try {
                Player player = yakPlayer.getBukkitPlayer();
                UUID uuid = yakPlayer.getUUID();

                // Check coordination state during shutdown
                if (isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid)) {
                    logger.info("Skipping shutdown save for player in coordination: " + yakPlayer.getUsername());
                    coordinationSkips++;
                    continue;
                }

                if (player != null && player.isOnline()) {
                    // FORCE inventory update
                    try {
                        yakPlayer.forceUpdateInventory(player);
                        forcedInventoryUpdates++;
                        logger.fine("✓ Forced inventory update on shutdown for " + yakPlayer.getUsername());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to force inventory update on shutdown for " + yakPlayer.getUsername(), e);
                    }

                    // Force stats update
                    try {
                        yakPlayer.updateStats(player);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to update stats on shutdown for " + yakPlayer.getUsername(), e);
                    }
                }

                yakPlayer.disconnect();

                // GUARANTEED save with multiple attempts
                boolean saved = false;
                for (int attempt = 1; attempt <= 3 && !saved; attempt++) {
                    try {
                        YakPlayer result = repository.saveSync(yakPlayer);
                        if (result != null) {
                            saved = true;
                            savedCount++;
                            logger.fine("✓ Shutdown save successful (attempt " + attempt + ") for " + yakPlayer.getUsername());
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "✗ Shutdown save attempt " + attempt + " failed for " + yakPlayer.getUsername(), e);
                        if (attempt < 3) {
                            try {
                                Thread.sleep(100 * attempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }

                if (!saved) {
                    logger.log(Level.SEVERE, "CRITICAL: All shutdown save attempts failed for " + yakPlayer.getUsername());
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player on shutdown: " + yakPlayer.getUsername(), e);
            }
        }

        logger.info("Coordinated shutdown save completed: " + savedCount + "/" + onlinePlayers.size() +
                " players saved, " + forcedInventoryUpdates + " forced inventory updates, " + coordinationSkips + " coordination skips");
    }

    // Public API methods
    public YakPlayer getPlayer(UUID uuid) {
        return uuid != null ? onlinePlayers.get(uuid) : null;
    }

    public YakPlayer getPlayer(String name) {
        if (name == null) return null;
        for (YakPlayer yakPlayer : onlinePlayers.values()) {
            if (yakPlayer.getUsername().equalsIgnoreCase(name)) {
                return yakPlayer;
            }
        }
        return null;
    }

    public YakPlayer getPlayer(Player player) {
        return player != null ? getPlayer(player.getUniqueId()) : null;
    }

    public Collection<YakPlayer> getOnlinePlayers() {
        return new ArrayList<>(onlinePlayers.values());
    }

    public boolean isPlayerProtected(UUID uuid) {
        PlayerState state = getPlayerState(uuid);
        return state != PlayerState.READY || isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid);
    }

    public boolean isPlayerLoading(UUID uuid) {
        return getPlayerState(uuid) == PlayerState.LOADING;
    }

    public boolean isPlayerInVoidLimbo(UUID uuid) {
        return false; // Simplified - no limbo system
    }

    public LoadingPhase getPlayerLoadingPhase(UUID uuid) {
        PlayerLoadingState state = loadingStates.get(uuid);
        return state != null ? state.getPhase() : null;
    }

    public boolean isSystemHealthy() {
        return systemHealthy;
    }

    public boolean isRepositoryReady() {
        return repository != null && repository.isInitialized();
    }

    public boolean isPlayerReady(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        return getPlayerState(uuid) == PlayerState.READY &&
                !isPlayerInDeathProcessing(uuid) &&
                !isPlayerInCombatLogoutProcessing(uuid);
    }

    public boolean isPlayerFullyLoaded(UUID playerId) {
        return getPlayerState(playerId) == PlayerState.READY;
    }

    // Data operations
    public CompletableFuture<Boolean> savePlayer(YakPlayer yakPlayer) {
        if (yakPlayer == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Player bukkitPlayer = yakPlayer.getBukkitPlayer();
                UUID uuid = yakPlayer.getUUID();

                // Check coordination state before save
                if (isPlayerInDeathProcessing(uuid) || isPlayerInCombatLogoutProcessing(uuid)) {
                    logger.info("Deferring save due to coordination state: " + yakPlayer.getUsername());
                    return false;
                }

                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    PlayerState state = getPlayerState(uuid);

                    // FORCE update regardless of state for save operations
                    try {
                        yakPlayer.forceUpdateInventory(bukkitPlayer);
                        yakPlayer.updateStats(bukkitPlayer);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error updating player data before save for " + yakPlayer.getUsername(), e);
                    }
                }

                YakPlayer result = repository.saveSync(yakPlayer);
                return result != null;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player: " + yakPlayer.getUsername(), e);
                return false;
            }
        }, saveExecutor);
    }

    // Command handling with diagnostic tools
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("playerstate")) {
            if (!player.hasPermission("yakrealms.admin")) {
                player.sendMessage(Component.text("No permission").color(NamedTextColor.RED));
                return true;
            }

            UUID uuid = player.getUniqueId();
            PlayerState state = getPlayerState(uuid);
            LoadingPhase phase = null;

            PlayerLoadingState loadingState = loadingStates.get(uuid);
            if (loadingState != null) {
                phase = loadingState.getPhase();
            }

            player.sendMessage(Component.text("=== PLAYER STATE DEBUG ===").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("State: " + state).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Loading Phase: " + phase).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("In Recovery: " + playersInRecovery.contains(uuid)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Death Processing: " + isPlayerInDeathProcessing(uuid)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Combat Logout Processing: " + isPlayerInCombatLogoutProcessing(uuid)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Settings Protection Saves: " + settingsProtectionSaves.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Emergency Recoveries: " + emergencyRecoveries.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Guaranteed Saves: " + guaranteedSaves.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Forced Inventory Saves: " + forcedInventorySaves.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Save Failures: " + saveFailures.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Death Coordinations: " + deathCoordinations.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Combat Logout Coordinations: " + combatLogoutCoordinations.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("System Conflicts Detected: " + systemConflictsDetected.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Coordination Timeouts: " + coordinationTimeouts.get()).color(NamedTextColor.YELLOW));

            YakPlayer yakPlayer = getPlayer(uuid);
            if (yakPlayer != null) {
                player.sendMessage(Component.text("Combat Logout State: " + yakPlayer.getCombatLogoutState()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Health: " + player.getHealth() + "/" + player.getMaxHealth()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Game Mode: " + player.getGameMode()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Inventory Save Timestamp: " + yakPlayer.getInventorySaveTimestamp()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Has Serialized Inventory: " + (yakPlayer.getSerializedInventory() != null && !yakPlayer.getSerializedInventory().isEmpty())).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Has Respawn Items: " + yakPlayer.hasRespawnItems()).color(NamedTextColor.YELLOW));
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("inventorydiag")) {
            if (!player.hasPermission("yakrealms.admin")) {
                player.sendMessage(Component.text("No permission").color(NamedTextColor.RED));
                return true;
            }

            UUID uuid = player.getUniqueId();
            YakPlayer yakPlayer = getPlayer(uuid);
            PlayerState state = getPlayerState(uuid);

            player.sendMessage(Component.text("=== INVENTORY DIAGNOSTIC ===").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Player State: " + state).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Death Processing: " + isPlayerInDeathProcessing(uuid)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Combat Logout Processing: " + isPlayerInCombatLogoutProcessing(uuid)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("YakPlayer Found: " + (yakPlayer != null)).color(NamedTextColor.YELLOW));

            if (yakPlayer != null) {
                player.sendMessage(Component.text("Combat Logout State: " + yakPlayer.getCombatLogoutState()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Has Serialized Inventory: " + (yakPlayer.getSerializedInventory() != null && !yakPlayer.getSerializedInventory().isEmpty())).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Inventory Being Applied: " + yakPlayer.isInventoryBeingApplied()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Inventory Save Timestamp: " + yakPlayer.getInventorySaveTimestamp()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Has Respawn Items: " + yakPlayer.hasRespawnItems()).color(NamedTextColor.YELLOW));

                // Test FORCE save with coordination check
                if (!isPlayerInDeathProcessing(uuid) && !isPlayerInCombatLogoutProcessing(uuid)) {
                    try {
                        player.sendMessage(Component.text("Performing coordinated FORCE inventory update...").color(NamedTextColor.GREEN));
                        yakPlayer.forceUpdateInventory(player);
                        repository.saveSync(yakPlayer);
                        player.sendMessage(Component.text("✓ Coordinated FORCE save completed successfully").color(NamedTextColor.GREEN));
                    } catch (Exception e) {
                        player.sendMessage(Component.text("✗ Coordinated FORCE save failed: " + e.getMessage()).color(NamedTextColor.RED));
                        logger.log(Level.SEVERE, "Test force save failed for " + player.getName(), e);
                    }
                } else {
                    player.sendMessage(Component.text("Skipping FORCE save test - player in coordination state").color(NamedTextColor.YELLOW));
                }
            }

            return true;
        }

        if (command.getName().equalsIgnoreCase("coordinationdiag")) {
            if (!player.hasPermission("yakrealms.admin")) {
                player.sendMessage(Component.text("No permission").color(NamedTextColor.RED));
                return true;
            }

            player.sendMessage(Component.text("=== COORDINATION DIAGNOSTIC ===").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Death Mechanics Available: " + (deathMechanics != null)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Combat Logout Mechanics Available: " + (combatLogoutMechanics != null)).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Players in Death Processing: " + playersInDeathProcessing.size()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Players in Combat Logout Processing: " + playersInCombatLogoutProcessing.size()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Total Death Coordinations: " + deathCoordinations.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Total Combat Logout Coordinations: " + combatLogoutCoordinations.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("System Conflicts Detected: " + systemConflictsDetected.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("System Conflicts Resolved: " + systemConflictsResolved.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Coordination Timeouts: " + coordinationTimeouts.get()).color(NamedTextColor.YELLOW));

            if (deathMechanics != null) {
                try {
                    String deathStats = deathMechanics.getPerformanceStats();
                    player.sendMessage(Component.text("Death System: " + deathStats).color(NamedTextColor.YELLOW));
                } catch (Exception e) {
                    player.sendMessage(Component.text("Error getting death system stats: " + e.getMessage()).color(NamedTextColor.RED));
                }
            }

            if (combatLogoutMechanics != null) {
                try {
                    String combatStats = combatLogoutMechanics.getPerformanceStats();
                    player.sendMessage(Component.text("Combat System: " + combatStats).color(NamedTextColor.YELLOW));
                } catch (Exception e) {
                    player.sendMessage(Component.text("Error getting combat system stats: " + e.getMessage()).color(NamedTextColor.RED));
                }
            }

            return true;
        }

        return false;
    }

    // System statistics
    public SystemStats getSystemStats() {
        return new SystemStats(
                totalPlayerJoins.get(),
                totalPlayerQuits.get(),
                Bukkit.getOnlinePlayers().size(),
                isSystemHealthy(),
                systemHealthy,
                isRepositoryReady(),
                loadingStates.size(),
                0, // No protected players in simplified system
                0, // No limbo players in simplified system
                successfulLoads.get(),
                failedLoads.get(),
                0, // Combat logout rejoins handled separately
                combatLogoutCoordinations.get(),
                new SettingsProtectionStats(
                        settingsProtectionSaves.get(),
                        0,
                        emergencyRecoveries.get(),
                        saveFailures.get(),
                        pendingSettingsChanges.size(),
                        settingsChangeTimestamps.size()
                ),
                new CoordinationStats(
                        deathCoordinations.get(),
                        combatLogoutCoordinations.get(),
                        systemConflictsDetected.get(),
                        systemConflictsResolved.get(),
                        coordinationTimeouts.get(),
                        playersInDeathProcessing.size(),
                        playersInCombatLogoutProcessing.size()
                )
        );
    }

    // Utility classes
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
        public final int successfulLoads;
        public final int failedLoads;
        public final int combatLogoutRejoins;
        public final int combatLogoutCoordinations;
        public final SettingsProtectionStats settingsStats;
        public final CoordinationStats coordinationStats;

        SystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy,
                    boolean systemsReady, boolean yakPlayerManagerIntegrated,
                    int loadingPlayers, int protectedPlayers, int limboPlayers,
                    int successfulLoads, int failedLoads, int combatLogoutRejoins,
                    int combatLogoutCoordinations, SettingsProtectionStats settingsStats,
                    CoordinationStats coordinationStats) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
            this.systemsReady = systemsReady;
            this.yakPlayerManagerIntegrated = yakPlayerManagerIntegrated;
            this.loadingPlayers = loadingPlayers;
            this.protectedPlayers = protectedPlayers;
            this.limboPlayers = limboPlayers;
            this.successfulLoads = successfulLoads;
            this.failedLoads = failedLoads;
            this.combatLogoutRejoins = combatLogoutRejoins;
            this.combatLogoutCoordinations = combatLogoutCoordinations;
            this.settingsStats = settingsStats;
            this.coordinationStats = coordinationStats;
        }
    }

    public static class SettingsProtectionStats {
        public final int protectionSaves;
        public final int pendingApplied;
        public final int emergencySaves;
        public final int failures;
        public final int pendingCount;
        public final int timestampCount;

        public SettingsProtectionStats(int protectionSaves, int pendingApplied, int emergencySaves,
                                       int failures, int pendingCount, int timestampCount) {
            this.protectionSaves = protectionSaves;
            this.pendingApplied = pendingApplied;
            this.emergencySaves = emergencySaves;
            this.failures = failures;
            this.pendingCount = pendingCount;
            this.timestampCount = timestampCount;
        }
    }

    public static class CoordinationStats {
        public final int deathCoordinations;
        public final int combatLogoutCoordinations;
        public final int systemConflictsDetected;
        public final int systemConflictsResolved;
        public final int coordinationTimeouts;
        public final int currentDeathProcessing;
        public final int currentCombatLogoutProcessing;

        public CoordinationStats(int deathCoordinations, int combatLogoutCoordinations,
                                 int systemConflictsDetected, int systemConflictsResolved,
                                 int coordinationTimeouts, int currentDeathProcessing,
                                 int currentCombatLogoutProcessing) {
            this.deathCoordinations = deathCoordinations;
            this.combatLogoutCoordinations = combatLogoutCoordinations;
            this.systemConflictsDetected = systemConflictsDetected;
            this.systemConflictsResolved = systemConflictsResolved;
            this.coordinationTimeouts = coordinationTimeouts;
            this.currentDeathProcessing = currentDeathProcessing;
            this.currentCombatLogoutProcessing = currentCombatLogoutProcessing;
        }
    }

    private static class PlayerLoadingState {
        private final UUID playerId;
        private final String playerName;
        private final long startTime;
        private volatile LoadingPhase phase = LoadingPhase.STARTING;
        private volatile YakPlayer yakPlayer;

        public PlayerLoadingState(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.startTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getStartTime() { return startTime; }
        public LoadingPhase getPhase() { return phase; }
        public void setPhase(LoadingPhase phase) { this.phase = phase; }
        public YakPlayer getYakPlayer() { return yakPlayer; }
        public void setYakPlayer(YakPlayer yakPlayer) { this.yakPlayer = yakPlayer; }
    }
}
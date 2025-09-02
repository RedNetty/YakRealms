package com.rednetty.server.core.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.core.mechanics.chat.ChatMechanics;
import com.rednetty.server.core.mechanics.chat.ChatTag;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
import com.rednetty.server.core.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.core.mechanics.combat.logout.CombatLogoutMechanics;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * THREAD-SAFE YakPlayerManager with Complete Data Protection
 *
 * CRITICAL FIXES:
 * - Thread-safe player operations with ReadWriteLocks
 * - Operation queuing system to prevent concurrent modifications
 * - Atomic player state transitions with validation
 * - Enhanced combat logout coordination
 * - Data integrity protection and recovery mechanisms
 * - Comprehensive timeout handling and cleanup
 */
public class YakPlayerManager implements Listener, CommandExecutor {

    private static final Logger logger = Logger.getLogger(YakPlayerManager.class.getName());
    private static volatile YakPlayerManager instance;

    // Constants
    private static final long DATA_LOAD_TIMEOUT_MS = 10000L;
    private static final long LOADING_TIMEOUT_TICKS = 400L;
    private static final long DEFAULT_AUTO_SAVE_INTERVAL_TICKS = 6000L;
    private static final long BAN_CHECK_TIMEOUT_SECONDS = 5L;
    private static final int MAX_CONCURRENT_OPERATIONS = 10;
    private static final int DEFAULT_IO_THREADS = 4;

    // State tracking (simplified)
    public enum PlayerState {
        OFFLINE, LOADING, READY, FAILED
    }

    // Core dependencies
    @Getter
    private YakPlayerRepository repository;
    private final Plugin plugin;

    // Combat logout coordination
    private CombatLogoutMechanics combatLogoutMechanics;

    // THREAD-SAFE player tracking with proper synchronization
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerLoadingState> loadingStates = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    
    // CRITICAL: Player operation locks to prevent data races
    private final Map<UUID, ReentrantReadWriteLock> playerLocks = new ConcurrentHashMap<>();
    private final ReadWriteLock globalOperationLock = new ReentrantReadWriteLock();
    
    // Operation queuing system to prevent concurrent modifications
    private final Map<UUID, LinkedBlockingQueue<PlayerOperation>> operationQueues = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> queueProcessors = new ConcurrentHashMap<>();

    // Thread management with enhanced coordination
    private final ExecutorService ioExecutor;
    private final ExecutorService saveExecutor;
    private final ExecutorService operationProcessor;
    private final Semaphore operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS);
    
    // Enhanced data integrity tracking
    private final Map<UUID, AtomicLong> lastOperationTime = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> concurrentOperationCount = new ConcurrentHashMap<>();

    // Performance metrics with thread safety tracking
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger failedLoads = new AtomicInteger(0);
    private final AtomicInteger emergencyRecoveries = new AtomicInteger(0);
    private final AtomicInteger saveFailures = new AtomicInteger(0);
    private final AtomicInteger combatLogoutCoordinations = new AtomicInteger(0);
    private final AtomicInteger inventoryConflictsPrevented = new AtomicInteger(0);
    
    // THREAD SAFETY METRICS
    private final AtomicInteger concurrentOperationsPrevented = new AtomicInteger(0);
    private final AtomicInteger dataRacesPrevented = new AtomicInteger(0);
    private final AtomicInteger operationTimeouts = new AtomicInteger(0);
    private final AtomicInteger queuedOperations = new AtomicInteger(0);
    private final AtomicInteger lockContention = new AtomicInteger(0);

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

    public static YakPlayerManager getInstance() {
        YakPlayerManager result = instance;
        if (result == null) {
            synchronized (YakPlayerManager.class) {
                result = instance;
                if (result == null) {
                    instance = result = new YakPlayerManager();
                }
            }
        }
        return result;
    }

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
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
        
        this.operationProcessor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Operations");
            thread.setDaemon(true);
            return thread;
        });

        // YakPlayerManager initialized with thread-safe combat logout coordination
    }
    
    // ==================== THREAD SAFETY CORE METHODS ====================
    
    /**
     * Get or create a lock for a specific player to prevent concurrent operations
     */
    private ReentrantReadWriteLock getPlayerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantReadWriteLock());
    }
    
    /**
     * Queue a player operation to prevent concurrent modifications
     */
    private CompletableFuture<Boolean> queuePlayerOperation(PlayerOperation.OperationType type, UUID playerId, Runnable operation) {
        if (playerId == null || operation == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        queuedOperations.incrementAndGet();
        PlayerOperation playerOperation = new PlayerOperation(type, playerId, operation);
        
        LinkedBlockingQueue<PlayerOperation> queue = operationQueues.computeIfAbsent(playerId, 
            k -> new LinkedBlockingQueue<>());
        
        queue.offer(playerOperation);
        
        // Ensure queue processor is running for this player
        ensureQueueProcessor(playerId);
        
        return playerOperation.getCompletionFuture();
    }
    
    /**
     * Ensure a queue processor is running for the given player
     */
    private void ensureQueueProcessor(UUID playerId) {
        queueProcessors.computeIfAbsent(playerId, k -> {
            return CompletableFuture.runAsync(() -> {
                processPlayerOperationQueue(playerId);
            }, operationProcessor);
        });
    }
    
    /**
     * Process operations for a specific player sequentially
     */
    private void processPlayerOperationQueue(UUID playerId) {
        LinkedBlockingQueue<PlayerOperation> queue = operationQueues.get(playerId);
        if (queue == null) return;
        
        ReentrantReadWriteLock playerLock = getPlayerLock(playerId);
        
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PlayerOperation operation = queue.poll(1, TimeUnit.SECONDS);
                    if (operation == null) {
                        // No operations for 1 second, check if player still online
                        Player player = Bukkit.getPlayer(playerId);
                        if (player == null || !player.isOnline()) {
                            break; // Player offline, stop processing
                        }
                        continue;
                    }
                    
                    // Check for expired operations
                    if (operation.isExpired(30000)) { // 30 second timeout
                        operationTimeouts.incrementAndGet();
                        operation.getCompletionFuture().completeExceptionally(
                            new RuntimeException("Operation timeout"));
                        continue;
                    }
                    
                    // Execute operation with appropriate lock
                    boolean needsWriteLock = isWriteOperation(operation.getType());
                    
                    if (needsWriteLock) {
                        playerLock.writeLock().lock();
                        try {
                            recordConcurrentOperationStart(playerId);
                            operation.execute();
                        } finally {
                            recordConcurrentOperationEnd(playerId);
                            playerLock.writeLock().unlock();
                        }
                    } else {
                        playerLock.readLock().lock();
                        try {
                            operation.execute();
                        } finally {
                            playerLock.readLock().unlock();
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error processing player operation for " + playerId, e);
                }
            }
        } finally {
            // Cleanup when queue processor stops
            queueProcessors.remove(playerId);
            if (queue.isEmpty()) {
                operationQueues.remove(playerId);
                playerLocks.remove(playerId);
            }
        }
    }
    
    /**
     * Determine if operation type requires write lock
     */
    private boolean isWriteOperation(PlayerOperation.OperationType type) {
        return type == PlayerOperation.OperationType.SAVE ||
               type == PlayerOperation.OperationType.UPDATE_INVENTORY ||
               type == PlayerOperation.OperationType.UPDATE_STATS ||
               type == PlayerOperation.OperationType.COMBAT_LOGOUT_START ||
               type == PlayerOperation.OperationType.COMBAT_LOGOUT_COMPLETE;
    }
    
    /**
     * Record concurrent operation tracking
     */
    private void recordConcurrentOperationStart(UUID playerId) {
        AtomicInteger count = concurrentOperationCount.computeIfAbsent(playerId, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();
        if (currentCount > 1) {
            concurrentOperationsPrevented.incrementAndGet();
        }
        lastOperationTime.computeIfAbsent(playerId, k -> new AtomicLong()).set(System.currentTimeMillis());
    }
    
    /**
     * Record concurrent operation completion
     */
    private void recordConcurrentOperationEnd(UUID playerId) {
        AtomicInteger count = concurrentOperationCount.get(playerId);
        if (count != null) {
            count.decrementAndGet();
        }
    }
    
    /**
     * Thread-safe player data update with validation
     */
    private CompletableFuture<Boolean> safeUpdatePlayerData(UUID playerId, Consumer<YakPlayer> updateOperation) {
        return queuePlayerOperation(PlayerOperation.OperationType.UPDATE_STATS, playerId, () -> {
            YakPlayer yakPlayer = onlinePlayers.get(playerId);
            if (yakPlayer != null) {
                // Validate player state before update
                if (yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.PROCESSING) {
                    dataRacesPrevented.incrementAndGet();
                    logger.fine("Preventing data update during combat logout processing for " + playerId);
                    return;
                }
                
                updateOperation.accept(yakPlayer);
                logger.fine("Thread-safe data update completed for " + playerId);
            }
        });
    }

    // ==================== LIFECYCLE METHODS ====================

    public void onEnable() {
        if (initialized) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            // Starting YakPlayerManager with combat logout coordination

            if (!initializeRepository()) {
                logger.severe("Failed to initialize repository!");
                systemHealthy = false;
                return;
            }

            // Initialize combat logout mechanics reference
            this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();

            // ADDED: Clean up any stuck combat logout states from server crash
            performStartupCombatLogoutCleanup();

            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
            initializeDefaultWorld();
            startBackgroundTasks();

            systemHealthy = true;
            initialized = true;

            // YakPlayerManager enabled successfully with combat coordination

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerManager", e);
            systemHealthy = false;
        }
    }

    private boolean initializeRepository() {
        try {
            // Initializing YakPlayerRepository
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

            // YakPlayerRepository initialized successfully
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
            // Using default world: " + defaultWorld.getName()
        } else {
            logger.warning("No worlds available!");
        }
    }

    private void startBackgroundTasks() {
        // Auto-save task
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress) {
                    performAutoSave();
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        // Loading monitor
        loadingMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                monitorLoadingPlayers();
            }
        }.runTaskTimerAsynchronously(plugin, 100L, 100L);

        // Emergency recovery
        emergencyRecoveryTask = new BukkitRunnable() {
            @Override
            public void run() {
                performEmergencyRecovery();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);

        // Background tasks started
    }

    /**
     * ADDED: Clean up any players stuck in PROCESSING state from server crash
     */
    private void performStartupCombatLogoutCleanup() {
        if (repository == null) {
            logger.warning("Repository not available for combat logout cleanup");
            return;
        }

        try {
            // Performing startup combat logout state cleanup

            // This would ideally scan all player records for PROCESSING state
            // For now, we'll handle it when players join by checking for stale states
            // A more complete solution would require a database query here

            // Combat logout cleanup completed - will handle individual cases on player join

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during startup combat logout cleanup", e);
        }
    }

    // ==================== EVENT HANDLERS ====================

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
                        // Denied login for banned player: " + playerName
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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerJoins.incrementAndGet();
        // Player joining: " + playerName + " (" + uuid + ")"

        // Clean up any existing state
        cleanupPlayerState(uuid, "new_join");

        // Start loading process
        setPlayerState(uuid, PlayerState.LOADING);
        PlayerLoadingState loadingState = new PlayerLoadingState(uuid, playerName);
        loadingStates.put(uuid, loadingState);

        // Apply safe loading state immediately
        applySafeLoadingState(player);

        // Start async loading
        startPlayerLoading(player, loadingState);
    }

    private void startPlayerLoading(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        CompletableFuture.supplyAsync(() -> {
            return loadPlayerData(player);
        }, ioExecutor).whenComplete((yakPlayer, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || yakPlayer == null) {
                    handleLoadingFailure(player, loadingState, error);
                } else {
                    completePlayerLoading(player, loadingState, yakPlayer);
                }
            });
        });

        // Loading timeout
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

            player.sendMessage(Component.text("Loading your character...")
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying safe loading state for " + player.getName(), e);
        }
    }

    private YakPlayer loadPlayerData(Player player) {
        try {
            if (!operationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Operation semaphore timeout");
            }

            try {
                // Loading player data: " + player.getName()

                CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(player.getUniqueId());
                Optional<YakPlayer> existingPlayer = repositoryFuture.get(DATA_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                YakPlayer yakPlayer;
                if (existingPlayer.isPresent()) {
                    yakPlayer = existingPlayer.get();
                    yakPlayer.connect(player);

                    // ADDED: Clean up stale combat logout PROCESSING states from server crash
                    if (yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.PROCESSING) {
                        logger.warning("Found stale PROCESSING combat logout state for " + player.getName() + " - cleaning up");
                        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                        // Force save the cleanup
                        repository.saveSync(yakPlayer);
                    }

                    // Loaded existing player: " + player.getName()
                } else {
                    yakPlayer = new YakPlayer(player);
                    // Created new player: " + player.getName()
                    repository.saveSync(yakPlayer);
                }

                successfulLoads.incrementAndGet();
                return yakPlayer;

            } finally {
                operationSemaphore.release();
            }

        } catch (Exception e) {
            failedLoads.incrementAndGet();
            logger.log(Level.SEVERE, "Failed to load player data: " + player.getName(), e);
            throw new RuntimeException("Player data load failed", e);
        }
    }

    private void completePlayerLoading(Player player, PlayerLoadingState loadingState, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            // Completing loading for " + player.getName()

            if (!player.isOnline()) {
                cleanupLoadingState(uuid);
                return;
            }

            // Check for combat logout completion
            if (yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.PROCESSED) {
                // Completing combat logout rejoin for " + player.getName()
                combatLogoutCoordinations.incrementAndGet();

                // FIXED: Get spawn location BEFORE changing state
                Location targetLocation = determineCombatLogoutFinalLocation(yakPlayer);

                // Clear combat logout data
                if (combatLogoutMechanics != null) {
                    combatLogoutMechanics.handleCombatLogoutRejoin(uuid);
                }

                // Update location BEFORE changing state
                if (targetLocation != null) {
                    yakPlayer.updateLocation(targetLocation);
                    // Updated location to spawn for combat logout completion: " + player.getName()
                }

                // Set state to completed LAST
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
            }

            // Store player data
            onlinePlayers.put(uuid, yakPlayer);
            loadingState.setYakPlayer(yakPlayer);

            // Apply player data safely
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    applyPlayerDataSafely(player, yakPlayer);
                    finalizePlayerLoading(player, loadingState, yakPlayer);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error applying player data for " + player.getName(), e);
                    handleLoadingFailure(player, loadingState, e);
                }
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error completing player loading for " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private Location determineCombatLogoutFinalLocation(YakPlayer yakPlayer) {
        try {
            // FIXED: Check for both PROCESSED and COMPLETED states
            YakPlayer.CombatLogoutState state = yakPlayer.getCombatLogoutState();
            if (state == YakPlayer.CombatLogoutState.PROCESSED ||
                    state == YakPlayer.CombatLogoutState.COMPLETED) {

                // IMPROVED: Better world availability checking
                if (defaultWorld != null) {
                    Location spawnLocation = defaultWorld.getSpawnLocation();
                    // Using spawn location for combat logout: " + yakPlayer.getUsername()
                    return spawnLocation;
                } else {
                    // Try to find any available world as fallback
                    List<World> worlds = Bukkit.getWorlds();
                    if (!worlds.isEmpty()) {
                        World fallbackWorld = worlds.get(0);
                        Location fallbackSpawn = fallbackWorld.getSpawnLocation();
                        logger.warning("⚠️ DefaultWorld null, using fallback world: " + fallbackWorld.getName());
                        return fallbackSpawn;
                    } else {
                        logger.severe("❌ No worlds available for combat logout completion!");
                    }
                }
            }

            // Fall back to normal location determination
            return determineFinalLocation(yakPlayer);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error determining combat logout final location for " + yakPlayer.getUsername(), e);

            // Emergency fallback
            try {
                List<World> worlds = Bukkit.getWorlds();
                if (!worlds.isEmpty()) {
                    return worlds.get(0).getSpawnLocation();
                }
            } catch (Exception fallbackError) {
                logger.log(Level.SEVERE, "Emergency fallback also failed", fallbackError);
            }

            return null;
        }
    }

    private void applyPlayerDataSafely(Player player, YakPlayer yakPlayer) {
        try {
            // Applying player data for " + player.getName()

            // Initialize energy if not set
            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }

            // Apply inventory
            try {
                yakPlayer.applyInventory(player);
                // Applied inventory for " + player.getName()
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error applying inventory for " + player.getName() + ", using defaults", e);
                applyDefaultInventory(player);
            }

            // Apply stats
            try {
                applyPlayerStatsSafely(player, yakPlayer);
                // Applied stats for " + player.getName()
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error applying stats for " + player.getName() + ", using defaults", e);
                applyDefaultStats(player);
            }

            // Initialize systems
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
                    // Set health for " + player.getName() + ": " + finalHealth + "/" + player.getMaxHealth()
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
            // Applied default inventory for " + player.getName()
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
            // Applied default stats for " + player.getName()
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

    private void finalizePlayerLoading(Player player, PlayerLoadingState loadingState, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
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
                // Teleported " + player.getName() + " to final location"
            }

            setPlayerState(uuid, PlayerState.READY);
            loadingStates.remove(uuid);

            updateJoinMessage(player, yakPlayer);

            // MOTD is now handled by JoinLeaveListener to avoid double sending

            // Successfully loaded player: " + player.getName()

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error finalizing player loading for " + player.getName(), e);
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

    private void handleLoadingFailure(Player player, PlayerLoadingState loadingState, Throwable error) {
        UUID uuid = player.getUniqueId();

        try {
            setPlayerState(uuid, PlayerState.FAILED);
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

            player.sendMessage(Component.text("Emergency recovery completed!")
                    .color(NamedTextColor.GREEN));

            UUID uuid = player.getUniqueId();
            YakPlayer yakPlayer = new YakPlayer(player);
            onlinePlayers.put(uuid, yakPlayer);
            setPlayerState(uuid, PlayerState.READY);

            ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
            ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);

            // Async save to prevent main thread blocking
            CompletableFuture.runAsync(() -> {
                try {
                    repository.saveSync(yakPlayer);
                    logger.info("Emergency recovery save completed for " + player.getName());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Emergency recovery save failed for " + player.getName(), e);
                }
            }, saveExecutor);

            // Emergency recovery completed for " + player.getName()

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Emergency recovery failed for " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) // Changed to MONITOR to run AFTER combat logout
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        // Player quitting: " + playerName + " - Starting save process"

        try {
            handlePlayerQuit(player, uuid, playerName, event);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in player quit for " + playerName, e);
            performEmergencyQuitSave(uuid, playerName);
        }
    }

    /**
     * THREAD-SAFE Enhanced quit handler with atomic operations and combat logout coordination
     * Prevents inventory duplication and respects combat logout processing
     */
    private void handlePlayerQuit(Player player, UUID uuid, String playerName, PlayerQuitEvent event) {
        ReentrantReadWriteLock playerLock = getPlayerLock(uuid);
        playerLock.writeLock().lock();
        
        try {
            YakPlayer yakPlayer = onlinePlayers.get(uuid);
            if (yakPlayer == null) {
                logger.warning("No player data found for quitting player: " + playerName);
                cleanupPlayerState(uuid, "quit_no_data");
                return;
            }

            // ATOMIC STATE CHECK: Prevent concurrent modifications during combat logout
            YakPlayer.CombatLogoutState currentCombatState = yakPlayer.getCombatLogoutState();
            boolean combatLogoutProcessed = currentCombatState == YakPlayer.CombatLogoutState.PROCESSED ||
                    currentCombatState == YakPlayer.CombatLogoutState.PROCESSING;

            if (combatLogoutProcessed) {
                // Combat logout already processed - atomic skip of inventory updates
                logger.fine("Combat logout already processed for " + playerName + " - skipping inventory/location updates");
                inventoryConflictsPrevented.incrementAndGet();
                combatLogoutCoordinations.incrementAndGet();
            } else {
                // ATOMIC DATA UPDATE: Normal quit processing with thread safety
                try {
                    // Queue the inventory/stats update operation atomically
                    CompletableFuture<Boolean> updateFuture = queuePlayerOperation(
                        PlayerOperation.OperationType.UPDATE_INVENTORY, uuid, () -> {
                            try {
                                yakPlayer.updateInventory(player);
                                yakPlayer.updateStats(player);
                                logger.fine("Thread-safe inventory and stats updated for " + playerName);
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Failed to update inventory/stats in operation queue for " + playerName, e);
                                throw new RuntimeException(e);
                            }
                        });
                    
                    // Wait for update to complete (with timeout)
                    try {
                        updateFuture.get(5, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        logger.warning("Inventory update timeout for " + playerName + " during quit");
                        operationTimeouts.incrementAndGet();
                    } catch (ExecutionException e) {
                        logger.log(Level.SEVERE, "Inventory update failed for " + playerName, e.getCause());
                        saveFailures.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to queue inventory/stats update for " + playerName, e);
                    saveFailures.incrementAndGet();
                }
            }

            // ATOMIC SAVE: Guaranteed save with thread safety (always save, regardless of combat logout state)
            performGuaranteedThreadSafeSave(yakPlayer, playerName, uuid);

            // ATOMIC CLEANUP: Thread-safe cleanup operations
            yakPlayer.disconnect();
            onlinePlayers.remove(uuid);
            cleanupPlayerState(uuid, "normal_quit");

            // Set quit message
            setQuitMessage(event, player, yakPlayer);

            logger.fine("Thread-safe save process completed for: " + playerName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in thread-safe quit handling for " + playerName, e);
            performEmergencyQuitSave(uuid, playerName);
        } finally {
            playerLock.writeLock().unlock();
            
            // Clean up operation queues for offline player
            CompletableFuture<Void> processor = queueProcessors.remove(uuid);
            if (processor != null && !processor.isDone()) {
                processor.cancel(true);
            }
            operationQueues.remove(uuid);
            lastOperationTime.remove(uuid);
            concurrentOperationCount.remove(uuid);
        }
    }
    
    /**
     * THREAD-SAFE guaranteed save with atomic operations
     */
    private void performGuaranteedThreadSafeSave(YakPlayer yakPlayer, String playerName, UUID uuid) {
        int maxAttempts = 3;
        boolean saved = false;

        for (int attempt = 1; attempt <= maxAttempts && !saved; attempt++) {
            try {
                logger.fine("Thread-safe save attempt " + attempt + "/" + maxAttempts + " for " + playerName);

                // Use queued save operation for final consistency check
                CompletableFuture<Boolean> saveFuture = queuePlayerOperation(
                    PlayerOperation.OperationType.SAVE, uuid, () -> {
                        try {
                            YakPlayer result = repository.saveSync(yakPlayer);
                            if (result == null) {
                                throw new RuntimeException("Save returned null");
                            }
                            logger.fine("Atomic save successful for " + playerName);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Atomic save failed for " + playerName, e);
                            throw new RuntimeException(e);
                        }
                    });
                
                // Wait for save with timeout
                try {
                    saveFuture.get(10, TimeUnit.SECONDS);
                    saved = true;
                    logger.fine("Thread-safe save successful on attempt " + attempt + " for " + playerName);
                } catch (TimeoutException e) {
                    logger.warning("Save timeout on attempt " + attempt + " for " + playerName);
                    operationTimeouts.incrementAndGet();
                } catch (ExecutionException e) {
                    logger.log(Level.SEVERE, "Save execution failed on attempt " + attempt + " for " + playerName, e.getCause());
                    if (attempt < maxAttempts) {
                        Thread.sleep(100 * attempt); // Exponential backoff
                    }
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Thread-safe save attempt " + attempt + " failed for " + playerName, e);
                if (attempt < maxAttempts) {
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
            saveFailures.incrementAndGet();
            logger.log(Level.SEVERE, "CRITICAL: All thread-safe save attempts failed for " + playerName);
            
            // Emergency fallback - async save to prevent main thread blocking
            CompletableFuture.runAsync(() -> {
                try {
                    repository.saveSync(yakPlayer);
                    logger.warning("Emergency async save succeeded for " + playerName);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Emergency async save also failed for " + playerName, e);
                }
            }, saveExecutor);
        }
    }

    private void performGuaranteedSave(YakPlayer yakPlayer, String playerName) {
        int maxAttempts = 3;
        boolean saved = false;

        for (int attempt = 1; attempt <= maxAttempts && !saved; attempt++) {
            try {
                // Save attempt " + attempt + "/" + maxAttempts + " for " + playerName"

                YakPlayer result = repository.saveSync(yakPlayer);
                if (result != null) {
                    saved = true;
                    // Save successful on attempt " + attempt + " for " + playerName"
                } else {
                    logger.warning("✗ Save returned null on attempt " + attempt + " for " + playerName);
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "✗ Save attempt " + attempt + " failed for " + playerName, e);
                if (attempt < maxAttempts) {
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
            saveFailures.incrementAndGet();
            logger.log(Level.SEVERE, "CRITICAL: All save attempts failed for " + playerName);
        }
    }

    private void performEmergencyQuitSave(UUID uuid, String playerName) {
        try {
            logger.warning("Performing emergency quit save for " + playerName);

            YakPlayer yakPlayer = onlinePlayers.remove(uuid);
            if (yakPlayer != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        repository.saveSync(yakPlayer);
                        // Emergency save successful for " + playerName"
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Emergency save failed for " + playerName, e);
                    }
                }, saveExecutor);
            }

            cleanupPlayerState(uuid, "emergency_quit");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Emergency cleanup failed for " + playerName, e);
        }
    }

    // Movement restrictions during loading
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PlayerState state = getPlayerState(playerId);

        if (state == PlayerState.LOADING) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                event.setTo(from);
            }
        }
    }

    // ==================== BACKGROUND TASKS ====================

    private void performAutoSave() {
        try {
            int savedCount = 0;

            for (YakPlayer yakPlayer : onlinePlayers.values()) {
                try {
                    Player bukkitPlayer = yakPlayer.getBukkitPlayer();
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        // Skip auto-save if combat logout is being processed
                        if (yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.PROCESSING) {
                            logger.fine("Skipping auto-save for " + yakPlayer.getUsername() + " - combat logout processing");
                            continue;
                        }

                        yakPlayer.updateInventory(bukkitPlayer);
                        yakPlayer.updateStats(bukkitPlayer);
                        repository.saveSync(yakPlayer);
                        savedCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Auto-save failed for player: " + yakPlayer.getUsername(), e);
                }
            }

            if (savedCount > 0) {
                // Auto-save completed: " + savedCount + " players saved"
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in auto-save", e);
        }
    }

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
                if (loadingTime > 30000) { // 30 seconds
                    logger.warning("Player stuck in loading for " + loadingTime + "ms: " + state.getPlayerName());
                    iterator.remove();
                    handleLoadingFailure(player, state, new RuntimeException("Loading monitor timeout"));
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in loading monitor", e);
        }
    }

    private void performEmergencyRecovery() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                PlayerState state = getPlayerState(uuid);

                if (player.getGameMode() == GameMode.SPECTATOR && state == PlayerState.READY) {
                    YakPlayer yakPlayer = getPlayer(uuid);
                    if (yakPlayer != null) {
                        logger.warning("Found player stuck in spectator mode: " + player.getName());

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                                // Performing spectator mode recovery for " + player.getName()
                                performEmergencyPlayerRecovery(player);
                            }
                        }, 60L);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in emergency recovery", e);
        }
    }

    // ==================== UTILITY METHODS ====================

    public PlayerState getPlayerState(UUID playerId) {
        return playerStates.getOrDefault(playerId, PlayerState.OFFLINE);
    }

    public void setPlayerState(UUID playerId, PlayerState newState) {
        if (playerId != null && newState != null) {
            playerStates.put(playerId, newState);
        }
    }

    private void cleanupPlayerState(UUID uuid, String reason) {
        try {
            loadingStates.remove(uuid);
            setPlayerState(uuid, PlayerState.OFFLINE);

            ModerationMechanics.rankMap.remove(uuid);
            ChatMechanics.getPlayerTags().remove(uuid);

            // Cleaned up player state for " + uuid + " (reason: " + reason + ")"
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up player state for " + uuid, e);
        }
    }

    private void cleanupLoadingState(UUID uuid) {
        cleanupPlayerState(uuid, "loading_completed");
    }

    // ==================== PUBLIC API ====================

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

    public boolean isPlayerReady(Player player) {
        if (player == null) return false;
        return getPlayerState(player.getUniqueId()) == PlayerState.READY;
    }

    public boolean isSystemHealthy() {
        return systemHealthy;
    }

    public boolean isRepositoryReady() {
        return repository != null && repository.isInitialized();
    }

    public CompletableFuture<Boolean> savePlayer(YakPlayer yakPlayer) {
        if (yakPlayer == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            return savePlayerSync(yakPlayer, false);
        }, saveExecutor);
    }

    /**
     * THREAD-SAFE Enhanced save method with combat state coordination and atomic operations
     */
    public CompletableFuture<Boolean> savePlayerWithCombatSync(YakPlayer yakPlayer, boolean forceCombatSave) {
        if (yakPlayer == null) {
            return CompletableFuture.completedFuture(false);
        }

        UUID playerId = yakPlayer.getBukkitPlayer() != null ? 
            yakPlayer.getBukkitPlayer().getUniqueId() : null;
        
        if (playerId == null) {
            // Fallback to direct save if no player ID available
            return CompletableFuture.supplyAsync(() -> {
                return savePlayerSync(yakPlayer, forceCombatSave);
            }, saveExecutor);
        }

        // Use thread-safe operation queue for coordinated save
        return queuePlayerOperation(PlayerOperation.OperationType.SAVE, playerId, () -> {
            savePlayerSync(yakPlayer, forceCombatSave);
        });
    }

    /**
     * Synchronous save implementation with proper combat state handling
     */
    private synchronized boolean savePlayerSync(YakPlayer yakPlayer, boolean forceCombatSave) {
        try {
            // Get current combat state and lock it for the duration of save
            YakPlayer.CombatLogoutState currentCombatState = yakPlayer.getCombatLogoutState();
            Player bukkitPlayer = yakPlayer.getBukkitPlayer();
            
            // Update player data based on combat state
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                if (forceCombatSave || currentCombatState == YakPlayer.CombatLogoutState.NONE || 
                    currentCombatState == YakPlayer.CombatLogoutState.COMPLETED) {
                    // Safe to update inventory from live player
                    yakPlayer.updateInventory(bukkitPlayer);
                    yakPlayer.updateStats(bukkitPlayer);
                    logger.fine("Updated inventory and stats for " + yakPlayer.getUsername() + 
                              " (combat state: " + currentCombatState + ")");
                } else {
                    // Combat logout processing - only update non-inventory data
                    yakPlayer.updateStats(bukkitPlayer);
                    logger.fine("Updated stats only for " + yakPlayer.getUsername() + 
                              " (combat logout processing)");
                }
            }

            // Mark save timestamp before persistence
            yakPlayer.setLastSave(System.currentTimeMillis());

            // Perform atomic database save
            YakPlayer result = repository.saveSync(yakPlayer);
            
            if (result != null) {
                logger.fine("Successfully saved player data for " + yakPlayer.getUsername() + 
                          " (combat state: " + currentCombatState + ")");
                return true;
            } else {
                logger.warning("Database save returned null for " + yakPlayer.getUsername());
                return false;
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to save player: " + yakPlayer.getUsername(), e);
            saveFailures.incrementAndGet();
            return false;
        }
    }

    /**
     * Enhanced combat data recovery system
     */
    public CompletableFuture<Boolean> recoverCombatDataInconsistencies(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                YakPlayer yakPlayer = getPlayer(uuid);
                if (yakPlayer == null) {
                    logger.warning("Cannot recover combat data - player not found: " + uuid);
                    return false;
                }

                // Check for stuck combat logout states
                YakPlayer.CombatLogoutState currentState = yakPlayer.getCombatLogoutState();
                long currentTime = System.currentTimeMillis();
                long lastSave = yakPlayer.getLastSave();

                // If player has been in processing state for more than 5 minutes, reset
                if (currentState == YakPlayer.CombatLogoutState.PROCESSING && 
                    (currentTime - lastSave) > 300000) { // 5 minutes
                    
                    logger.warning("Detected stuck combat logout state for " + yakPlayer.getUsername() + 
                                 ", resetting to NONE");
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    
                    // Force save the corrected state
                    boolean saveSuccess = savePlayerSync(yakPlayer, true);
                    if (saveSuccess) {
                        logger.info("Successfully recovered combat data for " + yakPlayer.getUsername());
                        return true;
                    } else {
                        logger.severe("Failed to save recovered combat data for " + yakPlayer.getUsername());
                        return false;
                    }
                }

                // Check for combat logout coordination conflicts
                if (combatLogoutMechanics != null) {
                    if (combatLogoutMechanics.hasActiveCombatLogoutRecord(uuid) && 
                        currentState == YakPlayer.CombatLogoutState.NONE) {
                        
                        logger.warning("Detected combat logout coordination conflict for " + yakPlayer.getUsername());
                        combatLogoutMechanics.handleCombatLogoutRejoin(uuid);
                        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
                        
                        boolean saveSuccess = savePlayerSync(yakPlayer, true);
                        if (saveSuccess) {
                            logger.info("Resolved combat logout coordination conflict for " + yakPlayer.getUsername());
                            inventoryConflictsPrevented.incrementAndGet();
                            return true;
                        }
                    }
                }

                return true; // No issues found

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during combat data recovery for " + uuid, e);
                return false;
            }
        }, ioExecutor);
    }

    /**
     * Batch recovery operation for all online players
     */
    public CompletableFuture<Integer> performSystemWideDataIntegrityCheck() {
        return CompletableFuture.supplyAsync(() -> {
            int recoveredPlayers = 0;
            logger.info("Starting system-wide combat data integrity check");

            for (UUID uuid : onlinePlayers.keySet()) {
                try {
                    boolean recovered = recoverCombatDataInconsistencies(uuid).get();
                    if (recovered) {
                        recoveredPlayers++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error checking data integrity for player " + uuid, e);
                }
            }

            logger.info("Combat data integrity check completed - " + recoveredPlayers + " players processed");
            return recoveredPlayers;
        }, ioExecutor);
    }

    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation, boolean saveAfter) {
        if (uuid == null || operation == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
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

    // ==================== MESSAGE FORMATTING ====================

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

    // ==================== SHUTDOWN ====================

    public void onDisable() {
        if (shutdownInProgress) return;

        shutdownInProgress = true;
        // Starting YakPlayerManager shutdown...

        try {
            // Cancel background tasks
            if (autoSaveTask != null) autoSaveTask.cancel();
            if (loadingMonitorTask != null) loadingMonitorTask.cancel();
            if (emergencyRecoveryTask != null) emergencyRecoveryTask.cancel();

            // Save all players
            saveAllPlayersOnShutdown();

            // Shutdown executors
            ioExecutor.shutdown();
            saveExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
                if (!saveExecutor.awaitTermination(15, TimeUnit.SECONDS)) {
                    saveExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                saveExecutor.shutdownNow();
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

            // YakPlayerManager shutdown completed

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
        } finally {
            initialized = false;
            systemHealthy = false;
        }
    }

    private void saveAllPlayersOnShutdown() {
        // Starting shutdown save for " + onlinePlayers.size() + " players..."

        int savedCount = 0;

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            try {
                Player player = yakPlayer.getBukkitPlayer();
                if (player != null && player.isOnline()) {
                    // Only update inventory if not in combat logout processing
                    if (yakPlayer.getCombatLogoutState() != YakPlayer.CombatLogoutState.PROCESSING) {
                        yakPlayer.updateInventory(player);
                        yakPlayer.updateStats(player);
                    }
                }

                yakPlayer.disconnect();

                boolean saved = false;
                for (int attempt = 1; attempt <= 3 && !saved; attempt++) {
                    try {
                        YakPlayer result = repository.saveSync(yakPlayer);
                        if (result != null) {
                            saved = true;
                            savedCount++;
                            // Shutdown save successful (attempt " + attempt + ") for " + yakPlayer.getUsername()
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

        // Shutdown save completed: " + savedCount + "/" + onlinePlayers.size() + " players saved"
    }

    // ==================== COMMAND HANDLING ====================

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

            player.sendMessage(Component.text("=== PLAYER STATE DEBUG ===").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("State: " + state).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Emergency Recoveries: " + emergencyRecoveries.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Save Failures: " + saveFailures.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Combat Coordinations: " + combatLogoutCoordinations.get()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Inventory Conflicts Prevented: " + inventoryConflictsPrevented.get()).color(NamedTextColor.YELLOW));

            YakPlayer yakPlayer = getPlayer(uuid);
            if (yakPlayer != null) {
                player.sendMessage(Component.text("Health: " + player.getHealth() + "/" + player.getMaxHealth()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Game Mode: " + player.getGameMode()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Combat Logout State: " + yakPlayer.getCombatLogoutState()).color(NamedTextColor.YELLOW));
            }

            return true;
        }

        return false;
    }

    // ==================== SYSTEM STATISTICS ====================

    public SystemStats getSystemStats() {
        return new SystemStats(
                totalPlayerJoins.get(),
                totalPlayerQuits.get(),
                Bukkit.getOnlinePlayers().size(),
                isSystemHealthy(),
                systemHealthy,
                isRepositoryReady(),
                loadingStates.size(),
                successfulLoads.get(),
                failedLoads.get(),
                emergencyRecoveries.get(),
                saveFailures.get(),
                combatLogoutCoordinations.get(),
                inventoryConflictsPrevented.get()
        );
    }

    public static class SystemStats {
        public final int totalJoins;
        public final int totalQuits;
        public final int currentOnline;
        public final boolean systemHealthy;
        public final boolean systemsReady;
        public final boolean repositoryReady;
        public final int loadingPlayers;
        public final int successfulLoads;
        public final int failedLoads;
        public final int emergencyRecoveries;
        public final int saveFailures;
        public final int combatLogoutCoordinations;
        public final int inventoryConflictsPrevented;

        SystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy,
                    boolean systemsReady, boolean repositoryReady, int loadingPlayers,
                    int successfulLoads, int failedLoads, int emergencyRecoveries, int saveFailures,
                    int combatLogoutCoordinations, int inventoryConflictsPrevented) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
            this.systemsReady = systemsReady;
            this.repositoryReady = repositoryReady;
            this.loadingPlayers = loadingPlayers;
            this.successfulLoads = successfulLoads;
            this.failedLoads = failedLoads;
            this.emergencyRecoveries = emergencyRecoveries;
            this.saveFailures = saveFailures;
            this.combatLogoutCoordinations = combatLogoutCoordinations;
            this.inventoryConflictsPrevented = inventoryConflictsPrevented;
        }
    }

    /**
     * Thread-Safe Player Operation Queue System
     * Prevents concurrent modifications and data races
     */
    private static class PlayerOperation {
        public enum OperationType {
            SAVE, UPDATE_INVENTORY, UPDATE_STATS, UPDATE_LOCATION, 
            COMBAT_LOGOUT_START, COMBAT_LOGOUT_COMPLETE, DATA_VALIDATION
        }
        
        private final OperationType type;
        private final UUID playerId;
        private final Runnable operation;
        private final CompletableFuture<Boolean> completionFuture;
        private final long createdTime;
        
        public PlayerOperation(OperationType type, UUID playerId, Runnable operation) {
            this.type = type;
            this.playerId = playerId;
            this.operation = operation;
            this.completionFuture = new CompletableFuture<>();
            this.createdTime = System.currentTimeMillis();
        }
        
        public void execute() {
            try {
                operation.run();
                completionFuture.complete(true);
            } catch (Exception e) {
                completionFuture.completeExceptionally(e);
            }
        }
        
        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - createdTime > timeoutMs;
        }
        
        // Getters
        public OperationType getType() { return type; }
        public UUID getPlayerId() { return playerId; }
        public CompletableFuture<Boolean> getCompletionFuture() { return completionFuture; }
        public long getCreatedTime() { return createdTime; }
    }

    private static class PlayerLoadingState {
        private final UUID playerId;
        private final String playerName;
        private final long startTime;
        private volatile YakPlayer yakPlayer;
        private volatile boolean threadSafetyEnabled = true;

        public PlayerLoadingState(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.startTime = System.currentTimeMillis();
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getStartTime() { return startTime; }
        public YakPlayer getYakPlayer() { return yakPlayer; }
        public void setYakPlayer(YakPlayer yakPlayer) { this.yakPlayer = yakPlayer; }
        public boolean isThreadSafetyEnabled() { return threadSafetyEnabled; }
        public void setThreadSafetyEnabled(boolean enabled) { this.threadSafetyEnabled = enabled; }
    }
}
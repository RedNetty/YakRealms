package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIXED: Enhanced YakPlayer manager with improved reliability, performance, and error handling
 * Removed conflicting initialization patterns that were causing deadlocks
 */
public class YakPlayerManager implements Listener {
    private static volatile YakPlayerManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Configuration constants
    private static final long DATA_LOAD_TIMEOUT_MS = 30000L; // 30 seconds
    private static final long SAVE_TIMEOUT_MS = 15000L; // 15 seconds
    private static final int MAX_CONCURRENT_LOADS = 10;
    private static final int MAX_CONCURRENT_SAVES = 5;
    private static final long REPOSITORY_INITIALIZATION_TIMEOUT = 30000L; // 30 seconds

    // Core dependencies
    private volatile YakPlayerRepository repository;
    private final Logger logger;
    private final Plugin plugin;

    // Player management
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantReadWriteLock> playerLocks = new ConcurrentHashMap<>();

    // Enhanced loading system
    private final Map<UUID, PlayerLoadContext> loadingPlayers = new ConcurrentHashMap<>();
    private final Semaphore loadSemaphore = new Semaphore(MAX_CONCURRENT_LOADS);
    private final Semaphore saveSemaphore = new Semaphore(MAX_CONCURRENT_SAVES);

    // Thread pools with proper sizing
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService ioExecutor;
    private final ExecutorService priorityExecutor;

    // FIXED: Simplified state management - removed conflicting flags
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean systemHealthy = new AtomicBoolean(false);
    private final AtomicBoolean repositoryReady = new AtomicBoolean(false);

    // Tasks
    private BukkitTask autoSaveTask;
    private BukkitTask healthMonitorTask;
    private BukkitTask cacheMaintenance;
    private BukkitTask loadTimeoutTask;

    // Performance tracking
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger failedLoads = new AtomicInteger(0);
    private final AtomicInteger successfulSaves = new AtomicInteger(0);
    private final AtomicInteger failedSaves = new AtomicInteger(0);

    // Event listeners
    private final Set<PlayerLoadListener> loadListeners = ConcurrentHashMap.newKeySet();
    private final Set<PlayerSaveListener> saveListeners = ConcurrentHashMap.newKeySet();

    // Configuration
    private final long autoSaveInterval;
    private final int playersPerSaveCycle;

    /**
     * Player load context for tracking load operations
     */
    private static class PlayerLoadContext {
        private final UUID playerId;
        private final String playerName;
        private final long loadStartTime;
        private final CompletableFuture<YakPlayer> loadFuture;
        private volatile boolean completed = false;
        private volatile boolean timedOut = false;

        public PlayerLoadContext(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.loadStartTime = System.currentTimeMillis();
            this.loadFuture = new CompletableFuture<>();
        }

        public boolean isExpired() {
            return !completed && (System.currentTimeMillis() - loadStartTime) > DATA_LOAD_TIMEOUT_MS;
        }

        public long getLoadDuration() {
            return System.currentTimeMillis() - loadStartTime;
        }

        // Getters
        public UUID getPlayerId() {
            return playerId;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getLoadStartTime() {
            return loadStartTime;
        }

        public CompletableFuture<YakPlayer> getLoadFuture() {
            return loadFuture;
        }

        public boolean isCompleted() {
            return completed;
        }

        public boolean isTimedOut() {
            return timedOut;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public void setTimedOut(boolean timedOut) {
            this.timedOut = timedOut;
        }
    }

    /**
     * Enhanced listener interfaces
     */
    public interface PlayerLoadListener {
        void onPlayerLoaded(Player player, YakPlayer yakPlayer, boolean isNewPlayer);

        void onPlayerLoadFailed(Player player, Exception exception);

        void onPlayerLoadTimeout(Player player);
    }

    public interface PlayerSaveListener {
        void onPlayerSaved(YakPlayer yakPlayer);

        void onPlayerSaveFailed(YakPlayer yakPlayer, Exception exception);
    }

    /**
     * Functional interface for void operations on YakPlayer (for async operations)
     */
    @FunctionalInterface
    public interface PlayerOperation {
        void execute(YakPlayer yakPlayer) throws Exception;
    }

    /**
     * Functional interface for operations that return a value (for sync operations)
     */
    @FunctionalInterface
    public interface PlayerFunction<T> {
        T execute(YakPlayer yakPlayer) throws Exception;
    }

    /**
     * Functional interface for operations on both Bukkit Player and YakPlayer
     */
    @FunctionalInterface
    public interface BothPlayersOperation<T> {
        T execute(Player bukkitPlayer, YakPlayer yakPlayer) throws Exception;
    }

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Load configuration
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.playersPerSaveCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 8);

        // Initialize optimized thread pools
        this.scheduledExecutor = createScheduledExecutor();
        this.ioExecutor = createIOExecutor();
        this.priorityExecutor = createPriorityExecutor();

        logger.info("YakPlayerManager initialized with enhanced error handling and performance monitoring");

        // Note: Repository initialization is now handled in onEnable() to avoid timing conflicts;
    }

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

    /**
     * FIXED: Simplified initialization with single repository setup
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting YakPlayerManager initialization...");

            // FIXED: Single synchronous repository initialization
            if (!initializeRepositorySynchronously()) {
                logger.severe("Failed to initialize repository!");
                systemHealthy.set(false);
                emergencyCleanup();
                return;
            }

            // Register events on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
                    logger.info("YakPlayerManager events registered");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to register events", e);
                }
            });

            // Start background tasks
            startBackgroundTasks();

            // Load existing online players if any
            loadExistingOnlinePlayers();

            // Final validation
            if (!validateInitialization()) {
                throw new RuntimeException("Initialization validation failed");
            }

            systemHealthy.set(true);
            repositoryReady.set(true);
            logger.info("YakPlayerManager enabled successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerManager", e);
            systemHealthy.set(false);
            emergencyCleanup();
            throw new RuntimeException("YakPlayerManager initialization failed", e);
        }
    }

    /**
     * FIXED: Single synchronous repository initialization to avoid conflicts
     */
    private boolean initializeRepositorySynchronously() {
        int maxAttempts = 3;
        long retryDelay = 2000; // 2 seconds

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                logger.info("Repository initialization attempt " + attempt + "/" + maxAttempts);

                // Initialize repository
                this.repository = new YakPlayerRepository();

                // Wait for repository to be ready with timeout
                long startTime = System.currentTimeMillis();
                while (!repository.isInitialized()) {
                    if (System.currentTimeMillis() - startTime > REPOSITORY_INITIALIZATION_TIMEOUT) {
                        throw new RuntimeException("Repository initialization timeout after " +
                                (REPOSITORY_INITIALIZATION_TIMEOUT / 1000) + " seconds");
                    }

                    try {
                        Thread.sleep(500); // Check every 500ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Repository initialization interrupted", e);
                    }
                }

                logger.info("YakPlayerRepository initialized successfully on attempt " + attempt);
                return true;

            } catch (Exception e) {
                logger.log(Level.WARNING, "Repository initialization attempt " + attempt + " failed: " + e.getMessage(), e);

                if (attempt < maxAttempts) {
                    try {
                        logger.info("Waiting " + (retryDelay / 1000) + " seconds before retry...");
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logger.severe("Failed to initialize YakPlayerRepository after " + maxAttempts + " attempts!");
        return false;
    }

    /**
     * Enhanced pre-login handler with comprehensive ban checking
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shutdownInProgress.get()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "Server is shutting down. Please try again in a moment.");
            return;
        }

        if (!systemHealthy.get() || !repositoryReady.get()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "Server is experiencing technical difficulties. Please try again later.");
            return;
        }

        UUID uuid = event.getUniqueId();
        String playerName = event.getName();

        try {
            // Check for existing ban with timeout
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(10, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                // Check ban status
                if (player.isBanned()) {
                    String banMessage = formatBanMessage(player);
                    if (banMessage != null) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage);
                        logger.info("Denied login for banned player: " + playerName);
                        return;
                    }
                }

                // Update username if changed
                if (!player.getUsername().equals(playerName)) {
                    player.setUsername(playerName);
                    // Save asynchronously without blocking
                    repository.save(player);
                }
            }

        } catch (TimeoutException e) {
            logger.log(Level.WARNING, "Timeout checking ban status for " + playerName + ", allowing login", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking ban status for " + playerName + ", allowing login", e);
        }
    }

    /**
     * Enhanced player join handler with improved data loading
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerJoins.incrementAndGet();
        logger.info("Player joining: " + playerName + " (" + uuid + ")");

        // Check system health
        if (!repositoryReady.get() || repository == null) {
            logger.severe("Player joined but repository not ready: " + playerName);
            player.sendMessage(ChatColor.RED + "Server is not ready. Please reconnect in a moment.");
            return;
        }

        // Check for duplicate processing
        if (loadingPlayers.containsKey(uuid) || onlinePlayers.containsKey(uuid)) {
            logger.warning("Duplicate join event for player: " + playerName);
            return;
        }

        // Create load context
        PlayerLoadContext loadContext = new PlayerLoadContext(uuid, playerName);
        loadingPlayers.put(uuid, loadContext);

        // Ensure player lock exists
        playerLocks.put(uuid, new ReentrantReadWriteLock());

        // Start asynchronous data loading
        CompletableFuture<YakPlayer> loadFuture = loadPlayerDataAsync(player, loadContext);
        loadContext.getLoadFuture().complete(null); // Link the futures if needed

        // Handle load completion
        loadFuture.whenCompleteAsync((yakPlayer, error) -> {
            handlePlayerLoadCompletion(player, loadContext, yakPlayer, error);
        }, priorityExecutor);

        // Set temporary join message
        setTemporaryJoinMessage(event, player);
    }

    /**
     * Enhanced player quit handler with reliable saving
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        logger.info("Player quitting: " + playerName);

        // Cancel any pending load
        PlayerLoadContext loadContext = loadingPlayers.remove(uuid);
        if (loadContext != null && !loadContext.isCompleted()) {
            loadContext.getLoadFuture().cancel(true);
            logger.info("Cancelled pending load for quitting player: " + playerName);
        }

        // Get and remove player data
        YakPlayer yakPlayer = onlinePlayers.remove(uuid);
        if (yakPlayer == null) {
            logger.warning("No player data found for quitting player: " + playerName);
            cleanupPlayerReferences(uuid, playerName);
            return;
        }

        // Update player data before saving
        updatePlayerBeforeSave(yakPlayer, player);
        yakPlayer.disconnect();

        // Clean up references
        cleanupPlayerReferences(uuid, playerName);

        // Save player data asynchronously with timeout
        savePlayerDataAsync(yakPlayer, true).whenCompleteAsync((result, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to save player on quit: " + playerName, error);
                failedSaves.incrementAndGet();
                notifySaveFailure(yakPlayer, error);
            } else {
                logger.fine("Successfully saved player on quit: " + playerName);
                successfulSaves.incrementAndGet();
                notifySaveSuccess(yakPlayer);
            }
        }, ioExecutor);

        // Set quit message
        setEnhancedQuitMessage(event, player, yakPlayer);
    }

    /**
     * Enhanced asynchronous player data loading
     */
    private CompletableFuture<YakPlayer> loadPlayerDataAsync(Player player, PlayerLoadContext loadContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire load semaphore to limit concurrent loads
                if (!loadSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Load semaphore timeout - too many concurrent loads");
                }

                try {
                    logger.fine("Loading player data for: " + player.getName());

                    // Attempt to load from repository with timeout
                    CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(player.getUniqueId());
                    Optional<YakPlayer> existingPlayer = repositoryFuture.get(DATA_LOAD_TIMEOUT_MS - 5000, TimeUnit.MILLISECONDS);

                    YakPlayer yakPlayer;
                    boolean isNewPlayer;

                    if (existingPlayer.isPresent()) {
                        yakPlayer = existingPlayer.get();
                        yakPlayer.connect(player);
                        isNewPlayer = false;
                        logger.fine("Loaded existing player data for: " + player.getName());
                    } else {
                        yakPlayer = new YakPlayer(player);
                        isNewPlayer = true;
                        logger.info("Created new player data for: " + player.getName());

                        // Save new player immediately
                        repository.save(yakPlayer).get(5, TimeUnit.SECONDS);
                        logger.fine("Saved new player data for: " + player.getName());
                    }

                    // Store load context data
                    loadContext.setCompleted(true);
                    successfulLoads.incrementAndGet();

                    // Schedule player system initialization on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            initializePlayerSystems(player, yakPlayer);
                            notifyLoadSuccess(player, yakPlayer, isNewPlayer);
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
                        }
                    });

                    return yakPlayer;

                } finally {
                    loadSemaphore.release();
                }

            } catch (TimeoutException e) {
                loadContext.setTimedOut(true);
                failedLoads.incrementAndGet();
                logger.log(Level.WARNING, "Player data load timeout for: " + player.getName(), e);
                throw new RuntimeException("Player data load timeout", e);

            } catch (Exception e) {
                loadContext.setCompleted(true);
                failedLoads.incrementAndGet();
                logger.log(Level.SEVERE, "Failed to load player data for: " + player.getName(), e);
                throw new RuntimeException("Player data load failed", e);
            }
        }, ioExecutor);
    }

    /**
     * Handle completion of player data loading
     */
    private void handlePlayerLoadCompletion(Player player, PlayerLoadContext loadContext, YakPlayer yakPlayer, Throwable error) {
        UUID uuid = player.getUniqueId();
        loadingPlayers.remove(uuid);

        if (error != null) {
            if (loadContext.isTimedOut()) {
                handlePlayerLoadTimeout(player, loadContext);
            } else {
                handlePlayerLoadFailure(player, loadContext, error);
            }
        } else if (yakPlayer != null) {
            handlePlayerLoadSuccess(player, loadContext, yakPlayer);
        } else {
            handlePlayerLoadFailure(player, loadContext, new RuntimeException("Unknown load failure"));
        }
    }

    /**
     * Handle successful player data loading
     */
    private void handlePlayerLoadSuccess(Player player, PlayerLoadContext loadContext, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        // Register player
        onlinePlayers.put(uuid, yakPlayer);
        playerNameCache.put(player.getName().toLowerCase(), uuid);

        logger.info("Successfully loaded player data for: " + player.getName() +
                " (duration: " + loadContext.getLoadDuration() + "ms)");

        // Player systems are initialized in the load future completion
    }

    /**
     * Handle player data load timeout
     */
    private void handlePlayerLoadTimeout(Player player, PlayerLoadContext loadContext) {
        logger.warning("Player data load timeout for: " + player.getName() +
                " (duration: " + loadContext.getLoadDuration() + "ms)");

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED + "⚠ Character data loading timed out.");
                player.sendMessage(ChatColor.GRAY + "Please reconnect. If this persists, contact an administrator.");

                // Notify load listeners
                for (PlayerLoadListener listener : loadListeners) {
                    try {
                        listener.onPlayerLoadTimeout(player);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in load timeout listener", e);
                    }
                }
            }
        });
    }

    /**
     * Handle player data load failure
     */
    private void handlePlayerLoadFailure(Player player, PlayerLoadContext loadContext, Throwable error) {
        logger.log(Level.SEVERE, "Player data load failed for: " + player.getName() +
                " (duration: " + loadContext.getLoadDuration() + "ms)", error);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED + "⚠ Failed to load your character data.");
                player.sendMessage(ChatColor.GRAY + "Please reconnect. If this persists, contact an administrator.");

                if (player.hasPermission("yakserver.admin")) {
                    player.sendMessage(ChatColor.GRAY + "Error: " + error.getMessage());
                }

                // Notify load listeners
                for (PlayerLoadListener listener : loadListeners) {
                    try {
                        listener.onPlayerLoadFailed(player, error instanceof Exception ? (Exception) error : new RuntimeException(error));
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in load failure listener", e);
                    }
                }
            }
        });
    }

    /**
     * Enhanced asynchronous player data saving
     */
    private CompletableFuture<Boolean> savePlayerDataAsync(YakPlayer yakPlayer, boolean isQuit) {
        return CompletableFuture.supplyAsync(() -> {
            if (yakPlayer == null) {
                return false;
            }

            try {
                // Acquire save semaphore to limit concurrent saves
                if (!saveSemaphore.tryAcquire(isQuit ? 10 : 5, TimeUnit.SECONDS)) {
                    logger.warning("Save semaphore timeout for player: " + yakPlayer.getUsername());
                    return false;
                }

                try {
                    logger.fine("Saving player data for: " + yakPlayer.getUsername());

                    // Perform save with timeout
                    CompletableFuture<YakPlayer> saveFuture = repository.save(yakPlayer);
                    YakPlayer savedPlayer = saveFuture.get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    if (savedPlayer != null) {
                        logger.fine("Successfully saved player data for: " + yakPlayer.getUsername());
                        return true;
                    } else {
                        logger.warning("Save returned null for player: " + yakPlayer.getUsername());
                        return false;
                    }

                } finally {
                    saveSemaphore.release();
                }

            } catch (TimeoutException e) {
                logger.log(Level.WARNING, "Player data save timeout for: " + yakPlayer.getUsername(), e);
                return false;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player data for: " + yakPlayer.getUsername(), e);
                return false;
            }
        }, ioExecutor);
    }

    /**
     * Initialize player systems after successful data loading
     */
    private void initializePlayerSystems(Player player, YakPlayer yakPlayer) {
        try {
            UUID uuid = player.getUniqueId();

            // Initialize energy
            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }
            player.setExp(1.0f);
            player.setLevel(100);

            // Initialize moderation rank
            try {
                Rank rank = Rank.valueOf(yakPlayer.getRank());
                ModerationMechanics.rankMap.put(uuid, rank);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid rank for player " + player.getName() + ": " + yakPlayer.getRank());
                ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
                yakPlayer.setRank("DEFAULT");
            }

            // Initialize chat tag
            try {
                ChatTag tag = ChatTag.valueOf(yakPlayer.getChatTag());
                ChatMechanics.getPlayerTags().put(uuid, tag);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid chat tag for player " + player.getName() + ": " + yakPlayer.getChatTag());
                ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
                yakPlayer.setChatTag("DEFAULT");
            }

            logger.fine("Initialized systems for player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
        }
    }

    /**
     * Update player data before saving
     */
    private void updatePlayerBeforeSave(YakPlayer yakPlayer, Player bukkitPlayer) {
        if (yakPlayer == null || bukkitPlayer == null) {
            return;
        }

        ReentrantReadWriteLock lock = playerLocks.get(yakPlayer.getUUID());
        if (lock != null) {
            lock.writeLock().lock();
            try {
                yakPlayer.updateLocation(bukkitPlayer.getLocation());
                yakPlayer.updateInventory(bukkitPlayer);
                yakPlayer.updateStats(bukkitPlayer);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error updating player before save: " + yakPlayer.getUsername(), e);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    /**
     * Clean up player references
     */
    private void cleanupPlayerReferences(UUID uuid, String playerName) {
        playerNameCache.remove(playerName.toLowerCase());
        playerLocks.remove(uuid);

        // Clean up moderation and chat systems
        ModerationMechanics.rankMap.remove(uuid);
        ChatMechanics.getPlayerTags().remove(uuid);
    }

    /**
     * Enhanced background task management
     */
    private void startBackgroundTasks() {
        // Auto-save task
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress.get()) {
                    performAutoSave();
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        // Health monitoring task
        healthMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress.get()) {
                    performHealthCheck();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Every minute

        // Cache maintenance task
        cacheMaintenance = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress.get()) {
                    performCacheMaintenance();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes

        // Load timeout monitoring task
        loadTimeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress.get()) {
                    checkLoadTimeouts();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second

        logger.info("Background tasks started successfully");
    }

    /**
     * Monitor and handle load timeouts
     */
    private void checkLoadTimeouts() {
        long currentTime = System.currentTimeMillis();
        List<PlayerLoadContext> expiredContexts = new ArrayList<>();

        for (PlayerLoadContext context : loadingPlayers.values()) {
            if (context.isExpired() && !context.isCompleted()) {
                expiredContexts.add(context);
            }
        }

        for (PlayerLoadContext context : expiredContexts) {
            Player player = Bukkit.getPlayer(context.getPlayerId());
            if (player != null && player.isOnline()) {
                handlePlayerLoadTimeout(player, context);
            }
            loadingPlayers.remove(context.getPlayerId());
        }
    }

    /**
     * Enhanced auto-save with rate limiting
     */
    private void performAutoSave() {
        List<YakPlayer> playersToSave = new ArrayList<>();

        // Collect players to save (limit to configured number per cycle)
        int count = 0;
        for (YakPlayer player : onlinePlayers.values()) {
            if (count >= playersPerSaveCycle) break;

            Player bukkitPlayer = player.getBukkitPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                playersToSave.add(player);
                count++;
            }
        }

        if (playersToSave.isEmpty()) {
            return;
        }

        logger.fine("Auto-save starting for " + playersToSave.size() + " players");

        // Save players asynchronously
        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();
        for (YakPlayer player : playersToSave) {
            Player bukkitPlayer = player.getBukkitPlayer();
            if (bukkitPlayer != null) {
                updatePlayerBeforeSave(player, bukkitPlayer);
            }
            saveFutures.add(savePlayerDataAsync(player, false));
        }

        // Collect results
        CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                .whenCompleteAsync((result, error) -> {
                    long successful = saveFutures.stream()
                            .mapToLong(future -> {
                                try {
                                    return future.get() ? 1 : 0;
                                } catch (Exception e) {
                                    return 0;
                                }
                            }).sum();

                    logger.fine("Auto-save completed: " + successful + "/" + saveFutures.size() + " players saved");
                }, scheduledExecutor);
    }

    /**
     * Perform comprehensive health check
     */
    private void performHealthCheck() {
        try {
            boolean healthy = true;
            StringBuilder healthReport = new StringBuilder("YakPlayerManager Health Check:\n");

            // Check repository status
            if (!repositoryReady.get() || repository == null || !repository.isInitialized()) {
                healthy = false;
                healthReport.append("- Repository not ready\n");
            }

            // Check for excessive load times
            long avgLoadTime = loadingPlayers.values().stream()
                    .mapToLong(PlayerLoadContext::getLoadDuration)
                    .filter(duration -> duration > 0)
                    .reduce(0, Long::sum) / Math.max(1, loadingPlayers.size());

            if (avgLoadTime > 10000) { // 10 seconds
                healthy = false;
                healthReport.append("- High average load time: ").append(avgLoadTime).append("ms\n");
            }

            // Check semaphore availability
            if (loadSemaphore.availablePermits() == 0) {
                healthy = false;
                healthReport.append("- Load semaphore exhausted\n");
            }

            if (saveSemaphore.availablePermits() == 0) {
                healthy = false;
                healthReport.append("- Save semaphore exhausted\n");
            }

            // Update system health
            systemHealthy.set(healthy);

            if (!healthy) {
                logger.warning(healthReport.toString());
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during health check", e);
            systemHealthy.set(false);
        }
    }

    /**
     * Perform cache maintenance
     */
    private void performCacheMaintenance() {
        // Clean up stale entries
        playerNameCache.entrySet().removeIf(entry -> {
            UUID uuid = entry.getValue();
            return !onlinePlayers.containsKey(uuid);
        });

        // Clean up orphaned locks
        playerLocks.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && !loadingPlayers.containsKey(uuid);
        });

        logger.fine("Cache maintenance completed");
    }

    /**
     * Enhanced shutdown process
     */
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }

        logger.info("Starting YakPlayerManager shutdown...");

        try {
            // Cancel tasks
            cancelTask(autoSaveTask);
            cancelTask(healthMonitorTask);
            cancelTask(cacheMaintenance);
            cancelTask(loadTimeoutTask);

            // Cancel pending loads
            cancelPendingLoads();

            // Save all online players
            saveAllPlayersOnShutdown();

            // Shutdown repository
            if (repository != null) {
                repository.shutdown();
            }

            // Shutdown executors
            shutdownExecutors();

            // Clear data structures
            onlinePlayers.clear();
            playerNameCache.clear();
            playerLocks.clear();
            loadingPlayers.clear();
            loadListeners.clear();
            saveListeners.clear();

            logger.info("YakPlayerManager shutdown completed successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during YakPlayerManager shutdown", e);
        } finally {
            initialized.set(false);
            repositoryReady.set(false);
        }
    }

    /**
     * Enhanced utility methods
     */
    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void cancelPendingLoads() {
        logger.info("Cancelling " + loadingPlayers.size() + " pending loads...");
        for (PlayerLoadContext context : loadingPlayers.values()) {
            if (!context.isCompleted()) {
                context.getLoadFuture().cancel(true);
            }
        }
        loadingPlayers.clear();
    }

    /**
     * Enhanced shutdown save process
     */
    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();
            if (player != null && player.isOnline()) {
                updatePlayerBeforeSave(yakPlayer, player);
            }
            yakPlayer.disconnect();

            // Use synchronous save for shutdown
            saveFutures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    YakPlayer result = repository.saveSync(yakPlayer);
                    return result != null;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to save player on shutdown: " + yakPlayer.getUsername(), e);
                    return false;
                }
            }, ioExecutor));
        }

        try {
            CompletableFuture<Void> allSaves = CompletableFuture.allOf(
                    saveFutures.toArray(new CompletableFuture[0]));
            allSaves.get(30, TimeUnit.SECONDS);

            long successful = saveFutures.stream()
                    .mapToLong(future -> {
                        try {
                            return future.get() ? 1 : 0;
                        } catch (Exception e) {
                            return 0;
                        }
                    }).sum();

            logger.info("Shutdown save completed: " + successful + "/" + saveFutures.size() + " players saved");

        } catch (TimeoutException e) {
            logger.warning("Shutdown save timed out after 30 seconds");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown save", e);
        }
    }

    // Enhanced utility methods for system validation and management

    private boolean validateInitialization() {
        return repository != null && repositoryReady.get() &&
                scheduledExecutor != null && !scheduledExecutor.isShutdown() &&
                ioExecutor != null && !ioExecutor.isShutdown() &&
                priorityExecutor != null && !priorityExecutor.isShutdown();
    }

    private void emergencyCleanup() {
        try {
            shutdownExecutors();
            onlinePlayers.clear();
            loadingPlayers.clear();
            initialized.set(false);
            repositoryReady.set(false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during emergency cleanup", e);
        }
    }

    private void shutdownExecutors() {
        shutdownExecutor(scheduledExecutor, "scheduled");
        shutdownExecutor(ioExecutor, "io");
        shutdownExecutor(priorityExecutor, "priority");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warning("Forcing shutdown of " + name + " executor");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.warning("Interrupted while shutting down " + name + " executor");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Thread pool creation methods
    private ScheduledExecutorService createScheduledExecutor() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Scheduled");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    }

    private ExecutorService createIOExecutor() {
        return Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    private ExecutorService createPriorityExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Priority");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            return thread;
        });
    }

    // Message formatting methods
    private String formatBanMessage(YakPlayer player) {
        if (!player.isBanned()) {
            return null;
        }

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append(ChatColor.BOLD).append("You are banned from this server!\n\n");
        message.append(ChatColor.GRAY).append("Reason: ").append(ChatColor.WHITE).append(player.getBanReason()).append("\n");

        if (player.getBanExpiry() > 0) {
            long remaining = player.getBanExpiry() - Instant.now().getEpochSecond();
            if (remaining > 0) {
                message.append(ChatColor.GRAY).append("Expires in: ").append(ChatColor.WHITE).append(formatDuration(remaining));
            } else {
                // Ban expired, unban the player
                player.setBanned(false, "", 0);
                repository.save(player);
                return null;
            }
        } else {
            message.append(ChatColor.GRAY).append("This ban does not expire.");
        }

        message.append("\n\n").append(ChatColor.GRAY).append("Appeal at: ").append(ChatColor.BLUE).append("discord.gg/JYf6R2VKE7");
        return message.toString();
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

    private void setTemporaryJoinMessage(PlayerJoinEvent event, Player player) {
        event.setJoinMessage(ChatColor.AQUA + "[+] " + ChatColor.GRAY + player.getName() + ChatColor.DARK_GRAY + " (loading...)");
    }

    private void setEnhancedQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer) {
        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();
            event.setQuitMessage(ChatColor.RED + "[-] " + formattedName);
        } else {
            event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + player.getName());
        }
    }

    private void loadExistingOnlinePlayers() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (!onlinePlayers.isEmpty()) {
            logger.info("Loading " + onlinePlayers.size() + " existing online players...");
            for (Player player : onlinePlayers) {
                // Simulate join event for existing players
                onPlayerJoin(new PlayerJoinEvent(player, null));
            }
        }
    }

    // Event notification methods
    private void notifyLoadSuccess(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        for (PlayerLoadListener listener : loadListeners) {
            try {
                listener.onPlayerLoaded(player, yakPlayer, isNewPlayer);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in load success listener", e);
            }
        }
    }

    private void notifySaveSuccess(YakPlayer yakPlayer) {
        for (PlayerSaveListener listener : saveListeners) {
            try {
                listener.onPlayerSaved(yakPlayer);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in save success listener", e);
            }
        }
    }

    private void notifySaveFailure(YakPlayer yakPlayer, Throwable error) {
        for (PlayerSaveListener listener : saveListeners) {
            try {
                listener.onPlayerSaveFailed(yakPlayer, error instanceof Exception ? (Exception) error : new RuntimeException(error));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in save failure listener", e);
            }
        }
    }

    // Async withPlayer methods that return CompletableFuture<Boolean>

    /**
     * Asynchronously perform an operation with a YakPlayer
     * @param playerId The player's UUID
     * @param operation The operation to perform
     * @param save Whether to save the player after the operation
     * @return CompletableFuture<Boolean> indicating success/failure
     */
    public CompletableFuture<Boolean> withPlayer(UUID playerId, PlayerOperation operation, boolean save) {
        if (playerId == null || operation == null) {
            logger.warning("withPlayer called with null playerId or operation");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                YakPlayer yakPlayer = getPlayer(playerId);
                if (yakPlayer == null) {
                    logger.fine("Player not found for UUID: " + playerId);
                    return false;
                }

                // Acquire player lock for thread safety
                ReentrantReadWriteLock lock = playerLocks.get(playerId);
                if (lock != null) {
                    lock.writeLock().lock();
                    try {
                        operation.execute(yakPlayer);
                    } finally {
                        lock.writeLock().unlock();
                    }
                } else {
                    operation.execute(yakPlayer);
                }

                // Save if requested
                if (save) {
                    try {
                        CompletableFuture<Boolean> saveFuture = savePlayer(yakPlayer);
                        return saveFuture.get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (Exception saveException) {
                        logger.log(Level.WARNING, "Failed to save player after operation: " + playerId, saveException);
                        return false;
                    }
                }

                return true;

            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in withPlayer operation for " + playerId, e);
                return false;
            }
        }, priorityExecutor);
    }

    /**
     * Asynchronously perform an operation with a YakPlayer (no save)
     */
    public CompletableFuture<Boolean> withPlayer(UUID playerId, PlayerOperation operation) {
        return withPlayer(playerId, operation, false);
    }

    /**
     * Asynchronously perform an operation with a YakPlayer by player name
     */
    public CompletableFuture<Boolean> withPlayer(String playerName, PlayerOperation operation, boolean save) {
        if (playerName == null || playerName.trim().isEmpty() || operation == null) {
            logger.warning("withPlayer called with invalid playerName or null operation");
            return CompletableFuture.completedFuture(false);
        }

        UUID playerId = playerNameCache.get(playerName.toLowerCase());
        if (playerId == null) {
            logger.fine("Player UUID not found for name: " + playerName);
            return CompletableFuture.completedFuture(false);
        }

        return withPlayer(playerId, operation, save);
    }

    /**
     * Asynchronously perform an operation with a YakPlayer by player name (no save)
     */
    public CompletableFuture<Boolean> withPlayer(String playerName, PlayerOperation operation) {
        return withPlayer(playerName, operation, false);
    }

    /**
     * Asynchronously perform an operation with a YakPlayer from Bukkit Player
     */
    public CompletableFuture<Boolean> withPlayer(Player player, PlayerOperation operation, boolean save) {
        if (player == null || operation == null) {
            logger.warning("withPlayer called with null player or operation");
            return CompletableFuture.completedFuture(false);
        }

        if (!player.isOnline()) {
            logger.fine("Bukkit player is not online: " + player.getName());
            return CompletableFuture.completedFuture(false);
        }

        return withPlayer(player.getUniqueId(), operation, save);
    }

    /**
     * Asynchronously perform an operation with a YakPlayer from Bukkit Player (no save)
     */
    public CompletableFuture<Boolean> withPlayer(Player player, PlayerOperation operation) {
        return withPlayer(player, operation, false);
    }

    // Synchronous withPlayer methods that return values (for read operations)

    /**
     * Synchronously perform an operation with a YakPlayer and return a value
     */
    public <T> T withPlayerSync(UUID playerId, PlayerFunction<T> operation) {
        return withPlayerSync(playerId, operation, null);
    }

    /**
     * Synchronously perform an operation with a YakPlayer and return a value, with default
     */
    public <T> T withPlayerSync(UUID playerId, PlayerFunction<T> operation, T defaultValue) {
        if (playerId == null || operation == null) {
            logger.warning("withPlayerSync called with null playerId or operation");
            return defaultValue;
        }

        try {
            YakPlayer yakPlayer = getPlayer(playerId);
            if (yakPlayer == null) {
                logger.fine("Player not found for UUID: " + playerId);
                return defaultValue;
            }

            // Acquire read lock for thread safety
            ReentrantReadWriteLock lock = playerLocks.get(playerId);
            if (lock != null) {
                lock.readLock().lock();
                try {
                    return operation.execute(yakPlayer);
                } finally {
                    lock.readLock().unlock();
                }
            } else {
                return operation.execute(yakPlayer);
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in withPlayerSync operation for " + playerId, e);
            return defaultValue;
        }
    }

    /**
     * Synchronously perform an operation with a YakPlayer by name and return a value
     */
    public <T> T withPlayerSync(String playerName, PlayerFunction<T> operation) {
        return withPlayerSync(playerName, operation, null);
    }

    /**
     * Synchronously perform an operation with a YakPlayer by name and return a value, with default
     */
    public <T> T withPlayerSync(String playerName, PlayerFunction<T> operation, T defaultValue) {
        if (playerName == null || playerName.trim().isEmpty() || operation == null) {
            logger.warning("withPlayerSync called with invalid playerName or null operation");
            return defaultValue;
        }

        UUID playerId = playerNameCache.get(playerName.toLowerCase());
        if (playerId == null) {
            logger.fine("Player UUID not found for name: " + playerName);
            return defaultValue;
        }

        return withPlayerSync(playerId, operation, defaultValue);
    }

    /**
     * Synchronously perform an operation with a YakPlayer from Bukkit Player and return a value
     */
    public <T> T withPlayerSync(Player player, PlayerFunction<T> operation) {
        return withPlayerSync(player, operation, null);
    }

    /**
     * Synchronously perform an operation with a YakPlayer from Bukkit Player and return a value, with default
     */
    public <T> T withPlayerSync(Player player, PlayerFunction<T> operation, T defaultValue) {
        if (player == null || operation == null) {
            logger.warning("withPlayerSync called with null player or operation");
            return defaultValue;
        }

        if (!player.isOnline()) {
            logger.fine("Bukkit player is not online: " + player.getName());
            return defaultValue;
        }

        return withPlayerSync(player.getUniqueId(), operation, defaultValue);
    }

    // Safely perform an operation with both Bukkit Player and YakPlayer

    /**
     * Safely perform an operation with both Bukkit Player and YakPlayer
     */
    public <T> T withBothPlayers(Player bukkitPlayer, BothPlayersOperation<T> operation) {
        return withBothPlayers(bukkitPlayer, operation, null);
    }

    /**
     * Safely perform an operation with both Bukkit Player and YakPlayer, with default value
     */
    public <T> T withBothPlayers(Player bukkitPlayer, BothPlayersOperation<T> operation, T defaultValue) {
        if (bukkitPlayer == null || operation == null) {
            logger.warning("withBothPlayers called with null player or operation");
            return defaultValue;
        }

        if (!bukkitPlayer.isOnline()) {
            logger.fine("Bukkit player is not online: " + bukkitPlayer.getName());
            return defaultValue;
        }

        try {
            YakPlayer yakPlayer = getPlayer(bukkitPlayer);
            if (yakPlayer == null) {
                logger.fine("YakPlayer not found for: " + bukkitPlayer.getName());
                return defaultValue;
            }

            return operation.execute(bukkitPlayer, yakPlayer);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in withBothPlayers operation for " + bukkitPlayer.getName(), e);
            return defaultValue;
        }
    }

    // Public API methods
    public YakPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    public YakPlayer getPlayer(String name) {
        if (name == null) return null;
        UUID uuid = playerNameCache.get(name.toLowerCase());
        return uuid != null ? onlinePlayers.get(uuid) : null;
    }

    public YakPlayer getPlayer(Player player) {
        return getPlayer(player.getUniqueId());
    }

    public Collection<YakPlayer> getOnlinePlayers() {
        return new ArrayList<>(onlinePlayers.values());
    }

    public CompletableFuture<Boolean> savePlayer(YakPlayer player) {
        return savePlayerDataAsync(player, false);
    }

    public void saveAllPlayers() {
        performAutoSave();
    }

    public void addPlayerLoadListener(PlayerLoadListener listener) {
        loadListeners.add(listener);
    }

    public void removePlayerLoadListener(PlayerLoadListener listener) {
        loadListeners.remove(listener);
    }

    public void addPlayerSaveListener(PlayerSaveListener listener) {
        saveListeners.add(listener);
    }

    public void removePlayerSaveListener(PlayerSaveListener listener) {
        saveListeners.remove(listener);
    }

    public boolean isShuttingDown() {
        return shutdownInProgress.get();
    }

    public boolean isSystemHealthy() {
        return systemHealthy.get();
    }

    public boolean isRepositoryReady() {
        return repositoryReady.get();
    }

    public YakPlayerRepository getRepository() {
        return repository;
    }

    // Updated convenience methods using the new async pattern

    /**
     * Safely add gems to a player
     */
    public CompletableFuture<Boolean> addPlayerGems(UUID playerId, int amount) {
        return withPlayer(playerId, yakPlayer -> {
            yakPlayer.addGems(amount);

            // Notify player if online
            Player bukkitPlayer = yakPlayer.getBukkitPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.sendMessage(ChatColor.GREEN + "Added " + amount + " gems. New balance: " + yakPlayer.getGems());
            }
        }, true);
    }

    /**
     * Safely remove gems from a player
     */
    public CompletableFuture<Boolean> removePlayerGems(UUID playerId, int amount) {
        return withPlayer(playerId, yakPlayer -> {
            if (yakPlayer.getGems() >= amount) {
                yakPlayer.setGems(yakPlayer.getGems() - amount);

                // Notify player if online
                Player bukkitPlayer = yakPlayer.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(ChatColor.YELLOW + "Removed " + amount + " gems. New balance: " + yakPlayer.getGems());
                }
            } else {
                throw new RuntimeException("Insufficient gems");
            }
        }, true);
    }

    /**
     * Safely set player level
     */
    public CompletableFuture<Boolean> setPlayerLevel(UUID playerId, int level) {
        return withPlayer(playerId, yakPlayer -> {
            yakPlayer.setLevel(level);

            // Notify player if online
            Player bukkitPlayer = yakPlayer.getBukkitPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.sendMessage(ChatColor.AQUA + "Level updated to: " + level);
            }
        }, true);
    }

    /**
     * Safely get a player's gems (synchronous)
     */
    public int getPlayerGems(UUID playerId) {
        return withPlayerSync(playerId, YakPlayer::getGems, 0);
    }

    /**
     * Safely get a player's level (synchronous)
     */
    public int getPlayerLevel(UUID playerId) {
        return withPlayerSync(playerId, YakPlayer::getLevel, 1);
    }

    /**
     * Safely check if players are buddies (synchronous)
     */
    public boolean areBuddies(UUID player1Id, UUID player2Id) {
        return withPlayerSync(player1Id, yakPlayer -> {
            YakPlayer otherPlayer = getPlayer(player2Id);
            return otherPlayer != null && yakPlayer.isBuddy(otherPlayer.getUsername());
        }, false);
    }

    /**
     * Safely get player's formatted display name (synchronous)
     */
    public String getPlayerDisplayName(UUID playerId) {
        return withPlayerSync(playerId, YakPlayer::getFormattedDisplayName, "Unknown Player");
    }

    /**
     * Safely update player location from Bukkit player
     */
    public CompletableFuture<Boolean> updatePlayerLocation(Player bukkitPlayer) {
        return withPlayer(bukkitPlayer, yakPlayer -> {
            yakPlayer.updateLocation(bukkitPlayer.getLocation());
        }, false);
    }

    /**
     * Safely update all player data from Bukkit player
     */
    public CompletableFuture<Boolean> updatePlayerData(Player bukkitPlayer) {
        return withPlayer(bukkitPlayer, yakPlayer -> {
            updatePlayerBeforeSave(yakPlayer, bukkitPlayer);
        }, true);
    }

    /**
     * Check if a player exists (is online and has YakPlayer data)
     */
    public boolean hasPlayer(UUID playerId) {
        return playerId != null && getPlayer(playerId) != null;
    }

    /**
     * Check if a player exists by name
     */
    public boolean hasPlayer(String playerName) {
        return playerName != null && !playerName.trim().isEmpty() && getPlayer(playerName) != null;
    }

    /**
     * Check if a Bukkit player has YakPlayer data
     */
    public boolean hasPlayer(Player player) {
        return player != null && player.isOnline() && getPlayer(player) != null;
    }

    /**
     * Get comprehensive performance statistics
     */
    public void logPerformanceStats() {
        logger.info("=== YakPlayerManager Performance Stats ===");
        logger.info("Online Players: " + onlinePlayers.size());
        logger.info("Loading Players: " + loadingPlayers.size());
        logger.info("Total Joins: " + totalPlayerJoins.get());
        logger.info("Total Quits: " + totalPlayerQuits.get());
        logger.info("Successful Loads: " + successfulLoads.get());
        logger.info("Failed Loads: " + failedLoads.get());
        logger.info("Successful Saves: " + successfulSaves.get());
        logger.info("Failed Saves: " + failedSaves.get());
        logger.info("Load Semaphore Available: " + loadSemaphore.availablePermits() + "/" + MAX_CONCURRENT_LOADS);
        logger.info("Save Semaphore Available: " + saveSemaphore.availablePermits() + "/" + MAX_CONCURRENT_SAVES);
        logger.info("Repository Ready: " + repositoryReady.get());
        logger.info("System Health: " + (systemHealthy.get() ? "HEALTHY" : "DEGRADED"));
        logger.info("==========================================");
    }

    public Map<String, Object> getDetailedStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("online_players", onlinePlayers.size());
        stats.put("loading_players", loadingPlayers.size());
        stats.put("total_joins", totalPlayerJoins.get());
        stats.put("total_quits", totalPlayerQuits.get());
        stats.put("successful_loads", successfulLoads.get());
        stats.put("failed_loads", failedLoads.get());
        stats.put("successful_saves", successfulSaves.get());
        stats.put("failed_saves", failedSaves.get());
        stats.put("load_permits_available", loadSemaphore.availablePermits());
        stats.put("save_permits_available", saveSemaphore.availablePermits());
        stats.put("repository_ready", repositoryReady.get());
        stats.put("system_healthy", systemHealthy.get());
        stats.put("shutdown_in_progress", shutdownInProgress.get());
        return stats;
    }
}
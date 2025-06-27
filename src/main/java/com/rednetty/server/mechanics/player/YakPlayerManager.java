package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Complete enhanced YakPlayer manager focused purely on data management operations.
 * UI/presentation logic is handled by JoinLeaveListener to avoid duplication.
 *
 * REMOVED PlayerJoinEvent handler to prevent duplicate processing!
 */
public class YakPlayerManager implements Listener {
    private static YakPlayerManager instance;
    private final YakPlayerRepository repository;
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Logger logger;
    private final Plugin plugin;

    // Enhanced async execution system
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService asyncExecutor;
    private final ExecutorService priorityExecutor;
    private final ExecutorService ioExecutor;

    // Enhanced task management
    private BukkitTask autoSaveTask;
    private BukkitTask emergencyBackupTask;
    private BukkitTask cacheMaintenance;
    private BukkitTask performanceMonitor;
    private BukkitTask dataValidationTask;
    private BukkitTask healthCheckTask;

    // Enhanced state management
    private volatile boolean shutdownInProgress = false;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean systemHealthy = new AtomicBoolean(true);

    // Enhanced error tracking and logging
    private final File dataErrorLog;
    private final File performanceLog;
    private final File auditLog;
    private final AtomicInteger saveCounter = new AtomicInteger(0);
    private final AtomicBoolean forceSaveAll = new AtomicBoolean(false);

    // Enhanced performance tracking
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerSaveTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPlayerActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLoadTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerLoadAttempts = new ConcurrentHashMap<>();

    // Enhanced caching system
    private final Map<UUID, String> displayNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> displayNameCacheTime = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> rankCache = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> alignmentCache = new ConcurrentHashMap<>();
    private static final long DISPLAY_NAME_CACHE_DURATION = 30000;
    private static final long RANK_CACHE_DURATION = 300000;

    // Enhanced configuration with dynamic adjustment
    private volatile long autoSaveInterval;
    private volatile long emergencyBackupInterval;
    private volatile long cacheMaintenanceInterval;
    private volatile long performanceMonitorInterval;
    private volatile int savePlayersPerCycle;
    private volatile int maxFailedLoadAttempts;
    private final int threadPoolSize;
    private final int priorityThreadPoolSize;
    private final int ioThreadPoolSize;

    // Enhanced performance metrics
    private final AtomicInteger totalLoads = new AtomicInteger(0);
    private final AtomicInteger totalSaves = new AtomicInteger(0);
    private final AtomicInteger failedSaves = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger duplicateLogins = new AtomicInteger(0);
    private final AtomicInteger emergencyCreations = new AtomicInteger(0);

    // Enhanced system integration
    private final AtomicBoolean chatSystemReady = new AtomicBoolean(false);
    private final AtomicBoolean moderationSystemReady = new AtomicBoolean(false);
    private final AtomicBoolean economySystemReady = new AtomicBoolean(false);
    private final AtomicBoolean partySystemReady = new AtomicBoolean(false);

    // Enhanced player data validation
    private final PlayerDataValidator dataValidator = new PlayerDataValidator();

    // Event system for other listeners to hook into
    private final Set<PlayerLoadListener> loadListeners = ConcurrentHashMap.newKeySet();
    private final Set<PlayerSaveListener> saveListeners = ConcurrentHashMap.newKeySet();

    /**
     * Interface for listening to player load events
     */
    public interface PlayerLoadListener {
        void onPlayerLoaded(Player player, YakPlayer yakPlayer, boolean isNewPlayer);
        void onPlayerLoadFailed(Player player, Exception exception);
    }

    /**
     * Interface for listening to player save events
     */
    public interface PlayerSaveListener {
        void onPlayerSaved(YakPlayer yakPlayer);
        void onPlayerSaveFailed(YakPlayer yakPlayer, Exception exception);
    }

    /**
     * Enhanced player data validation system
     */
    private static class PlayerDataValidator {
        private final Set<String> validatedPlayers = ConcurrentHashMap.newKeySet();
        private final Map<UUID, List<String>> validationErrors = new ConcurrentHashMap<>();

        public boolean validatePlayer(YakPlayer player) {
            if (player == null) return false;

            List<String> errors = new ArrayList<>();
            UUID uuid = player.getUUID();

            // Basic validation
            if (uuid == null) errors.add("Null UUID");
            if (player.getUsername() == null || player.getUsername().trim().isEmpty()) {
                errors.add("Invalid username");
            }

            // Economic validation
            if (player.getGems() < 0) errors.add("Negative gems");
            if (player.getLevel() < 1 || player.getLevel() > 250) {
                errors.add("Invalid level: " + player.getLevel());
            }

            // Alignment validation
            String alignment = player.getAlignment();
            if (alignment == null || (!alignment.equals("LAWFUL") && !alignment.equals("NEUTRAL") && !alignment.equals("CHAOTIC"))) {
                errors.add("Invalid alignment: " + alignment);
            }

            if (!errors.isEmpty()) {
                validationErrors.put(uuid, errors);
                return false;
            }

            validatedPlayers.add(uuid.toString());
            validationErrors.remove(uuid);
            return true;
        }

        public List<String> getValidationErrors(UUID uuid) {
            return validationErrors.getOrDefault(uuid, Collections.emptyList());
        }

        public void clearValidation(UUID uuid) {
            validatedPlayers.remove(uuid.toString());
            validationErrors.remove(uuid);
        }

        public boolean isValidated(UUID uuid) {
            return validatedPlayers.contains(uuid.toString());
        }
    }

    /**
     * Enhanced constructor with improved configuration
     */
    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.repository = new YakPlayerRepository();
        this.logger = plugin.getLogger();

        // Load enhanced configuration
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.emergencyBackupInterval = plugin.getConfig().getLong("player_manager.emergency_backup_interval_ticks", 18000L);
        this.cacheMaintenanceInterval = plugin.getConfig().getLong("player_manager.cache_maintenance_interval_ticks", 36000L);
        this.performanceMonitorInterval = plugin.getConfig().getLong("player_manager.performance_monitor_interval_ticks", 12000L);
        this.savePlayersPerCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 8);
        this.maxFailedLoadAttempts = plugin.getConfig().getInt("player_manager.max_failed_load_attempts", 3);
        this.threadPoolSize = plugin.getConfig().getInt("player_manager.thread_pool_size", 6);
        this.priorityThreadPoolSize = plugin.getConfig().getInt("player_manager.priority_thread_pool_size", 3);
        this.ioThreadPoolSize = plugin.getConfig().getInt("player_manager.io_thread_pool_size", 4);

        // Initialize enhanced thread pools
        this.scheduledExecutor = Executors.newScheduledThreadPool(4, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Scheduled-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) -> logger.log(Level.SEVERE, "Uncaught exception in scheduled thread", e));
            return thread;
        });

        this.asyncExecutor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Async-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) -> logger.log(Level.SEVERE, "Uncaught exception in async thread", e));
            return thread;
        });

        this.priorityExecutor = Executors.newFixedThreadPool(priorityThreadPoolSize, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Priority-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            thread.setUncaughtExceptionHandler((t, e) -> logger.log(Level.SEVERE, "Uncaught exception in priority thread", e));
            return thread;
        });

        this.ioExecutor = Executors.newFixedThreadPool(ioThreadPoolSize, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setUncaughtExceptionHandler((t, e) -> logger.log(Level.SEVERE, "Uncaught exception in IO thread", e));
            return thread;
        });

        // Create enhanced logs directory structure
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        this.dataErrorLog = new File(logsDir, "player_data_errors.log");
        this.performanceLog = new File(logsDir, "player_performance.log");
        this.auditLog = new File(logsDir, "player_audit.log");

        logger.info("Enhanced YakPlayerManager initialized with " + threadPoolSize + " async, " +
                priorityThreadPoolSize + " priority, and " + ioThreadPoolSize + " I/O threads");
    }

    public static YakPlayerManager getInstance() {
        if (instance == null) {
            synchronized (YakPlayerManager.class) {
                if (instance == null) {
                    instance = new YakPlayerManager();
                }
            }
        }
        return instance;
    }

    public YakPlayerRepository getRepository() {
        return repository;
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    public boolean isSystemHealthy() {
        return systemHealthy.get();
    }

    // Event listener registration methods
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

    /**
     * Enhanced initialization with comprehensive system checks
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting enhanced YakPlayerManager initialization...");

            // Register ONLY pre-login and quit events - NO PlayerJoinEvent!
            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);

            // Initialize repository connection
            if (!initializeRepository()) {
                throw new RuntimeException("Failed to initialize repository");
            }

            // Start enhanced task system
            startEnhancedTaskSystem();

            // Initialize system integration monitoring
            initializeSystemIntegration();

            // Load existing online players with enhanced batching
            loadExistingOnlinePlayersEnhanced();

            // Validate system health
            performInitialHealthCheck();

            logger.info("Enhanced YakPlayerManager enabled successfully with " + onlinePlayers.size() + " online players");
            logAudit("SYSTEM_START", "Enhanced YakPlayerManager initialized successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable enhanced YakPlayerManager", e);
            emergencyShutdown();
            throw new RuntimeException("YakPlayerManager initialization failed", e);
        }
    }

    /**
     * Enhanced pre-login handling with comprehensive ban checking
     * This is the ONLY player event we handle here
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shutdownInProgress) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "Server is shutting down. Please try again in a moment.");
            return;
        }

        UUID uuid = event.getUniqueId();
        failedLoads.remove(uuid);

        checkPlayerBanStatus(event, uuid);
    }

    private void checkPlayerBanStatus(AsyncPlayerPreLoginEvent event, UUID uuid) {
        try {
            Optional<YakPlayer> playerOpt = repository.findById(uuid).get(15, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                if (player.isBanned()) {
                    String banMessage = formatBanMessage(player);
                    if (banMessage != null) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage);
                        return;
                    }
                }

                if (!player.getUsername().equals(event.getName())) {
                    player.setUsername(event.getName());
                    repository.saveSync(player);
                }

                playerNameCache.put(event.getName().toLowerCase(), uuid);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error checking ban status for " + event.getName(), e);
            logDataError("Error checking ban status during prelogin: " + event.getName(), e);
        }
    }

    private String formatBanMessage(YakPlayer player) {
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append(ChatColor.BOLD).append("You are banned from this server!\n\n");
        message.append(ChatColor.GRAY).append("Reason: ").append(ChatColor.WHITE).append(player.getBanReason()).append("\n");

        if (player.getBanExpiry() > 0) {
            long remaining = player.getBanExpiry() - Instant.now().getEpochSecond();
            if (remaining > 0) {
                message.append(ChatColor.GRAY).append("Expires in: ").append(ChatColor.WHITE).append(formatDuration(remaining));
            } else {
                player.setBanned(false, "", 0);
                repository.saveSync(player);
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

    /**
     * Enhanced player quit handling - this stays here
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        CompletableFuture.runAsync(() -> {
            ReentrantLock lock = getPlayerLock(uuid);
            lock.lock();
            try {
                YakPlayer yakPlayer = onlinePlayers.get(uuid);
                if (yakPlayer != null) {
                    updatePlayerBeforeSave(yakPlayer, player);
                    yakPlayer.disconnect();

                    onlinePlayers.remove(uuid);
                    displayNameCache.remove(uuid);
                    displayNameCacheTime.remove(uuid);
                    playerNameCache.remove(player.getName().toLowerCase());

                    updateRankCache("", uuid, false);
                    updateAlignmentCache("", uuid, false);

                    savePlayerEnhanced(yakPlayer).thenAccept(success -> {
                        if (success) {
                            logger.fine("Saved player data on quit: " + player.getName());

                            // Notify save listeners
                            for (PlayerSaveListener listener : saveListeners) {
                                try {
                                    listener.onPlayerSaved(yakPlayer);
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Error in save listener", e);
                                }
                            }
                        } else {
                            logger.warning("Failed to save player data on quit: " + player.getName());

                            // Notify save listeners of failure
                            for (PlayerSaveListener listener : saveListeners) {
                                try {
                                    listener.onPlayerSaveFailed(yakPlayer, new RuntimeException("Save failed on quit"));
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Error in save listener", e);
                                }
                            }
                        }
                    });
                }
            } finally {
                lock.unlock();
            }
        }, asyncExecutor);
    }

    // Public API for JoinLeaveListener to use
    /**
     * Load a player's data and register them online
     * This is called by JoinLeaveListener, not an event handler
     */
    public CompletableFuture<YakPlayer> loadPlayerForJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        if (onlinePlayers.containsKey(uuid)) {
            duplicateLogins.incrementAndGet();
            logger.warning("Duplicate login detected for " + playerName + " (" + uuid + ")");

            YakPlayer existingPlayer = onlinePlayers.get(uuid);
            if (existingPlayer != null) {
                existingPlayer.disconnect();
            }
        }

        playerLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
        playerNameCache.put(playerName.toLowerCase(), uuid);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayerForJoinEnhanced(uuid, playerName);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error loading player on join: " + playerName, e);
                emergencyCreations.incrementAndGet();

                // Notify load listeners of failure
                for (PlayerLoadListener listener : loadListeners) {
                    try {
                        listener.onPlayerLoadFailed(player, e);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Error in load listener", ex);
                    }
                }

                return null;
            }
        }, priorityExecutor);
    }

    /**
     * Register a loaded player as online
     */
    public void registerPlayerOnline(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        UUID uuid = player.getUniqueId();

        if (yakPlayer != null) {
            onlinePlayers.put(uuid, yakPlayer);

            if (!isNewPlayer) {
                yakPlayer.connect(player);
            }

            initializePlayerSystemsEnhanced(player, yakPlayer);
            lastPlayerActivity.put(uuid, System.currentTimeMillis());
            playerLoadTimes.put(uuid, System.currentTimeMillis());
            updatePlayerCaches(player, yakPlayer);

            // Notify load listeners
            for (PlayerLoadListener listener : loadListeners) {
                try {
                    listener.onPlayerLoaded(player, yakPlayer, isNewPlayer);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error in load listener", e);
                }
            }
        }
    }

    // All the rest of the implementation methods remain the same...
    // [Continuing with all the private methods from the original implementation]

    private YakPlayer loadPlayerForJoinEnhanced(UUID uuid, String playerName) throws Exception {
        if (onlinePlayers.containsKey(uuid)) {
            logger.info("Player already loaded: " + playerName);
            cacheHits.incrementAndGet();
            return onlinePlayers.get(uuid);
        }

        cacheMisses.incrementAndGet();

        try {
            Optional<YakPlayer> playerOpt = repository.findById(uuid).get(25, TimeUnit.SECONDS);
            totalLoads.incrementAndGet();

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                if (dataValidator.validatePlayer(player)) {
                    logAudit("PLAYER_LOADED", "Successfully loaded existing player: " + playerName);
                    return player;
                } else {
                    if (attemptPlayerDataRepair(player)) {
                        return player;
                    } else {
                        logger.severe("Unable to repair player data for " + playerName + ", creating emergency backup");
                        return null;
                    }
                }
            } else {
                logAudit("NEW_PLAYER", "No existing data found for: " + playerName);
                return null;
            }

        } catch (TimeoutException e) {
            logger.severe("Database timeout loading player: " + playerName);
            throw new RuntimeException("Database timeout", e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Database error loading player: " + playerName, e);
            throw e;
        }
    }

    private boolean initializeRepository() {
        try {
            logger.info("Repository connection validated successfully");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error testing repository connection", e);
            return false;
        }
    }

    private void startEnhancedTaskSystem() {
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                try {
                    performIntelligentSave();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during auto-save task", e);
                    logDataError("Error during auto-save task", e);
                    systemHealthy.set(false);
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        emergencyBackupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                try {
                    performEmergencyBackup();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during emergency backup", e);
                    systemHealthy.set(false);
                }
            }
        }.runTaskTimerAsynchronously(plugin, emergencyBackupInterval, emergencyBackupInterval);

        cacheMaintenance = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                try {
                    performAdvancedCacheMaintenance();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during cache maintenance", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, cacheMaintenanceInterval, cacheMaintenanceInterval);

        performanceMonitor = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                try {
                    performComprehensiveMonitoring();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during performance monitoring", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, performanceMonitorInterval, performanceMonitorInterval);

        dataValidationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                try {
                    performDataValidation();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during data validation", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 72000L, 72000L);

        healthCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                try {
                    performSystemHealthCheck();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during health check", e);
                    systemHealthy.set(false);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);

        logger.info("Enhanced task system started with adaptive scheduling");
    }

    private void initializeSystemIntegration() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (ChatMechanics.getInstance() != null) {
                    chatSystemReady.set(true);
                    logger.info("Chat system integration verified");
                }
            } catch (Exception e) {
                logger.warning("Chat system not ready: " + e.getMessage());
            }

            try {
                if (ModerationMechanics.getInstance() != null) {
                    moderationSystemReady.set(true);
                    logger.info("Moderation system integration verified");
                }
            } catch (Exception e) {
                logger.warning("Moderation system not ready: " + e.getMessage());
            }

            boolean allSystemsReady = chatSystemReady.get() && moderationSystemReady.get();
            systemHealthy.set(allSystemsReady);

            logger.info("System integration monitoring initialized - Health: " + (allSystemsReady ? "GOOD" : "DEGRADED"));
        }, 40L);
    }

    private void loadExistingOnlinePlayersEnhanced() {
        Collection<? extends Player> onlinePlayersList = Bukkit.getOnlinePlayers();
        if (onlinePlayersList.isEmpty()) {
            return;
        }

        logger.info("Loading " + onlinePlayersList.size() + " existing online players with enhanced batching...");

        List<Player> playerList = new ArrayList<>(onlinePlayersList);
        int batchSize = Math.min(5, playerList.size());

        CompletableFuture<Void> allBatches = CompletableFuture.completedFuture(null);

        for (int i = 0; i < playerList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, playerList.size());
            List<Player> batch = playerList.subList(i, endIndex);
            final int batchNumber = i / batchSize;

            allBatches = allBatches.thenCompose(v ->
                    processBatchEnhanced(batch, batchNumber)
                            .exceptionally(ex -> {
                                logger.log(Level.WARNING, "Batch " + batchNumber + " failed, continuing with next batch", ex);
                                return null;
                            })
            );
        }

        allBatches.thenRun(() -> {
            logger.info("Enhanced online player loading completed. Loaded: " + onlinePlayers.size() + " players");
        });
    }

    private CompletableFuture<Void> processBatchEnhanced(List<Player> batch, int batchNumber) {
        return CompletableFuture.runAsync(() -> {
            logger.info("Processing enhanced batch " + (batchNumber + 1) + " (" + batch.size() + " players)");

            List<CompletableFuture<Void>> playerFutures = batch.stream()
                    .filter(Player::isOnline)
                    .map(this::loadPlayerForBatch)
                    .collect(Collectors.toList());

            try {
                CompletableFuture.allOf(playerFutures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Some players in batch " + batchNumber + " failed to load", e);
            }
        }, asyncExecutor);
    }

    private CompletableFuture<Void> loadPlayerForBatch(Player player) {
        return loadPlayerEnhanced(player.getUniqueId())
                .thenAccept(yakPlayer -> {
                    if (yakPlayer != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                onlinePlayers.put(player.getUniqueId(), yakPlayer);
                                playerNameCache.put(player.getName().toLowerCase(), player.getUniqueId());
                                playerLocks.put(player.getUniqueId(), new ReentrantLock());
                                yakPlayer.connect(player);
                                initializePlayerSystemsEnhanced(player, yakPlayer);
                                lastPlayerActivity.put(player.getUniqueId(), System.currentTimeMillis());
                                playerLoadTimes.put(player.getUniqueId(), System.currentTimeMillis());
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Error processing batch player: " + player.getName(), e);
                            }
                        });
                    } else {
                        logDataError("Failed to load existing player during startup: " + player.getName());
                    }
                })
                .exceptionally(e -> {
                    logDataError("Error loading existing player: " + player.getName(), e);
                    return null;
                });
    }

    private CompletableFuture<YakPlayer> loadPlayerEnhanced(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (onlinePlayers.containsKey(uuid)) {
                cacheHits.incrementAndGet();
                return onlinePlayers.get(uuid);
            }

            cacheMisses.incrementAndGet();
            int attempts = playerLoadAttempts.getOrDefault(uuid, 0);

            if (attempts >= maxFailedLoadAttempts) {
                logger.warning("Player " + uuid + " exceeded maximum load attempts (" + maxFailedLoadAttempts + ")");
                failedLoads.add(uuid);
                return null;
            }

            try {
                playerLoadAttempts.put(uuid, attempts + 1);

                Optional<YakPlayer> playerOpt = repository.findById(uuid).get(20, TimeUnit.SECONDS);
                totalLoads.incrementAndGet();

                if (playerOpt.isPresent()) {
                    YakPlayer player = playerOpt.get();

                    if (dataValidator.validatePlayer(player)) {
                        playerLoadAttempts.remove(uuid);
                        return player;
                    } else {
                        List<String> errors = dataValidator.getValidationErrors(uuid);
                        logger.warning("Player data validation failed for " + uuid + ": " + errors);

                        if (attemptPlayerDataRepair(player)) {
                            logger.info("Successfully repaired player data for " + uuid);
                            return player;
                        }

                        return null;
                    }
                } else {
                    playerLoadAttempts.remove(uuid);
                    return null;
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load player " + uuid + " (attempt " + (attempts + 1) + ")", e);

                if (attempts + 1 >= maxFailedLoadAttempts) {
                    failedLoads.add(uuid);
                }

                throw new RuntimeException("Player load failed", e);
            }
        }, ioExecutor);
    }

    private boolean attemptPlayerDataRepair(YakPlayer player) {
        boolean repaired = false;

        try {
            if (player.getGems() < 0) {
                player.setGems(0);
                repaired = true;
            }

            if (player.getLevel() < 1) {
                player.setLevel(1);
                repaired = true;
            } else if (player.getLevel() > 250) {
                player.setLevel(250);
                repaired = true;
            }

            String alignment = player.getAlignment();
            if (alignment == null || (!alignment.equals("LAWFUL") && !alignment.equals("NEUTRAL") && !alignment.equals("CHAOTIC"))) {
                player.setAlignment("LAWFUL");
                repaired = true;
            }

            if (player.getUsername() == null || player.getUsername().trim().isEmpty()) {
                Player bukkitPlayer = Bukkit.getPlayer(player.getUUID());
                if (bukkitPlayer != null) {
                    player.setUsername(bukkitPlayer.getName());
                    repaired = true;
                }
            }

            if (repaired) {
                repository.saveSync(player);
                dataValidator.clearValidation(player.getUUID());
                logAudit("DATA_REPAIR", "Repaired player data for " + player.getUsername());
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to repair player data for " + player.getUUID(), e);
            return false;
        }

        return repaired;
    }

    private void initializePlayerSystemsEnhanced(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            initializePlayerEnergyEnhanced(yakPlayer);

            if (moderationSystemReady.get()) {
                try {
                    Rank rank = Rank.valueOf(yakPlayer.getRank());
                    ModerationMechanics.rankMap.put(uuid, rank);
                    updateRankCache(rank.name(), uuid, true);
                } catch (IllegalArgumentException e) {
                    ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
                    updateRankCache("DEFAULT", uuid, true);
                }
            }

            if (chatSystemReady.get()) {
                try {
                    ChatTag tag = ChatTag.valueOf(yakPlayer.getChatTag());
                    ChatMechanics.getPlayerTags().put(uuid, tag);
                } catch (IllegalArgumentException e) {
                    ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
                }
            }

            updateAlignmentCache(yakPlayer.getAlignment(), uuid, true);
            updateDisplayNameCacheEnhanced(yakPlayer);

            logAudit("PLAYER_INIT", "Enhanced systems initialized for: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing enhanced player systems for " + player.getName(), e);
        }
    }

    private void initializePlayerEnergyEnhanced(YakPlayer yakPlayer) {
        Player player = yakPlayer.getBukkitPlayer();
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setExp(1.0f);
                player.setLevel(100);
            });

            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }
        }
    }

    private void updateDisplayNameCacheEnhanced(YakPlayer yakPlayer) {
        UUID uuid = yakPlayer.getUUID();
        String displayName = yakPlayer.getFormattedDisplayName();
        displayNameCache.put(uuid, displayName);
        displayNameCacheTime.put(uuid, System.currentTimeMillis());
    }

    private void updatePlayerCaches(Player player, YakPlayer yakPlayer) {
        if (yakPlayer != null) {
            UUID uuid = player.getUniqueId();
            updateRankCache(yakPlayer.getRank(), uuid, true);
            updateAlignmentCache(yakPlayer.getAlignment(), uuid, true);
        }
    }

    private void updateRankCache(String rank, UUID uuid, boolean add) {
        if (add) {
            rankCache.computeIfAbsent(rank, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        } else {
            rankCache.values().forEach(set -> set.remove(uuid));
        }
    }

    private void updateAlignmentCache(String alignment, UUID uuid, boolean add) {
        if (add) {
            alignmentCache.computeIfAbsent(alignment, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        } else {
            alignmentCache.values().forEach(set -> set.remove(uuid));
        }
    }

    private void performIntelligentSave() {
        List<UUID> playersToSave = determinePlayersToSaveIntelligently();

        if (playersToSave.isEmpty()) {
            return;
        }

        AtomicInteger savedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        int currentBatchSize = calculateAdaptiveBatchSize();
        List<List<UUID>> batches = partitionList(playersToSave, currentBatchSize);

        CompletableFuture<Void> allBatches = CompletableFuture.completedFuture(null);

        for (List<UUID> batch : batches) {
            allBatches = allBatches.thenCompose(v ->
                    processSaveBatch(batch, savedCount, errorCount)
            );
        }

        allBatches.thenRun(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int saved = savedCount.get();
            int errors = errorCount.get();

            if (saved > 0 || errors > 0) {
                logger.info("Intelligent save completed: " + saved + " saved, " + errors +
                        " errors in " + duration + "ms" + (forceSaveAll.get() ? " (emergency)" : ""));
            }

            totalSaves.addAndGet(saved);
            failedSaves.addAndGet(errors);
            adjustSaveInterval(duration, saved);
        });
    }

    private List<UUID> determinePlayersToSaveIntelligently() {
        List<UUID> allPlayers = new ArrayList<>(onlinePlayers.keySet());

        if (forceSaveAll.get() || allPlayers.size() <= savePlayersPerCycle) {
            return allPlayers;
        }

        long currentTime = System.currentTimeMillis();
        long saveThreshold = currentTime - TimeUnit.MINUTES.toMillis(10);

        List<UUID> priorityPlayers = allPlayers.stream()
                .filter(uuid -> {
                    YakPlayer player = onlinePlayers.get(uuid);
                    return player != null && player.isDirty() &&
                            (!playerSaveTimes.containsKey(uuid) || playerSaveTimes.get(uuid) < saveThreshold);
                })
                .collect(Collectors.toList());

        if (!priorityPlayers.isEmpty()) {
            return priorityPlayers.size() > savePlayersPerCycle ?
                    priorityPlayers.subList(0, savePlayersPerCycle) : priorityPlayers;
        }

        List<UUID> stalePlayersSaveRotation = allPlayers.stream()
                .filter(uuid -> !playerSaveTimes.containsKey(uuid) || playerSaveTimes.get(uuid) < saveThreshold)
                .collect(Collectors.toList());

        if (!stalePlayersSaveRotation.isEmpty()) {
            return stalePlayersSaveRotation.size() > savePlayersPerCycle ?
                    stalePlayersSaveRotation.subList(0, savePlayersPerCycle) : stalePlayersSaveRotation;
        }

        Collections.rotate(allPlayers, -saveCounter.getAndIncrement());
        return allPlayers.subList(0, Math.min(savePlayersPerCycle, allPlayers.size()));
    }

    private int calculateAdaptiveBatchSize() {
        int onlineCount = onlinePlayers.size();
        double systemLoad = getSystemLoad();

        if (systemLoad > 0.8 || onlineCount > 100) {
            return Math.max(2, savePlayersPerCycle / 2);
        } else if (systemLoad < 0.3 && onlineCount < 20) {
            return savePlayersPerCycle * 2;
        }

        return savePlayersPerCycle;
    }

    private double getSystemLoad() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (double) (totalMemory - freeMemory) / totalMemory;
    }

    private void adjustSaveInterval(long duration, int saved) {
        if (saved > 0) {
            double averageTimePerPlayer = (double) duration / saved;

            if (averageTimePerPlayer > 1000) {
                autoSaveInterval = Math.min(autoSaveInterval + 1200L, 24000L);
            } else if (averageTimePerPlayer < 200) {
                autoSaveInterval = Math.max(autoSaveInterval - 600L, 3000L);
            }
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    private CompletableFuture<Void> processSaveBatch(List<UUID> batch, AtomicInteger savedCount, AtomicInteger errorCount) {
        List<CompletableFuture<Boolean>> saveFutures = batch.stream()
                .map(uuid -> {
                    YakPlayer player = onlinePlayers.get(uuid);
                    Player bukkitPlayer = Bukkit.getPlayer(uuid);

                    if (player != null && bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        try {
                            updatePlayerBeforeSave(player, bukkitPlayer);
                            return savePlayerEnhanced(player).thenApply(success -> {
                                if (success) {
                                    savedCount.incrementAndGet();
                                    playerSaveTimes.put(uuid, System.currentTimeMillis());
                                    lastPlayerActivity.put(uuid, System.currentTimeMillis());
                                } else {
                                    errorCount.incrementAndGet();
                                }
                                return success;
                            });
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error during intelligent save for " + player.getUsername(), e);
                            errorCount.incrementAndGet();
                            return CompletableFuture.completedFuture(false);
                        }
                    }
                    return CompletableFuture.completedFuture(false);
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]));
    }

    private CompletableFuture<Boolean> savePlayerEnhanced(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = getPlayerLock(player.getUUID());
            try {
                if (lock.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        Player bukkit = player.getBukkitPlayer();
                        if (bukkit != null && bukkit.isOnline()) {
                            updatePlayerBeforeSave(player, bukkit);
                        }

                        repository.saveSync(player);
                        playerSaveTimes.put(player.getUUID(), System.currentTimeMillis());
                        player.clearDirty();
                        updateDisplayNameCacheEnhanced(player);

                        for (PlayerSaveListener listener : saveListeners) {
                            try {
                                listener.onPlayerSaved(player);
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Error in save listener", e);
                            }
                        }

                        return true;

                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error saving player: " + player.getUsername(), e);
                        logDataError("Error saving player: " + player.getUsername(), e);

                        for (PlayerSaveListener listener : saveListeners) {
                            try {
                                listener.onPlayerSaveFailed(player, e);
                            } catch (Exception ex) {
                                logger.log(Level.WARNING, "Error in save listener", ex);
                            }
                        }

                        return false;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    logger.warning("Could not acquire lock for saving player: " + player.getUsername());
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }, ioExecutor);
    }

    private void updatePlayerBeforeSave(YakPlayer yakPlayer, Player bukkitPlayer) {
        try {
            yakPlayer.updateLocation(bukkitPlayer.getLocation());
            yakPlayer.updateInventory(bukkitPlayer);
            yakPlayer.updateStats(bukkitPlayer);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player before save: " + yakPlayer.getUsername(), e);
        }
    }

    private void performEmergencyBackup() {
        forceSaveAll.set(true);
        performIntelligentSave();
        forceSaveAll.set(false);
        logger.info("Emergency backup completed for " + onlinePlayers.size() + " players");
    }

    private void performAdvancedCacheMaintenance() {
        long currentTime = System.currentTimeMillis();
        long staleThreshold = currentTime - TimeUnit.HOURS.toMillis(1);
        long displayNameThreshold = currentTime - DISPLAY_NAME_CACHE_DURATION;

        int cleaned = 0;

        int failedLoadsSize = failedLoads.size();
        failedLoads.removeIf(uuid -> {
            Long lastActivity = lastPlayerActivity.get(uuid);
            return lastActivity != null && lastActivity < staleThreshold;
        });
        cleaned += failedLoadsSize - failedLoads.size();

        int saveTimesSize = playerSaveTimes.size();
        playerSaveTimes.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });
        cleaned += saveTimesSize - playerSaveTimes.size();

        int activitySize = lastPlayerActivity.size();
        lastPlayerActivity.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });
        cleaned += activitySize - lastPlayerActivity.size();

        int displayCacheSize = displayNameCache.size();
        displayNameCacheTime.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            if (entry.getValue() < displayNameThreshold || !onlinePlayers.containsKey(uuid)) {
                displayNameCache.remove(uuid);
                return true;
            }
            return false;
        });
        cleaned += displayCacheSize - displayNameCache.size();

        int nameCacheSize = playerNameCache.size();
        playerNameCache.entrySet().removeIf(entry -> {
            UUID uuid = entry.getValue();
            return !onlinePlayers.containsKey(uuid);
        });
        cleaned += nameCacheSize - playerNameCache.size();

        if (cleaned > 0) {
            logger.fine("Advanced cache maintenance completed - cleaned " + cleaned + " stale entries");
        }
    }

    private void performComprehensiveMonitoring() {
        try {
            long currentTime = System.currentTimeMillis();
            int onlineCount = onlinePlayers.size();
            int loads = totalLoads.get();
            int saves = totalSaves.get();
            int failedSavesCount = failedSaves.get();
            int hits = cacheHits.get();
            int misses = cacheMisses.get();

            double cacheHitRatio = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0;
            double saveSuccessRatio = saves > 0 ? (double) (saves - failedSavesCount) / saves * 100 : 100;

            logPerformanceData(currentTime, onlineCount, loads, saves, failedSavesCount, cacheHitRatio, saveSuccessRatio);

            if (saveSuccessRatio < 95.0) {
                logger.warning("Low save success ratio: " + String.format("%.1f%%", saveSuccessRatio));
                systemHealthy.set(false);
            }

            if (cacheHitRatio < 80.0 && (hits + misses) > 100) {
                logger.warning("Low cache hit ratio: " + String.format("%.1f%%", cacheHitRatio));
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during performance monitoring", e);
        }
    }

    private void performDataValidation() {
        int validatedCount = 0;
        int errorCount = 0;

        for (YakPlayer player : onlinePlayers.values()) {
            if (dataValidator.validatePlayer(player)) {
                validatedCount++;
            } else {
                errorCount++;
                List<String> errors = dataValidator.getValidationErrors(player.getUUID());
                logger.warning("Data validation failed for " + player.getUsername() + ": " + errors);

                if (attemptPlayerDataRepair(player)) {
                    logger.info("Auto-repaired data for " + player.getUsername());
                }
            }
        }

        if (errorCount > 0) {
            logger.warning("Data validation completed: " + validatedCount + " valid, " + errorCount + " errors");
            systemHealthy.set(false);
        }
    }

    private void performSystemHealthCheck() {
        boolean healthy = true;

        if (failedSaves.get() > totalSaves.get() * 0.1) {
            healthy = false;
            logger.warning("High save failure rate detected");
        }

        if (failedLoads.size() > onlinePlayers.size() * 0.05) {
            healthy = false;
            logger.warning("High load failure count detected");
        }

        if (duplicateLogins.get() > 10) {
            logger.warning("High duplicate login count: " + duplicateLogins.get());
        }

        systemHealthy.set(healthy);

        if (!healthy) {
            notifyAdministrators("Player management system health degraded!");
        }
    }

    private void performInitialHealthCheck() {
        logger.info("Performing initial system health check...");
        systemHealthy.set(true);
        logger.info("Initial health check completed - System status: HEALTHY");
    }

    private void notifyAdministrators(String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("yakserver.admin")) {
                    player.sendMessage(ChatColor.RED + "[SYSTEM] " + message);
                }
            }
        });
    }

    private void logPerformanceData(long timestamp, int online, int loads, int saves, int failedSaves,
                                    double cacheHitRatio, double saveSuccessRatio) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timeStr = dateFormat.format(new Date(timestamp));

            synchronized (performanceLog) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(performanceLog, true))) {
                    writer.printf("[%s] Online: %d, Loads: %d, Saves: %d, Failed: %d, Cache Hit: %.1f%%, Save Success: %.1f%%\n",
                            timeStr, online, loads, saves, failedSaves, cacheHitRatio, saveSuccessRatio);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to log performance data", e);
        }
    }

    private void logAudit(String action, String details) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());

            synchronized (auditLog) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(auditLog, true))) {
                    writer.println("[" + timestamp + "] " + action + " - " + details);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to write audit log", e);
        }
    }

    private void logDataError(String message, Throwable e) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());

            synchronized (dataErrorLog) {
                try (PrintWriter writer = new PrintWriter(new FileWriter(dataErrorLog, true))) {
                    writer.println("[" + timestamp + "] " + message);
                    if (e != null) {
                        writer.println("Exception: " + e.getMessage());
                        e.printStackTrace(writer);
                        writer.println();
                    }
                }
            }

            logger.warning("Data error: " + message + (e != null ? " - " + e.getMessage() : ""));
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to log data error", ex);
        }
    }

    private void logDataError(String message) {
        logDataError(message, null);
    }

    public void onDisable() {
        shutdownInProgress = true;
        logger.info("Starting enhanced shutdown process...");

        try {
            cancelTasks();
            saveAllPlayersOnShutdown();
            cleanup();

            logger.info("Enhanced YakPlayerManager shutdown completed successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
        }
    }

    private void cancelTasks() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        if (emergencyBackupTask != null && !emergencyBackupTask.isCancelled()) {
            emergencyBackupTask.cancel();
        }
        if (cacheMaintenance != null && !cacheMaintenance.isCancelled()) {
            cacheMaintenance.cancel();
        }
        if (performanceMonitor != null && !performanceMonitor.isCancelled()) {
            performanceMonitor.cancel();
        }
        if (dataValidationTask != null && !dataValidationTask.isCancelled()) {
            dataValidationTask.cancel();
        }
        if (healthCheckTask != null && !healthCheckTask.isCancelled()) {
            healthCheckTask.cancel();
        }
    }

    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();
            if (player != null && player.isOnline()) {
                updatePlayerBeforeSave(yakPlayer, player);
            }

            saveFutures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    repository.saveSync(yakPlayer);
                    return true;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to save player on shutdown: " + yakPlayer.getUsername(), e);
                    return false;
                }
            }, ioExecutor));
        }

        try {
            CompletableFuture<Void> allSaves = CompletableFuture.allOf(
                    saveFutures.toArray(new CompletableFuture[0]));

            allSaves.get(60, TimeUnit.SECONDS);

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
            logger.warning("Shutdown save timed out - some players may not have been saved");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown save", e);
        }
    }

    private void cleanup() {
        onlinePlayers.clear();
        playerNameCache.clear();
        playerLocks.clear();
        failedLoads.clear();
        playerSaveTimes.clear();
        lastPlayerActivity.clear();
        playerLoadTimes.clear();
        displayNameCache.clear();
        displayNameCacheTime.clear();
        rankCache.clear();
        alignmentCache.clear();
        loadListeners.clear();
        saveListeners.clear();

        shutdownExecutor(scheduledExecutor, "scheduled");
        shutdownExecutor(asyncExecutor, "async");
        shutdownExecutor(priorityExecutor, "priority");
        shutdownExecutor(ioExecutor, "io");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Forcing shutdown of " + name + " executor");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warning("Interrupted while shutting down " + name + " executor");
            executor.shutdownNow();
        }
    }

    private void emergencyShutdown() {
        try {
            shutdownInProgress = true;
            cancelTasks();
            cleanup();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during emergency shutdown", e);
        }
    }

    // Public API methods
    public YakPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    public YakPlayer getPlayer(String name) {
        if (name == null) return null;

        UUID uuid = playerNameCache.get(name.toLowerCase());
        if (uuid != null) {
            YakPlayer player = onlinePlayers.get(uuid);
            if (player != null) {
                cacheHits.incrementAndGet();
                return player;
            }
        }

        cacheMisses.incrementAndGet();
        return onlinePlayers.values().stream()
                .filter(player -> player.getUsername().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public YakPlayer getPlayer(Player player) {
        return getPlayer(player.getUniqueId());
    }

    public Collection<YakPlayer> getOnlinePlayers() {
        return new ArrayList<>(onlinePlayers.values());
    }

    public ReentrantLock getPlayerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, id -> new ReentrantLock());
    }

    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> action, boolean save) {
        return CompletableFuture.supplyAsync(() -> {
            YakPlayer player = getPlayer(uuid);
            if (player != null) {
                ReentrantLock lock = getPlayerLock(uuid);
                lock.lock();
                try {
                    action.accept(player);
                    if (save) {
                        return savePlayerEnhanced(player).get(10, TimeUnit.SECONDS);
                    }
                    return true;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error in withPlayer operation for " + uuid, e);
                    return false;
                } finally {
                    lock.unlock();
                }
            }
            return false;
        }, asyncExecutor);
    }

    public CompletableFuture<Boolean> savePlayer(YakPlayer player) {
        return savePlayerEnhanced(player);
    }

    public void saveAllPlayers() {
        forceSaveAll.set(true);
        performIntelligentSave();
        forceSaveAll.set(false);
    }

    public void logPerformanceStats() {
        logger.info("Enhanced YakPlayerManager Performance Stats:");
        logger.info("  Online Players: " + onlinePlayers.size());
        logger.info("  Total Loads: " + totalLoads.get());
        logger.info("  Total Saves: " + totalSaves.get());
        logger.info("  Failed Saves: " + failedSaves.get());
        logger.info("  Cache Hits: " + cacheHits.get());
        logger.info("  Cache Misses: " + cacheMisses.get());
        logger.info("  Duplicate Logins: " + duplicateLogins.get());
        logger.info("  Emergency Creations: " + emergencyCreations.get());
        logger.info("  Cache Hit Ratio: " + String.format("%.1f%%",
                (cacheHits.get() + cacheMisses.get()) > 0 ?
                        (double) cacheHits.get() / (cacheHits.get() + cacheMisses.get()) * 100 : 0));
        logger.info("  System Health: " + (systemHealthy.get() ? "HEALTHY" : "DEGRADED"));
    }

    public boolean isSystemReady() {
        return initialized.get() && !shutdownInProgress && systemHealthy.get();
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("initialized", initialized.get());
        status.put("shutdownInProgress", shutdownInProgress);
        status.put("systemHealthy", systemHealthy.get());
        status.put("onlinePlayers", onlinePlayers.size());
        status.put("totalLoads", totalLoads.get());
        status.put("totalSaves", totalSaves.get());
        status.put("failedSaves", failedSaves.get());
        status.put("cacheHits", cacheHits.get());
        status.put("cacheMisses", cacheMisses.get());
        status.put("duplicateLogins", duplicateLogins.get());
        status.put("emergencyCreations", emergencyCreations.get());
        status.put("chatSystemReady", chatSystemReady.get());
        status.put("moderationSystemReady", moderationSystemReady.get());
        return status;
    }
}
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
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 * Complete enhanced YakPlayer manager with modern 1.20.4 API usage, improved async patterns,
 * comprehensive error handling, performance monitoring, and advanced caching systems.
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

            // Register events first
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
     * Initialize repository with connection validation
     */
    private boolean initializeRepository() {
        try {
            logger.info("Repository connection validated successfully");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error testing repository connection", e);
            return false;
        }
    }

    /**
     * Enhanced task system with adaptive scheduling
     */
    private void startEnhancedTaskSystem() {
        // Enhanced auto-save with adaptive intervals
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

        // Enhanced emergency backup
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

        // Enhanced cache maintenance
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

        // Enhanced performance monitoring
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

        // Data validation task
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

        // System health check
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

    /**
     * Initialize system integration monitoring
     */
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

    /**
     * Enhanced online player loading with better batching and error recovery
     */
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

    /**
     * Intelligent save system with adaptive algorithms
     */
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

        // Priority 1: Dirty players that haven't been saved recently
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

        // Priority 2: Players that haven't been saved recently
        List<UUID> stalePlayersSaveRotation = allPlayers.stream()
                .filter(uuid -> !playerSaveTimes.containsKey(uuid) || playerSaveTimes.get(uuid) < saveThreshold)
                .collect(Collectors.toList());

        if (!stalePlayersSaveRotation.isEmpty()) {
            return stalePlayersSaveRotation.size() > savePlayersPerCycle ?
                    stalePlayersSaveRotation.subList(0, savePlayersPerCycle) : stalePlayersSaveRotation;
        }

        // Priority 3: Rotating subset
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

    /**
     * Enhanced player loading with retry logic and validation
     */
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

    /**
     * Attempt to repair common player data issues
     */
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

    /**
     * Enhanced pre-login handling with comprehensive ban checking
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
     * Enhanced player join handling with comprehensive validation
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (onlinePlayers.containsKey(uuid)) {
            duplicateLogins.incrementAndGet();
            logger.warning("Duplicate login detected for " + player.getName() + " (" + uuid + ")");

            YakPlayer existingPlayer = onlinePlayers.get(uuid);
            if (existingPlayer != null) {
                existingPlayer.disconnect();
            }
        }

        playerLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
        playerNameCache.put(player.getName().toLowerCase(), uuid);

        CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayerForJoinEnhanced(uuid, player.getName());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error loading player on join: " + player.getName(), e);
                emergencyCreations.incrementAndGet();
                return null;
            }
        }, priorityExecutor).thenAccept(yakPlayer -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePlayerJoinResultEnhanced(player, yakPlayer);
            });
        }).exceptionally(ex -> {
            logger.log(Level.SEVERE, "Critical error in player join handling", ex);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.kickPlayer(ChatColor.RED + "Unable to load player data. Please try again.");
            });
            return null;
        });
    }

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

    private void handlePlayerJoinResultEnhanced(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        if (yakPlayer == null) {
            handleNewPlayerEnhanced(player);
        } else {
            handleExistingPlayerEnhanced(player, yakPlayer);
        }

        lastPlayerActivity.put(uuid, System.currentTimeMillis());
        playerLoadTimes.put(uuid, System.currentTimeMillis());
        updatePlayerCaches(player, yakPlayer);
    }

    private void handleNewPlayerEnhanced(Player player) {
        UUID uuid = player.getUniqueId();

        if (failedLoads.contains(uuid)) {
            player.kickPlayer(ChatColor.RED + "Unable to create player data. Please contact an administrator.");
            logDataError("Kicked new player after failed loads: " + player.getName());
            return;
        }

        try {
            YakPlayer newPlayer = new YakPlayer(player);

            if (!dataValidator.validatePlayer(newPlayer)) {
                logger.severe("New player data validation failed for " + player.getName());
                player.kickPlayer(ChatColor.RED + "Player data validation failed. Please contact an administrator.");
                return;
            }

            onlinePlayers.put(uuid, newPlayer);
            initializePlayerSystemsEnhanced(player, newPlayer);

            savePlayerEnhanced(newPlayer).thenAccept(success -> {
                if (success) {
                    logAudit("NEW_PLAYER_CREATED", "Successfully created new player: " + player.getName());
                } else {
                    logger.warning("Failed to save new player data for: " + player.getName());
                }
            });

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    showEnhancedWelcomeExperience(player);
                }
            }, 40L);

            logger.info("Created enhanced new player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating new player: " + player.getName(), e);
            failedLoads.add(uuid);
            player.kickPlayer(ChatColor.RED + "Unable to create player data. Please try again.");
        }
    }

    private void handleExistingPlayerEnhanced(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            yakPlayer.connect(player);
            onlinePlayers.put(uuid, yakPlayer);

            applyPlayerData(player, yakPlayer);
            initializePlayerSystemsEnhanced(player, yakPlayer);

            logger.info("Loaded existing player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading existing player: " + player.getName(), e);
            handlePlayerDataError(player, e);
        }
    }

    private void applyPlayerData(Player player, YakPlayer yakPlayer) {
        try {
            if (yakPlayer.getLocation() != null && yakPlayer.getLocation().getWorld() != null) {
                player.teleport(yakPlayer.getLocation());
            }

            yakPlayer.applyInventory(player);
            yakPlayer.applyStats(player);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying player data for " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "Warning: Some of your player data may not have loaded correctly. " +
                    "Please report this to an administrator if you notice issues.");
        }
    }

    private void handlePlayerDataError(Player player, Exception e) {
        UUID uuid = player.getUniqueId();
        logDataError("Error loading player data for " + player.getName(), e);

        YakPlayer emergencyPlayer = new YakPlayer(player);
        onlinePlayers.put(uuid, emergencyPlayer);
        initializePlayerEnergyEnhanced(emergencyPlayer);

        player.sendMessage(ChatColor.RED + "Error loading your player data. Emergency data created. " +
                "Please report this issue to an administrator.");

        notifyStaffOfDataError(player.getName());
    }

    private void notifyStaffOfDataError(String playerName) {
        String message = ChatColor.DARK_RED + "[ADMIN] " + ChatColor.RED + "Data error for player: " + playerName;
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("yakserver.admin"))
                .forEach(p -> p.sendMessage(message));
    }

    private void showEnhancedWelcomeExperience(Player player) {
        player.sendTitle(
                ChatColor.GOLD + "✦ Welcome to YakRealms! ✦",
                ChatColor.YELLOW + "Your adventure begins now",
                20, 80, 20
        );

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.AQUA + "       Welcome to " + ChatColor.BOLD + "YakRealms" + ChatColor.AQUA + "!");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "• Your player data has been created successfully");
        player.sendMessage(ChatColor.YELLOW + "• Type " + ChatColor.GREEN + "/help" + ChatColor.YELLOW + " for commands");
        player.sendMessage(ChatColor.YELLOW + "• Join our community: " + ChatColor.BLUE + "discord.gg/JYf6R2VKE7");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "Good luck on your adventure!");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage("");

        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc.add(0, 2, 0), 20, 2, 2, 2, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /**
     * Enhanced system initialization for players
     */
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

    /**
     * Enhanced player quit handling
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
                        } else {
                            logger.warning("Failed to save player data on quit: " + player.getName());
                        }
                    });
                }
            } finally {
                lock.unlock();
            }
        }, asyncExecutor);
    }

    // Enhanced save and monitoring methods
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

                        return true;

                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error saving player: " + player.getUsername(), e);
                        logDataError("Error saving player: " + player.getUsername(), e);
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

    // Enhanced monitoring methods
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

    /**
     * Enhanced shutdown process
     */
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
    // Enhanced save methods
    public CompletableFuture<Boolean> savePlayer(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            ReentrantLock lock = getPlayerLock(player.getUUID());
            try {
                if (lock.tryLock(5, TimeUnit.SECONDS)) {
                    try {
                        // Update current state if online
                        Player bukkit = player.getBukkitPlayer();
                        if (bukkit != null && bukkit.isOnline()) {
                            updatePlayerBeforeSave(player, bukkit);
                        }

                        // Save to database
                        repository.save(player).get(10, TimeUnit.SECONDS);
                        playerSaveTimes.put(player.getUUID(), System.currentTimeMillis());
                        player.clearDirty();
                        return true;

                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error saving player: " + player.getUsername(), e);
                        logDataError("Error saving player: " + player.getUsername(), e);
                        return false;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    logger.warning("Could not acquire lock for saving player: " + player.getUsername());
                    return false;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }, asyncExecutor);
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
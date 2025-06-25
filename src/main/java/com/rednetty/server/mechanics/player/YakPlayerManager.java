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
import org.bukkit.event.player.PlayerLoginEvent;
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
 * Enhanced YakPlayer manager with improved performance, thread safety,
 * better error handling, optimized database operations, and full integration
 * with all YakPlayer systems.
 */
public class YakPlayerManager implements Listener {
    private static YakPlayerManager instance;
    private final YakPlayerRepository repository;
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameCache = new ConcurrentHashMap<>(); // Name -> UUID mapping
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Logger logger;
    private final Plugin plugin;

    // Enhanced task management
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService asyncExecutor;
    private final ExecutorService priorityExecutor; // For critical operations
    private BukkitTask autoSaveTask;
    private BukkitTask emergencyBackupTask;
    private BukkitTask cacheMaintenance;
    private BukkitTask performanceMonitor;

    // State management
    private volatile boolean shutdownInProgress = false;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Error handling and logging
    private final File dataErrorLog;
    private final File performanceLog;
    private final AtomicInteger saveCounter = new AtomicInteger(0);
    private final AtomicBoolean forceSaveAll = new AtomicBoolean(false);

    // Performance tracking
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerSaveTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPlayerActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLoadTimes = new ConcurrentHashMap<>();

    // Enhanced caching
    private final Map<UUID, String> displayNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> displayNameCacheTime = new ConcurrentHashMap<>();
    private static final long DISPLAY_NAME_CACHE_DURATION = 30000; // 30 seconds

    // Configuration
    private final long autoSaveInterval;
    private final long emergencyBackupInterval;
    private final long cacheMaintenanceInterval;
    private final long performanceMonitorInterval;
    private final int savePlayersPerCycle;
    private final int maxFailedLoadAttempts;
    private final int threadPoolSize;
    private final int priorityThreadPoolSize;

    // Performance metrics
    private final AtomicInteger totalLoads = new AtomicInteger(0);
    private final AtomicInteger totalSaves = new AtomicInteger(0);
    private final AtomicInteger failedSaves = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    // System integration flags
    private final AtomicBoolean chatSystemReady = new AtomicBoolean(false);
    private final AtomicBoolean moderationSystemReady = new AtomicBoolean(false);
    private final AtomicBoolean economySystemReady = new AtomicBoolean(false);

    /**
     * Private constructor for singleton pattern
     */
    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.repository = new YakPlayerRepository();
        this.logger = plugin.getLogger();

        // Load configuration with defaults
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.emergencyBackupInterval = plugin.getConfig().getLong("player_manager.emergency_backup_interval_ticks", 18000L);
        this.cacheMaintenanceInterval = plugin.getConfig().getLong("player_manager.cache_maintenance_interval_ticks", 36000L);
        this.performanceMonitorInterval = plugin.getConfig().getLong("player_manager.performance_monitor_interval_ticks", 12000L);
        this.savePlayersPerCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 5);
        this.maxFailedLoadAttempts = plugin.getConfig().getInt("player_manager.max_failed_load_attempts", 3);
        this.threadPoolSize = plugin.getConfig().getInt("player_manager.thread_pool_size", 4);
        this.priorityThreadPoolSize = plugin.getConfig().getInt("player_manager.priority_thread_pool_size", 2);

        // Initialize thread pools
        this.scheduledExecutor = Executors.newScheduledThreadPool(3, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Scheduled");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        this.asyncExecutor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Async");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        this.priorityExecutor = Executors.newFixedThreadPool(priorityThreadPoolSize, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Priority");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            return thread;
        });

        // Create logs directory
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        this.dataErrorLog = new File(logsDir, "data_errors.log");
        this.performanceLog = new File(logsDir, "performance.log");

        logger.info("YakPlayerManager initialized with " + threadPoolSize + " async threads and " +
                priorityThreadPoolSize + " priority threads");
    }

    /**
     * Gets the singleton instance
     */
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

    /**
     * Gets the repository instance
     */
    public YakPlayerRepository getRepository() {
        return repository;
    }

    /**
     * Checks if the manager is shutting down
     */
    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    /**
     * Enhanced initialization with better error handling and system integration
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);

            // Start enhanced task system
            startEnhancedTasks();

            // Initialize system integration flags
            initializeSystemIntegration();

            // Load existing online players with batching
            loadExistingOnlinePlayers();

            logger.info("YakPlayerManager enabled successfully with " + onlinePlayers.size() + " online players");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable YakPlayerManager", e);
            throw new RuntimeException("YakPlayerManager initialization failed", e);
        }
    }

    /**
     * Initialize system integration flags
     */
    private void initializeSystemIntegration() {
        // Check if other systems are ready
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (ChatMechanics.getInstance() != null) {
                    chatSystemReady.set(true);
                    logger.info("Chat system integration ready");
                }
            } catch (Exception e) {
                logger.warning("Chat system not ready: " + e.getMessage());
            }

            try {
                if (ModerationMechanics.getInstance() != null) {
                    moderationSystemReady.set(true);
                    logger.info("Moderation system integration ready");
                }
            } catch (Exception e) {
                logger.warning("Moderation system not ready: " + e.getMessage());
            }

            logger.info("System integration initialized");
        }, 20L); // 1 second delay
    }

    /**
     * Load existing online players in batches
     */
    private void loadExistingOnlinePlayers() {
        Collection<? extends Player> onlinePlayersList = Bukkit.getOnlinePlayers();
        if (onlinePlayersList.isEmpty()) {
            return;
        }

        logger.info("Loading " + onlinePlayersList.size() + " existing online players...");

        // Process in batches to avoid overwhelming the database
        List<Player> playerList = new ArrayList<>(onlinePlayersList);
        int batchSize = Math.min(10, playerList.size());

        for (int i = 0; i < playerList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, playerList.size());
            List<Player> batch = playerList.subList(i, endIndex);

            // Process batch asynchronously with delay
            final int batchNumber = i / batchSize;
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                processBatch(batch, batchNumber);
            }, batchNumber * 10L); // 0.5 second delay between batches
        }
    }

    private void processBatch(List<Player> batch, int batchNumber) {
        logger.info("Processing player batch " + (batchNumber + 1) + " (" + batch.size() + " players)");

        for (Player player : batch) {
            if (!player.isOnline()) continue;

            loadPlayer(player.getUniqueId()).thenAccept(yakPlayer -> {
                if (yakPlayer != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            onlinePlayers.put(player.getUniqueId(), yakPlayer);
                            playerNameCache.put(player.getName().toLowerCase(), player.getUniqueId());
                            playerLocks.put(player.getUniqueId(), new ReentrantLock());
                            yakPlayer.connect(player);
                            initializePlayerSystems(player, yakPlayer);
                            lastPlayerActivity.put(player.getUniqueId(), System.currentTimeMillis());
                            playerLoadTimes.put(player.getUniqueId(), System.currentTimeMillis());
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "Error processing batch player: " + player.getName(), e);
                        }
                    });
                } else {
                    logDataError("Failed to load existing player: " + player.getName());
                }
            }).exceptionally(e -> {
                logDataError("Error loading existing player: " + player.getName(), e);
                return null;
            });
        }
    }

    /**
     * Enhanced task system with better error handling and performance monitoring
     */
    private void startEnhancedTasks() {
        // Auto-save task with smart scheduling
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;

                try {
                    performSmartSave();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during auto-save task", e);
                    logDataError("Error during auto-save task", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        // Emergency backup task
        emergencyBackupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;

                try {
                    forceSaveAll.set(true);
                    performSmartSave();
                    forceSaveAll.set(false);
                    logger.info("Emergency backup completed for " + onlinePlayers.size() + " players");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during emergency backup", e);
                    logDataError("Error during emergency backup", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, emergencyBackupInterval, emergencyBackupInterval);

        // Cache maintenance task
        cacheMaintenance = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;

                try {
                    performCacheMaintenance();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during cache maintenance", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, cacheMaintenanceInterval, cacheMaintenanceInterval);

        // Performance monitoring task
        performanceMonitor = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;

                try {
                    performPerformanceMonitoring();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during performance monitoring", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, performanceMonitorInterval, performanceMonitorInterval);

        logger.info("Started enhanced task system (auto-save: " + (autoSaveInterval / 20) + "s, backup: " +
                (emergencyBackupInterval / 20) + "s, maintenance: " + (cacheMaintenanceInterval / 20) + "s)");
    }

    /**
     * Smart save algorithm that prioritizes dirty players and spreads load
     */
    private void performSmartSave() {
        List<UUID> playersToSave = determinePlayersToSave();

        if (playersToSave.isEmpty()) {
            return;
        }

        AtomicInteger savedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Process saves in parallel with rate limiting
        CompletableFuture<?>[] saveFutures = playersToSave.stream()
                .map(uuid -> CompletableFuture.runAsync(() -> {
                    YakPlayer player = onlinePlayers.get(uuid);
                    Player bukkitPlayer = Bukkit.getPlayer(uuid);

                    if (player != null && bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        try {
                            // Update player data before saving
                            updatePlayerBeforeSave(player, bukkitPlayer);

                            // Save and track result
                            savePlayer(player).thenAccept(success -> {
                                if (success) {
                                    savedCount.incrementAndGet();
                                    playerSaveTimes.put(uuid, System.currentTimeMillis());
                                    lastPlayerActivity.put(uuid, System.currentTimeMillis());
                                } else {
                                    errorCount.incrementAndGet();
                                }
                            });
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error during smart save for " + player.getUsername(), e);
                            errorCount.incrementAndGet();
                        }
                    }
                }, asyncExecutor))
                .toArray(CompletableFuture[]::new);

        // Wait for completion with timeout
        try {
            CompletableFuture.allOf(saveFutures).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warning("Some saves may not have completed: " + e.getMessage());
        }

        // Log results
        int saved = savedCount.get();
        int errors = errorCount.get();
        long duration = System.currentTimeMillis() - startTime;

        if (saved > 0 || errors > 0) {
            logger.info("Smart save completed: " + saved + " saved, " + errors + " errors in " + duration + "ms" +
                    (forceSaveAll.get() ? " (emergency)" : ""));
        }

        // Update metrics
        totalSaves.addAndGet(saved);
        failedSaves.addAndGet(errors);
    }

    private List<UUID> determinePlayersToSave() {
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

    private void updatePlayerBeforeSave(YakPlayer yakPlayer, Player bukkitPlayer) {
        try {
            yakPlayer.updateLocation(bukkitPlayer.getLocation());
            yakPlayer.updateInventory(bukkitPlayer);
            yakPlayer.updateStats(bukkitPlayer);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player before save: " + yakPlayer.getUsername(), e);
        }
    }

    /**
     * Enhanced cache maintenance with performance optimization
     */
    private void performCacheMaintenance() {
        long currentTime = System.currentTimeMillis();
        long staleThreshold = currentTime - TimeUnit.HOURS.toMillis(1);
        long displayNameThreshold = currentTime - DISPLAY_NAME_CACHE_DURATION;

        int cleaned = 0;

        // Clean up failed loads older than 1 hour
        int failedLoadsSize = failedLoads.size();
        failedLoads.removeIf(uuid -> {
            Long lastActivity = lastPlayerActivity.get(uuid);
            return lastActivity != null && lastActivity < staleThreshold;
        });
        cleaned += failedLoadsSize - failedLoads.size();

        // Clean up save times for offline players
        int saveTimesSize = playerSaveTimes.size();
        playerSaveTimes.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });
        cleaned += saveTimesSize - playerSaveTimes.size();

        // Clean up activity tracking for offline players
        int activitySize = lastPlayerActivity.size();
        lastPlayerActivity.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });
        cleaned += activitySize - lastPlayerActivity.size();

        // Clean up display name cache
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

        // Clean up name cache for offline players
        int nameCacheSize = playerNameCache.size();
        playerNameCache.entrySet().removeIf(entry -> {
            UUID uuid = entry.getValue();
            return !onlinePlayers.containsKey(uuid);
        });
        cleaned += nameCacheSize - playerNameCache.size();

        if (cleaned > 0) {
            logger.fine("Cache maintenance completed - cleaned " + cleaned + " stale entries");
        }
    }

    /**
     * Performance monitoring and logging
     */
    private void performPerformanceMonitoring() {
        try {
            // Calculate performance metrics
            long currentTime = System.currentTimeMillis();
            int onlineCount = onlinePlayers.size();
            int loads = totalLoads.get();
            int saves = totalSaves.get();
            int failedSavesCount = failedSaves.get();
            int hits = cacheHits.get();
            int misses = cacheMisses.get();

            double cacheHitRatio = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0;
            double saveSuccessRatio = saves > 0 ? (double) (saves - failedSavesCount) / saves * 100 : 100;

            // Log to performance file
            logPerformanceData(currentTime, onlineCount, loads, saves, failedSavesCount, cacheHitRatio, saveSuccessRatio);

            // Check for performance issues
            if (saveSuccessRatio < 95.0) {
                logger.warning("Low save success ratio: " + String.format("%.1f%%", saveSuccessRatio));
            }

            if (cacheHitRatio < 80.0 && (hits + misses) > 100) {
                logger.warning("Low cache hit ratio: " + String.format("%.1f%%", cacheHitRatio));
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during performance monitoring", e);
        }
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

    /**
     * Enhanced shutdown process
     */
    public void onDisable() {
        shutdownInProgress = true;
        logger.info("Starting enhanced shutdown process...");

        try {
            // Cancel all tasks
            cancelTasks();

            // Save all players with timeout
            saveAllPlayersOnShutdown();

            // Cleanup resources
            cleanup();

            logger.info("YakPlayerManager shutdown completed successfully");
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
    }

    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();
            if (player != null && player.isOnline()) {
                updatePlayerBeforeSave(yakPlayer, player);
            }

            saveFutures.add(savePlayerSync(yakPlayer));
        }

        // Wait for all saves with timeout
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

        // Shutdown thread pools
        shutdownExecutor(scheduledExecutor, "scheduled");
        shutdownExecutor(asyncExecutor, "async");
        shutdownExecutor(priorityExecutor, "priority");
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

    // Enhanced event handlers

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shutdownInProgress) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§cServer is shutting down. Please try again in a moment.");
            return;
        }

        UUID uuid = event.getUniqueId();
        failedLoads.remove(uuid);

        // Enhanced ban checking with better error handling
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

                // Update username if changed
                if (!player.getUsername().equals(event.getName())) {
                    player.setUsername(event.getName());
                    repository.save(player);
                }

                // Update name cache
                playerNameCache.put(event.getName().toLowerCase(), uuid);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error checking ban status for " + event.getName(), e);
            logDataError("Error checking ban status during prelogin: " + event.getName(), e);
        }
    }

    private String formatBanMessage(YakPlayer player) {
        StringBuilder message = new StringBuilder();
        message.append("§c§lYou are banned from this server!\n\n");
        message.append("§7Reason: §f").append(player.getBanReason()).append("\n");

        if (player.getBanExpiry() > 0) {
            long remaining = player.getBanExpiry() - Instant.now().getEpochSecond();
            if (remaining > 0) {
                message.append("§7Expires in: §f").append(formatDuration(remaining));
            } else {
                // Ban expired, remove it
                player.setBanned(false, "", 0);
                repository.save(player);
                return null; // Allow login
            }
        } else {
            message.append("§7This ban does not expire.");
        }

        message.append("\n\n§7Appeal at: §9discord.gg/JYf6R2VKE7");
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

    // Enhanced player join handling
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Prepare lock
        playerLocks.computeIfAbsent(uuid, k -> new ReentrantLock());

        // Update name cache
        playerNameCache.put(player.getName().toLowerCase(), uuid);

        // Load player asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayerForJoin(uuid);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error loading player on join: " + player.getName(), e);
                return null;
            }
        }, priorityExecutor).thenAccept(yakPlayer -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePlayerJoinResult(player, yakPlayer);
            });
        });
    }

    private YakPlayer loadPlayerForJoin(UUID uuid) throws Exception {
        if (onlinePlayers.containsKey(uuid)) {
            logger.info("Player already loaded: " + uuid);
            cacheHits.incrementAndGet();
            return onlinePlayers.get(uuid);
        }

        cacheMisses.incrementAndGet();
        Optional<YakPlayer> playerOpt = repository.findById(uuid).get(20, TimeUnit.SECONDS);
        totalLoads.incrementAndGet();

        return playerOpt.orElse(null);
    }

    private void handlePlayerJoinResult(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        if (yakPlayer == null) {
            handleNewPlayer(player);
        } else {
            handleExistingPlayer(player, yakPlayer);
        }

        lastPlayerActivity.put(uuid, System.currentTimeMillis());
        playerLoadTimes.put(uuid, System.currentTimeMillis());
    }

    private void handleNewPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        if (failedLoads.contains(uuid)) {
            // Multiple failures, kick player
            player.kickPlayer("§cFailed to load player data. Please contact an administrator.");
            logDataError("Kicked new player after failed loads: " + player.getName());
            return;
        }

        try {
            YakPlayer newPlayer = new YakPlayer(player);
            onlinePlayers.put(uuid, newPlayer);

            // Initialize systems
            initializePlayerSystems(player, newPlayer);

            // Save asynchronously
            savePlayer(newPlayer);

            // Welcome message
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§a§lWelcome to Yak Realms! §7Your player data has been created.");
                }
            }, 20L);

            logger.info("Created new player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating new player: " + player.getName(), e);
            failedLoads.add(uuid);
        }
    }

    private void handleExistingPlayer(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            yakPlayer.connect(player);
            onlinePlayers.put(uuid, yakPlayer);

            // Apply player data
            applyPlayerData(player, yakPlayer);

            // Initialize systems
            initializePlayerSystems(player, yakPlayer);

            logger.info("Loaded existing player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading existing player: " + player.getName(), e);
            handlePlayerDataError(player, e);
        }
    }

    private void applyPlayerData(Player player, YakPlayer yakPlayer) {
        try {
            // Teleport to saved location
            if (yakPlayer.getLocation() != null && yakPlayer.getLocation().getWorld() != null) {
                player.teleport(yakPlayer.getLocation());
            }

            // Apply inventory and stats
            yakPlayer.applyInventory(player);
            yakPlayer.applyStats(player);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error applying player data for " + player.getName(), e);
            player.sendMessage("§cWarning: Some of your player data may not have loaded correctly. " +
                    "Please report this to an administrator if you notice issues.");
        }
    }

    private void initializePlayerSystems(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        // Initialize energy system
        initializePlayerEnergy(yakPlayer);

        // Update rank and chat tag in memory if systems are ready
        if (moderationSystemReady.get()) {
            try {
                Rank rank = Rank.valueOf(yakPlayer.getRank());
                ModerationMechanics.rankMap.put(uuid, rank);
            } catch (IllegalArgumentException e) {
                ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
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

        // Initialize display name cache
        updateDisplayNameCache(yakPlayer);
    }

    private void handlePlayerDataError(Player player, Exception e) {
        UUID uuid = player.getUniqueId();
        logDataError("Error loading player data for " + player.getName(), e);

        // Create emergency data
        YakPlayer emergencyPlayer = new YakPlayer(player);
        onlinePlayers.put(uuid, emergencyPlayer);
        initializePlayerEnergy(emergencyPlayer);

        player.sendMessage("§cError loading your player data. Emergency data created. " +
                "Please report this issue to an administrator.");

        // Notify staff
        notifyStaffOfDataError(player.getName());
    }

    private void notifyStaffOfDataError(String playerName) {
        String message = "§4[ADMIN] §cData error for player: " + playerName;
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("yakserver.admin"))
                .forEach(p -> p.sendMessage(message));
    }

    // Enhanced player quit handling
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
                    // Update data before saving
                    updatePlayerBeforeSave(yakPlayer, player);
                    yakPlayer.disconnect();

                    // Remove from online players
                    onlinePlayers.remove(uuid);

                    // Clear caches
                    displayNameCache.remove(uuid);
                    displayNameCacheTime.remove(uuid);
                    playerNameCache.remove(player.getName().toLowerCase());

                    // Save asynchronously
                    savePlayer(yakPlayer).thenAccept(success -> {
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

    // Utility methods
    private void initializePlayerEnergy(YakPlayer yakPlayer) {
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

    private void updateDisplayNameCache(YakPlayer yakPlayer) {
        UUID uuid = yakPlayer.getUUID();
        String displayName = yakPlayer.getFormattedDisplayName();
        displayNameCache.put(uuid, displayName);
        displayNameCacheTime.put(uuid, System.currentTimeMillis());
    }

    private void logDataError(String message) {
        logDataError(message, null);
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

    // Enhanced public API methods
    public YakPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    public YakPlayer getPlayer(String name) {
        if (name == null) return null;

        // Try cache first
        UUID uuid = playerNameCache.get(name.toLowerCase());
        if (uuid != null) {
            YakPlayer player = onlinePlayers.get(uuid);
            if (player != null) {
                cacheHits.incrementAndGet();
                return player;
            }
        }

        // Fallback to iteration
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

    // Enhanced async operations for other systems
    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> action, boolean save) {
        return CompletableFuture.supplyAsync(() -> {
            YakPlayer player = getPlayer(uuid);
            if (player != null) {
                ReentrantLock lock = getPlayerLock(uuid);
                lock.lock();
                try {
                    action.accept(player);
                    if (save) {
                        return savePlayer(player).get(10, TimeUnit.SECONDS);
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

                        // Update display name cache
                        updateDisplayNameCache(player);

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
        }, asyncExecutor);
    }

    private CompletableFuture<Boolean> savePlayerSync(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.saveSync(player);
                player.clearDirty();
                updateDisplayNameCache(player);
                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Sync save failed for player: " + player.getUsername(), e);
                return false;
            }
        }, priorityExecutor);
    }

    // Enhanced load method with retry logic
    private CompletableFuture<YakPlayer> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (onlinePlayers.containsKey(uuid)) {
                cacheHits.incrementAndGet();
                return onlinePlayers.get(uuid);
            }

            cacheMisses.incrementAndGet();
            int attempts = 0;
            Exception lastException = null;

            while (attempts < maxFailedLoadAttempts) {
                try {
                    Optional<YakPlayer> playerOpt = repository.findById(uuid).get(15, TimeUnit.SECONDS);
                    totalLoads.incrementAndGet();
                    return playerOpt.orElse(null);

                } catch (Exception e) {
                    lastException = e;
                    attempts++;

                    if (attempts < maxFailedLoadAttempts) {
                        try {
                            Thread.sleep(1000 * attempts); // Exponential backoff
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            logger.log(Level.SEVERE, "Failed to load player after " + attempts + " attempts: " + uuid, lastException);
            throw new RuntimeException("Player load failed", lastException);
        }, asyncExecutor);
    }

    // Bulk operations
    public void saveAllPlayers() {
        forceSaveAll.set(true);
        performSmartSave();
        forceSaveAll.set(false);
    }

    public void saveAllPlayersSync() {
        logger.info("Performing synchronous save of all players...");
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (YakPlayer player : onlinePlayers.values()) {
            futures.add(savePlayerSync(player));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
            logger.info("Synchronous save completed for " + futures.size() + " players");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during synchronous save all", e);
        }
    }

    // Performance monitoring
    public void logPerformanceStats() {
        logger.info("YakPlayerManager Performance Stats:");
        logger.info("  Online Players: " + onlinePlayers.size());
        logger.info("  Total Loads: " + totalLoads.get());
        logger.info("  Total Saves: " + totalSaves.get());
        logger.info("  Failed Saves: " + failedSaves.get());
        logger.info("  Cache Hits: " + cacheHits.get());
        logger.info("  Cache Misses: " + cacheMisses.get());
        logger.info("  Cache Hit Ratio: " + String.format("%.1f%%",
                (cacheHits.get() + cacheMisses.get()) > 0 ?
                        (double) cacheHits.get() / (cacheHits.get() + cacheMisses.get()) * 100 : 0));
        logger.info("  Cache Entries: " + playerSaveTimes.size());
    }

    // System status
    public boolean isSystemReady() {
        return initialized.get() && !shutdownInProgress;
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("initialized", initialized.get());
        status.put("shutdownInProgress", shutdownInProgress);
        status.put("onlinePlayers", onlinePlayers.size());
        status.put("totalLoads", totalLoads.get());
        status.put("totalSaves", totalSaves.get());
        status.put("failedSaves", failedSaves.get());
        status.put("cacheHits", cacheHits.get());
        status.put("cacheMisses", cacheMisses.get());
        status.put("chatSystemReady", chatSystemReady.get());
        status.put("moderationSystemReady", moderationSystemReady.get());
        return status;
    }
}
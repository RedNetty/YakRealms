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
 * better error handling, and optimized database operations.
 */
public class YakPlayerManager implements Listener {
    private static YakPlayerManager instance;
    private final YakPlayerRepository repository;
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Logger logger;
    private final Plugin plugin;

    // Enhanced task management
    private final ScheduledExecutorService scheduledExecutor;
    private final ExecutorService asyncExecutor;
    private BukkitTask autoSaveTask;
    private BukkitTask emergencyBackupTask;
    private BukkitTask cacheMaintenance;

    // State management
    private volatile boolean shutdownInProgress = false;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Error handling and logging
    private final File dataErrorLog;
    private final AtomicInteger saveCounter = new AtomicInteger(0);
    private final AtomicBoolean forceSaveAll = new AtomicBoolean(false);

    // Performance tracking
    private final Set<UUID> failedLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerSaveTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPlayerActivity = new ConcurrentHashMap<>();

    // Configuration
    private final long autoSaveInterval;
    private final long emergencyBackupInterval;
    private final long cacheMaintenanceInterval;
    private final int savePlayersPerCycle;
    private final int maxFailedLoadAttempts;
    private final int threadPoolSize;

    // Performance metrics
    private final AtomicInteger totalLoads = new AtomicInteger(0);
    private final AtomicInteger totalSaves = new AtomicInteger(0);
    private final AtomicInteger failedSaves = new AtomicInteger(0);

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
        this.savePlayersPerCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 5);
        this.maxFailedLoadAttempts = plugin.getConfig().getInt("player_manager.max_failed_load_attempts", 3);
        this.threadPoolSize = plugin.getConfig().getInt("player_manager.thread_pool_size", 4);

        // Initialize thread pools
        this.scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Scheduled");
            thread.setDaemon(true);
            return thread;
        });

        this.asyncExecutor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Async");
            thread.setDaemon(true);
            return thread;
        });

        // Create logs directory
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        this.dataErrorLog = new File(logsDir, "data_errors.log");

        logger.info("YakPlayerManager initialized with " + threadPoolSize + " async threads");
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
     * Enhanced initialization with better error handling
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

            // Load existing online players with batching
            loadExistingOnlinePlayers();

            logger.info("YakPlayerManager enabled successfully with " + onlinePlayers.size() + " online players");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable YakPlayerManager", e);
            throw new RuntimeException("YakPlayerManager initialization failed", e);
        }
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
                        onlinePlayers.put(player.getUniqueId(), yakPlayer);
                        playerLocks.put(player.getUniqueId(), new ReentrantLock());
                        yakPlayer.connect(player);
                        initializePlayerEnergy(yakPlayer);
                        lastPlayerActivity.put(player.getUniqueId(), System.currentTimeMillis());
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
     * Enhanced task system with better error handling
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
        if (saved > 0 || errors > 0) {
            logger.info("Smart save completed: " + saved + " saved, " + errors + " errors" +
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
     * Cache maintenance to clean up stale data
     */
    private void performCacheMaintenance() {
        long currentTime = System.currentTimeMillis();
        long staleThreshold = currentTime - TimeUnit.HOURS.toMillis(1);

        // Clean up failed loads older than 1 hour
        failedLoads.removeIf(uuid -> {
            Long lastActivity = lastPlayerActivity.get(uuid);
            return lastActivity != null && lastActivity < staleThreshold;
        });

        // Clean up save times for offline players
        playerSaveTimes.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });

        // Clean up activity tracking for offline players
        lastPlayerActivity.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });

        logger.fine("Cache maintenance completed - cleaned stale entries");
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
        playerLocks.clear();
        failedLoads.clear();
        playerSaveTimes.clear();
        lastPlayerActivity.clear();

        // Shutdown thread pools
        shutdownExecutor(scheduledExecutor, "scheduled");
        shutdownExecutor(asyncExecutor, "async");
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

    // Continue with event handlers and other methods...

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
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage);
                    return;
                }

                // Update username if changed
                if (!player.getUsername().equals(event.getName())) {
                    player.setUsername(event.getName());
                    repository.save(player);
                }
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

        // Load player asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                return loadPlayerForJoin(uuid);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error loading player on join: " + player.getName(), e);
                return null;
            }
        }, asyncExecutor).thenAccept(yakPlayer -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePlayerJoinResult(player, yakPlayer);
            });
        });
    }

    private YakPlayer loadPlayerForJoin(UUID uuid) throws Exception {
        if (onlinePlayers.containsKey(uuid)) {
            logger.info("Player already loaded: " + uuid);
            return onlinePlayers.get(uuid);
        }

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
            ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
            ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
            initializePlayerEnergy(newPlayer);

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

        // Update rank and chat tag in memory
        try {
            Rank rank = Rank.valueOf(yakPlayer.getRank());
            ModerationMechanics.rankMap.put(uuid, rank);
        } catch (IllegalArgumentException e) {
            ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
        }

        try {
            ChatTag tag = ChatTag.valueOf(yakPlayer.getChatTag());
            ChatMechanics.getPlayerTags().put(uuid, tag);
        } catch (IllegalArgumentException e) {
            ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
        }
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

    // Public API methods
    public YakPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    public YakPlayer getPlayer(String name) {
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

    private CompletableFuture<Boolean> savePlayerSync(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.saveSync(player);
                player.clearDirty();
                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Sync save failed for player: " + player.getUsername(), e);
                return false;
            }
        });
    }

    // Performance monitoring
    public void logPerformanceStats() {
        logger.info("YakPlayerManager Performance Stats:");
        logger.info("  Online Players: " + onlinePlayers.size());
        logger.info("  Total Loads: " + totalLoads.get());
        logger.info("  Total Saves: " + totalSaves.get());
        logger.info("  Failed Saves: " + failedSaves.get());
        logger.info("  Cache Entries: " + playerSaveTimes.size());
    }

    // Enhanced load method with retry logic
    private CompletableFuture<YakPlayer> loadPlayer(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (onlinePlayers.containsKey(uuid)) {
                return onlinePlayers.get(uuid);
            }

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

    public void saveAllPlayers() {
        onlinePlayers.values().forEach(this::savePlayer);
    }
}
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
 * Fixed YakPlayer manager with proper data loading and saving
 */
public class YakPlayerManager implements Listener {
    private static YakPlayerManager instance;
    private final YakPlayerRepository repository;
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<YakPlayer>> loadingPlayers = new ConcurrentHashMap<>();
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

    // Event system for other listeners to hook into
    private final Set<PlayerLoadListener> loadListeners = ConcurrentHashMap.newKeySet();
    private final Set<PlayerSaveListener> saveListeners = ConcurrentHashMap.newKeySet();

    // Enhanced configuration
    private volatile long autoSaveInterval;
    private volatile int savePlayersPerCycle;
    private final int threadPoolSize;

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

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.repository = new YakPlayerRepository();
        this.logger = plugin.getLogger();

        // Load configuration
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.savePlayersPerCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 8);
        this.threadPoolSize = plugin.getConfig().getInt("player_manager.thread_pool_size", 6);

        // Initialize thread pools
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

        this.priorityExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Priority-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1);
            thread.setUncaughtExceptionHandler((t, e) -> logger.log(Level.SEVERE, "Uncaught exception in priority thread", e));
            return thread;
        });

        this.ioExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO-" + Thread.currentThread().getId());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            thread.setUncaughtExceptionHandler((t, e) -> logger.log(Level.SEVERE, "Uncaught exception in IO thread", e));
            return thread;
        });

        // Create logs directory
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        this.dataErrorLog = new File(logsDir, "player_data_errors.log");
        this.performanceLog = new File(logsDir, "player_performance.log");
        this.auditLog = new File(logsDir, "player_audit.log");

        logger.info("YakPlayerManager initialized with " + threadPoolSize + " threads");
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

    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting YakPlayerManager initialization...");

            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);

            // Initialize repository
            if (!initializeRepository()) {
                throw new RuntimeException("Failed to initialize repository");
            }

            // Start tasks
            startTasks();

            // Load existing online players
            loadExistingOnlinePlayers();

            logger.info("YakPlayerManager enabled successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable YakPlayerManager", e);
            throw new RuntimeException("YakPlayerManager initialization failed", e);
        }
    }

    private boolean initializeRepository() {
        try {
            // Repository is already initialized in constructor
            logger.info("Repository initialized successfully");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing repository", e);
            return false;
        }
    }

    private void startTasks() {
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                performAutoSave();
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        emergencyBackupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                performEmergencyBackup();
            }
        }.runTaskTimerAsynchronously(plugin, 18000L, 18000L);

        cacheMaintenance = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                performCacheMaintenance();
            }
        }.runTaskTimerAsynchronously(plugin, 36000L, 36000L);

        performanceMonitor = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;
                logPerformanceStats();
            }
        }.runTaskTimerAsynchronously(plugin, 12000L, 12000L);

        logger.info("All tasks started successfully");
    }

    /**
     * Handle pre-login for ban checks
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shutdownInProgress) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "Server is shutting down. Please try again in a moment.");
            return;
        }

        UUID uuid = event.getUniqueId();

        try {
            // Check if player is banned
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
                    repository.saveSync(player);
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking ban status for " + event.getName(), e);
        }
    }

    /**
     * FIXED: Handle player join with proper data loading
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        logger.info("Player joining: " + playerName + " (" + uuid + ")");

        // Prevent duplicate loading
        if (loadingPlayers.containsKey(uuid)) {
            logger.warning("Player " + playerName + " is already being loaded!");
            return;
        }

        // Check if already loaded
        if (onlinePlayers.containsKey(uuid)) {
            logger.info("Player " + playerName + " already loaded in memory");
            YakPlayer yakPlayer = onlinePlayers.get(uuid);
            yakPlayer.connect(player);
            notifyLoadListeners(player, yakPlayer, false);
            return;
        }

        // Create loading future
        CompletableFuture<YakPlayer> loadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Loading player data for " + playerName);

                // Try to load from database
                Optional<YakPlayer> existingPlayer = repository.findById(uuid).get(20, TimeUnit.SECONDS);

                if (existingPlayer.isPresent()) {
                    logger.info("Found existing player data for " + playerName);
                    YakPlayer yakPlayer = existingPlayer.get();
                    yakPlayer.connect(player);
                    return yakPlayer;
                } else {
                    logger.info("Creating new player data for " + playerName);
                    // Create new player
                    YakPlayer newPlayer = new YakPlayer(player);

                    // Save to database immediately
                    repository.save(newPlayer).get(10, TimeUnit.SECONDS);
                    logger.info("Saved new player data for " + playerName);

                    return newPlayer;
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load/create player data for " + playerName, e);
                throw new RuntimeException("Failed to load player data", e);
            }
        }, ioExecutor);

        // Store loading future
        loadingPlayers.put(uuid, loadFuture);

        // Handle completion
        loadFuture.whenComplete((yakPlayer, error) -> {
            loadingPlayers.remove(uuid);

            if (error != null) {
                logger.log(Level.SEVERE, "Error loading player " + playerName, error);

                // Notify listeners of failure
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        notifyLoadFailure(player, new Exception(error));
                    }
                });
            } else if (yakPlayer != null) {
                // Success - register player
                onlinePlayers.put(uuid, yakPlayer);
                playerNameCache.put(playerName.toLowerCase(), uuid);
                playerLocks.put(uuid, new ReentrantLock());
                lastPlayerActivity.put(uuid, System.currentTimeMillis());

                logger.info("Successfully loaded player " + playerName);

                // Initialize player systems on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        initializePlayerSystems(player, yakPlayer);

                        // Notify listeners
                        boolean isNewPlayer = yakPlayer.getFirstJoin() == yakPlayer.getLastLogin();
                        notifyLoadListeners(player, yakPlayer, isNewPlayer);
                    }
                });
            }
        });
    }

    /**
     * Handle player quit with proper saving
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        logger.info("Player quitting: " + playerName);

        // Cancel any pending load
        CompletableFuture<YakPlayer> loadFuture = loadingPlayers.remove(uuid);
        if (loadFuture != null && !loadFuture.isDone()) {
            loadFuture.cancel(true);
        }

        // Get player data
        YakPlayer yakPlayer = onlinePlayers.remove(uuid);
        if (yakPlayer == null) {
            logger.warning("No player data found for quitting player: " + playerName);
            return;
        }

        // Update player data
        updatePlayerBeforeSave(yakPlayer, player);
        yakPlayer.disconnect();

        // Clean up caches
        playerNameCache.remove(playerName.toLowerCase());
        lastPlayerActivity.remove(uuid);

        // Save asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                repository.saveSync(yakPlayer);
                logger.info("Saved player data for " + playerName);

                // Notify save listeners
                for (PlayerSaveListener listener : saveListeners) {
                    try {
                        listener.onPlayerSaved(yakPlayer);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error in save listener", e);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player on quit: " + playerName, e);

                // Notify save failure
                for (PlayerSaveListener listener : saveListeners) {
                    try {
                        listener.onPlayerSaveFailed(yakPlayer, e);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Error in save listener", ex);
                    }
                }
            }
        }, ioExecutor);
    }

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
                ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
            }

            // Initialize chat tag
            try {
                ChatTag tag = ChatTag.valueOf(yakPlayer.getChatTag());
                ChatMechanics.getPlayerTags().put(uuid, tag);
            } catch (IllegalArgumentException e) {
                ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
            }

            logger.fine("Initialized systems for player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
        }
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

    private void loadExistingOnlinePlayers() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            return;
        }

        logger.info("Loading " + onlinePlayers.size() + " existing online players...");

        for (Player player : onlinePlayers) {
            // Simulate join event
            onPlayerJoin(new PlayerJoinEvent(player, null));
        }
    }

    private void performAutoSave() {
        List<UUID> playersToSave = new ArrayList<>(onlinePlayers.keySet());
        if (playersToSave.isEmpty()) {
            return;
        }

        int saved = 0;
        int errors = 0;

        for (UUID uuid : playersToSave) {
            YakPlayer player = onlinePlayers.get(uuid);
            Player bukkitPlayer = Bukkit.getPlayer(uuid);

            if (player != null && bukkitPlayer != null && bukkitPlayer.isOnline()) {
                try {
                    updatePlayerBeforeSave(player, bukkitPlayer);
                    repository.saveSync(player);
                    playerSaveTimes.put(uuid, System.currentTimeMillis());
                    saved++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error during auto-save for " + player.getUsername(), e);
                    errors++;
                }
            }
        }

        if (saved > 0 || errors > 0) {
            logger.info("Auto-save completed: " + saved + " saved, " + errors + " errors");
        }
    }

    private void performEmergencyBackup() {
        forceSaveAll.set(true);
        performAutoSave();
        forceSaveAll.set(false);
        logger.info("Emergency backup completed");
    }

    private void performCacheMaintenance() {
        long currentTime = System.currentTimeMillis();
        long staleThreshold = currentTime - TimeUnit.HOURS.toMillis(1);

        // Clean up stale data
        failedLoads.clear();
        playerSaveTimes.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });
        lastPlayerActivity.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return !onlinePlayers.containsKey(uuid) && entry.getValue() < staleThreshold;
        });

        logger.fine("Cache maintenance completed");
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

    private void notifyLoadListeners(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        for (PlayerLoadListener listener : loadListeners) {
            try {
                listener.onPlayerLoaded(player, yakPlayer, isNewPlayer);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in load listener", e);
            }
        }
    }

    private void notifyLoadFailure(Player player, Exception exception) {
        for (PlayerLoadListener listener : loadListeners) {
            try {
                listener.onPlayerLoadFailed(player, exception);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error in load listener", e);
            }
        }
    }

    public void onDisable() {
        shutdownInProgress = true;
        logger.info("Starting shutdown process...");

        try {
            // Cancel tasks
            if (autoSaveTask != null) autoSaveTask.cancel();
            if (emergencyBackupTask != null) emergencyBackupTask.cancel();
            if (cacheMaintenance != null) cacheMaintenance.cancel();
            if (performanceMonitor != null) performanceMonitor.cancel();

            // Save all players
            saveAllPlayersOnShutdown();

            // Shutdown executors
            shutdownExecutor(scheduledExecutor, "scheduled");
            shutdownExecutor(asyncExecutor, "async");
            shutdownExecutor(priorityExecutor, "priority");
            shutdownExecutor(ioExecutor, "io");

            // Clear caches
            onlinePlayers.clear();
            playerNameCache.clear();
            playerLocks.clear();
            loadingPlayers.clear();

            logger.info("YakPlayerManager shutdown completed");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
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
            logger.warning("Shutdown save timed out");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown save", e);
        }
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

    // Public API methods
    public YakPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    public YakPlayer getPlayer(String name) {
        if (name == null) return null;

        UUID uuid = playerNameCache.get(name.toLowerCase());
        if (uuid != null) {
            return onlinePlayers.get(uuid);
        }

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

    public CompletableFuture<Boolean> savePlayer(YakPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                repository.saveSync(player);
                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving player: " + player.getUsername(), e);
                return false;
            }
        }, ioExecutor);
    }

    public void saveAllPlayers() {
        forceSaveAll.set(true);
        performAutoSave();
        forceSaveAll.set(false);
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
        return shutdownInProgress;
    }

    public boolean isSystemHealthy() {
        return systemHealthy.get();
    }

    public YakPlayerRepository getRepository() {
        return repository;
    }

    public void logPerformanceStats() {
        logger.info("YakPlayerManager Performance Stats:");
        logger.info("  Online Players: " + onlinePlayers.size());
        logger.info("  Loading Players: " + loadingPlayers.size());
        logger.info("  Name Cache Size: " + playerNameCache.size());
        logger.info("  System Health: " + (systemHealthy.get() ? "HEALTHY" : "DEGRADED"));
    }
}
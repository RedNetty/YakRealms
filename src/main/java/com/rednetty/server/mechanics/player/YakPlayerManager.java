package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import org.bukkit.Bukkit;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages YakPlayer instances and synchronizes with the repository
 * Enhanced with better data protection, staggered saving, and error recovery
 */
public class YakPlayerManager implements Listener {
    private static YakPlayerManager instance;
    private final YakPlayerRepository repository;
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Logger logger;
    private final Plugin plugin;
    private BukkitTask autoSaveTask;
    private BukkitTask emergencyBackupTask;
    private boolean shutdownInProgress = false;
    private final File dataErrorLog;
    private final AtomicInteger saveCounter = new AtomicInteger(0);
    private final AtomicBoolean forceSaveAll = new AtomicBoolean(false);
    private final Set<UUID> failedLoads = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> playerSaveTimes = new ConcurrentHashMap<>();

    // Settings
    private final long autoSaveInterval;
    private final long emergencyBackupInterval;
    private final int savePlayersPerCycle;
    private final int maxFailedLoadAttempts = 3;

    /**
     * Private constructor for singleton pattern
     */
    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.repository = new YakPlayerRepository();
        this.logger = plugin.getLogger();

        // Load settings from config or use defaults
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.emergencyBackupInterval = plugin.getConfig().getLong("player_manager.emergency_backup_interval_ticks", 18000L);
        this.savePlayersPerCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 5);

        // Create logs directory
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        // Initialize error log file
        this.dataErrorLog = new File(logsDir, "data_errors.log");
    }

    /**
     * Gets the singleton instance
     *
     * @return The YakPlayerManager instance
     */
    public static YakPlayerManager getInstance() {
        if (instance == null) {
            instance = new YakPlayerManager();
        }
        return instance;
    }

    /**
     * Gets the repository instance
     *
     * @return The YakPlayerRepository
     */
    public YakPlayerRepository getRepository() {
        return repository;
    }

    /**
     * Checks if the manager is shutting down
     *
     * @return true if shutting down
     */
    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    /**
     * Initializes the manager and registers events
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, plugin);

        // Start tasks
        startAutoSaveTask();
        startEmergencyBackupTask();

        // Register online players that might have joined before the manager was enabled
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId()).thenAccept(yakPlayer -> {
                if (yakPlayer != null) {
                    onlinePlayers.put(player.getUniqueId(), yakPlayer);
                    playerLocks.put(player.getUniqueId(), new ReentrantLock());
                    yakPlayer.connect(player);

                    // Initialize energy system for the player
                    initializePlayerEnergy(yakPlayer);
                } else {
                    logDataError("Failed to load player during onEnable: " + player.getName() + " (" + player.getUniqueId() + ")");
                }
            });
        }

        YakRealms.log("YakPlayerManager has been enabled.");
    }

    /**
     * Log a data error to the error log file
     *
     * @param message The error message
     */
    private void logDataError(String message) {
        logDataError(message, null);
    }

    /**
     * Log a data error to the error log file with exception
     *
     * @param message The error message
     * @param e       The exception
     */
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

    /**
     * Initialize energy system for a player
     *
     * @param yakPlayer The player to initialize
     */
    private void initializePlayerEnergy(YakPlayer yakPlayer) {
        Player player = yakPlayer.getBukkitPlayer();
        if (player != null && player.isOnline()) {
            // Set initial energy display
            player.setExp(1.0f);
            player.setLevel(100);

            // Initialize energy value if not already set
            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }
        }
    }

    /**
     * Starts the auto-save task with staggered saving
     */
    private void startAutoSaveTask() {
        // Cancel existing task if any
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }

        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;

                try {
                    performStaggeredSave();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during auto-save task", e);
                    logDataError("Error during auto-save task", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        logger.info("Started auto-save task (interval: " + (autoSaveInterval / 20) + " seconds)");
    }

    /**
     * Starts the emergency backup task
     */
    private void startEmergencyBackupTask() {
        // Cancel existing task if any
        if (emergencyBackupTask != null && !emergencyBackupTask.isCancelled()) {
            emergencyBackupTask.cancel();
        }

        emergencyBackupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shutdownInProgress) return;

                try {
                    // Force save all players for emergency backup
                    forceSaveAll.set(true);
                    performStaggeredSave();
                    forceSaveAll.set(false);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during emergency backup task", e);
                    logDataError("Error during emergency backup task", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, emergencyBackupInterval, emergencyBackupInterval);

        logger.info("Started emergency backup task (interval: " + (emergencyBackupInterval / 20) + " seconds)");
    }

    /**
     * Performs staggered saving to distribute database load
     */
    private void performStaggeredSave() {
        // Get online players for saving
        List<UUID> playersToSave = new ArrayList<>(onlinePlayers.keySet());

        // If forcing save all, save everyone
        // Otherwise, save based on last save time or a subset of players
        if (!forceSaveAll.get()) {
            if (playersToSave.size() <= savePlayersPerCycle) {
                // Save all if the count is small
            } else {
                final long currentTime = System.currentTimeMillis();
                final long threshold = currentTime - TimeUnit.MINUTES.toMillis(10); // Players not saved in 10 minutes

                // Filter players by last save time or take a subset
                List<UUID> priorityPlayers = playersToSave.stream()
                        .filter(id -> !playerSaveTimes.containsKey(id) || playerSaveTimes.get(id) < threshold)
                        .collect(Collectors.toList());

                if (!priorityPlayers.isEmpty()) {
                    // Save players that haven't been saved recently
                    playersToSave = priorityPlayers;
                    if (playersToSave.size() > savePlayersPerCycle) {
                        playersToSave = playersToSave.subList(0, savePlayersPerCycle);
                    }
                } else {
                    // Save a rotating subset of players
                    Collections.rotate(playersToSave, -saveCounter.getAndIncrement());
                    playersToSave = playersToSave.subList(0, Math.min(savePlayersPerCycle, playersToSave.size()));
                }
            }
        }

        int savedCount = 0;
        for (UUID uuid : playersToSave) {
            YakPlayer player = onlinePlayers.get(uuid);
            Player bukkitPlayer = Bukkit.getPlayer(uuid);

            if (player != null && bukkitPlayer != null && bukkitPlayer.isOnline()) {
                try {
                    // Update player data before saving
                    player.updateLocation(bukkitPlayer.getLocation());
                    player.updateInventory(bukkitPlayer);
                    player.updateStats(bukkitPlayer);

                    savePlayer(player).thenAccept(success -> {
                        if (success) {
                            playerSaveTimes.put(uuid, System.currentTimeMillis());
                        }
                    });

                    savedCount++;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error during auto-save for player " + player.getUsername(), e);
                    logDataError("Error during auto-save for player " + player.getUsername(), e);
                }
            }
        }

        if (savedCount > 0) {
            logger.info("Auto-saved " + savedCount + " players" + (forceSaveAll.get() ? " (emergency backup)" : ""));
        }
    }

    /**
     * Saves all online players to the database immediately
     */
    public void saveAllPlayers() {
        int savedCount = 0;
        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (YakPlayer player : onlinePlayers.values()) {
            Player bukkitPlayer = player.getBukkitPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                // Update player data before saving
                player.updateLocation(bukkitPlayer.getLocation());
                player.updateInventory(bukkitPlayer);
                player.updateStats(bukkitPlayer);
            }

            saveFutures.add(savePlayer(player));
            savedCount++;
        }

        // Wait for all saves to complete (with timeout)
        try {
            CompletableFuture.allOf(saveFutures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
            logger.info("Saved all online players (" + savedCount + ")");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Some player saves may not have completed", e);
        }
    }

    /**
     * Saves all players and performs cleanup on shutdown
     */
    public void onDisable() {
        shutdownInProgress = true;

        // Cancel tasks
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        if (emergencyBackupTask != null && !emergencyBackupTask.isCancelled()) {
            emergencyBackupTask.cancel();
            emergencyBackupTask = null;
        }

        logger.info("Saving players on shutdown...");

        // Save all players synchronously
        int savedCount = 0;
        int backupCount = 0;
        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();
            if (player != null && player.isOnline()) {
                // Update the player's state before saving
                yakPlayer.updateLocation(player.getLocation());
                yakPlayer.updateInventory(player);
                yakPlayer.updateStats(player);
            }

            try {
                // Use synchronous saving during shutdown
                repository.saveSync(yakPlayer);
                savedCount++;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player during shutdown: " + yakPlayer.getUsername(), e);
                logDataError("Failed to save player during shutdown: " + yakPlayer.getUsername(), e);
                backupCount++;
            }
        }

        onlinePlayers.clear();
        playerLocks.clear();

        logger.info("Shutdown complete: Saved " + savedCount + " players, created " + backupCount + " local backups");
        YakRealms.log("YakPlayerManager has been disabled.");
    }

    /**
     * Gets a YakPlayer by their UUID
     *
     * @param uuid The player's UUID
     * @return The YakPlayer or null if not found
     */
    public YakPlayer getPlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    /**
     * Gets a YakPlayer by their name
     *
     * @param name The player's name
     * @return The YakPlayer or null if not found
     */
    public YakPlayer getPlayer(String name) {
        for (YakPlayer player : onlinePlayers.values()) {
            if (player.getUsername().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Gets a YakPlayer by their Bukkit player instance
     *
     * @param player The Bukkit player
     * @return The YakPlayer or null if not found
     */
    public YakPlayer getPlayer(Player player) {
        return getPlayer(player.getUniqueId());
    }

    /**
     * Gets all online players
     *
     * @return A Collection of all online YakPlayers
     */
    public Collection<YakPlayer> getOnlinePlayers() {
        return new ArrayList<>(onlinePlayers.values());
    }

    /**
     * Acquires a lock for a player
     *
     * @param uuid The player's UUID
     * @return The lock for the player
     */
    public ReentrantLock getPlayerLock(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, id -> new ReentrantLock());
    }

    /**
     * Handles player prelogin to check if they're banned
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        // Reject connections during shutdown
        if (shutdownInProgress) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Server is shutting down");
            return;
        }

        UUID uuid = event.getUniqueId();

        // Remove any previous failed load records
        failedLoads.remove(uuid);

        try {
            Optional<YakPlayer> playerOpt = repository.findById(uuid).get(10, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                // Check if the player is banned
                if (player.isBanned()) {
                    String message = "You are banned from this server.\nReason: " + player.getBanReason();

                    // Add expiry info for temporary bans
                    if (player.getBanExpiry() > 0) {
                        long remaining = player.getBanExpiry() - System.currentTimeMillis() / 1000;
                        if (remaining > 0) {
                            long days = remaining / 86400;
                            long hours = (remaining % 86400) / 3600;
                            long minutes = (remaining % 3600) / 60;

                            message += "\nExpires in: ";
                            if (days > 0) message += days + " days, ";
                            if (hours > 0) message += hours + " hours, ";
                            message += minutes + " minutes";
                        } else {
                            // Ban has expired
                            player.setBanned(false, "", 0);
                            repository.save(player);
                        }
                    } else {
                        message += "\nThis ban will not expire.";
                    }

                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, message);
                }

                // Update player name if it changed
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

    /**
     * Handles player login to track connection attempts
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();

        // Prepare a lock for this player
        if (!playerLocks.containsKey(uuid)) {
            playerLocks.put(uuid, new ReentrantLock());
        }
    }

    /**
     * Handles player join to load or create player data
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Acquire lock for this player
        ReentrantLock lock = getPlayerLock(uuid);

        // Run this in an async task to not block the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            lock.lock();
            try {
                // Check if the player is already loaded
                if (onlinePlayers.containsKey(uuid)) {
                    logger.info("Player " + player.getName() + " already loaded, skipping redundant load");
                    return;
                }

                loadPlayer(uuid).thenAccept(yakPlayer -> {
                    if (yakPlayer == null) {
                        // Player data doesn't exist, create a new player
                        if (!failedLoads.contains(uuid)) {
                            // This is a new player, create data
                            YakPlayer newPlayer = new YakPlayer(player);

                            // Run on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                onlinePlayers.put(uuid, newPlayer);

                                // Initialize default values and settings
                                ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
                                ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);

                                // Initialize energy system
                                initializePlayerEnergy(newPlayer);

                                // Save the new player data
                                savePlayer(newPlayer);

                                logger.info("Created new player data for " + player.getName());
                            });
                        } else {
                            // This is a data load failure, kick the player
                            int attempts = 1;
                            if (failedLoads.contains(uuid)) {
                                attempts = maxFailedLoadAttempts; // Force immediate kick
                            }

                            if (attempts >= maxFailedLoadAttempts) {
                                // Kick player after multiple failed attempts
                                final int finalAttempts = attempts;
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.kickPlayer("Failed to load your player data after " + finalAttempts +
                                            " attempts. Please try again later or contact an admin.");
                                });

                                logDataError("Kicked player " + player.getName() + " after " + attempts +
                                        " failed data load attempts");
                            } else {
                                // Log the error but allow player to join
                                failedLoads.add(uuid);
                                logDataError("Failed to load player data for " + player.getName() +
                                        ", attempt " + attempts + "/" + maxFailedLoadAttempts);

                                // Create emergency data to allow play
                                YakPlayer emergencyPlayer = new YakPlayer(player);

                                // Run on main thread
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    onlinePlayers.put(uuid, emergencyPlayer);
                                    initializePlayerEnergy(emergencyPlayer);

                                    // Notify player of the issue
                                    player.sendMessage("§cWarning: There was an issue loading your player data. " +
                                            "Emergency data has been created. Your progress may not be saved. " +
                                            "Please report this to an admin.");

                                    // Notify admins
                                    Bukkit.getOnlinePlayers().stream()
                                            .filter(p -> p.hasPermission("yakserver.admin"))
                                            .forEach(p -> p.sendMessage("§4[ADMIN] §cEmergency data created for " +
                                                    player.getName() + " due to data load failure!"));
                                });
                            }
                        }
                    } else {
                        // Player data exists, update online status
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            yakPlayer.connect(player);
                            onlinePlayers.put(uuid, yakPlayer);

                            // Apply player's inventory and stats with improved error handling
                            try {
                                // Teleport player to their saved location if valid
                                if (yakPlayer.getLocation() != null &&
                                        yakPlayer.getLocation().getWorld() != null) {
                                    player.teleport(yakPlayer.getLocation());
                                }

                                // Apply inventory and stats
                                yakPlayer.applyInventory(player);
                                yakPlayer.applyStats(player);
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Error applying player data for " + player.getName(), e);
                                logDataError("Error applying player data for " + player.getName(), e);

                                player.sendMessage("§cWarning: There was an issue applying some of your player data. " +
                                        "Please report this to an admin if you notice missing items or settings.");
                            }

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

                            logger.info("Loaded player data for " + player.getName());
                        });
                    }
                }).exceptionally(e -> {
                    logger.log(Level.SEVERE, "Error loading player data for " + player.getName(), e);
                    logDataError("Error loading player data for " + player.getName(), e);
                    failedLoads.add(uuid);

                    // If the player is still online, try to handle the error
                    if (player.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Create emergency data
                            YakPlayer emergencyPlayer = new YakPlayer(player);
                            onlinePlayers.put(uuid, emergencyPlayer);
                            initializePlayerEnergy(emergencyPlayer);

                            player.sendMessage("§cError: There was a problem loading your player data. " +
                                    "Emergency data has been created. Please report this to an admin.");

                            // Notify admins
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> p.hasPermission("yakserver.admin"))
                                    .forEach(p -> p.sendMessage("§4[ADMIN] §cError loading data for " +
                                            player.getName() + ": " + e.getMessage()));
                        });
                    }

                    return null;
                });
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * Handles player quit to save data and clean up
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Acquire lock for this player
        ReentrantLock lock = getPlayerLock(uuid);
        lock.lock();

        try {
            YakPlayer yakPlayer = onlinePlayers.get(uuid);
            if (yakPlayer != null) {
                // Update player data before disconnecting
                yakPlayer.updateLocation(player.getLocation());
                yakPlayer.updateInventory(player);
                yakPlayer.updateStats(player);

                yakPlayer.disconnect();

                // Create a local copy of the player for saving
                final YakPlayer playerToSave = yakPlayer;

                // Remove from online players map
                onlinePlayers.remove(uuid);

                // Save player data asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        savePlayer(playerToSave).get(10, TimeUnit.SECONDS);
                        logger.info("Saved and removed player data for " + player.getName());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error saving player data on quit: " + player.getName(), e);
                        logDataError("Error saving player data on quit: " + player.getName(), e);
                    }
                });
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling player quit: " + player.getName(), e);
            logDataError("Error handling player quit: " + player.getName(), e);
        } finally {
            lock.unlock();
            // Don't remove the lock yet in case there are pending operations
        }
    }

    /**
     * Loads a player's data from the repository with retry logic
     *
     * @param uuid The player's UUID
     * @return A CompletableFuture containing the loaded YakPlayer or null if not found
     */
    private CompletableFuture<YakPlayer> loadPlayer(UUID uuid) {
        CompletableFuture<YakPlayer> result = new CompletableFuture<>();

        // Check if already loaded
        YakPlayer cachedPlayer = onlinePlayers.get(uuid);
        if (cachedPlayer != null) {
            result.complete(cachedPlayer);
            return result;
        }

        // Load from repository with retry logic
        AtomicInteger attempts = new AtomicInteger(0);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    repository.findById(uuid).thenAccept(playerOpt -> {
                        if (playerOpt.isPresent()) {
                            result.complete(playerOpt.get());
                        } else if (attempts.incrementAndGet() < 3) {
                            // Retry after a delay
                            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, 10L); // 0.5 second delay
                        } else {
                            // Give up after 3 attempts
                            result.complete(null);
                        }
                    }).exceptionally(e -> {
                        if (attempts.incrementAndGet() < 3) {
                            // Retry after a delay
                            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, 10L); // 0.5 second delay
                        } else {
                            // Give up after 3 attempts
                            logger.log(Level.SEVERE, "Failed to load player after 3 attempts: " + uuid, e);
                            result.completeExceptionally(e);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    if (attempts.incrementAndGet() < 3) {
                        // Retry after a delay
                        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, 10L);
                    } else {
                        // Give up after 3 attempts
                        logger.log(Level.SEVERE, "Failed to load player after 3 attempts: " + uuid, e);
                        result.completeExceptionally(e);
                    }
                }
            }
        });

        return result;
    }

    /**
     * Saves a player's data to the repository with improved error handling
     *
     * @param player The player to save
     * @return A CompletableFuture that resolves to true if the save was successful
     */
    public CompletableFuture<Boolean> savePlayer(YakPlayer player) {
        // If the player is online, update their current state before saving
        Player bukkit = player.getBukkitPlayer();
        if (bukkit != null && bukkit.isOnline()) {
            player.updateLocation(bukkit.getLocation());
            player.updateInventory(bukkit);
            player.updateStats(bukkit);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();

        // Acquire lock for this player
        ReentrantLock lock = getPlayerLock(player.getUUID());
        if (lock.tryLock()) {
            try {
                repository.save(player).thenAccept(saved -> {
                    // Record successful save time
                    playerSaveTimes.put(player.getUUID(), System.currentTimeMillis());
                    result.complete(true);
                }).exceptionally(e -> {
                    logger.log(Level.SEVERE, "Error saving player data for " + player.getUsername(), e);
                    logDataError("Error saving player data for " + player.getUsername(), e);
                    result.complete(false);
                    return null;
                });
            } finally {
                lock.unlock();
            }
        } else {
            // If we can't get a lock, queue the save for later
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                savePlayer(player).thenAccept(result::complete);
            }, 5L); // Try again in 5 ticks
        }

        return result;
    }

    /**
     * Saves a player's data to the repository and runs a callback when complete
     *
     * @param player   The player to save
     * @param callback The callback to run when saving is complete
     */
    public void savePlayer(YakPlayer player, Consumer<YakPlayer> callback) {
        savePlayer(player).thenAccept(success -> {
            if (success && callback != null) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(player));
            }
        });
    }

    /**
     * Asynchronously loads a player's data from the repository and applies a callback on the main thread
     *
     * @param uuid     The player's UUID
     * @param callback The callback to run with the loaded player
     */
    public void loadPlayerAsync(UUID uuid, Consumer<YakPlayer> callback) {
        // Check cache first
        YakPlayer cachedPlayer = onlinePlayers.get(uuid);
        if (cachedPlayer != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cachedPlayer));
            return;
        }

        // Load from repository
        loadPlayer(uuid).thenAccept(player -> {
            // Run the callback on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(player));
        }).exceptionally(e -> {
            logger.log(Level.SEVERE, "Error loading player data for " + uuid, e);
            logDataError("Error loading player data for " + uuid, e);

            // Run callback with null on failure
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return null;
        });
    }

    /**
     * Finds players by a partial name (case insensitive)
     *
     * @param partialName The partial name to search for
     * @return A list of matching player UUIDs
     */
    public CompletableFuture<List<UUID>> findPlayersByPartialName(String partialName) {
        final String searchTerm = partialName.toLowerCase();

        // First check online players
        List<UUID> onlineMatches = onlinePlayers.values().stream()
                .filter(player -> player.getUsername().toLowerCase().contains(searchTerm))
                .map(YakPlayer::getUUID)
                .collect(Collectors.toList());

        // If we found matches, return them
        if (!onlineMatches.isEmpty()) {
            return CompletableFuture.completedFuture(onlineMatches);
        }

        // Otherwise search the database
        return CompletableFuture.supplyAsync(() -> {
            try {
                // This is a simplified approach. In a real implementation,
                // you'd want to use a database query to search for partial names.
                List<YakPlayer> players = repository.findAll().get(30, TimeUnit.SECONDS);

                return players.stream()
                        .filter(player -> player.getUsername().toLowerCase().contains(searchTerm))
                        .map(YakPlayer::getUUID)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error searching for players by partial name: " + partialName, e);
                logDataError("Error searching for players by partial name: " + partialName, e);
                return Collections.emptyList();
            }
        });
    }

    /**
     * Applies an action to a player, loading them if they're not already loaded.
     * Returns a CompletableFuture that resolves to true when the operation completes successfully.
     *
     * @param uuid   The player's UUID
     * @param action The action to apply
     * @param save   Whether to save the player after applying the action
     * @return A CompletableFuture that resolves to true if the operation was successful
     */
    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> action, boolean save) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        // Acquire lock for this player
        ReentrantLock lock = getPlayerLock(uuid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            lock.lock();
            try {
                YakPlayer player = onlinePlayers.get(uuid);

                if (player != null) {
                    // Player is online, apply action immediately
                    try {
                        // Run action on main thread to ensure thread safety
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                action.accept(player);
                                if (save) {
                                    // Schedule save task
                                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                        savePlayer(player).thenAccept(result::complete);
                                    });
                                } else {
                                    result.complete(true);
                                }
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "Error executing action on player " + uuid, e);
                                logDataError("Error executing action on player " + uuid, e);
                                result.complete(false);
                            }
                        });
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error scheduling action on player " + uuid, e);
                        logDataError("Error scheduling action on player " + uuid, e);
                        result.complete(false);
                    }
                } else {
                    // Player is offline, load them first
                    loadPlayer(uuid).thenAccept(loadedPlayer -> {
                        if (loadedPlayer != null) {
                            // Run on main thread to ensure thread safety
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    action.accept(loadedPlayer);
                                    if (save) {
                                        // Schedule save task
                                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                            savePlayer(loadedPlayer).thenAccept(result::complete);
                                        });
                                    } else {
                                        result.complete(true);
                                    }
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, "Error executing action on loaded player " + uuid, e);
                                    logDataError("Error executing action on loaded player " + uuid, e);
                                    result.complete(false);
                                }
                            });
                        } else {
                            logger.log(Level.WARNING, "Player data not found for " + uuid);
                            result.complete(false);
                        }
                    }).exceptionally(e -> {
                        logger.log(Level.SEVERE, "Error loading player data for " + uuid, e);
                        logDataError("Error loading player data for " + uuid, e);
                        result.complete(false);
                        return null;
                    });
                }
            } finally {
                lock.unlock();
            }
        });

        return result;
    }
}
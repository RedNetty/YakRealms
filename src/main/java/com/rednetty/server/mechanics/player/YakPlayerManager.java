package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simplified YakPlayer manager with reliable player data handling
 */
public class YakPlayerManager implements Listener {
    private static volatile YakPlayerManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Configuration constants
    private static final long DATA_LOAD_TIMEOUT_MS = 30000L; // 30 seconds
    private static final long SAVE_TIMEOUT_MS = 15000L; // 15 seconds
    private static final int MAX_CONCURRENT_OPERATIONS = 5;

    // Core dependencies
    private volatile YakPlayerRepository repository;
    private final Logger logger;
    private final Plugin plugin;

    // Player management
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameCache = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    // Thread management
    private final ExecutorService ioExecutor;
    private final Semaphore operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS);

    // State management
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean systemHealthy = new AtomicBoolean(false);
    private final AtomicBoolean repositoryReady = new AtomicBoolean(false);

    // Tasks
    private BukkitTask autoSaveTask;
    private BukkitTask healthMonitorTask;

    // Performance tracking
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger failedLoads = new AtomicInteger(0);
    private final AtomicInteger successfulSaves = new AtomicInteger(0);
    private final AtomicInteger failedSaves = new AtomicInteger(0);

    // Configuration
    private final long autoSaveInterval;
    private final int playersPerSaveCycle;

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Load configuration
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.playersPerSaveCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 8);

        // Initialize thread pool
        this.ioExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        logger.info("YakPlayerManager initialized");
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
     * Initialize the player manager
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting YakPlayerManager initialization...");

            // Initialize repository
            if (!initializeRepository()) {
                logger.severe("Failed to initialize repository!");
                systemHealthy.set(false);
                return;
            }

            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);

            // Start background tasks
            startBackgroundTasks();

            // Mark as ready
            systemHealthy.set(true);
            repositoryReady.set(true);

            logger.info("YakPlayerManager enabled successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerManager", e);
            systemHealthy.set(false);
        }
    }

    /**
     * Initialize the repository
     */
    private boolean initializeRepository() {
        try {
            logger.info("Initializing YakPlayerRepository...");
            this.repository = new YakPlayerRepository();

            // Wait for repository to be ready
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

    /**
     * Enhanced pre-login handler with ban checking
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
            // Check for existing ban
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
                    repository.save(player);
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking ban status for " + playerName + ", allowing login", e);
        }
    }


    /**
     * Load player data asynchronously
     */
    private CompletableFuture<YakPlayer> loadPlayerDataAsync(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire operation semaphore
                if (!operationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Operation semaphore timeout");
                }

                try {
                    logger.fine("Loading player data for: " + player.getName());

                    // Load from repository
                    CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(player.getUniqueId());
                    Optional<YakPlayer> existingPlayer = repositoryFuture.get(DATA_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

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
                    }

                    successfulLoads.incrementAndGet();
                    return yakPlayer;

                } finally {
                    operationSemaphore.release();
                }

            } catch (Exception e) {
                failedLoads.incrementAndGet();
                logger.log(Level.SEVERE, "Failed to load player data for: " + player.getName(), e);
                throw new RuntimeException("Player data load failed", e);
            }
        }, ioExecutor);
    }

    /**
     * Handle completion of player data loading
     */
    private void handlePlayerLoadCompletion(Player player, YakPlayer yakPlayer, Throwable error) {
        UUID uuid = player.getUniqueId();
        loadingPlayers.remove(uuid);

        if (error != null) {
            handlePlayerLoadFailure(player, error);
            return;
        }

        if (yakPlayer == null) {
            handlePlayerLoadFailure(player, new RuntimeException("Null player data"));
            return;
        }

        // Register player
        onlinePlayers.put(uuid, yakPlayer);
        playerNameCache.put(player.getName().toLowerCase(), uuid);

        logger.info("Successfully loaded player data for: " + player.getName());

        // Initialize player systems
        initializePlayerSystems(player, yakPlayer);

        // Update join message
        updateJoinMessage(player, yakPlayer);
    }

    /**
     * Handle player data load failure
     */
    private void handlePlayerLoadFailure(Player player, Throwable error) {
        logger.log(Level.SEVERE, "Player data load failed for: " + player.getName(), error);

        if (player.isOnline()) {
            player.sendMessage(ChatColor.RED + "⚠ Failed to load your character data.");
            player.sendMessage(ChatColor.GRAY + "Please reconnect. If this persists, contact an administrator.");

            if (player.hasPermission("yakrealms.admin")) {
                player.sendMessage(ChatColor.GRAY + "Error: " + error.getMessage());
            }
        }
    }

    /**
     * Save player data asynchronously
     */
    private CompletableFuture<Boolean> savePlayerDataAsync(YakPlayer yakPlayer) {
        return CompletableFuture.supplyAsync(() -> {
            if (yakPlayer == null) {
                return false;
            }

            try {
                // Acquire operation semaphore
                if (!operationSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                    logger.warning("Save semaphore timeout for player: " + yakPlayer.getUsername());
                    return false;
                }

                try {
                    logger.fine("Saving player data for: " + yakPlayer.getUsername());

                    // Perform save
                    CompletableFuture<YakPlayer> saveFuture = repository.save(yakPlayer);
                    YakPlayer savedPlayer = saveFuture.get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    return savedPlayer != null;

                } finally {
                    operationSemaphore.release();
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player data for: " + yakPlayer.getUsername(), e);
                return false;
            }
        }, ioExecutor);
    }

    /**
     * Clean up player references
     */
    private void cleanupPlayerReferences(UUID uuid, String playerName) {
        playerNameCache.remove(playerName.toLowerCase());

        // Clean up moderation and chat systems
        ModerationMechanics.rankMap.remove(uuid);
        ChatMechanics.getPlayerTags().remove(uuid);
    }

    /**
     * Start background tasks
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

        logger.info("Background tasks started successfully");
    }

    /**
     * Perform auto-save with rate limiting
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
        for (YakPlayer player : playersToSave) {
            Player bukkitPlayer = player.getBukkitPlayer();
            if (bukkitPlayer != null) {
                updatePlayerBeforeSave(player, bukkitPlayer);
            }
            savePlayerDataAsync(player);
        }
    }

    /**
     * Perform health check
     */
    private void performHealthCheck() {
        try {
            boolean healthy = true;

            // Check repository status
            if (!repositoryReady.get() || repository == null || !repository.isInitialized()) {
                healthy = false;
                logger.warning("Repository not ready during health check");
            }

            // Check semaphore availability
            if (operationSemaphore.availablePermits() == 0) {
                healthy = false;
                logger.warning("Operation semaphore exhausted");
            }

            systemHealthy.set(healthy);

            if (!healthy) {
                logger.warning("YakPlayerManager health check failed");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during health check", e);
            systemHealthy.set(false);
        }
    }

    /**
     * Shutdown process
     */
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }

        logger.info("Starting YakPlayerManager shutdown...");

        try {
            // Cancel tasks
            if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
                autoSaveTask.cancel();
            }
            if (healthMonitorTask != null && !healthMonitorTask.isCancelled()) {
                healthMonitorTask.cancel();
            }

            // Save all online players
            saveAllPlayersOnShutdown();

            // Shutdown repository
            if (repository != null) {
                repository.shutdown();
            }

            // Shutdown executor
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Clear data structures
            onlinePlayers.clear();
            playerNameCache.clear();
            loadingPlayers.clear();

            logger.info("YakPlayerManager shutdown completed successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during YakPlayerManager shutdown", e);
        } finally {
            initialized.set(false);
            repositoryReady.set(false);
        }
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

        message.append("\n\n").append(ChatColor.GRAY).append("Appeal at: ").append(ChatColor.BLUE).append("discord.gg/yakrealms");
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

    private void updateJoinMessage(Player player, YakPlayer yakPlayer) {
        String formattedName = yakPlayer.getFormattedDisplayName();
        int onlineCount = Bukkit.getOnlinePlayers().size();

        String joinMessage;
        if (onlineCount == 1) {
            joinMessage = ChatColor.GREEN + "✦ " + ChatColor.BOLD + formattedName +
                    ChatColor.GREEN + " started the adventure! ✦";
        } else if (onlineCount <= 5) {
            joinMessage = ChatColor.AQUA + "[+] " + formattedName +
                    ChatColor.GRAY + " (" + onlineCount + " online)";
        } else if (onlineCount <= 20) {
            joinMessage = ChatColor.AQUA + "[+] " + formattedName;
        } else {
            joinMessage = null; // High population - no message
        }

        if (joinMessage != null) {
            Bukkit.broadcastMessage(joinMessage);
        }
    }

    private void setEnhancedQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer) {
        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();
            event.setQuitMessage(ChatColor.RED + "[-] " + formattedName);
        } else {
            event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + player.getName());
        }
    }

    /**
     * Execute an operation on a YakPlayer with proper synchronization and error handling.
     * This method is used by the EconomyManager and other systems that need to perform
     * operations on player data safely.
     *
     * @param uuid The UUID of the player
     * @param operation The operation to perform on the YakPlayer
     * @param saveAfter Whether to save the player data after the operation
     * @return A CompletableFuture<Boolean> indicating success or failure
     */
    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation, boolean saveAfter) {
        if (uuid == null || operation == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Acquire operation semaphore
                if (!operationSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                    logger.warning("withPlayer semaphore timeout for player: " + uuid);
                    return false;
                }

                try {
                    // Get the player data
                    YakPlayer yakPlayer = getPlayer(uuid);
                    if (yakPlayer == null) {
                        logger.warning("Player not found for withPlayer operation: " + uuid);
                        return false;
                    }

                    // Execute the operation
                    operation.accept(yakPlayer);

                    // Save if requested
                    if (saveAfter) {
                        CompletableFuture<Boolean> saveFuture = savePlayerDataAsync(yakPlayer);
                        return saveFuture.get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    }

                    return true;

                } finally {
                    operationSemaphore.release();
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in withPlayer operation for " + uuid, e);
                return false;
            }
        }, ioExecutor);
    }

    /**
     * Overloaded version that defaults to saving after the operation
     */
    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation) {
        return withPlayer(uuid, operation, true);
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
        return savePlayerDataAsync(player);
    }

    public void saveAllPlayers() {
        performAutoSave();
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
        logger.info("Operation Permits Available: " + operationSemaphore.availablePermits() + "/" + MAX_CONCURRENT_OPERATIONS);
        logger.info("Repository Ready: " + repositoryReady.get());
        logger.info("System Health: " + (systemHealthy.get() ? "HEALTHY" : "DEGRADED"));
        logger.info("==========================================");
    }

    // Convenience methods for other systems
    public CompletableFuture<Boolean> addPlayerGems(UUID playerId, int amount) {
        YakPlayer yakPlayer = getPlayer(playerId);
        if (yakPlayer != null) {
            yakPlayer.setBankGems(yakPlayer.getBankGems() + amount);
            return savePlayer(yakPlayer);
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> removePlayerGems(UUID playerId, int amount) {
        YakPlayer yakPlayer = getPlayer(playerId);
        if (yakPlayer != null && yakPlayer.getBankGems() >= amount) {
            yakPlayer.setBankGems(yakPlayer.getBankGems() - amount);
            return savePlayer(yakPlayer);
        }
        return CompletableFuture.completedFuture(false);
    }

    public int getPlayerGems(UUID playerId) {
        YakPlayer yakPlayer = getPlayer(playerId);
        return yakPlayer != null ? yakPlayer.getBankGems() : 0;
    }

    public int getPlayerLevel(UUID playerId) {
        YakPlayer yakPlayer = getPlayer(playerId);
        return yakPlayer != null ? yakPlayer.getLevel() : 1;
    }

    public String getPlayerDisplayName(UUID playerId) {
        YakPlayer yakPlayer = getPlayer(playerId);
        return yakPlayer != null ? yakPlayer.getFormattedDisplayName() : "Unknown Player";
    }

    public boolean hasPlayer(UUID playerId) {
        return playerId != null && getPlayer(playerId) != null;
    }

    public boolean hasPlayer(String playerName) {
        return playerName != null && !playerName.trim().isEmpty() && getPlayer(playerName) != null;
    }

    /**
     *  Enhanced player quit handler with combat logout protection
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        logger.info("Player quitting: " + playerName);

        // Remove from loading if still loading
        loadingPlayers.remove(uuid);

        // Get and remove player data
        YakPlayer yakPlayer = onlinePlayers.remove(uuid);
        if (yakPlayer == null) {
            logger.warning("No player data found for quitting player: " + playerName);
            cleanupPlayerReferences(uuid, playerName);
            return;
        }

        //  Check if this is a combat logout scenario
        boolean isCombatLogout = false;
        try {
            // Check with AlignmentMechanics if player is combat logging
            isCombatLogout = AlignmentMechanics.getInstance().isCombatLoggingOut(player);
        } catch (Exception e) {
            logger.warning("Could not check combat logout status for " + playerName + ": " + e.getMessage());
        }

        if (isCombatLogout) {
            logger.info("COMBAT LOGOUT detected for " + playerName + " - special handling");

            // For combat logout, DON'T update inventory/stats from the bukkit player
            // This prevents the combat logger from keeping items they shouldn't have
            yakPlayer.disconnect();

            // Mark as combat logout in temporary data
            yakPlayer.setTemporaryData("combat_logout_quit", System.currentTimeMillis());

            // Clean up references
            cleanupPlayerReferences(uuid, playerName);

            // Save player data immediately (without updating from bukkit player)
            savePlayerDataAsync(yakPlayer).whenComplete((result, error) -> {
                if (error != null) {
                    logger.log(Level.SEVERE, "Failed to save combat logout player: " + playerName, error);
                    failedSaves.incrementAndGet();
                } else {
                    logger.info("Successfully saved combat logout player: " + playerName);
                    successfulSaves.incrementAndGet();
                }
            });

            // Set quit message
            event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + playerName + ChatColor.DARK_GRAY + " (combat logout)");
            return;
        }

        // Normal quit processing
        logger.fine("Normal quit processing for: " + playerName);

        // Update player data before saving (only for normal quits)
        updatePlayerBeforeSave(yakPlayer, player);
        yakPlayer.disconnect();

        // Clean up references
        cleanupPlayerReferences(uuid, playerName);

        // Save player data
        savePlayerDataAsync(yakPlayer).whenComplete((result, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Failed to save player on quit: " + playerName, error);
                failedSaves.incrementAndGet();
            } else {
                logger.fine("Successfully saved player on quit: " + playerName);
                successfulSaves.incrementAndGet();
            }
        });

        // Set quit message
        setEnhancedQuitMessage(event, player, yakPlayer);
    }

    /**
     *  Enhanced updatePlayerBeforeSave with combat logout protection
     */
    private void updatePlayerBeforeSave(YakPlayer yakPlayer, Player bukkitPlayer) {
        if (yakPlayer == null || bukkitPlayer == null) {
            return;
        }

        try {
            //  Don't update data if player is dead or has very low health (ghost state)
            if (bukkitPlayer.isDead() || bukkitPlayer.getHealth() <= 0.5) {
                logger.warning("Skipping data update for dead/ghost player: " + bukkitPlayer.getName());
                return;
            }

            //  Check if this player combat logged - if so, don't update inventory
            boolean isCombatLogout = false;
            try {
                isCombatLogout = AlignmentMechanics.getInstance().isCombatLoggingOut(bukkitPlayer);
            } catch (Exception e) {
                logger.fine("Could not check combat logout status during save: " + e.getMessage());
            }

            // Always update location and stats
            yakPlayer.updateLocation(bukkitPlayer.getLocation());
            yakPlayer.updateStats(bukkitPlayer);

            // Only update inventory for non-combat logouts
            if (!isCombatLogout) {
                yakPlayer.updateInventory(bukkitPlayer);
                logger.fine("Updated inventory for normal quit: " + bukkitPlayer.getName());
            } else {
                logger.info("Skipped inventory update for combat logout: " + bukkitPlayer.getName());
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player before save: " + yakPlayer.getUsername(), e);
        }
    }

    /**
     *  Enhanced player join handler with combat logout detection
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
        if (loadingPlayers.contains(uuid) || onlinePlayers.containsKey(uuid)) {
            logger.warning("Duplicate join event for player: " + playerName);
            return;
        }

        // Add to loading set
        loadingPlayers.add(uuid);

        // Set temporary join message
        setTemporaryJoinMessage(event, player);

        // Load player data asynchronously
        loadPlayerDataAsync(player).whenComplete((yakPlayer, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                handlePlayerLoadCompletion(player, yakPlayer, error);
            });
        });
    }

    /**
     *  Enhanced initialization with combat logout state cleanup
     */
    private void initializePlayerSystems(Player player, YakPlayer yakPlayer) {
        try {
            UUID uuid = player.getUniqueId();

            //  Check if player had a combat logout last session
            boolean hadCombatLogout = yakPlayer.hasTemporaryData("combat_logout_quit");
            if (hadCombatLogout) {
                logger.info("Player " + player.getName() + " had a combat logout last session");
                yakPlayer.removeTemporaryData("combat_logout_quit");

                // Notify player about the combat logout consequences
                player.sendMessage(ChatColor.RED + "⚠ You were punished for combat logging in your last session.");
                player.sendMessage(ChatColor.GRAY + "All items were dropped at your death location.");
            }

            // Initialize energy
            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }
            player.setExp(1.0f);
            player.setLevel(100);

            // Apply saved inventory and armor first (needed for health calculation)
            yakPlayer.applyInventory(player);

            // Calculate max health based on current armor and apply all stats
            // This needs to be done AFTER inventory is applied so armor is equipped
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    PlayerListenerManager.getInstance().getHealthListener().recalculateHealth(player);

                    // Apply all stats including the corrected health values
                    yakPlayer.applyStats(player);

                    logger.fine("Applied health stats for " + player.getName() +
                            ": " + yakPlayer.getHealth() + "/" + yakPlayer.getMaxHealth());

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error applying health stats for " + player.getName(), e);
                }
            }, 1L); // Delay by 1 tick to ensure inventory is fully applied

            // Initialize rank system
            try {
                String rankString = yakPlayer.getRank();
                if (rankString == null || rankString.trim().isEmpty()) {
                    rankString = "default";
                    yakPlayer.setRank("default");
                    logger.info("Set default rank for player with null/empty rank: " + player.getName());
                }

                Rank rank = Rank.fromString(rankString);
                ModerationMechanics.rankMap.put(uuid, rank);

                logger.fine("Successfully loaded rank " + rank.name() + " for player: " + player.getName());

            } catch (IllegalArgumentException e) {
                logger.warning("Invalid rank for player " + player.getName() + ": " + yakPlayer.getRank() +
                        ". Setting to DEFAULT and saving correction.");

                ModerationMechanics.rankMap.put(uuid, Rank.DEFAULT);
                yakPlayer.setRank("default");

                savePlayerDataAsync(yakPlayer).whenComplete((result, error) -> {
                    if (error != null) {
                        logger.warning("Failed to save rank correction for " + player.getName() + ": " + error.getMessage());
                    } else {
                        logger.info("Successfully corrected and saved rank for " + player.getName());
                    }
                });
            }

            // Initialize chat tag system
            try {
                String chatTagString = yakPlayer.getChatTag();
                if (chatTagString == null || chatTagString.trim().isEmpty()) {
                    chatTagString = "DEFAULT";
                    yakPlayer.setChatTag("DEFAULT");
                }

                ChatTag tag = ChatTag.valueOf(chatTagString);
                ChatMechanics.getPlayerTags().put(uuid, tag);

            } catch (IllegalArgumentException e) {
                logger.warning("Invalid chat tag for player " + player.getName() + ": " + yakPlayer.getChatTag() +
                        ". Setting to DEFAULT.");
                ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
                yakPlayer.setChatTag("DEFAULT");
            }

            logger.fine("Initialized systems for player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
        }
    }

    /**
     *  Enhanced save all players on shutdown with combat logout handling
     */
    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();

            //  For shutdown, save current state regardless of combat status
            // But log if it was a combat logout for debugging
            boolean wasCombatLogout = false;
            if (player != null && player.isOnline()) {
                try {
                    wasCombatLogout = AlignmentMechanics.getInstance().isCombatLoggingOut(player);
                    if (wasCombatLogout) {
                        logger.info("Saving combat logout player on shutdown: " + player.getName());
                    }
                } catch (Exception e) {
                    logger.fine("Could not check combat status during shutdown for " + yakPlayer.getUsername());
                }

                // Update player data before saving (normal update during shutdown)
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
    public boolean hasPlayer(Player player) {
        return player != null && player.isOnline() && getPlayer(player) != null;
    }
}
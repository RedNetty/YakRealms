package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * : YakPlayerManager with atomic operations and dupe prevention
 * - Comprehensive player state management with atomic operations
 * -  combat logout handling with proper state isolation
 * - Improved error handling and rollback capabilities
 * - Thread-safe operations with better concurrency control
 * - Guaranteed data consistency and dupe prevention
 * - Fixed deadlock issues with proper executor sizing and async chaining
 */
public class YakPlayerManager implements Listener {
    private static volatile YakPlayerManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    // Configuration constants
    private static final long DATA_LOAD_TIMEOUT_MS = 30000L;
    private static final long SAVE_TIMEOUT_MS = 15000L;
    private static final int MAX_CONCURRENT_OPERATIONS = 5;
    private static final long PLAYER_STATE_LOCK_TIMEOUT = 5000L;

    // Core dependencies
    private volatile YakPlayerRepository repository;
    private final Logger logger;
    private final Plugin plugin;

    //  player management with atomic operations
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, UUID> playerNameCache = new ConcurrentHashMap<>();
    private final Set<UUID> loadingPlayers = ConcurrentHashMap.newKeySet();

    // Player state management for dupe prevention
    private final Map<UUID, ReentrantReadWriteLock> playerStateLocks = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> playerDataDirty = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSaveTime = new ConcurrentHashMap<>();

    // Thread management -  Increased thread pool size to prevent deadlocks
    private final ExecutorService ioExecutor;
    private final ExecutorService saveExecutor; // Separate executor for saves to prevent deadlocks
    private final Semaphore operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS);

    // State management
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean systemHealthy = new AtomicBoolean(false);
    private final AtomicBoolean repositoryReady = new AtomicBoolean(false);

    // Tasks
    private BukkitTask autoSaveTask;
    private BukkitTask healthMonitorTask;
    private BukkitTask stateCleanupTask;

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

        // OPTIMIZED: Initialize thread pools with balanced sizing for performance
        // Reduced IO thread pool for better performance while preventing deadlocks
        int ioThreads = plugin.getConfig().getInt("player_manager.io_threads", 6);
        int saveThreads = plugin.getConfig().getInt("player_manager.save_threads", 3);

        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        // Separate executor for save operations to prevent deadlocks
        this.saveExecutor = Executors.newFixedThreadPool(saveThreads, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-Save");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        logger.info(" YakPlayerManager initialized with dupe prevention, deadlock fixes, and tab menu optimizations");
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
     * Initialize the  player manager
     */
    public void onEnable() {
        if (!initialized.compareAndSet(false, true)) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting  YakPlayerManager initialization...");

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

            logger.info(" YakPlayerManager enabled successfully with dupe prevention, deadlock fixes, and performance optimizations");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize  YakPlayerManager", e);
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
     *  pre-login handler with comprehensive ban checking
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
            // Check for existing ban with atomic operation
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(10, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                // Atomic ban status check
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
     * ATOMIC player join handler with comprehensive state management
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerJoins.incrementAndGet();
        logger.info("Player joining: " + playerName + " (" + uuid + ")");

        // Acquire player state lock immediately
        ReentrantReadWriteLock playerLock = getPlayerStateLock(uuid);
        playerLock.writeLock().lock();

        try {
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

        } finally {
            playerLock.writeLock().unlock();
        }
    }

    /**
     * ATOMIC player data loading with  error handling
     */
    private CompletableFuture<YakPlayer> loadPlayerDataAsync(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            UUID uuid = player.getUniqueId();

            try {
                // Acquire operation semaphore
                if (!operationSemaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Operation semaphore timeout");
                }

                try {
                    logger.fine("Loading player data for: " + player.getName());

                    // Load from repository with timeout
                    CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(uuid);
                    Optional<YakPlayer> existingPlayer = repositoryFuture.get(DATA_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    YakPlayer yakPlayer;

                    if (existingPlayer.isPresent()) {
                        yakPlayer = existingPlayer.get();

                        // ATOMIC connection setup
                        connectPlayerAtomically(yakPlayer, player);

                        logger.fine("Loaded existing player data for: " + player.getName());
                    } else {
                        yakPlayer = new YakPlayer(player);
                        logger.info("Created new player data for: " + player.getName());

                        // Save new player immediately with atomic operation
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
     * ATOMIC player connection setup
     */
    private void connectPlayerAtomically(YakPlayer yakPlayer, Player player) {
        UUID uuid = player.getUniqueId();
        ReentrantReadWriteLock playerLock = getPlayerStateLock(uuid);

        playerLock.writeLock().lock();
        try {
            yakPlayer.connect(player);

            // Initialize tracking
            playerDataDirty.put(uuid, new AtomicBoolean(false));
            lastSaveTime.put(uuid, System.currentTimeMillis());

        } finally {
            playerLock.writeLock().unlock();
        }
    }

    /**
     * Handle completion of player data loading with atomic registration
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

        // ATOMIC player registration
        ReentrantReadWriteLock playerLock = getPlayerStateLock(uuid);
        playerLock.writeLock().lock();

        try {
            // Register player atomically
            onlinePlayers.put(uuid, yakPlayer);
            playerNameCache.put(player.getName().toLowerCase(), uuid);

            logger.info("Successfully loaded player data for: " + player.getName());

            // Initialize player systems
            initializePlayerSystems(player, yakPlayer);

            // Update join message
            updateJoinMessage(player, yakPlayer);

        } finally {
            playerLock.writeLock().unlock();
        }
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
     *  Initialize player systems with combat logout state handling - NO DUPLICATE MESSAGES
     */
    private void initializePlayerSystems(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        try {
            // Check and handle combat logout state atomically
            YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();

            switch (logoutState) {
                case PROCESSED:
                case PROCESSING:
                    schedulePlayerDeath(player, yakPlayer);
                    return;

                case COMPLETED:
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    // REMOVED: showCombatLogoutMessage(player, yakPlayer);
                    // CombatLogoutMechanics handles all messaging to prevent duplicates
                    break;

                case NONE:
                default:
                    break;
            }

            // Initialize energy atomically
            if (!yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }
            player.setExp(1.0f);
            player.setLevel(100);

            // Apply saved inventory and armor
            yakPlayer.applyInventory(player);

            // Calculate and apply health stats with delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    PlayerListenerManager.getInstance().getHealthListener().recalculateHealth(player);
                    yakPlayer.applyStats(player);
                    logger.fine("Applied health stats for " + player.getName() +
                            ": " + yakPlayer.getHealth() + "/" + yakPlayer.getMaxHealth());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error applying health stats for " + player.getName(), e);
                }
            }, 1L);

            // Initialize rank system atomically
            initializeRankSystem(player, yakPlayer, uuid);

            if (yakPlayer.getHorseTier() == 6 || yakPlayer.getHorseTier() == 0) yakPlayer.setHorseTier(1);

            // Initialize chat tag system atomically
            initializeChatTagSystem(player, yakPlayer, uuid);

            logger.fine("Initialized systems for player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing player systems for " + player.getName(), e);
        }
    }

    /**
     * ATOMIC rank system initialization
     */
    private void initializeRankSystem(Player player, YakPlayer yakPlayer, UUID uuid) {
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

            // Save correction atomically
            savePlayerDataAsync(yakPlayer).whenComplete((result, error) -> {
                if (error != null) {
                    logger.warning("Failed to save rank correction for " + player.getName() + ": " + error.getMessage());
                } else {
                    logger.info("Successfully corrected and saved rank for " + player.getName());
                }
            });
        }
    }

    /**
     * ATOMIC chat tag system initialization
     */
    private void initializeChatTagSystem(Player player, YakPlayer yakPlayer, UUID uuid) {
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
    }

    /**
     * Schedule player death for combat logout processing
     */
    private void schedulePlayerDeath(Player player, YakPlayer yakPlayer) {
        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSING);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;

            try {
                player.setHealth(0);
                if (player.isDead()) {
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
                    showCombatLogoutMessage(player, yakPlayer);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing combat logout death", e);
            }
        }, 5L);
    }

    /**
     *  Show combat logout completion message - REMOVE DUPLICATE
     * This method should be REMOVED or commented out since CombatLogoutMechanics handles the messaging
     */
    private void showCombatLogoutMessage(Player player, YakPlayer yakPlayer) {
        // REMOVED: This was causing duplicate messages
        // The CombatLogoutMechanics.schedulePlayerDeathSafely() already handles messaging
        // No message needed here to prevent duplication

        YakRealms.log("Combat logout completion processing for: " + player.getName() + " (message handled by CombatLogoutMechanics)");
    }


    /**
     * Get punishment message based on alignment
     */
    private String getCombatLogoutPunishmentMessage(String alignment) {
        switch (alignment) {
            case "LAWFUL":
                return "As a lawful player, you kept your armor and first hotbar item but lost other inventory.";
            case "NEUTRAL":
                return "As a neutral player, you had chances to keep some gear based on luck.";
            case "CHAOTIC":
                return "As a chaotic player, you lost all items for combat logging.";
            default:
                return "Your items were handled according to your alignment rules.";
        }
    }

    /**
     * ATOMIC player quit handler with comprehensive state management
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        logger.info("Player quitting: " + playerName);

        // Acquire player state lock
        ReentrantReadWriteLock playerLock = getPlayerStateLock(uuid);
        playerLock.writeLock().lock();

        try {
            // Remove from loading if still loading
            loadingPlayers.remove(uuid);

            // Get and remove player data atomically
            YakPlayer yakPlayer = onlinePlayers.remove(uuid);
            if (yakPlayer == null) {
                logger.warning("No player data found for quitting player: " + playerName);
                cleanupPlayerReferences(uuid, playerName);
                return;
            }

            // Check if this is a combat logout scenario
            boolean isCombatLogout = false;
            try {
                isCombatLogout = AlignmentMechanics.getInstance().isCombatLoggingOut(player);
            } catch (Exception e) {
                logger.warning("Could not check combat logout status for " + playerName + ": " + e.getMessage());
            }

            if (isCombatLogout) {
                logger.info("COMBAT LOGOUT detected for " + playerName + " - processing handled by AlignmentMechanics");

                // For combat logout, DON'T update inventory/stats
                yakPlayer.disconnect();
                cleanupPlayerReferences(uuid, playerName);

                // Set quit message
                event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + playerName + ChatColor.DARK_GRAY + " (combat logout)");
                return;
            }

            // Normal quit processing
            logger.fine("Normal quit processing for: " + playerName);

            // Update player data before saving (atomic operation)
            updatePlayerBeforeSaveAtomically(yakPlayer, player);
            yakPlayer.disconnect();

            // Clean up references
            cleanupPlayerReferences(uuid, playerName);

            // Save player data asynchronously - don't block the quit event
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
            setQuitMessage(event, player, yakPlayer);

        } finally {
            playerLock.writeLock().unlock();
        }
    }

    /**
     * ATOMIC player data update before save
     */
    private void updatePlayerBeforeSaveAtomically(YakPlayer yakPlayer, Player bukkitPlayer) {
        if (yakPlayer == null || bukkitPlayer == null) {
            return;
        }

        try {
            // Don't update data if player is dead or has very low health
            if (bukkitPlayer.isDead() || bukkitPlayer.getHealth() <= 0.5) {
                logger.warning("Skipping data update for dead/ghost player: " + bukkitPlayer.getName());
                return;
            }

            // Atomic update of all player data
            yakPlayer.updateLocation(bukkitPlayer.getLocation());
            yakPlayer.updateStats(bukkitPlayer);
            yakPlayer.updateInventory(bukkitPlayer);

            // Mark as dirty
            AtomicBoolean dirty = playerDataDirty.get(bukkitPlayer.getUniqueId());
            if (dirty != null) {
                dirty.set(true);
            }

            logger.fine("Updated all data for normal quit: " + bukkitPlayer.getName());

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player before save: " + yakPlayer.getUsername(), e);
        }
    }

    /**
     *  ATOMIC player data saving with separate executor to prevent deadlocks
     */
    private CompletableFuture<Boolean> savePlayerDataAsync(YakPlayer yakPlayer) {
        return CompletableFuture.supplyAsync(() -> {
            if (yakPlayer == null) {
                return false;
            }

            UUID uuid = yakPlayer.getUUID();
            ReentrantReadWriteLock playerLock = getPlayerStateLock(uuid);

            // Use read lock for saving (allows concurrent reads)
            playerLock.readLock().lock();

            try {
                // Acquire operation semaphore
                if (!operationSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                    logger.warning("Save semaphore timeout for player: " + yakPlayer.getUsername());
                    return false;
                }

                try {
                    logger.fine("Saving player data for: " + yakPlayer.getUsername());

                    // Perform atomic save
                    CompletableFuture<YakPlayer> saveFuture = repository.save(yakPlayer);
                    YakPlayer savedPlayer = saveFuture.get(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    // Update save tracking
                    lastSaveTime.put(uuid, System.currentTimeMillis());
                    AtomicBoolean dirty = playerDataDirty.get(uuid);
                    if (dirty != null) {
                        dirty.set(false);
                    }

                    return savedPlayer != null;

                } finally {
                    operationSemaphore.release();
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player data for: " + yakPlayer.getUsername(), e);
                return false;
            } finally {
                playerLock.readLock().unlock();
            }
        }, saveExecutor); //  Use separate save executor to prevent deadlocks
    }

    /**
     * Get or create player state lock
     */
    private ReentrantReadWriteLock getPlayerStateLock(UUID uuid) {
        return playerStateLocks.computeIfAbsent(uuid, k -> new ReentrantReadWriteLock());
    }

    /**
     * Clean up player references atomically
     */
    private void cleanupPlayerReferences(UUID uuid, String playerName) {
        playerNameCache.remove(playerName.toLowerCase());

        // Clean up moderation and chat systems
        ModerationMechanics.rankMap.remove(uuid);
        ChatMechanics.getPlayerTags().remove(uuid);

        // Clean up tracking data
        playerDataDirty.remove(uuid);
        lastSaveTime.remove(uuid);
    }

    /**
     *  background tasks
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
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L);

        // State cleanup task
        stateCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress.get()) {
                    performStateCleanup();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes

        logger.info(" background tasks started successfully");
    }

    /**
     * Perform auto-save with atomic operations
     */
    private void performAutoSave() {
        List<YakPlayer> playersToSave = new ArrayList<>();

        // Collect players to save atomically
        int count = 0;
        for (Map.Entry<UUID, YakPlayer> entry : onlinePlayers.entrySet()) {
            if (count >= playersPerSaveCycle) break;

            UUID uuid = entry.getKey();
            YakPlayer player = entry.getValue();
            Player bukkitPlayer = player.getBukkitPlayer();

            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                // Check if player data is dirty
                AtomicBoolean dirty = playerDataDirty.get(uuid);
                if (dirty != null && dirty.get()) {
                    playersToSave.add(player);
                    count++;
                }
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
                updatePlayerBeforeSaveAtomically(player, bukkitPlayer);
            }
            savePlayerDataAsync(player);
        }
    }

    /**
     * Perform system health check
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

            // Check for stuck locks
            long currentTime = System.currentTimeMillis();
            int stuckLocks = 0;
            for (Map.Entry<UUID, Long> entry : lastSaveTime.entrySet()) {
                if (currentTime - entry.getValue() > 300000) { // 5 minutes
                    stuckLocks++;
                }
            }

            if (stuckLocks > 0) {
                logger.warning("Detected " + stuckLocks + " potentially stuck player locks");
            }

            systemHealthy.set(healthy);

            if (!healthy) {
                logger.warning(" YakPlayerManager health check failed");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during health check", e);
            systemHealthy.set(false);
        }
    }

    /**
     * Perform state cleanup
     */
    private void performStateCleanup() {
        try {
            long currentTime = System.currentTimeMillis();

            // Clean up orphaned player state locks
            Iterator<Map.Entry<UUID, ReentrantReadWriteLock>> lockIterator = playerStateLocks.entrySet().iterator();
            while (lockIterator.hasNext()) {
                Map.Entry<UUID, ReentrantReadWriteLock> entry = lockIterator.next();
                UUID uuid = entry.getKey();

                // Remove locks for offline players
                if (!onlinePlayers.containsKey(uuid)) {
                    Long lastSave = lastSaveTime.get(uuid);
                    if (lastSave == null || currentTime - lastSave > 600000) { // 10 minutes
                        lockIterator.remove();
                        playerDataDirty.remove(uuid);
                        lastSaveTime.remove(uuid);
                    }
                }
            }

            logger.fine("State cleanup completed");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during state cleanup", e);
        }
    }

    /**
     * ATOMIC save all players on shutdown
     */
    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>();

        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();

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

                // Update player data before saving
                updatePlayerBeforeSaveAtomically(yakPlayer, player);
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
            }, saveExecutor)); // Use save executor even for shutdown
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

    /**
     *  shutdown process
     */
    public void onDisable() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }

        logger.info("Starting  YakPlayerManager shutdown...");

        try {
            // Cancel tasks
            if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
                autoSaveTask.cancel();
            }
            if (healthMonitorTask != null && !healthMonitorTask.isCancelled()) {
                healthMonitorTask.cancel();
            }
            if (stateCleanupTask != null && !stateCleanupTask.isCancelled()) {
                stateCleanupTask.cancel();
            }

            // Save all online players
            saveAllPlayersOnShutdown();

            // Shutdown repository
            if (repository != null) {
                repository.shutdown();
            }

            //  Shutdown both executors
            ioExecutor.shutdown();
            saveExecutor.shutdown();

            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
                if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    saveExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                saveExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // Clear data structures
            onlinePlayers.clear();
            playerNameCache.clear();
            loadingPlayers.clear();
            playerStateLocks.clear();
            playerDataDirty.clear();
            lastSaveTime.clear();

            logger.info(" YakPlayerManager shutdown completed successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during  YakPlayerManager shutdown", e);
        } finally {
            initialized.set(false);
            repositoryReady.set(false);
        }
    }

    // Message formatting methods (unchanged but with  error handling)
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

    private void setQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer) {
        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();
            event.setQuitMessage(ChatColor.RED + "[-] " + formattedName);
        } else {
            event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + player.getName());
        }
    }

    /**
     *  ATOMIC operation execution with proper async chaining to prevent deadlocks
     */
    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation, boolean saveAfter) {
        if (uuid == null || operation == null) {
            return CompletableFuture.completedFuture(false);
        }

        //  Chain the operations properly instead of blocking
        return CompletableFuture.supplyAsync(() -> {
            ReentrantReadWriteLock playerLock = getPlayerStateLock(uuid);

            // Use appropriate lock based on whether we're saving
            if (saveAfter) {
                playerLock.writeLock().lock();
            } else {
                playerLock.readLock().lock();
            }

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

                    // Mark as dirty
                    AtomicBoolean dirty = playerDataDirty.get(uuid);
                    if (dirty != null) {
                        dirty.set(true);
                    }

                    return true; //  Return true immediately, don't wait for save

                } finally {
                    operationSemaphore.release();
                }

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error in withPlayer operation for " + uuid, e);
                return false;
            } finally {
                if (saveAfter) {
                    playerLock.writeLock().unlock();
                } else {
                    playerLock.readLock().unlock();
                }
            }
        }, ioExecutor).thenCompose(operationSuccess -> {
            //  Chain the save operation instead of blocking
            if (operationSuccess && saveAfter) {
                YakPlayer yakPlayer = getPlayer(uuid);
                if (yakPlayer != null) {
                    return savePlayerDataAsync(yakPlayer);
                }
            }
            return CompletableFuture.completedFuture(operationSuccess);
        });
    }

    public CompletableFuture<Boolean> withPlayer(UUID uuid, Consumer<YakPlayer> operation) {
        return withPlayer(uuid, operation, true);
    }

    // OPTIMIZED: Public API methods with fast read operations for tab menus
    public YakPlayer getPlayer(UUID uuid) {
        if (uuid == null) return null;
        // OPTIMIZED: Direct access for read operations - ConcurrentHashMap is thread-safe for reads
        return onlinePlayers.get(uuid);
    }

    public YakPlayer getPlayer(String name) {
        if (name == null) return null;
        UUID uuid = playerNameCache.get(name.toLowerCase());
        return uuid != null ? getPlayer(uuid) : null;
    }

    public YakPlayer getPlayer(Player player) {
        return player != null ? getPlayer(player.getUniqueId()) : null;
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
        logger.info("===  YakPlayerManager Performance Stats ===");
        logger.info("Online Players: " + onlinePlayers.size());
        logger.info("Loading Players: " + loadingPlayers.size());
        logger.info("Active Player Locks: " + playerStateLocks.size());
        logger.info("Total Joins: " + totalPlayerJoins.get());
        logger.info("Total Quits: " + totalPlayerQuits.get());
        logger.info("Successful Loads: " + successfulLoads.get());
        logger.info("Failed Loads: " + failedLoads.get());
        logger.info("Successful Saves: " + successfulSaves.get());
        logger.info("Failed Saves: " + failedSaves.get());
        logger.info("Operation Permits Available: " + operationSemaphore.availablePermits() + "/" + MAX_CONCURRENT_OPERATIONS);
        logger.info("IO Executor Active: " + ((ThreadPoolExecutor) ioExecutor).getActiveCount() + "/" + ((ThreadPoolExecutor) ioExecutor).getCorePoolSize());
        logger.info("Save Executor Active: " + ((ThreadPoolExecutor) saveExecutor).getActiveCount() + "/" + ((ThreadPoolExecutor) saveExecutor).getCorePoolSize());
        logger.info("Repository Ready: " + repositoryReady.get());
        logger.info("System Health: " + (systemHealthy.get() ? "HEALTHY" : "DEGRADED"));
        logger.info("Optimizations: Fast reads enabled, deadlock prevention active");
        logger.info("===================================================");
    }

    // OPTIMIZED:  convenience methods with fast read paths for tab menus
    public CompletableFuture<Boolean> addPlayerGems(UUID playerId, int amount) {
        return withPlayer(playerId, yakPlayer ->
                yakPlayer.setBankGems(yakPlayer.getBankGems() + amount));
    }

    public CompletableFuture<Boolean> removePlayerGems(UUID playerId, int amount) {
        return withPlayer(playerId, yakPlayer -> {
            if (yakPlayer.getBankGems() >= amount) {
                yakPlayer.setBankGems(yakPlayer.getBankGems() - amount);
            }
        });
    }

    // OPTIMIZED: Fast read methods for tab menus (no locking needed)
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
        return playerId != null && onlinePlayers.containsKey(playerId);
    }

    public boolean hasPlayer(String playerName) {
        return playerName != null && !playerName.trim().isEmpty() &&
                playerNameCache.containsKey(playerName.toLowerCase());
    }

    public boolean hasPlayer(Player player) {
        return player != null && player.isOnline() && onlinePlayers.containsKey(player.getUniqueId());
    }

    // OPTIMIZED: Fast read methods specifically for tab menu performance
    public Map<UUID, String> getAllPlayerDisplayNames() {
        Map<UUID, String> displayNames = new HashMap<>();
        for (Map.Entry<UUID, YakPlayer> entry : onlinePlayers.entrySet()) {
            YakPlayer player = entry.getValue();
            if (player != null) {
                displayNames.put(entry.getKey(), player.getFormattedDisplayName());
            }
        }
        return displayNames;
    }

    public Map<UUID, Integer> getAllPlayerLevels() {
        Map<UUID, Integer> levels = new HashMap<>();
        for (Map.Entry<UUID, YakPlayer> entry : onlinePlayers.entrySet()) {
            YakPlayer player = entry.getValue();
            if (player != null) {
                levels.put(entry.getKey(), player.getLevel());
            }
        }
        return levels;
    }

    public Map<UUID, Integer> getAllPlayerGems() {
        Map<UUID, Integer> gems = new HashMap<>();
        for (Map.Entry<UUID, YakPlayer> entry : onlinePlayers.entrySet()) {
            YakPlayer player = entry.getValue();
            if (player != null) {
                gems.put(entry.getKey(), player.getBankGems());
            }
        }
        return gems;
    }

    /**
     * Special method for combat logout processing
     */
    public YakPlayer getPlayerForCombatLogout(Player player) {
        if (player == null) return null;
        return onlinePlayers.get(player.getUniqueId());
    }

    /**
     * Mark a player as having combat logged for processing
     */
    public void markPlayerCombatLogged(UUID playerId) {
        YakPlayer yakPlayer = onlinePlayers.get(playerId);
        if (yakPlayer != null) {
            yakPlayer.setTemporaryData("combat_logout_processing", System.currentTimeMillis());
        }
    }
}
package com.rednetty.server.mechanics.player;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.limbo.LimboManager;
import lombok.Getter;
import org.bukkit.*;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIXED YakPlayerManager - properly coordinates with combat logout system
 *
 * CRITICAL FIXES:
 * - Now properly coordinates with CombatLogoutMechanics during player quit
 * - Delays player cleanup until combat logout processing is complete
 * - Uses MONITOR priority to run after all other quit handlers
 * - Provides access to player data for combat logout processing
 * - Maintains data integrity during combat logout scenarios
 * - CRITICAL FIX: Properly resets combat logout state to prevent infinite rejoin loop
 */
public class YakPlayerManager implements Listener, CommandExecutor {
    private static volatile YakPlayerManager instance;
    private static final Object INSTANCE_LOCK = new Object();

    private static final Logger logger = Logger.getLogger(YakPlayerManager.class.getName());

    // Configuration constants
    private static final long DATA_LOAD_TIMEOUT_MS = 15000L;
    private static final long LIMBO_CHECK_INTERVAL_TICKS = 5L;
    private static final long MAX_LIMBO_DURATION_TICKS = 600L;
    private static final int MAX_CONCURRENT_OPERATIONS = 10;

    // Player state management
    public enum PlayerState {
        OFFLINE, JOINING, LOADING, IN_LIMBO, READY,
        IN_COMBAT_LOGOUT, PROCESSING_DEATH, FAILED
    }

    // Loading phase enum
    public enum LoadingPhase {
        JOINING, IN_LIMBO, LOADING_DATA, APPLYING_INVENTORY,
        APPLYING_STATS, FINALIZING, TELEPORTING, COMPLETED, FAILED
    }

    // Core dependencies
    @Getter
    private YakPlayerRepository repository;
    private final Plugin plugin;
    private final LimboManager limboManager;

    // Player tracking
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerLoadingState> loadingStates = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final Set<UUID> protectedPlayers = ConcurrentHashMap.newKeySet();

    // FIXED: Combat logout coordination
    private final Set<UUID> playersInCombatLogoutProcessing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, YakPlayer> pendingCombatLogoutCleanup = new ConcurrentHashMap<>();

    // State management with locks
    private final Map<UUID, ReentrantReadWriteLock> playerStateLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStateChange = new ConcurrentHashMap<>();

    // Thread management
    private final ExecutorService ioExecutor;
    private final Semaphore operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS);

    // State management
    private volatile boolean initialized = false;
    private volatile boolean shutdownInProgress = false;
    private volatile boolean systemHealthy = false;
    private volatile boolean worldDetectionComplete = false;

    // Performance tracking
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger failedLoads = new AtomicInteger(0);
    private final AtomicInteger combatLogoutRejoins = new AtomicInteger(0);
    private final AtomicInteger combatLogoutCoordinations = new AtomicInteger(0);

    // Background tasks
    private BukkitTask autoSaveTask;
    private BukkitTask loadingMonitorTask;

    // Configuration
    private final long autoSaveInterval;
    private final int playersPerSaveCycle;

    // World management
    private volatile World voidLimboWorld;
    private final List<String> preferredWorldNames = Arrays.asList("world", "overworld", "main", "lobby", "spawn");

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.limboManager = LimboManager.getInstance();

        // Load configuration
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", 6000L);
        this.playersPerSaveCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", 10);

        // Initialize thread pool
        int ioThreads = plugin.getConfig().getInt("player_manager.io_threads", 4);
        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("FIXED YakPlayerManager initialized with combat logout coordination");
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
     * FIXED: Check if player is fully loaded for combat logout processing
     */
    public boolean isPlayerFullyLoaded(UUID playerId) {
        if (playerId == null) return false;
        PlayerState state = getPlayerState(playerId);
        return state == PlayerState.READY;
    }

    /**
     * FIXED: Get player for combat logout processing (won't return null during processing)
     */
    public YakPlayer getPlayerForCombatLogout(UUID playerId) {
        if (playerId == null) return null;

        // First try online players
        YakPlayer yakPlayer = onlinePlayers.get(playerId);
        if (yakPlayer != null) {
            return yakPlayer;
        }

        // If not found but in combat logout processing, try pending cleanup
        if (playersInCombatLogoutProcessing.contains(playerId)) {
            yakPlayer = pendingCombatLogoutCleanup.get(playerId);
            if (yakPlayer != null) {
                logger.info("Retrieved player from pending combat logout cleanup: " + yakPlayer.getUsername());
                return yakPlayer;
            }
        }

        return null;
    }

    /**
     * FIXED: Mark player as entering combat logout processing
     */
    public void markPlayerEnteringCombatLogout(UUID playerId) {
        if (playerId == null) return;

        playersInCombatLogoutProcessing.add(playerId);
        logger.info("Marked player as entering combat logout processing: " + playerId);

        // Move player data to pending cleanup to preserve it
        YakPlayer yakPlayer = onlinePlayers.get(playerId);
        if (yakPlayer != null) {
            pendingCombatLogoutCleanup.put(playerId, yakPlayer);
            logger.info("Moved player data to pending combat logout cleanup: " + yakPlayer.getUsername());
        }
    }

    /**
     * FIXED: Mark player as finished with combat logout processing
     */
    public void markPlayerFinishedCombatLogout(UUID playerId) {
        if (playerId == null) return;

        playersInCombatLogoutProcessing.remove(playerId);
        YakPlayer yakPlayer = pendingCombatLogoutCleanup.remove(playerId);

        if (yakPlayer != null) {
            logger.info("Completed combat logout processing cleanup for: " + yakPlayer.getUsername());
            combatLogoutCoordinations.incrementAndGet();
        }
    }

    /**
     * Initialize YakPlayerManager
     */
    public void onEnable() {
        if (initialized) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting FIXED YakPlayerManager...");

            if (!initializeRepository()) {
                logger.severe("Failed to initialize repository!");
                systemHealthy = false;
                return;
            }

            Bukkit.getServer().getPluginManager().registerEvents(this, plugin);
            initializeWorldDetection();

            if (voidLimboWorld != null) {
                limboManager.initialize(voidLimboWorld);
            }

            startBackgroundTasks();

            systemHealthy = true;
            initialized = true;

            logger.info("FIXED YakPlayerManager enabled successfully");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerManager", e);
            systemHealthy = false;
        }
    }

    private boolean initializeRepository() {
        try {
            logger.info("Initializing YakPlayerRepository...");
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

            logger.info("YakPlayerRepository initialized successfully");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error initializing repository", e);
            return false;
        }
    }

    private void initializeWorldDetection() {
        logger.info("Starting world detection...");

        // Try immediate detection
        List<World> availableWorlds = Bukkit.getWorlds();
        if (!availableWorlds.isEmpty()) {
            for (String worldName : preferredWorldNames) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    this.voidLimboWorld = world;
                    worldDetectionComplete = true;
                    logger.info("World detection SUCCESS - Using world: " + worldName);
                    return;
                }
            }

            // Use first available world as fallback
            this.voidLimboWorld = availableWorlds.get(0);
            worldDetectionComplete = true;
            logger.info("World detection SUCCESS - Using fallback world: " + availableWorlds.get(0).getName());
        } else {
            logger.warning("No worlds available during initialization");
        }
    }

    /**
     * Pre-login handler
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shutdownInProgress || !systemHealthy) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    ChatColor.RED + "Server is not ready. Please try again in a moment.");
            return;
        }

        UUID uuid = event.getUniqueId();
        String playerName = event.getName();

        try {
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(5, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer player = playerOpt.get();

                if (player.isBanned()) {
                    String banMessage = formatBanMessage(player);
                    if (banMessage != null) {
                        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage);
                        logger.info("Denied login for banned player: " + playerName);
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

    /**
     * CRITICAL FIX: Player join handler with proper combat logout rejoin detection and state reset
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerJoins.incrementAndGet();
        logger.info("FIXED: Player joining " + playerName + " (" + uuid + ")");

        // Check for duplicate processing
        if (loadingStates.containsKey(uuid) || onlinePlayers.containsKey(uuid)) {
            logger.warning("Duplicate join event for player: " + playerName + " - cleaning up old state");
            cleanupPlayerState(uuid, "duplicate_join");
        }

        // CRITICAL FIX: Check if this is a combat logout rejoin by checking player data
        try {
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(3, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer yakPlayer = playerOpt.get();

                // Check if player has active combat logout state
                YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();

                // CRITICAL FIX: Only treat as combat logout rejoin if state is PROCESSED
                // Don't treat COMPLETED state as combat logout rejoin (that's from a previous rejoin)
                boolean isCombatLogoutRejoin = (logoutState == YakPlayer.CombatLogoutState.PROCESSED);

                logger.info("Player " + playerName + " combat logout state: " + logoutState +
                        ", is rejoin: " + isCombatLogoutRejoin);

                if (isCombatLogoutRejoin) {
                    logger.info("Combat logout rejoin detected for " + playerName + " - processing completion");

                    // Suppress default join message for combat logout rejoins
                    event.setJoinMessage(null);

                    handleCombatLogoutRejoin(player, yakPlayer);
                    return;
                }

                // CRITICAL FIX: If player has COMPLETED state, reset it to NONE for normal processing
                if (logoutState == YakPlayer.CombatLogoutState.COMPLETED) {
                    logger.info("Resetting stale COMPLETED combat logout state to NONE for " + playerName);
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    repository.saveSync(yakPlayer);
                }
            } else {
                logger.info("No existing player data found for " + playerName + " - normal join");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking combat logout state for " + playerName, e);
            // Continue with normal join if check fails
        }

        // Normal join processing continues...
        logger.info("Processing normal join for " + playerName);

        // Set initial state for normal join
        if (!setPlayerState(uuid, PlayerState.JOINING, "player_join_event")) {
            logger.severe("Failed to set initial state for " + playerName);
            return;
        }

        // Create loading state
        PlayerLoadingState loadingState = new PlayerLoadingState(uuid, playerName);
        loadingStates.put(uuid, loadingState);
        protectedPlayers.add(uuid);

        // Start loading process
        performLimboTeleportation(player, loadingState);
    }

    /**
     * CRITICAL FIX: Handle combat logout rejoin with proper state reset
     */
    private void handleCombatLogoutRejoin(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        try {
            combatLogoutRejoins.incrementAndGet();
            logger.info("Processing combat logout rejoin for: " + playerName);

            // Set player state immediately
            setPlayerState(uuid, PlayerState.READY, "combat_logout_rejoin");

            // Connect the player data
            yakPlayer.connect(player);
            onlinePlayers.put(uuid, yakPlayer);

            // Apply their processed inventory (should contain only kept items)
            yakPlayer.applyInventory(player);
            yakPlayer.applyStats(player);

            // Initialize rank and chat systems
            initializeRankSystem(player, yakPlayer);
            initializeChatTagSystem(player, yakPlayer);

            // Teleport to spawn (they should already be at spawn from combat logout processing)
            World world = Bukkit.getWorlds().get(0);
            if (world != null) {
                Location spawnLocation = world.getSpawnLocation();
                player.teleport(spawnLocation);
                yakPlayer.updateLocation(spawnLocation);
            }

            // Clear combat logout data from CombatLogoutMechanics
            CombatLogoutMechanics combatLogout = CombatLogoutMechanics.getInstance();
            if (combatLogout != null) {
                combatLogout.handleCombatLogoutRejoin(uuid);
            }

            // CRITICAL FIX: Reset combat logout state to NONE immediately after rejoin
            // This prevents the system from treating every future login as a combat logout rejoin
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            logger.info("CRITICAL FIX: Reset combat logout state to NONE for " + playerName);

            // Save the player data with the reset state
            repository.saveSync(yakPlayer);
            logger.info("CRITICAL FIX: Saved player data with reset combat logout state for " + playerName);

            // Broadcast rejoin message
            Bukkit.broadcastMessage(ChatColor.GRAY + "⟨" + ChatColor.DARK_GRAY + "✧" + ChatColor.GRAY + "⟩ " +
                    yakPlayer.getFormattedDisplayName() + ChatColor.DARK_GRAY + " returned from combat logout");

            // Send completion message with delay
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT CONSEQUENCES COMPLETED");
                    player.sendMessage(ChatColor.GRAY + "Your items were processed according to your " +
                            yakPlayer.getAlignment().toLowerCase() + " alignment.");
                    player.sendMessage(ChatColor.GRAY + "You have respawned at the spawn location.");
                    player.sendMessage(ChatColor.YELLOW + "You may now continue playing normally.");
                    player.sendMessage("");
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }, 20L);

            // Send MOTD if available
            if (PlayerListenerManager.getInstance() != null &&
                    PlayerListenerManager.getInstance().getJoinLeaveListener() != null) {
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (player.isOnline()) {
                        PlayerListenerManager.getInstance().getJoinLeaveListener()
                                .sendMotd(player, yakPlayer, true); // true = combat logout rejoin
                    }
                }, 40L);
            }

            logger.info("Combat logout rejoin completed for: " + playerName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling combat logout rejoin for " + playerName, e);

            // Fallback to normal loading if combat logout rejoin fails
            logger.warning("Combat logout rejoin failed, falling back to normal loading for: " + playerName);

            // Clean up and start normal loading
            onlinePlayers.remove(uuid);

            // CRITICAL FIX: Reset state to NONE even on error to prevent infinite loop
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            repository.saveSync(yakPlayer);
            logger.info("CRITICAL FIX: Reset combat logout state to NONE on error for " + playerName);

            // Start normal loading process
            if (!setPlayerState(uuid, PlayerState.JOINING, "combat_logout_rejoin_fallback")) {
                logger.severe("Failed to set fallback state for " + playerName);
                return;
            }

            PlayerLoadingState loadingState = new PlayerLoadingState(uuid, playerName);
            loadingStates.put(uuid, loadingState);
            protectedPlayers.add(uuid);
            performLimboTeleportation(player, loadingState);
        }
    }

    /**
     * Perform limbo teleportation for normal loading
     */
    private void performLimboTeleportation(Player player, PlayerLoadingState loadingState) {
        try {
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            logger.info("Starting limbo teleportation for " + playerName);

            loadingState.storeOriginalState(player);
            loadingState.setPhase(LoadingPhase.JOINING);

            limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.JOINING);

            World targetWorld = voidLimboWorld;
            if (targetWorld == null) {
                logger.severe("No usable world available for " + playerName + " - using emergency fallback");
                targetWorld = Bukkit.getWorlds().get(0);
            }

            logger.info("Using world '" + targetWorld.getName() + "' for " + playerName);

            Location limboLocation = limboManager.createLimboLocation(targetWorld);
            if (limboLocation == null) {
                limboLocation = new Location(targetWorld, 0.5, -64.0, 0.5);
            }

            boolean teleportSuccess = player.teleport(limboLocation);
            logger.info("Teleportation result for " + playerName + ": " + teleportSuccess);

            // Complete limbo setup regardless of teleport result (force success)
            completeLimboSetup(player, loadingState, uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during teleportation for " + player.getName(), e);
            UUID uuid = player.getUniqueId();
            completeLimboSetup(player, loadingState, uuid);
        }
    }

    private void completeLimboSetup(Player player, PlayerLoadingState loadingState, UUID uuid) {
        setPlayerState(uuid, PlayerState.IN_LIMBO, "successful_teleportation");
        limboManager.addPlayerToLimbo(uuid);
        limboManager.applyLimboState(player);

        loadingState.setPhase(LoadingPhase.IN_LIMBO);
        loadingState.setLimboStartTime(System.currentTimeMillis());

        limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.IN_LIMBO);
        limboManager.sendLimboWelcome(player);

        startDataLoadingInLimbo(player, loadingState);
        startLoadingMonitor(player, loadingState);
    }

    /**
     * Start data loading in limbo
     */
    private void startDataLoadingInLimbo(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();
        setPlayerState(uuid, PlayerState.LOADING, "starting_data_load");
        loadingState.setPhase(LoadingPhase.LOADING_DATA);

        limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.LOADING_DATA);

        CompletableFuture.supplyAsync(() -> {
            try {
                if (!operationSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Operation semaphore timeout");
                }

                try {
                    logger.info("Loading player data for: " + player.getName());

                    CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(uuid);
                    Optional<YakPlayer> existingPlayer = repositoryFuture.get(DATA_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                    YakPlayer yakPlayer;

                    if (existingPlayer.isPresent()) {
                        yakPlayer = existingPlayer.get();
                        yakPlayer.connect(player);
                        logger.info("Loaded existing player data: " + player.getName());
                    } else {
                        yakPlayer = new YakPlayer(player);
                        logger.info("Created new player data: " + player.getName());
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
        }, ioExecutor).whenComplete((yakPlayer, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    handleLoadingFailure(player, loadingState, error);
                } else if (yakPlayer != null) {
                    continueLoadingInLimbo(player, loadingState, yakPlayer);
                } else {
                    handleLoadingFailure(player, loadingState, new RuntimeException("Null player data"));
                }
            });
        });
    }

    /**
     * Continue loading process after data is loaded
     */
    private void continueLoadingInLimbo(Player player, PlayerLoadingState loadingState, YakPlayer yakPlayer) {
        if (!player.isOnline()) {
            cleanupLoadingState(player.getUniqueId());
            return;
        }

        UUID uuid = player.getUniqueId();
        onlinePlayers.put(uuid, yakPlayer);
        loadingState.setYakPlayer(yakPlayer);

        // Clear any residual combat logout state for normal loading
        if (yakPlayer.getCombatLogoutState() != YakPlayer.CombatLogoutState.NONE) {
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
        }

        applyDataWhileInLimbo(player, yakPlayer, loadingState);
    }

    private void applyDataWhileInLimbo(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        if (!yakPlayer.hasTemporaryData("energy")) {
            yakPlayer.setTemporaryData("energy", 100);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || getPlayerState(uuid) == PlayerState.FAILED) return;

            loadingState.setPhase(LoadingPhase.APPLYING_INVENTORY);
            limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.APPLYING_INVENTORY);
            applyInventoryInLimbo(player, yakPlayer, loadingState);
        }, 5L);
    }

    private void applyInventoryInLimbo(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Applying inventory in limbo: " + player.getName());
            yakPlayer.applyInventory(player);

            player.sendTitle(
                    ChatColor.AQUA + "" + ChatColor.BOLD + "✦ Recovering Inventory.. ✦",
                    ChatColor.GRAY + "Items reconstructed from quantum data",
                    10, 30, 15
            );

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || getPlayerState(uuid) == PlayerState.FAILED) return;

                loadingState.setPhase(LoadingPhase.APPLYING_STATS);
                limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.APPLYING_STATS);
                applyStatsInLimbo(player, yakPlayer, loadingState);
            }, 20L);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying inventory in limbo: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private void applyStatsInLimbo(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Applying stats in limbo: " + player.getName());

            yakPlayer.applyStats(player, true);

            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
                player.setFlying(true);
                player.setAllowFlight(true);
                player.setInvulnerable(true);
            }

            player.setExp(1.0f);
            player.setLevel(100);

            player.sendTitle(
                    ChatColor.GOLD + "" + ChatColor.BOLD + "⚡ Applying Stats.. ⚡",
                    ChatColor.YELLOW + "Your abilities have been restored",
                    10, 30, 15
            );

            player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 1.5f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || getPlayerState(uuid) == PlayerState.FAILED) return;

                loadingState.setPhase(LoadingPhase.FINALIZING);
                limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.FINALIZING);
                finalizeInLimbo(player, yakPlayer, loadingState);
            }, 20L);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying stats in limbo: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private void finalizeInLimbo(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Finalizing in limbo: " + player.getName());

            initializeRankSystem(player, yakPlayer);
            initializeChatTagSystem(player, yakPlayer);

            player.sendTitle(
                    ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "◆ Synchronizing.. ◆",
                    ChatColor.GRAY + "Systems calibrated and ready",
                    10, 30, 15
            );

            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.0f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || getPlayerState(uuid) == PlayerState.FAILED) return;

                loadingState.setPhase(LoadingPhase.TELEPORTING);
                limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.TELEPORTING);
                teleportToFinalLocation(player, yakPlayer, loadingState);
            }, 20L);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error finalizing in limbo: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private void teleportToFinalLocation(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Teleporting to final location: " + player.getName());

            Location savedLocation = yakPlayer.getLocation();
            Location teleportLocation;
            boolean isNewPlayer = isNewPlayer(yakPlayer);

            if (isNewPlayer || savedLocation == null || savedLocation.getWorld() == null) {
                World defaultWorld = voidLimboWorld;
                if (defaultWorld != null) {
                    teleportLocation = defaultWorld.getSpawnLocation();
                    yakPlayer.updateLocation(teleportLocation);
                    logger.info("Teleporting " + player.getName() + " to spawn");
                } else {
                    throw new RuntimeException("No world available for spawn teleportation");
                }
            } else {
                teleportLocation = savedLocation;
                logger.info("Teleporting " + player.getName() + " to saved location");
            }

            player.sendTitle(
                    ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "⟨ Crossing the Veil ⟩",
                    ChatColor.GRAY + "Entering YakRealms...",
                    10, 40, 20
            );

            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

            restorePlayerState(player, yakPlayer);

            boolean teleportSuccess = player.teleport(teleportLocation);
            if (!teleportSuccess) {
                logger.warning("Failed to teleport " + player.getName() + " to final location");
                World defaultWorld = voidLimboWorld;
                if (defaultWorld != null) {
                    teleportLocation = defaultWorld.getSpawnLocation();
                    player.teleport(teleportLocation);
                    yakPlayer.updateLocation(teleportLocation);
                }
            }

            limboManager.removePlayerFromLimbo(uuid);
            setPlayerState(uuid, PlayerState.READY, "loading_completed");
            loadingState.setPhase(LoadingPhase.COMPLETED);

            limboManager.showCompletionCelebration(player);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                completePlayerLoading(player, loadingState);
            }, 10L);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in final teleportation: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private boolean isNewPlayer(YakPlayer yakPlayer) {
        try {
            long twoHoursAgo = (System.currentTimeMillis() / 1000) - 7200;
            return yakPlayer.getFirstJoin() > twoHoursAgo;
        } catch (Exception e) {
            return true;
        }
    }

    private void restorePlayerState(Player player, YakPlayer yakPlayer) {
        try {
            String savedGameMode = yakPlayer.getGameMode();
            GameMode targetMode = GameMode.SURVIVAL;

            if (savedGameMode != null) {
                try {
                    targetMode = GameMode.valueOf(savedGameMode);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid saved game mode: " + savedGameMode);
                }
            }

            player.setInvulnerable(false);
            player.setGameMode(targetMode);

            if (targetMode == GameMode.CREATIVE || targetMode == GameMode.SPECTATOR) {
                player.setAllowFlight(true);
            } else {
                player.setAllowFlight(false);
                player.setFlying(false);
            }

            logger.info("Restored " + player.getName() + " to " + targetMode + " mode");

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error restoring player state: " + player.getName(), e);
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(false);
        }
    }

    private void completePlayerLoading(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            long loadingTime = System.currentTimeMillis() - loadingState.getLimboStartTime();
            logger.info("Loading completed for " + player.getName() + " in " + loadingTime + "ms");

            updateJoinMessage(player, loadingState.getYakPlayer());
            cleanupLoadingState(uuid);

            if (PlayerListenerManager.getInstance() != null &&
                    PlayerListenerManager.getInstance().getJoinLeaveListener() != null) {
                PlayerListenerManager.getInstance().getJoinLeaveListener()
                        .sendMotd(player, getPlayer(uuid), false);
            }

            logger.info("Player fully loaded and ready: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error completing player loading: " + player.getName(), e);
        }
    }

    /**
     * Handle loading failure
     */
    private void handleLoadingFailure(Player player, PlayerLoadingState loadingState, Throwable error) {
        UUID uuid = player.getUniqueId();

        try {
            setPlayerState(uuid, PlayerState.FAILED, "loading_failure");
            loadingState.setPhase(LoadingPhase.FAILED);
            logger.log(Level.SEVERE, "Loading failed for: " + player.getName(), error);

            limboManager.removePlayerFromLimbo(uuid);

            if (player.isOnline()) {
                // Perform emergency recovery
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
                player.setFlying(false);
                player.setAllowFlight(false);

                World world = Bukkit.getWorlds().get(0);
                if (world != null) {
                    player.teleport(world.getSpawnLocation());
                }

                player.sendMessage(ChatColor.GREEN + "Emergency recovery completed - you are now safe.");
            }

            cleanupLoadingState(uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling loading failure: " + player.getName(), e);
        }
    }

    /**
     * Start loading monitor
     */
    private void startLoadingMonitor(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        BukkitTask monitorTask = new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !loadingStates.containsKey(uuid)) {
                    this.cancel();
                    return;
                }

                ticksElapsed++;
                LoadingPhase currentPhase = loadingState.getPhase();

                if (currentPhase == LoadingPhase.COMPLETED) {
                    this.cancel();
                    return;
                }

                if (limboManager.isPlayerInLimbo(uuid)) {
                    limboManager.maintainLimboState(player);
                }

                if (currentPhase == LoadingPhase.FAILED || ticksElapsed >= MAX_LIMBO_DURATION_TICKS) {
                    if (ticksElapsed >= MAX_LIMBO_DURATION_TICKS) {
                        logger.severe("Loading timeout for " + player.getName());
                    }
                    handleLoadingFailure(player, loadingState, new RuntimeException("Loading timeout"));
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, LIMBO_CHECK_INTERVAL_TICKS);

        loadingState.setMonitorTask(monitorTask);
    }

    /**
     * Player movement handler for limbo
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (limboManager.isPlayerInLimbo(playerId)) {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null) {
                event.setTo(from);
                limboManager.maintainLimboState(player);
            }
        }
    }

    /**
     * FIXED: Player quit handler with proper combat logout coordination and state reset
     *
     * CRITICAL FIX: Now uses MONITOR priority to run AFTER CombatLogoutMechanics
     * and coordinates cleanup timing for combat logout processing
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        logger.info("FIXED: Player quitting: " + playerName);

        try {
            // FIXED: Check if player is in combat logout processing
            if (playersInCombatLogoutProcessing.contains(uuid)) {
                logger.info("Player " + playerName + " is in combat logout processing - coordinating cleanup");
                handleCombatLogoutPlayerQuit(player, uuid, playerName);
                return;
            }

            // Normal quit processing
            handleNormalPlayerQuit(player, uuid, playerName, event);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in FIXED player quit for " + playerName, e);

            // Emergency cleanup
            try {
                cleanupPlayerState(uuid, "emergency_quit_cleanup");
                onlinePlayers.remove(uuid);
                markPlayerFinishedCombatLogout(uuid);
            } catch (Exception emergencyError) {
                logger.log(Level.SEVERE, "Emergency cleanup failed for " + playerName, emergencyError);
            }
        }
    }

    /**
     * FIXED: Handle quit for player in combat logout processing
     */
    private void handleCombatLogoutPlayerQuit(Player player, UUID uuid, String playerName) {
        try {
            logger.info("Handling combat logout quit for: " + playerName);

            // Don't clean up player state yet - CombatLogoutMechanics needs the data
            // Just mark them as offline but keep data available for processing
            setPlayerState(uuid, PlayerState.OFFLINE, "combat_logout_quit");

            // The player data is kept in pendingCombatLogoutCleanup for access by CombatLogoutMechanics
            // It will be cleaned up when markPlayerFinishedCombatLogout is called

            logger.info("Combat logout quit handling complete for: " + playerName + " (data preserved for processing)");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling combat logout quit for " + playerName, e);
            // Force cleanup on error
            markPlayerFinishedCombatLogout(uuid);
        }
    }

    /**
     * CRITICAL FIX: Handle normal player quit with proper combat logout state reset
     */
    private void handleNormalPlayerQuit(Player player, UUID uuid, String playerName, PlayerQuitEvent event) {
        logger.info("Handling normal quit for: " + playerName);

        // Normal cleanup process
        cleanupPlayerState(uuid, "normal_player_quit");

        YakPlayer yakPlayer = onlinePlayers.remove(uuid);
        if (yakPlayer == null) {
            logger.warning("No player data found for quitting player: " + playerName);
            return;
        }

        try {
            // Update inventory if not protected
            if (!protectedPlayers.contains(uuid) && !limboManager.isPlayerInLimbo(uuid)) {
                yakPlayer.updateInventory(player);
            }

            // CRITICAL FIX: Always reset combat logout state to NONE on normal quit
            // This ensures the player won't be treated as a combat logout rejoin on their next login
            YakPlayer.CombatLogoutState currentState = yakPlayer.getCombatLogoutState();
            if (currentState != YakPlayer.CombatLogoutState.NONE) {
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                logger.info("CRITICAL FIX: Reset combat logout state from " + currentState + " to NONE for normal quit: " + playerName);
            }

            // Disconnect and save
            yakPlayer.disconnect();
            repository.saveSync(yakPlayer);

            // Set quit message
            setQuitMessage(event, player, yakPlayer);

            logger.info("Normal quit processing complete for: " + playerName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in normal quit processing for " + playerName, e);
        }
    }

    private void cleanupPlayerState(UUID uuid, String reason) {
        try {
            PlayerLoadingState loadingState = loadingStates.remove(uuid);
            if (loadingState != null) {
                BukkitTask monitorTask = loadingState.getMonitorTask();
                if (monitorTask != null && !monitorTask.isCancelled()) {
                    monitorTask.cancel();
                }
            }

            protectedPlayers.remove(uuid);
            limboManager.removePlayerFromLimbo(uuid);
            setPlayerState(uuid, PlayerState.OFFLINE, reason);

            ModerationMechanics.rankMap.remove(uuid);
            ChatMechanics.getPlayerTags().remove(uuid);

            logger.fine("Cleaned up player state for " + uuid + " (reason: " + reason + ")");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up player state for " + uuid, e);
        }
    }

    private void cleanupLoadingState(UUID uuid) {
        cleanupPlayerState(uuid, "loading_completed");
    }

    private void initializeRankSystem(Player player, YakPlayer yakPlayer) {
        try {
            String rankString = yakPlayer.getRank();
            if (rankString == null || rankString.trim().isEmpty()) {
                rankString = "default";
                yakPlayer.setRank("default");
            }

            Rank rank = Rank.fromString(rankString);
            ModerationMechanics.rankMap.put(player.getUniqueId(), rank);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing rank: " + player.getName(), e);
            ModerationMechanics.rankMap.put(player.getUniqueId(), Rank.DEFAULT);
            yakPlayer.setRank("default");
            repository.saveSync(yakPlayer);
        }
    }

    private void initializeChatTagSystem(Player player, YakPlayer yakPlayer) {
        try {
            String chatTagString = yakPlayer.getChatTag();
            if (chatTagString == null || chatTagString.trim().isEmpty()) {
                chatTagString = "DEFAULT";
                yakPlayer.setChatTag("DEFAULT");
            }

            ChatTag tag = ChatTag.valueOf(chatTagString);
            ChatMechanics.getPlayerTags().put(player.getUniqueId(), tag);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error initializing chat tag: " + player.getName(), e);
            ChatMechanics.getPlayerTags().put(player.getUniqueId(), ChatTag.DEFAULT);
            yakPlayer.setChatTag("DEFAULT");
        }
    }

    /**
     * Start background tasks
     */
    private void startBackgroundTasks() {
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress) {
                    performAutoSave();
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        loadingMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupStaleLoadingStates();
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);

        logger.info("Background tasks started");
    }

    private void performAutoSave() {
        List<YakPlayer> playersToSave = new ArrayList<>();
        int count = 0;

        for (YakPlayer player : onlinePlayers.values()) {
            if (count >= playersPerSaveCycle) break;

            Player bukkitPlayer = player.getBukkitPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                UUID uuid = bukkitPlayer.getUniqueId();

                if (!protectedPlayers.contains(uuid) &&
                        !limboManager.isPlayerInLimbo(uuid) &&
                        !playersInCombatLogoutProcessing.contains(uuid) &&
                        getPlayerState(uuid) == PlayerState.READY) {
                    player.updateInventory(bukkitPlayer);
                    playersToSave.add(player);
                    count++;
                }
            }
        }

        for (YakPlayer player : playersToSave) {
            try {
                repository.saveSync(player);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Auto-save failed: " + player.getUsername(), e);
            }
        }
    }

    private void cleanupStaleLoadingStates() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PlayerLoadingState>> iterator = loadingStates.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerLoadingState> entry = iterator.next();
            UUID uuid = entry.getKey();
            PlayerLoadingState state = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                cleanupPlayerState(uuid, "player_offline");

                BukkitTask task = state.getMonitorTask();
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                continue;
            }

            long stateAge = now - state.getLimboStartTime();
            if (stateAge > 120000) {
                logger.warning("Cleaning up stale loading state: " + state.getPlayerName());
                handleLoadingFailure(player, state, new RuntimeException("Stale loading state"));
                iterator.remove();
            }
        }
    }

    /**
     * Save player with coordination
     */
    public CompletableFuture<Boolean> savePlayer(YakPlayer yakPlayer) {
        if (yakPlayer == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Player bukkitPlayer = yakPlayer.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    UUID uuid = bukkitPlayer.getUniqueId();

                    if (getPlayerState(uuid) == PlayerState.READY &&
                            !playersInCombatLogoutProcessing.contains(uuid)) {
                        yakPlayer.updateInventory(bukkitPlayer);
                    }
                }

                YakPlayer result = repository.saveSync(yakPlayer);
                return result != null;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player: " + yakPlayer.getUsername(), e);
                return false;
            }
        }, ioExecutor);
    }

    /**
     * CRITICAL FIX: Manual combat logout state reset methods
     */
    public boolean resetCombatLogoutState(UUID playerId) {
        try {
            // Try to get online player first
            YakPlayer yakPlayer = getPlayer(playerId);

            if (yakPlayer != null) {
                // Player is online
                YakPlayer.CombatLogoutState currentState = yakPlayer.getCombatLogoutState();
                if (currentState != YakPlayer.CombatLogoutState.NONE) {
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    savePlayer(yakPlayer);
                    logger.info("MANUAL RESET: Reset online player " + yakPlayer.getUsername() +
                            " combat logout state from " + currentState + " to NONE");
                    return true;
                } else {
                    logger.info("MANUAL RESET: Player " + yakPlayer.getUsername() + " already has NONE state");
                    return true;
                }
            } else {
                // Player is offline, load from database
                CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(playerId);
                Optional<YakPlayer> playerOpt = playerFuture.get(5, TimeUnit.SECONDS);

                if (playerOpt.isPresent()) {
                    YakPlayer offlinePlayer = playerOpt.get();
                    YakPlayer.CombatLogoutState currentState = offlinePlayer.getCombatLogoutState();

                    if (currentState != YakPlayer.CombatLogoutState.NONE) {
                        offlinePlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                        repository.saveSync(offlinePlayer);
                        logger.info("MANUAL RESET: Reset offline player " + offlinePlayer.getUsername() +
                                " combat logout state from " + currentState + " to NONE");
                        return true;
                    } else {
                        logger.info("MANUAL RESET: Offline player " + offlinePlayer.getUsername() + " already has NONE state");
                        return true;
                    }
                } else {
                    logger.warning("MANUAL RESET: No player data found for " + playerId);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "MANUAL RESET: Error resetting combat logout state for " + playerId, e);
            return false;
        }
    }

    public boolean resetCombatLogoutState(String playerName) {
        try {
            // Try online players first
            YakPlayer yakPlayer = getPlayer(playerName);
            if (yakPlayer != null) {
                return resetCombatLogoutState(yakPlayer.getUUID());
            }

            logger.warning("MANUAL RESET: Player " + playerName + " not online, need UUID for offline reset");
            return false;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "MANUAL RESET: Error resetting combat logout state for " + playerName, e);
            return false;
        }
    }

    /**
     * State management methods
     */
    private ReentrantReadWriteLock getPlayerStateLock(UUID playerId) {
        if (playerId == null) {
            logger.warning("Attempted to get state lock for null UUID");
            return new ReentrantReadWriteLock();
        }
        return playerStateLocks.computeIfAbsent(playerId, k -> new ReentrantReadWriteLock());
    }

    public PlayerState getPlayerState(UUID playerId) {
        if (playerId == null) return PlayerState.OFFLINE;
        ReentrantReadWriteLock lock = getPlayerStateLock(playerId);
        lock.readLock().lock();
        try {
            return playerStates.getOrDefault(playerId, PlayerState.OFFLINE);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean setPlayerState(UUID playerId, PlayerState newState, String reason) {
        if (playerId == null || newState == null) {
            logger.warning("Cannot set player state with null parameters");
            return false;
        }

        ReentrantReadWriteLock lock = getPlayerStateLock(playerId);
        lock.writeLock().lock();
        try {
            PlayerState currentState = playerStates.get(playerId);
            playerStates.put(playerId, newState);
            lastStateChange.put(playerId, System.currentTimeMillis());

            logger.fine("State transition for " + playerId + ": " + currentState + " -> " + newState +
                    " (" + reason + ")");
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Shutdown
     */
    public void onDisable() {
        if (shutdownInProgress) return;

        shutdownInProgress = true;
        logger.info("Starting FIXED YakPlayerManager shutdown...");

        try {
            if (autoSaveTask != null && !autoSaveTask.isCancelled()) autoSaveTask.cancel();
            if (loadingMonitorTask != null && !loadingMonitorTask.isCancelled()) loadingMonitorTask.cancel();

            for (PlayerLoadingState state : loadingStates.values()) {
                BukkitTask task = state.getMonitorTask();
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
            }

            if (limboManager.isInitialized()) {
                limboManager.shutdown();
            }

            saveAllPlayersOnShutdown();

            if (repository != null) {
                repository.shutdown();
            }

            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            onlinePlayers.clear();
            loadingStates.clear();
            playerStates.clear();
            protectedPlayers.clear();
            playerStateLocks.clear();
            lastStateChange.clear();
            playersInCombatLogoutProcessing.clear();
            pendingCombatLogoutCleanup.clear();

            logger.info("FIXED YakPlayerManager shutdown completed");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
        } finally {
            initialized = false;
        }
    }

    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        int savedCount = 0;
        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            Player player = yakPlayer.getBukkitPlayer();

            if (player != null && player.isOnline()) {
                UUID uuid = player.getUniqueId();
                if (getPlayerState(uuid) == PlayerState.READY) {
                    yakPlayer.updateInventory(player);
                }
            }

            yakPlayer.disconnect();

            try {
                repository.saveSync(yakPlayer);
                savedCount++;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player on shutdown: " + yakPlayer.getUsername(), e);
            }
        }

        logger.info("Shutdown save completed: " + savedCount + "/" + onlinePlayers.size() + " players saved");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("playerstate")) {
            if (!player.hasPermission("yakrealms.admin")) {
                player.sendMessage(ChatColor.RED + "No permission");
                return true;
            }

            UUID uuid = player.getUniqueId();
            PlayerState state = getPlayerState(uuid);
            LoadingPhase phase = null;

            PlayerLoadingState loadingState = loadingStates.get(uuid);
            if (loadingState != null) {
                phase = loadingState.getPhase();
            }

            player.sendMessage(ChatColor.YELLOW + "=== FIXED PLAYER STATE DEBUG ===");
            player.sendMessage(ChatColor.YELLOW + "State: " + state);
            player.sendMessage(ChatColor.YELLOW + "Loading Phase: " + phase);
            player.sendMessage(ChatColor.YELLOW + "In Limbo: " + limboManager.isPlayerInLimbo(uuid));
            player.sendMessage(ChatColor.YELLOW + "Protected: " + protectedPlayers.contains(uuid));
            player.sendMessage(ChatColor.YELLOW + "Combat Logout Rejoins: " + combatLogoutRejoins.get());
            player.sendMessage(ChatColor.YELLOW + "Combat Logout Processing: " + playersInCombatLogoutProcessing.contains(uuid));
            player.sendMessage(ChatColor.YELLOW + "Combat Logout Coordinations: " + combatLogoutCoordinations.get());

            // Show combat logout state
            YakPlayer yakPlayer = getPlayer(uuid);
            if (yakPlayer != null) {
                player.sendMessage(ChatColor.YELLOW + "Combat Logout State: " + yakPlayer.getCombatLogoutState());
            }

            player.sendMessage(ChatColor.YELLOW + "========================================");

            return true;
        }

        // CRITICAL FIX: Manual reset command
        if (command.getName().equalsIgnoreCase("resetcombatlogout")) {
            if (!player.hasPermission("yakrealms.admin")) {
                player.sendMessage(ChatColor.RED + "No permission");
                return true;
            }

            if (args.length == 0) {
                // Reset self
                boolean success = resetCombatLogoutState(player.getUniqueId());
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "Reset your combat logout state to NONE");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to reset your combat logout state");
                }
            } else {
                // Reset specified player
                String targetName = args[0];
                boolean success = resetCombatLogoutState(targetName);
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "Reset " + targetName + "'s combat logout state to NONE");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to reset " + targetName + "'s combat logout state");
                }
            }

            return true;
        }

        return false;
    }

    // Public API methods
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

    public boolean isPlayerProtected(UUID uuid) {
        return protectedPlayers.contains(uuid) || getPlayerState(uuid) != PlayerState.READY;
    }

    public boolean isPlayerLoading(UUID uuid) {
        PlayerState state = getPlayerState(uuid);
        return state == PlayerState.JOINING || state == PlayerState.LOADING || state == PlayerState.IN_LIMBO;
    }

    public boolean isPlayerInVoidLimbo(UUID uuid) {
        return limboManager.isPlayerInLimbo(uuid);
    }

    public LoadingPhase getPlayerLoadingPhase(UUID uuid) {
        PlayerLoadingState state = loadingStates.get(uuid);
        return state != null ? state.getPhase() : null;
    }

    public boolean isSystemHealthy() {
        return systemHealthy;
    }

    public boolean isRepositoryReady() {
        return repository != null && repository.isInitialized();
    }

    public boolean isShuttingDown() {
        return shutdownInProgress;
    }

    // Message formatting methods
    private String formatBanMessage(YakPlayer player) {
        if (!player.isBanned()) return null;

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append(ChatColor.BOLD).append("You are banned from this server!\n\n");
        message.append(ChatColor.GRAY).append("Reason: ").append(ChatColor.WHITE).append(player.getBanReason()).append("\n");

        if (player.getBanExpiry() > 0) {
            long remaining = player.getBanExpiry() - Instant.now().getEpochSecond();
            if (remaining > 0) {
                message.append(ChatColor.GRAY).append("Expires in: ").append(ChatColor.WHITE).append(formatDuration(remaining));
            } else {
                player.setBanned(false);
                player.setBanReason("");
                player.setBanExpiry(0);
                repository.saveSync(player);
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

    private void updateJoinMessage(Player player, YakPlayer yakPlayer) {
        String formattedName = yakPlayer.getFormattedDisplayName();
        int onlineCount = Bukkit.getOnlinePlayers().size();

        String joinMessage;
        if (onlineCount == 1) {
            joinMessage = ChatColor.GOLD + "✦ " + ChatColor.BOLD + formattedName +
                    ChatColor.GOLD + " has entered the realm! ✦";
        } else if (onlineCount <= 5) {
            joinMessage = ChatColor.AQUA + "⟨" + ChatColor.LIGHT_PURPLE + "✦" + ChatColor.AQUA + "⟩ " + formattedName +
                    ChatColor.GRAY + " (" + onlineCount + " online)";
        } else if (onlineCount <= 20) {
            joinMessage = ChatColor.AQUA + "⟨" + ChatColor.LIGHT_PURPLE + "✦" + ChatColor.AQUA + "⟩ " + formattedName;
        } else {
            joinMessage = null;
        }

        if (joinMessage != null) {
            Bukkit.broadcastMessage(joinMessage);
        }
    }

    private void setQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer) {
        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();
            event.setQuitMessage(ChatColor.RED + "⟨" + ChatColor.DARK_RED + "✧" + ChatColor.RED + "⟩ " + formattedName);
        } else {
            event.setQuitMessage(ChatColor.RED + "⟨" + ChatColor.DARK_RED + "✧" + ChatColor.RED + "⟩ " +
                    ChatColor.GRAY + player.getName());
        }
    }

    // Convenience methods
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

    // Player loading state tracker
    private static class PlayerLoadingState {
        private final UUID playerId;
        private final String playerName;
        private long limboStartTime;
        private volatile LoadingPhase phase = LoadingPhase.JOINING;
        private volatile YakPlayer yakPlayer;
        private volatile BukkitTask monitorTask;

        private Location originalLocation;
        private GameMode originalGameMode;

        public PlayerLoadingState(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.limboStartTime = System.currentTimeMillis();
        }

        public void storeOriginalState(Player player) {
            this.originalLocation = player.getLocation().clone();
            this.originalGameMode = player.getGameMode();
        }

        // Getters and setters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getLimboStartTime() { return limboStartTime; }
        public LoadingPhase getPhase() { return phase; }

        public void setLimboStartTime(long limboStartTime) {
            this.limboStartTime = limboStartTime;
        }

        public void setPhase(LoadingPhase phase) { this.phase = phase; }
        public YakPlayer getYakPlayer() { return yakPlayer; }
        public void setYakPlayer(YakPlayer yakPlayer) { this.yakPlayer = yakPlayer; }
        public BukkitTask getMonitorTask() { return monitorTask; }
        public void setMonitorTask(BukkitTask monitorTask) { this.monitorTask = monitorTask; }
        public Location getOriginalLocation() { return originalLocation; }
        public GameMode getOriginalGameMode() { return originalGameMode; }
    }
}
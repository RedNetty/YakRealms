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
 * COMPLETE UPDATED YakPlayerManager with COMPREHENSIVE SETTINGS PROTECTION
 *
 * MAJOR UPDATES:
 * - Enhanced settings protection system that preserves toggle changes during loading
 * - Immediate saving mechanism regardless of player protection status
 * - Pending changes system for settings made during loading states
 * - Enhanced coordination with combat logout system
 * - Bulletproof data integrity during all scenarios
 * - Recovery and repair mechanisms for corrupted settings
 * - Comprehensive debugging and monitoring tools
 * - Enhanced error handling and state management
 *
 * SETTINGS PROTECTION FEATURES:
 * - saveToggleSettingImmediate() - Immediate save regardless of state
 * - applyPendingSettingsChanges() - Apply changes after loading completes
 * - forceSettingsSave() - Emergency recovery method
 * - getPendingSettings() - Debug and monitoring support
 */
public class YakPlayerManager implements Listener, CommandExecutor {

    // ===============================================
    // CONSTANTS & CONFIGURATION
    // ===============================================

    private static final Logger logger = Logger.getLogger(YakPlayerManager.class.getName());
    private static final Object INSTANCE_LOCK = new Object();

    // Timing constants
    private static final long DATA_LOAD_TIMEOUT_MS = 15000L;
    private static final long LIMBO_CHECK_INTERVAL_TICKS = 5L;
    private static final long MAX_LIMBO_DURATION_TICKS = 600L;
    private static final long DEFAULT_AUTO_SAVE_INTERVAL_TICKS = 6000L;
    private static final long STALE_STATE_CLEANUP_INTERVAL_TICKS = 6000L;
    private static final long STALE_STATE_MAX_AGE_MS = 120000L;
    private static final long BAN_CHECK_TIMEOUT_SECONDS = 5L;
    private static final long COMBAT_LOGOUT_CHECK_TIMEOUT_SECONDS = 3L;
    private static final long EMERGENCY_SHUTDOWN_TIMEOUT_SECONDS = 10L;

    // Processing constants
    private static final int MAX_CONCURRENT_OPERATIONS = 10;
    private static final int DEFAULT_IO_THREADS = 4;
    private static final int DEFAULT_PLAYERS_PER_SAVE_CYCLE = 10;
    private static final int DEFAULT_MAX_BAN_CHECK_ATTEMPTS = 30;

    // Delay constants (in ticks)
    private static final long LIMBO_SETUP_DELAY = 5L;
    private static final long INVENTORY_APPLICATION_DELAY = 20L;
    private static final long STATS_APPLICATION_DELAY = 20L;
    private static final long FINALIZATION_DELAY = 20L;
    private static final long TELEPORT_COMPLETION_DELAY = 10L;
    private static final long REJOIN_MESSAGE_DELAY = 20L;
    private static final long MOTD_SEND_DELAY = 40L;

    // ===============================================
    // ENUMS
    // ===============================================

    public enum PlayerState {
        OFFLINE, JOINING, LOADING, IN_LIMBO, READY,
        IN_COMBAT_LOGOUT, PROCESSING_DEATH, FAILED
    }

    public enum LoadingPhase {
        JOINING, IN_LIMBO, LOADING_DATA, APPLYING_INVENTORY,
        APPLYING_STATS, FINALIZING, TELEPORTING, COMPLETED, FAILED
    }

    // ===============================================
    // SINGLETON INSTANCE
    // ===============================================

    private static volatile YakPlayerManager instance;

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

    // ===============================================
    // CORE DEPENDENCIES
    // ===============================================

    @Getter
    private YakPlayerRepository repository;
    private final Plugin plugin;
    private final LimboManager limboManager;

    // ===============================================
    // PLAYER TRACKING & STATE MANAGEMENT
    // ===============================================

    // Player data tracking
    private final Map<UUID, YakPlayer> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerLoadingState> loadingStates = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final Set<UUID> protectedPlayers = ConcurrentHashMap.newKeySet();

    // Combat logout coordination
    private final Set<UUID> playersInCombatLogoutProcessing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, YakPlayer> pendingCombatLogoutCleanup = new ConcurrentHashMap<>();

    // UPDATED SETTINGS PROTECTION: Track settings changes made during loading to preserve them
    private final Map<UUID, Map<String, Boolean>> pendingSettingsChanges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> settingsChangeTimestamps = new ConcurrentHashMap<>();

    // State management with locks
    private final Map<UUID, ReentrantReadWriteLock> playerStateLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastStateChange = new ConcurrentHashMap<>();

    // ===============================================
    // THREAD MANAGEMENT & PERFORMANCE
    // ===============================================

    private final ExecutorService ioExecutor;
    private final Semaphore operationSemaphore = new Semaphore(MAX_CONCURRENT_OPERATIONS);

    // UPDATED Performance tracking
    private final AtomicInteger totalPlayerJoins = new AtomicInteger(0);
    private final AtomicInteger totalPlayerQuits = new AtomicInteger(0);
    private final AtomicInteger successfulLoads = new AtomicInteger(0);
    private final AtomicInteger failedLoads = new AtomicInteger(0);
    private final AtomicInteger combatLogoutRejoins = new AtomicInteger(0);
    private final AtomicInteger combatLogoutCoordinations = new AtomicInteger(0);
    private final AtomicInteger settingsProtectionSaves = new AtomicInteger(0);
    private final AtomicInteger pendingSettingsApplied = new AtomicInteger(0);
    private final AtomicInteger emergencySettingsSaves = new AtomicInteger(0);
    private final AtomicInteger settingsProtectionFailures = new AtomicInteger(0);

    // ===============================================
    // SYSTEM STATE & CONFIGURATION
    // ===============================================

    // System state
    private volatile boolean initialized = false;
    private volatile boolean shutdownInProgress = false;
    private volatile boolean systemHealthy = false;
    private volatile boolean worldDetectionComplete = false;

    // Configuration
    private final long autoSaveInterval;
    private final int playersPerSaveCycle;

    // World management
    private volatile World voidLimboWorld;
    private final List<String> preferredWorldNames = Arrays.asList("world", "overworld", "main", "lobby", "spawn");

    // Background tasks
    private BukkitTask autoSaveTask;
    private BukkitTask loadingMonitorTask;
    private BukkitTask settingsProtectionTask;

    // ===============================================
    // CONSTRUCTOR
    // ===============================================

    private YakPlayerManager() {
        this.plugin = YakRealms.getInstance();
        this.limboManager = LimboManager.getInstance();

        // Load configuration with defaults
        this.autoSaveInterval = plugin.getConfig().getLong("player_manager.auto_save_interval_ticks", DEFAULT_AUTO_SAVE_INTERVAL_TICKS);
        this.playersPerSaveCycle = plugin.getConfig().getInt("player_manager.players_per_save_cycle", DEFAULT_PLAYERS_PER_SAVE_CYCLE);

        // Initialize thread pool
        int ioThreads = plugin.getConfig().getInt("player_manager.io_threads", DEFAULT_IO_THREADS);
        this.ioExecutor = Executors.newFixedThreadPool(ioThreads, r -> {
            Thread thread = new Thread(r, "YakPlayerManager-IO");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("UPDATED YakPlayerManager initialized with ENHANCED SETTINGS PROTECTION and combat logout coordination");
    }

    // ===============================================
    // INITIALIZATION & LIFECYCLE
    // ===============================================

    /**
     * UPDATED: Initialize YakPlayerManager with enhanced features
     */
    public void onEnable() {
        if (initialized) {
            logger.warning("YakPlayerManager already initialized!");
            return;
        }

        try {
            logger.info("Starting UPDATED YakPlayerManager with ENHANCED SETTINGS PROTECTION...");

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

            logger.info("UPDATED YakPlayerManager enabled successfully with ENHANCED SETTINGS PROTECTION");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize YakPlayerManager", e);
            systemHealthy = false;
        }
    }

    /**
     * Initialize the repository with timeout and retry logic
     */
    private boolean initializeRepository() {
        try {
            logger.info("Initializing YakPlayerRepository...");
            this.repository = new YakPlayerRepository();

            int attempts = 0;
            while (!repository.isInitialized() && attempts < DEFAULT_MAX_BAN_CHECK_ATTEMPTS) {
                Thread.sleep(1000);
                attempts++;
            }

            if (!repository.isInitialized()) {
                logger.severe("Repository failed to initialize after " + DEFAULT_MAX_BAN_CHECK_ATTEMPTS + " seconds");
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
     * Initialize world detection with preferred world fallback
     */
    private void initializeWorldDetection() {
        logger.info("Starting world detection...");

        List<World> availableWorlds = Bukkit.getWorlds();
        if (availableWorlds.isEmpty()) {
            logger.warning("No worlds available during initialization");
            return;
        }

        // Try preferred worlds first
        for (String worldName : preferredWorldNames) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                this.voidLimboWorld = world;
                worldDetectionComplete = true;
                logger.info("World detection SUCCESS - Using preferred world: " + worldName);
                return;
            }
        }

        // Use first available world as fallback
        this.voidLimboWorld = availableWorlds.get(0);
        worldDetectionComplete = true;
        logger.info("World detection SUCCESS - Using fallback world: " + availableWorlds.get(0).getName());
    }

    /**
     * UPDATED: Start background monitoring and maintenance tasks with settings protection
     */
    private void startBackgroundTasks() {
        // Auto-save task
        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress) {
                    performAutoSaveUpdated();
                }
            }
        }.runTaskTimerAsynchronously(plugin, autoSaveInterval, autoSaveInterval);

        // Loading state cleanup task
        loadingMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupStaleLoadingStates();
            }
        }.runTaskTimerAsynchronously(plugin, STALE_STATE_CLEANUP_INTERVAL_TICKS, STALE_STATE_CLEANUP_INTERVAL_TICKS);

        // UPDATED: Settings protection monitoring task
        settingsProtectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!shutdownInProgress) {
                    monitorSettingsProtection();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Every minute

        logger.info("Background tasks started with settings protection monitoring");
    }

    /**
     * UPDATED: Shutdown YakPlayerManager with settings protection cleanup
     */
    public void onDisable() {
        if (shutdownInProgress) return;

        shutdownInProgress = true;
        logger.info("Starting UPDATED YakPlayerManager shutdown with ENHANCED SETTINGS PROTECTION...");

        try {
            // UPDATED: Force save any pending settings before shutdown
            saveAllPendingSettingsOnShutdown();

            // Cancel background tasks
            cancelBackgroundTasks();

            // Cancel all loading monitor tasks
            cancelAllLoadingMonitorTasks();

            // Cleanup systems
            cleanupSystemsOnShutdown();

            // Save all players and shutdown repository
            saveAllPlayersOnShutdown();
            shutdownRepository();

            // Shutdown thread pool
            shutdownExecutorService();

            // Clear all data structures
            clearAllDataStructures();

            logger.info("UPDATED YakPlayerManager shutdown completed with ENHANCED SETTINGS PROTECTION");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown", e);
        } finally {
            initialized = false;
        }
    }

    /**
     * UPDATED: Save all pending settings changes before shutdown
     */
    private void saveAllPendingSettingsOnShutdown() {
        if (pendingSettingsChanges.isEmpty()) return;

        logger.info("Saving " + pendingSettingsChanges.size() + " pending settings changes on shutdown...");
        int saved = 0;

        for (Map.Entry<UUID, Map<String, Boolean>> entry : pendingSettingsChanges.entrySet()) {
            UUID playerId = entry.getKey();
            Map<String, Boolean> settings = entry.getValue();

            if (settings != null && !settings.isEmpty()) {
                try {
                    if (forceSettingsSave(playerId)) {
                        saved++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error saving pending settings on shutdown for " + playerId, e);
                }
            }
        }

        logger.info("Saved " + saved + " pending settings on shutdown");
    }

    private void cancelBackgroundTasks() {
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        if (loadingMonitorTask != null && !loadingMonitorTask.isCancelled()) {
            loadingMonitorTask.cancel();
        }
        if (settingsProtectionTask != null && !settingsProtectionTask.isCancelled()) {
            settingsProtectionTask.cancel();
        }
    }

    private void cancelAllLoadingMonitorTasks() {
        for (PlayerLoadingState state : loadingStates.values()) {
            BukkitTask task = state.getMonitorTask();
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
    }

    private void cleanupSystemsOnShutdown() {
        if (limboManager.isInitialized()) {
            limboManager.shutdown();
        }
    }

    private void shutdownRepository() {
        if (repository != null) {
            repository.shutdown();
        }
    }

    private void shutdownExecutorService() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(EMERGENCY_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void clearAllDataStructures() {
        onlinePlayers.clear();
        loadingStates.clear();
        playerStates.clear();
        protectedPlayers.clear();
        playerStateLocks.clear();
        lastStateChange.clear();
        playersInCombatLogoutProcessing.clear();
        pendingCombatLogoutCleanup.clear();
        pendingSettingsChanges.clear(); // UPDATED: Clear pending changes
        settingsChangeTimestamps.clear(); // UPDATED: Clear timestamps
    }

    // ===============================================
    // ENHANCED SETTINGS PROTECTION SYSTEM
    // ===============================================

    /**
     * CRITICAL UPDATE: Save toggle setting change immediately regardless of player state
     * This is the core method that preserves settings changes during loading states
     */
    public boolean saveToggleSettingImmediate(UUID playerId, String toggleName, boolean enabled) {
        if (playerId == null || toggleName == null) {
            return false;
        }

        long startTime = System.currentTimeMillis();
        logger.info("SETTINGS PROTECTION: Immediate toggle save for " + playerId + " - " + toggleName + "=" + enabled);

        try {
            // Get current player if available
            YakPlayer yakPlayer = getPlayer(playerId);

            if (yakPlayer != null) {
                // Player is loaded, apply setting immediately
                boolean currentState = yakPlayer.isToggled(toggleName);
                if (currentState != enabled) {
                    yakPlayer.toggleSetting(toggleName);

                    // IMMEDIATE save regardless of player state
                    CompletableFuture.runAsync(() -> {
                        try {
                            repository.saveSync(yakPlayer);
                            settingsProtectionSaves.incrementAndGet();
                            long duration = System.currentTimeMillis() - startTime;
                            logger.info("SETTINGS PROTECTION: Successfully saved toggle " + toggleName +
                                    " for " + yakPlayer.getUsername() + " in " + duration + "ms");
                        } catch (Exception e) {
                            settingsProtectionFailures.incrementAndGet();
                            logger.log(Level.SEVERE, "SETTINGS PROTECTION: Failed to save toggle " + toggleName, e);
                        }
                    }, ioExecutor);

                    return true;
                }
                return true; // Already in desired state
            }

            // Player not loaded yet, store for later application
            PlayerState state = getPlayerState(playerId);
            if (state == PlayerState.LOADING || state == PlayerState.IN_LIMBO || state == PlayerState.JOINING) {
                logger.info("SETTINGS PROTECTION: Storing pending toggle change during loading: " + toggleName + "=" + enabled);

                Map<String, Boolean> playerSettings = pendingSettingsChanges.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
                playerSettings.put(toggleName, enabled);
                settingsChangeTimestamps.put(playerId, System.currentTimeMillis());

                return true;
            }

            logger.warning("SETTINGS PROTECTION: Cannot save toggle for player not online: " + playerId);
            return false;

        } catch (Exception e) {
            settingsProtectionFailures.incrementAndGet();
            logger.log(Level.SEVERE, "SETTINGS PROTECTION: Error in immediate save for " + playerId, e);
            return false;
        }
    }

    /**
     * CRITICAL UPDATE: Apply pending settings changes after loading completes
     */
    private void applyPendingSettingsChanges(UUID playerId, YakPlayer yakPlayer) {
        Map<String, Boolean> pendingChanges = pendingSettingsChanges.remove(playerId);
        settingsChangeTimestamps.remove(playerId);

        if (pendingChanges == null || pendingChanges.isEmpty()) {
            return;
        }

        logger.info("SETTINGS PROTECTION: Applying " + pendingChanges.size() + " pending settings for " + yakPlayer.getUsername());

        boolean hasChanges = false;
        for (Map.Entry<String, Boolean> entry : pendingChanges.entrySet()) {
            String toggleName = entry.getKey();
            boolean desiredState = entry.getValue();
            boolean currentState = yakPlayer.isToggled(toggleName);

            if (currentState != desiredState) {
                yakPlayer.toggleSetting(toggleName);
                hasChanges = true;
                pendingSettingsApplied.incrementAndGet();
                logger.info("SETTINGS PROTECTION: Applied pending toggle " + toggleName + "=" + desiredState + " for " + yakPlayer.getUsername());
            }
        }

        // Save if any changes were applied
        if (hasChanges) {
            try {
                repository.saveSync(yakPlayer);
                logger.info("SETTINGS PROTECTION: Saved pending settings changes for " + yakPlayer.getUsername());
            } catch (Exception e) {
                settingsProtectionFailures.incrementAndGet();
                logger.log(Level.SEVERE, "SETTINGS PROTECTION: Failed to save pending changes", e);
            }
        }
    }

    /**
     * UPDATED: Emergency method to force save settings for any player
     */
    public boolean forceSettingsSave(UUID playerId) {
        if (playerId == null) return false;

        try {
            // Try to get from online players first
            YakPlayer yakPlayer = getPlayer(playerId);

            if (yakPlayer != null) {
                repository.saveSync(yakPlayer);
                emergencySettingsSaves.incrementAndGet();
                logger.info("SETTINGS PROTECTION: Force saved settings for online player: " + yakPlayer.getUsername());
                return true;
            }

            // Try to load from database and save pending changes
            Map<String, Boolean> pendingChanges = pendingSettingsChanges.get(playerId);
            if (pendingChanges != null && !pendingChanges.isEmpty()) {
                CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(playerId);
                Optional<YakPlayer> playerOpt = playerFuture.get(5, TimeUnit.SECONDS);

                if (playerOpt.isPresent()) {
                    YakPlayer offlinePlayer = playerOpt.get();

                    // Apply pending changes
                    boolean hasChanges = false;
                    for (Map.Entry<String, Boolean> entry : pendingChanges.entrySet()) {
                        String toggleName = entry.getKey();
                        boolean desiredState = entry.getValue();
                        boolean currentState = offlinePlayer.isToggled(toggleName);

                        if (currentState != desiredState) {
                            offlinePlayer.toggleSetting(toggleName);
                            hasChanges = true;
                        }
                    }

                    if (hasChanges) {
                        repository.saveSync(offlinePlayer);
                        pendingSettingsChanges.remove(playerId);
                        settingsChangeTimestamps.remove(playerId);
                        emergencySettingsSaves.incrementAndGet();
                        logger.info("SETTINGS PROTECTION: Applied and saved pending settings for offline player");
                        return true;
                    }
                }
            }

            logger.warning("SETTINGS PROTECTION: No player data or pending changes found for: " + playerId);
            return false;

        } catch (Exception e) {
            settingsProtectionFailures.incrementAndGet();
            logger.log(Level.SEVERE, "SETTINGS PROTECTION: Error during force settings save for " + playerId, e);
            return false;
        }
    }

    /**
     * UPDATED: Monitor settings protection system for issues
     */
    private void monitorSettingsProtection() {
        try {
            long currentTime = System.currentTimeMillis();
            int stalePendingSettings = 0;

            // Check for stale pending settings (older than 5 minutes)
            Iterator<Map.Entry<UUID, Long>> iterator = settingsChangeTimestamps.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                UUID playerId = entry.getKey();
                long timestamp = entry.getValue();

                if (currentTime - timestamp > 300000) { // 5 minutes
                    stalePendingSettings++;

                    // Try to force save stale settings
                    try {
                        if (forceSettingsSave(playerId)) {
                            logger.info("SETTINGS PROTECTION: Force saved stale pending settings for " + playerId);
                        }
                    } catch (Exception e) {
                        logger.warning("SETTINGS PROTECTION: Failed to save stale settings for " + playerId);
                    }

                    iterator.remove();
                    pendingSettingsChanges.remove(playerId);
                }
            }

            // Log monitoring statistics
            if (stalePendingSettings > 0 || pendingSettingsChanges.size() > 10) {
                logger.info("SETTINGS PROTECTION MONITOR: " +
                        "Pending=" + pendingSettingsChanges.size() +
                        ", Stale=" + stalePendingSettings +
                        ", Saves=" + settingsProtectionSaves.get() +
                        ", Applied=" + pendingSettingsApplied.get() +
                        ", Emergency=" + emergencySettingsSaves.get() +
                        ", Failures=" + settingsProtectionFailures.get());
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "SETTINGS PROTECTION: Error during monitoring", e);
        }
    }

    /**
     * UPDATED: Get pending settings count (for debugging)
     */
    public int getPendingSettingsCount() {
        return pendingSettingsChanges.size();
    }

    /**
     * UPDATED: Get pending settings for player (for debugging)
     */
    public Map<String, Boolean> getPendingSettings(UUID playerId) {
        Map<String, Boolean> pending = pendingSettingsChanges.get(playerId);
        return pending != null ? new HashMap<>(pending) : new HashMap<>();
    }

    /**
     * UPDATED: Get settings protection statistics
     */
    public SettingsProtectionStats getSettingsProtectionStats() {
        return new SettingsProtectionStats(
                settingsProtectionSaves.get(),
                pendingSettingsApplied.get(),
                emergencySettingsSaves.get(),
                settingsProtectionFailures.get(),
                pendingSettingsChanges.size(),
                settingsChangeTimestamps.size()
        );
    }

    // ===============================================
    // COMBAT LOGOUT COORDINATION
    // ===============================================

    /**
     * Check if player is fully loaded for combat logout processing
     */
    public boolean isPlayerFullyLoaded(UUID playerId) {
        if (playerId == null) return false;
        PlayerState state = getPlayerState(playerId);
        return state == PlayerState.READY;
    }

    /**
     * Get player for combat logout processing (won't return null during processing)
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
     * Mark player as entering combat logout processing
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
     * Mark player as finished with combat logout processing
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

    // ===============================================
    // EVENT HANDLERS
    // ===============================================

    /**
     * Pre-login handler - validates bans and prepares player data
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
            Optional<YakPlayer> playerOpt = playerFuture.get(BAN_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

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
                    repository.saveSync(player);
                }
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking ban status for " + playerName, e);
        }
    }

    /**
     * UPDATED: Player join handler with proper combat logout rejoin detection and state reset
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerJoins.incrementAndGet();
        logger.info("UPDATED: Player joining " + playerName + " (" + uuid + ")");

        // Check for duplicate processing
        if (loadingStates.containsKey(uuid) || onlinePlayers.containsKey(uuid)) {
            logger.warning("Duplicate join event for player: " + playerName + " - cleaning up old state");
            cleanupPlayerState(uuid, "duplicate_join");
        }

        // Check for combat logout rejoin
        if (handleCombatLogoutCheck(player, event)) {
            return; // Combat logout rejoin handled
        }

        // Normal join processing
        handleNormalPlayerJoin(player);
    }

    /**
     * Check if this is a combat logout rejoin and handle accordingly
     */
    private boolean handleCombatLogoutCheck(Player player, PlayerJoinEvent event) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        try {
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(uuid);
            Optional<YakPlayer> playerOpt = playerFuture.get(COMBAT_LOGOUT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (playerOpt.isPresent()) {
                YakPlayer yakPlayer = playerOpt.get();
                YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();

                // Only treat as combat logout rejoin if state is PROCESSED
                boolean isCombatLogoutRejoin = (logoutState == YakPlayer.CombatLogoutState.PROCESSED);

                logger.info("Player " + playerName + " combat logout state: " + logoutState +
                        ", is rejoin: " + isCombatLogoutRejoin);

                if (isCombatLogoutRejoin) {
                    logger.info("Combat logout rejoin detected for " + playerName + " - processing completion");
                    event.setJoinMessage(null); // Suppress default join message
                    handleCombatLogoutRejoin(player, yakPlayer);
                    return true;
                }

                // Reset stale COMPLETED state
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
        }

        return false; // Not a combat logout rejoin
    }

    /**
     * Handle normal player join process
     */
    private void handleNormalPlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        logger.info("Processing normal join for " + playerName);

        // Set initial state
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
     * Player movement handler for limbo restrictions
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
     * UPDATED: Player quit handler with proper combat logout coordination and state reset
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        totalPlayerQuits.incrementAndGet();
        logger.info("UPDATED: Player quitting: " + playerName);

        try {
            // Check if player is in combat logout processing
            if (playersInCombatLogoutProcessing.contains(uuid)) {
                logger.info("Player " + playerName + " is in combat logout processing - coordinating cleanup");
                handleCombatLogoutPlayerQuit(player, uuid, playerName);
                return;
            }

            // Normal quit processing
            handleNormalPlayerQuit(player, uuid, playerName, event);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in UPDATED player quit for " + playerName, e);
            performEmergencyQuitCleanup(uuid, playerName);
        }
    }

    /**
     * Handle quit for player in combat logout processing
     */
    private void handleCombatLogoutPlayerQuit(Player player, UUID uuid, String playerName) {
        try {
            logger.info("Handling combat logout quit for: " + playerName);

            // Don't clean up player state yet - CombatLogoutMechanics needs the data
            setPlayerState(uuid, PlayerState.OFFLINE, "combat_logout_quit");

            logger.info("Combat logout quit handling complete for: " + playerName + " (data preserved for processing)");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling combat logout quit for " + playerName, e);
            markPlayerFinishedCombatLogout(uuid);
        }
    }

    /**
     * UPDATED: Handle normal player quit with proper state reset and settings protection
     */
    private void handleNormalPlayerQuit(Player player, UUID uuid, String playerName, PlayerQuitEvent event) {
        logger.info("Handling normal quit for: " + playerName);

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

            // UPDATED: Save any pending settings immediately before quit
            Map<String, Boolean> pendingSettings = pendingSettingsChanges.remove(uuid);
            if (pendingSettings != null && !pendingSettings.isEmpty()) {
                logger.info("SETTINGS PROTECTION: Applying " + pendingSettings.size() + " pending settings on quit for " + playerName);
                boolean hasChanges = false;
                for (Map.Entry<String, Boolean> entry : pendingSettings.entrySet()) {
                    String toggleName = entry.getKey();
                    boolean desiredState = entry.getValue();
                    boolean currentState = yakPlayer.isToggled(toggleName);

                    if (currentState != desiredState) {
                        yakPlayer.toggleSetting(toggleName);
                        hasChanges = true;
                    }
                }

                if (hasChanges) {
                    logger.info("SETTINGS PROTECTION: Applied pending settings on quit for " + playerName);
                }
            }
            settingsChangeTimestamps.remove(uuid);

            // Reset combat logout state to prevent future issues
            YakPlayer.CombatLogoutState currentState = yakPlayer.getCombatLogoutState();
            if (currentState != YakPlayer.CombatLogoutState.NONE) {
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                logger.info("CRITICAL UPDATE: Reset combat logout state from " + currentState + " to NONE for normal quit: " + playerName);
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

    /**
     * Emergency cleanup for failed quit processing
     */
    private void performEmergencyQuitCleanup(UUID uuid, String playerName) {
        try {
            cleanupPlayerState(uuid, "emergency_quit_cleanup");
            onlinePlayers.remove(uuid);
            markPlayerFinishedCombatLogout(uuid);
            pendingSettingsChanges.remove(uuid);
            settingsChangeTimestamps.remove(uuid);
        } catch (Exception emergencyError) {
            logger.log(Level.SEVERE, "Emergency cleanup failed for " + playerName, emergencyError);
        }
    }

    // ===============================================
    // COMBAT LOGOUT REJOIN HANDLING
    // ===============================================

    /**
     * Handle combat logout rejoin with proper state reset
     */
    private void handleCombatLogoutRejoin(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        try {
            combatLogoutRejoins.incrementAndGet();
            logger.info("Processing combat logout rejoin for: " + playerName);

            // Set player state and connect data
            setPlayerState(uuid, PlayerState.READY, "combat_logout_rejoin");
            yakPlayer.connect(player);
            onlinePlayers.put(uuid, yakPlayer);

            // UPDATED: Apply any pending settings changes first
            applyPendingSettingsChanges(uuid, yakPlayer);

            // Apply processed inventory and stats
            yakPlayer.applyInventory(player);
            yakPlayer.applyStats(player);

            // Initialize systems
            initializeRankSystem(player, yakPlayer);
            initializeChatTagSystem(player, yakPlayer);

            // Teleport to spawn
            teleportToSpawn(player, yakPlayer);

            // Clear combat logout data and reset state
            clearCombatLogoutData(uuid, yakPlayer, playerName);

            // Send messages and notifications
            sendCombatLogoutRejoinMessages(player, yakPlayer);

            logger.info("Combat logout rejoin completed for: " + playerName);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling combat logout rejoin for " + playerName, e);
            handleCombatLogoutRejoinFailure(player, yakPlayer, e);
        }
    }

    private void teleportToSpawn(Player player, YakPlayer yakPlayer) {
        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            Location spawnLocation = world.getSpawnLocation();
            player.teleport(spawnLocation);
            yakPlayer.updateLocation(spawnLocation);
        }
    }

    private void clearCombatLogoutData(UUID uuid, YakPlayer yakPlayer, String playerName) {
        // Clear combat logout data from CombatLogoutMechanics
        CombatLogoutMechanics combatLogout = CombatLogoutMechanics.getInstance();
        if (combatLogout != null) {
            combatLogout.handleCombatLogoutRejoin(uuid);
        }

        // Reset combat logout state
        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
        logger.info("CRITICAL UPDATE: Reset combat logout state to NONE for " + playerName);

        // Save the player data with the reset state
        repository.saveSync(yakPlayer);
        logger.info("CRITICAL UPDATE: Saved player data with reset combat logout state for " + playerName);
    }

    private void sendCombatLogoutRejoinMessages(Player player, YakPlayer yakPlayer) {
        // Broadcast rejoin message
        Bukkit.broadcastMessage(ChatColor.GRAY + "⟨" + ChatColor.DARK_GRAY + "✧" + ChatColor.GRAY + "⟩ " +
                yakPlayer.getFormattedDisplayName() + ChatColor.DARK_GRAY + " returned from combat logout");

        // Send completion message with delay
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (player.isOnline()) {
                sendCombatLogoutCompletionMessage(player, yakPlayer);
            }
        }, REJOIN_MESSAGE_DELAY);

        // Send MOTD with delay
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (player.isOnline() && PlayerListenerManager.getInstance() != null &&
                    PlayerListenerManager.getInstance().getJoinLeaveListener() != null) {
                PlayerListenerManager.getInstance().getJoinLeaveListener()
                        .sendMotd(player, yakPlayer, true);
            }
        }, MOTD_SEND_DELAY);
    }

    private void sendCombatLogoutCompletionMessage(Player player, YakPlayer yakPlayer) {
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT CONSEQUENCES COMPLETED");
        player.sendMessage(ChatColor.GRAY + "Your items were processed according to your " +
                yakPlayer.getAlignment().toLowerCase() + " alignment.");
        player.sendMessage(ChatColor.GRAY + "You have respawned at the spawn location.");
        player.sendMessage(ChatColor.YELLOW + "You may now continue playing normally.");
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Handle combat logout rejoin failure with fallback to normal loading
     */
    private void handleCombatLogoutRejoinFailure(Player player, YakPlayer yakPlayer, Exception error) {
        String playerName = player.getName();
        UUID uuid = player.getUniqueId();

        logger.warning("Combat logout rejoin failed, falling back to normal loading for: " + playerName);

        // Clean up and reset state
        onlinePlayers.remove(uuid);
        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
        repository.saveSync(yakPlayer);
        logger.info("CRITICAL UPDATE: Reset combat logout state to NONE on error for " + playerName);

        // Start normal loading process
        if (setPlayerState(uuid, PlayerState.JOINING, "combat_logout_rejoin_fallback")) {
            PlayerLoadingState loadingState = new PlayerLoadingState(uuid, playerName);
            loadingStates.put(uuid, loadingState);
            protectedPlayers.add(uuid);
            performLimboTeleportation(player, loadingState);
        } else {
            logger.severe("Failed to set fallback state for " + playerName);
        }
    }

    // ===============================================
    // LOADING PROCESS - LIMBO TELEPORTATION
    // ===============================================

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

            World targetWorld = getLimboWorld();
            logger.info("Using world '" + targetWorld.getName() + "' for " + playerName);

            Location limboLocation = createLimboLocation(targetWorld);
            boolean teleportSuccess = player.teleport(limboLocation);

            logger.info("Teleportation result for " + playerName + ": " + teleportSuccess);

            // Complete limbo setup regardless of teleport result
            completeLimboSetup(player, loadingState, uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during teleportation for " + player.getName(), e);
            completeLimboSetup(player, loadingState, player.getUniqueId());
        }
    }

    private World getLimboWorld() {
        if (voidLimboWorld != null) {
            return voidLimboWorld;
        }

        List<World> availableWorlds = Bukkit.getWorlds();
        if (availableWorlds.isEmpty()) {
            throw new RuntimeException("No worlds available for limbo teleportation");
        }

        return availableWorlds.get(0);
    }

    private Location createLimboLocation(World targetWorld) {
        Location limboLocation = limboManager.createLimboLocation(targetWorld);
        if (limboLocation == null) {
            limboLocation = new Location(targetWorld, 0.5, -64.0, 0.5);
        }
        return limboLocation;
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

    // ===============================================
    // LOADING PROCESS - DATA LOADING
    // ===============================================

    /**
     * Start data loading process in limbo
     */
    private void startDataLoadingInLimbo(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();
        setPlayerState(uuid, PlayerState.LOADING, "starting_data_load");
        loadingState.setPhase(LoadingPhase.LOADING_DATA);

        limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.LOADING_DATA);

        CompletableFuture.supplyAsync(() -> loadPlayerData(player), ioExecutor)
                .whenComplete((yakPlayer, error) -> {
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
     * Load player data with timeout and error handling
     */
    private YakPlayer loadPlayerData(Player player) {
        try {
            if (!operationSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Operation semaphore timeout");
            }

            try {
                logger.info("Loading player data for: " + player.getName());

                CompletableFuture<Optional<YakPlayer>> repositoryFuture = repository.findById(player.getUniqueId());
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
    }

    /**
     * CRITICAL UPDATE: Continue loading process after data is loaded - with enhanced settings protection
     */
    private void continueLoadingInLimbo(Player player, PlayerLoadingState loadingState, YakPlayer yakPlayer) {
        if (!player.isOnline()) {
            cleanupLoadingState(player.getUniqueId());
            return;
        }

        UUID uuid = player.getUniqueId();

        // CRITICAL UPDATE: Apply pending settings changes BEFORE putting in onlinePlayers
        applyPendingSettingsChanges(uuid, yakPlayer);

        onlinePlayers.put(uuid, yakPlayer);
        loadingState.setYakPlayer(yakPlayer);

        // Clear any residual combat logout state for normal loading
        if (yakPlayer.getCombatLogoutState() != YakPlayer.CombatLogoutState.NONE) {
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
        }

        applyDataWhileInLimbo(player, yakPlayer, loadingState);
    }

    // ===============================================
    // LOADING PROCESS - DATA APPLICATION
    // ===============================================

    private void applyDataWhileInLimbo(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        // Set default energy if not present
        if (!yakPlayer.hasTemporaryData("energy")) {
            yakPlayer.setTemporaryData("energy", 100);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || getPlayerState(uuid) == PlayerState.FAILED) return;

            loadingState.setPhase(LoadingPhase.APPLYING_INVENTORY);
            limboManager.startPlayerVisualEffects(player, LimboManager.LoadingPhase.APPLYING_INVENTORY);
            applyInventoryInLimbo(player, yakPlayer, loadingState);
        }, LIMBO_SETUP_DELAY);
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
            }, INVENTORY_APPLICATION_DELAY);

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
            setLimboPlayerMode(player);

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
            }, STATS_APPLICATION_DELAY);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error applying stats in limbo: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private void setLimboPlayerMode(Player player) {
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setFlying(true);
            player.setAllowFlight(true);
            player.setInvulnerable(true);
        }

        player.setExp(1.0f);
        player.setLevel(100);
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
            }, FINALIZATION_DELAY);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error finalizing in limbo: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    // ===============================================
    // LOADING PROCESS - FINAL TELEPORTATION
    // ===============================================

    private void teleportToFinalLocation(Player player, YakPlayer yakPlayer, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            logger.info("Teleporting to final location: " + player.getName());

            Location teleportLocation = determineFinalLocation(yakPlayer);

            player.sendTitle(
                    ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "⟨ Crossing the Veil ⟩",
                    ChatColor.GRAY + "Entering YakRealms...",
                    10, 40, 20
            );

            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

            restorePlayerState(player, yakPlayer);

            boolean teleportSuccess = player.teleport(teleportLocation);
            if (!teleportSuccess) {
                teleportLocation = handleTeleportFailure(player, yakPlayer);
            }

            finalizeTeleportation(player, loadingState, uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in final teleportation: " + player.getName(), e);
            handleLoadingFailure(player, loadingState, e);
        }
    }

    private Location determineFinalLocation(YakPlayer yakPlayer) {
        Location savedLocation = yakPlayer.getLocation();
        boolean isNewPlayer = isNewPlayer(yakPlayer);

        if (isNewPlayer || savedLocation == null || savedLocation.getWorld() == null) {
            World defaultWorld = voidLimboWorld;
            if (defaultWorld != null) {
                Location spawnLocation = defaultWorld.getSpawnLocation();
                yakPlayer.updateLocation(spawnLocation);
                logger.info("Teleporting " + yakPlayer.getUsername() + " to spawn");
                return spawnLocation;
            } else {
                throw new RuntimeException("No world available for spawn teleportation");
            }
        } else {
            logger.info("Teleporting " + yakPlayer.getUsername() + " to saved location");
            return savedLocation;
        }
    }

    private Location handleTeleportFailure(Player player, YakPlayer yakPlayer) {
        logger.warning("Failed to teleport " + player.getName() + " to final location");
        World defaultWorld = voidLimboWorld;
        if (defaultWorld != null) {
            Location spawnLocation = defaultWorld.getSpawnLocation();
            player.teleport(spawnLocation);
            yakPlayer.updateLocation(spawnLocation);
            return spawnLocation;
        }
        throw new RuntimeException("No fallback world available");
    }

    private void finalizeTeleportation(Player player, PlayerLoadingState loadingState, UUID uuid) {
        limboManager.removePlayerFromLimbo(uuid);
        setPlayerState(uuid, PlayerState.READY, "loading_completed");
        loadingState.setPhase(LoadingPhase.COMPLETED);

        limboManager.showCompletionCelebration(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            completePlayerLoading(player, loadingState);
        }, TELEPORT_COMPLETION_DELAY);
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

    // ===============================================
    // LOADING PROCESS - COMPLETION
    // ===============================================

    private void completePlayerLoading(Player player, PlayerLoadingState loadingState) {
        UUID uuid = player.getUniqueId();

        try {
            long loadingTime = System.currentTimeMillis() - loadingState.getLimboStartTime();
            logger.info("Loading completed for " + player.getName() + " in " + loadingTime + "ms");

            updateJoinMessage(player, loadingState.getYakPlayer());
            cleanupLoadingState(uuid);

            sendMotdIfAvailable(player, uuid);

            logger.info("Player fully loaded and ready: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error completing player loading: " + player.getName(), e);
        }
    }

    private void sendMotdIfAvailable(Player player, UUID uuid) {
        if (PlayerListenerManager.getInstance() != null &&
                PlayerListenerManager.getInstance().getJoinLeaveListener() != null) {
            PlayerListenerManager.getInstance().getJoinLeaveListener()
                    .sendMotd(player, getPlayer(uuid), false);
        }
    }

    // ===============================================
    // LOADING PROCESS - ERROR HANDLING
    // ===============================================

    /**
     * Handle loading failure with emergency recovery
     */
    private void handleLoadingFailure(Player player, PlayerLoadingState loadingState, Throwable error) {
        UUID uuid = player.getUniqueId();

        try {
            setPlayerState(uuid, PlayerState.FAILED, "loading_failure");
            loadingState.setPhase(LoadingPhase.FAILED);
            logger.log(Level.SEVERE, "Loading failed for: " + player.getName(), error);

            limboManager.removePlayerFromLimbo(uuid);

            if (player.isOnline()) {
                performEmergencyRecovery(player);
                player.sendMessage(ChatColor.GREEN + "Emergency recovery completed - you are now safe.");
            }

            cleanupLoadingState(uuid);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling loading failure: " + player.getName(), e);
        }
    }

    private void performEmergencyRecovery(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(false);

        World world = Bukkit.getWorlds().get(0);
        if (world != null) {
            player.teleport(world.getSpawnLocation());
        }
    }

    // ===============================================
    // LOADING PROCESS - MONITORING
    // ===============================================

    /**
     * Start loading monitor task for timeout detection
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

    // ===============================================
    // SYSTEM INITIALIZATION
    // ===============================================

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

    // ===============================================
    // BACKGROUND TASKS & MAINTENANCE
    // ===============================================

    /**
     * UPDATED: Perform auto-save with enhanced settings protection - always save settings, inventory only when safe
     */
    private void performAutoSaveUpdated() {
        List<YakPlayer> playersToSave = new ArrayList<>();
        int count = 0;

        for (YakPlayer player : onlinePlayers.values()) {
            if (count >= playersPerSaveCycle) break;

            Player bukkitPlayer = player.getBukkitPlayer();
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                UUID uuid = bukkitPlayer.getUniqueId();

                // CRITICAL UPDATE: Always save settings, but only update inventory for ready players
                if (shouldSavePlayerInventory(uuid)) {
                    player.updateInventory(bukkitPlayer);
                }

                // Always save the player (preserves settings even if inventory isn't updated)
                playersToSave.add(player);
                count++;
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

    /**
     * UPDATED: Separate method to check if inventory should be saved (more restrictive)
     */
    private boolean shouldSavePlayerInventory(UUID uuid) {
        return !protectedPlayers.contains(uuid) &&
                !limboManager.isPlayerInLimbo(uuid) &&
                !playersInCombatLogoutProcessing.contains(uuid) &&
                getPlayerState(uuid) == PlayerState.READY;
    }

    /**
     * Clean up stale loading states
     */
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
                cancelMonitorTask(state);
                continue;
            }

            long stateAge = now - state.getLimboStartTime();
            if (stateAge > STALE_STATE_MAX_AGE_MS) {
                logger.warning("Cleaning up stale loading state: " + state.getPlayerName());
                handleLoadingFailure(player, state, new RuntimeException("Stale loading state"));
                iterator.remove();
            }
        }
    }

    private void cancelMonitorTask(PlayerLoadingState state) {
        BukkitTask task = state.getMonitorTask();
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Save all players during shutdown
     */
    private void saveAllPlayersOnShutdown() {
        logger.info("Saving " + onlinePlayers.size() + " players on shutdown...");

        int savedCount = 0;
        for (YakPlayer yakPlayer : new ArrayList<>(onlinePlayers.values())) {
            try {
                Player player = yakPlayer.getBukkitPlayer();

                if (player != null && player.isOnline()) {
                    UUID uuid = player.getUniqueId();
                    if (getPlayerState(uuid) == PlayerState.READY) {
                        yakPlayer.updateInventory(player);
                    }
                }

                yakPlayer.disconnect();
                repository.saveSync(yakPlayer);
                savedCount++;

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save player on shutdown: " + yakPlayer.getUsername(), e);
            }
        }

        logger.info("Shutdown save completed: " + savedCount + "/" + onlinePlayers.size() + " players saved");
    }

    // ===============================================
    // STATE MANAGEMENT
    // ===============================================

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

    // ===============================================
    // PLAYER STATE CLEANUP
    // ===============================================

    /**
     * UPDATED: Cleanup with enhanced settings protection awareness
     */
    private void cleanupPlayerState(UUID uuid, String reason) {
        try {
            PlayerLoadingState loadingState = loadingStates.remove(uuid);
            if (loadingState != null) {
                cancelMonitorTask(loadingState);
            }

            protectedPlayers.remove(uuid);
            limboManager.removePlayerFromLimbo(uuid);
            setPlayerState(uuid, PlayerState.OFFLINE, reason);

            ModerationMechanics.rankMap.remove(uuid);
            ChatMechanics.getPlayerTags().remove(uuid);

            // CRITICAL UPDATE: Don't clear pending settings on normal cleanup unless it's an actual quit
            if (reason.equals("player_offline") || reason.equals("normal_player_quit")) {
                // Only clear pending settings on actual quit, not on loading failures
                pendingSettingsChanges.remove(uuid);
                settingsChangeTimestamps.remove(uuid);
            }

            logger.fine("Cleaned up player state for " + uuid + " (reason: " + reason + ")");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error cleaning up player state for " + uuid, e);
        }
    }

    private void cleanupLoadingState(UUID uuid) {
        cleanupPlayerState(uuid, "loading_completed");
    }

    // ===============================================
    // PLAYER DATA OPERATIONS
    // ===============================================

    /**
     * Save player with coordination and validation
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
     * Execute operation on player with optional save
     */
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

    // ===============================================
    // MANUAL COMBAT LOGOUT STATE RESET
    // ===============================================

    /**
     * Manual combat logout state reset by UUID
     */
    public boolean resetCombatLogoutState(UUID playerId) {
        try {
            YakPlayer yakPlayer = getPlayer(playerId);

            if (yakPlayer != null) {
                // Player is online
                return resetOnlinePlayerCombatLogoutState(yakPlayer);
            } else {
                // Player is offline, load from database
                return resetOfflinePlayerCombatLogoutState(playerId);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "MANUAL RESET: Error resetting combat logout state for " + playerId, e);
            return false;
        }
    }

    private boolean resetOnlinePlayerCombatLogoutState(YakPlayer yakPlayer) {
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
    }

    private boolean resetOfflinePlayerCombatLogoutState(UUID playerId) {
        try {
            CompletableFuture<Optional<YakPlayer>> playerFuture = repository.findById(playerId);
            Optional<YakPlayer> playerOpt = playerFuture.get(BAN_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

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
        } catch (Exception e) {
            logger.log(Level.SEVERE, "MANUAL RESET: Error resetting offline player state for " + playerId, e);
            return false;
        }
    }

    /**
     * Manual combat logout state reset by name
     */
    public boolean resetCombatLogoutState(String playerName) {
        try {
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

    // ===============================================
    // COMMAND HANDLING
    // ===============================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("playerstate")) {
            return handlePlayerStateCommand(player);
        }

        if (command.getName().equalsIgnoreCase("resetcombatlogout")) {
            return handleResetCombatLogoutCommand(player, args);
        }

        if (command.getName().equalsIgnoreCase("settingsprotection")) {
            return handleSettingsProtectionCommand(player, args);
        }

        return false;
    }

    private boolean handlePlayerStateCommand(Player player) {
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

        sendPlayerStateDebugInfo(player, uuid, state, phase);
        return true;
    }

    private void sendPlayerStateDebugInfo(Player player, UUID uuid, PlayerState state, LoadingPhase phase) {
        player.sendMessage(ChatColor.YELLOW + "=== UPDATED PLAYER STATE DEBUG ===");
        player.sendMessage(ChatColor.YELLOW + "State: " + state);
        player.sendMessage(ChatColor.YELLOW + "Loading Phase: " + phase);
        player.sendMessage(ChatColor.YELLOW + "In Limbo: " + limboManager.isPlayerInLimbo(uuid));
        player.sendMessage(ChatColor.YELLOW + "Protected: " + protectedPlayers.contains(uuid));
        player.sendMessage(ChatColor.YELLOW + "Combat Logout Rejoins: " + combatLogoutRejoins.get());
        player.sendMessage(ChatColor.YELLOW + "Combat Logout Processing: " + playersInCombatLogoutProcessing.contains(uuid));
        player.sendMessage(ChatColor.YELLOW + "Combat Logout Coordinations: " + combatLogoutCoordinations.get());

        // UPDATED: Add enhanced settings debug info
        SettingsProtectionStats stats = getSettingsProtectionStats();
        player.sendMessage(ChatColor.YELLOW + "Settings Protection Saves: " + stats.protectionSaves);
        player.sendMessage(ChatColor.YELLOW + "Pending Settings Applied: " + stats.pendingApplied);
        player.sendMessage(ChatColor.YELLOW + "Emergency Settings Saves: " + stats.emergencySaves);
        player.sendMessage(ChatColor.YELLOW + "Settings Protection Failures: " + stats.failures);
        player.sendMessage(ChatColor.YELLOW + "Pending Settings Count: " + stats.pendingCount);

        Map<String, Boolean> pendingSettings = getPendingSettings(uuid);
        if (!pendingSettings.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Your Pending Settings: " + pendingSettings.size());
            for (Map.Entry<String, Boolean> entry : pendingSettings.entrySet()) {
                player.sendMessage(ChatColor.GRAY + "  - " + entry.getKey() + ": " + entry.getValue());
            }
        }

        YakPlayer yakPlayer = getPlayer(uuid);
        if (yakPlayer != null) {
            player.sendMessage(ChatColor.YELLOW + "Combat Logout State: " + yakPlayer.getCombatLogoutState());
        }

        player.sendMessage(ChatColor.YELLOW + "========================================");
    }

    private boolean handleResetCombatLogoutCommand(Player player, String[] args) {
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

    /**
     * UPDATED: Handle settings protection command
     */
    private boolean handleSettingsProtectionCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin")) {
            player.sendMessage(ChatColor.RED + "No permission");
            return true;
        }

        if (args.length == 0) {
            // Show stats
            SettingsProtectionStats stats = getSettingsProtectionStats();
            player.sendMessage(ChatColor.YELLOW + "=== SETTINGS PROTECTION STATS ===");
            player.sendMessage(ChatColor.YELLOW + "Protection Saves: " + stats.protectionSaves);
            player.sendMessage(ChatColor.YELLOW + "Pending Applied: " + stats.pendingApplied);
            player.sendMessage(ChatColor.YELLOW + "Emergency Saves: " + stats.emergencySaves);
            player.sendMessage(ChatColor.YELLOW + "Failures: " + stats.failures);
            player.sendMessage(ChatColor.YELLOW + "Current Pending: " + stats.pendingCount);
            player.sendMessage(ChatColor.YELLOW + "Pending Timestamps: " + stats.timestampCount);
            return true;
        }

        if (args[0].equalsIgnoreCase("force")) {
            // Force save settings
            UUID targetUuid = player.getUniqueId();
            if (args.length > 1) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    targetUuid = target.getUniqueId();
                } else {
                    player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
            }

            boolean success = forceSettingsSave(targetUuid);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Force saved settings successfully");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to force save settings");
            }
            return true;
        }

        return true;
    }

    // ===============================================
    // MESSAGE FORMATTING
    // ===============================================

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
                // Ban expired, clear it
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

        String joinMessage = createJoinMessage(formattedName, onlineCount);

        if (joinMessage != null) {
            Bukkit.broadcastMessage(joinMessage);
        }
    }

    private String createJoinMessage(String formattedName, int onlineCount) {
        if (onlineCount == 1) {
            return ChatColor.GOLD + "✦ " + ChatColor.BOLD + formattedName +
                    ChatColor.GOLD + " has entered the realm! ✦";
        } else if (onlineCount <= 5) {
            return ChatColor.AQUA + "⟨" + ChatColor.LIGHT_PURPLE + "✦" + ChatColor.AQUA + "⟩ " + formattedName +
                    ChatColor.GRAY + " (" + onlineCount + " online)";
        } else if (onlineCount <= 20) {
            return ChatColor.AQUA + "⟨" + ChatColor.LIGHT_PURPLE + "✦" + ChatColor.AQUA + "⟩ " + formattedName;
        } else {
            return null;
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

    // ===============================================
    // PUBLIC API METHODS
    // ===============================================

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

    /**
     * UPDATED: Check if player is ready for subsystem operations with enhanced protection awareness
     */
    public boolean isPlayerReady(Player player) {
        if (player == null) return false;

        UUID uuid = player.getUniqueId();
        PlayerState state = getPlayerState(uuid);

        // Player must be in READY state and not in any protected modes
        return state == PlayerState.READY &&
                !protectedPlayers.contains(uuid) &&
                !limboManager.isPlayerInLimbo(uuid) &&
                !playersInCombatLogoutProcessing.contains(uuid);
    }

    // ===============================================
    // ENHANCED STATISTICS AND MONITORING
    // ===============================================

    /**
     * UPDATED: Get comprehensive system statistics with enhanced settings protection metrics
     */
    public SystemStats getSystemStats() {
        boolean yakPlayerManagerIntegrated = repository != null && repository.isInitialized();

        int loadingPlayers = 0;
        int protectedPlayers = 0;
        int limboPlayers = 0;
        if (yakPlayerManagerIntegrated) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID uuid = p.getUniqueId();
                if (isPlayerLoading(uuid)) loadingPlayers++;
                if (isPlayerProtected(uuid)) protectedPlayers++;
                if (isPlayerInVoidLimbo(uuid)) limboPlayers++;
            }
        }

        SettingsProtectionStats settingsStats = getSettingsProtectionStats();

        return new SystemStats(
                totalPlayerJoins.get(),
                totalPlayerQuits.get(),
                Bukkit.getOnlinePlayers().size(),
                isSystemHealthy(),
                systemHealthy,
                yakPlayerManagerIntegrated,
                loadingPlayers,
                protectedPlayers,
                limboPlayers,
                successfulLoads.get(),
                failedLoads.get(),
                combatLogoutRejoins.get(),
                combatLogoutCoordinations.get(),
                settingsStats
        );
    }

    /**
     * UPDATED: Get comprehensive performance report
     */
    public String getPerformanceReport() {
        SystemStats stats = getSystemStats();
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        StringBuilder report = new StringBuilder();
        report.append("=== UPDATED YakPlayerManager Performance Report ===\n");
        report.append("System Status: ").append(stats.systemHealthy ? "HEALTHY" : "DEGRADED").append("\n");
        report.append("Repository: ").append(stats.yakPlayerManagerIntegrated ? "READY" : "NOT READY").append("\n");
        report.append("\n");
        report.append("Player Statistics:\n");
        report.append("  Online Players: ").append(stats.currentOnline).append("\n");
        report.append("  Total Joins: ").append(stats.totalJoins).append("\n");
        report.append("  Total Quits: ").append(stats.totalQuits).append("\n");
        report.append("  Loading Players: ").append(stats.loadingPlayers).append("\n");
        report.append("  Protected Players: ").append(stats.protectedPlayers).append("\n");
        report.append("  Limbo Players: ").append(stats.limboPlayers).append("\n");
        report.append("\n");
        report.append("Loading Statistics:\n");
        report.append("  Successful Loads: ").append(stats.successfulLoads).append("\n");
        report.append("  Failed Loads: ").append(stats.failedLoads).append("\n");
        report.append("  Success Rate: ").append(String.format("%.1f%%",
                stats.totalJoins > 0 ? (double) stats.successfulLoads / stats.totalJoins * 100 : 0)).append("\n");
        report.append("\n");
        report.append("Combat Logout Coordination:\n");
        report.append("  Rejoin Events: ").append(stats.combatLogoutRejoins).append("\n");
        report.append("  Coordination Events: ").append(stats.combatLogoutCoordinations).append("\n");
        report.append("\n");
        report.append("ENHANCED Settings Protection:\n");
        report.append("  Protection Saves: ").append(stats.settingsStats.protectionSaves).append("\n");
        report.append("  Pending Applied: ").append(stats.settingsStats.pendingApplied).append("\n");
        report.append("  Emergency Saves: ").append(stats.settingsStats.emergencySaves).append("\n");
        report.append("  Failures: ").append(stats.settingsStats.failures).append("\n");
        report.append("  Current Pending: ").append(stats.settingsStats.pendingCount).append("\n");
        report.append("\n");
        report.append("Memory Usage:\n");
        report.append("  Used: ").append(usedMemory / 1024 / 1024).append(" MB\n");
        report.append("  Total: ").append(totalMemory / 1024 / 1024).append(" MB\n");
        report.append("  Usage: ").append(String.format("%.1f%%", (double) usedMemory / totalMemory * 100)).append("\n");
        report.append("===============================================");

        return report.toString();
    }

    // ===============================================
    // UTILITY CLASSES
    // ===============================================

    /**
     * UPDATED: Settings protection statistics class
     */
    public static class SettingsProtectionStats {
        public final int protectionSaves;
        public final int pendingApplied;
        public final int emergencySaves;
        public final int failures;
        public final int pendingCount;
        public final int timestampCount;

        public SettingsProtectionStats(int protectionSaves, int pendingApplied, int emergencySaves,
                                       int failures, int pendingCount, int timestampCount) {
            this.protectionSaves = protectionSaves;
            this.pendingApplied = pendingApplied;
            this.emergencySaves = emergencySaves;
            this.failures = failures;
            this.pendingCount = pendingCount;
            this.timestampCount = timestampCount;
        }

        @Override
        public String toString() {
            return String.format("SettingsProtectionStats{saves=%d, applied=%d, emergency=%d, failures=%d, pending=%d, timestamps=%d}",
                    protectionSaves, pendingApplied, emergencySaves, failures, pendingCount, timestampCount);
        }
    }

    /**
     * UPDATED: Enhanced system statistics class with settings protection metrics
     */
    public static class SystemStats {
        public final int totalJoins;
        public final int totalQuits;
        public final int currentOnline;
        public final boolean systemHealthy;
        public final boolean systemsReady;
        public final boolean yakPlayerManagerIntegrated;
        public final int loadingPlayers;
        public final int protectedPlayers;
        public final int limboPlayers;
        public final int successfulLoads;
        public final int failedLoads;
        public final int combatLogoutRejoins;
        public final int combatLogoutCoordinations;
        public final SettingsProtectionStats settingsStats;

        SystemStats(int totalJoins, int totalQuits, int currentOnline, boolean systemHealthy,
                    boolean systemsReady, boolean yakPlayerManagerIntegrated,
                    int loadingPlayers, int protectedPlayers, int limboPlayers,
                    int successfulLoads, int failedLoads, int combatLogoutRejoins,
                    int combatLogoutCoordinations, SettingsProtectionStats settingsStats) {
            this.totalJoins = totalJoins;
            this.totalQuits = totalQuits;
            this.currentOnline = currentOnline;
            this.systemHealthy = systemHealthy;
            this.systemsReady = systemsReady;
            this.yakPlayerManagerIntegrated = yakPlayerManagerIntegrated;
            this.loadingPlayers = loadingPlayers;
            this.protectedPlayers = protectedPlayers;
            this.limboPlayers = limboPlayers;
            this.successfulLoads = successfulLoads;
            this.failedLoads = failedLoads;
            this.combatLogoutRejoins = combatLogoutRejoins;
            this.combatLogoutCoordinations = combatLogoutCoordinations;
            this.settingsStats = settingsStats;
        }

        @Override
        public String toString() {
            return String.format("SystemStats{joins=%d, quits=%d, online=%d, healthy=%s, ready=%s, " +
                            "integrated=%s, loading=%d, protected=%d, limbo=%d, loads=%d/%d, combat=%d/%d, settings=%s}",
                    totalJoins, totalQuits, currentOnline, systemHealthy, systemsReady,
                    yakPlayerManagerIntegrated, loadingPlayers, protectedPlayers, limboPlayers,
                    successfulLoads, failedLoads, combatLogoutRejoins, combatLogoutCoordinations, settingsStats);
        }
    }

    /**
     * Player loading state tracker
     */
    private static class PlayerLoadingState {
        private final UUID playerId;
        private final String playerName;
        private long limboStartTime;
        private volatile LoadingPhase phase = LoadingPhase.JOINING;
        private volatile YakPlayer yakPlayer;
        private volatile BukkitTask monitorTask;

        // Original state storage
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
        public void setLimboStartTime(long limboStartTime) { this.limboStartTime = limboStartTime; }
        public void setPhase(LoadingPhase phase) { this.phase = phase; }
        public YakPlayer getYakPlayer() { return yakPlayer; }
        public void setYakPlayer(YakPlayer yakPlayer) { this.yakPlayer = yakPlayer; }
        public BukkitTask getMonitorTask() { return monitorTask; }
        public void setMonitorTask(BukkitTask monitorTask) { this.monitorTask = monitorTask; }
        public Location getOriginalLocation() { return originalLocation; }
        public GameMode getOriginalGameMode() { return originalGameMode; }
    }
}
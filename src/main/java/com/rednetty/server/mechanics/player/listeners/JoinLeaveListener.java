package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.party.PartyMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Enhanced player join and leave event handler with improved null safety,
 * better data loading handling, and comprehensive error recovery mechanisms.
 */
public class JoinLeaveListener extends BaseListener implements YakPlayerManager.PlayerLoadListener {

    // Core dependencies
    private final YakPlayerManager playerManager;
    private final HealthListener healthListener;
    private final EconomyManager economyManager;
    private final PartyMechanics partyMechanics;
    private final ChatMechanics chatMechanics;

    // Enhanced state tracking with thread safety
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, LoginStreak> loginStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> welcomeBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private final Set<UUID> firstTimeJoins = ConcurrentHashMap.newKeySet();
    private final Map<UUID, WelcomeProgress> welcomeProgress = new ConcurrentHashMap<>();

    // Enhanced data loading tracking
    private final Map<UUID, PlayerLoadingContext> playersWaitingForData = new ConcurrentHashMap<>();
    private final Set<UUID> playersWithFailedLoads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> playerLoadRetryCount = new ConcurrentHashMap<>();

    // Server metrics and analytics
    private final ServerMetrics serverMetrics = new ServerMetrics();
    private final PlayerAnalytics playerAnalytics = new PlayerAnalytics();

    // Configuration with validation
    private volatile boolean enableAdvancedWelcome = true;
    private volatile boolean enableLoginRewards = true;
    private volatile boolean enableSocialNotifications = true;
    private volatile boolean enableSeasonalThemes = true;
    private volatile boolean enablePlayerAnalytics = true;
    private volatile int maxWelcomeSteps = 5;
    private volatile long notificationCooldown = 5000; // 5 seconds
    private static final long MAX_DATA_WAIT_TIME = 45000L; // 45 seconds max wait
    private static final long DATA_RETRY_INTERVAL = 5000L; // 5 seconds between retries
    private static final int MAX_LOAD_RETRIES = 3;

    // Task management
    private BukkitTask metricsTask;
    private BukkitTask welcomeTask;
    private BukkitTask cleanupTask;
    private BukkitTask dataWaitMonitorTask;
    private BukkitTask fallbackInitTask;

    /**
     * Enhanced player loading context for better tracking
     */
    private static class PlayerLoadingContext {
        private final UUID playerId;
        private final String playerName;
        private final long waitStartTime;
        private final AtomicBoolean resolved = new AtomicBoolean(false);
        private final AtomicBoolean fallbackInitialized = new AtomicBoolean(false);
        private final AtomicInteger retryCount = new AtomicInteger(0);
        private volatile String lastError = "";

        public PlayerLoadingContext(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.waitStartTime = System.currentTimeMillis();
        }

        public long getWaitDuration() {
            return System.currentTimeMillis() - waitStartTime;
        }

        public boolean isExpired() {
            return getWaitDuration() > MAX_DATA_WAIT_TIME;
        }

        public boolean shouldRetry() {
            return !resolved.get() && retryCount.get() < MAX_LOAD_RETRIES && !isExpired();
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getWaitStartTime() { return waitStartTime; }
        public boolean isResolved() { return resolved.get(); }
        public boolean isFallbackInitialized() { return fallbackInitialized.get(); }
        public int getRetryCount() { return retryCount.get(); }
        public String getLastError() { return lastError; }

        // State modifiers
        public void setResolved() { resolved.set(true); }
        public void setFallbackInitialized() { fallbackInitialized.set(true); }
        public int incrementRetryCount() { return retryCount.incrementAndGet(); }
        public void setLastError(String error) { this.lastError = error; }
    }

    /**
     * Enhanced player session with better tracking
     */
    private static class PlayerSession {
        private final UUID playerId;
        private final long joinTime;
        private final String joinLocation;
        private final int onlinePlayersAtJoin;
        private final AtomicBoolean hasReceivedWelcome = new AtomicBoolean(false);
        private final AtomicBoolean hasReceivedKit = new AtomicBoolean(false);
        private final AtomicBoolean hasReceivedMotd = new AtomicBoolean(false);
        private final AtomicInteger welcomeStep = new AtomicInteger(0);
        private final Map<String, Object> sessionData = new ConcurrentHashMap<>();
        private final AtomicBoolean dataLoadCompleted = new AtomicBoolean(false);
        private final AtomicLong dataLoadTime = new AtomicLong(0);

        public PlayerSession(UUID playerId, Location joinLoc, int onlineCount) {
            this.playerId = playerId;
            this.joinTime = System.currentTimeMillis();
            this.joinLocation = formatLocation(joinLoc);
            this.onlinePlayersAtJoin = onlineCount;
        }

        // Enhanced getters and setters
        public UUID getPlayerId() { return playerId; }
        public long getJoinTime() { return joinTime; }
        public String getJoinLocation() { return joinLocation; }
        public int getOnlinePlayersAtJoin() { return onlinePlayersAtJoin; }

        public boolean hasReceivedWelcome() { return hasReceivedWelcome.get(); }
        public void setReceivedWelcome(boolean received) { hasReceivedWelcome.set(received); }

        public boolean hasReceivedKit() { return hasReceivedKit.get(); }
        public void setReceivedKit(boolean received) { hasReceivedKit.set(received); }

        public boolean hasReceivedMotd() { return hasReceivedMotd.get(); }
        public void setReceivedMotd(boolean received) { hasReceivedMotd.set(received); }

        public int getWelcomeStep() { return welcomeStep.get(); }
        public void setWelcomeStep(int step) { welcomeStep.set(step); }

        public boolean isDataLoadCompleted() { return dataLoadCompleted.get(); }
        public void setDataLoadCompleted(boolean completed) {
            dataLoadCompleted.set(completed);
            if (completed) {
                dataLoadTime.set(System.currentTimeMillis());
            }
        }

        public long getDataLoadDuration() {
            long loadTime = dataLoadTime.get();
            return loadTime > 0 ? loadTime - joinTime : System.currentTimeMillis() - joinTime;
        }

        public long getSessionDuration() {
            return System.currentTimeMillis() - joinTime;
        }

        public void setSessionData(String key, Object value) {
            sessionData.put(key, value);
        }

        public Object getSessionData(String key) {
            return sessionData.get(key);
        }

        private static String formatLocation(Location loc) {
            if (loc == null || loc.getWorld() == null) {
                return "unknown";
            }
            return String.format("%s(%.1f,%.1f,%.1f)",
                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        }
    }

    /**
     * Enhanced login streak tracking
     */
    private static class LoginStreak {
        private final AtomicInteger currentStreak = new AtomicInteger(0);
        private final AtomicInteger longestStreak = new AtomicInteger(0);
        private final AtomicLong lastLoginDate = new AtomicLong(0);
        private final List<Long> recentLogins = Collections.synchronizedList(new ArrayList<>());

        public void recordLogin() {
            long today = System.currentTimeMillis() / (24 * 60 * 60 * 1000); // Days since epoch

            if (lastLoginDate.get() == today - 1) {
                // Consecutive day
                currentStreak.incrementAndGet();
            } else if (lastLoginDate.get() != today) {
                // New streak or broken streak
                currentStreak.set(1);
            }

            if (currentStreak.get() > longestStreak.get()) {
                longestStreak.set(currentStreak.get());
            }

            lastLoginDate.set(today);
            synchronized (recentLogins) {
                recentLogins.add(System.currentTimeMillis());
                // Keep only last 30 logins
                while (recentLogins.size() > 30) {
                    recentLogins.remove(0);
                }
            }
        }

        public int getCurrentStreak() { return currentStreak.get(); }
        public int getLongestStreak() { return longestStreak.get(); }
        public boolean isNewStreak() { return currentStreak.get() == 1; }
        public boolean isStreakMilestone() {
            int streak = currentStreak.get();
            return streak > 1 && (streak % 7 == 0 || streak % 30 == 0);
        }

        public List<Long> getRecentLogins() {
            synchronized (recentLogins) {
                return new ArrayList<>(recentLogins);
            }
        }
    }

    /**
     * Enhanced welcome progress tracking
     */
    private static class WelcomeProgress {
        private final Set<String> completedSteps = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean isComplete = new AtomicBoolean(false);
        private final AtomicInteger currentStep = new AtomicInteger(0);
        private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

        public void completeStep(String stepName) {
            completedSteps.add(stepName);
            currentStep.incrementAndGet();
        }

        public boolean hasCompleted(String stepName) {
            return completedSteps.contains(stepName);
        }

        public int getCurrentStep() { return currentStep.get(); }
        public boolean isComplete() { return isComplete.get(); }
        public void setComplete(boolean complete) { isComplete.set(complete); }
        public Set<String> getCompletedSteps() { return new HashSet<>(completedSteps); }
        public long getWelcomeProcessDuration() {
            return System.currentTimeMillis() - startTime.get();
        }
    }

    /**
     * Enhanced server metrics tracking
     */
    private static class ServerMetrics {
        private final AtomicInteger totalJoinsToday = new AtomicInteger(0);
        private final AtomicInteger totalLeavesToday = new AtomicInteger(0);
        private final AtomicInteger peakOnlineToday = new AtomicInteger(0);
        private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());
        private final Map<String, AtomicInteger> hourlyJoins = new ConcurrentHashMap<>();
        private final List<Integer> onlineHistory = Collections.synchronizedList(new ArrayList<>());

        public void recordJoin() {
            checkDayReset();
            totalJoinsToday.incrementAndGet();

            int hour = LocalDateTime.now().getHour();
            hourlyJoins.computeIfAbsent(String.valueOf(hour), k -> new AtomicInteger(0)).incrementAndGet();

            int currentOnline = Bukkit.getOnlinePlayers().size();
            if (currentOnline > peakOnlineToday.get()) {
                peakOnlineToday.set(currentOnline);
            }

            synchronized (onlineHistory) {
                onlineHistory.add(currentOnline);
                if (onlineHistory.size() > 1440) { // Keep 24 hours of minute data
                    onlineHistory.remove(0);
                }
            }
        }

        public void recordLeave() {
            checkDayReset();
            totalLeavesToday.incrementAndGet();
        }

        private void checkDayReset() {
            long now = System.currentTimeMillis();
            if (now - lastResetTime.get() > 24 * 60 * 60 * 1000) { // 24 hours
                totalJoinsToday.set(0);
                totalLeavesToday.set(0);
                peakOnlineToday.set(0);
                hourlyJoins.clear();
                lastResetTime.set(now);
            }
        }

        public int getTotalJoinsToday() { return totalJoinsToday.get(); }
        public int getTotalLeavesToday() { return totalLeavesToday.get(); }
        public int getPeakOnlineToday() { return peakOnlineToday.get(); }
        public Map<String, Integer> getHourlyJoins() {
            return hourlyJoins.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        }
        public List<Integer> getOnlineHistory() {
            synchronized (onlineHistory) {
                return new ArrayList<>(onlineHistory);
            }
        }
    }

    /**
     * Enhanced player analytics
     */
    private static class PlayerAnalytics {
        private final Map<String, AtomicInteger> joinReasons = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> leaveReasons = new ConcurrentHashMap<>();
        private final Map<UUID, List<String>> playerBehaviorPatterns = new ConcurrentHashMap<>();

        public void recordJoinReason(String reason) {
            joinReasons.computeIfAbsent(reason, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public void recordLeaveReason(String reason) {
            leaveReasons.computeIfAbsent(reason, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public void recordPlayerBehavior(UUID playerId, String behavior) {
            playerBehaviorPatterns.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(behavior);
        }

        public Map<String, Integer> getJoinReasons() {
            return joinReasons.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        }

        public Map<String, Integer> getLeaveReasons() {
            return leaveReasons.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
        }

        public List<String> getPlayerBehavior(UUID playerId) {
            List<String> behaviors = playerBehaviorPatterns.get(playerId);
            return behaviors != null ? new ArrayList<>(behaviors) : new ArrayList<>();
        }
    }

    public JoinLeaveListener() {
        this.playerManager = YakPlayerManager.getInstance();
        this.healthListener = new HealthListener();
        this.economyManager = YakRealms.getInstance().getEconomyManager();
        this.partyMechanics = PartyMechanics.getInstance();
        this.chatMechanics = ChatMechanics.getInstance();

        // Register as a load listener
        playerManager.addPlayerLoadListener(this);

        // Load configuration
        loadConfiguration();
    }

    @Override
    public void initialize() {
        logger.info("Enhanced Join/Leave listener initialized with improved data loading and null safety");
        startTasks();
    }

    @Override
    public void cleanup() {
        // Cancel tasks
        cancelTask(metricsTask);
        cancelTask(welcomeTask);
        cancelTask(cleanupTask);
        cancelTask(dataWaitMonitorTask);
        cancelTask(fallbackInitTask);

        // Clear boss bars
        welcomeBars.values().forEach(BossBar::removeAll);
        welcomeBars.clear();

        // Unregister from player manager
        playerManager.removePlayerLoadListener(this);
        super.cleanup();
    }

    private void loadConfiguration() {
        var config = YakRealms.getInstance().getConfig();
        this.enableAdvancedWelcome = config.getBoolean("join_leave.advanced_welcome", true);
        this.enableLoginRewards = config.getBoolean("join_leave.login_rewards", true);
        this.enableSocialNotifications = config.getBoolean("join_leave.social_notifications", true);
        this.enableSeasonalThemes = config.getBoolean("join_leave.seasonal_themes", true);
        this.enablePlayerAnalytics = config.getBoolean("join_leave.player_analytics", true);
        this.maxWelcomeSteps = config.getInt("join_leave.max_welcome_steps", 5);
        this.notificationCooldown = config.getLong("join_leave.notification_cooldown", 5000);
    }

    private void startTasks() {
        // Metrics collection task
        metricsTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateServerMetrics();
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L * 60); // Every minute

        // Welcome progress task
        welcomeTask = new BukkitRunnable() {
            @Override
            public void run() {
                processWelcomeProgress();
            }
        }.runTaskTimer(plugin, 20L, 20L * 5); // Every 5 seconds

        // Cleanup task
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupStaleData();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 300, 20L * 300); // Every 5 minutes

        // Enhanced data wait monitoring task
        dataWaitMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                monitorDataLoadingProgress();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second

        // Fallback initialization task
        fallbackInitTask = new BukkitRunnable() {
            @Override
            public void run() {
                processFallbackInitializations();
            }
        }.runTaskTimer(plugin, 20L * 3, 20L * 3); // Every 3 seconds
    }

    /**
     * Enhanced data loading monitoring with retry logic
     */
    private void monitorDataLoadingProgress() {
        long currentTime = System.currentTimeMillis();
        List<PlayerLoadingContext> toProcess = new ArrayList<>();

        for (PlayerLoadingContext context : playersWaitingForData.values()) {
            Player player = Bukkit.getPlayer(context.getPlayerId());

            if (player == null || !player.isOnline()) {
                toProcess.add(context);
                continue;
            }

            if (context.isExpired()) {
                logger.warning("Data loading expired for " + context.getPlayerName() +
                        " after " + (context.getWaitDuration() / 1000) + " seconds");
                toProcess.add(context);
            } else if (context.shouldRetry() &&
                    (currentTime - context.getWaitStartTime()) % DATA_RETRY_INTERVAL < 1000) {
                // Attempt retry
                attemptDataLoadRetry(context);
            }
        }

        // Process expired or completed contexts
        for (PlayerLoadingContext context : toProcess) {
            playersWaitingForData.remove(context.getPlayerId());

            Player player = Bukkit.getPlayer(context.getPlayerId());
            if (player != null && player.isOnline()) {
                if (context.isExpired()) {
                    handleDataLoadTimeout(player, context);
                }
            }
        }
    }

    /**
     * Attempt to retry data loading for a player
     */
    private void attemptDataLoadRetry(PlayerLoadingContext context) {
        context.incrementRetryCount();
        logger.info("Attempting data load retry " + context.getRetryCount() + "/" + MAX_LOAD_RETRIES +
                " for " + context.getPlayerName());

        Player player = Bukkit.getPlayer(context.getPlayerId());
        if (player == null || !player.isOnline()) {
            return;
        }

        // Check if data is now available
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            logger.info("Data load retry successful for " + context.getPlayerName());
            context.setResolved();
            playersWaitingForData.remove(context.getPlayerId());

            // Process the successfully loaded player
            PlayerSession session = activeSessions.get(context.getPlayerId());
            if (session != null) {
                session.setDataLoadCompleted(true);
                performDataDependentSetup(player, yakPlayer, false, session);
            }
        } else {
            logger.fine("Data load retry " + context.getRetryCount() + " failed for " + context.getPlayerName());
            player.sendMessage(ChatColor.YELLOW + "⚠ Still loading character data... (attempt " +
                    context.getRetryCount() + "/" + MAX_LOAD_RETRIES + ")");
        }
    }

    /**
     * Process fallback initializations for players with failed data loads
     */
    private void processFallbackInitializations() {
        for (UUID playerId : playersWithFailedLoads) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                playersWithFailedLoads.remove(playerId);
                continue;
            }

            PlayerLoadingContext context = playersWaitingForData.get(playerId);
            if (context != null && !context.isFallbackInitialized()) {
                initializeFallbackPlayer(player, context);
            }
        }
    }

    /**
     * Handle server lockdown check
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (isPatchLockdown()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, createLockdownMessage());
            return;
        }

        // Analytics
        if (enablePlayerAnalytics) {
            analyzeJoinAttempt(event);
        }
    }

    /**
     * Enhanced player join handler with improved null safety and data loading
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Record metrics
            serverMetrics.recordJoin();

            // Create player session
            PlayerSession session = new PlayerSession(playerId, player.getLocation(), Bukkit.getOnlinePlayers().size());
            activeSessions.put(playerId, session);

            // Set join message
            setEnhancedJoinMessage(event, player);

            // Initialize player attributes immediately (doesn't require YakPlayer data)
            initializePlayerAttributes(player);

            // Analytics
            if (enablePlayerAnalytics) {
                playerAnalytics.recordJoinReason(determineJoinReason(player));
                playerAnalytics.recordPlayerBehavior(playerId, "joined_server");
            }

            // Play join sound
            playJoinSound(player);

            // Handle server announcements
            handleServerAnnouncements(player, session);

            // Check for existing YakPlayer data
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                // Data already loaded - immediate setup
                logger.info("Player " + player.getName() + " joined with pre-loaded data");
                session.setDataLoadCompleted(true);
                performDataDependentSetup(player, yakPlayer, false, session);
            } else {
                // Data not yet loaded - set up waiting context
                logger.info("Player " + player.getName() + " joined - waiting for data load...");
                PlayerLoadingContext loadingContext = new PlayerLoadingContext(playerId, player.getName());
                playersWaitingForData.put(playerId, loadingContext);

                // Send loading message
                player.sendMessage(ChatColor.YELLOW + "⚠ Loading your character data...");

                // Perform immediate setup that doesn't require YakPlayer data
                performImmediateJoinSetup(player, session);
            }

        } catch (Exception ex) {
            logger.severe("Error in enhanced onJoin for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();

            // Emergency fallback
            initializeEmergencyFallback(player);
        }
    }

    /**
     * Immediate setup that doesn't require YakPlayer data
     */
    private void performImmediateJoinSetup(Player player, PlayerSession session) {
        try {
            // Send basic MOTD if possible
            if (!session.hasReceivedMotd()) {
                sendBasicMotd(player);
                session.setReceivedMotd(true);
            }

            // Try to set basic toggle (fallback if YakPlayer not available)
            try {
                Toggles.setToggle(player, "Player Messages", true);
            } catch (Exception e) {
                logger.fine("Could not set basic toggle for " + player.getName() + " (expected if no YakPlayer): " + e.getMessage());
            }

        } catch (Exception ex) {
            logger.warning("Error in immediate join setup for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Handle data load timeout with comprehensive fallback
     */
    private void handleDataLoadTimeout(Player player, PlayerLoadingContext context) {
        logger.warning("Data load timeout for " + player.getName() + " after " +
                (context.getWaitDuration() / 1000) + " seconds");

        PlayerSession session = activeSessions.get(player.getUniqueId());
        if (session != null && !context.isFallbackInitialized()) {
            initializeFallbackPlayer(player, context);
        }
    }

    /**
     * Initialize fallback player state when data loading fails
     */
    private void initializeFallbackPlayer(Player player, PlayerLoadingContext context) {
        context.setFallbackInitialized();
        playersWithFailedLoads.add(player.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Send user-friendly messages
                player.sendMessage(ChatColor.RED + "⚠ Character data loading timed out");
                player.sendMessage(ChatColor.YELLOW + "You can play with limited functionality");
                player.sendMessage(ChatColor.GRAY + "Try reconnecting or contact an admin if this persists");

                // Initialize basic attributes
                initializePlayerAttributes(player);

                // Set basic defaults
                try {
                    Toggles.setToggle(player, "Player Messages", true);
                } catch (Exception e) {
                    logger.fine("Could not set fallback toggle: " + e.getMessage());
                }

                // Schedule periodic data check
                schedulePeriodicDataCheck(player);

            } catch (Exception e) {
                logger.severe("Error in fallback player initialization for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Schedule periodic checks to see if data becomes available
     */
    private void schedulePeriodicDataCheck(Player player) {
        new BukkitRunnable() {
            int attempts = 0;
            final int maxAttempts = 60; // Check for 5 minutes (every 5 seconds)

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    // Data finally loaded!
                    player.sendMessage(ChatColor.GREEN + "✓ Character data loaded successfully!");

                    PlayerSession session = activeSessions.get(player.getUniqueId());
                    if (session != null) {
                        session.setDataLoadCompleted(true);
                        performDataDependentSetup(player, yakPlayer, false, session);
                    }

                    playersWithFailedLoads.remove(player.getUniqueId());
                    playersWaitingForData.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                attempts++;
                if (attempts >= maxAttempts) {
                    player.sendMessage(ChatColor.RED + "⚠ Character data loading failed permanently");
                    player.sendMessage(ChatColor.GRAY + "Please reconnect or contact an administrator");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L * 5, 20L * 5); // Check every 5 seconds
    }

    /**
     * Emergency fallback for critical errors
     */
    private void initializeEmergencyFallback(Player player) {
        logger.severe("Initializing emergency fallback for " + player.getName());

        try {
            initializePlayerAttributes(player);
            player.sendMessage(ChatColor.RED + "⚠ Emergency mode active - limited functionality");
            player.sendMessage(ChatColor.GRAY + "Please reconnect or contact an administrator");
        } catch (Exception e) {
            logger.severe("Emergency fallback failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * YakPlayerManager callback - player successfully loaded
     */
    @Override
    public void onPlayerLoaded(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                UUID playerId = player.getUniqueId();
                PlayerSession session = activeSessions.get(playerId);

                // Remove from waiting list
                PlayerLoadingContext loadingContext = playersWaitingForData.remove(playerId);
                if (loadingContext != null) {
                    loadingContext.setResolved();
                    logger.info("YakPlayer data loaded for " + player.getName() +
                            " after " + (loadingContext.getWaitDuration() / 1000) + " seconds (new: " + isNewPlayer + ")");
                } else {
                    logger.info("YakPlayer data loaded for " + player.getName() + " (new: " + isNewPlayer + ")");
                }

                // Remove from failed loads if present
                playersWithFailedLoads.remove(playerId);

                if (session != null) {
                    session.setSessionData("yakPlayer", yakPlayer);
                    session.setSessionData("isNewPlayer", isNewPlayer);
                    session.setDataLoadCompleted(true);
                }

                // Clear any loading messages and perform full setup
                player.sendMessage(ChatColor.GREEN + "✓ Character data loaded!");
                performDataDependentSetup(player, yakPlayer, isNewPlayer, session);

            } catch (Exception ex) {
                logger.severe("Error in enhanced onPlayerLoaded for " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /**
     * YakPlayerManager callback - player load failed
     */
    @Override
    public void onPlayerLoadFailed(Player player, Exception exception) {
        UUID playerId = player.getUniqueId();

        // Remove from waiting list and add to failed loads
        PlayerLoadingContext loadingContext = playersWaitingForData.remove(playerId);
        playersWithFailedLoads.add(playerId);

        if (loadingContext != null) {
            loadingContext.setLastError(exception.getMessage());
        }

        logger.severe("YakPlayer load failed for " + player.getName() + ": " + exception.getMessage());

        Bukkit.getScheduler().runTask(plugin, () -> {
            handleLoadFailure(player, exception);
        });
    }

    /**
     * YakPlayerManager callback - player load timeout
     */
    @Override
    public void onPlayerLoadTimeout(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerLoadingContext loadingContext = playersWaitingForData.get(playerId);

        if (loadingContext != null) {
            handleDataLoadTimeout(player, loadingContext);
        }
    }

    /**
     * Enhanced player quit handler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Record metrics
            serverMetrics.recordLeave();

            // Get session data
            PlayerSession session = activeSessions.remove(playerId);
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            // Set enhanced quit message
            setEnhancedQuitMessage(event, player, yakPlayer, session);

            // Analytics
            if (enablePlayerAnalytics && session != null) {
                String leaveReason = determineLeaveReason(player, session);
                playerAnalytics.recordLeaveReason(leaveReason);
                playerAnalytics.recordPlayerBehavior(playerId, "left_server");

                // Log data loading performance
                if (session.isDataLoadCompleted()) {
                    long loadDuration = session.getDataLoadDuration();
                    logger.fine("Player " + player.getName() + " data load took " + loadDuration + "ms");
                }
            }

            // Handle social notifications
            if (enableSocialNotifications && yakPlayer != null) {
                handleBuddyNotifications(player, false);
                if (partyMechanics != null) {
                    handlePartyNotifications(player, false);
                }
                handleGuildNotifications(player, false);
            }

            // Cleanup
            cleanupPlayerData(playerId);

            // Show logout effects
            if (yakPlayer != null) {
                showLogoutEffects(player, yakPlayer);
            }

        } catch (Exception ex) {
            logger.warning("Error in enhanced onPlayerQuit for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Enhanced player kick handler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Get session and player data
            PlayerSession session = activeSessions.remove(playerId);
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            // Set enhanced kick message
            setEnhancedKickMessage(event, player, yakPlayer);

            // Handle social notifications
            if (enableSocialNotifications && yakPlayer != null) {
                handleBuddyNotifications(player, true);
                if (partyMechanics != null) {
                    handlePartyNotifications(player, true);
                }
            }

            // Notify staff
            notifyStaffOfKick(player, event.getReason());

            // Analytics
            if (enablePlayerAnalytics) {
                playerAnalytics.recordLeaveReason("kicked: " + event.getReason());
                playerAnalytics.recordPlayerBehavior(playerId, "kicked_from_server");
            }

            // Cleanup
            cleanupPlayerData(playerId);

        } catch (Exception ex) {
            logger.warning("Error in enhanced onPlayerKick for " + player.getName() + ": " + ex.getMessage());
        }
    }

    // Enhanced setup methods
    private void initializePlayerAttributes(Player player) {
        try {
            // Set attack speed attribute to prevent attack cooldowns
            if (player.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
                player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(1024.0D);
            }

            // Set initial health display
            player.setHealthScale(20.0);
            player.setHealthScaled(true);

            // Set initial experience display
            player.setExp(1.0f);
            player.setLevel(100);

        } catch (Exception e) {
            logger.warning("Error initializing attributes for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void performDataDependentSetup(Player player, YakPlayer yakPlayer, boolean isNewPlayer, PlayerSession session) {
        UUID playerId = player.getUniqueId();

        try {
            // Send MOTD with player data if not already sent
            if (session != null && !session.hasReceivedMotd()) {
                sendEnhancedMotd(player, yakPlayer);
                session.setReceivedMotd(true);
            }

            // Set proper default toggle if needed
            if (!yakPlayer.isToggled("Player Messages")) {
                yakPlayer.toggleSetting("Player Messages");
                playerManager.savePlayer(yakPlayer);
            }

            // Check health after data is loaded
            if (!player.isDead()) {
                healthListener.recalculateHealth(player);
            }

            // Handle login streaks and rewards
            if (enableLoginRewards) {
                handleLoginStreak(player, yakPlayer);
                processLoginRewards(player, yakPlayer, isNewPlayer);
            }

            // Handle GM mode for operators
            handleGMMode(player);

            // Enhanced welcome experience for new players
            if (isNewPlayer && enableAdvancedWelcome) {
                scheduleNewPlayerWelcome(player, session);
                firstTimeJoins.add(playerId);
            } else if (enableAdvancedWelcome) {
                scheduleReturningPlayerWelcome(player, yakPlayer, session);
            }

            // Social integration
            if (enableSocialNotifications) {
                handleSocialIntegration(player, yakPlayer, isNewPlayer);
            }

            // Seasonal and special events
            if (enableSeasonalThemes) {
                handleSeasonalContent(player, yakPlayer);
            }

            // Achievement checks
            checkJoinAchievements(player, yakPlayer, isNewPlayer);

        } catch (Exception ex) {
            logger.severe("Error in data-dependent setup for " + player.getName() + ": " + ex.getMessage());
        }
    }

    // Enhanced messaging systems
    private void setEnhancedJoinMessage(PlayerJoinEvent event, Player player) {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();

            // Context-aware join messages
            if (onlineCount == 1) {
                event.setJoinMessage(ChatColor.GREEN + "✦ " + ChatColor.BOLD + formattedName +
                        ChatColor.GREEN + " started the adventure! ✦");
            } else if (isVip(player)) {
                event.setJoinMessage(ChatColor.AQUA + "✦ VIP " + formattedName +
                        ChatColor.AQUA + " has joined! ✦");
            } else if (isStaff(player)) {
                event.setJoinMessage(ChatColor.RED + "✦ Staff " + formattedName +
                        ChatColor.RED + " is now online! ✦");
            } else if (isFirstTimeJoin(player)) {
                event.setJoinMessage(ChatColor.GOLD + "✦ Welcome " + formattedName +
                        ChatColor.GOLD + " to YakRealms! ✦");
            } else if (onlineCount <= 5) {
                event.setJoinMessage(ChatColor.AQUA + "[+] " + formattedName +
                        ChatColor.GRAY + " (" + onlineCount + " online)");
            } else if (onlineCount <= 20) {
                event.setJoinMessage(ChatColor.AQUA + "[+] " + formattedName);
            } else {
                // High population - minimal message
                event.setJoinMessage(null);
            }
        } else {
            // Fallback when player data isn't loaded yet
            event.setJoinMessage(ChatColor.AQUA + "[+] " + ChatColor.GRAY + player.getName() +
                    ChatColor.DARK_GRAY + " (loading...)");
        }
    }

    private void setEnhancedQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer, PlayerSession session) {
        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();

            // Context-aware quit messages
            if (session != null && session.getSessionDuration() < 30000) { // Less than 30 seconds
                event.setQuitMessage(ChatColor.GRAY + "[-] " + formattedName + ChatColor.DARK_GRAY + " (quick leave)");
            } else if (isInCombat(player)) {
                event.setQuitMessage(ChatColor.RED + "[-] " + formattedName + ChatColor.DARK_RED + " (combat logged)");
            } else if (Bukkit.getOnlinePlayers().size() <= 3) {
                event.setQuitMessage(ChatColor.RED + "[-] " + formattedName + ChatColor.GRAY + " left the server");
            } else {
                event.setQuitMessage(ChatColor.RED + "[-] " + formattedName);
            }
        } else {
            event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + player.getName());
        }
    }

    private void setEnhancedKickMessage(PlayerKickEvent event, Player player, YakPlayer yakPlayer) {
        if (yakPlayer != null) {
            String formattedName = yakPlayer.getFormattedDisplayName();
            event.setLeaveMessage(ChatColor.RED + "[-] " + formattedName +
                    ChatColor.DARK_RED + " (kicked)");
        } else {
            event.setLeaveMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + player.getName() +
                    ChatColor.DARK_RED + " (kicked)");
        }
    }

    // Complete implementation of all previously placeholder methods

    private void sendBasicMotd(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Welcome to " + ChatColor.GOLD + ChatColor.BOLD + "YakRealms" + ChatColor.AQUA + "!");
        player.sendMessage(ChatColor.GRAY + "Loading your character data...");
        player.sendMessage("");
    }

    private void sendEnhancedMotd(Player player, YakPlayer yakPlayer) {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + ChatColor.BOLD.toString() + "     Welcome to YakRealms!     ");
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✓ Character data loaded successfully");
        player.sendMessage(ChatColor.YELLOW + "Alignment: " + yakPlayer.getAlignment());
        player.sendMessage(ChatColor.GOLD + "Gems: " + yakPlayer.getGems());

        if (yakPlayer.isInGuild()) {
            player.sendMessage(ChatColor.BLUE + "Guild: " + yakPlayer.getGuildName());
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.WHITE + "/help" + ChatColor.GRAY + " for assistance");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════");
        player.sendMessage("");
    }

    private void handleLoginStreak(Player player, YakPlayer yakPlayer) {
        LoginStreak streak = loginStreaks.computeIfAbsent(player.getUniqueId(), k -> new LoginStreak());
        streak.recordLogin();

        if (streak.isStreakMilestone()) {
            player.sendMessage(ChatColor.GOLD + "🔥 Login Streak: " + streak.getCurrentStreak() + " days!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

            // Reward gems for streaks
            int rewardGems = Math.min(streak.getCurrentStreak() * 10, 500);
            yakPlayer.addGems(rewardGems, "Login Streak");
        }
    }

    private void processLoginRewards(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        long today = System.currentTimeMillis() / (24 * 60 * 60 * 1000);
        String todayKey = "daily_" + today;

        if (!yakPlayer.getDailyRewardsClaimed().contains(todayKey)) {
            // Daily reward
            int dailyReward = 100 + (yakPlayer.getLevel() * 5);
            yakPlayer.addGems(dailyReward, "Daily Login Reward");
            yakPlayer.getDailyRewardsClaimed().add(todayKey);

            player.sendMessage(ChatColor.GREEN + "Daily Reward: +" + dailyReward + " gems!");
        }

        if (isNewPlayer) {
            // New player kit
            giveNewPlayerKit(player, yakPlayer);
        }
    }

    private void giveNewPlayerKit(Player player, YakPlayer yakPlayer) {
        // Give starter items
        player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 32));
        player.getInventory().addItem(new ItemStack(Material.TORCH, 64));

        // Give starter gems
        yakPlayer.addGems(500, "New Player Kit");

        player.sendMessage(ChatColor.GOLD + "✦ Welcome Kit Received! ✦");
        player.sendMessage(ChatColor.GREEN + "+ Iron Sword");
        player.sendMessage(ChatColor.GREEN + "+ 32 Cooked Beef");
        player.sendMessage(ChatColor.GREEN + "+ 64 Torches");
        player.sendMessage(ChatColor.GREEN + "+ 500 Gems");
    }

    private void handleGMMode(Player player) {
        if (player.isOp() && player.hasPermission("yakserver.gm")) {
            player.sendMessage(ChatColor.RED + "⚠ GM Mode available - Use /gm to toggle");
        }
    }

    private void scheduleNewPlayerWelcome(Player player, PlayerSession session) {
        WelcomeProgress progress = new WelcomeProgress();
        welcomeProgress.put(player.getUniqueId(), progress);

        // Create welcome boss bar
        BossBar welcomeBar = Bukkit.createBossBar(
                ChatColor.GOLD + "Welcome to YakRealms!",
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );
        welcomeBar.addPlayer(player);
        welcomeBar.setProgress(0.0);
        welcomeBars.put(player.getUniqueId(), welcomeBar);

        // Schedule welcome steps
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline() || step >= maxWelcomeSteps) {
                    cancel();
                    return;
                }

                performWelcomeStep(player, step, progress, welcomeBar);
                step++;
            }
        }.runTaskTimer(plugin, 20L * 3, 20L * 5); // Start after 3 seconds, repeat every 5 seconds
    }

    private void performWelcomeStep(Player player, int step, WelcomeProgress progress, BossBar welcomeBar) {
        switch (step) {
            case 0:
                player.sendMessage(ChatColor.GOLD + "✦ Step 1: Learn the basics with /help");
                welcomeBar.setTitle(ChatColor.GOLD + "Step 1: Getting Help");
                break;
            case 1:
                player.sendMessage(ChatColor.BLUE + "✦ Step 2: Check your stats with /stats");
                welcomeBar.setTitle(ChatColor.BLUE + "Step 2: Your Statistics");
                break;
            case 2:
                player.sendMessage(ChatColor.GREEN + "✦ Step 3: Make friends with /buddy add <player>");
                welcomeBar.setTitle(ChatColor.GREEN + "Step 3: Social Features");
                break;
            case 3:
                player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Step 4: Explore the world and have fun!");
                welcomeBar.setTitle(ChatColor.LIGHT_PURPLE + "Step 4: Adventure Awaits!");
                break;
            case 4:
                player.sendMessage(ChatColor.GOLD + "✦ Welcome process complete! Enjoy YakRealms!");
                welcomeBar.setTitle(ChatColor.GOLD + "Welcome Complete!");
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    welcomeBar.removeAll();
                    welcomeBars.remove(player.getUniqueId());
                }, 20L * 3);
                progress.setComplete(true);
                break;
        }

        welcomeBar.setProgress((step + 1.0) / maxWelcomeSteps);
        progress.completeStep("step_" + step);
    }

    private void scheduleReturningPlayerWelcome(Player player, YakPlayer yakPlayer, PlayerSession session) {
        long lastLogin = yakPlayer.getLastLogin();
        long timeSince = System.currentTimeMillis() / 1000 - lastLogin;

        if (timeSince > 86400) { // More than 1 day
            long daysSince = timeSince / 86400;
            player.sendMessage(ChatColor.YELLOW + "Welcome back! You were last online " + daysSince + " day" + (daysSince == 1 ? "" : "s") + " ago.");
        } else if (timeSince > 3600) { // More than 1 hour
            long hoursSince = timeSince / 3600;
            player.sendMessage(ChatColor.YELLOW + "Welcome back! You were last online " + hoursSince + " hour" + (hoursSince == 1 ? "" : "s") + " ago.");
        }
    }

    private void handleSocialIntegration(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        if (!isNewPlayer) {
            handleBuddyNotifications(player, false);
            if (partyMechanics != null) {
                handlePartyNotifications(player, false);
            }
            handleGuildNotifications(player, false);
        }
    }

    private void handleSeasonalContent(Player player, YakPlayer yakPlayer) {
        LocalDateTime now = LocalDateTime.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();

        // Halloween theme
        if (month == 10 && day >= 25) {
            player.sendMessage(ChatColor.DARK_PURPLE + "🎃 " + ChatColor.GOLD + "Happy Halloween! Spooky events are active!");
        }
        // Christmas theme
        else if (month == 12 && day >= 20) {
            player.sendMessage(ChatColor.RED + "🎄 " + ChatColor.GREEN + "Merry Christmas! Holiday events are active!");
        }
        // New Year
        else if (month == 1 && day <= 7) {
            player.sendMessage(ChatColor.GOLD + "🎊 " + ChatColor.YELLOW + "Happy New Year! Celebration events are active!");
        }
    }

    private void checkJoinAchievements(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        if (isNewPlayer) {
            yakPlayer.unlockAchievement("FIRST_JOIN");
        }

        LoginStreak streak = loginStreaks.get(player.getUniqueId());
        if (streak != null) {
            if (streak.getCurrentStreak() >= 7) {
                yakPlayer.unlockAchievement("WEEK_STREAK");
            }
            if (streak.getCurrentStreak() >= 30) {
                yakPlayer.unlockAchievement("MONTH_STREAK");
            }
        }

        // Check for VIP status
        if (isVip(player)) {
            yakPlayer.unlockAchievement("VIP_STATUS");
        }
    }

    private void handleBuddyNotifications(Player player, boolean isKick) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        long currentTime = System.currentTimeMillis();
        Long lastNotif = lastNotificationTime.get(player.getUniqueId());

        if (lastNotif != null && currentTime - lastNotif < notificationCooldown) {
            return; // Cooldown not expired
        }

        lastNotificationTime.put(player.getUniqueId(), currentTime);

        for (String buddyName : yakPlayer.getBuddies()) {
            Player buddy = Bukkit.getPlayer(buddyName);
            if (buddy != null && buddy.isOnline()) {
                YakPlayer buddyYakPlayer = playerManager.getPlayer(buddy);
                if (buddyYakPlayer != null && buddyYakPlayer.getNotificationSetting("buddy_join")) {
                    if (isKick) {
                        buddy.sendMessage(ChatColor.RED + "🔴 " + ChatColor.GRAY + "Your buddy " +
                                ChatColor.WHITE + player.getName() + ChatColor.GRAY + " was kicked from the server");
                    } else {
                        String action = yakPlayer != null ? "left" : "joined";
                        ChatColor color = yakPlayer != null ? ChatColor.RED : ChatColor.GREEN;
                        String icon = yakPlayer != null ? "🔴" : "🟢";

                        buddy.sendMessage(color + icon + " " + ChatColor.GRAY + "Your buddy " +
                                ChatColor.WHITE + player.getName() + ChatColor.GRAY + " " + action + " the server");
                    }
                }
            }
        }
    }

    private void handlePartyNotifications(Player player, boolean isKick) {
        // Implementation would depend on party system
        if (partyMechanics != null && partyMechanics.isInParty(player)) {
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            for (Player member : partyMembers) {
                if (!member.equals(player)) {
                    if (isKick) {
                        member.sendMessage(ChatColor.YELLOW + "Party member " + player.getName() + " was kicked from the server");
                    } else {
                        member.sendMessage(ChatColor.YELLOW + "Party member " + player.getName() + " left the server");
                    }
                }
            }
        }
    }

    private void handleGuildNotifications(Player player, boolean isKick) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null || !yakPlayer.isInGuild()) return;

        String guildName = yakPlayer.getGuildName();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(player)) continue;

            YakPlayer otherYakPlayer = playerManager.getPlayer(onlinePlayer);
            if (otherYakPlayer != null && guildName.equals(otherYakPlayer.getGuildName())) {
                if (otherYakPlayer.getNotificationSetting("guild_messages")) {
                    if (isKick) {
                        onlinePlayer.sendMessage(ChatColor.BLUE + "[Guild] " + ChatColor.GRAY + player.getName() + " was kicked from the server");
                    } else {
                        onlinePlayer.sendMessage(ChatColor.BLUE + "[Guild] " + ChatColor.GRAY + player.getName() + " left the server");
                    }
                }
            }
        }
    }

    private void showLogoutEffects(Player player, YakPlayer yakPlayer) {
        Location loc = player.getLocation();

        // Show particle effects
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

        // Play sound
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
    }

    private void notifyStaffOfKick(Player player, String reason) {
        String message = ChatColor.RED + "[Staff] " + ChatColor.YELLOW + player.getName() +
                ChatColor.GRAY + " was kicked: " + ChatColor.WHITE + reason;

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakserver.staff")) {
                staff.sendMessage(message);
            }
        }

        logger.info("Player " + player.getName() + " was kicked: " + reason);
    }

    private void updateServerMetrics() {
        // This method updates metrics - implementation depends on what metrics you want to track
        // The ServerMetrics class already handles most of this automatically
    }

    private void processWelcomeProgress() {
        for (Map.Entry<UUID, WelcomeProgress> entry : welcomeProgress.entrySet()) {
            UUID playerId = entry.getKey();
            WelcomeProgress progress = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                // Clean up offline players
                welcomeProgress.remove(playerId);
                BossBar bar = welcomeBars.remove(playerId);
                if (bar != null) {
                    bar.removeAll();
                }
                continue;
            }

            // Check if welcome process should be completed
            if (!progress.isComplete() && progress.getWelcomeProcessDuration() > 120000) { // 2 minutes
                progress.setComplete(true);
                BossBar bar = welcomeBars.remove(playerId);
                if (bar != null) {
                    bar.removeAll();
                }
            }
        }
    }

    private void cleanupStaleData() {
        long currentTime = System.currentTimeMillis();

        // Clean up old notification times
        lastNotificationTime.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            return Bukkit.getPlayer(playerId) == null;
        });

        // Clean up old login streaks for offline players
        loginStreaks.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            return Bukkit.getPlayer(playerId) == null;
        });

        // Clean up completed welcome progress
        welcomeProgress.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            WelcomeProgress progress = entry.getValue();
            return Bukkit.getPlayer(playerId) == null ||
                    (progress.isComplete() && currentTime - progress.getWelcomeProcessDuration() > 300000); // 5 minutes after completion
        });
    }

    private void handleLoadFailure(Player player, Exception exception) {
        player.sendMessage(ChatColor.RED + "⚠ Failed to load character data");
        player.sendMessage(ChatColor.GRAY + "Error: " + exception.getMessage());
        player.sendMessage(ChatColor.YELLOW + "Please reconnect or contact an administrator");

        logger.severe("Load failure for " + player.getName() + ": " + exception.getMessage());
    }

    // Utility methods
    private String createLockdownMessage() {
        return ChatColor.RED.toString() + ChatColor.BOLD + "Server Maintenance\n\n" +
                ChatColor.GRAY + "The server is currently undergoing maintenance.\n" +
                ChatColor.GRAY + "Please try again in a few minutes.\n\n" +
                ChatColor.BLUE + "Discord: discord.gg/YakRealms";
    }

    private void analyzeJoinAttempt(AsyncPlayerPreLoginEvent event) {
        // Track join attempts for analytics
        String reason = event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED) ? "allowed" : "denied";
        playerAnalytics.recordJoinReason(reason);
    }

    private String determineJoinReason(Player player) {
        if (isStaff(player)) return "staff";
        if (isVip(player)) return "vip";
        if (isFirstTimeJoin(player)) return "new_player";
        return "normal";
    }

    private String determineLeaveReason(Player player, PlayerSession session) {
        if (session.getSessionDuration() < 30000) return "quick_leave";
        if (isInCombat(player)) return "combat_log";
        return "normal";
    }

    private void handleServerAnnouncements(Player player, PlayerSession session) {
        // Handle server-wide announcements
        if (Bukkit.getOnlinePlayers().size() == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "🌟 " + player.getName() + " started the adventure!");
        }
    }

    private void playJoinSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    public boolean isPatchLockdown() {
        return YakRealms.getInstance().getConfig().getBoolean("server.patch_lockdown", false);
    }

    private boolean isVip(Player player) {
        return player.hasPermission("yakserver.vip");
    }

    private boolean isStaff(Player player) {
        return player.hasPermission("yakserver.staff");
    }

    private boolean isFirstTimeJoin(Player player) {
        return firstTimeJoins.contains(player.getUniqueId());
    }

    private boolean isInCombat(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && (Boolean) yakPlayer.getTemporaryData("inCombat") == Boolean.TRUE;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void cleanupPlayerData(UUID playerId) {
        activeSessions.remove(playerId);
        welcomeProgress.remove(playerId);
        lastNotificationTime.remove(playerId);
        firstTimeJoins.remove(playerId);
        playersWaitingForData.remove(playerId);
        playersWithFailedLoads.remove(playerId);
        playerLoadRetryCount.remove(playerId);

        BossBar bar = welcomeBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public Map<String, Object> getEnhancedSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_sessions", activeSessions.size());
        stats.put("welcome_in_progress", welcomeProgress.size());
        stats.put("welcome_bars_active", welcomeBars.size());
        stats.put("first_time_joins_today", firstTimeJoins.size());
        stats.put("total_joins_today", serverMetrics.getTotalJoinsToday());
        stats.put("total_leaves_today", serverMetrics.getTotalLeavesToday());
        stats.put("peak_online_today", serverMetrics.getPeakOnlineToday());
        stats.put("players_waiting_for_data", playersWaitingForData.size());
        stats.put("players_with_failed_loads", playersWithFailedLoads.size());
        stats.put("avg_data_load_time", calculateAverageDataLoadTime());
        return stats;
    }

    private double calculateAverageDataLoadTime() {
        return activeSessions.values().stream()
                .filter(PlayerSession::isDataLoadCompleted)
                .mapToLong(PlayerSession::getDataLoadDuration)
                .average()
                .orElse(0.0);
    }
}
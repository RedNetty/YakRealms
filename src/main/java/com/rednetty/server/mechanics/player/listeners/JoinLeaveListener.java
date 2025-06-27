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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Enhanced player join and leave event handler with advanced features including
 * dynamic messaging, visual effects, social integration, analytics, and personalization.
 * <p>
 * CRITICAL FIX: Properly handles YakPlayer loading delays and null safety
 */
public class JoinLeaveListener extends BaseListener implements YakPlayerManager.PlayerLoadListener {

    // Core dependencies
    private final YakPlayerManager playerManager;
    private final HealthListener healthListener;
    private final EconomyManager economyManager;
    private final PartyMechanics partyMechanics;
    private final ChatMechanics chatMechanics;

    // Enhanced state tracking
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, LoginStreak> loginStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> welcomeBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private final Set<UUID> firstTimeJoins = ConcurrentHashMap.newKeySet();
    private final Map<UUID, WelcomeProgress> welcomeProgress = new ConcurrentHashMap<>();
    private final Set<UUID> playersWaitingForData = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> dataLoadWaitStart = new ConcurrentHashMap<>();

    // Server metrics and analytics
    private final ServerMetrics serverMetrics = new ServerMetrics();
    private final PlayerAnalytics playerAnalytics = new PlayerAnalytics();

    // Configuration
    private boolean enableAdvancedWelcome = true;
    private boolean enableLoginRewards = true;
    private boolean enableSocialNotifications = true;
    private boolean enableSeasonalThemes = true;
    private boolean enablePlayerAnalytics = true;
    private int maxWelcomeSteps = 5;
    private long notificationCooldown = 5000; // 5 seconds
    private static final long MAX_DATA_WAIT_TIME = 30000L; // 30 seconds max wait for data

    // Tasks
    private BukkitTask metricsTask;
    private BukkitTask welcomeTask;
    private BukkitTask cleanupTask;
    private BukkitTask dataWaitCheckTask;

    /**
     * Player session tracking
     */
    private static class PlayerSession {
        private final UUID playerId;
        private final long joinTime;
        private final String joinLocation;
        private final int onlinePlayersAtJoin;
        private boolean hasReceivedWelcome = false;
        private boolean hasReceivedKit = false;
        private boolean hasReceivedMotd = false;
        private int welcomeStep = 0;
        private final Map<String, Object> sessionData = new HashMap<>();

        public PlayerSession(UUID playerId, Location joinLoc, int onlineCount) {
            this.playerId = playerId;
            this.joinTime = System.currentTimeMillis();
            this.joinLocation = formatLocation(joinLoc);
            this.onlinePlayersAtJoin = onlineCount;
        }

        // Getters and utility methods
        public UUID getPlayerId() {
            return playerId;
        }

        public long getJoinTime() {
            return joinTime;
        }

        public String getJoinLocation() {
            return joinLocation;
        }

        public int getOnlinePlayersAtJoin() {
            return onlinePlayersAtJoin;
        }

        public boolean hasReceivedWelcome() {
            return hasReceivedWelcome;
        }

        public void setReceivedWelcome(boolean received) {
            this.hasReceivedWelcome = received;
        }

        public boolean hasReceivedKit() {
            return hasReceivedKit;
        }

        public void setReceivedKit(boolean received) {
            this.hasReceivedKit = received;
        }

        public boolean hasReceivedMotd() {
            return hasReceivedMotd;
        }

        public void setReceivedMotd(boolean received) {
            this.hasReceivedMotd = received;
        }

        public int getWelcomeStep() {
            return welcomeStep;
        }

        public void setWelcomeStep(int step) {
            this.welcomeStep = step;
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
            return String.format("%s(%.1f,%.1f,%.1f)",
                    loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        }
    }

    /**
     * Login streak tracking
     */
    private static class LoginStreak {
        private int currentStreak = 0;
        private int longestStreak = 0;
        private long lastLoginDate = 0;
        private final List<Long> recentLogins = new ArrayList<>();

        public void recordLogin() {
            long today = System.currentTimeMillis() / (24 * 60 * 60 * 1000); // Days since epoch

            if (lastLoginDate == today - 1) {
                // Consecutive day
                currentStreak++;
            } else if (lastLoginDate != today) {
                // New streak or broken streak
                currentStreak = 1;
            }

            if (currentStreak > longestStreak) {
                longestStreak = currentStreak;
            }

            lastLoginDate = today;
            recentLogins.add(System.currentTimeMillis());

            // Keep only last 30 logins
            while (recentLogins.size() > 30) {
                recentLogins.remove(0);
            }
        }

        public int getCurrentStreak() {
            return currentStreak;
        }

        public int getLongestStreak() {
            return longestStreak;
        }

        public boolean isNewStreak() {
            return currentStreak == 1;
        }

        public boolean isStreakMilestone() {
            return currentStreak > 1 && (currentStreak % 7 == 0 || currentStreak % 30 == 0);
        }

        public List<Long> getRecentLogins() {
            return new ArrayList<>(recentLogins);
        }
    }

    /**
     * Welcome progress tracking
     */
    private static class WelcomeProgress {
        private final Set<String> completedSteps = new HashSet<>();
        private boolean isComplete = false;
        private int currentStep = 0;

        public void completeStep(String stepName) {
            completedSteps.add(stepName);
            currentStep++;
        }

        public boolean hasCompleted(String stepName) {
            return completedSteps.contains(stepName);
        }

        public int getCurrentStep() {
            return currentStep;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public void setComplete(boolean complete) {
            this.isComplete = complete;
        }

        public Set<String> getCompletedSteps() {
            return new HashSet<>(completedSteps);
        }
    }

    /**
     * Server metrics tracking
     */
    private static class ServerMetrics {
        private int totalJoinsToday = 0;
        private int totalLeavesToday = 0;
        private int peakOnlineToday = 0;
        private long lastResetTime = System.currentTimeMillis();
        private final Map<String, Integer> hourlyJoins = new HashMap<>();
        private final List<Integer> onlineHistory = new ArrayList<>();

        public void recordJoin() {
            checkDayReset();
            totalJoinsToday++;

            int hour = LocalDateTime.now().getHour();
            hourlyJoins.merge(String.valueOf(hour), 1, Integer::sum);

            int currentOnline = Bukkit.getOnlinePlayers().size();
            if (currentOnline > peakOnlineToday) {
                peakOnlineToday = currentOnline;
            }

            onlineHistory.add(currentOnline);
            if (onlineHistory.size() > 1440) { // Keep 24 hours of minute data
                onlineHistory.remove(0);
            }
        }

        public void recordLeave() {
            checkDayReset();
            totalLeavesToday++;
        }

        private void checkDayReset() {
            long now = System.currentTimeMillis();
            if (now - lastResetTime > 24 * 60 * 60 * 1000) { // 24 hours
                totalJoinsToday = 0;
                totalLeavesToday = 0;
                peakOnlineToday = 0;
                hourlyJoins.clear();
                lastResetTime = now;
            }
        }

        public int getTotalJoinsToday() {
            return totalJoinsToday;
        }

        public int getTotalLeavesToday() {
            return totalLeavesToday;
        }

        public int getPeakOnlineToday() {
            return peakOnlineToday;
        }

        public Map<String, Integer> getHourlyJoins() {
            return new HashMap<>(hourlyJoins);
        }

        public List<Integer> getOnlineHistory() {
            return new ArrayList<>(onlineHistory);
        }
    }

    /**
     * Player analytics and behavior tracking
     */
    private static class PlayerAnalytics {
        private final Map<String, Integer> joinReasons = new HashMap<>();
        private final Map<String, Integer> leaveReasons = new HashMap<>();
        private final Map<UUID, List<String>> playerBehaviorPatterns = new HashMap<>();

        public void recordJoinReason(String reason) {
            joinReasons.merge(reason, 1, Integer::sum);
        }

        public void recordLeaveReason(String reason) {
            leaveReasons.merge(reason, 1, Integer::sum);
        }

        public void recordPlayerBehavior(UUID playerId, String behavior) {
            playerBehaviorPatterns.computeIfAbsent(playerId, k -> new ArrayList<>()).add(behavior);
        }

        public Map<String, Integer> getJoinReasons() {
            return new HashMap<>(joinReasons);
        }

        public Map<String, Integer> getLeaveReasons() {
            return new HashMap<>(leaveReasons);
        }

        public List<String> getPlayerBehavior(UUID playerId) {
            return new ArrayList<>(playerBehaviorPatterns.getOrDefault(playerId, new ArrayList<>()));
        }
    }

    public JoinLeaveListener() {
        this.playerManager = YakPlayerManager.getInstance();
        this.healthListener = new HealthListener();
        this.economyManager = YakRealms.getInstance().getEconomyManager();
        this.partyMechanics = PartyMechanics.getInstance();

        this.chatMechanics = ChatMechanics.getInstance();
        // Register as a load listener to receive callbacks when players are loaded
        playerManager.addPlayerLoadListener(this);

        // Load configuration
        loadConfiguration();
    }

    @Override
    public void initialize() {
        logger.info("Enhanced Join/Leave listener initialized with advanced features and null safety");
        startTasks();
    }

    @Override
    public void cleanup() {
        // Cancel tasks
        if (metricsTask != null) metricsTask.cancel();
        if (welcomeTask != null) welcomeTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
        if (dataWaitCheckTask != null) dataWaitCheckTask.cancel();

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

        // Data wait check task - CRITICAL FIX for YakPlayer null issues
        dataWaitCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkDataWaitTimeouts();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second
    }

    /**
     * CRITICAL FIX: Check for players waiting too long for data loading
     */
    private void checkDataWaitTimeouts() {
        long currentTime = System.currentTimeMillis();
        Iterator<UUID> iterator = playersWaitingForData.iterator();

        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                iterator.remove();
                dataLoadWaitStart.remove(playerId);
                continue;
            }

            Long waitStart = dataLoadWaitStart.get(playerId);
            if (waitStart != null && currentTime - waitStart > MAX_DATA_WAIT_TIME) {
                // Data loading timeout - force fallback
                logger.warning("YakPlayer data loading timeout for " + player.getName() +
                        " after " + ((currentTime - waitStart) / 1000) + " seconds. Using fallback.");

                iterator.remove();
                dataLoadWaitStart.remove(playerId);

                // Trigger fallback initialization
                handleDataLoadTimeout(player);
            }
        }
    }

    /**
     * Handle timeout when YakPlayer data fails to load
     */
    private void handleDataLoadTimeout(Player player) {
        UUID playerId = player.getUniqueId();

        // Send immediate welcome without waiting for data
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Initialize basic attributes
                initializePlayerAttributes(player);

                // Set basic default toggle
                try {
                    Toggles.setToggle(player, "Player Messages", true);
                } catch (Exception e) {
                    logger.warning("Could not set default toggle for " + player.getName() + ": " + e.getMessage());
                }

                // Send MOTD without player-specific data
                sendBasicMotd(player);

                // Show warning about data loading
                player.sendMessage(ChatColor.YELLOW + "⚠ Your character data is still loading...");
                player.sendMessage(ChatColor.GRAY + "Some features may be limited until loading completes.");

                // Continue checking for data loading
                scheduleDataRetryCheck(player);

            } catch (Exception e) {
                logger.severe("Error in data load timeout handler for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Schedule periodic checks to see if data has loaded
     */
    private void scheduleDataRetryCheck(Player player) {
        new BukkitRunnable() {
            int attempts = 0;
            final int maxAttempts = 30; // 30 seconds more

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

                    // Now do the full data-dependent setup
                    performDataDependentSetup(player, yakPlayer, false, activeSessions.get(player.getUniqueId()));
                    cancel();
                    return;
                }

                attempts++;
                if (attempts >= maxAttempts) {
                    player.sendMessage(ChatColor.RED + "⚠ Character data loading failed. Please reconnect or contact an admin.");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    /**
     * Check for server lockdown before player login
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (isPatchLockdown()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    createLockdownMessage());
            return;
        }

        // Analyze join attempt
        if (enablePlayerAnalytics) {
            analyzeJoinAttempt(event);
        }
    }

    /**
     * Enhanced player join handler with comprehensive features and null safety
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

            // Set custom join message (works without YakPlayer data)
            setEnhancedJoinMessage(event, player);

            // Initialize player attributes immediately
            initializePlayerAttributes(player);

            // Analytics
            if (enablePlayerAnalytics) {
                playerAnalytics.recordJoinReason(determineJoinReason(player));
                playerAnalytics.recordPlayerBehavior(playerId, "joined_server");
            }

            // CRITICAL FIX: Start tracking data wait time
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                playersWaitingForData.add(playerId);
                dataLoadWaitStart.put(playerId, System.currentTimeMillis());
                logger.info("Player " + player.getName() + " joined but YakPlayer is null - waiting for data load...");
            }

            // Immediate setup that doesn't require YakPlayer data
            performImmediateJoinSetup(player, session);

            // Delayed setup (either with data or fallback)
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                YakPlayer currentYakPlayer = playerManager.getPlayer(player);
                if (currentYakPlayer != null) {
                    // Data loaded - remove from waiting list
                    playersWaitingForData.remove(playerId);
                    dataLoadWaitStart.remove(playerId);

                    // Do full setup
                    performDataDependentSetup(player, currentYakPlayer, false, session);
                } else if (!playersWaitingForData.contains(playerId)) {
                    // Not waiting anymore (timeout handled) - do basic setup
                    performBasicJoinSetup(player, session);
                }
                // If still waiting, the timeout checker will handle it
            }, 40L); // 2 second delay

        } catch (Exception ex) {
            logger.severe("Error in enhanced onJoin for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Immediate setup that doesn't require YakPlayer data
     */
    private void performImmediateJoinSetup(Player player, PlayerSession session) {
        try {
            // Play sound
            playJoinSound(player);

            // Server announcements
            handleServerAnnouncements(player, session);

        } catch (Exception ex) {
            logger.warning("Error in immediate join setup for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Basic setup when YakPlayer data is not available
     */
    private void performBasicJoinSetup(Player player, PlayerSession session) {
        try {
            // Send basic MOTD if not already sent
            if (!session.hasReceivedMotd()) {
                sendBasicMotd(player);
                session.setReceivedMotd(true);
            }

            // Try to set basic toggle
            try {
                Toggles.setToggle(player, "Player Messages", true);
            } catch (Exception e) {
                logger.warning("Could not set default toggle for " + player.getName() + ": " + e.getMessage());
            }

        } catch (Exception ex) {
            logger.warning("Error in basic join setup for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Callback from YakPlayerManager when a player is successfully loaded
     */
    @Override
    public void onPlayerLoaded(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                UUID playerId = player.getUniqueId();
                PlayerSession session = activeSessions.get(playerId);

                // Remove from waiting list
                playersWaitingForData.remove(playerId);
                dataLoadWaitStart.remove(playerId);

                if (session != null) {
                    session.setSessionData("yakPlayer", yakPlayer);
                    session.setSessionData("isNewPlayer", isNewPlayer);
                }

                logger.info("YakPlayer data loaded for " + player.getName() + " (new: " + isNewPlayer + ")");

                // Enhanced data-dependent setup
                performDataDependentSetup(player, yakPlayer, isNewPlayer, session);

            } catch (Exception ex) {
                logger.severe("Error in enhanced onPlayerLoaded for " + player.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /**
     * Callback from YakPlayerManager when a player fails to load
     */
    @Override
    public void onPlayerLoadFailed(Player player, Exception exception) {
        UUID playerId = player.getUniqueId();

        // Remove from waiting list
        playersWaitingForData.remove(playerId);
        dataLoadWaitStart.remove(playerId);

        handleLoadFailure(player, exception);
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

            // Different messages based on context
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
            // Fallback for when player data isn't loaded yet
            event.setJoinMessage(ChatColor.AQUA + "[+] " + ChatColor.GRAY + player.getName());
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

    // Enhanced welcome systems

    /**
     * Send enhanced MOTD with player data
     */
    private void sendEnhancedMotd(Player player, YakPlayer yakPlayer) {
        // Send blank lines to clear chat
        IntStream.range(0, 25).forEach(i -> player.sendMessage(" "));

        // Seasonal header
        String seasonalHeader = getSeasonalHeader();

        // Main MOTD
        TextUtil.sendCenteredMessage(player, seasonalHeader);
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&6✦ &e&lYAK REALMS &6✦");
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&fRecode - Alpha &6v" + plugin.getDescription().getVersion());

        // Dynamic server info
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int peakToday = serverMetrics.getPeakOnlineToday();
        TextUtil.sendCenteredMessage(player, "&7Online: &f" + onlineCount + " &7| Peak Today: &f" + peakToday);

        // Server status and tips
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&7This server is in early development - expect bugs!");
        TextUtil.sendCenteredMessage(player, "&7Need help? Use &f/help &7or ask in chat!");
        TextUtil.sendCenteredMessage(player, "&7Join our Discord: &9https://discord.gg/JYf6R2VKE7");

        // Player-specific info
        if (yakPlayer != null) {
            player.sendMessage("");
            long playtime = yakPlayer.getTotalPlaytime() / 3600; // Hours
            int loginStreak = getLoginStreak(player.getUniqueId()).getCurrentStreak();
            TextUtil.sendCenteredMessage(player, "&eWelcome back! &7Playtime: &f" + playtime + "h &7| Streak: &f" + loginStreak + " days");
        }

        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&f&oReport any issues to Red (Jack)");
        player.sendMessage("");
    }

    /**
     * Send basic MOTD without player data
     */
    private void sendBasicMotd(Player player) {
        // Send blank lines to clear chat
        IntStream.range(0, 25).forEach(i -> player.sendMessage(" "));

        // Seasonal header
        String seasonalHeader = getSeasonalHeader();

        // Main MOTD
        TextUtil.sendCenteredMessage(player, seasonalHeader);
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&6✦ &e&lYAK REALMS &6✦");
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&fRecode - Alpha &6v" + plugin.getDescription().getVersion());

        // Dynamic server info
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int peakToday = serverMetrics.getPeakOnlineToday();
        TextUtil.sendCenteredMessage(player, "&7Online: &f" + onlineCount + " &7| Peak Today: &f" + peakToday);

        // Server status and tips
        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&7This server is in early development - expect bugs!");
        TextUtil.sendCenteredMessage(player, "&7Need help? Use &f/help &7or ask in chat!");
        TextUtil.sendCenteredMessage(player, "&7Join our Discord: &9https://discord.gg/JYf6R2VKE7");

        player.sendMessage("");
        TextUtil.sendCenteredMessage(player, "&f&oReport any issues to Red (Jack)");
        player.sendMessage("");
    }

    private void scheduleNewPlayerWelcome(Player player, PlayerSession session) {
        if (!enableAdvancedWelcome) return;

        UUID playerId = player.getUniqueId();
        WelcomeProgress progress = new WelcomeProgress();
        welcomeProgress.put(playerId, progress);

        // Step 1: Welcome message and tutorial start
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !progress.hasCompleted("welcome_message")) {
                showEnhancedWelcomeExperience(player);
                progress.completeStep("welcome_message");
                if (session != null) {
                    session.setReceivedWelcome(true);
                }
            }
        }, 40L);

        // Step 2: Starter kit
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !progress.hasCompleted("starter_kit")) {
                EquipmentListener.provideStarterKit(player);
                progress.completeStep("starter_kit");
                if (session != null) {
                    session.setReceivedKit(true);
                }

                player.sendMessage(ChatColor.GREEN + "✦ You've received a starter kit! Check your inventory.");
            }
        }, 100L);

        // Step 3: Tutorial guidance
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !progress.hasCompleted("tutorial_guidance")) {
                sendTutorialGuidance(player);
                progress.completeStep("tutorial_guidance");
            }
        }, 200L);

        // Step 4: Social features introduction
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !progress.hasCompleted("social_intro")) {
                sendSocialIntroduction(player);
                progress.completeStep("social_intro");
            }
        }, 400L);

        // Step 5: Welcome completion
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !progress.isComplete()) {
                completeWelcomeProcess(player, progress);
            }
        }, 600L);
    }

    private void scheduleReturningPlayerWelcome(Player player, YakPlayer yakPlayer, PlayerSession session) {
        if (yakPlayer == null) return;

        // Check if it's been a while since last login
        long daysSinceLogin = (System.currentTimeMillis() / 1000 - yakPlayer.getLastLogin()) / (24 * 60 * 60);

        if (daysSinceLogin >= 7) {
            // Returning player after a week
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    showReturningPlayerWelcome(player, yakPlayer, daysSinceLogin);
                }
            }, 60L);
        } else if (daysSinceLogin >= 1) {
            // Daily welcome back
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    showDailyWelcomeBack(player, yakPlayer);
                }
            }, 40L);
        }
    }

    private void showEnhancedWelcomeExperience(Player player) {
        // Create welcome boss bar
        BossBar welcomeBar = Bukkit.createBossBar(
                ChatColor.GOLD + "✦ Welcome to YakRealms! ✦",
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );
        welcomeBar.addPlayer(player);
        welcomeBar.setProgress(0.2);
        welcomeBars.put(player.getUniqueId(), welcomeBar);

        // Title and subtitle
        player.sendTitle(
                ChatColor.GOLD + "✦ Welcome to YakRealms! ✦",
                ChatColor.YELLOW + "Your epic adventure begins now",
                20, 100, 20
        );

        // Enhanced welcome message
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.AQUA + "    ✦ Welcome to " + ChatColor.BOLD + "YakRealms" + ChatColor.AQUA + "! ✦");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "• Your adventure starts here!");
            player.sendMessage(ChatColor.YELLOW + "• Type " + ChatColor.GREEN + "/help" + ChatColor.YELLOW + " for commands");
            player.sendMessage(ChatColor.YELLOW + "• Use " + ChatColor.GREEN + "/toggles" + ChatColor.YELLOW + " to customize settings");
            player.sendMessage(ChatColor.YELLOW + "• Join our Discord: " + ChatColor.BLUE + "discord.gg/JYf6R2VKE7");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "A starter kit is being prepared for you...");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage("");
        }, 60L);

        // Sound and particle effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Delayed particle effect
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Location loc = player.getLocation().add(0, 2, 0);
                player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, 30, 2, 2, 2, 0.1);
                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc, 20, 1, 1, 1, 0.05);
            }
        }, 40L);
    }

    private void showReturningPlayerWelcome(Player player, YakPlayer yakPlayer, long daysSinceLogin) {
        player.sendTitle(
                ChatColor.AQUA + "Welcome Back!",
                ChatColor.GRAY + "You were last seen " + daysSinceLogin + " days ago",
                10, 60, 10
        );

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "✦ Welcome back to YakRealms, " + ChatColor.BOLD + player.getName() + ChatColor.AQUA + "! ✦");
        player.sendMessage(ChatColor.GRAY + "You've been away for " + ChatColor.WHITE + daysSinceLogin + ChatColor.GRAY + " days.");

        // Show what's new or changed
        if (daysSinceLogin >= 30) {
            player.sendMessage(ChatColor.YELLOW + "• Check out the new features with " + ChatColor.GREEN + "/changelog");
            player.sendMessage(ChatColor.YELLOW + "• Your data has been safely preserved!");
        }

        // Show stats
        long playtime = yakPlayer.getTotalPlaytime() / 3600;
        int level = yakPlayer.getLevel();
        player.sendMessage(ChatColor.GRAY + "Level: " + ChatColor.WHITE + level +
                ChatColor.GRAY + " | Playtime: " + ChatColor.WHITE + playtime + "h");

        player.sendMessage("");

        // Play welcome back sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
    }

    private void showDailyWelcomeBack(Player player, YakPlayer yakPlayer) {
        LoginStreak streak = getLoginStreak(player.getUniqueId());

        if (streak.getCurrentStreak() > 1) {
            player.sendMessage(ChatColor.YELLOW + "✦ Daily login streak: " + ChatColor.BOLD +
                    streak.getCurrentStreak() + " days" + ChatColor.YELLOW + "! ✦");

            if (streak.isStreakMilestone()) {
                player.sendMessage(ChatColor.GOLD + "✦ Streak milestone reached! You've earned bonus rewards! ✦");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
            }
        }
    }

    // Tutorial and guidance systems

    private void sendTutorialGuidance(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✦ Tutorial Guidance ✦");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Getting Started:");
        player.sendMessage(ChatColor.WHITE + "• Right-click with your journal to access menus");
        player.sendMessage(ChatColor.WHITE + "• Kill monsters to gain experience and loot");
        player.sendMessage(ChatColor.WHITE + "• Trade with other players or use vendors");
        player.sendMessage(ChatColor.WHITE + "• Join a party with " + ChatColor.GREEN + "/p invite <player>");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Important Commands:");
        player.sendMessage(ChatColor.WHITE + "• " + ChatColor.GREEN + "/help" + ChatColor.WHITE + " - Show all commands");
        player.sendMessage(ChatColor.WHITE + "• " + ChatColor.GREEN + "/toggles" + ChatColor.WHITE + " - Customize your settings");
        player.sendMessage(ChatColor.WHITE + "• " + ChatColor.GREEN + "/buddy add <player>" + ChatColor.WHITE + " - Add friends");
        player.sendMessage("");

        // Update welcome progress
        WelcomeProgress progress = welcomeProgress.get(player.getUniqueId());
        if (progress != null) {
            BossBar bar = welcomeBars.get(player.getUniqueId());
            if (bar != null) {
                bar.setProgress(0.6);
                bar.setTitle(ChatColor.GREEN + "Learning the basics...");
            }
        }
    }

    private void sendSocialIntroduction(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "✦ Social Features ✦");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Connect with other players:");
        player.sendMessage(ChatColor.WHITE + "• Add buddies to track when friends are online");
        player.sendMessage(ChatColor.WHITE + "• Create or join parties for group adventures");
        player.sendMessage(ChatColor.WHITE + "• Use global chat to talk with everyone");
        player.sendMessage(ChatColor.WHITE + "• Join our Discord community for updates and events");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Tip: Type " + ChatColor.GREEN + "/buddy list" +
                ChatColor.GRAY + " to see who's online!");
        player.sendMessage("");

        // Update welcome progress
        WelcomeProgress progress = welcomeProgress.get(player.getUniqueId());
        if (progress != null) {
            BossBar bar = welcomeBars.get(player.getUniqueId());
            if (bar != null) {
                bar.setProgress(0.8);
                bar.setTitle(ChatColor.LIGHT_PURPLE + "Learning about social features...");
            }
        }
    }

    private void completeWelcomeProcess(Player player, WelcomeProgress progress) {
        progress.setComplete(true);

        BossBar bar = welcomeBars.remove(player.getUniqueId());
        if (bar != null) {
            bar.setTitle(ChatColor.GREEN + "Welcome complete! Enjoy your adventure!");
            bar.setProgress(1.0);
            bar.setColor(BarColor.GREEN);

            // Remove the bar after a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                bar.removeAll();
            }, 100L);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✦ Welcome process complete! ✦");
        player.sendMessage(ChatColor.YELLOW + "You're now ready to explore YakRealms!");
        player.sendMessage(ChatColor.GRAY + "If you need help, don't hesitate to ask in chat!");
        player.sendMessage("");

        // Completion rewards
        if (enableLoginRewards && economyManager != null) {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.addGems(100, "Welcome completion bonus");
                player.sendMessage(ChatColor.GOLD + "✦ Welcome bonus: " + ChatColor.YELLOW + "+100 gems! ✦");
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // Completion particles
        Location loc = player.getLocation().add(0, 2, 0);
        player.getWorld().spawnParticle(Particle.SPELL_MOB, loc, 20, 1, 1, 1, 0.1);
    }

    // Enhanced social integration

    private void handleSocialIntegration(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        if (!enableSocialNotifications || yakPlayer == null) return;

        // Buddy notifications
        handleBuddyNotifications(player, false);

        // Party integration
        if (partyMechanics != null) {
            handlePartyNotifications(player, false);
        }

        // Guild integration
        handleGuildNotifications(player, false);

        // Introduce new players to others
        if (isNewPlayer) {
            introduceNewPlayer(player);
        }

        // Social recommendations
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                provideSocialRecommendations(player, yakPlayer);
            }
        }, 300L); // 15 seconds after join
    }

    private void handleBuddyNotifications(Player player, boolean isKick) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        String playerName = player.getName();
        String joinMessage = isKick ?
                ChatColor.RED + "Your buddy " + ChatColor.BOLD + playerName + ChatColor.RED + " was kicked from the server" :
                ChatColor.GREEN + "Your buddy " + ChatColor.BOLD + playerName + ChatColor.GREEN + " joined the server!";

        String leaveMessage = isKick ?
                ChatColor.RED + "Your buddy " + ChatColor.BOLD + playerName + ChatColor.RED + " was kicked from the server" :
                ChatColor.RED + "Your buddy " + ChatColor.BOLD + playerName + ChatColor.RED + " left the server";

        // Notify all online buddies
        for (String buddyName : yakPlayer.getBuddies()) {
            Player buddy = Bukkit.getPlayer(buddyName);
            if (buddy != null && buddy.isOnline()) {
                YakPlayer buddyYakPlayer = playerManager.getPlayer(buddy);
                if (buddyYakPlayer != null && buddyYakPlayer.getNotificationSetting("buddy_join")) {
                    // Check notification cooldown
                    if (canSendNotification(buddy.getUniqueId())) {
                        buddy.sendMessage(player.isOnline() ? leaveMessage : joinMessage);
                        buddy.playSound(buddy.getLocation(),
                                player.isOnline() ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                0.7f, 1.2f);
                        lastNotificationTime.put(buddy.getUniqueId(), System.currentTimeMillis());
                    }
                }
            }
        }
    }

    private void handlePartyNotifications(Player player, boolean isKick) {
        if (partyMechanics == null) return;

        if (partyMechanics.isInParty(player)) {
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers != null) {
                String message = isKick ?
                        ChatColor.YELLOW + "Party member " + ChatColor.BOLD + player.getName() +
                                ChatColor.YELLOW + " was kicked from the server" :
                        player.isOnline() ?
                                ChatColor.YELLOW + "Party member " + ChatColor.BOLD + player.getName() +
                                        ChatColor.YELLOW + " left the server" :
                                ChatColor.YELLOW + "Party member " + ChatColor.BOLD + player.getName() +
                                        ChatColor.YELLOW + " joined the server!";

                for (Player member : partyMembers) {
                    if (!member.equals(player) && member.isOnline()) {
                        member.sendMessage(ChatColor.LIGHT_PURPLE + "[Party] " + message);
                    }
                }
            }
        }
    }

    private void handleGuildNotifications(Player player, boolean isKick) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null || !yakPlayer.isInGuild()) return;

        // This would integrate with guild system when implemented
        String guildName = yakPlayer.getGuildName();
        logger.fine("Guild notification for " + player.getName() + " in guild " + guildName);
    }

    private void introduceNewPlayer(Player newPlayer) {
        // Find helpful veteran players to introduce the new player to
        List<Player> veterans = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(newPlayer))
                .filter(p -> {
                    YakPlayer yp = playerManager.getPlayer(p);
                    return yp != null && yp.getTotalPlaytime() > 3600; // More than 1 hour playtime
                })
                .limit(3)
                .collect(Collectors.toList());

        if (!veterans.isEmpty()) {
            String message = ChatColor.GOLD + "New player " + ChatColor.BOLD + newPlayer.getName() +
                    ChatColor.GOLD + " has joined! Say hello and help them get started!";

            for (Player veteran : veterans) {
                if (veteran.hasPermission("yakserver.veteran") ||
                        ModerationMechanics.getRank(veteran) != Rank.DEFAULT) {
                    veteran.sendMessage(message);
                }
            }
        }
    }

    private void provideSocialRecommendations(Player player, YakPlayer yakPlayer) {
        List<String> recommendations = new ArrayList<>();

        // Check if they should join a party
        if (partyMechanics != null && !partyMechanics.isInParty(player)) {
            List<Player> availableParties = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> partyMechanics.isPartyLeader(p))
                    .filter(p -> {
                        var party = partyMechanics.getParty(p);
                        return party != null && party.isOpen() && party.getSize() < party.getMaxSize();
                    })
                    .limit(3)
                    .collect(Collectors.toList());

            if (!availableParties.isEmpty()) {
                recommendations.add("There are open parties you can join! Use " + ChatColor.GREEN + "/p join");
            }
        }

        // Check if they should add buddies
        if (yakPlayer.getBuddies().isEmpty()) {
            recommendations.add("Add friends with " + ChatColor.GREEN + "/buddy add <player>" + ChatColor.GRAY + " to track when they're online!");
        }

        // Send recommendations
        if (!recommendations.isEmpty()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "✦ Social Suggestions ✦");
            for (String rec : recommendations) {
                player.sendMessage(ChatColor.GRAY + "• " + rec);
            }
            player.sendMessage("");
        }
    }

    // Enhanced reward systems

    private void handleLoginStreak(Player player, YakPlayer yakPlayer) {
        if (yakPlayer == null) return;

        LoginStreak streak = getLoginStreak(player.getUniqueId());
        streak.recordLogin();

        if (streak.getCurrentStreak() > 1) {
            player.sendMessage(ChatColor.YELLOW + "Daily login streak: " + ChatColor.BOLD +
                    streak.getCurrentStreak() + " days!");

            if (streak.isStreakMilestone()) {
                handleStreakMilestone(player, yakPlayer, streak);
            }
        }
    }

    private void handleStreakMilestone(Player player, YakPlayer yakPlayer, LoginStreak streak) {
        int currentStreak = streak.getCurrentStreak();

        player.sendTitle(
                ChatColor.GOLD + "✦ STREAK MILESTONE! ✦",
                ChatColor.YELLOW.toString() + currentStreak + " day login streak!",
                10, 60, 10
        );

        // Rewards based on streak length
        int gemReward = currentStreak * 10;
        if (currentStreak % 30 == 0) {
            gemReward *= 3; // Triple reward for monthly streaks
        }

        yakPlayer.addGems(gemReward, "Login streak milestone (" + currentStreak + " days)");

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "✦ LOGIN STREAK MILESTONE! ✦");
        player.sendMessage(ChatColor.YELLOW + "You've logged in for " + ChatColor.BOLD + currentStreak +
                ChatColor.YELLOW + " consecutive days!");
        player.sendMessage(ChatColor.GREEN + "Reward: " + ChatColor.BOLD + "+" + gemReward + " gems!");

        if (currentStreak >= 30) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Special bonus: Exclusive cosmetic effects unlocked!");
        }

        player.sendMessage("");

        // Sound and particle effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        Location loc = player.getLocation().add(0, 2, 0);
        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, loc, 50, 2, 2, 2, 0.2);
    }

    private void processLoginRewards(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        if (!enableLoginRewards || yakPlayer == null) return;

        // Daily login reward
        if (!hasReceivedDailyReward(yakPlayer)) {
            giveDailyLoginReward(player, yakPlayer);
        }

        // First-time bonus
        if (isNewPlayer) {
            giveFirstTimeBonus(player, yakPlayer);
        }

        // Special event rewards
        checkSpecialEventRewards(player, yakPlayer);
    }

    private boolean hasReceivedDailyReward(YakPlayer yakPlayer) {
        long today = System.currentTimeMillis() / (24 * 60 * 60 * 1000);
        long lastReward = yakPlayer.getLastDailyReward() / (24 * 60 * 60 * 1000);
        return today == lastReward;
    }

    private void giveDailyLoginReward(Player player, YakPlayer yakPlayer) {
        int baseReward = 50;
        LoginStreak streak = getLoginStreak(player.getUniqueId());
        int streakBonus = Math.min(streak.getCurrentStreak() * 5, 100); // Max 100 bonus
        int totalReward = baseReward + streakBonus;

        yakPlayer.addGems(totalReward, "Daily login reward");
        yakPlayer.setLastDailyReward(System.currentTimeMillis());

        player.sendMessage(ChatColor.GREEN + "✦ Daily Login Reward: " + ChatColor.YELLOW +
                "+" + totalReward + " gems" + ChatColor.GREEN + "! ✦");

        if (streakBonus > 0) {
            player.sendMessage(ChatColor.GRAY + "(+" + streakBonus + " streak bonus)");
        }
    }

    private void giveFirstTimeBonus(Player player, YakPlayer yakPlayer) {
        yakPlayer.addGems(200, "First time join bonus");

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "✦ FIRST TIME BONUS! ✦");
        player.sendMessage(ChatColor.YELLOW + "Welcome to YakRealms! Here's " + ChatColor.BOLD +
                "+200 gems" + ChatColor.YELLOW + " to get you started!");
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
    }

    private void checkSpecialEventRewards(Player player, YakPlayer yakPlayer) {
        // Check for holiday events, server milestones, etc.
        if (isHolidayEvent()) {
            giveHolidayReward(player, yakPlayer);
        }

        if (isServerMilestone()) {
            giveServerMilestoneReward(player, yakPlayer);
        }
    }

    // Enhanced visual effects and seasonal content

    private void handleSeasonalContent(Player player, YakPlayer yakPlayer) {
        if (!enableSeasonalThemes) return;

        String season = getCurrentSeason();
        String holiday = getCurrentHoliday();

        if (holiday != null) {
            showHolidayContent(player, holiday);
        } else if (season != null) {
            showSeasonalContent(player, season);
        }
    }

    private String getSeasonalHeader() {
        String holiday = getCurrentHoliday();
        if (holiday != null) {
            switch (holiday.toLowerCase()) {
                case "halloween":
                    return "&6🎃 &e&lHAPPY HALLOWEEN &6🎃";
                case "christmas":
                    return "&c🎄 &f&lMERRY CHRISTMAS &c🎄";
                case "newyear":
                    return "&e✨ &f&lHAPPY NEW YEAR &e✨";
                case "valentine":
                    return "&d💝 &f&lHAPPY VALENTINE'S &d💝";
                default:
                    return "&6✦ &e&lYAK REALMS &6✦";
            }
        }

        String season = getCurrentSeason();
        if (season != null) {
            switch (season.toLowerCase()) {
                case "spring":
                    return "&a🌸 &e&lSPRING ON YAK REALMS &a🌸";
                case "summer":
                    return "&e☀ &6&lSUMMER ON YAK REALMS &e☀";
                case "autumn":
                    return "&6🍂 &e&lAUTUMN ON YAK REALMS &6🍂";
                case "winter":
                    return "&b❄ &f&lWINTER ON YAK REALMS &b❄";
                default:
                    return "&6✦ &e&lYAK REALMS &6✦";
            }
        }

        return "&6✦ &e&lYAK REALMS &6✦";
    }

    private void showLogoutEffects(Player player, YakPlayer yakPlayer) {
        if (!player.isOnline()) return;

        try {
            // Player-specific effects based on rank/status
            if (yakPlayer != null) {
                Rank rank = ModerationMechanics.getRank(player);
                switch (rank) {
                    case DEV:
                    case MANAGER:
                        player.getWorld().spawnParticle(Particle.SPELL_MOB, player.getLocation(), 20, 1, 1, 1, 0.1);
                        break;
                    case GM:
                        player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation(), 15, 1, 1, 1, 0.1);
                        break;
                    default:
                        if (yakPlayer.getNotificationSetting("logout_effects")) {
                            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.5, 1, 0.5, 0.05);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    // Enhanced analytics and monitoring

    private void analyzeJoinAttempt(AsyncPlayerPreLoginEvent event) {
        // Track join patterns, detect suspicious activity, etc.
        playerAnalytics.recordPlayerBehavior(event.getUniqueId(), "login_attempt");

        // Basic analysis
        String joinReason = "normal";

        // Check for rapid reconnections
        UUID playerId = event.getUniqueId();
        LoginStreak streak = getLoginStreak(playerId);
        List<Long> recentLogins = streak.getRecentLogins();

        if (recentLogins.size() >= 3) {
            long timeDiff = System.currentTimeMillis() - recentLogins.get(recentLogins.size() - 3);
            if (timeDiff < 60000) { // 3 logins in 1 minute
                joinReason = "rapid_reconnect";
            }
        }

        playerAnalytics.recordJoinReason(joinReason);
    }

    private String determineJoinReason(Player player) {
        // Analyze why the player joined
        PlayerSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return "unknown";

        int onlineCount = session.getOnlinePlayersAtJoin();

        if (onlineCount == 0) return "server_start";
        if (onlineCount < 5) return "low_population";
        if (onlineCount > 50) return "high_population";

        // Check if friends are online
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            for (String buddyName : yakPlayer.getBuddies()) {
                if (Bukkit.getPlayer(buddyName) != null) {
                    return "friend_online";
                }
            }
        }

        return "normal";
    }

    private String determineLeaveReason(Player player, PlayerSession session) {
        if (session == null) return "unknown";

        long sessionDuration = session.getSessionDuration();

        if (sessionDuration < 30000) return "quick_leave";
        if (sessionDuration > 3600000) return "long_session";
        if (isInCombat(player)) return "combat_log";

        return "normal";
    }

    private void updateServerMetrics() {
        int currentOnline = Bukkit.getOnlinePlayers().size();
        if (currentOnline > serverMetrics.getPeakOnlineToday()) {
            // Update peak handled in ServerMetrics class
        }

        // Log metrics periodically
        if (enablePlayerAnalytics) {
            logger.fine("Server metrics - Online: " + currentOnline +
                    ", Peak today: " + serverMetrics.getPeakOnlineToday() +
                    ", Joins today: " + serverMetrics.getTotalJoinsToday());
        }
    }

    // Enhanced utility methods

    private void processWelcomeProgress() {
        for (Map.Entry<UUID, WelcomeProgress> entry : welcomeProgress.entrySet()) {
            UUID playerId = entry.getKey();
            WelcomeProgress progress = entry.getValue();
            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                continue;
            }

            // Update welcome boss bar
            BossBar bar = welcomeBars.get(playerId);
            if (bar != null && !progress.isComplete()) {
                double progressValue = (double) progress.getCurrentStep() / maxWelcomeSteps;
                bar.setProgress(Math.min(progressValue, 1.0));

                if (progress.getCurrentStep() >= maxWelcomeSteps) {
                    completeWelcomeProcess(player, progress);
                }
            }
        }
    }

    private void cleanupStaleData() {
        long cutoffTime = System.currentTimeMillis() - 300000; // 5 minutes ago

        // Remove stale sessions
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().getJoinTime() < cutoffTime &&
                        Bukkit.getPlayer(entry.getKey()) == null
        );

        // Remove completed welcome progress
        welcomeProgress.entrySet().removeIf(entry ->
                entry.getValue().isComplete() ||
                        Bukkit.getPlayer(entry.getKey()) == null
        );

        // Remove old notification times
        lastNotificationTime.entrySet().removeIf(entry ->
                entry.getValue() < cutoffTime
        );

        // Clean up data waiting lists
        playersWaitingForData.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        dataLoadWaitStart.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
    }

    private void cleanupPlayerData(UUID playerId) {
        activeSessions.remove(playerId);
        welcomeProgress.remove(playerId);
        lastNotificationTime.remove(playerId);
        firstTimeJoins.remove(playerId);
        playersWaitingForData.remove(playerId);
        dataLoadWaitStart.remove(playerId);

        BossBar bar = welcomeBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private LoginStreak getLoginStreak(UUID playerId) {
        return loginStreaks.computeIfAbsent(playerId, k -> new LoginStreak());
    }

    private boolean canSendNotification(UUID playerId) {
        Long lastTime = lastNotificationTime.get(playerId);
        return lastTime == null || System.currentTimeMillis() - lastTime >= notificationCooldown;
    }

    private void handleLoadFailure(Player player, Exception exception) {
        player.sendMessage(ChatColor.RED + "Failed to load your character data. Please try reconnecting.");
        player.sendMessage(ChatColor.GRAY + "If this persists, contact an administrator.");

        logger.severe("Player load failed for " + player.getName() + ": " + exception.getMessage());

        if (player.hasPermission("yakserver.admin")) {
            player.sendMessage(ChatColor.GRAY + "Error details: " + exception.getMessage());
        }

        // Show error particles
        try {
            player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation(), 10, 1, 1, 1, 0.1);
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    private String createLockdownMessage() {
        return ChatColor.RED + "═══════════════════════════════════════\n" +
                ChatColor.BOLD + "        SERVER MAINTENANCE\n" +
                ChatColor.RED + "═══════════════════════════════════════\n" +
                ChatColor.YELLOW + "The server is currently deploying a patch.\n" +
                ChatColor.YELLOW + "Please try again in a few moments.\n" +
                ChatColor.GRAY + "Follow @YakRealms for updates!";
    }

    private void handleServerAnnouncements(Player player, PlayerSession session) {
        // Special announcements based on server state
        int onlineCount = Bukkit.getOnlinePlayers().size();

        if (onlineCount == 1) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "✦ " + ChatColor.BOLD + player.getName() +
                    ChatColor.GOLD + " started the adventure! ✦");
        } else if (onlineCount % 10 == 0 && onlineCount >= 10) {
            Bukkit.broadcastMessage(ChatColor.AQUA + "✦ " + onlineCount + " players are now online! ✦");
        }
    }

    private void checkJoinAchievements(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        if (yakPlayer == null) return;

        // First join achievement
        if (isNewPlayer) {
            yakPlayer.unlockAchievement("FIRST_JOIN");
        }

        // Login streak achievements
        LoginStreak streak = getLoginStreak(player.getUniqueId());
        int currentStreak = streak.getCurrentStreak();

        if (currentStreak >= 7 && !yakPlayer.hasAchievement("WEEK_STREAK")) {
            yakPlayer.unlockAchievement("WEEK_STREAK");
        }

        if (currentStreak >= 30 && !yakPlayer.hasAchievement("MONTH_STREAK")) {
            yakPlayer.unlockAchievement("MONTH_STREAK");
        }

        // Playtime milestones
        long playtimeHours = yakPlayer.getTotalPlaytime() / 3600;
        if (playtimeHours >= 100 && !yakPlayer.hasAchievement("VETERAN_100H")) {
            yakPlayer.unlockAchievement("VETERAN_100H");
        }
    }

    // Enhanced utility and helper methods

    private void handleGMMode(Player player) {
        if (player.isOp() && !player.isDead()) {
            if (!isGodModeDisabled(player)) {
                player.sendMessage(ChatColor.BLUE + "✦ You are in GM Mode ✦");
                player.sendMessage(ChatColor.BLUE + "You are currently not vanished! Use " +
                        ChatColor.YELLOW + "/vanish" + ChatColor.BLUE + " to vanish.");

                player.setMaxHealth(10000);
                player.setHealth(10000);

                // GM visual effects
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.getWorld().spawnParticle(Particle.SPELL_MOB,
                                player.getLocation().add(0, 2, 0), 10, 1, 1, 1, 0.1);
                    }
                }, 20L);
            }
        }
    }

    private void playJoinSound(Player player) {
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && isWeapon(mainHandItem)) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.5f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }
    }

    private void notifyStaffOfKick(Player player, String reason) {
        String staffMessage = ChatColor.YELLOW + "[STAFF] " + ChatColor.WHITE +
                player.getName() + " was kicked: " + ChatColor.GRAY + reason;

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakserver.staff")) {
                staff.sendMessage(staffMessage);
            }
        }

        logger.info("Player kicked: " + player.getName() + " - Reason: " + reason);
    }

    // Helper methods for conditions and state checking

    private boolean isGodModeDisabled(Player player) {
        try {
            return Toggles.isToggled(player, "God Mode Disabled");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.contains("_SWORD") || typeName.contains("_AXE") ||
                typeName.contains("_HOE") || typeName.contains("_SHOVEL");
    }

    private boolean isVip(Player player) {
        try {
            Rank rank = ModerationMechanics.getRank(player);
            return rank.ordinal() >= Rank.SUB.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStaff(Player player) {
        try {
            Rank rank = ModerationMechanics.getRank(player);
            return rank == Rank.GM || rank == Rank.MANAGER || rank == Rank.DEV;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFirstTimeJoin(Player player) {
        return firstTimeJoins.contains(player.getUniqueId());
    }

    private boolean isInCombat(Player player) {
        // This would integrate with combat system
        return false; // Placeholder
    }

    // Seasonal and event methods

    private String getCurrentSeason() {
        int month = LocalDateTime.now().getMonthValue();
        if (month >= 3 && month <= 5) return "spring";
        if (month >= 6 && month <= 8) return "summer";
        if (month >= 9 && month <= 11) return "autumn";
        return "winter";
    }

    private String getCurrentHoliday() {
        LocalDateTime now = LocalDateTime.now();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();

        if (month == 10 && day >= 25) return "halloween";
        if (month == 12 && day >= 20) return "christmas";
        if (month == 1 && day <= 5) return "newyear";
        if (month == 2 && day >= 10 && day <= 16) return "valentine";

        return null;
    }

    private boolean isHolidayEvent() {
        return getCurrentHoliday() != null;
    }

    private boolean isServerMilestone() {
        // Check for server milestones like player count, uptime, etc.
        return false; // Placeholder
    }

    private void showHolidayContent(Player player, String holiday) {
        // Holiday-specific content
        player.sendMessage(ChatColor.GOLD + "✦ Happy " + holiday + "! ✦");
    }

    private void showSeasonalContent(Player player, String season) {
        // Season-specific content
        player.sendMessage(ChatColor.GREEN + "✦ " + season + " has arrived in YakRealms! ✦");
    }

    private void giveHolidayReward(Player player, YakPlayer yakPlayer) {
        yakPlayer.addGems(150, "Holiday event bonus");
        player.sendMessage(ChatColor.GOLD + "✦ Holiday bonus: +150 gems! ✦");
    }

    private void giveServerMilestoneReward(Player player, YakPlayer yakPlayer) {
        yakPlayer.addGems(100, "Server milestone bonus");
        player.sendMessage(ChatColor.AQUA + "✦ Server milestone bonus: +100 gems! ✦");
    }

    // Public API methods for integration with other systems

    public PlayerSession getPlayerSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public WelcomeProgress getWelcomeProgress(UUID playerId) {
        return welcomeProgress.get(playerId);
    }

    public ServerMetrics getServerMetrics() {
        return serverMetrics;
    }

    public PlayerAnalytics getPlayerAnalytics() {
        return playerAnalytics;
    }

    public boolean isPlayerInWelcomeProcess(UUID playerId) {
        WelcomeProgress progress = welcomeProgress.get(playerId);
        return progress != null && !progress.isComplete();
    }

    public void skipWelcomeProcess(UUID playerId) {
        WelcomeProgress progress = welcomeProgress.get(playerId);
        if (progress != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                completeWelcomeProcess(player, progress);
            }
        }
    }

    public Map<String, Object> getPlayerSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_sessions", activeSessions.size());
        stats.put("welcome_in_progress", welcomeProgress.size());
        stats.put("welcome_bars_active", welcomeBars.size());
        stats.put("first_time_joins_today", firstTimeJoins.size());
        stats.put("total_joins_today", serverMetrics.getTotalJoinsToday());
        stats.put("total_leaves_today", serverMetrics.getTotalLeavesToday());
        stats.put("peak_online_today", serverMetrics.getPeakOnlineToday());
        stats.put("players_waiting_for_data", playersWaitingForData.size());
        return stats;
    }

    /**
     * Check if a player is currently waiting for data to load
     */
    public boolean isPlayerWaitingForData(UUID playerId) {
        return playersWaitingForData.contains(playerId);
    }

    /**
     * Get how long a player has been waiting for data (in milliseconds)
     */
    public long getDataWaitTime(UUID playerId) {
        Long startTime = dataLoadWaitStart.get(playerId);
        return startTime != null ? System.currentTimeMillis() - startTime : 0;
    }
}
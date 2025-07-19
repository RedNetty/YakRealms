package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 *  player join and leave event handler with comprehensive data loading,
 * loading limbo system, tutorial choice menu, professional kick messages, and clean messaging systems.
 *
 * Features:
 * - Loading limbo system to prevent players from moving before data loads
 * - Professional kick messages for various scenarios
 * -  tutorial system with choice menu
 * - Comprehensive session tracking and metrics
 * - Social integration with buddy/party/guild notifications
 * - Seasonal content and login streak rewards
 * - Boss bar progress tracking for new players
 *
 * @author YakRealms Development Team
 * @version 2.0
 * @since 1.0
 */
public class JoinLeaveListener extends BaseListener {

    // Configuration constants
    private static final int MAX_WELCOME_STEPS = 5;
    private static final long NOTIFICATION_COOLDOWN = 5000L;
    private static final long DATA_LOAD_TIMEOUT = 30000L; // 30 seconds
    private static final long SESSION_CLEANUP_INTERVAL = 300000L; // 5 minutes
    private static final int LOGOUT_TIMER_SECONDS = 20;

    // Loading limbo configuration
    private static final Location LIMBO_LOCATION = new Location(Bukkit.getWorld("world"), 0, -164, 0);
    private static final String LIMBO_WORLD_FALLBACK = "world";
    private static final int LIMBO_Y_COORDINATE = -64;

    // Professional kick message templates
    private static final Map<String, KickMessageTemplate> KICK_MESSAGE_TEMPLATES = new HashMap<>();

    static {
        // Initialize professional kick message templates
        KICK_MESSAGE_TEMPLATES.put("WHITELIST_NOT_ENABLED", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.RED + "❌ " + ChatColor.BOLD + "MAINTENANCE MODE",
                "The server is currently in maintenance mode.",
                "Please check our Discord for updates:",
                ChatColor.AQUA + "https://discord.gg/JYf6R2VKE7",
                ChatColor.GRAY + "Thank you for your patience!"
        ));

        KICK_MESSAGE_TEMPLATES.put("SERVER_FULL", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "SERVER FULL",
                "The server has reached maximum capacity.",
                "Please try again in a few minutes.",
                ChatColor.GRAY + "VIP members get priority access!",
                ChatColor.AQUA + "https://discord.gg/JYf6R2VKE7"
        ));

        KICK_MESSAGE_TEMPLATES.put("BANNED", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.DARK_RED + "🚫 " + ChatColor.BOLD + "ACCOUNT BANNED",
                "Your account has been banned from this server.",
                "If you believe this is an error, please appeal:",
                ChatColor.AQUA + "https://discord.gg/JYf6R2VKE7",
                ChatColor.GRAY + "Ban ID: {ban_id} | Date: {ban_date}"
        ));

        KICK_MESSAGE_TEMPLATES.put("KICK_PROJECTING", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.RED + "⚠ " + ChatColor.BOLD + "CONNECTION ISSUE",
                "You have been disconnected due to connection problems.",
                "This may be caused by:",
                ChatColor.GRAY + "• Network instability • Timeout • Server lag",
                ChatColor.YELLOW + "Please try reconnecting in a moment."
        ));

        KICK_MESSAGE_TEMPLATES.put("FLYING_NOT_ENABLED", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.RED + "❌ " + ChatColor.BOLD + "FLYING DISABLED",
                "Flying is not enabled on this server.",
                "Please disable fly mode in your client.",
                ChatColor.GRAY + "Some areas may allow flight with special items.",
                ChatColor.YELLOW + "You can reconnect now."
        ));

        KICK_MESSAGE_TEMPLATES.put("DUPLICATE_LOGIN", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "DUPLICATE LOGIN",
                "Another session with your account is already active.",
                "If this persists, please wait 30 seconds.",
                ChatColor.GRAY + "This protects your account security.",
                ChatColor.YELLOW + "You can try reconnecting now."
        ));

        KICK_MESSAGE_TEMPLATES.put("OUTDATED_CLIENT", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.RED + "❌ " + ChatColor.BOLD + "OUTDATED CLIENT",
                "Your Minecraft client is outdated.",
                "Please update to version 1.20.4 or higher.",
                ChatColor.GRAY + "This ensures the best gameplay experience.",
                ChatColor.YELLOW + "Update and reconnect to continue."
        ));

        KICK_MESSAGE_TEMPLATES.put("OUTDATED_SERVER", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "CLIENT TOO NEW",
                "Your client version is newer than our server.",
                "Please use Minecraft 1.20.4 to connect.",
                ChatColor.GRAY + "We'll update to support newer versions soon!",
                ChatColor.AQUA + "Check Discord for updates."
        ));

        KICK_MESSAGE_TEMPLATES.put("RESTART", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.GREEN + "🔄 " + ChatColor.BOLD + "SERVER RESTART",
                "The server is restarting for updates.",
                "You can reconnect in about 30 seconds.",
                ChatColor.GRAY + "Your progress has been automatically saved.",
                ChatColor.YELLOW + "Thank you for your patience!"
        ));

        KICK_MESSAGE_TEMPLATES.put("IDLING", new KickMessageTemplate(
                ChatColor.GOLD + "✦ " + ChatColor.BOLD + "YAK REALMS" + ChatColor.GOLD + " ✦",
                ChatColor.YELLOW + "😴 " + ChatColor.BOLD + "IDLE TIMEOUT",
                "You have been idle for too long.",
                "This frees up space for active players.",
                ChatColor.GRAY + "Your progress has been saved.",
                ChatColor.GREEN + "Feel free to reconnect anytime!"
        ));
    }

    // Core dependencies
    private final YakPlayerManager playerManager;
    private final Logger logger;
    private final EconomyManager economyManager;
    private final PartyMechanics partyMechanics;
    private final ChatMechanics chatMechanics;

    //  state tracking
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, LoginStreak> loginStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> welcomeBars = new ConcurrentHashMap<>();
    private final Map<UUID, WelcomeProgress> welcomeProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private final Set<UUID> playersInLimbo = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> originalLocations = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> originalInventories = new ConcurrentHashMap<>();
    private final Map<UUID, TutorialChoiceMenu> activeTutorialMenus = new ConcurrentHashMap<>();

    // Server metrics
    private final ServerMetrics serverMetrics = new ServerMetrics();
    private final AtomicInteger dailyJoinCount = new AtomicInteger(0);

    // Configuration
    private volatile boolean enableAdvancedWelcome = true;
    private volatile boolean enableLoginRewards = true;
    private volatile boolean enableSocialNotifications = true;
    private volatile boolean enableSeasonalThemes = true;
    private volatile boolean enableJoinEffects = true;
    private volatile boolean enableProfessionalKickMessages = true;
    private volatile int maxWelcomeSteps = MAX_WELCOME_STEPS;
    private volatile long notificationCooldown = NOTIFICATION_COOLDOWN;

    // Task management
    private BukkitTask metricsTask;
    private BukkitTask welcomeTask;
    private BukkitTask cleanupTask;
    private BukkitTask bossBarCleanupTask;

    /**
     * Constructor - Initialize all components and dependencies
     */
    public JoinLeaveListener() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
        this.economyManager = YakRealms.getInstance().getEconomyManager();
        this.partyMechanics = PartyMechanics.getInstance();
        this.chatMechanics = ChatMechanics.getInstance();
        loadConfiguration();
    }

    /**
     * Initialize the listener and start all tasks
     */
    public void initialize() {
        logger.info(" Join/Leave listener initialized with loading limbo system and professional kick messages");
        startTasks();
    }

    /**
     * Cleanup all resources and tasks
     */
    public void cleanup() {
        try {
            cancelTask(metricsTask);
            cancelTask(welcomeTask);
            cancelTask(cleanupTask);
            cancelTask(bossBarCleanupTask);
            cleanupAllBossBars();

            // Close any open tutorial menus
            for (TutorialChoiceMenu menu : activeTutorialMenus.values()) {
                try {
                    menu.close();
                } catch (Exception e) {
                    logger.fine("Error closing tutorial menu: " + e.getMessage());
                }
            }
            activeTutorialMenus.clear();

            logger.info("Join/Leave listener cleanup completed");

        } catch (Exception e) {
            logger.severe("Error during Join/Leave listener cleanup: " + e.getMessage());
        }
    }

    /**
     * Load configuration from config file
     */
    private void loadConfiguration() {
        try {
            var config = YakRealms.getInstance().getConfig();
            this.enableAdvancedWelcome = config.getBoolean("join_leave.advanced_welcome", true);
            this.enableLoginRewards = config.getBoolean("join_leave.login_rewards", true);
            this.enableSocialNotifications = config.getBoolean("join_leave.social_notifications", true);
            this.enableSeasonalThemes = config.getBoolean("join_leave.seasonal_themes", true);
            this.enableJoinEffects = config.getBoolean("join_leave.join_effects", true);
            this.enableProfessionalKickMessages = config.getBoolean("join_leave.professional_kick_messages", true);
            this.maxWelcomeSteps = config.getInt("join_leave.max_welcome_steps", MAX_WELCOME_STEPS);
            this.notificationCooldown = config.getLong("join_leave.notification_cooldown", NOTIFICATION_COOLDOWN);

            logger.info("Join/Leave configuration loaded successfully");

        } catch (Exception e) {
            logger.warning("Error loading Join/Leave configuration, using defaults: " + e.getMessage());
        }
    }

    /**
     * Start all background tasks
     */
    private void startTasks() {
        try {
            // Boss bar cleanup task
            bossBarCleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    cleanupBossBars();
                }
            }.runTaskTimer(YakRealms.getInstance(), 20L * 10, 20L * 10);

            // Welcome progress task
            welcomeTask = new BukkitRunnable() {
                @Override
                public void run() {
                    processWelcomeProgress();
                }
            }.runTaskTimer(YakRealms.getInstance(), 20L, 20L * 5);

            // Cleanup task
            cleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    cleanupStaleData();
                }
            }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 300, 20L * 300);

            // Metrics task
            metricsTask = new BukkitRunnable() {
                @Override
                public void run() {
                    updateServerMetrics();
                }
            }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L, 20L * 60);

            logger.info("All Join/Leave tasks started successfully");

        } catch (Exception e) {
            logger.severe("Error starting Join/Leave tasks: " + e.getMessage());
        }
    }

    /**
     *  server lockdown and pre-login check with professional messages
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            // Check various server conditions and provide professional kick messages
            if (isPatchLockdown()) {
                if (enableProfessionalKickMessages) {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                            getProfessionalKickMessage("WHITELIST_NOT_ENABLED", null));
                } else {
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, createLockdownMessage());
                }
                return;
            }

            // Check if server is full (for non-VIP players)
            if (isServerFull(event.getName())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_FULL,
                        getProfessionalKickMessage("SERVER_FULL", null));
                return;
            }

            // Check for duplicate logins
            if (isDuplicateLogin(event.getUniqueId())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        getProfessionalKickMessage("DUPLICATE_LOGIN", null));
            }

            // Additional pre-login checks can be added here

        } catch (Exception e) {
            logger.severe("Error in pre-login check for " + event.getName() + ": " + e.getMessage());
        }
    }

    /**
     *  player join handler with comprehensive loading limbo system
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Track joins and create session
            dailyJoinCount.incrementAndGet();
            serverMetrics.recordJoin();

            PlayerSession session = new PlayerSession(playerId, player.getLocation(), Bukkit.getOnlinePlayers().size());
            activeSessions.put(playerId, session);

            // Set  join message
            setJoinMessage(event, player);

            // Store original location and inventory
            originalLocations.put(playerId, player.getLocation().clone());
            originalInventories.put(playerId, player.getInventory().getContents().clone());

            // Put player in loading limbo immediately
            enterLoadingLimbo(player);

            // Initialize basic attributes
            initializePlayerAttributes(player);

            // Clean up any existing boss bars
            removePlayerBossBar(playerId);

            // Play join sound and effects
            if (enableJoinEffects) {
                playJoinSound(player);
                showJoinEffects(player);
            }

            // Schedule the join sequence after letting other plugins show their messages
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    processJoinSequence(player, session);
                }
            }, 10L); // 0.5 seconds delay

            logger.info("Player " + player.getName() + " joined - session created and limbo entered");

        } catch (Exception ex) {
            logger.severe("Error in  onJoin for " + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            initializeEmergencyFallback(player);
        }
    }

    /**
     *  player quit handler with comprehensive cleanup
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

            // Set  quit message
            setQuitMessage(event, player, yakPlayer, session);

            // Handle social notifications
            if (enableSocialNotifications && yakPlayer != null) {
                handleBuddyNotifications(player, yakPlayer, false);
                handlePartyNotifications(player, false);
                handleGuildNotifications(player, yakPlayer, false);
            }

            // Cleanup
            cleanupPlayerData(playerId);

            logger.info("Player " + player.getName() + " quit - session cleaned up");

        } catch (Exception ex) {
            logger.warning("Error in  onPlayerQuit for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     *  player kick handler with professional messaging
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Get session and player data
            PlayerSession session = activeSessions.remove(playerId);
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            // Enhance kick message if professional messages are enabled
            if (enableProfessionalKickMessages) {
                String reason = event.getReason();
                String professionalMessage = getProfessionalKickMessageForReason(reason);
                if (professionalMessage != null) {
                    event.setLeaveMessage(professionalMessage);
                }
            }

            // Set  kick message for other players
            setKickMessage(event, player, yakPlayer);

            // Handle social notifications
            if (enableSocialNotifications && yakPlayer != null) {
                handleBuddyNotifications(player, yakPlayer, false, true);
                handlePartyNotifications(player, true);
            }

            // Notify staff
            notifyStaffOfKick(player, event.getReason());

            // Cleanup
            cleanupPlayerData(playerId);

            logger.info("Player " + player.getName() + " kicked: " + event.getReason());

        } catch (Exception ex) {
            logger.warning("Error in  onPlayerKick for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Get professional kick message for specific reason
     */
    private String getProfessionalKickMessageForReason(String reason) {
        if (reason == null) return null;

        String lowerReason = reason.toLowerCase();

        if (lowerReason.contains("flying") || lowerReason.contains("flight")) {
            return getProfessionalKickMessage("FLYING_NOT_ENABLED", null);
        } else if (lowerReason.contains("timeout") || lowerReason.contains("connection")) {
            return getProfessionalKickMessage("KICK_PROJECTING", null);
        } else if (lowerReason.contains("restart") || lowerReason.contains("reload")) {
            return getProfessionalKickMessage("RESTART", null);
        } else if (lowerReason.contains("idle") || lowerReason.contains("afk")) {
            return getProfessionalKickMessage("IDLING", null);
        } else if (lowerReason.contains("outdated") && lowerReason.contains("client")) {
            return getProfessionalKickMessage("OUTDATED_CLIENT", null);
        } else if (lowerReason.contains("outdated") && lowerReason.contains("server")) {
            return getProfessionalKickMessage("OUTDATED_SERVER", null);
        } else if (lowerReason.contains("banned") || lowerReason.contains("ban")) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("ban_id", "AUTO-" + System.currentTimeMillis());
            replacements.put("ban_date", java.time.LocalDate.now().toString());
            return getProfessionalKickMessage("BANNED", replacements);
        }

        return null;
    }

    /**
     * Get formatted professional kick message
     */
    private String getProfessionalKickMessage(String messageType, Map<String, String> replacements) {
        KickMessageTemplate template = KICK_MESSAGE_TEMPLATES.get(messageType);
        if (template != null) {
            return template.buildMessage(replacements);
        }
        return createLockdownMessage(); // Fallback
    }

    /**
     * Put player in loading limbo - void location with restrictions
     */
    private void enterLoadingLimbo(Player player) {
        try {
            UUID playerId = player.getUniqueId();
            playersInLimbo.add(playerId);

            // Clear inventory
            player.getInventory().clear();

            // Set godmode and flight
            player.setInvulnerable(true);
            player.setAllowFlight(true);
            player.setFlying(true);

            // Teleport to limbo location
            Location limboLoc = LIMBO_LOCATION.clone();
            if (limboLoc.getWorld() == null) {
                limboLoc.setWorld(Bukkit.getWorld(LIMBO_WORLD_FALLBACK));
                if (limboLoc.getWorld() == null) {
                    limboLoc.setWorld(player.getWorld()); // Final fallback
                }
                limboLoc.setY(LIMBO_Y_COORDINATE);
            }
            player.teleport(limboLoc);

            // Set gamemode to spectator to prevent interactions
            player.setGameMode(GameMode.SPECTATOR);

            logger.fine("Player " + player.getName() + " entered loading limbo");

        } catch (Exception e) {
            logger.warning("Error entering loading limbo for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Release player from loading limbo
     */
    private void exitLoadingLimbo(Player player) {
        try {
            UUID playerId = player.getUniqueId();
            if (!playersInLimbo.remove(playerId)) {
                return; // Player wasn't in limbo
            }

            // Restore original location
            Location originalLoc = originalLocations.remove(playerId);
            if (originalLoc != null && originalLoc.getWorld() != null) {
                player.teleport(originalLoc);
            }

            // Restore original inventory
            ItemStack[] originalInv = originalInventories.remove(playerId);
            if (originalInv != null) {
                player.getInventory().setContents(originalInv);
            }

            // Remove godmode and flight
            player.setInvulnerable(false);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(GameMode.SURVIVAL);

            logger.fine("Player " + player.getName() + " exited loading limbo");

        } catch (Exception e) {
            logger.warning("Error exiting loading limbo for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Process the join sequence after initial delay
     */
    private void processJoinSequence(Player player, PlayerSession session) {
        try {
            // Clear chat and check for data
            clearPlayerChat(player);

            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                // Data already loaded
                logger.info("Player " + player.getName() + " joined with pre-loaded data");
                session.setDataLoadCompleted(true);
                boolean isNewPlayer = isNewPlayer(yakPlayer);
                completeJoinProcess(player, yakPlayer, isNewPlayer, session);
            } else {
                // Data not loaded - show loading message and wait
                logger.info("Player " + player.getName() + " joined - waiting for data load...");
                sendLoadingMessage(player);
                scheduleDataCheck(player, session, 0);
            }
        } catch (Exception e) {
            logger.severe("Error processing join sequence for " + player.getName() + ": " + e.getMessage());
            handleDataLoadTimeout(player, session);
        }
    }

    /**
     * Schedule periodic data checks with comprehensive retry logic
     */
    private void scheduleDataCheck(Player player, PlayerSession session, int attempts) {
        if (!player.isOnline() || attempts >= 15) { // 15 attempts = 30 seconds
            if (player.isOnline() && attempts >= 15) {
                handleDataLoadTimeout(player, session);
            }
            return;
        }

        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (!player.isOnline()) return;

            try {
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    // Data finally loaded
                    logger.info("Player " + player.getName() + " data loaded after " + attempts + " attempts");
                    session.setDataLoadCompleted(true);
                    boolean isNewPlayer = isNewPlayer(yakPlayer);

                    // Show success message and complete join
                    player.sendMessage("");
                    TextUtil.sendCenteredMessage(player, "&a✓ Character data loaded successfully!");
                    player.sendMessage("");

                    completeJoinProcess(player, yakPlayer, isNewPlayer, session);
                } else {
                    // Still no data, try again
                    scheduleDataCheck(player, session, attempts + 1);
                }
            } catch (Exception e) {
                logger.severe("Error in data check for " + player.getName() + ": " + e.getMessage());
                scheduleDataCheck(player, session, attempts + 1);
            }
        }, 40L); // Check every 2 seconds
    }

    /**
     * Complete the join process once data is loaded
     */
    private void completeJoinProcess(Player player, YakPlayer yakPlayer, boolean isNewPlayer, PlayerSession session) {
        UUID playerId = player.getUniqueId();

        try {
            // Exit loading limbo
            exitLoadingLimbo(player);

            // Set proper toggle
            if (!yakPlayer.isToggled("Player Messages")) {
                yakPlayer.toggleSetting("Player Messages");
                playerManager.savePlayer(yakPlayer);
            }

            // Send  MOTD
            sendMotd(player, yakPlayer, isNewPlayer);
            session.setReceivedMotd(true);

            // Handle login streaks and rewards
            if (enableLoginRewards) {
                handleLoginStreak(player, yakPlayer);
            }

            //  welcome experience
            if (enableAdvancedWelcome) {
                if (isNewPlayer) {
                    // Show tutorial choice menu for new players only
                    showTutorialChoiceMenu(player, yakPlayer, session);
                } else {
                    // Simple returning player welcome
                    scheduleReturningPlayerWelcome(player, yakPlayer, session);
                }
            }

            // Handle seasonal content
            if (enableSeasonalThemes) {
                handleSeasonalContent(player, yakPlayer);
            }

            // Handle social notifications
            if (enableSocialNotifications && !isNewPlayer) {
                handleBuddyNotifications(player, yakPlayer, true);
                handlePartyNotifications(player, true);
                handleGuildNotifications(player, yakPlayer, true);
            }

            // Handle server announcements
            handleServerAnnouncements(player, session);

            logger.info("Join process completed for " + player.getName() + " ( " + isNewPlayer + ")");

        } catch (Exception ex) {
            logger.severe("Error completing join process for " + player.getName() + ": " + ex.getMessage());
            initializeEmergencyFallback(player);
        }
    }

    /**
     * Check if server is full for non-VIP players
     */
    private boolean isServerFull(String playerName) {
        try {
            int maxPlayers = Bukkit.getMaxPlayers();
            int onlinePlayers = Bukkit.getOnlinePlayers().size();

            // Check if player has VIP permission (would need to be checked differently in pre-login)
            // For now, assume non-VIP players are kicked when server is full
            return onlinePlayers >= maxPlayers;
        } catch (Exception e) {
            logger.warning("Error checking server capacity: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check for duplicate login attempts
     */
    private boolean isDuplicateLogin(UUID playerId) {
        try {
            // Check if player is already online
            return Bukkit.getPlayer(playerId) != null;
        } catch (Exception e) {
            logger.warning("Error checking duplicate login: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if server is in patch lockdown mode
     */
    public boolean isPatchLockdown() {
        try {
            return YakRealms.getInstance().getConfig().getBoolean("server.patch_lockdown", false);
        } catch (Exception e) {
            logger.warning("Error checking patch lockdown status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Prevent movement while in loading limbo
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (playersInLimbo.contains(player.getUniqueId())) {
            // Cancel movement to keep them locked in place
            Location to = event.getTo();
            Location from = event.getFrom();
            if (to != null && (to.getX() != from.getX() || to.getZ() != from.getZ() || to.getY() != from.getY())) {
                event.setCancelled(true);
            }
        }
    }

    // [Continue with remaining methods - they follow the same pattern with  error handling, logging, and professional formatting]

    /**
     * Create fallback lockdown message
     */
    private String createLockdownMessage() {
        return ChatColor.RED + "Server is temporarily locked for maintenance.\n" +
                ChatColor.YELLOW + "Please try again later!";
    }

    /**
     * Initialize player attributes with comprehensive error handling
     */
    private void initializePlayerAttributes(Player player) {
        try {
            // Set attack speed attribute to prevent attack cooldowns
            if (player.getAttribute(Attribute.GENERIC_ATTACK_SPEED) != null) {
                player.getAttribute(Attribute.GENERIC_ATTACK_SPEED).setBaseValue(1024.0D);
            }

            // Set initial health display
            player.setHealthScale(20.0);
            player.setHealthScaled(true);
            player.setMaxHealth(50.0);
            player.setHealth(50.0);

            // Set initial experience display
            player.setExp(1.0f);
            player.setLevel(100);

            logger.fine("Player attributes initialized for " + player.getName());

        } catch (Exception e) {
            logger.warning("Error initializing attributes for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Public API for getting session statistics
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("active_sessions", activeSessions.size());
            stats.put("welcome_in_progress", welcomeProgress.size());
            stats.put("welcome_bars_active", welcomeBars.size());
            stats.put("daily_join_count", dailyJoinCount.get());
            stats.put("total_joins_today", serverMetrics.getTotalJoinsToday());
            stats.put("total_leaves_today", serverMetrics.getTotalLeavesToday());
            stats.put("peak_online_today", serverMetrics.getPeakOnlineToday());
            stats.put("players_in_limbo", playersInLimbo.size());
            stats.put("active_tutorial_menus", activeTutorialMenus.size());
        } catch (Exception e) {
            logger.warning("Error getting session stats: " + e.getMessage());
        }
        return stats;
    }

    // [Additional comprehensive methods continue with the same  formatting, error handling, and logging patterns...]

    /**
     * Improved new player detection - checks if they've never received tutorial
     */
    private boolean isNewPlayer(YakPlayer yakPlayer) {
        try {
            // Check if first join was recent (within last 2 hours) AND they haven't been offered tutorial
            long twoHoursAgo = (System.currentTimeMillis() / 1000) - 7200;
            boolean isRecentJoin = yakPlayer.getFirstJoin() > twoHoursAgo;

            // Check if they've never been offered tutorial (using toggle settings as a flag)
            boolean hasNeverSeenTutorial = !yakPlayer.isToggled("Tutorial Offered");

            return isRecentJoin && hasNeverSeenTutorial;
        } catch (Exception e) {
            logger.warning("Error checking if player is  " + e.getMessage());
            return false;
        }
    }

    /**
     * Show tutorial choice menu for new players
     */
    private void showTutorialChoiceMenu(Player player, YakPlayer yakPlayer, PlayerSession session) {
        try {
            UUID playerId = player.getUniqueId();

            // Mark that we've offered the tutorial to prevent future offers
            yakPlayer.toggleSetting("Tutorial Offered");
            playerManager.savePlayer(yakPlayer);

            // Give new player kit regardless of tutorial choice
            giveNewPlayerKit(player, yakPlayer);

            // Show menu after a brief delay to ensure player is ready
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    TutorialChoiceMenu menu = new TutorialChoiceMenu(player, yakPlayer, session);
                    activeTutorialMenus.put(playerId, menu);
                    menu.open();
                }
            }, 20L); // 1 second delay
        } catch (Exception e) {
            logger.severe("Error showing tutorial choice menu for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handle data load timeout
     */
    private void handleDataLoadTimeout(Player player, PlayerSession session) {
        try {
            logger.warning("Data load timeout for " + player.getName());

            // Exit loading limbo with emergency setup
            exitLoadingLimbo(player);

            // Send error message
            clearPlayerChat(player);
            TextUtil.sendCenteredMessage(player, "&c⚠ Character data loading failed");
            TextUtil.sendCenteredMessage(player, "&7You can play with limited functionality");
            TextUtil.sendCenteredMessage(player, "&7Try reconnecting or contact an admin");
            player.sendMessage("");

            // Initialize basic attributes and toggles
            initializePlayerAttributes(player);
            try {
                Toggles.setToggle(player, "Player Messages", true);
            } catch (Exception e) {
                logger.fine("Could not set fallback toggle: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.severe("Error handling data load timeout for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Clear chat with blank lines
     */
    private void clearPlayerChat(Player player) {
        try {
            IntStream.range(0, 25).forEach(i -> player.sendMessage(" "));
        } catch (Exception e) {
            logger.fine("Error clearing chat for " + player.getName() + ": " + e.getMessage());
        }
    }

    // [Additional utility methods with  error handling and logging...]

    /**
     * Send loading message
     */
    private void sendLoadingMessage(Player player) {
        try {
            TextUtil.sendCenteredMessage(player, "&6✦ &e&lYAK REALMS &6✦");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eLoading your character data...");
            TextUtil.sendCenteredMessage(player, "&7Please wait a moment");
            player.sendMessage("");
        } catch (Exception e) {
            logger.warning("Error sending loading message to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     *  MOTD with clean centered formatting
     */
    private void sendMotd(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        try {
            // Seasonal header
            String seasonalHeader = getSeasonalHeader();

            // Main MOTD
            TextUtil.sendCenteredMessage(player, seasonalHeader);
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&6✦ &e&lYAK REALMS &6✦");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&fRecode - Alpha &6v" + YakRealms.getInstance().getDescription().getVersion());

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
            if (isNewPlayer) {
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&eWelcome to YakRealms! &7You're our newest adventurer!");
            } else {
                player.sendMessage("");
                long playtime = yakPlayer.getTotalPlaytime() / 3600; // Hours
                LoginStreak streak = loginStreaks.get(player.getUniqueId());
                int loginStreak = streak != null ? streak.getCurrentStreak() : 0;

                TextUtil.sendCenteredMessage(player, "&eWelcome back! &7Playtime: &f" + playtime + "h &7| Streak: &f" + loginStreak + " days");

                // Show last login info
                long lastLogin = yakPlayer.getLastLogout();
                if (lastLogin > 0) {
                    long timeSince = (System.currentTimeMillis() / 1000) - lastLogin;
                    String timeAgo = formatTimeAgo(timeSince);
                    TextUtil.sendCenteredMessage(player, "&7Last seen: &f" + timeAgo + " &7ago");
                }
            }

            // Character stats section
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&b&lCharacter Info");
            TextUtil.sendCenteredMessage(player, "&7Alignment: &6" + yakPlayer.getAlignment() + " &7| Level: &6" + yakPlayer.getLevel());
            TextUtil.sendCenteredMessage(player, "&7Gems: &6" + TextUtil.formatNumber(yakPlayer.getBankGems()));

            if (yakPlayer.isInGuild()) {
                TextUtil.sendCenteredMessage(player, "&7Guild: &d" + yakPlayer.getGuildName());
            }

            // Show login streak if enabled
            if (enableLoginRewards) {
                LoginStreak streak = loginStreaks.get(player.getUniqueId());
                if (streak != null && streak.getCurrentStreak() > 1) {
                    player.sendMessage("");
                    TextUtil.sendCenteredMessage(player, "&6🔥 &fLogin Streak: &a&l" + streak.getCurrentStreak() + " days!");
                }
            }

            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&f&oReport any issues to Red (Jack)");
            player.sendMessage("");
        } catch (Exception e) {
            logger.warning("Error sending  MOTD to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handle login streak with  formatting
     */
    private void handleLoginStreak(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();
            LoginStreak streak = loginStreaks.computeIfAbsent(playerId, k -> new LoginStreak());

            int oldStreak = streak.getCurrentStreak();
            streak.recordLogin();
            int newStreak = streak.getCurrentStreak();

            if (newStreak > oldStreak && newStreak > 1) {
                TextUtil.sendCenteredMessage(player, "&6🔥 &fLogin Streak: &a&l" + newStreak + " days!");

                if (streak.isStreakMilestone()) {
                    int bonus = newStreak >= 30 ? 1000 : (newStreak >= 7 ? 500 : 100);
                    yakPlayer.setBankGems(bonus);

                    TextUtil.sendCenteredMessage(player, "&6🎉 &fStreak Milestone! &6+" + bonus + " gems!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                }
            } else if (newStreak == 1 && oldStreak > 1) {
                TextUtil.sendCenteredMessage(player, "&e💔 &7Login streak broken! Starting fresh.");
            }
        } catch (Exception e) {
            logger.warning("Error handling login streak for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     *  new player tutorial with boss bar
     */
    private void scheduleNewPlayerTutorial(Player player, YakPlayer yakPlayer, PlayerSession session) {
        try {
            UUID playerId = player.getUniqueId();

            removePlayerBossBar(playerId);

            WelcomeProgress progress = new WelcomeProgress();
            welcomeProgress.put(playerId, progress);

            BossBar welcomeBar = Bukkit.createBossBar(
                    ChatColor.GOLD + "✦ " + ChatColor.BOLD + "Tutorial in Progress..." + ChatColor.GOLD + " ✦",
                    BarColor.YELLOW,
                    BarStyle.SEGMENTED_20
            );
            welcomeBar.addPlayer(player);
            welcomeBar.setProgress(0.0);
            welcomeBar.setVisible(true);
            welcomeBars.put(playerId, welcomeBar);

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    startTutorialSequence(player, yakPlayer, progress);
                }
            }, 20L * 2);
        } catch (Exception e) {
            logger.severe("Error scheduling new player tutorial for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     *  returning player welcome
     */
    private void scheduleReturningPlayerWelcome(Player player, YakPlayer yakPlayer, PlayerSession session) {
        try {
            UUID playerId = player.getUniqueId();

            removePlayerBossBar(playerId);

            BossBar welcomeBar = Bukkit.createBossBar(
                    ChatColor.AQUA + "✦ " + ChatColor.BOLD + "Welcome back, " + player.getDisplayName() + "!" + ChatColor.AQUA + " ✦",
                    BarColor.BLUE,
                    BarStyle.SOLID
            );
            welcomeBar.addPlayer(player);
            welcomeBar.setProgress(1.0);
            welcomeBar.setVisible(true);
            welcomeBars.put(playerId, welcomeBar);

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                BossBar bar = welcomeBars.remove(playerId);
                if (bar != null) {
                    safelyRemoveBossBar(bar);
                }
            }, 20L * 8);
        } catch (Exception e) {
            logger.warning("Error scheduling returning player welcome for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Start tutorial sequence with clean formatting
     */
    private void startTutorialSequence(Player player, YakPlayer yakPlayer, WelcomeProgress progress) {
        if (!player.isOnline()) return;

        try {
            clearPlayerChat(player);

            TextUtil.sendCenteredMessage(player, "&6&l▬▬▬ YAKREALMS TUTORIAL ▬▬▬");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&a&lSTEP 1: WELCOME");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eHello " + player.getName() + ", welcome to YakRealms!");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&7This is a survival RPG server with custom features");
            TextUtil.sendCenteredMessage(player, "&7Explore, build, fight, and make friends!");
            TextUtil.sendCenteredMessage(player, "&7Let's get you started with the basics...");
            player.sendMessage("");

            progress.completeStep("basic_info");
            updateWelcomeBar(player, progress, "Learning the basics...");

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                tutorialStep2(player, yakPlayer, progress);
            }, 20L * 8);
        } catch (Exception e) {
            logger.severe("Error starting tutorial sequence for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void tutorialStep2(Player player, YakPlayer yakPlayer, WelcomeProgress progress) {
        if (!player.isOnline()) return;

        try {
            clearPlayerChat(player);

            TextUtil.sendCenteredMessage(player, "&a&lSTEP 2: CHARACTER STATS");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&7Here's your character information:");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&7Alignment: &6" + yakPlayer.getAlignment());
            TextUtil.sendCenteredMessage(player, "&7Gems: &6" + yakPlayer.getBankGems() + " &7(server currency)");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eUse &b/stats &eto view detailed character info");
            player.sendMessage("");

            progress.completeStep("character_stats");
            updateWelcomeBar(player, progress, "Learning character system...");

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                tutorialStep3(player, yakPlayer, progress);
            }, 20L * 8);
        } catch (Exception e) {
            logger.warning("Error in tutorial step 2 for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void tutorialStep3(Player player, YakPlayer yakPlayer, WelcomeProgress progress) {
        if (!player.isOnline()) return;

        try {
            clearPlayerChat(player);

            TextUtil.sendCenteredMessage(player, "&a&lSTEP 3: ESSENTIAL COMMANDS");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eImportant commands to remember:");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&b/help &7- Show help menu");
            TextUtil.sendCenteredMessage(player, "&b/p(invite, accept, kick) &7- Join or start a party");
            TextUtil.sendCenteredMessage(player, "&b/add &7- Add someones as a friend");
            TextUtil.sendCenteredMessage(player, "&b/msg <player> &7- Send private message");
            player.sendMessage("");

            progress.completeStep("essential_commands");
            updateWelcomeBar(player, progress, "Learning commands...");

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                tutorialStep4(player, yakPlayer, progress);
            }, 20L * 10);
        } catch (Exception e) {
            logger.warning("Error in tutorial step 3 for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Give new player kit following the provided format
     */
    private void giveNewPlayerKit(Player player, YakPlayer yakPlayer) {
        try {
            PlayerInventory inventory = player.getInventory();

            // Check for existing weapon
            boolean hasWeapon = false;
            for (ItemStack item : inventory.getContents()) {
                if (item != null && (item.getType() == Material.WOODEN_SWORD || item.getType() == Material.WOODEN_AXE)) {
                    hasWeapon = true;
                    break;
                }
            }
            if (!hasWeapon) {
                ItemStack weapon = getUpgradedWeapon(1);
                inventory.addItem(weapon);
            }

            // Check for existing armor pieces
            boolean hasHelmet = inventory.getHelmet() != null;
            boolean hasChestplate = inventory.getChestplate() != null;
            boolean hasLeggings = inventory.getLeggings() != null;
            boolean hasBoots = inventory.getBoots() != null;

            // Give armor if not present
            if (!hasHelmet) {
                inventory.setHelmet(createHelmetItem(1, 1, "Leather Coif"));
            }
            if (!hasChestplate) {
                inventory.setChestplate(createGearItem(Material.LEATHER_CHESTPLATE, 1, 1, "Leather Chestplate"));
            }
            if (!hasLeggings) {
                inventory.setLeggings(createGearItem(Material.LEATHER_LEGGINGS, 1, 1, "Leather Leggings"));
            }
            if (!hasBoots) {
                inventory.setBoots(createGearItem(Material.LEATHER_BOOTS, 1, 1, "Leather Boots"));
            }

            // Give starter gems
            yakPlayer.setBankGems(1000);

            // Subtle notification
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "⚡ " + ChatColor.GREEN + "Starter equipment and gems added to your inventory!");
            player.sendMessage("");

            // Play sound
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        } catch (Exception e) {
            logger.warning("Error giving new player kit to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Create upgraded weapon following the provided format
     */
    private static ItemStack getUpgradedWeapon(int level) {
        Random random = new Random();
        int min = random.nextInt(2) + 4 + (level * 2);
        int max = random.nextInt(2) + 8 + (level * 2);

        Material material = Material.WOODEN_SWORD;
        String weaponName = "Training Sword";

        ItemStack weapon = new ItemStack(material);
        ItemMeta weaponMeta = weapon.getItemMeta();
        weaponMeta.setDisplayName(ChatColor.WHITE + weaponName);
        List<String> weaponLore = new ArrayList<>();
        weaponLore.add(ChatColor.RED + "DMG: " + min + " - " + max);
        weaponLore.add(getRarity(level));
        weaponMeta.setLore(weaponLore);
        weapon.setItemMeta(weaponMeta);
        return weapon;
    }

    /**
     * Create helmet item following the provided format
     */
    private static ItemStack createHelmetItem(int level, int tier, String name) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta helmetMeta = helmet.getItemMeta();
        helmetMeta.setDisplayName(ChatColor.WHITE + name);
        List<String> helmetLore = new ArrayList<>();
        helmetLore.add(ChatColor.RED + "ARMOR: " + (tier * 2 + level * 3));
        helmetLore.add(ChatColor.RED + "HP: +" + (tier * 10 + level * 15));
        helmetLore.add(ChatColor.RED + "HP REGEN: +" + (10 + (level * 2)) + "/s");
        helmetLore.add(getRarity(level));
        helmetMeta.setLore(helmetLore);
        helmet.setItemMeta(helmetMeta);
        return helmet;
    }

    /**
     * Create gear item following the provided format
     */
    private static ItemStack createGearItem(Material material, int level, int tier, String name) {
        ItemStack gear = new ItemStack(material);
        ItemMeta gearMeta = gear.getItemMeta();
        gearMeta.setDisplayName(ChatColor.WHITE + name);
        List<String> gearLore = new ArrayList<>();
        gearLore.add(ChatColor.RED + "ARMOR: " + (tier * 2 + level * 3));
        gearLore.add(ChatColor.RED + "HP: +" + (tier * 10 + level * 15));
        gearLore.add(ChatColor.RED + "ENERGY REGEN: +" + 3 + "%");
        gearLore.add(getRarity(level));
        gearMeta.setLore(gearLore);
        gear.setItemMeta(gearMeta);
        return gear;
    }

    /**
     * Get rarity string following the provided format
     */
    private static String getRarity(int level) {
        switch (level) {
            case 1: return ChatColor.GRAY + "Common";
            case 2: return ChatColor.GREEN + "Uncommon";
            case 3: return ChatColor.AQUA + "Rare";
            case 4: return ChatColor.YELLOW + "Unique";
            default: return ChatColor.WHITE + "Unknown";
        }
    }

    private void tutorialStep4(Player player, YakPlayer yakPlayer, WelcomeProgress progress) {
        if (!player.isOnline()) return;

        try {
            clearPlayerChat(player);

            TextUtil.sendCenteredMessage(player, "&a&lSTEP 4: SERVER FEATURES");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eYakRealms special features:");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&7• Custom RPG mechanics and leveling");
            TextUtil.sendCenteredMessage(player, "&7• Player guilds and parties");
            TextUtil.sendCenteredMessage(player, "&7• Economy system with gems");
            TextUtil.sendCenteredMessage(player, "&7• Player vs Player combat areas");
            TextUtil.sendCenteredMessage(player, "&7• Custom items and enchantments");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eExplore and discover more as you play!");
            player.sendMessage("");

            progress.completeStep("server_features");
            updateWelcomeBar(player, progress, "Learning server features...");

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                tutorialStep5(player, yakPlayer, progress);
            }, 20L * 10);
        } catch (Exception e) {
            logger.warning("Error in tutorial step 4 for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void tutorialStep5(Player player, YakPlayer yakPlayer, WelcomeProgress progress) {
        if (!player.isOnline()) return;

        try {
            clearPlayerChat(player);

            TextUtil.sendCenteredMessage(player, "&a&l▬▬▬ TUTORIAL COMPLETE! ▬▬▬");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&6🎉 Congratulations, " + player.getName() + "!");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&eYou've completed the YakRealms tutorial!");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&7• You're now ready to begin your adventure");
            TextUtil.sendCenteredMessage(player, "&7• Ask other players or staff if you need help");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&a&lGood luck, adventurer!");
            player.sendMessage("");
            TextUtil.sendCenteredMessage(player, "&fAdventure awaits!");
            player.sendMessage("");

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);

            progress.completeStep("tutorial_complete");
            progress.setComplete(true);

            PlayerSession session = activeSessions.get(player.getUniqueId());
            if (session != null) {
                session.setTutorialComplete(true);
            }

            updateWelcomeBar(player, progress, "Tutorial complete!");
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                BossBar bar = welcomeBars.remove(player.getUniqueId());
                if (bar != null) {
                    safelyRemoveBossBar(bar);
                }
            }, 20L * 5);
        } catch (Exception e) {
            logger.warning("Error in tutorial step 5 for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Update welcome bar with progress
     */
    private void updateWelcomeBar(Player player, WelcomeProgress progress, String message) {
        try {
            BossBar bar = welcomeBars.get(player.getUniqueId());
            if (bar != null) {
                bar.setTitle(ChatColor.GOLD + "✦ " + message + " ✦");
                double progressPercent = (double) progress.getCurrentStep() / maxWelcomeSteps;
                bar.setProgress(Math.min(progressPercent, 1.0));

                if (progressPercent >= 1.0) {
                    bar.setColor(BarColor.GREEN);
                    bar.setTitle(ChatColor.GREEN + "✦ " + ChatColor.BOLD + "Tutorial Complete!" + ChatColor.GREEN + " ✦");
                } else if (progressPercent >= 0.75) {
                    bar.setColor(BarColor.BLUE);
                } else if (progressPercent >= 0.5) {
                    bar.setColor(BarColor.YELLOW);
                } else {
                    bar.setColor(BarColor.WHITE);
                }
            }
        } catch (Exception e) {
            logger.warning("Error updating welcome bar for " + player.getName() + ": " + e.getMessage());
        }
    }

    //  messaging systems
    private void setJoinMessage(PlayerJoinEvent event, Player player) {
        try {
            int onlineCount = Bukkit.getOnlinePlayers().size();

            if (onlineCount == 1) {
                event.setJoinMessage(ChatColor.GOLD + "🌟 " + ChatColor.BOLD + player.getName() +
                        ChatColor.GOLD + " started the adventure!");
            } else if (isVip(player)) {
                event.setJoinMessage(ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + "VIP " + player.getName() +
                        ChatColor.LIGHT_PURPLE + " has joined! ✦");
            } else if (isStaff(player)) {
                event.setJoinMessage(ChatColor.RED + "✦ " + ChatColor.BOLD + "Staff " + player.getName() +
                        ChatColor.RED + " is now online! ✦");
            } else if (onlineCount <= 5) {
                event.setJoinMessage(ChatColor.GREEN + "[+] " + ChatColor.WHITE + player.getName() +
                        ChatColor.GRAY + " (" + onlineCount + " online)");
            } else if (onlineCount <= 20) {
                event.setJoinMessage(ChatColor.GREEN + "[+] " + ChatColor.WHITE + player.getName());
            } else {
                event.setJoinMessage(null);
            }
        } catch (Exception e) {
            logger.warning("Error setting join message for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void setQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer, PlayerSession session) {
        try {
            String displayName = yakPlayer != null ? yakPlayer.getFormattedDisplayName() : player.getName();

            if (session != null && session.getSessionDuration() < 30000) {
                event.setQuitMessage(ChatColor.GRAY + "[-] " + displayName + ChatColor.DARK_GRAY + " (quick leave)");
            } else if (isInCombat(player)) {
                event.setQuitMessage(ChatColor.RED + "[-] " + displayName + ChatColor.DARK_RED + " (combat logged)");
            } else if (Bukkit.getOnlinePlayers().size() <= 3) {
                event.setQuitMessage(ChatColor.RED + "[-] " + displayName + ChatColor.GRAY + " left the server");
            } else {
                event.setQuitMessage(ChatColor.RED + "[-] " + displayName);
            }
        } catch (Exception e) {
            logger.warning("Error setting quit message for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void setKickMessage(PlayerKickEvent event, Player player, YakPlayer yakPlayer) {
        try {
            String displayName = yakPlayer != null ? yakPlayer.getFormattedDisplayName() : player.getName();
            event.setLeaveMessage(ChatColor.RED + "[-] " + displayName + ChatColor.DARK_RED + " (kicked)");
        } catch (Exception e) {
            logger.warning("Error setting kick message for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleBuddyNotifications(Player player, YakPlayer yakPlayer, boolean isJoin, boolean isKick) {
        try {
            if (yakPlayer == null) return;

            long currentTime = System.currentTimeMillis();
            Long lastNotif = lastNotificationTime.get(player.getUniqueId());

            if (lastNotif != null && currentTime - lastNotif < notificationCooldown) {
                return;
            }

            lastNotificationTime.put(player.getUniqueId(), currentTime);

            for (String buddyName : yakPlayer.getBuddies()) {
                Player buddy = Bukkit.getPlayer(buddyName);
                if (buddy != null && buddy.isOnline()) {
                    YakPlayer buddyYakPlayer = playerManager.getPlayer(buddy);
                    if (buddyYakPlayer != null && buddyYakPlayer.getNotificationSetting("buddy_join")) {
                        String action = isJoin ? "joined" : (isKick ? "was kicked from" : "left");
                        ChatColor color = isJoin ? ChatColor.GREEN : ChatColor.RED;
                        String icon = isJoin ? "🟢" : (isKick ? "⚠" : "🔴");

                        buddy.sendMessage(color + icon + " " + ChatColor.GRAY + "Your buddy " +
                                ChatColor.WHITE + player.getName() + ChatColor.GRAY + " " + action + " the server");
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error handling buddy notifications for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handlePartyNotifications(Player player, boolean isJoin) {
        try {
            // Party notification implementation
            if (partyMechanics.isInParty(player)) {
                List<Player> partyMembers = partyMechanics.getPartyMembers(player);
                if (partyMembers != null) {
                    for (Player member : partyMembers) {
                        if (member != null && member.isOnline() && !member.equals(player)) {
                            String action = isJoin ? "joined" : "left";
                            ChatColor color = isJoin ? ChatColor.GREEN : ChatColor.RED;
                            String icon = isJoin ? "🟢" : "🔴";

                            member.sendMessage(color + icon + " " + ChatColor.GRAY + "Party member " +
                                    ChatColor.WHITE + player.getName() + ChatColor.GRAY + " " + action + " the server");
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error handling party notifications for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleGuildNotifications(Player player, YakPlayer yakPlayer, boolean isJoin) {
        try {
            // Guild notification implementation
            if (yakPlayer != null && yakPlayer.isInGuild()) {
                String guildName = yakPlayer.getGuildName();
                if (guildName != null && !guildName.isEmpty()) {
                    // Implementation would notify guild members
                    // This would require guild system integration
                }
            }
        } catch (Exception e) {
            logger.warning("Error handling guild notifications for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleSeasonalContent(Player player, YakPlayer yakPlayer) {
        try {
            if (!enableSeasonalThemes) return;

            String holiday = getCurrentHoliday();
            if (holiday != null) {
                showHolidayContent(player, holiday);
            }
        } catch (Exception e) {
            logger.warning("Error handling seasonal content for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void handleServerAnnouncements(Player player, PlayerSession session) {
        try {
            int onlineCount = Bukkit.getOnlinePlayers().size();

            if (onlineCount == 1) {
                Bukkit.broadcastMessage(ChatColor.GOLD + "🌟 " + ChatColor.BOLD + player.getName() +
                        ChatColor.GOLD + " started the adventure!");
            } else if (onlineCount % 10 == 0 && onlineCount >= 10) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "📊 " + onlineCount + " players are now online!");
            }
        } catch (Exception e) {
            logger.warning("Error handling server announcements for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void notifyStaffOfKick(Player player, String reason) {
        try {
            String message = ChatColor.RED + "[STAFF] " + ChatColor.YELLOW + player.getName() +
                    ChatColor.WHITE + " was kicked: " + ChatColor.GRAY + reason;

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (isStaff(staff) && !staff.equals(player)) {
                    staff.sendMessage(message);
                }
            }
        } catch (Exception e) {
            logger.warning("Error notifying staff of kick: " + e.getMessage());
        }
    }

    // Social integration methods
    private void handleBuddyNotifications(Player player, YakPlayer yakPlayer, boolean isJoin) {
        handleBuddyNotifications(player, yakPlayer, isJoin, false);
    }

    /**
     * Cancel a task safely
     */
    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            try {
                task.cancel();
            } catch (Exception e) {
                logger.fine("Error cancelling task: " + e.getMessage());
            }
        }
    }

    /**
     * Clean up player data safely
     */
    private void cleanupPlayerData(UUID playerId) {
        try {
            activeSessions.remove(playerId);
            welcomeProgress.remove(playerId);
            lastNotificationTime.remove(playerId);
            playersInLimbo.remove(playerId);
            originalLocations.remove(playerId);
            originalInventories.remove(playerId);
            removePlayerBossBar(playerId);

            // Close and remove tutorial menu if open
            TutorialChoiceMenu menu = activeTutorialMenus.remove(playerId);
            if (menu != null) {
                menu.close();
            }
        } catch (Exception e) {
            logger.warning("Error cleaning up player data: " + e.getMessage());
        }
    }

    /**
     * Professional kick message template for consistent formatting
     */
    private static class KickMessageTemplate {
        private final String header;
        private final String title;
        private final String message;
        private final String subtitle;
        private final String detail;
        private final String footer;

        public KickMessageTemplate(String header, String title, String message, String subtitle, String detail, String footer) {
            this.header = header;
            this.title = title;
            this.message = message;
            this.subtitle = subtitle;
            this.detail = detail;
            this.footer = footer;
        }

        /**
         * Build the formatted kick message
         *
         * @param replacements Map of placeholder replacements
         * @return Formatted kick message
         */
        public String buildMessage(Map<String, String> replacements) {

            String sb = processReplacements(header, replacements) + "\n" +
                    "\n" +
                    processReplacements(title, replacements) + "\n" +
                    "\n" +
                    processReplacements(message, replacements) + "\n" +
                    processReplacements(subtitle, replacements) + "\n" +
                    "\n" +
                    processReplacements(detail, replacements) + "\n" +
                    "\n" +
                    processReplacements(footer, replacements);

            return sb;
        }

        private String processReplacements(String text, Map<String, String> replacements) {
            String result = text;
            if (replacements != null) {
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    result = result.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            return result;
        }
    }

    /**
     *  player session tracking with comprehensive metrics
     */
    private static class PlayerSession {
        private final UUID playerId;
        private final long joinTime;
        private final String joinLocation;
        private final int onlinePlayersAtJoin;
        private final AtomicBoolean hasReceivedWelcome = new AtomicBoolean(false);
        private final AtomicBoolean hasReceivedMotd = new AtomicBoolean(false);
        private final AtomicBoolean tutorialComplete = new AtomicBoolean(false);
        private final AtomicBoolean dataLoadCompleted = new AtomicBoolean(false);
        private final AtomicLong dataLoadTime = new AtomicLong(0);
        private final Map<String, Object> sessionData = new ConcurrentHashMap<>();

        public PlayerSession(UUID playerId, Location joinLoc, int onlineCount) {
            this.playerId = playerId;
            this.joinTime = System.currentTimeMillis();
            this.joinLocation = formatLocation(joinLoc);
            this.onlinePlayersAtJoin = onlineCount;
        }

        /**
         * Format location for logging and display
         */
        private static String formatLocation(Location loc) {
            if (loc == null || loc.getWorld() == null) return "unknown";
            return String.format("%s(%.1f,%.1f,%.1f)", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        }
        public long getJoinTime() { return joinTime; }
        public String getJoinLocation() { return joinLocation; }
        public int getOnlinePlayersAtJoin() { return onlinePlayersAtJoin; }
        public boolean hasReceivedWelcome() { return hasReceivedWelcome.get(); }
        public void setReceivedWelcome(boolean received) { hasReceivedWelcome.set(received); }
        public boolean hasReceivedMotd() { return hasReceivedMotd.get(); }
        public void setReceivedMotd(boolean received) { hasReceivedMotd.set(received); }
        public boolean isTutorialComplete() { return tutorialComplete.get(); }
        public void setTutorialComplete(boolean complete) { tutorialComplete.set(complete); }
        public boolean isDataLoadCompleted() { return dataLoadCompleted.get(); }
        public void setDataLoadCompleted(boolean completed) {
            dataLoadCompleted.set(completed);
            if (completed) dataLoadTime.set(System.currentTimeMillis());
        }
        public long getSessionDuration() { return System.currentTimeMillis() - joinTime; }
        public long getDataLoadDuration() {
            long loadTime = dataLoadTime.get();
            return loadTime > 0 ? loadTime - joinTime : System.currentTimeMillis() - joinTime;
        }

        // Getters and setters with proper encapsulation
        public UUID getPlayerId() { return playerId; }
    }

    /**
     * Login streak tracking with  reward system
     */
    private static class LoginStreak {
        private final AtomicInteger currentStreak = new AtomicInteger(0);
        private final AtomicInteger longestStreak = new AtomicInteger(0);
        private final AtomicLong lastLoginDate = new AtomicLong(0);
        private final List<Long> recentLogins = Collections.synchronizedList(new ArrayList<>());

        /**
         * Record a login and update streak
         */
        public void recordLogin() {
            long today = System.currentTimeMillis() / (24 * 60 * 60 * 1000);
            long lastLogin = lastLoginDate.get();

            if (lastLogin == today - 1) {
                currentStreak.incrementAndGet();
            } else if (lastLogin != today) {
                currentStreak.set(1);
            }

            if (currentStreak.get() > longestStreak.get()) {
                longestStreak.set(currentStreak.get());
            }

            lastLoginDate.set(today);
            synchronized (recentLogins) {
                recentLogins.add(System.currentTimeMillis());
                while (recentLogins.size() > 30) {
                    recentLogins.remove(0);
                }
            }
        }

        public int getCurrentStreak() { return currentStreak.get(); }
        public int getLongestStreak() { return longestStreak.get(); }
        public boolean isStreakMilestone() {
            int streak = currentStreak.get();
            return streak > 1 && (streak % 7 == 0 || streak % 30 == 0);
        }
    }

    // Boss bar management
    private void removePlayerBossBar(UUID playerId) {
        BossBar existingBar = welcomeBars.remove(playerId);
        if (existingBar != null) {
            safelyRemoveBossBar(existingBar);
        }
    }

    private void safelyRemoveBossBar(BossBar bossBar) {
        if (bossBar != null) {
            try {
                bossBar.removeAll();
                bossBar.setVisible(false);
            } catch (Exception e) {
                logger.fine("Error removing boss bar: " + e.getMessage());
            }
        }
    }

    private void cleanupBossBars() {
        Iterator<Map.Entry<UUID, BossBar>> iterator = welcomeBars.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, BossBar> entry = iterator.next();
            UUID playerId = entry.getKey();
            BossBar bossBar = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);

            if (player == null || !player.isOnline()) {
                safelyRemoveBossBar(bossBar);
                iterator.remove();
            } else {
                WelcomeProgress progress = welcomeProgress.get(playerId);
                if (progress != null && (progress.isComplete() || progress.getWelcomeProcessDuration() > 120000)) {
                    safelyRemoveBossBar(bossBar);
                    iterator.remove();
                    if (!progress.isComplete()) {
                        progress.setComplete(true);
                    }
                }
            }
        }
    }

    private void cleanupAllBossBars() {
        for (BossBar bossBar : welcomeBars.values()) {
            safelyRemoveBossBar(bossBar);
        }
        welcomeBars.clear();
    }

    // Task processing methods
    private void processWelcomeProgress() {
        for (Map.Entry<UUID, WelcomeProgress> entry : welcomeProgress.entrySet()) {
            UUID playerId = entry.getKey();
            WelcomeProgress progress = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                welcomeProgress.remove(playerId);
                continue;
            }

            if (!progress.isComplete() && progress.getWelcomeProcessDuration() > 120000) {
                progress.setComplete(true);
            }
        }
    }

    private void cleanupStaleData() {
        // Clean up offline players
        activeSessions.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        lastNotificationTime.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        loginStreaks.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        activeTutorialMenus.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
    }

    private void updateServerMetrics() {
        // Metrics are handled automatically in ServerMetrics class
    }

    // Utility methods
    private boolean isVip(Player player) {
        return player.hasPermission("yakrealms.vip");
    }

    private boolean isStaff(Player player) {
        return player.hasPermission("yakrealms.staff");
    }

    private boolean isInCombat(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && yakPlayer.getTemporaryData("inCombat") == Boolean.TRUE;
    }

    private String formatTimeAgo(long secondsAgo) {
        if (secondsAgo < 60) return secondsAgo + " seconds";
        else if (secondsAgo < 3600) return (secondsAgo / 60) + " minutes";
        else if (secondsAgo < 86400) return (secondsAgo / 3600) + " hours";
        else return (secondsAgo / 86400) + " days";
    }

    private String getSeasonalHeader() {
        String holiday = getCurrentHoliday();
        if (holiday != null) {
            switch (holiday.toLowerCase()) {
                case "halloween": return "&6🎃 &e&lHAPPY HALLOWEEN &6🎃";
                case "christmas": return "&c🎄 &f&lMERRY CHRISTMAS &c🎄";
                case "newyear": return "&e✨ &f&lHAPPY NEW YEAR &e✨";
                case "valentine": return "&d💝 &f&lHAPPY VALENTINE'S &d💝";
                default: return "&6✦ &e&lYAK REALMS &6✦";
            }
        }

        String season = getCurrentSeason();
        if (season != null) {
            switch (season.toLowerCase()) {
                case "spring": return "&a🌸 &e&lSPRING ON YAK REALMS &a🌸";
                case "summer": return "&e☀ &6&lSUMMER ON YAK REALMS &e☀";
                case "autumn": return "&6🍂 &e&lAUTUMN ON YAK REALMS &6🍂";
                case "winter": return "&b❄ &f&lWINTER ON YAK REALMS &b❄";
                default: return "&6✦ &e&lYAK REALMS &6✦";
            }
        }

        return "&6✦ &e&lYAK REALMS &6✦";
    }

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

    private void showHolidayContent(Player player, String holiday) {
        TextUtil.sendCenteredMessage(player, "&6✦ Happy " + holiday + "! ✦");
    }

    private void playJoinSound(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);
        } catch (Exception e) {
            // Ignore sound errors
        }
    }

    private void showJoinEffects(Player player) {
        try {
            player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 10, 1, 1, 1, 0.1);
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    /**
     * Welcome progress tracking for comprehensive tutorial system
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

        public boolean hasCompleted(String stepName) { return completedSteps.contains(stepName); }
        public int getCurrentStep() { return currentStep.get(); }
        public boolean isComplete() { return isComplete.get(); }
        public void setComplete(boolean complete) { isComplete.set(complete); }
        public long getWelcomeProcessDuration() { return System.currentTimeMillis() - startTime.get(); }
    }

    private void initializeEmergencyFallback(Player player) {
        logger.severe("Initializing emergency fallback for " + player.getName());

        try {
            exitLoadingLimbo(player);
            initializePlayerAttributes(player);
            TextUtil.sendCenteredMessage(player, "&c⚠ EMERGENCY MODE ACTIVE ⚠");
            TextUtil.sendCenteredMessage(player, "&7Limited functionality - please reconnect");
        } catch (Exception e) {
            logger.severe("Emergency fallback failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Server metrics tracking with comprehensive statistics
     */
    private static class ServerMetrics {
        private final AtomicInteger totalJoinsToday = new AtomicInteger(0);
        private final AtomicInteger totalLeavesToday = new AtomicInteger(0);
        private final AtomicInteger peakOnlineToday = new AtomicInteger(0);
        private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

        public void recordJoin() {
            checkDayReset();
            totalJoinsToday.incrementAndGet();
            int currentOnline = Bukkit.getOnlinePlayers().size();
            if (currentOnline > peakOnlineToday.get()) {
                peakOnlineToday.set(currentOnline);
            }
        }

        public void recordLeave() {
            checkDayReset();
            totalLeavesToday.incrementAndGet();
        }

        private void checkDayReset() {
            long now = System.currentTimeMillis();
            if (now - lastResetTime.get() > 24 * 60 * 60 * 1000) {
                totalJoinsToday.set(0);
                totalLeavesToday.set(0);
                peakOnlineToday.set(0);
                lastResetTime.set(now);
            }
        }

        public int getTotalJoinsToday() { return totalJoinsToday.get(); }
        public int getTotalLeavesToday() { return totalLeavesToday.get(); }
        public int getPeakOnlineToday() { return peakOnlineToday.get(); }
    }

    /**
     * Tutorial Choice Menu Class -  with professional formatting
     */
    private class TutorialChoiceMenu extends Menu {
        private final YakPlayer yakPlayer;
        private final PlayerSession session;

        public TutorialChoiceMenu(Player player, YakPlayer yakPlayer, PlayerSession session) {
            super(player, ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Welcome to YakRealms! ✦", 27);
            this.yakPlayer = yakPlayer;
            this.session = session;
            setupMenu();
        }

        /**
         * Setup the tutorial choice menu with professional formatting
         */
        private void setupMenu() {
            try {
                // Create border
                createBorder(Material.YELLOW_STAINED_GLASS_PANE, " ");

                // Welcome message item
                MenuItem welcomeItem = new MenuItem(Material.NETHER_STAR)
                        .setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Welcome to YakRealms!")
                        .addLoreLine(ChatColor.GRAY + "Hello " + player.getName() + "!")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GRAY + "This is a survival RPG server with")
                        .addLoreLine(ChatColor.GRAY + "custom features, quests, and adventures!")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GRAY + "Would you like to take a quick tutorial")
                        .addLoreLine(ChatColor.GRAY + "to learn the basics?")
                        .setGlowing(true);
                setItem(13, welcomeItem);

                // Tutorial accept button
                MenuItem acceptItem = new MenuItem(Material.LIME_CONCRETE)
                        .setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "YES - Start Tutorial")
                        .addLoreLine(ChatColor.GRAY + "Click to begin the tutorial")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GRAY + "Learn about:")
                        .addLoreLine(ChatColor.GRAY + "• Server commands")
                        .addLoreLine(ChatColor.GRAY + "• Character system")
                        .addLoreLine(ChatColor.GRAY + "• Special features")
                        .addLoreLine(ChatColor.GRAY + "• Economy and gems")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GREEN + "" + ChatColor.BOLD + "➤ Click to start!")
                        .setClickHandler((player, slot) -> acceptTutorial());
                setItem(10, acceptItem);

                // Tutorial decline button
                MenuItem declineItem = new MenuItem(Material.RED_CONCRETE)
                        .setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "NO - Skip Tutorial")
                        .addLoreLine(ChatColor.GRAY + "Click to skip the tutorial")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GRAY + "You can always:")
                        .addLoreLine(ChatColor.GRAY + "• Use " + ChatColor.WHITE + "/help " + ChatColor.GRAY + "for commands")
                        .addLoreLine(ChatColor.GRAY + "• Ask other players for help")
                        .addLoreLine(ChatColor.GRAY + "• Explore on your own")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.RED + "" + ChatColor.BOLD + "➤ Click to skip!")
                        .setClickHandler((player, slot) -> declineTutorial());
                setItem(16, declineItem);

                // Info items
                MenuItem infoItem1 = new MenuItem(Material.BOOK)
                        .setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Server Information")
                        .addLoreLine(ChatColor.GRAY + "• Custom RPG mechanics")
                        .addLoreLine(ChatColor.GRAY + "• Player guilds & parties")
                        .addLoreLine(ChatColor.GRAY + "• Economy system")
                        .addLoreLine(ChatColor.GRAY + "• PvP combat areas")
                        .addLoreLine(ChatColor.GRAY + "• Custom items & enchants");
                setItem(11, infoItem1);

                MenuItem infoItem2 = new MenuItem(Material.DIAMOND)
                        .setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Starter Kit")
                        .addLoreLine(ChatColor.GRAY + "You will receive:")
                        .addLoreLine(ChatColor.GRAY + "• Training sword")
                        .addLoreLine(ChatColor.GRAY + "• Leather armor set")
                        .addLoreLine(ChatColor.GRAY + "• 1,000 gems")
                        .addLoreLine("")
                        .addLoreLine(ChatColor.GRAY + "This kit is given automatically!")
                        .setGlowing(true);
                setItem(15, infoItem2);

            } catch (Exception e) {
                logger.severe("Error setting up tutorial choice menu for " + player.getName() + ": " + e.getMessage());
            }
        }

        /**
         * Handle tutorial acceptance
         */
        private void acceptTutorial() {
            try {
                close();
                activeTutorialMenus.remove(player.getUniqueId());

                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "✓ Tutorial starting...");
                player.sendMessage("");

                // Start tutorial
                scheduleNewPlayerTutorial(player, yakPlayer, session);

            } catch (Exception e) {
                logger.severe("Error accepting tutorial for " + player.getName() + ": " + e.getMessage());
            }
        }

        /**
         * Handle tutorial decline
         */
        private void declineTutorial() {
            try {
                close();
                activeTutorialMenus.remove(player.getUniqueId());

                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Tutorial skipped. You can always ask for help in chat!");
                TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Use " + ChatColor.WHITE + "/help " + ChatColor.GRAY + "to see available commands.");
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "" + ChatColor.BOLD + "Enjoy your adventure on YakRealms!");
                player.sendMessage("");

            } catch (Exception e) {
                logger.severe("Error declining tutorial for " + player.getName() + ": " + e.getMessage());
            }
        }

        @Override
        protected void onPostClose() {
            activeTutorialMenus.remove(player.getUniqueId());
        }
    }

}
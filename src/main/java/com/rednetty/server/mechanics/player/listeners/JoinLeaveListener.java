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

/**
 *  JoinLeaveListener with  YakPlayerManager coordination
 *
 * CRITICAL FIXES:
 * -  coordination with YakPlayerManager state system
 * - Proper waiting for player loading completion before tutorials/welcome
 * - Better state validation and error handling
 * - Improved synchronization with all subsystems
 * - Comprehensive timeout handling and recovery
 */
public class JoinLeaveListener extends BaseListener {

    // Configuration constants
    private static final int MAX_TUTORIAL_STEPS = 5;
    private static final long NOTIFICATION_COOLDOWN = 5000L;
    private static final long WELCOME_DELAY_TICKS = 40L; // Wait for YakPlayerManager to finish
    private static final int MAX_LOADING_WAIT_ATTEMPTS = 200; // 100 seconds max wait
    private static final int STATE_CHECK_INTERVAL_TICKS = 10; // Check every 0.5 seconds

    // Core dependencies
    private final YakPlayerManager playerManager;
    private final Logger logger;
    private final EconomyManager economyManager;
    private final PartyMechanics partyMechanics;
    private final ChatMechanics chatMechanics;

    //  state tracking with YakPlayerManager coordination
    private final Map<UUID, WelcomeProgress> welcomeProgress = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> welcomeBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private final Map<UUID, TutorialChoiceMenu> activeTutorialMenus = new ConcurrentHashMap<>();
    private final Map<UUID, LoginStreak> loginStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> loadingWaitTasks = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> waitAttempts = new ConcurrentHashMap<>();

    // Server metrics
    private final ServerMetrics serverMetrics = new ServerMetrics();
    private final AtomicInteger dailyJoinCount = new AtomicInteger(0);
    private final AtomicInteger coordinationTimeouts = new AtomicInteger(0);
    private final AtomicInteger successfulWelcomes = new AtomicInteger(0);

    // Configuration
    private volatile boolean enableAdvancedWelcome = true;
    private volatile boolean enableLoginRewards = true;
    private volatile boolean enableSocialNotifications = true;
    private volatile boolean enableSeasonalThemes = true;
    private volatile boolean enableJoinEffects = true;
    private volatile long notificationCooldown = NOTIFICATION_COOLDOWN;

    // Task management
    private BukkitTask welcomeCleanupTask;
    private BukkitTask bossBarCleanupTask;

    /**
     * Constructor - Initialize all components
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
     * Initialize the listener
     */
    public void initialize() {
        logger.info(" JoinLeaveListener initialized with  YakPlayerManager coordination");
        startTasks();
    }

    /**
     * Cleanup all resources
     */
    public void cleanup() {
        try {
            cancelTask(welcomeCleanupTask);
            cancelTask(bossBarCleanupTask);
            cleanupAllBossBars();

            // Cancel all loading wait tasks
            for (BukkitTask task : loadingWaitTasks.values()) {
                cancelTask(task);
            }
            loadingWaitTasks.clear();
            waitAttempts.clear();

            // Close tutorial menus
            for (TutorialChoiceMenu menu : activeTutorialMenus.values()) {
                try {
                    menu.close();
                } catch (Exception e) {
                    logger.fine("Error closing tutorial menu: " + e.getMessage());
                }
            }
            activeTutorialMenus.clear();

            logger.info(" JoinLeaveListener cleanup completed");

        } catch (Exception e) {
            logger.severe("Error during JoinLeaveListener cleanup: " + e.getMessage());
        }
    }

    /**
     * Load configuration
     */
    private void loadConfiguration() {
        try {
            var config = YakRealms.getInstance().getConfig();
            this.enableAdvancedWelcome = config.getBoolean("join_leave.advanced_welcome", true);
            this.enableLoginRewards = config.getBoolean("join_leave.login_rewards", true);
            this.enableSocialNotifications = config.getBoolean("join_leave.social_notifications", true);
            this.enableSeasonalThemes = config.getBoolean("join_leave.seasonal_themes", true);
            this.enableJoinEffects = config.getBoolean("join_leave.join_effects", true);
            this.notificationCooldown = config.getLong("join_leave.notification_cooldown", NOTIFICATION_COOLDOWN);

            logger.info(" JoinLeaveListener configuration loaded");

        } catch (Exception e) {
            logger.warning("Error loading JoinLeaveListener configuration: " + e.getMessage());
        }
    }

    /**
     * Start background tasks
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

            // Welcome progress cleanup task
            welcomeCleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    cleanupStaleWelcomeData();
                }
            }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 300, 20L * 300);

            logger.info(" JoinLeaveListener tasks started");

        } catch (Exception e) {
            logger.severe("Error starting JoinLeaveListener tasks: " + e.getMessage());
        }
    }

    /**
     *  Player join handler - coordinates with  YakPlayerManager
     *
     * This runs AFTER YakPlayerManager (MONITOR priority)
     * YakPlayerManager handles all data loading, this handles welcome/tutorial
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            dailyJoinCount.incrementAndGet();
            serverMetrics.recordJoin();

            // Play join sound and effects immediately (safe during loading)
            if (enableJoinEffects) {
                playJoinSound(player);
                showJoinEffects(player);
            }

            // : Schedule  welcome process with proper YakPlayerManager coordination
            scheduleWelcomeProcess(player);

            logger.info(" JoinLeaveListener processing join for: " + player.getName());

        } catch (Exception ex) {
            logger.severe("Error in  JoinLeaveListener onJoin for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     *  Schedule welcome process with  YakPlayerManager coordination
     */
    private void scheduleWelcomeProcess(Player player) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing wait task for this player
        BukkitTask existingTask = loadingWaitTasks.remove(playerId);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }

        // Reset wait attempts
        waitAttempts.put(playerId, new AtomicInteger(0));

        //  waiting task with comprehensive YakPlayerManager state checking
        BukkitTask waitTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    loadingWaitTasks.remove(playerId);
                    waitAttempts.remove(playerId);
                    this.cancel();
                    return;
                }

                AtomicInteger attempts = waitAttempts.get(playerId);
                if (attempts == null) {
                    this.cancel();
                    return;
                }

                int currentAttempt = attempts.incrementAndGet();

                // : Check comprehensive YakPlayerManager state
                boolean isLoading = playerManager.isPlayerLoading(playerId);
                boolean isInLimbo = playerManager.isPlayerInVoidLimbo(playerId);
                boolean isProtected = playerManager.isPlayerProtected(playerId);
                boolean isFullyLoaded = playerManager.isPlayerFullyLoaded(playerId);
                YakPlayerManager.PlayerState playerState = playerManager.getPlayerState(playerId);
                YakPlayerManager.LoadingPhase phase = playerManager.getPlayerLoadingPhase(playerId);

                //  logging every 10 seconds for debugging
                if (currentAttempt % 20 == 0) { // Every 10 seconds
                    logger.info(" WAIT: " + player.getName() +
                            " - State: " + playerState +
                            ", Loading: " + isLoading +
                            ", InLimbo: " + isInLimbo +
                            ", Protected: " + isProtected +
                            ", FullyLoaded: " + isFullyLoaded +
                            ", Phase: " + phase +
                            ", Attempts: " + currentAttempt);
                }

                // : Check if YakPlayerManager processing is complete
                boolean loadingComplete = isFullyLoaded &&
                        !isLoading &&
                        !isInLimbo &&
                        !isProtected &&
                        playerState == YakPlayerManager.PlayerState.READY &&
                        (phase == YakPlayerManager.LoadingPhase.COMPLETED || phase == null);

                if (loadingComplete) {
                    // YakPlayerManager is completely done, proceed with welcome
                    YakPlayer yakPlayer = playerManager.getPlayer(playerId);
                    if (yakPlayer != null) {
                        logger.info(" WAIT: " + player.getName() +
                                " YakPlayerManager loading complete, starting welcome sequence");
                        processWelcomeSequence(player, yakPlayer);
                        successfulWelcomes.incrementAndGet();
                    } else {
                        logger.warning(" WAIT: YakPlayerManager finished but no YakPlayer found for " + player.getName());
                        showBasicWelcome(player);
                    }
                    loadingWaitTasks.remove(playerId);
                    waitAttempts.remove(playerId);
                    this.cancel();

                } else if (currentAttempt >= MAX_LOADING_WAIT_ATTEMPTS) {
                    // Timeout waiting for YakPlayerManager
                    logger.warning(" WAIT: Timeout waiting for YakPlayerManager to finish loading " + player.getName() +
                            " (State: " + playerState +
                            ", Loading: " + isLoading +
                            ", InLimbo: " + isInLimbo +
                            ", Protected: " + isProtected +
                            ", FullyLoaded: " + isFullyLoaded +
                            ", Phase: " + phase + ")");

                    coordinationTimeouts.incrementAndGet();
                    showBasicWelcome(player);
                    loadingWaitTasks.remove(playerId);
                    waitAttempts.remove(playerId);
                    this.cancel();
                }
                // Otherwise keep waiting for YakPlayerManager to complete
            }
        }.runTaskTimer(YakRealms.getInstance(), WELCOME_DELAY_TICKS, STATE_CHECK_INTERVAL_TICKS);

        loadingWaitTasks.put(playerId, waitTask);
    }

    /**
     *  Process welcome sequence once YakPlayerManager has finished loading
     */
    private void processWelcomeSequence(Player player, YakPlayer yakPlayer) {
        UUID playerId = player.getUniqueId();

        try {
            logger.info(" WELCOME: Starting sequence for " + player.getName());

            boolean isNewPlayer = isNewPlayer(yakPlayer);

            // Send MOTD
            sendMotd(player, yakPlayer, isNewPlayer);

            // Handle login streaks
            if (enableLoginRewards) {
                handleLoginStreak(player, yakPlayer);
            }

            // Advanced welcome experience
            if (enableAdvancedWelcome) {
                if (isNewPlayer) {
                    // Show tutorial choice menu for new players
                    scheduleNewPlayerWelcome(player, yakPlayer);
                } else {
                    // Show returning player welcome
                    scheduleReturningPlayerWelcome(player, yakPlayer);
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

            logger.info(" WELCOME: Sequence completed for " + player.getName() + " (new: " + isNewPlayer + ")");

        } catch (Exception e) {
            logger.severe("Error in  welcome sequence for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Show basic welcome for fallback scenarios
     */
    private void showBasicWelcome(Player player) {
        try {
            logger.info("BASIC WELCOME: Showing fallback welcome for " + player.getName());
        } catch (Exception e) {
            logger.warning("Error showing basic welcome: " + e.getMessage());
        }
    }

    /**
     * Player quit handler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            serverMetrics.recordLeave();

            YakPlayer yakPlayer = playerManager.getPlayer(player);

            // Set quit message
            setQuitMessage(event, player, yakPlayer);

            // Handle social notifications
            if (enableSocialNotifications && yakPlayer != null) {
                handleBuddyNotifications(player, yakPlayer, false);
                handlePartyNotifications(player, false);
                handleGuildNotifications(player, yakPlayer, false);
            }

            // Cleanup
            cleanupPlayerData(playerId);

            logger.info(" JoinLeaveListener processed quit for: " + player.getName());

        } catch (Exception ex) {
            logger.warning("Error in  JoinLeaveListener onPlayerQuit for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Player kick handler
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            // Set kick message
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

            logger.info(" JoinLeaveListener processed kick for: " + player.getName());

        } catch (Exception ex) {
            logger.warning("Error in  JoinLeaveListener onPlayerKick for " + player.getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Check if player is new
     */
    private boolean isNewPlayer(YakPlayer yakPlayer) {
        try {
            long twoHoursAgo = (System.currentTimeMillis() / 1000) - 7200;
            boolean isRecentJoin = yakPlayer.getFirstJoin() > twoHoursAgo;
            boolean hasNeverSeenTutorial = !yakPlayer.isToggled("Tutorial Offered");

            return isRecentJoin && hasNeverSeenTutorial;
        } catch (Exception e) {
            logger.warning("Error checking if player is new: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send MOTD with clean formatting
     */
    public void sendMotd(Player player, YakPlayer yakPlayer, boolean isNewPlayer) {
        try {
            // Give a moment for the player to fully settle after YakPlayerManager completion
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (!player.isOnline()) return;

                String seasonalHeader = getSeasonalHeader();

                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, seasonalHeader);
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&6✦ &e&lYAK REALMS &6✦");
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&fRecode - Alpha &6v" + YakRealms.getInstance().getDescription().getVersion());

                int onlineCount = Bukkit.getOnlinePlayers().size();
                int peakToday = serverMetrics.getPeakOnlineToday();
                TextUtil.sendCenteredMessage(player, "&7Online: &f" + onlineCount + " &7| Peak Today: &f" + peakToday);

                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&7This server is in early development - expect bugs!");
                TextUtil.sendCenteredMessage(player, "&7Need help? Use &f/help &7or ask in chat!");
                TextUtil.sendCenteredMessage(player, "&7Join our Discord: &9https://discord.gg/JYf6R2VKE7");

                if (isNewPlayer) {
                    player.sendMessage("");
                    TextUtil.sendCenteredMessage(player, "&eWelcome to YakRealms! &7You're our newest adventurer!");
                } else {
                    player.sendMessage("");
                    long playtime = yakPlayer.getTotalPlaytime() / 3600;
                    LoginStreak streak = loginStreaks.get(player.getUniqueId());
                    int loginStreak = streak != null ? streak.getCurrentStreak() : 0;

                    TextUtil.sendCenteredMessage(player, "&eWelcome back! &7Playtime: &f" + playtime + "h &7| Streak: &f" + loginStreak + " days");

                    long lastLogin = yakPlayer.getLastLogout();
                    if (lastLogin > 0) {
                        long timeSince = (System.currentTimeMillis() / 1000) - lastLogin;
                        String timeAgo = formatTimeAgo(timeSince);
                        TextUtil.sendCenteredMessage(player, "&7Last seen: &f" + timeAgo + " &7ago");
                    }
                }

                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&b&lCharacter Info");
                TextUtil.sendCenteredMessage(player, "&7Alignment: &6" + yakPlayer.getAlignment() + " &7| Level: &6" + yakPlayer.getLevel());
                TextUtil.sendCenteredMessage(player, "&7Gems: &6" + TextUtil.formatNumber(yakPlayer.getBankGems()));

                if (yakPlayer.isInGuild()) {
                    TextUtil.sendCenteredMessage(player, "&7Guild: &d" + yakPlayer.getGuildName());
                }

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
            }, 20L); // 1 second delay after YakPlayerManager completion

        } catch (Exception e) {
            logger.warning("Error sending MOTD to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handle login streak
     */
    private void handleLoginStreak(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();
            LoginStreak streak = loginStreaks.computeIfAbsent(playerId, k -> new LoginStreak());

            int oldStreak = streak.getCurrentStreak();
            streak.recordLogin();
            int newStreak = streak.getCurrentStreak();

            if (newStreak > oldStreak && newStreak > 1) {
                // Delay the streak message to not interfere with MOTD
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (player.isOnline()) {
                        TextUtil.sendCenteredMessage(player, "&6🔥 &fLogin Streak: &a&l" + newStreak + " days!");

                        if (streak.isStreakMilestone()) {
                            int bonus = newStreak >= 30 ? 1000 : (newStreak >= 7 ? 500 : 100);
                            yakPlayer.setBankGems(yakPlayer.getBankGems() + bonus);

                            TextUtil.sendCenteredMessage(player, "&6🎉 &fStreak Milestone! &6+" + bonus + " gems!");
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        }
                    }
                }, 60L);
            } else if (newStreak == 1 && oldStreak > 1) {
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (player.isOnline()) {
                        TextUtil.sendCenteredMessage(player, "&e💔 &7Login streak broken! Starting fresh.");
                    }
                }, 60L);
            }
        } catch (Exception e) {
            logger.warning("Error handling login streak for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Schedule new player welcome with tutorial choice
     */
    private void scheduleNewPlayerWelcome(Player player, YakPlayer yakPlayer) {
        UUID playerId = player.getUniqueId();

        try {
            // Mark tutorial as offered
            yakPlayer.toggleSetting("Tutorial Offered");
            playerManager.savePlayer(yakPlayer);

            // Give new player kit
            giveNewPlayerKit(player, yakPlayer);

            // : Show tutorial choice menu with proper delay after YakPlayerManager completion
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline() && playerManager.isPlayerFullyLoaded(playerId) &&
                        !playerManager.isPlayerInVoidLimbo(playerId) &&
                        !playerManager.isPlayerLoading(playerId) &&
                        !playerManager.isPlayerProtected(playerId)) {
                    showTutorialChoiceMenu(player, yakPlayer);
                } else if (player.isOnline()) {
                    // Player still not ready, wait a bit more
                    Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                        if (player.isOnline() && playerManager.isPlayerFullyLoaded(playerId) &&
                                !playerManager.isPlayerInVoidLimbo(playerId) &&
                                !playerManager.isPlayerLoading(playerId) &&
                                !playerManager.isPlayerProtected(playerId)) {
                            showTutorialChoiceMenu(player, yakPlayer);
                        } else {
                            logger.warning(": Player " + player.getName() + " still not ready for tutorial after extended delay");
                        }
                    }, 60L);
                }
            }, 80L); // 4 second delay

        } catch (Exception e) {
            logger.severe("Error scheduling new player welcome for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Show tutorial choice menu
     */
    private void showTutorialChoiceMenu(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();

            // : Triple-check they're completely ready with YakPlayerManager
            if (!playerManager.isPlayerFullyLoaded(playerId) ||
                    playerManager.isPlayerInVoidLimbo(playerId) ||
                    playerManager.isPlayerLoading(playerId) ||
                    playerManager.isPlayerProtected(playerId)) {
                logger.info("Tutorial delayed - player still in YakPlayerManager system: " + player.getName());
                return;
            }

            TutorialChoiceMenu menu = new TutorialChoiceMenu(player, yakPlayer);
            activeTutorialMenus.put(playerId, menu);
            menu.open();

        } catch (Exception e) {
            logger.severe("Error showing tutorial choice menu for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Schedule returning player welcome
     */
    private void scheduleReturningPlayerWelcome(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();

            // Delay the welcome bar to ensure smooth transition after YakPlayerManager completion
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (!player.isOnline() ||
                        !playerManager.isPlayerFullyLoaded(playerId) ||
                        playerManager.isPlayerInVoidLimbo(playerId) ||
                        playerManager.isPlayerLoading(playerId)) return;

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
                    removePlayerBossBar(playerId);
                }, 20L * 8);
            }, 60L); // 3 second delay

        } catch (Exception e) {
            logger.warning("Error scheduling returning player welcome for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Give new player kit
     */
    private void giveNewPlayerKit(Player player, YakPlayer yakPlayer) {
        try {
            UUID playerId = player.getUniqueId();

            // : Delay kit giving to ensure YakPlayerManager is completely done
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (!player.isOnline() ||
                        !playerManager.isPlayerFullyLoaded(playerId) ||
                        playerManager.isPlayerInVoidLimbo(playerId) ||
                        playerManager.isPlayerLoading(playerId) ||
                        playerManager.isPlayerProtected(playerId)) {
                    logger.info(": Delaying kit giving - player still in YakPlayerManager system: " + player.getName());
                    return;
                }

                PlayerInventory inventory = player.getInventory();

                // Give weapon if not present
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

                // Give armor if not present
                if (inventory.getHelmet() == null) {
                    inventory.setHelmet(createHelmetItem(1, 1, "Leather Coif"));
                }
                if (inventory.getChestplate() == null) {
                    inventory.setChestplate(createGearItem(Material.LEATHER_CHESTPLATE, 1, 1, "Leather Chestplate"));
                }
                if (inventory.getLeggings() == null) {
                    inventory.setLeggings(createGearItem(Material.LEATHER_LEGGINGS, 1, 1, "Leather Leggings"));
                }
                if (inventory.getBoots() == null) {
                    inventory.setBoots(createGearItem(Material.LEATHER_BOOTS, 1, 1, "Leather Boots"));
                }

                // Give starter gems
                yakPlayer.setBankGems(yakPlayer.getBankGems() + 1000);

                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "⚡ " + ChatColor.GREEN + "Starter equipment and gems added!");
                player.sendMessage("");

                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }, 80L); // 4 second delay

        } catch (Exception e) {
            logger.warning("Error giving new player kit to " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Social notification methods
     */
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

    private void handleBuddyNotifications(Player player, YakPlayer yakPlayer, boolean isJoin) {
        handleBuddyNotifications(player, yakPlayer, isJoin, false);
    }

    private void handlePartyNotifications(Player player, boolean isJoin) {
        try {
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
            if (yakPlayer != null && yakPlayer.isInGuild()) {
                // Guild notification implementation would go here
                // Placeholder for guild system integration
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

    /**
     * Message formatting methods
     */
    private void setQuitMessage(PlayerQuitEvent event, Player player, YakPlayer yakPlayer) {
        try {
            String displayName = yakPlayer != null ? yakPlayer.getFormattedDisplayName() : player.getName();
            event.setQuitMessage(ChatColor.RED + "[-] " + displayName);
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

    /**
     * Utility methods
     */
    private void cleanupPlayerData(UUID playerId) {
        try {
            welcomeProgress.remove(playerId);
            lastNotificationTime.remove(playerId);
            removePlayerBossBar(playerId);

            // Cancel loading wait task
            BukkitTask waitTask = loadingWaitTasks.remove(playerId);
            if (waitTask != null && !waitTask.isCancelled()) {
                waitTask.cancel();
            }
            waitAttempts.remove(playerId);

            TutorialChoiceMenu menu = activeTutorialMenus.remove(playerId);
            if (menu != null) {
                menu.close();
            }
        } catch (Exception e) {
            logger.warning("Error cleaning up player data: " + e.getMessage());
        }
    }

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
            }
        }
    }

    private void cleanupAllBossBars() {
        for (BossBar bossBar : welcomeBars.values()) {
            safelyRemoveBossBar(bossBar);
        }
        welcomeBars.clear();
    }

    private void cleanupStaleWelcomeData() {
        // Clean up offline players
        welcomeProgress.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        lastNotificationTime.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        loginStreaks.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        activeTutorialMenus.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);

        // Clean up loading wait tasks for offline players
        loadingWaitTasks.entrySet().removeIf(entry -> {
            if (Bukkit.getPlayer(entry.getKey()) == null) {
                BukkitTask task = entry.getValue();
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                }
                waitAttempts.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            try {
                task.cancel();
            } catch (Exception e) {
                logger.fine("Error cancelling task: " + e.getMessage());
            }
        }
    }

    // Helper methods
    private boolean isStaff(Player player) {
        return player.hasPermission("yakrealms.staff");
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
            player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 10, 1, 1, 1, 0.1);
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    // Item creation methods (simplified for brevity)
    private static ItemStack getUpgradedWeapon(int level) {
        Random random = new Random();
        int min = random.nextInt(2) + 4 + (level * 2);
        int max = random.nextInt(2) + 8 + (level * 2);

        ItemStack weapon = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta weaponMeta = weapon.getItemMeta();
        weaponMeta.setDisplayName(ChatColor.WHITE + "Training Sword");
        List<String> weaponLore = new ArrayList<>();
        weaponLore.add(ChatColor.RED + "DMG: " + min + " - " + max);
        weaponLore.add(getRarity(level));
        weaponMeta.setLore(weaponLore);
        weapon.setItemMeta(weaponMeta);
        return weapon;
    }

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

    private static String getRarity(int level) {
        switch (level) {
            case 1: return ChatColor.GRAY + "Common";
            case 2: return ChatColor.GREEN + "Uncommon";
            case 3: return ChatColor.AQUA + "Rare";
            case 4: return ChatColor.YELLOW + "Unique";
            default: return ChatColor.WHITE + "Unknown";
        }
    }

    // Data classes (simplified for brevity - same as original but with  metrics)
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

    private static class LoginStreak {
        private final AtomicInteger currentStreak = new AtomicInteger(0);
        private final AtomicInteger longestStreak = new AtomicInteger(0);
        private final AtomicLong lastLoginDate = new AtomicLong(0);

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
        }

        public int getCurrentStreak() { return currentStreak.get(); }
        public int getLongestStreak() { return longestStreak.get(); }
        public boolean isStreakMilestone() {
            int streak = currentStreak.get();
            return streak > 1 && (streak % 7 == 0 || streak % 30 == 0);
        }
    }

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
     * Tutorial Choice Menu for new players (simplified for brevity)
     */
    private class TutorialChoiceMenu extends Menu {
        private final YakPlayer yakPlayer;

        public TutorialChoiceMenu(Player player, YakPlayer yakPlayer) {
            super(player, ChatColor.GOLD + "" + ChatColor.BOLD + "✦ Welcome to YakRealms! ✦", 27);
            this.yakPlayer = yakPlayer;
            setupMenu();
        }

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

                // Tutorial accept/decline buttons (simplified)
                MenuItem acceptItem = new MenuItem(Material.LIME_CONCRETE)
                        .setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "YES - Start Tutorial")
                        .setClickHandler((player, slot) -> acceptTutorial());
                setItem(10, acceptItem);

                MenuItem declineItem = new MenuItem(Material.RED_CONCRETE)
                        .setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "NO - Skip Tutorial")
                        .setClickHandler((player, slot) -> declineTutorial());
                setItem(16, declineItem);

            } catch (Exception e) {
                logger.severe("Error setting up tutorial choice menu for " + player.getName() + ": " + e.getMessage());
            }
        }

        private void acceptTutorial() {
            try {
                close();
                activeTutorialMenus.remove(player.getUniqueId());
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "✓ Tutorial starting...");
                player.sendMessage("");
                startSimpleTutorial(player, yakPlayer);
            } catch (Exception e) {
                logger.severe("Error accepting tutorial for " + player.getName() + ": " + e.getMessage());
            }
        }

        private void declineTutorial() {
            try {
                close();
                activeTutorialMenus.remove(player.getUniqueId());
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Tutorial skipped. Use " + ChatColor.WHITE + "/help " + ChatColor.GRAY + "for commands.");
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

    /**
     * Simple tutorial implementation (simplified)
     */
    private void startSimpleTutorial(Player player, YakPlayer yakPlayer) {
        showTutorialWelcome(player, 0);
    }

    private void showTutorialWelcome(Player player, int step) {
        if (!player.isOnline()) return;

        switch (step) {
            case 0:
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&6&l▬▬▬ YAKREALMS TUTORIAL ▬▬▬");
                TextUtil.sendCenteredMessage(player, "&a&lWELCOME TO YAKREALMS!");
                TextUtil.sendCenteredMessage(player, "&eThis is a survival RPG server with custom features.");
                player.sendMessage("");
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () ->
                        showTutorialWelcome(player, 1), 80L);
                break;

            case 1:
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&a&lIMPORTANT COMMANDS:");
                TextUtil.sendCenteredMessage(player, "&b/help &7- Show help menu");
                TextUtil.sendCenteredMessage(player, "&b/stats &7- View your character stats");
                TextUtil.sendCenteredMessage(player, "&b/p invite <player> &7- Invite to party");
                player.sendMessage("");
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () ->
                        showTutorialWelcome(player, 2), 80L);
                break;

            case 2:
                player.sendMessage("");
                TextUtil.sendCenteredMessage(player, "&a&lTUTORIAL COMPLETE!");
                TextUtil.sendCenteredMessage(player, "&6🎉 Congratulations, " + player.getName() + "!");
                TextUtil.sendCenteredMessage(player, "&eYou're now ready to begin your adventure!");
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                break;
        }
    }

    /**
     * Public API methods with  metrics
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.put("welcome_in_progress", welcomeProgress.size());
            stats.put("welcome_bars_active", welcomeBars.size());
            stats.put("daily_join_count", dailyJoinCount.get());
            stats.put("total_joins_today", serverMetrics.getTotalJoinsToday());
            stats.put("total_leaves_today", serverMetrics.getTotalLeavesToday());
            stats.put("peak_online_today", serverMetrics.getPeakOnlineToday());
            stats.put("active_tutorial_menus", activeTutorialMenus.size());
            stats.put("loading_wait_tasks", loadingWaitTasks.size());
            stats.put("coordination_timeouts", coordinationTimeouts.get());
            stats.put("successful_welcomes", successfulWelcomes.get());
            stats.put("coordinates_with_yakplayermanager", true);
            stats.put("_coordination", true);
            stats.put("_phase_detection", true);
        } catch (Exception e) {
            logger.warning("Error getting session stats: " + e.getMessage());
        }
        return stats;
    }
}
package com.rednetty.server.mechanics.moderation;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.database.YakPlayerRepository;
import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced moderation functionality including:
 * - Comprehensive player ranks management with proper permission hierarchies
 * - Advanced banning and muting system with duration tracking
 * - Robust chat tag management with validation
 * - Performance optimized permission caching
 * - Enhanced error handling and recovery
 * - Staff activity monitoring and important staff protection
 * - Automatic rank validation and correction
 * - Permission debugging and audit capabilities
 */
public class ModerationMechanics implements Listener {

    private static ModerationMechanics instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicInteger permissionUpdates = new AtomicInteger(0);

    // Enhanced caching with performance tracking
    public static final Map<UUID, Rank> rankMap = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPermissionUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, String> permissionDebugLog = new ConcurrentHashMap<>();

    // Staff activity tracking
    private final Map<UUID, Long> staffLastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> onlineStaff = ConcurrentHashMap.newKeySet();

    // Performance monitoring
    private final Map<String, Long> operationTimes = new ConcurrentHashMap<>();
    private BukkitTask performanceTask;
    private BukkitTask staffActivityTask;

    // Enhanced constants
    private static final List<String> IMPORTANT_STAFF = Arrays.asList("Red");
    private static final long PERMISSION_UPDATE_COOLDOWN = 1000L; // 1 second cooldown
    private static final long STAFF_ACTIVITY_TIMEOUT = 300000L; // 5 minutes
    private static final int MAX_RANK_VALIDATION_ATTEMPTS = 3;

    /**
     * Gets the singleton instance of ModerationMechanics
     *
     * @return The ModerationMechanics instance
     */
    public static ModerationMechanics getInstance() {
        if (instance == null) {
            instance = new ModerationMechanics();
        }
        return instance;
    }

    /**
     * Private constructor for singleton pattern
     */
    private ModerationMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Enhanced initialization with performance monitoring
     */
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        try {
            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Initialize ranks for online players with enhanced error handling
            int successCount = 0;
            int failureCount = 0;

            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    initializePlayer(player);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    logger.log(Level.WARNING, "Failed to initialize player " + player.getName() + " during startup", e);
                }
            }

            // Start monitoring tasks
            startPerformanceMonitoring();
            startStaffActivityMonitoring();

            long initTime = System.currentTimeMillis() - startTime;
            recordOperationTime("startup", initTime);

            YakRealms.log("ModerationMechanics has been enabled successfully!");
            YakRealms.log("├─ Initialized " + successCount + " players successfully");
            if (failureCount > 0) {
                YakRealms.log("├─ Failed to initialize " + failureCount + " players");
            }
            YakRealms.log("├─ Startup time: " + initTime + "ms");
            YakRealms.log("└─ Performance monitoring enabled");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during ModerationMechanics initialization", e);
        }
    }

    /**
     * Enhanced cleanup with comprehensive data saving
     */
    public void onDisable() {
        long startTime = System.currentTimeMillis();
        isShuttingDown.set(true);

        try {
            // Cancel monitoring tasks
            if (performanceTask != null) {
                performanceTask.cancel();
            }
            if (staffActivityTask != null) {
                staffActivityTask.cancel();
            }

            // Remove all permission attachments safely
            int attachmentsCleaned = 0;
            for (Map.Entry<UUID, PermissionAttachment> entry : permissionMap.entrySet()) {
                try {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        player.removeAttachment(entry.getValue());
                        attachmentsCleaned++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error removing permission attachment during shutdown", e);
                }
            }

            // Save all ranks with enhanced error handling
            saveAllRanks();

            // Log performance statistics
            logPerformanceStats();

            // Clear all caches
            permissionMap.clear();
            rankMap.clear();
            lastPermissionUpdate.clear();
            permissionDebugLog.clear();
            staffLastActivity.clear();
            onlineStaff.clear();
            operationTimes.clear();

            long shutdownTime = System.currentTimeMillis() - startTime;

            YakRealms.log("ModerationMechanics has been disabled successfully!");
            YakRealms.log("├─ Cleaned " + attachmentsCleaned + " permission attachments");
            YakRealms.log("├─ Saved " + rankMap.size() + " player ranks");
            YakRealms.log("├─ Total permission updates: " + permissionUpdates.get());
            YakRealms.log("└─ Shutdown time: " + shutdownTime + "ms");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during ModerationMechanics shutdown", e);
        }
    }

    /**
     * Enhanced rank saving with comprehensive error handling and retry logic
     */
    private void saveAllRanks() {
        YakPlayerRepository repository = YakPlayerManager.getInstance().getRepository();
        boolean isShuttingDown = this.isShuttingDown.get();

        int savedCount = 0;
        int failedCount = 0;

        for (Map.Entry<UUID, Rank> entry : rankMap.entrySet()) {
            try {
                YakPlayer player = playerManager.getPlayer(entry.getKey());
                if (player != null) {
                    String rankStr = Rank.getString(entry.getValue());
                    String currentRank = player.getRank();

                    // Only save if rank actually changed
                    if (!rankStr.equals(currentRank)) {
                        player.setRank(rankStr);

                        // Use synchronous saving during shutdown with retry logic
                        if (isShuttingDown) {
                            boolean saved = false;
                            for (int attempt = 1; attempt <= MAX_RANK_VALIDATION_ATTEMPTS; attempt++) {
                                try {
                                    repository.saveSync(player);
                                    saved = true;
                                    break;
                                } catch (Exception e) {
                                    if (attempt == MAX_RANK_VALIDATION_ATTEMPTS) {
                                        throw e;
                                    }
                                    Thread.sleep(100 * attempt); // Progressive delay
                                }
                            }

                            if (saved) {
                                savedCount++;
                                YakRealms.log("Synchronously saved rank " + rankStr + " for player " + player.getUsername());
                            }
                        } else {
                            playerManager.savePlayer(player);
                            savedCount++;
                        }
                    }
                }
            } catch (Exception e) {
                failedCount++;
                YakRealms.getInstance().getLogger().severe("Failed to save rank for player: " + e.getMessage());
            }
        }

        if (savedCount > 0) {
            YakRealms.log("Successfully saved " + savedCount + " player ranks");
        }
        if (failedCount > 0) {
            YakRealms.log("Failed to save " + failedCount + " player ranks");
        }
    }

    /**
     * Enhanced player join handler with comprehensive initialization
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        try {
            // Set default rank immediately to prevent null pointer exceptions
            if (!rankMap.containsKey(uuid)) {
                rankMap.put(uuid, Rank.DEFAULT);
            }

            // Track staff activity
            if (isStaff(player)) {
                onlineStaff.add(uuid);
                staffLastActivity.put(uuid, System.currentTimeMillis());

                // Notify other staff of important staff joining
                if (IMPORTANT_STAFF.contains(player.getName())) {
                    notifyStaff(ChatColor.GREEN + "Important staff member " +
                            ChatColor.GOLD + player.getName() + ChatColor.GREEN + " has joined!");
                }
            }

            // Schedule delayed initialization with enhanced error handling
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    initializePlayer(player);

                    // Send welcome message based on rank
                    sendRankWelcomeMessage(player);

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to initialize player " + player.getName() + " on join", e);

                    // Fallback initialization
                    try {
                        rankMap.put(uuid, Rank.DEFAULT);
                        setupPermissions(player);
                        YakRealms.log("Applied fallback initialization for " + player.getName());
                    } catch (Exception fallbackError) {
                        logger.log(Level.SEVERE, "Fallback initialization also failed for " + player.getName(), fallbackError);
                    }
                }
            }, 5L); // 5 tick delay (0.25 seconds)

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during player join for " + player.getName(), e);
        }
    }

    /**
     * Enhanced player quit handler with comprehensive cleanup and data synchronization
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            long startTime = System.currentTimeMillis();

            // Remove from staff tracking
            onlineStaff.remove(uuid);

            // Notify staff if important staff leaves
            if (IMPORTANT_STAFF.contains(player.getName()) && isStaff(player)) {
                notifyStaff(ChatColor.RED + "Important staff member " +
                        ChatColor.GOLD + player.getName() + ChatColor.RED + " has left!");
            }

            // Get player data before cleanup
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            if (yakPlayer != null) {
                // Enhanced rank synchronization
                if (rankMap.containsKey(uuid)) {
                    Rank memoryRank = rankMap.get(uuid);
                    String currentRankStr = yakPlayer.getRank();
                    String memoryRankStr = Rank.getString(memoryRank);

                    // Only save if ranks differ
                    if (!memoryRankStr.equals(currentRankStr)) {
                        YakRealms.log("Synchronizing rank on quit for " + player.getName() +
                                ": " + currentRankStr + " -> " + memoryRankStr);
                        yakPlayer.setRank(memoryRankStr);

                        // Force save with retry logic
                        savePlayerWithRetry(yakPlayer, 3);
                    } else {
                        YakRealms.log("Rank already synchronized for " + player.getName() + ": " + memoryRankStr);
                    }
                } else {
                    // Handle missing rank in memory cache
                    handleMissingRankInCache(player, yakPlayer);
                }
            } else {
                YakRealms.log("WARNING: No player data found for quitting player: " + player.getName());
            }

            // Clean up permission attachment with enhanced error handling
            cleanupPermissionAttachment(uuid, player);

            // Clean up caches (do this last)
            rankMap.remove(uuid);
            lastPermissionUpdate.remove(uuid);
            permissionDebugLog.remove(uuid);
            staffLastActivity.remove(uuid);

            long quitTime = System.currentTimeMillis() - startTime;
            recordOperationTime("player_quit", quitTime);

            YakRealms.log("Completed quit cleanup for player: " + player.getName() + " (" + quitTime + "ms)");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during player quit cleanup for " + player.getName(), e);

            // Ensure cleanup even if there's an error
            forceCleanupPlayer(uuid);
        }
    }

    /**
     * Enhanced player initialization with comprehensive validation
     */
    private void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        try {
            if (yakPlayer != null) {
                // Enhanced rank loading with validation
                initializePlayerRank(player, yakPlayer);

                // Enhanced chat tag loading with validation
                initializePlayerChatTag(player, yakPlayer, uuid);

                // Save any corrections made during initialization
                playerManager.savePlayer(yakPlayer);

            } else {
                // Handle missing player data
                handleMissingPlayerData(player, uuid);
            }

            // Set up permissions (always do this)
            setupPermissions(player);

            // Record successful initialization
            YakRealms.log("Successfully initialized player: " + player.getName() +
                    " with rank: " + rankMap.get(uuid));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize player " + player.getName(), e);
            throw e; // Re-throw to trigger fallback handling
        }
    }

    /**
     * Initialize player rank with enhanced validation and error recovery
     */
    private void initializePlayerRank(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();
        String storedRank = yakPlayer.getRank();

        try {
            if (storedRank != null && !storedRank.isEmpty()) {
                YakRealms.log("Loading rank for " + player.getName() + ": " + storedRank);

                // Validate and parse rank
                Rank rank = validateAndParseRank(storedRank, player.getName());
                rankMap.put(uuid, rank);

                YakRealms.log("Successfully loaded rank " + rank.name() + " for " + player.getName());
            } else {
                YakRealms.log("No rank found for " + player.getName() + ", setting DEFAULT");
                setDefaultRank(player, yakPlayer, uuid);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading rank for " + player.getName(), e);
            setDefaultRank(player, yakPlayer, uuid);
        }
    }

    /**
     * Initialize player chat tag with validation
     */
    private void initializePlayerChatTag(Player player, YakPlayer yakPlayer, UUID uuid) {
        try {
            String chatTagStr = yakPlayer.getChatTag();
            if (chatTagStr != null && !chatTagStr.isEmpty()) {
                ChatTag tag = ChatTag.valueOf(chatTagStr);
                ChatMechanics.getPlayerTags().put(uuid, tag);
            } else {
                ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
                yakPlayer.setChatTag(ChatTag.DEFAULT.name());
            }
        } catch (IllegalArgumentException e) {
            ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
            yakPlayer.setChatTag(ChatTag.DEFAULT.name());
            YakRealms.log("Invalid chat tag found for " + player.getName() + ", set to DEFAULT");
        }
    }

    /**
     * Validate and parse rank with enhanced error handling
     */
    private Rank validateAndParseRank(String rankStr, String playerName) {
        try {
            Rank rank = Rank.fromString(rankStr);

            // Additional validation - ensure rank is not corrupted
            if (rank == null) {
                throw new IllegalArgumentException("Rank.fromString returned null");
            }

            // Validate that the rank string conversion works both ways
            String reconverted = Rank.getString(rank);
            if (!rankStr.equalsIgnoreCase(reconverted)) {
                YakRealms.log("Rank conversion mismatch for " + playerName +
                        ": " + rankStr + " != " + reconverted + ", but accepting");
            }

            return rank;

        } catch (Exception e) {
            YakRealms.log("Invalid rank found for " + playerName + ": " + rankStr + ". Setting to DEFAULT.");
            throw new IllegalArgumentException("Invalid rank: " + rankStr, e);
        }
    }

    /**
     * Set default rank for player with proper saving
     */
    private void setDefaultRank(Player player, YakPlayer yakPlayer, UUID uuid) {
        rankMap.put(uuid, Rank.DEFAULT);
        yakPlayer.setRank(Rank.getString(Rank.DEFAULT));
        playerManager.savePlayer(yakPlayer);
        YakRealms.log("Set DEFAULT rank for " + player.getName());
    }

    /**
     * Handle missing player data scenario
     */
    private void handleMissingPlayerData(Player player, UUID uuid) {
        rankMap.put(uuid, Rank.DEFAULT);
        ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
        YakRealms.log("WARNING: Player data not loaded yet for " + player.getName() +
                " during rank initialization - using defaults");
    }

    /**
     * Enhanced permission setup with comprehensive rank-based permissions
     */
    private void setupPermissions(Player player) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Check cooldown to prevent spam
        Long lastUpdate = lastPermissionUpdate.get(uuid);
        if (lastUpdate != null && (currentTime - lastUpdate) < PERMISSION_UPDATE_COOLDOWN) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Remove existing attachment
            cleanupPermissionAttachment(uuid, player);

            // Create new attachment
            PermissionAttachment attachment = player.addAttachment(YakRealms.getInstance());
            permissionMap.put(uuid, attachment);

            // Get player's rank
            Rank rank = rankMap.getOrDefault(uuid, Rank.DEFAULT);

            // Apply permissions based on rank hierarchy
            applyBasicPlayerPermissions(attachment);

            if (isDonator(player)) {
                applyDonatorPermissions(attachment, rank);
            }

            if (isStaff(player)) {
                applyStaffPermissions(attachment, rank);
            }

            if (isBuilder(rank)) {
                applyBuilderPermissions(attachment);
            }

            // Record update
            lastPermissionUpdate.put(uuid, currentTime);
            permissionUpdates.incrementAndGet();

            long setupTime = System.currentTimeMillis() - startTime;
            recordOperationTime("permission_setup", setupTime);

            // Debug logging for important operations
            if (setupTime > 50) { // Log if it takes more than 50ms
                YakRealms.log("Slow permission setup for " + player.getName() +
                        " (" + setupTime + "ms) with rank " + rank.name());
            }

            permissionDebugLog.put(uuid, "Rank: " + rank.name() + ", Setup time: " + setupTime + "ms");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to setup permissions for " + player.getName(), e);
        }
    }

    /**
     * Apply basic permissions that all players should have
     */
    private void applyBasicPlayerPermissions(PermissionAttachment attachment) {
        // Core player permissions
        attachment.setPermission("yakrealms.player", true);

        // Basic functionality - THIS INCLUDES GLOBAL CHAT
        attachment.setPermission("yakrealms.player.buddy", true);
        attachment.setPermission("yakrealms.player.msg", true);
        attachment.setPermission("yakrealms.player.reply", true);
        attachment.setPermission("yakrealms.player.global", true);  // CRITICAL: Global chat permission
        attachment.setPermission("yakrealms.player.party", true);
        attachment.setPermission("yakrealms.player.mount", true);
        attachment.setPermission("yakrealms.player.toggles", true);
        attachment.setPermission("yakrealms.player.journal", true);
        attachment.setPermission("yakrealms.player.scroll", true);
        attachment.setPermission("yakrealms.player.speedfish", true);
        attachment.setPermission("yakrealms.player.orb", true);
        attachment.setPermission("yakrealms.player.teleportbook", true);
        attachment.setPermission("yakrealms.player.trail", true);
        attachment.setPermission("yakrealms.player.chattag", true);
        attachment.setPermission("yakrealms.player.lootbuff", true);
        attachment.setPermission("yakrealms.player.mobinfo", true);
        attachment.setPermission("yakrealms.player.lootchest.info", true);


        // Market permissions
        attachment.setPermission("yakrealms.market.use", true);
        attachment.setPermission("yakrealms.market.sell", true);
        attachment.setPermission("yakrealms.market.buy", true);

    }

    /**
     * Apply donator-specific permissions
     */
    private void applyDonatorPermissions(PermissionAttachment attachment, Rank rank) {
        attachment.setPermission("yakrealms.donator", true);
        attachment.setPermission("yakrealms.vip", true);

        // Enhanced market permissions for donators
        attachment.setPermission("yakrealms.market.featured", true);

        // Enhanced crate permissions for donators
        attachment.setPermission("yakrealms.crate.preview", true);

        // Premium donator benefits
        if (isPremiumDonator(rank)) {
            attachment.setPermission("yakrealms.premium", true);
            attachment.setPermission("yakrealms.bypass.cooldowns", true);
        }
    }

    /**
     * Apply staff-specific permissions with hierarchy
     */
    private void applyStaffPermissions(PermissionAttachment attachment, Rank rank) {
        attachment.setPermission("yakrealms.staff", true);

        // Basic staff permissions
        attachment.setPermission("yakrealms.staff.kick", true);
        attachment.setPermission("yakrealms.staff.ban", true);
        attachment.setPermission("yakrealms.staff.unban", true);
        attachment.setPermission("yakrealms.staff.mute", true);
        attachment.setPermission("yakrealms.staff.unmute", true);
        attachment.setPermission("yakrealms.staff.vanish", true);
        attachment.setPermission("yakrealms.staff.chat", true);

        // Staff setrank permissions (limited)
        attachment.setPermission("yakrealms.setrank.premium", true);

        // Staff notifications
        attachment.setPermission("yakrealms.notify.admin", true);
        attachment.setPermission("yakrealms.notify.market", true);
        attachment.setPermission("yakrealms.notify.economy", true);
        attachment.setPermission("yakrealms.notify.lootchest", true);

        // Moderator permissions
        if (isModeratorOrHigher(rank)) {
            attachment.setPermission("yakrealms.moderator", true);
            attachment.setPermission("yakrealms.mod", true);

            // Enhanced staff permissions
            attachment.setPermission("yakrealms.setrank.staff", true);
            attachment.setPermission("yakrealms.admin.invsee", true);
            attachment.setPermission("yakrealms.admin.alignment", true);

            // Some admin permissions for mods
            attachment.setPermission("yakrealms.admin.market", true);
            attachment.setPermission("yakrealms.lootchest.admin", true);
            attachment.setPermission("yakrealms.crate.give", true);
        }

        // Admin permissions
        if (isAdminOrHigher(rank)) {
            applyAdminPermissions(attachment);
        }

        // Developer permissions
        if (isDeveloper(rank)) {
            attachment.setPermission("yakrealms.developer", true);
            attachment.setPermission("yakrealms.setrank.dev", true);
            attachment.setPermission("yakrealms.*", true);
        }
    }

    /**
     * Apply admin-specific permissions
     */
    private void applyAdminPermissions(PermissionAttachment attachment) {
        attachment.setPermission("yakrealms.admin", true);

        // Full admin permissions
        attachment.setPermission("yakrealms.admin.economy", true);
        attachment.setPermission("yakrealms.admin.market", true);
        attachment.setPermission("yakrealms.admin.mobs", true);
        attachment.setPermission("yakrealms.admin.items", true);
        attachment.setPermission("yakrealms.admin.teleport", true);
        attachment.setPermission("yakrealms.admin.nodemap", true);
        attachment.setPermission("yakrealms.admin.vendor", true);
        attachment.setPermission("yakrealms.admin.spawner", true);
        attachment.setPermission("yakrealms.admin.spawnmob", true);
        attachment.setPermission("yakrealms.admin.togglespawners", true);
        attachment.setPermission("yakrealms.admin.boss", true);
        attachment.setPermission("yakrealms.admin.droprate", true);
        attachment.setPermission("yakrealms.admin.elitedrop", true);
        attachment.setPermission("yakrealms.admin.item", true);
        attachment.setPermission("yakrealms.admin.lootchest", true);
        attachment.setPermission("yakrealms.admin.menu", true);

        // Full crate admin
        attachment.setPermission("yakrealms.crate.admin", true);
        attachment.setPermission("yakrealms.crate.reload", true);
        attachment.setPermission("yakrealms.crate.cleanup", true);
        attachment.setPermission("yakrealms.crate.test", true);

        // Full loot chest admin
        attachment.setPermission("yakrealms.lootchest.create", true);
        attachment.setPermission("yakrealms.lootchest.remove", true);
        attachment.setPermission("yakrealms.lootchest.carepackage", true);
        attachment.setPermission("yakrealms.lootchest.special", true);
        attachment.setPermission("yakrealms.lootchest.reload", true);
        attachment.setPermission("yakrealms.lootchest.cleanup", true);

        // Enhanced setrank permissions
        attachment.setPermission("yakrealms.setrank.mod", true);
        attachment.setPermission("yakrealms.setrank.admin", true);

        // Bypass permissions
        attachment.setPermission("yakrealms.bypass.cooldowns", true);
        attachment.setPermission("yakrealms.bypass.limits", true);
        attachment.setPermission("yakrealms.bypass.costs", true);
    }

    /**
     * Apply builder-specific permissions
     */
    private void applyBuilderPermissions(PermissionAttachment attachment) {
        attachment.setPermission("yakrealms.builder", true);
        attachment.setPermission("yakrealms.admin.nodemap", true);
        attachment.setPermission("yakrealms.admin.lootchest", true);
        attachment.setPermission("yakrealms.lootchest.create", true);
        attachment.setPermission("yakrealms.lootchest.remove", true);
    }

    /**
     * Enhanced rank checking methods with null safety
     */
    private boolean isPremiumDonator(Rank rank) {
        if (rank == null) return false;
        switch (rank) {
            case SUB2:
            case SUB3:
            case SUPPORTER:
            case YOUTUBER:
            case QUALITY:
            case BUILDER:
            case PMOD:
            case GM:
            case MANAGER:
            case DEV:
                return true;
            default:
                return false;
        }
    }

    private boolean isModeratorOrHigher(Rank rank) {
        if (rank == null) return false;
        switch (rank) {
            case PMOD:
            case GM:
            case MANAGER:
            case DEV:
                return true;
            default:
                return false;
        }
    }

    private boolean isAdminOrHigher(Rank rank) {
        if (rank == null) return false;
        switch (rank) {
            case GM:
            case MANAGER:
            case DEV:
                return true;
            default:
                return false;
        }
    }

    private boolean isDeveloper(Rank rank) {
        return rank == Rank.DEV;
    }

    private boolean isBuilder(Rank rank) {
        return rank == Rank.BUILDER;
    }

    /**
     * Enhanced utility methods for better functionality
     */

    /**
     * Check if a player has god mode disabled
     */
    public static boolean isGodModeDisabled(Player player) {
        return player.hasMetadata("disableGodMode") || hasTemporaryTag(player, "togglegm");
    }

    private static boolean hasTemporaryTag(Player player, String tag) {
        return player.hasMetadata(tag);
    }

    /**
     * Enhanced getRank method with comprehensive error handling and caching
     */
    public static Rank getRank(Player player) {
        if (player == null) return Rank.DEFAULT;

        UUID uuid = player.getUniqueId();

        // First check memory cache
        if (rankMap.containsKey(uuid)) {
            return rankMap.get(uuid);
        }

        // Try to get from YakPlayer data with enhanced error handling
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer == null) {
                // Player data not loaded yet, cache DEFAULT temporarily
                rankMap.put(uuid, Rank.DEFAULT);
                return Rank.DEFAULT;
            }

            String rankStr = yakPlayer.getRank();
            if (rankStr == null || rankStr.isEmpty()) {
                return handleNullRank(player, yakPlayer, uuid);
            }

            // Parse and validate rank
            Rank rank = parseAndValidateRank(rankStr, player, yakPlayer, uuid);
            return rank;

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().log(Level.WARNING,
                    "Error getting rank for " + player.getName(), e);

            // Fallback to default
            rankMap.put(uuid, Rank.DEFAULT);
            return Rank.DEFAULT;
        }
    }

    private static Rank handleNullRank(Player player, YakPlayer yakPlayer, UUID uuid) {
        rankMap.put(uuid, Rank.DEFAULT);
        yakPlayer.setRank("default");

        YakPlayerManager.getInstance().savePlayer(yakPlayer);
        YakRealms.log("Set default rank for player with null rank: " + player.getName());

        return Rank.DEFAULT;
    }

    private static Rank parseAndValidateRank(String rankStr, Player player, YakPlayer yakPlayer, UUID uuid) {
        try {
            Rank rank = Rank.fromString(rankStr);

            // Cache the result
            rankMap.put(uuid, rank);
            return rank;

        } catch (IllegalArgumentException e) {
            YakRealms.log("Invalid rank found for " + player.getName() + ": " + rankStr + ". Setting to DEFAULT.");

            // Set and cache default rank
            rankMap.put(uuid, Rank.DEFAULT);
            yakPlayer.setRank("default");

            // Save the correction asynchronously
            YakPlayerManager.getInstance().savePlayer(yakPlayer);

            return Rank.DEFAULT;
        }
    }

    /**
     * Enhanced setRank method with immediate permission updates and validation
     */
    public void setRank(Player player, Rank rank) {
        if (player == null || rank == null) {
            logger.warning("Attempted to set null rank or for null player");
            return;
        }

        UUID uuid = player.getUniqueId();
        Rank oldRank = rankMap.get(uuid);

        try {
            // Validate rank change permissions (if needed)
            if (!validateRankChange(oldRank, rank)) {
                logger.warning("Invalid rank change attempted: " + oldRank + " -> " + rank);
                return;
            }

            // Update memory cache immediately
            rankMap.put(uuid, rank);

            // Update permissions immediately on the main thread
            setupPermissions(player);

            // Update staff tracking
            updateStaffTracking(player, rank);

            // Save to player data asynchronously with enhanced error handling
            updateAndSaveRank(uuid, rank, () -> {
                // Post-rank-change operations
                handlePostRankChange(player, rank);

                String oldRankName = oldRank != null ? oldRank.name() : "null";
                YakRealms.log("Successfully updated rank for " + player.getName() + ": " +
                        oldRankName + " -> " + rank.name());

                // Send confirmation message to player
                sendRankChangeConfirmation(player, rank);
            });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to set rank for " + player.getName(), e);

            // Rollback on error
            if (oldRank != null) {
                rankMap.put(uuid, oldRank);
                setupPermissions(player);
            }
        }
    }

    /**
     * Enhanced updateAndSaveRank with better error handling and retry logic
     */
    public void updateAndSaveRank(UUID uuid, Rank rank, Runnable callback) {
        // Update the memory cache immediately
        rankMap.put(uuid, rank);

        // Update player data asynchronously with retry logic
        CompletableFuture<Boolean> future = playerManager.withPlayer(uuid, yakPlayer -> {
            String rankStr = Rank.getString(rank);
            String oldRank = yakPlayer.getRank();

            if (!rankStr.equals(oldRank)) {
                YakRealms.log("Updating rank for " + yakPlayer.getUsername() + ": " + oldRank + " -> " + rankStr);
                yakPlayer.setRank(rankStr);
            }
        }, true);

        future.thenAccept(success -> {
            if (!success) {
                YakRealms.log("Failed to update rank in database for " + uuid);

                // Retry once after a short delay
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    updateAndSaveRank(uuid, rank, null); // Don't double-call callback
                }, 20L); // 1 second delay
            }

            // Run callback if provided
            if (callback != null) {
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), callback);
            }
        }).exceptionally(throwable -> {
            logger.log(Level.SEVERE, "Exception during rank update for " + uuid, throwable);
            return null;
        });
    }

    /**
     * Validate if a rank change is allowed (can be overridden for custom logic)
     */
    private boolean validateRankChange(Rank oldRank, Rank newRank) {
        // Basic validation - can be enhanced based on business rules
        return newRank != null;
    }

    /**
     * Update staff tracking when rank changes
     */
    private void updateStaffTracking(Player player, Rank rank) {
        UUID uuid = player.getUniqueId();

        if (isStaff(player)) {
            onlineStaff.add(uuid);
            staffLastActivity.put(uuid, System.currentTimeMillis());
        } else {
            onlineStaff.remove(uuid);
            staffLastActivity.remove(uuid);
        }
    }

    /**
     * Handle post-rank-change operations
     */
    private void handlePostRankChange(Player player, Rank rank) {
        // If this is a donator rank, unlock all chat tags
        if (isDonator(player)) {
            unlockAllChatTags(player);
        }

        // Special handling for staff promotions
        if (isStaff(player)) {
            notifyStaff(ChatColor.GREEN + player.getName() + " has been promoted to " +
                    ChatColor.translateAlternateColorCodes('&', rank.getTag()) +
                    ChatColor.RESET + ChatColor.GREEN + " " + rank.name() + "!");
        }
    }

    /**
     * Send rank change confirmation to player
     */
    private void sendRankChangeConfirmation(Player player, Rank rank) {
        player.sendMessage(ChatColor.GREEN + "Your rank has been updated to: " +
                ChatColor.translateAlternateColorCodes('&', rank.getTag()) +
                ChatColor.RESET + ChatColor.GREEN + " " + rank.name());

        player.sendMessage(ChatColor.YELLOW + "Your permissions have been updated!");

        // Send additional information based on rank
        sendRankSpecificInfo(player, rank);
    }

    /**
     * Send rank-specific information to player
     */
    private void sendRankSpecificInfo(Player player, Rank rank) {
        if (isDonator(player)) {
            player.sendMessage(ChatColor.GOLD + "Thank you for supporting YakRealms! You now have access to donator features.");
        }

        if (isStaff(player)) {
            player.sendMessage(ChatColor.BLUE + "Welcome to the staff team! Use /staffchat to communicate with other staff.");
        }
    }

    /**
     * Send welcome message based on rank
     */
    private void sendRankWelcomeMessage(Player player) {
        Rank rank = getRank(player);

        if (rank != Rank.DEFAULT) {
            player.sendMessage(ChatColor.GREEN + "Welcome back, " +
                    ChatColor.translateAlternateColorCodes('&', rank.getTag()) +
                    ChatColor.RESET + ChatColor.GREEN + " " + player.getName() + "!");
        }
    }

    /**
     * Enhanced utility methods for staff management
     */

    /**
     * Refresh a player's permissions
     */
    public void refreshPermissions(Player player) {
        if (player != null && player.isOnline()) {
            setupPermissions(player);
            YakRealms.log("Refreshed permissions for " + player.getName());
        }
    }

    /**
     * Get performance statistics
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPermissionUpdates", permissionUpdates.get());
        stats.put("onlineStaffCount", onlineStaff.size());
        stats.put("cachedRanks", rankMap.size());
        stats.put("operationTimes", new HashMap<>(operationTimes));
        return stats;
    }

    /**
     * Enhanced staff checking methods
     */
    public static boolean isStaff(Player player) {
        if (player == null) return false;
        if (player.isOp()) return true;

        Rank rank = rankMap.getOrDefault(player.getUniqueId(), Rank.DEFAULT);
        switch (rank) {
            case QUALITY:
            case BUILDER:
            case PMOD:
            case GM:
            case MANAGER:
            case DEV:
                return true;
            default:
                return false;
        }
    }

    public static boolean isDonator(Player player) {
        if (player == null) return false;

        Rank rank = rankMap.getOrDefault(player.getUniqueId(), Rank.DEFAULT);
        switch (rank) {
            case SUB:
            case SUB1:
            case SUB2:
            case SUB3:
            case SUPPORTER:
            case YOUTUBER:
            case QUALITY:
            case PMOD:
            case GM:
            case DEV:
            case MANAGER:
                return true;
            default:
                return false;
        }
    }

    public static List<String> getImportantStaff() {
        return new ArrayList<>(IMPORTANT_STAFF);
    }

    /**
     * Enhanced moderation methods with better error handling
     */

    public void banPlayer(UUID targetUuid, String reason, long durationSeconds, String banner) {
        playerManager.withPlayer(targetUuid, player -> {
            long expiryTime = durationSeconds > 0 ? System.currentTimeMillis() / 1000 + durationSeconds : 0;
            player.setBanned(true, reason, expiryTime);

            logger.info(banner + " banned " + player.getUsername() +
                    (durationSeconds > 0 ? " for " + formatDuration(durationSeconds) : " permanently") +
                    " for: " + reason);

            // Kick the player if they're online
            Player bukkitPlayer = Bukkit.getPlayer(targetUuid);
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                String message = "§cYou have been banned\n§fReason: §e" + reason;
                if (durationSeconds > 0) {
                    message += "\n§fExpires in: §e" + formatDuration(durationSeconds);
                } else {
                    message += "\n§fThis ban will not expire.";
                }

                String finalMessage = message;
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () ->
                        bukkitPlayer.kickPlayer(finalMessage));
            }

            // Notify staff
            notifyStaff(ChatColor.RED + banner + " banned " + player.getUsername() +
                    " for: " + reason);

        }, true);
    }

    public void unbanPlayer(UUID targetUuid, String unbanner) {
        playerManager.withPlayer(targetUuid, player -> {
            if (player.isBanned()) {
                player.setBanned(false, "", 0);
                logger.info(unbanner + " unbanned " + player.getUsername());

                // Notify staff
                notifyStaff(ChatColor.GREEN + unbanner + " unbanned " + player.getUsername());
            }
        }, true);
    }

    public void mutePlayer(Player player, int durationSeconds, String muter) {
        ChatMechanics.mutePlayer(player, durationSeconds);

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setMuteTime(durationSeconds);
            playerManager.savePlayer(yakPlayer);
        }

        logger.info(muter + " muted " + player.getName() + " for " + formatDuration(durationSeconds));

        // Notify staff
        notifyStaff(ChatColor.YELLOW + muter + " muted " + player.getName() +
                " for " + formatDuration(durationSeconds));
    }

    public void unmutePlayer(Player player, String unmuter) {
        ChatMechanics.unmutePlayer(player);

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setMuteTime(0);
            playerManager.savePlayer(yakPlayer);
        }

        logger.info(unmuter + " unmuted " + player.getName());

        // Notify staff
        notifyStaff(ChatColor.GREEN + unmuter + " unmuted " + player.getName());
    }

    /**
     * Enhanced helper methods
     */

    private void unlockAllChatTags(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            List<String> tags = new ArrayList<>();
            for (ChatTag tag : ChatTag.values()) {
                tags.add(tag.name());
            }
            yakPlayer.setUnlockedChatTags(tags);
            playerManager.savePlayer(yakPlayer);
        }
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        StringBuilder builder = new StringBuilder();

        if (days > 0) {
            builder.append(days).append(days == 1 ? " day" : " days");
            if (hours > 0 || minutes > 0) builder.append(", ");
        }

        if (hours > 0) {
            builder.append(hours).append(hours == 1 ? " hour" : " hours");
            if (minutes > 0) builder.append(", ");
        }

        if (minutes > 0 || (days == 0 && hours == 0)) {
            builder.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }

        return builder.toString();
    }

    /**
     * Enhanced cleanup and utility methods
     */

    private void cleanupPermissionAttachment(UUID uuid, Player player) {
        if (permissionMap.containsKey(uuid)) {
            try {
                PermissionAttachment attachment = permissionMap.get(uuid);
                if (attachment != null) {
                    player.removeAttachment(attachment);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error removing permission attachment for " + player.getName(), e);
            }
            permissionMap.remove(uuid);
        }
    }

    private void forceCleanupPlayer(UUID uuid) {
        permissionMap.remove(uuid);
        rankMap.remove(uuid);
        lastPermissionUpdate.remove(uuid);
        permissionDebugLog.remove(uuid);
        staffLastActivity.remove(uuid);
        onlineStaff.remove(uuid);
    }

    private void handleMissingRankInCache(Player player, YakPlayer yakPlayer) {
        YakRealms.log("WARNING: No rank in memory cache for quitting player: " + player.getName());

        String currentRankStr = yakPlayer.getRank();
        if (currentRankStr == null || currentRankStr.isEmpty()) {
            yakPlayer.setRank("default");
            savePlayerWithRetry(yakPlayer, 2);
            YakRealms.log("Set default rank for player with missing rank: " + player.getName());
        } else {
            try {
                Rank.fromString(currentRankStr); // Validate rank string
            } catch (IllegalArgumentException e) {
                YakRealms.log("Invalid rank detected on quit for " + player.getName() +
                        ": " + currentRankStr + ". Setting to default.");
                yakPlayer.setRank("default");
                savePlayerWithRetry(yakPlayer, 2);
            }
        }
    }

    private void savePlayerWithRetry(YakPlayer yakPlayer, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                playerManager.savePlayer(yakPlayer);
                return;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    logger.log(Level.SEVERE, "Failed to save player after " + maxRetries + " attempts", e);
                } else {
                    try {
                        Thread.sleep(100 * (i + 1)); // Progressive delay
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void recordOperationTime(String operation, long timeMs) {
        operationTimes.put(operation, timeMs);
    }

    private void notifyStaff(String message) {
        for (UUID staffUuid : onlineStaff) {
            Player staff = Bukkit.getPlayer(staffUuid);
            if (staff != null && staff.isOnline()) {
                staff.sendMessage(ChatColor.GRAY + "[STAFF] " + message);
            }
        }
    }

    /**
     * Performance monitoring methods
     */

    private void startPerformanceMonitoring() {
        performanceTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            // Log performance stats every 5 minutes
            if (permissionUpdates.get() % 100 == 0 && permissionUpdates.get() > 0) {
                logPerformanceStats();
            }
        }, 6000L, 6000L); // Every 5 minutes
    }

    private void startStaffActivityMonitoring() {
        staffActivityTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            // Update staff activity
            long currentTime = System.currentTimeMillis();
            for (UUID staffUuid : new HashSet<>(onlineStaff)) {
                Player staff = Bukkit.getPlayer(staffUuid);
                if (staff != null && staff.isOnline()) {
                    staffLastActivity.put(staffUuid, currentTime);
                } else {
                    onlineStaff.remove(staffUuid);
                    staffLastActivity.remove(staffUuid);
                }
            }
        }, 1200L, 1200L); // Every minute
    }

    private void logPerformanceStats() {
        YakRealms.log("=== ModerationMechanics Performance Stats ===");
        YakRealms.log("Total permission updates: " + permissionUpdates.get());
        YakRealms.log("Online staff count: " + onlineStaff.size());
        YakRealms.log("Cached ranks: " + rankMap.size());
        YakRealms.log("Active permission attachments: " + permissionMap.size());

        if (!operationTimes.isEmpty()) {
            YakRealms.log("Recent operation times:");
            operationTimes.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> YakRealms.log("  " + entry.getKey() + ": " + entry.getValue() + "ms"));
        }

        YakRealms.log("============================================");
    }
}
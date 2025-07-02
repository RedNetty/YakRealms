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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles moderation functionality including:
 * - Player ranks management
 * - Banning and muting
 * - Chat tag management
 */
public class ModerationMechanics implements Listener {

    private static ModerationMechanics instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Store player ranks in memory for quick access
    public static final Map<UUID, Rank> rankMap = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> permissionMap = new ConcurrentHashMap<>();

    // Important staff list
    private static final List<String> IMPORTANT_STAFF = Arrays.asList("Red");

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
     * Initialize the moderation mechanics
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Initialize ranks for online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayer(player);
        }

        YakRealms.log("ModerationMechanics has been enabled.");
    }

    /**
     * Clean up when disabling
     */
    public void onDisable() {
        // Remove all permission attachments
        for (UUID uuid : permissionMap.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.removeAttachment(permissionMap.get(uuid));
            }
        }

        // Save all ranks back to player data
        saveAllRanks();

        permissionMap.clear();
        rankMap.clear();

        YakRealms.log("ModerationMechanics has been disabled.");
    }

    private void saveAllRanks() {
        YakPlayerRepository repository = YakPlayerManager.getInstance().getRepository();
        boolean isShuttingDown = YakPlayerManager.getInstance().isShuttingDown();

        for (Map.Entry<UUID, Rank> entry : rankMap.entrySet()) {
            YakPlayer player = playerManager.getPlayer(entry.getKey());
            if (player != null) {
                String rankStr = Rank.getString(entry.getValue());
                player.setRank(rankStr);

                // Use synchronous saving during shutdown
                if (isShuttingDown) {
                    try {
                        repository.saveSync(player);
                        YakRealms.log("Synchronously saved rank " + rankStr + " for player " + player.getUsername());
                    } catch (Exception e) {
                        YakRealms.getInstance().getLogger().severe("Failed to save rank for " + player.getUsername() + ": " + e.getMessage());
                    }
                } else {
                    playerManager.savePlayer(player);
                }
            }
        }
    }

    /**
     * Handler for player join to setup ranks and permissions
     * Using MONITOR priority to ensure YakPlayerManager has loaded the player
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        // Set default rank immediately to prevent null pointer exceptions
        if (!rankMap.containsKey(uuid)) {
            rankMap.put(uuid, Rank.DEFAULT);
        }

        // Schedule delayed initialization to give YakPlayerManager time to load the player
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            initializePlayer(player);
        }, 5L); // 5 tick delay (0.25 seconds)
    }

    /**
     * Handler for player quit to clean up - FIXED to ensure rank synchronization
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        try {
            // Get player data before cleanup
            YakPlayer yakPlayer = playerManager.getPlayer(player);

            if (yakPlayer != null) {
                // Ensure rank is synchronized between memory and player data
                if (rankMap.containsKey(uuid)) {
                    Rank memoryRank = rankMap.get(uuid);
                    String currentRankStr = yakPlayer.getRank();
                    String memoryRankStr = Rank.getString(memoryRank);

                    // Only save if ranks differ
                    if (!memoryRankStr.equals(currentRankStr)) {
                        YakRealms.log("Synchronizing rank on quit for " + player.getName() +
                                ": " + currentRankStr + " -> " + memoryRankStr);
                        yakPlayer.setRank(memoryRankStr);

                        // Force save the updated rank
                        playerManager.savePlayer(yakPlayer);
                    } else {
                        YakRealms.log("Rank already synchronized for " + player.getName() + ": " + memoryRankStr);
                    }
                } else {
                    // No rank in memory cache - this shouldn't happen but let's handle it
                    YakRealms.log("WARNING: No rank in memory cache for quitting player: " + player.getName());

                    // Try to get rank from player data and verify it's valid
                    String currentRankStr = yakPlayer.getRank();
                    if (currentRankStr == null || currentRankStr.isEmpty()) {
                        yakPlayer.setRank("default");
                        playerManager.savePlayer(yakPlayer);
                        YakRealms.log("Set default rank for player with missing rank: " + player.getName());
                    } else {
                        try {
                            Rank.fromString(currentRankStr); // Validate rank string
                        } catch (IllegalArgumentException e) {
                            YakRealms.log("Invalid rank detected on quit for " + player.getName() +
                                    ": " + currentRankStr + ". Setting to default.");
                            yakPlayer.setRank("default");
                            playerManager.savePlayer(yakPlayer);
                        }
                    }
                }
            } else {
                YakRealms.log("WARNING: No player data found for quitting player: " + player.getName());
            }

            // Clean up permission attachment
            if (permissionMap.containsKey(uuid)) {
                try {
                    player.removeAttachment(permissionMap.get(uuid));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error removing permission attachment for " + player.getName(), e);
                }
                permissionMap.remove(uuid);
            }

            // Clean up rank cache (do this last)
            rankMap.remove(uuid);

            YakRealms.log("Completed quit cleanup for player: " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during player quit cleanup for " + player.getName(), e);

            // Ensure cleanup even if there's an error
            permissionMap.remove(uuid);
            rankMap.remove(uuid);
        }
    }

    /**
     * Initialize a player's rank and permissions
     *
     * @param player The player to initialize
     */
    private void initializePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer != null) {
            // Load rank
            try {
                String storedRank = yakPlayer.getRank();

                if (storedRank != null && !storedRank.isEmpty()) {
                    YakRealms.log("Loading rank for " + player.getName() + ": " + storedRank);

                    Rank rank = Rank.fromString(storedRank);
                    rankMap.put(uuid, rank);
                    YakRealms.log("Successfully loaded rank " + rank.name() + " for " + player.getName());
                } else {
                    YakRealms.log("No rank found for " + player.getName() + ", setting DEFAULT");
                    rankMap.put(uuid, Rank.DEFAULT);
                    yakPlayer.setRank(Rank.getString(Rank.DEFAULT));
                    playerManager.savePlayer(yakPlayer);
                }
            } catch (IllegalArgumentException e) {
                YakRealms.log("Invalid rank found for " + player.getName() + ", setting DEFAULT");
                rankMap.put(uuid, Rank.DEFAULT);
                yakPlayer.setRank(Rank.getString(Rank.DEFAULT));
                playerManager.savePlayer(yakPlayer);
            }

            // Load chat tag
            try {
                ChatTag tag = ChatTag.valueOf(yakPlayer.getChatTag());
                ChatMechanics.getPlayerTags().put(uuid, tag);
            } catch (IllegalArgumentException e) {
                ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);
                yakPlayer.setChatTag(ChatTag.DEFAULT.name());
            }

            // Save if any corrections were made
            playerManager.savePlayer(yakPlayer);
        } else {
            // Player data not found or not loaded yet
            rankMap.put(uuid, Rank.DEFAULT);
            ChatMechanics.getPlayerTags().put(uuid, ChatTag.DEFAULT);

            // Log warning
            YakRealms.log("WARNING: Player data not loaded yet for " + player.getName() + " during rank initialization");
        }

        // Set up permissions
        setupPermissions(player);
    }

    /**
     * Set up a player's permissions based on their rank
     *
     * @param player The player to set up
     */
    private void setupPermissions(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove existing attachment
        if (permissionMap.containsKey(uuid)) {
            player.removeAttachment(permissionMap.get(uuid));
            permissionMap.remove(uuid);
        }

        // Create new attachment
        PermissionAttachment attachment = player.addAttachment(YakRealms.getInstance());
        permissionMap.put(uuid, attachment);

        // Add standard permissions based on rank
        Rank rank = rankMap.getOrDefault(uuid, Rank.DEFAULT);

        // Basic permissions for all players
        attachment.setPermission("yakrealms.player", true);

        // Staff permissions
        if (isStaff(player)) {
            attachment.setPermission("yakrealms.staff", true);

            // Moderator permissions
            if (rank == Rank.PMOD || rank == Rank.GM || rank == Rank.MANAGER || rank == Rank.DEV) {
                attachment.setPermission("yakrealms.mod", true);
                attachment.setPermission("yakrealms.staff.chat", true);
            }

            // Admin permissions
            if (rank == Rank.GM || rank == Rank.MANAGER || rank == Rank.DEV) {
                attachment.setPermission("yakrealms.admin", true);
            }
        }

        // Donator permissions
        if (isDonator(player)) {
            attachment.setPermission("yakrealms.donator", true);
        }
    }

    /**
     * Check if a player has god mode disabled
     * (Used by CombatMechanics to determine if op players should take damage)
     *
     * @param player The player to check
     * @return true if god mode is disabled
     */
    public static boolean isGodModeDisabled(Player player) {
        // ToggleGM command would normally store this information
        // For now, implementing a simple version that checks for a specific tag
        return player.hasMetadata("disableGodMode") ||
                hasTemporaryTag(player, "togglegm");
    }

    /**
     * Check if a player has a temporary tag
     *
     * @param player The player to check
     * @param tag    The tag to check for
     * @return true if the player has the tag
     */
    private static boolean hasTemporaryTag(Player player, String tag) {
        // This implements a simple check for temporary tags
        // You can expand this to use your own player tag system
        return player.hasMetadata(tag);
    }
    /**
     * Get a player's rank - FIXED to handle null YakPlayer safely and cache results
     *
     * @param player The player to check
     * @return The player's rank
     */
    public static Rank getRank(Player player) {
        if (player == null) return Rank.DEFAULT;

        UUID uuid = player.getUniqueId();

        // First check memory cache
        if (rankMap.containsKey(uuid)) {
            return rankMap.get(uuid);
        }

        // Try to get from YakPlayer data
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            // Player data not loaded yet, cache DEFAULT temporarily
            rankMap.put(uuid, Rank.DEFAULT);
            return Rank.DEFAULT;
        }

        String rankStr = yakPlayer.getRank();
        if (rankStr == null || rankStr.isEmpty()) {
            // Set and cache default rank
            rankMap.put(uuid, Rank.DEFAULT);
            yakPlayer.setRank("default");

            // Save the correction asynchronously
            YakPlayerManager.getInstance().savePlayer(yakPlayer);
            YakRealms.log("Set default rank for player with null rank: " + player.getName());

            return Rank.DEFAULT;
        }

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
     * Enhanced version of setRank with better error handling and validation
     */
    public void setRank(Player player, Rank rank) {
        if (player == null || rank == null) {
            logger.warning("Attempted to set null rank or for null player");
            return;
        }

        UUID uuid = player.getUniqueId();
        Rank oldRank = rankMap.get(uuid);

        // Update memory cache immediately
        rankMap.put(uuid, rank);

        // Update permissions on the main thread
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> setupPermissions(player));

        // Save to player data asynchronously with better error handling
        updateAndSaveRank(uuid, rank, () -> {
            // If this is a donator rank, unlock all chat tags
            if (isDonator(player)) {
                unlockAllChatTags(player);
            }

            String oldRankName = oldRank != null ? oldRank.name() : "null";
            YakRealms.log("Successfully updated rank for " + player.getName() + ": " +
                    oldRankName + " -> " + rank.name());

            // Send confirmation message to player
            player.sendMessage(ChatColor.GREEN + "Your rank has been updated to: " +
                    ChatColor.translateAlternateColorCodes('&', rank.getTag()) +
                    ChatColor.RESET + ChatColor.GREEN + " " + rank.name());
        });
    }

    /**
     * Update and save a player's rank asynchronously - FIXED to not block the main thread
     *
     * @param uuid     The player UUID
     * @param rank     The new rank
     * @param callback Optional callback to run when complete
     */
    public void updateAndSaveRank(UUID uuid, Rank rank, Runnable callback) {
        // Update the memory cache immediately
        rankMap.put(uuid, rank);

        // Update player data asynchronously
        playerManager.withPlayer(uuid, yakPlayer -> {
            String rankStr = Rank.getString(rank);
            String oldRank = yakPlayer.getRank();

            if (!rankStr.equals(oldRank)) {
                YakRealms.log("Updating rank for " + yakPlayer.getUsername() + ": " + oldRank + " -> " + rankStr);
                yakPlayer.setRank(rankStr);
            }
        }, true).thenAccept(success -> {
            if (!success) {
                YakRealms.log("Failed to update rank in database for " + uuid);
            }

            // Run callback if provided
            if (callback != null) {
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), callback);
            }
        });
    }



    /**
     * Unlock all chat tags for a player
     *
     * @param player The player to update
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

    /**
     * Check if a player is staff
     *
     * @param player The player to check
     * @return true if the player is staff
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

    /**
     * Check if a player is a donator
     *
     * @param player The player to check
     * @return true if the player is a donator
     */
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

    /**
     * Get a list of important staff members
     *
     * @return List of important staff member names
     */
    public static List<String> getImportantStaff() {
        return IMPORTANT_STAFF;
    }

    /**
     * Ban a player
     *
     * @param targetUuid      The UUID of the player to ban
     * @param reason          The reason for the ban
     * @param durationSeconds The duration of the ban in seconds, 0 for permanent
     * @param banner          The name of the staff member who issued the ban
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

                // Use delayed kick to avoid concurrent modification
                String finalMessage = message;
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () ->
                        bukkitPlayer.kickPlayer(finalMessage));
            }
        }, true);
    }

    /**
     * Unban a player
     *
     * @param targetUuid The UUID of the player to unban
     * @param unbanner   The name of the staff member who is unbanning
     */
    public void unbanPlayer(UUID targetUuid, String unbanner) {
        playerManager.withPlayer(targetUuid, player -> {
            if (player.isBanned()) {
                player.setBanned(false, "", 0);
                logger.info(unbanner + " unbanned " + player.getUsername());
            }
        }, true);
    }

    /**
     * Mute a player
     *
     * @param player          The player to mute
     * @param durationSeconds The duration of the mute in seconds
     * @param muter           The name of the staff member who issued the mute
     */
    public void mutePlayer(Player player, int durationSeconds, String muter) {
        // Use ChatMechanics to mute the player
        ChatMechanics.mutePlayer(player, durationSeconds);

        // Save the mute in the YakPlayer data
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setMuteTime(durationSeconds);
            playerManager.savePlayer(yakPlayer);
        }

        // Log the mute
        logger.info(muter + " muted " + player.getName() + " for " + formatDuration(durationSeconds));

        // We don't need to notify the player here as ChatMechanics.mutePlayer already does that
    }

    /**
     * Unmute a player
     *
     * @param player  The player to unmute
     * @param unmuter The name of the staff member who is unmuting
     */
    public void unmutePlayer(Player player, String unmuter) {
        // Use ChatMechanics to unmute the player
        ChatMechanics.unmutePlayer(player);

        // Update YakPlayer data
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setMuteTime(0);
            playerManager.savePlayer(yakPlayer);
        }

        // Log the unmute
        logger.info(unmuter + " unmuted " + player.getName());

        // We don't need to notify the player here as ChatMechanics.unmutePlayer already does that
    }

    /**
     * Format a duration in seconds to a human-readable string
     *
     * @param seconds The duration in seconds
     * @return A formatted string like "2 days, 5 hours, 30 minutes"
     */
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
}
package com.rednetty.server.utils.messaging;

import com.rednetty.server.utils.sounds.SoundUtil;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Unified messaging utility providing consistent messaging patterns across all systems.
 */
public class MessageUtil {

    // Message Prefixes

    public static final String ERROR_PREFIX = ChatColor.RED + "❌ ";
    public static final String SUCCESS_PREFIX = ChatColor.GREEN + "✓ ";
    public static final String WARNING_PREFIX = ChatColor.YELLOW + "⚠ ";
    public static final String INFO_PREFIX = ChatColor.BLUE + "ℹ ";
    public static final String TIP_PREFIX = ChatColor.GRAY + "💡 ";
    public static final String PARTY_PREFIX = ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + "> ";
    public static final String STAFF_PREFIX = ChatColor.RED + "[STAFF] " + ChatColor.RESET;
    public static final String COMBAT_PREFIX = ChatColor.DARK_RED + "[COMBAT] " + ChatColor.RESET;

    // Utility Methods
    public static String formatDuration(long seconds) {
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
     * Send error message with sound feedback
     * @param player The player to send message to
     * @param message The error message content
     */
    public static void sendError(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            player.sendMessage(ERROR_PREFIX + message);
            SoundUtil.playError(player);
        } catch (Exception e) {
            // Fallback without sound if error occurs
            player.sendMessage(ERROR_PREFIX + message);
        }
    }

    /**
     * Send success message with sound feedback
     * @param player The player to send message to
     * @param message The success message content
     */
    public static void sendSuccess(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            player.sendMessage(SUCCESS_PREFIX + message);
            SoundUtil.playSuccess(player);
        } catch (Exception e) {
            // Fallback without sound if error occurs
            player.sendMessage(SUCCESS_PREFIX + message);
        }
    }

    /**
     * Send warning message with sound feedback
     * @param player The player to send message to
     * @param message The warning message content
     */
    public static void sendWarning(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            player.sendMessage(WARNING_PREFIX + message);
            SoundUtil.playWarning(player);
        } catch (Exception e) {
            // Fallback without sound if error occurs
            player.sendMessage(WARNING_PREFIX + message);
        }
    }

    /**
     * Send info message with sound feedback
     * @param player The player to send message to
     * @param message The info message content
     */
    public static void sendInfo(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        try {
            player.sendMessage(INFO_PREFIX + message);
            SoundUtil.playNotification(player);
        } catch (Exception e) {
            // Fallback without sound if error occurs
            player.sendMessage(INFO_PREFIX + message);
        }
    }

    /**
     * Send tip message without sound (for helpful suggestions)
     * @param player The player to send message to
     * @param message The tip message content
     */
    public static void sendTip(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        player.sendMessage(TIP_PREFIX + ChatColor.ITALIC + message);
    }

    // ========================================
    // SPECIALIZED MESSAGE TYPES
    // ========================================

    /**
     * Send command usage error with helpful context
     * @param player The player to send message to
     * @param command The correct command syntax
     * @param description Brief description of what the command does
     */
    public static void sendCommandUsage(Player player, String command, String description) {
        if (player == null || !player.isOnline()) return;

        sendError(player, ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + command);
        if (description != null && !description.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + description);
        }
    }

    /**
     * Send command usage with multiple examples
     * @param player The player to send message to
     * @param commands Array of valid command syntaxes
     * @param description Brief description of what the command does
     */
    public static void sendCommandUsageMultiple(Player player, String[] commands, String description) {
        if (player == null || !player.isOnline()) return;

        sendError(player, ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage:");
        for (String command : commands) {
            player.sendMessage(ChatColor.GRAY + "  " + ChatColor.YELLOW + command);
        }
        if (description != null && !description.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + description);
        }
    }

    /**
     * Send smart context help when commands are used incorrectly
     * @param player The player to send message to
     * @param attemptedCommand What the player tried to do
     * @param suggestions Array of suggested alternatives
     */
    public static void sendSmartHelp(Player player, String attemptedCommand, String[] suggestions) {
        if (player == null || !player.isOnline()) return;

        sendError(player, "Command failed: " + ChatColor.GRAY + attemptedCommand);

        if (suggestions != null && suggestions.length > 0) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "💡 Try instead:");
            for (String suggestion : suggestions) {
                player.sendMessage(ChatColor.GRAY + "  " + ChatColor.YELLOW + suggestion);
            }
        }
    }

    /**
     * Send no permission message with consistent formatting
     * @param player The player to send message to
     * @param action What action was denied
     */
    public static void sendNoPermission(Player player, String action) {
        if (player == null || !player.isOnline()) return;

        sendError(player, "You don't have permission to " + action + ".");
        SoundUtil.playError(player);
    }

    /**
     * Send confirmation request for destructive actions
     * @param player The player to send message to
     * @param action The destructive action being confirmed
     * @param confirmCommand The command to type to confirm
     */
    public static void sendConfirmationRequest(Player player, String action, String confirmCommand) {
        if (player == null || !player.isOnline()) return;

        sendWarning(player, "Are you sure you want to " + action + "?");
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + confirmCommand + ChatColor.GRAY + " to confirm.");
        player.sendMessage(ChatColor.GRAY + "This action will expire in 30 seconds.");
    }

    // ========================================
    // PARTY MESSAGING
    // ========================================

    /**
     * Send party-specific error message
     * @param player The player to send message to
     * @param message The error message content
     */
    public static void sendPartyError(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(PARTY_PREFIX + ERROR_PREFIX + message);
        SoundUtil.playError(player);
    }

    /**
     * Send party-specific success message
     * @param player The player to send message to
     * @param message The success message content
     */
    public static void sendPartySuccess(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(PARTY_PREFIX + SUCCESS_PREFIX + message);
        SoundUtil.playSuccess(player);
    }

    /**
     * Send party notification to player
     * @param player The player to send message to
     * @param message The notification content
     */
    public static void sendPartyNotification(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(PARTY_PREFIX + ChatColor.YELLOW + message);
        SoundUtil.playNotification(player);
    }

    // ========================================
    // COMBAT MESSAGING
    // ========================================

    /**
     * Send combat-related error message
     * @param player The player to send message to
     * @param message The error message content
     */
    public static void sendCombatError(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(COMBAT_PREFIX + ERROR_PREFIX + message);
        SoundUtil.playError(player);
    }

    /**
     * Send combat warning message
     * @param player The player to send message to
     * @param message The warning message content
     */
    public static void sendCombatWarning(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(COMBAT_PREFIX + WARNING_PREFIX + message);
        SoundUtil.playWarning(player);
    }

    // ========================================
    // ECONOMY/VENDOR MESSAGING
    // ========================================

    /**
     * Send economy transaction success message
     * @param player The player to send message to
     * @param message The transaction details
     */
    public static void sendEconomySuccess(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        sendSuccess(player, message);
        // Additional economy-specific sound
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        } catch (Exception e) {
            // Ignore sound errors
        }
    }

    /**
     * Send economy transaction error message
     * @param player The player to send message to
     * @param message The error details
     */
    public static void sendEconomyError(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        sendError(player, message);
        // Additional emphasis for money-related errors
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
        } catch (Exception e) {
            // Ignore sound errors
        }
    }

    // ========================================
    // BROADCAST MESSAGING
    // ========================================

    /**
     * Send system-wide announcement
     * @param message The announcement message
     */
    public static void sendSystemAnnouncement(String message) {
        String formattedMessage = ChatColor.GOLD + "[SYSTEM] " + ChatColor.YELLOW + message;

        try {
            org.bukkit.Bukkit.getOnlinePlayers().forEach(player -> {
                player.sendMessage(formattedMessage);
                SoundUtil.playNotification(player);
            });
        } catch (Exception e) {
            // Log error but don't crash
            org.bukkit.Bukkit.getLogger().warning("Error sending system announcement: " + e.getMessage());
        }
    }

    /**
     * Send staff announcement to staff members only
     * @param message The staff announcement message
     */
    public static void sendStaffAnnouncement(String message) {
        String formattedMessage = STAFF_PREFIX + ChatColor.YELLOW + message;

        try {
            org.bukkit.Bukkit.getOnlinePlayers().forEach(player -> {
                if (player.hasPermission("yakrealms.staff") || player.hasPermission("yakrealms.admin")) {
                    player.sendMessage(formattedMessage);
                    SoundUtil.playNotification(player);
                }
            });
        } catch (Exception e) {
            // Log error but don't crash
            org.bukkit.Bukkit.getLogger().warning("Error sending staff announcement: " + e.getMessage());
        }
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Send blank line for spacing in chat
     * @param player The player to send blank line to
     */
    public static void sendBlankLine(Player player) {
        if (player == null || !player.isOnline()) return;
        player.sendMessage("");
    }

    /**
     * Send multiple blank lines for chat clearing
     * @param player The player to send blank lines to
     * @param count Number of blank lines to send
     */
    public static void sendBlankLines(Player player, int count) {
        if (player == null || !player.isOnline()) return;

        for (int i = 0; i < count; i++) {
            player.sendMessage("");
        }
    }

    /**
     * Send centered message with padding
     * @param player The player to send message to
     * @param message The message to center
     */
    public static void sendCenteredMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;

        // Simple centering logic - can be 
        int messageLength = ChatColor.stripColor(message).length();
        int spaces = Math.max(0, (50 - messageLength) / 2);

        StringBuilder centeredMessage = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            centeredMessage.append(" ");
        }
        centeredMessage.append(message);

        player.sendMessage(centeredMessage.toString());
    }

    /**
     * Send divider line for separating sections
     * @param player The player to send divider to
     * @param color The color of the divider
     */
    public static void sendDivider(Player player, ChatColor color) {
        if (player == null || !player.isOnline()) return;

        player.sendMessage(color + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Send header with title and dividers
     * @param player The player to send header to
     * @param title The header title
     */
    public static void sendHeader(Player player, String title) {
        if (player == null || !player.isOnline()) return;

        sendBlankLine(player);
        sendDivider(player, ChatColor.GRAY);
        sendCenteredMessage(player, ChatColor.BOLD + title);
        sendDivider(player, ChatColor.GRAY);
        sendBlankLine(player);
    }

    /**
     * Check if a player is online and can receive messages
     * @param player The player to check
     * @return true if player can receive messages
     */
    public static boolean canReceiveMessages(Player player) {
        return player != null && player.isOnline();
    }
}
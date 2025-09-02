package com.rednetty.server.core.mechanics.player.moderation;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive utility class for moderation system operations,
 * formatting, validation, and helper functions.
 */
public class ModerationUtils {
    
    private static final Logger logger = YakRealms.getInstance().getLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");
    private static final SimpleDateFormat SHORT_DATE_FORMAT = new SimpleDateFormat("MMM dd HH:mm");
    
    // Common time units in seconds
    private static final Map<String, Long> TIME_UNITS = new HashMap<>();
    static {
        TIME_UNITS.put("s", 1L);
        TIME_UNITS.put("sec", 1L);
        TIME_UNITS.put("second", 1L);
        TIME_UNITS.put("seconds", 1L);
        TIME_UNITS.put("m", 60L);
        TIME_UNITS.put("min", 60L);
        TIME_UNITS.put("minute", 60L);
        TIME_UNITS.put("minutes", 60L);
        TIME_UNITS.put("h", 3600L);
        TIME_UNITS.put("hr", 3600L);
        TIME_UNITS.put("hour", 3600L);
        TIME_UNITS.put("hours", 3600L);
        TIME_UNITS.put("d", 86400L);
        TIME_UNITS.put("day", 86400L);
        TIME_UNITS.put("days", 86400L);
        TIME_UNITS.put("w", 604800L);
        TIME_UNITS.put("week", 604800L);
        TIME_UNITS.put("weeks", 604800L);
        TIME_UNITS.put("mo", 2592000L);
        TIME_UNITS.put("month", 2592000L);
        TIME_UNITS.put("months", 2592000L);
        TIME_UNITS.put("y", 31536000L);
        TIME_UNITS.put("year", 31536000L);
        TIME_UNITS.put("years", 31536000L);
    }
    
    // IP address pattern for validation
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    // ==========================================
    // TIME PARSING AND FORMATTING
    // ==========================================
    
    /**
     * Parse duration string to seconds
     * Supports formats like: "1d", "2h30m", "45s", "1w2d3h"
     */
    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return -1;
        }
        
        durationStr = durationStr.toLowerCase().trim();
        
        // Handle special cases
        if (durationStr.equals("0") || durationStr.equals("perm") || durationStr.equals("permanent")) {
            return 0;
        }
        
        try {
            long totalSeconds = 0;
            StringBuilder numberBuffer = new StringBuilder();
            StringBuilder unitBuffer = new StringBuilder();
            
            for (char c : durationStr.toCharArray()) {
                if (Character.isDigit(c)) {
                    if (unitBuffer.length() > 0) {
                        // Process previous number-unit pair
                        totalSeconds += processTimeUnit(numberBuffer.toString(), unitBuffer.toString());
                        numberBuffer.setLength(0);
                        unitBuffer.setLength(0);
                    }
                    numberBuffer.append(c);
                } else if (Character.isLetter(c)) {
                    unitBuffer.append(c);
                } else if (c == ' ') {
                    if (unitBuffer.length() > 0) {
                        totalSeconds += processTimeUnit(numberBuffer.toString(), unitBuffer.toString());
                        numberBuffer.setLength(0);
                        unitBuffer.setLength(0);
                    }
                }
            }
            
            // Process final number-unit pair
            if (numberBuffer.length() > 0) {
                String unit = unitBuffer.length() > 0 ? unitBuffer.toString() : "m"; // Default to minutes
                totalSeconds += processTimeUnit(numberBuffer.toString(), unit);
            }
            
            return totalSeconds;
            
        } catch (Exception e) {
            logger.warning("Failed to parse duration: " + durationStr + " - " + e.getMessage());
            return -1;
        }
    }
    
    private static long processTimeUnit(String numberStr, String unit) {
        try {
            long number = Long.parseLong(numberStr);
            Long multiplier = TIME_UNITS.get(unit.toLowerCase());
            
            if (multiplier == null) {
                // Try partial matches
                for (Map.Entry<String, Long> entry : TIME_UNITS.entrySet()) {
                    if (entry.getKey().startsWith(unit.toLowerCase()) || 
                        unit.toLowerCase().startsWith(entry.getKey())) {
                        multiplier = entry.getValue();
                        break;
                    }
                }
            }
            
            return multiplier != null ? number * multiplier : number * 60; // Default to minutes
            
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Format duration in seconds to human-readable string
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return seconds == 0 ? "Permanent" : "Invalid";
        }
        
        long years = seconds / 31536000;
        seconds %= 31536000;
        long months = seconds / 2592000;
        seconds %= 2592000;
        long weeks = seconds / 604800;
        seconds %= 604800;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        
        List<String> parts = new ArrayList<>();
        
        if (years > 0) parts.add(years + "y");
        if (months > 0) parts.add(months + "mo");
        if (weeks > 0) parts.add(weeks + "w");
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (seconds > 0) parts.add(seconds + "s");
        
        if (parts.isEmpty()) return "0s";
        
        // Return up to 2 most significant units
        if (parts.size() <= 2) {
            return String.join(" ", parts);
        } else {
            return parts.get(0) + " " + parts.get(1);
        }
    }
    
    /**
     * Format duration with verbose names
     */
    public static String formatDurationVerbose(long seconds) {
        if (seconds <= 0) {
            return seconds == 0 ? "Permanent" : "Invalid";
        }
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        List<String> parts = new ArrayList<>();
        
        if (days > 0) parts.add(days + (days == 1 ? " day" : " days"));
        if (hours > 0) parts.add(hours + (hours == 1 ? " hour" : " hours"));
        if (minutes > 0) parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        if (secs > 0) parts.add(secs + (secs == 1 ? " second" : " seconds"));
        
        if (parts.isEmpty()) return "0 seconds";
        if (parts.size() == 1) return parts.get(0);
        if (parts.size() == 2) return parts.get(0) + " and " + parts.get(1);
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.size() - 1; i++) {
            result.append(parts.get(i)).append(", ");
        }
        result.append("and ").append(parts.get(parts.size() - 1));
        
        return result.toString();
    }
    
    /**
     * Format time remaining until expiry
     */
    public static String formatTimeRemaining(Date expiryDate) {
        if (expiryDate == null) return "Never expires";
        
        long remaining = (expiryDate.getTime() - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) return "Expired";
        
        return formatDuration(remaining);
    }
    
    // ==========================================
    // PLAYER UTILITIES
    // ==========================================
    
    /**
     * Find player by name (online or offline)
     */
    public static CompletableFuture<PlayerLookupResult> findPlayer(String name) {
        return CompletableFuture.supplyAsync(() -> {
            // Try online players first
            Player onlinePlayer = Bukkit.getPlayer(name);
            if (onlinePlayer != null) {
                return new PlayerLookupResult(onlinePlayer.getUniqueId(), onlinePlayer.getName(), true);
            }
            
            // Try offline players
            try {
                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(name);
                if (yakPlayer != null) {
                    return new PlayerLookupResult(yakPlayer.getUUID(), yakPlayer.getUsername(), false);
                }
            } catch (Exception e) {
                logger.warning("Error looking up offline player " + name + ": " + e.getMessage());
            }
            
            return null;
        });
    }
    
    /**
     * Get player's IP address
     */
    public static String getPlayerIP(Player player) {
        try {
            if (player.getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Exception e) {
            logger.warning("Error getting IP for player " + player.getName() + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Validate IP address format
     */
    public static boolean isValidIP(String ip) {
        return ip != null && IP_PATTERN.matcher(ip).matches();
    }
    
    /**
     * Get location info from IP (placeholder for GeoIP integration)
     */
    public static String getLocationFromIP(String ip) {
        // This would integrate with a GeoIP service
        // For now, return unknown
        return "Unknown";
    }
    
    // ==========================================
    // FORMATTING UTILITIES
    // ==========================================
    
    /**
     * Format date for display
     */
    public static String formatDate(Date date) {
        return date != null ? DATE_FORMAT.format(date) : "Never";
    }
    
    /**
     * Format date in short format
     */
    public static String formatDateShort(Date date) {
        return date != null ? SHORT_DATE_FORMAT.format(date) : "Never";
    }
    
    /**
     * Format action name for display
     */
    public static String formatActionName(ModerationHistory.ModerationAction action) {
        return action.name().toLowerCase().replace("_", " ");
    }
    
    /**
     * Get color for action type
     */
    public static ChatColor getActionColor(ModerationHistory.ModerationAction action) {
        switch (action) {
            case WARNING: return ChatColor.YELLOW;
            case MUTE: return ChatColor.GOLD;
            case TEMP_BAN: return ChatColor.RED;
            case PERMANENT_BAN:
            case IP_BAN: return ChatColor.DARK_RED;
            case KICK: return ChatColor.AQUA;
            case NOTE: return ChatColor.BLUE;
            case UNMUTE:
            case UNBAN: return ChatColor.GREEN;
            default: return ChatColor.WHITE;
        }
    }
    
    /**
     * Get color for severity level
     */
    public static ChatColor getSeverityColor(ModerationHistory.PunishmentSeverity severity) {
        switch (severity) {
            case LOW: return ChatColor.GREEN;
            case MEDIUM: return ChatColor.YELLOW;
            case HIGH: return ChatColor.GOLD;
            case SEVERE: return ChatColor.RED;
            case CRITICAL: return ChatColor.DARK_RED;
            default: return ChatColor.WHITE;
        }
    }
    
    /**
     * Get color for appeal status
     */
    public static ChatColor getAppealStatusColor(ModerationHistory.AppealStatus status) {
        switch (status) {
            case NOT_APPEALED: return ChatColor.GRAY;
            case PENDING:
            case UNDER_REVIEW: return ChatColor.YELLOW;
            case APPROVED: return ChatColor.GREEN;
            case DENIED: return ChatColor.RED;
            case WITHDRAWN: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }
    
    /**
     * Create severity indicator symbols
     */
    public static String getSeverityIndicator(ModerationHistory.PunishmentSeverity severity) {
        switch (severity) {
            case CRITICAL: return ChatColor.DARK_RED + "●●●●●";
            case SEVERE: return ChatColor.RED + "●●●●○";
            case HIGH: return ChatColor.GOLD + "●●●○○";
            case MEDIUM: return ChatColor.YELLOW + "●●○○○";
            case LOW: return ChatColor.GREEN + "●○○○○";
            default: return ChatColor.GRAY + "○○○○○";
        }
    }
    
    // ==========================================
    // VALIDATION UTILITIES
    // ==========================================
    
    /**
     * Validate reason length and content
     */
    public static ValidationResult validateReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return new ValidationResult(false, "Reason cannot be empty");
        }
        
        reason = reason.trim();
        
        if (reason.length() < 3) {
            return new ValidationResult(false, "Reason must be at least 3 characters long");
        }
        
        if (reason.length() > 500) {
            return new ValidationResult(false, "Reason cannot exceed 500 characters");
        }
        
        // Check for inappropriate content (basic filtering)
        if (containsInappropriateContent(reason)) {
            return new ValidationResult(false, "Reason contains inappropriate content");
        }
        
        return new ValidationResult(true, null);
    }
    
    /**
     * Validate duration
     */
    public static ValidationResult validateDuration(String durationStr) {
        long duration = parseDuration(durationStr);
        
        if (duration < 0) {
            return new ValidationResult(false, "Invalid duration format");
        }
        
        // Check reasonable limits
        if (duration > 0 && duration < 60) {
            return new ValidationResult(false, "Minimum duration is 1 minute");
        }
        
        if (duration > 31536000) { // More than 1 year
            return new ValidationResult(false, "Maximum duration is 1 year");
        }
        
        return new ValidationResult(true, null);
    }
    
    private static boolean containsInappropriateContent(String text) {
        // Basic inappropriate content filtering
        String lowerText = text.toLowerCase();
        String[] inappropriateWords = {"admin abuse", "corruption", "unfair"}; // Add more as needed
        
        for (String word : inappropriateWords) {
            if (lowerText.contains(word)) {
                return true;
            }
        }
        
        return false;
    }
    
    // ==========================================
    // NOTIFICATION UTILITIES
    // ==========================================
    
    /**
     * Send notification to staff with permission
     */
    public static void notifyStaff(String message, String permission) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(ChatColor.GRAY + "[STAFF] " + message);
            }
        }
        
        // Log to console
        logger.info("Staff Notification: " + ChatColor.stripColor(message));
    }
    
    /**
     * Send moderation alert to appropriate staff
     */
    public static void sendModerationAlert(String message, ModerationAlertLevel level) {
        ChatColor color;
        String prefix;
        String permission;
        
        switch (level) {
            case INFO:
                color = ChatColor.BLUE;
                prefix = "[INFO]";
                permission = "yakrealms.staff.alerts.info";
                break;
            case WARNING:
                color = ChatColor.YELLOW;
                prefix = "[WARNING]";
                permission = "yakrealms.staff.alerts.warning";
                break;
            case ALERT:
                color = ChatColor.RED;
                prefix = "[ALERT]";
                permission = "yakrealms.staff.alerts.alert";
                break;
            case CRITICAL:
                color = ChatColor.DARK_RED;
                prefix = "[CRITICAL]";
                permission = "yakrealms.staff.alerts.critical";
                break;
            default:
                return;
        }
        
        String fullMessage = color + prefix + " " + ChatColor.WHITE + message;
        notifyStaff(fullMessage, permission);
    }
    
    // ==========================================
    // HELPER CLASSES
    // ==========================================
    
    public static class PlayerLookupResult {
        private final UUID uuid;
        private final String name;
        private final boolean online;
        
        public PlayerLookupResult(UUID uuid, String name, boolean online) {
            this.uuid = uuid;
            this.name = name;
            this.online = online;
        }
        
        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public boolean isOnline() { return online; }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    public enum ModerationAlertLevel {
        INFO, WARNING, ALERT, CRITICAL
    }
    
    // ==========================================
    // PAGINATION UTILITIES
    // ==========================================
    
    /**
     * Create paginated list display
     */
    public static <T> void displayPaginatedList(CommandSender sender, List<T> items, 
                                               int page, int itemsPerPage,
                                               String title, ItemFormatter<T> formatter) {
        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + title);
            sender.sendMessage(ChatColor.GRAY + "No items to display.");
            return;
        }
        
        int totalPages = (items.size() + itemsPerPage - 1) / itemsPerPage;
        page = Math.max(1, Math.min(page, totalPages));
        
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + title + ChatColor.GRAY + " (Page " + page + "/" + totalPages + ")");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(formatter.format(items.get(i), i + 1));
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        if (totalPages > 1) {
            StringBuilder navigation = new StringBuilder(ChatColor.YELLOW + "Navigation: ");
            if (page > 1) {
                navigation.append(ChatColor.WHITE).append("[Prev] ");
            }
            navigation.append(ChatColor.GRAY).append("Page ").append(page).append("/").append(totalPages).append(" ");
            if (page < totalPages) {
                navigation.append(ChatColor.WHITE).append("[Next]");
            }
            sender.sendMessage(navigation.toString());
        }
    }
    
    @FunctionalInterface
    public interface ItemFormatter<T> {
        String format(T item, int index);
    }
    
    // ==========================================
    // STATISTICS UTILITIES
    // ==========================================
    
    /**
     * Calculate percentage safely
     */
    public static double calculatePercentage(int part, int total) {
        return total > 0 ? (double) part / total * 100.0 : 0.0;
    }
    
    /**
     * Format percentage for display
     */
    public static String formatPercentage(double percentage) {
        return String.format("%.1f%%", percentage);
    }
    
    /**
     * Create simple progress bar
     */
    public static String createProgressBar(double percentage, int length, ChatColor fillColor, ChatColor emptyColor) {
        int filled = (int) Math.round(percentage / 100.0 * length);
        filled = Math.max(0, Math.min(filled, length));
        
        StringBuilder bar = new StringBuilder();
        bar.append(fillColor);
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        bar.append(emptyColor);
        for (int i = filled; i < length; i++) {
            bar.append("█");
        }
        
        return bar.toString();
    }
}
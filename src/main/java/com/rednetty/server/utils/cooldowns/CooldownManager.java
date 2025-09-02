package com.rednetty.server.utils.cooldowns;

import com.rednetty.server.utils.messaging.MessageUtil;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified cooldown management system providing consistent cooldown handling
 * across all YakRealms systems with confirmation support.
 */
public class CooldownManager {

    // Storage and Constants

    // Thread-safe storage for cooldowns
    private static final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();

    // Confirmation system for destructive actions
    private static final Map<UUID, Map<String, Long>> confirmationRequests = new ConcurrentHashMap<>();

    // Default cooldown durations (in milliseconds)
    public static final long DEFAULT_CHAT_COOLDOWN = 3000; // 3 seconds
    public static final long DEFAULT_COMMAND_COOLDOWN = 1000; // 1 second
    public static final long DEFAULT_PARTY_ACTION_COOLDOWN = 5000; // 5 seconds
    public static final long DEFAULT_COMBAT_ABILITY_COOLDOWN = 10000; // 10 seconds
    public static final long DEFAULT_ECONOMY_ACTION_COOLDOWN = 2000; // 2 seconds
    public static final long DEFAULT_CONFIRMATION_TIMEOUT = 30000; // 30 seconds

    // Cooldown type constants
    public static final String CHAT_MESSAGE = "chat_message";
    public static final String CHAT_ITEM = "chat_item";
    public static final String PARTY_INVITE = "party_invite";
    public static final String PARTY_KICK = "party_kick";
    public static final String PARTY_PROMOTE = "party_promote";
    public static final String COMBAT_ABILITY = "combat_ability";
    public static final String COMBAT_SPECIAL = "combat_special";
    public static final String ECONOMY_PURCHASE = "economy_purchase";
    public static final String ECONOMY_SELL = "economy_sell";
    public static final String COMMAND_GENERAL = "command_general";
    public static final String VENDOR_INTERACTION = "vendor_interaction";
    public static final String PRIVATE_MESSAGE = "private_message";

    // Basic Cooldown Operations

    public static boolean hasCooldown(Player player, String type) {
        if (player == null || type == null) {
            return false;
        }

        return hasCooldown(player.getUniqueId(), type);
    }

    public static boolean hasCooldown(UUID playerId, String type) {
        if (playerId == null || type == null) {
            return false;
        }

        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) {
            return false;
        }

        Long expireTime = cooldowns.get(type);
        if (expireTime == null) {
            return false;
        }

        boolean isActive = System.currentTimeMillis() < expireTime;

        // Clean up expired cooldowns
        if (!isActive) {
            cooldowns.remove(type);
            if (cooldowns.isEmpty()) {
                playerCooldowns.remove(playerId);
            }
        }

        return isActive;
    }

    public static void applyCooldown(Player player, String type, long duration) {
        if (player == null || type == null || duration <= 0) {
            return;
        }

        applyCooldown(player.getUniqueId(), type, duration);
    }

    public static void applyCooldown(UUID playerId, String type, long duration) {
        if (playerId == null || type == null || duration <= 0) {
            return;
        }

        long expireTime = System.currentTimeMillis() + duration;

        playerCooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(type, expireTime);
    }

    public static long getRemainingCooldown(Player player, String type) {
        if (player == null || type == null) {
            return 0;
        }

        return getRemainingCooldown(player.getUniqueId(), type);
    }

    public static long getRemainingCooldown(UUID playerId, String type) {
        if (playerId == null || type == null) {
            return 0;
        }

        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) {
            return 0;
        }

        Long expireTime = cooldowns.get(type);
        if (expireTime == null) {
            return 0;
        }

        long remaining = expireTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public static void removeCooldown(Player player, String type) {
        if (player == null || type == null) {
            return;
        }

        removeCooldown(player.getUniqueId(), type);
    }

    public static void removeCooldown(UUID playerId, String type) {
        if (playerId == null || type == null) {
            return;
        }

        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns != null) {
            cooldowns.remove(type);
            if (cooldowns.isEmpty()) {
                playerCooldowns.remove(playerId);
            }
        }
    }

    // Cooldown with Messaging

    /**
     * Check cooldown and send message if active
     * @param player The player to check
     * @param type The cooldown type
     * @param actionName Human-readable action name for error message
     * @return true if no cooldown (action can proceed), false if on cooldown
     */
    public static boolean checkCooldownWithMessage(Player player, String type, String actionName) {
        if (player == null || type == null) {
            return true;
        }

        if (!hasCooldown(player, type)) {
            return true;
        }

        long remaining = getRemainingCooldown(player, type);
        String timeString = formatCooldownTime(remaining);

        MessageUtil.sendError(player, "Please wait " + timeString + " before " + actionName + " again.");
        return false;
    }

    /**
     * Apply cooldown with default duration based on type
     * @param player The player to apply cooldown to
     * @param type The cooldown type
     */
    public static void applyDefaultCooldown(Player player, String type) {
        if (player == null || type == null) {
            return;
        }

        long duration = getDefaultCooldownDuration(type);
        applyCooldown(player, type, duration);
    }

    /**
     * Check and apply cooldown in one operation
     * @param player The player to check and apply cooldown for
     * @param type The cooldown type
     * @param duration The cooldown duration
     * @param actionName Human-readable action name for error message
     * @return true if no cooldown (action can proceed), false if on cooldown
     */
    public static boolean checkAndApplyCooldown(Player player, String type, long duration, String actionName) {
        if (player == null || type == null) {
            return true;
        }

        if (hasCooldown(player, type)) {
            long remaining = getRemainingCooldown(player, type);
            String timeString = formatCooldownTime(remaining);
            MessageUtil.sendError(player, "Please wait " + timeString + " before " + actionName + " again.");
            return false;
        }

        applyCooldown(player, type, duration);
        return true;
    }

    // Confirmation System

    /**
     * Check if player has a recent confirmation for destructive actions
     * @param player The player to check
     * @param action The action requiring confirmation
     * @return true if player has recent confirmation
     */
    public static boolean hasRecentConfirmation(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        Map<String, Long> confirmations = confirmationRequests.get(player.getUniqueId());
        if (confirmations == null) {
            return false;
        }

        Long expireTime = confirmations.get(action);
        if (expireTime == null) {
            return false;
        }

        boolean isValid = System.currentTimeMillis() < expireTime;

        // Clean up expired confirmations
        if (!isValid) {
            confirmations.remove(action);
            if (confirmations.isEmpty()) {
                confirmationRequests.remove(player.getUniqueId());
            }
        }

        return isValid;
    }

    /**
     * Set confirmation requirement for destructive action
     * @param player The player requiring confirmation
     * @param action The action requiring confirmation
     */
    public static void setConfirmationRequired(Player player, String action) {
        if (player == null || action == null) {
            return;
        }

        long expireTime = System.currentTimeMillis() + DEFAULT_CONFIRMATION_TIMEOUT;

        confirmationRequests.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(action, expireTime);
    }

    /**
     * Mark confirmation as provided (consume the confirmation)
     * @param player The player providing confirmation
     * @param action The action being confirmed
     * @return true if confirmation was valid and consumed
     */
    public static boolean consumeConfirmation(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        Map<String, Long> confirmations = confirmationRequests.get(player.getUniqueId());
        if (confirmations == null) {
            return false;
        }

        Long expireTime = confirmations.remove(action);
        if (confirmations.isEmpty()) {
            confirmationRequests.remove(player.getUniqueId());
        }

        return expireTime != null && System.currentTimeMillis() < expireTime;
    }

    // Preset Cooldown Methods

    /**
     * Apply chat message cooldown
     * @param player The player to apply cooldown to
     */
    public static void applyChatCooldown(Player player) {
        applyCooldown(player, CHAT_MESSAGE, DEFAULT_CHAT_COOLDOWN);
    }

    /**
     * Check chat cooldown with message
     * @param player The player to check
     * @return true if can chat, false if on cooldown
     */
    public static boolean checkChatCooldown(Player player) {
        return checkCooldownWithMessage(player, CHAT_MESSAGE, "sending a message");
    }

    /**
     * Apply party action cooldown
     * @param player The player to apply cooldown to
     * @param action The specific party action
     */
    public static void applyPartyCooldown(Player player, String action) {
        applyCooldown(player, "party_" + action, DEFAULT_PARTY_ACTION_COOLDOWN);
    }

    /**
     * Check party action cooldown with message
     * @param player The player to check
     * @param action The specific party action
     * @return true if can perform action, false if on cooldown
     */
    public static boolean checkPartyCooldown(Player player, String action) {
        return checkCooldownWithMessage(player, "party_" + action, action + " in party");
    }

    /**
     * Apply combat ability cooldown
     * @param player The player to apply cooldown to
     * @param ability The specific ability name
     */
    public static void applyCombatAbilityCooldown(Player player, String ability) {
        applyCooldown(player, "combat_" + ability, DEFAULT_COMBAT_ABILITY_COOLDOWN);
    }

    /**
     * Check combat ability cooldown with message
     * @param player The player to check
     * @param ability The specific ability name
     * @return true if can use ability, false if on cooldown
     */
    public static boolean checkCombatAbilityCooldown(Player player, String ability) {
        return checkCooldownWithMessage(player, "combat_" + ability, "using " + ability);
    }

    /**
     * Apply economy action cooldown
     * @param player The player to apply cooldown to
     * @param action The specific economy action
     */
    public static void applyEconomyCooldown(Player player, String action) {
        applyCooldown(player, "economy_" + action, DEFAULT_ECONOMY_ACTION_COOLDOWN);
    }

    /**
     * Check economy action cooldown with message
     * @param player The player to check
     * @param action The specific economy action
     * @return true if can perform action, false if on cooldown
     */
    public static boolean checkEconomyCooldown(Player player, String action) {
        return checkCooldownWithMessage(player, "economy_" + action, action + " items");
    }

    // Utility Methods

    /**
     * Format cooldown time into human-readable string
     * @param milliseconds The time in milliseconds
     * @return Formatted time string
     */
    public static String formatCooldownTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "0 seconds";
        }

        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;

        if (minutes > 0) {
            long remainingSeconds = seconds % 60;
            if (remainingSeconds > 0) {
                return minutes + " minute" + (minutes > 1 ? "s" : "") +
                        " and " + remainingSeconds + " second" + (remainingSeconds > 1 ? "s" : "");
            } else {
                return minutes + " minute" + (minutes > 1 ? "s" : "");
            }
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "");
        }
    }

    /**
     * Get default cooldown duration for a type
     * @param type The cooldown type
     * @return Default duration in milliseconds
     */
    public static long getDefaultCooldownDuration(String type) {
        if (type == null) {
            return DEFAULT_COMMAND_COOLDOWN;
        }

        switch (type.toLowerCase()) {
            case CHAT_MESSAGE:
            case CHAT_ITEM:
                return DEFAULT_CHAT_COOLDOWN;
            case PARTY_INVITE:
            case PARTY_KICK:
            case PARTY_PROMOTE:
                return DEFAULT_PARTY_ACTION_COOLDOWN;
            case COMBAT_ABILITY:
            case COMBAT_SPECIAL:
                return DEFAULT_COMBAT_ABILITY_COOLDOWN;
            case ECONOMY_PURCHASE:
            case ECONOMY_SELL:
                return DEFAULT_ECONOMY_ACTION_COOLDOWN;
            default:
                return DEFAULT_COMMAND_COOLDOWN;
        }
    }

    /**
     * Clear all cooldowns for a player
     * @param player The player to clear cooldowns for
     */
    public static void clearAllCooldowns(Player player) {
        if (player == null) {
            return;
        }

        playerCooldowns.remove(player.getUniqueId());
        confirmationRequests.remove(player.getUniqueId());
    }

    /**
     * Clear expired cooldowns for all players (cleanup)
     */
    public static void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();

        // Clean up regular cooldowns
        playerCooldowns.entrySet().removeIf(playerEntry -> {
            Map<String, Long> cooldowns = playerEntry.getValue();
            cooldowns.entrySet().removeIf(cooldownEntry -> cooldownEntry.getValue() <= currentTime);
            return cooldowns.isEmpty();
        });

        // Clean up confirmations
        confirmationRequests.entrySet().removeIf(playerEntry -> {
            Map<String, Long> confirmations = playerEntry.getValue();
            confirmations.entrySet().removeIf(confirmEntry -> confirmEntry.getValue() <= currentTime);
            return confirmations.isEmpty();
        });
    }

    /**
     * Get all active cooldowns for a player
     * @param player The player to get cooldowns for
     * @return Map of cooldown types to expiry times
     */
    public static Map<String, Long> getActiveCooldowns(Player player) {
        if (player == null) {
            return new HashMap<>();
        }

        Map<String, Long> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns == null) {
            return new HashMap<>();
        }

        // Return copy to prevent external modification
        return new HashMap<>(cooldowns);
    }

    /**
     * Check if player can bypass cooldowns (admin/staff privilege)
     * @param player The player to check
     * @return true if player can bypass cooldowns
     */
    public static boolean canBypassCooldowns(Player player) {
        if (player == null) {
            return false;
        }

        return player.hasPermission("yakrealms.bypass.cooldowns") ||
                player.hasPermission("yakrealms.admin");
    }

    /**
     * Get total number of active cooldowns across all players
     * @return Total number of active cooldowns
     */
    public static int getTotalActiveCooldowns() {
        return playerCooldowns.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    /**
     * Check if a cooldown type is valid
     * @param type The cooldown type to validate
     * @return true if type is valid
     */
    public static boolean isValidCooldownType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return false;
        }

        // Allow alphanumeric characters, underscores, and hyphens
        return type.matches("^[a-zA-Z0-9_-]+$");
    }
}
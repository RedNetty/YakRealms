package com.rednetty.server.utils.permissions;

import com.rednetty.server.utils.messaging.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Unified Permission Utility System for YakRealms
 * Provides consistent permission checking and error handling across all systems
 * Replaces scattered permission implementations with standardized approach
 */
public class PermissionUtil {

    // ========================================
    // CORE PERMISSION NODES
    // ========================================

    // Base permissions
    public static final String BASE_PERMISSION = "yakrealms";
    public static final String PLAYER_PERMISSION = "yakrealms.player";
    public static final String STAFF_PERMISSION = "yakrealms.staff";
    public static final String ADMIN_PERMISSION = "yakrealms.admin";
    public static final String OWNER_PERMISSION = "yakrealms.owner";

    // Party permissions
    public static final String PARTY_BASE = "yakrealms.party";
    public static final String PARTY_CREATE = "yakrealms.party.create";
    public static final String PARTY_INVITE = "yakrealms.party.invite";
    public static final String PARTY_KICK = "yakrealms.party.kick";
    public static final String PARTY_PROMOTE = "yakrealms.party.promote";
    public static final String PARTY_ADMIN = "yakrealms.party.admin";

    // Chat permissions
    public static final String CHAT_BASE = "yakrealms.chat";
    public static final String CHAT_COLOR = "yakrealms.chat.color";
    public static final String CHAT_ITEM = "yakrealms.chat.item";
    public static final String CHAT_GLOBAL = "yakrealms.chat.global";
    public static final String CHAT_STAFF = "yakrealms.chat.staff";
    public static final String CHAT_BYPASS_COOLDOWN = "yakrealms.chat.bypasscooldown";
    public static final String CHAT_BYPASS_MUTE = "yakrealms.chat.bypassmute";

    // Combat permissions
    public static final String COMBAT_BASE = "yakrealms.combat";
    public static final String COMBAT_BYPASS_RESTRICTIONS = "yakrealms.combat.bypass";
    public static final String COMBAT_ADMIN = "yakrealms.combat.admin";

    // Economy permissions
    public static final String ECONOMY_BASE = "yakrealms.economy";
    public static final String ECONOMY_VENDOR = "yakrealms.economy.vendor";
    public static final String ECONOMY_ADMIN = "yakrealms.economy.admin";

    // Moderation permissions
    public static final String MODERATION_BASE = "yakrealms.moderation";
    public static final String MODERATION_MUTE = "yakrealms.moderation.mute";
    public static final String MODERATION_KICK = "yakrealms.moderation.kick";
    public static final String MODERATION_BAN = "yakrealms.moderation.ban";
    public static final String MODERATION_VIEW_LOGS = "yakrealms.moderation.logs";

    // ========================================
    // BASIC PERMISSION CHECKING
    // ========================================

    /**
     * Check if a command sender has a specific permission
     * @param sender The command sender to check
     * @param permission The permission to check for
     * @return true if sender has permission
     */
    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender == null || permission == null) {
            return false;
        }

        try {
            // Console always has permission
            if (!(sender instanceof Player)) {
                return true;
            }

            return sender.hasPermission(permission);
        } catch (Exception e) {
            // If permission check fails, deny access for safety
            return false;
        }
    }

    /**
     * Check if a player has a specific permission
     * @param player The player to check
     * @param permission The permission to check for
     * @return true if player has permission
     */
    public static boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) {
            return false;
        }

        try {
            return player.hasPermission(permission);
        } catch (Exception e) {
            // If permission check fails, deny access for safety
            return false;
        }
    }

    /**
     * Check if a command sender has any of the specified permissions
     * @param sender The command sender to check
     * @param permissions Array of permissions to check
     * @return true if sender has at least one of the permissions
     */
    public static boolean hasAnyPermission(CommandSender sender, String... permissions) {
        if (sender == null || permissions == null || permissions.length == 0) {
            return false;
        }

        for (String permission : permissions) {
            if (hasPermission(sender, permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a command sender has all of the specified permissions
     * @param sender The command sender to check
     * @param permissions Array of permissions to check
     * @return true if sender has all of the permissions
     */
    public static boolean hasAllPermissions(CommandSender sender, String... permissions) {
        if (sender == null || permissions == null || permissions.length == 0) {
            return false;
        }

        for (String permission : permissions) {
            if (!hasPermission(sender, permission)) {
                return false;
            }
        }

        return true;
    }

    // ========================================
    // ROLE-BASED CHECKING
    // ========================================

    /**
     * Check if a command sender is staff
     * @param sender The command sender to check
     * @return true if sender is staff
     */
    public static boolean isStaff(CommandSender sender) {
        return hasAnyPermission(sender, STAFF_PERMISSION, ADMIN_PERMISSION, OWNER_PERMISSION);
    }

    /**
     * Check if a player is staff
     * @param player The player to check
     * @return true if player is staff
     */
    public static boolean isStaff(Player player) {
        return hasAnyPermission(player, STAFF_PERMISSION, ADMIN_PERMISSION, OWNER_PERMISSION);
    }

    /**
     * Check if a command sender is admin
     * @param sender The command sender to check
     * @return true if sender is admin
     */
    public static boolean isAdmin(CommandSender sender) {
        return hasAnyPermission(sender, ADMIN_PERMISSION, OWNER_PERMISSION);
    }

    /**
     * Check if a player is admin
     * @param player The player to check
     * @return true if player is admin
     */
    public static boolean isAdmin(Player player) {
        return hasAnyPermission(player, ADMIN_PERMISSION, OWNER_PERMISSION);
    }

    /**
     * Check if a command sender is owner
     * @param sender The command sender to check
     * @return true if sender is owner
     */
    public static boolean isOwner(CommandSender sender) {
        return hasPermission(sender, OWNER_PERMISSION);
    }

    /**
     * Check if a player is owner
     * @param player The player to check
     * @return true if player is owner
     */
    public static boolean isOwner(Player player) {
        return hasPermission(player, OWNER_PERMISSION);
    }

    // ========================================
    // FEATURE-SPECIFIC PERMISSION CHECKING
    // ========================================

    /**
     * Check if a player has party permissions
     * @param player The player to check
     * @param action The specific party action (create, invite, kick, etc.)
     * @return true if player has permission
     */
    public static boolean hasPartyPermission(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        // Check for admin override
        if (isAdmin(player)) {
            return true;
        }

        // Check base party permission first
        if (!hasPermission(player, PARTY_BASE)) {
            return false;
        }

        // Check specific action permission
        String specificPermission = PARTY_BASE + "." + action.toLowerCase();
        return hasPermission(player, specificPermission);
    }

    /**
     * Check if a player has chat permissions
     * @param player The player to check
     * @param action The specific chat action (color, item, global, etc.)
     * @return true if player has permission
     */
    public static boolean hasChatPermission(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        // Check for admin override
        if (isAdmin(player)) {
            return true;
        }

        // Check base chat permission first
        if (!hasPermission(player, CHAT_BASE)) {
            return false;
        }

        // Check specific action permission
        String specificPermission = CHAT_BASE + "." + action.toLowerCase();
        return hasPermission(player, specificPermission);
    }

    /**
     * Check if a player has combat permissions
     * @param player The player to check
     * @param action The specific combat action
     * @return true if player has permission
     */
    public static boolean hasCombatPermission(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        // Check for admin override
        if (isAdmin(player)) {
            return true;
        }

        // Check base combat permission first
        if (!hasPermission(player, COMBAT_BASE)) {
            return false;
        }

        // Check specific action permission
        String specificPermission = COMBAT_BASE + "." + action.toLowerCase();
        return hasPermission(player, specificPermission);
    }

    /**
     * Check if a player has economy permissions
     * @param player The player to check
     * @param action The specific economy action
     * @return true if player has permission
     */
    public static boolean hasEconomyPermission(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        // Check for admin override
        if (isAdmin(player)) {
            return true;
        }

        // Check base economy permission first
        if (!hasPermission(player, ECONOMY_BASE)) {
            return false;
        }

        // Check specific action permission
        String specificPermission = ECONOMY_BASE + "." + action.toLowerCase();
        return hasPermission(player, specificPermission);
    }

    /**
     * Check if a player has moderation permissions
     * @param player The player to check
     * @param action The specific moderation action
     * @return true if player has permission
     */
    public static boolean hasModerationPermission(Player player, String action) {
        if (player == null || action == null) {
            return false;
        }

        // Check for admin override
        if (isAdmin(player)) {
            return true;
        }

        // Check if player is staff
        if (!isStaff(player)) {
            return false;
        }

        // Check specific action permission
        String specificPermission = MODERATION_BASE + "." + action.toLowerCase();
        return hasPermission(player, specificPermission);
    }

    // ========================================
    // PERMISSION CHECKING WITH MESSAGING
    // ========================================

    /**
     * Check permission and send error message if denied
     * @param sender The command sender to check
     * @param permission The permission to check for
     * @param action Description of the action being attempted
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkPermissionWithMessage(CommandSender sender, String permission, String action) {
        if (hasPermission(sender, permission)) {
            return true;
        }

        // Send appropriate error message
        if (sender instanceof Player) {
            MessageUtil.sendNoPermission((Player) sender, action);
        } else {
            sender.sendMessage("You don't have permission to " + action + ".");
        }

        return false;
    }

    /**
     * Check party permission and send error message if denied
     * @param player The player to check
     * @param action The party action being attempted
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkPartyPermissionWithMessage(Player player, String action) {
        if (hasPartyPermission(player, action)) {
            return true;
        }

        MessageUtil.sendNoPermission(player, action + " in parties");
        return false;
    }

    /**
     * Check chat permission and send error message if denied
     * @param player The player to check
     * @param action The chat action being attempted
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkChatPermissionWithMessage(Player player, String action) {
        if (hasChatPermission(player, action)) {
            return true;
        }

        MessageUtil.sendNoPermission(player, "use " + action + " in chat");
        return false;
    }

    /**
     * Check combat permission and send error message if denied
     * @param player The player to check
     * @param action The combat action being attempted
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkCombatPermissionWithMessage(Player player, String action) {
        if (hasCombatPermission(player, action)) {
            return true;
        }

        MessageUtil.sendNoPermission(player, action + " in combat");
        return false;
    }

    /**
     * Check economy permission and send error message if denied
     * @param player The player to check
     * @param action The economy action being attempted
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkEconomyPermissionWithMessage(Player player, String action) {
        if (hasEconomyPermission(player, action)) {
            return true;
        }

        MessageUtil.sendNoPermission(player, action + " with economy");
        return false;
    }

    /**
     * Check moderation permission and send error message if denied
     * @param player The player to check
     * @param action The moderation action being attempted
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkModerationPermissionWithMessage(Player player, String action) {
        if (hasModerationPermission(player, action)) {
            return true;
        }

        MessageUtil.sendNoPermission(player, "use moderation: " + action);
        return false;
    }

    // ========================================
    // COMMAND-SPECIFIC PERMISSION CHECKING
    // ========================================

    /**
     * Check if a command sender can use a specific command
     * @param sender The command sender to check
     * @param command The command name
     * @return true if sender can use the command
     */
    public static boolean hasCommandPermission(CommandSender sender, String command) {
        if (sender == null || command == null) {
            return false;
        }

        // Console can use all commands
        if (!(sender instanceof Player)) {
            return true;
        }

        // Check for admin override
        if (isAdmin(sender)) {
            return true;
        }

        // Build command permission string
        String commandPermission = "yakrealms.command." + command.toLowerCase();
        return hasPermission(sender, commandPermission);
    }

    /**
     * Check command permission and send error message if denied
     * @param sender The command sender to check
     * @param command The command name
     * @return true if permission granted, false if denied (message sent)
     */
    public static boolean checkCommandPermissionWithMessage(CommandSender sender, String command) {
        if (hasCommandPermission(sender, command)) {
            return true;
        }

        // Send appropriate error message
        if (sender instanceof Player) {
            MessageUtil.sendNoPermission((Player) sender, "use the /" + command + " command");
        } else {
            sender.sendMessage("You don't have permission to use the /" + command + " command.");
        }

        return false;
    }

    // ========================================
    // BYPASS PERMISSION CHECKING
    // ========================================

    /**
     * Check if a player can bypass chat restrictions
     * @param player The player to check
     * @return true if player can bypass restrictions
     */
    public static boolean canBypassChatRestrictions(Player player) {
        return hasAnyPermission(player, CHAT_BYPASS_COOLDOWN, CHAT_BYPASS_MUTE, STAFF_PERMISSION);
    }

    /**
     * Check if a player can bypass combat restrictions
     * @param player The player to check
     * @return true if player can bypass restrictions
     */
    public static boolean canBypassCombatRestrictions(Player player) {
        return hasAnyPermission(player, COMBAT_BYPASS_RESTRICTIONS, ADMIN_PERMISSION);
    }

    /**
     * Check if a player can bypass party restrictions
     * @param player The player to check
     * @return true if player can bypass restrictions
     */
    public static boolean canBypassPartyRestrictions(Player player) {
        return hasAnyPermission(player, PARTY_ADMIN, ADMIN_PERMISSION);
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * Get the highest permission level for a player
     * @param player The player to check
     * @return String representing the highest permission level
     */
    public static String getHighestPermissionLevel(Player player) {
        if (isOwner(player)) {
            return "Owner";
        } else if (isAdmin(player)) {
            return "Admin";
        } else if (isStaff(player)) {
            return "Staff";
        } else {
            return "Player";
        }
    }

    /**
     * Check if a player can target another player for actions
     * @param actor The player performing the action
     * @param target The player being targeted
     * @return true if actor can target the player
     */
    public static boolean canTargetPlayer(Player actor, Player target) {
        if (actor == null || target == null) {
            return false;
        }

        // Can't target yourself for most actions
        if (actor.equals(target)) {
            return false;
        }

        // Owners can target anyone
        if (isOwner(actor)) {
            return true;
        }

        // Admins can target non-owners
        if (isAdmin(actor) && !isOwner(target)) {
            return true;
        }

        // Staff can target non-staff
        if (isStaff(actor) && !isStaff(target)) {
            return true;
        }

        // Regular players can only target other regular players
        return !isStaff(target);
    }

    /**
     * Build a permission string for a specific feature and action
     * @param feature The feature name (party, chat, combat, etc.)
     * @param action The action name
     * @return The complete permission string
     */
    public static String buildPermission(String feature, String action) {
        if (feature == null || action == null) {
            return null;
        }

        return "yakrealms." + feature.toLowerCase() + "." + action.toLowerCase();
    }

    /**
     * Validate that a permission string is properly formatted
     * @param permission The permission to validate
     * @return true if permission is valid
     */
    public static boolean isValidPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        // Check if it starts with yakrealms
        if (!permission.startsWith("yakrealms")) {
            return false;
        }

        // Check for valid format (yakrealms.feature.action)
        String[] parts = permission.split("\\.");
        return parts.length >= 2 && parts.length <= 4;
    }
}
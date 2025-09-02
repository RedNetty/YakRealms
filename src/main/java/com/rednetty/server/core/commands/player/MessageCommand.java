package com.rednetty.server.core.commands.player;

import com.rednetty.server.core.mechanics.chat.ChatMechanics;
import com.rednetty.server.utils.messaging.MessageUtil;
import com.rednetty.server.utils.sounds.SoundUtil;
import com.rednetty.server.utils.permissions.PermissionUtil;
import com.rednetty.server.utils.cooldowns.CooldownManager;
import com.rednetty.server.utils.player.PlayerResolver;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 *  Message Command with unified utility integration
 * Uses MessageUtil, SoundUtil, PermissionUtil, CooldownManager, and PlayerResolver for consistency
 */
public class MessageCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Ensure command is executed by a player
        if (!(sender instanceof Player)) {
            MessageUtil.sendError(null, "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!PermissionUtil.checkChatPermissionWithMessage(player, "message")) {
            return true;
        }

        // Validate arguments
        if (args.length < 2) {
            sendUsageMessage(player);
            return true;
        }

        // Check cooldown (unless player can bypass)
        if (!PermissionUtil.canBypassChatRestrictions(player)) {
            if (!CooldownManager.checkCooldownWithMessage(player, CooldownManager.PRIVATE_MESSAGE, "sending private messages")) {
                return true;
            }
        }

        // Resolve target player
        String targetName = args[0];
        Player target = PlayerResolver.resolvePlayerWithMessage(player, targetName);

        if (target == null) {
            return true; // Error message already sent by PlayerResolver
        }

        // Additional target validation
        if (!validateTarget(player, target)) {
            return true;
        }

        // Build message
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) messageBuilder.append(" ");
            messageBuilder.append(args[i]);
        }
        String message = messageBuilder.toString().trim();

        // Validate message content
        if (!validateMessageContent(player, message)) {
            return true;
        }

        // Send the private message
        boolean success = sendPrivateMessage(player, target, message);

        if (success) {
            // Apply cooldown (unless bypassing)
            if (!PermissionUtil.canBypassChatRestrictions(player)) {
                CooldownManager.applyCooldown(player, CooldownManager.PRIVATE_MESSAGE, CooldownManager.DEFAULT_CHAT_COOLDOWN);
            }

            // Set reply targets
            ChatMechanics.setReplyTarget(target, player);
            ChatMechanics.setReplyTarget(player, target);
        }

        return true;
    }

    /**
     * Send usage message with helpful examples
     */
    private void sendUsageMessage(Player player) {
        MessageUtil.sendCommandUsage(player, "/msg <player> <message>", "Send a private message to another player.");

        MessageUtil.sendBlankLine(player);
        player.sendMessage(ChatColor.GRAY + "Examples:");
        player.sendMessage(ChatColor.GRAY + "  " + ChatColor.YELLOW + "/msg Steve Hello there!");
        player.sendMessage(ChatColor.GRAY + "  " + ChatColor.YELLOW + "/msg Alex Want to party up?");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "The recipient can reply with /r <message>");
    }

    /**
     * Validate the target player for messaging
     */
    private boolean validateTarget(Player sender, Player target) {
        // Check if trying to message themselves
        if (target.equals(sender)) {
            MessageUtil.sendError(sender, "You cannot send private messages to yourself!");
            MessageUtil.sendTip(sender, "Try talking to other players instead.");
            return false;
        }

        // Check if target is ignoring sender (if ignore system exists)
        if (isPlayerIgnoring(target, sender)) {
            MessageUtil.sendError(sender, target.getName() + " is not receiving messages right now.");
            MessageUtil.sendTip(sender, "Try contacting them later or through other means.");
            return false;
        }

        // Check if sender is muted
        if (ChatMechanics.isMuted(sender)) {
            MessageUtil.sendError(sender, "You cannot send private messages while muted.");
            return false;
        }

        // Check if target has DND mode (if it exists)
        if (hasDoNotDisturb(target)) {
            MessageUtil.sendWarning(sender, target.getName() + " has Do Not Disturb enabled.");
            sender.sendMessage(ChatColor.GRAY + "Your message was still delivered, but they might not respond immediately.");
        }

        return true;
    }

    /**
     * Validate message content
     */
    private boolean validateMessageContent(Player player, String message) {
        if (message == null || message.trim().isEmpty()) {
            MessageUtil.sendError(player, "Please provide a message to send.");
            return false;
        }

        // Check message length
        if (message.length() > 256) {
            MessageUtil.sendError(player, "Message is too long! Maximum 256 characters.");
            MessageUtil.sendTip(player, "Try breaking your message into smaller parts.");
            return false;
        }

        // Check for spam (basic)
        if (isSpamMessage(message)) {
            MessageUtil.sendError(player, "Please don't spam repeated characters or messages.");
            return false;
        }


        return true;
    }

    /**
     * Send the private message with proper formatting
     */
    private boolean sendPrivateMessage(Player sender, Player recipient, String message) {
        try {
            // Format messages
            String toRecipient = ChatColor.GRAY + "[" + ChatColor.AQUA + sender.getName() +
                    ChatColor.GRAY + " → " + ChatColor.AQUA + "You" +
                    ChatColor.GRAY + "] " + ChatColor.WHITE + message;

            String toSender = ChatColor.GRAY + "[" + ChatColor.AQUA + "You" +
                    ChatColor.GRAY + " → " + ChatColor.AQUA + recipient.getName() +
                    ChatColor.GRAY + "] " + ChatColor.WHITE + message;

            // Send messages
            sender.sendMessage(toSender);
            recipient.sendMessage(toRecipient);

            // Play sounds
            SoundUtil.playPrivateMessage(recipient);
            SoundUtil.playConfirmation(sender);

            // Log the message (for moderation purposes)
            logPrivateMessage(sender, recipient, message);

            // Send delivery confirmation to sender
            MessageUtil.sendBlankLine(sender);
            MessageUtil.sendTip(sender, "Message delivered to " + recipient.getName() +
                    ". They can reply with /r <message>");

            return true;

        } catch (Exception e) {
            MessageUtil.sendError(sender, "Failed to deliver message to " + recipient.getName() + ".");
            MessageUtil.sendTip(sender, "Please try again or contact staff if the issue persists.");
            return false;
        }
    }

    /**
     * Check if a player is ignoring another player
     */
    private boolean isPlayerIgnoring(Player target, Player sender) {
        // This would integrate with an ignore system if it exists
        // For now, return false
        return false;
    }

    /**
     * Check if a player has Do Not Disturb mode enabled
     */
    private boolean hasDoNotDisturb(Player player) {
        // This would integrate with a DND system if it exists
        // For now, return false
        return false;
    }

    /**
     * Check if a message is spam
     */
    private boolean isSpamMessage(String message) {
        // Basic spam detection
        if (message.length() < 3) {
            return false;
        }

        // Check for repeated characters (more than 50% of message)
        char firstChar = message.charAt(0);
        int sameCharCount = 0;
        for (char c : message.toCharArray()) {
            if (c == firstChar) {
                sameCharCount++;
            }
        }

        return (double) sameCharCount / message.length() > 0.5;
    }

    /**
     * Log private message for moderation
     */
    private void logPrivateMessage(Player sender, Player recipient, String message) {
        try {
            // This would integrate with a logging system
            String logEntry = String.format("[MSG] %s -> %s: %s",
                    sender.getName(), recipient.getName(), message);

            // Log to console and/or file
            org.bukkit.Bukkit.getLogger().info(logEntry);

        } catch (Exception e) {
            // Ignore logging errors
        }
    }

    /**
     * Show messaging statistics to player
     */
    private void showMessagingStats(Player player) {
        MessageUtil.sendBlankLine(player);
        player.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.WHITE + "Messaging Tips:");
        player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/r <message>" +
                ChatColor.GRAY + " to reply quickly");
        player.sendMessage(ChatColor.GRAY + "  • Private messages are only visible to you and the recipient");
        player.sendMessage(ChatColor.GRAY + "  • Be respectful and follow server rules");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "Consider using party chat for group conversations!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        // Check permission
        if (!PermissionUtil.hasChatPermission(player, "message")) {
            return completions;
        }

        if (args.length == 1) {
            // Suggest online player names
            return PlayerResolver.getPlayerSuggestions(args[0]);
        } else if (args.length == 2) {
            // Suggest common message starters
            String input = args[1].toLowerCase();
            String[] commonStarters = {
                    "Hi", "Hello", "Hey", "Want", "Can", "Do", "Are", "Could", "Would"
            };

            for (String starter : commonStarters) {
                if (starter.toLowerCase().startsWith(input)) {
                    completions.add(starter);
                }
            }
        }

        return completions;
    }
}
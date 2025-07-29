package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.chat.ChatMechanics;
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
import java.util.Arrays;
import java.util.List;

/**
 *  Reply Command with unified utility integration
 * Uses MessageUtil, SoundUtil, PermissionUtil, CooldownManager, and PlayerResolver for consistency
 * Provides smart reply functionality with  user experience
 */
public class ReplyCommand implements CommandExecutor, TabCompleter {

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
        if (args.length == 0) {
            sendUsageMessage(player);
            return true;
        }

        // Check cooldown (unless player can bypass)
        if (!PermissionUtil.canBypassChatRestrictions(player)) {
            if (!CooldownManager.checkCooldownWithMessage(player, CooldownManager.PRIVATE_MESSAGE, "sending replies")) {
                return true;
            }
        }

        // Check if player is muted
        if (ChatMechanics.isMuted(player)) {
            return true; // Error message handled by ChatMechanics
        }

        // Get the reply target with  validation
        Player recipient = getReplyTargetWithValidation(player);
        if (recipient == null) {
            return true; // Error message already sent
        }

        // Build the message
        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            if (messageBuilder.length() > 0) messageBuilder.append(" ");
            messageBuilder.append(arg);
        }
        String message = messageBuilder.toString().trim();

        // Validate message content
        if (!validateMessageContent(player, message)) {
            return true;
        }

        // Send the reply
        boolean success = sendReplyMessage(player, recipient, message);

        if (success) {
            // Apply cooldown (unless bypassing)
            if (!PermissionUtil.canBypassChatRestrictions(player)) {
                CooldownManager.applyCooldown(player, CooldownManager.PRIVATE_MESSAGE, CooldownManager.DEFAULT_CHAT_COOLDOWN);
            }

            // Update reply targets for continued conversation
            ChatMechanics.setReplyTarget(recipient, player);
            ChatMechanics.setReplyTarget(player, recipient);

            // Show delivery confirmation
            showDeliveryConfirmation(player, recipient);
        }

        return true;
    }

    /**
     * Send  usage message with helpful context
     */
    private void sendUsageMessage(Player player) {
        MessageUtil.sendCommandUsage(player, "/r <message>", "Reply to the last person who messaged you.");

        MessageUtil.sendBlankLine(player);

        // Check if player has a reply target
        Player replyTarget = ChatMechanics.getReplyTarget(player);
        if (replyTarget != null && replyTarget.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "💬 Your last conversation was with " +
                    ChatColor.YELLOW + replyTarget.getName());
            MessageUtil.sendTip(player, "Type your message after /r to reply to them.");
        } else {
            MessageUtil.sendTip(player, "You need to receive a message first before you can reply.");
            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.WHITE + "Alternative commands:");
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/msg <player> <message>" +
                    ChatColor.GRAY + " - Send a new private message");
            player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/p <message>" +
                    ChatColor.GRAY + " - Send a party message");
        }
    }

    /**
     * Get reply target with comprehensive validation and helpful feedback
     */
    private Player getReplyTargetWithValidation(Player player) {
        Player recipient = ChatMechanics.getReplyTarget(player);

        if (recipient == null) {
            MessageUtil.sendError(player, "You have nobody to reply to!");
            MessageUtil.sendTip(player, "Someone needs to message you first, or you can start a conversation with /msg <player> <message>");

            // Show recent activity suggestion
            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.WHITE + "Quick start options:");
            player.sendMessage(ChatColor.GRAY + "  • Look for active players in chat");
            player.sendMessage(ChatColor.GRAY + "  • Check who's in your party with " + ChatColor.YELLOW + "/p info");
            player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/msg <player> <message>" +
                    ChatColor.GRAY + " to start a conversation");
            return null;
        }

        if (!recipient.isOnline()) {
            MessageUtil.sendError(player, PlayerResolver.getName(recipient) + " is no longer online!");
            MessageUtil.sendTip(player, "Try messaging them when they return, or find someone else to chat with.");

            // Clear the invalid reply target
            ChatMechanics.setReplyTarget(player, null);

            // Suggest alternatives
            showAlternativeContacts(player);
            return null;
        }

        return recipient;
    }

    /**
     * Validate message content with helpful feedback
     */
    private boolean validateMessageContent(Player player, String message) {
        if (message == null || message.trim().isEmpty()) {
            MessageUtil.sendError(player, "Please provide a message to send.");
            MessageUtil.sendTip(player, "Example: /r Hello! How are you?");
            return false;
        }

        // Check message length
        if (message.length() > 256) {
            MessageUtil.sendError(player, "Message is too long! Maximum 256 characters.");
            MessageUtil.sendTip(player, "Try breaking your message into smaller parts.");
            return false;
        }

        // Check for spam (basic detection)
        if (isSpamMessage(message)) {
            MessageUtil.sendError(player, "Please don't spam repeated characters or messages.");
            MessageUtil.sendTip(player, "Write meaningful messages to have better conversations.");
            return false;
        }

        return true;
    }

    /**
     * Send the reply message with  formatting and feedback
     */
    private boolean sendReplyMessage(Player sender, Player recipient, String message) {
        try {
            // Format messages with  styling
            String toRecipient = formatPrivateMessage(sender.getName(), "You", message, true);
            String toSender = formatPrivateMessage("You", recipient.getName(), message, false);

            // Send messages
            sender.sendMessage(toSender);
            recipient.sendMessage(toRecipient);

            // Play appropriate sounds
            SoundUtil.playPrivateMessage(recipient);
            SoundUtil.playConfirmation(sender);

            // Log the message (for moderation purposes)
            logPrivateMessage(sender, recipient, message);

            // Update conversation statistics
            updateConversationStats(sender, recipient);

            return true;

        } catch (Exception e) {
            MessageUtil.sendError(sender, "Failed to deliver reply to " + recipient.getName() + ".");
            MessageUtil.sendTip(sender, "Please try again or contact staff if the issue persists.");
            return false;
        }
    }

    /**
     * Format private message with  styling
     */
    private String formatPrivateMessage(String from, String to, String message, boolean isIncoming) {
        ChatColor fromColor = isIncoming ? ChatColor.AQUA : ChatColor.GREEN;
        ChatColor toColor = isIncoming ? ChatColor.GREEN : ChatColor.AQUA;
        ChatColor arrowColor = ChatColor.GRAY;

        return ChatColor.GRAY + "[" + fromColor + from + arrowColor + " → " + toColor + to +
                ChatColor.GRAY + "] " + ChatColor.WHITE + message;
    }

    /**
     * Show delivery confirmation with helpful context
     */
    private void showDeliveryConfirmation(Player sender, Player recipient) {
        MessageUtil.sendBlankLine(sender);
        MessageUtil.sendTip(sender, "Reply delivered to " + recipient.getName() +
                ". They can continue the conversation with /r <message>");

        // Show conversation tips
        sender.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.WHITE + "Conversation tips:");
        sender.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/r <message>" +
                ChatColor.GRAY + " to continue replying");
        sender.sendMessage(ChatColor.GRAY + "  • Your reply target is now set to " +
                ChatColor.YELLOW + recipient.getName());
        sender.sendMessage(ChatColor.GRAY + "  • Both of you can use " + ChatColor.YELLOW + "/r" +
                ChatColor.GRAY + " to continue the conversation");
    }

    /**
     * Show alternative contacts when reply target is offline
     */
    private void showAlternativeContacts(Player player) {
        MessageUtil.sendBlankLine(player);
        player.sendMessage(ChatColor.GRAY + "💭 " + ChatColor.WHITE + "Find someone else to chat with:");

        // Show party members if in a party
        try {
            if (com.rednetty.server.mechanics.player.social.party.PartyMechanics.getInstance().isInParty(player)) {
                player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/p <message>" +
                        ChatColor.GRAY + " to chat with your party");
            }
        } catch (Exception e) {
            // Ignore if party system not available
        }

        player.sendMessage(ChatColor.GRAY + "  • Look for active players in general chat");
        player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/msg <player> <message>" +
                ChatColor.GRAY + " to start new conversations");
        player.sendMessage(ChatColor.GRAY + "  • Join community activities and events");
    }

    /**
     * Check if message is spam (basic detection)
     */
    private boolean isSpamMessage(String message) {
        if (message.length() < 3) {
            return false;
        }

        // Check for repeated characters (more than 60% of message)
        char firstChar = message.charAt(0);
        int sameCharCount = 0;
        for (char c : message.toCharArray()) {
            if (c == firstChar) {
                sameCharCount++;
            }
        }

        return (double) sameCharCount / message.length() > 0.6;
    }

    /**
     * Log private message for moderation purposes
     */
    private void logPrivateMessage(Player sender, Player recipient, String message) {
        try {
            String logEntry = String.format("[REPLY] %s -> %s: %s",
                    sender.getName(), recipient.getName(), message);
            org.bukkit.Bukkit.getLogger().info(logEntry);
        } catch (Exception e) {
            // Ignore logging errors
        }
    }

    /**
     * Update conversation statistics
     */
    private void updateConversationStats(Player sender, Player recipient) {
        try {
            // This would integrate with a statistics system
            // Track number of messages, conversation length, etc.

        } catch (Exception e) {
            // Ignore statistics errors
        }
    }

    /**
     * Show conversation history if available
     */
    private void showConversationHistory(Player player, Player target) {
        try {
            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "📜 " + ChatColor.WHITE + "Recent conversation with " +
                    ChatColor.YELLOW + target.getName() + ChatColor.WHITE + ":");
            player.sendMessage(ChatColor.GRAY + "Feature coming soon - conversation history!");

        } catch (Exception e) {
            // Ignore history errors
        }
    }

    /**
     * Check if players are ignoring each other
     */
    private boolean isIgnored(Player sender, Player recipient) {
        try {
            // This would integrate with an ignore system
            // For now, return false
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Show messaging statistics to player
     */
    private void showMessagingTips(Player player) {
        MessageUtil.sendBlankLine(player);
        player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.WHITE + "Pro messaging tips:");
        player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/r" +
                ChatColor.GRAY + " for quick replies");
        player.sendMessage(ChatColor.GRAY + "  • Private messages are only visible to you and the recipient");
        player.sendMessage(ChatColor.GRAY + "  • Be respectful and follow server rules");
        player.sendMessage(ChatColor.GRAY + "  • Use party chat for group conversations");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "Good communication builds lasting friendships!");
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

        // For first word, suggest common message starters
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            String[] commonStarters = {
                    "hi", "hello", "hey", "thanks", "sorry", "yes", "no", "ok", "sure", "maybe",
                    "how", "what", "when", "where", "why", "can", "could", "would", "will"
            };

            for (String starter : commonStarters) {
                if (starter.startsWith(input)) {
                    completions.add(starter);
                }
            }
        }
        // For subsequent words, suggest common follow-ups
        else if (args.length == 2) {
            String input = args[1].toLowerCase();
            String[] commonWords = {
                    "you", "are", "doing", "today", "there", "help", "please", "think", "about"
            };

            for (String word : commonWords) {
                if (word.startsWith(input)) {
                    completions.add(word);
                }
            }
        }

        return completions;
    }
}
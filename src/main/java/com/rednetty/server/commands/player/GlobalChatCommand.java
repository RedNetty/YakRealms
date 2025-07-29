package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class GlobalChatCommand implements CommandExecutor {
    private final YakPlayerManager playerManager;

    public GlobalChatCommand() {
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check if player is muted
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null && yakPlayer.getMuteTime() > 0) {
            int minutes = yakPlayer.getMuteTime() / 60;
            player.sendMessage(ChatColor.RED + "You are currently muted");
            if (yakPlayer.getMuteTime() > 0) {
                player.sendMessage(ChatColor.RED + "Your mute expires in " + minutes + " minutes.");
            } else {
                player.sendMessage(ChatColor.RED + "Your mute WILL NOT expire.");
            }
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /global <message>");
            return true;
        }

        // Build the message from args
        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(" ");
        }
        String message = messageBuilder.toString().trim();

        // Get the formatted name for the player
        String formattedName = getFormattedName(player);

        // Check if it's a trading message and change prefix accordingly
        List<String> tradeKeywords = Arrays.asList("wts", "trading", "selling", "casino", "wtb");
        boolean isTradingMessage = false;
        for (String keyword : tradeKeywords) {
            if (message.toLowerCase().contains(keyword)) {
                isTradingMessage = true;
                break;
            }
        }

        String globalPrefix = isTradingMessage ?
                ChatColor.GREEN + "<" + ChatColor.GREEN + ChatColor.BOLD + "T" + ChatColor.GREEN + "> " :
                ChatColor.AQUA + "<" + ChatColor.AQUA + ChatColor.BOLD + "G" + ChatColor.AQUA + "> ";

        // Full prefix including formatted name
        String fullPrefix = globalPrefix + formattedName;

        // Check if player is showing an item
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean showingItem = message.contains("@i@") && ChatMechanics.isPlayerHoldingValidItem(player);

        // Get all online players as recipients
        List<Player> recipients = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Send to all players
        if (showingItem) {
            // Send message with item display using the new public method
            try {
                int successCount = ChatMechanics.sendItemMessageToPlayers(player, itemInHand, fullPrefix, message, recipients);
                Bukkit.getLogger().info("[GLOBAL" + (isTradingMessage ? "-TRADE" : "") + "] " + player.getName() +
                        " (with item): " + message + " [sent to " + successCount + " players]");
            } catch (Exception e) {
                // Fallback to normal message if item display fails completely
                Bukkit.getLogger().log(Level.WARNING, "Global item message failed, using fallback", e);
                String itemName = ChatMechanics.getDisplayNameForItem(itemInHand);
                String fallbackMessage = fullPrefix + ChatColor.WHITE + ": " +
                        message.replace("@i@", ChatColor.YELLOW + "[" + itemName + "]" + ChatColor.WHITE);

                for (Player recipient : recipients) {
                    if (recipient != null && recipient.isOnline()) {
                        recipient.sendMessage(fallbackMessage);
                    }
                }

                Bukkit.getLogger().info("[GLOBAL" + (isTradingMessage ? "-TRADE" : "") + "] " + player.getName() +
                        " (with item - fallback): " + message);
            }
        } else {
            // Send regular message
            String fullMessage = fullPrefix + ChatColor.WHITE + ": " + message;
            for (Player recipient : recipients) {
                if (recipient != null && recipient.isOnline()) {
                    recipient.sendMessage(fullMessage);
                }
            }

            // Log the global message
            Bukkit.getLogger().info("[GLOBAL" + (isTradingMessage ? "-TRADE" : "") + "] " + player.getName() + ": " + message);
        }

        return true;
    }

    /**
     * Get a player's formatted name for chat
     *
     * @param player The player
     * @return The formatted name with rank and tag
     */
    private String getFormattedName(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            try {
                return yakPlayer.getFormattedDisplayName();
            } catch (Exception e) {
                // Fall through to manual formatting
            }
        }

        // Fallback manual formatting if YakPlayer method fails
        StringBuilder name = new StringBuilder();

        try {
            // Add guild tag if available
            if (yakPlayer != null && yakPlayer.isInGuild()) {
                String guildName = yakPlayer.getGuildName();
                if (guildName != null && !guildName.isEmpty()) {
                    name.append(ChatColor.WHITE).append("[").append(guildName).append("] ");
                }
            }

            // Add chat tag if not default
            ChatTag tag = ChatMechanics.getPlayerTag(player);
            if (tag != ChatTag.DEFAULT) {
                String tagString = tag.getTag();
                if (tagString != null && !tagString.isEmpty()) {
                    name.append(tagString).append(" ");
                }
            }

            // Add rank if not default
            Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
            if (rank != Rank.DEFAULT) {
                name.append(ChatColor.translateAlternateColorCodes('&', rank.tag)).append(" ");
            }

            // Add player name with color based on alignment
            ChatColor nameColor = ChatColor.GRAY; // Default

            if (yakPlayer != null) {
                String alignment = yakPlayer.getAlignment();
                if ("NEUTRAL".equals(alignment)) {
                    nameColor = ChatColor.YELLOW;
                } else if ("CHAOTIC".equals(alignment)) {
                    nameColor = ChatColor.RED;
                }
            }

            name.append(nameColor).append(player.getName());
        } catch (Exception e) {
            // Final fallback
            name.setLength(0);
            name.append(ChatColor.GRAY).append(player.getName());
        }

        return name.toString();
    }
}
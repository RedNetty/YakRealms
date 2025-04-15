package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

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
        if (yakPlayer != null && yakPlayer.isMuted()) {
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

        // Check if player is showing an item
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean showingItem = message.contains("@i@") && itemInHand != null && itemInHand.getType() != Material.AIR;

        // Send to all players
        if (showingItem) {
            // Send message with item display
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                String fullPrefix = globalPrefix + formattedName;
                sendItemMessage(player, itemInHand, fullPrefix, message, recipient);
            }
        } else {
            // Send regular message
            String fullMessage = globalPrefix + formattedName + ChatColor.WHITE + ": " + message;
            for (Player recipient : Bukkit.getOnlinePlayers()) {
                recipient.sendMessage(fullMessage);
            }
        }

        // Log the global message
        Bukkit.getLogger().info("[GLOBAL" + (isTradingMessage ? "-TRADE" : "") + "] " + player.getName() + ": " + message);

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
            return yakPlayer.getFormattedDisplayName();
        }

        // Fallback if YakPlayer not available
        StringBuilder name = new StringBuilder();

        // Add guild tag if available
        if (yakPlayer != null && yakPlayer.isInGuild()) {
            name.append(ChatColor.WHITE).append("[").append(yakPlayer.getGuildName()).append("] ");
        }

        // Add chat tag if not default
        ChatTag tag = ChatMechanics.getPlayerTag(player);
        if (tag != ChatTag.DEFAULT) {
            name.append(tag.getTag()).append(" ");
        }

        // Add rank if not default
        Rank rank = ModerationMechanics.getRank(player);
        if (rank != Rank.DEFAULT) {
            name.append(ChatColor.translateAlternateColorCodes('&', rank.tag)).append(" ");
        }

        // Add player name with color based on alignment
        ChatColor nameColor = ChatColor.GRAY; // Default

        if (yakPlayer != null) {
            if (yakPlayer.getAlignment().equals("NEUTRAL")) {
                nameColor = ChatColor.YELLOW;
            } else if (yakPlayer.getAlignment().equals("CHAOTIC")) {
                nameColor = ChatColor.RED;
            }
        }

        name.append(nameColor).append(player.getName());

        return name.toString();
    }

    /**
     * Send a message with an item showcase
     *
     * @param sender    The player sending the message
     * @param item      The item to show
     * @param prefix    The message prefix
     * @param message   The message text containing @i@
     * @param recipient The recipient to receive the message
     */
    private void sendItemMessage(Player sender, ItemStack item, String prefix, String message, Player recipient) {
        // Use the ChatMechanics to handle item display
        try {
            ChatMechanics.getInstance().getClass().getDeclaredMethod(
                            "sendItemHoverMessage", Player.class, ItemStack.class, String.class, String.class, Player.class)
                    .invoke(ChatMechanics.getInstance(), sender, item, prefix, message, recipient);
        } catch (Exception e) {
            // Fallback if reflection fails
            recipient.sendMessage(prefix + ChatColor.WHITE + ": " + message.replace("@i@", "[ITEM]"));
        }
    }
}
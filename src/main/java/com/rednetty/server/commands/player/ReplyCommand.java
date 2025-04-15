package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.chat.ChatMechanics;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReplyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /r <message>");
            return true;
        }

        // Get the reply target
        Player recipient = ChatMechanics.getReplyTarget(player);
        if (recipient == null || !recipient.isOnline()) {
            player.sendMessage(ChatColor.RED + "You have nobody to reply to.");
            return true;
        }

        // Build the message
        StringBuilder messageBuilder = new StringBuilder();
        for (String arg : args) {
            messageBuilder.append(arg).append(" ");
        }
        String message = messageBuilder.toString().trim();

        // Format messages
        String toRecipient = ChatColor.GRAY + "[" + ChatColor.AQUA + "You" + ChatColor.GRAY + " -> " +
                ChatColor.AQUA + recipient.getName() + ChatColor.GRAY + "] " + ChatColor.WHITE + message;

        String toSender = ChatColor.GRAY + "[" + ChatColor.AQUA + player.getName() + ChatColor.GRAY + " -> " +
                ChatColor.AQUA + "You" + ChatColor.GRAY + "] " + ChatColor.WHITE + message;

        // Send messages
        player.sendMessage(toRecipient);
        recipient.sendMessage(toSender);

        // Play sound to recipient
        recipient.playSound(recipient.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

        // Maintain reply targets
        ChatMechanics.setReplyTarget(recipient, player);

        return true;
    }
}
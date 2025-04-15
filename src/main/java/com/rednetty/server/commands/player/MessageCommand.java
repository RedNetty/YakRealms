package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.chat.ChatMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /msg <player> <message>");
            return true;
        }

        // Find the recipient
        Player recipient = Bukkit.getPlayer(args[0]);
        if (recipient == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Build the message
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
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

        // Set reply targets
        ChatMechanics.setReplyTarget(recipient, player);
        ChatMechanics.setReplyTarget(player, recipient);

        // Log the message
        Bukkit.getLogger().info("[PM] " + player.getName() + " to " + recipient.getName() + ": " + message);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Suggest online players
            return Bukkit.getOnlinePlayers().stream()
                    .filter(player -> player != sender) // Don't suggest self
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
package com.rednetty.server.core.commands.economy;

import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
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

public class PayCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;

    public PayCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Usage: /pay <player> <amount>");
            return true;
        }

        // Get target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }

        // Prevent paying yourself
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return true;
        }

        // Parse amount
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
            return true;
        }

        // Validate amount
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be positive.");
            return true;
        }

        // Check if player has enough gems
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null || economyManager.getPhysicalGems(player) < amount) {
            player.sendMessage(ChatColor.RED + "You don't have enough gems. You have " +
                    (yakPlayer == null ? 0 : economyManager.getPhysicalGems(player)) + " gems.");
            return true;
        }

        // Process payment
        if (economyManager.removePhysicalGems(player, amount).isSuccess()) {
            // Notify sender
            player.sendMessage(ChatColor.GREEN + "You paid " + target.getName() + " " + amount + " gems.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

            // Notify receiver
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " gems from " + player.getName() + ".");
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to process payment. Please try again.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase()) &&
                        !player.getName().equals(sender.getName())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            completions.add("10");
            completions.add("50");
            completions.add("100");
            completions.add("500");
            completions.add("1000");
        }

        return completions;
    }
}
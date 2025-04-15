package com.rednetty.server.commands.economy;

import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BalanceCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;

    public BalanceCommand(EconomyManager economyManager) {
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

        if (args.length == 0) {
            // Check own balance
            displayBalance(player, player.getUniqueId());
        } else if (args.length == 1) {
            // Check another player's balance (if they have permission)
            if (!player.hasPermission("yakrealms.balance.others")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to check other players' balances.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }

            displayBalance(player, target.getUniqueId());
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /balance [player]");
        }

        return true;
    }

    private void displayBalance(Player viewer, UUID targetUuid) {
        YakPlayer yakPlayer = playerManager.getPlayer(targetUuid);

        if (yakPlayer == null) {
            viewer.sendMessage(ChatColor.RED + "Player data not found.");
            return;
        }

        int inventoryGems = yakPlayer.getGems();
        int bankGems = yakPlayer.getBankGems();
        int total = inventoryGems + bankGems;

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "=== Balance for " + yakPlayer.getUsername() + " ===");
        viewer.sendMessage(ChatColor.GREEN + "Inventory: " + ChatColor.WHITE + inventoryGems + " gems");
        viewer.sendMessage(ChatColor.GREEN + "Bank: " + ChatColor.WHITE + bankGems + " gems");
        viewer.sendMessage(ChatColor.GREEN + "Total: " + ChatColor.WHITE + total + " gems");
        viewer.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("yakrealms.balance.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
package com.rednetty.server.core.commands.economy;

import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
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
    private static final String PERMISSION_OTHERS = "yakrealms.balance.others";
    private static final String MSG_PLAYERS_ONLY = ChatColor.RED + "This command can only be used by players.";
    private static final String MSG_NO_PERMISSION = ChatColor.RED + "You don't have permission to check other players' balances.";
    private static final String MSG_PLAYER_NOT_FOUND = ChatColor.RED + "Player not found: ";
    private static final String MSG_DATA_NOT_FOUND = ChatColor.RED + "Player data not found.";
    private static final String MSG_USAGE = ChatColor.RED + "Usage: /balance [player]";
    
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;

    public BalanceCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MSG_PLAYERS_ONLY);
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Check own balance
            displayBalance(player, player.getUniqueId());
        } else if (args.length == 1) {
            // Check another player's balance (if they have permission)
            if (!player.hasPermission(PERMISSION_OTHERS)) {
                player.sendMessage(MSG_NO_PERMISSION);
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(MSG_PLAYER_NOT_FOUND + args[0]);
                return true;
            }

            displayBalance(player, target.getUniqueId());
        } else {
            player.sendMessage(MSG_USAGE);
        }

        return true;
    }

    private void displayBalance(Player viewer, UUID targetUuid) {
        YakPlayer yakPlayer = playerManager.getPlayer(targetUuid);

        if (yakPlayer == null) {
            viewer.sendMessage(MSG_DATA_NOT_FOUND);
            return;
        }

        int bankGems = yakPlayer.getBankGems();

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "=== Balance for " + yakPlayer.getUsername() + " ===");
        viewer.sendMessage(ChatColor.GREEN + "Bank: " + ChatColor.WHITE + bankGems + " gems");
        viewer.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission(PERMISSION_OTHERS)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
package com.rednetty.server.core.commands.player;

import com.rednetty.server.core.mechanics.item.Journal;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for managing character journals
 */
public class JournalCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Open the player's own journal
            Journal.openJournal(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        // Check if they have permission to view others' journals
        if (args[0].equalsIgnoreCase("view") && args.length > 1) {
            if (!player.hasPermission("yakrealms.journal.others")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to view other players' journals.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            // Create a journal for the target player and give it to the requester
            player.sendMessage(ChatColor.GREEN + "Viewing " + target.getName() + "'s journal...");
            Journal.openJournal(target);
            return true;
        }

        // Check if they have admin permission to give journals
        if (args[0].equalsIgnoreCase("give") && args.length > 1) {
            if (!player.hasPermission("yakrealms.admin.journal")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to give journals.");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            player.sendMessage("Coffee is a little cute twink.");
            // Create a journal for the target player
            target.getInventory().addItem(Journal.createPlayerJournal(target));
            player.sendMessage(ChatColor.GREEN + "Gave a journal to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You received a character journal from " + player.getName());
            return true;
        }

        sendHelp(player);
        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== Journal Command Help =====");
        player.sendMessage(ChatColor.YELLOW + "/journal" + ChatColor.GRAY + " - Open your character journal");

        if (player.hasPermission("yakrealms.journal.others")) {
            player.sendMessage(ChatColor.YELLOW + "/journal view <player>" + ChatColor.GRAY + " - View another player's journal");
        }

        if (player.hasPermission("yakrealms.admin.journal")) {
            player.sendMessage(ChatColor.YELLOW + "/journal give <player>" + ChatColor.GRAY + " - Give a journal to a player");
        }

        player.sendMessage(ChatColor.YELLOW + "/journal help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("help");

            if (player.hasPermission("yakrealms.journal.others")) {
                completions.add("view");
            }

            if (player.hasPermission("yakrealms.admin.journal")) {
                completions.add("give");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if ((args[0].equalsIgnoreCase("view") && player.hasPermission("yakrealms.journal.others")) ||
                    (args[0].equalsIgnoreCase("give") && player.hasPermission("yakrealms.admin.journal"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
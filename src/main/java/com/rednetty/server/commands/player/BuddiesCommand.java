package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.player.social.friends.Buddies;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for managing player buddies (friends)
 */
public class BuddiesCommand implements CommandExecutor, TabCompleter {
    private final Buddies buddySystem;

    public BuddiesCommand() {
        this.buddySystem = Buddies.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            displayBuddyList(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "add":
                handleAddCommand(player, args);
                break;

            case "remove":
                handleRemoveCommand(player, args);
                break;

            case "list":
                displayBuddyList(player);
                break;

            case "help":
                displayHelp(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown buddy command: " + subCmd);
                player.sendMessage(ChatColor.GRAY + "Use /buddy help for available commands");
                break;
        }

        return true;
    }

    private void handleAddCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /buddy add <player>");
            return;
        }

        String targetName = args[1];
        Player target = buddySystem.findPlayer(targetName);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You cannot add yourself as a buddy.");
            return;
        }

        boolean success = buddySystem.addBuddy(player, target.getName());
        if (success) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void handleRemoveCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /buddy remove <player>");
            return;
        }

        String targetName = args[1];
        boolean success = buddySystem.removeBuddy(player, targetName);

        if (success) {
            player.sendMessage(ChatColor.YELLOW + "Removed " + targetName + " from your buddy list.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.5f);
        }
    }

    private void displayBuddyList(Player player) {
        List<String> buddies = buddySystem.getBuddies(player);

        player.sendMessage(ChatColor.GOLD + "===== Your Buddies =====");

        if (buddies.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You don't have any buddies yet.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/buddy add <player>" +
                    ChatColor.GRAY + " to add someone.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "You have " + buddies.size() + " buddies:");

            for (String buddy : buddies) {
                boolean online = Bukkit.getPlayerExact(buddy) != null;
                player.sendMessage(ChatColor.GRAY + "• " +
                        (online ? ChatColor.GREEN : ChatColor.RED) +
                        buddy +
                        (online ? ChatColor.GREEN + " (Online)" : ChatColor.RED + " (Offline)"));
            }
        }
    }

    private void displayHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== Buddy System Commands =====");
        player.sendMessage(ChatColor.YELLOW + "/buddy" + ChatColor.GRAY + " - Show your buddy list");
        player.sendMessage(ChatColor.YELLOW + "/buddy list" + ChatColor.GRAY + " - Show your buddy list");
        player.sendMessage(ChatColor.YELLOW + "/buddy add <player>" + ChatColor.GRAY + " - Add a player to your buddy list");
        player.sendMessage(ChatColor.YELLOW + "/buddy remove <player>" + ChatColor.GRAY + " - Remove a player from your buddy list");
        player.sendMessage(ChatColor.YELLOW + "/buddy help" + ChatColor.GRAY + " - Show this help message");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "You will be notified when your buddies join or leave the server.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "help").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            Player player = (Player) sender;

            if (args[0].equalsIgnoreCase("add")) {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player)) // Don't suggest self
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("remove")) {
                // For remove command, suggest from buddies list
                List<String> buddies = buddySystem.getBuddies(player);
                return buddies.stream()
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
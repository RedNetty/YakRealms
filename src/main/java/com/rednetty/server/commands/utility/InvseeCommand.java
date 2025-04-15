package com.rednetty.server.commands.utility;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to view and manage player inventories
 */
public class InvseeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("yakrealms.admin.invsee")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("inventory") || subCmd.equals("inv")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /invsee inventory <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            // Open player's inventory
            Inventory targetInv = Bukkit.createInventory(player, 36, target.getName() + "'s Inventory");

            // Copy main inventory contents
            ItemStack[] contents = target.getInventory().getContents();
            for (int i = 0; i < 36; i++) {
                if (i < contents.length && contents[i] != null) {
                    targetInv.setItem(i, contents[i].clone());
                }
            }

            player.openInventory(targetInv);
            player.sendMessage(ChatColor.GREEN + "Viewing " + target.getName() + "'s inventory (read-only view)");
            return true;
        }

        if (subCmd.equals("armor") || subCmd.equals("a")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /invsee armor <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            // Open player's armor inventory
            Inventory armorInv = Bukkit.createInventory(player, 9, target.getName() + "'s Armor");

            // Copy armor contents
            ItemStack[] armorContents = target.getInventory().getArmorContents();
            for (int i = 0; i < armorContents.length; i++) {
                if (armorContents[i] != null) {
                    armorInv.setItem(i, armorContents[i].clone());
                }
            }

            player.openInventory(armorInv);
            player.sendMessage(ChatColor.GREEN + "Viewing " + target.getName() + "'s armor (read-only view)");
            return true;
        }

        if (subCmd.equals("ender") || subCmd.equals("enderchest") || subCmd.equals("e")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /invsee ender <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            // Open player's ender chest
            player.openInventory(target.getEnderChest());
            player.sendMessage(ChatColor.GREEN + "Viewing " + target.getName() + "'s ender chest (read-only view)");
            return true;
        }

        if (subCmd.equals("edit") || subCmd.equals("modify")) {
            if (!player.hasPermission("yakrealms.admin.invsee.edit")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to edit inventories.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /invsee edit <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            // Open player's inventory directly (editable)
            player.openInventory(target.getInventory());
            player.sendMessage(ChatColor.YELLOW + "Editing " + target.getName() + "'s inventory. Changes will be saved.");
            return true;
        }

        if (subCmd.equals("clear")) {
            if (!player.hasPermission("yakrealms.admin.invsee.edit")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to clear inventories.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /invsee clear <player> [inventory|armor|enderchest|all]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            String clearType = args.length >= 3 ? args[2].toLowerCase() : "inventory";

            if (clearType.equals("inventory") || clearType.equals("inv")) {
                target.getInventory().clear();
                player.sendMessage(ChatColor.YELLOW + "Cleared " + target.getName() + "'s inventory.");
                target.sendMessage(ChatColor.RED + "Your inventory was cleared by an admin.");
            } else if (clearType.equals("armor") || clearType.equals("a")) {
                target.getInventory().setArmorContents(new ItemStack[4]);
                player.sendMessage(ChatColor.YELLOW + "Cleared " + target.getName() + "'s armor.");
                target.sendMessage(ChatColor.RED + "Your armor was cleared by an admin.");
            } else if (clearType.equals("enderchest") || clearType.equals("ender") || clearType.equals("e")) {
                target.getEnderChest().clear();
                player.sendMessage(ChatColor.YELLOW + "Cleared " + target.getName() + "'s ender chest.");
                target.sendMessage(ChatColor.RED + "Your ender chest was cleared by an admin.");
            } else if (clearType.equals("all")) {
                target.getInventory().clear();
                target.getInventory().setArmorContents(new ItemStack[4]);
                target.getEnderChest().clear();
                player.sendMessage(ChatColor.YELLOW + "Cleared all of " + target.getName() + "'s inventories.");
                target.sendMessage(ChatColor.RED + "All your inventories were cleared by an admin.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid clear type: " + clearType);
                player.sendMessage(ChatColor.GRAY + "Available types: inventory, armor, enderchest, all");
                return true;
            }

            return true;
        }

        if (subCmd.equals("help")) {
            sendHelp(player);
            return true;
        }

        if (Bukkit.getPlayer(args[0]) != null) {
            // Shorthand for /invsee inventory <player>
            Player target = Bukkit.getPlayer(args[0]);

            // Open player's inventory
            Inventory targetInv = Bukkit.createInventory(player, 36, target.getName() + "'s Inventory");

            // Copy main inventory contents
            ItemStack[] contents = target.getInventory().getContents();
            for (int i = 0; i < 36; i++) {
                if (i < contents.length && contents[i] != null) {
                    targetInv.setItem(i, contents[i].clone());
                }
            }

            player.openInventory(targetInv);
            player.sendMessage(ChatColor.GREEN + "Viewing " + target.getName() + "'s inventory (read-only view)");
            return true;
        }

        sendHelp(player);
        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Invsee Command Help =====");
        sender.sendMessage(ChatColor.YELLOW + "/invsee <player>" + ChatColor.GRAY + " - View a player's inventory");
        sender.sendMessage(ChatColor.YELLOW + "/invsee inventory <player>" + ChatColor.GRAY + " - View a player's inventory");
        sender.sendMessage(ChatColor.YELLOW + "/invsee armor <player>" + ChatColor.GRAY + " - View a player's armor");
        sender.sendMessage(ChatColor.YELLOW + "/invsee ender <player>" + ChatColor.GRAY + " - View a player's ender chest");

        if (sender.hasPermission("yakrealms.admin.invsee.edit")) {
            sender.sendMessage(ChatColor.YELLOW + "/invsee edit <player>" + ChatColor.GRAY + " - Edit a player's inventory");
            sender.sendMessage(ChatColor.YELLOW + "/invsee clear <player> [inv|armor|ender|all]" +
                    ChatColor.GRAY + " - Clear a player's inventory");
        }

        sender.sendMessage(ChatColor.YELLOW + "/invsee help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("yakrealms.admin.invsee")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "inventory", "inv", "armor", "a", "ender", "enderchest", "e", "help"));

            if (sender.hasPermission("yakrealms.admin.invsee.edit")) {
                completions.add("edit");
                completions.add("modify");
                completions.add("clear");
            }

            // Add online players
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // Player name completion for all subcommands
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("clear")) {
            return Arrays.asList("inventory", "inv", "armor", "a", "enderchest", "ender", "e", "all").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
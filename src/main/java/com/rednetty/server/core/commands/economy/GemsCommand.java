package com.rednetty.server.core.commands.economy;

import com.rednetty.server.core.mechanics.economy.EconomyManager;
import com.rednetty.server.core.mechanics.economy.MoneyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GemsCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economyManager;
    private final List<String> subCommands = Arrays.asList("withdraw", "deposit", "give", "count");

    public GemsCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /gems <withdraw|deposit|give|count> [amount|player]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "withdraw":
                handleWithdraw(player, args);
                break;

            case "deposit":
                handleDeposit(player, args);
                break;

            case "give":
                handleGive(player, args);
                break;

            case "count":
                handleCount(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown gems command: " + subCommand);
                player.sendMessage(ChatColor.YELLOW + "Available commands: withdraw, deposit, give, count");
                break;
        }

        return true;
    }

    private void handleWithdraw(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /gems withdraw <amount>");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be positive.");
            return;
        }

        // Check bank balance
        int bankBalance = economyManager.getBankGems(player.getUniqueId());
        if (bankBalance < amount) {
            player.sendMessage(ChatColor.RED + "You don't have enough gems in your bank. Bank balance: " + bankBalance);
            return;
        }

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Your inventory is full.");
            return;
        }

        // Process withdrawal
        if (economyManager.withdrawFromBank(player.getUniqueId(), amount).isSuccess()) {
            // Create gem item
            ItemStack gems = MoneyManager.makeGems(amount);
            player.getInventory().addItem(gems);

            player.sendMessage(ChatColor.GREEN + "You withdrew " + amount + " gems from your bank.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to withdraw gems. Please try again.");
        }
    }

    private void handleDeposit(Player player, String[] args) {
        // Count gems in inventory
        int gemsInInventory = MoneyManager.getGems(player);

        if (gemsInInventory <= 0) {
            player.sendMessage(ChatColor.RED + "You don't have any gems to deposit.");
            return;
        }

        int amount = gemsInInventory;

        // If amount specified, use that instead
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) {
                    player.sendMessage(ChatColor.RED + "Amount must be positive.");
                    return;
                }
                if (amount > gemsInInventory) {
                    player.sendMessage(ChatColor.RED + "You don't have that many gems. You have " + gemsInInventory + " gems.");
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount: " + args[1]);
                return;
            }
        }

        // Take gems from inventory
        MoneyManager.takeGems(player, amount);

        // Add to bank
        economyManager.depositToBank(player.getUniqueId(), amount);

        player.sendMessage(ChatColor.GREEN + "You deposited " + amount + " gems into your bank.");
    }

    private void handleGive(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /gems give <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You cannot give gems to yourself.");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Amount must be positive.");
            return;
        }

        // Count gems in inventory
        int gemsInInventory = MoneyManager.getGems(player);

        if (gemsInInventory < amount) {
            player.sendMessage(ChatColor.RED + "You don't have enough gems. You have " + gemsInInventory + " gems.");
            return;
        }

        // Take gems from player
        MoneyManager.takeGems(player, amount);

        // Create gem item for target
        ItemStack gems = MoneyManager.makeGems(amount);

        // Give to target
        if (target.getInventory().firstEmpty() != -1) {
            target.getInventory().addItem(gems);
        } else {
            // Drop at target's feet if inventory is full
            target.getWorld().dropItemNaturally(target.getLocation(), gems);
            target.sendMessage(ChatColor.YELLOW + "Your inventory was full. The gems were dropped at your feet.");
        }

        player.sendMessage(ChatColor.GREEN + "You gave " + amount + " gems to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "You received " + amount + " gems from " + player.getName() + ".");
    }

    private void handleCount(Player player) {
        int gemsInInventory = MoneyManager.getGems(player);
        player.sendMessage(ChatColor.GREEN + "You have " + gemsInInventory + " gems in your inventory.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("withdraw") || subCommand.equals("deposit")) {
                completions.add("10");
                completions.add("50");
                completions.add("100");
                completions.add("500");
                completions.add("1000");
            } else if (subCommand.equals("give")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase()) &&
                            !player.getName().equals(sender.getName())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("give")) {
                completions.add("10");
                completions.add("50");
                completions.add("100");
                completions.add("500");
                completions.add("1000");
            }
        }

        return completions;
    }
}
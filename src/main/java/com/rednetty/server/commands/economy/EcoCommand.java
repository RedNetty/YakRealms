package com.rednetty.server.commands.economy;

import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.mechanics.economy.TransactionResult;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EcoCommand implements CommandExecutor, TabCompleter {
    private final EconomyManager economyManager;
    private final YakPlayerManager playerManager;
    private final List<String> subCommands = Arrays.asList("give", "take", "set", "reset", "bank", "help");
    private final Logger logger = Logger.getLogger(EcoCommand.class.getName());

    public EcoCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.economy")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            displayHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give":
                handleGive(sender, args);
                break;

            case "take":
                handleTake(sender, args);
                break;

            case "set":
                handleSet(sender, args);
                break;

            case "reset":
                handleReset(sender, args);
                break;

            case "bank":
                handleBank(sender, args);
                break;

            case "help":
                displayHelp(sender);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown eco command: " + subCommand);
                sender.sendMessage(ChatColor.YELLOW + "Type /eco help for available commands");
                break;
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /eco give <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be positive.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Processing transaction...");
        TransactionResult result = economyManager.addGems(target.getUniqueId(), amount);

        if (result.isSuccess()) {
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " gems to " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to give gems: " + result.getMessage());
            logger.log(Level.WARNING, "Failed to give gems to player " + target.getName() + ": " + result.getMessage());
        }
    }

    private void handleTake(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /eco take <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Amount must be positive.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Processing transaction...");
        TransactionResult result = economyManager.removeGems(target.getUniqueId(), amount);

        if (result.isSuccess()) {
            sender.sendMessage(ChatColor.YELLOW + "Took " + amount + " gems from " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to take gems: " + result.getMessage());
            logger.log(Level.WARNING, "Failed to take gems from player " + target.getName() + ": " + result.getMessage());
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /eco set <player> <amount>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[2]);
            return;
        }

        if (amount < 0) {
            sender.sendMessage(ChatColor.RED + "Amount cannot be negative.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Processing transaction...");
        try {
            CompletableFuture<Boolean> future = playerManager.withPlayer(target.getUniqueId(), player -> {
                player.setGems(amount);

                // Notify player
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(ChatColor.YELLOW + "Your gems balance was set to " + amount + " by an admin.");
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS);

            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s gems balance to " + amount + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to set gems balance.");
                logger.log(Level.WARNING, "Failed to set gems balance for player " + target.getName());
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while processing your request.");
            logger.log(Level.SEVERE, "Error setting gems for player " + target.getName(), e);
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /eco reset <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Processing transaction...");
        try {
            CompletableFuture<Boolean> future = playerManager.withPlayer(target.getUniqueId(), player -> {
                player.setGems(0);
                player.setBankGems(0);

                // Notify player
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(ChatColor.RED + "Your economy data was reset by an admin.");
                }
            }, true);

            boolean success = future.get(10, TimeUnit.SECONDS);

            if (success) {
                sender.sendMessage(ChatColor.YELLOW + "Reset all economy data for " + target.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to reset economy data.");
                logger.log(Level.WARNING, "Failed to reset economy data for player " + target.getName());
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            sender.sendMessage(ChatColor.RED + "An error occurred while processing your request.");
            logger.log(Level.SEVERE, "Error resetting economy data for player " + target.getName(), e);
        }
    }

    private void handleBank(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /eco bank <give|take|set> <player> <amount>");
            return;
        }

        String bankAction = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[2]);
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
            return;
        }

        if (amount < 0 && !bankAction.equals("set")) {
            sender.sendMessage(ChatColor.RED + "Amount must be positive.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Processing transaction...");

        switch (bankAction) {
            case "give":
                TransactionResult giveResult = economyManager.addBankGems(target.getUniqueId(), amount);
                if (giveResult.isSuccess()) {
                    sender.sendMessage(ChatColor.GREEN + "Added " + amount + " gems to " + target.getName() + "'s bank.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to add gems to bank: " + giveResult.getMessage());
                    logger.log(Level.WARNING, "Failed to add gems to bank for player " + target.getName() + ": " + giveResult.getMessage());
                }
                break;

            case "take":
                TransactionResult takeResult = economyManager.removeBankGems(target.getUniqueId(), amount);
                if (takeResult.isSuccess()) {
                    sender.sendMessage(ChatColor.YELLOW + "Removed " + amount + " gems from " + target.getName() + "'s bank.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to remove gems from bank: " + takeResult.getMessage());
                    logger.log(Level.WARNING, "Failed to remove gems from bank for player " + target.getName() + ": " + takeResult.getMessage());
                }
                break;

            case "set":
                try {
                    CompletableFuture<Boolean> future = playerManager.withPlayer(target.getUniqueId(), player -> {
                        player.setBankGems(amount);

                        // Notify player
                        Player bukkitPlayer = player.getBukkitPlayer();
                        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                            bukkitPlayer.sendMessage(ChatColor.YELLOW + "Your bank balance was set to " + amount + " by an admin.");
                        }
                    }, true);

                    boolean success = future.get(10, TimeUnit.SECONDS);

                    if (success) {
                        sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s bank balance to " + amount + ".");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to set bank balance.");
                        logger.log(Level.WARNING, "Failed to set bank balance for player " + target.getName());
                    }
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    sender.sendMessage(ChatColor.RED + "An error occurred while processing your request.");
                    logger.log(Level.SEVERE, "Error setting bank balance for player " + target.getName(), e);
                }
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown bank action: " + bankAction);
                sender.sendMessage(ChatColor.YELLOW + "Available actions: give, take, set");
                break;
        }
    }

    private void displayHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Economy Admin Commands ===");
        sender.sendMessage(ChatColor.GREEN + "/eco give <player> <amount>" + ChatColor.GRAY + " - Give gems to a player");
        sender.sendMessage(ChatColor.GREEN + "/eco take <player> <amount>" + ChatColor.GRAY + " - Take gems from a player");
        sender.sendMessage(ChatColor.GREEN + "/eco set <player> <amount>" + ChatColor.GRAY + " - Set player's gems balance");
        sender.sendMessage(ChatColor.GREEN + "/eco reset <player>" + ChatColor.GRAY + " - Reset all economy data for a player");
        sender.sendMessage(ChatColor.GREEN + "/eco bank <give|take|set> <player> <amount>" + ChatColor.GRAY + " - Manage player's bank");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.economy")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("bank")) {
                completions.add("give");
                completions.add("take");
                completions.add("set");
            } else if (!subCommand.equals("help")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("bank")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (subCommand.equals("give") || subCommand.equals("take") || subCommand.equals("set")) {
                completions.add("100");
                completions.add("1000");
                completions.add("10000");
            }
        } else if (args.length == 4) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("bank")) {
                completions.add("100");
                completions.add("1000");
                completions.add("10000");
            }
        }

        return completions;
    }
}
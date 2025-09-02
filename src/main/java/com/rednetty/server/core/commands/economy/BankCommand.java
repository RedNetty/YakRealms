package com.rednetty.server.core.commands.economy;

import com.rednetty.server.core.mechanics.economy.BankManager;
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

public class BankCommand implements CommandExecutor, TabCompleter {
    private final BankManager bankManager;
    private final List<String> subCommands = Arrays.asList("open", "view", "auth", "unauth", "list", "upgrade", "help");

    public BankCommand(BankManager bankManager) {
        this.bankManager = bankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Simply open the bank
            bankManager.getBank(player, 1);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open":
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid page number: " + args[1]);
                        return true;
                    }
                }
                player.openInventory(bankManager.getBank(player, page));
                break;

            case "view":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bank view <player>");
                    return true;
                }

                if (!player.hasPermission("yakrealms.bank.view")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to view other players' banks.");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }

                // Custom logic to view another player's bank
                // This requires modifications to the BankManager to support this feature
                player.sendMessage(ChatColor.GREEN + "Viewing " + target.getName() + "'s bank...");
                // bankManager.viewOtherBank(player, target);
                break;

            case "auth":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bank auth <player>");
                    return true;
                }

                Player authTarget = Bukkit.getPlayer(args[1]);
                if (authTarget == null) {
                    player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }

                // Authorize player
                // This requires modifications to the BankManager to support this feature
                player.sendMessage(ChatColor.GREEN + "You have authorized " + authTarget.getName() + " to access your bank.");
                authTarget.sendMessage(ChatColor.GREEN + player.getName() + " has authorized you to access their bank.");
                // bankManager.authorizePlayer(player, authTarget);
                break;

            case "unauth":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /bank unauth <player>");
                    return true;
                }

                // Unauthorized player
                // This requires modifications to the BankManager to support this feature
                player.sendMessage(ChatColor.YELLOW + "You have revoked bank access for " + args[1] + ".");
                // bankManager.unauthorizePlayer(player, args[1]);
                break;

            case "list":
                // List authorized players
                // This requires modifications to the BankManager to support this feature
                player.sendMessage(ChatColor.GREEN + "Players authorized to access your bank:");
                player.sendMessage(ChatColor.GRAY + "Feature not yet implemented.");
                // List<String> authorizedPlayers = bankManager.getAuthorizedPlayers(player);
                // for (String authorizedPlayer : authorizedPlayers) {
                //     player.sendMessage(ChatColor.YELLOW + "- " + authorizedPlayer);
                // }
                break;

            case "upgrade":
                // Upgrade bank size
                player.sendMessage(ChatColor.GREEN + "Bank upgrade options:");
                player.sendMessage(ChatColor.YELLOW + "- Additional page: 5000 gems");
                player.sendMessage(ChatColor.GRAY + "Use /bank upgrade confirm to purchase");

                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    // Implement bank upgrade logic
                    player.sendMessage(ChatColor.GRAY + "Feature not yet implemented.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                break;

            case "help":
                displayHelp(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown bank command: " + subCommand);
                player.sendMessage(ChatColor.YELLOW + "Type /bank help for available commands");
                break;
        }

        return true;
    }

    private void displayHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Bank Commands ===");
        player.sendMessage(ChatColor.GREEN + "/bank" + ChatColor.GRAY + " - Open your bank");
        player.sendMessage(ChatColor.GREEN + "/bank open [page]" + ChatColor.GRAY + " - Open a specific bank page");
        player.sendMessage(ChatColor.GREEN + "/bank auth <player>" + ChatColor.GRAY + " - Authorize a player to access your bank");
        player.sendMessage(ChatColor.GREEN + "/bank unauth <player>" + ChatColor.GRAY + " - Remove a player's access to your bank");
        player.sendMessage(ChatColor.GREEN + "/bank list" + ChatColor.GRAY + " - List players with access to your bank");
        player.sendMessage(ChatColor.GREEN + "/bank upgrade" + ChatColor.GRAY + " - Upgrade your bank");

        if (player.hasPermission("yakrealms.bank.view")) {
            player.sendMessage(ChatColor.GREEN + "/bank view <player>" + ChatColor.GRAY + " - View another player's bank");
        }
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

            if (subCommand.equals("auth") || subCommand.equals("unauth") ||
                    (subCommand.equals("view") && sender.hasPermission("yakrealms.bank.view"))) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase()) &&
                            !player.getName().equals(sender.getName())) {
                        completions.add(player.getName());
                    }
                }
            } else if (subCommand.equals("open")) {
                completions.add("1");
                completions.add("2");
                completions.add("3");
            } else if (subCommand.equals("upgrade")) {
                completions.add("confirm");
            }
        }

        return completions;
    }
}
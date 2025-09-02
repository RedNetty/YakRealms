package com.rednetty.server.core.commands.player;

import com.rednetty.server.core.mechanics.player.items.SpeedfishMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Command for managing speedfish items
 */
public class SpeedfishCommand implements CommandExecutor, TabCompleter {
    private final SpeedfishMechanics speedfishMechanics;

    /**
     * Constructor
     *
     * @param speedfishMechanics The speedfish mechanics
     */
    public SpeedfishCommand(SpeedfishMechanics speedfishMechanics) {
        this.speedfishMechanics = speedfishMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.speedfish")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("give")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /speedfish give <player> <tier> [amount]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            int tier;
            try {
                tier = Integer.parseInt(args[2]);
                if (tier < 1 || tier > 5) {
                    sender.sendMessage(ChatColor.RED + "Tier must be between 1 and 5.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid tier: " + args[2]);
                return true;
            }

            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                    if (amount < 1) {
                        sender.sendMessage(ChatColor.RED + "Amount must be positive.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                    return true;
                }
            }

            ItemStack speedfish = SpeedfishMechanics.createSpeedfish(tier, false);
            speedfish.setAmount(amount);

            target.getInventory().addItem(speedfish);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x Tier " + tier + " speedfish to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You received " + amount + "x Tier " + tier + " speedfish from an admin");
            return true;
        }

        if (subCmd.equals("help")) {
            sendHelp(sender);
            return true;
        }

        sendHelp(sender);
        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Speedfish Command Help =====");
        sender.sendMessage(ChatColor.YELLOW + "/speedfish give <player> <tier> [amount]" +
                ChatColor.GRAY + " - Give speedfish to a player");
        sender.sendMessage(ChatColor.GRAY + "  Tiers: 1-5");
        sender.sendMessage(ChatColor.YELLOW + "/speedfish help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.speedfish")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return List.of("give", "help").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return IntStream.rangeClosed(1, 5)
                    .mapToObj(String::valueOf)
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return List.of("1", "8", "16", "32", "64").stream()
                    .filter(s -> s.startsWith(args[3]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
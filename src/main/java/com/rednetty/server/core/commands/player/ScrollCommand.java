package com.rednetty.server.core.commands.player;

import com.rednetty.server.core.mechanics.item.scroll.ScrollManager;
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
import java.util.stream.Collectors;

/**
 * Command for managing scrolls
 */
public class ScrollCommand implements CommandExecutor, TabCompleter {
    private final ScrollManager scrollManager;

    /**
     * Constructor
     *
     * @param scrollManager The scroll manager
     */
    public ScrollCommand(ScrollManager scrollManager) {
        this.scrollManager = scrollManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Please specify a player when using this command from console.");
            return true;
        }

        if (args.length == 0) {
            Player player = (Player) sender;
            // Open the scroll GUI
            scrollManager.openScrollGUI(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("shop") || subCmd.equals("gui")) {
            Player target;
            if (args.length < 2) {
                target = (Player) sender;
            } else {
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }

                // Check permission for opening GUI for others
                if (!sender.hasPermission("yakrealms.admin.scroll")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to open the scroll shop for other players.");
                    return true;
                }
            }

            scrollManager.openScrollGUI(target);
            if (sender != target) {
                sender.sendMessage(ChatColor.GREEN + "Opened scroll shop for " + target.getName());
            }
            return true;
        }

        // Admin commands below
        if (!sender.hasPermission("yakrealms.admin.scroll")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (subCmd.equals("give")) {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Usage: /scroll give <player> <type> <tier>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            String scrollType = args[2].toLowerCase();
            int tier;

            try {
                tier = Integer.parseInt(args[3]);
                if (tier < 0 || tier > 5) {
                    sender.sendMessage(ChatColor.RED + "Tier must be between 0 and 5.");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid tier: " + args[3]);
                return true;
            }

            ItemStack scroll;
            if (scrollType.equals("protection")) {
                scroll = scrollManager.getScrollGenerator().createProtectionScroll(tier);
            } else if (scrollType.equals("armor")) {
                scroll = scrollManager.getScrollGenerator().createArmorEnhancementScroll(tier);
            } else if (scrollType.equals("weapon")) {
                scroll = scrollManager.getScrollGenerator().createWeaponEnhancementScroll(tier);
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid scroll type: " + scrollType);
                sender.sendMessage(ChatColor.GRAY + "Available types: protection, armor, weapon");
                return true;
            }

            target.getInventory().addItem(scroll);
            sender.sendMessage(ChatColor.GREEN + "Gave " + scrollType + " scroll (tier " + tier + ") to " + target.getName());
            target.sendMessage(ChatColor.GREEN + "You received a " + scrollType + " scroll (tier " + tier + ") from an admin");
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
        sender.sendMessage(ChatColor.GOLD + "===== Scroll Command Help =====");
        sender.sendMessage(ChatColor.YELLOW + "/scroll" + ChatColor.GRAY + " - Open the scroll shop");
        sender.sendMessage(ChatColor.YELLOW + "/scroll shop [player]" + ChatColor.GRAY + " - Open the scroll shop for a player");

        if (sender.hasPermission("yakrealms.admin.scroll")) {
            sender.sendMessage(ChatColor.YELLOW + "/scroll give <player> <type> <tier>" +
                    ChatColor.GRAY + " - Give a scroll to a player");
            sender.sendMessage(ChatColor.GRAY + "  Types: protection, armor, weapon");
            sender.sendMessage(ChatColor.GRAY + "  Tiers: 0-5");
        }

        sender.sendMessage(ChatColor.YELLOW + "/scroll help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("shop", "gui", "help"));

            if (sender.hasPermission("yakrealms.admin.scroll")) {
                completions.add("give");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if ((args[0].equalsIgnoreCase("shop") || args[0].equalsIgnoreCase("gui")) &&
                    sender.hasPermission("yakrealms.admin.scroll")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("give") && sender.hasPermission("yakrealms.admin.scroll")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("yakrealms.admin.scroll")) {
            return Arrays.asList("protection", "armor", "weapon").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("yakrealms.admin.scroll")) {
            if (args[2].equalsIgnoreCase("protection")) {
                return Arrays.asList("0", "1", "2", "3", "4", "5").stream()
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            } else if (args[2].equalsIgnoreCase("armor") || args[2].equalsIgnoreCase("weapon")) {
                return Arrays.asList("1", "2", "3", "4", "5").stream()
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
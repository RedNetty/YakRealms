package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
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
import java.util.stream.Collectors;

/**
 * Command for managing player alignment
 */
public class AlignmentCommand implements CommandExecutor, TabCompleter {
    private final AlignmentMechanics alignmentMechanics;

    /**
     * Constructor
     *
     * @param alignmentMechanics The alignment mechanics
     */
    public AlignmentCommand(AlignmentMechanics alignmentMechanics) {
        this.alignmentMechanics = alignmentMechanics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Please specify a player when using this command from console.");
                return true;
            }

            Player player = (Player) sender;
            // Display own alignment info
            showAlignmentInfo(player, player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("info")) {
            if (args.length < 2 && !(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Please specify a player when using this command from console.");
                return true;
            }

            Player target;
            if (args.length < 2) {
                target = (Player) sender;
            } else {
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }

                // Check permission for viewing others
                if (!sender.hasPermission("yakrealms.alignment.others")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to view other players' alignment.");
                    return true;
                }
            }

            showAlignmentInfo(sender, target);
            return true;
        }

        // Admin commands below
        if (!sender.hasPermission("yakrealms.admin.alignment")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (subCmd.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /alignment set <player> <lawful|neutral|chaotic> [time]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            String alignment = args[2].toLowerCase();
            int time = 0;

            if (args.length >= 4) {
                try {
                    time = Integer.parseInt(args[3]);
                    if (time < 0) {
                        sender.sendMessage(ChatColor.RED + "Time cannot be negative.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid time: " + args[3]);
                    return true;
                }
            } else {
                // Default times
                if (alignment.equals("neutral")) {
                    time = 120; // 2 minutes
                } else if (alignment.equals("chaotic")) {
                    time = 300; // 5 minutes
                }
            }

            if (alignment.equals("lawful")) {
                AlignmentMechanics.setLawfulAlignment(target);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s alignment to LAWFUL");
            } else if (alignment.equals("neutral")) {
                AlignmentMechanics.setNeutralAlignment(target);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s alignment to NEUTRAL");
            } else if (alignment.equals("chaotic")) {
                AlignmentMechanics.setChaoticAlignment(target, time);
                sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s alignment to CHAOTIC for " + time + " seconds");
            } else {
                sender.sendMessage(ChatColor.RED + "Invalid alignment: " + alignment);
                sender.sendMessage(ChatColor.GRAY + "Available alignments: lawful, neutral, chaotic");
                return true;
            }

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
     * Show alignment info for a player
     */
    private void showAlignmentInfo(CommandSender viewer, Player target) {
        String alignmentString = AlignmentMechanics.getAlignmentString(target);
        int timeRemaining = AlignmentMechanics.getAlignmentTime(target);

        alignmentString = ChatColor.translateAlternateColorCodes('&', alignmentString);

        viewer.sendMessage(ChatColor.GOLD + "===== Alignment Info: " + target.getName() + " =====");
        viewer.sendMessage(ChatColor.YELLOW + "Current Alignment: " + alignmentString);

        if (timeRemaining > 0) {
            viewer.sendMessage(ChatColor.YELLOW + "Time Remaining: " + timeRemaining + " seconds");
        }

        if (alignmentString.contains("LAWFUL")) {
            viewer.sendMessage(ChatColor.GRAY + "Effect: -30% Durability Arm/Wep on Death");
        } else if (alignmentString.contains("NEUTRAL")) {
            viewer.sendMessage(ChatColor.GRAY + "Effect: 25%/50% Arm/Wep LOST on Death");
        } else if (alignmentString.contains("CHAOTIC")) {
            viewer.sendMessage(ChatColor.GRAY + "Effect: Inventory LOST on Death");
            viewer.sendMessage(ChatColor.GRAY + "Cannot enter safe zones");
        }
    }

    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Alignment Command Help =====");
        sender.sendMessage(ChatColor.YELLOW + "/alignment" + ChatColor.GRAY + " - Show your alignment info");
        sender.sendMessage(ChatColor.YELLOW + "/alignment info [player]" + ChatColor.GRAY + " - Show alignment info for a player");

        if (sender.hasPermission("yakrealms.admin.alignment")) {
            sender.sendMessage(ChatColor.YELLOW + "/alignment set <player> <lawful|neutral|chaotic> [time]" +
                    ChatColor.GRAY + " - Set a player's alignment");
        }

        sender.sendMessage(ChatColor.YELLOW + "/alignment help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("info", "help"));

            if (sender.hasPermission("yakrealms.admin.alignment")) {
                completions.add("set");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info") ||
                    (args[0].equalsIgnoreCase("set") && sender.hasPermission("yakrealms.admin.alignment"))) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.hasPermission("yakrealms.admin.alignment")) {
            return Arrays.asList("lawful", "neutral", "chaotic").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("set") &&
                sender.hasPermission("yakrealms.admin.alignment") &&
                !args[2].equalsIgnoreCase("lawful")) {
            if (args[2].equalsIgnoreCase("neutral")) {
                return Arrays.asList("120", "300", "600").stream()
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            } else if (args[2].equalsIgnoreCase("chaotic")) {
                return Arrays.asList("300", "600", "1200").stream()
                        .filter(s -> s.startsWith(args[3]))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
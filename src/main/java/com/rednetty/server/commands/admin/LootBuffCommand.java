package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.drops.buff.LootBuffManager;
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

public class LootBuffCommand implements CommandExecutor, TabCompleter {
    private final LootBuffManager lootBuffManager;

    public LootBuffCommand(LootBuffManager lootBuffManager) {
        this.lootBuffManager = lootBuffManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.drops")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        // Check for active buff info
        if (args[0].equalsIgnoreCase("info")) {
            if (lootBuffManager.isBuffActive()) {
                sender.sendMessage(ChatColor.GREEN + "Active Loot Buff:");
                sender.sendMessage(ChatColor.YELLOW + "Owner: " + lootBuffManager.getActiveBuff().getOwnerName());
                sender.sendMessage(ChatColor.YELLOW + "Buff Rate: " + lootBuffManager.getActiveBuff().getBuffRate() + "%");
                sender.sendMessage(ChatColor.YELLOW + "Time Left: " + formatTime(lootBuffManager.getActiveBuff().getRemainingSeconds()));
                sender.sendMessage(ChatColor.YELLOW + "Improved Drops: " + lootBuffManager.getImprovedDrops());
            } else {
                sender.sendMessage(ChatColor.YELLOW + "No active loot buff.");
            }
            return true;
        }

        // Create a buff item
        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /lootbuff create <player> <buff_rate> [duration_minutes]");
                return true;
            }

            String playerName = args[1];
            Player targetPlayer = Bukkit.getPlayer(playerName);

            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online: " + playerName);
                return true;
            }

            try {
                int buffRate = Integer.parseInt(args[2]);
                int durationMinutes = args.length > 3 ? Integer.parseInt(args[3]) : 30; // Default 30 minutes

                if (buffRate < 1 || buffRate > 100) {
                    sender.sendMessage(ChatColor.RED + "Buff rate must be between 1 and 100.");
                    return true;
                }

                if (durationMinutes < 1 || durationMinutes > 120) {
                    sender.sendMessage(ChatColor.RED + "Duration must be between 1 and 120 minutes.");
                    return true;
                }

                // Create the buff item with player info
                ItemStack buffItem = lootBuffManager.createBuffItem(
                        targetPlayer.getName(),
                        targetPlayer.getUniqueId(),
                        buffRate,
                        durationMinutes
                );

                // Give the item to the player
                if (targetPlayer.getInventory().firstEmpty() != -1) {
                    targetPlayer.getInventory().addItem(buffItem);
                    sender.sendMessage(ChatColor.GREEN + "Loot buff item created and given to " + targetPlayer.getName());
                    targetPlayer.sendMessage(ChatColor.GREEN + "You have received a loot buff item!");
                } else {
                    targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), buffItem);
                    sender.sendMessage(ChatColor.YELLOW + "Player's inventory is full. Dropped the item at their feet.");
                    targetPlayer.sendMessage(ChatColor.YELLOW + "Your inventory is full. A loot buff item was dropped at your feet!");
                }

                return true;
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number format. Usage: /lootbuff create <player> <buff_rate> [duration_minutes]");
                return true;
            }
        }

        // End active buff (admin override)
        if (args[0].equalsIgnoreCase("end") && sender.hasPermission("yakrealms.admin.drops")) {
            if (!lootBuffManager.isBuffActive()) {
                sender.sendMessage(ChatColor.RED + "No active loot buff to end.");
                return true;
            }

            String ownerName = lootBuffManager.getActiveBuff().getOwnerName();
            int improvedDrops = lootBuffManager.getImprovedDrops();

            // Force end the buff
            lootBuffManager.resetImprovedDrops();
            sender.sendMessage(ChatColor.GREEN + "Ended the active loot buff from " + ownerName +
                    " which improved " + improvedDrops + " drops.");

            return true;
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.drops")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = Arrays.asList("info", "create", "end");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("10", "25", "50", "75", "100").stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("15", "30", "45", "60", "90", "120").stream()
                    .filter(s -> s.startsWith(args[3]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Loot Buff Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/lootbuff info " + ChatColor.WHITE + "- Show active buff information");
        sender.sendMessage(ChatColor.YELLOW + "/lootbuff create <player> <buff_rate> [duration_minutes] " +
                ChatColor.WHITE + "- Create a buff item");
        if (sender.hasPermission("yakrealms.admin.drops")) {
            sender.sendMessage(ChatColor.YELLOW + "/lootbuff end " + ChatColor.WHITE + "- End the active buff early");
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }
}
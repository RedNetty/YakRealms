package com.rednetty.server.commands.player;

import com.rednetty.server.mechanics.chat.ChatMechanics;
import com.rednetty.server.mechanics.chat.ChatTag;
import com.rednetty.server.mechanics.player.YakPlayer;
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
import java.util.stream.Collectors;

public class ChatTagCommand implements CommandExecutor, TabCompleter {
    private final YakPlayerManager playerManager;

    public ChatTagCommand() {
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            showAvailableTags(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        if (subCmd.equals("list")) {
            showAvailableTags(player);
            return true;
        } else if (subCmd.equals("set")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /chattag set <tag>");
                return true;
            }

            String tagName = args[1].toUpperCase();
            ChatTag tag;

            try {
                tag = ChatTag.valueOf(tagName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid tag: " + args[1]);
                player.sendMessage(ChatColor.GRAY + "Use /chattag list to see available tags.");
                return true;
            }

            // Check if player has this tag unlocked
            if (tag != ChatTag.DEFAULT && !ChatMechanics.hasTagUnlocked(player, tag)) {
                player.sendMessage(ChatColor.RED + "You don't have this tag unlocked.");
                return true;
            }

            // Set the tag
            ChatMechanics.setPlayerTag(player, tag);

            if (tag == ChatTag.DEFAULT) {
                player.sendMessage(ChatColor.GREEN + "Your chat tag has been removed.");
            } else {
                player.sendMessage(ChatColor.GREEN + "Your chat tag has been set to: " + tag.getTag());
            }

            return true;
        } else if (subCmd.equals("unlock")) {
            // Unlock command is admin-only
            if (!player.hasPermission("yakrealms.admin.chattag")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /chattag unlock <player> <tag>");
                return true;
            }

            // Find target player
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return true;
            }

            // Find tag
            String tagName = args[2].toUpperCase();
            ChatTag tag;

            try {
                tag = ChatTag.valueOf(tagName);
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid tag: " + args[2]);
                return true;
            }

            // Unlock the tag
            ChatMechanics.unlockTag(target, tag);

            player.sendMessage(ChatColor.GREEN + "Unlocked tag " + tag.getTag() +
                    ChatColor.GREEN + " for " + target.getName());

            target.sendMessage(ChatColor.GREEN + "You've unlocked a new chat tag: " + tag.getTag());

            return true;
        } else if (subCmd.equals("help")) {
            showHelp(player);
            return true;
        }

        showHelp(player);
        return true;
    }

    private void showAvailableTags(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        player.sendMessage(ChatColor.GOLD + "===== Your Chat Tags =====");
        player.sendMessage(ChatColor.YELLOW + "Current Tag: " +
                (ChatMechanics.getPlayerTag(player) == ChatTag.DEFAULT ?
                        ChatColor.GRAY + "None" :
                        ChatMechanics.getPlayerTag(player).getTag()));

        player.sendMessage(ChatColor.YELLOW + "Available Tags:");
        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + "DEFAULT (No tag)");

        // List unlocked tags
        for (ChatTag tag : ChatTag.values()) {
            if (tag != ChatTag.DEFAULT && ChatMechanics.hasTagUnlocked(player, tag)) {
                player.sendMessage(ChatColor.GRAY + "- " + tag.getTag() +
                        (ChatMechanics.getPlayerTag(player) == tag ?
                                ChatColor.GREEN + " (Current)" : ""));
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/chattag set <tag>" +
                ChatColor.YELLOW + " to change your tag.");
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== Chat Tag Commands =====");
        player.sendMessage(ChatColor.YELLOW + "/chattag" + ChatColor.GRAY + " - Show your available tags");
        player.sendMessage(ChatColor.YELLOW + "/chattag list" + ChatColor.GRAY + " - Show your available tags");
        player.sendMessage(ChatColor.YELLOW + "/chattag set <tag>" + ChatColor.GRAY + " - Set your chat tag");

        if (player.hasPermission("yakrealms.admin.chattag")) {
            player.sendMessage(ChatColor.YELLOW + "/chattag unlock <player> <tag>" +
                    ChatColor.GRAY + " - Unlock a tag for a player");
        }

        player.sendMessage(ChatColor.YELLOW + "/chattag help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("list", "set", "help"));

            if (player.hasPermission("yakrealms.admin.chattag")) {
                completions.add("unlock");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                // List tags the player has unlocked
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    List<String> tags = new ArrayList<>();
                    tags.add("DEFAULT");

                    for (ChatTag tag : ChatTag.values()) {
                        if (tag != ChatTag.DEFAULT && ChatMechanics.hasTagUnlocked(player, tag)) {
                            tags.add(tag.name());
                        }
                    }

                    return tags.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            } else if (args[0].equalsIgnoreCase("unlock") && player.hasPermission("yakrealms.admin.chattag")) {
                // List online players
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("unlock") && player.hasPermission("yakrealms.admin.chattag")) {
            // List all tags
            return Arrays.stream(ChatTag.values())
                    .map(ChatTag::name)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
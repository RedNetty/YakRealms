package com.rednetty.server.core.commands.player;

import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.mounts.MountManager;
import com.rednetty.server.core.mechanics.player.mounts.type.MountType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to manage mounts
 */
public class MountCommand implements CommandExecutor {
    private final MountManager mountManager;

    public MountCommand() {
        this.mountManager = MountManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);

        if (args.length == 0) {
            // Display available mounts and usage
            showMountInfo(player, yakPlayer);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "summon":
                handleSummonCommand(player, args);
                break;
            case "dismount":
                handleDismountCommand(player);
                break;
            case "info":
                showMountInfo(player, yakPlayer);
                break;
            case "set":
                handleSetCommand(player, args);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                player.sendMessage(ChatColor.GRAY + "Usage: /mount [summon|dismount|info|set]");
                break;
        }

        return true;
    }

    private void handleSummonCommand(Player player, String[] args) {
        MountType mountType = null;

        if (args.length > 1) {
            try {
                mountType = MountType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid mount type: " + args[1]);
                return;
            }
        } else {
            // Use player's preferred mount type
            mountType = mountManager.getMountType(player);
        }

        if (mountType == MountType.NONE) {
            player.sendMessage(ChatColor.RED + "You don't have any mounts available.");
            return;
        }

        boolean success = mountManager.summonMount(player, mountType);
        if (!success) {
            player.sendMessage(ChatColor.RED + "Failed to summon mount. You may already have an active mount or be in an invalid location.");
        }
    }

    private void handleDismountCommand(Player player) {
        if (!mountManager.hasActiveMount(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't have an active mount to dismount from.");
            return;
        }

        boolean success = mountManager.dismountPlayer(player, true);
        if (!success) {
            player.sendMessage(ChatColor.RED + "Failed to dismount. You may not be on a mount.");
        }
    }

    private void showMountInfo(Player player, YakPlayer yakPlayer) {
        player.sendMessage(ChatColor.GOLD + "=== Mount Information ===");

        // Show active mount
        if (mountManager.hasActiveMount(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Active Mount: " +
                    ChatColor.WHITE + mountManager.getActiveMount(player.getUniqueId()).getType().name());
        } else {
            player.sendMessage(ChatColor.YELLOW + "Active Mount: " + ChatColor.GRAY + "None");
        }

        // Show available mount types
        player.sendMessage(ChatColor.YELLOW + "Available Mounts:");

        // Check horse mount
        int horseTier = yakPlayer != null ? yakPlayer.getHorseTier() : 0;
        if (horseTier >= 2) {
            String horseName = mountManager.getConfig().getHorseStats(horseTier).getName();
            player.sendMessage(ChatColor.YELLOW + "- Horse: " +
                    ChatColor.valueOf(mountManager.getConfig().getHorseStats(horseTier).getColor()) +
                    horseName + ChatColor.GRAY + " (Tier " + horseTier + ")");
        } else {
            player.sendMessage(ChatColor.YELLOW + "- Horse: " + ChatColor.GRAY + "None");
        }

        // Check elytra mount
        if (player.hasPermission("yakrp.mount.elytra")) {
            player.sendMessage(ChatColor.YELLOW + "- Elytra: " + ChatColor.AQUA + "Elytra Mount");
        } else {
            player.sendMessage(ChatColor.YELLOW + "- Elytra: " + ChatColor.GRAY + "None");
        }

        // Show command usage
        player.sendMessage(ChatColor.GOLD + "=== Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/mount summon [type]" + ChatColor.GRAY + " - Summon a mount");
        player.sendMessage(ChatColor.YELLOW + "/mount dismount" + ChatColor.GRAY + " - Dismount from your current mount");
        player.sendMessage(ChatColor.YELLOW + "/mount info" + ChatColor.GRAY + " - Show this information");

        if (player.hasPermission("yakrp.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/mount set <player> <type> <tier>" +
                    ChatColor.GRAY + " - Set a player's mount tier (admin only)");
        }
    }

    private void handleSetCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrp.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /mount set <player> <type> <tier>");
            return;
        }

        String targetName = args[1];
        Player targetPlayer = player.getServer().getPlayer(targetName);

        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        String typeStr = args[2].toUpperCase();
        int tier;

        try {
            tier = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid tier: " + args[3]);
            return;
        }

        if (typeStr.equals("HORSE")) {
            if (tier < 2 || tier > 5) {
                player.sendMessage(ChatColor.RED + "Horse tier must be between 2 and 5.");
                return;
            }

            mountManager.setHorseTier(targetPlayer, tier);
            player.sendMessage(ChatColor.GREEN + "Set " + targetPlayer.getName() + "'s horse tier to " + tier);
            targetPlayer.sendMessage(ChatColor.GREEN + "Your horse tier has been set to " + tier);
        } else {
            player.sendMessage(ChatColor.RED + "Invalid mount type: " + typeStr);
            player.sendMessage(ChatColor.GRAY + "Valid types: HORSE");
        }
    }
}
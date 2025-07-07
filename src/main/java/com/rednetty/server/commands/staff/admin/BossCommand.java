package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.mechanics.world.mobs.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for managing world bosses
 */
public class BossCommand implements CommandExecutor, TabCompleter {
    private final MobManager mobManager;

    /**
     * Constructor
     *
     * @param mobManager The mob manager
     */
    public BossCommand(MobManager mobManager) {
        this.mobManager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.boss")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "spawn":
                return handleSpawnCommand(sender, args);
            case "remove":
                return handleRemoveCommand(sender);
            case "info":
                return handleInfoCommand(sender);
            case "list":
                return handleListCommand(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCmd);
                sendHelp(sender);
                return true;
        }
    }

    /**
     * Handle the spawn boss subcommand
     */
    private boolean handleSpawnCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

/*        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /boss spawn <type>");
            player.sendMessage(ChatColor.GRAY + "Available boss types: " +
                    String.join(", ", mobManager.getWorldBossTypes()));
            return true;
        }

        String bossType = args[1].toLowerCase();

        // Check if valid boss type
        if (!mobManager.isValidWorldBossType(bossType)) {
            player.sendMessage(ChatColor.RED + "Invalid boss type: " + bossType);
            player.sendMessage(ChatColor.GRAY + "Available boss types: " +
                    String.join(", ", mobManager.getWorldBossTypes()));
            return true;
        }

        // Spawn the boss
        if (mobManager.spawnWorldBoss(bossType, player.getLocation())) {
            player.sendMessage(ChatColor.GREEN + "Successfully spawned " + bossType + " at your location!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to spawn boss. A world boss might already be active.");
        }*/

        return true;
    }

    /**
     * Handle the remove boss subcommand
     */
    private boolean handleRemoveCommand(CommandSender sender) {
/*        if (mobManager.removeActiveBoss()) {
            sender.sendMessage(ChatColor.GREEN + "Active boss has been removed!");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "No active boss to remove.");
        }*/
        return true;
    }

    /**
     * Handle the boss info subcommand
     */
    private boolean handleInfoCommand(CommandSender sender) {
/*        if (!mobManager.hasActiveBoss()) {
            sender.sendMessage(ChatColor.YELLOW + "No world boss is currently active.");
            return true;
        }

        mobManager.displayActiveBossInfo(sender);*/
        return true;
    }

    /**
     * Handle the list bosses subcommand
     */
    private boolean handleListCommand(CommandSender sender) {
/*        List<String> bossTypes = mobManager.getWorldBossTypes();

        sender.sendMessage(ChatColor.GOLD + "===== Available World Boss Types =====");
        for (String bossType : bossTypes) {
            sender.sendMessage(ChatColor.YELLOW + "- " + bossType);
        }*/

        return true;
    }

    /**
     * Send help message
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Boss Command Help =====");
        sender.sendMessage(ChatColor.YELLOW + "/boss spawn <type>" + ChatColor.GRAY + " - Spawn a world boss");
        sender.sendMessage(ChatColor.YELLOW + "/boss remove" + ChatColor.GRAY + " - Remove the active world boss");
        sender.sendMessage(ChatColor.YELLOW + "/boss info" + ChatColor.GRAY + " - Get info about the active world boss");
        sender.sendMessage(ChatColor.YELLOW + "/boss list" + ChatColor.GRAY + " - List all available boss types");
        sender.sendMessage(ChatColor.YELLOW + "/boss help" + ChatColor.GRAY + " - Show this help message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "remove", "info", "list", "help").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

    /*    if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return mobManager.getWorldBossTypes().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }*/

        return Arrays.asList();
    }
}
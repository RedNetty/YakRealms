package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.mobs.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for toggling spawner activation
 */
public class ToggleSpawnersCommand implements CommandExecutor, TabCompleter {
    private final MobManager mobManager;

    /**
     * Constructor
     *
     * @param mobManager The mob manager
     */
    public ToggleSpawnersCommand(MobManager mobManager) {
        this.mobManager = mobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.togglespawners")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length > 0) {
            String option = args[0].toLowerCase();

            if (option.equals("on") || option.equals("enable")) {
                mobManager.setSpawnersEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Spawners have been enabled!");
                return true;
            }

            if (option.equals("off") || option.equals("disable")) {
                mobManager.setSpawnersEnabled(false);
                sender.sendMessage(ChatColor.RED + "Spawners have been disabled!");
                return true;
            }

            if (option.equals("status")) {
                boolean enabled = mobManager.areSpawnersEnabled();
                sender.sendMessage(ChatColor.YELLOW + "Spawners are currently " +
                        (enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
                return true;
            }

            sender.sendMessage(ChatColor.RED + "Invalid option: " + option);
            sendUsage(sender);
            return true;
        }

        // Toggle if no arguments
        boolean newState = !mobManager.areSpawnersEnabled();
        mobManager.setSpawnersEnabled(newState);

        sender.sendMessage(ChatColor.YELLOW + "Spawners have been " +
                (newState ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));

        return true;
    }

    /**
     * Send usage message
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== ToggleSpawners Command Usage =====");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners" + ChatColor.GRAY + " - Toggle spawners on/off");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners on|enable" + ChatColor.GRAY + " - Enable spawners");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners off|disable" + ChatColor.GRAY + " - Disable spawners");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners status" + ChatColor.GRAY + " - Check spawner status");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("on", "off", "enable", "disable", "status").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
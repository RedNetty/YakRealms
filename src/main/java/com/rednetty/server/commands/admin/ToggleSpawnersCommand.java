package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.mobs.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced command for toggling spawner activation with improved functionality and statistics
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

            switch (option) {
                case "on":
                case "enable":
                    setSpawnersEnabled(sender, true);
                    return true;

                case "off":
                case "disable":
                    setSpawnersEnabled(sender, false);
                    return true;

                case "status":
                case "info":
                    showDetailedStatus(sender);
                    return true;

                case "stats":
                    showSpawnerStatistics(sender);
                    return true;

                case "help":
                    sendUsage(sender);
                    return true;

                default:
                    sender.sendMessage(ChatColor.RED + "Invalid option: " + option);
                    sendUsage(sender);
                    return true;
            }
        }

        // Toggle if no arguments
        boolean newState = !mobManager.areSpawnersEnabled();
        setSpawnersEnabled(sender, newState);

        return true;
    }

    /**
     * Set spawners enabled/disabled with enhanced feedback
     */
    private void setSpawnersEnabled(CommandSender sender, boolean enabled) {
        boolean wasEnabled = mobManager.areSpawnersEnabled();

        if (wasEnabled == enabled) {
            sender.sendMessage(ChatColor.YELLOW + "Spawners are already " +
                    (enabled ? "enabled" : "disabled") + "!");
            return;
        }

        mobManager.setSpawnersEnabled(enabled);

        String action = enabled ? "enabled" : "disabled";
        ChatColor color = enabled ? ChatColor.GREEN : ChatColor.RED;

        sender.sendMessage(color + "Spawners have been " + action + "!");

        // Show additional info
        if (enabled) {
            sender.sendMessage(ChatColor.GRAY + "Mobs will now spawn from configured spawners.");
        } else {
            sender.sendMessage(ChatColor.GRAY + "No new mobs will spawn until re-enabled.");
        }

        // Broadcast to admins if changed by a player
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String broadcastMessage = ChatColor.YELLOW + "[Admin] " + ChatColor.AQUA + player.getName() +
                    ChatColor.YELLOW + " has " + action + " mob spawners.";

            player.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("yakrealms.admin.togglespawners") && !p.equals(player))
                    .forEach(p -> p.sendMessage(broadcastMessage));
        }
    }

    /**
     * Show detailed status information
     */
    private void showDetailedStatus(CommandSender sender) {
        boolean enabled = mobManager.areSpawnersEnabled();
        ChatColor statusColor = enabled ? ChatColor.GREEN : ChatColor.RED;
        String statusText = enabled ? "ENABLED" : "DISABLED";

        sender.sendMessage(ChatColor.GOLD + "========== Spawner Status ==========");
        sender.sendMessage(ChatColor.YELLOW + "Status: " + statusColor + statusText);

        if (enabled) {
            sender.sendMessage(ChatColor.GREEN + "✓ Mobs can spawn from spawners");
            sender.sendMessage(ChatColor.GREEN + "✓ Spawner mechanics are active");
        } else {
            sender.sendMessage(ChatColor.RED + "✗ Mob spawning is disabled");
            sender.sendMessage(ChatColor.RED + "✗ Spawners will not create new mobs");
        }

        // Show spawner count
        int totalSpawners = mobManager.getAllSpawners().size();
        sender.sendMessage(ChatColor.YELLOW + "Total Spawners: " + ChatColor.WHITE + totalSpawners);

        if (totalSpawners > 0) {
            sender.sendMessage(ChatColor.GRAY + "Use '/togglespawners stats' for detailed statistics");
        }
    }

    /**
     * Show spawner statistics
     */
    private void showSpawnerStatistics(CommandSender sender) {
        int totalSpawners = mobManager.getAllSpawners().size();

        if (totalSpawners == 0) {
            sender.sendMessage(ChatColor.RED + "No spawners are currently configured.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "========== Spawner Statistics ==========");
        sender.sendMessage(ChatColor.YELLOW + "Total Spawners: " + ChatColor.WHITE + totalSpawners);
        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                (mobManager.areSpawnersEnabled() ?
                        ChatColor.GREEN + "Active" : ChatColor.RED + "Inactive"));

        // Count active mobs across all spawners
        int totalActiveMobs = 0;
        int activeSpawners = 0;

        for (var spawnerEntry : mobManager.getAllSpawners().entrySet()) {
            int mobCount = mobManager.getActiveMobCount(spawnerEntry.getKey());
            totalActiveMobs += mobCount;
            if (mobCount > 0) {
                activeSpawners++;
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "Active Spawners: " + ChatColor.WHITE + activeSpawners +
                ChatColor.GRAY + "/" + totalSpawners);
        sender.sendMessage(ChatColor.YELLOW + "Total Active Mobs: " + ChatColor.WHITE + totalActiveMobs);

        if (totalActiveMobs > 0) {
            double avgMobsPerActiveSpawner = activeSpawners > 0 ?
                    (double) totalActiveMobs / activeSpawners : 0;
            sender.sendMessage(ChatColor.YELLOW + "Avg Mobs/Active Spawner: " +
                    ChatColor.WHITE + String.format("%.1f", avgMobsPerActiveSpawner));
        }

        sender.sendMessage(ChatColor.GRAY + "Debug Mode: " +
                (mobManager.isDebugMode() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
    }

    /**
     * Send enhanced usage message
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== ToggleSpawners Command Usage ==========");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners" + ChatColor.GRAY + " - Toggle spawners on/off");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners on|enable" + ChatColor.GRAY + " - Enable spawners");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners off|disable" + ChatColor.GRAY + " - Disable spawners");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners status|info" + ChatColor.GRAY + " - Show detailed status");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners stats" + ChatColor.GRAY + " - Show spawner statistics");
        sender.sendMessage(ChatColor.YELLOW + "/togglespawners help" + ChatColor.GRAY + " - Show this help");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Permission: " + ChatColor.WHITE + "yakrealms.admin.togglespawners");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.togglespawners")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> options = Arrays.asList("on", "off", "enable", "disable", "status", "info", "stats", "help");
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
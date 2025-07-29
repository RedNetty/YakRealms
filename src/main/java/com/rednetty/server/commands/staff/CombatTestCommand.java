package com.rednetty.server.commands.staff;

import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.combat.pvp.ForceFieldManager;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
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
import java.util.logging.Logger;

/**
 * Test command for combat tagging system
 *
 * Usage:
 * /combattest tag [player] - Tag a player for combat
 * /combattest untag [player] - Remove combat tag from player
 * /combattest status [player] - Check combat status
 * /combattest forcefield [player] - Update force field for player
 * /combattest info [player] - Get detailed combat info
 */
public class CombatTestCommand implements CommandExecutor, TabCompleter {
    private static final Logger logger = Logger.getLogger(CombatTestCommand.class.getName());

    private final CombatLogoutMechanics combatLogoutMechanics;
    private final AlignmentMechanics alignmentMechanics;
    private final ForceFieldManager forceFieldManager;
    private final ModerationMechanics moderationMechanics;

    public CombatTestCommand() {
        this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
        this.alignmentMechanics = AlignmentMechanics.getInstance();
        this.forceFieldManager = ForceFieldManager.getInstance();
        this.moderationMechanics = ModerationMechanics.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permissions - only allow DEV, MANAGER, or GM ranks
        if (sender instanceof Player) {
            Player player = (Player) sender;
            Rank rank = moderationMechanics.getPlayerRank(player.getUniqueId());
            if (rank != Rank.DEV && rank != Rank.MANAGER && rank != Rank.GM) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "tag":
                return handleTag(sender, args);
            case "untag":
                return handleUntag(sender, args);
            case "status":
                return handleStatus(sender, args);
            case "forcefield":
            case "ff":
                return handleForceField(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "help":
                sendUsage(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleTag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /combattest tag <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found or not online.");
            return true;
        }

        try {
            // Tag the player for combat
            combatLogoutMechanics.markCombatTagged(target);

            // Verify the tag worked
            boolean isTagged = combatLogoutMechanics.isPlayerTagged(target.getUniqueId());
            int timeRemaining = combatLogoutMechanics.getCombatTimeRemaining(target);

            if (isTagged) {
                sender.sendMessage(ChatColor.GREEN + "✓ Successfully tagged " + target.getName() + " for combat");
                sender.sendMessage(ChatColor.GRAY + "  Time remaining: " + timeRemaining + " seconds");

                // Check force field status
                boolean hasForceFields = forceFieldManager.hasActiveForceFields(target);
                int fieldCount = forceFieldManager.getActiveForceFieldCount(target);
                sender.sendMessage(ChatColor.GRAY + "  Force fields: " + (hasForceFields ?
                        ChatColor.BLUE + "Active (" + fieldCount + " blocks)" :
                        ChatColor.RED + "None"));

                target.sendMessage(ChatColor.YELLOW + "[TEST] You have been tagged for combat by " + sender.getName());

                logger.info("Combat test: " + sender.getName() + " tagged " + target.getName() + " for combat");
            } else {
                sender.sendMessage(ChatColor.RED + "✗ Failed to tag " + target.getName() + " for combat");
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error tagging player: " + e.getMessage());
            logger.severe("Error in combat test tag command: " + e.getMessage());
        }

        return true;
    }

    private boolean handleUntag(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /combattest untag <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found or not online.");
            return true;
        }

        try {
            boolean wasTagged = combatLogoutMechanics.isPlayerTagged(target.getUniqueId());

            // Clear the combat tag
            combatLogoutMechanics.clearCombatTag(target);

            // Verify the tag was cleared
            boolean isStillTagged = combatLogoutMechanics.isPlayerTagged(target.getUniqueId());

            if (wasTagged && !isStillTagged) {
                sender.sendMessage(ChatColor.GREEN + "✓ Successfully removed combat tag from " + target.getName());

                // Check force field status after clearing
                boolean hasForceFields = forceFieldManager.hasActiveForceFields(target);
                sender.sendMessage(ChatColor.GRAY + "  Force fields: " + (hasForceFields ?
                        ChatColor.BLUE + "Still active (chaotic)" :
                        ChatColor.GREEN + "Cleared"));

                target.sendMessage(ChatColor.GREEN + "[TEST] Your combat tag has been cleared by " + sender.getName());

                logger.info("Combat test: " + sender.getName() + " removed combat tag from " + target.getName());
            } else if (!wasTagged) {
                sender.sendMessage(ChatColor.YELLOW + "Player " + target.getName() + " was not combat tagged");
            } else {
                sender.sendMessage(ChatColor.RED + "✗ Failed to remove combat tag from " + target.getName());
            }

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error removing combat tag: " + e.getMessage());
            logger.severe("Error in combat test untag command: " + e.getMessage());
        }

        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        Player target;

        if (args.length < 2) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /combattest status <player>");
                return true;
            }
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found or not online.");
                return true;
            }
        }

        try {
            boolean isTagged = combatLogoutMechanics.isPlayerTagged(target.getUniqueId());
            int timeRemaining = combatLogoutMechanics.getCombatTimeRemaining(target);
            boolean isCombatLoggingOut = combatLogoutMechanics.isCombatLoggingOut(target);
            boolean hasActiveCombatLogout = combatLogoutMechanics.hasActiveCombatLogout(target.getUniqueId());

            sender.sendMessage(ChatColor.GOLD + "=== Combat Status for " + target.getName() + " ===");
            sender.sendMessage(ChatColor.GRAY + "Combat Tagged: " + (isTagged ?
                    ChatColor.RED + "YES (" + timeRemaining + "s)" :
                    ChatColor.GREEN + "NO"));
            sender.sendMessage(ChatColor.GRAY + "Combat Logging Out: " + (isCombatLoggingOut ?
                    ChatColor.RED + "YES" :
                    ChatColor.GREEN + "NO"));
            sender.sendMessage(ChatColor.GRAY + "Has Active Combat Logout: " + (hasActiveCombatLogout ?
                    ChatColor.RED + "YES" :
                    ChatColor.GREEN + "NO"));

            // Force field info
            boolean hasForceFields = forceFieldManager.hasActiveForceFields(target);
            int fieldCount = forceFieldManager.getActiveForceFieldCount(target);
            sender.sendMessage(ChatColor.GRAY + "Force Fields: " + (hasForceFields ?
                    ChatColor.BLUE + "Active (" + fieldCount + " blocks)" :
                    ChatColor.GREEN + "None"));

            // Alignment info
            String alignmentString = AlignmentMechanics.getAlignmentString(target);
            int alignmentTime = AlignmentMechanics.getAlignmentTime(target);
            sender.sendMessage(ChatColor.GRAY + "Alignment: " + ChatColor.translateAlternateColorCodes('&', alignmentString) +
                    (alignmentTime > 0 ? " (" + alignmentTime + "s)" : ""));

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error getting combat status: " + e.getMessage());
            logger.severe("Error in combat test status command: " + e.getMessage());
        }

        return true;
    }

    private boolean handleForceField(CommandSender sender, String[] args) {
        Player target;

        if (args.length < 2) {
            if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /combattest forcefield <player>");
                return true;
            }
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found or not online.");
                return true;
            }
        }

        try {
            int beforeCount = forceFieldManager.getActiveForceFieldCount(target);

            // Force update the force field
            forceFieldManager.forceUpdatePlayerForceField(target);

            int afterCount = forceFieldManager.getActiveForceFieldCount(target);
            boolean hasFields = forceFieldManager.hasActiveForceFields(target);

            sender.sendMessage(ChatColor.GREEN + "✓ Updated force field for " + target.getName());
            sender.sendMessage(ChatColor.GRAY + "  Before: " + beforeCount + " blocks");
            sender.sendMessage(ChatColor.GRAY + "  After: " + afterCount + " blocks");
            sender.sendMessage(ChatColor.GRAY + "  Status: " + (hasFields ?
                    ChatColor.BLUE + "Active" :
                    ChatColor.GREEN + "None"));

            target.sendMessage(ChatColor.BLUE + "[TEST] Your force field has been updated by " + sender.getName());

            logger.info("Combat test: " + sender.getName() + " updated force field for " + target.getName());

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error updating force field: " + e.getMessage());
            logger.severe("Error in combat test forcefield command: " + e.getMessage());
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Show system info
            sender.sendMessage(ChatColor.GOLD + "=== Combat System Info ===");
            sender.sendMessage(ChatColor.GRAY + "Total Combat Logouts: " + combatLogoutMechanics.getTotalCombatLogouts());
            sender.sendMessage(ChatColor.GRAY + "Successful Processes: " + combatLogoutMechanics.getSuccessfulProcesses());
            sender.sendMessage(ChatColor.GRAY + "Failed Processes: " + combatLogoutMechanics.getFailedProcesses());
            sender.sendMessage(ChatColor.GRAY + "Active Combat Tags: " + combatLogoutMechanics.getActiveCombatTags());
            sender.sendMessage(ChatColor.GRAY + "Active Combat Logouts: " + combatLogoutMechanics.getActiveCombatLogouts());
            sender.sendMessage(ChatColor.GRAY + "Coordination Successes: " + combatLogoutMechanics.getCoordinationSuccesses());
            sender.sendMessage(ChatColor.GRAY + "Coordination Failures: " + combatLogoutMechanics.getCoordinationFailures());
            sender.sendMessage(ChatColor.GRAY + "System Healthy: " + (combatLogoutMechanics.isSystemHealthy() ?
                    ChatColor.GREEN + "YES" :
                    ChatColor.RED + "NO"));

            // Force field debug info
            var debugInfo = forceFieldManager.getDebugInfo();
            sender.sendMessage(ChatColor.GRAY + "Players with Force Fields: " + debugInfo.get("total_players_with_fields"));
            sender.sendMessage(ChatColor.GRAY + "Total Force Field Blocks: " + debugInfo.get("total_field_blocks"));
            sender.sendMessage(ChatColor.GRAY + "Update Task Running: " + (Boolean.TRUE.equals(debugInfo.get("update_task_running")) ?
                    ChatColor.GREEN + "YES" :
                    ChatColor.RED + "NO"));

        } else {
            // Show player-specific detailed info
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found or not online.");
                return true;
            }

            handleStatus(sender, args); // Reuse status command for detailed player info
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Combat Test Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/combattest tag <player>" + ChatColor.GRAY + " - Tag player for combat");
        sender.sendMessage(ChatColor.YELLOW + "/combattest untag <player>" + ChatColor.GRAY + " - Remove combat tag");
        sender.sendMessage(ChatColor.YELLOW + "/combattest status [player]" + ChatColor.GRAY + " - Check combat status");
        sender.sendMessage(ChatColor.YELLOW + "/combattest forcefield [player]" + ChatColor.GRAY + " - Update force field");
        sender.sendMessage(ChatColor.YELLOW + "/combattest info [player]" + ChatColor.GRAY + " - Get detailed info");
        sender.sendMessage(ChatColor.GRAY + "Note: Only DEV, MANAGER, and GM ranks can use this command");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Subcommands
            List<String> subCommands = Arrays.asList("tag", "untag", "status", "forcefield", "ff", "info", "help");
            String partial = args[0].toLowerCase();
            for (String subCmd : subCommands) {
                if (subCmd.startsWith(partial)) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2) {
            // Player names for commands that need them
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("tag") || subCommand.equals("untag") ||
                    subCommand.equals("status") || subCommand.equals("forcefield") ||
                    subCommand.equals("ff") || subCommand.equals("info")) {

                String partial = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}
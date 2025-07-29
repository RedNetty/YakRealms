package com.rednetty.server.commands.party;

import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import com.rednetty.server.utils.messaging.MessageUtil;
import com.rednetty.server.utils.sounds.SoundUtil;
import com.rednetty.server.utils.permissions.PermissionUtil;
import com.rednetty.server.utils.cooldowns.CooldownManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  Party Quit Command with unified utility integration and confirmation system
 * Uses MessageUtil, SoundUtil, PermissionUtil, and CooldownManager for consistency
 * Demonstrates the confirmation system for destructive actions
 */
public class PQuitCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;
    private static final String QUIT_ACTION = "party_quit";

    public PQuitCommand() {
        this.partyMechanics = PartyMechanics.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Ensure command is executed by a player
        if (!(sender instanceof Player)) {
            MessageUtil.sendError(null, "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!PermissionUtil.checkPartyPermissionWithMessage(player, "quit")) {
            return true;
        }

        // Check if player is in a party
        if (!partyMechanics.isInParty(player)) {
            MessageUtil.sendError(player, "You are not in a party!");
            MessageUtil.sendTip(player, "Join a party first with /pinvite <player> or /paccept");
            return true;
        }

        // Check cooldown
        if (!CooldownManager.checkPartyCooldown(player, "quit")) {
            return true;
        }

        // Handle confirmation argument
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            return handleConfirmedQuit(player);
        }

        // Validate command syntax
        if (args.length > 1) {
            sendUsageMessage(player);
            return true;
        }

        // Check if confirmation is required
        if (!CooldownManager.hasRecentConfirmation(player, QUIT_ACTION)) {
            requestConfirmation(player);
            return true;
        }

        // This shouldn't happen if confirmation system is working properly
        return handleConfirmedQuit(player);
    }

    /**
     * Request confirmation for leaving the party
     */
    private void requestConfirmation(Player player) {
        try {
            // Get party information for context
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            boolean isLeader = partyMechanics.isPartyLeader(player);

            MessageUtil.sendHeader(player, "LEAVE PARTY CONFIRMATION");

            // Show what they're leaving
            player.sendMessage(ChatColor.GRAY + "Party Size: " + ChatColor.WHITE + partyMembers.size() + " members");
            player.sendMessage(ChatColor.GRAY + "Your Role: " + ChatColor.WHITE +
                    (isLeader ? ChatColor.GOLD + "Leader" : "Member"));

            MessageUtil.sendBlankLine(player);

            // Warn about consequences
            MessageUtil.sendWarning(player, "Are you sure you want to leave your party?");

            if (isLeader) {
                if (partyMembers.size() > 1) {
                    player.sendMessage(ChatColor.RED + "⚠ As the leader, leaving will transfer leadership");
                    player.sendMessage(ChatColor.RED + "  to another party member automatically.");
                } else {
                    player.sendMessage(ChatColor.RED + "⚠ As the only member, the party will be disbanded.");
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "⚠ You will lose party bonuses and chat access.");
            }

            MessageUtil.sendBlankLine(player);

            // Show confirmation command
            MessageUtil.sendConfirmationRequest(player, "leave the party", "/pquit confirm");

            // Set confirmation requirement
            CooldownManager.setConfirmationRequired(player, QUIT_ACTION);

            // Play warning sound
            SoundUtil.playWarning(player);

            // Show alternatives
            showAlternatives(player);

        } catch (Exception e) {
            MessageUtil.sendError(player, "Error processing quit request.");
        }
    }

    /**
     * Handle confirmed quit action
     */
    private boolean handleConfirmedQuit(Player player) {
        // Check if confirmation is valid
        if (!CooldownManager.consumeConfirmation(player, QUIT_ACTION)) {
            MessageUtil.sendError(player, "Confirmation expired or invalid.");
            MessageUtil.sendTip(player, "Use /pquit again to get a new confirmation request.");
            return true;
        }

        // Get party information before leaving
        List<Player> partyMembers = partyMechanics.getPartyMembers(player);
        boolean wasLeader = partyMechanics.isPartyLeader(player);
        int partySize = partyMembers.size();

        // Attempt to leave the party
        boolean success = partyMechanics.leaveParty(player);

        if (success) {
            // Success feedback
            MessageUtil.sendSuccess(player, ChatColor.BOLD + "You have left the party.");

            // Provide context about what happened
            if (wasLeader && partySize > 1) {
                player.sendMessage(ChatColor.GRAY + "Leadership has been transferred to another member.");
            } else if (wasLeader && partySize == 1) {
                player.sendMessage(ChatColor.GRAY + "The party has been disbanded.");
            }

            MessageUtil.sendBlankLine(player);

            // Show what they can do now
            player.sendMessage(ChatColor.GRAY + "You can now:");
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/pinvite <player>" +
                    ChatColor.GRAY + " - Create a new party");
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/paccept" +
                    ChatColor.GRAY + " - Accept future invitations");
            player.sendMessage(ChatColor.GRAY + "• Continue your solo adventure");

            // Play leave sound
            SoundUtil.playPartyLeave(player);

            // Apply cooldown
            CooldownManager.applyPartyCooldown(player, "quit");

            // Send encouragement
            sendEncouragementMessage(player);

        } else {
            // Handle failure
            MessageUtil.sendError(player, "Failed to leave the party.");
            MessageUtil.sendTip(player, "Please try again or contact staff if the issue persists.");
        }

        return true;
    }

    /**
     * Send usage message with helpful context
     */
    private void sendUsageMessage(Player player) {
        MessageUtil.sendCommandUsageMultiple(player,
                new String[]{"/pquit", "/pquit confirm"},
                "Leaves your current party.");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "This action requires confirmation to prevent accidental use.");
    }

    /**
     * Show alternatives to leaving the party
     */
    private void showAlternatives(Player player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"),
                () -> {
                    if (player.isOnline()) {
                        MessageUtil.sendBlankLine(player);
                        player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.WHITE + "Consider these alternatives:");
                        player.sendMessage(ChatColor.GRAY + "  • Take a break but stay in party");
                        player.sendMessage(ChatColor.GRAY + "  • Ask party members about any issues");
                        player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/p help" +
                                ChatColor.GRAY + " to learn more party features");
                        player.sendMessage(ChatColor.GRAY + "  • Transfer leadership with " +
                                ChatColor.YELLOW + "/ppromote <player>");

                        MessageUtil.sendBlankLine(player);
                        MessageUtil.sendTip(player, "Parties are often more fun once you get used to them!");
                    }
                },
                60L // 3 second delay
        );
    }

    /**
     * Send encouraging message after leaving
     */
    private void sendEncouragementMessage(Player player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"),
                () -> {
                    if (player.isOnline()) {
                        MessageUtil.sendBlankLine(player);

                        // Randomize encouragement messages
                        String[] encouragements = {
                                "Solo adventures await! Don't forget you can always party up again. 🌟",
                                "Sometimes a change of pace is exactly what you need! 🔄",
                                "The world is full of new people to meet and party with! 🌍",
                                "Take your time - the right party will come along when you're ready! ⏰",
                                "Every ending is a new beginning. Happy adventuring! 🎯"
                        };

                        int randomIndex = (int) (Math.random() * encouragements.length);
                        player.sendMessage(ChatColor.GRAY + encouragements[randomIndex]);

                        SoundUtil.playNotification(player);
                    }
                },
                100L // 5 second delay
        );
    }

    /**
     * Show party statistics before leaving (if available)
     */
    private void showPartyStatistics(Player player) {
        try {
            // This would integrate with party statistics if they exist
            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "📊 " + ChatColor.WHITE + "Your Party Stats:");
            player.sendMessage(ChatColor.GRAY + "  • Time in party: " + ChatColor.YELLOW + "Coming Soon");
            player.sendMessage(ChatColor.GRAY + "  • Messages sent: " + ChatColor.YELLOW + "Coming Soon");
            player.sendMessage(ChatColor.GRAY + "  • Combat assists: " + ChatColor.YELLOW + "Coming Soon");
            player.sendMessage(ChatColor.GRAY + "  • Items shared: " + ChatColor.YELLOW + "Coming Soon");

        } catch (Exception e) {
            // Ignore errors in statistics display
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        // Check permission
        if (!PermissionUtil.hasPartyPermission(player, "quit")) {
            return completions;
        }

        // Check if player is in a party
        if (!partyMechanics.isInParty(player)) {
            return completions;
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();

            // Suggest confirm if they need confirmation
            if (!CooldownManager.hasRecentConfirmation(player, QUIT_ACTION)) {
                if ("confirm".startsWith(input)) {
                    completions.add("confirm");
                }
            }
        }

        return completions;
    }
}
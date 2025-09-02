package com.rednetty.server.core.commands.party;

import com.rednetty.server.core.mechanics.player.social.party.PartyMechanics;
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
import java.util.List;

/**
 *  Party Accept Command with unified utility integration
 * Uses MessageUtil, SoundUtil, PermissionUtil, and CooldownManager for consistency
 */
public class PAcceptCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    public PAcceptCommand() {
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
        if (!PermissionUtil.checkPartyPermissionWithMessage(player, "accept")) {
            return true;
        }

        // Check cooldown
        if (!CooldownManager.checkPartyCooldown(player, "accept")) {
            return true;
        }

        // Validate command syntax
        if (args.length != 0) {
            sendUsageMessage(player);
            return true;
        }

        // Check if player already in a party
        if (partyMechanics.isInParty(player)) {
            MessageUtil.sendError(player, "You are already in a party!");
            MessageUtil.sendTip(player, "Leave your current party first with /pquit");
            return true;
        }

        // Check if player has a pending invitation
        if (!partyMechanics.hasPendingInvite(player)) {
            MessageUtil.sendError(player, "You don't have any pending party invitations.");
            MessageUtil.sendTip(player, "Ask someone to invite you with /pinvite <your name>");
            sendAlternativeOptions(player);
            return true;
        }

        // Attempt to accept party invitation
        boolean success = partyMechanics.acceptPartyInvite(player);

        if (success) {
            // Success feedback with  effects
            MessageUtil.sendSuccess(player, ChatColor.BOLD + "Party invitation accepted!");

            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "Welcome to the party! Here's what you can do:");
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p <message>" + ChatColor.GRAY + " - Chat with party members");
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " - View party information");
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p help" + ChatColor.GRAY + " - See all party commands");
            MessageUtil.sendBlankLine(player);

            // Play success sound
            SoundUtil.playPartyJoin(player);

            // Apply cooldown
            CooldownManager.applyPartyCooldown(player, "accept");

            // Provide helpful tips for new party members
            sendPartyTips(player);
        } else {
            // Handle failure case (invitation might have expired)
            MessageUtil.sendError(player, "Failed to accept party invitation.");
            MessageUtil.sendTip(player, "The invitation may have expired or been cancelled.");

            // Suggest alternatives
            sendAlternativeOptions(player);
        }

        return true;
    }

    /**
     * Send  usage message with context-aware help
     */
    private void sendUsageMessage(Player player) {
        MessageUtil.sendCommandUsage(player, "/paccept", "Accepts a pending party invitation.");

        MessageUtil.sendBlankLine(player);

        // Check if player has a pending invite for more helpful feedback
        if (!partyMechanics.hasPendingInvite(player)) {
            MessageUtil.sendTip(player, "You don't currently have a pending party invitation.");
            player.sendMessage(ChatColor.GRAY + "Party invitations expire automatically after " +
                    partyMechanics.getInviteExpiryTimeSeconds() + " seconds.");
        } else {
            MessageUtil.sendTip(player, "Simply type /paccept (with no arguments) to join the party.");
        }
    }

    /**
     * Provide helpful tips for new party members
     */
    private void sendPartyTips(Player player) {
        // Delay tips slightly so they don't overwhelm
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"),
                () -> {
                    if (player.isOnline()) {
                        MessageUtil.sendHeader(player, "PARTY TIPS");

                        player.sendMessage(ChatColor.GRAY + "🎯 " + ChatColor.WHITE + "Party Benefits:");
                        player.sendMessage(ChatColor.GRAY + "  • Share experience and combat bonuses");
                        player.sendMessage(ChatColor.GRAY + "  • Coordinate attacks and strategies");
                        player.sendMessage(ChatColor.GRAY + "  • Get help when in trouble");

                        MessageUtil.sendBlankLine(player);

                        player.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.WHITE + "Communication:");
                        player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "@i@" + ChatColor.GRAY + " in party chat to show items");
                        player.sendMessage(ChatColor.GRAY + "  • Party chat is only visible to party members");
                        player.sendMessage(ChatColor.GRAY + "  • Use " + ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " to see who's nearby");

                        MessageUtil.sendBlankLine(player);
                        MessageUtil.sendTip(player, "Type /p help anytime to see all available party commands!");

                        SoundUtil.playNotification(player);
                    }
                },
                60L // 3 second delay
        );
    }

    /**
     * Suggest alternative options when accept fails or no invitation
     */
    private void sendAlternativeOptions(Player player) {
        MessageUtil.sendBlankLine(player);
        player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.WHITE + "Other options:");
        player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/pinvite <player>" + ChatColor.GRAY + " - Create your own party");
        player.sendMessage(ChatColor.GRAY + "  • Ask friends to invite you to their party");
        player.sendMessage(ChatColor.GRAY + "  • Check if you missed any invitation messages");

        MessageUtil.sendBlankLine(player);
        MessageUtil.sendTip(player, "Parties are great for exploring and combat together!");
    }

    /**
     * Show current party invitation status
     */
    private void showInvitationStatus(Player player) {
        try {
            if (partyMechanics.hasPendingInvite(player)) {
                String inviterName = partyMechanics.getPendingInviterName(player);
                long timeLeft = partyMechanics.getInviteTimeRemaining(player);

                MessageUtil.sendInfo(player, "You have a pending invitation from " +
                        ChatColor.YELLOW + inviterName + ChatColor.BLUE + "!");

                if (timeLeft > 0) {
                    String timeString = CooldownManager.formatCooldownTime(timeLeft);
                    player.sendMessage(ChatColor.GRAY + "Invitation expires in: " + ChatColor.YELLOW + timeString);
                }

                MessageUtil.sendBlankLine(player);
                player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "/paccept" + ChatColor.GRAY + " - Join the party");
                player.sendMessage(ChatColor.GRAY + "• " + ChatColor.RED + "/pdecline" + ChatColor.GRAY + " - Decline invitation");

            } else {
                MessageUtil.sendInfo(player, "You don't have any pending party invitations.");
                sendAlternativeOptions(player);
            }

        } catch (Exception e) {
            MessageUtil.sendError(player, "Unable to check invitation status.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        // This command takes no arguments, but we can provide helpful hints
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // Check permission
            if (!PermissionUtil.hasPartyPermission(player, "accept")) {
                return completions;
            }

            // If they're typing arguments, suggest they don't need any
            if (args.length > 0) {
                completions.add(""); // Empty suggestion to indicate no arguments needed
            }
        }

        return completions;
    }
}
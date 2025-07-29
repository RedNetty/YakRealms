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
import java.util.List;

/**
 *  Party Decline Command with unified utility integration
 * Uses MessageUtil, SoundUtil, PermissionUtil, and CooldownManager for consistency
 */
public class PDeclineCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    public PDeclineCommand() {
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
        if (!PermissionUtil.checkPartyPermissionWithMessage(player, "decline")) {
            return true;
        }

        // Check cooldown
        if (!CooldownManager.checkPartyCooldown(player, "decline")) {
            return true;
        }

        // Validate command syntax
        if (args.length != 0) {
            sendUsageMessage(player);
            return true;
        }

        // Check if player is already in a party
        if (partyMechanics.isInParty(player)) {
            MessageUtil.sendError(player, "You are already in a party!");
            MessageUtil.sendTip(player, "You cannot decline invitations while in a party. Use /pquit to leave first.");
            return true;
        }

        // Check if player has a pending invitation
        if (!partyMechanics.hasPendingInvite(player)) {
            MessageUtil.sendError(player, "You don't have any pending party invitations to decline.");
            MessageUtil.sendTip(player, "Party invitations expire automatically after " +
                    partyMechanics.getInviteExpiryTimeSeconds() + " seconds.");
            sendAlternativeOptions(player);
            return true;
        }

        // Get inviter information before declining
        String inviterName = partyMechanics.getPendingInviterName(player);

        // Attempt to decline party invitation
        boolean success = partyMechanics.declinePartyInvite(player);

        if (success) {
            // Success feedback with polite messaging
            MessageUtil.sendWarning(player, ChatColor.BOLD + "Party invitation declined.");

            if (inviterName != null && !inviterName.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Politely declined " + ChatColor.YELLOW + inviterName +
                        ChatColor.GRAY + "'s party invitation.");
            }

            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "That's perfectly fine! You can:");
            player.sendMessage(ChatColor.GRAY + "• Accept future invitations when you're ready");
            player.sendMessage(ChatColor.GRAY + "• Create your own party with " + ChatColor.YELLOW + "/pinvite <player>");
            player.sendMessage(ChatColor.GRAY + "• Continue adventuring solo");

            // Play neutral sound
            SoundUtil.playWarning(player);

            // Apply cooldown
            CooldownManager.applyPartyCooldown(player, "decline");

            // Provide helpful alternatives
            sendAlternativeOptions(player);

            // Send encouragement message
            sendEncouragementMessage(player);

        } else {
            // Handle failure case (invitation might have already expired)
            MessageUtil.sendError(player, "Failed to decline party invitation.");
            MessageUtil.sendTip(player, "The invitation may have already expired or been cancelled.");
        }

        return true;
    }

    /**
     * Send  usage message with context-aware help
     */
    private void sendUsageMessage(Player player) {
        MessageUtil.sendCommandUsage(player, "/pdecline", "Declines a pending party invitation.");

        MessageUtil.sendBlankLine(player);

        // Check if player has a pending invite for more helpful feedback
        if (!partyMechanics.hasPendingInvite(player)) {
            MessageUtil.sendTip(player, "You don't currently have a pending party invitation.");
            player.sendMessage(ChatColor.GRAY + "Party invitations expire automatically after " +
                    partyMechanics.getInviteExpiryTimeSeconds() + " seconds.");
        } else {
            String inviterName = partyMechanics.getPendingInviterName(player);
            if (inviterName != null) {
                MessageUtil.sendTip(player, "You have a pending invitation from " + inviterName + ".");
                player.sendMessage(ChatColor.GRAY + "Simply type " + ChatColor.YELLOW + "/pdecline" +
                        ChatColor.GRAY + " (with no arguments) to decline it.");
            }
        }
    }

    /**
     * Suggest alternative options when declining
     */
    private void sendAlternativeOptions(Player player) {
        // Delay this message slightly so it doesn't overwhelm
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"),
                () -> {
                    if (player.isOnline()) {
                        MessageUtil.sendBlankLine(player);
                        player.sendMessage(ChatColor.GRAY + "🎯 " + ChatColor.WHITE + "Other ways to enjoy YakRealms:");
                        player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/pinvite <player>" +
                                ChatColor.GRAY + " - Start your own party");
                        player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/msg <player>" +
                                ChatColor.GRAY + " - Chat with friends privately");
                        player.sendMessage(ChatColor.GRAY + "  • Explore solo and meet new players");
                        player.sendMessage(ChatColor.GRAY + "  • Ask in chat if anyone wants to party up");

                        MessageUtil.sendBlankLine(player);
                        MessageUtil.sendTip(player, "You can always change your mind and create or join a party later!");
                    }
                },
                40L // 2 second delay
        );
    }

    /**
     * Send an encouraging message to keep the player engaged
     */
    private void sendEncouragementMessage(Player player) {
        // Send after alternatives
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"),
                () -> {
                    if (player.isOnline()) {
                        MessageUtil.sendBlankLine(player);

                        // Randomize encouragement messages
                        String[] encouragements = {
                                "Solo adventures can be just as exciting! 🗡️",
                                "Sometimes the best discoveries are made alone! 🔍",
                                "Take your time - the right party will come along! ⏰",
                                "Independent explorers often become the strongest! 💪",
                                "Every great adventure starts with a single step! 👣"
                        };

                        int randomIndex = (int) (Math.random() * encouragements.length);
                        player.sendMessage(ChatColor.GRAY + encouragements[randomIndex]);

                        SoundUtil.playNotification(player);
                    }
                },
                80L // 4 second delay
        );
    }

    /**
     * Show current party invitation status with detailed information
     */
    private void showInvitationStatus(Player player) {
        try {
            if (partyMechanics.hasPendingInvite(player)) {
                String inviterName = partyMechanics.getPendingInviterName(player);
                long timeLeft = partyMechanics.getInviteTimeRemaining(player);

                MessageUtil.sendHeader(player, "PARTY INVITATION");

                player.sendMessage(ChatColor.GRAY + "From: " + ChatColor.YELLOW + inviterName);

                if (timeLeft > 0) {
                    String timeString = CooldownManager.formatCooldownTime(timeLeft);
                    player.sendMessage(ChatColor.GRAY + "Expires in: " + ChatColor.YELLOW + timeString);
                }

                MessageUtil.sendBlankLine(player);
                player.sendMessage(ChatColor.GRAY + "Your options:");
                player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "/paccept" + ChatColor.GRAY + " - Join " + inviterName + "'s party");
                player.sendMessage(ChatColor.GRAY + "• " + ChatColor.RED + "/pdecline" + ChatColor.GRAY + " - Politely decline the invitation");
                player.sendMessage(ChatColor.GRAY + "• Wait for it to expire automatically");

                MessageUtil.sendBlankLine(player);
                MessageUtil.sendTip(player, "Take your time deciding - there's no pressure!");

            } else {
                MessageUtil.sendInfo(player, "You don't have any pending party invitations.");
                sendAlternativeOptions(player);
            }

        } catch (Exception e) {
            MessageUtil.sendError(player, "Unable to check invitation status.");
        }
    }

    /**
     * Handle player feedback and suggestions
     */
    private void handlePlayerFeedback(Player player) {
        // This could be expanded to collect anonymous feedback about why players decline invitations
        // For now, just provide helpful information

        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.Bukkit.getPluginManager().getPlugin("YakRealms"),
                () -> {
                    if (player.isOnline()) {
                        MessageUtil.sendBlankLine(player);
                        player.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.WHITE + "Feedback:");
                        player.sendMessage(ChatColor.GRAY + "If you're having issues with party invitations,");
                        player.sendMessage(ChatColor.GRAY + "feel free to ask staff for help or report bugs!");
                    }
                },
                120L // 6 second delay
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        // This command takes no arguments, but we can provide helpful hints
        if (sender instanceof Player) {
            Player player = (Player) sender;

            // Check permission
            if (!PermissionUtil.hasPartyPermission(player, "decline")) {
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
package com.rednetty.server.commands.party;

import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 *  Party Decline Command with comprehensive error handling and user feedback
 * Integrates fully with the advanced PartyMechanics system
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
            sender.sendMessage(ChatColor.RED + "❌ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Validate command syntax
        if (args.length != 0) {
            sendUsageMessage(player);
            return true;
        }

        // Attempt to decline party invitation
        boolean success = partyMechanics.declinePartyInvite(player);

        if (success) {
            // Success feedback with polite messaging
            player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "Party invitation declined.");
            player.sendMessage(ChatColor.GRAY + "You can always accept future invitations or create your own party.");

            // Play neutral sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);

            // Provide helpful alternatives
            sendAlternativeOptions(player);
        }
        // Error messages are already handled by PartyMechanics.declinePartyInvite()

        return true;
    }

    /**
     * Send  usage message with context-aware help
     */
    private void sendUsageMessage(Player player) {
        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/pdecline");
        player.sendMessage(ChatColor.GRAY + "Declines a pending party invitation.");

        // Check if player has a pending invite for more helpful feedback
        if (!partyMechanics.isInParty(player)) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.ITALIC + "You don't currently have a pending party invitation.");
            player.sendMessage(ChatColor.GRAY + "Party invitations expire automatically after "
                    + partyMechanics.getInviteExpiryTimeSeconds() + " seconds.");
        }

        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    /**
     * Send alternative options to players who decline invitations
     */
    private void sendAlternativeOptions(Player player) {
        // Add a small delay before sending alternatives
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (player.isOnline() && !partyMechanics.isInParty(player)) {
                        player.sendMessage("");
                        player.sendMessage(ChatColor.AQUA + "🎯 " + ChatColor.BOLD + "Party Options:");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "/pinvite <player>" + ChatColor.GRAY + " - Create your own party");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "Wait for another invitation");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.BLUE + "Ask friends to invite you");
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.ITALIC + "Parties enable experience sharing, group chat, and teamwork!");
                    }
                }, 60L // 3 seconds delay
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion options for this command as it takes no arguments
        return new ArrayList<>();
    }
}
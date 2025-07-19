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
 *  Party Accept Command with comprehensive error handling and user feedback
 * Integrates fully with the advanced PartyMechanics system
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
            sender.sendMessage(ChatColor.RED + "❌ This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Validate command syntax
        if (args.length != 0) {
            sendUsageMessage(player);
            return true;
        }

        // Attempt to accept party invitation
        boolean success = partyMechanics.acceptPartyInvite(player);

        if (success) {
            // Success feedback with  effects
            player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "Party invitation accepted!");
            player.sendMessage(ChatColor.GRAY + "Welcome to the party! Use " + ChatColor.YELLOW + "/p <message>"
                    + ChatColor.GRAY + " to chat with your party members.");

            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

            // Provide helpful tips for new party members
            sendPartyTips(player);
        }
        // Error messages are already handled by PartyMechanics.acceptPartyInvite()

        return true;
    }

    /**
     * Send  usage message with context-aware help
     */
    private void sendUsageMessage(Player player) {
        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/paccept");
        player.sendMessage(ChatColor.GRAY + "Accepts a pending party invitation.");

        // Check if player has a pending invite for more helpful feedback
        if (!partyMechanics.isInParty(player)) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "💡 " + ChatColor.ITALIC + "You don't currently have a pending party invitation.");
            player.sendMessage(ChatColor.GRAY + "Ask a friend to invite you with " + ChatColor.YELLOW + "/pinvite <player>");
        }

        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    /**
     * Send helpful tips to new party members about party features
     */
    private void sendPartyTips(Player player) {
        // Add a small delay before sending tips to not overwhelm the player
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (player.isOnline() && partyMechanics.isInParty(player)) {
                        player.sendMessage("");
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "📋 " + ChatColor.BOLD + "Party Quick Guide:");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/p <message>" + ChatColor.GRAY + " - Chat with party");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/pquit" + ChatColor.GRAY + " - Leave party");

                        // Show leader-specific tips if they're the leader
                        if (partyMechanics.isPartyLeader(player)) {
                            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GOLD + "/pinvite <player>" + ChatColor.GRAY + " - Invite more players");
                            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GOLD + "/pkick <player>" + ChatColor.GRAY + " - Remove players");
                        }

                        // Show experience sharing info if enabled
                        if (partyMechanics.isExperienceSharing()) {
                            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "Experience sharing is enabled!");
                        }
                    }
                }, 40L // 2 seconds delay
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion options for this command as it takes no arguments
        return new ArrayList<>();
    }
}
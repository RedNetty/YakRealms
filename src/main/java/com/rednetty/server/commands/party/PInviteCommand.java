package com.rednetty.server.commands.party;

import com.rednetty.server.mechanics.player.social.party.PartyMechanics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 *  Party Invite Command with comprehensive features and validation
 * Integrates fully with the advanced PartyMechanics system including custom messages,
 * permission checks, and detailed feedback
 */
public class PInviteCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    public PInviteCommand() {
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
        if (args.length < 1) {
            sendUsageMessage(player);
            return true;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        // Comprehensive validation
        if (!validateInviteTarget(player, targetPlayer, targetName)) {
            return true;
        }

        // Extract optional custom message
        String customMessage = "";
        if (args.length > 1) {
            customMessage = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

            // Validate message length
            if (customMessage.length() > 100) {
                player.sendMessage(ChatColor.RED + "❌ Custom message too long! Maximum 100 characters.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return true;
            }
        }

        // Attempt to invite player with  feedback
        boolean success = partyMechanics.invitePlayerToParty(targetPlayer, player, customMessage);

        if (success) {
            //  success feedback
            player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "Party invitation sent!");
            player.sendMessage(ChatColor.GRAY + "Invited " + ChatColor.WHITE + ChatColor.BOLD + targetName
                    + ChatColor.GRAY + " to your party.");

            if (!customMessage.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Message: " + ChatColor.ITALIC + "\"" + customMessage + "\"");
            }

            // Show party status
            showPartyStatus(player);

            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            // Show invitation tips
            sendInvitationTips(player);
        }
        // Error messages are handled by PartyMechanics.invitePlayerToParty()

        return true;
    }

    /**
     * Comprehensive validation for invite target
     */
    private boolean validateInviteTarget(Player inviter, Player target, String targetName) {
        // Check if target player exists and is online
        if (target == null || !target.isOnline()) {
            inviter.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + targetName + ChatColor.RED + " is not online!");
            inviter.sendMessage(ChatColor.GRAY + "💡 Make sure to type the exact username.");
            inviter.playSound(inviter.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        // Check if trying to invite themselves
        if (target.equals(inviter)) {
            inviter.sendMessage(ChatColor.RED + "❌ You cannot invite yourself to your own party!");
            inviter.sendMessage(ChatColor.GRAY + "💡 Ask another player to join you instead.");
            inviter.playSound(inviter.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        // Check if target is already in inviter's party
        if (partyMechanics.arePartyMembers(inviter, target)) {
            inviter.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + targetName
                    + ChatColor.YELLOW + " is already in your party!");
            inviter.playSound(inviter.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
            return false;
        }

        // Check if target is in another party
        if (partyMechanics.isInParty(target)) {
            inviter.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + targetName
                    + ChatColor.RED + " is already in another party!");
            inviter.sendMessage(ChatColor.GRAY + "💡 They need to leave their current party first.");
            inviter.playSound(inviter.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        return true;
    }

    /**
     * Show current party status to the inviter
     */
    private void showPartyStatus(Player player) {
        PartyMechanics.Party party = partyMechanics.getParty(player);
        if (party != null) {
            int currentSize = party.getSize();
            int maxSize = party.getMaxSize();

            player.sendMessage(ChatColor.GRAY + "Party size: " + ChatColor.WHITE + currentSize
                    + ChatColor.GRAY + "/" + ChatColor.WHITE + maxSize);

            if (currentSize >= maxSize - 1) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Your party is almost full!");
            }
        }
    }

    /**
     * Send helpful tips about party invitations
     */
    private void sendInvitationTips(Player player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (player.isOnline() && partyMechanics.isInParty(player)) {
                        player.sendMessage("");
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "💡 " + ChatColor.BOLD + "Party Invitation Tips:");
                        player.sendMessage(ChatColor.GRAY + "• Invitations expire in " + ChatColor.WHITE
                                + partyMechanics.getInviteExpiryTimeSeconds() + " seconds");
                        player.sendMessage(ChatColor.GRAY + "• You can include a custom message: "
                                + ChatColor.YELLOW + "/pinvite <player> <message>");

                        if (partyMechanics.isPartyLeader(player)) {
                            player.sendMessage(ChatColor.GRAY + "• As party leader, you can manage all invitations");
                        }
                    }
                }, 80L // 4 seconds delay
        );
    }

    /**
     * Send  usage message with examples
     */
    private void sendUsageMessage(Player player) {
        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/pinvite <player> [message]");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Examples:");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/pinvite Steve");
        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/pinvite Alex Let's explore together!");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "💡 You can also " + ChatColor.UNDERLINE + "LEFT CLICK"
                + ChatColor.GRAY + " players with your " + ChatColor.ITALIC + "Character Journal"
                + ChatColor.GRAY + " to invite them.");

        // Show party creation info if not in a party
        if (!partyMechanics.isInParty(player)) {
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "ℹ " + ChatColor.ITALIC + "Creating your first invitation will automatically create a party!");
        }

        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Tab complete player names, filtering out players who can't be invited
            String partialName = args[0].toLowerCase();

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                // Skip self
                if (onlinePlayer.equals(player)) continue;

                // Skip if already in same party
                if (partyMechanics.arePartyMembers(player, onlinePlayer)) continue;

                // Skip if target is in another party
                if (partyMechanics.isInParty(onlinePlayer)) continue;

                // Add if name matches partial input
                if (onlinePlayer.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(onlinePlayer.getName());
                }
            }
        } else if (args.length == 2) {
            // Suggest common invitation messages
            String partialMessage = args[1].toLowerCase();
            List<String> suggestions = List.of(
                    "Let's", "Join", "Come", "Want", "Hey", "Adventure", "Explore", "Quest"
            );

            completions = suggestions.stream()
                    .filter(suggestion -> suggestion.toLowerCase().startsWith(partialMessage))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
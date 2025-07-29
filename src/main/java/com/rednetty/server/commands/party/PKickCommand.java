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

/**
 *  Party Kick Command with comprehensive permission checking and validation
 * Integrates fully with the advanced PartyMechanics system including role-based permissions
 */
public class PKickCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    public PKickCommand() {
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
        if (args.length != 1) {
            sendUsageMessage(player);
            return true;
        }

        // Validate player is in a party
        if (!partyMechanics.isInParty(player)) {
            player.sendMessage(ChatColor.RED + "❌ You are not in a party!");
            player.sendMessage(ChatColor.GRAY + "💡 Join or create a party first with " + ChatColor.YELLOW + "/pinvite <player>");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return true;
        }

        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        // Comprehensive validation
        if (!validateKickTarget(player, targetPlayer, targetName)) {
            return true;
        }

        // Show confirmation for kick action
        showKickConfirmation(player, targetPlayer);

        // Attempt to kick player with  feedback
        boolean success = partyMechanics.kickPlayerFromParty(player.getUniqueId(), targetPlayer.getUniqueId());

        if (success) {
            //  success feedback
            player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "Player removed from party!");
            player.sendMessage(ChatColor.GRAY + "Kicked " + ChatColor.WHITE + ChatColor.BOLD + targetName
                    + ChatColor.GRAY + " from the party.");

            // Show updated party status
            showPartyStatus(player);

            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            // Show leadership tips if needed
            sendLeadershipTips(player);
        }
        // Error messages are handled by PartyMechanics.kickPlayerFromParty()

        return true;
    }

    /**
     * Comprehensive validation for kick target
     */
    private boolean validateKickTarget(Player kicker, Player target, String targetName) {
        // Check if target player exists and is online
        if (target == null || !target.isOnline()) {
            kicker.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + targetName + ChatColor.RED + " is not online!");
            kicker.sendMessage(ChatColor.GRAY + "💡 Make sure to type the exact username of a party member.");
            kicker.playSound(kicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        // Check if trying to kick themselves
        if (target.equals(kicker)) {
            kicker.sendMessage(ChatColor.RED + "❌ You cannot kick yourself from the party!");
            kicker.sendMessage(ChatColor.GRAY + "💡 Use " + ChatColor.YELLOW + "/pquit" + ChatColor.GRAY + " to leave the party instead.");
            kicker.playSound(kicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        // Check if target is in the same party
        if (!partyMechanics.arePartyMembers(kicker, target)) {
            kicker.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + targetName + ChatColor.RED + " is not in your party!");
            kicker.sendMessage(ChatColor.GRAY + "💡 You can only kick members of your own party.");
            kicker.playSound(kicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return false;
        }

        // Check permissions using the advanced party system
        PartyMechanics.Party party = partyMechanics.getParty(kicker.getUniqueId());
        if (party != null) {
            // Check if kicker has permission to kick
            if (!party.hasPermission(kicker.getUniqueId(), PartyMechanics.PartyPermission.KICK)) {
                kicker.sendMessage(ChatColor.RED + "❌ You don't have permission to kick players from this party!");
                kicker.sendMessage(ChatColor.GRAY + "💡 Only the party leader and officers can kick members.");
                kicker.playSound(kicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return false;
            }

            // Check if trying to kick the party leader
            if (party.isLeader(target.getUniqueId())) {
                kicker.sendMessage(ChatColor.RED + "❌ You cannot kick the party leader!");
                kicker.sendMessage(ChatColor.GRAY + "💡 The party leader must transfer leadership or disband the party.");
                kicker.playSound(kicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return false;
            }

            // Check if non-leader is trying to kick an officer
            if (party.isOfficer(target.getUniqueId()) && !party.isLeader(kicker.getUniqueId())) {
                kicker.sendMessage(ChatColor.RED + "❌ You cannot kick another officer!");
                kicker.sendMessage(ChatColor.GRAY + "💡 Only the party leader can kick officers.");
                kicker.playSound(kicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return false;
            }
        }

        return true;
    }

    /**
     * Show kick confirmation with target information
     */
    private void showKickConfirmation(Player kicker, Player target) {
        PartyMechanics.Party party = partyMechanics.getParty(kicker.getUniqueId());
        if (party != null) {
            String roleInfo = "";
            if (party.isOfficer(target.getUniqueId())) {
                roleInfo = ChatColor.YELLOW + " (Officer)";
            }

            kicker.sendMessage(ChatColor.YELLOW + "⚠ Kicking " + ChatColor.BOLD + target.getName()
                    + roleInfo + ChatColor.YELLOW + " from the party...");
        }
    }

    /**
     * Show current party status after kick
     */
    private void showPartyStatus(Player player) {
        PartyMechanics.Party party = partyMechanics.getParty(player.getUniqueId());
        if (party != null) {
            int currentSize = party.getSize();
            int maxSize = party.getMaxSize();

            player.sendMessage(ChatColor.GRAY + "Party size: " + ChatColor.WHITE + currentSize
                    + ChatColor.GRAY + "/" + ChatColor.WHITE + maxSize);

            if (currentSize == 1) {
                player.sendMessage(ChatColor.YELLOW + "⚠ You are now alone in the party. Consider inviting more players!");
            }
        }
    }

    /**
     * Send leadership tips to party leaders/officers
     */
    private void sendLeadershipTips(Player player) {
        if (partyMechanics.isPartyLeader(player) || partyMechanics.isPartyOfficer(player)) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                    com.rednetty.server.YakRealms.getInstance(),
                    () -> {
                        if (player.isOnline() && partyMechanics.isInParty(player)) {
                            player.sendMessage("");
                            player.sendMessage(ChatColor.GOLD + "👑 " + ChatColor.BOLD + "Party Management Tips:");
                            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "/pinvite <player>" + ChatColor.GRAY + " - Invite new members");

                            if (partyMechanics.isPartyLeader(player)) {
                                player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GOLD + "Right-click members" + ChatColor.GRAY + " - Promote to officer");
                                player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "Maintain good team dynamics");
                            }

                            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.AQUA + "Use " + ChatColor.YELLOW + "/p <message>"
                                    + ChatColor.GRAY + " for team communication");
                        }
                    }, 100L // 5 seconds delay
            );
        }
    }

    /**
     * Send  usage message with role-based help
     */
    private void sendUsageMessage(Player player) {
        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/pkick <player>");
        player.sendMessage(ChatColor.GRAY + "Removes a player from your party.");
        player.sendMessage("");

        if (partyMechanics.isInParty(player)) {
            PartyMechanics.Party party = partyMechanics.getParty(player.getUniqueId());
            if (party != null) {
                if (party.hasPermission(player.getUniqueId(), PartyMechanics.PartyPermission.KICK)) {
                    List<Player> kickableMembers = getKickableMembers(player, party);
                    if (!kickableMembers.isEmpty()) {
                        player.sendMessage(ChatColor.GRAY + "Kickable members: " + ChatColor.WHITE
                                + String.join(", ", kickableMembers.stream().map(Player::getName).toArray(String[]::new)));
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "💡 No kickable members in your party.");
                    }
                } else {
                    player.sendMessage(ChatColor.YELLOW + "💡 You need officer or leader permissions to kick players.");
                }
            }
        } else {
            player.sendMessage(ChatColor.GRAY + "💡 You must be in a party to use this command.");
        }

        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    /**
     * Get list of members that can be kicked by the player
     */
    private List<Player> getKickableMembers(Player kicker, PartyMechanics.Party party) {
        List<Player> kickableMembers = new ArrayList<>();

        for (Player member : partyMechanics.getPartyMembers(kicker)) {
            if (member.equals(kicker)) continue; // Can't kick self
            if (party.isLeader(member.getUniqueId())) continue; // Can't kick leader

            // Officers can't kick other officers unless they're the leader
            if (party.isOfficer(member.getUniqueId()) && !party.isLeader(kicker.getUniqueId())) {
                continue;
            }

            kickableMembers.add(member);
        }

        return kickableMembers;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && partyMechanics.isInParty(player)) {
            String partialName = args[0].toLowerCase();
            PartyMechanics.Party party = partyMechanics.getParty(player.getUniqueId());

            if (party != null && party.hasPermission(player.getUniqueId(), PartyMechanics.PartyPermission.KICK)) {
                List<Player> kickableMembers = getKickableMembers(player, party);

                for (Player member : kickableMembers) {
                    if (member.getName().toLowerCase().startsWith(partialName)) {
                        completions.add(member.getName());
                    }
                }
            }
        }

        return completions;
    }
}
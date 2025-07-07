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
 * Enhanced Party Quit Command with comprehensive feedback and leadership handling
 * Integrates fully with the advanced PartyMechanics system including role transitions
 */
public class PQuitCommand implements CommandExecutor, TabCompleter {

    private final PartyMechanics partyMechanics;

    public PQuitCommand() {
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

        // Validate player is in a party
        if (!partyMechanics.isInParty(player)) {
            player.sendMessage(ChatColor.RED + "❌ You are not in a party!");
            player.sendMessage(ChatColor.GRAY + "💡 Join a party first with " + ChatColor.YELLOW + "/pinvite <player>"
                    + ChatColor.GRAY + " or accept an invitation with " + ChatColor.YELLOW + "/paccept");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            return true;
        }

        // Show pre-quit information and warnings
        showQuitInformation(player);

        // Attempt to quit party
        player.sendMessage(ChatColor.RED + "✓ You have left the party.");
        boolean success = partyMechanics.removePlayerFromParty(player);

        if (success) {
            // Enhanced feedback after leaving
            player.sendMessage(ChatColor.GRAY + "You are no longer part of a party group.");

            // Play farewell sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

            // Provide helpful next steps
            sendPostQuitOptions(player);
        }

        return true;
    }

    /**
     * Show information about quitting and potential consequences
     */
    private void showQuitInformation(Player player) {
        PartyMechanics.Party party = partyMechanics.getParty(player);
        if (party == null) return;

        boolean isLeader = party.isLeader(player.getUniqueId());
        boolean isOfficer = party.isOfficer(player.getUniqueId());
        int partySize = party.getSize();

        // Show role-specific warnings
        if (isLeader) {
            if (partySize > 1) {
                player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "Leadership Transfer Warning!");
                player.sendMessage(ChatColor.GRAY + "As the party leader, your departure will promote another member to leader.");

                // Show who will become the new leader
                List<Player> members = partyMechanics.getPartyMembers(player);
                if (members != null && members.size() > 1) {
                    Player nextLeader = null;
                    // Find the first officer or first member who isn't the current leader
                    for (Player member : members) {
                        if (!member.equals(player)) {
                            if (party.isOfficer(member.getUniqueId())) {
                                nextLeader = member;
                                break;
                            } else if (nextLeader == null) {
                                nextLeader = member;
                            }
                        }
                    }

                    if (nextLeader != null) {
                        String roleTransfer = party.isOfficer(nextLeader.getUniqueId()) ? "officer" : "member";
                        player.sendMessage(ChatColor.GRAY + "New leader will be: " + ChatColor.WHITE + ChatColor.BOLD
                                + nextLeader.getName() + ChatColor.GRAY + " (current " + roleTransfer + ")");
                    }
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "Party Disbanding Warning!");
                player.sendMessage(ChatColor.GRAY + "You are the only member, so the party will be disbanded.");
            }
        } else if (isOfficer) {
            player.sendMessage(ChatColor.YELLOW + "⚠ You are leaving as a party officer.");
            player.sendMessage(ChatColor.GRAY + "Your officer privileges will be lost.");
        }

        // Show party statistics if meaningful
        if (partySize > 2) {
            player.sendMessage(ChatColor.GRAY + "Leaving a party of " + ChatColor.WHITE + partySize
                    + ChatColor.GRAY + " members.");
        }

        // Show experience sharing warning if applicable
        if (partyMechanics.isExperienceSharing() && partySize > 1) {
            player.sendMessage(ChatColor.GRAY + "💡 You will lose party experience sharing benefits.");
        }
    }

    /**
     * Provide options and suggestions after leaving a party
     */
    private void sendPostQuitOptions(Player player) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                com.rednetty.server.YakRealms.getInstance(),
                () -> {
                    if (player.isOnline() && !partyMechanics.isInParty(player)) {
                        player.sendMessage("");
                        player.sendMessage(ChatColor.AQUA + "🎯 " + ChatColor.BOLD + "What's Next?");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GREEN + "/pinvite <player>" + ChatColor.GRAY + " - Create a new party");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.YELLOW + "Wait for party invitations");
                        player.sendMessage(ChatColor.GRAY + "• " + ChatColor.BLUE + "Continue playing solo");
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.ITALIC + "Remember: Parties provide experience bonuses and team features!");

                        // Show party statistics if the player has meaningful history
                        PartyMechanics.PartyStatistics stats = partyMechanics.getPartyStatistics(player.getUniqueId());
                        if (stats.getTotalPartiesJoined() > 0) {
                            player.sendMessage("");
                            player.sendMessage(ChatColor.DARK_GRAY + "📊 Your party history: "
                                    + stats.getTotalPartiesJoined() + " parties joined, "
                                    + stats.getTotalPartiesCreated() + " parties created");
                        }
                    }
                }, 80L // 4 seconds delay
        );
    }

    /**
     * Send enhanced usage message
     */
    private void sendUsageMessage(Player player) {
        player.sendMessage(ChatColor.RED + "❌ " + ChatColor.BOLD + "Invalid Syntax");
        player.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.YELLOW + "/pquit");
        player.sendMessage(ChatColor.GRAY + "Leaves your current party.");
        player.sendMessage("");

        if (partyMechanics.isInParty(player)) {
            PartyMechanics.Party party = partyMechanics.getParty(player);
            if (party != null) {
                player.sendMessage(ChatColor.GRAY + "Current party size: " + ChatColor.WHITE + party.getSize());

                if (party.isLeader(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "⚠ You are the party leader!");
                    if (party.getSize() > 1) {
                        player.sendMessage(ChatColor.GRAY + "Leadership will be transferred to another member.");
                    } else {
                        player.sendMessage(ChatColor.GRAY + "The party will be disbanded.");
                    }
                }
            }
        } else {
            player.sendMessage(ChatColor.GRAY + "💡 You are not currently in a party.");
        }

        // Play error sound
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion options for this command as it takes no arguments
        return new ArrayList<>();
    }
}
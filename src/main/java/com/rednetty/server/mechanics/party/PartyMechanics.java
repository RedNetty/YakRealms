package com.rednetty.server.mechanics.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main party system mechanics implementation
 * Manages player parties, invitations, and related functionality
 */
public class PartyMechanics implements Listener {
    private static PartyMechanics instance;
    private final Logger logger;
    private final YakPlayerManager playerManager;

    // Core data structures
    private final Map<UUID, List<UUID>> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inviteTimes = new ConcurrentHashMap<>();

    // Configuration
    private int maxPartySize = 8;
    private int inviteExpiryTimeSeconds = 30;

    // Tasks
    private BukkitTask scoreboardRefreshTask;
    private BukkitTask inviteCleanupTask;

    /**
     * Private constructor for singleton pattern
     */
    private PartyMechanics() {
        this.logger = YakRealms.getInstance().getLogger();
        this.playerManager = YakPlayerManager.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The PartyMechanics instance
     */
    public static PartyMechanics getInstance() {
        if (instance == null) {
            instance = new PartyMechanics();
        }
        return instance;
    }

    /**
     * Initialize the party system
     */
    public void onEnable() {
        // Register events
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start tasks
        startScoreboardRefreshTask();
        startInviteCleanupTask();

        YakRealms.log("Party mechanics have been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Cancel tasks
        if (scoreboardRefreshTask != null) {
            scoreboardRefreshTask.cancel();
        }

        if (inviteCleanupTask != null) {
            inviteCleanupTask.cancel();
        }

        // Clear data
        parties.clear();
        invites.clear();
        inviteTimes.clear();

        YakRealms.log("Party mechanics have been disabled.");
    }

    /**
     * Start the scoreboard refresh task
     */
    private void startScoreboardRefreshTask() {
        scoreboardRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        PartyScoreboards.refreshPlayerScoreboard(player);
                    } catch (Exception e) {
                        logger.warning("Error refreshing party scoreboard for " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Update every second for performance
    }

    /**
     * Start the invite cleanup task
     */
    private void startInviteCleanupTask() {
        inviteCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long currentTime = System.currentTimeMillis();
                    List<UUID> expiredInvites = new ArrayList<>();

                    for (Map.Entry<UUID, Long> entry : inviteTimes.entrySet()) {
                        UUID invitedPlayer = entry.getKey();
                        long inviteTime = entry.getValue();

                        if (currentTime - inviteTime > inviteExpiryTimeSeconds * 1000) {
                            expiredInvites.add(invitedPlayer);
                        }
                    }

                    for (UUID invitedPlayer : expiredInvites) {
                        UUID inviter = invites.get(invitedPlayer);

                        // Notify players if they're online
                        Player invitedBukkit = Bukkit.getPlayer(invitedPlayer);
                        if (invitedBukkit != null && invitedBukkit.isOnline()) {
                            invitedBukkit.sendMessage(ChatColor.RED + "Party invite from " +
                                    Bukkit.getPlayer(inviter).getName() + " expired.");
                        }

                        Player inviterBukkit = Bukkit.getPlayer(inviter);
                        if (inviterBukkit != null && inviterBukkit.isOnline()) {
                            inviterBukkit.sendMessage(ChatColor.RED + "Party invite to " +
                                    Bukkit.getPlayer(invitedPlayer).getName() + " has expired.");
                        }

                        invites.remove(invitedPlayer);
                        inviteTimes.remove(invitedPlayer);
                    }
                } catch (Exception e) {
                    logger.warning("Error in party invite cleanup: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Check every second
    }

    /**
     * Check if a player is a party leader
     *
     * @param player The player to check
     * @return true if the player is a party leader
     */
    public boolean isPartyLeader(Player player) {
        return isPartyLeader(player.getUniqueId());
    }

    /**
     * Check if a player is a party leader
     *
     * @param playerId The UUID of the player to check
     * @return true if the player is a party leader
     */
    public boolean isPartyLeader(UUID playerId) {
        return parties.containsKey(playerId);
    }

    /**
     * Get the party leader of a player
     *
     * @param player The player to get the leader for
     * @return The party leader, or the player themselves if not in a party or if they are the leader
     */
    public Player getPartyLeader(Player player) {
        UUID leaderId = getPartyLeaderId(player.getUniqueId());
        return leaderId != null ? Bukkit.getPlayer(leaderId) : player;
    }

    /**
     * Get the party leader's UUID of a player
     *
     * @param playerId The UUID of the player to get the leader for
     * @return The party leader's UUID, or null if not in a party
     */
    public UUID getPartyLeaderId(UUID playerId) {
        // If the player is a leader, return them
        if (isPartyLeader(playerId)) {
            return playerId;
        }

        // Otherwise, find which party they're in
        for (Map.Entry<UUID, List<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(playerId)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Check if a player is in a party
     *
     * @param player The player to check
     * @return true if the player is in a party
     */
    public boolean isInParty(Player player) {
        return isInParty(player.getUniqueId());
    }

    /**
     * Check if a player is in a party
     *
     * @param playerId The UUID of the player to check
     * @return true if the player is in a party
     */
    public boolean isInParty(UUID playerId) {
        // Check if they're a leader
        if (parties.containsKey(playerId)) {
            return true;
        }

        // Check if they're a member of any party
        for (List<UUID> partyMembers : parties.values()) {
            if (partyMembers.contains(playerId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all members of a player's party (including the player)
     *
     * @param player The player to get party members for
     * @return List of all party members, or null if not in a party
     */
    public List<Player> getPartyMembers(Player player) {
        List<UUID> memberIds = getPartyMemberIds(player.getUniqueId());
        if (memberIds == null) {
            return null;
        }

        return memberIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all member UUIDs of a player's party (including the player)
     *
     * @param playerId The UUID of the player to get party members for
     * @return List of all party member UUIDs, or null if not in a party
     */
    public List<UUID> getPartyMemberIds(UUID playerId) {
        // If the player is a leader, return their party members
        if (parties.containsKey(playerId)) {
            return new ArrayList<>(parties.get(playerId));
        }

        // Otherwise, find which party they're in
        for (Map.Entry<UUID, List<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(playerId)) {
                return new ArrayList<>(entry.getValue());
            }
        }

        return null;
    }

    /**
     * Check if two players are in the same party
     *
     * @param player1 The first player
     * @param player2 The second player
     * @return true if both players are in the same party
     */
    public boolean arePartyMembers(Player player1, Player player2) {
        return arePartyMembers(player1.getUniqueId(), player2.getUniqueId());
    }

    /**
     * Check if two players are in the same party
     *
     * @param player1Id The UUID of the first player
     * @param player2Id The UUID of the second player
     * @return true if both players are in the same party
     */
    public boolean arePartyMembers(UUID player1Id, UUID player2Id) {
        // Get the party members for player1
        List<UUID> partyMembers = getPartyMemberIds(player1Id);

        // If player1 isn't in a party, or player2 isn't in player1's party
        return partyMembers != null && partyMembers.contains(player2Id);
    }

    /**
     * Create a new party with the player as the leader
     *
     * @param player The player who will be the party leader
     */
    public void createParty(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if already in a party
        if (isInParty(playerId)) {
            return;
        }

        // Create the party with the player as the only member
        List<UUID> members = new ArrayList<>();
        members.add(playerId);
        parties.put(playerId, members);

        // Update scoreboard
        PartyScoreboards.updatePlayerScoreboard(player);
    }

    /**
     * Add a player to a party
     *
     * @param player The player to add
     * @param leader The party leader
     */
    public void addPlayerToParty(Player player, Player leader) {
        addPlayerToParty(player.getUniqueId(), leader.getUniqueId());
    }

    /**
     * Add a player to a party
     *
     * @param playerId The UUID of the player to add
     * @param leaderId The UUID of the party leader
     */
    public void addPlayerToParty(UUID playerId, UUID leaderId) {
        // Ensure the leader has a party
        if (!parties.containsKey(leaderId)) {
            return;
        }

        // Get the party members
        List<UUID> members = parties.get(leaderId);

        // Check if the player is already in the party
        if (members.contains(playerId)) {
            return;
        }

        // Add the player to the party
        members.add(playerId);

        // Update party
        parties.put(leaderId, members);

        // Update scoreboards for all party members
        for (UUID memberId : members) {
            Player memberPlayer = Bukkit.getPlayer(memberId);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                PartyScoreboards.updatePlayerScoreboard(memberPlayer);
            }
        }
    }

    /**
     * Remove a player from their party
     *
     * @param player The player to remove
     */
    public void removePlayerFromParty(Player player) {
        removePlayerFromParty(player.getUniqueId());
    }

    /**
     * Remove a player from their party
     *
     * @param playerId The UUID of the player to remove
     */
    public void removePlayerFromParty(UUID playerId) {
        // If they're not in a party, do nothing
        if (!isInParty(playerId)) {
            return;
        }

        // If they're the party leader
        if (isPartyLeader(playerId)) {
            List<UUID> members = parties.get(playerId);

            // If there are other members, promote someone else to leader
            if (members.size() > 1) {
                // Remove the current leader
                members.remove(playerId);

                // Get the new leader
                UUID newLeaderId = members.get(0);
                Player newLeader = Bukkit.getPlayer(newLeaderId);

                // Update party leadership
                parties.put(newLeaderId, members);
                parties.remove(playerId);

                // Notify the new leader
                if (newLeader != null && newLeader.isOnline()) {
                    newLeader.sendMessage(ChatColor.RED + "You have been made the party leader!");
                }

                // Notify other members
                Player oldLeader = Bukkit.getPlayer(playerId);
                String oldLeaderName = oldLeader != null ? oldLeader.getName() : "Unknown";
                String newLeaderName = newLeader != null ? newLeader.getName() : "Unknown";

                for (UUID memberId : members) {
                    Player memberPlayer = Bukkit.getPlayer(memberId);
                    if (memberPlayer != null && memberPlayer.isOnline()) {
                        memberPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                                ChatColor.GRAY + " " + oldLeaderName + ChatColor.GRAY + " has " +
                                ChatColor.LIGHT_PURPLE + ChatColor.UNDERLINE + "left" + ChatColor.GRAY + " your party.");

                        memberPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + "> " +
                                ChatColor.GRAY + ChatColor.LIGHT_PURPLE + newLeaderName + ChatColor.GRAY +
                                " has been promoted to " + ChatColor.UNDERLINE + "Party Leader");

                        // Update scoreboard
                        PartyScoreboards.updatePlayerScoreboard(memberPlayer);
                    }
                }
            } else {
                // If they're the only member, just remove the party
                parties.remove(playerId);
            }
        } else {
            // If they're not the leader, find their party and remove them
            UUID leaderId = getPartyLeaderId(playerId);
            if (leaderId != null) {
                List<UUID> members = parties.get(leaderId);
                members.remove(playerId);
                parties.put(leaderId, members);

                // Notify party members
                Player leavingPlayer = Bukkit.getPlayer(playerId);
                String leavingPlayerName = leavingPlayer != null ? leavingPlayer.getName() : "Unknown";

                for (UUID memberId : members) {
                    Player memberPlayer = Bukkit.getPlayer(memberId);
                    if (memberPlayer != null && memberPlayer.isOnline()) {
                        memberPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                                ChatColor.GRAY + " " + leavingPlayerName + ChatColor.GRAY + " has " +
                                ChatColor.RED + ChatColor.UNDERLINE + "left" + ChatColor.GRAY + " your party.");

                        // Update scoreboard
                        PartyScoreboards.updatePlayerScoreboard(memberPlayer);
                    }
                }
            }
        }

        // Update the leaving player's scoreboard
        Player leavingPlayer = Bukkit.getPlayer(playerId);
        if (leavingPlayer != null && leavingPlayer.isOnline()) {
            PartyScoreboards.clearPlayerScoreboard(leavingPlayer);
        }
    }

    /**
     * Invite a player to join a party
     *
     * @param invited The player being invited
     * @param inviter The player doing the inviting
     */
    public void invitePlayerToParty(Player invited, Player inviter) {
        if (invited.equals(inviter)) {
            inviter.sendMessage(ChatColor.RED + "You cannot invite yourself to your own party.");
            return;
        }

        // Check if the inviter is in a party but not the leader
        if (isInParty(inviter) && !isPartyLeader(inviter)) {
            inviter.sendMessage(ChatColor.RED + "You are NOT the leader of your party.");
            inviter.sendMessage(ChatColor.GRAY + "Type " + ChatColor.BOLD + "/pquit" + ChatColor.GRAY + " to quit your current party.");
            return;
        }

        // Check if the party is full
        if (isPartyLeader(inviter) && parties.get(inviter.getUniqueId()).size() >= maxPartySize) {
            inviter.sendMessage(ChatColor.RED + "You cannot have more than " + ChatColor.ITALIC + maxPartySize + " players" + ChatColor.RED + " in a party.");
            inviter.sendMessage(ChatColor.GRAY + "You may use /pkick to kick out unwanted members.");
            return;
        }

        // Check if the invited player is already in a party
        if (isInParty(invited)) {
            if (arePartyMembers(inviter, invited)) {
                inviter.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + invited.getName() + ChatColor.RED + " is already in your party.");
                inviter.sendMessage(ChatColor.GRAY + "Type /pkick " + invited.getName() + " to kick them out.");
            } else {
                inviter.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + invited.getName() + ChatColor.RED + " is already in another party.");
            }
            return;
        }

        // Check if the invited player already has a pending invite
        if (invites.containsKey(invited.getUniqueId())) {
            inviter.sendMessage(ChatColor.RED + invited.getName() + " has a pending party invite.");
            return;
        }

        // Create a party for the inviter if they don't have one
        if (!isInParty(inviter)) {
            inviter.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "Party created.");
            inviter.sendMessage(ChatColor.GRAY + "To invite more people to join your party, " + ChatColor.UNDERLINE + "Left Click" +
                    ChatColor.GRAY + " them with your character journal or use " + ChatColor.BOLD + "/pinvite" +
                    ChatColor.GRAY + ". To kick, use " + ChatColor.BOLD + "/pkick" + ChatColor.GRAY + ".");
            createParty(inviter);
        }

        // Send invite messages
        invited.sendMessage(ChatColor.LIGHT_PURPLE + ChatColor.UNDERLINE.toString() + inviter.getName() +
                ChatColor.GRAY + " has invited you to join their party. To accept, type " +
                ChatColor.LIGHT_PURPLE + "/paccept" + ChatColor.GRAY + " or to decline, type " +
                ChatColor.LIGHT_PURPLE + "/pdecline");

        inviter.sendMessage(ChatColor.GRAY + "You have invited " + ChatColor.LIGHT_PURPLE + invited.getName() +
                ChatColor.GRAY + " to join your party.");

        // Register the invite
        invites.put(invited.getUniqueId(), inviter.getUniqueId());
        inviteTimes.put(invited.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Accept a party invitation
     *
     * @param player The player accepting the invitation
     * @return true if the invitation was successfully accepted, false otherwise
     */
    public boolean acceptPartyInvite(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if the player has a pending invite
        if (!invites.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "No pending party invites.");
            return false;
        }

        // Get the inviter
        UUID inviterId = invites.get(playerId);
        Player inviter = Bukkit.getPlayer(inviterId);

        // Check if the inviter is still online and still a party leader
        if (inviter == null || !isPartyLeader(inviterId)) {
            player.sendMessage(ChatColor.RED + "This party invite is no longer available.");
            invites.remove(playerId);
            inviteTimes.remove(playerId);
            return false;
        }

        // Check if the party is full
        List<UUID> partyMembers = parties.get(inviterId);
        if (partyMembers.size() >= maxPartySize) {
            player.sendMessage(ChatColor.RED + "This party is currently full.");
            invites.remove(playerId);
            inviteTimes.remove(playerId);
            return false;
        }

        // Add the player to the party
        addPlayerToParty(playerId, inviterId);

        // Notify party members
        for (UUID memberId : partyMembers) {
            Player memberPlayer = Bukkit.getPlayer(memberId);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                memberPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                        ChatColor.GRAY + " " + player.getName() + ChatColor.GRAY + " has " +
                        ChatColor.LIGHT_PURPLE + ChatColor.UNDERLINE + "joined" + ChatColor.GRAY + " your party.");
            }
        }

        // Notify the player
        player.sendMessage("");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "You have joined " + ChatColor.BOLD + inviter.getName() + "'s" +
                ChatColor.LIGHT_PURPLE + " party.");
        player.sendMessage(ChatColor.GRAY + "To chat with your party, use " + ChatColor.BOLD + "/p" +
                ChatColor.GRAY + " OR " + ChatColor.BOLD + " /p <message>");

        // Remove the invite
        invites.remove(playerId);
        inviteTimes.remove(playerId);

        return true;
    }

    /**
     * Decline a party invitation
     *
     * @param player The player declining the invitation
     * @return true if the invitation was successfully declined, false otherwise
     */
    public boolean declinePartyInvite(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if the player has a pending invite
        if (!invites.containsKey(playerId)) {
            player.sendMessage(ChatColor.RED + "No pending party invites.");
            return false;
        }

        // Get the inviter
        UUID inviterId = invites.get(playerId);
        Player inviter = Bukkit.getPlayer(inviterId);

        // Notify the player
        player.sendMessage(ChatColor.RED + "Declined " + ChatColor.BOLD +
                (inviter != null ? inviter.getName() : "Unknown") + "'s" +
                ChatColor.RED + " party invitation.");

        // Notify the inviter if they're online
        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + player.getName() +
                    ChatColor.RED + " has " + ChatColor.UNDERLINE + "DECLINED" +
                    ChatColor.RED + " your party invitation.");
        }

        // Remove the invite
        invites.remove(playerId);
        inviteTimes.remove(playerId);

        return true;
    }

    /**
     * Kick a player from a party
     *
     * @param leader       The party leader
     * @param playerToKick The player to kick
     * @return true if the player was successfully kicked, false otherwise
     */
    public boolean kickPlayerFromParty(Player leader, Player playerToKick) {
        // Check if the leader is actually a party leader
        if (!isPartyLeader(leader)) {
            leader.sendMessage(ChatColor.RED + "You are NOT the leader of your party.");
            leader.sendMessage(ChatColor.GRAY + "Type " + ChatColor.BOLD + "/pquit" +
                    ChatColor.GRAY + " to quit your current party.");
            return false;
        }

        // Check if the player to kick is in the leader's party
        if (!arePartyMembers(leader, playerToKick)) {
            leader.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() +
                    playerToKick.getName() + " is not in your party.");
            return false;
        }

        // Notify the kicked player
        playerToKick.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() +
                "You have been kicked out of the party.");

        // Remove the player from the party
        removePlayerFromParty(playerToKick);

        return true;
    }

    /**
     * Send a message to all members of a party
     *
     * @param sender  The player sending the message
     * @param message The message to send
     * @return true if the message was sent, false otherwise
     */
    public boolean sendPartyMessage(Player sender, String message) {
        // Check if the player is in a party
        if (!isInParty(sender)) {
            sender.sendMessage(ChatColor.RED + "You are not in a party.");
            return false;
        }

        // Get party members
        List<Player> partyMembers = getPartyMembers(sender);
        if (partyMembers == null || partyMembers.isEmpty()) {
            return false;
        }

        // Send the message to all party members
        for (Player member : partyMembers) {
            member.sendMessage(ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                    " " + sender.getDisplayName() + ": " + ChatColor.GRAY + message);
        }

        return true;
    }

    /**
     * Get the maximum party size
     *
     * @return The maximum party size
     */
    public int getMaxPartySize() {
        return maxPartySize;
    }

    /**
     * Set the maximum party size
     *
     * @param maxPartySize The new maximum party size
     */
    public void setMaxPartySize(int maxPartySize) {
        this.maxPartySize = maxPartySize;
    }

    /**
     * Get the invite expiry time in seconds
     *
     * @return The invite expiry time in seconds
     */
    public int getInviteExpiryTimeSeconds() {
        return inviteExpiryTimeSeconds;
    }

    /**
     * Set the invite expiry time in seconds
     *
     * @param inviteExpiryTimeSeconds The new invite expiry time in seconds
     */
    public void setInviteExpiryTimeSeconds(int inviteExpiryTimeSeconds) {
        this.inviteExpiryTimeSeconds = inviteExpiryTimeSeconds;
    }

    /**
     * Clear all parties
     */
    public void clearAllParties() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PartyScoreboards.clearPlayerScoreboard(player);
        }
        parties.clear();
    }

    /**
     * Create a party for every online player that is not in a party
     */
    public void createPartyForEveryoneWithoutOne() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInParty(player)) {
                createParty(player);
            }
        }
    }

    /**
     * Handle player quit event to remove them from their party
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (isInParty(player)) {
            removePlayerFromParty(player);
        }
    }
}
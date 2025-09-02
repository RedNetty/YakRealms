package com.rednetty.server.core.mechanics.player.social.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.messaging.MessageUtil;
import com.rednetty.server.utils.sounds.SoundUtil;
import com.rednetty.server.utils.permissions.PermissionUtil;
import com.rednetty.server.utils.cooldowns.CooldownManager;
import com.rednetty.server.utils.player.PlayerResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * INTEGRATED Party System Mechanics with built-in scoreboard management
 * - Unified utility integration (MessageUtil, SoundUtil, PermissionUtil, CooldownManager, PlayerResolver)
 * - Integrated scoreboard management for party displays
 * - Comprehensive party functionality with improved user experience
 * - Automatic scoreboard updates for party events and health changes
 */
public class PartyMechanics implements Listener {

    // ========================================
    // INSTANCE MANAGEMENT
    // ========================================

    private static PartyMechanics instance;
    private final Logger logger;
    private final YakPlayerManager playerManager;

    // ========================================
    // CORE DATA STRUCTURES
    // ========================================

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, PartyInvite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToPartyMap = new ConcurrentHashMap<>();
    private final Set<PartyEventListener> eventListeners = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // ========================================
    // CONFIGURATION
    // ========================================

    private int maxPartySize = 8;
    private int inviteExpiryTimeSeconds = 30;
    private boolean experienceSharing = true;
    private boolean lootSharing = false;
    private double experienceShareRadius = 50.0;
    private double lootShareRadius = 30.0;
    private int partyWarpCooldown = 300; // 5 minutes
    private boolean allowCrossWorldParties = true;
    private boolean enablePartyEffects = true;
    private boolean enablePartyChat = true;

    // ========================================
    // BACKGROUND TASKS (ENHANCED WITH SCOREBOARD SUPPORT)
    // ========================================

    private BukkitTask inviteCleanupTask;
    private BukkitTask partyMaintenanceTask;
    private BukkitTask statisticsTask;
    private BukkitTask scoreboardHealthUpdateTask;
    private BukkitTask scoreboardMaintenanceTask;

    // ========================================
    // COOLDOWN TYPES
    // ========================================

    private static final String PARTY_INVITE_COOLDOWN = "party_invite";
    private static final String PARTY_WARP_COOLDOWN = "party_warp";
    private static final String PARTY_KICK_COOLDOWN = "party_kick";
    private static final String PARTY_PROMOTE_COOLDOWN = "party_promote";

    // ========================================
    // CONSTRUCTOR AND INITIALIZATION
    // ========================================

    private PartyMechanics() {
        this.logger = YakRealms.getInstance().getLogger();
        this.playerManager = YakPlayerManager.getInstance();
        initialize();
    }

    /**
     * Get singleton instance
     */
    public static PartyMechanics getInstance() {
        if (instance == null) {
            synchronized (PartyMechanics.class) {
                if (instance == null) {
                    instance = new PartyMechanics();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the party mechanics system with integrated scoreboard support
     */
    private void initialize() {
        try {
            // Start background tasks (includes scoreboard tasks)
            startInviteCleanupTask();
            startPartyMaintenanceTask();
            startStatisticsTask();
            startScoreboardTasks();

            // Register event listener
            Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());

            logger.info("PartyMechanics initialized with integrated scoreboard support");

        } catch (Exception e) {
            logger.severe("Failed to initialize PartyMechanics: " + e.getMessage());
        }
    }

    /**
     * Start scoreboard maintenance tasks
     */
    private void startScoreboardTasks() {
        // Health update task - runs frequently to keep health displays accurate
        scoreboardHealthUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PartyScoreboards.updateAllPlayerHealth();
                } catch (Exception e) {
                    logger.warning("Error in scoreboard health update task: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Every second

        // Scoreboard maintenance task - runs less frequently for validation and cleanup
        scoreboardMaintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PartyScoreboards.validateAndRepairScoreboards();
                } catch (Exception e) {
                    logger.warning("Error in scoreboard maintenance task: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 1200L, 1200L); // Every minute
    }

    /**
     * Clean up resources (enhanced with scoreboard cleanup)
     */
    public void cleanup() {
        try {
            // Cancel tasks
            if (inviteCleanupTask != null) {
                inviteCleanupTask.cancel();
            }
            if (partyMaintenanceTask != null) {
                partyMaintenanceTask.cancel();
            }
            if (statisticsTask != null) {
                statisticsTask.cancel();
            }
            if (scoreboardHealthUpdateTask != null) {
                scoreboardHealthUpdateTask.cancel();
            }
            if (scoreboardMaintenanceTask != null) {
                scoreboardMaintenanceTask.cancel();
            }

            // Clean up scoreboards
            PartyScoreboards.cleanupAll();

            // Clear data
            parties.clear();
            invites.clear();
            playerToPartyMap.clear();
            eventListeners.clear();

            logger.info("PartyMechanics cleanup completed (including scoreboards)");

        } catch (Exception e) {
            logger.warning("Error during PartyMechanics cleanup: " + e.getMessage());
        }
    }

    // ========================================
    // PARTY CREATION AND MANAGEMENT (ENHANCED WITH SCOREBOARD INTEGRATION)
    // ========================================

    /**
     * Create a new party with integrated scoreboard support
     */
    public boolean createParty(Player player) {
        if (player == null) return false;
        return createParty(player.getUniqueId());
    }

    public boolean createParty(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            // Check if player already in a party
            if (isInParty(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    MessageUtil.sendError(player, "You are already in a party!");
                    MessageUtil.sendTip(player, "Leave your current party first with /pquit");
                }
                return false;
            }

            // Check permissions
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && !PermissionUtil.hasPartyPermission(player, "create")) {
                return false; // Error message handled by PermissionUtil
            }

            // Check cooldown
            if (player != null && !CooldownManager.checkAndApplyCooldown(player, "party_create", 5000, "creating parties")) {
                return false;
            }

            // Create new party
            UUID partyId = UUID.randomUUID();
            Party party = new Party(partyId, playerId, maxPartySize);

            parties.put(partyId, party);
            playerToPartyMap.put(playerId, partyId);

            // Send success messages
            if (player != null) {
                MessageUtil.sendSuccess(player, ChatColor.BOLD + "Party created successfully!");

                MessageUtil.sendBlankLine(player);
                player.sendMessage(ChatColor.GRAY + "🎯 " + ChatColor.WHITE + "Party Features:");
                player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/pinvite <player>" + ChatColor.GRAY + " - Invite players");
                player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/p <message>" + ChatColor.GRAY + " - Party chat");
                player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " - View party details");

                MessageUtil.sendBlankLine(player);
                MessageUtil.sendTip(player, "You can invite up to " + (maxPartySize - 1) + " more players!");

                // Play creation effects
                SoundUtil.playSuccess(player);
                showPartyCreationEffects(player);

                // INTEGRATED: Update scoreboard for party creation
                PartyScoreboards.showPartyJoinEffects(player);
                PartyScoreboards.updatePlayerScoreboard(player);
            }

            // Fire event
            fireEvent(listener -> listener.onPartyCreate(party));

            logger.info("Party created by " + getPlayerName(playerId) + " (ID: " + partyId + ")");
            return true;

        } catch (Exception e) {
            logger.warning("Error creating party for " + playerId + ": " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Disband a party with confirmation and feedback (enhanced with scoreboard cleanup)
     */
    public boolean disbandParty(Player player) {
        if (player == null) return false;
        return disbandParty(player.getUniqueId());
    }

    public boolean disbandParty(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            Party party = getParty(playerId);
            if (party == null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    MessageUtil.sendError(player, "You are not in a party!");
                }
                return false;
            }

            // Check if player is leader
            if (!party.isLeader(playerId)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    MessageUtil.sendError(player, "Only the party leader can disband the party!");
                    MessageUtil.sendTip(player, "Use /pquit to leave the party instead.");
                }
                return false;
            }

            // Check permissions
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && !PermissionUtil.hasPartyPermission(player, "disband")) {
                return false; // Error message handled by PermissionUtil
            }

            // Notify all members and clear their scoreboards
            Set<UUID> membersCopy = new HashSet<>(party.getAllMembers());
            for (UUID memberId : membersCopy) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    if (memberId.equals(playerId)) {
                        MessageUtil.sendWarning(member, ChatColor.BOLD + "You disbanded the party.");
                    } else {
                        MessageUtil.sendWarning(member, ChatColor.BOLD + "The party has been disbanded.");
                        member.sendMessage(ChatColor.GRAY + "Party leader " + getPlayerName(playerId) + " disbanded the party.");
                    }

                    SoundUtil.playPartyLeave(member);
                    showPartyDisbandEffects(member);

                    // INTEGRATED: Clear scoreboard for disbanded party member
                    PartyScoreboards.clearPlayerScoreboard(member);
                }

                // Remove from mapping
                playerToPartyMap.remove(memberId);
            }

            // Remove party
            parties.remove(party.getId());

            // Fire event
            fireEvent(listener -> listener.onPartyDisband(party));

            logger.info("Party disbanded by " + getPlayerName(playerId) + " (ID: " + party.getId() + ")");
            return true;

        } catch (Exception e) {
            logger.warning("Error disbanding party for " + playerId + ": " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========================================
    // PARTY INVITATION SYSTEM (UNCHANGED BUT WITH SCOREBOARD INTEGRATION)
    // ========================================

    /**
     * Invite a player to party with validation and feedback
     */
    public boolean invitePlayerToParty(Player invited, Player inviter, String message) {
        if (invited == null || inviter == null) return false;
        return invitePlayerToParty(invited.getUniqueId(), inviter.getUniqueId(), message);
    }

    public boolean invitePlayerToParty(UUID invitedId, UUID inviterId, String message) {
        if (invitedId == null || inviterId == null) return false;

        lock.writeLock().lock();
        try {
            Player inviter = Bukkit.getPlayer(inviterId);
            Player invited = Bukkit.getPlayer(invitedId);

            // Basic validation
            if (invitedId.equals(inviterId)) {
                if (inviter != null) {
                    MessageUtil.sendError(inviter, "You cannot invite yourself to your own party!");
                    MessageUtil.sendTip(inviter, "Ask another player to join you instead.");
                }
                return false;
            }

            // Check target validation using PlayerResolver
            if (invited == null || !invited.isOnline()) {
                if (inviter != null) {
                    MessageUtil.sendError(inviter, PlayerResolver.getName(invited) + " is not online!");
                    MessageUtil.sendTip(inviter, "Make sure to type the exact username.");
                }
                return false;
            }

            // Check permissions
            if (inviter != null && !PermissionUtil.hasPartyPermission(inviter, "invite")) {
                return false; // Error message handled by PermissionUtil
            }

            // Check cooldown
            if (inviter != null && !CooldownManager.checkAndApplyCooldown(inviter, PARTY_INVITE_COOLDOWN, 3000, "sending invitations")) {
                return false;
            }

            // Get or create party for inviter
            Party party = getParty(inviterId);
            if (party == null) {
                if (!createParty(inviterId)) {
                    if (inviter != null) {
                        MessageUtil.sendError(inviter, "Failed to create party.");
                    }
                    return false;
                }
                party = getParty(inviterId);
            }

            if (party == null || !party.hasPermission(inviterId, PartyPermission.INVITE)) {
                if (inviter != null) {
                    MessageUtil.sendError(inviter, "You don't have permission to invite players!");
                    MessageUtil.sendTip(inviter, "Ask the party leader to promote you or invite the player yourself.");
                }
                return false;
            }

            // Check if party is full
            if (party.getSize() >= party.getMaxSize()) {
                if (inviter != null) {
                    MessageUtil.sendError(inviter, "Your party is full (" + party.getMaxSize() + " players max)!");
                    MessageUtil.sendTip(inviter, "Remove someone first or create a new party.");
                }
                return false;
            }

            // Check if invited player is already in a party
            if (isInParty(invitedId)) {
                if (arePartyMembers(inviterId, invitedId)) {
                    if (inviter != null) {
                        MessageUtil.sendWarning(inviter, PlayerResolver.getName(invited) + " is already in your party!");
                    }
                } else {
                    if (inviter != null) {
                        MessageUtil.sendError(inviter, PlayerResolver.getName(invited) + " is already in another party!");
                        MessageUtil.sendTip(inviter, "They need to leave their current party first.");
                    }
                }
                return false;
            }

            // Check if player already has a pending invite
            if (invites.containsKey(invitedId)) {
                if (inviter != null) {
                    MessageUtil.sendError(inviter, PlayerResolver.getName(invited) + " already has a pending party invite!");
                    MessageUtil.sendTip(inviter, "Wait for them to respond to their current invitation.");
                }
                return false;
            }

            // Check cross-world restrictions
            if (!allowCrossWorldParties && inviter != null && invited != null &&
                    !inviter.getWorld().equals(invited.getWorld())) {
                MessageUtil.sendError(inviter, "Cannot invite players from different worlds!");
                MessageUtil.sendTip(inviter, "Both players must be in the same world.");
                return false;
            }

            // Create and send invite
            PartyInvite invite = new PartyInvite(party.getId(), inviterId, invitedId, message);
            invites.put(invitedId, invite);

            // Send invitation messages
            sendInviteMessages(invited, inviter, party, message);

            // Fire event
            Party finalParty = party;
            fireEvent(listener -> listener.onPartyInvite(finalParty, inviterId, invitedId));

            logger.info("Party invite sent: " + getPlayerName(inviterId) + " -> " + getPlayerName(invitedId));
            return true;

        } catch (Exception e) {
            logger.warning("Error sending party invite: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Send invitation messages
     */
    private void sendInviteMessages(Player invited, Player inviter, Party party, String message) {
        if (invited == null || inviter == null) return;

        try {
            // Message to invited player
            MessageUtil.sendHeader(invited, "PARTY INVITATION");

            invited.sendMessage(ChatColor.GRAY + "From: " + ChatColor.YELLOW + ChatColor.BOLD + inviter.getName());
            invited.sendMessage(ChatColor.GRAY + "Party Size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());

            if (message != null && !message.trim().isEmpty()) {
                MessageUtil.sendBlankLine(invited);
                invited.sendMessage(ChatColor.GRAY + "Message: " + ChatColor.WHITE + ChatColor.ITALIC + "\"" + message + "\"");
            }

            MessageUtil.sendBlankLine(invited);
            invited.sendMessage(ChatColor.GREEN + "• " + ChatColor.BOLD + "/paccept" + ChatColor.GREEN + " - Join the party");
            invited.sendMessage(ChatColor.RED + "• " + ChatColor.BOLD + "/pdecline" + ChatColor.RED + " - Decline invitation");

            MessageUtil.sendBlankLine(invited);
            invited.sendMessage(ChatColor.GRAY + "⏰ This invitation expires in " + ChatColor.YELLOW + inviteExpiryTimeSeconds + " seconds");

            // Play invitation sound
            SoundUtil.playPartyInvite(invited);

            // Message to inviter
            MessageUtil.sendSuccess(inviter, ChatColor.BOLD + "Party invitation sent!");
            inviter.sendMessage(ChatColor.GRAY + "Invited " + ChatColor.YELLOW + ChatColor.BOLD + invited.getName() +
                    ChatColor.GRAY + " to your party.");

            if (message != null && !message.trim().isEmpty()) {
                inviter.sendMessage(ChatColor.GRAY + "Message: " + ChatColor.ITALIC + "\"" + message + "\"");
            }

            // Show party status to inviter
            showPartyStatus(inviter);
            SoundUtil.playConfirmation(inviter);

        } catch (Exception e) {
            logger.warning("Error sending invite messages: " + e.getMessage());
        }
    }

    /**
     * Accept a party invitation with integrated scoreboard support
     */
    public boolean acceptPartyInvite(Player player) {
        return player != null && acceptPartyInvite(player.getUniqueId());
    }

    public boolean acceptPartyInvite(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            Player player = Bukkit.getPlayer(playerId);

            // Check for pending invite
            PartyInvite invite = invites.remove(playerId);
            if (invite == null) {
                if (player != null) {
                    MessageUtil.sendError(player, "You don't have any pending party invitations!");
                    MessageUtil.sendTip(player, "Ask someone to invite you with /pinvite <your name>");
                }
                return false;
            }

            // Check if invite expired
            if (invite.isExpired(inviteExpiryTimeSeconds)) {
                if (player != null) {
                    MessageUtil.sendError(player, "That party invitation has expired!");
                    MessageUtil.sendTip(player, "Ask for a new invitation.");
                }
                return false;
            }

            // Check if party still exists
            Party party = parties.get(invite.getPartyId());
            if (party == null) {
                if (player != null) {
                    MessageUtil.sendError(player, "That party no longer exists!");
                    MessageUtil.sendTip(player, "The party may have been disbanded.");
                }
                return false;
            }

            // Check if party is full
            if (party.getSize() >= party.getMaxSize()) {
                if (player != null) {
                    MessageUtil.sendError(player, "That party is now full!");
                    MessageUtil.sendTip(player, "Try asking for an invitation to a different party.");
                }
                return false;
            }

            // Check if player is already in a party
            if (isInParty(playerId)) {
                if (player != null) {
                    MessageUtil.sendError(player, "You are already in a party!");
                    MessageUtil.sendTip(player, "Leave your current party first with /pquit");
                }
                return false;
            }

            // Add player to party
            if (addPlayerToParty(playerId, invite.getPartyId())) {
                // Success feedback
                if (player != null) {
                    MessageUtil.sendSuccess(player, ChatColor.BOLD + "Welcome to the party!");

                    MessageUtil.sendBlankLine(player);
                    player.sendMessage(ChatColor.GRAY + "🎉 " + ChatColor.WHITE + "You're now part of the team!");
                    player.sendMessage(ChatColor.GRAY + "Party Size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());

                    MessageUtil.sendBlankLine(player);
                    player.sendMessage(ChatColor.GRAY + "💬 " + ChatColor.WHITE + "Quick Start:");
                    player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/p <message>" + ChatColor.GRAY + " - Chat with party");
                    player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/p info" + ChatColor.GRAY + " - View party details");
                    player.sendMessage(ChatColor.GRAY + "  • " + ChatColor.YELLOW + "/p help" + ChatColor.GRAY + " - See all commands");

                    MessageUtil.sendBlankLine(player);
                    MessageUtil.sendTip(player, "Use @i@ in party chat to show items you're holding!");

                    SoundUtil.playPartyJoin(player);

                    // INTEGRATED: Show join effects and update scoreboard
                    PartyScoreboards.showPartyJoinEffects(player);
                    PartyScoreboards.updatePlayerScoreboard(player);
                }

                return true;
            } else {
                if (player != null) {
                    MessageUtil.sendError(player, "Failed to join the party!");
                    MessageUtil.sendTip(player, "Please try again or contact staff if the issue persists.");
                }
                return false;
            }

        } catch (Exception e) {
            logger.warning("Error accepting party invite for " + playerId + ": " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Decline a party invitation with polite feedback
     */
    public boolean declinePartyInvite(Player player) {
        return player != null && declinePartyInvite(player.getUniqueId());
    }

    public boolean declinePartyInvite(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            Player player = Bukkit.getPlayer(playerId);

            // Check for pending invite
            PartyInvite invite = invites.remove(playerId);
            if (invite == null) {
                if (player != null) {
                    MessageUtil.sendError(player, "You don't have any pending party invitations to decline!");
                    MessageUtil.sendTip(player, "Invitations expire automatically after " + inviteExpiryTimeSeconds + " seconds.");
                }
                return false;
            }

            // Notify players
            String inviterName = getPlayerName(invite.getInviter());
            String invitedName = getPlayerName(playerId);

            if (player != null) {
                MessageUtil.sendWarning(player, ChatColor.BOLD + "Party invitation declined.");
                player.sendMessage(ChatColor.GRAY + "Politely declined " + ChatColor.YELLOW + inviterName +
                        ChatColor.GRAY + "'s party invitation.");
                SoundUtil.playWarning(player);
            }

            // Notify inviter
            Player inviter = Bukkit.getPlayer(invite.getInviter());
            if (inviter != null) {
                MessageUtil.sendWarning(inviter, invitedName + " declined your party invitation.");
                MessageUtil.sendTip(inviter, "No worries! You can invite other players or try again later.");
                SoundUtil.playWarning(inviter);
            }

            // Fire event
            fireEvent(listener -> listener.onPartyInviteDeclined(invite));

            logger.info("Party invite declined: " + invitedName + " declined " + inviterName + "'s invite");
            return true;

        } catch (Exception e) {
            logger.warning("Error declining party invite for " + playerId + ": " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========================================
    // PARTY MEMBERSHIP MANAGEMENT (ENHANCED WITH SCOREBOARD INTEGRATION)
    // ========================================

    /**
     * Add player to party with comprehensive feedback and scoreboard updates
     */
    private boolean addPlayerToParty(UUID playerId, UUID partyId) {
        try {
            Party party = parties.get(partyId);
            if (party == null) return false;

            party.addMember(playerId);
            playerToPartyMap.put(playerId, partyId);

            // Notify all party members and update their scoreboards
            String playerName = getPlayerName(playerId);
            List<Player> members = getPartyMembers(party);

            for (Player member : members) {
                if (member.getUniqueId().equals(playerId)) {
                    // Welcome message already sent in acceptPartyInvite
                    continue;
                }

                MessageUtil.sendSuccess(member, playerName + " joined the party!");
                member.sendMessage(ChatColor.GRAY + "Party Size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());
                SoundUtil.playPartyJoin(member);

                // INTEGRATED: Update existing members' scoreboards
                PartyScoreboards.updatePlayerScoreboard(member);
            }

            // Fire event
            fireEvent(listener -> listener.onPlayerJoinParty(party, playerId));

            return true;

        } catch (Exception e) {
            logger.warning("Error adding player to party: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove player from party (leave) with scoreboard cleanup
     */
    public boolean leaveParty(Player player) {
        return player != null && leaveParty(player.getUniqueId());
    }

    public boolean leaveParty(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            Party party = getParty(playerId);
            if (party == null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    MessageUtil.sendError(player, "You are not in a party!");
                }
                return false;
            }

            String playerName = getPlayerName(playerId);
            boolean wasLeader = party.isLeader(playerId);

            // Remove player from party
            party.removeMember(playerId);
            playerToPartyMap.remove(playerId);

            Player leavingPlayer = Bukkit.getPlayer(playerId);

            // Handle different scenarios
            if (party.getSize() == 0) {
                // Party is now empty, disband it
                parties.remove(party.getId());

                if (leavingPlayer != null) {
                    MessageUtil.sendSuccess(leavingPlayer, ChatColor.BOLD + "You left the party.");
                    leavingPlayer.sendMessage(ChatColor.GRAY + "The party has been disbanded.");

                    // INTEGRATED: Clear scoreboard for leaving player
                    PartyScoreboards.clearPlayerScoreboard(leavingPlayer);
                }

                fireEvent(listener -> listener.onPartyDisband(party));

            } else if (wasLeader) {
                // Transfer leadership
                UUID newLeader = party.getMembers().iterator().next();
                party.setLeader(newLeader);

                // Notify everyone and update scoreboards
                List<Player> remainingMembers = getPartyMembers(party);
                String newLeaderName = getPlayerName(newLeader);

                for (Player member : remainingMembers) {
                    MessageUtil.sendWarning(member, playerName + " left the party.");
                    member.sendMessage(ChatColor.GRAY + "Leadership transferred to " + ChatColor.YELLOW + newLeaderName);
                    SoundUtil.playPartyLeave(member);

                    // INTEGRATED: Update remaining members' scoreboards
                    PartyScoreboards.updatePlayerScoreboard(member);

                    // Show leadership effects for new leader
                    if (member.getUniqueId().equals(newLeader)) {
                        PartyScoreboards.showLeaderPromotionEffects(member);
                    }
                }

                if (leavingPlayer != null) {
                    MessageUtil.sendSuccess(leavingPlayer, ChatColor.BOLD + "You left the party.");
                    leavingPlayer.sendMessage(ChatColor.GRAY + "Leadership transferred to " + ChatColor.YELLOW + newLeaderName);

                    // INTEGRATED: Clear scoreboard for leaving player
                    PartyScoreboards.clearPlayerScoreboard(leavingPlayer);
                }

                fireEvent(listener -> listener.onPartyLeaderChange(party, playerId, newLeader));

            } else {
                // Regular member leaving
                List<Player> remainingMembers = getPartyMembers(party);

                for (Player member : remainingMembers) {
                    MessageUtil.sendWarning(member, playerName + " left the party.");
                    member.sendMessage(ChatColor.GRAY + "Party Size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());
                    SoundUtil.playPartyLeave(member);

                    // INTEGRATED: Update remaining members' scoreboards
                    PartyScoreboards.updatePlayerScoreboard(member);
                }

                if (leavingPlayer != null) {
                    MessageUtil.sendSuccess(leavingPlayer, ChatColor.BOLD + "You left the party.");

                    // INTEGRATED: Clear scoreboard for leaving player
                    PartyScoreboards.clearPlayerScoreboard(leavingPlayer);
                }
            }

            // Fire event
            fireEvent(listener -> listener.onPlayerLeaveParty(party, playerId));

            logger.info("Player left party: " + playerName + " (was leader: " + wasLeader + ")");
            return true;

        } catch (Exception e) {
            logger.warning("Error processing party leave for " + playerId + ": " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Kick player from party with scoreboard updates
     */
    public boolean kickPlayerFromParty(Player kicker, String targetName) {
        if (kicker == null || targetName == null) return false;

        // Resolve target player
        Player target = PlayerResolver.resolvePlayerWithMessage(kicker, targetName);
        if (target == null) {
            return false; // Error message handled by PlayerResolver
        }

        return kickPlayerFromParty(kicker.getUniqueId(), target.getUniqueId());
    }

    public boolean kickPlayerFromParty(UUID kickerId, UUID targetId) {
        if (kickerId == null || targetId == null) return false;

        lock.writeLock().lock();
        try {
            Player kicker = Bukkit.getPlayer(kickerId);
            Player target = Bukkit.getPlayer(targetId);

            // Check permissions
            if (kicker != null && !PermissionUtil.hasPartyPermission(kicker, "kick")) {
                return false; // Error message handled by PermissionUtil
            }

            // Check cooldown
            if (kicker != null && !CooldownManager.checkAndApplyCooldown(kicker, PARTY_KICK_COOLDOWN, 5000, "kicking players")) {
                return false;
            }

            Party party = getParty(kickerId);
            if (party == null) {
                if (kicker != null) {
                    MessageUtil.sendError(kicker, "You are not in a party!");
                }
                return false;
            }

            // Check if kicker has permission to kick
            if (!party.hasPermission(kickerId, PartyPermission.KICK)) {
                if (kicker != null) {
                    MessageUtil.sendError(kicker, "You don't have permission to kick players!");
                    MessageUtil.sendTip(kicker, "Only party leaders and officers can kick members.");
                }
                return false;
            }

            // Check if target is in the same party
            if (!arePartyMembers(kickerId, targetId)) {
                if (kicker != null) {
                    MessageUtil.sendError(kicker, PlayerResolver.getName(target) + " is not in your party!");
                }
                return false;
            }

            // Can't kick yourself
            if (kickerId.equals(targetId)) {
                if (kicker != null) {
                    MessageUtil.sendError(kicker, "You cannot kick yourself!");
                    MessageUtil.sendTip(kicker, "Use /pquit to leave the party instead.");
                }
                return false;
            }

            // Can't kick the leader (unless you're admin)
            if (party.isLeader(targetId) && !PermissionUtil.canBypassPartyRestrictions(kicker)) {
                if (kicker != null) {
                    MessageUtil.sendError(kicker, "You cannot kick the party leader!");
                }
                return false;
            }

            // Perform the kick
            String kickerName = getPlayerName(kickerId);
            String targetName = getPlayerName(targetId);

            // Remove target from party
            party.removeMember(targetId);
            playerToPartyMap.remove(targetId);

            // Notify all party members and update their scoreboards
            List<Player> members = getPartyMembers(party);
            for (Player member : members) {
                MessageUtil.sendWarning(member, targetName + " was kicked from the party by " + kickerName);
                SoundUtil.playWarning(member);

                // INTEGRATED: Update remaining members' scoreboards
                PartyScoreboards.updatePlayerScoreboard(member);
            }

            // Notify the kicked player and clear their scoreboard
            if (target != null) {
                MessageUtil.sendError(target, "You were kicked from the party by " + kickerName + "!");
                MessageUtil.sendTip(target, "You can create your own party with /pinvite <player>");
                SoundUtil.playError(target);

                // INTEGRATED: Clear scoreboard for kicked player
                PartyScoreboards.clearPlayerScoreboard(target);
            }

            // Fire event
            fireEvent(listener -> listener.onPlayerKicked(party, kickerId, targetId));

            logger.info("Player kicked from party: " + targetName + " by " + kickerName);
            return true;

        } catch (Exception e) {
            logger.warning("Error kicking player from party: " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ========================================
    // PARTY MESSAGING SYSTEM (UNCHANGED)
    // ========================================

    /**
     * Send message to party with enhanced features
     */
    public boolean sendPartyMessage(Player sender, String message) {
        if (sender == null || message == null) return false;
        return sendPartyMessage(sender.getUniqueId(), message);
    }

    public boolean sendPartyMessage(UUID senderId, String message) {
        if (senderId == null || message == null || message.trim().isEmpty()) return false;

        lock.readLock().lock();
        try {
            Party party = getParty(senderId);
            if (party == null) {
                Player sender = Bukkit.getPlayer(senderId);
                if (sender != null) {
                    MessageUtil.sendError(sender, "You are not in a party!");
                    MessageUtil.sendTip(sender, "Join a party first with /pinvite <player> or /paccept");
                }
                return false;
            }

            Player sender = Bukkit.getPlayer(senderId);
            if (sender != null && !PermissionUtil.hasChatPermission(sender, "party")) {
                return false; // Error message handled by PermissionUtil
            }

            // Get party members
            List<Player> members = getPartyMembers(party);
            if (members.isEmpty()) {
                if (sender != null) {
                    MessageUtil.sendError(sender, "No party members are online!");
                }
                return false;
            }

            // Format the message
            String senderName = getPlayerName(senderId);
            String formattedMessage = formatPartyMessage(party, senderId, senderName, message);

            // Send to all online party members
            int deliveredCount = 0;
            for (Player member : members) {
                if (member != null && member.isOnline()) {
                    member.sendMessage(formattedMessage);

                    // Play subtle sound for recipients (not sender)
                    if (!member.getUniqueId().equals(senderId)) {
                        SoundUtil.playNotification(member);
                    }

                    deliveredCount++;
                }
            }

            // Confirm delivery to sender
            if (sender != null && deliveredCount > 1) {
                MessageUtil.sendBlankLine(sender);
                MessageUtil.sendTip(sender, "Message delivered to " + (deliveredCount - 1) + " party members.");
            }

            // Fire event
            fireEvent(listener -> listener.onPartyMessage(party, senderId, message));

            return true;

        } catch (Exception e) {
            logger.warning("Error sending party message: " + e.getMessage());
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Format party message with enhanced styling
     */
    private String formatPartyMessage(Party party, UUID senderId, String senderName, String message) {
        if (party == null || senderId == null || senderName == null || message == null) {
            return "";
        }

        StringBuilder formatted = new StringBuilder();

        // Party prefix with styling
        formatted.append(ChatColor.LIGHT_PURPLE).append("<").append(ChatColor.BOLD).append("P").append(ChatColor.LIGHT_PURPLE).append(">");

        // Add role indicators
        if (party.isLeader(senderId)) {
            formatted.append(ChatColor.GOLD).append("★");
        } else if (party.isOfficer(senderId)) {
            formatted.append(ChatColor.YELLOW).append("♦");
        }

        // Add sender name and message
        formatted.append(" ").append(ChatColor.WHITE).append(senderName).append(": ").append(ChatColor.WHITE).append(message);

        return formatted.toString();
    }

    // ========================================
    // ENHANCED EVENT HANDLERS WITH SCOREBOARD INTEGRATION
    // ========================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Clean up any expired invites
            PartyInvite invite = invites.get(playerId);
            if (invite != null && invite.isExpired(inviteExpiryTimeSeconds)) {
                invites.remove(playerId);
            }

            // INTEGRATED: Update scoreboards after player joins
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    PartyScoreboards.updatePlayerScoreboard(player);
                    PartyScoreboards.updateAllPlayerColors();
                } catch (Exception e) {
                    logger.warning("Error updating scoreboards for joined player " + player.getName() + ": " + e.getMessage());
                }
            }, 10L); // Half-second delay

        } catch (Exception e) {
            logger.warning("Error handling player join for party system: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        try {
            // INTEGRATED: Clean up scoreboard for quitting player
            PartyScoreboards.cleanupPlayer(player);

            // Update remaining players' scoreboards
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    PartyScoreboards.refreshAllPartyScoreboards();
                } catch (Exception e) {
                    logger.warning("Error refreshing scoreboards after player quit: " + e.getMessage());
                }
            }, 5L);

        } catch (Exception e) {
            logger.warning("Error handling player quit for party system: " + e.getMessage());
        }

        // Note: We don't automatically remove players from parties on logout
        // This allows for temporary disconnections
    }

    /**
     * INTEGRATED: Handle player damage events for health display updates
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        try {
            // Small delay to ensure health is updated after damage
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                updatePlayerHealthDisplays(player);
            }, 1L);

        } catch (Exception e) {
            // Ignore individual health update errors
        }
    }

    /**
     * INTEGRATED: Handle player healing events for health display updates
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        try {
            // Small delay to ensure health is updated after healing
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                updatePlayerHealthDisplays(player);
            }, 1L);

        } catch (Exception e) {
            // Ignore individual health update errors
        }
    }

    /**
     * INTEGRATED: Update health displays for a specific player across all relevant scoreboards
     */
    private void updatePlayerHealthDisplays(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            // If player is in a party, update all party members' scoreboards
            if (isInParty(player)) {
                for (Player partyMember : getPartyMembers(player)) {
                    if (partyMember != null && partyMember.isOnline()) {
                        PartyScoreboards.updatePlayerScoreboard(partyMember);
                    }
                }
            }

            // Update the player's own scoreboard
            PartyScoreboards.updatePlayerScoreboard(player);

        } catch (Exception e) {
            // Ignore health update errors to prevent spam
        }
    }

    // ========================================
    // PUBLIC SCOREBOARD INTEGRATION METHODS
    // ========================================

    /**
     * Handle alignment change - call this when a player's alignment changes
     */
    public void handleAlignmentChange(Player player) {
        try {
            PartyScoreboards.handleAlignmentChange(player);
        } catch (Exception e) {
            logger.warning("Error handling alignment change for " +
                    (player != null ? player.getName() : "unknown") + ": " + e.getMessage());
        }
    }

    /**
     * Force refresh all scoreboards - useful for debugging or after major changes
     */
    public void forceRefreshAllScoreboards() {
        try {
            PartyScoreboards.forceRefreshAll();
            logger.info("Forced refresh of all party scoreboards");
        } catch (Exception e) {
            logger.warning("Error forcing refresh of all scoreboards: " + e.getMessage());
        }
    }

    /**
     * Get scoreboard diagnostic information
     */
    public String getScoreboardDiagnostics() {
        try {
            StringBuilder diag = new StringBuilder();
            diag.append("=== Integrated Party Scoreboard Diagnostics ===\n");

            var stats = PartyScoreboards.getPartyStats();
            for (var entry : stats.entrySet()) {
                diag.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }

            diag.append("scoreboard_health_task_active: ").append(scoreboardHealthUpdateTask != null && !scoreboardHealthUpdateTask.isCancelled()).append("\n");
            diag.append("scoreboard_maintenance_task_active: ").append(scoreboardMaintenanceTask != null && !scoreboardMaintenanceTask.isCancelled()).append("\n");

            return diag.toString();
        } catch (Exception e) {
            return "Error generating scoreboard diagnostics: " + e.getMessage();
        }
    }

    // ========================================
    // PARTY INFORMATION AND STATUS (UNCHANGED)
    // ========================================

    /**
     * Get party members as Player objects
     */
    public List<Player> getPartyMembers(Player player) {
        return player != null ? getPartyMembers(player.getUniqueId()) : new ArrayList<>();
    }

    public List<Player> getPartyMembers(UUID playerId) {
        Party party = getParty(playerId);
        return getPartyMembers(party);
    }

    private List<Player> getPartyMembers(Party party) {
        if (party == null) return new ArrayList<>();

        return party.getAllMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Show enhanced party status
     */
    private void showPartyStatus(Player player) {
        if (player == null) return;

        try {
            Party party = getParty(player.getUniqueId());
            if (party == null) return;

            MessageUtil.sendBlankLine(player);
            player.sendMessage(ChatColor.GRAY + "🎯 " + ChatColor.WHITE + "Party Status:");
            player.sendMessage(ChatColor.GRAY + "  Size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());
            player.sendMessage(ChatColor.GRAY + "  Your Role: " + ChatColor.WHITE +
                    (party.isLeader(player.getUniqueId()) ? ChatColor.GOLD + "Leader" :
                            party.isOfficer(player.getUniqueId()) ? ChatColor.YELLOW + "Officer" : "Member"));

        } catch (Exception e) {
            logger.warning("Error showing party status: " + e.getMessage());
        }
    }

    /**
     * Check if player is in a party
     */
    public boolean isInParty(Player player) {
        return player != null && isInParty(player.getUniqueId());
    }

    public boolean isInParty(UUID playerId) {
        return playerId != null && playerToPartyMap.containsKey(playerId);
    }

    /**
     * Check if two players are in the same party
     */
    public boolean arePartyMembers(Player player1, Player player2) {
        return player1 != null && player2 != null &&
                arePartyMembers(player1.getUniqueId(), player2.getUniqueId());
    }

    public boolean arePartyMembers(UUID playerId1, UUID playerId2) {
        if (playerId1 == null || playerId2 == null) return false;

        UUID partyId1 = playerToPartyMap.get(playerId1);
        UUID partyId2 = playerToPartyMap.get(playerId2);

        return partyId1 != null && partyId1.equals(partyId2);
    }

    /**
     * Check if player is party leader
     */
    public boolean isPartyLeader(Player player) {
        if (player == null) return false;

        Party party = getParty(player.getUniqueId());
        return party != null && party.isLeader(player.getUniqueId());
    }

    /**
     * Check if player is party officer
     */
    public boolean isPartyOfficer(Player player) {
        if (player == null) return false;

        Party party = getParty(player.getUniqueId());
        return party != null && party.isOfficer(player.getUniqueId());
    }

    /**
     * Get party for player
     */
    public Party getParty(UUID playerId) {
        if (playerId == null) return null;

        UUID partyId = playerToPartyMap.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }

    // ========================================
    // INVITATION STATUS METHODS (UNCHANGED)
    // ========================================

    /**
     * Check if player has pending invitation
     */
    public boolean hasPendingInvite(Player player) {
        return player != null && invites.containsKey(player.getUniqueId());
    }

    /**
     * Get name of player who sent the invitation
     */
    public String getPendingInviterName(Player player) {
        if (player == null) return null;

        PartyInvite invite = invites.get(player.getUniqueId());
        return invite != null ? getPlayerName(invite.getInviter()) : null;
    }

    /**
     * Get remaining time for invitation
     */
    public long getInviteTimeRemaining(Player player) {
        if (player == null) return 0;

        PartyInvite invite = invites.get(player.getUniqueId());
        if (invite == null) return 0;

        long elapsed = System.currentTimeMillis() - invite.getInviteTime();
        long remaining = (inviteExpiryTimeSeconds * 1000L) - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Get invite expiry time in seconds
     */
    public int getInviteExpiryTimeSeconds() {
        return inviteExpiryTimeSeconds;
    }

    // ========================================
    // VISUAL EFFECTS (UNCHANGED)
    // ========================================

    /**
     * Show party creation effects
     */
    private void showPartyCreationEffects(Player player) {
        if (player == null || !player.isOnline() || !enablePartyEffects) return;

        try {
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 2, 0), 10, 1, 1, 1, 0.1);
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    /**
     * Show party disband effects
     */
    private void showPartyDisbandEffects(Player player) {
        if (player == null || !player.isOnline() || !enablePartyEffects) return;

        try {
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    // ========================================
    // UTILITY METHODS (UNCHANGED)
    // ========================================

    /**
     * Get player name with fallback
     */
    private String getPlayerName(UUID playerId) {
        return PlayerResolver.getName(Bukkit.getPlayer(playerId));
    }

    /**
     * Fire event to all listeners
     */
    private void fireEvent(java.util.function.Consumer<PartyEventListener> eventAction) {
        try {
            eventListeners.forEach(eventAction);
        } catch (Exception e) {
            logger.warning("Error firing party event: " + e.getMessage());
        }
    }

    // ========================================
    // BACKGROUND TASKS (ENHANCED WITH SCOREBOARD TASKS)
    // ========================================

    /**
     * Start invite cleanup task
     */
    private void startInviteCleanupTask() {
        inviteCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredInvites();
                } catch (Exception e) {
                    logger.warning("Error in invite cleanup task: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 10L, 20L * 10L); // Every 10 seconds
    }

    /**
     * Start party maintenance task
     */
    private void startPartyMaintenanceTask() {
        partyMaintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupEmptyParties();
                    updatePartyStatistics();
                } catch (Exception e) {
                    logger.warning("Error in party maintenance task: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 60L, 20L * 60L); // Every minute
    }

    /**
     * Start statistics task
     */
    private void startStatisticsTask() {
        statisticsTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    savePartyStatistics();
                } catch (Exception e) {
                    logger.warning("Error in statistics task: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 300L, 20L * 300L); // Every 5 minutes
    }

    /**
     * Clean up expired invitations
     */
    private void cleanupExpiredInvites() {
        invites.entrySet().removeIf(entry -> {
            PartyInvite invite = entry.getValue();
            if (invite.isExpired(inviteExpiryTimeSeconds)) {
                // Notify players of expiry
                try {
                    Player invited = Bukkit.getPlayer(entry.getKey());
                    Player inviter = Bukkit.getPlayer(invite.getInviter());

                    if (invited != null && invited.isOnline()) {
                        MessageUtil.sendWarning(invited, "Party invitation from " + getPlayerName(invite.getInviter()) + " has expired.");
                    }

                    if (inviter != null && inviter.isOnline()) {
                        MessageUtil.sendWarning(inviter, "Party invite to " + getPlayerName(invite.getInvited()) + " has expired.");
                    }
                } catch (Exception e) {
                    logger.warning("Error notifying invite expiry: " + e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Clean up empty parties
     */
    private void cleanupEmptyParties() {
        parties.entrySet().removeIf(entry -> {
            Party party = entry.getValue();
            if (party.getSize() == 0) {
                logger.info("Cleaned up empty party: " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Update party statistics
     */
    private void updatePartyStatistics() {
        // This would integrate with a statistics system
        // For now, just log basic info
        if (parties.size() > 0) {
            logger.info("Active parties: " + parties.size() + ", Total players in parties: " + playerToPartyMap.size());
        }
    }

    /**
     * Save party statistics
     */
    private void savePartyStatistics() {
        // This would save statistics to database or file
        // Implementation depends on persistence system
    }

    // ========================================
    // CONFIGURATION GETTERS/SETTERS (UNCHANGED)
    // ========================================

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public void setMaxPartySize(int maxPartySize) {
        this.maxPartySize = Math.max(2, maxPartySize);
    }

    public int getInviteExpiryTime() {
        return inviteExpiryTimeSeconds;
    }

    public void setInviteExpiryTime(int seconds) {
        this.inviteExpiryTimeSeconds = Math.max(10, seconds);
    }

    public boolean isExperienceSharing() {
        return experienceSharing;
    }

    public void setExperienceSharing(boolean experienceSharing) {
        this.experienceSharing = experienceSharing;
    }

    public boolean isLootSharing() {
        return lootSharing;
    }

    public void setLootSharing(boolean lootSharing) {
        this.lootSharing = lootSharing;
    }

    public double getExperienceShareRadius() {
        return experienceShareRadius;
    }

    public void setExperienceShareRadius(double radius) {
        this.experienceShareRadius = Math.max(10.0, radius);
    }

    public boolean isAllowCrossWorldParties() {
        return allowCrossWorldParties;
    }

    public void setAllowCrossWorldParties(boolean allow) {
        this.allowCrossWorldParties = allow;
    }

    public boolean isEnablePartyEffects() {
        return enablePartyEffects;
    }

    public void setEnablePartyEffects(boolean enable) {
        this.enablePartyEffects = enable;
    }

    // ========================================
    // STATISTICS AND DIAGNOSTICS (ENHANCED)
    // ========================================

    /**
     * Get system statistics (enhanced with scoreboard stats)
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_parties", parties.size());
        stats.put("total_players_in_parties", playerToPartyMap.size());
        stats.put("pending_invites", invites.size());
        stats.put("max_party_size", maxPartySize);
        stats.put("invite_expiry_seconds", inviteExpiryTimeSeconds);
        stats.put("experience_sharing", experienceSharing);
        stats.put("cross_world_parties", allowCrossWorldParties);

        // Add scoreboard stats
        var scoreboardStats = PartyScoreboards.getPartyStats();
        stats.putAll(scoreboardStats);

        return stats;
    }

    /**
     * Get system diagnostics (enhanced with scoreboard info)
     */
    public String getDiagnostics() {
        StringBuilder diag = new StringBuilder();
        diag.append("=== Integrated PartyMechanics Diagnostics ===\n");
        diag.append("Active Parties: ").append(parties.size()).append("\n");
        diag.append("Players in Parties: ").append(playerToPartyMap.size()).append("\n");
        diag.append("Pending Invites: ").append(invites.size()).append("\n");
        diag.append("Event Listeners: ").append(eventListeners.size()).append("\n");
        diag.append("Max Party Size: ").append(maxPartySize).append("\n");
        diag.append("Invite Expiry: ").append(inviteExpiryTimeSeconds).append("s\n");
        diag.append("Experience Sharing: ").append(experienceSharing).append("\n");
        diag.append("Cross-World Parties: ").append(allowCrossWorldParties).append("\n");
        diag.append("Party Effects: ").append(enablePartyEffects).append("\n");

        // Add scoreboard diagnostics
        diag.append("\n").append(getScoreboardDiagnostics());

        return diag.toString();
    }

    // ========================================
    // PARTY CLASS DEFINITIONS (UNCHANGED)
    // ========================================

    /**
     * Party class with comprehensive functionality
     */
    public static class Party {
        private final UUID id;
        private UUID leader;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Set<UUID> officers = ConcurrentHashMap.newKeySet();
        private final int maxSize;
        private final long creationTime;
        private final Map<String, Object> settings = new ConcurrentHashMap<>();

        public Party(UUID id, UUID leader, int maxSize) {
            this.id = id;
            this.leader = leader;
            this.maxSize = maxSize;
            this.creationTime = System.currentTimeMillis();
            this.members.add(leader);
        }

        // Basic getters
        public UUID getId() {
            return id;
        }

        public UUID getLeader() {
            return leader;
        }

        public void setLeader(UUID leader) {
            this.leader = leader;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public int getSize() {
            return members.size();
        }

        // Member management
        public Set<UUID> getMembers() {
            return new HashSet<>(members);
        }

        public Set<UUID> getAllMembers() {
            return new HashSet<>(members);
        }

        public void addMember(UUID playerId) {
            if (playerId != null) members.add(playerId);
        }

        public void removeMember(UUID playerId) {
            if (playerId != null) {
                members.remove(playerId);
                officers.remove(playerId);
            }
        }

        // Role management
        public boolean isLeader(UUID playerId) {
            return leader != null && leader.equals(playerId);
        }

        public boolean isOfficer(UUID playerId) {
            return officers.contains(playerId);
        }

        public void promoteToOfficer(UUID playerId) {
            if (playerId != null) officers.add(playerId);
        }

        public void demoteFromOfficer(UUID playerId) {
            if (playerId != null) officers.remove(playerId);
        }

        // Permission checking
        public boolean hasPermission(UUID playerId, PartyPermission permission) {
            if (playerId == null || permission == null) return false;

            if (isLeader(playerId)) return true; // Leaders have all permissions

            switch (permission) {
                case INVITE:
                case CHAT:
                case LEAVE:
                case WARP:
                    return true; // All members can do these
                case KICK:
                case PROMOTE_OFFICER:
                case DEMOTE_OFFICER:
                case SET_WARP:
                    return isOfficer(playerId); // Officers and leaders
                case DISBAND:
                case CHANGE_SETTINGS:
                    return false; // Only leaders (handled above)
                default:
                    return false;
            }
        }

        // Settings management
        public Object getSetting(String key) {
            return key != null ? settings.get(key) : null;
        }

        public void setSetting(String key, Object value) {
            if (key != null) settings.put(key, value);
        }

        public boolean getBooleanSetting(String key, boolean defaultValue) {
            Object value = getSetting(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
    }

    /**
     * Party invitation class
     */
    public static class PartyInvite {
        private final UUID partyId;
        private final UUID inviter;
        private final UUID invited;
        private final long inviteTime;
        private final String message;

        public PartyInvite(UUID partyId, UUID inviter, UUID invited, String message) {
            this.partyId = partyId;
            this.inviter = inviter;
            this.invited = invited;
            this.inviteTime = System.currentTimeMillis();
            this.message = message != null ? message : "";
        }

        public UUID getPartyId() {
            return partyId;
        }

        public UUID getInviter() {
            return inviter;
        }

        public UUID getInvited() {
            return invited;
        }

        public long getInviteTime() {
            return inviteTime;
        }

        public String getMessage() {
            return message;
        }

        public boolean isExpired(int expirySeconds) {
            return System.currentTimeMillis() - inviteTime > expirySeconds * 1000L;
        }
    }

    /**
     * Party permissions enum
     */
    public enum PartyPermission {
        INVITE, KICK, PROMOTE_OFFICER, DEMOTE_OFFICER, SET_WARP, WARP,
        DISBAND, CHANGE_SETTINGS, CHAT, LEAVE
    }

    /**
     * Party event listener interface
     */
    public interface PartyEventListener {
        default void onPartyCreate(Party party) {
        }

        default void onPartyDisband(Party party) {
        }

        default void onPlayerJoinParty(Party party, UUID playerId) {
        }

        default void onPlayerLeaveParty(Party party, UUID playerId) {
        }

        default void onPlayerKicked(Party party, UUID kickerId, UUID targetId) {
        }

        default void onPartyLeaderChange(Party party, UUID oldLeader, UUID newLeader) {
        }

        default void onPartyMessage(Party party, UUID sender, String message) {
        }

        default void onPartyInvite(Party party, UUID inviter, UUID invited) {
        }

        default void onPartyInviteDeclined(PartyInvite invite) {
        }
    }

    /**
     * Add event listener
     */
    public void addEventListener(PartyEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    /**
     * Remove event listener
     */
    public void removeEventListener(PartyEventListener listener) {
        if (listener != null) {
            eventListeners.remove(listener);
        }
    }

    // ========================================
    // EXTERNAL API METHODS (ENHANCED)
    // ========================================

    /**
     * Check if party statistics are available for a player
     */
    public boolean hasPartyStatistics(Player player) {
        // This would check if the player has party statistics
        // For now, return false as statistics aren't implemented yet
        return false;
    }

    /**
     * INTEGRATED: Get party members count for a player (useful for other systems)
     */
    public int getPartyMemberCount(Player player) {
        if (player == null) return 0;
        Party party = getParty(player.getUniqueId());
        return party != null ? party.getSize() : 0;
    }

    /**
     * INTEGRATED: Get party member UUIDs for a player (useful for other systems)
     */
    public Set<UUID> getPartyMemberUUIDs(Player player) {
        if (player == null) return new HashSet<>();
        Party party = getParty(player.getUniqueId());
        return party != null ? party.getAllMembers() : new HashSet<>();
    }

    /**
     * INTEGRATED: Check if a player can see another player's detailed info (party members can see more)
     */
    public boolean canSeeDetailedInfo(Player viewer, Player target) {
        if (viewer == null || target == null) return false;
        return arePartyMembers(viewer, target);
    }

    /**
     * INTEGRATED: Get formatted party member list for display
     */
    public List<String> getFormattedPartyMemberList(Player player) {
        List<String> memberList = new ArrayList<>();

        if (!isInParty(player)) {
            return memberList;
        }

        Party party = getParty(player.getUniqueId());
        if (party == null) return memberList;

        List<Player> members = getPartyMembers(party);

        for (Player member : members) {
            if (member == null || !member.isOnline()) continue;

            StringBuilder memberInfo = new StringBuilder();

            // Add role indicator
            if (party.isLeader(member.getUniqueId())) {
                memberInfo.append(ChatColor.GOLD).append("★ ");
            } else if (party.isOfficer(member.getUniqueId())) {
                memberInfo.append(ChatColor.YELLOW).append("♦ ");
            } else {
                memberInfo.append(ChatColor.GRAY).append("• ");
            }

            // Add name with health info
            memberInfo.append(ChatColor.WHITE).append(member.getName());

            int health = (int) Math.ceil(member.getHealth());
            int maxHealth = (int) Math.ceil(member.getMaxHealth());
            memberInfo.append(ChatColor.GRAY).append(" (").append(ChatColor.RED).append(health)
                    .append(ChatColor.GRAY).append("/").append(ChatColor.RED).append(maxHealth)
                    .append(" ❤").append(ChatColor.GRAY).append(")");

            memberList.add(memberInfo.toString());
        }

        return memberList;
    }

}
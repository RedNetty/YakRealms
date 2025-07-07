package com.rednetty.server.mechanics.player.social.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * FIXED: Enhanced party system mechanics implementation with improved stability
 * - Added comprehensive null checking
 * - Improved error handling and recovery
 * - Reduced complexity in critical paths
 * - Better thread safety and synchronization
 */
public class PartyMechanics implements Listener {
    private static PartyMechanics instance;
    private final Logger logger;
    private final YakPlayerManager playerManager;

    // Core data structures with enhanced thread safety
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, PartyInvite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToPartyMap = new ConcurrentHashMap<>();

    // Enhanced configuration
    private int maxPartySize = 8;
    private int inviteExpiryTimeSeconds = 30;
    private boolean experienceSharing = true;
    private boolean lootSharing = false;
    private double experienceShareRadius = 50.0;
    private double lootShareRadius = 30.0;
    private int partyWarpCooldown = 300; // 5 minutes
    private boolean allowCrossWorldParties = true;

    // Tasks with enhanced functionality
    private BukkitTask scoreboardRefreshTask;
    private BukkitTask inviteCleanupTask;
    private BukkitTask partyMaintenanceTask;

    // Thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Event system for other plugins to hook into
    private final Set<PartyEventListener> eventListeners = ConcurrentHashMap.newKeySet();

    // Statistics tracking
    private final Map<UUID, PartyStatistics> partyStats = new ConcurrentHashMap<>();

    /**
     * Enhanced Party class with roles and advanced features
     */
    public static class Party {
        private final UUID id;
        private UUID leader;
        private final Set<UUID> officers = ConcurrentHashMap.newKeySet();
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Map<String, Object> settings = new ConcurrentHashMap<>();
        private final long creationTime;
        private volatile Location warpLocation;
        private volatile long lastWarpTime = 0;
        private volatile String motd = "";
        private volatile boolean isOpen = false; // Allow anyone to join
        private volatile int maxSize = 8;

        public Party(UUID leaderId) {
            this.id = UUID.randomUUID();
            this.leader = leaderId;
            this.members.add(leaderId);
            this.creationTime = Instant.now().getEpochSecond();

            // Default settings
            this.settings.put("experience_sharing", true);
            this.settings.put("loot_sharing", false);
            this.settings.put("friendly_fire", false);
            this.settings.put("auto_invite", false);
            this.settings.put("public_chat", true);
        }

        // Getters and setters with null safety
        public UUID getId() { return id; }
        public UUID getLeader() { return leader; }
        public void setLeader(UUID leader) {
            if (leader != null) {
                this.leader = leader;
            }
        }

        public Set<UUID> getOfficers() { return new HashSet<>(officers); }
        public Set<UUID> getMembers() { return new HashSet<>(members); }
        public long getCreationTime() { return creationTime; }
        public Location getWarpLocation() { return warpLocation; }
        public void setWarpLocation(Location location) { this.warpLocation = location; }
        public long getLastWarpTime() { return lastWarpTime; }
        public void setLastWarpTime(long time) { this.lastWarpTime = time; }
        public String getMotd() { return motd != null ? motd : ""; }
        public void setMotd(String motd) { this.motd = motd != null ? motd : ""; }
        public boolean isOpen() { return isOpen; }
        public void setOpen(boolean open) { this.isOpen = open; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = Math.max(2, Math.min(20, maxSize)); }

        public List<UUID> getAllMembers() {
            return new ArrayList<>(members);
        }

        public int getSize() {
            return members.size();
        }

        public boolean isLeader(UUID playerId) {
            return playerId != null && playerId.equals(leader);
        }

        public boolean isOfficer(UUID playerId) {
            return playerId != null && officers.contains(playerId);
        }

        public boolean isMember(UUID playerId) {
            return playerId != null && members.contains(playerId);
        }

        public boolean hasPermission(UUID playerId, PartyPermission permission) {
            if (playerId == null || permission == null) return false;

            if (isLeader(playerId)) return true;
            if (isOfficer(playerId)) {
                return permission != PartyPermission.DISBAND &&
                        permission != PartyPermission.PROMOTE_OFFICER &&
                        permission != PartyPermission.DEMOTE_OFFICER;
            }
            return permission == PartyPermission.LEAVE || permission == PartyPermission.CHAT;
        }

        public void addMember(UUID playerId) {
            if (playerId != null) {
                members.add(playerId);
            }
        }

        public void removeMember(UUID playerId) {
            if (playerId == null) return;

            members.remove(playerId);
            officers.remove(playerId);

            if (playerId.equals(leader) && !members.isEmpty()) {
                // Promote first officer or first member to leader
                UUID newLeader = officers.isEmpty() ? members.iterator().next() : officers.iterator().next();
                setLeader(newLeader);
                officers.remove(newLeader);
            }
        }

        public void promoteToOfficer(UUID playerId) {
            if (playerId != null && members.contains(playerId) && !playerId.equals(leader)) {
                officers.add(playerId);
            }
        }

        public void demoteFromOfficer(UUID playerId) {
            if (playerId != null) {
                officers.remove(playerId);
            }
        }

        public Object getSetting(String key) {
            return key != null ? settings.get(key) : null;
        }

        public void setSetting(String key, Object value) {
            if (key != null) {
                settings.put(key, value);
            }
        }

        public boolean getBooleanSetting(String key, boolean defaultValue) {
            Object value = getSetting(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
    }

    /**
     * Party invite class with enhanced information
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

        public UUID getPartyId() { return partyId; }
        public UUID getInviter() { return inviter; }
        public UUID getInvited() { return invited; }
        public long getInviteTime() { return inviteTime; }
        public String getMessage() { return message; }

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
        default void onPartyCreate(Party party) {}
        default void onPartyDisband(Party party) {}
        default void onPlayerJoinParty(Party party, UUID playerId) {}
        default void onPlayerLeaveParty(Party party, UUID playerId) {}
        default void onPartyLeaderChange(Party party, UUID oldLeader, UUID newLeader) {}
        default void onPartyMessage(Party party, UUID sender, String message) {}
    }

    /**
     * Party statistics tracking
     */
    public static class PartyStatistics {
        private volatile int totalPartiesCreated = 0;
        private volatile int totalPartiesJoined = 0;
        private volatile long totalTimeInParty = 0;
        private volatile int messagesLent = 0;
        private volatile int experienceShared = 0;
        private volatile long lastPartyTime = 0;

        // Getters and setters
        public int getTotalPartiesCreated() { return totalPartiesCreated; }
        public void incrementPartiesCreated() { this.totalPartiesCreated++; }
        public int getTotalPartiesJoined() { return totalPartiesJoined; }
        public void incrementPartiesJoined() { this.totalPartiesJoined++; }
        public long getTotalTimeInParty() { return totalTimeInParty; }
        public void addTimeInParty(long time) { this.totalTimeInParty += time; }
        public int getMessagesLent() { return messagesLent; }
        public void incrementMessages() { this.messagesLent++; }
        public int getExperienceShared() { return experienceShared; }
        public void addExperienceShared(int exp) { this.experienceShared += exp; }
        public long getLastPartyTime() { return lastPartyTime; }
        public void setLastPartyTime(long time) { this.lastPartyTime = time; }
    }

    /**
     * Private constructor for singleton pattern
     */
    private PartyMechanics() {
        this.logger = YakRealms.getInstance().getLogger();
        this.playerManager = YakPlayerManager.getInstance();
        loadConfiguration();
    }

    /**
     * Gets the singleton instance
     */
    public static PartyMechanics getInstance() {
        if (instance == null) {
            instance = new PartyMechanics();
        }
        return instance;
    }

    /**
     * Load configuration from plugin config
     */
    private void loadConfiguration() {
        try {
            var config = YakRealms.getInstance().getConfig();
            this.maxPartySize = config.getInt("party.max-size", 8);
            this.inviteExpiryTimeSeconds = config.getInt("party.invite-expiry", 30);
            this.experienceSharing = config.getBoolean("party.experience-sharing", true);
            this.lootSharing = config.getBoolean("party.loot-sharing", false);
            this.experienceShareRadius = config.getDouble("party.experience-share-radius", 50.0);
            this.lootShareRadius = config.getDouble("party.loot-share-radius", 30.0);
            this.partyWarpCooldown = config.getInt("party.warp-cooldown", 300);
            this.allowCrossWorldParties = config.getBoolean("party.allow-cross-world", true);
        } catch (Exception e) {
            logger.warning("Error loading party configuration: " + e.getMessage());
        }
    }

    /**
     * Initialize the enhanced party system
     */
    public void onEnable() {
        try {
            // Register events
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Start enhanced tasks
            startScoreboardRefreshTask();
            startInviteCleanupTask();
            startPartyMaintenanceTask();

            logger.info("Enhanced party mechanics have been enabled.");
        } catch (Exception e) {
            logger.severe("Failed to enable party mechanics: " + e.getMessage());
        }
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        try {
            // Cancel tasks
            cancelAllTasks();

            // Save party statistics
            savePartyStatistics();

            // Clean up scoreboards
            PartyScoreboards.cleanupAll();

            // Clear data
            parties.clear();
            invites.clear();
            playerToPartyMap.clear();
            partyStats.clear();
            eventListeners.clear();

            logger.info("Enhanced party mechanics have been disabled.");
        } catch (Exception e) {
            logger.warning("Error disabling party mechanics: " + e.getMessage());
        }
    }

    /**
     * FIXED: Start the enhanced scoreboard refresh task with better error handling
     */
    private void startScoreboardRefreshTask() {
        scoreboardRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Update all party scoreboards - safer approach
                    safeRefreshAllPartyScoreboards();

                    // Update health displays for all players
                    safeUpdateAllPlayerHealth();
                } catch (Exception e) {
                    logger.warning("Error refreshing party scoreboards: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 40L, 40L); // Every 2 seconds (reduced frequency)
    }

    /**
     * FIXED: Safe method to refresh all party scoreboards
     */
    private void safeRefreshAllPartyScoreboards() {
        try {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            for (Player player : players) {
                if (player == null || !player.isOnline()) continue;

                try {
                    if (isInParty(player)) {
                        PartyScoreboards.updatePlayerScoreboard(player);
                    }
                } catch (Exception e) {
                    // Log but continue with other players
                    logger.fine("Error updating scoreboard for " + player.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Error in safe refresh all party scoreboards: " + e.getMessage());
        }
    }

    /**
     * FIXED: Safe method to update all player health
     */
    private void safeUpdateAllPlayerHealth() {
        try {
            PartyScoreboards.updateAllPlayerHealth();
        } catch (Exception e) {
            logger.warning("Error updating all player health: " + e.getMessage());
        }
    }

    /**
     * Start the enhanced invite cleanup task
     */
    private void startInviteCleanupTask() {
        inviteCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    List<UUID> expiredInvites = new ArrayList<>();

                    for (Map.Entry<UUID, PartyInvite> entry : invites.entrySet()) {
                        PartyInvite invite = entry.getValue();
                        if (invite != null && invite.isExpired(inviteExpiryTimeSeconds)) {
                            expiredInvites.add(entry.getKey());
                        }
                    }

                    for (UUID invitedPlayer : expiredInvites) {
                        PartyInvite invite = invites.remove(invitedPlayer);
                        if (invite != null) {
                            notifyInviteExpiry(invite);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Error in party invite cleanup: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 100L); // Every 5 seconds
    }

    /**
     * Start party maintenance task for statistics and cleanup
     */
    private void startPartyMaintenanceTask() {
        partyMaintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Update party statistics
                    updatePartyStatistics();

                    // Clean up empty parties
                    cleanupEmptyParties();
                } catch (Exception e) {
                    logger.warning("Error in party maintenance: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 60, 20L * 60); // Every minute
    }

    /**
     * Cancel all tasks
     */
    private void cancelAllTasks() {
        if (scoreboardRefreshTask != null) {
            scoreboardRefreshTask.cancel();
        }
        if (inviteCleanupTask != null) {
            inviteCleanupTask.cancel();
        }
        if (partyMaintenanceTask != null) {
            partyMaintenanceTask.cancel();
        }
    }

    // Event listener management
    public void addEventListener(PartyEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(PartyEventListener listener) {
        if (listener != null) {
            eventListeners.remove(listener);
        }
    }

    private void fireEvent(Runnable eventCall) {
        if (eventCall == null) return;

        for (PartyEventListener listener : eventListeners) {
            try {
                eventCall.run();
            } catch (Exception e) {
                logger.warning("Error in party event listener: " + e.getMessage());
            }
        }
    }

    // Enhanced party management methods

    /**
     * Check if a player is a party leader
     */
    public boolean isPartyLeader(Player player) {
        return player != null && isPartyLeader(player.getUniqueId());
    }

    public boolean isPartyLeader(UUID playerId) {
        if (playerId == null) return false;

        lock.readLock().lock();
        try {
            UUID partyId = playerToPartyMap.get(playerId);
            if (partyId == null) return false;
            Party party = parties.get(partyId);
            return party != null && party.isLeader(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a player is a party officer
     */
    public boolean isPartyOfficer(Player player) {
        return player != null && isPartyOfficer(player.getUniqueId());
    }

    public boolean isPartyOfficer(UUID playerId) {
        if (playerId == null) return false;

        lock.readLock().lock();
        try {
            UUID partyId = playerToPartyMap.get(playerId);
            if (partyId == null) return false;
            Party party = parties.get(partyId);
            return party != null && party.isOfficer(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the party leader of a player
     */
    public Player getPartyLeader(Player player) {
        UUID leaderId = getPartyLeaderId(player != null ? player.getUniqueId() : null);
        return leaderId != null ? Bukkit.getPlayer(leaderId) : null;
    }

    public UUID getPartyLeaderId(UUID playerId) {
        if (playerId == null) return null;

        lock.readLock().lock();
        try {
            UUID partyId = playerToPartyMap.get(playerId);
            if (partyId == null) return null;
            Party party = parties.get(partyId);
            return party != null ? party.getLeader() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a player is in a party
     */
    public boolean isInParty(Player player) {
        return player != null && isInParty(player.getUniqueId());
    }

    public boolean isInParty(UUID playerId) {
        if (playerId == null) return false;

        lock.readLock().lock();
        try {
            return playerToPartyMap.containsKey(playerId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get the party of a player
     */
    public Party getParty(Player player) {
        return player != null ? getParty(player.getUniqueId()) : null;
    }

    public Party getParty(UUID playerId) {
        if (playerId == null) return null;

        lock.readLock().lock();
        try {
            UUID partyId = playerToPartyMap.get(playerId);
            return partyId != null ? parties.get(partyId) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all members of a player's party
     */
    public List<Player> getPartyMembers(Player player) {
        if (player == null) return null;

        List<UUID> memberIds = getPartyMemberIds(player.getUniqueId());
        if (memberIds == null) return null;

        return memberIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<UUID> getPartyMemberIds(UUID playerId) {
        if (playerId == null) return null;

        lock.readLock().lock();
        try {
            Party party = getParty(playerId);
            return party != null ? party.getAllMembers() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * ENHANCED: Check if two players are in the same party with comprehensive validation
     */
    public boolean arePartyMembers(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            return false;
        }

        // Quick check - same player
        if (player1.equals(player2)) {
            return false; // Players are not party members of themselves for PVP purposes
        }

        return arePartyMembers(player1.getUniqueId(), player2.getUniqueId());
    }

    /**
     * ENHANCED: Check if two player UUIDs are in the same party with comprehensive validation
     */
    public boolean arePartyMembers(UUID player1Id, UUID player2Id) {
        if (player1Id == null || player2Id == null) {
            return false;
        }

        // Quick check - same player
        if (player1Id.equals(player2Id)) {
            return false; // Players are not party members of themselves for PVP purposes
        }

        lock.readLock().lock();
        try {
            // Get party IDs for both players
            UUID party1Id = playerToPartyMap.get(player1Id);
            UUID party2Id = playerToPartyMap.get(player2Id);

            // Both must be in parties
            if (party1Id == null || party2Id == null) {
                return false;
            }

            // Must be in the same party
            if (!party1Id.equals(party2Id)) {
                return false;
            }

            // Verify the party actually exists and contains both players
            Party party = parties.get(party1Id);
            if (party == null) {
                // Clean up invalid party mappings
                playerToPartyMap.remove(player1Id);
                playerToPartyMap.remove(player2Id);
                return false;
            }

            // Double-check that both players are actually in the party
            boolean player1InParty = party.isMember(player1Id);
            boolean player2InParty = party.isMember(player2Id);

            if (!player1InParty || !player2InParty) {
                // Clean up invalid mappings
                if (!player1InParty) {
                    playerToPartyMap.remove(player1Id);
                }
                if (!player2InParty) {
                    playerToPartyMap.remove(player2Id);
                }
                return false;
            }

            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a player can engage in friendly fire based on party settings
     */
    public boolean canPartyMemberAttack(Player attacker, Player victim) {
        if (!arePartyMembers(attacker, victim)) {
            return true; // Not party members, party rules don't apply
        }

        Party party = getParty(attacker);
        if (party == null) {
            return true; // No party found, allow attack
        }

        // Check party-wide friendly fire setting
        Boolean partyFriendlyFire = (Boolean) party.getSetting("friendly_fire");
        if (partyFriendlyFire != null && partyFriendlyFire) {
            return true; // Party allows friendly fire
        }

        // Check individual player settings via the toggle system
        YakPlayerManager playerManager = YakPlayerManager.getInstance();
        YakPlayer attackerData = playerManager.getPlayer(attacker);

        if (attackerData != null && attackerData.isToggled("Friendly Fire")) {
            return true; // Attacker has friendly fire enabled
        }

        return false; // Friendly fire not allowed
    }

    /**
     * Get party relationship info between two players (useful for debugging and logging)
     */
    public PartyRelationship getPartyRelationship(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            return new PartyRelationship(false, PartyRelationship.RelationType.NOT_RELATED, null);
        }

        if (!arePartyMembers(player1, player2)) {
            return new PartyRelationship(false, PartyRelationship.RelationType.NOT_RELATED, null);
        }

        Party party = getParty(player1);
        if (party == null) {
            return new PartyRelationship(false, PartyRelationship.RelationType.NOT_RELATED, null);
        }

        UUID player1Id = player1.getUniqueId();
        UUID player2Id = player2.getUniqueId();

        // Determine relationship types
        PartyRelationship.RelationType relationType;
        if (party.isLeader(player1Id) && party.isLeader(player2Id)) {
            relationType = PartyRelationship.RelationType.BOTH_LEADERS; // Shouldn't happen, but just in case
        } else if (party.isLeader(player1Id)) {
            relationType = PartyRelationship.RelationType.LEADER_TO_MEMBER;
        } else if (party.isLeader(player2Id)) {
            relationType = PartyRelationship.RelationType.MEMBER_TO_LEADER;
        } else if (party.isOfficer(player1Id) && party.isOfficer(player2Id)) {
            relationType = PartyRelationship.RelationType.OFFICER_TO_OFFICER;
        } else if (party.isOfficer(player1Id)) {
            relationType = PartyRelationship.RelationType.OFFICER_TO_MEMBER;
        } else if (party.isOfficer(player2Id)) {
            relationType = PartyRelationship.RelationType.MEMBER_TO_OFFICER;
        } else {
            relationType = PartyRelationship.RelationType.MEMBER_TO_MEMBER;
        }

        return new PartyRelationship(true, relationType, party);
    }
    /**
     * Class to represent the relationship between two players in a party context
     */
    public static class PartyRelationship {
        public enum RelationType {
            NOT_RELATED,
            LEADER_TO_MEMBER,
            MEMBER_TO_LEADER,
            OFFICER_TO_OFFICER,
            OFFICER_TO_MEMBER,
            MEMBER_TO_OFFICER,
            MEMBER_TO_MEMBER,
            BOTH_LEADERS
        }

        private final boolean inSameParty;
        private final RelationType relationType;
        private final Party party;

        public PartyRelationship(boolean inSameParty, RelationType relationType, Party party) {
            this.inSameParty = inSameParty;
            this.relationType = relationType;
            this.party = party;
        }

        public boolean isInSameParty() { return inSameParty; }
        public RelationType getRelationType() { return relationType; }
        public Party getParty() { return party; }

        public String getDescription() {
            if (!inSameParty) {
                return "Not in same party";
            }

            switch (relationType) {
                case LEADER_TO_MEMBER: return "Leader attacking member";
                case MEMBER_TO_LEADER: return "Member attacking leader";
                case OFFICER_TO_OFFICER: return "Officer attacking officer";
                case OFFICER_TO_MEMBER: return "Officer attacking member";
                case MEMBER_TO_OFFICER: return "Member attacking officer";
                case MEMBER_TO_MEMBER: return "Member attacking member";
                case BOTH_LEADERS: return "Leader attacking leader";
                default: return "Unknown relationship";
            }
        }
    }

    /**
     * Notify party members about friendly fire incidents
     */
    public void notifyPartyFriendlyFire(Player attacker, Player victim) {
        if (!arePartyMembers(attacker, victim)) {
            return;
        }

        Party party = getParty(attacker);
        if (party == null) {
            return;
        }

        PartyRelationship relationship = getPartyRelationship(attacker, victim);
        String message = ChatColor.YELLOW + "⚠ Friendly fire: " +
                ChatColor.WHITE + attacker.getName() +
                ChatColor.YELLOW + " attacked " +
                ChatColor.WHITE + victim.getName() +
                ChatColor.GRAY + " (" + relationship.getDescription().toLowerCase() + ")";

        // Notify all party members except the attacker and victim (they get their own messages)
        for (UUID memberId : party.getAllMembers()) {
            if (memberId.equals(attacker.getUniqueId()) || memberId.equals(victim.getUniqueId())) {
                continue;
            }

            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }
    /**
     * Create a new party with enhanced features
     */
    public boolean createParty(Player player) {
        return player != null && createParty(player.getUniqueId());
    }

    public boolean createParty(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            // Check if already in a party
            if (isInParty(playerId)) {
                return false;
            }

            // Create the party
            Party party = new Party(playerId);
            parties.put(party.getId(), party);
            playerToPartyMap.put(playerId, party.getId());

            // Update statistics
            PartyStatistics stats = partyStats.computeIfAbsent(playerId, k -> new PartyStatistics());
            stats.incrementPartiesCreated();
            stats.setLastPartyTime(System.currentTimeMillis());

            // Fire event
            fireEvent(() -> {
                for (PartyEventListener listener : eventListeners) {
                    listener.onPartyCreate(party);
                }
            });

            // Update scoreboard and team assignments
            Player bukkitPlayer = Bukkit.getPlayer(playerId);
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                    try {
                        PartyScoreboards.updatePlayerScoreboard(bukkitPlayer);
                        PartyScoreboards.updatePlayerTeamAssignments(bukkitPlayer);
                        showPartyCreationEffects(bukkitPlayer);
                    } catch (Exception e) {
                        logger.warning("Error updating visuals after party creation: " + e.getMessage());
                    }
                });
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * FIXED: Add a player to a party with enhanced validation and safer visual updates
     */
    public boolean addPlayerToParty(Player player, UUID partyId) {
        return player != null && addPlayerToParty(player.getUniqueId(), partyId);
    }

    public boolean addPlayerToParty(UUID playerId, UUID partyId) {
        if (playerId == null || partyId == null) return false;

        lock.writeLock().lock();
        try {
            Party party = parties.get(partyId);
            if (party == null) return false;

            // Check if player is already in this party
            if (party.isMember(playerId)) return false;

            // Check if player is in another party
            if (isInParty(playerId)) return false;

            // Check party size limit
            if (party.getSize() >= party.getMaxSize()) return false;

            // Add player to party
            party.addMember(playerId);
            playerToPartyMap.put(playerId, partyId);

            // Update statistics
            PartyStatistics stats = partyStats.computeIfAbsent(playerId, k -> new PartyStatistics());
            stats.incrementPartiesJoined();
            stats.setLastPartyTime(System.currentTimeMillis());

            // Fire event
            fireEvent(() -> {
                for (PartyEventListener listener : eventListeners) {
                    listener.onPlayerJoinParty(party, playerId);
                }
            });

            
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    updateAllPartyMemberVisuals(party);

                    // Show join effects
                    Player bukkitPlayer = Bukkit.getPlayer(playerId);
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        showPartyJoinEffects(bukkitPlayer);

                        // Send MOTD if available
                        String motd = party.getMotd();
                        if (motd != null && !motd.isEmpty()) {
                            bukkitPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "Party MOTD: " + ChatColor.WHITE + motd);
                        }
                    }

                    // Notify party members
                    notifyPartyMembers(party, ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                            ChatColor.GRAY + " " + getPlayerName(playerId) + ChatColor.GRAY + " has " +
                            ChatColor.GREEN + ChatColor.UNDERLINE + "joined" + ChatColor.GRAY + " the party.");
                } catch (Exception e) {
                    logger.warning("Error updating visuals after player joined party: " + e.getMessage());
                }
            });

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * FIXED: Remove a player from their party with enhanced handling and safer updates
     */
    public boolean removePlayerFromParty(Player player) {
        return player != null && removePlayerFromParty(player.getUniqueId());
    }

    public boolean removePlayerFromParty(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            UUID partyId = playerToPartyMap.get(playerId);
            if (partyId == null) return false;

            Party party = parties.get(partyId);
            if (party == null) return false;

            boolean wasLeader = party.isLeader(playerId);
            UUID oldLeader = party.getLeader();

            // Remove player from party
            party.removeMember(playerId);
            playerToPartyMap.remove(playerId);

            // Update statistics
            PartyStatistics stats = partyStats.get(playerId);
            if (stats != null) {
                long timeInParty = System.currentTimeMillis() - stats.getLastPartyTime();
                stats.addTimeInParty(timeInParty);
            }

            // Fire events
            fireEvent(() -> {
                for (PartyEventListener listener : eventListeners) {
                    listener.onPlayerLeaveParty(party, playerId);
                }
            });

            if (wasLeader && party.getLeader() != null && !party.getLeader().equals(oldLeader)) {
                // Leader changed
                fireEvent(() -> {
                    for (PartyEventListener listener : eventListeners) {
                        listener.onPartyLeaderChange(party, oldLeader, party.getLeader());
                    }
                });

                // Notify new leader
                Player newLeader = Bukkit.getPlayer(party.getLeader());
                if (newLeader != null && newLeader.isOnline()) {
                    newLeader.sendMessage(ChatColor.GOLD + "★ You have been promoted to party leader!");
                    showLeaderPromotionEffects(newLeader);
                }
            }

            // Handle party state after removal
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    if (party.getSize() == 0) {
                        // Disband empty party
                        disbandParty(partyId);
                    } else {
                        // Update visuals for remaining members
                        updateAllPartyMemberVisuals(party);

                        // Notify remaining members
                        notifyPartyMembers(party, ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                                ChatColor.GRAY + " " + getPlayerName(playerId) + ChatColor.GRAY + " has " +
                                ChatColor.RED + ChatColor.UNDERLINE + "left" + ChatColor.GRAY + " the party.");
                    }

                    // Update leaving player's visuals
                    Player leavingPlayer = Bukkit.getPlayer(playerId);
                    if (leavingPlayer != null && leavingPlayer.isOnline()) {
                        PartyScoreboards.clearPlayerScoreboard(leavingPlayer);
                        PartyScoreboards.updatePlayerTeamAssignments(leavingPlayer);
                        showPartyLeaveEffects(leavingPlayer);
                    }
                } catch (Exception e) {
                    logger.warning("Error updating visuals after player left party: " + e.getMessage());
                }
            });

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Disband a party
     */
    public boolean disbandParty(UUID partyId) {
        if (partyId == null) return false;

        lock.writeLock().lock();
        try {
            Party party = parties.remove(partyId);
            if (party == null) return false;

            // Remove all members from party map
            for (UUID memberId : party.getAllMembers()) {
                playerToPartyMap.remove(memberId);

                // Update member statistics
                PartyStatistics stats = partyStats.get(memberId);
                if (stats != null) {
                    long timeInParty = System.currentTimeMillis() - stats.getLastPartyTime();
                    stats.addTimeInParty(timeInParty);
                }
            }

            // Fire event
            fireEvent(() -> {
                for (PartyEventListener listener : eventListeners) {
                    listener.onPartyDisband(party);
                }
            });

            // Notify all members and update visuals
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    for (UUID memberId : party.getAllMembers()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            member.sendMessage(ChatColor.RED + "The party has been disbanded.");
                            PartyScoreboards.clearPlayerScoreboard(member);
                            PartyScoreboards.updatePlayerTeamAssignments(member);
                            showPartyDisbandEffects(member);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Error updating visuals after party disband: " + e.getMessage());
                }
            });

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enhanced party invitation system
     */
    public boolean invitePlayerToParty(Player invited, Player inviter, String message) {
        if (invited == null || inviter == null) return false;
        return invitePlayerToParty(invited.getUniqueId(), inviter.getUniqueId(), message);
    }

    public boolean invitePlayerToParty(UUID invitedId, UUID inviterId, String message) {
        if (invitedId == null || inviterId == null) return false;

        lock.writeLock().lock();
        try {
            if (invitedId.equals(inviterId)) {
                sendMessage(inviterId, ChatColor.RED + "You cannot invite yourself to your own party.");
                return false;
            }

            // Check if inviter is in a party and has permission
            Party party = getParty(inviterId);
            if (party == null) {
                // Create party for inviter
                if (!createParty(inviterId)) {
                    sendMessage(inviterId, ChatColor.RED + "Failed to create party.");
                    return false;
                }
                party = getParty(inviterId);
            }

            if (party == null || !party.hasPermission(inviterId, PartyPermission.INVITE)) {
                sendMessage(inviterId, ChatColor.RED + "You don't have permission to invite players.");
                return false;
            }

            // Check if party is full
            if (party.getSize() >= party.getMaxSize()) {
                sendMessage(inviterId, ChatColor.RED + "Your party is full (" + party.getMaxSize() + " players max).");
                return false;
            }

            // Check if invited player is already in a party
            if (isInParty(invitedId)) {
                if (arePartyMembers(inviterId, invitedId)) {
                    sendMessage(inviterId, ChatColor.RED + getPlayerName(invitedId) + " is already in your party.");
                } else {
                    sendMessage(inviterId, ChatColor.RED + getPlayerName(invitedId) + " is already in another party.");
                }
                return false;
            }

            // Check if player already has a pending invite
            if (invites.containsKey(invitedId)) {
                sendMessage(inviterId, ChatColor.RED + getPlayerName(invitedId) + " already has a pending party invite.");
                return false;
            }

            // Check cross-world restrictions
            if (!allowCrossWorldParties) {
                Player inviterPlayer = Bukkit.getPlayer(inviterId);
                Player invitedPlayer = Bukkit.getPlayer(invitedId);
                if (inviterPlayer != null && invitedPlayer != null &&
                        !inviterPlayer.getWorld().equals(invitedPlayer.getWorld())) {
                    sendMessage(inviterId, ChatColor.RED + "Cannot invite players from different worlds.");
                    return false;
                }
            }

            // Create and send invite
            PartyInvite invite = new PartyInvite(party.getId(), inviterId, invitedId, message);
            invites.put(invitedId, invite);

            // Send invite messages
            String inviterName = getPlayerName(inviterId);
            String invitedName = getPlayerName(invitedId);

            sendMessage(invitedId, ChatColor.LIGHT_PURPLE + "✦ Party Invitation ✦");
            sendMessage(invitedId, ChatColor.GRAY + inviterName + ChatColor.YELLOW + " has invited you to join their party!");
            if (message != null && !message.isEmpty()) {
                sendMessage(invitedId, ChatColor.GRAY + "Message: " + ChatColor.WHITE + message);
            }
            sendMessage(invitedId, ChatColor.GRAY + "Party size: " + ChatColor.WHITE + party.getSize() + "/" + party.getMaxSize());
            sendMessage(invitedId, ChatColor.GREEN + "Type " + ChatColor.BOLD + "/paccept" + ChatColor.GREEN + " to accept");
            sendMessage(invitedId, ChatColor.RED + "Type " + ChatColor.BOLD + "/pdecline" + ChatColor.RED + " to decline");
            sendMessage(invitedId, ChatColor.GRAY + "This invite expires in " + inviteExpiryTimeSeconds + " seconds.");

            sendMessage(inviterId, ChatColor.GREEN + "Sent party invitation to " + ChatColor.BOLD + invitedName + ChatColor.GREEN + ".");

            // Play sounds
            playSound(invitedId, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            playSound(inviterId, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Accept a party invitation with enhanced feedback
     */
    public boolean acceptPartyInvite(Player player) {
        return player != null && acceptPartyInvite(player.getUniqueId());
    }

    public boolean acceptPartyInvite(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            PartyInvite invite = invites.remove(playerId);
            if (invite == null) {
                sendMessage(playerId, ChatColor.RED + "You don't have any pending party invites.");
                return false;
            }

            if (invite.isExpired(inviteExpiryTimeSeconds)) {
                sendMessage(playerId, ChatColor.RED + "That party invite has expired.");
                return false;
            }

            Party party = parties.get(invite.getPartyId());
            if (party == null) {
                sendMessage(playerId, ChatColor.RED + "That party no longer exists.");
                return false;
            }

            if (party.getSize() >= party.getMaxSize()) {
                sendMessage(playerId, ChatColor.RED + "That party is now full.");
                return false;
            }

            // Add player to party
            if (addPlayerToParty(playerId, invite.getPartyId())) {
                // Success messages handled in addPlayerToParty
                return true;
            } else {
                sendMessage(playerId, ChatColor.RED + "Failed to join the party.");
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Decline a party invitation with enhanced feedback
     */
    public boolean declinePartyInvite(Player player) {
        return player != null && declinePartyInvite(player.getUniqueId());
    }

    public boolean declinePartyInvite(UUID playerId) {
        if (playerId == null) return false;

        lock.writeLock().lock();
        try {
            PartyInvite invite = invites.remove(playerId);
            if (invite == null) {
                sendMessage(playerId, ChatColor.RED + "You don't have any pending party invites.");
                return false;
            }

            String inviterName = getPlayerName(invite.getInviter());
            String invitedName = getPlayerName(playerId);

            // Notify both players
            sendMessage(playerId, ChatColor.RED + "Declined party invitation from " + ChatColor.BOLD + inviterName + ChatColor.RED + ".");
            sendMessage(invite.getInviter(), ChatColor.RED + invitedName + " has " + ChatColor.UNDERLINE + "declined" +
                    ChatColor.RED + " your party invitation.");

            // Play sounds
            playSound(playerId, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            playSound(invite.getInviter(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enhanced party messaging with formatting
     */
    public boolean sendPartyMessage(Player sender, String message) {
        return sender != null && sendPartyMessage(sender.getUniqueId(), message);
    }

    public boolean sendPartyMessage(UUID senderId, String message) {
        if (senderId == null || message == null || message.trim().isEmpty()) return false;

        lock.readLock().lock();
        try {
            Party party = getParty(senderId);
            if (party == null) {
                sendMessage(senderId, ChatColor.RED + "You are not in a party.");
                return false;
            }

            if (!party.hasPermission(senderId, PartyPermission.CHAT)) {
                sendMessage(senderId, ChatColor.RED + "You don't have permission to chat in this party.");
                return false;
            }

            String senderName = getPlayerName(senderId);
            String formattedMessage = formatPartyMessage(party, senderId, senderName, message);

            // Send to all party members
            for (UUID memberId : party.getAllMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(formattedMessage);
                }
            }

            // Update statistics
            PartyStatistics stats = partyStats.computeIfAbsent(senderId, k -> new PartyStatistics());
            stats.incrementMessages();

            // Fire event
            fireEvent(() -> {
                for (PartyEventListener listener : eventListeners) {
                    listener.onPartyMessage(party, senderId, message);
                }
            });

            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * FIXED: Update all visual elements for party members with better error handling
     */
    private void updateAllPartyMemberVisuals(Party party) {
        if (party == null) return;

        for (UUID memberId : party.getAllMembers()) {
            try {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    PartyScoreboards.updatePlayerScoreboard(member);
                    PartyScoreboards.updatePlayerTeamAssignments(member);
                }
            } catch (Exception e) {
                logger.fine("Error updating visuals for party member " + memberId + ": " + e.getMessage());
            }
        }
    }

    // Enhanced utility methods

    /**
     * Update party statistics
     */
    private void updatePartyStatistics() {
        try {
            for (Map.Entry<UUID, UUID> entry : playerToPartyMap.entrySet()) {
                UUID playerId = entry.getKey();
                PartyStatistics stats = partyStats.computeIfAbsent(playerId, k -> new PartyStatistics());

                // Update time in party for active members
                if (stats.getLastPartyTime() > 0) {
                    long currentTime = System.currentTimeMillis();
                    long sessionTime = currentTime - stats.getLastPartyTime();
                    stats.addTimeInParty(sessionTime);
                    stats.setLastPartyTime(currentTime);
                }
            }
        } catch (Exception e) {
            logger.warning("Error updating party statistics: " + e.getMessage());
        }
    }

    /**
     * Clean up empty parties
     */
    private void cleanupEmptyParties() {
        try {
            List<UUID> emptyParties = new ArrayList<>();

            for (Map.Entry<UUID, Party> entry : parties.entrySet()) {
                Party party = entry.getValue();
                if (party != null && party.getSize() == 0) {
                    emptyParties.add(entry.getKey());
                }
            }

            for (UUID partyId : emptyParties) {
                disbandParty(partyId);
            }
        } catch (Exception e) {
            logger.warning("Error cleaning up empty parties: " + e.getMessage());
        }
    }

    /**
     * Notify party invite expiry
     */
    private void notifyInviteExpiry(PartyInvite invite) {
        if (invite == null) return;

        try {
            String inviterName = getPlayerName(invite.getInviter());
            sendMessage(invite.getInvited(), ChatColor.RED + "Party invite from " + inviterName + " has expired.");
            sendMessage(invite.getInviter(), ChatColor.RED + "Party invite to " + getPlayerName(invite.getInvited()) + " has expired.");
        } catch (Exception e) {
            logger.warning("Error notifying invite expiry: " + e.getMessage());
        }
    }

    /**
     * Notify all party members with a message
     */
    private void notifyPartyMembers(Party party, String message) {
        if (party == null || message == null) return;

        for (UUID memberId : party.getAllMembers()) {
            try {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(message);
                }
            } catch (Exception e) {
                // Continue to other members if one fails
            }
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

        formatted.append(ChatColor.LIGHT_PURPLE).append("<").append(ChatColor.BOLD).append("P").append(ChatColor.LIGHT_PURPLE).append(">");

        // Add role indicators
        if (party.isLeader(senderId)) {
            formatted.append(ChatColor.GOLD).append("★");
        } else if (party.isOfficer(senderId)) {
            formatted.append(ChatColor.YELLOW).append("♦");
        }

        formatted.append(" ").append(senderName).append(": ").append(ChatColor.WHITE).append(message);

        return formatted.toString();
    }

    /**
     * Visual effects methods
     */
    private void showPartyCreationEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 10, 1, 1, 1, 0.1);
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    private void showPartyJoinEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            PartyScoreboards.showPartyJoinEffects(player);
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    private void showPartyLeaveEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    private void showPartyDisbandEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    private void showLeaderPromotionEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            PartyScoreboards.showLeaderPromotionEffects(player);
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    /**
     * Utility methods
     */
    private String getPlayerName(UUID playerId) {
        if (playerId == null) return "Unknown";

        try {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                return player.getName();
            }

            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(playerId);
                if (yakPlayer != null) {
                    return yakPlayer.getUsername();
                }
            }
        } catch (Exception e) {
            // Fall back to Unknown
        }

        return "Unknown";
    }

    private void sendMessage(UUID playerId, String message) {
        if (playerId == null || message == null) return;

        try {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        } catch (Exception e) {
            // Ignore message send errors
        }
    }

    private void playSound(UUID playerId, Sound sound, float volume, float pitch) {
        if (playerId == null || sound == null) return;

        try {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception e) {
            // Ignore sound play errors
        }
    }

    /**
     * Save party statistics
     */
    private void savePartyStatistics() {
        try {
            // This would save to database in a real implementation
            logger.info("Saving party statistics for " + partyStats.size() + " players");
        } catch (Exception e) {
            logger.warning("Error saving party statistics: " + e.getMessage());
        }
    }

    /**
     * FIXED: Handle player join event to set up their scoreboard safely
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Delay initial setup to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            try {
                if (player.isOnline()) {
                    if (isInParty(player)) {
                        PartyScoreboards.updatePlayerScoreboard(player);
                    }
                    PartyScoreboards.updatePlayerTeamAssignments(player);
                }
            } catch (Exception e) {
                logger.warning("Error setting up scoreboard for joining player " + player.getName() + ": " + e.getMessage());
            }
        }, 40L); // 2 second delay
    }

    /**
     * FIXED: Handle player quit event to remove them from their party safely
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        UUID playerId = player.getUniqueId();

        try {
            // Clean up scoreboard resources
            PartyScoreboards.cleanupPlayer(player);

            if (isInParty(playerId)) {
                // Update statistics before leaving
                PartyStatistics stats = partyStats.get(playerId);
                if (stats != null) {
                    long timeInParty = System.currentTimeMillis() - stats.getLastPartyTime();
                    stats.addTimeInParty(timeInParty);
                }

                removePlayerFromParty(playerId);
            }
        } catch (Exception e) {
            logger.warning("Error handling player quit for " + player.getName() + ": " + e.getMessage());
        }
    }

    // Public API methods for external use

    public int getMaxPartySize() { return maxPartySize; }
    public void setMaxPartySize(int maxPartySize) { this.maxPartySize = maxPartySize; }
    public int getInviteExpiryTimeSeconds() { return inviteExpiryTimeSeconds; }
    public void setInviteExpiryTimeSeconds(int inviteExpiryTimeSeconds) { this.inviteExpiryTimeSeconds = inviteExpiryTimeSeconds; }
    public boolean isExperienceSharing() { return experienceSharing; }
    public void setExperienceSharing(boolean experienceSharing) { this.experienceSharing = experienceSharing; }
    public boolean isLootSharing() { return lootSharing; }
    public void setLootSharing(boolean lootSharing) { this.lootSharing = lootSharing; }
    public double getExperienceShareRadius() { return experienceShareRadius; }
    public void setExperienceShareRadius(double experienceShareRadius) { this.experienceShareRadius = experienceShareRadius; }
    public double getLootShareRadius() { return lootShareRadius; }
    public void setLootShareRadius(double lootShareRadius) { this.lootShareRadius = lootShareRadius; }

    /**
     * Get party statistics for a player
     */
    public PartyStatistics getPartyStatistics(UUID playerId) {
        return playerId != null ? partyStats.computeIfAbsent(playerId, k -> new PartyStatistics()) : new PartyStatistics();
    }

    /**
     * Get all active parties
     */
    public Collection<Party> getAllParties() {
        return new ArrayList<>(parties.values());
    }

    /**
     * Get party count
     */
    public int getPartyCount() {
        return parties.size();
    }

    /**
     * Clear all parties (admin command)
     */
    public void clearAllParties() {
        lock.writeLock().lock();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    PartyScoreboards.clearPlayerScoreboard(player);
                    PartyScoreboards.updatePlayerTeamAssignments(player);
                } catch (Exception e) {
                    // Continue with other players
                }
            }

            // Fire disband events for all parties
            for (Party party : parties.values()) {
                fireEvent(() -> {
                    for (PartyEventListener listener : eventListeners) {
                        listener.onPartyDisband(party);
                    }
                });
            }

            parties.clear();
            playerToPartyMap.clear();
            invites.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
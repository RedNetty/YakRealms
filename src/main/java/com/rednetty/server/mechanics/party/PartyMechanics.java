package com.rednetty.server.mechanics.party;

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
 * Enhanced party system mechanics implementation with advanced features
 * Manages player parties, invitations, party roles, sharing systems, and related functionality
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
    private BukkitTask experienceShareTask;

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
        private Location warpLocation;
        private long lastWarpTime = 0;
        private String motd = "";
        private boolean isOpen = false; // Allow anyone to join
        private int maxSize = 8;

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

        // Getters and setters
        public UUID getId() { return id; }
        public UUID getLeader() { return leader; }
        public void setLeader(UUID leader) { this.leader = leader; }
        public Set<UUID> getOfficers() { return new HashSet<>(officers); }
        public Set<UUID> getMembers() { return new HashSet<>(members); }
        public long getCreationTime() { return creationTime; }
        public Location getWarpLocation() { return warpLocation; }
        public void setWarpLocation(Location location) { this.warpLocation = location; }
        public long getLastWarpTime() { return lastWarpTime; }
        public void setLastWarpTime(long time) { this.lastWarpTime = time; }
        public String getMotd() { return motd; }
        public void setMotd(String motd) { this.motd = motd != null ? motd : ""; }
        public boolean isOpen() { return isOpen; }
        public void setOpen(boolean open) { this.isOpen = open; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = Math.max(2, Math.min(20, maxSize)); }

        public List<UUID> getAllMembers() {
            List<UUID> all = new ArrayList<>(members);
            return all;
        }

        public int getSize() {
            return members.size();
        }

        public boolean isLeader(UUID playerId) {
            return leader.equals(playerId);
        }

        public boolean isOfficer(UUID playerId) {
            return officers.contains(playerId);
        }

        public boolean isMember(UUID playerId) {
            return members.contains(playerId);
        }

        public boolean hasPermission(UUID playerId, PartyPermission permission) {
            if (isLeader(playerId)) return true;
            if (isOfficer(playerId)) {
                return permission != PartyPermission.DISBAND &&
                        permission != PartyPermission.PROMOTE_OFFICER &&
                        permission != PartyPermission.DEMOTE_OFFICER;
            }
            return permission == PartyPermission.LEAVE || permission == PartyPermission.CHAT;
        }

        public void addMember(UUID playerId) {
            members.add(playerId);
        }

        public void removeMember(UUID playerId) {
            members.remove(playerId);
            officers.remove(playerId);
            if (leader.equals(playerId) && !members.isEmpty()) {
                // Promote first officer or first member to leader
                UUID newLeader = officers.isEmpty() ? members.iterator().next() : officers.iterator().next();
                setLeader(newLeader);
                officers.remove(newLeader);
            }
        }

        public void promoteToOfficer(UUID playerId) {
            if (members.contains(playerId) && !leader.equals(playerId)) {
                officers.add(playerId);
            }
        }

        public void demoteFromOfficer(UUID playerId) {
            officers.remove(playerId);
        }

        public Object getSetting(String key) {
            return settings.get(key);
        }

        public void setSetting(String key, Object value) {
            settings.put(key, value);
        }

        public boolean getBooleanSetting(String key, boolean defaultValue) {
            Object value = settings.get(key);
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
            return System.currentTimeMillis() - inviteTime > expirySeconds * 1000;
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
        private int totalPartiesCreated = 0;
        private int totalPartiesJoined = 0;
        private long totalTimeInParty = 0;
        private int messagesLent = 0;
        private int experienceShared = 0;
        private long lastPartyTime = 0;

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
        var config = YakRealms.getInstance().getConfig();
        this.maxPartySize = config.getInt("party.max-size", 8);
        this.inviteExpiryTimeSeconds = config.getInt("party.invite-expiry", 30);
        this.experienceSharing = config.getBoolean("party.experience-sharing", true);
        this.lootSharing = config.getBoolean("party.loot-sharing", false);
        this.experienceShareRadius = config.getDouble("party.experience-share-radius", 50.0);
        this.lootShareRadius = config.getDouble("party.loot-share-radius", 30.0);
        this.partyWarpCooldown = config.getInt("party.warp-cooldown", 300);
        this.allowCrossWorldParties = config.getBoolean("party.allow-cross-world", true);
    }

    /**
     * Initialize the enhanced party system
     */
    public void onEnable() {
        // Register events
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start enhanced tasks
        startScoreboardRefreshTask();
        startInviteCleanupTask();
        startPartyMaintenanceTask();
        if (experienceSharing) {
            startExperienceShareTask();
        }

        YakRealms.log("Enhanced party mechanics have been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Cancel tasks
        cancelAllTasks();

        // Save party statistics
        savePartyStatistics();

        // Clear data
        parties.clear();
        invites.clear();
        playerToPartyMap.clear();
        partyStats.clear();
        eventListeners.clear();

        YakRealms.log("Enhanced party mechanics have been disabled.");
    }

    /**
     * Start the enhanced scoreboard refresh task
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
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L);
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
                        if (invite.isExpired(inviteExpiryTimeSeconds)) {
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
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L);
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

                    // Update party experience sharing
                    if (experienceSharing) {
                        processExperienceSharing();
                    }
                } catch (Exception e) {
                    logger.warning("Error in party maintenance: " + e.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20L * 60, 20L * 60); // Every minute
    }

    /**
     * Start experience sharing task
     */
    private void startExperienceShareTask() {
        experienceShareTask = new BukkitRunnable() {
            @Override
            public void run() {
                processExperienceSharing();
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L * 5); // Every 5 seconds
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
        if (experienceShareTask != null) {
            experienceShareTask.cancel();
        }
    }

    // Event listener management
    public void addEventListener(PartyEventListener listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(PartyEventListener listener) {
        eventListeners.remove(listener);
    }

    private void fireEvent(Runnable eventCall) {
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
        return isPartyLeader(player.getUniqueId());
    }

    public boolean isPartyLeader(UUID playerId) {
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
        return isPartyOfficer(player.getUniqueId());
    }

    public boolean isPartyOfficer(UUID playerId) {
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
        UUID leaderId = getPartyLeaderId(player.getUniqueId());
        return leaderId != null ? Bukkit.getPlayer(leaderId) : null;
    }

    public UUID getPartyLeaderId(UUID playerId) {
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
        return isInParty(player.getUniqueId());
    }

    public boolean isInParty(UUID playerId) {
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
        return getParty(player.getUniqueId());
    }

    public Party getParty(UUID playerId) {
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
        List<UUID> memberIds = getPartyMemberIds(player.getUniqueId());
        if (memberIds == null) return null;

        return memberIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<UUID> getPartyMemberIds(UUID playerId) {
        lock.readLock().lock();
        try {
            Party party = getParty(playerId);
            return party != null ? party.getAllMembers() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if two players are in the same party
     */
    public boolean arePartyMembers(Player player1, Player player2) {
        return arePartyMembers(player1.getUniqueId(), player2.getUniqueId());
    }

    public boolean arePartyMembers(UUID player1Id, UUID player2Id) {
        lock.readLock().lock();
        try {
            UUID party1 = playerToPartyMap.get(player1Id);
            UUID party2 = playerToPartyMap.get(player2Id);
            return party1 != null && party1.equals(party2);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Create a new party with enhanced features
     */
    public boolean createParty(Player player) {
        return createParty(player.getUniqueId());
    }

    public boolean createParty(UUID playerId) {
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

            // Update scoreboard
            Player bukkitPlayer = Bukkit.getPlayer(playerId);
            if (bukkitPlayer != null) {
                PartyScoreboards.updatePlayerScoreboard(bukkitPlayer);

                // Show creation effects
                showPartyCreationEffects(bukkitPlayer);
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Add a player to a party with enhanced validation
     */
    public boolean addPlayerToParty(Player player, UUID partyId) {
        return addPlayerToParty(player.getUniqueId(), partyId);
    }

    public boolean addPlayerToParty(UUID playerId, UUID partyId) {
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

            // Update scoreboards for all party members
            updatePartyScoreboards(party);

            // Show join effects
            Player bukkitPlayer = Bukkit.getPlayer(playerId);
            if (bukkitPlayer != null) {
                showPartyJoinEffects(bukkitPlayer);

                // Send MOTD if available
                if (!party.getMotd().isEmpty()) {
                    bukkitPlayer.sendMessage(ChatColor.LIGHT_PURPLE + "Party MOTD: " + ChatColor.WHITE + party.getMotd());
                }
            }

            // Notify party members
            notifyPartyMembers(party, ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                    ChatColor.GRAY + " " + getPlayerName(playerId) + ChatColor.GRAY + " has " +
                    ChatColor.GREEN + ChatColor.UNDERLINE + "joined" + ChatColor.GRAY + " the party.");

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove a player from their party with enhanced handling
     */
    public boolean removePlayerFromParty(Player player) {
        return removePlayerFromParty(player.getUniqueId());
    }

    public boolean removePlayerFromParty(UUID playerId) {
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

            if (wasLeader && !party.getLeader().equals(oldLeader)) {
                // Leader changed
                fireEvent(() -> {
                    for (PartyEventListener listener : eventListeners) {
                        listener.onPartyLeaderChange(party, oldLeader, party.getLeader());
                    }
                });

                // Notify new leader
                Player newLeader = Bukkit.getPlayer(party.getLeader());
                if (newLeader != null) {
                    newLeader.sendMessage(ChatColor.GOLD + "★ You have been promoted to party leader!");
                    showLeaderPromotionEffects(newLeader);
                }
            }

            if (party.getSize() == 0) {
                // Disband empty party
                disbandParty(partyId);
            } else {
                // Update scoreboards for remaining members
                updatePartyScoreboards(party);

                // Notify remaining members
                notifyPartyMembers(party, ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                        ChatColor.GRAY + " " + getPlayerName(playerId) + ChatColor.GRAY + " has " +
                        ChatColor.RED + ChatColor.UNDERLINE + "left" + ChatColor.GRAY + " the party.");
            }

            // Update leaving player's scoreboard
            Player leavingPlayer = Bukkit.getPlayer(playerId);
            if (leavingPlayer != null) {
                PartyScoreboards.clearPlayerScoreboard(leavingPlayer);
                showPartyLeaveEffects(leavingPlayer);
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Disband a party
     */
    public boolean disbandParty(UUID partyId) {
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

            // Notify all members and clear scoreboards
            for (UUID memberId : party.getAllMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(ChatColor.RED + "The party has been disbanded.");
                    PartyScoreboards.clearPlayerScoreboard(member);
                    showPartyDisbandEffects(member);
                }
            }

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enhanced party invitation system
     */
    public boolean invitePlayerToParty(Player invited, Player inviter, String message) {
        return invitePlayerToParty(invited.getUniqueId(), inviter.getUniqueId(), message);
    }

    public boolean invitePlayerToParty(UUID invitedId, UUID inviterId, String message) {
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

            if (!party.hasPermission(inviterId, PartyPermission.INVITE)) {
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
            if (!message.isEmpty()) {
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
        return acceptPartyInvite(player.getUniqueId());
    }

    public boolean acceptPartyInvite(UUID playerId) {
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
        return declinePartyInvite(player.getUniqueId());
    }

    public boolean declinePartyInvite(UUID playerId) {
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
     * Kick a player from party with enhanced permissions
     */
    public boolean kickPlayerFromParty(Player leader, Player playerToKick) {
        return kickPlayerFromParty(leader.getUniqueId(), playerToKick.getUniqueId());
    }

    public boolean kickPlayerFromParty(UUID kickerId, UUID playerToKickId) {
        lock.writeLock().lock();
        try {
            Party party = getParty(kickerId);
            if (party == null) {
                sendMessage(kickerId, ChatColor.RED + "You are not in a party.");
                return false;
            }

            if (!party.hasPermission(kickerId, PartyPermission.KICK)) {
                sendMessage(kickerId, ChatColor.RED + "You don't have permission to kick players.");
                return false;
            }

            if (!party.isMember(playerToKickId)) {
                sendMessage(kickerId, ChatColor.RED + getPlayerName(playerToKickId) + " is not in your party.");
                return false;
            }

            if (kickerId.equals(playerToKickId)) {
                sendMessage(kickerId, ChatColor.RED + "You cannot kick yourself! Use /pquit to leave.");
                return false;
            }

            // Cannot kick the leader
            if (party.isLeader(playerToKickId)) {
                sendMessage(kickerId, ChatColor.RED + "You cannot kick the party leader!");
                return false;
            }

            // Officers cannot kick other officers (only leader can)
            if (party.isOfficer(playerToKickId) && !party.isLeader(kickerId)) {
                sendMessage(kickerId, ChatColor.RED + "You cannot kick another officer!");
                return false;
            }

            String kickerName = getPlayerName(kickerId);
            String kickedName = getPlayerName(playerToKickId);

            // Notify the kicked player
            sendMessage(playerToKickId, ChatColor.RED + "You have been kicked from the party by " +
                    ChatColor.BOLD + kickerName + ChatColor.RED + ".");

            // Remove player
            removePlayerFromParty(playerToKickId);

            // Notify party
            notifyPartyMembers(party, ChatColor.LIGHT_PURPLE + "<" + ChatColor.BOLD + "P" + ChatColor.LIGHT_PURPLE + ">" +
                    ChatColor.GRAY + " " + kickedName + " was " + ChatColor.RED + "kicked" + ChatColor.GRAY + " by " + kickerName + ".");

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Enhanced party messaging with formatting
     */
    public boolean sendPartyMessage(Player sender, String message) {
        return sendPartyMessage(sender.getUniqueId(), message);
    }

    public boolean sendPartyMessage(UUID senderId, String message) {
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
     * Enhanced party warping system
     */
    public boolean setPartyWarp(Player player) {
        return setPartyWarp(player.getUniqueId(), player.getLocation());
    }

    public boolean setPartyWarp(UUID playerId, Location location) {
        lock.writeLock().lock();
        try {
            Party party = getParty(playerId);
            if (party == null) {
                sendMessage(playerId, ChatColor.RED + "You are not in a party.");
                return false;
            }

            if (!party.hasPermission(playerId, PartyPermission.SET_WARP)) {
                sendMessage(playerId, ChatColor.RED + "You don't have permission to set the party warp.");
                return false;
            }

            party.setWarpLocation(location.clone());

            String playerName = getPlayerName(playerId);
            notifyPartyMembers(party, ChatColor.GREEN + "Party warp set by " + ChatColor.BOLD + playerName +
                    ChatColor.GREEN + " at " + formatLocation(location) + ".");

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean warpToParty(Player player) {
        return warpToParty(player.getUniqueId());
    }

    public boolean warpToParty(UUID playerId) {
        lock.writeLock().lock();
        try {
            Party party = getParty(playerId);
            if (party == null) {
                sendMessage(playerId, ChatColor.RED + "You are not in a party.");
                return false;
            }

            if (!party.hasPermission(playerId, PartyPermission.WARP)) {
                sendMessage(playerId, ChatColor.RED + "You don't have permission to use party warp.");
                return false;
            }

            Location warpLocation = party.getWarpLocation();
            if (warpLocation == null) {
                sendMessage(playerId, ChatColor.RED + "No party warp location set.");
                return false;
            }

            // Check cooldown
            long currentTime = System.currentTimeMillis();
            if (currentTime - party.getLastWarpTime() < partyWarpCooldown * 1000) {
                long remaining = (partyWarpCooldown * 1000 - (currentTime - party.getLastWarpTime())) / 1000;
                sendMessage(playerId, ChatColor.RED + "Party warp is on cooldown for " + remaining + " seconds.");
                return false;
            }

            Player bukkitPlayer = Bukkit.getPlayer(playerId);
            if (bukkitPlayer == null) return false;

            // Teleport player
            bukkitPlayer.teleport(warpLocation);
            party.setLastWarpTime(currentTime);

            sendMessage(playerId, ChatColor.GREEN + "Warped to party location!");
            playSound(playerId, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

            // Show particles
            warpLocation.getWorld().spawnParticle(Particle.PORTAL, warpLocation, 20, 1, 1, 1, 0.1);

            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Promote a player to officer
     */
    public boolean promoteToOfficer(UUID promoterId, UUID playerId) {
        lock.writeLock().lock();
        try {
            Party party = getParty(promoterId);
            if (party == null) {
                sendMessage(promoterId, ChatColor.RED + "You are not in a party.");
                return false;
            }

            if (!party.hasPermission(promoterId, PartyPermission.PROMOTE_OFFICER)) {
                sendMessage(promoterId, ChatColor.RED + "You don't have permission to promote officers.");
                return false;
            }

            if (!party.isMember(playerId)) {
                sendMessage(promoterId, ChatColor.RED + getPlayerName(playerId) + " is not in your party.");
                return false;
            }

            if (party.isLeader(playerId)) {
                sendMessage(promoterId, ChatColor.RED + "The party leader cannot be promoted to officer.");
                return false;
            }

            if (party.isOfficer(playerId)) {
                sendMessage(promoterId, ChatColor.RED + getPlayerName(playerId) + " is already an officer.");
                return false;
            }

            party.promoteToOfficer(playerId);

            String promoterName = getPlayerName(promoterId);
            String promotedName = getPlayerName(playerId);

            notifyPartyMembers(party, ChatColor.GOLD + promotedName + " has been promoted to officer by " + promoterName + "!");

            // Special notification to promoted player
            sendMessage(playerId, ChatColor.GOLD + "★ You have been promoted to party officer! ★");
            playSound(playerId, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

            updatePartyScoreboards(party);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Demote an officer to member
     */
    public boolean demoteFromOfficer(UUID demoterId, UUID playerId) {
        lock.writeLock().lock();
        try {
            Party party = getParty(demoterId);
            if (party == null) {
                sendMessage(demoterId, ChatColor.RED + "You are not in a party.");
                return false;
            }

            if (!party.hasPermission(demoterId, PartyPermission.DEMOTE_OFFICER)) {
                sendMessage(demoterId, ChatColor.RED + "You don't have permission to demote officers.");
                return false;
            }

            if (!party.isOfficer(playerId)) {
                sendMessage(demoterId, ChatColor.RED + getPlayerName(playerId) + " is not an officer.");
                return false;
            }

            party.demoteFromOfficer(playerId);

            String demoterName = getPlayerName(demoterId);
            String demotedName = getPlayerName(playerId);

            notifyPartyMembers(party, ChatColor.YELLOW + demotedName + " has been demoted from officer by " + demoterName + ".");

            sendMessage(playerId, ChatColor.YELLOW + "You have been demoted from party officer.");
            playSound(playerId, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

            updatePartyScoreboards(party);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // Enhanced utility methods

    /**
     * Update party statistics
     */
    private void updatePartyStatistics() {
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
    }

    /**
     * Clean up empty parties
     */
    private void cleanupEmptyParties() {
        List<UUID> emptyParties = new ArrayList<>();

        for (Map.Entry<UUID, Party> entry : parties.entrySet()) {
            Party party = entry.getValue();
            if (party.getSize() == 0) {
                emptyParties.add(entry.getKey());
            }
        }

        for (UUID partyId : emptyParties) {
            disbandParty(partyId);
        }
    }

    /**
     * Process experience sharing
     */
    private void processExperienceSharing() {
        for (Party party : parties.values()) {
            if (!party.getBooleanSetting("experience_sharing", true)) continue;
            if (party.getSize() < 2) continue;

            List<Player> nearbyMembers = new ArrayList<>();

            // Find nearby members
            for (UUID memberId : party.getAllMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    nearbyMembers.add(member);
                }
            }

            if (nearbyMembers.size() < 2) continue;

            // Check if members are within sharing radius
            for (int i = 0; i < nearbyMembers.size(); i++) {
                Player player1 = nearbyMembers.get(i);
                for (int j = i + 1; j < nearbyMembers.size(); j++) {
                    Player player2 = nearbyMembers.get(j);

                    if (player1.getWorld().equals(player2.getWorld()) &&
                            player1.getLocation().distance(player2.getLocation()) <= experienceShareRadius) {

                        // Share experience bonus
                        int bonusExp = calculateExperienceBonus(party.getSize());
                        if (bonusExp > 0) {
                            player1.giveExp(bonusExp);
                            player2.giveExp(bonusExp);

                            // Update statistics
                            PartyStatistics stats1 = partyStats.computeIfAbsent(player1.getUniqueId(), k -> new PartyStatistics());
                            PartyStatistics stats2 = partyStats.computeIfAbsent(player2.getUniqueId(), k -> new PartyStatistics());
                            stats1.addExperienceShared(bonusExp);
                            stats2.addExperienceShared(bonusExp);
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculate experience bonus based on party size
     */
    private int calculateExperienceBonus(int partySize) {
        return Math.max(0, partySize - 1); // 1 bonus exp per extra member
    }

    /**
     * Notify party invite expiry
     */
    private void notifyInviteExpiry(PartyInvite invite) {
        String inviterName = getPlayerName(invite.getInviter());
        sendMessage(invite.getInvited(), ChatColor.RED + "Party invite from " + inviterName + " has expired.");
        sendMessage(invite.getInviter(), ChatColor.RED + "Party invite to " + getPlayerName(invite.getInvited()) + " has expired.");
    }

    /**
     * Update scoreboards for all party members
     */
    private void updatePartyScoreboards(Party party) {
        for (UUID memberId : party.getAllMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                PartyScoreboards.updatePlayerScoreboard(member);
            }
        }
    }

    /**
     * Notify all party members with a message
     */
    private void notifyPartyMembers(Party party, String message) {
        for (UUID memberId : party.getAllMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    /**
     * Format party message with enhanced styling
     */
    private String formatPartyMessage(Party party, UUID senderId, String senderName, String message) {
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
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 10, 1, 1, 1, 0.1);
    }

    private void showPartyJoinEffects(Player player) {
        PartyScoreboards.showPartyJoinEffects(player);
    }

    private void showPartyLeaveEffects(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
    }

    private void showPartyDisbandEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
    }

    private void showLeaderPromotionEffects(Player player) {
        PartyScoreboards.showLeaderPromotionEffects(player);
    }

    /**
     * Utility methods
     */
    private String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        YakPlayer yakPlayer = playerManager.getPlayer(playerId);
        if (yakPlayer != null) {
            return yakPlayer.getUsername();
        }

        return "Unknown";
    }

    private void sendMessage(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    private void playSound(UUID playerId, Sound sound, float volume, float pitch) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private String formatLocation(Location location) {
        return String.format("%s (%.0f, %.0f, %.0f)",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }

    /**
     * Save party statistics
     */
    private void savePartyStatistics() {
        // This would save to database in a real implementation
        logger.info("Saving party statistics for " + partyStats.size() + " players");
    }

    /**
     * Handle player quit event to remove them from their party
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (isInParty(playerId)) {
            // Update statistics before leaving
            PartyStatistics stats = partyStats.get(playerId);
            if (stats != null) {
                long timeInParty = System.currentTimeMillis() - stats.getLastPartyTime();
                stats.addTimeInParty(timeInParty);
            }

            removePlayerFromParty(playerId);
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
        return partyStats.computeIfAbsent(playerId, k -> new PartyStatistics());
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
                PartyScoreboards.clearPlayerScoreboard(player);
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
}
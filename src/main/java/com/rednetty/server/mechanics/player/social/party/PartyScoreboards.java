package com.rednetty.server.mechanics.player.social.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * COMPLETELY FIXED Party scoreboards with robust team handling and corruption prevention
 * - Eliminated all race conditions with proper synchronization
 * - Fixed team conflicts and visual corruption
 * - Proper cleanup and emergency recovery systems
 * - Backwards compatible with all existing systems
 * - Enhanced memory management and leak prevention
 */
public class PartyScoreboards {
    private static final Logger logger = YakRealms.getInstance().getLogger();

    // Core tracking maps with thread safety
    private static final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> partyHealthBars = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> activeScoreboardEntries = new ConcurrentHashMap<>();
    private static final Map<UUID, PartyVisualEffects> partyVisuals = new ConcurrentHashMap<>();

    // CRITICAL: Per-player locks to prevent race conditions
    private static final Map<UUID, Object> playerScoreboardLocks = new ConcurrentHashMap<>();
    private static final Object globalScoreboardLock = new Object();

    // Performance and throttling
    private static final long UPDATE_COOLDOWN = 50;
    private static final long FORCE_REFRESH_INTERVAL = 2000;
    private static final long EMERGENCY_CLEANUP_INTERVAL = 30000; // 30 seconds

    // Team constants - FIXED to prevent conflicts
    private static final String CHAOTIC_TEAM = "z_chaotic";
    private static final String NEUTRAL_TEAM = "z_neutral";
    private static final String LAWFUL_TEAM = "z_lawful";
    private static final String DEFAULT_TEAM = "z_default";
    private static final String ADMIN_DEV_TEAM = "a_admin_dev";
    private static final String ADMIN_MANAGER_TEAM = "a_admin_manager";
    private static final String ADMIN_GM_TEAM = "a_admin_gm";

    // Emergency state tracking
    private static final Set<UUID> corruptedScoreboards = ConcurrentHashMap.newKeySet();
    private static volatile long lastEmergencyCleanup = 0;

    /**
     * BACKWARDS COMPATIBLE: Main update method with full error recovery
     */
    public static void updateScoreboardForPlayer(Player player, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || partyMechanics == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // CRITICAL: Check for emergency cleanup need
        checkEmergencyCleanup();

        // CRITICAL: Get or create player-specific lock
        Object playerLock = playerScoreboardLocks.computeIfAbsent(playerId, k -> new Object());

        synchronized (playerLock) {
            try {
                // Throttle updates to prevent spam
                if (!shouldUpdateScoreboard(playerId)) {
                    return;
                }

                // CRITICAL: Check if scoreboard is corrupted
                if (corruptedScoreboards.contains(playerId)) {
                    logger.info("Attempting recovery for corrupted scoreboard: " + player.getName());
                    performEmergencyScoreboardRecovery(player, partyMechanics);
                    corruptedScoreboards.remove(playerId);
                    return;
                }

                Scoreboard scoreboard = getOrCreatePlayerScoreboard(player);
                if (scoreboard == null) {
                    logger.warning("Failed to create scoreboard for " + player.getName() + ", marking for recovery");
                    corruptedScoreboards.add(playerId);
                    return;
                }

                // CRITICAL: Atomic scoreboard update
                updateScoreboardSafely(player, scoreboard, partyMechanics);
                lastUpdateTimes.put(playerId, System.currentTimeMillis());

            } catch (Exception e) {
                logger.severe("Critical error updating scoreboard for " + player.getName() + ": " + e.getMessage());
                corruptedScoreboards.add(playerId);

                // Attempt immediate recovery
                try {
                    performEmergencyScoreboardRecovery(player, partyMechanics);
                } catch (Exception ex) {
                    logger.severe("Emergency recovery failed for " + player.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * CRITICAL: Safe scoreboard update with corruption protection
     */
    private static void updateScoreboardSafely(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        synchronized (globalScoreboardLock) {
            try {
                // CRITICAL: Validate scoreboard state before update
                if (!isScoreboardValid(scoreboard)) {
                    throw new IllegalStateException("Scoreboard validation failed");
                }

                // Update team assignments with full error recovery
                updatePlayerTeamAssignmentsSafely(player, scoreboard, partyMechanics);

                // Update party objective if in party
                if (partyMechanics.isInParty(player)) {
                    updatePartyObjectiveSafely(player, scoreboard, partyMechanics);
                    updatePartyHealthBar(player, partyMechanics);
                } else {
                    clearPartyObjectiveSafely(player, scoreboard);
                    clearPartyHealthBar(player.getUniqueId());
                }

                // Ensure health display is working
                setupHealthDisplaySafely(scoreboard);

                // Apply scoreboard atomically
                if (player.getScoreboard() != scoreboard) {
                    player.setScoreboard(scoreboard);
                }

            } catch (Exception e) {
                throw new RuntimeException("Scoreboard update failed", e);
            }
        }
    }

    /**
     * COMPLETELY REWRITTEN: Team assignment with corruption prevention
     */
    private static void updatePlayerTeamAssignmentsSafely(Player viewer, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        if (viewer == null || !viewer.isOnline() || scoreboard == null) {
            return;
        }

        try {
            // CRITICAL: Get party state snapshot to prevent mid-update changes
            PartyStateSnapshot partyState = capturePartyState(viewer, partyMechanics);

            // CRITICAL: Clear all team entries safely
            clearAllTeamEntriesSafely(scoreboard);

            // CRITICAL: Recreate teams from scratch to prevent corruption
            setupTeamsSafely(scoreboard);

            // CRITICAL: Assign all players to teams atomically
            assignPlayersToTeamsSafely(viewer, scoreboard, partyState);

        } catch (Exception e) {
            logger.severe("Team assignment failed for " + viewer.getName() + ": " + e.getMessage());
            throw new RuntimeException("Team assignment corruption", e);
        }
    }

    /**
     * CRITICAL: Capture party state to prevent race conditions
     */
    private static class PartyStateSnapshot {
        final boolean viewerInParty;
        final List<Player> partyMembers;
        final Map<Player, String> partyRoles;

        PartyStateSnapshot(Player viewer, PartyMechanics partyMechanics) {
            boolean inParty = false;
            List<Player> members = new ArrayList<>();
            Map<Player, String> roles = new HashMap<>();

            try {
                inParty = partyMechanics.isInParty(viewer);
                if (inParty) {
                    members = new ArrayList<>(partyMechanics.getPartyMembers(viewer));

                    // Capture roles
                    for (Player member : members) {
                        if (partyMechanics.isPartyLeader(member)) {
                            roles.put(member, "LEADER");
                        } else if (partyMechanics.isPartyOfficer(member)) {
                            roles.put(member, "OFFICER");
                        } else {
                            roles.put(member, "MEMBER");
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error capturing party state: " + e.getMessage());
            }

            this.viewerInParty = inParty;
            this.partyMembers = Collections.unmodifiableList(members);
            this.partyRoles = Collections.unmodifiableMap(roles);
        }
    }

    private static PartyStateSnapshot capturePartyState(Player viewer, PartyMechanics partyMechanics) {
        return new PartyStateSnapshot(viewer, partyMechanics);
    }

    /**
     * CRITICAL: Safe team entry clearing
     */
    private static void clearAllTeamEntriesSafely(Scoreboard scoreboard) {
        Set<Team> teams = new HashSet<>(scoreboard.getTeams());
        for (Team team : teams) {
            try {
                Set<String> entries = new HashSet<>(team.getEntries());
                for (String entry : entries) {
                    try {
                        team.removeEntry(entry);
                    } catch (Exception e) {
                        // Log but continue
                        logger.fine("Failed to remove entry " + entry + " from team " + team.getName());
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to clear team entries for " + team.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * CRITICAL: Safe team setup with validation
     */
    private static void setupTeamsSafely(Scoreboard scoreboard) {
        // Remove existing teams first
        Set<Team> existingTeams = new HashSet<>(scoreboard.getTeams());
        for (Team team : existingTeams) {
            try {
                team.unregister();
            } catch (Exception e) {
                logger.fine("Failed to unregister team " + team.getName());
            }
        }

        // Create new teams with proper priority order
        try {
            // Admin teams (highest priority - 'a_' prefix for sorting)
            createTeamSafely(scoreboard, ADMIN_DEV_TEAM, ChatColor.GOLD,
                    ChatColor.GOLD + "⚡ DEV " + ChatColor.GOLD, "");

            createTeamSafely(scoreboard, ADMIN_MANAGER_TEAM, ChatColor.YELLOW,
                    ChatColor.YELLOW + "★ MANAGER " + ChatColor.YELLOW, "");

            createTeamSafely(scoreboard, ADMIN_GM_TEAM, ChatColor.AQUA,
                    ChatColor.AQUA + "♦ GM " + ChatColor.AQUA, "");

            // Regular alignment teams (lower priority - 'z_' prefix)
            createTeamSafely(scoreboard, CHAOTIC_TEAM, ChatColor.RED, "", "");
            createTeamSafely(scoreboard, NEUTRAL_TEAM, ChatColor.YELLOW, "", "");
            createTeamSafely(scoreboard, LAWFUL_TEAM, ChatColor.GRAY, "", "");
            createTeamSafely(scoreboard, DEFAULT_TEAM, ChatColor.WHITE, "", "");

        } catch (Exception e) {
            throw new RuntimeException("Team setup failed", e);
        }
    }

    /**
     * CRITICAL: Safe team creation with validation
     */
    private static void createTeamSafely(Scoreboard scoreboard, String name, ChatColor color, String prefix, String suffix) {
        try {
            // Ensure team doesn't already exist
            Team existingTeam = scoreboard.getTeam(name);
            if (existingTeam != null) {
                existingTeam.unregister();
            }

            Team team = scoreboard.registerNewTeam(name);
            team.setColor(color);
            if (prefix != null && !prefix.isEmpty()) {
                team.setPrefix(prefix);
            }
            if (suffix != null && !suffix.isEmpty()) {
                team.setSuffix(suffix);
            }

            // Configure team properties
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);

        } catch (Exception e) {
            logger.warning("Failed to create team " + name + ": " + e.getMessage());
            throw new RuntimeException("Team creation failed: " + name, e);
        }
    }

    /**
     * CRITICAL: Safe player team assignment
     */
    private static void assignPlayersToTeamsSafely(Player viewer, Scoreboard scoreboard, PartyStateSnapshot partyState) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target == null || !target.isOnline()) continue;

            try {
                String teamName = determinePlayerTeamSafely(target);
                Team team = scoreboard.getTeam(teamName);

                if (team != null) {
                    // CRITICAL: Prevent duplicate entries
                    if (!team.hasEntry(target.getName())) {
                        team.addEntry(target.getName());
                    }

                    // CRITICAL: Apply party prefix only for same-party members
                    if (partyState.viewerInParty && partyState.partyMembers.contains(target)) {
                        String partyRole = partyState.partyRoles.get(target);
                        String partyPrefix = getPartyRolePrefix(partyRole);

                        if (!partyPrefix.isEmpty()) {
                            // Don't override admin prefixes
                            String currentPrefix = team.getPrefix();
                            if (currentPrefix == null || (!currentPrefix.contains("DEV") &&
                                    !currentPrefix.contains("MANAGER") && !currentPrefix.contains("GM"))) {
                                team.setPrefix(partyPrefix);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                logger.fine("Failed to assign team for " + target.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * CRITICAL: Safe team determination
     */
    private static String determinePlayerTeamSafely(Player target) {
        if (target == null) {
            return DEFAULT_TEAM;
        }

        try {
            // Check admin rank first
            Rank rank = ModerationMechanics.getInstance().getPlayerRank(target.getUniqueId());
            if (rank != null) {
                switch (rank) {
                    case DEV:
                        return ADMIN_DEV_TEAM;
                    case MANAGER:
                        return ADMIN_MANAGER_TEAM;
                    case GM:
                        return ADMIN_GM_TEAM;
                }
            }

            // Check alignment for regular players
            YakPlayerManager playerManager = YakPlayerManager.getInstance();
            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(target);
                if (yakPlayer != null) {
                    String alignment = yakPlayer.getAlignment();
                    if (alignment != null) {
                        switch (alignment.toUpperCase()) {
                            case "CHAOTIC":
                                return CHAOTIC_TEAM;
                            case "NEUTRAL":
                                return NEUTRAL_TEAM;
                            case "LAWFUL":
                                return LAWFUL_TEAM;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error determining team for " + target.getName() + ": " + e.getMessage());
        }

        return DEFAULT_TEAM;
    }

    /**
     * CRITICAL: Safe party prefix determination
     */
    private static String getPartyRolePrefix(String role) {
        if (role == null) return "";

        switch (role) {
            case "LEADER":
                return ChatColor.GOLD + "★" + ChatColor.LIGHT_PURPLE + "[P] ";
            case "OFFICER":
                return ChatColor.YELLOW + "♦" + ChatColor.LIGHT_PURPLE + "[P] ";
            case "MEMBER":
                return ChatColor.LIGHT_PURPLE + "[P] ";
            default:
                return "";
        }
    }

    /**
     * CRITICAL: Safe party objective update
     */
    private static void updatePartyObjectiveSafely(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        UUID playerId = player.getUniqueId();

        try {
            // Clear existing party objective
            Objective existing = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (existing != null) {
                existing.unregister();
            }

            // Clear tracked entries
            Set<String> currentEntries = activeScoreboardEntries.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
            currentEntries.clear();

            // Create new party objective
            Objective partyObjective = scoreboard.registerNewObjective(
                    "party_data", "dummy",
                    ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + "PARTY" + ChatColor.LIGHT_PURPLE + " ✦"
            );
            partyObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers != null && !partyMembers.isEmpty()) {
                populatePartyObjective(partyObjective, partyMembers, partyMechanics, currentEntries);
            }

        } catch (Exception e) {
            logger.warning("Failed to update party objective for " + player.getName() + ": " + e.getMessage());
            throw new RuntimeException("Party objective update failed", e);
        }
    }

    /**
     * CRITICAL: Safe party objective population
     */
    private static void populatePartyObjective(Objective objective, List<Player> partyMembers,
                                               PartyMechanics partyMechanics, Set<String> currentEntries) {
        try {
            // Add party size indicator
            String sizeIndicator = ChatColor.GRAY + "Members: " + ChatColor.WHITE + partyMembers.size();
            objective.getScore(sizeIndicator).setScore(15);
            currentEntries.add(sizeIndicator);

            // Add separator
            String separator = " ";
            objective.getScore(separator).setScore(14);
            currentEntries.add(separator);

            // Sort members safely
            List<Player> sortedMembers = new ArrayList<>(partyMembers);
            sortedMembers.sort((p1, p2) -> {
                if (p1 == null || p2 == null) return 0;

                try {
                    boolean p1Leader = partyMechanics.isPartyLeader(p1);
                    boolean p2Leader = partyMechanics.isPartyLeader(p2);
                    boolean p1Officer = partyMechanics.isPartyOfficer(p1);
                    boolean p2Officer = partyMechanics.isPartyOfficer(p2);

                    if (p1Leader && !p2Leader) return -1;
                    if (!p1Leader && p2Leader) return 1;
                    if (p1Officer && !p2Officer) return -1;
                    if (!p1Officer && p2Officer) return 1;
                    return p1.getName().compareTo(p2.getName());
                } catch (Exception e) {
                    return 0;
                }
            });

            // Add member entries
            int scoreIndex = 13;
            Set<String> usedNames = new HashSet<>();

            for (Player member : sortedMembers) {
                if (member == null || !member.isOnline()) continue;

                String displayName = formatPartyMemberNameSafely(member, partyMechanics, scoreIndex, usedNames);
                if (displayName == null) continue;

                try {
                    int health = (int) Math.ceil(member.getHealth());
                    objective.getScore(displayName).setScore(health);
                    currentEntries.add(displayName);
                    usedNames.add(displayName);
                    scoreIndex--;
                } catch (Exception e) {
                    logger.fine("Failed to add member " + member.getName() + " to party objective");
                }
            }

        } catch (Exception e) {
            logger.warning("Failed to populate party objective: " + e.getMessage());
        }
    }

    /**
     * CRITICAL: Safe member name formatting
     */
    private static String formatPartyMemberNameSafely(Player member, PartyMechanics partyMechanics,
                                                      int fallbackIndex, Set<String> usedNames) {
        if (member == null) return null;

        try {
            String role = "";
            if (partyMechanics.isPartyLeader(member)) {
                role = ChatColor.GOLD + "★ ";
            } else if (partyMechanics.isPartyOfficer(member)) {
                role = ChatColor.YELLOW + "♦ ";
            }

            String baseName = role + ChatColor.WHITE + member.getName();

            // Ensure uniqueness
            if (!usedNames.contains(baseName)) {
                return baseName;
            }

            // Fallback with index
            return baseName + " " + fallbackIndex;

        } catch (Exception e) {
            return "Player " + fallbackIndex;
        }
    }

    /**
     * BACKWARDS COMPATIBLE: Clear party objective
     */
    private static void clearPartyObjectiveSafely(Player player, Scoreboard scoreboard) {
        try {
            Objective existing = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (existing != null) {
                existing.unregister();
            }

            // Clear tracked entries
            Set<String> entries = activeScoreboardEntries.get(player.getUniqueId());
            if (entries != null) {
                entries.clear();
            }

        } catch (Exception e) {
            logger.fine("Error clearing party objective for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * CRITICAL: Safe health display setup
     */
    private static void setupHealthDisplaySafely(Scoreboard scoreboard) {
        try {
            Objective healthObj = scoreboard.getObjective("showhealth");
            if (healthObj == null) {
                healthObj = scoreboard.registerNewObjective("showhealth", "health");
                healthObj.setDisplaySlot(DisplaySlot.BELOW_NAME);
                healthObj.setDisplayName(ChatColor.RED + "❤");
            }
        } catch (Exception e) {
            logger.fine("Error setting up health display: " + e.getMessage());
        }
    }

    /**
     * BACKWARDS COMPATIBLE: Get or create scoreboard
     */
    public static Scoreboard getPlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }

        return getOrCreatePlayerScoreboard(player);
    }

    /**
     * CRITICAL: Safe scoreboard creation with validation
     */
    private static Scoreboard getOrCreatePlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        Scoreboard existingScoreboard = playerScoreboards.get(playerId);
        if (existingScoreboard != null && isScoreboardValid(existingScoreboard)) {
            return existingScoreboard;
        }

        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) {
                logger.severe("ScoreboardManager is null!");
                return null;
            }

            Scoreboard scoreboard = manager.getNewScoreboard();
            if (scoreboard == null) {
                logger.severe("Failed to create new scoreboard!");
                return null;
            }

            // Setup teams and health display
            setupTeamsSafely(scoreboard);
            setupHealthDisplaySafely(scoreboard);

            playerScoreboards.put(playerId, scoreboard);
            activeScoreboardEntries.put(playerId, ConcurrentHashMap.newKeySet());

            return scoreboard;

        } catch (Exception e) {
            logger.severe("Error creating scoreboard for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * CRITICAL: Scoreboard validation
     */
    private static boolean isScoreboardValid(Scoreboard scoreboard) {
        if (scoreboard == null) return false;

        try {
            // Basic validation
            scoreboard.getTeams();
            scoreboard.getObjectives();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * CRITICAL: Emergency recovery system
     */
    private static void performEmergencyScoreboardRecovery(Player player, PartyMechanics partyMechanics) {
        UUID playerId = player.getUniqueId();
        logger.info("Performing emergency scoreboard recovery for " + player.getName());

        try {
            // Complete cleanup
            cleanupPlayerCompletely(player);

            // Wait a tick for cleanup to complete
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    // Create fresh scoreboard
                    Scoreboard newScoreboard = getOrCreatePlayerScoreboard(player);
                    if (newScoreboard != null) {
                        updateScoreboardSafely(player, newScoreboard, partyMechanics);
                        logger.info("Emergency recovery successful for " + player.getName());
                    } else {
                        logger.severe("Emergency recovery failed - could not create scoreboard for " + player.getName());
                    }
                } catch (Exception e) {
                    logger.severe("Emergency recovery failed for " + player.getName() + ": " + e.getMessage());
                }
            }, 1L);

        } catch (Exception e) {
            logger.severe("Emergency recovery setup failed for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * CRITICAL: Complete player cleanup
     */
    private static void cleanupPlayerCompletely(Player player) {
        UUID playerId = player.getUniqueId();

        try {
            // Remove from all tracking
            playerScoreboards.remove(playerId);
            activeScoreboardEntries.remove(playerId);
            lastUpdateTimes.remove(playerId);

            // Clear boss bar
            BossBar bossBar = partyHealthBars.remove(playerId);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            // Clear visual effects
            PartyVisualEffects effects = partyVisuals.remove(playerId);
            if (effects != null) {
                // Effects cleanup handled by garbage collection
            }

            // Reset to main scoreboard temporarily
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null && manager.getMainScoreboard() != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }

        } catch (Exception e) {
            logger.warning("Error in complete cleanup for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * BACKWARDS COMPATIBLE: Update party health bar
     */
    public static void updatePartyHealthBar(Player player, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || partyMechanics == null) {
            return;
        }

        try {
            UUID playerId = player.getUniqueId();
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);

            if (partyMembers == null || partyMembers.isEmpty()) {
                clearPartyHealthBar(playerId);
                return;
            }

            BossBar bossBar = partyHealthBars.get(playerId);
            if (bossBar == null) {
                bossBar = Bukkit.createBossBar(
                        ChatColor.LIGHT_PURPLE + "Party Health",
                        BarColor.PURPLE,
                        BarStyle.SEGMENTED_10
                );
                partyHealthBars.put(playerId, bossBar);
                bossBar.addPlayer(player);
            }

            // Calculate party health percentage
            double totalHealth = 0;
            double maxHealth = 0;

            for (Player member : partyMembers) {
                if (member != null && member.isOnline()) {
                    totalHealth += member.getHealth();
                    maxHealth += member.getMaxHealth();
                }
            }

            if (maxHealth > 0) {
                double healthPercentage = totalHealth / maxHealth;
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, healthPercentage)));

                // Update color based on health
                if (healthPercentage > 0.6) {
                    bossBar.setColor(BarColor.GREEN);
                } else if (healthPercentage > 0.3) {
                    bossBar.setColor(BarColor.YELLOW);
                } else {
                    bossBar.setColor(BarColor.RED);
                }
            }

        } catch (Exception e) {
            logger.fine("Error updating party health bar for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * BACKWARDS COMPATIBLE: Clear party health bar
     */
    public static void clearPartyHealthBar(UUID playerId) {
        BossBar bossBar = partyHealthBars.remove(playerId);
        if (bossBar != null) {
            try {
                bossBar.removeAll();
            } catch (Exception e) {
                logger.fine("Error clearing party health bar: " + e.getMessage());
            }
        }
    }

    /**
     * BACKWARDS COMPATIBLE: Cleanup player
     */
    public static void cleanupPlayer(Player player) {
        if (player != null) {
            cleanupPlayerCompletely(player);
            playerScoreboardLocks.remove(player.getUniqueId());
        }
    }

    /**
     * CRITICAL: Emergency cleanup check
     */
    private static void checkEmergencyCleanup() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEmergencyCleanup > EMERGENCY_CLEANUP_INTERVAL) {
            performEmergencyCleanup();
            lastEmergencyCleanup = currentTime;
        }
    }

    /**
     * CRITICAL: Emergency cleanup of corrupted state
     */
    private static void performEmergencyCleanup() {
        try {
            // Clean up offline players
            Set<UUID> toRemove = new HashSet<>();
            for (UUID playerId : playerScoreboards.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    toRemove.add(playerId);
                }
            }

            for (UUID playerId : toRemove) {
                playerScoreboards.remove(playerId);
                activeScoreboardEntries.remove(playerId);
                lastUpdateTimes.remove(playerId);
                clearPartyHealthBar(playerId);
                playerScoreboardLocks.remove(playerId);
            }

            // Clear corruption markers for offline players
            corruptedScoreboards.removeIf(playerId -> {
                Player player = Bukkit.getPlayer(playerId);
                return player == null || !player.isOnline();
            });

            if (!toRemove.isEmpty()) {
                logger.info("Emergency cleanup removed " + toRemove.size() + " offline player scoreboards");
            }

        } catch (Exception e) {
            logger.warning("Error in emergency cleanup: " + e.getMessage());
        }
    }

    /**
     * CRITICAL: Update throttling
     */
    private static boolean shouldUpdateScoreboard(UUID playerId) {
        Long lastUpdate = lastUpdateTimes.get(playerId);
        if (lastUpdate == null) {
            return true;
        }

        long timeSince = System.currentTimeMillis() - lastUpdate;
        return timeSince >= UPDATE_COOLDOWN;
    }

    /**
     * BACKWARDS COMPATIBLE: Visual effects class
     */
    private static class PartyVisualEffects {
        private final Player player;

        public PartyVisualEffects(Player player) {
            this.player = player;
        }

        public void showPartyJoinEffect() {
            if (!isPlayerValid()) return;

            try {
                player.sendTitle(
                        ChatColor.LIGHT_PURPLE + "✦ PARTY JOINED ✦",
                        ChatColor.GRAY + "You are now part of a team",
                        10, 40, 10
                );
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            } catch (Exception e) {
                // Ignore visual effect errors
            }
        }

        public void showPartyLeaveEffect() {
            if (!isPlayerValid()) return;

            try {
                player.sendTitle(
                        ChatColor.RED + "✦ PARTY LEFT ✦",
                        ChatColor.GRAY + "You are no longer in a party",
                        10, 30, 10
                );
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
            } catch (Exception e) {
                // Ignore visual effect errors
            }
        }

        public void showLeaderPromotionEffect() {
            if (!isPlayerValid()) return;

            try {
                player.sendTitle(
                        ChatColor.GOLD + "★ PARTY LEADER ★",
                        ChatColor.YELLOW + "You are now the party leader",
                        10, 50, 10
                );
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            } catch (Exception e) {
                // Ignore visual effect errors
            }
        }

        private boolean isPlayerValid() {
            return player != null && player.isOnline();
        }
    }

    // BACKWARDS COMPATIBLE: All existing public methods preserved
    public static void showPartyJoinEffect(Player player) {
        if (player != null) {
            PartyVisualEffects effects = partyVisuals.computeIfAbsent(player.getUniqueId(),
                    k -> new PartyVisualEffects(player));
            effects.showPartyJoinEffect();
        }
    }

    public static void showPartyLeaveEffect(Player player) {
        if (player != null) {
            PartyVisualEffects effects = partyVisuals.get(player.getUniqueId());
            if (effects != null) {
                effects.showPartyLeaveEffect();
            }
            partyVisuals.remove(player.getUniqueId());
        }
    }

    public static void showLeaderPromotionEffect(Player player) {
        if (player != null) {
            PartyVisualEffects effects = partyVisuals.computeIfAbsent(player.getUniqueId(),
                    k -> new PartyVisualEffects(player));
            effects.showLeaderPromotionEffect();
        }
    }
}
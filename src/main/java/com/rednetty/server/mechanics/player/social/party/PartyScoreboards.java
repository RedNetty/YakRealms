package com.rednetty.server.mechanics.player.social.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Party scoreboards with proper team handling and health display
 * - Fixed above head name colors to show alignment even in parties
 * - Fixed health display conflicts between party/non-party modes
 * - Simplified update logic to prevent throttling issues
 * - Improved team management to handle both party roles and alignment colors
 */
public class PartyScoreboards {
    private static final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> partyHealthBars = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    // Track entries for cleanup
    private static final Map<UUID, Set<String>> activeScoreboardEntries = new ConcurrentHashMap<>();

    // Visual effects system
    private static final Map<UUID, PartyVisualEffects> partyVisuals = new ConcurrentHashMap<>();

    // Reduced throttling for better responsiveness
    private static final long UPDATE_COOLDOWN = 50; // Reduced from 100ms
    private static final long FORCE_REFRESH_INTERVAL = 2000; // Reduced from 5000ms

    // Team name constants - SIMPLIFIED to avoid conflicts
    private static final String CHAOTIC_TEAM = "chaotic";
    private static final String NEUTRAL_TEAM = "neutral";
    private static final String LAWFUL_TEAM = "lawful";
    private static final String DEFAULT_TEAM = "default";
    private static final String ADMIN_DEV_TEAM = "admin_dev";
    private static final String ADMIN_MANAGER_TEAM = "admin_manager";
    private static final String ADMIN_GM_TEAM = "admin_gm";

    /**
     * Visual effects system for party interactions
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

                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
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

                player.playSound(player.getLocation(),
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
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

                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            } catch (Exception e) {
                // Ignore visual effect errors
            }
        }

        private boolean isPlayerValid() {
            return player != null && player.isOnline();
        }
    }

    /**
     * Get or create a player's scoreboard with proper error handling
     */
    public static Scoreboard getPlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }

        UUID playerId = player.getUniqueId();

        Scoreboard existingScoreboard = playerScoreboards.get(playerId);
        if (existingScoreboard != null) {
            return existingScoreboard;
        }

        try {
            // Create new scoreboard
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager == null) {
                Bukkit.getLogger().severe("ScoreboardManager is null!");
                return null;
            }

            Scoreboard scoreboard = manager.getNewScoreboard();
            if (scoreboard == null) {
                Bukkit.getLogger().severe("Failed to create new scoreboard!");
                return null;
            }

            // Setup teams and health display
            setupTeams(scoreboard);
            setupHealthDisplay(scoreboard);

            playerScoreboards.put(playerId, scoreboard);
            activeScoreboardEntries.put(playerId, ConcurrentHashMap.newKeySet());

            return scoreboard;
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error creating scoreboard for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Setup teams with alignment-based coloring and party role prefixes
     */
    private static void setupTeams(Scoreboard scoreboard) {
        if (scoreboard == null) return;

        try {
            // Clear existing teams safely
            Set<Team> existingTeams = new HashSet<>(scoreboard.getTeams());
            for (Team team : existingTeams) {
                try {
                    team.unregister();
                } catch (Exception e) {
                    // Ignore individual team cleanup errors
                }
            }

            // SIMPLIFIED: Only alignment and admin teams
            // Admin teams (highest priority)
            createTeamSafely(scoreboard, ADMIN_DEV_TEAM, ChatColor.GOLD,
                    ChatColor.GOLD + "⚡ DEV " + ChatColor.GOLD, "");

            createTeamSafely(scoreboard, ADMIN_MANAGER_TEAM, ChatColor.YELLOW,
                    ChatColor.YELLOW + "★ MANAGER " + ChatColor.YELLOW, "");

            createTeamSafely(scoreboard, ADMIN_GM_TEAM, ChatColor.AQUA,
                    ChatColor.AQUA + "♦ GM " + ChatColor.AQUA, "");

            // Alignment teams with colors but NO prefixes (party prefixes added dynamically)
            createTeamSafely(scoreboard, CHAOTIC_TEAM, ChatColor.RED, "", "");
            createTeamSafely(scoreboard, NEUTRAL_TEAM, ChatColor.YELLOW, "", "");
            createTeamSafely(scoreboard, LAWFUL_TEAM, ChatColor.GRAY, "", "");
            createTeamSafely(scoreboard, DEFAULT_TEAM, ChatColor.WHITE, "", "");

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error setting up teams: " + e.getMessage());
        }
    }

    /**
     * Safely create a team with error handling
     */
    private static void createTeamSafely(Scoreboard scoreboard, String name, ChatColor color, String prefix, String suffix) {
        if (scoreboard == null || name == null) return;

        try {
            Team existingTeam = scoreboard.getTeam(name);
            if (existingTeam != null) {
                existingTeam.unregister();
            }

            Team team = scoreboard.registerNewTeam(name);
            if (team != null) {
                team.setColor(color);
                if (prefix != null) team.setPrefix(prefix);
                if (suffix != null) team.setSuffix(suffix);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to create team " + name + ": " + e.getMessage());
        }
    }

    /**
     * Setup health display with proper conflict handling
     */
    private static void setupHealthDisplay(Scoreboard scoreboard) {
        if (scoreboard == null) return;

        try {
            // Clear existing health objective
            Objective existingHealth = scoreboard.getObjective("health");
            if (existingHealth != null) {
                existingHealth.unregister();
            }

            // Create health objective for above-head display
            Objective healthObjective = scoreboard.registerNewObjective(
                    "health", "health", ChatColor.RED + "❤");
            healthObjective.setDisplaySlot(DisplaySlot.BELOW_NAME);

            // Initialize health for all online players immediately
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    if (player != null && player.isOnline()) {
                        int health = (int) Math.ceil(player.getHealth());
                        healthObjective.getScore(player.getName()).setScore(health);
                    }
                } catch (Exception e) {
                    // Skip this player if there's an error
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to setup health display: " + e.getMessage());
        }
    }

    /**
     * Update player scoreboard with simplified logic
     */
    public static void updatePlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics == null) {
            return;
        }

        try {
            Scoreboard scoreboard = getPlayerScoreboard(player);
            if (scoreboard == null) {
                return;
            }

            // Update team assignments first (for name colors)
            updatePlayerTeamAssignments(player, scoreboard, partyMechanics);

            if (partyMechanics.isInParty(player)) {
                // Update party sidebar
                updatePartyObjective(player, scoreboard, partyMechanics);
                updatePartyHealthBar(player, partyMechanics);
            } else {
                // Clear party sidebar
                clearPartyObjective(player, scoreboard);
                clearPartyHealthBar(playerId);
            }

            // Ensure health display is working
            updateHealthDisplay(scoreboard);

            // Apply scoreboard to player
            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }

            lastUpdateTimes.put(playerId, System.currentTimeMillis());

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating scoreboard for " + player.getName() + ": " + e.getMessage());

            // Recovery: clear and recreate
            try {
                cleanupPlayer(player);
            } catch (Exception ex) {
                Bukkit.getLogger().severe("Failed to cleanup after scoreboard error for " + player.getName());
            }
        }
    }

    /**
     * Update team assignments with party role prefixes AND alignment colors
     */
    private static void updatePlayerTeamAssignments(Player viewer, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        if (viewer == null || !viewer.isOnline() || scoreboard == null || partyMechanics == null) {
            return;
        }

        try {
            // Clear all team entries safely
            for (Team team : scoreboard.getTeams()) {
                try {
                    Set<String> entries = new HashSet<>(team.getEntries());
                    for (String entry : entries) {
                        try {
                            team.removeEntry(entry);
                        } catch (Exception e) {
                            // Ignore individual entry removal errors
                        }
                    }
                } catch (Exception e) {
                    // Ignore team clearing errors
                }
            }

            // Get viewer's party members
            List<Player> viewerPartyMembers = null;
            if (partyMechanics.isInParty(viewer)) {
                viewerPartyMembers = partyMechanics.getPartyMembers(viewer);
            }

            // Assign teams for all online players
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target == null || !target.isOnline()) continue;

                try {
                    // Determine team and apply party prefix if needed
                    String teamName = determinePlayerTeam(target);
                    Team team = scoreboard.getTeam(teamName);

                    if (team != null) {
                        // FIXED: Add party role prefix dynamically
                        String partyPrefix = getPartyRolePrefix(target, viewer, viewerPartyMembers, partyMechanics);
                        if (partyPrefix != null && !partyPrefix.isEmpty()) {
                            // Update team prefix to include party role
                            String currentPrefix = team.getPrefix();
                            if (currentPrefix == null) currentPrefix = "";

                            // Only add party prefix if not already an admin
                            if (!currentPrefix.contains("DEV") && !currentPrefix.contains("MANAGER") && !currentPrefix.contains("GM")) {
                                team.setPrefix(partyPrefix);
                            }
                        }

                        team.addEntry(target.getName());
                    }
                } catch (Exception e) {
                    // Skip this player if assignment fails
                }
            }

            viewer.setScoreboard(scoreboard);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating team assignments for " + viewer.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Determine player team based on rank and alignment only
     */
    private static String determinePlayerTeam(Player target) {
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
                        switch (alignment) {
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
            // Fall back to default team on any error
        }

        return DEFAULT_TEAM;
    }

    /**
     * NEW: Get party role prefix for display
     */
    private static String getPartyRolePrefix(Player target, Player viewer, List<Player> viewerPartyMembers, PartyMechanics partyMechanics) {
        if (target == null || viewer == null || partyMechanics == null) {
            return "";
        }

        try {
            // Only show party prefixes if both players are in the same party
            if (viewerPartyMembers != null && viewerPartyMembers.contains(target)) {
                if (partyMechanics.isPartyLeader(target)) {
                    return ChatColor.GOLD + "★" + ChatColor.LIGHT_PURPLE + "[P] ";
                } else if (partyMechanics.isPartyOfficer(target)) {
                    return ChatColor.YELLOW + "♦" + ChatColor.LIGHT_PURPLE + "[P] ";
                } else {
                    return ChatColor.LIGHT_PURPLE + "[P] ";
                }
            }
        } catch (Exception e) {
            // Return empty on error
        }

        return "";
    }

    /**
     * Update party objective with cleaner entry management
     */
    private static void updatePartyObjective(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || scoreboard == null || partyMechanics == null) {
            return;
        }

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
                // Add party size indicator
                String sizeIndicator = ChatColor.GRAY + "Members: " + ChatColor.WHITE + partyMembers.size();
                partyObjective.getScore(sizeIndicator).setScore(15);
                currentEntries.add(sizeIndicator);

                // Add separator
                String separator = " ";
                partyObjective.getScore(separator).setScore(14);
                currentEntries.add(separator);

                // Sort members: leader first, then officers, then regular members
                partyMembers.sort((p1, p2) -> {
                    if (p1 == null || p2 == null) return 0;

                    boolean p1Leader = partyMechanics.isPartyLeader(p1);
                    boolean p2Leader = partyMechanics.isPartyLeader(p2);
                    boolean p1Officer = partyMechanics.isPartyOfficer(p1);
                    boolean p2Officer = partyMechanics.isPartyOfficer(p2);

                    if (p1Leader && !p2Leader) return -1;
                    if (!p1Leader && p2Leader) return 1;
                    if (p1Officer && !p2Officer) return -1;
                    if (!p1Officer && p2Officer) return 1;
                    return p1.getName().compareTo(p2.getName());
                });

                int scoreIndex = 13;
                Set<String> usedNames = new HashSet<>();

                for (Player member : partyMembers) {
                    if (member == null || !member.isOnline()) continue;

                    String displayName = formatPartyMemberName(member, partyMechanics, scoreIndex, usedNames);
                    if (displayName == null) continue;

                    int health = (int) Math.ceil(member.getHealth());
                    partyObjective.getScore(displayName).setScore(health);
                    currentEntries.add(displayName);
                    usedNames.add(displayName);
                    scoreIndex--;

                    if (scoreIndex < 0) break;
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating party objective for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Format party member name with role and health indicators
     */
    private static String formatPartyMemberName(Player member, PartyMechanics partyMechanics, int index, Set<String> usedNames) {
        if (member == null || !member.isOnline() || partyMechanics == null) {
            return null;
        }

        try {
            StringBuilder nameBuilder = new StringBuilder();

            // Add role indicator
            if (partyMechanics.isPartyLeader(member)) {
                nameBuilder.append(ChatColor.GOLD).append("★ ");
            } else if (partyMechanics.isPartyOfficer(member)) {
                nameBuilder.append(ChatColor.YELLOW).append("♦ ");
            } else {
                nameBuilder.append(ChatColor.GRAY).append("• ");
            }

            // Add player name with alignment color
            ChatColor nameColor = getPlayerDisplayColor(member);
            nameBuilder.append(nameColor).append(member.getName());

            // Add health status indicator
            double healthPercentage = member.getHealth() / member.getMaxHealth();
            if (healthPercentage <= 0.3) {
                nameBuilder.append(ChatColor.RED).append(" ♥");
            } else if (healthPercentage >= 1.0) {
                nameBuilder.append(ChatColor.GREEN).append(" ♥");
            } else if (healthPercentage <= 0.6) {
                nameBuilder.append(ChatColor.YELLOW).append(" ♥");
            }

            String result = nameBuilder.toString();

            // Ensure uniqueness
            String finalName = result;
            int suffix = 0;
            while (usedNames.contains(finalName)) {
                finalName = result + ChatColor.RESET + "" + ChatColor.BLACK + suffix;
                suffix++;
            }

            // Truncate if too long
            if (finalName.length() > 35) {
                finalName = finalName.substring(0, 32) + "...";
            }

            return finalName;
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error formatting party member name for " + member.getName() + ": " + e.getMessage());
            return member.getName();
        }
    }

    /**
     * Get appropriate display color for a player
     */
    private static ChatColor getPlayerDisplayColor(Player player) {
        if (player == null) {
            return ChatColor.WHITE;
        }

        try {
            // Check rank first for admins
            Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
            if (rank != null && rank != Rank.DEFAULT) {
                switch (rank) {
                    case DEV:
                        return ChatColor.GOLD;
                    case MANAGER:
                        return ChatColor.YELLOW;
                    case GM:
                        return ChatColor.AQUA;
                    default:
                        return ChatColor.WHITE;
                }
            }

            // Get alignment color
            YakPlayerManager playerManager = YakPlayerManager.getInstance();
            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    String alignment = yakPlayer.getAlignment();
                    if (alignment != null) {
                        switch (alignment) {
                            case "CHAOTIC":
                                return ChatColor.RED;
                            case "NEUTRAL":
                                return ChatColor.YELLOW;
                            case "LAWFUL":
                                return ChatColor.GRAY;
                            default:
                                return ChatColor.WHITE;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to white on any error
        }

        return ChatColor.WHITE;
    }

    /**
     * Clear party objective properly
     */
    private static void clearPartyObjective(Player player, Scoreboard scoreboard) {
        if (player == null || scoreboard == null) return;

        UUID playerId = player.getUniqueId();

        try {
            // Remove sidebar objective
            Objective sidebarObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (sidebarObjective != null) {
                sidebarObjective.unregister();
            }

            // Clear tracked entries
            Set<String> currentEntries = activeScoreboardEntries.get(playerId);
            if (currentEntries != null) {
                currentEntries.clear();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error clearing party objective for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Update health display to work properly
     */
    private static void updateHealthDisplay(Scoreboard scoreboard) {
        if (scoreboard == null) return;

        try {
            Objective healthObjective = scoreboard.getObjective("health");
            if (healthObjective == null) {
                setupHealthDisplay(scoreboard);
                healthObjective = scoreboard.getObjective("health");
            }

            if (healthObjective != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player == null || !player.isOnline()) continue;

                    try {
                        int health = (int) Math.ceil(player.getHealth());
                        Score score = healthObjective.getScore(player.getName());
                        score.setScore(health);
                    } catch (Exception e) {
                        // Skip this player
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating health display: " + e.getMessage());
        }
    }

    /**
     * Update party health bar
     */
    private static void updatePartyHealthBar(Player player, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || partyMechanics == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers == null || partyMembers.isEmpty()) {
                clearPartyHealthBar(playerId);
                return;
            }

            // Calculate party health statistics
            double totalHealth = 0;
            double maxTotalHealth = 0;
            int aliveMembersCount = 0;

            for (Player member : partyMembers) {
                if (member != null && member.isOnline()) {
                    totalHealth += member.getHealth();
                    maxTotalHealth += member.getMaxHealth();
                    if (member.getHealth() > 0) aliveMembersCount++;
                }
            }

            if (maxTotalHealth == 0) return;

            double healthPercentage = totalHealth / maxTotalHealth;

            // Create title
            String title = ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + "PARTY" +
                    ChatColor.LIGHT_PURPLE + " (" + aliveMembersCount + "/" + partyMembers.size() +
                    ") " + ChatColor.WHITE + (int)totalHealth + "/" + (int)maxTotalHealth + " ❤";

            BarColor barColor = getPartyHealthBarColor(healthPercentage, aliveMembersCount, partyMembers.size());

            BossBar bossBar = partyHealthBars.get(playerId);
            if (bossBar == null) {
                bossBar = Bukkit.createBossBar(title, barColor, BarStyle.SEGMENTED_10);
                bossBar.addPlayer(player);
                partyHealthBars.put(playerId, bossBar);
            }

            bossBar.setColor(barColor);
            bossBar.setTitle(title);
            bossBar.setProgress(Math.max(0.0, Math.min(1.0, healthPercentage)));
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating party health bar for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Clear party health bar
     */
    private static void clearPartyHealthBar(UUID playerId) {
        try {
            BossBar bossBar = partyHealthBars.remove(playerId);
            if (bossBar != null) {
                bossBar.removeAll();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Determine boss bar color based on party health status
     */
    private static BarColor getPartyHealthBarColor(double healthPercentage, int aliveCount, int totalCount) {
        // Critical if someone is dead
        if (aliveCount < totalCount) {
            return BarColor.RED;
        }

        // Color based on overall health
        if (healthPercentage > 0.75) return BarColor.GREEN;
        if (healthPercentage > 0.5) return BarColor.YELLOW;
        if (healthPercentage > 0.25) return BarColor.RED;
        return BarColor.PURPLE; // Critical health
    }

    /**
     * SIMPLIFIED: Update all player scoreboards
     */
    public static void refreshAllPartyScoreboards() {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player != null && player.isOnline()) {
                    updatePlayerScoreboard(player);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error refreshing scoreboard for " +
                        (player != null ? player.getName() : "unknown") + ": " + e.getMessage());
            }
        }
    }

    /**
     * Force refresh player scoreboard
     */
    public static void refreshPlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Clear cache and force update
        lastUpdateTimes.remove(player.getUniqueId());
        updatePlayerScoreboard(player);
    }

    /**
     * Clear a player's party scoreboard
     */
    public static void clearPlayerScoreboard(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            Scoreboard scoreboard = getPlayerScoreboard(player);
            if (scoreboard != null) {
                clearPartyObjective(player, scoreboard);
                clearPartyHealthBar(playerId);

                // Reset to main scoreboard
                ScoreboardManager manager = Bukkit.getScoreboardManager();
                if (manager != null && player.isOnline()) {
                    Scoreboard mainScoreboard = manager.getMainScoreboard();
                    if (mainScoreboard != null) {
                        player.setScoreboard(mainScoreboard);
                    }
                }
            }

            // Show leave effect
            if (player.isOnline()) {
                PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                        playerId, k -> new PartyVisualEffects(player));
                visuals.showPartyLeaveEffect();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error clearing party scoreboard for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Show party join effects
     */
    public static void showPartyJoinEffects(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                    player.getUniqueId(), k -> new PartyVisualEffects(player));
            visuals.showPartyJoinEffect();
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    /**
     * Show party leader promotion effects
     */
    public static void showLeaderPromotionEffects(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                    player.getUniqueId(), k -> new PartyVisualEffects(player));
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    /**
     * SIMPLIFIED: Update health for all players
     */
    public static void updateAllPlayerHealth() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) continue;

            try {
                Scoreboard scoreboard = viewer.getScoreboard();
                if (scoreboard != null) {
                    updateHealthDisplay(scoreboard);
                }
            } catch (Exception e) {
                // Continue with other players
            }
        }
    }

    /**
     * SIMPLIFIED: Update all player colors and team assignments
     */
    public static void updateAllPlayerColors() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) continue;

            try {
                updatePlayerScoreboard(viewer);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error updating colors for " + viewer.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Clean up resources for a player
     */
    public static void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            playerScoreboards.remove(playerId);
            lastUpdateTimes.remove(playerId);
            partyVisuals.remove(playerId);
            activeScoreboardEntries.remove(playerId);
            clearPartyHealthBar(playerId);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error cleaning up player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Clean up all resources
     */
    public static void cleanupAll() {
        try {
            for (BossBar bossBar : partyHealthBars.values()) {
                try {
                    bossBar.removeAll();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            playerScoreboards.clear();
            partyHealthBars.clear();
            lastUpdateTimes.clear();
            partyVisuals.clear();
            activeScoreboardEntries.clear();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error cleaning up all party scoreboards: " + e.getMessage());
        }
    }

    /**
     * Handle alignment changes properly
     */
    public static void handleAlignmentChange(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            // Force refresh for the changed player
            refreshPlayerScoreboard(player);

            // Update all other players' views of this player
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer != null && onlinePlayer.isOnline() && !onlinePlayer.equals(player)) {
                    try {
                        refreshPlayerScoreboard(onlinePlayer);
                    } catch (Exception e) {
                        // Continue with other players
                    }
                }
            }

            // Schedule a delayed update to ensure all changes are applied
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    updateAllPlayerColors();
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Error in delayed alignment update: " + e.getMessage());
                }
            }, 2L);

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error handling alignment change for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Force refresh all scoreboards
     */
    public static void forceRefreshAll() {
        try {
            // Clear all caches
            lastUpdateTimes.clear();

            // Update all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null || !player.isOnline()) continue;

                try {
                    updatePlayerScoreboard(player);
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Error force refreshing scoreboard for " + player.getName() + ": " + e.getMessage());
                }
            }

            // Update health displays
            updateAllPlayerHealth();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in force refresh all: " + e.getMessage());
        }
    }

    /**
     * Validate and repair scoreboard integrity
     */
    public static void validateAndRepairScoreboards() {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            try {
                Scoreboard scoreboard = player.getScoreboard();

                // Check if player's scoreboard needs repair
                if (scoreboard == null || scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
                    updatePlayerScoreboard(player);
                } else {
                    // Validate health objective
                    Objective healthObj = scoreboard.getObjective("health");
                    if (healthObj == null) {
                        setupHealthDisplay(scoreboard);
                        player.setScoreboard(scoreboard);
                    }
                }

            } catch (Exception e) {
                Bukkit.getLogger().warning("Error validating scoreboard for " + player.getName() + ": " + e.getMessage());

                try {
                    cleanupPlayer(player);
                    updatePlayerScoreboard(player);
                } catch (Exception ex) {
                    Bukkit.getLogger().severe("Failed to reset scoreboard for " + player.getName() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Get party statistics for debugging
     */
    public static Map<String, Object> getPartyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active_scoreboards", playerScoreboards.size());
        stats.put("active_health_bars", partyHealthBars.size());
        stats.put("cached_visuals", partyVisuals.size());
        stats.put("tracked_updates", lastUpdateTimes.size());
        stats.put("active_entries", activeScoreboardEntries.size());
        return stats;
    }
}
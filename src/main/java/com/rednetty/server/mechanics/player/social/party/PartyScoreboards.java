package com.rednetty.server.mechanics.player.social.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
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
 * FIXED: Enhanced party scoreboards with proper team handling and null safety
 * - Fixed immutable collection clear() error
 * - Added comprehensive null checking
 * - Improved team entry management
 * - Enhanced error handling and recovery
 */
public class PartyScoreboards {
    private static final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> partyHealthBars = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    // Track entries and player colors to detect alignment changes
    private static final Map<UUID, Set<String>> activeScoreboardEntries = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, ChatColor>> playerLastColors = new ConcurrentHashMap<>();

    // Enhanced visual system for party management
    private static final Map<UUID, PartyVisualEffects> partyVisuals = new ConcurrentHashMap<>();

    // Update throttling to prevent spam
    private static final long UPDATE_COOLDOWN = 100; // Increased to reduce spam
    private static final long FORCE_REFRESH_INTERVAL = 5000; // Force full refresh every 5 seconds

    // Team name constants for party members
    private static final String PARTY_LEADER_TEAM = "party_leader";
    private static final String PARTY_OFFICER_TEAM = "party_officer";
    private static final String PARTY_MEMBER_TEAM = "party_member";

    // Alignment team constants
    private static final String CHAOTIC_TEAM = "chaotic";
    private static final String NEUTRAL_TEAM = "neutral";
    private static final String LAWFUL_TEAM = "lawful";
    private static final String DEFAULT_TEAM = "default";

    /**
     * Enhanced visual effects system for party interactions
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
     * FIXED: Get or create a player's enhanced scoreboard with proper error handling
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

            // Enhanced team setup with modern styling
            setupEnhancedTeams(scoreboard);
            setupHealthDisplay(scoreboard);

            playerScoreboards.put(playerId, scoreboard);
            activeScoreboardEntries.put(playerId, ConcurrentHashMap.newKeySet());
            playerLastColors.put(playerId, new ConcurrentHashMap<>());

            return scoreboard;
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error creating scoreboard for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * FIXED: Setup enhanced teams with proper error handling
     */
    private static void setupEnhancedTeams(Scoreboard scoreboard) {
        if (scoreboard == null) return;

        try {
            // Clear any existing teams first - SAFELY
            Set<Team> existingTeams = new HashSet<>(scoreboard.getTeams());
            for (Team team : existingTeams) {
                try {
                    team.unregister();
                } catch (Exception e) {
                    // Ignore individual team cleanup errors
                }
            }

            // Party member teams with special visibility for party members only
            createTeamSafely(scoreboard, PARTY_LEADER_TEAM, ChatColor.GOLD,
                    ChatColor.GOLD + "★" + ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.GOLD,
                    ChatColor.GOLD + " ★");

            createTeamSafely(scoreboard, PARTY_OFFICER_TEAM, ChatColor.YELLOW,
                    ChatColor.YELLOW + "♦" + ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.YELLOW,
                    ChatColor.YELLOW + " ♦");

            createTeamSafely(scoreboard, PARTY_MEMBER_TEAM, ChatColor.AQUA,
                    ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.AQUA, "");

            // Admin teams with enhanced styling
            createTeamSafely(scoreboard, "dev", ChatColor.GOLD,
                    ChatColor.GOLD + "⚡ DEV " + ChatColor.GOLD,
                    ChatColor.GOLD + " ⚡");

            createTeamSafely(scoreboard, "manager", ChatColor.YELLOW,
                    ChatColor.YELLOW + "★ MANAGER " + ChatColor.YELLOW,
                    ChatColor.YELLOW + " ★");

            createTeamSafely(scoreboard, "gm", ChatColor.AQUA,
                    ChatColor.AQUA + "♦ GM " + ChatColor.AQUA,
                    ChatColor.AQUA + " ♦");

            // Alignment teams with proper colors for above-head display
            createTeamSafely(scoreboard, CHAOTIC_TEAM, ChatColor.RED, "", "");
            createTeamSafely(scoreboard, NEUTRAL_TEAM, ChatColor.YELLOW, "", "");
            createTeamSafely(scoreboard, LAWFUL_TEAM, ChatColor.GRAY, "", "");
            createTeamSafely(scoreboard, DEFAULT_TEAM, ChatColor.WHITE, "", "");

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error setting up enhanced teams: " + e.getMessage());
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
     * FIXED: Setup health display using BELOW_NAME for above-head display
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

            // Initialize health for all online players
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
     * FIXED: Update a player's scoreboard with comprehensive error handling
     */
    public static void updatePlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTimes.get(playerId);

        // Check if any party member's alignment has changed
        boolean alignmentChanged = hasAnyAlignmentChanged(player);
        boolean forceRefresh = (lastUpdate == null ||
                currentTime - lastUpdate > FORCE_REFRESH_INTERVAL ||
                alignmentChanged);

        // Throttle updates unless forced or alignment changed
        if (!forceRefresh && lastUpdate != null && currentTime - lastUpdate < UPDATE_COOLDOWN) {
            return;
        }
        lastUpdateTimes.put(playerId, currentTime);

        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics == null) {
            return;
        }

        if (!partyMechanics.isInParty(player)) {
            clearPlayerScoreboard(player);
            return;
        }

        try {
            Scoreboard scoreboard = getPlayerScoreboard(player);
            if (scoreboard == null) {
                return;
            }

            // Force complete refresh if alignment changed
            if (alignmentChanged || forceRefresh) {
                forceCompleteRefresh(player, scoreboard, partyMechanics);
            } else {
                updatePartyObjective(player, scoreboard, partyMechanics);
            }

            updatePartyHealthBar(player, partyMechanics);

            // Ensure player gets the updated scoreboard
            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating scoreboard for " + player.getName() + ": " + e.getMessage());

            // Try to recover by clearing and recreating
            try {
                cleanupPlayer(player);
                // Don't immediately recreate to avoid infinite loops
            } catch (Exception ex) {
                Bukkit.getLogger().severe("Failed to cleanup after scoreboard error for " + player.getName());
            }
        }
    }

    /**
     * Check if any party member's alignment has changed since last update
     */
    private static boolean hasAnyAlignmentChanged(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return false;
        }

        UUID viewerId = viewer.getUniqueId();
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        if (partyMechanics == null || !partyMechanics.isInParty(viewer)) {
            return false;
        }

        try {
            List<Player> partyMembers = partyMechanics.getPartyMembers(viewer);
            if (partyMembers == null) {
                return false;
            }

            Map<String, ChatColor> lastColors = playerLastColors.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());

            for (Player member : partyMembers) {
                if (member == null || !member.isOnline()) continue;

                ChatColor currentColor = getPlayerDisplayColor(member);
                ChatColor lastColor = lastColors.get(member.getName());

                if (lastColor == null || !lastColor.equals(currentColor)) {
                    // Color changed - update stored color and return true
                    lastColors.put(member.getName(), currentColor);
                    return true;
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error checking alignment changes for " + viewer.getName() + ": " + e.getMessage());
        }

        return false;
    }

    /**
     * Force complete refresh when alignment changes are detected
     */
    private static void forceCompleteRefresh(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || scoreboard == null || partyMechanics == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            // Get current active entries
            Set<String> currentEntries = activeScoreboardEntries.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

            // Clear ALL existing entries completely
            for (String entry : new HashSet<>(currentEntries)) {
                try {
                    scoreboard.resetScores(entry);
                } catch (Exception e) {
                    // Ignore errors when cleaning up
                }
            }
            currentEntries.clear();

            // Remove and recreate sidebar objective
            Objective existing = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (existing != null) {
                existing.unregister();
            }

            // Recreate the objective fresh
            updatePartyObjective(player, scoreboard, partyMechanics);

            // Update team assignments for all players
            updatePlayerTeamAssignments(player);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in force complete refresh for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * FIXED: Update the party objective with proper cleanup and unique entry names
     */
    private static void updatePartyObjective(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || scoreboard == null || partyMechanics == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            // Get current active entries for this player
            Set<String> currentEntries = activeScoreboardEntries.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

            // Remove existing sidebar objective if it exists
            Objective existing = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (existing != null) {
                existing.unregister();
            }

            // Create enhanced party objective
            Objective partyObjective = scoreboard.registerNewObjective(
                    "party_data", "dummy",
                    ChatColor.LIGHT_PURPLE + "✦ " + ChatColor.BOLD + "PARTY" + ChatColor.LIGHT_PURPLE + " ✦"
            );
            partyObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers != null && !partyMembers.isEmpty()) {

                // Add party size indicator at the top
                String sizeIndicator = ChatColor.GRAY + "Members: " + ChatColor.WHITE + partyMembers.size();
                partyObjective.getScore(sizeIndicator).setScore(15);
                currentEntries.add(sizeIndicator);

                // Add separator - make it unique with invisible characters
                String separator = " " + ChatColor.RESET;
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

                    // Create truly unique display name using current alignment colors
                    String displayName = formatPartyMemberName(member, partyMechanics, scoreIndex, usedNames);
                    if (displayName == null) continue;

                    int health = (int) Math.ceil(member.getHealth());

                    partyObjective.getScore(displayName).setScore(health);
                    currentEntries.add(displayName);
                    usedNames.add(displayName);
                    scoreIndex--;

                    // Prevent going below 0
                    if (scoreIndex < 0) break;
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating party objective for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Format party member name with current alignment colors and guaranteed uniqueness
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

            // Add player name with CURRENT alignment color
            ChatColor nameColor = getPlayerDisplayColor(member);
            nameBuilder.append(nameColor).append(member.getName());

            // Add health status indicator
            double healthPercentage = member.getHealth() / member.getMaxHealth();
            if (healthPercentage <= 0.3) { // Low health (30% or less)
                nameBuilder.append(ChatColor.RED).append(" ♥");
            } else if (healthPercentage >= 1.0) { // Full health
                nameBuilder.append(ChatColor.GREEN).append(" ♥");
            } else if (healthPercentage <= 0.6) { // Medium health
                nameBuilder.append(ChatColor.YELLOW).append(" ♥");
            }

            String result = nameBuilder.toString();

            // Ensure complete uniqueness by adding index if name is used
            String finalName = result;
            int suffix = 0;
            while (usedNames.contains(finalName)) {
                finalName = result + ChatColor.RESET + "" + ChatColor.BLACK + suffix;
                suffix++;
            }

            // Truncate if too long for scoreboard (max 40 characters)
            if (finalName.length() > 35) {
                finalName = finalName.substring(0, 32) + "...";
            }

            return finalName;
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error formatting party member name for " + member.getName() + ": " + e.getMessage());
            return member.getName(); // Fallback to simple name
        }
    }

    /**
     * Get appropriate display color for a player using CURRENT alignment
     */
    private static ChatColor getPlayerDisplayColor(Player player) {
        if (player == null) {
            return ChatColor.WHITE;
        }

        try {
            // Check rank first for admins
            Rank rank = ModerationMechanics.getRank(player);
            if (rank != null && rank != Rank.DEFAULT) {
                return switch (rank) {
                    case DEV -> ChatColor.GOLD;
                    case MANAGER -> ChatColor.YELLOW;
                    case GM -> ChatColor.AQUA;
                    default -> ChatColor.WHITE;
                };
            }

            // Get CURRENT alignment color
            YakPlayerManager playerManager = YakPlayerManager.getInstance();
            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    String alignment = yakPlayer.getAlignment();
                    if (alignment != null) {
                        return switch (alignment) {
                            case "CHAOTIC" -> ChatColor.RED;
                            case "NEUTRAL" -> ChatColor.YELLOW;
                            case "LAWFUL" -> ChatColor.GRAY;
                            default -> ChatColor.WHITE;
                        };
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to white on any error
        }

        return ChatColor.WHITE;
    }

    /**
     * Enhanced party health bar system using boss bars
     */
    private static void updatePartyHealthBar(Player player, PartyMechanics partyMechanics) {
        if (player == null || !player.isOnline() || partyMechanics == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            if (!partyMechanics.isInParty(player)) {
                BossBar existingBar = partyHealthBars.remove(playerId);
                if (existingBar != null) {
                    existingBar.removeAll();
                }
                return;
            }

            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers == null || partyMembers.isEmpty()) return;

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

            // Create enhanced title
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
     * FIXED: Update team assignments with proper error handling and safe team entry management
     */
    public static void updatePlayerTeamAssignments(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        try {
            Scoreboard scoreboard = getPlayerScoreboard(viewer);
            if (scoreboard == null) {
                return;
            }

            PartyMechanics partyMechanics = PartyMechanics.getInstance();
            if (partyMechanics == null) {
                return;
            }

            
            for (Team team : scoreboard.getTeams()) {
                try {
                    // Create a copy of entries to avoid concurrent modification
                    Set<String> entries = new HashSet<>(team.getEntries());
                    for (String entry : entries) {
                        try {
                            team.removeEntry(entry); // Use removeEntry instead of clear()
                        } catch (Exception e) {
                            // Ignore individual entry removal errors
                        }
                    }
                } catch (Exception e) {
                    // Ignore team clearing errors and continue
                }
            }

            // Get viewer's party info
            List<Player> viewerPartyMembers = null;
            if (partyMechanics.isInParty(viewer)) {
                viewerPartyMembers = partyMechanics.getPartyMembers(viewer);
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                if (target == null || !target.isOnline()) continue;

                try {
                    String teamName = determinePlayerTeam(target, viewer, viewerPartyMembers, partyMechanics);
                    Team team = scoreboard.getTeam(teamName);
                    if (team != null) {
                        team.addEntry(target.getName());
                    }
                } catch (Exception e) {
                    // Skip this player's team assignment if it fails
                }
            }

            viewer.setScoreboard(scoreboard);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating team assignments for " + viewer.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Determine which team a player should be assigned to with current alignment
     */
    private static String determinePlayerTeam(Player target, Player viewer, List<Player> viewerPartyMembers, PartyMechanics partyMechanics) {
        if (target == null || viewer == null || partyMechanics == null) {
            return DEFAULT_TEAM;
        }

        try {
            // If viewer is in a party and target is in the same party, use party teams
            if (viewerPartyMembers != null && viewerPartyMembers.contains(target)) {
                if (partyMechanics.isPartyLeader(target)) {
                    return PARTY_LEADER_TEAM;
                } else if (partyMechanics.isPartyOfficer(target)) {
                    return PARTY_OFFICER_TEAM;
                } else {
                    return PARTY_MEMBER_TEAM;
                }
            }

            // Check rank for non-party members or when viewer is not in party
            Rank rank = ModerationMechanics.getRank(target);
            if (rank != null) {
                if (rank == Rank.DEV) {
                    return "dev";
                } else if (rank == Rank.MANAGER) {
                    return "manager";
                } else if (rank == Rank.GM) {
                    return "gm";
                }
            }

            // Check CURRENT alignment for regular players
            YakPlayerManager playerManager = YakPlayerManager.getInstance();
            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(target);
                if (yakPlayer != null) {
                    String alignment = yakPlayer.getAlignment();
                    if (alignment != null) {
                        return switch (alignment) {
                            case "CHAOTIC" -> CHAOTIC_TEAM;
                            case "NEUTRAL" -> NEUTRAL_TEAM;
                            case "LAWFUL" -> LAWFUL_TEAM;
                            default -> DEFAULT_TEAM;
                        };
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to default team on any error
        }

        return DEFAULT_TEAM;
    }

    /**
     * Refresh all party scoreboards efficiently
     */
    public static void refreshAllPartyScoreboards() {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player != null && player.isOnline() && partyMechanics.isInParty(player)) {
                    refreshPlayerScoreboard(player);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error refreshing party scoreboard for " +
                        (player != null ? player.getName() : "unknown") + ": " + e.getMessage());
            }
        }
    }

    /**
     * Refresh player scoreboard with alignment change detection
     */
    public static void refreshPlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Force update by clearing last update time
        lastUpdateTimes.remove(player.getUniqueId());
        updatePlayerScoreboard(player);
    }

    /**
     * FIXED: Clear a player's party scoreboard with proper cleanup
     */
    public static void clearPlayerScoreboard(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            Scoreboard scoreboard = getPlayerScoreboard(player);
            if (scoreboard != null) {
                // Clear all active entries
                Set<String> currentEntries = activeScoreboardEntries.get(playerId);
                if (currentEntries != null) {
                    for (String entry : new HashSet<>(currentEntries)) {
                        try {
                            scoreboard.resetScores(entry);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    }
                    currentEntries.clear();
                }

                // Remove sidebar objective
                Objective sidebarObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
                if (sidebarObjective != null) {
                    sidebarObjective.unregister();
                }
            }

            // Remove party health bar
            BossBar bossBar = partyHealthBars.remove(playerId);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            // Show leave effect
            if (player.isOnline()) {
                PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                        playerId, k -> new PartyVisualEffects(player));

                if (scoreboard != null) {
                    player.setScoreboard(scoreboard);
                }
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
            visuals.showLeaderPromotionEffect();
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    /**
     * Update health display for all players with alignment awareness
     */
    public static void updateAllPlayerHealth() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) continue;

            try {
                Scoreboard scoreboard = getPlayerScoreboard(viewer);
                if (scoreboard == null) continue;

                Objective healthObjective = scoreboard.getObjective("health");
                if (healthObjective != null) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (target == null || !target.isOnline()) continue;

                        try {
                            int health = (int) Math.ceil(target.getHealth());
                            Score score = healthObjective.getScore(target.getName());
                            if (score.getScore() != health) {
                                score.setScore(health);
                            }
                        } catch (Exception e) {
                            // Skip this player's health update if it fails
                        }
                    }
                }

                // Ensure the viewer has the updated scoreboard
                if (viewer.getScoreboard() != scoreboard) {
                    viewer.setScoreboard(scoreboard);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error updating health display for " + viewer.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Update color teams for all players with enhanced team management
     */
    public static void updateAllPlayerColors() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) continue;

            try {
                updatePlayerTeamAssignments(viewer);
                // Force scoreboard refresh to pick up color changes
                refreshPlayerScoreboard(viewer);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error updating team assignments for " + viewer.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Clean up resources for a player with complete cleanup
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

            // Clean up tracking maps
            activeScoreboardEntries.remove(playerId);
            playerLastColors.remove(playerId);

            BossBar bossBar = partyHealthBars.remove(playerId);
            if (bossBar != null) {
                bossBar.removeAll();
            }
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
            playerLastColors.clear();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error cleaning up all party scoreboards: " + e.getMessage());
        }
    }

    /**
     * Force refresh all scoreboards with alignment updates
     */
    public static void forceRefreshAll() {
        try {
            // Clear all cached data
            lastUpdateTimes.clear();
            playerLastColors.clear();

            // Update all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null || !player.isOnline()) continue;

                try {
                    PartyMechanics partyMechanics = PartyMechanics.getInstance();
                    if (partyMechanics == null) continue;

                    if (partyMechanics.isInParty(player)) {
                        updatePlayerScoreboard(player);
                    } else {
                        clearPlayerScoreboard(player);
                    }

                    updatePlayerTeamAssignments(player);
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
     * FIXED: Public method to handle alignment changes
     * Call this whenever a player's alignment changes
     * Now updates ALL players' team assignments, not just party members
     */
    public static void handleAlignmentChange(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            // Clear color cache to force refresh
            Map<String, ChatColor> playerColors = playerLastColors.get(playerId);
            if (playerColors != null) {
                playerColors.clear();
            }

            // Clear last update time to force refresh
            lastUpdateTimes.remove(playerId);

            // Update team assignments and scoreboard for the changed player
            updatePlayerTeamAssignments(player);
            updatePlayerScoreboard(player);

            
            // This is crucial because name colors above heads are controlled by team assignments
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer != null && onlinePlayer.isOnline() && !onlinePlayer.equals(player)) {
                    try {
                        // Update this player's team assignments so they see the correct color for the changed player
                        updatePlayerTeamAssignments(onlinePlayer);

                        // If this player is in a party, also update their scoreboard
                        PartyMechanics partyMechanics = PartyMechanics.getInstance();
                        if (partyMechanics != null && partyMechanics.isInParty(onlinePlayer)) {
                            // Check if the changed player is in their party
                            List<Player> partyMembers = partyMechanics.getPartyMembers(onlinePlayer);
                            if (partyMembers != null && partyMembers.contains(player)) {
                                // Clear their cache since they see this player in their party
                                Map<String, ChatColor> memberColors = playerLastColors.get(onlinePlayer.getUniqueId());
                                if (memberColors != null) {
                                    memberColors.clear();
                                }
                                lastUpdateTimes.remove(onlinePlayer.getUniqueId());
                                updatePlayerScoreboard(onlinePlayer);
                            }
                        }
                    } catch (Exception e) {
                        // Continue with other players if one fails
                        Bukkit.getLogger().fine("Error updating team assignments for " + onlinePlayer.getName() + " during alignment change: " + e.getMessage());
                    }
                }
            }

            
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    updateAllPlayerColors();
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Error in delayed color update after alignment change: " + e.getMessage());
                }
            }, 2L); // Small delay to ensure all updates are processed

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error handling alignment change for " + player.getName() + ": " + e.getMessage());
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
        stats.put("player_colors", playerLastColors.size());
        return stats;
    }

    /**
     * Validate and repair scoreboard integrity with alignment support
     */
    public static void validateAndRepairScoreboards() {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;

            try {
                Scoreboard scoreboard = player.getScoreboard();

                // Check if player's scoreboard is corrupted or missing
                if (scoreboard == null || scoreboard == Bukkit.getScoreboardManager().getMainScoreboard()) {
                    // Reset to proper party scoreboard
                    if (partyMechanics.isInParty(player)) {
                        updatePlayerScoreboard(player);
                    }
                    updatePlayerTeamAssignments(player);
                }

                // Validate health objective
                Objective healthObj = scoreboard.getObjective("health");
                if (healthObj == null) {
                    setupHealthDisplay(scoreboard);
                    player.setScoreboard(scoreboard);
                }

            } catch (Exception e) {
                Bukkit.getLogger().warning("Error validating scoreboard for " + player.getName() + ": " + e.getMessage());

                // Try to reset the player's scoreboard completely
                try {
                    cleanupPlayer(player);
                    if (partyMechanics.isInParty(player)) {
                        updatePlayerScoreboard(player);
                    }
                    updatePlayerTeamAssignments(player);
                } catch (Exception ex) {
                    Bukkit.getLogger().severe("Failed to reset scoreboard for " + player.getName() + ": " + ex.getMessage());
                }
            }
        }
    }
}
package com.rednetty.server.mechanics.party;

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
 * FIXED: Enhanced party scoreboards with proper alignment change handling
 * - Fixed name duplication when alignment changes
 * - Proper cleanup of old colored entries
 * - Synchronized team updates with scoreboard refreshes
 * - Improved name uniqueness system
 */
public class PartyScoreboards {
    private static final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> partyHealthBars = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    // FIXED: Track both entries and player colors to detect alignment changes
    private static final Map<UUID, Set<String>> activeScoreboardEntries = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, ChatColor>> playerLastColors = new ConcurrentHashMap<>();

    // Enhanced visual system for party management
    private static final Map<UUID, PartyVisualEffects> partyVisuals = new ConcurrentHashMap<>();

    // Update throttling to prevent spam
    private static final long UPDATE_COOLDOWN = 50; // Reduced for more responsive updates
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
            if (!player.isOnline()) return;

            player.sendTitle(
                    ChatColor.LIGHT_PURPLE + "✦ PARTY JOINED ✦",
                    ChatColor.GRAY + "You are now part of a team",
                    10, 40, 10
            );

            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }

        public void showPartyLeaveEffect() {
            if (!player.isOnline()) return;

            player.sendTitle(
                    ChatColor.RED + "✦ PARTY LEFT ✦",
                    ChatColor.GRAY + "You are no longer in a party",
                    10, 30, 10
            );

            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
        }

        public void showLeaderPromotionEffect() {
            if (!player.isOnline()) return;

            player.sendTitle(
                    ChatColor.GOLD + "★ PARTY LEADER ★",
                    ChatColor.YELLOW + "You are now the party leader",
                    10, 50, 10
            );

            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    /**
     * FIXED: Get or create a player's enhanced scoreboard with proper cleanup
     */
    public static Scoreboard getPlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        Scoreboard existingScoreboard = playerScoreboards.get(playerId);
        if (existingScoreboard != null) {
            return existingScoreboard;
        }

        // Create new scoreboard
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();

        // Enhanced team setup with modern styling
        setupEnhancedTeams(scoreboard);
        setupHealthDisplay(scoreboard);

        playerScoreboards.put(playerId, scoreboard);
        activeScoreboardEntries.put(playerId, ConcurrentHashMap.newKeySet());
        playerLastColors.put(playerId, new ConcurrentHashMap<>());

        return scoreboard;
    }

    /**
     * FIXED: Setup enhanced teams with proper alignment colors
     */
    private static void setupEnhancedTeams(Scoreboard scoreboard) {
        // Clear any existing teams first
        for (Team team : new HashSet<>(scoreboard.getTeams())) {
            team.unregister();
        }

        // Party member teams with special visibility for party members only
        Team partyLeaderTeam = scoreboard.registerNewTeam(PARTY_LEADER_TEAM);
        partyLeaderTeam.setColor(ChatColor.GOLD);
        partyLeaderTeam.setPrefix(ChatColor.GOLD + "★" + ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.GOLD);
        partyLeaderTeam.setSuffix(ChatColor.GOLD + " ★");

        Team partyOfficerTeam = scoreboard.registerNewTeam(PARTY_OFFICER_TEAM);
        partyOfficerTeam.setColor(ChatColor.YELLOW);
        partyOfficerTeam.setPrefix(ChatColor.YELLOW + "♦" + ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.YELLOW);
        partyOfficerTeam.setSuffix(ChatColor.YELLOW + " ♦");

        Team partyMemberTeam = scoreboard.registerNewTeam(PARTY_MEMBER_TEAM);
        partyMemberTeam.setColor(ChatColor.AQUA);
        partyMemberTeam.setPrefix(ChatColor.LIGHT_PURPLE + "[P] " + ChatColor.AQUA);
        partyMemberTeam.setSuffix("");

        // Admin teams with enhanced styling
        Team devTeam = scoreboard.registerNewTeam("dev");
        devTeam.setColor(ChatColor.GOLD);
        devTeam.setPrefix(ChatColor.GOLD + "⚡ DEV " + ChatColor.GOLD);
        devTeam.setSuffix(ChatColor.GOLD + " ⚡");

        Team managerTeam = scoreboard.registerNewTeam("manager");
        managerTeam.setColor(ChatColor.YELLOW);
        managerTeam.setPrefix(ChatColor.YELLOW + "★ MANAGER " + ChatColor.YELLOW);
        managerTeam.setSuffix(ChatColor.YELLOW + " ★");

        Team gmTeam = scoreboard.registerNewTeam("gm");
        gmTeam.setColor(ChatColor.AQUA);
        gmTeam.setPrefix(ChatColor.AQUA + "♦ GM " + ChatColor.AQUA);
        gmTeam.setSuffix(ChatColor.AQUA + " ♦");

        // FIXED: Alignment teams with proper colors for above-head display
        Team chaoticTeam = scoreboard.registerNewTeam(CHAOTIC_TEAM);
        chaoticTeam.setColor(ChatColor.RED);
        chaoticTeam.setPrefix(ChatColor.RED + "");
        chaoticTeam.setSuffix("");

        Team neutralTeam = scoreboard.registerNewTeam(NEUTRAL_TEAM);
        neutralTeam.setColor(ChatColor.YELLOW);
        neutralTeam.setPrefix(ChatColor.YELLOW + "");
        neutralTeam.setSuffix("");

        Team lawfulTeam = scoreboard.registerNewTeam(LAWFUL_TEAM);
        lawfulTeam.setColor(ChatColor.GRAY);
        lawfulTeam.setPrefix(ChatColor.GRAY + "");
        lawfulTeam.setSuffix("");

        // Default team
        Team defaultTeam = scoreboard.registerNewTeam(DEFAULT_TEAM);
        defaultTeam.setColor(ChatColor.WHITE);
        defaultTeam.setPrefix(ChatColor.WHITE + "");
        defaultTeam.setSuffix("");
    }

    /**
     * FIXED: Setup health display using BELOW_NAME for above-head display
     */
    private static void setupHealthDisplay(Scoreboard scoreboard) {
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
                    int health = (int) Math.ceil(player.getHealth());
                    healthObjective.getScore(player.getName()).setScore(health);
                } catch (Exception e) {
                    // Skip this player if there's an error
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to setup health display: " + e.getMessage());
        }
    }

    /**
     * FIXED: Update a player's scoreboard with alignment change detection
     */
    public static void updatePlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTimes.get(playerId);

        // FIXED: Check if any party member's alignment has changed
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

        if (!partyMechanics.isInParty(player)) {
            clearPlayerScoreboard(player);
            return;
        }

        try {
            Scoreboard scoreboard = getPlayerScoreboard(player);

            // FIXED: Force complete refresh if alignment changed
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
        }
    }

    /**
     * FIXED: Check if any party member's alignment has changed since last update
     */
    private static boolean hasAnyAlignmentChanged(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        if (!partyMechanics.isInParty(viewer)) {
            return false;
        }

        List<Player> partyMembers = partyMechanics.getPartyMembers(viewer);
        if (partyMembers == null) {
            return false;
        }

        Map<String, ChatColor> lastColors = playerLastColors.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>());

        for (Player member : partyMembers) {
            if (!member.isOnline()) continue;

            ChatColor currentColor = getPlayerDisplayColor(member);
            ChatColor lastColor = lastColors.get(member.getName());

            if (lastColor == null || !lastColor.equals(currentColor)) {
                // Color changed - update stored color and return true
                lastColors.put(member.getName(), currentColor);
                return true;
            }
        }

        return false;
    }

    /**
     * FIXED: Force complete refresh when alignment changes are detected
     */
    private static void forceCompleteRefresh(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        UUID playerId = player.getUniqueId();

        // Get current active entries
        Set<String> currentEntries = activeScoreboardEntries.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // FIXED: Clear ALL existing entries completely
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
    }

    /**
     * FIXED: Update the party objective with proper cleanup and unique entry names
     */
    private static void updatePartyObjective(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        UUID playerId = player.getUniqueId();

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
                if (!member.isOnline()) continue;

                // FIXED: Create truly unique display name using current alignment colors
                String displayName = formatPartyMemberName(member, partyMechanics, scoreIndex, usedNames);
                int health = (int) Math.ceil(member.getHealth());

                partyObjective.getScore(displayName).setScore(health);
                currentEntries.add(displayName);
                usedNames.add(displayName);
                scoreIndex--;

                // Prevent going below 0
                if (scoreIndex < 0) break;
            }
        }
    }

    /**
     * FIXED: Format party member name with current alignment colors and guaranteed uniqueness
     */
    private static String formatPartyMemberName(Player member, PartyMechanics partyMechanics, int index, Set<String> usedNames) {
        StringBuilder nameBuilder = new StringBuilder();

        // Add role indicator
        if (partyMechanics.isPartyLeader(member)) {
            nameBuilder.append(ChatColor.GOLD).append("★ ");
        } else if (partyMechanics.isPartyOfficer(member)) {
            nameBuilder.append(ChatColor.YELLOW).append("♦ ");
        } else {
            nameBuilder.append(ChatColor.GRAY).append("• ");
        }

        // FIXED: Add player name with CURRENT alignment color
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

        // FIXED: Ensure complete uniqueness by adding index if name is used
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
    }

    /**
     * FIXED: Get appropriate display color for a player using CURRENT alignment
     */
    private static ChatColor getPlayerDisplayColor(Player player) {
        // Check rank first for admins
        Rank rank = ModerationMechanics.getRank(player);
        if (rank != Rank.DEFAULT) {
            return switch (rank) {
                case DEV -> ChatColor.GOLD;
                case MANAGER -> ChatColor.YELLOW;
                case GM -> ChatColor.AQUA;
                default -> ChatColor.WHITE;
            };
        }

        // FIXED: Get CURRENT alignment color
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null) {
            String alignment = yakPlayer.getAlignment();
            return switch (alignment) {
                case "CHAOTIC" -> ChatColor.RED;
                case "NEUTRAL" -> ChatColor.YELLOW;
                case "LAWFUL" -> ChatColor.GRAY;
                default -> ChatColor.WHITE;
            };
        }

        return ChatColor.WHITE;
    }

    /**
     * Enhanced party health bar system using boss bars
     */
    private static void updatePartyHealthBar(Player player, PartyMechanics partyMechanics) {
        UUID playerId = player.getUniqueId();

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
            if (member.isOnline()) {
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
     * FIXED: Update team assignments with proper alignment color handling
     */
    public static void updatePlayerTeamAssignments(Player viewer) {
        Scoreboard scoreboard = getPlayerScoreboard(viewer);
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        // Clear all team assignments first
        for (Team team : scoreboard.getTeams()) {
            team.getEntries().clear();
        }

        // Get viewer's party info
        List<Player> viewerPartyMembers = null;
        if (partyMechanics.isInParty(viewer)) {
            viewerPartyMembers = partyMechanics.getPartyMembers(viewer);
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            String teamName = determinePlayerTeam(target, viewer, viewerPartyMembers, partyMechanics);
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.addEntry(target.getName());
            }
        }

        viewer.setScoreboard(scoreboard);
    }

    /**
     * FIXED: Determine which team a player should be assigned to with current alignment
     */
    private static String determinePlayerTeam(Player target, Player viewer, List<Player> viewerPartyMembers, PartyMechanics partyMechanics) {
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
        if (rank == Rank.DEV) {
            return "dev";
        } else if (rank == Rank.MANAGER) {
            return "manager";
        } else if (rank == Rank.GM) {
            return "gm";
        }

        // FIXED: Check CURRENT alignment for regular players
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(target);
        if (yakPlayer != null) {
            String alignment = yakPlayer.getAlignment();
            return switch (alignment) {
                case "CHAOTIC" -> CHAOTIC_TEAM;
                case "NEUTRAL" -> NEUTRAL_TEAM;
                case "LAWFUL" -> LAWFUL_TEAM;
                default -> DEFAULT_TEAM;
            };
        }

        return DEFAULT_TEAM;
    }

    /**
     * Refresh all party scoreboards efficiently
     */
    public static void refreshAllPartyScoreboards() {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (partyMechanics.isInParty(player)) {
                    refreshPlayerScoreboard(player);
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error refreshing party scoreboard for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * FIXED: Refresh player scoreboard with alignment change detection
     */
    public static void refreshPlayerScoreboard(Player player) {
        // Force update by clearing last update time
        lastUpdateTimes.remove(player.getUniqueId());
        updatePlayerScoreboard(player);
    }

    /**
     * FIXED: Clear a player's party scoreboard with proper cleanup
     */
    public static void clearPlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        Scoreboard scoreboard = getPlayerScoreboard(player);

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

        // Remove party health bar
        BossBar bossBar = partyHealthBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        // Show leave effect
        PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                playerId, k -> new PartyVisualEffects(player));
        visuals.showPartyLeaveEffect();

        player.setScoreboard(scoreboard);
    }

    /**
     * Show party join effects
     */
    public static void showPartyJoinEffects(Player player) {
        PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                player.getUniqueId(), k -> new PartyVisualEffects(player));
        visuals.showPartyJoinEffect();
    }

    /**
     * Show party leader promotion effects
     */
    public static void showLeaderPromotionEffects(Player player) {
        PartyVisualEffects visuals = partyVisuals.computeIfAbsent(
                player.getUniqueId(), k -> new PartyVisualEffects(player));
        visuals.showLeaderPromotionEffect();
    }

    /**
     * FIXED: Update health display for all players with alignment awareness
     */
    public static void updateAllPlayerHealth() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            try {
                Scoreboard scoreboard = getPlayerScoreboard(viewer);
                Objective healthObjective = scoreboard.getObjective("health");

                if (healthObjective != null) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
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
     * FIXED: Update color teams for all players with enhanced team management
     */
    public static void updateAllPlayerColors() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
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
     * FIXED: Clean up resources for a player with complete cleanup
     */
    public static void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        playerScoreboards.remove(playerId);
        lastUpdateTimes.remove(playerId);
        partyVisuals.remove(playerId);

        // FIXED: Clean up tracking maps
        activeScoreboardEntries.remove(playerId);
        playerLastColors.remove(playerId);

        BossBar bossBar = partyHealthBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Clean up all resources
     */
    public static void cleanupAll() {
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
    }

    /**
     * FIXED: Force refresh all scoreboards with alignment updates
     */
    public static void forceRefreshAll() {
        // Clear all cached data
        lastUpdateTimes.clear();
        playerLastColors.clear();

        // Update all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                PartyMechanics partyMechanics = PartyMechanics.getInstance();

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
    }

    /**
     * FIXED: Public method to handle alignment changes
     * Call this whenever a player's alignment changes
     */
    public static void handleAlignmentChange(Player player) {
        UUID playerId = player.getUniqueId();

        // Clear color cache to force refresh
        Map<String, ChatColor> playerColors = playerLastColors.get(playerId);
        if (playerColors != null) {
            playerColors.clear();
        }

        // Clear last update time to force refresh
        lastUpdateTimes.remove(playerId);

        // Update team assignments and scoreboard
        updatePlayerTeamAssignments(player);
        updatePlayerScoreboard(player);

        // Update for all party members if this player is in a party
        PartyMechanics partyMechanics = PartyMechanics.getInstance();
        if (partyMechanics.isInParty(player)) {
            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers != null) {
                for (Player member : partyMembers) {
                    if (member.isOnline() && !member.equals(player)) {
                        // Clear their cache too since they see this player
                        Map<String, ChatColor> memberColors = playerLastColors.get(member.getUniqueId());
                        if (memberColors != null) {
                            memberColors.clear();
                        }
                        lastUpdateTimes.remove(member.getUniqueId());
                        updatePlayerScoreboard(member);
                    }
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
        stats.put("player_colors", playerLastColors.size());
        return stats;
    }

    /**
     * FIXED: Validate and repair scoreboard integrity with alignment support
     */
    public static void validateAndRepairScoreboards() {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        for (Player player : Bukkit.getOnlinePlayers()) {
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
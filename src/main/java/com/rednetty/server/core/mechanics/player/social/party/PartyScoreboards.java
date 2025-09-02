package com.rednetty.server.core.mechanics.player.social.party;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

// Adventure API imports for 1.21.7
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Party scoreboards with full Adventure API support for Paper Spigot 1.21.7
 * - Completely modernized with Adventure API - NO deprecated ChatColor usage
 * - Enhanced text components, boss bars, sounds, and titles
 * - Fixed above head name colors to show alignment even in parties
 * - Fixed health display conflicts between party/non-party modes
 * - Simplified update logic to prevent throttling issues
 * - Improved team management to handle both party roles and alignment colors
 * - Full backwards compatibility through Adventure API legacy serializer
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

    // Adventure API color constants
    private static final NamedTextColor PARTY_PRIMARY = NamedTextColor.LIGHT_PURPLE; // Light purple
    private static final NamedTextColor PARTY_ACCENT = NamedTextColor.GOLD;  // Gold
    private static final NamedTextColor HEALTH_COLOR = NamedTextColor.RED;
    private static final NamedTextColor LEADER_COLOR = NamedTextColor.GOLD;
    private static final NamedTextColor OFFICER_COLOR = NamedTextColor.YELLOW;
    private static final NamedTextColor MEMBER_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor CHAOTIC_COLOR = NamedTextColor.RED;
    private static final NamedTextColor NEUTRAL_COLOR = NamedTextColor.YELLOW;
    private static final NamedTextColor LAWFUL_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor DEFAULT_COLOR = NamedTextColor.WHITE;

    // Legacy component serializer for backwards compatibility with legacy string methods
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    /**
     * Adventure API helper methods for backwards compatibility
     */
    private static Component legacyToComponent(String legacyText) {
        if (legacyText == null) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(legacyText);
    }

    private static String componentToLegacy(Component component) {
        if (component == null) return "";
        return LEGACY_SERIALIZER.serialize(component);
    }

    private static BossBar.Color adventureBossBarColor(org.bukkit.boss.BarColor bukkitColor) {
        if (bukkitColor == null) return BossBar.Color.WHITE;

        switch (bukkitColor) {
            case PINK: return BossBar.Color.PINK;
            case BLUE: return BossBar.Color.BLUE;
            case RED: return BossBar.Color.RED;
            case GREEN: return BossBar.Color.GREEN;
            case YELLOW: return BossBar.Color.YELLOW;
            case PURPLE: return BossBar.Color.PURPLE;
            case WHITE: return BossBar.Color.WHITE;
            default: return BossBar.Color.WHITE;
        }
    }

    /**
     * Get NamedTextColor from alignment string
     */
    private static NamedTextColor getAlignmentNamedColor(String alignment) {
        if (alignment == null) return DEFAULT_COLOR;

        switch (alignment.toUpperCase()) {
            case "CHAOTIC":
                return CHAOTIC_COLOR;
            case "NEUTRAL":
                return NEUTRAL_COLOR;
            case "LAWFUL":
                return LAWFUL_COLOR;
            default:
                return DEFAULT_COLOR;
        }
    }

    /**
     * Get NamedTextColor from rank
     */
    private static NamedTextColor getRankNamedColor(Rank rank) {
        if (rank == null) return DEFAULT_COLOR;

        switch (rank) {
            case DEV:
                return LEADER_COLOR;
            case MANAGER:
                return OFFICER_COLOR;
            case GM:
                return NamedTextColor.AQUA;
            default:
                return DEFAULT_COLOR;
        }
    }

    /**
     * Visual effects system for party interactions with Adventure API
     */
    private static class PartyVisualEffects {
        private final Player player;

        public PartyVisualEffects(Player player) {
            this.player = player;
        }

        public void showPartyJoinEffect() {
            if (!isPlayerValid()) return;

            try {
                // Adventure API title
                Component titleComponent = Component.text("✦ PARTY JOINED ✦")
                        .color(PARTY_PRIMARY)
                        .decorate(TextDecoration.BOLD);

                Component subtitleComponent = Component.text("You are now part of a team")
                        .color(NamedTextColor.GRAY);

                Title title = Title.title(
                        titleComponent,
                        subtitleComponent,
                        Title.Times.times(
                                Duration.ofMillis(500),  // fade in
                                Duration.ofMillis(2000), // stay
                                Duration.ofMillis(500)   // fade out
                        )
                );

                player.showTitle(title);

                // Adventure API sound
                Sound sound = Sound.sound(
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                        Sound.Source.PLAYER,
                        0.7f,
                        1.2f
                );
                player.playSound(sound);

            } catch (Exception e) {
                // Ignore visual effect errors
            }
        }

        public void showPartyLeaveEffect() {
            if (!isPlayerValid()) return;

            try {
                // Adventure API title
                Component titleComponent = Component.text("✦ PARTY LEFT ✦")
                        .color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD);

                Component subtitleComponent = Component.text("You are no longer in a party")
                        .color(NamedTextColor.GRAY);

                Title title = Title.title(
                        titleComponent,
                        subtitleComponent,
                        Title.Times.times(
                                Duration.ofMillis(500),  // fade in
                                Duration.ofMillis(1500), // stay
                                Duration.ofMillis(500)   // fade out
                        )
                );

                player.showTitle(title);

                // Adventure API sound
                Sound sound = Sound.sound(
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS,
                        Sound.Source.PLAYER,
                        0.5f,
                        1.0f
                );
                player.playSound(sound);

            } catch (Exception e) {
                // Ignore visual effect errors
            }
        }

        public void showLeaderPromotionEffect() {
            if (!isPlayerValid()) return;

            try {
                // Adventure API title
                Component titleComponent = Component.text("★ PARTY LEADER ★")
                        .color(LEADER_COLOR)
                        .decorate(TextDecoration.BOLD);

                Component subtitleComponent = Component.text("You are now the party leader")
                        .color(OFFICER_COLOR);

                Title title = Title.title(
                        titleComponent,
                        subtitleComponent,
                        Title.Times.times(
                                Duration.ofMillis(500),  // fade in
                                Duration.ofMillis(2500), // stay
                                Duration.ofMillis(500)   // fade out
                        )
                );

                player.showTitle(title);

                // Adventure API sound
                Sound sound = Sound.sound(
                        org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                        Sound.Source.PLAYER,
                        1.0f,
                        1.5f
                );
                player.playSound(sound);

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
     * Setup teams with alignment-based coloring and party role prefixes (Adventure API)
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

            // Admin teams (highest priority) with Adventure API
            createTeamSafelyAdventure(scoreboard, ADMIN_DEV_TEAM, LEADER_COLOR,
                    Component.text("⚡ DEV ").color(LEADER_COLOR), Component.empty());

            createTeamSafelyAdventure(scoreboard, ADMIN_MANAGER_TEAM, OFFICER_COLOR,
                    Component.text("★ MANAGER ").color(OFFICER_COLOR), Component.empty());

            createTeamSafelyAdventure(scoreboard, ADMIN_GM_TEAM, NamedTextColor.AQUA,
                    Component.text("♦ GM ").color(NamedTextColor.AQUA), Component.empty());

            // Alignment teams with colors but NO prefixes (party prefixes added dynamically)
            createTeamSafelyAdventure(scoreboard, CHAOTIC_TEAM, CHAOTIC_COLOR,
                    Component.empty(), Component.empty());
            createTeamSafelyAdventure(scoreboard, NEUTRAL_TEAM, NEUTRAL_COLOR,
                    Component.empty(), Component.empty());
            createTeamSafelyAdventure(scoreboard, LAWFUL_TEAM, LAWFUL_COLOR,
                    Component.empty(), Component.empty());
            createTeamSafelyAdventure(scoreboard, DEFAULT_TEAM, DEFAULT_COLOR,
                    Component.empty(), Component.empty());

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error setting up teams: " + e.getMessage());
        }
    }

    /**
     * Safely create a team with Adventure API components
     */
    private static void createTeamSafelyAdventure(Scoreboard scoreboard, String name, NamedTextColor color, Component prefix, Component suffix) {
        if (scoreboard == null || name == null) return;

        try {
            Team existingTeam = scoreboard.getTeam(name);
            if (existingTeam != null) {
                existingTeam.unregister();
            }

            Team team = scoreboard.registerNewTeam(name);
            if (team != null) {
                // Set team color for name display
                team.color(color != null ? color : DEFAULT_COLOR);

                if (prefix != null) team.prefix(prefix);
                if (suffix != null) team.suffix(suffix);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to create Adventure team " + name + ": " + e.getMessage());
        }
    }

    /**
     * Setup health display with Adventure API
     */
    private static void setupHealthDisplay(Scoreboard scoreboard) {
        if (scoreboard == null) return;

        try {
            // Clear existing health objective
            Objective existingHealth = scoreboard.getObjective("health");
            if (existingHealth != null) {
                existingHealth.unregister();
            }

            // Create health objective for above-head display with Adventure API
            Objective healthObjective = scoreboard.registerNewObjective(
                    "health", "health", Component.text("♥").color(HEALTH_COLOR));
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
            Bukkit.getLogger().warning("Failed to setup Adventure health display: " + e.getMessage());
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
     * Update team assignments with party role prefixes AND alignment colors (Adventure API)
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
                        // Add party role prefix dynamically with Adventure API
                        Component partyPrefix = getPartyRolePrefixComponent(target, viewer, viewerPartyMembers, partyMechanics);
                        if (partyPrefix != null && !partyPrefix.equals(Component.empty())) {
                            // Update team prefix to include party role
                            Component currentPrefix = team.prefix();
                            if (currentPrefix == null) currentPrefix = Component.empty();

                            // Only add party prefix if not already an admin
                            String currentPrefixString = componentToLegacy(currentPrefix);
                            if (!currentPrefixString.contains("DEV") && !currentPrefixString.contains("MANAGER") && !currentPrefixString.contains("GM")) {
                                team.prefix(partyPrefix);
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
            Bukkit.getLogger().warning("Error updating Adventure team assignments for " + viewer.getName() + ": " + e.getMessage());
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
     * Get party role prefix for display (Adventure API)
     */
    private static Component getPartyRolePrefixComponent(Player target, Player viewer, List<Player> viewerPartyMembers, PartyMechanics partyMechanics) {
        if (target == null || viewer == null || partyMechanics == null) {
            return Component.empty();
        }

        try {
            // Only show party prefixes if both players are in the same party
            if (viewerPartyMembers != null && viewerPartyMembers.contains(target)) {
                if (partyMechanics.isPartyLeader(target)) {
                    return Component.text("★").color(LEADER_COLOR)
                            .append(Component.text("[P] ").color(PARTY_PRIMARY));
                } else if (partyMechanics.isPartyOfficer(target)) {
                    return Component.text("♦").color(OFFICER_COLOR)
                            .append(Component.text("[P] ").color(PARTY_PRIMARY));
                } else {
                    return Component.text("[P] ").color(PARTY_PRIMARY);
                }
            }
        } catch (Exception e) {
            // Return empty on error
        }

        return Component.empty();
    }

    /**
     * Update party objective with cleaner entry management (Adventure API)
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

            // Create new party objective with Adventure API
            Component objectiveTitle = Component.text("✦ ").color(PARTY_PRIMARY)
                    .append(Component.text("PARTY").color(PARTY_PRIMARY).decorate(TextDecoration.BOLD))
                    .append(Component.text(" ✦").color(PARTY_PRIMARY));

            Objective partyObjective = scoreboard.registerNewObjective("party_data", "dummy", objectiveTitle);
            partyObjective.setDisplaySlot(DisplaySlot.SIDEBAR);

            List<Player> partyMembers = partyMechanics.getPartyMembers(player);
            if (partyMembers != null && !partyMembers.isEmpty()) {
                // Add party size indicator using legacy format for scoreboard entries
                String sizeIndicator = componentToLegacy(
                        Component.text("Members: ").color(NamedTextColor.GRAY)
                                .append(Component.text(partyMembers.size()).color(NamedTextColor.WHITE))
                );
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
     * Format party member name with role and health indicators (Adventure API)
     */
    private static String formatPartyMemberName(Player member, PartyMechanics partyMechanics, int index, Set<String> usedNames) {
        if (member == null || !member.isOnline() || partyMechanics == null) {
            return null;
        }

        try {
            Component nameComponent = Component.empty();

            // Add role indicator
            if (partyMechanics.isPartyLeader(member)) {
                nameComponent = nameComponent.append(Component.text("★ ").color(LEADER_COLOR));
            } else if (partyMechanics.isPartyOfficer(member)) {
                nameComponent = nameComponent.append(Component.text("♦ ").color(OFFICER_COLOR));
            } else {
                nameComponent = nameComponent.append(Component.text("• ").color(MEMBER_COLOR));
            }

            // Add player name with alignment color
            NamedTextColor nameColor = getPlayerDisplayColor(member);
            nameComponent = nameComponent.append(Component.text(member.getName()).color(nameColor));

            // Add health status indicator
            double healthPercentage = member.getHealth() / member.getMaxHealth();
            if (healthPercentage <= 0.3) {
                nameComponent = nameComponent.append(Component.text(" ♥").color(NamedTextColor.RED));
            } else if (healthPercentage >= 1.0) {
                nameComponent = nameComponent.append(Component.text(" ♥").color(NamedTextColor.GREEN));
            } else if (healthPercentage <= 0.6) {
                nameComponent = nameComponent.append(Component.text(" ♥").color(NamedTextColor.YELLOW));
            }

            String result = componentToLegacy(nameComponent);

            // Ensure uniqueness
            String finalName = result;
            int suffix = 0;
            while (usedNames.contains(finalName)) {
                finalName = result + "§0" + suffix;
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
     * Get appropriate display color for a player (Adventure API)
     */
    private static NamedTextColor getPlayerDisplayColor(Player player) {
        if (player == null) {
            return DEFAULT_COLOR;
        }

        try {
            // Check rank first for admins
            Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
            if (rank != null && rank != Rank.DEFAULT) {
                return getRankNamedColor(rank);
            }

            // Get alignment color
            YakPlayerManager playerManager = YakPlayerManager.getInstance();
            if (playerManager != null) {
                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer != null) {
                    String alignment = yakPlayer.getAlignment();
                    return getAlignmentNamedColor(alignment);
                }
            }
        } catch (Exception e) {
            // Fall back to default color on any error
        }

        return DEFAULT_COLOR;
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
     * Update party health bar (Adventure API)
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

            // Create title with Adventure API
            Component title = Component.text("✦ ").color(PARTY_PRIMARY)
                    .append(Component.text("PARTY").color(PARTY_PRIMARY).decorate(TextDecoration.BOLD))
                    .append(Component.text(" (" + aliveMembersCount + "/" + partyMembers.size() + ")").color(PARTY_PRIMARY))
                    .append(Component.text(" ").color(NamedTextColor.WHITE))
                    .append(Component.text((int)totalHealth + "/" + (int)maxTotalHealth).color(NamedTextColor.WHITE))
                    .append(Component.text(" ♥").color(HEALTH_COLOR));

            BossBar.Color barColor = getPartyHealthBarColorAdventure(healthPercentage, aliveMembersCount, partyMembers.size());

            BossBar bossBar = partyHealthBars.get(playerId);
            if (bossBar == null) {
                bossBar = BossBar.bossBar(title, (float) Math.max(0.0, Math.min(1.0, healthPercentage)),
                        barColor, BossBar.Overlay.NOTCHED_10);
                player.showBossBar(bossBar);
                partyHealthBars.put(playerId, bossBar);
            }

            bossBar.color(barColor);
            bossBar.name(title);
            bossBar.progress((float) Math.max(0.0, Math.min(1.0, healthPercentage)));

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error updating Adventure party health bar for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Clear party health bar (Adventure API)
     */
    private static void clearPartyHealthBar(UUID playerId) {
        try {
            BossBar bossBar = partyHealthBars.remove(playerId);
            if (bossBar != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.hideBossBar(bossBar);
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    /**
     * Determine boss bar color based on party health status (Adventure API)
     */
    private static BossBar.Color getPartyHealthBarColorAdventure(double healthPercentage, int aliveCount, int totalCount) {
        // Critical if someone is dead
        if (aliveCount < totalCount) {
            return BossBar.Color.RED;
        }

        // Color based on overall health
        if (healthPercentage > 0.75) return BossBar.Color.GREEN;
        if (healthPercentage > 0.5) return BossBar.Color.YELLOW;
        if (healthPercentage > 0.25) return BossBar.Color.RED;
        return BossBar.Color.PURPLE; // Critical health
    }

    /**
     * Update all player scoreboards
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
     * Update health for all players
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
     * Update all player colors and team assignments
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
                    // Adventure API cleanup
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player != null && player.isOnline()) {
                            player.hideBossBar(bossBar);
                        }
                    }
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
        stats.put("adventure_api_enabled", true);
        stats.put("chatcolor_deprecated_removed", true);
        return stats;
    }

    // ========================================
    // ADVENTURE API SPECIFIC METHODS
    // ========================================

    /**
     * Send Adventure API message to player
     */
    public static void sendAdventureMessage(Player player, Component message) {
        if (player == null || !player.isOnline() || message == null) return;

        try {
            player.sendMessage(message);
        } catch (Exception e) {
            // Fallback to legacy serialized version
            player.sendMessage(componentToLegacy(message));
        }
    }

    /**
     * Show Adventure API title to player
     */
    public static void showAdventureTitle(Player player, Component title, Component subtitle, Duration fadeIn, Duration stay, Duration fadeOut) {
        if (player == null || !player.isOnline()) return;

        try {
            Title adventureTitle = Title.title(
                    title != null ? title : Component.empty(),
                    subtitle != null ? subtitle : Component.empty(),
                    Title.Times.times(fadeIn, stay, fadeOut)
            );
            player.showTitle(adventureTitle);
        } catch (Exception e) {
            // Adventure API might not be available, ignore
        }
    }

    /**
     * Play Adventure API sound to player
     */
    public static void playAdventureSound(Player player, Sound sound) {
        if (player == null || !player.isOnline() || sound == null) return;

        try {
            player.playSound(sound);
        } catch (Exception e) {
            // Adventure API might not be available, ignore
        }
    }

    /**
     * Get Adventure API color from alignment
     */
    public static NamedTextColor getAlignmentColor(String alignment) {
        return getAlignmentNamedColor(alignment);
    }

    /**
     * Create Adventure API component from player name with alignment color
     */
    public static Component createPlayerNameComponent(Player player) {
        if (player == null) return Component.text("Unknown").color(DEFAULT_COLOR);

        try {
            NamedTextColor color = getPlayerDisplayColor(player);
            return Component.text(player.getName()).color(color);
        } catch (Exception e) {
            return Component.text(player.getName()).color(DEFAULT_COLOR);
        }
    }

    /**
     * Create formatted party announcement component
     */
    public static Component createPartyAnnouncementComponent(String announcement) {
        return Component.text("✦ ").color(PARTY_PRIMARY)
                .append(Component.text("PARTY").color(PARTY_PRIMARY).decorate(TextDecoration.BOLD))
                .append(Component.text(" ✦ ").color(PARTY_PRIMARY))
                .append(Component.text(announcement).color(NamedTextColor.WHITE));
    }

    /**
     * Create health component with color based on percentage
     */
    public static Component createHealthComponent(double currentHealth, double maxHealth) {
        double percentage = currentHealth / maxHealth;
        NamedTextColor healthColor = NamedTextColor.GREEN;

        if (percentage <= 0.25) {
            healthColor = NamedTextColor.RED;
        } else if (percentage <= 0.5) {
            healthColor = NamedTextColor.YELLOW;
        } else if (percentage <= 0.75) {
            healthColor = NamedTextColor.GOLD;
        }

        return Component.text((int) currentHealth + "/" + (int) maxHealth + " ♥").color(healthColor);
    }

    /**
     * Create rank prefix component
     */
    public static Component createRankPrefixComponent(Rank rank) {
        if (rank == null || rank == Rank.DEFAULT) return Component.empty();

        switch (rank) {
            case DEV:
                return Component.text("⚡ DEV ").color(LEADER_COLOR);
            case MANAGER:
                return Component.text("★ MANAGER ").color(OFFICER_COLOR);
            case GM:
                return Component.text("♦ GM ").color(NamedTextColor.AQUA);
            default:
                return Component.empty();
        }
    }

    /**
     * Format player name with full context (rank, party role, alignment)
     */
    public static Component formatPlayerNameFull(Player player, Player viewer, PartyMechanics partyMechanics) {
        if (player == null) return Component.text("Unknown").color(DEFAULT_COLOR);

        Component result = Component.empty();

        try {
            // Add rank prefix if applicable
            Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
            if (rank != null && rank != Rank.DEFAULT) {
                result = result.append(createRankPrefixComponent(rank));
            } else if (partyMechanics != null && viewer != null) {
                // Add party prefix if no rank and in same party
                List<Player> viewerPartyMembers = partyMechanics.isInParty(viewer) ?
                        partyMechanics.getPartyMembers(viewer) : null;
                Component partyPrefix = getPartyRolePrefixComponent(player, viewer, viewerPartyMembers, partyMechanics);
                if (!partyPrefix.equals(Component.empty())) {
                    result = result.append(partyPrefix);
                }
            }

            // Add player name with alignment color
            NamedTextColor nameColor = getPlayerDisplayColor(player);
            result = result.append(Component.text(player.getName()).color(nameColor));

        } catch (Exception e) {
            result = Component.text(player.getName()).color(DEFAULT_COLOR);
        }

        return result;
    }
}
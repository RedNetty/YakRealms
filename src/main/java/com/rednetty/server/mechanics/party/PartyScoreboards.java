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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced party scoreboards with modern 1.20.4 API usage and improved visual design
 */
public class PartyScoreboards {
    private static final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private static final Map<UUID, BossBar> partyHealthBars = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    // Enhanced visual system for party management
    private static final Map<UUID, PartyVisualEffects> partyVisuals = new ConcurrentHashMap<>();

    // Update throttling to prevent spam
    private static final long UPDATE_COOLDOWN = 100; // 100ms between updates

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

            // Enhanced party join notification
            player.sendTitle(
                    ChatColor.LIGHT_PURPLE + "✦ PARTY JOINED ✦",
                    ChatColor.GRAY + "You are now part of a team",
                    10, 40, 10
            );

            // Sound effect
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
     * Get or create a player's enhanced scoreboard
     */
    public static Scoreboard getPlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerScoreboards.containsKey(playerId)) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            Scoreboard scoreboard = manager.getNewScoreboard();

            // Enhanced team setup with modern styling
            setupEnhancedTeams(scoreboard);
            setupHealthDisplay(scoreboard);

            playerScoreboards.put(playerId, scoreboard);
            return scoreboard;
        }

        return playerScoreboards.get(playerId);
    }

    /**
     * Setup enhanced teams with better visual hierarchy
     */
    private static void setupEnhancedTeams(Scoreboard scoreboard) {
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

        // Player alignment teams with symbols
        Team chaoticTeam = scoreboard.registerNewTeam("chaotic");
        chaoticTeam.setColor(ChatColor.RED);
        chaoticTeam.setPrefix(ChatColor.RED + "☠ ");
        chaoticTeam.setSuffix(ChatColor.RED + " ☠");

        Team neutralTeam = scoreboard.registerNewTeam("neutral");
        neutralTeam.setColor(ChatColor.YELLOW);
        neutralTeam.setPrefix(ChatColor.YELLOW + "◈ ");
        neutralTeam.setSuffix(ChatColor.YELLOW + " ◈");

        Team lawfulTeam = scoreboard.registerNewTeam("lawful");
        lawfulTeam.setColor(ChatColor.GRAY);

        // Default team
        Team defaultTeam = scoreboard.registerNewTeam("default");
        defaultTeam.setColor(ChatColor.WHITE);
    }

    /**
     * Setup enhanced health display using modern objectives
     */
    private static void setupHealthDisplay(Scoreboard scoreboard) {
        try {
            // Use TAB_LIST instead of deprecated BELOW_NAME
            Objective healthObjective = scoreboard.registerNewObjective(
                    "health", "health", ChatColor.RED + "❤");
            healthObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        } catch (Exception e) {
            // Fallback for older versions
            Objective healthObjective = scoreboard.registerNewObjective(
                    "health", "health", ChatColor.RED + "❤");
            // Don't set display slot if it fails
        }
    }

    /**
     * Update a player's scoreboard with enhanced party information
     */
    public static void updatePlayerScoreboard(Player player) {
        // Throttle updates to prevent spam
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTimes.get(playerId);

        if (lastUpdate != null && currentTime - lastUpdate < UPDATE_COOLDOWN) {
            return;
        }
        lastUpdateTimes.put(playerId, currentTime);

        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        if (!partyMechanics.isInParty(player)) {
            clearPlayerScoreboard(player);
            return;
        }

        Scoreboard scoreboard = getPlayerScoreboard(player);
        updatePartyObjective(player, scoreboard, partyMechanics);
        updatePartyHealthBar(player, partyMechanics);

        player.setScoreboard(scoreboard);
    }

    /**
     * Update the party objective with enhanced styling
     */
    private static void updatePartyObjective(Player player, Scoreboard scoreboard, PartyMechanics partyMechanics) {
        // Remove existing sidebar objective
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
            int scoreIndex = partyMembers.size();

            // Add party size indicator
            String sizeIndicator = ChatColor.GRAY + "Members: " + ChatColor.WHITE + partyMembers.size();
            partyObjective.getScore(sizeIndicator).setScore(scoreIndex + 1);

            // Add separator
            partyObjective.getScore(" ").setScore(scoreIndex);

            for (Player member : partyMembers) {
                if (!member.isOnline()) continue;

                String displayName = formatPartyMemberName(member, partyMechanics);
                int health = (int) member.getHealth();

                partyObjective.getScore(displayName).setScore(health);
                scoreIndex--;
            }
        }
    }

    /**
     * Format party member name with enhanced styling
     */
    private static String formatPartyMemberName(Player member, PartyMechanics partyMechanics) {
        StringBuilder nameBuilder = new StringBuilder();

        // Add leader indicator
        if (partyMechanics.isPartyLeader(member)) {
            nameBuilder.append(ChatColor.GOLD).append("★ ");
        } else {
            nameBuilder.append(ChatColor.GRAY).append("• ");
        }

        // Add player name with rank/alignment color
        ChatColor nameColor = getPlayerDisplayColor(member);
        nameBuilder.append(nameColor).append(member.getName());

        // Add status indicators
        if (member.getHealth() <= 6) { // Low health
            nameBuilder.append(ChatColor.RED).append(" ♥");
        } else if (member.getHealth() >= member.getMaxHealth()) { // Full health
            nameBuilder.append(ChatColor.GREEN).append(" ♥");
        }

        // Truncate if too long for scoreboard
        String result = nameBuilder.toString();
        if (result.length() > 32) {
            result = result.substring(0, 29) + "...";
        }

        return result;
    }

    /**
     * Get appropriate display color for a player
     */
    private static ChatColor getPlayerDisplayColor(Player player) {
        // Check rank first
        Rank rank = ModerationMechanics.getRank(player);
        if (rank != Rank.DEFAULT) {
            return switch (rank) {
                case DEV -> ChatColor.GOLD;
                case MANAGER -> ChatColor.YELLOW;
                case GM -> ChatColor.AQUA;
                default -> ChatColor.WHITE;
            };
        }

        // Check alignment
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null) {
            return switch (yakPlayer.getAlignment()) {
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
     * Refresh player scoreboard with optimized updates
     */
    public static void refreshPlayerScoreboard(Player player) {
        PartyMechanics partyMechanics = PartyMechanics.getInstance();

        if (!partyMechanics.isInParty(player)) {
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        Objective partyObjective = scoreboard.getObjective(DisplaySlot.SIDEBAR);

        if (partyObjective == null) {
            updatePlayerScoreboard(player);
            return;
        }

        // Quick health updates only
        List<Player> partyMembers = partyMechanics.getPartyMembers(player);
        if (partyMembers != null) {
            for (Player member : partyMembers) {
                if (!member.isOnline()) continue;

                String displayName = formatPartyMemberName(member, partyMechanics);
                int health = (int) member.getHealth();

                Score score = partyObjective.getScore(displayName);
                if (score.getScore() != health) {
                    score.setScore(health);
                }
            }
        }

        // Update health bar
        updatePartyHealthBar(player, partyMechanics);
    }

    /**
     * Clear a player's party scoreboard with effects
     */
    public static void clearPlayerScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        Scoreboard scoreboard = getPlayerScoreboard(player);

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
     * Update color teams for all players with enhanced team management
     */
    public static void updateAllPlayerColors() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            updatePlayerTeamAssignments(viewer);
        }
    }

    /**
     * Update team assignments for a specific player's view
     */
    private static void updatePlayerTeamAssignments(Player viewer) {
        Scoreboard scoreboard = getPlayerScoreboard(viewer);

        for (Player target : Bukkit.getOnlinePlayers()) {
            assignPlayerToAppropriateTeam(scoreboard, target);
        }

        viewer.setScoreboard(scoreboard);
    }

    /**
     * Assign a player to the appropriate team based on rank and alignment
     */
    private static void assignPlayerToAppropriateTeam(Scoreboard scoreboard, Player target) {
        // Remove from all teams first
        for (Team team : scoreboard.getTeams()) {
            team.removeEntry(target.getName());
        }

        // Determine appropriate team
        Rank rank = ModerationMechanics.getRank(target);
        String teamName = "default";

        if (rank == Rank.DEV) {
            teamName = "dev";
        } else if (rank == Rank.MANAGER) {
            teamName = "manager";
        } else if (rank == Rank.GM) {
            teamName = "gm";
        } else {
            // Check alignment for regular players
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(target);
            if (yakPlayer != null) {
                teamName = switch (yakPlayer.getAlignment()) {
                    case "CHAOTIC" -> "chaotic";
                    case "NEUTRAL" -> "neutral";
                    case "LAWFUL" -> "lawful";
                    default -> "default";
                };
            }
        }

        // Add to appropriate team
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.addEntry(target.getName());
        }
    }

    /**
     * Update health display for all players with modern API
     */
    public static void updateAllPlayerHealth() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard scoreboard = getPlayerScoreboard(viewer);
            Objective healthObjective = scoreboard.getObjective("health");

            if (healthObjective != null) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    healthObjective.getScore(target.getName()).setScore((int) target.getHealth());
                }
            }

            viewer.setScoreboard(scoreboard);
        }
    }

    /**
     * Clean up resources for a player
     */
    public static void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        playerScoreboards.remove(playerId);
        lastUpdateTimes.remove(playerId);
        partyVisuals.remove(playerId);

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
            bossBar.removeAll();
        }

        playerScoreboards.clear();
        partyHealthBars.clear();
        lastUpdateTimes.clear();
        partyVisuals.clear();
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
        return stats;
    }
}
package com.rednetty.server.mechanics.combat.pvp;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 *  PvP Rating and Kill Streak Manager
 * Manages player PvP ratings, kill streaks, and provides comprehensive tracking
 * with proper integration to the YakPlayer class and combat systems.
 */
public class PvPRatingManager implements Listener {
    private static PvPRatingManager instance;
    private final Logger logger;
    private final YakPlayerManager playerManager;

    // Rating and streak tracking
    private final Map<UUID, Long> lastKillTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killStreak = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> recentKills = new ConcurrentHashMap<>();

    // Configuration constants
    private static final int BASE_RATING = 1000;
    private static final int MAX_RATING = 3000;
    private static final int MIN_RATING = 100;
    private static final long KILL_SPAM_COOLDOWN = 30000; // 30 seconds between same victim kills
    private static final long KILL_STREAK_TIMEOUT = 300000; // 5 minutes to maintain streak
    private static final int MAX_RECENT_KILLS = 10;

    // Rating calculation factors
    private static final int BASE_RATING_GAIN = 25;
    private static final int BASE_RATING_LOSS = 20;
    private static final double RATING_DIFFERENCE_MULTIPLIER = 0.1;
    private static final double STREAK_BONUS_MULTIPLIER = 0.15;
    private static final double ALIGNMENT_MULTIPLIER = 0.2;

    // Kill streak tiers and rewards
    private static final Map<Integer, KillStreakTier> KILL_STREAK_TIERS = new HashMap<>();

    static {
        // Define kill streak tiers with rewards
        KILL_STREAK_TIERS.put(3, new KillStreakTier("Killing Spree", ChatColor.YELLOW, Sound.ENTITY_PLAYER_LEVELUP, 1.0f));
        KILL_STREAK_TIERS.put(5, new KillStreakTier("Rampage", ChatColor.GOLD, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f));
        KILL_STREAK_TIERS.put(7, new KillStreakTier("Dominating", ChatColor.RED, Sound.ENTITY_WITHER_SPAWN, 0.6f));
        KILL_STREAK_TIERS.put(10, new KillStreakTier("Unstoppable", ChatColor.DARK_RED, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f));
        KILL_STREAK_TIERS.put(15, new KillStreakTier("Godlike", ChatColor.DARK_PURPLE, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.2f));
        KILL_STREAK_TIERS.put(20, new KillStreakTier("LEGENDARY", ChatColor.LIGHT_PURPLE, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.1f));
    }

    /**
     * Kill streak tier definition
     */
    private static class KillStreakTier {
        final String name;
        final ChatColor color;
        final Sound sound;
        final float pitch;

        KillStreakTier(String name, ChatColor color, Sound sound, float pitch) {
            this.name = name;
            this.color = color;
            this.sound = sound;
            this.pitch = pitch;
        }
    }

    /**
     * PvP Rating tier system
     */
    public enum RatingTier {
        BRONZE(0, 799, "Bronze", ChatColor.GOLD),
        SILVER(800, 1199, "Silver", ChatColor.GRAY),
        GOLD(1200, 1599, "Gold", ChatColor.YELLOW),
        PLATINUM(1600, 1999, "Platinum", ChatColor.AQUA),
        DIAMOND(2000, 2399, "Diamond", ChatColor.BLUE),
        MASTER(2400, 2799, "Master", ChatColor.DARK_PURPLE),
        GRANDMASTER(2800, Integer.MAX_VALUE, "Grandmaster", ChatColor.LIGHT_PURPLE);

        final int minRating;
        final int maxRating;
        public final String name;
        public final ChatColor color;

        RatingTier(int minRating, int maxRating, String name, ChatColor color) {
            this.minRating = minRating;
            this.maxRating = maxRating;
            this.name = name;
            this.color = color;
        }

        public static RatingTier getTier(int rating) {
            for (RatingTier tier : values()) {
                if (rating >= tier.minRating && rating <= tier.maxRating) {
                    return tier;
                }
            }
            return BRONZE;
        }
    }

    /**
     * Kill result information
     */
    public static class KillResult {
        public final int ratingChange;
        public final int newRating;
        public final int killStreak;
        public final boolean isNewBestStreak;
        public final KillStreakTier streakTier;
        public final RatingTier ratingTier;
        public final boolean tierPromotion;

        public KillResult(int ratingChange, int newRating, int killStreak, boolean isNewBestStreak,
                          KillStreakTier streakTier, RatingTier ratingTier, boolean tierPromotion) {
            this.ratingChange = ratingChange;
            this.newRating = newRating;
            this.killStreak = killStreak;
            this.isNewBestStreak = isNewBestStreak;
            this.streakTier = streakTier;
            this.ratingTier = ratingTier;
            this.tierPromotion = tierPromotion;
        }
    }

    /**
     * Death result information
     */
    public static class DeathResult {
        public final int ratingChange;
        public final int newRating;
        public final int lostStreak;
        public final RatingTier ratingTier;
        public final boolean tierDemotion;

        public DeathResult(int ratingChange, int newRating, int lostStreak,
                           RatingTier ratingTier, boolean tierDemotion) {
            this.ratingChange = ratingChange;
            this.newRating = newRating;
            this.lostStreak = lostStreak;
            this.ratingTier = ratingTier;
            this.tierDemotion = tierDemotion;
        }
    }

    private PvPRatingManager() {
        this.logger = YakRealms.getInstance().getLogger();
        this.playerManager = YakPlayerManager.getInstance();
    }

    public static PvPRatingManager getInstance() {
        if (instance == null) {
            instance = new PvPRatingManager();
        }
        return instance;
    }

    /**
     * Initialize the PvP Rating Manager
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start cleanup task for old data
        startCleanupTask();

        logger.info("PvP Rating Manager enabled with comprehensive kill streak and rating system");
    }

    /**
     * Process a player kill and update ratings/streaks
     */
    public KillResult processKill(Player killer, Player victim) {
        if (killer == null || victim == null || killer.equals(victim)) {
            return null;
        }

        YakPlayer killerData = playerManager.getPlayer(killer);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (killerData == null || victimData == null) {
            logger.warning("Could not process kill: missing player data for " +
                    killer.getName() + " or " + victim.getName());
            return null;
        }

        UUID killerUUID = killer.getUniqueId();
        UUID victimUUID = victim.getUniqueId();

        // Check for kill spam protection
        if (isKillSpam(killerUUID, victim.getName())) {
            killer.sendMessage(ChatColor.RED + "⚠ Kill spam protection: Wait before killing " +
                    victim.getName() + " again for rating gain.");
            return null;
        }

        // Calculate rating changes
        int killerOldRating = killerData.getPvpRating();
        int victimOldRating = victimData.getPvpRating();

        RatingTier killerOldTier = RatingTier.getTier(killerOldRating);

        int ratingGain = calculateRatingGain(killerData, victimData, killer, victim);
        int ratingLoss = calculateRatingLoss(killerData, victimData);

        // Apply rating changes
        int newKillerRating = Math.min(MAX_RATING, Math.max(MIN_RATING, killerOldRating + ratingGain));
        int newVictimRating = Math.min(MAX_RATING, Math.max(MIN_RATING, victimOldRating - ratingLoss));

        killerData.setPvpRating(newKillerRating);
        victimData.setPvpRating(newVictimRating);

        // Update kill streak
        int currentStreak = killStreak.getOrDefault(killerUUID, 0) + 1;
        killStreak.put(killerUUID, currentStreak);
        lastKillTime.put(killerUUID, System.currentTimeMillis());

        // Update killer's stored kill streak and stats
        killerData.setKillStreak(currentStreak);
        boolean isNewBestStreak = currentStreak > killerData.getBestKillStreak();
        if (isNewBestStreak) {
            killerData.setBestKillStreak(currentStreak);
        }

        // Update kill counts
        killerData.setPlayerKills(killerData.getPlayerKills() + 1);

        // Track recent kills for spam protection
        trackRecentKill(killerUUID, victim.getName());

        // Check for tier promotion
        RatingTier newTier = RatingTier.getTier(newKillerRating);
        boolean tierPromotion = newTier.ordinal() > killerOldTier.ordinal();

        // Get streak tier if applicable
        KillStreakTier streakTier = KILL_STREAK_TIERS.get(currentStreak);

        // Save data
        playerManager.savePlayer(killerData);
        playerManager.savePlayer(victimData);

        // Create result
        KillResult result = new KillResult(ratingGain, newKillerRating, currentStreak,
                isNewBestStreak, streakTier, newTier, tierPromotion);

        // Announce the kill
        announceKill(killer, victim, result);

        logger.info("PvP Kill processed: " + killer.getName() + " killed " + victim.getName() +
                " | Rating: " + killerOldRating + " -> " + newKillerRating + " (+" + ratingGain + ")" +
                " | Streak: " + currentStreak);

        return result;
    }

    /**
     * Process a player death and reset streak
     */
    public DeathResult processDeath(Player victim, Player killer) {
        if (victim == null) {
            return null;
        }

        YakPlayer victimData = playerManager.getPlayer(victim);
        if (victimData == null) {
            return null;
        }

        UUID victimUUID = victim.getUniqueId();

        // Get old data
        int oldRating = victimData.getPvpRating();
        RatingTier oldTier = RatingTier.getTier(oldRating);
        int lostStreak = killStreak.getOrDefault(victimUUID, 0);

        // Reset kill streak
        killStreak.remove(victimUUID);
        lastKillTime.remove(victimUUID);
        victimData.setKillStreak(0);

        // Update death count
        victimData.setDeaths(victimData.getDeaths() + 1);

        // Rating loss is calculated in processKill for the killer
        // Here we just get the updated rating
        int newRating = victimData.getPvpRating();
        int ratingChange = newRating - oldRating;

        // Check for tier demotion
        RatingTier newTier = RatingTier.getTier(newRating);
        boolean tierDemotion = newTier.ordinal() < oldTier.ordinal();

        // Save data
        playerManager.savePlayer(victimData);

        // Create result
        DeathResult result = new DeathResult(ratingChange, newRating, lostStreak, newTier, tierDemotion);

        // Announce death effects
        announceDeath(victim, killer, result);

        logger.info("PvP Death processed: " + victim.getName() +
                " | Rating: " + oldRating + " -> " + newRating + " (" + ratingChange + ")" +
                " | Lost streak: " + lostStreak);

        return result;
    }

    /**
     * Calculate rating gain for a kill
     */
    private int calculateRatingGain(YakPlayer killerData, YakPlayer victimData, Player killer, Player victim) {
        int killerRating = killerData.getPvpRating();
        int victimRating = victimData.getPvpRating();

        // Base gain
        double gain = BASE_RATING_GAIN;

        // Rating difference modifier (gain more for killing higher rated players)
        double ratingDifference = victimRating - killerRating;
        gain += ratingDifference * RATING_DIFFERENCE_MULTIPLIER;

        // Kill streak bonus
        int currentStreak = killStreak.getOrDefault(killer.getUniqueId(), 0);
        if (currentStreak > 0) {
            gain += currentStreak * STREAK_BONUS_MULTIPLIER;
        }

        // Alignment bonus (killing lawful players gives more rating)
        if ("LAWFUL".equals(victimData.getAlignment())) {
            gain += gain * ALIGNMENT_MULTIPLIER;
        }

        // Victim streak bonus (killing someone on a streak gives more rating)
        int victimStreak = killStreak.getOrDefault(victim.getUniqueId(), 0);
        if (victimStreak >= 3) {
            gain += victimStreak * 2;
        }

        // Minimum and maximum bounds
        gain = Math.max(5, Math.min(100, gain));

        return (int) Math.round(gain);
    }

    /**
     * Calculate rating loss for a death
     */
    private int calculateRatingLoss(YakPlayer killerData, YakPlayer victimData) {
        int killerRating = killerData.getPvpRating();
        int victimRating = victimData.getPvpRating();

        // Base loss
        double loss = BASE_RATING_LOSS;

        // Rating difference modifier (lose less when killed by higher rated players)
        double ratingDifference = killerRating - victimRating;
        loss -= ratingDifference * RATING_DIFFERENCE_MULTIPLIER * 0.5;

        // Alignment modifier (chaotic players lose more)
        if ("CHAOTIC".equals(victimData.getAlignment())) {
            loss += loss * 0.1;
        }

        // Minimum and maximum bounds
        loss = Math.max(5, Math.min(80, loss));

        return (int) Math.round(loss);
    }

    /**
     * Check if this kill counts as spam
     */
    private boolean isKillSpam(UUID killerUUID, String victimName) {
        List<String> recent = recentKills.get(killerUUID);
        if (recent == null) {
            return false;
        }

        // Count recent kills of this victim
        long cutoff = System.currentTimeMillis() - KILL_SPAM_COOLDOWN;
        return recent.stream()
                .filter(kill -> kill.startsWith(victimName + ":"))
                .anyMatch(kill -> {
                    try {
                        long timestamp = Long.parseLong(kill.split(":")[1]);
                        return timestamp > cutoff;
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    /**
     * Track a recent kill for spam protection
     */
    private void trackRecentKill(UUID killerUUID, String victimName) {
        List<String> recent = recentKills.computeIfAbsent(killerUUID, k -> new ArrayList<>());

        // Add new kill
        recent.add(victimName + ":" + System.currentTimeMillis());

        // Keep only recent kills
        if (recent.size() > MAX_RECENT_KILLS) {
            recent.remove(0);
        }
    }

    /**
     * Announce a kill with proper formatting and effects
     */
    private void announceKill(Player killer, Player victim, KillResult result) {
        // Killer messages
        killer.sendMessage("");
        killer.sendMessage(ChatColor.GREEN + "§l✦ PLAYER KILL ✦");
        killer.sendMessage(ChatColor.GRAY + "Victim: " + ChatColor.WHITE + victim.getName());
        killer.sendMessage(ChatColor.GRAY + "Rating: " + ChatColor.YELLOW + result.newRating +
                ChatColor.GREEN + " (+" + result.ratingChange + ")");
        killer.sendMessage(ChatColor.GRAY + "Tier: " + result.ratingTier.color + result.ratingTier.name);
        killer.sendMessage(ChatColor.GRAY + "Kill Streak: " + ChatColor.YELLOW + result.killStreak);

        if (result.isNewBestStreak) {
            killer.sendMessage(ChatColor.GOLD + "§l★ NEW BEST STREAK! ★");
        }

        if (result.tierPromotion) {
            killer.sendMessage(ChatColor.LIGHT_PURPLE + "§l⬆ TIER PROMOTION! ⬆");
            killer.sendMessage(ChatColor.LIGHT_PURPLE + "You are now " + result.ratingTier.color + result.ratingTier.name);
        }

        killer.sendMessage("");

        // Play sounds and effects
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        if (result.tierPromotion) {
            killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Kill streak announcements
        if (result.streakTier != null) {
            announceKillStreak(killer, result);
        }

        // Victim notification
        YakPlayer victimData = playerManager.getPlayer(victim);
        if (victimData != null) {
            int victimRating = victimData.getPvpRating();
            RatingTier victimTier = RatingTier.getTier(victimRating);

            victim.sendMessage("");
            victim.sendMessage(ChatColor.RED + "§l✦ KILLED BY " + killer.getName().toUpperCase() + " ✦");
            victim.sendMessage(ChatColor.GRAY + "Your Rating: " + ChatColor.YELLOW + victimRating);
            victim.sendMessage(ChatColor.GRAY + "Your Tier: " + victimTier.color + victimTier.name);
            victim.sendMessage("");
        }
    }

    /**
     * Announce kill streak achievements
     */
    private void announceKillStreak(Player killer, KillResult result) {
        KillStreakTier tier = result.streakTier;

        // Server-wide announcement for significant streaks
        String announcement = tier.color + "§l" + killer.getName() + " is " + tier.name.toUpperCase() +
                "! (" + result.killStreak + " kills)";

        if (result.killStreak >= 7) {
            // Broadcast to all players
            Bukkit.broadcastMessage("");
            Bukkit.broadcastMessage(ChatColor.GOLD + "§l★═══════════════════════════════════════★");
            Bukkit.broadcastMessage(announcement);
            Bukkit.broadcastMessage(ChatColor.GOLD + "§l★═══════════════════════════════════════★");
            Bukkit.broadcastMessage("");

            // Play sound to all players
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), tier.sound, 1.0f, tier.pitch);
            }
        } else {
            // Just announce to killer
            killer.sendMessage(tier.color + "§l" + tier.name.toUpperCase() + "! (" + result.killStreak + " kills)");
            killer.playSound(killer.getLocation(), tier.sound, 1.0f, tier.pitch);
        }
    }

    /**
     * Announce death effects
     */
    private void announceDeath(Player victim, Player killer, DeathResult result) {
        if (result.lostStreak >= 3) {
            String message = ChatColor.RED + victim.getName() + "'s " + result.lostStreak +
                    " kill streak has been ended";

            if (killer != null) {
                message += " by " + killer.getName();
            }

            message += "!";

            if (result.lostStreak >= 7) {
                Bukkit.broadcastMessage(message);
            } else {
                victim.sendMessage(message);
                if (killer != null) {
                    killer.sendMessage(message);
                }
            }
        }

        if (result.tierDemotion) {
            victim.sendMessage(ChatColor.RED + "§l⬇ TIER DEMOTION ⬇");
            victim.sendMessage(ChatColor.RED + "You are now " + result.ratingTier.color + result.ratingTier.name);
        }
    }

    /**
     * Handle player death event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Process the death
        processDeath(victim, killer);

        // Process the kill if there's a killer
        if (killer != null && !killer.equals(victim)) {
            processKill(killer, victim);
        }
    }

    /**
     * Get current kill streak for a player
     */
    public int getCurrentKillStreak(Player player) {
        if (player == null) return 0;

        UUID uuid = player.getUniqueId();
        Long lastKill = lastKillTime.get(uuid);

        // Check if streak has timed out
        if (lastKill != null && System.currentTimeMillis() - lastKill > KILL_STREAK_TIMEOUT) {
            killStreak.remove(uuid);
            lastKillTime.remove(uuid);

            // Update stored streak
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setKillStreak(0);
                playerManager.savePlayer(yakPlayer);
            }

            return 0;
        }

        return killStreak.getOrDefault(uuid, 0);
    }

    /**
     * Get rating tier for a player
     */
    public RatingTier getRatingTier(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return RatingTier.BRONZE;
        }

        return RatingTier.getTier(yakPlayer.getPvpRating());
    }

    /**
     * Get formatted rating display for a player
     */
    public String getFormattedRating(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return ChatColor.GOLD + "Bronze" + ChatColor.GRAY + " (0)";
        }

        int rating = yakPlayer.getPvpRating();
        RatingTier tier = RatingTier.getTier(rating);

        return tier.color + tier.name + ChatColor.GRAY + " (" + rating + ")";
    }

    /**
     * Reset a player's kill streak (used for combat logging, etc.)
     */
    public void resetKillStreak(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        killStreak.remove(uuid);
        lastKillTime.remove(uuid);

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setKillStreak(0);
            playerManager.savePlayer(yakPlayer);
        }
    }

    /**
     * Apply rating penalty for combat logging
     */
    public void applyCombatLogPenalty(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        int penalty = Math.max(30, yakPlayer.getPvpRating() / 20);
        int newRating = Math.max(MIN_RATING, yakPlayer.getPvpRating() - penalty);

        yakPlayer.setPvpRating(newRating);
        playerManager.savePlayer(yakPlayer);

        // Reset kill streak
        resetKillStreak(player);

        logger.info("Applied combat log penalty to " + player.getName() + ": -" + penalty + " rating");
    }

    /**
     * Start cleanup task for old data
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long cutoff = System.currentTimeMillis() - KILL_STREAK_TIMEOUT;

                // Clean up expired kill streaks
                lastKillTime.entrySet().removeIf(entry -> entry.getValue() < cutoff);

                // Clean up recent kills older than spam cooldown
                long spamCutoff = System.currentTimeMillis() - KILL_SPAM_COOLDOWN * 2;
                recentKills.values().forEach(list ->
                        list.removeIf(kill -> {
                            try {
                                long timestamp = Long.parseLong(kill.split(":")[1]);
                                return timestamp < spamCutoff;
                            } catch (Exception e) {
                                return true;
                            }
                        })
                );

                // Remove empty entries
                recentKills.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 6000L, 6000L); // Every 5 minutes
    }

    /**
     * Get top players by rating
     */
    public List<Map.Entry<String, Integer>> getTopPlayersByRating(int limit) {
        Map<String, Integer> playerRatings = new HashMap<>();

        for (YakPlayer yakPlayer : playerManager.getOnlinePlayers()) {
            playerRatings.put(yakPlayer.getUsername(), yakPlayer.getPvpRating());
        }

        return playerRatings.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(ArrayList::new,
                        (list, entry) -> list.add(entry),
                        (list1, list2) -> list1.addAll(list2));
    }

    /**
     * Get player's PvP statistics summary
     */
    public String getPlayerStats(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return ChatColor.RED + "Player data not found.";
        }

        RatingTier tier = RatingTier.getTier(yakPlayer.getPvpRating());
        int currentStreak = getCurrentKillStreak(player);

        StringBuilder stats = new StringBuilder();
        stats.append(ChatColor.GOLD).append("§l=== PvP STATISTICS ===\n");
        stats.append(ChatColor.GRAY).append("Rating: ").append(tier.color).append(yakPlayer.getPvpRating())
                .append(" (").append(tier.name).append(")\n");
        stats.append(ChatColor.GRAY).append("Current Streak: ").append(ChatColor.YELLOW).append(currentStreak).append("\n");
        stats.append(ChatColor.GRAY).append("Best Streak: ").append(ChatColor.YELLOW).append(yakPlayer.getBestKillStreak()).append("\n");
        stats.append(ChatColor.GRAY).append("Total Kills: ").append(ChatColor.GREEN).append(yakPlayer.getPlayerKills()).append("\n");
        stats.append(ChatColor.GRAY).append("Total Deaths: ").append(ChatColor.RED).append(yakPlayer.getDeaths()).append("\n");

        if (yakPlayer.getDeaths() > 0) {
            double kdr = (double) yakPlayer.getPlayerKills() / yakPlayer.getDeaths();
            stats.append(ChatColor.GRAY).append("K/D Ratio: ").append(ChatColor.AQUA)
                    .append(String.format("%.2f", kdr)).append("\n");
        }

        return stats.toString();
    }

    public void onDisable() {
        // Clear memory maps
        killStreak.clear();
        lastKillTime.clear();
        recentKills.clear();

        logger.info("PvP Rating Manager disabled");
    }
}
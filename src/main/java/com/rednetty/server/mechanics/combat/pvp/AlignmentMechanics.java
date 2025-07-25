package com.rednetty.server.mechanics.combat.pvp;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.social.party.PartyScoreboards;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  AlignmentMechanics for Spigot 1.20.4
 * Focused on alignment management, PvP tagging, and safe zone enforcement
 * - Integrated with separated DeathMechanics and CombatLogoutMechanics
 * -  alignment timer management
 * - Improved safe zone handling
 * - Comprehensive health bar and healing system
 * - Streamlined PvP processing
 * - FIXED logging methods to use YakRealms.log(), YakRealms.warn(), YakRealms.error() properly
 */
public class AlignmentMechanics implements Listener {

    // Alignment timer constants from GDD
    private static final int NEUTRAL_SECONDS = 120;
    private static final int CHAOTIC_SECONDS = 300;

    // Health and UI management
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastSafeLocations = new HashMap<>();

    // Synchronization
    private final Object bossBarLock = new Object();

    // Dependencies
    private final YakPlayerManager playerManager;
    private final WorldGuardManager worldGuardManager;
    private final CombatLogoutMechanics combatLogoutMechanics;

    // Singleton instance
    private static AlignmentMechanics instance;

    /**
     * Get the singleton instance
     */
    public static AlignmentMechanics getInstance() {
        if (instance == null) {
            instance = new AlignmentMechanics();
        }
        return instance;
    }

    /**
     * Constructor initializes dependencies
     */
    private AlignmentMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.worldGuardManager = WorldGuardManager.getInstance();
        this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
    }

    /**
     * Generate a random spawn point for chaotic players
     */
    public static Location generateRandomSpawnPoint(String playerName) {
        World world = Bukkit.getWorlds().get(0);

        Random random = new Random(playerName.hashCode() + System.currentTimeMillis());
        double x = (random.nextDouble() - 0.5) * 2000;
        double z = (random.nextDouble() - 0.5) * 2000;

        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            Location loc = new Location(world, x, y, z);

            if (!isSafeZone(loc)) {
                return loc;
            }

            x = (random.nextDouble() - 0.5) * 2000;
            z = (random.nextDouble() - 0.5) * 2000;
        }

        return new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);
    }

    /**
     * Set a player to lawful alignment with scoreboard updates
     */
    public static void setLawfulAlignment(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        String oldAlignment = yakPlayer.getAlignment();
        yakPlayer.setAlignment("LAWFUL");
        YakPlayerManager.getInstance().savePlayer(yakPlayer);
        updatePlayerAlignment(player);

        TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "* YOU ARE NOW " + ChatColor.BOLD + "LAWFUL" + ChatColor.GREEN + " ALIGNMENT *");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "While lawful, you will not lose any equipped armor on death.");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Instead, all armor will lose 30% of its durability when you die. ");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Any players who kill you while you're lawfully aligned will become chaotic.");
        TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "* YOU ARE NOW " + ChatColor.BOLD + "LAWFUL" + ChatColor.GREEN + " ALIGNMENT *");

        if (!oldAlignment.equals("LAWFUL")) {
            PartyScoreboards.handleAlignmentChange(player);
        }
    }

    /**
     * Set a player to neutral alignment with scoreboard updates
     */
    public static void setNeutralAlignment(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        String oldAlignment = yakPlayer.getAlignment();
        yakPlayer.setAlignment("NEUTRAL");
        yakPlayer.setNeutralTime(System.currentTimeMillis() / 1000 + NEUTRAL_SECONDS);
        YakPlayerManager.getInstance().savePlayer(yakPlayer);
        updatePlayerAlignment(player);

        TextUtil.sendCenteredMessage(player, ChatColor.YELLOW + "* YOU ARE NOW " + ChatColor.BOLD + "NEUTRAL" + ChatColor.YELLOW + " ALIGNMENT *");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "While neutral, players who kill you will not become chaotic.");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "You have a 50% chance of dropping your weapon, and a 25% chance of dropping each piece of equipped armor on death.");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Neutral alignment will expire 2 minutes after last hit on player.");
        TextUtil.sendCenteredMessage(player, ChatColor.YELLOW + "* YOU ARE NOW " + ChatColor.BOLD + "NEUTRAL" + ChatColor.YELLOW + " ALIGNMENT *");

        if (!oldAlignment.equals("NEUTRAL")) {
            PartyScoreboards.handleAlignmentChange(player);
        }
    }

    /**
     * Set a player to chaotic alignment with scoreboard updates
     */
    public static void setChaoticAlignment(Player player, int time) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        String oldAlignment = yakPlayer.getAlignment();
        yakPlayer.setAlignment("CHAOTIC");
        yakPlayer.setChaoticTime(System.currentTimeMillis() / 1000 + time);
        YakPlayerManager.getInstance().savePlayer(yakPlayer);

        TextUtil.sendCenteredMessage(player, ChatColor.RED + "* YOU ARE NOW " + ChatColor.BOLD + "CHAOTIC" + ChatColor.RED + " ALIGNMENT *");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "While chaotic, you cannot enter any major cities or safe zones. If you are killed while chaotic, you will lose everything in your inventory. Chaotic alignment will expire 5 minutes after your last player kill.");
        TextUtil.sendCenteredMessage(player, ChatColor.RED + "* YOU ARE NOW " + ChatColor.BOLD + "CHAOTIC" + ChatColor.RED + " ALIGNMENT *");

        updatePlayerAlignment(player);

        if (!oldAlignment.equals("CHAOTIC")) {
            PartyScoreboards.handleAlignmentChange(player);
        }
    }

    /**
     * Update player display name based on alignment with  scoreboard integration
     */
    public static void updatePlayerAlignment(Player player) {
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer == null) return;

            ChatColor cc = ChatColor.GRAY;
            Rank rank = ModerationMechanics.getRank(player);

            if (rank == Rank.DEV) {
                cc = ChatColor.GOLD;
            } else if (rank == Rank.MANAGER) {
                cc = ChatColor.YELLOW;
            } else if (rank == Rank.GM) {
                cc = ChatColor.AQUA;
            } else if ("NEUTRAL".equals(yakPlayer.getAlignment())) {
                cc = ChatColor.YELLOW;
            } else if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
                cc = ChatColor.RED;
            }

            player.setDisplayName(cc + player.getName());

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    PartyScoreboards.handleAlignmentChange(player);
                } catch (Exception e) {
                    YakRealms.warn("Error updating scoreboards after alignment change for " + player.getName() + ": " + e.getMessage());
                }
            }, 1L);

            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_INFECT, 10.0f, 1.0f);
                } catch (Exception e) {
                    YakRealms.warn("Failed to play alignment update sound for " + player.getName());
                }
            });

            YakRealms.log("Updated alignment for player " + player.getName() + " to " + cc.name());
        } catch (Exception e) {
            YakRealms.error("Error updating alignment for player " + player.getName(), e);
        }
    }

    /**
     * Initialize the alignment system
     */
    public void onEnable() {
        instance = this;
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Initialize WorldGuard manager
        WorldGuardManager.getInstance();

        // Initialize force field manager
        ForceFieldManager.getInstance().onEnable();

        // Start alignment and health management task
        startAlignmentTask();

        YakRealms.log("AlignmentMechanics enabled with integrated combat systems for Spigot 1.20.4");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Clean up boss bars
        synchronized (bossBarLock) {
            for (BossBar bar : playerBossBars.values()) {
                try {
                    bar.removeAll();
                } catch (Exception e) {
                    YakRealms.warn("Error removing boss bar: " + e.getMessage());
                }
            }
            playerBossBars.clear();
        }

        // Clean up tracking maps
        lastSafeLocations.clear();

        // Disable subsystems
        ForceFieldManager.getInstance().onDisable();

        YakRealms.log("AlignmentMechanics disabled");
    }

    /**
     * Start the main alignment and health management task
     */
    private void startAlignmentTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isOnline()) continue;

                    YakPlayer yakPlayer = playerManager.getPlayer(player);
                    if (yakPlayer == null) continue;

                    try {
                        // Handle chaotic players in safe zones
                        if (isSafeZone(player.getLocation()) && isPlayerChaotic(yakPlayer)) {
                            kickFromSafeZone(player);
                            continue;
                        }

                        // Update alignment timers
                        updateAlignmentTimers(player, yakPlayer);

                        // Update health display
                        updateHealthBar(player);

                        // Heal players not in combat
                        if (!combatLogoutMechanics.isPlayerTagged(player)) {
                            healPlayer(player);
                        }
                    } catch (Exception e) {
                        YakRealms.warn("Error in alignment task for " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Run every second
    }

    /**
     * Get appropriate boss bar color based on health percentage
     */
    private BarColor getHealthBarColor(Player player) {
        double healthPercentage = player.getHealth() / player.getMaxHealth();

        if (healthPercentage > 0.5) {
            return BarColor.GREEN;
        } else if (healthPercentage > 0.25) {
            return BarColor.YELLOW;
        } else {
            return BarColor.RED;
        }
    }

    /**
     * Get appropriate text color based on health percentage
     */
    private ChatColor getHealthTextColor(Player player) {
        double healthPercentage = player.getHealth() / player.getMaxHealth();

        if (healthPercentage > 0.5) {
            return ChatColor.GREEN;
        } else if (healthPercentage > 0.25) {
            return ChatColor.YELLOW;
        } else {
            return ChatColor.RED;
        }
    }

    /**
     * Kick a chaotic player from a safe zone
     */
    private void kickFromSafeZone(Player player) {
        TextUtil.sendCenteredMessage(player, ChatColor.RED + "The guards have kicked you out of the " +
                ChatColor.UNDERLINE + "protected area" + ChatColor.RED +
                " due to your chaotic alignment.");

        Location spawnPoint = generateRandomSpawnPoint(player.getName());
        player.teleport(spawnPoint);
    }

    /**
     * Update alignment timers based on GDD specifications
     */
    private void updateAlignmentTimers(Player player, YakPlayer yakPlayer) {
        String oldAlignment = yakPlayer.getAlignment();

        // Update chaotic timer
        if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
            int timeLeft = yakPlayer.getChaoticTime() > 0 ?
                    (int) (yakPlayer.getChaoticTime() - (System.currentTimeMillis() / 1000)) : 0;

            if (timeLeft <= 0) {
                // Convert to neutral
                yakPlayer.setAlignment("NEUTRAL");
                yakPlayer.setNeutralTime(System.currentTimeMillis() / 1000 + NEUTRAL_SECONDS);
                playerManager.savePlayer(yakPlayer);
                updatePlayerAlignment(player);
                setNeutralAlignment(player);

                handleAlignmentChanged(player, oldAlignment, "NEUTRAL");
            }
        }

        // Update neutral timer
        else if ("NEUTRAL".equals(yakPlayer.getAlignment())) {
            int timeLeft = yakPlayer.getNeutralTime() > 0 ?
                    (int) (yakPlayer.getNeutralTime() - (System.currentTimeMillis() / 1000)) : 0;

            if (timeLeft <= 0) {
                // Convert to lawful
                yakPlayer.setAlignment("LAWFUL");
                playerManager.savePlayer(yakPlayer);
                updatePlayerAlignment(player);
                setLawfulAlignment(player);

                handleAlignmentChanged(player, oldAlignment, "LAWFUL");
            }
        }
    }

    /**
     * Check if a location is in a safe zone
     */
    public static boolean isSafeZone(Location location) {
        return WorldGuardManager.getInstance().isSafeZone(location);
    }

    /**
     * Handle alignment changes and update all relevant scoreboards
     */
    private void handleAlignmentChanged(Player player, String oldAlignment, String newAlignment) {
        updatePlayerAlignment(player);
        PartyScoreboards.handleAlignmentChange(player);
        YakRealms.log("Player " + player.getName() + " alignment changed from " + oldAlignment + " to " + newAlignment);
    }

    /**
     * Apply passive healing to players not in combat using PlayerStatsCalculator
     */
    private void healPlayer(Player player) {
        try {
            if (player.isDead() || player.getHealth() >= player.getMaxHealth()) {
                return;
            }

            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null && yakPlayer.hasTemporaryData("prevent_healing")) {
                long preventUntil = (long) yakPlayer.getTemporaryData("prevent_healing");
                if (System.currentTimeMillis() < preventUntil) {
                    return;
                } else {
                    yakPlayer.removeTemporaryData("prevent_healing");
                }
            }

            int healAmount = PlayerStatsCalculator.calculateTotalHealthRegen(player);
            double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healAmount);
            player.setHealth(newHealth);

        } catch (Exception e) {
            YakRealms.warn("Error healing player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Update a player's health bar display with proper synchronization
     */
    private void updateHealthBar(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            double healthPercentage = player.getHealth() / player.getMaxHealth();
            float progress = Math.max(0.0f, Math.min(1.0f, (float) healthPercentage));

            BarColor barColor = getHealthBarColor(player);
            ChatColor titleColor = getHealthTextColor(player);

            String safeZoneText = isSafeZone(player.getLocation()) ?
                    ChatColor.GRAY + " - " + ChatColor.GREEN + ChatColor.BOLD + "SAFE-ZONE" : "";

            // Add combat timer if in combat
            String combatText = "";
            if (combatLogoutMechanics.isPlayerTagged(player)) {
                int combatTime = combatLogoutMechanics.getCombatTimeRemaining(player);
                combatText = ChatColor.GRAY + " - " + ChatColor.RED + ChatColor.BOLD + "COMBAT " + combatTime + "s";
            }

            String title = titleColor + "" + ChatColor.BOLD + "HP " +
                    titleColor + (int) player.getHealth() + ChatColor.BOLD + " / " +
                    titleColor + (int) player.getMaxHealth() + safeZoneText + combatText;

            synchronized (bossBarLock) {
                BossBar bossBar = playerBossBars.get(playerId);

                if (bossBar == null) {
                    bossBar = Bukkit.createBossBar(title, barColor, BarStyle.SOLID);
                    bossBar.addPlayer(player);
                    playerBossBars.put(playerId, bossBar);
                } else {
                    bossBar.setTitle(title);
                    bossBar.setColor(barColor);

                    if (!bossBar.getPlayers().contains(player)) {
                        bossBar.addPlayer(player);
                    }
                }

                bossBar.setProgress(progress);
            }

        } catch (Exception e) {
            YakRealms.warn("Error updating health bar for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Safely remove boss bar for a player
     */
    private void removeBossBar(UUID playerId) {
        synchronized (bossBarLock) {
            BossBar existingBar = playerBossBars.remove(playerId);
            if (existingBar != null) {
                try {
                    existingBar.removeAll();
                } catch (Exception e) {
                    YakRealms.warn("Error removing boss bar for player " + playerId + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Get player's alignment as a string with color code
     */
    public static String getAlignmentString(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return "&aLAWFUL";

        String alignment = yakPlayer.getAlignment();
        if ("CHAOTIC".equals(alignment)) return "&cCHAOTIC";
        if ("NEUTRAL".equals(alignment)) return "&eNEUTRAL";
        return "&aLAWFUL";
    }

    /**
     * Get remaining alignment time for a player
     */
    public static int getAlignmentTime(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return 0;

        String alignment = yakPlayer.getAlignment();
        if ("CHAOTIC".equals(alignment)) {
            long remainingTime = yakPlayer.getChaoticTime() - (System.currentTimeMillis() / 1000);
            return (int) Math.max(0, remainingTime);
        } else if ("NEUTRAL".equals(alignment)) {
            long remainingTime = yakPlayer.getNeutralTime() - (System.currentTimeMillis() / 1000);
            return (int) Math.max(0, remainingTime);
        }

        return 0;
    }

    /**
     * Check if a player is chaotic-aligned
     */
    public static boolean isPlayerChaotic(YakPlayer yakPlayer) {
        return "CHAOTIC".equals(yakPlayer.getAlignment());
    }

    /**
     * Check if a player is lawful-aligned
     */
    public boolean isPlayerLawful(YakPlayer yakPlayer) {
        return "LAWFUL".equals(yakPlayer.getAlignment());
    }

    /**
     * Check if a player is neutral-aligned
     */
    public boolean isPlayerNeutral(YakPlayer yakPlayer) {
        return "NEUTRAL".equals(yakPlayer.getAlignment());
    }

    /**
     * Handle zone notifications when moving
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onZoneChange(PlayerMoveEvent event) {
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) return;

        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        boolean fromSafe = isSafeZone(event.getFrom());
        boolean toSafe = isSafeZone(event.getTo());

        if (!fromSafe && toSafe) {
            if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
                handleChaoticEnteringSafeZone(player, event);
                return;
            }

            if (combatLogoutMechanics.isPlayerTagged(player)) {
                handleCombatTaggedEnteringSafeZone(player, event);
                return;
            }

            lastSafeLocations.put(player.getUniqueId(), event.getTo().clone());
            notifyPlayerEnteredSafeZone(player);
        }

        if (fromSafe && !toSafe) {
            lastSafeLocations.put(player.getUniqueId(), event.getFrom().clone());
            notifyPlayerExitedSafeZone(player);
        }
    }

    /**
     * Handle player attempting to enter safe zone with chaotic alignment
     */
    private void handleChaoticEnteringSafeZone(Player player, PlayerMoveEvent event) {
        event.setCancelled(true);

        Location fallbackLocation = lastSafeLocations.getOrDefault(
                player.getUniqueId(),
                event.getFrom().clone()
        );

        if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            player.teleport(fallbackLocation);
        }

        TextUtil.sendCenteredMessage(player, ChatColor.RED + "You " +
                ChatColor.UNDERLINE + "cannot" +
                ChatColor.RED + " enter " +
                ChatColor.BOLD + "NON-PVP" +
                ChatColor.RED + " zones with a chaotic alignment.");
    }

    /**
     * Handle player attempting to enter safe zone while combat tagged
     */
    private void handleCombatTaggedEnteringSafeZone(Player player, PlayerMoveEvent event) {
        event.setCancelled(true);

        Location fallbackLocation = lastSafeLocations.getOrDefault(
                player.getUniqueId(),
                event.getFrom().clone()
        );

        if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            player.teleport(fallbackLocation);
        }

        int timeLeft = combatLogoutMechanics.getCombatTimeRemaining(player);
        TextUtil.sendCenteredMessage(player, ChatColor.RED + "You " +
                ChatColor.UNDERLINE + "cannot" +
                ChatColor.RED + " enter a safe zone while in combat.");
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Out of combat in: " +
                ChatColor.BOLD + timeLeft + "s");
    }

    /**
     * Notify player they've entered a safe zone
     */
    private void notifyPlayerEnteredSafeZone(Player player) {
        TextUtil.sendCenteredMessage(player, ChatColor.GREEN.toString() +
                ChatColor.BOLD + "*** SAFE ZONE (DMG-OFF)***");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.25f, 0.3f);
    }

    /**
     * Notify player they've exited a safe zone
     */
    private void notifyPlayerExitedSafeZone(Player player) {
        TextUtil.sendCenteredMessage(player, ChatColor.RED.toString() +
                ChatColor.BOLD + "*** CHAOTIC ZONE (PVP-ON)***");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.25f, 0.3f);
    }

    /**
     * Handle player join/login with proper scoreboard setup
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) return;

                updatePlayerAlignment(player);
                PartyScoreboards.updatePlayerTeamAssignments(player);
                PartyScoreboards.handleAlignmentChange(player);
            }
        }.runTaskLater(YakRealms.getInstance(), 3L);
    }

    /**
     * Handle player quit with cleanup
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clean up UI elements
        removeBossBar(playerId);
        lastSafeLocations.remove(playerId);
        ForceFieldManager.getInstance().removePlayerForceField(player);
        PartyScoreboards.cleanupPlayer(player);
    }

    /**
     * Handle player PvP and alignment changes with integrated combat logout system
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0.0) {
            return;
        }

        Player attacker = null;
        Player victim = null;

        // Handle projectile damage
        if (event.getDamager() instanceof Projectile && event.getEntity() instanceof Player) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
                victim = (Player) event.getEntity();
            }
        }

        // Handle direct damage
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            attacker = (Player) event.getDamager();
            victim = (Player) event.getEntity();
        }

        if (attacker != null && victim != null) {
            handlePvPAttack(attacker, victim);
        }
    }

    /**
     * Process a PvP attack between players with alignment updates and combat tagging
     */
    private void handlePvPAttack(Player attacker, Player victim) {
        YakPlayer yakAttacker = playerManager.getPlayer(attacker);
        YakPlayer yakVictim = playerManager.getPlayer(victim);

        if (yakAttacker == null || yakVictim == null) return;

        if (Toggles.isToggled(attacker, "Anti PVP")) return;

        // Cancel any logout commands
        try {
            Class<?> logoutCommandClass = Class.forName("com.rednetty.server.commands.player.LogoutCommand");
            java.lang.reflect.Method forceCancelMethod = logoutCommandClass.getMethod("forceCancelLogout", Player.class, String.class);

            forceCancelMethod.invoke(null, attacker, "You entered combat!");
            forceCancelMethod.invoke(null, victim, "You were attacked!");

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().fine("Could not cancel logout (command not available): " + e.getMessage());
        }

        // Handle alignment changes
        String oldAlignment = yakAttacker.getAlignment();

        if ("CHAOTIC".equals(yakAttacker.getAlignment())) {
            // Already chaotic - do nothing
        } else if ("NEUTRAL".equals(yakAttacker.getAlignment())) {
            yakAttacker.setNeutralTime(System.currentTimeMillis() / 1000 + NEUTRAL_SECONDS);
            playerManager.savePlayer(yakAttacker);
        } else {
            setNeutralAlignment(attacker);

            if (!oldAlignment.equals("NEUTRAL")) {
                PartyScoreboards.handleAlignmentChange(attacker);
            }
        }

        // Tag both players for combat using the combat logout system
        combatLogoutMechanics.markCombatTagged(attacker);
        combatLogoutMechanics.markCombatTagged(victim);
        combatLogoutMechanics.setLastAttacker(victim, attacker);

        YakRealms.log("PvP attack processed: " + attacker.getName() + " -> " + victim.getName());
    }

    /**
     * Handle player death and alignment changes with scoreboard updates
     * This integrates with the new DeathMechanics system
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeathForAlignment(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getEntity();
        YakPlayer yakVictim = playerManager.getPlayer(victim);
        if (yakVictim == null) return;

        Player killer = victim.getKiller();
        if (killer == null) return;

        YakPlayer yakKiller = playerManager.getPlayer(killer);
        if (yakKiller == null) return;

        // Only handle alignment changes for lawful victims being killed
        if (!"LAWFUL".equals(yakVictim.getAlignment())) {
            return;
        }

        String oldKillerAlignment = yakKiller.getAlignment();
        if ("CHAOTIC".equals(yakKiller.getAlignment())) {
            // Reset chaotic timer
            yakKiller.setChaoticTime(System.currentTimeMillis() / 1000 + CHAOTIC_SECONDS);
            playerManager.savePlayer(yakKiller);
            TextUtil.sendCenteredMessage(killer, ChatColor.RED + "Player slain, chaotic timer reset.");
        } else {
            // Make killer chaotic
            setChaoticAlignment(killer, CHAOTIC_SECONDS);

            if (!oldKillerAlignment.equals("CHAOTIC")) {
                PartyScoreboards.handleAlignmentChange(killer);
            }
        }

        YakRealms.log("Alignment change processed for kill: " + killer.getName() + " killed lawful " + victim.getName());
    }

    /**
     * Prevent portal teleportation
     */
    @EventHandler
    public void onPortalUse(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    // Public API methods for integration

    /**
     * Public method to force cleanup of a player's boss bar
     */
    public void cleanupPlayerBossBar(Player player) {
        if (player != null) {
            removeBossBar(player.getUniqueId());
        }
    }

    /**
     * Public method to force update of a player's health bar
     */
    public void forceUpdateHealthBar(Player player) {
        if (player != null && player.isOnline() && !player.isDead()) {
            updateHealthBar(player);
        }
    }

    /**
     * Check if a player is combat logging out (delegates to CombatLogoutMechanics)
     */
    public boolean isCombatLoggingOut(Player player) {
        return combatLogoutMechanics.isCombatLoggingOut(player);
    }

    /**
     * Check if player is combat tagged (delegates to CombatLogoutMechanics)
     */
    public boolean isPlayerTagged(Player player) {
        return combatLogoutMechanics.isPlayerTagged(player);
    }

    /**
     * Get combat time remaining (delegates to CombatLogoutMechanics)
     */
    public int getCombatTimeRemaining(Player player) {
        return combatLogoutMechanics.getCombatTimeRemaining(player);
    }
}
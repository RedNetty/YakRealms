package com.rednetty.server.mechanics.combat.pvp;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.party.PartyScoreboards;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXED: Manages player alignment system with proper scoreboard integration
 * - Fixed scoreboard duplication when alignment changes
 * - Proper integration with PartyScoreboards for color updates
 * - Enhanced boss bar management and health regeneration
 */
public class AlignmentMechanics implements Listener {

    // Alignment timer constants
    private static final int NEUTRAL_SECONDS = 120;
    private static final int CHAOTIC_SECONDS = 300;
    private static final long COMBAT_TAG_DURATION = 10000; // 10 seconds in milliseconds

    // Combat tracking maps
    private final ConcurrentHashMap<String, Long> taggedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastSafeLocations = new HashMap<>();
    private final Map<String, Player> lastAttackers = new HashMap<>();

    
    private final Object bossBarLock = new Object();

    // Dependencies
    private final YakPlayerManager playerManager;
    private final WorldGuardManager worldGuardManager;
    public static boolean logout = false;

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

        // Start alignment task
        startAlignmentTask();

        YakRealms.log("Alignment mechanics have been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        
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

        // Disable force field manager
        ForceFieldManager.getInstance().onDisable();

        YakRealms.log("Alignment mechanics have been disabled.");
    }

    /**
     * Start the main alignment update task
     */
    private void startAlignmentTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isOnline()) continue;

                    YakPlayer yakPlayer = playerManager.getPlayer(player);
                    if (yakPlayer == null) continue;

                    // Handle chaotic players in safe zones
                    if (isSafeZone(player.getLocation()) && isPlayerChaotic(yakPlayer)) {
                        kickFromSafeZone(player);
                        continue;
                    }

                    // Update alignment timers
                    updateTimers(player, yakPlayer);

                    // Update health display
                    updateHealthBar(player);

                    // Heal players not in combat
                    if (!isPlayerTagged(player)) {
                        healPlayer(player);
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 20L); // Run every second
    }

    private void updateTimers(Player player, YakPlayer yakPlayer) {
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
     * FIXED: Handle alignment changes and update all relevant scoreboards
     */
    private void handleAlignmentChanged(Player player, String oldAlignment, String newAlignment) {
        // Update the player's display name immediately
        updatePlayerAlignment(player);

        
        PartyScoreboards.handleAlignmentChange(player);

        // Log the change
        YakRealms.log("Player " + player.getName() + " alignment changed from " + oldAlignment + " to " + newAlignment);
    }

    /**
     * Check if a player is currently combat tagged
     */
    public boolean isPlayerTagged(Player player) {
        return taggedPlayers.containsKey(player.getName()) &&
                System.currentTimeMillis() - taggedPlayers.get(player.getName()) <= COMBAT_TAG_DURATION;
    }

    /**
     * Get time since player was last tagged
     */
    public long getTimeSinceLastTag(Player player) {
        if (taggedPlayers.containsKey(player.getName())) {
            return System.currentTimeMillis() - taggedPlayers.get(player.getName());
        }
        return Long.MAX_VALUE;
    }

    /**
     * Mark a player as combat tagged
     */
    public void markCombatTagged(Player player) {
        taggedPlayers.put(player.getName(), System.currentTimeMillis());
    }

    /**
     * Clear a player's combat tag
     */
    public void clearCombatTag(Player player) {
        taggedPlayers.remove(player.getName());
    }

    /**
     * Apply passive healing to players not in combat using PlayerStatsCalculator
     */
    private void healPlayer(Player player) {
        try {
            // Skip if player is dead or at full health
            if (player.isDead() || player.getHealth() >= player.getMaxHealth()) {
                return;
            }

            // Use PlayerStatsCalculator for HP regen calculation
            int healAmount = PlayerStatsCalculator.calculateTotalHealthRegen(player);

            // Apply healing (capped at max health)
            double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healAmount);
            player.setHealth(newHealth);

        } catch (Exception e) {
            YakRealms.warn("Error healing player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * FIXED: Update a player's health bar display with proper synchronization
     */
    private void updateHealthBar(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        try {
            // Calculate health percentage
            double healthPercentage = player.getHealth() / player.getMaxHealth();
            float progress = Math.max(0.0f, Math.min(1.0f, (float) healthPercentage));

            // Determine bar color based on health
            BarColor barColor = getHealthBarColor(player);
            ChatColor titleColor = getHealthTextColor(player);

            // Create bar title with formatting
            String safeZoneText = isSafeZone(player.getLocation()) ?
                    ChatColor.GRAY + " - " + ChatColor.GREEN + ChatColor.BOLD + "SAFE-ZONE" : "";

            String title = titleColor + "" + ChatColor.BOLD + "HP " +
                    titleColor + (int) player.getHealth() + ChatColor.BOLD + " / " +
                    titleColor + (int) player.getMaxHealth() + safeZoneText;

            
            synchronized (bossBarLock) {
                BossBar bossBar = playerBossBars.get(playerId);

                if (bossBar == null) {
                    // Create new boss bar
                    bossBar = Bukkit.createBossBar(title, barColor, BarStyle.SOLID);
                    bossBar.addPlayer(player);
                    playerBossBars.put(playerId, bossBar);
                } else {
                    // Update existing boss bar
                    bossBar.setTitle(title);
                    bossBar.setColor(barColor);

                    
                    if (!bossBar.getPlayers().contains(player)) {
                        bossBar.addPlayer(player);
                    }
                }

                // Update progress
                bossBar.setProgress(progress);
            }

        } catch (Exception e) {
            YakRealms.warn("Error updating health bar for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * FIXED: Safely remove boss bar for a player
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

        // Teleport to random spawn point
        Location spawnPoint = generateRandomSpawnPoint(player.getName());
        player.teleport(spawnPoint);
    }

    /**
     * Generate a random spawn point for chaotic players
     */
    public static Location generateRandomSpawnPoint(String playerName) {
        World world = Bukkit.getWorlds().get(0); // Main world

        // Get random coordinates in the wilderness
        Random random = new Random(playerName.hashCode() + System.currentTimeMillis());
        double x = (random.nextDouble() - 0.5) * 2000;
        double z = (random.nextDouble() - 0.5) * 2000;

        // Ensure spawn is not in a safe zone
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            Location loc = new Location(world, x, y, z);

            if (!isSafeZone(loc)) {
                return loc;
            }

            // Try different coordinates
            x = (random.nextDouble() - 0.5) * 2000;
            z = (random.nextDouble() - 0.5) * 2000;
        }

        // Fallback to a reasonable spawn
        return new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);
    }

    /**
     * Check if a location is in a safe zone
     */
    public static boolean isSafeZone(Location location) {
        return WorldGuardManager.getInstance().isSafeZone(location);
    }

    /**
     * FIXED: Set a player to lawful alignment with scoreboard updates
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
     * FIXED: Set a player to neutral alignment with scoreboard updates
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
     * FIXED: Set a player to chaotic alignment with scoreboard updates
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
     * FIXED: Update player display name based on alignment with enhanced scoreboard integration
     */
    public static void updatePlayerAlignment(Player player) {
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            if (yakPlayer == null) return;

            ChatColor cc = ChatColor.GRAY; // Default lawful color
            Rank rank = ModerationMechanics.getRank(player);

            // Set color based on rank and alignment
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

            // Update player display name
            player.setDisplayName(cc + player.getName());

            
            // This is crucial for proper team color display
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    // Update team assignments for all players to see the correct color
                    PartyScoreboards.handleAlignmentChange(player);
                } catch (Exception e) {
                    YakRealms.warn("Error updating scoreboards after alignment change for " + player.getName() + ": " + e.getMessage());
                }
            }, 1L); // Small delay to ensure display name is set first

            // Play sound effect
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
     * Handle player respawn based on alignment
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
            // Chaotic players get random spawn
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "You have been sent to a random location due to your chaotic alignment.");
            event.setRespawnLocation(generateRandomSpawnPoint(player.getName()));
        } else {
            // Lawful/neutral players get safe spawn
            World world = Bukkit.getWorlds().get(0);
            event.setRespawnLocation(world.getSpawnLocation());
        }
    }

    /**
     * Prevent portal teleportation
     */
    @EventHandler
    public void onPortalUse(PlayerPortalEvent event) {
        event.setCancelled(true);
    }

    /**
     * Handle zone notifications when moving
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onZoneChange(PlayerMoveEvent event) {
        // Skip if not actually changing blocks to avoid unnecessary processing
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) return;

        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        // Check for entering safe zone
        boolean fromSafe = isSafeZone(event.getFrom());
        boolean toSafe = isSafeZone(event.getTo());

        if (!fromSafe && toSafe) {
            // Prevent chaotic players from entering
            if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
                handleChaoticEnteringSafeZone(player, event);
                return;
            }

            // Prevent players in combat from entering
            if (isPlayerTagged(player)) {
                handleCombatTaggedEnteringSafeZone(player, event);
                return;
            }

            // Update last safe location
            lastSafeLocations.put(player.getUniqueId(), event.getTo().clone());

            // Notify player they've entered a safe zone
            notifyPlayerEnteredSafeZone(player);
        }

        // Check for exiting safe zone
        if (fromSafe && !toSafe) {
            // Save last safe location
            lastSafeLocations.put(player.getUniqueId(), event.getFrom().clone());

            // Notify player they've exited the safe zone
            notifyPlayerExitedSafeZone(player);
        }
    }

    /**
     * Handle player attempting to enter safe zone with chaotic alignment
     */
    private void handleChaoticEnteringSafeZone(Player player, PlayerMoveEvent event) {
        // Cancel the movement
        event.setCancelled(true);

        // Get last safe non-safe zone location or use current location
        Location fallbackLocation = lastSafeLocations.getOrDefault(
                player.getUniqueId(),
                event.getFrom().clone()
        );

        // Teleport back if the event cancellation doesn't work
        if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            player.teleport(fallbackLocation);
        }

        // Notify the player
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
        // Cancel the movement
        event.setCancelled(true);

        // Get last safe non-safe zone location or use current location
        Location fallbackLocation = lastSafeLocations.getOrDefault(
                player.getUniqueId(),
                event.getFrom().clone()
        );

        // Teleport back if the event cancellation doesn't work
        if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            player.teleport(fallbackLocation);
        }

        // Calculate remaining combat time
        long combatTime = getTimeSinceLastTag(player);
        int timeLeft = (int) ((COMBAT_TAG_DURATION - combatTime) / 1000);
        timeLeft = Math.max(0, timeLeft);

        // Notify the player
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
     * FIXED: Handle player death and alignment changes with scoreboard updates
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        YakPlayer yakVictim = playerManager.getPlayer(victim);
        if (yakVictim == null) return;

        if (logout) {
            logout = false;
            return;
        }

        // Get the killer
        Player killer = victim.getKiller();
        if (killer == null) return;

        YakPlayer yakKiller = playerManager.getPlayer(killer);
        if (yakKiller == null) return;

        // Skip neutral/chaotic victims (don't make killer chaotic)
        if ("NEUTRAL".equals(yakVictim.getAlignment()) ||
                "CHAOTIC".equals(yakVictim.getAlignment())) {
            return;
        }

        // Handle killer alignment change
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
    }

    /**
     * Handle custom death messages
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathMessage(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim == null) return;

        // Clear default death message
        event.setDeathMessage(null);

        // Build custom death message
        String deathReason = " has died";
        EntityDamageEvent lastDamage = victim.getLastDamageCause();

        if (lastDamage != null) {
            EntityDamageEvent.DamageCause cause = lastDamage.getCause();

            // Determine death reason by cause
            if (cause == EntityDamageEvent.DamageCause.LAVA ||
                    cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
                deathReason = " burned to death";
            } else if (cause == EntityDamageEvent.DamageCause.SUICIDE) {
                deathReason = " ended their own life";
            } else if (cause == EntityDamageEvent.DamageCause.FALL) {
                deathReason = " fell to their death";
            } else if (cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
                deathReason = " was crushed to death";
            } else if (cause == EntityDamageEvent.DamageCause.DROWNING) {
                deathReason = " drowned to death";
            }

            // Handle entity damage
            if (lastDamage instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent entityDamage = (EntityDamageByEntityEvent) lastDamage;
                Entity damager = entityDamage.getDamager();

                if (damager instanceof Player) {
                    Player killer = (Player) damager;
                    deathReason = " was killed by " + ChatColor.RESET + killer.getDisplayName();

                    // Include weapon if available
                    ItemStack weapon = killer.getInventory().getItemInMainHand();
                    if (weapon != null && weapon.getType() != Material.AIR) {
                        deathReason = " was killed by " + killer.getDisplayName() + ChatColor.WHITE + " with a(n) ";

                        // Add weapon name
                        if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
                            deathReason += weapon.getItemMeta().getDisplayName();
                        } else {
                            deathReason += TextUtil.formatItemName(weapon.getType().name());
                        }
                    }

                    // Track kill statistics
                    YakPlayer yakKiller = playerManager.getPlayer(killer);
                    if (yakKiller != null) {
                        yakKiller.addPlayerKill();
                        playerManager.savePlayer(yakKiller);
                    }

                } else if (damager instanceof LivingEntity) {
                    LivingEntity livingDamager = (LivingEntity) damager;
                    String mobName = livingDamager.getType().name();

                    // Get custom mob name if available
                    if (livingDamager.hasMetadata("name")) {
                        mobName = livingDamager.getMetadata("name").get(0).asString();
                    } else if (livingDamager.getCustomName() != null) {
                        mobName = livingDamager.getCustomName();
                    }

                    deathReason = " was killed by a(n) " + ChatColor.UNDERLINE + mobName;
                }
            }
        }

        // Update victim's death count
        YakPlayer yakVictim = playerManager.getPlayer(victim);
        if (yakVictim != null) {
            yakVictim.addDeath();
            playerManager.savePlayer(yakVictim);
        }

        // Build final death message
        String finalMessage = victim.getDisplayName() + ChatColor.RESET + deathReason;

        // Send to nearby players
        for (Entity entity : victim.getNearbyEntities(50, 50, 50)) {
            if (entity instanceof Player) {
                ((Player) entity).sendMessage(finalMessage);
            }
        }

        // Always send to the victim
        victim.sendMessage(finalMessage);
    }

    /**
     * FIXED: Handle player join/login with proper scoreboard setup
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule update of alignment display and scoreboards
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) return;

                updatePlayerAlignment(player);

                
                PartyScoreboards.updatePlayerTeamAssignments(player);
                PartyScoreboards.handleAlignmentChange(player);
            }
        }.runTaskLater(YakRealms.getInstance(), 3L); // Increased delay to ensure proper loading
    }

    /**
     * FIXED: Handle player PvP and alignment changes with scoreboard updates
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getDamage() <= 0.0) {
            return;
        }

        // Handle projectile damage
        if (event.getDamager() instanceof Projectile && event.getEntity() instanceof Player) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                Player attacker = (Player) projectile.getShooter();
                Player victim = (Player) event.getEntity();

                handlePvPAttack(attacker, victim);
            }
        }

        // Handle direct player attacks
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            Player victim = (Player) event.getEntity();

            handlePvPAttack(attacker, victim);
        }
    }

    /**
     * FIXED: Process a PvP attack between players with alignment updates
     */
    private void handlePvPAttack(Player attacker, Player victim) {
        YakPlayer yakAttacker = playerManager.getPlayer(attacker);
        YakPlayer yakVictim = playerManager.getPlayer(victim);

        if (yakAttacker == null || yakVictim == null) return;

        // Skip for anti-PvP
        if (Toggles.isToggled(attacker, "Anti PVP")) return;

        String oldAlignment = yakAttacker.getAlignment();

        // Set neutral alignment for the attacker
        if ("CHAOTIC".equals(yakAttacker.getAlignment())) {
            // Already chaotic - do nothing
        } else if ("NEUTRAL".equals(yakAttacker.getAlignment())) {
            // Reset neutral timer
            yakAttacker.setNeutralTime(System.currentTimeMillis() / 1000 + NEUTRAL_SECONDS);
            playerManager.savePlayer(yakAttacker);
        } else {
            // Change to neutral
            setNeutralAlignment(attacker);

            
            if (!oldAlignment.equals("NEUTRAL")) {
                PartyScoreboards.handleAlignmentChange(attacker);
            }
        }

        // Combat tag both players
        taggedPlayers.put(attacker.getName(), System.currentTimeMillis());
        taggedPlayers.put(victim.getName(), System.currentTimeMillis());

        // Track last attacker for combat logging prevention
        lastAttackers.put(victim.getName(), attacker);
    }

    /**
     * FIXED: Handle combat logout punishment and cleanup boss bars
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatLogout(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Check if player is combat tagged and not in a safe zone
        if (!isSafeZone(player.getLocation()) &&
                isPlayerTagged(player) &&
                getTimeSinceLastTag(player) < COMBAT_TAG_DURATION) {
            // Kill player for combat logging
            logout = true;
            player.setHealth(0);
        }

        
        UUID playerId = player.getUniqueId();

        // Clean up boss bar
        removeBossBar(playerId);

        // Clean up location cache
        lastSafeLocations.remove(playerId);

        // Clean up combat tracking
        taggedPlayers.remove(player.getName());
        lastAttackers.remove(player.getName());

        // Remove force field
        ForceFieldManager.getInstance().removePlayerForceField(player);

        
        PartyScoreboards.cleanupPlayer(player);
    }

    /**
     * FIXED: Public method to force cleanup of a player's boss bar
     */
    public void cleanupPlayerBossBar(Player player) {
        if (player != null) {
            removeBossBar(player.getUniqueId());
        }
    }

    /**
     * FIXED: Public method to force update of a player's health bar
     */
    public void forceUpdateHealthBar(Player player) {
        if (player != null && player.isOnline() && !player.isDead()) {
            updateHealthBar(player);
        }
    }
}
package com.rednetty.server.mechanics.player.movement;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.stamina.Energy;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Manages dash movement system with balance, visual effects, energy integration, and mechanics.
 */
public class DashMechanics implements Listener {

    private final YakPlayerManager playerManager;
    private final Energy energySystem;
    private final Logger logger;

    // Configuration constants
    private static final double BASE_DASH_SPEED = 1.35;
    private static final double MAX_DASH_SPEED = 2.5;
    private static final int BASE_DASH_DURATION = 3; // ticks
    private static final int MAX_DASH_DURATION = 6; // ticks
    private static final int BASE_COOLDOWN = 15; // seconds
    private static final int MIN_COOLDOWN = 8; // seconds
    private static final int DASH_ENERGY_COST = 25;
    private static final double COLLISION_RADIUS = 1.5;

    // Metadata keys
    private static final String DASH_COOLDOWN_META = "dashCooldown";
    private static final String DASH_ACTIVE_META = "dashActive";
    private static final String LAST_DASH_META = "lastDash";

    // Tracking structures
    private final Map<UUID, DashData> activeDashes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dashCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, DashStats> playerStats = new ConcurrentHashMap<>();

    // Performance counters
    private final AtomicInteger totalDashes = new AtomicInteger(0);
    private final AtomicInteger successfulDashes = new AtomicInteger(0);
    private final AtomicInteger blockedDashes = new AtomicInteger(0);

    // Background tasks
    private BukkitTask cooldownTask;
    private BukkitTask performanceTask;

    /**
     * Tracks dash state.
     */
    private static class DashData {
        final Vector direction;
        final double speed;
        final int duration;
        final long startTime;
        boolean collisionDetected;

        DashData(Vector direction, double speed, int duration) {
            this.direction = direction.clone();
            this.speed = speed;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            this.collisionDetected = false;
        }
    }

    /**
     * Tracks player dash statistics.
     */
    private static class DashStats {
        int totalDashes = 0;
        int successfulDashes = 0;
        double totalDistance = 0;
        long bestDashTime = 0;

        void recordDash(boolean successful, double distance, long duration) {
            totalDashes++;
            if (successful) {
                successfulDashes++;
                totalDistance += distance;
                if (bestDashTime == 0 || duration < bestDashTime) {
                    bestDashTime = duration;
                }
            }
        }
    }

    public DashMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.energySystem = Energy.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startTasks();
        YakRealms.log("Dash mechanics have been enabled.");
    }

    public void onDisable() {
        stopTasks();
        clearAllData();
        YakRealms.log("Dash mechanics have been disabled.");
    }

    /**
     * Starts background tasks for cooldowns and performance monitoring.
     */
    private void startTasks() {
        cooldownTask = new BukkitRunnable() {
            @Override
            public void run() {
                processCooldowns();
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, 20L);

        performanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                logPerformanceStats();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, 20L * 300); // Every 5 minutes

        logger.info("Started dash system tasks");
    }

    /**
     * Processes cooldowns and updates player notifications.
     */
    private void processCooldowns() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = dashCooldowns.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID uuid = entry.getKey();
            Long cooldownEnd = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            if (currentTime >= cooldownEnd) {
                iterator.remove();
                player.removeMetadata(DASH_COOLDOWN_META, YakRealms.getInstance());
                sendDashReadyNotification(player);
            } else {
                int remainingSeconds = (int) ((cooldownEnd - currentTime) / 1000);
                updateCooldownDisplay(player, remainingSeconds);
            }
        }
    }

    /**
     * Handles player interaction for initiating dash.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = event.getItem();

        if (event.hasBlock() && isInteractiveBlock(event.getClickedBlock())) {
            return;
        }

        if (isNearNPC(player)) {
            return;
        }

        if (item == null || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        boolean shouldDash = isWeapon(item) || (player.isSneaking() && isStaff(item));

        if (shouldDash) {
            attemptDash(player);
        }
    }

    /**
     * Attempts to perform a dash after validations.
     */
    private void attemptDash(Player player) {
        UUID uuid = player.getUniqueId();

        if (activeDashes.containsKey(uuid)) {
            return;
        }

        if (!canDash(player)) {
            sendCooldownMessage(player);
            return;
        }

        if (!checkEnergyRequirement(player)) {
            return;
        }

        if (!isSafeTowardDash(player)) {
            sendBlockedMessage(player);
            blockedDashes.incrementAndGet();
            return;
        }

        executeDash(player);
    }

    /**
     * Executes the dash sequence.
     */
    private void executeDash(Player player) {
        UUID uuid = player.getUniqueId();

        DashParameters params = calculateDashParameters(player);

        DashData dashData = new DashData(params.direction, params.speed, params.duration);
        activeDashes.put(uuid, dashData);

        energySystem.removeEnergy(player, DASH_ENERGY_COST);
        setCooldown(player, params.cooldown);
        playDashEffects(player);
        totalDashes.incrementAndGet();
        updatePlayerStats(player, true);

        player.setMetadata(DASH_ACTIVE_META, new FixedMetadataValue(YakRealms.getInstance(), true));
        player.setMetadata(LAST_DASH_META, new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        startDashTask(player, dashData);

        logger.fine("Player " + player.getName() + " executed dash: " + params);
    }

    /**
     * Calculates parameters for the dash based on player state.
     */
    private DashParameters calculateDashParameters(Player player) {
        double speed = BASE_DASH_SPEED;
        int duration = BASE_DASH_DURATION;
        int cooldown = BASE_COOLDOWN;

        Vector direction = player.getLocation().getDirection().setY(0).normalize();

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon != null) {
            WeaponDashModifier modifier = getWeaponModifier(weapon);
            speed *= modifier.speedMultiplier;
            duration = (int) (duration * modifier.durationMultiplier);
            cooldown = (int) (cooldown * modifier.cooldownMultiplier);
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            // TODO: Integrate with PlayerStatsCalculator for dexterity bonuses
        }

        speed = Math.min(MAX_DASH_SPEED, speed);
        duration = Math.min(MAX_DASH_DURATION, duration);
        cooldown = Math.max(MIN_COOLDOWN, cooldown);

        return new DashParameters(direction, speed, duration, cooldown);
    }

    /**
     * Starts the per-dash task for movement and effects.
     */
    private void startDashTask(Player player, DashData dashData) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= dashData.duration || !player.isOnline()) {
                    completeDash(player, dashData, tick);
                    cancel();
                    return;
                }

                if (checkDashCollision(player, dashData.direction)) {
                    dashData.collisionDetected = true;
                    completeDash(player, dashData, tick);
                    cancel();
                    return;
                }

                Vector velocity = dashData.direction.clone().multiply(dashData.speed);
                player.setVelocity(velocity);

                createTrailEffects(player, dashData);

                tick++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, 1L);
    }

    /**
     * Checks for collisions during dash.
     */
    private boolean checkDashCollision(Player player, Vector direction) {
        Location playerLoc = player.getLocation();
        Location checkLoc = playerLoc.clone().add(direction.clone().multiply(2));

        Block block = checkLoc.getBlock();
        if (block.getType().isSolid()) {
            return true;
        }

        for (Entity entity : checkLoc.getWorld().getNearbyEntities(checkLoc, COLLISION_RADIUS, COLLISION_RADIUS, COLLISION_RADIUS)) {
            if (entity instanceof Player && entity != player) {
                return true;
            }
        }

        if (checkLoc.getY() < 0 || checkLoc.getY() > 320) {
            return true;
        }

        return false;
    }

    /**
     * Spawns trail particles for dashing player.
     */
    private void createTrailEffects(Player player, DashData dashData) {
        Location loc = player.getLocation();

        player.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);

        Vector backwards = dashData.direction.clone().multiply(-0.5);
        Location trailLoc = loc.clone().add(backwards);
        player.getWorld().spawnParticle(Particle.PORTAL, trailLoc, 5, 0.2, 0.2, 0.2, 0.05);

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon != null && isWeapon(weapon)) {
            createWeaponTrailEffect(player, weapon);
        }
    }

    /**
     * Spawns weapon-specific trail particles.
     */
    private void createWeaponTrailEffect(Player player, ItemStack weapon) {
        String weaponType = weapon.getType().name();
        Location loc = player.getLocation().add(0, 1, 0);

        if (weaponType.contains("SWORD")) {
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
        } else if (weaponType.contains("AXE")) {
            player.getWorld().spawnParticle(Particle.LAVA, loc, 2, 0.2, 0.2, 0.2, 0);
        } else if (weaponType.contains("HOE")) {
            player.getWorld().spawnParticle(Particle.ENCHANT, loc, 10, 0.5, 0.5, 0.5, 1);
        }
    }

    /**
     * Completes the dash and performs cleanup.
     */
    private void completeDash(Player player, DashData dashData, int ticksExecuted) {
        UUID uuid = player.getUniqueId();

        activeDashes.remove(uuid);
        player.removeMetadata(DASH_ACTIVE_META, YakRealms.getInstance());

        boolean successful = !dashData.collisionDetected;
        if (successful) {
            successfulDashes.incrementAndGet();
            playDashCompleteEffects(player);
        } else {
            playDashFailedEffects(player);
        }

        long durationMs = System.currentTimeMillis() - dashData.startTime;
        double distance = dashData.speed * ticksExecuted;
        updatePlayerStats(player, successful, distance, durationMs);
    }

    /**
     * Plays initial dash sound and particle effects.
     */
    private void playDashEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 2.0f);

        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
    }

    private void playDashCompleteEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
    }

    private void playDashFailedEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
    }

    /**
     * Sets cooldown for the player.
     */
    private void setCooldown(Player player, int cooldownSeconds) {
        UUID uuid = player.getUniqueId();
        long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000L);

        dashCooldowns.put(uuid, cooldownEnd);
        player.setMetadata(DASH_COOLDOWN_META, new FixedMetadataValue(YakRealms.getInstance(), cooldownEnd));

        startCooldownDisplay(player, cooldownSeconds);
    }

    private void startCooldownDisplay(Player player, int cooldownSeconds) {
        try {
            ActionBarUtil.addCountdownMessage(player, ChatColor.GRAY + "Dash Cooldown: " + ChatColor.RED, cooldownSeconds);
        } catch (Exception e) {
            player.sendMessage(ChatColor.GRAY + "Dash on cooldown for " + cooldownSeconds + " seconds");
        }
    }

    private void sendDashReadyNotification(Player player) {
        try {
            ActionBarUtil.addTemporaryMessage(player, ChatColor.GREEN + "Dash Ready!", 40L);
        } catch (Exception e) {
            player.sendMessage(ChatColor.GREEN + "Dash Ready!");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
    }

    private boolean canDash(Player player) {
        return !player.hasMetadata(DASH_COOLDOWN_META) ||
                System.currentTimeMillis() >= player.getMetadata(DASH_COOLDOWN_META).get(0).asLong();
    }

    private boolean checkEnergyRequirement(Player player) {
        if (!energySystem.hasEnergy(player, DASH_ENERGY_COST)) {
            player.sendMessage(ChatColor.RED + "Not enough energy to dash! Need " + DASH_ENERGY_COST + " energy.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return false;
        }
        return true;
    }

    private boolean isSafeTowardDash(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Location checkLoc = player.getLocation().add(direction.multiply(3));

        Block block = checkLoc.getBlock();
        Material blockType = block.getType();

        if (blockType == Material.LAVA || blockType == Material.FIRE || blockType == Material.VOID_AIR) {
            return false;
        }

        if (checkLoc.getY() < 5) {
            return false;
        }

        return true;
    }

    private boolean isInteractiveBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST ||
                type == Material.CRAFTING_TABLE || type == Material.ANVIL || type == Material.FURNACE ||
                type == Material.BREWING_STAND || type == Material.ENCHANTING_TABLE ||
                type.name().contains("SHULKER_BOX") || type.name().contains("DOOR");
    }

    private boolean isNearNPC(Player player) {
        return player.getNearbyEntities(4, 4, 4).stream()
                .anyMatch(entity -> entity.hasMetadata("NPC"));
    }

    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String type = item.getType().name();
        return type.endsWith("_SWORD") || type.endsWith("_AXE") || type.endsWith("_SHOVEL");
    }

    private boolean isStaff(ItemStack item) {
        return item != null && item.getType().name().endsWith("_HOE");
    }

    private WeaponDashModifier getWeaponModifier(ItemStack weapon) {
        String weaponType = weapon.getType().name();

        if (weaponType.contains("SWORD")) {
            return new WeaponDashModifier(1.0, 1.0, 1.0);
        } else if (weaponType.contains("AXE")) {
            return new WeaponDashModifier(0.9, 0.8, 1.2);
        } else if (weaponType.contains("HOE")) {
            return new WeaponDashModifier(1.1, 1.2, 0.9);
        } else {
            return new WeaponDashModifier(0.95, 0.9, 1.1);
        }
    }

    private void updatePlayerStats(Player player, boolean successful) {
        updatePlayerStats(player, successful, 0, 0);
    }

    private void updatePlayerStats(Player player, boolean successful, double distance, long duration) {
        UUID uuid = player.getUniqueId();
        DashStats stats = playerStats.computeIfAbsent(uuid, k -> new DashStats());
        stats.recordDash(successful, distance, duration);
    }

    private void updateCooldownDisplay(Player player, int remainingSeconds) {
        // Optional: Implement action bar update for remaining cooldown
    }

    private void sendCooldownMessage(Player player) {
        long cooldownEnd = player.getMetadata(DASH_COOLDOWN_META).get(0).asLong();
        int remaining = (int) ((cooldownEnd - System.currentTimeMillis()) / 1000);
        player.sendMessage(ChatColor.RED + "Dash on cooldown for " + remaining + " more seconds!");
    }

    private void sendBlockedMessage(Player player) {
        player.sendMessage(ChatColor.RED + "Cannot dash in this direction - blocked!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 1.0f);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        activeDashes.remove(uuid);
        dashCooldowns.remove(uuid);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (activeDashes.containsKey(uuid)) {
            // Allow dash movement without interference
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeDashes.containsKey(uuid)) {
            activeDashes.remove(uuid);
            event.getPlayer().removeMetadata(DASH_ACTIVE_META, YakRealms.getInstance());
        }
    }

    private void stopTasks() {
        if (cooldownTask != null && !cooldownTask.isCancelled()) {
            cooldownTask.cancel();
        }
        if (performanceTask != null && !performanceTask.isCancelled()) {
            performanceTask.cancel();
        }
    }

    private void clearAllData() {
        activeDashes.clear();
        dashCooldowns.clear();
        playerStats.clear();
    }

    private void logPerformanceStats() {
        logger.info("Dash Mechanics Performance:");
        logger.info("  Total Dashes: " + totalDashes.get());
        logger.info("  Successful: " + successfulDashes.get());
        logger.info("  Blocked: " + blockedDashes.get());
        logger.info("  Active Dashes: " + activeDashes.size());
        logger.info("  Players on Cooldown: " + dashCooldowns.size());
    }

    private static class DashParameters {
        final Vector direction;
        final double speed;
        final int duration;
        final int cooldown;

        DashParameters(Vector direction, double speed, int duration, int cooldown) {
            this.direction = direction;
            this.speed = speed;
            this.duration = duration;
            this.cooldown = cooldown;
        }

        @Override
        public String toString() {
            return String.format("DashParams{speed=%.2f, duration=%d, cooldown=%d}", speed, duration, cooldown);
        }
    }

    private static class WeaponDashModifier {
        final double speedMultiplier;
        final double durationMultiplier;
        final double cooldownMultiplier;

        WeaponDashModifier(double speed, double duration, double cooldown) {
            this.speedMultiplier = speed;
            this.durationMultiplier = duration;
            this.cooldownMultiplier = cooldown;
        }
    }

    public boolean isDashing(Player player) {
        return activeDashes.containsKey(player.getUniqueId());
    }

    public boolean isOnCooldown(Player player) {
        return !canDash(player);
    }

    public int getRemainingCooldown(Player player) {
        if (!player.hasMetadata(DASH_COOLDOWN_META)) return 0;

        long cooldownEnd = player.getMetadata(DASH_COOLDOWN_META).get(0).asLong();
        return Math.max(0, (int) ((cooldownEnd - System.currentTimeMillis()) / 1000));
    }

    public DashStats getPlayerStats(Player player) {
        return playerStats.get(player.getUniqueId());
    }

    public void resetPlayerCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        dashCooldowns.remove(uuid);
        player.removeMetadata(DASH_COOLDOWN_META, YakRealms.getInstance());
        sendDashReadyNotification(player);
    }
}
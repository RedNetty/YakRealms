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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 *  dash movement system with improved balance, visual effects,
 * energy integration, and advanced mechanics.
 */
public class DashMechanics implements Listener {

    private final YakPlayerManager playerManager;
    private final Energy energySystem;
    private final Logger logger;

    //  configuration with balance improvements
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

    //  tracking and caching
    private final Map<UUID, DashData> activeDashes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dashCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, DashStats> playerStats = new ConcurrentHashMap<>();
    private final Set<UUID> dashTrails = ConcurrentHashMap.newKeySet();

    // Performance tracking
    private final AtomicInteger totalDashes = new AtomicInteger(0);
    private final AtomicInteger successfulDashes = new AtomicInteger(0);
    private final AtomicInteger blockedDashes = new AtomicInteger(0);

    // Tasks
    private BukkitTask cooldownTask;
    private BukkitTask trailTask;
    private BukkitTask performanceTask;

    /**
     *  dash data tracking
     */
    private static class DashData {
        final Vector direction;
        final double speed;
        final int duration;
        final long startTime;
        int ticksRemaining;
        boolean collisionDetected;

        DashData(Vector direction, double speed, int duration) {
            this.direction = direction.clone();
            this.speed = speed;
            this.duration = duration;
            this.startTime = System.currentTimeMillis();
            this.ticksRemaining = duration;
            this.collisionDetected = false;
        }
    }

    /**
     * Player dash statistics
     */
    private static class DashStats {
        int totalDashes = 0;
        int successfulDashes = 0;
        long totalDistance = 0;
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
        YakRealms.log(" Dash mechanics have been enabled.");
    }

    public void onDisable() {
        stopTasks();
        clearAllData();
        YakRealms.log(" Dash mechanics have been disabled.");
    }

    /**
     * Start  background tasks
     */
    private void startTasks() {
        // Cooldown management task
        cooldownTask = new BukkitRunnable() {
            @Override
            public void run() {
                processCooldowns();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, 20L);

        // Trail effect task
        trailTask = new BukkitRunnable() {
            @Override
            public void run() {
                processTrailEffects();
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, 2L);

        // Performance monitoring task
        performanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                logPerformanceStats();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, 20L * 300); // Every 5 minutes

        logger.info("Started  dash system tasks");
    }

    /**
     * Process cooldowns and update displays
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
                // Cooldown expired
                iterator.remove();
                player.removeMetadata(DASH_COOLDOWN_META, YakRealms.getInstance());

                // Notify player
                sendDashReadyNotification(player);
            } else {
                // Update cooldown display
                int remainingSeconds = (int) ((cooldownEnd - currentTime) / 1000);
                updateCooldownDisplay(player, remainingSeconds);
            }
        }
    }

    /**
     * Process visual trail effects for dashing players
     */
    private void processTrailEffects() {
        Iterator<UUID> iterator = dashTrails.iterator();

        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            DashData dashData = activeDashes.get(uuid);
            if (dashData == null) {
                iterator.remove();
                continue;
            }

            // Create  trail effects
            createTrailEffects(player, dashData);

            // Update dash progression
            dashData.ticksRemaining--;
            if (dashData.ticksRemaining <= 0) {
                completeDash(player, dashData);
                iterator.remove();
            }
        }
    }

    /**
     *  dash interaction handler
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = event.getItem();

        // Block dash near interactive blocks
        if (event.hasBlock() && isInteractiveBlock(event.getClickedBlock())) {
            return;
        }

        // Block dash near NPCs
        if (isNearNPC(player)) {
            return;
        }

        // Only process right-click actions with valid items
        if (item == null || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        // Determine if player should dash
        boolean shouldDash = isWeapon(item) || (player.isSneaking() && isStaff(item));

        if (shouldDash) {
            attemptDash(player);
        }
    }

    /**
     *  dash attempt with validation and effects
     */
    private void attemptDash(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if already dashing
        if (activeDashes.containsKey(uuid)) {
            return;
        }

        // Check cooldown
        if (!canDash(player)) {
            sendCooldownMessage(player);
            return;
        }

        // Check energy requirement
        if (!checkEnergyRequirement(player)) {
            return;
        }

        // Check environment safety
        if (!isSafeTowardDash(player)) {
            sendBlockedMessage(player);
            blockedDashes.incrementAndGet();
            return;
        }

        // Execute dash
        executeDash(player);
    }

    /**
     * Execute  dash with improved mechanics
     */
    private void executeDash(Player player) {
        UUID uuid = player.getUniqueId();

        // Calculate dash parameters
        DashParameters params = calculateDashParameters(player);

        // Create dash data
        DashData dashData = new DashData(params.direction, params.speed, params.duration);
        activeDashes.put(uuid, dashData);
        dashTrails.add(uuid);

        // Apply movement
        applyDashMovement(player, dashData);

        // Apply energy cost
        energySystem.removeEnergy(player, DASH_ENERGY_COST);

        // Set cooldown
        setCooldown(player, params.cooldown);

        // Play effects
        playDashEffects(player);

        // Update statistics
        totalDashes.incrementAndGet();
        updatePlayerStats(player, true);

        // Set player metadata
        player.setMetadata(DASH_ACTIVE_META, new FixedMetadataValue(YakRealms.getInstance(), true));
        player.setMetadata(LAST_DASH_META, new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        logger.fine("Player " + player.getName() + " executed dash: " + params);
    }

    /**
     *  dash parameter calculation
     */
    private DashParameters calculateDashParameters(Player player) {
        // Base parameters
        double speed = BASE_DASH_SPEED;
        int duration = BASE_DASH_DURATION;
        int cooldown = BASE_COOLDOWN;

        // Get player direction (horizontal only)
        Vector direction = player.getLocation().getDirection().setY(0).normalize();

        // Equipment modifiers
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon != null) {
            WeaponDashModifier modifier = getWeaponModifier(weapon);
            speed *= modifier.speedMultiplier;
            duration = (int) (duration * modifier.durationMultiplier);
            cooldown = (int) (cooldown * modifier.cooldownMultiplier);
        }

        // Dexterity bonuses
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            // TODO: Integrate with PlayerStatsCalculator when available
            // int dexterity = PlayerStatsCalculator.calculateTotalAttribute(player, "DEX");
            // Apply dexterity bonuses to speed and cooldown
        }

        // Apply limits
        speed = Math.min(MAX_DASH_SPEED, speed);
        duration = Math.min(MAX_DASH_DURATION, duration);
        cooldown = Math.max(MIN_COOLDOWN, cooldown);

        return new DashParameters(direction, speed, duration, cooldown);
    }

    /**
     * Apply  dash movement with collision detection
     */
    private void applyDashMovement(Player player, DashData dashData) {
        new BukkitRunnable() {
            int ticksRun = 0;

            @Override
            public void run() {
                if (ticksRun >= dashData.duration || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Check for collision
                if (checkDashCollision(player, dashData.direction)) {
                    dashData.collisionDetected = true;
                    cancel();
                    return;
                }

                // Apply velocity
                Vector velocity = dashData.direction.clone().multiply(dashData.speed);
                player.setVelocity(velocity);

                ticksRun++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, 1);
    }

    /**
     *  collision detection
     */
    private boolean checkDashCollision(Player player, Vector direction) {
        Location playerLoc = player.getLocation();
        Location checkLoc = playerLoc.clone().add(direction.clone().multiply(2));

        // Check for solid blocks
        Block block = checkLoc.getBlock();
        if (block.getType().isSolid()) {
            return true;
        }

        // Check for entities (players, mobs)
        for (Entity entity : player.getNearbyEntities(COLLISION_RADIUS, COLLISION_RADIUS, COLLISION_RADIUS)) {
            if (entity instanceof Player && entity != player) {
                return true;
            }
        }

        // Check for world boundaries
        if (checkLoc.getY() < 0 || checkLoc.getY() > 320) {
            return true;
        }

        return false;
    }

    /**
     * Create  trail effects
     */
    private void createTrailEffects(Player player, DashData dashData) {
        Location loc = player.getLocation();

        // Main dash particles
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                loc.clone().add(0, 1, 0),
                8, 0.3, 0.3, 0.3, 0.1
        );

        // Speed lines effect
        player.getWorld().spawnParticle(
                Particle.CRIT,
                loc.clone().add(0, 1, 0),
                3, 0.5, 0.5, 0.5, 0
        );

        // Directional particles
        Vector backwards = dashData.direction.clone().multiply(-0.5);
        Location trailLoc = loc.clone().add(backwards);
        player.getWorld().spawnParticle(
                Particle.PORTAL,
                trailLoc,
                5, 0.2, 0.2, 0.2, 0.05
        );

        //  effects for weapons
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon != null && isWeapon(weapon)) {
            createWeaponTrailEffect(player, weapon);
        }
    }

    /**
     * Create weapon-specific trail effects
     */
    private void createWeaponTrailEffect(Player player, ItemStack weapon) {
        String weaponType = weapon.getType().name();
        Location loc = player.getLocation().add(0, 1, 0);

        if (weaponType.contains("SWORD")) {
            // Sword - sharp cutting effect
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
        } else if (weaponType.contains("AXE")) {
            // Axe - heavy impact effect
            player.getWorld().spawnParticle(Particle.LAVA, loc, 2, 0.2, 0.2, 0.2, 0);
        } else if (weaponType.contains("HOE")) {
            // Staff - magical effect
            player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, loc, 10, 0.5, 0.5, 0.5, 1);
        }
    }

    /**
     * Complete dash with cleanup and effects
     */
    private void completeDash(Player player, DashData dashData) {
        UUID uuid = player.getUniqueId();

        // Clean up tracking
        activeDashes.remove(uuid);
        player.removeMetadata(DASH_ACTIVE_META, YakRealms.getInstance());

        // Calculate success
        boolean successful = !dashData.collisionDetected;
        if (successful) {
            successfulDashes.incrementAndGet();
        }

        // Update statistics
        long duration = System.currentTimeMillis() - dashData.startTime;
        double distance = dashData.speed * dashData.duration;
        updatePlayerStats(player, successful, distance, duration);

        // Play completion effects
        if (successful) {
            playDashCompleteEffects(player);
        } else {
            playDashFailedEffects(player);
        }
    }

    /**
     *  dash effects
     */
    private void playDashEffects(Player player) {
        // Sound effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 2.0f);

        // Visual burst effect
        player.getWorld().spawnParticle(
                Particle.EXPLOSION_NORMAL,
                player.getLocation().add(0, 1, 0),
                10, 0.5, 0.5, 0.5, 0.1
        );
    }

    private void playDashCompleteEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
    }

    private void playDashFailedEffects(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.05);
    }

    /**
     *  cooldown management
     */
    private void setCooldown(Player player, int cooldownSeconds) {
        UUID uuid = player.getUniqueId();
        long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000L);

        dashCooldowns.put(uuid, cooldownEnd);
        player.setMetadata(DASH_COOLDOWN_META, new FixedMetadataValue(YakRealms.getInstance(), cooldownEnd));

        // Start cooldown timer display
        startCooldownDisplay(player, cooldownSeconds);
    }

    private void startCooldownDisplay(Player player, int cooldownSeconds) {
        // Use ActionBarUtil if available for countdown
        if (ActionBarUtil.class != null) {
            try {
                ActionBarUtil.addCountdownMessage(player, ChatColor.GRAY + "Dash Cooldown: " + ChatColor.RED, cooldownSeconds);
            } catch (Exception e) {
                // Fallback to chat message
                player.sendMessage(ChatColor.GRAY + "Dash on cooldown for " + cooldownSeconds + " seconds");
            }
        }
    }

    private void sendDashReadyNotification(Player player) {
        if (ActionBarUtil.class != null) {
            try {
                ActionBarUtil.addTemporaryMessage(player, ChatColor.GREEN + "Dash Ready!", 40L);
            } catch (Exception e) {
                player.sendMessage(ChatColor.GREEN + "Dash Ready!");
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
    }

    // Validation and utility methods

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
        // Check for void, lava, or other dangerous areas
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Location checkLoc = player.getLocation().add(direction.multiply(3));

        Block block = checkLoc.getBlock();
        Material blockType = block.getType();

        // Check for dangerous blocks
        if (blockType == Material.LAVA || blockType == Material.FIRE || blockType == Material.VOID_AIR) {
            return false;
        }

        // Check Y level
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
            return new WeaponDashModifier(1.0, 1.0, 1.0); // Balanced
        } else if (weaponType.contains("AXE")) {
            return new WeaponDashModifier(0.9, 0.8, 1.2); // Slower, shorter, longer cooldown
        } else if (weaponType.contains("HOE")) {
            return new WeaponDashModifier(1.1, 1.2, 0.9); // Faster, longer, shorter cooldown
        } else {
            return new WeaponDashModifier(0.95, 0.9, 1.1); // Slightly worse for tools
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
        // Optional: Update action bar with remaining cooldown
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

    // Event handlers

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Clean up all tracking data
        activeDashes.remove(uuid);
        dashCooldowns.remove(uuid);
        dashTrails.remove(uuid);

        // Keep stats for potential future rejoins
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if player is dashing and handle movement
        DashData dashData = activeDashes.get(uuid);
        if (dashData != null) {
            // Prevent other movement during dash
            if (event.getTo().distance(event.getFrom()) > 0.1) {
                // Allow the dash movement but prevent other interference
            }
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Cancel active dash if player teleports
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeDashes.containsKey(uuid)) {
            activeDashes.remove(uuid);
            dashTrails.remove(uuid);
            event.getPlayer().removeMetadata(DASH_ACTIVE_META, YakRealms.getInstance());
        }
    }

    // Cleanup and performance

    private void stopTasks() {
        if (cooldownTask != null && !cooldownTask.isCancelled()) {
            cooldownTask.cancel();
        }
        if (trailTask != null && !trailTask.isCancelled()) {
            trailTask.cancel();
        }
        if (performanceTask != null && !performanceTask.isCancelled()) {
            performanceTask.cancel();
        }
    }

    private void clearAllData() {
        activeDashes.clear();
        dashCooldowns.clear();
        playerStats.clear();
        dashTrails.clear();
    }

    private void logPerformanceStats() {
        logger.info("Dash Mechanics Performance:");
        logger.info("  Total Dashes: " + totalDashes.get());
        logger.info("  Successful: " + successfulDashes.get());
        logger.info("  Blocked: " + blockedDashes.get());
        logger.info("  Active Dashes: " + activeDashes.size());
        logger.info("  Players on Cooldown: " + dashCooldowns.size());
    }

    // Helper classes

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

    // Public API methods

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
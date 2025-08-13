package com.rednetty.server.mechanics.combat.holograms;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced combat hologram handler that creates smooth arcing damage holograms
 * with proper toggle integration and collision-free animations.
 */
public class CombatHologramHandler {
    private static CombatHologramHandler instance;

    // Animation constants
    private static final double INITIAL_HEIGHT_OFFSET = .1; // Height above target to start
    private static final double PEAK_HEIGHT_ADDITION = .8; // Additional height for arc peak
    private static final double HORIZONTAL_SPREAD = 1.5; // Horizontal randomness
    private static final double ANIMATION_SPEED = 0.08; // Speed of animation (lower = slower)
    private static final int MAX_ANIMATION_TICKS = 60; // Maximum animation duration
    private static final double GROUND_CHECK_OFFSET = 0.4; // Ground collision detection
    private static final double FADE_START_HEIGHT = 0.5; // Height to start fading

    // Performance and cleanup constants
    private static final int MAX_CONCURRENT_HOLOGRAMS = 50; // Prevent spam
    private static final int CLEANUP_INTERVAL = 100; // Ticks between cleanup
    private static final long HOLOGRAM_EXPIRY_TIME = 10000; // 10 seconds max lifetime

    // Tracking and management
    private final Map<String, AnimatedHologram> activeHolograms = new ConcurrentHashMap<>();
    private final AtomicInteger hologramIdCounter = new AtomicInteger(0);
    private final Set<UUID> playersWithHologramsDisabled = ConcurrentHashMap.newKeySet();
    private BukkitTask cleanupTask;

    /**
     * Represents an animated hologram with arcing movement
     */
    private static class AnimatedHologram {
        final String id;
        final Location startLocation;
        final Location currentLocation;
        final Vector targetDirection;
        final String text;
        final HologramType type;
        final long creationTime;
        final double peakHeight;
        final double totalDistance;
        final List<UUID> visibleToPlayers;

        BukkitTask animationTask;
        int animationTick;
        boolean isActive;

        AnimatedHologram(String id, Location start, String text, HologramType type, List<UUID> visibleTo) {
            this.id = id;
            this.startLocation = start.clone();
            this.currentLocation = start.clone();
            this.text = text;
            this.type = type;
            this.creationTime = System.currentTimeMillis();
            this.visibleToPlayers = new ArrayList<>(visibleTo);
            this.animationTick = 0;
            this.isActive = true;

            // Calculate arc parameters
            double randomX = (ThreadLocalRandom.current().nextDouble() - 0.5) * HORIZONTAL_SPREAD;
            double randomZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * HORIZONTAL_SPREAD;
            this.targetDirection = new Vector(randomX, 0, randomZ);
            this.peakHeight = start.getY() + PEAK_HEIGHT_ADDITION;
            this.totalDistance = Math.sqrt(randomX * randomX + randomZ * randomZ);
        }

        void stopAnimation() {
            if (animationTask != null && !animationTask.isCancelled()) {
                animationTask.cancel();
            }
            isActive = false;
            HologramManager.removeHologram(id);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - creationTime > HOLOGRAM_EXPIRY_TIME;
        }
    }

    /**
     * Types of combat holograms with different visual styles
     */
    public enum HologramType {
        DAMAGE(ChatColor.RED, "⚔", true),
        CRITICAL_DAMAGE(ChatColor.GOLD, "💥", true),
        BLOCK(ChatColor.BLUE, "🛡", false),
        DODGE(ChatColor.GREEN, "💨", false),
        HEAL(ChatColor.GREEN, "❤", false),
        LIFESTEAL(ChatColor.DARK_GREEN, "🩸", false),
        THORNS(ChatColor.GOLD, "🌹", true),
        MISS(ChatColor.GRAY, "○", false),
        IMMUNE(ChatColor.YELLOW, "⚡", false),
        ELEMENTAL_ICE(ChatColor.AQUA, "❄", true),
        ELEMENTAL_FIRE(ChatColor.GOLD, "🔥", true),
        ELEMENTAL_POISON(ChatColor.DARK_GREEN, "☠", true),
        ELEMENTAL_PURE(ChatColor.WHITE, "✦", true);

        public final ChatColor color;
        public final String icon;
        public final boolean hasValue;

        HologramType(ChatColor color, String icon, boolean hasValue) {
            this.color = color;
            this.icon = icon;
            this.hasValue = hasValue;
        }
    }

    private CombatHologramHandler() {
        startCleanupTask();
    }

    public static CombatHologramHandler getInstance() {
        if (instance == null) {
            instance = new CombatHologramHandler();
        }
        return instance;
    }

    /**
     * Initialize the hologram handler
     */
    public void onEnable() {
        YakRealms.log("Combat Hologram Handler has been enabled");
    }

    /**
     * Cleanup and disable the hologram handler
     */
    public void onDisable() {
        // Stop all animations
        for (AnimatedHologram hologram : activeHolograms.values()) {
            hologram.stopAnimation();
        }
        activeHolograms.clear();

        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        YakRealms.log("Combat Hologram Handler has been disabled");
    }

    /**
     * Show a combat hologram with arcing animation
     * @param attacker The attacking entity (for positioning reference)
     * @param target The target entity to show hologram above
     * @param type The type of hologram
     * @param value The numeric value to display (if applicable)
     */
    public void showCombatHologram(Entity attacker, LivingEntity target, HologramType type, int value) {
        if (!shouldShowHologram(target)) {
            return;
        }

        // Prevent hologram spam
        if (activeHolograms.size() >= MAX_CONCURRENT_HOLOGRAMS) {
            return;
        }

        // Determine who should see this hologram
        List<UUID> visibleToPlayers = getVisiblePlayers(attacker, target);
        if (visibleToPlayers.isEmpty()) {
            return;
        }

        // Generate unique hologram ID
        String hologramId = "combat_" + target.getUniqueId() + "_" + hologramIdCounter.incrementAndGet();

        // Calculate starting position (above target's head)
        Location startLocation = target.getLocation().clone();
        startLocation.add(0, target.getHeight() + INITIAL_HEIGHT_OFFSET, 0);

        // Add small random offset to prevent overlapping
        double offsetX = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        double offsetZ = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.6;
        startLocation.add(offsetX, 0, offsetZ);

        // Create hologram text
        String hologramText = createHologramText(type, value);

        // Create and start animated hologram
        AnimatedHologram animatedHologram = new AnimatedHologram(hologramId, startLocation, hologramText, type, visibleToPlayers);
        activeHolograms.put(hologramId, animatedHologram);

        startHologramAnimation(animatedHologram);
    }

    /**
     * Show a multi-line hologram (for complex effects)
     */
    public void showMultiLineHologram(Entity attacker, LivingEntity target, List<String> lines) {
        if (!shouldShowHologram(target) || lines.isEmpty()) {
            return;
        }

        // Show each line as a separate hologram with slight delays
        for (int i = 0; i < lines.size(); i++) {
            final int lineIndex = i;
            final String line = lines.get(i);

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                showCustomHologram(attacker, target, line, HologramType.DAMAGE);
            }, i * 3L); // 3 tick delay between lines
        }
    }

    /**
     * Show a custom hologram with specific text
     */
    public void showCustomHologram(Entity attacker, LivingEntity target, String text, HologramType type) {
        if (!shouldShowHologram(target)) {
            return;
        }

        List<UUID> visibleToPlayers = getVisiblePlayers(attacker, target);
        if (visibleToPlayers.isEmpty()) {
            return;
        }

        String hologramId = "custom_" + target.getUniqueId() + "_" + hologramIdCounter.incrementAndGet();
        Location startLocation = target.getLocation().clone();
        startLocation.add(0, target.getHeight() + INITIAL_HEIGHT_OFFSET, 0);

        AnimatedHologram animatedHologram = new AnimatedHologram(hologramId, startLocation, text, type, visibleToPlayers);
        activeHolograms.put(hologramId, animatedHologram);

        startHologramAnimation(animatedHologram);
    }

    /**
     * Check if holograms should be shown for the given target
     */
    private boolean shouldShowHologram(LivingEntity target) {
        if (target == null || target.isDead()) {
            return false;
        }

        // Check if target is a player with holograms disabled
        if (target instanceof Player) {
            Player player = (Player) target;
            if (!Toggles.isToggled(player, "Hologram Damage")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get list of players who should see this hologram
     */
    private List<UUID> getVisiblePlayers(Entity attacker, LivingEntity target) {
        List<UUID> visiblePlayers = new ArrayList<>();
        Location location = target.getLocation();

        // Check nearby players
        for (Entity entity : target.getNearbyEntities(16, 16, 16)) {
            if (entity instanceof Player) {
                Player player = (Player) entity;

                // Check if player has hologram damage enabled
                if (Toggles.isToggled(player, "Hologram Damage")) {
                    visiblePlayers.add(player.getUniqueId());
                }
            }
        }

        // Always include attacker if they're a player
        if (attacker instanceof Player) {
            Player attackerPlayer = (Player) attacker;
            if (Toggles.isToggled(attackerPlayer, "Hologram Damage")) {
                visiblePlayers.add(attackerPlayer.getUniqueId());
            }
        }

        // Always include target if they're a player
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (Toggles.isToggled(targetPlayer, "Hologram Damage")) {
                visiblePlayers.add(targetPlayer.getUniqueId());
            }
        }

        return visiblePlayers;
    }

    /**
     * Create formatted hologram text based on type and value
     */
    private String createHologramText(HologramType type, int value) {
        StringBuilder text = new StringBuilder();

        // Add icon and color
        text.append(type.color).append(ChatColor.BOLD);

        if (!type.icon.isEmpty()) {
            text.append(type.icon).append(" ");
        }

        // Add value or text based on type
        switch (type) {
            case DAMAGE:
                text.append("-").append(value);
                break;
            case CRITICAL_DAMAGE:
                text.append("CRIT -").append(value);
                break;
            case BLOCK:
                text.append("BLOCKED");
                break;
            case DODGE:
                text.append("DODGED");
                break;
            case HEAL:
            case LIFESTEAL:
                text.append("+").append(value).append(" HP");
                break;
            case THORNS:
                text.append("THORNS ").append(value);
                break;
            case MISS:
                text.append("MISS");
                break;
            case IMMUNE:
                text.append("IMMUNE");
                break;
            case ELEMENTAL_ICE:
                text.append(value).append(" ICE");
                break;
            case ELEMENTAL_FIRE:
                text.append(value).append(" FIRE");
                break;
            case ELEMENTAL_POISON:
                text.append(value).append(" POISON");
                break;
            case ELEMENTAL_PURE:
                text.append(value).append(" PURE");
                break;
            default:
                if (type.hasValue) {
                    text.append(value);
                }
                break;
        }

        return text.toString();
    }

    /**
     * Start the smooth arcing animation for a hologram
     */
    private void startHologramAnimation(AnimatedHologram hologram) {
        hologram.animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!hologram.isActive || hologram.animationTick >= MAX_ANIMATION_TICKS) {
                    hologram.stopAnimation();
                    activeHolograms.remove(hologram.id);
                    cancel();
                    return;
                }

                // Calculate animation progress (0.0 to 1.0)
                double progress = (double) hologram.animationTick / MAX_ANIMATION_TICKS;

                // Calculate current position using parabolic arc
                Location newLocation = calculateArcPosition(hologram, progress);

                // Check if hologram hit the ground
                if (newLocation.getY() <= hologram.startLocation.getWorld().getHighestBlockYAt(newLocation) + GROUND_CHECK_OFFSET) {
                    // Add small impact effect
                    spawnImpactEffect(newLocation, hologram.type);
                    hologram.stopAnimation();
                    activeHolograms.remove(hologram.id);
                    cancel();
                    return;
                }

                // Update hologram position
                hologram.currentLocation.setX(newLocation.getX());
                hologram.currentLocation.setY(newLocation.getY());
                hologram.currentLocation.setZ(newLocation.getZ());

                // Apply fade effect as it gets closer to ground
                String displayText = applyFadeEffect(hologram.text, newLocation, hologram.startLocation);

                // Update hologram
                List<String> lines = Arrays.asList(displayText);
                HologramManager.createOrUpdateHologram(hologram.id, hologram.currentLocation, lines, 0.25);

                hologram.animationTick++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L); // Run every tick for smooth animation
    }

    /**
     * Calculate the current position in the parabolic arc
     */
    private Location calculateArcPosition(AnimatedHologram hologram, double progress) {
        Location start = hologram.startLocation;
        Vector direction = hologram.targetDirection;

        // Horizontal movement (linear)
        double newX = start.getX() + (direction.getX() * progress);
        double newZ = start.getZ() + (direction.getZ() * progress);

        // Vertical movement (parabolic arc)
        // Use quadratic equation: y = ax² + bx + c
        // Where the arc peaks at progress = 0.3 and falls to ground
        double peakProgress = 0.3;
        double newY;

        if (progress <= peakProgress) {
            // Rising phase
            double risingProgress = progress / peakProgress;
            newY = start.getY() + (hologram.peakHeight - start.getY()) * risingProgress;
        } else {
            // Falling phase
            double fallingProgress = (progress - peakProgress) / (1.0 - peakProgress);
            double groundY = start.getWorld().getHighestBlockYAt((int) newX, (int) newZ);
            newY = hologram.peakHeight - (hologram.peakHeight - groundY) * (fallingProgress * fallingProgress);
        }

        return new Location(start.getWorld(), newX, newY, newZ);
    }

    /**
     * Apply fade effect as hologram approaches ground
     */
    private String applyFadeEffect(String originalText, Location currentLocation, Location startLocation) {
        double groundY = currentLocation.getWorld().getHighestBlockYAt(currentLocation);
        double heightAboveGround = currentLocation.getY() - groundY;

        if (heightAboveGround <= FADE_START_HEIGHT) {
            // Calculate fade intensity
            double fadeRatio = heightAboveGround / FADE_START_HEIGHT;

            if (fadeRatio <= 0.3) {
                // Very faded
                return ChatColor.GRAY + originalText.substring(originalText.lastIndexOf('§') >= 0 ?
                        originalText.lastIndexOf('§') + 2 : 0);
            } else if (fadeRatio <= 0.6) {
                // Moderately faded
                return ChatColor.DARK_GRAY + originalText.substring(originalText.lastIndexOf('§') >= 0 ?
                        originalText.lastIndexOf('§') + 2 : 0);
            }
        }

        return originalText;
    }

    /**
     * Spawn a small impact effect when hologram hits ground
     */
    private void spawnImpactEffect(Location location, HologramType type) {
        try {
            World world = location.getWorld();
            if (world == null) return;

            // Spawn appropriate particle effect
            switch (type) {
                case DAMAGE:
                case CRITICAL_DAMAGE:
                    world.spawnParticle(Particle.DAMAGE_INDICATOR, location, 3, 0.1, 0.1, 0.1, 0.01);
                    break;
                case HEAL:
                case LIFESTEAL:
                    world.spawnParticle(Particle.HEART, location, 2, 0.1, 0.1, 0.1, 0.01);
                    break;
                case BLOCK:
                    world.spawnParticle(Particle.CRIT, location, 5, 0.2, 0.1, 0.2, 0.01);
                    break;
                case DODGE:
                    world.spawnParticle(Particle.CLOUD, location, 3, 0.2, 0.1, 0.2, 0.01);
                    break;
                default:
                    world.spawnParticle(Particle.SMOKE, location, 1, 0.1, 0.1, 0.1, 0.01);
                    break;
            }
        } catch (Exception e) {
            // Ignore particle errors
        }
    }

    /**
     * Start the cleanup task to remove expired holograms
     */
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<String> toRemove = new ArrayList<>();

                for (Map.Entry<String, AnimatedHologram> entry : activeHolograms.entrySet()) {
                    AnimatedHologram hologram = entry.getValue();

                    if (!hologram.isActive || hologram.isExpired()) {
                        hologram.stopAnimation();
                        toRemove.add(entry.getKey());
                    }
                }

                for (String id : toRemove) {
                    activeHolograms.remove(id);
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    /**
     * Get the number of active holograms (for debugging)
     */
    public int getActiveHologramCount() {
        return activeHolograms.size();
    }

    /**
     * Force cleanup of all holograms
     */
    public void forceCleanup() {
        for (AnimatedHologram hologram : activeHolograms.values()) {
            hologram.stopAnimation();
        }
        activeHolograms.clear();
    }
}
package com.rednetty.server.mechanics.world.lootchests;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * vault key animation with proper positioning and smooth effects
 *  armor stand positioning issues and improved visual flow
 *
 * Version 5.0 - Enhanced positioning and animation flow
 */
public class VaultKeyAnimation {

    private final JavaPlugin plugin;
    private final Item originalItem;
    private final VaultChest targetVault;
    private final Player player;
    private final LootChestManager manager;
    private ArmorStand keyHolder;
    private BukkitRunnable animationTask;

    // Enhanced animation parameters
    private static final double FLOAT_SPEED = 0.35;
    private static final double FLOAT_HEIGHT = 1.8; // Higher float for better visibility
    private static final int ANIMATION_DURATION_TICKS = 50; // 2.5 seconds - slightly faster
    private static final double ROTATION_SPEED = 0.15; // Key rotation speed

    // Trail effect storage
    private final List<Location> trailLocations = new ArrayList<>();
    private int trailIndex = 0;

    public VaultKeyAnimation(JavaPlugin plugin, Item originalItem, VaultChest targetVault, Player player, LootChestManager manager) {
        this.plugin = plugin;
        this.originalItem = originalItem;
        this.targetVault = targetVault;
        this.player = player;
        this.manager = manager;
    }

    public void start() {
        // Validate vault before starting
        if (targetVault == null || !targetVault.hasValidLocation()) {
            player.sendMessage("§c✗ Invalid vault target!");
            manager.clearVaultAnimation(targetVault != null ? targetVault.getId() : null);
            return;
        }

        // Store original item data before removal
        ItemStack keyItem = originalItem.getItemStack().clone();
        Location startLoc = originalItem.getLocation().clone();

        // Remove the original item immediately
        originalItem.remove();

        // Create armor stand at proper height (accounting for the helmet position)
        // The helmet sits about 1.5 blocks above the armor stand's location
        Location armorStandLoc = startLoc.clone().subtract(0, 1.2, 0); // Adjusted for proper visual height
        keyHolder = startLoc.getWorld().spawn(armorStandLoc, ArmorStand.class);

        // Configure armor stand for optimal display
        keyHolder.setVisible(false);
        keyHolder.setGravity(false);
        keyHolder.setSmall(true);
        keyHolder.setArms(false);
        keyHolder.setBasePlate(false);
        keyHolder.setMarker(true);
        keyHolder.setInvulnerable(true);
        keyHolder.setSilent(true);
        keyHolder.setCollidable(false);

        // Set the key as helmet with initial rotation
        keyHolder.getEquipment().setHelmet(keyItem);
        keyHolder.setHeadPose(new EulerAngle(Math.toRadians(15), 0, 0)); // Slight tilt for visibility

        // Enhanced pickup effects
        playPickupEffects(player, startLoc);

        // Single concise message
        String tierColor = targetVault.getTier().getColor();
        player.sendMessage("§6✦ " + tierColor + targetVault.getDisplayName() + " §6key acquired!");

        // Start enhanced animation
        animationTask = new BukkitRunnable() {
            private int ticks = 0;
            private final Location start = startLoc.clone().add(0, FLOAT_HEIGHT, 0); // Float up first
            private final Location target = targetVault.getLocation().clone().add(0.5, 1.5, 0.5); // Higher target
            private double rotationAngle = 0;

            @Override
            public void run() {
                ticks++;

                // Safety checks
                if (ticks >= ANIMATION_DURATION_TICKS || keyHolder == null || keyHolder.isDead()) {
                    completeAutomatically();
                    return;
                }

                // Verify vault still exists
                Block vaultBlock = targetVault.getLocation().getBlock();
                if (vaultBlock.getType() != Material.VAULT) {
                    cancelWithError("§cVault no longer exists!");
                    return;
                }

                // Verify player still online and in range
                if (!player.isOnline() || player.getLocation().distance(target) > 100) {
                    cancelSilently();
                    return;
                }

                // Calculate animation progress
                double progress = (double) ticks / ANIMATION_DURATION_TICKS;
                double easedProgress = easeInOutCubic(progress); // Smoother easing

                // Multi-phase animation
                Location currentPos;
                if (progress < 0.2) {
                    // Phase 1: Float up (20% of animation)
                    double upProgress = progress / 0.2;
                    currentPos = interpolate(originalItem.getLocation(), start, easeOutQuad(upProgress));
                } else {
                    // Phase 2: Arc to vault (80% of animation)
                    double arcProgress = (progress - 0.2) / 0.8;
                    currentPos = calculateArcPosition(start, target, easeInOutQuad(arcProgress));
                }

                // Add floating bob effect
                double bobAmount = Math.sin(ticks * 0.2) * 0.05;
                currentPos.add(0, bobAmount, 0);

                // Update armor stand position (accounting for helmet offset)
                Location armorStandPos = currentPos.clone().subtract(0, 1.5, 0); // Helmet is ~1.5 blocks above armor stand
                keyHolder.teleport(armorStandPos);

                // Rotate the key smoothly
                rotationAngle += ROTATION_SPEED;
                keyHolder.setHeadPose(new EulerAngle(
                        Math.toRadians(15 + Math.sin(rotationAngle) * 10), // Tilt animation
                        rotationAngle, // Spin
                        Math.toRadians(Math.sin(rotationAngle * 0.5) * 5) // Slight wobble
                ));

                // Store trail positions for effect
                if (ticks % 2 == 0) {
                    trailLocations.add(currentPos.clone());
                    if (trailLocations.size() > 10) {
                        trailLocations.remove(0);
                    }
                }

                // Enhanced particle effects
                spawnEnhancedKeyParticles(currentPos, targetVault.getTier(), progress);

                // Draw trail
                drawParticleTrail();

                // Periodic sound effects (less frequent)
                if (ticks % 15 == 0) {
                    float pitch = 1.2f + (float)(progress * 0.8);
                    currentPos.getWorld().playSound(currentPos, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.3f, pitch);
                }

                // Distance-based effects as key approaches vault
                double distanceToVault = currentPos.distance(target);
                if (distanceToVault < 2.0 && ticks % 5 == 0) {
                    // Vault reaction particles
                    target.getWorld().spawnParticle(Particle.END_ROD, target, 2, 0.2, 0.2, 0.2, 0.02);
                }
            }
        };

        animationTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void playPickupEffects(Player player, Location loc) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.8f);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0.05);
    }

    private Location calculateArcPosition(Location start, Location end, double progress) {
        // Create an arc path instead of straight line
        double x = start.getX() + (end.getX() - start.getX()) * progress;
        double z = start.getZ() + (end.getZ() - start.getZ()) * progress;

        // Arc height calculation
        double baseY = start.getY() + (end.getY() - start.getY()) * progress;
        double arcHeight = Math.sin(progress * Math.PI) * 0.8; // Arc peak at 50% progress
        double y = baseY + arcHeight;

        return new Location(start.getWorld(), x, y, z);
    }

    private void drawParticleTrail() {
        // Draw a fading trail behind the key
        for (int i = 0; i < trailLocations.size(); i++) {
            Location loc = trailLocations.get(i);
            double opacity = (double) i / trailLocations.size(); // Fade older particles
            int particleCount = Math.max(1, (int)(opacity * 2));

            // Tier-based trail colors
            switch (targetVault.getTier()) {
                case TIER_6:
                    loc.getWorld().spawnParticle(Particle.END_ROD, loc, particleCount, 0.05, 0.05, 0.05, 0.0);
                    break;
                case TIER_5:
                    loc.getWorld().spawnParticle(Particle.GLOW, loc, particleCount, 0.05, 0.05, 0.05, 0.0);
                    break;
                case TIER_4:
                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, particleCount, 0.05, 0.05, 0.05, 0.0);
                    break;
                case TIER_3:
                    loc.getWorld().spawnParticle(Particle.SCULK_SOUL, loc, particleCount, 0.05, 0.05, 0.05, 0.0);
                    break;
                default:
                    loc.getWorld().spawnParticle(Particle.ENCHANT, loc, particleCount, 0.05, 0.05, 0.05, 0.0);
                    break;
            }
        }
    }

    private void spawnEnhancedKeyParticles(Location location, LootChestManager.ChestTier tier, double progress) {
        // More refined particle effects
        double intensity = 0.5 + (progress * 0.5); // Increase intensity as key approaches vault

        location = location.add(0, -0.2, 0);
        switch (tier) {
            case TIER_1:
                if (progress > 0.3) {
                    location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 1, 0.1, 0.1, 0.1, 0.01);
                }
                break;
            case TIER_2:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 2, 0.15 * intensity, 0.15 * intensity, 0.15 * intensity, 0.02);
                break;
            case TIER_3:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 2, 0.15 * intensity, 0.15 * intensity, 0.15 * intensity, 0.02);
                if (progress > 0.5) {
                    location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 1, 0.1, 0.1, 0.1, 0.01);
                }
                break;
            case TIER_4:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 3, 0.2 * intensity, 0.2 * intensity, 0.2 * intensity, 0.03);
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 1, 0.08, 0.08, 0.08, 0.01);
                break;
            case TIER_5:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 3, 0.2 * intensity, 0.2 * intensity, 0.2 * intensity, 0.03);
                if (progress > 0.6) {
                    location.getWorld().spawnParticle(Particle.GLOW, location, 1, 0.1, 0.1, 0.1, 0.01);
                }
                break;
            case TIER_6:
                location.getWorld().spawnParticle(Particle.ENCHANT, location, 2, 0.25 * intensity, 0.25 * intensity, 0.25 * intensity, 0.04);
                location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 1, 0.15, 0.15, 0.15, 0.02);
                if (progress > 0.8) {
                    location.getWorld().spawnParticle(Particle.FIREWORK, location, 1, 0.1, 0.1, 0.1, 0.03);
                }
                break;
        }

        // Universal spark effect for automation feel
        if (progress > 0.7) {
            location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void completeAutomatically() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }

        if (keyHolder != null && !keyHolder.isDead()) {
            // Get proper vault location for final effects
            Location vaultLoc = targetVault.getLocation().clone().add(0.5, 1.0, 0.5);

            // Spawn refined completion effects
            spawnCompletionEffects(vaultLoc, targetVault.getTier());

            // Remove armor stand
            keyHolder.remove();
        }

        // Clear trail
        trailLocations.clear();

        // Trigger automatic vault opening with slight delay
        YakRealms.debug("Animation complete, triggering automatic vault opening");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (targetVault != null && player != null && player.isOnline()) {
                manager.openVaultAutomatically(targetVault, player);
                player.playSound(player.getLocation(), Sound.BLOCK_VAULT_INSERT_ITEM, 1.0f, 1.0f);
            }
        }, 3L); // Very short delay (0.15 seconds)
    }

    private void spawnCompletionEffects(Location location, LootChestManager.ChestTier tier) {
        // Refined completion effects - less spammy
        int baseParticles = 10 + (tier.getLevel() * 3);
        double spread = 0.3 + (tier.getLevel() * 0.05);

        // Core completion burst
        location.getWorld().spawnParticle(Particle.ENCHANT, location, baseParticles, spread, spread, spread, 0.1);

        // Tier-specific effects
        switch (tier) {
            case TIER_1:
                location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f);
                break;
            case TIER_2:
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 5, spread, spread, spread, 0.05);
                location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.4f);
                break;
            case TIER_3:
                location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, 8, spread, spread, spread, 0.08);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 0.4f, 1.2f);
                break;
            case TIER_4:
                location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 10, spread, spread, spread, 0.1);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 0.5f, 1.5f);
                break;
            case TIER_5:
                location.getWorld().spawnParticle(Particle.END_ROD, location, 12, spread, spread, spread, 0.1);
                location.getWorld().spawnParticle(Particle.GLOW, location, 8, spread * 0.8, spread * 0.8, spread * 0.8, 0.08);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 0.7f, 1.8f);
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.5f);
                break;
            case TIER_6:
                location.getWorld().spawnParticle(Particle.END_ROD, location, 15, spread, spread, spread, 0.15);
                location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 10, spread, spread, spread, 0.1);
                location.getWorld().spawnParticle(Particle.FIREWORK, location, 8, spread * 1.2, spread * 1.2, spread * 1.2, 0.2);
                location.getWorld().playSound(location, Sound.BLOCK_BELL_USE, 0.8f, 2.0f);
                location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 2.0f);
                location.getWorld().playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.0f);
                break;
        }

        // Universal completion indicator
        location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 5, spread * 0.5, spread * 0.5, spread * 0.5, 0.05);
    }

    private void cancelSilently() {
        // Silent cancellation for when player goes out of range
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }

        if (keyHolder != null && !keyHolder.isDead()) {
            // Drop the key at current location
            Location dropLoc = keyHolder.getLocation().add(0, 1.5, 0); // Account for helmet position
            dropLoc.getWorld().dropItem(dropLoc, originalItem.getItemStack());
            keyHolder.remove();
        }

        trailLocations.clear();
        manager.clearVaultAnimation(targetVault.getId());
    }

    private void cancelWithError(String errorMessage) {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }

        if (keyHolder != null && !keyHolder.isDead()) {
            keyHolder.remove();
        }

        trailLocations.clear();

        // Return the key to the player
        if (player.isOnline()) {
            player.getInventory().addItem(originalItem.getItemStack());
            player.sendMessage(errorMessage);
        }

        // Clean up the animation status
        manager.clearVaultAnimation(targetVault.getId());
    }

    private Location interpolate(Location start, Location end, double progress) {
        double x = start.getX() + (end.getX() - start.getX()) * progress;
        double y = start.getY() + (end.getY() - start.getY()) * progress;
        double z = start.getZ() + (end.getZ() - start.getZ()) * progress;

        return new Location(start.getWorld(), x, y, z);
    }

    // Enhanced easing functions for smoother animation
    private double easeInOutQuad(double t) {
        return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    private double easeOutQuad(double t) {
        return t * (2 - t);
    }

    private double easeInOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
    }

    public void cancel() {
        cancelWithError("§cAnimation cancelled!");
    }
}
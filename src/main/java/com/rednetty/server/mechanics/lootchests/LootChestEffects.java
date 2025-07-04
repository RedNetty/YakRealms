// === LootChestEffects.java ===
package com.rednetty.server.mechanics.lootchests;

import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import com.rednetty.server.mechanics.lootchests.data.LootChestConfig;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Handles visual and audio effects for loot chests
 */
public class LootChestEffects {
    private LootChestConfig config;

    // Particle effects for each tier - Updated to match tier colors
    private static final Map<ChestTier, Particle> TIER_PARTICLES = Map.of(
            ChestTier.WOODEN, Particle.WHITE_ASH,           // White
            ChestTier.STONE, Particle.COMPOSTER,            // Light Green
            ChestTier.IRON, Particle.BUBBLE_POP,            // Cyan
            ChestTier.DIAMOND, Particle.PORTAL,             // Purple
            ChestTier.GOLDEN, Particle.GLOW,                // Yellow
            ChestTier.LEGENDARY, Particle.SOUL_FIRE_FLAME   // Dark Blue
    );

    // Sound effects for each tier
    private static final Map<ChestTier, Sound> TIER_SOUNDS = Map.of(
            ChestTier.WOODEN, Sound.BLOCK_WOOD_PLACE,
            ChestTier.STONE, Sound.BLOCK_STONE_PLACE,
            ChestTier.IRON, Sound.BLOCK_METAL_PLACE,
            ChestTier.DIAMOND, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            ChestTier.GOLDEN, Sound.BLOCK_BELL_USE,
            ChestTier.LEGENDARY, Sound.ENTITY_ENDER_DRAGON_GROWL
    );

    public void initialize() {
        // Get config instance
        this.config = LootChestManager.getInstance().getConfig();
    }

    /**
     * Spawns creation effects when a chest is created
     */
    public void spawnCreationEffects(Location location, ChestTier tier, ChestType type) {
        World world = location.getWorld();
        if (world == null) return;

        // Check if effects are enabled
        if (config != null && !config.isParticleEffectsEnabled() && !config.isSoundEffectsEnabled()) {
            return;
        }

        // Spawn particles
        if (config == null || config.isParticleEffectsEnabled()) {
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, location.clone().add(0.5, 1, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
        }

        // Play sound
        if (config == null || config.isSoundEffectsEnabled()) {
            Sound sound = TIER_SOUNDS.getOrDefault(tier, Sound.BLOCK_CHEST_OPEN);
            world.playSound(location, sound, 1.0f, 1.0f);
        }

        // Special effects for care packages
        if (type == ChestType.CARE_PACKAGE) {
            spawnCarePackageEffects(location);
        }
    }

    /**
     * Spawns special effects for care packages
     */
    public void spawnCarePackageEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Check if effects are enabled
        if (config != null && !config.isParticleEffectsEnabled() && !config.isSoundEffectsEnabled()) {
            return;
        }

        if (config == null || config.isParticleEffectsEnabled()) {
            world.spawnParticle(Particle.DRAGON_BREATH, location.clone().add(0.5, 1, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
        }

        if (config == null || config.isSoundEffectsEnabled()) {
            world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        }
    }

    /**
     * Spawns ambient particles around available chests
     */
    public void spawnChestParticles(Location location, ChestTier tier) {
        World world = location.getWorld();
        if (world == null) return;

        // Check if particle effects are enabled
        if (config != null && !config.isParticleEffectsEnabled()) {
            return;
        }

        Location particleLocation = location.clone().add(0.5, 1.5, 0.5);
        Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);

        world.spawnParticle(particle, particleLocation, 1, 0.1, 0.1, 0.1, 0.0005);
    }

    /**
     * Plays effects when a chest is opened
     */
    public void playChestOpenEffects(Player player, ChestTier tier) {
        if (config != null && !config.isSoundEffectsEnabled()) {
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

        // Additional tier-specific sounds
        Sound tierSound = TIER_SOUNDS.get(tier);
        if (tierSound != null) {
            player.playSound(player.getLocation(), tierSound, 0.5f, 1.2f);
        }
    }

    /**
     * Plays effects when a chest is broken
     */
    public void playChestBreakEffects(Location location, ChestTier tier) {
        World world = location.getWorld();
        if (world == null) return;

        // Check if effects are enabled
        if (config != null && !config.isParticleEffectsEnabled() && !config.isSoundEffectsEnabled()) {
            return;
        }

        if (config == null || config.isSoundEffectsEnabled()) {
            world.playSound(location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
            world.playEffect(location, Effect.STEP_SOUND, Material.OAK_WOOD);
        }

        // Spawn break particles
        if (config == null || config.isParticleEffectsEnabled()) {
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, location.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.1);
        }
    }

    /**
     * Plays effects when a chest closes
     */
    public void playChestCloseEffects(Location location, ChestTier tier) {
        World world = location.getWorld();
        if (world == null) return;

        if (config != null && !config.isSoundEffectsEnabled()) {
            return;
        }

        world.playSound(location, Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
        world.playEffect(location, Effect.STEP_SOUND, Material.OAK_WOOD);
    }

    /**
     * Plays sound when inventory is closed for a player
     */
    public void playInventoryCloseSound(Player player) {
        if (config != null && !config.isSoundEffectsEnabled()) {
            return;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 1.2f);
    }

    /**
     * Spawns effects when a chest respawns
     */
    public void spawnRespawnEffects(Location location, ChestTier tier) {
        World world = location.getWorld();
        if (world == null) return;

        // Check if effects are enabled
        if (config != null && !config.isParticleEffectsEnabled() && !config.isSoundEffectsEnabled()) {
            return;
        }

        if (config == null || config.isParticleEffectsEnabled()) {
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, location.clone().add(0.5, 1, 0.5), 15, 0.5, 0.5, 0.5, 0.1);
        }

        if (config == null || config.isSoundEffectsEnabled()) {
            Sound sound = TIER_SOUNDS.getOrDefault(tier, Sound.BLOCK_CHEST_OPEN);
            world.playSound(location, sound, 0.8f, 1.0f);
        }
    }

    /**
     * Spawns effects when a chest is removed
     */
    public void spawnRemovalEffects(Location location, ChestTier tier) {
        World world = location.getWorld();
        if (world == null) return;

        // Check if effects are enabled
        if (config != null && !config.isParticleEffectsEnabled() && !config.isSoundEffectsEnabled()) {
            return;
        }

        if (config == null || config.isParticleEffectsEnabled()) {
            world.spawnParticle(Particle.SMOKE_NORMAL, location.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
        }

        if (config == null || config.isSoundEffectsEnabled()) {
            world.playSound(location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.0f, 1.0f);
        }
    }

    /**
     * Checks if there are hostile mobs near the location
     */
    public boolean hasNearbyMobs(Location location) {
        // Check if mob checking is disabled
        if (config != null && !config.isMobCheckEnabled()) {
            return false;
        }

        try {
            for (Entity entity : location.getWorld().getNearbyEntities(location, 6.0, 6.0, 6.0)) {
                if (!(entity instanceof LivingEntity) ||
                        entity instanceof Player ||
                        entity instanceof Horse ||
                        entity.hasMetadata("pet") ||
                        entity.hasMetadata("npc")) {
                    continue;
                }

                // Additional checks for non-hostile entities
                LivingEntity living = (LivingEntity) entity;

                // Skip passive mobs like cows, sheep, etc.
                String entityType = entity.getType().name();
                if (entityType.contains("COW") || entityType.contains("SHEEP") ||
                        entityType.contains("PIG") || entityType.contains("CHICKEN") ||
                        entityType.contains("VILLAGER") || entityType.contains("CAT") ||
                        entityType.contains("DOG") || entityType.contains("WOLF")) {
                    continue;
                }

                return true;
            }
        } catch (Exception e) {
            // If there's an error checking for mobs, err on the side of caution
            return false;
        }

        return false;
    }
}
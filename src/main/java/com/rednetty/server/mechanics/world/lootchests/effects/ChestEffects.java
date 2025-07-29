package com.rednetty.server.mechanics.world.lootchests.effects;

import com.rednetty.server.mechanics.world.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.world.lootchests.types.ChestType;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handles visual and audio effects for loot chests
 * : Particle positioning and consistency issues
 */
public class ChestEffects {
    private final Logger logger;

    // Particle effects for each tier - matched to tier colors
    private static final Map<ChestTier, Particle> TIER_PARTICLES = Map.of(
            ChestTier.WOODEN, Particle.WHITE_ASH,           // White particles
            ChestTier.STONE, Particle.COMPOSTER,            // Light green particles
            ChestTier.IRON, Particle.BUBBLE_POP,            // Cyan particles
            ChestTier.DIAMOND, Particle.PORTAL,             // Purple particles
            ChestTier.LEGENDARY, Particle.GLOW,                // Yellow particles
            ChestTier.NETHER_FORGED, Particle.SOUL_FIRE_FLAME   // Dark blue particles
    );

    // Sound effects for each tier
    private static final Map<ChestTier, Sound> TIER_SOUNDS = Map.of(
            ChestTier.WOODEN, Sound.BLOCK_WOOD_PLACE,
            ChestTier.STONE, Sound.BLOCK_STONE_PLACE,
            ChestTier.IRON, Sound.BLOCK_METAL_PLACE,
            ChestTier.DIAMOND, Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            ChestTier.LEGENDARY, Sound.BLOCK_BELL_USE,
            ChestTier.NETHER_FORGED, Sound.ENTITY_ENDER_DRAGON_GROWL
    );

    // : Consistent particle positioning constants
    private static final double CHEST_CENTER_OFFSET_X = 0.5;
    private static final double CHEST_CENTER_OFFSET_Z = 0.5;
    private static final double CHEST_TOP_OFFSET_Y = 1.0;      // Just above the chest
    private static final double CHEST_AMBIENT_OFFSET_Y = 1.2;  // Slightly higher for ambient effects
    private static final double CHEST_CREATION_OFFSET_Y = 1.1; // Between top and ambient

    // Effect settings
    private boolean particleEffectsEnabled = true;
    private boolean soundEffectsEnabled = true;
    private boolean mobCheckEnabled = true;

    public ChestEffects() {
        this.logger = org.bukkit.Bukkit.getLogger();
    }

    /**
     * Initializes the effects system
     */
    public void initialize() {
        logger.info("Chest effects system initialized with consistent particle positioning");
    }

    // === Creation Effects ===

    /**
     * Plays effects when a chest is created
     * : Consistent particle positioning
     */
    public void playCreationEffects(Location location, ChestTier tier, ChestType type) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();

        // Spawn particles - : Consistent positioning
        if (particleEffectsEnabled) {
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            Location particleLoc = getChestParticleLocation(location, CHEST_CREATION_OFFSET_Y);
            world.spawnParticle(particle, particleLoc, 20, 0.5, 0.5, 0.5, 0.1);
        }

        // Play sound
        if (soundEffectsEnabled) {
            Sound sound = TIER_SOUNDS.getOrDefault(tier, Sound.BLOCK_CHEST_OPEN);
            world.playSound(location, sound, 1.0f, 1.0f);
        }

        // Special effects for care packages
        if (type == ChestType.CARE_PACKAGE) {
            playCarePackageEffects(location);
        }

        // Special effects for special chests
        if (type == ChestType.SPECIAL) {
            playSpecialChestEffects(location, tier);
        }
    }

    /**
     * Plays special effects for care packages
     * : Consistent particle positioning
     */
    public void playCarePackageEffects(Location location) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();
        Location centerLoc = getChestParticleLocation(location, CHEST_CREATION_OFFSET_Y);

        if (particleEffectsEnabled) {
            // Dragon breath particles for dramatic effect
            world.spawnParticle(Particle.DRAGON_BREATH, centerLoc, 50, 1.0, 1.0, 1.0, 0.1);

            // Additional golden particles
            world.spawnParticle(Particle.GLOW, centerLoc, 30, 0.8, 0.8, 0.8, 0.05);
        }

        if (soundEffectsEnabled) {
            // Thunder sound for dramatic arrival
            world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.0f);

            // Bell chime for notification
            Bukkit.getScheduler().runTaskLater(com.rednetty.server.YakRealms.getInstance(), () -> {
                world.playSound(location, Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
            }, 10L);
        }
    }

    /**
     * Plays special effects for special chests
     * : Consistent particle positioning
     */
    private void playSpecialChestEffects(Location location, ChestTier tier) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();
        Location centerLoc = getChestParticleLocation(location, CHEST_CREATION_OFFSET_Y);

        if (particleEffectsEnabled) {
            //  particles for special chests
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, centerLoc, 40, 0.8, 0.8, 0.8, 0.1);

            // Additional enchantment particles
            world.spawnParticle(Particle.ENCHANTMENT_TABLE, centerLoc, 20, 0.5, 0.5, 0.5, 0.1);
        }

        if (soundEffectsEnabled) {
            Sound tierSound = TIER_SOUNDS.getOrDefault(tier, Sound.BLOCK_CHEST_OPEN);
            world.playSound(location, tierSound, 1.0f, 0.8f);

            // Magical chime
            world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.5f);
        }
    }

    // === Interaction Effects ===

    /**
     * Plays effects when a chest is opened
     */
    public void playOpenEffects(Player player, ChestTier tier) {
        if (player == null) return;

        Location playerLoc = player.getLocation();

        if (soundEffectsEnabled) {
            // Standard chest open sound
            player.playSound(playerLoc, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

            // Tier-specific sound
            Sound tierSound = TIER_SOUNDS.get(tier);
            if (tierSound != null) {
                player.playSound(playerLoc, tierSound, 0.5f, 1.2f);
            }

            // Special sound for higher tiers
            if (tier.getLevel() >= 5) {
                player.playSound(playerLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 2.0f);
            }
        }
    }

    /**
     * Plays effects when a chest is broken
     * : Consistent particle positioning
     */
    public void playBreakEffects(Location location, ChestTier tier) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();
        Location centerLoc = getChestParticleLocation(location, CHEST_TOP_OFFSET_Y);

        if (soundEffectsEnabled) {
            // Breaking sound
            world.playSound(location, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.2f);

            // Wood break effect
            world.playEffect(location, Effect.STEP_SOUND, Material.OAK_WOOD);
        }

        if (particleEffectsEnabled) {
            // Tier-specific break particles
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, centerLoc, 15, 0.3, 0.3, 0.3, 0.1);

            // Wood particles
            world.spawnParticle(Particle.BLOCK_CRACK, centerLoc, 10, 0.3, 0.3, 0.3, 0.1,
                    Material.OAK_WOOD.createBlockData());
        }
    }

    /**
     * Plays effects when a chest respawns
     * : Consistent particle positioning
     */
    public void playRespawnEffects(Location location, ChestTier tier) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();
        Location centerLoc = getChestParticleLocation(location, CHEST_CREATION_OFFSET_Y);

        if (particleEffectsEnabled) {
            // Tier-specific respawn particles
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, centerLoc, 25, 0.5, 0.5, 0.5, 0.1);

            // Additional sparkle effect
            world.spawnParticle(Particle.ENCHANTMENT_TABLE, centerLoc, 15, 0.3, 0.3, 0.3, 0.05);
        }

        if (soundEffectsEnabled) {
            Sound tierSound = TIER_SOUNDS.getOrDefault(tier, Sound.BLOCK_CHEST_OPEN);
            world.playSound(location, tierSound, 0.8f, 1.0f);

            // Magical appear sound
            world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        }
    }

    /**
     * Plays effects when a chest is removed
     * : Consistent particle positioning
     */
    public void playRemovalEffects(Location location, ChestTier tier) {
        if (location == null || location.getWorld() == null) return;

        World world = location.getWorld();
        Location centerLoc = getChestParticleLocation(location, CHEST_TOP_OFFSET_Y);

        if (particleEffectsEnabled) {
            // Smoke particles for disappearing effect
            world.spawnParticle(Particle.SMOKE_NORMAL, centerLoc, 20, 0.3, 0.3, 0.3, 0.05);

            // Tier-specific particles fading away
            Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
            world.spawnParticle(particle, centerLoc, 10, 0.4, 0.4, 0.4, 0.02);
        }

        if (soundEffectsEnabled) {
            // Extinguish sound for removal
            world.playSound(location, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 0.8f, 1.0f);
        }
    }

    // === Ambient Effects ===

    /**
     * Spawns ambient particles around available chests
     * : Proper positioning above chest
     */
    public void spawnAmbientParticles(Location location, ChestTier tier) {
        if (location == null || location.getWorld() == null || !particleEffectsEnabled) return;

        World world = location.getWorld();
        // : Use consistent ambient particle height
        Location particleLoc = getChestParticleLocation(location, CHEST_AMBIENT_OFFSET_Y);

        // Gentle ambient particles
        Particle particle = TIER_PARTICLES.getOrDefault(tier, Particle.WHITE_ASH);
        world.spawnParticle(particle, particleLoc, 1, 0.1, 0.1, 0.1, 0.001);

        // Special ambient effects for higher tiers
        if (tier.getLevel() >= 4) {
            // Additional enchantment particles for rare chests
            world.spawnParticle(Particle.ENCHANTMENT_TABLE, particleLoc, 1, 0.2, 0.2, 0.2, 0.01);
        }
    }

    // === Inventory Effects ===

    /**
     * Plays sound when inventory is closed
     */
    public void playInventoryCloseSound(Player player) {
        if (player == null || !soundEffectsEnabled) return;

        Location playerLoc = player.getLocation();

        // Standard inventory close sound
        player.playSound(playerLoc, Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);

        // Subtle additional sound
        player.playSound(playerLoc, Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f);
    }

    // === Utility Methods ===

    /**
     * : Helper method for consistent particle positioning
     */
    private Location getChestParticleLocation(Location chestLocation, double yOffset) {
        return chestLocation.clone().add(CHEST_CENTER_OFFSET_X, yOffset, CHEST_CENTER_OFFSET_Z);
    }

    /**
     * Checks if there are hostile mobs near the location
     */
    public boolean hasNearbyMobs(Location location) {
        if (location == null || location.getWorld() == null || !mobCheckEnabled) {
            return false;
        }

        try {
            // Check for hostile entities within 6 blocks
            for (Entity entity : location.getWorld().getNearbyEntities(location, 6.0, 6.0, 6.0)) {
                if (isHostileMob(entity)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // If there's an error checking for mobs, err on the side of caution
            logger.warning("Error checking for nearby mobs: " + e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Determines if an entity is a hostile mob
     */
    private boolean isHostileMob(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }

        // Skip players
        if (entity instanceof Player) {
            return false;
        }

        // Skip entities with special metadata (pets, NPCs, etc.)
        if (entity.hasMetadata("pet") || entity.hasMetadata("npc") || entity.hasMetadata("friendly")) {
            return false;
        }

        // Check entity type for known hostile mobs
        String entityType = entity.getType().name();

        // Skip passive mobs
        if (isPassiveMob(entityType)) {
            return false;
        }

        // Skip tamed animals
        if (entity.hasMetadata("tamed")) {
            return false;
        }

        // Consider everything else potentially hostile
        return true;
    }

    /**
     * Checks if an entity type is a passive mob
     */
    private boolean isPassiveMob(String entityType) {
        return entityType.contains("COW") || entityType.contains("SHEEP") ||
                entityType.contains("PIG") || entityType.contains("CHICKEN") ||
                entityType.contains("VILLAGER") || entityType.contains("CAT") ||
                entityType.contains("DOG") || entityType.contains("WOLF") ||
                entityType.contains("HORSE") || entityType.contains("RABBIT") ||
                entityType.contains("BAT") || entityType.contains("SQUID") ||
                entityType.contains("AXOLOTL") || entityType.contains("BEE") ||
                entityType.contains("TROPICAL_FISH") || entityType.contains("COD") ||
                entityType.contains("SALMON") || entityType.contains("PUFFERFISH");
    }

    // === Configuration Methods ===

    /**
     * Sets whether particle effects are enabled
     */
    public void setParticleEffectsEnabled(boolean enabled) {
        this.particleEffectsEnabled = enabled;
    }

    /**
     * Sets whether sound effects are enabled
     */
    public void setSoundEffectsEnabled(boolean enabled) {
        this.soundEffectsEnabled = enabled;
    }

    /**
     * Sets whether mob checking is enabled
     */
    public void setMobCheckEnabled(boolean enabled) {
        this.mobCheckEnabled = enabled;
    }

    /**
     * Gets whether particle effects are enabled
     */
    public boolean isParticleEffectsEnabled() {
        return particleEffectsEnabled;
    }

    /**
     * Gets whether sound effects are enabled
     */
    public boolean isSoundEffectsEnabled() {
        return soundEffectsEnabled;
    }

    /**
     * Gets whether mob checking is enabled
     */
    public boolean isMobCheckEnabled() {
        return mobCheckEnabled;
    }

    // === Testing and Debug Methods ===

    /**
     * Tests all effects for a specific tier (admin command)
     */
    public void testEffects(Player player, ChestTier tier) {
        if (player == null) return;

        Location testLoc = player.getLocation().add(2, 0, 0);

        player.sendMessage(ChatColor.YELLOW + "Testing effects for " + tier.getDisplayName() + "...");
        player.sendMessage(ChatColor.GRAY + "Particle positions: Top=" + CHEST_TOP_OFFSET_Y +
                ", Ambient=" + CHEST_AMBIENT_OFFSET_Y + ", Creation=" + CHEST_CREATION_OFFSET_Y);

        // Test creation effects
        playCreationEffects(testLoc, tier, ChestType.NORMAL);

        // Test ambient effects
        Bukkit.getScheduler().runTaskLater(com.rednetty.server.YakRealms.getInstance(), () -> {
            spawnAmbientParticles(testLoc, tier);
        }, 20L);

        // Test open effects
        Bukkit.getScheduler().runTaskLater(com.rednetty.server.YakRealms.getInstance(), () -> {
            playOpenEffects(player, tier);
        }, 40L);

        // Test break effects
        Bukkit.getScheduler().runTaskLater(com.rednetty.server.YakRealms.getInstance(), () -> {
            playBreakEffects(testLoc, tier);
        }, 60L);

        // Test respawn effects
        Bukkit.getScheduler().runTaskLater(com.rednetty.server.YakRealms.getInstance(), () -> {
            playRespawnEffects(testLoc, tier);
        }, 80L);
    }

    /**
     * Gets effect system statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("particleEffectsEnabled", particleEffectsEnabled);
        stats.put("soundEffectsEnabled", soundEffectsEnabled);
        stats.put("mobCheckEnabled", mobCheckEnabled);
        stats.put("supportedTiers", ChestTier.values().length);
        stats.put("supportedParticles", TIER_PARTICLES.size());
        stats.put("supportedSounds", TIER_SOUNDS.size());
        stats.put("chestTopOffset", CHEST_TOP_OFFSET_Y);
        stats.put("chestAmbientOffset", CHEST_AMBIENT_OFFSET_Y);
        stats.put("chestCreationOffset", CHEST_CREATION_OFFSET_Y);
        return stats;
    }

    @Override
    public String toString() {
        return "ChestEffects{particles=" + particleEffectsEnabled +
                ", sounds=" + soundEffectsEnabled +
                ", mobCheck=" + mobCheckEnabled +
                ", ambientHeight=" + CHEST_AMBIENT_OFFSET_Y + "}";
    }
}
package com.rednetty.server.core.mechanics.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.settings.Toggles;
import com.rednetty.server.core.mechanics.player.social.friends.Buddies;
import com.rednetty.server.core.mechanics.player.stamina.Energy;
import com.rednetty.server.utils.math.BoundingBox;
import com.rednetty.server.utils.math.RayTrace;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * magical staff weapons system with improved visuals and 1.21.7 compatibility
 * Maintains backwards compatibility while providing modern particle effects and sounds
 */
public class MagicStaff implements Listener {
    // Constants
    private static final String STAFF_COOLDOWN_META = "staffCooldown";
    private static final long STAFF_COOLDOWN_DURATION = 350L;
    private static final int MAX_PROJECTILE_TICKS = 35;
    private static final double MAX_PROJECTILE_DISTANCE = 25.0;
    private static final double PROJECTILE_COLLISION_RADIUS = 0.8;
    private static final float IMPACT_SOUND_VOLUME = 0.5f;
    private static final float IMPACT_SOUND_PITCH = 1.2f;

    // Staff tracking
    private static final Map<UUID, ItemStack> lastUsedStaff = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastStaffShotTime = new ConcurrentHashMap<>();

    // Dependencies
    private final YakPlayerManager playerManager;
    private static MagicStaff instance;

    /**
     * Constructor initializes dependencies
     */
    public MagicStaff() {
        this.playerManager = YakPlayerManager.getInstance();
        instance = this;
    }

    /**
     * Get the singleton instance
     */
    public static MagicStaff getInstance() {
        if (instance == null) {
            instance = new MagicStaff();
        }
        return instance;
    }

    /**
     * Creates and launches an enhanced magical projectile with tier-specific effects
     */
    public static void shootMagicProjectile(LivingEntity shooter, StaffType type) {
        if (shooter == null || type == null) {
            YakRealms.error("Attempted to shoot magic projectile with null shooter or type", null);
            return;
        }

        if (shooter.getLocation() == null || shooter.getLocation().getWorld() == null) {
            YakRealms.error("Shooter has invalid location", null);
            return;
        }

        // Enhanced shoot sound and effect
        playShootSound(shooter.getLocation(), type);
        createLaunchEffect(shooter.getEyeLocation(), type);

        // Create enhanced ray trace for projectile
        new BukkitRunnable() {
            Location eyeLocation = shooter.getEyeLocation();
            final Location startLocation = eyeLocation != null ? eyeLocation.clone() : shooter.getLocation().clone();
            final RayTrace magic;
            final int distanceSpeed;
            final double precision;
            final Iterator<Vector> trail;
            Location lastKnownLocation = startLocation.clone();

            int ticks = 0;
            boolean hasHit = false;
            double totalDistance = 0.0;

            {
                if (eyeLocation != null) {
                    magic = new RayTrace(
                            eyeLocation.add(0, -0.25, 0).toVector(),
                            eyeLocation.getDirection()
                    );
                    // Balanced speed with slight tier bonus
                    distanceSpeed = shooter instanceof Player ? 100 + (type.getTier() * 5) : 60;
                    precision = shooter instanceof Player ? 0.8 : 0.3;
                    trail = magic.traverse(distanceSpeed, precision).iterator();
                } else {
                    magic = null;
                    distanceSpeed = 0;
                    precision = 0;
                    trail = null;
                    eyeLocation = shooter.getLocation();
                }
            }

            @Override
            public void run() {
                try {
                    // Validate state
                    if (magic == null || trail == null || !trail.hasNext() ||
                            ticks > MAX_PROJECTILE_TICKS || hasHit || totalDistance > MAX_PROJECTILE_DISTANCE) {

                        if (totalDistance > MAX_PROJECTILE_DISTANCE && !hasHit && lastKnownLocation != null) {
                            createDissipationEffect(lastKnownLocation, type);
                        }

                        this.cancel();
                        return;
                    }

                    // Check if shooter or world is invalid
                    if (shooter.isDead() || !shooter.isValid() ||
                            shooter.getLocation() == null || shooter.getLocation().getWorld() == null) {
                        this.cancel();
                        return;
                    }

                    // Process multiple positions per tick for smoother movement
                    for (int i = 0; i < 4 && trail.hasNext() && !hasHit && totalDistance <= MAX_PROJECTILE_DISTANCE; i++) {
                        Vector pos = trail.next();
                        if (pos == null) {
                            continue;
                        }

                        Location currentLocation = pos.toLocation(shooter.getWorld());
                        if (currentLocation == null) {
                            continue;
                        }

                        // Update last known location and total distance traveled
                        lastKnownLocation = currentLocation.clone();
                        totalDistance = startLocation.distance(currentLocation);

                        // Check if we've exceeded maximum range
                        if (totalDistance > MAX_PROJECTILE_DISTANCE) {
                            createDissipationEffect(currentLocation, type);
                            hasHit = true;
                            this.cancel();
                            return;
                        }

                        // Check for block collision
                        Block block = currentLocation.getBlock();
                        if (block != null && block.getType().isSolid() &&
                                magic.intersectsBox(pos, new BoundingBox(block))) {
                            createImpactEffect(currentLocation, type);
                            hasHit = true;
                            this.cancel();
                            return;
                        }

                        // Check for entity collision
                        try {
                            for (Entity entity : shooter.getWorld().getNearbyEntities(currentLocation, 1.5f, 1.5f, 1.5f)) {
                                if (!(entity instanceof LivingEntity target) || entity == shooter || hasHit) {
                                    continue;
                                }

                                // Skip if we should ignore damage
                                if (shouldIgnoreDamage(shooter, target)) {
                                    continue;
                                }

                                // Check if projectile intersects with entity
                                if (magic.intersectsBox(pos, PROJECTILE_COLLISION_RADIUS, new BoundingBox(target))) {
                                    hasHit = true;

                                    // Handle different target types
                                    if (target instanceof Horse && target.getPassenger() != null) {
                                        handleHorsePassengerDamage(shooter, target);
                                    } else if (shooter instanceof Player &&
                                            shooter.getEquipment() != null &&
                                            shooter.getEquipment().getItemInMainHand() != null &&
                                            shooter.getEquipment().getItemInMainHand().getType().name().contains("_HOE")) {
                                        handlePlayerStaffDamage(shooter, target);
                                    } else {
                                        handleDefaultDamage(shooter, target);
                                    }

                                    createImpactEffect(currentLocation, type);
                                    this.cancel();
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            YakRealms.getInstance().getLogger().warning("Error checking entity collision: " + e.getMessage());
                        }

                        // Create enhanced projectile trail effect
                        createTrailEffect(currentLocation, type, ticks);
                    }
                    ticks++;
                } catch (Exception e) {
                    YakRealms.error("Error in magic projectile task", e);
                    this.cancel();
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }

    /**
     * launch effect with tier-specific visuals
     */
    private static void createLaunchEffect(Location location, StaffType type) {
        if (location == null || location.getWorld() == null || type == null) return;

        try {
            World world = location.getWorld();

            // Base colored dust particle
            DustOptions dustOptions = new DustOptions(type.getColor(), 1.5f);
            spawnParticle(world, Particle.DUST, location, 8, 0.3, 0.3, 0.3, 0, dustOptions);

            // Tier-specific launch effects
            switch (type) {
                case WOOD:
                    spawnParticle(world, getParticle("CRIT"), location, 5, 0.2, 0.2, 0.2, 0.1);
                    break;
                case STONE:
                    spawnParticle(world, getParticle("CRIT"), location, 6, 0.2, 0.2, 0.2, 0.1);
                    spawnParticle(world, getParticle("ENCHANT"), location, 3, 0.3, 0.3, 0.3, 0.5);
                    break;
                case IRON:
                    spawnParticle(world, getParticle("CRIT_MAGIC"), location, 5, 0.2, 0.2, 0.2, 0.1);
                    spawnParticle(world, getParticle("ENCHANT"), location, 5, 0.3, 0.3, 0.3, 0.8);
                    break;
                case DIAMOND:
                    spawnParticle(world, getParticle("CRIT_MAGIC"), location, 8, 0.3, 0.3, 0.3, 0.1);
                    spawnParticle(world, getParticle("ENCHANT"), location, 10, 0.4, 0.4, 0.4, 1.0);
                    break;
                case GOLD:
                    spawnParticle(world, getParticle("CRIT_MAGIC"), location, 6, 0.3, 0.3, 0.3, 0.1);
                    spawnParticle(world, getParticle("FIREWORK"), location, 5, 0.2, 0.2, 0.2, 0.1);
                    break;
                case NETHERITE:
                    spawnParticle(world, getParticle("CRIT_MAGIC"), location, 12, 0.4, 0.4, 0.4, 0.15);
                    spawnParticle(world, getParticle("ENCHANT"), location, 15, 0.5, 0.5, 0.5, 1.5);
                    spawnParticle(world, getParticle("FLAME"), location, 8, 0.2, 0.2, 0.2, 0.05);
                    break;
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error creating launch effect: " + e.getMessage());
        }
    }

    /**
     * trail effects with tier-specific animations
     */
    private static void createTrailEffect(Location location, StaffType type, int ticks) {
        if (location == null || location.getWorld() == null || type == null) return;

        try {
            World world = location.getWorld();

            // Base trail - colored dust and magic
            DustOptions dustOptions = new DustOptions(type.getColor(), 1.0f);
            spawnParticle(world, Particle.DUST, location, 1, 0.05, 0.05, 0.05, 0, dustOptions);
            spawnParticle(world, getParticle("SPELL_MOB"), location, 0, type.getRed(), type.getGreen(), type.getBlue(), 1);

            // Tier-specific trail enhancements
            switch (type) {
                case WOOD:
                    // Simple sparks
                    if (ticks % 4 == 0) {
                        spawnParticle(world, getParticle("CRIT"), location, 1, 0.1, 0.1, 0.1, 0.0);
                    }
                    break;
                case STONE:
                    // Occasional sparkle
                    if (ticks % 3 == 0) {
                        spawnParticle(world, getParticle("ENCHANT"), location, 1, 0.1, 0.1, 0.1, 0.2);
                    }
                    break;
                case IRON:
                    // Magic crits
                    if (ticks % 3 == 0) {
                        spawnParticle(world, getParticle("CRIT_MAGIC"), location, 1, 0.1, 0.1, 0.1, 0.0);
                    }
                    break;
                case DIAMOND:
                    // Consistent enchant glow
                    spawnParticle(world, getParticle("ENCHANT"), location, 2, 0.15, 0.15, 0.15, 0.3);
                    break;
                case GOLD:
                    // Firework sparks
                    if (ticks % 2 == 0) {
                        spawnParticle(world, getParticle("FIREWORK"), location, 1, 0.1, 0.1, 0.1, 0.0);
                    }
                    break;
                case NETHERITE:
                    // Full magical trail
                    spawnParticle(world, getParticle("ENCHANT"), location, 3, 0.12, 0.12, 0.12, 0.5);
                    if (ticks % 2 == 0) {
                        spawnParticle(world, getParticle("CRIT_MAGIC"), location, 1, 0.08, 0.08, 0.08, 0.0);
                    }
                    if (ticks % 4 == 0) {
                        spawnParticle(world, getParticle("FLAME"), location, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                    break;
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error creating trail effect: " + e.getMessage());
        }
    }

    /**
     * impact effects with tier-specific explosions
     */
    private static void createImpactEffect(Location location, StaffType type) {
        if (location == null || location.getWorld() == null || type == null) return;

        try {
            World world = location.getWorld();

            // Base impact - colored explosion
            DustOptions dustOptions = new DustOptions(type.getColor(), 1.3f);
            spawnParticle(world, Particle.DUST, location, 10 + type.getTier() * 2, 0.3, 0.3, 0.3, 0, dustOptions);
            spawnParticle(world, getParticle("CRIT_MAGIC"), location, 5 + type.getTier(), 0.25, 0.25, 0.25, 0.1);

            // Tier-specific impact effects
            switch (type) {
                case WOOD:
                    spawnParticle(world, getParticle("CRIT"), location, 8, 0.3, 0.3, 0.3, 0.1);
                    break;
                case STONE:
                    spawnParticle(world, getParticle("CRIT"), location, 10, 0.3, 0.3, 0.3, 0.1);
                    spawnParticle(world, getParticle("SPELL_WITCH"), location, 5, 0.2, 0.2, 0.2, 0.05);
                    break;
                case IRON:
                    spawnParticle(world, getParticle("SPELL_WITCH"), location, 8, 0.3, 0.3, 0.3, 0.05);
                    spawnParticle(world, getParticle("ENCHANT"), location, 5, 0.3, 0.3, 0.3, 0.5);
                    break;
                case DIAMOND:
                    spawnParticle(world, getParticle("SPELL_WITCH"), location, 12, 0.4, 0.4, 0.4, 0.08);
                    spawnParticle(world, getParticle("ENCHANT"), location, 15, 0.5, 0.5, 0.5, 1.0);
                    break;
                case GOLD:
                    spawnParticle(world, getParticle("SPELL_WITCH"), location, 10, 0.4, 0.4, 0.4, 0.06);
                    spawnParticle(world, getParticle("FIREWORK"), location, 8, 0.3, 0.3, 0.3, 0.1);
                    break;
                case NETHERITE:
                    spawnParticle(world, getParticle("SPELL_WITCH"), location, 18, 0.5, 0.5, 0.5, 0.1);
                    spawnParticle(world, getParticle("ENCHANT"), location, 25, 0.6, 0.6, 0.6, 1.5);
                    spawnParticle(world, getParticle("FIREWORK"), location, 12, 0.4, 0.4, 0.4, 0.15);
                    spawnParticle(world, getParticle("FLAME"), location, 8, 0.3, 0.3, 0.3, 0.05);
                    break;
            }

            playImpactSound(location, type);

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error creating impact effect: " + e.getMessage());
        }
    }

    /**
     * dissipation effect when projectile reaches max range
     */
    private static void createDissipationEffect(Location location, StaffType type) {
        if (location == null || location.getWorld() == null || type == null) return;

        try {
            World world = location.getWorld();

            // Gentle dissipation particles
            DustOptions dustOptions = new DustOptions(type.getColor(), 0.8f);
            spawnParticle(world, Particle.DUST, location, 5 + type.getTier(), 0.5, 0.5, 0.5, 0, dustOptions);
            spawnParticle(world, getParticle("SPELL_WITCH"), location, 5 + type.getTier(), 0.4, 0.4, 0.4, 0.02);

            // Higher tier gets enchant sparkles
            if (type.getTier() >= 3) {
                spawnParticle(world, getParticle("ENCHANT"), location, type.getTier(), 0.3, 0.3, 0.3, 0.2);
            }

            // Soft dissipation sound
            playDissipationSound(location, type);

        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error creating dissipation effect: " + e.getMessage());
        }
    }

    /**
     * sound system with backwards compatibility
     */
    private static void playShootSound(Location location, StaffType type) {
        if (location == null || location.getWorld() == null) return;

        try {
            World world = location.getWorld();
            Sound shootSound = getSound("ENTITY_BLAZE_SHOOT", "BLAZE_BREATH");

            float pitch = 1.3f + (type.getTier() * 0.15f);
            float volume = 0.8f + (type.getTier() * 0.1f);

            world.playSound(location, shootSound, volume, pitch);

            // Higher tier additional sounds
            if (type.getTier() >= 4) {
                Sound enchantSound = getSound("BLOCK_ENCHANTMENT_TABLE_USE", "ENCHANT_TABLE");
                world.playSound(location, enchantSound, 0.3f, 2.0f);
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error playing shoot sound: " + e.getMessage());
        }
    }

    private static void playImpactSound(Location location, StaffType type) {
        if (location == null || location.getWorld() == null) return;

        try {
            World world = location.getWorld();
            Sound impactSound = getSound("ENTITY_GENERIC_EXPLODE", "EXPLODE");

            float pitch = IMPACT_SOUND_PITCH + (type.getTier() * 0.1f);
            float volume = IMPACT_SOUND_VOLUME + (type.getTier() * 0.05f);

            world.playSound(location, impactSound, volume, pitch);
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error playing impact sound: " + e.getMessage());
        }
    }

    private static void playDissipationSound(Location location, StaffType type) {
        if (location == null || location.getWorld() == null) return;

        try {
            World world = location.getWorld();
            Sound dissipationSound = getSound("BLOCK_FIRE_EXTINGUISH", "FIRE_IGNITE");

            float pitch = 1.6f + (type.getTier() * 0.1f);
            float volume = 0.25f + (type.getTier() * 0.03f);

            world.playSound(location, dissipationSound, volume, pitch);
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().info("Error playing dissipation sound: " + e.getMessage());
        }
    }

    /**
     * Backwards compatible particle getter with modern names
     */
    private static Particle getParticle(String name) {
        try {
            switch (name) {
                case "SPELL_MOB":
                    // Try modern name first, fallback to old
                    try {
                        return Particle.valueOf("ENTITY_EFFECT");
                    } catch (IllegalArgumentException e) {
                        return Particle.valueOf("SPELL_MOB");
                    }
                case "SPELL_WITCH":
                    try {
                        return Particle.valueOf("WITCH");
                    } catch (IllegalArgumentException e) {
                        return Particle.valueOf("SPELL_WITCH");
                    }
                case "CRIT_MAGIC":
                    try {
                        return Particle.valueOf("ENCHANTED_HIT");
                    } catch (IllegalArgumentException e) {
                        return Particle.valueOf("CRIT_MAGIC");
                    }
                case "ENCHANT":
                    try {
                        return Particle.valueOf("ENCHANT");
                    } catch (IllegalArgumentException e) {
                        return Particle.valueOf("ENCHANTMENT_TABLE");
                    }
                case "FIREWORK":
                    return Particle.valueOf("FIREWORKS_SPARK");
                default:
                    return Particle.valueOf(name);
            }
        } catch (IllegalArgumentException e) {
            return Particle.DUST; // Ultimate fallback
        }
    }

    /**
     * Backwards compatible sound getter
     */
    @SuppressWarnings("deprecation")
    private static Sound getSound(String modernName, String fallbackName) {
        try {
            return Sound.valueOf(modernName);
        } catch (IllegalArgumentException e) {
            try {
                return Sound.valueOf(fallbackName);
            } catch (IllegalArgumentException e2) {
                return Sound.valueOf("CLICK"); // Ultimate fallback
            }
        }
    }

    /**
     * Safe particle spawning with backwards compatibility
     */
    private static void spawnParticle(World world, Particle particle, Location location,
                                      int count, double offsetX, double offsetY, double offsetZ, double extra, Object data) {
        try {
            if (data != null) {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
            } else {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            }
        } catch (Exception e) {
            try {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            } catch (Exception e2) {
                // Silent fail - don't spam logs for particle issues
            }
        }
    }

    private static void spawnParticle(World world, Particle particle, Location location,
                                      int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(world, particle, location, count, offsetX, offsetY, offsetZ, extra, null);
    }

    /**
     * Cleans up resources when disabling
     */
    public void onDisable() {
        lastUsedStaff.clear();
        lastStaffShotTime.clear();
        YakRealms.log("Magic Staff system has been disabled");
    }

    /**
     * Prevents creature spawning from eggs (prevent staff exploits)
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle staff right-click to shoot magic projectiles
     */
    @EventHandler
    public void onStaffShot(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Check if this is a right-click action with a potential staff item
        if (!isValidStaffAction(event)) {
            return;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            return;
        }

        // Safety check for safe zones
        if (isSafeZone(player.getLocation())) {
            player.playSound(player.getLocation(), getSound("BLOCK_LAVA_EXTINGUISH", "LAVA"), 1.0f, 1.25f);
            spawnMagicParticles(player.getLocation().add(0, 1, 0));
            return;
        }

        // Check cooldown
        if (isOnCooldown(player)) {
            return;
        }

        // Check energy
        int playerEnergy = Energy.getInstance().getEnergy(yakPlayer);
        if (playerEnergy <= 0) {
            handleOutOfEnergy(player, yakPlayer);
            return;
        }

        // Find staff type based on material
        ItemStack staffItem = player.getInventory().getItemInMainHand();
        if (staffItem == null || staffItem.getType() == Material.AIR) {
            return;
        }

        StaffType staffType = StaffType.getByMaterial(staffItem.getType());
        if (staffType != null) {
            // Check if player has enough energy for this staff
            if (playerEnergy < staffType.getEnergyCost()) {
                handleInsufficientEnergy(player, yakPlayer, playerEnergy);
                return;
            }

            // Shoot the magic projectile
            shootMagicProjectile(player, staffType);

            // Store staff for damage calculation
            lastUsedStaff.put(player.getUniqueId(), staffItem.clone());
            lastStaffShotTime.put(player.getUniqueId(), System.currentTimeMillis());

            // Consume energy
            Energy.getInstance().removeEnergy(player, staffType.getEnergyCost());

            // Reset durability and set cooldown
            staffItem.setDurability((short) 0);
            setStaffCooldown(player);
        }
    }

    /**
     * Checks if the interaction event is a valid staff action
     */
    private boolean isValidStaffAction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (!item.getType().name().contains("_HOE")) {
            return false;
        }

        return item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasLore();
    }

    /**
     * Checks if a player is on staff cooldown
     */
    private boolean isOnCooldown(Player player) {
        if (player == null) {
            return true;
        }
        return player.hasMetadata(STAFF_COOLDOWN_META) &&
                player.getMetadata(STAFF_COOLDOWN_META).get(0).asLong() > System.currentTimeMillis();
    }

    /**
     * Sets the staff cooldown for a player
     */
    private void setStaffCooldown(Player player) {
        if (player == null) {
            return;
        }
        player.setMetadata(STAFF_COOLDOWN_META,
                new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis() + STAFF_COOLDOWN_DURATION));
    }

    /**
     * Handles when a player is out of energy
     */
    private void handleOutOfEnergy(Player player, YakPlayer yakPlayer) {
        if (player == null || yakPlayer == null) {
            return;
        }
        Energy.getInstance().setEnergy(yakPlayer, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5, false, true));
        player.playSound(player.getLocation(), getSound("ENTITY_WOLF_PANT", "WOLF_PANT"), 0.5f, 1.5f);
    }

    /**
     * Handles when a player has insufficient energy for a staff
     */
    private void handleInsufficientEnergy(Player player, YakPlayer yakPlayer, int currentEnergy) {
        if (player == null) {
            return;
        }
        player.playSound(player.getLocation(), getSound("ENTITY_WOLF_PANT", "WOLF_PANT"), 0.5f, 1.5f);
        player.sendMessage(ChatColor.RED + "You need more energy to use this staff!");
    }

    /**
     * Get the last staff used by a player
     */
    public static ItemStack getLastUsedStaff(Player player) {
        if (player == null) {
            return null;
        }
        return lastUsedStaff.get(player.getUniqueId());
    }

    /**
     * Check if a player has recently used a staff
     */
    public static boolean isRecentStaffShot(Player player) {
        if (player == null) {
            return false;
        }
        Long shotTime = lastStaffShotTime.get(player.getUniqueId());
        return shotTime != null && System.currentTimeMillis() - shotTime < 5000;
    }

    /**
     * Clear staff shot data for a player
     */
    public static void clearStaffShot(Player player) {
        if (player == null) {
            return;
        }
        lastUsedStaff.remove(player.getUniqueId());
        lastStaffShotTime.remove(player.getUniqueId());
    }

    /**
     * Creates magic particles at a location
     */
    private static void spawnMagicParticles(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        spawnParticle(location.getWorld(), getParticle("CRIT_MAGIC"), location, 20, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Check if damage should be ignored (PvP toggle, buddy, guild member)
     */
    private static boolean shouldIgnoreDamage(LivingEntity shooter, LivingEntity target) {
        if (!(shooter instanceof Player) || !(target instanceof Player)) {
            return false;
        }

        Player playerShooter = (Player) shooter;
        Player playerTarget = (Player) target;

        if (isDueling(playerTarget)) {
            return true;
        }

        if (isInSameGuild(playerShooter, playerTarget)) {
            return true;
        }

        if (isInSameParty(playerShooter, playerTarget)) {
            return true;
        }

        if (shouldIgnorePvP(playerShooter, playerTarget)) {
            return true;
        }

        return false;
    }

    /**
     * Check if a player is dueling
     */
    private static boolean isDueling(Player player) {
        return false;
    }

    /**
     * Check if two players are in the same guild
     */
    private static boolean isInSameGuild(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            return false;
        }

        YakPlayer yakPlayer1 = YakPlayerManager.getInstance().getPlayer(player1);
        YakPlayer yakPlayer2 = YakPlayerManager.getInstance().getPlayer(player2);

        if (yakPlayer1 != null && yakPlayer2 != null) {
            return yakPlayer1.isInGuild() &&
                    yakPlayer2.isInGuild() &&
                    yakPlayer1.getGuildName().equals(yakPlayer2.getGuildName());
        }
        return false;
    }

    /**
     * Check if two players are in the same party
     */
    private static boolean isInSameParty(Player player1, Player player2) {
        return false;
    }

    /**
     * Check if PvP should be ignored based on preferences and settings
     */
    private static boolean shouldIgnorePvP(Player shooter, Player target) {
        if (shooter == null || target == null) {
            return true;
        }

        try {
            if (Buddies.getInstance().isBuddy(shooter, target.getName()) &&
                    !Toggles.getInstance().isToggled(shooter, "Friendly Fire")) {
                return true;
            }

            if (Toggles.getInstance().isToggled(shooter, "Anti PVP")) {
                return true;
            }

            if (isLawfulPlayer(target) && Toggles.getInstance().isToggled(shooter, "Chaotic")) {
                return true;
            }
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Error checking PvP settings: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check if a player has lawful alignment
     */
    private static boolean isLawfulPlayer(Player player) {
        if (player == null) {
            return false;
        }
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        return yakPlayer != null && "LAWFUL".equals(yakPlayer.getAlignment());
    }

    /**
     * Check if a location is in a safe zone
     */
    private static boolean isSafeZone(Location location) {
        if (location == null) {
            return true;
        }
        return AlignmentMechanics.isSafeZone(location);
    }

    /**
     * Handles damage to a horse with a passenger
     */
    private static void handleHorsePassengerDamage(LivingEntity shooter, LivingEntity horse) {
        if (shooter == null || horse == null) {
            return;
        }

        if (shooter instanceof Player && horse.getPassenger() instanceof Player) {
            Player player = (Player) shooter;
            Player passenger = (Player) horse.getPassenger();

            try {
                if (Buddies.getInstance().isBuddy(player, passenger.getName()) &&
                        !Toggles.getInstance().isToggled(player, "Friendly Fire")) {
                    return;
                }

                if (Toggles.getInstance().isToggled(player, "Anti PVP")) {
                    return;
                }

                if (isLawfulPlayer(passenger) && Toggles.getInstance().isToggled(player, "Chaotic")) {
                    return;
                }
            } catch (Exception e) {
                YakRealms.getInstance().getLogger().warning("Error checking horse damage protection: " + e.getMessage());
            }
        }

        horse.damage(1);
        horse.remove();
    }

    /**
     * Handle staff damage from a player
     */
    private static void handlePlayerStaffDamage(LivingEntity shooter, LivingEntity target) {
        if (!(shooter instanceof Player) || target == null) {
            return;
        }

        Player player = (Player) shooter;
        try {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != Material.AIR) {
                lastUsedStaff.put(player.getUniqueId(), mainHand.clone());
            }

            target.damage(1, shooter);

            HitRegisterEvent hitEvent = new HitRegisterEvent(player, target, 1);
            Bukkit.getPluginManager().callEvent(hitEvent);
        } catch (Exception e) {
            YakRealms.error("Error handling player staff damage", e);
        } finally {
            lastUsedStaff.remove(player.getUniqueId());
        }
    }

    /**
     * Handle default damage for non-player shooters
     */
    private static void handleDefaultDamage(LivingEntity shooter, LivingEntity target) {
        if (shooter == null || target == null) {
            return;
        }
        target.damage(1, shooter);
    }

    /**
     * Registers this listener and initializes the system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log(" Magic Staff system enabled with improved visuals and 1.21.7 compatibility");
    }

    /**
     * StaffType enum with improved visual distinction and tier system
     */
    public enum StaffType {
        WOOD(Material.WOODEN_HOE, 7, 0.9f, 0.7f, 0.4f, 1),        // Brown/Wood color
        STONE(Material.STONE_HOE, 8, 0.5f, 0.5f, 0.5f, 2),        // Gray/Stone color  
        IRON(Material.IRON_HOE, 9, 0.8f, 0.8f, 0.9f, 3),          // Silver/Iron color
        DIAMOND(Material.DIAMOND_HOE, 10, 0.4f, 0.8f, 1.0f, 4),   // Cyan/Diamond color
        GOLD(Material.GOLDEN_HOE, 11, 1.0f, 0.8f, 0.0f, 5),       // Gold color
        NETHERITE(Material.NETHERITE_HOE, 15, 0.4f, 0.2f, 0.2f, 6); // Dark red/Netherite color

        private final Material material;
        private final int energyCost;
        private final float red;
        private final float green;
        private final float blue;
        private final int tier;

        StaffType(Material material, int energyCost, float red, float green, float blue, int tier) {
            this.material = material;
            this.energyCost = energyCost;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.tier = tier;
        }

        public Material getMaterial() { return material; }
        public int getEnergyCost() { return energyCost; }
        public float getRed() { return red; }
        public float getGreen() { return green; }
        public float getBlue() { return blue; }
        public int getTier() { return tier; }

        public Color getColor() {
            return Color.fromRGB(
                    (int) (red * 255),
                    (int) (green * 255),
                    (int) (blue * 255)
            );
        }

        public static StaffType getByMaterial(Material material) {
            if (material == null) return null;
            for (StaffType type : values()) {
                if (type.getMaterial() == material) {
                    return type;
                }
            }
            return null;
        }
    }
}
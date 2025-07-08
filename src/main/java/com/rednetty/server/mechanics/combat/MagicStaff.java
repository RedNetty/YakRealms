package com.rednetty.server.mechanics.combat;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.social.friends.Buddies;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stamina.Energy;
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
 * Handles magical staff weapons and their projectile mechanics
 * <p>
 * This class manages:
 * - Staff projectile casting and travel
 * - Hit detection against entities and blocks
 * - Damage calculation and application
 * - Visual and sound effects
 */
public class MagicStaff implements Listener {
    // Constants
    private static final String STAFF_COOLDOWN_META = "staffCooldown";
    private static final long STAFF_COOLDOWN_DURATION = 350L; // Cooldown between staff shots in milliseconds
    private static final int MAX_PROJECTILE_TICKS = 70; // Maximum lifetime of a projectile in ticks
    private static final double PROJECTILE_COLLISION_RADIUS = 0.8; // Radius for entity collision detection
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
     *
     * @return The MagicStaff instance
     */
    public static MagicStaff getInstance() {
        if (instance == null) {
            instance = new MagicStaff();
        }
        return instance;
    }

    /**
     * Registers this listener and initializes the system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("Magic Staff system has been enabled");
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
            player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.25f);
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
     *
     * @param event The interaction event to check
     * @return true if this is a valid staff action
     */
    private boolean isValidStaffAction(PlayerInteractEvent event) {
        // Check if this is a right-click action
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if holding a valid item
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Check if the item is a hoe (staff)
        if (!item.getType().name().contains("_HOE")) {
            return false;
        }

        // Check if the item has metadata and lore
        return item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasLore();
    }

    /**
     * Checks if a player is on staff cooldown
     *
     * @param player The player to check
     * @return true if player is on cooldown
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
     *
     * @param player The player to set cooldown for
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
     *
     * @param player    The player
     * @param yakPlayer The YakPlayer object
     */
    private void handleOutOfEnergy(Player player, YakPlayer yakPlayer) {
        if (player == null || yakPlayer == null) {
            return;
        }
        // Player is out of energy
        Energy.getInstance().setEnergy(yakPlayer, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 40, 5, false, true));
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_PANT, 0.5f, 1.5f);
    }

    /**
     * Handles when a player has insufficient energy for a staff
     *
     * @param player        The player
     * @param yakPlayer     The YakPlayer object
     * @param currentEnergy Current energy level
     */
    private void handleInsufficientEnergy(Player player, YakPlayer yakPlayer, int currentEnergy) {
        if (player == null) {
            return;
        }
        // Player doesn't have enough energy for this staff
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_PANT, 0.5f, 1.5f);
        player.sendMessage(ChatColor.RED + "You need more energy to use this staff!");
    }

    /**
     * Get the last staff used by a player
     *
     * @param player The player to check
     * @return The ItemStack of the last used staff, or null if none
     */
    public static ItemStack getLastUsedStaff(Player player) {
        if (player == null) {
            return null;
        }
        return lastUsedStaff.get(player.getUniqueId());
    }

    /**
     * Check if a player has recently used a staff
     *
     * @param player The player to check
     * @return true if player has used a staff in the last 5 seconds
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
     *
     * @param player The player to clear data for
     */
    public static void clearStaffShot(Player player) {
        if (player == null) {
            return;
        }
        lastUsedStaff.remove(player.getUniqueId());
        lastStaffShotTime.remove(player.getUniqueId());
    }

    /**
     * Creates and launches a magical projectile from a staff
     *
     * @param shooter The living entity shooting the projectile
     * @param type    The type of staff being used
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

        // Play shoot sound
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.5f);

        // Create ray trace for projectile path
        new BukkitRunnable() {
            final Location eyeLocation = shooter.getEyeLocation();
            final RayTrace magic;
            final int distanceSpeed;
            final double precision;
            final Iterator<Vector> trail;

            {
                if (eyeLocation != null) {
                    magic = new RayTrace(
                            eyeLocation.add(0, -0.25, 0).toVector(),
                            eyeLocation.getDirection()
                    );
                    distanceSpeed = shooter instanceof Player ? 150 : 60;
                    precision = shooter instanceof Player ? 0.8 : 0.3;
                    trail = magic.traverse(distanceSpeed, precision).iterator();
                } else {
                    magic = null;
                    distanceSpeed = 0;
                    precision = 0;
                    trail = null;
                }
            }

            int ticks = 0;
            boolean hasHit = false;

            @Override
            public void run() {
                try {
                    // Validate state
                    if (magic == null || trail == null || !trail.hasNext() ||
                            ticks > MAX_PROJECTILE_TICKS || hasHit) {
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
                    for (int i = 0; i < 4 && trail.hasNext() && !hasHit; i++) {
                        Vector pos = trail.next();
                        if (pos == null) {
                            continue;
                        }

                        Location currentLocation = pos.toLocation(shooter.getWorld());
                        if (currentLocation == null) {
                            continue;
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
                                if (!(entity instanceof LivingEntity) || entity == shooter || hasHit) {
                                    continue;
                                }

                                LivingEntity target = (LivingEntity) entity;

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

                        // Create projectile trail effect
                        createTrailEffect(currentLocation, type);
                    }
                    ticks++;
                } catch (Exception e) {
                    // Log and cancel on error
                    YakRealms.error("Error in magic projectile task", e);
                    this.cancel();
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }

    /**
     * Creates visual trail effects for the projectile
     *
     * @param location The location to display the effect
     * @param type     The staff type for effect color
     */
    private static void createTrailEffect(Location location, StaffType type) {
        if (location == null || location.getWorld() == null || type == null) {
            return;
        }

        try {
            // Use a dust options object for the REDSTONE particle
            Color dustColor = Color.fromRGB(
                    (int) (type.getRed() * 255),
                    (int) (type.getGreen() * 255),
                    (int) (type.getBlue() * 255)
            );
            DustOptions dustOptions = new DustOptions(dustColor, 1.0f);

            // Spawn the colored particles
            location.getWorld().spawnParticle(Particle.SPELL_MOB, location, 0, type.getRed(), type.getGreen(), type.getBlue(), 1);
            location.getWorld().spawnParticle(Particle.REDSTONE, location, 1, 0, 0, 0, 0, dustOptions);
        } catch (Exception e) {
            // Log error but don't crash the projectile
            YakRealms.getInstance().getLogger().info("Error creating trail effect: " + e.getMessage());
        }
    }

    /**
     * Creates impact effect when projectile hits a target
     *
     * @param location The impact location
     * @param type     The staff type for effect color
     */
    private static void createImpactEffect(Location location, StaffType type) {
        if (location == null || location.getWorld() == null || type == null) {
            return;
        }

        try {
            // Create dust options for colored particles
            Color dustColor = Color.fromRGB(
                    (int) (type.getRed() * 255),
                    (int) (type.getGreen() * 255),
                    (int) (type.getBlue() * 255)
            );
            DustOptions dustOptions = new DustOptions(dustColor, 1.0f);

            // Spawn particles
            location.getWorld().spawnParticle(Particle.CRIT_MAGIC, location, 5, 0.2, 0.2, 0.2, 0.1);
            location.getWorld().spawnParticle(Particle.REDSTONE, location, 10, 0.3, 0.3, 0.3, 0, dustOptions);

            // Play sound
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, IMPACT_SOUND_VOLUME, IMPACT_SOUND_PITCH);
        } catch (Exception e) {
            // Log error but continue
            YakRealms.getInstance().getLogger().info("Error creating impact effect: " + e.getMessage());
        }
    }

    /**
     * Creates magic particles at a location
     *
     * @param location The location to display particles
     */
    private static void spawnMagicParticles(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.CRIT_MAGIC, location, 20, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Check if damage should be ignored (PvP toggle, buddy, guild member)
     *
     * @param shooter The entity shooting the projectile
     * @param target  The target entity
     * @return true if damage should be ignored
     */
    private static boolean shouldIgnoreDamage(LivingEntity shooter, LivingEntity target) {
        // Only perform checks for player shooter and target
        if (!(shooter instanceof Player) || !(target instanceof Player)) {
            return false;
        }

        Player playerShooter = (Player) shooter;
        Player playerTarget = (Player) target;

        // Check for duel
        if (isDueling(playerTarget)) {
            return true;
        }

        // Check for same guild
        if (isInSameGuild(playerShooter, playerTarget)) {
            return true;
        }

        // Check for party members
        if (isInSameParty(playerShooter, playerTarget)) {
            return true;
        }

        // Check PvP and friendly fire settings
        if (shouldIgnorePvP(playerShooter, playerTarget)) {
            return true;
        }

        return false;
    }

    /**
     * Check if a player is dueling
     *
     * @param player The player to check
     * @return true if the player is in a duel
     */
    private static boolean isDueling(Player player) {
        // This would integrate with your dueling system
        return false;
    }

    /**
     * Check if two players are in the same guild
     *
     * @param player1 First player
     * @param player2 Second player
     * @return true if they're in the same guild
     */
    private static boolean isInSameGuild(Player player1, Player player2) {
        if (player1 == null || player2 == null) {
            return false;
        }

        // This would integrate with your guild system
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
     *
     * @param player1 First player
     * @param player2 Second player
     * @return true if they're in the same party
     */
    private static boolean isInSameParty(Player player1, Player player2) {
        // This would integrate with your party system
        return false;
    }

    /**
     * Check if PvP should be ignored based on preferences and settings
     *
     * @param shooter The attacking player
     * @param target  The target player
     * @return true if PvP should be ignored
     */
    private static boolean shouldIgnorePvP(Player shooter, Player target) {
        if (shooter == null || target == null) {
            return true;
        }

        try {
            // Check if target is a buddy and friendly fire is disabled
            if (Buddies.getInstance().isBuddy(shooter, target.getName()) &&
                    !Toggles.getInstance().isToggled(shooter, "Friendly Fire")) {
                return true;
            }

            // Check if player has Anti PvP enabled
            if (Toggles.getInstance().isToggled(shooter, "Anti PVP")) {
                return true;
            }

            // Check if chaotic protection is enabled
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
     *
     * @param player The player to check
     * @return true if player is lawful
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
     *
     * @param location The location to check
     * @return true if location is in a safe zone
     */
    private static boolean isSafeZone(Location location) {
        if (location == null) {
            return true;
        }
        // Integrate with Alignments class
        return AlignmentMechanics.isSafeZone(location);
    }

    /**
     * Handles damage to a horse with a passenger
     *
     * @param shooter The entity that shot the projectile
     * @param horse   The horse entity
     */
    private static void handleHorsePassengerDamage(LivingEntity shooter, LivingEntity horse) {
        if (shooter == null || horse == null) {
            return;
        }

        // Don't damage if protection rules apply
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

        // Damage and remove the horse
        horse.damage(1);
        horse.remove();
    }

    /**
     * Handle staff damage from a player
     *
     * @param shooter The player shooter
     * @param target  The target entity
     */
    private static void handlePlayerStaffDamage(LivingEntity shooter, LivingEntity target) {
        if (!(shooter instanceof Player) || target == null) {
            return;
        }

        Player player = (Player) shooter;
        try {
            // Store the staff for damage calculation
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != Material.AIR) {
                lastUsedStaff.put(player.getUniqueId(), mainHand.clone());
            }

            // Apply damage
            target.damage(1, shooter);

            // Register hit for combat system
            HitRegisterEvent hitEvent = new HitRegisterEvent(player, target, 1);
            Bukkit.getPluginManager().callEvent(hitEvent);
        } catch (Exception e) {
            YakRealms.error("Error handling player staff damage", e);
        } finally {
            // Clear the stored staff
            lastUsedStaff.remove(player.getUniqueId());
        }
    }

    /**
     * Handle default damage for non-player shooters
     *
     * @param shooter The entity shooter
     * @param target  The target entity
     */
    private static void handleDefaultDamage(LivingEntity shooter, LivingEntity target) {
        if (shooter == null || target == null) {
            return;
        }
        target.damage(1, shooter);
    }

    /**
     * Represents different types of magical staves
     */
    public enum StaffType {
        WOOD(Material.WOODEN_HOE, 7, 1.0f, 1.0f, 1.0f),    // White
        STONE(Material.STONE_HOE, 8, 0.0f, 1.0f, 0.0f),    // Green
        IRON(Material.IRON_HOE, 9, 0.0f, 1.0f, 1.0f),      // Aqua
        DIAMOND(Material.DIAMOND_HOE, 10, 0.0f, 0.0f, 0.5f), // Navy
        GOLD(Material.GOLDEN_HOE, 11, 1.0f, 1.0f, 0.0f),   // Yellow
        NETHERITE(Material.NETHERITE_HOE, 15, .855f, .647f, 0.125f);   // Yellow

        private final Material material;
        private final int energyCost;
        private final float red;
        private final float green;
        private final float blue;

        StaffType(Material material, int energyCost, float red, float green, float blue) {
            this.material = material;
            this.energyCost = energyCost;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        /**
         * Get the material type of this staff
         *
         * @return The material type
         */
        public Material getMaterial() {
            return material;
        }

        /**
         * Get the energy cost to use this staff
         *
         * @return The energy cost
         */
        public int getEnergyCost() {
            return energyCost;
        }

        /**
         * Get the red color component for particles
         *
         * @return Red value (0-1)
         */
        public float getRed() {
            return red;
        }

        /**
         * Get the green color component for particles
         *
         * @return Green value (0-1)
         */
        public float getGreen() {
            return green;
        }

        /**
         * Get the blue color component for particles
         *
         * @return Blue value (0-1)
         */
        public float getBlue() {
            return blue;
        }

        /**
         * Get the Color object representing this staff's particle color
         *
         * @return Color object
         */
        public Color getColor() {
            return Color.fromRGB(
                    (int) (red * 255),
                    (int) (green * 255),
                    (int) (blue * 255)
            );
        }

        /**
         * Find a staff type by material
         *
         * @param material The material to search for
         * @return The matching staff type or null if not found
         */
        public static StaffType getByMaterial(Material material) {
            if (material == null) {
                return null;
            }
            for (StaffType type : values()) {
                if (type.getMaterial() == material) {
                    return type;
                }
            }
            return null;
        }
    }
}
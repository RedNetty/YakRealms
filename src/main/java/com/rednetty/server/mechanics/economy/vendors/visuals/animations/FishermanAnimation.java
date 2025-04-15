package com.rednetty.server.mechanics.economy.vendors.visuals.animations;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.visuals.AnimationOptions;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin; // Assuming 'plugin' is injected or accessible
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fisherman animation with orbiting fish and fishing equipment, featuring smoother
 * transitions and more dynamic movements.
 */
public class FishermanAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;
    private final JavaPlugin plugin; // Added for clarity, assuming it's available

    // Constants for animation parameters
    private static final int ROD_CYCLE_TICKS = 120; // 6 seconds
    private static final float ROD_ORBIT_RADIUS = 0.7f;
    private static final float FISH_BASE_ORBIT_RADIUS = 0.6f;
    private static final float FISH_RADIUS_VARIATION = 0.2f;
    private static final float FISH_BASE_HEIGHT = 1.0f;
    private static final float FISH_HEIGHT_VARIATION = 0.3f;
    private static final int INTERPOLATION_DURATION_TICKS = 5; // Smoothness factor

    public FishermanAnimation(AnimationOptions options) { // Pass plugin instance
        super(options);
        this.plugin = YakRealms.getInstance(); // Store plugin instance
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Cannot create vendor display entities: World is null for location " + loc);
            return Collections.emptySet();
        }

        Set<Entity> displays = ConcurrentHashMap.newKeySet(); // Use ConcurrentHashMap for potential thread safety

        // --- Create Orbiting Fishing Rod ---
        ItemDisplay rodDisplay = createItemDisplay(
                world,
                loc.clone(), // Clone location to avoid modification issues
                new ItemStack(Material.FISHING_ROD),
                "orbit_rod",
                0.5f // Scale
        );
        if (rodDisplay != null) {
            displays.add(rodDisplay);
        }

        // --- Create Different Orbiting Fish/Items ---
        Material[] fishTypes = {
                Material.COD, Material.SALMON, Material.TROPICAL_FISH,
                Material.PUFFERFISH, Material.LILY_PAD // Lily pad acts like an item
        };

        for (int i = 0; i < fishTypes.length; i++) {
            ItemDisplay fishDisplay = createItemDisplay(
                    world,
                    loc.clone(), // Clone location
                    new ItemStack(fishTypes[i]),
                    "orbit_fish_" + i, // Unique role identifier
                    0.4f // Scale
            );
            if (fishDisplay != null) {
                displays.add(fishDisplay);
            }
        }

        plugin.getLogger().info("Created " + displays.size() + " display entities for vendor " + vendorId);
        return displays;
    }

    @Override
    public void updateDisplayAnimations(Set<Entity> entities, Location centerLoc) {
        // Use the animationTick from the base class
        int tick = animationTick;

        // Iterate safely over the set
        Iterator<Entity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();

            // Basic validation and cleanup
            if (entity == null || !entity.isValid() || !(entity instanceof Display)) {
                iterator.remove(); // Remove invalid or non-display entities
                continue;
            }

            // Check persistent data for vendor role
            String meta = entity.getPersistentDataContainer().get(
                    vendorEntityKey,
                    PersistentDataType.STRING
            );

            // Ensure the entity belongs to this vendor instance
            if (meta == null || !meta.startsWith(vendorId + ":")) {
                continue; // Skip entities not managed by this animation instance
            }

            String role = meta.substring(vendorId.length() + 1); // Extract role (e.g., "orbit_rod")
            Display display = (Display) entity;

            // Calculate and apply the updated transformation
            updateDisplayTransform(display, role, centerLoc, tick);
        }
    }

    /**
     * Calculates and applies the new transformation (location, rotation) for a single display entity.
     *
     * @param display   The Display entity to update.
     * @param role      The role identifier (e.g., "orbit_rod", "orbit_fish_0").
     * @param centerLoc The central location of the vendor.
     * @param tick      The current animation tick.
     */
    private void updateDisplayTransform(Display display, String role, Location centerLoc, int tick) {
        Transformation currentTransform = display.getTransformation();
        Vector3f scale = currentTransform.getScale(); // Keep original scale
        Quaternionf newRotation = new Quaternionf(); // Initialize new rotation
        Location newLocation = centerLoc.clone(); // Start from the center for calculations

        // --- Fishing Rod Animation ---
        if (role.equals("orbit_rod")) {
            double rodCycle = (tick % ROD_CYCLE_TICKS) / (double) ROD_CYCLE_TICKS; // Cycle progress (0.0 to 1.0)
            double orbitAngle = Math.toRadians(tick * 1.5); // Base orbit speed
            double currentOrbitRadius = ROD_ORBIT_RADIUS;

            // --- Height Calculation (Casting Motion) ---
            double height = 1.4; // Base height
            double rodPitchAngleDeg = 0; // Angle up/down relative to horizon

            if (rodCycle < 0.3) { // Pull back phase (0.0 - 0.3)
                double phaseProgress = rodCycle / 0.3;
                height = 1.4 + phaseProgress * 0.15; // Moves up slightly
                rodPitchAngleDeg = phaseProgress * 35; // Tilts up
            } else if (rodCycle < 0.5) { // Cast forward phase (0.3 - 0.5)
                double phaseProgress = (rodCycle - 0.3) / 0.2;
                height = 1.55 - phaseProgress * 0.45; // Swoops down
                rodPitchAngleDeg = 35 - phaseProgress * 75; // Tilts down sharply (-40 deg)
            } else { // Rod out / return phase (0.5 - 1.0)
                double phaseProgress = (rodCycle - 0.5) / 0.5;
                height = 1.1 + phaseProgress * 0.3; // Slowly returns to base height
                // Add a gentle bobbing motion while the rod is out
                height += Math.sin(tick * 0.2) * 0.05; // Slow sine wave for bobbing
                rodPitchAngleDeg = -40 + phaseProgress * 40; // Tilts back towards horizontal (0 deg)
            }

            // --- Position Calculation (Orbit) ---
            double xOffset = Math.cos(orbitAngle) * currentOrbitRadius;
            double zOffset = Math.sin(orbitAngle) * currentOrbitRadius;
            newLocation.add(xOffset, height, zOffset);

            // --- Rotation Calculation ---
            // 1. Pitch rotation (casting motion - around local X or Z axis depending on orientation)
            //    Let's assume rotation around the Z-axis relative to its orientation
            Quaternionf pitchRotation = new Quaternionf(new AxisAngle4f(
                    (float) Math.toRadians(rodPitchAngleDeg),
                    0, 0, 1 // Rotate around local Z-axis for pitch
            ));

            // 2. Yaw rotation (to face outwards from the orbit center)
            //    Angle perpendicular to the radius vector -> orbitAngle + 90 degrees
            Quaternionf yawRotation = new Quaternionf(new AxisAngle4f(
                    (float) (orbitAngle + Math.PI / 2.0), // Add 90 degrees to face outwards
                    0, 1, 0 // Rotate around world Y-axis for yaw
            ));

            // Combine rotations: Apply yaw first, then pitch relative to the yawed orientation
            newRotation = yawRotation.mul(pitchRotation);

        }
        // --- Fish / Item Animation ---
        else if (role.startsWith("orbit_fish_")) {
            try {
                int fishIndex = Integer.parseInt(role.substring(role.lastIndexOf('_') + 1));
                boolean isLilyPad = fishIndex == 4; // Identify the lily pad

                // --- Base Orbit Calculation ---
                double baseSpeed = 3.0 + (fishIndex * 0.5); // Different base speeds
                double orbitAngle = Math.toRadians(tick * baseSpeed + (fishIndex * 72)); // Base angle + offset

                // --- Dynamic Radius and Height (Schooling/Floating Effect) ---
                // Primary oscillation (like original)
                double primaryRadiusOsc = Math.sin(Math.toRadians(tick * 2.0 + fishIndex * 30)) * FISH_RADIUS_VARIATION;
                double primaryHeightOsc = Math.sin(Math.toRadians(tick * 3.0 + fishIndex * 45)) * FISH_HEIGHT_VARIATION;
                // Secondary, slower oscillation for more variation
                double secondaryRadiusOsc = Math.sin(Math.toRadians(tick * 0.8 + fishIndex * 20)) * (FISH_RADIUS_VARIATION * 0.5);
                double secondaryHeightOsc = Math.cos(Math.toRadians(tick * 1.1 + fishIndex * 35)) * (FISH_HEIGHT_VARIATION * 0.6);

                double currentRadius = FISH_BASE_ORBIT_RADIUS + primaryRadiusOsc + secondaryRadiusOsc;
                double currentHeight = FISH_BASE_HEIGHT + primaryHeightOsc + secondaryHeightOsc;

                // --- Adjustments for Specific Items ---
                if (fishIndex == 3) { // Pufferfish stays more isolated
                    currentRadius += 0.2;
                    currentHeight += 0.1;
                } else if (isLilyPad) { // Lily pad stays lower and flatter
                    currentHeight = 0.6 + Math.sin(Math.toRadians(tick * 1.5)) * 0.05; // Flatter bobbing
                    currentRadius *= 1.1; // Slightly wider orbit
                }

                // Clamp height to prevent going too low/high if needed
                // currentHeight = Math.max(0.5, Math.min(1.8, currentHeight));

                // --- Position Calculation ---
                double xOffset = Math.cos(orbitAngle) * currentRadius;
                double zOffset = Math.sin(orbitAngle) * currentRadius;
                newLocation.add(xOffset, currentHeight, zOffset);

                // --- Rotation Calculation ---
                // 1. Base Yaw: Face direction of travel
                //    Calculate position slightly ahead in time to find direction vector
                double nextOrbitAngle = Math.toRadians((tick + 1) * baseSpeed + (fishIndex * 72));
                double nextX = Math.cos(nextOrbitAngle) * currentRadius; // Approximate next position
                double nextZ = Math.sin(nextOrbitAngle) * currentRadius;
                double angleToNext = Math.atan2(nextZ - zOffset, nextX - xOffset); // Yaw angle

                Quaternionf yawRotation = new Quaternionf(new AxisAngle4f(
                        (float) (-angleToNext + Math.PI / 2.0), // Adjust to face forward (atan2 reference)
                        0, 1, 0 // Rotate around world Y-axis
                ));
                newRotation = yawRotation;

                // 2. Add Natural Wobble/Rotation (Not for Lily Pad)
                if (!isLilyPad) {
                    // Gentle Pitch/Roll Wobble (Sine waves)
                    float pitchWobble = (float) Math.toRadians(Math.sin(Math.toRadians(tick * 4.5 + fishIndex * 25)) * 10.0); // Up/down tilt
                    float rollWobble = (float) Math.toRadians(Math.sin(Math.toRadians(tick * 3.5 + fishIndex * 35)) * 12.0); // Side-to-side roll

                    Quaternionf pitch = new Quaternionf(new AxisAngle4f(pitchWobble, 1, 0, 0)); // Rotate around local X
                    Quaternionf roll = new Quaternionf(new AxisAngle4f(rollWobble, 0, 0, 1));   // Rotate around local Z

                    newRotation.mul(pitch).mul(roll); // Apply wobble relative to yaw

                    // Occasional "Flip" (more pronounced roll)
                    if ((tick + fishIndex * 10) % 100 < 8) { // Less frequent, shorter duration
                        float flipProgress = ((tick + fishIndex * 10) % 100) / 8.0f; // 0.0 to 1.0
                        float flipAngle = (float) Math.toRadians(Math.sin(flipProgress * Math.PI) * 90); // Smooth 90 degree flip and back
                        Quaternionf flip = new Quaternionf(new AxisAngle4f(flipAngle, 0, 0, 1)); // Flip around local Z
                        newRotation.mul(flip);
                    }
                } else {
                    // Gentle Lily Pad Rotation
                    float lilyYaw = (float) Math.toRadians(tick * 0.5); // Slow rotation around Y-axis
                    Quaternionf lilyRotation = new Quaternionf(new AxisAngle4f(lilyYaw, 0, 1, 0));
                    newRotation.mul(lilyRotation); // Combine with facing direction
                }

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Failed to parse fish index from role: " + role);
                // Optionally remove the entity or skip update
            }
        }

        // --- Apply Updates ---
        // Teleport smoothly if position changed significantly (Display handles interpolation)
        // Small distance check to avoid unnecessary teleports if using interpolation heavily
        if (display.getLocation().distanceSquared(newLocation) > 0.001) {
            display.teleport(newLocation); // Teleport handles the move
        }

        // Set the calculated transformation
        Transformation newTransform = new Transformation(
                currentTransform.getTranslation(), // Translation is handled by teleport, keep relative offset 0
                newRotation,
                scale,
                currentTransform.getRightRotation() // Assuming right rotation isn't used
        );
        display.setTransformation(newTransform);
    }


    @Override
    public void applyParticleEffects(Location centerLoc) {
        World world = centerLoc.getWorld();
        if (world == null) return;

        int tick = animationTick; // Use base class tick

        // --- Water Drip Particles (Near Fish) ---
        if (tick % 6 == 0) { // Slightly less frequent
            // Calculate a position near one of the non-lilypad fish
            int fishIndex = (tick / 6) % 4; // Cycle through fish 0-3
            if (fishIndex < 4) { // Ensure it's not the lily pad index
                // Recalculate approximate fish position (simplified version for particles)
                double baseSpeed = 3.0 + (fishIndex * 0.5);
                double orbitAngle = Math.toRadians(tick * baseSpeed + (fishIndex * 72));
                double currentRadius = FISH_BASE_ORBIT_RADIUS + Math.sin(Math.toRadians(tick * 2.0 + fishIndex * 30)) * FISH_RADIUS_VARIATION;
                double currentHeight = FISH_BASE_HEIGHT + Math.sin(Math.toRadians(tick * 3.0 + fishIndex * 45)) * FISH_HEIGHT_VARIATION;

                double xOffset = Math.cos(orbitAngle) * currentRadius;
                double zOffset = Math.sin(orbitAngle) * currentRadius;

                Location fishLoc = centerLoc.clone().add(xOffset, currentHeight, zOffset);

                world.spawnParticle(
                        Particle.DRIP_WATER, // Changed from DRIP_WATER for potentially better visual
                        fishLoc,
                        1, // Count
                        0.05, 0.05, 0.05, // Offset variance
                        0 // Extra data (speed for some particles)
                );

                // Add occasional bubble particle near fish
                if (random.nextInt(15) == 0) { // Lower chance
                    world.spawnParticle(
                            Particle.WATER_BUBBLE, // Bubbles rising
                            fishLoc.clone().subtract(0, 0.1, 0), // Start slightly below fish
                            1, 0.1, 0.05, 0.1, 0.02 // Small spread, slow speed
                    );
                }
            }
        }

        // --- Water Splash (Occasional Ambient) ---
        if (tick % 25 == 0 && random.nextBoolean()) { // Less frequent
            Location splashLoc = centerLoc.clone().add(
                    (random.nextDouble() - 0.5) * 1.0, // Slightly wider area
                    0.7 + random.nextDouble() * 0.5, // Height range
                    (random.nextDouble() - 0.5) * 1.0
            );

            world.spawnParticle(
                    Particle.WATER_SPLASH, // Changed from WATER_SPLASH
                    splashLoc,
                    4, 0.15, 0.1, 0.15, 0.02 // Count, offset, speed
            );
        }

        // --- Fishing Cast Splash Effect ---
        double rodCycle = (tick % ROD_CYCLE_TICKS) / (double) ROD_CYCLE_TICKS;
        // Trigger just as the rod reaches its lowest point in the cast
        if (rodCycle > 0.49 && rodCycle < 0.51) {
            // Calculate approximate rod tip position during cast low point
            double orbitAngle = Math.toRadians(tick * 1.5);
            double xOffset = Math.cos(orbitAngle) * ROD_ORBIT_RADIUS;
            double zOffset = Math.sin(orbitAngle) * ROD_ORBIT_RADIUS;
            double castHeight = 1.1; // Height at lowest point

            Location castImpactLoc = centerLoc.clone().add(xOffset, castHeight - 0.2, zOffset); // Slightly below tip

            // Create splash effect
            world.spawnParticle(
                    Particle.WATER_SPLASH,
                    castImpactLoc,
                    10, 0.25, 0.1, 0.25, 0.15 // More particles, wider splash
            );

            // Add bubble effect
            world.spawnParticle(
                    Particle.BUBBLE_COLUMN_UP, // Rising bubbles effect
                    castImpactLoc,
                    1, 0.1, 0.05, 0.1, 0.1 // Count, offset, speed
            );
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return; // Check if sounds are globally enabled

        World world = loc.getWorld();
        if (world == null) return;

        int tick = animationTick; // Use base class tick

        // --- Fishing Cast Splash Sound ---
        double rodCycle = (tick % ROD_CYCLE_TICKS) / (double) ROD_CYCLE_TICKS;
        if (rodCycle > 0.49 && rodCycle < 0.51) { // Matches particle timing
            world.playSound(loc, Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.AMBIENT, 0.4f, 0.9f + random.nextFloat() * 0.3f);
        }

        // --- Occasional Fish Sounds ---
        if (tick % 70 == 0 && random.nextInt(3) > 0) { // ~4.6s interval, 2/3 chance
            Sound fishSound = random.nextBoolean() ? Sound.ENTITY_COD_FLOP : Sound.ENTITY_SALMON_FLOP;
            float volume = 0.15f + random.nextFloat() * 0.1f; // Quieter
            float pitch = 0.8f + random.nextFloat() * 0.4f;
            world.playSound(loc, fishSound, SoundCategory.AMBIENT, volume, pitch);
        }

        // --- Subtle Water Ambient Sound ---
        if (tick % 110 == 0) { // ~5.5s interval
            // Using a less intrusive ambient sound
            Sound ambientSound = Sound.BLOCK_WATER_AMBIENT; // Or AMBIENT_UNDERWATER_LOOP if preferred
            float volume = 0.1f;
            float pitch = 1.1f + random.nextFloat() * 0.2f;
            world.playSound(loc, ambientSound, SoundCategory.AMBIENT, volume, pitch);
        }
    }

    /**
     * Helper method to create and configure an ItemDisplay entity.
     *
     * @param world The world to spawn in.
     * @param loc   The initial location.
     * @param item  The ItemStack for the display.
     * @param role  The role identifier for persistent data.
     * @param scale The scale factor for the display.
     * @return The created ItemDisplay, or null on failure.
     */
    private ItemDisplay createItemDisplay(World world, Location loc, ItemStack item, String role, float scale) {
        try {
            ItemDisplay display = world.spawn(loc, ItemDisplay.class, spawnedDisplay -> {
                spawnedDisplay.setItemStack(item);

                // --- Set Initial Transformation ---
                Transformation transformation = spawnedDisplay.getTransformation();
                Vector3f scaleVector = new Vector3f(scale, scale, scale);
                // Set scale, leave rotation/translation as default initially
                spawnedDisplay.setTransformation(new Transformation(
                        transformation.getTranslation(),
                        transformation.getLeftRotation(), // Initial rotation (usually identity)
                        scaleVector,
                        transformation.getRightRotation()
                ));

                // --- Set Display Properties ---
                spawnedDisplay.setInterpolationDuration(INTERPOLATION_DURATION_TICKS); // ** Enable Smooth Interpolation **
                spawnedDisplay.setInterpolationDelay(-1); // Start interpolating immediately
                spawnedDisplay.setTeleportDuration(3); // Duration for smooth teleport movement (adjust as needed)

                spawnedDisplay.setShadowRadius(0.4f); // Slightly smaller shadow
                spawnedDisplay.setShadowStrength(0.6f);
                spawnedDisplay.setBrightness(new Display.Brightness(15, 15)); // Max brightness
                spawnedDisplay.setBillboard(Display.Billboard.FIXED); // Don't automatically face player
                spawnedDisplay.setViewRange(0.8f); // Adjust visibility range if needed
                spawnedDisplay.setDisplayWidth(0f); // No background billboard width
                spawnedDisplay.setDisplayHeight(0f); // No background billboard height

                // --- Store Metadata ---
                spawnedDisplay.getPersistentDataContainer().set(vendorEntityKey, PersistentDataType.STRING, vendorId + ":" + role);
                spawnedDisplay.setGravity(false); // Disable gravity
                spawnedDisplay.setPersistent(false); // Don't save with the world
            });
            return display;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn ItemDisplay for role " + role + " for vendor " + vendorId + ": " + e.getMessage());
            e.printStackTrace(); // Log the full error
            return null;
        }
    }

}

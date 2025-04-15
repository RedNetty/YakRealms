package com.rednetty.server.mechanics.economy.vendors.visuals.animations;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.visuals.AnimationOptions;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin; // Ensure plugin instance is available
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Book vendor animation with orbiting books, featuring smoother transitions,
 * more dynamic movements, and magical particle effects.
 */
public class BookVendorAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;
    private final JavaPlugin plugin; // Added for clarity, assuming it's available

    // Constants for animation parameters
    private static final float ENCHANTED_BOOK_RADIUS = 0.7f;
    private static final float NORMAL_BOOK_BASE_RADIUS = 0.6f;
    private static final float NORMAL_BOOK_RADIUS_VARIATION = 0.15f; // Slightly less variation than fish
    private static final float NORMAL_BOOK_BASE_HEIGHT = 1.1f;
    private static final float NORMAL_BOOK_HEIGHT_VARIATION = 0.25f;
    private static final int INTERPOLATION_DURATION_TICKS = 5; // Smoothness factor (matches fisherman)
    private static final float ENCHANTED_BOOK_SPEED_MOD = 0.025f; // Speed of figure-eight
    private static final float NORMAL_BOOK_SPEED_MOD = 2.5f; // Base orbit speed deg/tick

    public BookVendorAnimation(AnimationOptions options) { // Pass plugin instance
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

        // --- Create Orbiting Enchanted Book ---
        ItemDisplay enchantedBook = createItemDisplay(
                world,
                loc.clone(), // Clone location
                new ItemStack(Material.ENCHANTED_BOOK),
                "orbit_enchanted",
                0.5f // Scale
        );
        if (enchantedBook != null) {
            displays.add(enchantedBook);
        }

        // --- Create Orbiting Normal Books ---
        Material[] bookTypes = {
                Material.BOOK, Material.WRITABLE_BOOK, Material.WRITTEN_BOOK,
                Material.KNOWLEDGE_BOOK, Material.BOOK // Allow duplicates
        };

        for (int i = 0; i < bookTypes.length; i++) {
            ItemDisplay bookDisplay = createItemDisplay(
                    world,
                    loc.clone(), // Clone location
                    new ItemStack(bookTypes[i]),
                    "orbit_book_" + i, // Unique role identifier
                    0.4f // Scale
            );
            if (bookDisplay != null) {
                displays.add(bookDisplay);
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

            String role = meta.substring(vendorId.length() + 1); // Extract role
            Display display = (Display) entity;

            // Calculate and apply the updated transformation
            updateDisplayTransform(display, role, centerLoc, tick);
        }
    }

    /**
     * Calculates and applies the new transformation (location, rotation) for a single display entity.
     *
     * @param display   The Display entity to update.
     * @param role      The role identifier (e.g., "orbit_enchanted", "orbit_book_0").
     * @param centerLoc The central location of the vendor.
     * @param tick      The current animation tick.
     */
    private void updateDisplayTransform(Display display, String role, Location centerLoc, int tick) {
        Transformation currentTransform = display.getTransformation();
        Vector3f scale = currentTransform.getScale(); // Keep original scale
        Quaternionf newRotation = new Quaternionf(); // Initialize new rotation
        Location newLocation = centerLoc.clone(); // Start from the center for calculations

        // --- Enchanted Book Animation (Figure-Eight) ---
        if (role.equals("orbit_enchanted")) {
            double t = tick * ENCHANTED_BOOK_SPEED_MOD; // Time factor for pattern

            // --- Position Calculation (Figure-Eight / Lissajous curve) ---
            double height = 1.3 + Math.sin(t * 3.0) * 0.2; // Vertical oscillation
            // Lissajous curve parameters for figure-eight
            double xOffset = Math.sin(t * 2.0) * ENCHANTED_BOOK_RADIUS;
            double zOffset = Math.sin(t * 4.0) * ENCHANTED_BOOK_RADIUS * 0.6; // Make Z movement smaller
            newLocation.add(xOffset, height, zOffset);

            // --- Rotation Calculation ---
            // 1. Opening/Closing Animation (Local Y-axis)
            // Use a smoother sine wave for opening/closing effect (0 to 180 degrees)
            double openAngleDeg = (Math.sin(t * 2.5) + 1.0) * 0.5 * 160.0; // 0 to 160 degrees open
            Quaternionf openRotation = new Quaternionf(new AxisAngle4f(
                    (float) Math.toRadians(openAngleDeg),
                    0, 1, 0 // Rotate around local Y-axis to open/close
            ));

            // 2. Yaw Rotation (Face direction of travel)
            // Calculate next position to determine direction vector
            double nextT = (tick + 1) * ENCHANTED_BOOK_SPEED_MOD;
            double nextX = Math.sin(nextT * 2.0) * ENCHANTED_BOOK_RADIUS;
            double nextZ = Math.sin(nextT * 4.0) * ENCHANTED_BOOK_RADIUS * 0.6;
            double angleToNext = Math.atan2(nextZ - zOffset, nextX - xOffset); // Yaw angle in radians

            Quaternionf yawRotation = new Quaternionf(new AxisAngle4f(
                    (float) (-angleToNext + Math.PI / 2.0), // Adjust atan2 result to face forward
                    0, 1, 0 // Rotate around world Y-axis
            ));

            // 3. Subtle Tilt (Local X-axis) based on vertical movement
            double verticalVelocity = Math.cos(t * 3.0); // Derivative of sin(t*3) indicates vertical direction
            float tiltAngle = (float) Math.toRadians(verticalVelocity * -15.0); // Tilt slightly up when moving down, down when moving up
            Quaternionf tiltRotation = new Quaternionf(new AxisAngle4f(tiltAngle, 1, 0, 0)); // Rotate around local X-axis

            // Combine rotations: World Yaw -> Local Tilt -> Local Open/Close
            newRotation = yawRotation.mul(tiltRotation).mul(openRotation);

        }
        // --- Normal Book Animation (Dynamic Orbit) ---
        else if (role.startsWith("orbit_book_")) {
            try {
                int bookIndex = Integer.parseInt(role.substring(role.lastIndexOf('_') + 1));

                // --- Base Orbit Calculation ---
                double baseSpeed = NORMAL_BOOK_SPEED_MOD + (bookIndex * 0.3); // Slightly different speeds
                double orbitAngle = Math.toRadians(tick * baseSpeed + (bookIndex * (360.0 / 5.0))); // Spread evenly

                // --- Dynamic Radius and Height (More variation) ---
                // Primary oscillation
                double primaryRadiusOsc = Math.sin(Math.toRadians(tick * 1.8 + bookIndex * 40)) * NORMAL_BOOK_RADIUS_VARIATION;
                double primaryHeightOsc = Math.sin(Math.toRadians(tick * 2.2 + bookIndex * 55)) * NORMAL_BOOK_HEIGHT_VARIATION;
                // Secondary, slower oscillation
                double secondaryRadiusOsc = Math.cos(Math.toRadians(tick * 0.7 + bookIndex * 25)) * (NORMAL_BOOK_RADIUS_VARIATION * 0.6);
                double secondaryHeightOsc = Math.sin(Math.toRadians(tick * 0.9 + bookIndex * 35)) * (NORMAL_BOOK_HEIGHT_VARIATION * 0.7);

                // Alternate base radius slightly based on index
                double baseRadius = NORMAL_BOOK_BASE_RADIUS + (bookIndex % 2 == 0 ? 0.0 : 0.1);
                double currentRadius = baseRadius + primaryRadiusOsc + secondaryRadiusOsc;
                double currentHeight = NORMAL_BOOK_BASE_HEIGHT + primaryHeightOsc + secondaryHeightOsc;

                // --- Position Calculation ---
                double xOffset = Math.cos(orbitAngle) * currentRadius;
                double zOffset = Math.sin(orbitAngle) * currentRadius;
                newLocation.add(xOffset, currentHeight, zOffset);

                // --- Rotation Calculation ---
                // 1. Opening/Closing Animation (Local Y-axis)
                double openFactor = (Math.sin(Math.toRadians(tick * 3.5 + bookIndex * 60)) + 1.0) * 0.5; // 0 to 1
                double openAngleDeg = openFactor * 135.0; // Open up to 135 degrees
                Quaternionf openRotation = new Quaternionf(new AxisAngle4f(
                        (float) Math.toRadians(openAngleDeg),
                        0, 1, 0 // Rotate around local Y-axis
                ));

                // 2. Yaw Rotation (Face direction of travel)
                double nextOrbitAngle = Math.toRadians((tick + 1) * baseSpeed + (bookIndex * (360.0 / 5.0)));
                double nextX = Math.cos(nextOrbitAngle) * currentRadius; // Approximate next position
                double nextZ = Math.sin(nextOrbitAngle) * currentRadius;
                double angleToNext = Math.atan2(nextZ - zOffset, nextX - xOffset); // Yaw angle

                Quaternionf yawRotation = new Quaternionf(new AxisAngle4f(
                        (float) (-angleToNext + Math.PI / 2.0), // Adjust atan2 result
                        0, 1, 0 // Rotate around world Y-axis
                ));

                // 3. Subtle Tilt (Local X-axis) based on vertical oscillation
                double verticalVelocity = Math.cos(Math.toRadians(tick * 2.2 + bookIndex * 55)); // Approx derivative
                float tiltAngle = (float) Math.toRadians(verticalVelocity * -12.0); // Tilt based on up/down movement
                Quaternionf tiltRotation = new Quaternionf(new AxisAngle4f(tiltAngle, 1, 0, 0)); // Rotate around local X-axis

                // Combine rotations: World Yaw -> Local Tilt -> Local Open/Close
                newRotation = yawRotation.mul(tiltRotation).mul(openRotation);

            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Failed to parse book index from role: " + role);
            }
        }

        // --- Apply Updates ---
        // Teleport smoothly if position changed significantly
        if (display.getLocation().distanceSquared(newLocation) > 0.001) {
            display.teleport(newLocation); // Teleport handles the move
        }

        // Set the calculated transformation
        Transformation newTransform = new Transformation(
                currentTransform.getTranslation(), // Translation is handled by teleport
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

        // --- Enchantment Particles (Follow Enchanted Book) ---
        if (tick % 2 == 0) { // More frequent for enchanted book
            double t = tick * ENCHANTED_BOOK_SPEED_MOD;
            double height = 1.3 + Math.sin(t * 3.0) * 0.2;
            double xOffset = Math.sin(t * 2.0) * ENCHANTED_BOOK_RADIUS;
            double zOffset = Math.sin(t * 4.0) * ENCHANTED_BOOK_RADIUS * 0.6;
            Location bookLoc = centerLoc.clone().add(xOffset, height, zOffset);

            world.spawnParticle(
                    Particle.ENCHANTMENT_TABLE, // Changed from ENCHANTMENT_TABLE for a lighter effect
                    bookLoc.clone().add(
                            (random.nextDouble() - 0.5) * 0.4, // Slightly wider spread
                            (random.nextDouble() - 0.5) * 0.4,
                            (random.nextDouble() - 0.5) * 0.4
                    ),
                    1, 0.0, 0.0, 0.0, 0.01 // Count, offset, speed(extra)
            );
        }

        // --- Occasional Page/Dust Particle (Near Normal Books) ---
        if (tick % 12 == 0) { // Slightly more frequent
            // Find a random normal book's approximate position
            int bookIndex = random.nextInt(5); // 0 to 4
            double baseSpeed = NORMAL_BOOK_SPEED_MOD + (bookIndex * 0.3);
            double orbitAngle = Math.toRadians(tick * baseSpeed + (bookIndex * (360.0 / 5.0)));
            double baseRadius = NORMAL_BOOK_BASE_RADIUS + (bookIndex % 2 == 0 ? 0.0 : 0.1);
            double currentRadius = baseRadius + Math.sin(Math.toRadians(tick * 1.8 + bookIndex * 40)) * NORMAL_BOOK_RADIUS_VARIATION;
            double currentHeight = NORMAL_BOOK_BASE_HEIGHT + Math.sin(Math.toRadians(tick * 2.2 + bookIndex * 55)) * NORMAL_BOOK_HEIGHT_VARIATION;
            double xOffset = Math.cos(orbitAngle) * currentRadius;
            double zOffset = Math.sin(orbitAngle) * currentRadius;
            Location bookLoc = centerLoc.clone().add(xOffset, currentHeight, zOffset);

            // White paper dust
            Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 0.6f); // Slightly smaller size

            world.spawnParticle(
                    Particle.REDSTONE, // Changed from REDSTONE for direct color control
                    bookLoc,
                    1, 0.1, 0.1, 0.1, 0, whiteDust // Count, offset, speed(0), data
            );
        }

        // --- Magical Glyph/Sparkle Particles (General Ambiance) ---
        if (tick % 20 == 0 && random.nextBoolean()) { // Less frequent than dust
            Location ambientLoc = centerLoc.clone().add(
                    (random.nextDouble() - 0.5) * 1.2, // Wider area
                    0.8 + random.nextDouble() * 0.8, // Height range
                    (random.nextDouble() - 0.5) * 1.2
            );

            world.spawnParticle(
                    Particle.ENCHANTMENT_TABLE, // Use ENCHANT for subtle magical sparkles
                    ambientLoc,
                    1, 0.1, 0.1, 0.1, 0.005 // Count, offset, speed(extra)
            );
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return; // Check if sounds are globally enabled

        World world = loc.getWorld();
        if (world == null) return;

        int tick = animationTick; // Use base class tick

        // --- Book Page Flip Sound ---
        if (tick % 45 == 0 && random.nextInt(3) > 0) { // Slightly less frequent, chance-based
            float volume = 0.25f + random.nextFloat() * 0.1f; // Quieter
            float pitch = 0.9f + random.nextFloat() * 0.4f; // More pitch variation
            world.playSound(loc, Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.AMBIENT, volume, pitch);
        }

        // --- Enchantment Sound (Less Frequent) ---
        if (tick % 150 == 0) { // Every 7.5 seconds
            float volume = 0.15f + random.nextFloat() * 0.1f; // Quieter
            float pitch = 1.1f + random.nextFloat() * 0.2f;
            world.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.AMBIENT, volume, pitch);
        }

        // --- Subtle Magical Chime (Rarely) ---
        if (tick % 250 == 0 && random.nextBoolean()) { // Every ~12 seconds, 50% chance
            float volume = 0.2f;
            float pitch = 1.3f + random.nextFloat() * 0.3f;
            world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.AMBIENT, volume, pitch); // Or BELL
        }
    }

    /**
     * Helper method to create and configure an ItemDisplay entity.
     * (Copied and adapted from FishermanAnimation)
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
            // Use the lambda approach for cleaner configuration
            return world.spawn(loc, ItemDisplay.class, spawnedDisplay -> {
                spawnedDisplay.setItemStack(item);

                // --- Set Initial Transformation ---
                Transformation transformation = spawnedDisplay.getTransformation();
                Vector3f scaleVector = new Vector3f(scale, scale, scale);
                spawnedDisplay.setTransformation(new Transformation(
                        transformation.getTranslation(), // Initial translation offset (usually 0)
                        transformation.getLeftRotation(), // Initial rotation (usually identity)
                        scaleVector,
                        transformation.getRightRotation() // Initial right rotation (usually identity)
                ));

                // --- Set Display Properties ---
                spawnedDisplay.setInterpolationDuration(INTERPOLATION_DURATION_TICKS); // ** Enable Smooth Interpolation **
                spawnedDisplay.setInterpolationDelay(-1); // Start interpolating immediately (or 0)
                spawnedDisplay.setTeleportDuration(3); // Duration for smooth teleport movement (adjust as needed)

                spawnedDisplay.setShadowRadius(0.4f);
                spawnedDisplay.setShadowStrength(0.6f);
                spawnedDisplay.setBrightness(new Display.Brightness(15, 15)); // Max brightness
                spawnedDisplay.setBillboard(Display.Billboard.FIXED); // Don't automatically face player
                spawnedDisplay.setViewRange(0.8f); // Adjust visibility range if needed (default is often fine)
                spawnedDisplay.setDisplayWidth(0f); // No background billboard width
                spawnedDisplay.setDisplayHeight(0f); // No background billboard height

                // --- Store Metadata ---
                spawnedDisplay.getPersistentDataContainer().set(vendorEntityKey, PersistentDataType.STRING, vendorId + ":" + role);
                spawnedDisplay.setGravity(false); // Disable gravity
                spawnedDisplay.setPersistent(false); // Don't save with the world
            });
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn ItemDisplay for role " + role + " for vendor " + vendorId + ": " + e.getMessage());
            e.printStackTrace(); // Log the full error
            return null;
        }
    }


}

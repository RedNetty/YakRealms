package com.rednetty.server.mechanics.economy.vendors.visuals.animations;

import com.rednetty.server.mechanics.economy.vendors.visuals.AnimationOptions;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upgrade vendor animation with orbiting tools and forge effects
 */
public class UpgradeVendorAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;

    public UpgradeVendorAnimation(AnimationOptions options) {
        super(options);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) return Collections.emptySet();

        Set<Entity> displays = ConcurrentHashMap.newKeySet();

        // Create orbiting sword
        ItemDisplay swordDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.DIAMOND_SWORD),
                "orbit_sword",
                0.4f
        );
        displays.add(swordDisplay);

        // Create orbiting hammer
        ItemDisplay hammerDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.IRON_AXE),
                "orbit_hammer",
                0.4f
        );
        displays.add(hammerDisplay);

        // Create orbiting anvil
        ItemDisplay anvilDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.ANVIL),
                "orbit_anvil",
                0.3f
        );
        displays.add(anvilDisplay);

        return displays;
    }

    @Override
    public void updateDisplayAnimations(Set<Entity> entities, Location loc) {
        int tick = animationTick.get();

        for (Entity entity : entities) {
            if (!entity.isValid()) continue;

            String meta = entity.getPersistentDataContainer().get(
                    vendorEntityKey,
                    PersistentDataType.STRING
            );

            if (meta == null || !meta.startsWith(vendorId + ":")) continue;

            String role = meta.substring(vendorId.length() + 1);

            if (entity instanceof Display) {
                Display display = (Display) entity;
                updateDisplayAnimation(display, role, loc, tick);
            }
        }
    }

    private void updateDisplayAnimation(Display display, String role, Location loc, int tick) {
        Transformation transform = display.getTransformation();
        Vector3f scale = transform.getScale();
        Quaternionf rotation = transform.getLeftRotation();
        Location currentLoc = display.getLocation();
        Location newLoc = currentLoc.clone();

        if (role.equals("orbit_sword")) {
            // Orbit around the NPC with vertical wave
            double angle = Math.toRadians(tick * 2);
            double radius = 0.8;
            double height = 1.3 + Math.sin(Math.toRadians(tick * 3)) * 0.2;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate the sword
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 4 + 45),
                    0, 1, 0
            ));

            // Add tilt based on orbit position
            Quaternionf tilt = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(20 + Math.sin(angle) * 20),
                    1, 0, 0
            ));
            rotation.mul(tilt);
        }
        else if (role.equals("orbit_hammer")) {
            // Orbit at different speed and height
            double angle = Math.toRadians(tick * 3 + 120); // offset to separate from sword
            double radius = 0.7;
            double height = 1.5 + Math.sin(Math.toRadians(tick * 4 + 30)) * 0.15;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate the hammer
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(angle * 57.3 + 90), // convert to degrees + offset
                    0, 1, 0
            ));

            // Add tilt to look like hitting motion
            Quaternionf hammerTilt = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(-30 + Math.sin(Math.toRadians(tick * 5)) * 40),
                    1, 0, 0
            ));
            rotation.mul(hammerTilt);
        }
        else if (role.equals("orbit_anvil")) {
            // Slower orbit at lower height
            double angle = Math.toRadians(tick * 1.5 + 240); // offset to separate from other items
            double radius = 0.6;
            double height = 0.9 + Math.sin(Math.toRadians(tick * 2)) * 0.1;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate the anvil
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 2),
                    0, 1, 0
            ));
        }

        // Apply position update
        if (!currentLoc.equals(newLoc)) {
            display.teleport(newLoc);
        }

        // Apply transformation update
        transform = new Transformation(
                transform.getTranslation(),
                rotation,
                scale,
                transform.getRightRotation()
        );
        display.setTransformation(transform);
    }

    @Override
    public void applyParticleEffects(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // Calculate if hammer and sword are close to each other (potential strike)
        boolean strikeEvent = animationTick.get() % 120 < 10; // Every 6 seconds

        // Create sparks when hammer and sword are close
        if (strikeEvent && animationTick.get() % 2 == 0) {
            // Calculate position based on current tick
            double angle1 = Math.toRadians(animationTick.get() * 2);
            double angle2 = Math.toRadians(animationTick.get() * 3 + 120);

            double x1 = Math.cos(angle1) * 0.8;
            double z1 = Math.sin(angle1) * 0.8;
            double y1 = 1.3 + Math.sin(Math.toRadians(animationTick.get() * 3)) * 0.2;

            double x2 = Math.cos(angle2) * 0.7;
            double z2 = Math.sin(angle2) * 0.7;
            double y2 = 1.5 + Math.sin(Math.toRadians(animationTick.get() * 4 + 30)) * 0.15;

            // Calculate distance between items
            double dx = x1 - x2;
            double dy = y1 - y2;
            double dz = z1 - z2;
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);

            // If items are close enough, create spark effects
            if (distance < 0.8) {
                Location sparkLoc = loc.clone().add(
                        (x1 + x2) / 2,
                        (y1 + y2) / 2,
                        (z1 + z2) / 2
                );

                // Orange spark particles
                Particle.DustOptions sparkDust = new Particle.DustOptions(
                        Color.fromRGB(255, 150, 0),
                        0.8f
                );

                for (int i = 0; i < 4; i++) {
                    double spreadX = (random.nextDouble() - 0.5) * 0.3;
                    double spreadY = random.nextDouble() * 0.2;
                    double spreadZ = (random.nextDouble() - 0.5) * 0.3;

                    world.spawnParticle(
                            Particle.REDSTONE,
                            sparkLoc,
                            1, spreadX, spreadY, spreadZ, 0, sparkDust
                    );
                }

                // Add crit particles for extra effect
                world.spawnParticle(
                        Particle.CRIT,
                        sparkLoc,
                        5, 0.2, 0.2, 0.2, 0.05
                );
            }
        }

        // Create forge fire effect
        if (animationTick.get() % 10 == 0) {
            Location forgeLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.5,
                    0.5,
                    (random.nextDouble() - 0.5) * 0.5
            );

            world.spawnParticle(
                    Particle.FLAME,
                    forgeLoc,
                    1, 0.05, 0.05, 0.05, 0
            );
        }

        // Create smoke effect
        if (animationTick.get() % 25 == 0) {
            Location smokeLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.3,
                    0.7 + random.nextDouble() * 0.3,
                    (random.nextDouble() - 0.5) * 0.3
            );

            world.spawnParticle(
                    Particle.SMOKE_NORMAL,
                    smokeLoc,
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return;

        World world = loc.getWorld();
        if (world == null) return;

        // Strike sounds when items align
        if (animationTick.get() % 120 < 3) { // Aligned with spark effect
            Sound sound = Sound.BLOCK_ANVIL_USE;
            float volume = 0.3f;
            float pitch = 1.0f + random.nextFloat() * 0.2f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }

        // Ambient forge sounds
        if (animationTick.get() % 70 == 0) {
            Sound sound = Sound.BLOCK_FIRE_AMBIENT;
            float volume = 0.15f;
            float pitch = 0.8f + random.nextFloat() * 0.4f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }
    }

    private ItemDisplay createItemDisplay(World world, Location loc, ItemStack item, String role, float scale) {
        ItemDisplay display = world.spawn(loc, ItemDisplay.class);
        display.setItemStack(item);

        // Set transformation
        Transformation transformation = display.getTransformation();
        transformation = new Transformation(
                transformation.getTranslation(),
                transformation.getLeftRotation(),
                new Vector3f(scale, scale, scale),
                transformation.getRightRotation()
        );
        display.setTransformation(transformation);

        // Set display properties
        display.setShadowRadius(0.5f);
        display.setShadowStrength(0.7f);
        display.setBrightness(new Display.Brightness(15, 15));

        // Store metadata
        display.getPersistentDataContainer().set(vendorEntityKey, PersistentDataType.STRING, vendorId + ":" + role);
        display.setGravity(false);
        display.setPersistent(false);

        return display;
    }
}
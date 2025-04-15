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
 * Default animation for generic vendors with orbiting displays
 */
public class DefaultVendorAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;

    public DefaultVendorAnimation(AnimationOptions options) {
        super(options);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) return Collections.emptySet();

        Set<Entity> displays = ConcurrentHashMap.newKeySet();

        // Create orbiting generic items
        Material[] items = {
                Material.COMPASS, Material.CLOCK, Material.PAPER,
                Material.FEATHER, Material.BOOK
        };

        for (int i = 0; i < items.length; i++) {
            ItemDisplay itemDisplay = createItemDisplay(
                    world,
                    loc.clone(),
                    new ItemStack(items[i]),
                    "orbit_item_" + i,
                    0.4f
            );
            displays.add(itemDisplay);
        }

        return displays;
    }

    @Override
    public void updateDisplayAnimations(Set<Entity> entities, Location loc) {
        int tick = animationTick;

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

        if (role.startsWith("orbit_item_")) {
            int itemIndex = Integer.parseInt(role.substring(role.lastIndexOf('_') + 1));

            // Create orbital pattern
            // Each item has a different orbit speed and radius
            double speed = 1.5 + (itemIndex * 0.3);
            double angle = Math.toRadians(tick * speed + (itemIndex * 72));
            double radius = 0.6 + (itemIndex % 3) * 0.1;
            double height = 1.0 + (itemIndex * 0.1) + Math.sin(Math.toRadians(tick * 2 + itemIndex * 30)) * 0.1;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotation varies by item
            if (itemIndex % 2 == 0) {
                // Some items rotate constantly
                rotation = new Quaternionf(new AxisAngle4f(
                        (float)Math.toRadians(tick * 3),
                        0, 1, 0
                ));
            } else {
                // Others rotate to face direction of travel
                double nextAngle = angle + Math.toRadians(5);
                double nextX = Math.cos(nextAngle) * radius;
                double nextZ = Math.sin(nextAngle) * radius;
                double angleToNext = Math.atan2(nextZ - z, nextX - x);

                rotation = new Quaternionf(new AxisAngle4f(
                        (float)angleToNext,
                        0, 1, 0
                ));

                // Add tilt variation
                Quaternionf tilt = new Quaternionf(new AxisAngle4f(
                        (float)Math.toRadians(20 + Math.sin(Math.toRadians(tick + itemIndex * 20)) * 15),
                        1, 0, 0
                ));
                rotation.mul(tilt);
            }
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

        // Simple ambient particles
        if (animationTick % 10 == 0) {
            Location particleLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.6,
                    1.0 + random.nextDouble() * 0.4,
                    (random.nextDouble() - 0.5) * 0.6
            );

            world.spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }

        // Slow ascending particles
        if (animationTick % 20 == 0) {
            Location ascendLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.4,
                    0.8,
                    (random.nextDouble() - 0.5) * 0.4
            );

            Particle.DustOptions whiteDust = new Particle.DustOptions(
                    Color.fromRGB(240, 240, 255),
                    0.6f
            );

            world.spawnParticle(
                    Particle.REDSTONE,
                    ascendLoc,
                    1, 0.02, 0.1, 0.02, 0, whiteDust
            );
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return;

        World world = loc.getWorld();
        if (world == null) return;

        // Generic vendor sound
        if (animationTick % 100 == 0) {
            Sound sound = Sound.ENTITY_VILLAGER_AMBIENT;
            float volume = 0.1f;
            float pitch = 1.0f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }

        // Occasional item interaction sound
        if (animationTick % 60 == 0 && random.nextBoolean()) {
            Sound sound = Sound.ENTITY_ITEM_PICKUP;
            float volume = 0.15f;
            float pitch = 1.0f + random.nextFloat() * 0.5f;

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
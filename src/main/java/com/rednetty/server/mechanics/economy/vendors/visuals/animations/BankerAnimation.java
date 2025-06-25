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
 * Banker animation with orbiting coins and treasures
 */
public class BankerAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;

    public BankerAnimation(AnimationOptions options) {
        super(options);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) return Collections.emptySet();

        Set<Entity> displays = ConcurrentHashMap.newKeySet();

        // Create orbiting gold ingot
        ItemDisplay goldDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.GOLD_INGOT),
                "orbit_gold",
                0.4f
        );
        displays.add(goldDisplay);

        // Create orbiting emerald
        ItemDisplay emeraldDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.EMERALD),
                "orbit_emerald",
                0.4f
        );
        displays.add(emeraldDisplay);

        // Create orbiting diamond
        ItemDisplay diamondDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.DIAMOND),
                "orbit_diamond",
                0.4f
        );
        displays.add(diamondDisplay);

        // Create multiple orbiting gold nuggets
        for (int i = 0; i < 5; i++) {
            ItemDisplay coinDisplay = createItemDisplay(
                    world,
                    loc.clone(),
                    new ItemStack(Material.GOLD_NUGGET),
                    "orbit_coin_" + i,
                    0.3f
            );
            displays.add(coinDisplay);
        }

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

        if (role.equals("orbit_gold")) {
            // Gold ingot orbit
            double angle = Math.toRadians(tick * 2);
            double radius = 0.7;
            double height = 1.2 + Math.sin(Math.toRadians(tick * 2)) * 0.1;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate gold
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 4),
                    0, 1, 0
            ));
        }
        else if (role.equals("orbit_emerald")) {
            // Emerald orbit
            double angle = Math.toRadians(tick * 2.5 + 120); // offset to separate from gold
            double radius = 0.8;
            double height = 1.4 + Math.sin(Math.toRadians(tick * 3 + 45)) * 0.15;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate emerald
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 5),
                    0, 1, 0
            ));
        }
        else if (role.equals("orbit_diamond")) {
            // Diamond orbit
            double angle = Math.toRadians(tick * 1.8 + 240); // offset to separate from others
            double radius = 0.65;
            double height = 1.0 + Math.sin(Math.toRadians(tick * 2.5 + 90)) * 0.12;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate diamond
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 3.5),
                    0, 1, 0
            ));
        }
        else if (role.startsWith("orbit_coin_")) {
            int coinIndex = Integer.parseInt(role.substring(role.lastIndexOf('_') + 1));

            // Create double helix pattern with coins
            double mainAngle = Math.toRadians(tick * 4 + (coinIndex * 72)); // Even spacing around circle
            double verticalOffset = Math.sin(mainAngle + Math.toRadians(coinIndex * 30)) * 0.3;
            double radius = 0.5;

            double x = Math.cos(mainAngle) * radius;
            double z = Math.sin(mainAngle) * radius;
            double y = 1.3 + verticalOffset;

            newLoc = loc.clone().add(x, y, z);

            // Coin spinning rapidly
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 12),
                    0, 1, 0
            ));

            // Add tilt based on vertical position
            Quaternionf tilt = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(verticalOffset * 45),
                    1, 0, 0
            ));
            rotation.mul(tilt);
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

        // Gold sparkle particles
        if (animationTick.get() % 5 == 0) {
            // Calculate position based on current tick for gold
            double angle = Math.toRadians(animationTick.get() * 2);
            double radius = 0.7;
            double height = 1.2 + Math.sin(Math.toRadians(animationTick.get() * 2)) * 0.1;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location goldLoc = loc.clone().add(x, height, z);

            // Gold sparkles
            Particle.DustOptions goldDust = new Particle.DustOptions(
                    Color.fromRGB(255, 215, 0),
                    0.7f
            );

            world.spawnParticle(
                    Particle.REDSTONE,
                    goldLoc,
                    1, 0.05, 0.05, 0.05, 0, goldDust
            );
        }

        // Emerald sparkles
        if (animationTick.get() % 7 == 0) {
            // Calculate position based on current tick for emerald
            double angle = Math.toRadians(animationTick.get() * 2.5 + 120);
            double radius = 0.8;
            double height = 1.4 + Math.sin(Math.toRadians(animationTick.get() * 3 + 45)) * 0.15;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location emeraldLoc = loc.clone().add(x, height, z);

            // Green sparkles
            Particle.DustOptions greenDust = new Particle.DustOptions(
                    Color.fromRGB(0, 200, 100),
                    0.7f
            );

            world.spawnParticle(
                    Particle.REDSTONE,
                    emeraldLoc,
                    1, 0.05, 0.05, 0.05, 0, greenDust
            );
        }

        // Diamond sparkles
        if (animationTick.get() % 6 == 0) {
            // Calculate position based on current tick for diamond
            double angle = Math.toRadians(animationTick.get() * 1.8 + 240);
            double radius = 0.65;
            double height = 1.0 + Math.sin(Math.toRadians(animationTick.get() * 2.5 + 90)) * 0.12;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location diamondLoc = loc.clone().add(x, height, z);

            // Blue/white sparkles
            Particle.DustOptions diamondDust = new Particle.DustOptions(
                    Color.fromRGB(210, 240, 255),
                    0.7f
            );

            world.spawnParticle(
                    Particle.REDSTONE,
                    diamondLoc,
                    1, 0.05, 0.05, 0.05, 0, diamondDust
            );
        }

        // Money glitter effect
        if (animationTick.get() % 20 == 0) {
            Location centerLoc = loc.clone().add(0, 1.3, 0);

            Particle.DustOptions goldDust = new Particle.DustOptions(
                    Color.fromRGB(255, 215, 0),
                    0.6f
            );

            for (int i = 0; i < 3; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = random.nextDouble() * 0.5;
                double height = random.nextDouble() * 0.4;

                world.spawnParticle(
                        Particle.REDSTONE,
                        centerLoc.clone().add(
                                Math.cos(angle) * distance,
                                height,
                                Math.sin(angle) * distance
                        ),
                        1, 0.05, 0.05, 0.05, 0, goldDust
                );
            }
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return;

        World world = loc.getWorld();
        if (world == null) return;

        // Coin sounds
        if (animationTick.get() % 30 == 0) {
            Sound sound = Sound.BLOCK_CHAIN_PLACE;
            float volume = 0.15f;
            float pitch = 1.5f + random.nextFloat() * 0.2f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }

        // Second coin sound occasionally
        if (animationTick.get() % 80 == 0) {
            Sound sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            float volume = 0.2f;
            float pitch = 0.7f + random.nextFloat() * 0.3f;

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
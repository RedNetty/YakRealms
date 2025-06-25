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
 * Medic animation with orbiting healing items and effects
 */
public class MedicAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;

    public MedicAnimation(AnimationOptions options) {
        super(options);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) return Collections.emptySet();

        Set<Entity> displays = ConcurrentHashMap.newKeySet();

        // Create orbiting healing items
        Material[] healingItems = {
                Material.POTION, Material.SPLASH_POTION, Material.GOLDEN_APPLE,
                Material.GLISTERING_MELON_SLICE, Material.TOTEM_OF_UNDYING
        };

        for (int i = 0; i < healingItems.length; i++) {
            ItemDisplay itemDisplay = createItemDisplay(
                    world,
                    loc.clone(),
                    new ItemStack(healingItems[i]),
                    "orbit_item_" + i,
                    0.4f
            );
            displays.add(itemDisplay);
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

        if (role.startsWith("orbit_item_")) {
            int itemIndex = Integer.parseInt(role.substring(role.lastIndexOf('_') + 1));

            // Items orbit in DNA helix pattern
            double mainAngle = Math.toRadians(tick * 2 + (itemIndex * 72)); // spread evenly
            double radius = 0.7;

            // Create double helix effect
            boolean isLeftStrand = itemIndex % 2 == 0;
            double offset = isLeftStrand ? 0 : Math.PI; // Offset for second strand
            double sideRadius = 0.2;

            // Main orbital position
            double x = Math.cos(mainAngle) * radius;
            double z = Math.sin(mainAngle) * radius;

            // Add helix twist
            double twistAngle = mainAngle * 3 + offset;
            x += Math.cos(twistAngle) * sideRadius;
            z += Math.sin(twistAngle) * sideRadius;

            // Vertical position with wave
            double height = 1.2 + Math.sin(mainAngle * 1.5) * 0.3;

            newLoc = loc.clone().add(x, height, z);

            // Basic rotation to face outward
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)mainAngle,
                    0, 1, 0
            ));

            // Add item-specific animations
            switch(itemIndex) {
                case 0: // Regular potion - tilt to show liquid
                    Quaternionf potionTilt = new Quaternionf(new AxisAngle4f(
                            (float)Math.toRadians(30 + Math.sin(Math.toRadians(tick * 2)) * 10),
                            1, 0, 0
                    ));
                    rotation.mul(potionTilt);
                    break;

                case 1: // Splash potion - more dynamic movement
                    Quaternionf splashTilt = new Quaternionf(new AxisAngle4f(
                            (float)Math.toRadians(-20 + Math.sin(Math.toRadians(tick * 3)) * 20),
                            1, 0, 1
                    ));
                    rotation.mul(splashTilt);
                    break;

                case 2: // Golden apple - slow spin
                    Quaternionf appleSpin = new Quaternionf(new AxisAngle4f(
                            (float)Math.toRadians(tick * 3),
                            0, 1, 0
                    ));
                    rotation = appleSpin; // Override to just spin
                    break;

                case 4: // Totem - showcase display
                    // Pulse scale for totem
                    float totemPulse = 1.0f + (float)Math.sin(Math.toRadians(tick * 4)) * 0.15f;
                    scale = new Vector3f(0.4f * totemPulse, 0.4f * totemPulse, 0.4f * totemPulse);

                    // Special facing
                    rotation = new Quaternionf(new AxisAngle4f(
                            (float)Math.toRadians(tick + mainAngle * 57.3),
                            0, 1, 0
                    ));
                    break;
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

        // Healing particles (green)
        if (animationTick.get() % 3 == 0) {
            // Calculate position on helix based on tick
            double angle = Math.toRadians(animationTick.get() * 2);
            double radius = 0.7;
            double height = 1.2 + Math.sin(angle * 1.5) * 0.3;

            // Add randomness
            double x = Math.cos(angle) * radius + (random.nextDouble() - 0.5) * 0.3;
            double z = Math.sin(angle) * radius + (random.nextDouble() - 0.5) * 0.3;

            Location healLoc = loc.clone().add(x, height, z);

            // Green healing particles
            Particle.DustOptions healDust = new Particle.DustOptions(
                    Color.fromRGB(0, 255, 80),
                    0.7f
            );

            world.spawnParticle(
                    Particle.REDSTONE,
                    healLoc,
                    1, 0.05, 0.05, 0.05, 0, healDust
            );
        }

        // Heart particles occasionally
        if (animationTick.get() % 20 == 0) {
            Location heartLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.6,
                    1.3 + random.nextDouble() * 0.4,
                    (random.nextDouble() - 0.5) * 0.6
            );

            world.spawnParticle(
                    Particle.HEART,
                    heartLoc,
                    1, 0.05, 0.05, 0.05, 0
            );
        }

        // Special totem effect
        if (animationTick.get() % 80 == 0) {
            // Calculate totem position (index 4)
            double mainAngle = Math.toRadians(animationTick.get() * 2 + (4 * 72));
            double radius = 0.7;
            boolean isLeftStrand = 4 % 2 == 0;
            double offset = isLeftStrand ? 0 : Math.PI;
            double sideRadius = 0.2;

            double x = Math.cos(mainAngle) * radius;
            double z = Math.sin(mainAngle) * radius;

            double twistAngle = mainAngle * 3 + offset;
            x += Math.cos(twistAngle) * sideRadius;
            z += Math.sin(twistAngle) * sideRadius;

            double height = 1.2 + Math.sin(mainAngle * 1.5) * 0.3;

            Location totemLoc = loc.clone().add(x, height, z);

            // Create totem revival effect
            for (int i = 0; i < 8; i++) {
                double angle = Math.random() * Math.PI * 2;
                double distance = 0.2 + random.nextDouble() * 0.3;
                double yOffset = random.nextDouble() * 0.5;

                Particle.DustOptions totemDust = new Particle.DustOptions(
                        Color.fromRGB(255, 210, 100),
                        0.8f
                );

                world.spawnParticle(
                        Particle.REDSTONE,
                        totemLoc.clone().add(
                                Math.cos(angle) * distance,
                                yOffset,
                                Math.sin(angle) * distance
                        ),
                        1, 0.05, 0.05, 0.05, 0, totemDust
                );
            }
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return;

        World world = loc.getWorld();
        if (world == null) return;

        // Potion sounds
        if (animationTick.get() % 60 == 0 && random.nextBoolean()) {
            Sound sound = Sound.ENTITY_WITCH_DRINK;
            float volume = 0.2f;
            float pitch = 1.0f + random.nextFloat() * 0.5f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }

        // Brewing stand sound
        if (animationTick.get() % 90 == 0) {
            Sound sound = Sound.BLOCK_BREWING_STAND_BREW;
            float volume = 0.15f;
            float pitch = 0.8f + random.nextFloat() * 0.4f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }

        // Special totem activation sound
        if (animationTick.get() % 160 < 3) {
            Sound sound = Sound.ITEM_TOTEM_USE;
            float volume = 0.1f;
            float pitch = 1.2f;

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
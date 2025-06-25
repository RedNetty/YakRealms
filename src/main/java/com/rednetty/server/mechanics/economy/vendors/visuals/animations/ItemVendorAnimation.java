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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Item vendor animation with orbiting merchandise
 */
public class ItemVendorAnimation extends BaseVendorAnimation {
    private final Random random = new Random();
    private final NamespacedKey vendorEntityKey;

    public ItemVendorAnimation(AnimationOptions options) {
        super(options);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) return Collections.emptySet();

        Set<Entity> displays = ConcurrentHashMap.newKeySet();

        // Create orbiting items of different types representing merchandise
        Material[] shopItems = {
                Material.DIAMOND_SWORD, Material.DIAMOND_HELMET,
                Material.BOW, Material.SHIELD, Material.GOLDEN_APPLE,
                Material.IRON_CHESTPLATE, Material.TRIDENT
        };

        for (int i = 0; i < shopItems.length; i++) {
            ItemDisplay itemDisplay = createItemDisplay(
                    world,
                    loc.clone(),
                    new ItemStack(shopItems[i]),
                    "orbit_item_" + i,
                    0.45f
            );
            displays.add(itemDisplay);
        }

        return displays;
    }

    @Override
    public void updateDisplayAnimations(Set<Entity> entities, Location loc) {
        AtomicInteger tick = animationTick;

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
                updateDisplayAnimation(display, role, loc, tick.get());
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
            int totalItems = 7; // Match the number of items created

            // Create helix pattern for items
            // Each item has a position on the helix determined by its index
            double progress = ((double)tick / 180.0) + ((double)itemIndex / totalItems);
            progress = progress % 1.0; // Keep in 0-1 range

            // Helix parameters
            double heightRange = 1.0; // Total height of the helix
            double baseHeight = 0.8; // Bottom position
            double radius = 0.7;

            // Calculate position on helix
            double angle = progress * Math.PI * 2 * 3; // 3 full turns in the helix
            double height = baseHeight + progress * heightRange;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotation to face outward plus item-specific tilts
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)angle,
                    0, 1, 0
            ));

            // Add upward tilt for weapons
            if (itemIndex == 0 || itemIndex == 2 || itemIndex == 6) { // sword, bow, trident
                Quaternionf weaponTilt = new Quaternionf(new AxisAngle4f(
                        (float)Math.toRadians(45 + Math.sin(Math.toRadians(tick * 2)) * 10),
                        1, 0, 0
                ));
                rotation.mul(weaponTilt);
            }
            // Armor pieces face forward
            else if (itemIndex == 1 || itemIndex == 5) { // helmet, chestplate
                Quaternionf armorTilt = new Quaternionf(new AxisAngle4f(
                        (float)Math.toRadians(20),
                        1, 0, 0
                ));
                rotation.mul(armorTilt);
            }
            // Shield tilts to show face
            else if (itemIndex == 3) { // shield
                Quaternionf shieldTilt = new Quaternionf(new AxisAngle4f(
                        (float)Math.toRadians(-30),
                        1, 0, 0
                ));
                rotation.mul(shieldTilt);
            }

            // Highlight effect - items pulse slightly to draw attention
            if ((tick + itemIndex * 20) % 140 < 20) {
                float pulseFactor = 1.0f + (float)Math.sin(Math.toRadians((tick + itemIndex * 20) % 140 * 18)) * 0.2f;
                scale = new Vector3f(0.45f * pulseFactor, 0.45f * pulseFactor, 0.45f * pulseFactor);
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

        // Highlight particles for items being showcased
        if (animationTick.get() % 5 == 0) {
            // Determine which item is being highlighted based on tick
            int highlightIndex = (animationTick.get() / 140) % 7;

            // Calculate the position of the highlighted item
            int tick = animationTick.get();
            int totalItems = 7;

            double progress = ((double)tick / 180.0) + ((double)highlightIndex / totalItems);
            progress = progress % 1.0;

            double heightRange = 1.0;
            double baseHeight = 0.8;
            double radius = 0.7;

            double angle = progress * Math.PI * 2 * 3;
            double height = baseHeight + progress * heightRange;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location itemLoc = loc.clone().add(x, height, z);

            // Create sparkle effect around highlighted item
            if ((tick + highlightIndex * 20) % 140 < 20) {
                Particle.DustOptions sparkDust = new Particle.DustOptions(
                        Color.fromRGB(255, 255, 150),
                        0.7f
                );

                world.spawnParticle(
                        Particle.REDSTONE,
                        itemLoc.clone().add(
                                (random.nextDouble() - 0.5) * 0.3,
                                (random.nextDouble() - 0.5) * 0.3,
                                (random.nextDouble() - 0.5) * 0.3
                        ),
                        1, 0.02, 0.02, 0.02, 0, sparkDust
                );
            }
        }

        // Occasional generic item sparkle
        if (animationTick.get() % 20 == 0) {
            Location sparkLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 0.8,
                    1.0 + random.nextDouble() * 0.8,
                    (random.nextDouble() - 0.5) * 0.8
            );

            world.spawnParticle(
                    Particle.END_ROD,
                    sparkLoc,
                    1, 0.05, 0.05, 0.05, 0.01
            );
        }
    }

    @Override
    public void playAmbientSound(Location loc) {
        if (!soundsEnabled) return;

        World world = loc.getWorld();
        if (world == null) return;

        // Highlighting sound for currently showcased item
        if (animationTick.get() % 140 == 0) {
            Sound sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            float volume = 0.2f;
            float pitch = 1.0f + random.nextFloat() * 0.5f;

            world.playSound(loc, sound, SoundCategory.AMBIENT, volume, pitch);
        }

        // General item interactions
        if (animationTick.get() % 70 == 0 && random.nextBoolean()) {
            Sound sound = Sound.ENTITY_ITEM_PICKUP;
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
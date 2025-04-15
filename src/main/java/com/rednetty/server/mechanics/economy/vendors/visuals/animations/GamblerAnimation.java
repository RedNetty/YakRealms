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
 * Simple gambler animation with orbiting dice
 */
public class GamblerAnimation extends BaseVendorAnimation {
    private final NamespacedKey vendorEntityKey;

    public GamblerAnimation(AnimationOptions options) {
        super(options);
        this.vendorEntityKey = new NamespacedKey(plugin, "vendor_entity");
    }

    @Override
    public Set<Entity> createDisplayEntities(Location loc, NamespacedKey key) {
        World world = loc.getWorld();
        if (world == null) return Collections.emptySet();

        Set<Entity> displays = ConcurrentHashMap.newKeySet();

        // Create orbiting dice
        ItemDisplay diceDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.EMERALD),
                "orbiting_dice",
                0.3f
        );
        displays.add(diceDisplay);

        // Create gold nugget as second orbital
        ItemDisplay goldDisplay = createItemDisplay(
                world,
                loc.clone(),
                new ItemStack(Material.GOLD_INGOT),
                "orbiting_gold",
                0.3f
        );
        displays.add(goldDisplay);

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

        if (role.equals("orbiting_dice")) {
            // Simple orbit around the NPC
            double angle = Math.toRadians(tick * 2);
            double radius = 0.7;
            double height = 1.0;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate the dice
            rotation = new Quaternionf(new AxisAngle4f(
                    (float)Math.toRadians(tick * 3),
                    0, 1, 0
            ));
        }
        else if (role.equals("orbiting_gold")) {
            // Second orbit at different speed and angle
            double angle = Math.toRadians(tick * 3 + 200); // Offset by 180 degrees
            double radius = 0.7;
            double height = 1.2;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            newLoc = loc.clone().add(x, height, z);

            // Rotate the gold
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
        // No particle effects
    }

    @Override
    public void playAmbientSound(Location loc) {
        // No ambient sounds
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
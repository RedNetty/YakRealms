package com.rednetty.server.mechanics.combat.death.remnant.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

public class GroundDetectionUtil {
    private static final double MAX_CHECK_DISTANCE = 5.0;
    private static final double PRECISION = 0.1;

    /**
     * Finds the best ground location for remnant placement
     */
    public static Location findOptimalGroundLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        Location optimal = findInitialGroundLocation(location);
        if (optimal == null) return null;

        // Adjust for block type
        optimal = adjustForBlockType(optimal);

        // Ensure the space above is clear
        if (!hasEnoughClearance(optimal)) {
            // Try to find nearby clear location
            optimal = findNearbyClearLocation(optimal);
        }

        return optimal;
    }

    /**
     * Finds initial ground location with precise stepping
     */
    private static Location findInitialGroundLocation(Location location) {
        Location check = location.clone();
        double startY = check.getY();
        double endY = Math.max(0, startY - MAX_CHECK_DISTANCE);

        // Check downward with precision stepping
        for (double y = startY; y >= endY; y -= PRECISION) {
            check.setY(y);
            if (isValidGround(check)) {
                return check;
            }
        }

        // If not found going down, check upward for buried locations
        endY = Math.min(check.getWorld().getMaxHeight(), startY + MAX_CHECK_DISTANCE);
        for (double y = startY; y <= endY; y += PRECISION) {
            check.setY(y);
            if (isValidGround(check) && hasEnoughClearance(check)) {
                return check;
            }
        }

        return null;
    }

    /**
     * Checks if a location has valid ground below it
     */
    private static boolean isValidGround(Location location) {
        Block block = location.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);

        // Check if current block is air/passthrough and below is solid
        return isPassthrough(block.getType()) && isSupportive(below);
    }

    /**
     * Checks if a material can be passed through
     */
    private static boolean isPassthrough(Material material) {
        return material.isAir() ||
                material == Material.WATER ||
                material == Material.GRASS ||
                material == Material.TALL_GRASS ||
                material.name().contains("CARPET");
    }

    /**
     * Checks if a block can support the remnant
     */
    private static boolean isSupportive(Block block) {
        if (block.getType().isSolid()) {
            BlockData data = block.getBlockData();

            // Special handling for stairs and slabs
            if (data instanceof Stairs) {
                Stairs stairs = (Stairs) data;
                return stairs.getHalf() == Stairs.Half.BOTTOM;
            }

            if (data instanceof Slab) {
                Slab slab = (Slab) data;
                return slab.getType() != Slab.Type.TOP;
            }

            return true;
        }
        return false;
    }

    /**
     * Adjusts location based on block type (stairs, slabs, etc.)
     */
    private static Location adjustForBlockType(Location location) {
        Block below = location.getBlock().getRelative(BlockFace.DOWN);
        BlockData data = below.getBlockData();

        if (data instanceof Stairs) {
            Stairs stairs = (Stairs) data;
            // Adjust position based on stair orientation
            double yOffset = 0;
            if (stairs.getHalf() == Stairs.Half.TOP) {
                yOffset = 0.5;
            }
            return location.add(0, yOffset, 0);
        }

        if (data instanceof Slab) {
            Slab slab = (Slab) data;
            // Adjust position based on slab type
            double yOffset = 0;
            if (slab.getType() == Slab.Type.TOP) {
                yOffset = 0.5;
            } else if (slab.getType() == Slab.Type.DOUBLE) {
                yOffset = 1.0;
            }
            return location.add(0, yOffset, 0);
        }

        return location;
    }

    /**
     * Checks if there's enough vertical clearance for the remnant
     */
    private static boolean hasEnoughClearance(Location location) {
        double requiredHeight = 1.0; // Height needed for remnant
        Location check = location.clone();

        for (double y = 0; y <= requiredHeight; y += PRECISION) {
            check.setY(location.getY() + y);
            if (!isPassthrough(check.getBlock().getType())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds a nearby location with enough clearance
     */
    private static Location findNearbyClearLocation(Location location) {
        double searchRadius = 1.0;
        int checks = 8; // Number of points to check around the circle

        for (int i = 0; i < checks; i++) {
            double angle = (2 * Math.PI * i) / checks;
            double x = searchRadius * Math.cos(angle);
            double z = searchRadius * Math.sin(angle);

            Location check = location.clone().add(x, 0, z);
            if (hasEnoughClearance(check)) {
                return check;
            }
        }

        return null;
    }
}
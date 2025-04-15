package com.rednetty.server.utils.math;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Utility class for creating and manipulating axis-aligned bounding boxes.
 * Used for collision detection in the MagicStaff projectile system.
 */
public class BoundingBox {
    private final Vector min;
    private final Vector max;

    /**
     * Creates a new bounding box with the specified minimum and maximum corners
     *
     * @param min The minimum corner
     * @param max The maximum corner
     */
    public BoundingBox(Vector min, Vector max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Creates a new bounding box from a block
     *
     * @param block The block to create the bounding box for
     */
    public BoundingBox(Block block) {
        this.min = new Vector(block.getX(), block.getY(), block.getZ());
        this.max = new Vector(block.getX() + 1, block.getY() + 1, block.getZ() + 1);
    }

    /**
     * Creates a new bounding box from an entity
     *
     * @param entity The entity to create the bounding box for
     */
    public BoundingBox(Entity entity) {
        double width = entity.getWidth() / 2.0;
        double height = entity.getHeight();

        Location location = entity.getLocation();
        this.min = new Vector(
                location.getX() - width,
                location.getY(),
                location.getZ() - width
        );
        this.max = new Vector(
                location.getX() + width,
                location.getY() + height,
                location.getZ() + width
        );
    }

    /**
     * Gets the minimum corner of the bounding box
     *
     * @return The minimum corner
     */
    public Vector getMin() {
        return min.clone();
    }

    /**
     * Gets the maximum corner of the bounding box
     *
     * @return The maximum corner
     */
    public Vector getMax() {
        return max.clone();
    }

    /**
     * Checks if a point is within this bounding box
     *
     * @param point The point to check
     * @return true if the point is within the bounding box
     */
    public boolean contains(Vector point) {
        return point.getX() >= min.getX() && point.getX() <= max.getX() &&
                point.getY() >= min.getY() && point.getY() <= max.getY() &&
                point.getZ() >= min.getZ() && point.getZ() <= max.getZ();
    }

    /**
     * Checks if this bounding box intersects another bounding box
     *
     * @param other The other bounding box
     * @return true if the bounding boxes intersect
     */
    public boolean intersects(BoundingBox other) {
        return max.getX() >= other.min.getX() && min.getX() <= other.max.getX() &&
                max.getY() >= other.min.getY() && min.getY() <= other.max.getY() &&
                max.getZ() >= other.min.getZ() && min.getZ() <= other.max.getZ();
    }

    /**
     * Expands the bounding box by the given amount in all directions
     *
     * @param amount The amount to expand by
     * @return A new bounding box expanded by the given amount
     */
    public BoundingBox expand(double amount) {
        return new BoundingBox(
                min.clone().subtract(new Vector(amount, amount, amount)),
                max.clone().add(new Vector(amount, amount, amount))
        );
    }
}
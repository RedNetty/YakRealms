package com.rednetty.server.utils.math;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for performing ray tracing operations.
 * Used for projectile path calculation and collision detection.
 */
public class RayTrace {
    private final Vector origin;
    private final Vector direction;

    /**
     * Creates a new ray tracer with the given origin and direction
     *
     * @param origin    The starting point of the ray
     * @param direction The direction of the ray
     */
    public RayTrace(Vector origin, Vector direction) {
        this.origin = origin.clone();
        this.direction = direction.clone().normalize();
    }

    /**
     * Gets the origin of the ray
     *
     * @return The origin point
     */
    public Vector getOrigin() {
        return origin.clone();
    }

    /**
     * Gets the direction of the ray
     *
     * @return The normalized direction vector
     */
    public Vector getDirection() {
        return direction.clone();
    }

    /**
     * Calculates a point along the ray at a given distance
     *
     * @param distance The distance along the ray
     * @return The position vector at that distance
     */
    public Vector getPointAtDistance(double distance) {
        return origin.clone().add(direction.clone().multiply(distance));
    }

    /**
     * Traverses along the ray, returning points at regular intervals
     *
     * @param distance  The total distance to traverse
     * @param precision The interval between points
     * @return A list of points along the ray's path
     */
    public List<Vector> traverse(int distance, double precision) {
        List<Vector> points = new ArrayList<>();
        for (double d = 0; d <= distance; d += precision) {
            points.add(getPointAtDistance(d));
        }
        return points;
    }

    /**
     * Checks if the ray intersects a bounding box
     *
     * @param point The current position to check
     * @param box   The bounding box to check against
     * @return true if the ray intersects the bounding box
     */
    public boolean intersectsBox(Vector point, BoundingBox box) {
        return box.contains(point);
    }

    /**
     * Checks if the ray intersects a bounding box with increased precision
     *
     * @param point     The current position to check
     * @param precision Extra precision factor
     * @param box       The bounding box to check against
     * @return true if the ray intersects the bounding box
     */
    public boolean intersectsBox(Vector point, double precision, BoundingBox box) {
        // Check if the point itself is in the box
        if (box.contains(point)) {
            return true;
        }

        // If not, check additional points around it for more precision
        for (double x = -precision; x <= precision; x += precision) {
            for (double y = -precision; y <= precision; y += precision) {
                for (double z = -precision; z <= precision; z += precision) {
                    Vector testPoint = point.clone().add(new Vector(x, y, z));
                    if (box.contains(testPoint)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Calculates the intersection of the ray with a bounding box
     *
     * @param box The bounding box to check against
     * @return The distance to intersection, or -1 if no intersection
     */
    public double intersectionDistance(BoundingBox box) {
        Vector invDir = new Vector(1.0 / direction.getX(), 1.0 / direction.getY(), 1.0 / direction.getZ());
        boolean signDirX = invDir.getX() < 0;
        boolean signDirY = invDir.getY() < 0;
        boolean signDirZ = invDir.getZ() < 0;

        Vector bbox = signDirX ? box.getMax() : box.getMin();
        double txmin = (bbox.getX() - origin.getX()) * invDir.getX();
        bbox = signDirX ? box.getMin() : box.getMax();
        double txmax = (bbox.getX() - origin.getX()) * invDir.getX();

        bbox = signDirY ? box.getMax() : box.getMin();
        double tymin = (bbox.getY() - origin.getY()) * invDir.getY();
        bbox = signDirY ? box.getMin() : box.getMax();
        double tymax = (bbox.getY() - origin.getY()) * invDir.getY();

        if (txmin > tymax || tymin > txmax) {
            return -1;
        }

        if (tymin > txmin) {
            txmin = tymin;
        }

        if (tymax < txmax) {
            txmax = tymax;
        }

        bbox = signDirZ ? box.getMax() : box.getMin();
        double tzmin = (bbox.getZ() - origin.getZ()) * invDir.getZ();
        bbox = signDirZ ? box.getMin() : box.getMax();
        double tzmax = (bbox.getZ() - origin.getZ()) * invDir.getZ();

        if (txmin > tzmax || tzmin > txmax) {
            return -1;
        }

        if (tzmin > txmin) {
            txmin = tzmin;
        }

        if (tzmax < txmax) {
            txmax = tzmax;
        }

        return txmin >= 0 ? txmin : txmax >= 0 ? txmax : -1;
    }
}
package com.rednetty.server.mechanics.combat.death.remnant;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class DeathRemnantStructure {
    private static final Random RANDOM = new Random();

    public static Location calculatePartPosition(Location base, float rotation, Vector offset) {
        double rad = Math.toRadians(rotation);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double x = offset.getX() * cos - offset.getZ() * sin;
        double z = offset.getX() * sin + offset.getZ() * cos;

        return base.clone().add(x, 0, z);
    }

    public static Location getSkullPosition(Location spineLoc, float rotation) {
        return calculatePartPosition(spineLoc, rotation, new Vector(0, 0, 0.3));
    }

    public static EulerAngle getPartRotation(float baseRotation, DeathRemnant.BonePart part) {
        double yaw = baseRotation;
        String partName = part.name();
        if (partName.startsWith("RIB_RIGHT")) {
            yaw += 90;
        } else if (partName.startsWith("RIB_LEFT")) {
            yaw -= 90;
        } else if (partName.equals("ARM_RIGHT")) {
            yaw += 45;
        } else if (partName.equals("ARM_LEFT")) {
            yaw -= 45;
        } else if (partName.equals("LEG_RIGHT")) {
            yaw += 20;
        } else if (partName.equals("LEG_LEFT")) {
            yaw -= 20;
        }
        return new EulerAngle(Math.toRadians(90), Math.toRadians(yaw), 0);
    }

    public static Vector[] calculateSafeScatteredPositions(int itemCount, Location center,
                                                           Collection<ArmorStand> existingStands,
                                                           double minDistance) {
        List<Vector> positions = new ArrayList<>();
        List<Vector> occupied = new ArrayList<>();

        for (ArmorStand stand : existingStands) {
            Vector standPos = stand.getLocation().toVector().subtract(center.toVector());
            occupied.add(new Vector(standPos.getX(), 0, standPos.getZ()));
        }

        for (int i = 0; i < itemCount; i++) {
            Vector candidate;
            int attempts = 0;

            do {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double distance = RANDOM.nextDouble() * 1.5;
                candidate = new Vector(
                        Math.cos(angle) * distance,
                        0,
                        Math.sin(angle) * distance
                );
            } while (isTooClose(candidate, occupied, minDistance) && ++attempts < 20);

            positions.add(candidate);
            occupied.add(candidate);
        }

        return positions.toArray(new Vector[0]);
    }

    private static boolean isTooClose(Vector candidate, List<Vector> occupied, double minDistance) {
        double minDistSq = minDistance * minDistance;
        return occupied.stream().anyMatch(vec -> vec.distanceSquared(candidate) < minDistSq);
    }

    public static Location[] getRibPositions(Location spineLoc, float rotation, int pairIndex) {
        double offset = 0.2 + (pairIndex * 0.1);
        return new Location[]{
                calculatePartPosition(spineLoc, rotation + 90, new Vector(offset, 0, 0)),
                calculatePartPosition(spineLoc, rotation - 90, new Vector(-offset, 0, 0))
        };
    }

    public static Location[] getArmPositions(Location spineLoc, float rotation) {
        return new Location[]{
                calculatePartPosition(spineLoc, rotation + 45, new Vector(0.3, 0, -0.1)),
                calculatePartPosition(spineLoc, rotation - 45, new Vector(-0.3, 0, -0.1))
        };
    }

    public static Location[] getLegPositions(Location spineLoc, float rotation) {
        return new Location[]{
                calculatePartPosition(spineLoc, rotation + 20, new Vector(0.2, 0, -0.3)),
                calculatePartPosition(spineLoc, rotation - 20, new Vector(-0.2, 0, -0.3))
        };
    }
}
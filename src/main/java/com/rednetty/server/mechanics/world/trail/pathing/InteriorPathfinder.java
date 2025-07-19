package com.rednetty.server.mechanics.world.trail.pathing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 *  interior pathfinder that properly handles building navigation.
 * This version improves on the original by relaxing some conditions, adding
 * an iteration limit, and refining the move-generation and heuristic.
 */
public class InteriorPathfinder {
    private final Plugin plugin;
    private final boolean DEBUG;

    // Constants for pathfinding costs and limits.
    private static final int MAX_ITERATIONS = 10000;
    private static final double BASE_MOVE_COST = 1.0;
    private static final double STAIRS_COST_MULTIPLIER = 2.0;
    private static final double VERTICAL_MOVE_COST_MULTIPLIER = 3.0;
    private static final double DOOR_COST_MULTIPLIER = 1.5;
    private static final double GOAL_THRESHOLD = 2.0; // within 2 blocks, we consider the goal reached

    // Horizontal movement directions (8 directions).
    private static final int[][] HORIZONTAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    // Material sets for navigation.
    private static final Set<Material> INTERIOR_FLOOR_MATERIALS = EnumSet.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.DARK_OAK_PLANKS, Material.STONE_BRICKS, Material.DEEPSLATE_TILES,
            Material.SMOOTH_STONE, Material.DEEPSLATE_BRICKS, Material.POLISHED_ANDESITE,
            Material.STONE, Material.COBBLESTONE
    );

    private static final Set<Material> STAIR_MATERIALS = EnumSet.of(
            Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS,
            Material.DARK_OAK_STAIRS, Material.STONE_BRICK_STAIRS, Material.DEEPSLATE_BRICK_STAIRS,
            Material.COBBLESTONE_STAIRS, Material.STONE_STAIRS, Material.ANDESITE_STAIRS
    );

    private static final Set<Material> DOOR_MATERIALS = EnumSet.of(
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.DARK_OAK_DOOR, Material.ACACIA_DOOR, Material.IRON_DOOR
    );

    private static final Set<Material> WALL_MATERIALS = EnumSet.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.DARK_OAK_PLANKS, Material.STONE_BRICKS, Material.DEEPSLATE_TILES,
            Material.SMOOTH_STONE, Material.DEEPSLATE_BRICKS, Material.STONE,
            Material.COBBLESTONE, Material.BRICKS
    );

    private static final Set<Material> NON_COLLIDABLE = EnumSet.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.LIGHT, Material.WATER, Material.GRASS_BLOCK
    );

    public InteriorPathfinder(Plugin plugin, boolean debug) {
        this.plugin = plugin;
        this.DEBUG = debug;
    }

    /**
     * Determines if a location is inside a building by checking for a valid floor,
     * a nearby ceiling, and at least one adjacent wall.
     */
    public boolean isInsideBuilding(Location loc) {
        World world = loc.getWorld();
        Block block = loc.getBlock();

        // Check for a valid floor (allow any solid block as a fallback)
        Block floorBlock = block.getRelative(BlockFace.DOWN);
        if (!INTERIOR_FLOOR_MATERIALS.contains(floorBlock.getType()) &&
                !STAIR_MATERIALS.contains(floorBlock.getType()) &&
                !floorBlock.getType().isSolid()) {
            return false;
        }

        // Look for a ceiling within a few blocks above.
        boolean foundCeiling = false;
        for (int y = 1; y <= 5; y++) {
            Block ceiling = block.getRelative(BlockFace.UP, y);
            if (WALL_MATERIALS.contains(ceiling.getType())) {
                foundCeiling = true;
                break;
            }
        }
        if (!foundCeiling) return false;

        // Check for walls on at least one side (relaxed from two to one)
        int wallCount = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block wallBlock = block.getRelative(face);
            if (WALL_MATERIALS.contains(wallBlock.getType())) {
                wallCount++;
            }
        }
        return wallCount >= 1;
    }

    /**
     * Finds a path that navigates through a building's interior using an A* algorithm.
     *
     * @param start the starting location (must be inside)
     * @param goal  the goal location (must be inside)
     * @return a list of locations representing the path or an empty list if no path is found
     */
    public List<Location> findInteriorPath(Location start, Location goal) {
        if (!isInsideBuilding(start) || !isInsideBuilding(goal)) {
            debug("Either start or goal is not inside a building.");
            return Collections.emptyList();
        }

        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<String, PathNode> visited = new HashMap<>();

        String startKey = getNodeKey(start);
        PathNode startNode = new PathNode(start, null, 0, heuristic(start, goal));
        openSet.add(startNode);
        visited.put(startKey, startNode);

        int iterations = 0;
        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            PathNode current = openSet.poll();

            if (current.location.distance(goal) < GOAL_THRESHOLD) {
                debug("Goal reached after " + iterations + " iterations.");
                return reconstructPath(current);
            }

            for (Location neighborLoc : getValidMoves(current.location)) {
                // Ensure the neighbor is inside the building
                if (!isInsideBuilding(neighborLoc))
                    continue;

                String neighborKey = getNodeKey(neighborLoc);
                double tentativeG = current.gScore + getMoveCost(current.location, neighborLoc);

                if (visited.containsKey(neighborKey) && tentativeG >= visited.get(neighborKey).gScore)
                    continue;

                PathNode neighborNode = new PathNode(neighborLoc, current, tentativeG, heuristic(neighborLoc, goal));
                visited.put(neighborKey, neighborNode);
                openSet.add(neighborNode);
            }
        }

        debug("Interior pathfinding failed after " + iterations + " iterations.");
        return Collections.emptyList();
    }

    /**
     * Generates valid moves from the given location.
     * This includes horizontal moves, vertical variations, and moves through doors.
     */
    private List<Location> getValidMoves(Location loc) {
        List<Location> moves = new ArrayList<>();
        World world = loc.getWorld();

        // Horizontal moves in 8 directions.
        for (int[] dir : HORIZONTAL_DIRECTIONS) {
            Location newLoc = loc.clone().add(dir[0], 0, dir[1]);
            if (isValidMove(newLoc)) {
                moves.add(newLoc);
            }
            // Also try moving diagonally with a vertical change.
            for (int dy = -1; dy <= 1; dy += 2) {
                Location newLocVertical = loc.clone().add(dir[0], dy, dir[1]);
                if (isValidMove(newLocVertical)) {
                    moves.add(newLocVertical);
                }
            }
        }

        // Pure vertical moves.
        for (int dy = -1; dy <= 1; dy += 2) {
            Location verticalLoc = loc.clone().add(0, dy, 0);
            if (isValidMove(verticalLoc)) {
                moves.add(verticalLoc);
            }
        }

        // Moves through doors.
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block doorBlock = loc.getBlock().getRelative(face);
            if (DOOR_MATERIALS.contains(doorBlock.getType())) {
                Location throughDoor = loc.clone().add(face.getModX() * 2, 0, face.getModZ() * 2);
                if (isValidMove(throughDoor)) {
                    moves.add(throughDoor);
                }
            }
        }

        return moves;
    }

    /**
     * Checks if the location is a valid move by ensuring both the feet and head space are free,
     * and that there is a valid floor below.
     */
    private boolean isValidMove(Location loc) {
        Block block = loc.getBlock();
        Block head = block.getRelative(BlockFace.UP);

        if (!NON_COLLIDABLE.contains(block.getType()) || !NON_COLLIDABLE.contains(head.getType())) {
            return false;
        }
        return hasValidFloor(loc);
    }

    /**
     * Determines if there is a valid floor at the location.
     */
    private boolean hasValidFloor(Location loc) {
        Block floor = loc.getBlock().getRelative(BlockFace.DOWN);
        return INTERIOR_FLOOR_MATERIALS.contains(floor.getType()) ||
                STAIR_MATERIALS.contains(floor.getType()) ||
                floor.getType().isSolid();
    }

    /**
     * Calculates the movement cost from one location to another.
     */
    private double getMoveCost(Location from, Location to) {
        double cost = from.distance(to) * BASE_MOVE_COST;
        double dy = Math.abs(from.getY() - to.getY());
        if (dy > 0) {
            Block floor = to.getBlock().getRelative(BlockFace.DOWN);
            if (STAIR_MATERIALS.contains(floor.getType())) {
                cost *= STAIRS_COST_MULTIPLIER;
            } else {
                cost *= VERTICAL_MOVE_COST_MULTIPLIER;
            }
        }
        Block toBlock = to.getBlock();
        if (DOOR_MATERIALS.contains(toBlock.getType())) {
            cost *= DOOR_COST_MULTIPLIER;
        }
        return cost;
    }

    /**
     * Heuristic for A* using Manhattan distance with extra weight for vertical differences.
     */
    private double heuristic(Location from, Location to) {
        double dx = Math.abs(from.getX() - to.getX());
        double dy = Math.abs(from.getY() - to.getY());
        double dz = Math.abs(from.getZ() - to.getZ());
        return dx + dz + (dy * 2);
    }

    /**
     * Reconstructs the path from the goal node back to the start.
     */
    private List<Location> reconstructPath(PathNode node) {
        List<Location> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node.location);
            node = node.parent;
        }
        return path;
    }

    /**
     * Returns a string key for a location based on its block coordinates.
     */
    private String getNodeKey(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * Logs a debug message if debugging is enabled.
     */
    private void debug(String message) {
        if (DEBUG) {
            plugin.getLogger().info("[InteriorPathfinder] " + message);
        }
    }

    /**
     * Represents a node in the A* search.
     */
    private static class PathNode implements Comparable<PathNode> {
        Location location;
        PathNode parent;
        double gScore;
        double fScore;

        PathNode(Location location, PathNode parent, double gScore, double hScore) {
            this.location = location;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = gScore + hScore;
        }

        @Override
        public int compareTo(PathNode other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }
}

package com.rednetty.server.core.mechanics.world.trail.pathing.nodes;

import com.rednetty.server.core.mechanics.world.trail.pathing.InteriorPathfinder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * A pathfinder that builds a region graph from navigation nodes and uses an A* algorithm
 * with vertical penalties to compute a smooth path between two locations.
 *
 * <p>This class does not change how nodes are selected, only enhances clarity, modularity,
 * and performance for a better end goal.</p>
 */
public class NavGraphPathfinder {
    private final List<NavNode> nodes;
    private final World world;
    private final Plugin plugin;
    private final InteriorPathfinder interiorPathfinder;

    // Enable or disable debug logging.
    private final boolean DEBUG = true;

    // Core settings
    private static final double NODE_SPACING = 2.0;
    private static final double SEARCH_RADIUS = 200.0;
    private static final double CONNECTION_RANGE = 10.0;

    // Cost settings
    private static final double ROAD_COST = 15.0;
    private static final double CHEAP_NODE_THRESHOLD = 250.0;
    private static final double EXPENSIVE_NODE_MULTIPLIER = 100.0;

    // Interior pathfinding settings
    private static final double MAX_INTERIOR_DISTANCE = 30.0;
    private static final double NODE_SEARCH_RADIUS = 15.0;
    private static final int MAX_NODE_SEARCH_ATTEMPTS = 5;

    // Smoothing and vertical penalty settings
    private static final double MAX_VERTICAL_STEP = 1.5;
    private static final int MAX_ADJUST_ATTEMPTS = 3;
    private static final double VERTICAL_DIFF_THRESHOLD = 2.5;
    private static final double VERTICAL_PENALTY_CONSTANT = 6.0;

    public NavGraphPathfinder(List<NavNode> nodes, World world, Plugin plugin) {
        this.nodes = nodes;
        this.world = world;
        this.plugin = plugin;
        this.interiorPathfinder = new InteriorPathfinder(plugin, true);
        debug("Initialized with " + nodes.size() + " nodes");
    }

    /**
     * Finds a path between two locations, prioritizing proper interior navigation
     * before attempting any node-based pathfinding.
     */
    public List<Location> findPath(Location start, Location goal) {
        if (start == null || goal == null) {
            debug("Start or goal is null!");
            return Collections.emptyList();
        }
        debug("Finding path from " + formatLoc(start) + " to " + formatLoc(goal));

        boolean startInside = interiorPathfinder.isInsideBuilding(start);
        boolean goalInside = interiorPathfinder.isInsideBuilding(goal);

        // Case 1: Both points are inside the same or nearby buildings
        if (startInside && goalInside && start.distance(goal) < MAX_INTERIOR_DISTANCE) {
            debug("Both points are inside - attempting direct interior path");
            List<Location> interiorPath = interiorPathfinder.findInteriorPath(start, goal);
            if (!interiorPath.isEmpty()) {
                debug("Found direct interior path");
                return interiorPath;
            }
        }

        // Case 2: Start is inside a building
        if (startInside) {
            debug("Start is inside - finding path to exit");
            List<Location> fullPath = new ArrayList<>();

            // First, find all potential exits within reasonable distance
            List<BuildingExit> exits = findBuildingExits(start, 32);
            if (exits.isEmpty()) {
                debug("No valid building exits found!");
                return Collections.emptyList();
            }

            // Try each exit until we find a valid complete path
            for (BuildingExit exit : exits) {
                // Get path to the exit
                List<Location> exitPath = interiorPathfinder.findInteriorPath(start, exit.location);
                if (exitPath.isEmpty()) {
                    continue;
                }

                // From the exit, try to find path to goal
                RegionGraph region = buildRegionGraph(exit.location, goal);
                RegionNode exitNode = findNearestInRegion(region, exit.location);
                RegionNode goalNode = findNearestInRegion(region, goal);

                if (exitNode != null && goalNode != null) {
                    List<RegionNode> nodePath = findRegionPath(region, exitNode, goalNode);
                    if (!nodePath.isEmpty()) {
                        fullPath.addAll(exitPath);
                        fullPath.addAll(buildFullPath(exit.location, goal, nodePath));
                        debug("Found valid path through exit: " + formatLoc(exit.location));
                        return smoothPath(fullPath);
                    }
                }
            }
            debug("No valid path found through any exit");
            return Collections.emptyList();
        }

        // Case 3: Goal is inside a building
        if (goalInside) {
            debug("Goal is inside - finding path from entrance");
            List<Location> fullPath = new ArrayList<>();

            // Find potential building entrances
            List<BuildingExit> entrances = findBuildingExits(goal, 32);
            if (entrances.isEmpty()) {
                debug("No valid building entrances found!");
                return Collections.emptyList();
            }

            // Try each entrance until we find a valid complete path
            for (BuildingExit entrance : entrances) {
                // First find path to the entrance
                RegionGraph region = buildRegionGraph(start, entrance.location);
                RegionNode startNode = findNearestInRegion(region, start);
                RegionNode entranceNode = findNearestInRegion(region, entrance.location);

                if (startNode != null && entranceNode != null) {
                    List<RegionNode> nodePath = findRegionPath(region, startNode, entranceNode);
                    if (!nodePath.isEmpty()) {
                        List<Location> approachPath = buildFullPath(start, entrance.location, nodePath);

                        // Then find path from entrance to goal
                        List<Location> interiorPath = interiorPathfinder.findInteriorPath(entrance.location, goal);
                        if (!interiorPath.isEmpty()) {
                            fullPath.addAll(approachPath);
                            fullPath.addAll(interiorPath);
                            debug("Found valid path through entrance: " + formatLoc(entrance.location));
                            return smoothPath(fullPath);
                        }
                    }
                }
            }
            debug("No valid path found through any entrance");
            return Collections.emptyList();
        }

        // Case 4: Both points are exterior - use regular node pathfinding
        debug("Both points are exterior - using regular pathfinding");
        RegionGraph region = buildRegionGraph(start, goal);
        if (region.nodes.isEmpty()) {
            debug("No nodes found in region!");
            return Collections.emptyList();
        }

        RegionNode startNode = findNearestInRegion(region, start);
        RegionNode goalNode = findNearestInRegion(region, goal);

        if (startNode == null || goalNode == null) {
            debug("Could not find start or goal nodes!");
            return Collections.emptyList();
        }

        List<RegionNode> nodePath = findRegionPath(region, startNode, goalNode);
        if (nodePath.isEmpty()) {
            debug("No path found through region!");
            return Collections.emptyList();
        }

        return buildFullPath(start, goal, nodePath);
    }

    /**
     * Handles pathfinding when one point is inside a building and one is outside.
     */
    private List<Location> handleMixedPath(Location start, Location goal, boolean startInside) {
        debug("Mixed path: " + (startInside ? "Start" : "Goal") + " is inside building");

        Location interiorPoint = startInside ? start : goal;
        Location exteriorPoint = startInside ? goal : start;

        // Find nearest accessible node to the interior point
        RegionNode accessNode = findNearestAccessibleNode(interiorPoint);
        if (accessNode == null) {
            debug("Could not find accessible node near interior point!");
            return Collections.emptyList();
        }

        Location nodeLocation = new Location(world,
                accessNode.originalNode.x,
                accessNode.originalNode.y,
                accessNode.originalNode.z);

        // Get interior portion of the path
        List<Location> interiorPath = interiorPathfinder.findInteriorPath(
                interiorPoint,
                nodeLocation
        );

        if (interiorPath.isEmpty()) {
            debug("Could not find interior path to nearest node!");
            return Collections.emptyList();
        }

        // Get exterior portion of the path
        List<Location> exteriorPath = findRegularPath(nodeLocation, exteriorPoint);
        if (exteriorPath.isEmpty()) {
            debug("Could not find exterior path!");
            return Collections.emptyList();
        }

        // Combine paths in the correct order
        List<Location> fullPath = new ArrayList<>();
        if (startInside) {
            fullPath.addAll(interiorPath);
            fullPath.addAll(exteriorPath);
        } else {
            fullPath.addAll(exteriorPath);
            fullPath.addAll(interiorPath);
        }

        return smoothPath(fullPath);
    }

    /**
     * Finds the nearest accessible node to a location that has a clear path.
     */
    private RegionNode findNearestAccessibleNode(Location location) {
        List<RegionNode> candidates = new ArrayList<>();
        double searchRadius = NODE_SEARCH_RADIUS;

        while (candidates.isEmpty() && searchRadius <= SEARCH_RADIUS) {
            RegionGraph region = buildRegionGraph(location, location.clone().add(searchRadius, 0, 0));

            for (RegionNode node : region.nodes) {
                Location nodeLoc = new Location(world,
                        node.originalNode.x,
                        node.originalNode.y,
                        node.originalNode.z);

                if (interiorPathfinder.findInteriorPath(location, nodeLoc).isEmpty()) {
                    continue;
                }

                double distSq = location.distanceSquared(nodeLoc);
                if (distSq <= searchRadius * searchRadius) {
                    candidates.add(node);
                }
            }

            searchRadius *= 2;
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Sort by combination of distance and node cost
        candidates.sort((a, b) -> {
            Location aLoc = new Location(world, a.originalNode.x, a.originalNode.y, a.originalNode.z);
            Location bLoc = new Location(world, b.originalNode.x, b.originalNode.y, b.originalNode.z);
            double aDist = location.distanceSquared(aLoc);
            double bDist = location.distanceSquared(bLoc);
            double aScore = aDist * (a.originalNode.cost / ROAD_COST);
            double bScore = bDist * (b.originalNode.cost / ROAD_COST);
            return Double.compare(aScore, bScore);
        });

        return candidates.get(0);
    }

    /**
     * Finds a regular path between two exterior points using the node network.
     */
    private List<Location> findRegularPath(Location start, Location goal) {
        RegionGraph region = buildRegionGraph(start, goal);
        if (region.nodes.isEmpty()) {
            debug("No nodes found in region!");
            return Collections.emptyList();
        }

        RegionNode startNode = findNearestInRegion(region, start);
        RegionNode goalNode = findNearestInRegion(region, goal);

        if (startNode == null || goalNode == null) {
            debug("Could not find start or goal nodes!");
            return Collections.emptyList();
        }

        List<RegionNode> nodePath = findRegionPath(region, startNode, goalNode);
        if (nodePath.isEmpty()) {
            debug("No path found through region!");
            return Collections.emptyList();
        }

        return buildFullPath(start, goal, nodePath);
    }

    /**
     * Builds the region graph (subset of nodes) and creates connections between nodes.
     */
    private RegionGraph buildRegionGraph(Location start, Location goal) {
        final Vector midpoint = start.toVector().add(goal.toVector()).multiply(0.5);
        final double regionRadius = Math.max(SEARCH_RADIUS, start.distance(goal) * 0.75);
        final double radiusSq = regionRadius * regionRadius;

        List<RegionNode> regionNodes = new ArrayList<>();
        for (NavNode node : nodes) {
            Vector nodeVec = new Vector(node.x, node.y, node.z);
            if (nodeVec.distanceSquared(midpoint) <= radiusSq) {
                regionNodes.add(new RegionNode(node, regionNodes.size()));
            }
        }

        // Build spatial grid for fast connection lookups.
        final int cellSize = (int) Math.ceil(CONNECTION_RANGE);
        Map<GridCoord, List<RegionNode>> grid = buildSpatialGrid(regionNodes, cellSize);

        // Connect nodes using the grid
        final double connectionRangeSq = CONNECTION_RANGE * CONNECTION_RANGE;
        for (RegionNode node : regionNodes) {
            int cellX = getCell(node.originalNode.x, cellSize);
            int cellZ = getCell(node.originalNode.z, cellSize);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    GridCoord neighborCoord = new GridCoord(cellX + dx, cellZ + dz);
                    List<RegionNode> cellNodes = grid.get(neighborCoord);
                    if (cellNodes == null) continue;
                    for (RegionNode other : cellNodes) {
                        if (node == other) continue;
                        if (squaredDistance(node, other) <= connectionRangeSq) {
                            double baseCost = calculateBaseCost(node, other);
                            if (node.originalNode.cost <= ROAD_COST || other.originalNode.cost <= ROAD_COST) {
                                baseCost *= 0.1;
                            }
                            node.connections.add(new NodeConnection(other, baseCost));
                        }
                    }
                }
            }
        }
        return new RegionGraph(regionNodes);
    }

    /**
     * Builds a spatial grid mapping GridCoord -> list of RegionNodes.
     */
    private Map<GridCoord, List<RegionNode>> buildSpatialGrid(List<RegionNode> regionNodes, int cellSize) {
        Map<GridCoord, List<RegionNode>> grid = new HashMap<>();
        for (RegionNode node : regionNodes) {
            int cellX = getCell(node.originalNode.x, cellSize);
            int cellZ = getCell(node.originalNode.z, cellSize);
            GridCoord coord = new GridCoord(cellX, cellZ);
            grid.computeIfAbsent(coord, k -> new ArrayList<>()).add(node);
        }
        return grid;
    }

    private int getCell(double coordinate, int cellSize) {
        return (int) Math.floor(coordinate / cellSize);
    }

    // --- A* Pathfinding with Vertical Penalty ---
    private List<RegionNode> findRegionPath(RegionGraph region, RegionNode start, RegionNode goal) {
        PriorityQueue<PathNode> openSet = new PriorityQueue<>();
        Map<RegionNode, RegionNode> cameFrom = new HashMap<>();
        Map<RegionNode, Double> gScore = new HashMap<>();
        Set<RegionNode> closedSet = new HashSet<>();

        gScore.put(start, 0.0);
        openSet.add(new PathNode(start, heuristic(start, goal)));

        while (!openSet.isEmpty()) {
            PathNode current = openSet.poll();
            if (current.node.equals(goal)) {
                return reconstructPath(cameFrom, start, goal);
            }
            closedSet.add(current.node);

            for (NodeConnection conn : current.node.connections) {
                if (closedSet.contains(conn.to)) continue;
                double tentativeG = gScore.get(current.node) + calculatePathCost(current.node, conn.to);
                if (tentativeG < gScore.getOrDefault(conn.to, Double.MAX_VALUE)) {
                    cameFrom.put(conn.to, current.node);
                    gScore.put(conn.to, tentativeG);
                    double fScore = tentativeG + heuristic(conn.to, goal);
                    openSet.add(new PathNode(conn.to, fScore));
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * Heuristic using weighted Euclidean distance plus an extra vertical penalty.
     */
    private double heuristic(RegionNode a, RegionNode b) {
        double dx = a.originalNode.x - b.originalNode.x;
        double dz = a.originalNode.z - b.originalNode.z;
        double dy = a.originalNode.y - b.originalNode.y;
        double baseHeuristic = Math.sqrt(dx * dx + dz * dz + (dy * dy) * 2);
        double absDy = Math.abs(dy);
        if (absDy > VERTICAL_DIFF_THRESHOLD) {
            baseHeuristic += (absDy - VERTICAL_DIFF_THRESHOLD) * VERTICAL_PENALTY_CONSTANT;
        }
        return baseHeuristic;
    }
    // --- End A* modifications ---

    /**
     * Computes the cost for moving from one node to another.
     */
    private double calculatePathCost(RegionNode from, RegionNode to) {
        double cost = to.originalNode.cost;
        if (cost > CHEAP_NODE_THRESHOLD) {
            cost *= EXPENSIVE_NODE_MULTIPLIER;
        }
        if (from.originalNode.cost <= ROAD_COST && to.originalNode.cost > ROAD_COST) {
            cost *= 1000;
        }
        double verticalDiff = Math.abs(from.originalNode.y - to.originalNode.y);
        if (verticalDiff > VERTICAL_DIFF_THRESHOLD) {
            cost += (verticalDiff - VERTICAL_DIFF_THRESHOLD) * VERTICAL_PENALTY_CONSTANT;
        }
        return cost;
    }

    /**
     * Builds the complete path by filling gaps between region nodes and smoothing the result.
     */
    private List<Location> buildFullPath(Location start, Location goal, List<RegionNode> nodePath) {
        List<Location> fullPath = new ArrayList<>();
        fullPath.add(start);
        for (int i = 0; i < nodePath.size() - 1; i++) {
            Location currentLoc = toLocation(nodePath.get(i));
            Location nextLoc = toLocation(nodePath.get(i + 1));
            fullPath.addAll(fillPathGap(currentLoc, nextLoc));
        }
        fullPath.add(goal);
        return smoothPath(fullPath);
    }

    private Location toLocation(RegionNode node) {
        return new Location(world, node.originalNode.x, node.originalNode.y, node.originalNode.z);
    }

    /**
     * Fills the gap between two locations by adding intermediate steps.
     * More steps are added if the vertical difference is high.
     */
    private List<Location> fillPathGap(Location from, Location to) {
        List<Location> points = new ArrayList<>();
        double distance = from.distance(to);
        double verticalDiff = Math.abs(from.getY() - to.getY());
        int horizontalSteps = (int) (distance / NODE_SPACING);
        int verticalSteps = (int) (verticalDiff / MAX_VERTICAL_STEP);
        int steps = Math.max(1, Math.max(horizontalSteps, verticalSteps));

        for (int i = 1; i < steps; i++) {
            double ratio = (double) i / steps;
            double x = from.getX() + (to.getX() - from.getX()) * ratio;
            double z = from.getZ() + (to.getZ() - from.getZ()) * ratio;
            double desiredY = from.getY() + (to.getY() - from.getY()) * ratio;
            double prevY = (points.isEmpty() ? from.getY() : points.get(points.size() - 1).getY());
            double deltaY = desiredY - prevY;
            if (Math.abs(deltaY) > MAX_VERTICAL_STEP) {
                desiredY = prevY + Math.signum(deltaY) * MAX_VERTICAL_STEP;
            }
            points.add(new Location(world, x, desiredY, z));
        }
        return points;
    }

    /**
     * Smooths a path by averaging each intermediate point with its neighbors,
     * while clamping vertical differences.
     */
    /**
     *  path smoothing that maintains building entry/exit points.
     */
    private List<Location> smoothPath(List<Location> path) {
        if (path.size() <= 2) return path;

        List<Location> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));

        for (int i = 1; i < path.size() - 1; i++) {
            Location prev = path.get(i - 1);
            Location curr = path.get(i);
            Location next = path.get(i + 1);

            // Don't smooth points where interior/exterior transition occurs
            boolean currInside = interiorPathfinder.isInsideBuilding(curr);
            boolean nextInside = interiorPathfinder.isInsideBuilding(next);

            if (currInside != nextInside) {
                smoothed.add(curr);
                continue;
            }

            // Regular smoothing for other points
            double x = (prev.getX() + curr.getX() + next.getX()) / 3;
            double z = (prev.getZ() + curr.getZ() + next.getZ()) / 3;
            double desiredY = (prev.getY() + curr.getY() + next.getY()) / 3;

            // Maintain vertical constraints
            double lastY = smoothed.get(smoothed.size() - 1).getY();
            double deltaY = desiredY - lastY;
            if (Math.abs(deltaY) > MAX_VERTICAL_STEP) {
                desiredY = lastY + Math.signum(deltaY) * MAX_VERTICAL_STEP;
            }

            smoothed.add(new Location(world, x, desiredY, z));
        }

        smoothed.add(path.get(path.size() - 1));
        return smoothed;
    }

    /**
     * Finds the closest region node to the given location.
     * If a node has a cost lower than ROAD_COST, its distance is weighted more favorably.
     */
    private RegionNode findNearestInRegion(RegionGraph region, Location loc) {
        RegionNode nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        double x = loc.getX();
        double z = loc.getZ();
        for (RegionNode node : region.nodes) {
            double dx = node.originalNode.x - x;
            double dz = node.originalNode.z - z;
            double distSq = dx * dx + dz * dz;
            if (node.originalNode.cost <= ROAD_COST) {
                distSq *= 0.25;
            }
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = node;
            }
        }
        return nearest;
    }

    private double squaredDistance(RegionNode a, RegionNode b) {
        double dx = a.originalNode.x - b.originalNode.x;
        double dy = a.originalNode.y - b.originalNode.y;
        double dz = a.originalNode.z - b.originalNode.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private double calculateBaseCost(RegionNode a, RegionNode b) {
        return Math.max(a.originalNode.cost, b.originalNode.cost);
    }

    /**
     * Finds all valid building exits within the specified range of the given location
     */
    private List<BuildingExit> findBuildingExits(Location start, int maxRange) {
        List<BuildingExit> exits = new ArrayList<>();
        World world = start.getWorld();
        int startX = start.getBlockX();
        int startY = start.getBlockY();
        int startZ = start.getBlockZ();

        // Search in expanding squares
        for (int range = 1; range <= maxRange; range++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    // Only check the outer shell of each square
                    if (Math.abs(x) != range && Math.abs(z) != range) continue;

                    // Check different vertical levels
                    for (int y = -2; y <= 2; y++) {
                        Location testLoc = new Location(world, startX + x, startY + y, startZ + z);

                        if (isValidExit(testLoc)) {
                            // Measure clearance and check approach
                            double clearance = measureExitClearance(testLoc);
                            boolean hasApproach = checkExitApproach(testLoc);

                            if (clearance > 2.0 && hasApproach) {
                                exits.add(new BuildingExit(testLoc, clearance, true));
                            }
                        }
                    }
                }
            }

            // If we found some good exits, no need to keep searching further
            if (!exits.isEmpty()) {
                // Sort by a combination of distance and clearance
                exits.sort((a, b) -> {
                    double aScore = a.location.distance(start) / a.clearance;
                    double bScore = b.location.distance(start) / b.clearance;
                    return Double.compare(aScore, bScore);
                });
                // Return the best few exits
                return exits.subList(0, Math.min(exits.size(), 3));
            }
        }

        return exits;
    }

    /**
     * Checks if a location is a valid building exit
     */
    private boolean isValidExit(Location loc) {
        // Must have head room
        if (!isPassable(loc) || !isPassable(loc.clone().add(0, 1, 0))) {
            return false;
        }

        // Must have solid ground
        if (!loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
            return false;
        }

        // Must be transitioning from interior to exterior
        boolean locInside = interiorPathfinder.isInsideBuilding(loc);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Location adjacent = loc.clone().add(face.getModX(), 0, face.getModZ());
            boolean adjacentInside = interiorPathfinder.isInsideBuilding(adjacent);

            if (locInside != adjacentInside) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a block is passable (can be walked through)
     */
    private boolean isPassable(Location loc) {
        Material type = loc.getBlock().getType();
        return type == Material.AIR || type == Material.CAVE_AIR ||
                type == Material.VOID_AIR || type == Material.LIGHT;
    }

    /**
     * Measures how much clear space is around an exit
     */
    private double measureExitClearance(Location exit) {
        double clearance = 0;
        World world = exit.getWorld();
        int ex = exit.getBlockX();
        int ey = exit.getBlockY();
        int ez = exit.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;

                Location testLoc = new Location(world, ex + x, ey, ez + z);
                if (isPassable(testLoc) && isPassable(testLoc.clone().add(0, 1, 0))) {
                    clearance += 1.0 / Math.sqrt(x * x + z * z);
                }
            }
        }

        return clearance;
    }

    /**
     * Verifies that the exit can be approached from the exterior
     */
    private boolean checkExitApproach(Location exit) {
        // Find which side is exterior
        BlockFace exteriorFace = null;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Location test = exit.clone().add(face.getModX(), 0, face.getModZ());
            if (!interiorPathfinder.isInsideBuilding(test)) {
                exteriorFace = face;
                break;
            }
        }

        if (exteriorFace == null) return false;

        // Check if we have a clear approach path
        Location approach = exit.clone().add(
                exteriorFace.getModX() * 2,
                0,
                exteriorFace.getModZ() * 2
        );

        return isPassable(approach) && isPassable(approach.clone().add(0, 1, 0)) &&
                approach.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }

    /**
     * Reconstructs the path from the A* cameFrom map.
     */
    private List<RegionNode> reconstructPath(Map<RegionNode, RegionNode> cameFrom,
                                             RegionNode start, RegionNode goal) {
        List<RegionNode> path = new ArrayList<>();
        RegionNode current = goal;
        while (current != null) {
            path.add(current);
            current = cameFrom.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    private void debug(String message) {
        if (DEBUG) {
            plugin.getLogger().info("[PathDebug] " + message);
        }
    }

    private String formatLoc(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private String formatNode(RegionNode node) {
        return String.format("Node(x=%d, y=%d, z=%d, cost=%.1f)",
                node.originalNode.x, node.originalNode.y, node.originalNode.z, node.originalNode.cost);
    }

    // --- Inner Classes ---
    private static class RegionGraph {
        final List<RegionNode> nodes;

        RegionGraph(List<RegionNode> nodes) {
            this.nodes = nodes;
        }
    }

    private static class RegionNode {
        final NavNode originalNode;
        final int index;
        final List<NodeConnection> connections;

        RegionNode(NavNode original, int index) {
            this.originalNode = original;
            this.index = index;
            this.connections = new ArrayList<>();
        }
    }

    private static class NodeConnection {
        final RegionNode to;
        final double cost;

        NodeConnection(RegionNode to, double cost) {
            this.to = to;
            this.cost = cost;
        }
    }

    /**
     * Helper class to represent building exits/entrances with their properties
     */
    private static class BuildingExit {
        Location location;
        double clearance;  // How much open space around the exit
        boolean hasValidApproach;  // Whether there's a clear path to/from the exit

        BuildingExit(Location location, double clearance, boolean hasValidApproach) {
            this.location = location;
            this.clearance = clearance;
            this.hasValidApproach = hasValidApproach;
        }
    }

    private static class PathNode implements Comparable<PathNode> {
        final RegionNode node;
        final double cost;

        PathNode(RegionNode node, double cost) {
            this.node = node;
            this.cost = cost;
        }

        @Override
        public int compareTo(PathNode other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    private static class GridCoord {
        final int x;
        final int z;

        GridCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GridCoord)) return false;
            GridCoord that = (GridCoord) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
}

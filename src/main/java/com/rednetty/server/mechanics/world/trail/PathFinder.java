package com.rednetty.server.mechanics.world.trail;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.rednetty.server.YakRealms;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PathFinder {
    private static final int CHUNK_SIZE = 16;
    private static final int MAX_ITERATIONS = 8000;
    private static final double MAX_STEP_HEIGHT = 1.0;
    private static final double MAX_DROP_HEIGHT = 3.0;
    private static final int TERRAIN_SCAN_RADIUS = 8;
    private static final double VALLEY_WEIGHT = 0.4;  // Lower cost for valleys
    private static final double HILL_PENALTY = 2.5;   // Higher cost for hills
    private static final int MAX_GROUND_SEARCH_RADIUS = 8;
    private static final int MAX_HEIGHT_DIFFERENCE = 30;
    private static final int RETRY_ATTEMPTS = 3;

    private final YakRealms plugin;
    private final WorldAnalyzer worldAnalyzer;
    private final Cache<ChunkKey, TerrainData> terrainCache;
    private final ExecutorService executor;

    public PathFinder(YakRealms plugin) {
        this.plugin = plugin;
        this.worldAnalyzer = new WorldAnalyzer(plugin);
        this.executor = Executors.newWorkStealingPool();
        this.terrainCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }

    private static class TerrainData {
        final BitSet passable;
        final int[] heights;
        final TerrainType[] types;
        final long timestamp;

        TerrainData(BitSet passable, int[] heights, TerrainType[] types) {
            this.passable = passable;
            this.heights = heights;
            this.types = types;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private enum TerrainType {
        VALLEY(0.4),    // Significantly lower than surroundings
        HILL(2.5),      // Significantly higher than surroundings
        SLOPE(1.5),     // Gradual elevation change
        FLAT(1.0);      // Level terrain

        final double movementCost;

        TerrainType(double cost) {
            this.movementCost = cost;
        }
    }

    private int findSurfaceHeight(Chunk chunk, int x, int z) {
        World world = chunk.getWorld();
        int bx = (chunk.getX() << 4) + x;
        int bz = (chunk.getZ() << 4) + z;

        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();

        // Start from top and work down
        for (int y = maxY - 1; y >= minY; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            Block above = world.getBlockAt(bx, y + 1, bz);
            Block below = y > minY ? world.getBlockAt(bx, y - 1, bz) : null;

            // Check if this is a valid surface block
            if (isValidSurface(block, above, below)) {
                return y;
            }
        }

        return minY - 1; // No valid surface found
    }

    private boolean isValidSurface(Block block, Block above, Block below) {
        // Must have air or passable block above
        if (!worldAnalyzer.isPassable(above)) {
            return false;
        }

        // Must be able to stand on this block
        if (!worldAnalyzer.canStandOn(block)) {
            return false;
        }

        // Special cases for certain block types
        Material type = block.getType();

        // Handle stairs and slabs properly
        if (type.name().endsWith("_STAIRS") || type.name().endsWith("_SLAB")) {
            return true;
        }

        // Check for dangerous blocks
        if (worldAnalyzer.isDangerous(block)) {
            return false;
        }

        // For regular blocks, ensure there's solid ground below
        if (below != null && below.getType().isAir()) {
            return false;
        }

        return true;
    }

    private TerrainData analyzeChunkTerrain(Chunk chunk) {
        BitSet passable = new BitSet(CHUNK_SIZE * CHUNK_SIZE);
        int[] heights = new int[CHUNK_SIZE * CHUNK_SIZE];
        TerrainType[] types = new TerrainType[CHUNK_SIZE * CHUNK_SIZE];

        // First pass: Get heights
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int height = findSurfaceHeight(chunk, x, z);
                heights[x * CHUNK_SIZE + z] = height;
                if (height > chunk.getWorld().getMinHeight()) {
                    passable.set(x * CHUNK_SIZE + z);
                }
            }
        }

        // Second pass: Analyze terrain types
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                types[x * CHUNK_SIZE + z] = analyzeTerrainType(
                        chunk, x, z, heights
                );
            }
        }

        return new TerrainData(passable, heights, types);
    }

    public Location findValidStart(Location playerLoc) {
        // First try exact position
        Location groundPos = getGroundPosition(playerLoc);
        if (isValidPathingPosition(groundPos)) {
            plugin.getLogger().info("Found valid start at player position: " + formatLoc(groundPos));
            return groundPos;
        }

        // Search in expanding radius
        for (int radius = 1; radius <= MAX_GROUND_SEARCH_RADIUS; radius++) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                double x = playerLoc.getX() + radius * Math.cos(rad);
                double z = playerLoc.getZ() + radius * Math.sin(rad);

                Location checkLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
                groundPos = getGroundPosition(checkLoc);

                if (isValidPathingPosition(groundPos)) {
                    plugin.getLogger().info("Found valid start near player: " + formatLoc(groundPos));
                    return groundPos;
                }
            }
        }

        plugin.getLogger().warning("Could not find valid start position near " + formatLoc(playerLoc));
        return null;
    }

    public Location findValidDestination(Location targetLoc) {
        // Try finding ground at exact target first
        Location groundPos = getGroundPosition(targetLoc);
        if (isValidPathingPosition(groundPos)) {
            plugin.getLogger().info("Found valid destination at target: " + formatLoc(groundPos));
            return groundPos;
        }

        // Search in expanding radius
        int maxRadius = MAX_GROUND_SEARCH_RADIUS * 2; // Larger radius for destination
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int angle = 0; angle < 360; angle += 45) {
                double rad = Math.toRadians(angle);
                double x = targetLoc.getX() + radius * Math.cos(rad);
                double z = targetLoc.getZ() + radius * Math.sin(rad);

                Location checkLoc = new Location(targetLoc.getWorld(), x, targetLoc.getY(), z);
                groundPos = getGroundPosition(checkLoc);

                if (isValidPathingPosition(groundPos)) {
                    plugin.getLogger().info("Found valid destination near target: " + formatLoc(groundPos));
                    return groundPos;
                }
            }
        }

        plugin.getLogger().warning("Could not find valid destination near " + formatLoc(targetLoc));
        return null;
    }

    private Location getGroundPosition(Location loc) {
        if (loc == null) return null;
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        // Get world height limits
        int maxY = Math.min(loc.getBlockY() + MAX_HEIGHT_DIFFERENCE, world.getMaxHeight() - 2);
        int minY = Math.max(loc.getBlockY() - MAX_HEIGHT_DIFFERENCE, world.getMinHeight());

        // Search down from current position
        for (int y = maxY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (isValidGroundBlock(block, above, above2)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }

        // If nothing found, try searching from max height
        for (int y = world.getMaxHeight() - 2; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);

            if (isValidGroundBlock(block, above, above2)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }

        return null;
    }

    private boolean isValidGroundBlock(Block ground, Block above, Block above2) {
        return worldAnalyzer.canStandOn(ground) &&
                worldAnalyzer.isPassable(above) &&
                worldAnalyzer.isPassable(above2) &&
                !worldAnalyzer.isDangerous(ground) &&
                !worldAnalyzer.isDangerous(above);
    }

    private boolean isValidPathingPosition(Location loc) {
        if (loc == null) return false;

        // Check if chunk is loaded
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load(true);
            if (!loc.getChunk().isLoaded()) {
                plugin.getLogger().warning("Failed to load chunk at " + formatLoc(loc));
                return false;
            }
        }

        // Verify block states
        Block ground = loc.getBlock().getRelative(0, -1, 0);
        Block feet = loc.getBlock();
        Block head = loc.getBlock().getRelative(0, 1, 0);

        boolean valid = worldAnalyzer.canStandOn(ground) &&
                worldAnalyzer.isPassable(feet) &&
                worldAnalyzer.isPassable(head) &&
                !worldAnalyzer.isDangerous(ground) &&
                !worldAnalyzer.isDangerous(feet);

        if (!valid) {
            plugin.getLogger().fine("Invalid position at " + formatLoc(loc) +
                    " - Ground: " + ground.getType() +
                    " Feet: " + feet.getType() +
                    " Head: " + head.getType());
        }

        return valid;
    }

    private String formatLoc(Location loc) {
        return String.format("(%.2f, %.2f, %.2f)",
                loc.getX(), loc.getY(), loc.getZ());
    }

    private TerrainType analyzeTerrainType(Chunk chunk, int x, int z, int[] heights) {
        int centerHeight = heights[x * CHUNK_SIZE + z];
        int totalHeight = 0;
        int count = 0;
        int maxDiff = 0;

        // Analyze surrounding terrain
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int nx = x + dx;
                int nz = z + dz;

                if (nx < 0 || nx >= CHUNK_SIZE || nz < 0 || nz >= CHUNK_SIZE) {
                    continue;
                }

                int height = heights[nx * CHUNK_SIZE + nz];
                totalHeight += height;
                count++;
                maxDiff = Math.max(maxDiff, Math.abs(height - centerHeight));
            }
        }

        double avgHeight = totalHeight / (double) count;
        double heightDiff = centerHeight - avgHeight;

        if (maxDiff <= 1) {
            return TerrainType.FLAT;
        } else if (heightDiff <= -3 && isValleyShape(chunk, x, z, heights)) {
            return TerrainType.VALLEY;
        } else if (heightDiff >= 3) {
            return TerrainType.HILL;
        } else {
            return TerrainType.SLOPE;
        }
    }

    private boolean isValleyShape(Chunk chunk, int x, int z, int[] heights) {
        int centerHeight = heights[x * CHUNK_SIZE + z];
        int higherCount = 0;

        // Check if surrounded by higher terrain
        for (int radius = 1; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        int nx = x + dx;
                        int nz = z + dz;

                        if (nx >= 0 && nx < CHUNK_SIZE && nz >= 0 && nz < CHUNK_SIZE) {
                            if (heights[nx * CHUNK_SIZE + nz] > centerHeight + 2) {
                                higherCount++;
                            }
                        }
                    }
                }
            }
        }

        return higherCount >= 6; // At least half of the perimeter points are higher
    }

    public CompletableFuture<List<Location>> findPath(Location start, Location end) {
        return CompletableFuture.supplyAsync(() -> {
            PriorityQueue<PathNode> openSet = new PriorityQueue<>(
                    Comparator.comparingDouble(n -> n.fCost)
            );
            Map<Long, PathNode> nodeMap = new HashMap<>();
            Set<Long> closedSet = new HashSet<>();

            // Initialize terrain analysis for start and end chunks
            TerrainData startTerrain = getTerrainData(start.getChunk());
            TerrainData endTerrain = getTerrainData(end.getChunk());

            // Create starting node
            PathNode startNode = new PathNode(start, null);
            startNode.gCost = 0;
            startNode.fCost = heuristic(start, end);

            openSet.add(startNode);
            nodeMap.put(encodePosition(start), startNode);

            int iterations = 0;
            Vector[] directions = generateAdaptiveDirections(start, end);

            while (!openSet.isEmpty() && iterations++ < MAX_ITERATIONS) {
                PathNode current = openSet.poll();

                if (current.loc.distanceSquared(end) < 4) {
                    return smoothPath(reconstructPath(current));
                }

                long pos = encodePosition(current.loc);
                closedSet.add(pos);

                // Get terrain data for current position
                TerrainData terrain = getTerrainData(current.loc.getChunk());
                int cx = current.loc.getBlockX() & 15;
                int cz = current.loc.getBlockZ() & 15;
                TerrainType currentType = terrain.types[cx * CHUNK_SIZE + cz];

                // Adjust search pattern based on terrain
                Vector[] searchDirections = adaptDirectionsToTerrain(
                        directions, currentType, current.loc, end
                );

                for (Vector dir : searchDirections) {
                    Location nextLoc = current.loc.clone().add(dir);
                    TerrainData nextTerrain = getTerrainData(nextLoc.getChunk());

                    int nx = nextLoc.getBlockX() & 15;
                    int nz = nextLoc.getBlockZ() & 15;
                    int idx = nx * CHUNK_SIZE + nz;

                    if (!nextTerrain.passable.get(idx)) continue;

                    int height = nextTerrain.heights[idx];
                    nextLoc.setY(height);

                    if (!isValidMove(current.loc, nextLoc)) continue;

                    long nextPos = encodePosition(nextLoc);
                    if (closedSet.contains(nextPos)) continue;

                    // Calculate movement cost considering terrain types
                    double moveCost = calculateTerrainAwareMovementCost(
                            current.loc, nextLoc,
                            currentType,
                            nextTerrain.types[idx]
                    );

                    double newG = current.gCost + moveCost;
                    PathNode neighbor = nodeMap.get(nextPos);

                    if (neighbor == null) {
                        neighbor = new PathNode(nextLoc, current);
                        neighbor.gCost = newG;
                        neighbor.fCost = newG + heuristic(nextLoc, end);
                        nodeMap.put(nextPos, neighbor);
                        openSet.add(neighbor);
                    } else if (newG < neighbor.gCost) {
                        neighbor.parent = current;
                        neighbor.gCost = newG;
                        neighbor.fCost = newG + heuristic(nextLoc, end);

                        if (!openSet.contains(neighbor)) {
                            openSet.add(neighbor);
                        }
                    }
                }
            }

            return null;
        }, executor);
    }

    private Vector[] adaptDirectionsToTerrain(Vector[] baseDirections, TerrainType terrain, Location current, Location goal) {
        Vector toGoal = goal.toVector().subtract(current.toVector()).normalize();
        List<Vector> adapted = new ArrayList<>();

        switch (terrain) {
            case VALLEY:
                // In valleys, prioritize following the valley direction
                Vector valleyDir = analyzeValleyDirection(current);
                if (valleyDir != null) {
                    adapted.add(valleyDir);
                    adapted.add(valleyDir.clone().multiply(-1));
                }
                break;

            case HILL:
                // On hills, prioritize directions that lead downward
                for (Vector dir : baseDirections) {
                    Location check = current.clone().add(dir);
                    if (check.getY() <= current.getY()) {
                        adapted.add(dir);
                    }
                }
                break;

            case SLOPE:
                // On slopes, prioritize diagonal movements
                for (Vector dir : baseDirections) {
                    if (Math.abs(dir.getX()) == Math.abs(dir.getZ())) {
                        adapted.add(dir);
                    }
                }
                break;

            default:
                // For flat terrain, prioritize direction toward goal
                adapted.add(toGoal);
        }

        // Add remaining directions with lower priority
        for (Vector dir : baseDirections) {
            if (!adapted.contains(dir)) {
                adapted.add(dir);
            }
        }

        return adapted.toArray(new Vector[0]);
    }

    private Vector analyzeValleyDirection(Location loc) {
        int lowestDiff = Integer.MAX_VALUE;
        Vector valleyDir = null;

        // Check in cardinal directions
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            Vector dir = new Vector(Math.cos(rad), 0, Math.sin(rad));
            int heightDiff = 0;

            // Sample heights perpendicular to direction
            for (int d = 1; d <= 3; d++) {
                Vector perpDir = dir.clone().rotateAroundY(Math.PI / 2);
                Location pos1 = loc.clone().add(perpDir.clone().multiply(d));
                Location pos2 = loc.clone().add(perpDir.clone().multiply(-d));

                heightDiff += getHeight(pos1) - loc.getY();
                heightDiff += getHeight(pos2) - loc.getY();
            }

            if (heightDiff < lowestDiff) {
                lowestDiff = heightDiff;
                valleyDir = dir;
            }
        }

        return lowestDiff < -4 ? valleyDir : null;
    }

    private double calculateTerrainAwareMovementCost(Location from, Location to,
                                                     TerrainType fromType, TerrainType toType) {
        double base = from.distance(to);
        double heightDiff = to.getY() - from.getY();

        // Basic height cost
        double heightCost = Math.abs(heightDiff) * (heightDiff > 0 ? 1.5 : 0.8);

        // Terrain transition costs
        double terrainCost = 1.0;

        // Prefer staying in valleys
        if (fromType == TerrainType.VALLEY && toType == TerrainType.VALLEY) {
            terrainCost *= VALLEY_WEIGHT;
        }
        // Penalize climbing hills
        else if (toType == TerrainType.HILL) {
            terrainCost *= HILL_PENALTY;
        }
        // Slight penalty for terrain changes
        else if (fromType != toType) {
            terrainCost *= 1.2;
        }

        return (base + heightCost) * terrainCost;
    }

    private List<Location> smoothPath(List<Location> path) {
        if (path.size() <= 2) return path;

        List<Location> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));

        int current = 0;
        while (current < path.size() - 1) {
            int furthest = current + 1;
            Location start = path.get(current);

            // Look ahead for line-of-sight shortcuts
            for (int i = current + 2; i < path.size(); i++) {
                Location end = path.get(i);
                if (hasLineOfSight(start, end) &&
                        isValidMove(start, end)) {
                    furthest = i;
                }
            }

            smoothed.add(path.get(furthest));
            current = furthest;
        }

        return smoothed;
    }

    private boolean hasLineOfSight(Location start, Location end) {
        Vector dir = end.toVector().subtract(start.toVector());
        double distance = dir.length();
        dir.normalize();

        for (double d = 0; d < distance; d += 0.5) {
            Location check = start.clone().add(dir.clone().multiply(d));
            if (!worldAnalyzer.isPassable(check.getBlock()) ||
                    worldAnalyzer.isDangerous(check.getBlock())) {
                return false;
            }
        }
        return true;
    }

    private int getHeight(Location loc) {
        TerrainData terrain = getTerrainData(loc.getChunk());
        int x = loc.getBlockX() & 15;
        int z = loc.getBlockZ() & 15;
        return terrain.heights[x * CHUNK_SIZE + z];
    }

    private Vector[] generateAdaptiveDirections(Location start, Location end) {
        Vector toEnd = end.toVector().subtract(start.toVector()).normalize();
        List<Vector> directions = new ArrayList<>();

        // Add primary direction toward goal
        directions.add(toEnd);

        // Add variations of the primary direction
        directions.add(toEnd.clone().rotateAroundY(Math.PI / 6));  // 30 degrees
        directions.add(toEnd.clone().rotateAroundY(-Math.PI / 6));

        // Add cardinal directions
        directions.add(new Vector(1, 0, 0));
        directions.add(new Vector(-1, 0, 0));
        directions.add(new Vector(0, 0, 1));
        directions.add(new Vector(0, 0, -1));

        // Add diagonal directions
        directions.add(new Vector(1, 0, 1).normalize());
        directions.add(new Vector(-1, 0, 1).normalize());
        directions.add(new Vector(1, 0, -1).normalize());
        directions.add(new Vector(-1, 0, -1).normalize());

        return directions.toArray(new Vector[0]);
    }

    private boolean isValidMove(Location from, Location to) {
        double dy = to.getY() - from.getY();
        if (dy > MAX_STEP_HEIGHT || dy < -MAX_DROP_HEIGHT) return false;

        // Check for dangerous blocks along the path
        Vector dir = to.toVector().subtract(from.toVector());
        double distance = dir.length();
        dir.normalize();

        for (double d = 0; d < distance; d += 0.5) {
            Location check = from.clone().add(dir.clone().multiply(d));
            if (worldAnalyzer.isDangerous(check.getBlock())) {
                return false;
            }
        }

        // Verify landing position
        return worldAnalyzer.canStandOn(to.getBlock().getRelative(0, -1, 0)) &&
                worldAnalyzer.isPassable(to.getBlock()) &&
                worldAnalyzer.isPassable(to.getBlock().getRelative(0, 1, 0));
    }

    private TerrainData getTerrainData(Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk.getBlock(0, 0, 0).getLocation());
        TerrainData data = terrainCache.getIfPresent(key);

        if (data == null || System.currentTimeMillis() - data.timestamp > 30000) {
            data = analyzeChunkTerrain(chunk);
            terrainCache.put(key, data);
        }

        return data;
    }

    private static class ChunkKey {
        final int x, z;
        final World world;

        ChunkKey(Location loc) {
            this.x = loc.getBlockX() >> 4;
            this.z = loc.getBlockZ() >> 4;
            this.world = loc.getWorld();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey)) return false;
            ChunkKey key = (ChunkKey) o;
            return x == key.x && z == key.z && world.equals(key.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z, world);
        }
    }

    private static class PathNode {
        Location loc;
        PathNode parent;
        double gCost;
        double fCost;

        PathNode(Location loc, PathNode parent) {
            this.loc = loc;
            this.parent = parent;
        }
    }

    private static long encodePosition(Location loc) {
        return ((long) loc.getBlockX() & 0x3FFFFFF) << 38 |
                ((long) loc.getBlockY() & 0xFFF) << 26 |
                ((long) loc.getBlockZ() & 0x3FFFFFF);
    }

    private List<Location> reconstructPath(PathNode end) {
        List<Location> path = new ArrayList<>();
        PathNode current = end;

        while (current != null) {
            path.add(0, current.loc.clone());
            current = current.parent;
        }

        return path;
    }

    private double heuristic(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();

        // Basic distance
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Height difference penalty
        double heightDiff = Math.abs(dy);
        double heightPenalty = heightDiff > 3 ? heightDiff * 2 : heightDiff;

        return distance + heightPenalty;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
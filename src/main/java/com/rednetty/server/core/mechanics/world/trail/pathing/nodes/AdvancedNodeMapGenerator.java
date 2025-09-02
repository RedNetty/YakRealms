package com.rednetty.server.core.mechanics.world.trail.pathing.nodes;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.io.*;
import java.util.*;

/**
 * AdvancedNodeMapGenerator precomputes a sparse 3D navigation graph for Minecraft.
 * <p>
 * This  version scans each (x,z) column for candidate floor levels (e.g. in buildings,
 * city streets, forests, etc.) rather than only using the top “ground” block.
 */
public class AdvancedNodeMapGenerator {
    // Node spacing: a node is placed every NODE_SPACING blocks (horizontally)
    public static final int NODE_SPACING = 2;

    // Preferred and penalized material sets
    private static final Set<Material> PREFERRED_MATERIALS = EnumSet.of(Material.DIRT_PATH, Material.SAND, Material.GRASS_BLOCK, Material.COARSE_DIRT);
    // BUILT_PATH_MATERIALS now includes COARSE_DIRT as well.
    private static final Set<Material> BUILT_PATH_MATERIALS = EnumSet.of(Material.ROOTED_DIRT, Material.DIRT, Material.COARSE_DIRT);
    private static final Set<Material> HAZARD_MATERIALS = EnumSet.of(Material.LAVA, Material.WATER, Material.CACTUS, Material.FIRE, Material.MAGMA_BLOCK);
    private static final Set<Material> SLOW_MATERIALS = EnumSet.of(Material.SOUL_SAND, Material.COBWEB, Material.SWEET_BERRY_BUSH);
    private static final Set<Material> VEGETATION_MATERIALS = EnumSet.of(
            Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN, Material.SUNFLOWER,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.VINE, Material.OAK_LEAVES,
            Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES);
    private static final Set<Material> PLAYER_PATH_MATERIALS = EnumSet.of(
            Material.STONE_BRICKS, Material.BRICKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS,
            Material.DEEPSLATE_BRICKS, Material.NETHER_BRICKS);

    // Tree-related materials to avoid when determining ground level
    private static final Set<Material> TREE_MATERIALS = EnumSet.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES);

    private static final double BUILT_PATH_COST = 1.0;
    private static final double PLAYER_PATH_BASE_COST = 15.0;

    // Slope and variance analysis parameters
    private static final double SLOPE_THRESHOLD = 1.0;
    private static final int SLOPE_SAMPLE_RADIUS = 3;
    private static final int VARIANCE_SAMPLE_RADIUS = 4;

    // Light penalty (if too dark)
    private static final int DARK_LIGHT_THRESHOLD = 8;

    // Water/hazard vicinity parameters
    private static final int WATER_SAMPLE_RADIUS = 2;
    private static final double WATER_THRESHOLD = 0.15;
    private static final double WATER_EXPONENT_MULTIPLIER = 4.0;

    // Player path vicinity bonus sample radius
    private static final int PLAYER_PATH_SAMPLE_RADIUS = 2;
    private static final double PLAYER_PATH_BONUS = 0.5;

    // Slow penalty multiplier
    private static final double SLOW_MULTIPLIER = 3.0;

    // Vegetation penalty multiplier
    private static final double VEGETATION_PENALTY = 50;

    public List<Integer> getCandidateFloors(World world, int x, int z) {
        return getCandidateFloorYs(world, x, z); // Assuming getCandidateFloorYs is your existing method.
    }

    public double computeTerrainCost(World world, int x, int y, int z) {
        // This could simply call your existing method, for example:
        return computeTerrainCostAtLevel(world, x, y, z, new HashMap<>());
    }

    /**
     * Generates an advanced 3D node map for only a 100-block radius from the world spawn.
     * <p>
     * Scans each (x,z) column for candidate floors (solid block with headroom)
     * and creates a node at each candidate (x, y, z).
     *
     * @param world the Minecraft world to scan
     * @return a list of navigation nodes with precomputed costs and neighbor links
     */
    public List<NavNode> generateNodeMap(World world) {
        List<NavNode> nodeList = new ArrayList<>();
        Location spawn = world.getSpawnLocation();
        int radius = 100; // Only generate nodes within 100 blocks of spawn

        int minX = spawn.getBlockX() - radius;
        int maxX = spawn.getBlockX() + radius;
        int minZ = spawn.getBlockZ() - radius;
        int maxZ = spawn.getBlockZ() + radius;

        // Build a candidate map for each (x,z) column.
        // Key: "x_z", Value: List of candidate floor Y positions (where a player could stand)
        Map<String, List<Integer>> candidateMap = new HashMap<>();
        for (int x = minX; x <= maxX; x += NODE_SPACING) {
            for (int z = minZ; z <= maxZ; z += NODE_SPACING) {
                List<Integer> floors = getCandidateFloorYs(world, x, z);
                if (!floors.isEmpty()) {
                    candidateMap.put(x + "_" + z, floors);
                }
            }
        }

        // For each (x,z) with candidate floors, create nodes at those levels.
        for (int x = minX; x <= maxX; x += NODE_SPACING) {
            for (int z = minZ; z <= maxZ; z += NODE_SPACING) {
                String key = x + "_" + z;
                if (!candidateMap.containsKey(key)) continue;
                List<Integer> floors = candidateMap.get(key);
                for (int y : floors) {
                    // Only include nodes within the horizontal radius from spawn.
                    if (Math.abs(x - spawn.getBlockX()) > radius || Math.abs(z - spawn.getBlockZ()) > radius)
                        continue;
                    double cost = computeTerrainCostAtLevel(world, x, y, z, candidateMap);
                    NavNode node = new NavNode(x, y, z, cost);
                    nodeList.add(node);
                }
            }
        }

        buildNeighborLinks(nodeList);
        return nodeList;
    }

    /**
     * Scans a column (x,z) from world.getMinHeight() to world.getMaxHeight()-2 and returns candidate floor levels.
     * A candidate floor is detected when a solid block is found with two blocks of headroom above.
     *
     * @param world the world instance
     * @param x     the x-coordinate
     * @param z     the z-coordinate
     * @return list of y positions where a node can be placed (player’s feet level)
     */
    private List<Integer> getCandidateFloorYs(World world, int x, int z) {
        List<Integer> floors = new ArrayList<>();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 2; // ensure space for headroom
        for (int y = minY; y <= maxY; y++) {
            Block floorCandidate = world.getBlockAt(x, y, z);
            Block head1 = world.getBlockAt(x, y + 1, z);
            Block head2 = world.getBlockAt(x, y + 2, z);
            // Check for a solid floor with two non-solid blocks above (i.e. headroom)
            if (floorCandidate.getType().isSolid() && !head1.getType().isSolid() && !head2.getType().isSolid()) {
                floors.add(y + 1); // candidate floor level (player stands one block above the solid floor)
                y += 2; // skip a few blocks to avoid duplicates
            }
        }
        return floors;
    }

    /**
     * Returns the candidate floor level at (x,z) closest to the given referenceY.
     * If no candidate exists, falls back to world.getHighestBlockAt(x,z)+1.
     *
     * @param world        the world instance
     * @param candidateMap the precomputed candidate floors map
     * @param x            the x-coordinate
     * @param z            the z-coordinate
     * @param referenceY   the reference y-level
     * @return the candidate floor y-level
     */
    private int getFloorAt(World world, Map<String, List<Integer>> candidateMap, int x, int z, int referenceY) {
        String key = x + "_" + z;
        if (candidateMap.containsKey(key)) {
            List<Integer> floors = candidateMap.get(key);
            int best = floors.get(0);
            int bestDiff = Math.abs(best - referenceY);
            for (int floor : floors) {
                int diff = Math.abs(floor - referenceY);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = floor;
                }
            }
            return best;
        }
        // Fallback: use highest block (plus one for headroom)
        return world.getHighestBlockAt(x, z).getY() + 1;
    }

    /**
     * Computes the terrain cost at a specific (x,y,z) candidate floor.
     *
     * @param world        the world instance
     * @param x            the x-coordinate
     * @param y            the candidate floor y-coordinate (player’s feet)
     * @param z            the z-coordinate
     * @param candidateMap the candidate floors map for neighbor sampling
     * @return the computed cost for traversing at (x,y,z)
     */
    public double computeTerrainCostAtLevel(World world, int x, int y, int z, Map<String, List<Integer>> candidateMap) {
        // Use the block below the candidate floor as the “floor” material.
        Block floorBlock = world.getBlockAt(x, y - 1, z);
        Material baseMat = floorBlock.getType();


        double rawSlope = sampleAverageHeightDifferenceAtLevel(world, candidateMap, x, z, y, SLOPE_SAMPLE_RADIUS);
        double slopeFactor = Math.max(0, rawSlope - SLOPE_THRESHOLD);
        double variance = sampleHeightVarianceAtLevel(world, candidateMap, x, z, y, VARIANCE_SAMPLE_RADIUS);

        double baseCost = 150.0;
        double slopePenalty = slopeFactor * 250;
        double variancePenalty = variance * 200;

        if ((baseMat == Material.ANDESITE || baseMat == Material.STONE)
                && slopeFactor < 1.0 && variance < 1.5) {
            baseCost = 15.0;
            slopePenalty *= 0.2;
            variancePenalty *= 0.2;
        } else if (PLAYER_PATH_MATERIALS.contains(baseMat)) {
            baseCost = PLAYER_PATH_BASE_COST;
        } else if (PREFERRED_MATERIALS.contains(baseMat)) {
            baseCost = 15.0;
        }
        if (BUILT_PATH_MATERIALS.contains(baseMat)) {
            baseCost = 2.0;
            slopePenalty *= 0.05;
            variancePenalty *= 0.05;
        }
        if (baseMat.toString().contains("STAIR")) {
            baseCost = 15.0;
            slopePenalty *= 0.005;
            variancePenalty *= 0.05;
        }
        int ledgeCount = countLedgeIndicators(world, x, y, z);
        double ledgePenalty = ledgeCount * 200;
        double cliffPenalty = isNearCliff(world, x, y, z) ? 400 : 0;
        double neighborAvgY = sampleAverageNeighborHeightAtLevel(world, candidateMap, x, z, y, VARIANCE_SAMPLE_RADIUS);
        double verticalPenalty = (y > neighborAvgY + 2) ? (y - neighborAvgY - 2) * 180 : 0;
        if (BUILT_PATH_MATERIALS.contains(baseMat)) {
            return baseCost;
        }
        double waterRatio = sampleWaterPenaltyAtLevel(world, candidateMap, x, z, y, WATER_SAMPLE_RADIUS);
        double waterPenalty = (waterRatio > WATER_THRESHOLD) ? (waterRatio - WATER_THRESHOLD) * 150 : 0;
        if (HAZARD_MATERIALS.contains(baseMat)) {
            waterPenalty += 500;
        }
        if (SLOW_MATERIALS.contains(baseMat)) {
            waterPenalty += 300;
        }
        double slowPenalty = sampleSlowPenaltyAtLevel(world, candidateMap, x, y, z, WATER_SAMPLE_RADIUS) * 100;
        double vegetationPenalty = sampleVegetationDensityAtLevel(world, candidateMap, x, y, z, WATER_SAMPLE_RADIUS) * VEGETATION_PENALTY;

        int lightLevel = world.getBlockAt(x, y, z).getLightLevel();
        double lightPenalty = (lightLevel < DARK_LIGHT_THRESHOLD) ? (DARK_LIGHT_THRESHOLD - lightLevel) * 15 : 0;
        double pathBonus = samplePlayerPathBonusAtLevel(world, candidateMap, x, y, z, PLAYER_PATH_SAMPLE_RADIUS);
        double bonus = (pathBonus > 0.1) ? pathBonus * 30 : 0;

        double totalCost = baseCost + slopePenalty + variancePenalty + ledgePenalty
                + cliffPenalty + verticalPenalty + waterPenalty + slowPenalty
                + vegetationPenalty + lightPenalty - bonus;
        return Math.max(5.0, Math.min(totalCost, 1000.0));
    }

    // --- Sampling methods updated to use candidate floors ---

    private double sampleAverageHeightDifferenceAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int z, int centerY, int radius) {
        double totalDiff = 0;
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, centerY);
                totalDiff += Math.abs(centerY - sampleY);
                count++;
            }
        }
        return count > 0 ? totalDiff / count : 0;
    }

    private double sampleAverageNeighborHeightAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int z, int centerY, int radius) {
        int sum = 0, count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, centerY);
                sum += sampleY;
                count++;
            }
        }
        return count > 0 ? (double) sum / count : centerY;
    }

    private double sampleHeightVarianceAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int z, int centerY, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, centerY);
                heights.add(sampleY);
            }
        }
        double mean = heights.stream().mapToDouble(h -> h).average().orElse(centerY);
        double variance = heights.stream().mapToDouble(h -> (h - mean) * (h - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private double sampleWaterPenaltyAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int z, int centerY, int radius) {
        int waterCount = 0, total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, centerY);
                Block blockBelow = world.getBlockAt(x + dx, sampleY - 1, z + dz);
                total++;
                if (blockBelow.getType() == Material.WATER || blockBelow.getType() == Material.LAVA)
                    waterCount++;
            }
        }
        double ratio = (double) waterCount / total;
        return ratio > WATER_THRESHOLD ? Math.exp((ratio - WATER_THRESHOLD) * WATER_EXPONENT_MULTIPLIER) : 1.0;
    }

    private double sampleSlowPenaltyAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int y, int z, int radius) {
        int slowCount = 0, total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, y);
                Block block = world.getBlockAt(x + dx, sampleY - 1, z + dz);
                total++;
                if (SLOW_MATERIALS.contains(block.getType())) slowCount++;
            }
        }
        double ratio = (double) slowCount / total;
        return ratio > 0.1 ? Math.exp(ratio * SLOW_MULTIPLIER) : 1.0;
    }

    private double sampleVegetationDensityAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int y, int z, int radius) {
        int vegetationCount = 0;
        int total = (2 * radius + 1) * (2 * radius + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, y);
                Block sampleBlock = world.getBlockAt(x + dx, sampleY, z + dz);
                if (VEGETATION_MATERIALS.contains(sampleBlock.getType()))
                    vegetationCount++;
            }
        }
        return (double) vegetationCount / total;
    }

    private double samplePlayerPathBonusAtLevel(World world, Map<String, List<Integer>> candidateMap, int x, int y, int z, int radius) {
        int pathCount = 0, total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int sampleY = getFloorAt(world, candidateMap, x + dx, z + dz, y);
                Block block = world.getBlockAt(x + dx, sampleY - 1, z + dz);
                total++;
                if (PLAYER_PATH_MATERIALS.contains(block.getType()))
                    pathCount++;
            }
        }
        double ratio = (double) pathCount / total;
        return ratio > 0.1 ? Math.exp(-ratio * PLAYER_PATH_BONUS) : 1.0;
    }

    // --- Other helper methods (unchanged) ---

    /**
     * Checks adjacent blocks (in the four cardinal directions) at y-1 to see if any are empty.
     */
    private boolean isNearCliff(World world, int x, int y, int z) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block adjacent = world.getBlockAt(x + face.getModX(), y - 1, z + face.getModZ());
            if (adjacent.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Counts how many of the four cardinal adjacent blocks at y-1 are empty.
     */
    private int countLedgeIndicators(World world, int x, int y, int z) {
        int count = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block adjacent = world.getBlockAt(x + face.getModX(), y - 1, z + face.getModZ());
            if (adjacent.isEmpty()) count++;
        }
        return count;
    }

    private double movementCost(NavNode a, NavNode b) {
        int dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double baritoneHeuristic(NavNode a, NavNode b) {
        int dx = Math.abs(a.x - b.x), dz = Math.abs(a.z - b.z);
        return (dx + dz) + (Math.sqrt(2) - 2) * Math.min(dx, dz);
    }

    private double jumpCost(NavNode a, NavNode b) {
        int dx = Math.abs(a.x - b.x), dz = Math.abs(a.z - b.z);
        int horizontalDistance = dx + dz;
        if (horizontalDistance <= 1) return 0;
        double baseJumpCost = 0.548;
        double factor = (double) horizontalDistance / 4.0;
        return baseJumpCost * factor;
    }

    /**
     * Builds neighbor links for each node by connecting nodes in adjacent (x,z) columns,
     * and only linking if the vertical difference is within a threshold (here ≤4 blocks).
     * <p>
     * This produces a sparse 3D graph.
     *
     * @param nodes the list of navigation nodes to link
     */
    public void buildNeighborLinks(List<NavNode> nodes) {
        // Build an index mapping column (x_z) to node indices.
        Map<String, List<Integer>> columnIndex = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            NavNode node = nodes.get(i);
            String colKey = node.x + "_" + node.z;
            columnIndex.computeIfAbsent(colKey, k -> new ArrayList<>()).add(i);
        }
        // For each node, look in the adjacent columns.
        for (int i = 0; i < nodes.size(); i++) {
            NavNode node = nodes.get(i);
            for (int dx : new int[]{-NODE_SPACING, 0, NODE_SPACING}) {
                for (int dz : new int[]{-NODE_SPACING, 0, NODE_SPACING}) {
                    if (dx == 0 && dz == 0) continue;
                    String neighborColKey = (node.x + dx) + "_" + (node.z + dz);
                    if (!columnIndex.containsKey(neighborColKey)) continue;
                    for (int neighborIndex : columnIndex.get(neighborColKey)) {
                        NavNode neighbor = nodes.get(neighborIndex);
                        // Only link if vertical difference is within 4 blocks.
                        if (Math.abs(node.y - neighbor.y) <= 4) {
                            double transitionCost = (node.cost + neighbor.cost) / 2.0
                                    + movementCost(node, neighbor)
                                    + baritoneHeuristic(node, neighbor)
                                    + jumpCost(node, neighbor);
                            node.neighbors.add(new NavNode.Neighbor(neighborIndex, transitionCost));
                        }
                    }
                }
            }
        }
    }

    /**
     * Saves the generated node map to a file.
     *
     * @param nodes the list of navigation nodes to save
     * @param file  the file to which the node map is saved
     */
    public void saveNodeMap(List<NavNode> nodes, File file) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(nodes);
            System.out.println("Advanced node map saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads a node map from the specified file.
     *
     * @param file the file from which to load the node map
     * @return the list of navigation nodes loaded from the file, or an empty list on failure
     */
    @SuppressWarnings("unchecked")
    public List<NavNode> loadNodeMap(File file) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            List<NavNode> nodes = (List<NavNode>) ois.readObject();
            System.out.println("Advanced node map loaded from " + file.getAbsolutePath());
            return nodes;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Checks for the existence of a node map file.
     * If it exists, loads and returns the node map; otherwise, generates a new node map (within 100-block radius of spawn),
     * saves it to the file, and returns it.
     *
     * @param world the world instance to scan if needed
     * @param file  the file (typically "server_advanced_navgraph.dat") to load from or save to
     * @return the list of navigation nodes
     */
    public List<NavNode> getOrGenerateNodeMap(World world, File file) {
        if (file.exists()) {
            return loadNodeMap(file);
        } else {
            List<NavNode> nodeList = generateNodeMap(world);
            saveNodeMap(nodeList, file);
            return nodeList;
        }
    }
}

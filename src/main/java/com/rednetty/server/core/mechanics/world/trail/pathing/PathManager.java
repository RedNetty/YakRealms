package com.rednetty.server.core.mechanics.world.trail.pathing;

import com.rednetty.server.core.mechanics.world.trail.pathing.nodes.AdvancedNodeMapGenerator;
import com.rednetty.server.core.mechanics.world.trail.pathing.nodes.NavGraphPathfinder;
import com.rednetty.server.core.mechanics.world.trail.pathing.nodes.NavNode;
import com.rednetty.server.core.mechanics.world.trail.pathing.nodes.OptimizedNodeMapStorage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages the creation, visualization, and maintenance of paths for players.
 *
 * <p>This class loads or generates navigation nodes, creates paths using a {@link NavGraphPathfinder},
 * schedules periodic path-check and maintenance tasks, and handles visualization of both paths and nodes.</p>
 */
public class PathManager {
    private final JavaPlugin plugin;
    private final NavGraphPathfinder pathfinder;
    private final ParticleSystem particleSystem;
    private final List<NavNode> nodeList;

    // Active paths keyed by player UUID
    private final Map<UUID, ActivePathData> activePaths = new ConcurrentHashMap<>();
    // Pending asynchronous path calculations
    private final Map<UUID, Future<?>> pendingCalculations = new ConcurrentHashMap<>();
    // Player node visualization toggle states
    private final Map<UUID, Boolean> nodeVisualizationStates = new ConcurrentHashMap<>();

    private BukkitRunnable pathCheckTask;
    private BukkitRunnable maintenanceTask;
    private final ExecutorService pathExecutor;

    // Configuration constants
    private static final int MAX_CONCURRENT_PATHS = 8;
    private static final double WAYPOINT_REACH_DISTANCE = 3.0;
    private static final double PATH_RECALC_DISTANCE = 10.0;
    private static final long RECALC_COOLDOWN = 2000;
    private static final double MIN_PATH_LENGTH = 5.0;
    private static final long PATH_TIMEOUT = 300000; // 5 minutes
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int PATH_CHECK_INTERVAL = 5;       // in ticks
    private static final int MAINTENANCE_INTERVAL = 1200;     // in ticks

    /**
     * Defines types of paths with associated visual styles and progress sounds.
     */
    public enum PathType {
        QUEST(ParticleSystem.PathStyle.MAGICAL, Sound.BLOCK_NOTE_BLOCK_CHIME),
        DISCOVERY(ParticleSystem.PathStyle.NATURE, Sound.BLOCK_NOTE_BLOCK_FLUTE),
        EVENT(ParticleSystem.PathStyle.ROYAL, Sound.BLOCK_NOTE_BLOCK_BELL),
        DEFAULT(ParticleSystem.PathStyle.ETHEREAL, Sound.BLOCK_NOTE_BLOCK_PLING);

        final ParticleSystem.PathStyle style;
        final Sound progressSound;

        PathType(ParticleSystem.PathStyle style, Sound progressSound) {
            this.style = style;
            this.progressSound = progressSound;
        }
    }

    /**
     * Contains data related to an active path for a player.
     */
    private static class ActivePathData {
        final List<Location> fullPath;
        final List<Location> remainingPath;
        final Location destination;
        final PathType pathType;
        long lastRecalculation;
        boolean completed;
        int currentWaypointIndex;
        int failedRecalculationAttempts;
        boolean needsRecalculation;
        Location lastPlayerLocation;

        ActivePathData(List<Location> path, Location destination, PathType pathType) {
            this.fullPath = new ArrayList<>(path);
            this.remainingPath = new ArrayList<>(path);
            this.destination = destination;
            this.pathType = pathType;
            this.lastRecalculation = System.currentTimeMillis();
            this.completed = false;
            this.currentWaypointIndex = 0;
            this.failedRecalculationAttempts = 0;
            this.needsRecalculation = false;
            this.lastPlayerLocation = null;
        }
    }

    /**
     * Constructs a new PathManager. Loads nodes from disk (or generates them if needed),
     * creates the pathfinder, and starts periodic tasks.
     *
     * @param plugin         the plugin instance
     * @param particleSystem the particle system for visualization
     */
    public PathManager(JavaPlugin plugin, ParticleSystem particleSystem) {
        this.plugin = plugin;
        this.particleSystem = particleSystem;

        this.pathExecutor = new ThreadPoolExecutor(
                2, MAX_CONCURRENT_PATHS,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread thread = new Thread(r, "PathCalculation");
                    thread.setDaemon(true);
                    return thread;
                }
        );

        World world = plugin.getServer().getWorld("map");
        if (world == null) {
            throw new IllegalStateException("server world not found!");
        }

        File nodeMapFile = new File(plugin.getDataFolder(), "server_advanced_navgraph.dat");
        this.nodeList = loadNodes(world, nodeMapFile);
        this.pathfinder = new NavGraphPathfinder(nodeList, world, plugin);

        startPathCheckTask();
        startMaintenanceTask();
    }

    /**
     * Loads the navigation nodes from a file or generates a new map if necessary.
     *
     * @param world       the target world
     * @param nodeMapFile the file to load/save nodes
     * @return a List of NavNode objects
     */
    private List<NavNode> loadNodes(World world, File nodeMapFile) {
        List<NavNode> nodes = null;
        if (nodeMapFile.exists()) {
            try {
                nodes = OptimizedNodeMapStorage.loadOptimizedNodeMap(nodeMapFile);
                // Loaded optimized node map
            } catch (IOException e) {
                plugin.getLogger().severe("Error loading optimized node map: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (nodes == null || nodes.isEmpty()) {
            plugin.getLogger().info("Generating new node map...");
            AdvancedNodeMapGenerator generator = new AdvancedNodeMapGenerator();
            nodes = generator.generateNodeMap(world);
            try {
                OptimizedNodeMapStorage.saveOptimizedNodeMap(nodes, nodeMapFile);
                plugin.getLogger().info("Saved optimized node map with " + nodes.size() + " nodes.");
            } catch (IOException e) {
                plugin.getLogger().severe("Error saving optimized node map: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return nodes;
    }

    /* ===================== Task Scheduling ===================== */

    /**
     * Starts the periodic task that checks active paths and updates progress.
     */
    private void startPathCheckTask() {
        if (pathCheckTask != null) {
            pathCheckTask.cancel();
        }
        pathCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkActivePaths();
            }
        };
        pathCheckTask.runTaskTimer(plugin, PATH_CHECK_INTERVAL, PATH_CHECK_INTERVAL);
    }

    /**
     * Starts the maintenance task that cleans up stale data and recovers stuck paths.
     */
    private void startMaintenanceTask() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
        }
        maintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cleanupStalePaths(now);
                cleanupPendingCalculations();
                cleanupVisualizationStates();
                recoverStuckPaths(now);
            }
        };
        maintenanceTask.runTaskTimer(plugin, MAINTENANCE_INTERVAL, MAINTENANCE_INTERVAL);
    }

    /* ===================== Active Path Processing ===================== */

    /**
     * Checks all active paths for progress, recalculation needs, or cancellation.
     */
    private void checkActivePaths() {
        for (Map.Entry<UUID, ActivePathData> entry : activePaths.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            ActivePathData pathData = entry.getValue();

            if (player == null || !player.isOnline() || pathData.completed) {
                cancelPath(entry.getKey());
                continue;
            }

            Location playerLoc = player.getLocation();
            updateLastPlayerLocation(pathData, playerLoc);
            processWaypointProgress(player, pathData, playerLoc);
            processPathRecalculation(player, pathData, playerLoc);
        }
    }

    private void updateLastPlayerLocation(ActivePathData pathData, Location playerLoc) {
        if (pathData.lastPlayerLocation == null) {
            pathData.lastPlayerLocation = playerLoc;
        }
    }

    private void processWaypointProgress(Player player, ActivePathData pathData, Location playerLoc) {
        if (!pathData.remainingPath.isEmpty()) {
            Location currentWaypoint = pathData.remainingPath.get(0);
            if (isWaypointReached(playerLoc, currentWaypoint)) {
                progressPath(player, pathData);
            }
        }
    }

    private void processPathRecalculation(Player player, ActivePathData pathData, Location playerLoc) {
        if (!pathData.needsRecalculation && shouldRecalculatePath(playerLoc, pathData)) {
            pathData.needsRecalculation = true;
            recalculatePath(player, pathData);
        }
    }

    /**
     * Updates the path progress when a waypoint is reached.
     */
    private void progressPath(Player player, ActivePathData pathData) {
        pathData.remainingPath.remove(0);
        pathData.currentWaypointIndex++;
        player.playSound(player.getLocation(), pathData.pathType.progressSound, 0.5f, 1.2f);

        if (pathData.remainingPath.isEmpty()) {
            completePath(player, pathData);
        } else {
            particleSystem.startPathVisualization(player, pathData.remainingPath, pathData.pathType.style);
            sendProgressMessage(player, pathData);
        }
    }

    /**
     * Completes the navigation path for a player.
     */
    private void completePath(Player player, ActivePathData pathData) {
        pathData.completed = true;
        player.sendMessage(ChatColor.GREEN + "You have reached your destination safely!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        particleSystem.stopPathVisualization(player);
        activePaths.remove(player.getUniqueId());
    }

    /**
     * Recalculates the path for a player asynchronously.
     *
     * @param player   the player
     * @param pathData the current path data
     */
    private void recalculatePath(Player player, ActivePathData pathData) {
        if (pathData.failedRecalculationAttempts >= MAX_FAILED_ATTEMPTS) {
            player.sendMessage(ChatColor.RED + "Path recalculation failed too many times. Canceling navigation.");
            cancelPath(player.getUniqueId());
            return;
        }
        pathData.lastRecalculation = System.currentTimeMillis();
        pathData.needsRecalculation = false;

        Future<?> calculation = pathExecutor.submit(() -> {
            List<Location> newPath = pathfinder.findPath(player.getLocation(), pathData.destination);
            if (!newPath.isEmpty()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        pathData.remainingPath.clear();
                        pathData.remainingPath.addAll(newPath);
                        particleSystem.startPathVisualization(player, newPath, pathData.pathType.style);
                        pathData.failedRecalculationAttempts = 0;
                    }
                }.runTask(plugin);
            } else {
                pathData.failedRecalculationAttempts++;
                if (pathData.failedRecalculationAttempts >= MAX_FAILED_ATTEMPTS) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.sendMessage(ChatColor.RED + "Unable to recalculate path. Canceling navigation.");
                            cancelPath(player.getUniqueId());
                        }
                    }.runTask(plugin);
                }
            }
        });
        pendingCalculations.put(player.getUniqueId(), calculation);
    }

    /**
     * Determines whether the path should be recalculated based on the player's position.
     */
    private boolean shouldRecalculatePath(Location playerLoc, ActivePathData pathData) {
        if (System.currentTimeMillis() - pathData.lastRecalculation < RECALC_COOLDOWN) {
            return false;
        }
        double minDistance = pathData.remainingPath.stream()
                .mapToDouble(loc -> loc.distance(playerLoc))
                .min()
                .orElse(Double.MAX_VALUE);
        return minDistance > PATH_RECALC_DISTANCE;
    }

    /* ===================== Task Cleanup & Recovery ===================== */

    /**
     * Cleans up stale or timed-out active paths.
     */
    private void cleanupStalePaths(long now) {
        activePaths.entrySet().removeIf(entry -> {
            ActivePathData pathData = entry.getValue();
            if (pathData.completed || now - pathData.lastRecalculation > PATH_TIMEOUT) {
                Player player = plugin.getServer().getPlayer(entry.getKey());
                if (player != null) {
                    particleSystem.stopPathVisualization(player);
                    if (!pathData.completed) {
                        player.sendMessage(ChatColor.RED + "Path navigation timed out.");
                    }
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Removes completed or canceled pending calculations.
     */
    private void cleanupPendingCalculations() {
        pendingCalculations.entrySet().removeIf(entry -> {
            Future<?> future = entry.getValue();
            return future.isDone() || future.isCancelled();
        });
    }

    /**
     * Cleans up node visualization states for offline players.
     */
    private void cleanupVisualizationStates() {
        nodeVisualizationStates.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                cancelPath(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Checks for players who appear stuck and forces a path recalculation.
     */
    private void recoverStuckPaths(long now) {
        for (Map.Entry<UUID, ActivePathData> entry : activePaths.entrySet()) {
            ActivePathData pathData = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && !pathData.completed && pathData.lastPlayerLocation != null) {
                Location playerLoc = player.getLocation();
                double stuckTime = now - pathData.lastRecalculation;
                if (stuckTime > RECALC_COOLDOWN * 3 && playerLoc.distance(pathData.lastPlayerLocation) < 1.0) {
                    pathData.needsRecalculation = true;
                    recalculatePath(player, pathData);
                }
            }
        }
    }

    /* ===================== Visualization & Interaction ===================== */

    /**
     * Toggles node visualization for a player.
     *
     * @param player the player
     */
    public void toggleNodeVisualization(Player player) {
        UUID playerId = player.getUniqueId();
        boolean newState = !nodeVisualizationStates.getOrDefault(playerId, false);
        nodeVisualizationStates.put(playerId, newState);
        if (newState) {
            particleSystem.toggleNodeVisualization(player, nodeList);
            player.sendMessage(ChatColor.GREEN + "Node visualization enabled (colored by terrain cost)");
            player.sendMessage(ChatColor.GRAY + "Green = Easy terrain, Red = Difficult terrain");
        } else {
            particleSystem.toggleNodeVisualization(player, null);
            player.sendMessage(ChatColor.YELLOW + "Node visualization disabled");
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newState ? 1.2f : 0.8f);
    }

    /**
     * Creates a new path for the player toward a destination.
     *
     * @param player      the player
     * @param destination the destination location
     * @param pathType    the type/style of the path
     */
    public void createPath(Player player, Location destination, PathType pathType) {
        UUID playerId = player.getUniqueId();
        cancelPath(playerId);
        cancelPendingCalculation(playerId);

        if (player.getLocation().distance(destination) < MIN_PATH_LENGTH) {
            player.sendMessage(ChatColor.YELLOW + "Destination is too close for pathfinding.");
            return;
        }

        Future<?> calculation = pathExecutor.submit(() -> {
            try {
                calculateAndVisualizePath(player, destination, pathType);
            } catch (Exception e) {
                plugin.getLogger().warning("Path calculation failed for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.RED + "Failed to calculate path. Please try again.");
                    }
                }.runTask(plugin);
            }
        });
        pendingCalculations.put(playerId, calculation);
    }

    /**
     * Cancels any pending path calculation for a player.
     */
    private void cancelPendingCalculation(UUID playerId) {
        Future<?> pending = pendingCalculations.remove(playerId);
        if (pending != null) {
            pending.cancel(true);
        }
    }

    /**
     * Calculates a path asynchronously and visualizes it to the player.
     */
    private void calculateAndVisualizePath(Player player, Location destination, PathType pathType) {
        List<Location> path = pathfinder.findPath(player.getLocation(), destination);
        if (path.isEmpty() || getTotalPathLength(path) < MIN_PATH_LENGTH) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(ChatColor.RED + "Could not find a safe path to destination.");
                }
            }.runTask(plugin);
            return;
        }
        ActivePathData pathData = new ActivePathData(path, destination, pathType);
        pathData.lastPlayerLocation = player.getLocation();
        activePaths.put(player.getUniqueId(), pathData);
        new BukkitRunnable() {
            @Override
            public void run() {
                particleSystem.startPathVisualization(player, path, pathType.style);
                player.playSound(player.getLocation(), pathType.progressSound, 1.0f, 1.0f);
                sendPathCreatedMessage(player, destination);
            }
        }.runTask(plugin);
    }

    /**
     * Cancels an active path for a given player.
     *
     * @param playerId the player's UUID
     */
    public void cancelPath(UUID playerId) {
        ActivePathData pathData = activePaths.remove(playerId);
        if (pathData != null) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                particleSystem.stopPathVisualization(player);
                player.sendMessage(ChatColor.YELLOW + "Navigation cancelled.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
        }
        cancelPendingCalculation(playerId);
    }

    /**
     * Checks if the player has reached the given waypoint.
     */
    private boolean isWaypointReached(Location playerLoc, Location waypoint) {
        return playerLoc.distance(waypoint) <= WAYPOINT_REACH_DISTANCE;
    }

    /**
     * Computes the total length of a path.
     */
    private double getTotalPathLength(List<Location> path) {
        double length = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            length += path.get(i).distance(path.get(i + 1));
        }
        return length;
    }

    /**
     * Sends a message to the player when a path is created.
     */
    private void sendPathCreatedMessage(Player player, Location destination) {
        String coords = String.format("%.1f, %.1f, %.1f",
                destination.getX(), destination.getY(), destination.getZ());
        player.sendMessage(ChatColor.GREEN + "Following safest path to " + coords);
        player.sendMessage(ChatColor.GRAY + "Following roads and paths when possible.");
    }

    /**
     * Sends periodic progress messages to the player.
     */
    private void sendProgressMessage(Player player, ActivePathData pathData) {
        int remaining = pathData.remainingPath.size();
        int total = pathData.fullPath.size();
        int percent = (int) (((total - remaining) / (double) total) * 100);
        if (pathData.currentWaypointIndex % 5 == 0) {
            player.sendMessage(ChatColor.GRAY + "Progress: " + percent + "% complete");
        }
    }

    /* ===================== Shutdown & Utility ===================== */

    /**
     * Shuts down the PathManager by canceling tasks, stopping visualizations, and terminating the executor.
     */
    public void shutdown() {
        // Cancel active paths and notify players
        for (Map.Entry<UUID, ActivePathData> entry : activePaths.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                particleSystem.stopPathVisualization(player);
                player.sendMessage(ChatColor.RED + "Navigation cancelled due to system shutdown.");
            }
        }
        // Disable node visualizations for all players
        for (Map.Entry<UUID, Boolean> entry : nodeVisualizationStates.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                particleSystem.toggleNodeVisualization(player, null);
            }
        }
        // Cancel scheduled tasks
        if (pathCheckTask != null) {
            pathCheckTask.cancel();
            pathCheckTask = null;
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        // Shutdown executor
        pathExecutor.shutdown();
        try {
            if (!pathExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pathExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pathExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // Clear collections
        activePaths.clear();
        pendingCalculations.clear();
        nodeVisualizationStates.clear();
    }

    /**
     * Checks whether a player has an active path.
     */
    public boolean hasActivePath(Player player) {
        return activePaths.containsKey(player.getUniqueId());
    }

    /**
     * Gets the current destination of a player's active path.
     */
    public Location getCurrentDestination(Player player) {
        ActivePathData pathData = activePaths.get(player.getUniqueId());
        return pathData != null ? pathData.destination : null;
    }

    /**
     * Cancels all active paths.
     */
    public void clearAllPaths() {
        new ArrayList<>(activePaths.keySet()).forEach(this::cancelPath);
    }

    /**
     * Returns the number of active paths.
     */
    public int getActivePathCount() {
        return activePaths.size();
    }

    /**
     * Updates the path visualization for the given player.
     */
    public void updatePathVisuals(Player player) {
        ActivePathData pathData = activePaths.get(player.getUniqueId());
        if (pathData != null && !pathData.completed) {
            particleSystem.startPathVisualization(player, pathData.remainingPath, pathData.pathType.style);
        }
    }

    /**
     * Checks if the player is currently visualizing nodes.
     */
    public boolean isVisualizingNodes(Player player) {
        return nodeVisualizationStates.getOrDefault(player.getUniqueId(), false);
    }
}

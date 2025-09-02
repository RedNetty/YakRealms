package com.rednetty.server.core.mechanics.world.trail;

import com.rednetty.server.YakRealms;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Trail {
    private static final double DISPLAY_RADIUS = 100.0;
    private static final int DISPLAY_INTERVAL = 1;
    private static final int UPDATE_INTERVAL = 20;
    private static final double UPDATE_THRESHOLD = 3.0;
    private static final double RECALCULATION_THRESHOLD = 5.0;
    private static final double PARTICLE_SPACING = 1.0;
    private static final int PARTICLES_PER_STEP = 2;
    private static final double PARTICLE_OFFSET_Y = 1.0;
    private static final double ARRIVE_THRESHOLD = 3.0;
    private static final long PATH_UPDATE_COOLDOWN = 1000;
    private static final int MAX_VISIBLE_POINTS = 100;

    private final YakRealms plugin;
    private final Player player;
    private final Location destination;
    private final TrailSystem.ParticleStyle style;
    private final PathFinder pathFinder;
    private BukkitRunnable displayTask;
    private BukkitRunnable updateTask;
    private final List<Location> visiblePath = new ArrayList<>();
    private List<Location> currentPath;
    private final AtomicBoolean isRecalculating = new AtomicBoolean(false);
    private long lastPathUpdate = 0;
    private int currentPathIndex = 0;

    public Trail(YakRealms plugin, Player player, Location destination,
                 TrailSystem.ParticleStyle style, PathFinder pathFinder) {
        this.plugin = plugin;
        this.player = player;
        this.destination = destination;
        this.style = style;
        this.pathFinder = pathFinder;
    }

    public void start() {
        // Trail starting

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pathFinder.findPath(player.getLocation(), destination)
                    .thenAccept(path -> {
                        if (path != null) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                currentPath = new ArrayList<>(path);
                                plugin.getLogger().info("Path found with " +
                                        currentPath.size() + " points");
                                updateVisibleSegment();
                                startTasks();
                            });
                        } else {
                            plugin.getLogger().warning("Could not find valid path");
                        }
                    });
        });
    }

    private void findPathWithRetries(Location start, Location end, int attemptsLeft) {
        pathFinder.findPath(start, end)
                .thenAccept(path -> {
                    if (path != null) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            currentPath = new ArrayList<>(path);
                            plugin.getLogger().info("Path found with " +
                                    currentPath.size() + " points");
                            updateVisibleSegment();
                            startTasks();
                        });
                    } else if (attemptsLeft > 1) {
                        plugin.getLogger().info("Retrying path calculation, " +
                                (attemptsLeft - 1) + " attempts remaining");
                        // Wait briefly before retry
                        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                                findPathWithRetries(start, end, attemptsLeft - 1), 10L);
                    } else {
                        plugin.getLogger().warning("Failed to find path after all attempts");
                    }
                });
    }

    private void requestInitialPath() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location startLoc = player.getLocation().clone();
            // Finding initial path

            pathFinder.findPath(startLoc, destination)
                    .thenAccept(path -> {
                        if (path != null) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                currentPath = new ArrayList<>(path);
                                // Initial path found
                                updateVisibleSegment();
                                startTasks();
                            });
                        } else {
                            plugin.getLogger().warning("Failed to find initial path");
                        }
                    });
        });
    }

    private void startTasks() {
        stopTasks();

        // Particle display task
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    displayTrail();
                } catch (Exception e) {
                    plugin.getLogger().severe("Display task error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        displayTask.runTaskTimer(plugin, 0, DISPLAY_INTERVAL);

        // Path update task
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkAndUpdatePath();
                } catch (Exception e) {
                    plugin.getLogger().severe("Update task error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        updateTask.runTaskTimer(plugin, 0, UPDATE_INTERVAL);

        // Trail tasks started
    }

    private void displayTrail() {
        if (visiblePath.isEmpty() || !player.isOnline()) return;

        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        // Optimize particle rendering by using vectors
        Vector prevPoint = null;
        for (Location point : visiblePath) {
            if (point.getWorld() != world) continue;

            Vector currentPoint = point.toVector();
            if (prevPoint != null) {
                Vector direction = currentPoint.clone().subtract(prevPoint).normalize();
                double distance = currentPoint.distance(prevPoint);

                for (double d = 0; d < distance; d += PARTICLE_SPACING) {
                    Location particleLoc = prevPoint.clone()
                            .add(direction.clone().multiply(d))
                            .toLocation(world)
                            .add(0, PARTICLE_OFFSET_Y, 0);

                    if (particleLoc.distanceSquared(playerLoc) <= DISPLAY_RADIUS * DISPLAY_RADIUS) {
                        world.spawnParticle(
                                getParticle(),
                                particleLoc,
                                PARTICLES_PER_STEP,
                                0.1, 0.1, 0.1,
                                0.01
                        );
                    }
                }
            }
            prevPoint = currentPoint;
        }
    }

    private void checkAndUpdatePath() {
        if (!player.isOnline()) {
            stop();
            return;
        }

        Location playerLoc = player.getLocation();

        // Check if player reached destination
        if (playerLoc.distanceSquared(destination) <= ARRIVE_THRESHOLD * ARRIVE_THRESHOLD) {
            plugin.getLogger().info("Player reached destination");
            stop();
            return;
        }

        if (currentPath == null || currentPath.isEmpty()) {
            requestPathUpdate(playerLoc);
            return;
        }

        // Find closest point on current path
        int closestIndex = findClosestPointIndex(playerLoc);
        if (closestIndex == -1) {
            requestPathUpdate(playerLoc);
            return;
        }

        Location closestPoint = currentPath.get(closestIndex);
        double distanceToPath = playerLoc.distanceSquared(closestPoint);

        // Update path based on distance thresholds
        if (distanceToPath > RECALCULATION_THRESHOLD * RECALCULATION_THRESHOLD) {
            requestPathUpdate(playerLoc);
        } else if (distanceToPath > UPDATE_THRESHOLD * UPDATE_THRESHOLD ||
                closestIndex > currentPathIndex) {
            currentPathIndex = closestIndex;
            updateVisibleSegment();
        }
    }

    private void requestPathUpdate(Location start) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPathUpdate < PATH_UPDATE_COOLDOWN ||
                !isRecalculating.compareAndSet(false, true)) {
            return;
        }

        lastPathUpdate = currentTime;
        // Updating path

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pathFinder.findPath(start, destination)
                    .thenAccept(path -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (path != null) {
                                currentPath = new ArrayList<>(path);
                                currentPathIndex = 0;
                                updateVisibleSegment();
                                plugin.getLogger().info("Path updated: " +
                                        currentPath.size() + " points");
                            } else {
                                plugin.getLogger().warning("Path update failed");
                            }
                            isRecalculating.set(false);
                        });
                    });
        });
    }

    private int findClosestPointIndex(Location playerLoc) {
        if (currentPath == null || currentPath.isEmpty()) return -1;

        int closestIndex = -1;
        double closestDist = Double.MAX_VALUE;

        // Start search from current index to avoid backtracking
        for (int i = currentPathIndex; i < currentPath.size(); i++) {
            Location pathPoint = currentPath.get(i);
            double dist = pathPoint.distanceSquared(playerLoc);
            if (dist < closestDist) {
                closestDist = dist;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    private void updateVisibleSegment() {
        synchronized (visiblePath) {
            visiblePath.clear();
            if (currentPath == null || currentPath.isEmpty()) return;

            // Calculate visible segment
            int endIndex = Math.min(currentPathIndex + MAX_VISIBLE_POINTS,
                    currentPath.size());
            for (int i = currentPathIndex; i < endIndex; i++) {
                visiblePath.add(currentPath.get(i).clone());
            }
        }
    }

    private Particle getParticle() {
        return switch (style) {
            case FLAME -> Particle.FLAME;
            case PORTAL -> Particle.DRAGON_BREATH;
            case REDSTONE -> Particle.DUST;
            case WITCH -> Particle.WITCH;
            case END_ROD -> Particle.END_ROD;
            default -> Particle.FLAME;
        };
    }

    public void stop() {
        stopTasks();
        synchronized (visiblePath) {
            visiblePath.clear();
        }
        currentPath = null;
        plugin.getLogger().info("Trail ended for " + player.getName());
    }

    private void stopTasks() {
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    public boolean isActive() {
        return displayTask != null && updateTask != null;
    }

    public Location getDestination() {
        return destination.clone();
    }

    private String formatLocation(Location loc) {
        return String.format("(%.2f, %.2f, %.2f)",
                loc.getX(), loc.getY(), loc.getZ());
    }
}
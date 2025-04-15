package com.rednetty.server.mechanics.world.trail.pathing;

import com.rednetty.server.mechanics.world.trail.pathing.nodes.NavNode;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleSystem {
    private final Map<UUID, PathVisualization> activeVisualizations = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeNodeVisualizations = new ConcurrentHashMap<>();
    private final Plugin plugin;

    // Configuration constants
    private static final int MAX_RENDER_DISTANCE = 30;
    private static final double PARTICLE_SPACING = 2.0;
    private static final int FADE_DISTANCE = 10;
    private static final double HEIGHT_OFFSET = 2.5;
    private static final double PATH_WIDTH = 0.3;
    private static final int PARTICLES_PER_SEGMENT = 2;

    // For cost-based coloring: adjust these to match your expected cost range.
    private static final double MIN_COST = 1.0;  // Nodes at or below this cost will be green.
    private static final double MAX_COST = 1000.0; // Nodes at or above this cost will be red.

    public enum PathStyle {
        ETHEREAL(new Color[]{
                Color.fromRGB(164, 225, 255),
                Color.fromRGB(111, 183, 255)
        }),
        MAGICAL(new Color[]{
                Color.fromRGB(255, 192, 255),
                Color.fromRGB(192, 128, 255)
        }),
        NATURE(new Color[]{
                Color.fromRGB(141, 255, 141),
                Color.fromRGB(98, 225, 98)
        }),
        ANCIENT(new Color[]{
                Color.fromRGB(255, 223, 141),
                Color.fromRGB(255, 199, 98)
        }),
        ROYAL(new Color[]{
                Color.fromRGB(255, 192, 0),
                Color.fromRGB(218, 165, 32)
        });

        final Color[] colors;

        PathStyle(Color[] colors) {
            this.colors = colors;
        }
    }

    public ParticleSystem(Plugin plugin) {
        this.plugin = plugin;
    }

    public void startPathVisualization(Player player, List<Location> path, PathStyle style) {
        stopPathVisualization(player);
        if (path.isEmpty()) return;
        List<Location> processedPath = processPathLocations(path);
        PathVisualization viz = new PathVisualization(
                player.getUniqueId(),
                processedPath,
                style,
                System.currentTimeMillis()
        );
        activeVisualizations.put(player.getUniqueId(), viz);
        startVisualizationTask(viz);
    }

    private List<Location> processPathLocations(List<Location> rawPath) {
        List<Location> processed = new ArrayList<>();
        if (rawPath.isEmpty()) return processed;
        processed.add(adjustLocationHeight(rawPath.get(0)));
        for (int i = 1; i < rawPath.size(); i++) {
            Location prev = rawPath.get(i - 1);
            Location curr = rawPath.get(i);
            double distance = prev.distance(curr);
            if (distance > PARTICLE_SPACING) {
                int points = (int) (distance / PARTICLE_SPACING);
                Vector direction = curr.toVector().subtract(prev.toVector()).normalize();
                for (int j = 1; j <= points; j++) {
                    Vector offset = direction.clone().multiply(j * PARTICLE_SPACING);
                    Location interpolated = prev.clone().add(offset);
                    processed.add(adjustLocationHeight(interpolated));
                }
            } else {
                processed.add(adjustLocationHeight(curr));
            }
        }
        return processed;
    }

    private Location adjustLocationHeight(Location loc) {
        int groundY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        Location adjusted = loc.clone();
        adjusted.setY(Math.max(groundY + HEIGHT_OFFSET, loc.getY()));
        return adjusted;
    }

    private void startVisualizationTask(PathVisualization viz) {
        BukkitTask task = new BukkitRunnable() {
            private int currentTick = 0;

            @Override
            public void run() {
                Player player = plugin.getServer().getPlayer(viz.playerId);
                if (player == null || !player.isOnline()) {
                    cancel();
                    activeVisualizations.remove(viz.playerId);
                    plugin.getLogger().info("[ParticleSystem] Cancelled path visualization (player offline).");
                    return;
                }
                Location playerLoc = player.getLocation();
                int nearestIndex = findNearestPathIndex(playerLoc, viz.path);
                int renderStart = Math.max(0, nearestIndex - 10);
                int renderEnd = Math.min(viz.path.size(), nearestIndex + 15);
                for (int i = renderStart; i < renderEnd; i++) {
                    Location pathLoc = viz.path.get(i);
                    if (playerLoc.distance(pathLoc) <= MAX_RENDER_DISTANCE) {
                        renderPathSegment(player, pathLoc, viz.style, i, currentTick);
                    }
                }
                currentTick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        viz.task = task;
    }

    private void renderPathSegment(Player player, Location location, PathStyle style, int index, int tick) {
        Location particleLoc = location.clone();
        Color color = style.colors[((index + tick) / 4) % style.colors.length];
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
        player.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0, dustOptions);
        if (index % 15 == 0) {
            spawnDirectionIndicator(player, location, color);
        }
    }

    private void spawnDirectionIndicator(Player player, Location location, Color color) {
        Location arrowLoc = location.clone().add(0, 0.5, 0);
        Particle.DustOptions arrowOptions = new Particle.DustOptions(color, 0.8f);
        player.spawnParticle(Particle.REDSTONE, arrowLoc, 1, 0, 0, 0, 0, arrowOptions);
    }

    /**
     * New method: Toggle node visualization based on node cost.
     * Each node is rendered with a color that is determined by its cost.
     */
    public void toggleNodeVisualization(Player player, List<NavNode> navNodes) {
        UUID playerId = player.getUniqueId();
        BukkitTask existing = activeNodeVisualizations.remove(playerId);
        if (existing != null) {
            existing.cancel();
            player.sendMessage(ChatColor.YELLOW + "Node visualization disabled.");
            plugin.getLogger().info("[ParticleSystem] Node visualization disabled for " + player.getName());
            return;
        }
        if (navNodes == null || navNodes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No node data available for visualization.");
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    cancel();
                    activeNodeVisualizations.remove(playerId);
                    return;
                }
                Location playerLoc = player.getLocation();
                for (NavNode node : navNodes) {
                    Location nodeLoc = new Location(player.getWorld(), node.x + 0.5, node.y, node.z + 0.5);
                    if (playerLoc.distance(nodeLoc) <= MAX_RENDER_DISTANCE) {
                        Color color = getColorForCost(node.cost);
                        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);
                        player.spawnParticle(Particle.REDSTONE, nodeLoc, 1, 0, 0, 0, 0, dustOptions);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        activeNodeVisualizations.put(playerId, task);
        player.sendMessage(ChatColor.GREEN + "Node visualization enabled (cost based).");
        plugin.getLogger().info("[ParticleSystem] Node visualization enabled for " + player.getName());
    }

    /**
     * Returns a Color that represents the cost.
     * Costs at or below MIN_COST will be green; at or above MAX_COST will be red;
     * values in between are interpolated.
     */
    private Color getColorForCost(double cost) {
        double t = (cost - MIN_COST) / (MAX_COST - MIN_COST);
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        int red = (int) (255 * t);
        int green = (int) (255 * (1 - t));
        int blue = 0;
        return Color.fromRGB(red, green, blue);
    }

    private int findNearestPathIndex(Location point, List<Location> path) {
        int nearest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double dist = point.distanceSquared(path.get(i));
            if (dist < minDist) {
                minDist = dist;
                nearest = i;
            }
        }
        return nearest;
    }

    public void stopPathVisualization(Player player) {
        UUID playerId = player.getUniqueId();
        PathVisualization viz = activeVisualizations.remove(playerId);
        if (viz != null && viz.task != null) {
            viz.task.cancel();
            plugin.getLogger().info("[ParticleSystem] Stopped path visualization for " + player.getName());
        }
    }

    public void cleanup() {
        for (PathVisualization viz : activeVisualizations.values()) {
            if (viz.task != null) {
                viz.task.cancel();
            }
        }
        activeVisualizations.clear();
        for (BukkitTask task : activeNodeVisualizations.values()) {
            task.cancel();
        }
        activeNodeVisualizations.clear();
    }

    private static class PathVisualization {
        final UUID playerId;
        final List<Location> path;
        final PathStyle style;
        final long startTime;
        BukkitTask task;

        PathVisualization(UUID playerId, List<Location> path, PathStyle style, long startTime) {
            this.playerId = playerId;
            this.path = path;
            this.style = style;
            this.startTime = startTime;
        }
    }
}

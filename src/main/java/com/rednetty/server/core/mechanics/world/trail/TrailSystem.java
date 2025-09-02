package com.rednetty.server.core.mechanics.world.trail;

import com.rednetty.server.YakRealms;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrailSystem {
    private static final int CLEANUP_INTERVAL = 20 * 60; // 1 minute in ticks
    private final YakRealms plugin;
    private final Map<UUID, Trail> activeTrails;
    private final PathFinder pathFinder;
    private static TrailSystem trailSystem = null;

    public TrailSystem(YakRealms plugin) {
        this.plugin = plugin;
        this.activeTrails = new ConcurrentHashMap<>();
        this.pathFinder = new PathFinder(plugin);
        startCleanupTask();

        trailSystem = this;
    }

    public static TrailSystem getInstance() {
        return trailSystem;
    }

    public void startTrail(Player player, Location destination, ParticleStyle style) {
        UUID playerId = player.getUniqueId();
        stopTrail(player);
        Trail trail = new Trail(plugin, player, destination, style, pathFinder);
        activeTrails.put(playerId, trail);
        trail.start();
        // Trail started for player
    }

    public void stopTrail(Player player) {
        UUID playerId = player.getUniqueId();
        Trail trail = activeTrails.remove(playerId);
        if (trail != null) {
            trail.stop();
            plugin.getLogger().info("Stopped trail for " + player.getName());
        }
    }

    public void cleanup() {
        for (Trail trail : activeTrails.values()) {
            trail.stop();
        }
        activeTrails.clear();
        pathFinder.shutdown();
        plugin.getLogger().info("Cleaned up all trails and resources");
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupInactiveTrails();
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    private void cleanupInactiveTrails() {
        activeTrails.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !entry.getValue().isActive()) {
                entry.getValue().stop();
                plugin.getLogger().info("Cleaned up inactive trail for " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    public boolean hasActiveTrail(Player player) {
        return activeTrails.containsKey(player.getUniqueId());
    }

    public int getActiveTrailCount() {
        return activeTrails.size();
    }

    private String formatLocation(Location loc) {
        return String.format("(%.2f, %.2f, %.2f)", loc.getX(), loc.getY(), loc.getZ());
    }

    public void onDisable() {
        cleanup();
    }

    public enum ParticleStyle {
        FLAME, PORTAL, REDSTONE, WITCH, END_ROD
    }
}
package com.rednetty.server.mechanics.world;

import com.rednetty.server.YakRealms;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages integration with WorldGuard for zone detection and management
 */
public class WorldGuardManager implements Listener {
    private static WorldGuardManager instance;
    private boolean worldGuardEnabled = false;
    private StateFlag safeZoneFlag;

    // Cache of region lists by world to improve performance
    private final Map<String, Set<ProtectedRegion>> safeZoneRegions = new HashMap<>();

    /**
     * Get the singleton instance
     *
     * @return The WorldGuardManager instance
     */
    public static WorldGuardManager getInstance() {
        if (instance == null) {
            instance = new WorldGuardManager();
        }
        return instance;
    }

    /**
     * Constructor initializes the manager
     */
    private WorldGuardManager() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Try to initialize immediately if WorldGuard is already loaded
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            initialize();
        }
    }

    /**
     * Initialize the WorldGuard integration
     */
    private void initialize() {
        try {
            // Initialize WorldGuard flags and cache regions
            updateRegionCache();
            worldGuardEnabled = true;
            YakRealms.log("WorldGuard integration enabled successfully.");
        } catch (Exception e) {
            YakRealms.error("Failed to initialize WorldGuard integration", e);
            worldGuardEnabled = false;
        }
    }

    /**
     * Update the cache of safe zone regions for faster lookups
     */
    public void updateRegionCache() {
        safeZoneRegions.clear();

        if (!isWorldGuardAvailable()) {
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            Set<ProtectedRegion> safeRegions = new HashSet<>();

            // Get all regions and filter for those with PVP disabled
            for (ProtectedRegion region : regionManager.getRegions().values()) {
                if (region.getFlag(Flags.PVP) == StateFlag.State.DENY) {
                    safeRegions.add(region);
                }
            }

            safeZoneRegions.put(world.getName(), safeRegions);
        }

        YakRealms.log("WorldGuard region cache updated. Found " + getSafeZoneCount() + " safe zones.");
    }

    /**
     * Get the total count of safe zones across all worlds
     *
     * @return The count of safe zones
     */
    public int getSafeZoneCount() {
        return safeZoneRegions.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Check if WorldGuard integration is working
     *
     * @return true if WorldGuard is available
     */
    public boolean isWorldGuardAvailable() {
        return worldGuardEnabled && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    /**
     * Check if a location is in a safe zone (no PvP)
     *
     * @param location The location to check
     * @return true if the location is in a safe zone
     */
    public boolean isSafeZone(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // Fallback to simple distance check if WorldGuard isn't available
        if (!isWorldGuardAvailable()) {
            return isDefaultSafeZone(location);
        }

        // Use WorldGuard's region query
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return (query.queryState(BukkitAdapter.adapt(location), null, Flags.PVP) == StateFlag.State.DENY);
    }

    /**
     * Check if a location is at the boundary of a safe zone
     *
     * @param location The location to check
     * @return true if the location is at a safe zone boundary
     */
    public boolean isAtSafeZoneBoundary(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!isWorldGuardAvailable()) {
            return false;
        }

        // Check current location
        boolean currentIsSafe = isSafeZone(location);

        // Check adjacent blocks
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            Location adjacent = location.clone().add(offset[0], offset[1], offset[2]);
            boolean adjacentIsSafe = isSafeZone(adjacent);

            // If one is safe and the other isn't, we're at a boundary
            if (currentIsSafe != adjacentIsSafe) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all safe zone regions that apply to a location
     *
     * @param location The location to check
     * @return A set of regions that apply to the location
     */
    public ApplicableRegionSet getApplicableRegions(Location location) {
        if (!isWorldGuardAvailable() || location == null || location.getWorld() == null) {
            return null;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));

        if (regionManager == null) {
            return null;
        }

        BlockVector3 vector = BlockVector3.at(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        return regionManager.getApplicableRegions(vector);
    }

    /**
     * Default safe zone check used as fallback when WorldGuard isn't available
     *
     * @param location The location to check
     * @return true if the location is in the default safe zone
     */
    private boolean isDefaultSafeZone(Location location) {
        // Simple distance-based check from world spawn
        if (location.getWorld().getName().equals("world")) {
            double distFromSpawn = location.distance(location.getWorld().getSpawnLocation());
            return distFromSpawn < 100;
        }
        return false;
    }

    /**
     * Listen for WorldGuard being enabled to initialize integration
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("WorldGuard") && !worldGuardEnabled) {
            YakRealms.log("WorldGuard detected. Initializing integration...");
            initialize();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
            event.setCancelled(true);
        }
    }
}
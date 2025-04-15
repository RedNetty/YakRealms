package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Enhanced hologram manager for vendors with better error handling and tracking
 */
public class VendorHologramManager {

    private final JavaPlugin plugin;
    private final VendorManager vendorManager;
    // Track which vendors have holograms for better state management
    private final Map<String, Long> activeHolograms = new ConcurrentHashMap<>();

    /**
     * Constructor
     *
     * @param plugin The main plugin instance
     */
    public VendorHologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);

        // Schedule a delayed task to refresh all holograms after server fully starts
        new BukkitRunnable() {
            @Override
            public void run() {
                refreshAllHolograms();

                // Set up periodic hologram validation (every 15 minutes)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        validateHolograms();
                    }
                }.runTaskTimer(plugin, 20 * 60 * 15, 20 * 60 * 15);
            }
        }.runTaskLater(plugin, 100L); // 5 seconds after initialization
    }

    /**
     * Refresh all vendor holograms
     */
    public void refreshAllHolograms() {
        plugin.getLogger().info("Refreshing all vendor holograms...");

        // First remove all existing holograms
        for (Vendor vendor : vendorManager.getVendors().values()) {
            HologramManager.removeHologram(vendor.getVendorId());
            activeHolograms.remove(vendor.getVendorId());
        }

        // Wait a tick to ensure removal completes
        new BukkitRunnable() {
            @Override
            public void run() {
                // Then recreate all holograms
                int created = 0;
                for (Vendor vendor : vendorManager.getVendors().values()) {
                    if (createHologram(vendor)) {
                        created++;
                    }
                }
                plugin.getLogger().info("Vendor hologram refresh complete. Created " + created + " holograms.");
            }
        }.runTaskLater(plugin, 5L);
    }

    /**
     * Create a hologram for a vendor
     *
     * @param vendor The vendor to create a hologram for
     * @return true if the hologram was created successfully
     */
    public boolean createHologram(Vendor vendor) {
        NPC npc = null;

        try {
            npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get NPC for vendor " + vendor.getVendorId() + ": " + e.getMessage());
            return false;
        }

        Location hologramLocation;

        if (npc != null && npc.isSpawned()) {
            // If NPC is spawned, use its current location
            hologramLocation = npc.getEntity().getLocation().clone().add(0, 2.2, 0);
        } else {
            // Otherwise use stored location
            Location vendorLocation = vendor.getLocation();
            if (vendorLocation != null) {
                hologramLocation = vendorLocation.clone().add(0, 2.2, 0);
            } else {
                // No valid location
                plugin.getLogger().warning("No valid location for vendor " + vendor.getVendorId());
                return false;
            }
        }

        // Use hologram lines from vendor, or default if none
        List<String> hologramLines = vendor.getHologramLines();
        if (hologramLines == null || hologramLines.isEmpty()) {
            hologramLines = getDefaultHologramLines(vendor);
        }

        // Create the hologram
        try {
            HologramManager.createOrUpdateHologram(
                    vendor.getVendorId(),
                    hologramLocation,
                    hologramLines,
                    0.30
            );
            // Track when this hologram was created
            activeHolograms.put(vendor.getVendorId(), System.currentTimeMillis());
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create hologram for vendor " + vendor.getVendorId() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get default hologram lines based on vendor type
     *
     * @param vendor The vendor
     * @return Default hologram lines
     */
    private List<String> getDefaultHologramLines(Vendor vendor) {
        List<String> lines = new ArrayList<>();
        String type = vendor.getVendorType();

        switch (type.toLowerCase()) {
            case "item":
            case "shop":
                lines.add(ChatColor.GOLD + ChatColor.ITALIC.toString() + "Item Vendor");
                break;
            case "fisherman":
                lines.add(ChatColor.GOLD + ChatColor.ITALIC.toString() + "Fisherman");
                break;
            case "book":
                lines.add(ChatColor.GOLD + ChatColor.ITALIC.toString() + "Book Vendor");
                break;
            case "upgrade":
                lines.add(ChatColor.YELLOW + ChatColor.ITALIC.toString() + "Permanent Upgrades");
                break;
            case "banker":
                lines.add(ChatColor.GOLD + ChatColor.ITALIC.toString() + "Bank Services");
                break;
            case "medic":
                lines.add(ChatColor.GREEN + ChatColor.ITALIC.toString() + "Healing Services");
                break;
            case "gambler":
                lines.add(ChatColor.GOLD + ChatColor.ITALIC.toString() + "Games of Chance");
                break;
            default:
                lines.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Vendor");
                break;
        }

        return lines;
    }

    /**
     * Periodically validate all holograms
     */
    private void validateHolograms() {
        plugin.getLogger().info("Validating vendor holograms...");

        int fixed = 0;
        int total = vendorManager.getVendors().size();

        for (Vendor vendor : vendorManager.getVendors().values()) {
            // Check if this vendor has a hologram
            if (!activeHolograms.containsKey(vendor.getVendorId())) {
                // Create missing hologram
                if (createHologram(vendor)) {
                    fixed++;
                    plugin.getLogger().info("Fixed missing hologram for vendor " + vendor.getVendorId());
                }
            } else {
                // Check if the NPC is spawned but hasn't been recently updated
                NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
                if (npc != null && npc.isSpawned()) {
                    // Only update if it's been more than 1 hour since last update
                    long lastUpdate = activeHolograms.get(vendor.getVendorId());
                    if (System.currentTimeMillis() - lastUpdate > 3600000) {
                        // Recreate the hologram to ensure it's positioned correctly
                        HologramManager.removeHologram(vendor.getVendorId());
                        if (createHologram(vendor)) {
                            fixed++;
                            plugin.getLogger().info("Refreshed outdated hologram for vendor " + vendor.getVendorId());
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("Vendor hologram validation complete. Fixed " + fixed + " out of " + total + " holograms.");
    }

    /**
     * Fix orphaned holograms in a specific world
     *
     * @param world The world to check
     */
    public void fixOrphanedHolograms(World world) {
        plugin.getLogger().info("Checking for orphaned vendor holograms in world: " + world.getName());

        // Get all managed vendors
        Map<String, Vendor> vendors = vendorManager.getVendors();

        // Schedule hologram validation
        new BukkitRunnable() {
            @Override
            public void run() {
                int fixed = 0;

                // Refresh all managed vendor holograms in this world
                for (Vendor vendor : vendors.values()) {
                    if (vendor.getLocation() != null &&
                            vendor.getLocation().getWorld() != null &&
                            vendor.getLocation().getWorld().equals(world)) {

                        // First remove any existing hologram
                        HologramManager.removeHologram(vendor.getVendorId());

                        // Then create a new one
                        if (createHologram(vendor)) {
                            fixed++;
                        }
                    }
                }

                plugin.getLogger().info("Fixed " + fixed + " vendor holograms in world " + world.getName());
            }
        }.runTaskLater(plugin, 40L); // Wait 2 seconds after server startup
    }

    /**
     * Update a specific vendor's hologram
     *
     * @param vendor The vendor to update
     * @return true if the update was successful
     */
    public boolean updateVendorHologram(Vendor vendor) {
        // Remove existing hologram
        HologramManager.removeHologram(vendor.getVendorId());
        activeHolograms.remove(vendor.getVendorId());

        // Create new hologram
        return createHologram(vendor);
    }

    /**
     * Get statistics about hologram status
     *
     * @return Map of statistics
     */
    public Map<String, Integer> getHologramStats() {
        Map<String, Integer> stats = new HashMap<>();

        stats.put("totalVendors", vendorManager.getVendors().size());
        stats.put("activeHolograms", activeHolograms.size());

        return stats;
    }
}
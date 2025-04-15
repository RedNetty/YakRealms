package com.rednetty.server.mechanics.economy.vendors;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Factory class for creating common vendor types
 */
public class VendorFactory {

    private final VendorManager vendorManager;
    private final JavaPlugin plugin;

    /**
     * Constructor
     *
     * @param plugin The main plugin instance
     */
    public VendorFactory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance(plugin);
    }

    /**
     * Create an Item Vendor
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createItemVendor(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.GOLD + ChatColor.ITALIC.toString() + "Item Vendor"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.ItemVendorBehavior"
        );
    }

    /**
     * Create a Fisherman Vendor
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createFishermanVendor(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.GOLD + ChatColor.ITALIC.toString() + "Fisherman"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.FishermanBehavior"
        );
    }

    /**
     * Create a Book Vendor
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createBookVendor(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.GOLD + ChatColor.ITALIC.toString() + "Book Vendor"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.BookVendorBehavior"
        );
    }

    /**
     * Create an Upgrade Vendor
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createUpgradeVendor(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.YELLOW + ChatColor.ITALIC.toString() + "Permanent Upgrades"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.UpgradeVendorBehavior"
        );
    }

    /**
     * Create a Banker
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createBanker(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.GOLD + ChatColor.ITALIC.toString() + "Banker"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.BankerBehavior"
        );
    }

    /**
     * Create a Medic
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createMedic(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.GOLD + ChatColor.ITALIC.toString() + "Medic"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.MedicBehavior"
        );
    }

    /**
     * Create a Gambler
     *
     * @param vendorId Unique vendor ID
     * @param location Location to spawn the vendor
     * @return The created vendor
     */
    public Vendor createGambler(String vendorId, Location location) {
        List<String> hologramLines = Arrays.asList(
                ChatColor.GOLD + ChatColor.ITALIC.toString() + "Gambler"
        );

        return vendorManager.createVendor(
                vendorId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hologramLines,
                "com.rednetty.server.mechanics.economy.vendors.behaviors.GamblerBehavior"
        );
    }

    /**
     * Create predefined vendors for a world
     *
     * @param world The world to create vendors in
     */
    public void createDefaultVendors(World world) {
        // Only create vendors if none exist yet
        if (!vendorManager.getVendors().isEmpty()) {
            return;
        }

        plugin.getLogger().info("Creating default vendors in world: " + world.getName());

        try {
            // Example locations - you would customize these for your world
            Location spawnLoc = world.getSpawnLocation();

            // Create primary vendors near spawn
            createItemVendor("item_spawn", spawnLoc.clone().add(5, 0, 0));
            createFishermanVendor("fisherman_spawn", spawnLoc.clone().add(0, 0, 5));
            createBookVendor("book_spawn", spawnLoc.clone().add(-5, 0, 0));
            createUpgradeVendor("upgrade_spawn", spawnLoc.clone().add(0, 0, -5));
            createBanker("banker_spawn", spawnLoc.clone().add(8, 0, 8));
            createMedic("medic_spawn", spawnLoc.clone().add(-8, 0, -8));

            plugin.getLogger().info("Successfully created default vendors");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create default vendors: " + e.getMessage());
        }
    }
}
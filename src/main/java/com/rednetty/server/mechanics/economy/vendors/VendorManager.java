package com.rednetty.server.mechanics.economy.vendors;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Robust vendor manager with proper persistence and NPC recreation
 */
public class VendorManager {
    private static VendorManager instance;
    private final JavaPlugin plugin;
    private final Map<String, Vendor> vendors = new ConcurrentHashMap<>();
    private final Map<Integer, String> npcToVendor = new ConcurrentHashMap<>();
    private final Set<String> pendingVendors = ConcurrentHashMap.newKeySet();
    private final File configFile;
    private final File backupFile;
    private FileConfiguration config;
    private boolean isLoading = false;

    private VendorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "vendors.yml");
        this.backupFile = new File(plugin.getDataFolder(), "vendors_backup.yml");
        initializeFiles();
        loadConfig();
        // Delay loading to ensure Citizens is fully loaded
        new BukkitRunnable() {
            @Override
            public void run() {
                loadVendorsWithRetry();
            }
        }.runTaskLater(plugin, 20L); // 1 second delay
    }

    public static void initialize(JavaPlugin plugin) {
        if (instance == null) {
            instance = new VendorManager(plugin);
        }
    }

    public static VendorManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VendorManager not initialized!");
        }
        return instance;
    }

    /**
     * Create a vendor with full validation and persistence
     */
    public boolean createVendor(String id, String vendorType, String displayName, Location location) {
        if (id == null || id.trim().isEmpty()) {
            plugin.getLogger().warning("Cannot create vendor with null/empty ID");
            return false;
        }

        if (vendors.containsKey(id)) {
            plugin.getLogger().warning("Vendor with ID '" + id + "' already exists");
            return false;
        }

        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Cannot create vendor with invalid location");
            return false;
        }

        try {
            // Ensure Citizens is available
            if (!isCitizensAvailable()) {
                plugin.getLogger().severe("Citizens plugin not available - cannot create vendor");
                return false;
            }

            // Create NPC with error handling
            NPCRegistry registry = CitizensAPI.getNPCRegistry();
            NPC npc = registry.createNPC(EntityType.PLAYER, displayName != null ? displayName : "Vendor");

            if (npc == null) {
                plugin.getLogger().severe("Failed to create NPC for vendor " + id);
                return false;
            }

            // Spawn NPC with validation
            if (!npc.spawn(location)) {
                plugin.getLogger().severe("Failed to spawn NPC for vendor " + id);
                npc.destroy();
                return false;
            }

            // Create hologram lines based on type
            List<String> hologramLines = getDefaultHologramLines(vendorType);

            // Create and store vendor
            Vendor vendor = new Vendor(id, npc.getId(), location, vendorType, hologramLines);
            vendors.put(id, vendor);
            npcToVendor.put(npc.getId(), id);

            // Save immediately with error handling
            if (!saveVendorsToFile()) {
                plugin.getLogger().severe("Failed to save vendor " + id + " to file");
                // Rollback
                deleteVendor(id);
                return false;
            }

            plugin.getLogger().info("Successfully created vendor: " + id + " (NPC ID: " + npc.getId() + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Exception while creating vendor " + id, e);
            return false;
        }
    }

    /**
     * Delete a vendor with proper cleanup
     */
    public boolean deleteVendor(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        Vendor vendor = vendors.remove(id);
        if (vendor == null) {
            plugin.getLogger().warning("Attempted to delete non-existent vendor: " + id);
            return false;
        }

        try {
            // Remove from mappings
            npcToVendor.remove(vendor.getNpcId());
            pendingVendors.remove(id);

            // Remove NPC with error handling
            if (isCitizensAvailable()) {
                NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
                if (npc != null) {
                    npc.despawn();
                    npc.destroy();
                    plugin.getLogger().info("Removed NPC " + vendor.getNpcId() + " for vendor " + id);
                }
            }

            // Save changes
            saveVendorsToFile();
            plugin.getLogger().info("Successfully deleted vendor: " + id);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Exception while deleting vendor " + id, e);
            // Re-add vendor to prevent data loss
            vendors.put(id, vendor);
            npcToVendor.put(vendor.getNpcId(), id);
            return false;
        }
    }

    /**
     * Get vendor by ID
     */
    public Vendor getVendor(String id) {
        return vendors.get(id);
    }

    /**
     * Get vendor by NPC ID
     */
    public Vendor getVendorByNpcId(int npcId) {
        String vendorId = npcToVendor.get(npcId);
        return vendorId != null ? vendors.get(vendorId) : null;
    }

    /**
     * Get all vendors
     */
    public Collection<Vendor> getAllVendors() {
        return new ArrayList<>(vendors.values());
    }

    /**
     * Initialize configuration files
     */
    private void initializeFiles() {
        try {
            plugin.getDataFolder().mkdirs();

            if (!configFile.exists()) {
                configFile.createNewFile();
                plugin.getLogger().info("Created new vendors.yml file");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize vendor files", e);
        }
    }

    /**
     * Load configuration with error handling
     */
    private void loadConfig() {
        try {
            if (configFile.exists()) {
                config = YamlConfiguration.loadConfiguration(configFile);
                // Validate config structure
                if (config.get("vendors") == null) {
                    config.createSection("vendors");
                }
            } else {
                config = new YamlConfiguration();
                config.createSection("vendors");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load vendor config", e);
            config = new YamlConfiguration();
            config.createSection("vendors");
        }
    }

    /**
     * Load vendors with retry mechanism for world loading
     */
    private void loadVendorsWithRetry() {
        new BukkitRunnable() {
            private final int maxAttempts = 10;
            private int attempts = 0;

            @Override
            public void run() {
                attempts++;

                if (!isCitizensAvailable()) {
                    if (attempts >= maxAttempts) {
                        plugin.getLogger().severe("Citizens not available after " + maxAttempts + " attempts - vendor loading failed");
                        cancel();
                        return;
                    }
                    plugin.getLogger().warning("Citizens not yet available, retrying... (attempt " + attempts + "/" + maxAttempts + ")");
                    return;
                }

                int loaded = loadVendors();
                int pending = pendingVendors.size();

                if (pending == 0 || attempts >= maxAttempts) {
                    plugin.getLogger().info("Vendor loading completed: " + loaded + " loaded, " + pending + " failed");
                    if (pending > 0) {
                        plugin.getLogger().warning("Failed to load " + pending + " vendors due to world issues: " + pendingVendors);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 60L); // Check every 3 seconds
    }

    /**
     * Load vendors from configuration
     */
    private int loadVendors() {
        if (isLoading) {
            return 0;
        }

        isLoading = true;
        int loadedCount = 0;

        try {
            ConfigurationSection vendorsSection = config.getConfigurationSection("vendors");
            if (vendorsSection == null) {
                plugin.getLogger().info("No vendors to load");
                return 0;
            }

            Set<String> vendorIds = new HashSet<>(vendorsSection.getKeys(false));
            vendorIds.addAll(pendingVendors); // Include previously failed vendors

            for (String vendorId : vendorIds) {
                if (vendors.containsKey(vendorId)) {
                    continue; // Already loaded
                }

                try {
                    if (loadSingleVendor(vendorId)) {
                        loadedCount++;
                        pendingVendors.remove(vendorId);
                    } else {
                        pendingVendors.add(vendorId);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load vendor " + vendorId, e);
                    pendingVendors.add(vendorId);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Critical error during vendor loading", e);
        } finally {
            isLoading = false;
        }

        return loadedCount;
    }

    /**
     * Load a single vendor with full validation
     */
    private boolean loadSingleVendor(String vendorId) {
        String path = "vendors." + vendorId;

        try {
            // Validate required fields
            if (!config.contains(path + ".npcId") || !config.contains(path + ".world")) {
                plugin.getLogger().warning("Vendor " + vendorId + " missing required fields");
                return false;
            }

            int storedNpcId = config.getInt(path + ".npcId");
            String worldName = config.getString(path + ".world");
            double x = config.getDouble(path + ".x");
            double y = config.getDouble(path + ".y");
            double z = config.getDouble(path + ".z");
            float yaw = (float) config.getDouble(path + ".yaw");
            float pitch = (float) config.getDouble(path + ".pitch");
            String vendorType = config.getString(path + ".type", "shop");
            List<String> hologramLines = config.getStringList(path + ".hologramLines");

            // Validate world
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World '" + worldName + "' not found for vendor " + vendorId);
                return false;
            }

            Location location = new Location(world, x, y, z, yaw, pitch);

            // Handle NPC recreation
            NPC npc = CitizensAPI.getNPCRegistry().getById(storedNpcId);
            int actualNpcId = storedNpcId;

            if (npc == null) {
                // NPC doesn't exist, create a new one
                plugin.getLogger().info("Recreating missing NPC for vendor " + vendorId);
                npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Vendor");
                if (npc == null) {
                    plugin.getLogger().severe("Failed to recreate NPC for vendor " + vendorId);
                    return false;
                }
                actualNpcId = npc.getId();

                // Update stored NPC ID if it changed
                if (actualNpcId != storedNpcId) {
                    config.set(path + ".npcId", actualNpcId);
                    plugin.getLogger().info("Updated NPC ID for vendor " + vendorId + ": " + storedNpcId + " -> " + actualNpcId);
                }
            }

            // Ensure NPC is spawned at correct location
            if (!npc.isSpawned()) {
                if (!npc.spawn(location)) {
                    plugin.getLogger().warning("Failed to spawn NPC for vendor " + vendorId);
                    return false;
                }
            } else {
                // Update location if different
                Location currentLoc = npc.getEntity().getLocation();
                if (!locationsEqual(currentLoc, location)) {
                    npc.teleport(location, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }

            // Create vendor object
            Vendor vendor = new Vendor(vendorId, actualNpcId, location, vendorType,
                    hologramLines.isEmpty() ? getDefaultHologramLines(vendorType) : hologramLines);

            // Store vendor
            vendors.put(vendorId, vendor);
            npcToVendor.put(actualNpcId, vendorId);

            plugin.getLogger().info("Successfully loaded vendor: " + vendorId + " (NPC ID: " + actualNpcId + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Exception loading vendor " + vendorId, e);
            return false;
        }
    }

    /**
     * Save vendors to file with atomic operation
     */
    private boolean saveVendorsToFile() {
        try {
            // Create backup if file exists
            if (configFile.exists()) {
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Create temporary file
            File tempFile = new File(configFile.getParent(), "vendors_temp.yml");
            YamlConfiguration tempConfig = new YamlConfiguration();

            // Clear and rebuild vendors section
            ConfigurationSection vendorsSection = tempConfig.createSection("vendors");

            for (Vendor vendor : vendors.values()) {
                String path = "vendors." + vendor.getId();
                vendorsSection.set(vendor.getId() + ".npcId", vendor.getNpcId());
                vendorsSection.set(vendor.getId() + ".type", vendor.getVendorType());
                vendorsSection.set(vendor.getId() + ".world", vendor.getLocation().getWorld().getName());
                vendorsSection.set(vendor.getId() + ".x", vendor.getLocation().getX());
                vendorsSection.set(vendor.getId() + ".y", vendor.getLocation().getY());
                vendorsSection.set(vendor.getId() + ".z", vendor.getLocation().getZ());
                vendorsSection.set(vendor.getId() + ".yaw", vendor.getLocation().getYaw());
                vendorsSection.set(vendor.getId() + ".pitch", vendor.getLocation().getPitch());
                vendorsSection.set(vendor.getId() + ".hologramLines", vendor.getHologramLines());
            }

            // Save to temp file
            tempConfig.save(tempFile);

            // Atomic move
            Files.move(tempFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Update in-memory config
            config = tempConfig;

            plugin.getLogger().fine("Successfully saved " + vendors.size() + " vendors");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save vendors", e);

            // Attempt to restore from backup
            if (backupFile.exists()) {
                try {
                    Files.copy(backupFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Restored vendors.yml from backup");
                } catch (Exception backupError) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to restore from backup", backupError);
                }
            }
            return false;
        }
    }

    /**
     * Get default hologram lines for vendor type
     */
    private List<String> getDefaultHologramLines(String vendorType) {
        switch (vendorType.toLowerCase()) {
            case "item":
                return List.of(ChatColor.GOLD + "" + ChatColor.BOLD + "Item Vendor");
            case "fisherman":
                return List.of(ChatColor.AQUA + "" + ChatColor.BOLD + "Fisherman");
            case "book":
                return List.of(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Book Vendor");
            case "upgrade":
                return List.of(ChatColor.YELLOW + "" + ChatColor.BOLD + "Upgrade Vendor");
            case "banker":
                return List.of(ChatColor.GREEN + "" + ChatColor.BOLD + "Banker");
            case "medic":
                return List.of(ChatColor.RED + "" + ChatColor.BOLD + "Medic");
            case "gambler":
                return List.of(ChatColor.GOLD + "" + ChatColor.BOLD + "Gambler");
            case "mount":
                return List.of(ChatColor.YELLOW + "" + ChatColor.BOLD + "Mount Vendor");
            default:
                return List.of(ChatColor.GRAY + "" + ChatColor.BOLD + "Vendor");
        }
    }

    /**
     * Check if Citizens plugin is available and ready
     */
    private boolean isCitizensAvailable() {
        try {
            return CitizensAPI.getNPCRegistry() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Compare two locations for equality (ignoring minor floating point differences)
     */
    private boolean locationsEqual(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return false;
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;

        double threshold = 0.1;
        return Math.abs(loc1.getX() - loc2.getX()) < threshold &&
                Math.abs(loc1.getY() - loc2.getY()) < threshold &&
                Math.abs(loc1.getZ() - loc2.getZ()) < threshold;
    }

    /**
     * Reload vendor system
     */
    public void reload() {
        plugin.getLogger().info("Reloading vendor system...");

        // Clear current data
        vendors.clear();
        npcToVendor.clear();
        pendingVendors.clear();

        // Reload config and vendors
        loadConfig();
        loadVendorsWithRetry();
    }

    /**
     * Get system statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVendors", vendors.size());
        stats.put("pendingVendors", pendingVendors.size());
        stats.put("isLoading", isLoading);
        stats.put("citizensAvailable", isCitizensAvailable());
        return stats;
    }

    /**
     * Manual save trigger
     */
    public boolean saveVendors() {
        return saveVendorsToFile();
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down vendor system...");
        saveVendorsToFile();
        vendors.clear();
        npcToVendor.clear();
        pendingVendors.clear();
    }
}
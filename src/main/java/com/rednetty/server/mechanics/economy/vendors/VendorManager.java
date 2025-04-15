package com.rednetty.server.mechanics.economy.vendors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.vendors.visuals.VendorAuraManager;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import com.rednetty.server.mechanics.economy.vendors.config.VendorConfiguration;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages the loading, saving, and runtime control of Vendors.
 * Integrates with Citizens NPCs, HologramManager for floating text, and
 * VendorAuraManager for visual and sound effects.
 * Enhanced with validation, auto-repair, and backup capabilities.
 */
public class VendorManager implements Listener {

    private static VendorManager instance;

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    // vendorId -> Vendor
    private final Map<String, Vendor> vendors = new ConcurrentHashMap<>();
    private boolean citizensAvailable = false;

    // Configuration data
    private VendorConfiguration vendorConfig;

    // Metrics and debugging
    private int vendorsLoaded = 0;
    private int vendorsFixed = 0;
    private int totalSaves = 0;
    private long lastSaveTime = 0;
    private final List<String> recentErrors = new ArrayList<>();

    private VendorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // We'll store data in {pluginFolder}/vendors.yml
        this.configFile = new File(plugin.getDataFolder(), "vendors.yml");
    }

    /**
     * Get (or create) the singleton VendorManager instance.
     */
    public static VendorManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new VendorManager(plugin);
        }
        return instance;
    }

    /**
     * Check if Citizens API is available and functioning
     *
     * @return true if Citizens API is available
     */
    private boolean isCitizensAvailable() {
        try {
            // First check if the Citizens plugin is present and enabled
            Plugin citizensPlugin = Bukkit.getPluginManager().getPlugin("Citizens");
            if (citizensPlugin == null || !citizensPlugin.isEnabled()) {
                return false;
            }

            // Then try accessing a public method of CitizensAPI to check functionality
            CitizensAPI.getNPCRegistry();
            return true;
        } catch (Exception e) {
            // Log the specific error if debug mode is enabled
            if (isDebugMode()) {
                plugin.getLogger().warning("Citizens API check failed: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Check if debug mode is enabled
     *
     * @return true if debug mode is enabled
     */
    private boolean isDebugMode() {
        return vendorConfig != null && vendorConfig.getBoolean("debug-mode");
    }

    /**
     * Initialize the manager: load config, register events, etc.
     * Call this in your plugin's onEnable().
     */
    public void initialize() {
        // Load vendor configuration
        vendorConfig = VendorConfiguration.getInstance(YakRealms.getInstance());

        // Check if Citizens is available
        citizensAvailable = isCitizensAvailable();

        if (!citizensAvailable) {
            plugin.getLogger().warning("Citizens API not available. Vendor NPCs will not be functional.");
        }

        loadConfigFile();

        if (citizensAvailable) {
            try {
                loadVendorsFromConfig();

                // Run auto validation if enabled
                if (vendorConfig.getBoolean("auto-fix-behaviors")) {
                    int issuesFixed = validateAndFixVendors();
                    if (issuesFixed > 0) {
                        plugin.getLogger().info("Auto-repaired " + issuesFixed + " vendor issues on startup");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading vendors: " + e.getMessage());
                e.printStackTrace();
                // Add to recent errors
                recentErrors.add("Loading error: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("Deferring vendor loading until Citizens is available");
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Register the interaction handler
        new VendorInteractionHandler(plugin);
    }

    /**
     * Attempt to load vendors when Citizens becomes available
     */
    public void retryInitialization() {
        if (!citizensAvailable && isCitizensAvailable()) {
            citizensAvailable = true;
            plugin.getLogger().info("Citizens API now available. Loading vendors...");

            try {
                loadVendorsFromConfig();

                // Validate vendors after loading
                if (vendorConfig.getBoolean("auto-fix-behaviors")) {
                    int issuesFixed = validateAndFixVendors();
                    if (issuesFixed > 0) {
                        plugin.getLogger().info("Fixed " + issuesFixed + " vendor issues during retry initialization");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error during retry initialization: " + e.getMessage());
                e.printStackTrace();
                // Add to recent errors
                recentErrors.add("Retry init error: " + e.getMessage());
            }
        }
    }

    /**
     * Called in plugin onDisable() to save vendor data and clean up.
     */
    public void shutdown() {
        try {
            saveVendorsToConfig();
            HologramManager.cleanup(); // remove all holograms
            vendors.clear();
            plugin.getLogger().info("Vendor system shutdown complete");
        } catch (Exception e) {
            plugin.getLogger().severe("Error during vendor system shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reload vendors from config
     */
    public void reload() {
        try {
            vendors.clear();
            loadConfigFile();
            loadVendorsFromConfig();

            // Validate vendors after loading
            if (vendorConfig.getBoolean("auto-fix-behaviors")) {
                int issuesFixed = validateAndFixVendors();
                if (issuesFixed > 0) {
                    plugin.getLogger().info("Fixed " + issuesFixed + " vendor issues during reload");
                }
            }

            // Restart aura effects
            VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
            if (auraManager != null) {
                auraManager.stopAllAuras();
                auraManager.startAllAuras();
            }

            plugin.getLogger().info("Vendor system reloaded successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("Error reloading vendor system: " + e.getMessage());
            e.printStackTrace();
            // Add to recent errors
            recentErrors.add("Reload error: " + e.getMessage());
        }
    }

    // ================== CITIZENS EVENTS ==================

    /**
     * When a Citizens NPC is spawned, if it matches one of our vendors, we create/update the hologram.
     */
    @EventHandler
    public void onNPCSpawn(NPCSpawnEvent event) {
        if (!citizensAvailable) return;

        try {
            NPC npc = event.getNPC();
            Vendor vendor = getVendorByNpcId(npc.getId());
            if (vendor != null) {
                // Update vendor location
                vendor.setLocation(npc.getStoredLocation());

                // Create/update hologram
                createOrUpdateHologram(vendor);

                // Restart aura if needed
                VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
                if (auraManager != null) {
                    auraManager.updateVendorAura(vendor);
                }

                if (isDebugMode()) {
                    plugin.getLogger().info("NPC spawned for vendor " + vendor.getVendorId());
                }
            }
        } catch (Exception e) {
            if (isDebugMode()) {
                plugin.getLogger().warning("Error in NPC spawn event: " + e.getMessage());
            }
        }
    }

    /**
     * When a Citizens NPC is removed, if it matches one of our vendors, remove the hologram.
     */
    @EventHandler
    public void onNPCRemove(NPCRemoveEvent event) {
        if (!citizensAvailable) return;

        try {
            NPC npc = event.getNPC();
            Vendor vendor = getVendorByNpcId(npc.getId());
            if (vendor != null) {
                HologramManager.removeHologram(vendor.getVendorId());


                if (isDebugMode()) {
                    plugin.getLogger().info("NPC removed for vendor " + vendor.getVendorId());
                }
            }
        } catch (Exception e) {
            if (isDebugMode()) {
                plugin.getLogger().warning("Error in NPC remove event: " + e.getMessage());
            }
        }
    }

    // ================== HOLOGRAM UTILS ==================

    private void createOrUpdateHologram(Vendor vendor) {
        if (!citizensAvailable) return;

        try {
            NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
            if (npc != null && npc.isSpawned()) {
                double hologramHeight = vendorConfig.getDouble("hologram-height");
                if (hologramHeight <= 0) hologramHeight = 2.8;

                Location loc = npc.getStoredLocation().clone().add(0, hologramHeight, 0);

                double lineSpacing = vendorConfig.getDouble("hologram-line-spacing");
                if (lineSpacing <= 0) lineSpacing = 0.3;

                HologramManager.createOrUpdateHologram(
                        vendor.getVendorId(),
                        loc,
                        vendor.getHologramLines(),
                        lineSpacing
                );
            } else {
                // fallback to stored location
                Location fallback = vendor.getLocation();
                if (fallback != null) {
                    double hologramHeight = vendorConfig.getDouble("hologram-height");
                    if (hologramHeight <= 0) hologramHeight = 2.8;

                    Location hologramLocation = fallback.clone().add(0, hologramHeight, 0);

                    double lineSpacing = vendorConfig.getDouble("hologram-line-spacing");
                    if (lineSpacing <= 0) lineSpacing = 0.3;

                    HologramManager.createOrUpdateHologram(
                            vendor.getVendorId(),
                            hologramLocation,
                            vendor.getHologramLines(),
                            lineSpacing
                    );
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create/update hologram for vendor: " + vendor.getVendorId(), e);
            // Add to recent errors
            recentErrors.add("Hologram error for " + vendor.getVendorId() + ": " + e.getMessage());
        }
    }

    // ================== PUBLIC API ==================

    /**
     * Registers a vendor directly without creating a new NPC
     * Used when a NPC has already been created
     *
     * @param vendor The vendor to register
     */
    public void registerVendor(Vendor vendor) {
        if (vendor == null || !vendor.isValid()) {
            throw new IllegalArgumentException("Cannot register invalid vendor");
        }

        vendors.put(vendor.getVendorId(), vendor);

    }

    /**
     * Creates a new vendor, spawns its NPC, stores it in memory.
     * This won't immediately save to config unless you call saveVendorsToConfig().
     *
     * @return The newly created Vendor object or null if Citizens is not available
     */
    public Vendor createVendor(String vendorId,
                               String worldName,
                               double x, double y, double z,
                               float yaw, float pitch,
                               List<String> hologramLines,
                               String behaviorClass) {

        Vendor vendor = createVendorWithoutAura(vendorId, worldName, x, y, z, yaw, pitch, hologramLines, behaviorClass);

        return vendor;
    }

    /**
     * Internal method to create a vendor without starting aura
     * Same implementation as the original createVendor method
     */
    private Vendor createVendorWithoutAura(String vendorId,
                                           String worldName,
                                           double x, double y, double z,
                                           float yaw, float pitch,
                                           List<String> hologramLines,
                                           String behaviorClass) {
        if (!citizensAvailable) {
            plugin.getLogger().warning("Cannot create vendor: Citizens API not available");
            return null;
        }

        if (vendors.containsKey(vendorId)) {
            throw new IllegalArgumentException("A vendor with ID '" + vendorId + "' already exists!");
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("World not found: " + worldName);
        }

        try {
            // Validate behavior class
            if (behaviorClass == null || behaviorClass.isEmpty()) {
                behaviorClass = vendorConfig.getString("default-behavior-class");
                if (behaviorClass.isEmpty()) {
                    behaviorClass = "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior";
                }
            }

            // Use default hologram lines if none provided
            if (hologramLines == null || hologramLines.isEmpty()) {
                hologramLines = vendorConfig.getStringList("default-hologram-text").stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());
            }

            // Spawn a Citizens NPC for this vendor
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                    org.bukkit.entity.EntityType.PLAYER,
                    vendorId // set display name
            );
            Location loc = new Location(world, x, y, z, yaw, pitch);
            npc.spawn(loc);

            int npcId = npc.getId();
            Vendor vendor = new Vendor(
                    vendorId,
                    npcId,
                    loc,
                    hologramLines,
                    behaviorClass
            );
            vendors.put(vendorId, vendor);

            // Immediately create the hologram
            createOrUpdateHologram(vendor);

            // Save to config
            saveVendorsToConfig();

            return vendor;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating vendor: " + e.getMessage(), e);
            // Add to recent errors
            recentErrors.add("Create vendor error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deletes an existing vendor by ID: removes from memory, config,
     * despawns & destroys its Citizens NPC, removes the hologram, and stops aura effects.
     *
     * @param vendorId The unique ID of the vendor
     * @return true if a vendor was found and deleted, false otherwise
     */
    public boolean deleteVendor(String vendorId) {
        // First stop aura effects
        VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();

        Vendor vendor = vendors.remove(vendorId);
        if (vendor == null) {
            return false;
        }

        // Remove hologram
        HologramManager.removeHologram(vendorId);

        // Only attempt to remove from Citizens if the API is available
        if (citizensAvailable) {
            try {
                // Remove from Citizens
                NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
                if (npc != null) {
                    npc.despawn();
                    npc.destroy();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error removing Citizens NPC: " + e.getMessage(), e);
                // Add to recent errors
                recentErrors.add("Delete vendor error: " + e.getMessage());
            }
        }

        // Save changes to config
        saveVendorsToConfig();

        return true;
    }

    /**
     * Update a vendor's behavior class and refresh its aura
     */
    public void updateVendorBehavior(String vendorId, String behaviorClass) {
        Vendor vendor = getVendor(vendorId);
        if (vendor == null) {
            return;
        }

        // Store old type
        String oldType = vendor.getVendorType();

        // Update behavior
        vendor.setBehaviorClass(behaviorClass);

        // Check if type changed
        if (!oldType.equals(vendor.getVendorType())) {
            // Update aura if vendor type changed
            VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
            if (auraManager != null) {
                auraManager.updateVendorAura(vendor);
            }
        }

        // Save to config
        saveVendorsToConfig();
    }

    /**
     * Save all vendor data to the vendors.yml config.
     * Usually called during onDisable().
     */
    public void saveVendorsToConfig() {
        config.set("vendors", null); // clear old data

        for (Vendor vendor : vendors.values()) {
            String path = "vendors." + vendor.getVendorId();
            config.set(path + ".npcId", vendor.getNpcId());

            Location loc = vendor.getLocation();
            if (loc != null && loc.getWorld() != null) {
                config.set(path + ".world", loc.getWorld().getName());
                config.set(path + ".x", loc.getX());
                config.set(path + ".y", loc.getY());
                config.set(path + ".z", loc.getZ());
                config.set(path + ".yaw", loc.getYaw());
                config.set(path + ".pitch", loc.getPitch());
            }
            config.set(path + ".lines", vendor.getHologramLines());
            config.set(path + ".behaviorClass", vendor.getBehaviorClass());
            config.set(path + ".lastUpdated", vendor.getLastUpdated());
        }

        try {
            config.save(configFile);
            totalSaves++;
            lastSaveTime = System.currentTimeMillis();

            if (isDebugMode()) {
                plugin.getLogger().info("Saved " + vendors.size() + " vendors to config");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save vendors.yml", e);
            // Add to recent errors
            recentErrors.add("Save config error: " + e.getMessage());
        }
    }

    /**
     * Retrieves a vendor by its vendorId.
     */
    public Vendor getVendor(String vendorId) {
        return vendors.get(vendorId);
    }

    /**
     * Retrieves a vendor by Citizens npcId, or null if none.
     */
    public Vendor getVendorByNpcId(int npcId) {
        for (Vendor v : vendors.values()) {
            if (v.getNpcId() == npcId) return v;
        }
        return null;
    }

    /**
     * @return A read-only view of all known vendors.
     */
    public Map<String, Vendor> getVendors() {
        return Collections.unmodifiableMap(vendors);
    }

    /**
     * Validate all vendors and fix issues
     * @return The number of issues fixed
     */
    public int validateAndFixVendors() {
        int issuesFixed = 0;

        for (Map.Entry<String, Vendor> entry : vendors.entrySet()) {
            Vendor vendor = entry.getValue();
            boolean modified = false;

            // Check if behavior class exists
            String behaviorClass = vendor.getBehaviorClass();
            if (behaviorClass == null || behaviorClass.isEmpty()) {
                String vendorType = vendor.getVendorType();
                String newBehaviorClass = getDefaultBehaviorForType(vendorType);
                vendor.setBehaviorClass(newBehaviorClass);
                plugin.getLogger().warning("Fixed missing behavior class for vendor " + vendor.getVendorId() +
                        ". Set to " + newBehaviorClass);
                modified = true;
                issuesFixed++;
            } else {
                // Verify the behavior class can be instantiated
                try {
                    Class.forName(behaviorClass);
                } catch (ClassNotFoundException e) {
                    String vendorType = vendor.getVendorType();
                    String newBehaviorClass = getDefaultBehaviorForType(vendorType);
                    vendor.setBehaviorClass(newBehaviorClass);
                    plugin.getLogger().warning("Fixed invalid behavior class for vendor " + vendor.getVendorId() +
                            ". Changed from " + behaviorClass + " to " + newBehaviorClass);
                    modified = true;
                    issuesFixed++;
                }
            }

            // Check if vendor type matches behavior class
            String expectedType = extractTypeFromBehaviorClass(vendor.getBehaviorClass());
            if (!expectedType.equals(vendor.getVendorType())) {
                plugin.getLogger().warning("Vendor type mismatch for " + vendor.getVendorId() +
                        ". Type: " + vendor.getVendorType() +
                        ", Behavior suggests: " + expectedType);
                // Don't auto-fix this, just log it
            }

            // Check hologram lines
            if (vendor.getHologramLines() == null || vendor.getHologramLines().isEmpty()) {
                List<String> defaultLines = vendorConfig.getStringList("default-hologram-text").stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .collect(Collectors.toList());

                vendor.setHologramLines(defaultLines);
                plugin.getLogger().warning("Fixed missing hologram lines for vendor " + vendor.getVendorId());
                modified = true;
                issuesFixed++;
            }
        }

        if (issuesFixed > 0) {
            saveVendorsToConfig();
            vendorsFixed += issuesFixed;
        }

        return issuesFixed;
    }

    /**
     * Get the default behavior class for a vendor type
     * @param vendorType The vendor type
     * @return The default behavior class
     */
    public String getDefaultBehaviorForType(String vendorType) {
        String basePath = "com.rednetty.server.mechanics.economy.vendors.behaviors.";

        switch (vendorType.toLowerCase()) {
            case "item":
                return basePath + "ItemVendorBehavior";
            case "fisherman":
                return basePath + "FishermanBehavior";
            case "book":
                return basePath + "BookVendorBehavior";
            case "upgrade":
                return basePath + "UpgradeVendorBehavior";
            case "banker":
                return basePath + "BankerBehavior";
            case "medic":
                return basePath + "MedicBehavior";
            case "gambler":
                return basePath + "GamblerBehavior";
            default:
                return basePath + "ShopBehavior";
        }
    }

    /**
     * Extract vendor type from behavior class name
     * @param behaviorClass The behavior class name
     * @return The extracted vendor type
     */
    private String extractTypeFromBehaviorClass(String behaviorClass) {
        if (behaviorClass == null || behaviorClass.isEmpty()) {
            return "unknown";
        }

        // Extract class name without package
        String className = behaviorClass;
        if (behaviorClass.contains(".")) {
            className = behaviorClass.substring(behaviorClass.lastIndexOf('.') + 1);
        }

        // Remove "Behavior" suffix if present
        if (className.endsWith("Behavior")) {
            return className.substring(0, className.length() - 8).toLowerCase();
        } else {
            return "unknown";
        }
    }

    /**
     * Back up current vendor configuration
     * @return true if backup was successful
     */
    public boolean backupVendorConfig() {
        try {
            // Create a timestamped backup file
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File backupFile = new File(plugin.getDataFolder(), "vendors_backup_" + timestamp + ".yml");

            // Save current config to the backup file
            config.save(backupFile);

            plugin.getLogger().info("Created vendor config backup: " + backupFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create vendor config backup", e);
            // Add to recent errors
            recentErrors.add("Backup error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Restore vendor configuration from a backup
     * @param backupFileName The name of the backup file
     * @return true if restore was successful
     */
    public boolean restoreVendorConfig(String backupFileName) {
        try {
            File backupFile = new File(plugin.getDataFolder(), backupFileName);

            if (!backupFile.exists()) {
                plugin.getLogger().warning("Backup file not found: " + backupFileName);
                return false;
            }

            // Load the backup file
            FileConfiguration backupConfig = YamlConfiguration.loadConfiguration(backupFile);

            // Save to the current config file
            backupConfig.save(configFile);

            // Reload the config
            config = YamlConfiguration.loadConfiguration(configFile);

            // Stop all auras first
            VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
            if (auraManager != null) {
                auraManager.stopAllAuras();
            }

            // Reload vendors
            vendors.clear();
            loadVendorsFromConfig();

            // Restart auras
            if (auraManager != null) {
                auraManager.startAllAuras();
            }

            plugin.getLogger().info("Restored vendor config from backup: " + backupFileName);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to restore vendor config from backup", e);
            // Add to recent errors
            recentErrors.add("Restore error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get metrics and statistics about the vendor system
     *
     * @return A map of statistics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        metrics.put("vendorsCount", vendors.size());
        metrics.put("vendorsLoaded", vendorsLoaded);
        metrics.put("vendorsFixed", vendorsFixed);
        metrics.put("totalSaves", totalSaves);
        metrics.put("lastSaveTime", lastSaveTime);
        metrics.put("errorCount", recentErrors.size());
        metrics.put("citizensAvailable", citizensAvailable);

        // Get aura stats if available
        VendorAuraManager auraManager = VendorSystemInitializer.getAuraManager();
        if (auraManager != null) {
            metrics.putAll(auraManager.getAuraStats());
        }

        return metrics;
    }

    /**
     * Get recent errors
     *
     * @return List of recent errors
     */
    public List<String> getRecentErrors() {
        return new ArrayList<>(recentErrors);
    }

    /**
     * Clear recent errors list
     */
    public void clearRecentErrors() {
        recentErrors.clear();
    }

    // ================== INTERNALS ==================

    /**
     * Ensure the vendors.yml file exists (or create it), then load its data.
     */
    private void loadConfigFile() {
        if (!configFile.exists()) {
            try {
                // If you ship a default vendors.yml in your jar, you can copy it:
                plugin.saveResource("vendors.yml", false);
            } catch (Exception e) {
                // If no default file, create an empty one
                try {
                    plugin.getDataFolder().mkdirs();
                    configFile.createNewFile();
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to create vendors.yml", ex);
                    // Add to recent errors
                    recentErrors.add("Config creation error: " + ex.getMessage());
                }
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Parse vendor data from config and spawn them if needed.
     */
    private void loadVendorsFromConfig() {
        if (!citizensAvailable) {
            plugin.getLogger().warning("Skipping vendor loading because Citizens is not available");
            return;
        }

        if (!config.contains("vendors")) {
            return;
        }

        try {
            int loadedCount = 0;
            Set<String> vendorIds = config.getConfigurationSection("vendors").getKeys(false);
            for (String vendorId : vendorIds) {
                String path = "vendors." + vendorId;
                int npcId = config.getInt(path + ".npcId", -1);
                String worldName = config.getString(path + ".world", "world");
                double x = config.getDouble(path + ".x", 0.0);
                double y = config.getDouble(path + ".y", 64.0);
                double z = config.getDouble(path + ".z", 0.0);
                float yaw = (float) config.getDouble(path + ".yaw", 0.0);
                float pitch = (float) config.getDouble(path + ".pitch", 0.0);
                List<String> lines = config.getStringList(path + ".lines");

                // Get behavior class with improved handling
                String behaviorClass = config.getString(path + ".behaviorClass");

                if (behaviorClass == null || behaviorClass.isEmpty()) {
                    plugin.getLogger().warning("No behavior class found for vendor " + vendorId + ". Using default ShopBehavior.");
                    behaviorClass = "com.rednetty.server.mechanics.economy.vendors.behaviors.ShopBehavior";
                }

                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    if (!Bukkit.getWorlds().isEmpty()) {
                        w = Bukkit.getWorlds().get(0);
                        plugin.getLogger().warning("World '" + worldName + "' not found for vendor " + vendorId +
                                ". Using " + w.getName() + " instead.");
                    } else {
                        plugin.getLogger().warning("No valid world for vendor " + vendorId + ". Skipping.");
                        continue; // no valid world to spawn
                    }
                }
                Location loc = new Location(w, x, y, z, yaw, pitch);

                Vendor vendor = new Vendor(vendorId, npcId, loc, lines, behaviorClass);
                vendors.put(vendorId, vendor);
                loadedCount++;

                try {
                    // If an NPC with npcId exists in the registry, spawn it
                    NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
                    if (npc != null && !npc.isSpawned()) {
                        npc.spawn(loc);
                    }
                    // If the NPC is spawned, create/update the hologram
                    if (npc != null && npc.isSpawned()) {
                        createOrUpdateHologram(vendor);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error spawning NPC for vendor: " + vendorId, e);
                    // Add to recent errors
                    recentErrors.add("NPC spawn error for " + vendorId + ": " + e.getMessage());
                }
            }

            vendorsLoaded = loadedCount;
            plugin.getLogger().info("Loaded " + loadedCount + " vendors from config");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading vendors from config", e);
            // Add to recent errors
            recentErrors.add("Vendor loading error: " + e.getMessage());
        }
    }

    /**
     * Force update of a vendor's hologram
     *
     * @param vendor The vendor to update
     */
    public void refreshHologram(Vendor vendor) {
        if (!citizensAvailable) return;

        HologramManager.removeHologram(vendor.getVendorId());
        createOrUpdateHologram(vendor);
    }

    /**
     * Check if there are players within the specified distance of a location
     * @param loc The center location
     * @param distance The radius to check
     * @return true if players are nearby
     */
    private boolean hasPlayersNearby(Location loc, double distance) {
        if (loc == null || loc.getWorld() == null) return false;

        double distanceSquared = distance * distance;

        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= distanceSquared) {
                return true;
            }
        }
        return false;
    }
}
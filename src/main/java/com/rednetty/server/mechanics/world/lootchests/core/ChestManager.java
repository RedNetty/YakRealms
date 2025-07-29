package com.rednetty.server.mechanics.world.lootchests.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.lootchests.persistence.ChestRepository;
import com.rednetty.server.mechanics.world.lootchests.loot.LootGenerator;
import com.rednetty.server.mechanics.world.lootchests.events.ChestEventHandler;
import com.rednetty.server.mechanics.world.lootchests.effects.ChestEffects;
import com.rednetty.server.mechanics.world.lootchests.config.ChestConfig;
import com.rednetty.server.mechanics.world.lootchests.types.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Central manager for the loot chest system
 * : Proper inventory tracking and state management
 * Updated with loot inventory reuse/persistence support, world load validation, stuck state fixing, and better synchronization.
 */
public class ChestManager {
    private static ChestManager instance;
    private final YakRealms plugin;
    private final Logger logger;

    // Core components
    private ChestRegistry registry;
    private ChestRepository repository;
    private LootGenerator lootGenerator;
    private ChestEventHandler eventHandler;
    private ChestEffects effects;
    private ChestConfig config;

    // Background tasks
    private BukkitTask maintenanceTask;

    // State tracking
    private boolean initialized = false;
    private long lastSave = 0;

    // : Prevent race conditions with synchronized access
    private final Object stateLock = new Object();

    private ChestManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the singleton instance
     */
    public static ChestManager getInstance() {
        if (instance == null) {
            instance = new ChestManager();
        }
        return instance;
    }

    /**
     * Initializes the chest manager and all components
     */
    public void initialize() {
        synchronized (stateLock) {
            if (initialized) {
                logger.warning("ChestManager is already initialized!");
                return;
            }

            try {
                logger.info("Initializing Chest Manager...");

                // Initialize components in dependency order
                config = new ChestConfig();
                repository = new ChestRepository();
                registry = new ChestRegistry();
                lootGenerator = new LootGenerator();
                effects = new ChestEffects();
                eventHandler = new ChestEventHandler(this);

                // Initialize all components
                config.initialize();
                repository.initialize();
                lootGenerator.initialize();
                effects.initialize();
                eventHandler.initialize();

                // Load existing chests from storage
                loadChests();

                // Register world event listener
                Bukkit.getPluginManager().registerEvents(new WorldEventListener(), plugin);

                // Start background maintenance task
                startMaintenanceTask();

                initialized = true;
                lastSave = System.currentTimeMillis();

                logger.info("Chest Manager initialized successfully with " + registry.size() + " chests loaded");

            } catch (Exception e) {
                logger.severe("Failed to initialize Chest Manager: " + e.getMessage());
                e.printStackTrace();
                // Partial initialization: disable persistence if repo failed
            }
        }
    }

    /**
     * Shuts down the chest manager and saves all data
     */
    public void shutdown() {
        synchronized (stateLock) {
            if (!initialized) {
                return;
            }

            logger.info("Shutting down Chest Manager...");

            try {
                // Cancel background tasks
                if (maintenanceTask != null) {
                    maintenanceTask.cancel();
                    maintenanceTask = null;
                }

                // Save all chests
                saveAllChests();

                // Clear registry
                registry.clear();

                // Shutdown event handler
                if (eventHandler != null) {
                    eventHandler.shutdown();
                }

                initialized = false;
                logger.info("Chest Manager shutdown completed successfully");

            } catch (Exception e) {
                logger.severe("Error during Chest Manager shutdown: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // === Chest Operations ===

    /**
     * Creates a new chest at the specified location
     */
    public Chest createChest(Location location, ChestTier tier, ChestType type) {
        if (!initialized) {
            throw new IllegalStateException("ChestManager not initialized");
        }
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Tier cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        ChestLocation chestLoc = new ChestLocation(location);

        synchronized (stateLock) {
            // Check if chest already exists
            if (registry.hasChest(chestLoc)) {
                logger.warning("Chest already exists at " + chestLoc);
                return registry.getChest(chestLoc);
            }

            try {
                // Create new chest
                Chest chest = new Chest(chestLoc, tier, type);
                registry.addChest(chest);

                // Set physical block
                setChestBlock(location, type);

                // Play creation effects
                effects.playCreationEffects(location, tier, type);

                // Save immediately to prevent data loss
                repository.saveChest(chest);

                logger.info("Created " + type + " chest (tier " + tier.getLevel() + ") at " + chestLoc);
                return chest;

            } catch (Exception e) {
                logger.severe("Failed to create chest at " + chestLoc + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * Removes a chest at the specified location
     */
    public boolean removeChest(ChestLocation location) {
        if (!initialized) {
            throw new IllegalStateException("ChestManager not initialized");
        }
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        synchronized (stateLock) {
            Chest chest = registry.removeChest(location);
            if (chest == null) {
                return false;
            }

            try {
                // Remove physical block
                Location bukkitLoc = location.getBukkitLocationSafe();
                if (bukkitLoc != null) {
                    bukkitLoc.getBlock().setType(Material.AIR);
                }

                // Play removal effects
                effects.playRemovalEffects(bukkitLoc, chest.getTier());

                // Delete from storage
                repository.deleteChest(location);

                logger.info("Removed chest at " + location);
                return true;

            } catch (Exception e) {
                logger.severe("Failed to remove chest at " + location + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * Opens a chest for a player
     * : Proper state validation and race condition prevention
     */
    public boolean openChest(Player player, ChestLocation location) {
        if (!initialized) {
            if (player != null) {
                player.sendMessage("§cChest system is not ready.");
            }
            return false;
        }
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        synchronized (stateLock) {
            Chest chest = registry.getChest(location);
            if (chest == null) {
                player.sendMessage("§cNo chest found at this location.");
                return false;
            }

            // : Check for proper state and prevent multiple access
            if (chest.getState() != ChestState.AVAILABLE) {
                String message = switch (chest.getState()) {
                    case AVAILABLE -> "";
                    case OPENED -> "§cThis chest is currently being accessed by another player.";
                    case RESPAWNING -> "§cThis chest is respawning. Time remaining: " + chest.getRespawnTimeRemainingFormatted();
                    case EXPIRED -> "§cThis chest has expired.";
                };
                player.sendMessage(message);
                return false;
            }

            // Check if player already has another chest open
            if (eventHandler.isPlayerViewingChest(player)) {
                player.sendMessage("§cPlease close your current chest before opening another one.");
                return false;
            }

            try {
                // Check for nearby mobs if enabled
                if (effects.hasNearbyMobs(location.getBukkitLocationSafe())) {
                    player.sendMessage("§cIt's not safe to open that right now!");
                    player.sendMessage("§7Eliminate the monsters in the area first.");
                    return false;
                }

                // Use existing inventory if available, else generate and set
                Inventory inventory;
                if (chest.getLootInventory() != null) {
                    inventory = chest.getLootInventory();
                } else {
                    inventory = lootGenerator.createLootInventory(chest);
                    chest.setLootInventory(inventory);
                }
                player.openInventory(inventory);

                // Update chest state AFTER successfully opening inventory
                chest.setState(ChestState.OPENED);
                chest.addInteraction(player);

                // Play opening effects
                effects.playOpenEffects(player, chest.getTier());

                // Save state change immediately
                repository.saveChest(chest);

                logger.fine("Player " + player.getName() + " opened chest at " + location);
                return true;

            } catch (Exception e) {
                // : Ensure chest state is reverted on error
                if (chest.getState() == ChestState.OPENED) {
                    chest.setState(ChestState.AVAILABLE);
                    repository.saveChest(chest);
                }

                logger.severe("Failed to open chest at " + location + " for player " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                player.sendMessage("§cAn error occurred while opening the chest.");
                return false;
            }
        }
    }

    /**
     * Breaks a chest (called when player breaks it)
     * : Prevent breaking opened chests and improve state validation
     */
    public boolean breakChest(Player player, ChestLocation location) {
        if (!initialized) {
            if (player != null) {
                player.sendMessage("§cChest system is not ready.");
            }
            return false;
        }
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        synchronized (stateLock) {
            Chest chest = registry.getChest(location);
            if (chest == null) {
                return false;
            }

            // : Prevent breaking chests that are currently opened
            if (chest.getState() == ChestState.OPENED) {
                player.sendMessage("§cThis chest is currently being accessed. Please wait for it to be closed first.");
                return false;
            }

            // Care packages cannot be broken
            if (chest.getType() == ChestType.CARE_PACKAGE) {
                player.sendMessage("§cCare packages cannot be broken!");
                return false;
            }

            // Only allow breaking available chests
            if (chest.getState() != ChestState.AVAILABLE) {
                player.sendMessage("§cThis chest is not available for breaking (" + chest.getState() + ")");
                return false;
            }

            try {
                // Check for nearby mobs
                if (effects.hasNearbyMobs(location.getBukkitLocationSafe())) {
                    player.sendMessage("§cIt's not safe to break that right now!");
                    player.sendMessage("§7Eliminate the monsters in the area first.");
                    return false;
                }

                // Drop loot at the location
                Location bukkitLoc = location.getBukkitLocationSafe();
                if (bukkitLoc != null) {
                    lootGenerator.dropLoot(bukkitLoc, chest);
                }

                // Clear inventory after dropping
                chest.clearLootInventory();

                // Handle different chest types
                if (chest.getType() == ChestType.SPECIAL) {
                    // Special chests are removed completely
                    removeChest(location);
                    player.sendMessage("§eSpecial chest destroyed! §7(These do not respawn)");
                } else {
                    // Normal chests respawn after a delay
                    scheduleRespawn(chest);
                    int respawnMinutes = config.getRespawnTime(chest.getTier()) / 60;
                    int respawnSeconds = config.getRespawnTime(chest.getTier()) % 60;
                    player.sendMessage("§aChest broken! It will respawn in " + respawnMinutes + "m " + respawnSeconds + "s.");
                }

                // Play breaking effects
                if (bukkitLoc != null) {
                    effects.playBreakEffects(bukkitLoc, chest.getTier());
                }

                logger.fine("Player " + player.getName() + " broke chest at " + location);
                return true;

            } catch (Exception e) {
                logger.severe("Failed to break chest at " + location + " for player " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
                player.sendMessage("§cAn error occurred while breaking the chest.");
                return false;
            }
        }
    }

    /**
     * Spawns a care package at the specified location
     */
    public Chest spawnCarePackage(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        Chest chest = createChest(location, ChestTier.LEGENDARY, ChestType.CARE_PACKAGE);
        if (chest != null) {
            // Broadcast to all players
            Bukkit.broadcastMessage("§6§lCARE PACKAGE §r§ehas dropped at §6" +
                    location.getBlockX() + ", " + location.getBlockZ() + "§e!");

            // Special care package effects
            effects.playCarePackageEffects(location);
        }
        return chest;
    }

    /**
     * Creates a special chest with limited duration
     */
    public Chest createSpecialChest(Location location, ChestTier tier) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Tier cannot be null");
        }
        Chest chest = createChest(location, tier, ChestType.SPECIAL);
        if (chest != null) {
            // Special chests have  loot and expire automatically
            logger.info("Created special chest (tier " + tier.getLevel() + ") at " + location);
        }
        return chest;
    }

    // === Private Helper Methods ===

    /**
     * Schedules a chest to respawn after the configured delay
     */
    private void scheduleRespawn(Chest chest) {
        if (chest == null) {
            throw new IllegalArgumentException("Chest cannot be null");
        }
        chest.setState(ChestState.RESPAWNING);
        int respawnSeconds = config.getRespawnTime(chest.getTier());
        chest.setRespawnTime(System.currentTimeMillis() + (respawnSeconds * 1000L));

        // Remove physical block
        Location bukkitLoc = chest.getLocation().getBukkitLocationSafe();
        if (bukkitLoc != null) {
            bukkitLoc.getBlock().setType(Material.AIR);
        }

        // Save state change
        repository.saveChest(chest);
    }

    /**
     * Loads all chests from persistent storage
     */
    private void loadChests() {
        try {
            Map<ChestLocation, Chest> loadedChests = repository.loadAllChests();

            for (Map.Entry<ChestLocation, Chest> entry : loadedChests.entrySet()) {
                Chest chest = entry.getValue();
                registry.addChest(chest);

                // Restore physical blocks for available chests if world loaded
                Location bukkitLoc = entry.getKey().getBukkitLocationSafe();
                if (bukkitLoc != null && chest.getState() == ChestState.AVAILABLE) {
                    setChestBlock(bukkitLoc, chest.getType());
                } else if (bukkitLoc == null) {
                    logger.info("Deferred block restore for chest at " + entry.getKey() + " - world not loaded");
                }
            }

            logger.info("Loaded " + loadedChests.size() + " chests from storage");

        } catch (Exception e) {
            logger.severe("Failed to load chests from storage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves all chests to persistent storage
     */
    private void saveAllChests() {
        try {
            repository.saveAllChests(registry.getAllChests());
            lastSave = System.currentTimeMillis();
            logger.fine("Saved all chests to storage");
        } catch (Exception e) {
            logger.severe("Failed to save chests: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sets the appropriate physical block for a chest type
     */
    private void setChestBlock(Location location, ChestType type) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        Material blockType = switch (type) {
            case CARE_PACKAGE -> Material.ENDER_CHEST;
            case SPECIAL -> Material.BARREL;
            default -> Material.CHEST;
        };
        location.getBlock().setType(blockType);
    }

    /**
     * Starts the background maintenance task
     */
    private void startMaintenanceTask() {
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                processMaintenance();
            } catch (Exception e) {
                logger.warning("Error in maintenance task: " + e.getMessage());
            }
        }, 100L, 100L); // Every 5 seconds

        logger.info("Started maintenance task");
    }

    /**
     * Processes periodic maintenance tasks
     * : Better error handling and state validation
     */
    private void processMaintenance() {
        if (!initialized) return;

        synchronized (stateLock) {
            try {
                // Get a snapshot of current chests
                Collection<Chest> chestsToProcess = new ArrayList<>(registry.getChests());

                // Single loop for efficiency: process respawns, expirations, and particles
                for (Chest chest : chestsToProcess) {
                    try {
                        Location bukkitLoc = chest.getLocation().getBukkitLocationSafe();
                        if (chest.isReadyToRespawn()) {
                            respawnChest(chest);
                        } else if (chest.isExpired()) {
                            // If opened, force close
                            if (chest.getState() == ChestState.OPENED) {
                                forceCloseChest(chest.getLocation());
                            }
                            removeChest(chest.getLocation());
                        } else if (chest.getState() == ChestState.AVAILABLE) {
                            if (bukkitLoc != null) {
                                effects.spawnAmbientParticles(bukkitLoc, chest.getTier());
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Error processing chest " + chest.getLocation() + ": " + e.getMessage());
                    }
                }

                // Fix stuck opened states
                List<Chest> invalid = getInvalidStateChests();
                for (Chest chest : invalid) {
                    chest.setState(ChestState.AVAILABLE);
                    repository.saveChest(chest);
                    logger.warning("Force closed stuck chest at " + chest.getLocation());
                }

                // Auto-save every 5 minutes
                if (System.currentTimeMillis() - lastSave > 300000) { // 5 minutes
                    saveAllChests();
                }

            } catch (Exception e) {
                logger.warning("Error in maintenance processing: " + e.getMessage());
            }
        }
    }

    /**
     * Respawns a chest that is ready
     */
    private void respawnChest(Chest chest) {
        try {
            chest.setState(ChestState.AVAILABLE);
            chest.resetRespawnTime();
            chest.clearLootInventory(); // Clear old inventory on respawn

            // Place physical block
            Location bukkitLoc = chest.getLocation().getBukkitLocationSafe();
            if (bukkitLoc != null) {
                setChestBlock(bukkitLoc, chest.getType());

                // Play respawn effects
                effects.playRespawnEffects(bukkitLoc, chest.getTier());
            }

            // Save state change
            repository.saveChest(chest);

            logger.fine("Respawned chest at " + chest.getLocation());

        } catch (Exception e) {
            logger.warning("Failed to respawn chest at " + chest.getLocation() + ": " + e.getMessage());
        }
    }

    /**
     * Validates and restores chest blocks in a specific world on load.
     * @param world The world to validate chests in.
     */
    public void validateChestsInWorld(World world) {
        synchronized (stateLock) {
            for (Chest chest : registry.getChests()) {
                if (chest.getLocation().getWorldName().equals(world.getName())) {
                    Location loc = chest.getLocation().getBukkitLocationSafe();
                    if (loc != null) {
                        Material expected = (chest.getState() == ChestState.AVAILABLE) ?
                                switch (chest.getType()) {
                                    case CARE_PACKAGE -> Material.ENDER_CHEST;
                                    case SPECIAL -> Material.BARREL;
                                    default -> Material.CHEST;
                                } : Material.AIR;
                        if (loc.getBlock().getType() != expected) {
                            loc.getBlock().setType(expected);
                            logger.info("Restored chest block at " + loc + " to " + expected);
                        }
                    }
                }
            }
        }
    }

    // === Public Getters ===

    public ChestRegistry getRegistry() {
        return registry;
    }

    public ChestRepository getRepository() {
        return repository;
    }

    public LootGenerator getLootGenerator() {
        return lootGenerator;
    }

    public ChestEventHandler getEventHandler() {
        return eventHandler;
    }

    public ChestEffects getEffects() {
        return effects;
    }

    public ChestConfig getConfig() {
        return config;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Object getStateLock() {
        return stateLock;
    }

    /**
     * Gets statistics about the chest system
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        if (!initialized) {
            stats.put("status", "Not Initialized");
            return stats;
        }

        synchronized (stateLock) {
            Map<ChestLocation, Chest> allChests = registry.getAllChests();
            stats.put("totalChests", allChests.size());

            // Count by state
            long availableCount = allChests.values().stream().filter(c -> c.getState() == ChestState.AVAILABLE).count();
            long openedCount = allChests.values().stream().filter(c -> c.getState() == ChestState.OPENED).count();
            long respawningCount = allChests.values().stream().filter(c -> c.getState() == ChestState.RESPAWNING).count();

            stats.put("availableChests", availableCount);
            stats.put("openedChests", openedCount);
            stats.put("respawningChests", respawningCount);

            // Count by tier
            for (ChestTier tier : ChestTier.values()) {
                long count = allChests.values().stream().filter(c -> c.getTier() == tier).count();
                stats.put("tier" + tier.getLevel() + "Chests", count);
            }

            // Count by type
            for (ChestType type : ChestType.values()) {
                long count = allChests.values().stream().filter(c -> c.getType() == type).count();
                stats.put(type.name().toLowerCase() + "Chests", count);
            }

            stats.put("lastSave", lastSave);
            stats.put("initialized", initialized);
        }

        return stats;
    }

    /**
     * : Force close a chest (admin utility)
     * Useful for resolving stuck chest states
     */
    public boolean forceCloseChest(ChestLocation location) {
        if (!initialized) return false;
        if (location == null) return false;

        synchronized (stateLock) {
            Chest chest = registry.getChest(location);
            if (chest == null) return false;

            if (chest.getState() == ChestState.OPENED) {
                // Find and close for player if viewing
                Player player = eventHandler.getViewingPlayer(location);
                if (player != null) {
                    player.closeInventory();
                }
                chest.setState(ChestState.AVAILABLE);
                repository.saveChest(chest);
                logger.info("Force closed chest at " + location);
                return true;
            }
            return false;
        }
    }

    /**
     * Gets chests that may be in an invalid state (debugging utility)
     */
    public List<Chest> getInvalidStateChests() {
        if (!initialized) return Collections.emptyList();

        synchronized (stateLock) {
            return registry.getAllChests().values().stream()
                    .filter(chest -> {
                        // Check for chests in OPENED state for too long (more than 30 minutes)
                        return chest.getState() == ChestState.OPENED &&
                                chest.getTimeSinceLastInteraction() > 1800000; // 30 minutes
                    })
                    .collect(Collectors.toList());
        }
    }

    // Inner class for world events
    private class WorldEventListener implements Listener {
        @EventHandler
        public void onWorldLoad(WorldLoadEvent event) {
            validateChestsInWorld(event.getWorld());
        }
    }
}
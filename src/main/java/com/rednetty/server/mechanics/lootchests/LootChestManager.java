package com.rednetty.server.mechanics.lootchests;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.lootchests.data.LootChestConfig;
import com.rednetty.server.mechanics.lootchests.data.LootChestData;
import com.rednetty.server.mechanics.lootchests.types.LootChestLocation;
import com.rednetty.server.mechanics.lootchests.storage.LootChestRepository;
import com.rednetty.server.mechanics.lootchests.types.ChestState;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Main manager for the loot chest system
 * Handles chest creation, management, and coordination between components
 */
public class LootChestManager {
    private static LootChestManager instance;
    private final YakRealms plugin;
    private final Logger logger;

    // Core components
    private LootChestHandler handler;
    private LootChestFactory factory;
    private LootChestNotifier notifier;
    private LootChestEffects effects;
    private LootChestRepository repository;
    private LootChestConfig config;

    // Active chest data
    private final Map<LootChestLocation, LootChestData> activeChests = new ConcurrentHashMap<>();
    private final Map<LootChestLocation, Inventory> openedChests = new ConcurrentHashMap<>();
    private final Map<Player, LootChestLocation> viewingPlayers = new ConcurrentHashMap<>();

    // Background tasks
    private BukkitTask particleTask;
    private BukkitTask respawnTask;
    private BukkitTask cleanupTask;

    // Constants
    private static final int PARTICLE_INTERVAL_TICKS = 5;
    private static final int RESPAWN_CHECK_INTERVAL_TICKS = 20; // 1 second
    private static final int CLEANUP_INTERVAL_TICKS = 1200; // 1 minute
    private static final int CARE_PACKAGE_SIZE = 9;
    private static final int SPECIAL_CHEST_SIZE = 27;

    /**
     * Private constructor for singleton pattern
     */
    private LootChestManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The LootChestManager instance
     */
    public static LootChestManager getInstance() {
        if (instance == null) {
            instance = new LootChestManager();
        }
        return instance;
    }

    /**
     * Initializes the loot chest system
     */
    public void initialize() {
        logger.info("Initializing loot chest system...");

        // Initialize components
        this.config = new LootChestConfig();
        this.repository = new LootChestRepository();
        this.factory = new LootChestFactory();
        this.notifier = new LootChestNotifier();
        this.effects = new LootChestEffects();
        this.handler = new LootChestHandler();

        // Initialize all components
        config.initialize();
        repository.initialize();
        factory.initialize();
        notifier.initialize();
        effects.initialize();
        handler.initialize();

        // Load existing chests from storage
        loadChestsFromStorage();

        // Start background tasks
        startBackgroundTasks();

        logger.info("Loot chest system initialized successfully");
    }

    /**
     * Shuts down the loot chest system
     */
    public void shutdown() {
        logger.info("Shutting down loot chest system...");

        // Stop background tasks
        stopBackgroundTasks();

        // Save all chest data
        saveChestsToStorage();

        // Clear active data
        clearAllChestInventories();
        activeChests.clear();
        viewingPlayers.clear();

        // Shutdown components
        if (handler != null) handler.shutdown();
        if (repository != null) repository.shutdown();

        logger.info("Loot chest system shut down successfully");
    }

    /**
     * Creates a new loot chest at the specified location
     *
     * @param location The location to create the chest
     * @param tier     The tier of the chest
     * @param type     The type of chest
     * @return The created chest data
     */
    public LootChestData createChest(Location location, ChestTier tier, ChestType type) {
        LootChestLocation chestLocation = new LootChestLocation(location);

        // Check if chest already exists at this location
        if (activeChests.containsKey(chestLocation)) {
            logger.warning("Attempted to create chest at location that already has one: " + chestLocation);
            return activeChests.get(chestLocation);
        }

        // Create chest data
        LootChestData chestData = new LootChestData(chestLocation, tier, type);
        activeChests.put(chestLocation, chestData);

        // Set physical chest block
        if (type == ChestType.CARE_PACKAGE) {
            location.getBlock().setType(Material.ENDER_CHEST);
        } else if (type == ChestType.SPECIAL) {
            location.getBlock().setType(Material.GLOWSTONE);
        } else {
            location.getBlock().setType(Material.CHEST);
        }

        // Create initial effects
        effects.spawnCreationEffects(location, tier, type);

        // Notify nearby players for special chests
        if (type == ChestType.CARE_PACKAGE) {
            notifier.broadcastCarePackageDrop(location);
        } else if (type == ChestType.SPECIAL) {
            notifier.notifyNearbyPlayers(location, "A special loot chest has appeared!", 50);
        }

        logger.info("Created " + type + " chest (tier " + tier + ") at " + chestLocation);
        return chestData;
    }

    /**
     * Creates a care package at the specified location
     *
     * @param location The location to spawn the care package
     * @return The created chest data
     */
    public LootChestData spawnCarePackage(Location location) {
        LootChestData chestData = createChest(location, ChestTier.LEGENDARY, ChestType.CARE_PACKAGE);

        // Create care package inventory with high-tier loot
        Inventory carePackage = Bukkit.createInventory(null, CARE_PACKAGE_SIZE, "Care Package");
        factory.fillInventoryWithLoot(carePackage, ChestTier.LEGENDARY, CARE_PACKAGE_SIZE);

        // Store the inventory
        openedChests.put(chestData.getLocation(), carePackage);

        // Special effects for care package
        effects.spawnCarePackageEffects(location);

        return chestData;
    }

    /**
     * Creates a special loot chest with custom contents
     *
     * @param location The location to create the chest
     * @param tier     The tier of the chest
     * @return The created chest data
     */
    public LootChestData createSpecialChest(Location location, ChestTier tier) {
        LootChestData chestData = createChest(location, tier, ChestType.SPECIAL);

        // Create special chest inventory
        Inventory specialChest = Bukkit.createInventory(null, SPECIAL_CHEST_SIZE, "Special Loot Chest");
        factory.fillInventoryWithSpecialLoot(specialChest, tier);

        // Store the inventory
        openedChests.put(chestData.getLocation(), specialChest);

        // Schedule automatic despawn after 5 minutes
        scheduleSpecialChestDespawn(chestData, 300); // 5 minutes

        return chestData;
    }

    /**
     * Removes a chest from the system
     *
     * @param location The location of the chest to remove
     * @return true if the chest was removed, false if it didn't exist
     */
    public boolean removeChest(LootChestLocation location) {
        LootChestData chestData = activeChests.remove(location);
        if (chestData == null) {
            return false;
        }

        // Remove physical block
        location.getBukkitLocation().getBlock().setType(Material.AIR);

        // Remove opened inventory
        openedChests.remove(location);

        // Remove any viewers
        viewingPlayers.entrySet().removeIf(entry -> entry.getValue().equals(location));

        // Play removal effects
        effects.spawnRemovalEffects(location.getBukkitLocation(), chestData.getTier());

        logger.info("Removed chest at " + location);
        return true;
    }

    /**
     * Gets chest data at the specified location
     *
     * @param location The location to check
     * @return The chest data, or null if no chest exists
     */
    public LootChestData getChest(LootChestLocation location) {
        return activeChests.get(location);
    }

    /**
     * Gets the inventory for an opened chest
     *
     * @param location The location of the chest
     * @return The chest inventory, or null if not opened
     */
    public Inventory getChestInventory(LootChestLocation location) {
        return openedChests.get(location);
    }

    /**
     * Opens a chest for a player
     *
     * @param player   The player opening the chest
     * @param location The location of the chest
     * @return true if the chest was opened successfully
     */
    public boolean openChest(Player player, LootChestLocation location) {
        LootChestData chestData = activeChests.get(location);
        if (chestData == null) {
            return false;
        }

        // Check if chest is available
        if (chestData.getState() != ChestState.AVAILABLE) {
            player.sendMessage(ChatColor.RED + "This chest is not available");
            return false;
        }

        // Check for nearby mobs
        if (effects.hasNearbyMobs(location.getBukkitLocation())) {
            player.sendMessage(ChatColor.RED + "It is " + ChatColor.BOLD + "NOT" + ChatColor.RED + " safe to open that right now.");
            player.sendMessage(ChatColor.GRAY + "Eliminate the monsters in the area first.");
            return false;
        }

        // Get or create inventory
        Inventory inventory = openedChests.get(location);
        if (inventory == null) {
            // Create new inventory with loot
            String title = getChestTitle(chestData.getTier(), chestData.getType());
            inventory = Bukkit.createInventory(null, 27, title);
            factory.fillInventoryWithLoot(inventory, chestData.getTier(), 1); // Start with 1 item
            openedChests.put(location, inventory);
        }

        // Open inventory for player
        player.openInventory(inventory);
        viewingPlayers.put(player, location);

        // Update chest state
        chestData.setState(ChestState.OPENED);
        chestData.addInteraction(player.getUniqueId());

        // Play opening effects
        effects.playChestOpenEffects(player, chestData.getTier());

        // Update player statistics
        updatePlayerStatistics(player);

        logger.info("Player " + player.getName() + " opened chest at " + location);
        return true;
    }

    /**
     * Handles a player breaking a chest
     *
     * @param player   The player breaking the chest
     * @param location The location of the chest
     * @return true if the chest was broken successfully
     */
    public boolean breakChest(Player player, LootChestLocation location) {
        LootChestData chestData = activeChests.get(location);
        if (chestData == null) {
            return false;
        }

        // Check for nearby mobs
        if (effects.hasNearbyMobs(location.getBukkitLocation())) {
            player.sendMessage(ChatColor.RED + "It is " + ChatColor.BOLD + "NOT" + ChatColor.RED + " safe to break that right now.");
            player.sendMessage(ChatColor.GRAY + "Eliminate the monsters in the area first.");
            return false;
        }

        // Drop items if chest was opened
        Inventory inventory = openedChests.get(location);
        if (inventory != null) {
            factory.dropInventoryContents(location.getBukkitLocation(), inventory.getContents());
        } else {
            // Drop single item for unopened chest
            factory.dropSingleLootItem(location.getBukkitLocation(), chestData.getTier());
        }

        // Update chest state and schedule respawn
        chestData.setState(ChestState.RESPAWNING);
        scheduleChestRespawn(chestData);

        // Close chest for any viewers
        closeChestForAllViewers(location);

        // Remove physical block
        location.getBukkitLocation().getBlock().setType(Material.AIR);

        // Play breaking effects
        effects.playChestBreakEffects(location.getBukkitLocation(), chestData.getTier());

        // Update player statistics
        updatePlayerStatistics(player);

        logger.info("Player " + player.getName() + " broke chest at " + location);
        return true;
    }

    /**
     * Handles a player closing a chest inventory
     *
     * @param player The player closing the inventory
     */
    public void handleInventoryClose(Player player) {
        LootChestLocation location = viewingPlayers.remove(player);
        if (location == null) {
            return;
        }

        Inventory inventory = openedChests.get(location);
        if (inventory == null) {
            return;
        }

        // Check if inventory is empty
        boolean isEmpty = true;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) != null && inventory.getItem(i).getType() != Material.AIR) {
                isEmpty = false;
                break;
            }
        }

        // If empty, remove the chest
        if (isEmpty) {
            LootChestData chestData = activeChests.get(location);
            if (chestData != null) {
                chestData.setState(ChestState.RESPAWNING);
                scheduleChestRespawn(chestData);

                // Remove physical block
                location.getBukkitLocation().getBlock().setType(Material.AIR);

                // Close for all other viewers
                closeChestForAllViewers(location);

                // Play closing effects
                effects.playChestCloseEffects(location.getBukkitLocation(), chestData.getTier());
            }
        }

        // Play close sound for player
        effects.playInventoryCloseSound(player);
    }

    /**
     * Gets all active chests
     *
     * @return Map of all active chests
     */
    public Map<LootChestLocation, LootChestData> getActiveChests() {
        return new HashMap<>(activeChests);
    }

    /**
     * Gets chests by tier
     *
     * @param tier The tier to filter by
     * @return List of chests with the specified tier
     */
    public List<LootChestData> getChestsByTier(ChestTier tier) {
        return activeChests.values().stream()
                .filter(chest -> chest.getTier() == tier)
                .toList();
    }

    /**
     * Gets chests by type
     *
     * @param type The type to filter by
     * @return List of chests with the specified type
     */
    public List<LootChestData> getChestsByType(ChestType type) {
        return activeChests.values().stream()
                .filter(chest -> chest.getType() == type)
                .toList();
    }

    /**
     * Clears all chest inventories (for shutdown/reload)
     */
    public void clearAllChestInventories() {
        openedChests.clear();
        viewingPlayers.clear();
    }

    /**
     * Gets system statistics
     *
     * @return Map containing system statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChests", activeChests.size());
        stats.put("openedChests", openedChests.size());
        stats.put("viewingPlayers", viewingPlayers.size());

        // Count by tier
        for (ChestTier tier : ChestTier.values()) {
            long count = activeChests.values().stream()
                    .filter(chest -> chest.getTier() == tier)
                    .count();
            stats.put("tier" + tier.getLevel() + "Chests", count);
        }

        // Count by type
        for (ChestType type : ChestType.values()) {
            long count = activeChests.values().stream()
                    .filter(chest -> chest.getType() == type)
                    .count();
            stats.put(type.name().toLowerCase() + "Chests", count);
        }

        return stats;
    }

    // === Private Helper Methods ===

    /**
     * Starts all background tasks
     */
    private void startBackgroundTasks() {
        // Particle effect task
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (LootChestData chest : activeChests.values()) {
                    if (chest.getState() == ChestState.AVAILABLE) {
                        effects.spawnChestParticles(chest.getLocation().getBukkitLocation(), chest.getTier());
                    }
                }
            }
        }.runTaskTimer(plugin, 0, PARTICLE_INTERVAL_TICKS);

        // Respawn check task
        respawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                processChestRespawns();
            }
        }.runTaskTimer(plugin, 0, RESPAWN_CHECK_INTERVAL_TICKS);

        // Cleanup task
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    /**
     * Stops all background tasks
     */
    private void stopBackgroundTasks() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (respawnTask != null) {
            respawnTask.cancel();
            respawnTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    /**
     * Processes chest respawns
     */
    private void processChestRespawns() {
        for (LootChestData chest : activeChests.values()) {
            if (chest.getState() == ChestState.RESPAWNING && chest.isReadyToRespawn()) {
                respawnChest(chest);
            }
        }
    }

    /**
     * Respawns a chest
     *
     * @param chestData The chest to respawn
     */
    private void respawnChest(LootChestData chestData) {
        Location location = chestData.getLocation().getBukkitLocation();

        // Check if chunk is loaded
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        // Set chest block
        if (chestData.getType() == ChestType.CARE_PACKAGE) {
            location.getBlock().setType(Material.ENDER_CHEST);
        } else if (chestData.getType() == ChestType.SPECIAL) {
            location.getBlock().setType(Material.GLOWSTONE);
        } else {
            location.getBlock().setType(Material.CHEST);
        }

        // Update state
        chestData.setState(ChestState.AVAILABLE);
        chestData.resetRespawnTime();

        // Remove old inventory
        openedChests.remove(chestData.getLocation());

        // Play respawn effects
        effects.spawnRespawnEffects(location, chestData.getTier());

        logger.info("Respawned chest at " + chestData.getLocation());
    }

    /**
     * Schedules a chest for respawn
     *
     * @param chestData The chest to schedule for respawn
     */
    private void scheduleChestRespawn(LootChestData chestData) {
        int respawnTime = calculateRespawnTime(chestData.getTier());
        chestData.setRespawnTime(System.currentTimeMillis() + (respawnTime * 1000L));
    }

    /**
     * Calculates respawn time for a chest tier
     *
     * @param tier The chest tier
     * @return Respawn time in seconds
     */
    private int calculateRespawnTime(ChestTier tier) {
        return config.getRespawnTime(tier);
    }

    /**
     * Schedules a special chest for automatic despawn
     *
     * @param chestData   The chest to despawn
     * @param delaySeconds Delay in seconds before despawn
     */
    private void scheduleSpecialChestDespawn(LootChestData chestData, int delaySeconds) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeChests.containsKey(chestData.getLocation())) {
                    removeChest(chestData.getLocation());
                    notifier.notifyNearbyPlayers(
                            chestData.getLocation().getBukkitLocation(),
                            "The special loot chest has vanished!",
                            30
                    );
                }
            }
        }.runTaskLater(plugin, delaySeconds * 20L);
    }

    /**
     * Closes a chest for all viewing players
     *
     * @param location The location of the chest
     */
    private void closeChestForAllViewers(LootChestLocation location) {
        List<Player> viewers = new ArrayList<>();
        for (Map.Entry<Player, LootChestLocation> entry : viewingPlayers.entrySet()) {
            if (entry.getValue().equals(location)) {
                viewers.add(entry.getKey());
            }
        }

        for (Player viewer : viewers) {
            viewingPlayers.remove(viewer);
            viewer.closeInventory();
            effects.playInventoryCloseSound(viewer);
        }

        openedChests.remove(location);
    }

    /**
     * Gets the display title for a chest inventory
     *
     * @param tier The chest tier
     * @param type The chest type
     * @return The display title
     */
    private String getChestTitle(ChestTier tier, ChestType type) {
        return switch (type) {
            case CARE_PACKAGE -> "Care Package";
            case SPECIAL -> "Special Loot Chest";
            default -> "Loot Chest (Tier " + tier.getLevel() + ")";
        };
    }

    /**
     * Updates player statistics for chest interactions
     *
     * @param player The player to update statistics for
     */
    private void updatePlayerStatistics(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null) {
            //yakPlayer.incrementStat("lootChestsOpened");
        }
    }

    /**
     * Performs periodic cleanup tasks
     */
    private void performCleanup() {
        // Remove expired special chests
        List<LootChestLocation> toRemove = new ArrayList<>();
        for (Map.Entry<LootChestLocation, LootChestData> entry : activeChests.entrySet()) {
            LootChestData chest = entry.getValue();
            if (chest.getType() == ChestType.SPECIAL && chest.isExpired()) {
                toRemove.add(entry.getKey());
            }
        }

        for (LootChestLocation location : toRemove) {
            removeChest(location);
        }

        // Clean up disconnected viewers
        viewingPlayers.entrySet().removeIf(entry -> !entry.getKey().isOnline());

        logger.fine("Performed cleanup: removed " + toRemove.size() + " expired chests");
    }

    /**
     * Loads chests from persistent storage
     */
    private void loadChestsFromStorage() {
        try {
            Map<LootChestLocation, LootChestData> loadedChests = repository.loadAllChests();
            activeChests.putAll(loadedChests);

            // Restore physical chest blocks
            for (LootChestData chest : loadedChests.values()) {
                if (chest.getState() == ChestState.AVAILABLE) {
                    Location location = chest.getLocation().getBukkitLocation();
                    if (chest.getType() == ChestType.CARE_PACKAGE) {
                        location.getBlock().setType(Material.ENDER_CHEST);
                    } else if (chest.getType() == ChestType.SPECIAL) {
                        location.getBlock().setType(Material.GLOWSTONE);
                    } else {
                        location.getBlock().setType(Material.CHEST);
                    }
                }
            }

            logger.info("Loaded " + loadedChests.size() + " chests from storage");
        } catch (Exception e) {
            logger.severe("Failed to load chests from storage: " + e.getMessage());
        }
    }

    /**
     * Saves chests to persistent storage
     */
    private void saveChestsToStorage() {
        try {
            repository.saveAllChests(activeChests);
            logger.info("Saved " + activeChests.size() + " chests to storage");
        } catch (Exception e) {
            logger.severe("Failed to save chests to storage: " + e.getMessage());
        }
    }

    // === Getters for components ===

    public LootChestHandler getHandler() { return handler; }
    public LootChestFactory getFactory() { return factory; }
    public LootChestNotifier getNotifier() { return notifier; }
    public LootChestEffects getEffects() { return effects; }
    public LootChestRepository getRepository() { return repository; }
    public LootChestConfig getConfig() { return config; }
}
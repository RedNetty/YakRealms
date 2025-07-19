package com.rednetty.server.mechanics.world.lootchests.events;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.lootchests.core.Chest;
import com.rednetty.server.mechanics.world.lootchests.core.ChestManager;
import com.rednetty.server.mechanics.world.lootchests.types.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles all events related to loot chests
 * Fixed event handling with proper state management and inventory tracking
 */
public class ChestEventHandler implements Listener {
    private final ChestManager manager;
    private final Logger logger;

    // Track recent operations to prevent spam/race conditions
    private final Map<String, Long> recentOperations = new ConcurrentHashMap<>();
    private static final long OPERATION_COOLDOWN_MS = 500; // Reduced to 500ms for better responsiveness

    // Track which player has which chest inventory open
    private final Map<UUID, ChestLocation> playerChestInventories = new ConcurrentHashMap<>();

    public ChestEventHandler(ChestManager manager) {
        this.manager = manager;
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Initializes the event handler
     */
    public void initialize() {
        try {
            Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());

            // Start periodic cleanup task
            Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), this::cleanupOldCooldowns, 1200L, 1200L); // Every minute

            logger.info("Chest event handler initialized and registered");
        } catch (Exception e) {
            logger.severe("Failed to initialize chest event handler: " + e.getMessage());
            throw new RuntimeException("Event handler initialization failed", e);
        }
    }

    /**
     * Shuts down the event handler
     */
    public void shutdown() {
        try {
            // Clear all tracking maps
            recentOperations.clear();
            playerChestInventories.clear();
            logger.info("Chest event handler shutdown completed");
        } catch (Exception e) {
            logger.warning("Error during event handler shutdown: " + e.getMessage());
        }
    }

    /**
     * Handles player interactions with chest blocks
     * FIXED: Left-click now opens chests, shift+left-click breaks them
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChestInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Material blockType = block.getType();

        // Only handle chest-like blocks
        if (!isChestBlock(blockType)) {
            return;
        }

        try {
            ChestLocation location = new ChestLocation(block.getLocation());

            // Check if this is a managed loot chest
            boolean isManaged = manager.getRegistry().hasChest(location);

            if (!isManaged) {
                handleUnmanagedChest(event, player, location, blockType);
            } else {
                handleManagedChest(event, player, location);
            }

        } catch (Exception e) {
            logger.severe("Error handling chest interaction for player " + player.getName() +
                    " at " + block.getLocation() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "An error occurred while interacting with this chest.");
        }
    }

    /**
     * Handles block breaking events for chest blocks
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();
        Player player = event.getPlayer();

        // Only handle chest-like blocks
        if (!isChestBlock(blockType)) {
            return;
        }

        try {
            ChestLocation location = new ChestLocation(block.getLocation());

            // Check if this is a managed loot chest
            if (manager.getRegistry().hasChest(location)) {
                // Always cancel the break event for managed chests
                event.setCancelled(true);

                // Check operation cooldown
                if (isOnCooldown(player, "block_break")) {
                    player.sendMessage(ChatColor.YELLOW + "Please wait before breaking blocks.");
                    return;
                }

                if (player.isOp() && player.isSneaking()) {
                    // Admin removal
                    handleAdminChestRemoval(player, location);
                } else {
                    // Redirect to proper interaction
                    String message = switch (blockType) {
                        case CHEST -> "Use shift + left-click to break loot chests.";
                        case BARREL -> "Use shift + left-click to break special chests.";
                        case ENDER_CHEST -> "Care packages cannot be broken!";
                        default -> "You cannot break this type of loot chest.";
                    };
                    player.sendMessage(ChatColor.RED + message);
                }

                setOperationCooldown(player, "block_break");
            }

        } catch (Exception e) {
            logger.severe("Error handling block break at " + block.getLocation() +
                    " by player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handles inventory close events
     * FIXED: Properly tracks which chest each player is viewing and handles state transitions
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        try {
            UUID playerId = player.getUniqueId();

            // Check if this player was viewing a loot chest inventory
            ChestLocation chestLocation = playerChestInventories.remove(playerId);
            if (chestLocation == null) {
                return; // Not a loot chest inventory
            }

            // Get the chest from registry
            Chest chest = manager.getRegistry().getChest(chestLocation);
            if (chest == null) {
                logger.warning("Player " + player.getName() + " closed inventory for non-existent chest at " + chestLocation);
                return;
            }

            // Handle inventory close with a slight delay to avoid sync issues
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                try {
                    handleLootChestInventoryClose(player, event.getInventory(), chest);
                } catch (Exception e) {
                    logger.warning("Error handling inventory close for player " + player.getName() + ": " + e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.warning("Error in inventory close event for player " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Handles player disconnect to clean up any state
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Clear operation cooldowns for this player
            recentOperations.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getName() + ":"));

            // Clear inventory tracking for this player
            ChestLocation chestLocation = playerChestInventories.remove(playerId);
            if (chestLocation != null) {
                // Player disconnected while viewing a chest - set chest back to available
                Chest chest = manager.getRegistry().getChest(chestLocation);
                if (chest != null && chest.getState() == ChestState.OPENED) {
                    chest.setState(ChestState.AVAILABLE);
                    manager.getRepository().saveChest(chest);
                }
            }

        } catch (Exception e) {
            logger.warning("Error in player quit event for player " + player.getName() + ": " + e.getMessage());
        }
    }

    // === Private Helper Methods ===

    /**
     * Checks if a material is a chest-like block
     */
    private boolean isChestBlock(Material material) {
        return material == Material.CHEST ||
                material == Material.ENDER_CHEST ||
                material == Material.BARREL;
    }

    /**
     * Handles interaction with unmanaged chests (creation for ops)
     */
    private void handleUnmanagedChest(PlayerInteractEvent event, Player player, ChestLocation location, Material blockType) {
        // Allow ops to create chests by shift-right-clicking
        if (player.isOp() && event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
            event.setCancelled(true);

            // Check operation cooldown
            if (isOnCooldown(player, "chest_create")) {
                player.sendMessage(ChatColor.YELLOW + "Please wait before creating another chest.");
                return;
            }

            createChestFromBlock(player, location, blockType);
            setOperationCooldown(player, "chest_create");
        }
        // Otherwise, let normal chest interaction proceed
    }

    /**
     * Handles interaction with managed loot chests
     * FIXED: Left-click opens chest, shift+left-click breaks chest
     */
    private void handleManagedChest(PlayerInteractEvent event, Player player, ChestLocation location) {
        // Always cancel interaction with managed chests
        event.setCancelled(true);

        // Check operation cooldown
        if (isOnCooldown(player, "chest_interact")) {
            player.sendMessage(ChatColor.YELLOW + "Please wait before interacting again.");
            return;
        }

        Action action = event.getAction();

        // FIXED: Left-click (without shift) opens chest, shift+left-click breaks chest
        if (action == Action.LEFT_CLICK_BLOCK) {
            // Break chest
            manager.breakChest(player, location);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // Right-click also opens chest for backwards compatibility
            openChestForPlayer(player, location);
        }

        setOperationCooldown(player, "chest_interact");
    }

    /**
     * Opens a chest for a player and tracks the inventory
     */
    private void openChestForPlayer(Player player, ChestLocation location) {
        Chest chest = manager.getRegistry().getChest(location);
        if (chest == null) {
            player.sendMessage(ChatColor.RED + "Chest not found.");
            return;
        }

        // Check if player already has a chest inventory open
        UUID playerId = player.getUniqueId();
        if (playerChestInventories.containsKey(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "Please close your current chest before opening another one.");
            return;
        }

        // Try to open the chest
        if (manager.openChest(player, location)) {
            // Track that this player is viewing this chest
            playerChestInventories.put(playerId, location);
        }
    }

    /**
     * Creates a loot chest from an existing block (admin function)
     */
    private void createChestFromBlock(Player player, ChestLocation location, Material blockType) {
        try {
            ChestTier tier = determineTierFromTool(player);
            ChestType type = determineTypeFromBlock(blockType);

            Chest chest = manager.createChest(location.getBukkitLocation(), tier, type);

            if (chest != null) {
                player.sendMessage(ChatColor.GREEN + "§l*** LOOT CHEST CREATED ***");
                player.sendMessage(ChatColor.GRAY + "Type: " + type + ", Tier: " + tier.getDisplayName());

                logger.info("Admin " + player.getName() + " created loot chest (tier " + tier.getLevel() +
                        ", type " + type + ") at " + location);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create loot chest. Check console for errors.");
            }

        } catch (Exception e) {
            logger.severe("Error creating loot chest at " + location + " by admin " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "An error occurred while creating the chest.");
        }
    }

    /**
     * Handles admin chest removal
     */
    private void handleAdminChestRemoval(Player player, ChestLocation location) {
        try {
            boolean success = manager.removeChest(location);
            if (success) {
                player.sendMessage(ChatColor.RED + "§l*** LOOT CHEST REMOVED ***");
                logger.info("Admin " + player.getName() + " removed loot chest at " + location);
            } else {
                player.sendMessage(ChatColor.RED + "Failed to remove loot chest. It may have already been removed.");
            }

        } catch (Exception e) {
            logger.severe("Error removing chest at " + location + " by admin " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "An error occurred while removing the chest.");
        }
    }

    /**
     * Determines the chest tier based on the player's held item
     */
    private ChestTier determineTierFromTool(Player player) {
        try {
            Material tool = player.getInventory().getItemInMainHand().getType();
            String materialName = tool.name();

            if (materialName.contains("WOOD")) {
                return ChestTier.WOODEN;
            } else if (materialName.contains("STONE")) {
                return ChestTier.STONE;
            } else if (materialName.contains("IRON")) {
                return ChestTier.IRON;
            } else if (materialName.contains("DIAMOND")) {
                return ChestTier.DIAMOND;
            } else if (materialName.contains("GOLD") || materialName.contains("GOLDEN")) {
                return ChestTier.LEGENDARY;
            } else if (materialName.contains("NETHERITE")) {
                return ChestTier.NETHER_FORGED;
            }

            return ChestTier.IRON; // Default fallback

        } catch (Exception e) {
            logger.warning("Error determining player tier for " + player.getName() + ": " + e.getMessage());
            return ChestTier.IRON;
        }
    }

    /**
     * Determines the chest type based on the block material
     */
    private ChestType determineTypeFromBlock(Material blockType) {
        return switch (blockType) {
            case ENDER_CHEST -> ChestType.CARE_PACKAGE;
            case BARREL -> ChestType.SPECIAL;
            default -> ChestType.NORMAL;
        };
    }

    /**
     * Handles closing of loot chest inventories
     * FIXED: Properly handles empty chest state transitions
     */
    private void handleLootChestInventoryClose(Player player, Inventory inventory, Chest chest) {
        try {
            // Check if inventory is empty
            boolean isEmpty = isInventoryEmpty(inventory);

            if (isEmpty) {
                // Player took all items - schedule respawn
                chest.setState(ChestState.RESPAWNING);
                int respawnSeconds = manager.getConfig().getRespawnTime(chest.getTier());
                chest.setRespawnTime(System.currentTimeMillis() + (respawnSeconds * 1000L));

                // Remove physical block
                var bukkitLoc = chest.getLocation().getBukkitLocationSafe();
                if (bukkitLoc != null) {
                    bukkitLoc.getBlock().setType(Material.AIR);
                }

                // Save state change
                manager.getRepository().saveChest(chest);

                player.sendMessage(ChatColor.GRAY + "You've emptied the chest. It will respawn in " +
                        (respawnSeconds / 60) + " minutes and " + (respawnSeconds % 60) + " seconds.");

                logger.fine("Player " + player.getName() + " emptied chest at " + chest.getLocation());

            } else {
                // Player left items - chest remains available
                chest.setState(ChestState.AVAILABLE);
                manager.getRepository().saveChest(chest);

                player.sendMessage(ChatColor.GRAY + "You left items in the chest. It remains available.");
            }

            // Play close sound
            if (manager.getEffects() != null) {
                manager.getEffects().playInventoryCloseSound(player);
            }

        } catch (Exception e) {
            logger.severe("Error handling inventory close for chest " + chest.getLocation() +
                    " by player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if an inventory is completely empty
     */
    private boolean isInventoryEmpty(Inventory inventory) {
        if (inventory == null) return true;

        for (int i = 0; i < inventory.getSize(); i++) {
            var item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    // === Operation Cooldown Management ===

    /**
     * Checks if a player is on cooldown for a specific operation
     */
    private boolean isOnCooldown(Player player, String operation) {
        String key = player.getName() + ":" + operation;
        Long lastOperation = recentOperations.get(key);

        if (lastOperation == null) {
            return false;
        }

        return System.currentTimeMillis() - lastOperation < OPERATION_COOLDOWN_MS;
    }

    /**
     * Sets an operation cooldown for a player
     */
    private void setOperationCooldown(Player player, String operation) {
        String key = player.getName() + ":" + operation;
        recentOperations.put(key, System.currentTimeMillis());
    }

    /**
     * Cleans up old cooldown entries
     */
    private void cleanupOldCooldowns() {
        long cutoff = System.currentTimeMillis() - (OPERATION_COOLDOWN_MS * 3);
        recentOperations.entrySet().removeIf(entry -> entry.getValue() < cutoff);

        // Log cleanup if significant number of entries were removed
        if (recentOperations.size() > 100) {
            logger.fine("Cleaned up old cooldown entries, current size: " + recentOperations.size());
        }
    }

    // === Public Utility Methods ===

    /**
     * Gets the chest location a player is currently viewing (if any)
     */
    public ChestLocation getPlayerViewingChest(Player player) {
        return playerChestInventories.get(player.getUniqueId());
    }

    /**
     * Checks if a player is currently viewing a chest inventory
     */
    public boolean isPlayerViewingChest(Player player) {
        return playerChestInventories.containsKey(player.getUniqueId());
    }

    /**
     * Forces cleanup of a player's chest viewing state (for admin commands)
     */
    public void clearPlayerChestViewing(Player player) {
        playerChestInventories.remove(player.getUniqueId());
    }

    // === Statistics ===

    /**
     * Gets event handler statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("recentOperations", recentOperations.size());
        stats.put("playersViewingChests", playerChestInventories.size());
        stats.put("cooldownTimeMs", OPERATION_COOLDOWN_MS);
        return stats;
    }

    @Override
    public String toString() {
        return "ChestEventHandler{manager=" + (manager != null ? "available" : "null") +
                ", operations=" + recentOperations.size() +
                ", viewingChests=" + playerChestInventories.size() + "}";
    }
}
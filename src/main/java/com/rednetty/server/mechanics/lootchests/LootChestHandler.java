package com.rednetty.server.mechanics.lootchests;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.lootchests.types.LootChestLocation;
import com.rednetty.server.mechanics.lootchests.data.LootChestData;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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

import java.util.logging.Logger;

/**
 * Handles all events related to loot chests
 */
public class LootChestHandler implements Listener {
    private final Logger logger;
    private LootChestManager manager;

    public LootChestHandler() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Initializes the handler
     */
    public void initialize() {
        this.manager = LootChestManager.getInstance();
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        logger.info("LootChestHandler initialized and registered");
    }

    /**
     * Shuts down the handler
     */
    public void shutdown() {
        // Cleanup any remaining resources
        logger.info("LootChestHandler shutdown");
    }

    /**
     * Handles player interactions with chests
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        Location location = block.getLocation();
        LootChestLocation chestLocation = new LootChestLocation(location);

        // Check if this is a loot chest
        LootChestData chestData = manager.getChest(chestLocation);

        // Handle different block types
        Material blockType = block.getType();

        if (blockType == Material.CHEST) {
            handleChestInteraction(event, player, chestLocation, chestData);
        } else if (blockType == Material.ENDER_CHEST) {
            handleCarePackageInteraction(event, player, chestLocation, chestData);
        } else if (blockType == Material.BARREL) { // Changed from GLOWSTONE
            handleSpecialChestInteraction(event, player, chestLocation, chestData);
        }
    }

    /**
     * Handles normal chest interactions
     */
    private void handleChestInteraction(PlayerInteractEvent event, Player player,
                                        LootChestLocation location, LootChestData chestData) {
        // If not a managed loot chest, check if op is trying to create one
        if (chestData == null) {
            if (!player.isOp()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.GRAY + "The chest is locked.");
            }
            return;
        }

        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Open chest
            boolean success = manager.openChest(player, location);
            if (!success) {
                player.sendMessage(ChatColor.RED + "Unable to open this chest right now.");
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Only allow breaking if player is sneaking to prevent accidental breaks
            if (player.isSneaking()) {
                boolean success = manager.breakChest(player, location);
                if (!success) {
                    player.sendMessage(ChatColor.RED + "Unable to break this chest right now.");
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Sneak + left-click to break this chest");
            }
        }
    }

    /**
     * Handles care package interactions
     */
    private void handleCarePackageInteraction(PlayerInteractEvent event, Player player,
                                              LootChestLocation location, LootChestData chestData) {
        if (chestData == null || chestData.getType() != ChestType.CARE_PACKAGE) {
            return;
        }

        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            boolean success = manager.openChest(player, location);
            if (!success) {
                player.sendMessage(ChatColor.RED + "Unable to open this care package right now.");
            }
        }
        // Care packages cannot be broken with left click
    }

    /**
     * Handles special chest interactions (barrel)
     */
    private void handleSpecialChestInteraction(PlayerInteractEvent event, Player player,
                                               LootChestLocation location, LootChestData chestData) {
        if (chestData == null) {
            // Only ops can interact with barrels for chest creation
            if (!player.isOp()) {
                return;
            }

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                createLootChestFromBarrel(player, location);
            }
            return;
        }

        // This is a managed special chest
        event.setCancelled(true);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Open existing special chest
            boolean success = manager.openChest(player, location);
            if (!success) {
                player.sendMessage(ChatColor.RED + "Unable to open this special chest right now.");
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Break special chest with left-click
            boolean success = manager.breakChest(player, location);
            if (!success) {
                player.sendMessage(ChatColor.RED + "Unable to break this special chest right now.");
            }
        }
    }

    /**
     * Creates a loot chest from a barrel block (admin function)
     */
    private void createLootChestFromBarrel(Player player, LootChestLocation location) {
        // Determine tier based on player's tool or default to tier 3
        ChestTier tier = getPlayerTier(player);

        // Create the chest (will convert barrel to appropriate chest type)
        LootChestData chestData = manager.createChest(location.getBukkitLocation(), tier, ChestType.NORMAL);

        // Send confirmation to player
        player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "     *** LOOT CHEST CREATED ***");
        player.sendMessage(ChatColor.GRAY + "Tier: " + tier.toString());

        // Save configuration
        manager.getRepository().saveChest(location, chestData);

        logger.info("Admin " + player.getName() + " created loot chest (tier " + tier.getLevel() + ") at " + location);
    }

    /**
     * Determines the tier based on the player's held item
     */
    private ChestTier getPlayerTier(Player player) {
        if (player.getInventory().getItemInMainHand() == null) {
            return ChestTier.IRON; // Default tier
        }

        String materialName = player.getInventory().getItemInMainHand().getType().name();

        if (materialName.contains("WOOD")) {
            return ChestTier.WOODEN;
        } else if (materialName.contains("STONE")) {
            return ChestTier.STONE;
        } else if (materialName.contains("IRON")) {
            return ChestTier.IRON;
        } else if (materialName.contains("DIAMOND")) {
            return ChestTier.DIAMOND;
        } else if (materialName.contains("GOLD") || materialName.contains("GOLDEN")) {
            return ChestTier.GOLDEN;
        } else if (materialName.contains("NETHERITE") || materialName.contains("ICE")) {
            return ChestTier.LEGENDARY;
        }

        return ChestTier.IRON; // Default fallback
    }

    /**
     * Handles chest block breaking - Updated to handle all chest types properly
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        LootChestLocation chestLocation = new LootChestLocation(location);
        Player player = event.getPlayer();

        // Check if this is a managed loot chest
        LootChestData chestData = manager.getChest(chestLocation);

        if (chestData != null) {
            // This is a managed loot chest - always cancel the break event
            event.setCancelled(true);

            Material blockType = block.getType();

            if (player.isOp() && (blockType == Material.BARREL || blockType == Material.GLOWSTONE)) {
                // Admin removing a loot chest (special chests or legacy glowstone)
                boolean success = manager.removeChest(chestLocation);
                if (success) {
                    player.sendMessage(ChatColor.RED.toString() + ChatColor.BOLD + "     *** LOOT CHEST REMOVED ***");
                    manager.getRepository().deleteChest(chestLocation);
                }
            } else if (blockType == Material.CHEST) {
                // Regular players breaking normal chests - redirect to interaction handler
                player.sendMessage(ChatColor.RED + "You cannot break loot chests with tools. Use left-click instead.");
            } else if (blockType == Material.BARREL) {
                // Special chest - redirect to interaction handler
                player.sendMessage(ChatColor.RED + "You cannot break special chests with tools. Use left-click instead.");
            } else if (blockType == Material.ENDER_CHEST) {
                // Care packages cannot be broken
                player.sendMessage(ChatColor.RED + "Care packages cannot be broken!");
            } else {
                // Unknown block type
                player.sendMessage(ChatColor.RED + "You cannot break this type of loot chest.");
            }
        }
    }

    /**
     * Handles inventory close events
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();

        // Check if this is a loot chest inventory
        String title = event.getView().getTitle();
        if (title.contains("Loot Chest") || title.contains("Care Package") || title.contains("Special Loot Chest")) {
            // Run on next tick to avoid async issues
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                manager.handleInventoryClose(player);
            });
        }
    }

    /**
     * Handles player disconnect to clean up viewing state
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up any viewing state for this player - run sync
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            manager.handleInventoryClose(player);
        });
    }

    /**
     * Handles admin commands for chest management
     * This would typically be called from a command handler
     */
    public boolean handleAdminCommand(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        if (args.length == 0) {
            sendAdminHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                return handleCreateCommand(player, args);
            }
            case "remove" -> {
                return handleRemoveCommand(player, args);
            }
            case "info" -> {
                return handleInfoCommand(player, args);
            }
            case "list" -> {
                return handleListCommand(player, args);
            }
            case "stats" -> {
                return handleStatsCommand(player, args);
            }
            case "carepackage" -> {
                return handleCarePackageCommand(player, args);
            }
            case "special" -> {
                return handleSpecialCommand(player, args);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendAdminHelp(player);
                return false;
            }
        }
    }

    /**
     * Handles chest creation command
     */
    private boolean handleCreateCommand(Player player, String[] args) {
        ChestTier tier = ChestTier.IRON; // Default
        ChestType type = ChestType.NORMAL; // Default

        if (args.length > 1) {
            try {
                int tierLevel = Integer.parseInt(args[1]);
                tier = ChestTier.fromLevel(tierLevel);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid tier number. Use 1-6.");
                return false;
            }
        }

        if (args.length > 2) {
            try {
                type = ChestType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid chest type. Use: NORMAL, CARE_PACKAGE, SPECIAL");
                return false;
            }
        }

        Location location = player.getLocation().getBlock().getLocation();
        LootChestData chestData = manager.createChest(location, tier, type);

        player.sendMessage(ChatColor.GREEN + "Created " + type + " chest (tier " + tier.getLevel() + ") at your location.");
        return true;
    }

    /**
     * Handles chest removal command
     */
    private boolean handleRemoveCommand(Player player, String[] args) {
        Location location = player.getLocation().getBlock().getLocation();
        LootChestLocation chestLocation = new LootChestLocation(location);

        boolean success = manager.removeChest(chestLocation);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Removed loot chest at your location.");
        } else {
            player.sendMessage(ChatColor.RED + "No loot chest found at your location.");
        }
        return true;
    }

    /**
     * Handles chest info command
     */
    private boolean handleInfoCommand(Player player, String[] args) {
        Location location = player.getLocation().getBlock().getLocation();
        LootChestLocation chestLocation = new LootChestLocation(location);
        LootChestData chestData = manager.getChest(chestLocation);

        if (chestData == null) {
            player.sendMessage(ChatColor.RED + "No loot chest found at your location.");
            return false;
        }

        player.sendMessage(ChatColor.YELLOW + "=== Loot Chest Info ===");
        player.sendMessage(ChatColor.WHITE + "Location: " + chestLocation);
        player.sendMessage(ChatColor.WHITE + "Tier: " + chestData.getTier());
        player.sendMessage(ChatColor.WHITE + "Type: " + chestData.getType());
        player.sendMessage(ChatColor.WHITE + "State: " + chestData.getStatusString());
        player.sendMessage(ChatColor.WHITE + "Interactions: " + chestData.getInteractionCount());
        player.sendMessage(ChatColor.WHITE + "Age: " + (chestData.getAge() / 1000) + " seconds");

        return true;
    }

    /**
     * Handles list command
     */
    private boolean handleListCommand(Player player, String[] args) {
        var chests = manager.getActiveChests();

        player.sendMessage(ChatColor.YELLOW + "=== Active Loot Chests (" + chests.size() + ") ===");

        int shown = 0;
        for (var entry : chests.entrySet()) {
            if (shown >= 10) {
                player.sendMessage(ChatColor.GRAY + "... and " + (chests.size() - shown) + " more");
                break;
            }

            LootChestLocation loc = entry.getKey();
            LootChestData data = entry.getValue();

            player.sendMessage(ChatColor.WHITE.toString() + loc + " - " + data.getTier() + " " +
                    data.getType() + " (" + data.getStatusString() + ")");
            shown++;
        }

        return true;
    }

    /**
     * Handles stats command
     */
    private boolean handleStatsCommand(Player player, String[] args) {
        var stats = manager.getStatistics();

        player.sendMessage(ChatColor.YELLOW + "=== Loot Chest Statistics ===");
        for (var entry : stats.entrySet()) {
            player.sendMessage(ChatColor.WHITE + entry.getKey() + ": " + entry.getValue());
        }

        return true;
    }

    /**
     * Handles care package command
     */
    private boolean handleCarePackageCommand(Player player, String[] args) {
        Location location = player.getLocation();
        manager.spawnCarePackage(location);

        player.sendMessage(ChatColor.GREEN + "Spawned care package at your location.");
        return true;
    }

    /**
     * Handles special chest command
     */
    private boolean handleSpecialCommand(Player player, String[] args) {
        ChestTier tier = ChestTier.LEGENDARY; // Default for special chests

        if (args.length > 1) {
            try {
                int tierLevel = Integer.parseInt(args[1]);
                tier = ChestTier.fromLevel(tierLevel);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid tier number. Use 1-6.");
                return false;
            }
        }

        Location location = player.getLocation();
        manager.createSpecialChest(location, tier);

        player.sendMessage(ChatColor.GREEN + "Created special chest (tier " + tier.getLevel() + ") at your location.");
        return true;
    }

    /**
     * Sends admin help information
     */
    private void sendAdminHelp(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== Loot Chest Admin Commands ===");
        player.sendMessage(ChatColor.WHITE + "/lootchest create [tier] [type] - Create a chest");
        player.sendMessage(ChatColor.WHITE + "/lootchest remove - Remove chest at your location");
        player.sendMessage(ChatColor.WHITE + "/lootchest info - Get info about chest at your location");
        player.sendMessage(ChatColor.WHITE + "/lootchest list - List all active chests");
        player.sendMessage(ChatColor.WHITE + "/lootchest stats - Show system statistics");
        player.sendMessage(ChatColor.WHITE + "/lootchest carepackage - Spawn a care package");
        player.sendMessage(ChatColor.WHITE + "/lootchest special [tier] - Create a special chest");
        player.sendMessage(ChatColor.GRAY + "Tiers: 1-6, Types: NORMAL, CARE_PACKAGE, SPECIAL");
        player.sendMessage(ChatColor.GRAY + "Note: Use left-click to break chests");
        player.sendMessage(ChatColor.GRAY + "Special chests use barrel blocks and don't respawn when broken");
    }
}
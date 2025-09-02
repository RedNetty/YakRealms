package com.rednetty.server.core.mechanics.world.teleport;

import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * System for handling hearthstone teleportation
 */
public class HearthstoneSystem implements Listener {
    private static HearthstoneSystem instance;
    private final Logger logger;
    private final TeleportManager teleportManager;
    private final NamespacedKey hearthstoneKey;
    private final NamespacedKey homeLocationKey;

    // Map of players to their home locations
    private final Map<UUID, String> playerHomeLocations = new HashMap<>();

    // Default home location
    private static final String DEFAULT_HOME = "deadpeaks";

    /**
     * Private constructor for singleton pattern
     */
    private HearthstoneSystem() {
        this.logger = YakRealms.getInstance().getLogger();
        this.teleportManager = TeleportManager.getInstance();
        this.hearthstoneKey = new NamespacedKey(YakRealms.getInstance(), "hearthstone");
        this.homeLocationKey = new NamespacedKey(YakRealms.getInstance(), "home_location");
    }

    /**
     * Gets the singleton instance
     *
     * @return The HearthstoneSystem instance
     */
    public static HearthstoneSystem getInstance() {
        if (instance == null) {
            instance = new HearthstoneSystem();
        }
        return instance;
    }

    /**
     * Initializes the hearthstone system
     */
    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
            logger.info("HearthstoneSystem enabled successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to enable HearthstoneSystem", e);
        }
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        try {
            playerHomeLocations.clear();
            logger.info("HearthstoneSystem has been disabled");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during HearthstoneSystem shutdown", e);
        }
    }

    /**
     * Creates a hearthstone item
     *
     * @param homeLocation The home location ID
     * @return The created hearthstone
     */
    public ItemStack createHearthstone(String homeLocation) {
        try {
            if (homeLocation == null || homeLocation.trim().isEmpty()) {
                homeLocation = DEFAULT_HOME;
            }

            TeleportDestination destination = teleportManager.getDestination(homeLocation);
            if (destination == null) {
                homeLocation = DEFAULT_HOME;
                destination = teleportManager.getDestination(homeLocation);

                if (destination == null) {
                    // Fallback to first available destination
                    for (TeleportDestination dest : teleportManager.getAllDestinations()) {
                        destination = dest;
                        homeLocation = dest.getId();
                        break;
                    }

                    if (destination == null) {
                        logger.warning("No valid destinations found for hearthstone creation");
                        return null;
                    }
                }
            }

            ItemStack item = new ItemStack(Material.QUARTZ);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW.toString() + ChatColor.BOLD + "Hearthstone");

                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Teleports you to your home town.",
                        ChatColor.GRAY + "Talk to an Innkeeper to change your home town.",
                        ChatColor.GREEN + "Location: " + destination.getDisplayName()
                ));

                // Store hearthstone data
                PersistentDataContainer container = meta.getPersistentDataContainer();
                container.set(hearthstoneKey, PersistentDataType.BYTE, (byte) 1);
                container.set(homeLocationKey, PersistentDataType.STRING, homeLocation);

                item.setItemMeta(meta);
            }

            return item;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error creating hearthstone", e);
            return null;
        }
    }

    /**
     * Checks if an item is a hearthstone
     *
     * @param item The item to check
     * @return True if it's a hearthstone
     */
    public boolean isHearthstone(ItemStack item) {
        if (item == null || item.getType() != Material.QUARTZ || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        try {
            // Check for hearthstone tag
            PersistentDataContainer container = meta.getPersistentDataContainer();
            return container.has(hearthstoneKey, PersistentDataType.BYTE);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error checking if item is hearthstone", e);
            return false;
        }
    }

    /**
     * Gets the home location ID from a hearthstone
     *
     * @param item The hearthstone item
     * @return The home location ID or default if not found
     */
    public String getHomeLocationFromHearthstone(ItemStack item) {
        if (!isHearthstone(item)) {
            return DEFAULT_HOME;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return DEFAULT_HOME;
        }

        try {
            // Get home location from persistent data
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(homeLocationKey, PersistentDataType.STRING)) {
                String homeLocation = container.get(homeLocationKey, PersistentDataType.STRING);
                if (homeLocation != null && !homeLocation.trim().isEmpty()) {
                    return homeLocation;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting home location from hearthstone", e);
        }

        return DEFAULT_HOME;
    }

    /**
     * Sets a player's home location
     *
     * @param player     The player
     * @param locationId The new home location ID
     * @return True if successful
     */
    public boolean setPlayerHomeLocation(Player player, String locationId) {
        if (player == null || locationId == null || locationId.trim().isEmpty()) {
            return false;
        }

        try {
            TeleportDestination destination = teleportManager.getDestination(locationId);
            if (destination == null) {
                return false;
            }

            // Store the player's home location
            playerHomeLocations.put(player.getUniqueId(), locationId);

            // Update player's hearthstone if they have one
            updatePlayerHearthstones(player, locationId, destination);

            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error setting player home location", e);
            return false;
        }
    }

    /**
     * Updates all hearthstones in player's inventory
     *
     * @param player      The player
     * @param locationId  The new location ID
     * @param destination The destination object
     */
    private void updatePlayerHearthstones(Player player, String locationId, TeleportDestination destination) {
        if (player == null || locationId == null || destination == null) {
            return;
        }

        try {
            ItemStack[] contents = player.getInventory().getContents();
            if (contents == null) {
                return;
            }

            boolean updated = false;
            for (ItemStack item : contents) {
                if (isHearthstone(item)) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setLore(Arrays.asList(
                                ChatColor.GRAY + "Teleports you to your home town.",
                                ChatColor.GRAY + "Talk to an Innkeeper to change your home town.",
                                ChatColor.GREEN + "Location: " + destination.getDisplayName()
                        ));

                        // Update persistent data
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        container.set(homeLocationKey, PersistentDataType.STRING, locationId);

                        item.setItemMeta(meta);
                        updated = true;
                    }
                }
            }

            if (updated) {
                player.sendMessage(ChatColor.GREEN + "Your hearthstone has been updated to: " + destination.getDisplayName());
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player hearthstones", e);
        }
    }

    /**
     * Gets a player's home location
     *
     * @param player The player
     * @return The home location ID
     */
    public String getPlayerHomeLocation(Player player) {
        if (player == null) {
            return DEFAULT_HOME;
        }
        return playerHomeLocations.getOrDefault(player.getUniqueId(), DEFAULT_HOME);
    }

    /**
     * Validates that a hearthstone can be used
     *
     * @param player     The player
     * @param hearthstone The hearthstone item
     * @return Validation result message or null if valid
     */
    private String validateHearthstoneUsage(Player player, ItemStack hearthstone) {
        if (player == null) {
            return "Player is invalid";
        }

        if (!isHearthstone(hearthstone)) {
            return null; // Not a hearthstone, so validation doesn't apply
        }

        // Check if player is already teleporting
        if (teleportManager.isTeleporting(player.getUniqueId())) {
            return "You are already teleporting.";
        }

        // Check for alignment restrictions (chaotic players can't teleport)
        if (player.hasMetadata("alignment") &&
                player.getMetadata("alignment").size() > 0 &&
                "CHAOTIC".equals(player.getMetadata("alignment").get(0).asString())) {
            return "You " + ChatColor.UNDERLINE + "cannot" + ChatColor.RED + " do this while chaotic!";
        }

        // Check for combat/duel restrictions
        if (player.hasMetadata("inCombat") &&
                player.getMetadata("inCombat").size() > 0 &&
                player.getMetadata("inCombat").get(0).asBoolean()) {
            return "You cannot teleport while in combat!";
        }

        return null; // Valid
    }

    /**
     * Handles hearthstone usage
     */
    @EventHandler
    public void onHearthstoneUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the item is a hearthstone
        if (!isHearthstone(item)) {
            return;
        }

        // Prevent usage with off-hand items
        event.setCancelled(true);

        try {
            // Validate hearthstone usage
            String validationError = validateHearthstoneUsage(player, item);
            if (validationError != null) {
                player.sendMessage(ChatColor.RED + validationError);
                return;
            }

            // Get the home location
            String homeLocationId = getHomeLocationFromHearthstone(item);
            TeleportDestination destination = teleportManager.getDestination(homeLocationId);

            if (destination == null) {
                player.sendMessage(ChatColor.RED + "Your hearthstone's home location is invalid.");
                logger.warning("Invalid hearthstone destination for player " + player.getName() + ": " + homeLocationId);
                return;
            }

            // Validate destination location
            if (destination.getLocation() == null || destination.getLocation().getWorld() == null) {
                player.sendMessage(ChatColor.RED + "Your hearthstone's destination is corrupted.");
                logger.warning("Corrupted destination location for " + destination.getId());
                return;
            }

            // Start the teleport
            boolean success = teleportManager.startTeleport(player, destination, 10, null, TeleportEffectType.HEARTHSTONE);
            if (!success) {
                player.sendMessage(ChatColor.RED + "Failed to start hearthstone teleportation.");
            }

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error handling hearthstone usage for " + player.getName(), e);
            player.sendMessage(ChatColor.RED + "An error occurred while using your hearthstone.");
        }
    }

    /**
     * Creates a hearthstone for a player with their current home location
     *
     * @param player The player
     * @return The created hearthstone or null if failed
     */
    public ItemStack createHearthstoneForPlayer(Player player) {
        if (player == null) {
            return null;
        }

        String homeLocation = getPlayerHomeLocation(player);
        return createHearthstone(homeLocation);
    }

    /**
     * Validates that the default home location exists
     *
     * @return True if valid
     */
    public boolean validateDefaultHome() {
        TeleportDestination defaultDest = teleportManager.getDestination(DEFAULT_HOME);
        if (defaultDest == null) {
            logger.warning("Default hearthstone home location '" + DEFAULT_HOME + "' does not exist!");
            return false;
        }

        if (defaultDest.getLocation() == null || defaultDest.getLocation().getWorld() == null) {
            logger.warning("Default hearthstone home location '" + DEFAULT_HOME + "' has invalid location!");
            return false;
        }

        return true;
    }

    /**
     * Gets the default home location ID
     *
     * @return The default home location ID
     */
    public String getDefaultHome() {
        return DEFAULT_HOME;
    }

    /**
     * Gets all player home locations
     *
     * @return A copy of the player home locations map
     */
    public Map<UUID, String> getPlayerHomeLocations() {
        return new HashMap<>(playerHomeLocations);
    }

    /**
     * Clears a player's home location (resets to default)
     *
     * @param player The player
     */
    public void clearPlayerHomeLocation(Player player) {
        if (player != null) {
            playerHomeLocations.remove(player.getUniqueId());
        }
    }
}
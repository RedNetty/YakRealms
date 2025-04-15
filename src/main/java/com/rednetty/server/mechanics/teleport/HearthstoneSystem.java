package com.rednetty.server.mechanics.teleport;

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
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        logger.info("HearthstoneSystem has been enabled");
    }

    /**
     * Cleans up when plugin is disabled
     */
    public void onDisable() {
        logger.info("HearthstoneSystem has been disabled");
    }

    /**
     * Creates a hearthstone item
     *
     * @param homeLocation The home location ID
     * @return The created hearthstone
     */
    public ItemStack createHearthstone(String homeLocation) {
        if (homeLocation == null) {
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

        // Check for hearthstone tag
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(hearthstoneKey, PersistentDataType.BYTE);
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

        // Get home location from persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(homeLocationKey, PersistentDataType.STRING)) {
            return container.get(homeLocationKey, PersistentDataType.STRING);
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
        TeleportDestination destination = teleportManager.getDestination(locationId);
        if (destination == null) {
            return false;
        }

        // Store the player's home location
        playerHomeLocations.put(player.getUniqueId(), locationId);

        // Update player's hearthstone if they have one
        for (ItemStack item : player.getInventory().getContents()) {
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
                }
            }
        }

        return true;
    }

    /**
     * Gets a player's home location
     *
     * @param player The player
     * @return The home location ID
     */
    public String getPlayerHomeLocation(Player player) {
        return playerHomeLocations.getOrDefault(player.getUniqueId(), DEFAULT_HOME);
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
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check if the item is a hearthstone
        if (!isHearthstone(item)) {
            return;
        }

        // Prevent usage with off-hand items
        event.setCancelled(true);

        // Check if player is already teleporting
        if (teleportManager.isTeleporting(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already teleporting.");
            return;
        }

        // Check for alignment restrictions (chaotic players can't teleport)
        if (player.hasMetadata("alignment") && player.getMetadata("alignment").get(0).asString().equals("CHAOTIC")) {
            player.sendMessage(ChatColor.RED + "You " + ChatColor.UNDERLINE + "cannot" +
                    ChatColor.RED + " do this while chaotic!");
            return;
        }

        // Check for combat/duel restrictions
        if (player.hasMetadata("inCombat") && player.getMetadata("inCombat").get(0).asBoolean()) {
            player.sendMessage(ChatColor.RED + "You cannot teleport while in combat!");
            return;
        }

        // Get the home location
        String homeLocationId = getHomeLocationFromHearthstone(item);
        TeleportDestination destination = teleportManager.getDestination(homeLocationId);

        if (destination == null) {
            player.sendMessage(ChatColor.RED + "Your hearthstone's home location is invalid.");
            return;
        }

        // Start the teleport
        teleportManager.startTeleport(player, destination, 10, null, TeleportEffectType.HEARTHSTONE);
    }
}
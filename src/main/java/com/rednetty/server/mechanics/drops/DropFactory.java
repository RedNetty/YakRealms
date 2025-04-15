package com.rednetty.server.mechanics.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.teleport.TeleportDestination;
import com.rednetty.server.mechanics.teleport.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Factory class for creating different types of drops
 */
public class DropFactory {
    private static DropFactory instance;
    private final Logger logger;
    private final YakRealms plugin;
    private final TeleportManager teleportManager;
    private final TeleportBookSystem teleportBookSystem;

    // Cache of destination tiers for faster lookups
    private final Map<Integer, List<TeleportDestination>> tieredDestinations = new HashMap<>();
    private long lastDestinationCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = 60000; // 1 minute

    /**
     * Private constructor for singleton pattern
     */
    private DropFactory() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.teleportManager = TeleportManager.getInstance();
        this.teleportBookSystem = TeleportBookSystem.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The DropFactory instance
     */
    public static DropFactory getInstance() {
        if (instance == null) {
            instance = new DropFactory();
        }
        return instance;
    }

    /**
     * Creates a drop for a normal mob - delegates to DropsManager
     *
     * @param tier     The tier level (1-6)
     * @param itemType The item type (1-8)
     * @return The created ItemStack
     */
    public ItemStack createNormalDrop(int tier, int itemType) {
        return DropsManager.createDrop(tier, itemType);
    }

    /**
     * Creates a drop for an elite mob - delegates to DropsManager
     *
     * @param tier     The tier level (1-6)
     * @param itemType The item type (1-8)
     * @param rarity   The rarity level (1-4)
     * @return The created ItemStack
     */
    public ItemStack createEliteDrop(int tier, int itemType, int rarity) {
        return DropsManager.getInstance().createDrop(tier, itemType, rarity);
    }

    /**
     * Creates a crate drop
     *
     * @param tier The tier level (1-6)
     * @return The created crate ItemStack
     */
    public ItemStack createCrateDrop(int tier) {
        // Create a crate with appropriate tier
        ItemStack crate = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = crate.getItemMeta();

        if (meta != null) {
            // Set name
            meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Loot Crate (Tier " + tier + ")");

            // Set lore
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to open");
            lore.add(ChatColor.GRAY + "Contains tier " + tier + " items");
            meta.setLore(lore);

            // Add item flags to hide attributes
            for (ItemFlag flag : ItemFlag.values()) {
                meta.addItemFlags(flag);
            }

            // Store tier information
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey keyTier = new NamespacedKey(plugin, "crate_tier");
            container.set(keyTier, PersistentDataType.INTEGER, tier);

            crate.setItemMeta(meta);
        }

        return crate;
    }

    /**
     * Creates a gem drop
     *
     * @param tier The tier level (1-6)
     * @return The created gem ItemStack
     */
    public ItemStack createGemDrop(int tier) {
        // Calculate gem amount based on tier
        int gems = 3;
        int totalGems = 1;
        for (int i = 0; i < tier; i++) {
            totalGems = totalGems * gems;
        }
        if (totalGems > 64) {
            totalGems = 64;
        }
        return MoneyManager.makeGems(totalGems);
    }

    /**
     * Creates a scroll drop
     *
     * @param tier The tier level (1-6)
     * @return The created scroll ItemStack
     */
    public ItemStack createScrollDrop(int tier) {
        // Determine destination based on tier
        String destinationId = getScrollDestinationId(tier);

        // Get the destination from the teleport manager
        TeleportDestination destination = teleportManager.getDestination(destinationId);

        if (destination == null) {
            logger.warning("Failed to find destination '" + destinationId + "' for teleport scroll, using fallback");
            destination = getFallbackDestination();

            if (destination == null) {
                // If we still can't get a destination, create a generic paper item
                return createGenericScrollItem("Dead Peaks");
            }
        }

        // Create teleport book through our system
        ItemStack teleportBook = teleportBookSystem.createTeleportBook(destination.getId(), false);

        // Additional validation to ensure we have a valid item
        if (teleportBook == null || teleportBook.getType() == Material.AIR) {
            logger.warning("TeleportBookSystem returned null or AIR for destination: " + destination.getId());
            return createGenericScrollItem(destination.getDisplayName());
        }

        return teleportBook;
    }

    /**
     * Creates a generic scroll item if the teleport system fails
     *
     * @param destinationName The name of the destination
     * @return A basic paper item representing a scroll
     */
    private ItemStack createGenericScrollItem(String destinationName) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + destinationName + " Teleport Scroll");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Right-click to teleport to " + destinationName);
            meta.setLore(lore);

            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /**
     * Gets a fallback destination if the primary one doesn't exist
     *
     * @return The first available destination or null if none exist
     */
    private TeleportDestination getFallbackDestination() {
        // Try to find any destination as a fallback
        Collection<TeleportDestination> destinations = teleportManager.getAllDestinations();
        if (!destinations.isEmpty()) {
            return destinations.iterator().next(); // Return the first one we find
        }
        return null;
    }

    /**
     * Refreshes the destination tier cache if needed
     */
    private void refreshDestinationCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDestinationCacheUpdate > CACHE_REFRESH_INTERVAL || tieredDestinations.isEmpty()) {
            lastDestinationCacheUpdate = currentTime;
            tieredDestinations.clear();

            // Group destinations by assigned tier
            Collection<TeleportDestination> allDestinations = teleportManager.getAllDestinations();

            // Create default tier assignments if none exist
            for (TeleportDestination dest : allDestinations) {
                int destTier = getDestinationTier(dest);
                tieredDestinations.computeIfAbsent(destTier, k -> new ArrayList<>()).add(dest);
            }

            // Ensure we have at least some fallback in every tier
            ensureTierHasDestinations(1, allDestinations);
            ensureTierHasDestinations(2, allDestinations);
            ensureTierHasDestinations(3, allDestinations);
            ensureTierHasDestinations(4, allDestinations);
            ensureTierHasDestinations(5, allDestinations);
            ensureTierHasDestinations(6, allDestinations);
        }
    }

    /**
     * Ensures a tier has at least one destination
     *
     * @param tier            The tier to check
     * @param allDestinations All available destinations
     */
    private void ensureTierHasDestinations(int tier, Collection<TeleportDestination> allDestinations) {
        if (!tieredDestinations.containsKey(tier) || tieredDestinations.get(tier).isEmpty()) {
            List<TeleportDestination> fallbackList = new ArrayList<>(allDestinations);
            if (!fallbackList.isEmpty()) {
                tieredDestinations.put(tier, fallbackList);
            }
        }
    }

    /**
     * Gets the tier of a destination based on its properties
     *
     * @param destination The destination
     * @return The assigned tier (1-6)
     */
    private int getDestinationTier(TeleportDestination destination) {
        // Check if destination has a tier in its metadata
        if (destination.isPremium()) {
            return Math.min(5, Math.max(3, destination.getCost() / 20)); // Premium destinations are higher tier
        } else {
            return Math.min(3, Math.max(1, destination.getCost() / 25)); // Regular destinations are lower tier
        }
    }

    /**
     * Determines the scroll destination ID based on tier - dynamically selects from available destinations
     *
     * @param tier The tier level
     * @return The destination ID
     */
    private String getScrollDestinationId(int tier) {
        refreshDestinationCache();

        List<TeleportDestination> eligibleDestinations = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Select appropriate tier range based on loot tier
        switch (tier) {
            case 1:
            case 2:
                // Lower tier mobs drop lower tier destinations
                addTierToEligible(eligibleDestinations, 1);
                addTierToEligible(eligibleDestinations, 2);
                break;
            case 3:
                // Mid tier mobs drop mid tier destinations
                addTierToEligible(eligibleDestinations, 2);
                addTierToEligible(eligibleDestinations, 3);
                break;
            case 4:
                // Higher mid tier mobs can drop some premium destinations
                addTierToEligible(eligibleDestinations, 3);
                addTierToEligible(eligibleDestinations, 4);
                break;
            case 5:
            case 6:
                // High tier mobs drop high tier destinations
                addTierToEligible(eligibleDestinations, 4);
                addTierToEligible(eligibleDestinations, 5);
                addTierToEligible(eligibleDestinations, 6);
                break;
            default:
                // Fallback to basic destinations
                addTierToEligible(eligibleDestinations, 1);
                break;
        }

        // If we have no eligible destinations, use all destinations as fallback
        if (eligibleDestinations.isEmpty()) {
            Collection<TeleportDestination> allDestinations = teleportManager.getAllDestinations();
            if (!allDestinations.isEmpty()) {
                eligibleDestinations.addAll(allDestinations);
            } else {
                // No destinations at all, return a default ID
                return "deadpeaks";
            }
        }

        // Select a random destination from the eligible list
        TeleportDestination selected = eligibleDestinations.get(random.nextInt(eligibleDestinations.size()));
        return selected.getId();
    }

    /**
     * Adds destinations of a specific tier to the eligible list
     *
     * @param eligibleList The list to add to
     * @param tier         The tier to add
     */
    private void addTierToEligible(List<TeleportDestination> eligibleList, int tier) {
        if (tieredDestinations.containsKey(tier)) {
            eligibleList.addAll(tieredDestinations.get(tier));
        }
    }
}
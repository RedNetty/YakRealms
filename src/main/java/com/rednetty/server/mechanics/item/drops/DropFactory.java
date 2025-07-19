package com.rednetty.server.mechanics.item.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
import com.rednetty.server.mechanics.item.scroll.ItemAPI;
import com.rednetty.server.mechanics.world.teleport.TeleportBookSystem;
import com.rednetty.server.mechanics.world.teleport.TeleportDestination;
import com.rednetty.server.mechanics.world.teleport.TeleportManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *  factory class for creating different types of drops with improved performance and visual effects
 */
public class DropFactory {
    private static DropFactory instance;
    private final Logger logger;
    private final YakRealms plugin;
    private final TeleportManager teleportManager;
    private final TeleportBookSystem teleportBookSystem;

    //  cache management
    private final Map<Integer, List<TeleportDestination>> tieredDestinations = new HashMap<>();
    private volatile long lastDestinationCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(1); // More readable

    // Constants for gem calculation
    private static final int GEM_BASE_MULTIPLIER = 3;
    private static final int MAX_GEM_STACK = 64;

    //  crate visual effects with Tier 6 Netherite integration
    private static final Map<Integer, ChatColor> TIER_COLORS = Map.of(
            1, ChatColor.WHITE,
            2, ChatColor.GREEN,
            3, ChatColor.AQUA,
            4, ChatColor.LIGHT_PURPLE,
            5, ChatColor.YELLOW,
            6, ChatColor.GOLD  // Updated for Netherite
    );

    /**
     * Private constructor for singleton pattern
     */
    private DropFactory() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.teleportManager = TeleportManager.getInstance();
        this.teleportBookSystem = TeleportBookSystem.getInstance();

        // Initialize caches
        refreshDestinationCache();
    }

    /**
     * Gets the singleton instance with thread safety
     *
     * @return The DropFactory instance
     */
    public static synchronized DropFactory getInstance() {
        if (instance == null) {
            instance = new DropFactory();
        }
        return instance;
    }

    /**
     * Creates a drop for a normal mob - delegates to DropsManager with  logging
     *
     * @param tier     The tier level (1-6)
     * @param itemType The item type (1-8)
     * @return The created ItemStack
     */
    public ItemStack createNormalDrop(int tier, int itemType) {
        validateDropParameters(tier, itemType);

        try {
            ItemStack drop = DropsManager.createDrop(tier, itemType);
            logDropCreation("normal", tier, itemType, 0);
            return drop;
        } catch (Exception e) {
            logger.warning("Failed to create normal drop for tier " + tier + ", itemType " + itemType + ": " + e.getMessage());
            return createFallbackDrop();
        }
    }

    /**
     * Creates a drop for an elite mob with  error handling
     *
     * @param tier     The tier level (1-6)
     * @param itemType The item type (1-8)
     * @param rarity   The rarity level (1-4)
     * @return The created ItemStack
     */
    public ItemStack createEliteDrop(int tier, int itemType, int rarity) {
        validateDropParameters(tier, itemType);
        validateRarity(rarity);

        try {
            ItemStack drop = DropsManager.getInstance().createDrop(tier, itemType, rarity);
            logDropCreation("elite", tier, itemType, rarity);
            return drop;
        } catch (Exception e) {
            logger.warning("Failed to create elite drop for tier " + tier + ", itemType " + itemType +
                    ", rarity " + rarity + ": " + e.getMessage());
            return createFallbackDrop();
        }
    }

    /**
     * Creates an  crate drop with improved visual design
     *
     * @param tier The tier level (1-6)
     * @return The created crate ItemStack with  visuals
     */
    public ItemStack createCrateDrop(int tier) {
        return YakRealms.getInstance().getCrateManager().createCrate(CrateType.getByTier(tier, false),false);
    }

    /**
     * Creates  gem drop with tier-based scaling
     *
     * @param tier The tier level (1-6)
     * @return The created gem ItemStack
     */
    public ItemStack createGemDrop(int tier) {
        validateTier(tier);

        try {
            int gemAmount = calculateGemAmount(tier);
            ItemStack gems = MoneyManager.makeGems(gemAmount);

            logDropCreation("gem", tier, 0, 0);
            return gems;
        } catch (Exception e) {
            logger.warning("Failed to create gem drop for tier " + tier + ": " + e.getMessage());
            // Fallback to basic gem amount
            return MoneyManager.makeGems(tier * 5);
        }
    }

    /**
     * Creates scroll drop with  destination selection and error handling
     *
     * @param tier The tier level (1-6)
     * @return The created scroll ItemStack
     */
    public ItemStack createScrollDrop(int tier) {
        validateTier(tier);

        try {
            return ItemAPI.getScrollGenerator().createEnhancementScroll(tier, ThreadLocalRandom.current().nextInt(0, 1));

        } catch (Exception e) {
            return createGenericScrollItem("Unknown Destination", tier);
        }
    }

    /**
     * Creates  generic scroll with tier-based visual effects
     */
    private ItemStack createGenericScrollItem(String destinationName, int tier) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        ItemMeta meta = scroll.getItemMeta();

        if (meta != null) {
            ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.YELLOW);

            meta.setDisplayName(tierColor + "⚡ " + ChatColor.BOLD + destinationName +
                    " Teleport Scroll " + ChatColor.RESET + tierColor + "⚡");

            List<String> lore = Arrays.asList(
                    "",
                    ChatColor.GRAY + "Right-click to teleport to " + ChatColor.WHITE + destinationName,
                    ChatColor.GRAY + "Tier: " + tierColor + tier,
                    "",
                    tierColor + "✨ " + ChatColor.ITALIC + "Mystical transportation awaits..."
            );
            meta.setLore(lore);

            // Add visual enhancements
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    /**
     * Refreshes the destination tier cache with improved performance
     */
    private synchronized void refreshDestinationCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDestinationCacheUpdate < CACHE_REFRESH_INTERVAL && !tieredDestinations.isEmpty()) {
            return;
        }

        lastDestinationCacheUpdate = currentTime;
        tieredDestinations.clear();

        try {
            Collection<TeleportDestination> allDestinations = teleportManager.getAllDestinations();

            if (allDestinations.isEmpty()) {
                logger.warning("No teleport destinations available for scroll drops");
                return;
            }

            // Group destinations by tier with  logic
            for (TeleportDestination dest : allDestinations) {
                int destTier = calculateDestinationTier(dest);
                tieredDestinations.computeIfAbsent(destTier, k -> new ArrayList<>()).add(dest);
            }

            // Ensure fallback destinations for all tiers
            ensureAllTiersHaveDestinations(allDestinations);

            logger.fine("Refreshed destination cache with " + allDestinations.size() + " destinations across " +
                    tieredDestinations.size() + " tiers");

        } catch (Exception e) {
            logger.severe("Failed to refresh destination cache: " + e.getMessage());
        }
    }

    /**
     *  destination tier calculation with better logic
     */
    private int calculateDestinationTier(TeleportDestination destination) {
        if (destination.isPremium()) {
            // Premium destinations map to higher tiers (3-6)
            int baseTier = Math.max(3, Math.min(6, destination.getCost() / 15));
            return baseTier;
        } else {
            // Regular destinations map to lower tiers (1-4)
            int baseTier = Math.max(1, Math.min(4, destination.getCost() / 20));
            return baseTier;
        }
    }

    /**
     * Ensures all tiers have at least one destination available
     */
    private void ensureAllTiersHaveDestinations(Collection<TeleportDestination> allDestinations) {
        List<TeleportDestination> fallbackList = new ArrayList<>(allDestinations);

        for (int tier = 1; tier <= 6; tier++) {
            if (!tieredDestinations.containsKey(tier) || tieredDestinations.get(tier).isEmpty()) {
                tieredDestinations.put(tier, new ArrayList<>(fallbackList));
            }
        }
    }

    /**
     * Selects optimal destination based on tier with improved algorithm
     */
    private String selectOptimalDestination(int tier) {
        refreshDestinationCache();

        List<TeleportDestination> eligibleDestinations = new ArrayList<>();

        //  tier-based destination selection
        switch (tier) {
            case 1:
            case 2:
                addDestinationsFromTiers(eligibleDestinations, 1, 2);
                break;
            case 3:
                addDestinationsFromTiers(eligibleDestinations, 2, 3, 4);
                break;
            case 4:
                addDestinationsFromTiers(eligibleDestinations, 3, 4, 5);
                break;
            case 5:
            case 6:
                addDestinationsFromTiers(eligibleDestinations, 4, 5, 6);
                break;
            default:
                addDestinationsFromTiers(eligibleDestinations, 1);
                break;
        }

        if (eligibleDestinations.isEmpty()) {
            // Ultimate fallback
            return tieredDestinations.values().stream()
                    .flatMap(List::stream)
                    .findFirst()
                    .map(TeleportDestination::getId)
                    .orElse("deadpeaks");
        }

        // Weighted random selection favoring appropriate tier destinations
        return selectWeightedDestination(eligibleDestinations, tier);
    }

    /**
     * Adds destinations from multiple tiers to the eligible list
     */
    private void addDestinationsFromTiers(List<TeleportDestination> eligibleList, int... tiers) {
        for (int tier : tiers) {
            List<TeleportDestination> tierDestinations = tieredDestinations.get(tier);
            if (tierDestinations != null) {
                eligibleList.addAll(tierDestinations);
            }
        }
    }

    /**
     * Selects destination with weighted probability favoring tier-appropriate destinations
     */
    private String selectWeightedDestination(List<TeleportDestination> destinations, int targetTier) {
        if (destinations.size() == 1) {
            return destinations.get(0).getId();
        }

        // Create weighted list favoring destinations closer to target tier
        List<TeleportDestination> weightedList = new ArrayList<>();

        for (TeleportDestination dest : destinations) {
            int destTier = calculateDestinationTier(dest);
            int weight = Math.max(1, 4 - Math.abs(targetTier - destTier)); // Higher weight for closer tiers

            for (int i = 0; i < weight; i++) {
                weightedList.add(dest);
            }
        }

        TeleportDestination selected = weightedList.get(ThreadLocalRandom.current().nextInt(weightedList.size()));
        return selected.getId();
    }

    /**
     *  fallback destination selection
     */
    private TeleportDestination getFallbackDestination() {
        return teleportManager.getAllDestinations().stream()
                .filter(dest -> !dest.isPremium()) // Prefer non-premium for fallback
                .findFirst()
                .orElse(teleportManager.getAllDestinations().stream().findFirst().orElse(null));
    }

    /**
     * Creates  lore for crates with visual elements
     */
    private List<String> createCrateLore(int tier, ChatColor tierColor) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Right-click to " + ChatColor.GREEN + ChatColor.BOLD + "OPEN");
        lore.add("");
        lore.add(ChatColor.GRAY + "Contains: " + tierColor + ChatColor.BOLD + "Tier " + tier + " Items");
        lore.add(ChatColor.GRAY + "Quality: " + getRarityIndicator(tier));
        lore.add("");

        // Add tier-specific flavor text
        String flavorText = getTierFlavorText(tier);
        if (!flavorText.isEmpty()) {
            lore.add(ChatColor.DARK_GRAY.toString() + ChatColor.ITALIC + flavorText);
            lore.add("");
        }

        lore.add(tierColor + "✨ " + ChatColor.BOLD + "MAGICAL CONTAINER" + ChatColor.RESET + " " + tierColor + "✨");
        return lore;
    }

    /**
     * Gets rarity indicator based on tier
     */
    private String getRarityIndicator(int tier) {
        return switch (tier) {
            case 1, 2 -> ChatColor.WHITE + "Common-Uncommon";
            case 3, 4 -> ChatColor.GREEN + "Uncommon-Rare";
            case 5, 6 -> ChatColor.YELLOW + "Rare-Legendary";
            default -> ChatColor.GRAY + "Unknown";
        };
    }

    /**
     * Gets flavor text for different tiers
     */
    private String getTierFlavorText(int tier) {
        return switch (tier) {
            case 1 -> "A simple container holding basic treasures";
            case 2 -> "Reinforced chest containing quality items";
            case 3 -> "Mystical container radiating magical energy";
            case 4 -> "Ancient chest blessed by powerful forces";
            case 5 -> "Legendary container of immense power";
            case 6 -> "Nether Forged chest forged in the depths of the Nether";
            default -> "";
        };
    }

    /**
     * Calculates gem amount with  scaling
     */
    private int calculateGemAmount(int tier) {
        int baseAmount = 1;
        for (int i = 0; i < tier; i++) {
            baseAmount *= GEM_BASE_MULTIPLIER;
        }
        return Math.min(baseAmount, MAX_GEM_STACK);
    }

    /**
     * Enhances scroll visuals based on tier
     */
    private void enhanceScrollVisuals(ItemStack scroll, int tier) {
        ItemMeta meta = scroll.getItemMeta();
        if (meta != null && meta.hasLore()) {
            List<String> currentLore = new ArrayList<>(meta.getLore());
            ChatColor tierColor = TIER_COLORS.getOrDefault(tier, ChatColor.YELLOW);

            // Add tier information to existing lore
            currentLore.add("");
            currentLore.add(ChatColor.GRAY + "Scroll Tier: " + tierColor + ChatColor.BOLD + tier);
            currentLore.add(tierColor + "⚡ Instant Teleportation ⚡");

            meta.setLore(currentLore);
            scroll.setItemMeta(meta);
        }
    }

    /**
     * Parameter validation methods
     */
    private void validateDropParameters(int tier, int itemType) {
        validateTier(tier);
        if (itemType < 1 || itemType > 8) {
            throw new IllegalArgumentException("Item type must be between 1 and 8, got: " + itemType);
        }
    }

    private void validateTier(int tier) {
        if (tier < 1 || tier > 6) {
            throw new IllegalArgumentException("Tier must be between 1 and 6, got: " + tier);
        }
    }

    private void validateRarity(int rarity) {
        if (rarity < 1 || rarity > 4) {
            throw new IllegalArgumentException("Rarity must be between 1 and 4, got: " + rarity);
        }
    }

    /**
     *  logging for drop creation
     */
    private void logDropCreation(String dropType, int tier, int itemType, int rarity) {
        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine(String.format("Created %s drop: tier=%d, itemType=%d, rarity=%d",
                    dropType, tier, itemType, rarity));
        }
    }

    /**
     * Creates a fallback drop in case of errors
     */
    private ItemStack createFallbackDrop() {
        ItemStack fallback = new ItemStack(Material.STONE);
        ItemMeta meta = fallback.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Drop");
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Something went wrong during drop creation"));
            fallback.setItemMeta(meta);
        }
        return fallback;
    }

    /**
     * Gets cache statistics for debugging
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("tieredDestinations", tieredDestinations.size());
        stats.put("totalDestinations", tieredDestinations.values().stream().mapToInt(List::size).sum());
        stats.put("lastCacheUpdate", new Date(lastDestinationCacheUpdate));
        stats.put("cacheAge", System.currentTimeMillis() - lastDestinationCacheUpdate);
        return stats;
    }

    /**
     * Forces cache refresh (for admin commands)
     */
    public void forceCacheRefresh() {
        lastDestinationCacheUpdate = 0;
        refreshDestinationCache();
        logger.info("Destination cache forcefully refreshed");
    }
}
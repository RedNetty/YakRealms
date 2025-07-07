package com.rednetty.server.mechanics.world.lootchests;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.DropFactory;
import com.rednetty.server.mechanics.world.lootchests.types.ChestTier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Factory class for creating loot chest contents and managing loot distribution
 */
public class LootChestFactory {
    private final Logger logger;
    private final DropFactory dropFactory;

    private static final double[] GEM_CHANCES = {0.6, 0.65, 0.7, 0.75, 0.8, 0.85};
    private static final double[] SCROLL_CHANCES = {0.15, 0.20, 0.25, 0.30, 0.35, 0.40};
    private static final double[] CRATE_CHANCES = {0.08, 0.12, 0.18, 0.25, 0.30, 0.35};
    private static final double[] ARTIFACT_CHANCES = {0.02, 0.05, 0.08, 0.12, 0.18, 0.25};

    public LootChestFactory() {
        this.logger = YakRealms.getInstance().getLogger();
        this.dropFactory = DropFactory.getInstance();
    }

    /**
     * Initializes the factory
     */
    public void initialize() {
        logger.info("LootChestFactory initialized");
    }

    /**
     * Fills an inventory with loot based on the chest tier
     *
     * @param inventory The inventory to fill
     * @param tier      The chest tier
     * @param itemCount The number of items to generate
     */
    public void fillInventoryWithLoot(Inventory inventory, ChestTier tier, int itemCount) {
        for (int i = 0; i < itemCount && i < inventory.getSize(); i++) {
            ItemStack loot = generateLootItem(tier);
            if (loot != null) {
                inventory.setItem(i, loot);
            }
        }
    }

    /**
     * Fills an inventory with special high-quality loot
     *
     * @param inventory The inventory to fill
     * @param tier      The chest tier
     */
    public void fillInventoryWithSpecialLoot(Inventory inventory, ChestTier tier) {
        int slots = inventory.getSize();

        // Fill with guaranteed high-tier items
        for (int i = 0; i < Math.min(5, slots); i++) {
            ItemStack item = generateSpecialLootItem(tier);
            inventory.setItem(i, item);
        }

        // Fill remaining slots with normal loot
        for (int i = 6; i < slots; i++) {
            if (ThreadLocalRandom.current().nextDouble() < 0.7) { // 70% chance for additional items
                ItemStack item = generateLootItem(tier);
                if (item != null) {
                    inventory.setItem(i, item);
                }
            }
        }
    }

    /**
     * Drops the contents of an inventory at a location
     *
     * @param location The location to drop items
     * @param contents The items to drop
     */
    public void dropInventoryContents(Location location, ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    /**
     * Drops a single loot item at a location
     *
     * @param location The location to drop the item
     * @param tier     The chest tier
     */
    public void dropSingleLootItem(Location location, ChestTier tier) {
        ItemStack loot = generateLootItem(tier);
        if (loot != null) {
            location.getWorld().dropItemNaturally(location, loot);
        }
    }

    /**
     * Generates a random loot item based on chest tier
     *
     * @param tier The chest tier
     * @return The generated loot item
     */
    private ItemStack generateLootItem(ChestTier tier) {
        double random = ThreadLocalRandom.current().nextDouble();
        int tierLevel = tier.getLevel();

        // Determine loot type based on probabilities
        if (random < GEM_CHANCES[tierLevel - 1]) {
            return generateGems(tier);
        } else if (random < GEM_CHANCES[tierLevel - 1] + SCROLL_CHANCES[tierLevel - 1]) {
            return generateScroll(tier);
        } else if (random < GEM_CHANCES[tierLevel - 1] + SCROLL_CHANCES[tierLevel - 1] + CRATE_CHANCES[tierLevel - 1]) {
            return generateCrate(tier);
        } else if (random < GEM_CHANCES[tierLevel - 1] + SCROLL_CHANCES[tierLevel - 1] +
                CRATE_CHANCES[tierLevel - 1] + ARTIFACT_CHANCES[tierLevel - 1]) {
            return generateArtifact(tier);
        } else {
            return generateEquipment(tier);
        }
    }

    /**
     * Generates special high-quality loot
     *
     * @param tier The chest tier
     * @return The generated special loot item
     */
    private ItemStack generateSpecialLootItem(ChestTier tier) {
        double random = ThreadLocalRandom.current().nextDouble();

        if (random < 0.4) {
            // 40% chance for equipment with higher rarity
            return generateHighQualityEquipment(tier);
        } else if (random < 0.7) {
            // 30% chance for large gem stacks
            return generateLargeGemStack(tier);
        } else if (random < 0.9) {
            // 20% chance for multiple scrolls
            return generateScrollBundle(tier);
        } else {
            // 10% chance for rare artifacts
            return generateRareArtifact(tier);
        }
    }

    /**
     * Generates gems based on tier
     */
    private ItemStack generateGems(ChestTier tier) {
        try {
            return YakRealms.getInstance().getBankManager().createBankNote(tier.getLevel() * ThreadLocalRandom.current().nextInt(125, 185));
        } catch (Exception e) {
            logger.warning("Failed to create gem drop: " + e.getMessage());
            return createFallbackGems(tier);
        }
    }

    /**
     * Generates a teleport scroll based on tier
     */
    private ItemStack generateScroll(ChestTier tier) {
        try {
            return dropFactory.createScrollDrop(tier.getLevel());
        } catch (Exception e) {
            logger.warning("Failed to create scroll drop: " + e.getMessage());
            return createFallbackScroll(tier);
        }
    }

    /**
     * Generates a crate based on tier
     */
    private ItemStack generateCrate(ChestTier tier) {
        try {
            return dropFactory.createCrateDrop(tier.getLevel());
        } catch (Exception e) {
            logger.warning("Failed to create crate drop: " + e.getMessage());
            return createFallbackCrate(tier);
        }
    }

    /**
     * Generates an elemental artifact
     */
    private ItemStack generateArtifact(ChestTier tier) {
/*        try {
            ElementalArtifacts.ElementType[] elements = ElementalArtifacts.ElementType.values();
            ElementalArtifacts.ElementType randomElement = elements[
                    ThreadLocalRandom.current().nextInt(elements.length)
                    ];

            int quality = ThreadLocalRandom.current().nextInt(4) + 1;
            return ElementalArtifacts.createElementalArtifact(tier.getLevel(), quality, randomElement);
        } catch (Exception e) {
            logger.warning("Failed to create artifact: " + e.getMessage());*/
            return generateEquipment(tier);
        //}
    }

    /**
     * Generates equipment based on tier
     */
    private ItemStack generateEquipment(ChestTier tier) {
        try {
            int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
            return dropFactory.createNormalDrop(tier.getLevel(), itemType);
        } catch (Exception e) {
            logger.warning("Failed to create equipment drop: " + e.getMessage());
            return createFallbackItem(tier);
        }
    }

    /**
     * Generates high-quality equipment with better rarity
     */
    private ItemStack generateHighQualityEquipment(ChestTier tier) {
        try {
            int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
            int rarity = Math.min(4, ThreadLocalRandom.current().nextInt(2) + 3); // Rare or Unique
            return dropFactory.createEliteDrop(tier.getLevel(), itemType, rarity);
        } catch (Exception e) {
            logger.warning("Failed to create high-quality equipment: " + e.getMessage());
            return generateEquipment(tier);
        }
    }

    /**
     * Generates a large stack of gems
     */
    private ItemStack generateLargeGemStack(ChestTier tier) {
        ItemStack gems = generateGems(tier);
        if (gems != null) {
            // Multiply amount by 2-4x for special chests
            int multiplier = ThreadLocalRandom.current().nextInt(3) + 2;
            gems.setAmount(Math.min(64, gems.getAmount() * multiplier));
        }
        return gems;
    }

    /**
     * Generates a bundle of scrolls
     */
    private ItemStack generateScrollBundle(ChestTier tier) {
        ItemStack scroll = generateScroll(tier);
        if (scroll != null) {
            // Give 2-5 scrolls instead of 1
            int amount = ThreadLocalRandom.current().nextInt(4) + 2;
            scroll.setAmount(Math.min(64, amount));
        }
        return scroll;
    }

    /**
     * Generates a rare artifact with guaranteed high quality
     */
    private ItemStack generateRareArtifact(ChestTier tier) {
 /*       try {
            ElementalArtifacts.ElementType[] elements = ElementalArtifacts.ElementType.values();
            ElementalArtifacts.ElementType randomElement = elements[
                    ThreadLocalRandom.current().nextInt(elements.length)
                    ];

            // Guarantee high quality (3-4)
            int quality = ThreadLocalRandom.current().nextInt(2) + 3;
            return ElementalArtifacts.createElementalArtifact(tier.getLevel(), quality, randomElement);
        } catch (Exception e) {
            logger.warning("Failed to create rare artifact: " + e.getMessage());*/
            return generateHighQualityEquipment(tier);
        //}
    }

    // === Fallback Methods ===

    /**
     * Creates fallback gems if the drop factory fails
     */
    private ItemStack createFallbackGems(ChestTier tier) {
        try {
            return YakRealms.getInstance().getBankManager().createBankNote(tier.getLevel() * 50);
        } catch (Exception e) {
            // Ultimate fallback - create a basic item
            ItemStack fallback = new ItemStack(Material.EMERALD, tier.getLevel() * 10);
            return fallback;
        }
    }

    /**
     * Creates fallback scroll if the drop factory fails
     */
    private ItemStack createFallbackScroll(ChestTier tier) {
        ItemStack scroll = new ItemStack(Material.PAPER);
        // This would ideally use the scroll system, but as a fallback we create a basic item
        return scroll;
    }

    /**
     * Creates fallback crate if the drop factory fails
     */
    private ItemStack createFallbackCrate(ChestTier tier) {
        ItemStack crate = new ItemStack(Material.CHEST);
        // This would ideally use the crate system, but as a fallback we create a basic item
        return crate;
    }

    /**
     * Creates a basic fallback item
     */
    private ItemStack createFallbackItem(ChestTier tier) {
        Material material = switch (tier) {
            case WOODEN -> Material.WOODEN_SWORD;
            case STONE -> Material.STONE_SWORD;
            case IRON -> Material.IRON_SWORD;
            case DIAMOND -> Material.DIAMOND_SWORD;
            case GOLDEN -> Material.GOLDEN_SWORD;
            case LEGENDARY -> Material.NETHERITE_SWORD;
        };
        return new ItemStack(material);
    }

    /**
     * Calculates the optimal number of items for a chest based on tier and type
     *
     * @param tier     The chest tier
     * @param isSpecial Whether this is a special chest
     * @return The number of items to generate
     */
    public int calculateOptimalItemCount(ChestTier tier, boolean isSpecial) {
        int baseCount = switch (tier) {
            case WOODEN -> 1;
            case STONE -> 2;
            case IRON -> 3;
            case DIAMOND -> 4;
            case GOLDEN -> 5;
            case LEGENDARY -> 6;
        };

        if (isSpecial) {
            baseCount *= 2; // Special chests have more items
        }

        // Add some randomness
        return baseCount + ThreadLocalRandom.current().nextInt(2);
    }

    /**
     * Gets loot generation statistics for debugging
     *
     * @return Map of statistics
     */
    public List<String> getLootStatistics() {
        List<String> stats = new ArrayList<>();
        stats.add("Loot Generation Statistics:");

        for (ChestTier tier : ChestTier.values()) {
            int level = tier.getLevel();
            stats.add(String.format("%s (T%d): Gems: %.1f%%, Scrolls: %.1f%%, Crates: %.1f%%, Artifacts: %.1f%%",
                    tier.getDisplayName(), level,
                    GEM_CHANCES[level - 1] * 100,
                    SCROLL_CHANCES[level - 1] * 100,
                    CRATE_CHANCES[level - 1] * 100,
                    ARTIFACT_CHANCES[level - 1] * 100
            ));
        }

        return stats;
    }
}
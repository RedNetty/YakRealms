package com.rednetty.server.mechanics.world.lootchests.loot;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.crates.CrateManager;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.item.scroll.ScrollManager;
import com.rednetty.server.mechanics.world.lootchests.core.Chest;
import com.rednetty.server.mechanics.world.lootchests.types.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Generates loot for chests based on tier and type
 * Integrates with existing drop system while providing fallbacks
 */
public class LootGenerator {
    private final Logger logger;
    private final YakRealms plugin;

    // Loot probabilities by tier (0.0 to 1.0)
    private static final double[] GEM_CHANCES = {0.6, 0.65, 0.7, 0.75, 0.8, 0.85};
    private static final double[] SCROLL_CHANCES = {0.15, 0.20, 0.25, 0.30, 0.35, 0.40};
    private static final double[] CRATE_CHANCES = {0.08, 0.12, 0.18, 0.25, 0.30, 0.35};
    private static final double[] ARTIFACT_CHANCES = {0.02, 0.05, 0.08, 0.12, 0.18, 0.25};

    public LootGenerator() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Initializes the loot generator
     */
    public void initialize() {
        logger.info("Loot generator initialized successfully");
    }

    /**
     * Creates a loot inventory for a chest
     * @param chest The chest to generate loot for
     * @return Inventory containing generated loot
     */
    public Inventory createLootInventory(Chest chest) {
        if (chest == null) {
            throw new IllegalArgumentException("Cannot create loot inventory for null chest");
        }

        String title = getInventoryTitle(chest);
        int size = getInventorySize(chest.getType());
        Inventory inventory = Bukkit.createInventory(null, size, title);

        fillInventoryWithLoot(inventory, chest);

        return inventory;
    }

    /**
     * Drops loot directly into the world at the specified location
     * @param location Where to drop the loot
     * @param chest The chest to generate loot for
     */
    public void dropLoot(Location location, Chest chest) {
        if (location == null || chest == null) {
            logger.warning("Cannot drop loot: null location or chest");
            return;
        }

        try {
            int itemCount = calculateItemCount(chest);

            for (int i = 0; i < itemCount; i++) {
                ItemStack loot = generateLootItem(chest.getTier(), chest.getType());
                if (loot != null && loot.getType() != Material.AIR) {
                    location.getWorld().dropItemNaturally(location, loot);
                }
            }

            logger.fine("Dropped " + itemCount + " items for " + chest.getTier() + " " + chest.getType() + " chest");

        } catch (Exception e) {
            logger.warning("Failed to drop loot for chest " + chest.getLocation() + ": " + e.getMessage());

            // Emergency fallback - drop a basic item
            ItemStack fallback = createFallbackItem(chest.getTier());
            location.getWorld().dropItemNaturally(location, fallback);
        }
    }

    /**
     * Fills an inventory with loot based on the chest
     */
    private void fillInventoryWithLoot(Inventory inventory, Chest chest) {
        int itemCount = calculateItemCount(chest);
        int maxSlots = inventory.getSize();

        // Generate items for each slot
        for (int i = 0; i < itemCount && i < maxSlots; i++) {
            ItemStack loot = generateLootItem(chest.getTier(), chest.getType());
            if (loot != null && loot.getType() != Material.AIR) {
                inventory.setItem(i, loot);
            }
        }

        // Special handling for special chests - add bonus items
        if (chest.getType() == ChestType.SPECIAL) {
            addSpecialBonusLoot(inventory, chest.getTier(), itemCount, maxSlots);
        }
    }

    /**
     * Generates a single loot item based on tier and type
     */
    private ItemStack generateLootItem(ChestTier tier, ChestType type) {
        try {
            double random = ThreadLocalRandom.current().nextDouble();
            int tierLevel = tier.getLevel() - 1; // Convert to 0-based index

            // Determine loot type based on probabilities
            double gemChance = GEM_CHANCES[tierLevel];
            double scrollChance = SCROLL_CHANCES[tierLevel];
            double crateChance = CRATE_CHANCES[tierLevel];
            double artifactChance = ARTIFACT_CHANCES[tierLevel];

            if (random < gemChance) {
                return generateGems(tier, type);
            } else if (random < gemChance + scrollChance) {
                return generateScroll(tier);
            } else if (random < gemChance + scrollChance + crateChance) {
                return generateCrate(tier);
            } else if (random < gemChance + scrollChance + crateChance + artifactChance) {
                return generateArtifact(tier);
            } else {
                return generateEquipment(tier, type);
            }

        } catch (Exception e) {
            logger.warning("Failed to generate loot item for tier " + tier + ": " + e.getMessage());
            return createFallbackItem(tier);
        }
    }

    /**
     * Generates gems/currency using the existing bank system
     */
    private ItemStack generateGems(ChestTier tier, ChestType type) {
        try {
            if (plugin.getBankManager() != null) {
                int baseAmount = tier.getLevel() * ThreadLocalRandom.current().nextInt(125, 185);

                // Bonus for special chests
                if (type == ChestType.SPECIAL) {
                    baseAmount = (int) (baseAmount * 1.5);
                } else if (type == ChestType.CARE_PACKAGE) {
                    baseAmount = (int) (baseAmount * 2.0);
                }

                return plugin.getBankManager().createBankNote(baseAmount);
            }
        } catch (Exception e) {
            logger.fine("Bank manager not available, using fallback gems: " + e.getMessage());
        }

        // Fallback to emeralds
        return new ItemStack(Material.EMERALD, Math.min(64, tier.getLevel() * 10));
    }

    /**
     * Generates scrolls using the existing scroll system
     */
    private ItemStack generateScroll(ChestTier tier) {
        try {
            if (plugin.getDropsHandler() != null) {
                return ScrollManager.getScrollGenerator().createEnhancementScroll(tier.getLevel(), ThreadLocalRandom.current().nextInt(0, 1));
            }
        } catch (Exception e) {
            logger.fine("Drops handler not available, using fallback scroll: " + e.getMessage());
        }

        // Fallback to paper
        return new ItemStack(Material.PAPER, 1);
    }

    /**
     * Generates crates using the existing crate system
     */
    private ItemStack generateCrate(ChestTier tier) {
        try {
            if (plugin.getDropsHandler() != null) {
                return CrateManager.getInstance().createCrate(CrateType.getByTier(tier.getLevel(), false), false);
            }
        } catch (Exception e) {
            logger.fine("Drops handler not available, using fallback crate: " + e.getMessage());
        }

        // Fallback to chest
        return new ItemStack(Material.CHEST, 1);
    }

    /**
     * Generates artifacts (placeholder for future artifact system)
     */
    private ItemStack generateArtifact(ChestTier tier) {
        // Future: integrate with elemental artifacts system
        // For now, generate equipment as fallback
        return generateEquipment(tier, ChestType.NORMAL);
    }

    /**
     * Generates equipment using the existing drops system
     */
    private ItemStack generateEquipment(ChestTier tier, ChestType type) {
        try {
            if (plugin.getDropsHandler() != null) {
                int itemType = ThreadLocalRandom.current().nextInt(8) + 1;

                // Special chests get better quality equipment
                if (type == ChestType.SPECIAL || type == ChestType.CARE_PACKAGE) {
                    int rarity = Math.min(4, ThreadLocalRandom.current().nextInt(2) + 3); // Rare or Unique
                    return DropsManager.getInstance().createDrop(tier.getLevel(), itemType, rarity);
                } else {
                    return DropsManager.getInstance().createDrop(tier.getLevel(), itemType);
                }
            }
        } catch (Exception e) {
            logger.fine("Drops handler not available, using fallback equipment: " + e.getMessage());
        }

        return createFallbackItem(tier);
    }

    /**
     * Adds bonus loot for special chests
     */
    private void addSpecialBonusLoot(Inventory inventory, ChestTier tier, int baseItems, int maxSlots) {
        int bonusSlots = maxSlots - baseItems;
        int bonusItems = Math.min(bonusSlots, tier.getLevel()); // Up to tier level bonus items

        for (int i = 0; i < bonusItems; i++) {
            int slot = baseItems + i;
            if (slot < maxSlots && inventory.getItem(slot) == null) {
                // 70% chance for bonus item
                if (ThreadLocalRandom.current().nextDouble() < 0.7) {
                    ItemStack bonus = generateLootItem(tier, ChestType.SPECIAL);
                    if (bonus != null) {
                        inventory.setItem(slot, bonus);
                    }
                }
            }
        }
    }

    /**
     * Creates a fallback item when other systems fail
     */
    private ItemStack createFallbackItem(ChestTier tier) {
        Material material = switch (tier) {
            case WOODEN -> Material.WOODEN_SWORD;
            case STONE -> Material.STONE_SWORD;
            case IRON -> Material.IRON_SWORD;
            case DIAMOND -> Material.DIAMOND_SWORD;
            case LEGENDARY -> Material.GOLDEN_SWORD;
            case NETHER_FORGED -> Material.NETHERITE_SWORD;
        };
        return new ItemStack(material, 1);
    }

    /**
     * Calculates how many items to generate based on chest properties
     */
    private int calculateItemCount(Chest chest) {
        int baseCount = chest.getTier().getLevel(); // 1-6 base items

        // Type modifiers
        switch (chest.getType()) {
            case SPECIAL -> baseCount = (int) (baseCount * 1.5); // 50% more items
            case CARE_PACKAGE -> baseCount = Math.max(baseCount, 4); // At least 4 items
        }

        // Add randomness (±1 item)
        int variance = ThreadLocalRandom.current().nextInt(3) - 1; // -1, 0, or 1
        baseCount = Math.max(1, baseCount + variance);

        return baseCount;
    }

    /**
     * Gets the appropriate inventory title for a chest
     */
    private String getInventoryTitle(Chest chest) {
        return switch (chest.getType()) {
            case CARE_PACKAGE -> "§6Care Package";
            case SPECIAL -> "§5Special Loot Chest";
            default -> chest.getTier().getColor() + "Loot Chest (Tier " + chest.getTier().getLevel() + ")";
        };
    }

    /**
     * Gets the appropriate inventory size for a chest type
     */
    private int getInventorySize(ChestType type) {
        return switch (type) {
            case CARE_PACKAGE -> 9;  // Single row for care packages
            case SPECIAL -> 27;      // Full chest for special chests
            default -> 27;           // Full chest for normal chests
        };
    }

    // === Statistics and Debugging ===

    /**
     * Gets loot generation statistics for debugging
     */
    public java.util.List<String> getLootStatistics() {
        java.util.List<String> stats = new java.util.ArrayList<>();
        stats.add("Loot Generation Statistics:");

        for (ChestTier tier : ChestTier.values()) {
            int level = tier.getLevel() - 1; // Convert to 0-based index
            stats.add(String.format("%s (T%d): Gems: %.1f%%, Scrolls: %.1f%%, Crates: %.1f%%, Artifacts: %.1f%%",
                    tier.getDisplayName(), tier.getLevel(),
                    GEM_CHANCES[level] * 100,
                    SCROLL_CHANCES[level] * 100,
                    CRATE_CHANCES[level] * 100,
                    ARTIFACT_CHANCES[level] * 100
            ));
        }

        return stats;
    }

    /**
     * Tests loot generation for a specific tier and type
     */
    public java.util.Map<String, Integer> testLootGeneration(ChestTier tier, ChestType type, int iterations) {
        java.util.Map<String, Integer> results = new java.util.HashMap<>();
        results.put("gems", 0);
        results.put("scrolls", 0);
        results.put("crates", 0);
        results.put("artifacts", 0);
        results.put("equipment", 0);
        results.put("errors", 0);

        for (int i = 0; i < iterations; i++) {
            try {
                ItemStack item = generateLootItem(tier, type);
                if (item != null) {
                    String category = categorizeItem(item);
                    results.put(category, results.get(category) + 1);
                }
            } catch (Exception e) {
                results.put("errors", results.get("errors") + 1);
            }
        }

        return results;
    }

    /**
     * Categorizes an item for statistics
     */
    private String categorizeItem(ItemStack item) {
        if (item == null) return "equipment";

        Material type = item.getType();

        if (type == Material.EMERALD || (item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().contains("Bank Note"))) {
            return "gems";
        } else if (type == Material.PAPER) {
            return "scrolls";
        } else if (type == Material.CHEST) {
            return "crates";
        } else {
            return "equipment";
        }
    }

    @Override
    public String toString() {
        return "LootGenerator{plugin=" + (plugin != null ? "available" : "null") + "}";
    }
}
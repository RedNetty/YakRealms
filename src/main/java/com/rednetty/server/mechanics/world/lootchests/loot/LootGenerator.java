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
 * Generates loot for chests based on tier and type.
 * Integrates with existing drop systems and provides fallbacks.
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
     * Initializes the loot generator.
     */
    public void initialize() {
        logger.info("Loot generator initialized successfully");
    }

    /**
     * Creates a loot inventory for a chest.
     *
     * @param chest The chest to generate loot for.
     * @return Inventory containing generated loot.
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
     * Drops loot directly into the world at the specified location.
     *
     * @param location Where to drop the loot.
     * @param chest The chest to generate loot for.
     */
    public void dropLoot(Location location, Chest chest) {
        if (location == null || chest == null) {
            logger.warning("Cannot drop loot: null location or chest");
            return;
        }

        try {
            Inventory lootInv = chest.getLootInventory();
            if (lootInv != null) {
                // Drop from existing inventory if available
                for (ItemStack item : lootInv.getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        location.getWorld().dropItemNaturally(location, item);
                    }
                }
                logger.fine("Dropped existing loot from chest at " + chest.getLocation());
            } else {
                // Fallback: generate and drop new
                int itemCount = calculateItemCount(chest);
                for (int i = 0; i < itemCount; i++) {
                    ItemStack loot = generateLootItem(chest.getTier(), chest.getType());
                    if (loot != null && loot.getType() != Material.AIR) {
                        location.getWorld().dropItemNaturally(location, loot);
                    }
                }
                logger.fine("Dropped " + itemCount + " new items for " + chest.getTier() + " " + chest.getType() + " chest");
            }

        } catch (Exception e) {
            logger.warning("Failed to drop loot for chest " + chest.getLocation() + ": " + e.getMessage());
            // Emergency fallback - drop a basic item
            ItemStack fallback = createFallbackItem(chest.getTier());
            location.getWorld().dropItemNaturally(location, fallback);
        }
    }

    /**
     * Fills an inventory with loot based on the chest.
     *
     * @param inventory The inventory to fill.
     * @param chest The chest providing tier and type.
     */
    private void fillInventoryWithLoot(Inventory inventory, Chest chest) {
        int itemCount = calculateItemCount(chest);
        int maxSlots = inventory.getSize();

        // Generate items for slots
        for (int i = 0; i < Math.min(itemCount, maxSlots); i++) {
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
     * Generates a single loot item based on tier and type.
     *
     * @param tier The chest tier.
     * @param type The chest type.
     * @return The generated ItemStack, or null on failure.
     */
    private ItemStack generateLootItem(ChestTier tier, ChestType type) {
        try {
            double random = ThreadLocalRandom.current().nextDouble();
            int tierLevel = tier.getLevel() - 1; // 0-based index

            // Get probabilities
            double gemChance = GEM_CHANCES[tierLevel];
            double scrollChance = SCROLL_CHANCES[tierLevel];
            double crateChance = CRATE_CHANCES[tierLevel];
            double artifactChance = ARTIFACT_CHANCES[tierLevel];

            // Calculate total defined prob
            double totalProb = gemChance + scrollChance + crateChance + artifactChance;

            // Normalize if >1
            double scale = 1.0;
            if (totalProb > 1.0) {
                scale = 1.0 / totalProb;
                gemChance *= scale;
                scrollChance *= scale;
                crateChance *= scale;
                artifactChance *= scale;
                totalProb = 1.0;
            }

            // Equipment chance is remainder
            double equipmentChance = 1.0 - totalProb;

            // Cumulative checks
            double cumulative = 0.0;
            cumulative += gemChance;
            if (random < cumulative) {
                return generateGems(tier, type);
            }
            cumulative += scrollChance;
            if (random < cumulative) {
                return generateScroll(tier);
            }
            cumulative += crateChance;
            if (random < cumulative) {
                return generateCrate(tier);
            }
            cumulative += artifactChance;
            if (random < cumulative) {
                return generateArtifact(tier);
            }
            // Else equipment
            return generateEquipment(tier, type);

        } catch (Exception e) {
            logger.warning("Failed to generate loot item for tier " + tier + ": " + e.getMessage());
            return createFallbackItem(tier);
        }
    }

    /**
     * Generates gems/currency using the bank system or fallback.
     *
     * @param tier The chest tier.
     * @param type The chest type.
     * @return The gem ItemStack.
     */
    private ItemStack generateGems(ChestTier tier, ChestType type) {
        try {
            if (plugin.getBankManager() != null) {
                int baseAmount = tier.getLevel() * ThreadLocalRandom.current().nextInt(125, 185);

                // Bonus for special types
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
     * Generates a scroll using the scroll system or fallback.
     *
     * @param tier The chest tier.
     * @return The scroll ItemStack.
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
     * Generates a crate using the crate system or fallback.
     *
     * @param tier The chest tier.
     * @return The crate ItemStack.
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
     * Generates an artifact (placeholder).
     *
     * @param tier The chest tier.
     * @return The artifact ItemStack.
     */
    private ItemStack generateArtifact(ChestTier tier) {
        // Future: integrate artifact system
        // Fallback to equipment
        return generateEquipment(tier, ChestType.NORMAL);
    }

    /**
     * Generates equipment using the drops system or fallback.
     *
     * @param tier The chest tier.
     * @param type The chest type.
     * @return The equipment ItemStack.
     */
    private ItemStack generateEquipment(ChestTier tier, ChestType type) {
        try {
            if (plugin.getDropsHandler() != null) {
                int itemType = ThreadLocalRandom.current().nextInt(8) + 1;

                // Better quality for special types
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
     * Adds bonus loot for special chests.
     *
     * @param inventory The inventory to add to.
     * @param tier The chest tier.
     * @param baseItems The base item count.
     * @param maxSlots The max slots in inventory.
     */
    private void addSpecialBonusLoot(Inventory inventory, ChestTier tier, int baseItems, int maxSlots) {
        int bonusSlots = maxSlots - baseItems;
        int bonusItems = Math.min(bonusSlots, tier.getLevel());

        for (int i = 0; i < bonusItems; i++) {
            int slot = baseItems + i;
            if (slot < maxSlots && inventory.getItem(slot) == null) {
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
     * Creates a fallback item when generation fails.
     *
     * @param tier The chest tier.
     * @return The fallback ItemStack.
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
     * Calculates the number of items to generate.
     *
     * @param chest The chest.
     * @return The item count.
     */
    private int calculateItemCount(Chest chest) {
        int baseCount = chest.getTier().getLevel();

        // Type modifiers
        switch (chest.getType()) {
            case SPECIAL -> baseCount = (int) (baseCount * 1.5);
            case CARE_PACKAGE -> baseCount = Math.max(baseCount, 4);
        }

        // Random variance
        int variance = ThreadLocalRandom.current().nextInt(3) - 1;
        return Math.max(1, baseCount + variance);
    }

    /**
     * Gets the inventory title for the chest.
     *
     * @param chest The chest.
     * @return The title.
     */
    private String getInventoryTitle(Chest chest) {
        return switch (chest.getType()) {
            case CARE_PACKAGE -> "§6Care Package";
            case SPECIAL -> "§5Special Loot Chest";
            default -> chest.getTier().getColor() + "Loot Chest (Tier " + chest.getTier().getLevel() + ")";
        };
    }

    /**
     * Gets the inventory size for the chest type.
     *
     * @param type The chest type.
     * @return The size.
     */
    private int getInventorySize(ChestType type) {
        return switch (type) {
            case CARE_PACKAGE -> 9;
            case SPECIAL -> 27;
            default -> 27;
        };
    }

    // === Statistics and Debugging ===

    /**
     * Gets loot generation statistics.
     *
     * @return List of statistic strings.
     */
    public java.util.List<String> getLootStatistics() {
        java.util.List<String> stats = new java.util.ArrayList<>();
        stats.add("Loot Generation Statistics:");

        for (ChestTier tier : ChestTier.values()) {
            int level = tier.getLevel() - 1;
            double gem = GEM_CHANCES[level];
            double scroll = SCROLL_CHANCES[level];
            double crate = CRATE_CHANCES[level];
            double artifact = ARTIFACT_CHANCES[level];
            double total = gem + scroll + crate + artifact;
            double scale = total > 1.0 ? 1.0 / total : 1.0;
            double equipment = 1.0 - (total * scale);
            stats.add(String.format("%s (T%d): Gems: %.1f%%, Scrolls: %.1f%%, Crates: %.1f%%, Artifacts: %.1f%%, Equipment: %.1f%%",
                    tier.getDisplayName(), tier.getLevel(),
                    gem * scale * 100,
                    scroll * scale * 100,
                    crate * scale * 100,
                    artifact * scale * 100,
                    equipment * 100
            ));
        }

        return stats;
    }

    /**
     * Tests loot generation for statistics.
     *
     * @param tier The tier to test.
     * @param type The type to test.
     * @param iterations Number of iterations.
     * @return Map of category counts.
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
     * Categorizes an item for statistics.
     *
     * @param item The item to categorize.
     * @return The category string.
     */
    private String categorizeItem(ItemStack item) {
        if (item == null) return "equipment";

        Material mat = item.getType();

        if (mat == Material.EMERALD || (item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().contains("Bank Note"))) {
            return "gems";
        } else if (mat == Material.PAPER) {
            return "scrolls";
        } else if (mat == Material.CHEST) {
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
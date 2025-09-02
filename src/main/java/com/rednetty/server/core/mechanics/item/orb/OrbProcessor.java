package com.rednetty.server.core.mechanics.item.orb;

import org.bukkit.inventory.ItemStack;
import java.util.logging.Logger;

/**
 * MAJOR FIX: Simple wrapper for OrbManager to maintain backwards compatibility
 * This class now delegates all orb processing to OrbManager's complete stat replacement system
 */
public class OrbProcessor {
    private static final Logger LOGGER = Logger.getLogger(OrbProcessor.class.getName());
    private final OrbManager orbManager;

    /**
     * Constructor - gets OrbManager instance
     */
    public OrbProcessor() {
        this.orbManager = OrbManager.getInstance();
    }

    /**
     * CORE FIX: Apply an orb to an item with complete stat replacement
     * This method now delegates to OrbManager which completely rebuilds item lore
     *
     * @param item        The item to modify
     * @param isLegendary Whether using a legendary orb
     * @param bonusRolls  Bonus rolls for legendary orbs
     * @return The modified item with completely new stats (no duplicates)
     */
    public ItemStack applyOrbToItem(ItemStack item, boolean isLegendary, int bonusRolls) {
        if (item == null) {
            LOGGER.warning("§c[OrbProcessor] Cannot apply orb to null item");
            return null;
        }

        try {
            // Delegate to OrbManager for complete stat replacement
            ItemStack result = orbManager.applyOrbToItem(item, isLegendary, bonusRolls);

            LOGGER.fine("§a[OrbProcessor] §7Successfully processed orb application through OrbManager");
            return result;

        } catch (Exception e) {
            LOGGER.severe("§c[OrbProcessor] Error applying orb: " + e.getMessage());
            e.printStackTrace();
            return item; // Return original item if anything goes wrong
        }
    }

    /**
     * Validates if an item can have orbs applied to it
     *
     * @param item The item to validate
     * @return true if the item is valid for orb application
     */
    public boolean isValidForOrbApplication(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Use OrbAPI validation
        return OrbAPI.isValidItemForOrb(item);
    }

    /**
     * Gets item statistics for debugging orb application
     *
     * @param item The item to analyze
     * @return Diagnostic information about the item
     */
    public String getItemDiagnostics(ItemStack item) {
        if (item == null) {
            return "Item is null";
        }

        try {
            StringBuilder diagnostics = new StringBuilder();
            diagnostics.append("Item Type: ").append(item.getType().name()).append("\n");
            diagnostics.append("Has Meta: ").append(item.hasItemMeta()).append("\n");

            if (item.hasItemMeta()) {
                diagnostics.append("Display Name: ").append(
                        item.getItemMeta().hasDisplayName() ?
                                item.getItemMeta().getDisplayName() : "None"
                ).append("\n");

                diagnostics.append("Has Lore: ").append(item.getItemMeta().hasLore()).append("\n");
                if (item.getItemMeta().hasLore()) {
                    diagnostics.append("Lore Lines: ").append(item.getItemMeta().getLore().size()).append("\n");
                }
            }

            diagnostics.append("Detected Tier: ").append(OrbAPI.getItemTier(item)).append("\n");
            diagnostics.append("Detected Type: ").append(OrbAPI.getItemType(item)).append("\n");
            diagnostics.append("Is Weapon: ").append(OrbAPI.isWeapon(item)).append("\n");
            diagnostics.append("Is Armor: ").append(OrbAPI.isArmor(item)).append("\n");
            diagnostics.append("Valid for Orb: ").append(OrbAPI.isValidItemForOrb(item)).append("\n");
            diagnostics.append("Is Protected: ").append(OrbAPI.isProtected(item)).append("\n");

            return diagnostics.toString();

        } catch (Exception e) {
            return "Error generating diagnostics: " + e.getMessage();
        }
    }

    /**
     * Simulates orb application without actually modifying the item (for testing)
     *
     * @param item        The item to simulate orb application on
     * @param isLegendary Whether to simulate a legendary orb
     * @param bonusRolls  Number of bonus rolls to simulate
     * @return A description of what would happen
     */
    public String simulateOrbApplication(ItemStack item, boolean isLegendary, int bonusRolls) {
        if (item == null) {
            return "Cannot simulate on null item";
        }

        try {
            StringBuilder simulation = new StringBuilder();
            simulation.append("=== Orb Application Simulation ===\n");
            simulation.append("Orb Type: ").append(isLegendary ? "Legendary" : "Normal").append("\n");
            simulation.append("Bonus Rolls: ").append(bonusRolls).append("\n");
            simulation.append("Current Item: ").append(
                    item.getItemMeta().hasDisplayName() ?
                            item.getItemMeta().getDisplayName() :
                            item.getType().name()
            ).append("\n");

            int tier = OrbAPI.getItemTier(item);
            int itemType = OrbAPI.getItemType(item);
            boolean isWeapon = OrbAPI.isWeapon(item);

            simulation.append("Detected Tier: ").append(tier).append("\n");
            simulation.append("Detected Type: ").append(itemType).append("\n");
            simulation.append("Is Weapon: ").append(isWeapon).append("\n");

            if (tier > 0 && itemType != OrbAPI.TYPE_UNKNOWN) {
                simulation.append("\nWould generate new stats for:\n");
                simulation.append("- ").append(isWeapon ? "Weapon" : "Armor").append(" stats\n");
                simulation.append("- Tier ").append(tier).append(" scaling\n");
                simulation.append("- ").append(isLegendary ? "" : "Normal").append(" quality\n");

                if (isLegendary) {
                    simulation.append("- Guaranteed +4 enhancement (if below +4)\n");
                    simulation.append("- Bonus stat rolls: ").append(bonusRolls).append("\n");
                }

                simulation.append("\nAll existing stats would be completely replaced.\n");
            } else {
                simulation.append("\nERROR: Cannot determine item properties for orb application\n");
            }

            simulation.append("================================");
            return simulation.toString();

        } catch (Exception e) {
            return "Error in simulation: " + e.getMessage();
        }
    }

    /**
     * Legacy method for backwards compatibility
     * @deprecated Use applyOrbToItem instead
     */
    @Deprecated
    public ItemStack processOrb(ItemStack item, boolean isLegendary) {
        return applyOrbToItem(item, isLegendary, 0);
    }

    /**
     * Gets statistics about orb processing for monitoring
     *
     * @return Processing statistics
     */
    public String getProcessingStats() {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("=== Orb Processor Statistics ===\n");
            stats.append("Delegation Target: OrbManager\n");
            stats.append("Stat Replacement: Complete (No Duplicates)\n");
            stats.append("Legendary Enhancement: +4 Minimum\n");
            stats.append("Special Lore: Preserved\n");
            stats.append("Error Handling: Fallback to Original\n");
            stats.append("==============================");
            return stats.toString();
        } catch (Exception e) {
            return "Error getting stats: " + e.getMessage();
        }
    }
}
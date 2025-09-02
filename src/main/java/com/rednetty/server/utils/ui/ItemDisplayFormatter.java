package com.rednetty.server.utils.ui;

import com.rednetty.server.core.mechanics.item.drops.types.RarityConfig;
import com.rednetty.server.core.mechanics.item.drops.types.TierConfig;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Item Display Formatter - Enhanced formatting for premium items
 * 
 * Handles the application of gradient colors and special formatting
 * for T6 and Unique items to give them that premium feel.
 */
public class ItemDisplayFormatter {
    
    // ==================== MAIN FORMATTING METHODS ====================
    
    /**
     * Format an item name with appropriate tier coloring
     * T6 items get beautiful gradient treatment
     */
    public static String formatTierItemName(String itemName, int tier) {
        if (tier == 6) {
            return GradientColors.getT6Gradient(itemName);
        }
        
        TierConfig tierConfig = TierConfig.createDefault(tier);
        return tierConfig.getTierColorCode() + itemName;
    }
    
    /**
     * Format an item name with appropriate rarity coloring
     * Unique items get beautiful gradient treatment
     */
    public static String formatRarityItemName(String itemName, int rarity) {
        if (rarity == 4) { // Unique
            return GradientColors.getUniqueGradient(itemName);
        }
        
        RarityConfig rarityConfig = RarityConfig.createDefault(rarity);
        return rarityConfig.getColor() + itemName;
    }
    
    /**
     * Format a complete item display name with both tier and rarity
     * Handles special combinations like T6 Unique items
     */
    public static String formatCompleteItemName(String itemName, int tier, int rarity) {
        TierConfig tierConfig = TierConfig.createDefault(tier);
        RarityConfig rarityConfig = RarityConfig.createDefault(rarity);
        
        // Special handling for T6 Unique (ultimate combination)
        if (tier == 6 && rarity == 4) {
            // Create an ultra-premium gradient mixing both T6 and Unique colors
            String t6Part = GradientColors.getT6Gradient(itemName.substring(0, itemName.length() / 2));
            String uniquePart = GradientColors.getUniqueGradient(itemName.substring(itemName.length() / 2));
            return t6Part + uniquePart;
        }
        
        // T6 items use T6 gradient regardless of rarity
        if (tier == 6) {
            return GradientColors.getT6Gradient(itemName);
        }
        
        // Unique items use Unique gradient regardless of tier (unless T6)
        if (rarity == 4) {
            return GradientColors.getUniqueGradient(itemName);
        }
        
        // Standard formatting for non-premium items
        return tierConfig.getTierColorCode() + itemName;
    }
    
    // ==================== TIER DISPLAY FORMATTING ====================
    
    /**
     * Format tier display text (like "T6" or "Legendary")
     */
    public static String formatTierDisplay(int tier, boolean useRoman) {
        TierConfig tierConfig = TierConfig.createDefault(tier);
        String tierText = useRoman ? tierConfig.getTierRoman() : ("T" + tier);
        
        if (tier == 6) {
            return GradientColors.getT6Gradient(tierText);
        }
        
        return tierConfig.getTierColorCode() + tierText;
    }
    
    /**
     * Format tier name (like "Legendary", "Master", etc.)
     */
    public static String formatTierName(int tier) {
        TierConfig tierConfig = TierConfig.createDefault(tier);
        String tierName = tierConfig.getTierName();
        
        if (tier == 6) {
            return GradientColors.getT6Gradient(tierName);
        }
        
        return tierConfig.getTierColorCode() + tierName;
    }
    
    // ==================== RARITY DISPLAY FORMATTING ====================
    
    /**
     * Format rarity display text
     */
    public static String formatRarityDisplay(int rarity) {
        RarityConfig rarityConfig = RarityConfig.createDefault(rarity);
        String rarityName = rarityConfig.getName();
        
        if (rarity == 4) { // Unique
            return GradientColors.getUniqueGradient(rarityName);
        }
        
        return rarityConfig.getColor() + rarityName;
    }
    
    /**
     * Format rarity symbol (like ★, ◦, etc.)
     */
    public static String formatRaritySymbol(int rarity) {
        RarityConfig rarityConfig = RarityConfig.createDefault(rarity);
        String symbol = rarityConfig.getSymbol();
        
        if (rarity == 4) { // Unique
            return GradientColors.getUniqueGradient(symbol);
        }
        
        return rarityConfig.getColor() + symbol;
    }
    
    // ==================== ITEMSTACK FORMATTING ====================
    
    /**
     * Apply premium formatting to an ItemStack
     * Updates display name and lore with appropriate gradients
     */
    public static void formatItemStack(ItemStack item, int tier, int rarity) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        
        // Format the display name
        String currentName = meta.hasDisplayName() ? 
            ChatColor.stripColor(meta.getDisplayName()) : 
            item.getType().name().replace("_", " ");
            
        String formattedName = formatCompleteItemName(currentName, tier, rarity);
        meta.setDisplayName(formattedName);
        
        // Format the lore with premium styling
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        List<String> formattedLore = formatItemLore(lore, tier, rarity);
        meta.setLore(formattedLore);
        
        item.setItemMeta(meta);
    }
    
    /**
     * Format item lore with appropriate styling
     */
    public static List<String> formatItemLore(List<String> originalLore, int tier, int rarity) {
        List<String> formattedLore = new ArrayList<>();
        
        // Add tier and rarity display
        formattedLore.add("");
        formattedLore.add(formatTierDisplay(tier, false) + " " + formatRarityDisplay(rarity));
        formattedLore.add("");
        
        // Format each lore line
        if (originalLore != null) {
            for (String loreLine : originalLore) {
                if (loreLine == null || loreLine.trim().isEmpty()) {
                    formattedLore.add("");
                    continue;
                }
                
                String cleanLine = ChatColor.stripColor(loreLine);
                
                // Apply special formatting based on tier/rarity
                if (tier == 6) {
                    formattedLore.add(GradientColors.formatT6Lore(cleanLine));
                } else if (rarity == 4) {
                    formattedLore.add(GradientColors.formatUniqueLore(cleanLine));
                } else {
                    // Standard formatting
                    formattedLore.add("§7" + cleanLine);
                }
            }
        }
        
        return formattedLore;
    }
    
    // ==================== SPECIAL EFFECT FORMATTING ====================
    
    /**
     * Create an animated shimmer effect for T6 items
     * Call this periodically to create the shimmer animation
     */
    public static String formatT6Shimmer(String text, long gameTime) {
        // Create a shimmer effect that cycles every 2 seconds (40 ticks)
        boolean alternatePhase = (gameTime / 20) % 2 == 0;
        return GradientColors.getT6Shimmer(text, alternatePhase);
    }
    
    /**
     * Create an animated shimmer effect for Unique items
     */
    public static String formatUniqueShimmer(String text, long gameTime) {
        // Create a shimmer effect that cycles every 2 seconds (40 ticks)
        boolean alternatePhase = (gameTime / 20) % 2 == 0;
        return GradientColors.getUniqueShimmer(text, alternatePhase);
    }
    
    /**
     * Create a pulsing effect for T6 items
     * Intensity varies with game time for breathing effect
     */
    public static String formatT6Pulse(String text, long gameTime) {
        // Create a pulsing effect with sine wave (period of 3 seconds = 60 ticks)
        double pulsePhase = (gameTime % 60) / 60.0 * 2 * Math.PI;
        double intensity = 0.8 + 0.4 * Math.sin(pulsePhase); // Range: 0.4 to 1.2
        return GradientColors.getT6Pulse(text, intensity);
    }
    
    /**
     * Create a pulsing effect for Unique items
     */
    public static String formatUniquePulse(String text, long gameTime) {
        // Create a pulsing effect with sine wave (period of 3 seconds = 60 ticks)
        double pulsePhase = (gameTime % 60) / 60.0 * 2 * Math.PI;
        double intensity = 0.8 + 0.4 * Math.sin(pulsePhase); // Range: 0.4 to 1.2
        return GradientColors.getUniquePulse(text, intensity);
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Check if an item should use premium formatting
     */
    public static boolean isPremiumItem(int tier, int rarity) {
        return tier == 6 || rarity == 4;
    }
    
    /**
     * Get the appropriate formatting type for an item
     */
    public enum FormattingType {
        STANDARD,    // Regular color codes
        T6_GRADIENT, // T6 gold gradient
        UNIQUE_GRADIENT, // Unique yellow-orange gradient
        ULTRA_PREMIUM // T6 + Unique combination
    }
    
    /**
     * Determine the formatting type for an item
     */
    public static FormattingType getFormattingType(int tier, int rarity) {
        if (tier == 6 && rarity == 4) {
            return FormattingType.ULTRA_PREMIUM;
        } else if (tier == 6) {
            return FormattingType.T6_GRADIENT;
        } else if (rarity == 4) {
            return FormattingType.UNIQUE_GRADIENT;
        } else {
            return FormattingType.STANDARD;
        }
    }
    
    // ==================== PREVIEW AND TESTING ====================
    
    /**
     * Generate preview text for testing formatting
     */
    public static String[] generateFormattingPreview() {
        return new String[] {
            "§e=== Item Display Formatting Preview ===",
            "",
            "§fStandard T1: " + formatTierItemName("Iron Sword", 1),
            "§aStandard T2: " + formatTierItemName("Steel Sword", 2),
            "§bStandard T3: " + formatTierItemName("Mithril Sword", 3),
            "§dStandard T4: " + formatTierItemName("Adamant Sword", 4),
            "§eStandard T5: " + formatTierItemName("Runite Sword", 5),
            "§6T6 Gradient: " + formatTierItemName("Legendary Sword", 6),
            "",
            "§7Common: " + formatRarityItemName("Basic Sword", 1),
            "§aUncommon: " + formatRarityItemName("Enhanced Sword", 2),
            "§bRare: " + formatRarityItemName("Superior Sword", 3),
            "§eUnique Gradient: " + formatRarityItemName("Mythical Sword", 4),
            "",
            "§6§lULTRA PREMIUM T6 Unique: " + formatCompleteItemName("Excalibur", 6, 4)
        };
    }
}
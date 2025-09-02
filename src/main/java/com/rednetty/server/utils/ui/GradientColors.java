package com.rednetty.server.utils.ui;

import net.md_5.bungee.api.ChatColor;
import java.awt.Color;

/**
 * Gradient Color System for Premium Items
 * 
 * Provides beautiful gradient colors for T6 and Unique items to give them
 * that special premium feeling that sets them apart from lower tiers.
 */
public class GradientColors {
    
    // ==================== T6 GRADIENT COLORS ====================
    
    // T6 uses a gold-to-amber gradient for that legendary feel
    private static final Color T6_START = new Color(255, 215, 0);    // Gold
    private static final Color T6_END = new Color(255, 191, 0);      // Amber
    
    // Alternative T6 gradient options
    private static final Color T6_ALT_START = new Color(255, 223, 0);  // Light Gold  
    private static final Color T6_ALT_END = new Color(184, 134, 11);   // Dark Gold
    
    // ==================== UNIQUE GRADIENT COLORS ====================
    
    // Unique uses a yellow-to-orange gradient for that special unique feel
    private static final Color UNIQUE_START = new Color(255, 255, 0);   // Bright Yellow
    private static final Color UNIQUE_END = new Color(255, 165, 0);     // Orange
    
    // Alternative Unique gradient options
    private static final Color UNIQUE_ALT_START = new Color(255, 223, 0); // Gold-ish Yellow
    private static final Color UNIQUE_ALT_END = new Color(255, 140, 0);   // Dark Orange
    
    /**
     * Generate a gradient string for T6 items (Legendary tier)
     * Uses gold-to-amber gradient for that premium legendary feel
     */
    public static String getT6Gradient(String text) {
        return generateGradient(text, T6_START, T6_END);
    }
    
    /**
     * Generate an alternative T6 gradient with more contrast
     */
    public static String getT6GradientAlt(String text) {
        return generateGradient(text, T6_ALT_START, T6_ALT_END);
    }
    
    /**
     * Generate a gradient string for Unique items
     * Uses yellow-to-orange gradient for that special unique feel
     */
    public static String getUniqueGradient(String text) {
        return generateGradient(text, UNIQUE_START, UNIQUE_END);
    }
    
    /**
     * Generate an alternative Unique gradient with more warmth
     */
    public static String getUniqueGradientAlt(String text) {
        return generateGradient(text, UNIQUE_ALT_START, UNIQUE_ALT_END);
    }
    
    /**
     * Core gradient generation method
     * Creates smooth color transitions character by character
     */
    private static String generateGradient(String text, Color startColor, Color endColor) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        StringBuilder gradientText = new StringBuilder();
        int length = text.length();
        
        for (int i = 0; i < length; i++) {
            // Calculate interpolation factor (0.0 to 1.0)
            double factor = length == 1 ? 0 : (double) i / (length - 1);
            
            // Interpolate between start and end colors
            int red = (int) (startColor.getRed() + (endColor.getRed() - startColor.getRed()) * factor);
            int green = (int) (startColor.getGreen() + (endColor.getGreen() - startColor.getGreen()) * factor);
            int blue = (int) (startColor.getBlue() + (endColor.getBlue() - startColor.getBlue()) * factor);
            
            // Create ChatColor from RGB
            ChatColor color = ChatColor.of(new Color(red, green, blue));
            
            // Add the colored character
            gradientText.append(color).append(text.charAt(i));
        }
        
        return gradientText.toString();
    }
    
    // ==================== SPECIAL EFFECTS ====================
    
    /**
     * Create a shimmering T6 gradient that alternates between two gradients
     * Call this periodically to create an animated shimmer effect
     */
    public static String getT6Shimmer(String text, boolean alternatePhase) {
        return alternatePhase ? getT6GradientAlt(text) : getT6Gradient(text);
    }
    
    /**
     * Create a shimmering Unique gradient that alternates between two gradients
     */
    public static String getUniqueShimmer(String text, boolean alternatePhase) {
        return alternatePhase ? getUniqueGradientAlt(text) : getUniqueGradient(text);
    }
    
    /**
     * Create a pulsing effect by varying the intensity of the gradient
     */
    public static String getT6Pulse(String text, double intensity) {
        // Adjust intensity (0.5 to 1.5 for subtle pulsing)
        intensity = Math.max(0.3, Math.min(1.7, intensity));
        
        Color adjustedStart = adjustBrightness(T6_START, intensity);
        Color adjustedEnd = adjustBrightness(T6_END, intensity);
        
        return generateGradient(text, adjustedStart, adjustedEnd);
    }
    
    /**
     * Create a pulsing effect for Unique items
     */
    public static String getUniquePulse(String text, double intensity) {
        intensity = Math.max(0.3, Math.min(1.7, intensity));
        
        Color adjustedStart = adjustBrightness(UNIQUE_START, intensity);
        Color adjustedEnd = adjustBrightness(UNIQUE_END, intensity);
        
        return generateGradient(text, adjustedStart, adjustedEnd);
    }
    
    /**
     * Adjust the brightness of a color by a factor
     */
    private static Color adjustBrightness(Color color, double factor) {
        int red = Math.min(255, Math.max(0, (int) (color.getRed() * factor)));
        int green = Math.min(255, Math.max(0, (int) (color.getGreen() * factor)));
        int blue = Math.min(255, Math.max(0, (int) (color.getBlue() * factor)));
        
        return new Color(red, green, blue);
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get the appropriate gradient color for a tier
     */
    public static String getTierGradient(int tier, String text) {
        switch (tier) {
            case 6:
                return getT6Gradient(text);
            default:
                // For non-T6 tiers, return with regular color codes
                return getTierColorCode(tier) + text;
        }
    }
    
    /**
     * Get the appropriate gradient color for a rarity
     */
    public static String getRarityGradient(int rarity, String text) {
        switch (rarity) {
            case 4: // Unique
                return getUniqueGradient(text);
            default:
                // For non-Unique rarities, return with regular color codes
                return getRarityColorCode(rarity) + text;
        }
    }
    
    /**
     * Get standard tier color codes (for non-gradient tiers)
     */
    private static String getTierColorCode(int tier) {
        switch (tier) {
            case 1: return "§f"; // White
            case 2: return "§a"; // Green  
            case 3: return "§b"; // Aqua
            case 4: return "§d"; // Light Purple
            case 5: return "§e"; // Yellow
            case 6: return "§6"; // Gold (fallback if gradient fails)
            default: return "§7"; // Gray
        }
    }
    
    /**
     * Get standard rarity color codes (for non-gradient rarities)
     */
    private static String getRarityColorCode(int rarity) {
        switch (rarity) {
            case 1: return "§7"; // Gray - Common
            case 2: return "§a"; // Green - Uncommon
            case 3: return "§b"; // Aqua - Rare  
            case 4: return "§e"; // Yellow (fallback if gradient fails)
            default: return "§7"; // Gray
        }
    }
    
    // ==================== FORMATTING HELPERS ====================
    
    /**
     * Format T6 item name with gradient and special formatting
     */
    public static String formatT6Item(String itemName, String tierName) {
        String gradientName = getT6Gradient(itemName);
        String gradientTier = getT6Gradient("[" + tierName + "]");
        return gradientTier + " " + gradientName;
    }
    
    /**
     * Format Unique item name with gradient and special formatting  
     */
    public static String formatUniqueItem(String itemName, String rarityName) {
        String gradientName = getUniqueGradient(itemName);
        String gradientRarity = getUniqueGradient("[" + rarityName + "]");
        return gradientRarity + " " + gradientName;
    }
    
    /**
     * Format item lore line with appropriate gradient
     */
    public static String formatT6Lore(String loreLine) {
        return getT6Gradient("▪ " + loreLine);
    }
    
    /**
     * Format unique item lore line with appropriate gradient
     */
    public static String formatUniqueLore(String loreLine) {
        return getUniqueGradient("◆ " + loreLine);
    }
    
    // ==================== PREVIEW METHODS (for testing/admin) ====================
    
    /**
     * Generate a preview of all gradient styles for testing
     */
    public static String[] getGradientPreview() {
        String testText = "LEGENDARY ITEM";
        String testText2 = "UNIQUE ITEM";
        
        return new String[] {
            "§6Standard T6: §6" + testText,
            "§eT6 Gradient: " + getT6Gradient(testText),
            "§eT6 Alt Gradient: " + getT6GradientAlt(testText),
            "§e" + "─".repeat(30),
            "§eStandard Unique: §e" + testText2,
            "§eUnique Gradient: " + getUniqueGradient(testText2),
            "§eUnique Alt Gradient: " + getUniqueGradientAlt(testText2)
        };
    }
    
}
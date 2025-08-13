package com.rednetty.server.mechanics.economy.vendors.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  vendor utilities with proper item cleaning that removes ALL vendor-added content
 * Players receive the exact same items that the original item classes generate
 */
public class VendorUtils {
    //  price patterns to catch various formats
    private static final Pattern PRICE_PATTERN = Pattern.compile("Price:\\s*([\\d,]+)\\s*g?", Pattern.CASE_INSENSITIVE);
    private static final Pattern COST_PATTERN = Pattern.compile("Cost:\\s*([\\d,]+)\\s*g?", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEMS_PATTERN = Pattern.compile("([\\d,]+)\\s*gems?", Pattern.CASE_INSENSITIVE);

    //  number formatting
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");
    private static final DecimalFormat LARGE_NUMBER_FORMAT = new DecimalFormat("#,###.#");

    //  More comprehensive vendor-specific lore patterns to remove
    private static final String[] VENDOR_LORE_PATTERNS = {
            "click to purchase",
            "click to buy",
            "price:",
            "cost:",
            "gems",
            "available for purchase",
            "buy now",
            "purchase this item",
            "vendor item",
            "for sale",
            "description:", // ADDED: Remove vendor-added descriptions
            "click on a", // ADDED: Remove vendor instruction text
            "▶", "▷", "►", // ADDED: Remove vendor arrows/symbols
            "💰", "🛒", "📦", "✨", "🌟", "⚠", "📖", "📚" // ADDED: Remove vendor emojis
    };

    /**
     * Extract price from item lore with multiple pattern support
     */
    public static int extractPriceFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return -1;
        }

        List<String> lore = item.getItemMeta().getLore();

        // Try multiple patterns in order of preference
        for (String line : lore) {
            String cleaned = ChatColor.stripColor(line).trim();

            // Try primary price pattern
            Matcher priceMatcher = PRICE_PATTERN.matcher(cleaned);
            if (priceMatcher.find()) {
                try {
                    return Integer.parseInt(priceMatcher.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    // Continue to next pattern
                }
            }

            // Try cost pattern
            Matcher costMatcher = COST_PATTERN.matcher(cleaned);
            if (costMatcher.find()) {
                try {
                    return Integer.parseInt(costMatcher.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    // Continue to next pattern
                }
            }

            // Try gems pattern (for mount vendor, etc.)
            Matcher gemsMatcher = GEMS_PATTERN.matcher(cleaned);
            if (gemsMatcher.find()) {
                try {
                    return Integer.parseInt(gemsMatcher.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    // Continue searching
                }
            }
        }

        return -1;
    }

    /**
     * Add price to item with consistent, beautiful formatting
     */
    public static ItemStack addPriceToItem(ItemStack item, int price) {
        if (item == null) return null;

        ItemStack priced = item.clone();
        ItemMeta meta = priced.getItemMeta();
        if (meta == null) return priced;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Remove any existing price lore first (in case of re-pricing)
        lore = cleanVendorLoreFromList(lore);

        // Add beautiful price section with consistent formatting
        lore.add("");
        lore.add(ChatColor.GOLD + "▶ " + ChatColor.GREEN + "Price: " + ChatColor.WHITE + formatCurrency(price));
        lore.add(ChatColor.YELLOW + "▶ " + ChatColor.GRAY + "Click to purchase!");

        meta.setLore(lore);
        priced.setItemMeta(meta);
        return priced;
    }

    /**
     *  Create vendor display item with description (for display only)
     * This should ONLY be used for vendor menu display, never for the actual item given to players
     */
    public static ItemStack createVendorDisplayItem(ItemStack originalItem, int price, String description) {
        if (originalItem == null) return null;

        ItemStack displayItem = originalItem.clone();
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null && description != null && !description.trim().isEmpty()) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            // Add description section for display
            lore.add("");
            lore.add(ChatColor.YELLOW + "Description:");

            // Handle multi-line descriptions
            String[] descLines = description.split("\n");
            for (String line : descLines) {
                lore.add(ChatColor.GRAY + line.trim());
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        // Add price using the  method
        return addPriceToItem(displayItem, price);
    }

    /**
     * Get the original clean item - this should return the exact item from the original generators
     * This method should be used to get the item that players actually receive
     */
    public static ItemStack getOriginalCleanItem(ItemStack vendorDisplayItem) {
        // For vendor items, we should not try to "clean" them, but rather return the original
        // The vendor menus should store references to the original clean items
        // This method is a fallback that tries to clean as much as possible
        return removeAllVendorContent(vendorDisplayItem);
    }

    /**
     * Completely remove ALL vendor-added content
     */
    private static ItemStack removeAllVendorContent(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return item;
        }

        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();

        if (!meta.hasLore()) {
            return clean;
        }

        List<String> originalLore = meta.getLore();
        List<String> cleanedLore = completelyCleanVendorLore(originalLore);

        // Set cleaned lore (null if empty to prevent empty lore sections)
        meta.setLore(cleanedLore.isEmpty() ? null : cleanedLore);
        clean.setItemMeta(meta);
        return clean;
    }

    /**
     *  Completely clean vendor-specific lore from a lore list
     * This removes ALL vendor-added content including descriptions, prices, instructions, etc.
     */
    private static List<String> completelyCleanVendorLore(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> cleanedLore = new ArrayList<>();
        boolean inVendorSection = false;
        boolean skipNextEmpty = false;

        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            String stripped = ChatColor.stripColor(line).toLowerCase().trim();

            // Check if this line is vendor-related
            boolean isVendorLine = false;
            for (String pattern : VENDOR_LORE_PATTERNS) {
                if (stripped.contains(pattern)) {
                    isVendorLine = true;
                    inVendorSection = true;
                    skipNextEmpty = true;
                    break;
                }
            }

            // Check for vendor-specific formatting patterns
            if (!isVendorLine) {
                // Check for lines that start with vendor symbols
                if (stripped.startsWith("▶") || stripped.startsWith("▷") || stripped.startsWith("►")) {
                    isVendorLine = true;
                    inVendorSection = true;
                    skipNextEmpty = true;
                }

                // Check for lines with vendor emojis
                if (line.contains("💰") || line.contains("🛒") || line.contains("📦") ||
                        line.contains("✨") || line.contains("🌟") || line.contains("⚠") ||
                        line.contains("📖") || line.contains("📚")) {
                    isVendorLine = true;
                    inVendorSection = true;
                    skipNextEmpty = true;
                }
            }

            // Skip vendor lines
            if (isVendorLine) {
                continue;
            }

            // Handle empty lines
            if (stripped.isEmpty() || line.trim().isEmpty()) {
                if (skipNextEmpty || inVendorSection) {
                    skipNextEmpty = false;
                    inVendorSection = false;
                    continue;
                } else {
                    // Keep empty lines that are part of original item lore
                    cleanedLore.add(line);
                }
                continue;
            }

            // If we hit a non-vendor, non-empty line, we're out of vendor section
            if (inVendorSection) {
                inVendorSection = false;
                skipNextEmpty = false;
            }

            // Keep non-vendor lines
            cleanedLore.add(line);
        }

        // Remove trailing empty lines
        while (!cleanedLore.isEmpty() &&
                (cleanedLore.get(cleanedLore.size() - 1).trim().isEmpty() ||
                        ChatColor.stripColor(cleanedLore.get(cleanedLore.size() - 1)).trim().isEmpty())) {
            cleanedLore.remove(cleanedLore.size() - 1);
        }

        return cleanedLore;
    }

    /**
     * LEGACY: Clean vendor lore (kept for backward compatibility)
     */
    private static List<String> cleanVendorLoreFromList(List<String> lore) {
        return completelyCleanVendorLore(lore);
    }

    /**
     *  Create completely clean item copy for giving to players
     * This should ideally be replaced with direct references to original items
     */
    public static ItemStack createCleanItemCopy(ItemStack vendorItem) {
        if (vendorItem == null) return null;
        return getOriginalCleanItem(vendorItem);
    }

    /**
     * Format numbers with proper comma separation and size handling
     */
    public static String formatNumber(long number) {
        if (number >= 1000000) {
            return LARGE_NUMBER_FORMAT.format(number / 1000000.0) + "M";
        } else if (number >= 1000) {
            return NUMBER_FORMAT.format(number);
        } else {
            return String.valueOf(number);
        }
    }

    /**
     * Format currency with proper styling
     */
    public static String formatCurrency(long amount) {
        return formatNumber(amount) + "g";
    }

    /**
     * Format currency with color coding based on amount
     */
    public static String formatColoredCurrency(long amount) {
        String formatted = formatCurrency(amount);

        if (amount >= 1000000) {
            return ChatColor.GOLD + formatted;
        } else if (amount >= 100000) {
            return ChatColor.YELLOW + formatted;
        } else if (amount >= 10000) {
            return ChatColor.GREEN + formatted;
        } else if (amount >= 1000) {
            return ChatColor.AQUA + formatted;
        } else {
            return ChatColor.WHITE + formatted;
        }
    }

    /**
     * Create a separator item for menus
     */
    public static ItemStack createSeparator(Material material, String name) {
        ItemStack separator = new ItemStack(material);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            separator.setItemMeta(meta);
        }
        return separator;
    }

    /**
     * Capitalize first letter of each word
     */
    public static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;

        String[] words = str.toLowerCase().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (!words[i].isEmpty()) {
                result.append(words[i].substring(0, 1).toUpperCase())
                        .append(words[i].substring(1));
            }
        }

        return result.toString();
    }

    /**
     * Format material name for display with better handling
     */
    public static String formatVendorTypeName(String type) {
        if (type == null || type.isEmpty()) return "Unknown Item";

        // Handle special cases
        String processed = type.toLowerCase()
                .replace('_', ' ')
                .replace("magma cream", "Orb")
                .replace("cod", "Speedfish")
                .replace("book", "Teleport Book")
                .replace("saddle", "Mount Access");

        return capitalizeFirst(processed);
    }

    /**
     * Validate that an item has a valid price
     */
    public static boolean hasValidPrice(ItemStack item) {
        return extractPriceFromLore(item) > 0;
    }

    /**
     * Get a display-friendly price string
     */
    public static String getPriceDisplay(ItemStack item) {
        int price = extractPriceFromLore(item);
        return price > 0 ? formatColoredCurrency(price) : ChatColor.RED + "Not for sale";
    }

    /**
     * DEPRECATED: Use createVendorDisplayItem instead
     * This method is kept for backward compatibility but should not be used for new code
     */
    @Deprecated
    public static ItemStack createVendorItem(ItemStack baseItem, int price, String description) {
        return createVendorDisplayItem(baseItem, price, description);
    }

    /**
     * DEPRECATED: Use getOriginalCleanItem instead
     */
    @Deprecated
    public static ItemStack removePriceFromItem(ItemStack item) {
        return getOriginalCleanItem(item);
    }
}
package com.rednetty.server.mechanics.economy.vendors.utils;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized utility class for vendor system operations.
 * Reduces code duplication and provides common functionality across all vendor components.
 */
public class VendorUtils {

    // Constants for price handling
    public static final String PRICE_PREFIX = "Price: ";
    public static final String PRICE_SUFFIX = "g";
    // Enhanced regex patterns for comprehensive lore cleaning
    public static final Pattern PRICE_PATTERN = Pattern.compile("Price:\\s*([\\d,]+)g?", Pattern.CASE_INSENSITIVE);
    public static final Pattern COST_PATTERN = Pattern.compile("Cost:\\s*([\\d,]+)g?", Pattern.CASE_INSENSITIVE);

    // Vendor lore markers and separators
    private static final String VENDOR_SEPARATOR = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    private static final List<String> VENDOR_KEYWORDS = Arrays.asList(
            "click to purchase", "left-click", "right-click", "shift-click",
            "quick buy", "shop info", "description:", "price:", "cost:",
            "gems", "currency", "purchase", "buy", "vendor",
            "click to purchase!", "shift-click for quick buy"
    );

    // Formatters (thread-safe)
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    // Color schemes for different vendor types
    private static final Map<String, ChatColor> VENDOR_TYPE_COLORS = new HashMap<>();

    static {
        VENDOR_TYPE_COLORS.put("item", ChatColor.YELLOW);
        VENDOR_TYPE_COLORS.put("fisherman", ChatColor.AQUA);
        VENDOR_TYPE_COLORS.put("book", ChatColor.LIGHT_PURPLE);
        VENDOR_TYPE_COLORS.put("upgrade", ChatColor.GREEN);
        VENDOR_TYPE_COLORS.put("banker", ChatColor.GOLD);
        VENDOR_TYPE_COLORS.put("medic", ChatColor.RED);
        VENDOR_TYPE_COLORS.put("gambler", ChatColor.DARK_PURPLE);
        VENDOR_TYPE_COLORS.put("default", ChatColor.GRAY);
    }

    /**
     * Extract price from an item's lore with enhanced parsing that handles comma-formatted numbers
     */
    public static int extractPriceFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return -1;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line).trim();

            // Check both price and cost patterns
            Matcher priceMatcher = PRICE_PATTERN.matcher(cleanLine);
            Matcher costMatcher = COST_PATTERN.matcher(cleanLine);

            if (priceMatcher.find()) {
                try {
                    String numberStr = priceMatcher.group(1).replace(",", "");
                    return Integer.parseInt(numberStr);
                } catch (NumberFormatException e) {
                    // Continue searching other lines
                }
            } else if (costMatcher.find()) {
                try {
                    String numberStr = costMatcher.group(1).replace(",", "");
                    return Integer.parseInt(numberStr);
                } catch (NumberFormatException e) {
                    // Continue searching other lines
                }
            }
        }

        return -1;
    }

    /**
     * Add or update price in item lore with consistent formatting
     */
    public static ItemStack addPriceToItem(ItemStack item, int price) {
        if (item == null) return null;

        ItemStack newItem = item.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return newItem;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Remove any existing vendor lore first to prevent duplicates
        lore = cleanVendorLoreFromList(lore);

        // Add vendor section with proper formatting
        lore.add("");
        lore.add(ChatColor.GRAY + VENDOR_SEPARATOR);
        lore.add(ChatColor.GREEN + PRICE_PREFIX + ChatColor.WHITE + formatNumber(price) + PRICE_SUFFIX);
        lore.add(ChatColor.YELLOW + "⚡ Left-click to purchase");
        lore.add(ChatColor.GRAY + "⚡ Shift-click for quick buy");
        lore.add(ChatColor.GRAY + VENDOR_SEPARATOR);

        meta.setLore(lore);
        newItem.setItemMeta(meta);
        return newItem;
    }

    /**
     * Comprehensively remove all vendor-added lore from an item
     * This is the main method that should be used when purchasing items
     */
    public static ItemStack removePriceFromItem(ItemStack item) {
        if (item == null) return null;

        ItemStack cleanItem = item.clone();
        ItemMeta meta = cleanItem.getItemMeta();

        if (meta == null || !meta.hasLore()) {
            return cleanItem;
        }

        List<String> originalLore = meta.getLore();
        List<String> cleanedLore = cleanVendorLoreFromList(originalLore);

        // Update item meta with cleaned lore
        if (cleanedLore.isEmpty()) {
            meta.setLore(null);
        } else {
            meta.setLore(cleanedLore);
        }

        cleanItem.setItemMeta(meta);
        return cleanItem;
    }

    /**
     * Clean vendor lore from a lore list with comprehensive vendor content detection
     */
    private static List<String> cleanVendorLoreFromList(List<String> originalLore) {
        if (originalLore == null || originalLore.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> cleanedLore = new ArrayList<>();
        boolean inVendorSection = false;
        boolean inDescriptionSection = false;
        int vendorSectionStart = -1;

        for (int i = 0; i < originalLore.size(); i++) {
            String line = originalLore.get(i);
            String strippedLine = ChatColor.stripColor(line).trim().toLowerCase();

            // Check for vendor section markers (separators)
            if (line.contains(VENDOR_SEPARATOR)) {
                if (!inVendorSection) {
                    // Start of vendor section found
                    inVendorSection = true;
                    vendorSectionStart = i;

                    // Check if there's an empty line immediately before this separator
                    // that was added by the vendor system
                    if (vendorSectionStart > 0) {
                        int previousLineIndex = vendorSectionStart - 1;
                        String previousLine = originalLore.get(previousLineIndex);

                        // If the previous line is empty and we haven't already added it to cleaned lore,
                        // or if it's the last thing we added and it's empty, it's likely vendor-added
                        if (isEmptyLine(previousLine)) {
                            // Remove the empty line if it was the last thing added to cleanedLore
                            if (!cleanedLore.isEmpty() &&
                                    isEmptyLine(cleanedLore.get(cleanedLore.size() - 1))) {
                                cleanedLore.remove(cleanedLore.size() - 1);
                            }
                        }
                    }
                } else {
                    // End of vendor section
                    inVendorSection = false;
                    vendorSectionStart = -1;
                }
                continue; // Skip the separator line itself
            }

            // Check for ItemVendorMenu description section start
            if (strippedLine.equals("description:") &&
                    line.startsWith(ChatColor.YELLOW.toString())) {
                inDescriptionSection = true;

                // Remove empty line before description if it exists
                if (!cleanedLore.isEmpty() && isEmptyLine(cleanedLore.get(cleanedLore.size() - 1))) {
                    cleanedLore.remove(cleanedLore.size() - 1);
                }
                continue; // Skip the "Description:" line
            }

            // Skip content inside vendor separator sections
            if (inVendorSection) {
                continue;
            }

            // Handle description section content
            if (inDescriptionSection) {
                // Check if this line ends the description section
                if (strippedLine.contains("click to purchase") ||
                        strippedLine.contains("shift-click") ||
                        strippedLine.contains("quick buy") ||
                        isEmptyLine(line)) {

                    // If it's an empty line, check what comes next
                    if (isEmptyLine(line)) {
                        // Look ahead to see if vendor content follows
                        if (i + 1 < originalLore.size()) {
                            String nextLine = ChatColor.stripColor(originalLore.get(i + 1)).trim().toLowerCase();
                            if (nextLine.contains("click to purchase") ||
                                    nextLine.contains("shift-click") ||
                                    nextLine.contains("quick buy")) {
                                // This empty line is part of vendor section, skip it
                                continue;
                            }
                        }
                        // If no vendor content follows, this might be original lore
                        inDescriptionSection = false;
                        cleanedLore.add(line);
                        continue;
                    }

                    // This is a vendor action line, skip it but check if description section continues
                    if (strippedLine.contains("click to purchase")) {
                        // Look ahead for more vendor lines
                        continue;
                    } else if (strippedLine.contains("shift-click") || strippedLine.contains("quick buy")) {
                        // End of description section
                        inDescriptionSection = false;
                        continue;
                    }
                } else {
                    // This is the description text itself, skip it
                    continue;
                }
            }

            // Check for standalone vendor-related lines outside of separator sections
            boolean isVendorLine = isVendorRelatedLine(strippedLine, line);

            // If this is a vendor line outside of a separator section,
            // also check if we should remove a preceding empty line
            if (isVendorLine && !cleanedLore.isEmpty()) {
                String lastAddedLine = cleanedLore.get(cleanedLore.size() - 1);
                if (isEmptyLine(lastAddedLine)) {
                    // Check if this empty line is likely vendor-added by looking ahead
                    // If the next few lines are also vendor-related, the empty line was probably vendor-added
                    if (isFollowedByVendorContent(originalLore, i)) {
                        cleanedLore.remove(cleanedLore.size() - 1);
                    }
                }
            }

            // Keep non-vendor lines
            if (!isVendorLine) {
                cleanedLore.add(line);
            }
        }

        // Final cleanup: remove trailing empty lines that might be vendor-added
        // But be conservative - only remove if there are multiple trailing empty lines
        cleanTrailingVendorEmptyLines(cleanedLore);

        return cleanedLore;
    }

    /**
     * Check if a line is empty (null, empty string, or only whitespace)
     */
    private static boolean isEmptyLine(String line) {
        return line == null || line.trim().isEmpty() || line.equals("");
    }

    /**
     * Check if a line is vendor-related (enhanced detection)
     */
    private static boolean isVendorRelatedLine(String strippedLine, String originalLine) {
        // Check against patterns
        if (PRICE_PATTERN.matcher(strippedLine).find() ||
                COST_PATTERN.matcher(strippedLine).find()) {
            return true;
        }

        // Check against vendor keywords
        for (String keyword : VENDOR_KEYWORDS) {
            if (strippedLine.contains(keyword)) {
                return true;
            }
        }

        // Check for common vendor lore patterns
        if (strippedLine.startsWith("⚡") ||
                strippedLine.contains("click") ||
                strippedLine.contains("purchase") ||
                strippedLine.matches(".*\\d+g.*") ||  // Lines containing number + g
                originalLine.startsWith(ChatColor.YELLOW.toString()) && strippedLine.contains("click")) {
            return true;
        }

        // Check for ItemVendorMenu specific patterns
        if (strippedLine.equals("description:") && originalLine.startsWith(ChatColor.YELLOW.toString())) {
            return true;
        }

        // Check for purchase action lines
        if (strippedLine.contains("click to purchase") ||
                strippedLine.contains("left-click to purchase") ||
                strippedLine.contains("shift-click for quick buy") ||
                strippedLine.contains("quick buy")) {
            return true;
        }

        // Check for lines that start with action symbols
        if (originalLine.startsWith(ChatColor.GREEN.toString()) && strippedLine.contains("click") ||
                originalLine.startsWith(ChatColor.GRAY.toString()) && strippedLine.contains("shift-click")) {
            return true;
        }

        return false;
    }

    /**
     * Check if the current position is followed by vendor content (enhanced detection)
     */
    private static boolean isFollowedByVendorContent(List<String> lore, int currentIndex) {
        int vendorLineCount = 0;
        int totalLinesChecked = 0;
        int maxLinesToCheck = Math.min(5, lore.size() - currentIndex);

        for (int i = currentIndex; i < currentIndex + maxLinesToCheck && i < lore.size(); i++) {
            String line = lore.get(i);
            String strippedLine = ChatColor.stripColor(line).trim().toLowerCase();

            if (isVendorRelatedLine(strippedLine, line) ||
                    line.contains(VENDOR_SEPARATOR) ||
                    strippedLine.equals("description:") ||
                    strippedLine.contains("click to purchase") ||
                    strippedLine.contains("shift-click")) {
                vendorLineCount++;
            }
            totalLinesChecked++;
        }

        // If more than half of the following lines are vendor-related,
        // the empty line was probably vendor-added
        return totalLinesChecked > 0 && (vendorLineCount * 2) > totalLinesChecked;
    }

    /**
     * Conservatively clean trailing empty lines that appear to be vendor-added
     */
    private static void cleanTrailingVendorEmptyLines(List<String> lore) {
        if (lore.isEmpty()) return;

        // Only remove trailing empty lines if there are multiple consecutive ones
        // This helps preserve intentional single trailing empty lines
        int trailingEmptyCount = 0;
        int size = lore.size();

        // Count trailing empty lines
        for (int i = size - 1; i >= 0; i--) {
            if (isEmptyLine(lore.get(i))) {
                trailingEmptyCount++;
            } else {
                break;
            }
        }

        // Only remove if there are 2+ trailing empty lines (likely vendor-added)
        if (trailingEmptyCount >= 2) {
            // Remove all but one trailing empty line
            for (int i = 0; i < trailingEmptyCount - 1; i++) {
                if (!lore.isEmpty()) {
                    lore.remove(lore.size() - 1);
                }
            }
        }
    }

    /**
     * Create a standardized separator item for inventories
     */
    public static ItemStack createSeparator(Material material, String name) {
        ItemStack separator = new ItemStack(material);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name != null ? name : " ");
            separator.setItemMeta(meta);
        }
        return separator;
    }

    /**
     * Create colored glass pane separator
     */
    public static ItemStack createColoredSeparator(ChatColor color, String name) {
        Material glassType = getGlassPaneForColor(color);
        ItemStack separator = new ItemStack(glassType);
        ItemMeta meta = separator.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + (name != null ? name : " "));
            separator.setItemMeta(meta);
        }
        return separator;
    }

    /**
     * Get appropriate glass pane material for color
     */
    private static Material getGlassPaneForColor(ChatColor color) {
        switch (color) {
            case RED: return Material.RED_STAINED_GLASS_PANE;
            case BLUE: return Material.BLUE_STAINED_GLASS_PANE;
            case GREEN: return Material.GREEN_STAINED_GLASS_PANE;
            case YELLOW: return Material.YELLOW_STAINED_GLASS_PANE;
            case LIGHT_PURPLE: return Material.MAGENTA_STAINED_GLASS_PANE;
            case AQUA: return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case GOLD: return Material.ORANGE_STAINED_GLASS_PANE;
            case DARK_PURPLE: return Material.PURPLE_STAINED_GLASS_PANE;
            default: return Material.GRAY_STAINED_GLASS_PANE;
        }
    }

    /**
     * Get color for vendor type
     */
    public static ChatColor getVendorTypeColor(String vendorType) {
        return VENDOR_TYPE_COLORS.getOrDefault(vendorType.toLowerCase(), ChatColor.GRAY);
    }

    /**
     * Format numbers with commas for better readability
     */
    public static String formatNumber(long number) {
        return DECIMAL_FORMAT.format(number);
    }

    /**
     * Format currency amounts
     */
    public static String formatCurrency(long amount) {
        return formatNumber(amount) + "g";
    }

    /**
     * Format time duration in a human-readable way
     */
    public static String formatDuration(long milliseconds) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Format timestamp for logging
     */
    public static String formatTimestamp(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    /**
     * Format time for display
     */
    public static String formatTime(long timestamp) {
        return TIME_FORMAT.format(new Date(timestamp));
    }

    /**
     * Validate location for vendor placement
     */
    public static boolean isValidVendorLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // Check for reasonable coordinates
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        // Prevent extreme coordinates
        if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000) {
            return false;
        }

        // Prevent placing vendors too high or too low
        if (y < -100 || y > 1000) {
            return false;
        }

        // Check if world is loaded
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }

        return true;
    }

    /**
     * Calculate distance between two locations safely
     */
    public static double safeDistance(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            return Double.MAX_VALUE;
        }

        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return Double.MAX_VALUE;
        }

        return loc1.distance(loc2);
    }

    /**
     * Calculate squared distance for performance
     */
    public static double safeDistanceSquared(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) {
            return Double.MAX_VALUE;
        }

        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return Double.MAX_VALUE;
        }

        return loc1.distanceSquared(loc2);
    }

    /**
     * Check if player is within range of location
     */
    public static boolean isPlayerInRange(Player player, Location location, double range) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        return safeDistanceSquared(player.getLocation(), location) <= range * range;
    }

    /**
     * Get players within range of a location
     */
    public static List<Player> getPlayersInRange(Location center, double range) {
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }

        List<Player> playersInRange = new ArrayList<>();
        double rangeSquared = range * range;

        for (Player player : center.getWorld().getPlayers()) {
            if (safeDistanceSquared(player.getLocation(), center) <= rangeSquared) {
                playersInRange.add(player);
            }
        }

        return playersInRange;
    }

    /**
     * Sanitize vendor ID to ensure it's safe for file names and keys
     */
    public static String sanitizeVendorId(String vendorId) {
        if (vendorId == null) {
            return "unknown_vendor";
        }

        // Remove or replace problematic characters
        return vendorId.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
    }

    /**
     * Validate vendor ID
     */
    public static boolean isValidVendorId(String vendorId) {
        if (vendorId == null || vendorId.trim().isEmpty()) {
            return false;
        }

        String sanitized = sanitizeVendorId(vendorId);
        return vendorId.equals(sanitized) && vendorId.length() <= 32;
    }

    /**
     * Create a standardized error message with timestamp
     */
    public static String createErrorMessage(String context, String error) {
        return "[" + formatTime(System.currentTimeMillis()) + "] " + context + ": " + error;
    }

    /**
     * Log error with context for vendor system
     */
    public static void logVendorError(JavaPlugin plugin, String vendorId, String operation, Exception e) {
        String message = String.format("Vendor %s failed during %s: %s",
                vendorId != null ? vendorId : "unknown",
                operation != null ? operation : "unknown operation",
                e.getMessage());

        plugin.getLogger().log(Level.WARNING, message, e);
    }

    /**
     * Create standardized hologram lines for vendor types
     */
    public static List<String> createDefaultHologramLines(String vendorType) {
        ChatColor color = getVendorTypeColor(vendorType);
        String displayName = formatVendorTypeName(vendorType);

        return Arrays.asList(
                color + ChatColor.BOLD.toString() + displayName,
                ChatColor.GRAY + "Click to interact"
        );
    }

    /**
     * Format vendor type name for display
     */
    public static String formatVendorTypeName(String vendorType) {
        if (vendorType == null || vendorType.isEmpty()) {
            return "Vendor";
        }

        switch (vendorType.toLowerCase()) {
            case "item": return "Item Vendor";
            case "fisherman": return "Fisherman";
            case "book": return "Book Vendor";
            case "upgrade": return "Upgrade Vendor";
            case "banker": return "Banker";
            case "medic": return "Medic";
            case "gambler": return "Gambler";
            default: return capitalizeFirst(vendorType) + " Vendor";
        }
    }

    /**
     * Capitalize first letter of string
     */
    public static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    /**
     * Create a safe copy of a location
     */
    public static Location safeCopyLocation(Location location) {
        if (location == null) {
            return null;
        }
        return location.clone();
    }

    /**
     * Check if two locations are approximately equal (within small tolerance)
     */
    public static boolean locationsEqual(Location loc1, Location loc2, double tolerance) {
        if (loc1 == null && loc2 == null) {
            return true;
        }
        if (loc1 == null || loc2 == null) {
            return false;
        }
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return false;
        }

        return Math.abs(loc1.getX() - loc2.getX()) <= tolerance &&
                Math.abs(loc1.getY() - loc2.getY()) <= tolerance &&
                Math.abs(loc1.getZ() - loc2.getZ()) <= tolerance;
    }

    /**
     * Get safe chunk coordinates from location
     */
    public static int[] getChunkCoords(Location location) {
        if (location == null) {
            return new int[]{0, 0};
        }
        return new int[]{location.getBlockX() >> 4, location.getBlockZ() >> 4};
    }

    /**
     * Check if chunk is loaded
     */
    public static boolean isChunkLoaded(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        int[] coords = getChunkCoords(location);
        return location.getWorld().isChunkLoaded(coords[0], coords[1]);
    }

    /**
     * Create a map from list for easier parameter passing
     */
    public static <T> Map<String, T> createMap(String key1, T value1) {
        Map<String, T> map = new HashMap<>();
        map.put(key1, value1);
        return map;
    }

    /**
     * Create a map from two key-value pairs
     */
    public static <T> Map<String, T> createMap(String key1, T value1, String key2, T value2) {
        Map<String, T> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    /**
     * Null-safe string comparison
     */
    public static boolean stringsEqual(String str1, String str2) {
        return Objects.equals(str1, str2);
    }

    /**
     * Null-safe string comparison (case insensitive)
     */
    public static boolean stringsEqualIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) {
            return true;
        }
        if (str1 == null || str2 == null) {
            return false;
        }
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * Convert stack trace to string for logging
     */
    public static String stackTraceToString(Exception e) {
        if (e == null) {
            return "No exception";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");

        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Clamp value between min and max
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamp double value between min and max
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Check if string is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Get non-null string with default fallback
     */
    public static String getOrDefault(String value, String defaultValue) {
        return isNullOrEmpty(value) ? defaultValue : value;
    }

    /**
     * Check if an item currently has vendor lore attached
     */
    public static boolean hasVendorLore(ItemStack item) {
        return extractPriceFromLore(item) > 0;
    }

    /**
     * Create a display copy of an item for vendor menus (with price lore)
     */
    public static ItemStack createVendorDisplayItem(ItemStack originalItem, int price) {
        if (originalItem == null) {
            return null;
        }

        // First ensure the item is clean of any existing vendor lore
        ItemStack cleanItem = removePriceFromItem(originalItem);

        // Then add the price lore for display
        return addPriceToItem(cleanItem, price);
    }

    /**
     * Get a clean copy of an item suitable for giving to players
     */
    public static ItemStack createCleanItemCopy(ItemStack vendorItem) {
        if (vendorItem == null) {
            return null;
        }

        return removePriceFromItem(vendorItem);
    }
}
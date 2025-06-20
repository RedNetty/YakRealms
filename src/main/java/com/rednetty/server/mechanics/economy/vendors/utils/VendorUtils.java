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

    // Constants
    public static final String PRICE_PREFIX = "Price: ";
    public static final String PRICE_SUFFIX = "g";
    public static final Pattern PRICE_PATTERN = Pattern.compile("Price:\\s*(\\d+)g?", Pattern.CASE_INSENSITIVE);

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
     * Extract price from an item's lore with enhanced parsing
     */
    public static int extractPriceFromLore(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return -1;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line).trim();

            // Use regex for more flexible parsing
            Matcher matcher = PRICE_PATTERN.matcher(cleanLine);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Continue searching other lines
                }
            }
        }

        return -1;
    }

    /**
     * Add or update price in item lore
     */
    public static ItemStack addPriceToItem(ItemStack item, int price) {
        if (item == null) return null;

        ItemStack newItem = item.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null) return newItem;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Remove existing price lines
        lore.removeIf(line -> {
            String cleanLine = ChatColor.stripColor(line).trim();
            return PRICE_PATTERN.matcher(cleanLine).find();
        });

        // Add new price line
        lore.add(ChatColor.GREEN + PRICE_PREFIX + ChatColor.WHITE + formatNumber(price) + PRICE_SUFFIX);

        meta.setLore(lore);
        newItem.setItemMeta(meta);
        return newItem;
    }

    /**
     * Remove price from item lore
     */
    public static ItemStack removePriceFromItem(ItemStack item) {
        if (item == null) return null;

        ItemStack newItem = item.clone();
        ItemMeta meta = newItem.getItemMeta();
        if (meta == null || !meta.hasLore()) return newItem;

        List<String> lore = new ArrayList<>(meta.getLore());
        lore.removeIf(line -> {
            String cleanLine = ChatColor.stripColor(line).trim();
            return PRICE_PATTERN.matcher(cleanLine).find();
        });

        meta.setLore(lore);
        newItem.setItemMeta(meta);
        return newItem;
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
}
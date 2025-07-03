package com.rednetty.server.mechanics.market;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Market categories for organizing items
 */
public enum MarketCategory {
    WEAPONS("Weapons", Material.DIAMOND_SWORD, ChatColor.RED,
            "sword", "bow", "crossbow", "trident", "axe", "axe", "shovel", "hoe"),

    ARMOR("Armor", Material.DIAMOND_CHESTPLATE, ChatColor.BLUE,
            "helmet", "chestplate", "leggings", "boots", "shield"),

    TOOLS("Tools", Material.DIAMOND_PICKAXE, ChatColor.YELLOW,
            "pickaxe", "shears", "flint_and_steel"),

    FOOD("Food", Material.BREAD, ChatColor.GOLD,
            "bread", "meat", "fish", "fruit", "vegetable", "cookie", "cake"),

    POTIONS("Potions", Material.POTION, ChatColor.LIGHT_PURPLE,
            "potion", "splash", "lingering", "bottle"),

    ENCHANTED("Enchanted Items", Material.ENCHANTED_BOOK, ChatColor.AQUA,
            "enchanted", "book"),

    MISCELLANEOUS("Miscellaneous", Material.STICK, ChatColor.GRAY,
            "stick", "paper", "book", "misc");

    private final String displayName;
    private final Material icon;
    private final ChatColor color;
    private final String[] keywords;

    MarketCategory(String displayName, Material icon, ChatColor color, String... keywords) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.keywords = keywords;
    }

    /**
     * Get the display name with color
     */
    public String getColoredDisplayName() {
        return color + displayName;
    }

    /**
     * Determine category from material
     */
    public static MarketCategory fromMaterial(Material material) {
        String materialName = material.name().toLowerCase();

        for (MarketCategory category : values()) {
            for (String keyword : category.keywords) {
                if (materialName.contains(keyword)) {
                    return category;
                }
            }
        }

        return MISCELLANEOUS;
    }

    /**
     * Get category by name (case insensitive)
     */
    public static MarketCategory fromString(String name) {
        if (name == null) return MISCELLANEOUS;

        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Try by display name
            for (MarketCategory category : values()) {
                if (category.displayName.equalsIgnoreCase(name)) {
                    return category;
                }
            }
            return MISCELLANEOUS;
        }
    }

    // Getters
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public ChatColor getColor() { return color; }
    public String[] getKeywords() { return keywords.clone(); }
}
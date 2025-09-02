package com.rednetty.server.core.mechanics.economy.merchant;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Logger;

/**
 * Utility class for item validation and processing in the merchant system
 */
public class MerchantItemUtil {

    private static final Logger logger = Logger.getLogger(MerchantItemUtil.class.getName());

    // Sets for efficient lookups
    private static final Set<Material> TRADEABLE_ORES = new HashSet<>();
    private static final Set<Material> TRADEABLE_WEAPONS = new HashSet<>();
    private static final Set<Material> TRADEABLE_ARMOR = new HashSet<>();
    private static final Set<String> BLACKLISTED_ITEMS = new HashSet<>();
    private static final Set<String> SOULBOUND_KEYWORDS = new HashSet<>();

    static {
        initializeTradeableItems();
        initializeBlacklists();
    }

    /**
     * Initialize tradeable item sets
     */
    private static void initializeTradeableItems() {
        // Ores
        TRADEABLE_ORES.addAll(Arrays.asList(
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                Material.ANCIENT_DEBRIS,
                Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE
        ));

        // Weapons
        TRADEABLE_WEAPONS.addAll(Arrays.asList(
                // Swords
                Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                // Axes
                Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
                Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
                Material.GOLDEN_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE,
                Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
                Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
                // Ranged weapons
                Material.BOW, Material.CROSSBOW, Material.TRIDENT
        ));

        // Armor
        TRADEABLE_ARMOR.addAll(Arrays.asList(
                // Helmets
                Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
                Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
                Material.TURTLE_HELMET,
                // Chestplates
                Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
                Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
                Material.ELYTRA,
                // Leggings
                Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
                Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
                // Boots
                Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
                Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
        ));
    }

    /**
     * Initialize blacklisted items and keywords
     */
    private static void initializeBlacklists() {
        // Soulbound/untradeable keywords
        SOULBOUND_KEYWORDS.addAll(Arrays.asList(
                "soulbound", "untradeable", "bound", "cannot be traded",
                "personal", "account bound", "character bound", "soul bound",
                "not tradeable", "no trade", "untrade"
        ));

        // Specific item names that cannot be traded
        BLACKLISTED_ITEMS.addAll(Arrays.asList(
                "admin", "moderator", "owner", "console",
                "creative", "debug", "test", "gm", "gamemaster",
                "dev", "developer", "op", "operator"
        ));
    }

    /**
     * Check if an item is tradeable with the merchant
     *
     * @param item The item to check
     * @return true if the item can be traded
     */
    public static boolean isTradeableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Check if item is explicitly blacklisted
        if (isBlacklistedItem(item)) {
            return false;
        }

        // Check for soulbound/untradeable lore
        if (hasSoulboundLore(item)) {
            return false;
        }

        // Check if the item type is enabled in configuration
        MerchantConfig config = MerchantConfig.getInstance();
        if (isOre(item) && !config.isOresAllowed()) {
            return false;
        }
        if (isWeapon(item) && !config.isWeaponsAllowed()) {
            return false;
        }
        if (isArmor(item) && !config.isArmorAllowed()) {
            return false;
        }
        if (isOrbOfAlteration(item) && !config.isOrbsAllowed()) {
            return false;
        }

        // Check material type
        return isTradeableMaterial(item.getType());
    }

    /**
     * Check if the item material is tradeable
     *
     * @param material The material to check
     * @return true if tradeable
     */
    public static boolean isTradeableMaterial(Material material) {
        return TRADEABLE_ORES.contains(material) ||
                TRADEABLE_WEAPONS.contains(material) ||
                TRADEABLE_ARMOR.contains(material) ||
                material == Material.MAGMA_CREAM; // Orb of Alteration
    }

    /**
     * Check if an item has soulbound or untradeable lore
     *
     * @param item The item to check
     * @return true if the item has soulbound lore
     */
    public static boolean hasSoulboundLore(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line).toLowerCase().trim();
            for (String keyword : SOULBOUND_KEYWORDS) {
                if (cleanLine.contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if an item is explicitly blacklisted
     *
     * @param item The item to check
     * @return true if blacklisted
     */
    public static boolean isBlacklistedItem(ItemStack item) {
        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        // Check display name
        if (meta.hasDisplayName()) {
            String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
            for (String blacklisted : BLACKLISTED_ITEMS) {
                if (displayName.contains(blacklisted)) {
                    return true;
                }
            }
        }

        // Check lore for blacklisted terms
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                String cleanLine = ChatColor.stripColor(line).toLowerCase();
                for (String blacklisted : BLACKLISTED_ITEMS) {
                    if (cleanLine.contains(blacklisted)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Get the rarity of an item based on its lore
     *
     * @param item The item to check
     * @return ItemRarity enum value
     */
    public static ItemRarity getItemRarity(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return ItemRarity.COMMON;
        }

        List<String> lore = item.getItemMeta().getLore();
        if (lore.isEmpty()) {
            return ItemRarity.COMMON;
        }

        // Check the last line of lore for rarity
        String lastLine = ChatColor.stripColor(lore.get(lore.size() - 1)).toLowerCase().trim();

        for (ItemRarity rarity : ItemRarity.values()) {
            if (lastLine.contains(rarity.getDisplayName().toLowerCase())) {
                return rarity;
            }
        }

        return ItemRarity.COMMON;
    }

    /**
     * Get the tier of an item based on its display name color or material
     *
     * @param item The item to check
     * @return The tier level (higher = better)
     */
    public static int getItemTier(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();

            // Tier based on color codes
            if (displayName.contains(ChatColor.GOLD.toString())) return 1500;
            if (displayName.contains(ChatColor.YELLOW.toString())) return 1000;
            if (displayName.contains(ChatColor.LIGHT_PURPLE.toString())) return 750;
            if (displayName.contains(ChatColor.BLUE.toString())) return 150;
            if (displayName.contains(ChatColor.GREEN.toString())) return 80;
            if (displayName.contains(ChatColor.WHITE.toString())) return 20;
        }

        // Fallback to material-based tier
        return getMaterialTier(item.getType());
    }

    /**
     * Get the base tier of a material
     *
     * @param material The material
     * @return The tier level
     */
    public static int getMaterialTier(Material material) {
        String name = material.name();

        if (name.contains("NETHERITE")) return 1500;
        if (name.contains("DIAMOND")) return 750;
        if (name.contains("IRON")) return 155;
        if (name.contains("GOLD")) return 1000;
        if (name.contains("STONE") || name.contains("CHAINMAIL")) return 85;
        if (name.contains("WOOD") || name.contains("LEATHER")) return 20;

        // Special cases for ores
        if (material == Material.ANCIENT_DEBRIS) return 60;
        if (name.contains("EMERALD_ORE")) return 40;
        if (name.contains("DIAMOND_ORE")) return 35;
        if (name.contains("GOLD_ORE")) return 20;
        if (name.contains("IRON_ORE")) return 15;
        if (name.contains("COAL_ORE")) return 5;

        return 10; // Default tier
    }

    /**
     * Get the ore tier for ore materials
     *
     * @param material The ore material
     * @return The ore tier (0 if not an ore)
     */
    public static int getOreTier(Material material) {
        return MerchantConfig.getInstance().getOreTier(material.name());
    }

    /**
     * Check if an item is a weapon
     *
     * @param item The item to check
     * @return true if the item is a weapon
     */
    public static boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        return TRADEABLE_WEAPONS.contains(item.getType());
    }

    /**
     * Check if an item is armor
     *
     * @param item The item to check
     * @return true if the item is armor
     */
    public static boolean isArmor(ItemStack item) {
        if (item == null) return false;
        return TRADEABLE_ARMOR.contains(item.getType());
    }

    /**
     * Check if an item is an ore
     *
     * @param item The item to check
     * @return true if the item is an ore
     */
    public static boolean isOre(ItemStack item) {
        if (item == null) return false;
        return TRADEABLE_ORES.contains(item.getType());
    }

    /**
     * Check if an item is an Orb of Alteration
     *
     * @param item The item to check
     * @return true if the item is an Orb of Alteration
     */
    public static boolean isOrbOfAlteration(ItemStack item) {
        if (item == null || item.getType() != Material.MAGMA_CREAM) {
            return false;
        }

        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return displayName.equalsIgnoreCase("Orb of Alteration");
    }

    /**
     * Get a formatted description of why an item cannot be traded
     *
     * @param item The item to check
     * @return A user-friendly reason, or null if the item is tradeable
     */
    public static String getUntradeableReason(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "No item to trade";
        }

        if (hasSoulboundLore(item)) {
            return "This item is soulbound and cannot be traded";
        }

        if (isBlacklistedItem(item)) {
            return "This item is not allowed to be traded";
        }

        MerchantConfig config = MerchantConfig.getInstance();

        if (isOre(item) && !config.isOresAllowed()) {
            return "Ore trading is currently disabled";
        }

        if (isWeapon(item) && !config.isWeaponsAllowed()) {
            return "Weapon trading is currently disabled";
        }

        if (isArmor(item) && !config.isArmorAllowed()) {
            return "Armor trading is currently disabled";
        }

        if (isOrbOfAlteration(item) && !config.isOrbsAllowed()) {
            return "Orb trading is currently disabled";
        }

        if (!isTradeableMaterial(item.getType())) {
            return "This type of item cannot be traded with the merchant";
        }

        return null; // Item is tradeable
    }

    /**
     * Get a list of all tradeable material types
     *
     * @return Set of all tradeable materials
     */
    public static Set<Material> getAllTradeableMaterials() {
        Set<Material> allTradeable = new HashSet<>();
        allTradeable.addAll(TRADEABLE_ORES);
        allTradeable.addAll(TRADEABLE_WEAPONS);
        allTradeable.addAll(TRADEABLE_ARMOR);
        allTradeable.add(Material.MAGMA_CREAM); // Orb of Alteration
        return Collections.unmodifiableSet(allTradeable);
    }

    /**
     * Generate a deterministic hash for an item to ensure consistent variation
     * This ensures that the same item always has the same "random" variation
     *
     * @param item The item to hash
     * @return A deterministic hash value
     */
    public static int generateItemHash(ItemStack item) {
        if (item == null) return 0;

        int hash = item.getType().ordinal();

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                hash = hash * 31 + meta.getDisplayName().hashCode();
            }
            if (meta.hasLore()) {
                hash = hash * 31 + meta.getLore().hashCode();
            }
            if (meta.hasEnchants()) {
                hash = hash * 31 + meta.getEnchants().hashCode();
            }
        }

        // Make sure hash is positive
        return Math.abs(hash);
    }

    /**
     * Enum for item rarity levels
     */
    public enum ItemRarity {
        COMMON("Common", 1.0),
        UNCOMMON("Uncommon", 1.5),
        RARE("Rare", 2),
        UNIQUE("Unique", 2.75);
        private final String displayName;
        private final double valueMultiplier;

        ItemRarity(String displayName, double valueMultiplier) {
            this.displayName = displayName;
            this.valueMultiplier = valueMultiplier;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getValueMultiplier() {
            return valueMultiplier;
        }

        /**
         * Get the color associated with this rarity
         *
         * @return ChatColor for this rarity
         */
        public ChatColor getColor() {
            switch (this) {
                case COMMON: return ChatColor.WHITE;
                case UNCOMMON: return ChatColor.GREEN;
                case RARE:
                    return ChatColor.AQUA;
                case UNIQUE:
                    return ChatColor.YELLOW;
                default: return ChatColor.WHITE;
            }
        }

        /**
         * Get the formatted display name with color
         *
         * @return Colored display name
         */
        public String getColoredDisplayName() {
            return getColor() + displayName;
        }
    }

    /**
     * Private constructor to prevent instantiation
     */
    private MerchantItemUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
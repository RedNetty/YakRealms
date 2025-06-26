package com.rednetty.server.mechanics.crates.factory;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Factory class for creating crate items and keys with enhanced visual effects
 */
public class CrateFactory {
    private final YakRealms plugin;
    private final Logger logger;

    // NBT keys for identification
    private static final String NBT_CRATE_TYPE = "crateType";
    private static final String NBT_CRATE_TIER = "crateTier";
    private static final String NBT_IS_HALLOWEEN = "isHalloween";
    private static final String NBT_CREATION_TIME = "creationTime";
    private static final String NBT_CRATE_KEY = "crateKey";

    /**
     * Constructor
     */
    public CrateFactory() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Creates a crate item of the specified type
     *
     * @param crateType   The type of crate to create
     * @param isHalloween Whether this is a Halloween variant
     * @return The created crate ItemStack
     */
    public ItemStack createCrate(CrateType crateType, boolean isHalloween) {
        if (crateType == null) {
            logger.warning("Attempted to create crate with null type");
            return null;
        }

        try {
            // Choose base material based on type and season
            Material material = determineCrateMaterial(crateType, isHalloween);
            ItemStack crate = new ItemStack(material);
            ItemMeta meta = crate.getItemMeta();

            if (meta == null) {
                logger.warning("Failed to get ItemMeta for crate");
                return crate;
            }

            // Set display name with enhanced formatting
            String displayName = createCrateDisplayName(crateType, isHalloween);
            meta.setDisplayName(displayName);

            // Create enhanced lore
            List<String> lore = createCrateLore(crateType, isHalloween);
            meta.setLore(lore);

            // Add visual enhancements
            enhanceCrateVisuals(meta, crateType, isHalloween);

            // Store NBT data for identification
            storeCrateNBTData(meta, crateType, isHalloween);

            crate.setItemMeta(meta);

            logger.fine("Created " + (isHalloween ? "Halloween " : "") + crateType + " crate");
            return crate;

        } catch (Exception e) {
            logger.severe("Error creating crate: " + e.getMessage());
            e.printStackTrace();
            return createFallbackCrate();
        }
    }

    /**
     * Creates a crate key item
     *
     * @return The created crate key ItemStack
     */
    public ItemStack createCrateKey() {
        try {
            ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
            ItemMeta meta = key.getItemMeta();

            if (meta == null) {
                return key;
            }

            // Enhanced key design
            meta.setDisplayName(ChatColor.AQUA + "✦ " + ChatColor.BOLD + "Crate Key" +
                    ChatColor.RESET + ChatColor.AQUA + " ✦");

            List<String> lore = Arrays.asList(
                    "",
                    ChatColor.GRAY + "A mystical key that unlocks",
                    ChatColor.GRAY + "the secrets within locked crates.",
                    "",
                    ChatColor.YELLOW + "Right-click on a locked crate to unlock it",
                    "",
                    ChatColor.GOLD + "✨ " + ChatColor.ITALIC + "Forged by ancient magic" + ChatColor.GOLD + " ✨"
            );
            meta.setLore(lore);

            // Add subtle enchantment glow
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Store NBT data
            NBTAccessor nbt = new NBTAccessor(key);
            nbt.setString(NBT_CRATE_KEY, "true");
            nbt.setLong(NBT_CREATION_TIME, System.currentTimeMillis());

            key.setItemMeta(meta);
            return nbt.update();

        } catch (Exception e) {
            logger.severe("Error creating crate key: " + e.getMessage());
            return new ItemStack(Material.TRIPWIRE_HOOK);
        }
    }

    /**
     * Determines the crate type from an ItemStack
     *
     * @param item The item to analyze
     * @return The CrateType, or null if not a valid crate
     */
    public CrateType determineCrateType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        try {
            // Check NBT data first
            NBTAccessor nbt = new NBTAccessor(item);
            if (nbt.hasKey(NBT_CRATE_TYPE)) {
                String crateTypeName = nbt.getString(NBT_CRATE_TYPE);
                return CrateType.valueOf(crateTypeName);
            }

            // Fallback to display name analysis
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();

                if (displayName.contains("frozen")) return CrateType.FROZEN;
                if (displayName.contains("legendary")) return CrateType.LEGENDARY;
                if (displayName.contains("ancient")) return CrateType.ANCIENT;
                if (displayName.contains("war")) return CrateType.WAR;
                if (displayName.contains("medium")) return CrateType.MEDIUM;
                if (displayName.contains("basic")) return CrateType.BASIC;
            }

        } catch (Exception e) {
            logger.warning("Error determining crate type: " + e.getMessage());
        }

        return null;
    }

    /**
     * Checks if an item is a valid crate
     *
     * @param item The item to check
     * @return true if the item is a valid crate
     */
    public boolean isCrate(ItemStack item) {
        return determineCrateType(item) != null;
    }

    /**
     * Checks if an item is a crate key
     *
     * @param item The item to check
     * @return true if the item is a crate key
     */
    public boolean isCrateKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        return nbt.hasKey(NBT_CRATE_KEY) && "true".equals(nbt.getString(NBT_CRATE_KEY));
    }

    /**
     * Checks if a crate is a Halloween variant
     *
     * @param item The crate item to check
     * @return true if it's a Halloween crate
     */
    public boolean isHalloweenCrate(ItemStack item) {
        if (!isCrate(item)) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        return nbt.hasKey(NBT_IS_HALLOWEEN) && nbt.getBoolean(NBT_IS_HALLOWEEN);
    }

    /**
     * Creates a locked variant of a crate
     *
     * @param crateType   The type of crate
     * @param isHalloween Whether it's a Halloween variant
     * @return The locked crate item
     */
    public ItemStack createLockedCrate(CrateType crateType, boolean isHalloween) {
        ItemStack crate = createCrate(crateType, isHalloween);
        if (crate == null) return null;

        ItemMeta meta = crate.getItemMeta();
        if (meta == null) return crate;

        // Modify the lore to indicate it's locked
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add locked indicator
        lore.add("");
        lore.add(ChatColor.RED + "🔒 " + ChatColor.BOLD + "LOCKED" + ChatColor.RED + " 🔒");
        lore.add(ChatColor.GRAY + "Requires a " + ChatColor.AQUA + "Crate Key" + ChatColor.GRAY + " to unlock");

        meta.setLore(lore);

        // Store locked status in NBT
        NBTAccessor nbt = new NBTAccessor(crate);
        nbt.setBoolean("locked", true);

        crate.setItemMeta(meta);
        return nbt.update();
    }

    /**
     * Determines the appropriate material for a crate
     *
     * @param crateType   The crate type
     * @param isHalloween Whether it's Halloween variant
     * @return The Material to use
     */
    private Material determineCrateMaterial(CrateType crateType, boolean isHalloween) {
        if (isHalloween) {
            return Material.CARVED_PUMPKIN;
        }

        // Use different materials based on tier for visual variety
        return switch (crateType.getTier()) {
            case 1, 2 -> Material.CHEST;
            case 3, 4 -> Material.TRAPPED_CHEST;
            case 5, 6 -> Material.ENDER_CHEST;
            default -> Material.CHEST;
        };
    }

    /**
     * Creates the display name for a crate
     *
     * @param crateType   The crate type
     * @param isHalloween Whether it's Halloween variant
     * @return The formatted display name
     */
    private String createCrateDisplayName(CrateType crateType, boolean isHalloween) {
        ChatColor color = getCrateColor(crateType);
        String tierName = crateType.getDisplayName();
        String symbol = getCrateSymbol(crateType);

        StringBuilder name = new StringBuilder();
        name.append(color).append(symbol).append(" ").append(ChatColor.BOLD);

        if (isHalloween) {
            name.append("Halloween ");
        }

        name.append(tierName).append(" Loot Crate");
        name.append(ChatColor.RESET).append(color).append(" ").append(symbol);

        return name.toString();
    }

    /**
     * Creates enhanced lore for a crate
     *
     * @param crateType   The crate type
     * @param isHalloween Whether it's Halloween variant
     * @return The lore list
     */
    private List<String> createCrateLore(CrateType crateType, boolean isHalloween) {
        List<String> lore = new ArrayList<>();
        ChatColor color = getCrateColor(crateType);

        lore.add("");
        lore.add(ChatColor.WHITE + "" + ChatColor.BOLD + "" + ChatColor.UNDERLINE + "Contents:");
        lore.add(color + "Randomized Tier " + crateType.getTier() + " Equipment");

        // Add tier-specific content descriptions
        addTierSpecificLore(lore, crateType, color);

        if (isHalloween) {
            lore.add(ChatColor.GOLD + "🎃 Halloween Bonus Rewards 🎃");
            lore.add(ChatColor.DARK_PURPLE + "Enhanced with spooky magic!");
        }

        lore.add("");
        lore.add(ChatColor.YELLOW + "Click in your inventory to open");
        lore.add(ChatColor.GRAY + "Right-click in hand for scrap value");
        lore.add("");
        lore.add(color + "✦ " + ChatColor.BOLD + "MAGICAL CONTAINER" + ChatColor.RESET + color + " ✦");

        return lore;
    }

    /**
     * Adds tier-specific lore content
     *
     * @param lore      The lore list to add to
     * @param crateType The crate type
     * @param color     The crate color
     */
    private void addTierSpecificLore(List<String> lore, CrateType crateType, ChatColor color) {
        switch (crateType.getTier()) {
            case 1:
                lore.add(color + "Basic weapons and armor");
                lore.add(color + "Common enhancement scrolls");
                break;
            case 2:
                lore.add(color + "Improved weapons and armor");
                lore.add(color + "Uncommon enhancement scrolls");
                break;
            case 3:
                lore.add(color + "War-grade equipment");
                lore.add(color + "Rare enhancement scrolls");
                lore.add(color + "Basic orbs of alteration");
                break;
            case 4:
                lore.add(color + "Ancient artifacts");
                lore.add(color + "Epic enhancement scrolls");
                lore.add(color + "Powerful orbs of alteration");
                break;
            case 5:
                lore.add(color + "Legendary equipment");
                lore.add(color + "Master enhancement scrolls");
                lore.add(color + "Legendary orbs of alteration");
                lore.add(color + "Rare teleport scrolls");
                break;
            case 6:
                lore.add(color + "Frozen artifacts of power");
                lore.add(color + "Ultimate enhancement scrolls");
                lore.add(color + "Celestial orbs of alteration");
                lore.add(color + "Epic teleport scrolls");
                lore.add(color + "Exclusive frozen weapons");
                break;
        }
    }

    /**
     * Enhances crate visuals with effects
     *
     * @param meta        The item meta
     * @param crateType   The crate type
     * @param isHalloween Whether it's Halloween variant
     */
    private void enhanceCrateVisuals(ItemMeta meta, CrateType crateType, boolean isHalloween) {
        // Add enchantment glow for higher tier crates or Halloween variants
        if (crateType.getTier() >= 4 || isHalloween) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Hide other attributes for cleaner appearance
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    /**
     * Stores NBT data for crate identification
     *
     * @param meta        The item meta
     * @param crateType   The crate type
     * @param isHalloween Whether it's Halloween variant
     */
    private void storeCrateNBTData(ItemMeta meta, CrateType crateType, boolean isHalloween) {
        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(new NamespacedKey(plugin, NBT_CRATE_TYPE),
                PersistentDataType.STRING, crateType.name());
        container.set(new NamespacedKey(plugin, NBT_CRATE_TIER),
                PersistentDataType.INTEGER, crateType.getTier());
        container.set(new NamespacedKey(plugin, NBT_IS_HALLOWEEN),
                PersistentDataType.BYTE, isHalloween ? (byte) 1 : (byte) 0);
        container.set(new NamespacedKey(plugin, NBT_CREATION_TIME),
                PersistentDataType.LONG, System.currentTimeMillis());
    }

    /**
     * Gets the color associated with a crate type
     *
     * @param crateType The crate type
     * @return The ChatColor
     */
    private ChatColor getCrateColor(CrateType crateType) {
        return switch (crateType.getTier()) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.BLUE;
            default -> ChatColor.GRAY;
        };
    }

    /**
     * Gets the symbol associated with a crate type
     *
     * @param crateType The crate type
     * @return The symbol string
     */
    private String getCrateSymbol(CrateType crateType) {
        return switch (crateType.getTier()) {
            case 1 -> "◆";
            case 2 -> "◇";
            case 3 -> "★";
            case 4 -> "✦";
            case 5 -> "✧";
            case 6 -> "❅";
            default -> "◆";
        };
    }

    /**
     * Creates a fallback crate in case of errors
     *
     * @return A basic fallback crate
     */
    private ItemStack createFallbackCrate() {
        ItemStack fallback = new ItemStack(Material.CHEST);
        ItemMeta meta = fallback.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Crate");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Something went wrong during creation",
                    ChatColor.GRAY + "Please contact an administrator"
            ));
            fallback.setItemMeta(meta);
        }

        return fallback;
    }

    /**
     * Gets crate statistics for debugging
     *
     * @return Statistics map
     */
    public java.util.Map<String, Object> getFactoryStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("supportedCrateTypes", CrateType.values().length);
        stats.put("materialsUsed", java.util.Set.of(Material.CHEST, Material.TRAPPED_CHEST,
                Material.ENDER_CHEST, Material.CARVED_PUMPKIN).size());
        return stats;
    }
}
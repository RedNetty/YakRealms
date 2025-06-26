package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.types.CrateKey;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Enhanced factory class for creating crate items and keys using 1.20.4 features
 * Provides comprehensive item creation with modern visual effects and metadata
 */
public class CrateFactory {
    private final YakRealms plugin;
    private final Logger logger;

    // Enhanced NBT keys for comprehensive identification
    private static final String NBT_CRATE_TYPE = "crateType";
    private static final String NBT_CRATE_TIER = "crateTier";
    private static final String NBT_IS_HALLOWEEN = "isHalloween";
    private static final String NBT_CREATION_TIME = "creationTime";
    private static final String NBT_CREATION_DATE = "creationDate";
    private static final String NBT_CRATE_KEY = "crateKey";
    private static final String NBT_KEY_TYPE = "keyType";
    private static final String NBT_FACTORY_VERSION = "factoryVersion";
    private static final String NBT_LOCKED = "locked";
    private static final String NBT_SESSION_ID = "sessionId";
    private static final String NBT_CREATOR = "creator";

    // Enhanced visual elements
    private static final String FACTORY_VERSION = "2.0.0";
    private static final Map<Integer, String> TIER_SYMBOLS = Map.of(
            1, "◆", 2, "◇", 3, "★", 4, "✦", 5, "✧", 6, "❅"
    );
    private static final Map<Integer, String> TIER_NAMES = Map.of(
            1, "Common", 2, "Uncommon", 3, "Rare", 4, "Epic", 5, "Legendary", 6, "Mythical"
    );

    /**
     * Enhanced crate material configuration
     */
    private static class CrateMaterials {
        private static final Map<Integer, List<Material>> TIER_MATERIALS = Map.of(
                1, Arrays.asList(Material.CHEST, Material.BARREL),
                2, Arrays.asList(Material.CHEST, Material.TRAPPED_CHEST),
                3, Arrays.asList(Material.TRAPPED_CHEST, Material.ENDER_CHEST),
                4, Arrays.asList(Material.ENDER_CHEST, Material.SHULKER_BOX),
                5, Arrays.asList(Material.ENDER_CHEST, Material.SHULKER_BOX),
                6, Arrays.asList(Material.ENDER_CHEST, Material.SHULKER_BOX)
        );

        private static final Material HALLOWEEN_MATERIAL = Material.CARVED_PUMPKIN;
        private static final Material LOCKED_OVERLAY = Material.IRON_BARS;
    }

    /**
     * Constructor
     */
    public CrateFactory() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    /**
     * Creates an enhanced crate item with comprehensive metadata and visuals
     *
     * @param crateType   The type of crate to create
     * @param isHalloween Whether this is a Halloween variant
     * @return The created crate ItemStack with enhanced features
     */
    public ItemStack createCrate(CrateType crateType, boolean isHalloween) {
        return createCrate(crateType, isHalloween, null);
    }

    /**
     * Creates an enhanced crate item with creator information
     *
     * @param crateType   The type of crate to create
     * @param isHalloween Whether this is a Halloween variant
     * @param creator     The player who created this crate (null for system)
     * @return The created crate ItemStack with enhanced features
     */
    public ItemStack createCrate(CrateType crateType, boolean isHalloween, String creator) {
        if (crateType == null) {
            logger.warning("Attempted to create crate with null type");
            return null;
        }

        try {
            // Choose enhanced material based on type and season
            Material material = determineEnhancedCrateMaterial(crateType, isHalloween);
            ItemStack crate = new ItemStack(material);
            ItemMeta meta = crate.getItemMeta();

            if (meta == null) {
                logger.warning("Failed to get ItemMeta for crate");
                return crate;
            }

            // Set enhanced display name with modern formatting
            String displayName = createEnhancedCrateDisplayName(crateType, isHalloween);
            meta.setDisplayName(displayName);

            // Create comprehensive lore with enhanced information
            List<String> lore = createEnhancedCrateLore(crateType, isHalloween);
            meta.setLore(lore);

            // Add enhanced visual effects
            enhanceEnhancedCrateVisuals(meta, crateType, isHalloween);

            // Store comprehensive NBT data
            storeEnhancedCrateNBTData(meta, crateType, isHalloween, creator);

            // Apply final meta
            crate.setItemMeta(meta);

            logger.fine("Created enhanced " + (isHalloween ? "Halloween " : "") +
                    crateType + " crate" + (creator != null ? " for " + creator : ""));
            return crate;

        } catch (Exception e) {
            logger.severe("Error creating enhanced crate: " + e.getMessage());
            e.printStackTrace();
            return createFallbackCrate(crateType);
        }
    }

    /**
     * Creates an enhanced crate key with modern features
     *
     * @param keyType The type of key to create (optional, defaults to universal)
     * @return The created crate key ItemStack
     */
    public ItemStack createCrateKey(CrateKey keyType) {
        if (keyType == null) {
            keyType = CrateKey.UNIVERSAL;
        }

        try {
            // Enhanced key material selection
            Material keyMaterial = determineKeyMaterial(keyType);
            ItemStack key = new ItemStack(keyMaterial);
            ItemMeta meta = key.getItemMeta();

            if (meta == null) {
                return key;
            }

            // Enhanced key design with tier-specific styling
            String displayName = createEnhancedKeyDisplayName(keyType);
            meta.setDisplayName(displayName);

            // Enhanced lore with detailed information
            List<String> lore = createEnhancedKeyLore(keyType);
            meta.setLore(lore);

            // Enhanced visual effects
            enhanceKeyVisuals(meta, keyType);

            // Store enhanced NBT data
            storeEnhancedKeyNBTData(meta, keyType);

            key.setItemMeta(meta);
            return key;

        } catch (Exception e) {
            logger.severe("Error creating enhanced crate key: " + e.getMessage());
            return createFallbackKey();
        }
    }

    /**
     * Creates a universal crate key (default)
     */
    public ItemStack createCrateKey() {
        return createCrateKey(CrateKey.UNIVERSAL);
    }

    /**
     * Creates a locked crate with enhanced security features
     *
     * @param crateType   The type of crate
     * @param isHalloween Whether it's a Halloween variant
     * @return The locked crate item with enhanced security metadata
     */
    public ItemStack createLockedCrate(CrateType crateType, boolean isHalloween) {
        return createLockedCrate(crateType, isHalloween, null);
    }

    /**
     * Creates a locked crate with creator information
     *
     * @param crateType   The type of crate
     * @param isHalloween Whether it's a Halloween variant
     * @param creator     The player who created this locked crate
     * @return The locked crate item with enhanced security metadata
     */
    public ItemStack createLockedCrate(CrateType crateType, boolean isHalloween, String creator) {
        ItemStack crate = createCrate(crateType, isHalloween, creator);
        if (crate == null) return null;

        ItemMeta meta = crate.getItemMeta();
        if (meta == null) return crate;

        // Enhanced locked crate modifications
        enhanceLockedCrate(meta, crateType);

        // Store enhanced locked status in NBT
        storeLockedNBTData(meta);

        crate.setItemMeta(meta);
        return crate;
    }

    /**
     * Creates a special edition crate with unique properties
     *
     * @param crateType   The base crate type
     * @param edition     The special edition name
     * @param properties  Additional properties for the special edition
     * @return The special edition crate
     */
    public ItemStack createSpecialEditionCrate(CrateType crateType, String edition, Map<String, Object> properties) {
        ItemStack crate = createCrate(crateType, false);
        if (crate == null) return null;

        ItemMeta meta = crate.getItemMeta();
        if (meta == null) return crate;

        // Enhance for special edition
        String currentName = meta.getDisplayName();
        meta.setDisplayName(ChatColor.GOLD + "✧ SPECIAL EDITION ✧ " + currentName);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GOLD + "★ " + ChatColor.BOLD + edition.toUpperCase() + " EDITION" + ChatColor.GOLD + " ★");
        lore.add(ChatColor.YELLOW + "Limited time exclusive variant!");

        // Add property-specific lore
        if (properties != null) {
            properties.forEach((key, value) -> {
                lore.add(ChatColor.GRAY + "• " + formatPropertyName(key) + ": " +
                        ChatColor.WHITE + value.toString());
            });
        }

        meta.setLore(lore);

        // Add special enchantment glow
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Store special edition data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, "specialEdition"), PersistentDataType.STRING, edition);
        if (properties != null) {
            properties.forEach((key, value) -> {
                container.set(new NamespacedKey(plugin, "prop_" + key),
                        PersistentDataType.STRING, value.toString());
            });
        }

        crate.setItemMeta(meta);
        return crate;
    }

    /**
     * Enhanced crate type determination with comprehensive validation
     *
     * @param item The item to analyze
     * @return The CrateType, or null if not a valid crate
     */
    public CrateType determineCrateType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        try {
            // Primary method: Check enhanced NBT data
            CrateType nbtType = getCrateTypeFromNBT(item);
            if (nbtType != null) {
                return nbtType;
            }

            // Secondary method: Check persistent data container
            CrateType pdcType = getCrateTypeFromPDC(item);
            if (pdcType != null) {
                return pdcType;
            }

            // Fallback method: Analyze display name and lore
            CrateType fallbackType = getCrateTypeFromDisplayName(item);
            if (fallbackType != null) {
                // Upgrade old crate with new NBT data
                upgradeOldCrate(item, fallbackType);
                return fallbackType;
            }

        } catch (Exception e) {
            logger.warning("Error determining crate type: " + e.getMessage());
        }

        return null;
    }

    /**
     * Enhanced validation methods
     */
    public boolean isCrate(ItemStack item) {
        return determineCrateType(item) != null;
    }

    public boolean isCrateKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        // Check material first for performance
        if (!isValidKeyMaterial(item.getType())) {
            return false;
        }

        // Check NBT data
        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey(NBT_CRATE_KEY) && "true".equals(nbt.getString(NBT_CRATE_KEY))) {
            return true;
        }

        // Check persistent data container
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(new NamespacedKey(plugin, NBT_CRATE_KEY), PersistentDataType.STRING);
    }

    public boolean isHalloweenCrate(ItemStack item) {
        if (!isCrate(item)) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey(NBT_IS_HALLOWEEN)) {
            return nbt.getBoolean(NBT_IS_HALLOWEEN);
        }

        // Check PDC as fallback
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(new NamespacedKey(plugin, NBT_IS_HALLOWEEN), PersistentDataType.BYTE)) {
            return container.get(new NamespacedKey(plugin, NBT_IS_HALLOWEEN), PersistentDataType.BYTE) == 1;
        }

        return false;
    }

    public boolean isLockedCrate(ItemStack item) {
        if (!isCrate(item)) {
            return false;
        }

        NBTAccessor nbt = new NBTAccessor(item);
        if (nbt.hasKey(NBT_LOCKED)) {
            return nbt.getBoolean(NBT_LOCKED);
        }

        // Check lore as fallback
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            return item.getItemMeta().getLore().stream()
                    .anyMatch(line -> line.contains("LOCKED"));
        }

        return false;
    }

    public boolean isSpecialEdition(ItemStack item) {
        if (!isCrate(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(new NamespacedKey(plugin, "specialEdition"), PersistentDataType.STRING);
    }

    /**
     * Enhanced helper methods for material determination
     */
    private Material determineEnhancedCrateMaterial(CrateType crateType, boolean isHalloween) {
        if (isHalloween) {
            return CrateMaterials.HALLOWEEN_MATERIAL;
        }

        List<Material> tierMaterials = CrateMaterials.TIER_MATERIALS.get(crateType.getTier());
        if (tierMaterials == null || tierMaterials.isEmpty()) {
            return Material.CHEST;
        }

        // For higher tiers, prefer more exotic materials
        if (crateType.getTier() >= 4 && tierMaterials.size() > 1) {
            return tierMaterials.get(1); // Second material (more exotic)
        }

        return tierMaterials.get(0); // First material (common)
    }

    private Material determineKeyMaterial(CrateKey keyType) {
        return switch (keyType) {
            case UNIVERSAL -> Material.TRIPWIRE_HOOK;
            case BASIC -> Material.IRON_NUGGET;
            case MEDIUM -> Material.GOLD_NUGGET;
            case WAR -> Material.DIAMOND;
            case ANCIENT -> Material.EMERALD;
            case LEGENDARY -> Material.NETHERITE_INGOT;
            case FROZEN -> Material.PRISMARINE_SHARD;
        };
    }

    private boolean isValidKeyMaterial(Material material) {
        return material == Material.TRIPWIRE_HOOK ||
                material == Material.IRON_NUGGET ||
                material == Material.GOLD_NUGGET ||
                material == Material.DIAMOND ||
                material == Material.EMERALD ||
                material == Material.NETHERITE_INGOT ||
                material == Material.PRISMARINE_SHARD;
    }

    /**
     * Enhanced display name creation methods
     */
    private String createEnhancedCrateDisplayName(CrateType crateType, boolean isHalloween) {
        ChatColor color = getEnhancedCrateColor(crateType);
        String tierName = crateType.getDisplayName();
        String symbol = TIER_SYMBOLS.get(crateType.getTier());
        String quality = TIER_NAMES.get(crateType.getTier());

        StringBuilder name = new StringBuilder();
        name.append(color).append(symbol).append(" ").append(ChatColor.BOLD);

        if (isHalloween) {
            name.append("Halloween ");
        }

        name.append(tierName).append(" Mystical Crate");
        name.append(ChatColor.RESET).append(color).append(" ").append(symbol);

        return name.toString();
    }

    private String createEnhancedKeyDisplayName(CrateKey keyType) {
        ChatColor color = getKeyColor(keyType);
        String keySymbol = getKeySymbol(keyType);

        return color + keySymbol + " " + ChatColor.BOLD + keyType.getDisplayName() +
                ChatColor.RESET + color + " " + keySymbol;
    }

    /**
     * Enhanced lore creation methods
     */
    private List<String> createEnhancedCrateLore(CrateType crateType, boolean isHalloween) {
        List<String> lore = new ArrayList<>();
        ChatColor color = getEnhancedCrateColor(crateType);
        String quality = TIER_NAMES.get(crateType.getTier());

        // Header section
        lore.add("");
        lore.add(ChatColor.GRAY + "═══ " + ChatColor.WHITE + ChatColor.BOLD +
                "MYSTICAL CONTAINER" + ChatColor.GRAY + " ═══");
        lore.add("");

        // Quality and tier information
        lore.add(ChatColor.WHITE + "Quality: " + color + ChatColor.BOLD + quality);
        lore.add(ChatColor.WHITE + "Tier: " + color + crateType.getTier() +
                ChatColor.GRAY + "/6");

        if (isHalloween) {
            lore.add(ChatColor.GOLD + "Season: " + ChatColor.GOLD + ChatColor.BOLD +
                    "🎃 Halloween Special 🎃");
        }

        lore.add("");

        // Contents preview
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "" + ChatColor.UNDERLINE + "Mystical Contents:");
        addEnhancedTierSpecificLore(lore, crateType, color);

        if (isHalloween) {
            lore.add(ChatColor.GOLD + "🎃 Halloween Bonus Rewards");
            lore.add(ChatColor.DARK_PURPLE + "Enhanced with spooky magic!");
        }

        lore.add("");

        // Usage instructions
        lore.add(ChatColor.GREEN + "▶ " + ChatColor.BOLD + "Usage Instructions:");
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Left-click in inventory to open");
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Right-click in hand for scrap value");

        // Value information
        lore.add("");
        int scrapValue = calculateScrapValue(crateType, isHalloween);
        lore.add(ChatColor.GOLD + "💰 Scrap Value: " + ChatColor.YELLOW +
                formatNumber(scrapValue) + "g");

        // Footer with metadata
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Created: " + getCurrentDateString());
        lore.add(ChatColor.DARK_GRAY + "Session: " + YakRealms.getSessionID());

        return lore;
    }

    private List<String> createEnhancedKeyLore(CrateKey keyType) {
        List<String> lore = new ArrayList<>();
        ChatColor color = getKeyColor(keyType);

        lore.add("");
        lore.add(ChatColor.GRAY + "═══ " + ChatColor.AQUA + ChatColor.BOLD +
                "MYSTICAL KEY" + ChatColor.GRAY + " ═══");
        lore.add("");

        lore.add(ChatColor.WHITE + "Type: " + color + ChatColor.BOLD + keyType.getDisplayName());
        lore.add(ChatColor.WHITE + "Purpose: " + ChatColor.GRAY + keyType.getDescription());

        lore.add("");
        lore.add(ChatColor.YELLOW + "" + ChatColor.BOLD + "Unlocking Power:");

        if (keyType.isUniversal()) {
            lore.add(ChatColor.GREEN + "✓ Can unlock ALL crate types");
            lore.add(ChatColor.GOLD + "⚡ Universal compatibility");
        } else {
            lore.add(ChatColor.WHITE + "• Unlocks: " + color +
                    keyType.name().replace("_", " ").toLowerCase() + " crates");
            lore.add(ChatColor.GRAY + "• Specific tier compatibility");
        }

        lore.add("");
        lore.add(ChatColor.GREEN + "▶ " + ChatColor.BOLD + "Usage:");
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Drag onto locked crate");
        lore.add(ChatColor.WHITE + "• " + ChatColor.GRAY + "Both items will be consumed");

        lore.add("");
        lore.add(ChatColor.AQUA + "✨ " + ChatColor.ITALIC + "Forged by ancient magic" +
                ChatColor.AQUA + " ✨");

        return lore;
    }

    /**
     * Enhanced visual effect methods
     */
    private void enhanceEnhancedCrateVisuals(ItemMeta meta, CrateType crateType, boolean isHalloween) {
        // Add enchantment glow for higher tier crates or Halloween variants
        if (crateType.getTier() >= 3 || isHalloween) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Hide all attributes for cleaner appearance
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
        meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // Make unbreakable for durability
        meta.setUnbreakable(true);
    }

    private void enhanceKeyVisuals(ItemMeta meta, CrateKey keyType) {
        // Add subtle enchantment glow for all keys
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Hide attributes
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // Make unbreakable
        meta.setUnbreakable(true);

        // Add special effects for higher tier keys
        if (keyType == CrateKey.LEGENDARY || keyType == CrateKey.FROZEN) {
            meta.addEnchant(Enchantment.MENDING, 1, true);
        }
    }

    private void enhanceLockedCrate(ItemMeta meta, CrateType crateType) {
        // Modify the display name to indicate locked status
        String currentName = meta.getDisplayName();
        meta.setDisplayName(currentName + ChatColor.RED + " 🔒");

        // Modify the lore to indicate it's locked
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add locked section before the footer
        lore.add("");
        lore.add(ChatColor.RED + "🔒 " + ChatColor.BOLD + "MYSTICALLY SEALED" + ChatColor.RED + " 🔒");
        lore.add(ChatColor.GRAY + "This crate is bound by ancient magic");
        lore.add(ChatColor.YELLOW + "Requires: " + ChatColor.AQUA + "Crate Key " +
                ChatColor.YELLOW + "to unlock");

        meta.setLore(lore);

        // Add red enchantment glow
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
    }

    /**
     * Enhanced NBT data storage methods
     */
    private void storeEnhancedCrateNBTData(ItemMeta meta, CrateType crateType, boolean isHalloween, String creator) {
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // Core crate data
        container.set(new NamespacedKey(plugin, NBT_CRATE_TYPE),
                PersistentDataType.STRING, crateType.name());
        container.set(new NamespacedKey(plugin, NBT_CRATE_TIER),
                PersistentDataType.INTEGER, crateType.getTier());
        container.set(new NamespacedKey(plugin, NBT_IS_HALLOWEEN),
                PersistentDataType.BYTE, isHalloween ? (byte) 1 : (byte) 0);

        // Enhanced metadata
        container.set(new NamespacedKey(plugin, NBT_CREATION_TIME),
                PersistentDataType.LONG, System.currentTimeMillis());
        container.set(new NamespacedKey(plugin, NBT_CREATION_DATE),
                PersistentDataType.STRING, getCurrentDateString());
        container.set(new NamespacedKey(plugin, NBT_FACTORY_VERSION),
                PersistentDataType.STRING, FACTORY_VERSION);
        container.set(new NamespacedKey(plugin, NBT_SESSION_ID),
                PersistentDataType.INTEGER, YakRealms.getSessionID());

        if (creator != null) {
            container.set(new NamespacedKey(plugin, NBT_CREATOR),
                    PersistentDataType.STRING, creator);
        }
    }

    private void storeEnhancedKeyNBTData(ItemMeta meta, CrateKey keyType) {
        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(new NamespacedKey(plugin, NBT_CRATE_KEY),
                PersistentDataType.STRING, "true");
        container.set(new NamespacedKey(plugin, NBT_KEY_TYPE),
                PersistentDataType.STRING, keyType.name());
        container.set(new NamespacedKey(plugin, NBT_CREATION_TIME),
                PersistentDataType.LONG, System.currentTimeMillis());
        container.set(new NamespacedKey(plugin, NBT_FACTORY_VERSION),
                PersistentDataType.STRING, FACTORY_VERSION);
    }

    private void storeLockedNBTData(ItemMeta meta) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(new NamespacedKey(plugin, NBT_LOCKED),
                PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Enhanced retrieval methods
     */
    private CrateType getCrateTypeFromNBT(ItemStack item) {
        try {
            NBTAccessor nbt = new NBTAccessor(item);
            if (nbt.hasKey(NBT_CRATE_TYPE)) {
                String crateTypeName = nbt.getString(NBT_CRATE_TYPE);
                return CrateType.valueOf(crateTypeName);
            }
        } catch (Exception e) {
            // Ignore and try other methods
        }
        return null;
    }

    private CrateType getCrateTypeFromPDC(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(new NamespacedKey(plugin, NBT_CRATE_TYPE), PersistentDataType.STRING)) {
                    String crateTypeName = container.get(new NamespacedKey(plugin, NBT_CRATE_TYPE), PersistentDataType.STRING);
                    return CrateType.valueOf(crateTypeName);
                }
            }
        } catch (Exception e) {
            // Ignore and try other methods
        }
        return null;
    }

    private CrateType getCrateTypeFromDisplayName(ItemStack item) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();

                // Check for each crate type
                for (CrateType type : CrateType.values()) {
                    String typeName = type.getDisplayName().toLowerCase();
                    if (displayName.contains(typeName) && displayName.contains("crate")) {
                        return type;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Enhanced utility methods
     */
    private void addEnhancedTierSpecificLore(List<String> lore, CrateType crateType, ChatColor color) {
        switch (crateType.getTier()) {
            case 1:
                lore.add(color + "• Basic weapons and armor");
                lore.add(color + "• Common enhancement scrolls");
                lore.add(color + "• Small gem rewards");
                break;
            case 2:
                lore.add(color + "• Improved weapons and armor");
                lore.add(color + "• Uncommon enhancement scrolls");
                lore.add(color + "• Moderate gem rewards");
                lore.add(color + "• Basic orbs of alteration");
                break;
            case 3:
                lore.add(color + "• War-grade equipment");
                lore.add(color + "• Rare enhancement scrolls");
                lore.add(color + "• Valuable gem rewards");
                lore.add(color + "• Enhanced orbs of alteration");
                break;
            case 4:
                lore.add(color + "• Ancient artifacts");
                lore.add(color + "• Epic enhancement scrolls");
                lore.add(color + "• Large gem rewards");
                lore.add(color + "• Powerful orbs of alteration");
                lore.add(color + "• Protection scrolls");
                break;
            case 5:
                lore.add(color + "• Legendary equipment");
                lore.add(color + "• Master enhancement scrolls");
                lore.add(color + "• Massive gem rewards");
                lore.add(color + "• Legendary orbs of alteration");
                lore.add(color + "• Advanced protection scrolls");
                lore.add(color + "• Rare teleport scrolls");
                break;
            case 6:
                lore.add(color + "• Mythical frozen artifacts");
                lore.add(color + "• Ultimate enhancement scrolls");
                lore.add(color + "• Epic gem collections");
                lore.add(color + "• Celestial orbs of alteration");
                lore.add(color + "• Ultimate protection scrolls");
                lore.add(color + "• Epic teleport scrolls");
                lore.add(color + "• Exclusive frozen weapons");
                break;
        }
    }

    private ChatColor getEnhancedCrateColor(CrateType crateType) {
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

    private ChatColor getKeyColor(CrateKey keyType) {
        return switch (keyType) {
            case UNIVERSAL -> ChatColor.AQUA;
            case BASIC -> ChatColor.WHITE;
            case MEDIUM -> ChatColor.GREEN;
            case WAR -> ChatColor.BLUE;
            case ANCIENT -> ChatColor.LIGHT_PURPLE;
            case LEGENDARY -> ChatColor.YELLOW;
            case FROZEN -> ChatColor.BLUE;
        };
    }

    private String getKeySymbol(CrateKey keyType) {
        return switch (keyType) {
            case UNIVERSAL -> "✦";
            case BASIC -> "◆";
            case MEDIUM -> "◇";
            case WAR -> "★";
            case ANCIENT -> "✧";
            case LEGENDARY -> "❋";
            case FROZEN -> "❅";
        };
    }

    private int calculateScrapValue(CrateType crateType, boolean isHalloween) {
        int baseValue = switch (crateType.getTier()) {
            case 1 -> 75;
            case 2 -> 150;
            case 3 -> 300;
            case 4 -> 600;
            case 5 -> 1250;
            case 6 -> 2500;
            default -> 50;
        };

        if (isHalloween) {
            baseValue = (int) (baseValue * 1.75);
        }

        return baseValue;
    }

    private String getCurrentDateString() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    private String formatPropertyName(String property) {
        return Arrays.stream(property.split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .reduce((a, b) -> a + " " + b)
                .orElse(property);
    }

    /**
     * Legacy support methods
     */
    private void upgradeOldCrate(ItemStack item, CrateType crateType) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Add missing NBT data
                storeEnhancedCrateNBTData(meta, crateType, false, null);
                item.setItemMeta(meta);
                logger.fine("Upgraded old crate to new format: " + crateType);
            }
        } catch (Exception e) {
            logger.warning("Failed to upgrade old crate: " + e.getMessage());
        }
    }

    /**
     * Fallback creation methods
     */
    private ItemStack createFallbackCrate(CrateType crateType) {
        ItemStack fallback = new ItemStack(Material.CHEST);
        ItemMeta meta = fallback.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Crate (" +
                    (crateType != null ? crateType.getDisplayName() : "Unknown") + ")");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "An error occurred during crate creation",
                    ChatColor.GRAY + "Please contact an administrator",
                    ChatColor.DARK_GRAY + "Error Code: FACTORY_FALLBACK"
            ));
            fallback.setItemMeta(meta);
        }

        return fallback;
    }

    private ItemStack createFallbackKey() {
        ItemStack fallback = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = fallback.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Error Crate Key");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "An error occurred during key creation",
                    ChatColor.GRAY + "Please contact an administrator"
            ));
            fallback.setItemMeta(meta);
        }

        return fallback;
    }

    /**
     * Enhanced statistics and debugging
     */
    public Map<String, Object> getFactoryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("factoryVersion", FACTORY_VERSION);
        stats.put("supportedCrateTypes", CrateType.values().length);
        stats.put("supportedKeyTypes", CrateKey.values().length);
        stats.put("tierSymbols", TIER_SYMBOLS.size());
        stats.put("tierNames", TIER_NAMES.size());
        stats.put("materialsPerTier", CrateMaterials.TIER_MATERIALS.size());
        stats.put("nbtsUsed", Arrays.asList(
                NBT_CRATE_TYPE, NBT_CRATE_TIER, NBT_IS_HALLOWEEN, NBT_CREATION_TIME,
                NBT_CRATE_KEY, NBT_KEY_TYPE, NBT_FACTORY_VERSION, NBT_LOCKED
        ).size());
        return stats;
    }

    /**
     * Validation method for factory integrity
     */
    public boolean validateFactoryIntegrity() {
        try {
            // Test crate creation for each type
            for (CrateType type : CrateType.getRegularTypes()) {
                ItemStack testCrate = createCrate(type, false);
                if (testCrate == null || determineCrateType(testCrate) != type) {
                    logger.warning("Factory integrity check failed for crate type: " + type);
                    return false;
                }
            }

            // Test key creation for each type
            for (CrateKey keyType : CrateKey.values()) {
                ItemStack testKey = createCrateKey(keyType);
                if (testKey == null || !isCrateKey(testKey)) {
                    logger.warning("Factory integrity check failed for key type: " + keyType);
                    return false;
                }
            }

            logger.fine("Factory integrity check passed");
            return true;

        } catch (Exception e) {
            logger.severe("Factory integrity check failed with exception: " + e.getMessage());
            return false;
        }
    }
}
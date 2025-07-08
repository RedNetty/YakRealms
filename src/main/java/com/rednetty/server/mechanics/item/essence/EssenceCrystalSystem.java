package com.rednetty.server.mechanics.item.essence;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.particles.FireworkUtil;
import com.rednetty.server.utils.particles.ParticleUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive Essence Crystal System - Extract, store, and transfer item stats
 */
public class EssenceCrystalSystem implements Listener {
    private static EssenceCrystalSystem instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Namespaced keys for persistent data
    private final NamespacedKey keyEssenceType;
    private final NamespacedKey keyEssenceValue;
    private final NamespacedKey keyEssenceQuality;
    private final NamespacedKey keyEssenceTier;
    private final NamespacedKey keyExtractorType;
    private final NamespacedKey keyInfuserType;

    // Processing tracking to prevent double-clicks
    private final Set<UUID> processingPlayers = new HashSet<>();

    // Stat extraction patterns
    private static final Pattern DAMAGE_PATTERN = Pattern.compile("DMG: (\\d+) - (\\d+)");
    private static final Pattern HP_PATTERN = Pattern.compile("HP: \\+(\\d+)");
    private static final Pattern ARMOR_PATTERN = Pattern.compile("ARMOR: (\\d+) - (\\d+)%");
    private static final Pattern DPS_PATTERN = Pattern.compile("DPS: (\\d+) - (\\d+)%");
    private static final Pattern STAT_PATTERN = Pattern.compile("(\\w+): \\+(\\d+)");
    private static final Pattern PERCENT_STAT_PATTERN = Pattern.compile("(\\w+ \\w+): (\\d+)%");
    private static final Pattern ELEMENTAL_PATTERN = Pattern.compile("(\\w+ DMG): \\+(\\d+)");

    // Crystal quality levels
    public enum CrystalQuality {
        FLAWED(0.8, ChatColor.GRAY, "◆"),
        NORMAL(1.0, ChatColor.WHITE, "◇"),
        PERFECT(1.25, ChatColor.YELLOW, "✦");

        private final double multiplier;
        private final ChatColor color;
        private final String symbol;

        CrystalQuality(double multiplier, ChatColor color, String symbol) {
            this.multiplier = multiplier;
            this.color = color;
            this.symbol = symbol;
        }

        public double getMultiplier() { return multiplier; }
        public ChatColor getColor() { return color; }
        public String getSymbol() { return symbol; }
    }

    // Essence types that can be extracted
    public enum EssenceType {
        DAMAGE("DMG", ChatColor.RED, "⚔"),
        HP("HP", ChatColor.GREEN, "❤"),
        ARMOR("ARMOR", ChatColor.BLUE, "🛡"),
        DPS("DPS", ChatColor.DARK_RED, "⚡"),
        STRENGTH("STR", ChatColor.GOLD, "💪"),
        INTELLECT("INT", ChatColor.AQUA, "🧠"),
        VITALITY("VIT", ChatColor.DARK_GREEN, "🌿"),
        DEXTERITY("DEX", ChatColor.YELLOW, "🏃"),
        CRITICAL_HIT("CRITICAL HIT", ChatColor.GOLD, "💥"),
        LIFE_STEAL("LIFE STEAL", ChatColor.DARK_RED, "🩸"),
        ACCURACY("ACCURACY", ChatColor.WHITE, "🎯"),
        DODGE("DODGE", ChatColor.LIGHT_PURPLE, "💨"),
        BLOCK("BLOCK", ChatColor.GRAY, "🛡"),
        ENERGY_REGEN("ENERGY REGEN", ChatColor.BLUE, "⚡"),
        HP_REGEN("HP REGEN", ChatColor.GREEN, "💚"),
        FIRE_DAMAGE("FIRE DMG", ChatColor.RED, "🔥"),
        ICE_DAMAGE("ICE DMG", ChatColor.AQUA, "❄"),
        POISON_DAMAGE("POISON DMG", ChatColor.DARK_GREEN, "☠"),
        PURE_DAMAGE("PURE DMG", ChatColor.WHITE, "✨");

        private final String displayName;
        private final ChatColor color;
        private final String symbol;

        EssenceType(String displayName, ChatColor color, String symbol) {
            this.displayName = displayName;
            this.color = color;
            this.symbol = symbol;
        }

        public String getDisplayName() { return displayName; }
        public ChatColor getColor() { return color; }
        public String getSymbol() { return symbol; }
    }

    private EssenceCrystalSystem() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys
        this.keyEssenceType = new NamespacedKey(plugin, "essence_type");
        this.keyEssenceValue = new NamespacedKey(plugin, "essence_value");
        this.keyEssenceQuality = new NamespacedKey(plugin, "essence_quality");
        this.keyEssenceTier = new NamespacedKey(plugin, "essence_tier");
        this.keyExtractorType = new NamespacedKey(plugin, "extractor_type");
        this.keyInfuserType = new NamespacedKey(plugin, "infuser_type");
    }

    public static EssenceCrystalSystem getInstance() {
        if (instance == null) {
            instance = new EssenceCrystalSystem();
        }
        return instance;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        logger.info("[EssenceCrystalSystem] Essence Crystal System initialized");
    }

    /**
     * Creates an Essence Extractor tool
     */
    public ItemStack createEssenceExtractor() {
        ItemStack extractor = new ItemStack(Material.GHAST_TEAR);
        ItemMeta meta = extractor.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "⚗ " + ChatColor.BOLD + "Essence Extractor" + ChatColor.RESET + " ⚗");

        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY + "Extracts one random stat from an item",
                ChatColor.GRAY + "into an Essence Crystal.",
                "",
                ChatColor.RED + "⚠ The item loses that stat permanently!",
                "",
                ChatColor.YELLOW + "Usage: Click on any enhanced item",
                ChatColor.AQUA + "Price: " + ChatColor.WHITE + "1,500g",
                "",
                ChatColor.GOLD + "✨ " + ChatColor.ITALIC + "Channel the essence within..."
        );
        meta.setLore(lore);

        // Store extractor type in persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyExtractorType, PersistentDataType.STRING, "essence_extractor");

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        extractor.setItemMeta(meta);

        return extractor;
    }

    /**
     * Creates an Essence Infuser tool
     */
    public ItemStack createEssenceInfuser() {
        ItemStack infuser = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = infuser.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "🔥 " + ChatColor.BOLD + "Essence Infuser" + ChatColor.RESET + " 🔥");

        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY + "Applies an Essence Crystal to an item.",
                "",
                ChatColor.GREEN + "✓ 25% chance to upgrade crystal quality",
                ChatColor.RED + "✗ 10% chance crystal is destroyed",
                ChatColor.DARK_RED + "✗ 5% chance item gains a curse",
                "",
                ChatColor.YELLOW + "Usage: Click crystal on item, then use this",
                ChatColor.AQUA + "Price: " + ChatColor.WHITE + "750g",
                "",
                ChatColor.GOLD + "🔥 " + ChatColor.ITALIC + "Fuse essence with matter..."
        );
        meta.setLore(lore);

        // Store infuser type in persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyInfuserType, PersistentDataType.STRING, "essence_infuser");

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        infuser.setItemMeta(meta);

        return infuser;
    }

    /**
     * Creates an Essence Crystal with specific properties
     */
    public ItemStack createEssenceCrystal(EssenceType type, int value, CrystalQuality quality, int tier) {
        ItemStack crystal = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = crystal.getItemMeta();

        String qualityPrefix = quality.getColor() + quality.getSymbol() + " " + quality.name().toLowerCase();
        meta.setDisplayName(qualityPrefix + " " + type.getColor() + type.getDisplayName() + " Crystal");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(type.getColor() + type.getSymbol() + " " + type.getDisplayName() + ": +" + value);
        lore.add("");
        lore.add(ChatColor.GRAY + "Quality: " + quality.getColor() + quality.name().toLowerCase());
        lore.add(ChatColor.GRAY + "Tier Compatibility: " + getTierColorCode(tier) + "T" + tier + " and below");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Apply with an Essence Infuser");
        lore.add(ChatColor.DARK_GRAY.toString() + ChatColor.ITALIC + "Crystallized essence of power...");

        meta.setLore(lore);

        // Store crystal data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyEssenceType, PersistentDataType.STRING, type.name());
        container.set(keyEssenceValue, PersistentDataType.INTEGER, value);
        container.set(keyEssenceQuality, PersistentDataType.STRING, quality.name());
        container.set(keyEssenceTier, PersistentDataType.INTEGER, tier);

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        crystal.setItemMeta(meta);

        // Add glow effect for perfect crystals
        if (quality == CrystalQuality.PERFECT) {
            Enchants.addGlow(crystal);
        }

        return crystal;
    }

    /**
     * Main event handler for essence crystal interactions
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerUuid = player.getUniqueId();

        // Prevent double processing
        if (processingPlayers.contains(playerUuid)) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (cursor == null || currentItem == null ||
                cursor.getType() == Material.AIR || currentItem.getType() == Material.AIR) {
            return;
        }

        // Handle Essence Extractor usage
        if (isEssenceExtractor(cursor) && isValidItemForExtraction(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processEssenceExtraction(player, event, cursor, currentItem);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }

        // Handle Essence Crystal application (stage 1 - mark item for infusion)
        else if (isEssenceCrystal(cursor) && isValidItemForInfusion(currentItem)) {
            event.setCancelled(true);
            markItemForInfusion(player, currentItem, cursor, event);
        }

        // Handle Essence Infuser usage (stage 2 - complete the infusion)
        else if (isEssenceInfuser(cursor) && isMarkedForInfusion(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processEssenceInfusion(player, event, cursor, currentItem);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }
    }

    /**
     * Processes essence extraction from an item
     */
    private void processEssenceExtraction(Player player, InventoryClickEvent event, ItemStack extractor, ItemStack targetItem) {
        // Get all extractable stats from the item
        List<ExtractableEssence> extractableStats = getExtractableStats(targetItem);

        if (extractableStats.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This item has no extractable stats!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Randomly select a stat to extract
        ExtractableEssence selectedEssence = extractableStats.get(ThreadLocalRandom.current().nextInt(extractableStats.size()));

        // Determine crystal quality
        CrystalQuality quality = determineCrystalQuality();

        // Get item tier for compatibility
        int itemTier = getItemTier(targetItem);

        // Create the essence crystal
        ItemStack crystal = createEssenceCrystal(selectedEssence.type, selectedEssence.value, quality, itemTier);

        // Remove the stat from the original item
        ItemStack modifiedItem = removeStatFromItem(targetItem, selectedEssence);

        // Consume the extractor
        if (extractor.getAmount() > 1) {
            extractor.setAmount(extractor.getAmount() - 1);
            event.setCursor(extractor);
        } else {
            event.setCursor(null);
        }

        // Update the target item
        event.setCurrentItem(modifiedItem);

        // Give the crystal to the player
        giveItemToPlayer(player, crystal);

        // Success feedback
        player.sendMessage(ChatColor.GREEN + "✓ Extracted " + selectedEssence.type.getColor() +
                selectedEssence.type.getDisplayName() + ChatColor.GREEN + " essence!");
        player.sendMessage(ChatColor.YELLOW + "Crystal Quality: " + quality.getColor() + quality.name().toLowerCase());

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        showExtractionEffect(player);
    }

    /**
     * Marks an item for essence infusion
     */
    private void markItemForInfusion(Player player, ItemStack targetItem, ItemStack crystal, InventoryClickEvent event) {
        // Get crystal data
        EssenceCrystal crystalData = getEssenceCrystalData(crystal);
        if (crystalData == null) return;

        // Check tier compatibility
        int itemTier = getItemTier(targetItem);
        if (itemTier > crystalData.tier) {
            player.sendMessage(ChatColor.RED + "This crystal is not compatible with this item's tier!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Mark the item with pending infusion data
        ItemStack markedItem = targetItem.clone();
        ItemMeta meta = markedItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(new NamespacedKey(plugin, "pending_essence_type"), PersistentDataType.STRING, crystalData.type.name());
        container.set(new NamespacedKey(plugin, "pending_essence_value"), PersistentDataType.INTEGER, crystalData.value);
        container.set(new NamespacedKey(plugin, "pending_essence_quality"), PersistentDataType.STRING, crystalData.quality.name());

        markedItem.setItemMeta(meta);

        // Store the crystal for consumption later
        player.getInventory().setItem(event.getSlot(), markedItem);

        player.sendMessage(ChatColor.YELLOW + "Item marked for infusion. Use an Essence Infuser to complete the process!");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /**
     * Processes essence infusion into an item
     */
    private void processEssenceInfusion(Player player, InventoryClickEvent event, ItemStack infuser, ItemStack targetItem) {
        // Get pending infusion data
        ItemMeta meta = targetItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String essenceTypeName = container.get(new NamespacedKey(plugin, "pending_essence_type"), PersistentDataType.STRING);
        Integer essenceValue = container.get(new NamespacedKey(plugin, "pending_essence_value"), PersistentDataType.INTEGER);
        String qualityName = container.get(new NamespacedKey(plugin, "pending_essence_quality"), PersistentDataType.STRING);

        if (essenceTypeName == null || essenceValue == null || qualityName == null) {
            player.sendMessage(ChatColor.RED + "No pending essence infusion found on this item!");
            return;
        }

        EssenceType essenceType = EssenceType.valueOf(essenceTypeName);
        CrystalQuality quality = CrystalQuality.valueOf(qualityName);

        // Roll for infusion outcomes
        int roll = ThreadLocalRandom.current().nextInt(100);

        if (roll < 5) { // 5% chance item gains curse
            handleInfusionCurse(player, event, targetItem);
            return;
        } else if (roll < 15) { // 10% chance crystal is destroyed
            handleCrystalDestruction(player, event, targetItem);
            return;
        }

        // Check for quality upgrade (25% chance)
        if (roll < 40 && quality != CrystalQuality.PERFECT) {
            quality = upgradeQuality(quality);
            player.sendMessage(ChatColor.YELLOW + "✨ Crystal quality upgraded to " + quality.getColor() + quality.name().toLowerCase() + "!");
        }

        // Apply the essence to the item
        int finalValue = (int) (essenceValue * quality.getMultiplier());
        ItemStack infusedItem = addStatToItem(targetItem, essenceType, finalValue);

        // Clear pending infusion data
        ItemMeta infusedMeta = infusedItem.getItemMeta();
        PersistentDataContainer infusedContainer = infusedMeta.getPersistentDataContainer();
        infusedContainer.remove(new NamespacedKey(plugin, "pending_essence_type"));
        infusedContainer.remove(new NamespacedKey(plugin, "pending_essence_value"));
        infusedContainer.remove(new NamespacedKey(plugin, "pending_essence_quality"));
        infusedItem.setItemMeta(infusedMeta);

        // Consume the infuser
        if (infuser.getAmount() > 1) {
            infuser.setAmount(infuser.getAmount() - 1);
            event.setCursor(infuser);
        } else {
            event.setCursor(null);
        }

        // Update the item
        event.setCurrentItem(infusedItem);

        // Success feedback
        player.sendMessage(ChatColor.GREEN + "✓ Successfully infused " + essenceType.getColor() +
                essenceType.getDisplayName() + " +" + finalValue + ChatColor.GREEN + "!");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
        showInfusionEffect(player);
    }

    /**
     * Gets all extractable stats from an item
     */
    private List<ExtractableEssence> getExtractableStats(ItemStack item) {
        List<ExtractableEssence> extractable = new ArrayList<>();

        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return extractable;
        }

        List<String> lore = item.getItemMeta().getLore();

        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line);

            // Check for damage
            Matcher damageMatcher = DAMAGE_PATTERN.matcher(cleanLine);
            if (damageMatcher.find()) {
                int avgDamage = (Integer.parseInt(damageMatcher.group(1)) + Integer.parseInt(damageMatcher.group(2))) / 2;
                extractable.add(new ExtractableEssence(EssenceType.DAMAGE, avgDamage, line));
            }

            // Check for HP
            Matcher hpMatcher = HP_PATTERN.matcher(cleanLine);
            if (hpMatcher.find()) {
                extractable.add(new ExtractableEssence(EssenceType.HP, Integer.parseInt(hpMatcher.group(1)), line));
            }

            // Check for armor/DPS
            Matcher armorMatcher = ARMOR_PATTERN.matcher(cleanLine);
            if (armorMatcher.find()) {
                int avgArmor = (Integer.parseInt(armorMatcher.group(1)) + Integer.parseInt(armorMatcher.group(2))) / 2;
                extractable.add(new ExtractableEssence(EssenceType.ARMOR, avgArmor, line));
            }

            Matcher dpsMatcher = DPS_PATTERN.matcher(cleanLine);
            if (dpsMatcher.find()) {
                int avgDps = (Integer.parseInt(dpsMatcher.group(1)) + Integer.parseInt(dpsMatcher.group(2))) / 2;
                extractable.add(new ExtractableEssence(EssenceType.DPS, avgDps, line));
            }

            // Check for elemental damage
            Matcher elementalMatcher = ELEMENTAL_PATTERN.matcher(cleanLine);
            if (elementalMatcher.find()) {
                String elementType = elementalMatcher.group(1);
                int value = Integer.parseInt(elementalMatcher.group(2));

                EssenceType type = switch (elementType) {
                    case "FIRE DMG" -> EssenceType.FIRE_DAMAGE;
                    case "ICE DMG" -> EssenceType.ICE_DAMAGE;
                    case "POISON DMG" -> EssenceType.POISON_DAMAGE;
                    case "PURE DMG" -> EssenceType.PURE_DAMAGE;
                    default -> null;
                };

                if (type != null) {
                    extractable.add(new ExtractableEssence(type, value, line));
                }
            }

            // Check for percentage stats
            Matcher percentMatcher = PERCENT_STAT_PATTERN.matcher(cleanLine);
            if (percentMatcher.find()) {
                String statName = percentMatcher.group(1);
                int value = Integer.parseInt(percentMatcher.group(2));

                EssenceType type = switch (statName.toUpperCase()) {
                    case "CRITICAL HIT" -> EssenceType.CRITICAL_HIT;
                    case "LIFE STEAL" -> EssenceType.LIFE_STEAL;
                    case "ACCURACY" -> EssenceType.ACCURACY;
                    case "DODGE" -> EssenceType.DODGE;
                    case "BLOCK" -> EssenceType.BLOCK;
                    case "ENERGY REGEN" -> EssenceType.ENERGY_REGEN;
                    default -> null;
                };

                if (type != null) {
                    extractable.add(new ExtractableEssence(type, value, line));
                }
            }

            // Check for flat stats
            Matcher statMatcher = STAT_PATTERN.matcher(cleanLine);
            if (statMatcher.find()) {
                String statName = statMatcher.group(1);
                int value = Integer.parseInt(statMatcher.group(2));

                EssenceType type = switch (statName.toUpperCase()) {
                    case "STR" -> EssenceType.STRENGTH;
                    case "INT" -> EssenceType.INTELLECT;
                    case "VIT" -> EssenceType.VITALITY;
                    case "DEX" -> EssenceType.DEXTERITY;
                    default -> null;
                };

                if (type != null) {
                    extractable.add(new ExtractableEssence(type, value, line));
                }
            }
        }

        return extractable;
    }

    /**
     * Helper class to store extractable essence data
     */
    private static class ExtractableEssence {
        final EssenceType type;
        final int value;
        final String originalLoreLine;

        ExtractableEssence(EssenceType type, int value, String originalLoreLine) {
            this.type = type;
            this.value = value;
            this.originalLoreLine = originalLoreLine;
        }
    }

    /**
     * Helper class to store essence crystal data
     */
    private static class EssenceCrystal {
        final EssenceType type;
        final int value;
        final CrystalQuality quality;
        final int tier;

        EssenceCrystal(EssenceType type, int value, CrystalQuality quality, int tier) {
            this.type = type;
            this.value = value;
            this.quality = quality;
            this.tier = tier;
        }
    }

    // Helper methods for validation and utility
    private boolean isEssenceExtractor(ItemStack item) {
        return item.getType() == Material.GHAST_TEAR &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(keyExtractorType, PersistentDataType.STRING);
    }

    private boolean isEssenceInfuser(ItemStack item) {
        return item.getType() == Material.BLAZE_POWDER &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(keyInfuserType, PersistentDataType.STRING);
    }

    private boolean isEssenceCrystal(ItemStack item) {
        return item.getType() == Material.PRISMARINE_CRYSTALS &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(keyEssenceType, PersistentDataType.STRING);
    }

    private boolean isValidItemForExtraction(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasLore() &&
                !getExtractableStats(item).isEmpty();
    }

    private boolean isValidItemForInfusion(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasLore();
    }

    private boolean isMarkedForInfusion(ItemStack item) {
        return item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(new NamespacedKey(plugin, "pending_essence_type"), PersistentDataType.STRING);
    }

    private CrystalQuality determineCrystalQuality() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 15) return CrystalQuality.PERFECT;  // 15%
        if (roll < 50) return CrystalQuality.NORMAL;   // 35%
        return CrystalQuality.FLAWED;                  // 50%
    }

    private CrystalQuality upgradeQuality(CrystalQuality current) {
        return switch (current) {
            case FLAWED -> CrystalQuality.NORMAL;
            case NORMAL -> CrystalQuality.PERFECT;
            case PERFECT -> CrystalQuality.PERFECT;
        };
    }

    private int getItemTier(ItemStack item) {
        if (item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey tierKey = new NamespacedKey(plugin, "item_tier");
            if (container.has(tierKey, PersistentDataType.INTEGER)) {
                return container.get(tierKey, PersistentDataType.INTEGER);
            }
        }

        // Fallback: try to determine from material or enhancement level
        String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName() ?
                item.getItemMeta().getDisplayName() : "";

        if (itemName.contains("Frozen") || itemName.contains("Blue")) return 6;
        if (itemName.contains("Legendary") || itemName.contains("Yellow")) return 5;
        if (itemName.contains("Ancient") || itemName.contains("Purple")) return 4;
        if (itemName.contains("Magic") || itemName.contains("Aqua")) return 3;
        if (itemName.contains("Green")) return 2;

        return 1; // Default
    }

    private ChatColor getTierColorCode(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.BLUE;
            default -> ChatColor.GRAY;
        };
    }

    private EssenceCrystal getEssenceCrystalData(ItemStack crystal) {
        if (!isEssenceCrystal(crystal)) return null;

        ItemMeta meta = crystal.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        try {
            EssenceType type = EssenceType.valueOf(container.get(keyEssenceType, PersistentDataType.STRING));
            int value = container.get(keyEssenceValue, PersistentDataType.INTEGER);
            CrystalQuality quality = CrystalQuality.valueOf(container.get(keyEssenceQuality, PersistentDataType.STRING));
            int tier = container.get(keyEssenceTier, PersistentDataType.INTEGER);

            return new EssenceCrystal(type, value, quality, tier);
        } catch (Exception e) {
            return null;
        }
    }

    private ItemStack removeStatFromItem(ItemStack item, ExtractableEssence essence) {
        ItemStack modifiedItem = item.clone();
        ItemMeta meta = modifiedItem.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());

        // Remove the specific stat line
        lore.removeIf(line -> line.equals(essence.originalLoreLine));

        meta.setLore(lore);
        modifiedItem.setItemMeta(meta);

        return modifiedItem;
    }

    private ItemStack addStatToItem(ItemStack item, EssenceType essenceType, int value) {
        ItemStack modifiedItem = item.clone();
        ItemMeta meta = modifiedItem.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());

        // Create the new stat line
        String newStatLine = essenceType.getColor() + essenceType.getDisplayName() + ": ";

        if (essenceType.getDisplayName().contains("%")) {
            newStatLine += value + "%";
        } else {
            newStatLine += "+" + value;
        }

        // Find the best position to insert the stat (before rarity line)
        int insertPosition = lore.size() - 1; // Before rarity
        for (int i = 0; i < lore.size(); i++) {
            String line = ChatColor.stripColor(lore.get(i));
            if (line.toLowerCase().contains("common") || line.toLowerCase().contains("uncommon") ||
                    line.toLowerCase().contains("rare") || line.toLowerCase().contains("unique") ||
                    line.toLowerCase().contains("legendary")) {
                insertPosition = i;
                break;
            }
        }

        lore.add(insertPosition, newStatLine);
        meta.setLore(lore);
        modifiedItem.setItemMeta(meta);

        return modifiedItem;
    }

    private void handleInfusionCurse(Player player, InventoryClickEvent event, ItemStack item) {
        // Add a random curse to the item
        ItemStack cursedItem = addRandomCurse(item);

        // Clear pending infusion
        ItemMeta meta = cursedItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(new NamespacedKey(plugin, "pending_essence_type"));
        container.remove(new NamespacedKey(plugin, "pending_essence_value"));
        container.remove(new NamespacedKey(plugin, "pending_essence_quality"));
        cursedItem.setItemMeta(meta);

        event.setCurrentItem(cursedItem);

        player.sendMessage(ChatColor.DARK_RED + "💀 The infusion backfired! Your item has been cursed!");
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        showCurseEffect(player);
    }

    private void handleCrystalDestruction(Player player, InventoryClickEvent event, ItemStack item) {
        // Clear pending infusion
        ItemStack clearedItem = item.clone();
        ItemMeta meta = clearedItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(new NamespacedKey(plugin, "pending_essence_type"));
        container.remove(new NamespacedKey(plugin, "pending_essence_value"));
        container.remove(new NamespacedKey(plugin, "pending_essence_quality"));
        clearedItem.setItemMeta(meta);

        event.setCurrentItem(clearedItem);

        player.sendMessage(ChatColor.RED + "💥 The essence crystal shattered during infusion!");
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        showFailureEffect(player);
    }

    private ItemStack addRandomCurse(ItemStack item) {
        String[] curses = {
                ChatColor.DARK_RED + "CURSED: -10% damage taken heals attackers",
                ChatColor.DARK_RED + "CURSED: -5% chance to drop item on death",
                ChatColor.DARK_RED + "CURSED: Item occasionally refuses to work",
                ChatColor.DARK_RED + "CURSED: -20% durability decay rate"
        };

        ItemStack cursedItem = item.clone();
        ItemMeta meta = cursedItem.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());

        String curse = curses[ThreadLocalRandom.current().nextInt(curses.length)];
        lore.add(lore.size() - 1, curse); // Add before rarity line

        meta.setLore(lore);
        cursedItem.setItemMeta(meta);

        return cursedItem;
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItem(player.getLocation(), item);
            player.sendMessage(ChatColor.YELLOW + "Your inventory is full! The crystal was dropped on the ground.");
        }
    }

    // Visual effects
    private void showExtractionEffect(Player player) {
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.STAR, Color.PURPLE);
    }

    private void showInfusionEffect(Player player) {
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.YELLOW);
    }

    private void showCurseEffect(Player player) {
        player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
        ParticleUtil.showFailureEffect(player.getLocation());
    }

    private void showFailureEffect(Player player) {
        ParticleUtil.showFailureEffect(player.getLocation());
    }
}
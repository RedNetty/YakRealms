package com.rednetty.server.mechanics.item.forge;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.utils.particles.FireworkUtil;
import com.rednetty.server.utils.particles.ParticleUtil;
import com.rednetty.server.utils.text.TextUtil;
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
 * Forge Hammer System - Precision crafting for selective stat rerolling
 * Allows players to reroll specific stats instead of full item randomization
 */
public class ForgeHammerSystem implements Listener {
    private static ForgeHammerSystem instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Namespaced keys for persistent data
    private final NamespacedKey keyHammerType;
    private final NamespacedKey keySelectedStat;
    private final NamespacedKey keyPreviewOptions;
    private final NamespacedKey keyMasterworkStatus;

    // Processing tracking
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Map<UUID, HammerPreview> pendingPreviews = new HashMap<>();

    // Hammer types
    public enum HammerType {
        APPRENTICE("Apprentice Hammer", Material.IRON_INGOT, ChatColor.GRAY, 800,
                "Rerolls the LAST stat line on an item", 6, false),
        MASTER("Master Hammer", Material.DIAMOND, ChatColor.AQUA, 2500,
                "Choose which stat line to reroll", 12, true),
        LEGENDARY("Legendary Forge Hammer", Material.NETHERITE_INGOT, ChatColor.GOLD, 15000,
                "Reroll with guaranteed improvement", 15, true);

        private final String displayName;
        private final Material material;
        private final ChatColor color;
        private final int price;
        private final String description;
        private final int safeLevel;
        private final boolean allowsSelection;

        HammerType(String displayName, Material material, ChatColor color, int price,
                   String description, int safeLevel, boolean allowsSelection) {
            this.displayName = displayName;
            this.material = material;
            this.color = color;
            this.price = price;
            this.description = description;
            this.safeLevel = safeLevel;
            this.allowsSelection = allowsSelection;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public ChatColor getColor() { return color; }
        public int getPrice() { return price; }
        public String getDescription() { return description; }
        public int getSafeLevel() { return safeLevel; }
        public boolean allowsSelection() { return allowsSelection; }
    }

    // Stat types that can be rerolled
    public enum StatType {
        DAMAGE("DMG", "⚔", true),
        HP("HP", "❤", false),
        ARMOR("ARMOR", "🛡", true),
        DPS("DPS", "⚡", true),
        STRENGTH("STR", "💪", false),
        INTELLECT("INT", "🧠", false),
        VITALITY("VIT", "🌿", false),
        DEXTERITY("DEX", "🏃", false),
        CRITICAL_HIT("CRITICAL HIT", "💥", false),
        LIFE_STEAL("LIFE STEAL", "🩸", false),
        ACCURACY("ACCURACY", "🎯", false),
        DODGE("DODGE", "💨", false),
        BLOCK("BLOCK", "🛡", false),
        ENERGY_REGEN("ENERGY REGEN", "⚡", false),
        HP_REGEN("HP REGEN", "💚", false),
        FIRE_DAMAGE("FIRE DMG", "🔥", false),
        ICE_DAMAGE("ICE DMG", "❄", false),
        POISON_DAMAGE("POISON DMG", "☠", false),
        PURE_DAMAGE("PURE DMG", "✨", false);

        private final String displayName;
        private final String symbol;
        private final boolean isRange;

        StatType(String displayName, String symbol, boolean isRange) {
            this.displayName = displayName;
            this.symbol = symbol;
            this.isRange = isRange;
        }

        public String getDisplayName() { return displayName; }
        public String getSymbol() { return symbol; }
        public boolean isRange() { return isRange; }
    }

    // Preview options for Master Hammer
    private static class HammerPreview {
        final List<StatRerollOption> options;
        final long expiryTime;

        HammerPreview(List<StatRerollOption> options) {
            this.options = options;
            this.expiryTime = System.currentTimeMillis() + 30000; // 30 seconds
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private static class StatRerollOption {
        final String originalLine;
        final String newLine;
        final StatType statType;
        final int lineIndex;

        StatRerollOption(String originalLine, String newLine, StatType statType, int lineIndex) {
            this.originalLine = originalLine;
            this.newLine = newLine;
            this.statType = statType;
            this.lineIndex = lineIndex;
        }
    }

    private static class RerollableStatLine {
        final String loreLine;
        final StatType statType;
        final int lineIndex;
        final String baseValue;

        RerollableStatLine(String loreLine, StatType statType, int lineIndex, String baseValue) {
            this.loreLine = loreLine;
            this.statType = statType;
            this.lineIndex = lineIndex;
            this.baseValue = baseValue;
        }
    }

    private ForgeHammerSystem() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys
        this.keyHammerType = new NamespacedKey(plugin, "hammer_type");
        this.keySelectedStat = new NamespacedKey(plugin, "selected_stat");
        this.keyPreviewOptions = new NamespacedKey(plugin, "preview_options");
        this.keyMasterworkStatus = new NamespacedKey(plugin, "masterwork_status");
    }

    public static ForgeHammerSystem getInstance() {
        if (instance == null) {
            instance = new ForgeHammerSystem();
        }
        return instance;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Start cleanup task for expired previews
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredPreviews, 600L, 600L);

        logger.info("[ForgeHammerSystem] Forge Hammer System initialized");
    }

    /**
     * Creates an Apprentice Hammer
     */
    public ItemStack createApprenticeHammer() {
        return createHammer(HammerType.APPRENTICE);
    }

    /**
     * Creates a Master Hammer
     */
    public ItemStack createMasterHammer() {
        return createHammer(HammerType.MASTER);
    }

    /**
     * Creates a Legendary Forge Hammer
     */
    public ItemStack createLegendaryHammer() {
        return createHammer(HammerType.LEGENDARY);
    }

    /**
     * Creates a hammer of the specified type
     */
    private ItemStack createHammer(HammerType type) {
        ItemStack hammer = new ItemStack(type.getMaterial());
        ItemMeta meta = hammer.getItemMeta();

        meta.setDisplayName(type.getColor() + "🔨 " + ChatColor.BOLD + type.getDisplayName() + ChatColor.RESET);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + type.getDescription());

        switch (type) {
            case APPRENTICE:
                lore.add("");
                lore.add(ChatColor.GREEN + "✓ Safe up to +6 enhancement");
                lore.add(ChatColor.RED + "✗ Risky beyond +6");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Usage: Click on any  item");
                break;

            case MASTER:
                lore.add("");
                lore.add(ChatColor.GREEN + "✓ Choose which stat to reroll");
                lore.add(ChatColor.GREEN + "✓ Preview 3 possible outcomes");
                lore.add(ChatColor.RED + "✗ 15% chance to reroll ALL stats");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Usage: Click on item, select stat, choose outcome");
                break;

            case LEGENDARY:
                lore.add("");
                lore.add(ChatColor.GREEN + "✓ Guaranteed improvement");
                lore.add(ChatColor.GREEN + "✓ Can add new stat lines");
                lore.add(ChatColor.GOLD + "✓ 5% chance for Masterwork status");
                lore.add("");
                lore.add(ChatColor.YELLOW + "Usage: Click on any item");
                break;
        }

        lore.add(ChatColor.AQUA + "Price: " + ChatColor.WHITE + TextUtil.formatNumber(type.getPrice()) + "g");
        lore.add("");
        lore.add(type.getColor() + "🔨 " + ChatColor.ITALIC + "Precision crafting awaits...");

        meta.setLore(lore);

        // Store hammer type in persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyHammerType, PersistentDataType.STRING, type.name());

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        hammer.setItemMeta(meta);

        // Add glow effect
        Enchants.addGlow(hammer);

        return hammer;
    }

    /**
     * Main event handler for forge hammer interactions
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

        // Handle hammer usage
        HammerType hammerType = getHammerType(cursor);
        if (hammerType != null && isValidItemForReroll(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processHammerUsage(player, event, cursor, currentItem, hammerType);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }
    }

    /**
     * Processes the usage of a forge hammer on an item
     */
    private void processHammerUsage(Player player, InventoryClickEvent event, ItemStack hammer,
                                    ItemStack targetItem, HammerType hammerType) {

        // Get enhancement level for safety checks
        int enhancementLevel = getEnhancementLevel(targetItem);

        // Check if operation is safe
        boolean isSafe = enhancementLevel <= hammerType.getSafeLevel();

        switch (hammerType) {
            case APPRENTICE:
                processApprenticeHammer(player, event, hammer, targetItem, isSafe);
                break;
            case MASTER:
                processMasterHammer(player, event, hammer, targetItem, isSafe);
                break;
            case LEGENDARY:
                processLegendaryHammer(player, event, hammer, targetItem);
                break;
        }
    }

    /**
     * Processes Apprentice Hammer usage (rerolls last stat line)
     */
    private void processApprenticeHammer(Player player, InventoryClickEvent event, ItemStack hammer,
                                         ItemStack targetItem, boolean isSafe) {

        List<RerollableStatLine> rerollableStats = getRerollableStats(targetItem);
        if (rerollableStats.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This item has no rerollable stats!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Risk check for non-safe enhancements
        if (!isSafe && ThreadLocalRandom.current().nextInt(100) < 30) { // 30% failure chance
            handleHammerFailure(player, event, hammer, targetItem);
            return;
        }

        // Get the last stat line
        RerollableStatLine lastStat = rerollableStats.get(rerollableStats.size() - 1);

        // Generate new value for the stat
        String newStatLine = generateNewStatLine(lastStat.statType, getItemTier(targetItem), false);

        // Apply the reroll
        ItemStack rerolledItem = applySingleStatReroll(targetItem, lastStat, newStatLine);

        // Consume the hammer
        consumeItem(event, hammer);

        // Update the item
        event.setCurrentItem(rerolledItem);

        // Success feedback
        player.sendMessage(ChatColor.GREEN + "🔨 Successfully rerolled " +
                lastStat.statType.getSymbol() + " " + lastStat.statType.getDisplayName() + "!");

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        showHammerEffect(player, false);
    }

    /**
     * Processes Master Hammer usage (choose stat, preview options)
     */
    private void processMasterHammer(Player player, InventoryClickEvent event, ItemStack hammer,
                                     ItemStack targetItem, boolean isSafe) {

        // Check for catastrophic failure (15% chance to reroll ALL stats)
        if (ThreadLocalRandom.current().nextInt(100) < 15) {
            handleCatastrophicReroll(player, event, hammer, targetItem);
            return;
        }

        // Check if player already has a preview pending
        if (pendingPreviews.containsKey(player.getUniqueId())) {
            HammerPreview preview = pendingPreviews.get(player.getUniqueId());
            if (!preview.isExpired()) {
                showPreviewSelectionGUI(player, preview);
                return;
            } else {
                pendingPreviews.remove(player.getUniqueId());
            }
        }

        // Generate preview options
        List<RerollableStatLine> rerollableStats = getRerollableStats(targetItem);
        if (rerollableStats.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This item has no rerollable stats!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Show stat selection GUI
        showStatSelectionGUI(player, rerollableStats, targetItem);
    }

    /**
     * Processes Legendary Hammer usage (guaranteed improvement)
     */
    private void processLegendaryHammer(Player player, InventoryClickEvent event, ItemStack hammer,
                                        ItemStack targetItem) {

        // Check for Masterwork upgrade (5% chance)
        boolean masterworkUpgrade = ThreadLocalRandom.current().nextInt(100) < 5;

        List<RerollableStatLine> rerollableStats = getRerollableStats(targetItem);
        ItemStack improvedItem = targetItem.clone();

        // Reroll one random stat with guaranteed improvement
        if (!rerollableStats.isEmpty()) {
            RerollableStatLine selectedStat = rerollableStats.get(ThreadLocalRandom.current().nextInt(rerollableStats.size()));
            String improvedStatLine = generateNewStatLine(selectedStat.statType, getItemTier(targetItem), true);
            improvedItem = applySingleStatReroll(improvedItem, selectedStat, improvedStatLine);
        }

        // Chance to add new stat line if item has room
        if (getStatLineCount(improvedItem) < getMaxStatLines(improvedItem) && ThreadLocalRandom.current().nextBoolean()) {
            improvedItem = addRandomStatLine(improvedItem);
        }

        // Apply Masterwork status if triggered
        if (masterworkUpgrade) {
            improvedItem = applyMasterworkStatus(improvedItem);
            player.sendMessage(ChatColor.GOLD + "✧ MASTERWORK CREATION! ✧");
            player.sendMessage(ChatColor.YELLOW + "Your item has gained permanent stat bonuses!");
        }

        // Consume the hammer
        consumeItem(event, hammer);

        // Update the item
        event.setCurrentItem(improvedItem);

        // Success feedback
        player.sendMessage(ChatColor.GOLD + "🔨 Legendary crafting complete!");
        if (masterworkUpgrade) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
            showMasterworkEffect(player);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 0.8f);
            showHammerEffect(player, true);
        }
    }

    /**
     * Gets all rerollable stat lines from an item
     */
    private List<RerollableStatLine> getRerollableStats(ItemStack item) {
        List<RerollableStatLine> rerollable = new ArrayList<>();

        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return rerollable;
        }

        List<String> lore = item.getItemMeta().getLore();

        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            String cleanLine = ChatColor.stripColor(line);

            // Skip empty lines and rarity lines
            if (cleanLine.trim().isEmpty() ||
                    cleanLine.toLowerCase().contains("common") ||
                    cleanLine.toLowerCase().contains("uncommon") ||
                    cleanLine.toLowerCase().contains("rare") ||
                    cleanLine.toLowerCase().contains("unique") ||
                    cleanLine.toLowerCase().contains("legendary")) {
                continue;
            }

            // Check for each stat type
            for (StatType statType : StatType.values()) {
                if (cleanLine.contains(statType.getDisplayName() + ":")) {
                    // Extract the current value
                    String baseValue = extractStatValue(cleanLine, statType);
                    rerollable.add(new RerollableStatLine(line, statType, i, baseValue));
                    break;
                }
            }
        }

        return rerollable;
    }

    /**
     * Generates a new stat line with optional improvement guarantee
     */
    private String generateNewStatLine(StatType statType, int itemTier, boolean guaranteeImprovement) {
        ChatColor color = ChatColor.RED; // Default stat color

        // Generate values based on stat type and tier
        int baseValue = itemTier * 10;
        int newValue;

        if (statType.isRange()) {
            // For range stats like damage, armor
            int min = baseValue;
            int max = (int) (baseValue * 1.5);

            if (guaranteeImprovement) {
                min = (int) (min * 1.2); // Increase minimum for improvement
                max = (int) (max * 1.2);
            }

            int rollMin = ThreadLocalRandom.current().nextInt(min / 2, min + 1);
            int rollMax = ThreadLocalRandom.current().nextInt(max, max + max / 2);

            return color + statType.getDisplayName() + ": " + rollMin + " - " + rollMax +
                    (statType.getDisplayName().contains("ARMOR") || statType.getDisplayName().contains("DPS") ? "%" : "");
        } else {
            // For flat stats
            if (statType.getDisplayName().contains("%")) {
                newValue = ThreadLocalRandom.current().nextInt(5, 15 + itemTier * 2);
                if (guaranteeImprovement) newValue = (int) (newValue * 1.3);
                return color + statType.getDisplayName() + ": " + newValue + "%";
            } else {
                newValue = ThreadLocalRandom.current().nextInt(baseValue / 2, baseValue * 2);
                if (guaranteeImprovement) newValue = (int) (newValue * 1.3);
                return color + statType.getDisplayName() + ": +" + newValue;
            }
        }
    }

    /**
     * Applies a single stat reroll to an item
     */
    private ItemStack applySingleStatReroll(ItemStack item, RerollableStatLine oldStat, String newStatLine) {
        ItemStack rerolledItem = item.clone();
        ItemMeta meta = rerolledItem.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());

        // Replace the old stat line with the new one
        lore.set(oldStat.lineIndex, newStatLine);

        meta.setLore(lore);
        rerolledItem.setItemMeta(meta);

        return rerolledItem;
    }

    /**
     * Applies Masterwork status to an item
     */
    private ItemStack applyMasterworkStatus(ItemStack item) {
        ItemStack masterworkItem = item.clone();
        ItemMeta meta = masterworkItem.getItemMeta();

        // Add Masterwork to the name
        String currentName = meta.getDisplayName();
        if (!currentName.contains("Masterwork")) {
            meta.setDisplayName(ChatColor.GOLD + "[Masterwork] " + currentName);
        }

        // Add all stats +1 bonus
        List<String> lore = new ArrayList<>(meta.getLore());
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            String cleanLine = ChatColor.stripColor(line);

            // Boost numerical stats
            if (cleanLine.contains(": +") || cleanLine.contains(": ")) {
                String boostedLine = boostStatLine(line);
                lore.set(i, boostedLine);
            }
        }

        // Add masterwork indicator
        lore.add(lore.size() - 1, ChatColor.GOLD + "✧ MASTERWORK QUALITY ✧");

        // Store masterwork status
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyMasterworkStatus, PersistentDataType.INTEGER, 1);

        meta.setLore(lore);
        masterworkItem.setItemMeta(meta);

        // Add special glow
        Enchants.addGlow(masterworkItem);

        return masterworkItem;
    }

    /**
     * Boosts a stat line by a small amount (for Masterwork)
     */
    private String boostStatLine(String line) {
        // Find numbers in the line and increase them by 1
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher matcher = numberPattern.matcher(line);

        StringBuffer boosted = new StringBuffer();
        while (matcher.find()) {
            int originalValue = Integer.parseInt(matcher.group(1));
            int boostedValue = originalValue + 1;
            matcher.appendReplacement(boosted, String.valueOf(boostedValue));
        }
        matcher.appendTail(boosted);

        return boosted.toString();
    }

    /**
     * Handles hammer failure (item destruction or degradation)
     */
    private void handleHammerFailure(Player player, InventoryClickEvent event, ItemStack hammer, ItemStack item) {
        // Check if item is protected
        if (isProtectedItem(item)) {
            // Remove protection but save item
            ItemStack unprotectedItem = removeProtection(item);
            event.setCurrentItem(unprotectedItem);

            player.sendMessage(ChatColor.GREEN + "Your protection prevented the hammer from destroying your item!");
        } else {
            // Destroy the item
            event.setCurrentItem(null);
            player.sendMessage(ChatColor.RED + "💥 The hammer backfired and destroyed your item!");
        }

        // Consume the hammer
        consumeItem(event, hammer);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1.0f, 0.8f);
        showFailureEffect(player);
    }

    /**
     * Handles catastrophic reroll (all stats randomized)
     */
    private void handleCatastrophicReroll(Player player, InventoryClickEvent event, ItemStack hammer, ItemStack item) {
        player.sendMessage(ChatColor.DARK_RED + "⚠ CATASTROPHIC REROLL! All stats randomized!");

        // Use the orb system to completely reroll the item
        ItemStack rerolledItem = DropsManager.getInstance().createDrop(getItemTier(item), getItemType(item), getRarity(item));

        // Preserve enhancement level
        int enhancementLevel = getEnhancementLevel(item);
        if (enhancementLevel > 0) {
            rerolledItem = applyEnhancementLevel(rerolledItem, enhancementLevel);
        }

        // Consume hammer and update item
        consumeItem(event, hammer);
        event.setCurrentItem(rerolledItem);

        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        showCatastrophicEffect(player);
    }

    // GUI Methods for Master Hammer
    private void showStatSelectionGUI(Player player, List<RerollableStatLine> stats, ItemStack item) {
        // This would open a GUI showing all rerollable stats
        // For now, automatically select a random stat and show preview

        RerollableStatLine selectedStat = stats.get(ThreadLocalRandom.current().nextInt(stats.size()));

        // Generate 3 preview options
        List<StatRerollOption> options = new ArrayList<>();
        int tier = getItemTier(item);

        for (int i = 0; i < 3; i++) {
            String newLine = generateNewStatLine(selectedStat.statType, tier, false);
            options.add(new StatRerollOption(selectedStat.loreLine, newLine, selectedStat.statType, selectedStat.lineIndex));
        }

        // Store preview for the player
        pendingPreviews.put(player.getUniqueId(), new HammerPreview(options));

        // Show preview to player
        showPreviewSelectionGUI(player, pendingPreviews.get(player.getUniqueId()));
    }

    private void showPreviewSelectionGUI(Player player, HammerPreview preview) {
        player.sendMessage(ChatColor.YELLOW + "=== Master Hammer Preview ===");
        player.sendMessage(ChatColor.GRAY + "Choose your preferred outcome:");
        player.sendMessage("");

        for (int i = 0; i < preview.options.size(); i++) {
            StatRerollOption option = preview.options.get(i);
            player.sendMessage(ChatColor.AQUA + "Option " + (i + 1) + ": " + option.newLine);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Type 1, 2, or 3 to select your choice, or 'cancel' to abort.");
        player.sendMessage(ChatColor.RED + "You have 30 seconds to decide!");
    }

    // Utility methods
    private HammerType getHammerType(ItemStack item) {
        if (!item.hasItemMeta()) return null;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String hammerTypeName = container.get(keyHammerType, PersistentDataType.STRING);

        try {
            return HammerType.valueOf(hammerTypeName);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidItemForReroll(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasLore() &&
                !getRerollableStats(item).isEmpty();
    }

    private int getEnhancementLevel(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return 0;

        String name = item.getItemMeta().getDisplayName();
        Pattern plusPattern = Pattern.compile("\\[\\+(\\d+)\\]");
        Matcher matcher = plusPattern.matcher(ChatColor.stripColor(name));

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private int getItemTier(ItemStack item) {
        if (item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey tierKey = new NamespacedKey(plugin, "item_tier");
            if (container.has(tierKey, PersistentDataType.INTEGER)) {
                return container.get(tierKey, PersistentDataType.INTEGER);
            }
        }
        return 3; // Default tier
    }

    private int getItemType(ItemStack item) {
        if (item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey typeKey = new NamespacedKey(plugin, "item_type");
            if (container.has(typeKey, PersistentDataType.INTEGER)) {
                return container.get(typeKey, PersistentDataType.INTEGER);
            }
        }
        return 1; // Default type
    }

    private int getRarity(ItemStack item) {
        if (item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            NamespacedKey rarityKey = new NamespacedKey(plugin, "item_rarity");
            if (container.has(rarityKey, PersistentDataType.INTEGER)) {
                return container.get(rarityKey, PersistentDataType.INTEGER);
            }
        }
        return 1; // Default rarity
    }

    private String extractStatValue(String line, StatType statType) {
        // Extract the current value from the stat line
        Pattern valuePattern = Pattern.compile(statType.getDisplayName() + ":\\s*([+]?\\d+(?:\\s*-\\s*\\d+)?[%]?)");
        Matcher matcher = valuePattern.matcher(line);
        return matcher.find() ? matcher.group(1) : "0";
    }

    private int getStatLineCount(ItemStack item) {
        return getRerollableStats(item).size();
    }

    private int getMaxStatLines(ItemStack item) {
        int tier = getItemTier(item);
        return 3 + (tier / 2); // Base 3, +1 every 2 tiers
    }

    private ItemStack addRandomStatLine(ItemStack item) {
        StatType[] possibleStats = {StatType.STRENGTH, StatType.INTELLECT, StatType.VITALITY,
                StatType.DEXTERITY, StatType.CRITICAL_HIT, StatType.ACCURACY};

        StatType randomStat = possibleStats[ThreadLocalRandom.current().nextInt(possibleStats.length)];
        String newStatLine = generateNewStatLine(randomStat, getItemTier(item), false);

        ItemStack modifiedItem = item.clone();
        ItemMeta meta = modifiedItem.getItemMeta();
        List<String> lore = new ArrayList<>(meta.getLore());

        // Insert before rarity line
        int insertIndex = lore.size() - 1;
        lore.add(insertIndex, newStatLine);

        meta.setLore(lore);
        modifiedItem.setItemMeta(meta);

        return modifiedItem;
    }

    private ItemStack applyEnhancementLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        String currentName = meta.getDisplayName();

        // Remove existing enhancement prefix if any
        currentName = currentName.replaceAll("\\[\\+\\d+\\]\\s*", "");

        // Add new enhancement level
        meta.setDisplayName(ChatColor.RED + "[+" + level + "] " + currentName);

        item.setItemMeta(meta);
        return item;
    }

    private boolean isProtectedItem(ItemStack item) {
        // Check for protection scroll effects
        return item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(new NamespacedKey(plugin, "protection"), PersistentDataType.INTEGER);
    }

    private ItemStack removeProtection(ItemStack item) {
        ItemStack unprotected = item.clone();
        ItemMeta meta = unprotected.getItemMeta();

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(new NamespacedKey(plugin, "protection"));

        unprotected.setItemMeta(meta);
        return unprotected;
    }

    private void consumeItem(InventoryClickEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            event.setCursor(item);
        } else {
            event.setCursor(null);
        }
    }

    private void cleanupExpiredPreviews() {
        pendingPreviews.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    // Visual effects
    private void showHammerEffect(Player player, boolean isLegendary) {
        if (isLegendary) {
            player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
            FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BALL_LARGE, Color.ORANGE);
        } else {
            player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
            FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.ORANGE);
        }
    }

    private void showMasterworkEffect(Player player) {
        player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.STAR, Color.ORANGE);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BALL_LARGE, Color.YELLOW);
    }

    private void showCatastrophicEffect(Player player) {
        player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation().add(0, 1, 0), 10, 2, 2, 2, 0);
        ParticleUtil.showFailureEffect(player.getLocation());
    }

    private void showFailureEffect(Player player) {
        ParticleUtil.showFailureEffect(player.getLocation());
    }
}
package com.rednetty.server.mechanics.item.awakening;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.particles.FireworkUtil;
import com.rednetty.server.utils.particles.ParticleUtil;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Awakening Stone System - Unlock dormant power within items based on usage patterns
 * Rewards long-term item investment and creates attachment through usage-based progression
 */
public class AwakeningStoneSystem implements Listener {
    private static AwakeningStoneSystem instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Namespaced keys for persistent data
    private final NamespacedKey keyStoneType;
    private final NamespacedKey keyUsageKills;
    private final NamespacedKey keyUsageDamageTaken;
    private final NamespacedKey keyUsageCombatTime;
    private final NamespacedKey keyAwakened;
    private final NamespacedKey keyAwakenedStats;
    private final NamespacedKey keyLegendaryStatus;

    // Processing tracking
    private final Set<UUID> processingPlayers = new HashSet<>();

    // Usage tracking for items
    private final Map<String, ItemUsageData> itemUsageTracking = new HashMap<>();

    // Stone types
    public enum StoneType {
        AWAKENING("Stone of Awakening", Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE, 5000,
                "Reveals 1-3 hidden stats based on usage", false),
        TRUE_AWAKENING("Stone of True Awakening", Material.BEACON, ChatColor.GOLD, 25000,
                "Guarantees maximum awakened stats", true);

        private final String displayName;
        private final Material material;
        private final ChatColor color;
        private final int price;
        private final String description;
        private final boolean isLegendary;

        StoneType(String displayName, Material material, ChatColor color, int price,
                  String description, boolean isLegendary) {
            this.displayName = displayName;
            this.material = material;
            this.color = color;
            this.price = price;
            this.description = description;
            this.isLegendary = isLegendary;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public ChatColor getColor() { return color; }
        public int getPrice() { return price; }
        public String getDescription() { return description; }
        public boolean isLegendary() { return isLegendary; }
    }

    // Awakened stat types based on usage patterns
    public enum AwakenedStatType {
        // Combat-based stats
        BATTLE_TESTED("Battle-Tested", "+{value}% damage for every 100 kills", ChatColor.RED, "⚔"),
        KILLER_INSTINCT("Killer Instinct", "+{value}% critical hit for every 50 kills", ChatColor.DARK_RED, "💀"),
        VETERAN_EDGE("Veteran's Edge", "Attacks ignore {value}% of target's armor", ChatColor.GOLD, "🗡"),
        BLOODLUST("Bloodlust", "+{value}% life steal after 200+ kills", ChatColor.DARK_RED, "🩸"),

        // Defensive stats
        DEFENDER_RESOLVE("Defender's Resolve", "+{value}% damage reduction for every 50 hits taken", ChatColor.BLUE, "🛡"),
        SURVIVOR_WILL("Survivor's Will", "Regenerate {value}% max HP when below 25% health", ChatColor.GREEN, "💚"),
        IRON_SKIN("Iron Skin", "+{value} armor for every 100 damage taken", ChatColor.GRAY, "🦾"),
        LAST_STAND("Last Stand", "+{value}% damage when below 50% HP", ChatColor.YELLOW, "⚡"),

        // Utility stats
        EXPERIENCED("Experienced", "+{value}% accuracy from combat mastery", ChatColor.WHITE, "🎯"),
        WARRIOR_SPIRIT("Warrior's Spirit", "+{value}% energy regeneration in combat", ChatColor.AQUA, "⚡"),
        BATTLE_FOCUS("Battle Focus", "+{value}% chance to ignore stuns/slows", ChatColor.LIGHT_PURPLE, "🧠"),
        COMBAT_REFLEXES("Combat Reflexes", "+{value}% dodge chance in prolonged fights", ChatColor.YELLOW, "💨");

        private final String displayName;
        private final String description;
        private final ChatColor color;
        private final String symbol;

        AwakenedStatType(String displayName, String description, ChatColor color, String symbol) {
            this.displayName = displayName;
            this.description = description;
            this.color = color;
            this.symbol = symbol;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public ChatColor getColor() { return color; }
        public String getSymbol() { return symbol; }

        public String getFormattedDescription(int value) {
            return description.replace("{value}", String.valueOf(value));
        }
    }

    // Item usage data tracking
    private static class ItemUsageData {
        int totalKills = 0;
        int totalDamageTaken = 0;
        int combatTimeSeconds = 0;
        long lastUsed = System.currentTimeMillis();

        // Usage thresholds for awakening eligibility
        boolean isEligibleForAwakening() {
            return totalKills >= 50 || totalDamageTaken >= 1000 || combatTimeSeconds >= 600; // 10 minutes
        }

        int getUsageScore() {
            return (totalKills * 10) + (totalDamageTaken / 10) + (combatTimeSeconds / 60);
        }
    }

    // Awakened stat instance
    private static class AwakenedStat {
        final AwakenedStatType type;
        final int value;
        final String formattedLine;

        AwakenedStat(AwakenedStatType type, int value) {
            this.type = type;
            this.value = value;
            this.formattedLine = type.getColor() + type.getSymbol() + " " + type.getFormattedDescription(value);
        }
    }

    private AwakeningStoneSystem() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys
        this.keyStoneType = new NamespacedKey(plugin, "stone_type");
        this.keyUsageKills = new NamespacedKey(plugin, "usage_kills");
        this.keyUsageDamageTaken = new NamespacedKey(plugin, "usage_damage_taken");
        this.keyUsageCombatTime = new NamespacedKey(plugin, "usage_combat_time");
        this.keyAwakened = new NamespacedKey(plugin, "awakened");
        this.keyAwakenedStats = new NamespacedKey(plugin, "awakened_stats");
        this.keyLegendaryStatus = new NamespacedKey(plugin, "legendary_status");
    }

    public static AwakeningStoneSystem getInstance() {
        if (instance == null) {
            instance = new AwakeningStoneSystem();
        }
        return instance;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Start usage tracking update task
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateCombatTime, 20L, 20L);

        logger.info("[AwakeningStoneSystem] Awakening Stone System initialized");
    }

    /**
     * Creates a Stone of Awakening
     */
    public ItemStack createStoneOfAwakening() {
        return createAwakeningStone(StoneType.AWAKENING);
    }

    /**
     * Creates a Stone of True Awakening
     */
    public ItemStack createStoneOfTrueAwakening() {
        return createAwakeningStone(StoneType.TRUE_AWAKENING);
    }

    /**
     * Creates an awakening stone of the specified type
     */
    private ItemStack createAwakeningStone(StoneType stoneType) {
        ItemStack stone = new ItemStack(stoneType.getMaterial());
        ItemMeta meta = stone.getItemMeta();

        String title = stoneType.isLegendary() ? "✧ " + ChatColor.BOLD + stoneType.getDisplayName() + ChatColor.RESET + " ✧" :
                "◆ " + ChatColor.BOLD + stoneType.getDisplayName() + ChatColor.RESET + " ◆";

        meta.setDisplayName(stoneType.getColor() + title);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + stoneType.getDescription());
        lore.add("");

        if (stoneType.isLegendary()) {
            lore.add(ChatColor.GOLD + "✓ Guarantees maximum awakened stats");
            lore.add(ChatColor.GOLD + "✓ Adds unique 'Legendary' prefix");
            lore.add(ChatColor.GOLD + "✓ Grants special visual aura effect");
        } else {
            lore.add(ChatColor.GREEN + "✓ Reveals 1-3 hidden stat lines");
            lore.add(ChatColor.GREEN + "✓ Based on item usage patterns:");
            lore.add(ChatColor.YELLOW + "  • Combat → combat stats");
            lore.add(ChatColor.YELLOW + "  • Damage taken → defensive stats");
            lore.add(ChatColor.YELLOW + "  • Kills → offensive stats");
        }

        lore.add("");
        lore.add(ChatColor.RED + "⚠ Each item can only be awakened once!");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Usage: Click on extensively used items");
        lore.add(ChatColor.AQUA + "Price: " + ChatColor.WHITE + TextUtil.formatNumber(stoneType.getPrice()) + "g");

        if (stoneType.isLegendary()) {
            lore.add(ChatColor.DARK_GRAY.toString() + ChatColor.ITALIC + "Rare drop from world bosses");
        }

        lore.add("");
        lore.add(stoneType.getColor() + (stoneType.isLegendary() ? "✧" : "◆") + " " +
                ChatColor.ITALIC + "Awaken the dormant power within...");

        meta.setLore(lore);

        // Store stone type
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyStoneType, PersistentDataType.STRING, stoneType.name());

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        stone.setItemMeta(meta);

        // Add glow effect for legendary stones
        if (stoneType.isLegendary()) {
            Enchants.addGlow(stone);
        }

        return stone;
    }

    /**
     * Main event handler for awakening stone interactions
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

        // Handle Awakening Stone usage
        StoneType stoneType = getStoneType(cursor);
        if (stoneType != null && isValidItemForAwakening(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processItemAwakening(player, event, cursor, currentItem, stoneType);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }
    }

    /**
     * Processes awakening stone application to an item
     */
    private void processItemAwakening(Player player, InventoryClickEvent event, ItemStack stone,
                                      ItemStack targetItem, StoneType stoneType) {

        // Check if item is already awakened
        if (isAwakened(targetItem)) {
            player.sendMessage(ChatColor.RED + "This item has already been awakened!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Check if item has sufficient usage
        ItemUsageData usage = getItemUsageData(targetItem);
        if (!usage.isEligibleForAwakening() && stoneType != StoneType.TRUE_AWAKENING) {
            player.sendMessage(ChatColor.RED + "This item hasn't been used extensively enough to awaken!");
            player.sendMessage(ChatColor.YELLOW + "Required: 50+ kills OR 1000+ damage taken OR 10+ minutes combat");
            player.sendMessage(ChatColor.GRAY + "Current: " + usage.totalKills + " kills, " +
                    usage.totalDamageTaken + " damage taken, " +
                    (usage.combatTimeSeconds / 60) + " minutes combat");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Generate awakened stats based on usage patterns
        List<AwakenedStat> awakenedStats = generateAwakenedStats(usage, stoneType);

        if (awakenedStats.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Failed to generate awakened stats for this item!");
            return;
        }

        // Apply awakening to the item
        ItemStack awakenedItem = applyAwakening(targetItem, awakenedStats, stoneType);

        // Consume the stone
        consumeItem(event, stone);

        // Update the item
        event.setCurrentItem(awakenedItem);

        // Success feedback
        player.sendMessage(ChatColor.GOLD + "✧ AWAKENING SUCCESSFUL! ✧");
        player.sendMessage(ChatColor.YELLOW + "Your " + getItemName(targetItem) + " has awakened!");

        for (AwakenedStat stat : awakenedStats) {
            player.sendMessage(ChatColor.GREEN + "  ✓ " + stat.formattedLine);
        }

        if (stoneType.isLegendary()) {
            player.sendMessage(ChatColor.GOLD + "  ✧ LEGENDARY STATUS GRANTED! ✧");
        }

        // Visual and sound effects
        if (stoneType.isLegendary()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
            showLegendaryAwakeningEffect(player);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
            showAwakeningEffect(player);
        }
    }

    /**
     * Generates awakened stats based on item usage patterns
     */
    private List<AwakenedStat> generateAwakenedStats(ItemUsageData usage, StoneType stoneType) {
        List<AwakenedStat> stats = new ArrayList<>();

        // Determine how many stats to generate
        int statCount;
        if (stoneType.isLegendary()) {
            statCount = 3; // Always maximum for True Awakening
        } else {
            // 1-3 stats based on usage score
            int usageScore = usage.getUsageScore();
            if (usageScore >= 1000) statCount = 3;
            else if (usageScore >= 500) statCount = 2;
            else statCount = 1;
        }

        // Collect eligible stat types based on usage patterns
        List<AwakenedStatType> eligibleStats = new ArrayList<>();

        // Combat-based stats (kills)
        if (usage.totalKills >= 50) {
            eligibleStats.addAll(Arrays.asList(
                    AwakenedStatType.BATTLE_TESTED,
                    AwakenedStatType.KILLER_INSTINCT,
                    AwakenedStatType.VETERAN_EDGE,
                    AwakenedStatType.BLOODLUST
            ));
        }

        // Defensive stats (damage taken)
        if (usage.totalDamageTaken >= 500) {
            eligibleStats.addAll(Arrays.asList(
                    AwakenedStatType.DEFENDER_RESOLVE,
                    AwakenedStatType.SURVIVOR_WILL,
                    AwakenedStatType.IRON_SKIN,
                    AwakenedStatType.LAST_STAND
            ));
        }

        // Utility stats (combat time)
        if (usage.combatTimeSeconds >= 300) { // 5+ minutes
            eligibleStats.addAll(Arrays.asList(
                    AwakenedStatType.EXPERIENCED,
                    AwakenedStatType.WARRIOR_SPIRIT,
                    AwakenedStatType.BATTLE_FOCUS,
                    AwakenedStatType.COMBAT_REFLEXES
            ));
        }

        // Fallback stats if none eligible
        if (eligibleStats.isEmpty()) {
            eligibleStats.addAll(Arrays.asList(
                    AwakenedStatType.EXPERIENCED,
                    AwakenedStatType.WARRIOR_SPIRIT
            ));
        }

        // Generate stats
        Set<AwakenedStatType> usedTypes = new HashSet<>();
        for (int i = 0; i < statCount && !eligibleStats.isEmpty(); i++) {
            // Filter out already used types
            List<AwakenedStatType> availableTypes = new ArrayList<>();
            for (AwakenedStatType type : eligibleStats) {
                if (!usedTypes.contains(type)) {
                    availableTypes.add(type);
                }
            }

            if (availableTypes.isEmpty()) break;

            // Select random stat type
            AwakenedStatType selectedType = availableTypes.get(ThreadLocalRandom.current().nextInt(availableTypes.size()));
            usedTypes.add(selectedType);

            // Generate value based on usage and stone type
            int value = generateStatValue(selectedType, usage, stoneType);
            stats.add(new AwakenedStat(selectedType, value));
        }

        return stats;
    }

    /**
     * Generates an appropriate value for an awakened stat
     */
    private int generateStatValue(AwakenedStatType statType, ItemUsageData usage, StoneType stoneType) {
        int baseValue;

        switch (statType) {
            case BATTLE_TESTED:
                baseValue = Math.min(20, usage.totalKills / 50); // 1% per 50 kills, max 20%
                break;
            case KILLER_INSTINCT:
                baseValue = Math.min(15, usage.totalKills / 100); // 1% per 100 kills, max 15%
                break;
            case VETERAN_EDGE:
                baseValue = Math.min(25, 10 + (usage.totalKills / 100)); // Base 10%, +1% per 100 kills
                break;
            case BLOODLUST:
                baseValue = usage.totalKills >= 200 ? ThreadLocalRandom.current().nextInt(10, 21) : 0; // 10-20% after 200 kills
                break;
            case DEFENDER_RESOLVE:
                baseValue = Math.min(15, usage.totalDamageTaken / 200); // 1% per 200 damage, max 15%
                break;
            case SURVIVOR_WILL:
                baseValue = Math.min(10, 2 + (usage.totalDamageTaken / 500)); // Base 2%, +1% per 500 damage
                break;
            case IRON_SKIN:
                baseValue = Math.min(50, usage.totalDamageTaken / 100); // 1 armor per 100 damage
                break;
            case LAST_STAND:
                baseValue = Math.min(30, 10 + (usage.totalDamageTaken / 300)); // Base 10%, scaling
                break;
            case EXPERIENCED:
                baseValue = Math.min(20, usage.combatTimeSeconds / 120); // 1% per 2 minutes
                break;
            case WARRIOR_SPIRIT:
                baseValue = Math.min(25, 5 + (usage.combatTimeSeconds / 180)); // Base 5%, scaling
                break;
            case BATTLE_FOCUS:
                baseValue = Math.min(15, usage.combatTimeSeconds / 240); // 1% per 4 minutes
                break;
            case COMBAT_REFLEXES:
                baseValue = Math.min(20, usage.combatTimeSeconds / 150); // 1% per 2.5 minutes
                break;
            default:
                baseValue = 5;
        }

        // Boost for legendary stones
        if (stoneType.isLegendary()) {
            baseValue = (int) (baseValue * 1.5);
        }

        return Math.max(1, baseValue);
    }

    /**
     * Applies awakening to an item
     */
    private ItemStack applyAwakening(ItemStack item, List<AwakenedStat> awakenedStats, StoneType stoneType) {
        ItemStack awakenedItem = item.clone();
        ItemMeta meta = awakenedItem.getItemMeta();

        // Update name
        String currentName = meta.getDisplayName();
        if (stoneType.isLegendary()) {
            if (!currentName.contains("[Legendary]")) {
                meta.setDisplayName(ChatColor.GOLD + "[Legendary] " + currentName);
            }
        } else {
            if (!currentName.contains("[Awakened]")) {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "[Awakened] " + currentName);
            }
        }

        // Add awakened stats to lore
        List<String> lore = new ArrayList<>(meta.getLore());

        // Find insertion point (before rarity line)
        int insertIndex = lore.size() - 1;
        for (int i = 0; i < lore.size(); i++) {
            String line = ChatColor.stripColor(lore.get(i));
            if (line.toLowerCase().contains("common") || line.toLowerCase().contains("uncommon") ||
                    line.toLowerCase().contains("rare") || line.toLowerCase().contains("unique") ||
                    line.toLowerCase().contains("legendary")) {
                insertIndex = i;
                break;
            }
        }

        // Add awakened stats
        lore.add(insertIndex, "");
        lore.add(insertIndex + 1, ChatColor.LIGHT_PURPLE + "◆ AWAKENED ABILITIES ◆");

        for (int i = 0; i < awakenedStats.size(); i++) {
            lore.add(insertIndex + 2 + i, awakenedStats.get(i).formattedLine);
        }

        meta.setLore(lore);

        // Store awakening data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyAwakened, PersistentDataType.INTEGER, 1);
        container.set(keyAwakenedStats, PersistentDataType.STRING, serializeAwakenedStats(awakenedStats));

        if (stoneType.isLegendary()) {
            container.set(keyLegendaryStatus, PersistentDataType.INTEGER, 1);
        }

        awakenedItem.setItemMeta(meta);

        // Add glow effect
        Enchants.addGlow(awakenedItem);

        return awakenedItem;
    }

    /**
     * Tracks kills for items
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player) {
            Player killer = event.getEntity().getKiller();
            ItemStack weapon = killer.getInventory().getItemInMainHand();

            if (weapon != null && weapon.getType() != Material.AIR) {
                incrementItemKills(weapon);
            }
        }
    }

    /**
     * Tracks damage taken for armor items
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            double damage = event.getFinalDamage();

            // Track damage for all equipped armor
            ItemStack[] armor = player.getInventory().getArmorContents();
            for (ItemStack piece : armor) {
                if (piece != null && piece.getType() != Material.AIR) {
                    incrementItemDamageTaken(piece, (int) damage);
                }
            }
        }
    }

    /**
     * Updates combat time for held items
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // Mark the new item as being used in combat if player is in combat
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem != null && newItem.getType() != Material.AIR && isPlayerInCombat(player)) {
            markItemInCombat(newItem);
        }
    }

    /**
     * Updates combat time tracking
     */
    private void updateCombatTime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isPlayerInCombat(player)) {
                ItemStack weapon = player.getInventory().getItemInMainHand();
                if (weapon != null && weapon.getType() != Material.AIR) {
                    incrementItemCombatTime(weapon);
                }
            }
        }
    }

    // Usage tracking helper methods
    private void incrementItemKills(ItemStack item) {
        ItemUsageData usage = getItemUsageData(item);
        usage.totalKills++;
        usage.lastUsed = System.currentTimeMillis();
        storeItemUsageData(item, usage);
    }

    private void incrementItemDamageTaken(ItemStack item, int damage) {
        ItemUsageData usage = getItemUsageData(item);
        usage.totalDamageTaken += damage;
        usage.lastUsed = System.currentTimeMillis();
        storeItemUsageData(item, usage);
    }

    private void incrementItemCombatTime(ItemStack item) {
        ItemUsageData usage = getItemUsageData(item);
        usage.combatTimeSeconds++;
        usage.lastUsed = System.currentTimeMillis();
        storeItemUsageData(item, usage);
    }

    private void markItemInCombat(ItemStack item) {
        // Just update last used time
        ItemUsageData usage = getItemUsageData(item);
        usage.lastUsed = System.currentTimeMillis();
        storeItemUsageData(item, usage);
    }

    private ItemUsageData getItemUsageData(ItemStack item) {
        if (!item.hasItemMeta()) {
            return new ItemUsageData();
        }

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        ItemUsageData usage = new ItemUsageData();

        if (container.has(keyUsageKills, PersistentDataType.INTEGER)) {
            usage.totalKills = container.get(keyUsageKills, PersistentDataType.INTEGER);
        }
        if (container.has(keyUsageDamageTaken, PersistentDataType.INTEGER)) {
            usage.totalDamageTaken = container.get(keyUsageDamageTaken, PersistentDataType.INTEGER);
        }
        if (container.has(keyUsageCombatTime, PersistentDataType.INTEGER)) {
            usage.combatTimeSeconds = container.get(keyUsageCombatTime, PersistentDataType.INTEGER);
        }

        return usage;
    }

    private void storeItemUsageData(ItemStack item, ItemUsageData usage) {
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(keyUsageKills, PersistentDataType.INTEGER, usage.totalKills);
        container.set(keyUsageDamageTaken, PersistentDataType.INTEGER, usage.totalDamageTaken);
        container.set(keyUsageCombatTime, PersistentDataType.INTEGER, usage.combatTimeSeconds);

        item.setItemMeta(meta);
    }

    // Utility methods
    private StoneType getStoneType(ItemStack item) {
        if (!item.hasItemMeta()) return null;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String stoneTypeName = container.get(keyStoneType, PersistentDataType.STRING);

        try {
            return StoneType.valueOf(stoneTypeName);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidItemForAwakening(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasLore() && !isAwakened(item);
    }

    private boolean isAwakened(ItemStack item) {
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(keyAwakened, PersistentDataType.INTEGER);
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        return TextUtil.formatItemName(item.getType().name());
    }

    private boolean isPlayerInCombat(Player player) {
        // Simple combat check - could be  with actual combat tracking
        return player.getLastDamageCause() != null &&
                (System.currentTimeMillis() - player.getLastDamage()) < 10000; // 10 seconds
    }

    private String serializeAwakenedStats(List<AwakenedStat> stats) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stats.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(stats.get(i).type.name()).append(":").append(stats.get(i).value);
        }
        return sb.toString();
    }

    private List<AwakenedStat> deserializeAwakenedStats(String serialized) {
        List<AwakenedStat> stats = new ArrayList<>();
        if (serialized == null || serialized.isEmpty()) return stats;

        String[] parts = serialized.split(";");
        for (String part : parts) {
            String[] statData = part.split(":");
            if (statData.length == 2) {
                try {
                    AwakenedStatType type = AwakenedStatType.valueOf(statData[0]);
                    int value = Integer.parseInt(statData[1]);
                    stats.add(new AwakenedStat(type, value));
                } catch (Exception e) {
                    // Skip invalid entries
                }
            }
        }

        return stats;
    }

    private void consumeItem(InventoryClickEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            event.setCursor(item);
        } else {
            event.setCursor(null);
        }
    }

    // Visual effects
    private void showAwakeningEffect(Player player) {
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.PURPLE);
    }

    private void showLegendaryAwakeningEffect(Player player) {
        player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1, 0), 100, 2, 2, 2, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0), 50, 2, 2, 2, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.STAR, Color.ORANGE);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BALL_LARGE, Color.YELLOW);

        // Create a spectacular multi-firework display
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.ORANGE);
        }, 10L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BALL, Color.RED);
        }, 20L);
    }

    /**
     * Gets item usage statistics for display
     */
    public ItemUsageData getItemUsageStats(ItemStack item) {
        return getItemUsageData(item);
    }

    /**
     * Gets awakened stats from an item
     */
    public List<AwakenedStat> getAwakenedStats(ItemStack item) {
        if (!isAwakened(item)) return new ArrayList<>();

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String serialized = container.get(keyAwakenedStats, PersistentDataType.STRING);

        return deserializeAwakenedStats(serialized);
    }

    /**
     * Checks if an item has legendary awakening status
     */
    public boolean hasLegendaryStatus(ItemStack item) {
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(keyLegendaryStatus, PersistentDataType.INTEGER);
    }
}
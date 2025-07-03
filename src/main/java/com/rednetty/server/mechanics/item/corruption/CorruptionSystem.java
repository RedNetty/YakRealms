package com.rednetty.server.mechanics.item.corruption;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.particles.FireworkUtil;
import com.rednetty.server.utils.particles.ParticleUtil;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Corruption System - High-risk modifications that can greatly enhance or ruin items
 * Power at a price - embrace the darkness for potential great rewards
 */
public class CorruptionSystem implements Listener {
    private static CorruptionSystem instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Namespaced keys for persistent data
    private final NamespacedKey keyCorruptionType;
    private final NamespacedKey keyCorruptionLevel;
    private final NamespacedKey keyCorruptionEffects;
    private final NamespacedKey keyPurificationTome;
    private final NamespacedKey keyCorruptionResistance;

    // Processing tracking
    private final Set<UUID> processingPlayers = new HashSet<>();

    // Corruption vial types
    public enum CorruptionVialType {
        MINOR("Vial of Minor Corruption", Material.LINGERING_POTION, ChatColor.DARK_PURPLE, 1200,
                "Adds random corruption effect", 70, 30, 0, 3),
        MAJOR("Vial of Major Corruption", Material.TOTEM_OF_UNDYING, ChatColor.DARK_RED, 8000,
                "Powerful corruption effects", 50, 40, 10, 1);

        private final String displayName;
        private final Material material;
        private final ChatColor color;
        private final int price;
        private final String description;
        private final int beneficialChance;
        private final int detrimentalChance;
        private final int destructionChance;
        private final int maxStacks;

        CorruptionVialType(String displayName, Material material, ChatColor color, int price,
                           String description, int beneficialChance, int detrimentalChance,
                           int destructionChance, int maxStacks) {
            this.displayName = displayName;
            this.material = material;
            this.color = color;
            this.price = price;
            this.description = description;
            this.beneficialChance = beneficialChance;
            this.detrimentalChance = detrimentalChance;
            this.destructionChance = destructionChance;
            this.maxStacks = maxStacks;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public ChatColor getColor() { return color; }
        public int getPrice() { return price; }
        public String getDescription() { return description; }
        public int getBeneficialChance() { return beneficialChance; }
        public int getDetrimentalChance() { return detrimentalChance; }
        public int getDestructionChance() { return destructionChance; }
        public int getMaxStacks() { return maxStacks; }
    }

    // Corruption effects
    public enum CorruptionEffect {
        // Beneficial corruptions
        BLOODTHIRST("Bloodthirst", true, "+{value}% life steal, -{penalty}% max HP",
                ChatColor.DARK_RED, "🩸", 25, 10),
        BERSERKER_FURY("Berserker's Fury", true, "+{value}% damage when below 50% HP",
                ChatColor.RED, "😡", 40, 0),
        PHASE_WALKER("Phase Walker", true, "{value}% chance to dodge through attacks",
                ChatColor.LIGHT_PURPLE, "👻", 15, 0),
        SOUL_BURN("Soul Burn", true, "Elemental damage spreads to nearby enemies",
                ChatColor.BLUE, "🔥", 100, 0),
        VOID_TOUCHED("Void Touched", true, "+{value}% damage, attacks ignore {penalty}% accuracy",
                ChatColor.DARK_PURPLE, "🌀", 30, 20),
        SHADOW_INFUSED("Shadow Infused", true, "+{value}% critical hit, -{penalty}% visibility",
                ChatColor.GRAY, "🌙", 20, 15),

        // Detrimental corruptions
        CURSED_LUCK("Cursed Luck", false, "-{penalty}% critical hit chance, but critical hits deal +{value}% damage",
                ChatColor.YELLOW, "🎲", 100, 10),
        FRAGILE_POWER("Fragile Power", false, "+{value}% damage, item loses durability {penalty}x faster",
                ChatColor.GOLD, "💥", 30, 3),
        MANA_BURN("Mana Burn", false, "Using abilities costs HP instead of energy",
                ChatColor.DARK_BLUE, "💀", 0, 0),
        VENGEFUL_SPIRIT("Vengeful Spirit", false, "Item occasionally attacks random nearby players",
                ChatColor.DARK_RED, "👹", 0, 0),
        CHAOS_TOUCH("Chaos Touch", false, "Stats randomly fluctuate by ±{penalty}% each minute",
                ChatColor.DARK_PURPLE, "🌪", 0, 25),
        SOUL_DRAIN("Soul Drain", false, "-{penalty}% max HP, +{value}% damage to undead",
                ChatColor.BLACK, "💀", 50, 20);

        private final String displayName;
        private final boolean isBeneficial;
        private final String description;
        private final ChatColor color;
        private final String symbol;
        private final int baseValue;
        private final int basePenalty;

        CorruptionEffect(String displayName, boolean isBeneficial, String description,
                         ChatColor color, String symbol, int baseValue, int basePenalty) {
            this.displayName = displayName;
            this.isBeneficial = isBeneficial;
            this.description = description;
            this.color = color;
            this.symbol = symbol;
            this.baseValue = baseValue;
            this.basePenalty = basePenalty;
        }

        public String getDisplayName() { return displayName; }
        public boolean isBeneficial() { return isBeneficial; }
        public String getDescription() { return description; }
        public ChatColor getColor() { return color; }
        public String getSymbol() { return symbol; }
        public int getBaseValue() { return baseValue; }
        public int getBasePenalty() { return basePenalty; }

        public String getFormattedDescription(int value, int penalty) {
            return description.replace("{value}", String.valueOf(value))
                    .replace("{penalty}", String.valueOf(penalty));
        }
    }

    // Corruption instance on an item
    private static class ItemCorruption {
        final CorruptionEffect effect;
        final int value;
        final int penalty;
        final String formattedLine;

        ItemCorruption(CorruptionEffect effect, int value, int penalty) {
            this.effect = effect;
            this.value = value;
            this.penalty = penalty;

            String prefix = effect.isBeneficial() ?
                    effect.getColor() + "✓ CORRUPT: " :
                    ChatColor.DARK_RED + "✗ CORRUPT: ";

            this.formattedLine = prefix + effect.getSymbol() + " " +
                    effect.getFormattedDescription(value, penalty);
        }
    }

    private CorruptionSystem() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys
        this.keyCorruptionType = new NamespacedKey(plugin, "corruption_type");
        this.keyCorruptionLevel = new NamespacedKey(plugin, "corruption_level");
        this.keyCorruptionEffects = new NamespacedKey(plugin, "corruption_effects");
        this.keyPurificationTome = new NamespacedKey(plugin, "purification_tome");
        this.keyCorruptionResistance = new NamespacedKey(plugin, "corruption_resistance");
    }

    public static CorruptionSystem getInstance() {
        if (instance == null) {
            instance = new CorruptionSystem();
        }
        return instance;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Start corruption effect update task
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateCorruptionEffects, 20L, 1200L); // Every minute

        logger.info("[CorruptionSystem] Corruption System initialized");
    }

    /**
     * Creates a Vial of Minor Corruption
     */
    public ItemStack createMinorCorruptionVial() {
        return createCorruptionVial(CorruptionVialType.MINOR);
    }

    /**
     * Creates a Vial of Major Corruption
     */
    public ItemStack createMajorCorruptionVial() {
        return createCorruptionVial(CorruptionVialType.MAJOR);
    }

    /**
     * Creates a corruption vial of the specified type
     */
    private ItemStack createCorruptionVial(CorruptionVialType vialType) {
        ItemStack vial = new ItemStack(vialType.getMaterial());
        ItemMeta meta = vial.getItemMeta();

        meta.setDisplayName(vialType.getColor() + "🧪 " + ChatColor.BOLD + vialType.getDisplayName() +
                ChatColor.RESET + " 🧪");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + vialType.getDescription());
        lore.add("");

        // Add outcome chances
        lore.add(ChatColor.GREEN + "✓ " + vialType.getBeneficialChance() + "% beneficial corruption");
        lore.add(ChatColor.RED + "✗ " + vialType.getDetrimentalChance() + "% detrimental corruption");

        if (vialType.getDestructionChance() > 0) {
            lore.add(ChatColor.DARK_RED + "💀 " + vialType.getDestructionChance() + "% item destruction");
        }

        lore.add("");

        if (vialType == CorruptionVialType.MINOR) {
            lore.add(ChatColor.YELLOW + "Can stack up to " + vialType.getMaxStacks() + " corruptions per item");
        } else {
            lore.add(ChatColor.YELLOW + "Overwrites all existing corruptions");
        }

        lore.add("");
        lore.add(ChatColor.RED + "⚠ CORRUPTION IS PERMANENT WITHOUT PURIFICATION!");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Usage: Click on any item");
        lore.add(ChatColor.AQUA + "Price: " + ChatColor.WHITE + TextUtil.formatNumber(vialType.getPrice()) + "g");
        lore.add("");
        lore.add(vialType.getColor() + "🧪 " + ChatColor.ITALIC + "Embrace the darkness within...");

        meta.setLore(lore);

        // Store vial type
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyCorruptionType, PersistentDataType.STRING, vialType.name());

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
        vial.setItemMeta(meta);

        // Add dark glow effect
        Enchants.addGlow(vial);

        return vial;
    }

    /**
     * Creates a Purification Tome
     */
    public ItemStack createPurificationTome() {
        ItemStack tome = new ItemStack(Material.BOOK);
        ItemMeta meta = tome.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "📖 " + ChatColor.BOLD + "Purification Tome" + ChatColor.RESET + " 📖");

        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY + "Removes all corruptions from an item,",
                ChatColor.GRAY + "cleansing it of dark influences.",
                "",
                ChatColor.GREEN + "✓ Removes all corruption effects",
                ChatColor.YELLOW + "◆ 25% chance to leave beneficial corruptions intact",
                "",
                ChatColor.YELLOW + "Usage: Click on any corrupted item",
                ChatColor.AQUA + "Price: " + ChatColor.WHITE + "3,000g",
                "",
                ChatColor.GOLD + "📖 " + ChatColor.ITALIC + "Purify the tainted essence..."
        );
        meta.setLore(lore);

        // Store tome type
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyPurificationTome, PersistentDataType.STRING, "purification_tome");

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        tome.setItemMeta(meta);

        // Add holy glow
        Enchants.addGlow(tome);

        return tome;
    }

    /**
     * Main event handler for corruption interactions
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

        // Handle Corruption Vial usage
        CorruptionVialType vialType = getCorruptionVialType(cursor);
        if (vialType != null && isValidItemForCorruption(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processItemCorruption(player, event, cursor, currentItem, vialType);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }

        // Handle Purification Tome usage
        else if (isPurificationTome(cursor) && isCorrupted(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processItemPurification(player, event, cursor, currentItem);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }
    }

    /**
     * Processes corruption vial application to an item
     */
    private void processItemCorruption(Player player, InventoryClickEvent event, ItemStack vial,
                                       ItemStack targetItem, CorruptionVialType vialType) {

        // Check current corruption level
        int currentCorruptions = getCorruptionCount(targetItem);

        if (vialType == CorruptionVialType.MINOR && currentCorruptions >= vialType.getMaxStacks()) {
            player.sendMessage(ChatColor.RED + "This item already has the maximum number of corruptions!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Roll for outcome
        int roll = ThreadLocalRandom.current().nextInt(100);

        // Handle destruction chance for major corruption
        if (vialType == CorruptionVialType.MAJOR && roll < vialType.getDestructionChance()) {
            handleItemDestruction(player, event, vial, targetItem);
            return;
        }

        // Determine if corruption is beneficial or detrimental
        boolean isBeneficial;
        if (roll < vialType.getBeneficialChance()) {
            isBeneficial = true;
        } else {
            isBeneficial = false;
        }

        // Generate corruption effect
        CorruptionEffect effect = selectRandomCorruption(isBeneficial);
        ItemCorruption corruption = generateCorruption(effect, getItemTier(targetItem));

        // Apply corruption to item
        ItemStack corruptedItem;
        if (vialType == CorruptionVialType.MAJOR) {
            // Major corruption overwrites all existing corruptions
            corruptedItem = applyMajorCorruption(targetItem, corruption);
        } else {
            // Minor corruption adds to existing
            corruptedItem = applyMinorCorruption(targetItem, corruption);
        }

        // Consume the vial
        consumeItem(event, vial);

        // Update the item
        event.setCurrentItem(corruptedItem);

        // Feedback
        if (isBeneficial) {
            player.sendMessage(ChatColor.GREEN + "✓ Beneficial corruption applied!");
            player.sendMessage(ChatColor.YELLOW + "Effect: " + corruption.formattedLine);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
            showBeneficialCorruptionEffect(player);
        } else {
            player.sendMessage(ChatColor.RED + "✗ Detrimental corruption applied!");
            player.sendMessage(ChatColor.DARK_RED + "Effect: " + corruption.formattedLine);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
            showDetrimentalCorruptionEffect(player);
        }
    }

    /**
     * Processes purification tome usage on a corrupted item
     */
    private void processItemPurification(Player player, InventoryClickEvent event, ItemStack tome, ItemStack corruptedItem) {
        List<ItemCorruption> currentCorruptions = getItemCorruptions(corruptedItem);

        if (currentCorruptions.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This item is not corrupted!");
            return;
        }

        // Check for selective purification (25% chance to keep beneficial corruptions)
        boolean selectivePurification = ThreadLocalRandom.current().nextInt(100) < 25;

        ItemStack purifiedItem;
        List<ItemCorruption> preservedCorruptions = new ArrayList<>();

        if (selectivePurification) {
            // Keep only beneficial corruptions
            for (ItemCorruption corruption : currentCorruptions) {
                if (corruption.effect.isBeneficial()) {
                    preservedCorruptions.add(corruption);
                }
            }

            if (!preservedCorruptions.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "✨ Selective purification! Beneficial corruptions preserved!");
            }
        }

        // Apply purification
        if (preservedCorruptions.isEmpty()) {
            purifiedItem = removeAllCorruptions(corruptedItem);
        } else {
            purifiedItem = applySelectivePurification(corruptedItem, preservedCorruptions);
        }

        // Consume the tome
        consumeItem(event, tome);

        // Update the item
        event.setCurrentItem(purifiedItem);

        // Success feedback
        int removedCount = currentCorruptions.size() - preservedCorruptions.size();
        player.sendMessage(ChatColor.GOLD + "📖 Purification complete!");
        player.sendMessage(ChatColor.GREEN + "Removed " + removedCount + " corruption(s)");

        if (!preservedCorruptions.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Preserved " + preservedCorruptions.size() + " beneficial corruption(s)");
        }

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        showPurificationEffect(player);
    }

    /**
     * Handles item destruction from corruption
     */
    private void handleItemDestruction(Player player, InventoryClickEvent event, ItemStack vial, ItemStack item) {
        // Destroy the item
        event.setCurrentItem(null);

        // Consume the vial
        consumeItem(event, vial);

        // Dramatic feedback
        player.sendMessage(ChatColor.DARK_RED + "💀 THE CORRUPTION WAS TOO STRONG! 💀");
        player.sendMessage(ChatColor.RED + "Your item has been consumed by darkness!");

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        showDestructionEffect(player);
    }

    /**
     * Applies major corruption (overwrites existing)
     */
    private ItemStack applyMajorCorruption(ItemStack item, ItemCorruption corruption) {
        ItemStack corruptedItem = removeAllCorruptions(item); // Clear existing first
        return addCorruptionToItem(corruptedItem, corruption, true);
    }

    /**
     * Applies minor corruption (adds to existing)
     */
    private ItemStack applyMinorCorruption(ItemStack item, ItemCorruption corruption) {
        return addCorruptionToItem(item, corruption, false);
    }

    /**
     * Adds a corruption effect to an item
     */
    private ItemStack addCorruptionToItem(ItemStack item, ItemCorruption corruption, boolean isMajor) {
        ItemStack corruptedItem = item.clone();
        ItemMeta meta = corruptedItem.getItemMeta();

        // Update name to indicate corruption
        String currentName = meta.getDisplayName();
        String corruptionPrefix = isMajor ? ChatColor.DARK_RED + "[MAJOR CORRUPT] " :
                ChatColor.DARK_PURPLE + "[CORRUPT] ";

        if (!currentName.contains("[CORRUPT]") && !currentName.contains("[MAJOR CORRUPT]")) {
            meta.setDisplayName(corruptionPrefix + currentName);
        } else if (isMajor && currentName.contains("[CORRUPT]") && !currentName.contains("[MAJOR CORRUPT]")) {
            // Upgrade to major corruption
            currentName = currentName.replace("[CORRUPT]", "[MAJOR CORRUPT]");
            meta.setDisplayName(currentName);
        }

        // Add corruption to lore
        List<String> lore = new ArrayList<>(meta.getLore());

        // Find insertion point (before rarity line)
        int insertIndex = findCorruptionInsertionPoint(lore);

        // Add corruption section header if this is the first corruption
        List<ItemCorruption> existingCorruptions = getItemCorruptions(item);
        if (existingCorruptions.isEmpty()) {
            lore.add(insertIndex, "");
            lore.add(insertIndex + 1, ChatColor.DARK_PURPLE + "◆ CORRUPTED ESSENCE ◆");
            insertIndex += 2;
        }

        // Add the new corruption effect
        lore.add(insertIndex, corruption.formattedLine);

        meta.setLore(lore);

        // Store corruption data
        List<ItemCorruption> allCorruptions = new ArrayList<>(existingCorruptions);
        allCorruptions.add(corruption);
        storeCorruptionData(meta, allCorruptions);

        corruptedItem.setItemMeta(meta);

        // Add dark glow effect
        Enchants.addGlow(corruptedItem);

        return corruptedItem;
    }

    /**
     * Removes all corruptions from an item
     */
    private ItemStack removeAllCorruptions(ItemStack item) {
        ItemStack purifiedItem = item.clone();
        ItemMeta meta = purifiedItem.getItemMeta();

        // Remove corruption prefix from name
        String currentName = meta.getDisplayName();
        currentName = currentName.replaceAll("\\[(MAJOR )?CORRUPT\\]\\s*", "");
        meta.setDisplayName(currentName);

        // Remove corruption effects from lore
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.removeIf(line -> {
            String cleanLine = ChatColor.stripColor(line);
            return cleanLine.contains("CORRUPTED ESSENCE") ||
                    cleanLine.contains("✓ CORRUPT:") ||
                    cleanLine.contains("✗ CORRUPT:");
        });

        // Remove empty lines that might be left behind
        removeEmptyLines(lore);

        meta.setLore(lore);

        // Clear corruption data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(keyCorruptionEffects);
        container.remove(keyCorruptionLevel);

        purifiedItem.setItemMeta(meta);

        // Remove glow effect if no other special properties
        if (!hasOtherSpecialProperties(purifiedItem)) {
            Enchants.removeGlow(purifiedItem);
        }

        return purifiedItem;
    }

    /**
     * Applies selective purification (keeps beneficial corruptions)
     */
    private ItemStack applySelectivePurification(ItemStack item, List<ItemCorruption> preservedCorruptions) {
        // Start with fully purified item
        ItemStack selectivelyPurified = removeAllCorruptions(item);

        // Re-add preserved corruptions
        for (ItemCorruption corruption : preservedCorruptions) {
            selectivelyPurified = addCorruptionToItem(selectivelyPurified, corruption, false);
        }

        return selectivelyPurified;
    }

    /**
     * Handles corruption effects during gameplay
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null || !isCorrupted(weapon)) return;

        List<ItemCorruption> corruptions = getItemCorruptions(weapon);
        LivingEntity target = (LivingEntity) event.getEntity();

        for (ItemCorruption corruption : corruptions) {
            switch (corruption.effect) {
                case BLOODTHIRST:
                    // Life steal effect
                    double lifeSteal = event.getDamage() * (corruption.value / 100.0);
                    player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + lifeSteal));
                    showLifeStealEffect(player);
                    break;

                case BERSERKER_FURY:
                    // Bonus damage when below 50% HP
                    if (player.getHealth() < player.getMaxHealth() * 0.5) {
                        event.setDamage(event.getDamage() * (1 + corruption.value / 100.0));
                        showBerserkerEffect(player);
                    }
                    break;

                case PHASE_WALKER:
                    // Chance to completely ignore damage
                    if (ThreadLocalRandom.current().nextInt(100) < corruption.value) {
                        event.setCancelled(true);
                        showPhaseEffect(player);
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "👻 Phased through the attack!");
                    }
                    break;

                case SOUL_BURN:
                    // Spread elemental damage to nearby enemies
                    spreadElementalDamage(player, target, event.getDamage() * 0.5);
                    break;

                case VOID_TOUCHED:
                    // Bonus damage but reduced accuracy
                    event.setDamage(event.getDamage() * (1 + corruption.value / 100.0));
                    break;

                case VENGEFUL_SPIRIT:
                    // Occasionally attack random nearby players
                    if (ThreadLocalRandom.current().nextInt(100) < 5) {
                        attackRandomNearbyPlayer(player, target.getLocation());
                    }
                    break;
            }
        }
    }

    /**
     * Updates dynamic corruption effects
     */
    private void updateCorruptionEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check all equipped items for corruptions
            ItemStack[] equipment = {
                    player.getInventory().getItemInMainHand(),
                    player.getInventory().getItemInOffHand(),
                    player.getInventory().getHelmet(),
                    player.getInventory().getChestplate(),
                    player.getInventory().getLeggings(),
                    player.getInventory().getBoots()
            };

            for (ItemStack item : equipment) {
                if (item != null && isCorrupted(item)) {
                    List<ItemCorruption> corruptions = getItemCorruptions(item);

                    for (ItemCorruption corruption : corruptions) {
                        switch (corruption.effect) {
                            case MANA_BURN:
                                // Apply mana burn debuff
                                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 120, 0, true, false));
                                break;

                            case CHAOS_TOUCH:
                                // Random stat fluctuation (visual effect only for now)
                                if (ThreadLocalRandom.current().nextInt(100) < 10) {
                                    player.sendMessage(ChatColor.DARK_PURPLE + "🌪 Your corrupted gear fluctuates with chaotic energy!");
                                    showChaosEffect(player);
                                }
                                break;

                            case SOUL_DRAIN:
                                // Periodic HP drain
                                if (ThreadLocalRandom.current().nextInt(100) < 5) {
                                    double damage = player.getMaxHealth() * (corruption.penalty / 100.0);
                                    player.damage(Math.min(damage, player.getHealth() - 1)); // Never fatal
                                    showSoulDrainEffect(player);
                                }
                                break;
                        }
                    }
                }
            }
        }
    }

    // Helper methods for corruption effects
    private void spreadElementalDamage(Player player, LivingEntity target, double damage) {
        target.getWorld().getNearbyEntities(target.getLocation(), 3, 3, 3).forEach(entity -> {
            if (entity instanceof LivingEntity && entity != player && entity != target) {
                ((LivingEntity) entity).damage(damage, player);
                showSoulBurnEffect(entity.getLocation());
            }
        });

        player.sendMessage(ChatColor.BLUE + "🔥 Soul burn spreads to nearby enemies!");
    }

    private void attackRandomNearbyPlayer(Player corruptedPlayer, Location center) {
        List<Player> nearbyPlayers = new ArrayList<>();
        center.getWorld().getNearbyEntities(center, 10, 10, 10).forEach(entity -> {
            if (entity instanceof Player && entity != corruptedPlayer) {
                nearbyPlayers.add((Player) entity);
            }
        });

        if (!nearbyPlayers.isEmpty()) {
            Player target = nearbyPlayers.get(ThreadLocalRandom.current().nextInt(nearbyPlayers.size()));
            target.damage(5.0);

            corruptedPlayer.sendMessage(ChatColor.DARK_RED + "👹 Your vengeful spirit attacks " + target.getName() + "!");
            target.sendMessage(ChatColor.DARK_RED + "👹 " + corruptedPlayer.getName() + "'s vengeful spirit attacks you!");

            showVengefulSpiritEffect(target.getLocation());
        }
    }

    // Utility methods
    private CorruptionVialType getCorruptionVialType(ItemStack item) {
        if (!item.hasItemMeta()) return null;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String vialTypeName = container.get(keyCorruptionType, PersistentDataType.STRING);

        try {
            return CorruptionVialType.valueOf(vialTypeName);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isPurificationTome(ItemStack item) {
        return item.getType() == Material.BOOK &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(keyPurificationTome, PersistentDataType.STRING);
    }

    private boolean isValidItemForCorruption(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasLore();
    }

    private boolean isCorrupted(ItemStack item) {
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(keyCorruptionEffects, PersistentDataType.STRING);
    }

    private int getCorruptionCount(ItemStack item) {
        return getItemCorruptions(item).size();
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

    private CorruptionEffect selectRandomCorruption(boolean beneficial) {
        List<CorruptionEffect> eligibleEffects = new ArrayList<>();

        for (CorruptionEffect effect : CorruptionEffect.values()) {
            if (effect.isBeneficial() == beneficial) {
                eligibleEffects.add(effect);
            }
        }

        return eligibleEffects.get(ThreadLocalRandom.current().nextInt(eligibleEffects.size()));
    }

    private ItemCorruption generateCorruption(CorruptionEffect effect, int itemTier) {
        // Generate values based on item tier and effect base values
        int value = effect.getBaseValue() + (itemTier * ThreadLocalRandom.current().nextInt(1, 6));
        int penalty = effect.getBasePenalty() + ThreadLocalRandom.current().nextInt(0, itemTier * 2);

        // Cap values at reasonable limits
        value = Math.min(value, effect.getBaseValue() * 2);
        penalty = Math.min(penalty, effect.getBasePenalty() * 2);

        return new ItemCorruption(effect, value, penalty);
    }

    private List<ItemCorruption> getItemCorruptions(ItemStack item) {
        if (!isCorrupted(item)) return new ArrayList<>();

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String serialized = container.get(keyCorruptionEffects, PersistentDataType.STRING);

        return deserializeCorruptions(serialized);
    }

    private void storeCorruptionData(ItemMeta meta, List<ItemCorruption> corruptions) {
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyCorruptionEffects, PersistentDataType.STRING, serializeCorruptions(corruptions));
        container.set(keyCorruptionLevel, PersistentDataType.INTEGER, corruptions.size());
    }

    private String serializeCorruptions(List<ItemCorruption> corruptions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < corruptions.size(); i++) {
            if (i > 0) sb.append(";");
            ItemCorruption c = corruptions.get(i);
            sb.append(c.effect.name()).append(":").append(c.value).append(":").append(c.penalty);
        }
        return sb.toString();
    }

    private List<ItemCorruption> deserializeCorruptions(String serialized) {
        List<ItemCorruption> corruptions = new ArrayList<>();
        if (serialized == null || serialized.isEmpty()) return corruptions;

        String[] parts = serialized.split(";");
        for (String part : parts) {
            String[] corruptionData = part.split(":");
            if (corruptionData.length == 3) {
                try {
                    CorruptionEffect effect = CorruptionEffect.valueOf(corruptionData[0]);
                    int value = Integer.parseInt(corruptionData[1]);
                    int penalty = Integer.parseInt(corruptionData[2]);
                    corruptions.add(new ItemCorruption(effect, value, penalty));
                } catch (Exception e) {
                    // Skip invalid entries
                }
            }
        }

        return corruptions;
    }

    private int findCorruptionInsertionPoint(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            String line = ChatColor.stripColor(lore.get(i));
            if (line.toLowerCase().contains("common") || line.toLowerCase().contains("uncommon") ||
                    line.toLowerCase().contains("rare") || line.toLowerCase().contains("unique") ||
                    line.toLowerCase().contains("legendary")) {
                return i;
            }
        }
        return lore.size();
    }

    private void removeEmptyLines(List<String> lore) {
        lore.removeIf(line -> ChatColor.stripColor(line).trim().isEmpty());
    }

    private boolean hasOtherSpecialProperties(ItemStack item) {
        // Check for other reasons the item might glow (awakened, masterwork, etc.)
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(new NamespacedKey(plugin, "awakened"), PersistentDataType.INTEGER) ||
                container.has(new NamespacedKey(plugin, "masterwork_status"), PersistentDataType.INTEGER);
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
    private void showBeneficialCorruptionEffect(Player player) {
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.PURPLE);
    }

    private void showDetrimentalCorruptionEffect(Player player) {
        player.getWorld().spawnParticle(Particle.SMOKE_LARGE, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        ParticleUtil.showFailureEffect(player.getLocation());
    }

    private void showDestructionEffect(Player player) {
        player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation().add(0, 1, 0), 5, 2, 2, 2, 0);
        player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 1, 0), 50, 2, 2, 2, 0.1);
    }

    private void showPurificationEffect(Player player) {
        player.getWorld().spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.STAR, Color.WHITE);
    }

    private void showLifeStealEffect(Player player) {
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 5, 0.5, 0.5, 0.5, 0);
    }

    private void showBerserkerEffect(Player player) {
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 10, 1, 1, 1, 0.1);
    }

    private void showPhaseEffect(Player player) {
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
    }

    private void showSoulBurnEffect(Location location) {
        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 10, 0.5, 0.5, 0.5, 0.1);
    }

    private void showVengefulSpiritEffect(Location location) {
        location.getWorld().spawnParticle(Particle.SMOKE_LARGE, location, 15, 1, 1, 1, 0.1);
    }

    private void showChaosEffect(Player player) {
        player.getWorld().spawnParticle(Particle.WARPED_SPORE, player.getLocation().add(0, 1, 0), 15, 1, 1, 1, 0.1);
    }

    private void showSoulDrainEffect(Player player) {
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 10, 1, 1, 1, 0.1);
    }

    /**
     * Gets corruption effects from an item for external use
     */
    public List<ItemCorruption> getCorruptionEffects(ItemStack item) {
        return getItemCorruptions(item);
    }

    /**
     * Checks if an item has beneficial corruptions
     */
    public boolean hasBeneficialCorruptions(ItemStack item) {
        return getItemCorruptions(item).stream().anyMatch(c -> c.effect.isBeneficial());
    }

    /**
     * Gets corruption level (number of corruptions) on an item
     */
    public int getCorruptionLevel(ItemStack item) {
        return getCorruptionCount(item);
    }
}
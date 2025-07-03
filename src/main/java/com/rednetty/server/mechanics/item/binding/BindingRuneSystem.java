package com.rednetty.server.mechanics.item.binding;

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
import org.bukkit.event.player.PlayerItemHeldEvent;
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
 * Binding Rune System - Create item sets with synergy bonuses
 * Unity through enchantment - build themed character builds with set effects
 */
public class BindingRuneSystem implements Listener {
    private static BindingRuneSystem instance;
    private final Logger logger;
    private final YakRealms plugin;

    // Namespaced keys for persistent data
    private final NamespacedKey keyRuneType;
    private final NamespacedKey keyRuneId;
    private final NamespacedKey keySetBonusActive;
    private final NamespacedKey keyUnbindingSolvent;

    // Active set tracking for players
    private final Map<UUID, PlayerSetData> playerSets = new HashMap<>();
    private final Set<UUID> processingPlayers = new HashSet<>();

    // Rune types with their properties
    public enum RuneType {
        FIRE("Fire", ChatColor.RED, Color.RED, "🔥", Material.REDSTONE,
                "Ignite nearby enemies", PotionEffectType.FIRE_RESISTANCE),
        ICE("Ice", ChatColor.AQUA, Color.AQUA, "❄", Material.PACKED_ICE,
                "Slow attackers", PotionEffectType.SLOW),
        POISON("Poison", ChatColor.DARK_GREEN, Color.GREEN, "☠", Material.POISONOUS_POTATO,
                "Apply poison DOT", PotionEffectType.POISON),
        LIGHTNING("Lightning", ChatColor.YELLOW, Color.YELLOW, "⚡", Material.LIGHTNING_ROD,
                "Chain lightning on crits", PotionEffectType.SPEED),
        SHADOW("Shadow", ChatColor.DARK_PURPLE, Color.PURPLE, "🌙", Material.OBSIDIAN,
                "+25% vs players, +15% dodge", PotionEffectType.INVISIBILITY),
        LIGHT("Light", ChatColor.WHITE, Color.WHITE, "☀", Material.GLOWSTONE,
                "+25% vs monsters, +3 HP regen", PotionEffectType.REGENERATION);

        private final String displayName;
        private final ChatColor color;
        private final Color particleColor;
        private final String symbol;
        private final Material baseMaterial;
        private final String specialEffect;
        private final PotionEffectType associatedPotion;

        RuneType(String displayName, ChatColor color, Color particleColor, String symbol,
                 Material baseMaterial, String specialEffect, PotionEffectType associatedPotion) {
            this.displayName = displayName;
            this.color = color;
            this.particleColor = particleColor;
            this.symbol = symbol;
            this.baseMaterial = baseMaterial;
            this.specialEffect = specialEffect;
            this.associatedPotion = associatedPotion;
        }

        public String getDisplayName() { return displayName; }
        public ChatColor getColor() { return color; }
        public Color getParticleColor() { return particleColor; }
        public String getSymbol() { return symbol; }
        public Material getBaseMaterial() { return baseMaterial; }
        public String getSpecialEffect() { return specialEffect; }
        public PotionEffectType getAssociatedPotion() { return associatedPotion; }
    }

    // Set bonus levels
    public enum SetBonusLevel {
        TWO_PIECE(2, 10, 0, ""),
        THREE_PIECE(3, 15, 5, ""),
        FOUR_PIECE(4, 20, 10, "Special Effect");

        private final int requiredPieces;
        private final int elementalDamageBonus;
        private final int criticalHitBonus;
        private final String specialEffectName;

        SetBonusLevel(int requiredPieces, int elementalDamageBonus, int criticalHitBonus, String specialEffectName) {
            this.requiredPieces = requiredPieces;
            this.elementalDamageBonus = elementalDamageBonus;
            this.criticalHitBonus = criticalHitBonus;
            this.specialEffectName = specialEffectName;
        }

        public int getRequiredPieces() { return requiredPieces; }
        public int getElementalDamageBonus() { return elementalDamageBonus; }
        public int getCriticalHitBonus() { return criticalHitBonus; }
        public String getSpecialEffectName() { return specialEffectName; }
    }

    // Player set data tracking
    private static class PlayerSetData {
        final RuneType activeRuneType;
        final Set<UUID> boundItems;
        final SetBonusLevel currentLevel;
        final long lastUpdate;

        PlayerSetData(RuneType runeType, Set<UUID> boundItems, SetBonusLevel level) {
            this.activeRuneType = runeType;
            this.boundItems = new HashSet<>(boundItems);
            this.currentLevel = level;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    private BindingRuneSystem() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();

        // Initialize namespaced keys
        this.keyRuneType = new NamespacedKey(plugin, "rune_type");
        this.keyRuneId = new NamespacedKey(plugin, "rune_id");
        this.keySetBonusActive = new NamespacedKey(plugin, "set_bonus_active");
        this.keyUnbindingSolvent = new NamespacedKey(plugin, "unbinding_solvent");
    }

    public static BindingRuneSystem getInstance() {
        if (instance == null) {
            instance = new BindingRuneSystem();
        }
        return instance;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Start set bonus update task
        Bukkit.getScheduler().runTaskTimer(plugin, this::updatePlayerSetBonuses, 20L, 100L);

        logger.info("[BindingRuneSystem] Binding Rune System initialized");
    }

    /**
     * Creates a Binding Rune of the specified type
     */
    public ItemStack createBindingRune(RuneType runeType) {
        ItemStack rune = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = rune.getItemMeta();

        meta.setDisplayName(runeType.getColor() + runeType.getSymbol() + " " + ChatColor.BOLD +
                "Binding Rune of " + runeType.getDisplayName() + ChatColor.RESET + " " + runeType.getSymbol());

        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY + "Binds 2-4 items into a matched set with",
                ChatColor.GRAY + "escalating elemental bonuses:",
                "",
                ChatColor.AQUA + "2 pieces: " + ChatColor.WHITE + "+10% " + runeType.getDisplayName().toLowerCase() + " damage",
                ChatColor.AQUA + "3 pieces: " + ChatColor.WHITE + "+15% " + runeType.getDisplayName().toLowerCase() + " damage, +5% critical hit",
                ChatColor.AQUA + "4 pieces: " + ChatColor.WHITE + "+20% " + runeType.getDisplayName().toLowerCase() + " damage, +10% critical hit",
                ChatColor.GOLD + "           " + runeType.getSpecialEffect(),
                "",
                ChatColor.YELLOW + "Usage: Apply to 2-4 compatible items",
                ChatColor.RED + "Warning: Items become bound together",
                "",
                runeType.getColor() + runeType.getSymbol() + " " + ChatColor.ITALIC + "Unity through elemental power..."
        );
        meta.setLore(lore);

        // Store rune data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRuneType, PersistentDataType.STRING, runeType.name());
        container.set(keyRuneId, PersistentDataType.STRING, UUID.randomUUID().toString());

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        rune.setItemMeta(meta);

        // Add visual glow effect
        Enchants.addGlow(rune);

        return rune;
    }

    /**
     * Creates an Unbinding Solvent
     */
    public ItemStack createUnbindingSolvent() {
        ItemStack solvent = new ItemStack(Material.FERMENTED_SPIDER_EYE);
        ItemMeta meta = solvent.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_RED + "🧪 " + ChatColor.BOLD + "Unbinding Solvent" + ChatColor.RESET + " 🧪");

        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY + "Removes binding runes from items,",
                ChatColor.GRAY + "breaking set connections.",
                "",
                ChatColor.GREEN + "✓ 50% chance to recover the rune for reuse",
                ChatColor.RED + "✗ 50% chance the rune is destroyed",
                "",
                ChatColor.YELLOW + "Usage: Click on any bound item",
                ChatColor.AQUA + "Price: " + ChatColor.WHITE + "500g",
                "",
                ChatColor.DARK_RED + "🧪 " + ChatColor.ITALIC + "Dissolve the bonds of magic..."
        );
        meta.setLore(lore);

        // Store solvent type
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyUnbindingSolvent, PersistentDataType.STRING, "unbinding_solvent");

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        solvent.setItemMeta(meta);

        return solvent;
    }

    /**
     * Main event handler for binding rune interactions
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

        // Handle Binding Rune application
        RuneType runeType = getRuneType(cursor);
        if (runeType != null && isValidItemForBinding(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processRuneBinding(player, event, cursor, currentItem, runeType);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }

        // Handle Unbinding Solvent usage
        else if (isUnbindingSolvent(cursor) && isBoundItem(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                processRuneUnbinding(player, event, cursor, currentItem);
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }
    }

    /**
     * Processes binding rune application to an item
     */
    private void processRuneBinding(Player player, InventoryClickEvent event, ItemStack rune,
                                    ItemStack targetItem, RuneType runeType) {

        // Check if item is already bound
        if (isBoundItem(targetItem)) {
            player.sendMessage(ChatColor.RED + "This item is already bound to a set!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Get or create rune ID for this set
        String runeId = getRuneId(rune);

        // Check how many items are already bound to this rune
        int currentBoundCount = countBoundItems(player, runeType, runeId);

        if (currentBoundCount >= 4) {
            player.sendMessage(ChatColor.RED + "This rune set already has the maximum of 4 items!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Apply the binding
        ItemStack boundItem = applyRuneBinding(targetItem, runeType, runeId);

        // Consume the rune if this is the first application
        if (currentBoundCount == 0) {
            consumeItem(event, rune);
        }

        // Update the item
        event.setCurrentItem(boundItem);

        // Update player's set data
        updatePlayerSetData(player);

        // Success feedback
        int newBoundCount = currentBoundCount + 1;
        player.sendMessage(ChatColor.GREEN + "✓ Item bound to " + runeType.getColor() +
                runeType.getDisplayName() + " Set " + ChatColor.GREEN + "(" + newBoundCount + "/4)");

        // Check for set bonus activation
        SetBonusLevel bonusLevel = getSetBonusLevel(newBoundCount);
        if (bonusLevel != null) {
            player.sendMessage(ChatColor.GOLD + "🎊 " + runeType.getDisplayName() + " Set Bonus Activated! 🎊");
            player.sendMessage(ChatColor.YELLOW + getBonusDescription(runeType, bonusLevel));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        showBindingEffect(player, runeType);
    }

    /**
     * Processes unbinding solvent usage on a bound item
     */
    private void processRuneUnbinding(Player player, InventoryClickEvent event, ItemStack solvent, ItemStack boundItem) {
        RuneType runeType = getBoundRuneType(boundItem);
        String runeId = getBoundRuneId(boundItem);

        if (runeType == null || runeId == null) {
            player.sendMessage(ChatColor.RED + "This item is not properly bound!");
            return;
        }

        // Remove the binding
        ItemStack unboundItem = removeRuneBinding(boundItem);

        // Check for rune recovery (50% chance)
        boolean recoverRune = ThreadLocalRandom.current().nextBoolean();

        // Consume the solvent
        consumeItem(event, solvent);

        // Update the item
        event.setCurrentItem(unboundItem);

        // Update player's set data
        updatePlayerSetData(player);

        // Give recovered rune if successful
        if (recoverRune) {
            ItemStack recoveredRune = createBindingRune(runeType);
            giveItemToPlayer(player, recoveredRune);
            player.sendMessage(ChatColor.GREEN + "✓ Successfully recovered the " + runeType.getColor() +
                    runeType.getDisplayName() + " Rune!");
        } else {
            player.sendMessage(ChatColor.RED + "The rune was destroyed in the unbinding process.");
        }

        player.sendMessage(ChatColor.YELLOW + "Item unbound from " + runeType.getColor() +
                runeType.getDisplayName() + " Set");

        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
        showUnbindingEffect(player);
    }

    /**
     * Applies rune binding to an item
     */
    private ItemStack applyRuneBinding(ItemStack item, RuneType runeType, String runeId) {
        ItemStack boundItem = item.clone();
        ItemMeta meta = boundItem.getItemMeta();

        // Add set indicator to name
        String currentName = meta.getDisplayName();
        if (!currentName.contains("[" + runeType.getDisplayName() + " Set]")) {
            meta.setDisplayName(runeType.getColor() + "[" + runeType.getDisplayName() + " Set] " + currentName);
        }

        // Add set info to lore
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add(runeType.getColor() + runeType.getSymbol() + " " + runeType.getDisplayName() + " Set Piece " + runeType.getSymbol());
        lore.add(ChatColor.GRAY + "Part of an elemental set");

        meta.setLore(lore);

        // Store binding data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(keyRuneType, PersistentDataType.STRING, runeType.name());
        container.set(keyRuneId, PersistentDataType.STRING, runeId);

        boundItem.setItemMeta(meta);

        // Add visual glow
        Enchants.addGlow(boundItem);

        return boundItem;
    }

    /**
     * Removes rune binding from an item
     */
    private ItemStack removeRuneBinding(ItemStack item) {
        ItemStack unboundItem = item.clone();
        ItemMeta meta = unboundItem.getItemMeta();

        // Remove set indicator from name
        String currentName = meta.getDisplayName();
        currentName = currentName.replaceAll("\\[\\w+ Set\\]\\s*", "");
        meta.setDisplayName(currentName);

        // Remove set info from lore
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.removeIf(line -> {
            String cleanLine = ChatColor.stripColor(line);
            return cleanLine.contains("Set Piece") || cleanLine.contains("Part of an elemental set");
        });

        // Remove empty lines at the end
        while (!lore.isEmpty() && ChatColor.stripColor(lore.get(lore.size() - 1)).trim().isEmpty()) {
            lore.remove(lore.size() - 1);
        }

        meta.setLore(lore);

        // Remove binding data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(keyRuneType);
        container.remove(keyRuneId);

        unboundItem.setItemMeta(meta);

        // Remove glow effect
        Enchants.removeGlow(unboundItem);

        return unboundItem;
    }

    /**
     * Updates player's set data and activates bonuses
     */
    private void updatePlayerSetData(Player player) {
        Map<String, List<ItemStack>> setGroups = new HashMap<>();

        // Scan player inventory for bound items
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isBoundItem(item)) continue;

            RuneType runeType = getBoundRuneType(item);
            String runeId = getBoundRuneId(item);

            if (runeType != null && runeId != null) {
                String setKey = runeType.name() + ":" + runeId;
                setGroups.computeIfAbsent(setKey, k -> new ArrayList<>()).add(item);
            }
        }

        // Find the largest active set
        PlayerSetData newSetData = null;
        for (Map.Entry<String, List<ItemStack>> entry : setGroups.entrySet()) {
            List<ItemStack> items = entry.getValue();
            if (items.size() >= 2) { // Minimum 2 pieces for a set
                String[] parts = entry.getKey().split(":");
                RuneType runeType = RuneType.valueOf(parts[0]);

                Set<UUID> itemIds = new HashSet<>();
                for (ItemStack item : items) {
                    itemIds.add(UUID.fromString(item.toString())); // Simple UUID generation
                }

                SetBonusLevel level = getSetBonusLevel(items.size());
                if (level != null) {
                    if (newSetData == null || items.size() > newSetData.boundItems.size()) {
                        newSetData = new PlayerSetData(runeType, itemIds, level);
                    }
                }
            }
        }

        // Update player set data
        if (newSetData != null) {
            playerSets.put(player.getUniqueId(), newSetData);
        } else {
            playerSets.remove(player.getUniqueId());
        }
    }

    /**
     * Applies set bonuses to players
     */
    private void updatePlayerSetBonuses() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSetData setData = playerSets.get(player.getUniqueId());
            if (setData != null) {
                applySetBonuses(player, setData);
            }
        }
    }

    /**
     * Applies set bonuses to a player
     */
    private void applySetBonuses(Player player, PlayerSetData setData) {
        RuneType runeType = setData.activeRuneType;
        SetBonusLevel level = setData.currentLevel;

        // Apply passive bonuses (these would be handled in combat calculations)
        // For now, just apply visual effects and potion effects for some sets

        if (level == SetBonusLevel.FOUR_PIECE) {
            switch (runeType) {
                case LIGHT:
                    // +3 HP regen - apply regeneration effect
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, false));
                    break;
                case LIGHTNING:
                    // Speed boost for lightning set
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, false));
                    break;
                case SHADOW:
                    // Occasional invisibility for shadow set
                    if (ThreadLocalRandom.current().nextInt(100) < 5) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, true, false));
                    }
                    break;
            }
        }
    }

    /**
     * Handles set bonus special effects in combat
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player player = (Player) event.getDamager();
        PlayerSetData setData = playerSets.get(player.getUniqueId());

        if (setData == null || setData.currentLevel != SetBonusLevel.FOUR_PIECE) return;

        LivingEntity target = (LivingEntity) event.getEntity();
        RuneType runeType = setData.activeRuneType;

        switch (runeType) {
            case FIRE:
                // 15% chance to ignite nearby enemies
                if (ThreadLocalRandom.current().nextInt(100) < 15) {
                    igniteNearbyEnemies(player, target);
                }
                break;

            case ICE:
                // 20% chance to slow attackers
                if (ThreadLocalRandom.current().nextInt(100) < 20) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
                    showIceEffect(target.getLocation());
                }
                break;

            case POISON:
                // Apply poison DOT
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                showPoisonEffect(target.getLocation());
                break;

            case LIGHTNING:
                // 10% chance for chain lightning on critical hits
                if (ThreadLocalRandom.current().nextInt(100) < 10) {
                    triggerChainLightning(player, target);
                }
                break;

            case SHADOW:
                // Bonus damage vs players
                if (target instanceof Player) {
                    event.setDamage(event.getDamage() * 1.25);
                }
                break;

            case LIGHT:
                // Bonus damage vs monsters
                if (!(target instanceof Player)) {
                    event.setDamage(event.getDamage() * 1.25);
                }
                break;
        }
    }

    /**
     * Updates set bonuses when player changes equipment
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Delay the update to allow the item change to complete
        Bukkit.getScheduler().runTaskLater(plugin, () -> updatePlayerSetData(player), 1L);
    }

    // Special effect implementations
    private void igniteNearbyEnemies(Player player, LivingEntity center) {
        center.getWorld().getNearbyEntities(center.getLocation(), 5, 5, 5).forEach(entity -> {
            if (entity instanceof LivingEntity && entity != player && entity != center) {
                entity.setFireTicks(100);
                showFireEffect(entity.getLocation());
            }
        });

        player.sendMessage(ChatColor.RED + "🔥 Fire set: Ignited nearby enemies!");
    }

    private void triggerChainLightning(Player player, LivingEntity target) {
        List<LivingEntity> nearbyTargets = new ArrayList<>();
        target.getWorld().getNearbyEntities(target.getLocation(), 8, 8, 8).forEach(entity -> {
            if (entity instanceof LivingEntity && entity != player && entity != target) {
                nearbyTargets.add((LivingEntity) entity);
            }
        });

        if (!nearbyTargets.isEmpty()) {
            // Strike up to 3 additional targets
            int strikes = Math.min(3, nearbyTargets.size());
            for (int i = 0; i < strikes; i++) {
                LivingEntity chainTarget = nearbyTargets.get(i);
                chainTarget.damage(5.0, player);
                showLightningEffect(chainTarget.getLocation());
            }

            player.sendMessage(ChatColor.YELLOW + "⚡ Lightning set: Chain lightning struck " + strikes + " enemies!");
        }
    }

    // Utility methods
    private RuneType getRuneType(ItemStack item) {
        if (!item.hasItemMeta()) return null;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        String runeTypeName = container.get(keyRuneType, PersistentDataType.STRING);

        try {
            return RuneType.valueOf(runeTypeName);
        } catch (Exception e) {
            return null;
        }
    }

    private String getRuneId(ItemStack item) {
        if (!item.hasItemMeta()) return null;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.get(keyRuneId, PersistentDataType.STRING);
    }

    private boolean isValidItemForBinding(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().hasLore() &&
                !isBoundItem(item);
    }

    private boolean isBoundItem(ItemStack item) {
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(keyRuneType, PersistentDataType.STRING);
    }

    private boolean isUnbindingSolvent(ItemStack item) {
        return item.getType() == Material.FERMENTED_SPIDER_EYE &&
                item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer()
                        .has(keyUnbindingSolvent, PersistentDataType.STRING);
    }

    private RuneType getBoundRuneType(ItemStack item) {
        return getRuneType(item);
    }

    private String getBoundRuneId(ItemStack item) {
        return getRuneId(item);
    }

    private int countBoundItems(Player player, RuneType runeType, String runeId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !isBoundItem(item)) continue;

            RuneType itemRuneType = getBoundRuneType(item);
            String itemRuneId = getBoundRuneId(item);

            if (runeType == itemRuneType && runeId.equals(itemRuneId)) {
                count++;
            }
        }
        return count;
    }

    private SetBonusLevel getSetBonusLevel(int pieceCount) {
        for (SetBonusLevel level : SetBonusLevel.values()) {
            if (pieceCount >= level.getRequiredPieces()) {
                continue;
            }
            return SetBonusLevel.values()[Arrays.asList(SetBonusLevel.values()).indexOf(level) - 1];
        }
        return pieceCount >= 4 ? SetBonusLevel.FOUR_PIECE :
                pieceCount >= 3 ? SetBonusLevel.THREE_PIECE :
                        pieceCount >= 2 ? SetBonusLevel.TWO_PIECE : null;
    }

    private String getBonusDescription(RuneType runeType, SetBonusLevel level) {
        StringBuilder desc = new StringBuilder();
        desc.append("+").append(level.getElementalDamageBonus()).append("% ").append(runeType.getDisplayName().toLowerCase()).append(" damage");

        if (level.getCriticalHitBonus() > 0) {
            desc.append(", +").append(level.getCriticalHitBonus()).append("% critical hit");
        }

        if (level == SetBonusLevel.FOUR_PIECE) {
            desc.append(", ").append(runeType.getSpecialEffect());
        }

        return desc.toString();
    }

    private void consumeItem(InventoryClickEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            event.setCursor(item);
        } else {
            event.setCursor(null);
        }
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItem(player.getLocation(), item);
            player.sendMessage(ChatColor.YELLOW + "Your inventory is full! The rune was dropped on the ground.");
        }
    }

    // Visual effects
    private void showBindingEffect(Player player, RuneType runeType) {
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, runeType.getParticleColor());
    }

    private void showUnbindingEffect(Player player) {
        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, player.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
    }

    private void showFireEffect(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 10, 0.5, 0.5, 0.5, 0.1);
    }

    private void showIceEffect(Location location) {
        location.getWorld().spawnParticle(Particle.SNOWBALL, location, 10, 0.5, 0.5, 0.5, 0.1);
    }

    private void showPoisonEffect(Location location) {
        location.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, location, 10, 0.5, 0.5, 0.5, 0.1);
    }

    private void showLightningEffect(Location location) {
        location.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, location, 20, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Gets active set data for a player
     */
    public PlayerSetData getPlayerSetData(Player player) {
        return playerSets.get(player.getUniqueId());
    }

    /**
     * Gets set bonus damage multiplier for a player
     */
    public double getSetDamageMultiplier(Player player, RuneType damageType) {
        PlayerSetData setData = playerSets.get(player.getUniqueId());
        if (setData == null || setData.activeRuneType != damageType) {
            return 1.0;
        }

        return 1.0 + (setData.currentLevel.getElementalDamageBonus() / 100.0);
    }

    /**
     * Gets set bonus critical hit chance for a player
     */
    public double getSetCriticalBonus(Player player) {
        PlayerSetData setData = playerSets.get(player.getUniqueId());
        if (setData == null) {
            return 0.0;
        }

        return setData.currentLevel.getCriticalHitBonus() / 100.0;
    }
}
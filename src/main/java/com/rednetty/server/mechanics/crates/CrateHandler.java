package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.utils.text.TextUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Enhanced event handler for crate-related interactions using 1.20.4 features
 * Provides comprehensive event handling with improved UX and error handling
 */
public class CrateHandler implements Listener {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;
    private final CrateFactory crateFactory;
    private final EconomyManager economyManager;

    // Enhanced spam prevention and cooldown management
    private final Set<UUID> processingClicks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClickTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastScrapTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clickCounts = new ConcurrentHashMap<>();

    // Enhanced cooldown settings
    private static final long CLICK_COOLDOWN_MS = 500; // 0.5 second between clicks
    private static final long SCRAP_COOLDOWN_MS = 2000; // 2 seconds between scraps
    private static final long SPAM_THRESHOLD_MS = 5000; // 5 seconds for spam detection
    private static final int MAX_CLICKS_PER_THRESHOLD = 10; // Max clicks in threshold period

    // Animation inventory tracking
    private final Map<UUID, String> animationInventories = new ConcurrentHashMap<>();
    private static final String ANIMATION_INVENTORY_TITLE = "✦ Mystical Crate Opening ✦";

    /**
     * Constructor
     */
    public CrateHandler() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
        this.crateFactory = crateManager.getCrateFactory();
        this.economyManager = EconomyManager.getInstance();

        // Start cleanup task for expired data
        startCleanupTask();
    }

    /**
     * Enhanced crate opening via inventory clicking with modern UX
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if this is an animation inventory
        if (isAnimationInventory(event.getView().getTitle())) {
            event.setCancelled(true);
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();

        // Handle crate inventory interactions
        if (isPlayerInventoryClick(event) && isCrateItem(clickedItem)) {
            event.setCancelled(true);
            handleCrateInventoryClick(player, clickedItem, event);
        }
        // Handle crate key interactions
        else if (isPlayerInventoryClick(event) && isCrateKeyInteraction(event)) {
            event.setCancelled(true);
            handleCrateKeyUsage(player, event);
        }
    }

    /**
     * Enhanced crate interactions via right-clicking in hand
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isCrateItem(item)) {
            return;
        }

        event.setCancelled(true);

        // Enhanced duel check (if duel system exists)
        if (isPlayerInDuel(player)) {
            sendDuelMessage(player);
            return;
        }

        // Handle right-click for enhanced scrap value
        handleEnhancedCrateScrapValue(player, item);
    }

    /**
     * Handles animation inventory close events
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Check if closing an animation inventory
        if (isAnimationInventory(event.getView().getTitle())) {
            UUID playerId = player.getUniqueId();
            animationInventories.remove(playerId);

            // If player is still in processing, this might be an unexpected close
            if (crateManager.getProcessingPlayers().contains(playerId)) {
                // Send notification but don't interrupt the process
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.YELLOW + "⚠ Crate is still opening in the background..."));
            }
        }
    }

    /**
     * Enhanced player join handling
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clean up any stale data for this player
        processingClicks.remove(playerId);
        lastClickTimes.remove(playerId);
        lastScrapTimes.remove(playerId);
        clickCounts.remove(playerId);
        animationInventories.remove(playerId);

        // Send welcome message about crates if they have any
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndNotifyCrates(player);
            }
        }.runTaskLater(plugin, 40L); // 2 seconds after join
    }

    /**
     * Enhanced player quit handling
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Clean up all player data
        processingClicks.remove(playerId);
        lastClickTimes.remove(playerId);
        lastScrapTimes.remove(playerId);
        clickCounts.remove(playerId);
        animationInventories.remove(playerId);

        // Cancel any active animations
        crateManager.getAnimationManager().cancelAnimation(playerId);
    }

    /**
     * Enhanced crate inventory click handler with modern UX
     */
    private void handleCrateInventoryClick(Player player, ItemStack crateItem, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();

        // Enhanced spam prevention
        if (!checkEnhancedCooldown(player, CLICK_COOLDOWN_MS, lastClickTimes)) {
            return;
        }

        // Check if player is already processing a crate
        if (crateManager.getProcessingPlayers().contains(playerId)) {
            sendProcessingMessage(player);
            return;
        }

        // Enhanced duel check
        if (isPlayerInDuel(player)) {
            sendDuelMessage(player);
            return;
        }

        // Enhanced inventory space check
        if (!hasEnhancedInventorySpace(player)) {
            sendEnhancedInventoryFullMessage(player);
            return;
        }

        // Check if crate is locked
        if (isCrateLocked(crateItem)) {
            sendLockedCrateMessage(player);
            return;
        }

        processingClicks.add(playerId);

        try {
            // Enhanced opening attempt with user feedback
            sendOpeningStartMessage(player, crateItem);

            boolean success = crateManager.openCrate(player, crateItem);

            if (success) {
                // Consume the crate item with enhanced feedback
                consumeCrateItem(event, crateItem);

                // Track the animation inventory
                animationInventories.put(playerId, ANIMATION_INVENTORY_TITLE);

                logger.fine("Player " + player.getName() + " successfully opened a crate");
            } else {
                sendOpeningFailedMessage(player);
            }

        } catch (Exception e) {
            logger.severe("Error handling crate inventory click for player " + player.getName() + ": " + e.getMessage());
            sendErrorMessage(player, "An unexpected error occurred while opening the crate!");
        } finally {
            processingClicks.remove(playerId);
        }
    }

    /**
     * Enhanced crate key usage handler
     */
    private void handleCrateKeyUsage(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (!crateFactory.isCrateKey(cursor) || !isCrateItem(target)) {
            return;
        }

        if (!isCrateLocked(target)) {
            player.sendMessage(ChatColor.RED + "✗ This crate is not locked!");
            playErrorSound(player);
            return;
        }

        // Enhanced validation
        CrateType crateType = crateFactory.determineCrateType(target);
        if (crateType == null) {
            player.sendMessage(ChatColor.RED + "✗ Invalid crate type!");
            playErrorSound(player);
            return;
        }

        // Enhanced key validation (placeholder for future key type checking)
        if (!validateCrateKey(cursor, crateType)) {
            player.sendMessage(ChatColor.RED + "✗ This key cannot unlock this crate type!");
            playErrorSound(player);
            return;
        }

        // Unlock the crate with enhanced effects
        ItemStack unlockedCrate = unlockCrateWithEffects(player, target);
        event.setCurrentItem(unlockedCrate);

        // Consume the key
        consumeKeyItem(event, cursor);

        // Enhanced success feedback
        sendKeySuccessMessage(player, crateType);

        logger.fine("Player " + player.getName() + " unlocked a " + crateType + " crate");
    }

    /**
     * Enhanced crate scrap value handler with modern UX
     */
    private void handleEnhancedCrateScrapValue(Player player, ItemStack crateItem) {
        UUID playerId = player.getUniqueId();

        // Enhanced cooldown check for scrapping
        if (!checkEnhancedCooldown(player, SCRAP_COOLDOWN_MS, lastScrapTimes)) {
            long remaining = getRemainingCooldown(playerId, SCRAP_COOLDOWN_MS, lastScrapTimes);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.YELLOW + "⏳ Scrap cooldown: " + (remaining / 1000) + "s"));
            return;
        }

        CrateType crateType = crateFactory.determineCrateType(crateItem);
        if (crateType == null) {
            player.sendMessage(ChatColor.RED + "✗ Invalid crate type!");
            playErrorSound(player);
            return;
        }

        // Enhanced scrap value calculation
        int scrapValue = calculateEnhancedScrapValue(crateType);

        // Show confirmation for valuable crates
        if (crateType.getTier() >= 4) {
            sendScrapConfirmation(player, crateType, scrapValue);
            return;
        }

        // Perform the scrap operation
        performScrapOperation(player, crateItem, crateType, scrapValue);
    }

    /**
     * Performs the actual scrap operation with enhanced effects
     */
    private void performScrapOperation(Player player, ItemStack crateItem, CrateType crateType, int scrapValue) {
        // Try to give gems to player
        var result = economyManager.addGems(player.getUniqueId(), scrapValue);

        if (result.isSuccess()) {
            // Remove the crate from hand
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getAmount() > 1) {
                handItem.setAmount(handItem.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            // Enhanced success message with animations
            sendEnhancedScrapSuccessMessage(player, crateType, scrapValue);

            // Enhanced effects
            playScrapEffects(player, crateType);

            // Update scrap cooldown
            lastScrapTimes.put(player.getUniqueId(), System.currentTimeMillis());

        } else {
            sendScrapFailedMessage(player);
        }
    }

    /**
     * Enhanced validation and utility methods
     */
    private boolean checkEnhancedCooldown(Player player, long cooldownMs, Map<UUID, Long> timeMap) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = timeMap.get(playerId);

        if (lastTime != null && (currentTime - lastTime) < cooldownMs) {
            return false;
        }

        // Update click count for spam detection
        updateClickCount(playerId, currentTime);

        // Check for spam
        if (isPlayerSpamming(playerId)) {
            sendSpamWarning(player);
            return false;
        }

        timeMap.put(playerId, currentTime);
        return true;
    }

    private void updateClickCount(UUID playerId, long currentTime) {
        clickCounts.merge(playerId, 1, Integer::sum);

        // Reset count after threshold period
        new BukkitRunnable() {
            @Override
            public void run() {
                clickCounts.computeIfPresent(playerId, (id, count) -> Math.max(0, count - 1));
            }
        }.runTaskLater(plugin, SPAM_THRESHOLD_MS / 50); // Convert to ticks
    }

    private boolean isPlayerSpamming(UUID playerId) {
        Integer count = clickCounts.get(playerId);
        return count != null && count > MAX_CLICKS_PER_THRESHOLD;
    }

    private long getRemainingCooldown(UUID playerId, long cooldownMs, Map<UUID, Long> timeMap) {
        Long lastTime = timeMap.get(playerId);
        if (lastTime == null) return 0;

        long elapsed = System.currentTimeMillis() - lastTime;
        return Math.max(0, cooldownMs - elapsed);
    }

    private boolean hasEnhancedInventorySpace(Player player) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots >= 3; // Require at least 3 empty slots
    }

    private boolean isAnimationInventory(String title) {
        return title != null && title.contains("Crate Opening");
    }

    private boolean isPlayerInventoryClick(InventoryClickEvent event) {
        return event.getInventory().getType() == InventoryType.PLAYER &&
                event.getSlotType() != InventoryType.SlotType.ARMOR;
    }

    private boolean isCrateItem(ItemStack item) {
        return item != null && crateFactory.isCrate(item);
    }

    private boolean isCrateKeyInteraction(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        return crateFactory.isCrateKey(cursor) && isCrateItem(target);
    }

    private boolean isCrateLocked(ItemStack crateItem) {
        if (crateItem == null || !crateItem.hasItemMeta()) {
            return false;
        }

        // Check NBT data for locked status
        com.rednetty.server.utils.nbt.NBTAccessor nbt =
                new com.rednetty.server.utils.nbt.NBTAccessor(crateItem);
        if (nbt.hasKey("locked")) {
            return nbt.getBoolean("locked");
        }

        // Check lore for locked indicator
        if (crateItem.getItemMeta().hasLore()) {
            return crateItem.getItemMeta().getLore().stream()
                    .anyMatch(line -> line.contains("LOCKED"));
        }

        return false;
    }

    private boolean validateCrateKey(ItemStack key, CrateType crateType) {
        // Enhanced key validation - placeholder for future implementation
        // For now, all keys can open all crates
        return true;
    }

    private boolean isPlayerInDuel(Player player) {
        // Enhanced duel check - placeholder for integration with duel system
        // return Duels.duelers.containsKey(player);
        return false;
    }

    /**
     * Enhanced item manipulation methods
     */
    private void consumeCrateItem(InventoryClickEvent event, ItemStack crateItem) {
        if (crateItem.getAmount() > 1) {
            crateItem.setAmount(crateItem.getAmount() - 1);
        } else {
            event.setCurrentItem(new ItemStack(Material.AIR));
        }
    }

    private void consumeKeyItem(InventoryClickEvent event, ItemStack keyItem) {
        if (keyItem.getAmount() > 1) {
            keyItem.setAmount(keyItem.getAmount() - 1);
        } else {
            event.setCursor(new ItemStack(Material.AIR));
        }
    }

    private ItemStack unlockCrateWithEffects(Player player, ItemStack lockedCrate) {
        ItemStack unlocked = lockedCrate.clone();

        // Remove locked status from NBT
        com.rednetty.server.utils.nbt.NBTAccessor nbt =
                new com.rednetty.server.utils.nbt.NBTAccessor(unlocked);
        nbt.setBoolean("locked", false);

        // Remove locked lines from lore
        if (unlocked.hasItemMeta() && unlocked.getItemMeta().hasLore()) {
            java.util.List<String> lore = new java.util.ArrayList<>(unlocked.getItemMeta().getLore());
            lore.removeIf(line -> line.contains("LOCKED") || line.contains("Requires a"));

            // Remove trailing empty lines
            while (!lore.isEmpty() && lore.get(lore.size() - 1).trim().isEmpty()) {
                lore.remove(lore.size() - 1);
            }

            ItemMeta meta = unlocked.getItemMeta();
            meta.setLore(lore);
            unlocked.setItemMeta(meta);
        }

        // Play unlock effects
        playUnlockEffects(player);

        return nbt.update();
    }

    /**
     * Enhanced scrap value calculation
     */
    private int calculateEnhancedScrapValue(CrateType crateType) {
        int baseValue = switch (crateType.getTier()) {
            case 1 -> 75;   // Increased from 50
            case 2 -> 150;  // Increased from 125
            case 3 -> 300;  // Increased from 250
            case 4 -> 600;  // Increased from 500
            case 5 -> 1250; // Increased from 1000
            case 6 -> 2500; // Increased from 2000
            default -> 50;
        };

        // Halloween crates are worth 75% more (increased from 50%)
        if (crateType.isHalloween()) {
            baseValue = (int) (baseValue * 1.75);
        }

        // Add small random variation (±10%)
        int variation = (int) (baseValue * 0.1);
        int randomBonus = (int) (Math.random() * variation * 2) - variation;

        return baseValue + randomBonus;
    }

    /**
     * Enhanced message sending methods
     */
    private void sendProcessingMessage(Player player) {
        player.sendMessage(ChatColor.RED + "⚡ You are already opening a mystical crate!");
        player.sendMessage(ChatColor.GRAY + "Please wait for the current ritual to complete...");
        playErrorSound(player);
    }

    private void sendDuelMessage(Player player) {
        player.sendMessage(ChatColor.RED + "⚔ You cannot open crates while in a duel!");
        player.sendMessage(ChatColor.GRAY + "Finish your duel first, then return to your mystical pursuits.");
        playErrorSound(player);
    }

    private void sendEnhancedInventoryFullMessage(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "MYSTICAL OVERFLOW PROTECTION" + ChatColor.RED + " ⚠");
        player.sendMessage(ChatColor.GRAY + "Your inventory lacks space for the mystical energies!");
        player.sendMessage(ChatColor.YELLOW + "Please ensure at least 3 empty slots and try again.");
        player.sendMessage("");

        // Enhanced suggestion with clickable elements
        net.md_5.bungee.api.chat.TextComponent bankTip =
                new net.md_5.bungee.api.chat.TextComponent(ChatColor.AQUA + "» Quick Fix: Click here to open your bank! «");
        bankTip.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/bank"));
        bankTip.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder(ChatColor.YELLOW + "Click to open your bank storage!").create()));

        player.spigot().sendMessage(bankTip);

        playErrorSound(player);
    }

    private void sendLockedCrateMessage(Player player) {
        player.sendMessage(ChatColor.RED + "🔒 " + ChatColor.BOLD + "MYSTICAL SEAL DETECTED" + ChatColor.RED + " 🔒");
        player.sendMessage(ChatColor.GRAY + "This crate is bound by ancient seals!");
        player.sendMessage(ChatColor.YELLOW + "You need a " + ChatColor.AQUA + "Crate Key" +
                ChatColor.YELLOW + " to break the enchantment.");

        // Play locked sound
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
    }

    private void sendOpeningStartMessage(Player player, ItemStack crateItem) {
        CrateType crateType = crateFactory.determineCrateType(crateItem);
        if (crateType != null) {
            String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.AQUA + "✨ Preparing to unseal " + crateName + " Crate... ✨"));
        }
    }

    private void sendOpeningFailedMessage(Player player) {
        player.sendMessage(ChatColor.RED + "✗ The mystical energies rejected the opening ritual!");
        player.sendMessage(ChatColor.GRAY + "Please try again in a moment...");
        playErrorSound(player);
    }

    private void sendKeySuccessMessage(Player player, CrateType crateType) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "🔓 " + ChatColor.BOLD + "MYSTICAL SEAL BROKEN!" + ChatColor.GREEN + " 🔓");

        String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
        player.sendMessage(ChatColor.WHITE + "Your " + ChatColor.YELLOW + crateName + ChatColor.WHITE +
                " Crate has been successfully unsealed!");
        player.sendMessage(ChatColor.GRAY + "The ancient bindings have been dissolved by your key's power.");
        player.sendMessage("");

        // Success sounds
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    private void sendScrapConfirmation(Player player, CrateType crateType, int scrapValue) {
        // For high-tier crates, require confirmation (placeholder implementation)
        player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "HIGH VALUE CRATE DETECTED");

        String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
        player.sendMessage(ChatColor.WHITE + "Scrapping " + ChatColor.YELLOW + crateName + ChatColor.WHITE +
                " Crate will give " + ChatColor.GREEN + TextUtil.formatNumber(scrapValue) + "g");
        player.sendMessage(ChatColor.GRAY + "Right-click again within 5 seconds to confirm.");

        // This would require a confirmation system - for now, just proceed
        performScrapOperation(player, player.getInventory().getItemInMainHand(), crateType, scrapValue);
    }

    private void sendEnhancedScrapSuccessMessage(Player player, CrateType crateType, int scrapValue) {
        player.sendMessage("");
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.YELLOW + "⚒ " + ChatColor.BOLD + "CRATE DISASSEMBLED" + ChatColor.YELLOW + " ⚒"
        ));

        String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.GRAY + crateName + " Crate • Tier " + crateType.getTier()
        ));
        player.sendMessage(TextUtil.getCenteredMessage(
                ChatColor.WHITE + "Salvaged: " + ChatColor.GREEN + TextUtil.formatNumber(scrapValue) + "g"
        ));

        if (crateType.isHalloween()) {
            player.sendMessage(TextUtil.getCenteredMessage(
                    ChatColor.GOLD + "🎃 Halloween Bonus Applied! 🎃"
            ));
        }

        player.sendMessage("");

        // Enhanced action bar feedback
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GREEN + "+" + TextUtil.formatNumber(scrapValue) + "g " +
                        ChatColor.GRAY + "• Balance updated!"));
    }

    private void sendScrapFailedMessage(Player player) {
        player.sendMessage(ChatColor.RED + "✗ Failed to salvage crate materials!");
        player.sendMessage(ChatColor.GRAY + "The mystical forge seems to be malfunctioning...");
        playErrorSound(player);
    }

    private void sendSpamWarning(Player player) {
        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "SLOW DOWN!" + ChatColor.RED + " ⚠");
        player.sendMessage(ChatColor.GRAY + "The mystical energies need time to stabilize between actions.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.3f);
    }

    private void sendErrorMessage(Player player, String message) {
        player.sendMessage(ChatColor.RED + "✗ " + ChatColor.BOLD + "ERROR" + ChatColor.RED + " ✗");
        player.sendMessage(ChatColor.GRAY + message);
        playErrorSound(player);
    }

    /**
     * Enhanced sound and effect methods
     */
    private void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    private void playUnlockEffects(Player player) {
        // Sound effects
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Particle effects
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 2, 0), 15, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.05);
    }

    private void playScrapEffects(Player player, CrateType crateType) {
        // Enhanced scrap sound
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.5f);

        // Tier-based particle effects
        Particle scrapParticle = switch (crateType.getTier()) {
            case 1, 2 -> Particle.CRIT;
            case 3, 4 -> Particle.ENCHANTMENT_TABLE;
            case 5, 6 -> Particle.VILLAGER_HAPPY;
            default -> Particle.CRIT;
        };

        player.getWorld().spawnParticle(scrapParticle,
                player.getLocation().add(0, 1.5, 0), 8, 0.5, 0.5, 0.5, 0.1);

        // Halloween special effects
        if (crateType.isHalloween()) {
            player.getWorld().spawnParticle(Particle.FLAME,
                    player.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /**
     * Player notification methods
     */
    private void checkAndNotifyCrates(Player player) {
        // Check if player has any crates in inventory
        int crateCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCrateItem(item)) {
                crateCount += item.getAmount();
            }
        }

        if (crateCount > 0) {
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "✦ " + ChatColor.BOLD + "MYSTICAL DETECTION" + ChatColor.AQUA + " ✦");
            player.sendMessage(ChatColor.WHITE + "You have " + ChatColor.YELLOW + crateCount +
                    ChatColor.WHITE + " mystical crates ready to open!");
            player.sendMessage(ChatColor.GRAY + "Click them in your inventory to begin the unsealing ritual.");
            player.sendMessage("");

            // Play notification sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.5f);
        }
    }

    /**
     * Cleanup and maintenance
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L); // Every 30 seconds
    }

    private void performCleanup() {
        long currentTime = System.currentTimeMillis();

        // Clean up old click times (older than 5 minutes)
        lastClickTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 300000);

        // Clean up old scrap times (older than 5 minutes)
        lastScrapTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 300000);

        // Clean up old click counts (older than spam threshold)
        clickCounts.entrySet().removeIf(entry -> entry.getValue() <= 0);

        // Clean up processing clicks for offline players
        processingClicks.removeIf(playerId -> {
            Player player = plugin.getServer().getPlayer(playerId);
            return player == null || !player.isOnline();
        });

        // Clean up animation inventories for offline players
        animationInventories.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }

    /**
     * Gets handler statistics for debugging
     */
    public Map<String, Object> getHandlerStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("processingClicks", processingClicks.size());
        stats.put("lastClickTimes", lastClickTimes.size());
        stats.put("lastScrapTimes", lastScrapTimes.size());
        stats.put("clickCounts", clickCounts.size());
        stats.put("animationInventories", animationInventories.size());
        stats.put("clickCooldownMs", CLICK_COOLDOWN_MS);
        stats.put("scrapCooldownMs", SCRAP_COOLDOWN_MS);
        stats.put("spamThresholdMs", SPAM_THRESHOLD_MS);
        stats.put("maxClicksPerThreshold", MAX_CLICKS_PER_THRESHOLD);
        return stats;
    }

    /**
     * Enhanced cleanup method for shutdown
     */
    public void cleanup() {
        // Clear all tracking data
        processingClicks.clear();
        lastClickTimes.clear();
        lastScrapTimes.clear();
        clickCounts.clear();
        animationInventories.clear();

        logger.fine("Crate handler cleanup completed");
    }
}
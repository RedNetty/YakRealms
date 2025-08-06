package com.rednetty.server.mechanics.item.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
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
import org.bukkit.event.inventory.ClickType;
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
 * Event handler for crate-related interactions using 1.20.4 features.
 * Provides comprehensive event handling with improved UX and error handling.
 */
public class CrateHandler implements Listener {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;
    private final CrateFactory crateFactory;
    private final EconomyManager economyManager;

    // Spam prevention and cooldown management
    private final Set<UUID> processingClicks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastClickTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastScrapTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> clickCounts = new ConcurrentHashMap<>();

    // Animation inventory tracking
    private final Map<UUID, String> animationInventories = new ConcurrentHashMap<>();

    // Cooldown and limit constants
    private static final long CLICK_COOLDOWN_MS = 500;
    private static final long SCRAP_COOLDOWN_MS = 2000;
    private static final long SPAM_THRESHOLD_MS = 5000;
    private static final int MAX_CLICKS_PER_THRESHOLD = 10;
    private static final int REQUIRED_INVENTORY_SLOTS = 3;
    private static final long CLEANUP_INTERVAL_TICKS = 600L;
    private static final long DATA_CLEANUP_AGE_MS = 300000L;
    private static final long PLAYER_JOIN_DELAY_TICKS = 40L;
    private static final long INVENTORY_UPDATE_DELAY_TICKS = 1L;

    // Animation inventory identifier
    private static final String ANIMATION_INVENTORY_TITLE = "✦ Mystical Crate Opening ✦";

    // Message formatting constants
    private static final String MESSAGE_PREFIX_ERROR = ChatColor.RED + "✗ " + ChatColor.BOLD + "ERROR" + ChatColor.RED + " ✗";
    private static final String MESSAGE_PREFIX_WARNING = ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "WARNING" + ChatColor.YELLOW + " ⚠";
    private static final String MESSAGE_PREFIX_SUCCESS = ChatColor.GREEN + "✓ " + ChatColor.BOLD + "SUCCESS" + ChatColor.GREEN + " ✓";

    /**
     * Constructor for CrateHandler.
     */
    public CrateHandler() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
        this.crateFactory = crateManager.getCrateFactory();
        this.economyManager = EconomyManager.getInstance();

        startCleanupTask();
    }

    /**
     * Handles crate opening via inventory clicking with modern UX.
     * Only intercepts specific click types to allow normal inventory manipulation.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Always cancel clicks in animation inventories
        if (isAnimationInventory(event.getView().getTitle())) {
            event.setCancelled(true);
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        logInventoryClick(player, event, clickedItem, cursorItem);

        // Handle crate key interactions (drag and drop)
        if (isCrateKeyInteraction(event)) {
            event.setCancelled(true);
            handleCrateKeyUsage(player, event);
            return;
        }

        // Handle crate opening - only for LEFT clicks to preserve inventory manipulation
        if (shouldHandleCrateOpening(event, clickedItem)) {
            event.setCancelled(true);
            handleCrateInventoryClick(player, clickedItem, event);
        }
    }

    /**
     * Handles crate interactions via right-clicking in hand.
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

        if (isPlayerInDuel(player)) {
            sendDuelMessage(player);
            return;
        }

        handleCrateScrapValue(player, item);
    }

    /**
     * Handles animation inventory close events.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (isAnimationInventory(event.getView().getTitle())) {
            UUID playerId = player.getUniqueId();
            animationInventories.remove(playerId);

            if (crateManager.getProcessingPlayers().contains(playerId)) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.YELLOW + "⚠ Crate is still opening in the background..."));
            }
        }
    }

    /**
     * Handles player join events.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        cleanupPlayerData(playerId);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndNotifyCrates(player);
            }
        }.runTaskLater(plugin, PLAYER_JOIN_DELAY_TICKS);
    }

    /**
     * Handles player quit events.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        cleanupPlayerData(playerId);
        crateManager.getAnimationManager().cancelAnimation(playerId);
    }

    /**
     * Handles crate inventory click with modern UX.
     */
    private void handleCrateInventoryClick(Player player, ItemStack crateItem, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();

        if (!checkCooldown(player, CLICK_COOLDOWN_MS, lastClickTimes)) {
            return;
        }

        if (crateManager.getProcessingPlayers().contains(playerId)) {
            sendProcessingMessage(player);
            return;
        }

        if (isPlayerInDuel(player)) {
            sendDuelMessage(player);
            return;
        }

        if (!hasInventorySpace(player)) {
            sendInventoryFullMessage(player);
            return;
        }

        if (isCrateLocked(crateItem)) {
            sendLockedCrateMessage(player);
            return;
        }

        processingClicks.add(playerId);

        try {
            sendOpeningStartMessage(player, crateItem);

            boolean success = crateManager.openCrate(player, crateItem);

            if (success) {
                consumeCrateItem(event, crateItem);
                scheduleInventoryUpdate(player);
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
     * Handles crate key usage.
     */
    private void handleCrateKeyUsage(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (!crateFactory.isCrateKey(cursor) || !isCrateItem(target)) {
            return;
        }

        if (!isCrateLocked(target)) {
            sendMessage(player, ChatColor.RED + "This crate is not locked!", Sound.BLOCK_NOTE_BLOCK_BASS);
            return;
        }

        CrateType crateType = crateFactory.determineCrateType(target);
        if (crateType == null) {
            sendMessage(player, ChatColor.RED + "Invalid crate type!", Sound.BLOCK_NOTE_BLOCK_BASS);
            return;
        }

        if (!validateCrateKey(cursor, crateType)) {
            sendMessage(player, ChatColor.RED + "This key cannot unlock this crate type!", Sound.BLOCK_NOTE_BLOCK_BASS);
            return;
        }

        ItemStack unlockedCrate = unlockCrateWithEffects(player, target);
        event.setCurrentItem(unlockedCrate);
        consumeKeyItem(event, cursor);
        scheduleInventoryUpdate(player);
        sendKeySuccessMessage(player, crateType);

        logger.fine("Player " + player.getName() + " unlocked a " + crateType + " crate");
    }

    /**
     * Handles crate scrap value with modern UX.
     */
    private void handleCrateScrapValue(Player player, ItemStack crateItem) {
        UUID playerId = player.getUniqueId();

        if (!checkCooldown(player, SCRAP_COOLDOWN_MS, lastScrapTimes)) {
            long remaining = getRemainingCooldown(playerId, SCRAP_COOLDOWN_MS, lastScrapTimes);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.YELLOW + "⏳ Scrap cooldown: " + (remaining / 1000) + "s"));
            return;
        }

        CrateType crateType = crateFactory.determineCrateType(crateItem);
        if (crateType == null) {
            sendMessage(player, ChatColor.RED + "Invalid crate type!", Sound.BLOCK_NOTE_BLOCK_BASS);
            return;
        }

        int scrapValue = calculateScrapValue(crateType);

        if (crateType.getTier() >= 4) {
            sendScrapConfirmation(player, crateType, scrapValue);
            return;
        }

        performScrapOperation(player, crateItem, crateType, scrapValue);
    }

    /**
     * Performs the actual scrap operation with effects.
     */
    private void performScrapOperation(Player player, ItemStack crateItem, CrateType crateType, int scrapValue) {
        var result = economyManager.addBankGems(player.getUniqueId(), scrapValue);

        if (result.isSuccess()) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getAmount() > 1) {
                ItemStack reducedItem = handItem.clone();
                reducedItem.setAmount(handItem.getAmount() - 1);
                player.getInventory().setItemInMainHand(reducedItem);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            scheduleInventoryUpdate(player);
            sendScrapSuccessMessage(player, crateType, scrapValue);
            playScrapEffects(player, crateType);
            lastScrapTimes.put(player.getUniqueId(), System.currentTimeMillis());

        } else {
            sendScrapFailedMessage(player);
        }
    }

    /**
     * Validation and utility methods
     */
    private boolean checkCooldown(Player player, long cooldownMs, Map<UUID, Long> timeMap) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastTime = timeMap.get(playerId);

        if (lastTime != null && (currentTime - lastTime) < cooldownMs) {
            return false;
        }

        updateClickCount(playerId, currentTime);

        if (isPlayerSpamming(playerId)) {
            sendSpamWarning(player);
            return false;
        }

        timeMap.put(playerId, currentTime);
        return true;
    }

    private void updateClickCount(UUID playerId, long currentTime) {
        clickCounts.merge(playerId, 1, Integer::sum);

        new BukkitRunnable() {
            @Override
            public void run() {
                clickCounts.computeIfPresent(playerId, (id, count) -> Math.max(0, count - 1));
            }
        }.runTaskLater(plugin, SPAM_THRESHOLD_MS / 50);
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

    private boolean hasInventorySpace(Player player) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots >= REQUIRED_INVENTORY_SLOTS;
    }

    private boolean isAnimationInventory(String title) {
        return title != null && title.contains("Crate Opening");
    }

    /**
     * Determines if a crate opening should be handled based on click type and context.
     * This allows normal inventory manipulation while preserving opening functionality.
     */
    private boolean shouldHandleCrateOpening(InventoryClickEvent event, ItemStack clickedItem) {
        if (!isCrateItem(clickedItem)) {
            return false;
        }

        // Only handle LEFT clicks for opening to allow other clicks for inventory manipulation
        if (event.getClick() != ClickType.LEFT) {
            return false;
        }

        // Must be in a player-related inventory
        return isPlayerRelatedInventory(event) && isValidCrateInventoryClick(event);
    }

    /**
     * Better detection of valid crate inventory clicks.
     */
    private boolean isValidCrateInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType() == InventoryType.SlotType.CONTAINER ||
                event.getSlotType() == InventoryType.SlotType.QUICKBAR) {
            return true;
        }

        if (event.getInventory().getType() == InventoryType.PLAYER) {
            return true;
        }

        if (event.getView().getBottomInventory().equals(event.getClickedInventory())) {
            return true;
        }

        return event.getSlotType() != InventoryType.SlotType.ARMOR &&
                event.getSlotType() != InventoryType.SlotType.OUTSIDE;
    }

    /**
     * Better detection of player-related inventories.
     */
    private boolean isPlayerRelatedInventory(InventoryClickEvent event) {
        return event.getClickedInventory() != null &&
                (event.getClickedInventory().equals(event.getView().getBottomInventory()) ||
                        event.getInventory().getType() == InventoryType.PLAYER ||
                        event.getClickedInventory().getType() == InventoryType.PLAYER);
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

        com.rednetty.server.utils.nbt.NBTAccessor nbt =
                new com.rednetty.server.utils.nbt.NBTAccessor(crateItem);
        if (nbt.hasKey("locked")) {
            return nbt.getBoolean("locked");
        }

        if (crateItem.getItemMeta().hasLore()) {
            return crateItem.getItemMeta().getLore().stream()
                    .anyMatch(line -> line.contains("LOCKED"));
        }

        return false;
    }

    private boolean validateCrateKey(ItemStack key, CrateType crateType) {
        // Key validation - placeholder for future implementation
        return true;
    }

    private boolean isPlayerInDuel(Player player) {
        // Duel check - placeholder for integration with duel system
        return false;
    }

    /**
     * Item manipulation methods to prevent ghost item bugs.
     */
    private void consumeCrateItem(InventoryClickEvent event, ItemStack crateItem) {
        if (crateItem.getAmount() > 1) {
            ItemStack reducedItem = crateItem.clone();
            reducedItem.setAmount(crateItem.getAmount() - 1);
            event.setCurrentItem(reducedItem);
        } else {
            event.setCurrentItem(new ItemStack(Material.AIR));
        }
    }

    private void consumeKeyItem(InventoryClickEvent event, ItemStack keyItem) {
        if (keyItem.getAmount() > 1) {
            ItemStack reducedKey = keyItem.clone();
            reducedKey.setAmount(keyItem.getAmount() - 1);
            event.setCursor(reducedKey);
        } else {
            event.setCursor(new ItemStack(Material.AIR));
        }
    }

    private ItemStack unlockCrateWithEffects(Player player, ItemStack lockedCrate) {
        ItemStack unlocked = lockedCrate.clone();

        com.rednetty.server.utils.nbt.NBTAccessor nbt =
                new com.rednetty.server.utils.nbt.NBTAccessor(unlocked);
        nbt.setBoolean("locked", false);

        if (unlocked.hasItemMeta() && unlocked.getItemMeta().hasLore()) {
            java.util.List<String> lore = new java.util.ArrayList<>(unlocked.getItemMeta().getLore());
            lore.removeIf(line -> line.contains("LOCKED") || line.contains("Requires a"));

            while (!lore.isEmpty() && lore.get(lore.size() - 1).trim().isEmpty()) {
                lore.remove(lore.size() - 1);
            }

            ItemMeta meta = unlocked.getItemMeta();
            meta.setLore(lore);
            unlocked.setItemMeta(meta);
        }

        playUnlockEffects(player);
        return nbt.update();
    }

    /**
     * Scrap value calculation based on crate tier and type.
     */
    private int calculateScrapValue(CrateType crateType) {
        int baseValue = switch (crateType.getTier()) {
            case 1 -> 75;
            case 2 -> 150;
            case 3 -> 300;
            case 4 -> 600;
            case 5 -> 1250;
            case 6 -> 2500;
            default -> 50;
        };

        if (crateType.isHalloween()) {
            baseValue = (int) (baseValue * 1.75);
        }

        int variation = (int) (baseValue * 0.1);
        int randomBonus = (int) (Math.random() * variation * 2) - variation;

        return baseValue + randomBonus;
    }

    /**
     * Centralized message sending methods with consistent formatting.
     */
    private void sendMessage(Player player, String message, Sound sound) {
        player.sendMessage(message);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

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

    private void sendInventoryFullMessage(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "⚠ " + ChatColor.BOLD + "MYSTICAL OVERFLOW PROTECTION" + ChatColor.RED + " ⚠");
        player.sendMessage(ChatColor.GRAY + "Your inventory lacks space for the mystical energies!");
        player.sendMessage(ChatColor.YELLOW + "Please ensure at least " + REQUIRED_INVENTORY_SLOTS + " empty slots and try again.");
        player.sendMessage("");
        playErrorSound(player);
    }

    private void sendLockedCrateMessage(Player player) {
        player.sendMessage(ChatColor.RED + "🔒 " + ChatColor.BOLD + "MYSTICAL SEAL DETECTED" + ChatColor.RED + " 🔒");
        player.sendMessage(ChatColor.GRAY + "This crate is bound by ancient seals!");
        player.sendMessage(ChatColor.YELLOW + "You need a " + ChatColor.AQUA + "Crate Key" +
                ChatColor.YELLOW + " to break the enchantment.");

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

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    private void sendScrapConfirmation(Player player, CrateType crateType, int scrapValue) {
        player.sendMessage(ChatColor.YELLOW + "⚠ " + ChatColor.BOLD + "HIGH VALUE CRATE DETECTED");

        String crateName = (crateType.isHalloween() ? "Halloween " : "") + crateType.getDisplayName();
        player.sendMessage(ChatColor.WHITE + "Scrapping " + ChatColor.YELLOW + crateName + ChatColor.WHITE +
                " Crate will give " + ChatColor.GREEN + TextUtil.formatNumber(scrapValue) + "g");
        player.sendMessage(ChatColor.GRAY + "Right-click again within 5 seconds to confirm.");

        performScrapOperation(player, player.getInventory().getItemInMainHand(), crateType, scrapValue);
    }

    private void sendScrapSuccessMessage(Player player, CrateType crateType, int scrapValue) {
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
        player.sendMessage(MESSAGE_PREFIX_ERROR);
        player.sendMessage(ChatColor.GRAY + message);
        playErrorSound(player);
    }

    /**
     * Sound and effect methods.
     */
    private void playErrorSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    private void playUnlockEffects(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                player.getLocation().add(0, 2, 0), 15, 1, 1, 1, 0.1);
        player.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE,
                player.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.05);
    }

    private void playScrapEffects(Player player, CrateType crateType) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.5f);

        Particle scrapParticle = switch (crateType.getTier()) {
            case 1, 2 -> Particle.CRIT;
            case 3, 4 -> Particle.ENCHANTMENT_TABLE;
            case 5, 6 -> Particle.VILLAGER_HAPPY;
            default -> Particle.CRIT;
        };

        player.getWorld().spawnParticle(scrapParticle,
                player.getLocation().add(0, 1.5, 0), 8, 0.5, 0.5, 0.5, 0.1);

        if (crateType.isHalloween()) {
            player.getWorld().spawnParticle(Particle.FLAME,
                    player.getLocation().add(0, 1.5, 0), 5, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /**
     * Player notification methods.
     */
    private void checkAndNotifyCrates(Player player) {
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

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.5f);
        }
    }

    /**
     * Utility methods for code organization.
     */
    private void logInventoryClick(Player player, InventoryClickEvent event, ItemStack clickedItem, ItemStack cursorItem) {
        logger.fine("Inventory click - Player: " + player.getName() +
                ", Click: " + event.getClick() +
                ", Slot: " + event.getSlot() +
                ", SlotType: " + event.getSlotType() +
                ", InventoryType: " + event.getInventory().getType() +
                ", CurrentItem: " + (clickedItem != null ? clickedItem.getType() : "null") +
                ", CursorItem: " + (cursorItem != null ? cursorItem.getType() : "null"));
    }

    private void cleanupPlayerData(UUID playerId) {
        processingClicks.remove(playerId);
        lastClickTimes.remove(playerId);
        lastScrapTimes.remove(playerId);
        clickCounts.remove(playerId);
        animationInventories.remove(playerId);
    }

    private void scheduleInventoryUpdate(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.updateInventory();
            }
        }.runTaskLater(plugin, INVENTORY_UPDATE_DELAY_TICKS);
    }

    /**
     * Cleanup and maintenance methods.
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        }.runTaskTimerAsynchronously(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    private void performCleanup() {
        long currentTime = System.currentTimeMillis();

        lastClickTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > DATA_CLEANUP_AGE_MS);

        lastScrapTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > DATA_CLEANUP_AGE_MS);

        clickCounts.entrySet().removeIf(entry -> entry.getValue() <= 0);

        processingClicks.removeIf(playerId -> {
            Player player = plugin.getServer().getPlayer(playerId);
            return player == null || !player.isOnline();
        });

        animationInventories.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }

    /**
     * Gets handler statistics for debugging.
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
     * Cleanup method for shutdown.
     */
    public void cleanup() {
        processingClicks.clear();
        lastClickTimes.clear();
        lastScrapTimes.clear();
        clickCounts.clear();
        animationInventories.clear();

        logger.fine("Crate handler cleanup completed");
    }
}
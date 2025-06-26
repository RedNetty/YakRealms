package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.CrateManager;
import com.rednetty.server.mechanics.crates.factory.CrateFactory;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.economy.EconomyManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Enhanced event handler for crate-related interactions
 */
public class CrateHandler implements Listener {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;
    private final CrateFactory crateFactory;
    private final EconomyManager economyManager;

    // Prevent spam clicking
    private final Set<UUID> processingClicks = ConcurrentHashMap.newKeySet();
    private static final long CLICK_COOLDOWN_MS = 500; // 0.5 second cooldown
    private final java.util.Map<UUID, Long> lastClickTimes = new ConcurrentHashMap<>();

    /**
     * Constructor
     */
    public CrateHandler() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
        this.crateFactory = crateManager.getCrateFactory();
        this.economyManager = EconomyManager.getInstance();
    }

    /**
     * Handles crate opening via inventory clicking
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // Check if clicking on a crate in player inventory
        if (isPlayerInventoryClick(event) && isCrateItem(clickedItem)) {
            event.setCancelled(true);
            handleCrateInventoryClick(player, clickedItem, event);
        }
        // Check if using a crate key on a locked crate
        else if (isPlayerInventoryClick(event) && isCrateKeyInteraction(event)) {
            event.setCancelled(true);
            handleCrateKeyUsage(player, event);
        }
    }

    /**
     * Handles crate interactions via right-clicking in hand
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

/*        // Check if player is in duel
        if (Duels.duelers.containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You cannot open crates while in a duel!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }*/

        // Handle right-click for scrap value
        handleCrateScrapValue(player, item);
    }

    /**
     * Handles crate opening when clicked in inventory
     *
     * @param player      The player
     * @param crateItem   The crate item
     * @param event       The click event
     */
    private void handleCrateInventoryClick(Player player, ItemStack crateItem, InventoryClickEvent event) {
        UUID playerId = player.getUniqueId();

        // Check click cooldown
        if (!checkClickCooldown(player)) {
            return;
        }

        // Check if player is already processing a crate
        if (crateManager.getProcessingPlayers().contains(playerId)) {
            player.sendMessage(ChatColor.RED + "Please wait for your current crate to finish opening!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

/*        // Check if player is in duel
        if (Duels.duelers.containsKey(player)) {
            player.sendMessage(ChatColor.RED + "You cannot open crates while in a duel!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }*/

        // Check if inventory has space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Your inventory is full! Please make space before opening crates.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Check if crate is locked
        if (isCrateLocked(crateItem)) {
            player.sendMessage(ChatColor.RED + "This crate is locked! You need a " +
                    ChatColor.AQUA + "Crate Key" + ChatColor.RED + " to unlock it first.");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
            return;
        }

        processingClicks.add(playerId);

        try {
            // Attempt to open the crate
            boolean success = crateManager.openCrate(player, crateItem);

            if (success) {
                // Consume the crate item
                if (crateItem.getAmount() > 1) {
                    crateItem.setAmount(crateItem.getAmount() - 1);
                } else {
                    event.setCurrentItem(new ItemStack(Material.AIR));
                }

                logger.info("Player " + player.getName() + " successfully opened a crate");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to open crate. Please try again.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }

        } catch (Exception e) {
            logger.severe("Error handling crate inventory click for player " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "An error occurred while opening the crate!");
        } finally {
            processingClicks.remove(playerId);
        }
    }

    /**
     * Handles crate key usage on locked crates
     *
     * @param player The player
     * @param event  The click event
     */
    private void handleCrateKeyUsage(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (!crateFactory.isCrateKey(cursor) || !isCrateItem(target)) {
            return;
        }

        if (!isCrateLocked(target)) {
            player.sendMessage(ChatColor.RED + "This crate is not locked!");
            return;
        }

        // Check if key can open this crate type
        CrateType crateType = crateFactory.determineCrateType(target);
        if (crateType == null) {
            player.sendMessage(ChatColor.RED + "Invalid crate type!");
            return;
        }

        // TODO: Implement specific key type checking when CrateKey enum is used
        // For now, assume all keys can open all crates

        // Unlock the crate
        ItemStack unlockedCrate = unlockCrate(target);
        event.setCurrentItem(unlockedCrate);

        // Consume the key
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
        } else {
            event.setCursor(new ItemStack(Material.AIR));
        }

        // Success feedback
        player.sendMessage(ChatColor.GREEN + "✓ Crate unlocked successfully!");
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    /**
     * Handles crate scrap value when right-clicked in hand
     *
     * @param player    The player
     * @param crateItem The crate item
     */
    private void handleCrateScrapValue(Player player, ItemStack crateItem) {
        CrateType crateType = crateFactory.determineCrateType(crateItem);
        if (crateType == null) {
            player.sendMessage(ChatColor.RED + "Invalid crate type!");
            return;
        }

        // Calculate scrap value based on tier
        int scrapValue = calculateScrapValue(crateType);

        // Give gems to player
        boolean success = economyManager.addGems(player.getUniqueId(), scrapValue).isSuccess();

        if (success) {
            // Remove the crate from hand
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getAmount() > 1) {
                handItem.setAmount(handItem.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            // Success message
            player.sendMessage(TextUtil.getCenteredMessage(
                    ChatColor.YELLOW + "⚒ " + ChatColor.BOLD + "CRATE SCRAPPED" + ChatColor.YELLOW + " ⚒"
            ));
            player.sendMessage(TextUtil.getCenteredMessage(
                    ChatColor.WHITE + "Received: " + ChatColor.GREEN + TextUtil.formatNumber(scrapValue) + "g"
            ));

            // Play scrapping sound
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        } else {
            player.sendMessage(ChatColor.RED + "Failed to scrap crate. Please try again.");
        }
    }

    /**
     * Checks if an inventory click is in the player's main inventory
     *
     * @param event The click event
     * @return true if clicking in player inventory
     */
    private boolean isPlayerInventoryClick(InventoryClickEvent event) {
        return event.getInventory().getType() == InventoryType.PLAYER &&
                event.getSlotType() != InventoryType.SlotType.ARMOR;
    }

    /**
     * Checks if an item is a crate
     *
     * @param item The item to check
     * @return true if the item is a crate
     */
    private boolean isCrateItem(ItemStack item) {
        return item != null && crateFactory.isCrate(item);
    }

    /**
     * Checks if this is a crate key interaction (key being used on a crate)
     *
     * @param event The click event
     * @return true if this is a key-crate interaction
     */
    private boolean isCrateKeyInteraction(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        return crateFactory.isCrateKey(cursor) && isCrateItem(target);
    }

    /**
     * Checks if a crate is locked
     *
     * @param crateItem The crate item
     * @return true if the crate is locked
     */
    private boolean isCrateLocked(ItemStack crateItem) {
        if (crateItem == null || !crateItem.hasItemMeta()) {
            return false;
        }

        // Check NBT data for locked status
        com.rednetty.server.utils.nbt.NBTAccessor nbt = new com.rednetty.server.utils.nbt.NBTAccessor(crateItem);
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

    /**
     * Unlocks a crate by removing the locked indicator
     *
     * @param lockedCrate The locked crate
     * @return The unlocked crate
     */
    private ItemStack unlockCrate(ItemStack lockedCrate) {
        ItemStack unlocked = lockedCrate.clone();

        // Remove locked status from NBT
        com.rednetty.server.utils.nbt.NBTAccessor nbt = new com.rednetty.server.utils.nbt.NBTAccessor(unlocked);
        nbt.setBoolean("locked", false);

        // Remove locked lines from lore
        if (unlocked.hasItemMeta() && unlocked.getItemMeta().hasLore()) {
            java.util.List<String> lore = new java.util.ArrayList<>(unlocked.getItemMeta().getLore());
            lore.removeIf(line -> line.contains("LOCKED") || line.contains("Requires a"));

            // Remove empty lines that were adjacent to locked lines
            while (!lore.isEmpty() && lore.get(lore.size() - 1).trim().isEmpty()) {
                lore.remove(lore.size() - 1);
            }

            org.bukkit.inventory.meta.ItemMeta meta = unlocked.getItemMeta();
            meta.setLore(lore);
            unlocked.setItemMeta(meta);
        }

        return nbt.update();
    }

    /**
     * Calculates the scrap value for a crate type
     *
     * @param crateType The crate type
     * @return The scrap value in gems
     */
    private int calculateScrapValue(CrateType crateType) {
        int baseValue = switch (crateType.getTier()) {
            case 1 -> 50;
            case 2 -> 125;
            case 3 -> 250;
            case 4 -> 500;
            case 5 -> 1000;
            case 6 -> 2000;
            default -> 25;
        };

        // Halloween crates are worth 50% more
        if (crateType.isHalloween()) {
            baseValue = (int) (baseValue * 1.5);
        }

        return baseValue;
    }

    /**
     * Checks and updates click cooldown for a player
     *
     * @param player The player
     * @return true if the player can click (not on cooldown)
     */
    private boolean checkClickCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTimes.get(playerId);

        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN_MS) {
            return false;
        }

        lastClickTimes.put(playerId, currentTime);
        return true;
    }

    /**
     * Gets handler statistics for debugging
     *
     * @return Statistics map
     */
    public java.util.Map<String, Object> getHandlerStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("processingClicks", processingClicks.size());
        stats.put("lastClickTimes", lastClickTimes.size());
        stats.put("clickCooldownMs", CLICK_COOLDOWN_MS);
        return stats;
    }

    /**
     * Cleanup method to remove expired data
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();

        // Clean up old click times (older than 5 minutes)
        lastClickTimes.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 300000);

        // Clean up any stuck processing clicks
        processingClicks.removeIf(playerId -> {
            Player player = plugin.getServer().getPlayer(playerId);
            return player == null || !player.isOnline();
        });
    }
}
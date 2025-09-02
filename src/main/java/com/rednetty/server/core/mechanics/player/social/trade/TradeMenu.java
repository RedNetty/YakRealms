package com.rednetty.server.core.mechanics.player.social.trade;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.items.ItemUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TradeMenu {
    // Inventory slots configuration
    private static final int[] INITIATOR_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21};
    private static final int[] TARGET_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26};
    private static final int INITIATOR_CONFIRM_SLOT = 38;
    private static final int TARGET_CONFIRM_SLOT = 42;
    private static final int INITIATOR_HEAD_SLOT = 36;
    private static final int TARGET_HEAD_SLOT = 44;

    private static final Map<UUID, Boolean> playersInPrompt = new ConcurrentHashMap<>();

    private static final long CLICK_COOLDOWN = 50; // 50ms cooldown between clicks
    // Add click processing prevention
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    // Add processing lock to prevent concurrent modifications
    private volatile boolean processingClick = false;

    private final YakRealms plugin;
    private final Trade trade;
    private final Inventory inventory;

    public TradeMenu(YakRealms plugin, Trade trade) {
        this.plugin = plugin;
        this.trade = trade;
        this.inventory = Bukkit.createInventory(null, 54,
                "Trade: " + trade.getInitiator().getName() + " <-> " + trade.getTarget().getName());
        initializeInventory();
    }

    public static boolean isPlayerInPrompt(UUID playerId) {
        return playersInPrompt.getOrDefault(playerId, false);
    }

    public static void clearPromptState(UUID playerId) {
        playersInPrompt.remove(playerId);
    }

    public void openMenu() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    trade.getInitiator().openInventory(inventory);
                    trade.getTarget().openInventory(inventory);
                    plugin.getLogger().info("Trade menu opened for " + trade.getInitiator().getDisplayName() +
                            " and " + trade.getTarget().getDisplayName());
                } catch (Exception e) {
                    plugin.getLogger().severe("Error opening trade menu: " + e.getMessage());
                    e.printStackTrace();
                    plugin.getTradeManager().cancelTrade(trade.getInitiator());
                }
            }
        }.runTask(plugin);
    }

    private void initializeInventory() {
        // Create filler item
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }

        // Fill inventory
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Clear trade slots
        for (int slot : INITIATOR_SLOTS) inventory.clear(slot);
        for (int slot : TARGET_SLOTS) inventory.clear(slot);

        // Initialize UI elements
        updateConfirmButton(trade.getInitiator());
        updateConfirmButton(trade.getTarget());
        setPlayerHead(trade.getInitiator(), INITIATOR_HEAD_SLOT);
        setPlayerHead(trade.getTarget(), TARGET_HEAD_SLOT);

        updateInventory();
    }

    public void updateInventory() {
        updatePlayerItems(trade.getInitiator());
        updatePlayerItems(trade.getTarget());
        updateConfirmButton(trade.getInitiator());
        updateConfirmButton(trade.getTarget());
    }

    private void updatePlayerItems(Player player) {
        int[] slots = (player == trade.getInitiator()) ? INITIATOR_SLOTS : TARGET_SLOTS;
        List<ItemStack> items = trade.getPlayerItems(player);

        // Clear slots
        for (int slot : slots) {
            inventory.setItem(slot, null);
        }

        // Place items
        for (int i = 0; i < Math.min(items.size(), slots.length); i++) {
            inventory.setItem(slots[i], items.get(i));
        }
    }

    private void setPlayerHead(Player player, int slot) {
        inventory.setItem(slot, new ItemUtils().createPlayerHead(player));
    }

    // Completely rewritten click handling with proper synchronization
    public void handleClick(InventoryClickEvent event) {
        // Always cancel the event first to prevent default behavior
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        // Implement click cooldown to prevent spam
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerId);
        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN) {
            return; // Ignore rapid clicks
        }
        lastClickTime.put(playerId, currentTime);

        // Prevent concurrent click processing
        if (processingClick) {
            return;
        }

        processingClick = true;

        try {
            // Schedule the actual handling for next tick to avoid inventory modification issues
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        handleClickSafely(event);
                    } finally {
                        processingClick = false;
                    }
                }
            }.runTask(plugin);
        } catch (Exception e) {
            processingClick = false;
            plugin.getLogger().warning("Error handling trade click: " + e.getMessage());
        }
    }

    // Safe click handling that runs on the next tick
    private void handleClickSafely(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Validate trade is still active
        if (trade.isCompleted()) {
            return;
        }

        // Handle confirm button clicks
        if ((slot == INITIATOR_CONFIRM_SLOT && player == trade.getInitiator()) ||
                (slot == TARGET_CONFIRM_SLOT && player == trade.getTarget())) {
            handleConfirmClick(player);
            return;
        }

        // Handle clicks in trade slots (item removal)
        if (isPlayerTradeSlot(player, slot)) {
            ItemStack clickedItem = inventory.getItem(slot);
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                handleItemRemoval(player, clickedItem, slot);
            }
            return;
        }

        // Handle clicks in player's inventory (item addition)
        // Better validation for player inventory clicks
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().equals(player.getInventory()) &&
                event.getCurrentItem() != null &&
                event.getCurrentItem().getType() != Material.AIR) {
            handleItemAddition(player, event.getCurrentItem());
        }
    }

    private boolean isPlayerTradeSlot(Player player, int slot) {
        int[] slots = (player == trade.getInitiator()) ? INITIATOR_SLOTS : TARGET_SLOTS;
        return Arrays.stream(slots).anyMatch(s -> s == slot);
    }

    private void handleConfirmClick(Player player) {
        trade.setPlayerConfirmed(player, !trade.isPlayerConfirmed(player));
        updateConfirmButton(player);
        player.playSound(Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK, Sound.Source.PLAYER, 0.5f, 1.2f));

        if (trade.isConfirmed()) {
            // Schedule completion for next tick
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getTradeManager().completeTrade(trade);
                    trade.getInitiator().closeInventory();
                    trade.getTarget().closeInventory();
                    playTradeCompletionEffects();
                }
            }.runTask(plugin);
        }
    }

    // Improved item removal with better inventory management
    private void handleItemRemoval(Player player, ItemStack item, int slot) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Remove from trade data
        trade.removePlayerItem(player, item);

        // Schedule inventory updates for next tick
        new BukkitRunnable() {
            @Override
            public void run() {
                // Add item back to player inventory
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());

                // Drop any items that don't fit
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }

                // Clear the slot in trade inventory
                inventory.setItem(slot, null);

                // Reset confirmations and update UI
                trade.resetConfirmations();
                updateConfirmButton(trade.getInitiator());
                updateConfirmButton(trade.getTarget());

                // Update player inventory
                player.updateInventory();

                // Play feedback
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ITEM_PICKUP, Sound.Source.PLAYER, 0.5f, 1.2f));
                player.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0),
                        10, 0.2, 0.2, 0.2, 0.05);
            }
        }.runTask(plugin);
    }

    // Improved item addition with better validation
    private void handleItemAddition(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        int[] slots = (player == trade.getInitiator()) ? INITIATOR_SLOTS : TARGET_SLOTS;

        // Find first available slot
        for (int slot : slots) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR) {

                // Create trade item (single quantity)
                ItemStack tradeItem = item.clone();
                tradeItem.setAmount(1);

                // Add to trade data
                trade.addPlayerItem(player, tradeItem);

                // Schedule inventory updates for next tick
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Remove item from player inventory
                        ItemStack itemToRemove = item.clone();
                        itemToRemove.setAmount(1);
                        player.getInventory().removeItem(itemToRemove);

                        // Add to trade inventory
                        inventory.setItem(slot, tradeItem);

                        // Reset confirmations and update UI
                        trade.resetConfirmations();
                        updateConfirmButton(trade.getInitiator());
                        updateConfirmButton(trade.getTarget());

                        // Update player inventory
                        player.updateInventory();

                        // Play feedback
                        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ITEM_PICKUP, Sound.Source.PLAYER, 0.5f, 0.8f));
                        player.spawnParticle(Particle.INSTANT_EFFECT, player.getLocation().add(0, 1, 0),
                                15, 0.2, 0.2, 0.2, 0.05);
                    }
                }.runTask(plugin);

                break; // Only add one item
            }
        }
    }

    private void updateConfirmButton(Player player) {
        int slot = (player == trade.getInitiator()) ? INITIATOR_CONFIRM_SLOT : TARGET_CONFIRM_SLOT;
        boolean confirmed = trade.isPlayerConfirmed(player);

        ItemStack button = new ItemStack(confirmed ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            // Use Adventure API for item display names and lore
            Component displayName = confirmed
                    ? Component.text("Confirmed", NamedTextColor.GREEN)
                    : Component.text("Click to Confirm", NamedTextColor.RED);

            meta.displayName(displayName);

            List<Component> lore = Arrays.asList(
                    Component.empty(),
                    confirmed
                            ? Component.text("Click to unconfirm", NamedTextColor.GRAY)
                            : Component.text("Click when ready to trade", NamedTextColor.GRAY),
                    Component.text("Trading: ", NamedTextColor.GRAY)
                            .append(Component.text(trade.getPlayerItems(player).size(), NamedTextColor.YELLOW))
                            .append(Component.text(" items", NamedTextColor.GRAY))
            );
            meta.lore(lore);
            button.setItemMeta(meta);
        }
        inventory.setItem(slot, button);
    }

    private void playTradeCompletionEffects() {
        Player initiator = trade.getInitiator();
        Player target = trade.getTarget();

        initiator.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 0.7f, 1.0f));
        target.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 0.7f, 1.0f));

        initiator.spawnParticle(Particle.HAPPY_VILLAGER,
                initiator.getLocation().add(0, 2, 0),
                20, 0.5, 0.5, 0.5, 0);
        target.spawnParticle(Particle.HAPPY_VILLAGER,
                target.getLocation().add(0, 2, 0),
                20, 0.5, 0.5, 0.5, 0);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (trade.isCompleted()) {
            return;
        }

        Player closer = (Player) event.getPlayer();

        // Clean up click tracking
        lastClickTime.remove(closer.getUniqueId());

        // Only handle close if player is not in a prompt
        if (!TradeMenu.isPlayerInPrompt(closer.getUniqueId())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getTradeManager().cancelTrade(closer);
                }
            }.runTask(plugin);
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void updateTradeDisplays() {
        updateConfirmButton(trade.getInitiator());
        updateConfirmButton(trade.getTarget());
    }

    public void cancelTrade(String reason) {
        Player initiator = trade.getInitiator();
        Player target = trade.getTarget();

        // Clean up click tracking
        lastClickTime.remove(initiator.getUniqueId());
        lastClickTime.remove(target.getUniqueId());

        // Send cancellation messages using Adventure API
        Component cancelMessage = Component.text("✖ Trade cancelled: ", NamedTextColor.RED)
                .append(Component.text(reason, NamedTextColor.GRAY));

        initiator.sendMessage(cancelMessage);
        target.sendMessage(cancelMessage);

        // Play cancellation sound
        initiator.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 0.5f, 1.0f));
        target.playSound(Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.PLAYER, 0.5f, 1.0f));

        // Schedule inventory closing for next tick
        new BukkitRunnable() {
            @Override
            public void run() {
                initiator.closeInventory();
                target.closeInventory();
            }
        }.runTask(plugin);

        // Mark trade as completed to prevent double cancellation
        trade.setCompleted(true);
    }

    public void validateTrade() {
        Player initiator = trade.getInitiator();
        Player target = trade.getTarget();

        // Validate inventory space for items
        if (!hasEnoughInventorySpace(initiator, trade.getPlayerItems(target).size()) ||
                !hasEnoughInventorySpace(target, trade.getPlayerItems(initiator).size())) {
            cancelTrade("One of the players doesn't have enough inventory space!");
            return;
        }
    }

    private boolean hasEnoughInventorySpace(Player player, int requiredSlots) {
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        return emptySlots >= requiredSlots;
    }

    // Add cleanup method
    public void cleanup() {
        lastClickTime.clear();
        processingClick = false;
    }
}
package com.rednetty.server.mechanics.player.social.trade;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.items.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        handleRegularTradeClick(event);
    }

    private void handleRegularTradeClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Handle confirm button clicks
        if ((slot == INITIATOR_CONFIRM_SLOT && player == trade.getInitiator()) ||
                (slot == TARGET_CONFIRM_SLOT && player == trade.getTarget())) {
            handleConfirmClick(player);
            return;
        }

        // Handle clicks in trade slots
        if (isPlayerTradeSlot(player, slot)) {
            handleItemRemoval(player, inventory.getItem(slot), slot);
            return;
        }

        // Handle clicks in player's inventory
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().equals(player.getInventory()) &&
                event.getCurrentItem() != null) {
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
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

        if (trade.isConfirmed()) {
            plugin.getTradeManager().completeTrade(trade);
            trade.getInitiator().closeInventory();
            trade.getTarget().closeInventory();
            playTradeCompletionEffects();
        }
    }

    private void handleItemRemoval(Player player, ItemStack item, int slot) {
        if (item != null && item.getType() != Material.AIR) {
            trade.removePlayerItem(player, item);
            player.getInventory().addItem(item.clone());
            inventory.setItem(slot, null);
            trade.resetConfirmations();
            updateConfirmButton(trade.getInitiator());
            updateConfirmButton(trade.getTarget());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            player.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0),
                    10, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private void handleItemAddition(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            int[] slots = (player == trade.getInitiator()) ? INITIATOR_SLOTS : TARGET_SLOTS;

            for (int slot : slots) {
                if (inventory.getItem(slot) == null ||
                        inventory.getItem(slot).getType() == Material.AIR) {
                    ItemStack tradeItem = item.clone();
                    tradeItem.setAmount(1);
                    trade.addPlayerItem(player, tradeItem);
                    inventory.setItem(slot, tradeItem);

                    ItemStack itemToRemove = item.clone();
                    itemToRemove.setAmount(1);
                    player.getInventory().removeItem(itemToRemove);

                    trade.resetConfirmations();
                    updateConfirmButton(trade.getInitiator());
                    updateConfirmButton(trade.getTarget());

                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);
                    player.spawnParticle(Particle.SPELL_INSTANT, player.getLocation().add(0, 1, 0),
                            15, 0.2, 0.2, 0.2, 0.05);
                    break;
                }
            }
        }
    }

    private void updateConfirmButton(Player player) {
        int slot = (player == trade.getInitiator()) ? INITIATOR_CONFIRM_SLOT : TARGET_CONFIRM_SLOT;
        boolean confirmed = trade.isPlayerConfirmed(player);

        ItemStack button = new ItemStack(confirmed ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(confirmed ? "§aConfirmed" : "§cClick to Confirm");
            List<String> lore = Arrays.asList(
                    "",
                    confirmed ? "§7Click to unconfirm" : "§7Click when ready to trade",
                    "§7Trading: §e" + trade.getPlayerItems(player).size() + " §7items"
            );
            meta.setLore(lore);
            button.setItemMeta(meta);
        }
        inventory.setItem(slot, button);
    }

    private void playTradeCompletionEffects() {
        Player initiator = trade.getInitiator();
        Player target = trade.getTarget();

        initiator.playSound(initiator.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);

        initiator.spawnParticle(Particle.VILLAGER_HAPPY,
                initiator.getLocation().add(0, 2, 0),
                20, 0.5, 0.5, 0.5, 0);
        target.spawnParticle(Particle.VILLAGER_HAPPY,
                target.getLocation().add(0, 2, 0),
                20, 0.5, 0.5, 0.5, 0);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (trade.isCompleted()) {
            return;
        }

        Player closer = (Player) event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getTradeManager().cancelTrade(closer);
            }
        }.runTask(plugin);
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

        // Send cancellation messages
        initiator.sendMessage("§c✖ Trade cancelled: " + reason);
        target.sendMessage("§c✖ Trade cancelled: " + reason);

        // Play cancellation sound
        initiator.playSound(initiator.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        target.playSound(target.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);

        // Close inventories
        initiator.closeInventory();
        target.closeInventory();

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
}
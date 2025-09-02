package com.rednetty.server.core.mechanics.player.listeners;

import com.rednetty.server.core.mechanics.item.Journal;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Handles player interactions with special items like journals, maps, and other items
 */
public class ItemInteractionListener extends BaseListener {

    private final YakPlayerManager playerManager;

    public ItemInteractionListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("Item interaction listener initialized");
    }

    /**
     * Handle opening character journals
     */
    @EventHandler
    public void onBookOpen(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getType() == Material.WRITTEN_BOOK &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {

            // If it's a character journal, let the Journal class handle it
            if (item.hasItemMeta() && item.getItemMeta() instanceof BookMeta) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta.getTitle() != null && meta.getTitle().contains("Character Journal")) {
                    event.setCancelled(true);
                    Journal.openJournal(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.25f);
                }
            }
        }
    }

    /**
     * Prevent map opening
     */
    @EventHandler
    public void onMapOpen(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && item.getType() == Material.FILLED_MAP) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    /**
     * Handle furnace interaction with informational message
     */
    @EventHandler
    public void onClickFurnace(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack handItem = player.getInventory().getItemInMainHand();

            if ((handItem == null || handItem.getType() == Material.AIR) &&
                    (event.getClickedBlock().getType() == Material.FURNACE ||
                            event.getClickedBlock().getType() == Material.TORCH)) {

                player.sendMessage(ChatColor.RED +
                        "This can be used to cook fish! Right click this furnace while holding raw fish to cook it.");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent offhand item use (keeping this behavioral restriction from original code)
     */
    @EventHandler
    public void onOffhandUse(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        if (player.getInventory().getItemInOffHand().getType() != Material.AIR) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle special item interactions like spectral gear
     */
    @EventHandler
    public void onInteractSpecialItem(PlayerInteractEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            NBTAccessor nbtAccessor = new NBTAccessor(item);
            String displayName = item.getItemMeta().getDisplayName();

            // Handle special items
            if (displayName.contains("Spectral") && !nbtAccessor.hasKey("gear")) {
                // TODO: Implement this when EliteDrops is converted
                /*
                In the original code, this would call:
                EliteDrops.createCustomEliteDrop("spectralKnight");
                */
                logger.info("Special item interaction detected for " + player.getName());
            }
        }
    }
}
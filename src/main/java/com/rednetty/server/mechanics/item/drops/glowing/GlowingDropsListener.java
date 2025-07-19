package com.rednetty.server.mechanics.item.drops.glowing;

import com.rednetty.server.YakRealms;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Logger;

/**
 * Event listener for the GlowingEntities-based glowing drops system
 */
public class GlowingDropsListener implements Listener {
    private final GlowingDropsManager manager;
    private final Logger logger;

    public GlowingDropsListener(GlowingDropsManager manager) {
        this.manager = manager;
        this.logger = YakRealms.getInstance().getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Item item = event.getEntity();
        if (item == null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (item.isValid() && !item.isDead()) {
                    manager.addGlowingItem(item);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Item item = event.getItemDrop();
        if (item == null) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (item.isValid() && !item.isDead()) {
                    manager.addGlowingItem(item);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Item item = event.getItem();
        if (item != null) {
            manager.removeGlowingItem(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Item item = event.getEntity();
        if (item != null) {
            manager.removeGlowingItem(item);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Item target = event.getTarget();
        if (target != null) {
            manager.removeGlowingItem(target);
        }

        Item entity = event.getEntity();
        if (entity != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (entity.isValid() && !entity.isDead()) {
                        manager.removeGlowingItem(entity);
                        manager.addGlowingItem(entity);
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        // Update all items for this player after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    manager.updateAllItemsForPlayer(player);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 20L);
    }
}
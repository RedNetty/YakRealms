package com.rednetty.server.mechanics.world.teleport;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener for teleport-related events
 */
public class TeleportListener implements Listener {
    private final TeleportManager teleportManager;

    /**
     * Creates a new teleport listener
     *
     * @param teleportManager The teleport manager
     */
    public TeleportListener(TeleportManager teleportManager) {
        this.teleportManager = teleportManager;
    }

    /**
     * Cancels teleport when player takes damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getDamage() <= 0 || !(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (teleportManager.isTeleporting(player.getUniqueId())) {
            teleportManager.cancelTeleport(player.getUniqueId(), "You took damage");
        }
    }

    /**
     * Cancels teleport when player deals damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamageEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        if (teleportManager.isTeleporting(player.getUniqueId())) {
            teleportManager.cancelTeleport(player.getUniqueId(), "You attacked something");
        }
    }

    /**
     * Cancels teleport when player disconnects
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        teleportManager.cancelTeleport(player.getUniqueId(), "You disconnected");
    }
}
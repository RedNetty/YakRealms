package com.rednetty.server.mechanics.mounts;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.friends.Buddies;
import com.rednetty.server.mechanics.player.settings.Toggles;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Event handler for all mount-related events
 */
public class MountEvents implements Listener {
    private final MountManager manager;

    /**
     * Constructs a new MountEvents
     *
     * @param manager The mount manager
     */
    public MountEvents(MountManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) {
            return;
        }

        // Check for horse mount item
        int horseTier = manager.getHorseMount().getMountTier(item);
        if (horseTier > 0) {
            event.setCancelled(true);
            manager.summonMount(player, MountType.HORSE);
            return;
        }

        // Check for elytra mount item
        if (manager.getElytraMount().isElytraMount(item)) {
            event.setCancelled(true);
            manager.summonMount(player, MountType.ELYTRA);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check if player is summoning a mount and has moved
        if (manager.getHorseMount().isSummoning(player) && manager.getHorseMount().hasMoved(player)) {
            manager.getHorseMount().cancelSummoning(player, "MOVEMENT");
        }

        if (manager.getElytraMount().isSummoning(player) && manager.getElytraMount().hasMoved(player)) {
            manager.getElytraMount().cancelSummoning(player, "MOVEMENT");
        }

        // Check if player is gliding above the height limit
        if (player.isGliding() && manager.getElytraMount().isUsingElytra(player) &&
                manager.getElytraMount().isAboveHeightLimit(player)) {
            manager.dismountPlayer(player, true);
        }

        // Update mount position visually for other players
        if (player.isInsideVehicle() && player.getVehicle() instanceof Horse) {
            // This is handled differently in 1.20.2 - the chunk tracking automatically updates
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Cancel mount summoning if player is damaged
            if (manager.getHorseMount().isSummoning(player)) {
                manager.getHorseMount().cancelSummoning(player, "DAMAGE");
            }

            if (manager.getElytraMount().isSummoning(player)) {
                manager.getElytraMount().cancelSummoning(player, "DAMAGE");
            }

            // Prevent fall damage while mounted or using elytra
            if ((event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                    event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION ||
                    event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL) &&
                    (player.isInsideVehicle() ||
                            (player.isGliding() && manager.getElytraMount().isUsingElytra(player)))) {
                event.setCancelled(true);
                return;
            }

            // Dismount on other damage types if mounted
            if (player.isInsideVehicle() && player.getVehicle() instanceof Horse &&
                    event.getCause() != EntityDamageEvent.DamageCause.FALL &&
                    event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) {
                manager.dismountPlayer(player, false);
            }

            // Dismount on damage if using elytra
            if (manager.getElytraMount().isUsingElytra(player) && event.getDamage() > 0) {
                manager.dismountPlayer(player, true);
            }
        }

        // Horse damage handling
        if (event.getEntity() instanceof Horse) {
            Horse horse = (Horse) event.getEntity();
            UUID ownerUUID = manager.getHorseMount().getHorseOwner(horse.getUniqueId());

            // Prevent all damage to owned horses
            if (ownerUUID != null) {
                event.setCancelled(true);


                // Special case for damage by entity
                if (event instanceof EntityDamageByEntityEvent) {
                    EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event;
                    Entity damager = entityEvent.getDamager();

                    if (damager instanceof Player) {
                        Player attackingPlayer = (Player) damager;
                        Player owner = YakRealms.getInstance().getServer().getPlayer(ownerUUID);
                        YakPlayer yakOwner = YakPlayerManager.getInstance().getPlayer(owner);

                        // Don't dismount for buddy attacks
                        if (owner != null && Buddies.getInstance().isBuddy(attackingPlayer, owner.getName()) &&
                                !Toggles.isToggled(attackingPlayer, "Friendly Fire")) {
                            return;
                        }

                        // Don't dismount if attacker has AntiPVP toggled
                        if (Toggles.isToggled(attackingPlayer, "Anti PVP")) {
                            return;
                        }

                        // Don't dismount if attacker has Chaotic toggle and owner is lawful
                        if (owner != null && Toggles.isToggled(attackingPlayer, "Chaotic") &&
                                AlignmentMechanics.getInstance().isPlayerLawful(yakOwner)) {
                            return;
                        }
                    }

                    // Dismount the owner
                    if (horse.getPassengers().size() > 0) {
                        Entity passenger = horse.getPassengers().get(0);
                        if (passenger instanceof Player) {
                            manager.dismountPlayer((Player) passenger, false);
                        }
                    } else {
                        horse.remove();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();

            // Cancel mount summoning if player attacks
            if (manager.getHorseMount().isSummoning(player)) {
                manager.getHorseMount().cancelSummoning(player, "COMBAT");
            }

            if (manager.getElytraMount().isSummoning(player)) {
                manager.getElytraMount().cancelSummoning(player, "COMBAT");
            }

            // Dismount player if they attack while mounted
            if (player.isInsideVehicle() || manager.getElytraMount().isUsingElytra(player)) {
                manager.dismountPlayer(player, false);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player && event.getVehicle() instanceof Horse) {
            Player player = (Player) event.getExited();
            manager.dismountPlayer(player, false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Cancel mount summoning on teleport
        if (manager.getHorseMount().isSummoning(player)) {
            manager.getHorseMount().cancelSummoning(player, "TELEPORT");
        }

        if (manager.getElytraMount().isSummoning(player)) {
            manager.getElytraMount().cancelSummoning(player, "TELEPORT");
        }

        // Dismount player on teleport
        if (manager.hasActiveMount(player.getUniqueId())) {
            manager.dismountPlayer(player, false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cancel mount summoning on quit
        if (manager.getHorseMount().isSummoning(player)) {
            manager.getHorseMount().cancelSummoning(player, null);
        }

        if (manager.getElytraMount().isSummoning(player)) {
            manager.getElytraMount().cancelSummoning(player, null);
        }

        // Dismount player on quit
        if (manager.hasActiveMount(player.getUniqueId())) {
            manager.dismountPlayer(player, false);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            // Prevent inventory manipulation while gliding
            if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
                event.setCancelled(true);
                return;
            }

            // Prevent moving elytra in armor slot
            if (event.getSlotType() == InventoryType.SlotType.ARMOR &&
                    (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ELYTRA)) {
                if (manager.getElytraMount().isUsingElytra(player)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Prevent adding elytra to armor slot
            if (event.getSlotType() == InventoryType.SlotType.ARMOR &&
                    (event.getCursor() != null && event.getCursor().getType() == Material.ELYTRA)) {
                event.setCancelled(true);
                return;
            }

            // Prevent moving mount items to storage
            if (event.getWhoClicked().getOpenInventory().getTitle().contains("Bank Chest") ||
                    event.getWhoClicked().getOpenInventory().getTitle().contains("Horse Inventory")) {
                ItemStack item = event.getCurrentItem();
                if (item != null && (
                        manager.getHorseMount().getMountTier(item) > 0 ||
                                manager.getElytraMount().isElytraMount(item))) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot store mount items in containers.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        // Prevent dropping mount items
        if (manager.getHorseMount().getMountTier(item) > 0 ||
                manager.getElytraMount().isElytraMount(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot drop mount items.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Cancel commands while using elytra
        if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use commands while gliding.");
        }
    }
}
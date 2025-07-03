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
 * Enhanced event handler for all mount-related events with improved damage tracking and elytra handling
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

        // Enhanced elytra height limit checking
        if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
            if (manager.getElytraMount().isAboveHeightLimit(player)) {
                manager.dismountPlayer(player, true);
                player.sendMessage(ChatColor.RED + "You have exceeded the flight ceiling for this region!");
            }
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

            // Record damage for elytra cooldown tracking
            if (event.getDamage() > 0 && !event.isCancelled()) {
                manager.getElytraMount().recordPlayerDamage(player);
            }

            // Cancel mount summoning if player is damaged
            if (manager.getHorseMount().isSummoning(player)) {
                manager.getHorseMount().cancelSummoning(player, "DAMAGE");
            }

            if (manager.getElytraMount().isSummoning(player)) {
                manager.getElytraMount().cancelSummoning(player, "DAMAGE");
            }

            // Enhanced fall damage prevention for mounted players and elytra users
            if ((event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                    event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION ||
                    event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL)) {

                // Prevent damage while mounted on horse
                if (player.isInsideVehicle() && player.getVehicle() instanceof Horse) {
                    event.setCancelled(true);
                    return;
                }

                // Prevent damage while using elytra mount
                if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Enhanced damage handling for elytra users
            if (manager.getElytraMount().isUsingElytra(player) && event.getDamage() > 0) {
                // Allow certain damage types but dismount
                if (event.getCause() != EntityDamageEvent.DamageCause.FALL &&
                        event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION &&
                        event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) {

                    manager.dismountPlayer(player, false);
                    player.sendMessage(ChatColor.RED + "Your elytra mount has been disrupted by damage!");
                }
            }

            // Dismount horse riders on damage (except fall damage)
            if (player.isInsideVehicle() && player.getVehicle() instanceof Horse &&
                    event.getCause() != EntityDamageEvent.DamageCause.FALL &&
                    event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) {
                manager.dismountPlayer(player, false);
            }
        }

        // Enhanced horse damage handling
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

                        // Notify attacking player
                        attackingPlayer.sendMessage(ChatColor.YELLOW + "You have disrupted " + owner.getName() + "'s mount!");
                    }

                    // Dismount the owner
                    if (horse.getPassengers().size() > 0) {
                        Entity passenger = horse.getPassengers().get(0);
                        if (passenger instanceof Player) {
                            Player mountedPlayer = (Player) passenger;
                            manager.dismountPlayer(mountedPlayer, false);
                            mountedPlayer.sendMessage(ChatColor.RED + "Your mount has been attacked and fled!");
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
                player.sendMessage(ChatColor.RED + "You cannot attack while using a mount!");
            }
        }

        // Enhanced damage tracking for targeted players
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (event.getDamage() > 0 && !event.isCancelled()) {
                manager.getElytraMount().recordPlayerDamage(victim);
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
            player.sendMessage(ChatColor.YELLOW + "Your mount has been dismissed due to teleportation.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Enhanced cleanup for player leaving
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

        // Cleanup elytra mount data
        manager.getElytraMount().cleanup(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Clear any lingering damage cooldown on join (optional - might want to persist this)
        // manager.getElytraMount().clearDamageCooldown(player);

        // Check for any lingering enhanced elytra and restore chestplate
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && manager.getElytraMount().isEnhancedElytraMount(chestplate)) {
            // Player logged out with enhanced elytra - this shouldn't happen but let's handle it
            player.getInventory().setChestplate(null);
            player.sendMessage(ChatColor.YELLOW + "Your elytra mount has been reset due to logout.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            // Enhanced elytra inventory protection
            if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot access your inventory while gliding!");
                return;
            }

            // Prevent moving enhanced elytra in armor slot
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                ItemStack currentItem = event.getCurrentItem();
                ItemStack cursor = event.getCursor();

                // Prevent removing enhanced elytra
                if (currentItem != null && manager.getElytraMount().isEnhancedElytraMount(currentItem)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot remove your active elytra mount!");
                    return;
                }

                // Prevent adding regular elytra to armor slot
                if (cursor != null && cursor.getType() == Material.ELYTRA) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot manually equip elytra items!");
                    return;
                }
            }

            // Enhanced mount item protection
            if (event.getWhoClicked().getOpenInventory().getTitle().contains("Bank Chest") ||
                    event.getWhoClicked().getOpenInventory().getTitle().contains("Horse Inventory") ||
                    event.getWhoClicked().getOpenInventory().getTitle().contains("Chest")) {

                ItemStack item = event.getCurrentItem();
                if (item != null && (
                        manager.getHorseMount().getMountTier(item) > 0 ||
                                manager.getElytraMount().isElytraMount(item) ||
                                manager.getElytraMount().isEnhancedElytraMount(item))) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot store mount items in containers.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        // Enhanced mount item drop protection
        if (manager.getHorseMount().getMountTier(item) > 0 ||
                manager.getElytraMount().isElytraMount(item) ||
                manager.getElytraMount().isEnhancedElytraMount(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot drop mount items.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Enhanced command restriction while using elytra
        if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
            String command = event.getMessage().toLowerCase();

            // Allow certain essential commands
            if (command.startsWith("/msg") || command.startsWith("/tell") || command.startsWith("/r") ||
                    command.startsWith("/help") || command.startsWith("/spawn")) {
                return;
            }

            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Most commands are disabled while gliding for safety!");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Dismount player when changing worlds
        if (manager.hasActiveMount(player.getUniqueId())) {
            manager.dismountPlayer(player, false);
            player.sendMessage(ChatColor.YELLOW + "Your mount has been dismissed due to world change.");
        }

        // Cancel any summoning
        if (manager.getHorseMount().isSummoning(player)) {
            manager.getHorseMount().cancelSummoning(player, "WORLD_CHANGE");
        }

        if (manager.getElytraMount().isSummoning(player)) {
            manager.getElytraMount().cancelSummoning(player, "WORLD_CHANGE");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerToggleGlide(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        // Handle manual glide stopping for elytra mount users
        if (!player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
            // Player manually stopped gliding - this should trigger dismount
            manager.dismountPlayer(player, false);
            player.sendMessage(ChatColor.YELLOW + "Elytra mount deactivated.");
        }
    }
}
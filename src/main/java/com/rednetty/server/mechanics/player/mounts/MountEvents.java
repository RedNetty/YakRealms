package com.rednetty.server.mechanics.player.mounts;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.mounts.type.Mount;
import com.rednetty.server.mechanics.player.mounts.type.MountType;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.social.friends.Buddies;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ULTRA ROBUST event handler for all mount-related events
 * Fixed to prevent desync between mount tracking and actual vehicle state
 */
public class MountEvents implements Listener {
    private final MountManager manager;

    private static final long DISMOUNT_COOLDOWN = 2000; // 2 second cooldown
    private static final int MAX_DISMOUNT_ATTEMPTS = 5; // Max attempts before ignoring
    // Track dismount attempts to prevent spam dismounting
    private final Map<UUID, Long> lastDismountAttempt = new HashMap<>();
    private final Map<UUID, Integer> consecutiveDismountAttempts = new HashMap<>();

    /**
     * Constructs a new MountEvents
     *
     * @param manager The mount manager
     */
    public MountEvents(MountManager manager) {
        this.manager = manager;

        // Start periodic validation task to fix desyncs
        startPeriodicValidation();
    }

    /**
     * CRITICAL: Start a task that periodically validates mount states to prevent desyncs
     */
    private void startPeriodicValidation() {
        new BukkitRunnable() {
            @Override
            public void run() {
                validateAllMountStates();
            }
        }.runTaskTimer(manager.getPlugin(), 100L, 100L); // Every 5 seconds
    }

    /**
     * CRITICAL: Validate all mount states and fix desyncs
     */
    private void validateAllMountStates() {
        // Create a copy to avoid concurrent modification
        Map<UUID, Mount> activeMountsCopy = new HashMap<>(manager.getActiveMounts());

        for (Map.Entry<UUID, Mount> entry : activeMountsCopy.entrySet()) {
            UUID playerUUID = entry.getKey();
            Mount mount = entry.getValue();
            Player player = manager.getPlugin().getServer().getPlayer(playerUUID);

            if (player == null || !player.isOnline()) {
                // Player is offline, clean up
                manager.unregisterActiveMount(playerUUID);
                continue;
            }

            // Check for horse mount desyncs
            if (mount.getType() == MountType.HORSE) {
                if (!player.isInsideVehicle() || !(player.getVehicle() instanceof Horse)) {
                    // Player is not actually in a horse vehicle but manager thinks they are
                    Horse activeHorse = manager.getHorseMount().getActiveHorse(player);
                    if (activeHorse == null || !activeHorse.isValid()) {
                        // Horse doesn't exist, clear the mount
                        manager.unregisterActiveMount(playerUUID);
                        player.sendMessage(ChatColor.YELLOW + "Your mount registration has been cleared due to a desync.");
                    } else {
                        // Horse exists but player isn't mounted - try to remount
                        if (activeHorse.getLocation().distance(player.getLocation()) <= 10.0) {
                            activeHorse.addPassenger(player);
                        } else {
                            // Horse is too far, dismiss it
                            mount.dismount(player, false);
                            player.sendMessage(ChatColor.YELLOW + "Your mount was too far away and has been dismissed.");
                        }
                    }
                }
            }

            // Check for elytra mount desyncs
            if (mount.getType() == MountType.ELYTRA) {
                if (!player.isGliding() || !manager.getElytraMount().isUsingElytra(player)) {
                    // Player is not actually using elytra but manager thinks they are
                    mount.dismount(player, false);
                    player.sendMessage(ChatColor.YELLOW + "Your elytra mount registration has been cleared due to a desync.");
                }
            }
        }
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

            // CRITICAL: Validate mount state before allowing new summon
            if (validateAndCleanupMountState(player)) {
                manager.summonMount(player, MountType.HORSE);
            }
            return;
        }

        // Check for elytra mount item
        if (manager.getElytraMount().isElytraMount(item)) {
            event.setCancelled(true);

            // CRITICAL: Validate mount state before allowing new summon
            if (validateAndCleanupMountState(player)) {
                manager.summonMount(player, MountType.ELYTRA);
            }
        }
    }

    /**
     * CRITICAL: Validate and cleanup mount state before allowing new actions
     *
     * @param player The player
     * @return True if the player can perform mount actions
     */
    private boolean validateAndCleanupMountState(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Check if manager thinks player has active mount
        if (manager.hasActiveMount(playerUUID)) {
            Mount activeMount = manager.getActiveMount(playerUUID);

            if (activeMount.getType() == MountType.HORSE) {
                // Check if player is actually in a horse vehicle
                if (!player.isInsideVehicle() || !(player.getVehicle() instanceof Horse)) {
                    // Desync detected - player isn't actually mounted
                    Horse horse = manager.getHorseMount().getActiveHorse(player);
                    if (horse == null || !horse.isValid()) {
                        // Horse doesn't exist, force cleanup
                        manager.unregisterActiveMount(playerUUID);
                        player.sendMessage(ChatColor.YELLOW + "Cleared invalid mount registration.");
                        return true;
                    } else {
                        // Horse exists, try to remount or dismiss
                        if (horse.getLocation().distance(player.getLocation()) <= 5.0) {
                            horse.addPassenger(player);
                            player.sendMessage(ChatColor.GREEN + "Remounted your horse.");
                            return false; // Already has mount
                        } else {
                            // Dismiss distant horse
                            activeMount.dismount(player, false);
                            player.sendMessage(ChatColor.YELLOW + "Dismissed distant mount.");
                            return true;
                        }
                    }
                }
                // Player is properly mounted
                player.sendMessage(ChatColor.RED + "You already have an active horse mount!");
                return false;
            }

            if (activeMount.getType() == MountType.ELYTRA) {
                // Check if player is actually using elytra
                if (!player.isGliding() || !manager.getElytraMount().isUsingElytra(player)) {
                    // Desync detected - force cleanup
                    activeMount.dismount(player, false);
                    player.sendMessage(ChatColor.YELLOW + "Cleared invalid elytra mount registration.");
                    return true;
                }
                // Player is properly using elytra
                player.sendMessage(ChatColor.RED + "You already have an active elytra mount!");
                return false;
            }
        }

        return true; // No active mount or mount is valid
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Check if player is summoning a mount and has moved
        if (manager.getHorseMount().isSummoning(player) && manager.getHorseMount().hasMoved(player)) {
            manager.getHorseMount().cancelSummoning(player, "MOVEMENT");
        }

        if (manager.getElytraMount().isSummoning(player) && manager.getElytraMount().hasMoved(player)) {
            manager.getElytraMount().cancelSummoning(player, "MOVEMENT");
        }

        // REMOVED: All automatic dismounting based on vehicle state
        // This was causing the desync issues

        // Only keep elytra height limit checking
        if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
            double currentHeight = player.getLocation().getY();
            double heightLimit = manager.getElytraMount().getHeightLimitForRegion(player);

            // Only check height limit if player has been gliding for at least 5 seconds
            if (manager.getElytraMount().hasBeenGlidingLongEnough(player) && currentHeight > heightLimit) {
                manager.dismountPlayer(player, true);
                player.sendMessage(ChatColor.RED + "You have exceeded the flight ceiling for this region! (Limit: " + (int) heightLimit + ")");
            }
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

            //  fall damage prevention for mounted players and elytra users
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

            //  damage handling for elytra users
            if (manager.getElytraMount().isUsingElytra(player) && event.getDamage() > 0) {
                // Allow certain damage types but dismount for others
                if (event.getCause() != EntityDamageEvent.DamageCause.FALL &&
                        event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION &&
                        event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) {

                    manager.dismountPlayer(player, false);
                    player.sendMessage(ChatColor.RED + "Your elytra mount has been disrupted by damage!");
                }
            }

            // MUCH MORE RESTRICTIVE: Only dismount on very specific harmful damage
            if (player.isInsideVehicle() && player.getVehicle() instanceof Horse) {
                // Only dismount on player vs player combat or entity attacks
                if (event instanceof EntityDamageByEntityEvent &&
                        event.getDamage() > 2.0 && // Only significant damage
                        (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                                event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE)) {

                    manager.dismountPlayer(player, false);
                    player.sendMessage(ChatColor.RED + "Your mount was disrupted by combat!");
                }
            }
        }

        //  horse damage handling - same as before but more robust
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

                    // Dismount the owner - this is legitimate damage-based dismounting
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

        //  damage tracking for targeted players
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (event.getDamage() > 0 && !event.isCancelled()) {
                manager.getElytraMount().recordPlayerDamage(victim);
            }
        }
    }

    /**
     * COMPLETELY REWRITTEN: Ultra robust vehicle exit handling
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player) || !(event.getVehicle() instanceof Horse horse)) {
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // Check if this is a legitimate mount
        if (!manager.getHorseMount().isHorseOwner(horse, playerUUID)) {
            return;
        }

        // ANTI-SPAM: Prevent excessive dismount attempts
        long currentTime = System.currentTimeMillis();
        Long lastAttempt = lastDismountAttempt.get(playerUUID);

        if (lastAttempt != null && (currentTime - lastAttempt) < DISMOUNT_COOLDOWN) {
            // Too soon since last attempt, increment counter
            int attempts = consecutiveDismountAttempts.getOrDefault(playerUUID, 0) + 1;
            consecutiveDismountAttempts.put(playerUUID, attempts);

            if (attempts >= MAX_DISMOUNT_ATTEMPTS) {
                // Too many attempts, definitely a glitch - cancel and schedule remount
                event.setCancelled(true);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && horse.isValid() && !player.isInsideVehicle()) {
                            horse.addPassenger(player);
                        }
                    }
                }.runTaskLater(manager.getPlugin(), 2L);

                return;
            }
        } else {
            // Reset counter if enough time has passed
            consecutiveDismountAttempts.put(playerUUID, 1);
        }

        lastDismountAttempt.put(playerUUID, currentTime);

        // Check if this is intentional dismounting (player is sneaking)
        if (player.isSneaking()) {
            // Allow intentional dismount
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isInsideVehicle()) {
                        manager.dismountPlayer(player, false);
                    }
                }
            }.runTaskLater(manager.getPlugin(), 1L);
            return;
        }

        // For all other cases, cancel the exit and schedule a validation check
        event.setCancelled(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !horse.isValid()) {
                    return;
                }

                // If player is still not in vehicle after delay, it might be legitimate
                if (!player.isInsideVehicle()) {
                    // Check if horse is in a problematic state
                    if (horse.getLocation().getBlock().getType().isSolid() ||
                            player.getLocation().getBlock().getType().isSolid()) {
                        // Likely a glitch, try to remount
                        horse.addPassenger(player);
                    } else {
                        // Probably a legitimate exit, allow dismount
                        manager.dismountPlayer(player, false);
                    }
                } else {
                    // Player remounted naturally, all good
                }
            }
        }.runTaskLater(manager.getPlugin(), 10L); // Longer delay for more stability
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Only cancel/dismount for certain teleport causes
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN ||
                event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {

            // Cancel mount summoning on teleport
            if (manager.getHorseMount().isSummoning(player)) {
                manager.getHorseMount().cancelSummoning(player, "TELEPORT");
            }

            if (manager.getElytraMount().isSummoning(player)) {
                manager.getElytraMount().cancelSummoning(player, "TELEPORT");
            }

            // Dismount player on major teleports only
            if (manager.hasActiveMount(player.getUniqueId())) {
                manager.dismountPlayer(player, false);
                player.sendMessage(ChatColor.YELLOW + "Your mount has been dismissed due to teleportation.");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Clean up tracking data
        lastDismountAttempt.remove(playerUUID);
        consecutiveDismountAttempts.remove(playerUUID);

        //  cleanup for player leaving
        if (manager.getHorseMount().isSummoning(player)) {
            manager.getHorseMount().cancelSummoning(player, null);
        }

        if (manager.getElytraMount().isSummoning(player)) {
            manager.getElytraMount().cancelSummoning(player, null);
        }

        // Dismount player on quit
        if (manager.hasActiveMount(playerUUID)) {
            manager.dismountPlayer(player, false);
        }

        // Cleanup elytra mount data
        manager.getElytraMount().cleanup(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Reset tracking data
        lastDismountAttempt.remove(playerUUID);
        consecutiveDismountAttempts.remove(playerUUID);

        // Validate mount state on join
        validateAndCleanupMountState(player);

        // Check for any lingering  elytra and restore chestplate
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && manager.getElytraMount().isElytraMount(chestplate)) {
            player.getInventory().setChestplate(null);
            player.sendMessage(ChatColor.YELLOW + "Your elytra mount has been reset due to logout.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            //  elytra inventory protection
            if (player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot access your inventory while gliding!");
                return;
            }

            // Prevent moving  elytra in armor slot
            if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
                ItemStack currentItem = event.getCurrentItem();
                ItemStack cursor = event.getCursor();

                // Prevent removing  elytra
                if (currentItem != null && manager.getElytraMount().isElytraMount(currentItem)) {
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

            //  mount item protection
            if (event.getWhoClicked().getOpenInventory().getTitle().contains("Bank Chest") ||
                    event.getWhoClicked().getOpenInventory().getTitle().contains("Horse Inventory") ||
                    event.getWhoClicked().getOpenInventory().getTitle().contains("Chest")) {

                ItemStack item = event.getCurrentItem();
                if (item != null && (
                        manager.getHorseMount().getMountTier(item) > 0 ||
                                manager.getElytraMount().isElytraMount(item) ||
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

        //  mount item drop protection
        if (manager.getHorseMount().getMountTier(item) > 0 ||
                manager.getElytraMount().isElytraMount(item) ||
                manager.getElytraMount().isElytraMount(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot drop mount items.");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        //  command restriction while using elytra
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
        if (!event.isFlying() && player.isGliding() && manager.getElytraMount().isUsingElytra(player)) {
            // Player manually stopped gliding - this should trigger dismount
            manager.dismountPlayer(player, false);
            player.sendMessage(ChatColor.YELLOW + "Elytra mount deactivated.");
        }
    }

    /**
     *  Add method to allow manual dismounting via sneaking
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        // If player starts sneaking while on horse, allow dismount
        if (event.isSneaking() && player.isInsideVehicle() &&
                player.getVehicle() instanceof Horse &&
                manager.hasActiveMount(player.getUniqueId())) {

            // This is intentional dismounting - will be handled by VehicleExitEvent
            player.sendMessage(ChatColor.YELLOW + "Dismounting...");
        }
    }
}
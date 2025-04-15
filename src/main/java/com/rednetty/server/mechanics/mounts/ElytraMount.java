package com.rednetty.server.mechanics.mounts;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Handler for elytra mounts
 */
public class ElytraMount implements Mount {
    private final MountManager manager;
    private final Map<UUID, BukkitTask> summonTasks = new HashMap<>();
    private final Map<UUID, Location> summonLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> durationTasks = new HashMap<>();
    private final Map<UUID, ItemStack> originalChestplates = new HashMap<>();

    /**
     * Constructs a new ElytraMount
     *
     * @param manager The mount manager
     */
    public ElytraMount(MountManager manager) {
        this.manager = manager;
    }

    @Override
    public MountType getType() {
        return MountType.ELYTRA;
    }

    @Override
    public boolean summonMount(Player player) {
        // Check permission
        if (!player.hasPermission("yakrp.mount.elytra")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use an elytra mount.");
            return false;
        }

        // Check if already summoning
        if (isSummoning(player)) {
            player.sendMessage(ChatColor.RED + "You are already summoning a mount.");
            return false;
        }

        // Check height restrictions
        if (!checkElytraLaunchRequirements(player)) {
            return false;
        }

        // Start summoning process
        int summonTime = manager.getConfig().getElytraSummonTime();

        summonLocations.put(player.getUniqueId(), player.getLocation());

        player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() +
                "SUMMONING ELYTRA MOUNT.... " + summonTime);

        BukkitTask task = new BukkitRunnable() {
            int countdown = summonTime;

            @Override
            public void run() {
                // Check if player is still online
                if (!player.isOnline()) {
                    cancel();
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    return;
                }

                // Decrement countdown
                countdown--;

                if (countdown <= 0) {
                    // Launch the player and equip elytra
                    activateElytra(player);

                    // Clean up
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    cancel();
                } else {
                    // Update message
                    player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() +
                            "SUMMONING ELYTRA MOUNT.... " + countdown);
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 20L);

        summonTasks.put(player.getUniqueId(), task);
        return true;
    }

    @Override
    public boolean dismount(Player player, boolean sendMessage) {
        UUID playerUUID = player.getUniqueId();

        // Cancel duration task if active
        BukkitTask durationTask = durationTasks.remove(playerUUID);
        if (durationTask != null) {
            durationTask.cancel();
        }

        // Restore original chestplate
        ItemStack originalChestplate = originalChestplates.remove(playerUUID);

        // Get current armor contents
        ItemStack[] armorContents = player.getInventory().getArmorContents();

        // Only replace if current chestplate is elytra
        if (armorContents[2] != null && armorContents[2].getType() == Material.ELYTRA) {
            armorContents[2] = originalChestplate;
            player.getInventory().setArmorContents(armorContents);
        }

        // Stop gliding
        if (player.isGliding()) {
            player.setGliding(false);
            player.setFallDistance(0);
        }

        // Unregister from mount manager
        manager.unregisterActiveMount(playerUUID);

        if (sendMessage) {
            player.sendMessage(ChatColor.RED + "Your elytra wings have blistered and are unable to fly anymore.");
        }

        return true;
    }

    @Override
    public boolean isSummoning(Player player) {
        return summonTasks.containsKey(player.getUniqueId());
    }

    @Override
    public void cancelSummoning(Player player, String reason) {
        UUID playerUUID = player.getUniqueId();

        BukkitTask task = summonTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        summonLocations.remove(playerUUID);

        player.sendMessage(ChatColor.RED + "CANCELLED ELYTRA MOUNT" +
                (reason != null ? " DUE TO " + reason : ""));
    }

    /**
     * Checks if the player has moved from their summoning location
     *
     * @param player The player
     * @return True if the player has moved
     */
    public boolean hasMoved(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!summonLocations.containsKey(playerUUID)) {
            return false;
        }

        Location summonLocation = summonLocations.get(playerUUID);
        Location currentLocation = player.getLocation();

        return summonLocation.distance(currentLocation) > 2.0;
    }

    /**
     * Checks if a player is currently using an elytra mount
     *
     * @param player The player
     * @return True if the player is using an elytra mount
     */
    public boolean isUsingElytra(Player player) {
        return durationTasks.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player is above the height limit for their region
     *
     * @param player The player
     * @return True if the player is above the height limit
     */
    public boolean isAboveHeightLimit(Player player) {
        String region = getPlayerRegion(player);
        double heightLimit = manager.getConfig().getElytraHeightLimit(region);

        return player.getLocation().getY() >= heightLimit;
    }

    /**
     * Gets the region a player is in
     *
     * @param player The player
     * @return The region name
     */
    private String getPlayerRegion(Player player) {
        Location loc = player.getLocation();
        double x = loc.getX();
        double z = loc.getZ();

        // FrostFall region check
        if (x <= 270 && x >= -100 && z >= 100 && z <= 267) {
            return "frostfall";
        }

        // Deadpeaks region check
        if (x >= 620 && z >= -270 && z < 230) {
            return "deadpeaks";
        }

        // Avalon region check
        if (x <= 645 && z >= 240) {
            return "avalon";
        }

        return "default";
    }

    /**
     * Checks if a player can launch an elytra
     *
     * @param player The player
     * @return True if the player can launch
     */
    private boolean checkElytraLaunchRequirements(Player player) {
        Location loc = player.getLocation();
        String region = getPlayerRegion(player);
        double heightLimit = manager.getConfig().getElytraHeightLimit(region);

        // Check if there's enough space above
        Location aboveLocation = loc.clone().add(0, 15, 0);
        if (aboveLocation.getBlock().getType() != Material.AIR || aboveLocation.getY() >= heightLimit) {
            player.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() +
                    "Unable to launch elytra mount, something would block your flight above you, or you are too high up to launch.");
            return false;
        }

        return true;
    }

    /**
     * Activates the elytra mount for a player
     *
     * @param player The player
     */
    private void activateElytra(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Launch the player upward
        player.setVelocity(new Vector(0, 2, 0));

        // Schedule equipping the elytra after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() + "ACTIVATED ELYTRA MOUNT!");

                // Store original chestplate
                ItemStack[] armorContents = player.getInventory().getArmorContents();
                originalChestplates.put(playerUUID, armorContents[2]);

                // Equip elytra
                armorContents[2] = new ItemStack(Material.ELYTRA);
                player.getInventory().setArmorContents(armorContents);

                // Start gliding
                player.setGliding(true);

                // Register mount
                manager.registerActiveMount(playerUUID, ElytraMount.this);

                // Play sound
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.0f);

                // Schedule elytra duration
                int duration = manager.getConfig().getElytraDuration();

                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            dismount(player, true);
                        }
                    }
                }.runTaskLater(manager.getPlugin(), duration * 20L);

                durationTasks.put(playerUUID, task);
            }
        }.runTaskLater(manager.getPlugin(), 10L);
    }

    /**
     * Creates an elytra mount item
     *
     * @return The elytra mount item
     */
    public ItemStack createElytraItem() {
        ItemStack itemStack = new ItemStack(Material.ELYTRA);
        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.setDisplayName(ChatColor.AQUA + "Elytra Mount");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A magical set of wings that allows");
        lore.add(ChatColor.GRAY + "you to glide through the air.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Duration: " + manager.getConfig().getElytraDuration() + " seconds");
        lore.add(ChatColor.GRAY + "Right-click to activate");
        lore.add(ChatColor.GRAY + "Permanent Untradeable");

        itemMeta.setLore(lore);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        itemMeta.setUnbreakable(true);

        // Mark as elytra mount
        NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "elytra_mount");
        itemMeta.getPersistentDataContainer().set(mountKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    /**
     * Checks if an item is an elytra mount
     *
     * @param item The item
     * @return True if the item is an elytra mount
     */
    public boolean isElytraMount(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "elytra_mount");
        if (meta.getPersistentDataContainer().has(mountKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            return true;
        }

        // Fallback to display name check for legacy items
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.AQUA + "Elytra Mount");
    }
}
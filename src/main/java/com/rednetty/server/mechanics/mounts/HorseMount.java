package com.rednetty.server.mechanics.mounts;

import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Handler for horse mounts
 */
public class HorseMount implements Mount {
    private final MountManager manager;
    private final Map<UUID, BukkitTask> summonTasks = new HashMap<>();
    private final Map<UUID, Location> summonLocations = new HashMap<>();
    private final Map<UUID, Horse> activeHorses = new HashMap<>();
    private final NamespacedKey ownerKey;

    /**
     * Constructs a new HorseMount
     *
     * @param manager The mount manager
     */
    public HorseMount(MountManager manager) {
        this.manager = manager;
        this.ownerKey = new NamespacedKey(manager.getPlugin(), "horse_owner");
    }

    @Override
    public MountType getType() {
        return MountType.HORSE;
    }

    @Override
    public boolean summonMount(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) {
            return false;
        }

        // Check if player has a horse tier
        int horseTier = yakPlayer.getHorseTier();
        if (horseTier < 2) {
            player.sendMessage(ChatColor.RED + "You don't have a horse mount.");
            return false;
        }

        // Check if already summoning
        if (isSummoning(player)) {
            player.sendMessage(ChatColor.RED + "You are already summoning a mount.");
            return false;
        }

        boolean inSafeZone = AlignmentMechanics.getInstance().isSafeZone(player.getLocation());

        // If in safe zone and instant mounting is enabled, spawn immediately
        if (inSafeZone && manager.getConfig().isInstantMountInSafeZone()) {
            spawnHorse(player, horseTier);
            return true;
        }

        // Otherwise, start summoning process
        int summonTime = yakPlayer.getAlignment().equalsIgnoreCase("CHAOTIC")
                ? manager.getConfig().getChaoticHorseSummonTime()
                : manager.getConfig().getHorseSummonTime();

        summonLocations.put(player.getUniqueId(), player.getLocation());

        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(horseTier);
        player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() + "SUMMONING " +
                ChatColor.valueOf(stats.getColor()) + stats.getName() + ChatColor.WHITE + " ... " + summonTime + "s");

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
                    // Spawn the horse
                    spawnHorse(player, horseTier);

                    // Clean up
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    cancel();
                } else {
                    // Update message
                    player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() + "SUMMONING" +
                            ChatColor.WHITE + " ... " + countdown + "s");
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 20L);

        summonTasks.put(player.getUniqueId(), task);
        return true;
    }

    @Override
    public boolean dismount(Player player, boolean sendMessage) {
        UUID playerUUID = player.getUniqueId();

        // Check if player has an active horse
        Horse horse = activeHorses.remove(playerUUID);
        if (horse == null || !horse.isValid()) {
            return false;
        }

        // Remove horse and dismount player
        if (horse.getPassengers().contains(player)) {
            horse.removePassenger(player);
        }

        // Teleport player to horse location before removing
        Location dismountLocation = horse.getLocation().clone().add(0, 0.5, 0);
        player.teleport(dismountLocation);

        horse.remove();

        // Unregister from mount manager
        manager.unregisterActiveMount(playerUUID);

        if (sendMessage) {
            player.sendMessage(ChatColor.RED + "Your mount has been dismissed.");
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

        player.sendMessage(ChatColor.RED + "Mount Summon - " + ChatColor.BOLD + "CANCELLED" +
                (reason != null ? " - " + reason : ""));
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

        return summonLocation.distanceSquared(currentLocation) > 2.0;
    }

    /**
     * Spawns a horse for a player
     *
     * @param player The player
     * @param tier   The horse tier
     */
    private void spawnHorse(Player player, int tier) {
        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(tier);

        // Create the horse
        Horse horse = player.getWorld().spawn(player.getLocation(), Horse.class);

        // Set horse properties
        horse.setAdult();
        horse.setTamed(true);
        horse.setOwner(player);
        horse.setColor(Horse.Color.BLACK);
        horse.setStyle(Horse.Style.NONE);

        // Set attributes
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(stats.getSpeed());
        horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(stats.getJump());
        horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        horse.setHealth(20.0);

        // Set inventory
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

        // Add armor if specified
        if (!stats.getArmorType().equals("NONE")) {
            Material armorMaterial;
            switch (stats.getArmorType().toUpperCase()) {
                case "IRON":
                    armorMaterial = Material.IRON_HORSE_ARMOR;
                    break;
                case "GOLD":
                    armorMaterial = Material.GOLDEN_HORSE_ARMOR;
                    break;
                case "DIAMOND":
                    armorMaterial = Material.DIAMOND_HORSE_ARMOR;
                    break;
                default:
                    armorMaterial = Material.LEATHER_HORSE_ARMOR;
                    break;
            }
            horse.getInventory().setArmor(new ItemStack(armorMaterial));
        }

        // Set custom name
        String displayName = ChatColor.valueOf(stats.getColor()) + stats.getName();
        horse.setCustomName(displayName);
        horse.setCustomNameVisible(true);

        // Store owner in persistent data
        horse.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());

        // Add the player as a passenger
        horse.addPassenger(player);

        // Register active horse
        activeHorses.put(player.getUniqueId(), horse);
        manager.registerActiveMount(player.getUniqueId(), this);

        player.sendMessage(ChatColor.GREEN + "You have summoned a " + displayName + ChatColor.GREEN + "!");
    }

    /**
     * Creates a mount item for a specific tier
     *
     * @param tier   The tier
     * @param inShop Whether this is for shop display
     * @return The item stack
     */
    public ItemStack createMountItem(int tier, boolean inShop) {
        if (tier < 2) {
            return new ItemStack(Material.AIR);
        }

        MountConfig.HorseStats stats = manager.getConfig().getHorseStats(tier);

        ItemStack itemStack = new ItemStack(Material.SADDLE);
        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null) {
            return itemStack;
        }

        // Set name and lore
        String name = ChatColor.valueOf(stats.getColor()) + stats.getName();
        itemMeta.setDisplayName(name);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + "Speed: " + (int) (stats.getSpeed() * 100) + "%");
        lore.add(ChatColor.RED + "Jump: " + (int) (stats.getJump() * 100) + "%");

        // Add requirement for shop items
        if (inShop && tier > 2) {
            MountConfig.HorseStats prevStats = manager.getConfig().getHorseStats(tier - 1);
            lore.add(ChatColor.RED.toString() + ChatColor.BOLD + "REQ: " +
                    ChatColor.valueOf(prevStats.getColor()) + prevStats.getName());
        }

        lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + stats.getDescription());
        lore.add(ChatColor.GRAY + "Permanent Untradeable");

        // Add price for shop items
        if (inShop) {
            lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + stats.getPrice() + "g");
        }

        itemMeta.setLore(lore);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Store tier in custom data
        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        itemMeta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    /**
     * Gets the tier of a mount item
     *
     * @param item The item
     * @return The tier, or 0 if not a mount item
     */
    public int getMountTier(ItemStack item) {
        if (item == null || item.getType() != Material.SADDLE || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }

        NamespacedKey tierKey = new NamespacedKey(manager.getPlugin(), "mount_tier");
        if (meta.getPersistentDataContainer().has(tierKey, PersistentDataType.INTEGER)) {
            return meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        }

        // Fallback to display name check for legacy items
        String name = meta.getDisplayName();
        if (name.contains(ChatColor.GREEN.toString())) {
            return 2;
        } else if (name.contains(ChatColor.AQUA.toString())) {
            return 3;
        } else if (name.contains(ChatColor.LIGHT_PURPLE.toString())) {
            return 4;
        } else if (name.contains(ChatColor.YELLOW.toString())) {
            return 5;
        }

        return 0;
    }

    /**
     * Checks if a horse is owned by a specific player
     *
     * @param horse      The horse
     * @param playerUUID The player UUID
     * @return True if the player owns the horse
     */
    public boolean isHorseOwner(Horse horse, UUID playerUUID) {
        String storedUUID = horse.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return storedUUID != null && storedUUID.equals(playerUUID.toString());
    }

    /**
     * Gets the horse by its UUID
     *
     * @param horseUUID The horse UUID
     * @return The owner UUID, or null if not found
     */
    public UUID getHorseOwner(UUID horseUUID) {
        for (Map.Entry<UUID, Horse> entry : activeHorses.entrySet()) {
            if (entry.getValue().getUniqueId().equals(horseUUID)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
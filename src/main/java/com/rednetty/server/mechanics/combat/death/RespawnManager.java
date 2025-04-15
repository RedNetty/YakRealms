package com.rednetty.server.mechanics.combat.death;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.stamina.Energy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Manages player death and respawn mechanics, including:
 * - Determining which items to drop on death based on alignment
 * - Creating death remnants at death locations
 * - Handling player respawn and restoration of kept items
 */
public class RespawnManager implements Listener {
    private final ConcurrentHashMap<UUID, Long> respawnProcessed = new ConcurrentHashMap<>();
    private final DeathRemnantManager remnantManager;
    private final YakPlayerManager playerManager;

    private static final String RESPAWN_PENDING_KEY = "respawn_pending";
    private static final long RESPAWN_EXPIRATION = 3600000; // 1 hour in milliseconds

    /**
     * Constructor initializes the respawn manager
     */
    public RespawnManager() {
        this.remnantManager = new DeathRemnantManager(YakRealms.getInstance());
        this.playerManager = YakPlayerManager.getInstance();
    }

    /**
     * Initializes the respawn mechanics
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start cleanup task for expired respawn items
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredRespawnItems();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 1200L, 1200L); // Run every minute (1200 ticks)

        YakRealms.log("Respawn mechanics have been enabled.");
    }

    /**
     * Cleans up resources on disable
     */
    public void onDisable() {
        YakRealms.log("Respawn mechanics have been disabled.");
    }

    /**
     * Handles player death events to determine item drops and create death remnants
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Skip if death was already processed recently
        if (respawnProcessed.containsKey(playerId) &&
                System.currentTimeMillis() - respawnProcessed.get(playerId) < 5000) {
            return;
        }

        // Mark this death as processed
        respawnProcessed.put(playerId, System.currentTimeMillis());

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            YakRealms.warn("Could not find YakPlayer data for " + player.getName() + " during death processing");
            return;
        }

        // Create a list of items to keep
        List<ItemStack> keptItems = new ArrayList<>();

        // Save gem containers (these are always kept regardless of alignment)
        player.getInventory().all(Material.INK_SAC).values().stream()
                .filter(item -> item != null)
                .filter(item -> item.getType() == Material.INK_SAC)
                .filter(item -> item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                .filter(item -> ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Gem Container"))
                .forEach(item -> {
                    keptItems.add(item);
                    event.getDrops().remove(item);
                });

        // Process alignment-specific item keeping
        String alignment = yakPlayer.getAlignment();

        if ("LAWFUL".equals(alignment)) {
            // Lawful players keep all armor and weapons
            keepAllArmorAndWeapons(player, keptItems, event.getDrops());
        } else if ("NEUTRAL".equals(alignment)) {
            // Neutral players have a chance to lose armor and weapons
            keepSomeArmorAndWeapons(player, keptItems, event.getDrops());
        }
        // Chaotic players lose everything except gem containers

        // Always keep untradeable items
        Stream.of(player.getInventory().getContents())
                .filter(item -> item != null && item.getType() != Material.AIR)
                .filter(item -> item.hasItemMeta() && item.getItemMeta().hasLore())
                .filter(item -> {
                    List<String> lore = item.getItemMeta().getLore();
                    return lore != null && lore.contains(ChatColor.GRAY + "Permenant Untradeable");
                })
                .filter(item -> !keptItems.contains(item))
                .forEach(item -> {
                    keptItems.add(item);
                    event.getDrops().remove(item);
                });

        // Store kept items in the database
        if (!keptItems.isEmpty()) {
            try {
                // Serialize the respawn items
                String serializedItems = yakPlayer.serializeItemStacks(keptItems.toArray(new ItemStack[0]));

                // Store the serialized items and a flag indicating respawn is pending
                yakPlayer.setTemporaryData(RESPAWN_PENDING_KEY, System.currentTimeMillis());
                yakPlayer.setTemporaryData("respawn_items", serializedItems);

                // Save immediately to ensure data is not lost
                playerManager.savePlayer(yakPlayer);

                YakRealms.debug("Saved " + keptItems.size() + " respawn items for player " + player.getName());
            } catch (Exception e) {
                YakRealms.error("Failed to save respawn items for " + player.getName(), e);
            }
        }

        // Create death remnant with remaining items
        if (!event.getDrops().isEmpty()) {
            List<ItemStack> droppedItems = new ArrayList<>(event.getDrops());
            remnantManager.createDeathRemnant(player.getLocation(), droppedItems, player);
            event.getDrops().clear();
        }
    }

    /**
     * Handles player respawn events to restore kept items
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        UUID playerId = player.getUniqueId();
        if (yakPlayer == null) {
            YakRealms.warn("Could not find YakPlayer data for " + player.getName() + " during respawn processing");
            return;
        }
        // Determine appropriate respawn location based on alignment
        if (AlignmentMechanics.isPlayerChaotic(yakPlayer)) {
            // Random spawn for chaotic players
            Location randomLocation = AlignmentMechanics.generateRandomSpawnPoint(player.getName());
            event.setRespawnLocation(randomLocation);
        }


        // Check if player has respawn items pending
        if (yakPlayer.hasTemporaryData(RESPAWN_PENDING_KEY)) {
            // Restore kept items from temporary storage
            restoreRespawnItems(player, yakPlayer);
        }

        // Initialize player state
        initializeRespawnedPlayer(player, yakPlayer);

        // Add brief blindness effect when respawning
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 5L);
    }

    /**
     * Restores respawn items for a player from database storage
     */
    private void restoreRespawnItems(Player player, YakPlayer yakPlayer) {
        try {
            // Get the serialized respawn items
            String serializedItems = (String) yakPlayer.getTemporaryData("respawn_items");

            if (serializedItems != null && !serializedItems.isEmpty()) {
                // Deserialize the respawn items
                ItemStack[] items = yakPlayer.deserializeItemStacks(serializedItems);

                // Add items to player inventory
                for (ItemStack item : items) {
                    if (item != null && item.getType() != Material.AIR) {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

                        // Drop any items that couldn't fit in inventory
                        if (!leftover.isEmpty()) {
                            for (ItemStack leftoverItem : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                            }
                        }
                    }
                }

                YakRealms.debug("Restored " + items.length + " respawn items for player " + player.getName());
            }

            // Clear respawn data to prevent duplication
            yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
            yakPlayer.removeTemporaryData("respawn_items");

            // Save the updated player data
            playerManager.savePlayer(yakPlayer);

        } catch (Exception e) {
            YakRealms.error("Failed to restore respawn items for " + player.getName(), e);
        }
    }

    /**
     * Clean up expired respawn items to prevent memory leaks
     */
    private void cleanupExpiredRespawnItems() {
        long currentTime = System.currentTimeMillis();

        // Iterate through all online players
        for (YakPlayer yakPlayer : playerManager.getOnlinePlayers()) {
            if (yakPlayer.hasTemporaryData(RESPAWN_PENDING_KEY)) {
                long storedTime = (long) yakPlayer.getTemporaryData(RESPAWN_PENDING_KEY);

                // Check if expired (older than 1 hour)
                if (currentTime - storedTime > RESPAWN_EXPIRATION) {
                    yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
                    yakPlayer.removeTemporaryData("respawn_items");
                    playerManager.savePlayer(yakPlayer);

                    YakRealms.debug("Cleaned up expired respawn items for " + yakPlayer.getUsername());
                }
            }
        }
    }

    /**
     * Keeps all armor and weapons for lawful players
     */
    private void keepAllArmorAndWeapons(Player player, List<ItemStack> keptItems, List<ItemStack> drops) {
        // Keep all armor
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece != null && armorPiece.getType() != Material.AIR) {
                keptItems.add(armorPiece);
                drops.remove(armorPiece);
            }
        }

        // Keep main weapon if not a pickaxe or fishing rod
        ItemStack mainWeapon = player.getInventory().getItem(0);
        if (mainWeapon != null && !mainWeapon.getType().name().contains("_PICKAXE") &&
                !mainWeapon.getType().name().contains("FISHING") &&
                mainWeapon.getType().name().matches(".*(_SWORD|_AXE|_SHOVEL|_HOE).*")) {
            keptItems.add(mainWeapon);
            drops.remove(mainWeapon);
        }

        // Keep all tools (pickaxes and fishing rods)
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR &&
                    (item.getType().name().contains("_PICKAXE") || item.getType().name().contains("FISHING"))) {
                keptItems.add(item);
                drops.remove(item);
            }
        }
    }

    /**
     * Keeps some armor and weapons for neutral players (random chance)
     */
    private void keepSomeArmorAndWeapons(Player player, List<ItemStack> keptItems, List<ItemStack> drops) {
        Random random = new Random();

        // 25% chance to keep each armor piece
        for (ItemStack armorPiece : player.getInventory().getArmorContents()) {
            if (armorPiece != null && armorPiece.getType() != Material.AIR && random.nextInt(4) == 0) {
                keptItems.add(armorPiece);
                drops.remove(armorPiece);
            }
        }

        // 50% chance to keep main weapon
        ItemStack mainWeapon = player.getInventory().getItem(0);
        if (mainWeapon != null && !mainWeapon.getType().name().contains("_PICKAXE") &&
                !mainWeapon.getType().name().contains("FISHING") &&
                mainWeapon.getType().name().matches(".*(_SWORD|_AXE|_SHOVEL|_HOE).*") &&
                random.nextBoolean()) {
            keptItems.add(mainWeapon);
            drops.remove(mainWeapon);
        }

        // Keep all tools (pickaxes and fishing rods)
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR &&
                    (item.getType().name().contains("_PICKAXE") || item.getType().name().contains("FISHING"))) {
                keptItems.add(item);
                drops.remove(item);
            }
        }
    }

    /**
     * Initializes a player's state after respawning
     */
    private void initializeRespawnedPlayer(Player player, YakPlayer yakPlayer) {
        // Set health and max health
        player.setMaxHealth(50.0);
        player.setHealth(50.0);

        // Set energy display and value
        player.setLevel(100);
        player.setExp(1.0f);
        yakPlayer.setTemporaryData("energy", 100);
        Energy.getInstance().setEnergy(yakPlayer, 100);

        // Reset inventory slot
        player.getInventory().setHeldItemSlot(0);
    }
}
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

/**
 * Manages player death and respawn mechanics, including:
 * - Determining which items to drop on death based on alignment
 * - Creating decorative death remnants at death locations
 * - Handling player respawn and restoration of kept items
 *
 * FIXED: Proper item dropping - only specific items are kept, everything else drops normally
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
     * FIXED: Handles player death events with proper item dropping logic
     * Only specific items are kept based on alignment, everything else drops normally
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

        YakRealms.log("Processing death for " + player.getName() + " (alignment: " + yakPlayer.getAlignment() + ")");

        // Create a list of items to keep (these will be restored on respawn)
        List<ItemStack> keptItems = new ArrayList<>();

        // Get player's current inventory slots for reference
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        ItemStack mainHandItem = player.getInventory().getItem(0); // First hotbar slot

        String alignment = yakPlayer.getAlignment();

        // Process items from event.getDrops() to determine what to keep vs drop
        Iterator<ItemStack> dropIterator = event.getDrops().iterator();
        while (dropIterator.hasNext()) {
            ItemStack item = dropIterator.next();
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            boolean shouldKeep = false;

            // Always keep gem containers regardless of alignment
            if (isGemContainer(item)) {
                shouldKeep = true;
                YakRealms.debug("Keeping gem container: " + item.getType());
            }
            // Always keep untradeable items
            else if (isUntradeableItem(item)) {
                shouldKeep = true;
                YakRealms.debug("Keeping untradeable item: " + item.getType());
            }
            // Check alignment-specific rules
            else if ("LAWFUL".equals(alignment)) {
                // Lawful players keep all armor, weapons, and tools
                if (isArmorPiece(item, armorContents) ||
                        isMainWeaponOrTool(item, mainHandItem) ||
                        isToolItem(item)) {
                    shouldKeep = true;
                    YakRealms.debug("Lawful keeping item: " + item.getType());
                }
            }
            else if ("NEUTRAL".equals(alignment)) {
                // Neutral players have chances to keep armor and weapons
                if (isArmorPiece(item, armorContents)) {
                    // 25% chance to keep each armor piece
                    if (new Random().nextInt(4) == 0) {
                        shouldKeep = true;
                        YakRealms.debug("Neutral keeping armor (25% chance): " + item.getType());
                    }
                } else if (isMainWeaponOrTool(item, mainHandItem)) {
                    if (isToolItem(item)) {
                        // Always keep tools
                        shouldKeep = true;
                        YakRealms.debug("Neutral keeping tool: " + item.getType());
                    } else {
                        // 50% chance to keep main weapon
                        if (new Random().nextBoolean()) {
                            shouldKeep = true;
                            YakRealms.debug("Neutral keeping weapon (50% chance): " + item.getType());
                        }
                    }
                } else if (isToolItem(item)) {
                    // Always keep tools
                    shouldKeep = true;
                    YakRealms.debug("Neutral keeping tool: " + item.getType());
                }
            }
            // CHAOTIC players lose everything except gem containers and untradeable items
            // (already handled above)

            // If item should be kept, add to kept items and remove from drops
            if (shouldKeep) {
                keptItems.add(item.clone());
                dropIterator.remove(); // Safely remove from drops while iterating
            }
        }

        // Store kept items in the database for respawn restoration
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

        // Create decorative death remnant (no items, just visual skeleton)
        remnantManager.createDeathRemnant(player.getLocation(), Collections.emptyList(), player);

        YakRealms.debug("Death processing complete for " + player.getName() +
                ". Kept items: " + keptItems.size() +
                ", Items dropping: " + event.getDrops().size());
    }

    /**
     * Check if an item is a gem container
     */
    private boolean isGemContainer(ItemStack item) {
        return item.getType() == Material.INK_SAC &&
                item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Gem Container");
    }

    /**
     * Check if an item is untradeable
     */
    private boolean isUntradeableItem(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        List<String> lore = item.getItemMeta().getLore();
        return lore != null && lore.contains(ChatColor.GRAY + "Permenant Untradeable");
    }

    /**
     * Check if an item is an armor piece by comparing with equipped armor
     */
    private boolean isArmorPiece(ItemStack item, ItemStack[] armorContents) {
        for (ItemStack armorPiece : armorContents) {
            if (armorPiece != null && armorPiece.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an item is the main weapon/tool (first hotbar slot)
     */
    private boolean isMainWeaponOrTool(ItemStack item, ItemStack mainHandItem) {
        return mainHandItem != null && mainHandItem.isSimilar(item);
    }

    /**
     * Check if an item is a tool (pickaxe, fishing rod, etc.)
     */
    private boolean isToolItem(ItemStack item) {
        String typeName = item.getType().name();
        return typeName.contains("_PICKAXE") ||
                typeName.contains("FISHING") ||
                typeName.contains("_SHOVEL") ||
                typeName.contains("_HOE") ||
                typeName.contains("_AXE");
    }

    /**
     * FIXED: Handles player respawn events to restore kept items with proper health coordination
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        UUID playerId = player.getUniqueId();

        if (yakPlayer == null) {
            YakRealms.warn("Could not find YakPlayer data for " + player.getName() + " during respawn processing");
            return;
        }

        YakRealms.log("Processing respawn for " + player.getName());

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

        // FIXED: Schedule initialization after a delay to ensure respawn is complete
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    initializeRespawnedPlayer(player, yakPlayer);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 10L); // 0.5 second delay

        // Add brief blindness effect when respawning
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 15L); // Slightly later than initialization
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
     * FIXED: Initializes a player's state after respawning with proper health coordination
     */
    private void initializeRespawnedPlayer(Player player, YakPlayer yakPlayer) {
        try {
            YakRealms.debug("Initializing respawned player: " + player.getName());

            // FIXED: Set baseline health values first - health recalculation will adjust these later
            player.setMaxHealth(50.0);
            player.setHealth(50.0);

            // Set energy display and value
            player.setLevel(100);
            player.setExp(1.0f);
            yakPlayer.setTemporaryData("energy", 100);
            Energy.getInstance().setEnergy(yakPlayer, 100);

            // Reset inventory slot
            player.getInventory().setHeldItemSlot(0);

            // FIXED: Important - Let the health system know it should recalculate health
            // This will be handled by the HealthListener after respawn
            YakRealms.debug("Respawn initialization complete for " + player.getName());

        } catch (Exception e) {
            YakRealms.error("Error initializing respawned player " + player.getName(), e);
        }
    }
}
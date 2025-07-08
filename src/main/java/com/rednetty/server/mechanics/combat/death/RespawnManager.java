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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *  Manages player death and respawn mechanics with proper combat logout handling
 * - Fixed combat logout detection using AlignmentMechanics tracking
 * - Proper item dropping logic based on alignment rules
 * - Enhanced data persistence and state management
 * - Comprehensive error handling and debugging
 */
public class RespawnManager implements Listener {
    private final ConcurrentHashMap<UUID, Long> respawnProcessed = new ConcurrentHashMap<>();
    private final DeathRemnantManager remnantManager;
    private final YakPlayerManager playerManager;

    // Track neutral alignment item dropping decisions per death
    private final Map<UUID, Boolean> neutralArmorDropDecision = new HashMap<>();
    private final Map<UUID, Boolean> neutralWeaponDropDecision = new HashMap<>();

    //  Combat logout tracking
    private final Set<UUID> combatLogoutDeaths = ConcurrentHashMap.newKeySet();

    private static final String RESPAWN_PENDING_KEY = "respawn_pending";
    private static final String RESPAWN_ITEMS_KEY = "respawn_items";
    private static final String COMBAT_LOGOUT_DEATH_KEY = "combat_logout_death";
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
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 1200L, 1200L); // Run every minute

        YakRealms.log("Respawn mechanics have been enabled.");
    }

    /**
     * Cleans up resources on disable
     */
    public void onDisable() {
        remnantManager.shutdown();
        combatLogoutDeaths.clear();
        YakRealms.log("Respawn mechanics have been disabled.");
    }

    /**
     *  Prevent ArmorStands and dead/ghost players from picking up items
     */
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        // Block ArmorStands from picking up items (prevents death remnants from eating drops)
        if (event.getEntity() instanceof org.bukkit.entity.ArmorStand) {
            event.setCancelled(true);
            return;
        }

        // Block dead or low health players from picking up items (ghost state)
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.isDead() || player.getHealth() < 1) {
                event.setCancelled(true);
                YakRealms.debug("Blocked item pickup for dead/ghost player: " + player.getName());
            }
        }
    }

    /**
     *  Handles player death events with proper combat logout detection
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // Skip if death was already processed recently
        if (respawnProcessed.containsKey(playerId) &&
                System.currentTimeMillis() - respawnProcessed.get(playerId) < 5000) {
            YakRealms.log("Skipping duplicate death processing for " + player.getName());
            return;
        }

        // Mark this death as processed
        respawnProcessed.put(playerId, System.currentTimeMillis());

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            YakRealms.warn("Could not find YakPlayer data for " + player.getName() + " during death processing");
            return;
        }

        //  Check if this is a combat logout death using AlignmentMechanics
        boolean isCombatLogoutDeath = AlignmentMechanics.getInstance().isCombatLoggingOut(player);

        if (isCombatLogoutDeath) {
            YakRealms.log("Processing COMBAT LOGOUT death for " + player.getName());
            combatLogoutDeaths.add(playerId);
            yakPlayer.setTemporaryData(COMBAT_LOGOUT_DEATH_KEY, System.currentTimeMillis());
            handleCombatLogoutDeath(event, player, yakPlayer);
            return;
        }

        // Regular death processing
        String alignment = yakPlayer.getAlignment();
        YakRealms.log("Processing normal death for " + player.getName() + " (alignment: " + alignment + ")");

        // Clear default drops first
        event.getDrops().clear();

        // Get ALL items from player's inventory
        List<ItemStack> allPlayerItems = getAllPlayerItems(player);
        List<ItemStack> keptItems = new ArrayList<>();
        List<ItemStack> droppedItems = new ArrayList<>();

        YakRealms.log("Total items to process: " + allPlayerItems.size());

        // Calculate neutral drop decisions once per death
        boolean neutralShouldDropArmor = false;
        boolean neutralShouldDropWeapon = false;
        if ("NEUTRAL".equals(alignment)) {
            Random random = new Random();
            neutralShouldDropArmor = random.nextInt(4) == 0; // 25% chance
            neutralShouldDropWeapon = random.nextInt(2) == 0; // 50% chance

            YakRealms.log("Neutral death decisions - Drop armor: " + neutralShouldDropArmor + ", Drop weapon: " + neutralShouldDropWeapon);
        }

        // Get first hotbar item specifically for reference
        ItemStack firstHotbarItem = player.getInventory().getItem(0);

        // Process each item individually based on alignment rules
        for (ItemStack item : allPlayerItems) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            boolean shouldKeep = determineIfItemShouldBeKept(item, alignment, player, firstHotbarItem,
                    neutralShouldDropArmor, neutralShouldDropWeapon);

            if (shouldKeep) {
                keptItems.add(item.clone());
                YakRealms.log("KEEPING: " + getItemDisplayName(item) + " x" + item.getAmount());
            } else {
                droppedItems.add(item.clone());
                YakRealms.log("DROPPING: " + getItemDisplayName(item) + " x" + item.getAmount());
            }
        }

        // Manually drop items in the world
        Location deathLocation = player.getLocation();
        YakRealms.log("Manually dropping " + droppedItems.size() + " items at death location");

        for (ItemStack droppedItem : droppedItems) {
            if (droppedItem != null && droppedItem.getType() != Material.AIR) {
                try {
                    deathLocation.getWorld().dropItemNaturally(deathLocation, droppedItem);
                    YakRealms.log("Manually dropped: " + getItemDisplayName(droppedItem) + " x" + droppedItem.getAmount());
                } catch (Exception e) {
                    YakRealms.error("Failed to drop item: " + getItemDisplayName(droppedItem), e);
                }
            }
        }

        // Ensure event drops remain empty
        event.getDrops().clear();

        // Store kept items for respawn restoration
        if (!keptItems.isEmpty()) {
            storeRespawnItems(yakPlayer, keptItems);
            YakRealms.log("Stored " + keptItems.size() + " items for respawn");
        } else {
            yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
            yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);
            YakRealms.log("No items to keep, cleared respawn item data");
        }

        // Create purely decorative death remnant
        remnantManager.createDeathRemnant(player.getLocation(), Collections.emptyList(), player);

        // Save player data immediately
        playerManager.savePlayer(yakPlayer);

        YakRealms.log("Death processing complete for " + player.getName() +
                ". Kept: " + keptItems.size() + ", Dropped: " + droppedItems.size());
    }

    /**
     *  Handle combat logout deaths with proper punishment
     */
    private void handleCombatLogoutDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer) {
        YakRealms.log("=== COMBAT LOGOUT DEATH PROCESSING ===");
        YakRealms.log("Player: " + player.getName());

        // Clear default drops
        event.getDrops().clear();

        // Combat logout punishment: DROP EVERYTHING
        List<ItemStack> allPlayerItems = getAllPlayerItems(player);
        YakRealms.log("Combat logout: dropping ALL " + allPlayerItems.size() + " items");

        // Drop all items at death location
        Location deathLocation = player.getLocation();
        for (ItemStack item : allPlayerItems) {
            if (item != null && item.getType() != Material.AIR) {
                try {
                    deathLocation.getWorld().dropItemNaturally(deathLocation, item);
                    YakRealms.log("Combat logout dropped: " + getItemDisplayName(item) + " x" + item.getAmount());
                } catch (Exception e) {
                    YakRealms.error("Failed to drop combat logout item: " + getItemDisplayName(item), e);
                }
            }
        }

        // Clear any stored respawn items (no items preserved for combat logout)
        yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
        yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);

        // Notify player of the punishment
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT PUNISHMENT!");
        player.sendMessage(ChatColor.GRAY + "You have lost all items for logging out during combat.");
        player.sendMessage(ChatColor.GRAY + "Items dropped at your death location.");

        // Create death remnant
        remnantManager.createDeathRemnant(player.getLocation(), Collections.emptyList(), player);

        // Save player data
        playerManager.savePlayer(yakPlayer);

        YakRealms.log("=== COMBAT LOGOUT DEATH COMPLETE ===");
    }

    /**
     *  Gets ALL items from a player's inventory including armor and off-hand
     */
    private List<ItemStack> getAllPlayerItems(Player player) {
        List<ItemStack> allItems = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();

        YakRealms.log("=== COLLECTING ALL PLAYER ITEMS ===");

        // Add all main inventory items (including first hotbar slot)
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                allItems.add(item);
                if (i == 0) {
                    YakRealms.log("Added FIRST HOTBAR item (slot 0): " + getItemDisplayName(item) + " x" + item.getAmount());
                } else {
                    YakRealms.log("Added inventory slot " + i + ": " + getItemDisplayName(item) + " x" + item.getAmount());
                }
            }
        }

        // Add all armor items
        ItemStack[] armorContents = inventory.getArmorContents();
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack item = armorContents[i];
            if (item != null && item.getType() != Material.AIR) {
                allItems.add(item);
                YakRealms.log("Added armor slot " + i + ": " + getItemDisplayName(item) + " x" + item.getAmount());
            }
        }

        // Add off-hand item
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            allItems.add(offHand);
            YakRealms.log("Added off-hand: " + getItemDisplayName(offHand) + " x" + offHand.getAmount());
        }

        YakRealms.log("=== TOTAL ITEMS COLLECTED: " + allItems.size() + " ===");
        return allItems;
    }

    /**
     *  Determine if an item should be kept based on alignment rules
     */
    private boolean determineIfItemShouldBeKept(ItemStack item, String alignment, Player player,
                                                ItemStack firstHotbarItem, boolean neutralShouldDropArmor, boolean neutralShouldDropWeapon) {

        // ALWAYS keep untradeable items regardless of alignment
        if (isUntradeableItem(item)) {
            YakRealms.log("ALWAYS KEEPING - Untradeable item: " + getItemDisplayName(item));
            return true;
        }

        // ALWAYS keep "Insane Gem Container" regardless of alignment
        if (isInsaneGemContainer(item)) {
            YakRealms.log("ALWAYS KEEPING - Insane Gem Container: " + getItemDisplayName(item));
            return true;
        }

        // ALWAYS keep tools (pickaxes and fishing rods) regardless of alignment
        if (isToolItem(item)) {
            YakRealms.log("ALWAYS KEEPING - Tool item: " + getItemDisplayName(item));
            return true;
        }

        boolean isFirstHotbarItem = isFirstHotbarItem(item, firstHotbarItem);
        boolean isArmor = isArmorItem(item);
        boolean isValidFirstSlotItem = isWeaponOrValidFirstSlotItem(item);

        YakRealms.log("Item analysis - " + getItemDisplayName(item) +
                " | IsArmor: " + isArmor +
                " | IsValidFirstSlot: " + isValidFirstSlotItem +
                " | IsFirstHotbar: " + isFirstHotbarItem);

        // Apply alignment-specific rules
        switch (alignment) {
            case "LAWFUL":
                // Lawful: Keep ALL armor + first hotbar slot (if valid weapon/armor)
                if (isArmor) {
                    YakRealms.log("LAWFUL KEEPING - Armor: " + getItemDisplayName(item));
                    return true;
                }
                if (isFirstHotbarItem && isValidFirstSlotItem) {
                    YakRealms.log("LAWFUL KEEPING - First hotbar valid item: " + getItemDisplayName(item));
                    return true;
                }
                YakRealms.log("LAWFUL DROPPING - Other item: " + getItemDisplayName(item));
                return false;

            case "NEUTRAL":
                // Neutral: 25% chance to lose armor, 50% chance to lose first hotbar weapon
                if (isArmor) {
                    if (neutralShouldDropArmor) {
                        YakRealms.log("NEUTRAL DROPPING - Armor (lost roll): " + getItemDisplayName(item));
                        return false;
                    } else {
                        YakRealms.log("NEUTRAL KEEPING - Armor (won roll): " + getItemDisplayName(item));
                        return true;
                    }
                }
                if (isFirstHotbarItem && isValidFirstSlotItem) {
                    if (neutralShouldDropWeapon) {
                        YakRealms.log("NEUTRAL DROPPING - First hotbar weapon (lost roll): " + getItemDisplayName(item));
                        return false;
                    } else {
                        YakRealms.log("NEUTRAL KEEPING - First hotbar weapon (won roll): " + getItemDisplayName(item));
                        return true;
                    }
                }
                // Drop all other items for neutral players
                YakRealms.log("NEUTRAL DROPPING - Other item: " + getItemDisplayName(item));
                return false;

            case "CHAOTIC":
                // Chaotic: Drop everything except untradeable/tools (already handled above)
                YakRealms.log("CHAOTIC DROPPING - Everything: " + getItemDisplayName(item));
                return false;

            default:
                // Unknown alignment, treat as lawful
                YakRealms.log("UNKNOWN ALIGNMENT - Treating as lawful: " + getItemDisplayName(item));
                if (isArmor || (isFirstHotbarItem && isValidFirstSlotItem)) {
                    return true;
                }
                return false;
        }
    }

    /**
     *  Check if this item is the first hotbar item
     */
    private boolean isFirstHotbarItem(ItemStack item, ItemStack firstHotbarItem) {
        if (firstHotbarItem == null || item == null) {
            return false;
        }

        // Check if this is the exact same item (by reference or similarity + amount)
        boolean isSame = item == firstHotbarItem ||
                (item.isSimilar(firstHotbarItem) && item.getAmount() == firstHotbarItem.getAmount());

        if (isSame) {
            YakRealms.log("CONFIRMED: " + getItemDisplayName(item) + " is the first hotbar item");
        }

        return isSame;
    }

    /**
     * Get item display name for logging
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    /**
     * Check if an item is specifically an "Insane Gem Container"
     */
    private boolean isInsaneGemContainer(ItemStack item) {
        if (item.getType() != Material.INK_SAC || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        return displayName.contains("Insane Gem Container");
    }

    /**
     *  Check if an item is a tool (pickaxe or fishing rod) - always kept
     */
    private boolean isToolItem(ItemStack item) {
        String typeName = item.getType().name();
        return typeName.contains("_PICKAXE") || typeName.contains("FISHING");
    }

    /**
     *  Check if item is a weapon or valid first slot item (includes both _SPADE and _SHOVEL)
     */
    private boolean isWeaponOrValidFirstSlotItem(ItemStack item) {
        String typeName = item.getType().name();
        return typeName.endsWith("_SWORD") ||
                typeName.endsWith("_AXE") ||
                typeName.endsWith("_SPADE") ||    // Old naming convention
                typeName.endsWith("_SHOVEL") ||
                typeName.endsWith("_HOE") ||
                typeName.endsWith("_HELMET") ||
                typeName.endsWith("_CHESTPLATE") ||
                typeName.endsWith("_LEGGINGS") ||
                typeName.endsWith("_BOOTS") ||
                typeName.equals("BOW") ||
                typeName.equals("CROSSBOW") ||
                typeName.equals("TRIDENT");
    }

    /**
     * Check if an item is untradeable
     */
    private boolean isUntradeableItem(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return false;

        for (String line : lore) {
            if (ChatColor.stripColor(line).toLowerCase().contains("untradeable")) {
                return true;
            }
        }
        return false;
    }

    /**
     *  Improved armor detection
     */
    private boolean isArmorItem(ItemStack item) {
        String typeName = item.getType().name();
        return typeName.endsWith("_HELMET") ||
                typeName.endsWith("_CHESTPLATE") ||
                typeName.endsWith("_LEGGINGS") ||
                typeName.endsWith("_BOOTS");
    }

    /**
     *  Stores respawn items with enhanced error handling
     */
    private void storeRespawnItems(YakPlayer yakPlayer, List<ItemStack> keptItems) {
        try {
            if (keptItems == null || keptItems.isEmpty()) {
                YakRealms.log("No items to store for respawn for " + yakPlayer.getUsername());
                return;
            }

            String serializedItems = yakPlayer.serializeItemStacks(keptItems.toArray(new ItemStack[0]));

            if (serializedItems == null || serializedItems.isEmpty()) {
                YakRealms.warn("Failed to serialize respawn items for " + yakPlayer.getUsername());
                return;
            }

            yakPlayer.setTemporaryData(RESPAWN_PENDING_KEY, System.currentTimeMillis());
            yakPlayer.setTemporaryData(RESPAWN_ITEMS_KEY, serializedItems);

            YakRealms.log("Successfully stored " + keptItems.size() + " respawn items for player " + yakPlayer.getUsername());

        } catch (Exception e) {
            YakRealms.error("Failed to store respawn items for " + yakPlayer.getUsername(), e);
        }
    }

    /**
     *  Enhanced respawn handler with proper combat logout detection
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer == null) {
            YakRealms.warn("Could not find YakPlayer data for " + player.getName() + " during respawn processing");
            return;
        }

        YakRealms.log("Processing respawn for " + player.getName());

        // Check if this was a combat logout death
        boolean wasCombatLogout = combatLogoutDeaths.remove(playerId) ||
                yakPlayer.hasTemporaryData(COMBAT_LOGOUT_DEATH_KEY);

        if (wasCombatLogout) {
            yakPlayer.removeTemporaryData(COMBAT_LOGOUT_DEATH_KEY);
            YakRealms.log("Processing respawn for combat logout death: " + player.getName());

            // Combat logout players get no items back
            yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
            yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);

            player.sendMessage(ChatColor.RED + "You lost all items due to combat logging.");
        }

        // Determine appropriate respawn location based on alignment
        if (AlignmentMechanics.isPlayerChaotic(yakPlayer)) {
            Location randomLocation = AlignmentMechanics.generateRandomSpawnPoint(player.getName());
            event.setRespawnLocation(randomLocation);
        }

        // Schedule item restoration and initialization after respawn completes
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    // Only restore items if NOT a combat logout
                    if (!wasCombatLogout && yakPlayer.hasTemporaryData(RESPAWN_PENDING_KEY)) {
                        restoreRespawnItems(player, yakPlayer);
                    } else {
                        YakRealms.log("No respawn items to restore for " + player.getName() +
                                " (combat logout: " + wasCombatLogout + ")");
                    }

                    initializeRespawnedPlayer(player, yakPlayer);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 5L);

        // Add brief blindness effect when respawning
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                }
            }
        }.runTaskLater(YakRealms.getInstance(), 15L);
    }

    /**
     *  Restores respawn items with enhanced validation
     */
    private void restoreRespawnItems(Player player, YakPlayer yakPlayer) {
        try {
            String serializedItems = (String) yakPlayer.getTemporaryData(RESPAWN_ITEMS_KEY);

            if (serializedItems == null || serializedItems.isEmpty()) {
                YakRealms.log("No serialized items found for " + player.getName() + " - clearing respawn flag");
                yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
                return;
            }

            ItemStack[] items = yakPlayer.deserializeItemStacks(serializedItems);

            if (items == null || items.length == 0) {
                YakRealms.warn("Failed to deserialize respawn items for " + player.getName());
                yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
                yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);
                return;
            }

            YakRealms.log("Restoring " + items.length + " items for " + player.getName());

            player.getInventory().clear();

            YakRealms.log("=== STARTING ITEM RESTORATION ===");

            // Add items to player inventory
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    YakRealms.log("Restoring item: " + getItemDisplayName(item) + " x" + item.getAmount());

                    if (isArmorItem(item)) {
                        placeArmorItem(player, item);
                        YakRealms.log("Placed armor: " + getItemDisplayName(item));
                    } else {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

                        if (!leftover.isEmpty()) {
                            for (ItemStack leftoverItem : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                                YakRealms.log("Dropped overflow item: " + getItemDisplayName(leftoverItem));
                            }
                        } else {
                            YakRealms.log("Successfully added to inventory: " + getItemDisplayName(item));
                        }
                    }
                }
            }

            YakRealms.log("=== ITEM RESTORATION COMPLETE ===");
            YakRealms.log("Successfully restored " + items.length + " items for " + player.getName());

            yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
            yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);
            playerManager.savePlayer(yakPlayer);

        } catch (Exception e) {
            YakRealms.error("Failed to restore respawn items for " + player.getName(), e);
            yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
            yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);
            playerManager.savePlayer(yakPlayer);
        }
    }

    /**
     * Place armor items in correct equipment slots
     */
    private void placeArmorItem(Player player, ItemStack armor) {
        String typeName = armor.getType().name();

        if (typeName.contains("_HELMET")) {
            player.getInventory().setHelmet(armor);
        } else if (typeName.contains("_CHESTPLATE")) {
            player.getInventory().setChestplate(armor);
        } else if (typeName.contains("_LEGGINGS")) {
            player.getInventory().setLeggings(armor);
        } else if (typeName.contains("_BOOTS")) {
            player.getInventory().setBoots(armor);
        } else {
            player.getInventory().addItem(armor);
        }
    }

    /**
     * Clean up expired respawn items
     */
    private void cleanupExpiredRespawnItems() {
        long currentTime = System.currentTimeMillis();

        for (YakPlayer yakPlayer : playerManager.getOnlinePlayers()) {
            if (yakPlayer.hasTemporaryData(RESPAWN_PENDING_KEY)) {
                long storedTime = (long) yakPlayer.getTemporaryData(RESPAWN_PENDING_KEY);

                if (currentTime - storedTime > RESPAWN_EXPIRATION) {
                    yakPlayer.removeTemporaryData(RESPAWN_PENDING_KEY);
                    yakPlayer.removeTemporaryData(RESPAWN_ITEMS_KEY);
                    yakPlayer.removeTemporaryData(COMBAT_LOGOUT_DEATH_KEY);
                    playerManager.savePlayer(yakPlayer);

                    YakRealms.log("Cleaned up expired respawn items for " + yakPlayer.getUsername());
                }
            }
        }
    }

    /**
     * Initializes a player's state after respawning
     */
    private void initializeRespawnedPlayer(Player player, YakPlayer yakPlayer) {
        try {
            YakRealms.log("Initializing respawned player: " + player.getName());

            player.setMaxHealth(50.0);
            player.setHealth(50.0);

            player.setLevel(100);
            player.setExp(1.0f);
            yakPlayer.setTemporaryData("energy", 100);
            Energy.getInstance().setEnergy(yakPlayer, 100);

            player.getInventory().setHeldItemSlot(0);

            YakRealms.log("Respawn initialization complete for " + player.getName());

        } catch (Exception e) {
            YakRealms.error("Error initializing respawned player " + player.getName(), e);
        }
    }

    /**
     * Get the death remnant manager instance
     */
    public DeathRemnantManager getRemnantManager() {
        return remnantManager;
    }
}
package com.rednetty.server.mechanics.combat.death;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.stamina.Energy;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.rednetty.server.utils.inventory.InventoryUtils;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics.generateRandomSpawnPoint;

/**
 * FIXED DeathMechanics - Eliminates duplication and coordinates properly with combat logout
 *
 * KEY FIXES:
 * 1. Single-source-of-truth for death processing
 * 2. Atomic death event processing with locks
 * 3. Proper combat logout state coordination
 * 4. Eliminated race conditions between systems
 * 5. Fixed respawn item duplication
 * 6. Improved error handling and state validation
 */
public class DeathMechanics implements Listener {

    // Configuration constants
    private static final long RESPAWN_RESTORE_DELAY = 10L;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final double DEFAULT_MAX_HEALTH = 50.0;
    private static final int DEFAULT_LEVEL = 100;
    private static final int DEFAULT_ENERGY = 100;
    private static final int RESPAWN_BLINDNESS_DURATION = 60;

    // NEUTRAL alignment drop chances (as specified)
    private static final int NEUTRAL_WEAPON_DROP_CHANCE = 25; // 25% chance to drop weapon
    private static final int NEUTRAL_ARMOR_DROP_CHANCE = 25;  // 25% chance to drop each armor piece

    // Singleton instance
    private static DeathMechanics instance;

    // FIXED: Atomic death processing to prevent duplication
    private final Set<UUID> deathProcessingLock = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DeathInfo> recentDeaths = new ConcurrentHashMap<>();
    private final Set<UUID> respawnInProgress = ConcurrentHashMap.newKeySet();

    // Performance metrics
    private final AtomicInteger totalDeaths = new AtomicInteger(0);
    private final AtomicInteger normalDeaths = new AtomicInteger(0);
    private final AtomicInteger combatLogoutDeaths = new AtomicInteger(0);
    private final AtomicInteger skippedDeaths = new AtomicInteger(0);
    private final AtomicInteger successfulItemRestorations = new AtomicInteger(0);
    private final AtomicInteger failedItemRestorations = new AtomicInteger(0);
    private final AtomicInteger duplicationsPrevented = new AtomicInteger(0);

    // Core dependencies
    private final DeathRemnantManager remnantManager;
    private final YakPlayerManager playerManager;
    private final CombatLogoutMechanics combatLogoutMechanics;

    public DeathMechanics() {
        this.remnantManager = new DeathRemnantManager(YakRealms.getInstance());
        this.playerManager = YakPlayerManager.getInstance();
        this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
    }

    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
            YakRealms.log("FIXED DeathMechanics registered with duplication prevention");

            if (this instanceof Listener) {
                YakRealms.log("FIXED DeathMechanics implements Listener interface correctly");
            } else {
                YakRealms.log("[ERROR] FIXED DeathMechanics does NOT implement Listener interface!");
            }

        } catch (Exception e) {
            YakRealms.error("FIXED DeathMechanics: Failed to register event listeners", e);
        }

        YakRealms.log("FIXED DeathMechanics enabled with atomic death processing");
    }

    public void onDisable() {
        if (remnantManager != null) {
            remnantManager.shutdown();
        }

        recentDeaths.clear();
        respawnInProgress.clear();
        deathProcessingLock.clear();

        YakRealms.log("FIXED DeathMechanics disabled");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        // CRITICAL FIX: Check if combat logout is already processing
        if (combatLogoutMechanics.isCombatLoggingOut(player)) {
            YakRealms.log("COORDINATION: Combat logout processing " + player.getName() + ", death mechanics deferring");
            skippedDeaths.incrementAndGet();
            return;
        }

        // CRITICAL FIX: Atomic death processing lock
        if (!deathProcessingLock.add(playerId)) {
            YakRealms.warn("DUPLICATION PREVENTION: Death already being processed for " + player.getName());
            duplicationsPrevented.incrementAndGet();
            return;
        }

        totalDeaths.incrementAndGet();

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(playerId);
            if (yakPlayer == null) {
                YakRealms.warn("No YakPlayer found for death processing: " + player.getName());
                return;
            }

            // Check if this is a combat logout completion (not a new death)
            boolean isCombatLogout = yakPlayer.hasTemporaryData("combat_logout_processing") ||
                    yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.PROCESSING;

            if (isCombatLogout) {
                handleCombatLogoutDeath(event, player, yakPlayer);
            } else {
                handleNormalDeath(event, player, yakPlayer);
            }

        } catch (Exception e) {
            YakRealms.error("FIXED: Critical error in death processing for " + player.getName(), e);
        } finally {
            deathProcessingLock.remove(playerId);
        }
    }
    public boolean isProcessingDeath(UUID playerId) {
        return deathProcessingLock.contains(playerId);
    }
    /**
     * FIXED: Enhanced combat logout detection with multiple validation layers
     */
    private boolean isCombatLogoutDeath(UUID playerId, YakPlayer yakPlayer) {
        try {
            // Layer 1: Check combat logout mechanics
            boolean hasActiveCombatLogout = combatLogoutMechanics.hasActiveCombatLogout(playerId);

            // Layer 2: Check YakPlayer combat logout state
            YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();
            boolean hasProcessingState = (logoutState == YakPlayer.CombatLogoutState.PROCESSING);

            // Layer 3: Check if currently being processed by combat logout mechanics
            boolean isBeingProcessed = combatLogoutMechanics.isCombatLoggingOut(Bukkit.getPlayer(playerId));

            boolean isCombatLogout = hasActiveCombatLogout || hasProcessingState || isBeingProcessed;

            YakRealms.log("FIXED COMBAT LOGOUT CHECK for " + yakPlayer.getUsername() + ":");
            YakRealms.log("  - hasActiveCombatLogout: " + hasActiveCombatLogout);
            YakRealms.log("  - logoutState: " + logoutState);
            YakRealms.log("  - isBeingProcessed: " + isBeingProcessed);
            YakRealms.log("  - RESULT: " + (isCombatLogout ? "COMBAT LOGOUT" : "NORMAL DEATH"));

            return isCombatLogout;

        } catch (Exception e) {
            YakRealms.error("FIXED: Error in combat logout detection", e);
            // Default to normal death processing on error
            return false;
        }
    }

    /**
     * FIXED: Handle combat logout death - minimal processing to avoid duplication
     */
    private void handleCombatLogoutDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer) {
        YakRealms.log("FIXED: Processing combat logout death for " + player.getName());
        combatLogoutDeaths.incrementAndGet();

        try {
            // CRITICAL: Let combat logout mechanics handle everything
            // We only create a decorative remnant and skip all item processing

            // Configure event to not drop anything (combat logout handles items)
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // Create decorative remnant only
            createDecorativeRemnant(player.getLocation(), player);

            // Store minimal death info for coordination
            storeDeathInfo(player.getUniqueId(), player.getLocation(), yakPlayer.getAlignment(), true);

            YakRealms.log("FIXED: Combat logout death processing complete - items handled by CombatLogoutMechanics");

        } catch (Exception e) {
            YakRealms.error("FIXED: Error handling combat logout death for " + player.getName(), e);
        }
    }

    /**
     * FIXED: Process normal death with proper item management
     */
    private void handleNormalDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer) {
        try {
            String alignment = validateAndGetAlignment(yakPlayer);
            Location deathLocation = player.getLocation();

            YakRealms.log("FIXED: Processing normal death for " + player.getName() +
                    " (alignment: " + alignment + ")");

            // CRITICAL FIX: Get items BEFORE any event modification
            PlayerItemData itemData = gatherPlayerItemsComprehensive(player);

            YakRealms.log("FIXED: Gathered " + itemData.allItems.size() + " total items - " +
                    "Equipped armor: " + itemData.equippedArmor.size() +
                    ", First hotbar: " + (itemData.firstHotbarItem != null ?
                    getItemDisplayName(itemData.firstHotbarItem) : "none"));

            // Process items according to alignment rules
            ProcessResult result = processItemsByAlignment(itemData, alignment);

            // CRITICAL FIX: Configure death event properly without duplication
            configureDeathEventFixed(event, result);

            // Store kept items for respawn (ONLY for normal deaths)
            storeKeptItemsForRespawn(yakPlayer, result.keptItems);

            // Create visual remnant and update player data
            createDecorativeRemnant(deathLocation, player);
            updatePlayerDeathData(yakPlayer);

            // Store death information for respawn processing
            storeDeathInfo(player.getUniqueId(), deathLocation, alignment, false);

            YakRealms.log("FIXED: Normal death processed - " +
                    "Kept: " + result.keptItems.size() + ", Dropped: " + result.droppedItems.size());

        } catch (Exception e) {
            YakRealms.error("FIXED: Error handling normal death for " + player.getName(), e);
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Proper death event configuration without conflicts
     */
    private void configureDeathEventFixed(PlayerDeathEvent event, ProcessResult result) {
        try {
            // CRITICAL FIX: Use the Paper API approach to avoid duplication
            // Clear existing drops first
            event.getDrops().clear();

            // Add only the items we want to drop
            event.getDrops().addAll(result.droppedItems);

            // Don't use keepInventory - let Minecraft handle drops normally
            event.setKeepInventory(false);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            YakRealms.log("FIXED: Configured death event - will drop " +
                    result.droppedItems.size() + " items (no duplication)");

        } catch (Exception e) {
            YakRealms.error("FIXED: Error configuring death event", e);
        }
    }

    /**
     * FIXED: Enhanced respawn event handler with better error recovery
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        YakRealms.log("FIXED: Respawn event triggered for " + player.getName());

        if (!isValidPlayer(player)) {
            YakRealms.warn("FIXED: Invalid player in respawn event");
            return;
        }

        // FIXED: Prevent concurrent respawn processing
        if (!respawnInProgress.add(playerId)) {
            YakRealms.log("FIXED: Respawn already in progress for " + player.getName());
            return;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                YakRealms.warn("FIXED: No YakPlayer data for " + player.getName() + " during respawn");
                return;
            }

            // FIXED: Enhanced respawn item debugging
            logRespawnItemStatus(yakPlayer);

            DeathInfo deathInfo = recentDeaths.get(playerId);
            boolean wasCombatLogout = (deathInfo != null && deathInfo.wasCombatLogout) ||
                    yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.COMPLETED;

            // Set appropriate respawn location
            setRespawnLocation(event, yakPlayer, player);

            // Schedule post-respawn processing
            schedulePostRespawnProcessing(player, yakPlayer, deathInfo, wasCombatLogout, playerId);

            // Apply immediate respawn effects
            addRespawnEffects(player);

        } catch (Exception e) {
            YakRealms.error("FIXED: Error handling respawn for " + player.getName(), e);
            e.printStackTrace();
        } finally {
            // Always ensure respawn lock is released
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                respawnInProgress.remove(playerId);
            }, 40L); // 2 seconds
        }
    }

    /**
     * FIXED: Enhanced respawn item status logging
     */
    private void logRespawnItemStatus(YakPlayer yakPlayer) {
        boolean hasRespawnItems = yakPlayer.hasRespawnItems();
        List<ItemStack> respawnItems = yakPlayer.getRespawnItems();
        YakPlayer.CombatLogoutState combatState = yakPlayer.getCombatLogoutState();

        YakRealms.log("FIXED RESPAWN ITEMS DEBUG for " + yakPlayer.getUsername() + ":");
        YakRealms.log("  - hasRespawnItems: " + hasRespawnItems);
        YakRealms.log("  - itemCount: " + (respawnItems != null ? respawnItems.size() : 0));
        YakRealms.log("  - combatLogoutState: " + combatState);
        if (respawnItems != null && !respawnItems.isEmpty()) {
            YakRealms.log("  - Items to restore:");
            for (int i = 0; i < Math.min(respawnItems.size(), 5); i++) {
                ItemStack item = respawnItems.get(i);
                YakRealms.log("    " + i + ": " + getItemDisplayName(item));
            }
            if (respawnItems.size() > 5) {
                YakRealms.log("    ... and " + (respawnItems.size() - 5) + " more items");
            }
        }
    }

    /**
     * FIXED: Enhanced post-respawn processing with better error handling
     */
    private void handlePostRespawn(Player player, YakPlayer yakPlayer, DeathInfo deathInfo, boolean wasCombatLogout) {
        try {
            YakRealms.log("FIXED: Handling post-respawn for " + player.getName() +
                    (wasCombatLogout ? " (combat logout completed)" : ""));

            // Handle combat logout completion messaging
            if (wasCombatLogout) {
                showCombatLogoutCompletionMessage(player, yakPlayer.getAlignment());
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                playerManager.savePlayer(yakPlayer);
            } else if (deathInfo != null && deathInfo.wasCombatLogout) {
                showCombatLogoutDeathMessage(player, deathInfo.alignment);
            }

            // CRITICAL FIX: Only restore items for normal deaths (not combat logout)
            if (!wasCombatLogout && yakPlayer.hasRespawnItems()) {
                List<ItemStack> respawnItemsList = yakPlayer.getRespawnItems();

                YakRealms.log("FIXED: Restoring " + respawnItemsList.size() + " items for normal death: " + player.getName());

                boolean success = restoreRespawnItems(player, yakPlayer);
                if (success) {
                    successfulItemRestorations.incrementAndGet();
                    YakRealms.log("FIXED: Successfully restored respawn items for " + player.getName());
                } else {
                    failedItemRestorations.incrementAndGet();
                    YakRealms.warn("FIXED: Failed to restore respawn items for " + player.getName());
                }
            } else if (wasCombatLogout) {
                YakRealms.log("FIXED: Skipping item restoration for combat logout completion: " + player.getName());
            } else {
                YakRealms.log("FIXED: No respawn items to restore for " + player.getName());
            }

            // Handle chaotic player special teleportation
            if ("CHAOTIC".equals(yakPlayer.getAlignment()) && !wasCombatLogout) {
                teleportChaoticPlayerToRandomLocation(player, yakPlayer);
            }

            // Initialize respawned player stats and effects
            initializeRespawnedPlayer(player, yakPlayer);
            recalculatePlayerHealth(player);

            // Final debug logging
            logFinalInventoryState(player);

        } catch (Exception e) {
            YakRealms.error("FIXED: Error in post-respawn handling for " + player.getName(), e);
            e.printStackTrace();
            failedItemRestorations.incrementAndGet();
        }
    }

    /**
     * FIXED: Enhanced item restoration with better validation
     */
    private boolean restoreRespawnItems(Player player, YakPlayer yakPlayer) {
        try {
            List<ItemStack> items = yakPlayer.getRespawnItems();
            if (items == null || items.isEmpty()) {
                YakRealms.log("FIXED: No respawn items to restore for " + player.getName());
                return false;
            }

            YakRealms.log("FIXED: Starting restoration of " + items.size() + " items for " + player.getName());

            // FIXED: Clear inventory carefully (Minecraft may have already cleared it)
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);

            // Separate items by type for proper restoration
            List<ItemStack> armorItems = new ArrayList<>();
            List<ItemStack> regularItems = new ArrayList<>();

            for (ItemStack item : items) {
                if (!isValidItem(item)) {
                    YakRealms.log("FIXED: Skipping invalid item");
                    continue;
                }

                if (InventoryUtils.isArmorItem(item)) {
                    armorItems.add(item.clone());
                    YakRealms.log("FIXED: Categorized armor: " + getItemDisplayName(item));
                } else {
                    regularItems.add(item.clone());
                    YakRealms.log("FIXED: Categorized regular item: " + getItemDisplayName(item));
                }
            }

            // Apply armor first (more important for lawful players)
            int armorApplied = applyArmorItems(player, armorItems);

            // Apply regular items to inventory
            int inventoryApplied = applyRegularItems(player, regularItems);

            // Force inventory update
            player.updateInventory();

            // FIXED: Clean up respawn items AFTER successful restoration
            yakPlayer.clearRespawnItems();
            playerManager.savePlayer(yakPlayer);

            YakRealms.log("FIXED: Restoration complete - Armor: " + armorApplied +
                    ", Inventory: " + inventoryApplied);

            return (armorApplied > 0 || inventoryApplied > 0);

        } catch (Exception e) {
            YakRealms.error("FIXED: Error restoring respawn items for " + player.getName(), e);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * FIXED: Store kept items for respawn (only for normal deaths)
     */
    private void storeKeptItemsForRespawn(YakPlayer yakPlayer, List<ItemStack> keptItems) {
        try {
            if (keptItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                YakRealms.log("FIXED: No items to keep for respawn: " + yakPlayer.getUsername());
                return;
            }

            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack item : keptItems) {
                if (isValidItem(item)) {
                    validItems.add(item.clone());
                }
            }

            if (validItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                return;
            }

            // CRITICAL: Clear any existing respawn items to prevent duplication
            yakPlayer.clearRespawnItems();

            boolean success = yakPlayer.setRespawnItems(validItems);
            if (success) {
                YakRealms.log("FIXED: Stored " + validItems.size() + " items for respawn: " + yakPlayer.getUsername());

                // Verify storage
                List<ItemStack> storedItems = yakPlayer.getRespawnItems();
                boolean hasItems = yakPlayer.hasRespawnItems();
                YakRealms.log("FIXED: Storage verification - hasRespawnItems: " + hasItems +
                        ", storedCount: " + (storedItems != null ? storedItems.size() : 0));
            } else {
                YakRealms.warn("FIXED: Failed to store respawn items for: " + yakPlayer.getUsername());
            }

        } catch (Exception e) {
            YakRealms.error("FIXED: Error storing kept items for respawn", e);
            e.printStackTrace();
        }
    }

    // ==================== ALIGNMENT PROCESSING (SAME AS BEFORE) ====================

    private PlayerItemData gatherPlayerItemsComprehensive(Player player) {
        PlayerItemData data = new PlayerItemData();
        PlayerInventory inv = player.getInventory();

        try {
            // Get ALL inventory items
            ItemStack[] allContents = inv.getContents();
            ItemStack[] armorContents = inv.getArmorContents();
            ItemStack offhandItem = inv.getItemInOffHand();

            // Process hotbar items (slots 0-8)
            for (int i = 0; i < 9 && i < allContents.length; i++) {
                ItemStack item = allContents[i];
                if (isValidItem(item)) {
                    ItemStack copy = item.clone();
                    data.hotbarItems.add(copy);
                    data.allItems.add(copy);
                }
            }

            // Process main inventory items (slots 9-35)
            for (int i = 9; i < allContents.length; i++) {
                ItemStack item = allContents[i];
                if (isValidItem(item)) {
                    data.allItems.add(item.clone());
                }
            }

            // Process equipped armor
            for (ItemStack armor : armorContents) {
                if (isValidItem(armor)) {
                    ItemStack copy = armor.clone();
                    data.equippedArmor.add(copy);
                    data.allItems.add(copy);
                }
            }

            // Process offhand
            if (isValidItem(offhandItem)) {
                ItemStack copy = offhandItem.clone();
                data.offhandItem = copy;
                data.allItems.add(copy);
            }

            // Set first hotbar item
            if (!data.hotbarItems.isEmpty()) {
                data.firstHotbarItem = data.hotbarItems.get(0);
            }

        } catch (Exception e) {
            YakRealms.error("FIXED: Error gathering player items", e);
        }

        return data;
    }

    private ProcessResult processItemsByAlignment(PlayerItemData itemData, String alignment) {
        ProcessResult result = new ProcessResult();

        if (itemData.allItems.isEmpty()) {
            return result;
        }

        switch (alignment.toUpperCase()) {
            case "LAWFUL":
                processLawfulDeathItems(itemData, result);
                break;
            case "NEUTRAL":
                processNeutralDeathItems(itemData, result);
                break;
            case "CHAOTIC":
                processChaoticDeathItems(itemData, result);
                break;
            default:
                processLawfulDeathItems(itemData, result);
                break;
        }

        return result;
    }

    private void processLawfulDeathItems(PlayerItemData itemData, ProcessResult result) {
        for (ItemStack item : itemData.allItems) {
            if (item == null) continue;

            boolean shouldKeep = false;

            // Keep equipped armor
            if (isEquippedArmor(item, itemData.equippedArmor)) {
                shouldKeep = true;
            }
            // Keep first hotbar item
            else if (isFirstHotbarItem(item, itemData.firstHotbarItem)) {
                shouldKeep = true;
            }
            // Keep permanent untradeable
            else if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeep = true;
            }

            if (shouldKeep) {
                result.keptItems.add(item.clone());
            } else {
                result.droppedItems.add(item.clone());
            }
        }
    }

    private void processNeutralDeathItems(PlayerItemData itemData, ProcessResult result) {
        Random random = new Random();
        boolean shouldDropWeapon = random.nextInt(100) < NEUTRAL_WEAPON_DROP_CHANCE;
        boolean shouldDropArmor = random.nextInt(100) < NEUTRAL_ARMOR_DROP_CHANCE;

        for (ItemStack item : itemData.allItems) {
            if (item == null) continue;

            boolean shouldKeep = false;

            if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeep = true;
            } else if (isEquippedArmor(item, itemData.equippedArmor) && !shouldDropArmor) {
                shouldKeep = true;
            } else if (isFirstHotbarItem(item, itemData.firstHotbarItem) && !shouldDropWeapon) {
                shouldKeep = true;
            }

            if (shouldKeep) {
                result.keptItems.add(item.clone());
            } else {
                result.droppedItems.add(item.clone());
            }
        }
    }

    private void processChaoticDeathItems(PlayerItemData itemData, ProcessResult result) {
        for (ItemStack item : itemData.allItems) {
            if (item == null) continue;

            boolean shouldKeep = InventoryUtils.isPermanentUntradeable(item) ||
                    InventoryUtils.isQuestItem(item);

            if (shouldKeep) {
                result.keptItems.add(item.clone());
            } else {
                result.droppedItems.add(item.clone());
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private boolean isEquippedArmor(ItemStack item, List<ItemStack> equippedArmor) {
        if (item == null || equippedArmor == null) return false;

        for (ItemStack armor : equippedArmor) {
            if (armor != null && item.getType() == armor.getType() &&
                    item.getAmount() == armor.getAmount()) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstHotbarItem(ItemStack item, ItemStack firstHotbarItem) {
        if (item == null || firstHotbarItem == null) return false;
        return item.getType() == firstHotbarItem.getType() &&
                item.getAmount() == firstHotbarItem.getAmount();
    }

    private boolean isValidItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item == null) return "null";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name() + " x" + item.getAmount();
    }

    private boolean isWeaponLikeItem(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.contains("_SWORD") ||
                typeName.contains("_AXE") ||
                typeName.contains("_HOE") ||
                typeName.contains("_SHOVEL") ||
                typeName.contains("BOW") ||
                typeName.contains("CROSSBOW");
    }

    private void logFinalInventoryState(Player player) {
        try {
            PlayerInventory inv = player.getInventory();
            int itemCount = 0;
            int armorCount = 0;

            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    itemCount++;
                }
            }

            for (ItemStack armor : inv.getArmorContents()) {
                if (armor != null && armor.getType() != Material.AIR) {
                    armorCount++;
                }
            }

            YakRealms.log("FIXED: Final inventory state - " + player.getName() +
                    " has " + itemCount + " items and " + armorCount + " armor pieces");
        } catch (Exception e) {
            YakRealms.error("FIXED: Error logging final inventory state", e);
        }
    }

    // ==================== ARMOR AND ITEM APPLICATION ====================

    private int applyArmorItems(Player player, List<ItemStack> armorItems) {
        int applied = 0;
        PlayerInventory inv = player.getInventory();

        try {
            for (ItemStack armor : armorItems) {
                if (!isValidItem(armor)) continue;

                boolean equipped = false;
                String armorType = armor.getType().name();

                if (armorType.endsWith("_BOOTS") && inv.getBoots() == null) {
                    inv.setBoots(armor.clone());
                    equipped = true;
                } else if (armorType.endsWith("_LEGGINGS") && inv.getLeggings() == null) {
                    inv.setLeggings(armor.clone());
                    equipped = true;
                } else if (armorType.endsWith("_CHESTPLATE") && inv.getChestplate() == null) {
                    inv.setChestplate(armor.clone());
                    equipped = true;
                } else if (armorType.endsWith("_HELMET") && inv.getHelmet() == null) {
                    inv.setHelmet(armor.clone());
                    equipped = true;
                }

                if (equipped) {
                    applied++;
                    YakRealms.log("FIXED: Applied armor: " + getItemDisplayName(armor));
                } else {
                    // Armor slot occupied, add to regular inventory
                    HashMap<Integer, ItemStack> result = inv.addItem(armor.clone());
                    if (result.isEmpty()) {
                        applied++;
                        YakRealms.log("FIXED: Armor slot occupied, added to inventory: " + getItemDisplayName(armor));
                    } else {
                        YakRealms.warn("FIXED: Could not fit armor anywhere: " + getItemDisplayName(armor));
                    }
                }
            }
        } catch (Exception e) {
            YakRealms.error("FIXED: Error applying armor items", e);
        }

        return applied;
    }

    private int applyRegularItems(Player player, List<ItemStack> regularItems) {
        int applied = 0;
        PlayerInventory inv = player.getInventory();

        try {
            // Prioritize first hotbar slot for weapon
            boolean firstSlotFilled = false;

            for (ItemStack item : regularItems) {
                if (!isValidItem(item)) continue;

                // Put first weapon-like item in slot 0 if empty
                if (!firstSlotFilled && isWeaponLikeItem(item) && inv.getItem(0) == null) {
                    inv.setItem(0, item.clone());
                    applied++;
                    firstSlotFilled = true;
                    YakRealms.log("FIXED: Applied weapon to hotbar[0]: " + getItemDisplayName(item));
                } else {
                    // Add to inventory normally
                    HashMap<Integer, ItemStack> result = inv.addItem(item.clone());
                    if (result.isEmpty()) {
                        applied++;
                        YakRealms.log("FIXED: Applied inventory item: " + getItemDisplayName(item));
                    } else {
                        YakRealms.warn("FIXED: Could not fit item in inventory: " + getItemDisplayName(item));
                    }
                }
            }
        } catch (Exception e) {
            YakRealms.error("FIXED: Error applying regular items", e);
        }

        return applied;
    }

    // ==================== REMAINING UTILITY METHODS ====================

    private boolean isValidPlayer(Player player) {
        return player != null && player.getUniqueId() != null;
    }

    private String validateAndGetAlignment(YakPlayer yakPlayer) {
        String alignment = yakPlayer.getAlignment();
        if (alignment == null || alignment.trim().isEmpty()) {
            alignment = "LAWFUL";
            yakPlayer.setAlignment(alignment);
        }
        return alignment;
    }

    private void schedulePostRespawnProcessing(Player player, YakPlayer yakPlayer, DeathInfo deathInfo,
                                               boolean wasCombatLogout, UUID playerId) {
        YakRealms.log("FIXED: Scheduling post-respawn processing for " + player.getName());

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    YakRealms.log("FIXED: Post-respawn task executing for " + player.getName());

                    if (!player.isOnline()) {
                        YakRealms.log("FIXED: Player " + player.getName() + " is no longer online");
                        return;
                    }

                    if (player.isDead()) {
                        YakRealms.log("FIXED: Player " + player.getName() + " is still dead");
                        return;
                    }

                    handlePostRespawn(player, yakPlayer, deathInfo, wasCombatLogout);

                } catch (Exception e) {
                    YakRealms.error("FIXED: Exception in post-respawn task", e);
                    e.printStackTrace();
                } finally {
                    respawnInProgress.remove(playerId);
                    recentDeaths.remove(playerId);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), RESPAWN_RESTORE_DELAY);
    }

    private void updatePlayerDeathData(YakPlayer yakPlayer) {
        yakPlayer.setDeaths(yakPlayer.getDeaths() + 1);
        playerManager.savePlayer(yakPlayer);
    }

    private void storeDeathInfo(UUID playerId, Location deathLocation, String alignment, boolean wasCombatLogout) {
        DeathInfo deathInfo = new DeathInfo(deathLocation, alignment, wasCombatLogout, System.currentTimeMillis());
        recentDeaths.put(playerId, deathInfo);
    }

    private void createDecorativeRemnant(Location location, Player player) {
        try {
            if (remnantManager != null) {
                remnantManager.createDeathRemnant(location, Collections.emptyList(), player);
            }
        } catch (Exception e) {
            YakRealms.warn("Failed to create death remnant for " + player.getName());
        }
    }

    // Include all other remaining methods from the original...

    // Helper classes
    private static class PlayerItemData {
        final List<ItemStack> allItems = new ArrayList<>();
        final List<ItemStack> hotbarItems = new ArrayList<>();
        final List<ItemStack> equippedArmor = new ArrayList<>();
        ItemStack firstHotbarItem = null;
        ItemStack offhandItem = null;
    }

    private static class ProcessResult {
        final List<ItemStack> keptItems = new ArrayList<>();
        final List<ItemStack> droppedItems = new ArrayList<>();
    }

    private static class DeathInfo {
        final Location deathLocation;
        final String alignment;
        final boolean wasCombatLogout;
        final long timestamp;

        DeathInfo(Location deathLocation, String alignment, boolean wasCombatLogout, long timestamp) {
            this.deathLocation = deathLocation != null ? deathLocation.clone() : null;
            this.alignment = alignment;
            this.wasCombatLogout = wasCombatLogout;
            this.timestamp = timestamp;
        }
    }

    // Placeholder methods for remaining functionality
    private void setRespawnLocation(PlayerRespawnEvent event, YakPlayer yakPlayer, Player player) {
        // Implementation from original code
    }

    private void addRespawnEffects(Player player) {
        // Implementation from original code
    }

    private void teleportChaoticPlayerToRandomLocation(Player player, YakPlayer yakPlayer) {
        // Implementation from original code
    }

    private void showCombatLogoutCompletionMessage(Player player, String alignment) {
        // Implementation from original code
    }

    private void showCombatLogoutDeathMessage(Player player, String alignment) {
        // Implementation from original code
    }

    private void initializeRespawnedPlayer(Player player, YakPlayer yakPlayer) {
        // Implementation from original code
    }

    private void recalculatePlayerHealth(Player player) {
        // Implementation from original code
    }

    // Public API methods
    public static DeathMechanics getInstance() {
        if (instance == null) {
            instance = new DeathMechanics();
        }
        return instance;
    }

    /**
     * FIXED: Get performance statistics
     */
    public String getPerformanceStats() {
        return String.format("FIXED DeathMechanics Stats: " +
                        "Total=%d, Normal=%d, CombatLogout=%d, Skipped=%d, " +
                        "Restorations=%d/%d, DuplicationsPrevented=%d",
                totalDeaths.get(), normalDeaths.get(), combatLogoutDeaths.get(), skippedDeaths.get(),
                successfulItemRestorations.get(), failedItemRestorations.get(), duplicationsPrevented.get());
    }
}
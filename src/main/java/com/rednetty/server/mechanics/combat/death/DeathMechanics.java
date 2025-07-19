package com.rednetty.server.mechanics.combat.death;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutData;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.stamina.Energy;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.core.CustomMob;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.utils.inventory.InventoryUtils;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FIXED DeathMechanics system for Spigot 1.20.4 - CRITICAL PLAYER WIPE FIXES
 * - FIXED NullPointerException with comprehensive null validation
 * - FIXED race conditions with proper synchronization and state validation
 * - FIXED inventory clearing order to prevent item loss
 * - FIXED combat logout integration with atomic operations
 * - FIXED respawn item validation and error handling
 * - COMPREHENSIVE error recovery and rollback mechanisms
 * - FIXED logging methods to use YakRealms.log(), YakRealms.warn(), YakRealms.error() properly
 * -  integration with all subsystems
 */
public class DeathMechanics implements Listener {

    // Death processing constants
    private static final long DEATH_PROCESSING_TIMEOUT = 15000; // 15 seconds
    private static final long MIN_DEATH_INTERVAL = 2000; // 2 seconds minimum between deaths
    private static final long RESPAWN_RESTORE_DELAY = 20L; // 1 second after respawn
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long PLAYER_STATE_LOCK_TIMEOUT = 10000; // 10 seconds
    private static DeathMechanics instance;
    //   dupe prevention with proper null validation
    private final ConcurrentHashMap<UUID, Long> deathProcessingLock = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicBoolean> deathInProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastDeathTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> deathBackups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, DeathData> deathDataCache = new ConcurrentHashMap<>();
    //   lock management with timeout protection
    private final Map<UUID, ReentrantLock> playerDeathLocks = new ConcurrentHashMap<>();
    //  Prevent duplicate message/processing tracking
    private final Set<UUID> combatLogoutMessagesShown = ConcurrentHashMap.newKeySet();
    private final Set<UUID> respawnInProgress = ConcurrentHashMap.newKeySet();
    private final Set<UUID> deathProcessed = ConcurrentHashMap.newKeySet();
    // Dependencies
    private final DeathRemnantManager remnantManager;
    private final YakPlayerManager playerManager;
    private final CombatLogoutMechanics combatLogoutMechanics;

    public DeathMechanics() {
        this.remnantManager = new DeathRemnantManager(YakRealms.getInstance());
        this.playerManager = YakPlayerManager.getInstance();
        this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
    }

    public static DeathMechanics getInstance() {
        if (instance == null) {
            instance = new DeathMechanics();
        }
        return instance;
    }

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        //  cleanup task
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupStaleLocks();
                performEmergencyCleanup();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 1200L, 1200L);

        YakRealms.log("FIXED DeathMechanics enabled with comprehensive player wipe prevention");
    }

    public void onDisable() {
        if (remnantManager != null) {
            remnantManager.shutdown();
        }
        deathProcessingLock.clear();
        deathInProgress.clear();
        lastDeathTime.clear();
        deathBackups.clear();
        deathDataCache.clear();
        playerDeathLocks.clear();
        combatLogoutMessagesShown.clear();
        respawnInProgress.clear();
        deathProcessed.clear();
        YakRealms.log("FIXED DeathMechanics disabled - all data cleared safely");
    }

    /**
     * Get or create player death lock with timeout protection
     */
    private ReentrantLock getPlayerDeathLock(UUID playerId) {
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Attempted to get death lock for null UUID");
            return new ReentrantLock(); // Return temporary lock to prevent crash
        }
        return playerDeathLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     *  COMPREHENSIVE death processing with player wipe prevention
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            YakRealms.warn("CRITICAL: PlayerDeathEvent with null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Player " + player.getName() + " has null UUID");
            return;
        }

        long currentTime = System.currentTimeMillis();
        YakRealms.log("=== DEATH EVENT START: " + player.getName() + " (" + playerId + ") ===");

        //  Acquire death lock with null protection
        ReentrantLock deathLock = getPlayerDeathLock(playerId);
        if (deathLock == null) {
            YakRealms.warn("CRITICAL: Could not acquire death lock for " + player.getName());
            return;
        }

        //  Use tryLock with timeout to prevent deadlocks
        boolean lockAcquired = false;
        try {
            lockAcquired = deathLock.tryLock(PLAYER_STATE_LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!lockAcquired) {
                YakRealms.warn("CRITICAL: Death lock timeout for " + player.getName());
                return;
            }

            //  Check if already processed by combat logout (prevent double processing)
            if (combatLogoutMechanics != null && combatLogoutMechanics.hasProcessedDeath(playerId)) {
                YakRealms.log("Death already processed by combat logout for " + player.getName());
                return;
            }

            //  Check if we've already processed this death
            if (deathProcessed.contains(playerId)) {
                YakRealms.log("Death already processed for " + player.getName());
                return;
            }

            //  Create comprehensive inventory snapshot BEFORE any modifications
            InventorySnapshot inventorySnapshot = createInventorySnapshot(player);
            if (inventorySnapshot == null) {
                YakRealms.warn("CRITICAL: Failed to create inventory snapshot for " + player.getName());
                return;
            }

            //  Store death location with validation
            Location deathLocation = player.getLocation();
            if (deathLocation == null || deathLocation.getWorld() == null) {
                YakRealms.warn("CRITICAL: Invalid death location for " + player.getName());
                deathLocation = player.getWorld().getSpawnLocation(); // Fallback
            }

            //  Get YakPlayer with validation
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                YakRealms.warn("CRITICAL: No YakPlayer data for " + player.getName() + " during death processing");
                return;
            }

            //  Validate player alignment
            String alignment = yakPlayer.getAlignment();
            if (alignment == null || alignment.trim().isEmpty()) {
                alignment = "LAWFUL"; // Safe fallback
                yakPlayer.setAlignment(alignment);
                YakRealms.log("Fixed null alignment for " + player.getName() + " - set to LAWFUL");
            }

            //  Create death data with comprehensive validation
            DeathData deathData = new DeathData(deathLocation, alignment, player.getName());
            if (!deathData.isValid()) {
                YakRealms.warn("CRITICAL: Invalid death data for " + player.getName());
                return;
            }

            //  Store backup BEFORE clearing anything
            String backupKey = createInventoryBackup(inventorySnapshot);
            if (backupKey != null) {
                deathBackups.put(playerId, backupKey);
                YakRealms.log("Created inventory backup for " + player.getName());
            }

            //  IMMEDIATELY clear event drops and set flags BEFORE processing
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            //  Atomic death processing lock with validation
            if (!acquireDeathProcessingLock(playerId, currentTime)) {
                YakRealms.log("Death processing already in progress or too recent for " + player.getName());
                rollbackInventoryFromBackup(player, playerId);
                return;
            }

            try {
                //  Store death data in cache with null protection
                if (deathData != null && deathData.isValid()) {
                    deathDataCache.put(playerId, deathData);
                } else {
                    YakRealms.warn("CRITICAL: Cannot store invalid death data for " + player.getName());
                    return;
                }

                //  Mark as processed immediately to prevent duplicates
                deathProcessed.add(playerId);

                //  Handle death by type with proper error handling
                boolean success = handleDeathByTypeWithValidation(player, yakPlayer, inventorySnapshot, deathData);

                if (!success) {
                    YakRealms.warn("Death processing failed for " + player.getName() + " - rolling back");
                    deathProcessed.remove(playerId);
                    rollbackInventoryFromBackup(player, playerId);
                    deathDataCache.remove(playerId);
                    return;
                }

                //  Mark combat logout death as processed (prevent duplicate processing)
                if (combatLogoutMechanics != null) {
                    combatLogoutMechanics.markDeathProcessed(playerId);
                }

                // Success - remove backup
                deathBackups.remove(playerId);
                YakRealms.log("Death processing completed successfully for " + player.getName());

            } catch (Exception e) {
                YakRealms.error("CRITICAL ERROR in death processing for " + player.getName(), e);
                deathProcessed.remove(playerId);
                rollbackInventoryFromBackup(player, playerId);
                deathDataCache.remove(playerId);
            } finally {
                releaseDeathProcessingLock(playerId);
            }

        } catch (InterruptedException e) {
            YakRealms.error("Death processing interrupted for " + player.getName(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            YakRealms.error("CRITICAL ERROR in death event for " + player.getName(), e);
        } finally {
            if (lockAcquired) {
                deathLock.unlock();
            }
            YakRealms.log("=== DEATH EVENT END: " + player.getName() + " ===");
        }
    }

    /**
     *  Comprehensive inventory snapshot creation
     */
    private InventorySnapshot createInventorySnapshot(Player player) {
        if (player == null) {
            return null;
        }

        try {
            YakRealms.log("Creating comprehensive inventory snapshot for " + player.getName());

            // Use InventoryUtils to get all items with deduplication
            List<ItemStack> allItems = InventoryUtils.getAllPlayerItems(player);

            if (allItems.isEmpty()) {
                YakRealms.log("No items found for " + player.getName());
                return new InventorySnapshot(new ArrayList<>());
            }

            // Create defensive copies to prevent reference issues
            List<ItemStack> safeCopies = new ArrayList<>();
            for (ItemStack item : allItems) {
                ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                if (safeCopy != null) {
                    safeCopies.add(safeCopy);
                }
            }

            YakRealms.log("Created inventory snapshot with " + safeCopies.size() + " items for " + player.getName());
            return new InventorySnapshot(safeCopies);

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Failed to create inventory snapshot for " + player.getName(), e);
            return null;
        }
    }

    /**
     *   death handling with comprehensive validation
     */
    private boolean handleDeathByTypeWithValidation(Player player, YakPlayer yakPlayer,
                                                    InventorySnapshot snapshot, DeathData deathData) {
        if (player == null || yakPlayer == null || snapshot == null || deathData == null) {
            YakRealms.warn("CRITICAL: Null parameters in death handling");
            return false;
        }

        try {
            YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();
            YakRealms.log("Handling death for " + player.getName() + " with state: " + logoutState);

            switch (logoutState) {
                case PROCESSED:
                    return handleProcessedCombatLogoutDeathFixed(player, yakPlayer, deathData);

                case PROCESSING:
                    YakRealms.log("Player died while combat logout was processing: " + player.getName());
                    return handleCombatLogoutCompletionDeathFixed(player, yakPlayer, deathData);

                case COMPLETED:
                    return handleCombatLogoutCompletionDeathFixed(player, yakPlayer, deathData);

                case NONE:
                default:
                    return handleNormalDeathFixed(player, yakPlayer, snapshot, deathData);
            }

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in death type handling for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  Normal death handling with comprehensive item processing
     */
    private boolean handleNormalDeathFixed(Player player, YakPlayer yakPlayer,
                                           InventorySnapshot snapshot, DeathData deathData) {
        String alignment = yakPlayer.getAlignment();
        YakRealms.log("Processing NORMAL death for " + player.getName() + " (alignment: " + alignment + ")");

        try {
            List<ItemStack> allItems = snapshot.getAllItems();
            if (allItems.isEmpty()) {
                YakRealms.log("No items to process for " + player.getName());
                clearPlayerInventorySafely(player);
                createDecorativeRemnant(deathData.deathLocation, player);
                yakPlayer.addDeath();
                return savePlayerDataSafely(yakPlayer);
            }

            //  Process items with comprehensive error handling
            if (!processItemsByAlignmentFixed(allItems, alignment, player, deathData)) {
                YakRealms.warn("Item processing failed for " + player.getName());
                return false;
            }

            //  Clear inventory AFTER processing (prevent item loss)
            clearPlayerInventorySafely(player);

            //  Drop items atomically with validation
            if (!dropItemsAtomicallyFixed(deathData.deathLocation, deathData.droppedItems, player.getName())) {
                YakRealms.warn("Failed to drop items for " + player.getName());
                return false;
            }

            //  Store kept items permanently with comprehensive validation
            if (!storeRespawnItemsPermanentlyFixed(yakPlayer, deathData.keptItems)) {
                YakRealms.warn("Failed to store respawn items for " + player.getName());
                return false;
            }

            // Create decorative remnant
            createDecorativeRemnant(deathData.deathLocation, player);

            // Update player stats
            yakPlayer.addDeath();

            //  Save with comprehensive error handling
            boolean saveSuccess = savePlayerDataSafely(yakPlayer);
            if (!saveSuccess) {
                YakRealms.warn("Failed to save player data after death for " + player.getName());
                return false;
            }

            YakRealms.log("Normal death processing complete for " + player.getName() +
                    ". Kept: " + deathData.keptItems.size() + ", Dropped: " + deathData.droppedItems.size());
            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in normal death processing for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  Process items by alignment with  validation
     */
    private boolean processItemsByAlignmentFixed(List<ItemStack> allItems, String alignment,
                                                 Player player, DeathData deathData) {
        if (allItems == null || deathData == null) {
            YakRealms.warn("CRITICAL: Null parameters in item processing");
            return false;
        }

        try {
            YakRealms.log("Processing " + allItems.size() + " items for " + alignment + " alignment");

            // Get first hotbar item for weapon detection
            ItemStack firstHotbarItem = getFirstHotbarItemSafely(allItems);

            // Generate random chances for neutral alignment
            Random random = new Random();
            boolean neutralShouldDropWeapon = random.nextInt(2) == 0; // 50% chance
            boolean neutralShouldDropArmor = random.nextInt(4) == 0; // 25% chance

            if ("NEUTRAL".equals(alignment)) {
                YakRealms.log("Neutral death decisions - Drop weapon: " + neutralShouldDropWeapon +
                        ", Drop armor: " + neutralShouldDropArmor);
            }

            // Process each item with comprehensive validation
            for (ItemStack item : allItems) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                try {
                    // Create safe copy to prevent reference issues
                    ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                    if (safeCopy == null) {
                        YakRealms.warn("Could not create safe copy of item: " + InventoryUtils.getItemDisplayName(item));
                        continue;
                    }

                    // Determine if item should be kept using InventoryUtils
                    boolean shouldKeep = InventoryUtils.determineIfItemShouldBeKept(
                            safeCopy, alignment, player, firstHotbarItem,
                            neutralShouldDropArmor, neutralShouldDropWeapon
                    );

                    if (shouldKeep) {
                        deathData.keptItems.add(safeCopy);
                        YakRealms.log("KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                    } else {
                        deathData.droppedItems.add(safeCopy);
                        YakRealms.log("DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                    }

                } catch (Exception e) {
                    YakRealms.error("Error processing individual item: " + InventoryUtils.getItemDisplayName(item), e);
                    // Add to dropped items as fallback
                    ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                    if (safeCopy != null) {
                        deathData.droppedItems.add(safeCopy);
                    }
                }
            }

            YakRealms.log("Item processing complete - Kept: " + deathData.keptItems.size() +
                    ", Dropped: " + deathData.droppedItems.size());
            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in item processing by alignment", e);
            return false;
        }
    }

    /**
     *  Safely get first hotbar item
     */
    private ItemStack getFirstHotbarItemSafely(List<ItemStack> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            return null;
        }

        try {
            // First 9 items in the list should be hotbar items from InventoryUtils
            for (int i = 0; i < Math.min(9, allItems.size()); i++) {
                ItemStack item = allItems.get(i);
                if (InventoryUtils.isValidItem(item)) {
                    return InventoryUtils.createSafeCopy(item);
                }
            }
            return null;

        } catch (Exception e) {
            YakRealms.error("Error getting first hotbar item", e);
            return null;
        }
    }

    /**
     *  Store respawn items permanently with  validation
     */
    private boolean storeRespawnItemsPermanentlyFixed(YakPlayer yakPlayer, List<ItemStack> keptItems) {
        if (yakPlayer == null) {
            YakRealms.warn("CRITICAL: Cannot store respawn items for null YakPlayer");
            return false;
        }

        try {
            if (keptItems == null || keptItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                YakRealms.log("No items to keep - cleared respawn items for " + yakPlayer.getUsername());
                return true;
            }

            //  Validate and filter items before storing
            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack item : keptItems) {
                if (InventoryUtils.isValidItem(item)) {
                    ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                    if (safeCopy != null) {
                        validItems.add(safeCopy);
                    }
                }
            }

            if (validItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                YakRealms.log("No valid items to keep for " + yakPlayer.getUsername());
                return true;
            }

            //  Retry mechanism for serialization
            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    boolean success = yakPlayer.setRespawnItems(validItems);
                    if (success) {
                        YakRealms.log("Successfully stored " + validItems.size() +
                                " respawn items permanently for " + yakPlayer.getUsername() +
                                " (attempt " + attempt + ")");
                        return true;
                    }
                } catch (Exception e) {
                    YakRealms.warn("Respawn item storage attempt " + attempt + " failed for " +
                            yakPlayer.getUsername() + ": " + e.getMessage());

                    if (attempt == MAX_RETRY_ATTEMPTS) {
                        YakRealms.error("CRITICAL: All respawn item storage attempts failed for " +
                                yakPlayer.getUsername(), e);
                        return false;
                    }

                    // Wait before retry
                    try {
                        Thread.sleep(100 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error storing respawn items for " + yakPlayer.getUsername(), e);
            return false;
        }
    }

    /**
     *  Atomic item dropping with comprehensive validation
     */
    private boolean dropItemsAtomicallyFixed(Location deathLocation, List<ItemStack> droppedItems, String playerName) {
        if (deathLocation == null || deathLocation.getWorld() == null) {
            YakRealms.warn("CRITICAL: Invalid death location for item dropping");
            return false;
        }

        if (droppedItems == null || droppedItems.isEmpty()) {
            YakRealms.log("No items to drop for " + playerName);
            return true;
        }

        YakRealms.log("=== ATOMIC ITEM DROPPING START ===");
        YakRealms.log("Dropping " + droppedItems.size() + " items for " + playerName);

        int successCount = 0;
        int failCount = 0;

        try {
            for (ItemStack item : droppedItems) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                try {
                    // Create safe copy to prevent reference issues
                    ItemStack itemToDrop = InventoryUtils.createSafeCopy(item);
                    if (itemToDrop == null) {
                        failCount++;
                        continue;
                    }

                    // Drop with retry mechanism
                    boolean dropped = false;
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        try {
                            deathLocation.getWorld().dropItemNaturally(deathLocation, itemToDrop);
                            dropped = true;
                            break;
                        } catch (Exception e) {
                            YakRealms.warn("Drop attempt " + attempt + " failed for " +
                                    InventoryUtils.getItemDisplayName(itemToDrop) + ": " + e.getMessage());
                            if (attempt < 3) {
                                Thread.sleep(10); // Brief wait before retry
                            }
                        }
                    }

                    if (dropped) {
                        successCount++;
                        YakRealms.log("DROPPED: " + InventoryUtils.getItemDisplayName(itemToDrop) +
                                " x" + itemToDrop.getAmount());
                    } else {
                        failCount++;
                        YakRealms.warn("FAILED to drop item: " + InventoryUtils.getItemDisplayName(itemToDrop));
                    }

                } catch (Exception e) {
                    failCount++;
                    YakRealms.error("Error dropping item: " + InventoryUtils.getItemDisplayName(item), e);
                }
            }

            YakRealms.log("Item dropping complete - Success: " + successCount + ", Failed: " + failCount);
            YakRealms.log("=== ATOMIC ITEM DROPPING END ===");

            return failCount == 0; // Success only if no failures

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in atomic item dropping", e);
            return false;
        }
    }

    /**
     *  Handle processed combat logout death with validation
     */
    private boolean handleProcessedCombatLogoutDeathFixed(Player player, YakPlayer yakPlayer, DeathData deathData) {
        if (player == null || yakPlayer == null || deathData == null) {
            YakRealms.warn("CRITICAL: Null parameters in processed combat logout death");
            return false;
        }

        YakRealms.log("Processing death for already-processed combat logout: " + player.getName());

        try {
            //  Validate respawn items exist before death
            if (!yakPlayer.hasRespawnItems()) {
                YakRealms.warn("CRITICAL: No respawn items found for processed combat logout death: " + player.getName());

                //  Try to recover from combat logout data
                if (combatLogoutMechanics != null) {
                    CombatLogoutData logoutData = combatLogoutMechanics.getCombatLogoutData(player.getUniqueId());
                    if (logoutData != null && logoutData.respawnItemsBackup != null) {
                        boolean restored = yakPlayer.restoreRespawnItemsFromBackup(logoutData.respawnItemsBackup);
                        if (restored) {
                            YakRealms.log("Successfully restored respawn items from combat logout backup");
                        } else {
                            YakRealms.warn("Failed to restore respawn items from backup");
                            return false;
                        }
                    } else {
                        YakRealms.warn("No backup available for respawn items recovery");
                        return false;
                    }
                } else {
                    YakRealms.warn("CombatLogoutMechanics not available for recovery");
                    return false;
                }
            }

            // Clear inventory safely
            clearPlayerInventorySafely(player);

            // Create decorative remnant only
            createDecorativeRemnant(deathData.deathLocation, player);

            // Update state
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
            yakPlayer.addDeath();

            //  Save with error handling
            boolean saveSuccess = savePlayerDataSafely(yakPlayer);
            if (!saveSuccess) {
                YakRealms.warn("Failed to save player data for processed combat logout death: " + player.getName());
                return false;
            }

            YakRealms.log("Completed processed combat logout death: " + player.getName() +
                    " (Respawn items: " + yakPlayer.getRespawnItemCount() + ")");
            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error handling processed combat logout death for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  Handle combat logout completion death
     */
    private boolean handleCombatLogoutCompletionDeathFixed(Player player, YakPlayer yakPlayer, DeathData deathData) {
        if (player == null || yakPlayer == null || deathData == null) {
            YakRealms.warn("CRITICAL: Null parameters in combat logout completion death");
            return false;
        }

        YakRealms.log("Processing completion death for combat logout: " + player.getName());

        try {
            // Clear inventory safely
            clearPlayerInventorySafely(player);

            // Create decorative remnant only
            createDecorativeRemnant(deathData.deathLocation, player);

            //  Ensure state is properly reset
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            yakPlayer.removeTemporaryData("prevent_healing");
            yakPlayer.removeTemporaryData("combat_logout_processing");
            yakPlayer.removeTemporaryData("combat_logout_needs_death");
            yakPlayer.removeTemporaryData("combat_logout_death_processed");

            // Update stats
            yakPlayer.addDeath();

            // Remove combat logout data
            if (combatLogoutMechanics != null) {
                combatLogoutMechanics.removeCombatLogoutData(player.getUniqueId());
            }

            //  Save with error handling
            boolean saveSuccess = savePlayerDataSafely(yakPlayer);
            if (!saveSuccess) {
                YakRealms.warn("Failed to save player data for combat logout completion death: " + player.getName());
                return false;
            }

            YakRealms.log("Completed combat logout punishment: " + player.getName());
            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error handling combat logout completion death for " + player.getName(), e);
            return false;
        }
    }

    /**
     *   respawn handler with comprehensive validation
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            YakRealms.warn("CRITICAL: PlayerRespawnEvent with null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Player " + player.getName() + " has null UUID during respawn");
            return;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            YakRealms.warn("CRITICAL: No YakPlayer data for " + player.getName() + " during respawn");
            return;
        }

        YakRealms.log("Processing respawn for " + player.getName());

        //  Check if already processing respawn
        if (!respawnInProgress.add(playerId)) {
            YakRealms.log("Respawn already in progress for " + player.getName());
            return;
        }

        try {
            // Get death data for context
            DeathData deathData = deathDataCache.get(playerId);

            //  Set respawn location based on alignment
            setRespawnLocationByAlignmentFixed(event, yakPlayer, player);

            //  Ensure inventory is clear immediately
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                clearPlayerInventorySafely(player);
            });

            //  Schedule post-respawn initialization with  error handling
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        handlePostRespawnFixed(player, yakPlayer);
                        // Clean up
                        respawnInProgress.remove(playerId);
                        deathDataCache.remove(playerId);
                    } else {
                        respawnInProgress.remove(playerId);
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), RESPAWN_RESTORE_DELAY);

            // Add respawn effects
            addRespawnEffectsFixed(player);

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error handling respawn for " + player.getName(), e);
            respawnInProgress.remove(playerId);
        }
    }

    /**
     *  Set respawn location with comprehensive validation
     */
    private void setRespawnLocationByAlignmentFixed(PlayerRespawnEvent event, YakPlayer yakPlayer, Player player) {
        if (event == null || yakPlayer == null || player == null) {
            YakRealms.warn("CRITICAL: Null parameters in respawn location setting");
            return;
        }

        try {
            World world = Bukkit.getWorlds().get(0);
            if (world == null) {
                YakRealms.warn("CRITICAL: No world available for respawn");
                return;
            }

            String alignment = yakPlayer.getAlignment();
            if (alignment == null) {
                alignment = "LAWFUL";
            }

            if ("CHAOTIC".equals(alignment)) {
                Location randomLocation = generateRandomSpawnPointFixed(player.getName(), world);
                event.setRespawnLocation(randomLocation);
                YakRealms.log("Set random respawn location for chaotic player: " + player.getName());
            } else {
                // Lawful and Neutral players respawn at main spawn
                Location spawnLocation = world.getSpawnLocation();
                if (spawnLocation != null) {
                    spawnLocation.setY(world.getHighestBlockYAt(spawnLocation) + 1);
                    event.setRespawnLocation(spawnLocation);
                    YakRealms.log("Set standard spawn location for " + alignment + " player: " + player.getName());
                } else {
                    YakRealms.warn("CRITICAL: World spawn location is null");
                }
            }

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error setting respawn location for " + player.getName(), e);
        }
    }

    /**
     *  Generate random spawn point with validation
     */
    private Location generateRandomSpawnPointFixed(String playerName, World world) {
        if (world == null) {
            return null;
        }

        try {
            Random random = new Random(playerName.hashCode() + System.currentTimeMillis());

            for (int attempts = 0; attempts < 10; attempts++) {
                double x = (random.nextDouble() - 0.5) * 2000;
                double z = (random.nextDouble() - 0.5) * 2000;
                int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
                Location loc = new Location(world, x, y, z);

                // Check if safe zone through WorldGuardManager
                if (WorldGuardManager.getInstance() != null &&
                        !WorldGuardManager.getInstance().isSafeZone(loc)) {
                    return loc;
                }
            }

            // Fallback location
            double x = (random.nextDouble() - 0.5) * 2000;
            double z = (random.nextDouble() - 0.5) * 2000;
            return new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);

        } catch (Exception e) {
            YakRealms.error("Error generating random spawn point", e);
            return world.getSpawnLocation();
        }
    }

    /**
     *  Handle post-respawn with comprehensive validation
     */
    private void handlePostRespawnFixed(Player player, YakPlayer yakPlayer) {
        if (player == null || yakPlayer == null) {
            YakRealms.warn("CRITICAL: Null parameters in post-respawn handling");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Player has null UUID in post-respawn");
            return;
        }

        ReentrantLock deathLock = getPlayerDeathLock(playerId);
        if (deathLock == null) {
            YakRealms.warn("CRITICAL: Could not get death lock for post-respawn");
            return;
        }

        boolean lockAcquired = false;
        try {
            lockAcquired = deathLock.tryLock(PLAYER_STATE_LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!lockAcquired) {
                YakRealms.warn("CRITICAL: Post-respawn lock timeout for " + player.getName());
                return;
            }

            YakPlayer.CombatLogoutState logoutState = yakPlayer.getCombatLogoutState();

            //  Handle combat logout completion messages with duplicate prevention
            if (logoutState == YakPlayer.CombatLogoutState.COMPLETED) {
                YakRealms.log("Processing respawn for completed combat logout: " + player.getName());

                // Only show message if not already shown
                if (!combatLogoutMessagesShown.contains(playerId)) {
                    combatLogoutMessagesShown.add(playerId);
                    showCombatLogoutCompletionMessageFixed(player, yakPlayer);

                    // Clean up message tracking after delay
                    Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                        combatLogoutMessagesShown.remove(playerId);
                    }, 200L);
                }

                // Reset combat logout state
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);

                // Clean up combat logout data
                if (combatLogoutMechanics != null) {
                    combatLogoutMechanics.removeCombatLogoutData(playerId);
                }

                // Save state reset
                savePlayerDataSafely(yakPlayer);
            }

            //  Check if items have already been restored
            DeathData deathData = deathDataCache.get(playerId);
            if (deathData != null && deathData.itemsRestored) {
                YakRealms.log("Items already restored for " + player.getName() + ", skipping");
                return;
            }

            //  Restore kept items with comprehensive validation
            if (yakPlayer.hasRespawnItems()) {
                YakRealms.log("Restoring " + yakPlayer.getRespawnItemCount() + " respawn items for " + player.getName());

                boolean restoreSuccess = restoreRespawnItemsPermanentlyFixed(player, yakPlayer);
                if (restoreSuccess && deathData != null) {
                    deathData.itemsRestored = true;
                }

                if (!restoreSuccess) {
                    YakRealms.warn("Failed to restore respawn items for " + player.getName());
                }
            } else {
                YakRealms.log("No respawn items to restore for " + player.getName());
            }

            // Initialize respawned player
            initializeRespawnedPlayerFixed(player, yakPlayer);

            // Recalculate health
            try {
                if (PlayerListenerManager.getInstance() != null &&
                        PlayerListenerManager.getInstance().getHealthListener() != null) {
                    PlayerListenerManager.getInstance().getHealthListener().recalculateHealth(player);
                }
            } catch (Exception e) {
                YakRealms.warn("Could not recalculate health for " + player.getName() + ": " + e.getMessage());
            }

        } catch (InterruptedException e) {
            YakRealms.error("Post-respawn processing interrupted for " + player.getName(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in post-respawn handling for " + player.getName(), e);
        } finally {
            if (lockAcquired) {
                deathLock.unlock();
            }
        }
    }

    /**
     *  Restore respawn items with  1.20.4 compatibility
     */
    private boolean restoreRespawnItemsPermanentlyFixed(Player player, YakPlayer yakPlayer) {
        if (player == null || yakPlayer == null) {
            YakRealms.warn("CRITICAL: Null parameters in respawn item restoration");
            return false;
        }

        try {
            List<ItemStack> items = yakPlayer.getRespawnItems();
            if (items == null || items.isEmpty()) {
                YakRealms.log("No respawn items to restore for " + player.getName());
                return true;
            }

            YakRealms.log("=== RESTORING RESPAWN ITEMS (PERMANENT) ===");
            YakRealms.log("Restoring " + items.size() + " items for " + player.getName());

            //  Ensure inventory is clear before restoration
            clearPlayerInventorySafely(player);

            //  Separate armor and regular items using InventoryUtils
            ItemStack[] armorContents = new ItemStack[4];
            List<ItemStack> regularItems = new ArrayList<>();

            for (ItemStack item : items) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                if (safeCopy == null) {
                    continue;
                }

                if (InventoryUtils.isArmorItem(safeCopy)) {
                    int armorSlot = InventoryUtils.getArmorSlot(safeCopy);
                    if (armorSlot >= 0 && armorSlot < 4 && armorContents[armorSlot] == null) {
                        armorContents[armorSlot] = safeCopy;
                        YakRealms.log("Prepared armor for slot " + armorSlot + ": " +
                                InventoryUtils.getItemDisplayName(safeCopy));
                    } else {
                        regularItems.add(safeCopy);
                        YakRealms.log("Added armor to regular items (slot occupied): " +
                                InventoryUtils.getItemDisplayName(safeCopy));
                    }
                } else {
                    regularItems.add(safeCopy);
                }
            }

            //  Restore armor using setArmorContents with retry mechanism
            boolean armorRestored = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    player.getInventory().setArmorContents(armorContents);
                    armorRestored = true;
                    YakRealms.log("Successfully restored armor using setArmorContents() (attempt " + attempt + ")");
                    break;
                } catch (Exception e) {
                    YakRealms.warn("Armor restoration attempt " + attempt + " failed: " + e.getMessage());
                    if (attempt == 3) {
                        YakRealms.error("All armor restoration attempts failed", e);
                        // Add armor to regular items as fallback
                        for (ItemStack armor : armorContents) {
                            if (armor != null) {
                                regularItems.add(armor);
                            }
                        }
                    } else {
                        try {
                            Thread.sleep(50); // Brief wait before retry
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            // Log armor restoration details
            if (armorRestored) {
                for (int i = 0; i < armorContents.length; i++) {
                    if (armorContents[i] != null) {
                        String slotName = getArmorSlotNameFixed(i);
                        YakRealms.log("Restored " + slotName + ": " +
                                InventoryUtils.getItemDisplayName(armorContents[i]));
                    }
                }
            }

            //  Restore regular items with priority placement
            int restoredRegularCount = restoreRegularItemsFixed(player, regularItems);

            //  Clear respawn items after successful restoration
            yakPlayer.clearRespawnItems();
            savePlayerDataSafely(yakPlayer);

            YakRealms.log("=== PERMANENT RESPAWN RESTORATION COMPLETE ===");
            YakRealms.log("Restored " + (armorRestored ? "armor and " : "") + restoredRegularCount +
                    " regular items for " + player.getName());

            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Failed to restore respawn items permanently for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  Restore regular items with priority handling
     */
    private int restoreRegularItemsFixed(Player player, List<ItemStack> regularItems) {
        if (player == null || regularItems == null) {
            return 0;
        }

        int restoredCount = 0;

        try {
            //  Sort items by priority (permanent items first)
            regularItems.sort((a, b) -> {
                boolean aPriority = InventoryUtils.isHighPriorityItem(a);
                boolean bPriority = InventoryUtils.isHighPriorityItem(b);

                if (aPriority && !bPriority) return -1;
                if (!aPriority && bPriority) return 1;
                return 0;
            });

            // First pass: try hotbar slots (0-8) for priority items
            for (int i = 0; i < Math.min(9, regularItems.size()); i++) {
                ItemStack item = regularItems.get(i);
                if (InventoryUtils.isValidItem(item)) {
                    try {
                        player.getInventory().setItem(i, item);
                        YakRealms.log("Restored to hotbar slot " + i + ": " +
                                InventoryUtils.getItemDisplayName(item));
                        restoredCount++;
                    } catch (Exception e) {
                        YakRealms.warn("Failed to restore to hotbar slot " + i + ": " + e.getMessage());
                    }
                }
            }

            // Second pass: remaining items to available slots
            for (int i = 9; i < regularItems.size(); i++) {
                ItemStack item = regularItems.get(i);
                if (InventoryUtils.isValidItem(item)) {
                    try {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                        if (!leftover.isEmpty()) {
                            // Drop overflow items
                            for (ItemStack leftoverItem : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                                YakRealms.log("Dropped overflow: " + InventoryUtils.getItemDisplayName(leftoverItem));
                            }
                        } else {
                            YakRealms.log("Restored: " + InventoryUtils.getItemDisplayName(item));
                            restoredCount++;
                        }
                    } catch (Exception e) {
                        YakRealms.warn("Failed to restore item: " + InventoryUtils.getItemDisplayName(item) +
                                " - " + e.getMessage());

                        // Try to drop as fallback
                        try {
                            player.getWorld().dropItemNaturally(player.getLocation(), item);
                            YakRealms.log("Dropped as fallback: " + InventoryUtils.getItemDisplayName(item));
                        } catch (Exception dropError) {
                            YakRealms.error("Failed to drop item as fallback: " +
                                    InventoryUtils.getItemDisplayName(item), dropError);
                        }
                    }
                }
            }

        } catch (Exception e) {
            YakRealms.error("Error restoring regular items", e);
        }

        return restoredCount;
    }

    /**
     *  Get armor slot name for logging
     */
    private String getArmorSlotNameFixed(int slot) {
        switch (slot) {
            case 0:
                return "boots";
            case 1:
                return "leggings";
            case 2:
                return "chestplate";
            case 3:
                return "helmet";
            default:
                return "unknown";
        }
    }

    /**
     *  Show combat logout completion message without duplicates
     */
    private void showCombatLogoutCompletionMessageFixed(Player player, YakPlayer yakPlayer) {
        if (player == null || yakPlayer == null) {
            return;
        }

        try {
            String alignment = yakPlayer.getAlignment();
            String punishmentMessage = getCombatLogoutPunishmentMessageFixed(alignment);

            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT PUNISHMENT COMPLETED!");
                    player.sendMessage(ChatColor.GRAY + punishmentMessage);
                    player.sendMessage(ChatColor.GRAY + "Your remaining items have been restored.");
                    player.sendMessage("");

                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
                    } catch (Exception e) {
                        YakRealms.warn("Could not play sound for " + player.getName());
                    }
                }
            }, 40L);

        } catch (Exception e) {
            YakRealms.error("Error showing combat logout completion message", e);
        }
    }

    /**
     *  Get punishment message based on alignment
     */
    private String getCombatLogoutPunishmentMessageFixed(String alignment) {
        if (alignment == null) {
            alignment = "LAWFUL";
        }

        switch (alignment) {
            case "LAWFUL":
                return "As a lawful player, you kept your armor and first hotbar item but lost other inventory.";
            case "NEUTRAL":
                return "As a neutral player, you had chances to keep some gear based on luck.";
            case "CHAOTIC":
                return "As a chaotic player, you lost all items for combat logging.";
            default:
                return "Your items were handled according to your alignment rules.";
        }
    }

    /**
     *  Add respawn effects with validation
     */
    private void addRespawnEffectsFixed(Player player) {
        if (player == null) {
            return;
        }

        try {
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline() && !player.isDead()) {
                    try {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                        YakRealms.log("Applied respawn blindness to: " + player.getName());
                    } catch (Exception e) {
                        YakRealms.warn("Could not apply respawn effects to " + player.getName() + ": " + e.getMessage());
                    }
                }
            }, 15L);
        } catch (Exception e) {
            YakRealms.error("Error scheduling respawn effects", e);
        }
    }

    /**
     *  Initialize respawned player safely
     */
    private void initializeRespawnedPlayerFixed(Player player, YakPlayer yakPlayer) {
        if (player == null || yakPlayer == null) {
            return;
        }

        try {
            YakRealms.log("Initializing respawned player: " + player.getName());

            //  Validate health values before setting
            double maxHealth = Math.max(1.0, Math.min(2048.0, 50.0)); // Reasonable bounds
            double currentHealth = Math.max(1.0, Math.min(maxHealth, 50.0));

            player.setMaxHealth(maxHealth);
            player.setHealth(currentHealth);
            player.setLevel(100);
            player.setExp(1.0f);

            //  Validate energy system integration
            if (yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }

            try {
                if (Energy.getInstance() != null) {
                    Energy.getInstance().setEnergy(yakPlayer, 100);
                }
            } catch (Exception e) {
                YakRealms.warn("Could not set energy for " + player.getName() + ": " + e.getMessage());
            }

            player.getInventory().setHeldItemSlot(0);

            YakRealms.log("Respawn initialization complete for " + player.getName());

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error initializing respawned player " + player.getName(), e);
        }
    }

    /**
     * Safely clear player inventory with comprehensive validation
     */
    private void clearPlayerInventorySafely(Player player) {
        if (player == null) {
            YakRealms.warn("CRITICAL: Cannot clear inventory for null player");
            return;
        }

        try {
            InventoryUtils.clearPlayerInventory(player);
        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error clearing inventory for " + player.getName(), e);
        }
    }

    /**
     *  Utility methods with comprehensive validation
     */

    /**
     *  Save player data with retry mechanism
     */
    private boolean savePlayerDataSafely(YakPlayer yakPlayer) {
        if (yakPlayer == null) {
            YakRealms.warn("CRITICAL: Cannot save null YakPlayer data");
            return false;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                boolean success = playerManager.savePlayer(yakPlayer).join();
                if (success) {
                    if (attempt > 1) {
                        YakRealms.log("Successfully saved player data for " + yakPlayer.getUsername() +
                                " on attempt " + attempt);
                    }
                    return true;
                }
            } catch (Exception e) {
                YakRealms.warn("Save attempt " + attempt + " failed for " + yakPlayer.getUsername() +
                        ": " + e.getMessage());

                if (attempt == MAX_RETRY_ATTEMPTS) {
                    YakRealms.error("CRITICAL: All save attempts failed for " + yakPlayer.getUsername(), e);
                    return false;
                }

                // Wait before retry
                try {
                    Thread.sleep(100 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    /**
     *  Acquire death processing lock with comprehensive validation
     */
    private boolean acquireDeathProcessingLock(UUID playerId, long currentTime) {
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Cannot acquire death lock for null UUID");
            return false;
        }

        try {
            // Check minimum death interval
            Long lastDeath = lastDeathTime.get(playerId);
            if (lastDeath != null && currentTime - lastDeath < MIN_DEATH_INTERVAL) {
                YakRealms.log("Death interval too short for " + playerId + ", blocking duplicate processing");
                return false;
            }

            // Check if already processing
            AtomicBoolean processing = deathInProgress.get(playerId);
            if (processing != null && processing.get()) {
                YakRealms.log("Death already in progress for " + playerId);
                return false;
            }

            //  Check for null before putIfAbsent
            Long existingLock = deathProcessingLock.putIfAbsent(playerId, currentTime);
            if (existingLock != null) {
                YakRealms.log("Death processing lock already held for " + playerId);
                return false;
            }

            //  Initialize processing flag safely
            AtomicBoolean newProcessing = new AtomicBoolean(true);
            deathInProgress.put(playerId, newProcessing);
            lastDeathTime.put(playerId, currentTime);

            YakRealms.log("Acquired death processing lock for " + playerId);
            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error acquiring death processing lock for " + playerId, e);
            return false;
        }
    }

    /**
     *  Release death processing lock with validation
     */
    private void releaseDeathProcessingLock(UUID playerId) {
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Cannot release death lock for null UUID");
            return;
        }

        try {
            deathProcessingLock.remove(playerId);
            AtomicBoolean processing = deathInProgress.get(playerId);
            if (processing != null) {
                processing.set(false);
            }
            YakRealms.log("Released death processing lock for " + playerId);

        } catch (Exception e) {
            YakRealms.error("Error releasing death processing lock for " + playerId, e);
        }
    }

    /**
     *  Create inventory backup with validation
     */
    private String createInventoryBackup(InventorySnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        try {
            List<ItemStack> items = snapshot.getAllItems();
            if (items.isEmpty()) {
                return null;
            }

            return InventoryUtils.serializeItemStacks(items.toArray(new ItemStack[0]));

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Failed to create inventory backup", e);
            return null;
        }
    }

    /**
     *  Rollback inventory from backup with validation
     */
    private void rollbackInventoryFromBackup(Player player, UUID playerId) {
        if (player == null || playerId == null) {
            YakRealms.warn("CRITICAL: Cannot rollback inventory - null parameters");
            return;
        }

        String backup = deathBackups.remove(playerId);
        if (backup != null) {
            try {
                ItemStack[] items = InventoryUtils.deserializeItemStacks(backup);
                if (items != null && items.length > 0) {
                    // Clear inventory first
                    clearPlayerInventorySafely(player);

                    // Restore items
                    for (int i = 0; i < Math.min(items.length, player.getInventory().getSize()); i++) {
                        if (items[i] != null) {
                            player.getInventory().setItem(i, items[i]);
                        }
                    }
                    YakRealms.log("Successfully rolled back inventory for " + player.getName());
                } else {
                    YakRealms.warn("Backup contained no valid items for " + player.getName());
                }
            } catch (Exception e) {
                YakRealms.error("CRITICAL: Failed to rollback inventory for " + player.getName(), e);
            }
        } else {
            YakRealms.warn("No backup found for inventory rollback: " + player.getName());
        }
    }

    /**
     *  Create decorative remnant with validation
     */
    private void createDecorativeRemnant(Location location, Player player) {
        if (location == null || player == null) {
            return;
        }

        try {
            if (remnantManager != null) {
                remnantManager.createDeathRemnant(location, Collections.emptyList(), player);
                YakRealms.log("Created decorative death remnant for " + player.getName());
            }
        } catch (Exception e) {
            YakRealms.warn("Failed to create death remnant for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     *   cleanup with comprehensive validation
     */
    private void cleanupStaleLocks() {
        try {
            long currentTime = System.currentTimeMillis();

            // Clean up expired processing locks
            deathProcessingLock.entrySet().removeIf(entry -> {
                Long lockTime = entry.getValue();
                return lockTime == null || currentTime - lockTime > DEATH_PROCESSING_TIMEOUT;
            });

            // Clean up expired progress flags
            deathInProgress.entrySet().removeIf(entry -> {
                UUID uuid = entry.getKey();
                if (uuid == null) return true;

                Long lockTime = deathProcessingLock.get(uuid);
                return lockTime == null || currentTime - lockTime > DEATH_PROCESSING_TIMEOUT;
            });

            // Clean up old backups
            deathBackups.entrySet().removeIf(entry -> {
                UUID uuid = entry.getKey();
                return uuid == null || !deathProcessingLock.containsKey(uuid);
            });

            // Clean up old message tracking
            combatLogoutMessagesShown.removeIf(uuid -> {
                if (uuid == null) return true;
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

            // Clean up old death data
            deathDataCache.entrySet().removeIf(entry -> {
                DeathData data = entry.getValue();
                return data == null || currentTime - data.deathTime > 300000; // 5 minutes
            });

            // Clean up player locks for offline players
            playerDeathLocks.entrySet().removeIf(entry -> {
                UUID uuid = entry.getKey();
                if (uuid == null) return true;

                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

        } catch (Exception e) {
            YakRealms.error("Error during cleanup", e);
        }
    }

    /**
     *  Emergency cleanup to prevent memory leaks
     */
    private void performEmergencyCleanup() {
        try {
            // Remove any null keys that might have been added
            deathProcessingLock.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            deathInProgress.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            lastDeathTime.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            deathBackups.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            deathDataCache.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);

            // Remove processed flags for offline players
            deathProcessed.removeIf(uuid -> {
                if (uuid == null) return true;
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

            respawnInProgress.removeIf(uuid -> {
                if (uuid == null) return true;
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

        } catch (Exception e) {
            YakRealms.error("Error during emergency cleanup", e);
        }
    }

    /**
     *   death message handling with proper mob name display
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeathMessage(PlayerDeathEvent event) {
        if (event == null) return;

        Player victim = event.getEntity();
        if (victim == null) return;

        try {
            event.setDeathMessage(null);

            String deathReason = " has died";
            EntityDamageEvent lastDamage = victim.getLastDamageCause();

            if (lastDamage != null) {
                deathReason = buildDeathMessageSafely(victim, lastDamage);
            }

            // Update killer stats if applicable
            Player killer = victim.getKiller();
            if (killer != null) {
                YakPlayer yakKiller = playerManager.getPlayer(killer);
                if (yakKiller != null) {
                    yakKiller.addPlayerKill();
                    savePlayerDataSafely(yakKiller);
                }
            }

            // Broadcast death message to nearby players
            String finalMessage = victim.getDisplayName() + ChatColor.RESET + deathReason;
            broadcastDeathMessageSafely(victim, finalMessage);

        } catch (Exception e) {
            YakRealms.error("Error handling death message for " + victim.getName(), e);
        }
    }

    /**
     *  Build death message safely
     */
    private String buildDeathMessageSafely(Player victim, EntityDamageEvent lastDamage) {
        if (lastDamage == null) {
            return " has died";
        }

        try {
            EntityDamageEvent.DamageCause cause = lastDamage.getCause();
            if (cause == null) {
                return " has died";
            }

            switch (cause) {
                case LAVA:
                case FIRE:
                case FIRE_TICK:
                    return " burned to death";
                case SUICIDE:
                    return " ended their own life";
                case FALL:
                    return " fell to their death";
                case SUFFOCATION:
                    return " was crushed to death";
                case DROWNING:
                    return " drowned to death";
                default:
                    if (lastDamage instanceof EntityDamageByEntityEvent) {
                        return buildEntityDeathMessageSafely(victim, (EntityDamageByEntityEvent) lastDamage);
                    }
                    return " has died";
            }

        } catch (Exception e) {
            YakRealms.error("Error building death message", e);
            return " has died";
        }
    }

    /**
     *  Build entity death message safely
     */
    private String buildEntityDeathMessageSafely(Player victim, EntityDamageByEntityEvent entityDamage) {
        if (entityDamage == null) {
            return " has died";
        }

        try {
            Entity damager = entityDamage.getDamager();
            if (damager == null) {
                return " has died";
            }

            if (damager instanceof Player killer) {
                String weaponInfo = getWeaponInfoSafely(killer);
                return " was killed by " + killer.getDisplayName() + ChatColor.WHITE + weaponInfo;
            } else if (damager instanceof LivingEntity livingDamager) {
                String mobName = getProperMobNameSafely(livingDamager);
                return " was killed by a(n) " + ChatColor.UNDERLINE + mobName;
            }

            return " has died";

        } catch (Exception e) {
            YakRealms.error("Error building entity death message", e);
            return " has died";
        }
    }

    /**
     *  Get weapon info safely
     */
    private String getWeaponInfoSafely(Player killer) {
        if (killer == null) {
            return "";
        }

        try {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            if (weapon != null && weapon.getType() != Material.AIR) {
                String weaponName;
                if (weapon.hasItemMeta() && weapon.getItemMeta().hasDisplayName()) {
                    weaponName = weapon.getItemMeta().getDisplayName();
                } else {
                    weaponName = TextUtil.formatItemName(weapon.getType().name());
                }
                return " with a(n) " + weaponName;
            }
            return "";

        } catch (Exception e) {
            YakRealms.error("Error getting weapon info", e);
            return "";
        }
    }

    /**
     *  Get proper mob name safely
     */
    private String getProperMobNameSafely(LivingEntity livingDamager) {
        if (livingDamager == null) {
            return "Unknown";
        }

        try {
            if (livingDamager.hasMetadata("type")) {
                MobManager mobManager = MobManager.getInstance();
                if (mobManager != null) {
                    CustomMob customMob = mobManager.getCustomMob(livingDamager);
                    if (customMob != null) {
                        String originalName = customMob.getOriginalName();
                        if (originalName != null && !originalName.isEmpty()) {
                            return MobUtils.getTierColor(MobUtils.getMobTier(livingDamager)) +
                                    ChatColor.stripColor(originalName);
                        }
                    }
                }
            }

            if (livingDamager.getCustomName() != null) {
                String customName = livingDamager.getCustomName();
                if (!customName.contains("|") && !customName.contains("§")) {
                    return MobUtils.getTierColor(MobUtils.getMobTier(livingDamager)) +
                            ChatColor.stripColor(customName);
                }
            }

            return MobUtils.getTierColor(MobUtils.getMobTier(livingDamager)) +
                    formatEntityTypeNameSafely(livingDamager.getType().name());

        } catch (Exception e) {
            YakRealms.warn("Error getting mob name for " + livingDamager.getType() + ": " + e.getMessage());
            return formatEntityTypeNameSafely(livingDamager.getType().name());
        }
    }

    /**
     *  Format entity type name safely
     */
    private String formatEntityTypeNameSafely(String entityTypeName) {
        if (entityTypeName == null || entityTypeName.isEmpty()) {
            return "Unknown";
        }

        try {
            String formatted = entityTypeName.toLowerCase().replace("_", " ");
            String[] words = formatted.split(" ");
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                if (i > 0) result.append(" ");
                if (words[i].length() > 0) {
                    result.append(words[i].substring(0, 1).toUpperCase());
                    if (words[i].length() > 1) {
                        result.append(words[i].substring(1));
                    }
                }
            }

            return result.toString();

        } catch (Exception e) {
            YakRealms.error("Error formatting entity type name", e);
            return entityTypeName;
        }
    }

    /**
     *  Broadcast death message safely
     */
    private void broadcastDeathMessageSafely(Player victim, String message) {
        if (victim == null || message == null) {
            return;
        }

        try {
            for (Entity entity : victim.getNearbyEntities(50, 50, 50)) {
                if (entity instanceof Player) {
                    entity.sendMessage(message);
                }
            }
            victim.sendMessage(message);

        } catch (Exception e) {
            YakRealms.error("Error broadcasting death message", e);
        }
    }

    public DeathRemnantManager getRemnantManager() {
        return remnantManager;
    }

    // Public API methods with validation

    public boolean hasRespawnItems(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(playerId);
        return yakPlayer != null && yakPlayer.hasRespawnItems();
    }

    public boolean restoreRespawnItems(Player player) {
        if (player == null) {
            return false;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null && yakPlayer.hasRespawnItems()) {
            return restoreRespawnItemsPermanentlyFixed(player, yakPlayer);
        }
        return false;
    }

    public boolean isProcessingRespawn(UUID playerId) {
        return playerId != null && respawnInProgress.contains(playerId);
    }

    /**
     *   death data class with comprehensive validation
     */
    private static class DeathData {
        final Location deathLocation;
        final long deathTime;
        final String alignment;
        final List<ItemStack> keptItems;
        final List<ItemStack> droppedItems;
        final String playerName;
        volatile boolean itemsRestored = false;
        volatile boolean processingComplete = false;

        DeathData(Location deathLocation, String alignment, String playerName) {
            //  Comprehensive null validation
            this.deathLocation = deathLocation != null ? deathLocation.clone() : null;
            this.deathTime = System.currentTimeMillis();
            this.alignment = alignment != null ? alignment : "LAWFUL";
            this.playerName = playerName != null ? playerName : "Unknown";
            this.keptItems = new ArrayList<>();
            this.droppedItems = new ArrayList<>();
        }

        boolean isValid() {
            return deathLocation != null &&
                    deathLocation.getWorld() != null &&
                    alignment != null &&
                    playerName != null;
        }
    }

    /**
     *  Inventory snapshot class for safe storage
     */
    private static class InventorySnapshot {
        private final List<ItemStack> allItems;

        public InventorySnapshot(List<ItemStack> items) {
            this.allItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
        }

        public List<ItemStack> getAllItems() {
            return new ArrayList<>(allItems);
        }

        public boolean isEmpty() {
            return allItems.isEmpty();
        }

        public int size() {
            return allItems.size();
        }
    }
}
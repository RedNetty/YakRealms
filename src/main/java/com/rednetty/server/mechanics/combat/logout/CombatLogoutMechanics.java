package com.rednetty.server.mechanics.combat.logout;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.utils.CombatLogoutBackup;
import com.rednetty.server.mechanics.player.utils.CombatLogoutMonitor;
import com.rednetty.server.mechanics.player.utils.CombatLogoutValidator;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.rednetty.server.utils.inventory.InventoryUtils;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FIXED CombatLogoutMechanics for Spigot 1.20.4 - CRITICAL PLAYER WIPE FIXES v2
 * - FIXED state management issues causing second combat logout to fail
 * - FIXED proper cleanup after successful processing
 * - FIXED respawn items validation and backup/restore system
 * - FIXED race conditions with  synchronization
 * - FIXED data cleanup to prevent stale state issues
 * - COMPREHENSIVE error recovery and rollback mechanisms
 * - FIXED logging methods to use YakRealms.log(), YakRealms.warn(), YakRealms.error() properly
 * -  state isolation to prevent cross-session contamination
 */
@Slf4j
public class CombatLogoutMechanics implements Listener {

    // Combat constants
    private static final long COMBAT_TAG_DURATION = 10000; // 10 seconds in milliseconds
    private static final long LOGOUT_PROCESSING_TIMEOUT = 30000; // 30 seconds
    private static final long LOGOUT_STATE_CLEANUP_INTERVAL = 300000; // 5 minutes (reduced from 10)
    private static final long DEATH_SCHEDULE_DELAY = 10L; // 0.5 seconds after join
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static CombatLogoutMechanics instance;
    // Combat tracking with  validation
    private final ConcurrentHashMap<String, Long> taggedPlayers = new ConcurrentHashMap<>();
    private final Map<String, Player> lastAttackers = new ConcurrentHashMap<>();
    //   combat logout processing with proper cleanup tracking
    private final Map<UUID, CombatLogoutData> combatLogouts = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> logoutProcessing = new ConcurrentHashMap<>();
    private final Map<UUID, String> logoutBackups = new ConcurrentHashMap<>();
    private final Set<UUID> processedDeaths = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingDeathSchedule = ConcurrentHashMap.newKeySet();
    //  Add completion tracking to prevent state contamination
    private final Set<UUID> completedSessions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastProcessingTime = new ConcurrentHashMap<>();
    //   lock management with timeout protection
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    // Performance tracking
    private final AtomicInteger totalCombatLogouts = new AtomicInteger(0);
    private final AtomicInteger successfulProcessing = new AtomicInteger(0);
    private final AtomicInteger failedProcessing = new AtomicInteger(0);
    // Dependencies
    private final YakPlayerManager playerManager;
    private final WorldGuardManager worldGuardManager;

    private CombatLogoutMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.worldGuardManager = WorldGuardManager.getInstance();
    }

    public static CombatLogoutMechanics getInstance() {
        if (instance == null) {
            instance = new CombatLogoutMechanics();
        }
        return instance;
    }

    /**
     * Initialize the fixed combat logout system
     */
    public void onEnable() {
        instance = this;
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Initialize utilities
        CombatLogoutBackup.initialize();
        CombatLogoutMonitor.initialize();

        // Start cleanup task with more aggressive cleanup
        startCleanupTask();

        YakRealms.log("FIXED CombatLogoutMechanics v2 enabled with comprehensive state management and wipe prevention");
    }

    /**
     *  cleanup task with better state management
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredData();
                    performEmergencyCleanup();
                    //  Add state validation cleanup
                    performStateValidationCleanup();
                } catch (Exception e) {
                    YakRealms.error("Error during combat logout cleanup", e);
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 600L, 600L); // Every 30 seconds (more frequent)
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        // Clean up tracking maps
        combatLogouts.clear();
        taggedPlayers.clear();
        lastAttackers.clear();
        logoutProcessing.clear();
        logoutBackups.clear();
        processedDeaths.clear();
        pendingDeathSchedule.clear();
        completedSessions.clear();
        lastProcessingTime.clear();
        playerLocks.clear();

        // Disable subsystems
        CombatLogoutBackup.shutdown();
        CombatLogoutMonitor.shutdown();

        YakRealms.log("FIXED CombatLogoutMechanics v2 disabled - all data cleared safely");
    }

    /**
     *  Get or create player lock with validation
     */
    private ReentrantLock getPlayerLock(UUID playerId) {
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Attempted to get player lock for null UUID");
            return new ReentrantLock(); // Return temporary lock to prevent crash
        }
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     * Check if a player is currently combat tagged
     */
    public boolean isPlayerTagged(Player player) {
        if (player == null || player.getName() == null) {
            return false;
        }

        String playerName = player.getName();
        Long tagTime = taggedPlayers.get(playerName);
        return tagTime != null && System.currentTimeMillis() - tagTime <= COMBAT_TAG_DURATION;
    }

    /**
     * Get time since player was last tagged
     */
    public long getTimeSinceLastTag(Player player) {
        if (player == null || player.getName() == null) {
            return Long.MAX_VALUE;
        }

        Long tagTime = taggedPlayers.get(player.getName());
        return tagTime != null ? System.currentTimeMillis() - tagTime : Long.MAX_VALUE;
    }

    /**
     * Mark a player as combat tagged
     */
    public void markCombatTagged(Player player) {
        if (player == null || player.getName() == null) {
            YakRealms.warn("Cannot mark null player as combat tagged");
            return;
        }

        taggedPlayers.put(player.getName(), System.currentTimeMillis());
    }

    /**
     * Clear a player's combat tag
     */
    public void clearCombatTag(Player player) {
        if (player == null || player.getName() == null) {
            return;
        }

        taggedPlayers.remove(player.getName());
        lastAttackers.remove(player.getName());
    }

    /**
     * Set the last attacker for a player
     */
    public void setLastAttacker(Player victim, Player attacker) {
        if (victim == null || victim.getName() == null) {
            return;
        }

        if (attacker != null) {
            lastAttackers.put(victim.getName(), attacker);
        }
    }

    /**
     *  Combat logout detection with comprehensive validation
     */
    public boolean isCombatLoggingOut(Player player) {
        if (player == null) {
            return false;
        }

        try {
            // Check if player is in safe zone (no combat logout in safe zones)
            if (WorldGuardManager.getInstance().isSafeZone(player.getLocation())) {
                return false;
            }

            // Check if player is currently combat tagged
            return isPlayerTagged(player) && getTimeSinceLastTag(player) < COMBAT_TAG_DURATION;

        } catch (Exception e) {
            YakRealms.error("Error checking combat logout status for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  Combat logout handling with comprehensive validation and proper cleanup
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatLogout(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            YakRealms.warn("CRITICAL: PlayerQuitEvent with null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Player " + player.getName() + " has null UUID");
            cleanupPlayerReferences(player);
            return;
        }

        //  Clear any previous completed session state immediately
        if (completedSessions.contains(playerId)) {
            YakRealms.log("Clearing previous completed session state for: " + player.getName());
            completedSessions.remove(playerId);
            // Force cleanup of any stale data
            forceCleanupPlayerData(playerId);
        }

        // Check if player is combat logging
        if (!isCombatLoggingOut(player)) {
            // Normal quit processing - clean up references
            cleanupPlayerReferences(player);
            return;
        }

        // COMBAT LOGOUT DETECTED
        YakRealms.log("COMBAT LOGOUT detected for " + player.getName());
        totalCombatLogouts.incrementAndGet();

        //  Acquire player lock with timeout
        ReentrantLock playerLock = getPlayerLock(playerId);
        if (playerLock == null) {
            YakRealms.warn("CRITICAL: Could not acquire player lock for " + player.getName());
            cleanupPlayerReferences(player);
            return;
        }

        boolean lockAcquired = false;
        try {
            lockAcquired = playerLock.tryLock(5, TimeUnit.SECONDS);
            if (!lockAcquired) {
                YakRealms.warn("CRITICAL: Combat logout lock timeout for " + player.getName());
                cleanupPlayerReferences(player);
                return;
            }

            //  Check if already processing with null protection AND check recent processing
            AtomicBoolean processing = logoutProcessing.computeIfAbsent(playerId, k -> new AtomicBoolean(false));
            if (processing == null || !processing.compareAndSet(false, true)) {
                YakRealms.log("Combat logout already processing for " + player.getName());
                return;
            }

            //  Check if we recently processed this player (prevent rapid re-processing)
            Long lastProcessed = lastProcessingTime.get(playerId);
            if (lastProcessed != null && System.currentTimeMillis() - lastProcessed < 10000) { // 10 seconds
                YakRealms.log("Combat logout too recent for " + player.getName() + ", skipping");
                processing.set(false);
                cleanupPlayerReferences(player);
                return;
            }

            // Record processing time
            lastProcessingTime.put(playerId, System.currentTimeMillis());

            //  Get player data with comprehensive validation
            YakPlayer yakPlayer = playerManager.getPlayerForCombatLogout(player);
            if (yakPlayer == null) {
                YakRealms.warn("CRITICAL: No YakPlayer found for combat logout: " + player.getName());
                processing.set(false);
                cleanupPlayerReferences(player);
                return;
            }

            //  Reset any previous combat logout state first
            if (yakPlayer.isInCombatLogoutState()) {
                YakRealms.log("Player has existing combat logout state, resetting: " + player.getName());
                yakPlayer.resetCombatLogoutState();
                yakPlayer.clearRespawnItems(); // Clear any old respawn items
            }

            //  Backup existing respawn items before processing
            String respawnItemsBackup = yakPlayer.backupRespawnItems();

            //  Get attacker with validation
            Player attacker = lastAttackers.get(player.getName());

            //  Validate player alignment
            String alignment = yakPlayer.getAlignment();
            if (alignment == null || alignment.trim().isEmpty()) {
                alignment = "LAWFUL";
                yakPlayer.setAlignment(alignment);
                YakRealms.log("Fixed null alignment for combat logout: " + player.getName());
            }

            //  Create combat logout data with comprehensive validation
            CombatLogoutData logoutData = createCombatLogoutDataSafely(attacker, player.getLocation(), alignment);
            if (logoutData == null) {
                YakRealms.warn("CRITICAL: Failed to create combat logout data for " + player.getName());
                processing.set(false);
                cleanupPlayerReferences(player);
                return;
            }

            // Store backup in logout data
            logoutData.respawnItemsBackup = respawnItemsBackup;

            //  Store combat logout data with null protection
            combatLogouts.put(playerId, logoutData);

            // Start monitoring
            CombatLogoutMonitor.recordCombatLogoutStart(player, alignment);

            // Set state to processing
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSING);

            // Create backup before processing
            CombatLogoutBackup.createBackup(player, yakPlayer);

            //  Process combat logout with comprehensive error handling
            boolean success = processCombatLogoutAtomicFixed(player, yakPlayer, logoutData);

            // Record completion
            CombatLogoutMonitor.recordCombatLogoutComplete(player, success,
                    success ? null : "atomic_processing_failed");

            if (success) {
                successfulProcessing.incrementAndGet();
                YakRealms.log("Successfully processed combat logout for " + player.getName());
            } else {
                failedProcessing.incrementAndGet();
                processing.set(false);
                rollbackCombatLogoutSafely(player, yakPlayer, logoutData);
                YakRealms.warn("Combat logout processing failed for " + player.getName());
            }

        } catch (InterruptedException e) {
            YakRealms.error("Combat logout processing interrupted for " + player.getName(), e);
            Thread.currentThread().interrupt();
            failedProcessing.incrementAndGet();
        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in combat logout processing for " + player.getName(), e);
            failedProcessing.incrementAndGet();
        } finally {
            if (lockAcquired) {
                playerLock.unlock();
            }

            // Clean up references
            cleanupPlayerReferences(player);
        }

        // Set  quit message
        event.setQuitMessage(ChatColor.RED + "[-] " + ChatColor.GRAY + player.getName() +
                ChatColor.DARK_GRAY + " (combat logout)");
    }

    /**
     *  Force cleanup player data to prevent stale state
     */
    private void forceCleanupPlayerData(UUID playerId) {
        if (playerId == null) return;

        try {
            // Remove all tracking data
            combatLogouts.remove(playerId);
            logoutProcessing.remove(playerId);
            logoutBackups.remove(playerId);
            processedDeaths.remove(playerId);
            pendingDeathSchedule.remove(playerId);
            lastProcessingTime.remove(playerId);

            YakRealms.log("Force cleaned up stale data for player: " + playerId);
        } catch (Exception e) {
            YakRealms.error("Error force cleaning player data", e);
        }
    }

    /**
     *  Create combat logout data safely
     */
    private CombatLogoutData createCombatLogoutDataSafely(Player attacker, Location logoutLocation, String alignment) {
        if (logoutLocation == null) {
            YakRealms.warn("CRITICAL: Cannot create combat logout data with null location");
            return null;
        }

        if (logoutLocation.getWorld() == null) {
            YakRealms.warn("CRITICAL: Cannot create combat logout data with null world");
            return null;
        }

        if (alignment == null) {
            alignment = "LAWFUL";
        }

        try {
            return new CombatLogoutData(attacker, logoutLocation, alignment);
        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error creating combat logout data", e);
            return null;
        }
    }

    /**
     *  Atomic combat logout processing with comprehensive validation
     */
    private boolean processCombatLogoutAtomicFixed(Player player, YakPlayer yakPlayer, CombatLogoutData logoutData) {
        if (player == null || yakPlayer == null || logoutData == null) {
            YakRealms.warn("CRITICAL: Null parameters in atomic combat logout processing");
            return false;
        }

        try {
            YakRealms.log("=== ATOMIC COMBAT LOGOUT PROCESSING v2 ===");
            YakRealms.log("Player: " + player.getName() + " (Alignment: " + yakPlayer.getAlignment() + ")");

            //  Validate processing can proceed
            if (!CombatLogoutValidator.validateCombatLogoutProcessing(player, yakPlayer)) {
                YakRealms.warn("Combat logout processing validation failed for: " + player.getName());
                return false;
            }

            String alignment = yakPlayer.getAlignment();
            if (alignment == null) {
                alignment = "LAWFUL";
                yakPlayer.setAlignment(alignment);
            }

            Location logoutLocation = player.getLocation();
            if (logoutLocation == null || logoutLocation.getWorld() == null) {
                YakRealms.warn("CRITICAL: Invalid logout location for " + player.getName());
                return false;
            }

            //  Get ALL items from player's inventory BEFORE any modifications
            List<ItemStack> allPlayerItems = InventoryUtils.getAllPlayerItems(player);
            if (allPlayerItems == null) {
                allPlayerItems = new ArrayList<>();
            }

            YakRealms.log("Total items to process: " + allPlayerItems.size());

            //  Process items based on alignment using  logic
            if (!processCombatLogoutItemsByAlignmentFixed(allPlayerItems, alignment, player,
                    logoutData.keptItems, logoutData.droppedItems)) {
                YakRealms.warn("Item processing failed for combat logout: " + player.getName());
                return false;
            }

            //  ATOMIC INVENTORY PROCESSING with comprehensive validation
            if (!processInventoryAtomicallyFixed(player, yakPlayer, logoutData.keptItems,
                    logoutData.droppedItems, logoutLocation)) {
                YakRealms.warn("Atomic inventory processing failed for: " + player.getName());
                return false;
            }

            // Mark items as processed
            logoutData.itemsProcessed = true;

            // Mark as processed and needs death
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSED);

            //  Save player data with timeout and retry mechanism
            boolean saveSuccess = savePlayerDataSafely(yakPlayer);
            if (!saveSuccess) {
                YakRealms.warn("Failed to save combat logout player data: " + player.getName());
                return false;
            }

            YakRealms.log("=== ATOMIC COMBAT LOGOUT PROCESSING v2 COMPLETE ===");
            YakRealms.log("Alignment: " + alignment + " | Kept: " + logoutData.keptItems.size() +
                    " | Dropped: " + logoutData.droppedItems.size());

            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error in atomic combat logout processing for " + player.getName(), e);
            return false;
        }
    }

    /**
     *  Process combat logout items by alignment with  validation
     */
    private boolean processCombatLogoutItemsByAlignmentFixed(List<ItemStack> allPlayerItems, String alignment,
                                                             Player player, List<ItemStack> keptItems,
                                                             List<ItemStack> droppedItems) {
        if (allPlayerItems == null || keptItems == null || droppedItems == null) {
            YakRealms.warn("CRITICAL: Null parameters in combat logout item processing");
            return false;
        }

        try {
            // Get first hotbar item safely
            ItemStack firstHotbarItem = getFirstHotbarItemSafely(allPlayerItems);

            switch (alignment) {
                case "LAWFUL":
                    return processLawfulCombatLogoutFixed(allPlayerItems, firstHotbarItem, keptItems, droppedItems, player);

                case "NEUTRAL":
                    return processNeutralCombatLogoutFixed(allPlayerItems, firstHotbarItem, keptItems, droppedItems);

                case "CHAOTIC":
                    return processChaoticCombatLogoutFixed(allPlayerItems, keptItems, droppedItems);

                default:
                    YakRealms.warn("Unknown alignment for combat logout: " + alignment + " - defaulting to lawful");
                    return processLawfulCombatLogoutFixed(allPlayerItems, firstHotbarItem, keptItems, droppedItems, player);
            }

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error processing combat logout items by alignment", e);
            return false;
        }
    }

    /**
     *  Process lawful combat logout - keep EQUIPPED armor and weapon, drop everything else
     */
    private boolean processLawfulCombatLogoutFixed(List<ItemStack> allPlayerItems, ItemStack firstHotbarItem,
                                                   List<ItemStack> keptItems, List<ItemStack> droppedItems,
                                                   Player player) {
        if (allPlayerItems == null || keptItems == null || droppedItems == null || player == null) {
            return false;
        }

        try {
            //  Get equipped armor items using InventoryUtils
            List<ItemStack> equippedArmor = InventoryUtils.getEquippedArmor(player);
            List<ItemStack> otherItems = new ArrayList<>();

            // Separate equipped armor from other items
            for (ItemStack item : allPlayerItems) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                boolean isEquippedArmor = false;
                for (ItemStack equipped : equippedArmor) {
                    if (equipped != null && equipped.isSimilar(item)) {
                        isEquippedArmor = true;
                        break;
                    }
                }

                if (!isEquippedArmor) {
                    otherItems.add(item);
                }
            }

            YakRealms.log("Processing lawful combat logout - Found " + equippedArmor.size() +
                    " equipped armor pieces and " + otherItems.size() + " other items");

            // Process equipped armor - always keep
            for (ItemStack armor : equippedArmor) {
                ItemStack safeCopy = InventoryUtils.createSafeCopy(armor);
                if (safeCopy != null) {
                    keptItems.add(safeCopy);
                    YakRealms.log("LAWFUL KEEPING EQUIPPED ARMOR: " + InventoryUtils.getItemDisplayName(safeCopy) +
                            " x" + safeCopy.getAmount());
                }
            }

            // Process other items
            for (ItemStack item : otherItems) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                boolean shouldKeep = false;
                ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                if (safeCopy == null) {
                    continue;
                }

                // Keep first hotbar item (weapon)
                if (firstHotbarItem != null && item.isSimilar(firstHotbarItem)) {
                    shouldKeep = true;
                    YakRealms.log("LAWFUL KEEPING WEAPON: " + InventoryUtils.getItemDisplayName(safeCopy));
                }
                // Keep permanent untradeable items
                else if (InventoryUtils.isPermanentUntradeable(safeCopy)) {
                    shouldKeep = true;
                    YakRealms.log("LAWFUL KEEPING UNTRADEABLE: " + InventoryUtils.getItemDisplayName(safeCopy));
                }

                if (shouldKeep) {
                    keptItems.add(safeCopy);
                } else {
                    droppedItems.add(safeCopy);
                    // Log unequipped armor being dropped
                    if (InventoryUtils.isArmorItem(safeCopy)) {
                        YakRealms.log("LAWFUL DROPPING UNEQUIPPED ARMOR: " +
                                InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                    } else {
                        YakRealms.log("LAWFUL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy) +
                                " x" + safeCopy.getAmount());
                    }
                }
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Error processing lawful combat logout", e);
            return false;
        }
    }

    /**
     *  Process neutral combat logout - 50% weapon drop, 25% armor drop chance
     */
    private boolean processNeutralCombatLogoutFixed(List<ItemStack> allPlayerItems, ItemStack firstHotbarItem,
                                                    List<ItemStack> keptItems, List<ItemStack> droppedItems) {
        if (allPlayerItems == null || keptItems == null || droppedItems == null) {
            return false;
        }

        try {
            Random random = new Random();
            boolean shouldDropWeapon = random.nextInt(2) == 0; // 50% chance
            boolean shouldDropArmor = random.nextInt(4) == 0; // 25% chance

            YakRealms.log("Neutral combat logout decisions - Drop weapon: " + shouldDropWeapon +
                    ", Drop armor: " + shouldDropArmor);

            for (ItemStack item : allPlayerItems) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                boolean shouldKeep = false;
                ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                if (safeCopy == null) {
                    continue;
                }

                // Always keep permanent untradeable items
                if (InventoryUtils.isPermanentUntradeable(safeCopy)) {
                    shouldKeep = true;
                }
                // Handle armor (both equipped and unequipped)
                else if (InventoryUtils.isArmorItem(safeCopy)) {
                    shouldKeep = !shouldDropArmor;
                }
                // Handle weapon (first hotbar item)
                else if (firstHotbarItem != null && item.isSimilar(firstHotbarItem)) {
                    shouldKeep = !shouldDropWeapon;
                }
                // Drop all other items
                else {
                    shouldKeep = false;
                }

                if (shouldKeep) {
                    keptItems.add(safeCopy);
                    YakRealms.log("NEUTRAL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                } else {
                    droppedItems.add(safeCopy);
                    YakRealms.log("NEUTRAL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                }
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Error processing neutral combat logout", e);
            return false;
        }
    }

    /**
     *  Process chaotic combat logout - drop everything except permanent items
     */
    private boolean processChaoticCombatLogoutFixed(List<ItemStack> allPlayerItems,
                                                    List<ItemStack> keptItems, List<ItemStack> droppedItems) {
        if (allPlayerItems == null || keptItems == null || droppedItems == null) {
            return false;
        }

        try {
            for (ItemStack item : allPlayerItems) {
                if (!InventoryUtils.isValidItem(item)) {
                    continue;
                }

                ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
                if (safeCopy == null) {
                    continue;
                }

                // Only keep permanent untradeable items and quest items
                if (InventoryUtils.isPermanentUntradeable(safeCopy) || InventoryUtils.isQuestItem(safeCopy)) {
                    keptItems.add(safeCopy);
                    YakRealms.log("CHAOTIC KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                } else {
                    droppedItems.add(safeCopy);
                    YakRealms.log("CHAOTIC DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy) + " x" + safeCopy.getAmount());
                }
            }

            return true;

        } catch (Exception e) {
            YakRealms.error("Error processing chaotic combat logout", e);
            return false;
        }
    }

    /**
     *  Get first hotbar item safely
     */
    private ItemStack getFirstHotbarItemSafely(List<ItemStack> allItems) {
        if (allItems == null || allItems.isEmpty()) {
            return null;
        }

        try {
            // First 9 items should be hotbar items from InventoryUtils
            for (int i = 0; i < Math.min(9, allItems.size()); i++) {
                ItemStack item = allItems.get(i);
                if (InventoryUtils.isValidItem(item)) {
                    return InventoryUtils.createSafeCopy(item);
                }
            }
            return null;

        } catch (Exception e) {
            YakRealms.error("Error getting first hotbar item safely", e);
            return null;
        }
    }

    /**
     *  Process inventory atomically with comprehensive validation
     */
    private boolean processInventoryAtomicallyFixed(Player player, YakPlayer yakPlayer,
                                                    List<ItemStack> keptItems, List<ItemStack> droppedItems,
                                                    Location logoutLocation) {
        if (player == null || yakPlayer == null || keptItems == null ||
                droppedItems == null || logoutLocation == null) {
            YakRealms.warn("CRITICAL: Null parameters in atomic inventory processing");
            return false;
        }

        try {
            YakRealms.log("Starting atomic inventory processing for: " + player.getName());

            //  Step 1: Clear live inventory first to prevent duplication
            InventoryUtils.clearPlayerInventory(player);

            //  Step 2: Drop items at logout location with validation
            for (ItemStack droppedItem : droppedItems) {
                if (!InventoryUtils.isValidItem(droppedItem)) {
                    continue;
                }

                boolean dropped = false;
                for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                    try {
                        logoutLocation.getWorld().dropItemNaturally(logoutLocation, droppedItem);
                        dropped = true;
                        YakRealms.log("Dropped: " + InventoryUtils.getItemDisplayName(droppedItem) +
                                " x" + droppedItem.getAmount());
                        break;
                    } catch (Exception e) {
                        YakRealms.warn("Drop attempt " + attempt + " failed for " +
                                InventoryUtils.getItemDisplayName(droppedItem) + ": " + e.getMessage());
                        if (attempt == MAX_RETRY_ATTEMPTS) {
                            throw new RuntimeException("Failed to drop item after " + MAX_RETRY_ATTEMPTS + " attempts");
                        }
                    }
                }

                if (!dropped) {
                    throw new RuntimeException("Failed to drop item: " + InventoryUtils.getItemDisplayName(droppedItem));
                }
            }

            //  Step 3: Store kept items permanently in database with validation
            if (!keptItems.isEmpty()) {
                boolean storeSuccess = storeKeptItemsSafely(yakPlayer, keptItems);
                if (!storeSuccess) {
                    throw new RuntimeException("Failed to store kept items in database");
                }
                YakRealms.log("Stored " + keptItems.size() + " kept items for respawn");
            } else {
                yakPlayer.clearRespawnItems();
                YakRealms.log("No items to keep - cleared respawn items");
            }

            //  Step 4: Validate serialization worked
            if (!CombatLogoutValidator.validateInventorySerialization(yakPlayer)) {
                throw new RuntimeException("Inventory serialization validation failed");
            }

            YakRealms.log("Atomic inventory processing completed successfully");
            return true;

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Atomic inventory processing failed for " + player.getName() +
                    " - attempting emergency restoration", e);

            // Emergency restoration - try to reload original inventory
            try {
                yakPlayer.applyInventory(player);
                YakRealms.log("Emergency restoration completed for: " + player.getName());
            } catch (Exception restoreError) {
                YakRealms.error("CRITICAL: Emergency restoration also failed for " + player.getName(), restoreError);
            }

            return false;
        }
    }

    /**
     *  Store kept items safely with retry mechanism
     */
    private boolean storeKeptItemsSafely(YakPlayer yakPlayer, List<ItemStack> keptItems) {
        if (yakPlayer == null || keptItems == null) {
            return false;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                boolean success = yakPlayer.setRespawnItems(keptItems);
                if (success) {
                    if (attempt > 1) {
                        YakRealms.log("Successfully stored kept items on attempt " + attempt +
                                " for " + yakPlayer.getUsername());
                    }
                    return true;
                }
            } catch (Exception e) {
                YakRealms.warn("Store attempt " + attempt + " failed for " + yakPlayer.getUsername() +
                        ": " + e.getMessage());

                if (attempt == MAX_RETRY_ATTEMPTS) {
                    YakRealms.error("CRITICAL: All store attempts failed for " + yakPlayer.getUsername(), e);
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
     *  Save player data safely with retry mechanism
     */
    private boolean savePlayerDataSafely(YakPlayer yakPlayer) {
        if (yakPlayer == null) {
            return false;
        }

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                CompletableFuture<Boolean> saveFuture = playerManager.savePlayer(yakPlayer);
                Boolean saveResult = saveFuture.get(10, TimeUnit.SECONDS);

                if (saveResult != null && saveResult) {
                    if (attempt > 1) {
                        YakRealms.log("Successfully saved player data on attempt " + attempt +
                                " for " + yakPlayer.getUsername());
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
     *   rollback combat logout processing with validation
     */
    private void rollbackCombatLogoutSafely(Player player, YakPlayer yakPlayer, CombatLogoutData logoutData) {
        if (player == null || yakPlayer == null) {
            YakRealms.warn("CRITICAL: Cannot rollback combat logout - null parameters");
            return;
        }

        try {
            // Restore respawn items from backup if available
            if (logoutData != null && logoutData.respawnItemsBackup != null) {
                boolean restored = yakPlayer.restoreRespawnItemsFromBackup(logoutData.respawnItemsBackup);
                if (restored) {
                    YakRealms.log("Restored respawn items from backup during rollback for: " + player.getName());
                } else {
                    YakRealms.warn("Failed to restore respawn items from backup for: " + player.getName());
                }
            }

            // Use validator for rollback if available
            if (CombatLogoutValidator.rollbackCombatLogout(player, yakPlayer)) {
                YakRealms.log("Successfully rolled back combat logout for: " + player.getName());
            } else {
                YakRealms.warn("Combat logout rollback validation failed for: " + player.getName());
            }

        } catch (Exception e) {
            YakRealms.error("CRITICAL: Failed to rollback combat logout for " + player.getName(), e);
        }
    }

    /**
     *  Handle player rejoin after combat logout with comprehensive validation and proper cleanup
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRejoinAfterCombatLogout(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            YakRealms.warn("CRITICAL: PlayerJoinEvent with null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("CRITICAL: Player " + player.getName() + " has null UUID on join");
            return;
        }

        // Check if this player has pending combat logout
        CombatLogoutData logoutData = combatLogouts.get(playerId);
        if (logoutData == null) {
            return;
        }

        ReentrantLock playerLock = getPlayerLock(playerId);
        if (playerLock == null) {
            YakRealms.warn("CRITICAL: Could not get player lock for rejoin: " + player.getName());
            return;
        }

        boolean lockAcquired = false;
        try {
            lockAcquired = playerLock.tryLock(5, TimeUnit.SECONDS);
            if (!lockAcquired) {
                YakRealms.warn("CRITICAL: Rejoin lock timeout for " + player.getName());
                return;
            }

            // Get player data
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                YakRealms.warn("No YakPlayer found for rejoining combat logger: " + player.getName());
                return;
            }

            YakPlayer.CombatLogoutState state = yakPlayer.getCombatLogoutState();

            //  Validate respawn items before scheduling death
            if (state == YakPlayer.CombatLogoutState.PROCESSED ||
                    state == YakPlayer.CombatLogoutState.PROCESSING) {

                // Validate respawn items exist
                if (!yakPlayer.hasRespawnItems()) {
                    YakRealms.warn("No respawn items found for combat logout player: " + player.getName() +
                            ". Checking for backup...");

                    // Try to restore from backup
                    if (logoutData.respawnItemsBackup != null) {
                        boolean restored = yakPlayer.restoreRespawnItemsFromBackup(logoutData.respawnItemsBackup);
                        if (restored) {
                            YakRealms.log("Restored respawn items from backup for: " + player.getName());
                        } else {
                            YakRealms.warn("Failed to restore respawn items from backup for: " + player.getName());
                        }
                    }
                }
            }

            // Check if death needs to be scheduled
            if ((state == YakPlayer.CombatLogoutState.PROCESSED ||
                    state == YakPlayer.CombatLogoutState.PROCESSING) &&
                    !logoutData.deathScheduled &&
                    !hasProcessedDeath(playerId)) {

                YakRealms.log("Scheduling death for combat logout player: " + player.getName() +
                        " (Has respawn items: " + yakPlayer.hasRespawnItems() + ")");
                logoutData.deathScheduled = true;
                pendingDeathSchedule.add(playerId);

                //  First teleport player to logout location to ensure proper death location
                scheduleLocationTeleport(player, logoutData);

                // Schedule death after teleport
                schedulePlayerDeathSafely(player, yakPlayer, logoutData);
            }

            // Show message only once
            if (!logoutData.messageShown) {
                logoutData.messageShown = true;
                showCombatLogoutMessage(player);
            }

        } catch (InterruptedException e) {
            YakRealms.error("Player rejoin processing interrupted for " + player.getName(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            YakRealms.error("CRITICAL: Error handling player rejoin for " + player.getName(), e);
        } finally {
            if (lockAcquired) {
                playerLock.unlock();
            }
        }
    }

    /**
     *  Schedule location teleport safely
     */
    private void scheduleLocationTeleport(Player player, CombatLogoutData logoutData) {
        if (player == null || logoutData == null) {
            return;
        }

        try {
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                if (player.isOnline() && !player.isDead()) {
                    // Teleport to exact logout location
                    World world = Bukkit.getWorld(logoutData.world);
                    if (world != null) {
                        Location deathLocation = new Location(world, logoutData.x, logoutData.y, logoutData.z,
                                logoutData.yaw, logoutData.pitch);
                        player.teleport(deathLocation);
                        YakRealms.log("Teleported player to logout location for death: " + player.getName());
                    }
                }
            });
        } catch (Exception e) {
            YakRealms.error("Error scheduling location teleport for " + player.getName(), e);
        }
    }

    /**
     *  Schedule player death safely with proper cleanup
     */
    private void schedulePlayerDeathSafely(Player player, YakPlayer yakPlayer, CombatLogoutData logoutData) {
        if (player == null || yakPlayer == null || logoutData == null) {
            YakRealms.warn("CRITICAL: Cannot schedule player death - null parameters");
            return;
        }

        try {
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (!player.isOnline() || player.isDead()) {
                    pendingDeathSchedule.remove(player.getUniqueId());
                    return;
                }

                try {
                    // Double-check they haven't already been processed
                    if (hasProcessedDeath(player.getUniqueId())) {
                        YakRealms.log("Player already processed, skipping death: " + player.getName());
                        return;
                    }

                    //  Final validation before death
                    if (!yakPlayer.hasRespawnItems() && !logoutData.keptItems.isEmpty()) {
                        YakRealms.warn("CRITICAL: Respawn items missing before death for: " + player.getName());
                        // Try one more time to set them
                        storeKeptItemsSafely(yakPlayer, logoutData.keptItems);
                    }

                    // Ensure player is at logout location before death
                    World world = Bukkit.getWorld(logoutData.world);
                    if (world != null) {
                        Location deathLocation = new Location(world, logoutData.x, logoutData.y, logoutData.z,
                                logoutData.yaw, logoutData.pitch);
                        if (player.getLocation().distance(deathLocation) > 5.0) {
                            player.teleport(deathLocation);
                        }
                    }

                    // Kill the player
                    player.setHealth(0);
                    YakRealms.log("Executed combat logout death for: " + player.getName());

                    // Update state to completed and mark for cleanup
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
                    completedSessions.add(player.getUniqueId());

                    //  Schedule proper cleanup after death is processed
                    Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                        cleanupCompletedCombatLogout(player.getUniqueId());
                    }, 100L); // 5 seconds later

                } catch (Exception e) {
                    YakRealms.error("CRITICAL: Error processing scheduled combat logout death for " + player.getName(), e);
                } finally {
                    pendingDeathSchedule.remove(player.getUniqueId());
                }
            }, DEATH_SCHEDULE_DELAY);

        } catch (Exception e) {
            YakRealms.error("Error scheduling player death for " + player.getName(), e);
        }
    }

    /**
     *  Clean up completed combat logout session
     */
    private void cleanupCompletedCombatLogout(UUID playerId) {
        if (playerId == null) return;

        try {
            combatLogouts.remove(playerId);
            logoutProcessing.remove(playerId);
            logoutBackups.remove(playerId);
            processedDeaths.remove(playerId);
            pendingDeathSchedule.remove(playerId);

            YakRealms.log("Cleaned up completed combat logout session for: " + playerId);
        } catch (Exception e) {
            YakRealms.error("Error cleaning up completed combat logout", e);
        }
    }

    /**
     *  Show combat logout message safely
     */
    private void showCombatLogoutMessage(Player player) {
        if (player == null) {
            return;
        }

        try {
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT DETECTED!");
                    player.sendMessage(ChatColor.GRAY + "You will be punished according to your alignment.");
                    player.sendMessage(ChatColor.GRAY + "Preparing to process your death...");
                    player.sendMessage("");
                }
            }, 20L);
        } catch (Exception e) {
            YakRealms.error("Error showing combat logout message", e);
        }
    }

    /**
     * Clean up player references
     */
    private void cleanupPlayerReferences(Player player) {
        if (player == null || player.getName() == null) {
            return;
        }

        try {
            taggedPlayers.remove(player.getName());
            lastAttackers.remove(player.getName());
        } catch (Exception e) {
            YakRealms.error("Error cleaning up player references", e);
        }
    }

    /**
     *   cleanup with comprehensive validation and better state management
     */
    private void cleanupExpiredData() {
        try {
            long currentTime = System.currentTimeMillis();
            int initialSize = combatLogouts.size();

            // Clean up old combat logout entries (but not too aggressively)
            combatLogouts.entrySet().removeIf(entry -> {
                CombatLogoutData data = entry.getValue();
                UUID playerId = entry.getKey();

                if (data == null) return true;

                // Don't clean up if player is online and in an active state
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    YakPlayer yakPlayer = playerManager.getPlayer(player);
                    if (yakPlayer != null && yakPlayer.isInCombatLogoutState()) {
                        return false; // Keep active sessions
                    }
                }

                // Clean up old completed sessions
                boolean isExpired = currentTime - data.timestamp > LOGOUT_STATE_CLEANUP_INTERVAL;
                if (isExpired && completedSessions.contains(playerId)) {
                    completedSessions.remove(playerId);
                    return true;
                }

                return isExpired;
            });

            // Clean up expired combat tags
            taggedPlayers.entrySet().removeIf(entry -> {
                Long tagTime = entry.getValue();
                return tagTime == null || currentTime - tagTime > COMBAT_TAG_DURATION * 2;
            });

            // Clean up stale processing flags (more conservative)
            logoutProcessing.entrySet().removeIf(entry -> {
                UUID playerId = entry.getKey();
                AtomicBoolean processing = entry.getValue();

                if (playerId == null || processing == null) {
                    return true;
                }

                // Only clean up if no active combat logout data
                if (!combatLogouts.containsKey(playerId)) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        return true;
                    }

                    YakPlayer yakPlayer = playerManager.getPlayer(playerId);
                    return yakPlayer == null || !yakPlayer.isInCombatLogoutState();
                }

                return false;
            });

            // Clean up old backups
            logoutBackups.entrySet().removeIf(entry -> {
                UUID playerId = entry.getKey();
                return playerId == null || !combatLogouts.containsKey(playerId);
            });

            // Clean up processed deaths older than 10 minutes
            processedDeaths.removeIf(playerId -> {
                if (playerId == null) return true;

                CombatLogoutData data = combatLogouts.get(playerId);
                return data == null || (currentTime - data.timestamp > 600000);
            });

            // Clean up pending death schedules for offline players
            pendingDeathSchedule.removeIf(playerId -> {
                if (playerId == null) return true;

                Player player = Bukkit.getPlayer(playerId);
                return player == null || !player.isOnline();
            });

            // Clean up player locks for offline players (more conservative)
            playerLocks.entrySet().removeIf(entry -> {
                UUID playerId = entry.getKey();
                if (playerId == null) return true;

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    // Also check if they have any active combat logout data
                    return !combatLogouts.containsKey(playerId);
                }
                return false;
            });

            // Clean up old processing time records
            lastProcessingTime.entrySet().removeIf(entry -> {
                Long processTime = entry.getValue();
                return processTime == null || currentTime - processTime > 600000; // 10 minutes
            });

            int removed = initialSize - combatLogouts.size();
            if (removed > 0) {
                YakRealms.log("Cleaned up " + removed + " old combat logout entries");
            }

        } catch (Exception e) {
            YakRealms.error("Error during cleanup", e);
        }
    }

    /**
     *  Emergency cleanup to prevent memory leaks and null pointer issues
     */
    private void performEmergencyCleanup() {
        try {
            // Remove any null keys or values that might have been added
            combatLogouts.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            logoutProcessing.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            logoutBackups.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            taggedPlayers.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            lastAttackers.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
            lastProcessingTime.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);

            // Remove processed flags and pending schedules for null or offline players
            processedDeaths.removeIf(uuid -> {
                if (uuid == null) return true;
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

            pendingDeathSchedule.removeIf(uuid -> {
                if (uuid == null) return true;
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

            // Clean up completed sessions for offline players
            completedSessions.removeIf(uuid -> {
                if (uuid == null) return true;
                Player player = Bukkit.getPlayer(uuid);
                return player == null || !player.isOnline();
            });

        } catch (Exception e) {
            YakRealms.error("Error during emergency cleanup", e);
        }
    }

    /**
     *  Add state validation cleanup to prevent stale states
     */
    private void performStateValidationCleanup() {
        try {
            // Validate that online players with combat logout states have corresponding data
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                if (playerId == null) continue;

                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer == null) continue;

                // If player has combat logout state but no data, clean it up
                if (yakPlayer.isInCombatLogoutState() && !combatLogouts.containsKey(playerId)) {
                    YakPlayer.CombatLogoutState state = yakPlayer.getCombatLogoutState();

                    // Only clean up COMPLETED states, others might be valid
                    if (state == YakPlayer.CombatLogoutState.COMPLETED) {
                        YakRealms.log("Cleaning up orphaned COMPLETED combat logout state for: " + player.getName());
                        yakPlayer.resetCombatLogoutState();
                        completedSessions.remove(playerId);
                    }
                }
            }

        } catch (Exception e) {
            YakRealms.error("Error during state validation cleanup", e);
        }
    }

    // Public API methods

    /**
     * Get current combat tag time remaining for player
     */
    public int getCombatTimeRemaining(Player player) {
        if (!isPlayerTagged(player)) {
            return 0;
        }

        long timeSinceTag = getTimeSinceLastTag(player);
        long remainingTime = COMBAT_TAG_DURATION - timeSinceTag;
        return (int) Math.max(0, remainingTime / 1000);
    }

    /**
     * Force cancel combat logout for a player (admin command)
     */
    public boolean forceCancelCombatLogout(Player player, String reason) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            return false;
        }

        ReentrantLock playerLock = getPlayerLock(playerId);
        boolean lockAcquired = false;

        try {
            lockAcquired = playerLock.tryLock(5, TimeUnit.SECONDS);
            if (!lockAcquired) {
                return false;
            }

            // Clear combat tags
            clearCombatTag(player);

            // Remove from combat logout tracking
            combatLogouts.remove(playerId);

            // Reset logout processing
            AtomicBoolean processing = logoutProcessing.get(playerId);
            if (processing != null) {
                processing.set(false);
            }

            // Reset player state if online
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                savePlayerDataSafely(yakPlayer);
            }

            // Remove from processed deaths and completed sessions
            processedDeaths.remove(playerId);
            pendingDeathSchedule.remove(playerId);
            completedSessions.remove(playerId);
            lastProcessingTime.remove(playerId);

            player.sendMessage(ChatColor.GREEN + "Combat logout cancelled: " + reason);
            YakRealms.log("Force cancelled combat logout for " + player.getName() + ": " + reason);

            return true;

        } catch (Exception e) {
            YakRealms.error("Failed to force cancel combat logout for " + player.getName(), e);
            return false;
        } finally {
            if (lockAcquired) {
                playerLock.unlock();
            }
        }
    }

    /**
     * Check if player death has already been processed
     */
    public boolean hasProcessedDeath(UUID playerId) {
        return playerId != null && processedDeaths.contains(playerId);
    }

    /**
     * Mark player death as processed
     */
    public void markDeathProcessed(UUID playerId) {
        if (playerId != null) {
            processedDeaths.add(playerId);
        }
    }

    /**
     * Get combat logout statistics
     */
    public CombatLogoutStats getStats() {
        return new CombatLogoutStats(
                totalCombatLogouts.get(),
                successfulProcessing.get(),
                failedProcessing.get(),
                combatLogouts.size(),
                taggedPlayers.size(),
                completedSessions.size()
        );
    }

    /**
     * Check if player has an active combat logout being processed
     */
    public boolean hasActiveCombatLogout(UUID playerId) {
        if (playerId == null) {
            return false;
        }

        return combatLogouts.containsKey(playerId) ||
                (logoutProcessing.containsKey(playerId) &&
                        logoutProcessing.get(playerId) != null &&
                        logoutProcessing.get(playerId).get());
    }

    /**
     * Get combat logout data for a player
     */
    public CombatLogoutData getCombatLogoutData(UUID playerId) {
        return playerId != null ? combatLogouts.get(playerId) : null;
    }

    /**
     * Remove combat logout data after processing
     */
    public void removeCombatLogoutData(UUID playerId) {
        if (playerId != null) {
            combatLogouts.remove(playerId);
            pendingDeathSchedule.remove(playerId);
            completedSessions.remove(playerId);
        }
    }

    /**
     * Check if player has pending death schedule
     */
    public boolean hasPendingDeathSchedule(UUID playerId) {
        return playerId != null && pendingDeathSchedule.contains(playerId);
    }

    /**
     * Log performance statistics
     */
    public void logPerformanceStats() {
        CombatLogoutStats stats = getStats();
        YakRealms.log("=== FIXED CombatLogoutMechanics v2 Performance Stats ===");
        YakRealms.log("Total Combat Logouts: " + stats.totalLogouts);
        YakRealms.log("Successful Processing: " + stats.successfulProcessing);
        YakRealms.log("Failed Processing: " + stats.failedProcessing);
        YakRealms.log("Success Rate: " + String.format("%.1f%%", stats.getSuccessRate()));
        YakRealms.log("Active Combat Logouts: " + stats.activeCombatLogouts);
        YakRealms.log("Currently Tagged Players: " + stats.currentlyTagged);
        YakRealms.log("Completed Sessions: " + stats.completedSessions);
        YakRealms.log("Pending Death Schedules: " + pendingDeathSchedule.size());
        YakRealms.log("Player Wipe Prevention: ACTIVE v2");
        YakRealms.log("State Management: ");
        YakRealms.log("Cleanup System: COMPREHENSIVE");
        YakRealms.log("===================================================");
    }

    /**
     * Check if a player should be prevented from certain actions due to combat
     */
    public boolean shouldPreventAction(Player player, String action) {
        if (!isPlayerTagged(player)) {
            return false;
        }

        if (action == null) {
            return false;
        }

        switch (action.toLowerCase()) {
            case "teleport":
            case "warp":
            case "home":
            case "spawn":
                return true;
            case "logout":
                return false; // Allow logout, but will be punished
            default:
                return false;
        }
    }

    //  public API for integration

    /**
     * Send combat tag notification to player
     */
    public void sendCombatNotification(Player player, String message) {
        if (player == null || message == null) {
            return;
        }

        try {
            if (isPlayerTagged(player)) {
                int timeLeft = getCombatTimeRemaining(player);
                player.sendMessage(ChatColor.RED + message);
                player.sendMessage(ChatColor.GRAY + "Combat ends in: " + ChatColor.BOLD + timeLeft + "s");
            }
        } catch (Exception e) {
            YakRealms.error("Error sending combat notification", e);
        }
    }

    /**
     * : Combat logout statistics class
     */
    public static class CombatLogoutStats {
        public final int totalLogouts;
        public final int successfulProcessing;
        public final int failedProcessing;
        public final int activeCombatLogouts;
        public final int currentlyTagged;
        public final int completedSessions;

        CombatLogoutStats(int totalLogouts, int successfulProcessing, int failedProcessing,
                          int activeCombatLogouts, int currentlyTagged, int completedSessions) {
            this.totalLogouts = totalLogouts;
            this.successfulProcessing = successfulProcessing;
            this.failedProcessing = failedProcessing;
            this.activeCombatLogouts = activeCombatLogouts;
            this.currentlyTagged = currentlyTagged;
            this.completedSessions = completedSessions;
        }

        public double getSuccessRate() {
            if (totalLogouts == 0) return 100.0;
            return ((double) successfulProcessing / totalLogouts) * 100.0;
        }

        @Override
        public String toString() {
            return String.format("CombatLogoutStats{total=%d, success=%d, failed=%d, active=%d, tagged=%d, completed=%d, rate=%.1f%%}",
                    totalLogouts, successfulProcessing, failedProcessing, activeCombatLogouts,
                    currentlyTagged, completedSessions, getSuccessRate());
        }
    }
}
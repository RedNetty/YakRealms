package com.rednetty.server.core.mechanics.combat.logout;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.combat.pvp.ForceFieldManager;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.inventory.InventoryUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIXED CombatLogoutMechanics - Properly distinguishes combat tagging from combat logout
 *
 * Key Fixes:
 * - Combat tagging ≠ Combat logout - these are separate concerns
 * - Death while combat tagged = normal death processing (DeathMechanics handles it)
 * - Quit while combat tagged = combat logout processing (this class handles it)
 * - Fixed spam of combat tag marking
 * - Proper coordination to prevent system conflicts
 */
public class CombatLogoutMechanics implements Listener {
    private static final Logger LOGGER = Logger.getLogger(CombatLogoutMechanics.class.getName());
    private static CombatLogoutMechanics instance;

    // Combat system configuration constants
    private static final int COMBAT_TAG_DURATION_SECONDS = 15;
    private static final long COMBAT_TAG_CLEANUP_INTERVAL_TICKS = 20L;

    // Alignment-based drop probability constants
    private static final int NEUTRAL_WEAPON_DROP_PERCENTAGE = 50;
    private static final int NEUTRAL_ARMOR_PIECE_DROP_PERCENTAGE = 25;

    // FIXED: State tracking collections - separate combat tagging from logout processing
    private final Map<UUID, Long> combatTaggedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Player> lastKnownAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, CombatLogoutRecord> activeCombatLogouts = new ConcurrentHashMap<>();

    // FIXED: Track last tag time to prevent spam
    private final Map<UUID, Long> lastCombatTagTime = new ConcurrentHashMap<>();
    private static final long COMBAT_TAG_SPAM_PREVENTION_MS = 1000; // 1 second

    // Performance monitoring counters
    private final AtomicInteger totalCombatLogouts = new AtomicInteger(0);
    private final AtomicInteger successfulCombatLogoutProcesses = new AtomicInteger(0);
    private final AtomicInteger failedCombatLogoutProcesses = new AtomicInteger(0);
    private final AtomicInteger combatDeathsIgnored = new AtomicInteger(0);
    private final AtomicInteger combatTagSpamPrevented = new AtomicInteger(0);
    private final AtomicInteger systemCoordinationConflicts = new AtomicInteger(0);
    private final AtomicInteger inventoryDuplicationsFixed = new AtomicInteger(0);

    // System dependencies
    private final YakPlayerManager playerManager;
    private final ForceFieldManager forceFieldManager;
    private BukkitTask combatTagCleanupTask;

    private CombatLogoutMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        this.forceFieldManager = ForceFieldManager.getInstance();
    }

    public static CombatLogoutMechanics getInstance() {
        if (instance == null) {
            instance = new CombatLogoutMechanics();
        }
        return instance;
    }

    // ==================== LIFECYCLE METHODS ====================

    /**
     * Initialize the combat logout system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startCombatTagCleanupTask();
        // CombatLogoutMechanics system initialized with proper death coordination
    }

    /**
     * Shutdown the combat logout system
     */
    public void onDisable() {
        stopCleanupTask();
        processRemainingCombatLogouts();
        clearAllSystemState();
        // CombatLogoutMechanics system shutdown completed
    }

    /**
     * Stop the combat tag cleanup task
     */
    private void stopCleanupTask() {
        if (combatTagCleanupTask != null && !combatTagCleanupTask.isCancelled()) {
            combatTagCleanupTask.cancel();
        }
    }

    /**
     * Process any remaining combat logouts during shutdown
     */
    private void processRemainingCombatLogouts() {
        for (UUID playerId : new HashSet<>(activeCombatLogouts.keySet())) {
            try {
                processOfflineCombatLogout(playerId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing combat logout during shutdown: " + playerId, e);
            }
        }
    }

    /**
     * Clear all system state
     */
    private void clearAllSystemState() {
        combatTaggedPlayers.clear();
        lastKnownAttackers.clear();
        activeCombatLogouts.clear();
        lastCombatTagTime.clear();
    }

    // ==================== COMBAT TAG MANAGEMENT ====================

    /**
     * FIXED: Mark a player as combat tagged with spam prevention
     * This only marks combat status - does NOT set combat logout state
     */
    public void markPlayerCombatTagged(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // FIXED: Prevent spam of combat tag marking
        Long lastTagTime = lastCombatTagTime.get(playerId);
        if (lastTagTime != null && (currentTime - lastTagTime) < COMBAT_TAG_SPAM_PREVENTION_MS) {
            combatTagSpamPrevented.incrementAndGet();
            return; // Skip this tag to prevent spam
        }

        lastCombatTagTime.put(playerId, currentTime);
        boolean wasAlreadyTagged = combatTaggedPlayers.containsKey(playerId);

        combatTaggedPlayers.put(playerId, currentTime);
        updatePlayerCombatState(player, true);
        forceFieldManager.updatePlayerForceField(player);

        // Only notify once when first tagged
        if (!wasAlreadyTagged) {
            notifyPlayerCombatTagStart(player);
            // Player " + player.getName() + " marked as combat tagged"
        }
    }

    /**
     * Clear combat tag for a player and update their force field
     */
    public void clearPlayerCombatTag(Player player) {
        if (player == null || player.getUniqueId() == null) return;

        UUID playerId = player.getUniqueId();
        boolean wasTagged = combatTaggedPlayers.remove(playerId) != null;
        lastKnownAttackers.remove(playerId);
        lastCombatTagTime.remove(playerId);

        updatePlayerCombatState(player, false);

        if (wasTagged) {
            forceFieldManager.updatePlayerForceField(player);
            // Cleared combat tag and updated force field for " + player.getName()
        }
    }

    /**
     * Set the last known attacker for a player
     */
    public void setLastKnownAttacker(Player victim, Player attacker) {
        if (victim == null || attacker == null) return;

        lastKnownAttackers.put(victim.getUniqueId(), attacker);
        LOGGER.fine("Set last attacker for " + victim.getName() + ": " + attacker.getName());
    }

    /**
     * Update player's combat state in YakPlayer data
     */
    private void updatePlayerCombatState(Player player, boolean inCombat) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setInCombat(inCombat);
        }
    }

    /**
     * Notify player that combat has started
     */
    private void notifyPlayerCombatTagStart(Player player) {
        player.sendMessage(ChatColor.RED + "⚔️ You are in combat! Quitting now will result in item penalties!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
    }

    // ==================== COMBAT TAG QUERIES ====================

    /**
     * Check if a player is currently combat tagged
     */
    public boolean isPlayerCombatTagged(UUID playerId) {
        return combatTaggedPlayers.containsKey(playerId);
    }

    /**
     * Get remaining combat time for a player in seconds
     */
    public int getRemainingCombatTime(Player player) {
        if (player == null) return 0;

        Long tagTime = combatTaggedPlayers.get(player.getUniqueId());
        if (tagTime == null) return 0;

        long elapsedSeconds = (System.currentTimeMillis() - tagTime) / 1000L;
        return Math.max(0, COMBAT_TAG_DURATION_SECONDS - (int) elapsedSeconds);
    }

    /**
     * Check if a player is currently being processed for combat logout
     */
    public boolean isPlayerCombatLoggingOut(Player player) {
        if (player == null) return false;
        return activeCombatLogouts.containsKey(player.getUniqueId());
    }

    /**
     * Check if a player has an active combat logout record
     */
    public boolean hasActiveCombatLogoutRecord(UUID playerId) {
        return playerId != null && activeCombatLogouts.containsKey(playerId);
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * COMPLETELY FIXED: Handle player quit events - ONLY process if player actually quit while combat tagged
     *
     * KEY DISTINCTION:
     * - Death while combat tagged = normal death (DeathMechanics handles it)
     * - Quit while combat tagged = combat logout penalty (this class handles it)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Processing quit event for " + player.getName()

        // CRITICAL FIX: Only process combat logout if player is ACTUALLY combat tagged
        // Deaths while combat tagged are handled by DeathMechanics normally
        if (!isPlayerCombatTagged(playerId)) {
            // Player " + player.getName() + " quit but was not combat tagged - normal quit"
            return;
        }

        // CRITICAL: This is an actual combat logout (quit while tagged, not death)
        // COMBAT LOGOUT DETECTED for " + player.getName() + " - applying penalties"
        totalCombatLogouts.incrementAndGet();

        // IMPORTANT: Check if player is in godmode - if so, we shouldn't process combat logout penalties
        if (isPlayerInvulnerable(player)) {
            LOGGER.info("Player " + player.getName() + " is in godmode/invulnerable - skipping combat logout penalties");
            clearPlayerCombatTag(player);
            return;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(playerId);
            if (yakPlayer == null) {
                LOGGER.warning("No YakPlayer data found for combat logout: " + player.getName());
                failedCombatLogoutProcesses.incrementAndGet();
                return;
            }

            // Set combat logout state to PROCESSING to coordinate with DeathMechanics
            YakPlayer.CombatLogoutState previousState = yakPlayer.getCombatLogoutState();
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSING);
            // Set PROCESSING state for combat logout: " + player.getName() + " (previous: " + previousState + ")"

            // Check for existing processing conflicts
            if (previousState == YakPlayer.CombatLogoutState.PROCESSING) {
                // Combat logout already processing for " + player.getName() + " - preventing conflict"
                systemCoordinationConflicts.incrementAndGet();
                return;
            }

            boolean success = processCombatLogout(player, yakPlayer);

            if (success) {
                successfulCombatLogoutProcesses.incrementAndGet();
                updateQuitMessage(event, player.getName());
                LOGGER.info("✅ Combat logout processed successfully for " + player.getName());
            } else {
                failedCombatLogoutProcesses.incrementAndGet();
                LOGGER.warning("❌ Combat logout processing failed for " + player.getName());
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            }

        } catch (Exception e) {
            failedCombatLogoutProcesses.incrementAndGet();
            LOGGER.log(Level.SEVERE, "Error processing combat logout for " + player.getName(), e);

            // Reset state on exception
            try {
                YakPlayer yakPlayer = playerManager.getPlayer(playerId);
                if (yakPlayer != null) {
                    yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                    LOGGER.info("Reset combat logout state after exception for " + player.getName());
                }
            } catch (Exception resetError) {
                LOGGER.log(Level.SEVERE, "Failed to reset combat logout state", resetError);
            }
        } finally {
            finalizeCombatLogoutProcessing(playerId, player.getName());
        }
    }

    /**
     * Update quit message for combat logout
     */
    private void updateQuitMessage(PlayerQuitEvent event, String playerName) {
        event.setQuitMessage(ChatColor.RED + "⟨" + ChatColor.DARK_RED + "✦" + ChatColor.RED + "⟩ " +
                ChatColor.GRAY + playerName + ChatColor.DARK_GRAY + " fled from combat");
    }

    /**
     * Finalize combat logout processing cleanup
     */
    private void finalizeCombatLogoutProcessing(UUID playerId, String playerName) {
        combatTaggedPlayers.remove(playerId);
        lastKnownAttackers.remove(playerId);
        lastCombatTagTime.remove(playerId);
        LOGGER.info("Combat logout processing completed for " + playerName);
    }

    // ==================== COMBAT LOGOUT PROCESSING ====================

    /**
     * Process combat logout with alignment-based item handling
     */
    private boolean processCombatLogout(Player player, YakPlayer yakPlayer) {
        UUID playerId = player.getUniqueId();
        Location logoutLocation = player.getLocation().clone();
        String playerAlignment = getPlayerAlignment(yakPlayer);

        LOGGER.info("Processing combat logout for " + player.getName() + " with alignment: " + playerAlignment);

        try {
            CombatLogoutRecord logoutRecord = createCombatLogoutRecord(playerId, logoutLocation, playerAlignment);

            // Gather player items with duplication prevention
            PlayerInventoryData inventoryData = gatherPlayerInventoryData(player);
            LOGGER.info("Found " + inventoryData.allItems.size() + " items to process for combat logout");

            // Process items by alignment (same logic as DeathMechanics)
            ItemProcessingResult processingResult = processItemsByAlignment(inventoryData, playerAlignment);
            LOGGER.info("Combat logout processing result - Items kept: " + processingResult.itemsToKeep.size() +
                    ", Items dropped: " + processingResult.itemsToDrop.size());

            // Execute item processing and update player data
            executeItemProcessing(player, logoutLocation, processingResult, logoutRecord);
            updatePlayerDataAfterCombatLogout(player, yakPlayer, logoutLocation);
            finalizeCombatLogoutRecord(playerId, logoutRecord);

            // Broadcast combat logout notification
            broadcastCombatLogoutNotification(player.getName(), lastKnownAttackers.get(playerId), playerAlignment);
            LOGGER.info("✅ Combat logout fully processed for " + player.getName());
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in combat logout processing for " + player.getName(), e);
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            return false;
        }
    }

    /**
     * Gather player inventory data with duplication prevention
     */
    private PlayerInventoryData gatherPlayerInventoryData(Player player) {
        PlayerInventoryData inventoryData = new PlayerInventoryData();
        PlayerInventory inventory = player.getInventory();

        try {
            // Use a Set to track processed items and prevent duplicates
            Set<ItemStack> processedItems = new HashSet<>();
            int duplicatesSkipped = 0;

            // Process equipped armor first (priority locations)
            duplicatesSkipped += processEquippedArmor(inventory, inventoryData, processedItems);

            // Process hotbar items (skip if already processed as equipped armor)
            duplicatesSkipped += processHotbarItems(inventory, inventoryData, processedItems);

            // Process main inventory items (skip if already processed)
            duplicatesSkipped += processMainInventoryItems(inventory, inventoryData, processedItems);

            // Process offhand item (skip if already processed)
            duplicatesSkipped += processOffhandItem(inventory, inventoryData, processedItems);

            if (duplicatesSkipped > 0) {
                LOGGER.info("Prevented " + duplicatesSkipped + " item duplications for " + player.getName());
                inventoryDuplicationsFixed.addAndGet(duplicatesSkipped);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error gathering player inventory data", e);
        }

        return inventoryData;
    }

    /**
     * Process equipped armor pieces
     */
    private int processEquippedArmor(PlayerInventory inventory, PlayerInventoryData inventoryData, Set<ItemStack> processedItems) {
        ItemStack[] armorContents = {
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };

        int duplicatesSkipped = 0;
        for (ItemStack armorPiece : armorContents) {
            if (isValidItem(armorPiece)) {
                if (!processedItems.contains(armorPiece)) {
                    ItemStack copy = armorPiece.clone();
                    inventoryData.equippedArmor.add(copy);
                    inventoryData.allItems.add(copy);
                    processedItems.add(armorPiece);
                } else {
                    duplicatesSkipped++;
                    LOGGER.fine("Skipped duplicate armor piece: " + getItemDisplayName(armorPiece));
                }
            }
        }
        return duplicatesSkipped;
    }

    /**
     * Process hotbar items
     */
    private int processHotbarItems(PlayerInventory inventory, PlayerInventoryData inventoryData, Set<ItemStack> processedItems) {
        ItemStack[] contents = inventory.getContents();
        int duplicatesSkipped = 0;

        for (int i = 0; i < 9 && i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isValidItem(item)) {
                if (!processedItems.contains(item)) {
                    ItemStack copy = item.clone();
                    inventoryData.hotbarItems.add(copy);
                    inventoryData.allItems.add(copy);
                    processedItems.add(item);

                    // First hotbar slot is primary weapon slot
                    if (i == 0) {
                        inventoryData.primaryWeapon = copy;
                    }
                } else {
                    duplicatesSkipped++;
                    LOGGER.fine("Skipped duplicate item in hotbar slot " + i + ": " + getItemDisplayName(item));
                }
            }
        }
        return duplicatesSkipped;
    }

    /**
     * Process main inventory items
     */
    private int processMainInventoryItems(PlayerInventory inventory, PlayerInventoryData inventoryData, Set<ItemStack> processedItems) {
        ItemStack[] contents = inventory.getContents();
        int duplicatesSkipped = 0;

        for (int i = 9; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isValidItem(item)) {
                if (!processedItems.contains(item)) {
                    inventoryData.allItems.add(item.clone());
                    processedItems.add(item);
                } else {
                    duplicatesSkipped++;
                    LOGGER.fine("Skipped duplicate item in inventory slot " + i + ": " + getItemDisplayName(item));
                }
            }
        }
        return duplicatesSkipped;
    }

    /**
     * Process offhand item
     */
    private int processOffhandItem(PlayerInventory inventory, PlayerInventoryData inventoryData, Set<ItemStack> processedItems) {
        ItemStack offhandItem = inventory.getItemInOffHand();
        int duplicatesSkipped = 0;

        if (isValidItem(offhandItem)) {
            if (!processedItems.contains(offhandItem)) {
                ItemStack copy = offhandItem.clone();
                inventoryData.offhandItem = copy;
                inventoryData.allItems.add(copy);
                processedItems.add(offhandItem);
            } else {
                duplicatesSkipped++;
                LOGGER.fine("Skipped duplicate item in offhand: " + getItemDisplayName(offhandItem));
            }
        }
        return duplicatesSkipped;
    }

    /**
     * Process items according to player alignment rules
     */
    private ItemProcessingResult processItemsByAlignment(PlayerInventoryData inventoryData, String alignment) {
        ItemProcessingResult result = new ItemProcessingResult();

        if (inventoryData.allItems.isEmpty()) {
            LOGGER.info("No items to process for alignment: " + alignment);
            return result;
        }

        switch (alignment.toUpperCase()) {
            case "LAWFUL":
                processLawfulAlignmentItems(inventoryData, result);
                break;
            case "NEUTRAL":
                processNeutralAlignmentItems(inventoryData, result);
                break;
            case "CHAOTIC":
                processChaoticAlignmentItems(inventoryData, result);
                break;
            default:
                LOGGER.warning("Unknown alignment: " + alignment + " - defaulting to lawful rules");
                processLawfulAlignmentItems(inventoryData, result);
                break;
        }

        return result;
    }

    /**
     * Lawful alignment: Keep equipped armor + primary weapon + permanent untradeable items
     */
    private void processLawfulAlignmentItems(PlayerInventoryData inventoryData, ItemProcessingResult result) {
        for (ItemStack item : inventoryData.allItems) {
            if (item == null) continue;

            boolean shouldKeepItem = false;
            String retentionReason = "";

            if (isEquippedArmor(item, inventoryData.equippedArmor)) {
                shouldKeepItem = true;
                retentionReason = "equipped armor";
            } else if (isPrimaryWeapon(item, inventoryData.primaryWeapon)) {
                shouldKeepItem = true;
                retentionReason = "primary weapon";
            } else if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeepItem = true;
                retentionReason = "permanent untradeable";
            }

            if (shouldKeepItem) {
                result.itemsToKeep.add(item.clone());
                LOGGER.fine("LAWFUL - Keeping: " + getItemDisplayName(item) + " (" + retentionReason + ")");
            } else {
                result.itemsToDrop.add(item.clone());
                LOGGER.fine("LAWFUL - Dropping: " + getItemDisplayName(item));
            }
        }
    }

    /**
     * Neutral alignment: Keep permanent untradeable + equipped armor (75% per piece) + primary weapon (50% chance)
     */
    private void processNeutralAlignmentItems(PlayerInventoryData inventoryData, ItemProcessingResult result) {
        Random random = new Random();
        boolean shouldDropWeapon = random.nextInt(100) < NEUTRAL_WEAPON_DROP_PERCENTAGE;

        LOGGER.info("Neutral alignment rolls - Weapon: " + (shouldDropWeapon ? "DROP" : "KEEP"));

        // Pre-calculate armor drop results per piece
        Map<ItemStack, Boolean> armorDropResults = new HashMap<>();
        for (ItemStack armorPiece : inventoryData.equippedArmor) {
            boolean shouldDropThisPiece = random.nextInt(100) < NEUTRAL_ARMOR_PIECE_DROP_PERCENTAGE;
            armorDropResults.put(armorPiece, shouldDropThisPiece);
            LOGGER.info("Neutral alignment - Armor piece " + getItemDisplayName(armorPiece) + ": " +
                    (shouldDropThisPiece ? "DROP" : "KEEP"));
        }

        for (ItemStack item : inventoryData.allItems) {
            if (item == null) continue;

            boolean shouldKeepItem = false;
            String retentionReason = "";

            if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeepItem = true;
                retentionReason = "permanent untradeable";
            } else if (isEquippedArmor(item, inventoryData.equippedArmor)) {
                Boolean shouldDrop = armorDropResults.get(findMatchingArmorPiece(item, inventoryData.equippedArmor));
                if (shouldDrop == null || !shouldDrop) {
                    shouldKeepItem = true;
                    retentionReason = "equipped armor (passed 75% roll)";
                } else {
                    retentionReason = "equipped armor (failed 75% roll)";
                }
            } else if (isPrimaryWeapon(item, inventoryData.primaryWeapon)) {
                if (!shouldDropWeapon) {
                    shouldKeepItem = true;
                    retentionReason = "primary weapon (passed 50% roll)";
                } else {
                    retentionReason = "primary weapon (failed 50% roll)";
                }
            }

            if (shouldKeepItem) {
                result.itemsToKeep.add(item.clone());
                LOGGER.fine("NEUTRAL - Keeping: " + getItemDisplayName(item) + " (" + retentionReason + ")");
            } else {
                result.itemsToDrop.add(item.clone());
                LOGGER.fine("NEUTRAL - Dropping: " + getItemDisplayName(item) + " (" + retentionReason + ")");
            }
        }
    }

    /**
     * Chaotic alignment: Keep only permanent untradeable + quest items
     */
    private void processChaoticAlignmentItems(PlayerInventoryData inventoryData, ItemProcessingResult result) {
        for (ItemStack item : inventoryData.allItems) {
            if (item == null) continue;

            boolean shouldKeepItem = false;
            String retentionReason = "";

            if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeepItem = true;
                retentionReason = "permanent untradeable";
            } else if (InventoryUtils.isQuestItem(item)) {
                shouldKeepItem = true;
                retentionReason = "quest item";
            }

            if (shouldKeepItem) {
                result.itemsToKeep.add(item.clone());
                LOGGER.fine("CHAOTIC - Keeping: " + getItemDisplayName(item) + " (" + retentionReason + ")");
            } else {
                result.itemsToDrop.add(item.clone());
                LOGGER.fine("CHAOTIC - Dropping: " + getItemDisplayName(item));
            }
        }
    }

    /**
     * Find matching armor piece in equipped armor list
     */
    private ItemStack findMatchingArmorPiece(ItemStack item, List<ItemStack> equippedArmor) {
        for (ItemStack armorPiece : equippedArmor) {
            if (armorPiece != null && item.isSimilar(armorPiece)) {
                return armorPiece;
            }
        }
        return null;
    }

    /**
     * Check if item is equipped armor
     */
    private boolean isEquippedArmor(ItemStack item, List<ItemStack> equippedArmor) {
        return findMatchingArmorPiece(item, equippedArmor) != null;
    }

    /**
     * Check if item is the primary weapon
     */
    private boolean isPrimaryWeapon(ItemStack item, ItemStack primaryWeapon) {
        if (item == null || primaryWeapon == null) {
            return false;
        }
        return item.isSimilar(primaryWeapon);
    }

    /**
     * Execute item processing and inventory updates
     */
    private void executeItemProcessing(Player player, Location logoutLocation, ItemProcessingResult processingResult, CombatLogoutRecord logoutRecord) {
        // Drop items at logout location
        dropItemsAtLocation(logoutLocation, processingResult.itemsToDrop, player.getName());
        logoutRecord.droppedItems.addAll(processingResult.itemsToDrop);
        logoutRecord.keptItems.addAll(processingResult.itemsToKeep);
        logoutRecord.markItemsProcessed();

        // Clear inventory and apply kept items
        InventoryUtils.clearPlayerInventory(player);

        if (!processingResult.itemsToKeep.isEmpty()) {
            setKeptItemsAsInventory(player, processingResult.itemsToKeep);
            LOGGER.info("Set " + processingResult.itemsToKeep.size() + " kept items as new inventory");
        } else {
            LOGGER.info("No items kept, inventory remains empty");
        }
    }

    /**
     * Update player data after combat logout processing
     */
    private void updatePlayerDataAfterCombatLogout(Player player, YakPlayer yakPlayer, Location logoutLocation) {
        // Update inventory data to match processed items
        yakPlayer.updateInventory(player);
        LOGGER.info("Updated YakPlayer inventory data with processed items");

        // Move player to spawn location for combat logout
        World world = logoutLocation.getWorld();
        if (world != null) {
            Location spawnLocation = world.getSpawnLocation();
            yakPlayer.updateLocation(spawnLocation);
            LOGGER.info("Updated player location to spawn for combat logout");
        }

        // Increment death count and set final logout state
        yakPlayer.setDeaths(yakPlayer.getDeaths() + 1);
        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSED);
        LOGGER.info("Set combat logout state to PROCESSED");

        // Save all changes immediately with combat state coordination
        try {
            boolean saveSuccess = playerManager.savePlayerWithCombatSync(yakPlayer, true).get();
            if (saveSuccess) {
                LOGGER.info("Saved combat logout changes for " + player.getName());
            } else {
                LOGGER.warning("Failed to save combat logout changes for " + player.getName());
                throw new RuntimeException("Combat logout save failed");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save combat logout changes for " + player.getName(), e);
            // Reset combat state on save failure to prevent permanent inconsistency
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            throw new RuntimeException("Combat logout processing failed", e);
        }
    }

    /**
     * Finalize combat logout record
     */
    private void finalizeCombatLogoutRecord(UUID playerId, CombatLogoutRecord logoutRecord) {
        activeCombatLogouts.put(playerId, logoutRecord);
    }

    // ==================== ITEM HANDLING UTILITIES ====================

    /**
     * Drop items at specified location with safety checks
     */
    private void dropItemsAtLocation(Location location, List<ItemStack> items, String playerName) {
        if (location == null || location.getWorld() == null || items.isEmpty()) {
            LOGGER.fine("No items to drop for " + playerName);
            return;
        }

        LOGGER.info("Dropping " + items.size() + " items at combat logout location for " + playerName);

        World world = location.getWorld();

        // Ensure chunk is loaded for item dropping
        if (!world.isChunkLoaded(location.getChunk())) {
            LOGGER.info("Loading chunk for item drop at " + formatLocation(location));
            world.loadChunk(location.getChunk());
        }

        // Schedule item drop with slight delay to ensure world stability
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            try {
                if (world.isChunkLoaded(location.getChunk())) {
                    int successfulDrops = 0;

                    for (ItemStack item : items) {
                        if (!isValidItem(item)) continue;

                        try {
                            Location dropLocation = findSafeDropLocation(location, world);
                            world.dropItemNaturally(dropLocation, item.clone());
                            successfulDrops++;

                            LOGGER.fine("Dropped item: " + getItemDisplayName(item) + " at " + formatLocation(dropLocation));

                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Failed to drop item: " + getItemDisplayName(item), e);
                        }
                    }

                    if (successfulDrops > 0) {
                        LOGGER.info("Successfully dropped " + successfulDrops + "/" + items.size() + " items for " + playerName);
                    } else {
                        LOGGER.warning("Failed to drop any items for " + playerName);
                    }
                } else {
                    LOGGER.warning("Chunk unloaded during item drop for " + playerName + " - items lost");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Critical error dropping items for " + playerName, e);
            }
        });
    }

    /**
     * Find a safe location for dropping items, avoiding solid blocks
     */
    private Location findSafeDropLocation(Location originalLocation, World world) {
        Location dropLocation = originalLocation.clone();

        // Move up if location is inside a solid block
        while (dropLocation.getBlock().getType().isSolid() && dropLocation.getY() < world.getMaxHeight() - 1) {
            dropLocation.add(0, 1, 0);
        }

        return dropLocation;
    }

    /**
     * Set kept items as player's new inventory
     */
    private void setKeptItemsAsInventory(Player player, List<ItemStack> keptItems) {
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);

            ItemStack[] armorSlots = new ItemStack[4];
            List<ItemStack> inventoryItems = new ArrayList<>();

            // Categorize items
            for (ItemStack item : keptItems) {
                if (InventoryUtils.isArmorItem(item)) {
                    int armorSlot = InventoryUtils.getArmorSlot(item);
                    if (armorSlot >= 0 && armorSlot < 4 && armorSlots[armorSlot] == null) {
                        armorSlots[armorSlot] = item.clone();
                    } else {
                        inventoryItems.add(item.clone());
                    }
                } else {
                    inventoryItems.add(item.clone());
                }
            }

            // Apply items to inventory
            player.getInventory().setArmorContents(armorSlots);
            for (int i = 0; i < inventoryItems.size() && i < 36; i++) {
                player.getInventory().setItem(i, inventoryItems.get(i));
            }

            player.updateInventory();
            LOGGER.info("Set kept items as inventory for " + player.getName());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting kept items as inventory for " + player.getName(), e);
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Validate if an ItemStack is valid and not empty
     */
    private boolean isValidItem(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    /**
     * Check if a player is invulnerable (godmode, admin mode, etc.)
     * This prevents combat logout penalties for players who are invulnerable
     */
    private boolean isPlayerInvulnerable(Player player) {
        if (player == null) return false;

        try {
            // Check for Bukkit's built-in invulnerability
            if (player.isInvulnerable()) {
                return true;
            }

            // Check for operator with godmode enabled (unless they disabled god mode)
            if (player.isOp() && !isGodModeDisabled(player)) {
                return true;
            }

            // Check for any permission-based godmode plugins
            if (player.hasPermission("essentials.god") || 
                player.hasPermission("godmode") || 
                player.hasPermission("admin.god") ||
                player.hasPermission("modifyworld.immortal") ||
                player.hasPermission("worldguard.god")) {
                return true;
            }

            // Check for creative mode (usually invulnerable)
            if (player.getGameMode() == GameMode.CREATIVE) {
                return true;
            }

            return false;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking player invulnerability for " + player.getName(), e);
            return false; // For combat logout, we're less strict - only block if we're certain
        }
    }

    /**
     * Check if god mode is disabled for a player (from toggle system)
     */
    private boolean isGodModeDisabled(Player player) {
        try {
            // Use reflection to check toggles if available, otherwise default to false
            Class<?> togglesClass = Class.forName("com.rednetty.server.core.mechanics.player.settings.Toggles");
            java.lang.reflect.Method isToggled = togglesClass.getMethod("isToggled", Player.class, String.class);
            return (Boolean) isToggled.invoke(null, player, "God Mode Disabled");
        } catch (Exception e) {
            // If toggles system is not available, assume god mode is NOT disabled for ops
            return false;
        }
    }

    /**
     * Get display name for an ItemStack
     */
    private String getItemDisplayName(ItemStack item) {
        if (item == null) return "null";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name() + " x" + item.getAmount();
    }

    /**
     * Get player alignment with fallback to lawful
     */
    private String getPlayerAlignment(YakPlayer yakPlayer) {
        String alignment = yakPlayer.getAlignment();
        if (alignment == null || alignment.trim().isEmpty()) {
            alignment = "LAWFUL";
            yakPlayer.setAlignment(alignment);
        }
        return alignment;
    }

    /**
     * Create combat logout record
     */
    private CombatLogoutRecord createCombatLogoutRecord(UUID playerId, Location logoutLocation, String alignment) {
        Player attacker = lastKnownAttackers.get(playerId);
        return new CombatLogoutRecord(attacker, logoutLocation, alignment);
    }

    /**
     * Format location for logging
     */
    private String formatLocation(Location location) {
        if (location == null) return "null";
        return String.format("%.1f,%.1f,%.1f", location.getX(), location.getY(), location.getZ());
    }

    // ==================== MESSAGING AND NOTIFICATIONS ====================

    /**
     * Broadcast combat logout notification
     */
    private void broadcastCombatLogoutNotification(String playerName, Player attacker, String alignment) {
        try {
            String attackerName = attacker != null ? attacker.getName() : "unknown forces";
            String message = ChatColor.DARK_GRAY + "☠ " + ChatColor.RED + playerName +
                    ChatColor.GRAY + " was slain by " + ChatColor.RED + attackerName +
                    ChatColor.GRAY + " (combat logout)";

            Bukkit.broadcastMessage(message);
            LOGGER.info("Broadcast combat logout notification: " + playerName + " vs " + attackerName);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error broadcasting combat logout notification", e);
        }
    }

    // ==================== CLEANUP AND MAINTENANCE ====================

    /**
     * Start the combat tag cleanup task
     */
    private void startCombatTagCleanupTask() {
        combatTagCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredCombatTags();
            }
        }.runTaskTimer(YakRealms.getInstance(), COMBAT_TAG_CLEANUP_INTERVAL_TICKS, COMBAT_TAG_CLEANUP_INTERVAL_TICKS);
    }

    /**
     * Clean up expired combat tags
     */
    private void cleanupExpiredCombatTags() {
        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = combatTaggedPlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID playerId = entry.getKey();
            long tagTime = entry.getValue();

            if (currentTime - tagTime > (COMBAT_TAG_DURATION_SECONDS * 1000L)) {
                iterator.remove();
                lastKnownAttackers.remove(playerId);
                lastCombatTagTime.remove(playerId);

                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    notifyPlayerCombatTagEnd(player);
                    forceFieldManager.updatePlayerForceField(player);
                    LOGGER.info("Player " + player.getName() + " is no longer in combat");
                }
            }
        }
    }

    /**
     * Notify player that combat has ended
     */
    private void notifyPlayerCombatTagEnd(Player player) {
        player.sendMessage(ChatColor.GREEN + "You are no longer in combat.");
    }

    /**
     * Process offline combat logout
     */
    private void processOfflineCombatLogout(UUID playerId) {
        CombatLogoutRecord logoutRecord = activeCombatLogouts.get(playerId);
        if (logoutRecord == null || logoutRecord.itemsProcessed) {
            return;
        }

        LOGGER.info("Processing offline combat logout for " + playerId);
        logoutRecord.markItemsProcessed();
        activeCombatLogouts.put(playerId, logoutRecord);
    }

    /**
     * Handle player rejoining after combat logout
     */
    public void handleCombatLogoutRejoin(UUID playerId) {
        CombatLogoutRecord logoutRecord = activeCombatLogouts.remove(playerId);
        if (logoutRecord != null) {
            updatePlayerAfterRejoin(playerId);
            LOGGER.info("Cleared combat logout data for rejoining player: " + playerId);
        } else {
            LOGGER.fine("No combat logout data found for rejoining player: " + playerId);
        }
    }

    /**
     * Update player state after rejoining
     */
    private void updatePlayerAfterRejoin(UUID playerId) {
        YakPlayer yakPlayer = playerManager.getPlayer(playerId);
        if (yakPlayer != null) {
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
            try {
                playerManager.savePlayer(yakPlayer);
                LOGGER.info("Set combat logout state to COMPLETED for " + playerId);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save combat logout completion state", e);
            }
        }
    }

    // ==================== PERFORMANCE METRICS ====================

    /**
     * Get total number of combat logouts processed
     */
    public int getTotalCombatLogouts() {
        return totalCombatLogouts.get();
    }

    /**
     * Get number of successful combat logout processes
     */
    public int getSuccessfulCombatLogoutProcesses() {
        return successfulCombatLogoutProcesses.get();
    }

    /**
     * Get number of failed combat logout processes
     */
    public int getFailedCombatLogoutProcesses() {
        return failedCombatLogoutProcesses.get();
    }

    /**
     * Get number of active combat tags
     */
    public int getActiveCombatTagCount() {
        return combatTaggedPlayers.size();
    }

    /**
     * Get number of active combat logout records
     */
    public int getActiveCombatLogoutCount() {
        return activeCombatLogouts.size();
    }

    /**
     * Check if the system is operating within healthy parameters
     */
    public boolean isSystemHealthy() {
        int totalProcesses = successfulCombatLogoutProcesses.get() + failedCombatLogoutProcesses.get();
        if (totalProcesses == 0) return true;

        double failureRate = (double) failedCombatLogoutProcesses.get() / totalProcesses;
        return failureRate < 0.1; // Less than 10% failure rate
    }

    /**
     * FIXED: Get comprehensive performance statistics
     */
    public String getPerformanceStatistics() {
        return String.format("CombatLogoutMechanics Performance - " +
                        "Total: %d, Successful: %d, Failed: %d, Active: %d, " +
                        "Combat Deaths Ignored: %d, Spam Prevented: %d, Coordination Conflicts: %d, Duplications Fixed: %d",
                totalCombatLogouts.get(), successfulCombatLogoutProcesses.get(),
                failedCombatLogoutProcesses.get(), activeCombatLogouts.size(),
                combatDeathsIgnored.get(), combatTagSpamPrevented.get(),
                systemCoordinationConflicts.get(), inventoryDuplicationsFixed.get());
    }

    // ==================== DATA CLASSES ====================

    /**
     * Container for item processing results
     */
    private static class ItemProcessingResult {
        final List<ItemStack> itemsToKeep = new ArrayList<>();
        final List<ItemStack> itemsToDrop = new ArrayList<>();
    }

    /**
     * Container for player inventory data
     */
    private static class PlayerInventoryData {
        final List<ItemStack> allItems = new ArrayList<>();
        final List<ItemStack> hotbarItems = new ArrayList<>();
        final List<ItemStack> equippedArmor = new ArrayList<>();
        ItemStack primaryWeapon = null;
        ItemStack offhandItem = null;
    }

    /**
     * Record of combat logout information
     */
    private static class CombatLogoutRecord {
        final Player attacker;
        final Location logoutLocation;
        final String alignment;
        final List<ItemStack> droppedItems = new ArrayList<>();
        final List<ItemStack> keptItems = new ArrayList<>();
        final long timestamp;
        boolean itemsProcessed = false;

        CombatLogoutRecord(Player attacker, Location logoutLocation, String alignment) {
            this.attacker = attacker;
            this.logoutLocation = logoutLocation != null ? logoutLocation.clone() : null;
            this.alignment = alignment;
            this.timestamp = System.currentTimeMillis();
        }

        void markItemsProcessed() {
            this.itemsProcessed = true;
        }
    }
}
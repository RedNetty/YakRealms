package com.rednetty.server.mechanics.combat.logout;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.death.DeathMechanics;
import com.rednetty.server.mechanics.combat.pvp.ForceFieldManager;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.inventory.InventoryUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  Combat Logout Mechanics - Coordinates properly with DeathMechanics to prevent duplication
 *
 * KEY FIXES:
 * 1. Proper coordination with DeathMechanics to prevent double processing
 * 2. Atomic combat logout processing with state management
 * 3. Eliminated respawn item duplication by using different storage
 * 4. Enhanced state coordination and validation
 * 5. Better error handling and recovery mechanisms
 * 6. Clear separation of responsibilities between systems
 */
public class CombatLogoutMechanics implements Listener {
    private static final Logger logger = Logger.getLogger(CombatLogoutMechanics.class.getName());
    private static CombatLogoutMechanics instance;

    // Combat system constants
    private static final int COMBAT_TAG_DURATION_SECONDS = 15;
    private static final long COMBAT_TAG_CLEANUP_INTERVAL_TICKS = 20L;
    private static final long COORDINATION_DELAY_TICKS = 1L;
    private static final double HEALTHY_FAILURE_RATE_THRESHOLD = 0.1;

    // Neutral alignment drop chances
    private static final int NEUTRAL_WEAPON_DROP_CHANCE = 50; // 50%
    private static final int NEUTRAL_ARMOR_DROP_CHANCE = 25;  // 25%

    // FIXED: Atomic processing to prevent duplication
    private final Set<UUID> combatLogoutProcessingLock = ConcurrentHashMap.newKeySet();

    // Core state tracking
    private final Map<UUID, Long> combatTaggedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Player> lastAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, CombatLogoutData> activeCombatLogouts = new ConcurrentHashMap<>();
    private final Set<UUID> processingCombatLogouts = ConcurrentHashMap.newKeySet();

    // Performance metrics
    private final AtomicInteger totalCombatLogouts = new AtomicInteger(0);
    private final AtomicInteger successfulProcesses = new AtomicInteger(0);
    private final AtomicInteger failedProcesses = new AtomicInteger(0);
    private final AtomicInteger coordinationSuccesses = new AtomicInteger(0);
    private final AtomicInteger coordinationFailures = new AtomicInteger(0);
    private final AtomicInteger duplicationsPrevented = new AtomicInteger(0);

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

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startCombatTagCleanupTask();
        logger.info(" CombatLogoutMechanics enabled with duplication prevention");
    }

    public void onDisable() {
        stopCleanupTask();
        processRemainingCombatLogouts();
        clearAllState();
        logger.info(" CombatLogoutMechanics disabled");
    }

    private void stopCleanupTask() {
        if (combatTagCleanupTask != null && !combatTagCleanupTask.isCancelled()) {
            combatTagCleanupTask.cancel();
        }
    }

    private void processRemainingCombatLogouts() {
        for (UUID playerId : new HashSet<>(activeCombatLogouts.keySet())) {
            try {
                processOfflineCombatLogout(playerId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing combat logout during shutdown: " + playerId, e);
            }
        }
    }

    private void clearAllState() {
        combatTaggedPlayers.clear();
        lastAttackers.clear();
        activeCombatLogouts.clear();
        processingCombatLogouts.clear();
        combatLogoutProcessingLock.clear();
    }

    // ==================== COMBAT TAG MANAGEMENT ====================

    /**
     * Mark a player as combat tagged and update their force field
     */
    public void markCombatTagged(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        boolean wasAlreadyTagged = combatTaggedPlayers.containsKey(playerId);

        combatTaggedPlayers.put(playerId, System.currentTimeMillis());
        updatePlayerCombatState(player, true);
        forceFieldManager.updatePlayerForceField(player);

        if (!wasAlreadyTagged) {
            notifyPlayerCombatStart(player);
        }

        logger.info(": Player " + player.getName() + " marked as combat tagged");
    }

    /**
     * Clear combat tag for a player and update their force field
     */
    public void clearCombatTag(Player player) {
        if (player == null || player.getUniqueId() == null) return;

        UUID playerId = player.getUniqueId();
        boolean wasTagged = combatTaggedPlayers.remove(playerId) != null;
        lastAttackers.remove(playerId);

        updatePlayerCombatState(player, false);

        if (wasTagged) {
            forceFieldManager.updatePlayerForceField(player);
            logger.info(": Cleared combat tag and updated force field for " + player.getName());
        }
    }

    /**
     * Set the last attacker for a player
     */
    public void setLastAttacker(Player victim, Player attacker) {
        if (victim == null || attacker == null) return;

        lastAttackers.put(victim.getUniqueId(), attacker);
        logger.fine("Set last attacker for " + victim.getName() + ": " + attacker.getName());
    }

    private void updatePlayerCombatState(Player player, boolean inCombat) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setInCombat(inCombat);
        }
    }

    private void notifyPlayerCombatStart(Player player) {
        player.sendMessage(ChatColor.RED + "You are now in combat! Do not log out for 15 seconds!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
    }

    // ==================== COMBAT TAG QUERIES ====================

    /**
     * Check if a player is currently combat tagged
     */
    public boolean isPlayerTagged(UUID uuid) {
        boolean tagged = combatTaggedPlayers.containsKey(uuid);
        logger.fine("Combat tag check for " + uuid + ": " + tagged);
        return tagged;
    }

    /**
     * Get remaining combat time for a player
     */
    public int getCombatTimeRemaining(Player player) {
        if (player == null) return 0;

        Long tagTime = combatTaggedPlayers.get(player.getUniqueId());
        if (tagTime == null) return 0;

        long elapsedSeconds = (System.currentTimeMillis() - tagTime) / 1000L;
        return Math.max(0, COMBAT_TAG_DURATION_SECONDS - (int) elapsedSeconds);
    }

    /**
     * Check if a player is currently being processed for combat logout
     */
    public boolean isCombatLoggingOut(Player player) {
        if (player == null) return false;
        return processingCombatLogouts.contains(player.getUniqueId());
    }

    /**
     * Check if a player has an active combat logout record
     */
    public boolean hasActiveCombatLogout(UUID playerId) {
        // FIXED: Also check the processing lock to coordinate with DeathMechanics
        boolean hasActive = playerId != null &&
                (activeCombatLogouts.containsKey(playerId) || combatLogoutProcessingLock.contains(playerId));
        logger.fine(": Active combat logout check for " + playerId + ": " + hasActive);
        return hasActive;
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * Main combat logout handler with atomic processing
     * Uses HIGHEST priority to run before YakPlayerManager and coordinate data access
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        logger.info(": Player quit event for " + player.getName());

        if (!isPlayerTagged(playerId)) {
            logger.info("Player " + player.getName() + " quit but was not in combat");
            return;
        }

        // CRITICAL FIX: Check if DeathMechanics is already processing this player
        if (DeathMechanics.getInstance().isProcessingDeath(playerId)) {
            logger.info("COORDINATION: DeathMechanics already processing " + player.getName() + ", skipping combat logout");
            combatTaggedPlayers.remove(playerId);
            lastAttackers.remove(playerId);
            duplicationsPrevented.incrementAndGet();
            return;
        }

        // CRITICAL FIX: Atomic processing lock to prevent duplication
        if (!combatLogoutProcessingLock.add(playerId)) {
            logger.warning("DUPLICATION PREVENTION: Combat logout already being processed for " + player.getName());
            duplicationsPrevented.incrementAndGet();
            return;
        }

        totalCombatLogouts.incrementAndGet();

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(playerId);
            if (yakPlayer == null) {
                logger.warning("No YakPlayer found for combat logout: " + player.getName());
                failedProcesses.incrementAndGet();
                return;
            }

            // Set state to prevent DeathMechanics interference
            yakPlayer.setTemporaryData("combat_logout_processing", true);
            playerManager.savePlayer(yakPlayer);

            boolean success = processCombatLogoutFixed(player, yakPlayer);

            if (success) {
                successfulProcesses.incrementAndGet();
                coordinationSuccesses.incrementAndGet();
                updateQuitMessage(event, player.getName());
                logger.info(": Combat logout processed successfully for " + player.getName());
            } else {
                failedProcesses.incrementAndGet();
                coordinationFailures.incrementAndGet();
                logger.warning(": Combat logout processing failed for " + player.getName());
            }

        } catch (Exception e) {
            failedProcesses.incrementAndGet();
            coordinationFailures.incrementAndGet();
            logger.severe(": Error processing combat logout for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            finalizeCombatLogoutProcessing(playerId, player.getName());
        }
    }

    private void updateQuitMessage(PlayerQuitEvent event, String playerName) {
        event.setQuitMessage(ChatColor.RED + "⟨" + ChatColor.DARK_RED + "✦" + ChatColor.RED + "⟩ " +
                ChatColor.GRAY + playerName + ChatColor.DARK_GRAY + " fled from combat");
    }

    private void finalizeCombatLogoutProcessing(UUID playerId, String playerName) {
        combatTaggedPlayers.remove(playerId);
        lastAttackers.remove(playerId);
        processingCombatLogouts.remove(playerId);

        // FIXED: Release atomic processing lock
        combatLogoutProcessingLock.remove(playerId);

        // Signal completion to YakPlayerManager after a small delay
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            playerManager.markPlayerFinishedCombatLogout(playerId);
        }, COORDINATION_DELAY_TICKS);

        logger.info("=== FIXED COMBAT LOGOUT PROCESSING COMPLETE: " + playerName + " ===");
    }

    // ==================== FIXED COMBAT LOGOUT PROCESSING ====================

    /**
     * Process combat logout with better coordination and no respawn item duplication
     */
    private boolean processCombatLogoutFixed(Player player, YakPlayer yakPlayer) {
        UUID playerId = player.getUniqueId();
        Location logoutLocation = player.getLocation().clone();
        String alignment = getPlayerAlignment(yakPlayer);

        logger.info(": Processing combat logout for " + player.getName() + " (alignment: " + alignment + ")");

        try {
            // Set combat logout state IMMEDIATELY to coordinate with DeathMechanics
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSING);
            playerManager.savePlayer(yakPlayer);
            logger.info(": Set combat logout state to PROCESSING for " + player.getName());

            CombatLogoutData logoutData = createCombatLogoutData(playerId, logoutLocation, alignment);
            List<ItemStack> allItems = InventoryUtils.getAllPlayerItems(player);
            if (allItems == null) allItems = new ArrayList<>();

            logger.info(": Found " + allItems.size() + " items to process for combat logout");

            ProcessResult result = processItemsByAlignment(allItems, alignment, player);
            logger.info(": Combat logout processing result - Kept: " + result.keptItems.size() +
                    ", Dropped: " + result.droppedItems.size());

            executeItemProcessingFixed(player, logoutLocation, result, logoutData);
            updatePlayerDataAfterCombatLogoutFixed(player, yakPlayer, logoutLocation);
            finalizeCombatLogoutData(playerId, logoutData);

            broadcastCombatLogoutDeath(player.getName(), lastAttackers.get(playerId), alignment);
            logger.info(": Combat logout fully processed for " + player.getName());
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, ": Error in combat logout processing for " + player.getName(), e);
            // Reset state on error
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
            playerManager.savePlayer(yakPlayer);
            return false;
        }
    }

    private String getPlayerAlignment(YakPlayer yakPlayer) {
        String alignment = yakPlayer.getAlignment();
        if (alignment == null || alignment.trim().isEmpty()) {
            alignment = "LAWFUL";
            yakPlayer.setAlignment(alignment);
        }
        return alignment;
    }

    private CombatLogoutData createCombatLogoutData(UUID playerId, Location logoutLocation, String alignment) {
        Player attacker = lastAttackers.get(playerId);
        return new CombatLogoutData(attacker, logoutLocation, alignment);
    }

    /**
     * Execute item processing with better coordination
     */
    private void executeItemProcessingFixed(Player player, Location logoutLocation, ProcessResult result, CombatLogoutData logoutData) {
        dropItemsAtLocation(logoutLocation, result.droppedItems, player.getName());
        logoutData.droppedItems.addAll(result.droppedItems);
        logoutData.keptItems.addAll(result.keptItems);
        logoutData.markItemsProcessed();

        // FIXED: Clear inventory and apply kept items directly
        InventoryUtils.clearPlayerInventory(player);

        if (!result.keptItems.isEmpty()) {
            setKeptItemsAsInventoryFixed(player, result.keptItems);
            logger.info(": Set " + result.keptItems.size() + " kept items as new inventory");
        } else {
            logger.info(": No items kept, inventory remains empty");
        }
    }

    /**
     * Update player data after combat logout with better state management
     */
    private void updatePlayerDataAfterCombatLogoutFixed(Player player, YakPlayer yakPlayer, Location logoutLocation) {
        // CRITICAL FIX: Update inventory data to match processed items (already set above)
        yakPlayer.updateInventory(player);
        logger.info(": Updated YakPlayer inventory data with processed items");

        // Move player to spawn location
        World world = logoutLocation.getWorld();
        if (world != null) {
            Location spawnLocation = world.getSpawnLocation();
            yakPlayer.updateLocation(spawnLocation);
            logger.info(": Updated player location to spawn for combat logout");
        }

        // Increment death count and set final logout state
        yakPlayer.setDeaths(yakPlayer.getDeaths() + 1);
        yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSED);
        logger.info(": Set combat logout state to PROCESSED");

        // CRITICAL FIX: Do NOT store respawn items for combat logout
        // Combat logout processed inventory IS the final inventory
        yakPlayer.clearRespawnItems();
        logger.info(": Cleared any existing respawn items (combat logout handles inventory directly)");

        // Save all changes
        playerManager.savePlayer(yakPlayer);
    }

    private void finalizeCombatLogoutData(UUID playerId, CombatLogoutData logoutData) {
        activeCombatLogouts.put(playerId, logoutData);
    }

    // ==================== ITEM PROCESSING BY ALIGNMENT ====================

    /**
     * Process items based on player alignment rules
     */
    private ProcessResult processItemsByAlignment(List<ItemStack> allItems, String alignment, Player player) {
        ProcessResult result = new ProcessResult();

        if (allItems.isEmpty()) {
            logger.info(": No items to process for alignment: " + alignment);
            return result;
        }

        ItemStack firstHotbarItem = getFirstHotbarItem(allItems);

        switch (alignment.toUpperCase()) {
            case "LAWFUL":
                processLawfulCombatLogoutItems(allItems, firstHotbarItem, player, result);
                break;
            case "NEUTRAL":
                processNeutralCombatLogoutItems(allItems, firstHotbarItem, result);
                break;
            case "CHAOTIC":
                processChaoticCombatLogoutItems(allItems, result);
                break;
            default:
                logger.warning(": Unknown alignment: " + alignment + " - defaulting to lawful");
                processLawfulCombatLogoutItems(allItems, firstHotbarItem, player, result);
                break;
        }

        logger.info(": Processed " + allItems.size() + " items by " + alignment + " alignment: " +
                result.keptItems.size() + " kept, " + result.droppedItems.size() + " dropped");

        return result;
    }

    private void processLawfulCombatLogoutItems(List<ItemStack> allItems, ItemStack firstHotbarItem, Player player, ProcessResult result) {
        List<ItemStack> equippedArmor = InventoryUtils.getEquippedArmor(player);

        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            if (shouldKeepLawfulItem(item, equippedArmor, firstHotbarItem, safeCopy)) {
                result.keptItems.add(safeCopy);
                logger.fine(" LAWFUL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                logger.fine(" LAWFUL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private boolean shouldKeepLawfulItem(ItemStack item, List<ItemStack> equippedArmor, ItemStack firstHotbarItem, ItemStack safeCopy) {
        boolean isEquippedArmor = equippedArmor.stream().anyMatch(e -> e != null && e.isSimilar(item));
        boolean isFirstHotbar = firstHotbarItem != null && item.isSimilar(firstHotbarItem);
        boolean isPermanentUntradeable = InventoryUtils.isPermanentUntradeable(safeCopy);

        return isEquippedArmor || isFirstHotbar || isPermanentUntradeable;
    }

    private void processNeutralCombatLogoutItems(List<ItemStack> allItems, ItemStack firstHotbarItem, ProcessResult result) {
        Random random = new Random();
        boolean shouldDropWeapon = random.nextInt(100) < NEUTRAL_WEAPON_DROP_CHANCE;
        boolean shouldDropArmor = random.nextInt(100) < NEUTRAL_ARMOR_DROP_CHANCE;

        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            if (shouldKeepNeutralItem(item, firstHotbarItem, safeCopy, shouldDropWeapon, shouldDropArmor)) {
                result.keptItems.add(safeCopy);
                logger.fine(" NEUTRAL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                logger.fine(" NEUTRAL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private boolean shouldKeepNeutralItem(ItemStack item, ItemStack firstHotbarItem, ItemStack safeCopy, boolean shouldDropWeapon, boolean shouldDropArmor) {
        if (InventoryUtils.isPermanentUntradeable(safeCopy)) {
            return true;
        }

        if (InventoryUtils.isArmorItem(safeCopy) && !shouldDropArmor) {
            return true;
        }

        if (firstHotbarItem != null && item.isSimilar(firstHotbarItem) && !shouldDropWeapon) {
            return true;
        }

        return false;
    }

    private void processChaoticCombatLogoutItems(List<ItemStack> allItems, ProcessResult result) {
        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            if (InventoryUtils.isPermanentUntradeable(safeCopy) || InventoryUtils.isQuestItem(safeCopy)) {
                result.keptItems.add(safeCopy);
                logger.fine(" CHAOTIC KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                logger.fine(" CHAOTIC DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private ItemStack getFirstHotbarItem(List<ItemStack> allItems) {
        try {
            for (int i = 0; i < Math.min(9, allItems.size()); i++) {
                ItemStack item = allItems.get(i);
                if (InventoryUtils.isValidItem(item)) {
                    return InventoryUtils.createSafeCopy(item);
                }
            }
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, ": Error getting first hotbar item", e);
            return null;
        }
    }

    // ==================== ITEM HANDLING UTILITIES ====================

    /**
     * Drop items at the combat logout location
     */
    private void dropItemsAtLocation(Location location, List<ItemStack> items, String playerName) {
        if (location == null || location.getWorld() == null || items.isEmpty()) {
            logger.info(": No items to drop for " + playerName);
            return;
        }

        logger.info(": Dropping " + items.size() + " items at combat logout location for " + playerName);

        for (ItemStack item : items) {
            if (!InventoryUtils.isValidItem(item)) continue;

            try {
                location.getWorld().dropItemNaturally(location, item);
                logger.fine(": Dropped: " + InventoryUtils.getItemDisplayName(item));
            } catch (Exception e) {
                logger.log(Level.WARNING, ": Failed to drop item: " + InventoryUtils.getItemDisplayName(item), e);
            }
        }

        logger.info(": Successfully dropped all items for " + playerName);
    }

    /**
     * Set kept items as the player's inventory for saving (no respawn items)
     */
    private void setKeptItemsAsInventoryFixed(Player player, List<ItemStack> keptItems) {
        try {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);

            ItemStack[] armor = new ItemStack[4];
            List<ItemStack> regularItems = new ArrayList<>();

            categorizeKeptItems(keptItems, armor, regularItems);
            applyItemsToInventory(player, armor, regularItems);

            player.updateInventory();
            logger.info(": Set kept items as inventory for " + player.getName() + " (no respawn items stored)");

        } catch (Exception e) {
            logger.log(Level.SEVERE, ": Error setting kept items as inventory for " + player.getName(), e);
        }
    }

    private void categorizeKeptItems(List<ItemStack> keptItems, ItemStack[] armor, List<ItemStack> regularItems) {
        for (ItemStack item : keptItems) {
            if (InventoryUtils.isArmorItem(item)) {
                int slot = InventoryUtils.getArmorSlot(item);
                if (slot >= 0 && slot < 4 && armor[slot] == null) {
                    armor[slot] = item.clone();
                } else {
                    regularItems.add(item.clone());
                }
            } else {
                regularItems.add(item.clone());
            }
        }
    }

    private void applyItemsToInventory(Player player, ItemStack[] armor, List<ItemStack> regularItems) {
        player.getInventory().setArmorContents(armor);

        for (int i = 0; i < regularItems.size() && i < 36; i++) {
            player.getInventory().setItem(i, regularItems.get(i));
        }
    }

    // ==================== MESSAGING AND NOTIFICATIONS ====================

    /**
     * Broadcast combat logout death message to server
     */
    private void broadcastCombatLogoutDeath(String playerName, Player attacker, String alignment) {
        try {
            String attackerName = attacker != null ? attacker.getName() : "unknown forces";
            String message = ChatColor.DARK_GRAY + "☠ " + ChatColor.RED + playerName +
                    ChatColor.GRAY + " was slain by " + ChatColor.RED + attackerName +
                    ChatColor.GRAY + " (combat logout)";

            Bukkit.broadcastMessage(message);
            logger.info(": Broadcast combat logout death: " + playerName + " vs " + attackerName);

        } catch (Exception e) {
            logger.log(Level.WARNING, ": Error broadcasting combat logout death", e);
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

    private void cleanupExpiredCombatTags() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = combatTaggedPlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID playerId = entry.getKey();
            long tagTime = entry.getValue();

            if (isTagExpired(now, tagTime)) {
                processExpiredTag(iterator, playerId);
            }
        }
    }

    private boolean isTagExpired(long currentTime, long tagTime) {
        return currentTime - tagTime > (COMBAT_TAG_DURATION_SECONDS * 1000L);
    }

    private void processExpiredTag(Iterator<Map.Entry<UUID, Long>> iterator, UUID playerId) {
        iterator.remove();
        lastAttackers.remove(playerId);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            notifyPlayerCombatEnd(player);
            forceFieldManager.updatePlayerForceField(player);
            logger.info(": Player " + player.getName() + " is no longer in combat");
        }
    }

    private void notifyPlayerCombatEnd(Player player) {
        player.sendMessage(ChatColor.GREEN + "You are no longer in combat.");
    }

    /**
     * Process offline combat logout for server shutdown
     */
    private void processOfflineCombatLogout(UUID playerId) {
        CombatLogoutData logoutData = activeCombatLogouts.get(playerId);
        if (logoutData == null || logoutData.itemsProcessed) {
            return;
        }

        logger.info(": Processing offline combat logout for " + playerId);
        logoutData.markItemsProcessed();
        activeCombatLogouts.put(playerId, logoutData);
    }

    /**
     * Handle player rejoining after combat logout with better state management
     */
    public void handleCombatLogoutRejoin(UUID playerId) {
        CombatLogoutData logoutData = activeCombatLogouts.remove(playerId);
        if (logoutData != null) {
            updatePlayerAfterRejoin(playerId);
            logger.info(": Cleared combat logout data for rejoining player: " + playerId);
        } else {
            logger.warning(": No combat logout data found for rejoining player: " + playerId);
        }
    }

    private void updatePlayerAfterRejoin(UUID playerId) {
        YakPlayer yakPlayer = playerManager.getPlayer(playerId);
        if (yakPlayer != null) {
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
            yakPlayer.removeTemporaryData("combat_logout_processing");
            playerManager.savePlayer(yakPlayer);
            logger.info(": Set combat logout state to COMPLETED for " + playerId);
        }
    }

    // ==================== PERFORMANCE METRICS ====================

    public int getTotalCombatLogouts() {
        return totalCombatLogouts.get();
    }

    public int getSuccessfulProcesses() {
        return successfulProcesses.get();
    }

    public int getFailedProcesses() {
        return failedProcesses.get();
    }

    public int getActiveCombatTags() {
        return combatTaggedPlayers.size();
    }

    public int getActiveCombatLogouts() {
        return activeCombatLogouts.size();
    }

    public int getCoordinationSuccesses() {
        return coordinationSuccesses.get();
    }

    public int getCoordinationFailures() {
        return coordinationFailures.get();
    }

    public int getDuplicationsPrevented() {
        return duplicationsPrevented.get();
    }

    public boolean isSystemHealthy() {
        int totalCoordinations = coordinationSuccesses.get() + coordinationFailures.get();
        if (totalCoordinations == 0) return true;

        double failureRate = (double) coordinationFailures.get() / totalCoordinations;
        return failureRate < HEALTHY_FAILURE_RATE_THRESHOLD;
    }

    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        return String.format(" CombatLogoutMechanics Stats: " +
                        "Total=%d, Success=%d, Failed=%d, Active=%d, DuplicationsPrevented=%d",
                totalCombatLogouts.get(), successfulProcesses.get(), failedProcesses.get(),
                activeCombatLogouts.size(), duplicationsPrevented.get());
    }

    // ==================== HELPER CLASSES ====================

    private static class ProcessResult {
        final List<ItemStack> keptItems = new ArrayList<>();
        final List<ItemStack> droppedItems = new ArrayList<>();
    }

    /**
     * Combat logout data storage class
     */
    private static class CombatLogoutData {
        final Player attacker;
        final Location logoutLocation;
        final String alignment;
        final List<ItemStack> droppedItems = new ArrayList<>();
        final List<ItemStack> keptItems = new ArrayList<>();
        final long timestamp;
        boolean itemsProcessed = false;

        CombatLogoutData(Player attacker, Location logoutLocation, String alignment) {
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
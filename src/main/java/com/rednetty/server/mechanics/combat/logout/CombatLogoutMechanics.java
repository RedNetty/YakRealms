package com.rednetty.server.mechanics.combat.logout;

import com.rednetty.server.YakRealms;
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
 * FIXED Combat Logout Mechanics - properly coordinates with YakPlayerManager and ForceFieldManager
 *
 * CRITICAL FIXES:
 * - Now uses HIGHEST priority to run BEFORE YakPlayerManager
 * - Coordinates with YakPlayerManager for proper data access
 * - Uses new coordination methods to preserve player data during processing
 * - Prevents race conditions by marking players entering combat logout processing
 * - Properly signals when processing is complete for cleanup
 * - FIXED: Properly integrates with ForceFieldManager for combat-tagged players
 * - CRITICAL FIX: Now properly updates YakPlayer inventory data after processing to prevent item duplication
 */
public class CombatLogoutMechanics implements Listener {
    private static final Logger logger = Logger.getLogger(CombatLogoutMechanics.class.getName());
    private static CombatLogoutMechanics instance;

    // Combat timing constants
    private static final int COMBAT_TAG_DURATION = 15; // seconds
    private static final long COMBAT_TAG_CLEANUP_INTERVAL = 20L; // ticks

    // State tracking
    private final Map<UUID, Long> combatTaggedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Player> lastAttackers = new ConcurrentHashMap<>();
    private final Map<UUID, CombatLogoutData> activeCombatLogouts = new ConcurrentHashMap<>();
    private final Set<UUID> processingCombatLogouts = ConcurrentHashMap.newKeySet();

    // Performance tracking
    private final AtomicInteger totalCombatLogouts = new AtomicInteger(0);
    private final AtomicInteger successfulProcesses = new AtomicInteger(0);
    private final AtomicInteger failedProcesses = new AtomicInteger(0);
    private final AtomicInteger coordinationSuccesses = new AtomicInteger(0);
    private final AtomicInteger coordinationFailures = new AtomicInteger(0);

    // Dependencies
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

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startCombatTagCleanupTask();
        logger.info("FIXED CombatLogoutMechanics enabled with proper YakPlayerManager and ForceFieldManager coordination");
    }

    public void onDisable() {
        if (combatTagCleanupTask != null && !combatTagCleanupTask.isCancelled()) {
            combatTagCleanupTask.cancel();
        }

        // Process any remaining combat logouts
        for (UUID playerId : new HashSet<>(activeCombatLogouts.keySet())) {
            try {
                processOfflineCombatLogout(playerId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error processing combat logout during shutdown: " + playerId, e);
            }
        }

        combatTaggedPlayers.clear();
        lastAttackers.clear();
        activeCombatLogouts.clear();
        processingCombatLogouts.clear();

        logger.info("FIXED CombatLogoutMechanics disabled");
    }

    /**
     * Start combat tag cleanup task
     */
    private void startCombatTagCleanupTask() {
        combatTagCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Long>> iterator = combatTaggedPlayers.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    UUID playerId = entry.getKey();
                    long tagTime = entry.getValue();

                    if (now - tagTime > (COMBAT_TAG_DURATION * 1000L)) {
                        iterator.remove();
                        lastAttackers.remove(playerId);

                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline()) {
                            player.sendMessage(ChatColor.GREEN + "You are no longer in combat.");

                            // FIXED: Update force field when player leaves combat
                            forceFieldManager.updatePlayerForceField(player);

                            logger.info("Player " + player.getName() + " is no longer in combat");
                        }
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), COMBAT_TAG_CLEANUP_INTERVAL, COMBAT_TAG_CLEANUP_INTERVAL);
    }

    /**
     * FIXED: Mark player as combat tagged and immediately update force field
     */
    public void markCombatTagged(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        boolean wasAlreadyTagged = combatTaggedPlayers.containsKey(playerId);

        combatTaggedPlayers.put(playerId, System.currentTimeMillis());
        logger.info("Player " + player.getName() + " marked as combat tagged");

        // Update YakPlayer combat state
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setInCombat(true);
        }

        // FIXED: Immediately update force field for combat-tagged player
        forceFieldManager.updatePlayerForceField(player);

        // Only send message if player wasn't already in combat
        if (!wasAlreadyTagged) {
            player.sendMessage(ChatColor.RED + "You are now in combat! Do not log out for 15 seconds!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
        }
    }

    /**
     * FIXED: Clear combat tag for a player and update force field
     */
    public void clearCombatTag(Player player) {
        if (player == null || player.getUniqueId() == null) return;

        boolean wasTagged = combatTaggedPlayers.remove(player.getUniqueId()) != null;
        lastAttackers.remove(player.getUniqueId());

        // Update YakPlayer combat state
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer != null) {
            yakPlayer.setInCombat(false);
        }

        // FIXED: Update force field when combat tag is cleared
        if (wasTagged) {
            forceFieldManager.updatePlayerForceField(player);
            logger.info("Cleared combat tag and updated force field for " + player.getName());
        }
    }

    /**
     * Set last attacker for a player
     */
    public void setLastAttacker(Player victim, Player attacker) {
        if (victim == null || attacker == null) return;
        lastAttackers.put(victim.getUniqueId(), attacker);
        logger.fine("Set last attacker for " + victim.getName() + ": " + attacker.getName());
    }

    /**
     * Check if player is combat tagged
     */
    public boolean isPlayerTagged(UUID uuid) {
        boolean tagged = combatTaggedPlayers.containsKey(uuid);
        logger.fine("Combat tag check for " + uuid + ": " + tagged);
        return tagged;
    }

    /**
     * Get combat time remaining
     */
    public int getCombatTimeRemaining(Player player) {
        if (player == null) return 0;

        Long tagTime = combatTaggedPlayers.get(player.getUniqueId());
        if (tagTime == null) return 0;

        long elapsed = (System.currentTimeMillis() - tagTime) / 1000L;
        return Math.max(0, COMBAT_TAG_DURATION - (int) elapsed);
    }

    /**
     * Check if player is combat logging out
     */
    public boolean isCombatLoggingOut(Player player) {
        if (player == null) return false;
        return processingCombatLogouts.contains(player.getUniqueId());
    }

    /**
     * Check if player has active combat logout
     */
    public boolean hasActiveCombatLogout(UUID playerId) {
        boolean hasActive = playerId != null && activeCombatLogouts.containsKey(playerId);
        logger.fine("Active combat logout check for " + playerId + ": " + hasActive);
        return hasActive;
    }

    /**
     * FIXED: Main combat logout handler - properly coordinates with YakPlayerManager
     *
     * CRITICAL FIX: Now uses HIGHEST priority to run BEFORE YakPlayerManager
     * and coordinates data access through the manager
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        logger.info("FIXED: Player quit event for " + player.getName());

        // Check if player is combat tagged
        if (!isPlayerTagged(player.getUniqueId())) {
            logger.info("Player " + player.getName() + " quit but was not in combat");
            return; // Not in combat, normal quit
        }

        logger.warning("=== COMBAT LOGOUT DETECTED: " + player.getName() + " ===");
        totalCombatLogouts.incrementAndGet();

        try {
            // FIXED: Mark as processing to prevent duplicate processing and coordinate with YakPlayerManager
            processingCombatLogouts.add(playerId);
            playerManager.markPlayerEnteringCombatLogout(playerId);

            // FIXED: Get YakPlayer data through coordination method
            YakPlayer yakPlayer = playerManager.getPlayerForCombatLogout(playerId);
            if (yakPlayer == null) {
                logger.severe("FIXED: No YakPlayer data for combat logout: " + player.getName());
                coordinationFailures.incrementAndGet();
                failedProcesses.incrementAndGet();
                playerManager.markPlayerFinishedCombatLogout(playerId);
                return;
            }

            logger.info("FIXED: Successfully retrieved YakPlayer data for combat logout: " + player.getName());
            coordinationSuccesses.incrementAndGet();

            // Process combat logout immediately
            boolean success = processCombatLogout(player, yakPlayer);

            if (success) {
                successfulProcesses.incrementAndGet();

                // Update quit message to indicate combat logout
                event.setQuitMessage(ChatColor.RED + "⟨" + ChatColor.DARK_RED + "✦" + ChatColor.RED + "⟩ " +
                        ChatColor.GRAY + player.getName() + ChatColor.DARK_GRAY + " fled from combat");

                logger.info("FIXED: Combat logout processed successfully for " + player.getName());
            } else {
                failedProcesses.incrementAndGet();
                logger.severe("FIXED: Combat logout processing failed for " + player.getName());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "FIXED: Error processing combat logout for " + player.getName(), e);
            failedProcesses.incrementAndGet();
            coordinationFailures.incrementAndGet();
        } finally {
            // Clean up tracking but KEEP activeCombatLogouts for rejoin detection
            combatTaggedPlayers.remove(playerId);
            lastAttackers.remove(playerId);
            processingCombatLogouts.remove(playerId);

            // FIXED: Signal YakPlayerManager that combat logout processing is complete
            // This allows YakPlayerManager to properly clean up player data
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                playerManager.markPlayerFinishedCombatLogout(playerId);
            }, 1L); // Small delay to ensure this runs after everything else

            logger.info("=== COMBAT LOGOUT PROCESSING COMPLETE: " + player.getName() + " ===");
        }
    }

    /**
     * CRITICAL FIX: Process combat logout for online player with proper inventory synchronization
     *
     * FIXED: Now properly updates YakPlayer inventory data after processing to prevent item duplication
     */
    private boolean processCombatLogout(Player player, YakPlayer yakPlayer) {
        UUID playerId = player.getUniqueId();
        Location logoutLocation = player.getLocation().clone();
        String alignment = yakPlayer.getAlignment();

        if (alignment == null || alignment.trim().isEmpty()) {
            alignment = "LAWFUL";
            yakPlayer.setAlignment(alignment);
        }

        logger.info("FIXED: Processing combat logout for " + player.getName() + " (alignment: " + alignment + ")");

        try {
            // Create combat logout data
            Player attacker = lastAttackers.get(playerId);
            CombatLogoutData logoutData = new CombatLogoutData(attacker, logoutLocation, alignment);

            // Get all player items before processing
            List<ItemStack> allItems = InventoryUtils.getAllPlayerItems(player);
            if (allItems == null) allItems = new ArrayList<>();

            logger.info("FIXED: Found " + allItems.size() + " items to process for combat logout");

            // Process items by alignment
            ProcessResult result = processItemsByAlignment(allItems, alignment, player);

            logger.info("FIXED: Combat logout processing result - Kept: " + result.keptItems.size() +
                    ", Dropped: " + result.droppedItems.size());

            // Drop lost items at logout location
            dropItemsAtLocation(logoutLocation, result.droppedItems, player.getName());

            // Store processing results in combat logout data
            logoutData.droppedItems.addAll(result.droppedItems);
            logoutData.keptItems.addAll(result.keptItems);
            logoutData.markItemsProcessed();

            // Clear player inventory completely
            InventoryUtils.clearPlayerInventory(player);

            // Set kept items as player's new inventory (for saving)
            if (!result.keptItems.isEmpty()) {
                setKeptItemsAsInventory(player, result.keptItems);
                logger.info("FIXED: Set " + result.keptItems.size() + " kept items as new inventory");
            } else {
                logger.info("FIXED: No items kept, inventory remains empty");
            }

            // CRITICAL FIX: Update YakPlayer's serialized inventory data to match the processed inventory
            // This prevents item duplication by ensuring the saved data matches the processed result
            yakPlayer.updateInventory(player);
            logger.info("CRITICAL FIX: Updated YakPlayer inventory data with processed items");

            // Update player location to spawn for respawn
            World world = logoutLocation.getWorld();
            if (world != null) {
                Location spawnLocation = world.getSpawnLocation();
                yakPlayer.updateLocation(spawnLocation);
                logger.info("FIXED: Updated player location to spawn for combat logout");
            }

            // Update death count (simulated death)
            yakPlayer.setDeaths(yakPlayer.getDeaths() + 1);

            // Set combat logout state for rejoin detection
            yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.PROCESSED);
            logger.info("FIXED: Set combat logout state to PROCESSED");

            // FIXED: Save player data with new inventory and state through YakPlayerManager
            playerManager.savePlayer(yakPlayer);

            // Store combat logout data for rejoin handling
            activeCombatLogouts.put(playerId, logoutData);

            // Broadcast combat logout death message
            broadcastCombatLogoutDeath(player.getName(), attacker, alignment);

            logger.info("FIXED: Combat logout fully processed for " + player.getName());
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "FIXED: Error in combat logout processing for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Process items by alignment for combat logout
     */
    private ProcessResult processItemsByAlignment(List<ItemStack> allItems, String alignment, Player player) {
        ProcessResult result = new ProcessResult();

        if (allItems.isEmpty()) {
            logger.info("No items to process for alignment: " + alignment);
            return result;
        }

        // Get first hotbar item for weapon detection
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
                logger.warning("Unknown alignment: " + alignment + " - defaulting to lawful");
                processLawfulCombatLogoutItems(allItems, firstHotbarItem, player, result);
                break;
        }

        logger.info("Processed " + allItems.size() + " items by " + alignment + " alignment: " +
                result.keptItems.size() + " kept, " + result.droppedItems.size() + " dropped");

        return result;
    }

    private void processLawfulCombatLogoutItems(List<ItemStack> allItems, ItemStack firstHotbarItem, Player player, ProcessResult result) {
        List<ItemStack> equippedArmor = InventoryUtils.getEquippedArmor(player);

        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            // Keep equipped armor and first hotbar item
            boolean isEquippedArmor = equippedArmor.stream().anyMatch(e -> e != null && e.isSimilar(item));
            boolean isFirstHotbar = firstHotbarItem != null && item.isSimilar(firstHotbarItem);
            boolean isPermanentUntradeable = InventoryUtils.isPermanentUntradeable(safeCopy);

            if (isEquippedArmor || isFirstHotbar || isPermanentUntradeable) {
                result.keptItems.add(safeCopy);
                logger.fine("LAWFUL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                logger.fine("LAWFUL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private void processNeutralCombatLogoutItems(List<ItemStack> allItems, ItemStack firstHotbarItem, ProcessResult result) {
        Random random = new Random();
        boolean shouldDropWeapon = random.nextInt(2) == 0; // 50%
        boolean shouldDropArmor = random.nextInt(4) == 0; // 25%

        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            boolean shouldKeep = InventoryUtils.isPermanentUntradeable(safeCopy) ||
                    (InventoryUtils.isArmorItem(safeCopy) && !shouldDropArmor) ||
                    (firstHotbarItem != null && item.isSimilar(firstHotbarItem) && !shouldDropWeapon);

            if (shouldKeep) {
                result.keptItems.add(safeCopy);
                logger.fine("NEUTRAL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                logger.fine("NEUTRAL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private void processChaoticCombatLogoutItems(List<ItemStack> allItems, ProcessResult result) {
        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            if (InventoryUtils.isPermanentUntradeable(safeCopy) || InventoryUtils.isQuestItem(safeCopy)) {
                result.keptItems.add(safeCopy);
                logger.fine("CHAOTIC KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                logger.fine("CHAOTIC DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
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
            logger.log(Level.WARNING, "Error getting first hotbar item", e);
            return null;
        }
    }

    /**
     * Drop items at logout location
     */
    private void dropItemsAtLocation(Location location, List<ItemStack> items, String playerName) {
        if (location == null || location.getWorld() == null || items.isEmpty()) {
            logger.info("No items to drop for " + playerName);
            return;
        }

        logger.info("Dropping " + items.size() + " items at combat logout location for " + playerName);

        for (ItemStack item : items) {
            if (!InventoryUtils.isValidItem(item)) continue;

            try {
                location.getWorld().dropItemNaturally(location, item);
                logger.fine("Dropped: " + InventoryUtils.getItemDisplayName(item));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to drop item: " + InventoryUtils.getItemDisplayName(item), e);
            }
        }

        logger.info("Successfully dropped all items for " + playerName);
    }

    /**
     * Set kept items as player's inventory for saving
     */
    private void setKeptItemsAsInventory(Player player, List<ItemStack> keptItems) {
        try {
            // Clear inventory first
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);

            // Separate armor and regular items
            ItemStack[] armor = new ItemStack[4];
            List<ItemStack> regularItems = new ArrayList<>();

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

            // Set armor
            player.getInventory().setArmorContents(armor);

            // Set regular items (prioritize first hotbar slot)
            for (int i = 0; i < regularItems.size() && i < 36; i++) {
                player.getInventory().setItem(i, regularItems.get(i));
            }

            player.updateInventory();
            logger.info("Set kept items as inventory for " + player.getName());

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error setting kept items as inventory for " + player.getName(), e);
        }
    }

    /**
     * Broadcast combat logout death message
     */
    private void broadcastCombatLogoutDeath(String playerName, Player attacker, String alignment) {
        try {
            String attackerName = attacker != null ? attacker.getName() : "unknown forces";
            String message = ChatColor.DARK_GRAY + "☠ " + ChatColor.RED + playerName +
                    ChatColor.GRAY + " was slain by " + ChatColor.RED + attackerName +
                    ChatColor.GRAY + " (combat logout)";

            Bukkit.broadcastMessage(message);
            logger.info("Broadcast combat logout death: " + playerName + " vs " + attackerName);

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error broadcasting combat logout death", e);
        }
    }

    /**
     * Process offline combat logout (for shutdown)
     */
    private void processOfflineCombatLogout(UUID playerId) {
        CombatLogoutData logoutData = activeCombatLogouts.get(playerId);
        if (logoutData == null || logoutData.itemsProcessed) {
            return;
        }

        logger.info("Processing offline combat logout for " + playerId);

        // Mark as processed to prevent reprocessing
        logoutData.markItemsProcessed();
        activeCombatLogouts.put(playerId, logoutData);
    }

    /**
     * Handle combat logout rejoin - clear data after successful rejoin
     */
    public void handleCombatLogoutRejoin(UUID playerId) {
        CombatLogoutData logoutData = activeCombatLogouts.remove(playerId);
        if (logoutData != null) {
            logger.info("Cleared combat logout data for rejoining player: " + playerId);

            // Get player and clear combat logout state
            YakPlayer yakPlayer = playerManager.getPlayer(playerId);
            if (yakPlayer != null) {
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.COMPLETED);
                playerManager.savePlayer(yakPlayer);
                logger.info("Set combat logout state to COMPLETED for " + playerId);
            }
        } else {
            logger.warning("No combat logout data found for rejoining player: " + playerId);
        }
    }

    // Helper classes
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

    // Performance tracking methods
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

    public boolean isSystemHealthy() {
        int totalCoordinations = coordinationSuccesses.get() + coordinationFailures.get();
        if (totalCoordinations == 0) return true;

        double failureRate = (double) coordinationFailures.get() / totalCoordinations;
        return failureRate < 0.1; // Less than 10% failure rate
    }
}
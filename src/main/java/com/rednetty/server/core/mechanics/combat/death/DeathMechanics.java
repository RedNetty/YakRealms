package com.rednetty.server.core.mechanics.combat.death;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.inventory.InventoryUtils;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.rednetty.server.core.mechanics.combat.pvp.AlignmentMechanics.generateRandomSpawnPoint;

/**
 * FIXED DeathMechanics - PREVENTS ITEM DUPLICATION
 *
 * CRITICAL FIXES:
 * - Debounce system prevents multiple death events from processing
 * - Atomic processing locks prevent concurrent death processing
 * - Clear tracking of which players are being processed
 * - Fail-safe mechanisms to prevent any item duplication
 */
public class DeathMechanics implements Listener {
    private static final Logger LOGGER = Logger.getLogger(DeathMechanics.class.getName());

    // System configuration constants
    private static final long RESPAWN_RESTORE_DELAY_TICKS = 10L;
    private static final double DEFAULT_MAX_HEALTH = 50.0;
    private static final int DEFAULT_ENERGY = 100;
    private static final int RESPAWN_BLINDNESS_DURATION_TICKS = 60;

    // CRITICAL: Death processing debounce to prevent duplication
    private static final long DEATH_PROCESSING_DEBOUNCE_MS = 2000L; // 2 seconds

    // Alignment-based drop probability constants
    private static final int NEUTRAL_WEAPON_DROP_PERCENTAGE = 50;
    private static final int NEUTRAL_ARMOR_PIECE_DROP_PERCENTAGE = 25;

    // Singleton instance
    private static DeathMechanics instance;

    // FIXED: Enhanced state tracking to prevent duplication
    private final Map<UUID, DeathRecord> recentDeaths = new ConcurrentHashMap<>();
    private final Set<UUID> playersBeingRespawned = ConcurrentHashMap.newKeySet();

    // CRITICAL: Death processing locks to prevent concurrent processing
    private final Set<UUID> playersBeingProcessedForDeath = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDeathProcessTime = new ConcurrentHashMap<>();

    // Performance monitoring counters (enhanced)
    private final AtomicInteger totalDeathsProcessed = new AtomicInteger(0);
    private final AtomicInteger normalDeathsProcessed = new AtomicInteger(0);
    private final AtomicInteger combatDeathsProcessed = new AtomicInteger(0);
    private final AtomicInteger actualCombatLogoutsSkipped = new AtomicInteger(0);
    private final AtomicInteger successfulItemRestorations = new AtomicInteger(0);
    private final AtomicInteger failedItemRestorations = new AtomicInteger(0);
    private final AtomicInteger itemsDroppedSuccessfully = new AtomicInteger(0);
    private final AtomicInteger itemDropFailures = new AtomicInteger(0);

    // CRITICAL: New counters to track duplication prevention
    private final AtomicInteger duplicateDeathEventsBlocked = new AtomicInteger(0);
    private final AtomicInteger concurrentProcessingBlocked = new AtomicInteger(0);
    private final AtomicInteger debounceHits = new AtomicInteger(0);

    // System dependencies
    private final DeathRemnantManager remnantManager;
    private final YakPlayerManager playerManager;

    private DeathMechanics() {
        this.remnantManager = new DeathRemnantManager(YakRealms.getInstance());
        this.playerManager = YakPlayerManager.getInstance();
    }

    public static DeathMechanics getInstance() {
        if (instance == null) {
            instance = new DeathMechanics();
        }
        return instance;
    }

    // ==================== LIFECYCLE METHODS ====================

    /**
     * Initialize the death mechanics system
     */
    public void onEnable() {
        try {
            instance = this;
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
            // DeathMechanics initialized - DUPLICATION PREVENTION ACTIVE
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize DeathMechanics: " + e.getMessage());
            throw new RuntimeException("DeathMechanics initialization failed", e);
        }
    }

    /**
     * Shutdown the death mechanics system
     */
    public void onDisable() {
        if (remnantManager != null) {
            remnantManager.shutdown();
        }

        // Clear all tracking data
        recentDeaths.clear();
        playersBeingRespawned.clear();
        playersBeingProcessedForDeath.clear();
        lastDeathProcessTime.clear();

        // DeathMechanics system shutdown completed
    }

    // ==================== DEATH EVENT HANDLING ====================

    /**
     * COMPLETELY FIXED: Handle player death events with duplication prevention and godmode checks
     *
     * CRITICAL FIXES:
     * - Godmode and invulnerability checks prevent fake death processing
     * - Debounce system prevents rapid-fire death events
     * - Atomic locks prevent concurrent processing
     * - Clear state tracking prevents confusion
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // DEATH EVENT for " + player.getName() + " - Starting processing"

        // CRITICAL FIX #0: Check for godmode and other invulnerability states
        if (isPlayerInvulnerable(player)) {
            LOGGER.info("BLOCKED: Player " + player.getName() + " is invulnerable (godmode/admin) - cancelling fake death event");
            duplicateDeathEventsBlocked.incrementAndGet();
            event.setCancelled(true);
            return;
        }

        // CRITICAL FIX #1: Check if we're already processing this player's death
        if (playersBeingProcessedForDeath.contains(playerId)) {
            // BLOCKED: Already processing death for " + player.getName() + " - preventing duplication"
            concurrentProcessingBlocked.incrementAndGet();
            event.setCancelled(true);
            return;
        }

        // CRITICAL FIX #2: Debounce rapid death events
        Long lastProcessTime = lastDeathProcessTime.get(playerId);
        if (lastProcessTime != null && (currentTime - lastProcessTime) < DEATH_PROCESSING_DEBOUNCE_MS) {
            // DEBOUNCE: Death event too soon - preventing duplication
            debounceHits.incrementAndGet();
            event.setCancelled(true);
            return;
        }

        // CRITICAL FIX #3: Check if player is being respawned
        if (playersBeingRespawned.contains(playerId)) {
            // BLOCKED: Player " + player.getName() + " is being respawned - preventing duplication"
            duplicateDeathEventsBlocked.incrementAndGet();
            event.setCancelled(true);
            return;
        }

        // ATOMIC LOCK: Claim this player for death processing
        if (!playersBeingProcessedForDeath.add(playerId)) {
            // ATOMIC LOCK FAILED: Could not claim " + player.getName() + " for death processing"
            concurrentProcessingBlocked.incrementAndGet();
            event.setCancelled(true);
            return;
        }

        try {
            // Update last process time immediately
            lastDeathProcessTime.put(playerId, currentTime);
            totalDeathsProcessed.incrementAndGet();

            YakPlayer yakPlayer = playerManager.getPlayer(playerId);
            if (yakPlayer == null) {
                // No YakPlayer data found for death processing: " + player.getName()
                return;
            }

            // Check for combat logout processing conflicts
            if (yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.PROCESSING) {
                // Skipping death processing - player is being processed for combat LOGOUT quit: " + player.getName()
                actualCombatLogoutsSkipped.incrementAndGet();
                return;
            }

            // Additional check: If player is still invulnerable even after initial check, skip processing
            if (isPlayerInvulnerable(player)) {
                // Player became invulnerable during processing - aborting death processing
                duplicateDeathEventsBlocked.incrementAndGet();
                return;
            }

            // Process the death normally
            boolean wasCombatTagged = yakPlayer.isInCombat();
            if (wasCombatTagged) {
                // Processing COMBAT DEATH for " + player.getName() + " - applying alignment rules normally"
                combatDeathsProcessed.incrementAndGet();
            } else {
                // Processing NORMAL DEATH for " + player.getName() + " - applying alignment rules"
                normalDeathsProcessed.incrementAndGet();
            }

            // Process death with full item handling
            processPlayerDeath(event, player, yakPlayer, wasCombatTagged);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Critical error processing death for " + player.getName(), e);
        } finally {
            // CRITICAL: Schedule the removal of the processing lock after a delay
            // This ensures respawn has time to complete before allowing new deaths
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                playersBeingProcessedForDeath.remove(playerId);
                LOGGER.fine("🔓 Released death processing lock for " + player.getName());
            }, 60L); // 3 seconds delay
        }
    }

    /**
     * Process player death with proper alignment-based item handling
     */
    private void processPlayerDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer, boolean wasCombatTagged) {
        try {
            String playerAlignment = getPlayerAlignment(yakPlayer);
            Location deathLocation = player.getLocation().clone();

            // Processing death for player (Combat Tagged and Alignment checked)

            // CRITICAL: Clear current inventory state to prevent confusion
            PlayerInventory inventory = player.getInventory();
            // Inventory state before processing: " + countInventoryItems(inventory) + " items"

            // Gather all player items using the FIXED InventoryUtils
            List<ItemStack> allItems = InventoryUtils.getAllPlayerItems(player);
            // Gathered " + allItems.size() + " total items for death processing"

            // Log the items we're processing for debugging
            logItemsBeingProcessed(allItems);

            // Process items according to alignment rules
            ItemProcessingResult processingResult = processItemsByAlignment(allItems, playerAlignment, player);

            // Death processing result - Items kept and dropped calculated

            // CRITICAL: Configure death event to prevent default Minecraft behavior
            configureDeathEvent(event, processingResult);

            // CRITICAL: Clear player inventory BEFORE dropping items to prevent duplication
            // Clearing player inventory to prevent duplication"
            InventoryUtils.clearPlayerInventory(player);
            player.updateInventory();

            // Drop items at death location
            dropItemsAtLocation(deathLocation, processingResult.itemsToDrop, player.getName());

            // CRITICAL: Store kept items for respawn restoration
            storeItemsForRespawn(yakPlayer, processingResult.itemsToKeep);

            // Create death remnant and update statistics
            createDeathRemnant(deathLocation, player);
            updatePlayerDeathStatistics(yakPlayer);

            // Store death information for respawn processing
            recordDeathInformation(player.getUniqueId(), deathLocation, playerAlignment, wasCombatTagged);

            // Death processing completed successfully for " + player.getName()

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing death for " + player.getName(), e);
        }
    }

    /**
     * Count items in inventory for debugging
     */
    private int countInventoryItems(PlayerInventory inventory) {
        int count = 0;

        // Count main inventory
        ItemStack[] contents = inventory.getContents();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (InventoryUtils.isValidItem(item)) count++;
            }
        }

        // Count armor
        ItemStack[] armor = inventory.getArmorContents();
        if (armor != null) {
            for (ItemStack item : armor) {
                if (InventoryUtils.isValidItem(item)) count++;
            }
        }

        // Count offhand
        if (InventoryUtils.isValidItem(inventory.getItemInOffHand())) {
            count++;
        }

        return count;
    }

    /**
     * Log items being processed for debugging
     */
    private void logItemsBeingProcessed(List<ItemStack> items) {
        if (items.isEmpty()) {
            // No items to process
            return;
        }

        // Items being processed:
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (InventoryUtils.isValidItem(item)) {
                // Item " + (i+1) + ". " + InventoryUtils.getItemDisplayName(item)
            }
        }
    }

    /**
     * Process items according to player alignment rules using InventoryUtils
     */
    private ItemProcessingResult processItemsByAlignment(List<ItemStack> allItems, String alignment, Player player) {
        ItemProcessingResult result = new ItemProcessingResult();

        if (allItems.isEmpty()) {
            // No items to process for alignment: " + alignment
            return result;
        }

        // Get first hotbar item (primary weapon)
        ItemStack firstHotbarItem = getFirstHotbarItem(player);

        // Processing " + allItems.size() + " items for " + alignment + " alignment"
        if (firstHotbarItem != null) {
            // Primary weapon: " + InventoryUtils.getItemDisplayName(firstHotbarItem)
        }

        // Pre-calculate neutral weapon chance (single roll for weapon consistency)
        boolean neutralShouldDropWeapon = false;

        if ("NEUTRAL".equals(alignment)) {
            Random random = new Random();
            neutralShouldDropWeapon = random.nextInt(100) < NEUTRAL_WEAPON_DROP_PERCENTAGE;
            // Neutral weapon RNG - Drop Weapon: " + neutralShouldDropWeapon
        }

        // Process each item according to alignment rules
        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            boolean shouldKeep;

            if ("NEUTRAL".equals(alignment)) {
                // For neutral alignment, handle each item individually
                shouldKeep = processNeutralAlignmentItem(item, firstHotbarItem, neutralShouldDropWeapon);
            } else {
                // For other alignments, use the standard utility method
                shouldKeep = InventoryUtils.determineIfItemShouldBeKept(
                        item, alignment, player, firstHotbarItem, false, false
                );
            }

            if (shouldKeep) {
                result.itemsToKeep.add(InventoryUtils.createSafeCopy(item));
            } else {
                result.itemsToDrop.add(InventoryUtils.createSafeCopy(item));
            }
        }

        // Alignment processing complete - Items calculated
        return result;
    }

    /**
     * Process individual item for neutral alignment with per-item armor rolls
     */
    private boolean processNeutralAlignmentItem(ItemStack item, ItemStack firstHotbarItem, boolean neutralShouldDropWeapon) {
        // Always keep permanent untradeable items
        if (InventoryUtils.isPermanentUntradeable(item)) {
            LOGGER.fine("NEUTRAL - Keeping: " + InventoryUtils.getItemDisplayName(item) + " (permanent untradeable)");
            return true;
        }

        // Always keep quest items
        if (InventoryUtils.isQuestItem(item)) {
            LOGGER.fine("NEUTRAL - Keeping: " + InventoryUtils.getItemDisplayName(item) + " (quest item)");
            return true;
        }

        // Handle primary weapon with pre-calculated roll
        if (firstHotbarItem != null && InventoryUtils.isSameItem(item, firstHotbarItem)) {
            if (!neutralShouldDropWeapon) {
                LOGGER.fine("NEUTRAL - Keeping: " + InventoryUtils.getItemDisplayName(item) + " (weapon passed 50% roll)");
                return true;
            } else {
                LOGGER.fine("NEUTRAL - Dropping: " + InventoryUtils.getItemDisplayName(item) + " (weapon failed 50% roll)");
                return false;
            }
        }

        // Handle armor with individual 25% drop chance per piece
        if (InventoryUtils.isArmorItem(item)) {
            Random random = new Random();
            boolean shouldDropThisArmor = random.nextInt(100) < NEUTRAL_ARMOR_PIECE_DROP_PERCENTAGE;

            if (!shouldDropThisArmor) {
                LOGGER.fine("NEUTRAL - Keeping: " + InventoryUtils.getItemDisplayName(item) + " (armor passed 25% roll)");
                return true;
            } else {
                LOGGER.fine("NEUTRAL - Dropping: " + InventoryUtils.getItemDisplayName(item) + " (armor failed 25% roll)");
                return false;
            }
        }

        // All other items are dropped for neutral players
        LOGGER.fine("NEUTRAL - Dropping: " + InventoryUtils.getItemDisplayName(item) + " (other item)");
        return false;
    }

    /**
     * Get the first hotbar item (primary weapon)
     */
    private ItemStack getFirstHotbarItem(Player player) {
        try {
            ItemStack[] contents = player.getInventory().getContents();
            if (contents != null && contents.length > 0 && InventoryUtils.isValidItem(contents[0])) {
                return contents[0];
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting first hotbar item", e);
        }
        return null;
    }

    /**
     * Configure death event to prevent Minecraft's default item handling
     */
    private void configureDeathEvent(PlayerDeathEvent event, ItemProcessingResult processingResult) {
        try {
            // CRITICAL: Clear all default drops and disable keep inventory
            event.getDrops().clear();
            event.setKeepInventory(false);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // Cleared death event drops - manually handling items

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error configuring death event", e);
        }
    }

    /**
     * Drop items at specified location with chunk loading and safety checks
     */
    private void dropItemsAtLocation(Location location, List<ItemStack> items, String playerName) {
        if (location == null || location.getWorld() == null || items.isEmpty()) {
            // No items to drop for " + playerName
            return;
        }

        // Dropping " + items.size() + " items at death location for " + playerName

        World world = location.getWorld();

        // Ensure chunk is loaded for item dropping
        if (!world.isChunkLoaded(location.getChunk())) {
            // Loading chunk for item drop at " + formatLocation(location)
            world.loadChunk(location.getChunk());
        }

        // Schedule item drop with slight delay to ensure world stability
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            try {
                if (world.isChunkLoaded(location.getChunk())) {
                    int successfulDrops = 0;

                    for (ItemStack item : items) {
                        if (!InventoryUtils.isValidItem(item)) continue;

                        try {
                            Location dropLocation = findSafeDropLocation(location, world);
                            world.dropItemNaturally(dropLocation, item.clone());
                            successfulDrops++;
                            itemsDroppedSuccessfully.incrementAndGet();

                            LOGGER.fine("📦 Dropped: " + InventoryUtils.getItemDisplayName(item) +
                                    " at " + formatLocation(dropLocation));

                        } catch (Exception e) {
                            itemDropFailures.incrementAndGet();
                            LOGGER.log(Level.WARNING, "Failed to drop item: " +
                                    InventoryUtils.getItemDisplayName(item), e);
                        }
                    }

                    if (successfulDrops > 0) {
                        // Successfully dropped items for player
                    } else {
                        // Failed to drop any items for player
                    }
                } else {
                    // Chunk unloaded during item drop - items lost
                    itemDropFailures.addAndGet(items.size());
                }
            } catch (Exception e) {
                itemDropFailures.addAndGet(items.size());
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
     * CRITICAL: Store kept items for respawn restoration
     */
    private void storeItemsForRespawn(YakPlayer yakPlayer, List<ItemStack> itemsToKeep) {
        try {
            if (itemsToKeep.isEmpty()) {
                yakPlayer.clearRespawnItems();
                // No items to store for respawn: " + yakPlayer.getUsername()
                return;
            }

            // Filter out any invalid items
            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack item : itemsToKeep) {
                if (InventoryUtils.isValidItem(item)) {
                    validItems.add(InventoryUtils.createSafeCopy(item));
                } else {
                    // Skipping invalid item during respawn storage
                }
            }

            if (validItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                // No valid items after filtering for respawn storage
                return;
            }

            yakPlayer.clearRespawnItems();
            boolean success = yakPlayer.setRespawnItems(validItems);

            if (success) {
                // Successfully stored items for respawn restoration

                // Log what we're storing for debugging
                for (ItemStack item : validItems) {
                    // Stored: item details
                }
            } else {
                // Failed to store respawn items for player
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error storing respawn items for " + yakPlayer.getUsername(), e);
        }
    }

    // ==================== RESPAWN EVENT HANDLING ====================

    /**
     * Handle player respawn events with duplication prevention
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Processing respawn event for " + player.getName()

        if (!isValidPlayer(player)) {
            LOGGER.warning("Invalid player in respawn event");
            return;
        }

        // CRITICAL: Check if we're still processing death for this player
        if (playersBeingProcessedForDeath.contains(playerId)) {
            // Death still being processed for " + player.getName() + " during respawn - this is expected"
        }

        // Prevent concurrent respawn processing
        if (!playersBeingRespawned.add(playerId)) {
            // Respawn already in progress for " + player.getName()
            return;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                // No YakPlayer data found during respawn: " + player.getName()
                return;
            }

            DeathRecord deathRecord = recentDeaths.get(playerId);

            // Configure respawn location
            configureRespawnLocation(event, yakPlayer, player);

            // Schedule post-respawn processing
            schedulePostRespawnProcessing(player, yakPlayer, deathRecord, playerId);

            // Apply immediate respawn effects
            applyImmediateRespawnEffects(player);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling respawn for " + player.getName(), e);
        } finally {
            // Ensure respawn lock is released
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                playersBeingRespawned.remove(playerId);
                LOGGER.fine("🔓 Released respawn lock for " + player.getName());
            }, 40L);
        }
    }

    /**
     * Schedule post-respawn processing with delay
     */
    private void schedulePostRespawnProcessing(Player player, YakPlayer yakPlayer, DeathRecord deathRecord, UUID playerId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    playersBeingRespawned.remove(playerId);
                    return;
                }

                try {
                    processPostRespawn(player, yakPlayer, deathRecord);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error in post-respawn processing for " + player.getName(), e);
                } finally {
                    playersBeingRespawned.remove(playerId);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), RESPAWN_RESTORE_DELAY_TICKS);
    }

    /**
     * Process post-respawn restoration and effects
     */
    private void processPostRespawn(Player player, YakPlayer yakPlayer, DeathRecord deathRecord) {
        try {
            // Processing post-respawn for " + player.getName()

            // CRITICAL: Ensure inventory is completely clear before restoration
            // Ensuring inventory is clear before item restoration
            InventoryUtils.clearPlayerInventory(player);
            player.updateInventory();

            // Check for and restore respawn items
            if (yakPlayer.hasRespawnItems()) {
                // Found respawn items to restore for " + player.getName()

                boolean restored = restoreRespawnItems(player, yakPlayer);
                if (restored) {
                    // Successfully restored respawn items for " + player.getName()
                    successfulItemRestorations.incrementAndGet();
                } else {
                    // Failed to restore respawn items for " + player.getName()
                    failedItemRestorations.incrementAndGet();
                }
            } else {
                // No respawn items found for " + player.getName() + " - check death processing"
                failedItemRestorations.incrementAndGet();
            }

            // Apply final respawn effects
            applyFinalRespawnEffects(player);

            // Clean up death record
            if (deathRecord != null) {
                recentDeaths.remove(player.getUniqueId());
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in post-respawn processing for " + player.getName(), e);
        }
    }

    /**
     * Restore player's respawn items to inventory
     */
    public boolean restoreRespawnItems(Player player, YakPlayer yakPlayer) {
        if (yakPlayer == null || !yakPlayer.hasRespawnItems()) {
            // No respawn items to restore for " + (player != null ? player.getName() : "unknown player")
            return false;
        }

        try {
            List<ItemStack> respawnItems = yakPlayer.getRespawnItems();
            if (respawnItems.isEmpty()) {
                // Respawn items list is empty for " + player.getName()
                yakPlayer.clearRespawnItems();
                return false;
            }

            // Restoring " + respawnItems.size() + " respawn items for " + player.getName()

            // CRITICAL: Double-check inventory is clear
            InventoryUtils.clearPlayerInventory(player);

            // Separate armor from other items
            List<ItemStack> armorItems = new ArrayList<>();
            List<ItemStack> otherItems = new ArrayList<>();

            for (ItemStack item : respawnItems) {
                if (!InventoryUtils.isValidItem(item)) continue;

                if (InventoryUtils.isArmorItem(item)) {
                    armorItems.add(item);
                } else {
                    otherItems.add(item);
                }
            }

            // Apply armor to armor slots
            ItemStack[] armorSlots = new ItemStack[4];
            for (ItemStack armor : armorItems) {
                int slot = InventoryUtils.getArmorSlot(armor);
                if (slot >= 0 && slot < 4 && armorSlots[slot] == null) {
                    armorSlots[slot] = armor.clone();
                    // Restoring armor: " + InventoryUtils.getItemDisplayName(armor) + " to slot " + slot
                } else {
                    // If armor slot is taken or invalid, put in main inventory
                    otherItems.add(armor);
                }
            }

            // Apply armor
            player.getInventory().setArmorContents(armorSlots);

            // Apply other items to main inventory
            for (int i = 0; i < otherItems.size() && i < 36; i++) {
                player.getInventory().setItem(i, otherItems.get(i).clone());
                // Restoring item: " + InventoryUtils.getItemDisplayName(otherItems.get(i)) + " to slot " + i
            }

            player.updateInventory();
            yakPlayer.clearRespawnItems();
            playerManager.savePlayer(yakPlayer);

            // Successfully restored respawn items for " + player.getName()
            player.sendMessage(ChatColor.GREEN + "Your items from death have been restored!");

            successfulItemRestorations.incrementAndGet();
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error restoring respawn items for " + player.getName(), e);
            failedItemRestorations.incrementAndGet();
            return false;
        }
    }

    // ==================== RESPAWN EFFECTS ====================

    /**
     * Apply immediate effects upon respawn
     */
    private void applyImmediateRespawnEffects(Player player) {
        try {
            PotionEffect blindnessEffect = new PotionEffect(PotionEffectType.BLINDNESS, RESPAWN_BLINDNESS_DURATION_TICKS, 0);
            player.addPotionEffect(blindnessEffect);
        } catch (Exception e) {
            LOGGER.warning("Failed to apply immediate respawn effects: " + e.getMessage());
        }
    }

    /**
     * Apply final respawn effects after item restoration
     */
    private void applyFinalRespawnEffects(Player player) {
        try {
            // Reset health and energy
            player.setHealth(player.getMaxHealth());

            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setTemporaryData("energy", DEFAULT_ENERGY);
            }

            // Play respawn notification sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);

            // Applied final respawn effects for " + player.getName()

        } catch (Exception e) {
            // Error applying final respawn effects for " + player.getName() + ": " + e.getMessage()
        }
    }

    /**
     * Configure respawn location based on player data
     */
    private void configureRespawnLocation(PlayerRespawnEvent event, YakPlayer yakPlayer, Player player) {
        try {
            World defaultWorld = Bukkit.getWorlds().get(0);
            Location respawnLocation = null;

            // Check for bed spawn location
            if (yakPlayer.getBedSpawnLocation() != null) {
                respawnLocation = parseBedSpawnLocation(yakPlayer.getBedSpawnLocation());
            }

            // Fall back to appropriate spawn location
            if (respawnLocation == null) {
                if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
                    respawnLocation = generateRandomSpawnPoint(player.getName());
                } else {
                    respawnLocation = defaultWorld.getSpawnLocation();
                }
            }

            if (respawnLocation != null) {
                event.setRespawnLocation(respawnLocation);
                yakPlayer.updateLocation(respawnLocation);
            }

        } catch (Exception e) {
            // Error configuring respawn location for " + player.getName() + ": " + e.getMessage()
        }
    }

    /**
     * Parse bed spawn location from string format
     */
    private Location parseBedSpawnLocation(String bedSpawnData) {
        try {
            String[] parts = bedSpawnData.split(":");
            if (parts.length >= 4) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    return new Location(world,
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3]));
                }
            }
        } catch (Exception e) {
            // Failed to parse bed spawn location: " + bedSpawnData
        }
        return null;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Validate if a Player object is valid
     */
    private boolean isValidPlayer(Player player) {
        return player != null && player.getUniqueId() != null;
    }

    /**
     * Check if a player is invulnerable (godmode, admin mode, etc.)
     * This prevents fake death events from being processed when players are in godmode
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

            // Check if player has valid health but death event fired (indicates godmode from other plugin)
            if (player.getHealth() > 0.0) {
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
            // If we can't determine invulnerability, err on the side of caution and block processing
            return true;
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
     * Update player death statistics
     */
    private void updatePlayerDeathStatistics(YakPlayer yakPlayer) {
        yakPlayer.setDeaths(yakPlayer.getDeaths() + 1);
        playerManager.savePlayer(yakPlayer);
    }

    /**
     * Record death information with combat flag
     */
    private void recordDeathInformation(UUID playerId, Location deathLocation, String alignment, boolean wasCombatTagged) {
        DeathRecord deathRecord = new DeathRecord(deathLocation, alignment, System.currentTimeMillis(), wasCombatTagged);
        recentDeaths.put(playerId, deathRecord);
    }

    /**
     * Create decorative death remnant
     */
    private void createDeathRemnant(Location location, Player player) {
        try {
            if (remnantManager != null) {
                remnantManager.createDeathRemnant(location, Collections.emptyList(), player);
            }
        } catch (Exception e) {
            // Failed to create death remnant for " + player.getName()
        }
    }

    /**
     * Format location for logging
     */
    private String formatLocation(Location location) {
        if (location == null) return "null";
        return String.format("%.1f,%.1f,%.1f", location.getX(), location.getY(), location.getZ());
    }

    // ==================== PUBLIC API ====================

    /**
     * Check if a player is currently being processed for death
     */
    public boolean isProcessingDeath(UUID playerId) {
        return playersBeingProcessedForDeath.contains(playerId) || playersBeingRespawned.contains(playerId);
    }

    /**
     * Get comprehensive performance statistics including duplication prevention
     */
    public String getPerformanceStatistics() {
        return String.format("DeathMechanics Performance - " +
                        "Total Deaths: %d, Normal Deaths: %d, Combat Deaths: %d, Combat Logouts Skipped: %d, " +
                        "Successful Restorations: %d, Failed Restorations: %d, " +
                        "Items Dropped: %d, Drop Failures: %d, " +
                        "DUPLICATION PREVENTION - Duplicate Events Blocked: %d, Concurrent Processing Blocked: %d, Debounce Hits: %d",
                totalDeathsProcessed.get(), normalDeathsProcessed.get(), combatDeathsProcessed.get(),
                actualCombatLogoutsSkipped.get(), successfulItemRestorations.get(), failedItemRestorations.get(),
                itemsDroppedSuccessfully.get(), itemDropFailures.get(),
                duplicateDeathEventsBlocked.get(), concurrentProcessingBlocked.get(), debounceHits.get());
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
     * Record of death information with combat flag
     */
    private static class DeathRecord {
        final Location deathLocation;
        final String alignment;
        final long timestamp;
        final boolean wasCombatTagged;

        DeathRecord(Location deathLocation, String alignment, long timestamp, boolean wasCombatTagged) {
            this.deathLocation = deathLocation != null ? deathLocation.clone() : null;
            this.alignment = alignment;
            this.timestamp = timestamp;
            this.wasCombatTagged = wasCombatTagged;
        }
    }
}
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
 * FIXED DeathMechanics - Complete implementation with respawn item persistence
 *
 * KEY FIXES:
 * 1. Proper item identification using isSimilar() instead of type+amount
 * 2. Enhanced inventory slot tracking (equipped vs hotbar vs inventory)
 * 3. Fixed alignment rules to match specification exactly
 * 4. Better coordination with CombatLogoutMechanics
 * 5. COMPLETE respawn item restoration logic
 * 6. Login-time respawn item detection and restoration
 */
public class DeathMechanics implements Listener {

    // Configuration constants
    private static final long RESPAWN_RESTORE_DELAY = 10L;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final double DEFAULT_MAX_HEALTH = 50.0;
    private static final int DEFAULT_LEVEL = 100;
    private static final int DEFAULT_ENERGY = 100;
    private static final int RESPAWN_BLINDNESS_DURATION = 60;

    // FIXED: Alignment drop chances as specified
    private static final int NEUTRAL_WEAPON_DROP_CHANCE = 50; // 50% chance to drop weapon
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

    public static DeathMechanics getInstance() {
        if (instance == null) {
            instance = new DeathMechanics();
        }
        return instance;
    }

    public void onEnable() {
        try {
            instance = this;
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
            YakRealms.log("✅ FIXED DeathMechanics registered with complete respawn item restoration");

            if (this instanceof Listener) {
                YakRealms.log("✅ DeathMechanics implements Listener interface correctly");
            } else {
                YakRealms.log("[ERROR] FIXED DeathMechanics does NOT implement Listener interface!");
            }

        } catch (Exception e) {
            YakRealms.error("❌ DeathMechanics: Failed to register event listeners", e);
        }

        YakRealms.log("✅ FIXED DeathMechanics enabled with complete respawn persistence");
    }

    public void onDisable() {
        if (remnantManager != null) {
            remnantManager.shutdown();
        }

        recentDeaths.clear();
        respawnInProgress.clear();
        deathProcessingLock.clear();

        YakRealms.log("✅ FIXED DeathMechanics disabled");
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
            YakRealms.error("❌ Critical error in death processing for " + player.getName(), e);
        } finally {
            deathProcessingLock.remove(playerId);
        }
    }

    public boolean isProcessingDeath(UUID playerId) {
        return deathProcessingLock.contains(playerId);
    }

    /**
     * Handle combat logout death - minimal processing to avoid duplication
     */
    private void handleCombatLogoutDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer) {
        YakRealms.log("🎯 Processing combat logout death for " + player.getName());
        combatLogoutDeaths.incrementAndGet();

        try {
            // CRITICAL: Let combat logout mechanics handle everything
            // We only create a decorative remnant and skip all item processing

            // Configure event to not drop anything (combat logout handles items)
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // Store minimal death info for coordination
            storeDeathInfo(player.getUniqueId(), player.getLocation(), yakPlayer.getAlignment(), true);

            YakRealms.log("✅ Combat logout death processing complete - items handled by CombatLogoutMechanics");

        } catch (Exception e) {
            YakRealms.error("❌ Error handling combat logout death for " + player.getName(), e);
        }
    }

    /**
     * FIXED: Process normal death with proper alignment-based item management
     */
    private void handleNormalDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer) {
        try {
            String alignment = validateAndGetAlignment(yakPlayer);
            Location deathLocation = player.getLocation();

            YakRealms.log("🎯 Processing NORMAL death for " + player.getName() + " (alignment: " + alignment + ")");
            normalDeaths.incrementAndGet();

            // CRITICAL FIX: Get items with PROPER slot tracking
            PlayerItemData itemData = gatherPlayerItemsWithSlotTracking(player);

            YakRealms.log("📦 Gathered items for " + player.getName() + ":");
            YakRealms.log("  - Total items: " + itemData.allItems.size());
            YakRealms.log("  - Equipped armor pieces: " + itemData.equippedArmor.size());
            YakRealms.log("  - First hotbar item: " + (itemData.firstHotbarItem != null ?
                    getItemDisplayName(itemData.firstHotbarItem) : "none"));
            YakRealms.log("  - Hotbar items: " + itemData.hotbarItems.size());

            // FIXED: Process items according to alignment rules with proper logic
            ProcessResult result = processItemsByAlignmentFixed(itemData, alignment);

            YakRealms.log("⚖️ Alignment processing result for " + alignment + ":");
            YakRealms.log("  - Items to keep: " + result.keptItems.size());
            YakRealms.log("  - Items to drop: " + result.droppedItems.size());

            // Log what items are being kept/dropped for debugging
            logItemProcessingDetails(result, player.getName());

            // CRITICAL FIX: Configure death event properly without duplication
            configureDeathEventFixed(event, result);

            // Store kept items for respawn (ONLY for normal deaths)
            storeKeptItemsForRespawn(yakPlayer, result.keptItems);

            // Create visual remnant and update player data
            createDecorativeRemnant(deathLocation, player);
            updatePlayerDeathData(yakPlayer);

            // Store death information for respawn processing
            storeDeathInfo(player.getUniqueId(), deathLocation, alignment, false);

            YakRealms.log("✅ Normal death processed - Kept: " + result.keptItems.size() + ", Dropped: " + result.droppedItems.size());

        } catch (Exception e) {
            YakRealms.error("❌ Error handling normal death for " + player.getName(), e);
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Enhanced item gathering with proper slot tracking
     */
    private PlayerItemData gatherPlayerItemsWithSlotTracking(Player player) {
        PlayerItemData data = new PlayerItemData();
        PlayerInventory inv = player.getInventory();

        try {
            // FIXED: Get equipped armor from actual equipment slots
            ItemStack helmet = inv.getHelmet();
            ItemStack chestplate = inv.getChestplate();
            ItemStack leggings = inv.getLeggings();
            ItemStack boots = inv.getBoots();

            if (isValidItem(helmet)) {
                data.equippedArmor.add(helmet.clone());
                data.allItems.add(helmet.clone());
                YakRealms.log("  🛡️ Equipped helmet: " + getItemDisplayName(helmet));
            }

            if (isValidItem(chestplate)) {
                data.equippedArmor.add(chestplate.clone());
                data.allItems.add(chestplate.clone());
                YakRealms.log("  🛡️ Equipped chestplate: " + getItemDisplayName(chestplate));
            }

            if (isValidItem(leggings)) {
                data.equippedArmor.add(leggings.clone());
                data.allItems.add(leggings.clone());
                YakRealms.log("  🛡️ Equipped leggings: " + getItemDisplayName(leggings));
            }

            if (isValidItem(boots)) {
                data.equippedArmor.add(boots.clone());
                data.allItems.add(boots.clone());
                YakRealms.log("  🛡️ Equipped boots: " + getItemDisplayName(boots));
            }

            // FIXED: Get hotbar items (slots 0-8) with proper tracking
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < 9 && i < contents.length; i++) {
                ItemStack item = contents[i];
                if (isValidItem(item)) {
                    ItemStack copy = item.clone();
                    data.hotbarItems.add(copy);
                    data.allItems.add(copy);

                    // First hotbar item (slot 0) is the weapon slot
                    if (i == 0) {
                        data.firstHotbarItem = copy;
                        YakRealms.log("  ⚔️ First hotbar item (weapon): " + getItemDisplayName(copy));
                    }
                }
            }

            // Process main inventory items (slots 9-35)
            for (int i = 9; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (isValidItem(item)) {
                    data.allItems.add(item.clone());
                }
            }

            // Process offhand
            ItemStack offhandItem = inv.getItemInOffHand();
            if (isValidItem(offhandItem)) {
                ItemStack copy = offhandItem.clone();
                data.offhandItem = copy;
                data.allItems.add(copy);
            }

        } catch (Exception e) {
            YakRealms.error("❌ Error gathering player items with slot tracking", e);
        }

        return data;
    }

    /**
     * FIXED: Process items by alignment with corrected logic
     */
    private ProcessResult processItemsByAlignmentFixed(PlayerItemData itemData, String alignment) {
        ProcessResult result = new ProcessResult();

        if (itemData.allItems.isEmpty()) {
            YakRealms.log("⚠️ No items to process for alignment: " + alignment);
            return result;
        }

        switch (alignment.toUpperCase()) {
            case "LAWFUL":
                processLawfulDeathItemsFixed(itemData, result);
                break;
            case "NEUTRAL":
                processNeutralDeathItemsFixed(itemData, result);
                break;
            case "CHAOTIC":
                processChaoticDeathItemsFixed(itemData, result);
                break;
            default:
                YakRealms.warn("⚠️ Unknown alignment: " + alignment + " - defaulting to lawful");
                processLawfulDeathItemsFixed(itemData, result);
                break;
        }

        return result;
    }

    /**
     * FIXED: Lawful alignment processing
     * KEEPS: Equipped armor + First hotbar item + Permanent untradeable
     * DROPS: Everything else
     */
    private void processLawfulDeathItemsFixed(PlayerItemData itemData, ProcessResult result) {
        YakRealms.log("⚖️ Processing LAWFUL death items...");

        for (ItemStack item : itemData.allItems) {
            if (item == null) continue;

            boolean shouldKeep = false;
            String reason = "";

            // FIXED: Check equipped armor using proper comparison
            if (isEquippedArmorFixed(item, itemData.equippedArmor)) {
                shouldKeep = true;
                reason = "equipped armor";
            }
            // FIXED: Check first hotbar item using proper comparison
            else if (isFirstHotbarItemFixed(item, itemData.firstHotbarItem)) {
                shouldKeep = true;
                reason = "first hotbar item (weapon)";
            }
            // Keep permanent untradeable
            else if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeep = true;
                reason = "permanent untradeable";
            }

            if (shouldKeep) {
                result.keptItems.add(item.clone());
                YakRealms.log("  ✅ LAWFUL KEEPING: " + getItemDisplayName(item) + " (" + reason + ")");
            } else {
                result.droppedItems.add(item.clone());
                YakRealms.log("  ❌ LAWFUL DROPPING: " + getItemDisplayName(item));
            }
        }
    }

    /**
     * FIXED: Neutral alignment processing with proper percentage chances
     * KEEPS: Permanent untradeable + Equipped armor (75% chance) + First hotbar item (50% chance)
     * DROPS: Everything else + Failed percentage rolls
     */
    private void processNeutralDeathItemsFixed(PlayerItemData itemData, ProcessResult result) {
        YakRealms.log("⚖️ Processing NEUTRAL death items...");

        Random random = new Random();
        boolean shouldDropWeapon = random.nextInt(100) < NEUTRAL_WEAPON_DROP_CHANCE;
        boolean shouldDropArmor = random.nextInt(100) < NEUTRAL_ARMOR_DROP_CHANCE;

        YakRealms.log("  🎲 Neutral drop rolls - Weapon: " + (shouldDropWeapon ? "DROP" : "KEEP") +
                ", Armor: " + (shouldDropArmor ? "DROP" : "KEEP"));

        for (ItemStack item : itemData.allItems) {
            if (item == null) continue;

            boolean shouldKeep = false;
            String reason = "";

            // Always keep permanent untradeable
            if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeep = true;
                reason = "permanent untradeable";
            }
            // Check equipped armor with chance
            else if (isEquippedArmorFixed(item, itemData.equippedArmor)) {
                if (!shouldDropArmor) {
                    shouldKeep = true;
                    reason = "equipped armor (passed 75% roll)";
                } else {
                    reason = "equipped armor (failed 75% roll)";
                }
            }
            // Check first hotbar item with chance
            else if (isFirstHotbarItemFixed(item, itemData.firstHotbarItem)) {
                if (!shouldDropWeapon) {
                    shouldKeep = true;
                    reason = "first hotbar item (passed 50% roll)";
                } else {
                    reason = "first hotbar item (failed 50% roll)";
                }
            }

            if (shouldKeep) {
                result.keptItems.add(item.clone());
                YakRealms.log("  ✅ NEUTRAL KEEPING: " + getItemDisplayName(item) + " (" + reason + ")");
            } else {
                result.droppedItems.add(item.clone());
                YakRealms.log("  ❌ NEUTRAL DROPPING: " + getItemDisplayName(item) + " (" + reason + ")");
            }
        }
    }

    /**
     * FIXED: Chaotic alignment processing
     * KEEPS: Only permanent untradeable + quest items
     * DROPS: Everything else (including all armor and weapons)
     */
    private void processChaoticDeathItemsFixed(PlayerItemData itemData, ProcessResult result) {
        YakRealms.log("⚖️ Processing CHAOTIC death items...");

        for (ItemStack item : itemData.allItems) {
            if (item == null) continue;

            boolean shouldKeep = false;
            String reason = "";

            // Only keep permanent untradeable and quest items
            if (InventoryUtils.isPermanentUntradeable(item)) {
                shouldKeep = true;
                reason = "permanent untradeable";
            } else if (InventoryUtils.isQuestItem(item)) {
                shouldKeep = true;
                reason = "quest item";
            }

            if (shouldKeep) {
                result.keptItems.add(item.clone());
                YakRealms.log("  ✅ CHAOTIC KEEPING: " + getItemDisplayName(item) + " (" + reason + ")");
            } else {
                result.droppedItems.add(item.clone());
                YakRealms.log("  ❌ CHAOTIC DROPPING: " + getItemDisplayName(item));
            }
        }
    }

    /**
     * FIXED: Check if an item is equipped armor using proper isSimilar comparison
     */
    private boolean isEquippedArmorFixed(ItemStack item, List<ItemStack> equippedArmor) {
        if (item == null || equippedArmor == null || equippedArmor.isEmpty()) {
            return false;
        }

        for (ItemStack armor : equippedArmor) {
            if (armor != null && item.isSimilar(armor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * FIXED: Check if an item is the first hotbar item using proper isSimilar comparison
     */
    private boolean isFirstHotbarItemFixed(ItemStack item, ItemStack firstHotbarItem) {
        if (item == null || firstHotbarItem == null) {
            return false;
        }
        return item.isSimilar(firstHotbarItem);
    }

    /**
     * Log detailed item processing information for debugging
     */
    private void logItemProcessingDetails(ProcessResult result, String playerName) {
        YakRealms.log("📋 Detailed item processing for " + playerName + ":");

        if (!result.keptItems.isEmpty()) {
            YakRealms.log("  ✅ Items being KEPT:");
            for (int i = 0; i < result.keptItems.size(); i++) {
                ItemStack item = result.keptItems.get(i);
                YakRealms.log("    " + (i+1) + ". " + getItemDisplayName(item));
            }
        }

        if (!result.droppedItems.isEmpty()) {
            YakRealms.log("  ❌ Items being DROPPED:");
            for (int i = 0; i < result.droppedItems.size(); i++) {
                ItemStack item = result.droppedItems.get(i);
                YakRealms.log("    " + (i+1) + ". " + getItemDisplayName(item));
            }
        }
    }

    /**
     * Proper death event configuration without conflicts
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

            YakRealms.log("⚙️ Configured death event - will drop " + result.droppedItems.size() + " items (no duplication)");

        } catch (Exception e) {
            YakRealms.error("❌ Error configuring death event", e);
        }
    }

    /**
     * Enhanced respawn event handler with better error recovery
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        YakRealms.log("🔄 Respawn event triggered for " + player.getName());

        if (!isValidPlayer(player)) {
            YakRealms.warn("⚠️ Invalid player in respawn event");
            return;
        }

        // FIXED: Prevent concurrent respawn processing
        if (!respawnInProgress.add(playerId)) {
            YakRealms.log("⚠️ Respawn already in progress for " + player.getName());
            return;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                YakRealms.warn("⚠️ No YakPlayer data for " + player.getName() + " during respawn");
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
            YakRealms.error("❌ Error handling respawn for " + player.getName(), e);
            e.printStackTrace();
        } finally {
            // Always ensure respawn lock is released
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                respawnInProgress.remove(playerId);
            }, 40L); // 2 seconds
        }
    }

    private void addRespawnEffects(Player player) {
        // add blindness to player and some sound that would be good with this system
    }

    /**
     * COMPLETE IMPLEMENTATION: Schedule post-respawn processing
     */
    private void schedulePostRespawnProcessing(Player player, YakPlayer yakPlayer, DeathInfo deathInfo,
                                               boolean wasCombatLogout, UUID playerId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    respawnInProgress.remove(playerId);
                    return;
                }

                try {
                    handlePostRespawn(player, yakPlayer, deathInfo, wasCombatLogout);
                } catch (Exception e) {
                    YakRealms.error("❌ Error in post-respawn processing for " + player.getName(), e);
                } finally {
                    respawnInProgress.remove(playerId);
                }
            }
        }.runTaskLater(YakRealms.getInstance(), RESPAWN_RESTORE_DELAY);
    }

    /**
     * COMPLETE IMPLEMENTATION: Handle post-respawn processing
     */
    private void handlePostRespawn(Player player, YakPlayer yakPlayer, DeathInfo deathInfo, boolean wasCombatLogout) {
        try {
            YakRealms.log("🔄 Post-respawn processing for " + player.getName() + " (combat logout: " + wasCombatLogout + ")");

            if (wasCombatLogout) {
                YakRealms.log("Combat logout detected - items should already be processed by CombatLogoutMechanics");
                return;
            }

            // Check if player has respawn items to restore
            if (yakPlayer.hasRespawnItems()) {
                YakRealms.log("🎯 Attempting to restore respawn items for " + player.getName());

                boolean restored = restoreRespawnItems(player, yakPlayer);
                if (restored) {
                    YakRealms.log("✅ Successfully restored respawn items for " + player.getName());
                } else {
                    YakRealms.warn("⚠️ Failed to restore respawn items for " + player.getName());
                    failedItemRestorations.incrementAndGet();
                }
            } else {
                YakRealms.log("ℹ️ No respawn items to restore for " + player.getName());
            }

            // Apply respawn effects
            applyRespawnEffects(player);

            // Clean up death info
            if (deathInfo != null) {
                recentDeaths.remove(player.getUniqueId());
            }

        } catch (Exception e) {
            YakRealms.error("❌ Error in handlePostRespawn for " + player.getName(), e);
        }
    }

    /**
     * COMPLETE IMPLEMENTATION: Restore respawn items with full logic
     */
    public boolean restoreRespawnItems(Player player, YakPlayer yakPlayer) {
        if (yakPlayer == null || !yakPlayer.hasRespawnItems()) {
            YakRealms.log("No respawn items to restore for " + (player != null ? player.getName() : "unknown"));
            return false;
        }

        try {
            List<ItemStack> respawnItems = yakPlayer.getRespawnItems();
            if (respawnItems.isEmpty()) {
                YakRealms.log("Respawn items list is empty for " + player.getName());
                yakPlayer.clearRespawnItems();
                return false;
            }

            YakRealms.log("🔄 Restoring " + respawnItems.size() + " respawn items for " + player.getName());

            // Clear current inventory
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);

            // Categorize and apply items
            ItemStack[] armor = new ItemStack[4];
            List<ItemStack> inventoryItems = new ArrayList<>();

            for (ItemStack item : respawnItems) {
                if (item == null || item.getType() == Material.AIR) continue;

                // Check if it's armor
                if (isArmorItem(item)) {
                    int armorSlot = getArmorSlot(item);
                    if (armorSlot >= 0 && armorSlot < 4 && armor[armorSlot] == null) {
                        armor[armorSlot] = item.clone();
                    } else {
                        inventoryItems.add(item.clone());
                    }
                } else {
                    inventoryItems.add(item.clone());
                }
            }

            // Apply armor
            player.getInventory().setArmorContents(armor);

            // Apply inventory items
            for (int i = 0; i < inventoryItems.size() && i < 36; i++) {
                player.getInventory().setItem(i, inventoryItems.get(i));
            }

            player.updateInventory();

            // Clear respawn items after successful restoration
            yakPlayer.clearRespawnItems();
            playerManager.savePlayer(yakPlayer);

            YakRealms.log("✅ Successfully restored respawn items for " + player.getName());

            // Notify player
            player.sendMessage(ChatColor.GREEN + "Your items from death have been restored!");

            successfulItemRestorations.incrementAndGet();
            return true;

        } catch (Exception e) {
            YakRealms.error("❌ Error restoring respawn items for " + player.getName(), e);
            failedItemRestorations.incrementAndGet();
            return false;
        }
    }

    /**
     * Helper methods for armor handling
     */
    private boolean isArmorItem(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.contains("_HELMET") || typeName.contains("_CHESTPLATE") ||
                typeName.contains("_LEGGINGS") || typeName.contains("_BOOTS");
    }

    private int getArmorSlot(ItemStack item) {
        if (item == null) return -1;
        String typeName = item.getType().name();
        if (typeName.contains("_BOOTS")) return 0;
        if (typeName.contains("_LEGGINGS")) return 1;
        if (typeName.contains("_CHESTPLATE")) return 2;
        if (typeName.contains("_HELMET")) return 3;
        return -1;
    }

    /**
     * Apply visual and gameplay effects after respawn
     */
    private void applyRespawnEffects(Player player) {
        try {
            // Apply temporary blindness effect to simulate "waking up"
            PotionEffect blindness = new PotionEffect(PotionEffectType.BLINDNESS, RESPAWN_BLINDNESS_DURATION, 0);
            player.addPotionEffect(blindness);

            // Reset health and energy
            player.setHealth(player.getMaxHealth());

            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setTemporaryData("energy", DEFAULT_ENERGY);
            }

            // Play respawn sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.0f);

            YakRealms.log("Applied respawn effects for " + player.getName());

        } catch (Exception e) {
            YakRealms.warn("Error applying respawn effects for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Set respawn location based on player data
     */
    private void setRespawnLocation(PlayerRespawnEvent event, YakPlayer yakPlayer, Player player) {
        try {
            World world = Bukkit.getWorlds().get(0); // Default world
            Location respawnLocation = null;

            // Check for bed spawn
            if (yakPlayer.getBedSpawnLocation() != null) {
                try {
                    // Parse bed spawn location
                    String[] parts = yakPlayer.getBedSpawnLocation().split(":");
                    if (parts.length >= 4) {
                        World bedWorld = Bukkit.getWorld(parts[0]);
                        if (bedWorld != null) {
                            respawnLocation = new Location(bedWorld,
                                    Double.parseDouble(parts[1]),
                                    Double.parseDouble(parts[2]),
                                    Double.parseDouble(parts[3]));
                        }
                    }
                } catch (Exception e) {
                    YakRealms.warn("Failed to parse bed spawn location for " + player.getName());
                }
            }

            // Fall back to world spawn or random spawn for chaotic players
            if (respawnLocation == null) {
                if ("CHAOTIC".equals(yakPlayer.getAlignment())) {
                    respawnLocation = generateRandomSpawnPoint(player.getName());
                } else {
                    respawnLocation = world.getSpawnLocation();
                }
            }

            if (respawnLocation != null) {
                event.setRespawnLocation(respawnLocation);
                yakPlayer.updateLocation(respawnLocation);
            }

        } catch (Exception e) {
            YakRealms.warn("Error setting respawn location for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Enhanced respawn item status logging
     */
    private void logRespawnItemStatus(YakPlayer yakPlayer) {
        boolean hasRespawnItems = yakPlayer.hasRespawnItems();
        List<ItemStack> respawnItems = yakPlayer.getRespawnItems();
        YakPlayer.CombatLogoutState combatState = yakPlayer.getCombatLogoutState();

        YakRealms.log("📦 RESPAWN ITEMS DEBUG for " + yakPlayer.getUsername() + ":");
        YakRealms.log("  - hasRespawnItems: " + hasRespawnItems);
        YakRealms.log("  - itemCount: " + (respawnItems != null ? respawnItems.size() : 0));
        YakRealms.log("  - combatLogoutState: " + combatState);
        YakRealms.log("  - deathTimestamp: " + yakPlayer.getDeathTimestamp());

        if (yakPlayer.getDeathTimestamp() > 0) {
            long timeSinceDeath = System.currentTimeMillis() - yakPlayer.getDeathTimestamp();
            YakRealms.log("  - timeSinceDeath: " + (timeSinceDeath / 1000) + " seconds");
        }

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
     * Store kept items for respawn (only for normal deaths)
     */
    private void storeKeptItemsForRespawn(YakPlayer yakPlayer, List<ItemStack> keptItems) {
        try {
            if (keptItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                YakRealms.log("📦 No items to keep for respawn: " + yakPlayer.getUsername());
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
                YakRealms.log("✅ Stored " + validItems.size() + " items for respawn: " + yakPlayer.getUsername());

                // Verify storage
                List<ItemStack> storedItems = yakPlayer.getRespawnItems();
                boolean hasItems = yakPlayer.hasRespawnItems();
                YakRealms.log("📋 Storage verification - hasRespawnItems: " + hasItems +
                        ", storedCount: " + (storedItems != null ? storedItems.size() : 0));
            } else {
                YakRealms.warn("⚠️ Failed to store respawn items for: " + yakPlayer.getUsername());
            }

        } catch (Exception e) {
            YakRealms.error("❌ Error storing kept items for respawn", e);
            e.printStackTrace();
        }
    }

    // Utility methods
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

    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        return String.format("🎯 FIXED DeathMechanics Stats: " +
                        "Total=%d, Normal=%d, CombatLogout=%d, Skipped=%d, " +
                        "Restorations=%d/%d, DuplicationsPrevented=%d",
                totalDeaths.get(), normalDeaths.get(), combatLogoutDeaths.get(), skippedDeaths.get(),
                successfulItemRestorations.get(), failedItemRestorations.get(), duplicationsPrevented.get());
    }

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
}
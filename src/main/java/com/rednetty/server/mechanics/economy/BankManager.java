package com.rednetty.server.mechanics.economy;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
import com.rednetty.server.utils.text.TextUtil;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.sound.Sound;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Enhanced Bank Manager with bulletproof persistence and cache management
 * FIXED: Bank inventory persistence through restarts and coordination with YakPlayerManager
 *
 * Key Improvements:
 * - CRITICAL FIX: Enhanced save coordination with YakPlayerManager
 * - Improved cache invalidation and cleanup
 * - Better error handling and recovery
 * - Enhanced dirty page tracking with atomic operations
 * - Bulletproof shutdown process
 * - Bank data validation and corruption recovery
 */
public class BankManager implements Listener {
    // Constants
    public static final int BANK_SIZE = 54;
    public static final int BANK_CONTENT_SIZE = BANK_SIZE - 9; // Usable area excluding bottom row
    public static final String BANK_TITLE_PREFIX = "Bank Chest (";
    public static final String BANK_TITLE_SUFFIX = ")";
    public static final String COLLECTION_BIN_TITLE = "Collection Bin";
    public static final int MAX_BANK_PAGES = 10;
    public static final int BASE_BANK_PAGES = 1;

    private static BankManager instance;
    private final Logger logger;

    // Adventure API serializer for backwards compatibility
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    // Cache for bank inventories and collection bins with enhanced management
    private final Map<UUID, Map<Integer, Inventory>> playerBanks = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> collectionBins = new ConcurrentHashMap<>();

    // Players in withdraw prompts and upgrade prompts
    private final Set<UUID> withdrawPrompt = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> upgradePrompt = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Player viewing another player's bank
    private final Map<UUID, UUID> bankViewMap = new ConcurrentHashMap<>();

    // Enhanced dirty tracking with atomic operations
    private final Map<UUID, Set<Integer>> dirtyBankPages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSaveTimestamp = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> currentlySaving = new ConcurrentHashMap<>();

    // Performance and error tracking
    private final AtomicInteger totalBankOpens = new AtomicInteger(0);
    private final AtomicInteger totalBankSaves = new AtomicInteger(0);
    private final AtomicInteger successfulBankSaves = new AtomicInteger(0);
    private final AtomicInteger failedBankSaves = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger bankDataCorruptions = new AtomicInteger(0);
    private final AtomicInteger bankDataRecoveries = new AtomicInteger(0);

    // System state
    private volatile boolean shutdownInProgress = false;

    // Bank upgrade tiers and costs
    public enum BankUpgradeTier {
        TIER_1(1, 0),       // Base tier
        TIER_2(2, 7500),      // 2 pages for 50 gems
        TIER_3(3, 10000),     // 3 pages for 125 gems
        TIER_4(4, 15000),     // 4 pages for 500 gems
        TIER_5(5, 20000),    // 5 pages for 1500 gems
        TIER_6(6, 25000),    // 6 pages for 3500 gems
        TIER_7(7, 30000),    // 7 pages for 7500 gems
        TIER_8(8, 35000),   // 8 pages for 15000 gems
        TIER_9(9, 45000),   // 9 pages for 30000 gems
        TIER_10(10, 75000); // 10 pages for 50000 gems

        private final int pages;
        private final int cost;

        BankUpgradeTier(int pages, int cost) {
            this.pages = pages;
            this.cost = cost;
        }

        public int getPages() { return pages; }
        public int getCost() { return cost; }

        public static BankUpgradeTier getTier(int pages) {
            for (BankUpgradeTier tier : values()) {
                if (tier.pages == pages) {
                    return tier;
                }
            }
            return TIER_1;
        }

        public BankUpgradeTier getNext() {
            BankUpgradeTier[] tiers = values();
            int nextIndex = ordinal() + 1;
            return nextIndex < tiers.length ? tiers[nextIndex] : this;
        }

        public boolean hasNext() {
            return ordinal() < values().length - 1;
        }
    }

    /**
     * Private constructor for singleton pattern
     */
    private BankManager() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Gets the singleton instance
     */
    public static BankManager getInstance() {
        if (instance == null) {
            instance = new BankManager();
        }
        return instance;
    }

    /**
     * Initialize the enhanced bank system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start auto-save task for bank inventories with enhanced coordination
        Bukkit.getScheduler().runTaskTimerAsynchronously(YakRealms.getInstance(), this::autoSaveBanks,
                20L * 60 * 2, // 2 minutes initial delay
                20L * 60 * 5  // 5 minutes interval
        );

        // Start cache cleanup task
        Bukkit.getScheduler().runTaskTimerAsynchronously(YakRealms.getInstance(), this::cleanupStaleCache,
                20L * 60 * 10, // 10 minutes initial delay
                20L * 60 * 10  // 10 minutes interval
        );

        logger.info("✅ Enhanced Bank system enabled with bulletproof persistence");
    }

    /**
     * Enhanced shutdown with guaranteed saves
     */
    public void onDisable() {
        shutdownInProgress = true;
        logger.info("Starting enhanced bank system shutdown with guaranteed saves...");

        try {
            // Force save all banks immediately
            saveBanksImmediate();
            logger.info("✅ Enhanced Bank system shutdown completed");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during bank system shutdown", e);
        }
    }

    /**
     * Enhanced auto-save with better coordination
     */
    private void autoSaveBanks() {
        if (shutdownInProgress) {
            return;
        }

        try {
            logger.fine("Starting enhanced auto-save of bank inventories...");
            int savedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (Map.Entry<UUID, Set<Integer>> entry : dirtyBankPages.entrySet()) {
                UUID playerUuid = entry.getKey();
                Set<Integer> dirtyPages = entry.getValue();

                if (dirtyPages.isEmpty()) {
                    continue;
                }

                // Check if YakPlayerManager is coordinating this player
                YakPlayerManager playerManager = YakPlayerManager.getInstance();
                if (playerManager != null && playerManager.isPlayerProtected(playerUuid)) {
                    logger.fine("Skipping auto-save for protected player: " + playerUuid);
                    skippedCount++;
                    continue;
                }

                Map<Integer, Inventory> playerBankPages = playerBanks.get(playerUuid);
                if (playerBankPages != null) {
                    Set<Integer> pagesToSave = new HashSet<>(dirtyPages);

                    for (Integer page : pagesToSave) {
                        try {
                            Inventory bankInv = playerBankPages.get(page);
                            if (bankInv != null) {
                                if (saveBankWithCoordination(bankInv, playerUuid, page, "auto_save")) {
                                    savedCount++;
                                    dirtyPages.remove(page);
                                } else {
                                    errorCount++;
                                }
                            }
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error auto-saving bank page " + page + " for player " + playerUuid, e);
                            errorCount++;
                        }
                    }
                }
            }

            // Save collection bins
            for (Map.Entry<UUID, Inventory> entry : collectionBins.entrySet()) {
                try {
                    if (saveCollectionBinWithCoordination(entry.getValue(), entry.getKey(), "auto_save")) {
                        savedCount++;
                    } else {
                        errorCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error auto-saving collection bin for player " + entry.getKey(), e);
                    errorCount++;
                }
            }

            if (savedCount > 0 || errorCount > 0) {
                logger.info("Enhanced auto-save completed: " + savedCount + " saved, " + skippedCount + " skipped, " + errorCount + " errors");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during enhanced auto-save of banks", e);
        }
    }

    /**
     * Clean up stale cache entries
     */
    private void cleanupStaleCache() {
        if (shutdownInProgress) {
            return;
        }

        try {
            long currentTime = System.currentTimeMillis();
            int cleanedPlayers = 0;

            Iterator<Map.Entry<UUID, Map<Integer, Inventory>>> iterator = playerBanks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Map<Integer, Inventory>> entry = iterator.next();
                UUID playerUuid = entry.getKey();

                // Check if player is online
                Player player = Bukkit.getPlayer(playerUuid);
                if (player == null || !player.isOnline()) {
                    // Check last save timestamp
                    Long lastSave = lastSaveTimestamp.get(playerUuid);
                    if (lastSave == null || (currentTime - lastSave) > 600000) { // 10 minutes
                        // Force save before cleanup
                        try {
                            Map<Integer, Inventory> bankPages = entry.getValue();
                            for (Map.Entry<Integer, Inventory> pageEntry : bankPages.entrySet()) {
                                saveBankWithCoordination(pageEntry.getValue(), playerUuid, pageEntry.getKey(), "cache_cleanup");
                            }
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error saving bank during cache cleanup for " + playerUuid, e);
                        }

                        // Clean up cache
                        iterator.remove();
                        collectionBins.remove(playerUuid);
                        dirtyBankPages.remove(playerUuid);
                        lastSaveTimestamp.remove(playerUuid);
                        currentlySaving.remove(playerUuid);
                        bankViewMap.remove(playerUuid);
                        cleanedPlayers++;
                    }
                }
            }

            if (cleanedPlayers > 0) {
                logger.fine("Cache cleanup completed: " + cleanedPlayers + " offline players cleaned");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during cache cleanup", e);
        }
    }

    /**
     * Immediate save all banks (for shutdown)
     */
    private void saveBanksImmediate() {
        try {
            logger.info("Starting immediate save of " + playerBanks.size() + " player banks...");
            int savedCount = 0;
            int errorCount = 0;

            for (Map.Entry<UUID, Map<Integer, Inventory>> entry : playerBanks.entrySet()) {
                UUID playerUuid = entry.getKey();
                Map<Integer, Inventory> banks = entry.getValue();

                for (Map.Entry<Integer, Inventory> bankEntry : banks.entrySet()) {
                    try {
                        if (saveBankWithCoordination(bankEntry.getValue(), playerUuid, bankEntry.getKey(), "shutdown")) {
                            savedCount++;
                        } else {
                            errorCount++;
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Error saving bank during shutdown for " + playerUuid + " page " + bankEntry.getKey(), e);
                        errorCount++;
                    }
                }
            }

            // Save collection bins
            for (Map.Entry<UUID, Inventory> entry : collectionBins.entrySet()) {
                try {
                    if (saveCollectionBinWithCoordination(entry.getValue(), entry.getKey(), "shutdown")) {
                        savedCount++;
                    } else {
                        errorCount++;
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error saving collection bin during shutdown for " + entry.getKey(), e);
                    errorCount++;
                }
            }

            // Clear cache after saving
            playerBanks.clear();
            collectionBins.clear();
            dirtyBankPages.clear();
            lastSaveTimestamp.clear();
            currentlySaving.clear();
            bankViewMap.clear();

            logger.info("✅ Immediate shutdown save completed: " + savedCount + " saved, " + errorCount + " errors");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during immediate bank save", e);
        }
    }

    /**
     * Enhanced bank saving with coordination
     */
    private boolean saveBankWithCoordination(Inventory inventory, UUID playerUuid, int page, String context) {
        try {
            // Check if already saving this page
            Set<Integer> currentSaving = currentlySaving.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
            if (currentSaving.contains(page)) {
                logger.fine("Bank page " + page + " already being saved for player " + playerUuid);
                return false;
            }

            currentSaving.add(page);

            try {
                totalBankSaves.incrementAndGet();

                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);
                if (yakPlayer == null) {
                    logger.warning("Cannot save bank - YakPlayer not found for UUID: " + playerUuid);
                    failedBankSaves.incrementAndGet();
                    return false;
                }

                // Enhanced serialization with validation
                String serializedItems = serializeInventoryWithValidation(inventory, playerUuid, page);
                if (serializedItems == null) {
                    logger.warning("Failed to serialize bank inventory for player " + yakPlayer.getUsername() + " page " + page);
                    failedBankSaves.incrementAndGet();
                    return false;
                }

                // CRITICAL: Save to YakPlayer with enhanced coordination
                yakPlayer.setSerializedBankItems(page, serializedItems);

                // Update bank balance from UI item
                updateBankBalanceFromUI(inventory, yakPlayer);

                // Coordinate with YakPlayerManager for immediate save
                YakPlayerManager playerManager = YakPlayerManager.getInstance();
                if (playerManager != null) {
                    // Use YakPlayerManager's coordinated save
                    boolean saveResult = playerManager.savePlayer(yakPlayer).get();
                    if (saveResult) {
                        successfulBankSaves.incrementAndGet();
                        lastSaveTimestamp.put(playerUuid, System.currentTimeMillis());
                        logger.fine("✅ Enhanced bank save successful for " + yakPlayer.getUsername() + " page " + page + " (" + context + ")");
                        return true;
                    } else {
                        logger.warning("YakPlayerManager save failed for " + yakPlayer.getUsername() + " page " + page);
                        failedBankSaves.incrementAndGet();
                        return false;
                    }
                } else {
                    logger.severe("YakPlayerManager not available for bank save!");
                    failedBankSaves.incrementAndGet();
                    return false;
                }

            } finally {
                currentSaving.remove(page);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in enhanced bank save for player " + playerUuid + " page " + page, e);
            failedBankSaves.incrementAndGet();
            return false;
        }
    }

    /**
     * Enhanced collection bin saving with coordination
     */
    private boolean saveCollectionBinWithCoordination(Inventory inventory, UUID playerUuid, String context) {
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);
            if (yakPlayer == null) {
                logger.warning("Cannot save collection bin - YakPlayer not found for UUID: " + playerUuid);
                return false;
            }

            String serializedItems = serializeInventoryWithValidation(inventory, playerUuid, -1);
            if (serializedItems != null) {
                yakPlayer.setSerializedCollectionBin(serializedItems);

                // Coordinate with YakPlayerManager
                YakPlayerManager playerManager = YakPlayerManager.getInstance();
                if (playerManager != null) {
                    boolean saveResult = playerManager.savePlayer(yakPlayer).get();
                    if (saveResult) {
                        logger.fine("✅ Enhanced collection bin save successful for " + yakPlayer.getUsername() + " (" + context + ")");
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving collection bin for player " + playerUuid, e);
            return false;
        }
    }

    /**
     * Enhanced inventory serialization with validation and corruption detection
     */
    private String serializeInventoryWithValidation(Inventory inventory, UUID playerUuid, int page) {
        try {
            if (inventory == null) {
                logger.warning("Cannot serialize null inventory for player " + playerUuid + " page " + page);
                return null;
            }

            // Validate inventory contents before serialization
            int validItemCount = 0;
            int corruptedItemCount = 0;

            for (int i = 0; i < BANK_CONTENT_SIZE; i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != Material.AIR) {
                    try {
                        // Test item validity
                        item.clone();
                        validItemCount++;
                    } catch (Exception e) {
                        logger.warning("Corrupted item detected in bank for player " + playerUuid + " page " + page + " slot " + i);
                        corruptedItemCount++;
                        inventory.setItem(i, null); // Remove corrupted item
                    }
                }
            }

            if (corruptedItemCount > 0) {
                bankDataCorruptions.incrementAndGet();
                logger.warning("Removed " + corruptedItemCount + " corrupted items from bank for player " + playerUuid + " page " + page);
            }

            // Perform serialization with multiple attempts
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String result = serializeInventory(inventory);
                    if (result != null && !result.trim().isEmpty()) {
                        logger.fine("Serialized bank page " + page + " for player " + playerUuid + " (" + validItemCount + " items, attempt " + attempt + ")");
                        return result;
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Serialization attempt " + attempt + " failed for player " + playerUuid + " page " + page, e);
                    if (attempt == 3) {
                        throw e;
                    }
                    // Brief pause before retry
                    Thread.sleep(50);
                }
            }

            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Enhanced serialization failed for player " + playerUuid + " page " + page, e);
            return null;
        }
    }

    /**
     * Update bank balance from UI item
     */
    private void updateBankBalanceFromUI(Inventory inventory, YakPlayer yakPlayer) {
        try {
            ItemStack balanceItem = inventory.getItem(BANK_SIZE - 5);
            if (balanceItem != null && balanceItem.getType() == Material.EMERALD) {
                ItemMeta meta = balanceItem.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    Component displayName = meta.displayName();
                    if (displayName != null) {
                        String plainText = LEGACY_SERIALIZER.serialize(displayName);
                        String balanceStr = ChatColor.stripColor(plainText).split(" ")[0];
                        int bankBalance = Integer.parseInt(balanceStr);
                        yakPlayer.setBankGems(bankBalance);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing bank balance from item", e);
        }
    }

    /**
     * Mark a bank page as dirty (needs saving) with atomic operations
     */
    private void markBankDirty(UUID playerUuid, int page) {
        if (playerUuid == null || page < 1 || page > MAX_BANK_PAGES) {
            return;
        }

        dirtyBankPages.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(page);
        logger.fine("Marked bank page " + page + " as dirty for player " + playerUuid);
    }

    /**
     * Enhanced bank inventory retrieval with improved caching and validation
     */
    public Inventory getBank(Player player, int page) {
        if (player == null || page < 1) {
            logger.warning("Invalid bank access request: player=" + (player != null ? player.getName() : "null") + ", page=" + page);
            return null;
        }

        UUID viewerUuid = player.getUniqueId();
        UUID targetUuid = bankViewMap.getOrDefault(viewerUuid, viewerUuid);

        totalBankOpens.incrementAndGet();

        // Validate page number with YakPlayer data
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(targetUuid);
        if (yakPlayer == null) {
            logger.warning("No YakPlayer found for bank access: " + targetUuid);
            return null;
        }

        int maxPages = yakPlayer.getBankPages();
        if (page > maxPages) {
            logger.warning("Invalid bank page " + page + " for player " + yakPlayer.getUsername() + " (max: " + maxPages + ")");
            return null;
        }

        // Get or create page map for this player
        Map<Integer, Inventory> playerBankPages = playerBanks.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());

        // Check cache first
        Inventory cachedBank = playerBankPages.get(page);
        if (cachedBank != null) {
            cacheHits.incrementAndGet();
            // Update UI before returning
            updateBankUI(cachedBank, player, page, maxPages);
            logger.fine("Cache hit for bank page " + page + " for player " + yakPlayer.getUsername());
            return cachedBank;
        }

        cacheMisses.incrementAndGet();

        // Create new bank inventory
        String title = BANK_TITLE_PREFIX + page + "/" + maxPages + BANK_TITLE_SUFFIX;
        Inventory bankInv = Bukkit.createInventory(null, BANK_SIZE, title);

        // Load bank contents from YakPlayer with enhanced error handling
        boolean dataLoaded = false;
        try {
            String serializedItems = yakPlayer.getSerializedBankItems(page);
            if (serializedItems != null && !serializedItems.isEmpty()) {
                if (deserializeInventoryWithValidation(serializedItems, bankInv, targetUuid, page)) {
                    dataLoaded = true;
                    logger.fine("✅ Loaded bank data for page " + page + " for player " + yakPlayer.getUsername());
                } else {
                    logger.warning("Failed to deserialize bank data for page " + page + " for player " + yakPlayer.getUsername());
                    bankDataCorruptions.incrementAndGet();
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading bank data for page " + page + " for player " + yakPlayer.getUsername(), e);
            bankDataCorruptions.incrementAndGet();
        }

        // Initialize UI elements
        initializeBankInventory(bankInv, player, page, maxPages);

        // Cache the inventory
        playerBankPages.put(page, bankInv);

        if (dataLoaded) {
            logger.fine("Bank page " + page + " loaded and cached for player " + yakPlayer.getUsername());
        } else {
            logger.fine("Empty bank page " + page + " created and cached for player " + yakPlayer.getUsername());
        }

        return bankInv;
    }

    /**
     * Enhanced inventory deserialization with validation and corruption recovery
     */
    private boolean deserializeInventoryWithValidation(String data, Inventory inventory, UUID playerUuid, int page) {
        try {
            if (data == null || data.trim().isEmpty()) {
                return true; // Empty data is valid
            }

            // Multiple deserialization attempts
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    deserializeInventory(data, inventory);

                    // Validate loaded items
                    int validItems = 0;
                    int corruptedItems = 0;

                    for (int i = 0; i < BANK_CONTENT_SIZE; i++) {
                        ItemStack item = inventory.getItem(i);
                        if (item != null && item.getType() != Material.AIR) {
                            try {
                                // Test item validity
                                item.clone();
                                validItems++;
                            } catch (Exception e) {
                                logger.warning("Corrupted item found after deserialization for player " + playerUuid + " page " + page + " slot " + i);
                                inventory.setItem(i, null);
                                corruptedItems++;
                            }
                        }
                    }

                    if (corruptedItems > 0) {
                        bankDataCorruptions.incrementAndGet();
                        bankDataRecoveries.incrementAndGet();
                        logger.warning("Recovered from " + corruptedItems + " corrupted items for player " + playerUuid + " page " + page + " (attempt " + attempt + ")");
                        // Mark as dirty to save the cleaned version
                        markBankDirty(playerUuid, page);
                    }

                    logger.fine("Successfully deserialized " + validItems + " items for player " + playerUuid + " page " + page + " (attempt " + attempt + ")");
                    return true;

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Deserialization attempt " + attempt + " failed for player " + playerUuid + " page " + page, e);
                    if (attempt == 3) {
                        throw e;
                    }
                    // Clear inventory before retry
                    for (int i = 0; i < BANK_CONTENT_SIZE; i++) {
                        inventory.setItem(i, null);
                    }
                }
            }

            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Enhanced deserialization failed for player " + playerUuid + " page " + page, e);
            bankDataCorruptions.incrementAndGet();
            return false;
        }
    }

    /**
     * Enhanced collection bin with improved error handling
     */
    public Inventory getCollectionBin(Player player) {
        UUID playerUuid = player.getUniqueId();

        Inventory cachedBin = collectionBins.get(playerUuid);
        if (cachedBin != null) {
            return cachedBin;
        }

        // Create new collection bin
        Inventory binInv = Bukkit.createInventory(null, BANK_SIZE, COLLECTION_BIN_TITLE);

        // Load from YakPlayer data with enhanced error handling
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);
        if (yakPlayer != null) {
            try {
                String serializedItems = yakPlayer.getSerializedCollectionBin();
                if (serializedItems != null && !serializedItems.isEmpty()) {
                    if (!deserializeInventoryWithValidation(serializedItems, binInv, playerUuid, -1)) {
                        logger.warning("Failed to load collection bin data for " + yakPlayer.getUsername());
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error loading collection bin for " + yakPlayer.getUsername(), e);
            }
        }

        collectionBins.put(playerUuid, binInv);
        return binInv;
    }

    /**
     * Initialize a bank inventory with UI elements
     */
    private void initializeBankInventory(Inventory inventory, Player player, int page, int maxPages) {
        // Bottom row border
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = BANK_CONTENT_SIZE; i < BANK_SIZE; i++) {
            inventory.setItem(i, glass);
        }

        // Navigation buttons
        if (page > 1) {
            inventory.setItem(BANK_SIZE - 9, createItemWithComponents(Material.ARROW,
                    Component.text("Previous Page", NamedTextColor.GREEN),
                    Arrays.asList(Component.text("Go to page " + (page - 1), NamedTextColor.GRAY))));
        }

        if (page < maxPages) {
            inventory.setItem(BANK_SIZE - 1, createItemWithComponents(Material.ARROW,
                    Component.text("Next Page", NamedTextColor.GREEN),
                    Arrays.asList(Component.text("Go to page " + (page + 1), NamedTextColor.GRAY))));
        }

        // Upgrade button
        BankUpgradeTier currentTier = BankUpgradeTier.getTier(maxPages);
        if (currentTier.hasNext()) {
            BankUpgradeTier nextTier = currentTier.getNext();
            inventory.setItem(BANK_SIZE - 7, createItemWithComponents(Material.CHEST,
                    Component.text("Upgrade Bank", NamedTextColor.GOLD),
                    Arrays.asList(
                            Component.text("Current: ", NamedTextColor.GRAY)
                                    .append(Component.text(maxPages + " pages", NamedTextColor.WHITE)),
                            Component.text("Upgrade to: ", NamedTextColor.GRAY)
                                    .append(Component.text(nextTier.getPages() + " pages", NamedTextColor.WHITE)),
                            Component.text("Cost: ", NamedTextColor.GRAY)
                                    .append(Component.text(nextTier.getCost() + " gems", NamedTextColor.YELLOW)),
                            Component.empty(),
                            Component.text("Click to upgrade!", NamedTextColor.YELLOW)
                    )));
        }

        // Collection bin button
        inventory.setItem(BANK_SIZE - 3, createItemWithComponents(Material.TRAPPED_CHEST,
                Component.text("Collection Bin", NamedTextColor.BLUE),
                Arrays.asList(Component.text("Access your collection bin", NamedTextColor.GRAY))));

        // Gem balance display
        inventory.setItem(BANK_SIZE - 5, createGemBankItem(player));
    }

    /**
     * Update the bank UI elements
     */
    private void updateBankUI(Inventory inventory, Player player, int page, int maxPages) {
        initializeBankInventory(inventory, player, page, maxPages);
    }

    /**
     * Create the gem balance indicator for the bank
     */
    private ItemStack createGemBankItem(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        int balance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

        return createItemWithComponents(Material.EMERALD,
                Component.text(balance + " ", NamedTextColor.GREEN)
                        .append(Component.text("GEM(s)", NamedTextColor.GREEN, TextDecoration.BOLD)),
                Collections.singletonList(
                        Component.text("Right Click to create ", NamedTextColor.GRAY)
                                .append(Component.text("A GEM NOTE", NamedTextColor.GREEN))
                ));
    }

    /**
     * Create a bank note item with unique ID
     */
    public ItemStack createBankNote(int amount) {
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();

        meta.displayName(Component.text("Bank Note", NamedTextColor.GREEN));

        NBTAccessor nbt = new NBTAccessor(note);
        nbt.setInt("note_id", ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
        nbt.setInt("gem_value", amount);
        note = nbt.update();

        meta.lore(Arrays.asList(
                Component.text("Value: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(Component.text(amount + " Gems", NamedTextColor.WHITE)),
                Component.text("Exchange at any bank for GEM(s)", NamedTextColor.GRAY)
        ));
        note.setItemMeta(meta);
        return note;
    }

    /**
     * Handle right-clicking on an ender chest to open the bank
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock().getType() != Material.ENDER_CHEST) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        // Don't reopen if already viewing bank
        if (player.getOpenInventory().getTitle().contains(BANK_TITLE_PREFIX)) {
            return;
        }

        // Check if player is ready (coordinated with YakPlayerManager)
        YakPlayerManager playerManager = YakPlayerManager.getInstance();
        if (playerManager != null && !playerManager.isPlayerReady(player)) {
            player.sendMessage(Component.text("Please wait, your character is still loading...", NamedTextColor.YELLOW));
            return;
        }

        // Check if player is sneaking for upgrade prompt
        if (player.isSneaking()) {
            promptBankUpgrade(player);
            return;
        }

        // Open bank inventory
        try {
            Inventory bankInv = getBank(player, 1);
            if (bankInv != null) {
                player.openInventory(bankInv);
                player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_CHEST_OPEN, Sound.Source.PLAYER, 1.0f, 1.0f),
                        player.getLocation().x(), player.getLocation().y(), player.getLocation().z());
            } else {
                player.sendMessage(Component.text("Unable to access your bank at this time.", NamedTextColor.RED));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("There was an error opening your bank.", NamedTextColor.RED));
            logger.log(Level.SEVERE, "Error opening bank for player " + player.getName(), e);
        }
    }

    /**
     * Enhanced inventory click events with better coordination
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;

        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        // Handle bank note merging
        if (isBankNote(event.getCurrentItem()) && isBankNote(event.getCursor())) {
            handleBankNoteMerge(event, player);
            return;
        }

        // Handle collection bin
        if (title.equals(COLLECTION_BIN_TITLE)) {
            handleCollectionBinClick(event, player);
            return;
        }

        // Only proceed for bank inventories
        if (!title.contains(BANK_TITLE_PREFIX) || title.contains("Guild")) {
            return;
        }

        // Check if player is ready for bank operations
        YakPlayerManager playerManager = YakPlayerManager.getInstance();
        if (playerManager != null && !playerManager.isPlayerReady(player)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Please wait, your character is still loading...", NamedTextColor.YELLOW));
            return;
        }

        // Parse page from title
        int page;
        try {
            String pageStr = title.substring(title.indexOf("(") + 1, title.indexOf("/"));
            page = Integer.parseInt(pageStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing bank page from title: " + title, e);
            return;
        }

        // Mark bank as dirty when items are modified (with enhanced tracking)
        markBankDirty(player.getUniqueId(), page);

        // Determine click location and action
        if (event.getRawSlot() >= BANK_SIZE) {
            // Clicked in player inventory
            if (event.isShiftClick()) {
                event.setCancelled(true);
                handleShiftClickToBank(event, player);
            }
        } else if (event.getRawSlot() < BANK_CONTENT_SIZE) {
            // Clicked in bank content area
            if (event.isShiftClick()) {
                event.setCancelled(true);
                handleShiftClickFromBank(event, player);
            }
        } else {
            // Clicked in bank UI (bottom row)
            event.setCancelled(true);
            handleBankUIClick(event, player, page);
        }
    }

    /**
     * Handle collection bin clicks
     */
    private void handleCollectionBinClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        if (event.isShiftClick()) return; // No shift clicks
        if (event.getRawSlot() >= event.getInventory().getSize()) return; // Only handle clicks in bin

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Return the item to player inventory
        if (player.getInventory().firstEmpty() >= 0) {
            event.setCurrentItem(new ItemStack(Material.AIR));
            player.getInventory().addItem(clickedItem);
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ITEM_PICKUP, Sound.Source.PLAYER, 1.0f, 1.0f),
                    player.getLocation().x(), player.getLocation().y(), player.getLocation().z());
        } else {
            player.sendMessage(Component.text("Your inventory is full!", NamedTextColor.RED));
        }
    }

    /**
     * Handle clicks on the bank UI elements (bottom row)
     */
    private void handleBankUIClick(InventoryClickEvent event, Player player, int currentPage) {
        int slot = event.getSlot();
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        int maxPages = yakPlayer.getBankPages();

        // Gem balance - open withdrawal prompt
        if (slot == BANK_SIZE - 5 && event.getClick() == ClickType.RIGHT) {
            promptForWithdraw(player);
        }
        // Previous page
        else if (slot == BANK_SIZE - 9 && currentPage > 1) {
            changeBankPage(player, currentPage - 1);
        }
        // Next page
        else if (slot == BANK_SIZE - 1 && currentPage < maxPages) {
            changeBankPage(player, currentPage + 1);
        }
        // Upgrade bank
        else if (slot == BANK_SIZE - 7) {
            promptBankUpgrade(player);
        }
        // Collection bin
        else if (slot == BANK_SIZE - 3) {
            openCollectionBin(player);
        }
    }

    /**
     * Enhanced page changing with better save coordination
     */
    private void changeBankPage(Player player, int newPage) {
        try {
            // Save current page before switching with enhanced coordination
            String currentTitle = player.getOpenInventory().getTitle();
            int currentPage = Integer.parseInt(currentTitle.substring(currentTitle.indexOf("(") + 1, currentTitle.indexOf("/")));

            UUID playerUuid = player.getUniqueId();
            markBankDirty(playerUuid, currentPage);

            // Force save current page immediately
            Inventory currentInventory = player.getOpenInventory().getTopInventory();
            saveBankWithCoordination(currentInventory, playerUuid, currentPage, "page_change");

            // Open new page
            player.closeInventory();
            Inventory newBankInv = getBank(player, newPage);
            if (newBankInv != null) {
                player.openInventory(newBankInv);
                player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_BAT_TAKEOFF, Sound.Source.PLAYER, 1.0f, 1.25f),
                        player.getLocation().x(), player.getLocation().y(), player.getLocation().z());
            }

        } catch (Exception e) {
            player.sendMessage(Component.text("Could not change bank page.", NamedTextColor.RED));
            logger.log(Level.WARNING, "Error changing bank page for player " + player.getName(), e);
        }
    }

    /**
     * Open collection bin
     */
    private void openCollectionBin(Player player) {
        try {
            Inventory binInv = getCollectionBin(player);
            player.closeInventory();
            player.openInventory(binInv);
            player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_CHEST_OPEN, Sound.Source.PLAYER, 1.0f, 1.0f),
                    player.getLocation().x(), player.getLocation().y(), player.getLocation().z());
        } catch (Exception e) {
            player.sendMessage(Component.text("Could not open collection bin.", NamedTextColor.RED));
            logger.log(Level.WARNING, "Error opening collection bin for player " + player.getName(), e);
        }
    }

    /**
     * Prompt player for bank upgrade
     */
    private void promptBankUpgrade(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        BankUpgradeTier currentTier = BankUpgradeTier.getTier(yakPlayer.getBankPages());
        if (!currentTier.hasNext()) {
            player.sendMessage(Component.text("You've reached the maximum bank size!", NamedTextColor.RED));
            return;
        }

        BankUpgradeTier nextTier = currentTier.getNext();
        int currentGems = EconomyManager.getInstance().getPhysicalGems(player);

        player.closeInventory();

        // Send upgrade confirmation messages using Adventure API
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("           *** ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Bank Upgrade Confirmation", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" ***", NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("           CURRENT Pages: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(String.valueOf(currentTier.getPages()), NamedTextColor.GREEN))
                .append(Component.text("          NEW Pages: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(nextTier.getPages()), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("                  Upgrade Cost: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(nextTier.getCost() + " Gem(s)", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("                  Your Gems: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(currentGems + " Gem(s)", NamedTextColor.YELLOW)));
        player.sendMessage(Component.empty());

        if (currentGems >= nextTier.getCost()) {
            player.sendMessage(Component.text("Enter '", NamedTextColor.GREEN)
                    .append(Component.text("confirm", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("' to confirm your upgrade.", NamedTextColor.GREEN)));
        } else {
            player.sendMessage(Component.text("You don't have enough gems for this upgrade!", NamedTextColor.RED));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("WARNING:", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(" Bank upgrades are ", NamedTextColor.RED))
                .append(Component.text("NOT", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" reversible or refundable. Type 'cancel' to void this upgrade request.", NamedTextColor.RED)));
        player.sendMessage(Component.empty());

        upgradePrompt.add(player.getUniqueId());
    }

    /**
     * Handle chat input for bank upgrade prompt
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChatForUpgrade(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!upgradePrompt.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().toLowerCase();

        upgradePrompt.remove(player.getUniqueId());

        if (message.equals("cancel")) {
            player.sendMessage(Component.text("Bank upgrade cancelled.", NamedTextColor.RED));
            return;
        }

        if (!message.equals("confirm")) {
            player.sendMessage(Component.text("Invalid response. Bank upgrade cancelled.", NamedTextColor.RED));
            return;
        }

        // Process upgrade
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> processBankUpgrade(player));
    }

    /**
     * Process bank upgrade with enhanced coordination
     */
    private void processBankUpgrade(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        BankUpgradeTier currentTier = BankUpgradeTier.getTier(yakPlayer.getBankPages());
        if (!currentTier.hasNext()) {
            player.sendMessage(Component.text("You've reached the maximum bank size!", NamedTextColor.RED));
            return;
        }

        BankUpgradeTier nextTier = currentTier.getNext();

        // Check if player has enough gems
        TransactionResult result = EconomyManager.getInstance().removePhysicalGems(player, nextTier.getCost());
        if (!result.isSuccess()) {
            player.sendMessage(Component.text("You don't have enough gems for this upgrade!", NamedTextColor.RED));
            player.sendMessage(Component.text("Required: " + nextTier.getCost() + " gems", NamedTextColor.RED));
            return;
        }

        // Upgrade the bank
        yakPlayer.setBankPages(nextTier.getPages());

        // Enhanced save coordination for upgrade
        YakPlayerManager playerManager = YakPlayerManager.getInstance();
        if (playerManager != null) {
            playerManager.savePlayer(yakPlayer);
        }

        // Success message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("*** BANK UPGRADE TO " + nextTier.getPages() + " PAGES COMPLETE ***",
                NamedTextColor.GREEN, TextDecoration.BOLD));
        player.sendMessage(Component.text("Your bank now has " + nextTier.getPages() + " pages!", NamedTextColor.GREEN));
        player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.PLAYER, 1F, 1.25F),
                player.getLocation().x(), player.getLocation().y(), player.getLocation().z());

        // Clear any cached bank pages to refresh UI
        Map<Integer, Inventory> playerBankPages = playerBanks.get(player.getUniqueId());
        if (playerBankPages != null) {
            // Save all pages before clearing cache
            UUID playerUuid = player.getUniqueId();
            for (Map.Entry<Integer, Inventory> entry : playerBankPages.entrySet()) {
                saveBankWithCoordination(entry.getValue(), playerUuid, entry.getKey(), "upgrade_cache_clear");
            }
            playerBankPages.clear();
        }
    }

    /**
     * Add item to collection bin if inventory full
     */
    public boolean addToCollectionBinIfNeeded(Player player, ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            return false; // Inventory has space
        }

        Inventory binInv = getCollectionBin(player);
        if (binInv.firstEmpty() != -1) {
            binInv.addItem(item);
            player.sendMessage(Component.text("Item sent to collection bin (inventory full)", NamedTextColor.YELLOW));
            return true;
        }

        return false; // Collection bin also full
    }

    /**
     * Serialize an inventory to Base64 string
     */
    private String serializeInventory(Inventory inventory) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Only serialize the content area (not UI elements)
            dataOutput.writeInt(BANK_CONTENT_SIZE);

            for (int i = 0; i < BANK_CONTENT_SIZE; i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error serializing inventory", e);
            return null;
        }
    }

    /**
     * Deserialize an inventory from Base64 string
     */
    private void deserializeInventory(String data, Inventory inventory) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();

            for (int i = 0; i < size && i < BANK_CONTENT_SIZE; i++) {
                inventory.setItem(i, (ItemStack) dataInput.readObject());
            }

            dataInput.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deserializing inventory", e);
        }
    }

    /**
     * Handle merging of bank notes
     */
    private void handleBankNoteMerge(InventoryClickEvent event, Player player) {
        event.setCancelled(true);

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        try {
            int firstValue = extractGemValue(currentItem);
            int secondValue = extractGemValue(cursorItem);

            if (firstValue <= 0 || secondValue <= 0) {
                player.sendMessage(Component.text("Invalid bank note value detected.", NamedTextColor.RED));
                return;
            }

            int totalValue = firstValue + secondValue;
            ItemStack mergedNote = createBankNote(totalValue);

            event.setCurrentItem(mergedNote);
            event.setCursor(null);

            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_ENDER_DRAGON_FLAP, Sound.Source.PLAYER, 1.0f, 1.2f),
                    player.getLocation().x(), player.getLocation().y(), player.getLocation().z());
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to merge bank notes.", NamedTextColor.RED));
            logger.log(Level.WARNING, "Error merging bank notes for player " + player.getName(), e);
        }
    }

    /**
     * Handle shift-clicking an item from player inventory to bank
     */
    private void handleShiftClickToBank(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        Inventory bankInv = event.getInventory();

        // Check if the item is currency
        if (isCurrencyItem(clickedItem)) {
            // Process currency deposit
            processCurrencyDeposit(player, clickedItem);
        } else {
            // Check if there's space in the bank
            int firstEmpty = findFirstEmptyInBank(bankInv);

            if (firstEmpty != -1) {
                // Move the item to the bank
                ItemStack itemToAdd = clickedItem.clone();
                bankInv.setItem(firstEmpty, itemToAdd);
                event.setCurrentItem(null);
            } else {
                // Try to add to collection bin
                if (!addToCollectionBinIfNeeded(player, clickedItem)) {
                    player.sendMessage(Component.text("Your bank and collection bin are full!", NamedTextColor.RED));
                } else {
                    event.setCurrentItem(null);
                }
            }
        }

        player.updateInventory();
    }

    /**
     * Find the first empty slot in the bank (excluding UI elements)
     */
    private int findFirstEmptyInBank(Inventory bankInv) {
        for (int i = 0; i < BANK_CONTENT_SIZE; i++) {
            if (bankInv.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Handle shift-clicking an item from bank to player inventory
     */
    private void handleShiftClickFromBank(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        // Try to add the item to the player's inventory
        HashMap<Integer, ItemStack> notAdded = player.getInventory().addItem(clickedItem.clone());

        if (notAdded.isEmpty()) {
            // All items were added to player inventory
            event.setCurrentItem(null);
        } else {
            // Some items couldn't be added, update the bank slot with remaining items
            clickedItem.setAmount(notAdded.get(0).getAmount());
        }

        player.updateInventory();
    }

    /**
     * Process the deposit of currency items
     */
    private void processCurrencyDeposit(Player player, ItemStack item) {
        int totalAmount = 0;

        try {
            if (item.getType() == Material.EMERALD && isValidGemItem(item)) {
                totalAmount = item.getAmount();
                player.getInventory().removeItem(item);
            } else if (item.getType() == Material.PAPER && isBankNote(item)) {
                totalAmount = extractGemValue(item);
                player.getInventory().removeItem(item);
            } else if (item.getType() == Material.INK_SAC) {
                GemPouchManager pouchManager = GemPouchManager.getInstance();
                if (pouchManager.isGemPouch(item)) {
                    totalAmount = pouchManager.getCurrentValue(item);
                    pouchManager.setPouchValue(item, 0);
                }
            }

            if (totalAmount > 0) {
                TransactionResult result = EconomyManager.getInstance().addBankGems(player, totalAmount);
                if (result.isSuccess()) {
                    updatePlayerBalance(player, totalAmount, true);
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to process currency deposit.", NamedTextColor.RED));
            logger.log(Level.WARNING, "Error processing currency deposit for player " + player.getName(), e);
        }
    }

    /**
     * Display the withdraw prompt to the player
     */
    private void promptForWithdraw(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        int balance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

        TextUtil.sendCenteredMessage(player, LEGACY_SERIALIZER.serialize(
                Component.text("Current Balance: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(Component.text(balance + " GEM(s)", NamedTextColor.GREEN))));

        if (balance <= 0) {
            TextUtil.sendCenteredMessage(player, LEGACY_SERIALIZER.serialize(
                    Component.text("You have no gems to withdraw.", NamedTextColor.RED)));
            return;
        }

        withdrawPrompt.add(player.getUniqueId());
        TextUtil.sendCenteredMessage(player, LEGACY_SERIALIZER.serialize(
                Component.text("Please enter the amount you'd like to CONVERT into a gem note. ", NamedTextColor.GRAY)
                        .append(Component.text("Alternatively, type ", NamedTextColor.GRAY))
                        .append(Component.text("'cancel'", NamedTextColor.RED))
                        .append(Component.text(" to void this operation.", NamedTextColor.GRAY))));
        player.closeInventory();
    }

    /**
     * Handle chat input for withdraw prompt
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!withdrawPrompt.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();

        // Handle cancel command
        if (message.equalsIgnoreCase("cancel")) {
            withdrawPrompt.remove(player.getUniqueId());
            TextUtil.sendCenteredMessage(player, LEGACY_SERIALIZER.serialize(
                    Component.text("Withdraw operation - ", NamedTextColor.RED)
                            .append(Component.text("CANCELLED", NamedTextColor.RED, TextDecoration.BOLD))));
            return;
        }

        // Parse amount
        try {
            int amount = Integer.parseInt(message);
            // Schedule processing on the main thread
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> processWithdraw(player, amount));
        } catch (NumberFormatException e) {
            TextUtil.sendCenteredMessage(player, LEGACY_SERIALIZER.serialize(
                    Component.text("Please enter a NUMBER or type 'cancel'.", NamedTextColor.RED)));
        }
    }

    /**
     * Process the withdrawal request
     */
    private void processWithdraw(Player player, int amount) {
        if (!withdrawPrompt.contains(player.getUniqueId())) {
            return;
        }

        withdrawPrompt.remove(player.getUniqueId());

        TransactionResult result = EconomyManager.getInstance().withdrawFromBank(player, amount);
        if (result.isSuccess()) {
            updatePlayerBalance(player, amount, false);
        } else {
            player.sendMessage(Component.text(result.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * Update the player's balance display
     */
    private void updatePlayerBalance(Player player, int amount, boolean isDeposit) {
        try {
            String prefix = isDeposit ? "&a+" : "&c-";
            String suffix = isDeposit ? "&a&lG" : "&c&lG";
            String destination = isDeposit ? "Your Bank" : "Your Inventory";

            TextUtil.sendCenteredMessage(player, TextUtil.colorize(prefix + amount + suffix + " &7→ " + destination));

            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            int newBalance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

            TextUtil.sendCenteredMessage(player, TextUtil.colorize("&a&lNew Balance: &a" + newBalance + " &aGEM(s)"));

            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, 1.0f, 1.0f),
                    player.getLocation().x(), player.getLocation().y(), player.getLocation().z());

            // Update bank UI if open
            if (player.getOpenInventory().getTitle().contains(BANK_TITLE_PREFIX)) {
                player.getOpenInventory().setItem(BANK_SIZE - 5, createGemBankItem(player));
            }

            player.updateInventory();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player balance for " + player.getName(), e);
        }
    }

    /**
     * Enhanced inventory close event with better save coordination
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        String title = event.getView().getTitle();
        Player player = (Player) event.getPlayer();

        if (title.contains(BANK_TITLE_PREFIX) && !title.contains("Guild")) {
            try {
                String pageStr = title.substring(title.indexOf("(") + 1, title.indexOf("/"));
                int page = Integer.parseInt(pageStr);
                UUID playerUuid = player.getUniqueId();

                // Mark this page as dirty for saving
                markBankDirty(playerUuid, page);

                // Enhanced immediate save coordination
                Inventory bankInventory = event.getInventory();
                boolean saveSuccess = saveBankWithCoordination(bankInventory, playerUuid, page, "inventory_close");

                // Remove from viewing map
                bankViewMap.remove(player.getUniqueId());

                if (saveSuccess) {
                    logger.fine("✅ Bank closed and saved successfully for player " + player.getName() + " page " + page);
                } else {
                    logger.warning("Bank save failed on close for player " + player.getName() + " page " + page);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving bank on close for player " + player.getName(), e);
            }
        } else if (title.equals(COLLECTION_BIN_TITLE)) {
            // Enhanced collection bin save
            boolean saveSuccess = saveCollectionBinWithCoordination(event.getInventory(), player.getUniqueId(), "inventory_close");
            if (saveSuccess) {
                logger.fine("✅ Collection bin closed and saved successfully for player " + player.getName());
            } else {
                logger.warning("Collection bin save failed on close for player " + player.getName());
            }
        }
    }

    // Utility methods
    private boolean isCurrencyItem(ItemStack item) {
        if (item == null) return false;
        return isValidGemItem(item) || isBankNote(item) || (item.getType() == Material.INK_SAC && GemPouchManager.getInstance().isGemPouch(item));
    }

    private boolean isValidGemItem(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String plainText = LEGACY_SERIALIZER.serialize(displayName);
                return plainText.contains("Gem");
            }
        }
        return false;
    }

    private boolean isBankNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                String plainText = LEGACY_SERIALIZER.serialize(displayName);
                if (plainText.equals("Bank Note") && meta.hasLore() && !meta.lore().isEmpty()) {
                    List<Component> lore = meta.lore();
                    if (lore != null && !lore.isEmpty()) {
                        String firstLoreLine = LEGACY_SERIALIZER.serialize(lore.get(0));
                        return firstLoreLine.contains("Value");
                    }
                }
            }
        }
        return false;
    }

    private int extractGemValue(ItemStack item) {
        if (!isBankNote(item)) return 0;
        try {
            NBTAccessor nbt = new NBTAccessor(item);
            if (nbt.hasKey("gem_value")) {
                return nbt.getInt("gem_value");
            }
            // Fallback to parsing from lore
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<Component> lore = meta.lore();
                if (lore != null && !lore.isEmpty()) {
                    String valueLine = LEGACY_SERIALIZER.serialize(lore.get(0));
                    String[] parts = valueLine.split(": ");
                    if (parts.length < 2) return 0;
                    String valueStr = parts[1].split(" Gems")[0];
                    return Integer.parseInt(valueStr);
                }
            }
        } catch (Exception e) {
            return 0;
        }
        return 0;
    }

    /**
     * Create item with legacy string support (backwards compatibility)
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore != null) {
                List<Component> componentLore = new ArrayList<>();
                for (String loreLine : lore) {
                    componentLore.add(Component.text(loreLine));
                }
                meta.lore(componentLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create item with Adventure Components
     */
    private ItemStack createItemWithComponents(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createGems(int amount) {
        if (amount <= 0) return null;
        ItemStack gem = new ItemStack(Material.EMERALD, Math.min(amount, 64));
        ItemMeta meta = gem.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Gem", NamedTextColor.WHITE));
            meta.lore(Collections.singletonList(Component.text("The currency of Andalucia", NamedTextColor.GRAY)));
            gem.setItemMeta(meta);
        }
        return gem;
    }

    // Public getters for other classes
    public int getMaxPages() { return MAX_BANK_PAGES; }

    public boolean hasCollectionBinItems(Player player) {
        Inventory bin = collectionBins.get(player.getUniqueId());
        if (bin == null) return false;

        for (ItemStack item : bin.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enhanced bank system statistics
     */
    public BankStats getBankStats() {
        return new BankStats(
                totalBankOpens.get(),
                totalBankSaves.get(),
                successfulBankSaves.get(),
                failedBankSaves.get(),
                cacheHits.get(),
                cacheMisses.get(),
                bankDataCorruptions.get(),
                bankDataRecoveries.get(),
                playerBanks.size(),
                dirtyBankPages.values().stream().mapToInt(Set::size).sum(),
                collectionBins.size()
        );
    }

    /**
     * Bank statistics class
     */
    public static class BankStats {
        public final int totalOpens;
        public final int totalSaves;
        public final int successfulSaves;
        public final int failedSaves;
        public final int cacheHits;
        public final int cacheMisses;
        public final int dataCorruptions;
        public final int dataRecoveries;
        public final int cachedPlayers;
        public final int dirtyPages;
        public final int collectionBins;

        public BankStats(int totalOpens, int totalSaves, int successfulSaves, int failedSaves,
                         int cacheHits, int cacheMisses, int dataCorruptions, int dataRecoveries,
                         int cachedPlayers, int dirtyPages, int collectionBins) {
            this.totalOpens = totalOpens;
            this.totalSaves = totalSaves;
            this.successfulSaves = successfulSaves;
            this.failedSaves = failedSaves;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.dataCorruptions = dataCorruptions;
            this.dataRecoveries = dataRecoveries;
            this.cachedPlayers = cachedPlayers;
            this.dirtyPages = dirtyPages;
            this.collectionBins = collectionBins;
        }

        public double getCacheHitRate() {
            int total = cacheHits + cacheMisses;
            return total > 0 ? ((double) cacheHits / total) * 100 : 0.0;
        }

        public double getSaveSuccessRate() {
            return totalSaves > 0 ? ((double) successfulSaves / totalSaves) * 100 : 0.0;
        }

        @Override
        public String toString() {
            return String.format("BankStats{opens=%d, saves=%d/%d (%.1f%%), cache=%d/%d (%.1f%%), " +
                            "corruptions=%d, recoveries=%d, cached=%d, dirty=%d, bins=%d}",
                    totalOpens, successfulSaves, totalSaves, getSaveSuccessRate(),
                    cacheHits, cacheHits + cacheMisses, getCacheHitRate(),
                    dataCorruptions, dataRecoveries, cachedPlayers, dirtyPages, collectionBins);
        }
    }
}
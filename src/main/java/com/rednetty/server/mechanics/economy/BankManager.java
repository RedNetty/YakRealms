package com.rednetty.server.mechanics.economy;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.nbt.NBTAccessor;
import com.rednetty.server.utils.text.TextUtil;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bank Manager with multi-page storage, upgrades, and collection bins
 * Backwards compatible with existing YakRealms bank system
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

    // Cache for bank inventories and collection bins
    private final Map<UUID, Map<Integer, Inventory>> playerBanks = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> collectionBins = new ConcurrentHashMap<>();

    // Players in withdraw prompts and upgrade prompts
    private final Set<UUID> withdrawPrompt = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> upgradePrompt = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Player viewing another player's bank
    private final Map<UUID, UUID> bankViewMap = new ConcurrentHashMap<>();

    // Track dirty bank pages that need saving
    private final Map<UUID, Set<Integer>> dirtyBankPages = new ConcurrentHashMap<>();

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

        // Start auto-save task for bank inventories
        Bukkit.getScheduler().runTaskTimerAsynchronously(YakRealms.getInstance(), this::autoSaveBanks,
                20L * 60 * 2, // 2 minutes initial delay
                20L * 60 * 5  // 5 minutes interval
        );

        logger.info(" Bank system has been enabled with multi-page support");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        saveBanks();
        logger.info(" Bank system has been disabled");
    }

    /**
     * Auto-save banks periodically
     */
    private void autoSaveBanks() {
        try {
            logger.fine("Auto-saving bank inventories...");
            int savedCount = 0;

            for (Map.Entry<UUID, Set<Integer>> entry : dirtyBankPages.entrySet()) {
                UUID playerUuid = entry.getKey();
                Set<Integer> dirtyPages = entry.getValue();

                if (!dirtyPages.isEmpty()) {
                    Map<Integer, Inventory> playerBankPages = playerBanks.get(playerUuid);
                    if (playerBankPages != null) {
                        for (Integer page : new HashSet<>(dirtyPages)) {
                            Inventory bankInv = playerBankPages.get(page);
                            if (bankInv != null) {
                                saveBank(bankInv, playerUuid, page);
                                savedCount++;
                            }
                        }
                        dirtyPages.clear();
                    }
                }
            }

            // Save collection bins
            for (Map.Entry<UUID, Inventory> entry : collectionBins.entrySet()) {
                saveCollectionBin(entry.getValue(), entry.getKey());
                savedCount++;
            }

            if (savedCount > 0) {
                logger.fine("Auto-saved " + savedCount + " bank pages and collection bins");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during auto-save of banks", e);
        }
    }

    /**
     * Save all banks to persistent storage
     */
    private void saveBanks() {
        try {
            logger.info("Saving " + playerBanks.size() + " player banks...");
            int savedCount = 0;

            for (Map.Entry<UUID, Map<Integer, Inventory>> entry : playerBanks.entrySet()) {
                UUID playerUuid = entry.getKey();
                Map<Integer, Inventory> banks = entry.getValue();

                for (Map.Entry<Integer, Inventory> bankEntry : banks.entrySet()) {
                    saveBank(bankEntry.getValue(), playerUuid, bankEntry.getKey());
                    savedCount++;
                }
            }

            // Save collection bins
            for (Map.Entry<UUID, Inventory> entry : collectionBins.entrySet()) {
                saveCollectionBin(entry.getValue(), entry.getKey());
                savedCount++;
            }

            // Clear cache after saving
            playerBanks.clear();
            collectionBins.clear();
            dirtyBankPages.clear();

            logger.info("Successfully saved " + savedCount + " bank pages and collection bins");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving banks", e);
        }
    }

    /**
     * Save a specific bank inventory
     */
    private void saveBank(Inventory inventory, UUID playerUuid, int page) {
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);

            if (yakPlayer != null) {
                // Serialize bank inventory contents
                String serializedItems = serializeInventory(inventory);
                if (serializedItems != null) {
                    yakPlayer.setSerializedBankItems(page, serializedItems);
                    logger.fine("Saved bank page " + page + " for player " + yakPlayer.getUsername());
                } else {
                    logger.warning("Failed to serialize bank inventory for player " + yakPlayer.getUsername() + " page " + page);
                }

                // Update bank balance from UI item
                ItemStack balanceItem = inventory.getItem(BANK_SIZE - 5);
                if (balanceItem != null && balanceItem.getType() == Material.EMERALD) {
                    try {
                        String displayName = balanceItem.getItemMeta().getDisplayName();
                        String balanceStr = ChatColor.stripColor(displayName).split(" ")[0];
                        int bankBalance = Integer.parseInt(balanceStr);
                        yakPlayer.setBankGems(bankBalance);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error parsing bank balance from item", e);
                    }
                }

                // Mark for saving and save player data
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            } else {
                logger.warning("Could not save bank - YakPlayer not found for UUID: " + playerUuid);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving bank for player " + playerUuid + " page " + page, e);
        }
    }

    /**
     * Save collection bin
     */
    private void saveCollectionBin(Inventory inventory, UUID playerUuid) {
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);

            if (yakPlayer != null) {
                String serializedItems = serializeInventory(inventory);
                yakPlayer.setSerializedCollectionBin(serializedItems);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
                logger.fine("Saved collection bin for player " + yakPlayer.getUsername());
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving collection bin for player " + playerUuid, e);
        }
    }

    /**
     * Mark a bank page as dirty (needs saving)
     */
    private void markBankDirty(UUID playerUuid, int page) {
        dirtyBankPages.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(page);
    }

    /**
     * Get a player's bank inventory for specific page
     */
    public Inventory getBank(Player player, int page) {
        UUID viewerUuid = player.getUniqueId();
        UUID targetUuid = bankViewMap.getOrDefault(viewerUuid, viewerUuid);

        // Validate page number
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(targetUuid);
        if (yakPlayer == null) {
            logger.warning("No YakPlayer found for bank access: " + targetUuid);
            return null;
        }

        int maxPages = yakPlayer.getBankPages();
        if (page < 1 || page > maxPages) {
            logger.warning("Invalid bank page " + page + " for player " + yakPlayer.getUsername() + " (max: " + maxPages + ")");
            return null;
        }

        // Get or create page map for this player
        Map<Integer, Inventory> playerBankPages = playerBanks.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());

        // Check if we already have this bank page cached
        Inventory cachedBank = playerBankPages.get(page);
        if (cachedBank != null) {
            // Update UI before returning
            updateBankUI(cachedBank, player, page, maxPages);
            return cachedBank;
        }

        // Create new bank inventory
        String title = BANK_TITLE_PREFIX + page + "/" + maxPages + BANK_TITLE_SUFFIX;
        Inventory bankInv = Bukkit.createInventory(null, BANK_SIZE, title);

        // Load bank contents from YakPlayer if available
        String serializedItems = yakPlayer.getSerializedBankItems(page);
        if (serializedItems != null && !serializedItems.isEmpty()) {
            deserializeInventory(serializedItems, bankInv);
        }

        // Initialize UI elements
        initializeBankInventory(bankInv, player, page, maxPages);

        // Cache and return
        playerBankPages.put(page, bankInv);
        return bankInv;
    }

    /**
     * Get collection bin for player
     */
    public Inventory getCollectionBin(Player player) {
        UUID playerUuid = player.getUniqueId();

        Inventory cachedBin = collectionBins.get(playerUuid);
        if (cachedBin != null) {
            return cachedBin;
        }

        // Create new collection bin
        Inventory binInv = Bukkit.createInventory(null, BANK_SIZE, COLLECTION_BIN_TITLE);

        // Load from YakPlayer data
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);
        if (yakPlayer != null) {
            String serializedItems = yakPlayer.getSerializedCollectionBin();
            if (serializedItems != null && !serializedItems.isEmpty()) {
                deserializeInventory(serializedItems, binInv);
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
            inventory.setItem(BANK_SIZE - 9, createItem(Material.ARROW, ChatColor.GREEN + "Previous Page",
                    Arrays.asList(ChatColor.GRAY + "Go to page " + (page - 1))));
        }

        if (page < maxPages) {
            inventory.setItem(BANK_SIZE - 1, createItem(Material.ARROW, ChatColor.GREEN + "Next Page",
                    Arrays.asList(ChatColor.GRAY + "Go to page " + (page + 1))));
        }

        // Upgrade button
        BankUpgradeTier currentTier = BankUpgradeTier.getTier(maxPages);
        if (currentTier.hasNext()) {
            BankUpgradeTier nextTier = currentTier.getNext();
            inventory.setItem(BANK_SIZE - 7, createItem(Material.CHEST, ChatColor.GOLD + "Upgrade Bank",
                    Arrays.asList(
                            ChatColor.GRAY + "Current: " + ChatColor.WHITE + maxPages + " pages",
                            ChatColor.GRAY + "Upgrade to: " + ChatColor.WHITE + nextTier.getPages() + " pages",
                            ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + nextTier.getCost() + " gems",
                            "",
                            ChatColor.YELLOW + "Click to upgrade!"
                    )));
        }

        // Collection bin button
        inventory.setItem(BANK_SIZE - 3, createItem(Material.TRAPPED_CHEST, ChatColor.BLUE + "Collection Bin",
                Arrays.asList(ChatColor.GRAY + "Access your collection bin")));

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

        return createItem(Material.EMERALD,
                ChatColor.GREEN.toString() + balance + ChatColor.GREEN + ChatColor.BOLD + " GEM(s)",
                Collections.singletonList(ChatColor.GRAY + "Right Click to create " + ChatColor.GREEN + "A GEM NOTE"));
    }

    /**
     * Create a bank note item with unique ID
     */
    public ItemStack createBankNote(int amount) {
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Bank Note");

        NBTAccessor nbt = new NBTAccessor(note);
        nbt.setInt("note_id", ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
        nbt.setInt("gem_value", amount);
        note = nbt.update();

        meta.setLore(Arrays.asList(
                ChatColor.WHITE.toString() + ChatColor.BOLD + "Value: " + ChatColor.WHITE + amount + " Gems",
                ChatColor.GRAY + "Exchange at any bank for GEM(s)"
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
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "There was an error opening your bank.");
            logger.log(Level.SEVERE, "Error opening bank for player " + player.getName(), e);
        }
    }

    /**
     * Handle inventory click events within the bank
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

        // Parse page from title
        int page;
        try {
            String pageStr = title.substring(title.indexOf("(") + 1, title.indexOf("/"));
            page = Integer.parseInt(pageStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing bank page from title: " + title, e);
            return;
        }

        // Mark bank as dirty when items are modified
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
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.RED + "Your inventory is full!");
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
     * Change to a different bank page
     */
    private void changeBankPage(Player player, int newPage) {
        try {
            // Save current page before switching
            String currentTitle = player.getOpenInventory().getTitle();
            int currentPage = Integer.parseInt(currentTitle.substring(currentTitle.indexOf("(") + 1, currentTitle.indexOf("/")));

            markBankDirty(player.getUniqueId(), currentPage);
            saveBank(player.getOpenInventory().getTopInventory(), player.getUniqueId(), currentPage);

            // Open new page
            player.closeInventory();
            Inventory newBankInv = getBank(player, newPage);
            if (newBankInv != null) {
                player.openInventory(newBankInv);
                player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.25f);
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Could not change bank page.");
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
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Could not open collection bin.");
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
            player.sendMessage(ChatColor.RED + "You've reached the maximum bank size!");
            return;
        }

        BankUpgradeTier nextTier = currentTier.getNext();
        int currentGems = EconomyManager.getInstance().getPhysicalGems(player);

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "           *** " + ChatColor.GREEN + ChatColor.BOLD + "Bank Upgrade Confirmation" + ChatColor.DARK_GRAY + " ***");
        player.sendMessage(ChatColor.DARK_GRAY + "           CURRENT Pages: " + ChatColor.GREEN + currentTier.getPages() + ChatColor.DARK_GRAY + "          NEW Pages: " + ChatColor.GREEN + nextTier.getPages());
        player.sendMessage(ChatColor.DARK_GRAY + "                  Upgrade Cost: " + ChatColor.GREEN + "" + nextTier.getCost() + " Gem(s)");
        player.sendMessage(ChatColor.DARK_GRAY + "                  Your Gems: " + ChatColor.YELLOW + "" + currentGems + " Gem(s)");
        player.sendMessage("");

        if (currentGems >= nextTier.getCost()) {
            player.sendMessage(ChatColor.GREEN + "Enter '" + ChatColor.BOLD + "confirm" + ChatColor.GREEN + "' to confirm your upgrade.");
        } else {
            player.sendMessage(ChatColor.RED + "You don't have enough gems for this upgrade!");
        }

        player.sendMessage("");
        player.sendMessage("" + ChatColor.RED + ChatColor.BOLD + "WARNING:" + ChatColor.RED + " Bank upgrades are " + ChatColor.BOLD + ChatColor.RED + "NOT" + ChatColor.RED + " reversible or refundable. Type 'cancel' to void this upgrade request.");
        player.sendMessage("");

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
            player.sendMessage(ChatColor.RED + "Bank upgrade cancelled.");
            return;
        }

        if (!message.equals("confirm")) {
            player.sendMessage(ChatColor.RED + "Invalid response. Bank upgrade cancelled.");
            return;
        }

        // Process upgrade
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> processBankUpgrade(player));
    }

    /**
     * Process bank upgrade
     */
    private void processBankUpgrade(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        BankUpgradeTier currentTier = BankUpgradeTier.getTier(yakPlayer.getBankPages());
        if (!currentTier.hasNext()) {
            player.sendMessage(ChatColor.RED + "You've reached the maximum bank size!");
            return;
        }

        BankUpgradeTier nextTier = currentTier.getNext();

        // Check if player has enough gems
        TransactionResult result = EconomyManager.getInstance().removePhysicalGems(player, nextTier.getCost());
        if (!result.isSuccess()) {
            player.sendMessage(ChatColor.RED + "You don't have enough gems for this upgrade!");
            player.sendMessage(ChatColor.RED + "Required: " + nextTier.getCost() + " gems");
            return;
        }

        // Upgrade the bank
        yakPlayer.setBankPages(nextTier.getPages());
        YakPlayerManager.getInstance().savePlayer(yakPlayer);

        // Success message
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "*** BANK UPGRADE TO " + nextTier.getPages() + " PAGES COMPLETE ***");
        player.sendMessage(ChatColor.GREEN + "Your bank now has " + nextTier.getPages() + " pages!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1F, 1.25F);

        // Clear any cached bank pages to refresh UI
        Map<Integer, Inventory> playerBankPages = playerBanks.get(player.getUniqueId());
        if (playerBankPages != null) {
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
            player.sendMessage(ChatColor.YELLOW + "Item sent to collection bin (inventory full)");
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

    // Handle other existing bank operations (withdraw prompts, currency handling, etc.)
    // ... (keeping existing methods from the original BankManager for backwards compatibility)

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
                player.sendMessage(ChatColor.RED + "Invalid bank note value detected.");
                return;
            }

            int totalValue = firstValue + secondValue;
            ItemStack mergedNote = createBankNote(totalValue);

            event.setCurrentItem(mergedNote);
            event.setCursor(null);

            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to merge bank notes.");
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
                    player.sendMessage(ChatColor.RED + "Your bank and collection bin are full!");
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
            player.sendMessage(ChatColor.RED + "Failed to process currency deposit.");
            logger.log(Level.WARNING, "Error processing currency deposit for player " + player.getName(), e);
        }
    }

    /**
     * Display the withdraw prompt to the player
     */
    private void promptForWithdraw(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        int balance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

        TextUtil.sendCenteredMessage(player, ChatColor.GREEN + "" + ChatColor.BOLD + "Current Balance: " + ChatColor.GREEN + balance + " GEM(s)");

        if (balance <= 0) {
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "You have no gems to withdraw.");
            return;
        }

        withdrawPrompt.add(player.getUniqueId());
        TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "Please enter the amount you'd like to CONVERT into a gem note. " + "Alternatively, type " + ChatColor.RED + "'cancel'" + ChatColor.GRAY + " to void this operation.");
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
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "Withdraw operation - " + ChatColor.BOLD + "CANCELLED");
            return;
        }

        // Parse amount
        try {
            int amount = Integer.parseInt(message);
            // Schedule processing on the main thread
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> processWithdraw(player, amount));
        } catch (NumberFormatException e) {
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "Please enter a NUMBER or type 'cancel'.");
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
            player.sendMessage(ChatColor.RED + result.getMessage());
        }
    }

    /**
     * Update the player's balance display
     */
    private void updatePlayerBalance(Player player, int amount, boolean isDeposit) {
        try {
            String prefix = isDeposit ? "&a+" : "&c-";
            TextUtil.sendCenteredMessage(player, TextUtil.colorize(prefix + amount + (isDeposit ? "&a&lG" : "&c&lG") + " &7➜ " + (isDeposit ? "Your Bank" : "Your Inventory")));

            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            int newBalance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

            TextUtil.sendCenteredMessage(player, TextUtil.colorize("&a&lNew Balance: &a" + newBalance + " &aGEM(s)"));

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

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
     * Handle inventory close event to save the bank
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

                // Immediate save of the bank page
                Inventory bankInventory = event.getInventory();
                saveBank(bankInventory, playerUuid, page);

                // Remove from viewing map
                bankViewMap.remove(player.getUniqueId());

                logger.fine("Bank closed and saved for player " + player.getName() + " page " + page);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving bank on close for player " + player.getName(), e);
            }
        } else if (title.equals(COLLECTION_BIN_TITLE)) {
            // Save collection bin
            saveCollectionBin(event.getInventory(), player.getUniqueId());
            logger.fine("Collection bin closed and saved for player " + player.getName());
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
        return meta.hasDisplayName() && meta.getDisplayName().contains("Gem");
    }

    private boolean isBankNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.GREEN + "Bank Note") && meta.hasLore() && !meta.getLore().isEmpty() && meta.getLore().get(0).contains("Value");
    }

    private int extractGemValue(ItemStack item) {
        if (!isBankNote(item)) return 0;
        try {
            NBTAccessor nbt = new NBTAccessor(item);
            if (nbt.hasKey("gem_value")) {
                return nbt.getInt("gem_value");
            }
            // Fallback to parsing from lore
            List<String> lore = item.getItemMeta().getLore();
            String valueLine = ChatColor.stripColor(lore.get(0));
            String[] parts = valueLine.split(": ");
            if (parts.length < 2) return 0;
            String valueStr = parts[1].split(" Gems")[0];
            return Integer.parseInt(valueStr);
        } catch (Exception e) {
            return 0;
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createGems(int amount) {
        if (amount <= 0) return null;
        ItemStack gem = new ItemStack(Material.EMERALD, Math.min(amount, 64));
        ItemMeta meta = gem.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Gem");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "The currency of Andalucia"));
        gem.setItemMeta(meta);
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
}
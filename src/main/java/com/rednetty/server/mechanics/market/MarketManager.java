package com.rednetty.server.mechanics.market;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.market.menu.MarketMainMenu;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced market manager with comprehensive functionality and performance optimization
 */
public class MarketManager implements Listener {
    private static MarketManager instance;
    private final MarketRepository repository;
    private final YakRealms plugin;
    private final Logger logger;

    // Configuration
    private final int maxListingsPerPlayer;
    private final int maxListingDurationDays;
    private final int minItemPrice;
    private final int maxItemPrice;
    private final double marketTaxRate;
    private final int featuredListingCost;
    private final int minLevelToUse;
    private final Set<Material> bannedMaterials;

    // Active state tracking
    private final Map<UUID, MarketSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTransactionTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dailyListings = new ConcurrentHashMap<>();
    private final Map<UUID, LocalDate> lastDailyReset = new ConcurrentHashMap<>();

    // Chat input tracking
    private final Map<UUID, ChatInputContext> chatInputContexts = new ConcurrentHashMap<>();

    // Performance tracking
    private volatile int totalTransactions = 0;
    private volatile long totalGemsTraded = 0;
    private volatile int totalListings = 0;

    // Tasks
    private BukkitTask maintenanceTask;
    private BukkitTask statisticsTask;

    /**
     * Market session for tracking user state
     */
    public static class MarketSession {
        private final UUID playerId;
        private final long sessionStart;
        private MarketCategory selectedCategory;
        private String searchQuery = "";
        private MarketRepository.SortOrder sortOrder = MarketRepository.SortOrder.NEWEST_FIRST;
        private int currentPage = 0;
        private boolean isListingMode = false;
        private ItemStack itemToList;
        private int priceToList;

        // Session data storage
        private final Map<String, Object> sessionData = new ConcurrentHashMap<>();

        public MarketSession(UUID playerId) {
            this.playerId = playerId;
            this.sessionStart = System.currentTimeMillis();
        }

        // Session data methods
        public void setData(String key, Object value) {
            sessionData.put(key, value);
        }

        public Object getData(String key) {
            return sessionData.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T getData(String key, Class<T> type) {
            Object value = sessionData.get(key);
            if (value != null && type.isInstance(value)) {
                return (T) value;
            }
            return null;
        }

        public boolean hasData(String key) {
            return sessionData.containsKey(key);
        }

        public void removeData(String key) {
            sessionData.remove(key);
        }

        public void clearData() {
            sessionData.clear();
        }

        // Getters and setters
        public UUID getPlayerId() { return playerId; }
        public long getSessionStart() { return sessionStart; }
        public MarketCategory getSelectedCategory() { return selectedCategory; }
        public void setSelectedCategory(MarketCategory category) { this.selectedCategory = category; }
        public String getSearchQuery() { return searchQuery; }
        public void setSearchQuery(String query) { this.searchQuery = query != null ? query : ""; }
        public MarketRepository.SortOrder getSortOrder() { return sortOrder; }
        public void setSortOrder(MarketRepository.SortOrder sortOrder) { this.sortOrder = sortOrder; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int page) { this.currentPage = Math.max(0, page); }
        public boolean isListingMode() { return isListingMode; }
        public void setListingMode(boolean listingMode) { this.isListingMode = listingMode; }
        public ItemStack getItemToList() { return itemToList; }
        public void setItemToList(ItemStack item) { this.itemToList = item; }
        public int getPriceToList() { return priceToList; }
        public void setPriceToList(int price) { this.priceToList = price; }
    }

    /**
     * Chat input context for handling user input
     */
    private static class ChatInputContext {
        private final ChatInputType type;
        private final long startTime;
        private final Map<String, Object> data;

        public ChatInputContext(ChatInputType type) {
            this.type = type;
            this.startTime = System.currentTimeMillis();
            this.data = new HashMap<>();
        }

        public ChatInputType getType() { return type; }
        public long getStartTime() { return startTime; }
        public Map<String, Object> getData() { return data; }
        public boolean isExpired(long timeoutMs) {
            return System.currentTimeMillis() - startTime > timeoutMs;
        }
    }

    /**
     * Types of chat input we're waiting for
     */
    private enum ChatInputType {
        LISTING_PRICE,
        PURCHASE_CONFIRMATION,
        REMOVAL_CONFIRMATION,
        SEARCH_QUERY
    }

    /**
     * Market transaction result
     */
    public enum TransactionResult {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        ITEM_NOT_FOUND,
        ITEM_EXPIRED,
        INVENTORY_FULL,
        PERMISSION_DENIED,
        COOLDOWN_ACTIVE,
        INVALID_ITEM,
        LISTING_LIMIT_REACHED,
        PLAYER_OFFLINE,
        DATABASE_ERROR
    }

    /**
     * Private constructor for singleton
     */
    private MarketManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.repository = new MarketRepository();

        // Load configuration
        this.maxListingsPerPlayer = plugin.getConfig().getInt("market.max_listings_per_player", 10);
        this.maxListingDurationDays = plugin.getConfig().getInt("market.max_listing_duration_days", 7);
        this.minItemPrice = plugin.getConfig().getInt("market.min_item_price", 1);
        this.maxItemPrice = plugin.getConfig().getInt("market.max_item_price", 1000000);
        this.marketTaxRate = plugin.getConfig().getDouble("market.tax_rate", 0.05);
        this.featuredListingCost = plugin.getConfig().getInt("market.featured_listing_cost", 1000);
        this.minLevelToUse = plugin.getConfig().getInt("market.min_level_to_use", 5);

        // Load banned materials
        this.bannedMaterials = plugin.getConfig().getStringList("market.banned_materials")
                .stream()
                .map(Material::matchMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Add default banned materials
        bannedMaterials.addAll(Arrays.asList(
                Material.BEDROCK, Material.COMMAND_BLOCK, Material.STRUCTURE_BLOCK,
                Material.BARRIER, Material.DEBUG_STICK, Material.KNOWLEDGE_BOOK
        ));
    }

    /**
     * Get singleton instance
     */
    public static MarketManager getInstance() {
        if (instance == null) {
            synchronized (MarketManager.class) {
                if (instance == null) {
                    instance = new MarketManager();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize the market manager
     */
    public void onEnable() {
        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Start maintenance tasks
        startMaintenanceTasks();

        // Load existing sessions for online players
        Bukkit.getOnlinePlayers().forEach(player ->
                activeSessions.put(player.getUniqueId(), new MarketSession(player.getUniqueId())));

        logger.info("Market manager enabled successfully");
    }

    /**
     * Shutdown the market manager
     */
    public void onDisable() {
        // Cancel tasks
        if (maintenanceTask != null && !maintenanceTask.isCancelled()) {
            maintenanceTask.cancel();
        }
        if (statisticsTask != null && !statisticsTask.isCancelled()) {
            statisticsTask.cancel();
        }

        // Clear active sessions
        activeSessions.clear();
        chatInputContexts.clear();

        logger.info("Market manager disabled");
    }

    /**
     * Start maintenance and statistics tasks
     */
    private void startMaintenanceTasks() {
        // Maintenance task (every 10 minutes)
        maintenanceTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                performMaintenance();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during market maintenance", e);
            }
        }, 12000L, 12000L);

        // Statistics task (every hour)
        statisticsTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                logStatistics();
                resetDailyLimits();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error during statistics update", e);
            }
        }, 72000L, 72000L);
    }

    /**
     * Perform routine maintenance
     */
    private void performMaintenance() {
        // Clean up expired sessions
        long currentTime = System.currentTimeMillis();
        activeSessions.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline() ||
                    (currentTime - entry.getValue().getSessionStart()) > TimeUnit.HOURS.toMillis(2);
        });

        // Clean up old transaction times
        lastTransactionTime.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > TimeUnit.HOURS.toMillis(24));

        // Clean up expired chat input contexts
        chatInputContexts.entrySet().removeIf(entry ->
                entry.getValue().isExpired(TimeUnit.MINUTES.toMillis(5)));
    }

    /**
     * Reset daily limits for players
     */
    private void resetDailyLimits() {
        LocalDate currentDate = LocalDate.now();

        dailyListings.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            LocalDate lastReset = lastDailyReset.get(playerId);

            if (lastReset == null || !lastReset.equals(currentDate)) {
                lastDailyReset.put(playerId, currentDate);
                return true; // Remove from dailyListings to reset
            }
            return false;
        });
    }

    /**
     * Log performance statistics
     */
    private void logStatistics() {
        logger.info("Market Statistics:");
        logger.info("  Active Sessions: " + activeSessions.size());
        logger.info("  Total Transactions: " + totalTransactions);
        logger.info("  Total Gems Traded: " + TextUtil.formatNumber(totalGemsTraded));
        logger.info("  Total Listings: " + totalListings);

        // Repository stats
        Map<String, Object> repoStats = repository.getPerformanceStats();
        logger.info("  Database Cache Hit Rate: " + String.format("%.1f%%", repoStats.get("cacheHitRate")));
    }

    /**
     * Open the main market menu for a player
     */
    public void openMarketMenu(Player player) {
        if (!canUseMarket(player)) {
            return;
        }

        MarketSession session = getOrCreateSession(player.getUniqueId());
        new MarketMainMenu(player, this, session).open();
    }

    /**
     * Start listing process with chat input
     */
    public void startListingProcess(Player player, ItemStack item) {
        if (!canUseMarket(player)) {
            return;
        }

        TransactionResult validation = validateListing(player, item, 1); // Basic validation
        if (validation != TransactionResult.SUCCESS) {
            handleTransactionResult(player, validation);
            return;
        }

        MarketSession session = getOrCreateSession(player.getUniqueId());
        session.setItemToList(item);
        session.setListingMode(true);

        // Set up chat input context
        ChatInputContext context = new ChatInputContext(ChatInputType.LISTING_PRICE);
        context.getData().put("item", item);
        chatInputContexts.put(player.getUniqueId(), context);

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "⚡ " + ChatColor.YELLOW + "List Item for Sale");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + getItemDisplayName(item));
        player.sendMessage(ChatColor.GRAY + "Amount: " + ChatColor.WHITE + item.getAmount());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Enter the price in gems:");
        player.sendMessage(ChatColor.GRAY + "Range: " + TextUtil.formatNumber(minItemPrice) +
                " - " + TextUtil.formatNumber(maxItemPrice) + " gems");
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.RED + "'cancel'" + ChatColor.GRAY + " to abort.");
        player.sendMessage("");
    }

    /**
     * Start purchase confirmation process
     */
    public void startPurchaseConfirmation(Player player, UUID itemId) {
        repository.findById(itemId).thenAccept(itemOpt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!itemOpt.isPresent()) {
                    player.sendMessage(ChatColor.RED + "✗ Item not found!");
                    return;
                }

                MarketItem marketItem = itemOpt.get();

                if (marketItem.isExpired()) {
                    player.sendMessage(ChatColor.RED + "✗ This item has expired!");
                    return;
                }

                if (marketItem.getOwnerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "✗ You cannot purchase your own item!");
                    return;
                }

                // Set up chat input context
                ChatInputContext context = new ChatInputContext(ChatInputType.PURCHASE_CONFIRMATION);
                context.getData().put("itemId", itemId);
                context.getData().put("marketItem", marketItem);
                chatInputContexts.put(player.getUniqueId(), context);

                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "⚡ Confirm Purchase");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + marketItem.getDisplayName());
                player.sendMessage(ChatColor.GRAY + "Price: " + ChatColor.GREEN + TextUtil.formatNumber(marketItem.getPrice()) + " gems");
                player.sendMessage(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + marketItem.getOwnerName());
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "Type " + ChatColor.YELLOW + "'confirm'" + ChatColor.GREEN + " to purchase");
                player.sendMessage(ChatColor.RED + "Type " + ChatColor.YELLOW + "'cancel'" + ChatColor.RED + " to abort");
                player.sendMessage("");
            });
        });
    }

    /**
     * Start removal confirmation process
     */
    public void startRemovalConfirmation(Player player, UUID itemId) {
        repository.findById(itemId).thenAccept(itemOpt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!itemOpt.isPresent()) {
                    player.sendMessage(ChatColor.RED + "✗ Item not found!");
                    return;
                }

                MarketItem marketItem = itemOpt.get();

                if (!marketItem.getOwnerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "✗ You don't own this item!");
                    return;
                }

                // Set up chat input context
                ChatInputContext context = new ChatInputContext(ChatInputType.REMOVAL_CONFIRMATION);
                context.getData().put("itemId", itemId);
                context.getData().put("marketItem", marketItem);
                chatInputContexts.put(player.getUniqueId(), context);

                player.closeInventory();
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "⚡ Confirm Removal");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Item: " + ChatColor.WHITE + marketItem.getDisplayName());
                player.sendMessage(ChatColor.GRAY + "Price: " + ChatColor.GREEN + TextUtil.formatNumber(marketItem.getPrice()) + " gems");
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "Remove this listing from the market?");
                player.sendMessage(ChatColor.GRAY + "The item will be returned to your inventory.");
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "Type " + ChatColor.YELLOW + "'confirm'" + ChatColor.GREEN + " to remove");
                player.sendMessage(ChatColor.RED + "Type " + ChatColor.YELLOW + "'cancel'" + ChatColor.RED + " to abort");
                player.sendMessage("");
            });
        });
    }

    /**
     * Start search input process
     */
    public void startSearchInput(Player player) {
        // Set up chat input context
        ChatInputContext context = new ChatInputContext(ChatInputType.SEARCH_QUERY);
        chatInputContexts.put(player.getUniqueId(), context);

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚡ Market Search");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Enter your search term:");
        player.sendMessage(ChatColor.GRAY + "Example: 'diamond sword', 'enchanted', 'steve'");
        player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.RED + "'cancel'" + ChatColor.GRAY + " to abort.");
        player.sendMessage("");
    }

    /**
     * List an item on the market
     */
    public CompletableFuture<TransactionResult> listItem(Player player, ItemStack item, int price, boolean featured) {
        return CompletableFuture.supplyAsync(() -> {
            // Validation
            TransactionResult validation = validateListing(player, item, price);
            if (validation != TransactionResult.SUCCESS) {
                return validation;
            }

            try {
                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
                if (yakPlayer == null) {
                    return TransactionResult.PLAYER_OFFLINE;
                }

                // Calculate total cost (featured listing cost)
                int totalCost = featured ? featuredListingCost : 0;
                if (totalCost > 0 && yakPlayer.getGems() < totalCost) {
                    return TransactionResult.INSUFFICIENT_FUNDS;
                }

                // Remove item from inventory
                if (!removeItemFromInventory(player, item)) {
                    return TransactionResult.INVALID_ITEM;
                }

                // Deduct cost
                if (totalCost > 0) {
                    yakPlayer.removeGems(totalCost, "Market featured listing");
                }

                // Create market item
                MarketCategory category = MarketCategory.fromMaterial(item.getType());
                MarketItem marketItem = new MarketItem(
                        player.getUniqueId(),
                        player.getName(),
                        item,
                        price,
                        category
                );
                marketItem.setFeatured(featured);

                // Save to database
                repository.save(marketItem).get(10, TimeUnit.SECONDS);

                // Update tracking
                incrementDailyListings(player.getUniqueId());
                totalListings++;

                // Notify player
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "✓ Item listed successfully!");
                    player.sendMessage(ChatColor.GRAY + "Price: " + ChatColor.YELLOW + TextUtil.formatNumber(price) + " gems");
                    if (featured) {
                        player.sendMessage(ChatColor.GOLD + "★ Featured listing");
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                });

                return TransactionResult.SUCCESS;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error listing item for player " + player.getName(), e);
                return TransactionResult.DATABASE_ERROR;
            }
        });
    }

    /**
     * Purchase an item from the market
     */
    public CompletableFuture<TransactionResult> purchaseItem(Player buyer, UUID itemId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get the item
                Optional<MarketItem> itemOpt = repository.findById(itemId).get(10, TimeUnit.SECONDS);
                if (!itemOpt.isPresent()) {
                    return TransactionResult.ITEM_NOT_FOUND;
                }

                MarketItem marketItem = itemOpt.get();

                // Validation
                if (marketItem.isExpired()) {
                    return TransactionResult.ITEM_EXPIRED;
                }

                if (marketItem.getOwnerUuid().equals(buyer.getUniqueId())) {
                    return TransactionResult.PERMISSION_DENIED;
                }

                YakPlayer buyerData = YakPlayerManager.getInstance().getPlayer(buyer);
                if (buyerData == null) {
                    return TransactionResult.PLAYER_OFFLINE;
                }

                // Check funds
                int totalCost = marketItem.getPrice();
                if (buyerData.getGems() < totalCost) {
                    return TransactionResult.INSUFFICIENT_FUNDS;
                }

                // Check inventory space
                if (!hasInventorySpace(buyer, marketItem.getItemStack())) {
                    return TransactionResult.INVENTORY_FULL;
                }

                // Process transaction
                buyerData.removeGems(totalCost, "Market purchase");

                // Calculate seller payment (after tax)
                int tax = (int) (totalCost * marketTaxRate);
                int sellerPayment = totalCost - tax;

                // Pay seller
                YakPlayer sellerData = YakPlayerManager.getInstance().getPlayer(marketItem.getOwnerUuid());
                if (sellerData != null) {
                    sellerData.addGems(sellerPayment, "Market sale");
                } else {
                    // Handle offline seller
                    YakPlayerManager.getInstance().getRepository()
                            .findById(marketItem.getOwnerUuid())
                            .thenAccept(sellerOpt -> {
                                if (sellerOpt.isPresent()) {
                                    YakPlayer seller = sellerOpt.get();
                                    seller.addGems(sellerPayment, "Market sale");
                                    YakPlayerManager.getInstance().getRepository().save(seller);
                                }
                            });
                }

                // Give item to buyer
                ItemStack item = marketItem.getItemStack();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buyer.getInventory().addItem(item);
                });

                // Remove from market
                repository.deleteById(itemId);

                // Update statistics
                totalTransactions++;
                totalGemsTraded += totalCost;
                lastTransactionTime.put(buyer.getUniqueId(), System.currentTimeMillis());

                // Notify players
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buyer.sendMessage(ChatColor.GREEN + "✓ Purchase successful!");
                    buyer.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + TextUtil.formatNumber(totalCost) + " gems");
                    buyer.playSound(buyer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                    Player seller = Bukkit.getPlayer(marketItem.getOwnerUuid());
                    if (seller != null && seller.isOnline()) {
                        seller.sendMessage(ChatColor.GREEN + "✓ Your item was sold!");
                        seller.sendMessage(ChatColor.GRAY + "Earned: " + ChatColor.YELLOW +
                                TextUtil.formatNumber(sellerPayment) + " gems " +
                                ChatColor.DARK_GRAY + "(Tax: " + TextUtil.formatNumber(tax) + ")");
                        seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                    }
                });

                return TransactionResult.SUCCESS;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing purchase for player " + buyer.getName(), e);
                return TransactionResult.DATABASE_ERROR;
            }
        });
    }

    /**
     * Remove an item listing
     */
    public CompletableFuture<TransactionResult> removeItemListing(Player player, UUID itemId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<MarketItem> itemOpt = repository.findById(itemId).get(10, TimeUnit.SECONDS);
                if (!itemOpt.isPresent()) {
                    return TransactionResult.ITEM_NOT_FOUND;
                }

                MarketItem marketItem = itemOpt.get();

                // Check ownership
                if (!marketItem.getOwnerUuid().equals(player.getUniqueId())) {
                    return TransactionResult.PERMISSION_DENIED;
                }

                // Check inventory space
                if (!hasInventorySpace(player, marketItem.getItemStack())) {
                    return TransactionResult.INVENTORY_FULL;
                }

                // Remove from database
                repository.deleteById(itemId);

                // Return item to player
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(marketItem.getItemStack());
                    player.sendMessage(ChatColor.YELLOW + "Item removed from market and returned to your inventory.");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                });

                return TransactionResult.SUCCESS;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error removing listing for player " + player.getName(), e);
                return TransactionResult.DATABASE_ERROR;
            }
        });
    }

    /**
     * Get market items with filtering
     */
    public CompletableFuture<List<MarketItem>> getMarketItems(MarketCategory category, String searchQuery,
                                                              MarketRepository.SortOrder sortOrder, int page, int itemsPerPage) {
        int skip = page * itemsPerPage;
        return repository.findActiveItems(category, searchQuery, sortOrder, skip, itemsPerPage);
    }

    /**
     * Get player's market listings
     */
    public CompletableFuture<List<MarketItem>> getPlayerListings(UUID playerId) {
        return repository.findByOwner(playerId);
    }

    // Chat event handler
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        ChatInputContext context = chatInputContexts.get(playerId);
        if (context == null) {
            return; // Not waiting for input from this player
        }

        // Cancel the chat event to prevent public message
        event.setCancelled(true);

        String message = event.getMessage().trim();

        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            chatInputContexts.remove(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + "Operation cancelled.");
                openMarketMenu(player);
            });
            return;
        }

        // Handle different input types
        switch (context.getType()) {
            case LISTING_PRICE:
                handleListingPriceInput(player, message, context);
                break;
            case PURCHASE_CONFIRMATION:
                handlePurchaseConfirmationInput(player, message, context);
                break;
            case REMOVAL_CONFIRMATION:
                handleRemovalConfirmationInput(player, message, context);
                break;
            case SEARCH_QUERY:
                handleSearchQueryInput(player, message, context);
                break;
        }
    }

    private void handleListingPriceInput(Player player, String message, ChatInputContext context) {
        try {
            int price = Integer.parseInt(message);

            if (price < minItemPrice || price > maxItemPrice) {
                player.sendMessage(ChatColor.RED + "Price must be between " +
                        TextUtil.formatNumber(minItemPrice) + " and " +
                        TextUtil.formatNumber(maxItemPrice) + " gems.");
                return;
            }

            ItemStack item = (ItemStack) context.getData().get("item");
            if (item == null) {
                player.sendMessage(ChatColor.RED + "Error: Item not found.");
                chatInputContexts.remove(player.getUniqueId());
                return;
            }

            chatInputContexts.remove(player.getUniqueId());

            // Ask about featured listing
            Bukkit.getScheduler().runTask(plugin, () -> {
                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
                boolean canAffordFeatured = yakPlayer != null && yakPlayer.getGems() >= featuredListingCost;

                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "Price set to: " + ChatColor.GREEN + TextUtil.formatNumber(price) + " gems");
                player.sendMessage("");

                if (canAffordFeatured) {
                    player.sendMessage(ChatColor.GOLD + "★ Make this a featured listing?");
                    player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + TextUtil.formatNumber(featuredListingCost) + " gems");
                    player.sendMessage(ChatColor.GRAY + "Featured listings appear at the top and get more views.");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "Type " + ChatColor.YELLOW + "'yes'" + ChatColor.GREEN + " for featured listing");
                    player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.YELLOW + "'no'" + ChatColor.GRAY + " for regular listing");

                    // Set up new context for featured confirmation
                    ChatInputContext featuredContext = new ChatInputContext(ChatInputType.PURCHASE_CONFIRMATION);
                    featuredContext.getData().put("item", item);
                    featuredContext.getData().put("price", price);
                    featuredContext.getData().put("featured_choice", true);
                    chatInputContexts.put(player.getUniqueId(), featuredContext);
                } else {
                    // List normally
                    listItem(player, item, price, false).thenAccept(result ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                handleTransactionResult(player, result);
                                if (result == TransactionResult.SUCCESS) {
                                    openMarketMenu(player);
                                }
                            }));
                }
            });

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid price. Please enter a valid number.");
        }
    }

    private void handlePurchaseConfirmationInput(Player player, String message, ChatInputContext context) {
        if (context.getData().containsKey("featured_choice")) {
            // This is actually for featured listing choice
            boolean featured = message.equalsIgnoreCase("yes") || message.equalsIgnoreCase("y");
            ItemStack item = (ItemStack) context.getData().get("item");
            int price = (Integer) context.getData().get("price");

            chatInputContexts.remove(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                listItem(player, item, price, featured).thenAccept(result ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            handleTransactionResult(player, result);
                            if (result == TransactionResult.SUCCESS) {
                                openMarketMenu(player);
                            }
                        }));
            });
            return;
        }

        if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes")) {
            UUID itemId = (UUID) context.getData().get("itemId");
            chatInputContexts.remove(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                purchaseItem(player, itemId).thenAccept(result ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            handleTransactionResult(player, result);
                            if (result == TransactionResult.SUCCESS) {
                                openMarketMenu(player);
                            }
                        }));
            });
        } else {
            chatInputContexts.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + "Purchase cancelled.");
                openMarketMenu(player);
            });
        }
    }

    private void handleRemovalConfirmationInput(Player player, String message, ChatInputContext context) {
        if (message.equalsIgnoreCase("confirm") || message.equalsIgnoreCase("yes")) {
            UUID itemId = (UUID) context.getData().get("itemId");
            chatInputContexts.remove(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                removeItemListing(player, itemId).thenAccept(result ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            handleTransactionResult(player, result);
                            openMarketMenu(player);
                        }));
            });
        } else {
            chatInputContexts.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.YELLOW + "Removal cancelled.");
                openMarketMenu(player);
            });
        }
    }

    private void handleSearchQueryInput(Player player, String message, ChatInputContext context) {
        chatInputContexts.remove(player.getUniqueId());

        MarketSession session = getOrCreateSession(player.getUniqueId());
        session.setSearchQuery(message);
        session.setCurrentPage(0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage(ChatColor.GREEN + "Search set to: " + ChatColor.WHITE + message);
            openMarketMenu(player);
        });
    }

    /**
     * Handle transaction results with appropriate messaging
     */
    private void handleTransactionResult(Player player, TransactionResult result) {
        switch (result) {
            case SUCCESS:
                // Already handled in individual methods
                break;
            case INSUFFICIENT_FUNDS:
                player.sendMessage(ChatColor.RED + "✗ You don't have enough gems!");
                break;
            case ITEM_NOT_FOUND:
                player.sendMessage(ChatColor.RED + "✗ Item not found!");
                break;
            case ITEM_EXPIRED:
                player.sendMessage(ChatColor.RED + "✗ This item has expired!");
                break;
            case INVENTORY_FULL:
                player.sendMessage(ChatColor.RED + "✗ Your inventory is full!");
                break;
            case PERMISSION_DENIED:
                player.sendMessage(ChatColor.RED + "✗ You don't have permission to do that!");
                break;
            case COOLDOWN_ACTIVE:
                player.sendMessage(ChatColor.RED + "✗ Please wait before trying again!");
                break;
            case INVALID_ITEM:
                player.sendMessage(ChatColor.RED + "✗ Invalid item or price!");
                break;
            case LISTING_LIMIT_REACHED:
                player.sendMessage(ChatColor.RED + "✗ You've reached your daily listing limit!");
                break;
            case PLAYER_OFFLINE:
                player.sendMessage(ChatColor.RED + "✗ Player data could not be loaded!");
                break;
            case DATABASE_ERROR:
                player.sendMessage(ChatColor.RED + "✗ Database error occurred. Please try again!");
                break;
        }
    }

    /**
     * Validation methods
     */
    private TransactionResult validateListing(Player player, ItemStack item, int price) {
        if (!canUseMarket(player)) {
            return TransactionResult.PERMISSION_DENIED;
        }

        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return TransactionResult.INVALID_ITEM;
        }

        if (bannedMaterials.contains(item.getType())) {
            player.sendMessage(ChatColor.RED + "This item cannot be sold on the market.");
            return TransactionResult.INVALID_ITEM;
        }

        if (price < minItemPrice || price > maxItemPrice) {
            return TransactionResult.INVALID_ITEM;
        }

        if (getDailyListings(player.getUniqueId()) >= maxListingsPerPlayer) {
            return TransactionResult.LISTING_LIMIT_REACHED;
        }

        // Check cooldown
        Long lastTransaction = lastTransactionTime.get(player.getUniqueId());
        if (lastTransaction != null) {
            long timeSince = System.currentTimeMillis() - lastTransaction;
            if (timeSince < TimeUnit.SECONDS.toMillis(3)) {
                return TransactionResult.COOLDOWN_ACTIVE;
            }
        }

        return TransactionResult.SUCCESS;
    }

    private boolean canUseMarket(Player player) {
        if (!player.hasPermission("yakserver.market.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the market.");
            return false;
        }

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer != null && yakPlayer.getLevel() < minLevelToUse) {
            player.sendMessage(ChatColor.RED + "You must be level " + minLevelToUse + " to use the market.");
            return false;
        }

        return true;
    }

    /**
     * Utility methods
     */
    private boolean removeItemFromInventory(Player player, ItemStack item) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack slotItem = player.getInventory().getItem(slot);
            if (slotItem != null && slotItem.isSimilar(item) && slotItem.getAmount() >= item.getAmount()) {
                if (slotItem.getAmount() == item.getAmount()) {
                    player.getInventory().setItem(slot, null);
                } else {
                    slotItem.setAmount(slotItem.getAmount() - item.getAmount());
                }
                return true;
            }
        }
        return false;
    }

    private boolean hasInventorySpace(Player player, ItemStack item) {
        // Check if player has space for the item
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());

        // Remove the test item
        player.getInventory().removeItem(item.clone());

        return leftover.isEmpty();
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return TextUtil.formatItemName(item.getType().name());
    }

    public MarketSession getOrCreateSession(UUID playerId) {
        return activeSessions.computeIfAbsent(playerId, MarketSession::new);
    }

    private int getDailyListings(UUID playerId) {
        return dailyListings.getOrDefault(playerId, 0);
    }

    private void incrementDailyListings(UUID playerId) {
        dailyListings.merge(playerId, 1, Integer::sum);
        lastDailyReset.put(playerId, LocalDate.now());
    }

    // Event handlers
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        activeSessions.put(event.getPlayer().getUniqueId(),
                new MarketSession(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        activeSessions.remove(playerId);
        chatInputContexts.remove(playerId);
    }

    // Getters
    public MarketRepository getRepository() { return repository; }
    public int getMaxListingsPerPlayer() { return maxListingsPerPlayer; }
    public int getMinItemPrice() { return minItemPrice; }
    public int getMaxItemPrice() { return maxItemPrice; }
    public double getMarketTaxRate() { return marketTaxRate; }
    public int getFeaturedListingCost() { return featuredListingCost; }
    public Set<Material> getBannedMaterials() { return new HashSet<>(bannedMaterials); }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", activeSessions.size());
        stats.put("totalTransactions", totalTransactions);
        stats.put("totalGemsTraded", totalGemsTraded);
        stats.put("totalListings", totalListings);
        stats.putAll(repository.getPerformanceStats());
        return stats;
    }
}
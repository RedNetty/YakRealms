package com.rednetty.server.mechanics.economy;

import com.rednetty.server.YakRealms;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages bank inventories and transactions
 */
public class BankManager implements Listener {
    // Constants
    public static final int BANK_SIZE = 54;
    public static final int BANK_CONTENT_SIZE = BANK_SIZE - 9; // Usable area excluding bottom row
    public static final String BANK_TITLE_PREFIX = "Bank Chest (";
    public static final String BANK_TITLE_SUFFIX = "/1)";
    private static BankManager instance;
    private final Logger logger;
    // Cache for bank inventories
    private final Map<UUID, Map<Integer, Inventory>> playerBanks = new ConcurrentHashMap<>();

    // Players in withdraw prompts
    private final Set<UUID> withdrawPrompt = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Player viewing another player's bank
    private final Map<UUID, UUID> bankViewMap = new ConcurrentHashMap<>();

    /**
     * Private constructor for singleton pattern
     */
    private BankManager() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The BankManager instance
     */
    public static BankManager getInstance() {
        if (instance == null) {
            instance = new BankManager();
        }
        return instance;
    }

    /**
     * Initialize the bank system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        logger.info("Bank system has been enabled");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        saveBanks();
        logger.info("Bank system has been disabled");
    }

    /**
     * Save all banks to persistent storage
     */
    private void saveBanks() {
        try {
            logger.info("Saving " + playerBanks.size() + " banks...");

            for (Map.Entry<UUID, Map<Integer, Inventory>> entry : playerBanks.entrySet()) {
                UUID playerUuid = entry.getKey();
                Map<Integer, Inventory> banks = entry.getValue();

                for (Map.Entry<Integer, Inventory> bankEntry : banks.entrySet()) {
                    saveBank(bankEntry.getValue(), playerUuid, bankEntry.getKey());
                }
            }

            playerBanks.clear();
            logger.info("All banks saved successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving banks", e);
        }
    }

    /**
     * Save a specific bank inventory
     *
     * @param inventory  The bank inventory
     * @param playerUuid The player's UUID
     * @param page       The bank page
     */
    private void saveBank(Inventory inventory, UUID playerUuid, int page) {
        try {
            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(playerUuid);

            if (yakPlayer != null) {
                // Serialize bank inventory contents
                String serializedItems = serializeInventory(inventory);
                yakPlayer.setSerializedBankItems(page, serializedItems);

                // Update bank balance from UI item
                ItemStack balanceItem = inventory.getItem(BANK_SIZE - 5);
                if (balanceItem != null && balanceItem.getType() == Material.EMERALD) {
                    // Extract bank balance
                    String displayName = balanceItem.getItemMeta().getDisplayName();
                    try {
                        String balanceStr = ChatColor.stripColor(displayName).split(" ")[0];
                        int bankBalance = Integer.parseInt(balanceStr);
                        yakPlayer.setBankGems(bankBalance);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error parsing bank balance from item", e);
                    }
                }

                // Save player data
                YakPlayerManager.getInstance().savePlayer(yakPlayer);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving bank for player " + playerUuid, e);
        }
    }

    /**
     * Serialize an inventory to Base64 string
     */
    private String serializeInventory(Inventory inventory) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Only serialize the bank content area (not UI elements)
            dataOutput.writeInt(BANK_CONTENT_SIZE);

            for (int i = 0; i < BANK_CONTENT_SIZE; i++) {
                dataOutput.writeObject(inventory.getItem(i));
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error serializing bank inventory", e);
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

            for (int i = 0; i < size; i++) {
                if (i < BANK_CONTENT_SIZE) {
                    inventory.setItem(i, (ItemStack) dataInput.readObject());
                } else {
                    // Skip extra items if somehow size is larger than BANK_CONTENT_SIZE
                    dataInput.readObject();
                }
            }

            dataInput.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deserializing bank inventory", e);
        }
    }

    /**
     * Get a player's bank inventory
     *
     * @param player The player
     * @param page   The bank page
     * @return The bank inventory
     */
    public Inventory getBank(Player player, int page) {
        UUID viewerUuid = player.getUniqueId();
        UUID targetUuid = bankViewMap.getOrDefault(viewerUuid, viewerUuid);

        // Get or create page map for this player
        Map<Integer, Inventory> playerBankPages = playerBanks.computeIfAbsent(targetUuid, k -> new ConcurrentHashMap<>());

        // Check if we already have this bank page cached
        Inventory cachedBank = playerBankPages.get(page);
        if (cachedBank != null) {
            return cachedBank;
        }

        // Create new bank inventory
        String title = BANK_TITLE_PREFIX + page + BANK_TITLE_SUFFIX;
        Inventory bankInv = Bukkit.createInventory(null, BANK_SIZE, title);

        // Load bank contents from YakPlayer if available
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(targetUuid);
        if (yakPlayer != null) {
            String serializedItems = yakPlayer.getSerializedBankItems(page);
            if (serializedItems != null && !serializedItems.isEmpty()) {
                deserializeInventory(serializedItems, bankInv);
            }
        }

        // Initialize UI elements
        initializeBankInventory(bankInv, player);

        // Cache and return
        playerBankPages.put(page, bankInv);
        return bankInv;
    }

    /**
     * Initialize a bank inventory with UI elements
     *
     * @param inventory The inventory to initialize
     * @param player    The player whose bank it is
     */
    private void initializeBankInventory(Inventory inventory, Player player) {
        // Bottom row border
        ItemStack glass = createItem(Material.GLASS_PANE, " ", null);
        for (int i = BANK_CONTENT_SIZE; i < BANK_SIZE; i++) {
            inventory.setItem(i, glass);
        }

        // Navigation buttons
        inventory.setItem(BANK_SIZE - 9, createItem(Material.ARROW, ChatColor.GREEN + "Previous Page", null));
        inventory.setItem(BANK_SIZE - 1, createItem(Material.ARROW, ChatColor.GREEN + "Next Page", null));

        // Gem balance display
        inventory.setItem(BANK_SIZE - 5, createGemBankItem(player));
    }

    /**
     * Create the gem balance indicator for the bank
     *
     * @param player The player
     * @return The gem balance ItemStack
     */
    private ItemStack createGemBankItem(Player player) {
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        int balance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

        return createItem(Material.EMERALD, ChatColor.GREEN.toString() + balance + ChatColor.GREEN + ChatColor.BOLD + " GEM(s)", Collections.singletonList(ChatColor.GRAY + "Right Click to create " + ChatColor.GREEN + "A GEM NOTE"));
    }

    /**
     * Create a bank note item
     *
     * @param amount The gem amount
     * @return The bank note ItemStack
     */
    public ItemStack createBankNote(int amount) {
        ItemStack note = new ItemStack(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Bank Note");
        meta.setLore(Arrays.asList(ChatColor.WHITE.toString() + ChatColor.BOLD + "Value: " + ChatColor.WHITE + amount + " Gems", ChatColor.GRAY + "Exchange at any bank for GEM(s)"));
        note.setItemMeta(meta);
        return note;
    }

    /* Event Handlers */

    /**
     * Handle right-clicking on an ender chest to open the bank
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock().getType() != Material.ENDER_CHEST) {
            return;
        }

        Player player = event.getPlayer();

        // Prevent default chest opening
        event.setCancelled(true);

        // Don't reopen if already viewing bank
        if (player.getOpenInventory().getTitle().contains(BANK_TITLE_PREFIX)) {
            return;
        }

        // Open bank inventory
        try {
            Inventory bankInv = getBank(player, 1);
            player.openInventory(bankInv);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "There was an error opening your bank.");
            logger.log(Level.SEVERE, "Error opening bank for player " + player.getName(), e);
        }
    }

    /**
     * Handle inventory open event to update the bank UI
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        if (title.contains(BANK_TITLE_PREFIX) && !title.contains("Guild")) {
            updateBankUI(inventory, player);
        }
    }

    /**
     * Update the bank UI elements
     */
    private void updateBankUI(Inventory inventory, Player player) {
        // Update the gem balance display
        inventory.setItem(BANK_SIZE - 5, createGemBankItem(player));
    }

    /**
     * Handle inventory close event to save the bank
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (title.contains(BANK_TITLE_PREFIX) && !title.contains("Guild")) {
            Player player = (Player) event.getPlayer();

            try {
                int page = Integer.parseInt(title.substring(title.indexOf("(") + 1, title.indexOf("/")));

                // Use async task to save the bank without blocking the main thread
                Bukkit.getScheduler().runTaskAsynchronously(YakRealms.getInstance(), () -> {
                    YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
                    if (yakPlayer != null) {
                        YakPlayerManager.getInstance().savePlayer(yakPlayer);
                    }
                });

                // Remove from viewing map
                bankViewMap.remove(player.getUniqueId());
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error saving bank on close for player " + player.getName(), e);
            }
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

        // Only proceed for bank inventories
        if (!title.contains(BANK_TITLE_PREFIX) || title.contains("Guild")) {
            return;
        }

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
            handleBankUIClick(event, player);
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
                player.sendMessage(ChatColor.RED + "Your bank is full. Unable to deposit items.");
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
     * Handle clicks on the bank UI elements (bottom row)
     */
    private void handleBankUIClick(InventoryClickEvent event, Player player) {
        int slot = event.getSlot();

        // Gem balance - open withdrawal prompt
        if (slot == BANK_SIZE - 5 && event.getClick() == ClickType.RIGHT) {
            promptForWithdraw(player);
        }
        // Previous page
        else if (slot == BANK_SIZE - 9) {
            changeBankPage(player, -1);
        }
        // Next page
        else if (slot == BANK_SIZE - 1) {
            changeBankPage(player, 1);
        }
    }

    /**
     * Change to a different bank page
     */
    private void changeBankPage(Player player, int delta) {
        try {
            String title = player.getOpenInventory().getTitle();
            int currentPage = Integer.parseInt(title.substring(title.indexOf("(") + 1, title.indexOf("/")));
            int newPage = currentPage + delta;

            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);

            // Check if player has access to this page
            int maxPages = yakPlayer != null ? yakPlayer.getBankPages() : 1;

            if (newPage < 1 || newPage > maxPages) {
                player.sendMessage(ChatColor.RED + "You do not have access to that bank page.");
                return;
            }

            // Save current page before switching
            int finalCurrentPage = currentPage;
            Bukkit.getScheduler().runTaskAsynchronously(YakRealms.getInstance(), () -> saveBank(player.getOpenInventory().getTopInventory(), player.getUniqueId(), finalCurrentPage));

            // Open new page
            player.closeInventory();
            player.openInventory(getBank(player, newPage));
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.25f);

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Could not change bank page.");
            logger.log(Level.WARNING, "Error changing bank page for player " + player.getName(), e);
        }
    }

    /**
     * Process the deposit of currency items
     */
    private void processCurrencyDeposit(Player player, ItemStack item) {
        int totalAmount = 0;

        try {
            if (item.getType() == Material.EMERALD) {
                // Regular gems
                totalAmount = item.getAmount();
                player.getInventory().removeItem(item);
            } else if (item.getType() == Material.PAPER && isBankNote(item)) {
                // Bank note
                totalAmount = extractGemValue(item);
                player.getInventory().removeItem(item);
            } else if (item.getType() == Material.INK_SAC) {
                // Gem pouch
                GemPouchManager pouchManager = GemPouchManager.getInstance();
                if (pouchManager.isGemPouch(item)) {
                    totalAmount = pouchManager.getCurrentValue(item);
                    pouchManager.setPouchValue(item, 0);
                }
            }

            if (totalAmount > 0) {
                // Add to player's bank balance
                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
                if (yakPlayer != null) {
                    yakPlayer.setBankGems(yakPlayer.getBankGems() + totalAmount);
                    YakPlayerManager.getInstance().savePlayer(yakPlayer);

                    // Update display
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

        // Check if player has a balance
        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        int balance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

        if (balance <= 0) {
            withdrawPrompt.remove(player.getUniqueId());
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "Your bank balance is zero. Withdrawal cannot be processed.");
            return;
        }

        // Parse amount
        try {
            int amount = Integer.parseInt(message);
            // Schedule processing on the main thread for safety
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> processWithdraw(player, amount));
        } catch (NumberFormatException e) {
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "Please enter a NUMBER, the amount you'd like to WITHDRAW " + "from your bank account. Or type 'cancel' to void the withdrawal.");
        }
    }

    /**
     * Process the withdrawal request
     */
    private void processWithdraw(Player player, int amount) {
        if (!withdrawPrompt.contains(player.getUniqueId())) {
            return; // Player may have disconnected or operation was cancelled
        }

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        int balance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

        if (amount <= 0) {
            TextUtil.sendCenteredMessage(player, ChatColor.RED + "You must enter a POSITIVE amount.");
        } else if (amount > balance) {
            TextUtil.sendCenteredMessage(player, ChatColor.GRAY + "You cannot withdraw more GEMS than you have stored. " + "Current balance: " + balance + " GEM(s)");
        } else {
            withdrawPrompt.remove(player.getUniqueId());

            // Process the withdrawal
            if (yakPlayer != null) {
                yakPlayer.setBankGems(balance - amount);
                YakPlayerManager.getInstance().savePlayer(yakPlayer);

                // Give bank note to player
                givePlayerBankNote(player, amount);

                // Update display
                updatePlayerBalance(player, amount, false);
            }
        }
    }

    /**
     * Give a bank note to a player
     */
    private void givePlayerBankNote(Player player, int amount) {
        ItemStack bankNote = createBankNote(amount);

        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(bankNote);
        } else {
            // Drop at player's feet if inventory is full
            player.getWorld().dropItemNaturally(player.getLocation(), bankNote);
            player.sendMessage(ChatColor.YELLOW + "Your inventory was full. The bank note was dropped at your feet.");
        }
    }

    /**
     * Update the player's balance and display
     */
    private void updatePlayerBalance(Player player, int amount, boolean isDeposit) {
        try {
            // Display messages
            String prefix = isDeposit ? "&a+" : "&c-";
            TextUtil.sendCenteredMessage(player, TextUtil.colorize(prefix + amount + (isDeposit ? "&a&lG" : "&c&lG") + " &7➜ " + (isDeposit ? "Your Bank" : "Your Inventory")));

            YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
            int newBalance = yakPlayer != null ? yakPlayer.getBankGems() : 0;

            TextUtil.sendCenteredMessage(player, TextUtil.colorize("&a&lNew Balance: &a" + newBalance + " &aGEM(s)"));

            // Play sound effect
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // Update bank UI if open
            if (player.getOpenInventory().getTitle().contains(BANK_TITLE_PREFIX)) {
                player.getOpenInventory().setItem(BANK_SIZE - 5, createGemBankItem(player));
            }

            // Update inventory
            player.updateInventory();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error updating player balance for " + player.getName(), e);
        }
    }

    /* Utility Methods */

    /**
     * Check if an item is a currency item
     */
    private boolean isCurrencyItem(ItemStack item) {
        if (item == null) return false;

        return item.getType() == Material.EMERALD || isBankNote(item) || (item.getType() == Material.INK_SAC && GemPouchManager.getInstance().isGemPouch(item));
    }

    /**
     * Check if an item is a bank note
     */
    private boolean isBankNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.GREEN + "Bank Note") && meta.hasLore() && !meta.getLore().isEmpty() && meta.getLore().get(0).contains("Value");
    }

    /**
     * Extract the gem value from a bank note
     */
    private int extractGemValue(ItemStack item) {
        if (!isBankNote(item)) return 0;

        try {
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

    /**
     * Create an ItemStack with custom name and lore
     */
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

    /**
     * Create a gem item with specified amount
     */
    public ItemStack createGems(int amount) {
        if (amount <= 0) return null;

        ItemStack gem = new ItemStack(Material.EMERALD, Math.min(amount, 64)); // Cap at stack size

        ItemMeta meta = gem.getItemMeta();
        meta.setDisplayName(ChatColor.WHITE + "Gem");
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "The currency of Andalucia"));

        gem.setItemMeta(meta);
        return gem;
    }
}
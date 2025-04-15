package com.rednetty.server.mechanics.player.settings;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.moderation.Rank;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manages player toggle preferences and provides a GUI for changing them
 */
public class Toggles implements Listener {
    private static Toggles instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Confirmation map for sensitive toggles
    private final Map<UUID, String> confirmationMap = new HashMap<>();

    // Expiry time for confirmations (in milliseconds)
    private final long CONFIRMATION_EXPIRY = 10000; // 10 seconds
    private final Map<UUID, Long> confirmationTimestamps = new HashMap<>();

    // Toggle categories
    private static final String CATEGORY_COMBAT = "Combat";
    private static final String CATEGORY_DISPLAY = "Display";
    private static final String CATEGORY_SYSTEM = "System";
    private static final String CATEGORY_SOCIAL = "Social";

    // Map of toggle names to their categories
    private final Map<String, String> toggleCategories = new HashMap<>();

    // Map of toggle names to their required permissions (null = no permission required)
    private final Map<String, String> togglePermissions = new HashMap<>();

    // Map of toggle names to whether they require confirmation
    private final Set<String> sensitiveToggles = new HashSet<>();

    /**
     * Private constructor for singleton pattern
     */
    private Toggles() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();

        // Initialize toggle categories
        initializeToggleCategories();

        // Initialize toggle permissions
        initializeTogglePermissions();

        // Initialize sensitive toggles
        initializeSensitiveToggles();
    }

    /**
     * Sets up categories for all toggles
     */
    private void initializeToggleCategories() {
        // Combat toggles
        toggleCategories.put("Anti PVP", CATEGORY_COMBAT);
        toggleCategories.put("Friendly Fire", CATEGORY_COMBAT);
        toggleCategories.put("Chaotic", CATEGORY_COMBAT);

        // Display toggles
        toggleCategories.put("Hologram Damage", CATEGORY_DISPLAY);
        toggleCategories.put("Debug", CATEGORY_DISPLAY);
        toggleCategories.put("Particles", CATEGORY_DISPLAY);

        // System toggles
        toggleCategories.put("Energy Disabled", CATEGORY_SYSTEM);
        toggleCategories.put("Drop Protection", CATEGORY_SYSTEM);
        toggleCategories.put("Auto Bank", CATEGORY_SYSTEM);

        // Social toggles
        toggleCategories.put("Trading", CATEGORY_SOCIAL);
    }

    /**
     * Sets up permissions required for toggles
     */
    private void initializeTogglePermissions() {
        // Standard toggles - no special permissions
        togglePermissions.put("Anti PVP", null);
        togglePermissions.put("Friendly Fire", null);
        togglePermissions.put("Hologram Damage", null);
        togglePermissions.put("Debug", null);
        togglePermissions.put("Trading", null);
        togglePermissions.put("Drop Protection", null);
        togglePermissions.put("Chaotic", null);

        // Premium toggles
        togglePermissions.put("Particles", "yakserver.donator");
        togglePermissions.put("Auto Bank", "yakserver.donator.tier2");

        // Admin toggles
        togglePermissions.put("Energy Disabled", "yakserver.admin");
    }

    /**
     * Sets up which toggles require confirmation
     */
    private void initializeSensitiveToggles() {
        sensitiveToggles.add("Energy Disabled");
        sensitiveToggles.add("Chaotic");
    }

    /**
     * Gets the singleton instance
     *
     * @return The Toggles instance
     */
    public static Toggles getInstance() {
        if (instance == null) {
            instance = new Toggles();
        }
        return instance;
    }

    /**
     * Initialize the toggle system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("Toggles system has been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        confirmationMap.clear();
        confirmationTimestamps.clear();
        YakRealms.log("Toggles system has been disabled.");
    }

    /**
     * Check if a toggle is enabled for a player
     *
     * @param player The player to check
     * @param toggle The toggle name
     * @return true if the toggle is enabled
     */
    public static boolean isToggled(Player player, String toggle) {
        YakPlayer yakPlayer = getInstance().playerManager.getPlayer(player);
        return yakPlayer != null && yakPlayer.isToggled(toggle);
    }

    /**
     * Set a toggle state for a player
     *
     * @param player  The player to update
     * @param toggle  The toggle name
     * @param enabled The new state
     * @return true if the toggle was changed, false if not authorized
     */
    public static boolean setToggle(Player player, String toggle, boolean enabled) {
        Toggles toggles = getInstance();

        // Check permission
        String permission = toggles.togglePermissions.get(toggle);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the " + toggle + " toggle.");
            return false;
        }

        YakPlayer yakPlayer = toggles.playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        if (enabled && !yakPlayer.isToggled(toggle)) {
            yakPlayer.toggleSetting(toggle);
        } else if (!enabled && yakPlayer.isToggled(toggle)) {
            yakPlayer.toggleSetting(toggle);
        }

        toggles.playerManager.savePlayer(yakPlayer);
        return true;
    }

    /**
     * Change a toggle state (flip its current value)
     *
     * @param player The player to update
     * @param toggle The toggle to flip
     * @return true if the toggle was changed or confirmation is required, false if not authorized
     */
    public boolean changeToggle(Player player, String toggle) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        // Check permission
        String permission = togglePermissions.get(toggle);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the " + toggle + " toggle.");
            return false;
        }

        // Check if this requires confirmation
        if (sensitiveToggles.contains(toggle)) {
            UUID uuid = player.getUniqueId();

            // If player has a pending confirmation for this toggle
            if (confirmationMap.containsKey(uuid) && confirmationMap.get(uuid).equals(toggle)) {
                // Check if confirmation has expired
                Long timestamp = confirmationTimestamps.get(uuid);
                if (timestamp != null && System.currentTimeMillis() - timestamp > CONFIRMATION_EXPIRY) {
                    confirmationMap.remove(uuid);
                    confirmationTimestamps.remove(uuid);

                    player.sendMessage(ChatColor.YELLOW + "Confirmation expired. Click again to toggle " + toggle + ".");
                    return true;
                }

                // Execute the toggle since it's confirmed
                boolean currentState = yakPlayer.isToggled(toggle);
                yakPlayer.toggleSetting(toggle);
                playerManager.savePlayer(yakPlayer);

                boolean newState = !currentState;

                // Clear the confirmation
                confirmationMap.remove(uuid);
                confirmationTimestamps.remove(uuid);

                // Send feedback
                player.sendMessage((newState ? ChatColor.GREEN : ChatColor.RED) + ChatColor.BOLD.toString() +
                        "Toggle " + toggle + " - " + (newState ? "Enabled!" : "Disabled!"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

                return true;
            } else {
                // Require confirmation
                confirmationMap.put(uuid, toggle);
                confirmationTimestamps.put(uuid, System.currentTimeMillis());

                player.sendMessage(ChatColor.YELLOW + "This is a sensitive toggle. Click again to confirm changing " + toggle + ".");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);

                return true;
            }
        }

        // Standard toggle (no confirmation needed)
        boolean currentState = yakPlayer.isToggled(toggle);
        yakPlayer.toggleSetting(toggle);
        playerManager.savePlayer(yakPlayer);

        boolean newState = !currentState;

        // Send feedback
        player.sendMessage((newState ? ChatColor.GREEN : ChatColor.RED) + ChatColor.BOLD.toString() +
                "Toggle " + toggle + " - " + (newState ? "Enabled!" : "Disabled!"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

        return true;
    }

    /**
     * Get a player's toggle menu
     *
     * @param player The player to create menu for
     * @return The toggle menu inventory
     */
    public Inventory getToggleMenu(Player player) {
        // Create a larger inventory with 45 slots (5 rows) to accommodate categories
        Inventory inventory = Bukkit.createInventory(null, 45, "Toggle Settings");
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return inventory;

        // Get player's rank for permission-based toggles
        Rank rank = ModerationMechanics.rankMap.getOrDefault(player.getUniqueId(), Rank.DEFAULT);
        boolean isDonator = (rank != Rank.DEFAULT);
        boolean isHighTierDonator = (rank == Rank.SUB2 || rank == Rank.SUB3 || rank == Rank.SUPPORTER ||
                rank == Rank.YOUTUBER || ModerationMechanics.isStaff(player));
        boolean isAdmin = ModerationMechanics.isStaff(player);

        // Add category headers
        addCategoryHeader(inventory, CATEGORY_COMBAT, 0);
        addCategoryHeader(inventory, CATEGORY_DISPLAY, 9);
        addCategoryHeader(inventory, CATEGORY_SYSTEM, 18);
        addCategoryHeader(inventory, CATEGORY_SOCIAL, 27);

        // Combat toggles
        addToggle(inventory, "Anti PVP", yakPlayer, 1, "Prevents you from dealing damage to other players");
        addToggle(inventory, "Friendly Fire", yakPlayer, 2, "Allows damaging buddies and guild members");
        addToggle(inventory, "Chaotic", yakPlayer, 3, "Prevents accidentally attacking lawful players");

        // Display toggles
        addToggle(inventory, "Hologram Damage", yakPlayer, 10, "Shows damage numbers in combat");
        addToggle(inventory, "Debug", yakPlayer, 11, "Shows extra combat information");

        if (isDonator || player.hasPermission("yakserver.donator")) {
            addToggle(inventory, "Particles", yakPlayer, 12, "Displays special particle effects");
        } else {
            addLockedToggle(inventory, "Particles", 12, "Requires Donator rank");
        }

        // System toggles
        addToggle(inventory, "Drop Protection", yakPlayer, 19, "Protects dropped items for a few seconds");

        if (isHighTierDonator || player.hasPermission("yakserver.donator.tier2")) {
            addToggle(inventory, "Auto Bank", yakPlayer, 20, "Automatically deposits gems in your bank");
        } else {
            addLockedToggle(inventory, "Auto Bank", 20, "Requires SUB2+ rank");
        }

        if (isAdmin || player.hasPermission("yakserver.admin")) {
            addToggle(inventory, "Energy Disabled", yakPlayer, 21, "Disables the energy system");
        }

        // Social toggles
        addToggle(inventory, "Trading", yakPlayer, 28, "Allows other players to trade with you");

        // Add information button
        addInfoButton(inventory, 44);

        return inventory;
    }

    /**
     * Add a category header to the inventory
     *
     * @param inventory The inventory to add to
     * @param category  The category name
     * @param slot      The inventory slot
     */
    private void addCategoryHeader(Inventory inventory, String category, int slot) {
        ItemStack header = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
        ItemMeta meta = header.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + ChatColor.BOLD.toString() + category + " Toggles");
        header.setItemMeta(meta);
        inventory.setItem(slot, header);
    }

    /**
     * Add a toggle button to an inventory
     *
     * @param inventory   The inventory to add to
     * @param toggle      The toggle name
     * @param yakPlayer   The player's data
     * @param slot        The inventory slot
     * @param description The toggle description
     */
    private void addToggle(Inventory inventory, String toggle, YakPlayer yakPlayer, int slot, String description) {
        boolean isEnabled = yakPlayer.isToggled(toggle);
        boolean isSensitive = sensitiveToggles.contains(toggle);

        // Choose material based on state and sensitivity
        Material material;
        if (isEnabled) {
            material = isSensitive ? Material.LIME_CONCRETE : Material.LIME_DYE;
        } else {
            material = isSensitive ? Material.RED_CONCRETE : Material.GRAY_DYE;
        }

        ItemStack toggleItem = new ItemStack(material, 1);
        ItemMeta meta = toggleItem.getItemMeta();

        meta.setDisplayName((isEnabled ? ChatColor.GREEN : ChatColor.RED) + toggle);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + description);

        // Add category to lore
        String category = toggleCategories.getOrDefault(toggle, "Other");
        lore.add(ChatColor.YELLOW + "Category: " + category);

        // Add sensitivity warning if applicable
        if (isSensitive) {
            lore.add("");
            lore.add(ChatColor.RED + "This toggle requires confirmation to change");
        }

        meta.setLore(lore);
        toggleItem.setItemMeta(meta);
        inventory.setItem(slot, toggleItem);
    }

    /**
     * Add a locked toggle button to an inventory
     *
     * @param inventory  The inventory to add to
     * @param toggle     The toggle name
     * @param slot       The inventory slot
     * @param lockReason The reason the toggle is locked
     */
    private void addLockedToggle(Inventory inventory, String toggle, int slot, String lockReason) {
        ItemStack toggleItem = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = toggleItem.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_GRAY + toggle + " " + ChatColor.RED + "(Locked)");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + lockReason);

        // Add category to lore
        String category = toggleCategories.getOrDefault(toggle, "Other");
        lore.add(ChatColor.YELLOW + "Category: " + category);

        meta.setLore(lore);
        toggleItem.setItemMeta(meta);
        inventory.setItem(slot, toggleItem);
    }

    /**
     * Add an information button to the inventory
     *
     * @param inventory The inventory to add to
     * @param slot      The inventory slot
     */
    private void addInfoButton(Inventory inventory, int slot) {
        ItemStack infoItem = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = infoItem.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "Toggle Information");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Click a toggle to enable or disable it");
        lore.add(ChatColor.GRAY + "Green toggles are enabled");
        lore.add(ChatColor.GRAY + "Gray toggles are disabled");
        lore.add(ChatColor.GRAY + "Red barriers are locked toggles");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Some toggles require specific ranks");
        lore.add(ChatColor.YELLOW + "Some toggles require confirmation");

        meta.setLore(lore);
        infoItem.setItemMeta(meta);
        inventory.setItem(slot, infoItem);
    }

    /**
     * Handle clicks in the toggle menu
     */
    @EventHandler
    public void onToggleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals("Toggle Settings")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        // Check if it's a category header or info button
        if (clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                clickedItem.getType() == Material.BOOK) {
            return;
        }

        // Check if it's a locked toggle
        if (clickedItem.getType() == Material.BARRIER) {
            String name = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            name = name.replace(" (Locked)", "");

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
            player.sendMessage(ChatColor.RED + "You don't have access to the " + name + " toggle.");
            return;
        }

        // Process toggle click - accept any valid toggle material
        if (clickedItem.getType() == Material.LIME_DYE ||
                clickedItem.getType() == Material.GRAY_DYE ||
                clickedItem.getType() == Material.LIME_CONCRETE ||
                clickedItem.getType() == Material.RED_CONCRETE) {

            String toggleName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // Handle toggle change
            if (changeToggle(player, toggleName)) {
                // Update the inventory if toggle was changed or requires confirmation
                player.openInventory(getToggleMenu(player));
            }
        }
    }

    /**
     * Handle PVP based on toggle settings
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;
        if (event.getDamage() <= 0.0) return;

        Player damager = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        YakPlayer damagerData = playerManager.getPlayer(damager);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (damagerData == null || victimData == null) return;

        // Check for duels or other PVP exceptions
        // if (isDueling(damager, victim)) return;

        // Check various PVP-prevention scenarios
        if (damagerData.isToggled("Anti PVP")) {
            event.setCancelled(true);
            damager.sendMessage(ChatColor.RED + "You have PVP disabled (/toggle)");
            return;
        }

        // Check victim's trading toggle if applicable
        if (victimData.isToggled("Anti PVP")) {
            event.setCancelled(true);
            damager.sendMessage(ChatColor.RED + victim.getName() + " has PVP disabled");
            return;
        }

        // Friendly fire check (buddies)
        if (damagerData.isBuddy(victim.getName()) && !damagerData.isToggled("Friendly Fire")) {
            event.setCancelled(true);
            damager.sendMessage(ChatColor.RED + "You cannot attack your buddy. Enable Friendly Fire to do this.");
            return;
        }

        // Guild/party check
        if (isInSameGuild(damager, victim)) {
            if (!damagerData.isToggled("Friendly Fire")) {
                event.setCancelled(true);
                damager.sendMessage(ChatColor.RED + "You cannot attack your guild member. Enable Friendly Fire to do this.");
                return;
            }
        }

        if (isInSameParty(damager, victim)) {
            if (!damagerData.isToggled("Friendly Fire")) {
                event.setCancelled(true);
                damager.sendMessage(ChatColor.RED + "You cannot attack your party member. Enable Friendly Fire to do this.");
                return;
            }
        }

        // Chaotic protection (prevents attacking lawful players)
        if (damagerData.isToggled("Chaotic") && isLawfulPlayer(victim)) {
            event.setCancelled(true);
            damager.sendMessage(ChatColor.RED + "You have Chaotic protection enabled. Disable it to attack lawful players.");
            return;
        }
    }

    /**
     * Check if a player is lawful (non-chaotic)
     *
     * @param player The player to check
     * @return true if the player is lawful
     */
    private boolean isLawfulPlayer(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && "LAWFUL".equals(yakPlayer.getAlignment());
    }

    /**
     * Check if two players are in the same guild
     *
     * @param player1 The first player
     * @param player2 The second player
     * @return true if they're in the same guild
     */
    private boolean isInSameGuild(Player player1, Player player2) {
        YakPlayer yakPlayer1 = playerManager.getPlayer(player1);
        YakPlayer yakPlayer2 = playerManager.getPlayer(player2);

        if (yakPlayer1 == null || yakPlayer2 == null) return false;

        String guild1 = yakPlayer1.getGuildName();
        String guild2 = yakPlayer2.getGuildName();

        return guild1 != null && !guild1.isEmpty() && guild1.equals(guild2);
    }

    /**
     * Check if two players are in the same party
     *
     * @param player1 The first player
     * @param player2 The second player
     * @return true if they're in the same party
     */
    private boolean isInSameParty(Player player1, Player player2) {
        // This would integrate with your party system
        // For now, returning false
        return false;
    }

    /**
     * Initialize default toggles for new players
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer != null && yakPlayer.getToggleSettings().isEmpty()) {
            // Set default toggles for new players
            yakPlayer.toggleSetting("Hologram Damage"); // On by default
            yakPlayer.toggleSetting("Drop Protection"); // On by default
            yakPlayer.toggleSetting("Anti PVP"); // On by default for new players
            playerManager.savePlayer(yakPlayer);

            // Send welcome message
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.GREEN + "Default toggles have been set. Use /toggle to customize your settings.");
                }
            }, 60L); // 3 seconds later
        }
    }

    /**
     * Command to open the toggle menu
     *
     * @param player The player to show the menu to
     */
    public void openToggleMenu(Player player) {
        player.openInventory(getToggleMenu(player));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f);
    }

    /**
     * Get all available toggles for a player
     *
     * @param player The player to check
     * @return A list of toggle names
     */
    public List<String> getAvailableToggles(Player player) {
        List<String> available = new ArrayList<>();

        for (String toggle : toggleCategories.keySet()) {
            String permission = togglePermissions.get(toggle);
            if (permission == null || player.hasPermission(permission)) {
                available.add(toggle);
            }
        }

        return available;
    }

    /**
     * Check if a player has permission to use a toggle
     *
     * @param player The player to check
     * @param toggle The toggle name
     * @return true if the player can use the toggle
     */
    public boolean canUseToggle(Player player, String toggle) {
        String permission = togglePermissions.get(toggle);
        return permission == null || player.hasPermission(permission);
    }
}
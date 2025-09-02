package com.rednetty.server.core.mechanics.player.settings;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.social.party.PartyMechanics;
import org.bukkit.*;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * UPDATED:  toggle system with proper initialization, debugging, validation, and simplified PVP handling.
 */
public class Toggles implements Listener {
    private static Toggles instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    //  confirmation system
    private final Map<UUID, PendingConfirmation> confirmationMap = new ConcurrentHashMap<>();

    
    private final long CONFIRMATION_EXPIRY = 15000; // 15 seconds
    private final int MENU_SIZE = 54; // 6 rows
    private final int USABLE_SLOTS = 45; // Reserve bottom row (slots 0-44)
    private final int INFO_SLOT = 53; // Bottom right corner
    private final int MAX_TOGGLES_PER_ROW = 7; // Limit toggles per row

    
    private final Map<String, ToggleDefinition> toggleDefinitions = new LinkedHashMap<>();
    private boolean definitionsInitialized = false;

    /**
     *  confirmation tracking
     */
    private static class PendingConfirmation {
        final String toggleName;
        final long timestamp;
        final boolean originalState;

        PendingConfirmation(String toggleName, boolean originalState) {
            this.toggleName = toggleName;
            this.originalState = originalState;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long expiryTime) {
            return System.currentTimeMillis() - timestamp > expiryTime;
        }
    }

    /**
     *  toggle definition with metadata
     */
    public static class ToggleDefinition {
        public final String name;
        public final String category;
        public final String description;
        public final String permission;
        public final boolean requiresConfirmation;
        public final boolean defaultValue;
        public final Material iconMaterial;
        public final String iconName;
        public final List<String> loreLines;

        public ToggleDefinition(String name, String category, String description, String permission,
                                boolean requiresConfirmation, boolean defaultValue, Material iconMaterial) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.permission = permission;
            this.requiresConfirmation = requiresConfirmation;
            this.defaultValue = defaultValue;
            this.iconMaterial = iconMaterial;
            this.iconName = formatToggleName(name);
            this.loreLines = createLoreLines(description, requiresConfirmation);
        }

        private String formatToggleName(String name) {
            return "§f§l" + name;
        }

        private List<String> createLoreLines(String description, boolean requiresConfirmation) {
            List<String> lore = new ArrayList<>();
            lore.add("§7" + description);
            lore.add("");

            if (requiresConfirmation) {
                lore.add("§c§l⚠ §cRequires Confirmation");
                lore.add("§7Click twice to change this setting");
            }

            return lore;
        }
    }

    private Toggles() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();

        
        initializeToggleDefinitions();
    }

    /**
     *  Initialize all toggle definitions with  validation
     */
    private void initializeToggleDefinitions() {
        // Initializing toggle definitions...

        try {
            // Clear existing definitions
            toggleDefinitions.clear();

            // Combat toggles
            addToggleDefinition("Anti PVP", "Combat", "Prevents you from dealing damage to other players",
                    null, false, true, Material.IRON_SWORD);
            addToggleDefinition("Friendly Fire", "Combat", "Allows damaging buddies and guild members",
                    null, false, false, Material.DIAMOND_SWORD);
            addToggleDefinition("Chaotic Protection", "Combat", "Prevents accidentally attacking lawful players",
                    null, true, false, Material.SHIELD);

            // Display toggles
            addToggleDefinition("Hologram Damage", "Display", "Shows floating damage numbers in combat",
                    null, false, true, Material.NAME_TAG);
            addToggleDefinition("Debug", "Display", "Shows detailed combat and system information",
                    null, false, false, Material.REDSTONE);
            addToggleDefinition("Trail Effects", "Display", "Displays special particle trail effects",
                    "yakrealms.donator", false, false, Material.FIREWORK_ROCKET);
            addToggleDefinition("Particles", "Display", "Shows various particle effects",
                    "yakrealms.donator", false, false, Material.BLAZE_POWDER);
            addToggleDefinition("Glowing Drops", "Display", "Makes dropped items glow with colors based on rarity",
                    null, false, false, Material.GLOWSTONE_DUST);
            // System toggles
            addToggleDefinition("Drop Protection", "System", "Protects your dropped items for 5 seconds",
                    null, false, true, Material.CHEST);
            addToggleDefinition("Auto Bank", "System", "Automatically deposits gems into your bank",
                    "yakrealms.donator.tier2", false, false, Material.GOLD_INGOT);
            addToggleDefinition("Disable Kit", "System", "Prevents receiving starter kits on respawn",
                    null, false, false, Material.LEATHER_CHESTPLATE);

            // Social toggles
            addToggleDefinition("Trading", "Social", "Allows other players to send you trade requests",
                    null, false, true, Material.EMERALD);
            addToggleDefinition("Player Messages", "Social", "Receive private messages from other players",
                    null, false, true, Material.PAPER);
            addToggleDefinition("Party Invites", "Social", "Receive party invitation requests",
                    null, false, true, Material.BLUE_BANNER);
            addToggleDefinition("Buddy Requests", "Social", "Receive buddy/friend requests",
                    null, false, true, Material.GOLDEN_APPLE);


            definitionsInitialized = true;
            // Successfully initialized " + toggleDefinitions.size() + " toggle definitions"

            
            logToggleDefinitions();

        } catch (Exception e) {
            logger.severe("Failed to initialize toggle definitions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     *  Debug method to log all toggle definitions
     */
    private void logToggleDefinitions() {
        logger.info("Toggle Definitions Summary:");
        Map<String, List<String>> categories = toggleDefinitions.values().stream()
                .collect(Collectors.groupingBy(def -> def.category,
                        Collectors.mapping(def -> def.name, Collectors.toList())));

        for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
            logger.info("  " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private void addToggleDefinition(String name, String category, String description, String permission,
                                     boolean requiresConfirmation, boolean defaultValue, Material iconMaterial) {
        if (name == null || category == null || description == null) {
            logger.warning("Invalid toggle definition parameters: name=" + name + ", category=" + category);
            return;
        }

        ToggleDefinition def = new ToggleDefinition(name, category, description, permission,
                requiresConfirmation, defaultValue, iconMaterial);
        toggleDefinitions.put(name, def);

        logger.fine("Added toggle definition: " + name + " in category " + category);
    }

    public static Toggles getInstance() {
        if (instance == null) {
            instance = new Toggles();
        }
        return instance;
    }

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());

        // Start cleanup task for expired confirmations
        Bukkit.getScheduler().runTaskTimerAsynchronously(YakRealms.getInstance(), () -> {
            cleanupExpiredConfirmations();
        }, 20L * 30, 20L * 30); // Every 30 seconds

        YakRealms.log("Toggles system enabled successfully");
    }

    public void onDisable() {
        confirmationMap.clear();
        YakRealms.log(" Toggles system has been disabled.");
    }

    /**
     * Clean up expired confirmations
     */
    private void cleanupExpiredConfirmations() {
        confirmationMap.entrySet().removeIf(entry ->
                entry.getValue().isExpired(CONFIRMATION_EXPIRY));
    }

    /**
     *   toggle checking with better validation
     */
    public static boolean isToggled(Player player, String toggle) {
        if (player == null || toggle == null) {
            return false;
        }

        Toggles toggles = getInstance();
        if (!toggles.definitionsInitialized) {
            toggles.logger.warning("Toggle definitions not initialized when checking: " + toggle);
            return false;
        }

        YakPlayer yakPlayer = toggles.playerManager.getPlayer(player);
        if (yakPlayer == null) {
            toggles.logger.fine("YakPlayer is null for " + player.getName() + " when checking toggle: " + toggle);
            return false;
        }

        // Check if toggle exists and get default value
        ToggleDefinition def = toggles.toggleDefinitions.get(toggle);
        if (def == null) {
            toggles.logger.warning("Unknown toggle requested: " + toggle);
            return false;
        }

        return yakPlayer.isToggled(toggle);
    }

    /**
     *   toggle setting with better validation
     */
    public static boolean setToggle(Player player, String toggle, boolean enabled) {
        if (player == null || toggle == null) {
            return false;
        }

        Toggles toggles = getInstance();
        ToggleDefinition def = toggles.toggleDefinitions.get(toggle);
        if (def == null) {
            toggles.logger.warning("Attempted to set unknown toggle: " + toggle);
            return false;
        }

        // Check permission
        if (def.permission != null && !player.hasPermission(def.permission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the " + toggle + " toggle.");
            return false;
        }

        YakPlayer yakPlayer = toggles.playerManager.getPlayer(player);
        if (yakPlayer == null) {
            toggles.logger.warning("YakPlayer is null for " + player.getName() + " when setting toggle: " + toggle);
            return false;
        }

        boolean currentState = yakPlayer.isToggled(toggle);
        if (currentState != enabled) {
            yakPlayer.toggleSetting(toggle);
            toggles.playerManager.savePlayer(yakPlayer);

            // Send feedback with  formatting
            String status = enabled ? "§a§lEnabled" : "§c§lDisabled";
            player.sendMessage("§6§l✦ §f" + toggle + " " + status);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f,
                    enabled ? 1.2f : 0.8f);
        }

        return true;
    }

    /**
     *   toggle menu with improved debugging and validation
     */
    public Inventory getToggleMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, MENU_SIZE, "§6§l✦ §e§lTOGGLE SETTINGS §6§l✦");

        
        if (!definitionsInitialized) {
            logger.severe("Toggle definitions not initialized when creating menu for " + player.getName());
            addErrorItem(inventory, "System not initialized");
            return inventory;
        }

        if (toggleDefinitions.isEmpty()) {
            logger.severe("Toggle definitions map is empty when creating menu for " + player.getName());
            addErrorItem(inventory, "No toggles available");
            return inventory;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            logger.warning("YakPlayer is null when creating toggle menu for " + player.getName());
            addErrorItem(inventory, "Player data not loaded");
            return inventory;
        }

        logger.info("Creating toggle menu for " + player.getName() + " with " +
                toggleDefinitions.size() + " toggle definitions");

        // Group toggles by category
        Map<String, List<ToggleDefinition>> categories = toggleDefinitions.values().stream()
                .collect(Collectors.groupingBy(def -> def.category, LinkedHashMap::new, Collectors.toList()));

        logger.info("Categories found: " + categories.keySet());

        
        int currentSlot = 0;
        int itemsAdded = 0;

        // Add category sections
        for (Map.Entry<String, List<ToggleDefinition>> entry : categories.entrySet()) {
            String category = entry.getKey();
            List<ToggleDefinition> categoryToggles = entry.getValue();

            logger.info("Processing category: " + category + " with " + categoryToggles.size() + " toggles");

            // Add category header
            if (isValidSlot(currentSlot)) {
                addCategoryHeader(inventory, category, currentSlot);
                currentSlot++;
                itemsAdded++;
            }

            // Add toggles for this category
            for (ToggleDefinition def : categoryToggles) {
                if (!isValidSlot(currentSlot)) {
                    logger.warning("Ran out of slots at slot " + currentSlot);
                    break;
                }

                if (hasPermissionForToggle(player, def)) {
                    addToggleItem(inventory, def, yakPlayer, currentSlot);
                    logger.fine("Added toggle item: " + def.name + " at slot " + currentSlot);
                } else {
                    addLockedToggleItem(inventory, def, currentSlot);
                    logger.fine("Added locked toggle item: " + def.name + " at slot " + currentSlot);
                }

                currentSlot++;
                itemsAdded++;

                // Move to next row if we're getting close to the edge
                if (currentSlot % 9 >= 8) {
                    currentSlot = ((currentSlot / 9) + 1) * 9;
                }
            }

            // Add spacing between categories
            currentSlot = ((currentSlot / 9) + 1) * 9;
        }

        logger.info("Added " + itemsAdded + " items to toggle menu");

        // Add decorative elements and info
        addMenuDecorations(inventory);
        addInfoButton(inventory, INFO_SLOT);

        return inventory;
    }

    /**
     *  Add error item for debugging
     */
    private void addErrorItem(Inventory inventory, String error) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        meta.setDisplayName("§c§lError: " + error);

        List<String> lore = new ArrayList<>();
        lore.add("§7There was an issue loading the toggle menu.");
        lore.add("§7Please contact an administrator.");
        lore.add("");
        lore.add("§8Error: " + error);
        meta.setLore(lore);

        errorItem.setItemMeta(meta);
        inventory.setItem(22, errorItem); // Center slot
    }

    /**
     *  Validate slot numbers with better logging
     */
    private boolean isValidSlot(int slot) {
        boolean valid = slot >= 0 && slot < USABLE_SLOTS;
        if (!valid) {
            logger.fine("Invalid slot: " + slot + " (valid range: 0-" + (USABLE_SLOTS - 1) + ")");
        }
        return valid;
    }

    /**
     *   category header with better validation
     */
    private void addCategoryHeader(Inventory inventory, String category, int slot) {
        if (!isValidSlot(slot)) {
            logger.warning("Invalid slot for category header: " + slot + " for category: " + category);
            return;
        }

        Material headerMaterial = getCategoryMaterial(category);
        ItemStack header = new ItemStack(headerMaterial);
        ItemMeta meta = header.getItemMeta();

        meta.setDisplayName("§6§l✦ " + category.toUpperCase() + " TOGGLES §6§l✦");

        List<String> lore = new ArrayList<>();
        lore.add("§7Settings related to " + category.toLowerCase());
        lore.add("§8Toggles in this category:");

        // List toggles in this category
        List<ToggleDefinition> categoryToggles = toggleDefinitions.values().stream()
                .filter(def -> def.category.equals(category))
                .collect(Collectors.toList());

        for (ToggleDefinition def : categoryToggles) {
            lore.add("§8• " + def.name);
        }

        meta.setLore(lore);
        header.setItemMeta(meta);
        inventory.setItem(slot, header);

        logger.fine("Added category header for " + category + " at slot " + slot);
    }

    /**
     * Get material for category headers
     */
    private Material getCategoryMaterial(String category) {
        switch (category) {
            case "Combat": return Material.IRON_SWORD;
            case "Display": return Material.PAINTING;
            case "System": return Material.REDSTONE_BLOCK;
            case "Social": return Material.PLAYER_HEAD;
            case "Audio": return Material.JUKEBOX;
            default: return Material.BOOKSHELF;
        }
    }

    /**
     *  Add  toggle item with better validation and debugging
     */
    private void addToggleItem(Inventory inventory, ToggleDefinition def, YakPlayer yakPlayer, int slot) {
        if (!isValidSlot(slot)) {
            logger.warning("Invalid slot for toggle item: " + slot + " for toggle: " + def.name);
            return;
        }

        boolean isEnabled = yakPlayer.isToggled(def.name);

        // Choose material and color based on state
        Material material = isEnabled ? Material.LIME_DYE : Material.GRAY_DYE;
        if (def.requiresConfirmation) {
            material = isEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        //  naming with status
        String status = isEnabled ? "§a§lEnabled" : "§c§lDisabled";
        String icon = isEnabled ? "§a✓" : "§c✗";
        meta.setDisplayName(icon + " §f§l" + def.name + " " + status);

        //  lore
        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.description);
        lore.add("");
        lore.add("§8Category: §7" + def.category);

        if (def.requiresConfirmation) {
            lore.add("");
            lore.add("§c§l⚠ §cSensitive Setting");
            lore.add("§7Requires confirmation to change");
        }

        lore.add("");
        lore.add("§e§lClick to " + (isEnabled ? "disable" : "enable"));

        meta.setLore(lore);
        item.setItemMeta(meta);

        inventory.setItem(slot, item);
        logger.fine("Added toggle item " + def.name + " (" + status + ") at slot " + slot);
    }

    /**
     *  Add locked toggle item with better validation
     */
    private void addLockedToggleItem(Inventory inventory, ToggleDefinition def, int slot) {
        if (!isValidSlot(slot)) {
            logger.warning("Invalid slot for locked toggle item: " + slot + " for toggle: " + def.name);
            return;
        }

        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c§l🔒 " + def.name + " §8(Locked)");

        List<String> lore = new ArrayList<>();
        lore.add("§7" + def.description);
        lore.add("");
        lore.add("§8Category: §7" + def.category);
        lore.add("");
        lore.add("§c§lRequired Permission:");
        lore.add("§7" + def.permission);
        lore.add("");
        lore.add("§8Contact staff for access");

        meta.setLore(lore);
        item.setItemMeta(meta);

        inventory.setItem(slot, item);
        logger.fine("Added locked toggle item " + def.name + " at slot " + slot);
    }

    /**
     *  Add menu decorations with proper bounds checking
     */
    private void addMenuDecorations(Inventory inventory) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§8");
        glass.setItemMeta(glassMeta);

        // Fill bottom row for navigation/info area (slots 45-53)
        for (int i = 45; i < MENU_SIZE - 1; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, glass);
            }
        }
    }

    /**
     *  Add  info button with bounds checking
     */
    private void addInfoButton(Inventory inventory, int slot) {
        if (slot < 0 || slot >= MENU_SIZE) {
            logger.warning("Invalid slot for info button: " + slot);
            return;
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();

        meta.setDisplayName("§b§l📖 Toggle Information");

        List<String> lore = new ArrayList<>();
        lore.add("§7Click any toggle to enable or disable it");
        lore.add("§7§lColors:");
        lore.add("  §a§l✓ Green §7- Enabled");
        lore.add("  §c§l✗ Red §7- Disabled");
        lore.add("  §8🔒 Barrier §7- Locked (requires permission)");
        lore.add("");
        lore.add("§6§lSpecial Toggles:");
        lore.add("§7Some toggles require confirmation");
        lore.add("§7and may have additional requirements");
        lore.add("");
        lore.add("§8Total toggles: " + toggleDefinitions.size());

        meta.setLore(lore);
        info.setItemMeta(meta);

        inventory.setItem(slot, info);
    }

    /**
     * Check if player has permission for a toggle
     */
    private boolean hasPermissionForToggle(Player player, ToggleDefinition def) {
        if (def.permission == null) {
            return true;
        }

        boolean hasPermission = player.hasPermission(def.permission);
        logger.fine("Permission check for " + player.getName() + " and toggle " + def.name +
                " (permission: " + def.permission + "): " + hasPermission);
        return hasPermission;
    }

    /**
     *   toggle change with better validation
     */
    public boolean changeToggle(Player player, String toggle) {
        if (player == null || toggle == null) {
            return false;
        }

        ToggleDefinition def = toggleDefinitions.get(toggle);
        if (def == null) {
            player.sendMessage(ChatColor.RED + "Unknown toggle: " + toggle);
            logger.warning("Unknown toggle requested by " + player.getName() + ": " + toggle);
            return false;
        }

        // Check permission
        if (def.permission != null && !player.hasPermission(def.permission)) {
            player.sendMessage(ChatColor.RED + "§l⚠ §cYou don't have permission to use the " + toggle + " toggle.");
            player.sendMessage(ChatColor.GRAY + "Required permission: " + def.permission);
            return false;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage(ChatColor.RED + "§l⚠ §cPlayer data not loaded. Please try again.");
            logger.warning("YakPlayer is null for " + player.getName() + " when changing toggle: " + toggle);
            return false;
        }

        UUID uuid = player.getUniqueId();
        boolean currentState = yakPlayer.isToggled(toggle);

        // Handle confirmation system for sensitive toggles
        if (def.requiresConfirmation) {
            PendingConfirmation pending = confirmationMap.get(uuid);

            if (pending != null && pending.toggleName.equals(toggle)) {
                if (pending.isExpired(CONFIRMATION_EXPIRY)) {
                    confirmationMap.remove(uuid);
                    sendConfirmationRequest(player, toggle, currentState);
                    return true;
                }

                // Execute the confirmed toggle
                confirmationMap.remove(uuid);
                executeToggleChange(player, yakPlayer, toggle, !currentState);
                return true;
            } else {
                // Request confirmation
                sendConfirmationRequest(player, toggle, currentState);
                confirmationMap.put(uuid, new PendingConfirmation(toggle, currentState));
                return true;
            }
        }

        // Execute toggle change immediately for non-sensitive toggles
        executeToggleChange(player, yakPlayer, toggle, !currentState);
        return true;
    }

    /**
     * Send  confirmation request
     */
    private void sendConfirmationRequest(Player player, String toggle, boolean currentState) {
        ToggleDefinition def = toggleDefinitions.get(toggle);
        String newState = !currentState ? "§a§lEnabled" : "§c§lDisabled";

        player.sendMessage("");
        player.sendMessage("§6§l⚠ CONFIRMATION REQUIRED ⚠");
        player.sendMessage("§7You are about to change §f" + toggle + " §7to " + newState);
        player.sendMessage("§7" + def.description);
        player.sendMessage("");
        player.sendMessage("§e§lClick the toggle again within 15 seconds to confirm.");
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
    }

    /**
     * Execute the actual toggle change with  feedback
     */
    private void executeToggleChange(Player player, YakPlayer yakPlayer, String toggle, boolean newState) {
        yakPlayer.toggleSetting(toggle);
        playerManager.savePlayer(yakPlayer);

        ToggleDefinition def = toggleDefinitions.get(toggle);
        String status = newState ? "§a§lEnabled" : "§c§lDisabled";

        player.sendMessage("");
        player.sendMessage("§6§l✦ TOGGLE UPDATED ✦");
        player.sendMessage("§f" + toggle + " " + status);
        player.sendMessage("§7" + def.description);
        player.sendMessage("");

        // Play appropriate sound
        Sound sound = newState ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_BASS;
        float pitch = newState ? 1.5f : 0.8f;
        player.playSound(player.getLocation(), sound, 0.8f, pitch);

        // Special handling for certain toggles
        handleSpecialToggleEffects(player, toggle, newState);

        logger.info("Toggle changed for " + player.getName() + ": " + toggle + " = " + newState);
    }

    /**
     * Handle special effects for certain toggles
     */
    private void handleSpecialToggleEffects(Player player, String toggle, boolean enabled) {
        switch (toggle) {
            case "Trail Effects":
                if (enabled) {
                    player.sendMessage("§a§l✨ §aTrail effects will now appear when you move!");
                } else {
                    player.sendMessage("§7Trail effects have been disabled.");
                }
                break;

            case "Energy System":
                if (!enabled) {
                    player.sendMessage("§c§l⚠ §cEnergy system disabled - unlimited stamina!");
                } else {
                    player.sendMessage("§a§lEnergy system re-enabled!");
                }
                break;

            case "Auto Bank":
                if (enabled) {
                    player.sendMessage("§6§l💰 §6Gems will now automatically deposit to your bank!");
                } else {
                    player.sendMessage("§7Auto-banking disabled.");
                }
                break;
        }
    }

    /**
     *   inventory click handler with better debugging
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().contains("TOGGLE SETTINGS")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            logger.fine("Player " + player.getName() + " clicked empty slot in toggle menu");
            return;
        }

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        logger.fine("Player " + player.getName() + " clicked item: " + displayName);

        // Skip decorative items
        if (clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                clickedItem.getType() == Material.BOOK ||
                displayName.contains("TOGGLES")) {
            logger.fine("Skipping decorative item: " + displayName);
            return;
        }

        // Handle locked toggles
        if (clickedItem.getType() == Material.BARRIER) {
            String toggleName = extractToggleName(displayName);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
            player.sendMessage("§c§l🔒 §cYou don't have access to the §f" + toggleName + " §ctoggle.");
            logger.fine("Player " + player.getName() + " clicked locked toggle: " + toggleName);
            return;
        }

        // Process toggle click
        String toggleName = extractToggleName(displayName);
        logger.info("Extracted toggle name: " + toggleName + " from display name: " + displayName);

        if (toggleName != null && toggleDefinitions.containsKey(toggleName)) {
            logger.info("Processing toggle change for " + player.getName() + ": " + toggleName);
            if (changeToggle(player, toggleName)) {
                // Refresh the menu after a short delay
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.openInventory(getToggleMenu(player));
                    }
                }, 5L);
            }
        } else {
            logger.warning("Failed to process toggle click for " + player.getName() +
                    ": toggleName=" + toggleName + ", exists=" + toggleDefinitions.containsKey(toggleName));
            player.sendMessage("§c§l⚠ §cFailed to process toggle. Please try again.");
        }
    }

    /**
     *  Better toggle name extraction with debugging
     */
    private String extractToggleName(String displayName) {
        logger.fine("Extracting toggle name from: '" + displayName + "'");

        // Remove status indicators and formatting
        String cleaned = displayName;

        // Remove icons and status text
        cleaned = cleaned.replaceAll("^[✓✗🔒]\\s*", ""); // Remove icons at start
        cleaned = cleaned.replaceAll("\\s+(Enabled|Disabled|\\(Locked\\)).*$", ""); // Remove status at end
        cleaned = cleaned.trim();

        logger.fine("Cleaned display name: '" + cleaned + "'");

        // Find matching toggle definition
        for (String toggleName : toggleDefinitions.keySet()) {
            if (cleaned.equals(toggleName)) {
                logger.fine("Found exact match: " + toggleName);
                return toggleName;
            }
        }

        // Try partial matching as fallback
        for (String toggleName : toggleDefinitions.keySet()) {
            if (cleaned.contains(toggleName) || toggleName.contains(cleaned)) {
                logger.fine("Found partial match: " + toggleName + " for cleaned name: " + cleaned);
                return toggleName;
            }
        }

        logger.warning("No toggle found for display name: '" + displayName + "' (cleaned: '" + cleaned + "')");
        logger.warning("Available toggles: " + toggleDefinitions.keySet());
        return null;
    }

    /**
     * UPDATED: Simplified PVP damage handler - main protection logic moved to CombatMechanics
     * This now only handles toggle-specific feedback and logging
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        // Only handle player vs player damage
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }

        if (event.getDamage() <= 0.0 || event.isCancelled()) {
            return;
        }

        Player damager = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        YakPlayer damagerData = playerManager.getPlayer(damager);
        YakPlayer victimData = playerManager.getPlayer(victim);

        if (damagerData == null || victimData == null) {
            return;
        }

        // The main PVP protection logic is now handled in CombatMechanics.onPvpProtection
        // This handler only deals with toggle-specific effects and feedback

        // Check if this is a successful PVP hit (not cancelled by protection)
        if (!event.isCancelled()) {
            // Handle successful PVP hit effects
            handleSuccessfulPVPHit(damager, victim, damagerData, victimData);
        }
    }

    /**
     * Handle effects and feedback for successful PVP hits
     */
    private void handleSuccessfulPVPHit(Player damager, Player victim, YakPlayer damagerData, YakPlayer victimData) {
        // Debug information for successful hits
        if (Toggles.isToggled(damager, "Debug")) {
            String relationship = getPlayerRelationship(damager, victim, damagerData, victimData);
        }

        // Special effects for friendly fire hits
        if (isFriendlyFireHit(damager, victim, damagerData, victimData)) {
            showFriendlyFireEffects(damager, victim);
        }
    }

    /**
     * Check if this is a friendly fire hit (attacking friend/party member with friendly fire enabled)
     */
    private boolean isFriendlyFireHit(Player damager, Player victim, YakPlayer damagerData, YakPlayer victimData) {
        if (!damagerData.isToggled("Friendly Fire")) {
            return false;
        }

        // Check if they're buddies
        if (damagerData.isBuddy(victim.getName()) || victimData.isBuddy(damager.getName())) {
            return true;
        }

        // Check if they're party members
        if (PartyMechanics.getInstance().arePartyMembers(damager, victim)) {
            return true;
        }

        // Check if they're guild members
        if (isInSameGuild(damager, victim)) {
            return true;
        }

        return false;
    }

    /**
     * Get the relationship between two players for debug purposes
     */
    private String getPlayerRelationship(Player damager, Player victim, YakPlayer damagerData, YakPlayer victimData) {
        List<String> relationships = new ArrayList<>();

        if (damagerData.isBuddy(victim.getName())) {
            relationships.add("buddy");
        }

        if (PartyMechanics.getInstance().arePartyMembers(damager, victim)) {
            relationships.add("party");
        }

        if (isInSameGuild(damager, victim)) {
            relationships.add("guild");
        }

        if (damagerData.isToggled("Friendly Fire")) {
            relationships.add("friendly fire enabled");
        }

        return String.join(", ", relationships);
    }

    /**
     * Show special effects for friendly fire hits
     */
    private void showFriendlyFireEffects(Player damager, Player victim) {
        try {
            // Visual warning for both players
            damager.sendMessage(ChatColor.YELLOW + "⚠ Friendly fire hit on " + victim.getName() + "!");
            victim.sendMessage(ChatColor.YELLOW + "⚠ Friendly fire from " + damager.getName() + "!");

            // Special sound effect
            damager.playSound(damager.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);
            victim.playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.8f);

            // Particle effects
            victim.getWorld().spawnParticle(Particle.ANGRY_VILLAGER,
                    victim.getLocation().add(0, 2, 0),
                    3, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Ignore visual effect errors
        }
    }

    /**
     * Check if two players are in the same guild (moved from main PVP handler)
     */
    private boolean isInSameGuild(Player player1, Player player2) {
        if (player1 == null || player2 == null) return false;

        YakPlayer yakPlayer1 = playerManager.getPlayer(player1);
        YakPlayer yakPlayer2 = playerManager.getPlayer(player2);

        if (yakPlayer1 == null || yakPlayer2 == null) return false;

        String guild1 = yakPlayer1.getGuildName();
        String guild2 = yakPlayer2.getGuildName();

        // Both must be in a guild and the same guild
        return guild1 != null && !guild1.trim().isEmpty() &&
                guild2 != null && !guild2.trim().isEmpty() &&
                guild1.equals(guild2);
    }

    /**
     *   player join handler with better initialization
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay initialization to ensure player data is loaded
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            initializePlayerToggles(player);
        }, 40L); // 2 second delay
    }

    /**
     *  Initialize player toggles with better validation
     */
    private void initializePlayerToggles(Player player) {
        if (!player.isOnline()) {
            return;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            logger.warning("YakPlayer is null for " + player.getName() + " during toggle initialization");

            // Retry after another delay
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    initializePlayerToggles(player);
                }
            }, 40L);
            return;
        }

        // Check if toggles need initialization
        if (yakPlayer.getToggleSettings().isEmpty()) {
            logger.info("Initializing default toggles for new player: " + player.getName());
            setDefaultToggles(yakPlayer);
            playerManager.savePlayer(yakPlayer);

            // Send  welcome message
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    sendWelcomeMessage(player);
                }
            }, 20L);
        } else {
            logger.fine("Player " + player.getName() + " already has " +
                    yakPlayer.getToggleSettings().size() + " toggle settings");
        }
    }

    /**
     * Set default toggles for new players
     */
    private void setDefaultToggles(YakPlayer yakPlayer) {
        int defaultsSet = 0;
        for (ToggleDefinition def : toggleDefinitions.values()) {
            if (def.defaultValue) {
                yakPlayer.toggleSetting(def.name);
                defaultsSet++;
                logger.fine("Set default toggle: " + def.name + " = " + def.defaultValue);
            }
        }
        logger.info("Set " + defaultsSet + " default toggles for player");
    }

    /**
     * Send  welcome message
     */
    private void sendWelcomeMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§6§l✦ §e§lWELCOME TO YAK REALMS §6§l✦");
        player.sendMessage("§7Your toggle settings have been initialized!");
        player.sendMessage("§7Use §f/toggle §7to customize your experience.");
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    // Utility methods
    private boolean isLawfulPlayer(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && "LAWFUL".equals(yakPlayer.getAlignment());
    }

    /**
     * Open  toggle menu with validation
     */
    public void openToggleMenu(Player player) {
        if (!definitionsInitialized) {
            player.sendMessage("§c§l⚠ §cToggle system is not ready. Please try again.");
            logger.severe("Attempted to open toggle menu when definitions not initialized");
            return;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) {
            player.sendMessage("§c§l⚠ §cPlayer data not loaded. Please try again.");
            logger.warning("Attempted to open toggle menu for " + player.getName() + " but YakPlayer is null");
            return;
        }

        logger.info("Opening toggle menu for " + player.getName());
        player.openInventory(getToggleMenu(player));
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Get available toggles for a player
     */
    public List<String> getAvailableToggles(Player player) {
        return toggleDefinitions.values().stream()
                .filter(def -> hasPermissionForToggle(player, def))
                .map(def -> def.name)
                .collect(Collectors.toList());
    }

    /**
     * Check if player can use a toggle
     */
    public boolean canUseToggle(Player player, String toggle) {
        ToggleDefinition def = toggleDefinitions.get(toggle);
        return def != null && hasPermissionForToggle(player, def);
    }

    /**
     * Get toggle definition
     */
    public ToggleDefinition getToggleDefinition(String toggle) {
        return toggleDefinitions.get(toggle);
    }

    /**
     * Get toggles by category
     */
    public Map<String, List<String>> getTogglesByCategory() {
        return toggleDefinitions.values().stream()
                .collect(Collectors.groupingBy(
                        def -> def.category,
                        Collectors.mapping(def -> def.name, Collectors.toList())
                ));
    }

    /**
     *  Debug method to get system status
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("definitionsInitialized", definitionsInitialized);
        status.put("totalDefinitions", toggleDefinitions.size());
        status.put("categoriesCount", toggleDefinitions.values().stream()
                .collect(Collectors.groupingBy(def -> def.category)).size());
        status.put("pendingConfirmations", confirmationMap.size());
        return status;
    }
}
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced toggle system with improved UI, better performance,
 * validation, and enhanced user experience.
 */
public class Toggles implements Listener {
    private static Toggles instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Enhanced confirmation system
    private final Map<UUID, PendingConfirmation> confirmationMap = new ConcurrentHashMap<>();

    // Configuration
    private final long CONFIRMATION_EXPIRY = 15000; // 15 seconds
    private final int MENU_SIZE = 54; // 6 rows for better organization

    // Toggle definitions with enhanced metadata
    private final Map<String, ToggleDefinition> toggleDefinitions = new HashMap<>();

    /**
     * Enhanced confirmation tracking
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
     * Enhanced toggle definition with metadata
     */
    private static class ToggleDefinition {
        final String name;
        final String category;
        final String description;
        final String permission;
        final boolean requiresConfirmation;
        final boolean defaultValue;
        final Material iconMaterial;
        final String iconName;
        final List<String> loreLines;

        ToggleDefinition(String name, String category, String description, String permission,
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
     * Initialize all toggle definitions with enhanced metadata
     */
    private void initializeToggleDefinitions() {
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
        addToggleDefinition("Debug Mode", "Display", "Shows detailed combat and system information",
                null, false, false, Material.REDSTONE);
        addToggleDefinition("Trail Effects", "Display", "Displays special particle trail effects",
                "yakserver.donator", false, false, Material.FIREWORK_ROCKET);
        addToggleDefinition("Particles", "Display", "Shows various particle effects",
                "yakserver.donator", false, false, Material.BLAZE_POWDER);

        // System toggles
        addToggleDefinition("Drop Protection", "System", "Protects your dropped items for 5 seconds",
                null, false, true, Material.CHEST);
        addToggleDefinition("Auto Bank", "System", "Automatically deposits gems into your bank",
                "yakserver.donator.tier2", false, false, Material.GOLD_INGOT);
        addToggleDefinition("Energy System", "System", "Enables/disables the energy/stamina system",
                "yakserver.admin", true, true, Material.SUGAR);
        addToggleDefinition("Disable Kit", "System", "Prevents receiving starter kits on respawn",
                null, false, false, Material.LEATHER_CHESTPLATE);

        // Social toggles
        addToggleDefinition("Trading", "Social", "Allows other players to send you trade requests",
                null, false, true, Material.EMERALD);
        addToggleDefinition("Player Messages", "Social", "Receive private messages from other players",
                null, false, true, Material.PAPER);
        addToggleDefinition("Guild Invites", "Social", "Receive guild invitation requests",
                null, false, true, Material.BLUE_BANNER);
        addToggleDefinition("Buddy Requests", "Social", "Receive buddy/friend requests",
                null, false, true, Material.GOLDEN_APPLE);

        // Audio toggles
        addToggleDefinition("Sound Effects", "Audio", "Play UI and interaction sound effects",
                null, false, true, Material.NOTE_BLOCK);
        addToggleDefinition("Combat Sounds", "Audio", "Play enhanced combat sound effects",
                null, false, true, Material.BELL);

        logger.info("Initialized " + toggleDefinitions.size() + " toggle definitions");
    }

    private void addToggleDefinition(String name, String category, String description, String permission,
                                     boolean requiresConfirmation, boolean defaultValue, Material iconMaterial) {
        toggleDefinitions.put(name, new ToggleDefinition(name, category, description, permission,
                requiresConfirmation, defaultValue, iconMaterial));
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

        YakRealms.log("Enhanced Toggles system has been enabled.");
    }

    public void onDisable() {
        confirmationMap.clear();
        YakRealms.log("Enhanced Toggles system has been disabled.");
    }

    /**
     * Clean up expired confirmations
     */
    private void cleanupExpiredConfirmations() {
        confirmationMap.entrySet().removeIf(entry ->
                entry.getValue().isExpired(CONFIRMATION_EXPIRY));
    }

    /**
     * Enhanced toggle checking with null safety
     */
    public static boolean isToggled(Player player, String toggle) {
        if (player == null || toggle == null) return false;

        YakPlayer yakPlayer = getInstance().playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        // Check if toggle exists and get default value
        ToggleDefinition def = getInstance().toggleDefinitions.get(toggle);
        if (def == null) return false;

        return yakPlayer.isToggled(toggle);
    }

    /**
     * Enhanced toggle setting with validation
     */
    public static boolean setToggle(Player player, String toggle, boolean enabled) {
        if (player == null || toggle == null) return false;

        Toggles toggles = getInstance();
        ToggleDefinition def = toggles.toggleDefinitions.get(toggle);
        if (def == null) return false;

        // Check permission
        if (def.permission != null && !player.hasPermission(def.permission)) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use the " + toggle + " toggle.");
            return false;
        }

        YakPlayer yakPlayer = toggles.playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        boolean currentState = yakPlayer.isToggled(toggle);
        if (currentState != enabled) {
            yakPlayer.toggleSetting(toggle);
            toggles.playerManager.savePlayer(yakPlayer);

            // Send feedback with enhanced formatting
            String status = enabled ? "§a§lEnabled" : "§c§lDisabled";
            player.sendMessage("§6§l✦ §f" + toggle + " " + status);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f,
                    enabled ? 1.2f : 0.8f);
        }

        return true;
    }

    /**
     * Enhanced toggle change with confirmation system
     */
    public boolean changeToggle(Player player, String toggle) {
        if (player == null || toggle == null) return false;

        ToggleDefinition def = toggleDefinitions.get(toggle);
        if (def == null) {
            player.sendMessage(ChatColor.RED + "Unknown toggle: " + toggle);
            return false;
        }

        // Check permission
        if (def.permission != null && !player.hasPermission(def.permission)) {
            player.sendMessage(ChatColor.RED + "§l⚠ §cYou don't have permission to use the " + toggle + " toggle.");
            player.sendMessage(ChatColor.GRAY + "Required permission: " + def.permission);
            return false;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

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
     * Send enhanced confirmation request
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
     * Execute the actual toggle change with enhanced feedback
     */
    private void executeToggleChange(Player player, YakPlayer yakPlayer, String toggle, boolean newState) {
        yakPlayer.toggleSetting(toggle);
        playerManager.savePlayer(yakPlayer);

        ToggleDefinition def = toggleDefinitions.get(toggle);
        String status = newState ? "§a§lEnabled" : "§c§lDisabled";
        String icon = newState ? "§a✓" : "§c✗";

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
     * Enhanced toggle menu with improved organization and visuals
     */
    public Inventory getToggleMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, MENU_SIZE, "§6§l✦ §e§lTOGGLE SETTINGS §6§l✦");

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return inventory;

        // Group toggles by category
        Map<String, List<ToggleDefinition>> categories = toggleDefinitions.values().stream()
                .collect(Collectors.groupingBy(def -> def.category));

        int slot = 0;

        // Add category sections
        for (Map.Entry<String, List<ToggleDefinition>> entry : categories.entrySet()) {
            String category = entry.getKey();
            List<ToggleDefinition> categoryToggles = entry.getValue();

            // Add category header
            addCategoryHeader(inventory, category, slot);
            slot += 9; // Move to next row

            // Add toggles for this category
            for (ToggleDefinition def : categoryToggles) {
                if (hasPermissionForToggle(player, def)) {
                    addToggleItem(inventory, def, yakPlayer, slot);
                } else {
                    addLockedToggleItem(inventory, def, slot);
                }
                slot++;

                if (slot % 9 == 0) slot += 0; // Stay in same row until full
            }

            // Align to next row if not already
            slot = ((slot / 9) + 1) * 9;
        }

        // Add decorative elements and info
        addMenuDecorations(inventory);
        addInfoButton(inventory, MENU_SIZE - 1);

        return inventory;
    }

    /**
     * Add enhanced category header
     */
    private void addCategoryHeader(Inventory inventory, String category, int slot) {
        Material headerMaterial = getCategoryMaterial(category);
        ItemStack header = new ItemStack(headerMaterial);
        ItemMeta meta = header.getItemMeta();

        meta.setDisplayName("§6§l✦ " + category.toUpperCase() + " TOGGLES §6§l✦");

        List<String> lore = new ArrayList<>();
        lore.add("§7Settings related to " + category.toLowerCase());
        lore.add("§8Click toggles below to change them");
        meta.setLore(lore);

        header.setItemMeta(meta);
        inventory.setItem(slot, header);
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
     * Add enhanced toggle item
     */
    private void addToggleItem(Inventory inventory, ToggleDefinition def, YakPlayer yakPlayer, int slot) {
        boolean isEnabled = yakPlayer.isToggled(def.name);

        // Choose material and color based on state
        Material material = isEnabled ? Material.LIME_DYE : Material.GRAY_DYE;
        if (def.requiresConfirmation) {
            material = isEnabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Enhanced naming with status
        String status = isEnabled ? "§a§lEnabled" : "§c§lDisabled";
        String icon = isEnabled ? "§a✓" : "§c✗";
        meta.setDisplayName(icon + " §f§l" + def.name + " " + status);

        // Enhanced lore
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
    }

    /**
     * Add locked toggle item with enhanced visuals
     */
    private void addLockedToggleItem(Inventory inventory, ToggleDefinition def, int slot) {
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
    }

    /**
     * Add menu decorations
     */
    private void addMenuDecorations(Inventory inventory) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§8");
        glass.setItemMeta(glassMeta);

        // Fill empty border slots
        for (int i = 0; i < MENU_SIZE; i++) {
            if (inventory.getItem(i) == null && (i < 9 || i >= MENU_SIZE - 9 || i % 9 == 0 || i % 9 == 8)) {
                inventory.setItem(i, glass);
            }
        }
    }

    /**
     * Add enhanced info button
     */
    private void addInfoButton(Inventory inventory, int slot) {
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
        return def.permission == null || player.hasPermission(def.permission);
    }

    /**
     * Enhanced inventory click handler
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onToggleClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().contains("TOGGLE SETTINGS")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Skip decorative items
        if (clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                clickedItem.getType() == Material.BOOK ||
                displayName.contains("TOGGLES")) {
            return;
        }

        // Handle locked toggles
        if (clickedItem.getType() == Material.BARRIER) {
            String toggleName = extractToggleName(displayName);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
            player.sendMessage("§c§l🔒 §cYou don't have access to the §f" + toggleName + " §ctoggle.");
            return;
        }

        // Process toggle click
        String toggleName = extractToggleName(displayName);
        if (toggleName != null && toggleDefinitions.containsKey(toggleName)) {
            if (changeToggle(player, toggleName)) {
                // Refresh the menu after a short delay
                Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                    if (player.isOnline()) {
                        player.openInventory(getToggleMenu(player));
                    }
                }, 5L);
            }
        }
    }

    /**
     * Extract toggle name from display name
     */
    private String extractToggleName(String displayName) {
        // Remove status indicators and formatting
        displayName = displayName.replaceAll("^[✓✗🔒]\\s*", "")
                .replaceAll("\\s+(Enabled|Disabled|\\(Locked\\)).*$", "")
                .trim();

        // Find matching toggle definition
        for (String toggleName : toggleDefinitions.keySet()) {
            if (displayName.equals(toggleName)) {
                return toggleName;
            }
        }

        return null;
    }

    /**
     * Enhanced PVP damage handler with better messaging
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

        // Enhanced PVP prevention with better feedback
        if (damagerData.isToggled("Anti PVP")) {
            event.setCancelled(true);
            damager.sendMessage("§c§l⚠ §cYou have PVP disabled! §7Use §f/toggle §7to enable it.");
            damager.playSound(damager.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        if (victimData.isToggled("Anti PVP")) {
            event.setCancelled(true);
            damager.sendMessage("§c§l⚠ §f" + victim.getName() + " §chas PVP disabled!");
            damager.playSound(damager.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Enhanced buddy protection
        if (damagerData.isBuddy(victim.getName()) && !damagerData.isToggled("Friendly Fire")) {
            event.setCancelled(true);
            damager.sendMessage("§c§l⚠ §cYou cannot attack your buddy §f" + victim.getName() + "§c!");
            damager.sendMessage("§7Enable §fFriendly Fire §7in §f/toggle §7to allow this.");
            return;
        }

        // Enhanced guild protection
        if (isInSameGuild(damager, victim) && !damagerData.isToggled("Friendly Fire")) {
            event.setCancelled(true);
            damager.sendMessage("§c§l⚠ §cYou cannot attack your guild member §f" + victim.getName() + "§c!");
            damager.sendMessage("§7Enable §fFriendly Fire §7in §f/toggle §7to allow this.");
            return;
        }

        // Enhanced chaotic protection
        if (damagerData.isToggled("Chaotic Protection") && isLawfulPlayer(victim)) {
            event.setCancelled(true);
            damager.sendMessage("§c§l⚠ §cChaotic Protection prevented you from attacking §f" + victim.getName() + "§c!");
            damager.sendMessage("§7This player is lawful. Disable §fChaotic Protection §7to attack them.");
            return;
        }
    }

    /**
     * Enhanced player join handler with improved defaults
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer != null && yakPlayer.getToggleSettings().isEmpty()) {
            // Set enhanced default toggles
            setDefaultToggles(yakPlayer);
            playerManager.savePlayer(yakPlayer);

            // Send enhanced welcome message
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    sendWelcomeMessage(player);
                }
            }, 60L);
        }
    }

    /**
     * Set default toggles for new players
     */
    private void setDefaultToggles(YakPlayer yakPlayer) {
        for (ToggleDefinition def : toggleDefinitions.values()) {
            if (def.defaultValue) {
                yakPlayer.toggleSetting(def.name);
            }
        }
    }

    /**
     * Send enhanced welcome message
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

    private boolean isInSameGuild(Player player1, Player player2) {
        YakPlayer yakPlayer1 = playerManager.getPlayer(player1);
        YakPlayer yakPlayer2 = playerManager.getPlayer(player2);

        if (yakPlayer1 == null || yakPlayer2 == null) return false;
//
        //String guild1 = yakPlayer1.getGuildName();
        // guild2 = yakPlayer2.getGuildName();

        return false;
    }

    /**
     * Open enhanced toggle menu
     */
    public void openToggleMenu(Player player) {
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
}
package com.rednetty.server.mechanics.item.drops.glowing;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.settings.Toggles;
import fr.skytasul.glowingentities.GlowingEntities;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * GlowingEntities-based manager for the glowing drops system
 * Uses shaded GlowingEntities library with correct API methods and colors
 */
public class GlowingDropsManager {
    // Rarity detection patterns
    private static final Pattern COMMON_PATTERN = Pattern.compile("(?i)\\b(common|trash|junk)\\b");
    private static final Pattern UNCOMMON_PATTERN = Pattern.compile("(?i)\\b(uncommon|magic)\\b");
    private static final Pattern RARE_PATTERN = Pattern.compile("(?i)\\b(rare|superior)\\b");
    private static final Pattern UNIQUE_PATTERN = Pattern.compile("(?i)\\b(unique|epic|artifact)\\b");
    private static final Pattern LEGENDARY_PATTERN = Pattern.compile("(?i)\\b(legendary|mythic|divine)\\b");
    // Material-based rarity fallbacks
    private static final Set<Material> UNCOMMON_MATERIALS = Set.of(
            Material.IRON_SWORD, Material.IRON_AXE, Material.IRON_PICKAXE, Material.IRON_SHOVEL, Material.IRON_HOE,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS
    );
    private static final Set<Material> RARE_MATERIALS = Set.of(
            Material.DIAMOND_SWORD, Material.DIAMOND_AXE, Material.DIAMOND_PICKAXE, Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.ENCHANTED_BOOK, Material.NETHER_STAR, Material.BEACON
    );
    private static final Set<Material> UNIQUE_MATERIALS = Set.of(
            Material.NETHERITE_SWORD, Material.NETHERITE_AXE, Material.NETHERITE_PICKAXE, Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.ELYTRA, Material.TRIDENT, Material.TOTEM_OF_UNDYING
    );
    // Rarity colors for GlowingEntities
    private static final Map<String, ChatColor> RARITY_COLORS = Map.of(
            "common", ChatColor.WHITE,
            "uncommon", ChatColor.GREEN,
            "rare", ChatColor.AQUA,
            "unique", ChatColor.YELLOW,
            "legendary", ChatColor.GOLD
    );
    // Toggle name
    private static final String TOGGLE_NAME = "Glowing Drops";
    private static GlowingDropsManager instance;
    private final Logger logger;
    // Tracked glowing items
    private final Map<UUID, GlowingItemData> glowingItems = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerVisibleItems = new ConcurrentHashMap<>();
    private GlowingEntities glowingEntities;
    private boolean enabled = false;
    // Tasks
    private BukkitTask updateTask;
    private BukkitTask cleanupTask;
    // Configuration
    private int glowRadius = 32;
    private final long cleanupInterval = 600L; // 30 seconds
    private final long updateInterval = 10L; // 0.5 seconds
    private boolean autoEnableToggle = true; // Auto-enable toggle for new players
    private boolean showCommonItems = false; // Show common items too (for debugging)

    private GlowingDropsManager() {
        this.logger = YakRealms.getInstance().getLogger();
    }

    public static GlowingDropsManager getInstance() {
        if (instance == null) {
            instance = new GlowingDropsManager();
        }
        return instance;
    }

    public void onEnable() {
        if (enabled) {
            logger.warning("Glowing Drops system is already enabled");
            return;
        }

        logger.info("Initializing GlowingEntities-based Glowing Drops system...");

        // Initialize GlowingEntities (shaded library - no plugin dependency check needed)
        if (!initializeGlowingEntities()) {
            logger.severe("Failed to initialize GlowingEntities API - system disabled");
            return;
        }

        // Start tasks
        startUpdateTask();
        startCleanupTask();

        // Register events
        try {
            Bukkit.getPluginManager().registerEvents(new GlowingDropsListener(this), YakRealms.getInstance());
            logger.info("GlowingDropsListener registered successfully");
        } catch (Exception e) {
            logger.severe("Failed to register GlowingDropsListener: " + e.getMessage());
            return;
        }

        enabled = true;
        logger.info("GlowingEntities-based Glowing Drops system initialized successfully");

        // Auto-enable toggle for all online players if configured
        if (autoEnableToggle) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    if (!Toggles.isToggled(player, TOGGLE_NAME)) {
                        Toggles.setToggle(player, TOGGLE_NAME, true);
                        logger.fine("Auto-enabled glowing drops for player: " + player.getName());
                    }
                } catch (Exception e) {
                    logger.fine("Could not auto-enable toggle for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean initializeGlowingEntities() {
        try {
            // Initialize the shaded GlowingEntities library
            glowingEntities = new GlowingEntities(YakRealms.getInstance());
            logger.info("GlowingEntities API (shaded library) initialized successfully");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to initialize GlowingEntities API: " + e.getMessage());
            e.printStackTrace();

            // Try a delayed initialization
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                try {
                    glowingEntities = new GlowingEntities(YakRealms.getInstance());
                    logger.info("GlowingEntities API initialized successfully (delayed)");
                } catch (Exception ex) {
                    logger.severe("Failed to initialize GlowingEntities API (delayed): " + ex.getMessage());
                    ex.printStackTrace();
                }
            }, 20L);

            return false;
        }
    }

    public void onDisable() {
        if (!enabled) {
            return;
        }

        logger.info("Disabling Glowing Drops system...");

        // Cancel tasks
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        // Remove all glowing effects
        if (glowingEntities != null) {
            try {
                for (UUID itemId : glowingItems.keySet()) {
                    Item item = getItemByUUID(itemId);
                    if (item != null) {
                        removeGlowingItem(item);
                    }
                }

                // Disable the glowing entities API
                glowingEntities.disable();
            } catch (Exception e) {
                logger.warning("Error during GlowingEntities cleanup: " + e.getMessage());
            }
        }

        // Clear maps
        glowingItems.clear();
        playerVisibleItems.clear();

        enabled = false;
        logger.info("Glowing Drops system disabled");
    }

    private void startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                updateGlowingVisibility();
            } catch (Exception e) {
                logger.warning("Error in glowing drops update task: " + e.getMessage());
                if (YakRealms.getInstance().isDebugMode()) {
                    e.printStackTrace();
                }
            }
        }, updateInterval, updateInterval);
        logger.fine("Update task started with interval: " + updateInterval + " ticks");
    }

    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(YakRealms.getInstance(), () -> {
            try {
                cleanupDeadItems();
            } catch (Exception e) {
                logger.warning("Error in glowing drops cleanup task: " + e.getMessage());
                if (YakRealms.getInstance().isDebugMode()) {
                    e.printStackTrace();
                }
            }
        }, cleanupInterval, cleanupInterval);
        logger.fine("Cleanup task started with interval: " + cleanupInterval + " ticks");
    }

    public void addGlowingItem(Item item) {
        if (item == null || item.isDead() || glowingEntities == null || !enabled) {
            return;
        }

        ItemStack itemStack = item.getItemStack();
        String rarity = detectItemRarity(itemStack);

        // Only add glowing for uncommon and above (unless showing common items for debug)
        if ("common".equals(rarity) && !showCommonItems) {
            return;
        }

        UUID itemId = item.getUniqueId();
        GlowingItemData data = new GlowingItemData(itemId, rarity, System.currentTimeMillis());
        glowingItems.put(itemId, data);

        logger.fine("Added glowing item: " + itemId + " with rarity: " + rarity +
                " (" + itemStack.getType() + ")");
    }

    public void removeGlowingItem(Item item) {
        if (item == null || glowingEntities == null) {
            return;
        }

        UUID itemId = item.getUniqueId();
        glowingItems.remove(itemId);

        // Remove from all player visible sets
        for (Set<UUID> visibleSet : playerVisibleItems.values()) {
            visibleSet.remove(itemId);
        }

        // Remove glowing for all players using the correct API method
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                // Use entity ID instead of Entity object
                glowingEntities.unsetGlowing(item.getEntityId(), player);
            } catch (Exception e) {
                logger.fine("Error removing glow for player " + player.getName() + ": " + e.getMessage());
            }
        }

        logger.fine("Removed glowing item: " + itemId);
    }

    private void updateGlowingVisibility() {
        if (glowingEntities == null || !enabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check if player has the toggle enabled
            boolean hasToggle = false;
            try {
                hasToggle = Toggles.isToggled(player, TOGGLE_NAME);
            } catch (Exception e) {
                logger.fine("Error checking/setting toggle for player " + player.getName() + ": " + e.getMessage());
                // Assume enabled if toggle system is not working
                hasToggle = autoEnableToggle;
            }

            if (!hasToggle) {
                // Remove all visible items for this player
                Set<UUID> visibleItems = playerVisibleItems.remove(player.getUniqueId());
                if (visibleItems != null && !visibleItems.isEmpty()) {
                    for (UUID itemId : visibleItems) {
                        Item item = getItemByUUID(itemId);
                        if (item != null) {
                            try {
                                glowingEntities.unsetGlowing(item.getEntityId(), player);
                            } catch (Exception e) {
                                logger.fine("Error removing glow: " + e.getMessage());
                            }
                        }
                    }
                }
                continue;
            }

            Set<UUID> currentlyVisible = playerVisibleItems.computeIfAbsent(
                    player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()
            );

            Set<UUID> shouldBeVisible = new HashSet<>();

            // Check which items should be visible
            for (Map.Entry<UUID, GlowingItemData> entry : glowingItems.entrySet()) {
                UUID itemId = entry.getKey();
                Item item = getItemByUUID(itemId);

                if (item != null && !item.isDead() && isItemVisibleToPlayer(item, player)) {
                    shouldBeVisible.add(itemId);
                }
            }

            // Items to show
            Set<UUID> toShow = new HashSet<>(shouldBeVisible);
            toShow.removeAll(currentlyVisible);

            // Items to hide
            Set<UUID> toHide = new HashSet<>(currentlyVisible);
            toHide.removeAll(shouldBeVisible);

            // Update visibility
            if (!toShow.isEmpty() || !toHide.isEmpty()) {
                updatePlayerItemVisibility(player, toShow, toHide);
            }

            // Update the visible set
            currentlyVisible.clear();
            currentlyVisible.addAll(shouldBeVisible);
        }
    }

    private void updatePlayerItemVisibility(Player player, Set<UUID> toShow, Set<UUID> toHide) {
        // Show items
        for (UUID itemId : toShow) {
            Item item = getItemByUUID(itemId);
            if (item != null && !item.isDead()) {
                GlowingItemData data = glowingItems.get(itemId);
                if (data != null) {
                    try {
                        ChatColor color = RARITY_COLORS.get(data.getRarity());
                        if (color != null) {
                            // Try multiple API approaches to ensure colors work

                            // First try: Use the simple method if available
                            try {
                                // This might be available as a convenience method
                                glowingEntities.setGlowing(item, player, color);
                                logger.fine("Set glow (simple method) for item " + itemId + " (" + data.getRarity() + " = " + color + ") for player " + player.getName());
                            } catch (Exception e1) {
                                // Fallback: Use the complex method
                                try {
                                    String teamName = "yakrealms_" + data.getRarity();
                                    glowingEntities.setGlowing(item.getEntityId(), teamName, player, color);
                                    logger.fine("Set glow (complex method) for item " + itemId + " (" + data.getRarity() + " = " + color + ") for player " + player.getName());
                                } catch (Exception e2) {
                                    // Final fallback: Try with different parameters
                                    logger.warning("Failed to set glow color for item " + itemId + " after trying all methods: " + e2.getMessage());
                                }
                            }
                        } else {
                            logger.warning("No color found for rarity: " + data.getRarity());
                        }
                    } catch (Exception e) {
                        logger.warning("Error setting glow for item " + itemId + ": " + e.getMessage());
                        if (YakRealms.getInstance().isDebugMode()) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        // Hide items
        for (UUID itemId : toHide) {
            Item item = getItemByUUID(itemId);
            if (item != null) {
                try {
                    glowingEntities.unsetGlowing(item.getEntityId(), player);
                    logger.fine("Removed glow for item " + itemId + " for player " + player.getName());
                } catch (Exception e) {
                    logger.fine("Error removing glow for item " + itemId + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean isItemVisibleToPlayer(Item item, Player player) {
        if (item == null || item.isDead() || player == null || !player.isOnline()) {
            return false;
        }

        if (!item.getWorld().equals(player.getWorld())) {
            return false;
        }

        double distance = item.getLocation().distance(player.getLocation());
        return distance <= glowRadius;
    }

    private void cleanupDeadItems() {
        Set<UUID> toRemove = new HashSet<>();

        for (UUID itemId : glowingItems.keySet()) {
            Item item = getItemByUUID(itemId);
            if (item == null || item.isDead()) {
                toRemove.add(itemId);
            }
        }

        for (UUID itemId : toRemove) {
            glowingItems.remove(itemId);
            for (Set<UUID> visibleSet : playerVisibleItems.values()) {
                visibleSet.remove(itemId);
            }
        }

        if (!toRemove.isEmpty()) {
            logger.fine("Cleaned up " + toRemove.size() + " dead glowing items");
        }
    }

    public String detectItemRarity(ItemStack item) {
        if (item == null) {
            return "common";
        }

        ItemMeta meta = item.getItemMeta();

        // Check lore for rarity first
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                // Check all lore lines, not just the last one
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();

                    if (LEGENDARY_PATTERN.matcher(cleanLine).find()) return "legendary";
                    if (UNIQUE_PATTERN.matcher(cleanLine).find()) return "unique";
                    if (RARE_PATTERN.matcher(cleanLine).find()) return "rare";
                    if (UNCOMMON_PATTERN.matcher(cleanLine).find()) return "uncommon";
                    if (COMMON_PATTERN.matcher(cleanLine).find()) return "common";
                }
            }
        }

        // Check item name colors and text
        if (meta != null && meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            String cleanName = ChatColor.stripColor(displayName).toLowerCase();

            // Check name text patterns
            if (LEGENDARY_PATTERN.matcher(cleanName).find()) return "legendary";
            if (UNIQUE_PATTERN.matcher(cleanName).find()) return "unique";
            if (RARE_PATTERN.matcher(cleanName).find()) return "rare";
            if (UNCOMMON_PATTERN.matcher(cleanName).find()) return "uncommon";
            if (COMMON_PATTERN.matcher(cleanName).find()) return "common";

            // Check name colors
            if (displayName.contains(ChatColor.GOLD.toString()) ||
                    displayName.contains("§6")) {
                return "legendary";
            }
            if (displayName.contains(ChatColor.YELLOW.toString()) ||
                    displayName.contains("§e")) {
                return "unique";
            }
            if (displayName.contains(ChatColor.AQUA.toString()) ||
                    displayName.contains(ChatColor.DARK_AQUA.toString()) ||
                    displayName.contains("§b") || displayName.contains("§3")) {
                return "rare";
            }
            if (displayName.contains(ChatColor.GREEN.toString()) ||
                    displayName.contains(ChatColor.DARK_GREEN.toString()) ||
                    displayName.contains("§a") || displayName.contains("§2")) {
                return "uncommon";
            }
        }

        // Fallback: Check enchantments
        if (meta != null && meta.hasEnchants() && !meta.getEnchants().isEmpty()) {
            return "uncommon"; // Enchanted items are at least uncommon
        }

        // Fallback: Check material-based rarity
        Material material = item.getType();
        if (UNIQUE_MATERIALS.contains(material)) {
            return "unique";
        }
        if (RARE_MATERIALS.contains(material)) {
            return "rare";
        }
        if (UNCOMMON_MATERIALS.contains(material)) {
            return "uncommon";
        }

        return "common";
    }

    private Item getItemByUUID(UUID uuid) {
        if (uuid == null) {
            return null;
        }

        for (Item item : Bukkit.getWorlds().stream()
                .flatMap(world -> world.getEntitiesByClass(Item.class).stream())
                .toList()) {
            if (item.getUniqueId().equals(uuid)) {
                return item;
            }
        }
        return null;
    }

    public void updateAllItemsForPlayer(Player player) {
        // Clear the player's visible items to force refresh
        playerVisibleItems.remove(player.getUniqueId());
    }

    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getGlowRadius() {
        return glowRadius;
    }

    public void setGlowRadius(int radius) {
        this.glowRadius = Math.max(1, Math.min(radius, 64));
    }

    public String getToggleName() {
        return TOGGLE_NAME;
    }

    public GlowingEntities getGlowingEntities() {
        return glowingEntities;
    }

    public boolean isAutoEnableToggle() {
        return autoEnableToggle;
    }

    public void setAutoEnableToggle(boolean autoEnableToggle) {
        this.autoEnableToggle = autoEnableToggle;
    }

    public boolean isShowCommonItems() {
        return showCommonItems;
    }

    public void setShowCommonItems(boolean showCommonItems) {
        this.showCommonItems = showCommonItems;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled);
        stats.put("glowRadius", glowRadius);
        stats.put("trackedItems", glowingItems.size());
        stats.put("playersWithVisibleItems", playerVisibleItems.size());
        stats.put("toggleName", TOGGLE_NAME);
        stats.put("glowingEntitiesInitialized", glowingEntities != null);
        stats.put("autoEnableToggle", autoEnableToggle);
        stats.put("showCommonItems", showCommonItems);

        Map<String, Integer> rarityCount = new HashMap<>();
        for (GlowingItemData data : glowingItems.values()) {
            rarityCount.merge(data.getRarity(), 1, Integer::sum);
        }
        stats.put("itemsByRarity", rarityCount);

        return stats;
    }

    public static class GlowingItemData {
        private final UUID itemId;
        private final String rarity;
        private final long timestamp;

        public GlowingItemData(UUID itemId, String rarity, long timestamp) {
            this.itemId = itemId;
            this.rarity = rarity;
            this.timestamp = timestamp;
        }

        public UUID getItemId() {
            return itemId;
        }

        public String getRarity() {
            return rarity;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
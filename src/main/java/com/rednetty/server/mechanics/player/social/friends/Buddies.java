package com.rednetty.server.mechanics.player.social.friends;

import com.rednetty.server.YakRealms;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Enhanced buddy/friend system with improved UI, notifications,
 * status tracking, and better user experience.
 */
public class Buddies implements Listener {
    private static Buddies instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Enhanced tracking and caching
    private final Map<UUID, Set<String>> buddyCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOnlineTime = new ConcurrentHashMap<>();
    private final Map<UUID, BuddyNotificationSettings> notificationSettings = new ConcurrentHashMap<>();
    private final Set<UUID> openBuddyMenus = ConcurrentHashMap.newKeySet();

    // Performance tracking
    private final AtomicInteger totalBuddyRequests = new AtomicInteger(0);
    private final AtomicInteger totalBuddyAdds = new AtomicInteger(0);
    private final AtomicInteger totalBuddyRemoves = new AtomicInteger(0);

    // Tasks
    private BukkitTask cacheUpdateTask;
    private BukkitTask menuRefreshTask;

    // Configuration
    private static final int MAX_BUDDIES_DEFAULT = 50;
    private static final int MAX_BUDDIES_DONATOR = 100;
    private static final int BUDDY_MENU_SIZE = 54;
    private static final long CACHE_UPDATE_INTERVAL = 20L * 30; // 30 seconds
    private static final long MENU_REFRESH_INTERVAL = 20L * 5; // 5 seconds

    /**
     * Enhanced notification settings for buddies
     */
    private static class BuddyNotificationSettings {
        boolean joinNotifications = true;
        boolean quitNotifications = true;
        boolean soundEnabled = true;

        BuddyNotificationSettings() {}

        BuddyNotificationSettings(boolean join, boolean quit, boolean sound) {
            this.joinNotifications = join;
            this.quitNotifications = quit;
            this.soundEnabled = sound;
        }
    }

    private Buddies() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    public static Buddies getInstance() {
        if (instance == null) {
            instance = new Buddies();
        }
        return instance;
    }

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startEnhancedTasks();
        loadOnlinePlayerCache();
        YakRealms.log("Enhanced Buddies system has been enabled.");
    }

    public void onDisable() {
        stopTasks();
        clearCaches();
        YakRealms.log("Enhanced Buddies system has been disabled.");
    }

    /**
     * Start enhanced background tasks
     */
    private void startEnhancedTasks() {
        // Cache update task
        cacheUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateBuddyCache();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, CACHE_UPDATE_INTERVAL);

        // Menu refresh task
        menuRefreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshOpenMenus();
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, MENU_REFRESH_INTERVAL);

        logger.info("Started enhanced buddy system tasks");
    }

    /**
     * Load initial cache for online players
     */
    private void loadOnlinePlayerCache() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                buddyCache.put(player.getUniqueId(), new HashSet<>(yakPlayer.getBuddies()));
                lastOnlineTime.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }
        logger.info("Loaded buddy cache for " + buddyCache.size() + " online players");
    }

    /**
     * Update buddy cache from database
     */
    private void updateBuddyCache() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                Set<String> currentBuddies = new HashSet<>(yakPlayer.getBuddies());
                buddyCache.put(player.getUniqueId(), currentBuddies);
            }
        }
    }

    /**
     * Refresh open buddy menus
     */
    private void refreshOpenMenus() {
        if (openBuddyMenus.isEmpty()) return;

        Set<UUID> toRemove = new HashSet<>();
        for (UUID uuid : openBuddyMenus) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                toRemove.add(uuid);
                continue;
            }

            // Check if player still has buddy menu open
            String title = player.getOpenInventory().getTitle();
            if (title.contains("Buddy List")) {
                // Refresh the menu
                Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                    openBuddyMenu(player);
                });
            } else {
                toRemove.add(uuid);
            }
        }

        // Clean up closed menus
        openBuddyMenus.removeAll(toRemove);
    }

    /**
     * Enhanced buddy addition with validation and feedback
     */
    public boolean addBuddy(Player player, String buddyName) {
        if (player == null || buddyName == null || buddyName.trim().isEmpty()) {
            return false;
        }

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        String normalizedBuddyName = buddyName.trim();
        UUID playerUuid = player.getUniqueId();

        // Enhanced validation
        if (player.getName().equalsIgnoreCase(normalizedBuddyName)) {
            player.sendMessage(ChatColor.RED + "§l⚠ §cYou cannot add yourself as a buddy!");
            return false;
        }

        if (yakPlayer.isBuddy(normalizedBuddyName)) {
            player.sendMessage(ChatColor.YELLOW + "§f" + normalizedBuddyName + " §7is already on your buddy list!");
            return false;
        }

        // Check buddy limit
        int maxBuddies = getMaxBuddies(player);
        if (yakPlayer.getBuddies().size() >= maxBuddies) {
            player.sendMessage(ChatColor.RED + "§l⚠ §cBuddy list full! §7Maximum: " + maxBuddies);
            if (maxBuddies == MAX_BUDDIES_DEFAULT) {
                player.sendMessage(ChatColor.GRAY + "Upgrade to donator for more buddy slots!");
            }
            return false;
        }

        // Validate target player exists
        Player targetPlayer = findPlayer(normalizedBuddyName);
        if (targetPlayer == null) {
            // Check if player exists offline
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(normalizedBuddyName);
            if (!offlinePlayer.hasPlayedBefore()) {
                player.sendMessage(ChatColor.RED + "§l⚠ §cPlayer §f" + normalizedBuddyName + " §cnot found!");
                return false;
            }
        }

        // Add buddy with enhanced feedback
        if (yakPlayer.addBuddy(normalizedBuddyName)) {
            totalBuddyAdds.incrementAndGet();

            // Update cache
            buddyCache.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(normalizedBuddyName.toLowerCase());

            // Save to database
            playerManager.savePlayer(yakPlayer);

            // Enhanced success feedback
            player.sendMessage("");
            player.sendMessage("§a§l✓ §a§lBUDDY ADDED!");
            player.sendMessage("§f" + normalizedBuddyName + " §7has been added to your buddy list!");
            player.sendMessage("§7You will now receive notifications when they join/leave.");
            player.sendMessage("");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);

            // Notify target player if online
            if (targetPlayer != null && targetPlayer.isOnline()) {
                sendBuddyAddNotification(targetPlayer, player.getName());
            }

            return true;
        }

        return false;
    }

    /**
     * Enhanced buddy removal with confirmation
     */
    public boolean removeBuddy(Player player, String buddyName) {
        if (player == null || buddyName == null) return false;

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return false;

        String normalizedBuddyName = buddyName.trim();

        if (!yakPlayer.isBuddy(normalizedBuddyName)) {
            player.sendMessage(ChatColor.RED + "§f" + normalizedBuddyName + " §cis not on your buddy list!");
            return false;
        }

        // Remove buddy with enhanced feedback
        if (yakPlayer.removeBuddy(normalizedBuddyName)) {
            totalBuddyRemoves.incrementAndGet();

            // Update cache
            Set<String> playerBuddies = buddyCache.get(player.getUniqueId());
            if (playerBuddies != null) {
                playerBuddies.remove(normalizedBuddyName.toLowerCase());
            }

            // Save to database
            playerManager.savePlayer(yakPlayer);

            // Enhanced removal feedback
            player.sendMessage("");
            player.sendMessage("§c§l✗ §c§lBUDDY REMOVED");
            player.sendMessage("§f" + normalizedBuddyName + " §7has been removed from your buddy list.");
            player.sendMessage("");

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);

            return true;
        }

        return false;
    }

    /**
     * Get enhanced buddy list with status information
     */
    public List<BuddyInfo> getBuddyInfoList(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return new ArrayList<>();

        return yakPlayer.getBuddies().stream()
                .map(buddyName -> {
                    Player onlineBuddy = Bukkit.getPlayerExact(buddyName);
                    boolean isOnline = onlineBuddy != null && onlineBuddy.isOnline();

                    return new BuddyInfo(
                            buddyName,
                            isOnline,
                            isOnline ? null : getLastSeenTime(buddyName),
                            isOnline ? onlineBuddy.getWorld().getName() : null
                    );
                })
                .sorted((a, b) -> {
                    // Online buddies first, then alphabetical
                    if (a.isOnline != b.isOnline) {
                        return Boolean.compare(b.isOnline, a.isOnline);
                    }
                    return a.name.compareToIgnoreCase(b.name);
                })
                .collect(Collectors.toList());
    }

    /**
     * Enhanced buddy menu with status and management options
     */
    public void openBuddyMenu(Player player) {
        List<BuddyInfo> buddies = getBuddyInfoList(player);

        Inventory menu = Bukkit.createInventory(null, BUDDY_MENU_SIZE,
                "§6§l✦ §e§lBUDDY LIST §8(" + buddies.size() + "/" + getMaxBuddies(player) + ")");

        // Add buddies to menu
        int slot = 0;
        for (BuddyInfo buddy : buddies) {
            if (slot >= BUDDY_MENU_SIZE - 9) break; // Leave room for controls

            ItemStack item = createBuddyItem(buddy);
            menu.setItem(slot, item);
            slot++;
        }

        // Add control items
        addMenuControls(menu, player, buddies.size());

        // Track open menu
        openBuddyMenus.add(player.getUniqueId());

        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Create buddy item for menu
     */
    private ItemStack createBuddyItem(BuddyInfo buddy) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        // Set player head
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(buddy.name);
        meta.setOwningPlayer(offlinePlayer);

        // Enhanced display name with status
        String status = buddy.isOnline ? "§a§lOnline" : "§7§lOffline";
        String statusIcon = buddy.isOnline ? "§a●" : "§7●";
        meta.setDisplayName(statusIcon + " §f§l" + buddy.name + " " + status);

        // Enhanced lore with information
        List<String> lore = new ArrayList<>();
        lore.add("§7Status: " + (buddy.isOnline ? "§aOnline" : "§7Offline"));

        if (buddy.isOnline && buddy.world != null) {
            lore.add("§7World: §f" + buddy.world);
        } else if (!buddy.isOnline && buddy.lastSeen != null) {
            lore.add("§7Last seen: §f" + buddy.lastSeen);
        }

        lore.add("");
        lore.add("§e§lLeft-click §7to teleport (if online)");
        lore.add("§c§lRight-click §7to remove from buddy list");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Add control items to buddy menu
     */
    private void addMenuControls(Inventory menu, Player player, int buddyCount) {
        // Add buddy button
        ItemStack addBuddy = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addBuddy.getItemMeta();
        addMeta.setDisplayName("§a§l+ Add Buddy");

        List<String> addLore = new ArrayList<>();
        addLore.add("§7Click to add a new buddy!");
        addLore.add("§7Current: §f" + buddyCount + "§7/§f" + getMaxBuddies(player));
        addMeta.setLore(addLore);
        addBuddy.setItemMeta(addMeta);

        // Settings button
        ItemStack settings = new ItemStack(Material.REDSTONE);
        ItemMeta settingsMeta = settings.getItemMeta();
        settingsMeta.setDisplayName("§6§l⚙ Notification Settings");

        List<String> settingsLore = new ArrayList<>();
        settingsLore.add("§7Configure buddy notifications");
        BuddyNotificationSettings playerSettings = notificationSettings.getOrDefault(
                player.getUniqueId(), new BuddyNotificationSettings());
        settingsLore.add("§7Join notifications: " + (playerSettings.joinNotifications ? "§aEnabled" : "§cDisabled"));
        settingsLore.add("§7Quit notifications: " + (playerSettings.quitNotifications ? "§aEnabled" : "§cDisabled"));
        settingsLore.add("§7Sound effects: " + (playerSettings.soundEnabled ? "§aEnabled" : "§cDisabled"));
        settingsMeta.setLore(settingsLore);
        settings.setItemMeta(settingsMeta);

        // Info button
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§b§l📖 Buddy System Info");

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7The buddy system allows you to:");
        infoLore.add("§8• §7Track when friends join/leave");
        infoLore.add("§8• §7See their online status");
        infoLore.add("§8• §7Quick teleport to buddies");
        infoLore.add("§8• §7Manage your friend list");
        infoLore.add("");
        infoLore.add("§7Buddy limit: §f" + getMaxBuddies(player));
        if (getMaxBuddies(player) == MAX_BUDDIES_DEFAULT) {
            infoLore.add("§7Upgrade to donator for more slots!");
        }
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c§lClose Menu");
        close.setItemMeta(closeMeta);

        // Place control items
        menu.setItem(45, addBuddy);
        menu.setItem(47, settings);
        menu.setItem(49, info);
        menu.setItem(53, close);

        // Fill empty slots with glass panes
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§8");
        glass.setItemMeta(glassMeta);

        for (int i = 46; i <= 52; i++) {
            if (menu.getItem(i) == null) {
                menu.setItem(i, glass);
            }
        }
    }

    /**
     * Handle buddy menu clicks
     */
    @EventHandler
    public void onBuddyMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().contains("BUDDY LIST")) return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        String displayName = clicked.getItemMeta().getDisplayName();

        // Handle control buttons
        if (clicked.getType() == Material.EMERALD && displayName.contains("Add Buddy")) {
            player.closeInventory();
            player.sendMessage("§e§lType the name of the player you want to add as a buddy:");
            // TODO: Implement text input system or command prompt
            return;
        }

        if (clicked.getType() == Material.REDSTONE && displayName.contains("Notification Settings")) {
            openNotificationSettings(player);
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Handle buddy item clicks
        if (clicked.getType() == Material.PLAYER_HEAD) {
            String buddyName = extractBuddyName(displayName);
            if (buddyName != null) {
                if (event.isLeftClick()) {
                    handleBuddyTeleport(player, buddyName);
                } else if (event.isRightClick()) {
                    handleBuddyRemove(player, buddyName);
                }
            }
        }
    }

    /**
     * Enhanced buddy notifications with better formatting
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();

        // Skip notifications for staff in vanish mode
        if (joiningPlayer.isOp() || joiningPlayer.hasPermission("yakrealms.staff")) return;

        String joiningPlayerName = joiningPlayer.getName();

        // Update cache
        lastOnlineTime.put(joiningPlayer.getUniqueId(), System.currentTimeMillis());

        // Notify buddies with enhanced messages
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(joiningPlayer)) continue;

                BuddyNotificationSettings settings = notificationSettings.getOrDefault(
                        onlinePlayer.getUniqueId(), new BuddyNotificationSettings());

                if (!settings.joinNotifications) continue;

                YakPlayer onlineYakPlayer = playerManager.getPlayer(onlinePlayer);
                if (onlineYakPlayer != null && onlineYakPlayer.isBuddy(joiningPlayerName)) {
                    // Enhanced join notification
                    onlinePlayer.sendMessage("§a§l✦ §a§lBuddy Online!");
                    onlinePlayer.sendMessage("§f" + joiningPlayerName + " §7has joined the server");

                    if (settings.soundEnabled) {
                        onlinePlayer.playSound(onlinePlayer.getLocation(),
                                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                    }
                }
            }
        }, 20L); // 1 second delay
    }

    /**
     * Enhanced buddy leave notifications
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player leavingPlayer = event.getPlayer();

        // Skip notifications for staff
        if (leavingPlayer.isOp() || leavingPlayer.hasPermission("yakrealms.staff")) return;

        String leavingPlayerName = leavingPlayer.getName();
        UUID leavingUuid = leavingPlayer.getUniqueId();

        // Update cache
        lastOnlineTime.put(leavingUuid, System.currentTimeMillis());

        // Clean up tracking
        openBuddyMenus.remove(leavingUuid);

        // Notify buddies
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(leavingPlayer)) continue;

            BuddyNotificationSettings settings = notificationSettings.getOrDefault(
                    onlinePlayer.getUniqueId(), new BuddyNotificationSettings());

            if (!settings.quitNotifications) continue;

            YakPlayer onlineYakPlayer = playerManager.getPlayer(onlinePlayer);
            if (onlineYakPlayer != null && onlineYakPlayer.isBuddy(leavingPlayerName)) {
                // Enhanced leave notification
                onlinePlayer.sendMessage("§c§l✦ §c§lBuddy Offline");
                onlinePlayer.sendMessage("§f" + leavingPlayerName + " §7has left the server");

                if (settings.soundEnabled) {
                    onlinePlayer.playSound(onlinePlayer.getLocation(),
                            Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f);
                }
            }
        }
    }

    // Utility methods and helper classes

    /**
     * Buddy information class
     */
    public static class BuddyInfo {
        public final String name;
        public final boolean isOnline;
        public final String lastSeen;
        public final String world;

        BuddyInfo(String name, boolean isOnline, String lastSeen, String world) {
            this.name = name;
            this.isOnline = isOnline;
            this.lastSeen = lastSeen;
            this.world = world;
        }
    }

    private int getMaxBuddies(Player player) {
        if (player.hasPermission("yakrealms.donator")) {
            return MAX_BUDDIES_DONATOR;
        }
        return MAX_BUDDIES_DEFAULT;
    }

    private String getLastSeenTime(String playerName) {
        @SuppressWarnings("deprecation")
        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            long lastPlayed = offlinePlayer.getLastPlayed();
            long diffMinutes = (System.currentTimeMillis() - lastPlayed) / (1000 * 60);

            if (diffMinutes < 60) {
                return diffMinutes + " minutes ago";
            } else if (diffMinutes < 1440) {
                return (diffMinutes / 60) + " hours ago";
            } else {
                return (diffMinutes / 1440) + " days ago";
            }
        }
        return "Never";
    }

    private void sendBuddyAddNotification(Player targetPlayer, String adderName) {
        targetPlayer.sendMessage("§a§l✦ §a§lNew Buddy!");
        targetPlayer.sendMessage("§f" + adderName + " §7has added you as a buddy!");
        targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    private String extractBuddyName(String displayName) {
        // Extract buddy name from display name format: "● Name Status"
        String cleaned = ChatColor.stripColor(displayName);
        String[] parts = cleaned.split(" ");
        if (parts.length >= 2) {
            return parts[1]; // Get the name part
        }
        return null;
    }

    private void handleBuddyTeleport(Player player, String buddyName) {
        Player buddy = Bukkit.getPlayerExact(buddyName);
        if (buddy == null || !buddy.isOnline()) {
            player.sendMessage("§c§l⚠ §f" + buddyName + " §cis not online!");
            return;
        }

        // TODO: Implement teleport with cooldown and restrictions
        player.sendMessage("§e§lTeleporting to §f" + buddyName + "§e§l...");
        // player.teleport(buddy.getLocation());
    }

    private void handleBuddyRemove(Player player, String buddyName) {
        // Confirmation could be added here
        if (removeBuddy(player, buddyName)) {
            // Refresh menu
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                openBuddyMenu(player);
            });
        }
    }

    private void openNotificationSettings(Player player) {
        // TODO: Implement notification settings menu
        player.sendMessage("§e§lNotification settings coming soon!");
    }

    public Player findPlayer(String partialName) {
        Player exactMatch = Bukkit.getPlayerExact(partialName);
        if (exactMatch != null) return exactMatch;

        List<Player> matches = Bukkit.matchPlayer(partialName);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private void stopTasks() {
        if (cacheUpdateTask != null && !cacheUpdateTask.isCancelled()) {
            cacheUpdateTask.cancel();
        }
        if (menuRefreshTask != null && !menuRefreshTask.isCancelled()) {
            menuRefreshTask.cancel();
        }
    }

    private void clearCaches() {
        buddyCache.clear();
        lastOnlineTime.clear();
        notificationSettings.clear();
        openBuddyMenus.clear();
    }

    // Static helper methods for backward compatibility
    public static List<String> getBuddies(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return new ArrayList<>();

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        return yakPlayer != null ? (List<String>) yakPlayer.getBuddies() : new ArrayList<>();
    }

    public List<String> getBuddies(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null ? (List<String>) yakPlayer.getBuddies() : new ArrayList<>();
    }

    public boolean isBuddy(Player player, String buddyName) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && yakPlayer.isBuddy(buddyName);
    }

    // Performance and statistics methods
    public void logPerformanceStats() {
        logger.info("Buddy System Performance:");
        logger.info("  Total Requests: " + totalBuddyRequests.get());
        logger.info("  Total Adds: " + totalBuddyAdds.get());
        logger.info("  Total Removes: " + totalBuddyRemoves.get());
        logger.info("  Cache Size: " + buddyCache.size());
        logger.info("  Open Menus: " + openBuddyMenus.size());
    }
}
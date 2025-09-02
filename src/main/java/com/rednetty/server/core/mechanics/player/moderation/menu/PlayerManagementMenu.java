package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.moderation.ModerationHistory;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationRepository;
import com.rednetty.server.utils.input.ChatInputHandler;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Clean and intuitive player management menu
 */
public class PlayerManagementMenu extends Menu {

    private final ModerationRepository repository;
    private final YakPlayerManager playerManager;
    private final MenuBreadcrumb breadcrumb;
    private final ChatInputHandler chatInputHandler;
    
    private String searchQuery = "";
    private int currentPage = 0;

    public PlayerManagementMenu(Player player) {
        super(player, "<blue><bold>Player Management", 27);
        this.repository = ModerationRepository.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
        this.breadcrumb = new MenuBreadcrumb(player);
        this.chatInputHandler = ChatInputHandler.getInstance();
        
        breadcrumb.push("Main Menu", ModerationMainMenu::new);
        breadcrumb.push("Player Management", PlayerManagementMenu::new);
        
        setupMenu();
    }

    private void setupMenu() {
        // Clear existing items
        items.clear();
        
        // Header with breadcrumbs
        setItem(4, createHeaderItem());
        
        // Search options
        setupSearchOptions();
        
        // Recently active players
        setupRecentPlayers();
        
        // Navigation
        setupNavigation();
    }
    
    private void setupSearchOptions() {
        // Search by name
        setItem(10, new MenuItem(Material.NAME_TAG)
            .setDisplayName("<yellow><bold>Search by Name")
            .addLoreLine("<gray>Search for players by username")
            .addLoreLine("")
            .addLoreLine("<green>▶ Click to search")
            .setClickHandler((p, slot) -> {
                chatInputHandler.startSearchInput(p, "Player Search", 
                    this::handlePlayerSearchInput,
                    () -> new PlayerManagementMenu(p).open()
                );
            }));
        
        // Search by IP
        if (player.hasPermission("yakrealms.staff.advanced")) {
            setItem(12, new MenuItem(Material.COMPASS)
                .setDisplayName("<gold><bold>Search by IP")
                .addLoreLine("<gray>Find players by IP address")
                .addLoreLine("")
                .addLoreLine("<green>▶ Click to search")
                .setClickHandler((p, slot) -> {
                    chatInputHandler.startSearchInput(p, "IP Address Search",
                        this::handleIPSearchInput,
                        () -> new PlayerManagementMenu(p).open()
                    );
                }));
        } else {
            setItem(12, new MenuItem(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("<gray>Search by IP")
                .addLoreLine("<red>Requires advanced permissions"));
        }
        
        // Online players
        setItem(14, new MenuItem(Material.EMERALD)
            .setDisplayName("<green><bold>Online Players")
            .addLoreLine("<gray>View all currently online players")
            .addLoreLine("<gray>Players: <white>" + Bukkit.getOnlinePlayers().size())
            .addLoreLine("")
            .addLoreLine("<green>▶ Click to view")
            .setClickHandler((p, slot) -> showOnlinePlayers()));
        
        // Recent bans
        setItem(16, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lRecent Bans")
            .addLoreLine(ChatColor.GRAY + "View recently banned players")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to view")
            .setClickHandler((p, slot) -> showRecentBans()));
    }
    
    private void setupRecentPlayers() {
        // This would show the most recently active players
        setItem(22, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.AQUA + "&lRecent Activity")
            .addLoreLine(ChatColor.GRAY + "Players who joined recently")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to view")
            .setClickHandler((p, slot) -> showRecentActivity()));
    }
    
    private void setupNavigation() {
        // Back to main menu
        setItem(18, breadcrumb.createBackButton(18));
        
        // Home
        setItem(26, breadcrumb.createHomeButton(26));
        
        // Breadcrumb display
        setItem(0, breadcrumb.createBreadcrumbDisplay());
        
        // Quick actions
        setItem(8, new MenuItem(Material.DIAMOND_SWORD)
            .setDisplayName(ChatColor.GOLD + "&lQuick Actions")
            .addLoreLine(ChatColor.GRAY + "Fast moderation commands")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click for menu")
            .setClickHandler((p, slot) -> showQuickActions()));
    }
    
    private MenuItem createHeaderItem() {
        return new MenuItem(Material.PLAYER_HEAD)
            .setDisplayName(ChatColor.BLUE + "&l👥 Player Management")
            .addLoreLine(ChatColor.GRAY + "Search and manage players")
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "📊 Quick Stats:")
            .addLoreLine(ChatColor.GRAY + "  Online Players: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size())
            .addLoreLine(ChatColor.GRAY + "  Total Registered: " + ChatColor.WHITE + "Loading...")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Select a search option below")
            .setGlowing(true);
    }
    
    private void showOnlinePlayers() {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No players are currently online.");
            return;
        }
        
        // Create a new menu showing online players
        OnlinePlayersMenu onlineMenu = new OnlinePlayersMenu(player, onlinePlayers, breadcrumb);
        onlineMenu.open();
    }
    
    private void showRecentBans() {
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "        ⚠ RECENT BANS");
        player.sendMessage(ChatColor.RED + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GRAY + "Loading recent ban data...");
        player.sendMessage(ChatColor.RED + "═══════════════════════════════════════");
        
        // Would implement actual ban history retrieval here
    }
    
    private void showRecentActivity() {
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "        📊 RECENT ACTIVITY");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GRAY + "Loading recent player activity...");
        player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
    }
    
    private void showQuickActions() {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "        ⚡ QUICK ACTIONS");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.WHITE + "Available commands:");
        player.sendMessage(ChatColor.GRAY + "  /warn <player> <reason>" + ChatColor.DARK_GRAY + " - Issue warning");
        player.sendMessage(ChatColor.GRAY + "  /mute <player> <time> <reason>" + ChatColor.DARK_GRAY + " - Mute player");
        player.sendMessage(ChatColor.GRAY + "  /ban <player> <reason>" + ChatColor.DARK_GRAY + " - Ban player");
        player.sendMessage(ChatColor.GRAY + "  /kick <player> <reason>" + ChatColor.DARK_GRAY + " - Kick player");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "💡 Tip: Use tab completion for player names");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
    }
    
    private MenuItem createPlayerItem(Player target, boolean online) {
        YakPlayer yakPlayer = playerManager.getPlayer(target.getUniqueId());
        String status = online ? ChatColor.GREEN + "🟢 Online" : ChatColor.GRAY + "🔴 Offline";
        
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            skull.setItemMeta(meta);
        }
        
        return new MenuItem(skull)
            .setDisplayName(ChatColor.YELLOW + target.getName())
            .addLoreLine(ChatColor.GRAY + "Status: " + status)
            .addLoreLine(ChatColor.GRAY + "Rank: " + ChatColor.WHITE + ModerationMechanics.getRank(target).name())
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "📊 Quick Info:")
            .addLoreLine(ChatColor.GRAY + "  Warnings: " + ChatColor.WHITE + "0") // Would get actual data
            .addLoreLine(ChatColor.GRAY + "  Last Punishment: " + ChatColor.WHITE + "None")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to manage")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, target.getUniqueId(), target.getName()).open());
    }
    
    /**
     * Handle player name search input
     */
    private void handlePlayerSearchInput(String playerName) {
        if (playerName.trim().isEmpty()) {
            MessageUtils.send(player, "<red>❌ Player name cannot be empty.");
            new PlayerManagementMenu(player).open();
            return;
        }
        
        // Search for player (exact match first, then partial matches)
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            // Found online player
            showPlayerSearchResults(playerName, List.of(onlinePlayer));
            return;
        }
        
        // Search offline players
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            showPlayerSearchResults(playerName, List.of());
            return;
        }
        
        // No exact match, search for partial matches
        searchPartialMatches(playerName);
    }
    
    /**
     * Handle IP address search input
     */
    private void handleIPSearchInput(String ipAddress) {
        if (ipAddress.trim().isEmpty()) {
            MessageUtils.send(player, "<red>❌ IP address cannot be empty.");
            new PlayerManagementMenu(player).open();
            return;
        }
        
        // Validate IP format (basic validation)
        if (!isValidIPAddress(ipAddress.trim())) {
            MessageUtils.send(player, "<red>❌ Invalid IP address format.");
            new PlayerManagementMenu(player).open();
            return;
        }
        
        MessageUtils.send(player, "<yellow>🔍 Searching for players with IP: <white>" + ipAddress);
        MessageUtils.send(player, "<gray>This feature requires database implementation...");
        
        // TODO: Implement actual IP search in database
        // For now, just return to menu
        new PlayerManagementMenu(player).open();
    }
    
    /**
     * Search for partial player name matches
     */
    private void searchPartialMatches(String searchTerm) {
        List<Player> matches = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getName().toLowerCase().contains(searchTerm.toLowerCase()))
                .collect(Collectors.toList());
                
        if (matches.isEmpty()) {
            MessageUtils.send(player, "<red>❌ No players found matching '" + searchTerm + "'");
            new PlayerManagementMenu(player).open();
        } else {
            showPlayerSearchResults(searchTerm, matches);
        }
    }
    
    /**
     * Show search results to the player
     */
    private void showPlayerSearchResults(String searchTerm, List<Player> results) {
        if (results.size() == 1) {
            // Single result - go directly to player actions
            Player target = results.get(0);
            new PlayerActionsMenu(player, target.getUniqueId(), target.getName()).open();
        } else if (results.isEmpty()) {
            // No online results, show offline player options
            MessageUtils.send(player, "<yellow>Found offline player: <white>" + searchTerm);
            OfflinePlayer offline = Bukkit.getOfflinePlayer(searchTerm);
            new PlayerActionsMenu(player, offline.getUniqueId(), searchTerm).open();
        } else {
            // Multiple results - show selection menu
            MenuBreadcrumb newBreadcrumb = new MenuBreadcrumb(player);
            newBreadcrumb.push("Main Menu", (p) -> new ModerationMainMenu(p));
            newBreadcrumb.push("Player Management", (p) -> new PlayerManagementMenu(p));
            newBreadcrumb.push("Search Results", (p) -> new PlayerManagementMenu(p));
            
            new OnlinePlayersMenu(player, results, newBreadcrumb).open();
        }
    }
    
    /**
     * Basic IP address validation
     */
    private boolean isValidIPAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
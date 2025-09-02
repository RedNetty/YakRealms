package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.moderation.ModerationDashboard;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationStats;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Clean and intuitive main moderation menu
 * Redesigned for better navigation and user experience
 */
public class ModerationMainMenu extends Menu {

    private final ModerationDashboard dashboard;
    
    public ModerationMainMenu(Player player) {
        super(player, "<dark_red><bold>Moderation Panel", 27);
        this.dashboard = ModerationDashboard.getInstance();
        setupMenu();
    }
    
    private void setupMenu() {
        // Clear existing items
        items.clear();
        
        // Header with server info
        setItem(4, createHeaderItem());
        
        // Core moderation functions (organized in a clean grid)
        setupCoreOptions();
        
        // Navigation
        setupNavigation();
    }
    
    private void setupCoreOptions() {
        // Row 1: Primary Actions
        setItem(10, new MenuItem(Material.PLAYER_HEAD)
            .setDisplayName(ChatColor.AQUA + "&lPlayer Management")
            .addLoreLine(ChatColor.GRAY + "• Search players")
            .addLoreLine(ChatColor.GRAY + "• View history") 
            .addLoreLine(ChatColor.GRAY + "• Issue punishments")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to open")
            .setClickHandler((p, slot) -> new PlayerManagementMenu(p).open()));
        
        setItem(12, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.YELLOW + "&lPunishment History")
            .addLoreLine(ChatColor.GRAY + "• Recent actions")
            .addLoreLine(ChatColor.GRAY + "• Search by player")
            .addLoreLine(ChatColor.GRAY + "• Filter by type/date")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to view")
            .setClickHandler((p, slot) -> new PunishmentHistoryMenu(p).open()));
        
        setItem(14, new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.RED + "&lLive Dashboard")
            .addLoreLine(ChatColor.GRAY + "• Real-time monitoring")
            .addLoreLine(ChatColor.GRAY + "• Staff activity")
            .addLoreLine(ChatColor.GRAY + "• System alerts")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to view")
            .setClickHandler((p, slot) -> openDashboard()));
        
        setItem(16, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.GOLD + "&lAppeal Center")
            .addLoreLine(ChatColor.GRAY + "• Review appeals")
            .addLoreLine(ChatColor.GRAY + "• Pending requests")
            .addLoreLine(ChatColor.GRAY + "• Appeal history")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to manage")
            .setClickHandler((p, slot) -> new AppealManagementMenu(p).open()));
        
        // Row 2: Advanced Options (only for higher staff)
        if (player.hasPermission("yakrealms.staff.advanced") || player.hasPermission("yakrealms.admin")) {
            setItem(19, new MenuItem(Material.GOLDEN_SWORD)
                .setDisplayName(ChatColor.GOLD + "&lStaff Tools")
                .addLoreLine(ChatColor.GRAY + "• Advanced utilities")
                .addLoreLine(ChatColor.GRAY + "• Bulk actions")
                .addLoreLine(ChatColor.GRAY + "• System tools")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "▶ Click to access")
                .setClickHandler((p, slot) -> {
                    if (!ModerationMechanics.isStaff(p)) {
                        p.sendMessage(ChatColor.RED + "Access denied - Staff permissions required");
                        return;
                    }
                    new StaffToolsMenu(p).open();
                }));
        } else {
            setItem(19, new MenuItem(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName(ChatColor.GRAY + "Staff Tools")
                .addLoreLine(ChatColor.RED + "Requires elevated permissions"));
        }
        
        if (player.hasPermission("yakrealms.admin")) {
            setItem(25, new MenuItem(Material.COMPARATOR)
                .setDisplayName(ChatColor.LIGHT_PURPLE + "&lSystem Settings")
                .addLoreLine(ChatColor.GRAY + "• Configure rules")
                .addLoreLine(ChatColor.GRAY + "• Escalation settings")
                .addLoreLine(ChatColor.GRAY + "• Alert thresholds")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "▶ Click to configure")
                .setClickHandler((p, slot) -> new SystemSettingsMenu(p).open()));
        } else {
            setItem(25, new MenuItem(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName(ChatColor.GRAY + "System Settings")
                .addLoreLine(ChatColor.RED + "Admin access only"));
        }
    }
    
    private void setupNavigation() {
        // Help
        setItem(0, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.GREEN + "&lHelp & Commands")
            .addLoreLine(ChatColor.GRAY + "View command help")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click for help")
            .setClickHandler((p, slot) -> showHelpMenu()));
        
        // Quick Stats
        setItem(8, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.AQUA + "&lQuick Stats")
            .addLoreLine(ChatColor.GRAY + "View your recent actions")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to view")
            .setClickHandler((p, slot) -> showQuickStats()));
        
        // Close
        setItem(22, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .addLoreLine(ChatColor.GRAY + "Exit the moderation panel")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "▶ Click to close")
            .setClickHandler((p, slot) -> close()));
    }
    
    private MenuItem createHeaderItem() {
        ModerationStats stats = dashboard.getCurrentStats();
        return new MenuItem(Material.DIAMOND_BLOCK)
            .setDisplayName(ChatColor.GOLD + "&l🛡 YakRealms Moderation")
            .addLoreLine(ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + player.getName())
            .addLoreLine(ChatColor.YELLOW + "Rank: " + ChatColor.WHITE + ModerationMechanics.getRank(player).name())
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "📊 Server Status:")
            .addLoreLine(ChatColor.GRAY + "  Players Online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size())
            .addLoreLine(ChatColor.GRAY + "  Active Punishments: " + ChatColor.WHITE + stats.getActivePunishments())
            .addLoreLine(ChatColor.GRAY + "  Actions Today: " + ChatColor.WHITE + stats.getActionsLast24Hours())
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "System Status: " + ChatColor.GREEN + "✓ Online")
            .setGlowing(true);
    }
    
    private void openDashboard() {
        dashboard.sendDashboardSummary(player);
        player.sendMessage(ChatColor.YELLOW + "💡 Use /moddash gui for the interactive dashboard");
    }
    
    private void showQuickStats() {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "        📊 YOUR RECENT ACTIVITY");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.WHITE + "Actions Today: " + ChatColor.GREEN + "0"); // Would query actual data
        player.sendMessage(ChatColor.WHITE + "This Week: " + ChatColor.GREEN + "0");
        player.sendMessage(ChatColor.WHITE + "This Month: " + ChatColor.GREEN + "0");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/modhistory " + player.getName() + ChatColor.GRAY + " for detailed history");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
    }
    
    private void showHelpMenu() {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "        🔧 MODERATION COMMANDS");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.WHITE + "Quick Access:");
        player.sendMessage(ChatColor.GRAY + "  /modmenu" + ChatColor.DARK_GRAY + " - Open this menu");
        player.sendMessage(ChatColor.GRAY + "  /moddash" + ChatColor.DARK_GRAY + " - Open dashboard");
        player.sendMessage(ChatColor.GRAY + "  /modhistory <player>" + ChatColor.DARK_GRAY + " - View history");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Basic Actions:");
        player.sendMessage(ChatColor.GRAY + "  /warn <player> <reason>" + ChatColor.DARK_GRAY + " - Issue warning");
        player.sendMessage(ChatColor.GRAY + "  /mute <player> <time> <reason>" + ChatColor.DARK_GRAY + " - Mute player");
        player.sendMessage(ChatColor.GRAY + "  /ban <player> <reason>" + ChatColor.DARK_GRAY + " - Ban player");
        player.sendMessage(ChatColor.GRAY + "  /kick <player> <reason>" + ChatColor.DARK_GRAY + " - Kick player");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Your Access Level: " + 
            (ModerationMechanics.isStaff(player) ? ChatColor.GREEN + "Staff" : ChatColor.RED + "Player"));
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
    }
    
    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player) && !player.hasPermission("yakrealms.moderation.menu")) {
            player.sendMessage(ChatColor.RED + "❌ You don't have permission to access the moderation menu!");
            return;
        }
    }
}
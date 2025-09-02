package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationStats;
import com.rednetty.server.core.mechanics.player.moderation.ModerationDashboard;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Advanced staff tools menu providing access to bulk operations and system utilities
 */
public class StaffToolsMenu extends Menu {

    private final ModerationDashboard dashboard;

    public StaffToolsMenu(Player player) {
        super(player, "&6&lStaff Tools", 54);
        this.dashboard = ModerationDashboard.getInstance();
        setupMenu();
    }

    private void setupMenu() {
        createBorder(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GOLD + " ");
        
        // Header
        setupHeader();
        
        // Bulk operations
        setupBulkOperations();
        
        // System tools
        setupSystemTools();
        
        // Monitoring tools
        setupMonitoringTools();
        
        // Administrative tools
        setupAdministrativeTools();
        
        // Navigation
        setupNavigation();
    }

    private void setupHeader() {
        setItem(4, new MenuItem(Material.GOLDEN_SWORD)
            .setDisplayName(ChatColor.GOLD + "&lStaff Tools")
            .addLoreLine(ChatColor.GRAY + "Advanced moderation utilities")
            .addLoreLine(ChatColor.GRAY + "Bulk operations and system management")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Staff Level Required")
            .setGlowing(true));
    }

    private void setupBulkOperations() {
        // Mass ban by IP
        setItem(19, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.DARK_RED + "&lMass IP Ban")
            .addLoreLine(ChatColor.GRAY + "Ban multiple accounts by IP address")
            .addLoreLine(ChatColor.GRAY + "Useful for dealing with alt accounts")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Click to configure")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for this tool!");
                    return;
                }
                new MassIPBanMenu(p).open();
            }));

        // Bulk mute
        setItem(20, new MenuItem(Material.CHAIN)
            .setDisplayName(ChatColor.GOLD + "&lBulk Mute")
            .addLoreLine(ChatColor.GRAY + "Mute multiple players at once")
            .addLoreLine(ChatColor.GRAY + "Based on criteria or manual selection")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new BulkMuteMenu(p).open()));

        // Clear chat
        setItem(21, new MenuItem(Material.SPONGE)
            .setDisplayName(ChatColor.YELLOW + "&lClear Chat")
            .addLoreLine(ChatColor.GRAY + "Clear chat for all players")
            .addLoreLine(ChatColor.GRAY + "Useful for cleaning up spam")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to clear")
            .setClickHandler((p, slot) -> {
                for (int i = 0; i < 100; i++) {
                    Bukkit.broadcastMessage("");
                }
                Bukkit.broadcastMessage(ChatColor.GREEN + "Chat cleared by " + p.getName());
                p.sendMessage(ChatColor.GREEN + "Chat has been cleared!");
            }));

        // Lockdown mode
        setItem(22, new MenuItem(Material.IRON_DOOR)
            .setDisplayName(ChatColor.RED + "&lServer Lockdown")
            .addLoreLine(ChatColor.GRAY + "Prevent new players from joining")
            .addLoreLine(ChatColor.GRAY + "Useful during incidents")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Click to toggle")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for lockdown!");
                    return;
                }
                toggleLockdown();
            }));

        // Emergency broadcast
        setItem(23, new MenuItem(Material.BELL)
            .setDisplayName(ChatColor.AQUA + "&lEmergency Broadcast")
            .addLoreLine(ChatColor.GRAY + "Send important message to all players")
            .addLoreLine(ChatColor.GRAY + "High visibility announcement")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to broadcast")
            .setClickHandler((p, slot) -> initiateEmergencyBroadcast()));
    }

    private void setupSystemTools() {
        // Player lookup tools
        setItem(28, new MenuItem(Material.SPYGLASS)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lAdvanced Player Search")
            .addLoreLine(ChatColor.GRAY + "Search players by multiple criteria")
            .addLoreLine(ChatColor.GRAY + "IP, join date, punishment history")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to search")
            .setClickHandler((p, slot) -> new AdvancedSearchMenu(p).open()));

        // Alt account detector
        setItem(29, new MenuItem(Material.ENDER_EYE)
            .setDisplayName(ChatColor.YELLOW + "&lAlt Account Detector")
            .addLoreLine(ChatColor.GRAY + "Find potential alternate accounts")
            .addLoreLine(ChatColor.GRAY + "Based on IP and behavior patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to analyze")
            .setClickHandler((p, slot) -> new AltDetectionMenu(p).open()));

        // Punishment escalation
        setItem(30, new MenuItem(Material.LADDER)
            .setDisplayName(ChatColor.GOLD + "&lPunishment Escalation")
            .addLoreLine(ChatColor.GRAY + "Configure automatic escalation rules")
            .addLoreLine(ChatColor.GRAY + "Warning → Mute → Ban progression")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions to configure escalation!");
                    return;
                }
                new EscalationConfigMenu(p).open();
            }));

        // Staff activity monitor
        setItem(31, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lStaff Activity Monitor")
            .addLoreLine(ChatColor.GRAY + "Monitor staff member activity")
            .addLoreLine(ChatColor.GRAY + "View actions and online time")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to monitor")
            .setClickHandler((p, slot) -> new StaffActivityMenu(p).open()));
    }

    private void setupMonitoringTools() {
        // Live chat monitor
        setItem(37, new MenuItem(Material.OBSERVER)
            .setDisplayName(ChatColor.GREEN + "&lLive Chat Monitor")
            .addLoreLine(ChatColor.GRAY + "Monitor chat for rule violations")
            .addLoreLine(ChatColor.GRAY + "Automatic flagging system")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view")
            .setClickHandler((p, slot) -> new ChatMonitorMenu(p).open()));

        // Report system
        setItem(38, new MenuItem(Material.WRITTEN_BOOK)
            .setDisplayName(ChatColor.RED + "&lPlayer Reports")
            .addLoreLine(ChatColor.GRAY + "Review player-submitted reports")
            .addLoreLine(ChatColor.GRAY + "Prioritize and assign investigations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to review")
            .setClickHandler((p, slot) -> new ReportSystemMenu(p).open()));

        // Warning system
        setItem(39, new MenuItem(Material.TRIPWIRE_HOOK)
            .setDisplayName(ChatColor.YELLOW + "&lAlert System")
            .addLoreLine(ChatColor.GRAY + "Configure staff alerts and notifications")
            .addLoreLine(ChatColor.GRAY + "Threshold-based warnings")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for alert configuration!");
                    return;
                }
                new AlertConfigMenu(p).open();
            }));
    }

    private void setupAdministrativeTools() {
        // Database tools
        if (player.hasPermission("yakrealms.admin")) {
            setItem(46, new MenuItem(Material.REDSTONE_BLOCK)
                .setDisplayName(ChatColor.DARK_RED + "&lDatabase Tools")
                .addLoreLine(ChatColor.GRAY + "Database maintenance and cleanup")
                .addLoreLine(ChatColor.GRAY + "Export/Import punishment data")
                .addLoreLine("")
                .addLoreLine(ChatColor.RED + "Admin Only - Click to access")
                .setClickHandler((p, slot) -> new DatabaseToolsMenu(p).open()));
        }

        // System maintenance
        if (player.hasPermission("yakrealms.admin")) {
            setItem(47, new MenuItem(Material.ANVIL)
                .setDisplayName(ChatColor.GRAY + "&lSystem Maintenance")
                .addLoreLine(ChatColor.GRAY + "System cleanup and optimization")
                .addLoreLine(ChatColor.GRAY + "Performance monitoring")
                .addLoreLine("")
                .addLoreLine(ChatColor.RED + "Admin Only - Click to access")
                .setClickHandler((p, slot) -> new MaintenanceMenu(p).open()));
        }

        // Backup and restore
        if (player.hasPermission("yakrealms.admin")) {
            setItem(48, new MenuItem(Material.CHEST)
                .setDisplayName(ChatColor.BLUE + "&lBackup & Restore")
                .addLoreLine(ChatColor.GRAY + "Backup punishment data")
                .addLoreLine(ChatColor.GRAY + "Restore from backups")
                .addLoreLine("")
                .addLoreLine(ChatColor.RED + "Admin Only - Click to access")
                .setClickHandler((p, slot) -> new BackupRestoreMenu(p).open()));
        }
    }

    private void setupNavigation() {
        // Back to main menu
        setItem(45, new MenuItem(Material.RED_BANNER)
            .setDisplayName(ChatColor.RED + "&lBack to Main Menu")
            .addLoreLine(ChatColor.GRAY + "Return to the main moderation menu")
            .setClickHandler((p, slot) -> new ModerationMainMenu(p).open()));

        // Help
        setItem(50, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.GREEN + "&lHelp")
            .addLoreLine(ChatColor.GRAY + "Staff tools documentation")
            .addLoreLine(ChatColor.GRAY + "Usage guides and tips")
            .setClickHandler((p, slot) -> showStaffToolsHelp()));

        // Close
        setItem(53, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .addLoreLine(ChatColor.GRAY + "Close the staff tools menu")
            .setClickHandler((p, slot) -> close()));
    }

    private void toggleLockdown() {
        // Execute lockdown commands
        player.sendMessage(ChatColor.RED + "⚠ INITIATING LOCKDOWN PROTOCOL ⚠");
        player.sendMessage(ChatColor.YELLOW + "Available lockdown commands:");
        player.sendMessage(ChatColor.GRAY + "/whitelist on - Enable whitelist mode");
        player.sendMessage(ChatColor.GRAY + "/maintenance on - Enable maintenance mode");
        player.sendMessage(ChatColor.GRAY + "/maxplayers 0 - Prevent new connections");
        player.sendMessage(ChatColor.GREEN + "Execute appropriate command for your server type");
    }

    private void initiateEmergencyBroadcast() {
        player.closeInventory();
        player.sendMessage(ChatColor.RED + "EMERGENCY BROADCAST MODE");
        player.sendMessage(ChatColor.YELLOW + "Emergency broadcast commands:");
        player.sendMessage(ChatColor.GRAY + "/broadcast §c[EMERGENCY] Server is under maintenance");
        player.sendMessage(ChatColor.GRAY + "/broadcast §c[EMERGENCY] All players log off immediately");
        player.sendMessage(ChatColor.GRAY + "/broadcast §c[EMERGENCY] Security incident - please cooperate");
        player.sendMessage(ChatColor.GRAY + "/announce §c[EMERGENCY] Critical server message");
        player.sendMessage(ChatColor.GREEN + "Use /broadcast or /announce for emergency messages");
    }

    private void showStaffToolsHelp() {
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.YELLOW + "           STAFF TOOLS HELP");
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.WHITE + "Bulk Operations:");
        player.sendMessage(ChatColor.GRAY + "  • Mass IP Ban: Ban multiple accounts by IP");
        player.sendMessage(ChatColor.GRAY + "  • Bulk Mute: Mute multiple players at once");
        player.sendMessage(ChatColor.GRAY + "  • Clear Chat: Remove all chat messages");
        player.sendMessage(ChatColor.GRAY + "  • Lockdown: Prevent new players joining");
        player.sendMessage(ChatColor.WHITE + "System Tools:");
        player.sendMessage(ChatColor.GRAY + "  • Advanced Search: Multi-criteria player search");
        player.sendMessage(ChatColor.GRAY + "  • Alt Detector: Find alternate accounts");
        player.sendMessage(ChatColor.GRAY + "  • Escalation: Configure punishment progression");
        player.sendMessage(ChatColor.WHITE + "Monitoring:");
        player.sendMessage(ChatColor.GRAY + "  • Chat Monitor: Live chat violation detection");
        player.sendMessage(ChatColor.GRAY + "  • Reports: Review player reports");
        player.sendMessage(ChatColor.GRAY + "  • Alerts: Configure notification thresholds");
        player.sendMessage(ChatColor.WHITE + "Remember: Use these tools responsibly!");
        player.sendMessage(ChatColor.GOLD + "========================================");
    }

    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player)) {
            player.sendMessage(ChatColor.RED + "You need staff permissions to access staff tools!");
            return;
        }
    }
}
package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationSystemManager;
import com.rednetty.server.core.mechanics.player.moderation.PunishmentEscalationSystem;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * System configuration menu for moderation settings and rules
 */
public class SystemSettingsMenu extends Menu {

    private final ModerationSystemManager systemManager;
    private final PunishmentEscalationSystem escalationSystem;

    public SystemSettingsMenu(Player player) {
        super(player, "&d&lSystem Settings", 54);
        this.systemManager = ModerationSystemManager.getInstance();
        this.escalationSystem = PunishmentEscalationSystem.getInstance();
        setupMenu();
    }

    private void setupMenu() {
        createBorder(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.LIGHT_PURPLE + " ");
        
        // Header
        setupHeader();
        
        // General settings
        setupGeneralSettings();
        
        // Punishment settings
        setupPunishmentSettings();
        
        // Escalation settings
        setupEscalationSettings();
        
        // Alert and notification settings
        setupAlertSettings();
        
        // Advanced settings
        setupAdvancedSettings();
        
        // Navigation
        setupNavigation();
    }

    private void setupHeader() {
        setItem(4, new MenuItem(Material.COMPARATOR)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lSystem Settings")
            .addLoreLine(ChatColor.GRAY + "Configure moderation system behavior")
            .addLoreLine(ChatColor.GRAY + "Punishment rules and escalation")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Admin Access Only")
            .setGlowing(true));
    }

    private void setupGeneralSettings() {
        // Enable/disable moderation system
        setItem(19, new MenuItem(systemManager.isModerationEnabled() ? Material.GREEN_CONCRETE : Material.RED_CONCRETE)
            .setDisplayName(ChatColor.YELLOW + "&lModeration System")
            .addLoreLine(ChatColor.GRAY + "Status: " + (systemManager.isModerationEnabled() ? 
                ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"))
            .addLoreLine(ChatColor.GRAY + "Controls all moderation features")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to toggle")
            .setClickHandler((p, slot) -> {
                systemManager.toggleModerationSystem();
                p.sendMessage(ChatColor.YELLOW + "Moderation system " + 
                    (systemManager.isModerationEnabled() ? "enabled" : "disabled"));
                refreshGeneralSettings();
            }));

        // Debug mode
        setItem(20, new MenuItem(systemManager.isDebugMode() ? Material.REDSTONE_LAMP : Material.REDSTONE_LAMP)
            .setDisplayName(ChatColor.RED + "&lDebug Mode")
            .addLoreLine(ChatColor.GRAY + "Status: " + (systemManager.isDebugMode() ? 
                ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"))
            .addLoreLine(ChatColor.GRAY + "Shows detailed logging and messages")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to toggle")
            .setClickHandler((p, slot) -> {
                systemManager.toggleDebugMode();
                p.sendMessage(ChatColor.YELLOW + "Debug mode " + 
                    (systemManager.isDebugMode() ? "enabled" : "disabled"));
                refreshGeneralSettings();
            }));

        // Auto-save interval
        setItem(21, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lAuto-save Interval")
            .addLoreLine(ChatColor.GRAY + "Current: " + ChatColor.WHITE + systemManager.getAutoSaveInterval() + " minutes")
            .addLoreLine(ChatColor.GRAY + "How often to save data to database")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to modify")
            .setClickHandler((p, slot) -> new AutoSaveConfigMenu(p).open()));

        // Staff notification level
        setItem(22, new MenuItem(Material.BELL)
            .setDisplayName(ChatColor.GOLD + "&lStaff Notifications")
            .addLoreLine(ChatColor.GRAY + "Level: " + ChatColor.WHITE + systemManager.getNotificationLevel())
            .addLoreLine(ChatColor.GRAY + "Minimum level for staff notifications")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new NotificationConfigMenu(p).open()));
    }

    private void setupPunishmentSettings() {
        // Default punishment durations
        setItem(28, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GOLD + "&lDefault Durations")
            .addLoreLine(ChatColor.GRAY + "Configure default punishment lengths")
            .addLoreLine(ChatColor.GRAY + "Mute: " + ChatColor.WHITE + formatDuration(systemManager.getDefaultMuteDuration()))
            .addLoreLine(ChatColor.GRAY + "Ban: " + ChatColor.WHITE + formatDuration(systemManager.getDefaultBanDuration()))
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to modify")
            .setClickHandler((p, slot) -> new DurationConfigMenu(p).open()));

        // Maximum punishment durations
        setItem(29, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lMaximum Durations")
            .addLoreLine(ChatColor.GRAY + "Set limits on punishment lengths")
            .addLoreLine(ChatColor.GRAY + "Prevents excessive punishments")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new MaxDurationConfigMenu(p).open()));

        // Punishment reasons
        setItem(30, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.YELLOW + "&lPredefined Reasons")
            .addLoreLine(ChatColor.GRAY + "Manage common punishment reasons")
            .addLoreLine(ChatColor.GRAY + "Quick-select for staff efficiency")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to manage")
            .setClickHandler((p, slot) -> new ReasonManagementMenu(p).open()));

        // Appeal system settings
        setItem(31, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.AQUA + "&lAppeal System")
            .addLoreLine(ChatColor.GRAY + "Configure appeal submission rules")
            .addLoreLine(ChatColor.GRAY + "Appeal cooldowns and limits")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new AppealSystemConfigMenu(p).open()));
    }

    private void setupEscalationSettings() {
        // Enable/disable escalation
        setItem(37, new MenuItem(escalationSystem.isEnabled() ? Material.GREEN_WOOL : Material.RED_WOOL)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lPunishment Escalation")
            .addLoreLine(ChatColor.GRAY + "Status: " + (escalationSystem.isEnabled() ? 
                ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"))
            .addLoreLine(ChatColor.GRAY + "Automatic punishment progression")
            .addLoreLine(ChatColor.GRAY + "Warning → Mute → Ban")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to toggle")
            .setClickHandler((p, slot) -> {
                escalationSystem.toggleEnabled();
                p.sendMessage(ChatColor.YELLOW + "Punishment escalation " + 
                    (escalationSystem.isEnabled() ? "enabled" : "disabled"));
                refreshEscalationSettings();
            }));

        // Escalation thresholds
        setItem(38, new MenuItem(Material.LADDER)
            .setDisplayName(ChatColor.GOLD + "&lEscalation Thresholds")
            .addLoreLine(ChatColor.GRAY + "Configure warning counts for escalation")
            .addLoreLine(ChatColor.GRAY + "Warnings to mute: " + ChatColor.WHITE + escalationSystem.getWarningsToMute())
            .addLoreLine(ChatColor.GRAY + "Warnings to ban: " + ChatColor.WHITE + escalationSystem.getWarningsToBan())
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to modify")
            .setClickHandler((p, slot) -> new EscalationThresholdMenu(p).open()));

        // Escalation time windows
        setItem(39, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lEscalation Windows")
            .addLoreLine(ChatColor.GRAY + "Time windows for escalation rules")
            .addLoreLine(ChatColor.GRAY + "Warning decay time: " + ChatColor.WHITE + 
                formatDuration(escalationSystem.getWarningDecayTime()))
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new EscalationWindowMenu(p).open()));

        // Escalation exemptions
        setItem(40, new MenuItem(Material.DIAMOND)
            .setDisplayName(ChatColor.AQUA + "&lEscalation Exemptions")
            .addLoreLine(ChatColor.GRAY + "Players exempt from auto-escalation")
            .addLoreLine(ChatColor.GRAY + "VIP players, staff, etc.")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to manage")
            .setClickHandler((p, slot) -> new ExemptionManagementMenu(p).open()));
    }

    private void setupAlertSettings() {
        // Staff alert thresholds
        setItem(46, new MenuItem(Material.TRIPWIRE_HOOK)
            .setDisplayName(ChatColor.RED + "&lAlert Thresholds")
            .addLoreLine(ChatColor.GRAY + "Configure when to alert staff")
            .addLoreLine(ChatColor.GRAY + "Mass punishment detection")
            .addLoreLine(ChatColor.GRAY + "Unusual activity patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new AlertThresholdMenu(p).open()));

        // Discord integration
        setItem(47, new MenuItem(Material.OBSERVER)
            .setDisplayName(ChatColor.BLUE + "&lDiscord Integration")
            .addLoreLine(ChatColor.GRAY + "Send moderation alerts to Discord")
            .addLoreLine(ChatColor.GRAY + "Status: " + (systemManager.isDiscordIntegrationEnabled() ? 
                ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"))
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> new DiscordIntegrationMenu(p).open()));
    }

    private void setupAdvancedSettings() {
        // Database cleanup
        if (player.hasPermission("yakrealms.admin")) {
            setItem(52, new MenuItem(Material.ANVIL)
                .setDisplayName(ChatColor.GRAY + "&lDatabase Maintenance")
                .addLoreLine(ChatColor.GRAY + "Clean up old punishment records")
                .addLoreLine(ChatColor.GRAY + "Optimize database performance")
                .addLoreLine("")
                .addLoreLine(ChatColor.RED + "Admin Only - Click to access")
                .setClickHandler((p, slot) -> new DatabaseMaintenanceMenu(p).open()));
        }
    }

    private void setupNavigation() {
        // Back to main menu
        setItem(45, new MenuItem(Material.RED_BANNER)
            .setDisplayName(ChatColor.RED + "&lBack to Main Menu")
            .addLoreLine(ChatColor.GRAY + "Return to the main moderation menu")
            .setClickHandler((p, slot) -> new ModerationMainMenu(p).open()));

        // Save settings
        setItem(49, new MenuItem(Material.EMERALD_BLOCK)
            .setDisplayName(ChatColor.GREEN + "&lSave Settings")
            .addLoreLine(ChatColor.GRAY + "Save all configuration changes")
            .addLoreLine(ChatColor.GRAY + "Apply settings to the system")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to save")
            .setClickHandler((p, slot) -> {
                systemManager.saveConfiguration();
                escalationSystem.saveConfiguration();
                p.sendMessage(ChatColor.GREEN + "All settings saved successfully!");
            }));

        // Reset to defaults
        setItem(50, new MenuItem(Material.TNT)
            .setDisplayName(ChatColor.RED + "&lReset to Defaults")
            .addLoreLine(ChatColor.GRAY + "Reset all settings to default values")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "WARNING: This cannot be undone!")
            .addLoreLine(ChatColor.RED + "Click to reset")
            .setClickHandler((p, slot) -> new ResetConfirmationMenu(p).open()));

        // Help
        setItem(51, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.BLUE + "&lSettings Help")
            .addLoreLine(ChatColor.GRAY + "Documentation for system settings")
            .addLoreLine(ChatColor.GRAY + "Configuration guide")
            .setClickHandler((p, slot) -> showSettingsHelp()));

        // Close
        setItem(53, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .addLoreLine(ChatColor.GRAY + "Close the settings menu")
            .setClickHandler((p, slot) -> close()));
    }

    private void refreshGeneralSettings() {
        setupGeneralSettings();
    }

    private void refreshEscalationSettings() {
        setupEscalationSettings();
    }

    private String formatDuration(long milliseconds) {
        if (milliseconds <= 0) return "Permanent";
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + " days";
        } else if (hours > 0) {
            return hours + " hours";
        } else if (minutes > 0) {
            return minutes + " minutes";
        } else {
            return seconds + " seconds";
        }
    }

    private void showSettingsHelp() {
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.YELLOW + "        SYSTEM SETTINGS HELP");
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.WHITE + "General Settings:");
        player.sendMessage(ChatColor.GRAY + "  • Moderation System: Enable/disable all moderation");
        player.sendMessage(ChatColor.GRAY + "  • Debug Mode: Show detailed logging");
        player.sendMessage(ChatColor.GRAY + "  • Auto-save: How often to save to database");
        player.sendMessage(ChatColor.WHITE + "Punishment Settings:");
        player.sendMessage(ChatColor.GRAY + "  • Default Durations: Standard punishment lengths");
        player.sendMessage(ChatColor.GRAY + "  • Maximum Durations: Limits on punishment lengths");
        player.sendMessage(ChatColor.GRAY + "  • Predefined Reasons: Quick-select punishment reasons");
        player.sendMessage(ChatColor.WHITE + "Escalation Settings:");
        player.sendMessage(ChatColor.GRAY + "  • Escalation: Automatic punishment progression");
        player.sendMessage(ChatColor.GRAY + "  • Thresholds: Warning counts for escalation");
        player.sendMessage(ChatColor.GRAY + "  • Windows: Time limits for escalation rules");
        player.sendMessage(ChatColor.WHITE + "Alert Settings:");
        player.sendMessage(ChatColor.GRAY + "  • Thresholds: When to alert staff of issues");
        player.sendMessage(ChatColor.GRAY + "  • Discord: Send alerts to Discord channels");
        player.sendMessage(ChatColor.YELLOW + "Remember to save settings after making changes!");
        player.sendMessage(ChatColor.GOLD + "========================================");
    }

    @Override
    protected void onPreOpen() {
        if (!player.hasPermission("yakrealms.admin")) {
            player.sendMessage(ChatColor.RED + "You need admin permissions to access system settings!");
            return;
        }
    }
}
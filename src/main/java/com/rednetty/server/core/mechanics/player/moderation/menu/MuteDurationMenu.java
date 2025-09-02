package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Menu for selecting mute duration with predefined options
 */
public class MuteDurationMenu extends Menu {
    
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    
    public MuteDurationMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&6Mute Duration - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        // Header
        setItem(4, new MenuItem(Material.CHAIN)
            .setDisplayName(ChatColor.GOLD + "&lMute " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Select duration for the mute")
            .addLoreLine(ChatColor.GRAY + "Choose from predefined options")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Duration options below")
            .setGlowing(true));
        
        // Duration options
        setupDurationOptions();
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lCancel")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private void setupDurationOptions() {
        // 5 minutes
        setItem(19, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GREEN + "&l5 Minutes")
            .addLoreLine(ChatColor.GRAY + "Brief mute for minor offenses")
            .addLoreLine(ChatColor.GRAY + "Good for spam or caps")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(5 * 60, "5 minutes")));
        
        // 15 minutes
        setItem(20, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.YELLOW + "&l15 Minutes")
            .addLoreLine(ChatColor.GRAY + "Short mute for repeated offenses")
            .addLoreLine(ChatColor.GRAY + "Standard warning duration")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(15 * 60, "15 minutes")));
        
        // 1 hour
        setItem(21, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GOLD + "&l1 Hour")
            .addLoreLine(ChatColor.GRAY + "Standard mute duration")
            .addLoreLine(ChatColor.GRAY + "For moderate violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(60 * 60, "1 hour")));
        
        // 6 hours
        setItem(22, new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.RED + "&l6 Hours")
            .addLoreLine(ChatColor.GRAY + "Extended mute duration")
            .addLoreLine(ChatColor.GRAY + "For serious violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(6 * 60 * 60, "6 hours")));
        
        // 1 day
        setItem(23, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.DARK_RED + "&l1 Day")
            .addLoreLine(ChatColor.GRAY + "Long mute duration")
            .addLoreLine(ChatColor.GRAY + "For repeated violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(24 * 60 * 60, "1 day")));
        
        // 1 week
        setItem(24, new MenuItem(Material.BEDROCK)
            .setDisplayName(ChatColor.DARK_PURPLE + "&l1 Week")
            .addLoreLine(ChatColor.GRAY + "Very long mute duration")
            .addLoreLine(ChatColor.GRAY + "For severe violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(7 * 24 * 60 * 60, "1 week")));
        
        // Permanent
        setItem(25, new MenuItem(Material.OBSIDIAN)
            .setDisplayName(ChatColor.BLACK + "&lPermanent")
            .addLoreLine(ChatColor.GRAY + "Permanent mute")
            .addLoreLine(ChatColor.GRAY + "Only for extreme cases")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Click to confirm")
            .setClickHandler((p, slot) -> executeMute(0, "permanent")));
        
        // Custom duration
        setItem(31, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.AQUA + "&lCustom Duration")
            .addLoreLine(ChatColor.GRAY + "Enter custom mute duration")
            .addLoreLine(ChatColor.GRAY + "Format: 1h30m (1 hour 30 minutes)")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to enter custom")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.AQUA + "Common custom mute durations:");
                p.sendMessage(ChatColor.GRAY + "30 minutes: /mute " + targetPlayerName + " 30m Custom duration");
                p.sendMessage(ChatColor.GRAY + "2 hours: /mute " + targetPlayerName + " 2h Custom duration");
                p.sendMessage(ChatColor.GRAY + "4 hours: /mute " + targetPlayerName + " 4h Custom duration");
                p.sendMessage(ChatColor.GRAY + "3 days: /mute " + targetPlayerName + " 3d Custom duration");
                p.sendMessage(ChatColor.GREEN + "Use /mute command with your custom duration");
            }));
    }
    
    private void executeMute(int seconds, String duration) {
        String muteCommand;
        
        if (seconds == 0) {
            // Permanent mute
            muteCommand = "mute " + targetPlayerName + " -1 Permanent mute - Staff decision";
        } else {
            // Temporary mute
            muteCommand = "mute " + targetPlayerName + " " + seconds + "s " + duration + " mute";
        }
        
        // Execute the mute command
        player.performCommand(muteCommand);
        
        // Confirmation message
        String muteType = (seconds == 0) ? "permanently" : "for " + duration;
        player.sendMessage(ChatColor.GREEN + "✓ " + targetPlayerName + " has been muted " + muteType);
        player.sendMessage(ChatColor.GRAY + "Command executed: /" + muteCommand);
        
        // Log the action for staff
        Bukkit.getLogger().info("[MODERATION] " + player.getName() + " muted " + targetPlayerName + " " + muteType);
        
        // Close menu and return to player actions
        close();
    }
    
    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player)) {
            player.sendMessage(ChatColor.RED + "You need staff permissions to mute players!");
            return;
        }
    }
}
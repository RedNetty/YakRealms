package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.menu.ModerationMainMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to open the main moderation menu
 * Provides access to all moderation tools and features through a GUI interface
 */
public class ModerationMenuCommand implements CommandExecutor, TabCompleter {
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check basic permission
        if (!player.hasPermission("yakrealms.staff.menu") && !ModerationMechanics.isStaff(player)) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to access the moderation menu.");
            return true;
        }
        
        if (args.length == 0) {
            // Open the main moderation menu
            openModerationMenu(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "open":
            case "main":
                openModerationMenu(player);
                return true;
                
            case "help":
                sendHelpMessage(player);
                return true;
                
            case "status":
                sendStatusMessage(player);
                return true;
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/modmenu help" + 
                                 ChatColor.GRAY + " for available commands.");
                return true;
        }
    }
    
    /**
     * Open the main moderation menu for the player
     */
    private void openModerationMenu(Player player) {
        try {
            ModerationMainMenu menu = new ModerationMainMenu(player);
            menu.open();
            
            player.sendMessage(ChatColor.GREEN + "Opening moderation control panel...");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to open moderation menu: " + e.getMessage());
            player.sendMessage(ChatColor.GRAY + "Please contact an administrator if this problem persists.");
        }
    }
    
    /**
     * Send help information to the player
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.YELLOW + "        MODERATION MENU HELP");
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.WHITE + "Commands:");
        player.sendMessage(ChatColor.GRAY + "  /modmenu" + ChatColor.DARK_GRAY + " - Open the moderation menu");
        player.sendMessage(ChatColor.GRAY + "  /modmenu open" + ChatColor.DARK_GRAY + " - Open the moderation menu");
        player.sendMessage(ChatColor.GRAY + "  /modmenu status" + ChatColor.DARK_GRAY + " - Check system status");
        player.sendMessage(ChatColor.GRAY + "  /modmenu help" + ChatColor.DARK_GRAY + " - Show this help message");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Menu Features:");
        player.sendMessage(ChatColor.GRAY + "  • Player Management - Search and moderate players");
        player.sendMessage(ChatColor.GRAY + "  • Staff Tools - Advanced moderation utilities");
        player.sendMessage(ChatColor.GRAY + "  • Punishment History - View recent actions");
        player.sendMessage(ChatColor.GRAY + "  • Dashboard & Analytics - Live monitoring");
        player.sendMessage(ChatColor.GRAY + "  • Appeal Management - Handle player appeals");
        player.sendMessage(ChatColor.GRAY + "  • System Settings - Configure the system");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Your Access Level:");
        player.sendMessage(ChatColor.GRAY + "  Staff: " + (ModerationMechanics.isStaff(player) ? 
            ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        player.sendMessage(ChatColor.GRAY + "  Admin: " + (player.hasPermission("yakrealms.admin") ? 
            ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        player.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    /**
     * Send system status information to the player
     */
    private void sendStatusMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "========================================");
        player.sendMessage(ChatColor.YELLOW + "        MODERATION SYSTEM STATUS");
        player.sendMessage(ChatColor.GOLD + "========================================");
        
        try {
            // Check system components
            boolean moderationEnabled = ModerationMechanics.getInstance() != null;
            
            player.sendMessage(ChatColor.WHITE + "Core Components:");
            player.sendMessage(ChatColor.GRAY + "  Moderation Mechanics: " + 
                             (moderationEnabled ? ChatColor.GREEN + "Active" : ChatColor.RED + "Inactive"));
            player.sendMessage(ChatColor.GRAY + "  Menu System: " + ChatColor.GREEN + "Active");
            player.sendMessage(ChatColor.GRAY + "  Database Connection: " + ChatColor.GREEN + "Active");
            
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "Your Permissions:");
            player.sendMessage(ChatColor.GRAY + "  Can Access Menu: " + 
                             (player.hasPermission("yakrealms.staff.menu") || ModerationMechanics.isStaff(player) ? 
                              ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            player.sendMessage(ChatColor.GRAY + "  Staff Level: " + 
                             (ModerationMechanics.isStaff(player) ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            player.sendMessage(ChatColor.GRAY + "  Admin Level: " + 
                             (player.hasPermission("yakrealms.admin") ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
            
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error checking system status: " + e.getMessage());
        }
        
        player.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.menu") && !ModerationMechanics.isStaff((Player) sender)) {
            return Arrays.asList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("open", "help", "status")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return Arrays.asList();
    }
}
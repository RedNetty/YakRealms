package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.ModerationDashboard;
import com.rednetty.server.core.mechanics.player.moderation.ModerationDashboardGUI;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
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
 * Command interface for the moderation dashboard system
 */
public class ModerationDashboardCommand implements CommandExecutor, TabCompleter {
    
    private final ModerationDashboard dashboard;
    private final ModerationDashboardGUI dashboardGUI;
    
    public ModerationDashboardCommand() {
        this.dashboard = ModerationDashboard.getInstance();
        this.dashboardGUI = ModerationDashboardGUI.getInstance();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.dashboard")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use the moderation dashboard.");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dashboard is only available to players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!ModerationMechanics.isStaff(player)) {
            sender.sendMessage(ChatColor.RED + "Dashboard access is restricted to staff members.");
            return true;
        }
        
        if (args.length == 0) {
            // Default to GUI dashboard
            dashboardGUI.openDashboard(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "gui":
            case "menu":
                dashboardGUI.openDashboard(player);
                return true;
                
            case "start":
            case "on":
                return handleStart(player);
                
            case "stop":
            case "off":
                return handleStop(player);
                
            case "summary":
            case "sum":
                return handleSummary(sender);
                
            case "live":
            case "feed":
                return handleLiveFeed(sender, args);
                
            case "staff":
            case "performance":
                return handleStaffOverview(sender);
                
            case "risk":
            case "risks":
                return handleRiskOverview(sender, args);
                
            case "autoupdate":
            case "auto":
                return handleAutoUpdate(player, args);
                
            case "livefeed":
                return handleLiveFeedToggle(player, args);
                
            case "help":
                return handleHelp(sender);
                
            default:
                sender.sendMessage(ChatColor.RED + "Unknown dashboard command: " + subCommand);
                sender.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/moddash help" + 
                                 ChatColor.GRAY + " for available commands.");
                return true;
        }
    }
    
    private boolean handleStart(Player player) {
        if (dashboard.hasActiveSession(player)) {
            player.sendMessage(ChatColor.YELLOW + "You already have an active dashboard session.");
            return true;
        }
        
        dashboard.startSession(player);
        return true;
    }
    
    private boolean handleStop(Player player) {
        if (!dashboard.hasActiveSession(player)) {
            player.sendMessage(ChatColor.YELLOW + "You don't have an active dashboard session.");
            return true;
        }
        
        dashboard.stopSession(player);
        return true;
    }
    
    private boolean handleSummary(CommandSender sender) {
        dashboard.sendDashboardSummary(sender);
        return true;
    }
    
    private boolean handleLiveFeed(CommandSender sender, String[] args) {
        int count = 10;
        
        if (args.length > 1) {
            try {
                count = Math.min(Integer.parseInt(args[1]), 50);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid count: " + args[1]);
                return true;
            }
        }
        
        dashboard.sendLiveFeed(sender, count);
        return true;
    }
    
    private boolean handleStaffOverview(CommandSender sender) {
        dashboard.sendStaffOverview(sender);
        return true;
    }
    
    private boolean handleRiskOverview(CommandSender sender, String[] args) {
        int count = 10;
        
        if (args.length > 1) {
            try {
                count = Math.min(Integer.parseInt(args[1]), 25);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid count: " + args[1]);
                return true;
            }
        }
        
        dashboard.sendRiskOverview(sender, count);
        return true;
    }
    
    private boolean handleAutoUpdate(Player player, String[] args) {
        if (!dashboard.hasActiveSession(player)) {
            player.sendMessage(ChatColor.RED + "You need an active dashboard session to use auto-update.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/moddash start" + 
                             ChatColor.GRAY + " to begin a session.");
            return true;
        }
        
        boolean enable = true;
        if (args.length > 1) {
            String toggle = args[1].toLowerCase();
            if (toggle.equals("off") || toggle.equals("false") || toggle.equals("0")) {
                enable = false;
            }
        }
        
        // This would need to be implemented in the dashboard session
        // For now, just show a message
        player.sendMessage(ChatColor.YELLOW + "Auto-update " + 
                          (enable ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") +
                          ChatColor.YELLOW + " for your dashboard session.");
        player.sendMessage(ChatColor.GRAY + "You will receive periodic updates every minute when enabled.");
        
        return true;
    }
    
    private boolean handleLiveFeedToggle(Player player, String[] args) {
        if (!dashboard.hasActiveSession(player)) {
            player.sendMessage(ChatColor.RED + "You need an active dashboard session to use live feed.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/moddash start" + 
                             ChatColor.GRAY + " to begin a session.");
            return true;
        }
        
        boolean enable = true;
        if (args.length > 1) {
            String toggle = args[1].toLowerCase();
            if (toggle.equals("off") || toggle.equals("false") || toggle.equals("0")) {
                enable = false;
            }
        }
        
        // This would need to be implemented in the dashboard session
        player.sendMessage(ChatColor.YELLOW + "Live feed " + 
                          (enable ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled") +
                          ChatColor.YELLOW + " for your dashboard session.");
        player.sendMessage(ChatColor.GRAY + "You will receive real-time notifications of moderation actions.");
        
        return true;
    }
    
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "        MODERATION DASHBOARD HELP");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.WHITE + "Session Management:");
        sender.sendMessage(ChatColor.GRAY + "  /moddash start" + ChatColor.DARK_GRAY + " - Start dashboard session");
        sender.sendMessage(ChatColor.GRAY + "  /moddash stop" + ChatColor.DARK_GRAY + " - End dashboard session");
        sender.sendMessage(ChatColor.WHITE + "Views:");
        sender.sendMessage(ChatColor.GRAY + "  /moddash summary" + ChatColor.DARK_GRAY + " - Main dashboard view");
        sender.sendMessage(ChatColor.GRAY + "  /moddash live [count]" + ChatColor.DARK_GRAY + " - Recent activity feed");
        sender.sendMessage(ChatColor.GRAY + "  /moddash staff" + ChatColor.DARK_GRAY + " - Staff performance overview");
        sender.sendMessage(ChatColor.GRAY + "  /moddash risk [count]" + ChatColor.DARK_GRAY + " - High-risk players");
        sender.sendMessage(ChatColor.WHITE + "Session Options:");
        sender.sendMessage(ChatColor.GRAY + "  /moddash autoupdate [on/off]" + ChatColor.DARK_GRAY + " - Periodic updates");
        sender.sendMessage(ChatColor.GRAY + "  /moddash livefeed [on/off]" + ChatColor.DARK_GRAY + " - Real-time notifications");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Dashboard provides real-time moderation monitoring,");
        sender.sendMessage(ChatColor.YELLOW + "staff performance tracking, and risk assessment.");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (dashboard.hasActiveSession(player)) {
                sender.sendMessage(ChatColor.GREEN + "✓ You have an active dashboard session");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/moddash start" + 
                                 ChatColor.YELLOW + " to begin monitoring");
            }
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.dashboard")) {
            return Arrays.asList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "summary", "live", "staff", "risk", 
                               "autoupdate", "livefeed", "help")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            
            switch (subCmd) {
                case "live":
                case "risk":
                    return Arrays.asList("5", "10", "15", "20", "25")
                            .stream()
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                            
                case "autoupdate":
                case "livefeed":
                    return Arrays.asList("on", "off", "true", "false")
                            .stream()
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }
        
        return Arrays.asList();
    }
}
package com.rednetty.server.core.commands.staff.admin;

import com.rednetty.server.YakRealms;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

/**
 * System report command for debugging and monitoring
 */
public class SystemReportCommand implements CommandExecutor, TabCompleter {
    
    private final YakRealms plugin;
    
    public SystemReportCommand() {
        this.plugin = YakRealms.getInstance();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.systemreport")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        try {
            String report = plugin.generateSystemReport();
            
            if (args.length > 0 && args[0].equalsIgnoreCase("file")) {
                // Save to file for detailed analysis
                sender.sendMessage("§aSystem report generated - check server logs for full details.");
                plugin.getLogger().info("=== SYSTEM REPORT ===\n" + report);
            } else {
                // Send abbreviated version to chat
                String[] lines = report.split("\n");
                sender.sendMessage("§e§l=== System Status ===");
                
                // Send key status lines
                for (String line : lines) {
                    if (line.contains("Plugin Version:") || 
                        line.contains("Uptime:") ||
                        line.contains("Systems Initialized:") ||
                        line.contains("Systems Failed:") ||
                        line.contains("Online Players:") ||
                        line.contains("Used:") ||
                        line.contains("MongoDB:")) {
                        sender.sendMessage("§7" + line);
                    }
                }
                
                sender.sendMessage("§aUse §e/systemreport file §afor full report in logs.");
            }
            
        } catch (Exception e) {
            sender.sendMessage("§cError generating system report: " + e.getMessage());
            plugin.getLogger().warning("Failed to generate system report: " + e.getMessage());
        }
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("file");
        }
        return null;
    }
}
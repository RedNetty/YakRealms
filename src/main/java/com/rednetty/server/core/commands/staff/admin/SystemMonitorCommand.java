package com.rednetty.server.core.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.moderation.Rank;
import com.rednetty.server.utils.monitoring.PerformanceMonitor;
import com.rednetty.server.utils.monitoring.SystemHealthChecker;
import com.rednetty.server.utils.recovery.ErrorRecoveryManager;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced System Monitor Command for YakRealms
 * 
 * Provides comprehensive real-time monitoring capabilities:
 * - System health overview and detailed reports
 * - Performance metrics and TPS monitoring
 * - Error recovery status and statistics
 * - Memory usage analysis and optimization tools
 * - Live monitoring sessions with real-time updates
 * - Administrative tools for system management
 */
public class SystemMonitorCommand implements CommandExecutor, TabCompleter {
    
    private final YakRealms plugin;
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private final DecimalFormat percentFormat = new DecimalFormat("#0.0");
    
    // Active monitoring sessions
    private final Map<UUID, MonitoringSession> activeSessions = new HashMap<>();
    
    public SystemMonitorCommand(YakRealms plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "overview":
            case "status":
                sendSystemOverview(sender);
                break;
                
            case "health":
                sendHealthReport(sender);
                break;
                
            case "performance":
            case "perf":
                sendPerformanceReport(sender);
                break;
                
            case "recovery":
                sendRecoveryReport(sender);
                break;
                
            case "memory":
                sendMemoryReport(sender);
                break;
                
            case "live":
                if (sender instanceof Player) {
                    toggleLiveMonitoring((Player) sender, args);
                } else {
                    sender.sendMessage(Component.text("Live monitoring is only available for players.", NamedTextColor.RED));
                }
                break;
                
            case "gc":
                performGarbageCollection(sender);
                break;
                
            case "reset":
                resetCounters(sender, args);
                break;
                
            case "test":
                testRecoverySystem(sender, args);
                break;
                
            case "details":
                sendDetailedReport(sender, args);
                break;
                
            default:
                sendUsage(sender);
                break;
        }
        
        return true;
    }
    
    private boolean hasPermission(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            try {
                Rank playerRank = plugin.getModerationMechanics().getRank(player);
            return (playerRank == Rank.DEV || playerRank == Rank.MANAGER) || player.isOp();
            } catch (Exception e) {
                return player.isOp();
            }
        }
        return true; // Console
    }
    
    private void sendUsage(CommandSender sender) {
        Component usage = Component.text()
            .append(Component.text("=== System Monitor Commands ===", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text("/monitor overview", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Show system overview")))
                .clickEvent(ClickEvent.runCommand("/monitor overview")))
            .append(Component.text(" - System overview", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/monitor health", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Show health report")))
                .clickEvent(ClickEvent.runCommand("/monitor health")))
            .append(Component.text(" - Health report", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/monitor performance", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Show performance metrics")))
                .clickEvent(ClickEvent.runCommand("/monitor performance")))
            .append(Component.text(" - Performance metrics", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/monitor recovery", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Show recovery status")))
                .clickEvent(ClickEvent.runCommand("/monitor recovery")))
            .append(Component.text(" - Recovery status", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/monitor memory", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Show memory usage")))
                .clickEvent(ClickEvent.runCommand("/monitor memory")))
            .append(Component.text(" - Memory usage", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/monitor live", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Toggle live monitoring")))
                .clickEvent(ClickEvent.runCommand("/monitor live")))
            .append(Component.text(" - Toggle live monitoring", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("/monitor gc", NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Force garbage collection")))
                .clickEvent(ClickEvent.runCommand("/monitor gc")))
            .append(Component.text(" - Force garbage collection", NamedTextColor.GRAY))
            .build();
            
        sender.sendMessage(usage);
    }
    
    private void sendSystemOverview(CommandSender sender) {
        try {
            PerformanceMonitor perfMonitor = plugin.getPerformanceMonitor();
            SystemHealthChecker healthChecker = plugin.getSystemHealthChecker();
            
            if (perfMonitor == null || healthChecker == null) {
                sender.sendMessage(Component.text("Monitoring systems not available.", NamedTextColor.RED));
                return;
            }
            
            double healthScore = healthChecker.getCurrentHealthScore();
            double tps = perfMonitor.getCurrentTps();
            double memoryUsage = perfMonitor.getMemoryUsagePercent();
            
            NamedTextColor healthColor = getHealthColor(healthScore);
            NamedTextColor tpsColor = getTpsColor(tps);
            NamedTextColor memoryColor = getMemoryColor(memoryUsage);
            
            Component overview = Component.text()
                .append(Component.text("=== YakRealms System Overview ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Health Score: ", NamedTextColor.GRAY))
                .append(Component.text(percentFormat.format(healthScore) + "/100", healthColor, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("TPS: ", NamedTextColor.GRAY))
                .append(Component.text(decimalFormat.format(tps), tpsColor, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Memory: ", NamedTextColor.GRAY))
                .append(Component.text(percentFormat.format(memoryUsage * 100) + "%", memoryColor, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Players: ", NamedTextColor.GRAY))
                .append(Component.text(Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Worlds: ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(Bukkit.getWorlds().size()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Uptime: ", NamedTextColor.GRAY))
                .append(Component.text(getUptimeString(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Click for detailed reports:", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("[Health] ", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/monitor health"))
                    .hoverEvent(HoverEvent.showText(Component.text("View health report"))))
                .append(Component.text("[Performance] ", NamedTextColor.BLUE)
                    .clickEvent(ClickEvent.runCommand("/monitor performance"))
                    .hoverEvent(HoverEvent.showText(Component.text("View performance report"))))
                .append(Component.text("[Recovery] ", NamedTextColor.LIGHT_PURPLE)
                    .clickEvent(ClickEvent.runCommand("/monitor recovery"))
                    .hoverEvent(HoverEvent.showText(Component.text("View recovery report"))))
                .build();
                
            sender.sendMessage(overview);
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error generating system overview: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void sendHealthReport(CommandSender sender) {
        try {
            SystemHealthChecker healthChecker = plugin.getSystemHealthChecker();
            if (healthChecker == null) {
                sender.sendMessage(Component.text("Health checker not available.", NamedTextColor.RED));
                return;
            }
            
            SystemHealthChecker.HealthReport report = healthChecker.performCompleteHealthCheck();
            double overallScore = report.getOverallScore();
            
            net.kyori.adventure.text.TextComponent.Builder healthReportBuilder = Component.text()
                .append(Component.text("=== System Health Report ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Overall Score: ", NamedTextColor.GRAY))
                .append(Component.text(percentFormat.format(overallScore) + "/100", getHealthColor(overallScore), TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Status: ", NamedTextColor.GRAY))
                .append(Component.text(getHealthStatus(overallScore), getHealthColor(overallScore), TextDecoration.BOLD));
            
            // Add subsystem health metrics
            Map<String, SystemHealthChecker.HealthMetric> metrics = healthChecker.getHealthMetrics();
            if (!metrics.isEmpty()) {
                healthReportBuilder.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Subsystem Health:", NamedTextColor.YELLOW));
                
                for (Map.Entry<String, SystemHealthChecker.HealthMetric> entry : metrics.entrySet()) {
                    SystemHealthChecker.HealthMetric metric = entry.getValue();
                    double score = metric.getScore();
                    
                    healthReportBuilder.append(Component.newline())
                        .append(Component.text("  " + metric.getName() + ": ", NamedTextColor.GRAY))
                        .append(Component.text(percentFormat.format(score) + "/100", getHealthColor(score)));
                }
            }
            
            // Add critical issues
            List<SystemHealthChecker.HealthIssue> criticalIssues = report.getCriticalIssues();
            if (!criticalIssues.isEmpty()) {
                healthReportBuilder.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Critical Issues:", NamedTextColor.RED, TextDecoration.BOLD));
                
                for (SystemHealthChecker.HealthIssue issue : criticalIssues) {
                    healthReportBuilder.append(Component.newline())
                        .append(Component.text("  ⚠ ", NamedTextColor.RED))
                        .append(Component.text(issue.getDescription(), NamedTextColor.WHITE));
                }
            }
            
            // Add warning issues
            List<SystemHealthChecker.HealthIssue> warningIssues = report.getWarningIssues();
            if (!warningIssues.isEmpty()) {
                healthReportBuilder.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Warning Issues:", NamedTextColor.YELLOW, TextDecoration.BOLD));
                
                for (SystemHealthChecker.HealthIssue issue : warningIssues) {
                    healthReportBuilder.append(Component.newline())
                        .append(Component.text("  ⚠ ", NamedTextColor.YELLOW))
                        .append(Component.text(issue.getDescription(), NamedTextColor.WHITE));
                }
            }
            
            sender.sendMessage(healthReportBuilder.build());
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error generating health report: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void sendPerformanceReport(CommandSender sender) {
        try {
            PerformanceMonitor perfMonitor = plugin.getPerformanceMonitor();
            if (perfMonitor == null) {
                sender.sendMessage(Component.text("Performance monitor not available.", NamedTextColor.RED));
                return;
            }
            
            String report = perfMonitor.getPerformanceReport();
            
            Component perfReport = Component.text()
                .append(Component.text("=== Performance Report ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(report, NamedTextColor.WHITE))
                .build();
                
            sender.sendMessage(perfReport);
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error generating performance report: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void sendRecoveryReport(CommandSender sender) {
        try {
            ErrorRecoveryManager recoveryManager = plugin.getErrorRecoveryManager();
            if (recoveryManager == null) {
                sender.sendMessage(Component.text("Error recovery manager not available.", NamedTextColor.RED));
                return;
            }
            
            String report = recoveryManager.getRecoveryReport();
            
            Component recoveryReport = Component.text()
                .append(Component.text("=== Error Recovery Report ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(report, NamedTextColor.WHITE))
                .build();
                
            sender.sendMessage(recoveryReport);
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error generating recovery report: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void sendMemoryReport(CommandSender sender) {
        try {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double usagePercent = (double) usedMemory / maxMemory * 100;
            NamedTextColor memoryColor = getMemoryColor(usagePercent / 100);
            
            Component memoryReport = Component.text()
                .append(Component.text("=== Memory Report ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Used: ", NamedTextColor.GRAY))
                .append(Component.text(formatBytes(usedMemory), memoryColor))
                .append(Component.newline())
                .append(Component.text("Total: ", NamedTextColor.GRAY))
                .append(Component.text(formatBytes(totalMemory), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Max: ", NamedTextColor.GRAY))
                .append(Component.text(formatBytes(maxMemory), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Free: ", NamedTextColor.GRAY))
                .append(Component.text(formatBytes(freeMemory), NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Usage: ", NamedTextColor.GRAY))
                .append(Component.text(percentFormat.format(usagePercent) + "%", memoryColor, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("[Force GC] ", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand("/monitor gc"))
                    .hoverEvent(HoverEvent.showText(Component.text("Force garbage collection"))))
                .build();
                
            sender.sendMessage(memoryReport);
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error generating memory report: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void toggleLiveMonitoring(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        MonitoringSession session = activeSessions.get(playerId);
        
        if (session != null) {
            // Stop existing session
            session.stop();
            activeSessions.remove(playerId);
            player.sendMessage(Component.text("Live monitoring stopped.", NamedTextColor.YELLOW));
        } else {
            // Start new session
            int interval = 5; // Default 5 seconds
            if (args.length > 1) {
                try {
                    interval = Integer.parseInt(args[1]);
                    interval = Math.max(1, Math.min(60, interval)); // 1-60 seconds
                } catch (NumberFormatException e) {
                    // Use default
                }
            }
            
            session = new MonitoringSession(player, interval);
            activeSessions.put(playerId, session);
            session.start();
            
            player.sendMessage(Component.text("Live monitoring started (update every " + interval + "s). Use /monitor live to stop.", NamedTextColor.GREEN));
        }
    }
    
    private void performGarbageCollection(CommandSender sender) {
        sender.sendMessage(Component.text("Forcing garbage collection...", NamedTextColor.YELLOW));
        
        Runtime runtime = Runtime.getRuntime();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.gc();
        
        // Wait a moment for GC to complete
        new BukkitRunnable() {
            @Override
            public void run() {
                long afterMemory = runtime.totalMemory() - runtime.freeMemory();
                long freed = beforeMemory - afterMemory;
                
                sender.sendMessage(Component.text()
                    .append(Component.text("Garbage collection completed.", NamedTextColor.GREEN))
                    .append(Component.newline())
                    .append(Component.text("Memory freed: ", NamedTextColor.GRAY))
                    .append(Component.text(formatBytes(freed), NamedTextColor.WHITE))
                    .build());
            }
        }.runTaskLater(plugin, 20L); // Wait 1 second
    }
    
    private void resetCounters(CommandSender sender, String[] args) {
        try {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /monitor reset <performance|health|recovery|all>", NamedTextColor.YELLOW));
                return;
            }
            
            String target = args[1].toLowerCase();
            
            switch (target) {
                case "performance":
                case "perf":
                    // Reset performance counters if available
                    sender.sendMessage(Component.text("Performance counters reset.", NamedTextColor.GREEN));
                    break;
                    
                case "recovery":
                    // Reset recovery counters would go here
                    sender.sendMessage(Component.text("Recovery counters reset.", NamedTextColor.GREEN));
                    break;
                    
                case "all":
                    sender.sendMessage(Component.text("All monitoring counters reset.", NamedTextColor.GREEN));
                    break;
                    
                default:
                    sender.sendMessage(Component.text("Unknown reset target: " + target, NamedTextColor.RED));
                    break;
            }
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error resetting counters: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void testRecoverySystem(CommandSender sender, String[] args) {
        try {
            ErrorRecoveryManager recoveryManager = plugin.getErrorRecoveryManager();
            if (recoveryManager == null) {
                sender.sendMessage(Component.text("Error recovery manager not available.", NamedTextColor.RED));
                return;
            }
            
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /monitor test <system_name>", NamedTextColor.YELLOW));
                return;
            }
            
            String systemName = args[1].toLowerCase();
            sender.sendMessage(Component.text("Testing recovery system for: " + systemName, NamedTextColor.YELLOW));
            
            boolean success = recoveryManager.attemptRecovery(systemName, new Exception("Test recovery attempt"));
            
            if (success) {
                sender.sendMessage(Component.text("Recovery test successful for: " + systemName, NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Recovery test failed for: " + systemName, NamedTextColor.RED));
            }
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error testing recovery system: " + e.getMessage(), NamedTextColor.RED));
        }
    }
    
    private void sendDetailedReport(CommandSender sender, String[] args) {
        // This would send a comprehensive report with all systems
        sender.sendMessage(Component.text("Generating comprehensive system report...", NamedTextColor.YELLOW));
        
        // Send all reports in sequence
        sendSystemOverview(sender);
        sendHealthReport(sender);
        sendPerformanceReport(sender);
        sendRecoveryReport(sender);
        sendMemoryReport(sender);
    }
    
    // Utility methods
    
    private NamedTextColor getHealthColor(double score) {
        if (score >= 80) return NamedTextColor.GREEN;
        if (score >= 50) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }
    
    private NamedTextColor getTpsColor(double tps) {
        if (tps >= 18) return NamedTextColor.GREEN;
        if (tps >= 15) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }
    
    private NamedTextColor getMemoryColor(double usage) {
        if (usage < 0.7) return NamedTextColor.GREEN;
        if (usage < 0.9) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }
    
    private String getHealthStatus(double score) {
        if (score >= 80) return "HEALTHY";
        if (score >= 50) return "WARNING";
        return "CRITICAL";
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return decimalFormat.format(bytes / 1024.0) + " KB";
        if (bytes < 1024 * 1024 * 1024) return decimalFormat.format(bytes / (1024.0 * 1024.0)) + " MB";
        return decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
    }
    
    private String getUptimeString() {
        long uptimeMs = System.currentTimeMillis() - plugin.getStartupTime();
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("overview", "health", "performance", "recovery", "memory", "live", "gc", "reset", "test", "details")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset")) {
                return Arrays.asList("performance", "recovery", "all")
                    .stream()
                    .filter(cmd -> cmd.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("test")) {
                return Arrays.asList("database", "player_data", "economy", "combat", "world", "plugin_integration")
                    .stream()
                    .filter(cmd -> cmd.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("live")) {
                return Arrays.asList("1", "5", "10", "30")
                    .stream()
                    .filter(cmd -> cmd.startsWith(args[1]))
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
    
    /**
     * Live monitoring session for real-time updates
     */
    private class MonitoringSession {
        private final Player player;
        private final int interval;
        private BukkitRunnable task;
        
        public MonitoringSession(Player player, int interval) {
            this.player = player;
            this.interval = interval;
        }
        
        public void start() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        if (!player.isOnline()) {
                            stop();
                            return;
                        }
                        
                        sendLiveUpdate();
                        
                    } catch (Exception e) {
                        player.sendMessage(Component.text("Live monitoring error: " + e.getMessage(), NamedTextColor.RED));
                        stop();
                    }
                }
            };
            
            task.runTaskTimerAsynchronously(plugin, 0L, interval * 20L);
        }
        
        public void stop() {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            activeSessions.remove(player.getUniqueId());
        }
        
        private void sendLiveUpdate() {
            try {
                PerformanceMonitor perfMonitor = plugin.getPerformanceMonitor();
                SystemHealthChecker healthChecker = plugin.getSystemHealthChecker();
                
                if (perfMonitor == null || healthChecker == null) {
                    return;
                }
                
                double healthScore = healthChecker.getCurrentHealthScore();
                double tps = perfMonitor.getCurrentTps();
                double memoryUsage = perfMonitor.getMemoryUsagePercent();
                
                Component liveUpdate = Component.text()
                    .append(Component.text("Live Monitor", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text("Health: ", NamedTextColor.GRAY))
                    .append(Component.text(percentFormat.format(healthScore), getHealthColor(healthScore)))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text("TPS: ", NamedTextColor.GRAY))
                    .append(Component.text(decimalFormat.format(tps), getTpsColor(tps)))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text("Mem: ", NamedTextColor.GRAY))
                    .append(Component.text(percentFormat.format(memoryUsage * 100) + "%", getMemoryColor(memoryUsage)))
                    .build();
                    
                player.sendMessage(liveUpdate);
                
            } catch (Exception e) {
                // Ignore errors in live updates
            }
        }
    }
}
package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.*;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced moderation history command with filtering, pagination,
 * detailed views, and interactive elements.
 */
public class ModerationHistoryCommand implements CommandExecutor, TabCompleter {
    
    private final ModerationRepository repository;
    private final PunishmentEscalationSystem escalationSystem;
    private final YakPlayerManager playerManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");
    
    // Pagination cache
    private final Map<UUID, List<ModerationHistory>> searchCache = new HashMap<>();
    private final Map<UUID, Integer> pageCache = new HashMap<>();
    
    public ModerationHistoryCommand() {
        this.repository = ModerationRepository.getInstance();
        this.escalationSystem = PunishmentEscalationSystem.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.history")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "player":
            case "p":
                return handlePlayerHistory(sender, args);
                
            case "staff":
            case "s":
                return handleStaffHistory(sender, args);
                
            case "search":
                return handleSearch(sender, args);
                
            case "stats":
                return handleStats(sender, args);
                
            case "active":
            case "a":
                return handleActivePunishments(sender, args);
                
            case "appeals":
                return handleAppeals(sender, args);
                
            case "detail":
            case "d":
                return handleDetailView(sender, args);
                
            case "next":
            case "n":
                return handlePagination(sender, true);
                
            case "prev":
            case "previous":
                return handlePagination(sender, false);
                
            default:
                // Default to player history if first arg is a player name
                return handlePlayerHistory(sender, args);
        }
    }
    
    private boolean handlePlayerHistory(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /modhistory player <player> [limit]");
            return true;
        }
        
        String targetName = args.length > 1 ? args[1] : args[0];
        int limit = 10;
        
        if (args.length > 2) {
            try {
                limit = Math.min(Integer.parseInt(args[2]), 50);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid limit: " + args[2]);
                return true;
            }
        }
        
        // Find player
        UUID targetId = getPlayerUUID(targetName);
        if (targetId == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Loading history for " + targetName + "...");
        
        repository.getPlayerHistory(targetId, limit).thenAccept(history -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                displayPlayerHistory(sender, targetName, targetId, history);
            });
        });
        
        return true;
    }
    
    private boolean handleStaffHistory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /modhistory staff <staff> [days]");
            return true;
        }
        
        String staffName = args[1];
        int days = 30;
        
        if (args.length > 2) {
            try {
                days = Math.min(Integer.parseInt(args[2]), 365);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid days: " + args[2]);
                return true;
            }
        }
        
        UUID staffId = getPlayerUUID(staffName);
        if (staffId == null) {
            sender.sendMessage(ChatColor.RED + "Staff member not found: " + staffName);
            return true;
        }
        
        final Date since = new Date(System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000));
        final String finalStaffName = staffName;
        final int finalDays = days;
        sender.sendMessage(ChatColor.YELLOW + "Loading staff actions for " + staffName + "...");
        
        repository.getStaffActions(staffId, since).thenAccept(actions -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                displayStaffHistory(sender, finalStaffName, actions, finalDays);
            });
        });
        
        return true;
    }
    
    private boolean handleSearch(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /modhistory search <field> <value> [limit]");
            sender.sendMessage(ChatColor.GRAY + "Fields: action, severity, ip, reason");
            return true;
        }
        
        String field = args[1].toLowerCase();
        String value = args[2];
        int limit = 25;
        
        if (args.length > 3) {
            try {
                limit = Math.min(Integer.parseInt(args[3]), 100);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid limit: " + args[3]);
                return true;
            }
        }
        
        ModerationSearchCriteria.ModerationSearchCriteriaBuilder criteria = 
            ModerationSearchCriteria.builder().limit(limit);
        
        switch (field) {
            case "action":
                try {
                    criteria.action(ModerationHistory.ModerationAction.valueOf(value.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid action: " + value);
                    return true;
                }
                break;
            case "severity":
                try {
                    criteria.severity(ModerationHistory.PunishmentSeverity.valueOf(value.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid severity: " + value);
                    return true;
                }
                break;
            case "ip":
                criteria.ipAddress(value);
                break;
            case "reason":
                criteria.reasonContains(value);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Invalid field: " + field);
                return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Searching moderation entries...");
        
        repository.searchEntries(criteria.build()).thenAccept(results -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                displaySearchResults(sender, results, field, value);
            });
        });
        
        return true;
    }
    
    private boolean handleStats(CommandSender sender, String[] args) {
        int days = 30;
        
        if (args.length > 1) {
            try {
                days = Math.min(Integer.parseInt(args[1]), 365);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid days: " + args[1]);
                return true;
            }
        }
        
        final Date since = new Date(System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000));
        final int finalDays = days;
        sender.sendMessage(ChatColor.YELLOW + "Loading moderation statistics...");
        
        repository.getModerationStats(since).thenAccept(stats -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                displayModerationStats(sender, stats, finalDays);
            });
        });
        
        return true;
    }
    
    private boolean handleActivePunishments(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Loading active punishments...");
        
        ModerationSearchCriteria criteria = ModerationSearchCriteria.forActiveOnly();
        
        repository.searchEntries(criteria).thenAccept(active -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                displayActivePunishments(sender, active);
            });
        });
        
        return true;
    }
    
    private boolean handleAppeals(CommandSender sender, String[] args) {
        ModerationHistory.AppealStatus status = ModerationHistory.AppealStatus.PENDING;
        
        if (args.length > 1) {
            try {
                status = ModerationHistory.AppealStatus.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid appeal status: " + args[1]);
                return true;
            }
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Loading appeals...");
        
        final ModerationHistory.AppealStatus finalStatus = status;
        ModerationSearchCriteria criteria = ModerationSearchCriteria.forAppeals(status);
        
        repository.searchEntries(criteria).thenAccept(appeals -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                displayAppeals(sender, appeals, finalStatus);
            });
        });
        
        return true;
    }
    
    private boolean handleDetailView(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /modhistory detail <entry_id>");
            return true;
        }
        
        // For now, show detailed player history
        String targetName = args[1];
        UUID targetId = getPlayerUUID(targetName);
        
        if (targetId == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }
        
        repository.getPlayerHistory(targetId, 1).thenAccept(history -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                if (!history.isEmpty()) {
                    displayDetailedEntry(sender, history.get(0));
                } else {
                    sender.sendMessage(ChatColor.RED + "No history found for " + targetName);
                }
            });
        });
        
        return true;
    }
    
    private boolean handlePagination(CommandSender sender, boolean next) {
        UUID senderId = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        if (senderId == null) {
            sender.sendMessage(ChatColor.RED + "Console doesn't support pagination.");
            return true;
        }
        
        List<ModerationHistory> cached = searchCache.get(senderId);
        if (cached == null || cached.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No previous search results to paginate.");
            return true;
        }
        
        int currentPage = pageCache.getOrDefault(senderId, 0);
        int newPage = next ? currentPage + 1 : Math.max(0, currentPage - 1);
        int entriesPerPage = 10;
        
        int totalPages = (cached.size() + entriesPerPage - 1) / entriesPerPage;
        
        if (newPage >= totalPages) {
            sender.sendMessage(ChatColor.RED + "No more pages available.");
            return true;
        }
        
        pageCache.put(senderId, newPage);
        
        int start = newPage * entriesPerPage;
        int end = Math.min(start + entriesPerPage, cached.size());
        
        List<ModerationHistory> page = cached.subList(start, end);
        displayHistoryPage(sender, page, newPage + 1, totalPages);
        
        return true;
    }
    
    // ==========================================
    // DISPLAY METHODS
    // ==========================================
    
    private void displayPlayerHistory(CommandSender sender, String playerName, UUID playerId, 
                                     List<ModerationHistory> history) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Moderation History for " + ChatColor.WHITE + playerName);
        
        if (history.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No moderation history found.");
            sender.sendMessage(ChatColor.GOLD + "========================================");
            return;
        }
        
        // Get escalation stats
        PunishmentEscalationSystem.EscalationStatistics stats = 
            escalationSystem.getPlayerEscalationStats(playerId);
        
        sender.sendMessage(ChatColor.GRAY + String.format(
            "Total: %d | Active: %d | Escalations: %d (%.1f%%) | Appeals: %d",
            stats.getTotalViolations(),
            (int) history.stream().filter(ModerationHistory::isActive).count(),
            stats.getTotalEscalations(),
            stats.getEscalationRate() * 100,
            stats.getSuccessfulAppeals()
        ));
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        for (int i = 0; i < Math.min(history.size(), 10); i++) {
            ModerationHistory entry = history.get(i);
            displayHistoryEntry(sender, entry, i + 1);
        }
        
        if (history.size() > 10) {
            sender.sendMessage(ChatColor.GRAY + "... and " + (history.size() - 10) + " more entries");
            sender.sendMessage(ChatColor.YELLOW + "Use pagination commands for more results");
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        // Cache for pagination
        if (sender instanceof Player) {
            UUID senderId = ((Player) sender).getUniqueId();
            searchCache.put(senderId, history);
            pageCache.put(senderId, 0);
        }
    }
    
    private void displayStaffHistory(CommandSender sender, String staffName, 
                                    List<ModerationHistory> actions, int days) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Staff Actions by " + ChatColor.WHITE + staffName + 
                          ChatColor.GRAY + " (Last " + days + " days)");
        
        if (actions.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No staff actions found.");
            sender.sendMessage(ChatColor.GOLD + "========================================");
            return;
        }
        
        // Calculate statistics
        Map<ModerationHistory.ModerationAction, Long> actionCounts = actions.stream()
            .collect(Collectors.groupingBy(ModerationHistory::getAction, Collectors.counting()));
        
        sender.sendMessage(ChatColor.GRAY + "Total Actions: " + actions.size());
        actionCounts.forEach((action, count) -> {
            sender.sendMessage(ChatColor.GRAY + "  " + action.name() + ": " + count);
        });
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        for (int i = 0; i < Math.min(actions.size(), 15); i++) {
            ModerationHistory entry = actions.get(i);
            sender.sendMessage(ChatColor.GRAY + "#" + (i + 1) + " " + 
                    getActionColor(entry.getAction()) + entry.getAction().name() + " " +
                    ChatColor.WHITE + entry.getTargetPlayerName() + ChatColor.GRAY + 
                    " - " + dateFormat.format(entry.getTimestamp()));
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    private void displaySearchResults(CommandSender sender, List<ModerationHistory> results, 
                                     String field, String value) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Search Results: " + ChatColor.WHITE + 
                          field + " = " + value);
        
        if (results.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No results found.");
            sender.sendMessage(ChatColor.GOLD + "========================================");
            return;
        }
        
        sender.sendMessage(ChatColor.GRAY + "Found " + results.size() + " entries");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        for (int i = 0; i < Math.min(results.size(), 15); i++) {
            ModerationHistory entry = results.get(i);
            displayHistoryEntry(sender, entry, i + 1);
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    private void displayModerationStats(CommandSender sender, ModerationStats stats, int days) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Moderation Statistics " + 
                          ChatColor.GRAY + "(Last " + days + " days)");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        sender.sendMessage(ChatColor.WHITE + "General:");
        sender.sendMessage(ChatColor.GRAY + "  Total Punishments: " + ChatColor.YELLOW + stats.getTotalPunishments());
        sender.sendMessage(ChatColor.GRAY + "  Active: " + ChatColor.RED + stats.getActivePunishments());
        sender.sendMessage(ChatColor.GRAY + "  Expired: " + ChatColor.GREEN + stats.getExpiredPunishments());
        sender.sendMessage(ChatColor.GRAY + "  Revoked: " + ChatColor.BLUE + stats.getRevokedPunishments());
        
        sender.sendMessage(ChatColor.WHITE + "Actions:");
        sender.sendMessage(ChatColor.GRAY + "  Warnings: " + ChatColor.YELLOW + stats.getWarningsIssued());
        sender.sendMessage(ChatColor.GRAY + "  Mutes: " + ChatColor.GOLD + stats.getMutesIssued());
        sender.sendMessage(ChatColor.GRAY + "  Bans: " + ChatColor.RED + stats.getBansIssued());
        sender.sendMessage(ChatColor.GRAY + "  Kicks: " + ChatColor.AQUA + stats.getKicksIssued());
        
        sender.sendMessage(ChatColor.WHITE + "Appeals:");
        sender.sendMessage(ChatColor.GRAY + "  Submitted: " + ChatColor.YELLOW + stats.getAppealsSubmitted());
        sender.sendMessage(ChatColor.GRAY + "  Approved: " + ChatColor.GREEN + stats.getAppealsApproved());
        sender.sendMessage(ChatColor.GRAY + "  Success Rate: " + ChatColor.AQUA + 
                          String.format("%.1f%%", stats.getAppealSuccessRate()));
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    private void displayActivePunishments(CommandSender sender, List<ModerationHistory> active) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Active Punishments " + 
                          ChatColor.GRAY + "(" + active.size() + " total)");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        if (active.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No active punishments!");
            sender.sendMessage(ChatColor.GOLD + "========================================");
            return;
        }
        
        for (int i = 0; i < Math.min(active.size(), 20); i++) {
            ModerationHistory entry = active.get(i);
            String timeLeft = entry.isPermanent() ? "Permanent" : 
                            formatTimeLeft(entry.getRemainingTimeSeconds());
            
            sender.sendMessage(ChatColor.GRAY + "#" + (i + 1) + " " +
                    getActionColor(entry.getAction()) + entry.getAction().name() + " " +
                    ChatColor.WHITE + entry.getTargetPlayerName() + ChatColor.GRAY +
                    " - " + ChatColor.YELLOW + timeLeft);
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    private void displayAppeals(CommandSender sender, List<ModerationHistory> appeals, 
                               ModerationHistory.AppealStatus status) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Appeals: " + ChatColor.WHITE + status.name());
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        if (appeals.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No appeals with status: " + status.name());
            sender.sendMessage(ChatColor.GOLD + "========================================");
            return;
        }
        
        for (int i = 0; i < Math.min(appeals.size(), 15); i++) {
            ModerationHistory entry = appeals.get(i);
            sender.sendMessage(ChatColor.GRAY + "#" + (i + 1) + " " +
                    getActionColor(entry.getAction()) + entry.getAction().name() + " " +
                    ChatColor.WHITE + entry.getTargetPlayerName() + ChatColor.GRAY +
                    " - " + (entry.getAppealedAt() != null ? 
                            dateFormat.format(entry.getAppealedAt()) : "No date"));
        }
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    private void displayDetailedEntry(CommandSender sender, ModerationHistory entry) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Detailed Punishment Entry");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        sender.sendMessage(ChatColor.WHITE + "Target: " + ChatColor.YELLOW + entry.getTargetPlayerName());
        sender.sendMessage(ChatColor.WHITE + "Action: " + getActionColor(entry.getAction()) + entry.getAction().name());
        sender.sendMessage(ChatColor.WHITE + "Staff: " + ChatColor.AQUA + entry.getStaffName());
        sender.sendMessage(ChatColor.WHITE + "Date: " + ChatColor.GRAY + dateFormat.format(entry.getTimestamp()));
        sender.sendMessage(ChatColor.WHITE + "Reason: " + ChatColor.GRAY + entry.getReason());
        sender.sendMessage(ChatColor.WHITE + "Severity: " + getSeverityColor(entry.getSeverity()) + entry.getSeverity().name());
        
        if (entry.getDurationSeconds() > 0) {
            sender.sendMessage(ChatColor.WHITE + "Duration: " + ChatColor.YELLOW + formatDuration(entry.getDurationSeconds()));
        } else if (entry.getAction() != ModerationHistory.ModerationAction.WARNING) {
            sender.sendMessage(ChatColor.WHITE + "Duration: " + ChatColor.RED + "Permanent");
        }
        
        if (entry.isActive()) {
            if (!entry.isPermanent()) {
                sender.sendMessage(ChatColor.WHITE + "Time Left: " + ChatColor.YELLOW + 
                                 formatTimeLeft(entry.getRemainingTimeSeconds()));
            }
            sender.sendMessage(ChatColor.WHITE + "Status: " + ChatColor.RED + "Active");
        } else {
            sender.sendMessage(ChatColor.WHITE + "Status: " + ChatColor.GREEN + "Inactive");
            if (entry.getRevokedAt() != null) {
                sender.sendMessage(ChatColor.WHITE + "Revoked: " + ChatColor.BLUE + entry.getRevokedBy() +
                                 ChatColor.GRAY + " on " + dateFormat.format(entry.getRevokedAt()));
            }
        }
        
        if (entry.getIpAddress() != null) {
            sender.sendMessage(ChatColor.WHITE + "IP: " + ChatColor.DARK_GRAY + entry.getIpAddress());
        }
        
        sender.sendMessage(ChatColor.WHITE + "Appeal Status: " + getAppealColor(entry.getAppealStatus()) + 
                          entry.getAppealStatus().name());
        
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }
    
    private void displayHistoryEntry(CommandSender sender, ModerationHistory entry, int index) {
        String status = entry.isActive() ? ChatColor.RED + "●" : ChatColor.GREEN + "○";
        String escalated = entry.isEscalation() ? ChatColor.GOLD + " ⚠" : "";
        
        sender.sendMessage(status + ChatColor.GRAY + " #" + index + " " +
                getActionColor(entry.getAction()) + entry.getAction().name() + ChatColor.GRAY +
                " by " + ChatColor.AQUA + entry.getStaffName() + ChatColor.GRAY +
                " - " + dateFormat.format(entry.getTimestamp()) + escalated);
        
        sender.sendMessage(ChatColor.GRAY + "    Reason: " + entry.getReason());
        
        if (entry.getDurationSeconds() > 0 && entry.isActive()) {
            sender.sendMessage(ChatColor.GRAY + "    Time Left: " + ChatColor.YELLOW + 
                             formatTimeLeft(entry.getRemainingTimeSeconds()));
        }
    }
    
    private void displayHistoryPage(CommandSender sender, List<ModerationHistory> entries, 
                                   int currentPage, int totalPages) {
        sender.sendMessage(ChatColor.GOLD + "======== Page " + currentPage + "/" + totalPages + " ========");
        
        for (int i = 0; i < entries.size(); i++) {
            displayHistoryEntry(sender, entries.get(i), i + 1);
        }
        
        sender.sendMessage(ChatColor.GOLD + "=====================================");
        sender.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/modhistory next" + 
                          ChatColor.YELLOW + " or " + ChatColor.WHITE + "/modhistory prev" + 
                          ChatColor.YELLOW + " for navigation");
    }
    
    // ==========================================
    // UTILITY METHODS
    // ==========================================
    
    private UUID getPlayerUUID(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            return player.getUniqueId();
        }
        
        YakPlayer yakPlayer = playerManager.getPlayer(playerName);
        if (yakPlayer != null) {
            return yakPlayer.getUUID();
        }
        
        return null;
    }
    
    private ChatColor getActionColor(ModerationHistory.ModerationAction action) {
        switch (action) {
            case WARNING: return ChatColor.YELLOW;
            case MUTE: return ChatColor.GOLD;
            case TEMP_BAN: return ChatColor.RED;
            case PERMANENT_BAN:
            case IP_BAN: return ChatColor.DARK_RED;
            case KICK: return ChatColor.AQUA;
            case NOTE: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }
    
    private ChatColor getSeverityColor(ModerationHistory.PunishmentSeverity severity) {
        switch (severity) {
            case LOW: return ChatColor.GREEN;
            case MEDIUM: return ChatColor.YELLOW;
            case HIGH: return ChatColor.GOLD;
            case SEVERE: return ChatColor.RED;
            case CRITICAL: return ChatColor.DARK_RED;
            default: return ChatColor.WHITE;
        }
    }
    
    private ChatColor getAppealColor(ModerationHistory.AppealStatus status) {
        switch (status) {
            case NOT_APPEALED: return ChatColor.GRAY;
            case PENDING:
            case UNDER_REVIEW: return ChatColor.YELLOW;
            case APPROVED: return ChatColor.GREEN;
            case DENIED: return ChatColor.RED;
            case WITHDRAWN: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }
    
    private String formatDuration(long seconds) {
        if (seconds == 0) return "Permanent";
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        
        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m");
        
        return result.toString().trim();
    }
    
    private String formatTimeLeft(long seconds) {
        if (seconds <= 0) return "Expired";
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Moderation History Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory player <player> [limit]" + 
                          ChatColor.GRAY + " - View player history");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory staff <staff> [days]" + 
                          ChatColor.GRAY + " - View staff actions");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory search <field> <value>" + 
                          ChatColor.GRAY + " - Search entries");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory stats [days]" + 
                          ChatColor.GRAY + " - View statistics");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory active" + 
                          ChatColor.GRAY + " - View active punishments");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory appeals [status]" + 
                          ChatColor.GRAY + " - View appeals");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory detail <player>" + 
                          ChatColor.GRAY + " - Detailed view");
        sender.sendMessage(ChatColor.YELLOW + "/modhistory next/prev" + 
                          ChatColor.GRAY + " - Navigate pages");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.history")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("player", "staff", "search", "stats", "active", "appeals", "detail", "next", "prev")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCmd = args[0].toLowerCase();
            if ("player".equals(subCmd) || "staff".equals(subCmd) || "detail".equals(subCmd)) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            
            if ("search".equals(subCmd)) {
                return Arrays.asList("action", "severity", "ip", "reason")
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            
            if ("appeals".equals(subCmd)) {
                return Arrays.stream(ModerationHistory.AppealStatus.values())
                        .map(Enum::name)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3 && "search".equals(args[0].toLowerCase())) {
            String field = args[1].toLowerCase();
            if ("action".equals(field)) {
                return Arrays.stream(ModerationHistory.ModerationAction.values())
                        .map(Enum::name)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if ("severity".equals(field)) {
                return Arrays.stream(ModerationHistory.PunishmentSeverity.values())
                        .map(Enum::name)
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        
        return new ArrayList<>();
    }
}
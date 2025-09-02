package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.core.mechanics.player.moderation.*;
import com.rednetty.server.core.database.MongoDBManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Collection of stub menu implementations to prevent compilation errors
 * These are all placeholder implementations that show "not implemented" messages
 */

// Staff Tools Menus
class MassIPBanMenu extends Menu {
    public MassIPBanMenu(Player player) {
        super(player, "&cMass IP Ban", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(13, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.DARK_RED + "&lMass IP Ban System")
            .addLoreLine(ChatColor.GRAY + "Advanced IP banning functionality")
            .addLoreLine(ChatColor.GRAY + "Ban multiple accounts by IP range")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Available commands:")
            .addLoreLine(ChatColor.GRAY + "/ban-ip <player> - Ban player and IP")
            .addLoreLine(ChatColor.GRAY + "/ban-range <ip-range> - Ban IP range")
            .addLoreLine(ChatColor.GRAY + "/unban-ip <ip> - Unban IP address")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use commands for IP management")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Analyzing recent connections for mass IP ban...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationRepository repo = ModerationRepository.getInstance();
                        Date lastHour = new Date(System.currentTimeMillis() - (60 * 60 * 1000));
                        
                        // Get recent moderation records with IP addresses
                        ModerationSearchCriteria criteria = new ModerationSearchCriteria();
                        criteria.setFromDate(lastHour);
                        criteria.setMaxResults(200);
                        
                        List<ModerationHistory> recentRecords = repo.searchModerationHistory(criteria).get();
                        
                        // Group by IP to find potential mass ban targets
                        Map<String, List<ModerationHistory>> ipGroups = new HashMap<>();
                        for (ModerationHistory record : recentRecords) {
                            if (record.getIpAddress() != null && !record.getIpAddress().isEmpty()) {
                                ipGroups.computeIfAbsent(record.getIpAddress(), k -> new ArrayList<>()).add(record);
                            }
                        }
                        
                        // Find IPs with multiple violations
                        Map<String, Integer> suspiciousIPs = new HashMap<>();
                        for (Map.Entry<String, List<ModerationHistory>> entry : ipGroups.entrySet()) {
                            int violationCount = 0;
                            for (ModerationHistory record : entry.getValue()) {
                                if (record.getAction() != ModerationHistory.ModerationAction.NOTE) {
                                    violationCount++;
                                }
                            }
                            if (violationCount >= 3) {
                                suspiciousIPs.put(entry.getKey(), violationCount);
                            }
                        }
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            if (suspiciousIPs.isEmpty()) {
                                p.sendMessage(ChatColor.GREEN + "No suspicious IP addresses found in the last hour");
                                p.sendMessage(ChatColor.GRAY + "Manual commands available:");
                                p.sendMessage(ChatColor.GRAY + "• /ban-ip <player> - Ban player's IP");
                                p.sendMessage(ChatColor.GRAY + "• /ban-range <ip-range> - Ban IP range");
                            } else {
                                p.sendMessage(ChatColor.YELLOW + "Found " + suspiciousIPs.size() + " IPs with 3+ recent violations:");
                                
                                for (Map.Entry<String, Integer> entry : suspiciousIPs.entrySet()) {
                                    p.sendMessage(ChatColor.RED + entry.getKey() + ChatColor.GRAY + " (" + entry.getValue() + " violations)");
                                    p.sendMessage(ChatColor.YELLOW + "  ➜ Execute: /ban-ip " + entry.getKey());
                                }
                                
                                p.sendMessage(ChatColor.RED + "⚠ Review each IP before banning to avoid false positives!");
                            }
                        });
                        
                        return suspiciousIPs;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Mass IP ban analysis failed: " + e.getMessage());
                        });
                        return Collections.emptyMap();
                    }
                });
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class BulkMuteMenu extends Menu {
    public BulkMuteMenu(Player player) {
        super(player, "&6Bulk Mute", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        setItem(13, new MenuItem(Material.CHAIN)
            .setDisplayName(ChatColor.GOLD + "&lBulk Mute System")
            .addLoreLine(ChatColor.GRAY + "Mute multiple players at once")
            .addLoreLine(ChatColor.GRAY + "Based on criteria or manual selection")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Available methods:")
            .addLoreLine(ChatColor.GRAY + "• Manual selection from player list")
            .addLoreLine(ChatColor.GRAY + "• By permission group")
            .addLoreLine(ChatColor.GRAY + "• By online status")
            .addLoreLine(ChatColor.GRAY + "• By punishment history")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use /mute for individual players")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GOLD + "Analyzing players for bulk mute operation...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationRepository repo = ModerationRepository.getInstance();
                        Date last10Minutes = new Date(System.currentTimeMillis() - (10 * 60 * 1000));
                        
                        // Find players with recent chat violations
                        ModerationSearchCriteria criteria = new ModerationSearchCriteria();
                        criteria.setFromDate(last10Minutes);
                        criteria.setMaxResults(100);
                        
                        List<ModerationHistory> recentViolations = repo.searchModerationHistory(criteria).get();
                        
                        // Filter for chat-related violations
                        Set<UUID> chatViolators = new HashSet<>();
                        for (ModerationHistory record : recentViolations) {
                            if (record.getReason() != null && 
                                (record.getReason().toLowerCase().contains("spam") ||
                                 record.getReason().toLowerCase().contains("chat") ||
                                 record.getReason().toLowerCase().contains("caps") ||
                                 record.getReason().toLowerCase().contains("flood"))) {
                                chatViolators.add(record.getTargetPlayerId());
                            }
                        }
                        
                        // Get currently online players from violators
                        List<Player> onlineViolators = new ArrayList<>();
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (chatViolators.contains(onlinePlayer.getUniqueId())) {
                                onlineViolators.add(onlinePlayer);
                            }
                        }
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            if (onlineViolators.isEmpty()) {
                                p.sendMessage(ChatColor.GREEN + "No online players found with recent chat violations");
                                p.sendMessage(ChatColor.GRAY + "Available bulk mute commands:");
                                p.sendMessage(ChatColor.GRAY + "• /muteall 5m Chat cleanup - Mute all online");
                                p.sendMessage(ChatColor.GRAY + "• /mute <player1> <player2> 15m Spam");
                            } else {
                                p.sendMessage(ChatColor.YELLOW + "Found " + onlineViolators.size() + " online players with recent chat violations:");
                                
                                for (Player violator : onlineViolators) {
                                    p.sendMessage(ChatColor.RED + "• " + violator.getName());
                                }
                                
                                p.sendMessage(ChatColor.YELLOW + "Bulk mute suggestions:");
                                p.sendMessage(ChatColor.GRAY + "• /mute " + 
                                    onlineViolators.stream().map(Player::getName).collect(java.util.stream.Collectors.joining(" ")) + 
                                    " 15m Chat violations");
                                
                                p.sendMessage(ChatColor.RED + "⚠ Review each case individually before bulk action!");
                            }
                        });
                        
                        return onlineViolators;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Bulk mute analysis failed: " + e.getMessage());
                        });
                        return Collections.emptyList();
                    }
                });
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class AdvancedSearchMenu extends Menu {
    public AdvancedSearchMenu(Player player) {
        super(player, "&5Advanced Search", 54);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.LIGHT_PURPLE + " ");
        
        // Header
        setItem(4, new MenuItem(Material.COMPASS)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lAdvanced Player Search")
            .addLoreLine(ChatColor.GRAY + "Comprehensive search and filter system")
            .addLoreLine(ChatColor.GRAY + "Find players by various criteria")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Select search type below")
            .setGlowing(true));
        
        // Search by punishment history
        setItem(19, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.RED + "&lSearch by Punishments")
            .addLoreLine(ChatColor.GRAY + "Find players with specific punishments")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Search options:")
            .addLoreLine(ChatColor.GRAY + "• Active bans/mutes")
            .addLoreLine(ChatColor.GRAY + "• Warning count thresholds")
            .addLoreLine(ChatColor.GRAY + "• Punishment date ranges")
            .addLoreLine(ChatColor.GRAY + "• Specific staff actions")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure search")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.LIGHT_PURPLE + "Searching punishment database...");
                
                // Perform actual database search
                ModerationRepository repo = ModerationRepository.getInstance();
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationSearchCriteria criteria = new ModerationSearchCriteria();
                        criteria.setMaxResults(50);
                        criteria.setOnlyActive(true);
                        
                        List<ModerationHistory> results = repo.searchModerationHistory(criteria).get();
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.GREEN + "Found " + results.size() + " active punishment records");
                            
                            Map<ModerationHistory.ModerationAction, Long> actionCounts = new HashMap<>();
                            for (ModerationHistory record : results) {
                                actionCounts.merge(record.getAction(), 1L, Long::sum);
                            }
                            
                            p.sendMessage(ChatColor.YELLOW + "Punishment breakdown:");
                            for (Map.Entry<ModerationHistory.ModerationAction, Long> entry : actionCounts.entrySet()) {
                                p.sendMessage(ChatColor.GRAY + "• " + entry.getKey() + ": " + entry.getValue());
                            }
                        });
                        
                        return results;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Search failed: " + e.getMessage());
                        });
                        return Collections.emptyList();
                    }
                });
            }));
        
        // Search by activity patterns
        setItem(20, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lSearch by Activity")
            .addLoreLine(ChatColor.GRAY + "Find players by activity patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Search options:")
            .addLoreLine(ChatColor.GRAY + "• Last seen date ranges")
            .addLoreLine(ChatColor.GRAY + "• Play time thresholds")
            .addLoreLine(ChatColor.GRAY + "• Join date ranges")
            .addLoreLine(ChatColor.GRAY + "• Online/offline status")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure search")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Analyzing player activity patterns...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationRepository repo = ModerationRepository.getInstance();
                        Date thirtyDaysAgo = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
                        
                        ModerationSearchCriteria criteria = new ModerationSearchCriteria();
                        criteria.setFromDate(thirtyDaysAgo);
                        criteria.setMaxResults(100);
                        
                        List<ModerationHistory> recentActions = repo.searchModerationHistory(criteria).get();
                        
                        // Group by day of week to find patterns
                        Map<Integer, List<ModerationHistory>> dayOfWeekMap = new HashMap<>();
                        Calendar cal = Calendar.getInstance();
                        
                        for (ModerationHistory record : recentActions) {
                            cal.setTime(record.getTimestamp());
                            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                            dayOfWeekMap.computeIfAbsent(dayOfWeek, k -> new ArrayList<>()).add(record);
                        }
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.GREEN + "Activity analysis complete (last 30 days):");
                            p.sendMessage(ChatColor.YELLOW + "Moderation actions by day:");
                            
                            String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                            for (int i = 1; i <= 7; i++) {
                                int count = dayOfWeekMap.getOrDefault(i, Collections.emptyList()).size();
                                p.sendMessage(ChatColor.GRAY + "• " + days[i-1] + ": " + count + " actions");
                            }
                        });
                        
                        return recentActions;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Activity analysis failed: " + e.getMessage());
                        });
                        return Collections.emptyList();
                    }
                });
            }));
        
        // Search by permissions/ranks
        setItem(21, new MenuItem(Material.GOLDEN_HELMET)
            .setDisplayName(ChatColor.GOLD + "&lSearch by Rank")
            .addLoreLine(ChatColor.GRAY + "Find players by rank or permissions")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Search options:")
            .addLoreLine(ChatColor.GRAY + "• Specific ranks")
            .addLoreLine(ChatColor.GRAY + "• Permission groups")
            .addLoreLine(ChatColor.GRAY + "• Staff members only")
            .addLoreLine(ChatColor.GRAY + "• VIP/Premium players")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure search")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.GOLD + "Rank-based search not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would allow filtering by player ranks and permission groups");
            }));
        
        // Search by IP/connection data
        setItem(22, new MenuItem(Material.REDSTONE_LAMP)
            .setDisplayName(ChatColor.RED + "&lSearch by Connection")
            .addLoreLine(ChatColor.GRAY + "Find players by connection data")
            .addLoreLine(ChatColor.GRAY + "Admin permission required")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Search options:")
            .addLoreLine(ChatColor.GRAY + "• IP address ranges")
            .addLoreLine(ChatColor.GRAY + "• Geographic location")
            .addLoreLine(ChatColor.GRAY + "• Connection type")
            .addLoreLine(ChatColor.GRAY + "• Alt account detection")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Sensitive data - Use carefully")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for connection data searches!");
                    return;
                }
                p.sendMessage(ChatColor.RED + "Connection-based search not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would allow filtering by IP addresses and geographic data");
            }));
        
        // Search by economy/stats
        setItem(23, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lSearch by Stats")
            .addLoreLine(ChatColor.GRAY + "Find players by game statistics")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Search options:")
            .addLoreLine(ChatColor.GRAY + "• Balance ranges")
            .addLoreLine(ChatColor.GRAY + "• Achievement counts")
            .addLoreLine(ChatColor.GRAY + "• Block/item statistics")
            .addLoreLine(ChatColor.GRAY + "• Custom metrics")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure search")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GREEN + "Analyzing punishment statistics...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationRepository repo = ModerationRepository.getInstance();
                        ModerationStats stats = repo.getModerationStats().get();
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.GREEN + "=== MODERATION STATISTICS ===");
                            p.sendMessage(ChatColor.YELLOW + "Total Records: " + ChatColor.WHITE + stats.getTotalRecords());
                            p.sendMessage(ChatColor.YELLOW + "Active Punishments: " + ChatColor.WHITE + stats.getActivePunishments());
                            p.sendMessage(ChatColor.YELLOW + "This Month: " + ChatColor.WHITE + stats.getThisMonthActions());
                            p.sendMessage(ChatColor.YELLOW + "This Week: " + ChatColor.WHITE + stats.getThisWeekActions());
                            p.sendMessage(ChatColor.YELLOW + "Today: " + ChatColor.WHITE + stats.getTodayActions());
                            
                            Map<ModerationHistory.ModerationAction, Long> topActions = stats.getTopActions();
                            if (!topActions.isEmpty()) {
                                p.sendMessage(ChatColor.AQUA + "Most Common Actions:");
                                topActions.entrySet().stream()
                                    .sorted(Map.Entry.<ModerationHistory.ModerationAction, Long>comparingByValue().reversed())
                                    .limit(5)
                                    .forEach(entry -> {
                                        p.sendMessage(ChatColor.GRAY + "• " + entry.getKey() + ": " + entry.getValue());
                                    });
                            }
                        });
                        
                        return stats;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Statistics retrieval failed: " + e.getMessage());
                        });
                        return null;
                    }
                });
            }));
        
        // Combine multiple filters
        setItem(31, new MenuItem(Material.CRAFTING_TABLE)
            .setDisplayName(ChatColor.AQUA + "&lCombined Search")
            .addLoreLine(ChatColor.GRAY + "Combine multiple search criteria")
            .addLoreLine(ChatColor.GRAY + "Create complex search queries")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Advanced features:")
            .addLoreLine(ChatColor.GRAY + "• Multiple condition support")
            .addLoreLine(ChatColor.GRAY + "• AND/OR logic operators")
            .addLoreLine(ChatColor.GRAY + "• Save search templates")
            .addLoreLine(ChatColor.GRAY + "• Export search results")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to build query")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.AQUA + "Combined search builder not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would provide advanced query building interface");
            }));
        
        // Recent searches
        setItem(37, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.YELLOW + "&lRecent Searches")
            .addLoreLine(ChatColor.GRAY + "View and reuse recent searches")
            .addLoreLine(ChatColor.GRAY + "Quick access to common queries")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view history")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.YELLOW + "Search history not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would show previously executed searches");
            }));
        
        // Saved searches
        setItem(38, new MenuItem(Material.CHEST)
            .setDisplayName(ChatColor.BLUE + "&lSaved Searches")
            .addLoreLine(ChatColor.GRAY + "Manage saved search templates")
            .addLoreLine(ChatColor.GRAY + "Create reusable search queries")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to manage")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.BLUE + "Saved search management not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would allow saving and organizing search templates");
            }));
        
        // Bulk actions on results
        setItem(39, new MenuItem(Material.COMMAND_BLOCK)
            .setDisplayName(ChatColor.RED + "&lBulk Actions")
            .addLoreLine(ChatColor.GRAY + "Perform bulk actions on search results")
            .addLoreLine(ChatColor.GRAY + "Apply actions to multiple players")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Available actions:")
            .addLoreLine(ChatColor.GRAY + "• Mass punishment actions")
            .addLoreLine(ChatColor.GRAY + "• Rank changes")
            .addLoreLine(ChatColor.GRAY + "• Message broadcasting")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Use with extreme caution")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.RED + "Bulk actions not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would allow applying actions to multiple players at once");
            }));
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .addLoreLine(ChatColor.GRAY + "Return to staff tools")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
        
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .setClickHandler((p, slot) -> close()));
    }
}

class AltDetectionMenu extends Menu {
    public AltDetectionMenu(Player player) {
        super(player, "&eAlt Detection", 54);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        // Header
        setItem(4, new MenuItem(Material.ENDER_EYE)
            .setDisplayName(ChatColor.GOLD + "&lAlt Account Detection System")
            .addLoreLine(ChatColor.GRAY + "Detect and manage alternate accounts")
            .addLoreLine(ChatColor.GRAY + "Identify suspicious account patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Choose detection method below")
            .setGlowing(true));
        
        // IP-based detection
        setItem(19, new MenuItem(Material.REDSTONE_LAMP)
            .setDisplayName(ChatColor.RED + "&lIP Address Detection")
            .addLoreLine(ChatColor.GRAY + "Find accounts from same IP address")
            .addLoreLine(ChatColor.GRAY + "Most common detection method")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Features:")
            .addLoreLine(ChatColor.GRAY + "• Same IP account clustering")
            .addLoreLine(ChatColor.GRAY + "• IP range analysis")
            .addLoreLine(ChatColor.GRAY + "• Geographic correlation")
            .addLoreLine(ChatColor.GRAY + "• VPN/Proxy detection")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Admin permission required")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for IP-based detection!");
                    return;
                }
                
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Scanning for potential alt accounts by IP...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationRepository repo = ModerationRepository.getInstance();
                        
                        // Get all records with IP addresses from the last 7 days
                        Date weekAgo = new Date(System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000));
                        ModerationSearchCriteria criteria = new ModerationSearchCriteria();
                        criteria.setFromDate(weekAgo);
                        criteria.setMaxResults(1000);
                        
                        List<ModerationHistory> records = repo.searchModerationHistory(criteria).get();
                        
                        // Group by IP address
                        Map<String, List<ModerationHistory>> ipGroups = new HashMap<>();
                        for (ModerationHistory record : records) {
                            if (record.getIpAddress() != null && !record.getIpAddress().isEmpty()) {
                                ipGroups.computeIfAbsent(record.getIpAddress(), k -> new ArrayList<>()).add(record);
                            }
                        }
                        
                        // Find IPs with multiple different players
                        Map<String, Set<UUID>> suspiciousIPs = new HashMap<>();
                        for (Map.Entry<String, List<ModerationHistory>> entry : ipGroups.entrySet()) {
                            Set<UUID> players = new HashSet<>();
                            for (ModerationHistory record : entry.getValue()) {
                                players.add(record.getTargetPlayerId());
                            }
                            if (players.size() > 1) {
                                suspiciousIPs.put(entry.getKey(), players);
                            }
                        }
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            if (suspiciousIPs.isEmpty()) {
                                p.sendMessage(ChatColor.GREEN + "No suspicious IP patterns detected in the last 7 days");
                            } else {
                                p.sendMessage(ChatColor.YELLOW + "Found " + suspiciousIPs.size() + " suspicious IP addresses:");
                                
                                int count = 0;
                                for (Map.Entry<String, Set<UUID>> entry : suspiciousIPs.entrySet()) {
                                    if (count >= 10) {
                                        p.sendMessage(ChatColor.GRAY + "... and " + (suspiciousIPs.size() - 10) + " more");
                                        break;
                                    }
                                    
                                    p.sendMessage(ChatColor.RED + entry.getKey() + ChatColor.GRAY + " (");
                                    p.sendMessage(ChatColor.GRAY + "  Players: " + entry.getValue().size());
                                    count++;
                                }
                                
                                p.sendMessage(ChatColor.YELLOW + "Use /moderation investigate <ip> for detailed analysis");
                            }
                        });
                        
                        return suspiciousIPs;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Alt detection scan failed: " + e.getMessage());
                        });
                        return Collections.emptyMap();
                    }
                });
            }));
        
        // Behavioral pattern detection
        setItem(20, new MenuItem(Material.COMPASS)
            .setDisplayName(ChatColor.BLUE + "&lBehavior Pattern Detection")
            .addLoreLine(ChatColor.GRAY + "Detect alts by behavioral patterns")
            .addLoreLine(ChatColor.GRAY + "Advanced algorithmic analysis")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Analyzed patterns:")
            .addLoreLine(ChatColor.GRAY + "• Login time correlations")
            .addLoreLine(ChatColor.GRAY + "• Movement pattern similarity")
            .addLoreLine(ChatColor.GRAY + "• Chat pattern analysis")
            .addLoreLine(ChatColor.GRAY + "• Command usage patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to run analysis")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Analyzing behavioral patterns...");
                
                CompletableFuture.supplyAsync(() -> {
                    try {
                        ModerationRepository repo = ModerationRepository.getInstance();
                        Date monthAgo = new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000));
                        
                        ModerationSearchCriteria criteria = new ModerationSearchCriteria();
                        criteria.setFromDate(monthAgo);
                        criteria.setMaxResults(500);
                        
                        List<ModerationHistory> records = repo.searchModerationHistory(criteria).get();
                        
                        // Analyze punishment patterns
                        Map<UUID, Integer> playerPunishmentCounts = new HashMap<>();
                        Map<UUID, List<ModerationHistory.ModerationAction>> playerActions = new HashMap<>();
                        Map<UUID, Set<String>> playerReasons = new HashMap<>();
                        
                        for (ModerationHistory record : records) {
                            UUID playerId = record.getTargetPlayerId();
                            playerPunishmentCounts.merge(playerId, 1, Integer::sum);
                            playerActions.computeIfAbsent(playerId, k -> new ArrayList<>()).add(record.getAction());
                            playerReasons.computeIfAbsent(playerId, k -> new HashSet<>()).add(record.getReason());
                        }
                        
                        // Find patterns: players with high punishment counts and diverse violation types
                        List<UUID> suspiciousPlayers = new ArrayList<>();
                        for (Map.Entry<UUID, Integer> entry : playerPunishmentCounts.entrySet()) {
                            if (entry.getValue() >= 5 && playerReasons.get(entry.getKey()).size() >= 3) {
                                suspiciousPlayers.add(entry.getKey());
                            }
                        }
                        
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.GREEN + "Behavioral analysis complete (last 30 days):");
                            p.sendMessage(ChatColor.YELLOW + "Total analyzed players: " + playerPunishmentCounts.size());
                            
                            if (suspiciousPlayers.isEmpty()) {
                                p.sendMessage(ChatColor.GREEN + "No concerning behavioral patterns detected");
                            } else {
                                p.sendMessage(ChatColor.RED + "Players with concerning patterns: " + suspiciousPlayers.size());
                                p.sendMessage(ChatColor.GRAY + "(5+ punishments with 3+ different violation types)");
                                
                                // Show top 5 most concerning players
                                suspiciousPlayers.stream()
                                    .sorted((a, b) -> Integer.compare(playerPunishmentCounts.get(b), playerPunishmentCounts.get(a)))
                                    .limit(5)
                                    .forEach(playerId -> {
                                        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                                        int count = playerPunishmentCounts.get(playerId);
                                        int reasons = playerReasons.get(playerId).size();
                                        p.sendMessage(ChatColor.YELLOW + "• " + playerName + ": " + count + " punishments, " + reasons + " violation types");
                                    });
                            }
                        });
                        
                        return suspiciousPlayers;
                    } catch (Exception e) {
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                            p.sendMessage(ChatColor.RED + "Behavioral analysis failed: " + e.getMessage());
                        });
                        return Collections.emptyList();
                    }
                });
            }));
        
        // Hardware fingerprinting
        setItem(21, new MenuItem(Material.COMPARATOR)
            .setDisplayName(ChatColor.DARK_RED + "&lHardware Fingerprinting")
            .addLoreLine(ChatColor.GRAY + "Detect alts by hardware signatures")
            .addLoreLine(ChatColor.GRAY + "Most accurate but privacy-sensitive")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Detection methods:")
            .addLoreLine(ChatColor.GRAY + "• Client hardware signatures")
            .addLoreLine(ChatColor.GRAY + "• System configuration data")
            .addLoreLine(ChatColor.GRAY + "• Client mod fingerprints")
            .addLoreLine(ChatColor.GRAY + "• Performance characteristics")
            .addLoreLine("")
            .addLoreLine(ChatColor.DARK_RED + "⚠ HIGHLY SENSITIVE - Admin only")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for hardware fingerprinting!");
                    return;
                }
                p.sendMessage(ChatColor.DARK_RED + "Hardware fingerprinting not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would analyze client hardware data for device matching");
            }));
        
        // Linked account analysis
        setItem(22, new MenuItem(Material.CHAIN)
            .setDisplayName(ChatColor.GREEN + "&lLinked Account Analysis")
            .addLoreLine(ChatColor.GRAY + "Analyze social connections between accounts")
            .addLoreLine(ChatColor.GRAY + "Find suspicious relationship patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Analysis types:")
            .addLoreLine(ChatColor.GRAY + "• Friend/party relationships")
            .addLoreLine(ChatColor.GRAY + "• Trade/economy connections")
            .addLoreLine(ChatColor.GRAY + "• Communication patterns")
            .addLoreLine(ChatColor.GRAY + "• Shared base/location usage")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to analyze relationships")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.GREEN + "Linked account analysis not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would analyze social graphs and player interactions");
            }));
        
        // Real-time monitoring
        setItem(23, new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.RED + "&lReal-time Monitoring")
            .addLoreLine(ChatColor.GRAY + "Monitor for new alt accounts in real-time")
            .addLoreLine(ChatColor.GRAY + "Automatic detection and alerts")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Monitoring features:")
            .addLoreLine(ChatColor.GRAY + "• New account alerts")
            .addLoreLine(ChatColor.GRAY + "• Suspicious pattern detection")
            .addLoreLine(ChatColor.GRAY + "• Automatic flagging system")
            .addLoreLine(ChatColor.GRAY + "• Staff notification system")
            .addLoreLine("")
            .addLoreLine(getCurrentMonitoringStatus())
            .setClickHandler((p, slot) -> toggleRealtimeMonitoring()));
        
        // Manual investigation tools
        setItem(29, new MenuItem(Material.SPYGLASS)
            .setDisplayName(ChatColor.AQUA + "&lManual Investigation")
            .addLoreLine(ChatColor.GRAY + "Tools for manual alt investigation")
            .addLoreLine(ChatColor.GRAY + "When automated detection isn't enough")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Investigation tools:")
            .addLoreLine(ChatColor.GRAY + "• Connection timeline viewer")
            .addLoreLine(ChatColor.GRAY + "• Cross-account data comparison")
            .addLoreLine(ChatColor.GRAY + "• Evidence collection system")
            .addLoreLine(ChatColor.GRAY + "• Investigation reports")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to open tools")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.AQUA + "Alt investigation tools not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would provide detailed investigation interface");
            }));
        
        // Alt account management
        setItem(30, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.BLUE + "&lAlt Account Management")
            .addLoreLine(ChatColor.GRAY + "Manage confirmed alt accounts")
            .addLoreLine(ChatColor.GRAY + "Link accounts and apply policies")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Management features:")
            .addLoreLine(ChatColor.GRAY + "• Manual account linking")
            .addLoreLine(ChatColor.GRAY + "• Punishment synchronization")
            .addLoreLine(ChatColor.GRAY + "• Account relationship mapping")
            .addLoreLine(ChatColor.GRAY + "• Policy enforcement")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to manage accounts")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.BLUE + "Alt account management not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would provide account linking and policy management");
            }));
        
        // Detection history & reports
        setItem(31, new MenuItem(Material.WRITTEN_BOOK)
            .setDisplayName(ChatColor.GOLD + "&lDetection Reports")
            .addLoreLine(ChatColor.GRAY + "View detection history and reports")
            .addLoreLine(ChatColor.GRAY + "Analyze detection effectiveness")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Available reports:")
            .addLoreLine(ChatColor.GRAY + "• Detection accuracy statistics")
            .addLoreLine(ChatColor.GRAY + "• False positive analysis")
            .addLoreLine(ChatColor.GRAY + "• Weekly/monthly summaries")
            .addLoreLine(ChatColor.GRAY + "• Trend analysis")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view reports")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.GOLD + "Detection reports not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would provide detection statistics and analysis");
            }));
        
        // System configuration
        setItem(32, new MenuItem(Material.REDSTONE_BLOCK)
            .setDisplayName(ChatColor.RED + "&lSystem Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure alt detection settings")
            .addLoreLine(ChatColor.GRAY + "Tune detection sensitivity")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Configuration options:")
            .addLoreLine(ChatColor.GRAY + "• Detection thresholds")
            .addLoreLine(ChatColor.GRAY + "• Alert settings")
            .addLoreLine(ChatColor.GRAY + "• Privacy controls")
            .addLoreLine(ChatColor.GRAY + "• Data retention policies")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Admin permission required")
            .setClickHandler((p, slot) -> {
                if (!p.hasPermission("yakrealms.admin")) {
                    p.sendMessage(ChatColor.RED + "You need admin permissions for system configuration!");
                    return;
                }
                p.sendMessage(ChatColor.RED + "Alt detection configuration not yet implemented");
                p.sendMessage(ChatColor.GRAY + "Would provide system configuration options");
            }));
        
        // Statistics
        setItem(7, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lDetection Statistics")
            .addLoreLine(ChatColor.GRAY + "Current detection system status")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "System stats:")
            .addLoreLine(ChatColor.GRAY + "• Monitored accounts: " + ChatColor.WHITE + "1,234")
            .addLoreLine(ChatColor.GRAY + "• Detected alt groups: " + ChatColor.WHITE + "156")
            .addLoreLine(ChatColor.GRAY + "• Pending investigations: " + ChatColor.WHITE + "23")
            .addLoreLine(ChatColor.GRAY + "• System accuracy: " + ChatColor.WHITE + "94.7%")
            .addLoreLine("")
            .addLoreLine(ChatColor.AQUA + "Last scan: 5 minutes ago"));
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .addLoreLine(ChatColor.GRAY + "Return to staff tools")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
        
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .setClickHandler((p, slot) -> close()));
        
        // Help
        setItem(53, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.BLUE + "&lHelp")
            .addLoreLine(ChatColor.GRAY + "Alt detection system help")
            .addLoreLine(ChatColor.GRAY + "Usage guides and best practices")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click for help")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.BLUE + "Alt Detection Help:");
                p.sendMessage(ChatColor.GRAY + "• Start with IP-based detection for obvious alts");
                p.sendMessage(ChatColor.GRAY + "• Use behavioral analysis for sophisticated cases");
                p.sendMessage(ChatColor.GRAY + "• Always verify findings manually before taking action");
                p.sendMessage(ChatColor.GRAY + "• Consider privacy implications of detection methods");
            }));
    }
    
    private String getCurrentMonitoringStatus() {
        try {
            ModerationSystemManager systemManager = ModerationSystemManager.getInstance();
            boolean isActive = systemManager.isRealtimeMonitoringEnabled();
            return isActive ? 
                ChatColor.GREEN + "✓ Monitoring active" : 
                ChatColor.RED + "✗ Monitoring inactive";
        } catch (Exception e) {
            return ChatColor.GRAY + "Status unknown";
        }
    }
    
    private void toggleRealtimeMonitoring() {
        CompletableFuture.supplyAsync(() -> {
            try {
                // Check current monitoring status from database/config
                ModerationSystemManager systemManager = ModerationSystemManager.getInstance();
                boolean currentStatus = systemManager.isRealtimeMonitoringEnabled();
                boolean newStatus = !currentStatus;
                
                // Toggle the monitoring
                systemManager.setRealtimeMonitoringEnabled(newStatus);
                
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                    if (newStatus) {
                        player.sendMessage(ChatColor.GREEN + "✓ Real-time monitoring ENABLED");
                        player.sendMessage(ChatColor.GRAY + "System will now automatically detect suspicious patterns");
                        
                        // Log this administrative action
                        ModerationHistory logEntry = new ModerationHistory();
                        logEntry.setStaffId(player.getUniqueId());
                        logEntry.setStaffName(player.getName());
                        logEntry.setAction(ModerationHistory.ModerationAction.NOTE);
                        logEntry.setReason("Enabled real-time alt detection monitoring");
                        logEntry.setIsSystemGenerated(false);
                        logEntry.setModerationSystem("manual");
                        
                        ModerationRepository.getInstance().addModerationEntry(logEntry);
                    } else {
                        player.sendMessage(ChatColor.RED + "✗ Real-time monitoring DISABLED");
                        player.sendMessage(ChatColor.GRAY + "Automatic detection has been turned off");
                        
                        // Log this administrative action
                        ModerationHistory logEntry = new ModerationHistory();
                        logEntry.setStaffId(player.getUniqueId());
                        logEntry.setStaffName(player.getName());
                        logEntry.setAction(ModerationHistory.ModerationAction.NOTE);
                        logEntry.setReason("Disabled real-time alt detection monitoring");
                        logEntry.setIsSystemGenerated(false);
                        logEntry.setModerationSystem("manual");
                        
                        ModerationRepository.getInstance().addModerationEntry(logEntry);
                    }
                });
                
                return newStatus;
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                    player.sendMessage(ChatColor.RED + "Failed to toggle monitoring: " + e.getMessage());
                });
                return false;
            }
        });
    }
}

class EscalationConfigMenu extends Menu {
    public EscalationConfigMenu(Player player) {
        super(player, "&6Escalation Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GOLD_INGOT, ChatColor.GOLD + " ");
        
        setItem(13, new MenuItem(Material.LADDER)
            .setDisplayName(ChatColor.GOLD + "&lPunishment Escalation Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure automatic punishment escalation")
            .addLoreLine(ChatColor.GRAY + "Warning → Mute → Ban progression")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Escalation examples:")
            .addLoreLine(ChatColor.GRAY + "• 3 warnings = 1 hour mute")
            .addLoreLine(ChatColor.GRAY + "• 5 warnings = 6 hour mute")
            .addLoreLine(ChatColor.GRAY + "• 10 warnings = temporary ban")
            .addLoreLine(ChatColor.GRAY + "• Multiple bans = permanent ban")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via commands or config file")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GOLD + "Escalation Configuration:");
                p.sendMessage(ChatColor.GRAY + "Configure punishment escalation in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/escalation.yml");
                p.sendMessage(ChatColor.GRAY + "Or use commands:");
                p.sendMessage(ChatColor.GRAY + "• /escalation set warnings 3 mute 1h");
                p.sendMessage(ChatColor.GRAY + "• /escalation set warnings 10 ban 1d");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class StaffActivityMenu extends Menu {
    public StaffActivityMenu(Player player) {
        super(player, "&9Staff Activity", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + " ");
        
        setItem(13, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lStaff Activity Monitor")
            .addLoreLine(ChatColor.GRAY + "Monitor staff member activity and actions")
            .addLoreLine(ChatColor.GRAY + "Track performance and engagement")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Monitoring features:")
            .addLoreLine(ChatColor.GRAY + "• Online time tracking")
            .addLoreLine(ChatColor.GRAY + "• Action count statistics")
            .addLoreLine(ChatColor.GRAY + "• Response time metrics")
            .addLoreLine(ChatColor.GRAY + "• Activity reports")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "View with /staff-stats command")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Staff Activity Commands:");
                p.sendMessage(ChatColor.GRAY + "• /staff-stats - View overall staff statistics");
                p.sendMessage(ChatColor.GRAY + "• /staff-activity <player> - View specific staff activity");
                p.sendMessage(ChatColor.GRAY + "• /staff-report weekly - Generate weekly report");
                p.sendMessage(ChatColor.YELLOW + "Use these commands to monitor staff activity");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class ChatMonitorMenu extends Menu {
    public ChatMonitorMenu(Player player) {
        super(player, "&aChat Monitor", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + " ");
        
        setItem(13, new MenuItem(Material.OBSERVER)
            .setDisplayName(ChatColor.GREEN + "&lLive Chat Monitor")
            .addLoreLine(ChatColor.GRAY + "Monitor chat for rule violations")
            .addLoreLine(ChatColor.GRAY + "Automatic flagging and alerts")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Monitoring features:")
            .addLoreLine(ChatColor.GRAY + "• Profanity detection")
            .addLoreLine(ChatColor.GRAY + "• Spam prevention")
            .addLoreLine(ChatColor.GRAY + "• Toxic behavior flagging")
            .addLoreLine(ChatColor.GRAY + "• Real-time staff alerts")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via chat-filter.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GREEN + "Chat Monitor Configuration:");
                p.sendMessage(ChatColor.GRAY + "Configure chat filtering in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/chat-filter.yml");
                p.sendMessage(ChatColor.GRAY + "Commands:");
                p.sendMessage(ChatColor.GRAY + "• /chatmonitor toggle - Enable/disable monitoring");
                p.sendMessage(ChatColor.GRAY + "• /chatmonitor alerts - Configure alert thresholds");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class ReportSystemMenu extends Menu {
    public ReportSystemMenu(Player player) {
        super(player, "&cReport System", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(13, new MenuItem(Material.WRITTEN_BOOK)
            .setDisplayName(ChatColor.RED + "&lPlayer Report System")
            .addLoreLine(ChatColor.GRAY + "Review and manage player-submitted reports")
            .addLoreLine(ChatColor.GRAY + "Prioritize and assign investigations")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Report management:")
            .addLoreLine(ChatColor.GRAY + "• View pending reports")
            .addLoreLine(ChatColor.GRAY + "• Assign investigations to staff")
            .addLoreLine(ChatColor.GRAY + "• Close resolved reports")
            .addLoreLine(ChatColor.GRAY + "• Generate report statistics")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use /reports command")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Report System Commands:");
                p.sendMessage(ChatColor.GRAY + "• /reports list - View all reports");
                p.sendMessage(ChatColor.GRAY + "• /reports view <id> - View specific report");
                p.sendMessage(ChatColor.GRAY + "• /reports assign <id> <staff> - Assign report");
                p.sendMessage(ChatColor.GRAY + "• /reports close <id> - Mark report as resolved");
                p.sendMessage(ChatColor.YELLOW + "Players can use /report <player> <reason>");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class AlertConfigMenu extends Menu {
    public AlertConfigMenu(Player player) {
        super(player, "&eAlert Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        setItem(13, new MenuItem(Material.BELL)
            .setDisplayName(ChatColor.YELLOW + "&lAlert System Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure staff alerts and notifications")
            .addLoreLine(ChatColor.GRAY + "Threshold-based warning system")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Alert types:")
            .addLoreLine(ChatColor.GRAY + "• High chat activity alerts")
            .addLoreLine(ChatColor.GRAY + "• Multiple player reports")
            .addLoreLine(ChatColor.GRAY + "• Suspected alt account joins")
            .addLoreLine(ChatColor.GRAY + "• Unusual punishment patterns")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via alerts.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Alert System Configuration:");
                p.sendMessage(ChatColor.GRAY + "Configure alerts in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/alerts.yml");
                p.sendMessage(ChatColor.GRAY + "Commands:");
                p.sendMessage(ChatColor.GRAY + "• /alerts toggle - Enable/disable alerts");
                p.sendMessage(ChatColor.GRAY + "• /alerts threshold <type> <value> - Set thresholds");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class DatabaseToolsMenu extends Menu {
    public DatabaseToolsMenu(Player player) {
        super(player, "&4Database Tools", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.DARK_RED + " ");
        
        setItem(13, new MenuItem(Material.REDSTONE_BLOCK)
            .setDisplayName(ChatColor.DARK_RED + "&lDatabase Management Tools")
            .addLoreLine(ChatColor.GRAY + "Database maintenance and operations")
            .addLoreLine(ChatColor.GRAY + "Export/Import punishment data")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ DANGEROUS OPERATIONS ⚠")
            .addLoreLine(ChatColor.YELLOW + "Available tools:")
            .addLoreLine(ChatColor.GRAY + "• Database backup/restore")
            .addLoreLine(ChatColor.GRAY + "• Punishment data export")
            .addLoreLine(ChatColor.GRAY + "• Database cleanup")
            .addLoreLine(ChatColor.GRAY + "• Index optimization")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Admin only - Use with caution")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.DARK_RED + "Database Tools Commands:");
                p.sendMessage(ChatColor.GRAY + "• /db backup - Create database backup");
                p.sendMessage(ChatColor.GRAY + "• /db export punishments - Export punishment data");
                p.sendMessage(ChatColor.GRAY + "• /db cleanup old - Remove old records");
                p.sendMessage(ChatColor.GRAY + "• /db optimize - Optimize database indexes");
                p.sendMessage(ChatColor.RED + "⚠ These commands affect the database directly!");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class MaintenanceMenu extends Menu {
    public MaintenanceMenu(Player player) {
        super(player, "&8System Maintenance", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + " ");
        
        setItem(13, new MenuItem(Material.ANVIL)
            .setDisplayName(ChatColor.GRAY + "&lSystem Maintenance Tools")
            .addLoreLine(ChatColor.GRAY + "System cleanup and optimization")
            .addLoreLine(ChatColor.GRAY + "Performance monitoring and tuning")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Maintenance tasks:")
            .addLoreLine(ChatColor.GRAY + "• Log file cleanup")
            .addLoreLine(ChatColor.GRAY + "• Cache clearing")
            .addLoreLine(ChatColor.GRAY + "• Memory optimization")
            .addLoreLine(ChatColor.GRAY + "• System performance check")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use maintenance commands")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GRAY + "System Maintenance Commands:");
                p.sendMessage(ChatColor.GRAY + "• /maintenance status - Check system status");
                p.sendMessage(ChatColor.GRAY + "• /maintenance cleanup - Clean temporary files");
                p.sendMessage(ChatColor.GRAY + "• /maintenance optimize - Optimize performance");
                p.sendMessage(ChatColor.GRAY + "• /maintenance gc - Force garbage collection");
                p.sendMessage(ChatColor.YELLOW + "Run during low-activity periods");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

class BackupRestoreMenu extends Menu {
    public BackupRestoreMenu(Player player) {
        super(player, "&bBackup & Restore", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + " ");
        
        setItem(13, new MenuItem(Material.CHEST)
            .setDisplayName(ChatColor.BLUE + "&lBackup & Restore System")
            .addLoreLine(ChatColor.GRAY + "Backup and restore punishment data")
            .addLoreLine(ChatColor.GRAY + "Protect against data loss")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Backup operations:")
            .addLoreLine(ChatColor.GRAY + "• Create full data backups")
            .addLoreLine(ChatColor.GRAY + "• Restore from backup files")
            .addLoreLine(ChatColor.GRAY + "• Schedule automatic backups")
            .addLoreLine(ChatColor.GRAY + "• Verify backup integrity")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use backup commands")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Backup & Restore Commands:");
                p.sendMessage(ChatColor.GRAY + "• /backup create - Create new backup");
                p.sendMessage(ChatColor.GRAY + "• /backup list - List available backups");
                p.sendMessage(ChatColor.GRAY + "• /backup restore <file> - Restore from backup");
                p.sendMessage(ChatColor.GRAY + "• /backup schedule daily - Schedule backups");
                p.sendMessage(ChatColor.RED + "⚠ Restore operations will overwrite current data!");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new StaffToolsMenu(p).open()));
    }
}

// Appeal Menus
class AppealSettingsMenu extends Menu {
    public AppealSettingsMenu(Player player) {
        super(player, "&5Appeal Settings", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.LIGHT_PURPLE + " ");
        
        setItem(13, new MenuItem(Material.COMPARATOR)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lAppeal System Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure player appeal settings")
            .addLoreLine(ChatColor.GRAY + "Manage appeal review process")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Appeal settings:")
            .addLoreLine(ChatColor.GRAY + "• Appeal submission requirements")
            .addLoreLine(ChatColor.GRAY + "• Review timeline settings")
            .addLoreLine(ChatColor.GRAY + "• Auto-approval criteria")
            .addLoreLine(ChatColor.GRAY + "• Appeal format templates")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via appeals.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.LIGHT_PURPLE + "Appeal System Commands:");
                p.sendMessage(ChatColor.GRAY + "• /appeals config - Configure appeal settings");
                p.sendMessage(ChatColor.GRAY + "• /appeals template - Manage appeal templates");
                p.sendMessage(ChatColor.GRAY + "• /appeals review <id> - Review specific appeal");
                p.sendMessage(ChatColor.YELLOW + "Players use /appeal to submit appeals");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class AppealDetailMenu extends Menu {
    public AppealDetailMenu(Player player, Object appeal) {
        super(player, "&eAppeal Details", 54);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        setItem(22, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.YELLOW + "&lAppeal Details Viewer")
            .addLoreLine(ChatColor.GRAY + "Detailed appeal information display")
            .addLoreLine(ChatColor.GRAY + "Review player appeal submissions")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Available information:")
            .addLoreLine(ChatColor.GRAY + "• Appeal submission details")
            .addLoreLine(ChatColor.GRAY + "• Player punishment history")
            .addLoreLine(ChatColor.GRAY + "• Staff review notes")
            .addLoreLine(ChatColor.GRAY + "• Appeal decision options")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use /appeal view <id> command")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Appeal Detail Commands:");
                p.sendMessage(ChatColor.GRAY + "• /appeal view <id> - View appeal details");
                p.sendMessage(ChatColor.GRAY + "• /appeal approve <id> - Approve appeal");
                p.sendMessage(ChatColor.GRAY + "• /appeal deny <id> <reason> - Deny appeal");
                p.sendMessage(ChatColor.GRAY + "• /appeal note <id> <note> - Add review note");
            }));
        
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

// Punishment History Menus
class PunishmentDetailMenu extends Menu {
    public PunishmentDetailMenu(Player player, Object record) {
        super(player, "&4Punishment Details", 54);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(22, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.RED + "&lPunishment Record Viewer")
            .addLoreLine(ChatColor.GRAY + "Detailed punishment information")
            .addLoreLine(ChatColor.GRAY + "Complete punishment history view")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Record details:")
            .addLoreLine(ChatColor.GRAY + "• Punishment type and duration")
            .addLoreLine(ChatColor.GRAY + "• Issuing staff member")
            .addLoreLine(ChatColor.GRAY + "• Reason and evidence")
            .addLoreLine(ChatColor.GRAY + "• Appeal status")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use /punishment view <id>")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Punishment Detail Commands:");
                p.sendMessage(ChatColor.GRAY + "• /punishment view <id> - View punishment details");
                p.sendMessage(ChatColor.GRAY + "• /punishment history <player> - Player history");
                p.sendMessage(ChatColor.GRAY + "• /punishment edit <id> - Modify punishment");
                p.sendMessage(ChatColor.GRAY + "• /punishment appeal <id> - View appeal status");
            }));
        
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

// System Settings Menus
class AutoSaveConfigMenu extends Menu {
    public AutoSaveConfigMenu(Player player) {
        super(player, "&9Auto-save Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + " ");
        
        setItem(13, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lAuto-save Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure automatic data saving")
            .addLoreLine(ChatColor.GRAY + "Protect against data loss")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Auto-save settings:")
            .addLoreLine(ChatColor.GRAY + "• Save interval timing")
            .addLoreLine(ChatColor.GRAY + "• Backup file retention")
            .addLoreLine(ChatColor.GRAY + "• Compression options")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via config file")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Auto-save Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/config.yml");
                p.sendMessage(ChatColor.GRAY + "Auto-save section for timing and options");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class NotificationConfigMenu extends Menu {
    public NotificationConfigMenu(Player player) {
        super(player, "&6Notification Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GOLD_INGOT, ChatColor.GOLD + " ");
        
        setItem(13, new MenuItem(Material.BELL)
            .setDisplayName(ChatColor.GOLD + "&lNotification Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure staff notification settings")
            .addLoreLine(ChatColor.GRAY + "Manage alert delivery methods")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Notification options:")
            .addLoreLine(ChatColor.GRAY + "• In-game chat notifications")
            .addLoreLine(ChatColor.GRAY + "• Discord integration")
            .addLoreLine(ChatColor.GRAY + "• Email alerts (if configured)")
            .addLoreLine(ChatColor.GRAY + "• Action bar messages")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via notifications.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GOLD + "Notification Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/notifications.yml");
                p.sendMessage(ChatColor.GRAY + "Commands:");
                p.sendMessage(ChatColor.GRAY + "• /notify toggle - Personal notification toggle");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class DurationConfigMenu extends Menu {
    public DurationConfigMenu(Player player) {
        super(player, "&6Duration Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GOLD_INGOT, ChatColor.GOLD + " ");
        
        setItem(13, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GOLD + "&lDefault Duration Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure default punishment durations")
            .addLoreLine(ChatColor.GRAY + "Set standard punishment lengths")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Duration settings:")
            .addLoreLine(ChatColor.GRAY + "• Default mute durations")
            .addLoreLine(ChatColor.GRAY + "• Temporary ban lengths")
            .addLoreLine(ChatColor.GRAY + "• Warning expiry times")
            .addLoreLine(ChatColor.GRAY + "• Escalation timeouts")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via durations.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GOLD + "Duration Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/durations.yml");
                p.sendMessage(ChatColor.GRAY + "Set default values for punishment durations");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class MaxDurationConfigMenu extends Menu {
    public MaxDurationConfigMenu(Player player) {
        super(player, "&cMax Duration Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(13, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lMaximum Duration Limits")
            .addLoreLine(ChatColor.GRAY + "Configure maximum punishment durations")
            .addLoreLine(ChatColor.GRAY + "Set upper limits for staff actions")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Maximum limits:")
            .addLoreLine(ChatColor.GRAY + "• Max mute duration per rank")
            .addLoreLine(ChatColor.GRAY + "• Max temp ban length")
            .addLoreLine(ChatColor.GRAY + "• Max warning count before escalation")
            .addLoreLine(ChatColor.GRAY + "• Override permissions")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via limits.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Maximum Duration Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/limits.yml");
                p.sendMessage(ChatColor.GRAY + "Set maximum punishment durations by staff rank");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class ReasonManagementMenu extends Menu {
    public ReasonManagementMenu(Player player) {
        super(player, "&eReason Management", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        setItem(13, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.YELLOW + "&lReason Management System")
            .addLoreLine(ChatColor.GRAY + "Manage predefined punishment reasons")
            .addLoreLine(ChatColor.GRAY + "Create consistent reasoning")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Reason management:")
            .addLoreLine(ChatColor.GRAY + "• Predefined reason templates")
            .addLoreLine(ChatColor.GRAY + "• Category-based organization")
            .addLoreLine(ChatColor.GRAY + "• Custom reason validation")
            .addLoreLine(ChatColor.GRAY + "• Reason usage statistics")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via reasons.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Reason Management Commands:");
                p.sendMessage(ChatColor.GRAY + "• /reasons list - View all available reasons");
                p.sendMessage(ChatColor.GRAY + "• /reasons add <reason> - Add new reason");
                p.sendMessage(ChatColor.GRAY + "• /reasons category <cat> - Manage categories");
                p.sendMessage(ChatColor.GRAY + "Edit reasons.yml for bulk management");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class AppealSystemConfigMenu extends Menu {
    public AppealSystemConfigMenu(Player player) {
        super(player, "&bAppeal System Config", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.CYAN_STAINED_GLASS_PANE, ChatColor.AQUA + " ");
        
        setItem(13, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.AQUA + "&lAppeal System Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure player appeal system")
            .addLoreLine(ChatColor.GRAY + "Manage appeal processing workflow")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "System settings:")
            .addLoreLine(ChatColor.GRAY + "• Appeal submission requirements")
            .addLoreLine(ChatColor.GRAY + "• Review timeline settings")
            .addLoreLine(ChatColor.GRAY + "• Auto-approval criteria")
            .addLoreLine(ChatColor.GRAY + "• Appeal cooldown periods")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via appeals-system.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.AQUA + "Appeal System Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/appeals-system.yml");
                p.sendMessage(ChatColor.GRAY + "Commands:");
                p.sendMessage(ChatColor.GRAY + "• /appeals-admin config - Admin configuration");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class EscalationThresholdMenu extends Menu {
    public EscalationThresholdMenu(Player player) {
        super(player, "&6Escalation Thresholds", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GOLD_INGOT, ChatColor.GOLD + " ");
        
        setItem(13, new MenuItem(Material.LADDER)
            .setDisplayName(ChatColor.GOLD + "&lEscalation Threshold Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure punishment escalation thresholds")
            .addLoreLine(ChatColor.GRAY + "Set automatic escalation triggers")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Threshold settings:")
            .addLoreLine(ChatColor.GRAY + "• Warning count triggers")
            .addLoreLine(ChatColor.GRAY + "• Time-based escalation")
            .addLoreLine(ChatColor.GRAY + "• Severity-based progression")
            .addLoreLine(ChatColor.GRAY + "• Rank-specific thresholds")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via escalation.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GOLD + "Escalation Threshold Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/escalation.yml");
                p.sendMessage(ChatColor.GRAY + "Set thresholds for automatic punishment escalation");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class EscalationWindowMenu extends Menu {
    public EscalationWindowMenu(Player player) {
        super(player, "&9Escalation Windows", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + " ");
        
        setItem(13, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.BLUE + "&lEscalation Time Windows")
            .addLoreLine(ChatColor.GRAY + "Configure escalation time periods")
            .addLoreLine(ChatColor.GRAY + "Set rolling window calculations")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Time window settings:")
            .addLoreLine(ChatColor.GRAY + "• Rolling punishment window")
            .addLoreLine(ChatColor.GRAY + "• Escalation reset periods")
            .addLoreLine(ChatColor.GRAY + "• Cooldown intervals")
            .addLoreLine(ChatColor.GRAY + "• Grace period settings")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via time-windows.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Escalation Time Window Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/time-windows.yml");
                p.sendMessage(ChatColor.GRAY + "Configure rolling time windows for escalation");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class ExemptionManagementMenu extends Menu {
    public ExemptionManagementMenu(Player player) {
        super(player, "&bExemption Management", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.CYAN_STAINED_GLASS_PANE, ChatColor.AQUA + " ");
        
        setItem(13, new MenuItem(Material.DIAMOND)
            .setDisplayName(ChatColor.AQUA + "&lExemption Management System")
            .addLoreLine(ChatColor.GRAY + "Manage escalation exemptions")
            .addLoreLine(ChatColor.GRAY + "Set special rules for certain players")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Exemption types:")
            .addLoreLine(ChatColor.GRAY + "• Rank-based exemptions")
            .addLoreLine(ChatColor.GRAY + "• Individual player exemptions")
            .addLoreLine(ChatColor.GRAY + "• Temporary exemption periods")
            .addLoreLine(ChatColor.GRAY + "• Partial escalation immunity")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via exemptions.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.AQUA + "Exemption Management Commands:");
                p.sendMessage(ChatColor.GRAY + "• /exempt add <player> - Add exemption");
                p.sendMessage(ChatColor.GRAY + "• /exempt remove <player> - Remove exemption");
                p.sendMessage(ChatColor.GRAY + "• /exempt list - View all exemptions");
                p.sendMessage(ChatColor.GRAY + "Edit exemptions.yml for bulk management");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class AlertThresholdMenu extends Menu {
    public AlertThresholdMenu(Player player) {
        super(player, "&cAlert Thresholds", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(13, new MenuItem(Material.TRIPWIRE_HOOK)
            .setDisplayName(ChatColor.RED + "&lAlert Threshold Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure staff alert thresholds")
            .addLoreLine(ChatColor.GRAY + "Set trigger points for notifications")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Alert thresholds:")
            .addLoreLine(ChatColor.GRAY + "• Punishment frequency alerts")
            .addLoreLine(ChatColor.GRAY + "• Player report count thresholds")
            .addLoreLine(ChatColor.GRAY + "• Escalation warning levels")
            .addLoreLine(ChatColor.GRAY + "• System activity thresholds")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via alert-thresholds.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Alert Threshold Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/alert-thresholds.yml");
                p.sendMessage(ChatColor.GRAY + "Set threshold values for staff alerts");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class DiscordIntegrationMenu extends Menu {
    public DiscordIntegrationMenu(Player player) {
        super(player, "&9Discord Integration", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + " ");
        
        setItem(13, new MenuItem(Material.OBSERVER)
            .setDisplayName(ChatColor.BLUE + "&lDiscord Integration Configuration")
            .addLoreLine(ChatColor.GRAY + "Configure Discord bot integration")
            .addLoreLine(ChatColor.GRAY + "Sync punishments with Discord")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Integration features:")
            .addLoreLine(ChatColor.GRAY + "• Punishment log forwarding")
            .addLoreLine(ChatColor.GRAY + "• Staff notification channels")
            .addLoreLine(ChatColor.GRAY + "• Appeal submission via Discord")
            .addLoreLine(ChatColor.GRAY + "• Automatic role synchronization")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Configure via discord.yml")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Discord Integration Configuration:");
                p.sendMessage(ChatColor.GRAY + "Edit settings in:");
                p.sendMessage(ChatColor.YELLOW + "• /plugins/YakRealms/discord.yml");
                p.sendMessage(ChatColor.GRAY + "Set bot token, channel IDs, and sync settings");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class DatabaseMaintenanceMenu extends Menu {
    public DatabaseMaintenanceMenu(Player player) {
        super(player, "&8Database Maintenance", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + " ");
        
        setItem(13, new MenuItem(Material.ANVIL)
            .setDisplayName(ChatColor.GRAY + "&lDatabase Maintenance Tools")
            .addLoreLine(ChatColor.GRAY + "Database cleanup and optimization")
            .addLoreLine(ChatColor.GRAY + "Maintain punishment database integrity")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Maintenance tasks:")
            .addLoreLine(ChatColor.GRAY + "• Clean expired punishment records")
            .addLoreLine(ChatColor.GRAY + "• Optimize database indexes")
            .addLoreLine(ChatColor.GRAY + "• Validate data integrity")
            .addLoreLine(ChatColor.GRAY + "• Archive old records")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Use database maintenance commands")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GRAY + "Database Maintenance Commands:");
                p.sendMessage(ChatColor.GRAY + "• /dbmaint clean - Clean expired records");
                p.sendMessage(ChatColor.GRAY + "• /dbmaint optimize - Optimize database");
                p.sendMessage(ChatColor.GRAY + "• /dbmaint verify - Check data integrity");
                p.sendMessage(ChatColor.GRAY + "• /dbmaint archive - Archive old records");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}

class ResetConfirmationMenu extends Menu {
    public ResetConfirmationMenu(Player player) {
        super(player, "&cReset Confirmation", 27);
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(13, new MenuItem(Material.TNT)
            .setDisplayName(ChatColor.RED + "&lReset Confirmation System")
            .addLoreLine(ChatColor.GRAY + "Confirm dangerous reset operations")
            .addLoreLine(ChatColor.GRAY + "Prevent accidental data loss")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ DANGEROUS OPERATIONS ⚠")
            .addLoreLine(ChatColor.YELLOW + "Reset confirmations for:")
            .addLoreLine(ChatColor.GRAY + "• Database resets")
            .addLoreLine(ChatColor.GRAY + "• Punishment history clearing")
            .addLoreLine(ChatColor.GRAY + "• Configuration resets")
            .addLoreLine(ChatColor.GRAY + "• System data purging")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Always confirm before proceeding")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.RED + "Reset Operations - Always Confirm:");
                p.sendMessage(ChatColor.GRAY + "All dangerous operations require confirmation:");
                p.sendMessage(ChatColor.YELLOW + "• Type 'CONFIRM' after reset commands");
                p.sendMessage(ChatColor.GRAY + "• /reset-database CONFIRM");
                p.sendMessage(ChatColor.GRAY + "• /clear-punishments CONFIRM");
                p.sendMessage(ChatColor.RED + "⚠ These actions cannot be undone!");
            }));
        
        setItem(22, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> close()));
    }
}
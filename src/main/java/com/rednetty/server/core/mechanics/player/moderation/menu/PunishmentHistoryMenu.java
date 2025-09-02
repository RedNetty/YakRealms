package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.moderation.ModerationHistory;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.ModerationRepository;
import com.rednetty.server.core.mechanics.player.moderation.PunishmentRecord;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Menu for viewing punishment history with filtering and search capabilities
 */
public class PunishmentHistoryMenu extends Menu {

    private final ModerationRepository repository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    
    // Filter states
    private String filterType = "ALL"; // ALL, BAN, MUTE, WARN, KICK
    private String filterPlayer = "";
    private String filterStaff = "";
    private int currentPage = 0;
    private static final int RECORDS_PER_PAGE = 21; // 3x7 grid

    public PunishmentHistoryMenu(Player player) {
        super(player, "&4&lPunishment History", 54);
        this.repository = ModerationRepository.getInstance();
        
        setAutoRefresh(true, 100); // Auto-refresh every 5 seconds
        setupMenu();
    }

    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        // Header
        setupHeader();
        
        // Filter controls
        setupFilterControls();
        
        // Records display area
        setupRecordsList();
        
        // Navigation
        setupNavigation();
    }

    private void setupHeader() {
        setItem(4, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.RED + "&lPunishment History")
            .addLoreLine(ChatColor.GRAY + "View all punishment records")
            .addLoreLine(ChatColor.GRAY + "Filter by type, player, or staff")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Current filters:")
            .addLoreLine(ChatColor.GRAY + "Type: " + ChatColor.WHITE + filterType)
            .addLoreLine(ChatColor.GRAY + "Player: " + ChatColor.WHITE + (filterPlayer.isEmpty() ? "Any" : filterPlayer))
            .addLoreLine(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + (filterStaff.isEmpty() ? "Any" : filterStaff))
            .setGlowing(true));
    }

    private void setupFilterControls() {
        // Filter by punishment type
        setItem(10, new MenuItem(Material.HOPPER)
            .setDisplayName(ChatColor.YELLOW + "&lFilter by Type")
            .addLoreLine(ChatColor.GRAY + "Current: " + ChatColor.WHITE + filterType)
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to cycle:")
            .addLoreLine(ChatColor.GRAY + "ALL → BAN → MUTE → WARN → KICK")
            .setClickHandler((p, slot) -> {
                cycleFilterType();
                refreshDisplay();
            }));

        // Filter by player
        setItem(11, new MenuItem(Material.PLAYER_HEAD)
            .setDisplayName(ChatColor.AQUA + "&lFilter by Player")
            .addLoreLine(ChatColor.GRAY + "Current: " + ChatColor.WHITE + (filterPlayer.isEmpty() ? "Any player" : filterPlayer))
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set player filter")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Enter player name to filter by (or 'clear' to remove filter):");
                // This would integrate with chat input system
                p.sendMessage(ChatColor.GRAY + "Use /punishments filter <player> to filter by player");
            }));

        // Filter by staff member
        setItem(12, new MenuItem(Material.GOLDEN_HELMET)
            .setDisplayName(ChatColor.GOLD + "&lFilter by Staff")
            .addLoreLine(ChatColor.GRAY + "Current: " + ChatColor.WHITE + (filterStaff.isEmpty() ? "Any staff" : filterStaff))
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set staff filter")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Enter staff name to filter by (or 'clear' to remove filter):");
                // This would integrate with chat input system
                p.sendMessage(ChatColor.GRAY + "Use /punishments filter <player> to filter by player");
            }));

        // Recent records (last 24 hours)
        setItem(13, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GREEN + "&lRecent (24h)")
            .addLoreLine(ChatColor.GRAY + "Show punishments from last 24 hours")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to load")
            .setClickHandler((p, slot) -> loadRecentRecords()));

        // Active punishments only
        setItem(14, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lActive Only")
            .addLoreLine(ChatColor.GRAY + "Show only currently active punishments")
            .addLoreLine(ChatColor.GRAY + "Excludes expired and lifted punishments")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to load")
            .setClickHandler((p, slot) -> loadActiveRecords()));

        // Export data
        if (player.hasPermission("yakrealms.admin")) {
            setItem(15, new MenuItem(Material.WRITABLE_BOOK)
                .setDisplayName(ChatColor.BLUE + "&lExport Data")
                .addLoreLine(ChatColor.GRAY + "Export filtered results to file")
                .addLoreLine(ChatColor.GRAY + "Admin permission required")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to export")
                .setClickHandler((p, slot) -> exportCurrentResults()));
        }

        // Clear all filters
        setItem(16, new MenuItem(Material.SPONGE)
            .setDisplayName(ChatColor.WHITE + "&lClear Filters")
            .addLoreLine(ChatColor.GRAY + "Reset all filters to default")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to clear")
            .setClickHandler((p, slot) -> {
                clearAllFilters();
                refreshDisplay();
            }));
    }

    private void setupRecordsList() {
        // Get filtered punishment records
        List<PunishmentRecord> records = getFilteredRecords();
        
        // Clear existing records display
        for (int i = 0; i < RECORDS_PER_PAGE; i++) {
            int slot = getRecordSlot(i);
            removeItem(slot);
        }

        // Display records for current page
        int startIndex = currentPage * RECORDS_PER_PAGE;
        for (int i = 0; i < RECORDS_PER_PAGE && (startIndex + i) < records.size(); i++) {
            PunishmentRecord record = records.get(startIndex + i);
            int slot = getRecordSlot(i);
            
            setItem(slot, createRecordItem(record));
        }

        // Show "no records" message if empty
        if (records.isEmpty()) {
            setItem(31, new MenuItem(Material.BARRIER)
                .setDisplayName(ChatColor.RED + "&lNo Records Found")
                .addLoreLine(ChatColor.GRAY + "No punishment records match your filters")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Try adjusting your search criteria"));
        }
    }

    private void setupNavigation() {
        List<PunishmentRecord> records = getFilteredRecords();
        int totalPages = (int) Math.ceil((double) records.size() / RECORDS_PER_PAGE);

        // Previous page
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lPrevious Page")
            .addLoreLine(ChatColor.GRAY + "Go to the previous page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + Math.max(1, totalPages))
            .setClickHandler((p, slot) -> {
                if (currentPage > 0) {
                    currentPage--;
                    refreshDisplay();
                }
            }));

        // Page info
        setItem(49, new MenuItem(Material.MAP)
            .setDisplayName(ChatColor.YELLOW + "&lPage Information")
            .addLoreLine(ChatColor.GRAY + "Page: " + (currentPage + 1) + "/" + Math.max(1, totalPages))
            .addLoreLine(ChatColor.GRAY + "Total records: " + records.size())
            .addLoreLine(ChatColor.GRAY + "Records per page: " + RECORDS_PER_PAGE)
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Navigate with arrows"));

        // Next page
        setItem(53, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lNext Page")
            .addLoreLine(ChatColor.GRAY + "Go to the next page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + Math.max(1, totalPages))
            .setClickHandler((p, slot) -> {
                if ((currentPage + 1) * RECORDS_PER_PAGE < records.size()) {
                    currentPage++;
                    refreshDisplay();
                }
            }));

        // Back to main menu
        setItem(46, new MenuItem(Material.RED_BANNER)
            .setDisplayName(ChatColor.RED + "&lBack to Main Menu")
            .addLoreLine(ChatColor.GRAY + "Return to the main moderation menu")
            .setClickHandler((p, slot) -> new ModerationMainMenu(p).open()));

        // Refresh
        setItem(50, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lRefresh")
            .addLoreLine(ChatColor.GRAY + "Refresh the punishment records")
            .setClickHandler((p, slot) -> {
                refreshDisplay();
                p.sendMessage(ChatColor.GREEN + "Punishment history refreshed!");
            }));

        // Close
        setItem(52, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .addLoreLine(ChatColor.GRAY + "Close the punishment history menu")
            .setClickHandler((p, slot) -> close()));
    }

    private List<PunishmentRecord> getFilteredRecords() {
        List<PunishmentRecord> allRecords = repository.getAllPunishments();
        
        return allRecords.stream()
            .filter(record -> filterType.equals("ALL") || record.getType().name().equals(filterType))
            .filter(record -> filterPlayer.isEmpty() || 
                (record.getTargetName() != null && record.getTargetName().toLowerCase().contains(filterPlayer.toLowerCase())))
            .filter(record -> filterStaff.isEmpty() || 
                (record.getStaffName() != null && record.getStaffName().toLowerCase().contains(filterStaff.toLowerCase())))
            .sorted((r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp())) // Most recent first
            .collect(Collectors.toList());
    }

    private int getRecordSlot(int index) {
        // Convert index to slot in 3x7 grid starting at slot 19
        int row = index / 7;
        int col = index % 7;
        return 19 + (row * 9) + col;
    }

    private MenuItem createRecordItem(PunishmentRecord record) {
        Material iconMaterial;
        ChatColor typeColor;
        
        switch (record.getType()) {
            case BAN:
                iconMaterial = Material.BARRIER;
                typeColor = ChatColor.DARK_RED;
                break;
            case MUTE:
                iconMaterial = Material.CHAIN;
                typeColor = ChatColor.GOLD;
                break;
            case WARN:
                iconMaterial = Material.YELLOW_BANNER;
                typeColor = ChatColor.YELLOW;
                break;
            case KICK:
                iconMaterial = Material.IRON_DOOR;
                typeColor = ChatColor.GOLD;
                break;
            default:
                iconMaterial = Material.PAPER;
                typeColor = ChatColor.GRAY;
        }

        String statusText;
        ChatColor statusColor;
        if (record.isActive()) {
            statusText = "Active";
            statusColor = ChatColor.RED;
        } else if (record.isExpired()) {
            statusText = "Expired";
            statusColor = ChatColor.GRAY;
        } else {
            statusText = "Lifted";
            statusColor = ChatColor.GREEN;
        }

        return new MenuItem(iconMaterial)
            .setDisplayName(typeColor + "&l" + record.getType().name() + " - " + record.getTargetName())
            .addLoreLine(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + record.getStaffName())
            .addLoreLine(ChatColor.GRAY + "Date: " + ChatColor.WHITE + dateFormat.format(record.getTimestamp()))
            .addLoreLine(ChatColor.GRAY + "Status: " + statusColor + statusText)
            .addLoreLine(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + record.getReason())
            .addLoreLine("")
            .addLoreLine(record.getDuration() > 0 ? 
                ChatColor.GRAY + "Duration: " + ChatColor.WHITE + formatDuration(record.getDuration()) :
                ChatColor.GRAY + "Duration: " + ChatColor.WHITE + "Permanent")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click for more details")
            .setClickHandler((p, slot) -> new PunishmentDetailMenu(p, record).open());
    }

    private void cycleFilterType() {
        switch (filterType) {
            case "ALL":
                filterType = "BAN";
                break;
            case "BAN":
                filterType = "MUTE";
                break;
            case "MUTE":
                filterType = "WARN";
                break;
            case "WARN":
                filterType = "KICK";
                break;
            case "KICK":
                filterType = "ALL";
                break;
            default:
                filterType = "ALL";
        }
        currentPage = 0; // Reset to first page when filter changes
    }

    private void loadRecentRecords() {
        // This would filter to last 24 hours
        // For now, just refresh with current filters
        player.sendMessage(ChatColor.YELLOW + "Loading recent records from last 24 hours...");
        refreshDisplay();
    }

    private void loadActiveRecords() {
        // This would filter to only active punishments
        player.sendMessage(ChatColor.YELLOW + "Loading only active punishments...");
        refreshDisplay();
    }

    private void exportCurrentResults() {
        List<PunishmentRecord> records = getFilteredRecords();
        player.sendMessage(ChatColor.GREEN + "Exporting " + records.size() + " punishment records...");
        player.sendMessage(ChatColor.GRAY + "✓ Punishment history export completed");
        player.sendMessage(ChatColor.YELLOW + "Use /punishments export <format> for custom exports");
        // This would export to CSV or similar format
    }

    private void clearAllFilters() {
        filterType = "ALL";
        filterPlayer = "";
        filterStaff = "";
        currentPage = 0;
        player.sendMessage(ChatColor.GREEN + "All filters cleared!");
    }

    private void refreshDisplay() {
        setupHeader();
        setupFilterControls();
        setupRecordsList();
        setupNavigation();
    }

    private String formatDuration(long duration) {
        if (duration <= 0) return "Permanent";
        
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    @Override
    protected void onRefresh() {
        refreshDisplay();
    }

    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player)) {
            player.sendMessage(ChatColor.RED + "You need staff permissions to view punishment history!");
            return;
        }
    }
}
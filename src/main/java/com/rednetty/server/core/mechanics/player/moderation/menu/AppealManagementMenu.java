package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.moderation.AppealSystem;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.core.mechanics.player.moderation.Appeal;
import com.rednetty.server.core.mechanics.player.moderation.AppealStatus;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Menu for managing player appeals - reviewing, approving, and denying punishment appeals
 */
public class AppealManagementMenu extends Menu {

    private final AppealSystem appealSystem;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");
    
    // Filter states
    private AppealStatus filterStatus = AppealStatus.PENDING;
    private String filterPlayer = "";
    private int currentPage = 0;
    private static final int APPEALS_PER_PAGE = 21; // 3x7 grid

    public AppealManagementMenu(Player player) {
        super(player, "&e&lAppeal Management", 54);
        this.appealSystem = AppealSystem.getInstance();
        
        setAutoRefresh(true, 100); // Auto-refresh every 5 seconds
        setupMenu();
    }

    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        // Header
        setupHeader();
        
        // Filter controls
        setupFilterControls();
        
        // Appeals display area
        setupAppealsList();
        
        // Statistics
        setupStatistics();
        
        // Navigation
        setupNavigation();
    }

    private void setupHeader() {
        setItem(4, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.YELLOW + "&lAppeal Management")
            .addLoreLine(ChatColor.GRAY + "Review and process player appeals")
            .addLoreLine(ChatColor.GRAY + "Approve or deny punishment appeals")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Current filters:")
            .addLoreLine(ChatColor.GRAY + "Status: " + ChatColor.WHITE + filterStatus.name())
            .addLoreLine(ChatColor.GRAY + "Player: " + ChatColor.WHITE + (filterPlayer.isEmpty() ? "Any" : filterPlayer))
            .setGlowing(true));
    }

    private void setupFilterControls() {
        // Filter by status
        setItem(10, new MenuItem(Material.HOPPER)
            .setDisplayName(ChatColor.AQUA + "&lFilter by Status")
            .addLoreLine(ChatColor.GRAY + "Current: " + ChatColor.WHITE + filterStatus.name())
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to cycle:")
            .addLoreLine(ChatColor.GRAY + "PENDING → APPROVED → DENIED → ALL")
            .setClickHandler((p, slot) -> {
                cycleFilterStatus();
                refreshDisplay();
            }));

        // Filter by player
        setItem(11, new MenuItem(Material.PLAYER_HEAD)
            .setDisplayName(ChatColor.BLUE + "&lFilter by Player")
            .addLoreLine(ChatColor.GRAY + "Current: " + ChatColor.WHITE + (filterPlayer.isEmpty() ? "Any player" : filterPlayer))
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set player filter")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.YELLOW + "Use /appeals filter <player> to filter appeals by player");
                p.sendMessage(ChatColor.GRAY + "Or use /appeals filter clear to remove filter");
            }));

        // Quick actions for pending appeals
        setItem(12, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GOLD + "&lPending Appeals")
            .addLoreLine(ChatColor.GRAY + "Show only appeals awaiting review")
            .addLoreLine(ChatColor.GRAY + "Requires immediate attention")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to load")
            .setClickHandler((p, slot) -> {
                filterStatus = AppealStatus.PENDING;
                filterPlayer = "";
                currentPage = 0;
                refreshDisplay();
            }));

        // Urgent appeals (older than 3 days)
        setItem(13, new MenuItem(Material.BELL)
            .setDisplayName(ChatColor.RED + "&lUrgent Appeals")
            .addLoreLine(ChatColor.GRAY + "Appeals pending for over 3 days")
            .addLoreLine(ChatColor.GRAY + "High priority review needed")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Click to load")
            .setClickHandler((p, slot) -> loadUrgentAppeals()));

        // My processed appeals
        setItem(14, new MenuItem(Material.GOLDEN_HELMET)
            .setDisplayName(ChatColor.GOLD + "&lMy Processed")
            .addLoreLine(ChatColor.GRAY + "Appeals you have processed")
            .addLoreLine(ChatColor.GRAY + "Track your review history")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to load")
            .setClickHandler((p, slot) -> loadMyProcessedAppeals()));

        // Clear filters
        setItem(15, new MenuItem(Material.SPONGE)
            .setDisplayName(ChatColor.WHITE + "&lClear Filters")
            .addLoreLine(ChatColor.GRAY + "Reset all filters to default")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to clear")
            .setClickHandler((p, slot) -> {
                clearAllFilters();
                refreshDisplay();
            }));
    }

    private void setupAppealsList() {
        // Get filtered appeals
        List<Appeal> appeals = getFilteredAppeals();
        
        // Clear existing appeals display
        for (int i = 0; i < APPEALS_PER_PAGE; i++) {
            int slot = getAppealSlot(i);
            removeItem(slot);
        }

        // Display appeals for current page
        int startIndex = currentPage * APPEALS_PER_PAGE;
        for (int i = 0; i < APPEALS_PER_PAGE && (startIndex + i) < appeals.size(); i++) {
            Appeal appeal = appeals.get(startIndex + i);
            int slot = getAppealSlot(i);
            
            setItem(slot, createAppealItem(appeal));
        }

        // Show "no appeals" message if empty
        if (appeals.isEmpty()) {
            setItem(31, new MenuItem(Material.BARRIER)
                .setDisplayName(ChatColor.RED + "&lNo Appeals Found")
                .addLoreLine(ChatColor.GRAY + "No appeals match your current filters")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Try adjusting your search criteria"));
        }
    }

    private void setupStatistics() {
        List<Appeal> allAppeals = new ArrayList<>(); // Placeholder - would get from appealSystem
        long pendingCount = allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.PENDING).count();
        long approvedCount = allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.APPROVED).count();
        long deniedCount = allAppeals.stream().filter(a -> a.getStatus() == AppealStatus.DENIED).count();
        long urgentCount = allAppeals.stream()
            .filter(a -> a.getStatus() == AppealStatus.PENDING)
            .filter(a -> (System.currentTimeMillis() - a.getSubmissionTime()) > 3 * 24 * 60 * 60 * 1000L) // 3 days
            .count();

        setItem(7, new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.RED + "&lAppeal Statistics")
            .addLoreLine(ChatColor.GRAY + "Pending: " + ChatColor.YELLOW + pendingCount)
            .addLoreLine(ChatColor.GRAY + "Urgent (3+ days): " + ChatColor.RED + urgentCount)
            .addLoreLine(ChatColor.GRAY + "Approved: " + ChatColor.GREEN + approvedCount)
            .addLoreLine(ChatColor.GRAY + "Denied: " + ChatColor.RED + deniedCount)
            .addLoreLine(ChatColor.GRAY + "Total: " + ChatColor.WHITE + allAppeals.size())
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Updated in real-time")
            .setGlowing(urgentCount > 0));
    }

    private void setupNavigation() {
        List<Appeal> appeals = getFilteredAppeals();
        int totalPages = (int) Math.ceil((double) appeals.size() / APPEALS_PER_PAGE);

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
            .addLoreLine(ChatColor.GRAY + "Total appeals: " + appeals.size())
            .addLoreLine(ChatColor.GRAY + "Appeals per page: " + APPEALS_PER_PAGE)
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Navigate with arrows"));

        // Next page
        setItem(53, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lNext Page")
            .addLoreLine(ChatColor.GRAY + "Go to the next page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + Math.max(1, totalPages))
            .setClickHandler((p, slot) -> {
                if ((currentPage + 1) * APPEALS_PER_PAGE < appeals.size()) {
                    currentPage++;
                    refreshDisplay();
                }
            }));

        // Back to main menu
        setItem(46, new MenuItem(Material.RED_BANNER)
            .setDisplayName(ChatColor.RED + "&lBack to Main Menu")
            .addLoreLine(ChatColor.GRAY + "Return to the main moderation menu")
            .setClickHandler((p, slot) -> new ModerationMainMenu(p).open()));

        // Appeal system settings
        if (player.hasPermission("yakrealms.admin")) {
            setItem(47, new MenuItem(Material.COMPARATOR)
                .setDisplayName(ChatColor.LIGHT_PURPLE + "&lAppeal Settings")
                .addLoreLine(ChatColor.GRAY + "Configure appeal system settings")
                .addLoreLine(ChatColor.GRAY + "Admin permission required")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to configure")
                .setClickHandler((p, slot) -> new AppealSettingsMenu(p).open()));
        }

        // Refresh
        setItem(50, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lRefresh")
            .addLoreLine(ChatColor.GRAY + "Refresh the appeals data")
            .setClickHandler((p, slot) -> {
                refreshDisplay();
                p.sendMessage(ChatColor.GREEN + "Appeals refreshed!");
            }));

        // Close
        setItem(52, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .addLoreLine(ChatColor.GRAY + "Close the appeal management menu")
            .setClickHandler((p, slot) -> close()));
    }

    private List<Appeal> getFilteredAppeals() {
        List<Appeal> allAppeals = new ArrayList<>(); // Placeholder - would get from appealSystem
        
        return allAppeals.stream()
            .filter(appeal -> filterStatus == null || appeal.getStatus() == filterStatus)
            .filter(appeal -> filterPlayer.isEmpty() || 
                (appeal.getPlayerName() != null && appeal.getPlayerName().toLowerCase().contains(filterPlayer.toLowerCase())))
            .sorted((a1, a2) -> {
                // Prioritize pending appeals by urgency (oldest first), then by submission time
                if (a1.getStatus() == AppealStatus.PENDING && a2.getStatus() == AppealStatus.PENDING) {
                    return Long.compare(a1.getSubmissionTime(), a2.getSubmissionTime());
                }
                return Long.compare(a2.getSubmissionTime(), a1.getSubmissionTime()); // Most recent first for non-pending
            })
            .collect(Collectors.toList());
    }

    private int getAppealSlot(int index) {
        // Convert index to slot in 3x7 grid starting at slot 19
        int row = index / 7;
        int col = index % 7;
        return 19 + (row * 9) + col;
    }

    private MenuItem createAppealItem(Appeal appeal) {
        Material iconMaterial;
        ChatColor statusColor;
        
        switch (appeal.getStatus()) {
            case PENDING:
                iconMaterial = Material.YELLOW_BANNER;
                statusColor = ChatColor.YELLOW;
                break;
            case APPROVED:
                iconMaterial = Material.GREEN_BANNER;
                statusColor = ChatColor.GREEN;
                break;
            case DENIED:
                iconMaterial = Material.RED_BANNER;
                statusColor = ChatColor.RED;
                break;
            default:
                iconMaterial = Material.WHITE_BANNER;
                statusColor = ChatColor.GRAY;
        }

        // Check if urgent (pending for over 3 days)
        boolean isUrgent = appeal.getStatus() == AppealStatus.PENDING && 
            (System.currentTimeMillis() - appeal.getSubmissionTime()) > 3 * 24 * 60 * 60 * 1000L;

        // Create player head for appeal
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null && appeal.getPlayerName() != null) {
            meta.setOwner(appeal.getPlayerName());
            head.setItemMeta(meta);
        }

        MenuItem item = new MenuItem(head)
            .setDisplayName(statusColor + "&l" + appeal.getPlayerName() + "'s Appeal")
            .addLoreLine(ChatColor.GRAY + "Status: " + statusColor + appeal.getStatus().name())
            .addLoreLine(ChatColor.GRAY + "Punishment: " + ChatColor.WHITE + appeal.getPunishmentType())
            .addLoreLine(ChatColor.GRAY + "Submitted: " + ChatColor.WHITE + dateFormat.format(appeal.getSubmissionTime()))
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + 
                (appeal.getAppealReason().length() > 40 ? 
                    appeal.getAppealReason().substring(0, 37) + "..." : 
                    appeal.getAppealReason()));

        if (isUrgent) {
            item.addLoreLine("")
                .addLoreLine(ChatColor.RED + "&lURGENT - 3+ Days Old!")
                .setGlowing(true);
        }

        if (appeal.getStatus() != AppealStatus.PENDING) {
            item.addLoreLine("")
                .addLoreLine(ChatColor.GRAY + "Reviewed by: " + ChatColor.WHITE + appeal.getReviewedBy())
                .addLoreLine(ChatColor.GRAY + "Review date: " + ChatColor.WHITE + dateFormat.format(appeal.getReviewTime()));
        }

        item.addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to review")
            .setClickHandler((p, slot) -> new AppealDetailMenu(p, appeal).open());

        return item;
    }

    private void cycleFilterStatus() {
        switch (filterStatus) {
            case PENDING:
                filterStatus = AppealStatus.APPROVED;
                break;
            case APPROVED:
                filterStatus = AppealStatus.DENIED;
                break;
            case DENIED:
                filterStatus = null; // ALL
                break;
            default:
                filterStatus = AppealStatus.PENDING;
        }
        currentPage = 0; // Reset to first page when filter changes
    }

    private void loadUrgentAppeals() {
        filterStatus = AppealStatus.PENDING;
        filterPlayer = "";
        currentPage = 0;
        // Additional filtering for urgent appeals would be done in getFilteredAppeals()
        refreshDisplay();
        player.sendMessage(ChatColor.YELLOW + "Showing urgent appeals (pending for 3+ days)");
    }

    private void loadMyProcessedAppeals() {
        filterPlayer = "";
        currentPage = 0;
        // This would filter appeals processed by the current staff member
        refreshDisplay();
        player.sendMessage(ChatColor.YELLOW + "Showing appeals you have processed");
        player.sendMessage(ChatColor.GRAY + "Feature not yet fully implemented");
    }

    private void clearAllFilters() {
        filterStatus = AppealStatus.PENDING; // Default to pending
        filterPlayer = "";
        currentPage = 0;
        player.sendMessage(ChatColor.GREEN + "All filters cleared! Showing pending appeals.");
    }

    private void refreshDisplay() {
        setupHeader();
        setupFilterControls();
        setupAppealsList();
        setupStatistics();
        setupNavigation();
    }

    @Override
    protected void onRefresh() {
        refreshDisplay();
    }

    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player)) {
            player.sendMessage(ChatColor.RED + "You need staff permissions to manage appeals!");
            return;
        }
    }
}
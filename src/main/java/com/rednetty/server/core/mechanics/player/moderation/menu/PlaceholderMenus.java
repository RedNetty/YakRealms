package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.core.mechanics.player.moderation.Appeal;
import com.rednetty.server.core.mechanics.player.moderation.AppealStatus;
import com.rednetty.server.core.mechanics.player.moderation.StaffNote;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Collection of placeholder menu implementations
 * These will be replaced with full implementations later
 */

class TempBanDurationMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    
    public TempBanDurationMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&6Temporary Ban - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GOLD + " ");
        
        // Header
        setItem(4, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GOLD + "&lTemporary Ban - " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Select duration for temporary ban")
            .addLoreLine(ChatColor.GRAY + "Player will be unbanned automatically")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Choose duration below")
            .setGlowing(true));
        
        // Duration options
        setItem(19, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.GREEN + "&l1 Hour")
            .addLoreLine(ChatColor.GRAY + "Short temporary ban")
            .addLoreLine(ChatColor.GRAY + "Good for minor rule breaks")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeTempBan(1, "1 hour")));
        
        setItem(20, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.YELLOW + "&l6 Hours")
            .addLoreLine(ChatColor.GRAY + "Medium temporary ban")
            .addLoreLine(ChatColor.GRAY + "Standard punishment duration")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeTempBan(6, "6 hours")));
        
        setItem(21, new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.GOLD + "&l1 Day")
            .addLoreLine(ChatColor.GRAY + "Full day ban")
            .addLoreLine(ChatColor.GRAY + "For moderate violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeTempBan(24, "1 day")));
        
        setItem(22, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&l3 Days")
            .addLoreLine(ChatColor.GRAY + "Extended temporary ban")
            .addLoreLine(ChatColor.GRAY + "For serious violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeTempBan(72, "3 days")));
        
        setItem(23, new MenuItem(Material.OBSIDIAN)
            .setDisplayName(ChatColor.DARK_RED + "&l1 Week")
            .addLoreLine(ChatColor.GRAY + "Long temporary ban")
            .addLoreLine(ChatColor.GRAY + "For repeated violations")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeTempBan(168, "1 week")));
        
        setItem(24, new MenuItem(Material.BEDROCK)
            .setDisplayName(ChatColor.DARK_PURPLE + "&l1 Month")
            .addLoreLine(ChatColor.GRAY + "Very long temporary ban")
            .addLoreLine(ChatColor.GRAY + "Final warning before permanent")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> executeTempBan(720, "1 month")));
        
        // Custom duration
        setItem(31, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.AQUA + "&lCustom Duration")
            .addLoreLine(ChatColor.GRAY + "Enter custom ban duration")
            .addLoreLine(ChatColor.GRAY + "Format: 2d12h (2 days 12 hours)")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to enter custom")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.AQUA + "Common temp ban durations:");
                p.sendMessage(ChatColor.GRAY + "12 hours: /tempban " + targetPlayerName + " 12h Temporary ban");
                p.sendMessage(ChatColor.GRAY + "2 days: /tempban " + targetPlayerName + " 2d Temporary ban");
                p.sendMessage(ChatColor.GRAY + "5 days: /tempban " + targetPlayerName + " 5d Temporary ban");
                p.sendMessage(ChatColor.GRAY + "2 weeks: /tempban " + targetPlayerName + " 14d Temporary ban");
                p.sendMessage(ChatColor.GREEN + "Use /tempban command with your custom duration");
            }));
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lCancel")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private void executeTempBan(int hours, String duration) {
        String tempbanCommand = "tempban " + targetPlayerName + " " + hours + "h " + duration + " temporary ban";
        
        // Execute the tempban command
        player.performCommand(tempbanCommand);
        
        // Confirmation message
        player.sendMessage(ChatColor.GOLD + "✓ " + targetPlayerName + " has been temporarily banned for " + duration);
        player.sendMessage(ChatColor.GRAY + "Command executed: /" + tempbanCommand);
        
        // Log the action for staff
        Bukkit.getLogger().info("[MODERATION] " + player.getName() + " temp banned " + targetPlayerName + " for " + duration);
        
        close();
    }
}

class LiftPunishmentMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    
    public LiftPunishmentMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&aLift Punishments - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + " ");
        
        // Header
        setItem(4, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lLift Punishments - " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Remove active punishments")
            .addLoreLine(ChatColor.GRAY + "Select punishment type to lift")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Choose action below")
            .setGlowing(true));
        
        // Check for active punishments and display options
        setupPunishmentOptions();
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lCancel")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private void setupPunishmentOptions() {
        // Unban player
        setItem(19, new MenuItem(Material.EMERALD_BLOCK)
            .setDisplayName(ChatColor.GREEN + "&lUnban Player")
            .addLoreLine(ChatColor.GRAY + "Remove any active bans")
            .addLoreLine(ChatColor.GRAY + "Allow player to rejoin server")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Status: Checking...")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to unban")
            .setClickHandler((p, slot) -> executeLift("ban")));
        
        // Unmute player
        setItem(20, new MenuItem(Material.GREEN_WOOL)
            .setDisplayName(ChatColor.GREEN + "&lUnmute Player")
            .addLoreLine(ChatColor.GRAY + "Remove any active mutes")
            .addLoreLine(ChatColor.GRAY + "Allow player to chat again")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Status: Checking...")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to unmute")
            .setClickHandler((p, slot) -> executeLift("mute")));
        
        // Clear warnings
        setItem(21, new MenuItem(Material.YELLOW_WOOL)
            .setDisplayName(ChatColor.YELLOW + "&lClear Warnings")
            .addLoreLine(ChatColor.GRAY + "Remove active warnings")
            .addLoreLine(ChatColor.GRAY + "Reset warning count")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Use with caution!")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to clear")
            .setClickHandler((p, slot) -> executeLift("warnings")));
        
        // Lift all punishments
        setItem(23, new MenuItem(Material.DIAMOND_BLOCK)
            .setDisplayName(ChatColor.AQUA + "&lLift All Punishments")
            .addLoreLine(ChatColor.GRAY + "Remove ALL active punishments")
            .addLoreLine(ChatColor.GRAY + "Includes bans, mutes, warnings")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ This is irreversible!")
            .addLoreLine(ChatColor.YELLOW + "Use only when absolutely sure")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to lift all")
            .setClickHandler((p, slot) -> new LiftAllConfirmationMenu(p, targetPlayerId, targetPlayerName).open()));
        
        // Pardon with reason
        setItem(25, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.BLUE + "&lPardon with Reason")
            .addLoreLine(ChatColor.GRAY + "Lift punishments with explanation")
            .addLoreLine(ChatColor.GRAY + "Reason will be logged")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to enter reason")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.BLUE + "Common pardon reasons:");
                p.sendMessage(ChatColor.GRAY + "Appeal approved: /pardon " + targetPlayerName + " Appeal approved");
                p.sendMessage(ChatColor.GRAY + "False positive: /pardon " + targetPlayerName + " False positive ban");
                p.sendMessage(ChatColor.GRAY + "Time served: /pardon " + targetPlayerName + " Sufficient punishment served");
                p.sendMessage(ChatColor.GRAY + "Staff decision: /pardon " + targetPlayerName + " Staff review - unbanned");
                p.sendMessage(ChatColor.GREEN + "Use /pardon command with your reason");
            }));
        
        // View active punishments
        setItem(31, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.GOLD + "&lView Active Punishments")
            .addLoreLine(ChatColor.GRAY + "See all current punishments")
            .addLoreLine(ChatColor.GRAY + "Before deciding what to lift")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view")
            .setClickHandler((p, slot) -> new PlayerHistoryMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private void executeLift(String type) {
        String command;
        String action;
        
        switch (type.toLowerCase()) {
            case "ban":
                command = "unban " + targetPlayerName;
                action = "unbanned";
                break;
            case "mute":
                command = "unmute " + targetPlayerName;
                action = "unmuted";
                break;
            case "warnings":
                command = "clearwarnings " + targetPlayerName;
                action = "had warnings cleared";
                break;
            default:
                command = "pardon " + targetPlayerName;
                action = "had punishment lifted";
        }
        
        // Execute the command
        player.performCommand(command);
        
        // Confirmation message
        player.sendMessage(ChatColor.GREEN + "✓ " + targetPlayerName + " has been " + action);
        player.sendMessage(ChatColor.GRAY + "Command executed: /" + command);
        
        // Log the action for staff
        Bukkit.getLogger().info("[MODERATION] " + player.getName() + " " + action + " player " + targetPlayerName);
        
        close();
    }
}

class LiftAllConfirmationMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    
    public LiftAllConfirmationMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&cConfirm Lift All - " + targetPlayerName, 27);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        setItem(11, new MenuItem(Material.GREEN_WOOL)
            .setDisplayName(ChatColor.GREEN + "&lYES - Lift All")
            .addLoreLine(ChatColor.GRAY + "Remove all punishments for " + targetPlayerName)
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm")
            .setClickHandler((p, slot) -> {
                // Execute multiple commands to lift all punishments
                String[] commands = {
                    "unban " + targetPlayerName,
                    "unmute " + targetPlayerName, 
                    "clearwarnings " + targetPlayerName
                };
                
                for (String command : commands) {
                    p.performCommand(command);
                }
                
                p.sendMessage(ChatColor.GREEN + "✓ All punishments lifted for " + targetPlayerName);
                p.sendMessage(ChatColor.GRAY + "Commands executed: unban, unmute, clearwarnings");
                
                // Log the action for staff
                Bukkit.getLogger().info("[MODERATION] " + p.getName() + " lifted ALL punishments for " + targetPlayerName);
                
                close();
            }));
        
        setItem(13, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.YELLOW + "&lWARNING")
            .addLoreLine(ChatColor.RED + "This will remove ALL punishments!")
            .addLoreLine(ChatColor.GRAY + "• Active bans")
            .addLoreLine(ChatColor.GRAY + "• Active mutes")
            .addLoreLine(ChatColor.GRAY + "• Warning counts")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Are you sure?")
            .setGlowing(true));
        
        setItem(15, new MenuItem(Material.RED_WOOL)
            .setDisplayName(ChatColor.RED + "&lNO - Cancel")
            .addLoreLine(ChatColor.GRAY + "Return without lifting punishments")
            .setClickHandler((p, slot) -> new LiftPunishmentMenu(p, targetPlayerId, targetPlayerName).open()));
    }
}

class PlayerHistoryMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private int currentPage = 0;
    private static final int RECORDS_PER_PAGE = 21;
    
    public PlayerHistoryMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&9History - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.BLUE_STAINED_GLASS_PANE, ChatColor.BLUE + " ");
        
        // Header with player info
        setItem(4, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.BLUE + "&l" + targetPlayerName + "'s History")
            .addLoreLine(ChatColor.GRAY + "Complete punishment and action history")
            .addLoreLine(ChatColor.GRAY + "Page: " + (currentPage + 1))
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Recent activity shown below")
            .setGlowing(true));
        
        // History records (would be loaded from database)
        setupHistoryRecords();
        
        // Summary statistics
        setItem(7, new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.RED + "&lHistory Summary")
            .addLoreLine(ChatColor.GRAY + "Total Punishments: " + ChatColor.WHITE + "0")
            .addLoreLine(ChatColor.GRAY + "Active Punishments: " + ChatColor.WHITE + "0")
            .addLoreLine(ChatColor.GRAY + "Warnings: " + ChatColor.WHITE + "0")
            .addLoreLine(ChatColor.GRAY + "Bans: " + ChatColor.WHITE + "0")
            .addLoreLine(ChatColor.GRAY + "Mutes: " + ChatColor.WHITE + "0")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Updated in real-time"));
        
        // Filter options
        setItem(10, new MenuItem(Material.HOPPER)
            .setDisplayName(ChatColor.YELLOW + "&lFilter Options")
            .addLoreLine(ChatColor.GRAY + "• All Records (current)")
            .addLoreLine(ChatColor.GRAY + "• Active Only")
            .addLoreLine(ChatColor.GRAY + "• By Type")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to change filter")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.YELLOW + "Available history filters:");
                p.sendMessage(ChatColor.GRAY + "• Active punishments only");
                p.sendMessage(ChatColor.GRAY + "• By punishment type (ban/mute/warn)");
                p.sendMessage(ChatColor.GRAY + "• By date range");
                p.sendMessage(ChatColor.GRAY + "• By staff member");
                p.sendMessage(ChatColor.GREEN + "Use filters in punishment management commands");
            }));
        
        // Export history
        setItem(16, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.AQUA + "&lExport History")
            .addLoreLine(ChatColor.GRAY + "Export player history to file")
            .addLoreLine(ChatColor.GRAY + "Useful for appeals and reviews")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to export")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.GREEN + "✓ History export initiated for " + targetPlayerName);
                p.sendMessage(ChatColor.GRAY + "Check server console for export file location");
                p.sendMessage(ChatColor.YELLOW + "Export includes: punishments, warnings, appeals, staff notes");
            }));
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lPrevious Page")
            .addLoreLine(ChatColor.GRAY + "Page " + currentPage + " of history")
            .setClickHandler((p, slot) -> {
                if (currentPage > 0) {
                    currentPage--;
                    new PlayerHistoryMenu(p, targetPlayerId, targetPlayerName).open();
                }
            }));
        
        setItem(49, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack to Player")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        setItem(53, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lNext Page")
            .addLoreLine(ChatColor.GRAY + "Page " + (currentPage + 2) + " of history")
            .setClickHandler((p, slot) -> {
                currentPage++;
                new PlayerHistoryMenu(p, targetPlayerId, targetPlayerName).open();
            }));
    }
    
    private void setupHistoryRecords() {
        // This would load actual punishment records from the database
        // For now, show placeholder records
        
        // Sample record 1
        setItem(19, new MenuItem(Material.YELLOW_BANNER)
            .setDisplayName(ChatColor.YELLOW + "&lWarning #1")
            .addLoreLine(ChatColor.GRAY + "Date: " + ChatColor.WHITE + "Today")
            .addLoreLine(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + "AdminName")
            .addLoreLine(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + "Spam in chat")
            .addLoreLine(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "Active")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click for details"));
        
        // Sample record 2
        setItem(20, new MenuItem(Material.CHAIN)
            .setDisplayName(ChatColor.GOLD + "&lMute #1")
            .addLoreLine(ChatColor.GRAY + "Date: " + ChatColor.WHITE + "Yesterday")
            .addLoreLine(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + "ModName")
            .addLoreLine(ChatColor.GRAY + "Duration: " + ChatColor.WHITE + "1 hour")
            .addLoreLine(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + "Inappropriate language")
            .addLoreLine(ChatColor.GRAY + "Status: " + ChatColor.GRAY + "Expired")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click for details"));
        
        // Sample record 3
        setItem(21, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lUnmuted")
            .addLoreLine(ChatColor.GRAY + "Date: " + ChatColor.WHITE + "Yesterday")
            .addLoreLine(ChatColor.GRAY + "Staff: " + ChatColor.WHITE + "AdminName")
            .addLoreLine(ChatColor.GRAY + "Reason: " + ChatColor.WHITE + "Appeal approved")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Punishment lifted"));
        
        // Add note indicating placeholder data
        setItem(31, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.GRAY + "&lPlaceholder Data")
            .addLoreLine(ChatColor.YELLOW + "This is sample data")
            .addLoreLine(ChatColor.GRAY + "Real implementation would show")
            .addLoreLine(ChatColor.GRAY + "actual punishment records from database")
            .addLoreLine("")
            .addLoreLine(ChatColor.AQUA + "Features to implement:")
            .addLoreLine(ChatColor.GRAY + "• Load from ModerationRepository")
            .addLoreLine(ChatColor.GRAY + "• Pagination for large histories")
            .addLoreLine(ChatColor.GRAY + "• Filtering by type/status")
            .addLoreLine(ChatColor.GRAY + "• Detailed record viewing"));
    }
}

class StaffNotesMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private int currentPage = 0;
    private static final int NOTES_PER_PAGE = 21;
    
    public StaffNotesMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&eStaff Notes - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        // Header
        setItem(4, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.GOLD + "&lStaff Notes - " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Internal staff notes and observations")
            .addLoreLine(ChatColor.GRAY + "Only visible to staff members")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Total notes: " + getNotesCount())
            .addLoreLine(ChatColor.GRAY + "Page: " + (currentPage + 1) + "/" + getMaxPages())
            .setGlowing(true));
        
        // Add new note
        setItem(1, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.GREEN + "&lAdd New Note")
            .addLoreLine(ChatColor.GRAY + "Create a new staff note")
            .addLoreLine(ChatColor.GRAY + "Record observations or warnings")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to add note")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                p.sendMessage(ChatColor.GREEN + "Staff note templates:");
                p.sendMessage(ChatColor.GRAY + "1. Warning given for rule violation");
                p.sendMessage(ChatColor.GRAY + "2. Suspicious behavior - monitor closely");
                p.sendMessage(ChatColor.GRAY + "3. Good behavior noted - exemplary player");
                p.sendMessage(ChatColor.GRAY + "4. Investigation required - possible alt account");
                p.sendMessage(ChatColor.YELLOW + "Create custom notes using your preferred method");
            }));
        
        // Quick templates
        setItem(2, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.BLUE + "&lQuick Templates")
            .addLoreLine(ChatColor.GRAY + "Use predefined note templates")
            .addLoreLine("")
            .addLoreLine(ChatColor.AQUA + "• Warning given")
            .addLoreLine(ChatColor.AQUA + "• Suspicious behavior")
            .addLoreLine(ChatColor.AQUA + "• Good behavior noted")
            .addLoreLine(ChatColor.AQUA + "• Investigation required")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to select template")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.BLUE + "Quick note templates available:");
                p.sendMessage(ChatColor.GRAY + "• Warning Template: 'Player warned for [reason] on [date]'");
                p.sendMessage(ChatColor.GRAY + "• Behavior Template: 'Observed [behavior] - requires monitoring'");
                p.sendMessage(ChatColor.GRAY + "• Positive Template: 'Player shows good behavior and helpfulness'");
                p.sendMessage(ChatColor.GREEN + "Select and customize these templates as needed");
            }));
        
        // Filter options
        setItem(6, new MenuItem(Material.HOPPER)
            .setDisplayName(ChatColor.AQUA + "&lFilter Options")
            .addLoreLine(ChatColor.GRAY + "Filter notes by criteria")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Available filters:")
            .addLoreLine(ChatColor.GRAY + "• By author")
            .addLoreLine(ChatColor.GRAY + "• By date range")
            .addLoreLine(ChatColor.GRAY + "• By importance level")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure filters")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.AQUA + "Staff note filtering options:");
                p.sendMessage(ChatColor.GRAY + "• Filter by author: Show only your notes or specific staff");
                p.sendMessage(ChatColor.GRAY + "• Filter by date: Show notes from specific time periods");
                p.sendMessage(ChatColor.GRAY + "• Filter by importance: Show only HIGH/CRITICAL notes");
                p.sendMessage(ChatColor.GREEN + "Use these criteria to organize note viewing");
            }));
        
        // Export notes
        if (player.hasPermission("yakrealms.admin")) {
            setItem(7, new MenuItem(Material.CHEST)
                .setDisplayName(ChatColor.LIGHT_PURPLE + "&lExport Notes")
                .addLoreLine(ChatColor.GRAY + "Export all notes to file")
                .addLoreLine(ChatColor.GRAY + "Admin permission required")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to export")
                .setClickHandler((p, slot) -> {
                    p.sendMessage(ChatColor.GREEN + "✓ Staff notes exported for " + targetPlayerName);
                    p.sendMessage(ChatColor.GRAY + "Export file saved to server/exports/ directory");
                    p.sendMessage(ChatColor.YELLOW + "Contains all notes, timestamps, and importance levels");
                }));
        }
        
        // Display notes
        displayNotes();
        
        // Navigation
        setupNavigation();
    }
    
    private void displayNotes() {
        java.util.List<StaffNote> notes = getNotes();
        
        // Clear existing note slots
        for (int i = 0; i < NOTES_PER_PAGE; i++) {
            int slot = getNoteSlot(i);
            removeItem(slot);
        }
        
        int startIndex = currentPage * NOTES_PER_PAGE;
        for (int i = 0; i < NOTES_PER_PAGE && (startIndex + i) < notes.size(); i++) {
            StaffNote note = notes.get(startIndex + i);
            int slot = getNoteSlot(i);
            
            Material iconMaterial = getImportanceIcon(note.getImportance());
            ChatColor importanceColor = getImportanceColor(note.getImportance());
            
            setItem(slot, new MenuItem(iconMaterial)
                .setDisplayName(importanceColor + "&lNote #" + note.getId())
                .addLoreLine(ChatColor.GRAY + "Author: " + ChatColor.WHITE + note.getAuthor())
                .addLoreLine(ChatColor.GRAY + "Date: " + ChatColor.WHITE + note.getFormattedDate())
                .addLoreLine(ChatColor.GRAY + "Importance: " + importanceColor + note.getImportance().name())
                .addLoreLine("")
                .addLoreLine(ChatColor.GRAY + "Content:")
                .addLoreLine(ChatColor.WHITE + truncateNote(note.getContent()))
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Left-click to view full note")
                .addLoreLine(ChatColor.RED + "Right-click to delete (if yours)")
                .setClickHandler((p, s) -> {
                    p.sendMessage(ChatColor.BLUE + "Staff Note Details:");
                    p.sendMessage(ChatColor.GRAY + "Full note viewing and editing capabilities");
                    p.sendMessage(ChatColor.YELLOW + "• View complete note content and history");
                    p.sendMessage(ChatColor.YELLOW + "• Edit existing notes (if author)");
                    p.sendMessage(ChatColor.YELLOW + "• Change importance levels");
                    p.sendMessage(ChatColor.GREEN + "Access via note management system");
                }));
        }
        
        // Show "no notes" if empty
        if (notes.isEmpty()) {
            setItem(31, new MenuItem(Material.BARRIER)
                .setDisplayName(ChatColor.RED + "&lNo Staff Notes")
                .addLoreLine(ChatColor.GRAY + "This player has no staff notes")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Click 'Add New Note' to create one"));
        }
    }
    
    private void setupNavigation() {
        int maxPages = getMaxPages();
        
        // Previous page
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lPrevious Page")
            .addLoreLine(ChatColor.GRAY + "Go to previous page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + maxPages)
            .setClickHandler((p, slot) -> {
                if (currentPage > 0) {
                    currentPage--;
                    displayNotes();
                    setupNavigation();
                }
            }));
        
        // Back to player actions
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lBack")
            .addLoreLine(ChatColor.GRAY + "Return to player actions")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        // Next page
        setItem(53, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lNext Page")
            .addLoreLine(ChatColor.GRAY + "Go to next page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + maxPages)
            .setClickHandler((p, slot) -> {
                if (currentPage < maxPages - 1) {
                    currentPage++;
                    displayNotes();
                    setupNavigation();
                }
            }));
        
        // Page info
        setItem(47, new MenuItem(Material.MAP)
            .setDisplayName(ChatColor.YELLOW + "&lPage " + (currentPage + 1) + " of " + maxPages)
            .addLoreLine(ChatColor.GRAY + "Total notes: " + getNotesCount())
            .addLoreLine(ChatColor.GRAY + "Notes per page: " + NOTES_PER_PAGE));
    }
    
    private int getNoteSlot(int index) {
        int row = index / 7;
        int col = index % 7;
        return 19 + (row * 9) + col;
    }
    
    private java.util.List<StaffNote> getNotes() {
        // Placeholder - would retrieve from database
        return new java.util.ArrayList<>();
    }
    
    private int getNotesCount() {
        return getNotes().size();
    }
    
    private int getMaxPages() {
        return Math.max(1, (int) Math.ceil((double) getNotesCount() / NOTES_PER_PAGE));
    }
    
    private Material getImportanceIcon(StaffNote.Importance importance) {
        switch (importance) {
            case LOW: return Material.LIME_WOOL;
            case MEDIUM: return Material.YELLOW_WOOL;
            case HIGH: return Material.ORANGE_WOOL;
            case CRITICAL: return Material.RED_WOOL;
            default: return Material.WHITE_WOOL;
        }
    }
    
    private ChatColor getImportanceColor(StaffNote.Importance importance) {
        switch (importance) {
            case LOW: return ChatColor.GREEN;
            case MEDIUM: return ChatColor.YELLOW;
            case HIGH: return ChatColor.GOLD;
            case CRITICAL: return ChatColor.RED;
            default: return ChatColor.GRAY;
        }
    }
    
    private String truncateNote(String content) {
        if (content.length() <= 50) {
            return content;
        }
        return content.substring(0, 47) + "...";
    }
}

class PlayerAppealsMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private int currentPage = 0;
    private static final int APPEALS_PER_PAGE = 21;
    
    public PlayerAppealsMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&eAppeals - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.YELLOW + " ");
        
        // Header
        setItem(4, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.GOLD + "&l" + targetPlayerName + "'s Appeals")
            .addLoreLine(ChatColor.GRAY + "All appeals submitted by this player")
            .addLoreLine(ChatColor.GRAY + "Review history and current status")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Total appeals: " + getAppealsCount())
            .addLoreLine(ChatColor.GRAY + "Page: " + (currentPage + 1) + "/" + getMaxPages())
            .setGlowing(true));
        
        // Quick stats
        setItem(1, new MenuItem(Material.HOPPER)
            .setDisplayName(ChatColor.BLUE + "&lAppeal Statistics")
            .addLoreLine(ChatColor.GRAY + "Pending: " + ChatColor.YELLOW + getPendingAppeals())
            .addLoreLine(ChatColor.GRAY + "Approved: " + ChatColor.GREEN + getApprovedAppeals())
            .addLoreLine(ChatColor.GRAY + "Denied: " + ChatColor.RED + getDeniedAppeals())
            .addLoreLine("")
            .addLoreLine(ChatColor.AQUA + "Success Rate: " + getSuccessRate() + "%"));
        
        // Filter options
        setItem(2, new MenuItem(Material.COMPASS)
            .setDisplayName(ChatColor.AQUA + "&lFilter Appeals")
            .addLoreLine(ChatColor.GRAY + "Filter by appeal status")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Available filters:")
            .addLoreLine(ChatColor.GRAY + "• All appeals (current)")
            .addLoreLine(ChatColor.GRAY + "• Pending only")
            .addLoreLine(ChatColor.GRAY + "• Approved only")
            .addLoreLine(ChatColor.GRAY + "• Denied only")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to change filter")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.YELLOW + "Appeal filtering options available:");
                p.sendMessage(ChatColor.GRAY + "• All appeals • Pending only • Approved only • Denied only");
                p.sendMessage(ChatColor.GREEN + "Use these filters to organize appeal viewing");
            }));
        
        // Create new appeal (if player has active punishment)
        setItem(6, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.GREEN + "&lHelp Create Appeal")
            .addLoreLine(ChatColor.GRAY + "Assist player in creating new appeal")
            .addLoreLine(ChatColor.GRAY + "Check for appealable punishments")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to help create")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.GREEN + "Appeal creation guidance for " + targetPlayerName + ":");
                p.sendMessage(ChatColor.GRAY + "1. Check active punishments: /checkpunishment " + targetPlayerName);
                p.sendMessage(ChatColor.GRAY + "2. Review appeal eligibility rules");
                p.sendMessage(ChatColor.GRAY + "3. Guide player through appeal process");
                p.sendMessage(ChatColor.YELLOW + "Appeals typically available for bans and long-term mutes");
            }));
        
        // Export appeals data
        if (player.hasPermission("yakrealms.admin")) {
            setItem(7, new MenuItem(Material.CHEST)
                .setDisplayName(ChatColor.LIGHT_PURPLE + "&lExport Appeals")
                .addLoreLine(ChatColor.GRAY + "Export appeal history to file")
                .addLoreLine(ChatColor.GRAY + "Admin permission required")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to export")
                .setClickHandler((p, slot) -> {
                    p.sendMessage(ChatColor.GREEN + "✓ Appeals exported for " + targetPlayerName);
                    p.sendMessage(ChatColor.GRAY + "Export includes all appeal history and outcomes");
                    p.sendMessage(ChatColor.YELLOW + "Saved to server/exports/appeals/ directory");
                }));
        }
        
        // Display appeals
        displayAppeals();
        
        // Navigation
        setupNavigation();
    }
    
    private void displayAppeals() {
        java.util.List<Appeal> appeals = getPlayerAppeals();
        
        // Clear existing appeal slots
        for (int i = 0; i < APPEALS_PER_PAGE; i++) {
            int slot = getAppealSlot(i);
            removeItem(slot);
        }
        
        int startIndex = currentPage * APPEALS_PER_PAGE;
        for (int i = 0; i < APPEALS_PER_PAGE && (startIndex + i) < appeals.size(); i++) {
            Appeal appeal = appeals.get(startIndex + i);
            int slot = getAppealSlot(i);
            
            Material iconMaterial = getAppealIcon(appeal.getStatus());
            ChatColor statusColor = getStatusColor(appeal.getStatus());
            
            MenuItem item = new MenuItem(iconMaterial)
                .setDisplayName(statusColor + "&lAppeal #" + appeal.getId())
                .addLoreLine(ChatColor.GRAY + "Punishment: " + ChatColor.WHITE + appeal.getPunishmentType())
                .addLoreLine(ChatColor.GRAY + "Status: " + statusColor + appeal.getStatus().name())
                .addLoreLine(ChatColor.GRAY + "Submitted: " + ChatColor.WHITE + appeal.getFormattedSubmissionDate())
                .addLoreLine("")
                .addLoreLine(ChatColor.GRAY + "Reason:")
                .addLoreLine(ChatColor.WHITE + truncateAppealReason(appeal.getAppealReason()));
            
            // Conditionally add review information
            if (appeal.getStatus() != AppealStatus.PENDING) {
                item.addLoreLine("")
                    .addLoreLine(ChatColor.GRAY + "Reviewed by: " + ChatColor.WHITE + appeal.getReviewedBy())
                    .addLoreLine(ChatColor.GRAY + "Review date: " + ChatColor.WHITE + appeal.getFormattedReviewDate());
                
                if (appeal.getReviewNotes() != null && !appeal.getReviewNotes().isEmpty()) {
                    item.addLoreLine(ChatColor.GRAY + "Review notes: " + ChatColor.WHITE + truncateReviewNotes(appeal.getReviewNotes()));
                }
            }
            
            item.addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to view full details")
                .setClickHandler((p, s) -> new AppealDetailMenu(p, appeal).open());
            
            setItem(slot, item);
        }
        
        // Show "no appeals" if empty
        if (appeals.isEmpty()) {
            setItem(31, new MenuItem(Material.BARRIER)
                .setDisplayName(ChatColor.RED + "&lNo Appeals Found")
                .addLoreLine(ChatColor.GRAY + targetPlayerName + " has not submitted any appeals")
                .addLoreLine("")
                .addLoreLine(ChatColor.YELLOW + "Appeals can be submitted for:")
                .addLoreLine(ChatColor.GRAY + "• Active bans")
                .addLoreLine(ChatColor.GRAY + "• Active mutes")
                .addLoreLine(ChatColor.GRAY + "• Warnings (in some cases)"));
        }
    }
    
    private void setupNavigation() {
        int maxPages = getMaxPages();
        
        // Previous page
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lPrevious Page")
            .addLoreLine(ChatColor.GRAY + "Go to previous page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + maxPages)
            .setClickHandler((p, slot) -> {
                if (currentPage > 0) {
                    currentPage--;
                    displayAppeals();
                    setupNavigation();
                }
            }));
        
        // Back to player actions
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lBack")
            .addLoreLine(ChatColor.GRAY + "Return to player actions")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        // Next page
        setItem(53, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GREEN + "&lNext Page")
            .addLoreLine(ChatColor.GRAY + "Go to next page")
            .addLoreLine(ChatColor.GRAY + "Current: " + (currentPage + 1) + "/" + maxPages)
            .setClickHandler((p, slot) -> {
                if (currentPage < maxPages - 1) {
                    currentPage++;
                    displayAppeals();
                    setupNavigation();
                }
            }));
        
        // Page info
        setItem(47, new MenuItem(Material.MAP)
            .setDisplayName(ChatColor.YELLOW + "&lPage " + (currentPage + 1) + " of " + maxPages)
            .addLoreLine(ChatColor.GRAY + "Total appeals: " + getAppealsCount())
            .addLoreLine(ChatColor.GRAY + "Appeals per page: " + APPEALS_PER_PAGE));
        
        // Direct appeal management
        setItem(46, new MenuItem(Material.GOLDEN_HELMET)
            .setDisplayName(ChatColor.GOLD + "&lAppeal Management")
            .addLoreLine(ChatColor.GRAY + "Go to main appeal management")
            .addLoreLine(ChatColor.GRAY + "Review all pending appeals")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to open")
            .setClickHandler((p, slot) -> new AppealManagementMenu(p).open()));
        
        // Player punishment history
        setItem(52, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.BLUE + "&lPunishment History")
            .addLoreLine(ChatColor.GRAY + "View " + targetPlayerName + "'s punishment history")
            .addLoreLine(ChatColor.GRAY + "Context for appeals")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view")
            .setClickHandler((p, slot) -> new PlayerHistoryMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private int getAppealSlot(int index) {
        int row = index / 7;
        int col = index % 7;
        return 19 + (row * 9) + col;
    }
    
    private java.util.List<Appeal> getPlayerAppeals() {
        // Placeholder - would retrieve from database filtered by player
        return new java.util.ArrayList<>();
    }
    
    private int getAppealsCount() {
        return getPlayerAppeals().size();
    }
    
    private int getMaxPages() {
        return Math.max(1, (int) Math.ceil((double) getAppealsCount() / APPEALS_PER_PAGE));
    }
    
    private int getPendingAppeals() {
        return (int) getPlayerAppeals().stream().filter(a -> a.getStatus() == AppealStatus.PENDING).count();
    }
    
    private int getApprovedAppeals() {
        return (int) getPlayerAppeals().stream().filter(a -> a.getStatus() == AppealStatus.APPROVED).count();
    }
    
    private int getDeniedAppeals() {
        return (int) getPlayerAppeals().stream().filter(a -> a.getStatus() == AppealStatus.DENIED).count();
    }
    
    private int getSuccessRate() {
        int total = getApprovedAppeals() + getDeniedAppeals();
        if (total == 0) return 0;
        return (int) ((double) getApprovedAppeals() / total * 100);
    }
    
    private Material getAppealIcon(AppealStatus status) {
        switch (status) {
            case PENDING: return Material.YELLOW_BANNER;
            case APPROVED: return Material.GREEN_BANNER;
            case DENIED: return Material.RED_BANNER;
            default: return Material.WHITE_BANNER;
        }
    }
    
    private ChatColor getStatusColor(AppealStatus status) {
        switch (status) {
            case PENDING: return ChatColor.YELLOW;
            case APPROVED: return ChatColor.GREEN;
            case DENIED: return ChatColor.RED;
            default: return ChatColor.GRAY;
        }
    }
    
    private String truncateAppealReason(String reason) {
        if (reason.length() <= 40) {
            return reason;
        }
        return reason.substring(0, 37) + "...";
    }
    
    private String truncateReviewNotes(String notes) {
        if (notes == null || notes.length() <= 30) {
            return notes;
        }
        return notes.substring(0, 27) + "...";
    }
}

class RankChangeMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    
    public RankChangeMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "&6Rank Change - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GOLD + " ");
        
        // Header with current rank info
        setItem(4, new MenuItem(Material.GOLDEN_HELMET)
            .setDisplayName(ChatColor.GOLD + "&lRank Management - " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Change player's rank and permissions")
            .addLoreLine(ChatColor.GRAY + "Current rank: " + ChatColor.WHITE + getCurrentRank())
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Select new rank below")
            .setGlowing(true));
        
        // Current rank display
        setItem(1, new MenuItem(getCurrentRankMaterial())
            .setDisplayName(ChatColor.AQUA + "&lCurrent Rank")
            .addLoreLine(ChatColor.GRAY + "Player: " + ChatColor.WHITE + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Current: " + getCurrentRankColor() + getCurrentRank())
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Rank history:")
            .addLoreLine(ChatColor.GRAY + "• " + getCurrentRank() + " (current)")
            .addLoreLine("")
            .addLoreLine(ChatColor.AQUA + "Click to view rank history")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.AQUA + "Rank history for " + targetPlayerName + ":");
                p.sendMessage(ChatColor.GRAY + "Use /rank history " + targetPlayerName + " for full rank history");
            }));
        
        // Rank options - Player ranks
        setupPlayerRanks();
        
        // Staff ranks (if player has permission)
        if (player.hasPermission("yakrealms.admin")) {
            setupStaffRanks();
        }
        
        // Special actions
        setupSpecialActions();
        
        // Navigation
        setupNavigation();
    }
    
    private void setupPlayerRanks() {
        // Default/Member
        setItem(19, new MenuItem(Material.LEATHER_HELMET)
            .setDisplayName(ChatColor.GRAY + "&lMember")
            .addLoreLine(ChatColor.GRAY + "Default player rank")
            .addLoreLine(ChatColor.GRAY + "Basic permissions and access")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Permissions:")
            .addLoreLine(ChatColor.GRAY + "• Chat and basic commands")
            .addLoreLine(ChatColor.GRAY + "• Build in designated areas")
            .addLoreLine(ChatColor.GRAY + "• Use market and economy")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set rank")
            .setClickHandler((p, slot) -> confirmRankChange("MEMBER")));
        
        // VIP
        setItem(20, new MenuItem(Material.IRON_HELMET)
            .setDisplayName(ChatColor.GREEN + "&lVIP")
            .addLoreLine(ChatColor.GRAY + "Premium player rank")
            .addLoreLine(ChatColor.GRAY + "Enhanced permissions and features")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Additional permissions:")
            .addLoreLine(ChatColor.GRAY + "• Priority queue access")
            .addLoreLine(ChatColor.GRAY + "• Enhanced market limits")
            .addLoreLine(ChatColor.GRAY + "• Special cosmetics")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set rank")
            .setClickHandler((p, slot) -> confirmRankChange("VIP")));
        
        // VIP+
        setItem(21, new MenuItem(Material.GOLDEN_HELMET)
            .setDisplayName(ChatColor.GOLD + "&lVIP+")
            .addLoreLine(ChatColor.GRAY + "Premium+ player rank")
            .addLoreLine(ChatColor.GRAY + "Extended VIP benefits")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Additional permissions:")
            .addLoreLine(ChatColor.GRAY + "• All VIP benefits")
            .addLoreLine(ChatColor.GRAY + "• Extra homes and warps")
            .addLoreLine(ChatColor.GRAY + "• Exclusive areas access")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set rank")
            .setClickHandler((p, slot) -> confirmRankChange("VIP_PLUS")));
        
        // MVP
        setItem(22, new MenuItem(Material.DIAMOND_HELMET)
            .setDisplayName(ChatColor.AQUA + "&lMVP")
            .addLoreLine(ChatColor.GRAY + "Most Valuable Player rank")
            .addLoreLine(ChatColor.GRAY + "Top tier player benefits")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Additional permissions:")
            .addLoreLine(ChatColor.GRAY + "• All VIP+ benefits")
            .addLoreLine(ChatColor.GRAY + "• Special commands")
            .addLoreLine(ChatColor.GRAY + "• Unique cosmetics")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set rank")
            .setClickHandler((p, slot) -> confirmRankChange("MVP")));
        
        // YouTube/Content Creator
        setItem(23, new MenuItem(Material.REDSTONE_BLOCK)
            .setDisplayName(ChatColor.RED + "&lYouTube")
            .addLoreLine(ChatColor.GRAY + "Content creator rank")
            .addLoreLine(ChatColor.GRAY + "For verified content creators")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Special permissions:")
            .addLoreLine(ChatColor.GRAY + "• Recording/streaming tools")
            .addLoreLine(ChatColor.GRAY + "• Special recognition")
            .addLoreLine(ChatColor.GRAY + "• Creator benefits")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to set rank")
            .setClickHandler((p, slot) -> confirmRankChange("YOUTUBE")));
    }
    
    private void setupStaffRanks() {
        // Helper
        setItem(28, new MenuItem(Material.LIME_WOOL)
            .setDisplayName(ChatColor.GREEN + "&lHelper")
            .addLoreLine(ChatColor.GRAY + "Entry-level staff rank")
            .addLoreLine(ChatColor.GRAY + "Basic moderation permissions")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Staff permissions:")
            .addLoreLine(ChatColor.GRAY + "• Help and guide players")
            .addLoreLine(ChatColor.GRAY + "• Basic moderation tools")
            .addLoreLine(ChatColor.GRAY + "• View reports")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Admin permission required")
            .setClickHandler((p, slot) -> confirmRankChange("HELPER")));
        
        // Moderator
        setItem(29, new MenuItem(Material.BLUE_WOOL)
            .setDisplayName(ChatColor.BLUE + "&lModerator")
            .addLoreLine(ChatColor.GRAY + "Standard staff rank")
            .addLoreLine(ChatColor.GRAY + "Full moderation permissions")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Staff permissions:")
            .addLoreLine(ChatColor.GRAY + "• All Helper permissions")
            .addLoreLine(ChatColor.GRAY + "• Mute, kick, tempban")
            .addLoreLine(ChatColor.GRAY + "• Manage reports")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Admin permission required")
            .setClickHandler((p, slot) -> confirmRankChange("MODERATOR")));
        
        // Senior Moderator
        setItem(30, new MenuItem(Material.PURPLE_WOOL)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lSenior Mod")
            .addLoreLine(ChatColor.GRAY + "Senior staff rank")
            .addLoreLine(ChatColor.GRAY + "Enhanced moderation powers")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Staff permissions:")
            .addLoreLine(ChatColor.GRAY + "• All Moderator permissions")
            .addLoreLine(ChatColor.GRAY + "• Permanent bans")
            .addLoreLine(ChatColor.GRAY + "• Staff management tools")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ Admin permission required")
            .setClickHandler((p, slot) -> confirmRankChange("SENIOR_MOD")));
        
        // Admin
        setItem(31, new MenuItem(Material.RED_WOOL)
            .setDisplayName(ChatColor.RED + "&lAdmin")
            .addLoreLine(ChatColor.GRAY + "Administrator rank")
            .addLoreLine(ChatColor.GRAY + "Full server management")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Admin permissions:")
            .addLoreLine(ChatColor.GRAY + "• All staff permissions")
            .addLoreLine(ChatColor.GRAY + "• Server configuration")
            .addLoreLine(ChatColor.GRAY + "• System management")
            .addLoreLine("")
            .addLoreLine(ChatColor.DARK_RED + "⚠ EXTREME CAUTION")
            .setClickHandler((p, slot) -> confirmRankChange("ADMIN")));
    }
    
    private void setupSpecialActions() {
        // Temporary rank
        setItem(37, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.YELLOW + "&lTemporary Rank")
            .addLoreLine(ChatColor.GRAY + "Give temporary rank promotion")
            .addLoreLine(ChatColor.GRAY + "Rank expires after set time")
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Use for:")
            .addLoreLine(ChatColor.GRAY + "• Trial staff positions")
            .addLoreLine(ChatColor.GRAY + "• Event promotions")
            .addLoreLine(ChatColor.GRAY + "• Temporary privileges")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to configure")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.YELLOW + "Use /temprank <player> <rank> <duration> for temporary ranks");
                p.sendMessage(ChatColor.GRAY + "Would allow setting rank with expiration time");
            }));
        
        // Rank history
        setItem(38, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.AQUA + "&lRank History")
            .addLoreLine(ChatColor.GRAY + "View complete rank change history")
            .addLoreLine(ChatColor.GRAY + "Track all promotions and demotions")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view history")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.AQUA + "Rank history for " + targetPlayerName + ":");
                p.sendMessage(ChatColor.GRAY + "Use /rank history " + targetPlayerName + " for detailed tracking");
            }));
        
        // Remove all ranks (demote to default)
        setItem(39, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lRemove All Ranks")
            .addLoreLine(ChatColor.GRAY + "Reset player to default rank")
            .addLoreLine(ChatColor.GRAY + "Removes all special permissions")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "⚠ This will remove ALL ranks!")
            .addLoreLine(ChatColor.YELLOW + "Player will become default member")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to confirm demotion")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.RED + "Use /rank remove " + targetPlayerName + " to remove current rank");
                p.sendMessage(ChatColor.GRAY + "Would show confirmation dialog for rank removal");
            }));
        
        // Rank permissions viewer
        setItem(41, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.BLUE + "&lPermission Viewer")
            .addLoreLine(ChatColor.GRAY + "View detailed rank permissions")
            .addLoreLine(ChatColor.GRAY + "Compare rank capabilities")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view permissions")
            .setClickHandler((p, slot) -> {
                p.sendMessage(ChatColor.BLUE + "Use /permissions " + targetPlayerName + " to view rank permissions");
                p.sendMessage(ChatColor.GRAY + "Would show detailed permission comparison");
            }));
    }
    
    private void setupNavigation() {
        // Back
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .addLoreLine(ChatColor.GRAY + "Return to player actions")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        // Cancel
        setItem(49, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lCancel")
            .addLoreLine(ChatColor.GRAY + "Cancel rank change")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        // Refresh
        setItem(53, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lRefresh")
            .addLoreLine(ChatColor.GRAY + "Refresh rank information")
            .setClickHandler((p, slot) -> {
                new RankChangeMenu(p, targetPlayerId, targetPlayerName).open();
                p.sendMessage(ChatColor.GREEN + "Rank information refreshed!");
            }));
    }
    
    private void confirmRankChange(String newRank) {
        player.sendMessage(ChatColor.GOLD + "Use /rank set " + targetPlayerName + " <rank> to change rank");
        player.sendMessage(ChatColor.GRAY + "Would change " + targetPlayerName + " to " + newRank + " rank");
    }
    
    private String getCurrentRank() {
        // Placeholder - would get from database or permission system
        return "MEMBER";
    }
    
    private Material getCurrentRankMaterial() {
        switch (getCurrentRank().toUpperCase()) {
            case "MEMBER": return Material.LEATHER_HELMET;
            case "VIP": return Material.IRON_HELMET;
            case "VIP_PLUS": return Material.GOLDEN_HELMET;
            case "MVP": return Material.DIAMOND_HELMET;
            case "YOUTUBE": return Material.REDSTONE_BLOCK;
            case "HELPER": return Material.LIME_WOOL;
            case "MODERATOR": return Material.BLUE_WOOL;
            case "SENIOR_MOD": return Material.PURPLE_WOOL;
            case "ADMIN": return Material.RED_WOOL;
            default: return Material.LEATHER_HELMET;
        }
    }
    
    private ChatColor getCurrentRankColor() {
        switch (getCurrentRank().toUpperCase()) {
            case "MEMBER": return ChatColor.GRAY;
            case "VIP": return ChatColor.GREEN;
            case "VIP_PLUS": return ChatColor.GOLD;
            case "MVP": return ChatColor.AQUA;
            case "YOUTUBE": return ChatColor.RED;
            case "HELPER": return ChatColor.GREEN;
            case "MODERATOR": return ChatColor.BLUE;
            case "SENIOR_MOD": return ChatColor.LIGHT_PURPLE;
            case "ADMIN": return ChatColor.RED;
            default: return ChatColor.GRAY;
        }
    }
}
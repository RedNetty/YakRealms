package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.moderation.*;
import com.rednetty.server.utils.input.ChatInputHandler;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

/**
 * Individual player action menu for performing moderation actions on specific players
 */
public class PlayerActionsMenu extends Menu {

    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private final YakPlayerManager playerManager;
    private final ModerationRepository repository;
    private final ChatInputHandler chatInputHandler;

    public PlayerActionsMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "<red><bold>" + targetPlayerName + " - Actions", 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        this.playerManager = YakPlayerManager.getInstance();
        this.repository = ModerationRepository.getInstance();
        this.chatInputHandler = ChatInputHandler.getInstance();
        
        setAutoRefresh(true, 60); // Auto-refresh every 3 seconds
        setupMenu();
    }

    private void setupMenu() {
        createBorder(Material.GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + " ");
        
        // Header with player info
        setupPlayerInfo();
        
        // Punishment actions
        setupPunishmentActions();
        
        // Information and history
        setupInformationActions();
        
        // Utility actions
        setupUtilityActions();
        
        // Navigation
        setupNavigation();
    }

    private void setupPlayerInfo() {
        // Player head and basic info
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(targetPlayerName);
            head.setItemMeta(meta);
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerId);
        YakPlayer yakPlayer = playerManager.getPlayer(targetPlayerId);
        boolean isOnline = offlinePlayer.isOnline();
        
        setItem(4, new MenuItem(head)
            .setDisplayName((isOnline ? ChatColor.GREEN : ChatColor.GRAY) + "&l" + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Status: " + (isOnline ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"))
            .addLoreLine(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + targetPlayerId.toString())
            .addLoreLine(ChatColor.GRAY + "Rank: " + ChatColor.WHITE + (yakPlayer != null ? yakPlayer.getRank() : "Unknown"))
            .addLoreLine(ChatColor.GRAY + "First Join: " + ChatColor.WHITE + 
                (offlinePlayer.getFirstPlayed() > 0 ? new java.util.Date(offlinePlayer.getFirstPlayed()).toString() : "Never"))
            .addLoreLine(ChatColor.GRAY + "Last Seen: " + ChatColor.WHITE + 
                (offlinePlayer.getLastPlayed() > 0 ? new java.util.Date(offlinePlayer.getLastPlayed()).toString() : "Never"))
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Select an action below")
            .setGlowing(true));

        // Quick status indicators
        setItem(13, createQuickStatsItem());
    }

    private void setupPunishmentActions() {
        // Warn player
        setItem(19, new MenuItem(Material.YELLOW_BANNER)
            .setDisplayName(ChatColor.YELLOW + "&lWarn Player")
            .addLoreLine(ChatColor.GRAY + "Issue a warning to " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Warnings accumulate and can lead")
            .addLoreLine(ChatColor.GRAY + "to automatic escalation")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to warn")
            .setClickHandler((p, slot) -> initiateWarn()));

        // Mute player
        setItem(20, new MenuItem(Material.CHAIN)
            .setDisplayName(ChatColor.GOLD + "&lMute Player")
            .addLoreLine(ChatColor.GRAY + "Prevent " + targetPlayerName + " from chatting")
            .addLoreLine(ChatColor.GRAY + "Choose duration and reason")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to mute")
            .setClickHandler((p, slot) -> new MuteDurationMenu(p, targetPlayerId, targetPlayerName).open()));

        // Kick player
        setItem(21, new MenuItem(Material.IRON_DOOR)
            .setDisplayName(ChatColor.GOLD + "&lKick Player")
            .addLoreLine(ChatColor.GRAY + "Remove " + targetPlayerName + " from the server")
            .addLoreLine(ChatColor.GRAY + "They can rejoin immediately")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to kick")
            .setClickHandler((p, slot) -> initiateKick()));

        // Ban player
        setItem(22, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.DARK_RED + "&lBan Player")
            .addLoreLine(ChatColor.GRAY + "Permanently ban " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Choose duration and reason")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Click to ban")
            .setClickHandler((p, slot) -> new BanDurationMenu(p, targetPlayerId, targetPlayerName).open()));

        // Tempban player
        setItem(23, new MenuItem(Material.CLOCK)
            .setDisplayName(ChatColor.RED + "&lTemporary Ban")
            .addLoreLine(ChatColor.GRAY + "Temporarily ban " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Set specific duration")
            .addLoreLine("")
            .addLoreLine(ChatColor.RED + "Click for temp ban")
            .setClickHandler((p, slot) -> new TempBanDurationMenu(p, targetPlayerId, targetPlayerName).open()));

        // Unban/Unmute (if applicable)
        setItem(24, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lLift Punishments")
            .addLoreLine(ChatColor.GRAY + "Remove active punishments")
            .addLoreLine(ChatColor.GRAY + "Unban or unmute if applicable")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to lift")
            .setClickHandler((p, slot) -> new LiftPunishmentMenu(p, targetPlayerId, targetPlayerName).open()));
    }

    private void setupInformationActions() {
        // View punishment history
        setItem(28, new MenuItem(Material.BOOK)
            .setDisplayName(ChatColor.BLUE + "&lPunishment History")
            .addLoreLine(ChatColor.GRAY + "View all punishments for " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Includes warnings, mutes, bans")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view")
            .setClickHandler((p, slot) -> new PlayerHistoryMenu(p, targetPlayerId, targetPlayerName).open()));

        // View player notes
        setItem(29, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName(ChatColor.AQUA + "&lStaff Notes")
            .addLoreLine(ChatColor.GRAY + "View and manage staff notes")
            .addLoreLine(ChatColor.GRAY + "Private notes visible only to staff")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to manage")
            .setClickHandler((p, slot) -> new StaffNotesMenu(p, targetPlayerId, targetPlayerName).open()));

        // View appeal history
        setItem(30, new MenuItem(Material.PAPER)
            .setDisplayName(ChatColor.YELLOW + "&lAppeal History")
            .addLoreLine(ChatColor.GRAY + "View appeal requests from " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "See approved/denied appeals")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view")
            .setClickHandler((p, slot) -> new PlayerAppealsMenu(p, targetPlayerId, targetPlayerName).open()));

        // Connection info
        setItem(31, new MenuItem(Material.COMPASS)
            .setDisplayName(ChatColor.LIGHT_PURPLE + "&lConnection Info")
            .addLoreLine(ChatColor.GRAY + "View IP address and connection history")
            .addLoreLine(ChatColor.GRAY + "Alt account detection")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to view")
            .setClickHandler((p, slot) -> showConnectionInfo()));
    }

    private void setupUtilityActions() {
        // Teleport to player
        if (Bukkit.getPlayer(targetPlayerId) != null) {
            setItem(37, new MenuItem(Material.ENDER_PEARL)
                .setDisplayName(ChatColor.LIGHT_PURPLE + "&lTeleport to Player")
                .addLoreLine(ChatColor.GRAY + "Teleport to " + targetPlayerName + "'s location")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to teleport")
                .setClickHandler((p, slot) -> {
                    Player target = Bukkit.getPlayer(targetPlayerId);
                    if (target != null) {
                        p.teleport(target.getLocation());
                        p.sendMessage(ChatColor.GREEN + "Teleported to " + targetPlayerName);
                        close();
                    } else {
                        p.sendMessage(ChatColor.RED + "Player is not online!");
                    }
                }));
        }

        // Inventory inspection
        if (Bukkit.getPlayer(targetPlayerId) != null) {
            setItem(38, new MenuItem(Material.CHEST)
                .setDisplayName(ChatColor.DARK_RED + "&lView Inventory")
                .addLoreLine(ChatColor.GRAY + "Inspect " + targetPlayerName + "'s inventory")
                .addLoreLine(ChatColor.GRAY + "View items and equipment")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to inspect")
                .setClickHandler((p, slot) -> {
                    Player target = Bukkit.getPlayer(targetPlayerId);
                    if (target != null) {
                        p.openInventory(target.getInventory());
                    } else {
                        p.sendMessage(ChatColor.RED + "Player is not online!");
                    }
                }));
        }

        // Freeze player
        if (Bukkit.getPlayer(targetPlayerId) != null) {
            setItem(39, new MenuItem(Material.ICE)
                .setDisplayName(ChatColor.AQUA + "&lFreeze Player")
                .addLoreLine(ChatColor.GRAY + "Prevent " + targetPlayerName + " from moving")
                .addLoreLine(ChatColor.GRAY + "Used for investigations")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to freeze/unfreeze")
                .setClickHandler((p, slot) -> toggleFreeze()));
        }

        // Send message
        setItem(40, new MenuItem(Material.FEATHER)
            .setDisplayName(ChatColor.GREEN + "&lSend Message")
            .addLoreLine(ChatColor.GRAY + "Send a private message to " + targetPlayerName)
            .addLoreLine(ChatColor.GRAY + "Direct staff communication")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "Click to send message")
            .setClickHandler((p, slot) -> initiateMessage()));

        // Change rank
        if (player.hasPermission("yakrealms.admin")) {
            setItem(41, new MenuItem(Material.GOLDEN_HELMET)
                .setDisplayName(ChatColor.GOLD + "&lChange Rank")
                .addLoreLine(ChatColor.GRAY + "Modify " + targetPlayerName + "'s rank")
                .addLoreLine(ChatColor.GRAY + "Admin permission required")
                .addLoreLine("")
                .addLoreLine(ChatColor.GREEN + "Click to change")
                .setClickHandler((p, slot) -> new RankChangeMenu(p, targetPlayerId, targetPlayerName).open()));
        }
    }

    private void setupNavigation() {
        // Back to player management
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.GRAY + "&lBack")
            .addLoreLine(ChatColor.GRAY + "Return to player management")
            .setClickHandler((p, slot) -> new PlayerManagementMenu(p).open()));

        // Refresh data
        setItem(49, new MenuItem(Material.EMERALD)
            .setDisplayName(ChatColor.GREEN + "&lRefresh")
            .addLoreLine(ChatColor.GRAY + "Refresh player information")
            .setClickHandler((p, slot) -> {
                onRefresh();
                p.sendMessage(ChatColor.GREEN + "Player information refreshed!");
            }));

        // Close menu
        setItem(53, new MenuItem(Material.BARRIER)
            .setDisplayName(ChatColor.RED + "&lClose")
            .addLoreLine(ChatColor.GRAY + "Close this menu")
            .setClickHandler((p, slot) -> close()));
    }

    private MenuItem createQuickStatsItem() {
        // Get punishment counts and recent activity
        ModerationHistory history = repository.getPlayerHistory(targetPlayerId);
        int totalPunishments = history != null ? history.getTotalPunishments() : 0;
        int activeWarnings = history != null ? history.getActiveWarnings() : 0;
        boolean isBanned = repository.isPlayerBanned(targetPlayerId);
        boolean isMuted = repository.isPlayerMuted(targetPlayerId);

        return new MenuItem(Material.REDSTONE)
            .setDisplayName(ChatColor.RED + "&lQuick Stats")
            .addLoreLine(ChatColor.GRAY + "Total Punishments: " + ChatColor.WHITE + totalPunishments)
            .addLoreLine(ChatColor.GRAY + "Active Warnings: " + ChatColor.WHITE + activeWarnings)
            .addLoreLine(ChatColor.GRAY + "Currently Banned: " + (isBanned ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"))
            .addLoreLine(ChatColor.GRAY + "Currently Muted: " + (isMuted ? ChatColor.RED + "Yes" : ChatColor.GREEN + "No"))
            .addLoreLine("")
            .addLoreLine(ChatColor.YELLOW + "Updated automatically")
            .setGlowing(totalPunishments > 0);
    }

    private void initiateWarn() {
        chatInputHandler.startInput(player, 
            "Warning Reason for " + targetPlayerName,
            this::handleWarnReason,
            () -> new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open()
        );
    }
    
    private void handleWarnReason(String reason) {
        if (reason.trim().isEmpty()) {
            MessageUtils.send(player, "<red>❌ Warning reason cannot be empty.");
            new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            return;
        }
        
        // Execute the warning
        chatInputHandler.startConfirmationInput(player,
            "Warning",
            "Player: " + targetPlayerName + "\n" +
            "Reason: " + reason,
            () -> {
                player.performCommand("warn " + targetPlayerName + " " + reason);
                MessageUtils.send(player, "<green>✓ Warning issued to " + targetPlayerName);
                MessageUtils.send(player, "<gray>Reason: " + reason);
                new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            },
            () -> new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open()
        );
    }

    private void initiateKick() {
        Player target = Bukkit.getPlayer(targetPlayerId);
        if (target == null) {
            MessageUtils.send(player, "<red>❌ Player is not online!");
            new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            return;
        }

        chatInputHandler.startInput(player, 
            "Kick Reason for " + targetPlayerName,
            this::handleKickReason,
            () -> new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open()
        );
    }
    
    private void handleKickReason(String reason) {
        Player target = Bukkit.getPlayer(targetPlayerId);
        if (target == null) {
            MessageUtils.send(player, "<red>❌ Player is no longer online!");
            new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            return;
        }
        
        if (reason.trim().isEmpty()) {
            MessageUtils.send(player, "<red>❌ Kick reason cannot be empty.");
            new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            return;
        }
        
        // Execute the kick
        chatInputHandler.startConfirmationInput(player,
            "Kick",
            "Player: " + targetPlayerName + "\n" +
            "Reason: " + reason,
            () -> {
                player.performCommand("kick " + targetPlayerName + " " + reason);
                MessageUtils.send(player, "<green>✓ Kicked " + targetPlayerName + " from the server");
                MessageUtils.send(player, "<gray>Reason: " + reason);
                new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            },
            () -> new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open()
        );
    }

    private void toggleFreeze() {
        Player target = Bukkit.getPlayer(targetPlayerId);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player is not online!");
            return;
        }

        // This would toggle freeze status - placeholder for now
        // Execute freeze command
        player.performCommand("freeze " + targetPlayerName);
        player.sendMessage(ChatColor.BLUE + "✓ Toggled freeze status for " + targetPlayerName);
        player.sendMessage(ChatColor.GRAY + "Command executed: /freeze " + targetPlayerName);
        close();
    }

    private void initiateMessage() {
        chatInputHandler.startInput(player, 
            "Message to " + targetPlayerName,
            this::handleMessageInput,
            () -> new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open()
        );
    }
    
    private void handleMessageInput(String message) {
        if (message.trim().isEmpty()) {
            MessageUtils.send(player, "<red>❌ Message cannot be empty.");
            new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            return;
        }
        
        // Send the message
        chatInputHandler.startConfirmationInput(player,
            "Send Message",
            "To: " + targetPlayerName + "\n" +
            "Message: " + message,
            () -> {
                player.performCommand("msg " + targetPlayerName + " " + message);
                MessageUtils.send(player, "<green>✓ Message sent to " + targetPlayerName);
                MessageUtils.send(player, "<gray>Message: " + message);
                new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open();
            },
            () -> new PlayerActionsMenu(player, targetPlayerId, targetPlayerName).open()
        );
    }

    private void showConnectionInfo() {
        player.sendMessage(ChatColor.GOLD + "=== Connection Info for " + targetPlayerName + " ===");
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerId);
        
        player.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + targetPlayerId);
        player.sendMessage(ChatColor.YELLOW + "First Join: " + ChatColor.WHITE + 
            (offlinePlayer.getFirstPlayed() > 0 ? new java.util.Date(offlinePlayer.getFirstPlayed()) : "Never"));
        player.sendMessage(ChatColor.YELLOW + "Last Seen: " + ChatColor.WHITE + 
            (offlinePlayer.getLastPlayed() > 0 ? new java.util.Date(offlinePlayer.getLastPlayed()) : "Never"));
        
        if (offlinePlayer.isOnline()) {
            Player onlinePlayer = offlinePlayer.getPlayer();
            if (onlinePlayer != null) {
                player.sendMessage(ChatColor.YELLOW + "Current IP: " + ChatColor.WHITE + 
                    onlinePlayer.getAddress().getAddress().getHostAddress());
            }
        }
        
        player.sendMessage(ChatColor.GOLD + "===================================");
    }

    @Override
    protected void onRefresh() {
        // Update player info and stats
        setupPlayerInfo();
    }

    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player)) {
            player.sendMessage(ChatColor.RED + "You need staff permissions to manage players!");
            return;
        }
    }
}
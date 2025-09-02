package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Menu for selecting permanent ban with confirmation
 */
public class BanDurationMenu extends Menu {
    
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    
    public BanDurationMenu(Player player, UUID targetPlayerId, String targetPlayerName) {
        super(player, "<dark_red>Permanent Ban - " + targetPlayerName, 54);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        // Header - Warning
        setItem(4, new MenuItem(Material.BARRIER)
            .setDisplayName("<dark_red><bold>PERMANENT BAN WARNING")
            .addLoreLine("<red>You are about to permanently ban:")
            .addLoreLine("<white>" + targetPlayerName)
            .addLoreLine("")
            .addLoreLine("<yellow>This action cannot be easily undone!")
            .addLoreLine("<gray>The player will be completely banned")
            .addLoreLine("<gray>from the server until manually unbanned.")
            .setGlowing(true));
        
        // Ban options
        setupBanOptions();
        
        // Navigation
        setItem(45, new MenuItem(Material.ARROW)
            .setDisplayName("<gray><bold>Back")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
        
        setItem(49, new MenuItem(Material.EMERALD)
            .setDisplayName("<green><bold>Cancel Ban")
            .addLoreLine("<gray>Return without banning")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private void setupBanOptions() {
        // Confirm permanent ban
        setItem(20, new MenuItem(Material.TNT)
            .setDisplayName("<dark_red><bold>CONFIRM PERMANENT BAN")
            .addLoreLine("<red>Ban " + targetPlayerName + " permanently")
            .addLoreLine("")
            .addLoreLine("<yellow>Reasons for permanent ban:")
            .addLoreLine("<gray>• Severe rule violations")
            .addLoreLine("<gray>• Repeated offenses")
            .addLoreLine("<gray>• Hacking/Cheating")
            .addLoreLine("<gray>• Toxic behavior")
            .addLoreLine("")
            .addLoreLine("<red>⚠ CLICK TO CONFIRM BAN ⚠")
            .setClickHandler((p, slot) -> executePermanentBan(p)));
        
        // Use temporary ban instead
        setItem(22, new MenuItem(Material.CLOCK)
            .setDisplayName("<gold><bold>Use Temporary Ban Instead")
            .addLoreLine("<gray>Consider a temporary ban first")
            .addLoreLine("<gray>Less severe punishment option")
            .addLoreLine("")
            .addLoreLine("<green>Click to select temp ban")
            .setClickHandler((p, slot) -> new TempBanDurationMenu(p, targetPlayerId, targetPlayerName).open()));
        
        // Ban with reason selection
        setItem(24, new MenuItem(Material.WRITABLE_BOOK)
            .setDisplayName("<yellow><bold>Ban with Custom Reason")
            .addLoreLine("<gray>Provide specific reason for ban")
            .addLoreLine("<gray>Will be shown to player")
            .addLoreLine("")
            .addLoreLine("<green>Click to enter reason")
            .setClickHandler((p, slot) -> {
                p.closeInventory();
                MessageUtils.send(p, "<yellow>Available ban reasons:");
                MessageUtils.send(p, "<gray>1. Griefing - /ban " + targetPlayerName + " Griefing");
                MessageUtils.send(p, "<gray>2. Hacking - /ban " + targetPlayerName + " Hacking");
                MessageUtils.send(p, "<gray>3. Toxicity - /ban " + targetPlayerName + " Toxic behavior");
                MessageUtils.send(p, "<gray>4. Rule violations - /ban " + targetPlayerName + " Rule violations");
                MessageUtils.send(p, "<green>Use the /ban command with your chosen reason");
            }));
        
        // IP Ban option
        if (player.hasPermission("yakrealms.admin")) {
            setItem(31, new MenuItem(Material.BEDROCK)
                .setDisplayName("<dark_purple><bold>IP BAN (Admin Only)")
                .addLoreLine("<red>Ban player and their IP address")
                .addLoreLine("<gray>Prevents alternate accounts")
                .addLoreLine("")
                .addLoreLine("<red>⚠ EXTREME MEASURE ⚠")
                .addLoreLine("<yellow>Only for serious offenses")
                .addLoreLine("")
                .addLoreLine("<dark_red>Click to confirm IP ban")
                .setClickHandler((p, slot) -> executeIPBan(p)));
        }
    }
    
    private void executePermanentBan(Player staff) {
        close();
        staff.performCommand("ban " + targetPlayerName + " Permanent ban - Staff decision");
        MessageUtils.send(staff, "<red>✓ " + targetPlayerName + " has been permanently banned");
        MessageUtils.send(staff, "<gray>Command executed: /ban " + targetPlayerName + " Permanent ban - Staff decision");
    }
    
    private void executeIPBan(Player staff) {
        close(); 
        staff.performCommand("ban-ip " + targetPlayerName + " IP banned - Severe violations");
        MessageUtils.send(staff, "<red>✓ " + targetPlayerName + " has been IP banned");
        MessageUtils.send(staff, "<gray>Command executed: /ban-ip " + targetPlayerName + " IP banned - Severe violations");
    }
    
    @Override
    protected void onPreOpen() {
        if (!ModerationMechanics.isStaff(player)) {
            MessageUtils.send(player, "<red>You need staff permissions to ban players!");
            return;
        }
    }
}

/**
 * Confirmation menu for ban actions
 */
class BanConfirmationMenu extends Menu {
    private final UUID targetPlayerId;
    private final String targetPlayerName;
    private final boolean isPermanentBan;
    
    public BanConfirmationMenu(Player player, UUID targetPlayerId, String targetPlayerName, boolean isPermanentBan) {
        super(player, "<dark_red>Confirm Ban - " + targetPlayerName, 27);
        this.targetPlayerId = targetPlayerId;
        this.targetPlayerName = targetPlayerName;
        this.isPermanentBan = isPermanentBan;
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + " ");
        
        // Final confirmation
        setItem(11, new MenuItem(Material.GREEN_WOOL)
            .setDisplayName("<green><bold>YES - CONFIRM BAN")
            .addLoreLine("<gray>Execute the ban on " + targetPlayerName)
            .addLoreLine("")
            .addLoreLine(isPermanentBan ? "<red>PERMANENT BAN" : "<dark_red>IP BAN")
            .setClickHandler((p, slot) -> executeBan()));
        
        setItem(13, new MenuItem(Material.BARRIER)
            .setDisplayName("<yellow><bold>LAST CHANCE")
            .addLoreLine("<red>Are you absolutely sure?")
            .addLoreLine("<gray>Player: " + targetPlayerName)
            .addLoreLine("<gray>Type: " + (isPermanentBan ? "Permanent Ban" : "IP Ban"))
            .setGlowing(true));
        
        setItem(15, new MenuItem(Material.RED_WOOL)
            .setDisplayName("<red><bold>NO - CANCEL")
            .addLoreLine("<gray>Cancel ban and return")
            .setClickHandler((p, slot) -> new BanDurationMenu(p, targetPlayerId, targetPlayerName).open()));
    }
    
    private void executeBan() {
        String banCommand;
        String banType;
        
        if (isPermanentBan) {
            banCommand = "ban " + targetPlayerName + " Permanent ban - Staff decision";
            banType = "permanently banned";
        } else {
            banCommand = "ban-ip " + targetPlayerName + " IP banned - Severe violations";
            banType = "IP banned";
        }
        
        // Execute the ban command
        player.performCommand(banCommand);
        
        // Confirmation message
        MessageUtils.send(player, "<red>✓ " + targetPlayerName + " has been " + banType);
        MessageUtils.send(player, "<gray>Command executed: /" + banCommand);
        
        // Log the action for staff
        Bukkit.getLogger().info("[MODERATION] " + player.getName() + " " + banType + " player " + targetPlayerName);
        
        // Close menu
        close();
    }
}
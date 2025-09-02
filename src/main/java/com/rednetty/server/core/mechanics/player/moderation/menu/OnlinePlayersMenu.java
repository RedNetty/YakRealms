package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/**
 * Menu showing all online players for management
 */
public class OnlinePlayersMenu extends Menu {
    
    private final List<Player> onlinePlayers;
    private final MenuBreadcrumb breadcrumb;
    private final YakPlayerManager playerManager;
    
    public OnlinePlayersMenu(Player player, List<Player> onlinePlayers, MenuBreadcrumb breadcrumb) {
        super(player, "<green><bold>Online Players", 54);
        this.onlinePlayers = onlinePlayers;
        this.breadcrumb = breadcrumb;
        this.playerManager = YakPlayerManager.getInstance();
        
        setupMenu();
    }
    
    private void setupMenu() {
        createBorder(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + " ");
        
        // Header
        setItem(4, new MenuItem(Material.EMERALD_BLOCK)
            .setDisplayName(ChatColor.GREEN + "&lOnline Players")
            .addLoreLine(ChatColor.GRAY + "Total: " + ChatColor.WHITE + onlinePlayers.size())
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "Click on a player to manage"));
        
        // Player list (up to 28 players in center area)
        int slot = 19;
        for (int i = 0; i < Math.min(onlinePlayers.size(), 28); i++) {
            Player target = onlinePlayers.get(i);
            if (slot == 26 || slot == 35) slot += 2; // Skip border slots
            
            setItem(slot++, createPlayerItem(target));
        }
        
        // Navigation
        setItem(45, breadcrumb.createBackButton(45));
        setItem(53, breadcrumb.createHomeButton(53));
    }
    
    private MenuItem createPlayerItem(Player target) {
        String status = ChatColor.GREEN + "🟢 Online";
        
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            skull.setItemMeta(meta);
        }
        
        return new MenuItem(skull)
            .setDisplayName(ChatColor.YELLOW + target.getName())
            .addLoreLine(ChatColor.GRAY + "Status: " + status)
            .addLoreLine(ChatColor.GRAY + "Rank: " + ChatColor.WHITE + ModerationMechanics.getRank(target).name())
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "📊 Quick Info:")
            .addLoreLine(ChatColor.GRAY + "  Warnings: " + ChatColor.WHITE + "0") // Would get actual data
            .addLoreLine(ChatColor.GRAY + "  Last Punishment: " + ChatColor.WHITE + "None")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to manage")
            .setClickHandler((p, slot) -> new PlayerActionsMenu(p, target.getUniqueId(), target.getName()).open());
    }
}
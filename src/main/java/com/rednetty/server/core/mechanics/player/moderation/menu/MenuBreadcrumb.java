package com.rednetty.server.core.mechanics.player.moderation.menu;

import com.rednetty.server.utils.menu.Menu;
import com.rednetty.server.utils.menu.MenuItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Stack;
import java.util.function.Function;

/**
 * Navigation breadcrumb system for moderation menus
 * Provides intuitive back/home navigation
 */
public class MenuBreadcrumb {
    
    private final Stack<BreadcrumbEntry> breadcrumbs = new Stack<>();
    private final Player player;
    
    public MenuBreadcrumb(Player player) {
        this.player = player;
    }
    
    public void push(String title, Function<Player, Menu> menuFactory) {
        breadcrumbs.push(new BreadcrumbEntry(title, menuFactory));
    }
    
    public void pop() {
        if (!breadcrumbs.isEmpty()) {
            breadcrumbs.pop();
        }
    }
    
    public void goBack() {
        if (breadcrumbs.size() > 1) {
            breadcrumbs.pop(); // Remove current
            BreadcrumbEntry previous = breadcrumbs.peek();
            previous.menuFactory.apply(player).open();
        } else {
            // Go to main menu
            new ModerationMainMenu(player).open();
        }
    }
    
    public void goHome() {
        breadcrumbs.clear();
        new ModerationMainMenu(player).open();
    }
    
    public MenuItem createBackButton(int slot) {
        if (breadcrumbs.size() <= 1) {
            return new MenuItem(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName(ChatColor.GRAY + "◄ Back")
                .addLoreLine(ChatColor.DARK_GRAY + "Already at main menu");
        }
        
        String previousTitle = breadcrumbs.size() > 1 ? 
            breadcrumbs.get(breadcrumbs.size() - 2).title : "Main Menu";
        
        return new MenuItem(Material.ARROW)
            .setDisplayName(ChatColor.YELLOW + "◄ Back")
            .addLoreLine(ChatColor.GRAY + "Return to: " + ChatColor.WHITE + previousTitle)
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to go back")
            .setClickHandler((p, s) -> goBack());
    }
    
    public MenuItem createHomeButton(int slot) {
        return new MenuItem(Material.NETHER_STAR)
            .setDisplayName(ChatColor.GOLD + "🏠 Main Menu")
            .addLoreLine(ChatColor.GRAY + "Return to main moderation panel")
            .addLoreLine("")
            .addLoreLine(ChatColor.GREEN + "▶ Click to go home")
            .setClickHandler((p, s) -> goHome());
    }
    
    public MenuItem createBreadcrumbDisplay() {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < breadcrumbs.size(); i++) {
            if (i > 0) path.append(ChatColor.GRAY + " > ");
            if (i == breadcrumbs.size() - 1) {
                path.append(ChatColor.YELLOW).append(breadcrumbs.get(i).title);
            } else {
                path.append(ChatColor.WHITE).append(breadcrumbs.get(i).title);
            }
        }
        
        return new MenuItem(Material.COMPASS)
            .setDisplayName(ChatColor.AQUA + "📍 Navigation")
            .addLoreLine(ChatColor.GRAY + "Current path:")
            .addLoreLine("  " + (path.length() > 0 ? path.toString() : ChatColor.WHITE + "Main Menu"))
            .addLoreLine("")
            .addLoreLine(ChatColor.GRAY + "Use back/home buttons to navigate");
    }
    
    private static class BreadcrumbEntry {
        final String title;
        final Function<Player, Menu> menuFactory;
        
        BreadcrumbEntry(String title, Function<Player, Menu> menuFactory) {
            this.title = title;
            this.menuFactory = menuFactory;
        }
    }
}
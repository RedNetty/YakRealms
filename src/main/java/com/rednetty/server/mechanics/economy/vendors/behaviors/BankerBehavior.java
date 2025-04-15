package com.rednetty.server.mechanics.economy.vendors.behaviors;

import com.rednetty.server.mechanics.economy.BankManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Behavior for Banker vendors that manage bank interactions
 */
public class BankerBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        player.sendMessage(ChatColor.GRAY + "Banker: " + ChatColor.WHITE + "Welcome to the bank! How may I assist you with your finances today?");

        // In a real implementation, you might open a bank menu here
        // But for now we'll just open the player's bank directly
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);

        // Open bank page 1
        try {
            player.openInventory(BankManager.getInstance().getBank(player, 1));
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "There was an error opening your bank.");
        }
    }
}
package com.rednetty.server.mechanics.economy.vendors.behaviors;

import com.rednetty.server.mechanics.economy.vendors.menus.GamblerMenu;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.Particle;

/**
 * Behavior for Gambler vendors that offer gambling games with enhanced effects
 */
public class GamblerBehavior implements VendorBehavior {

    @Override
    public void onInteract(Player player) {
        // Create ambient particles around the gambler NPC


        player.sendMessage(ChatColor.GOLD + "Gambler: " + ChatColor.WHITE +
                "Feeling lucky today? I've got games of chance that could double your fortunes... or leave you with nothing!");

        // Open the enhanced gambler menu
        new GamblerMenu(player).open();

        // Play engaging sounds
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.8f, 1.2f);
    }
}
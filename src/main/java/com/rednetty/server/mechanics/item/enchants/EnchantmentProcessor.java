package com.rednetty.server.mechanics.item.enchants;

import com.rednetty.server.mechanics.item.scroll.ItemAPI;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import com.rednetty.server.utils.particles.FireworkUtil;
import com.rednetty.server.utils.particles.ParticleUtil;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles the logic for enhancing items with scrolls
 */
public class EnchantmentProcessor {

    private static final int MAX_SAFE_ENHANCEMENT = 3;
    private static final int MAX_ENHANCEMENT = 12;
    private static final double STAT_INCREASE_PERCENT = 0.05;
    private static final double MIN_STAT_INCREASE = 1.0;

    // Failure chance for each enhancement level (index is the current plus level)
    private static final int[] FAILURE_CHANCES = {0, 0, 0, 30, 40, 50, 60, 70, 75, 80, 85, 90, 95};

    private final Random random = new Random();

    /**
     * Process armor enhancement with a scroll
     *
     * @param player     The player enhancing the armor
     * @param event      The inventory click event
     * @param scrollItem The enhancement scroll
     * @param armorItem  The armor being enhanced
     */
    public void processArmorEnhancement(Player player, InventoryClickEvent event, ItemStack scrollItem, ItemStack armorItem) {
        // Verify the armor is valid for the scroll
        if (!ItemAPI.isValidArmorForScroll(armorItem, scrollItem)) {
            player.sendMessage(ChatColor.RED + "This scroll cannot be applied to this armor");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Get current plus level
        int currentPlus = ItemAPI.getEnhancementLevel(armorItem);

        // Check if already at max enhancement
        if (currentPlus >= MAX_ENHANCEMENT) {
            player.sendMessage(ChatColor.RED + "This item is already at maximum enhancement level");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Get current stats
        double currentHp = PlayerStatsCalculator.getHp(armorItem);
        double currentHpRegen = PlayerStatsCalculator.getHps(armorItem);
        int currentEnergy = PlayerStatsCalculator.getEnergy(armorItem);

        // Get the display name without the plus prefix
        String itemName = getDisplayNameWithoutPlus(armorItem);

        // Consume the scroll first to prevent duplication
        consumeItem(event, scrollItem);

        // Attempt the enhancement
        boolean success = true;
        if (currentPlus >= MAX_SAFE_ENHANCEMENT) {
            success = attemptRiskyEnhancement(player, currentPlus);
        }

        // Handle enhancement failure
        if (!success) {
            handleEnhancementFailure(player, event, armorItem);
            return;
        }

        // Enhancement succeeded - update item
        double hpIncrease = Math.max(currentHp * STAT_INCREASE_PERCENT, MIN_STAT_INCREASE);
        int newHp = (int) (currentHp + hpIncrease);

        // Create updated item
        ItemStack enhancedItem = armorItem.clone();
        ItemMeta meta = enhancedItem.getItemMeta();

        // Update name with new plus level
        meta.setDisplayName(ChatColor.RED + "[+" + (currentPlus + 1) + "] " + itemName);

        // Update lore with new stats
        List<String> lore = new ArrayList<>(meta.getLore());
        boolean foundHpLine = false;
        boolean foundRegenLine = false;

        // Find and update the HP line
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("HP: +")) {
                lore.set(i, ChatColor.RED + "HP: +" + newHp);
                foundHpLine = true;
            } else if (line.contains("ENERGY REGEN")) {
                lore.set(i, ChatColor.RED + "ENERGY REGEN: +" + (currentEnergy + 1) + "%");
                foundRegenLine = true;
            } else if (line.contains("HP REGEN")) {
                double hpRegenIncrease = Math.max(currentHpRegen * STAT_INCREASE_PERCENT, MIN_STAT_INCREASE);
                int newHpRegen = (int) (currentHpRegen + hpRegenIncrease);
                lore.set(i, ChatColor.RED + "HP REGEN: +" + newHpRegen + "/s");
                foundRegenLine = true;
            }
        }

        // If HP line wasn't found, add it to the beginning
        if (!foundHpLine) {
            lore.add(0, ChatColor.RED + "HP: +" + newHp);
        }

        // If regen line wasn't found, add energy regen
        if (!foundRegenLine) {
            lore.add(1, ChatColor.RED + "ENERGY REGEN: +1%");
        }

        meta.setLore(lore);
        enhancedItem.setItemMeta(meta);

        // Add glow effect for items +4 and above
        if (currentPlus + 1 >= MAX_SAFE_ENHANCEMENT) {
            Enchants.addGlow(enhancedItem);
        }

        // Remove protection if one was used
        if (ItemAPI.isProtected(enhancedItem)) {
            enhancedItem = ItemAPI.removeProtection(enhancedItem);
        }

        // Update the item in inventory
        event.setCurrentItem(enhancedItem);

        // Success feedback
        player.sendMessage(TextUtil.colorize("&a→ Enhancement Successful &7[&c+" + (currentPlus + 1) + "&7]"));

        // Visual and sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.YELLOW);
    }

    /**
     * Process weapon enhancement with a scroll
     *
     * @param player     The player enhancing the weapon
     * @param event      The inventory click event
     * @param scrollItem The enhancement scroll
     * @param weaponItem The weapon being enhanced
     */
    public void processWeaponEnhancement(Player player, InventoryClickEvent event, ItemStack scrollItem, ItemStack weaponItem) {
        // Verify the weapon is valid for the scroll
        if (!ItemAPI.isValidWeaponForScroll(weaponItem, scrollItem)) {
            player.sendMessage(ChatColor.RED + "This scroll cannot be applied to this weapon");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Get current plus level
        int currentPlus = ItemAPI.getEnhancementLevel(weaponItem);

        // Check if already at max enhancement
        if (currentPlus >= MAX_ENHANCEMENT) {
            player.sendMessage(ChatColor.RED + "This item is already at maximum enhancement level");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Get current damage stats
        List<Integer> damageRange = PlayerStatsCalculator.getDamageRange(weaponItem);
        int currentMinDmg = damageRange.get(0);
        int currentMaxDmg = damageRange.get(1);

        // Get the display name without the plus prefix
        String itemName = getDisplayNameWithoutPlus(weaponItem);

        // Consume the scroll first to prevent duplication
        consumeItem(event, scrollItem);

        // Attempt the enhancement
        boolean success = true;
        if (currentPlus >= MAX_SAFE_ENHANCEMENT) {
            success = attemptRiskyEnhancement(player, currentPlus);
        }

        // Handle enhancement failure
        if (!success) {
            handleEnhancementFailure(player, event, weaponItem);
            return;
        }

        // Enhancement succeeded - update item
        double minDmgIncrease = Math.max(currentMinDmg * STAT_INCREASE_PERCENT, MIN_STAT_INCREASE);
        double maxDmgIncrease = Math.max(currentMaxDmg * STAT_INCREASE_PERCENT, MIN_STAT_INCREASE);

        int newMinDmg = (int) (currentMinDmg + minDmgIncrease);
        int newMaxDmg = (int) (currentMaxDmg + maxDmgIncrease);

        // Create updated item
        ItemStack enhancedItem = weaponItem.clone();
        ItemMeta meta = enhancedItem.getItemMeta();

        // Update name with new plus level
        meta.setDisplayName(ChatColor.RED + "[+" + (currentPlus + 1) + "] " + itemName);

        // Update lore with new damage values
        List<String> lore = new ArrayList<>(meta.getLore());
        boolean foundDmgLine = false;

        // Find and update the damage line
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("DMG:")) {
                lore.set(i, ChatColor.RED + "DMG: " + newMinDmg + " - " + newMaxDmg);
                foundDmgLine = true;
                break;
            }
        }

        // If damage line wasn't found, add it
        if (!foundDmgLine) {
            lore.add(0, ChatColor.RED + "DMG: " + newMinDmg + " - " + newMaxDmg);
        }

        meta.setLore(lore);
        enhancedItem.setItemMeta(meta);

        // Add glow effect for items +4 and above
        if (currentPlus + 1 >= MAX_SAFE_ENHANCEMENT) {
            Enchants.addGlow(enhancedItem);
        }

        // Remove protection if one was used
        if (ItemAPI.isProtected(enhancedItem)) {
            enhancedItem = ItemAPI.removeProtection(enhancedItem);
        }

        // Update the item in inventory
        event.setCurrentItem(enhancedItem);

        // Success feedback
        player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "→ Enhancement Successful +" + (currentPlus + 1));

        // Visual and sound effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.YELLOW);
    }

    /**
     * Handles a failed enhancement attempt
     *
     * @param player The player
     * @param event  The inventory event
     * @param item   The item that failed enhancement
     */
    private void handleEnhancementFailure(Player player, InventoryClickEvent event, ItemStack item) {
        if (ItemAPI.isProtected(item)) {
            // Protection scroll prevents item destruction
            event.setCurrentItem(ItemAPI.removeProtection(item));
            player.sendMessage(ChatColor.GREEN + "YOUR PROTECTION SCROLL HAS PREVENTED THIS ITEM FROM VANISHING");
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);
        } else {
            // Item is destroyed
            event.setCurrentItem(null);

            player.sendMessage(TextUtil.colorize("&c→ Enhancement Failed &7- &cItem Destroyed"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 1.0f, 0.5f);
        }

        // Failure visual effects
        ParticleUtil.showFailureEffect(player.getLocation());
    }

    /**
     * Attempts a risky enhancement with chance of failure
     *
     * @param player      The player attempting the enhancement
     * @param currentPlus The current enhancement level
     * @return true if enhancement succeeded, false if failed
     */
    private boolean attemptRiskyEnhancement(Player player, int currentPlus) {
        // Get the index for failure chance array
        int index = Math.min(currentPlus, FAILURE_CHANCES.length - 1);
        int failureChance = FAILURE_CHANCES[index];

        // Apply luck modifiers if any
        // In a real implementation, this would read from player data
        // failureChance -= player.getLuck() * 2;

        // Roll for success
        int roll = random.nextInt(100);
        boolean success = roll >= failureChance;

        // If failed, show fail effects
        if (!success) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 2.0f, 1.25f);
            ParticleUtil.showFailureEffect(player.getLocation());
        }

        return success;
    }

    /**
     * Helper method to safely consume one item from a stack
     */
    private void consumeItem(InventoryClickEvent event, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            event.setCursor(null);
        }
    }

    /**
     * Gets the display name without the plus prefix
     */
    private String getDisplayNameWithoutPlus(ItemStack item) {
        String displayName = item.getItemMeta().getDisplayName();
        if (displayName.startsWith(ChatColor.RED + "[+")) {
            int endIndex = displayName.indexOf("] ") + 2;
            if (endIndex < displayName.length()) {
                return displayName.substring(endIndex);
            }
        }
        return displayName;
    }
}
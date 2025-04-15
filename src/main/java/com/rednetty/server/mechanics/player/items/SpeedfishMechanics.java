package com.rednetty.server.mechanics.player.items;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Handles speedfish consumable items that provide temporary speed buffs
 */
public class SpeedfishMechanics implements Listener {

    // Cache for active player cooldowns
    private final Map<UUID, Long> speedfishCooldowns = new HashMap<>();
    private final YakPlayerManager playerManager;

    /**
     * Constructor initializes dependencies
     */
    public SpeedfishMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
    }

    /**
     * Initialize the speedfish system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("Speedfish mechanics have been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        speedfishCooldowns.clear();
        YakRealms.log("Speedfish mechanics have been disabled.");
    }

    /**
     * Create a speedfish item of a specific tier
     *
     * @param tier   The tier of speedfish (1-5)
     * @param inShop Whether the item is being displayed in a shop
     * @return The created speedfish ItemStack
     */
    public static ItemStack createSpeedfish(int tier, boolean inShop) {
        ItemStack fish = new ItemStack(Material.COD);
        ItemMeta meta = fish.getItemMeta();
        List<String> lore = new ArrayList<>();
        String name;
        int price = 0;

        switch (tier) {
            case 1:
                price = 25;
                name = ChatColor.WHITE + "Raw Shrimp of Lesser Agility";
                lore.add(ChatColor.RED + "SPEED (I) BUFF " + ChatColor.GRAY + "(15s)");
                lore.add(ChatColor.RED + "-10% HUNGER " + ChatColor.GRAY + "(instant)");
                lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "A raw and pink crustacean.");
                break;

            case 2:
                price = 50;
                name = ChatColor.WHITE + "Raw Herring of Greater Agility";
                lore.add(ChatColor.RED + "SPEED (I) BUFF " + ChatColor.GRAY + "(30s)");
                lore.add(ChatColor.RED + "-20% HUNGER " + ChatColor.GRAY + "(instant)");
                lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "A colourful and medium-sized fish.");
                break;

            case 3:
                price = 100;
                name = ChatColor.AQUA + "Raw Salmon of Lasting Agility";
                lore.add(ChatColor.RED + "SPEED (I) BUFF " + ChatColor.GRAY + "(30s)");
                lore.add(ChatColor.RED + "-30% HUNGER " + ChatColor.GRAY + "(instant)");
                lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "An elongated fish with a long bill.");
                break;

            case 4:
                price = 200;
                name = ChatColor.LIGHT_PURPLE + "Raw Lobster of Bursting Agility";
                lore.add(ChatColor.RED + "SPEED (II) BUFF " + ChatColor.GRAY + "(15s)");
                lore.add(ChatColor.RED + "-40% HUNGER " + ChatColor.GRAY + "(instant)");
                lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "An elongated fish with a long bill.");
                break;

            case 5:
                price = 300;
                name = ChatColor.YELLOW + "Raw Swordfish of Godlike Speed";
                lore.add(ChatColor.RED + "SPEED (II) BUFF " + ChatColor.GRAY + "(30s)");
                lore.add(ChatColor.RED + "-50% HUNGER " + ChatColor.GRAY + "(instant)");
                lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "An elongated fish with a long bill.");
                break;

            default:
                name = ChatColor.WHITE + "Unknown Fish";
                lore.add(ChatColor.GRAY + "A mysterious fish.");
                break;
        }

        if (inShop) {
            lore.add(ChatColor.GREEN + "Price: " + ChatColor.WHITE + price + "g");
        }

        meta.setDisplayName(name);
        meta.setLore(lore);
        fish.setItemMeta(meta);

        return fish;
    }

    /**
     * Extract speed buff level from item lore
     *
     * @param item The item to check
     * @return The speed level (0 for level I, 1 for level II)
     */
    private int getSpeedLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        for (String line : item.getItemMeta().getLore()) {
            if (!line.contains("SPEED")) continue;

            if (line.contains("(I)")) {
                return 0;
            } else if (line.contains("(II)")) {
                return 1;
            }
        }

        return 0;
    }

    /**
     * Extract effect duration from item lore
     *
     * @param item The item to check
     * @return The duration in seconds
     */
    private int getEffectDuration(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        for (String line : item.getItemMeta().getLore()) {
            if (!line.contains("SPEED")) continue;

            try {
                return Integer.parseInt(line.split("\\(")[2].split("s\\)")[0]);
            } catch (Exception e) {
                return 0;
            }
        }

        return 0;
    }

    /**
     * Extract hunger restore amount from item lore
     *
     * @param item The item to check
     * @return The hunger amount to restore
     */
    private int getHungerRestore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        for (String line : item.getItemMeta().getLore()) {
            if (!line.contains("HUNGER")) continue;

            try {
                int percent = Integer.parseInt(line.split("-")[1].split("%")[0]);
                return percent / 5; // Convert percentage to food level
            } catch (Exception e) {
                return 0;
            }
        }

        return 0;
    }

    /**
     * Handle speedfish consumption
     */
    @EventHandler
    public void onSpeedfishUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return;
        }

        // Handle cooked speedfish consumption
        if (isSpeedfishCooked(heldItem)) {
            handleCookedSpeedfishConsumption(event, player, heldItem);
            return;
        }

        // Handle raw speedfish interaction
        if (isSpeedfishRaw(heldItem)) {
            handleRawSpeedfishInteraction(event, player, heldItem);
        }
    }

    /**
     * Handle cooking raw speedfish when clicked on heat source
     */
    @EventHandler
    public void onSpeedfishCook(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!isSpeedfishRaw(heldItem) || !isHeatSource(event)) {
            return;
        }

        // Cook the speedfish
        event.setCancelled(true);
        cookSpeedfish(player, heldItem);
    }

    /**
     * Check if an item is a cooked speedfish
     *
     * @param item The item to check
     * @return true if cooked speedfish
     */
    private boolean isSpeedfishCooked(ItemStack item) {
        return item.getType() == Material.COOKED_COD &&
                item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().contains("Cooked") &&
                item.getItemMeta().hasLore();
    }

    /**
     * Check if an item is a raw speedfish
     *
     * @param item The item to check
     * @return true if raw speedfish
     */
    private boolean isSpeedfishRaw(ItemStack item) {
        return item.getType() == Material.COD &&
                item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                (item.getItemMeta().getDisplayName().contains("Agility") ||
                        item.getItemMeta().getDisplayName().contains("Speed"));
    }

    /**
     * Check if the player is interacting with a heat source
     *
     * @param event The interact event
     * @return true if heat source
     */
    private boolean isHeatSource(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return false;
        }

        Material blockType = event.getClickedBlock().getType();
        return blockType == Material.FURNACE ||
                blockType == Material.CAMPFIRE ||
                blockType == Material.FIRE ||
                blockType == Material.LAVA;
    }

    /**
     * Handle consumption of a cooked speedfish
     *
     * @param event  The interact event
     * @param player The player
     * @param item   The speedfish item
     */
    private void handleCookedSpeedfishConsumption(PlayerInteractEvent event, Player player, ItemStack item) {
        // Check for heat sources to prevent accidental usage
        if (event.hasBlock() && isHeatSource(event)) {
            return;
        }

        // Check if player already has speed effect
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You already have a speed effect active.");
            return;
        }

        // Apply speed effect
        int duration = getEffectDuration(item);
        int speedLevel = getSpeedLevel(item);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration * 20, speedLevel));

        // Apply hunger restoration
        int hungerRestore = getHungerRestore(item);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + hungerRestore));

        // Play consumption sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);

        // Consume the item
        consumeItem(player, item, event);
    }

    /**
     * Handle interaction with a raw speedfish
     *
     * @param event  The interact event
     * @param player The player
     * @param item   The speedfish item
     */
    private void handleRawSpeedfishInteraction(PlayerInteractEvent event, Player player, ItemStack item) {
        // Don't handle if clicking on furnace (will be handled by cook event)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
                event.getClickedBlock() != null &&
                event.getClickedBlock().getType() == Material.FURNACE) {
            return;
        }

        // Send cooking instructions
        player.sendMessage(ChatColor.YELLOW + "To cook, " + ChatColor.UNDERLINE +
                "RIGHT CLICK" + ChatColor.YELLOW + " any heat source.");
        player.sendMessage(ChatColor.GRAY + "Ex. Fire, Lava, Furnace");
    }

    /**
     * Cook a raw speedfish
     *
     * @param player The player
     * @param item   The raw speedfish
     */
    private void cookSpeedfish(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String name = meta.getDisplayName();

        // Check if already cooked
        if (name.contains("Cooked")) {
            player.sendMessage(ChatColor.RED + "This juicy fish flesh has already been cooked.");
            return;
        }

        // Play cooking sound
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.0f);

        // Modify the item name to indicate it's cooked
        if (name.contains("Andalucian")) {
            name = ChatColor.GRAY + "Cooked " + name;
        } else {
            name = name.substring(0, 2) + "Cooked " + name.substring(6);
        }

        // Update item properties
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        item.setType(Material.COOKED_COD);

        // Update the player's inventory
        player.getInventory().setItemInMainHand(item);
    }

    /**
     * Consume an item from the player's inventory
     *
     * @param player The player
     * @param item   The item to consume
     * @param event  The interact event
     */
    private void consumeItem(Player player, ItemStack item, PlayerInteractEvent event) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        // Check if in a duel (don't consume items in duels)
/*        if (yakPlayer.isInDuel()) {
            return;
        }*/

        // Consume the item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(null);
        }
    }
}
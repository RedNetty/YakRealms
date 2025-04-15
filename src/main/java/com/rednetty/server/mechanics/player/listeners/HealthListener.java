package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Handles all health-related events including health checks,
 * health potions, and health display.
 */
public class HealthListener extends BaseListener {

    private final YakPlayerManager playerManager;

    public HealthListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("Health listener initialized");
    }

    /**
     * Recalculates a player's max health based on their equipment
     *
     * @param player The player to check
     */
    public void recalculateHealth(Player player) {
        if (player.isDead()) return;

        // Skip for operators in GM mode
        if (player.isOp() && !isGodModeDisabled(player)) {
            return;
        }

        // Calculate base health and bonuses
        double baseHealth = 50.0;
        double vitalityBonus = 0.0;

        // Check armor pieces for HP and VIT bonuses
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (PlayerStatsCalculator.hasValidItemStats(item)) {
                // Add direct HP bonus
                baseHealth += PlayerStatsCalculator.getHp(item);

                // Add vitality bonus
                int vitality = PlayerStatsCalculator.getElementalAttribute(item, "VIT");
                vitalityBonus += vitality;

                // Show frost armor effects if applicable
                applyFrostArmorEffect(player, item);
            }
        }

        // Apply vitality bonus to max health
        if (vitalityBonus > 0.0) {
            double bonus = vitalityBonus * 0.05;
            baseHealth += baseHealth * (bonus / 100.0);
        }

        // Set max health
        player.setMaxHealth(baseHealth);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
    }

    /**
     * Display frost armor particle effects
     */
    private void applyFrostArmorEffect(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = item.getItemMeta().getDisplayName();
        if (displayName.contains("Frost-Wing's")) {
            Location location = player.getLocation().clone();

            // Position particles based on armor type
            if (item.getType().name().contains("HELMET")) {
                location.add(0, 2, 0);
            } else if (item.getType().name().contains("CHESTPLATE")) {
                location.add(0, 1.25, 0);
            } else if (item.getType().name().contains("LEGGINGS")) {
                location.add(0, 0.75, 0);
            }

            // Display particles
            player.getWorld().spawnParticle(Particle.SPELL_MOB, location, 30, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.WHITE, 1));
            player.getWorld().spawnParticle(Particle.REDSTONE, location, 30, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.YELLOW, 1));
        }
    }

    /**
     * Handle health recalculation after inventory changes
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Schedule health check to run after inventory is updated
        new BukkitRunnable() {
            @Override
            public void run() {
                recalculateHealth(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Handle health potion consumption
     */
    @EventHandler
    public void onHealthPotionUse(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return;
        }

        // Cancel the default potion drink action
        event.setCancelled(true);

        // Extract HP amount from lore
        int healAmount = extractHealAmount(item);
        if (healAmount <= 0) {
            return;
        }

        // Drink potion effects
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);

        // Remove the potion from hand
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getHeldItemSlot();
        inventory.setItemInMainHand(null);

        // Find another potion and move it to hand
        for (int i = 35; i >= 0; i--) {
            ItemStack invItem = inventory.getItem(i);
            if (invItem != null && invItem.getType() == Material.POTION &&
                    invItem != item && isHealthPotion(invItem)) {
                inventory.setItem(slot, invItem);
                inventory.setItem(i, null);
                break;
            }
        }

        // Apply healing
        double newHealth = player.getHealth() + healAmount;
        if (newHealth > player.getMaxHealth()) {
            player.sendMessage(" " + ChatColor.GREEN + ChatColor.BOLD + "+" + ChatColor.GREEN + healAmount
                    + ChatColor.BOLD + " HP" + ChatColor.GRAY + " [" + (int) player.getMaxHealth() + "/"
                    + (int) player.getMaxHealth() + "HP]");
            player.setHealth(player.getMaxHealth());
        } else {
            player.sendMessage(" " + ChatColor.GREEN + ChatColor.BOLD + "+" + ChatColor.GREEN + healAmount
                    + ChatColor.BOLD + " HP" + ChatColor.GRAY + " [" + (int) (player.getHealth() + healAmount)
                    + "/" + (int) player.getMaxHealth() + "HP]");
            player.setHealth(newHealth);
        }
    }

    /**
     * Check if an item is a health potion
     */
    private boolean isHealthPotion(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return false;
        }

        List<String> lore = item.getItemMeta().getLore();
        for (String line : lore) {
            if (line.contains("HP") && !line.contains("REGEN")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract heal amount from potion lore
     */
    private int extractHealAmount(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return 0;
        }

        List<String> lore = item.getItemMeta().getLore();
        String firstLine = ChatColor.stripColor(lore.get(0));

        if (!firstLine.contains("HP")) {
            return 0;
        }

        try {
            // Extract number from format like "Heals 100 HP"
            String[] parts = firstLine.split(" ");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("HP") && i > 0) {
                    return Integer.parseInt(parts[i - 1]);
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to parse health potion amount: " + firstLine);
        }

        return 0;
    }

    /**
     * Handle player join to set health
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule health check after player is fully loaded
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    recalculateHealth(player);
                }
            }
        }.runTaskLater(plugin, 10L);
    }

    /**
     * Disable natural health regeneration
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHealthRegen(EntityRegainHealthEvent event) {
        // Disable vanilla health regeneration
        event.setCancelled(true);
    }

    /**
     * Play equip sound when switching weapons
     */
    @EventHandler
    public void onWeaponSwitch(PlayerItemHeldEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem != null && isWeapon(newItem.getType())) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.5f);
        }
    }

    /**
     * Check if a material is a weapon
     */
    private boolean isWeapon(Material material) {
        String typeName = material.name();
        return typeName.contains("_SWORD") ||
                typeName.contains("_AXE") ||
                typeName.contains("_HOE") ||
                typeName.contains("_SHOVEL");
    }

    /**
     * Check if god mode is disabled for a player
     *
     * @param player The player to check
     * @return true if god mode is disabled
     */
    private boolean isGodModeDisabled(Player player) {
        return Toggles.isToggled(player, "God Mode Disabled");
    }
}
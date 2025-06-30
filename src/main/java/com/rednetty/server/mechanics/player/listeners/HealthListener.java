package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.pvp.AlignmentMechanics;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * FIXED: Handles all health-related events including health checks,
 * health potions, and health display with proper timing and coordination with AlignmentMechanics
 */
public class HealthListener extends BaseListener {

    private final YakPlayerManager playerManager;
    private final AlignmentMechanics alignmentMechanics;

    // FIXED: Track pending health recalculations to prevent spam
    private final Map<String, Long> pendingRecalculations = new ConcurrentHashMap<>();
    private static final long RECALCULATION_COOLDOWN = 1000L; // 1 second cooldown

    public HealthListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
        this.alignmentMechanics = AlignmentMechanics.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("Health listener initialized");
    }

    /**
     * FIXED: Recalculates a player's max health based on their equipment
     * Now includes proper validation, cooldown, and coordination with AlignmentMechanics
     *
     * @param player The player to check
     */
    public void recalculateHealth(Player player) {
        if (player == null || player.isDead() || !player.isOnline()) {
            return;
        }

        // FIXED: Check cooldown to prevent spam
        String playerName = player.getName();
        long currentTime = System.currentTimeMillis();
        Long lastRecalculation = pendingRecalculations.get(playerName);

        if (lastRecalculation != null && currentTime - lastRecalculation < RECALCULATION_COOLDOWN) {
            return; // Skip if too recent
        }

        pendingRecalculations.put(playerName, currentTime);

        // Skip for operators in GM mode (unless they have disabled god mode)
        if (player.isOp() && !isGodModeDisabled(player)) {
            return;
        }

        try {
            // Calculate base health and bonuses using PlayerStatsCalculator
            double baseHealth = 50.0; // Base health
            double totalHpBonus = 0.0;
            double vitalityBonus = 0.0;

            // Check armor pieces for HP and VIT bonuses
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (PlayerStatsCalculator.hasValidItemStats(item)) {
                    // Add direct HP bonus
                    totalHpBonus += PlayerStatsCalculator.getHp(item);

                    // Add vitality bonus
                    int vitality = PlayerStatsCalculator.getElementalAttribute(item, "VIT");
                    vitalityBonus += vitality;

                    // Show frost armor effects if applicable
                    applyFrostArmorEffect(player, item);
                }
            }

            // Add HP bonus to base health
            baseHealth += totalHpBonus;

            // Apply vitality bonus to max health (5% per VIT point as per original logic)
            if (vitalityBonus > 0.0) {
                double bonus = vitalityBonus * 0.05;
                baseHealth += baseHealth * (bonus / 100.0);
            }

            // Ensure minimum health
            baseHealth = Math.max(baseHealth, 20.0);

            // FIXED: Only update if the values are significantly different to prevent flickering
            double currentMaxHealth = player.getMaxHealth();
            if (Math.abs(currentMaxHealth - baseHealth) > 0.5) {
                player.setMaxHealth(baseHealth);

                // FIXED: Ensure health doesn't exceed new max health after recalculation
                if (player.getHealth() > baseHealth) {
                    player.setHealth(baseHealth);
                }

                logger.fine("Recalculated health for " + player.getName() + ": " +
                        currentMaxHealth + " -> " + baseHealth);
            }

            // FIXED: Always ensure health scaling is properly set
            player.setHealthScale(20.0);
            player.setHealthScaled(true);

            // FIXED: Trigger health bar update in AlignmentMechanics after a short delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        alignmentMechanics.forceUpdateHealthBar(player);
                    }
                }
            }.runTaskLater(plugin, 1L);

        } catch (Exception e) {
            logger.warning("Error recalculating health for " + player.getName() + ": " + e.getMessage());
        }
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
     * FIXED: Handle health recalculation after inventory changes with proper timing
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

        // FIXED: Only recalculate if it's an armor slot or equipment change
        boolean isArmorChange = false;
        if (event.getSlot() >= 36 && event.getSlot() <= 39) { // Armor slots
            isArmorChange = true;
        } else if (event.getCurrentItem() != null && isArmor(event.getCurrentItem().getType())) {
            isArmorChange = true;
        } else if (event.getCursor() != null && isArmor(event.getCursor().getType())) {
            isArmorChange = true;
        }

        if (isArmorChange) {
            // FIXED: Longer delay to ensure inventory is fully updated
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        recalculateHealth(player);
                    }
                }
            }.runTaskLater(plugin, 5L); // 5 ticks = 0.25 seconds delay
        }
    }

    /**
     * Handle health potion consumption
     */
    @EventHandler
    public void onHealthPotionUse(PlayerInteractEvent event) {
        if (!(event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return;
        }

        // Check if it's a health potion
        if (!isHealthPotion(item)) {
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

        // FIXED: Update health bar after healing
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    alignmentMechanics.forceUpdateHealthBar(player);
                }
            }
        }.runTaskLater(plugin, 1L);
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
            String cleanLine = ChatColor.stripColor(line);
            if (cleanLine.contains("HP") && !cleanLine.contains("REGEN") && cleanLine.contains("Heals")) {
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
        for (String line : lore) {
            String cleanLine = ChatColor.stripColor(line);

            if (cleanLine.contains("Heals") && cleanLine.contains("HP")) {
                try {
                    // Extract number from format like "Heals 100 HP"
                    String[] parts = cleanLine.split(" ");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("HP") && i > 0) {
                            return Integer.parseInt(parts[i - 1]);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to parse health potion amount: " + cleanLine);
                }
            }
        }

        return 0;
    }

    /**
     * FIXED: Handle player join to set health - but ONLY after data is loaded
     * This is now called by JoinLeaveListener after player data is loaded
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // FIXED: Much longer delay to ensure all systems are initialized
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    // Verify player data is loaded before recalculating health
                    if (playerManager.getPlayer(player) != null) {
                        recalculateHealth(player);
                        logger.fine("Initial health calculation completed for " + player.getName());
                    } else {
                        // If still not loaded, try again in a bit
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (player.isOnline() && !player.isDead() && playerManager.getPlayer(player) != null) {
                                    recalculateHealth(player);
                                    logger.fine("Delayed health calculation completed for " + player.getName());
                                }
                            }
                        }.runTaskLater(plugin, 40L); // Try again after 2 seconds
                    }
                }
            }
        }.runTaskLater(plugin, 60L); // 3 second delay to ensure everything is loaded
    }

    /**
     * Disable natural health regeneration
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHealthRegen(EntityRegainHealthEvent event) {
        // Disable vanilla health regeneration for players
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    /**
     * FIXED: Play equip sound when switching weapons with proper validation
     */
    @EventHandler
    public void onWeaponSwitch(PlayerItemHeldEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();

        try {
            ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

            if (newItem != null && isWeapon(newItem.getType())) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.5f);
            }
        } catch (Exception e) {
            // Ignore sound errors
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
     * Check if a material is armor
     */
    private boolean isArmor(Material material) {
        String name = material.name();
        return name.contains("_HELMET") ||
                name.contains("_CHESTPLATE") ||
                name.contains("_LEGGINGS") ||
                name.contains("_BOOTS");
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

    /**
     * Check if patch lockdown is active
     *
     * @return true if patch lockdown is active
     */
    public boolean isPatchLockdown() {
        return YakRealms.isPatchLockdown();
    }

    /**
     * Public method for other systems to trigger health recalculation
     * This ensures proper timing and validation
     */
    public void scheduleHealthRecalculation(Player player, long delayTicks) {
        if (player == null) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !player.isDead()) {
                    recalculateHealth(player);
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    /**
     * Force immediate health recalculation (use with caution)
     */
    public void forceHealthRecalculation(Player player) {
        if (player != null && player.isOnline() && !player.isDead()) {
            // Remove cooldown temporarily for forced recalculation
            pendingRecalculations.remove(player.getName());
            recalculateHealth(player);
        }
    }

    /**
     * Check if a player's health needs recalculation based on equipment changes
     */
    public boolean needsHealthRecalculation(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        // Check if player has any equipment that affects health
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (PlayerStatsCalculator.hasValidItemStats(item)) {
                // Check if item has HP or VIT bonuses
                if (PlayerStatsCalculator.getHp(item) > 0 ||
                        PlayerStatsCalculator.getElementalAttribute(item, "VIT") > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * FIXED: Clean up pending recalculations when player quits
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            pendingRecalculations.remove(player.getName());
        }
    }

    /**
     * Get debug information about a player's health calculation
     */
    public String getHealthDebugInfo(Player player) {
        if (player == null || !player.isOnline()) {
            return "Player is null or offline";
        }

        StringBuilder info = new StringBuilder();
        info.append("=== Health Debug for ").append(player.getName()).append(" ===\n");
        info.append("Current Health: ").append(player.getHealth()).append("/").append(player.getMaxHealth()).append("\n");
        info.append("Base Health: 50\n");

        double totalHp = 0;
        int totalVit = 0;

        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (PlayerStatsCalculator.hasValidItemStats(item)) {
                int hp = PlayerStatsCalculator.getHp(item);
                int vit = PlayerStatsCalculator.getElementalAttribute(item, "VIT");

                if (hp > 0 || vit > 0) {
                    info.append("Item: ").append(item.getType()).append(" - HP: +").append(hp).append(", VIT: +").append(vit).append("\n");
                    totalHp += hp;
                    totalVit += vit;
                }
            }
        }

        info.append("Total HP Bonus: +").append(totalHp).append("\n");
        info.append("Total VIT: ").append(totalVit).append(" (").append(totalVit * 5).append("% health bonus)\n");

        double calculatedMaxHealth = 50 + totalHp;
        if (totalVit > 0) {
            calculatedMaxHealth += calculatedMaxHealth * (totalVit * 0.05);
        }
        calculatedMaxHealth = Math.max(calculatedMaxHealth, 20.0);

        info.append("Calculated Max Health: ").append(calculatedMaxHealth).append("\n");
        info.append("================================");

        return info.toString();
    }
}
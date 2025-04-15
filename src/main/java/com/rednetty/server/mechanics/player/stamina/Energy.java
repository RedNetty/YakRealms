package com.rednetty.server.mechanics.player.stamina;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages player energy mechanics - a stamina system that affects
 * combat and movement capabilities.
 */
public class Energy implements Listener {
    private static Energy instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Configuration constants
    private static final int MAX_ENERGY = 100;
    private static final int ENERGY_UPDATE_INTERVAL = 1; // ticks
    private static final int ENERGY_REDUCTION_INTERVAL = 4; // ticks
    private static final int BASE_ENERGY_REGEN = 2;
    private static final float BASE_ENERGY_INCREASE = 100.0f;
    private static final float ENERGY_PER_ARMOR_PIECE = 7.5f;
    private static final float ENERGY_INCREASE_PER_INTEL = 0.02f;
    private static final int ENERGY_REDUCTION_AMOUNT = 85;
    private static final float ENERGY_REDUCTION_MULTIPLIER = 3.2f;
    private static final int FATIGUE_DURATION = 40; // ticks (2 seconds)
    private static final int FATIGUE_AMPLIFIER = 5;
    private static final long REGEN_DELAY = 2000L; // milliseconds

    // In-memory caches
    private final Map<UUID, Long> attackCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> energyRegenCooldowns = new ConcurrentHashMap<>();
    public final Map<UUID, Long> noDamage = new ConcurrentHashMap<>();

    /**
     * Private constructor for singleton pattern
     */
    private Energy() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    /**
     * Gets the singleton instance
     *
     * @return The Energy instance
     */
    public static Energy getInstance() {
        if (instance == null) {
            instance = new Energy();
        }
        return instance;
    }

    /**
     * Initialize the energy system
     */
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startEnergyTasks();
        YakRealms.log("Energy system has been enabled.");
    }

    /**
     * Clean up on disable
     */
    public void onDisable() {
        attackCooldowns.clear();
        energyRegenCooldowns.clear();
        noDamage.clear();
        YakRealms.log("Energy system has been disabled.");
    }

    /**
     * Start the energy update and reduction tasks
     */
    private void startEnergyTasks() {
        // Energy regeneration task
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    YakPlayer yakPlayer = playerManager.getPlayer(player);
                    if (yakPlayer == null) continue;

                    UUID uuid = player.getUniqueId();

                    // Skip if player is in energy cooldown
                    if (energyRegenCooldowns.containsKey(uuid) &&
                            System.currentTimeMillis() - energyRegenCooldowns.get(uuid) < REGEN_DELAY) {
                        continue;
                    }

                    // Calculate energy regeneration
                    float energy = BASE_ENERGY_INCREASE;
                    int addedEnergy = 0;

                    // Check armor for energy regen bonuses
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor != null && armor.getType().name().contains("_") &&
                                armor.hasItemMeta() && armor.getItemMeta().hasLore()) {
                            addedEnergy += getEnergyRegenFromItem(armor);
                            int addedIntel = getElementalAttributeFromItem(armor, "INT");

                            if (addedIntel > 0) {
                                addedEnergy += Math.round(addedIntel * ENERGY_INCREASE_PER_INTEL);
                            }

                            energy += addedEnergy * ENERGY_PER_ARMOR_PIECE;
                        }
                    }

                    // Apply energy regen
                    if (getEnergy(yakPlayer) < MAX_ENERGY) {
                        float energyRegen = energy / MAX_ENERGY;
                        setEnergy(yakPlayer, getEnergy(yakPlayer) + energyRegen);
                    }
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, ENERGY_UPDATE_INTERVAL);

        // Energy reduction while sprinting task
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    YakPlayer yakPlayer = playerManager.getPlayer(player);
                    if (yakPlayer == null) continue;

                    // Skip if player is not sprinting or has energy disabled
                    if (!player.isSprinting() || isEnergyDisabled(yakPlayer) ||
                            isSafeZone(player.getLocation())) {
                        continue;
                    }

                    // Calculate energy reduction
                    float energyReduction = BASE_ENERGY_INCREASE + ENERGY_REDUCTION_AMOUNT;
                    energyReduction *= ENERGY_REDUCTION_MULTIPLIER;
                    float adjustedReduction = energyReduction / MAX_ENERGY;

                    // Apply energy reduction
                    if (getEnergy(yakPlayer) > 0) {
                        setEnergy(yakPlayer, getEnergy(yakPlayer) - adjustedReduction);
                    }

                    // Apply fatigue if energy is depleted
                    if (getEnergy(yakPlayer) <= 0) {
                        setEnergy(yakPlayer, 0);
                        energyRegenCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                        applyFatigue(player);
                    }
                }
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, ENERGY_REDUCTION_INTERVAL);
    }

    /**
     * Apply the fatigue effect to a player
     *
     * @param player The player to apply fatigue to
     */
    private void applyFatigue(Player player) {
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_DIGGING,
                    FATIGUE_DURATION,
                    FATIGUE_AMPLIFIER,
                    false,
                    true
            ));
            player.setSprinting(false);
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_PANT, 0.5f, 1.5f);
        });
    }

    /**
     * Get energy regen value from an item
     *
     * @param item The item to check
     * @return The energy regen value
     */
    private int getEnergyRegenFromItem(ItemStack item) {
        if (item != null && item.getType().name().contains("_") &&
                item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.contains("ENERGY REGEN")) {
                    try {
                        return Integer.parseInt(line.split(": ")[1].split("%")[0]);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Get an elemental attribute from an item
     *
     * @param item      The item to check
     * @param attribute The attribute name (e.g., "INT")
     * @return The attribute value
     */
    private int getElementalAttributeFromItem(ItemStack item, String attribute) {
        if (item != null && item.getType().name().contains("_") &&
                item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String line : item.getItemMeta().getLore()) {
                if (line.contains(attribute + ":")) {
                    try {
                        return Integer.parseInt(line.split(": ")[1]);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Calculate energy cost based on weapon type
     *
     * @param item        The weapon item
     * @param isActualHit Whether this is an actual hit or just a swing
     * @return The energy cost
     */
    private int calculateWeaponEnergyCost(ItemStack item, boolean isActualHit) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return isActualHit ? 6 : 3;
        }

        String materialName = item.getType().name();
        int cost;

        if (isActualHit) {
            // Actual hit costs
            if (materialName.contains("WOODEN") || materialName.contains("WOOD")) {
                if (materialName.contains("SWORD") || materialName.contains("SHOVEL") || materialName.contains("SPADE") || materialName.contains("HOE")) {
                    cost = 8;
                } else { // AXE
                    cost = 10;
                }
            } else if (materialName.contains("STONE")) {
                if (materialName.contains("SWORD") || materialName.contains("HOE")) {
                    cost = 10;
                } else { // AXE or SHOVEL
                    cost = 11;
                }
            } else if (materialName.contains("IRON")) {
                if (materialName.contains("SWORD")) {
                    cost = 11;
                } else if (materialName.contains("HOE") || materialName.contains("SHOVEL") || materialName.contains("SPADE")) {
                    cost = 12;
                } else { // AXE
                    cost = 13;
                }
            } else if (materialName.contains("DIAMOND")) {
                if (materialName.contains("SWORD")) {
                    cost = 12;
                } else if (materialName.contains("HOE") || materialName.contains("SHOVEL") || materialName.contains("SPADE")) {
                    cost = 13;
                } else { // AXE
                    cost = 14;
                }
            } else if (materialName.contains("GOLDEN") || materialName.contains("GOLD")) {
                if (materialName.contains("SWORD")) {
                    cost = 13;
                } else if (materialName.contains("HOE") || materialName.contains("SHOVEL") || materialName.contains("SPADE")) {
                    cost = 16;
                } else { // AXE
                    cost = 14;
                }
            } else {
                cost = 12; // Default for other materials
            }
        } else {
            // Swing costs
            if (materialName.contains("WOODEN") || materialName.contains("WOOD")) {
                cost = 3;
            } else if (materialName.contains("STONE")) {
                if (materialName.contains("SWORD")) {
                    cost = 3;
                } else { // AXE/SHOVEL
                    cost = 4;
                }
            } else if (materialName.contains("IRON")) {
                if (materialName.contains("SWORD")) {
                    cost = 4;
                } else { // AXE/SHOVEL
                    cost = 6;
                }
            } else if (materialName.contains("DIAMOND") || materialName.contains("GOLD") || materialName.contains("GOLDEN")) {
                if (materialName.contains("SWORD") || materialName.contains("SHOVEL") || materialName.contains("SPADE") || materialName.contains("HOE")) {
                    cost = 8;
                } else { // AXE
                    cost = 10;
                }
            } else {
                cost = 6; // Default for other materials
            }
        }

        return cost;
    }

    /**
     * Get a player's current energy level
     *
     * @param yakPlayer The player to check
     * @return The player's energy level (0-100)
     */
    public int getEnergy(YakPlayer yakPlayer) {
        Object energy = yakPlayer.getTemporaryData("energy");
        return energy != null ? (Integer) energy : MAX_ENERGY;
    }

    /**
     * Set a player's energy level
     *
     * @param yakPlayer The player to update
     * @param energy    The new energy value
     */
    public void setEnergy(YakPlayer yakPlayer, float energy) {
        // Clamp energy between 0 and MAX_ENERGY
        int clampedEnergy = Math.max(0, Math.min(MAX_ENERGY, (int) energy));

        // Update the data in YakPlayer
        yakPlayer.setTemporaryData("energy", clampedEnergy);

        // Update visual indicators if player is online
        Player player = yakPlayer.getBukkitPlayer();
        if (player != null && player.isOnline()) {
            // Use experience bar to display energy level
            player.setExp((float) clampedEnergy / MAX_ENERGY);
            player.setLevel(clampedEnergy);
        }
    }

    /**
     * Remove energy from a player
     *
     * @param player The player to remove energy from
     * @param amount The amount of energy to remove
     */
    public void removeEnergy(Player player, int amount) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        // Skip in safe zones or if energy is disabled
        if (isSafeZone(player.getLocation()) ||
                player.getGameMode() == GameMode.CREATIVE ||
                isEnergyDisabled(yakPlayer)) {
            return;
        }

        // Check for cooldown
        if (player.hasMetadata("lastenergy") &&
                System.currentTimeMillis() - player.getMetadata("lastenergy").get(0).asLong() < ENERGY_UPDATE_INTERVAL) {
            return;
        }

        // Set cooldown
        player.setMetadata("lastenergy", new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        // Calculate and set new energy level
        int currentEnergy = getEnergy(yakPlayer);
        int newEnergy = Math.max(0, currentEnergy - amount);
        setEnergy(yakPlayer, newEnergy);

        // Apply fatigue if energy is depleted
        if (newEnergy <= 0) {
            energyRegenCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            applyFatigue(player);
        }
    }

    /**
     * Check if energy system is disabled for a player
     *
     * @param yakPlayer The player to check
     * @return true if energy is disabled
     */
    private boolean isEnergyDisabled(YakPlayer yakPlayer) {
        return WorldGuardManager.getInstance().isSafeZone(yakPlayer.getLocation());
    }

    /**
     * Check if a location is in a safe zone
     *
     * @param location The location to check
     * @return true if the location is in a safe zone
     */
    private boolean isSafeZone(org.bukkit.Location location) {
        // This would integrate with your alignment/territory system
        // For now, returning false as we don't have the integration yet
        return false;
    }

    /**
     * Handles energy consumption for player attacks (left-click)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnergyUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer == null) return;

        // Check attack cooldown
        if (noDamage.containsKey(uuid) &&
                System.currentTimeMillis() - noDamage.get(uuid) < ENERGY_UPDATE_INTERVAL) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }

        // Get item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null) return;

        // Calculate energy cost based on weapon type
        int energyCost = calculateWeaponEnergyCost(itemInHand, false);

        // Remove energy
        if (energyCost > 0 && getEnergy(yakPlayer) > 0) {
            removeEnergy(player, energyCost);
        }

        // Handle energy depletion
        if (getEnergy(yakPlayer) <= 0) {
            setEnergy(yakPlayer, 0);
            energyRegenCooldowns.put(uuid, System.currentTimeMillis());
            applyFatigue(player);
        }
    }

    /**
     * Handles energy consumption for actual damage events
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnergyUseDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Player player = (Player) event.getDamager();
        UUID uuid = player.getUniqueId();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer == null) return;

        // Check attack cooldown
        if (noDamage.containsKey(uuid) &&
                System.currentTimeMillis() - noDamage.get(uuid) < ENERGY_UPDATE_INTERVAL) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        // Check if player has the fatigue effect
        if (player.hasPotionEffect(PotionEffectType.SLOW_DIGGING)) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        // Set the attack cooldown
        noDamage.put(uuid, System.currentTimeMillis());

        // Get item in hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null) return;

        // Calculate energy cost based on weapon type
        int energyCost = calculateWeaponEnergyCost(itemInHand, true);

        // Remove energy
        if (energyCost > 0 && getEnergy(yakPlayer) > 0) {
            removeEnergy(player, energyCost);
        }

        // Handle energy depletion
        if (getEnergy(yakPlayer) <= 0) {
            setEnergy(yakPlayer, 0);
            energyRegenCooldowns.put(uuid, System.currentTimeMillis());
            applyFatigue(player);
        }
    }
}
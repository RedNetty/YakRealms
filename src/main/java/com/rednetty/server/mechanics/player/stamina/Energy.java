package com.rednetty.server.mechanics.player.stamina;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Clean Energy System - Simplified and fixed implementation
 * <p>
 * Updated to use Adventure API and modern Paper capabilities for 1.21.7.
 * Maintains backwards compatibility for core logic and public methods.
 *
 * Features:
 * - Energy consumption for combat actions
 * - Energy regeneration over time
 * - Fatigue effects when energy is depleted
 * - Integration with armor and weapon systems
 * - Safe zone protections
 * - YakPlayer integration
 *
 * @author YakRealms Development Team
 * @version 4.0 - ADVENTURE & PAPER UPDATE
 */
public class Energy implements Listener {

    // ==================== SINGLETON PATTERN ====================

    private static Energy instance;

    public static Energy getInstance() {
        if (instance == null) {
            synchronized (Energy.class) {
                if (instance == null) {
                    instance = new Energy();
                }
            }
        }
        return instance;
    }

    // ==================== CONFIGURATION CONSTANTS ====================

    private static final int MAX_ENERGY = 100;
    private static final int ENERGY_UPDATE_INTERVAL = 1; // ticks
    private static final int ENERGY_REDUCTION_INTERVAL = 4; // ticks
    private static final long DAMAGE_COOLDOWN_MS = 50L; // 50ms between damage
    private static final long REGEN_DELAY_MS = 2000L; // 2 seconds after energy depletion

    // Energy calculation constants (from old system)
    private static final float BASE_ENERGY_INCREASE = 100.0f;
    private static final float ENERGY_PER_ARMOR_PIECE = 7.5f;
    private static final float ENERGY_INCREASE_PER_INTEL = 0.02f;
    private static final int ENERGY_REDUCTION_AMOUNT = 85;
    private static final float ENERGY_REDUCTION_MULTIPLIER = 3.2f;

    // Fatigue effects
    private static final int FATIGUE_DURATION = 40; // ticks
    private static final int FATIGUE_AMPLIFIER = 5;

    // --- Adventure API Messages ---
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String PREFIX = "<gray>[<red><bold>ENERGY</bold></gray>] ";
    private static final String ENERGY_DEPLETED_MSG_STRING = "<red>You are exhausted! Wait for your energy to recover.";
    private static final Component ENERGY_DEPLETED_COMPONENT = MINI_MESSAGE.deserialize(PREFIX + ENERGY_DEPLETED_MSG_STRING);


    // ==================== INSTANCE VARIABLES ====================

    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Simple tracking maps
    private final Map<UUID, Long> damageCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> regenCooldowns = new ConcurrentHashMap<>();

    // ==================== CONSTRUCTOR ====================

    private Energy() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
    }

    // ==================== LIFECYCLE METHODS ====================

    public void onEnable() {
        try {
            Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
            startEnergyTasks();
            initializeOnlinePlayers();
            logger.info("Energy system enabled successfully (v4.0 - Adventure/Paper)");
        } catch (Exception e) {
            logger.severe("Failed to enable Energy system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void onDisable() {
        try {
            damageCooldowns.clear();
            regenCooldowns.clear();
            logger.info("Energy system disabled");
        } catch (Exception e) {
            logger.warning("Error during Energy system disable: " + e.getMessage());
        }
    }

    // ==================== INITIALIZATION ====================

    private void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayerEnergy(player);
        }
    }

    private void initializePlayerEnergy(Player player) {
        if (player == null) return;

        try {
            // Get or create energy value
            int energy = getEnergyValue(player);

            // Update display
            updateEnergyDisplay(player, energy);

            logger.fine("Initialized energy for " + player.getName() + ": " + energy);
        } catch (Exception e) {
            logger.warning("Error initializing energy for " + player.getName() + ": " + e.getMessage());
        }
    }

    // ==================== CORE ENERGY METHODS ====================

    /**
     * Get current energy value for player
     */
    public int getEnergyValue(Player player) {
        if (player == null) return MAX_ENERGY;

        try {
            // Try YakPlayer first
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                Object energyData = yakPlayer.getTemporaryData("energy");
                if (energyData instanceof Integer) {
                    int energy = (Integer) energyData;
                    return Math.max(0, Math.min(MAX_ENERGY, energy));
                }
            }
        } catch (Exception e) {
            logger.fine("Error getting energy from YakPlayer for " + player.getName() + ": " + e.getMessage());
        }

        // Fallback to display values (like old system)
        float expEnergy = player.getExp() * MAX_ENERGY;
        return Math.max(0, Math.min(MAX_ENERGY, (int) expEnergy));
    }

    /**
     * Set energy value for player
     */
    public void setEnergyValue(Player player, int energy) {
        if (player == null) return;

        // Clamp energy value
        int clampedEnergy = Math.max(0, Math.min(MAX_ENERGY, energy));

        try {
            // Try to store in YakPlayer
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer != null) {
                yakPlayer.setTemporaryData("energy", clampedEnergy);
            }
        } catch (Exception e) {
            logger.fine("Error setting energy in YakPlayer for " + player.getName() + ": " + e.getMessage());
        }

        // Always update display (this is the primary storage like old system)
        updateEnergyDisplay(player, clampedEnergy);
    }

    /**
     * Remove energy from player
     */
    public void removeEnergy(Player player, int amount) {
        if (player == null || amount <= 0) return;

        if (shouldSkipEnergyReduction(player)) {
            return;
        }

        // Check cooldown to prevent spam (50ms cooldown like old system)
        if (player.hasMetadata("lastenergy")) {
            long lastEnergy = player.getMetadata("lastenergy").get(0).asLong();
            if (System.currentTimeMillis() - lastEnergy < 50) {
                return;
            }
        }

        player.setMetadata("lastenergy", new FixedMetadataValue(YakRealms.getInstance(), System.currentTimeMillis()));

        int currentEnergy = getEnergyValue(player);
        int newEnergy = Math.max(0, currentEnergy - amount);
        setEnergyValue(player, newEnergy);

        if (newEnergy <= 0) {
            handleEnergyDepletion(player);
        }
    }

    /**
     * Check if player has enough energy
     */
    public boolean hasEnergy(Player player, int amount) {
        if (player == null) return false;
        return getEnergyValue(player) >= amount;
    }

    // ==================== DISPLAY MANAGEMENT ====================

    private void updateEnergyDisplay(Player player, int energy) {
        if (player == null || !player.isOnline()) return;

        try {
            // Set exp bar as energy percentage (like old system)
            float expValue = Math.max(0.0f, Math.min(1.0f, (float) energy / MAX_ENERGY));
            player.setExp(expValue);

            // Set level as energy value (like old system)
            player.setLevel(energy);
        } catch (Exception e) {
            logger.fine("Error updating energy display for " + player.getName() + ": " + e.getMessage());
        }
    }

    // ==================== ENERGY TASKS ====================

    private void startEnergyTasks() {
        startEnergyRegenerationTask();
        startEnergyReductionTask();
    }

    /**
     * Energy regeneration task.
     * NOTE: This task is now SYNCHRONOUS to ensure thread safety when accessing Player and Bukkit API.
     * The performance impact is negligible.
     */
    private void startEnergyRegenerationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        processEnergyRegeneration(player);
                    }
                } catch (Exception e) {
                    logger.warning("Error in energy regeneration task: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, ENERGY_UPDATE_INTERVAL);
    }

    /**
     * Energy reduction task for sprinting.
     * NOTE: This task is now SYNCHRONOUS to ensure thread safety.
     */
    private void startEnergyReductionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        processSprintEnergyReduction(player);
                    }
                } catch (Exception e) {
                    logger.warning("Error in energy reduction task: " + e.getMessage());
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, ENERGY_REDUCTION_INTERVAL);
    }

    // ==================== ENERGY PROCESSING ====================

    private void processEnergyRegeneration(Player player) {
        if (player == null) return;

        // Check if player is on regeneration cooldown
        if (regenCooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis() - REGEN_DELAY_MS) {
            return; // Still on cooldown
        }

        int currentEnergy = getEnergyValue(player);
        if (currentEnergy >= MAX_ENERGY) {
            return; // Already at max
        }

        // Calculate energy regeneration (same formula as old system)
        float energyRegen = calculateEnergyRegeneration(player);
        int newEnergy = Math.min(MAX_ENERGY, (int) (currentEnergy + energyRegen));

        if (newEnergy != currentEnergy) {
            setEnergyValue(player, newEnergy);
        }
    }

    private void processSprintEnergyReduction(Player player) {
        if (player == null || !player.isSprinting() || shouldSkipEnergyReduction(player)) {
            return;
        }

        int currentEnergy = getEnergyValue(player);
        if (currentEnergy <= 0) {
            return; // Already depleted
        }

        // Calculate energy reduction (same formula as old system)
        float energyReduction = calculateSprintEnergyReduction();
        int newEnergy = Math.max(0, currentEnergy - (int) energyReduction);

        setEnergyValue(player, newEnergy);

        if (newEnergy <= 0) {
            handleEnergyDepletion(player);
        }
    }

    // ==================== ENERGY CALCULATIONS ====================

    private float calculateEnergyRegeneration(Player player) {
        PlayerInventory inventory = player.getInventory();
        float energy = BASE_ENERGY_INCREASE;

        // Check armor pieces for energy bonuses (same logic as old system)
        for (ItemStack armorPiece : inventory.getArmorContents()) {
            if (isValidArmorPiece(armorPiece)) {
                int addedEnergy = getEnergyFromItem(armorPiece);
                int addedIntel = getIntelFromItem(armorPiece);

                if (addedIntel > 0) {
                    addedEnergy += Math.round(addedIntel * ENERGY_INCREASE_PER_INTEL);
                }

                energy += addedEnergy * ENERGY_PER_ARMOR_PIECE;
            }
        }

        return energy / MAX_ENERGY;
    }

    private float calculateSprintEnergyReduction() {
        float energyReduction = BASE_ENERGY_INCREASE + ENERGY_REDUCTION_AMOUNT;
        energyReduction *= ENERGY_REDUCTION_MULTIPLIER;
        return energyReduction / MAX_ENERGY;
    }

    private int calculateWeaponEnergyCost(ItemStack item, boolean isHit) {
        if (item == null || item.getType() == Material.AIR) {
            return isHit ? 6 : 6; // Default values
        }

        String materialName = item.getType().name();

        // This logic is preserved exactly from the original for compatibility.
        if (isHit) {
            // Hit energy costs
            if (materialName.contains("WOOD")) return materialName.contains("AXE") ? 10 : 8;
            if (materialName.contains("STONE")) return materialName.contains("AXE") ? 11 : 10;
            if (materialName.contains("IRON")) return materialName.contains("AXE") ? 12 : 11;
            if (materialName.contains("DIAMOND")) return materialName.contains("SWORD") ? 12 : 13;
            if (materialName.contains("GOLD") || materialName.contains("NETHERITE")) {
                if (materialName.contains("SWORD")) return 13;
                if (materialName.contains("AXE")) return 14;
                return 16;
            }
        } else {
            // Swing energy costs
            if (materialName.contains("WOOD")) return 3;
            if (materialName.contains("STONE")) return materialName.contains("SWORD") ? 3 : 4;
            if (materialName.contains("IRON")) return materialName.contains("SWORD") ? 4 : 6;
            if (materialName.contains("DIAMOND") || materialName.contains("GOLD") || materialName.contains("NETHERITE")) return 8;
        }

        return isHit ? 6 : 6; // Default fallback
    }

    // ==================== HELPER METHODS ====================

    private boolean shouldSkipEnergyReduction(Player player) {
        // Creative mode or safe zone check
        if (player.getGameMode() == GameMode.CREATIVE || isSafeZone(player.getLocation())) {
            return true;
        }

        // Energy disabled check (YakPlayer integration)
        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            return yakPlayer != null && yakPlayer.isEnergyDisabled();
        } catch (Exception e) {
            logger.fine("Error checking energy disabled status: " + e.getMessage());
            return false;
        }
    }

    private boolean isSafeZone(Location location) {
        try {
            return WorldGuardManager.getInstance().isSafeZone(location);
        } catch (Exception e) {
            logger.fine("Error checking safe zone: " + e.getMessage());
            return false; // Fail safe
        }
    }

    private boolean isValidArmorPiece(ItemStack armor) {
        return armor != null && armor.getType() != Material.AIR && armor.hasItemMeta();
    }

    private int getEnergyFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = meta.lore();
        if (lore == null) return 0;

        try {
            for (Component componentLine : lore) {
                // Serialize component to legacy string for backwards compatibility with old item lore
                String line = LegacyComponentSerializer.legacySection().serialize(componentLine);
                if (line.contains("ENERGY REGEN")) {
                    String[] parts = ChatColor.stripColor(line).split(": ");
                    if (parts.length > 1) {
                        return Integer.parseInt(parts[1].replace("%", ""));
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error parsing energy from item: " + e.getMessage());
        }
        return 0;
    }

    private int getIntelFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = meta.lore();
        if (lore == null) return 0;

        try {
            for (Component componentLine : lore) {
                // Serialize component to legacy string for backwards compatibility with old item lore
                String line = LegacyComponentSerializer.legacySection().serialize(componentLine);
                if (line.contains("INT:")) {
                    String[] parts = ChatColor.stripColor(line).split(": ");
                    if (parts.length > 1) {
                        return Integer.parseInt(parts[1]);
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("Error parsing intelligence from item: " + e.getMessage());
        }
        return 0;
    }


    private void handleEnergyDepletion(Player player) {
        // Set regeneration cooldown
        regenCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        // Apply fatigue effects on the main thread
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            try {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.MINING_FATIGUE,
                        FATIGUE_DURATION,
                        FATIGUE_AMPLIFIER
                ), true);
                player.setSprinting(false);

                // Play sound using Adventure API
                final Sound sound = Sound.sound(Key.key("entity.wolf.pant"), Sound.Source.PLAYER, 1.0f, 1.5f);
                player.playSound(sound, Sound.Emitter.self());

            } catch (Exception e) {
                logger.warning("Error applying fatigue effects: " + e.getMessage());
            }
        });

        // Send message to action bar
        sendEnergyDepletedMessage(player);
    }

    private void sendEnergyDepletedMessage(Player player) {
        if (player == null || !player.isOnline()) return;
        player.sendActionBar(ENERGY_DEPLETED_COMPONENT);
    }

    // ==================== EVENT HANDLERS ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Initialize energy after a short delay to ensure YakPlayer is ready
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (player.isOnline()) {
                initializePlayerEnergy(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnergyUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check damage cooldown
        if (damageCooldowns.getOrDefault(uuid, 0L) > System.currentTimeMillis() - DAMAGE_COOLDOWN_MS) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }

        // Calculate and remove energy for swing
        int energyCost = calculateWeaponEnergyCost(player.getInventory().getItemInMainHand(), false);
        if (energyCost > 0 && getEnergyValue(player) > 0) {
            removeEnergy(player, energyCost);
        }

        // Handle energy depletion immediately after use
        if (getEnergyValue(player) <= 0) {
            handleEnergyDepletion(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEnergyUseDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Player player = (Player) event.getDamager();
        UUID uuid = player.getUniqueId();

        // Check damage cooldown
        if (damageCooldowns.getOrDefault(uuid, 0L) > System.currentTimeMillis() - DAMAGE_COOLDOWN_MS) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        // Cancel damage if player is exhausted
        if (player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
            event.setDamage(0.0);
            event.setCancelled(true);
            return;
        }

        damageCooldowns.put(uuid, System.currentTimeMillis());

        // Calculate and remove energy for hit
        if (hasEnergy(player, 1)) {
            int energyCost = calculateWeaponEnergyCost(player.getInventory().getItemInMainHand(), true);
            if (energyCost > 0) {
                removeEnergy(player, energyCost);
            }
        }

        // Handle energy depletion immediately after hit
        if (getEnergyValue(player) <= 0) {
            handleEnergyDepletion(player);
        }
    }

    // ==================== PUBLIC API METHODS (FOR BACKWARDS COMPATIBILITY) ====================

    public int getEnergy(YakPlayer yakPlayer) {
        if (yakPlayer == null) return MAX_ENERGY;
        Player player = yakPlayer.getBukkitPlayer();
        if (player != null) {
            return getEnergyValue(player);
        }
        Object energy = yakPlayer.getTemporaryData("energy");
        if (energy instanceof Integer) {
            return Math.max(0, Math.min(MAX_ENERGY, (Integer) energy));
        }
        return MAX_ENERGY;
    }

    public void setEnergy(YakPlayer yakPlayer, float energy) {
        if (yakPlayer == null) return;
        int clampedEnergy = Math.max(0, Math.min(MAX_ENERGY, (int) energy));
        Player player = yakPlayer.getBukkitPlayer();
        if (player != null) {
            setEnergyValue(player, clampedEnergy);
        } else {
            yakPlayer.setTemporaryData("energy", clampedEnergy);
        }
    }

    public int getEnergyByPlayer(Player player) {
        return getEnergyValue(player);
    }

    public void setEnergyByPlayer(Player player, int energy) {
        setEnergyValue(player, energy);
    }
}
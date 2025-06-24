package com.rednetty.server.mechanics.player.stamina;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.stats.PlayerStatsCalculator;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Enhanced energy/stamina system with improved performance, visual feedback,
 * and better gameplay balance.
 */
public class Energy implements Listener {
    private static Energy instance;
    private final YakPlayerManager playerManager;
    private final Logger logger;

    // Enhanced configuration with balance improvements
    private static final int MAX_ENERGY = 100;
    private static final int MIN_ENERGY = 0;
    private static final float BASE_REGEN_RATE = 2.0f;
    private static final float BASE_SPRINT_COST = 1.5f;
    private static final float BASE_COMBAT_COST = 3.0f;
    private static final long REGEN_DELAY_AFTER_COMBAT = 2000L; // 2 seconds
    private static final long REGEN_DELAY_AFTER_SPRINT = 1000L; // 1 second
    private static final int FATIGUE_DURATION = 60; // 3 seconds in ticks
    private static final int LOW_ENERGY_THRESHOLD = 20;
    private static final int CRITICAL_ENERGY_THRESHOLD = 5;

    // Enhanced timing and intervals
    private static final int ENERGY_UPDATE_INTERVAL = 1; // Every tick for smooth updates
    private static final int SPRINT_DRAIN_INTERVAL = 4; // Every 4 ticks for sprinting
    private static final int DISPLAY_UPDATE_INTERVAL = 5; // Every 5 ticks for UI updates

    // Performance tracking
    private final AtomicInteger totalEnergyOperations = new AtomicInteger(0);
    private final AtomicLong lastPerformanceLog = new AtomicLong(0);

    // Enhanced caching and state management
    private final Map<UUID, Long> energyRegenCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSprintTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cachedEnergyValues = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEnergyUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> lowEnergyWarningShown = new ConcurrentHashMap<>();

    // Enhanced weapon energy costs with better balance
    private final Map<String, WeaponEnergyData> weaponEnergyCosts = new ConcurrentHashMap<>();

    // Task management
    private BukkitTask energyRegenTask;
    private BukkitTask sprintDrainTask;
    private BukkitTask displayUpdateTask;
    private BukkitTask performanceTask;

    /**
     * Enhanced weapon energy data
     */
    private static class WeaponEnergyData {
        final int swingCost;
        final int hitCost;
        final float speedMultiplier;

        WeaponEnergyData(int swingCost, int hitCost, float speedMultiplier) {
            this.swingCost = swingCost;
            this.hitCost = hitCost;
            this.speedMultiplier = speedMultiplier;
        }
    }

    private Energy() {
        this.playerManager = YakPlayerManager.getInstance();
        this.logger = YakRealms.getInstance().getLogger();
        initializeWeaponData();
    }

    public static Energy getInstance() {
        if (instance == null) {
            instance = new Energy();
        }
        return instance;
    }

    /**
     * Initialize weapon energy costs with enhanced balance
     */
    private void initializeWeaponData() {
        // Swords - balanced for versatility
        weaponEnergyCosts.put("WOODEN_SWORD", new WeaponEnergyData(2, 6, 1.0f));
        weaponEnergyCosts.put("STONE_SWORD", new WeaponEnergyData(3, 7, 1.0f));
        weaponEnergyCosts.put("IRON_SWORD", new WeaponEnergyData(3, 8, 1.0f));
        weaponEnergyCosts.put("DIAMOND_SWORD", new WeaponEnergyData(4, 9, 1.0f));
        weaponEnergyCosts.put("NETHERITE_SWORD", new WeaponEnergyData(4, 10, 1.0f));
        weaponEnergyCosts.put("GOLDEN_SWORD", new WeaponEnergyData(2, 8, 1.2f));

        // Axes - higher damage, higher cost
        weaponEnergyCosts.put("WOODEN_AXE", new WeaponEnergyData(4, 10, 0.8f));
        weaponEnergyCosts.put("STONE_AXE", new WeaponEnergyData(4, 11, 0.8f));
        weaponEnergyCosts.put("IRON_AXE", new WeaponEnergyData(5, 12, 0.8f));
        weaponEnergyCosts.put("DIAMOND_AXE", new WeaponEnergyData(5, 13, 0.8f));
        weaponEnergyCosts.put("NETHERITE_AXE", new WeaponEnergyData(6, 14, 0.8f));
        weaponEnergyCosts.put("GOLDEN_AXE", new WeaponEnergyData(4, 12, 1.0f));

        // Tools as weapons - lower efficiency
        weaponEnergyCosts.put("WOODEN_SHOVEL", new WeaponEnergyData(3, 8, 0.9f));
        weaponEnergyCosts.put("STONE_SHOVEL", new WeaponEnergyData(3, 8, 0.9f));
        weaponEnergyCosts.put("IRON_SHOVEL", new WeaponEnergyData(4, 9, 0.9f));
        weaponEnergyCosts.put("DIAMOND_SHOVEL", new WeaponEnergyData(4, 10, 0.9f));
        weaponEnergyCosts.put("NETHERITE_SHOVEL", new WeaponEnergyData(5, 11, 0.9f));

        // Hoes as staves - magic weapons
        weaponEnergyCosts.put("WOODEN_HOE", new WeaponEnergyData(3, 7, 1.1f));
        weaponEnergyCosts.put("STONE_HOE", new WeaponEnergyData(3, 8, 1.1f));
        weaponEnergyCosts.put("IRON_HOE", new WeaponEnergyData(4, 9, 1.1f));
        weaponEnergyCosts.put("DIAMOND_HOE", new WeaponEnergyData(4, 10, 1.1f));
        weaponEnergyCosts.put("NETHERITE_HOE", new WeaponEnergyData(5, 11, 1.1f));

        logger.info("Initialized energy costs for " + weaponEnergyCosts.size() + " weapon types");
    }

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        startEnhancedTasks();
        YakRealms.log("Enhanced Energy system has been enabled.");
    }

    public void onDisable() {
        stopAllTasks();
        clearAllCaches();
        YakRealms.log("Enhanced Energy system has been disabled.");
    }

    /**
     * Start enhanced task system with better performance
     */
    private void startEnhancedTasks() {
        // Energy regeneration task - smooth and frequent
        energyRegenTask = new BukkitRunnable() {
            @Override
            public void run() {
                processEnergyRegeneration();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, ENERGY_UPDATE_INTERVAL);

        // Sprint energy drain task
        sprintDrainTask = new BukkitRunnable() {
            @Override
            public void run() {
                processSprintEnergyDrain();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 0, SPRINT_DRAIN_INTERVAL);

        // Display update task - less frequent to reduce overhead
        displayUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateEnergyDisplays();
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, DISPLAY_UPDATE_INTERVAL);

        // Performance monitoring task
        performanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                logPerformanceMetrics();
            }
        }.runTaskTimerAsynchronously(YakRealms.getInstance(), 20 * 60, 20 * 60); // Every minute

        logger.info("Started enhanced energy task system");
    }

    /**
     * Process energy regeneration with enhanced logic
     */
    private void processEnergyRegeneration() {
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldProcessPlayer(player)) continue;

            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) continue;

            UUID uuid = player.getUniqueId();

            // Check regeneration cooldowns
            if (isInRegenCooldown(uuid, currentTime)) continue;

            // Calculate current energy
            int currentEnergy = getEnergy(yakPlayer);
            if (currentEnergy >= MAX_ENERGY) continue;

            // Calculate regeneration rate
            float regenRate = calculateRegenRate(player, yakPlayer);

            // Apply regeneration
            float newEnergy = Math.min(MAX_ENERGY, currentEnergy + regenRate);
            setEnergy(yakPlayer, newEnergy);

            // Clear low energy warning if recovered
            if (currentEnergy < LOW_ENERGY_THRESHOLD && newEnergy >= LOW_ENERGY_THRESHOLD) {
                lowEnergyWarningShown.remove(uuid);
            }

            totalEnergyOperations.incrementAndGet();
        }
    }

    /**
     * Process sprint energy drain with smooth consumption
     */
    private void processSprintEnergyDrain() {
        long currentTime = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldProcessPlayer(player) || !player.isSprinting()) continue;

            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) continue;

            UUID uuid = player.getUniqueId();
            int currentEnergy = getEnergy(yakPlayer);

            if (currentEnergy <= MIN_ENERGY) {
                handleEnergyDepletion(player, yakPlayer);
                continue;
            }

            // Calculate sprint cost with modifiers
            float sprintCost = calculateSprintCost(player, yakPlayer);

            // Apply sprint drain
            float newEnergy = Math.max(MIN_ENERGY, currentEnergy - sprintCost);
            setEnergy(yakPlayer, newEnergy);

            // Update last sprint time
            lastSprintTime.put(uuid, currentTime);

            // Handle low energy warnings
            handleLowEnergyWarnings(player, (int) newEnergy);

            totalEnergyOperations.incrementAndGet();
        }
    }

    /**
     * Update energy displays for all players
     */
    private void updateEnergyDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) continue;

            updatePlayerEnergyDisplay(player, yakPlayer);
        }
    }

    /**
     * Calculate enhanced regeneration rate
     */
    private float calculateRegenRate(Player player, YakPlayer yakPlayer) {
        float baseRegen = BASE_REGEN_RATE;

        // Equipment bonuses
        float equipmentBonus = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (PlayerStatsCalculator.hasValidItemStats(armor)) {
                equipmentBonus += getEnergyRegenFromItem(armor);

                // Intelligence bonus
                int intelligence = PlayerStatsCalculator.getElementalAttribute(armor, "INT");
                equipmentBonus += intelligence * 0.02f;
            }
        }

        // Player level bonus
        int playerLevel = yakPlayer.getLevel();
        float levelBonus = playerLevel * 0.1f;

        // Resting bonus (not moving)
        float restingBonus = 0;
        if (!player.isSprinting() && player.getVelocity().lengthSquared() < 0.01) {
            restingBonus = baseRegen * 0.5f; // 50% bonus when resting
        }

        return baseRegen + equipmentBonus + levelBonus + restingBonus;
    }

    /**
     * Calculate enhanced sprint cost
     */
    private float calculateSprintCost(Player player, YakPlayer yakPlayer) {
        float baseCost = BASE_SPRINT_COST;

        // Armor weight penalty
        float armorPenalty = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType().name().contains("_")) {
                String materialName = armor.getType().name();
                if (materialName.contains("DIAMOND") || materialName.contains("NETHERITE")) {
                    armorPenalty += 0.3f;
                } else if (materialName.contains("IRON")) {
                    armorPenalty += 0.2f;
                } else if (materialName.contains("CHAIN")) {
                    armorPenalty += 0.1f;
                }
            }
        }

        // Dexterity bonus (reduces cost)
        int dexterity = PlayerStatsCalculator.calculateTotalAttribute(player, "DEX");
        float dexterityBonus = dexterity * 0.01f; // 1% reduction per dexterity point

        return Math.max(0.5f, baseCost + armorPenalty - dexterityBonus);
    }

    /**
     * Handle energy depletion with enhanced effects
     */
    private void handleEnergyDepletion(Player player, YakPlayer yakPlayer) {
        UUID uuid = player.getUniqueId();

        // Force stop sprinting
        player.setSprinting(false);

        // Apply enhanced fatigue effect
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_DIGGING, FATIGUE_DURATION, 3, false, true
            ));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW, FATIGUE_DURATION / 2, 1, false, true
            ));
        });

        // Enhanced audio and visual feedback
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, 0.7f, 0.8f);

        // Set regeneration cooldown
        energyRegenCooldowns.put(uuid, System.currentTimeMillis() + REGEN_DELAY_AFTER_COMBAT);

        // Action bar message
        if (ActionBarUtil.class != null) {
            ActionBarUtil.addTemporaryMessage(player, "§c§lExhausted! §7Rest to recover energy...", 60L);
        } else {
            player.sendMessage("§c§lExhausted! §7Rest to recover energy...");
        }
    }

    /**
     * Handle low energy warnings
     */
    private void handleLowEnergyWarnings(Player player, int energy) {
        UUID uuid = player.getUniqueId();

        if (energy <= CRITICAL_ENERGY_THRESHOLD && !lowEnergyWarningShown.getOrDefault(uuid, false)) {
            player.sendMessage("§c§l⚠ CRITICAL ENERGY! §7Stop sprinting to recover!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
            lowEnergyWarningShown.put(uuid, true);
        } else if (energy <= LOW_ENERGY_THRESHOLD && !lowEnergyWarningShown.getOrDefault(uuid, false)) {
            player.sendMessage("§e§l⚠ Low Energy! §7Consider resting soon.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
            lowEnergyWarningShown.put(uuid, true);
        }
    }

    /**
     * Enhanced energy display update
     */
    private void updatePlayerEnergyDisplay(Player player, YakPlayer yakPlayer) {
        if (!player.isOnline()) return;

        int energy = getEnergy(yakPlayer);

        // Use experience bar for energy display
        float expBar = (float) energy / MAX_ENERGY;
        int expLevel = energy;

        // Color-coded level display
        if (energy <= CRITICAL_ENERGY_THRESHOLD) {
            // Red for critical
            expLevel = energy;
        } else if (energy <= LOW_ENERGY_THRESHOLD) {
            // Yellow for low
            expLevel = energy;
        } else {
            // Normal display
            expLevel = energy;
        }

        int finalExpLevel = expLevel;
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            player.setExp(expBar);
            player.setLevel(finalExpLevel);
        });
    }

    /**
     * Enhanced weapon energy calculation
     */
    private int calculateWeaponEnergyCost(ItemStack weapon, boolean isHit) {
        if (weapon == null) return isHit ? 6 : 3;

        String weaponType = weapon.getType().name();
        WeaponEnergyData weaponData = weaponEnergyCosts.get(weaponType);

        if (weaponData == null) {
            // Default costs for unknown weapons
            return isHit ? 6 : 3;
        }

        return isHit ? weaponData.hitCost : weaponData.swingCost;
    }

    /**
     * Enhanced energy management methods
     */
    public int getEnergy(YakPlayer yakPlayer) {
        Object energy = yakPlayer.getTemporaryData("energy");
        if (energy instanceof Integer) {
            return (Integer) energy;
        }
        return MAX_ENERGY; // Default to full energy
    }

    public void setEnergy(YakPlayer yakPlayer, float energy) {
        int clampedEnergy = Math.max(MIN_ENERGY, Math.min(MAX_ENERGY, (int) energy));
        yakPlayer.setTemporaryData("energy", clampedEnergy);

        // Cache the value for performance
        UUID uuid = yakPlayer.getUUID();
        if (uuid != null) {
            cachedEnergyValues.put(uuid, clampedEnergy);
            lastEnergyUpdate.put(uuid, System.currentTimeMillis());
        }
    }

    public void removeEnergy(Player player, int amount) {
        if (amount <= 0) return;

        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        // Skip if energy is disabled or in creative mode
        if (yakPlayer.isToggled("Energy Disabled") || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Rate limiting
        Long lastUpdate = lastEnergyUpdate.get(uuid);
        if (lastUpdate != null && currentTime - lastUpdate < 50) { // 50ms limit
            return;
        }

        // Apply energy reduction
        int currentEnergy = getEnergy(yakPlayer);
        int newEnergy = Math.max(MIN_ENERGY, currentEnergy - amount);
        setEnergy(yakPlayer, newEnergy);

        // Set regeneration cooldown
        energyRegenCooldowns.put(uuid, currentTime + REGEN_DELAY_AFTER_COMBAT);
        lastCombatTime.put(uuid, currentTime);

        // Handle depletion
        if (newEnergy <= MIN_ENERGY) {
            handleEnergyDepletion(player, yakPlayer);
        }

        totalEnergyOperations.incrementAndGet();
    }

    // Event handlers with enhanced functionality

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null) return;

        // Calculate and apply energy cost
        int energyCost = calculateWeaponEnergyCost(weapon, false);
        removeEnergy(player, energyCost);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }

        Player player = (Player) event.getDamager();
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        int energyCost = calculateWeaponEnergyCost(weapon, true);

        removeEnergy(player, energyCost);
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (event.isSprinting()) {
            lastSprintTime.put(uuid, System.currentTimeMillis());
        } else {
            // Set regeneration delay when stopping sprint
            energyRegenCooldowns.put(uuid, System.currentTimeMillis() + REGEN_DELAY_AFTER_SPRINT);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        YakPlayer yakPlayer = playerManager.getPlayer(player);

        if (yakPlayer != null) {
            // Initialize energy display
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                updatePlayerEnergyDisplay(player, yakPlayer);
            }, 10L);
        }
    }

    // Utility methods

    private boolean shouldProcessPlayer(Player player) {
        return player != null && player.isOnline() &&
                player.getGameMode() != GameMode.CREATIVE &&
                player.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean isInRegenCooldown(UUID uuid, long currentTime) {
        Long cooldownTime = energyRegenCooldowns.get(uuid);
        return cooldownTime != null && currentTime < cooldownTime;
    }

    private int getEnergyRegenFromItem(ItemStack item) {
        if (!PlayerStatsCalculator.hasValidItemStats(item)) return 0;

        for (String line : item.getItemMeta().getLore()) {
            if (line.contains("ENERGY REGEN")) {
                try {
                    return Integer.parseInt(line.split(": ")[1].split("%")[0]);
                } catch (Exception e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void logPerformanceMetrics() {
        long currentTime = System.currentTimeMillis();
        long lastLog = lastPerformanceLog.getAndSet(currentTime);

        if (lastLog > 0) {
            int operations = totalEnergyOperations.getAndSet(0);
            long timeDiff = currentTime - lastLog;

            if (operations > 0) {
                double opsPerSecond = (operations * 1000.0) / timeDiff;
                logger.info("Energy System Performance: " + String.format("%.1f", opsPerSecond) + " ops/sec, " +
                        "Active players: " + Bukkit.getOnlinePlayers().size() +
                        ", Cache size: " + cachedEnergyValues.size());
            }
        }
    }

    private void stopAllTasks() {
        if (energyRegenTask != null && !energyRegenTask.isCancelled()) {
            energyRegenTask.cancel();
        }
        if (sprintDrainTask != null && !sprintDrainTask.isCancelled()) {
            sprintDrainTask.cancel();
        }
        if (displayUpdateTask != null && !displayUpdateTask.isCancelled()) {
            displayUpdateTask.cancel();
        }
        if (performanceTask != null && !performanceTask.isCancelled()) {
            performanceTask.cancel();
        }
    }

    private void clearAllCaches() {
        energyRegenCooldowns.clear();
        lastSprintTime.clear();
        lastCombatTime.clear();
        cachedEnergyValues.clear();
        lastEnergyUpdate.clear();
        lowEnergyWarningShown.clear();
    }

    // Public API methods

    public boolean hasEnergy(Player player, int amount) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && getEnergy(yakPlayer) >= amount;
    }

    public void restoreEnergy(Player player, int amount) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        int currentEnergy = getEnergy(yakPlayer);
        setEnergy(yakPlayer, currentEnergy + amount);

        player.sendMessage("§a§l+ " + amount + " Energy!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    public float getEnergyPercentage(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return 1.0f;

        return (float) getEnergy(yakPlayer) / MAX_ENERGY;
    }

    public boolean isEnergyDepleted(Player player) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        return yakPlayer != null && getEnergy(yakPlayer) <= MIN_ENERGY;
    }

    public void setEnergyEnabled(Player player, boolean enabled) {
        YakPlayer yakPlayer = playerManager.getPlayer(player);
        if (yakPlayer == null) return;

        yakPlayer.setEnergyDisabled(!enabled);

        if (enabled) {
            setEnergy(yakPlayer, MAX_ENERGY);
            player.sendMessage("§a§lEnergy system enabled!");
        } else {
            player.sendMessage("§c§lEnergy system disabled!");
        }
    }
}
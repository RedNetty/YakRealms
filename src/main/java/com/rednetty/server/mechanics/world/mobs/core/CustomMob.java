package com.rednetty.server.mechanics.world.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.drops.DropsManager;
import com.rednetty.server.mechanics.world.mobs.CritManager;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import com.rednetty.server.mechanics.world.holograms.HologramManager;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * CustomMob class with simplified, reliable spawning and enhanced hologram system
 * FIXED: Hologram system now prevents trails and updates efficiently
 * - Equipment never takes durability damage (unbreakable)
 * - Mobs never burn in sunlight
 * - All entity operations properly validated for main thread
 * - Fixed entity creation issues that caused "naked mob" fallbacks
 * - Enhanced hologram system with position tracking to prevent trails
 * - Optimized hologram updates to reduce lag
 */
@Getter
public class CustomMob {

    protected static final Random RANDOM = new Random();
    protected static final Logger LOGGER = YakRealms.getInstance().getLogger();
    protected static final long NAME_VISIBILITY_TIMEOUT = 6500; // 6.5 seconds

    // Make these package-private so MobManager can access them directly
    public long lastDamageTime = 0;
    public boolean nameVisible = true;

    // ================ STATE VARIABLES ================
    protected LivingEntity entity;
    protected int lightningMultiplier = 0;
    private boolean showingHealthBar = false;

    // ================ CORE PROPERTIES ================
    protected final MobType type;
    protected final int tier;
    protected final boolean elite;

    // ================ NAME SYSTEM ================
    private String originalName; // Proper tier-specific name
    protected String currentDisplayName; // What's currently shown
    private boolean originalNameStored = false;

    // ================ FIXED HOLOGRAM SYSTEM ================
    private String hologramId;
    private boolean hologramActive = false;
    private double lastHologramHealth = -1;
    private double lastHologramMaxHealth = -1;
    private Location lastHologramLocation; // Track last hologram position
    private List<String> lastHologramLines; // Track last hologram content
    private long lastHologramUpdate = 0; // Track last update time
    private static final long HOLOGRAM_UPDATE_INTERVAL = 2; // Update every 2 ticks (10 times per second)
    private static final double POSITION_UPDATE_THRESHOLD = 0.15; // Only update if moved more than 0.15 blocks

    // ================ HEALTH SYSTEM ================
    private double baseHealth = 0;

    /**
     * Immediate health bar refresh method for external calls
     */
    public void refreshHealthBar() {
        if (!isValid()) return;

        // Force immediate update with current health
        Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
            if (isValid()) {
                updateHealthBar();
                forceUpdateHologram(); // Force hologram update
            }
        });
    }

    /**
     * FIXED: Force update hologram regardless of timing constraints
     */
    public void forceUpdateHologram() {
        lastHologramUpdate = 0; // Reset timing to force update
        updateHologramOverlay();
    }

    /**
     * Get current health percentage (useful for debugging)
     */
    public double getHealthPercentage() {
        if (!isValid()) return 0.0;

        try {
            double current = entity.getHealth();
            double max = entity.getMaxHealth();

            if (max <= 0) return 0.0;
            return (current / max) * 100.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Force health bar update with specific values (for special cases)
     */
    public void updateHealthBarWithValues(double health, double maxHealth) {
        if (!isValid()) return;

        try {
            String healthBar = generateHealthBar(health, maxHealth);
            if (!hologramActive) {
                entity.setCustomName(healthBar);
                entity.setCustomNameVisible(true);
            }
            currentDisplayName = healthBar;
            showingHealthBar = true;
            lastDamageTime = System.currentTimeMillis();

            // Force update hologram as well
            forceUpdateHologram();
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Forced health bar update failed: " + e.getMessage());
        }
    }

    /**
     * Create a new custom mob with basic validation
     */
    public CustomMob(MobType type, int tier, boolean elite) {
        if (type == null) {
            throw new IllegalArgumentException("MobType cannot be null");
        }

        this.type = type;
        this.tier = Math.max(1, Math.min(6, tier));
        this.elite = elite;

        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("[CustomMob] Creating %s mob: %s T%d%s",
                    elite ? "elite" : "normal", type.getId(), this.tier, elite ? "+" : ""));
        }
    }

    // ================ LIFECYCLE SYSTEM ================

    /**
     * Main tick method called every game tick for active mobs
     * FIXED: Now includes optimized hologram updates
     */
    public void tick() {
        if (!isValid()) return;

        try {
            updateNameVisibility();
            preventSunlightBurning(); // Ensure mobs don't burn in sunlight
            updateHologramOverlayOptimized(); // FIXED: Optimized hologram updates
            // Subclasses can override to add specific behavior
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Tick error for " + type.getId() + ": " + e.getMessage());
        }
    }

    // ================ FIXED HOLOGRAM SYSTEM ================

    /**
     * FIXED: Optimized hologram overlay system that prevents trails and reduces lag
     */
    private void updateHologramOverlayOptimized() {
        if (!isValid()) return;

        long currentTime = System.currentTimeMillis();

        // Throttle updates to reduce lag (update at most every HOLOGRAM_UPDATE_INTERVAL ticks)
        if (currentTime - lastHologramUpdate < (HOLOGRAM_UPDATE_INTERVAL * 50)) { // 50ms per tick
            return;
        }

        try {
            if (hologramId == null) {
                hologramId = "mob_" + entity.getUniqueId().toString();
            }

            // Get current values
            Location currentLocation = entity.getLocation().add(0, entity.getHeight() + 0.4, 0);
            double currentHealth = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            // Check if significant changes occurred
            boolean positionChanged = hasSignificantPositionChange(currentLocation);
            boolean healthChanged = hasSignificantHealthChange(currentHealth, maxHealth);
            boolean contentChanged = hasContentChanged(currentHealth, maxHealth);

            // Only update if there are significant changes
            if (!positionChanged && !healthChanged && !contentChanged) {
                return;
            }

            List<String> hologramLines = createHologramLines(currentHealth, maxHealth);

            // Use efficient update method
            boolean updated = HologramManager.updateHologramEfficiently(hologramId, currentLocation, hologramLines, 0.3);

            if (updated) {
                // Hide entity name when hologram is active
                entity.setCustomNameVisible(false);
                hologramActive = true;

                // Store last values
                lastHologramLocation = currentLocation.clone();
                lastHologramHealth = currentHealth;
                lastHologramMaxHealth = maxHealth;
                lastHologramLines = new ArrayList<>(hologramLines);
                lastHologramUpdate = currentTime;

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.fine("[CustomMob] Updated hologram for " + type.getId() +
                            " - Pos: " + positionChanged + ", Health: " + healthChanged + ", Content: " + contentChanged);
                }
            }

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Hologram update failed: " + e.getMessage());
        }
    }

    /**
     * FIXED: Check if position has changed significantly
     */
    private boolean hasSignificantPositionChange(Location currentLocation) {
        if (lastHologramLocation == null) {
            return true; // First time, always update
        }

        if (!lastHologramLocation.getWorld().equals(currentLocation.getWorld())) {
            return true; // World changed
        }

        double distance = lastHologramLocation.distance(currentLocation);
        return distance > POSITION_UPDATE_THRESHOLD;
    }

    /**
     * FIXED: Check if health has changed significantly
     */
    private boolean hasSignificantHealthChange(double currentHealth, double maxHealth) {
        return Math.abs(currentHealth - lastHologramHealth) > 0.1 ||
                Math.abs(maxHealth - lastHologramMaxHealth) > 0.1;
    }

    /**
     * FIXED: Check if hologram content has changed
     */
    private boolean hasContentChanged(double currentHealth, double maxHealth) {
        if (lastHologramLines == null) {
            return true; // First time
        }

        List<String> newLines = createHologramLines(currentHealth, maxHealth);
        return !newLines.equals(lastHologramLines);
    }

    /**
     * DEPRECATED: Use updateHologramOverlayOptimized instead
     */
    public void updateHologramOverlay() {
        updateHologramOverlayOptimized();
    }

    /**
     * Create hologram lines with enhanced visual design
     */
    private List<String> createHologramLines(double currentHealth, double maxHealth) {
        List<String> lines = new ArrayList<>();

        try {
            // Line 1: Mob name with tier color
            String mobName = getTierColoredName();
            lines.add(mobName);

            // Line 2: Visual health bar
            String healthBar = createVisualHealthBar(currentHealth, maxHealth);
            lines.add(healthBar);

            // Line 3: Elite/Tier status (if applicable)
            if (elite || isInCriticalState()) {
                String statusLine = createStatusLine();
                if (!statusLine.isEmpty()) {
                    lines.add(statusLine);
                }
            }

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Error creating hologram lines: " + e.getMessage());
            // Fallback lines
            lines.add(ChatColor.WHITE + getCustomName());
            lines.add(ChatColor.RED + "Health: " + (int)currentHealth + "/" + (int)maxHealth);
        }

        return lines;
    }

    /**
     * Get mob name with appropriate tier coloring
     */
    private String getTierColoredName() {
        try {
            String baseName = getCleanMobName();
            ChatColor tierColor = getTierColor();

            if (elite) {
                return tierColor.toString() + ChatColor.BOLD + baseName;
            } else {
                return tierColor + baseName;
            }
        } catch (Exception e) {
            return ChatColor.WHITE + type.getId();
        }
    }

    /**
     * Get clean mob name without tier indicators
     */
    private String getCleanMobName() {
        try {
            String name = type.getTierSpecificName(tier);
            // Remove any existing color codes and tier indicators
            name = ChatColor.stripColor(name);
            // Remove tier patterns like "T1", "T2", etc.
            name = name.replaceAll("\\s*T[1-6]\\+?\\s*", "").trim();
            return name;
        } catch (Exception e) {
            return type.getDefaultName();
        }
    }

    /**
     * Get tier-appropriate color
     */
    private ChatColor getTierColor() {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.GOLD;
            default: return ChatColor.WHITE;
        }
    }

    /**
     * Create visual health bar with colors and design
     */
    private String createVisualHealthBar(double currentHealth, double maxHealth) {
        try {
            if (maxHealth <= 0) {
                return ChatColor.RED + "[||||||||||||||||||||||||] " + ChatColor.WHITE + "0%";
            }

            double healthPercentage = (currentHealth / maxHealth) * 100.0;
            int barLength = 24; // Total bar length
            int filledBars = (int) Math.round((currentHealth / maxHealth) * barLength);
            filledBars = Math.max(0, Math.min(barLength, filledBars));
            int emptyBars = barLength - filledBars;

            // Determine health bar color based on percentage
            ChatColor barColor = getHealthBarColor(healthPercentage);

            // Build the visual bar
            StringBuilder healthBar = new StringBuilder();
            healthBar.append(ChatColor.GRAY).append("[");

            // Filled portion
            if (filledBars > 0) {
                healthBar.append(barColor);
                for (int i = 0; i < filledBars; i++) {
                    healthBar.append("|");
                }
            }

            // Empty portion
            if (emptyBars > 0) {
                healthBar.append(ChatColor.DARK_GRAY);
                for (int i = 0; i < emptyBars; i++) {
                    healthBar.append("|");
                }
            }

            healthBar.append(ChatColor.GRAY).append("] ");

            // Add percentage
            ChatColor percentColor = getHealthPercentageColor(healthPercentage);
            healthBar.append(percentColor).append(String.format("%.1f%%", healthPercentage));

            return healthBar.toString();

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Error creating visual health bar: " + e.getMessage());
            return ChatColor.RED + "[ERROR] Health Bar";
        }
    }

    /**
     * Get health bar color based on health percentage
     */
    private ChatColor getHealthBarColor(double percentage) {
        if (isInCriticalState()) {
            return ChatColor.DARK_PURPLE; // Purple for crit state
        } else if (percentage > 75) {
            return ChatColor.GREEN;
        } else if (percentage > 50) {
            return ChatColor.YELLOW;
        } else if (percentage > 25) {
            return ChatColor.GOLD;
        } else {
            return ChatColor.RED;
        }
    }

    /**
     * Get health percentage text color
     */
    private ChatColor getHealthPercentageColor(double percentage) {
        if (isInCriticalState()) {
            return ChatColor.LIGHT_PURPLE;
        } else if (percentage > 60) {
            return ChatColor.GREEN;
        } else if (percentage > 30) {
            return ChatColor.YELLOW;
        } else {
            return ChatColor.RED;
        }
    }

    /**
     * Create status line for elite/special states
     */
    private String createStatusLine() {
        try {
            List<String> statusParts = new ArrayList<>();

            // Elite status
            if (elite) {
                statusParts.add(ChatColor.GOLD + "★ ELITE ★");
            }

            // Critical state
            if (isInCriticalState()) {
                if (isCritReadyToAttack()) {
                    statusParts.add(ChatColor.DARK_PURPLE + "⚡ CHARGED ⚡");
                } else {
                    int countdown = getCritCountdown();
                    if (countdown > 0) {
                        statusParts.add(ChatColor.LIGHT_PURPLE + "⚠ CRITICAL " + countdown + " ⚠");
                    }
                }
            }

            // Lightning mob
            if (lightningMultiplier > 0) {
                statusParts.add(ChatColor.YELLOW + "⚡ LIGHTNING x" + lightningMultiplier + " ⚡");
            }

            return String.join(" ", statusParts);

        } catch (Exception e) {
            return "";
        }
    }

    /**
     * FIXED: Remove hologram when mob is removed - ensures complete cleanup
     */
    public void removeHologram() {
        try {
            if (hologramId != null) {
                HologramManager.removeHologram(hologramId);
                hologramActive = false;
                lastHologramLocation = null;
                lastHologramLines = null;
                lastHologramHealth = -1;
                lastHologramMaxHealth = -1;
                lastHologramUpdate = 0;

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info("[CustomMob] Removed hologram for " + type.getId() + " (ID: " + hologramId + ")");
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Error removing hologram: " + e.getMessage());
        }
    }

    // ================ SIMPLIFIED SPAWNING SYSTEM ================

    /**
     * Simplified spawn method that focuses on success over perfection
     */
    public boolean spawn(Location location) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            LOGGER.severe("[CustomMob] CRITICAL: spawn() called from async thread!");
            return false;
        }

        try {
            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("[CustomMob] Starting spawn for %s T%d%s at %s",
                        type.getId(), tier, elite ? "+" : "", formatLocation(location)));
            }

            // Step 1: Get spawn location (with simple validation)
            Location spawnLoc = getSpawnLocation(location);
            if (spawnLoc == null) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.warning("[CustomMob] No spawn location available");
                }
                return false;
            }

            // Step 2: Create entity (simplified with multiple fallbacks)
            entity = createEntity(spawnLoc);
            if (entity == null) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.warning("[CustomMob] Entity creation failed");
                }
                return false;
            }

            // Step 3: Initialize mob (basic setup only)
            if (!initializeMob()) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.warning("[CustomMob] Mob initialization failed");
                }
                cleanup();
                return false;
            }

            // Step 4: Register with manager
            try {
                MobManager.getInstance().registerMob(this);
            } catch (Exception e) {
                // Don't fail spawn for registration issues
                LOGGER.warning("[CustomMob] Registration warning: " + e.getMessage());
            }

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("[CustomMob] Successfully spawned %s T%d%s with ID: %s",
                        type.getId(), tier, elite ? "+" : "",
                        entity.getUniqueId().toString().substring(0, 8)));
            }

            return true;

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Spawn error for " + type.getId() + ": " + e.getMessage());
            if (YakRealms.getInstance().isDebugMode()) {
                e.printStackTrace();
            }
            cleanup();
            return false;
        }
    }

    /**
     * SIMPLIFIED: Get spawn location with basic validation only
     */
    private Location getSpawnLocation(Location baseLocation) {
        if (baseLocation == null || baseLocation.getWorld() == null) {
            return null;
        }

        // Try the base location first
        if (isBasicSafeLocation(baseLocation)) {
            return baseLocation.clone().add(0.5, 0, 0.5);
        }

        // Try a few nearby locations
        for (int attempts = 0; attempts < 5; attempts++) {
            Location candidate = baseLocation.clone().add(
                    (RANDOM.nextDouble() * 4 - 2),
                    (RANDOM.nextDouble() * 2),
                    (RANDOM.nextDouble() * 4 - 2)
            );

            if (isBasicSafeLocation(candidate)) {
                return candidate;
            }
        }

        // Final fallback - just use a safe Y offset
        return baseLocation.clone().add(0.5, 2, 0.5);
    }

    /**
     * SIMPLIFIED: Basic safety check (not overly strict)
     */
    private boolean isBasicSafeLocation(Location loc) {
        try {
            if (loc == null || loc.getWorld() == null) {
                return false;
            }

            // Just check Y bounds and basic block types
            if (loc.getY() < 0 || loc.getY() > loc.getWorld().getMaxHeight() - 3) {
                return false;
            }

            // Check if current block is obviously bad
            Material currentBlock = loc.getBlock().getType();
            if (currentBlock == Material.LAVA || currentBlock == Material.BEDROCK) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return true; // Default to safe if check fails
        }
    }

    /**
     * SIMPLIFIED: Entity creation with guaranteed fallbacks
     */
    protected LivingEntity createEntity(Location location) {
        try {
            World world = location.getWorld();
            if (world == null) {
                return null;
            }

            // Get EntityType with bulletproof fallback
            EntityType entityType = getEntityType();
            if (entityType == null) {
                return null;
            }

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("[CustomMob] Creating entity type %s at %s",
                        entityType, formatLocation(location)));
            }

            // Attempt entity creation with multiple tries
            LivingEntity createdEntity = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Entity spawnedEntity = world.spawnEntity(location, entityType);

                    if (spawnedEntity instanceof LivingEntity) {
                        createdEntity = (LivingEntity) spawnedEntity;

                        // Basic validation
                        if (createdEntity.isValid() && !createdEntity.isDead()) {
                            break; // Success!
                        } else {
                            createdEntity.remove();
                            createdEntity = null;
                        }
                    } else if (spawnedEntity != null) {
                        spawnedEntity.remove();
                    }
                } catch (Exception e) {
                    if (YakRealms.getInstance().isDebugMode()) {
                        LOGGER.warning("[CustomMob] Entity creation attempt " + attempt + " failed: " + e.getMessage());
                    }
                }

                // Small delay between attempts
                if (attempt < 3) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (createdEntity != null && YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("[CustomMob] Successfully created entity: %s (ID: %s)",
                        createdEntity.getType(), createdEntity.getUniqueId().toString().substring(0, 8)));
            }

            return createdEntity;

        } catch (Exception e) {
            LOGGER.warning(String.format("[CustomMob] Critical error creating entity for %s: %s",
                    type.getId(), e.getMessage()));
            return null;
        }
    }

    /**
     * BULLETPROOF: Get EntityType with guaranteed fallback
     */
    private EntityType getEntityType() {
        try {
            // Primary: use MobType system
            EntityType entityType = type.getEntityType();
            if (entityType != null && isValidEntityType(entityType)) {
                return entityType;
            }

            // Fallback: use type-based mapping
            EntityType fallbackType = getFallbackEntityType();
            if (fallbackType != null && isValidEntityType(fallbackType)) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info(String.format("[CustomMob] Using fallback EntityType %s for %s",
                            fallbackType, type.getId()));
                }
                return fallbackType;
            }

            // Final fallback: skeleton (always works)
            return EntityType.SKELETON;

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Error getting EntityType: " + e.getMessage());
            return EntityType.SKELETON; // Safe fallback
        }
    }

    /**
     * Get fallback EntityType based on mob type ID
     */
    private EntityType getFallbackEntityType() {
        String typeId = type.getId().toLowerCase();

        switch (typeId) {
            case "skeleton":
                return EntityType.SKELETON;
            case "witherskeleton":
            case "wither_skeleton":
                return EntityType.WITHER_SKELETON;
            case "zombie":
                return EntityType.ZOMBIE;
            case "spider":
                return EntityType.SPIDER;
            case "cavespider":
            case "cave_spider":
                return EntityType.CAVE_SPIDER;
            case "magmacube":
            case "magma_cube":
                return EntityType.MAGMA_CUBE;
            case "zombifiedpiglin":
            case "pigzombie":
                return EntityType.ZOMBIFIED_PIGLIN;
            case "enderman":
                return EntityType.ENDERMAN;
            case "creeper":
                return EntityType.CREEPER;
            case "blaze":
                return EntityType.BLAZE;
            case "warden":
                return EntityType.WARDEN;
            case "golem":
            case "irongolem":
                return EntityType.IRON_GOLEM;
            case "imp":
                return EntityType.ZOMBIE; // Imp is a baby zombie
            case "frozenboss":
            case "frozenelite":
            case "bossskeleton":
            case "bossSkeletonDungeon":
                return EntityType.WITHER_SKELETON;
            case "frozengolem":
                return EntityType.IRON_GOLEM;
            case "frostwing":
                return EntityType.PHANTOM;
            case "chronos":
                return EntityType.WITHER;
            default:
                // Default to skeleton for unknown types
                return EntityType.SKELETON;
        }
    }

    /**
     * Validate that an EntityType is usable
     */
    private boolean isValidEntityType(EntityType entityType) {
        try {
            if (entityType == null) {
                return false;
            }

            Class<?> entityClass = entityType.getEntityClass();
            if (entityClass == null) {
                return false;
            }

            if (!LivingEntity.class.isAssignableFrom(entityClass)) {
                return false;
            }

            return entityType.isSpawnable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * SIMPLIFIED: Basic mob initialization
     */
    private boolean initializeMob() {
        try {
            if (entity == null) {
                return false;
            }

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("[CustomMob] Initializing mob %s T%d%s",
                        type.getId(), tier, elite ? "+" : ""));
            }

            // Step 1: Basic properties
            applyBasicProperties();

            // Step 2: Sunlight protection - CRITICAL FIX
            applySunlightProtection();

            // Step 3: Equipment (don't fail if this doesn't work)
            try {
                applyEquipment();
            } catch (Exception e) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.warning("[CustomMob] Equipment application failed (continuing): " + e.getMessage());
                }
            }

            // Step 4: Health
            calculateAndSetHealth();

            // Step 5: Type-specific properties (don't fail if this doesn't work)
            try {
                applyTypeProperties();
            } catch (Exception e) {
                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.warning("[CustomMob] Type properties failed (continuing): " + e.getMessage());
                }
            }

            // Step 6: Name and hologram setup
            captureOriginalName();
            lastDamageTime = 0;

            // Step 7: Initialize hologram system with delay to ensure entity is fully ready
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (isValid()) {
                    forceUpdateHologram();
                }
            }, 1L);

            return true;

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Initialization failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * CRITICAL FIX: Apply sunlight protection to prevent burning
     */
    private void applySunlightProtection() {
        if (entity == null) return;

        try {
            // For undead mobs that normally burn in sunlight
            if (isUndeadMob()) {
                // Method 1: Give them a helmet (even if invisible) to prevent burning
                if (entity.getEquipment() != null && entity.getEquipment().getHelmet() == null) {
                    // Create an invisible helmet that prevents sunlight burning
                    ItemStack protectionHelmet = new ItemStack(Material.LEATHER_HELMET);
                    ItemMeta meta = protectionHelmet.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§fSunlight Protection");
                        meta.setUnbreakable(true);
                        protectionHelmet.setItemMeta(meta);
                    }

                    // Set it with no drop chance and make it invisible in some cases
                    entity.getEquipment().setHelmet(protectionHelmet);
                    entity.getEquipment().setHelmetDropChance(0.0f);
                }

                // Method 2: Additional protection through metadata
                YakRealms plugin = YakRealms.getInstance();
                entity.setMetadata("sunlight_immune", new FixedMetadataValue(plugin, true));

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info("[CustomMob] Applied sunlight protection to " + type.getId());
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Failed to apply sunlight protection: " + e.getMessage());
        }
    }

    /**
     * Check if this mob is an undead type that would normally burn in sunlight
     */
    private boolean isUndeadMob() {
        if (entity == null) return false;

        EntityType entityType = entity.getType();
        return entityType == EntityType.SKELETON ||
                entityType == EntityType.WITHER_SKELETON ||
                entityType == EntityType.ZOMBIE ||
                entityType == EntityType.ZOMBIE_VILLAGER ||
                entityType == EntityType.HUSK ||
                entityType == EntityType.DROWNED ||
                entityType == EntityType.STRAY ||
                entityType == EntityType.PHANTOM;
    }

    /**
     * Prevent sunlight burning during tick updates
     */
    private void preventSunlightBurning() {
        if (!isValid() || !isUndeadMob()) return;

        try {
            // If the mob is on fire and it's daytime, extinguish them
            if (entity.getFireTicks() > 0) {
                World world = entity.getWorld();
                if (world != null && world.getTime() >= 0 && world.getTime() < 12300) {
                    // It's daytime, check if they're burning from sunlight
                    Location loc = entity.getLocation();
                    if (loc.getBlockY() < world.getHighestBlockYAt(loc) - 1) {
                        // They're not under direct sunlight, but might be burning anyway
                        // Extinguish them to be safe
                        entity.setFireTicks(0);
                    } else if (entity.getEquipment() != null && entity.getEquipment().getHelmet() != null) {
                        // They have a helmet, shouldn't burn
                        entity.setFireTicks(0);
                    }
                }
            }
        } catch (Exception e) {
            // Silent fail for sunlight prevention
        }
    }

    /**
     * Apply basic properties
     */
    protected void applyBasicProperties() {
        if (entity == null) return;

        try {
            // Basic metadata
            YakRealms plugin = YakRealms.getInstance();
            entity.setMetadata("type", new FixedMetadataValue(plugin, type.getId()));
            entity.setMetadata("tier", new FixedMetadataValue(plugin, String.valueOf(tier)));
            entity.setMetadata("customTier", new FixedMetadataValue(plugin, tier));
            entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));
            entity.setMetadata("customName", new FixedMetadataValue(plugin, type.getId()));
            entity.setMetadata("dropTier", new FixedMetadataValue(plugin, tier));
            entity.setMetadata("dropElite", new FixedMetadataValue(plugin, elite));

            if (type.isWorldBoss()) {
                entity.setMetadata("worldboss", new FixedMetadataValue(plugin, true));
            }

            // Basic behavior
            entity.setCanPickupItems(false);
            entity.setRemoveWhenFarAway(false);

            // Basic appearance - prepare name but don't show it (hologram will handle display)
            String tierSpecificName = getTierSpecificNameSafe();
            String formattedName = MobUtils.formatMobName(tierSpecificName, tier, elite);

            // Set name but hide it (hologram will show instead)
            //entity.setCustomName(formattedName);
            entity.setCustomNameVisible(false); // Hide entity name - hologram will show instead
            currentDisplayName = formattedName;

            // Basic effects - including fire resistance
            entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 2));

            // Type-specific behavior (basic)
            configureBasicBehavior();

            lastDamageTime = System.currentTimeMillis();

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Failed to apply basic properties: " + e.getMessage());
        }
    }

    /**
     * Configure basic mob-specific behavior
     */
    private void configureBasicBehavior() {
        try {
            if (entity instanceof Zombie zombie) {
                zombie.setBaby(type.getId().equals("imp"));
            } else if (entity instanceof PigZombie pigZombie) {
                pigZombie.setAngry(true);
                pigZombie.setBaby(type.getId().equals("imp"));
            } else if (entity instanceof MagmaCube magmaCube) {
                magmaCube.setSize(Math.max(1, Math.min(tier, 4)));
            }
        } catch (Exception e) {
            // Silent fail for behavior configuration
        }
    }

    /**
     * Get tier-specific name with safe fallback
     */
    private String getTierSpecificNameSafe() {
        try {
            return type.getTierSpecificName(tier);
        } catch (Exception e) {
            return getDisplayNameForType(type.getId());
        }
    }

    /**
     * Get display name for mob type with fallbacks
     */
    private String getDisplayNameForType(String typeId) {
        switch (typeId.toLowerCase()) {
            case "witherskeleton":
                return "Wither Skeleton";
            case "cavespider":
                return "Cave Spider";
            case "magmacube":
                return "Magma Cube";
            case "zombifiedpiglin":
                return "Zombified Piglin";
            case "elderguardian":
                return "Elder Guardian";
            case "irongolem":
                return "Iron Golem";
            case "frozenboss":
                return "Frozen Boss";
            case "frozenelite":
                return "Frozen Elite";
            case "frozengolem":
                return "Frozen Golem";
            case "bossskeleton":
                return "Boss Skeleton";
            default:
                return typeId.substring(0, 1).toUpperCase() + typeId.substring(1).toLowerCase();
        }
    }

    /**
     * CRITICAL FIX: Apply equipment with unbreakable items to prevent durability damage
     */
    protected void applyEquipment() {
        if (entity == null) return;

        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return; // No equipment slots
        }

        try {
            equipment.clear();

            int gearPieces = calculateGearPieces();
            ItemStack weapon = createWeapon();
            ItemStack[] armor = createArmorSet(gearPieces);

            if (weapon != null) {
                // Make weapon unbreakable
                makeItemUnbreakable(weapon);
                equipment.setItemInMainHand(weapon);
            }

            // Apply armor pieces and make them unbreakable
            if (armor[0] != null) {
                makeItemUnbreakable(armor[0]);
                equipment.setHelmet(armor[0]);
            }
            if (armor[1] != null) {
                makeItemUnbreakable(armor[1]);
                equipment.setChestplate(armor[1]);
            }
            if (armor[2] != null) {
                makeItemUnbreakable(armor[2]);
                equipment.setLeggings(armor[2]);
            }
            if (armor[3] != null) {
                makeItemUnbreakable(armor[3]);
                equipment.setBoots(armor[3]);
            }

            // Set drop chances to 0 - items never drop
            equipment.setItemInMainHandDropChance(0.0f);
            equipment.setHelmetDropChance(0.0f);
            equipment.setChestplateDropChance(0.0f);
            equipment.setLeggingsDropChance(0.0f);
            equipment.setBootsDropChance(0.0f);

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info("[CustomMob] Applied unbreakable equipment to " + type.getId());
            }

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Equipment application failed: " + e.getMessage());
        }
    }

    /**
     * CRITICAL FIX: Make an item unbreakable so it never takes durability damage
     */
    private void makeItemUnbreakable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                item.setItemMeta(meta);
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Failed to make item unbreakable: " + e.getMessage());
        }
    }

    protected int calculateGearPieces() {
        int gearPieces = RANDOM.nextInt(3) + 1;

        if (tier == 3) {
            gearPieces = RANDOM.nextBoolean() ? 3 : 4;
        } else if (tier >= 4 || elite) {
            gearPieces = 4;
        }

        return gearPieces;
    }

    /**
     * Create weapon with fallback
     */
    protected ItemStack createWeapon() {
        try {
            if (DropsManager.getInstance() != null) {
                ItemStack weapon = DropsManager.getInstance().createDrop(tier, getWeaponType());
                if (weapon != null) {
                    if (elite) {
                        weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                    }
                    return weapon;
                }
            }
        } catch (Exception e) {
            // Fall back to simple weapon
        }

        // Fallback weapon creation
        return createFallbackWeapon();
    }

    private ItemStack createFallbackWeapon() {
        try {
            Material weaponMaterial;
            switch (tier) {
                case 1:
                    weaponMaterial = Material.WOODEN_SWORD;
                    break;
                case 2:
                    weaponMaterial = Material.STONE_SWORD;
                    break;
                case 3:
                    weaponMaterial = Material.IRON_SWORD;
                    break;
                case 4:
                    weaponMaterial = Material.DIAMOND_SWORD;
                    break;
                case 5:
                    weaponMaterial = Material.GOLDEN_SWORD;
                    break;
                case 6:
                    weaponMaterial = Material.DIAMOND_SWORD;
                    break;
                default:
                    weaponMaterial = Material.WOODEN_SWORD;
            }

            ItemStack weapon = new ItemStack(weaponMaterial);
            if (elite) {
                weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
            }
            return weapon;
        } catch (Exception e) {
            return new ItemStack(Material.WOODEN_SWORD);
        }
    }

    protected int getWeaponType() {
        int weaponType = RANDOM.nextInt(5);
        if (weaponType == 0) weaponType = 1;
        if (weaponType == 1) {
            weaponType = ThreadLocalRandom.current().nextInt(1, 5);
        }
        return weaponType;
    }

    /**
     * Create armor set with fallback
     */
    protected ItemStack[] createArmorSet(int gearPieces) {
        ItemStack[] armor = new ItemStack[4];

        try {
            if (DropsManager.getInstance() != null) {
                while (gearPieces > 0) {
                    int armorType = RANDOM.nextInt(4);

                    if (armor[armorType] == null) {
                        int dropType = armorType + 5;
                        ItemStack piece = DropsManager.getInstance().createDrop(tier, dropType);

                        if (piece != null) {
                            if (elite) {
                                piece.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                            }
                            armor[armorType] = piece;
                        }
                        gearPieces--;
                    }
                }
            } else {
                armor = createFallbackArmor(gearPieces);
            }
        } catch (Exception e) {
            armor = createFallbackArmor(gearPieces);
        }

        return armor;
    }

    private ItemStack[] createFallbackArmor(int gearPieces) {
        ItemStack[] armor = new ItemStack[4];

        try {
            Material armorMaterial;
            switch (tier) {
                case 1:
                case 2:
                    armorMaterial = Material.LEATHER_HELMET;
                    break;
                case 3:
                    armorMaterial = Material.IRON_HELMET;
                    break;
                case 4:
                    armorMaterial = Material.DIAMOND_HELMET;
                    break;
                case 5:
                    armorMaterial = Material.GOLDEN_HELMET;
                    break;
                case 6:
                    armorMaterial = Material.LEATHER_HELMET;
                    break;
                default:
                    armorMaterial = Material.LEATHER_HELMET;
            }

            String baseName = armorMaterial.name().replace("_HELMET", "");

            while (gearPieces > 0) {
                int armorType = RANDOM.nextInt(4);
                if (armor[armorType] == null) {
                    try {
                        Material pieceType;
                        switch (armorType) {
                            case 0:
                                pieceType = Material.valueOf(baseName + "_HELMET");
                                break;
                            case 1:
                                pieceType = Material.valueOf(baseName + "_CHESTPLATE");
                                break;
                            case 2:
                                pieceType = Material.valueOf(baseName + "_LEGGINGS");
                                break;
                            case 3:
                                pieceType = Material.valueOf(baseName + "_BOOTS");
                                break;
                            default:
                                continue;
                        }

                        armor[armorType] = new ItemStack(pieceType);
                        if (elite && armor[armorType] != null) {
                            armor[armorType].addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                        }
                    } catch (IllegalArgumentException e) {
                        // Material doesn't exist, skip
                    }
                    gearPieces--;
                }
            }
        } catch (Exception e) {
            // Return empty armor on failure
        }

        return armor;
    }

    /**
     * SIMPLIFIED: Calculate and set health
     */
    protected void calculateAndSetHealth() {
        if (entity == null) return;

        try {
            int hp = calculateBaseHealth();
            hp = applyHealthModifiers(hp);
            hp = applySpecialHealthValues(hp);

            if (lightningMultiplier > 0) {
                hp *= lightningMultiplier;
            }

            hp = Math.max(1, Math.min(hp, 2000000)); // Clamp between 1 and 2M

            entity.setMaxHealth(hp);
            entity.setHealth(hp);
            baseHealth = hp;

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("[CustomMob] Set health for %s T%d%s: %d HP",
                        type.getId(), tier, elite ? "+" : "", hp));
            }

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health calculation failed: " + e.getMessage());
            try {
                entity.setMaxHealth(20);
                entity.setHealth(20);
                baseHealth = 20;
            } catch (Exception fallbackError) {
                // Even fallback failed
            }
        }
    }

    private int calculateBaseHealth() {
        try {
            return MobUtils.calculateArmorHealth(entity);
        } catch (Exception e) {
            return 20 + (tier * 10); // Simple fallback
        }
    }

    private int applyHealthModifiers(int baseHp) {
        try {
            return MobUtils.applyHealthMultiplier(baseHp, tier, elite);
        } catch (Exception e) {
            // Manual fallback calculation
            double multiplier = elite ? (1.5 + tier * 0.5) : (0.5 + tier * 0.2);
            return (int) (baseHp * multiplier);
        }
    }

    private int applySpecialHealthValues(int hp) {
        try {
            switch (type.getId().toLowerCase()) {
                case "warden":
                    return 85000;
                case "bossskeleton":
                case "bossSkeletonDungeon":
                    return 115000;
                case "frostwing":
                case "chronos":
                    return ThreadLocalRandom.current().nextInt(210000, 234444);
                case "frozenelite":
                    return YakRealms.isT6Enabled() ? 200000 : 100000;
                case "frozenboss":
                    return YakRealms.isT6Enabled() ? 300000 : 200000;
                case "frozengolem":
                    return YakRealms.isT6Enabled() ? 400000 : 200000;
                default:
                    return hp;
            }
        } catch (Exception e) {
            return hp;
        }
    }

    /**
     * SIMPLIFIED: Apply type-specific properties
     */
    protected void applyTypeProperties() {
        if (entity == null) return;

        try {
            // Basic movement effects
            if (elite && !type.getId().equals("frozenboss")) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
            }

            if (tier > 2 && !type.getId().equals("frozenboss")) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
            }

            // Special type effects
            switch (type.getId().toLowerCase()) {
                case "frozengolem":
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                    break;
                case "weakskeleton":
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1));
                    break;
            }
        } catch (Exception e) {
            // Silent fail for type properties
        }
    }

    /**
     * Capture original name
     */
    private void captureOriginalName() {
        if (entity == null || originalNameStored) return;

        try {
            String capturedName = MobUtils.captureOriginalName(entity);

            if (capturedName != null && MobUtils.isValidRestorationName(capturedName)) {
                originalName = capturedName;
                originalNameStored = true;
            } else {
                originalName = generateDefaultNameSafe();
                originalNameStored = true;
            }

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info("[CustomMob] Stored original name: '" + originalName + "'");
            }
        } catch (Exception e) {
            originalName = generateDefaultNameSafe();
            originalNameStored = true;
        }
    }

    /**
     * Generate default name with safe fallbacks
     */
    String generateDefaultNameSafe() {
        try {
            String tierSpecificName = getTierSpecificNameSafe();
            return MobUtils.formatMobName(tierSpecificName, tier, elite);
        } catch (Exception e) {
            return "§7" + type.getId() + " T" + tier + (elite ? "+" : "");
        }
    }

    /**
     * FIXED: Clean up resources with proper hologram removal
     */
    private void cleanup() {
        try {
            removeHologram(); // Remove hologram first

            if (entity != null && entity.isValid()) {
                entity.remove();
                entity = null;
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Cleanup error: " + e.getMessage());
        }
    }

    // ================ NAME VISIBILITY MANAGEMENT ================

    /**
     * Enhanced name visibility management with hologram integration
     */
    public void updateNameVisibility() {
        if (!isValid()) return;

        try {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;
            boolean inCritState = CritManager.getInstance().isInCritState(entity.getUniqueId());
            boolean shouldShowHealthBar = recentlyDamaged || inCritState;

            // Always keep entity name hidden when hologram is active
            if (hologramActive) {
                entity.setCustomNameVisible(false);
                nameVisible = false;
            } else {
                // Fallback to normal name visibility when hologram is not active
                if (shouldShowHealthBar) {
                    if (!showingHealthBar) {
                        showingHealthBar = true;
                        nameVisible = true;
                        updateHealthBar();
                    }
                } else {
                    if (showingHealthBar || nameVisible) {
                        restoreOriginalName();
                        showingHealthBar = false;
                        nameVisible = false;
                    }
                }
            }

            // Always update hologram regardless of entity name state
            updateHologramOverlayOptimized();

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Name visibility update failed: " + e.getMessage());
        }
    }

    /**
     * Restore original name (fallback when hologram not active)
     */
    private void restoreOriginalName() {
        if (!isValid() || CritManager.getInstance().isInCritState(entity.getUniqueId()) || hologramActive) return;

        try {
            String nameToRestore = originalName;
            if (nameToRestore == null || nameToRestore.isEmpty()) {
                nameToRestore = generateDefaultNameSafe();
            }

            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                entity.setCustomName(nameToRestore);
                entity.setCustomNameVisible(true);
                currentDisplayName = nameToRestore;
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Name restoration failed: " + e.getMessage());
        }
    }

    // ================  CRITICAL HIT DELEGATION ================

    /**
     * Roll for critical hit using CritManager
     */
    public boolean rollForCritical() {
        return CritManager.getInstance().initiateCrit(this);
    }

    /**
     * Handle crit attack using CritManager
     */
    public double applyCritDamageToPlayer(Player player, double baseDamage) {
        return CritManager.getInstance().handleCritAttack(this, player, baseDamage);
    }

    /**
     * Check if mob is in critical state via CritManager
     */
    public boolean isInCriticalState() {
        return CritManager.getInstance().isInCritState(entity.getUniqueId());
    }

    /**
     * Check if mob is ready for crit attack via CritManager
     */
    public boolean isCritReadyToAttack() {
        return CritManager.getInstance().isCritCharged(entity.getUniqueId());
    }

    /**
     * Get current crit countdown via CritManager
     */
    public int getCritCountdown() {
        return CritManager.getInstance().getCritCountdown(entity.getUniqueId());
    }

    // ================ DAMAGE PROCESSING ================

    public double damage(double damage) {
        if (!isValid()) {
            return 0;
        }

        try {
            double finalDamage = processDamage(damage);
            updateHealthState(finalDamage);
            return finalDamage;
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Damage processing error: " + e.getMessage());
            return 0;
        }
    }

    private double processDamage(double damage) {
        return damage;
    }

    /**
     * Damage processing with immediate health bar update
     */
    private void updateHealthState(double damage) {
        double currentHealth = entity.getHealth();
        double newHealth = currentHealth - damage;

        if (newHealth <= 0) {
            handleDeath();
        } else {
            // Apply damage first
            entity.setHealth(newHealth);

            // Update damage time
            updateDamageTime();

            // CRITICAL: Small delay to ensure health is fully updated
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), () -> {
                if (isValid()) {
                    updateHealthBar();
                    forceUpdateHologram(); // Force immediate hologram update
                }
            });
        }
    }

    private void handleDeath() {
        entity.setHealth(0);
        MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);
    }

    private void updateDamageTime() {
        lastDamageTime = System.currentTimeMillis();
        nameVisible = true;
    }

    // ================ HEALTH BAR SYSTEM ================

    /**
     * Health bar update system with validation and real-time accuracy
     */
    public void updateHealthBar() {
        if (!isValid()) return;

        try {
            // CRITICAL FIX: Ensure we get the CURRENT health after any recent damage
            double currentHealth = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            // Validate health values
            if (currentHealth < 0) currentHealth = 0;
            if (maxHealth <= 0) maxHealth = 1;

            // Ensure current health doesn't exceed max health
            if (currentHealth > maxHealth) {
                currentHealth = maxHealth;
            }

            // Only update entity name if hologram is not active
            if (!hologramActive) {
                String healthBar = generateHealthBar(currentHealth, maxHealth);
                entity.setCustomName(healthBar);
                entity.setCustomNameVisible(true);
                currentDisplayName = healthBar;
            }

            showingHealthBar = true;
            lastDamageTime = System.currentTimeMillis();

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.fine("[CustomMob] Updated health bar: " + String.format("%.1f/%.1f (%.1f%%)",
                        currentHealth, maxHealth, (currentHealth / maxHealth) * 100));
            }

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health bar update failed: " + e.getMessage());
        }
    }

    /**
     * Generate health bar with current health values passed in
     */
    private String generateHealthBar(double health, double maxHealth) {
        try {
            if (health <= 0) health = 0.1;
            if (maxHealth <= 0) maxHealth = 1;

            boolean inCritState = CritManager.getInstance().isInCritState(entity.getUniqueId());

            return MobUtils.generateHealthBar(entity, health, maxHealth, tier, inCritState);
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health bar generation failed: " + e.getMessage());
            return originalName != null ? originalName : generateDefaultNameSafe();
        }
    }

    /**
     * Health bar generation using MobUtils for consistency with real-time values
     */
    public String generateHealthBar() {
        if (!isValid()) return "";

        try {
            // Get CURRENT health values
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            return generateHealthBar(health, maxHealth);
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health bar generation failed: " + e.getMessage());
            return originalName != null ? originalName : generateDefaultNameSafe();
        }
    }

    // ================ LIGHTNING ENHANCEMENT ================

    public void enhanceAsLightningMob(int multiplier) {
        if (!isValid()) return;

        try {
            this.lightningMultiplier = multiplier;

            double newMaxHealth = entity.getMaxHealth() * multiplier;
            entity.setMaxHealth(newMaxHealth);
            entity.setHealth(newMaxHealth);

            entity.setGlowing(true);

            String tierSpecificName = getTierSpecificNameSafe();
            String lightningName = String.format("§6⚡ Lightning %s ⚡",
                    ChatColor.stripColor(tierSpecificName));

            // Update hologram with lightning status
            originalName = lightningName;
            currentDisplayName = lightningName;
            forceUpdateHologram();

            YakRealms plugin = YakRealms.getInstance();
            entity.setMetadata("LightningMultiplier", new FixedMetadataValue(plugin, multiplier));
            entity.setMetadata("LightningMob", new FixedMetadataValue(plugin, lightningName));

            LOGGER.info("[CustomMob] Enhanced " + type.getId() + " as lightning mob (x" + multiplier + ")");

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Lightning enhancement failed: " + e.getMessage());
        }
    }

    // ================ UTILITY METHODS ================

    public boolean isValid() {
        if (entity == null) return false;

        try {
            return entity.isValid() && !entity.isDead() && entity.getWorld() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void remove() {
        try {
            // Remove hologram first - CRITICAL for preventing leftover holograms
            removeHologram();

            if (isValid()) {
                MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);
                entity.remove();
            }

            MobManager.getInstance().unregisterMob(this);

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Removal failed: " + e.getMessage());
        }
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Unknown";
        }

        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    // ================ GETTERS ================

    public MobType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public boolean isElite() {
        return elite;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public String getCustomName() {
        return currentDisplayName;
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public boolean isNameVisible() {
        return nameVisible;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getFormattedOriginalName() {
        return originalName;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public int getLightningMultiplier() {
        return lightningMultiplier;
    }

    public boolean isShowingHealthBar() {
        return showingHealthBar;
    }

    public boolean isHologramActive() {
        return hologramActive;
    }
}
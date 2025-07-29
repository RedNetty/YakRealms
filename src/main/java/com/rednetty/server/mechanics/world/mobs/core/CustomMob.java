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
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *  CustomMob class with improved architecture and cleaner code
 * Key improvements:
 * - Simplified state management with thread-safe operations
 * - Cleaner display system with clear separation of concerns
 * - Reduced code duplication and improved error handling
 * - Better performance with optimized operations
 * - Improved readability and maintainability
 */
@Getter
public class CustomMob {

    // Constants
    public static final Random RANDOM = ThreadLocalRandom.current();
    public static final Logger LOGGER = YakRealms.getInstance().getLogger();
    public static final long COMBAT_TIMEOUT_MS = 6500L;
    public static final long HOLOGRAM_UPDATE_INTERVAL_MS = 5000L;
    public static final double HEALTH_CHANGE_THRESHOLD = 0.05; // 5% health change
    public static final int MAX_SPAWN_ATTEMPTS = 3;
    public static final int HEALTH_BAR_LENGTH = 36;

    // Core properties
    @Getter public final MobType type;
    @Getter public final int tier;
    @Getter public final boolean elite;
    @Getter public final String uniqueMobId;

    // Entity reference
    public volatile LivingEntity entity;
    public final ReadWriteLock entityLock = new ReentrantReadWriteLock();

    // Display system
    public volatile String baseDisplayName;
    public volatile String hologramId;
    public volatile long lastHologramUpdate = 0;

    // State management
    public final AtomicBoolean valid = new AtomicBoolean(true);
    public final AtomicBoolean removing = new AtomicBoolean(false);
    public final AtomicBoolean hologramActive = new AtomicBoolean(false);

    // Combat tracking
    public volatile long lastDamageTime = 0;
    public volatile boolean inCombat = false;

    // Health tracking
    public volatile double lastKnownHealth = -1;
    public volatile double lastKnownMaxHealth = -1;

    // Special modifiers
    @Getter public int lightningMultiplier = 0;

    /**
     * Creates a new CustomMob instance
     */
    public CustomMob(MobType type, int tier, boolean elite) {
        Objects.requireNonNull(type, "MobType cannot be null");

        this.type = type;
        this.tier = Math.max(1, Math.min(6, tier));
        this.elite = elite;
        this.uniqueMobId = generateUniqueMobId();

        logDebug("Creating %s mob: %s T%d%s (ID: %s)",
                elite ? "elite" : "normal", type.getId(), this.tier, elite ? "+" : "", uniqueMobId);
    }

    private String generateUniqueMobId() {
        return String.format("mob_%s_%d_%d", type.getId(), System.currentTimeMillis(), RANDOM.nextInt(10000));
    }

    // ==================== LIFECYCLE MANAGEMENT ====================

    /**
     * Main tick method - called periodically to update mob state
     */
    public void tick() {
        if (!isValid()) return;

        entityLock.readLock().lock();
        try {
            if (entity == null) return;

            updateCombatState();
            updateDisplayElements();
            preventSunlightDamage();

        } catch (Exception e) {
            logError("Tick error", e);
        } finally {
            entityLock.readLock().unlock();
        }
    }

    /**
     * Spawns the mob at the specified location
     */
    public boolean spawn(Location location) {
        if (!Bukkit.isPrimaryThread()) {
            LOGGER.severe("spawn() called from async thread!");
            return false;
        }

        entityLock.writeLock().lock();
        try {
            Location spawnLoc = findSafeSpawnLocation(location);
            if (spawnLoc == null) return false;

            entity = createEntity(spawnLoc);
            if (entity == null) return false;

            if (!initializeMob()) {
                cleanup();
                return false;
            }

            MobManager.getInstance().registerMob(this);
            logDebug("Successfully spawned %s", uniqueMobId);
            return true;

        } catch (Exception e) {
            logError("Spawn error", e);
            cleanup();
            return false;
        } finally {
            entityLock.writeLock().unlock();
        }
    }

    /**
     * Removes the mob and cleans up resources
     */
    public void remove() {
        if (!removing.compareAndSet(false, true)) return;

        entityLock.writeLock().lock();
        try {
            valid.set(false);
            removeHologram();

            if (entity != null && entity.isValid()) {
                MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);
                entity.remove();
            }

            MobManager.getInstance().unregisterMob(this);

        } catch (Exception e) {
            logError("Removal error", e);
        } finally {
            entity = null;
            entityLock.writeLock().unlock();
        }
    }

    // ==================== COMBAT MANAGEMENT ====================

    /**
     * Handles damage taken by the mob
     */
    public void handleDamage(double damage) {
        if (!isValid()) return;

        lastDamageTime = System.currentTimeMillis();
        updateDisplayElements();
    }

    /**
     * Updates combat state based on recent damage
     */
    private void updateCombatState() {
        long currentTime = System.currentTimeMillis();
        boolean wasInCombat = inCombat;

        inCombat = (currentTime - lastDamageTime) < COMBAT_TIMEOUT_MS;

        if (inCombat != wasInCombat) {
            if (inCombat) {
                onEnterCombat();
            } else {
                scheduleExitCombat();
            }
        }
    }

    private void onEnterCombat() {
        hologramActive.set(true);
    }

    private void scheduleExitCombat() {
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (!inCombat) {
                hologramActive.set(false);
                removeHologram();
            }
        }, 60L); // 3 seconds
    }

    // ==================== DISPLAY SYSTEM ====================

    /**
     * Updates all display elements (name and hologram)
     */
    private void updateDisplayElements() {
        updateMobName();

        if (shouldUpdateHologram()) {
            updateHologram();
        }
    }

    /**
     * Updates the mob's display name based on current state
     */
    private void updateMobName() {
        if (entity == null) return;

        try {
            if (baseDisplayName == null) {
                baseDisplayName = generateBaseDisplayName();
            }

            String displayName = generateCurrentDisplayName();
            if (!displayName.equals(entity.getCustomName())) {
                entity.setCustomName(displayName);
                entity.setCustomNameVisible(true);
            }

        } catch (Exception e) {
            logError("Name update failed", e);
        }
    }

    /**
     * Generates the base display name for the mob
     */
    private String generateBaseDisplayName() {
        String tierName = type.getTierSpecificName(tier);
        return MobUtils.formatMobName(tierName, tier, elite);
    }

    /**
     * Generates the current display name based on mob state
     */
    private String generateCurrentDisplayName() {
        if (baseDisplayName == null) return type.getId();

        // Show health percentage in combat
        if(!inCombat) {
            removeHologram();
        }
        if (inCombat && entity != null) {
            try {
                double healthPercent = (entity.getHealth() / entity.getMaxHealth()) * 100.0;
                ChatColor healthColor = getHealthColor(healthPercent);
                return String.format("%s %s(%.1f%%)", baseDisplayName, healthColor, healthPercent);
            } catch (Exception e) {
                // Fall back to base name
            }
        }

        // Show critical state
        if (isInCriticalState()) {
            int countdown = getCritCountdown();
            if (countdown > 0) {
                return baseDisplayName + " §c⚠ " + countdown + " ⚠";
            } else {
                return baseDisplayName + " §5⚡ CHARGED ⚡";
            }
        }

        return baseDisplayName;
    }

    /**
     * Determines if hologram should be updated
     */
    private boolean shouldUpdateHologram() {
        if (!hologramActive.get()) return false;

        long currentTime = System.currentTimeMillis();

        // Update in combat or periodically
        return inCombat ||
                (currentTime - lastHologramUpdate) > HOLOGRAM_UPDATE_INTERVAL_MS ||
                hasHealthChangedSignificantly();
    }

    /**
     * Updates the hologram display
     */
    // In CustomMob.java, update updateHologram()
    private void updateHologram() {
        if (entity == null || !hologramActive.get()) return;

        try {
            if (hologramId == null) {
                hologramId = "holo_" + uniqueMobId;
            }

            List<String> lines = createHologramLines();
            if (lines.isEmpty()) return;

            Location holoLoc = entity.getLocation().add(0, entity.getHeight() + 0.5, 0);

            boolean updated = HologramManager.updateHologramEfficiently(
                    hologramId, holoLoc, lines, 0.25, getEntityUuid()  // Use entity UUID string
            );

            if (updated) {
                lastHologramUpdate = System.currentTimeMillis();
            }

        } catch (Exception e) {
            logError("Hologram update failed", e);
        }
    }
    // In CustomMob.java, add this method
    public String getEntityUuid() {
        LivingEntity ent = getEntity();
        return ent != null ? ent.getUniqueId().toString() : null;
    }
    /**
     * Creates hologram display lines
     */
    private List<String> createHologramLines() {
        List<String> lines = new ArrayList<>();

        if (!inCombat || entity == null) return lines;

        try {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            if (maxHealth > 0) {
                lines.add(createHealthBar(health, maxHealth));
            }

        } catch (Exception e) {
            logError("Hologram line creation failed", e);
        }

        return lines;
    }

    /**
     * Creates a visual health bar
     */
    private String createHealthBar(double health, double maxHealth) {
        double percentage = (health / maxHealth) * 100.0;
        int filled = (int) Math.round((health / maxHealth) * HEALTH_BAR_LENGTH);
        filled = Math.max(0, Math.min(HEALTH_BAR_LENGTH, filled));

        ChatColor barColor = getHealthBarColor(percentage);

        return String.format("§8[%s%s§7%s§8]",
                barColor,
                "|".repeat(filled),
                "|".repeat(HEALTH_BAR_LENGTH - filled)
        );
    }

    /**
     * Removes the hologram display
     */
    private void removeHologram() {
        if (hologramId == null) return;

        try {
            String entityUuidStr = entity.getUniqueId().toString();
            if (entityUuidStr != null) {
                HologramManager.removeHologramByMob(entityUuidStr);
            }
            HologramManager.removeHologram(hologramId);
            hologramActive.set(false);
        } catch (Exception e) {
            logError("Hologram removal failed", e);
        }
    }

    // ==================== MOB INITIALIZATION ====================

    /**
     * Creates the entity at the specified location
     */
    private LivingEntity createEntity(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        EntityType entityType = determineEntityType();

        for (int attempt = 1; attempt <= MAX_SPAWN_ATTEMPTS; attempt++) {
            try {
                Entity spawned = world.spawnEntity(location, entityType);

                if (spawned instanceof LivingEntity living && living.isValid()) {
                    return living;
                }

                if (spawned != null) spawned.remove();

            } catch (Exception e) {
                logDebug("Entity creation attempt %d failed: %s", attempt, e.getMessage());
            }

            if (attempt < MAX_SPAWN_ATTEMPTS) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return null;
    }

    /**
     * Initializes the mob with properties and equipment
     */
    private boolean initializeMob() {
        if (entity == null) return false;

        try {
            applyMetadata();
            applyBaseProperties();
            applyEquipment();
            applyHealthAndStats();

            baseDisplayName = generateBaseDisplayName();
            updateMobName();

            return true;

        } catch (Exception e) {
            logError("Initialization failed", e);
            return false;
        }
    }

    /**
     * Applies metadata to the entity
     */
    private void applyMetadata() {
        YakRealms plugin = YakRealms.getInstance();

        entity.setMetadata("type", new FixedMetadataValue(plugin, type.getId()));
        entity.setMetadata("tier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("customTier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));
        entity.setMetadata("mob_unique_id", new FixedMetadataValue(plugin, uniqueMobId));

        if (type.isWorldBoss()) {
            entity.setMetadata("worldboss", new FixedMetadataValue(plugin, true));
        }
    }

    /**
     * Applies base properties to the entity
     */
    private void applyBaseProperties() {
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 2));

        if (isUndead()) {
            entity.setMetadata("sunlight_immune",
                    new FixedMetadataValue(YakRealms.getInstance(), true));
        }

        applyTypeSpecificProperties();
    }

    /**
     * Applies type-specific properties
     */
    private void applyTypeSpecificProperties() {
        if (entity instanceof Zombie zombie) {
            zombie.setBaby(type.getId().equalsIgnoreCase("imp"));
        } else if (entity instanceof PigZombie pigZombie) {
            pigZombie.setAngry(true);
            pigZombie.setBaby(type.getId().equalsIgnoreCase("imp"));
        } else if (entity instanceof MagmaCube magmaCube) {
            magmaCube.setSize(Math.max(1, Math.min(tier, 4)));
        }
    }

    /**
     * Applies equipment to the entity
     */
    private void applyEquipment() {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        equipment.clear();

        // Weapon
        ItemStack weapon = createWeapon();
        if (weapon != null) {
            makeUnbreakable(weapon);
            equipment.setItemInMainHand(weapon);
            equipment.setItemInMainHandDropChance(0.0f);
        }

        // Armor
        int gearPieces = calculateGearPieces();
        ItemStack[] armor = createArmorSet(gearPieces);

        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null) {
                makeUnbreakable(armor[i]);
                setArmorPiece(equipment, i, armor[i]);
            }
        }

        // No drops
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
    }

    /**
     * Applies health and stats to the entity
     */
    private void applyHealthAndStats() {
        int health = calculateHealth();

        if (lightningMultiplier > 0) {
            health *= lightningMultiplier;
        }

        health = Math.max(1, Math.min(health, 2_000_000));

        entity.setMaxHealth(health);
        entity.setHealth(health);

        lastKnownHealth = health;
        lastKnownMaxHealth = health;
    }

    /**
     * Calculates the mob's health based on tier and equipment
     */
    private int calculateHealth() {
        int baseHealth = MobUtils.calculateArmorHealth(entity);
        baseHealth = MobUtils.applyHealthMultiplier(baseHealth, tier, elite);

        // Special mob health overrides
        return switch (type.getId().toLowerCase()) {
            case "warden" -> 85_000;
            case "bossskeleton", "bossskeletondungeon" -> 115_000;
            case "frostwing", "chronos" -> ThreadLocalRandom.current().nextInt(210_000, 234_444);
            case "frozenelite" -> YakRealms.isT6Enabled() ? 200_000 : 100_000;
            case "frozenboss" -> YakRealms.isT6Enabled() ? 300_000 : 200_000;
            case "frozengolem" -> YakRealms.isT6Enabled() ? 400_000 : 200_000;
            default -> baseHealth;
        };
    }

    // ==================== EQUIPMENT CREATION ====================

    /**
     * Calculates number of gear pieces based on tier
     */
    private int calculateGearPieces() {
        if (tier >= 4 || elite) return 4;
        if (tier == 3) return RANDOM.nextBoolean() ? 3 : 4;
        return RANDOM.nextInt(3) + 1;
    }

    /**
     * Creates a weapon for the mob
     */
    private ItemStack createWeapon() {
        try {
            DropsManager drops = DropsManager.getInstance();
            if (drops != null) {
                int weaponType = ThreadLocalRandom.current().nextInt(1, 5);
                ItemStack weapon = drops.createDrop(tier, weaponType);

                if (weapon != null) {
                    if (elite) {
                        weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                    }
                    return weapon;
                }
            }
        } catch (Exception e) {
            // Fall through to default weapon
        }

        return createDefaultWeapon();
    }

    /**
     * Creates a default weapon based on tier
     */
    private ItemStack createDefaultWeapon() {
        Material material = switch (tier) {
            case 1 -> Material.WOODEN_SWORD;
            case 2 -> Material.STONE_SWORD;
            case 3 -> Material.IRON_SWORD;
            case 4, 6 -> Material.DIAMOND_SWORD;
            case 5 -> Material.GOLDEN_SWORD;
            default -> Material.WOODEN_SWORD;
        };

        ItemStack weapon = new ItemStack(material);
        if (elite) {
            weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
        }
        return weapon;
    }

    /**
     * Creates armor set for the mob
     */
    private ItemStack[] createArmorSet(int pieces) {
        ItemStack[] armor = new ItemStack[4];
        Set<Integer> usedSlots = new HashSet<>();

        try {
            DropsManager drops = DropsManager.getInstance();
            if (drops != null) {
                while (pieces > 0 && usedSlots.size() < 4) {
                    int slot = RANDOM.nextInt(4);

                    if (!usedSlots.contains(slot)) {
                        ItemStack piece = drops.createDrop(tier, slot + 5);

                        if (piece != null) {
                            if (elite) {
                                piece.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                            }
                            armor[slot] = piece;
                            usedSlots.add(slot);
                        }
                        pieces--;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to default armor
        }

        return armor;
    }

    /**
     * Sets an armor piece on the equipment
     */
    private void setArmorPiece(EntityEquipment equipment, int slot, ItemStack item) {
        switch (slot) {
            case 0 -> equipment.setHelmet(item);
            case 1 -> equipment.setChestplate(item);
            case 2 -> equipment.setLeggings(item);
            case 3 -> equipment.setBoots(item);
        }
    }

    /**
     * Makes an item unbreakable
     */
    private void makeUnbreakable(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
    }

    // ==================== SPECIAL FEATURES ====================

    /**
     * Enhances the mob as a lightning mob
     */
    public void enhanceAsLightningMob(int multiplier) {
        if (!isValid()) return;

        entityLock.writeLock().lock();
        try {
            this.lightningMultiplier = multiplier;

            double newHealth = entity.getMaxHealth() * multiplier;
            entity.setMaxHealth(newHealth);
            entity.setHealth(newHealth);
            entity.setGlowing(true);

            baseDisplayName = String.format("§6⚡ Lightning %s ⚡",
                    ChatColor.stripColor(baseDisplayName != null ? baseDisplayName : type.getId()));

            updateMobName();

            YakRealms plugin = YakRealms.getInstance();
            entity.setMetadata("LightningMultiplier", new FixedMetadataValue(plugin, multiplier));
            entity.setMetadata("LightningMob", new FixedMetadataValue(plugin, baseDisplayName));

            logInfo(" %s as lightning mob (x%d)", uniqueMobId, multiplier);

        } catch (Exception e) {
            logError("Lightning enhancement failed", e);
        } finally {
            entityLock.writeLock().unlock();
        }
    }

    // ==================== CRITICAL HIT SYSTEM ====================

    public boolean rollForCritical() {
        return CritManager.getInstance().initiateCrit(this);
    }

    public double applyCritDamageToPlayer(Player player, double baseDamage) {
        return CritManager.getInstance().handleCritAttack(this, player, baseDamage);
    }

    public boolean isInCriticalState() {
        return entity != null && CritManager.getInstance().isInCritState(entity.getUniqueId());
    }

    public boolean isCritReadyToAttack() {
        return entity != null && CritManager.getInstance().isCritCharged(entity.getUniqueId());
    }

    public int getCritCountdown() {
        return entity != null ? CritManager.getInstance().getCritCountdown(entity.getUniqueId()) : 0;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Finds a safe spawn location near the target
     */
    private Location findSafeSpawnLocation(Location target) {
        if (target == null || target.getWorld() == null) return null;

        if (isSafeLocation(target)) {
            return target.clone().add(0.5, 0, 0.5);
        }

        // Try nearby locations
        for (int i = 0; i < 5; i++) {
            Location candidate = target.clone().add(
                    RANDOM.nextDouble() * 4 - 2,
                    RANDOM.nextDouble() * 2,
                    RANDOM.nextDouble() * 4 - 2
            );

            if (isSafeLocation(candidate)) {
                return candidate;
            }
        }

        // Last resort - spawn above
        return target.clone().add(0.5, 2, 0.5);
    }

    /**
     * Checks if a location is safe for spawning
     */
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (loc.getY() < 0 || loc.getY() > loc.getWorld().getMaxHeight() - 3) return false;

        Material block = loc.getBlock().getType();
        return block != Material.LAVA && block != Material.BEDROCK;
    }

    /**
     * Determines the entity type for spawning
     */
    private EntityType determineEntityType() {
        EntityType configuredType = type.getEntityType();
        if (configuredType != null && isValidEntityType(configuredType)) {
            return configuredType;
        }

        // Fallback based on type ID
        return switch (type.getId().toLowerCase()) {
            case "skeleton" -> EntityType.SKELETON;
            case "witherskeleton", "wither_skeleton" -> EntityType.WITHER_SKELETON;
            case "zombie" -> EntityType.ZOMBIE;
            case "spider" -> EntityType.SPIDER;
            case "cavespider", "cave_spider" -> EntityType.CAVE_SPIDER;
            case "magmacube", "magma_cube" -> EntityType.MAGMA_CUBE;
            case "zombifiedpiglin", "pigzombie" -> EntityType.ZOMBIFIED_PIGLIN;
            case "enderman" -> EntityType.ENDERMAN;
            case "creeper" -> EntityType.CREEPER;
            case "blaze" -> EntityType.BLAZE;
            case "warden" -> EntityType.WARDEN;
            case "golem", "irongolem" -> EntityType.IRON_GOLEM;
            default -> EntityType.SKELETON;
        };
    }

    /**
     * Validates if an entity type can be used
     */
    private boolean isValidEntityType(EntityType type) {
        return type != null &&
                type.getEntityClass() != null &&
                LivingEntity.class.isAssignableFrom(type.getEntityClass()) &&
                type.isSpawnable();
    }

    /**
     * Checks if the mob is undead
     */
    private boolean isUndead() {
        if (entity == null) return false;

        EntityType type = entity.getType();
        return type == EntityType.SKELETON ||
                type == EntityType.WITHER_SKELETON ||
                type == EntityType.ZOMBIE ||
                type == EntityType.ZOMBIE_VILLAGER ||
                type == EntityType.HUSK ||
                type == EntityType.DROWNED ||
                type == EntityType.STRAY ||
                type == EntityType.PHANTOM;
    }

    /**
     * Prevents sunlight damage for undead mobs
     */
    private void preventSunlightDamage() {
        if (!isUndead() || entity == null) return;

        if (entity.getFireTicks() > 0) {
            entity.setFireTicks(0);
        }
    }

    /**
     * Checks if health has changed significantly
     */
    private boolean hasHealthChangedSignificantly() {
        if (entity == null) return false;

        try {
            double currentHealth = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            if (Math.abs(currentHealth - lastKnownHealth) > (maxHealth * HEALTH_CHANGE_THRESHOLD)) {
                lastKnownHealth = currentHealth;
                lastKnownMaxHealth = maxHealth;
                return true;
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    /**
     * Gets color based on health percentage
     */
    private ChatColor getHealthColor(double percentage) {
        if (percentage > 75) return ChatColor.GREEN;
        if (percentage > 50) return ChatColor.YELLOW;
        if (percentage > 25) return ChatColor.GOLD;
        return ChatColor.RED;
    }

    /**
     * Gets health bar color based on percentage
     */
    private ChatColor getHealthBarColor(double percentage) {
        if (isInCriticalState()) return ChatColor.LIGHT_PURPLE;
        return getHealthColor(percentage);
    }

    /**
     * Cleans up resources
     */
    private void cleanup() {
        valid.set(false);
        removeHologram();

        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        entity = null;
    }

    // ==================== STATE CHECKS ====================

    /**
     * Checks if the mob is still valid
     */
    public boolean isValid() {
        if (!valid.get()) return false;

        entityLock.readLock().lock();
        try {
            return entity != null && entity.isValid() && !entity.isDead();
        } finally {
            entityLock.readLock().unlock();
        }
    }

    /**
     * Gets the entity (thread-safe)
     */
    public LivingEntity getEntity() {
        entityLock.readLock().lock();
        try {
            return entity;
        } finally {
            entityLock.readLock().unlock();
        }
    }

    /**
     * Gets the current health percentage
     */
    public double getHealthPercentage() {
        entityLock.readLock().lock();
        try {
            if (entity == null) return 0.0;

            double current = entity.getHealth();
            double max = entity.getMaxHealth();
            return max > 0 ? (current / max) * 100.0 : 0.0;

        } catch (Exception e) {
            return 0.0;
        } finally {
            entityLock.readLock().unlock();
        }
    }

    // ==================== LOGGING UTILITIES ====================

    private void logDebug(String format, Object... args) {
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("[CustomMob] " + format, args));
        }
    }

    private void logInfo(String format, Object... args) {
        LOGGER.info(String.format("[CustomMob] " + format, args));
    }

    private void logError(String message, Exception e) {
        LOGGER.log(Level.WARNING, "[CustomMob] " + message + " for " + uniqueMobId, e);
    }

    // ==================== LEGACY COMPATIBILITY ====================

    public String getCustomName() { return baseDisplayName; }
    public String getOriginalName() { return baseDisplayName; }
    public boolean isInCombat() { return inCombat; }
    public boolean isHologramActive() { return hologramActive.get(); }

    public void updateHealthBar() {
        updateDisplayElements();
    }

    public void refreshHealthBar() {
        if (isValid()) {
            Bukkit.getScheduler().runTask(YakRealms.getInstance(), this::updateDisplayElements);
        }
    }

    public void updateDamageTime() {
        lastDamageTime = System.currentTimeMillis();
    }
}
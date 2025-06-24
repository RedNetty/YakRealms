package com.rednetty.server.mechanics.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.drops.DropsManager;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Enhanced base class for all custom mobs with improved functionality and performance
 */
public class CustomMob {

    // ================ CONSTANTS ================

    protected static final Random RANDOM = new Random();
    protected static final Logger LOGGER = YakRealms.getInstance().getLogger();
    protected static final int NAME_VISIBILITY_TIMEOUT = 5000; // 5 seconds
    protected static final long MIN_RESPAWN_DELAY = 180000; // 3 minutes minimum

    // ================ CORE PROPERTIES ================

    protected final MobType type;
    protected final int tier;
    protected final boolean elite;
    protected final String originalName;

    // ================ STATE VARIABLES ================

    protected LivingEntity entity;
    protected String customName;
    protected boolean inCriticalState;
    protected int criticalStateDuration;
    protected int lightningMultiplier = 0;
    protected long lastDamageTime = -1000000;
    protected long lastDeathTime = 0;

    // ================ VISIBILITY MANAGEMENT ================

    public boolean nameVisible = true; // Initially true - show name by default

    // ================ HEALTH SYSTEM ================

    private double baseHealth = 0;
    private boolean healthCalculated = false;

    /**
     * Create a new custom mob with enhanced initialization
     *
     * @param type  Mob type configuration
     * @param tier  Mob tier (1-6)
     * @param elite Whether this is an elite mob
     */
    public CustomMob(MobType type, int tier, boolean elite) {
        validateConstructorParameters(type, tier);

        this.type = type;
        this.tier = Math.max(1, Math.min(6, tier));
        this.elite = elite;
        this.customName = type.getTierSpecificName(this.tier);
        this.originalName = this.customName;
        this.inCriticalState = false;
        this.criticalStateDuration = 0;
    }

    private void validateConstructorParameters(MobType type, int tier) {
        if (type == null) {
            throw new IllegalArgumentException("MobType cannot be null");
        }

        if (tier < 1 || tier > 6) {
            LOGGER.warning(String.format("§c[CustomMob] Invalid tier %d, clamping to valid range", tier));
        }
    }

    // ================ SPAWNING SYSTEM ================

    /**
     * Enhanced spawn method with improved error handling and validation
     *
     * @param location Location to spawn at
     * @return true if spawned successfully
     */
    public boolean spawn(Location location) {
        try {
            if (!validateSpawnLocation(location)) {
                return false;
            }

            if (!checkRespawnCooldown()) {
                return false;
            }

            Location spawnLoc = getOptimalSpawnLocation(location);
            entity = createEntity(spawnLoc);

            if (entity == null) {
                LOGGER.warning(String.format("§c[CustomMob] Failed to create entity for %s", type.getId()));
                return false;
            }

            if (!initializeMob()) {
                cleanup();
                return false;
            }

            registerWithManager();
            logSpawnSuccess();

            return true;

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Spawn error for %s: %s", type.getId(), e.getMessage()));
            cleanup();
            return false;
        }
    }

    private boolean validateSpawnLocation(Location location) {
        if (location == null) {
            LOGGER.warning("§c[CustomMob] Spawn location is null");
            return false;
        }

        if (location.getWorld() == null) {
            LOGGER.warning("§c[CustomMob] Spawn world is null");
            return false;
        }

        return true;
    }

    private boolean checkRespawnCooldown() {
        long currentTime = System.currentTimeMillis();
        long typeDeath = MobManager.getInstance().getLastDeathTime(type.getId(), tier, elite);

        if (typeDeath > 0) {
            long timeSinceDeath = currentTime - typeDeath;
            long requiredCooldown = calculateRespawnDelay();

            if (timeSinceDeath < requiredCooldown) {
                if (YakRealms.getInstance().isDebugMode()) {
                    long remainingSeconds = (requiredCooldown - timeSinceDeath) / 1000;
                    LOGGER.info(String.format("§6[CustomMob] §7Prevented respawn of %s T%d%s - %ds remaining",
                            type.getId(), tier, elite ? "+" : "", remainingSeconds));
                }
                return false;
            }
        }

        return true;
    }

    protected long calculateRespawnDelay() {
        return MobManager.getInstance().calculateRespawnDelay(tier, elite);
    }

    private Location getOptimalSpawnLocation(Location baseLocation) {
        for (int attempts = 0; attempts < 5; attempts++) {
            Location candidate = generateRandomSpawnLocation(baseLocation);
            if (isValidSpawnLocation(candidate)) {
                return candidate;
            }
        }

        // Fallback to safe location above base
        return baseLocation.clone().add(0.5, 2.0, 0.5);
    }

    private Location generateRandomSpawnLocation(Location baseLocation) {
        int randX = RANDOM.nextInt(7) - 3;
        int randZ = RANDOM.nextInt(7) - 3;

        return new Location(
                baseLocation.getWorld(),
                baseLocation.getX() + randX + 0.5,
                baseLocation.getY() + 2.0,
                baseLocation.getZ() + randZ + 0.5
        );
    }

    private boolean isValidSpawnLocation(Location location) {
        return !location.getBlock().getType().isSolid() &&
                !location.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }

    protected LivingEntity createEntity(Location location) {
        try {
            World world = location.getWorld();
            EntityType entityType = type.getEntityType();
            Entity spawnedEntity = world.spawnEntity(location, entityType);

            if (spawnedEntity instanceof LivingEntity) {
                return (LivingEntity) spawnedEntity;
            } else {
                spawnedEntity.remove();
                return null;
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Entity creation failed: %s", e.getMessage()));
            return null;
        }
    }

    private boolean initializeMob() {
        try {
            applyBasicProperties();
            applyEquipment();
            calculateAndSetHealth();
            applyTypeProperties();

            lastDamageTime = 0;
            return true;

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Initialization failed: %s", e.getMessage()));
            return false;
        }
    }

    private void registerWithManager() {
        MobManager.getInstance().registerMob(this);
        updateNameVisibility();
    }

    private void logSpawnSuccess() {
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("§a[CustomMob] §7Spawned %s T%d%s at %s",
                    type.getId(), tier, elite ? "+" : "",
                    formatLocation(entity.getLocation())));
        }
    }

    private void cleanup() {
        if (entity != null && entity.isValid()) {
            entity.remove();
            entity = null;
        }
    }

    // ================ PROPERTY APPLICATION ================

    protected void applyBasicProperties() {
        if (entity == null) return;

        // Essential metadata for identification and drops system
        setEntityMetadata();

        // Configure name and appearance
        configureEntityAppearance();

        // Apply basic behavioral settings
        configureBehavior();

        // Apply fundamental effects
        applyBasicEffects();

        // Initialize timing
        lastDamageTime = System.currentTimeMillis();
    }

    private void setEntityMetadata() {
        YakRealms plugin = YakRealms.getInstance();

        // Core identification metadata
        entity.setMetadata("type", new FixedMetadataValue(plugin, type.getId()));
        entity.setMetadata("tier", new FixedMetadataValue(plugin, String.valueOf(tier)));
        entity.setMetadata("customTier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("elite", new FixedMetadataValue(plugin, elite));
        entity.setMetadata("customName", new FixedMetadataValue(plugin, type.getId()));

        // Drop system metadata
        entity.setMetadata("dropTier", new FixedMetadataValue(plugin, tier));
        entity.setMetadata("dropElite", new FixedMetadataValue(plugin, elite));

        // Special type metadata
        if (type.isWorldBoss()) {
            entity.setMetadata("worldboss", new FixedMetadataValue(plugin, true));
        }
    }

    private void configureEntityAppearance() {
        String tierSpecificName = type.getTierSpecificName(tier);
        this.customName = tierSpecificName;

        ChatColor tierColor = getTierColor(tier);
        String formattedName = elite ?
                tierColor.toString() + ChatColor.BOLD + tierSpecificName :
                tierColor + tierSpecificName;

        entity.setCustomName(formattedName);
        entity.setCustomNameVisible(true);

        // Store original name in metadata
        entity.setMetadata("name", new FixedMetadataValue(YakRealms.getInstance(), formattedName));
    }

    private ChatColor getTierColor(int tier) {
        switch (tier) {
            case 1: return ChatColor.WHITE;
            case 2: return ChatColor.GREEN;
            case 3: return ChatColor.AQUA;
            case 4: return ChatColor.LIGHT_PURPLE;
            case 5: return ChatColor.YELLOW;
            case 6: return ChatColor.BLUE;
            default: return ChatColor.WHITE;
        }
    }

    private void configureBehavior() {
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);

        // Entity-specific configurations
        if (entity instanceof Zombie zombie) {
            zombie.setBaby(false);
        } else if (entity instanceof PigZombie pigZombie) {
            pigZombie.setAngry(true);
            pigZombie.setBaby(type == MobType.IMP || type == MobType.SPECTRAL_GUARD);
        } else if (entity instanceof MagmaCube magmaCube) {
            magmaCube.setSize(3);
        }
    }

    private void applyBasicEffects() {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));
    }

    // ================ EQUIPMENT SYSTEM ================

    protected void applyEquipment() {
        if (entity == null) return;

        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        try {
            equipment.clear();

            int gearPieces = calculateGearPieces();
            ItemStack weapon = createWeapon();
            ItemStack[] armor = createArmorSet(gearPieces);

            // Apply equipment
            equipment.setItemInMainHand(weapon);
            equipment.setHelmet(armor[0]);
            equipment.setChestplate(armor[1]);
            equipment.setLeggings(armor[2]);
            equipment.setBoots(armor[3]);

            // Prevent drops
            setDropChances(equipment);

            // Set equipment tier metadata
            setEquipmentTierMetadata(weapon);

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Equipment application failed: %s", e.getMessage()));
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

    protected ItemStack createWeapon() {
        try {
            ItemStack weapon = DropsManager.getInstance().createDrop(tier, getWeaponType());

            if (elite && weapon != null) {
                weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                applyTierColorToWeapon(weapon);
            }

            return weapon;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Weapon creation failed: %s", e.getMessage()));
            return null;
        }
    }

    private void applyTierColorToWeapon(ItemStack weapon) {
        if (weapon.getItemMeta() != null && weapon.getItemMeta().hasDisplayName()) {
            ChatColor tierColor = getTierColor(tier);
            String displayName = weapon.getItemMeta().getDisplayName();

            if (!displayName.startsWith(tierColor.toString())) {
                org.bukkit.inventory.meta.ItemMeta meta = weapon.getItemMeta();
                meta.setDisplayName(tierColor + ChatColor.stripColor(displayName));
                weapon.setItemMeta(meta);
            }
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

    protected ItemStack[] createArmorSet(int gearPieces) {
        ItemStack[] armor = new ItemStack[4];

        while (gearPieces > 0) {
            int armorType = RANDOM.nextInt(4);

            if (armor[armorType] == null) {
                int dropType = armorType + 5; // Convert to drop type
                ItemStack piece = DropsManager.getInstance().createDrop(tier, dropType);

                if (elite && piece != null) {
                    piece.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                    applySpecialArmorEffects(piece, armorType);
                }

                armor[armorType] = piece;
                gearPieces--;
            }
        }

        return armor;
    }

    private void applySpecialArmorEffects(ItemStack piece, int armorType) {
        // T6 special handling
        if (tier == 6 && armorType == 0 && piece.getType().name().contains("LEATHER_")) {
            org.bukkit.inventory.meta.ItemMeta meta = piece.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.BLUE + "T6 Armor Piece");
                piece.setItemMeta(meta);
            }
        }
    }

    private void setDropChances(EntityEquipment equipment) {
        equipment.setItemInMainHandDropChance(0);
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setLeggingsDropChance(0);
        equipment.setBootsDropChance(0);
    }

    private void setEquipmentTierMetadata(ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR) return;

        String itemType = weapon.getType().name();
        int equipTier = 1;

        if (itemType.contains("WOODEN_") || itemType.contains("WOOD_")) {
            equipTier = 1;
        } else if (itemType.contains("STONE_")) {
            equipTier = 2;
        } else if (itemType.contains("IRON_")) {
            equipTier = 3;
        } else if (itemType.contains("DIAMOND_")) {
            equipTier = (weapon.getItemMeta() != null &&
                    weapon.getItemMeta().hasDisplayName() &&
                    weapon.getItemMeta().getDisplayName().contains(ChatColor.BLUE.toString())) ? 6 : 4;
        } else if (itemType.contains("GOLDEN_") || itemType.contains("GOLD_")) {
            equipTier = 5;
        }

        entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), equipTier));
    }

    // ================ HEALTH SYSTEM ================

    protected void calculateAndSetHealth() {
        if (entity == null) return;

        try {
            int hp = calculateBaseHealth();
            hp = applyHealthModifiers(hp);
            hp = applySpecialHealthValues(hp);

            if (lightningMultiplier > 0) {
                hp *= lightningMultiplier;
            }

            hp = Math.max(1, hp);

            entity.setMaxHealth(hp);
            entity.setHealth(hp);

            baseHealth = hp;
            healthCalculated = true;

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Health calculation failed: %s", e.getMessage()));
            entity.setMaxHealth(20); // Fallback
            entity.setHealth(20);
        }
    }

    private int calculateBaseHealth() {
        return MobUtils.calculateArmorHealth(entity);
    }

    private int applyHealthModifiers(int baseHp) {
        return MobUtils.applyHealthMultiplier(baseHp, tier, elite);
    }

    private int applySpecialHealthValues(int hp) {
        switch (type) {
            case WARDEN:
                return 85000;
            case BOSS_SKELETON:
                return 115000;
            case FROSTWING:
            case CHRONOS:
                return ThreadLocalRandom.current().nextInt(210000, 234444);
            case FROZEN_ELITE:
                return YakRealms.isT6Enabled() ? 200000 : 100000;
            case FROZEN_BOSS:
                return YakRealms.isT6Enabled() ? 300000 : 200000;
            case FROZEN_GOLEM:
                return YakRealms.isT6Enabled() ? 400000 : 200000;
            default:
                return hp;
        }
    }

    // ================ TYPE-SPECIFIC PROPERTIES ================

    protected void applyTypeProperties() {
        if (entity == null) return;

        try {
            applyMovementEffects();
            applySpecialTypeEffects();
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Type properties application failed: %s", e.getMessage()));
        }
    }

    private void applyMovementEffects() {
        // Elite movement effects
        if (elite && !type.equals(MobType.FROZEN_BOSS)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        }

        // Tier-based movement
        if (tier > 2 && !type.equals(MobType.FROZEN_BOSS)) {
            ItemStack mainHand = entity.getEquipment().getItemInMainHand();
            if (mainHand != null && mainHand.getType().name().contains("_HOE")) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 1));
            } else {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
            }
        }
    }

    private void applySpecialTypeEffects() {
        switch (type) {
            case FROZEN_GOLEM:
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                break;
            case WEAK_SKELETON:
                entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1));
                break;
        }
    }

    // ================ DAMAGE PROCESSING ================

    /**
     * Enhanced damage processing with improved health bar management
     *
     * @param damage Amount of damage to apply
     * @return The actual damage dealt
     */
    public double damage(double damage) {
        if (!isValid()) {
            return 0;
        }

        try {
            double finalDamage = processDamage(damage);
            updateHealthState(finalDamage);

            return finalDamage;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Damage processing error: %s", e.getMessage()));
            return 0;
        }
    }

    private double processDamage(double damage) {
        // Allow for custom damage modifications in subclasses
        return damage;
    }

    private void updateHealthState(double damage) {
        double newHealth = entity.getHealth() - damage;

        if (newHealth <= 0) {
            handleDeath();
        } else {
            entity.setHealth(newHealth);
            updateDamageTime();
            updateHealthBar();
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

    public void updateHealthBar() {
        if (!isValid() || lastDamageTime == 0) return;

        try {
            storeOriginalNameIfNeeded();
            String healthBar = generateHealthBar();

            entity.setCustomName(healthBar);
            entity.setCustomNameVisible(true);

            lastDamageTime = System.currentTimeMillis();
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Health bar update failed: %s", e.getMessage()));
        }
    }

    private void storeOriginalNameIfNeeded() {
        if (originalName == null || originalName.isEmpty()) {
            if (entity.hasMetadata("name")) {
                String name = entity.getMetadata("name").get(0).asString();
                // Store as field (originalName is final, so this is for current name tracking)
                customName = name;
            } else if (entity.getCustomName() != null && !isHealthBar(entity.getCustomName())) {
                customName = entity.getCustomName();
            }
        }
    }

    private boolean isHealthBar(String name) {
        return name.contains(ChatColor.GREEN + "|") || name.contains(ChatColor.GRAY + "|");
    }

    public String generateHealthBar() {
        if (!isValid()) return "";

        try {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            // Ensure valid values
            if (health <= 0) health = 0.1;
            if (maxHealth <= 0) maxHealth = 1;

            double healthPercentage = health / maxHealth;

            StringBuilder bar = new StringBuilder();

            // Add tier color prefix
            bar.append(getTierColor(tier));

            // Generate health bar
            String barColor = inCriticalState ? ChatColor.LIGHT_PURPLE.toString() : ChatColor.GREEN.toString();
            int barLength = 40;

            for (int i = 1; i <= barLength; i++) {
                if (healthPercentage >= (double) i / barLength) {
                    bar.append(barColor).append("|");
                } else {
                    bar.append(ChatColor.GRAY).append("|");
                }
            }

            // Remove last character for non-elite mobs
            if (!elite && bar.length() > 0) {
                bar.setLength(bar.length() - 1);
            }

            return bar.toString();
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Health bar generation failed: %s", e.getMessage()));
            return originalName;
        }
    }

    // ================ NAME VISIBILITY MANAGEMENT ================

    public void updateNameVisibility() {
        if (!isValid()) return;

        try {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;

            if (recentlyDamaged || inCriticalState) {
                nameVisible = true;

                if (!isCurrentlyShowingHealthBar()) {
                    updateHealthBar();
                }
            } else if (nameVisible && !inCriticalState) {
                restoreName();
                nameVisible = false;
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Name visibility update failed: %s", e.getMessage()));
        }
    }

    private boolean isCurrentlyShowingHealthBar() {
        String currentName = entity.getCustomName();
        return currentName != null && currentName.contains("|");
    }

    public void restoreName() {
        if (!isValid() || inCriticalState) return;

        try {
            String nameToRestore = getNameToRestore();

            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                entity.setCustomName(nameToRestore);
                entity.setCustomNameVisible(true);

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info(String.format("§6[CustomMob] §7Restored name for %s: %s",
                            entity.getType(), nameToRestore));
                }
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Name restoration failed: %s", e.getMessage()));
        }
    }

    private String getNameToRestore() {
        // Try cached original name first
        if (originalName != null && !originalName.isEmpty()) {
            return originalName;
        }

        // Try metadata
        if (entity.hasMetadata("name")) {
            return entity.getMetadata("name").get(0).asString();
        }

        if (entity.hasMetadata("LightningMob")) {
            return entity.getMetadata("LightningMob").get(0).asString();
        }

        // Generate default name
        return generateDefaultName();
    }

    private String generateDefaultName() {
        String tierName = type.getTierSpecificName(tier);
        ChatColor tierColor = getTierColor(tier);

        return elite ? tierColor.toString() + ChatColor.BOLD + tierName : tierColor + tierName;
    }

    // ================ CRITICAL STATE SYSTEM ================

    public void setCriticalState(int duration) {
        if (!isValid()) return;

        try {
            this.inCriticalState = true;
            this.criticalStateDuration = duration;
            this.nameVisible = true;

            entity.setMetadata("criticalState", new FixedMetadataValue(YakRealms.getInstance(), true));

            updateHealthBar();
            applyCriticalEffects();

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Critical state application failed: %s", e.getMessage()));
        }
    }

    private void applyCriticalEffects() {
        if (!elite) {
            // Regular mob critical effects
            entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    20, 0.3, 0.3, 0.3, 0.5);
        }
        // Elite-specific effects handled in EliteMob class
    }

    public boolean processCriticalStateTick() {
        if (!inCriticalState || !isValid()) {
            return false;
        }

        try {
            // Don't process negative counters (ready state)
            if (criticalStateDuration < 0) {
                return false;
            }

            criticalStateDuration--;

            // Show periodic particles
            if (criticalStateDuration % 5 == 0) {
                showCriticalParticles();
            }

            // Maintain health bar visibility
            ensureHealthBarVisible();

            if (criticalStateDuration <= 0) {
                return handleCriticalStateEnd();
            }

            return false;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Critical state tick processing failed: %s", e.getMessage()));
            return false;
        }
    }

    private void showCriticalParticles() {
        org.bukkit.Particle particleType = elite ? org.bukkit.Particle.SPELL_WITCH : org.bukkit.Particle.CRIT;

        entity.getWorld().spawnParticle(particleType,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                5, 0.3, 0.3, 0.3, 0.05);
    }

    private void ensureHealthBarVisible() {
        if (!entity.getCustomName().contains("|")) {
            updateHealthBar();
        }
    }

    private boolean handleCriticalStateEnd() {
        if (!elite) {
            // Regular mobs transition to ready state
            criticalStateDuration = -1;
            return true;
        } else {
            // Elite mobs end critical state
            inCriticalState = false;

            if (entity.hasMetadata("criticalState")) {
                entity.removeMetadata("criticalState", YakRealms.getInstance());
            }

            return true;
        }
    }

    // ================ LIGHTNING ENHANCEMENT ================

    public void enhanceAsLightningMob(int multiplier) {
        if (!isValid()) return;

        try {
            this.lightningMultiplier = multiplier;

            // Increase health
            double newMaxHealth = entity.getMaxHealth() * multiplier;
            entity.setMaxHealth(newMaxHealth);
            entity.setHealth(newMaxHealth);

            // Apply visual effects
            entity.setGlowing(true);

            // Update name with lightning theme
            String lightningName = String.format("§6⚡ Lightning %s ⚡",
                    ChatColor.stripColor(entity.getCustomName()));
            entity.setCustomName(lightningName);
            entity.setCustomNameVisible(true);

            // Store lightning metadata
            YakRealms plugin = YakRealms.getInstance();
            entity.setMetadata("LightningMultiplier", new FixedMetadataValue(plugin, multiplier));
            entity.setMetadata("LightningMob", new FixedMetadataValue(plugin, lightningName));

            LOGGER.info(String.format("§6[CustomMob] §7Enhanced %s as lightning mob (x%d)",
                    type.getId(), multiplier));

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Lightning enhancement failed: %s", e.getMessage()));
        }
    }

    // ================ UTILITY METHODS ================

    public boolean isEligibleForCritical() {
        return !inCriticalState && isValid();
    }

    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public void remove() {
        try {
            if (isValid()) {
                MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);
                entity.remove();
            }

            MobManager.getInstance().unregisterMob(this);

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[CustomMob] Removal failed: %s", e.getMessage()));
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

    public MobType getType() { return type; }
    public int getTier() { return tier; }
    public boolean isElite() { return elite; }
    public LivingEntity getEntity() { return entity; }
    public String getCustomName() { return customName; }
    public boolean isInCriticalState() { return inCriticalState; }
    public int getCriticalStateDuration() { return criticalStateDuration; }
    public long getLastDamageTime() { return lastDamageTime; }
    public boolean isNameVisible() { return nameVisible; }
    public String getOriginalName() { return originalName; }
    public double getBaseHealth() { return baseHealth; }
    public boolean isHealthCalculated() { return healthCalculated; }
    public int getLightningMultiplier() { return lightningMultiplier; }
}
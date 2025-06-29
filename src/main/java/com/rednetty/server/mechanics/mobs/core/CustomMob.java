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
 * IMPROVED: Simplified CustomMob class with proper tier-specific name handling
 * Maintains name/health bar functionality while using MobType system correctly
 */
public class CustomMob {

    protected static final Random RANDOM = new Random();
    protected static final Logger LOGGER = YakRealms.getInstance().getLogger();
    protected static final int NAME_VISIBILITY_TIMEOUT = 6500; // 6.5 seconds
    protected static final long MIN_RESPAWN_DELAY = 180000; // 3 minutes

    // ================ CORE PROPERTIES ================
    protected final MobType type;
    protected final int tier;
    protected final boolean elite;

    // ================ NAME SYSTEM - PRESERVED ================
    private String originalName; // Proper tier-specific name
    protected String currentDisplayName; // What's currently shown
    private boolean originalNameStored = false;

    // ================ STATE VARIABLES ================
    protected LivingEntity entity;
    protected boolean inCriticalState;
    protected int criticalStateDuration;
    protected int lightningMultiplier = 0;
    protected long lastDamageTime = 0;

    // ================ VISIBILITY MANAGEMENT - PRESERVED ================
    public boolean nameVisible = true;
    private boolean showingHealthBar = false;

    // ================ HEALTH SYSTEM ================
    private double baseHealth = 0;

    /**
     * Create a new custom mob with proper tier-specific naming
     */
    public CustomMob(MobType type, int tier, boolean elite) {
        if (type == null) {
            throw new IllegalArgumentException("MobType cannot be null");
        }

        this.type = type;
        this.tier = Math.max(1, Math.min(6, tier));
        this.elite = elite;
        this.inCriticalState = false;
        this.criticalStateDuration = 0;
    }

    // ================ SPAWNING SYSTEM ================

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
                LOGGER.warning("[CustomMob] Failed to create entity for " + type.getId());
                return false;
            }

            if (!initializeMob()) {
                cleanup();
                return false;
            }

            registerWithManager();

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info("[CustomMob] Spawned " + type.getId() + " T" + tier + (elite ? "+" : "") +
                        " at " + formatLocation(spawnLoc));
            }

            return true;

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Spawn error for " + type.getId() + ": " + e.getMessage());
            cleanup();
            return false;
        }
    }

    private boolean validateSpawnLocation(Location location) {
        return location != null && location.getWorld() != null;
    }

    private boolean checkRespawnCooldown() {
        long currentTime = System.currentTimeMillis();
        long typeDeath = MobManager.getInstance().getLastDeathTime(type.getId(), tier, elite);

        if (typeDeath > 0) {
            long timeSinceDeath = currentTime - typeDeath;
            long requiredCooldown = calculateRespawnDelay();
            return timeSinceDeath >= requiredCooldown;
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
            LOGGER.warning("[CustomMob] Entity creation failed: " + e.getMessage());
            return null;
        }
    }

    private boolean initializeMob() {
        try {
            applyBasicProperties();
            applyEquipment();
            calculateAndSetHealth();
            applyTypeProperties();

            // Capture original name after full setup
            captureOriginalName();
            lastDamageTime = 0;
            return true;

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Initialization failed: " + e.getMessage());
            return false;
        }
    }

    private void registerWithManager() {
        MobManager.getInstance().registerMob(this);
        updateNameVisibility();
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

        setEntityMetadata();
        configureEntityAppearance();
        configureBehavior();
        applyBasicEffects();
        lastDamageTime = System.currentTimeMillis();
    }

    private void setEntityMetadata() {
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
    }

    /**
     * IMPROVED: Configure entity appearance using proper tier-specific names
     */
    private void configureEntityAppearance() {
        // Get tier-specific name from MobType system
        String tierSpecificName = type.getTierSpecificName(tier);

        // Format with proper tier color using MobUtils
        String formattedName = MobUtils.formatMobName(tierSpecificName, tier, elite);

        entity.setCustomName(formattedName);
        entity.setCustomNameVisible(true);
        currentDisplayName = formattedName;

        entity.setMetadata("name", new FixedMetadataValue(YakRealms.getInstance(), formattedName));
    }

    private void configureBehavior() {
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);

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

    // ================ NAME SYSTEM - PRESERVED WITH IMPROVEMENTS ================

    /**
     * IMPROVED: Original name capture using tier-specific names and MobUtils
     */
    private void captureOriginalName() {
        if (entity == null || originalNameStored) return;

        try {
            // Use MobUtils to capture the proper original name
            String capturedName = MobUtils.captureOriginalName(entity);

            if (capturedName != null && MobUtils.isValidRestorationName(capturedName)) {
                originalName = capturedName;
                originalNameStored = true;

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info("[CustomMob] Stored original name: '" + originalName + "'");
                }
            } else {
                // Fallback to generating the proper tier-specific name
                originalName = generateDefaultName();
                originalNameStored = true;
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Failed to capture original name: " + e.getMessage());
            // Fallback to default name generation
            originalName = generateDefaultName();
            originalNameStored = true;
        }
    }

    /**
     * PRESERVED: Name visibility management - keeps name/health bar switching
     */
    public void updateNameVisibility() {
        if (!isValid()) return;

        try {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;
            boolean shouldShowHealthBar = recentlyDamaged || inCriticalState;

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
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Name visibility update failed: " + e.getMessage());
        }
    }

    /**
     * IMPROVED: Original name restoration using tier-specific names
     */
    private void restoreOriginalName() {
        if (!isValid() || inCriticalState) return;

        try {
            String nameToRestore = originalName;
            if (nameToRestore == null || nameToRestore.isEmpty()) {
                nameToRestore = generateDefaultName();
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

    /**
     * IMPROVED: Generate default name using proper tier-specific names and MobUtils
     */
    private String generateDefaultName() {
        // Get tier-specific name from MobType system
        String tierSpecificName = type.getTierSpecificName(tier);

        // Format with proper tier color using MobUtils
        return MobUtils.formatMobName(tierSpecificName, tier, elite);
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

            equipment.setItemInMainHand(weapon);
            equipment.setHelmet(armor[0]);
            equipment.setChestplate(armor[1]);
            equipment.setLeggings(armor[2]);
            equipment.setBoots(armor[3]);

            setDropChances(equipment);

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Equipment application failed: " + e.getMessage());
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
            if (DropsManager.getInstance() != null) {
                ItemStack weapon = DropsManager.getInstance().createDrop(tier, getWeaponType());

                if (elite && weapon != null) {
                    weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                    applyTierColorToWeapon(weapon);
                }

                return weapon;
            } else {
                return createFallbackWeapon();
            }
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Weapon creation failed: " + e.getMessage());
            return createFallbackWeapon();
        }
    }

    private ItemStack createFallbackWeapon() {
        Material weaponMaterial;
        switch (tier) {
            case 1: weaponMaterial = Material.WOODEN_SWORD; break;
            case 2: weaponMaterial = Material.STONE_SWORD; break;
            case 3: weaponMaterial = Material.IRON_SWORD; break;
            case 4: weaponMaterial = Material.DIAMOND_SWORD; break;
            case 5: weaponMaterial = Material.GOLDEN_SWORD; break;
            case 6: weaponMaterial = Material.DIAMOND_SWORD; break;
            default: weaponMaterial = Material.WOODEN_SWORD;
        }

        ItemStack weapon = new ItemStack(weaponMaterial);
        if (elite) {
            weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
        }
        return weapon;
    }

    private void applyTierColorToWeapon(ItemStack weapon) {
        if (weapon.getItemMeta() != null && weapon.getItemMeta().hasDisplayName()) {
            ChatColor tierColor = MobUtils.getTierColor(tier);
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

        if (DropsManager.getInstance() != null) {
            while (gearPieces > 0) {
                int armorType = RANDOM.nextInt(4);

                if (armor[armorType] == null) {
                    int dropType = armorType + 5;
                    ItemStack piece = DropsManager.getInstance().createDrop(tier, dropType);

                    if (elite && piece != null) {
                        piece.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                        applySpecialArmorEffects(piece, armorType);
                    }

                    armor[armorType] = piece;
                    gearPieces--;
                }
            }
        } else {
            armor = createFallbackArmor(gearPieces);
        }

        return armor;
    }

    private ItemStack[] createFallbackArmor(int gearPieces) {
        ItemStack[] armor = new ItemStack[4];
        Material armorMaterial;

        switch (tier) {
            case 1: armorMaterial = Material.LEATHER_HELMET; break;
            case 2: armorMaterial = Material.CHAINMAIL_HELMET; break;
            case 3: armorMaterial = Material.IRON_HELMET; break;
            case 4: armorMaterial = Material.DIAMOND_HELMET; break;
            case 5: armorMaterial = Material.GOLDEN_HELMET; break;
            case 6: armorMaterial = Material.LEATHER_HELMET; break; // T6 special
            default: armorMaterial = Material.LEATHER_HELMET;
        }

        String baseName = armorMaterial.name().replace("_HELMET", "");

        while (gearPieces > 0) {
            int armorType = RANDOM.nextInt(4);
            if (armor[armorType] == null) {
                try {
                    switch (armorType) {
                        case 0: armor[0] = new ItemStack(Material.valueOf(baseName + "_HELMET")); break;
                        case 1: armor[1] = new ItemStack(Material.valueOf(baseName + "_CHESTPLATE")); break;
                        case 2: armor[2] = new ItemStack(Material.valueOf(baseName + "_LEGGINGS")); break;
                        case 3: armor[3] = new ItemStack(Material.valueOf(baseName + "_BOOTS")); break;
                    }

                    if (elite && armor[armorType] != null) {
                        armor[armorType].addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);
                    }
                } catch (IllegalArgumentException e) {
                    // Material doesn't exist, skip
                }
                gearPieces--;
            }
        }

        return armor;
    }

    private void applySpecialArmorEffects(ItemStack piece, int armorType) {
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

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health calculation failed: " + e.getMessage());
            entity.setMaxHealth(20);
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
            LOGGER.warning("[CustomMob] Type properties application failed: " + e.getMessage());
        }
    }

    private void applyMovementEffects() {
        if (elite && !type.equals(MobType.FROZEN_BOSS)) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        }

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

    // ================ HEALTH BAR SYSTEM - PRESERVED ================

    /**
     * PRESERVED: Health bar update system
     */
    public void updateHealthBar() {
        if (!isValid()) return;

        try {
            String healthBar = generateHealthBar();

            entity.setCustomName(healthBar);
            entity.setCustomNameVisible(true);
            currentDisplayName = healthBar;
            showingHealthBar = true;

            lastDamageTime = System.currentTimeMillis();
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health bar update failed: " + e.getMessage());
        }
    }

    /**
     * IMPROVED: Health bar generation using MobUtils for consistency
     */
    public String generateHealthBar() {
        if (!isValid()) return "";

        try {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            if (health <= 0) health = 0.1;
            if (maxHealth <= 0) maxHealth = 1;

            // Use MobUtils for consistent health bar generation
            return MobUtils.generateHealthBar(entity, health, maxHealth, tier, inCriticalState);
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Health bar generation failed: " + e.getMessage());
            return originalName != null ? originalName : generateDefaultName();
        }
    }

    // ================ CRITICAL STATE SYSTEM - PRESERVED ================

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
            LOGGER.warning("[CustomMob] Critical state application failed: " + e.getMessage());
        }
    }

    private void applyCriticalEffects() {
        if (!elite) {
            entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    20, 0.3, 0.3, 0.3, 0.5);
        }
    }

    public boolean processCriticalStateTick() {
        if (!inCriticalState || !isValid()) {
            return false;
        }

        try {
            if (criticalStateDuration < 0) {
                return false;
            }

            criticalStateDuration--;

            if (!elite && criticalStateDuration > 0 && criticalStateDuration % 40 == 0) {
                entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);
            }

            if (criticalStateDuration % 5 == 0) {
                showCriticalParticles();
            }

            if (!showingHealthBar) {
                updateHealthBar();
            }

            if (criticalStateDuration <= 0) {
                return handleCriticalStateEnd();
            }

            return false;
        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Critical state tick processing failed: " + e.getMessage());
            return false;
        }
    }

    private void showCriticalParticles() {
        org.bukkit.Particle particleType = elite ? org.bukkit.Particle.SPELL_WITCH : org.bukkit.Particle.CRIT;

        entity.getWorld().spawnParticle(particleType,
                entity.getLocation().clone().add(0.0, 1.0, 0.0),
                5, 0.3, 0.3, 0.3, 0.05);
    }

    private boolean handleCriticalStateEnd() {
        if (!elite) {
            criticalStateDuration = -1;
            return true;
        } else {
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

            double newMaxHealth = entity.getMaxHealth() * multiplier;
            entity.setMaxHealth(newMaxHealth);
            entity.setHealth(newMaxHealth);

            entity.setGlowing(true);

            // Generate proper lightning name using tier-specific name
            String tierSpecificName = type.getTierSpecificName(tier);
            String lightningName = String.format("§6⚡ Lightning %s ⚡",
                    ChatColor.stripColor(tierSpecificName));

            entity.setCustomName(lightningName);
            entity.setCustomNameVisible(true);

            originalName = lightningName;
            currentDisplayName = lightningName;

            YakRealms plugin = YakRealms.getInstance();
            entity.setMetadata("LightningMultiplier", new FixedMetadataValue(plugin, multiplier));
            entity.setMetadata("LightningMob", new FixedMetadataValue(plugin, lightningName));

            LOGGER.info("[CustomMob] Enhanced " + type.getId() + " as lightning mob (x" + multiplier + ")");

        } catch (Exception e) {
            LOGGER.warning("[CustomMob] Lightning enhancement failed: " + e.getMessage());
        }
    }

    // ================ UTILITY METHODS ================

    public boolean isEligibleForCritical() {
        return !inCriticalState && isValid();
    }

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

    public MobType getType() { return type; }
    public int getTier() { return tier; }
    public boolean isElite() { return elite; }
    public LivingEntity getEntity() { return entity; }
    public String getCustomName() { return currentDisplayName; }
    public boolean isInCriticalState() { return inCriticalState; }
    public int getCriticalStateDuration() { return criticalStateDuration; }
    public long getLastDamageTime() { return lastDamageTime; }
    public boolean isNameVisible() { return nameVisible; }
    public String getOriginalName() { return originalName; }
    public String getFormattedOriginalName() { return originalName; }
    public double getBaseHealth() { return baseHealth; }
    public int getLightningMultiplier() { return lightningMultiplier; }
    public boolean isShowingHealthBar() { return showingHealthBar; }
}
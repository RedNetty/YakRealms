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
 * Base class for all custom mobs
 */
public class CustomMob {
    protected static final Random random = new Random();
    protected static final Logger logger = YakRealms.getInstance().getLogger();
    protected static final int NAME_VISIBILITY_TIMEOUT = 5000; // 5 seconds

    protected MobType type;
    protected int tier;
    protected boolean elite;
    protected LivingEntity entity;
    protected String customName;
    protected boolean inCriticalState;
    protected int criticalStateDuration;
    protected int lightningMultiplier = 0;
    protected long lastDamageTime = -1000000;
    public boolean nameVisible = true; // Initially true - show name by default until damaged
    protected String originalName = ""; // Store original name for restoration

    // RESPAWN FIX: Add respawn tracking
    protected long lastDeathTime = 0;
    protected static final long MIN_RESPAWN_DELAY = 180000; // 3 minutes minimum

    /**
     * Create a new custom mob
     *
     * @param type  Mob type
     * @param tier  Mob tier (1-6)
     * @param elite Whether this is an elite mob
     */
    public CustomMob(MobType type, int tier, boolean elite) {
        this.type = type;
        this.tier = tier;
        this.elite = elite;
        this.customName = type.getTierSpecificName(tier);
        this.originalName = this.customName; // Store original name immediately
        this.inCriticalState = false;
        this.criticalStateDuration = 0;
    }

    /**
     * Spawn the mob at a location
     *
     * @param location Location to spawn at
     * @return true if spawned successfully
     */
    public boolean spawn(Location location) {
        try {
            // RESPAWN FIX: Check for minimum respawn time
            long currentTime = System.currentTimeMillis();

            // Get the last death time for this mob type+tier from MobManager
            long typeDeath = MobManager.getInstance().getLastDeathTime(type.getId(), tier, elite);
            if (typeDeath > 0) {
                long timeSinceTypeDeath = currentTime - typeDeath;

                // Calculate minimum respawn time based on tier and elite status
                long respawnTime = calculateRespawnDelay();

                if (timeSinceTypeDeath < respawnTime) {
                    // Still in respawn cooldown period
                    long remainingSeconds = (respawnTime - timeSinceTypeDeath) / 1000;
                    logger.info("[CustomMob] PREVENTED respawn of " + type.getId() +
                            " T" + tier + (elite ? "+" : "") +
                            " - Respawn available in " + remainingSeconds + " seconds");
                    return false;
                } else {
                    logger.info("[CustomMob] Allowing respawn of " + type.getId() +
                            " T" + tier + (elite ? "+" : "") +
                            " after " + (timeSinceTypeDeath / 1000) + " seconds");
                }
            }

            // Get a safe spawn location
            Location spawnLoc = getSpawnLocation(location);

            // Create entity
            entity = createEntity(spawnLoc);
            if (entity == null) return false;

            // Apply basic properties
            applyBasicProperties();

            // Apply equipment
            applyEquipment();

            // Apply health
            calculateAndSetHealth();

            // Apply type-specific properties
            applyTypeProperties();

            lastDamageTime = 0;
            // Register with manager
            MobManager.getInstance().registerMob(this);
            updateNameVisibility();

            return true;
        } catch (Exception e) {
            YakRealms.getInstance().getLogger().warning("Error spawning mob: " + e.getMessage());
            return false;
        }
    }

    /**
     * Calculate respawn delay based on tier and elite status
     * Delegates to MobManager for consistency
     *
     * @return Respawn delay in milliseconds
     */
    protected long calculateRespawnDelay() {
        return com.rednetty.server.mechanics.mobs.MobManager.getInstance()
                .calculateRespawnDelay(tier, elite);
    }


    /**
     * Create the actual entity at the location
     *
     * @param location Spawn location
     * @return Created entity
     */
    protected LivingEntity createEntity(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        EntityType entityType = type.getEntityType();
        Entity entity = world.spawnEntity(location, entityType);

        if (entity instanceof LivingEntity) {
            return (LivingEntity) entity;
        } else {
            entity.remove();
            return null;
        }
    }

    /**
     * Get a safe spawn location near the target location
     *
     * @param baseLocation Base location
     * @return Safe location for spawning
     */
    protected Location getSpawnLocation(Location baseLocation) {
        int randX = random.nextInt(7) - 3;
        int randZ = random.nextInt(7) - 3;

        Location spawnLoc = new Location(
                baseLocation.getWorld(),
                baseLocation.getX() + randX + 0.5,
                baseLocation.getY() + 2.0,
                baseLocation.getZ() + randZ + 0.5
        );

        // Check if there's enough space for the mob
        if (spawnLoc.getBlock().getType().isSolid() ||
                spawnLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {

            // Fall back to the original location with a small offset
            return baseLocation.clone().add(0, 1, 0);
        } else {
            // Adjust to ground level
            spawnLoc.subtract(0, 1, 0);
            return spawnLoc;
        }
    }

    /**
     * Apply basic properties to the entity
     */
    protected void applyBasicProperties() {
        if (entity == null) return;

        // Add essential metadata for the Drops system and identification
        entity.setMetadata("type", new FixedMetadataValue(YakRealms.getInstance(), type.getId()));

        // IMPORTANT: Store tier as both string and integer to ensure compatibility
        entity.setMetadata("tier", new FixedMetadataValue(YakRealms.getInstance(), String.valueOf(tier)));
        entity.setMetadata("customTier", new FixedMetadataValue(YakRealms.getInstance(), tier));

        entity.setMetadata("elite", new FixedMetadataValue(YakRealms.getInstance(), elite));

        // Use tier-specific name from MobType
        String tierSpecificName = type.getTierSpecificName(tier);
        this.customName = tierSpecificName;
        this.originalName = tierSpecificName; // Store original name

        // Format with appropriate color and styling
        ChatColor tierColor;
        switch (tier) {
            case 1:
                tierColor = ChatColor.WHITE;
                break;
            case 2:
                tierColor = ChatColor.GREEN;
                break;
            case 3:
                tierColor = ChatColor.AQUA;
                break;
            case 4:
                tierColor = ChatColor.LIGHT_PURPLE;
                break;
            case 5:
                tierColor = ChatColor.YELLOW;
                break;
            case 6:
                tierColor = ChatColor.BLUE;
                break;
            default:
                tierColor = ChatColor.WHITE;
                break;
        }

        // Apply bold formatting for elite mobs
        String formattedName = elite ?
                tierColor.toString() + ChatColor.BOLD + tierSpecificName :
                tierColor + tierSpecificName;

        this.originalName = formattedName; // Store the formatted name as original

        // Set name and visibility
        entity.setCustomName(formattedName);
        entity.setCustomNameVisible(true);

        // Store original name in metadata for future reference
        entity.setMetadata("name", new FixedMetadataValue(YakRealms.getInstance(), formattedName));
        entity.setMetadata("customName", new FixedMetadataValue(YakRealms.getInstance(), type.getId()));

        // Special metadata for certain types
        if (type.isWorldBoss()) {
            entity.setMetadata("worldboss", new FixedMetadataValue(YakRealms.getInstance(), true));
        }

        // IMPORTANT: Store drop data explicitly for the drops system
        entity.setMetadata("dropTier", new FixedMetadataValue(YakRealms.getInstance(), tier));
        entity.setMetadata("dropElite", new FixedMetadataValue(YakRealms.getInstance(), elite));

        // Apply basic effects
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1));

        // Apply type-specific adjustments
        if (entity instanceof Zombie) {
            ((Zombie) entity).setBaby(false);
        } else if (entity instanceof PigZombie) {
            PigZombie pigZombie = (PigZombie) entity;
            pigZombie.setAngry(true);

            if (type == MobType.IMP || type == MobType.SPECTRAL_GUARD) {
                pigZombie.setBaby(true);
            } else {
                pigZombie.setBaby(false);
            }
        } else if (entity instanceof MagmaCube) {
            ((MagmaCube) entity).setSize(3);
        }

        // Initialize last damage time to ensure health bar visibility
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Apply mob equipment based on tier and elite status
     */
    protected void applyEquipment() {
        if (entity == null) return;

        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        // Clear existing equipment
        equipment.clear();

        // Determine gear distribution
        int gearPieces = calculateGearPieces();

        // Create weapon
        ItemStack weapon = createWeapon();
        if (elite && weapon != null) {
            weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);

            // Add metadata to weapon for drop system identification
            if (weapon.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = weapon.getItemMeta();
                String displayName = meta.hasDisplayName() ? meta.getDisplayName() : "";

                // Apply tier-based color
                ChatColor tierColor;
                switch (tier) {
                    case 6:
                        tierColor = ChatColor.BLUE;
                        break;
                    case 5:
                        tierColor = ChatColor.YELLOW;
                        break;
                    case 4:
                        tierColor = ChatColor.LIGHT_PURPLE;
                        break;
                    case 3:
                        tierColor = ChatColor.AQUA;
                        break;
                    case 2:
                        tierColor = ChatColor.GREEN;
                        break;
                    default:
                        tierColor = ChatColor.WHITE;
                        break;
                }

                if (!displayName.startsWith(tierColor.toString())) {
                    meta.setDisplayName(tierColor + ChatColor.stripColor(displayName));
                    weapon.setItemMeta(meta);
                }
            }
        }

        // Create armor pieces
        ItemStack[] armor = createArmorSet(gearPieces);

        // Apply equipment
        equipment.setItemInMainHand(weapon);
        equipment.setHelmet(armor[0]);
        equipment.setChestplate(armor[1]);
        equipment.setLeggings(armor[2]);
        equipment.setBoots(armor[3]);

        // Set drop chances to 0 to prevent drops
        equipment.setItemInMainHandDropChance(0);
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setLeggingsDropChance(0);
        equipment.setBootsDropChance(0);

        // Add extra metadata for tier identification via equipment
        if (weapon != null && weapon.getType() != Material.AIR) {
            String itemType = weapon.getType().name();
            if (itemType.contains("WOODEN_") || itemType.contains("WOOD_")) {
                entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), 1));
            } else if (itemType.contains("STONE_")) {
                entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), 2));
            } else if (itemType.contains("IRON_")) {
                entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), 3));
            } else if (itemType.contains("DIAMOND_")) {
                if (weapon.getItemMeta() != null && weapon.getItemMeta().hasDisplayName() &&
                        weapon.getItemMeta().getDisplayName().contains(ChatColor.BLUE.toString())) {
                    entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), 6));
                } else {
                    entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), 4));
                }
            } else if (itemType.contains("GOLDEN_") || itemType.contains("GOLD_")) {
                entity.setMetadata("equipTier", new FixedMetadataValue(YakRealms.getInstance(), 5));
            }
        }
    }

    /**
     * Calculate how many gear pieces this mob should have
     */
    protected int calculateGearPieces() {
        int gearPieces = random.nextInt(3) + 1;

        if (tier == 3) {
            int m_type = random.nextInt(2);
            gearPieces = (m_type == 0) ? 3 : 4;
        }

        if (tier >= 4 || elite) {
            gearPieces = 4;
        }

        return gearPieces;
    }

    /**
     * Create a weapon for this mob with proper tier handling
     */
    protected ItemStack createWeapon() {
        // IMPORTANT: Ensure we're creating a weapon of appropriate tier
        // FIXED: Now uses exact tier without any adjustments
        ItemStack weapon = DropsManager.getInstance().createDrop(tier, getWeaponType());

        // Add enchantment for elites
        if (elite && weapon != null) {
            weapon.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);

            // FIXED: Ensure weapon name matches exact tier color
            if (weapon.getItemMeta() != null) {
                ChatColor tierColor;
                switch (tier) {
                    case 6:
                        tierColor = ChatColor.BLUE;
                        break;
                    case 5:
                        tierColor = ChatColor.YELLOW;
                        break;
                    case 4:
                        tierColor = ChatColor.LIGHT_PURPLE;
                        break;
                    case 3:
                        tierColor = ChatColor.AQUA;
                        break;
                    case 2:
                        tierColor = ChatColor.GREEN;
                        break;
                    default:
                        tierColor = ChatColor.WHITE;
                        break;
                }

                if (weapon.getItemMeta().hasDisplayName()) {
                    String displayName = weapon.getItemMeta().getDisplayName();

                    // Only modify if color doesn't match tier
                    if (!displayName.startsWith(tierColor.toString())) {
                        org.bukkit.inventory.meta.ItemMeta meta = weapon.getItemMeta();
                        meta.setDisplayName(tierColor + ChatColor.stripColor(displayName));
                        weapon.setItemMeta(meta);
                    }
                }
            }
        }

        return weapon;
    }

    /**
     * Get the weapon type for drops
     */
    protected int getWeaponType() {
        int weaponType = random.nextInt(5);
        if (weaponType == 0) weaponType = 1;
        if (weaponType == 1) {
            weaponType = ThreadLocalRandom.current().nextInt(1, 5);
        }
        return weaponType;
    }

    /**
     * Create a full set of armor with proper tier handling
     *
     * @param gearPieces How many pieces to create
     * @return Array of armor items [helmet, chestplate, leggings, boots]
     */
    protected ItemStack[] createArmorSet(int gearPieces) {
        ItemStack[] armor = new ItemStack[4];

        while (gearPieces > 0) {
            int armorType = random.nextInt(4);

            if (armor[armorType] == null) {
                // Convert to drop type (5=helmet, 6=chestplate, etc.)
                int dropType = armorType + 5;

                // FIXED: Now uses exact tier without any adjustments
                ItemStack piece = DropsManager.getInstance().createDrop(tier, dropType);

                if (elite && piece != null) {
                    piece.addUnsafeEnchantment(Enchantment.LOOT_BONUS_MOBS, 1);

                    // For T6 mobs, set blue leather armor
                    if (tier == 6 && armorType == 0 && piece.getType().name().contains("LEATHER_")) {
                        org.bukkit.inventory.meta.ItemMeta meta = piece.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.BLUE + "T6 Armor Piece");
                            piece.setItemMeta(meta);
                        }
                    }
                }

                armor[armorType] = piece;
                gearPieces--;
            }
        }

        return armor;
    }

    /**
     * Calculate and set the entity's health
     */
    protected void calculateAndSetHealth() {
        if (entity == null) return;

        // Calculate HP from armor
        int hp = MobUtils.calculateArmorHealth(entity);

        // Apply the tier multiplier
        hp = MobUtils.applyHealthMultiplier(hp, tier, elite);

        // Special health values for certain types
        if (type == MobType.WARDEN) {
            hp = 85000;
        } else if (type == MobType.BOSS_SKELETON) {
            hp = 115000;
        } else if (type == MobType.FROSTWING || type == MobType.CHRONOS) {
            hp = ThreadLocalRandom.current().nextInt(210000, 234444);
        } else if (type == MobType.FROZEN_ELITE) {
            hp = YakRealms.isT6Enabled() ? 200000 : 100000;
        } else if (type == MobType.FROZEN_BOSS) {
            hp = YakRealms.isT6Enabled() ? 300000 : 200000;
        } else if (type == MobType.FROZEN_GOLEM) {
            hp = YakRealms.isT6Enabled() ? 400000 : 200000;
        }

        // Apply lightning multiplier if applicable
        if (lightningMultiplier > 0) {
            hp *= lightningMultiplier;
        }

        // Ensure minimum health
        if (hp < 1) hp = 1;

        // Set entity health
        entity.setMaxHealth(hp);
        entity.setHealth(hp);
    }

    /**
     * Apply type-specific properties
     */
    protected void applyTypeProperties() {
        if (entity == null) return;

        // Apply movement effects
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

        // Special type properties
        if (type == MobType.FROZEN_GOLEM) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
        } else if (type == MobType.WEAK_SKELETON) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1));
        }
    }

    /**
     * Process damage to the mob with improved health bar updates
     *
     * @param damage Amount of damage
     * @return The actual damage dealt
     */
    public double damage(double damage) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return 0;
        }

        double finalDamage = damage;
        // Custom damage modifications can be applied here

        // Apply damage
        double health = entity.getHealth() - finalDamage;
        if (health <= 0) {
            entity.setHealth(0);

            // RESPAWN FIX: Record death time in MobManager when killed
            MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);

            return finalDamage;
        } else {
            entity.setHealth(health);

            // IMMEDIATE update - matching original behavior
            lastDamageTime = System.currentTimeMillis();
            nameVisible = true;

            // Generate new health bar exactly as in original Mobs code
            updateHealthBar();

            return finalDamage;
        }
    }

    /**
     * Generate a health bar string based on current health
     *
     * @return Formatted health bar string
     */
    public String generateHealthBar() {
        boolean boss = elite;

        // Set color based on tier
        String str = "";
        switch (tier) {
            case 1:
                str = ChatColor.WHITE + "";
                break;
            case 2:
                str = ChatColor.GREEN + "";
                break;
            case 3:
                str = ChatColor.AQUA + "";
                break;
            case 4:
                str = ChatColor.LIGHT_PURPLE + "";
                break;
            case 5:
                str = ChatColor.YELLOW + "";
                break;
            case 6:
                str = ChatColor.BLUE + "";
                break;
            default:
                str = ChatColor.WHITE + "";
                break;
        }

        // Calculate health percentage
        double health = entity.getHealth();
        double maxHealth = entity.getMaxHealth();

        if (health <= 0) health = 0.1;
        if (maxHealth <= 0) maxHealth = 1;

        double perc = health / maxHealth;
        int lines = 40;

        // Set bar color based on critical state
        String barColor = inCriticalState ?
                ChatColor.LIGHT_PURPLE.toString() : ChatColor.GREEN.toString();

        // Generate the bar
        for (int i = 1; i <= lines; ++i) {
            str = perc >= (double) i / (double) lines ?
                    str + barColor + "|" : str + ChatColor.GRAY + "|";
        }

        // Remove last character for non-elite mobs
        if (!boss) {
            str = str.substring(0, str.length() - 1);
        }

        return str;
    }

    /**
     * Update the health bar display
     */
    public void updateHealthBar() {
        if (entity == null || !entity.isValid()) return;


        if (lastDamageTime == 0) return;

        // Store original name first if we don't have it yet
        if (originalName == null || originalName.isEmpty()) {
            if (entity.hasMetadata("name")) {
                originalName = entity.getMetadata("name").get(0).asString();
            } else if (entity.getCustomName() != null &&
                    !entity.getCustomName().contains(ChatColor.GREEN + "|") &&
                    !entity.getCustomName().contains(ChatColor.GRAY + "|")) {
                originalName = entity.getCustomName();
            }
        }

        // Generate health bar
        String healthBar = generateHealthBar();

        // Update display
        entity.setCustomName(healthBar);
        entity.setCustomNameVisible(true);

        // Record update time
        lastDamageTime = System.currentTimeMillis();
    }

    /**
     * Update name visibility - restore original name if not damaged recently
     */
    public void updateNameVisibility() {
        if (entity == null || !entity.isValid()) return;

        long now = System.currentTimeMillis();
        boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;

        // Only show health bar if damaged recently OR in critical state
        if (recentlyDamaged || inCriticalState) {
            nameVisible = true;

            // Only update the health bar if necessary
            if (!entity.getCustomName().contains("|")) {
                updateHealthBar();
            }
        }
        // Otherwise restore original name - but don't restore during critical state
        else if (nameVisible && !inCriticalState) {
            restoreName();
            nameVisible = false;
        }
    }

    /**
     * Set mob in critical state
     *
     * @param duration Duration in ticks
     */
    public void setCriticalState(int duration) {
        if (entity == null || !entity.isValid()) return;

        this.inCriticalState = true;
        this.criticalStateDuration = duration;
        this.nameVisible = true;

        // Store critical state in metadata
        entity.setMetadata("criticalState",
                new FixedMetadataValue(YakRealms.getInstance(), true));

        // Update health bar
        updateHealthBar();

        // Add non-elite effects
        if (!elite) {
            // Regular mobs get standard effects
            entity.getWorld().playSound(entity.getLocation(),
                    org.bukkit.Sound.BLOCK_PISTON_EXTEND, 1.0f, 2.0f);

            // Standard particle effect
            entity.getWorld().spawnParticle(
                    org.bukkit.Particle.CRIT,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    20, 0.3, 0.3, 0.3, 0.5
            );
        }
        // Elite-specific effects handled in EliteMob class
    }

    /**
     * Process critical state tick
     *
     * @return true if the critical state ended
     */
    public boolean processCriticalStateTick() {
        if (!inCriticalState || entity == null || !entity.isValid()) {
            return false;
        }

        // FIX: Don't keep processing negative counters (ready state)
        if (criticalStateDuration < 0) {
            // Maintain ready state but don't spam logs
            return false;
        }

        criticalStateDuration--;

        // Show particles during critical state
        if (criticalStateDuration % 5 == 0) {
            org.bukkit.Particle particleType = elite ?
                    org.bukkit.Particle.SPELL_WITCH : org.bukkit.Particle.CRIT;

            entity.getWorld().spawnParticle(
                    particleType,
                    entity.getLocation().clone().add(0.0, 1.0, 0.0),
                    5, 0.3, 0.3, 0.3, 0.05
            );
        }

        // Always keep health bar visible during critical state
        if (!entity.getCustomName().contains("|")) {
            updateHealthBar();
        }

        if (criticalStateDuration <= 0) {
            // For normal mobs, transition to ready state instead of ending
            if (!elite) {
                // Set to ready state
                criticalStateDuration = -1;
                return true;
            } else {
                // For elite mobs, we'll end the critical state
                inCriticalState = false;

                // Clear metadata
                if (entity.hasMetadata("criticalState")) {
                    entity.removeMetadata("criticalState", YakRealms.getInstance());
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Enhance this mob as a lightning mob
     *
     * @param multiplier Stat multiplier
     */
    public void enhanceAsLightningMob(int multiplier) {
        if (entity == null) return;

        this.lightningMultiplier = multiplier;

        // Increase health
        double newHealth = entity.getMaxHealth() * multiplier;
        entity.setMaxHealth(newHealth);
        entity.setHealth(newHealth);

        // Add visual effects - now only using glow effect, no lightning
        entity.setGlowing(true);

        // Update name
        String lightningName = ChatColor.GOLD + "⚡ Lightning " +
                ChatColor.stripColor(entity.getCustomName()) + " ⚡";
        entity.setCustomName(lightningName);
        entity.setCustomNameVisible(true);

        // Store lightning name as the new original name
        this.originalName = lightningName;

        // Add metadata
        entity.setMetadata("LightningMultiplier",
                new FixedMetadataValue(YakRealms.getInstance(), multiplier));
        entity.setMetadata("LightningMob",
                new FixedMetadataValue(YakRealms.getInstance(), lightningName));
    }

    /**
     * Check if the mob should roll for a critical hit
     *
     * @return true if eligible for critical hit
     */
    public boolean isEligibleForCritical() {
        return !inCriticalState && entity != null && entity.isValid();
    }

    /**
     * Remove the mob
     */
    public void remove() {
        if (entity != null && entity.isValid()) {
            // RESPAWN FIX: Record death time for this mob type/tier in MobManager
            MobManager.getInstance().recordMobDeath(type.getId(), tier, elite);

            entity.remove();
        }

        MobManager.getInstance().unregisterMob(this);
    }

    /**
     * Check if the mob is still valid
     */
    public boolean isValid() {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    /**
     * Restore the mob's original name (after health bar) with improved reliability
     */
    public void restoreName() {
        if (entity == null || !entity.isValid()) return;

        // Don't restore name during critical state
        if (inCriticalState) {
            return;
        }

        // Try using our cached originalName first
        if (originalName != null && !originalName.isEmpty()) {
            entity.setCustomName(originalName);
            entity.setCustomNameVisible(true);
            if (logger != null && originalName != null) {
                logger.info("[CustomMob] Restored original name for " + entity.getType() + ": " + originalName);
            }
            return;
        }

        // Fall back to metadata sources
        if (entity.hasMetadata("name")) {
            originalName = entity.getMetadata("name").get(0).asString();
            entity.setCustomName(originalName);
            entity.setCustomNameVisible(true);
            return;
        }

        if (entity.hasMetadata("LightningMob")) {
            originalName = entity.getMetadata("LightningMob").get(0).asString();
            entity.setCustomName(originalName);
            entity.setCustomNameVisible(true);
            return;
        }

        // Last resort: generate a name based on mob type
        MobType mobType = type;
        int mobTier = tier;
        boolean isElite = elite;

        // Set color based on tier
        ChatColor tierColor;
        switch (mobTier) {
            case 1:
                tierColor = ChatColor.WHITE;
                break;
            case 2:
                tierColor = ChatColor.GREEN;
                break;
            case 3:
                tierColor = ChatColor.AQUA;
                break;
            case 4:
                tierColor = ChatColor.LIGHT_PURPLE;
                break;
            case 5:
                tierColor = ChatColor.YELLOW;
                break;
            case 6:
                tierColor = ChatColor.BLUE;
                break;
            default:
                tierColor = ChatColor.WHITE;
                break;
        }

        String baseName = mobType.getTierSpecificName(mobTier);

        // Apply elite formatting if needed
        if (isElite) {
            originalName = tierColor.toString() + ChatColor.BOLD + baseName;
        } else {
            originalName = tierColor + baseName;
        }

        entity.setCustomName(originalName);
        entity.setCustomNameVisible(true);
    }

    // Getters
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
        return customName;
    }

    public boolean isInCriticalState() {
        return inCriticalState;
    }

    public int getCriticalStateDuration() {
        return criticalStateDuration;
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
}
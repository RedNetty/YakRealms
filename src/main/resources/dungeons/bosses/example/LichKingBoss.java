package com.rednetty.server.mechanics.dungeons.bosses.example;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.dungeons.bosses.DungeonBoss;
import com.rednetty.server.mechanics.dungeons.config.DungeonTemplate;
import com.rednetty.server.mechanics.dungeons.instance.DungeonInstance;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * EXAMPLE: Complete Lich King Boss Implementation
 *
 * A fully featured example boss showcasing all aspects of the dungeon boss system.
 * This boss demonstrates multi-phase combat, complex abilities, environmental effects,
 * and advanced AI behaviors.
 *
 * Boss Overview:
 * - Tier 5 Elite Skeleton Boss
 * - 3 Combat Phases with unique mechanics
 * - 12 Different abilities across phases
 * - Environmental arena effects
 * - Minion summoning and necromancy
 * - Dynamic difficulty scaling
 * - Phase-specific vulnerabilities
 *
 * Phase 1 (100% - 65% HP): "The Awakening"
 * - Basic melee and ranged attacks
 * - Occasional skeleton minion summoning
 * - Frost aura that slows nearby players
 *
 * Phase 2 (65% - 30% HP): "Necromantic Fury"
 * - Increased aggression and new abilities
 * - Area denial with bone spikes
 * - Undead resurrection mechanics
 * - Teleportation and positioning
 *
 * Phase 3 (30% - 0% HP): "Death's Embrace"
 * - Desperate and dangerous abilities
 * - Massive area attacks
 * - Environmental hazards
 * - Final stand mechanics
 */
public class LichKingBoss extends DungeonBoss {

    // ================ BOSS CONSTANTS ================

    private static final String BOSS_ID = "lich_king";
    private static final String BOSS_NAME = "§5§lThe Lich King";
    private static final int BOSS_TIER = 5;
    private static final boolean IS_ELITE = true;

    // Phase health thresholds
    private static final double PHASE_2_THRESHOLD = 0.65; // 65% HP
    private static final double PHASE_3_THRESHOLD = 0.30; // 30% HP

    // Arena configuration
    private static final double ARENA_RADIUS = 25.0;
    private static final int ARENA_BARRIER_HEIGHT = 4;

    // ================ CUSTOM STATE TRACKING ================

    private final Logger logger = YakRealms.getInstance().getLogger();
    private final Set<UUID> summonedMinions = new HashSet<>();
    private final Set<Location> boneSpikeLocations = new HashSet<>();
    private final Map<UUID, Long> playerCurseTimers = new HashMap<>();

    // Phase-specific data
    private boolean phase1FrostAuraActive = false;
    private boolean phase2TeleportUnlocked = false;
    private boolean phase3BerserkActive = false;
    private int phase3DesperationStacks = 0;

    // Environmental effects
    private boolean arenaBarriersActive = false;
    private long lastEnvironmentalEffect = 0;
    private final List<Location> ritualCircles = new ArrayList<>();

    // ================ CONSTRUCTOR ================

    public LichKingBoss(DungeonInstance dungeonInstance) {
        super(dungeonInstance, createLichKingDefinition());
    }

    /**
     * Create the boss definition for the Lich King
     */
    private static DungeonTemplate.BossDefinition createLichKingDefinition() {
        DungeonTemplate.BossDefinition definition = new DungeonTemplate.BossDefinition(
                BOSS_ID, "boss_chamber", "witherskeleton", BOSS_TIER, IS_ELITE);

        // Add all abilities
        definition.getAbilities().addAll(Arrays.asList(
                // Phase 1 abilities
                "lich_melee_strike", "bone_bolt", "summon_skeletons", "frost_aura",
                // Phase 2 abilities
                "bone_spikes", "necromantic_heal", "shadow_teleport", "curse_of_weakness",
                // Phase 3 abilities
                "death_nova", "raise_undead", "soul_drain", "apocalypse"
        ));

        // Configure phases
        definition.getPhases().add(new DungeonTemplate.PhaseDefinition(1, 1.0));
        definition.getPhases().add(new DungeonTemplate.PhaseDefinition(2, PHASE_2_THRESHOLD));
        definition.getPhases().add(new DungeonTemplate.PhaseDefinition(3, PHASE_3_THRESHOLD));

        // Custom properties
        definition.getCustomProperties().put("arena_radius", ARENA_RADIUS);
        definition.getCustomProperties().put("has_barriers", true);
        definition.getCustomProperties().put("environmental_effects", true);

        return definition;
    }

    // ================ INITIALIZATION OVERRIDE ================

    @Override
    public boolean initialize() {
        if (!super.initialize()) {
            return false;
        }

        try {
            // Initialize custom abilities
            initializeLichKingAbilities();

            // Setup arena-specific elements
            setupLichKingArena();

            // Initialize ritual circles for environmental effects
            initializeRitualCircles();

            logger.info("§a[LichKingBoss] §7Successfully initialized The Lich King boss");
            return true;

        } catch (Exception e) {
            logger.severe("§c[LichKingBoss] Failed to initialize: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initialize Lich King specific abilities
     */
    private void initializeLichKingAbilities() {
        // Phase 1 Abilities
        createLichMeleeStrike();
        createBoneBolt();
        createSummonSkeletons();
        createFrostAura();

        // Phase 2 Abilities
        createBoneSpikes();
        createNecromancticHeal();
        createShadowTeleport();
        createCurseOfWeakness();

        // Phase 3 Abilities
        createDeathNova();
        createRaiseUndead();
        createSoulDrain();
        createApocalypse();
    }

    // ================ PHASE 1 ABILITIES ================

    private void createLichMeleeStrike() {
        BossAbility ability = new BossAbility("lich_melee_strike", "§cNecrotic Strike",
                BossAbility.AbilityType.MELEE_ATTACK, 3000L, 4.0, 35);
        ability.setParameter("lifesteal", 0.5); // 50% lifesteal
        ability.setParameter("necrotic_damage", 10); // Additional magic damage
        abilities.put("lich_melee_strike", ability);
    }

    private void createBoneBolt() {
        BossAbility ability = new BossAbility("bone_bolt", "§fBone Bolt",
                BossAbility.AbilityType.RANGED_ATTACK, 4000L, 20.0, 25);
        ability.setParameter("projectile_speed", 2.0);
        ability.setParameter("piercing", false);
        abilities.put("bone_bolt", ability);
    }

    private void createSummonSkeletons() {
        BossAbility ability = new BossAbility("summon_skeletons", "§7Raise Minions",
                BossAbility.AbilityType.SUMMON, 25000L, 0.0, 0);
        ability.setParameter("minion_count", 3);
        ability.setParameter("minion_type", "skeleton");
        ability.setParameter("minion_tier", BOSS_TIER - 2);
        abilities.put("summon_skeletons", ability);
    }

    private void createFrostAura() {
        BossAbility ability = new BossAbility("frost_aura", "§bFrost Aura",
                BossAbility.AbilityType.ENVIRONMENTAL, 30000L, 12.0, 0);
        ability.setParameter("slow_amplifier", 2);
        ability.setParameter("aura_duration", 15000L);
        ability.setParameter("damage_per_tick", 2);
        abilities.put("frost_aura", ability);
    }

    // ================ PHASE 2 ABILITIES ================

    private void createBoneSpikes() {
        BossAbility ability = new BossAbility("bone_spikes", "§eErupting Bones",
                BossAbility.AbilityType.AOE_ATTACK, 12000L, 15.0, 30);
        ability.setParameter("spike_count", 8);
        ability.setParameter("warning_time", 2000L);
        ability.setParameter("spike_duration", 10000L);
        abilities.put("bone_spikes", ability);
    }

    private void createNecromancticHeal() {
        BossAbility ability = new BossAbility("necromantic_heal", "§2Dark Regeneration",
                BossAbility.AbilityType.HEAL, 20000L, 0.0, 0);
        ability.setParameter("heal_percentage", 0.15); // 15% max health
        ability.setParameter("channel_time", 3000L);
        ability.addRequirement("health_below_50");
        abilities.put("necromantic_heal", ability);
    }

    private void createShadowTeleport() {
        BossAbility ability = new BossAbility("shadow_teleport", "§8Shadow Step",
                BossAbility.AbilityType.TELEPORT, 8000L, 20.0, 0);
        ability.setParameter("teleport_to_player", true);
        ability.setParameter("confusion_duration", 3000L);
        abilities.put("shadow_teleport", ability);
    }

    private void createCurseOfWeakness() {
        BossAbility ability = new BossAbility("curse_of_weakness", "§4Curse of Weakness",
                BossAbility.AbilityType.DEBUFF, 15000L, 25.0, 0);
        ability.setParameter("weakness_duration", 20000L);
        ability.setParameter("damage_reduction", 0.4); // 40% damage reduction
        abilities.put("curse_of_weakness", ability);
    }

    // ================ PHASE 3 ABILITIES ================

    private void createDeathNova() {
        BossAbility ability = new BossAbility("death_nova", "§c§lDEATH NOVA",
                BossAbility.AbilityType.AOE_ATTACK, 18000L, 20.0, 60);
        ability.setParameter("warning_time", 4000L);
        ability.setParameter("explosion_radius", 15.0);
        ability.setParameter("knockback", 4.0);
        abilities.put("death_nova", ability);
    }

    private void createRaiseUndead() {
        BossAbility ability = new BossAbility("raise_undead", "§5Undead Legion",
                BossAbility.AbilityType.SUMMON, 30000L, 0.0, 0);
        ability.setParameter("minion_count", 6);
        ability.setParameter("minion_type", "zombie");
        ability.setParameter("minion_tier", BOSS_TIER - 1);
        ability.setParameter("explosive_minions", true);
        abilities.put("raise_undead", ability);
    }

    private void createSoulDrain() {
        BossAbility ability = new BossAbility("soul_drain", "§d§lSoul Drain",
                BossAbility.AbilityType.SPECIAL, 25000L, 30.0, 40);
        ability.setParameter("drain_duration", 6000L);
        ability.setParameter("heal_per_tick", 5);
        ability.setParameter("mana_drain", true);
        abilities.put("soul_drain", ability);
    }

    private void createApocalypse() {
        BossAbility ability = new BossAbility("apocalypse", "§4§l§nAPOCALYPSE",
                BossAbility.AbilityType.SPECIAL, 60000L, 0.0, 100);
        ability.setParameter("channel_time", 8000L);
        ability.setParameter("area_damage", 75);
        ability.setParameter("environmental_destruction", true);
        ability.addRequirement("health_below_15");
        abilities.put("apocalypse", ability);
    }

    // ================ ABILITY EXECUTION OVERRIDES ================

    @Override
    protected void executeAbility(BossAbility ability) {
        try {
            String abilityId = ability.getId();

            switch (abilityId) {
                // Phase 1
                case "lich_melee_strike":
                    executeLichMeleeStrike(ability);
                    break;
                case "bone_bolt":
                    executeBoneBolt(ability);
                    break;
                case "summon_skeletons":
                    executeSummonSkeletons(ability);
                    break;
                case "frost_aura":
                    executeFrostAura(ability);
                    break;

                // Phase 2
                case "bone_spikes":
                    executeBoneSpikes(ability);
                    break;
                case "necromantic_heal":
                    executeNecromancticHeal(ability);
                    break;
                case "shadow_teleport":
                    executeShadowTeleport(ability);
                    break;
                case "curse_of_weakness":
                    executeCurseOfWeakness(ability);
                    break;

                // Phase 3
                case "death_nova":
                    executeDeathNova(ability);
                    break;
                case "raise_undead":
                    executeRaiseUndead(ability);
                    break;
                case "soul_drain":
                    executeSoulDrain(ability);
                    break;
                case "apocalypse":
                    executeApocalypse(ability);
                    break;

                default:
                    super.executeAbility(ability); // Fallback to parent implementation
                    break;
            }

            // Mark ability as used
            ability.use();

            if (YakRealms.getInstance().isDebugMode()) {
                logger.info("§a[LichKingBoss] §7Executed ability: " + ability.getName());
            }

        } catch (Exception e) {
            logger.warning("§c[LichKingBoss] Error executing ability " + ability.getId() + ": " + e.getMessage());
        }
    }

    // ================ PHASE 1 ABILITY IMPLEMENTATIONS ================

    private void executeLichMeleeStrike(BossAbility ability) {
        Player target = getCurrentTarget();
        if (target == null || !isTargetInRange(target, ability.getRange())) return;

        // Calculate damage
        double damage = calculateAbilityDamage(ability);
        double necroticDamage = ability.getParameter("necrotic_damage", Double.class, 10.0);
        double lifesteal = ability.getParameter("lifesteal", Double.class, 0.5);

        // Apply damage
        target.damage(damage, bossEntity);

        // Apply necrotic damage (bypasses armor)
        target.setHealth(Math.max(1, target.getHealth() - necroticDamage));

        // Lifesteal
        double healAmount = damage * lifesteal;
        bossEntity.setHealth(Math.min(bossEntity.getMaxHealth(), bossEntity.getHealth() + healAmount));

        // Visual effects
        target.getWorld().spawnParticle(Particle.SPELL_WITCH, target.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 0.8f);

        broadcastToArena(BOSS_NAME + " §cstrike drains the life from " + target.getName() + "!");
    }

    private void executeBoneBolt(BossAbility ability) {
        Player target = getCurrentTarget();
        if (target == null) return;

        // Visual projectile effect
        Location startLoc = bossEntity.getLocation().add(0, 1.5, 0);
        Location targetLoc = target.getLocation().add(0, 1, 0);

        // Create projectile trail
        Vector direction = targetLoc.toVector().subtract(startLoc.toVector()).normalize();

        new BukkitRunnable() {
            Location current = startLoc.clone();
            int ticks = 0;
            final int maxTicks = 40; // 2 second flight time

            @Override
            public void run() {
                if (ticks++ >= maxTicks) {
                    cancel();
                    return;
                }

                // Move projectile
                current.add(direction.clone().multiply(0.5));

                // Visual trail
                current.getWorld().spawnParticle(Particle.BONE_MEAL, current, 3, 0.1, 0.1, 0.1, 0.01);

                // Check for hit
                for (Player player : getNearbyPlayers(current, 1.5)) {
                    if (player.equals(target)) {
                        // Hit!
                        double damage = calculateAbilityDamage(ability);
                        player.damage(damage, bossEntity);

                        // Impact effects
                        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0),
                                15, 0.5, 0.5, 0.5, 0.1);
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT, 1.0f, 0.6f);

                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);

        broadcastToArena(BOSS_NAME + " §fhurls a bone bolt at " + target.getName() + "!");
    }

    private void executeSummonSkeletons(BossAbility ability) {
        Integer minionCount = ability.getParameter("minion_count", Integer.class, 3);
        String minionType = ability.getParameter("minion_type", String.class, "skeleton");
        Integer minionTier = ability.getParameter("minion_tier", Integer.class, BOSS_TIER - 2);

        broadcastToArena(BOSS_NAME + " §7raises skeletal minions from the ground!");

        for (int i = 0; i < minionCount; i++) {
            Location spawnLoc = getRandomLocationInArena();

            // Spawn effect first
            spawnLoc.getWorld().spawnParticle(Particle.SOUL, spawnLoc.add(0, 1, 0), 30, 1, 1, 1, 0.1);
            spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_SKELETON_AMBIENT, 1.0f, 0.7f);

            // Delay spawn slightly for effect
            new BukkitRunnable() {
                @Override
                public void run() {
                    LivingEntity minion = MobManager.getInstance().spawnMobFromSpawner(
                            spawnLoc, minionType, minionTier, false);

                    if (minion != null) {
                        summonedMinions.add(minion.getUniqueId());
                        minion.setMetadata("lich_minion", new org.bukkit.metadata.MetadataValue(YakRealms.getInstance(), true));

                        // Give minions a speed boost
                        minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), 20L + (i * 10L)); // Staggered spawning
        }
    }

    private void executeFrostAura(BossAbility ability) {
        if (phase1FrostAuraActive) return; // Don't stack

        phase1FrostAuraActive = true;
        Long auraDuration = ability.getParameter("aura_duration", Long.class, 15000L);
        Integer slowAmplifier = ability.getParameter("slow_amplifier", Integer.class, 2);
        Integer damagePerTick = ability.getParameter("damage_per_tick", Integer.class, 2);

        broadcastToArena(BOSS_NAME + " §bsurrounds himself with freezing aura!");

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(auraDuration / 1000); // Convert to seconds

            @Override
            public void run() {
                if (ticks++ >= maxTicks || !isActive()) {
                    phase1FrostAuraActive = false;
                    cancel();
                    return;
                }

                // Apply effects to nearby players
                for (Player player : getNearbyPlayers(ability.getRange())) {
                    // Slow effect
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, slowAmplifier, false, true));

                    // Periodic damage
                    if (ticks % 3 == 0) { // Every 3 seconds
                        player.damage(damagePerTick, bossEntity);
                    }
                }

                // Visual effects
                bossEntity.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        bossEntity.getLocation().add(0, 1, 0), 20,
                        ability.getRange(), 2, ability.getRange(), 0.1);
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 20L);
    }

    // ================ PHASE 2 ABILITY IMPLEMENTATIONS ================

    private void executeBoneSpikes(BossAbility ability) {
        Integer spikeCount = ability.getParameter("spike_count", Integer.class, 8);
        Long warningTime = ability.getParameter("warning_time", Long.class, 2000L);
        Long spikeDuration = ability.getParameter("spike_duration", Long.class, 10000L);

        broadcastToArena(BOSS_NAME + " §ebegins channeling bone spikes from the ground!");

        // Get spike locations
        List<Location> spikeLocations = new ArrayList<>();
        for (int i = 0; i < spikeCount; i++) {
            Location spikeLoc = getRandomLocationInArena();
            spikeLocations.add(spikeLoc);

            // Warning effects
            spikeLoc.getWorld().spawnParticle(Particle.CRIT, spikeLoc.add(0, 0.1, 0),
                    5, 0.3, 0.1, 0.3, 0.1);
        }

        // Warning phase
        new BukkitRunnable() {
            @Override
            public void run() {
                // Create spikes
                for (Location spikeLoc : spikeLocations) {
                    createBoneSpike(spikeLoc, ability, spikeDuration);
                }
                broadcastToArena("§eSharp bone spikes erupt from the ground!");
            }
        }.runTaskLater(YakRealms.getInstance(), warningTime / 50);
    }

    private void createBoneSpike(Location location, BossAbility ability, long duration) {
        boneSpikeLocations.add(location);

        // Visual spike effect
        location.getWorld().spawnParticle(Particle.BLOCK_CRACK, location.add(0, 1, 0),
                50, 1, 2, 1, 0.1, Material.BONE_BLOCK.createBlockData());
        location.getWorld().playSound(location, Sound.BLOCK_BONE_BLOCK_BREAK, 1.0f, 0.5f);

        // Damage players in range
        for (Player player : getNearbyPlayers(location, 2.0)) {
            double damage = calculateAbilityDamage(ability);
            player.damage(damage, bossEntity);
            player.sendMessage("§c§lYou are impaled by bone spikes!");
        }

        // Remove spike after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                boneSpikeLocations.remove(location);
            }
        }.runTaskLater(YakRealms.getInstance(), duration / 50);
    }

    private void executeNecromancticHeal(BossAbility ability) {
        Double healPercentage = ability.getParameter("heal_percentage", Double.class, 0.15);
        Long channelTime = ability.getParameter("channel_time", Long.class, 3000L);

        broadcastToArena(BOSS_NAME + " §2begins channeling dark regeneration!");

        // Make boss stationary and vulnerable during channel
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int)(channelTime / 50), 10));

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(channelTime / 50);

            @Override
            public void run() {
                if (ticks++ >= maxTicks || !isActive()) {
                    // Execute heal
                    double healAmount = bossEntity.getMaxHealth() * healPercentage;
                    bossEntity.setHealth(Math.min(bossEntity.getMaxHealth(), bossEntity.getHealth() + healAmount));

                    // Heal effects
                    bossEntity.getWorld().spawnParticle(Particle.HEART,
                            bossEntity.getLocation().add(0, 2, 0), 30, 2, 1, 2, 0.1);
                    bossEntity.getWorld().playSound(bossEntity.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

                    broadcastToArena(BOSS_NAME + " §2is healed by dark magic!");
                    cancel();
                    return;
                }

                // Channel effects
                bossEntity.getWorld().spawnParticle(Particle.SPELL_WITCH,
                        bossEntity.getLocation().add(0, 1, 0), 10, 1, 1, 1, 0.1);
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }

    private void executeShadowTeleport(BossAbility ability) {
        if (!phase2TeleportUnlocked) {
            phase2TeleportUnlocked = true;
            broadcastToArena(BOSS_NAME + " §8master the shadows and gains new mobility!");
        }

        Player target = getCurrentTarget();
        if (target == null) return;

        Boolean teleportToPlayer = ability.getParameter("teleport_to_player", Boolean.class, true);
        Long confusionDuration = ability.getParameter("confusion_duration", Long.class, 3000L);

        // Teleport effects at current location
        Location oldLoc = bossEntity.getLocation();
        oldLoc.getWorld().spawnParticle(Particle.PORTAL, oldLoc.add(0, 1, 0), 50, 1, 1, 1, 0.2);
        oldLoc.getWorld().playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);

        // Calculate teleport location
        Location teleportLoc;
        if (teleportToPlayer) {
            Vector behind = target.getLocation().getDirection().multiply(-3);
            teleportLoc = target.getLocation().add(behind);
        } else {
            teleportLoc = getRandomLocationInArena();
        }

        // Teleport
        bossEntity.teleport(teleportLoc);

        // Teleport effects at new location
        teleportLoc.getWorld().spawnParticle(Particle.PORTAL, teleportLoc.add(0, 1, 0), 50, 1, 1, 1, 0.2);
        teleportLoc.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f);

        // Apply confusion to target
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (int)(confusionDuration / 50), 1));

        broadcastToArena(BOSS_NAME + " §8emerges from the shadows behind " + target.getName() + "!");
    }

    private void executeCurseOfWeakness(BossAbility ability) {
        Long weaknessDuration = ability.getParameter("weakness_duration", Long.class, 20000L);
        Double damageReduction = ability.getParameter("damage_reduction", Double.class, 0.4);

        broadcastToArena(BOSS_NAME + " §4curses all nearby adventurers with weakness!");

        for (Player player : getNearbyPlayers(ability.getRange())) {
            // Apply weakness effect
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int)(weaknessDuration / 50), 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, (int)(weaknessDuration / 50), 1));

            // Store curse timer for damage reduction
            playerCurseTimers.put(player.getUniqueId(), System.currentTimeMillis() + weaknessDuration);

            // Visual effects
            player.getWorld().spawnParticle(Particle.SPELL_MOB, player.getLocation().add(0, 1, 0),
                    20, 0.5, 1, 0.5, 0.1);

            player.sendMessage("§4§lYou are cursed with weakness!");
        }
    }

    // ================ PHASE 3 ABILITY IMPLEMENTATIONS ================

    private void executeDeathNova(BossAbility ability) {
        Long warningTime = ability.getParameter("warning_time", Long.class, 4000L);
        Double explosionRadius = ability.getParameter("explosion_radius", Double.class, 15.0);
        Double knockback = ability.getParameter("knockback", Double.class, 4.0);

        broadcastToArena("§c§l" + BOSS_NAME + " BEGINS CHANNELING DEATH NOVA!");
        broadcastToArena("§c§lGET AWAY FROM THE LICH KING!");

        // Warning effects
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(warningTime / 250); // Every 0.25 seconds

            @Override
            public void run() {
                if (ticks++ >= maxTicks || !isActive()) {
                    // EXPLODE!
                    executeDeathNovaExplosion(ability, explosionRadius, knockback);
                    cancel();
                    return;
                }

                // Intensifying warning effects
                int particleCount = Math.min(100, ticks * 5);
                bossEntity.getWorld().spawnParticle(Particle.FLAME,
                        bossEntity.getLocation().add(0, 1, 0), particleCount,
                        explosionRadius * 0.3, 2, explosionRadius * 0.3, 0.1);

                // Warning sound
                if (ticks % 8 == 0) { // Every 2 seconds
                    bossEntity.getWorld().playSound(bossEntity.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 2.0f, 0.5f);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 5L);
    }

    private void executeDeathNovaExplosion(BossAbility ability, double radius, double knockback) {
        Location center = bossEntity.getLocation();

        // Massive explosion effects
        center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center.add(0, 1, 0), 20, 3, 3, 3, 0.2);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.5f);
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);

        // Damage and knockback all players in range
        for (Player player : getNearbyPlayers(radius)) {
            double distance = player.getLocation().distance(center);
            double damageMultiplier = 1.0 - (distance / radius); // Less damage farther away

            double damage = calculateAbilityDamage(ability) * damageMultiplier;
            player.damage(damage, bossEntity);

            // Knockback
            Vector direction = player.getLocation().subtract(center).toVector().normalize();
            direction.setY(Math.max(0.5, direction.getY())); // Ensure upward component
            player.setVelocity(direction.multiply(knockback * damageMultiplier));

            player.sendMessage("§c§l§nYou are caught in the DEATH NOVA!");
        }

        broadcastToArena("§c§l" + BOSS_NAME + " UNLEASHES DEVASTATING DEATH NOVA!");
    }

    private void executeRaiseUndead(BossAbility ability) {
        Integer minionCount = ability.getParameter("minion_count", Integer.class, 6);
        String minionType = ability.getParameter("minion_type", String.class, "zombie");
        Integer minionTier = ability.getParameter("minion_tier", Integer.class, BOSS_TIER - 1);
        Boolean explosiveMinions = ability.getParameter("explosive_minions", Boolean.class, true);

        broadcastToArena(BOSS_NAME + " §5raises an undead legion!");

        for (int i = 0; i < minionCount; i++) {
            Location spawnLoc = getRandomLocationInArena();

            // Dramatic spawn effects
            spawnLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, spawnLoc.add(0, 1, 0),
                    50, 2, 2, 2, 0.1);
            spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);

            new BukkitRunnable() {
                @Override
                public void run() {
                    LivingEntity minion = MobManager.getInstance().spawnMobFromSpawner(
                            spawnLoc, minionType, minionTier, false);

                    if (minion != null) {
                        summonedMinions.add(minion.getUniqueId());
                        minion.setMetadata("lich_minion", new org.bukkit.metadata.MetadataValue(YakRealms.getInstance(), true));

                        if (explosiveMinions) {
                            minion.setMetadata("explosive_minion", new org.bukkit.metadata.MetadataValue(YakRealms.getInstance(), true));
                            // Make minions faster and more aggressive
                            minion.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                            minion.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));
                        }
                    }
                }
            }.runTaskLater(YakRealms.getInstance(), 30L + (i * 5L));
        }
    }

    private void executeSoulDrain(BossAbility ability) {
        Long drainDuration = ability.getParameter("drain_duration", Long.class, 6000L);
        Integer healPerTick = ability.getParameter("heal_per_tick", Integer.class, 5);

        broadcastToArena(BOSS_NAME + " §d§lbegins draining the souls of the living!");

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(drainDuration / 250); // Every 0.25 seconds

            @Override
            public void run() {
                if (ticks++ >= maxTicks || !isActive()) {
                    broadcastToArena("§dThe soul drain ends...");
                    cancel();
                    return;
                }

                // Drain from all players in range
                for (Player player : getNearbyPlayers(ability.getRange())) {
                    // Damage player
                    player.damage(3, bossEntity);

                    // Heal boss
                    bossEntity.setHealth(Math.min(bossEntity.getMaxHealth(), bossEntity.getHealth() + healPerTick));

                    // Visual connection
                    drawSoulConnectionEffect(player.getLocation(), bossEntity.getLocation());

                    if (ticks % 8 == 0) { // Every 2 seconds
                        player.sendMessage("§d§lYour soul is being drained!");
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 5L);
    }

    private void executeApocalypse(BossAbility ability) {
        if (!phase3BerserkActive) {
            phase3BerserkActive = true;
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2));
            bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
        }

        Long channelTime = ability.getParameter("channel_time", Long.class, 8000L);
        Integer areaDamage = ability.getParameter("area_damage", Integer.class, 75);

        broadcastToArena("§4§l§n" + BOSS_NAME + " BEGINS THE APOCALYPSE!");
        broadcastToArena("§4§l§nTHE END OF ALL THINGS IS UPON YOU!");

        // Lock boss in place during channel
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, (int)(channelTime / 50), 10));

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = (int)(channelTime / 50);

            @Override
            public void run() {
                if (ticks++ >= maxTicks || !isActive()) {
                    // APOCALYPSE!
                    executeApocalypseExplosion(areaDamage);
                    cancel();
                    return;
                }

                // Intensifying effects
                int intensity = (int)((double)ticks / maxTicks * 10) + 1;

                // Particle effects
                bossEntity.getWorld().spawnParticle(Particle.FLAME,
                        bossEntity.getLocation().add(0, 2, 0), intensity * 10,
                        ARENA_RADIUS * 0.5, 5, ARENA_RADIUS * 0.5, 0.2);

                bossEntity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        bossEntity.getLocation().add(0, 3, 0), intensity * 5,
                        ARENA_RADIUS * 0.3, 3, ARENA_RADIUS * 0.3, 0.1);

                // Warning sounds
                if (ticks % 20 == 0) {
                    bossEntity.getWorld().playSound(bossEntity.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.5f);
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }

    private void executeApocalypseExplosion(int areaDamage) {
        Location center = bossEntity.getLocation();

        // MASSIVE EXPLOSION
        center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center, 50, 10, 10, 10, 0.5);
        center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.3f);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.3f);

        // Damage EVERYONE in the arena
        for (Player player : getAllPlayersInArena()) {
            player.damage(areaDamage, bossEntity);

            // Massive knockback
            Vector direction = player.getLocation().subtract(center).toVector().normalize();
            direction.setY(Math.max(1.0, direction.getY()));
            player.setVelocity(direction.multiply(6.0));

            player.sendMessage("§4§l§n§oTHE APOCALYPSE CONSUMES ALL!");
        }

        // Environmental destruction
        createEnvironmentalDestruction();

        broadcastToArena("§4§l§n" + BOSS_NAME + " BRINGS ABOUT THE APOCALYPSE!");
    }

    // ================ PHASE MANAGEMENT OVERRIDES ================

    @Override
    protected void startPhase(int phaseIndex) {
        super.startPhase(phaseIndex);

        switch (phaseIndex) {
            case 0: // Phase 1
                startPhase1();
                break;
            case 1: // Phase 2
                startPhase2();
                break;
            case 2: // Phase 3
                startPhase3();
                break;
        }
    }

    private void startPhase1() {
        broadcastToArena("§5§l" + BOSS_NAME + " awakens from his eternal slumber!");
        broadcastToArena("§7The temperature drops as death magic fills the air...");

        // Phase 1 setup
        setupPhase1Environment();
    }

    private void startPhase2() {
        broadcastToArena("§c§l" + BOSS_NAME + " enters NECROMANTIC FURY!");
        broadcastToArena("§e§lThe Lich King becomes more aggressive and dangerous!");

        //  abilities
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 0));

        setupPhase2Environment();
    }

    private void startPhase3() {
        phase3DesperationStacks = 0;

        broadcastToArena("§4§l§n" + BOSS_NAME + " EMBRACES DEATH!");
        broadcastToArena("§4§l§nTHE LICH KING FIGHTS WITH DESPERATE FURY!");

        // Desperate measures
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));
        bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0));

        setupPhase3Environment();

        // Start desperation stacking
        startDesperationMechanic();
    }

    // ================ HELPER METHODS ================

    private void setupLichKingArena() {
        // Create arena barriers
        if (!arenaBarriersActive) {
            createArenaBarriers();
            arenaBarriersActive = true;
        }
    }

    private void initializeRitualCircles() {
        // Create ritual circles around the arena for visual effects
        Location center = getArenaCenter();
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI * 2 / 4) * i;
            double x = center.getX() + Math.cos(angle) * (ARENA_RADIUS * 0.7);
            double z = center.getZ() + Math.sin(angle) * (ARENA_RADIUS * 0.7);
            ritualCircles.add(new Location(center.getWorld(), x, center.getY(), z));
        }
    }

    private void setupPhase1Environment() {
        // Spawn some initial skeletons
        for (int i = 0; i < 2; i++) {
            executeSummonSkeletons(abilities.get("summon_skeletons"));
        }
    }

    private void setupPhase2Environment() {
        // Clear minions from phase 1
        clearAllMinions();

        // Add some bone spikes around the arena
        for (int i = 0; i < 4; i++) {
            Location spikeLoc = getRandomLocationInArena();
            createBoneSpike(spikeLoc, abilities.get("bone_spikes"), 30000L); // Long duration
        }
    }

    private void setupPhase3Environment() {
        // Clear everything
        clearAllMinions();
        boneSpikeLocations.clear();

        // Make ritual circles active
        activateRitualCircles();
    }

    private void startDesperationMechanic() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive() || getCurrentHealthPercent() <= 0) {
                    cancel();
                    return;
                }

                phase3DesperationStacks++;

                // Each stack increases damage and speed
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1200, phase3DesperationStacks));
                bossEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, Math.min(phase3DesperationStacks, 3)));

                broadcastToArena("§c§l" + BOSS_NAME + " grows more desperate! (Stack " + phase3DesperationStacks + ")");

                // At high stacks, trigger apocalypse automatically
                if (phase3DesperationStacks >= 5) {
                    BossAbility apocalypse = abilities.get("apocalypse");
                    if (apocalypse != null && apocalypse.canUse(LichKingBoss.this)) {
                        executeApocalypse(apocalypse);
                    }
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 600L, 600L); // Every 30 seconds
    }

    private void createArenaBarriers() {
        // This would create physical barriers around the arena
        // Implementation depends on your world building system
        Location center = getArenaCenter();
        broadcastToArena("§8Dark barriers rise around the arena!");
    }

    private void activateRitualCircles() {
        for (Location circle : ritualCircles) {
            circle.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, circle.add(0, 1, 0),
                    50, 2, 2, 2, 0.1);
            circle.getWorld().playSound(circle, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.7f);
        }
        broadcastToArena("§5The ritual circles activate with dark energy!");
    }

    private void createEnvironmentalDestruction() {
        // Create destruction effects around the arena
        for (int i = 0; i < 20; i++) {
            Location destructionLoc = getRandomLocationInArena();
            destructionLoc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE,
                    destructionLoc.add(0, 1, 0), 5, 2, 2, 2, 0.1);
        }
    }

    private void drawSoulConnectionEffect(Location from, Location to) {
        // Create a visual connection between two points
        Vector direction = to.toVector().subtract(from.toVector()).normalize();
        Location current = from.clone();

        for (int i = 0; i < 10; i++) {
            current.add(direction.clone().multiply(0.5));
            current.getWorld().spawnParticle(Particle.SOUL, current, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void clearAllMinions() {
        for (UUID minionId : summonedMinions) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(minionId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        summonedMinions.clear();
    }

    private Location getRandomLocationInArena() {
        Location center = getArenaCenter();
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        double distance = ThreadLocalRandom.current().nextDouble() * (ARENA_RADIUS * 0.8);

        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;

        return new Location(center.getWorld(), x, center.getY(), z);
    }

    private List<Player> getNearbyPlayers(Location center, double radius) {
        return center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .collect(Collectors.toList());
    }

    private List<Player> getAllPlayersInArena() {
        return getNearbyPlayers(getArenaCenter(), ARENA_RADIUS);
    }

    private void broadcastToArena(String message) {
        for (Player player : getAllPlayersInArena()) {
            player.sendMessage(message);
        }
    }

    // ================ OVERRIDES ================

    @Override
    public void defeat() {
        // Clear all effects before defeat
        clearAllMinions();
        boneSpikeLocations.clear();
        phase1FrostAuraActive = false;
        playerCurseTimers.clear();

        broadcastToArena("§a§l" + BOSS_NAME + " HAS BEEN DEFEATED!");
        broadcastToArena("§a§lThe dark magic dissipates and peace returns...");

        super.defeat();
    }

    @Override
    public void cleanup() {
        clearAllMinions();
        boneSpikeLocations.clear();
        playerCurseTimers.clear();
        ritualCircles.clear();
        super.cleanup();
    }
}
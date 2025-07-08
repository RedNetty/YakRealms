package com.rednetty.server.mechanics.world.mobs.core;

import com.rednetty.server.YakRealms;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLEANED: WorldBoss class - specialized EliteMob with phases and abilities
 * - Integrates with the cleaned OOP structure
 * - Uses tick() lifecycle method
 * - Simplified name management
 * - Preserves all unique boss mechanics
 * - Works with centralized CritManager
 */
public class WorldBoss extends EliteMob {

    // ================ BOSS STATE ================
    private int currentPhase = 1;
    private boolean berserkMode = false;
    private final Map<String, Long> abilityCooldowns = new HashMap<>();
    private long lastPhaseChangeTime = 0;
    private Location spawnLocation;

    // ================ BOSS-SPECIFIC NAME/TITLE ================
    private String bossTitle;
    private boolean hasCustomTitle = false;
    private long lastAbilityUse = 0;

    // ================ BOSS CONSTANTS ================
    private static final long PHASE_CHANGE_COOLDOWN = 5000; // 5 seconds between phase changes
    private static final double PHASE_2_THRESHOLD = 0.75;
    private static final double PHASE_3_THRESHOLD = 0.50;
    private static final double PHASE_4_THRESHOLD = 0.25;
    private static final long ABILITY_GLOBAL_COOLDOWN = 3000; // 3 seconds between any abilities

    public WorldBoss(MobType type, int tier) {
        super(type, tier);
        if (type.isWorldBoss()) {
            this.bossTitle = type.getDefaultName();
            this.hasCustomTitle = true;
        }
    }

    @Override
    public boolean spawn(Location location) {
        boolean result = super.spawn(location);
        if (result && entity != null) {
            this.spawnLocation = location.clone();
            entity.setMetadata("worldboss", new FixedMetadataValue(YakRealms.getInstance(), true));
            announceSpawn();
        }
        return result;
    }

    /**
     * Generate the boss's unique, formatted name
     */
    private String generateBossName() {
        if (hasCustomTitle && bossTitle != null && !bossTitle.isEmpty()) {
            return ChatColor.DARK_RED + "" + ChatColor.BOLD + bossTitle;
        }
        // Fallback to a standard boss-formatted name if no special title is set
        String tierName = type.getTierSpecificName(tier);
        return ChatColor.DARK_RED + "" + ChatColor.BOLD + tierName;
    }

    /**
     * Enhanced tick method with boss-specific logic
     */
    @Override
    public void tick() {
        super.tick(); // IMPORTANT: Runs the tick logic from CustomMob and EliteMob first

        if (!isValid()) return;

        try {
            // Boss-specific logic
            processPhaseTransitions();
            considerUsingAbilities();

            // Apply boss visual effects
            if (System.currentTimeMillis() % 1000 < 50) { // Every second (with 50ms tolerance)
                applyBossVisualEffects();
            }
        } catch (Exception e) {
            LOGGER.warning("[WorldBoss] Tick error: " + e.getMessage());
        }
    }

    protected void announceSpawn() {
        if (!isValid()) return;
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        entity.getWorld().strikeLightningEffect(entity.getLocation());
        broadcastMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD +
                getDisplayBossName() + ": " + ChatColor.YELLOW +
                "Prepare for your doom, mortals!", 50);
    }

    // ================ PHASE MANAGEMENT ================

    public int getCurrentPhase() {
        return currentPhase;
    }

    public boolean setPhase(int phase) {
        if (phase >= 1 && phase <= 4 && phase != currentPhase && canChangePhase()) {
            int oldPhase = currentPhase;
            currentPhase = phase;
            onPhaseChanged(oldPhase, currentPhase);
            return true;
        }
        return false;
    }

    public void processPhaseTransitions() {
        if (!canChangePhase()) return;

        double healthPercentage = getHealthPercentage();
        if (healthPercentage <= PHASE_4_THRESHOLD && currentPhase < 4) {
            setPhase(4);
        } else if (healthPercentage <= PHASE_3_THRESHOLD && currentPhase < 3) {
            setPhase(3);
        } else if (healthPercentage <= PHASE_2_THRESHOLD && currentPhase < 2) {
            setPhase(2);
        }
    }

    protected void onPhaseChanged(int oldPhase, int newPhase) {
        if (!isValid()) return;
        lastPhaseChangeTime = System.currentTimeMillis();
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.7f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE, entity.getLocation(), 5, 1, 1, 1, 0.1);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0)); // Glow for 5 seconds
        broadcastMessage(ChatColor.YELLOW + "The " + getDisplayBossName() + " enters phase " + newPhase + "!", 50);
        applyPhaseEffects(newPhase);

        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info(String.format("§6[WorldBoss] §7%s phase change: %d → %d", getDisplayBossName(), oldPhase, newPhase));
        }
    }

    protected void applyPhaseEffects(int phase) {
        if (!isValid()) return;
        // Apply effects based on phase
        switch (phase) {
            case 2 -> entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, true));
            case 3 -> {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, true));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1, true));
            }
            case 4 -> {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 3, true));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2, true));
                if (!berserkMode) {
                    activateBerserkMode();
                }
            }
        }
    }

    public double getHealthPercentage() {
        if (!isValid()) return 0;
        return entity.getHealth() / entity.getMaxHealth();
    }

    // ================ BERSERK MODE ================

    public boolean isBerserk() {
        return berserkMode;
    }

    public void activateBerserkMode() {
        if (berserkMode || !isValid()) return;
        berserkMode = true;
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2, true));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        broadcastMessage(ChatColor.RED + "The " + getDisplayBossName() + " has entered a berserk state!", 50);
    }

    // ================ ABILITY SYSTEM ================

    private void considerUsingAbilities() {
        if (isOnGlobalCooldown()) return;
        double abilityChance = 0.05 + (currentPhase * 0.02); // 5% base + 2% per phase, checked every tick
        if (RANDOM.nextDouble() < abilityChance) {
            List<String> possible = getPossibleAbilities();
            if (!possible.isEmpty()) {
                String abilityToUse = possible.get(RANDOM.nextInt(possible.size()));
                executeAbility(abilityToUse);
            }
        }
    }

    public boolean executeAbility(String abilityId) {
        if (!isValid() || isOnGlobalCooldown() || isAbilityOnCooldown(abilityId)) return false;

        boolean executed = switch (abilityId.toLowerCase()) {
            case "roar" -> executeRoarAbility();
            case "stomp" -> executeStompAbility();
            case "summon" -> executeSummonAbility();
            case "freeze" -> executeFreezeAbility();
            case "darkness" -> executeDarknessAbility();
            default -> false; // For subclass extension
        };

        if (executed) {
            triggerGlobalCooldown();
            setAbilityCooldown(abilityId, getAbilityCooldown(abilityId));
        }
        return executed;
    }

    protected boolean executeRoarAbility() {
        broadcastMessage(ChatColor.RED + getDisplayBossName() + " roars with rage!", 50);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
        getNearbyPlayers(15.0).forEach(p -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 1));
        });
        return true;
    }

    protected boolean executeStompAbility() {
        broadcastMessage(ChatColor.RED + "The ground shakes as " + getDisplayBossName() + " stomps!", 50);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, entity.getLocation(), 10, 3, 0.5, 3, 0.1);
        getNearbyPlayers(8.0).forEach(p -> {
            double distance = p.getLocation().distance(entity.getLocation());
            double damage = Math.max(5, 20 - (distance * 2));
            p.damage(damage, entity);
            Vector knockback = p.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(2).setY(0.5);
            p.setVelocity(knockback);
        });
        return true;
    }

    protected boolean executeSummonAbility() {
        broadcastMessage(ChatColor.GOLD + getDisplayBossName() + " summons reinforcements!", 50);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);
        int minionCount = 2 + (currentPhase - 1);
        for (int i = 0; i < minionCount; i++) {
            // Placeholder for minion spawning logic
            if (YakRealms.getInstance().isDebugMode()) LOGGER.info("Spawning minion " + (i + 1));
        }
        return true;
    }

    protected boolean executeFreezeAbility() {
        if (!isFrozenType()) return false;

        broadcastMessage(ChatColor.BLUE + getDisplayBossName() + " unleashes a freezing blast!", 50);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 0.3f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, entity.getLocation(), 50, 5, 2, 5, 0.1);

        getNearbyPlayers(12.0).forEach(p -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 3));
            p.damage(15 + (currentPhase * 5), entity);
        });
        return true;
    }

    protected boolean executeDarknessAbility() {
        if (!isWardenType()) return false;

        broadcastMessage(ChatColor.GOLD + getDisplayBossName() + " shrouds the area in darkness!", 50);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, entity.getLocation(), 80, 8, 3, 8, 0.2);

        getNearbyPlayers(15.0).forEach(p -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 150, 0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 150, 1));
        });
        return true;
    }

    public boolean isAbilityOnCooldown(String abilityId) {
        return abilityCooldowns.getOrDefault(abilityId, 0L) > System.currentTimeMillis();
    }

    public void setAbilityCooldown(String abilityId, long cooldownMs) {
        abilityCooldowns.put(abilityId, System.currentTimeMillis() + cooldownMs);
    }

    public boolean isOnGlobalCooldown() {
        return System.currentTimeMillis() - lastAbilityUse < ABILITY_GLOBAL_COOLDOWN;
    }

    public void triggerGlobalCooldown() {
        lastAbilityUse = System.currentTimeMillis();
    }

    protected long getAbilityCooldown(String abilityId) {
        return switch (abilityId.toLowerCase()) {
            case "roar" -> 15000L;
            case "stomp" -> 12000L;
            case "summon" -> 30000L;
            case "freeze" -> 20000L;
            case "darkness" -> 25000L;
            default -> 10000L;
        };
    }

    protected List<String> getPossibleAbilities() {
        List<String> abilities = new ArrayList<>();
        if (!isAbilityOnCooldown("roar")) abilities.add("roar");
        if (!isAbilityOnCooldown("stomp")) abilities.add("stomp");
        if (currentPhase >= 2 && !isAbilityOnCooldown("summon")) abilities.add("summon");

        // Type-specific abilities
        if (isFrozenType() && currentPhase >= 2 && !isAbilityOnCooldown("freeze")) {
            abilities.add("freeze");
        }
        if (isWardenType() && currentPhase >= 3 && !isAbilityOnCooldown("darkness")) {
            abilities.add("darkness");
        }

        return abilities;
    }

    // ================ VISUAL EFFECTS ================

    private void applyBossVisualEffects() {
        if (!isValid()) return;

        try {
            Location loc = entity.getLocation();

            // Phase-based effects
            switch (currentPhase) {
                case 1 -> {
                    entity.getWorld().spawnParticle(org.bukkit.Particle.FLAME,
                            loc.clone().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0.02);
                }
                case 2 -> {
                    entity.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                            loc.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0.05);
                }
                case 3 -> {
                    entity.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH,
                            loc.clone().add(0, 1, 0), 4, 0.6, 0.6, 0.6, 0.1);
                }
                case 4 -> {
                    entity.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                            loc.clone().add(0, 1, 0), 5, 0.8, 0.8, 0.8, 0.1);
                    if (berserkMode) {
                        entity.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                                loc.clone().add(0, 0.5, 0), 2, 0.4, 0.2, 0.4, 0.05);
                    }
                }
            }

            // Type-specific effects
            if (isFrozenType()) {
                entity.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                        loc.clone().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.02);
            } else if (isWardenType()) {
                entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL,
                        loc.clone().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0.01);
            }

        } catch (Exception e) {
            // Silent fail for effects
        }
    }

    // ================ UTILITY & GETTERS ================

    private String getDisplayBossName() {
        if (hasCustomTitle && bossTitle != null && !bossTitle.isEmpty()) {
            return bossTitle;
        }
        return (getOriginalName() != null) ? ChatColor.stripColor(getOriginalName()) : type.getDefaultName();
    }

    public void broadcastMessage(String message, double radius) {
        if (!isValid()) return;
        getNearbyPlayers(radius).forEach(player -> player.sendMessage(message));
    }

    public List<Player> getNearbyPlayers(double radius) {
        if (!isValid()) return java.util.Collections.emptyList();
        try {
            return entity.getNearbyEntities(radius, radius, radius).stream()
                    .filter(Entity::isValid)
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .filter(player -> player.isOnline() && !player.isDead())
                    .filter(player -> player.getGameMode() != org.bukkit.GameMode.CREATIVE)
                    .filter(player -> player.getGameMode() != org.bukkit.GameMode.SPECTATOR)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    public boolean canChangePhase() {
        return System.currentTimeMillis() - lastPhaseChangeTime >= PHASE_CHANGE_COOLDOWN;
    }

    // ================ ENHANCED HEALTH BAR ================

    @Override
    public String generateHealthBar() {
        if (!isValid()) return "";

        try {
            double health = entity.getHealth();
            double maxHealth = entity.getMaxHealth();

            if (health <= 0) health = 0.1;
            if (maxHealth <= 0) maxHealth = 1;

            // Extra long bar for world bosses
            int barLength = 60;

            // Check if in crit state
            boolean inCritState = isInCriticalState();

            ChatColor tierColor = ChatColor.DARK_RED; // World bosses always dark red
            String str = tierColor.toString();

            // Calculate health percentage
            double perc = health / maxHealth;

            // Set bar color based on critical state and phase
            String barColor;
            if (inCritState) {
                barColor = ChatColor.LIGHT_PURPLE.toString();
            } else if (berserkMode) {
                barColor = ChatColor.RED.toString();
            } else {
                barColor = ChatColor.GREEN.toString();
            }

            // Generate the bar
            for (int i = 1; i <= barLength; ++i) {
                str = perc >= (double) i / (double) barLength ?
                        str + barColor + "|" : str + ChatColor.GRAY + "|";
            }

            // Add phase indicator
            str += ChatColor.WHITE + " §7[P" + currentPhase + "]";

            return str;

        } catch (Exception e) {
            LOGGER.warning("[WorldBoss] Health bar generation failed: " + e.getMessage());
            return generateBossName();
        }
    }

    // ================ STATUS METHODS ================

    /**
     * Get boss status description
     */
    public String getBossStatus() {
        StringBuilder status = new StringBuilder();

        status.append("§4[WORLD BOSS] ");
        if (berserkMode) {
            status.append("§c§lBERSERK ");
        }
        status.append("§7Phase ").append(currentPhase);

        if (isInCriticalState()) {
            if (isCritReadyToAttack()) {
                status.append(" §5CHARGED");
            } else {
                status.append(" §6CRITICAL");
            }
        }

        return status.toString();
    }

    /**
     * Get detailed boss information
     */
    public String getBossInfo() {
        StringBuilder info = new StringBuilder();

        info.append(getBossStatus()).append("\n");
        info.append("§7Health: §f").append(String.format("%.0f", entity.getHealth()))
                .append("§7/§f").append(String.format("%.0f", entity.getMaxHealth()))
                .append(" §7(").append(String.format("%.1f", getHealthPercentage() * 100)).append("%)\n");
        info.append("§7Active Abilities: §f").append(getPossibleAbilities().size()).append("/").append(4);

        return info.toString();
    }

    // ================ ENHANCED REMOVAL ================

    @Override
    public void remove() {
        try {
            // Apply boss death effects
            if (isValid()) {
                applyBossDeathEffects();
            }

            // Broadcast death message
            broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD +
                    getDisplayBossName() + " has been defeated!", 100);

            // Call parent removal
            super.remove();

        } catch (Exception e) {
            LOGGER.warning("[WorldBoss] Boss removal failed: " + e.getMessage());
        }
    }

    private void applyBossDeathEffects() {
        try {
            Location loc = entity.getLocation();

            // Massive death explosion
            entity.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.5f);
            entity.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.3f);

            // Multiple explosion particles
            for (int i = 0; i < 5; i++) {
                Location explosionLoc = loc.clone().add(
                        (Math.random() - 0.5) * 4,
                        Math.random() * 2,
                        (Math.random() - 0.5) * 4
                );
                entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE,
                        explosionLoc, 3, 0.5, 0.5, 0.5, 0.1);
            }

            // Celebration effects
            entity.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK,
                    loc.clone().add(0, 2, 0), 50, 3, 3, 3, 0.3);

            // Type-specific death effects
            if (isFrozenType()) {
                entity.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE,
                        loc, 100, 5.0, 5.0, 5.0, 0.2);
            } else if (isWardenType()) {
                entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                        loc, 50, 3.0, 3.0, 3.0, 0.2);
            }

        } catch (Exception e) {
            // Silent fail for death effects
        }
    }

    // ================ BOSS GETTERS ================

    public String getBossTitle() { return bossTitle; }
    public boolean hasCustomTitle() { return hasCustomTitle; }
    public Location getSpawnLocation() { return spawnLocation; }
    public long getLastPhaseChangeTime() { return lastPhaseChangeTime; }
    public long getLastAbilityUse() { return lastAbilityUse; }
    public Map<String, Long> getAbilityCooldowns() { return new HashMap<>(abilityCooldowns); }
}
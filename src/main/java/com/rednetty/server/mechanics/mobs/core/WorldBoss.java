package com.rednetty.server.mechanics.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enhanced extension of EliteMob for world bosses with phases and improved name tracking
 */
public class WorldBoss extends EliteMob {

    // ================ BOSS STATE ================

    private int currentPhase = 1;
    private boolean berserkMode = false;
    private Map<String, Long> abilityCooldowns = new HashMap<>();
    private long lastPhaseChangeTime = 0;
    private Location spawnLocation;

    // ================ ENHANCED NAME TRACKING ================

    private String bossTitle; // The boss's special title
    private boolean hasCustomTitle = false;
    private long lastAbilityUse = 0;
    private int totalAbilitiesUsed = 0;

    // ================ BOSS CONSTANTS ================

    private static final long PHASE_CHANGE_COOLDOWN = 5000; // 5 seconds between phase changes
    private static final double PHASE_2_THRESHOLD = 0.75;
    private static final double PHASE_3_THRESHOLD = 0.5;
    private static final double PHASE_4_THRESHOLD = 0.25;
    private static final long ABILITY_GLOBAL_COOLDOWN = 3000; // 3 seconds between any abilities
    private String formattedOriginalName;
    private String storedOriginalName;

    /**
     * Create a new world boss
     *
     * @param type Boss type
     * @param tier Boss tier (typically 5-6)
     */
    public WorldBoss(MobType type, int tier) {
        super(type, tier);

        // Ensure world boss metadata and capture title
        if (type.isWorldBoss()) {
            this.bossTitle = type.getDefaultName();
            this.hasCustomTitle = true;
        }
    }

    @Override
    public boolean spawn(Location location) {
        boolean result = super.spawn(location);

        if (result && entity != null) {
            // Store spawn location
            this.spawnLocation = location.clone();

            // Set world boss metadata
            entity.setMetadata("worldboss",
                    new FixedMetadataValue(YakRealms.getInstance(), true));

            // FIXED: Apply boss-specific name formatting
            applyBossNameFormatting();

            // Register with manager
            //MobManager.getInstance().registerWorldBoss(this);

            // Announce spawn
            announceSpawn();
        }

        return result;
    }

    /**
     * FIXED: Apply special boss name formatting and store properly
     */
    private void applyBossNameFormatting() {
        if (entity == null) return;

        try {
            String bossName;
            if (hasCustomTitle && bossTitle != null && !bossTitle.isEmpty()) {
                // Use the boss's special title
                bossName = ChatColor.DARK_RED + "" + ChatColor.BOLD + bossTitle;
            } else {
                // Fallback to tier-specific name
                String tierName = type.getTierSpecificName(tier);
                bossName = ChatColor.DARK_RED + "" + ChatColor.BOLD + tierName;
            }

            entity.setCustomName(bossName);
            entity.setCustomNameVisible(true);
            currentDisplayName = bossName;

            // FIXED: Update our internal name tracking
            if (hasCustomTitle) {
                // Store the boss title as our formatted original name
                formattedOriginalName = bossName;
                storedOriginalName = ChatColor.stripColor(bossName);
            }

            // Update metadata
            entity.setMetadata("name", new FixedMetadataValue(YakRealms.getInstance(), bossName));

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[WorldBoss] §7Applied boss name formatting: '%s'", bossName));
            }

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Boss name formatting failed: %s", e.getMessage()));
        }
    }

    /**
     * Announce the boss spawn to nearby players
     */
    protected void announceSpawn() {
        if (entity == null) return;

        try {
            // Visual and sound effects
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            entity.getWorld().strikeLightning(entity.getLocation());

            // Broadcast message with boss name
            String bossName = getDisplayBossName();
            broadcastMessage(ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD +
                    bossName + ": " + ChatColor.YELLOW +
                    "Prepare for your doom, mortals!", 50);

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Spawn announcement failed: %s", e.getMessage()));
        }
    }

    /**
     * FIXED: Get the proper display name for the boss
     */
    private String getDisplayBossName() {
        if (hasCustomTitle && bossTitle != null && !bossTitle.isEmpty()) {
            return bossTitle;
        }

        if (currentDisplayName != null && !currentDisplayName.isEmpty()) {
            return ChatColor.stripColor(currentDisplayName);
        }

        return type.getDefaultName();
    }

    /**
     * Broadcast a message to nearby players
     *
     * @param message The message to send
     * @param radius  The radius to broadcast to
     */
    public void broadcastMessage(String message, double radius) {
        if (entity == null) return;

        try {
            for (Player player : getNearbyPlayers(radius)) {
                player.sendMessage(message);
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Message broadcast failed: %s", e.getMessage()));
        }
    }

    /**
     * Get nearby players within a radius with enhanced error handling
     *
     * @param radius The radius to check
     * @return List of players
     */
    @Override
    public List<Player> getNearbyPlayers(double radius) {
        if (entity == null) return java.util.Collections.emptyList();

        try {
            return entity.getNearbyEntities(radius, radius, radius).stream()
                    .filter(Entity::isValid)
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .filter(player -> player.isOnline() && !player.isDead())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Failed to get nearby players: %s", e.getMessage()));
            return java.util.Collections.emptyList();
        }
    }

    // ================ PHASE MANAGEMENT ================

    /**
     * Get the current phase
     *
     * @return Current phase (1-4)
     */
    public int getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Transition to the next phase
     *
     * @return true if successful
     */
    public boolean nextPhase() {
        if (currentPhase < 4) {
            int oldPhase = currentPhase;
            currentPhase++;
            onPhaseChanged(oldPhase, currentPhase);
            return true;
        }
        return false;
    }

    /**
     * Set a specific phase
     *
     * @param phase Phase to set (1-4)
     * @return true if successful
     */
    public boolean setPhase(int phase) {
        if (phase >= 1 && phase <= 4 && phase != currentPhase) {
            int oldPhase = currentPhase;
            currentPhase = phase;
            onPhaseChanged(oldPhase, currentPhase);
            return true;
        }
        return false;
    }

    /**
     * Process phase transitions based on health
     */
    public void processPhaseTransitions() {
        if (entity == null || !entity.isValid() ||
                System.currentTimeMillis() - lastPhaseChangeTime < PHASE_CHANGE_COOLDOWN) {
            return;
        }

        double healthPercentage = getHealthPercentage();

        if (healthPercentage <= PHASE_4_THRESHOLD && currentPhase < 4) {
            setPhase(4);
        } else if (healthPercentage <= PHASE_3_THRESHOLD && currentPhase < 3) {
            setPhase(3);
        } else if (healthPercentage <= PHASE_2_THRESHOLD && currentPhase < 2) {
            setPhase(2);
        }
    }

    /**
     * Get the boss's health percentage
     *
     * @return Health percentage (0.0 - 1.0)
     */
    public double getHealthPercentage() {
        if (entity == null) return 0;
        return entity.getHealth() / entity.getMaxHealth();
    }

    /**
     * Handle phase change events
     *
     * @param oldPhase The previous phase
     * @param newPhase The new phase
     */
    protected void onPhaseChanged(int oldPhase, int newPhase) {
        if (entity == null || !entity.isValid()) return;

        try {
            // Update last phase change time
            lastPhaseChangeTime = System.currentTimeMillis();

            // Apply visual effects
            entity.getWorld().playSound(entity.getLocation(),
                    Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE,
                    entity.getLocation(), 5, 1, 1, 1, 0.1);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));

            // Broadcast phase change
            String bossName = getDisplayBossName();
            String message = ChatColor.YELLOW + "The " + bossName +
                    ChatColor.YELLOW + " enters phase " + newPhase + "!";
            broadcastMessage(message, 50);

            // Apply phase-specific effects
            applyPhaseEffects(newPhase);

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[WorldBoss] §7%s phase change: %d → %d",
                        getDisplayBossName(), oldPhase, newPhase));
            }

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Phase change handling failed: %s", e.getMessage()));
        }
    }

    /**
     * Apply effects for a specific phase
     *
     * @param phase The phase number
     */
    protected void applyPhaseEffects(int phase) {
        if (entity == null) return;

        try {
            // Remove existing phase effects first
            entity.removePotionEffect(PotionEffectType.SPEED);
            entity.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);

            // Apply effects based on phase
            switch (phase) {
                case 2:
                    // Phase 2 - Faster movement, minor damage boost
                    entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                    break;
                case 3:
                    // Phase 3 - Even faster, stronger damage
                    entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
                    entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 1));
                    break;
                case 4:
                    // Phase 4 - Maximum speed, damage, and enter berserk
                    entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, Integer.MAX_VALUE, 3));
                    entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2));

                    if (!berserkMode) {
                        activateBerserkMode();
                    }
                    break;
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Phase effects application failed: %s", e.getMessage()));
        }
    }

    // ================ BERSERK MODE ================

    /**
     * Check if boss is in berserk mode
     *
     * @return true if in berserk mode
     */
    public boolean isBerserk() {
        return berserkMode;
    }

    /**
     * Activate berserk mode
     */
    public void activateBerserkMode() {
        if (!berserkMode) {
            berserkMode = true;
            onBerserkModeChanged(true);
        }
    }

    /**
     * Deactivate berserk mode
     */
    public void deactivateBerserkMode() {
        if (berserkMode) {
            berserkMode = false;
            onBerserkModeChanged(false);
        }
    }

    /**
     * Handle berserk mode changes
     *
     * @param berserk true if entering berserk, false if leaving
     */
    protected void onBerserkModeChanged(boolean berserk) {
        if (entity == null || !entity.isValid()) return;

        try {
            if (berserk) {
                // Apply berserk effects
                entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2));
                entity.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, Integer.MAX_VALUE, 2));

                // Visual effects
                entity.getWorld().playSound(entity.getLocation(),
                        Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
                entity.getWorld().spawnParticle(org.bukkit.Particle.LAVA,
                        entity.getLocation(), 20, 1, 1, 1, 0.1);

                // Broadcast message
                String bossName = getDisplayBossName();
                String message = ChatColor.RED + "The " + bossName +
                        ChatColor.RED + " has entered a berserk state!";
                broadcastMessage(message, 50);
            } else {
                // Remove berserk effects
                entity.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);

                // Keep some effects based on phase
                int speedLevel = Math.min(currentPhase - 1, 1);
                if (speedLevel >= 0) {
                    entity.addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, Integer.MAX_VALUE, speedLevel));
                } else {
                    entity.removePotionEffect(PotionEffectType.SPEED);
                }

                // Broadcast message
                String bossName = getDisplayBossName();
                String message = ChatColor.YELLOW + "The " + bossName +
                        ChatColor.YELLOW + " is no longer berserk.";
                broadcastMessage(message, 50);
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Berserk mode change failed: %s", e.getMessage()));
        }
    }

    // ================ ABILITY COOLDOWNS ================

    /**
     * Check if an ability is on cooldown
     *
     * @param abilityId The ability ID to check
     * @return true if on cooldown
     */
    public boolean isAbilityOnCooldown(String abilityId) {
        if (!abilityCooldowns.containsKey(abilityId)) {
            return false;
        }

        return System.currentTimeMillis() < abilityCooldowns.get(abilityId);
    }

    /**
     * Set cooldown for an ability
     *
     * @param abilityId  The ability ID
     * @param cooldownMs The cooldown in milliseconds
     */
    public void setAbilityCooldown(String abilityId, long cooldownMs) {
        abilityCooldowns.put(abilityId, System.currentTimeMillis() + cooldownMs);
    }

    /**
     * Get cooldown data for all abilities
     *
     * @return Map of ability ID to cooldown end time
     */
    public Map<String, Long> getAbilityCooldowns() {
        return new HashMap<>(abilityCooldowns);
    }

    /**
     * Check if the global ability cooldown is active
     *
     * @return true if on global cooldown
     */
    public boolean isOnGlobalCooldown() {
        return System.currentTimeMillis() - lastAbilityUse < ABILITY_GLOBAL_COOLDOWN;
    }

    /**
     * Trigger the global ability cooldown
     */
    public void triggerGlobalCooldown() {
        lastAbilityUse = System.currentTimeMillis();
        totalAbilitiesUsed++;
    }

    // ================ ENHANCED NAME VISIBILITY OVERRIDE ================

    @Override
    public void updateNameVisibility() {
        if (!isValid()) return;

        try {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamageTime) < NAME_VISIBILITY_TIMEOUT;
            boolean shouldShowHealthBar = recentlyDamaged || inCriticalState;

            if (shouldShowHealthBar) {
                // Show health bar for world boss
                if (!isShowingHealthBar()) {
                    updateHealthBar();
                }
                nameVisible = true;
            } else {
                // Restore boss name (not regular elite name)
                if (isShowingHealthBar() || nameVisible) {
                    restoreBossName();
                    nameVisible = false;
                }
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Name visibility update failed: %s", e.getMessage()));
        }
    }

    /**
     * FIXED: Restore proper boss name with special formatting
     */
    private void restoreBossName() {
        if (!isValid() || inCriticalState) return;

        try {
            String nameToRestore = getBossNameToRestore();

            if (nameToRestore != null && !nameToRestore.isEmpty()) {
                entity.setCustomName(nameToRestore);
                entity.setCustomNameVisible(true);
                currentDisplayName = nameToRestore;

                if (YakRealms.getInstance().isDebugMode()) {
                    LOGGER.info(String.format("§6[WorldBoss] §7Restored boss name: %s", nameToRestore));
                }
            }
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Boss name restoration failed: %s", e.getMessage()));
        }
    }

    private String getBossNameToRestore() {
        // First priority: boss title
        if (hasCustomTitle && bossTitle != null && !bossTitle.isEmpty()) {
            return ChatColor.DARK_RED + "" + ChatColor.BOLD + bossTitle;
        }

        // Second priority: stored formatted original name
        String formattedOriginal = getFormattedOriginalName();
        if (formattedOriginal != null && !formattedOriginal.isEmpty() && !isHealthBar(formattedOriginal)) {
            return formattedOriginal;
        }

        // Third priority: stored original name with boss formatting
        String originalName = getOriginalName();
        if (originalName != null && !originalName.isEmpty()) {
            return ChatColor.DARK_RED + "" + ChatColor.BOLD + originalName;
        }

        // Fourth priority: metadata
        if (entity.hasMetadata("name")) {
            try {
                String metaName = entity.getMetadata("name").get(0).asString();
                if (metaName != null && !metaName.isEmpty() && !isHealthBar(metaName)) {
                    return metaName;
                }
            } catch (Exception e) {
                // Fall through to next option
            }
        }

        // Fifth priority: lightning mob metadata
        if (entity.hasMetadata("LightningMob")) {
            try {
                String lightningName = entity.getMetadata("LightningMob").get(0).asString();
                if (lightningName != null && !lightningName.isEmpty()) {
                    return lightningName;
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }

        // Last resort: generate default boss name
        return generateDefaultBossName();
    }

    private boolean isHealthBar(String name) {
        if (name == null) return false;
        return name.contains(ChatColor.GREEN + "|") ||
                name.contains(ChatColor.GRAY + "|") ||
                name.contains(ChatColor.LIGHT_PURPLE + "|");
    }

    private String generateDefaultBossName() {
        String tierName = type.getTierSpecificName(tier);
        return ChatColor.DARK_RED + "" + ChatColor.BOLD + tierName;
    }

    // ================ ABILITY SYSTEM ================

    /**
     * Execute a specific ability
     *
     * @param abilityId The ability ID
     * @return true if executed successfully
     */
    public boolean executeAbility(String abilityId) {
        if (!isValid() || isOnGlobalCooldown() || isAbilityOnCooldown(abilityId)) {
            return false;
        }

        try {
            boolean executed = false;

            switch (abilityId.toLowerCase()) {
                case "roar":
                    executed = executeRoarAbility();
                    break;
                case "stomp":
                    executed = executeStompAbility();
                    break;
                case "summon":
                    executed = executeSummonAbility();
                    break;
                case "heal":
                    executed = executeHealAbility();
                    break;
                case "teleport":
                    executed = executeTeleportAbility();
                    break;
                default:
                    // Allow subclasses to handle custom abilities
                    executed = executeCustomAbility(abilityId);
                    break;
            }

            if (executed) {
                triggerGlobalCooldown();
                setAbilityCooldown(abilityId, getAbilityCooldown(abilityId));
            }

            return executed;

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Ability execution failed (%s): %s", abilityId, e.getMessage()));
            return false;
        }
    }

    /**
     * Get the cooldown time for a specific ability
     *
     * @param abilityId The ability ID
     * @return Cooldown time in milliseconds
     */
    protected long getAbilityCooldown(String abilityId) {
        switch (abilityId.toLowerCase()) {
            case "roar": return 15000; // 15 seconds
            case "stomp": return 12000; // 12 seconds
            case "summon": return 30000; // 30 seconds
            case "heal": return 45000; // 45 seconds
            case "teleport": return 20000; // 20 seconds
            default: return 10000; // 10 seconds default
        }
    }

    /**
     * Execute roar ability - fear effect on nearby players
     */
    protected boolean executeRoarAbility() {
        if (entity == null) return false;

        try {
            List<Player> nearbyPlayers = getNearbyPlayers(15.0);

            // Visual and sound effects
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE,
                    entity.getLocation().add(0, 1, 0), 20, 2, 1, 2, 0.1);

            // Apply fear effect (slow + confusion)
            for (Player player : nearbyPlayers) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 1));
                player.sendMessage(ChatColor.DARK_RED + "The " + getDisplayBossName() + " lets out a terrifying roar!");
            }

            broadcastMessage(ChatColor.RED + "The " + getDisplayBossName() + " roars with rage!", 50);
            return true;

        } catch (Exception e) {
            LOGGER.warning("Roar ability failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute stomp ability - knockback and damage
     */
    protected boolean executeStompAbility() {
        if (entity == null) return false;

        try {
            List<Player> nearbyPlayers = getNearbyPlayers(8.0);

            // Visual and sound effects
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE,
                    entity.getLocation(), 10, 3, 0.5, 3, 0.1);

            // Apply damage and knockback
            for (Player player : nearbyPlayers) {
                // Calculate damage based on distance
                double distance = player.getLocation().distance(entity.getLocation());
                int damage = (int) (20 - (distance * 2)); // Less damage further away
                damage = Math.max(5, damage); // Minimum 5 damage

                player.damage(damage, entity);

                // Apply knockback
                org.bukkit.util.Vector knockback = player.getLocation().toVector()
                        .subtract(entity.getLocation().toVector()).normalize().multiply(2);
                knockback.setY(0.5); // Add upward component
                player.setVelocity(knockback);
            }

            broadcastMessage(ChatColor.RED + "The ground shakes as " + getDisplayBossName() + " stomps!", 50);
            return true;

        } catch (Exception e) {
            LOGGER.warning("Stomp ability failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute summon ability - spawn minions
     */
    protected boolean executeSummonAbility() {
        if (entity == null) return false;

        try {
            // Spawn 2-4 minions around the boss
            int minionCount = 2 + (currentPhase - 1); // More minions in later phases

            for (int i = 0; i < minionCount; i++) {
                double angle = (2 * Math.PI / minionCount) * i;
                double x = entity.getLocation().getX() + (3 * Math.cos(angle));
                double z = entity.getLocation().getZ() + (3 * Math.sin(angle));

                Location spawnLoc = new Location(entity.getWorld(), x, entity.getLocation().getY(), z);

                // Spawn a minion (implementation depends on your mob system)
                spawnMinion(spawnLoc);
            }

            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);
            broadcastMessage(ChatColor.DARK_PURPLE + getDisplayBossName() + " summons reinforcements!", 50);
            return true;

        } catch (Exception e) {
            LOGGER.warning("Summon ability failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute heal ability - restore health
     */
    protected boolean executeHealAbility() {
        if (entity == null) return false;

        try {
            double currentHealth = entity.getHealth();
            double maxHealth = entity.getMaxHealth();
            double healAmount = maxHealth * 0.15; // Heal 15% of max health

            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            entity.setHealth(newHealth);

            // Visual effects
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                    entity.getLocation().add(0, 2, 0), 10, 1, 1, 1, 0.1);

            broadcastMessage(ChatColor.GREEN + getDisplayBossName() + " regenerates health!", 50);
            return true;

        } catch (Exception e) {
            LOGGER.warning("Heal ability failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute teleport ability - teleport to a player
     */
    protected boolean executeTeleportAbility() {
        if (entity == null) return false;

        try {
            List<Player> nearbyPlayers = getNearbyPlayers(30.0);
            if (nearbyPlayers.isEmpty()) return false;

            // Choose a random player to teleport to
            Player target = nearbyPlayers.get((int) (Math.random() * nearbyPlayers.size()));
            Location teleportLoc = target.getLocation().clone();

            // Offset slightly to avoid teleporting inside the player
            teleportLoc.add(2, 0, 2);

            // Effects at old location
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
                    entity.getLocation(), 20, 1, 1, 1, 0.1);

            // Teleport
            entity.teleport(teleportLoc);

            // Effects at new location
            entity.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            entity.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
                    teleportLoc, 20, 1, 1, 1, 0.1);

            broadcastMessage(ChatColor.DARK_PURPLE + getDisplayBossName() + " teleports!", 50);
            return true;

        } catch (Exception e) {
            LOGGER.warning("Teleport ability failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Execute custom ability - override in subclasses
     *
     * @param abilityId The custom ability ID
     * @return true if executed successfully
     */
    protected boolean executeCustomAbility(String abilityId) {
        // Override in specific boss implementations
        return false;
    }

    /**
     * Spawn a minion at the specified location
     *
     * @param location The spawn location
     */
    protected void spawnMinion(Location location) {
        // Implementation depends on your mob system
        // This is a placeholder that can be overridden in specific boss implementations
        if (YakRealms.getInstance().isDebugMode()) {
            LOGGER.info("§6[WorldBoss] §7Would spawn minion at " +
                    location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        }
    }

    // ================ UPDATE AND MANAGEMENT ================

    /**
     * Get the spawn location
     *
     * @return Original spawn location
     */
    public Location getSpawnLocation() {
        return spawnLocation != null ? spawnLocation.clone() : null;
    }

    /**
     * Update boss states and mechanics
     */
    public void update() {
        if (entity == null || !entity.isValid() || entity.isDead()) return;

        try {
            // Process phase transitions
            processPhaseTransitions();

            // Remove expired cooldowns
            long currentTime = System.currentTimeMillis();
            abilityCooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue());

            // Update name visibility
            updateNameVisibility();

            // Use abilities based on phase and health
            considerUsingAbilities();

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Update failed: %s", e.getMessage()));
        }
    }

    /**
     * Consider using abilities based on current state
     */
    private void considerUsingAbilities() {
        if (isOnGlobalCooldown()) return;

        try {
            // Use abilities more frequently in later phases
            double abilityChance = 0.05 + (currentPhase * 0.02); // 5% base + 2% per phase

            if (Math.random() < abilityChance) {
                String[] possibleAbilities = getPossibleAbilities();

                if (possibleAbilities.length > 0) {
                    String ability = possibleAbilities[(int) (Math.random() * possibleAbilities.length)];
                    executeAbility(ability);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Ability consideration failed: " + e.getMessage());
        }
    }

    /**
     * Get possible abilities for current state
     *
     * @return Array of ability IDs
     */
    protected String[] getPossibleAbilities() {
        // Basic abilities available to all world bosses
        java.util.List<String> abilities = new java.util.ArrayList<>();

        if (!isAbilityOnCooldown("roar")) abilities.add("roar");
        if (!isAbilityOnCooldown("stomp")) abilities.add("stomp");

        // More abilities unlock in later phases
        if (currentPhase >= 2 && !isAbilityOnCooldown("summon")) abilities.add("summon");
        if (currentPhase >= 3 && !isAbilityOnCooldown("teleport")) abilities.add("teleport");
        if (currentPhase >= 4 && getHealthPercentage() < 0.5 && !isAbilityOnCooldown("heal")) abilities.add("heal");

        return abilities.toArray(new String[0]);
    }

    /**
     * Format a message from this boss
     *
     * @param message Message content
     * @return Formatted message
     */
    protected String formatBossMessage(String message) {
        String bossName = getDisplayBossName();
        return ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD +
                bossName + ": " + ChatColor.YELLOW + message;
    }

    // ================ ENHANCED DAMAGE PROCESSING ================

    @Override
    public double damage(double damage) {
        if (!isValid()) return 0;

        try {
            double finalDamage = super.damage(damage);

            // World boss specific damage handling
            handleBossDamageEffects(finalDamage);

            return finalDamage;
        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Damage processing failed: %s", e.getMessage()));
            return 0;
        }
    }

    private void handleBossDamageEffects(double damage) {
        try {
            // Phase transition checks
            processPhaseTransitions();

            // Special boss abilities on damage
            if (getCurrentPhase() >= 3 && !isAbilityOnCooldown("damage_response")) {
                triggerDamageResponseAbility();
                setAbilityCooldown("damage_response", 10000); // 10 second cooldown
            }

            // Enrage at low health
            if (getHealthPercentage() < 0.2 && !berserkMode) {
                activateBerserkMode();
            }

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Damage effects failed: %s", e.getMessage()));
        }
    }

    private void triggerDamageResponseAbility() {
        // Random damage response ability
        String[] responseAbilities = {"roar", "stomp"};
        String ability = responseAbilities[(int) (Math.random() * responseAbilities.length)];

        if (!isAbilityOnCooldown(ability)) {
            executeAbility(ability);
        }
    }

    @Override
    public void remove() {
        try {
            super.remove();

            // Unregister from world boss manager
            //MobManager.getInstance().unregisterWorldBoss();

            // Clear boss-specific data
            abilityCooldowns.clear();
            spawnLocation = null;

            if (YakRealms.getInstance().isDebugMode()) {
                LOGGER.info(String.format("§6[WorldBoss] §7Removed world boss: %s", getDisplayBossName()));
            }

        } catch (Exception e) {
            LOGGER.warning(String.format("§c[WorldBoss] Removal failed: %s", e.getMessage()));
        }
    }

    // ================ GETTERS AND SETTERS ================

    public String getBossTitle() {
        return bossTitle;
    }

    public boolean hasCustomTitle() {
        return hasCustomTitle;
    }

    public void setBossTitle(String title) {
        this.bossTitle = title;
        this.hasCustomTitle = title != null && !title.isEmpty();

        // Update the displayed name if not in combat
        if (!isShowingHealthBar()) {
            applyBossNameFormatting();
        }
    }

    public int getTotalAbilitiesUsed() {
        return totalAbilitiesUsed;
    }

    public long getLastAbilityUse() {
        return lastAbilityUse;
    }

    public long getTimeSinceLastPhaseChange() {
        return System.currentTimeMillis() - lastPhaseChangeTime;
    }

    public boolean canChangePhase() {
        return System.currentTimeMillis() - lastPhaseChangeTime >= PHASE_CHANGE_COOLDOWN;
    }

    /**
     * Get boss statistics for debugging/admin purposes
     *
     * @return Formatted stats string
     */
    public String getBossStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("§6=== ").append(getDisplayBossName()).append(" Stats ===\n");
        stats.append("§7Phase: §f").append(currentPhase).append("/4\n");
        stats.append("§7Health: §f").append(String.format("%.1f%%", getHealthPercentage() * 100)).append("\n");
        stats.append("§7Berserk: §f").append(berserkMode ? "§cYes" : "§aNo").append("\n");
        stats.append("§7Abilities Used: §f").append(totalAbilitiesUsed).append("\n");
        stats.append("§7Active Cooldowns: §f").append(abilityCooldowns.size()).append("\n");

        if (spawnLocation != null) {
            stats.append("§7Spawn Location: §f").append(spawnLocation.getBlockX())
                    .append(", ").append(spawnLocation.getBlockY())
                    .append(", ").append(spawnLocation.getBlockZ()).append("\n");
        }

        return stats.toString();
    }
}
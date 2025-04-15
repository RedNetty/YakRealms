package com.rednetty.server.mechanics.mobs.core;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.MobManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extension of EliteMob for world bosses with phases
 */
public class WorldBoss extends EliteMob {

    private int currentPhase = 1;
    private boolean berserkMode = false;
    private Map<String, Long> abilityCooldowns = new HashMap<>();
    private long lastPhaseChangeTime = 0;
    private Location spawnLocation;

    /**
     * Create a new world boss
     *
     * @param type Boss type
     * @param tier Boss tier (typically 5-6)
     */
    public WorldBoss(MobType type, int tier) {
        super(type, tier);

        // Ensure world boss metadata
        if (type.isWorldBoss()) {
            this.customName = type.getDefaultName();
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

            // Register with manager
            MobManager.getInstance().registerWorldBoss(this);

            // Announce spawn
            announceSpawn();
        }

        return result;
    }

    /**
     * Announce the boss spawn to nearby players
     */
    protected void announceSpawn() {
        if (entity == null) return;

        // Visual and sound effects
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        entity.getWorld().strikeLightning(entity.getLocation());

        // Broadcast message
        broadcastMessage(ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD +
                getCustomName() + ": " + ChatColor.YELLOW +
                "Prepare for your doom, mortals!", 50);
    }

    /**
     * Broadcast a message to nearby players
     *
     * @param message The message to send
     * @param radius  The radius to broadcast to
     */
    public void broadcastMessage(String message, double radius) {
        if (entity == null) return;

        for (Player player : getNearbyPlayers(radius)) {
            player.sendMessage(message);
        }
    }

    /**
     * Get nearby players within a radius
     *
     * @param radius The radius to check
     * @return List of players
     */
    public List<Player> getNearbyPlayers(double radius) {
        if (entity == null) return java.util.Collections.emptyList();

        List<Player> players = new java.util.ArrayList<>();

        for (org.bukkit.entity.Entity nearby :
                entity.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player) {
                players.add((Player) nearby);
            }
        }

        return players;
    }

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
                System.currentTimeMillis() - lastPhaseChangeTime < 5000) { // 5-second cooldown
            return;
        }

        double healthPercentage = getHealthPercentage();

        if (healthPercentage <= 0.25 && currentPhase < 4) {
            setPhase(4);
        } else if (healthPercentage <= 0.5 && currentPhase < 3) {
            setPhase(3);
        } else if (healthPercentage <= 0.75 && currentPhase < 2) {
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

        // Update last phase change time
        lastPhaseChangeTime = System.currentTimeMillis();

        // Apply visual effects
        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        entity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_HUGE,
                entity.getLocation(), 5, 1, 1, 1, 0.1);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));

        // Broadcast phase change
        String message = ChatColor.YELLOW + "The " + getCustomName() +
                ChatColor.YELLOW + " enters phase " + newPhase + "!";
        broadcastMessage(message, 50);

        // Apply phase-specific effects
        applyPhaseEffects(newPhase);
    }

    /**
     * Apply effects for a specific phase
     *
     * @param phase The phase number
     */
    protected void applyPhaseEffects(int phase) {
        if (entity == null) return;

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
    }

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
            String message = ChatColor.RED + "The " + getCustomName() +
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
            String message = ChatColor.YELLOW + "The " + getCustomName() +
                    ChatColor.YELLOW + " is no longer berserk.";
            broadcastMessage(message, 50);
        }
    }

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
     * Get the spawn location
     *
     * @return Original spawn location
     */
    public Location getSpawnLocation() {
        return spawnLocation;
    }

    /**
     * Update boss states and mechanics
     */
    public void update() {
        if (entity == null || !entity.isValid() || entity.isDead()) return;

        // Process phase transitions
        processPhaseTransitions();

        // Remove expired cooldowns
        abilityCooldowns.entrySet().removeIf(
                entry -> System.currentTimeMillis() >= entry.getValue());
    }

    /**
     * Format a message from this boss
     *
     * @param message Message content
     * @return Formatted message
     */
    protected String formatBossMessage(String message) {
        return ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD +
                customName + ": " + ChatColor.YELLOW + message;
    }

    /**
     * Execute a specific ability
     *
     * @param abilityId The ability ID
     * @return true if executed successfully
     */
    public boolean executeAbility(String abilityId) {
        // Override in specific boss implementations
        return false;
    }

    @Override
    public void remove() {
        super.remove();

        // Unregister from world boss manager
        MobManager.getInstance().unregisterWorldBoss();
    }
}
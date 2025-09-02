package com.rednetty.server.core.mechanics.world.mobs.abilities;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.abilities.impl.DevastatingChargeAbility;
import com.rednetty.server.core.mechanics.world.mobs.abilities.impl.EarthquakeStompAbility;
import com.rednetty.server.core.mechanics.world.mobs.abilities.impl.TeleportStrikeAbility;
import com.rednetty.server.core.mechanics.world.mobs.abilities.impl.VoidPulseAbility;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.core.CustomMob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Elite Ability Manager - Professional strategic combat coordination system
 * 
 * This system manages:
 * - Intelligent ability selection based on combat context
 * - Telegraph timing and player warnings
 * - Ability cooldowns and usage limits
 * - Professional combat flow and pacing
 * - Strategic decision making for each elite archetype
 */
public class EliteAbilityManager {
    
    private static final Logger LOGGER = YakRealms.getInstance().getLogger();
    private static EliteAbilityManager instance;
    
    // Registry of all available abilities by archetype
    private final Map<EliteBehaviorArchetype, List<EliteAbility>> abilityRegistry = new HashMap<>();
    
    // Active abilities per mob (for cooldown tracking)
    private final Map<UUID, List<EliteAbility>> mobAbilities = new HashMap<>();
    
    // Combat contexts for active elites
    private final Map<UUID, CombatContext> combatContexts = new HashMap<>();
    
    // Ability usage tracking
    private final Map<UUID, Integer> abilityUsageCount = new HashMap<>();
    private final Map<UUID, Long> lastAbilityTime = new HashMap<>();
    
    // Configuration
    private static final int MAX_ABILITIES_PER_FIGHT = 8;
    private static final int MIN_ABILITY_INTERVAL = 100; // 5 seconds between abilities
    private static final double GLOBAL_ABILITY_CHANCE = 0.08; // 8% per tick when conditions are met
    
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    
    private EliteAbilityManager() {
        initializeAbilityRegistry();
        startAbilityManagementTask();
    }
    
    public static EliteAbilityManager getInstance() {
        if (instance == null) {
            instance = new EliteAbilityManager();
        }
        return instance;
    }
    
    // ==================== ABILITY REGISTRATION ====================
    
    private void initializeAbilityRegistry() {
        // Register Brute abilities
        List<EliteAbility> bruteAbilities = new ArrayList<>();
        bruteAbilities.add(new DevastatingChargeAbility());
        bruteAbilities.add(new EarthquakeStompAbility());
        abilityRegistry.put(EliteBehaviorArchetype.BRUTE, bruteAbilities);
        
        // Register Assassin abilities
        List<EliteAbility> assassinAbilities = new ArrayList<>();
        assassinAbilities.add(new TeleportStrikeAbility());
        abilityRegistry.put(EliteBehaviorArchetype.ASSASSIN, assassinAbilities);
        
        // Register Guardian abilities
        abilityRegistry.put(EliteBehaviorArchetype.GUARDIAN, new ArrayList<>());
        
        // Register Elementalist abilities (ElementalPhaseShiftAbility is now telegraphed)
        List<EliteAbility> elementalistAbilities = new ArrayList<>();
        elementalistAbilities.add(new com.rednetty.server.core.mechanics.world.mobs.abilities.impl.ElementalPhaseShiftAbility());
        abilityRegistry.put(EliteBehaviorArchetype.ELEMENTALIST, elementalistAbilities);
        
        // Register VoidWalker abilities
        List<EliteAbility> voidWalkerAbilities = new ArrayList<>();
        voidWalkerAbilities.add(new VoidPulseAbility());
        abilityRegistry.put(EliteBehaviorArchetype.VOID_WALKER, voidWalkerAbilities);
        
    }
    
    /**
     * Register a new ability for a specific archetype
     */
    public void registerAbility(EliteBehaviorArchetype archetype, EliteAbility ability) {
        abilityRegistry.computeIfAbsent(archetype, k -> new ArrayList<>()).add(ability);
    }
    
    // ==================== MOB MANAGEMENT ====================
    
    /**
     * Initialize abilities for a new elite mob
     */
    public void initializeMobAbilities(CustomMob mob, EliteBehaviorArchetype archetype) {
        UUID mobId = mob.getEntity().getUniqueId();
        
        // Clone abilities for this specific mob to track individual cooldowns
        List<EliteAbility> archAbilities = abilityRegistry.getOrDefault(archetype, new ArrayList<>());
        List<EliteAbility> mobSpecificAbilities = new ArrayList<>();
        
        for (EliteAbility template : archAbilities) {
            // Create new instances to avoid shared state
            try {
                EliteAbility instance = template.getClass().getDeclaredConstructor().newInstance();
                mobSpecificAbilities.add(instance);
            } catch (Exception e) {
                // Skip failed ability instances
            }
        }
        
        mobAbilities.put(mobId, mobSpecificAbilities);
        abilityUsageCount.put(mobId, 0);
        lastAbilityTime.put(mobId, 0L);
        
    }
    
    /**
     * Clean up data for a removed mob
     */
    public void cleanupMobData(UUID mobId) {
        mobAbilities.remove(mobId);
        combatContexts.remove(mobId);
        abilityUsageCount.remove(mobId);
        lastAbilityTime.remove(mobId);
    }
    
    // ==================== ABILITY EXECUTION ====================
    
    /**
     * Main ability processing method - called regularly for each elite
     */
    public void processAbilities(CustomMob mob, List<Player> nearbyPlayers) {
        UUID mobId = mob.getEntity().getUniqueId();
        
        if (!mobAbilities.containsKey(mobId) || nearbyPlayers.isEmpty()) {
            return;
        }
        
        // Check usage limits
        int usageCount = abilityUsageCount.getOrDefault(mobId, 0);
        if (usageCount >= MAX_ABILITIES_PER_FIGHT) {
            return;
        }
        
        // Check minimum interval between abilities
        long currentTime = System.currentTimeMillis();
        long lastUsed = lastAbilityTime.getOrDefault(mobId, 0L);
        if (currentTime - lastUsed < MIN_ABILITY_INTERVAL * 50) { // Convert ticks to ms
            return;
        }
        
        // Update combat context
        EliteBehaviorArchetype archetype = getArchetypeForMob(mob);
        if (archetype == null) return;
        
        CombatContext context = new CombatContext(mob.getEntity().getLocation(), nearbyPlayers, archetype);
        combatContexts.put(mobId, context);
        
        // Check global ability trigger chance
        if (random.nextDouble() > GLOBAL_ABILITY_CHANCE) {
            return;
        }
        
        // Get available abilities
        List<EliteAbility> abilities = mobAbilities.get(mobId);
        List<EliteAbility> usableAbilities = abilities.stream()
            .filter(ability -> ability.canUse(mob, nearbyPlayers, mob.getEntity().getTicksLived()))
            .toList();
            
        if (usableAbilities.isEmpty()) {
            return;
        }
        
        // Select the best ability based on context and priority
        EliteAbility selectedAbility = selectBestAbility(mob, usableAbilities, nearbyPlayers, context);
        
        if (selectedAbility != null) {
            double triggerChance = selectedAbility.getTriggerChance(mob, nearbyPlayers, context);
            
            if (random.nextDouble() < triggerChance) {
                executeAbility(mob, selectedAbility, nearbyPlayers, context);
            }
        }
    }
    
    /**
     * Select the best ability to use in the current context
     */
    private EliteAbility selectBestAbility(CustomMob mob, List<EliteAbility> usableAbilities, 
                                         List<Player> targets, CombatContext context) {
        
        // Sort abilities by priority and trigger chance
        return usableAbilities.stream()
            .sorted((a1, a2) -> {
                // First by priority
                int p1 = a1.getPriority(mob, targets, context).getValue();
                int p2 = a2.getPriority(mob, targets, context).getValue();
                int priorityComparison = Integer.compare(p2, p1); // Higher priority first
                
                if (priorityComparison != 0) {
                    return priorityComparison;
                }
                
                // Then by trigger chance
                double c1 = a1.getTriggerChance(mob, targets, context);
                double c2 = a2.getTriggerChance(mob, targets, context);
                return Double.compare(c2, c1); // Higher chance first
            })
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Execute the selected ability
     */
    private void executeAbility(CustomMob mob, EliteAbility ability, List<Player> targets, CombatContext context) {
        UUID mobId = mob.getEntity().getUniqueId();
        
        try {
            boolean success = ability.execute(mob, targets, context);
            
            if (success) {
                // Update tracking
                abilityUsageCount.put(mobId, abilityUsageCount.getOrDefault(mobId, 0) + 1);
                lastAbilityTime.put(mobId, System.currentTimeMillis());
                
            }
            
        } catch (Exception e) {
            // Log critical errors only
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Get the archetype for a mob (from metadata or behavior system)
     */
    private EliteBehaviorArchetype getArchetypeForMob(CustomMob mob) {
        if (!mob.getEntity().hasMetadata("elite_archetype")) {
            return null;
        }
        
        try {
            String archetypeName = mob.getEntity().getMetadata("elite_archetype").get(0).asString();
            return EliteBehaviorArchetype.valueOf(archetypeName);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Handle ability interruption
     */
    public void interruptAbilities(CustomMob mob, Player interrupter) {
        UUID mobId = mob.getEntity().getUniqueId();
        List<EliteAbility> abilities = mobAbilities.get(mobId);
        
        if (abilities != null) {
            abilities.stream()
                .filter(EliteAbility::isCharging)
                .forEach(ability -> ability.onInterrupt(mob, interrupter));
        }
    }
    
    /**
     * Get current combat context for a mob
     */
    public CombatContext getCombatContext(UUID mobId) {
        return combatContexts.get(mobId);
    }
    
    /**
     * Get ability usage statistics for a mob
     */
    public int getAbilityUsageCount(UUID mobId) {
        return abilityUsageCount.getOrDefault(mobId, 0);
    }
    
    /**
     * Check if a mob is currently using an ability
     */
    public boolean isUsingAbility(UUID mobId) {
        List<EliteAbility> abilities = mobAbilities.get(mobId);
        if (abilities == null) return false;
        
        return abilities.stream().anyMatch(ability -> ability.isCharging() || ability.isExecuting());
    }
    
    /**
     * Get all abilities for a mob
     */
    public List<EliteAbility> getMobAbilities(UUID mobId) {
        return mobAbilities.getOrDefault(mobId, new ArrayList<>());
    }
    
    // ==================== BACKGROUND TASKS ====================
    
    /**
     * Start the background task that manages ability systems
     */
    private void startAbilityManagementTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Clean up expired combat contexts
                    cleanupExpiredData();
                    
                    // Update ability cooldowns and states
                    updateAbilityStates();
                    
                } catch (Exception e) {
                    // Ignore management errors
                }
            }
        }.runTaskTimer(YakRealms.getInstance(), 20L, 40L); // Every 2 seconds
    }
    
    private void cleanupExpiredData() {
        // Remove data for mobs that no longer exist
        Set<UUID> toRemove = new HashSet<>();
        
        for (UUID mobId : mobAbilities.keySet()) {
            if (org.bukkit.Bukkit.getEntity(mobId) == null) {
                toRemove.add(mobId);
            }
        }
        
        toRemove.forEach(this::cleanupMobData);
        
    }
    
    private void updateAbilityStates() {
        // Update any time-based ability states
        long currentTime = System.currentTimeMillis();
        
        mobAbilities.values().forEach(abilities -> {
            abilities.forEach(ability -> {
                // Update ability internal states if needed
                // This could include telegraph timers, effect durations, etc.
            });
        });
    }
    
    // ==================== DEBUG AND ADMIN METHODS ====================
    
    /**
     * Get total number of registered abilities
     */
    public int getTotalAbilityCount() {
        return abilityRegistry.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * Get debug information about the ability system
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("§e=== Elite Ability Manager Debug ===\n");
        info.append("§fRegistered Abilities: ").append(getTotalAbilityCount()).append("\n");
        info.append("§fActive Mobs: ").append(mobAbilities.size()).append("\n");
        info.append("§fCombat Contexts: ").append(combatContexts.size()).append("\n");
        
        for (Map.Entry<EliteBehaviorArchetype, List<EliteAbility>> entry : abilityRegistry.entrySet()) {
            info.append("§6").append(entry.getKey().getDisplayName()).append("§f: ");
            info.append(entry.getValue().size()).append(" abilities\n");
        }
        
        return info.toString();
    }
    
    /**
     * Force trigger an ability for testing (admin command)
     */
    public boolean forceAbility(CustomMob mob, String abilityId, List<Player> targets) {
        UUID mobId = mob.getEntity().getUniqueId();
        List<EliteAbility> abilities = mobAbilities.get(mobId);
        
        if (abilities == null) return false;
        
        EliteAbility ability = abilities.stream()
            .filter(a -> a.getAbilityId().equalsIgnoreCase(abilityId))
            .findFirst()
            .orElse(null);
            
        if (ability == null) return false;
        
        EliteBehaviorArchetype archetype = getArchetypeForMob(mob);
        if (archetype == null) return false;
        
        CombatContext context = new CombatContext(mob.getEntity().getLocation(), targets, archetype);
        
        return ability.execute(mob, targets, context);
    }
}
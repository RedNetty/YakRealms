package com.rednetty.server.core.mechanics.world.mobs.behaviors;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.modifiers.EliteBehaviorModifier;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.modifiers.EliteModifierManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import com.rednetty.server.core.mechanics.world.mobs.core.EliteMob;
import com.rednetty.server.core.mechanics.world.mobs.core.MobType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * Test validator for the enhanced Elite Behavior system.
 * Provides comprehensive testing and validation of all elite behavior enhancements,
 * including archetypes, modifiers, and tier-based variations.
 */
public class EliteBehaviorTestValidator {
    
    private final Logger logger;
    private final Map<String, TestResult> testResults = new HashMap<>();
    
    public EliteBehaviorTestValidator() {
        this.logger = YakRealms.getInstance().getLogger();
    }
    
    // ==================== PUBLIC TEST METHODS ====================
    
    /**
     * Runs comprehensive validation tests on the elite behavior system
     */
    public void runFullValidation(CommandSender sender) {
        sender.sendMessage("§6=== Elite Behavior System Validation ===");
        
        // Test archetype distribution
        testArchetypeDistribution(sender);
        
        // Test modifier assignment
        testModifierAssignment(sender);
        
        // Test tier progression
        testTierProgression(sender);
        
        // Test conflict resolution
        testConflictResolution(sender);
        
        // Test performance
        testPerformance(sender);
        
        // Display summary
        displayTestSummary(sender);
    }
    
    /**
     * Tests a specific tier's behavior variety
     */
    public void testTierVariety(CommandSender sender, int tier) {
        sender.sendMessage("§6=== Testing Tier " + tier + " Variety ===");
        
        // Get all available archetypes for this tier
        List<EliteBehaviorArchetype> archetypes = EliteBehaviorArchetype.getArchetypesForTier(tier);
        List<EliteBehaviorModifier> modifiers = EliteBehaviorModifier.getModifiersForTier(tier);
        
        sender.sendMessage("§7Available Archetypes for Tier " + tier + ": §a" + archetypes.size());
        for (EliteBehaviorArchetype archetype : archetypes) {
            sender.sendMessage("  §f- " + archetype.getDisplayColor() + archetype.getDisplayName() + 
                              "§7 (Rarity: " + (archetype.getRarity() * 100) + "%)");
        }
        
        sender.sendMessage("§7Available Modifiers for Tier " + tier + ": §a" + modifiers.size());
        for (EliteBehaviorModifier modifier : modifiers) {
            sender.sendMessage("  §f- " + modifier.getDisplayColor() + modifier.getDisplayName() + 
                              "§7 (" + modifier.getRarity().getDisplayName() + ")");
        }
        
        // Calculate total possible combinations
        int totalCombinations = calculateCombinations(archetypes.size(), modifiers.size());
        sender.sendMessage("§7Total Possible Combinations: §e" + totalCombinations);
        
        if (totalCombinations >= 50) {
            sender.sendMessage("§aExcellent variety! This tier has " + totalCombinations + " possible combinations.");
        } else if (totalCombinations >= 20) {
            sender.sendMessage("§eGood variety. This tier has " + totalCombinations + " possible combinations.");
        } else {
            sender.sendMessage("§cLimited variety. Consider adding more archetypes or modifiers for this tier.");
        }
    }
    
    /**
     * Spawns test elites to demonstrate behavior variety
     */
    public void spawnTestElites(Player player, int tier, int count) {
        Location spawnLoc = player.getLocation();
        player.sendMessage("§6Spawning " + count + " test elites at tier " + tier + "...");
        
        for (int i = 0; i < count; i++) {
            try {
                // Create test elite (this would need integration with your mob spawning system)
                Location offset = spawnLoc.clone().add(i * 3, 0, 0);
                
                // For demonstration, we'll just show what would be assigned
                EliteBehaviorArchetype archetype = EliteBehaviorArchetype.getRandomForTier(tier);
                EliteBehaviorModifier modifier = EliteBehaviorModifier.getRandomForTier(tier);
                
                String displayName = archetype.getDisplayColor() + "[" + archetype.getDisplayName().toUpperCase() + "] " +
                                   modifier.getDisplayColor() + "[" + modifier.getDisplayName().toUpperCase() + "] " +
                                   "§fTest Elite #" + (i + 1);
                
                player.sendMessage("§7Would spawn: " + displayName);
                
            } catch (Exception e) {
                player.sendMessage("§cFailed to spawn test elite #" + (i + 1) + ": " + e.getMessage());
            }
        }
    }
    
    // ==================== PRIVATE TEST METHODS ====================
    
    private void testArchetypeDistribution(CommandSender sender) {
        sender.sendMessage("§7Testing archetype distribution...");
        TestResult result = new TestResult("Archetype Distribution");
        
        try {
            // Test each tier has appropriate archetypes
            for (int tier = 1; tier <= 6; tier++) {
                List<EliteBehaviorArchetype> available = EliteBehaviorArchetype.getArchetypesForTier(tier);
                
                if (available.isEmpty()) {
                    result.addFailure("Tier " + tier + " has no available archetypes");
                    continue;
                }
                
                // Check archetype power scaling
                for (EliteBehaviorArchetype archetype : available) {
                    double powerScaling = archetype.getPowerScaling(tier);
                    if (powerScaling < 1.0) {
                        result.addFailure("Archetype " + archetype.name() + " has invalid power scaling: " + powerScaling);
                    }
                }
                
                result.addSuccess("Tier " + tier + " has " + available.size() + " available archetypes");
            }
            
            // Test rarity distribution
            double totalRarity = Arrays.stream(EliteBehaviorArchetype.values())
                    .mapToDouble(EliteBehaviorArchetype::getRarity)
                    .sum();
            
            if (Math.abs(totalRarity - 1.0) > 0.1) {
                result.addWarning("Total archetype rarity sum is " + totalRarity + " (should be close to 1.0)");
            } else {
                result.addSuccess("Archetype rarity distribution is balanced");
            }
            
        } catch (Exception e) {
            result.addFailure("Exception during archetype distribution test: " + e.getMessage());
        }
        
        testResults.put("Archetype Distribution", result);
        displayTestResult(sender, result);
    }
    
    private void testModifierAssignment(CommandSender sender) {
        sender.sendMessage("§7Testing modifier assignment...");
        TestResult result = new TestResult("Modifier Assignment");
        
        try {
            // Test modifier availability per tier
            for (int tier = 1; tier <= 6; tier++) {
                List<EliteBehaviorModifier> available = EliteBehaviorModifier.getModifiersForTier(tier);
                
                if (available.isEmpty()) {
                    result.addFailure("Tier " + tier + " has no available modifiers");
                    continue;
                }
                
                // Check modifier power scaling
                for (EliteBehaviorModifier modifier : available) {
                    double powerMultiplier = modifier.getPowerMultiplier(tier);
                    if (powerMultiplier < 1.0) {
                        result.addFailure("Modifier " + modifier.name() + " has invalid power multiplier: " + powerMultiplier);
                    }
                }
                
                result.addSuccess("Tier " + tier + " has " + available.size() + " available modifiers");
            }
            
            // Test conflict resolution
            for (EliteBehaviorModifier modifier1 : EliteBehaviorModifier.values()) {
                for (EliteBehaviorModifier modifier2 : EliteBehaviorModifier.values()) {
                    if (modifier1 != modifier2 && modifier1.conflictsWith(modifier2)) {
                        // Verify the conflict is mutual
                        if (!modifier2.conflictsWith(modifier1)) {
                            result.addWarning("Conflict between " + modifier1.name() + " and " + 
                                             modifier2.name() + " is not mutual");
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            result.addFailure("Exception during modifier assignment test: " + e.getMessage());
        }
        
        testResults.put("Modifier Assignment", result);
        displayTestResult(sender, result);
    }
    
    private void testTierProgression(CommandSender sender) {
        sender.sendMessage("§7Testing tier progression...");
        TestResult result = new TestResult("Tier Progression");
        
        try {
            // Test that higher tiers have more variety
            for (int tier = 1; tier < 6; tier++) {
                int currentTierArchetypes = EliteBehaviorArchetype.getArchetypesForTier(tier).size();
                int nextTierArchetypes = EliteBehaviorArchetype.getArchetypesForTier(tier + 1).size();
                
                if (nextTierArchetypes < currentTierArchetypes) {
                    result.addWarning("Tier " + (tier + 1) + " has fewer archetypes than tier " + tier);
                }
                
                int currentTierModifiers = EliteBehaviorModifier.getModifiersForTier(tier).size();
                int nextTierModifiers = EliteBehaviorModifier.getModifiersForTier(tier + 1).size();
                
                if (nextTierModifiers < currentTierModifiers) {
                    result.addWarning("Tier " + (tier + 1) + " has fewer modifiers than tier " + tier);
                }
            }
            
            // Test power scaling progression
            for (EliteBehaviorArchetype archetype : EliteBehaviorArchetype.values()) {
                for (int tier = archetype.getMinTier(); tier < Math.min(archetype.getMaxTier(), 6); tier++) {
                    double currentPower = archetype.getPowerScaling(tier);
                    double nextPower = archetype.getPowerScaling(tier + 1);
                    
                    if (nextPower <= currentPower) {
                        result.addWarning("Archetype " + archetype.name() + " power doesn't increase from tier " + 
                                         tier + " to " + (tier + 1));
                    }
                }
            }
            
            result.addSuccess("Tier progression validation completed");
            
        } catch (Exception e) {
            result.addFailure("Exception during tier progression test: " + e.getMessage());
        }
        
        testResults.put("Tier Progression", result);
        displayTestResult(sender, result);
    }
    
    private void testConflictResolution(CommandSender sender) {
        sender.sendMessage("§7Testing conflict resolution...");
        TestResult result = new TestResult("Conflict Resolution");
        
        try {
            // Test that conflicting modifiers are properly identified
            Map<EliteBehaviorModifier, Set<EliteBehaviorModifier>> conflicts = new HashMap<>();
            
            for (EliteBehaviorModifier modifier : EliteBehaviorModifier.values()) {
                Set<EliteBehaviorModifier> conflictSet = new HashSet<>();
                for (EliteBehaviorModifier other : EliteBehaviorModifier.values()) {
                    if (modifier != other && modifier.conflictsWith(other)) {
                        conflictSet.add(other);
                    }
                }
                if (!conflictSet.isEmpty()) {
                    conflicts.put(modifier, conflictSet);
                }
            }
            
            result.addSuccess("Found " + conflicts.size() + " modifiers with conflicts");
            
            // Verify logical conflicts make sense
            if (conflicts.containsKey(EliteBehaviorModifier.ENRAGED) && 
                !conflicts.get(EliteBehaviorModifier.ENRAGED).contains(EliteBehaviorModifier.PHANTOM)) {
                result.addWarning("ENRAGED should conflict with PHANTOM (aggressive vs evasive)");
            }
            
            if (conflicts.containsKey(EliteBehaviorModifier.VOIDTOUCHED) && 
                !conflicts.get(EliteBehaviorModifier.VOIDTOUCHED).contains(EliteBehaviorModifier.ASCENDANT)) {
                result.addWarning("VOIDTOUCHED should conflict with ASCENDANT (cosmic horror vs divine power)");
            }
            
        } catch (Exception e) {
            result.addFailure("Exception during conflict resolution test: " + e.getMessage());
        }
        
        testResults.put("Conflict Resolution", result);
        displayTestResult(sender, result);
    }
    
    private void testPerformance(CommandSender sender) {
        sender.sendMessage("§7Testing performance...");
        TestResult result = new TestResult("Performance");
        
        try {
            // Test archetype selection performance
            long startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                EliteBehaviorArchetype.getRandomForTier(3);
            }
            long archetypeTime = System.nanoTime() - startTime;
            
            // Test modifier selection performance
            startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                EliteBehaviorModifier.getRandomForTier(3);
            }
            long modifierTime = System.nanoTime() - startTime;
            
            double archetypeMs = archetypeTime / 1_000_000.0;
            double modifierMs = modifierTime / 1_000_000.0;
            
            if (archetypeMs > 100) {
                result.addWarning("Archetype selection is slow: " + String.format("%.2f", archetypeMs) + "ms for 1000 selections");
            } else {
                result.addSuccess("Archetype selection performance: " + String.format("%.2f", archetypeMs) + "ms for 1000 selections");
            }
            
            if (modifierMs > 100) {
                result.addWarning("Modifier selection is slow: " + String.format("%.2f", modifierMs) + "ms for 1000 selections");
            } else {
                result.addSuccess("Modifier selection performance: " + String.format("%.2f", modifierMs) + "ms for 1000 selections");
            }
            
        } catch (Exception e) {
            result.addFailure("Exception during performance test: " + e.getMessage());
        }
        
        testResults.put("Performance", result);
        displayTestResult(sender, result);
    }
    
    // ==================== HELPER METHODS ====================
    
    private int calculateCombinations(int archetypes, int modifiers) {
        // Each elite gets 1 archetype and 1-3 modifiers
        // Simplified calculation: archetype * (1-modifier + 2-modifier + 3-modifier combinations)
        int oneModifier = modifiers;
        int twoModifiers = modifiers * (modifiers - 1) / 2; // Combinations without repetition
        int threeModifiers = modifiers * (modifiers - 1) * (modifiers - 2) / 6; // Combinations without repetition
        
        return archetypes * (oneModifier + twoModifiers + threeModifiers);
    }
    
    private void displayTestResult(CommandSender sender, TestResult result) {
        if (result.failures.isEmpty() && result.warnings.isEmpty()) {
            sender.sendMessage("  §a✓ " + result.testName + " - All tests passed");
        } else {
            sender.sendMessage("  §e⚠ " + result.testName + " - " + 
                              result.failures.size() + " failures, " + result.warnings.size() + " warnings");
        }
        
        for (String failure : result.failures) {
            sender.sendMessage("    §c✗ " + failure);
        }
        
        for (String warning : result.warnings) {
            sender.sendMessage("    §e⚠ " + warning);
        }
        
        for (String success : result.successes) {
            sender.sendMessage("    §a✓ " + success);
        }
    }
    
    private void displayTestSummary(CommandSender sender) {
        sender.sendMessage("§6=== Test Summary ===");
        
        int totalTests = testResults.size();
        int passedTests = 0;
        int totalFailures = 0;
        int totalWarnings = 0;
        
        for (TestResult result : testResults.values()) {
            if (result.failures.isEmpty()) {
                passedTests++;
            }
            totalFailures += result.failures.size();
            totalWarnings += result.warnings.size();
        }
        
        sender.sendMessage("§7Total Tests: §f" + totalTests);
        sender.sendMessage("§7Passed: §a" + passedTests);
        sender.sendMessage("§7Failed: §c" + (totalTests - passedTests));
        sender.sendMessage("§7Total Failures: §c" + totalFailures);
        sender.sendMessage("§7Total Warnings: §e" + totalWarnings);
        
        if (totalFailures == 0) {
            sender.sendMessage("§a🎉 All tests passed! Elite behavior system is ready for use.");
        } else if (totalFailures <= 2) {
            sender.sendMessage("§e⚠ Minor issues found. System is mostly ready but consider addressing failures.");
        } else {
            sender.sendMessage("§c❌ Significant issues found. Please address failures before using the system.");
        }
    }
    
    // ==================== UTILITY CLASSES ====================
    
    private static class TestResult {
        final String testName;
        final List<String> successes = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        
        TestResult(String testName) {
            this.testName = testName;
        }
        
        void addSuccess(String message) {
            successes.add(message);
        }
        
        void addFailure(String message) {
            failures.add(message);
        }
        
        void addWarning(String message) {
            warnings.add(message);
        }
    }
    
    // ==================== DEMONSTRATION METHODS ====================
    
    /**
     * Demonstrates the variety available for a specific tier
     */
    public void demonstrateVariety(CommandSender sender, int tier) {
        sender.sendMessage("§6=== Elite Variety Demonstration - Tier " + tier + " ===");
        
        // Show 10 random combinations for this tier
        for (int i = 1; i <= 10; i++) {
            EliteBehaviorArchetype archetype = EliteBehaviorArchetype.getRandomForTier(tier);
            EliteBehaviorModifier modifier = EliteBehaviorModifier.getRandomForTier(tier);
            
            String displayName = archetype.getDisplayColor() + "[" + archetype.getDisplayName().toUpperCase() + "] " +
                               modifier.getDisplayColor() + "[" + modifier.getDisplayName().toUpperCase() + "] " +
                               "§7Elite #" + i;
            
            double combinedPower = archetype.getPowerScaling(tier) * modifier.getPowerMultiplier(tier);
            
            sender.sendMessage(displayName + " §7(Power: §e" + String.format("%.1f", combinedPower) + "x§7)");
        }
        
        sender.sendMessage("§7This demonstrates the variety possible with the enhanced behavior system!");
    }
    
    /**
     * Shows statistics about current behavior distribution
     */
    public void showStatistics(CommandSender sender) {
        sender.sendMessage("§6=== Elite Behavior Statistics ===");
        
        EliteArchetypeBehaviorManager archetypeManager = EliteArchetypeBehaviorManager.getInstance();
        EliteModifierManager modifierManager = EliteModifierManager.getInstance();
        
        sender.sendMessage("§7Total Elite Spawns: §f" + archetypeManager.getTotalEliteSpawns());
        sender.sendMessage("§7Modified Elites: §f" + modifierManager.getTotalModifiedElites());
        
        // Show archetype distribution
        sender.sendMessage("§7\n§eArchetype Distribution:");
        var archetypeStats = archetypeManager.getArchetypeSpawnStats();
        archetypeStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    double percentage = (entry.getValue() * 100.0) / archetypeManager.getTotalEliteSpawns();
                    sender.sendMessage("  §6" + entry.getKey() + 
                                     " §7: §f" + entry.getValue() + " (" + String.format("%.1f", percentage) + "%)");
                });
        
        // Show modifier distribution  
        sender.sendMessage("§7\n§eModifier Distribution:");
        var modifierStats = modifierManager.getModifierSpawnStats();
        modifierStats.entrySet().stream()
                .sorted(Map.Entry.<EliteBehaviorModifier, Integer>comparingByValue().reversed())
                .limit(10) // Show top 10
                .forEach(entry -> {
                    sender.sendMessage("  " + entry.getKey().getDisplayColor() + entry.getKey().getDisplayName() + 
                                     "§7: " + entry.getValue() + " spawns");
                });
    }
}
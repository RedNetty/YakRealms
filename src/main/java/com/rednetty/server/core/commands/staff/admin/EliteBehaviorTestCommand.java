package com.rednetty.server.core.commands.staff.admin;

import com.rednetty.server.core.mechanics.world.mobs.behaviors.EliteArchetypeBehaviorManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.EliteBehaviorTestValidator;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.MobBehaviorManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.modifiers.EliteBehaviorModifier;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.modifiers.EliteModifierManager;
import com.rednetty.server.core.mechanics.world.mobs.behaviors.types.EliteBehaviorArchetype;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Admin command for testing and demonstrating the enhanced elite behavior system.
 * Provides comprehensive tools for validating behavior implementations and showcasing variety.
 */
public class EliteBehaviorTestCommand implements CommandExecutor, TabCompleter {
    
    private final EliteBehaviorTestValidator validator = new EliteBehaviorTestValidator();
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "validate":
                handleValidate(sender, args);
                break;
                
            case "test":
                handleTest(sender, args);
                break;
                
            case "spawn":
                handleSpawn(sender, args);
                break;
                
            case "stats":
                handleStats(sender, args);
                break;
                
            case "demo":
                handleDemo(sender, args);
                break;
                
            case "info":
                handleInfo(sender, args);
                break;
                
            case "reload":
                handleReload(sender, args);
                break;
                
            default:
                sender.sendMessage("§cUnknown subcommand: " + subCommand);
                showUsage(sender);
                break;
        }
        
        return true;
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage("§6=== Elite Behavior Test Commands ===");
        sender.sendMessage("§e/elitetest validate §7- Run full system validation");
        sender.sendMessage("§e/elitetest test <tier> §7- Test variety for specific tier");
        sender.sendMessage("§e/elitetest spawn <tier> [count] §7- Spawn test elites");
        sender.sendMessage("§e/elitetest stats §7- Show behavior statistics");
        sender.sendMessage("§e/elitetest demo <tier> §7- Demonstrate tier variety");
        sender.sendMessage("§e/elitetest info <archetype|modifier> <name> §7- Get detailed info");
        sender.sendMessage("§e/elitetest reload §7- Reload behavior system");
    }
    
    private void handleValidate(CommandSender sender, String[] args) {
        sender.sendMessage("§6Running comprehensive elite behavior validation...");
        validator.runFullValidation(sender);
    }
    
    private void handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /elitetest test <tier>");
            return;
        }
        
        try {
            int tier = Integer.parseInt(args[1]);
            if (tier < 1 || tier > 6) {
                sender.sendMessage("§cTier must be between 1 and 6");
                return;
            }
            
            validator.testTierVariety(sender, tier);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid tier number: " + args[1]);
        }
    }
    
    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /elitetest spawn <tier> [count]");
            return;
        }
        
        try {
            int tier = Integer.parseInt(args[1]);
            int count = args.length >= 3 ? Integer.parseInt(args[2]) : 5;
            
            if (tier < 1 || tier > 6) {
                sender.sendMessage("§cTier must be between 1 and 6");
                return;
            }
            
            if (count < 1 || count > 20) {
                sender.sendMessage("§cCount must be between 1 and 20");
                return;
            }
            
            Player player = (Player) sender;
            validator.spawnTestElites(player, tier, count);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number format");
        }
    }
    
    private void handleStats(CommandSender sender, String[] args) {
        validator.showStatistics(sender);
    }
    
    private void handleDemo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /elitetest demo <tier>");
            return;
        }
        
        try {
            int tier = Integer.parseInt(args[1]);
            if (tier < 1 || tier > 6) {
                sender.sendMessage("§cTier must be between 1 and 6");
                return;
            }
            
            validator.demonstrateVariety(sender, tier);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid tier number: " + args[1]);
        }
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /elitetest info <archetype|modifier> <name>");
            return;
        }
        
        String type = args[1].toLowerCase();
        String name = args[2].toUpperCase();
        
        if (type.equals("archetype")) {
            showArchetypeInfo(sender, name);
        } else if (type.equals("modifier")) {
            showModifierInfo(sender, name);
        } else {
            sender.sendMessage("§cType must be 'archetype' or 'modifier'");
        }
    }
    
    private void handleReload(CommandSender sender, String[] args) {
        try {
            sender.sendMessage("§6Reloading elite behavior system...");
            
            // Reinitialize behavior managers
            MobBehaviorManager.getInstance().clearCaches();
            MobBehaviorManager.getInstance().initializeDefaultBehaviors();
            
            EliteArchetypeBehaviorManager.getInstance().clearAll();
            EliteModifierManager.getInstance().clearAll();
            
            sender.sendMessage("§aElite behavior system reloaded successfully!");
            
        } catch (Exception e) {
            sender.sendMessage("§cFailed to reload behavior system: " + e.getMessage());
        }
    }
    
    private void showArchetypeInfo(CommandSender sender, String name) {
        try {
            EliteBehaviorArchetype archetype = EliteBehaviorArchetype.valueOf(name);
            
            sender.sendMessage("§6=== " + archetype.getDisplayColor() + archetype.getDisplayName() + " §6Archetype ===");
            sender.sendMessage("§7Tier Range: §f" + archetype.getMinTier() + "-" + archetype.getMaxTier());
            sender.sendMessage("§7Spawn Chance: §f" + (archetype.getRarity() * 100) + "%");
            sender.sendMessage("§7Characteristics:");
            
            for (String characteristic : archetype.getCharacteristics()) {
                sender.sendMessage("  §7- " + characteristic);
            }
            
            sender.sendMessage("§7Abilities:");
            for (String ability : archetype.getAbilities()) {
                sender.sendMessage("  §7- " + ability.replace("_", " "));
            }
            
            // Show power scaling examples
            sender.sendMessage("§7Power Scaling:");
            for (int tier = archetype.getMinTier(); tier <= Math.min(archetype.getMaxTier(), 6); tier++) {
                double power = archetype.getPowerScaling(tier);
                sender.sendMessage("  §7Tier " + tier + ": §e" + String.format("%.1f", power) + "x");
            }
            
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cUnknown archetype: " + name);
            sender.sendMessage("§7Available archetypes: " + String.join(", ", 
                Arrays.stream(EliteBehaviorArchetype.values()).map(Enum::name).toArray(String[]::new)));
        }
    }
    
    private void showModifierInfo(CommandSender sender, String name) {
        try {
            EliteBehaviorModifier modifier = EliteBehaviorModifier.valueOf(name);
            
            sender.sendMessage("§6=== " + modifier.getDisplayColor() + modifier.getDisplayName() + " §6Modifier ===");
            sender.sendMessage("§7Tier Range: §f" + modifier.getMinTier() + "-" + modifier.getMaxTier());
            sender.sendMessage("§7Rarity: " + modifier.getRarity().getColor() + modifier.getRarity().getDisplayName());
            sender.sendMessage("§7Spawn Chance: §f" + (modifier.getSpawnChance() * 100) + "%");
            sender.sendMessage("§7Effects:");
            
            for (String effect : modifier.getEffects()) {
                sender.sendMessage("  §7- " + effect);
            }
            
            sender.sendMessage("§7Tags:");
            for (String tag : modifier.getModifierTags()) {
                sender.sendMessage("  §7- " + tag.replace("_", " "));
            }
            
            // Show power multiplier examples
            sender.sendMessage("§7Power Multipliers:");
            for (int tier = modifier.getMinTier(); tier <= Math.min(modifier.getMaxTier(), 6); tier++) {
                double multiplier = modifier.getPowerMultiplier(tier);
                sender.sendMessage("  §7Tier " + tier + ": §e" + String.format("%.1f", multiplier) + "x");
            }
            
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cUnknown modifier: " + name);
            sender.sendMessage("§7Available modifiers: " + String.join(", ", 
                Arrays.stream(EliteBehaviorModifier.values()).map(Enum::name).toArray(String[]::new)));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("validate", "test", "spawn", "stats", "demo", "info", "reload");
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "test":
                case "spawn":
                case "demo":
                    return IntStream.rangeClosed(1, 6).mapToObj(String::valueOf).toList();
                case "info":
                    return List.of("archetype", "modifier");
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("info")) {
            String type = args[1].toLowerCase();
            if (type.equals("archetype")) {
                return Arrays.stream(EliteBehaviorArchetype.values()).map(Enum::name).toList();
            } else if (type.equals("modifier")) {
                return Arrays.stream(EliteBehaviorModifier.values()).map(Enum::name).toList();
            }
        }
        
        return List.of();
    }
}
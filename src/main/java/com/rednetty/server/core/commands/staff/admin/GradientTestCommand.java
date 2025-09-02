package com.rednetty.server.core.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.utils.ui.GradientColors;
import com.rednetty.server.utils.ui.ItemDisplayFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Test command to preview the new gradient color system
 * Usage: /gradienttest [shimmer|pulse]
 */
public class GradientTestCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check permissions
        if (!player.hasPermission("yakrealms.admin") && !player.hasPermission("yakrealms.gradienttest")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        String subCommand = args.length > 0 ? args[0].toLowerCase() : "basic";
        
        switch (subCommand) {
            case "basic" -> showBasicGradients(player);
            case "shimmer" -> showShimmerEffects(player);
            case "pulse" -> showPulseEffects(player);
            case "items" -> showItemFormatting(player);
            case "all" -> {
                showBasicGradients(player);
                player.sendMessage("");
                showShimmerEffects(player);
                player.sendMessage("");
                showPulseEffects(player);
                player.sendMessage("");
                showItemFormatting(player);
            }
            default -> {
                player.sendMessage("§e§lGradient Test Command");
                player.sendMessage("§7Usage: /gradienttest [basic|shimmer|pulse|items|all]");
                player.sendMessage("§7- §ebasic§7: Show basic gradient colors");
                player.sendMessage("§7- §eshimmer§7: Show shimmer effects");
                player.sendMessage("§7- §epulse§7: Show pulse effects");
                player.sendMessage("§7- §eitems§7: Show item formatting examples");
                player.sendMessage("§7- §eall§7: Show everything");
                return true;
            }
        }
        
        return true;
    }
    
    private void showBasicGradients(Player player) {
        player.sendMessage("§e§l=== Basic Gradient Colors ===");
        player.sendMessage("");
        
        // T6 Gradients
        player.sendMessage("§6T6 Standard: " + GradientColors.getT6Gradient("LEGENDARY WEAPON"));
        player.sendMessage("§6T6 Alternative: " + GradientColors.getT6GradientAlt("LEGENDARY WEAPON"));
        player.sendMessage("");
        
        // Unique Gradients
        player.sendMessage("§eUnique Standard: " + GradientColors.getUniqueGradient("MYTHICAL ARTIFACT"));
        player.sendMessage("§eUnique Alternative: " + GradientColors.getUniqueGradientAlt("MYTHICAL ARTIFACT"));
        player.sendMessage("");
        
        // Comparison with regular colors
        player.sendMessage("§7Regular T6: §6LEGENDARY WEAPON");
        player.sendMessage("§7Gradient T6: " + GradientColors.getT6Gradient("LEGENDARY WEAPON"));
        player.sendMessage("");
        player.sendMessage("§7Regular Unique: §eUNIQUE ITEM");
        player.sendMessage("§7Gradient Unique: " + GradientColors.getUniqueGradient("UNIQUE ITEM"));
    }
    
    private void showShimmerEffects(Player player) {
        player.sendMessage("§e§l=== Shimmer Effects ===");
        player.sendMessage("§7(These would alternate in real-time)");
        player.sendMessage("");
        
        // Show both phases of shimmer
        String testText = "SHIMMERING BLADE";
        player.sendMessage("§6T6 Shimmer Phase 1: " + GradientColors.getT6Shimmer(testText, false));
        player.sendMessage("§6T6 Shimmer Phase 2: " + GradientColors.getT6Shimmer(testText, true));
        player.sendMessage("");
        
        String uniqueText = "PRISMATIC GEM";
        player.sendMessage("§eUnique Shimmer Phase 1: " + GradientColors.getUniqueShimmer(uniqueText, false));
        player.sendMessage("§eUnique Shimmer Phase 2: " + GradientColors.getUniqueShimmer(uniqueText, true));
        
        // Start a real-time shimmer demo
        startShimmerDemo(player, testText, uniqueText);
    }
    
    private void showPulseEffects(Player player) {
        player.sendMessage("§e§l=== Pulse Effects ===");
        player.sendMessage("§7(These would pulse in real-time)");
        player.sendMessage("");
        
        String testText = "PULSING CORE";
        
        // Show different pulse intensities
        player.sendMessage("§6T6 Pulse 50%: " + GradientColors.getT6Pulse(testText, 0.5));
        player.sendMessage("§6T6 Pulse 100%: " + GradientColors.getT6Pulse(testText, 1.0));
        player.sendMessage("§6T6 Pulse 150%: " + GradientColors.getT6Pulse(testText, 1.5));
        player.sendMessage("");
        
        String uniqueText = "RESONANT CRYSTAL";
        player.sendMessage("§eUnique Pulse 50%: " + GradientColors.getUniquePulse(uniqueText, 0.5));
        player.sendMessage("§eUnique Pulse 100%: " + GradientColors.getUniquePulse(uniqueText, 1.0));
        player.sendMessage("§eUnique Pulse 150%: " + GradientColors.getUniquePulse(uniqueText, 1.5));
        
        // Start a real-time pulse demo
        startPulseDemo(player, testText, uniqueText);
    }
    
    private void showItemFormatting(Player player) {
        player.sendMessage("§e§l=== Item Formatting Examples ===");
        player.sendMessage("");
        
        // Show the complete preview
        for (String line : ItemDisplayFormatter.generateFormattingPreview()) {
            player.sendMessage(line);
        }
    }
    
    private void startShimmerDemo(Player player, String t6Text, String uniqueText) {
        player.sendMessage("");
        player.sendMessage("§e§lReal-time Shimmer Demo (5 seconds):");
        
        org.bukkit.scheduler.BukkitRunnable shimmerTask = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 100; // 5 seconds
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    player.sendMessage("§7Shimmer demo complete!");
                    cancel();
                    return;
                }
                
                boolean phase = (ticks / 10) % 2 == 0; // Switch every 0.5 seconds
                
                // Clear previous lines and show new shimmer
                player.sendMessage("§6Live T6: " + GradientColors.getT6Shimmer(t6Text, phase));
                player.sendMessage("§eLive Unique: " + GradientColors.getUniqueShimmer(uniqueText, phase));
                
                ticks++;
            }
        };
        
        shimmerTask.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
    
    private void startPulseDemo(Player player, String t6Text, String uniqueText) {
        player.sendMessage("");
        player.sendMessage("§e§lReal-time Pulse Demo (5 seconds):");
        
        org.bukkit.scheduler.BukkitRunnable pulseTask = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 100; // 5 seconds
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    player.sendMessage("§7Pulse demo complete!");
                    cancel();
                    return;
                }
                
                // Create smooth pulsing effect
                double pulsePhase = (ticks % 60) / 60.0 * 2 * Math.PI;
                double intensity = 0.8 + 0.4 * Math.sin(pulsePhase);
                
                player.sendMessage("§6Live T6: " + GradientColors.getT6Pulse(t6Text, intensity));
                player.sendMessage("§eLive Unique: " + GradientColors.getUniquePulse(uniqueText, intensity));
                
                ticks++;
            }
        };
        
        pulseTask.runTaskTimer(YakRealms.getInstance(), 0L, 1L);
    }
}
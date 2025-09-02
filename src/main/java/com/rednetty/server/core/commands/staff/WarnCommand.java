package com.rednetty.server.core.commands.staff;

import com.rednetty.server.core.mechanics.player.moderation.*;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.messaging.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced warning command with automatic escalation, severity selection,
 * and comprehensive tracking integration.
 */
public class WarnCommand implements CommandExecutor, TabCompleter {
    
    private final ModerationActionProcessor actionProcessor;
    private final PunishmentEscalationSystem escalationSystem;
    
    public WarnCommand() {
        this.actionProcessor = ModerationActionProcessor.getInstance();
        this.escalationSystem = PunishmentEscalationSystem.getInstance();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.warn")) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>You don't have permission to use this command.");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>You don't have permission to use this command.").toString());
            }
            return true;
        }
        
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }
        
        // Parse arguments
        String targetName = args[0];
        StringBuilder reasonBuilder = new StringBuilder();
        ModerationHistory.PunishmentSeverity severity = ModerationHistory.PunishmentSeverity.MEDIUM;
        boolean skipEscalation = false;
        
        // Parse flags and reason
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.startsWith("-severity:") && arg.length() > 10) {
                try {
                    String severityStr = arg.substring(10).toUpperCase();
                    severity = ModerationHistory.PunishmentSeverity.valueOf(severityStr);
                } catch (IllegalArgumentException e) {
                    if (sender instanceof Player) {
                        MessageUtils.send((Player) sender, "<red>Invalid severity: " + arg.substring(10));
                        MessageUtils.send((Player) sender, "<gray>Valid severities: LOW, MEDIUM, HIGH, SEVERE, CRITICAL");
                    } else {
                        sender.sendMessage(MessageUtils.parse("<red>Invalid severity: " + arg.substring(10)).toString());
                        sender.sendMessage(MessageUtils.parse("<gray>Valid severities: LOW, MEDIUM, HIGH, SEVERE, CRITICAL").toString());
                    }
                    return true;
                }
            } else if (arg.equals("-noescalation") && sender.hasPermission("yakrealms.staff.override")) {
                skipEscalation = true;
            } else {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(arg);
            }
        }
        
        String reason = reasonBuilder.toString().trim();
        if (reason.isEmpty()) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<red>You must provide a reason for the warning.");
            } else {
                sender.sendMessage(MessageUtils.parse("<red>You must provide a reason for the warning.").toString());
            }
            return true;
        }
        
        // Find target player
        Player target = Bukkit.getPlayer(targetName);
        UUID targetId = null;
        
        if (target != null) {
            targetId = target.getUniqueId();
        } else {
            // Try to find offline player
            var yakPlayer = YakPlayerManager.getInstance().getPlayer(targetName);
            if (yakPlayer != null) {
                targetId = yakPlayer.getUUID();
            } else {
                if (sender instanceof Player) {
                    MessageUtils.send((Player) sender, "<red>Player not found: " + targetName);
                } else {
                    sender.sendMessage(MessageUtils.parse("<red>Player not found: " + targetName).toString());
                }
                return true;
            }
        }
        
        // Check escalation bypass
        if (skipEscalation && escalationSystem.shouldBypassEscalation(targetId, 
                sender instanceof Player ? ((Player) sender).getUniqueId() : null)) {
            if (sender instanceof Player) {
                MessageUtils.send((Player) sender, "<yellow>Escalation will be bypassed for this warning.");
            } else {
                sender.sendMessage(MessageUtils.parse("<yellow>Escalation will be bypassed for this warning.").toString());
            }
        }
        
        // Show escalation preview
        if (!skipEscalation) {
            showEscalationPreview(sender, targetId, reason, severity);
        }
        
        final UUID finalTargetId = targetId;
        
        // Process the warning
        if (sender instanceof Player) {
            MessageUtils.send((Player) sender, "<yellow>Processing warning for " + targetName + "...");
        } else {
            sender.sendMessage(MessageUtils.parse("<yellow>Processing warning for " + targetName + "...").toString());
        }
        
        CompletableFuture<ModerationActionProcessor.ModerationResult> future;
        if (skipEscalation) {
            // Direct warning without escalation
            future = actionProcessor.issueWarning(finalTargetId, sender, reason, severity);
        } else {
            // Let escalation system handle it
            PunishmentEscalationSystem.EscalationResult escalation = 
                escalationSystem.calculateEscalation(finalTargetId, reason, severity);
            
            if (escalation.isValid()) {
                switch (escalation.getRecommendedAction()) {
                    case WARNING:
                        future = actionProcessor.issueWarning(finalTargetId, sender, reason, 
                                escalation.getRecommendedSeverity());
                        break;
                    case MUTE:
                        future = actionProcessor.issueMute(finalTargetId, sender, reason, 
                                escalation.getRecommendedDuration(), escalation.getRecommendedSeverity());
                        break;
                    case TEMP_BAN:
                        future = actionProcessor.issueTempBan(finalTargetId, sender, reason,
                                escalation.getRecommendedDuration(), escalation.getRecommendedSeverity());
                        break;
                    default:
                        future = actionProcessor.issueWarning(finalTargetId, sender, reason, severity);
                        break;
                }
            } else {
                future = actionProcessor.issueWarning(finalTargetId, sender, reason, severity);
            }
        }
        
        // Handle result
        future.thenAccept(result -> {
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("YakRealms"), () -> {
                if (result.isSuccess()) {
                    ModerationHistory entry = result.getEntry();
                    
                    if (sender instanceof Player) {
                        MessageUtils.send((Player) sender, "<green>Successfully issued " + 
                                entry.getAction().name().toLowerCase().replace("_", " ") + 
                                " to " + targetName);
                        
                        if (result.getMessage() != null) {
                            MessageUtils.send((Player) sender, "<yellow>" + result.getMessage());
                        }
                        
                        // Show escalation information
                        if (entry.isEscalation()) {
                            MessageUtils.send((Player) sender, "<gold>⚠ This punishment was automatically escalated");
                        }
                        
                        // Show statistics
                        PunishmentEscalationSystem.EscalationStatistics stats = 
                            escalationSystem.getPlayerEscalationStats(finalTargetId);
                        MessageUtils.send((Player) sender, "<gray>" + String.format(
                            "Player stats: %d total violations, %d escalations (%.1f%% rate)",
                            stats.getTotalViolations(), stats.getTotalEscalations(), 
                            stats.getEscalationRate() * 100
                        ));
                    } else {
                        sender.sendMessage(MessageUtils.parse("<green>Successfully issued " + 
                                entry.getAction().name().toLowerCase().replace("_", " ") + 
                                " to " + targetName).toString());
                        
                        if (result.getMessage() != null) {
                            sender.sendMessage(MessageUtils.parse("<yellow>" + result.getMessage()).toString());
                        }
                        
                        // Show escalation information
                        if (entry.isEscalation()) {
                            sender.sendMessage(MessageUtils.parse("<gold>⚠ This punishment was automatically escalated").toString());
                        }
                        
                        // Show statistics
                        PunishmentEscalationSystem.EscalationStatistics stats = 
                            escalationSystem.getPlayerEscalationStats(finalTargetId);
                        sender.sendMessage(MessageUtils.parse("<gray>" + String.format(
                            "Player stats: %d total violations, %d escalations (%.1f%% rate)",
                            stats.getTotalViolations(), stats.getTotalEscalations(), 
                            stats.getEscalationRate() * 100
                        )).toString());
                    }
                    
                } else {
                    if (sender instanceof Player) {
                        MessageUtils.send((Player) sender, "<red>Failed to issue warning: " + result.getMessage());
                    } else {
                        sender.sendMessage(MessageUtils.parse("<red>Failed to issue warning: " + result.getMessage()).toString());
                    }
                }
            });
        });
        
        return true;
    }
    
    private void sendUsage(CommandSender sender) {
        sendMessage(sender, "<gold>=== Warn Command Usage ===");
        sendMessage(sender, "<yellow>/warn <player> <reason> [flags]");
        sendMessage(sender, "<gray>");
        sendMessage(sender, "<white>Flags:");
        sendMessage(sender, "<gray>  -severity:<level>  Set severity (LOW, MEDIUM, HIGH, SEVERE, CRITICAL)");
        
        if (sender.hasPermission("yakrealms.staff.override")) {
            sendMessage(sender, "<gray>  -noescalation     Skip automatic escalation");
        }
        
        sendMessage(sender, "<gray>");
        sendMessage(sender, "<white>Examples:");
        sendMessage(sender, "<gray>  /warn Player123 Spamming in chat");
        sendMessage(sender, "<gray>  /warn Player123 Serious harassment -severity:HIGH");
        sendMessage(sender, "<gray>  /warn Player123 Minor issue -severity:LOW -noescalation");
    }
    
    private void showEscalationPreview(CommandSender sender, UUID targetId, String reason, 
                                      ModerationHistory.PunishmentSeverity severity) {
        PunishmentEscalationSystem.EscalationResult escalation = 
            escalationSystem.calculateEscalation(targetId, reason, severity);
        
        if (escalation.isValid() && escalation.wasEscalated()) {
            sendMessage(sender, "<gold>⚠ Escalation Preview:");
            sendMessage(sender, "<yellow>  Recommended action: " + 
                    escalation.getRecommendedAction().name().replace("_", " "));
            
            if (escalation.getRecommendedDuration() > 0) {
                sendMessage(sender, "<yellow>  Duration: " + 
                        formatDuration(escalation.getRecommendedDuration()));
            }
            
            sendMessage(sender, "<yellow>  Severity: " + 
                    escalation.getRecommendedSeverity().name());
            sendMessage(sender, "<gray>  Reason: " + escalation.getEscalationReason());
        }
    }
    
    private String formatDuration(long seconds) {
        if (seconds == 0) return "Permanent";
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        
        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m");
        
        return result.toString().trim();
    }
    
    /**
     * Helper method to send messages to both Player and CommandSender
     */
    private void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player) {
            MessageUtils.send((Player) sender, message);
        } else {
            sender.sendMessage(MessageUtils.toLegacy(MessageUtils.parse(message)));
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.staff.warn")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            // Suggest online players
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length > 1) {
            String lastArg = args[args.length - 1];
            List<String> suggestions = new ArrayList<>();
            
            // Severity flag suggestions
            if (lastArg.startsWith("-severity:")) {
                String prefix = "-severity:";
                suggestions.addAll(Arrays.stream(ModerationHistory.PunishmentSeverity.values())
                        .map(s -> prefix + s.name())
                        .filter(s -> s.toLowerCase().startsWith(lastArg.toLowerCase()))
                        .collect(Collectors.toList()));
            } else if (lastArg.startsWith("-")) {
                suggestions.add("-severity:MEDIUM");
                suggestions.add("-severity:HIGH");
                suggestions.add("-severity:SEVERE");
                
                if (sender.hasPermission("yakrealms.staff.override")) {
                    suggestions.add("-noescalation");
                }
                
                suggestions = suggestions.stream()
                        .filter(s -> s.toLowerCase().startsWith(lastArg.toLowerCase()))
                        .collect(Collectors.toList());
            } else {
                // Common warning reasons
                if (args.length == 2) {
                    suggestions.addAll(Arrays.asList(
                        "Spamming", "Inappropriate_language", "Harassment", 
                        "Griefing", "Rule_violation", "Disrespectful_behavior"
                    ));
                    
                    suggestions = suggestions.stream()
                            .filter(s -> s.toLowerCase().startsWith(lastArg.toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
            
            return suggestions;
        }
        
        return new ArrayList<>();
    }
}
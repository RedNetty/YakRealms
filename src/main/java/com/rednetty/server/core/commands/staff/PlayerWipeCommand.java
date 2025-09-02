package com.rednetty.server.core.commands.staff;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Administrative command for wiping player data with safety confirmations.
 * Supports selective wiping (inventory, economy, location, stats) while preserving
 * persistent data like ranks, moderation history, and achievements.
 */
public class PlayerWipeCommand implements CommandExecutor, TabCompleter {
    
    private static final long CONFIRMATION_TIMEOUT = 30000; // 30 seconds
    
    private final YakRealms plugin;
    private final YakPlayerManager playerManager;
    private final Map<UUID, WipeRequest> pendingWipes = new ConcurrentHashMap<>();
    
    public PlayerWipeCommand() {
        this.plugin = YakRealms.getInstance();
        this.playerManager = YakPlayerManager.getInstance();
        
        // Start cleanup task for expired confirmations
        startCleanupTask();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (!sender.hasPermission("yakrealms.admin.wipe")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "player":
                return handlePlayerWipe(sender, args);
            case "confirm":
                return handleConfirm(sender);
            case "cancel":
                return handleCancel(sender);
            case "info":
                return handleInfo(sender, args);
            case "help":
                sendUsage(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendUsage(sender);
                return true;
        }
    }
    
    private boolean handlePlayerWipe(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /wipe player <player> [type]");
            return true;
        }
        
        String targetName = args[1];
        String wipeType = args.length >= 3 ? args[2] : "FULL";
        
        // Find target player
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or offline: " + targetName);
            return true;
        }
        
        YakPlayer yakPlayer = playerManager.getPlayer(targetPlayer);
        if (yakPlayer == null) {
            sender.sendMessage(ChatColor.RED + "YakPlayer data not found for: " + targetName);
            return true;
        }
        
        // Validate wipe type
        WipeType type;
        try {
            type = WipeType.valueOf(wipeType.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid wipe type: " + wipeType);
            sender.sendMessage(ChatColor.YELLOW + "Valid types: full, inventory, economy, location, stats");
            return true;
        }
        
        // Create wipe request and require confirmation
        UUID staffId = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        WipeRequest request = new WipeRequest(staffId, targetPlayer.getUniqueId(), 
                                            targetName, type, System.currentTimeMillis());
        
        if (staffId != null) {
            pendingWipes.put(staffId, request);
        }
        
        // Send confirmation message
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "PLAYER WIPE CONFIRMATION");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "Target: " + ChatColor.WHITE + targetName);
        sender.sendMessage(ChatColor.YELLOW + "Wipe Type: " + ChatColor.WHITE + type.getDisplayName());
        sender.sendMessage(ChatColor.YELLOW + "Staff: " + ChatColor.WHITE + sender.getName());
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "This action will permanently delete:");
        
        for (String item : type.getWipedItems()) {
            sender.sendMessage(ChatColor.GRAY + "  - " + item);
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "This action will preserve:");
        for (String item : getPreservedItems()) {
            sender.sendMessage(ChatColor.GRAY + "  - " + item);
        }
        
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "WARNING: This action cannot be undone!");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/wipe confirm" + 
                          ChatColor.YELLOW + " within 30 seconds to proceed.");
        sender.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/wipe cancel" + 
                          ChatColor.YELLOW + " to cancel.");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        
        return true;
    }
    
    private boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Console cannot confirm wipes.");
            return true;
        }
        
        Player staff = (Player) sender;
        WipeRequest request = pendingWipes.remove(staff.getUniqueId());
        
        if (request == null) {
            sender.sendMessage(ChatColor.RED + "No pending wipe request found.");
            return true;
        }
        
        if (System.currentTimeMillis() - request.getTimestamp() > CONFIRMATION_TIMEOUT) {
            sender.sendMessage(ChatColor.RED + "Wipe confirmation expired. Please restart the process.");
            return true;
        }
        
        // Execute the wipe
        return executeWipe(sender, request);
    }
    
    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Console cannot cancel wipes.");
            return true;
        }
        
        Player staff = (Player) sender;
        WipeRequest request = pendingWipes.remove(staff.getUniqueId());
        
        if (request == null) {
            sender.sendMessage(ChatColor.RED + "No pending wipe request found.");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Wipe request for " + request.getTargetName() + " has been cancelled.");
        return true;
    }
    
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /wipe info <player>");
            return true;
        }
        
        String targetName = args[1];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }
        
        YakPlayer yakPlayer = playerManager.getPlayer(targetPlayer);
        if (yakPlayer == null) {
            sender.sendMessage(ChatColor.RED + "YakPlayer data not found for: " + targetName);
            return true;
        }
        
        // Display player information
        sender.sendMessage(ChatColor.GOLD + "======== Player Information ========");
        sender.sendMessage(ChatColor.YELLOW + "Name: " + ChatColor.WHITE + targetName);
        sender.sendMessage(ChatColor.YELLOW + "Online: " + (targetPlayer.isOnline() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        sender.sendMessage(ChatColor.YELLOW + "Level: " + ChatColor.WHITE + yakPlayer.getLevel());
        sender.sendMessage(ChatColor.YELLOW + "Bank Gems: " + ChatColor.WHITE + yakPlayer.getBankGems());
        sender.sendMessage(ChatColor.YELLOW + "Playtime: " + ChatColor.WHITE + (yakPlayer.getTotalPlaytime() / 3600) + "h");
        sender.sendMessage(ChatColor.YELLOW + "Location: " + ChatColor.WHITE + formatLocation(targetPlayer.getLocation()));
        sender.sendMessage(ChatColor.YELLOW + "Rank: " + ChatColor.WHITE + yakPlayer.getRank());
        sender.sendMessage(ChatColor.GOLD + "===================================");
        
        return true;
    }
    
    private boolean executeWipe(CommandSender sender, WipeRequest request) {
        Player targetPlayer = Bukkit.getPlayer(request.getTargetId());
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Target player is no longer online.");
            return true;
        }
        
        YakPlayer yakPlayer = playerManager.getPlayer(targetPlayer);
        if (yakPlayer == null) {
            sender.sendMessage(ChatColor.RED + "YakPlayer data not found.");
            return true;
        }
        
        sender.sendMessage(ChatColor.YELLOW + "Executing wipe for " + request.getTargetName() + "...");
        
        try {
            switch (request.getWipeType()) {
                case FULL:
                    wipeInventory(targetPlayer);
                    wipeEconomy(yakPlayer);
                    wipeLocation(targetPlayer);
                    wipeStats(targetPlayer, yakPlayer);
                    break;
                case INVENTORY:
                    wipeInventory(targetPlayer);
                    break;
                case ECONOMY:
                    wipeEconomy(yakPlayer);
                    break;
                case LOCATION:
                    wipeLocation(targetPlayer);
                    break;
                case STATS:
                    wipeStats(targetPlayer, yakPlayer);
                    break;
            }
            
            // Save the player data
            playerManager.savePlayer(yakPlayer);
            
            // Log the action
            String logMessage = String.format("[PLAYER WIPE] %s wiped %s (Type: %s)",
                    sender.getName(), request.getTargetName(), request.getWipeType().getDisplayName());
            plugin.getLogger().info(logMessage);
            
            // Notify staff
            notifyStaff(sender, targetPlayer, request.getWipeType());
            
            // Notify target player
            targetPlayer.sendMessage("");
            targetPlayer.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "Your player data has been wiped!");
            targetPlayer.sendMessage(ChatColor.YELLOW + "Wipe type: " + ChatColor.WHITE + request.getWipeType().getDisplayName());
            targetPlayer.sendMessage(ChatColor.YELLOW + "Staff member: " + ChatColor.WHITE + sender.getName());
            targetPlayer.sendMessage(ChatColor.GRAY + "Your rank, achievements, and moderation history have been preserved.");
            targetPlayer.sendMessage("");
            
            sender.sendMessage(ChatColor.GREEN + "Successfully wiped " + request.getTargetName() + 
                              " (" + request.getWipeType().getDisplayName() + ")");
            
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error executing wipe: " + e.getMessage());
            plugin.getLogger().severe("Error executing player wipe: " + e.getMessage());
            return false;
        }
        
        return true;
    }
    
    private void wipeInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setHelmet(null);
        inventory.setChestplate(null);
        inventory.setLeggings(null);
        inventory.setBoots(null);
        
        // Clear enderchest
        player.getEnderChest().clear();
        
        // Reset held item slot
        player.getInventory().setHeldItemSlot(0);
    }
    
    private void wipeEconomy(YakPlayer yakPlayer) {
        yakPlayer.setBankGems(0);
        // Reset any other economy-related data here
    }
    
    private void wipeLocation(Player player) {
        // Teleport to spawn location
        Location spawn = player.getWorld().getSpawnLocation();
        player.teleport(spawn);
    }
    
    private void wipeStats(Player player, YakPlayer yakPlayer) {
        // Reset experience
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        
        // Reset health and hunger
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        
        // Clear potion effects
        player.getActivePotionEffects().forEach(effect -> 
            player.removePotionEffect(effect.getType()));
        
        // Reset YakPlayer level (but preserve other stats)
        yakPlayer.setLevel(1);
        // Note: We preserve playtime, join dates, etc.
    }
    
    private void notifyStaff(CommandSender sender, Player target, WipeType wipeType) {
        String message = ChatColor.RED + "[STAFF] " + ChatColor.YELLOW + sender.getName() +
                        ChatColor.WHITE + " wiped " + ChatColor.AQUA + target.getName() +
                        ChatColor.WHITE + " (" + wipeType.getDisplayName() + ")";
        
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("yakrealms.staff") && !staff.equals(sender)) {
                staff.sendMessage(message);
            }
        }
    }
    
    private String formatLocation(Location loc) {
        if (loc == null) return "Unknown";
        return String.format("%s: %d, %d, %d", 
                loc.getWorld().getName(), 
                (int) loc.getX(), 
                (int) loc.getY(), 
                (int) loc.getZ());
    }
    
    private List<String> getPreservedItems() {
        return Arrays.asList(
            "Rank and permissions",
            "Moderation history",
            "Join date and playtime statistics",
            "Friends/buddies list",
            "Achievement progress",
            "Player preferences/settings",
            "Guild membership (if applicable)"
        );
    }
    
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            pendingWipes.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().getTimestamp() > CONFIRMATION_TIMEOUT);
        }, 20L * 60, 20L * 60); // Run every minute
    }
    
    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========= Player Wipe Commands =========");
        sender.sendMessage(ChatColor.YELLOW + "/wipe player <player> [type]" + 
                          ChatColor.GRAY + " - Start wipe process");
        sender.sendMessage(ChatColor.YELLOW + "/wipe confirm" + 
                          ChatColor.GRAY + " - Confirm pending wipe");
        sender.sendMessage(ChatColor.YELLOW + "/wipe cancel" + 
                          ChatColor.GRAY + " - Cancel pending wipe");
        sender.sendMessage(ChatColor.YELLOW + "/wipe info <player>" + 
                          ChatColor.GRAY + " - View player information");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.WHITE + "Wipe Types:");
        sender.sendMessage(ChatColor.GRAY + "  full - Complete wipe (default)");
        sender.sendMessage(ChatColor.GRAY + "  inventory - Items and equipment only");
        sender.sendMessage(ChatColor.GRAY + "  economy - Bank balance and gems only");
        sender.sendMessage(ChatColor.GRAY + "  location - Teleport to spawn only");
        sender.sendMessage(ChatColor.GRAY + "  stats - Experience and health only");
        sender.sendMessage(ChatColor.GOLD + "======================================");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("yakrealms.admin.wipe")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("player", "confirm", "cancel", "info", "help")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 2 && ("player".equals(args[0].toLowerCase()) || "info".equals(args[0].toLowerCase()))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 3 && "player".equals(args[0].toLowerCase())) {
            return Arrays.stream(WipeType.values())
                    .map(type -> type.name().toLowerCase())
                    .filter(name -> name.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
    
    // ==========================================
    // DATA CLASSES
    // ==========================================
    
    public enum WipeType {
        FULL("Complete Wipe", 
             Arrays.asList("All inventory items", "All bank gems/coins", "Experience and levels", 
                          "Location (teleport to spawn)", "Health and hunger", "Potion effects")),
        INVENTORY("Inventory Only", 
                  Arrays.asList("All inventory items", "Armor and equipment", "Enderchest contents")),
        ECONOMY("Economy Only", 
                Arrays.asList("Bank gems/coins", "Economic transactions history")),
        LOCATION("Location Only", 
                 Arrays.asList("Current location (teleport to spawn)")),
        STATS("Stats Only", 
              Arrays.asList("Experience and levels", "Health and hunger", "Potion effects"));
        
        private final String displayName;
        private final List<String> wipedItems;
        
        WipeType(String displayName, List<String> wipedItems) {
            this.displayName = displayName;
            this.wipedItems = wipedItems;
        }
        
        public String getDisplayName() { return displayName; }
        public List<String> getWipedItems() { return wipedItems; }
    }
    
    private static class WipeRequest {
        private final UUID staffId;
        private final UUID targetId;
        private final String targetName;
        private final WipeType wipeType;
        private final long timestamp;
        
        public WipeRequest(UUID staffId, UUID targetId, String targetName, WipeType wipeType, long timestamp) {
            this.staffId = staffId;
            this.targetId = targetId;
            this.targetName = targetName;
            this.wipeType = wipeType;
            this.timestamp = timestamp;
        }
        
        public UUID getStaffId() { return staffId; }
        public UUID getTargetId() { return targetId; }
        public String getTargetName() { return targetName; }
        public WipeType getWipeType() { return wipeType; }
        public long getTimestamp() { return timestamp; }
    }
}
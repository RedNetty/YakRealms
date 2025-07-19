package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.mechanics.world.lootchests.core.Chest;
import com.rednetty.server.mechanics.world.lootchests.core.ChestManager;
import com.rednetty.server.mechanics.world.lootchests.types.ChestState;
import com.rednetty.server.mechanics.world.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.world.lootchests.types.ChestType;
import com.rednetty.server.mechanics.world.lootchests.types.ChestLocation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LootChestCommand implements CommandExecutor, TabCompleter {

    private final ChestManager manager;

    public LootChestCommand() {
        this.manager = ChestManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> {
                return handleCreateCommand(player, args);
            }
            case "remove", "delete" -> {
                return handleRemoveCommand(player, args);
            }
            case "info" -> {
                return handleInfoCommand(player, args);
            }
            case "list" -> {
                return handleListCommand(player, args);
            }
            case "stats" -> {
                return handleStatsCommand(player, args);
            }
            case "carepackage", "care" -> {
                return handleCarePackageCommand(player, args);
            }
            case "special" -> {
                return handleSpecialCommand(player, args);
            }
            case "reload" -> {
                return handleReloadCommand(player, args);
            }
            case "cleanup" -> {
                return handleCleanupCommand(player, args);
            }
            case "help" -> {
                sendHelpMessage(player);
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sendHelpMessage(player);
                return false;
            }
        }
    }

    private boolean handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        ChestTier tier = ChestTier.IRON; // Default
        ChestType type = ChestType.NORMAL; // Default

        if (args.length > 1) {
            try {
                int tierLevel = Integer.parseInt(args[1]);
                tier = ChestTier.fromLevel(tierLevel);
            } catch (NumberFormatException e) {
                try {
                    tier = ChestTier.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(ChatColor.RED + "Invalid tier. Use 1-6 or tier names (WOODEN, STONE, IRON, DIAMOND, GOLDEN, LEGENDARY).");
                    return false;
                }
            }
        }

        if (args.length > 2) {
            try {
                type = ChestType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Invalid chest type. Use: NORMAL, CARE_PACKAGE, SPECIAL");
                return false;
            }
        }

        Location location = player.getLocation().getBlock().getLocation();
        Chest chest = manager.createChest(location, tier, type);

        if (chest != null) {
            player.sendMessage(ChatColor.GREEN + "Created " + type + " chest (tier " + tier.getLevel() + ") at your location.");
            player.sendMessage(ChatColor.GRAY + "Display: " + chest.getDisplayName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create chest at your location.");
        }

        return true;
    }

    private boolean handleRemoveCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        Location location = player.getLocation().getBlock().getLocation();
        ChestLocation chestLocation = new ChestLocation(location);

        boolean success = manager.removeChest(chestLocation);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Removed loot chest at your location.");
        } else {
            player.sendMessage(ChatColor.RED + "No loot chest found at your location.");
        }
        return true;
    }

    private boolean handleInfoCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.player.lootchest.info")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        Location location = player.getLocation().getBlock().getLocation();
        ChestLocation chestLocation = new ChestLocation(location);
        Chest chest = manager.getRegistry().getChest(chestLocation);

        if (chest == null) {
            player.sendMessage(ChatColor.RED + "No loot chest found at your location.");
            return false;
        }

        player.sendMessage(ChatColor.YELLOW + "=== Loot Chest Info ===");
        player.sendMessage(ChatColor.WHITE + "Location: " + chestLocation);
        player.sendMessage(ChatColor.WHITE + "Display: " + chest.getDisplayName());
        player.sendMessage(ChatColor.WHITE + "Tier: " + chest.getTier());
        player.sendMessage(ChatColor.WHITE + "Type: " + chest.getType());
        player.sendMessage(ChatColor.WHITE + "State: " + chest.getStatusString());
        player.sendMessage(ChatColor.WHITE + "Interactions: " + chest.getInteractionCount());
        player.sendMessage(ChatColor.WHITE + "Age: " + (chest.getAge() / 1000) + " seconds");

        if (chest.getRespawnTimeRemaining() > 0) {
            player.sendMessage(ChatColor.WHITE + "Respawn in: " + (chest.getRespawnTimeRemaining() / 1000) + " seconds");
        }

        return true;
    }

    private boolean handleListCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        var chests = manager.getRegistry().getAllChests();

        player.sendMessage(ChatColor.YELLOW + "=== Active Loot Chests (" + chests.size() + ") ===");

        if (chests.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No active loot chests found.");
            return true;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid page number.");
                return false;
            }
        }

        int itemsPerPage = 10;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, chests.size());

        var chestList = new ArrayList<>(chests.entrySet());

        for (int i = startIndex; i < endIndex; i++) {
            var entry = chestList.get(i);
            ChestLocation loc = entry.getKey();
            Chest data = entry.getValue();

            player.sendMessage(ChatColor.WHITE.toString() + (i + 1) + ". " + loc + " - " +
                    data.getTier() + " " + data.getType() + " (" + data.getStatusString() + ")");
        }

        int totalPages = (int) Math.ceil((double) chests.size() / itemsPerPage);
        if (totalPages > 1) {
            player.sendMessage(ChatColor.GRAY + "Page " + page + " of " + totalPages);
            if (page < totalPages) {
                player.sendMessage(ChatColor.GRAY + "Use /lootchest list " + (page + 1) + " for the next page");
            }
        }

        return true;
    }

    private boolean handleStatsCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        var stats = manager.getStatistics();

        player.sendMessage(ChatColor.YELLOW + "=== Loot Chest Statistics ===");
        for (var entry : stats.entrySet()) {
            player.sendMessage(ChatColor.WHITE + entry.getKey() + ": " + ChatColor.AQUA + entry.getValue());
        }

        // Loot generation statistics
        var factoryStats = manager.getLootGenerator().getLootStatistics();
        player.sendMessage(ChatColor.YELLOW + "\n=== Loot Generation Statistics ===");
        for (String stat : factoryStats) {
            player.sendMessage(ChatColor.WHITE + stat);
        }

        return true;
    }

    private boolean handleCarePackageCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        Location location = player.getLocation();
        Chest chest = manager.spawnCarePackage(location);

        if (chest != null) {
            player.sendMessage(ChatColor.GREEN + "Spawned care package at your location.");
            player.sendMessage(ChatColor.GRAY + "All online players have been notified!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to spawn care package.");
        }

        return true;
    }

    private boolean handleSpecialCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        ChestTier tier = ChestTier.LEGENDARY; // Default for special chests

        if (args.length > 1) {
            try {
                int tierLevel = Integer.parseInt(args[1]);
                tier = ChestTier.fromLevel(tierLevel);
            } catch (NumberFormatException e) {
                try {
                    tier = ChestTier.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(ChatColor.RED + "Invalid tier. Use 1-6 or tier names.");
                    return false;
                }
            }
        }

        Location location = player.getLocation();
        Chest chest = manager.createSpecialChest(location, tier);

        if (chest != null) {
            player.sendMessage(ChatColor.GREEN + "Created special chest (tier " + tier.getLevel() + ") at your location.");
            player.sendMessage(ChatColor.GRAY + "This chest will automatically despawn after 5 minutes.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create special chest.");
        }

        return true;
    }

    private boolean handleReloadCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        try {
            // Reload configuration
            manager.getConfig().initialize();
            player.sendMessage(ChatColor.GREEN + "Loot chest system configuration reloaded!");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to reload configuration: " + e.getMessage());
        }

        return true;
    }

    private boolean handleCleanupCommand(Player player, String[] args) {
        if (!player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return false;
        }

        int beforeCount = manager.getRegistry().size();

        int cleaned = 0;
        for (Chest chest : new ArrayList<>(manager.getRegistry().getAllChests().values())) {
            if (chest.getState() == ChestState.OPENED) {
                chest.setState(ChestState.AVAILABLE);
                manager.getRepository().saveChest(chest);
                cleaned++;
            }
        }

        int afterCount = manager.getRegistry().size();
        player.sendMessage(ChatColor.GREEN + "Cleanup completed!");
        player.sendMessage(ChatColor.GRAY + "Cleaned up " + cleaned + " chest inventories.");
        player.sendMessage(ChatColor.GRAY + "Active chests: " + afterCount);

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "=== Loot Chest Commands ===");

        if (player.hasPermission("yakrealms.admin.lootchest")) {
            player.sendMessage(ChatColor.WHITE + "/lootchest create [tier] [type] - Create a chest");
            player.sendMessage(ChatColor.WHITE + "/lootchest remove - Remove chest at your location");
            player.sendMessage(ChatColor.WHITE + "/lootchest list [page] - List all active chests");
            player.sendMessage(ChatColor.WHITE + "/lootchest stats - Show system statistics");
            player.sendMessage(ChatColor.WHITE + "/lootchest carepackage - Spawn a care package");
            player.sendMessage(ChatColor.WHITE + "/lootchest special [tier] - Create a special chest");
            player.sendMessage(ChatColor.WHITE + "/lootchest reload - Reload configuration");
            player.sendMessage(ChatColor.WHITE + "/lootchest cleanup - Clean up system");
        }

        if (player.hasPermission("yakrealms.player.lootchest.info")) {
            player.sendMessage(ChatColor.WHITE + "/lootchest info - Get info about chest at your location");
        }

        player.sendMessage(ChatColor.GRAY + "Tiers: 1-6 or WOODEN, STONE, IRON, DIAMOND, GOLDEN, LEGENDARY");
        player.sendMessage(ChatColor.GRAY + "Types: NORMAL, CARE_PACKAGE, SPECIAL");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();

            if (player.hasPermission("yakrealms.admin.lootchest")) {
                commands.addAll(Arrays.asList("create", "remove", "list", "stats", "carepackage", "special", "reload", "cleanup"));
            }

            if (player.hasPermission("yakrealms.player.lootchest.info")) {
                commands.add("info");
            }

            commands.add("help");

            return commands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "create", "special" -> {
                    List<String> tiers = new ArrayList<>();
                    tiers.addAll(Arrays.asList("1", "2", "3", "4", "5", "6"));
                    tiers.addAll(Arrays.asList("WOODEN", "STONE", "IRON", "DIAMOND", "GOLDEN", "LEGENDARY"));
                    return tiers.stream()
                            .filter(tier -> tier.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "list" -> {
                    return Arrays.asList("1", "2", "3", "4", "5");
                }
            }
        }

        if (args.length == 3 && args[0].toLowerCase().equals("create")) {
            return Arrays.stream(ChestType.values())
                    .map(type -> type.name())
                    .filter(type -> type.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
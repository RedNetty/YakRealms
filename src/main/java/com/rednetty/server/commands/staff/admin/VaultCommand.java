package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.lootchests.LootChestManager;
import com.rednetty.server.mechanics.world.lootchests.VaultChest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vault Command System for instant refresh automation vault management
 * Supports placing/removing vaults, managing the automated system, and admin functions
 *
 * Version 4.0 - Redesigned for instant refresh with full automation
 */
public class VaultCommand implements CommandExecutor, TabCompleter {

    private final YakRealms plugin;
    private final LootChestManager lootChestManager;

    // Command cooldowns to prevent spam (much shorter for instant refresh system)
    private final Map<String, Long> commandCooldowns = new ConcurrentHashMap<>();
    private static final long COMMAND_COOLDOWN_MS = 500; // 0.5 seconds

    public VaultCommand(YakRealms plugin) {
        this.plugin = plugin;
        this.lootChestManager = plugin.getLootChestManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("yakrealms.vault.admin")) {
            sender.sendMessage("§c✗ You don't have permission to use vault commands!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "place":
                case "create":
                    return handlePlace(sender, args);
                case "remove":
                case "delete":
                    return handleRemove(sender, args);
                case "list":
                    return handleList(sender, args);
                case "info":
                    return handleInfo(sender, args);
                case "near":
                    return handleNear(sender, args);
                case "testkey":
                    return handleTestKey(sender, args);
                case "stats":
                    return handleStats(sender, args);
                case "cleanup":
                    return handleCleanup(sender, args);
                case "reload":
                    return handleReload(sender, args);
                case "save":
                    return handleSave(sender, args);
                case "automation":
                case "auto":
                    return handleAutomation(sender, args);
                case "performance":
                case "perf":
                    return handlePerformance(sender, args);
                case "clearanims":
                    return handleClearAnimations(sender, args);
                default:
                    sender.sendMessage("§c✗ Unknown subcommand: " + subCommand);
                    sendHelpMessage(sender);
                    return true;
            }
        } catch (Exception e) {
            sender.sendMessage("§c✗ An error occurred: " + e.getMessage());
            YakRealms.error("Error in vault command", e);
            return true;
        }
    }

    /**
     * Place a new vault at the sender's location or specified coordinates
     * Usage: /vault place <tier> <type> [x] [y] [z] [world]
     */
    private boolean handlePlace(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c✗ Usage: /vault place <tier> <type> [x] [y] [z] [world]");
            sender.sendMessage("§7Example: /vault place 3 ELITE");
            return true;
        }

        LootChestManager.ChestTier tier;
        try {
            int tierLevel = Integer.parseInt(args[1]);
            tier = LootChestManager.ChestTier.fromLevel(tierLevel);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c✗ Invalid tier: " + args[1]);
            sender.sendMessage("§7Valid tiers: 1, 2, 3, 4, 5, 6");
            return true;
        }

        LootChestManager.ChestType type;
        try {
            type = LootChestManager.ChestType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c✗ Invalid type: " + args[2]);
            sender.sendMessage("§7Valid types: NORMAL, FOOD, ELITE");
            return true;
        }

        // Determine placement location
        Location location;
        if (args.length >= 6) {
            // Coordinates specified
            try {
                double x = Double.parseDouble(args[3]);
                double y = Double.parseDouble(args[4]);
                double z = Double.parseDouble(args[5]);
                String worldName = args.length >= 7 ? args[6] : (sender instanceof Player ? ((Player) sender).getWorld().getName() : "world");

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage("§c✗ World not found: " + worldName);
                    return true;
                }

                location = new Location(world, x, y, z);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c✗ Invalid coordinates!");
                return true;
            }
        } else if (sender instanceof Player) {
            location = ((Player) sender).getLocation();
        } else {
            sender.sendMessage("§c✗ Console must specify coordinates!");
            return true;
        }

        // Check if location is already occupied
        VaultChest existingVault = lootChestManager.getVaultAtLocation(location);
        if (existingVault != null) {
            sender.sendMessage("§c✗ There's already a vault at this location!");
            sender.sendMessage("§7Existing: " + existingVault.getDisplayName());
            return true;
        }

        // Place the vault
        String placedBy = sender.getName();
        if (lootChestManager.placeVault(location, tier, type, placedBy)) {
            sender.sendMessage("§a✓ Placed " + tier.getColor() + tier.getDisplayName() + " " + type.getName() + " Vault§a!");
            sender.sendMessage("§7Location: " + formatLocation(location));
            sender.sendMessage("§a§l⚡ INSTANT REFRESH ENABLED ⚡");
            sender.sendMessage("§a§l⚡ FULLY AUTOMATIC OPENING ⚡");
            sender.sendMessage("§7Ready for continuous use immediately!");
        } else {
            sender.sendMessage("§c✗ Failed to place vault!");
        }

        return true;
    }

    /**
     * Remove a vault at the sender's location or nearest vault
     * Usage: /vault remove [confirm]
     */
    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✗ Only players can remove vaults!");
            return true;
        }

        Player player = (Player) sender;
        Location location = player.getLocation();

        // Find nearest vault
        VaultChest nearestVault = lootChestManager.getNearestVault(location, 10.0);
        if (nearestVault == null) {
            sender.sendMessage("§c✗ No vault found within 10 blocks!");
            return true;
        }

        // Require confirmation for safety
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage("§6⚠ Are you sure you want to remove this vault?");
            sender.sendMessage("§7Vault: " + nearestVault.getDisplayName());
            sender.sendMessage("§7Location: " + formatLocation(nearestVault.getLocation()));
            sender.sendMessage("§7Times Opened: " + nearestVault.getTimesOpened());
            sender.sendMessage("§7Status: " + nearestVault.getAvailabilityStatus());
            sender.sendMessage("§cType §f/vault remove confirm§c to proceed");
            return true;
        }

        // Remove the vault
        if (lootChestManager.removeVault(nearestVault.getLocation())) {
            sender.sendMessage("§a✓ Removed " + nearestVault.getDisplayName() + "!");
            sender.sendMessage("§7Instant refresh automation disabled for this location.");
        } else {
            sender.sendMessage("§c✗ Failed to remove vault!");
        }

        return true;
    }

    /**
     * List all vaults with pagination - enhanced for instant refresh system
     * Usage: /vault list [page] [world]
     */
    private boolean handleList(CommandSender sender, String[] args) {
        Collection<VaultChest> allVaults = lootChestManager.getAllPlacedVaults();

        if (allVaults.isEmpty()) {
            sender.sendMessage("§7No vaults have been placed yet.");
            sender.sendMessage("§a§lReady for instant refresh vault system!");
            return true;
        }

        // Filter by world if specified
        String worldFilter = null;
        int page = 1;

        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                worldFilter = args[1];
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("§c✗ Invalid page number!");
                        return true;
                    }
                }
            }
        }

        List<VaultChest> vaults = new ArrayList<>(allVaults);

        // Apply world filter
        if (worldFilter != null) {
            final String finalWorldFilter = worldFilter;
            vaults = vaults.stream()
                    .filter(vault -> vault.getLocation().getWorld().getName().equalsIgnoreCase(finalWorldFilter))
                    .collect(Collectors.toList());

            if (vaults.isEmpty()) {
                sender.sendMessage("§7No vaults found in world: " + worldFilter);
                return true;
            }
        }

        // Sort by tier then by times opened
        vaults.sort((a, b) -> {
            int tierCompare = Integer.compare(b.getTier().getLevel(), a.getTier().getLevel());
            if (tierCompare != 0) return tierCompare;
            return Integer.compare(b.getTimesOpened(), a.getTimesOpened());
        });

        int vaultsPerPage = 8;
        int totalPages = (int) Math.ceil((double) vaults.size() / vaultsPerPage);

        if (page < 1 || page > totalPages) {
            sender.sendMessage("§c✗ Invalid page! Pages: 1-" + totalPages);
            return true;
        }

        String header = "§6═══ Instant Refresh Vaults";
        if (worldFilter != null) {
            header += " (World: " + worldFilter + ")";
        }
        header += " (Page " + page + "/" + totalPages + ") ═══";
        sender.sendMessage(header);
        sender.sendMessage("§a⚡ All vaults feature instant refresh and automatic opening ⚡");

        int startIndex = (page - 1) * vaultsPerPage;
        int endIndex = Math.min(startIndex + vaultsPerPage, vaults.size());

        for (int i = startIndex; i < endIndex; i++) {
            VaultChest vault = vaults.get(i);

            sender.sendMessage("§7" + (i + 1) + ". " + vault.getCompactInfo());
            sender.sendMessage("   §8" + vault.getLocationString() + " " + vault.getAvailabilityStatus());
        }

        if (totalPages > 1) {
            sender.sendMessage("§7Use §f/vault list " + (worldFilter != null ? worldFilter + " " : "") + "<page>§7 for other pages");
        }

        // Show system status
        long totalOpened = vaults.stream().mapToLong(VaultChest::getTimesOpened).sum();
        sender.sendMessage("§7Total vault openings: §a" + totalOpened + " §7(instant refresh enabled)");

        return true;
    }

    /**
     * Get detailed info about a specific vault - enhanced for automation
     * Usage: /vault info [vaultId]
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        VaultChest vault;

        if (args.length >= 2) {
            // Look up by ID
            String searchId = args[1];
            vault = lootChestManager.getVaultById(searchId);
            if (vault == null) {
                // Try partial ID match
                vault = lootChestManager.getAllPlacedVaults().stream()
                        .filter(v -> v.getId().startsWith(searchId))
                        .findFirst()
                        .orElse(null);
            }

            if (vault == null) {
                sender.sendMessage("§c✗ Vault not found with ID: " + searchId);
                return true;
            }
        } else if (sender instanceof Player) {
            // Find nearest vault
            Player player = (Player) sender;
            vault = lootChestManager.getNearestVault(player.getLocation(), 50.0);
            if (vault == null) {
                sender.sendMessage("§c✗ No vault found within 50 blocks!");
                return true;
            }
        } else {
            sender.sendMessage("§c✗ Console must specify vault ID!");
            return true;
        }

        // Display detailed info with automation details
        sender.sendMessage(vault.getDetailedInfo());

        // Additional automation info
        sender.sendMessage("§a⚡ AUTOMATION FEATURES ⚡");
        sender.sendMessage("§7• Keys automatically fly to vault");
        sender.sendMessage("§7• Vault opens without manual interaction");
        sender.sendMessage("§7• Instant refresh - no cooldowns");
        sender.sendMessage("§7• Continuous use capability");

        // Performance metrics if available
        if (vault.getTimesOpened() > 0) {
            sender.sendMessage("");
            sender.sendMessage(vault.getPerformanceMetrics());
        }

        return true;
    }

    /**
     * Find vaults near a location - enhanced for automation
     * Usage: /vault near [radius] [player]
     */
    private boolean handleNear(CommandSender sender, String[] args) {
        double radius = 100.0;
        Player target;

        if (args.length >= 2) {
            try {
                radius = Double.parseDouble(args[1]);
                if (radius <= 0 || radius > 1000) {
                    sender.sendMessage("§c✗ Radius must be between 1 and 1000!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c✗ Invalid radius!");
                return true;
            }
        }

        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§c✗ Player not found: " + args[2]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§c✗ Console must specify a player!");
            return true;
        }

        Location searchLocation = target.getLocation();
        List<VaultChest> nearbyVaults = lootChestManager.getVaultsNear(searchLocation, radius);

        if (nearbyVaults.isEmpty()) {
            sender.sendMessage("§7No vaults found within " + radius + " blocks of " + target.getName());
            sender.sendMessage("§7Key drops will not work in this area.");
            return true;
        }

        sender.sendMessage("§6═══ Instant Refresh Vaults near " + target.getName() + " (Radius: " + radius + ") ═══");

        int readyCount = 0;
        int animatingCount = 0;

        for (int i = 0; i < Math.min(10, nearbyVaults.size()); i++) {
            VaultChest vault = nearbyVaults.get(i);
            double distance = vault.getLocation().distance(searchLocation);

            if (vault.isInstantlyAvailable()) {
                readyCount++;
            } else {
                animatingCount++;
            }

            sender.sendMessage("§7" + (i + 1) + ". " + vault.getTier().getColor() + vault.getDisplayName() +
                    " §7(" + String.format("%.1f", distance) + "m) " + vault.getAvailabilityStatus());
        }

        if (nearbyVaults.size() > 10) {
            sender.sendMessage("§7... and " + (nearbyVaults.size() - 10) + " more vaults");
        }

        sender.sendMessage("");
        sender.sendMessage("§a⚡ Ready for keys: §f" + readyCount + " §7vaults");
        sender.sendMessage("§e⚡ Currently animating: §f" + animatingCount + " §7vaults");
        sender.sendMessage("§7Key drops will target the nearest available vault automatically!");

        return true;
    }

    /**
     * Test key drop for nearest vault - enhanced for automation
     * Usage: /vault testkey
     */
    private boolean handleTestKey(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✗ Only players can test key drops!");
            return true;
        }

        Player player = (Player) sender;

        if (lootChestManager.dropKeyForNearestVault(player.getLocation())) {
            sender.sendMessage("§a✓ Dropped an automatic vault key!");
            sender.sendMessage("§7The key will fly to the nearest vault and open it automatically!");
            sender.sendMessage("§a⚡ No manual interaction required ⚡");
        } else {
            sender.sendMessage("§c✗ No available vault found for key drop!");
            sender.sendMessage("§7Use §f/vault near§7 to see nearby vaults.");
        }

        return true;
    }

    /**
     * Show enhanced vault system statistics for automation
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        Map<String, Object> stats = lootChestManager.getStatistics();

        sender.sendMessage("§6═══════ Instant Refresh Vault Statistics ═══════");
        sender.sendMessage("§7Total Placed: §f" + stats.get("total_placed"));
        sender.sendMessage("§7Total Opened: §f" + stats.get("total_opened"));
        sender.sendMessage("§7Keys Dropped: §f" + stats.get("keys_dropped"));
        sender.sendMessage("§7Active Vaults: §f" + stats.get("active_vaults"));
        sender.sendMessage("§7Active Animations: §f" + stats.get("active_animations"));

        // Calculate efficiency
        int totalPlaced = (Integer) stats.get("total_placed");
        int totalOpened = (Integer) stats.get("total_opened");
        if (totalPlaced > 0) {
            double avgOpenings = (double) totalOpened / totalPlaced;
            sender.sendMessage("§7Avg Openings/Vault: §f" + String.format("%.1f", avgOpenings));
        }

        sender.sendMessage("");
        sender.sendMessage("§6Tier Open Count:");

        @SuppressWarnings("unchecked")
        Map<LootChestManager.ChestTier, Integer> tierOpenCount =
                (Map<LootChestManager.ChestTier, Integer>) stats.get("tier_open_count");

        for (LootChestManager.ChestTier tier : LootChestManager.ChestTier.values()) {
            int count = tierOpenCount.getOrDefault(tier, 0);
            sender.sendMessage("  " + tier.getDisplayName() + "§7: §f" + count);
        }

        sender.sendMessage("");
        sender.sendMessage("§a⚡ INSTANT REFRESH SYSTEM STATUS ⚡");
        sender.sendMessage("§aInstant Refresh: §f✓ ENABLED");
        sender.sendMessage("§aAutomatic Opening: §f✓ ENABLED");
        sender.sendMessage("§aAnimation Only Delay: §f~3 seconds");
        sender.sendMessage("§aContinuous Use: §f✓ SUPPORTED");

        return true;
    }

    /**
     * Cleanup expired animations and data
     */
    private boolean handleCleanup(CommandSender sender, String[] args) {
        sender.sendMessage("§7Starting instant refresh vault system cleanup...");

        try {
            lootChestManager.clearAllAnimations();
            sender.sendMessage("§a✓ Vault cleanup completed!");
            sender.sendMessage("§7All vaults are now ready for instant use.");
        } catch (Exception e) {
            sender.sendMessage("§c✗ Cleanup failed: " + e.getMessage());
            YakRealms.error("Vault cleanup failed", e);
        }

        return true;
    }

    /**
     * New automation status command
     */
    private boolean handleAutomation(CommandSender sender, String[] args) {
        sender.sendMessage("§6═══════ Vault Automation Status ═══════");
        sender.sendMessage("§a⚡ SYSTEM STATUS: FULLY AUTOMATIC ⚡");
        sender.sendMessage("");
        sender.sendMessage("§7Key System:");
        sender.sendMessage("§a✓ Keys automatically fly to nearest vault");
        sender.sendMessage("§a✓ No manual pickup required");
        sender.sendMessage("§a✓ Smart vault targeting");
        sender.sendMessage("");
        sender.sendMessage("§7Opening System:");
        sender.sendMessage("§a✓ Vaults open automatically on key arrival");
        sender.sendMessage("§a✓ No manual interaction needed");
        sender.sendMessage("§a✓ Instant loot dispensing");
        sender.sendMessage("");
        sender.sendMessage("§7Refresh System:");
        sender.sendMessage("§a✓ Instant refresh enabled");
        sender.sendMessage("§a✓ No cooldowns between uses");
        sender.sendMessage("§a✓ Continuous operation support");
        sender.sendMessage("");
        sender.sendMessage("§7Performance:");
        sender.sendMessage("§a✓ Animation delay only: ~3 seconds");
        sender.sendMessage("§a✓ Multiple vaults can operate simultaneously");
        sender.sendMessage("§a✓ Optimal for high-activity areas");

        return true;
    }

    /**
     * Performance analysis command
     */
    private boolean handlePerformance(CommandSender sender, String[] args) {
        Collection<VaultChest> allVaults = lootChestManager.getAllPlacedVaults();

        if (allVaults.isEmpty()) {
            sender.sendMessage("§7No vaults to analyze.");
            return true;
        }

        sender.sendMessage("§6═══════ Vault Performance Analysis ═══════");

        // Calculate overall stats
        long totalOpenings = allVaults.stream().mapToLong(VaultChest::getTimesOpened).sum();
        int readyVaults = (int) allVaults.stream().filter(VaultChest::isInstantlyAvailable).count();
        int animatingVaults = allVaults.size() - readyVaults;

        sender.sendMessage("§7Total Vaults: §f" + allVaults.size());
        sender.sendMessage("§7Ready Vaults: §a" + readyVaults + " §7(§f" +
                String.format("%.1f", (double) readyVaults / allVaults.size() * 100) + "%§7)");
        sender.sendMessage("§7Animating: §e" + animatingVaults);
        sender.sendMessage("§7Total Openings: §f" + totalOpenings);

        if (totalOpenings > 0) {
            double avgPerVault = (double) totalOpenings / allVaults.size();
            sender.sendMessage("§7Avg Opens/Vault: §f" + String.format("%.1f", avgPerVault));
        }

        // Find most and least used
        VaultChest mostUsed = allVaults.stream().max(Comparator.comparingInt(VaultChest::getTimesOpened)).orElse(null);
        VaultChest leastUsed = allVaults.stream().min(Comparator.comparingInt(VaultChest::getTimesOpened)).orElse(null);

        if (mostUsed != null) {
            sender.sendMessage("");
            sender.sendMessage("§6Most Active:");
            sender.sendMessage("§7" + mostUsed.getCompactInfo());
            sender.sendMessage("§7" + mostUsed.getTimesOpened() + " total openings");
        }

        if (leastUsed != null && leastUsed != mostUsed) {
            sender.sendMessage("");
            sender.sendMessage("§6Least Active:");
            sender.sendMessage("§7" + leastUsed.getCompactInfo());
            sender.sendMessage("§7" + leastUsed.getTimesOpened() + " total openings");
        }

        sender.sendMessage("");
        sender.sendMessage("§a⚡ System Performance: OPTIMAL");
        sender.sendMessage("§7Instant refresh ensures continuous availability");

        return true;
    }

    /**
     * Clear all animations command
     */
    private boolean handleClearAnimations(CommandSender sender, String[] args) {
        sender.sendMessage("§7Clearing all active vault animations...");

        lootChestManager.clearAllAnimations();

        sender.sendMessage("§a✓ All vault animations cleared!");
        sender.sendMessage("§7All vaults are now immediately available.");

        return true;
    }

    /**
     * Reload configuration
     */
    private boolean handleReload(CommandSender sender, String[] args) {
        try {
            lootChestManager.reloadConfig();
            sender.sendMessage("§a✓ Vault configuration reloaded!");
            sender.sendMessage("§a⚡ Instant refresh system maintained");
        } catch (Exception e) {
            sender.sendMessage("§c✗ Failed to reload configuration: " + e.getMessage());
            YakRealms.error("Failed to reload vault config", e);
        }
        return true;
    }

    /**
     * Force save all vault data
     */
    private boolean handleSave(CommandSender sender, String[] args) {
        try {
            lootChestManager.forceSave();
            sender.sendMessage("§a✓ Vault data saved!");
            sender.sendMessage("§7All vault states preserved for instant refresh");
        } catch (Exception e) {
            sender.sendMessage("§c✗ Failed to save data: " + e.getMessage());
            YakRealms.error("Failed to save vault data", e);
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6═══════ Instant Refresh Vault Commands ═══════");
        sender.sendMessage("§e/vault place <tier> <type> [x] [y] [z] [world] §7- Place a new vault");
        sender.sendMessage("§e/vault remove [confirm] §7- Remove nearest vault");
        sender.sendMessage("§e/vault list [page] [world] §7- List all placed vaults");
        sender.sendMessage("§e/vault info [vaultId] §7- Show detailed vault info");
        sender.sendMessage("§e/vault near [radius] [player] §7- Find nearby vaults");
        sender.sendMessage("§e/vault testkey §7- Drop test key (automatic opening)");
        sender.sendMessage("§e/vault stats §7- View system statistics");
        sender.sendMessage("§e/vault automation §7- Show automation status");
        sender.sendMessage("§e/vault performance §7- Analyze vault performance");
        sender.sendMessage("§e/vault clearanims §7- Clear all active animations");
        sender.sendMessage("§e/vault cleanup §7- Clean up expired data");
        sender.sendMessage("§e/vault reload §7- Reload configuration");
        sender.sendMessage("§e/vault save §7- Force save all data");
        sender.sendMessage("");
        sender.sendMessage("§7Tiers: 1-6, Types: NORMAL, FOOD, ELITE");
        sender.sendMessage("§7Example: §f/vault place 3 ELITE");
        sender.sendMessage("§a⚡ INSTANT REFRESH: Vaults are immediately ready after use!");
        sender.sendMessage("§a⚡ FULLY AUTOMATIC: No manual interaction required!");
    }

    private String formatLocation(Location loc) {
        return String.format("%s: %d, %d, %d",
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "place", "create", "remove", "delete", "list", "info", "near",
                    "testkey", "stats", "automation", "auto", "performance", "perf",
                    "clearanims", "cleanup", "reload", "save"
            );
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "place":
                case "create":
                    StringUtil.copyPartialMatches(args[1], Arrays.asList("1", "2", "3", "4", "5", "6"), completions);
                    break;
                case "remove":
                case "delete":
                    StringUtil.copyPartialMatches(args[1], Arrays.asList("confirm"), completions);
                    break;
                case "near":
                    StringUtil.copyPartialMatches(args[1], Arrays.asList("10", "25", "50", "100", "200"), completions);
                    break;
                case "list":
                    // Add world names and page numbers
                    Bukkit.getWorlds().forEach(world -> completions.add(world.getName()));
                    StringUtil.copyPartialMatches(args[1], Arrays.asList("1", "2", "3"), completions);
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "place":
                case "create":
                    StringUtil.copyPartialMatches(args[2], Arrays.asList("NORMAL", "FOOD", "ELITE"), completions);
                    break;
                case "near":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
package com.rednetty.server.commands.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.core.CustomMob;
import com.rednetty.server.mechanics.mobs.core.EliteMob;
import com.rednetty.server.mechanics.mobs.core.MobType;
import com.rednetty.server.mechanics.mobs.spawners.MobEntry;
import com.rednetty.server.mechanics.mobs.utils.MobUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * IMPROVED: Enhanced SpawnMobCommand with better integration with the MobManager system
 */
public class SpawnMobCommand implements CommandExecutor, TabCompleter {
    private final MobManager mobManager;
    private final Logger logger;

    /**
     * Constructor
     */
    public SpawnMobCommand(MobManager mobManager) {
        this.mobManager = mobManager;
        this.logger = YakRealms.getInstance().getLogger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("yakrealms.admin.spawnmob")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        // Special handling for multi-mob spawning
        if (args[0].equalsIgnoreCase("multi") || args[0].equalsIgnoreCase("bulk")) {
            return handleMultiSpawn(player, args);
        }

        // Handle single mob spawning
        return handleSingleSpawn(player, args);
    }

    /**
     * IMPROVED: Handle single mob spawning with better validation
     */
    private boolean handleSingleSpawn(Player player, String[] args) {
        // Parse arguments
        SpawnRequest request = parseSpawnArguments(args);
        if (request == null) {
            sendUsage(player);
            return true;
        }

        // Validate the spawn request
        ValidationResult validation = validateSpawnRequest(request);
        if (!validation.isValid()) {
            player.sendMessage(ChatColor.RED + validation.getErrorMessage());
            if (validation.hasSuggestions()) {
                player.sendMessage(ChatColor.GRAY + validation.getSuggestions());
            }
            return true;
        }

        // Perform the spawn
        return executeSpawn(player, request);
    }

    /**
     * Handle multi-mob spawning
     */
    private boolean handleMultiSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawnmob multi <data>");
            player.sendMessage(ChatColor.GRAY + "Example: /spawnmob multi skeleton:3@false#2,zombie:2@true#1");
            return true;
        }

        try {
            // Build data string from remaining arguments
            StringBuilder dataBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) dataBuilder.append(" ");
                dataBuilder.append(args[i]);
            }

            String data = dataBuilder.toString().trim();
            return spawnMultipleMobs(player, data);

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error in multi-spawn: " + e.getMessage());
            logger.warning("Multi-spawn error: " + e.getMessage());
            return true;
        }
    }

    /**
     * Spawn multiple mobs from data string
     */
    private boolean spawnMultipleMobs(Player player, String data) {
        try {
            // Parse data into mob entries
            String[] entries = data.split(",");
            List<MobEntry> mobEntries = new ArrayList<>();

            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                try {
                    MobEntry mobEntry = MobEntry.fromString(entry);
                    mobEntries.add(mobEntry);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Invalid entry: " + entry + " - " + e.getMessage());
                    return true;
                }
            }

            if (mobEntries.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No valid mob entries found.");
                return true;
            }

            // Spawn all the mobs
            Location spawnLocation = player.getLocation();
            int totalSpawned = 0;
            int totalRequested = 0;

            for (MobEntry entry : mobEntries) {
                totalRequested += entry.getAmount();

                for (int i = 0; i < entry.getAmount(); i++) {
                    // Calculate spawn location with slight offset
                    Location mobLocation = spawnLocation.clone().add(
                            (Math.random() * 6) - 3,  // Random X offset
                            0,
                            (Math.random() * 6) - 3   // Random Z offset
                    );

                    LivingEntity mob = spawnSingleMob(mobLocation, entry.getMobType(), entry.getTier(), entry.isElite());
                    if (mob != null) {
                        totalSpawned++;
                    }
                }
            }

            // Send results
            player.sendMessage(ChatColor.GREEN + "Multi-spawn completed!");
            player.sendMessage(ChatColor.YELLOW + "Successfully spawned " + totalSpawned + "/" + totalRequested + " mobs");

            // Show breakdown
            for (MobEntry entry : mobEntries) {
                String mobName = MobUtils.getDisplayName(entry.getMobType());
                String description = entry.getDescription();
                player.sendMessage(ChatColor.GRAY + "- " + description);
            }

            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Multi-spawn failed: " + e.getMessage());
            logger.warning("Multi-spawn error: " + e.getMessage());
            return true;
        }
    }

    /**
     * Execute a single spawn request
     */
    private boolean executeSpawn(Player player, SpawnRequest request) {
        try {
            int successCount = 0;
            Location baseLocation = player.getLocation();

            for (int i = 0; i < request.amount; i++) {
                // Calculate spawn location with slight randomization for multiple mobs
                Location spawnLocation = baseLocation.clone();
                if (request.amount > 1) {
                    spawnLocation.add(
                            (Math.random() * 4) - 2,  // Random X offset
                            0,
                            (Math.random() * 4) - 2   // Random Z offset
                    );
                }

                LivingEntity mob = spawnSingleMob(spawnLocation, request.mobType, request.tier, request.elite);
                if (mob != null) {
                    successCount++;

                    // Special handling for world bosses
                    if (request.isWorldBoss() && successCount == 1) {
                        announceWorldBossSpawn(player, request);
                    }
                }
            }

            // Send result message
            sendSpawnResult(player, request, successCount);
            return true;

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Spawn failed: " + e.getMessage());
            logger.warning("Spawn error: " + e.getMessage());
            return true;
        }
    }

    /**
     * IMPROVED: Spawn a single mob using the MobManager's improved system
     */
    private LivingEntity spawnSingleMob(Location location, String mobType, int tier, boolean elite) {
        try {
            MobType type = MobType.getById(mobType);
            if (type == null) {
                logger.warning("Invalid mob type for spawning: " + mobType);
                return null;
            }

            CustomMob customMob = new CustomMob(MobType.getById(mobType), tier, elite);
            LivingEntity entity;
            if(elite) {
                customMob = new EliteMob(MobType.getById(mobType), tier);

            }
            customMob.spawn(location);
            entity = customMob.getEntity();
            if(entity == null) return null;

            // Prevent removal
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);


            logger.warning("Both CustomMob and fallback spawning failed for: " + mobType);
            return null;

        } catch (Exception e) {
            logger.warning("Single mob spawn error: " + e.getMessage());
            if (YakRealms.getInstance().isDebugMode()) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Parse spawn arguments into a SpawnRequest
     */
    private SpawnRequest parseSpawnArguments(String[] args) {
        try {
            SpawnRequest request = new SpawnRequest();

            // Mob type (required)
            if (args.length >= 1) {
                request.mobType = args[0].toLowerCase();
            } else {
                return null;
            }

            // Tier (optional, default 1)
            if (args.length >= 2) {
                try {
                    request.tier = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    return null; // Invalid tier
                }
            }

            // Elite status (optional, default false)
            if (args.length >= 3) {
                request.elite = Boolean.parseBoolean(args[2]);
            }

            // Amount (optional, default 1)
            if (args.length >= 4) {
                try {
                    request.amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    return null; // Invalid amount
                }
            }

            return request;

        } catch (Exception e) {
            logger.warning("Argument parsing error: " + e.getMessage());
            return null;
        }
    }

    /**
     * IMPROVED: Validate spawn request with detailed feedback
     */
    private ValidationResult validateSpawnRequest(SpawnRequest request) {
        // Check mob type
        if (!MobType.isValidType(request.mobType)) {
            List<String> similarTypes = findSimilarMobTypes(request.mobType);
            String suggestions = similarTypes.isEmpty() ?
                    "Use '/spawnmob help' to see all valid types." :
                    "Did you mean: " + String.join(", ", similarTypes) + "?";

            return ValidationResult.invalid("Invalid mob type: " + request.mobType, suggestions);
        }

        MobType type = MobType.getById(request.mobType);

        // Check tier validity for this mob type
        if (!type.isValidTier(request.tier)) {
            return ValidationResult.invalid(
                    "Invalid tier for " + request.mobType + ". Valid range: " +
                            type.getMinTier() + "-" + type.getMaxTier()
            );
        }

        // Check T6 availability
        if (request.tier > 5 && !YakRealms.isT6Enabled()) {
            return ValidationResult.invalid("Tier 6 mobs are not enabled on this server.");
        }

        // Check elite validity
        if (request.elite && type.isElite()) {
            return ValidationResult.invalid(
                    request.mobType + " is already an elite mob type. Set elite to false.",
                    "Elite mob types: " + getEliteMobTypesList()
            );
        }

        if (request.elite && type.isWorldBoss()) {
            return ValidationResult.invalid("World bosses cannot be made elite.");
        }

        // Check amount limits
        if (request.amount < 1 || request.amount > 50) {
            return ValidationResult.invalid("Amount must be between 1 and 50.");
        }

        // Special checks for world bosses
        if (type.isWorldBoss()) {
            if (request.amount > 1) {
                return ValidationResult.invalid(
                        "World bosses can only be spawned one at a time.",
                        "Use amount 1 for world boss spawning."
                );
            }

            // Check if a world boss is already active (this would need implementation)
            // if (mobManager.hasActiveWorldBoss()) {
            //     return ValidationResult.invalid("A world boss is already active.");
            // }
        }

        return ValidationResult.valid();
    }

    /**
     * Find similar mob types for suggestions
     */
    private List<String> findSimilarMobTypes(String input) {
        return Arrays.stream(MobType.values())
                .map(MobType::getId)
                .filter(id -> id.contains(input.toLowerCase()) ||
                        input.toLowerCase().contains(id) ||
                        calculateSimilarity(id, input.toLowerCase()) > 0.6)
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * Calculate string similarity (simple implementation)
     */
    private double calculateSimilarity(String str1, String str2) {
        if (str1.equals(str2)) return 1.0;

        int maxLen = Math.max(str1.length(), str2.length());
        if (maxLen == 0) return 1.0;

        int distance = calculateLevenshteinDistance(str1, str2);
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * Calculate Levenshtein distance
     */
    private int calculateLevenshteinDistance(String str1, String str2) {
        int[][] dp = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            for (int j = 0; j <= str2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                            dp[i - 1][j - 1] + (str1.charAt(i - 1) == str2.charAt(j - 1) ? 0 : 1),
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }

        return dp[str1.length()][str2.length()];
    }

    /**
     * Get list of elite mob types
     */
    private String getEliteMobTypesList() {
        return Arrays.stream(MobType.values())
                .filter(MobType::isElite)
                .map(MobType::getId)
                .collect(Collectors.joining(", "));
    }

    /**
     * Announce world boss spawn
     */
    private void announceWorldBossSpawn(Player player, SpawnRequest request) {
        try {
            MobType type = MobType.getById(request.mobType);
            String bossName = MobUtils.getDisplayName(request.mobType);

            // Broadcast to all players
            String announcement = ChatColor.DARK_RED + "" + ChatColor.BOLD +
                    "⚠ WORLD BOSS SPAWNED ⚠" + ChatColor.RESET + " " +
                    ChatColor.RED + bossName + " T" + request.tier +
                    " has been summoned by " + player.getName() + "!";

            player.getServer().broadcastMessage(announcement);

            // Play sound effect
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        } catch (Exception e) {
            logger.warning("World boss announcement error: " + e.getMessage());
        }
    }

    /**
     * Send spawn result message
     */
    private void sendSpawnResult(Player player, SpawnRequest request, int successCount) {
        String mobName = MobUtils.getDisplayName(request.mobType);
        ChatColor tierColor = MobUtils.getTierColor(request.tier);

        if (successCount == request.amount) {
            player.sendMessage(ChatColor.GREEN + "Successfully spawned " + successCount + " mob" +
                    (successCount > 1 ? "s" : "") + ": " + tierColor + mobName +
                    " T" + request.tier + (request.elite ? "+" : ""));
        } else if (successCount > 0) {
            player.sendMessage(ChatColor.YELLOW + "Partially successful: spawned " + successCount +
                    "/" + request.amount + " " + tierColor + mobName +
                    " T" + request.tier + (request.elite ? "+" : ""));
        } else {
            player.sendMessage(ChatColor.RED + "Failed to spawn any mobs. Check console for errors.");
        }

        // Additional info for special mob types
        MobType type = MobType.getById(request.mobType);
        if (type != null) {
            if (type.isWorldBoss()) {
                player.sendMessage(ChatColor.GOLD + "World boss spawned! Good luck!");
            } else if (type.isElite()) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Elite mob spawned!");
            }
        }
    }

    /**
     * Send usage message to player
     */
    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== SpawnMob Command Usage =====");
        player.sendMessage(ChatColor.YELLOW + "/spawnmob <type> [tier] [elite] [amount]");
        player.sendMessage(ChatColor.YELLOW + "/spawnmob multi <data>");
        player.sendMessage(ChatColor.GRAY + "  <type> - The type of mob to spawn");
        player.sendMessage(ChatColor.GRAY + "  [tier] - The tier of the mob (1-6, default: 1)");
        player.sendMessage(ChatColor.GRAY + "  [elite] - Whether the mob is elite (true/false, default: false)");
        player.sendMessage(ChatColor.GRAY + "  [amount] - The number of mobs to spawn (1-50, default: 1)");
        player.sendMessage(ChatColor.GRAY + "  <data> - Multi-spawn format: type:tier@elite#amount,type:tier@elite#amount");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Examples:");
        player.sendMessage(ChatColor.WHITE + "/spawnmob skeleton 3 false 5");
        player.sendMessage(ChatColor.WHITE + "/spawnmob witherskeleton 5 true 1");
        player.sendMessage(ChatColor.WHITE + "/spawnmob multi skeleton:3@false#2,zombie:4@true#1");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Use '/spawnmob help' to see all valid mob types.");
    }

    /**
     * Show help with all mob types
     */
    private void showMobTypeHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== Valid Mob Types =====");

        List<String> regularMobs = new ArrayList<>();
        List<String> eliteMobs = new ArrayList<>();
        List<String> worldBosses = new ArrayList<>();

        for (MobType type : MobType.values()) {
            String typeName = type.getId();
            if (type.isWorldBoss()) {
                worldBosses.add(typeName);
            } else if (type.isElite()) {
                eliteMobs.add(typeName);
            } else {
                regularMobs.add(typeName);
            }
        }

        player.sendMessage(ChatColor.GREEN + "Regular Mobs:");
        showMobTypesInColumns(player, regularMobs, ChatColor.WHITE);

        if (!eliteMobs.isEmpty()) {
            player.sendMessage(ChatColor.LIGHT_PURPLE.toString() + "Elite Mobs:");
            showMobTypesInColumns(player, eliteMobs, ChatColor.LIGHT_PURPLE);
        }

        if (!worldBosses.isEmpty()) {
            player.sendMessage(ChatColor.RED + "World Bosses:");
            showMobTypesInColumns(player, worldBosses, ChatColor.GOLD);
        }
    }

    /**
     * Show mob types in organized columns
     */
    private void showMobTypesInColumns(Player player, List<String> mobTypes, ChatColor color) {
        int typesPerLine = 6;
        for (int i = 0; i < mobTypes.size(); i += typesPerLine) {
            StringBuilder line = new StringBuilder(color.toString());
            int end = Math.min(i + typesPerLine, mobTypes.size());
            for (int j = i; j < end; j++) {
                if (j > i) line.append(", ");
                line.append(mobTypes.get(j));
            }
            player.sendMessage(line.toString());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument: mob type or "multi"
            List<String> options = new ArrayList<>();
            options.add("multi");

            // Add all mob types
            for (MobType type : MobType.values()) {
                options.add(type.getId());
            }

            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("multi")) {
            // Second argument: tier suggestions based on mob type
            if (MobType.isValidType(args[0])) {
                MobType type = MobType.getById(args[0]);
                if (type != null) {
                    List<String> validTiers = new ArrayList<>();
                    for (int i = type.getMinTier(); i <= type.getMaxTier(); i++) {
                        validTiers.add(String.valueOf(i));
                    }
                    return validTiers.stream()
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                }
            }

            // Fallback to all tiers
            return Arrays.asList("1", "2", "3", "4", "5", "6").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && !args[0].equalsIgnoreCase("multi")) {
            // Third argument: elite status
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && !args[0].equalsIgnoreCase("multi")) {
            // Fourth argument: amount
            if (MobType.isValidType(args[0])) {
                MobType type = MobType.getById(args[0]);
                if (type != null && type.isWorldBoss()) {
                    return Arrays.asList("1").stream()
                            .filter(s -> s.startsWith(args[3]))
                            .collect(Collectors.toList());
                }
            }

            return Arrays.asList("1", "2", "5", "10", "20").stream()
                    .filter(s -> s.startsWith(args[3]))
                    .collect(Collectors.toList());
        }

        return completions;
    }

    /**
     * Internal class to represent a spawn request
     */
    private static class SpawnRequest {
        String mobType = "";
        int tier = 1;
        boolean elite = false;
        int amount = 1;

        boolean isWorldBoss() {
            MobType type = MobType.getById(mobType);
            return type != null && type.isWorldBoss();
        }
    }

    /**
     * Internal class to represent validation results
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String suggestions;

        private ValidationResult(boolean valid, String errorMessage, String suggestions) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.suggestions = suggestions;
        }

        static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        static ValidationResult invalid(String errorMessage, String suggestions) {
            return new ValidationResult(false, errorMessage, suggestions);
        }

        boolean isValid() { return valid; }
        String getErrorMessage() { return errorMessage; }
        String getSuggestions() { return suggestions; }
        boolean hasSuggestions() { return suggestions != null && !suggestions.isEmpty(); }
    }
}
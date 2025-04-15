package com.rednetty.server.commands.admin;

import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.core.MobType;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for spawning custom mobs
 */
public class SpawnMobCommand implements CommandExecutor, TabCompleter {
    private final MobManager mobManager;

    /**
     * Constructor
     *
     * @param mobManager The mob manager
     */
    public SpawnMobCommand(MobManager mobManager) {
        this.mobManager = mobManager;
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

        // Get mob type
        String mobType = args[0].toLowerCase();

        // Check if valid mob type
        if (!MobType.isValidType(mobType)) {
            player.sendMessage(ChatColor.RED + "Invalid mob type: " + mobType);
            player.sendMessage(ChatColor.GRAY + "Available types: " + String.join(", ", getAllMobTypes()));
            return true;
        }

        MobType type = MobType.getById(mobType);
        if (type == null) {
            player.sendMessage(ChatColor.RED + "Error loading mob type: " + mobType);
            return true;
        }

        // Get tier
        int tier = 1;
        if (args.length > 1) {
            try {
                tier = Integer.parseInt(args[1]);
                if (tier < 1 || tier > 6) {
                    player.sendMessage(ChatColor.RED + "Tier must be between 1 and 6.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid tier: " + args[1]);
                return true;
            }
        }

        // Get elite flag
        boolean elite = false;
        if (args.length > 2) {
            elite = Boolean.parseBoolean(args[2]);
        }

        // Check if tier is valid for this mob type
        if (tier < type.getMinTier() || tier > type.getMaxTier()) {
            player.sendMessage(ChatColor.RED + "Invalid tier for mob type " + mobType);
            player.sendMessage(ChatColor.GRAY + "Valid tier range: " +
                    type.getMinTier() + "-" +
                    type.getMaxTier());
            return true;
        }

        // Check if elite is valid for this mob type (or if already an elite type)
        if (elite && !type.isElite() && !canBecomeElite(type)) {
            player.sendMessage(ChatColor.RED + "This mob type cannot be elite: " + mobType);
            return true;
        }

        // Get amount
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 50) {
                    player.sendMessage(ChatColor.RED + "Amount must be between 1 and 50.");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid amount: " + args[3]);
                return true;
            }
        }

        // Special warning for world bosses
        if (type.isWorldBoss() && amount > 1) {
            player.sendMessage(ChatColor.RED + "Warning: Spawning multiple world bosses may cause issues!");
            // Limit to 1 if it's a world boss
   /*         if (mobManager.hasActiveBoss()) {
                player.sendMessage(ChatColor.RED + "A world boss is already active. Cannot spawn another one.");
                return true;
            }*/
            amount = 1;
        }

        // Spawn the mobs
        int successCount = 0;
        for (int i = 0; i < amount; i++) {
            LivingEntity mob = mobManager.spawnMob(player.getLocation(), mobType, tier, elite);
            if (mob != null) {
                successCount++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Successfully spawned " + successCount + "/" + amount + " mobs: " +
                ChatColor.YELLOW + mobType + " (Tier " + tier + ", Elite: " + elite + ")");

        return true;
    }

    /**
     * Send usage message to player
     */
    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== SpawnMob Command Usage =====");
        player.sendMessage(ChatColor.YELLOW + "/spawnmob <type> [tier] [elite] [amount]");
        player.sendMessage(ChatColor.GRAY + "  <type> - The type of mob to spawn");
        player.sendMessage(ChatColor.GRAY + "  [tier] - The tier of the mob (1-6, default: 1)");
        player.sendMessage(ChatColor.GRAY + "  [elite] - Whether the mob is elite (true/false, default: false)");
        player.sendMessage(ChatColor.GRAY + "  [amount] - The number of mobs to spawn (1-50, default: 1)");
        player.sendMessage(ChatColor.GRAY + "Example: /spawnmob skeleton 3 false 5");
    }

    /**
     * Check if a mob type can become elite
     *
     * @param type The mob type
     * @return true if it can be elite
     */
    private boolean canBecomeElite(MobType type) {
        // Regular mobs can become elite, named elites and world bosses are already elite
        return !type.isElite() && !type.isWorldBoss();
    }

    /**
     * Get all available mob types
     *
     * @return List of mob type IDs
     */
    private List<String> getAllMobTypes() {
        List<String> types = new ArrayList<>();
        for (MobType type : MobType.values()) {
            types.add(type.getId());
        }
        return types;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return getAllMobTypes().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // If a valid mob type was entered, suggest appropriate tiers
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
            return Arrays.asList("1", "2", "3", "4", "5", "6").stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            // For world bosses, only suggest 1
            if (MobType.isValidType(args[0])) {
                MobType type = MobType.getById(args[0]);
                if (type != null && type.isWorldBoss()) {
                    return Arrays.asList("1").stream()
                            .filter(s -> s.startsWith(args[3]))
                            .collect(Collectors.toList());
                }
            }
            return Arrays.asList("1", "5", "10", "20", "50").stream()
                    .filter(s -> s.startsWith(args[3]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
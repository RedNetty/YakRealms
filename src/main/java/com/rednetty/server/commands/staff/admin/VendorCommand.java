package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.mechanics.economy.vendors.Vendor;
import com.rednetty.server.mechanics.economy.vendors.VendorManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *  command for managing vendors with comprehensive Mount Vendor support
 * and improved aura system integration
 */
public class VendorCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final VendorManager vendorManager;

    // Pattern to match quoted strings (to allow spaces in vendor names)
    private static final Pattern QUOTED_TEXT_PATTERN = Pattern.compile("\"([^\"]*)\"");

    // Available vendor types - Updated to include mount
    private static final List<String> VENDOR_TYPES = Arrays.asList(
            "item", "fisherman", "book", "upgrade", "banker", "medic", "shop", "gambler", "mount"
    );

    // Available subcommands by category
    private static final Map<String, List<String>> COMMAND_CATEGORIES = new HashMap<>();
    static {
        COMMAND_CATEGORIES.put("Creation", Arrays.asList("create", "createat"));
        COMMAND_CATEGORIES.put("Management", Arrays.asList("delete", "rename"));
        COMMAND_CATEGORIES.put("Information", Arrays.asList("list", "types", "find", "stats"));
        COMMAND_CATEGORIES.put("System", Arrays.asList("reload", "help"));
    }

    // All available subcommands
    private static final List<String> SUBCOMMANDS = new ArrayList<>();
    static {
        COMMAND_CATEGORIES.values().forEach(SUBCOMMANDS::addAll);
    }

    /**
     * Constructor for VendorCommand
     *
     * @param plugin The JavaPlugin instance
     */
    public VendorCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vendorManager = VendorManager.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Basic syntax check
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "create":
                if (args.length < 7) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vendor create <id> <type> <\"display name\"> <world> <x> <y> <z> [yaw] [pitch]");
                    return true;
                }
                return handleCreateCommand(sender, args);

            case "createat":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vendor createat <id> <type> <\"display name\">");
                    return true;
                }
                return handleCreateAtCommand((Player) sender, args);

            case "rename":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vendor rename <id> <\"new name\">");
                    return true;
                }
                return handleRenameCommand(sender, args);

            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vendor delete <id>");
                    return true;
                }
                return handleDeleteCommand(sender, args);

            case "list":
                return handleListCommand(sender, args);

            case "types":
                return handleTypesCommand(sender);

            case "find":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vendor find <id>");
                    return true;
                }
                return handleFindCommand(sender, args);

            case "reload":
                return handleReloadCommand(sender);

            case "stats":
                return handleStatsCommand(sender);

            case "help":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCommand);
                sender.sendMessage(ChatColor.YELLOW + "Use /vendor help for a list of commands.");
                return true;
        }
    }

    /**
     * Extract a single quoted string from a command argument array
     *
     * @param args       Command arguments
     * @param startIndex Index to start looking for quoted string
     * @return The extracted quoted string or null if not found
     */
    private String extractQuotedString(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return null;
        }

        StringBuilder combined = new StringBuilder();
        boolean insideQuotes = false;
        boolean foundQuotedString = false;

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];

            if (!insideQuotes && arg.startsWith("\"")) {
                insideQuotes = true;
                // If it also ends with quote and is longer than just a quote
                if (arg.endsWith("\"") && arg.length() > 1) {
                    String content = arg.substring(1, arg.length() - 1);
                    return ChatColor.translateAlternateColorCodes('&', content);
                }
                combined.append(arg.substring(1)).append(" ");
            } else if (insideQuotes) {
                if (arg.endsWith("\"")) {
                    combined.append(arg, 0, arg.length() - 1);
                    foundQuotedString = true;
                    break;
                } else {
                    combined.append(arg).append(" ");
                }
            } else {
                // Not a quoted string, return the single argument
                return ChatColor.translateAlternateColorCodes('&', arg);
            }
        }

        if (foundQuotedString) {
            return ChatColor.translateAlternateColorCodes('&', combined.toString());
        } else if (insideQuotes) {
            // Unclosed quotes, return what we have
            return ChatColor.translateAlternateColorCodes('&', combined.toString().trim());
        } else {
            // No quotes found at all, return the single argument
            return ChatColor.translateAlternateColorCodes('&', args[startIndex]);
        }
    }

    /**
     * Find the index of the next argument after a quoted string
     *
     * @param args       Command arguments
     * @param startIndex Index to start looking for quoted string
     * @return Index of the next argument after the quoted string
     */
    private int findNextArgAfterQuotes(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return startIndex;
        }

        // If not starting with a quote, it's just a single word
        if (!args[startIndex].startsWith("\"")) {
            return startIndex + 1;
        }

        // If it starts and ends with a quote in the same argument
        if (args[startIndex].startsWith("\"") && args[startIndex].endsWith("\"") && args[startIndex].length() > 1) {
            return startIndex + 1;
        }

        // Look for the ending quote
        for (int i = startIndex; i < args.length; i++) {
            if (args[i].endsWith("\"")) {
                return i + 1;
            }
        }

        // No ending quote found, return the end
        return args.length;
    }

    /**
     * Handle the create subcommand with  mount vendor support
     */
    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        try {
            String vendorId = args[1];
            String vendorType = args[2].toLowerCase();

            // Validate vendor type
            if (!VENDOR_TYPES.contains(vendorType)) {
                sender.sendMessage(ChatColor.RED + "Unknown vendor type: " + vendorType);
                sender.sendMessage(ChatColor.RED + "Use /vendor types to see available vendor types");
                return true;
            }

            // Extract display name
            String displayName = extractQuotedString(args, 3);
            if (displayName == null) {
                sender.sendMessage(ChatColor.RED + "Missing display name. Use quotes for names with spaces: \"Display Name\"");
                return true;
            }

            // Find the next argument after the display name
            int nextArgIndex = findNextArgAfterQuotes(args, 3);

            if (args.length < nextArgIndex + 4) { // Need world, x, y, z at minimum
                sender.sendMessage(ChatColor.RED + "Not enough arguments after display name. Need world, x, y, z coordinates.");
                return true;
            }

            String worldName = args[nextArgIndex];
            double x = Double.parseDouble(args[nextArgIndex + 1]);
            double y = Double.parseDouble(args[nextArgIndex + 2]);
            double z = Double.parseDouble(args[nextArgIndex + 3]);

            nextArgIndex += 4;

            float yaw = 0;
            float pitch = 0;

            // Optional yaw/pitch if there are enough arguments
            if (args.length > nextArgIndex + 1) {
                try {
                    yaw = Float.parseFloat(args[nextArgIndex]);
                    pitch = Float.parseFloat(args[nextArgIndex + 1]);
                } catch (NumberFormatException e) {
                    // If parsing fails, ignore yaw/pitch
                }
            }

            // Get world
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "World not found: " + worldName);
                return true;
            }

            Location location = new Location(world, x, y, z, yaw, pitch);

            // Create vendor using VendorManager
            boolean success = vendorManager.createVendor(vendorId, vendorType, displayName, location);

            if (!success) {
                sender.sendMessage(ChatColor.RED + "Failed to create vendor. A vendor with ID '" + vendorId + "' may already exist.");
                return true;
            }

            // Get the created vendor for confirmation
            Vendor vendor = vendorManager.getVendor(vendorId);

            if (vendor != null) {
                sender.sendMessage(ChatColor.GREEN + "Created " + vendorType + " vendor with ID '" + vendorId +
                        "' and display name '" + displayName + "'");
            } else {
                sender.sendMessage(ChatColor.GREEN + "Created " + vendorType + " vendor with ID '" + vendorId +
                        "' and display name '" + displayName + "'");
            }

            // Special message for mount vendors
            if ("mount".equals(vendorType)) {
                sender.sendMessage(ChatColor.YELLOW + "Mount vendor ready to sell horse upgrades and elytra!");
            }

        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Invalid number format: " + ex.getMessage());
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error creating vendor: " + e.getMessage());
            plugin.getLogger().severe("Error creating vendor: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handle the createat subcommand (create vendor at player's location)
     */
    private boolean handleCreateAtCommand(Player player, String[] args) {
        try {
            String vendorId = args[1];
            String vendorType = args[2].toLowerCase();

            // Validate vendor type
            if (!VENDOR_TYPES.contains(vendorType)) {
                player.sendMessage(ChatColor.RED + "Unknown vendor type: " + vendorType);
                player.sendMessage(ChatColor.RED + "Use /vendor types to see available vendor types");
                return true;
            }

            // Extract display name
            String displayName = extractQuotedString(args, 3);
            if (displayName == null) {
                player.sendMessage(ChatColor.RED + "Missing display name. Use quotes for names with spaces: \"Display Name\"");
                return true;
            }

            // Get player's location
            Location location = player.getLocation();

            // Create vendor using VendorManager
            boolean success = vendorManager.createVendor(vendorId, vendorType, displayName, location);

            if (!success) {
                player.sendMessage(ChatColor.RED + "Failed to create vendor. A vendor with ID '" + vendorId + "' may already exist.");
                return true;
            }

            // Get the created vendor for confirmation
            Vendor vendor = vendorManager.getVendor(vendorId);

            if (vendor != null) {
                player.sendMessage(ChatColor.GREEN + "Created " + vendorType + " vendor with ID '" + vendorId +
                        "' and display name '" + displayName + "' at your location");
            } else {
                player.sendMessage(ChatColor.GREEN + "Created " + vendorType + " vendor with ID '" + vendorId +
                        "' and display name '" + displayName + "' at your location");
            }

            // Special message for mount vendors
            if ("mount".equals(vendorType)) {
                player.sendMessage(ChatColor.YELLOW + "Mount vendor ready to sell horse upgrades and elytra!");
            }

        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error creating vendor: " + e.getMessage());
            plugin.getLogger().severe("Error creating vendor: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handle the rename subcommand
     */
    private boolean handleRenameCommand(CommandSender sender, String[] args) {
        String vendorId = args[1];
        Vendor vendor = vendorManager.getVendor(vendorId);

        if (vendor == null) {
            sender.sendMessage(ChatColor.RED + "No vendor found with ID " + vendorId);
            return true;
        }

        // Extract new display name
        String newName = extractQuotedString(args, 2);
        if (newName == null) {
            sender.sendMessage(ChatColor.RED + "Missing new display name. Use quotes for names with spaces: \"New Display Name\"");
            return true;
        }

        // Update the NPC name
        NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
        if (npc != null) {
            npc.setName(newName);
            sender.sendMessage(ChatColor.GREEN + "Renamed vendor " + vendorId + " to '" + newName + "'");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to find the NPC for vendor " + vendorId);
        }

        return true;
    }

    /**
     * Handle the delete subcommand
     */
    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        String vendorId = args[1];
        boolean removed = vendorManager.deleteVendor(vendorId);

        if (removed) {
            sender.sendMessage(ChatColor.GREEN + "Deleted vendor " + vendorId);
        } else {
            sender.sendMessage(ChatColor.RED + "No vendor found with ID " + vendorId);
        }

        return true;
    }

    /**
     * Handle the list subcommand
     */
    private boolean handleListCommand(CommandSender sender, String[] args) {
        Collection<Vendor> allVendors = vendorManager.getAllVendors();

        // Convert to map for easier filtering
        Map<String, Vendor> vendors = allVendors.stream()
                .collect(Collectors.toMap(Vendor::getId, v -> v));

        // Filter by type if specified
        if (args.length > 1) {
            String typeFilter = args[1].toLowerCase();
            vendors = vendors.entrySet().stream()
                    .filter(entry -> entry.getValue().getVendorType().equalsIgnoreCase(typeFilter))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (vendors.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No vendors found of type '" + typeFilter + "'.");
                return true;
            }
        }

        if (vendors.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No vendors are currently registered.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Registered vendors" +
                    (args.length > 1 ? " of type '" + args[1] + "'" : "") + ":");

            // Sort vendors by ID for consistent display
            List<Vendor> sortedVendors = new ArrayList<>(vendors.values());
            sortedVendors.sort(Comparator.comparing(Vendor::getId));

            for (Vendor v : sortedVendors) {
                String locationInfo = String.format("(%.1f, %.1f, %.1f, %s)",
                        v.getLocation().getX(),
                        v.getLocation().getY(),
                        v.getLocation().getZ(),
                        v.getLocation().getWorld().getName());

                // Get NPC name
                NPC npc = CitizensAPI.getNPCRegistry().getById(v.getNpcId());
                String displayName = npc != null ? npc.getName() : "Unknown";

                sender.sendMessage(ChatColor.AQUA + " - " + v.getId() + ChatColor.GRAY
                        + " [" + v.getVendorType() + "] "
                        + "'" + displayName + "' "
                        + locationInfo);
            }
        }

        return true;
    }

    /**
     * Handle the types subcommand -  with mount vendor description
     */
    private boolean handleTypesCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "Available vendor types:");
        sender.sendMessage(ChatColor.AQUA + " - item: " + ChatColor.GRAY + "Sells items, orbs, and pouches");
        sender.sendMessage(ChatColor.AQUA + " - fisherman: " + ChatColor.GRAY + "Sells fishing rods and magical fish");
        sender.sendMessage(ChatColor.AQUA + " - book: " + ChatColor.GRAY + "Sells teleport books");
        sender.sendMessage(ChatColor.AQUA + " - upgrade: " + ChatColor.GRAY + "Sells permanent upgrades");
        sender.sendMessage(ChatColor.AQUA + " - banker: " + ChatColor.GRAY + "Provides bank services");
        sender.sendMessage(ChatColor.AQUA + " - medic: " + ChatColor.GRAY + "Heals players");
        sender.sendMessage(ChatColor.AQUA + " - shop: " + ChatColor.GRAY + "Generic shop (custom behavior)");
        sender.sendMessage(ChatColor.AQUA + " - gambler: " + ChatColor.GRAY + "Runs gambling games");
        sender.sendMessage(ChatColor.AQUA + " - mount: " + ChatColor.GRAY + "Sells horse tier upgrades and elytra mounts");

        return true;
    }

    /**
     * Handle the find subcommand
     */
    private boolean handleFindCommand(CommandSender sender, String[] args) {
        String vendorId = args[1];
        Vendor vendor = vendorManager.getVendor(vendorId);

        if (vendor == null) {
            sender.sendMessage(ChatColor.RED + "No vendor found with ID " + vendorId);
            return true;
        }

        Location loc = vendor.getLocation();
        String locationInfo = String.format("%.1f, %.1f, %.1f in %s",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());

        // Get NPC name
        NPC npc = CitizensAPI.getNPCRegistry().getById(vendor.getNpcId());
        String displayName = npc != null ? npc.getName() : "Unknown";

        sender.sendMessage(ChatColor.GREEN + "Vendor " + vendorId + " '" + displayName + "' is located at: " + locationInfo);

        if (sender instanceof Player) {
            Player player = (Player) sender;
            // If player is in the same world, offer teleport
            if (player.getWorld().equals(loc.getWorld())) {
                sender.sendMessage(ChatColor.YELLOW + "Use /tp " + locationInfo + " to teleport to this vendor");
            }
        }

        return true;
    }

    /**
     * Handle the reload command to reload the vendor system
     */
    private boolean handleReloadCommand(CommandSender sender) {
        try {

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading vendor system: " + e.getMessage());
            plugin.getLogger().severe("Error reloading vendor system: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Handle the stats command to show system statistics with mount data
     */
    private boolean handleStatsCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==== Vendor System Statistics ====");

        Collection<Vendor> allVendors = vendorManager.getAllVendors();
        sender.sendMessage(ChatColor.YELLOW + "Total vendors: " + ChatColor.WHITE + allVendors.size());

        // Count vendors by type
        Map<String, Integer> vendorsByType = new HashMap<>();
        for (Vendor vendor : allVendors) {
            String type = vendor.getVendorType().toLowerCase();
            vendorsByType.put(type, vendorsByType.getOrDefault(type, 0) + 1);
        }

        sender.sendMessage(ChatColor.YELLOW + "Vendors by type:");
        for (Map.Entry<String, Integer> entry : vendorsByType.entrySet()) {
            sender.sendMessage(ChatColor.GRAY + " - " + entry.getKey() + ": " + ChatColor.WHITE + entry.getValue());
        }

        sender.sendMessage(ChatColor.YELLOW + "Aura system: " + ChatColor.RED + "INACTIVE");

        return true;
    }

    /**
     * Send usage instructions to the sender
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==== Vendor Command Help ====");

        // Group commands by category for better organization
        for (Map.Entry<String, List<String>> category : COMMAND_CATEGORIES.entrySet()) {
            sender.sendMessage(ChatColor.YELLOW + "=== " + category.getKey() + " ===");

            for (String cmd : category.getValue()) {
                switch (cmd) {
                    case "create":
                        sender.sendMessage(ChatColor.AQUA + "/vendor create <id> <type> <\"display name\"> <world> <x> <y> <z> [yaw] [pitch]");
                        break;
                    case "createat":
                        sender.sendMessage(ChatColor.AQUA + "/vendor createat <id> <type> <\"display name\">");
                        break;
                    case "rename":
                        sender.sendMessage(ChatColor.AQUA + "/vendor rename <id> <\"new name\">");
                        break;
                    case "delete":
                        sender.sendMessage(ChatColor.AQUA + "/vendor delete <id>");
                        break;
                    case "list":
                        sender.sendMessage(ChatColor.AQUA + "/vendor list [type]");
                        break;
                    case "types":
                        sender.sendMessage(ChatColor.AQUA + "/vendor types");
                        break;
                    case "find":
                        sender.sendMessage(ChatColor.AQUA + "/vendor find <id>");
                        break;
                    case "refresh":
                        sender.sendMessage(ChatColor.AQUA + "/vendor refresh [id]");
                        break;
                    case "reload":
                        sender.sendMessage(ChatColor.AQUA + "/vendor reload");
                        break;
                    case "stats":
                        sender.sendMessage(ChatColor.AQUA + "/vendor stats");
                        break;
                    case "help":
                        sender.sendMessage(ChatColor.AQUA + "/vendor help");
                        break;
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Examples:");
        sender.sendMessage(ChatColor.GRAY + "/vendor createat mount_vendor mount \"Stable Master\"");
        sender.sendMessage(ChatColor.GRAY + "/vendor list mount");
        sender.sendMessage(ChatColor.GRAY + "/vendor create horse_stable mount \"Horse Trainer\" world 100 64 200");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommand
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, completions);
        } else if (args.length >= 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "delete":
                case "find":
                case "rename":
                case "refresh":
                    // Suggest vendor IDs
                    if (args.length == 2) {
                        Collection<Vendor> vendors = vendorManager.getAllVendors();
                        List<String> vendorIds = vendors.stream()
                                .map(Vendor::getId)
                                .collect(Collectors.toList());
                        StringUtil.copyPartialMatches(args[1], vendorIds, completions);
                    }
                    break;

                case "list":
                    // Suggest vendor types
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], VENDOR_TYPES, completions);
                    }
                    break;

                case "create":
                case "createat":
                    // Suggest vendor type (arg 2)
                    if (args.length == 3) {
                        StringUtil.copyPartialMatches(args[2], VENDOR_TYPES, completions);
                    }

                    // Suggest worlds for create command
                    if (subCommand.equals("create") && args.length == findNextArgAfterQuotes(args, 3) + 1) {
                        completions.addAll(plugin.getServer().getWorlds().stream()
                                .map(World::getName)
                                .collect(Collectors.toList()));
                    }
                    break;
            }
        }

        return completions;
    }
}
package com.rednetty.server.commands.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.mechanics.mobs.SpawnerProperties;
import com.rednetty.server.mechanics.mobs.core.MobType;
import com.rednetty.server.mechanics.mobs.spawners.MobSpawner;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Command for managing mob spawners with enhanced functionality
 */
public class SpawnerCommand implements CommandExecutor, TabCompleter, Listener {
    private final MobManager mobManager;
    private final MobSpawner spawner;
    private final YakRealms plugin;
    private final NamespacedKey wandKey;

    // Map for tracking guided property editing
    private final Map<String, PropertyEditSession> propertyEditors = new HashMap<>();

    // Default visibility radius when not specified
    private static final int DEFAULT_VISIBILITY_RADIUS = 30;

    /**
     * Constructor
     *
     * @param mobManager The mob manager
     */
    public SpawnerCommand(MobManager mobManager) {
        this.mobManager = mobManager;
        this.spawner = mobManager.getSpawner();
        this.plugin = YakRealms.getInstance();
        this.wandKey = new NamespacedKey(plugin, "spawner_wand");

        // Register events for wand functionality
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Session for property editing
     */
    private static class PropertyEditSession {
        private final Player player;
        private final Location spawnerLocation;
        private final long startTime;
        private String currentProperty;

        public PropertyEditSession(Player player, Location spawnerLocation) {
            this.player = player;
            this.spawnerLocation = spawnerLocation;
            this.startTime = System.currentTimeMillis();
            this.currentProperty = null;
        }

        public Player getPlayer() {
            return player;
        }

        public Location getSpawnerLocation() {
            return spawnerLocation;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getCurrentProperty() {
            return currentProperty;
        }

        public void setCurrentProperty(String property) {
            this.currentProperty = property;
        }

        public boolean hasTimedOut() {
            // Sessions time out after 5 minutes
            return System.currentTimeMillis() - startTime > 300000;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("yakrealms.admin.spawner")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCmd = args[0].toLowerCase();

        switch (subCmd) {
            case "create":
                return handleCreateCommand(player, args);
            case "remove":
            case "delete":
                return handleRemoveCommand(player);
            case "list":
                return handleListCommand(player, args);
            case "info":
                return handleInfoCommand(player);
            case "visibility":
            case "toggle":
            case "show":
            case "hide":
                return handleVisibilityCommand(player, args);
            case "reset":
                return handleResetCommand(player, args);
            case "visualize":
                return handleVisualizeCommand(player);
            case "property":
            case "set":
                return handlePropertyCommand(player, args);
            case "group":
                return handleGroupCommand(player, args);
            case "template":
                return handleTemplateCommand(player, args);
            case "wand":
                return handleWandCommand(player);
            case "display":
                return handleDisplayModeCommand(player, args);
            case "edit":
                return handleEditCommand(player);
            case "debug":
                return handleDebugCommand(player);
            case "help":
                sendHelp(player);
                return true;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCmd);
                sendHelp(player);
                return true;
        }
    }

    /**
     * Handle the create subcommand
     */
    private boolean handleCreateCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner create <data> or /spawner create template:<name>");
            player.sendMessage(ChatColor.GRAY + "Example: /spawner create skeleton:3@false#2,zombie:2@false#1");
            player.sendMessage(ChatColor.GRAY + "Template: /spawner create template:elite_t4");
            return true;
        }

        // Get the target block
        Block target = player.getTargetBlock(null, 5);
        if (target == null || target.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must be looking at a block to create a spawner.");
            return true;
        }

        Location loc = target.getLocation();

        // Enable debug mode temporarily for detailed logging
        boolean oldDebug = spawner.isDebugMode();
        spawner.setDebugMode(true);

        try {
            // Check if using a template
            if (args[1].toLowerCase().startsWith("template:")) {
                String templateName = args[1].substring(9);
                if (spawner.addSpawnerFromTemplate(loc, templateName)) {
                    player.sendMessage(ChatColor.GREEN + "Spawner created successfully using template: " +
                            ChatColor.YELLOW + templateName);
                    player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW +
                            loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());

                    // Set default visibility
                    spawner.setSpawnerVisibility(loc, spawner.getDefaultVisibility());
                    return true;
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to create spawner. Template not found: " + templateName);
                    player.sendMessage(ChatColor.GRAY + "Available templates: " +
                            String.join(", ", spawner.getAllTemplates().keySet()));
                    return true;
                }
            }

            // Build the data string from args - combine all remaining args
            StringBuilder dataBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) dataBuilder.append(" ");
                dataBuilder.append(args[i]);
            }
            String data = dataBuilder.toString().trim();

            // Remove any extra spaces around separators to ensure proper format
            data = data.replace(" :", ":").replace(": ", ":")
                    .replace(" @", "@").replace("@ ", "@")
                    .replace(" #", "#").replace("# ", "#");

            // Add the spawner
            if (spawner.addSpawner(loc, data)) {
                player.sendMessage(ChatColor.GREEN + "Spawner created successfully!");
                player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW +
                        loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                player.sendMessage(ChatColor.GRAY + "Data: " + ChatColor.YELLOW + data);

                // Set default visibility
                spawner.setSpawnerVisibility(loc, spawner.getDefaultVisibility());
            } else {
                player.sendMessage(ChatColor.RED + "Failed to create spawner. Invalid data format.");
                player.sendMessage(ChatColor.GRAY + "Format must be: mobType:tier@elite#amount");
                player.sendMessage(ChatColor.GRAY + "Example: skeleton:3@false#2,zombie:2@false#1");

                // List some valid mob types to help the player
                List<String> validMobTypes = new ArrayList<>();
                for (MobType type : MobType.values()) {
                    validMobTypes.add(type.getId());
                }

                player.sendMessage(ChatColor.YELLOW + "Valid mob types include:");

                // Show 10 mob types per line to avoid message overflow
                int typesPerLine = 10;
                for (int i = 0; i < validMobTypes.size(); i += typesPerLine) {
                    StringBuilder line = new StringBuilder(ChatColor.GRAY.toString());
                    int end = Math.min(i + typesPerLine, validMobTypes.size());
                    for (int j = i; j < end; j++) {
                        if (j > i) line.append(", ");
                        line.append(validMobTypes.get(j));
                    }
                    player.sendMessage(line.toString());
                }
            }
        } finally {
            // Restore debug mode
            spawner.setDebugMode(oldDebug);
        }

        return true;
    }

    /**
     * Handle the remove subcommand
     */
    private boolean handleRemoveCommand(Player player) {
        // Check if player is looking at a spawner or hidden spawner
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to remove it.");
            return true;
        }

        Location loc = target.getLocation();

        // Try to remove the spawner
        if (spawner.removeSpawner(loc)) {
            player.sendMessage(ChatColor.GREEN + "Spawner removed successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }

        return true;
    }

    /**
     * Handle the list subcommand
     */
    private boolean handleListCommand(Player player, String[] args) {
        Map<Location, String> spawners = spawner.getAllSpawners();

        if (spawners.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No spawners found.");
            return true;
        }

        // Check for group filter
        String groupFilter = null;
        if (args.length > 1 && args[1].startsWith("group:")) {
            groupFilter = args[1].substring(6);

            // Adjust args for pagination
            if (args.length > 2) {
                args = new String[]{"list", args[2]};
            } else {
                args = new String[]{"list"};
            }
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid page number: " + args[1]);
                return true;
            }
        }

        // Filter by group if specified
        List<Map.Entry<Location, String>> spawnerList;

        if (groupFilter != null) {
            // Get spawners in the specified group
            List<Location> groupSpawners = spawner.findSpawnersInGroup(groupFilter);
            spawnerList = new ArrayList<>();

            for (Location groupLoc : groupSpawners) {
                String data = spawners.get(groupLoc);
                if (data != null) {
                    spawnerList.add(Map.entry(groupLoc, data));
                }
            }

            player.sendMessage(ChatColor.GOLD + "===== Spawners in Group '" + groupFilter +
                    "' (" + spawnerList.size() + " total) =====");
        } else {
            spawnerList = new ArrayList<>(spawners.entrySet());
            player.sendMessage(ChatColor.GOLD + "===== All Spawners (" + spawnerList.size() + " total) =====");
        }

        int spawnerCount = spawnerList.size();
        int spawnerPerPage = 5;
        int maxPage = Math.max(1, (int) Math.ceil(spawnerCount / (double) spawnerPerPage));

        if (page < 1 || page > maxPage) {
            player.sendMessage(ChatColor.RED + "Invalid page number. Valid range: 1-" + maxPage);
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "Page " + page + "/" + maxPage);

        int start = (page - 1) * spawnerPerPage;
        int end = Math.min(start + spawnerPerPage, spawnerCount);

        for (int i = start; i < end; i++) {
            Map.Entry<Location, String> entry = spawnerList.get(i);
            Location loc = entry.getKey();
            String data = entry.getValue();

            // Get visibility status
            boolean isVisible = spawner.isSpawnerVisible(loc);
            String visibilityStatus = isVisible ?
                    ChatColor.GREEN + "[Visible]" : ChatColor.RED + "[Hidden]";

            // Get properties
            SpawnerProperties props = spawner.getSpawnerProperties(loc);
            String groupName = props != null && !props.getSpawnerGroup().isEmpty() ?
                    " " + ChatColor.AQUA + "[" + props.getSpawnerGroup() + "]" : "";

            // Get active mob count
            int activeMobs = spawner.getActiveMobCount(loc);
            String activeStatus = activeMobs > 0 ?
                    " " + ChatColor.RED + "(" + activeMobs + " active)" : "";

            player.sendMessage(ChatColor.YELLOW.toString() + (i + 1) + ". " +
                    ChatColor.GRAY.toString() + loc.getWorld().getName() + " [" +
                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "] " +
                    visibilityStatus + groupName + activeStatus + " " +
                    ChatColor.WHITE.toString() + formatSpawnerData(data));
        }

        if (page < maxPage) {
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/spawner list " +
                    (groupFilter != null ? "group:" + groupFilter + " " : "") + (page + 1) +
                    ChatColor.GRAY + " to see the next page.");
        }

        return true;
    }

    /**
     * Handle the info subcommand
     */
    private boolean handleInfoCommand(Player player) {
        // Check if player is looking at a block
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to get info.");
            return true;
        }

        Location loc = target.getLocation();

        // Send detailed info
        spawner.sendSpawnerInfo(player, loc);
        return true;
    }

    /**
     * Handle the visibility subcommand
     */
    private boolean handleVisibilityCommand(Player player, String[] args) {
        boolean show;
        int radius = DEFAULT_VISIBILITY_RADIUS;

        // Handle variations of the command
        if (args[0].equalsIgnoreCase("show")) {
            show = true;
            if (args.length > 1) {
                try {
                    radius = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    // Just use default
                }
            }
        } else if (args[0].equalsIgnoreCase("hide")) {
            show = false;
            if (args.length > 1) {
                try {
                    radius = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    // Just use default
                }
            }
        } else if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner visibility <show|hide> [radius]");
            return true;
        } else {
            String action = args[1].toLowerCase();

            if (action.equals("show")) {
                show = true;
            } else if (action.equals("hide")) {
                show = false;
            } else {
                player.sendMessage(ChatColor.RED + "Invalid option. Use 'show' or 'hide'.");
                return true;
            }

            // Get radius if specified
            if (args.length > 2) {
                try {
                    radius = Integer.parseInt(args[2]);
                    if (radius <= 0) {
                        player.sendMessage(ChatColor.RED + "Radius must be greater than 0.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid radius: " + args[2]);
                    return true;
                }
            }
        }

        // Check if targeting a specific spawner
        Block target = player.getTargetBlock(null, 5);
        if (target != null && (target.getType() == Material.SPAWNER ||
                (target.getType() == Material.AIR && target.hasMetadata("isSpawner")))) {
            // Toggle just this spawner
            if (spawner.setSpawnerVisibility(target.getLocation(), show)) {
                player.sendMessage(ChatColor.GREEN + "Spawner " +
                        (show ? "shown" : "hidden") + " successfully!");
            } else {
                player.sendMessage(ChatColor.RED + "No spawner found at that location.");
            }
            return true;
        }

        // Toggle spawners in radius
        int count = spawner.toggleSpawnerVisibility(player.getLocation(), radius, show);

        if (count > 0) {
            player.sendMessage(ChatColor.GREEN + "Successfully " +
                    (show ? "showed" : "hid") + " " +
                    count + " spawners within " + radius + " blocks.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "No spawners found within " + radius + " blocks.");
        }

        return true;
    }

    /**
     * Handle the reset subcommand
     */
    private boolean handleResetCommand(Player player, String[] args) {
        // Check if targeting a specific spawner or requesting a group reset
        if (args.length > 1 && args[1].startsWith("group:")) {
            // Group reset
            String groupName = args[1].substring(6);
            List<Location> groupSpawners = spawner.findSpawnersInGroup(groupName);

            if (groupSpawners.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No spawners found in group: " + groupName);
                return true;
            }

            int count = 0;
            for (Location loc : groupSpawners) {
                if (spawner.resetSpawner(loc)) {
                    count++;
                }
            }

            player.sendMessage(ChatColor.GREEN + "Reset " + count + " spawners in group: " + groupName);
            return true;
        }

        // Check for all option
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            spawner.resetAllSpawners();
            player.sendMessage(ChatColor.GREEN + "Reset all spawners successfully!");
            return true;
        }

        // Reset specific spawner
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to reset it.");
            return true;
        }

        Location loc = target.getLocation();

        if (spawner.resetSpawner(loc)) {
            player.sendMessage(ChatColor.GREEN + "Spawner reset successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }

        return true;
    }

    /**
     * Handle the visualize subcommand
     */
    private boolean handleVisualizeCommand(Player player) {
        // Check if targeting a specific spawner
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to visualize it.");
            return true;
        }

        Location loc = target.getLocation();

        if (spawner.visualizeSpawnerForPlayer(player, loc)) {
            player.sendMessage(ChatColor.GREEN + "Visualization created!");
            player.sendMessage(ChatColor.GRAY + "Will automatically remove after 10 seconds.");
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }

        return true;
    }

    /**
     * Handle the property subcommand
     */
    private boolean handlePropertyCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner property <property> <value>");
            player.sendMessage(ChatColor.GRAY + "Available properties: timeRestricted, startHour, endHour, " +
                    "weatherRestricted, spawnInClear, spawnInRain, spawnInThunder, spawnerGroup, " +
                    "spawnRadiusX, spawnRadiusY, spawnRadiusZ, maxMobOverride, playerDetectionRangeOverride, displayName");
            return true;
        }

        String property = args[1];
        String value = args[2];

        // For multi-word values, combine remaining args
        if (args.length > 3) {
            StringBuilder valueBuilder = new StringBuilder(value);
            for (int i = 3; i < args.length; i++) {
                valueBuilder.append(" ").append(args[i]);
            }
            value = valueBuilder.toString();
        }

        // Check if targeting a group
        if (property.startsWith("group:")) {
            String groupName = property.substring(6);
            List<Location> groupSpawners = spawner.findSpawnersInGroup(groupName);

            if (groupSpawners.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No spawners found in group: " + groupName);
                return true;
            }

            // Extract actual property name
            String actualProperty = args[2];
            String actualValue = args.length > 3 ? args[3] : "";

            // For multi-word values, combine remaining args
            if (args.length > 4) {
                StringBuilder valueBuilder = new StringBuilder(actualValue);
                for (int i = 4; i < args.length; i++) {
                    valueBuilder.append(" ").append(args[i]);
                }
                actualValue = valueBuilder.toString();
            }

            // Apply property to all spawners in group
            int count = 0;
            for (Location loc : groupSpawners) {
                if (spawner.setSpawnerProperty(loc, actualProperty, actualValue)) {
                    count++;
                }
            }

            player.sendMessage(ChatColor.GREEN + "Set property " + actualProperty + "=" + actualValue +
                    " for " + count + " spawners in group: " + groupName);
            return true;
        }

        // Check if targeting a specific spawner
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to set properties.");
            return true;
        }

        Location loc = target.getLocation();

        // Get current value before changing
        Object currentValue = spawner.getSpawnerProperty(loc, property);

        if (spawner.setSpawnerProperty(loc, property, value)) {
            player.sendMessage(ChatColor.GREEN + "Property updated successfully!");
            player.sendMessage(ChatColor.GRAY + property + ": " + formatPropertyValue(currentValue) +
                    ChatColor.GRAY + " -> " + ChatColor.YELLOW + value);

            // Ensure data is saved
            spawner.saveSpawners();
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update property. Check the property name and value.");
        }

        return true;
    }

    /**
     * Format a property value for display
     */
    private String formatPropertyValue(Object value) {
        if (value == null) {
            return ChatColor.RED + "null";
        }
        return ChatColor.YELLOW + value.toString();
    }

    /**
     * Handle the group subcommand
     */
    private boolean handleGroupCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner group <list|create|add|remove|toggle> [args]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                // List all groups
                Set<String> groups = spawner.getAllSpawnerGroups();
                if (groups.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "No spawner groups found.");
                } else {
                    player.sendMessage(ChatColor.GOLD + "===== Spawner Groups =====");
                    for (String group : groups) {
                        List<Location> groupSpawners = spawner.findSpawnersInGroup(group);
                        player.sendMessage(ChatColor.YELLOW + group + ChatColor.GRAY +
                                " (" + groupSpawners.size() + " spawners)");
                    }
                }
                break;

            case "create":
                // Create a new group
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /spawner group create <name>");
                    return true;
                }

                String newGroup = args[2];

                // Set group property on the spawner being looked at
                Block target = player.getTargetBlock(null, 5);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "You must be looking at a spawner to add it to a group.");
                    return true;
                }

                Location loc = target.getLocation();

                if (spawner.setSpawnerProperty(loc, "spawnerGroup", newGroup)) {
                    player.sendMessage(ChatColor.GREEN + "Spawner added to group: " + newGroup);
                    spawner.saveSpawners();
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to add spawner to group.");
                }
                break;

            case "add":
                // Add a spawner to an existing group
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /spawner group add <name>");
                    return true;
                }

                String addGroup = args[2];

                // Set group property on the spawner being looked at
                Block addTarget = player.getTargetBlock(null, 5);
                if (addTarget == null) {
                    player.sendMessage(ChatColor.RED + "You must be looking at a spawner to add it to a group.");
                    return true;
                }

                Location addLoc = addTarget.getLocation();

                if (spawner.setSpawnerProperty(addLoc, "spawnerGroup", addGroup)) {
                    player.sendMessage(ChatColor.GREEN + "Spawner added to group: " + addGroup);
                    spawner.saveSpawners();
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to add spawner to group.");
                }
                break;

            case "remove":
                // Remove a spawner from its group
                Block removeTarget = player.getTargetBlock(null, 5);
                if (removeTarget == null) {
                    player.sendMessage(ChatColor.RED + "You must be looking at a spawner to remove it from a group.");
                    return true;
                }

                Location removeLoc = removeTarget.getLocation();

                if (spawner.setSpawnerProperty(removeLoc, "spawnerGroup", "")) {
                    player.sendMessage(ChatColor.GREEN + "Spawner removed from group.");
                    spawner.saveSpawners();
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to remove spawner from group.");
                }
                break;

            case "toggle":
                // Toggle a group's enabled/disabled state
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /spawner group toggle <name> <enable|disable>");
                    return true;
                }

                String toggleGroup = args[2];
                boolean enable = args.length < 4 || !args[3].equalsIgnoreCase("disable");

                int count = spawner.toggleSpawnerGroupEnabled(toggleGroup, enable);
                if (count > 0) {
                    player.sendMessage(ChatColor.GREEN + (enable ? "Enabled" : "Disabled") +
                            " " + count + " spawners in group: " + toggleGroup);
                } else {
                    player.sendMessage(ChatColor.RED + "No spawners found in group: " + toggleGroup);
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown group action: " + action);
                player.sendMessage(ChatColor.RED + "Usage: /spawner group <list|create|add|remove|toggle> [args]");
                break;
        }

        return true;
    }

    /**
     * Handle the template subcommand
     */
    private boolean handleTemplateCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner template <list|info|create> [args]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                // List all templates
                Map<String, String> templates = spawner.getAllTemplates();
                if (templates.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "No templates found.");
                } else {
                    player.sendMessage(ChatColor.GOLD + "===== Spawner Templates =====");
                    for (String templateName : templates.keySet()) {
                        player.sendMessage(ChatColor.YELLOW + templateName);
                    }
                    player.sendMessage(ChatColor.GRAY + "Use '/spawner template info <name>' for details.");
                }
                break;

            case "info":
                // Show template details
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /spawner template info <name>");
                    return true;
                }

                String templateName = args[2];
                Map<String, String> allTemplates = spawner.getAllTemplates();

                if (!allTemplates.containsKey(templateName)) {
                    player.sendMessage(ChatColor.RED + "Template not found: " + templateName);
                    player.sendMessage(ChatColor.GRAY + "Available templates: " +
                            String.join(", ", allTemplates.keySet()));
                    return true;
                }

                String templateData = allTemplates.get(templateName);
                player.sendMessage(ChatColor.GOLD + "===== Template: " + templateName + " =====");
                player.sendMessage(ChatColor.GRAY + "Data: " + templateData);
                player.sendMessage(ChatColor.GRAY + "Mobs:");

                // Parse and display mob data
                String[] mobEntries = templateData.split(",");
                for (String entry : mobEntries) {
                    try {
                        String[] parts = entry.split(":");
                        String mobType = parts[0];

                        String[] tierParts = parts[1].split("@");
                        int tier = Integer.parseInt(tierParts[0]);

                        String[] eliteParts = tierParts[1].split("#");
                        boolean elite = Boolean.parseBoolean(eliteParts[0]);
                        int amount = Integer.parseInt(eliteParts[1]);

                        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.YELLOW + mobType +
                                ChatColor.GRAY + " (Tier " + tier + ", " +
                                (elite ? "Elite" : "Regular") + ") x" + amount);
                    } catch (Exception e) {
                        player.sendMessage(ChatColor.GRAY + "- " + ChatColor.RED + entry + " (error parsing)");
                    }
                }
                break;

            case "create":
                // This would be used for dynamic template creation
                player.sendMessage(ChatColor.RED + "This feature is coming soon!");
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown template action: " + action);
                player.sendMessage(ChatColor.RED + "Usage: /spawner template <list|info|create> [args]");
                break;
        }

        return true;
    }

    /**
     * Handle the wand subcommand
     */
    private boolean handleWandCommand(Player player) {
        // Give player a spawner control wand
        ItemStack wand = createSpawnerWand();
        player.getInventory().addItem(wand);

        player.sendMessage(ChatColor.GREEN + "You have been given a Spawner Control Wand!");
        player.sendMessage(ChatColor.GRAY + "Right-click on a block to create a spawner");
        player.sendMessage(ChatColor.GRAY + "Left-click a spawner to remove it");
        player.sendMessage(ChatColor.GRAY + "Shift+right-click to view spawner controls");

        return true;
    }

    /**
     * Create a spawner wand item
     */
    private ItemStack createSpawnerWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "Spawner Control Wand");
        meta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Right-click on a block to create a spawner",
                ChatColor.YELLOW + "Left-click a spawner to remove it",
                ChatColor.YELLOW + "Shift+right-click to view controls"
        ));

        // Add persistent data so we can identify this item as a wand
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);

        wand.setItemMeta(meta);
        return wand;
    }

    /**
     * Handle the wand use event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Skip if not using main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Check if player is using a wand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !isWandItem(item)) return;

        // Check permission
        if (!player.hasPermission("yakrealms.admin.spawner")) return;

        // Cancel the event to prevent normal interaction
        event.setCancelled(true);

        // Handle different interactions
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Shift+right click for spawner info/controls
            if (player.isSneaking()) {
                // Check if this is a spawner
                if (isSpawnerBlock(clickedBlock)) {
                    // Show spawner control panel
                    spawner.createSpawnerControlPanel(player, clickedBlock.getLocation());
                } else {
                    player.sendMessage(ChatColor.RED + "No spawner found at this location.");
                }
            } else {
                // Regular right click - guided spawner creation
                // Make sure this isn't already a spawner
                if (isSpawnerBlock(clickedBlock)) {
                    player.sendMessage(ChatColor.RED + "There is already a spawner at this location.");
                    return;
                }

                // Start guided creation
                spawner.beginGuidedSpawnerCreation(player, clickedBlock.getLocation());
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Left click to remove spawner
            if (isSpawnerBlock(clickedBlock)) {
                if (spawner.removeSpawner(clickedBlock.getLocation())) {
                    player.sendMessage(ChatColor.GREEN + "Spawner removed successfully!");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to remove spawner.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "No spawner found at this location.");
            }
        }
    }

    /**
     * Critical addition: Process player chat for spawner creation and property editing
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Check if player is in a spawner creation session
        if (spawner.hasActiveCreationSession(playerName)) {
            event.setCancelled(true); // Cancel the chat message

            // We need to schedule this on the main thread as some operations require it
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawner.processCreationChat(player, event.getMessage());
            });
            return;
        }

        // Check if player is in a property editing session
        PropertyEditSession session = propertyEditors.get(playerName);
        if (session != null) {
            event.setCancelled(true); // Cancel the chat message

            // Schedule on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                processPropertyEdit(player, event.getMessage(), session);
            });
        }
    }

    /**
     * Process property editing chat
     *
     * @param player  The player
     * @param message The chat message
     * @param session The edit session
     */
    private void processPropertyEdit(Player player, String message, PropertyEditSession session) {
        // Check for cancellation
        if (message.equalsIgnoreCase("cancel")) {
            propertyEditors.remove(player.getName());
            player.sendMessage(ChatColor.RED + "Property editing cancelled.");
            return;
        }

        // If no property selected yet, handle property selection
        if (session.getCurrentProperty() == null) {
            handlePropertySelection(player, message, session);
        } else {
            // Handle property value input
            handlePropertyValueInput(player, message, session);
        }
    }

    /**
     * Handle property selection during editing
     */
    private void handlePropertySelection(Player player, String message, PropertyEditSession session) {
        try {
            int selection = Integer.parseInt(message);

            switch (selection) {
                case 1: // Display Name
                    session.setCurrentProperty("displayName");
                    player.sendMessage(ChatColor.YELLOW + "Enter a new display name for the spawner:");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(session.getSpawnerLocation(), "displayName")));
                    break;
                case 2: // Group
                    session.setCurrentProperty("spawnerGroup");
                    player.sendMessage(ChatColor.YELLOW + "Enter a group name for the spawner:");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(session.getSpawnerLocation(), "spawnerGroup")));
                    break;
                case 3: // Time Restrictions
                    handleTimeRestrictionSelection(player, session);
                    break;
                case 4: // Weather Restrictions
                    handleWeatherRestrictionSelection(player, session);
                    break;
                case 5: // Spawn Radius
                    handleSpawnRadiusSelection(player, session);
                    break;
                case 6: // Detection Range
                    session.setCurrentProperty("playerDetectionRangeOverride");
                    player.sendMessage(ChatColor.YELLOW + "Enter a detection range override (or -1 for default):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(session.getSpawnerLocation(), "playerDetectionRangeOverride")));
                    break;
                case 7: // Max Mobs
                    session.setCurrentProperty("maxMobOverride");
                    player.sendMessage(ChatColor.YELLOW + "Enter a max mob override (or -1 for default):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(session.getSpawnerLocation(), "maxMobOverride")));
                    break;
                case 8: // Display Mode
                    session.setCurrentProperty("displayMode");
                    player.sendMessage(ChatColor.YELLOW + "Enter display mode (0=Basic, 1=Detailed, 2=Admin):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerDisplayMode(session.getSpawnerLocation())));
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Invalid selection. Please enter a number 1-8 or 'cancel'.");
                    break;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a number 1-8 or 'cancel'.");
        }
    }

    /**
     * Handle time restriction selection submenu
     */
    private void handleTimeRestrictionSelection(Player player, PropertyEditSession session) {
        session.setCurrentProperty("timeMenu");
        player.sendMessage(ChatColor.YELLOW + "Time Restriction Options:");
        player.sendMessage(ChatColor.YELLOW + "1. " + ChatColor.WHITE + "Enable/Disable Time Restrictions");
        player.sendMessage(ChatColor.YELLOW + "2. " + ChatColor.WHITE + "Set Start Hour (0-23)");
        player.sendMessage(ChatColor.YELLOW + "3. " + ChatColor.WHITE + "Set End Hour (0-23)");
        player.sendMessage(ChatColor.GRAY + "Enter option number:");
    }

    /**
     * Handle weather restriction selection submenu
     */
    private void handleWeatherRestrictionSelection(Player player, PropertyEditSession session) {
        session.setCurrentProperty("weatherMenu");
        player.sendMessage(ChatColor.YELLOW + "Weather Restriction Options:");
        player.sendMessage(ChatColor.YELLOW + "1. " + ChatColor.WHITE + "Enable/Disable Weather Restrictions");
        player.sendMessage(ChatColor.YELLOW + "2. " + ChatColor.WHITE + "Allow Spawning in Clear Weather");
        player.sendMessage(ChatColor.YELLOW + "3. " + ChatColor.WHITE + "Allow Spawning in Rain");
        player.sendMessage(ChatColor.YELLOW + "4. " + ChatColor.WHITE + "Allow Spawning in Thunder");
        player.sendMessage(ChatColor.GRAY + "Enter option number:");
    }

    /**
     * Handle spawn radius selection submenu
     */
    private void handleSpawnRadiusSelection(Player player, PropertyEditSession session) {
        session.setCurrentProperty("radiusMenu");
        player.sendMessage(ChatColor.YELLOW + "Spawn Radius Options:");
        player.sendMessage(ChatColor.YELLOW + "1. " + ChatColor.WHITE + "Set X Radius");
        player.sendMessage(ChatColor.YELLOW + "2. " + ChatColor.WHITE + "Set Y Radius");
        player.sendMessage(ChatColor.YELLOW + "3. " + ChatColor.WHITE + "Set Z Radius");
        player.sendMessage(ChatColor.GRAY + "Enter option number:");
    }

    /**
     * Handle property value input during editing
     */
    private void handlePropertyValueInput(Player player, String message, PropertyEditSession session) {
        String property = session.getCurrentProperty();
        Location location = session.getSpawnerLocation();

        // Handle submenus
        if (property.equals("timeMenu")) {
            handleTimeMenuInput(player, message, session);
            return;
        } else if (property.equals("weatherMenu")) {
            handleWeatherMenuInput(player, message, session);
            return;
        } else if (property.equals("radiusMenu")) {
            handleRadiusMenuInput(player, message, session);
            return;
        }

        // Set the property
        if (spawner.setSpawnerProperty(location, property, message)) {
            player.sendMessage(ChatColor.GREEN + "Property '" + property + "' set to: " + ChatColor.YELLOW + message);

            // For display mode, also need to call setSpawnerDisplayMode
            if (property.equals("displayMode")) {
                try {
                    int mode = Integer.parseInt(message);
                    spawner.setSpawnerDisplayMode(location, mode);
                } catch (NumberFormatException ignored) {
                }
            }

            // Property set, ask for another property
            player.sendMessage(ChatColor.GREEN + "Property updated successfully!");
            propertyEditors.remove(player.getName());

            // Prompt if they want to edit another property
            player.sendMessage(ChatColor.YELLOW + "Type '/spawner edit' to edit another property.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to set property. Please try again.");
        }
    }

    /**
     * Handle time menu input
     */
    private void handleTimeMenuInput(Player player, String message, PropertyEditSession session) {
        try {
            int option = Integer.parseInt(message);
            Location location = session.getSpawnerLocation();

            switch (option) {
                case 1: // Enable/Disable Time Restrictions
                    session.setCurrentProperty("timeRestricted");
                    player.sendMessage(ChatColor.YELLOW + "Enable time restrictions? (true/false)");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "timeRestricted")));
                    break;
                case 2: // Set Start Hour
                    session.setCurrentProperty("startHour");
                    player.sendMessage(ChatColor.YELLOW + "Enter start hour (0-23):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "startHour")));
                    break;
                case 3: // Set End Hour
                    session.setCurrentProperty("endHour");
                    player.sendMessage(ChatColor.YELLOW + "Enter end hour (0-23):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "endHour")));
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Invalid option. Please enter 1-3 or 'cancel'.");
                    break;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid option number.");
        }
    }

    /**
     * Handle weather menu input
     */
    private void handleWeatherMenuInput(Player player, String message, PropertyEditSession session) {
        try {
            int option = Integer.parseInt(message);
            Location location = session.getSpawnerLocation();

            switch (option) {
                case 1: // Enable/Disable Weather Restrictions
                    session.setCurrentProperty("weatherRestricted");
                    player.sendMessage(ChatColor.YELLOW + "Enable weather restrictions? (true/false)");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "weatherRestricted")));
                    break;
                case 2: // Allow Clear
                    session.setCurrentProperty("spawnInClear");
                    player.sendMessage(ChatColor.YELLOW + "Allow spawning in clear weather? (true/false)");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "spawnInClear")));
                    break;
                case 3: // Allow Rain
                    session.setCurrentProperty("spawnInRain");
                    player.sendMessage(ChatColor.YELLOW + "Allow spawning in rain? (true/false)");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "spawnInRain")));
                    break;
                case 4: // Allow Thunder
                    session.setCurrentProperty("spawnInThunder");
                    player.sendMessage(ChatColor.YELLOW + "Allow spawning in thunder? (true/false)");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "spawnInThunder")));
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Invalid option. Please enter 1-4 or 'cancel'.");
                    break;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid option number.");
        }
    }

    /**
     * Handle radius menu input
     */
    private void handleRadiusMenuInput(Player player, String message, PropertyEditSession session) {
        try {
            int option = Integer.parseInt(message);
            Location location = session.getSpawnerLocation();

            switch (option) {
                case 1: // X Radius
                    session.setCurrentProperty("spawnRadiusX");
                    player.sendMessage(ChatColor.YELLOW + "Enter X radius (1.0-10.0):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "spawnRadiusX")));
                    break;
                case 2: // Y Radius
                    session.setCurrentProperty("spawnRadiusY");
                    player.sendMessage(ChatColor.YELLOW + "Enter Y radius (1.0-5.0):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "spawnRadiusY")));
                    break;
                case 3: // Z Radius
                    session.setCurrentProperty("spawnRadiusZ");
                    player.sendMessage(ChatColor.YELLOW + "Enter Z radius (1.0-10.0):");
                    player.sendMessage(ChatColor.GRAY + "Current value: " +
                            formatPropertyValue(spawner.getSpawnerProperty(location, "spawnRadiusZ")));
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Invalid option. Please enter 1-3 or 'cancel'.");
                    break;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a valid option number.");
        }
    }

    /**
     * Check if a block is a spawner (visible or hidden)
     */
    private boolean isSpawnerBlock(Block block) {
        return block.getType() == Material.SPAWNER ||
                (block.getType() == Material.AIR && block.hasMetadata("isSpawner"));
    }

    /**
     * Check if an item is a spawner wand
     */
    private boolean isWandItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    /**
     * Handle the display mode subcommand
     */
    private boolean handleDisplayModeCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner display <0|1|2>");
            player.sendMessage(ChatColor.GRAY + "0 = Basic, 1 = Detailed, 2 = Admin");
            return true;
        }

        int mode;
        try {
            mode = Integer.parseInt(args[1]);
            if (mode < 0 || mode > 2) {
                player.sendMessage(ChatColor.RED + "Invalid display mode. Use 0, 1, or 2.");
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid display mode. Use 0, 1, or 2.");
            return true;
        }

        // Get the target block
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to change its display mode.");
            return true;
        }

        Location loc = target.getLocation();

        if (spawner.setSpawnerDisplayMode(loc, mode)) {
            String modeText;
            switch (mode) {
                case 0:
                    modeText = "Basic";
                    break;
                case 1:
                    modeText = "Detailed";
                    break;
                case 2:
                    modeText = "Admin";
                    break;
                default:
                    modeText = "Unknown";
                    break;
            }

            player.sendMessage(ChatColor.GREEN + "Display mode set to: " + modeText);
            spawner.saveSpawners();
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }

        return true;
    }

    /**
     * Handle the edit command for guided property editing
     */
    private boolean handleEditCommand(Player player) {
        // Check if targeting a spawner
        Block target = player.getTargetBlock(null, 5);
        if (target == null || !isSpawnerBlock(target)) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to edit it.");
            return true;
        }

        Location loc = target.getLocation();

        // Start property editing session
        PropertyEditSession session = new PropertyEditSession(player, loc);
        propertyEditors.put(player.getName(), session);

        // Show property categories
        player.sendMessage(ChatColor.GREEN + "=== Spawner Property Editor ===");
        player.sendMessage(ChatColor.GRAY + "Choose a property to edit:");
        player.sendMessage(ChatColor.YELLOW + "1. " + ChatColor.WHITE + "Display Name");
        player.sendMessage(ChatColor.YELLOW + "2. " + ChatColor.WHITE + "Group");
        player.sendMessage(ChatColor.YELLOW + "3. " + ChatColor.WHITE + "Time Restrictions");
        player.sendMessage(ChatColor.YELLOW + "4. " + ChatColor.WHITE + "Weather Restrictions");
        player.sendMessage(ChatColor.YELLOW + "5. " + ChatColor.WHITE + "Spawn Radius");
        player.sendMessage(ChatColor.YELLOW + "6. " + ChatColor.WHITE + "Detection Range");
        player.sendMessage(ChatColor.YELLOW + "7. " + ChatColor.WHITE + "Max Mobs");
        player.sendMessage(ChatColor.YELLOW + "8. " + ChatColor.WHITE + "Display Mode");
        player.sendMessage(ChatColor.GRAY + "Type a number to select, or 'cancel' to exit.");

        return true;
    }

    /**
     * Handle the debug subcommand
     */
    private boolean handleDebugCommand(Player player) {
        // Toggle debug mode in MobSpawner
        boolean newMode = !spawner.isDebugMode();
        spawner.setDebugMode(newMode);

        player.sendMessage(ChatColor.GREEN + "Spawner debug mode: " +
                (newMode ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

        if (newMode) {
            // Print status to console
            spawner.printSpawnerStatus();
        }

        return true;
    }

    /**
     * Format spawner data for display
     */
    private String formatSpawnerData(String data) {
        if (data == null || data.isEmpty()) return "Unknown";

        // Simple formatting for display
        StringBuilder result = new StringBuilder();
        String[] parts = data.split(",");

        // Limit to first 3 entries to avoid long messages
        int displayLimit = Math.min(parts.length, 3);

        for (int i = 0; i < displayLimit; i++) {
            String part = parts[i];
            String[] typeInfo = part.split(":");

            if (typeInfo.length >= 2) {
                String mobType = typeInfo[0];
                String[] tierInfo = typeInfo[1].split("@");

                if (tierInfo.length >= 2) {
                    String tier = tierInfo[0];
                    String[] eliteParts = tierInfo[1].split("#");

                    if (eliteParts.length >= 2) {
                        boolean elite = Boolean.parseBoolean(eliteParts[0]);
                        int amount = Integer.parseInt(eliteParts[1]);

                        if (i > 0) result.append(", ");

                        result.append(capitalize(mobType))
                                .append(" T").append(tier)
                                .append(elite ? "+" : "")
                                .append("×").append(amount);
                    }
                }
            }
        }

        if (parts.length > displayLimit) {
            result.append(" +").append(parts.length - displayLimit).append(" more");
        }

        return result.toString();
    }

    /**
     * Capitalize first letter of a string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Update all hologram displays for visible spawners
     * This should be called periodically by the MobSpawner system
     */
    public void updateAllHolograms() {
        spawner.updateAllHolograms();
    }

    /**
     * Send help message to player
     */
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== Spawner Command Help =====");
        player.sendMessage(ChatColor.YELLOW + "/spawner create <data>" + ChatColor.GRAY + " - Create a spawner");
        player.sendMessage(ChatColor.YELLOW + "/spawner create template:<name>" + ChatColor.GRAY + " - Create from template");
        player.sendMessage(ChatColor.YELLOW + "/spawner remove" + ChatColor.GRAY + " - Remove a spawner you're looking at");
        player.sendMessage(ChatColor.YELLOW + "/spawner list [page]" + ChatColor.GRAY + " - List all spawners");
        player.sendMessage(ChatColor.YELLOW + "/spawner list group:<name> [page]" + ChatColor.GRAY + " - List spawners in group");
        player.sendMessage(ChatColor.YELLOW + "/spawner info" + ChatColor.GRAY + " - Get info about a spawner");
        player.sendMessage(ChatColor.YELLOW + "/spawner toggle <show|hide> [radius]" + ChatColor.GRAY + " - Toggle visibility");
        player.sendMessage(ChatColor.YELLOW + "/spawner reset" + ChatColor.GRAY + " - Reset spawner you're looking at");
        player.sendMessage(ChatColor.YELLOW + "/spawner reset all" + ChatColor.GRAY + " - Reset all spawners");
        player.sendMessage(ChatColor.YELLOW + "/spawner reset group:<name>" + ChatColor.GRAY + " - Reset group");
        player.sendMessage(ChatColor.YELLOW + "/spawner visualize" + ChatColor.GRAY + " - Visualize spawner ranges");
        player.sendMessage(ChatColor.YELLOW + "/spawner property <prop> <value>" + ChatColor.GRAY + " - Set property");
        player.sendMessage(ChatColor.YELLOW + "/spawner group <action> [args]" + ChatColor.GRAY + " - Manage groups");
        player.sendMessage(ChatColor.YELLOW + "/spawner template <action> [args]" + ChatColor.GRAY + " - Manage templates");
        player.sendMessage(ChatColor.YELLOW + "/spawner display <0|1|2>" + ChatColor.GRAY + " - Set display mode");
        player.sendMessage(ChatColor.YELLOW + "/spawner wand" + ChatColor.GRAY + " - Get spawner control wand");
        player.sendMessage(ChatColor.YELLOW + "/spawner edit" + ChatColor.GRAY + " - Start guided property editor");
        player.sendMessage(ChatColor.YELLOW + "/spawner debug" + ChatColor.GRAY + " - Toggle debug mode");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = Arrays.asList(
                    "create", "remove", "delete", "list", "info", "visibility", "toggle",
                    "show", "hide", "reset", "visualize", "property", "set", "group",
                    "template", "wand", "display", "edit", "debug", "help");

            StringUtil.copyPartialMatches(args[0], commands, completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                List<String> options = new ArrayList<>();
                options.add("skeleton:3@false#2");
                options.add("zombie:2@false#1");
                options.add("skeleton:5@true#1");
                options.add("witherskeleton:4@true#1");

                // Add templates
                for (String template : spawner.getAllTemplates().keySet()) {
                    options.add("template:" + template);
                }

                StringUtil.copyPartialMatches(args[1], options, completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("visibility") ||
                    args[0].equalsIgnoreCase("toggle")) {
                return Arrays.asList("show", "hide");
            } else if (args[0].equalsIgnoreCase("reset")) {
                List<String> options = new ArrayList<>();
                options.add("all");

                // Add groups
                for (String group : spawner.getAllSpawnerGroups()) {
                    options.add("group:" + group);
                }

                StringUtil.copyPartialMatches(args[1], options, completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("property") ||
                    args[0].equalsIgnoreCase("set")) {
                List<String> properties = Arrays.asList(
                        "timeRestricted", "startHour", "endHour", "weatherRestricted",
                        "spawnInClear", "spawnInRain", "spawnInThunder", "spawnerGroup",
                        "spawnRadiusX", "spawnRadiusY", "spawnRadiusZ", "maxMobOverride",
                        "playerDetectionRangeOverride", "displayName");

                // Add groups
                for (String group : spawner.getAllSpawnerGroups()) {
                    properties.add("group:" + group);
                }

                StringUtil.copyPartialMatches(args[1], properties, completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("group")) {
                return Arrays.asList("list", "create", "add", "remove", "toggle");
            } else if (args[0].equalsIgnoreCase("template")) {
                return Arrays.asList("list", "info", "create");
            } else if (args[0].equalsIgnoreCase("display")) {
                return Arrays.asList("0", "1", "2");
            } else if (args[0].equalsIgnoreCase("list")) {
                List<String> options = new ArrayList<>();

                // Add page numbers
                options.add("1");
                options.add("2");
                options.add("3");

                // Add groups
                for (String group : spawner.getAllSpawnerGroups()) {
                    options.add("group:" + group);
                }

                StringUtil.copyPartialMatches(args[1], options, completions);
                return completions;
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("template") && args[1].equalsIgnoreCase("info")) {
                List<String> templates = new ArrayList<>(spawner.getAllTemplates().keySet());
                StringUtil.copyPartialMatches(args[2], templates, completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("property") || args[0].equalsIgnoreCase("set")) {
                if (args[1].equalsIgnoreCase("timeRestricted") ||
                        args[1].equalsIgnoreCase("weatherRestricted") ||
                        args[1].equalsIgnoreCase("spawnInClear") ||
                        args[1].equalsIgnoreCase("spawnInRain") ||
                        args[1].equalsIgnoreCase("spawnInThunder")) {
                    return Arrays.asList("true", "false");
                } else if (args[1].equalsIgnoreCase("startHour") || args[1].equalsIgnoreCase("endHour")) {
                    return Arrays.asList("0", "6", "12", "18", "22");
                } else if (args[1].equalsIgnoreCase("spawnerGroup")) {
                    List<String> groups = new ArrayList<>(spawner.getAllSpawnerGroups());
                    groups.add(""); // Option to clear group
                    StringUtil.copyPartialMatches(args[2], groups, completions);
                    return completions;
                } else if (args[1].equalsIgnoreCase("displayName")) {
                    return Arrays.asList("Tier_1_Spawner", "Elite_Spawner", "Boss_Spawner", "");
                } else if (args[1].startsWith("group:")) {
                    return Arrays.asList("timeRestricted", "startHour", "endHour", "weatherRestricted",
                            "spawnInClear", "spawnInRain", "spawnInThunder", "displayName");
                }
            } else if (args[0].equalsIgnoreCase("group") && args[1].equalsIgnoreCase("toggle")) {
                List<String> groups = new ArrayList<>(spawner.getAllSpawnerGroups());
                StringUtil.copyPartialMatches(args[2], groups, completions);
                return completions;
            } else if (args[0].equalsIgnoreCase("visibility") || args[0].equalsIgnoreCase("toggle")) {
                return Arrays.asList("10", "20", "30", "50", "100");
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("group") && args[1].equalsIgnoreCase("toggle")) {
                return Arrays.asList("enable", "disable");
            }
        }

        return completions;
    }

    /**
     * String utility class for tab completion
     */
    private static class StringUtil {
        public static void copyPartialMatches(String token, Iterable<String> originals, Collection<String> collection) {
            for (String string : originals) {
                if (startsWithIgnoreCase(string, token)) {
                    collection.add(string);
                }
            }
        }

        public static boolean startsWithIgnoreCase(String string, String prefix) {
            if (string.length() < prefix.length()) {
                return false;
            }
            return string.regionMatches(true, 0, prefix, 0, prefix.length());
        }
    }
}
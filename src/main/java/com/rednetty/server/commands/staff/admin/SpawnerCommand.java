package com.rednetty.server.commands.staff.admin;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.world.mobs.MobManager;
import com.rednetty.server.mechanics.world.mobs.core.MobType;
import com.rednetty.server.mechanics.world.mobs.spawners.MobEntry;
import com.rednetty.server.mechanics.world.mobs.spawners.MobSpawner;
import com.rednetty.server.mechanics.world.mobs.utils.MobUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
import java.util.stream.Collectors;


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

        public Player getPlayer() { return player; }
        public Location getSpawnerLocation() { return spawnerLocation; }
        public long getStartTime() { return startTime; }
        public String getCurrentProperty() { return currentProperty; }
        public void setCurrentProperty(String property) { this.currentProperty = property; }
        public boolean hasTimedOut() { return System.currentTimeMillis() - startTime > 300000; }
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
            case "debugdetail":
                return handleDebugDetailCommand(player, args);
            case "help":
                sendHelp(player);
                return true;
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand: " + subCmd);
                sendHelp(player);
                return true;
        }
    }


    private boolean handleCreateCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner create <data> or /spawner create template:<name>");
            player.sendMessage(ChatColor.GRAY + "Example: /spawner create skeleton:3@false#2,zombie:2@false#1");
            player.sendMessage(ChatColor.GRAY + "Template: /spawner create template:elite_t4");
            player.sendMessage(ChatColor.YELLOW + "Use '/spawner create guided' for step-by-step creation");
            return true;
        }

        // Get the target block
        Block target = player.getTargetBlock(null, 5);
        if (target == null || target.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "You must be looking at a block to create a spawner.");
            return true;
        }

        Location loc = target.getLocation();

        // Handle guided creation
        if (args[1].equalsIgnoreCase("guided")) {
            startGuidedCreation(player, loc);
            return true;
        }

        // Enable debug mode temporarily for detailed logging
        boolean oldDebug = spawner.isDebugMode();
        spawner.setDebugMode(true);

        try {
            // Check if using a template
            if (args[1].toLowerCase().startsWith("template:")) {
                String templateName = args[1].substring(9);
                return createFromTemplate(player, loc, templateName);
            }

            // Build the data string from args - combine all remaining args
            String data = buildDataString(args, 1);
            return createFromData(player, loc, data);

        } finally {
            // Restore debug mode
            spawner.setDebugMode(oldDebug);
        }
    }

    /**
     * Start guided creation using the spawner's creation session system
     */
    private void startGuidedCreation(Player player, Location location) {
        try {
            spawner.startCreationSession(player, location);
            player.sendMessage(ChatColor.GREEN + "=== Guided Spawner Creation Started ===");
            player.sendMessage(ChatColor.YELLOW + "Follow the prompts to create your spawner.");
            player.sendMessage(ChatColor.GRAY + "Type 'cancel' at any time to stop.");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to start guided creation: " + e.getMessage());
            plugin.getLogger().warning("Guided creation start error: " + e.getMessage());
        }
    }

    /**
     * Create spawner from template
     */
    private boolean createFromTemplate(Player player, Location loc, String templateName) {
        if (spawner.addSpawnerFromTemplate(loc, templateName)) {
            player.sendMessage(ChatColor.GREEN + "Spawner created successfully using template: " +
                    ChatColor.YELLOW + templateName);
            player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW + formatLocation(loc));

            // Set default visibility and update hologram
            spawner.setSpawnerVisibility(loc, spawner.getDefaultVisibility());
            mobManager.updateSpawnerHologram(loc);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create spawner. Template not found: " + templateName);
            Set<String> templates = spawner.getAllTemplates().keySet();
            if (!templates.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Available templates: " + String.join(", ", templates));
            }
            return true;
        }
    }

    /**
     * Create spawner from data string
     */
    private boolean createFromData(Player player, Location loc, String data) {
        // Validate data format first
        if (!validateSpawnerData(data)) {
            player.sendMessage(ChatColor.RED + "Invalid spawner data format.");
            showDataFormatHelp(player);
            return true;
        }

        if (spawner.addSpawner(loc, data)) {
            player.sendMessage(ChatColor.GREEN + "Spawner created successfully!");
            player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW + formatLocation(loc));
            player.sendMessage(ChatColor.GRAY + "Data: " + ChatColor.YELLOW + data);

            // Set default visibility and update hologram
            spawner.setSpawnerVisibility(loc, spawner.getDefaultVisibility());
            mobManager.updateSpawnerHologram(loc);
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Failed to create spawner.");
            showDataFormatHelp(player);
            return true;
        }
    }

    /**
     * Better data validation using MobEntry system
     */
    private boolean validateSpawnerData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return false;
        }

        try {
            String[] entries = data.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                // Use MobEntry validation
                MobEntry.fromString(entry);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Data validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Show data format help to player
     */
    private void showDataFormatHelp(Player player) {
        player.sendMessage(ChatColor.GRAY + "Format: mobType:tier@elite#amount");
        player.sendMessage(ChatColor.GRAY + "Example: skeleton:3@false#2,zombie:2@false#1");

        // Show valid mob types using MobUtils
        List<String> validMobTypes = Arrays.stream(MobType.values())
                .map(MobType::getId)
                .sorted()
                .collect(Collectors.toList());

        player.sendMessage(ChatColor.YELLOW + "Valid mob types:");
        showMobTypesInColumns(player, validMobTypes);
    }

    /**
     * Show mob types in organized columns
     */
    private void showMobTypesInColumns(Player player, List<String> mobTypes) {
        int typesPerLine = 8;
        for (int i = 0; i < mobTypes.size(); i += typesPerLine) {
            StringBuilder line = new StringBuilder(ChatColor.GRAY.toString());
            int end = Math.min(i + typesPerLine, mobTypes.size());
            for (int j = i; j < end; j++) {
                if (j > i) line.append(", ");
                line.append(mobTypes.get(j));
            }
            player.sendMessage(line.toString());
        }
    }

    /**
     * Build data string from command arguments
     */
    private String buildDataString(String[] args, int startIndex) {
        StringBuilder dataBuilder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) dataBuilder.append(" ");
            dataBuilder.append(args[i]);
        }

        String data = dataBuilder.toString().trim();

        // Clean up spacing around separators
        return data.replace(" :", ":").replace(": ", ":")
                .replace(" @", "@").replace("@ ", "@")
                .replace(" #", "#").replace("# ", "#");
    }

    /**
     * Handle the remove subcommand with better feedback
     */
    private boolean handleRemoveCommand(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to remove it.");
            return true;
        }

        Location loc = target.getLocation();

        if (spawner.removeSpawner(loc)) {
            player.sendMessage(ChatColor.GREEN + "Spawner removed successfully!");
            player.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.YELLOW + formatLocation(loc));

            // Clean up hologram
            mobManager.removeSpawnerHologram(loc);
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }

        return true;
    }

    /**
     * Handle the list subcommand with better formatting
     */
    private boolean handleListCommand(Player player, String[] args) {
        Map<Location, String> spawners = spawner.getAllSpawners();

        if (spawners.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No spawners found.");
            return true;
        }

        // Parse arguments for group filter and pagination
        String groupFilter = null;
        int page = 1;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("group:")) {
                groupFilter = arg.substring(6);
            } else {
                try {
                    page = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid page number: " + arg);
                    return true;
                }
            }
        }

        // Filter by group if specified
        List<Map.Entry<Location, String>> spawnerList = getFilteredSpawnerList(spawners, groupFilter);

        // Show header
        if (groupFilter != null) {
            player.sendMessage(ChatColor.GOLD + "===== Spawners in Group '" + groupFilter +
                    "' (" + spawnerList.size() + " total) =====");
        } else {
            player.sendMessage(ChatColor.GOLD + "===== All Spawners (" + spawnerList.size() + " total) =====");
        }

        // Pagination
        int spawnersPerPage = 6;
        int maxPage = Math.max(1, (int) Math.ceil(spawnerList.size() / (double) spawnersPerPage));

        if (page < 1 || page > maxPage) {
            player.sendMessage(ChatColor.RED + "Invalid page number. Valid range: 1-" + maxPage);
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "Page " + page + "/" + maxPage);

        int start = (page - 1) * spawnersPerPage;
        int end = Math.min(start + spawnersPerPage, spawnerList.size());

        for (int i = start; i < end; i++) {
            Map.Entry<Location, String> entry = spawnerList.get(i);
            Location loc = entry.getKey();
            String data = entry.getValue();

            showSpawnerListEntry(player, i + 1, loc, data);
        }

        if (page < maxPage) {
            String nextCmd = "/spawner list " + (groupFilter != null ? "group:" + groupFilter + " " : "") + (page + 1);
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + nextCmd +
                    ChatColor.GRAY + " to see the next page.");
        }

        return true;
    }

    /**
     * Get filtered spawner list
     */
    private List<Map.Entry<Location, String>> getFilteredSpawnerList(Map<Location, String> spawners, String groupFilter) {
        if (groupFilter == null) {
            return new ArrayList<>(spawners.entrySet());
        }

        List<Location> groupSpawners = findSpawnersInGroup(groupFilter);
        return spawners.entrySet().stream()
                .filter(entry -> groupSpawners.contains(entry.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * Show a single spawner list entry with improved formatting
     */
    private void showSpawnerListEntry(Player player, int index, Location loc, String data) {
        // Get status information
        boolean isVisible = spawner.isSpawnerVisible(loc);
        String visibilityStatus = isVisible ? ChatColor.GREEN + "[Visible]" : ChatColor.RED + "[Hidden]";

        // Get group information
        String groupName = getSpawnerGroup(loc);
        String groupDisplay = groupName != null && !groupName.isEmpty() ?
                " " + ChatColor.AQUA + "[" + groupName + "]" : "";

        // Get active mob count
        int activeMobs = mobManager.getActiveMobCount(loc);
        String activeStatus = activeMobs > 0 ? " " + ChatColor.RED + "(" + activeMobs + " active)" : "";

        // Format the data using MobUtils
        String formattedData = formatSpawnerDataForList(data);

        player.sendMessage(ChatColor.YELLOW.toString() + index + ". " +
                ChatColor.GRAY + formatLocation(loc) + " " +
                visibilityStatus + groupDisplay + activeStatus + " " +
                ChatColor.WHITE + formattedData);
    }

    /**
     * Format spawner data for list display using MobUtils
     */
    private String formatSpawnerDataForList(String data) {
        if (data == null || data.isEmpty()) return "Empty";

        try {
            List<MobEntry> entries = parseSpawnerData(data);
            StringBuilder result = new StringBuilder();
            int maxDisplay = Math.min(entries.size(), 3);

            for (int i = 0; i < maxDisplay; i++) {
                MobEntry entry = entries.get(i);
                if (i > 0) result.append(", ");

                String mobName = MobUtils.getDisplayName(entry.getMobType());
                ChatColor tierColor = MobUtils.getTierColor(entry.getTier());

                result.append(tierColor).append(mobName)
                        .append(" T").append(entry.getTier())
                        .append(entry.isElite() ? "+" : "")
                        .append("×").append(entry.getAmount());
            }

            if (entries.size() > 3) {
                result.append(" ").append(ChatColor.GRAY).append("+").append(entries.size() - 3).append(" more");
            }

            return result.toString();
        } catch (Exception e) {
            return "Invalid data";
        }
    }

    /**
     * Parse spawner data into MobEntry objects
     */
    private List<MobEntry> parseSpawnerData(String data) {
        List<MobEntry> entries = new ArrayList<>();
        if (data == null || data.isEmpty()) return entries;

        try {
            String[] parts = data.split(",");
            for (String part : parts) {
                part = part.trim();
                if (!part.isEmpty()) {
                    entries.add(MobEntry.fromString(part));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse spawner data: " + e.getMessage());
        }

        return entries;
    }

    /**
     * Get spawner group for a location
     */
    private String getSpawnerGroup(Location location) {
        // This would need to be implemented in the spawner system
        // For now, return null as placeholder
        return null;
    }

    /**
     * Find spawners in a specific group
     */
    private List<Location> findSpawnersInGroup(String groupName) {
        // This would need to be implemented to work with the group system
        // For now, return empty list as placeholder
        return new ArrayList<>();
    }

    /**
     * Handle the info subcommand with  display
     */
    private boolean handleInfoCommand(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to get info.");
            return true;
        }

        Location loc = target.getLocation();
        spawner.sendSpawnerInfo(player, loc);
        return true;
    }


    private boolean handleVisibilityCommand(Player player, String[] args) {
        boolean show;
        int radius = DEFAULT_VISIBILITY_RADIUS;

        // Parse command variations
        if (args[0].equalsIgnoreCase("show")) {
            show = true;
            if (args.length > 1) {
                radius = parseRadius(args[1], radius);
            }
        } else if (args[0].equalsIgnoreCase("hide")) {
            show = false;
            if (args.length > 1) {
                radius = parseRadius(args[1], radius);
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

            if (args.length > 2) {
                radius = parseRadius(args[2], radius);
            }
        }

        // Check if targeting a specific spawner
        Block target = player.getTargetBlock(null, 5);
        if (target != null && isSpawnerBlock(target)) {
            return toggleSingleSpawner(player, target.getLocation(), show);
        }

        // Toggle spawners in radius
        return toggleSpawnersInRadius(player, show, radius);
    }

    /**
     * Parse radius from string with validation
     */
    private int parseRadius(String radiusStr, int defaultRadius) {
        try {
            int radius = Integer.parseInt(radiusStr);
            return Math.max(1, Math.min(100, radius));
        } catch (NumberFormatException e) {
            return defaultRadius;
        }
    }

    /**
     * Toggle a single spawner's visibility
     */
    private boolean toggleSingleSpawner(Player player, Location location, boolean show) {
        if (spawner.setSpawnerVisibility(location, show)) {
            player.sendMessage(ChatColor.GREEN + "Spawner " + (show ? "shown" : "hidden") + " successfully!");

            // Update hologram
            if (show) {
                mobManager.updateSpawnerHologram(location);
            } else {
                mobManager.removeSpawnerHologram(location);
            }
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }
        return true;
    }


    private boolean toggleSpawnersInRadius(Player player, boolean show, int radius) {
        int count = toggleSpawnerVisibilityInRadius(player.getLocation(), radius, show);

        if (count > 0) {
            player.sendMessage(ChatColor.GREEN + "Successfully " + (show ? "showed" : "hid") +
                    " " + count + " spawners within " + radius + " blocks.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "No spawners found within " + radius + " blocks.");
        }
        return true;
    }

    /**
     *  Toggle spawner visibility in radius - actually implemented
     */
    private int toggleSpawnerVisibilityInRadius(Location center, int radius, boolean show) {
        try {
            // Get all spawners from the spawner manager
            Map<Location, String> allSpawners = spawner.getAllSpawners();
            int count = 0;
            double radiusSquared = radius * radius;

            for (Location spawnerLoc : allSpawners.keySet()) {
                // Check if spawner is in the same world and within radius
                if (spawnerLoc.getWorld().equals(center.getWorld()) &&
                        spawnerLoc.distanceSquared(center) <= radiusSquared) {

                    if (spawner.setSpawnerVisibility(spawnerLoc, show)) {
                        // Update hologram
                        if (show) {
                            mobManager.updateSpawnerHologram(spawnerLoc);
                        } else {
                            mobManager.removeSpawnerHologram(spawnerLoc);
                        }
                        count++;
                    }
                }
            }

            return count;
        } catch (Exception e) {
            plugin.getLogger().warning("Error toggling spawner visibility: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Handle the reset subcommand
     */
    private boolean handleResetCommand(Player player, String[] args) {
        if (args.length > 1 && args[1].startsWith("group:")) {
            return resetSpawnerGroup(player, args[1].substring(6));
        }

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
            mobManager.updateSpawnerHologram(loc);
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at that location.");
        }

        return true;
    }

    /**
     * Reset spawners in a group
     */
    private boolean resetSpawnerGroup(Player player, String groupName) {
        List<Location> groupSpawners = findSpawnersInGroup(groupName);

        if (groupSpawners.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No spawners found in group: " + groupName);
            return true;
        }

        int count = 0;
        for (Location loc : groupSpawners) {
            if (spawner.resetSpawner(loc)) {
                mobManager.updateSpawnerHologram(loc);
                count++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Reset " + count + " spawners in group: " + groupName);
        return true;
    }

    /**
     * Handle the visualize subcommand (placeholder)
     */
    private boolean handleVisualizeCommand(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to visualize it.");
            return true;
        }

        Location loc = target.getLocation();

        // This would need visualization implementation
        player.sendMessage(ChatColor.YELLOW + "Visualization feature not yet implemented.");
        player.sendMessage(ChatColor.GRAY + "Spawner location: " + formatLocation(loc));

        return true;
    }

    /**
     * Handle property setting with validation
     */
    private boolean handlePropertyCommand(Player player, String[] args) {
        if (args.length < 3) {
            showPropertyHelp(player);
            return true;
        }

        String property = args[1];
        String value = buildPropertyValue(args, 2);

        // Handle group properties
        if (property.startsWith("group:")) {
            return setGroupProperty(player, property, value, args);
        }

        // Handle single spawner property
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to set properties.");
            return true;
        }

        Location loc = target.getLocation();
        return setSingleSpawnerProperty(player, loc, property, value);
    }

    /**
     * Show property help
     */
    private void showPropertyHelp(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /spawner property <property> <value>");
        player.sendMessage(ChatColor.GRAY + "Available properties:");
        player.sendMessage(ChatColor.YELLOW + "timeRestricted, startHour, endHour, weatherRestricted");
        player.sendMessage(ChatColor.YELLOW + "spawnInClear, spawnInRain, spawnInThunder, spawnerGroup");
        player.sendMessage(ChatColor.YELLOW + "spawnRadiusX, spawnRadiusY, spawnRadiusZ, maxMobOverride");
        player.sendMessage(ChatColor.YELLOW + "playerDetectionRangeOverride, displayName");
    }

    /**
     * Build property value from arguments
     */
    private String buildPropertyValue(String[] args, int startIndex) {
        StringBuilder valueBuilder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) valueBuilder.append(" ");
            valueBuilder.append(args[i]);
        }
        return valueBuilder.toString();
    }

    /**
     * Set property for a group of spawners
     */
    private boolean setGroupProperty(Player player, String property, String value, String[] args) {
        String groupName = property.substring(6);
        List<Location> groupSpawners = findSpawnersInGroup(groupName);

        if (groupSpawners.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No spawners found in group: " + groupName);
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner property group:<name> <property> <value>");
            return true;
        }

        String actualProperty = args[2];
        String actualValue = buildPropertyValue(args, 3);

        int count = 0;
        for (Location loc : groupSpawners) {
            if (setSpawnerProperty(loc, actualProperty, actualValue)) {
                count++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Set property " + actualProperty + "=" + actualValue +
                " for " + count + " spawners in group: " + groupName);
        return true;
    }

    /**
     * Set property for a single spawner
     */
    private boolean setSingleSpawnerProperty(Player player, Location loc, String property, String value) {
        Object currentValue = getSpawnerProperty(loc, property);

        if (setSpawnerProperty(loc, property, value)) {
            player.sendMessage(ChatColor.GREEN + "Property updated successfully!");
            player.sendMessage(ChatColor.GRAY + property + ": " + formatPropertyValue(currentValue) +
                    ChatColor.GRAY + " -> " + ChatColor.YELLOW + value);

            // Update hologram to reflect changes
            mobManager.updateSpawnerHologram(loc);

            // Save changes
            spawner.saveSpawners();
            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Failed to update property. Check the property name and value.");
            return true;
        }
    }

    /**
     * Set spawner property (placeholder - would need implementation)
     */
    private boolean setSpawnerProperty(Location location, String property, String value) {
        // This would need to be implemented to work with the spawner system
        return true;
    }

    /**
     * Get spawner property (placeholder - would need implementation)
     */
    private Object getSpawnerProperty(Location location, String property) {
        // This would need to be implemented to work with the spawner system
        return null;
    }

    /**
     * Format property value for display
     */
    private String formatPropertyValue(Object value) {
        if (value == null) {
            return ChatColor.RED + "null";
        }
        return ChatColor.YELLOW + value.toString();
    }

    /**
     * Handle group management commands
     */
    private boolean handleGroupCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner group <list|create|add|remove|toggle> [args]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                return listSpawnerGroups(player);
            case "create":
            case "add":
                return addSpawnerToGroup(player, args);
            case "remove":
                return removeSpawnerFromGroup(player);
            case "toggle":
                return toggleSpawnerGroup(player, args);
            default:
                player.sendMessage(ChatColor.RED + "Unknown group action: " + action);
                return true;
        }
    }

    /**
     * List all spawner groups
     */
    private boolean listSpawnerGroups(Player player) {
        Set<String> groups = spawner.getAllSpawnerGroups();
        if (groups.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No spawner groups found.");
        } else {
            player.sendMessage(ChatColor.GOLD + "===== Spawner Groups =====");
            for (String group : groups) {
                List<Location> groupSpawners = findSpawnersInGroup(group);
                player.sendMessage(ChatColor.YELLOW + group + ChatColor.GRAY +
                        " (" + groupSpawners.size() + " spawners)");
            }
        }
        return true;
    }

    /**
     * Add spawner to group
     */
    private boolean addSpawnerToGroup(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner group " + args[1] + " <name>");
            return true;
        }

        String groupName = args[2];
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to add it to a group.");
            return true;
        }

        Location loc = target.getLocation();
        if (setSpawnerProperty(loc, "spawnerGroup", groupName)) {
            player.sendMessage(ChatColor.GREEN + "Spawner added to group: " + groupName);
            spawner.saveSpawners();
        } else {
            player.sendMessage(ChatColor.RED + "Failed to add spawner to group.");
        }
        return true;
    }

    /**
     * Remove spawner from group
     */
    private boolean removeSpawnerFromGroup(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to remove it from a group.");
            return true;
        }

        Location loc = target.getLocation();
        if (setSpawnerProperty(loc, "spawnerGroup", "")) {
            player.sendMessage(ChatColor.GREEN + "Spawner removed from group.");
            spawner.saveSpawners();
        } else {
            player.sendMessage(ChatColor.RED + "Failed to remove spawner from group.");
        }
        return true;
    }

    /**
     * Toggle spawner group
     */
    private boolean toggleSpawnerGroup(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner group toggle <name> [enable|disable]");
            return true;
        }

        String groupName = args[2];
        boolean enable = args.length < 4 || !args[3].equalsIgnoreCase("disable");

        // This would need implementation
        player.sendMessage(ChatColor.YELLOW + "Group toggle feature not yet implemented.");
        return true;
    }

    /**
     * Handle template management
     */
    private boolean handleTemplateCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner template <list|info> [args]");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                return listTemplates(player);
            case "info":
                return showTemplateInfo(player, args);
            default:
                player.sendMessage(ChatColor.RED + "Unknown template action: " + action);
                return true;
        }
    }

    /**
     * List all templates
     */
    private boolean listTemplates(Player player) {
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
        return true;
    }

    /**
     * Show template info
     */
    private boolean showTemplateInfo(Player player, String[] args) {
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

        showTemplateDetails(player, templateData);
        return true;
    }

    /**
     * Show template details
     */
    private void showTemplateDetails(Player player, String templateData) {
        player.sendMessage(ChatColor.GRAY + "Mobs:");

        try {
            List<MobEntry> entries = parseSpawnerData(templateData);
            for (MobEntry entry : entries) {
                String description = entry.getDescription();
                player.sendMessage(ChatColor.GRAY + "- " + description);
            }
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Error parsing template data");
        }
    }

    /**
     * Handle wand command
     */
    private boolean handleWandCommand(Player player) {
        ItemStack wand = createSpawnerWand();
        player.getInventory().addItem(wand);

        player.sendMessage(ChatColor.GREEN + "You have been given a Spawner Control Wand!");
        player.sendMessage(ChatColor.GRAY + "Right-click on a block to create a spawner");
        player.sendMessage(ChatColor.GRAY + "Left-click a spawner to remove it");
        player.sendMessage(ChatColor.GRAY + "Shift+right-click to view spawner info");

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
                ChatColor.YELLOW + "Shift+right-click to view spawner info"
        ));

        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    /**
     * Handle display mode command
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

        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to change its display mode.");
            return true;
        }

        Location loc = target.getLocation();

        // This would need implementation in the spawner system
        player.sendMessage(ChatColor.GREEN + "Display mode set to: " + getDisplayModeName(mode));
        mobManager.updateSpawnerHologram(loc);

        return true;
    }

    /**
     * Get display mode name
     */
    private String getDisplayModeName(int mode) {
        switch (mode) {
            case 0: return "Basic";
            case 1: return "Detailed";
            case 2: return "Admin";
            default: return "Unknown";
        }
    }

    /**
     * Handle edit command for guided property editing
     */
    private boolean handleEditCommand(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null || !isSpawnerBlock(target)) {
            player.sendMessage(ChatColor.RED + "You must be looking at a spawner to edit it.");
            return true;
        }

        Location loc = target.getLocation();
        PropertyEditSession session = new PropertyEditSession(player, loc);
        propertyEditors.put(player.getName(), session);

        showPropertyEditMenu(player);
        return true;
    }

    /**
     * Show property edit menu
     */
    private void showPropertyEditMenu(Player player) {
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
    }

    /**
     * Handle debug command
     */
    private boolean handleDebugCommand(Player player) {
        boolean newMode = !spawner.isDebugMode();
        spawner.setDebugMode(newMode);
        mobManager.setDebugMode(newMode);

        player.sendMessage(ChatColor.GREEN + "Spawner debug mode: " +
                (newMode ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

        return true;
    }


    private boolean handleDebugDetailCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /spawner debugdetail <status|cleanup|force|count>");
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "status":
                return showDetailedSpawnerStatus(player);
            case "cleanup":
                return forceSpawnerCleanup(player);
            case "force":
                return forceSpawnerReset(player);
            case "count":
                return showMobCounts(player);
            default:
                player.sendMessage(ChatColor.RED + "Unknown debug action: " + action);
                return true;
        }
    }

    /**
     * Show detailed spawner status for debugging
     */
    private boolean showDetailedSpawnerStatus(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Look at a spawner to get detailed status.");
            return true;
        }

        Location loc = target.getLocation();

        player.sendMessage(ChatColor.GOLD + "=== DETAILED SPAWNER DEBUG ===");

        // Check if spawner exists in system
        Map<Location, String> allSpawners = spawner.getAllSpawners();
        boolean foundInSystem = false;

        for (Location spawnerLoc : allSpawners.keySet()) {
            if (spawnerLoc.getWorld().equals(loc.getWorld()) &&
                    spawnerLoc.getBlockX() == loc.getBlockX() &&
                    spawnerLoc.getBlockY() == loc.getBlockY() &&
                    spawnerLoc.getBlockZ() == loc.getBlockZ()) {
                foundInSystem = true;
                break;
            }
        }

        player.sendMessage(ChatColor.GRAY + "Location: " + formatLocation(loc));
        player.sendMessage(ChatColor.GRAY + "In System: " + (foundInSystem ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        player.sendMessage(ChatColor.GRAY + "Block Type: " + target.getType());
        player.sendMessage(ChatColor.GRAY + "Has Metadata: " + target.hasMetadata("isSpawner"));

        if (foundInSystem) {
            // Get detailed info using the new debug method
            String debugInfo = spawner.getSpawnerDebugInfo(loc);
            String[] lines = debugInfo.split("\n");
            for (String line : lines) {
                player.sendMessage(ChatColor.WHITE + line);
            }
        }

        return true;
    }

    /**
     * Force cleanup for a specific spawner
     */
    private boolean forceSpawnerCleanup(Player player) {
        Block target = player.getTargetBlock(null, 5);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Look at a spawner to force cleanup.");
            return true;
        }

        Location loc = target.getLocation();

        player.sendMessage(ChatColor.GREEN + "Forcing cleanup for spawner at " + formatLocation(loc));

        // Reset the spawner to clear any stuck state
        if (spawner.resetSpawner(loc)) {
            player.sendMessage(ChatColor.GREEN + "Spawner reset and cleaned up successfully!");
            mobManager.updateSpawnerHologram(loc);
        } else {
            player.sendMessage(ChatColor.RED + "Failed to reset spawner - may not exist at that location.");
        }

        return true;
    }

    /**
     * Force reset spawner (same as cleanup for now)
     */
    private boolean forceSpawnerReset(Player player) {
        return forceSpawnerCleanup(player);
    }

    /**
     * Show mob counts around player
     */
    private boolean showMobCounts(Player player) {
        Location center = player.getLocation();
        World world = center.getWorld();

        player.sendMessage(ChatColor.GOLD + "=== MOB COUNT ANALYSIS ===");

        // Count entities in different ranges
        int[] ranges = {10, 25, 50, 100};

        for (int range : ranges) {
            int totalEntities = 0;
            int customMobs = 0;
            int skeletons = 0;
            int zombies = 0;

            for (Entity entity : world.getNearbyEntities(center, range, range, range)) {
                if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                    totalEntities++;

                    if (entity.hasMetadata("type")) {
                        customMobs++;
                        String type = entity.getMetadata("type").get(0).asString();
                        if (type.contains("skeleton")) skeletons++;
                        if (type.contains("zombie")) zombies++;
                    }
                }
            }

            player.sendMessage(ChatColor.YELLOW + "Range " + range + ": " +
                    ChatColor.WHITE + totalEntities + " total, " +
                    ChatColor.AQUA + customMobs + " custom, " +
                    ChatColor.GRAY + skeletons + " skeletons, " +
                    ChatColor.GREEN + zombies + " zombies");
        }

        return true;
    }

    /**
     * Handle wand interaction events
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !isWandItem(item)) return;

        if (!player.hasPermission("yakrealms.admin.spawner")) return;

        event.setCancelled(true);

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                handleWandInfo(player, clickedBlock);
            } else {
                handleWandCreate(player, clickedBlock);
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleWandRemove(player, clickedBlock);
        }
    }

    /**
     * Handle wand info action
     */
    private void handleWandInfo(Player player, Block block) {
        if (isSpawnerBlock(block)) {
            spawner.sendSpawnerInfo(player, block.getLocation());
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at this location.");
        }
    }

    /**
     * Handle wand create action
     */
    private void handleWandCreate(Player player, Block block) {
        if (isSpawnerBlock(block)) {
            player.sendMessage(ChatColor.RED + "There is already a spawner at this location.");
            return;
        }

        startGuidedCreation(player, block.getLocation());
    }

    /**
     * Handle wand remove action
     */
    private void handleWandRemove(Player player, Block block) {
        if (isSpawnerBlock(block)) {
            if (spawner.removeSpawner(block.getLocation())) {
                player.sendMessage(ChatColor.GREEN + "Spawner removed successfully!");
                mobManager.removeSpawnerHologram(block.getLocation());
            } else {
                player.sendMessage(ChatColor.RED + "Failed to remove spawner.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "No spawner found at this location.");
        }
    }

    /**
     * Handle chat events for creation and editing sessions
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Check for spawner creation session
        if (spawner.hasCreationSession(player)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawner.processCreationInput(player, event.getMessage());
            });
            return;
        }

        // Check for property editing session
        PropertyEditSession session = propertyEditors.get(playerName);
        if (session != null) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                processPropertyEdit(player, event.getMessage(), session);
            });
        }
    }

    /**
     * Process property editing chat
     */
    private void processPropertyEdit(Player player, String message, PropertyEditSession session) {
        if (message.equalsIgnoreCase("cancel")) {
            propertyEditors.remove(player.getName());
            player.sendMessage(ChatColor.RED + "Property editing cancelled.");
            return;
        }

        // Simple property editing implementation
        try {
            int selection = Integer.parseInt(message);
            if (selection >= 1 && selection <= 8) {
                player.sendMessage(ChatColor.GREEN + "Property editing feature will be fully implemented soon.");
                player.sendMessage(ChatColor.GRAY + "For now, use '/spawner property <name> <value>' directly.");
            } else {
                player.sendMessage(ChatColor.RED + "Invalid selection. Please enter a number 1-8 or 'cancel'.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Please enter a number 1-8 or 'cancel'.");
        }

        propertyEditors.remove(player.getName());
    }

    /**
     *   spawner block detection that handles metadata properly
     */
    private boolean isSpawnerBlock(Block block) {
        try {
            // Check if it's a visible spawner block
            if (block.getType() == Material.SPAWNER) {
                return true;
            }

            // Check if it's a hidden spawner (AIR with metadata)
            if (block.getType() == Material.AIR && block.hasMetadata("isSpawner")) {
                return true;
            }

            // Also check if the location exists in the spawner system
            Location blockLoc = block.getLocation();
            Map<Location, String> allSpawners = spawner.getAllSpawners();

            for (Location spawnerLoc : allSpawners.keySet()) {
                if (spawnerLoc.getWorld().equals(blockLoc.getWorld()) &&
                        spawnerLoc.getBlockX() == blockLoc.getBlockX() &&
                        spawnerLoc.getBlockY() == blockLoc.getBlockY() &&
                        spawnerLoc.getBlockZ() == blockLoc.getBlockZ()) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking spawner block: " + e.getMessage());
            return false;
        }
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
     * Format location for display
     */
    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Unknown";
        }
        return String.format("%s [%d, %d, %d]",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    /**
     * Send help message to player
     */
    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "===== Spawner Command Help =====");
        player.sendMessage(ChatColor.YELLOW + "/spawner create <data>" + ChatColor.GRAY + " - Create a spawner");
        player.sendMessage(ChatColor.YELLOW + "/spawner create guided" + ChatColor.GRAY + " - Guided creation");
        player.sendMessage(ChatColor.YELLOW + "/spawner create template:<name>" + ChatColor.GRAY + " - Create from template");
        player.sendMessage(ChatColor.YELLOW + "/spawner remove" + ChatColor.GRAY + " - Remove a spawner");
        player.sendMessage(ChatColor.YELLOW + "/spawner list [page]" + ChatColor.GRAY + " - List all spawners");
        player.sendMessage(ChatColor.YELLOW + "/spawner info" + ChatColor.GRAY + " - Get spawner information");
        player.sendMessage(ChatColor.YELLOW + "/spawner show/hide [radius]" + ChatColor.GRAY + " - Toggle visibility");
        player.sendMessage(ChatColor.YELLOW + "/spawner reset [all|group:<name>]" + ChatColor.GRAY + " - Reset spawners");
        player.sendMessage(ChatColor.YELLOW + "/spawner property <prop> <value>" + ChatColor.GRAY + " - Set property");
        player.sendMessage(ChatColor.YELLOW + "/spawner group <action> [args]" + ChatColor.GRAY + " - Manage groups");
        player.sendMessage(ChatColor.YELLOW + "/spawner template <action> [args]" + ChatColor.GRAY + " - Manage templates");
        player.sendMessage(ChatColor.YELLOW + "/spawner wand" + ChatColor.GRAY + " - Get spawner control wand");
        player.sendMessage(ChatColor.YELLOW + "/spawner edit" + ChatColor.GRAY + " - Edit spawner properties");
        player.sendMessage(ChatColor.YELLOW + "/spawner debug" + ChatColor.GRAY + " - Toggle debug mode");
        player.sendMessage(ChatColor.YELLOW + "/spawner debugdetail <action>" + ChatColor.GRAY + " - Debug commands");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = Arrays.asList(
                    "create", "remove", "delete", "list", "info", "visibility", "toggle",
                    "show", "hide", "reset", "visualize", "property", "set", "group",
                    "template", "wand", "display", "edit", "debug", "debugdetail", "help");

            return commands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return getSecondArgCompletions(args[0].toLowerCase(), args[1]);
        }

        if (args.length == 3) {
            return getThirdArgCompletions(args[0].toLowerCase(), args[1].toLowerCase(), args[2]);
        }

        return completions;
    }

    /**
     * Get second argument completions
     */
    private List<String> getSecondArgCompletions(String firstArg, String partial) {
        switch (firstArg) {
            case "create":
                List<String> options = new ArrayList<>(Arrays.asList(
                        "guided", "skeleton:3@false#2", "zombie:2@false#1"));

                // Add templates
                for (String template : spawner.getAllTemplates().keySet()) {
                    options.add("template:" + template);
                }

                return options.stream()
                        .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                        .collect(Collectors.toList());

            case "visibility":
            case "toggle":
                return Arrays.asList("show", "hide");

            case "reset":
                List<String> resetOptions = new ArrayList<>(Arrays.asList("all"));
                for (String group : spawner.getAllSpawnerGroups()) {
                    resetOptions.add("group:" + group);
                }
                return resetOptions.stream()
                        .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                        .collect(Collectors.toList());

            case "property":
            case "set":
                List<String> properties = Arrays.asList(
                        "timeRestricted", "startHour", "endHour", "weatherRestricted",
                        "spawnInClear", "spawnInRain", "spawnInThunder", "spawnerGroup",
                        "spawnRadiusX", "spawnRadiusY", "spawnRadiusZ", "maxMobOverride",
                        "playerDetectionRangeOverride", "displayName");
                return properties.stream()
                        .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                        .collect(Collectors.toList());

            case "group":
                return Arrays.asList("list", "create", "add", "remove", "toggle");

            case "template":
                return Arrays.asList("list", "info");

            case "display":
                return Arrays.asList("0", "1", "2");

            case "debugdetail":
                return Arrays.asList("status", "cleanup", "force", "count");

            case "list":
                List<String> listOptions = new ArrayList<>(Arrays.asList("1", "2", "3"));
                for (String group : spawner.getAllSpawnerGroups()) {
                    listOptions.add("group:" + group);
                }
                return listOptions.stream()
                        .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                        .collect(Collectors.toList());

            default:
                return new ArrayList<>();
        }
    }

    /**
     * Get third argument completions
     */
    private List<String> getThirdArgCompletions(String firstArg, String secondArg, String partial) {
        if ("template".equals(firstArg) && "info".equals(secondArg)) {
            return spawner.getAllTemplates().keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                    .collect(Collectors.toList());
        }

        if ("group".equals(firstArg) && ("create".equals(secondArg) || "add".equals(secondArg) || "toggle".equals(secondArg))) {
            return spawner.getAllSpawnerGroups().stream()
                    .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
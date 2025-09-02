package com.rednetty.server.core.mechanics.world.mobs.spawners;

import com.rednetty.server.core.mechanics.world.mobs.SpawnerProperties;
import com.rednetty.server.core.mechanics.world.mobs.core.MobType;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a session for creating a spawner with guided configuration
 */
public class SpawnerCreationSession {
    private final String playerName;
    private final Location location;
    private final long startTime;

    // Mob entry tracking
    private List<MobEntry> mobEntries = new ArrayList<>();
    private int currentStep = 0;

    // Current mob entry being configured
    private String currentMobType = "";
    private int currentTier = 1;
    private boolean currentElite = false;
    private int currentAmount = 1;

    // Template support
    private String templateName = null;

    // Advanced configuration
    private boolean configureAdvanced = false;
    private String displayName = "";
    private String spawnerGroup = "";
    private boolean timeRestricted = false;
    private int startHour = 0;
    private int endHour = 24;
    private boolean weatherRestricted = false;
    private boolean spawnInClear = true;
    private boolean spawnInRain = true;
    private boolean spawnInThunder = true;
    private double spawnRadiusX = 3.0;
    private double spawnRadiusY = 1.0;
    private double spawnRadiusZ = 3.0;
    private int displayMode = 0;

    /**
     * Constructor for spawner creation session
     *
     * @param location   The location for the spawner
     * @param playerName The name of the player creating the spawner
     */
    public SpawnerCreationSession(Location location, String playerName) {
        this.location = location;
        this.playerName = playerName;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Validate if the mob type is valid
     *
     * @param mobType The mob type to check
     * @return true if valid
     */
    public boolean validateMobType(String mobType) {
        return MobType.isValidType(mobType.toLowerCase());
    }

    /**
     * Validate the tier
     *
     * @param tier The tier to check
     * @return true if valid
     */
    public boolean validateTier(int tier) {
        return tier >= 1 && tier <= 6;
    }

    /**
     * Add the current mob entry to the list
     */
    public void addCurrentMobEntry() {
        if (!currentMobType.isEmpty()) {
            MobEntry entry = new MobEntry(
                    currentMobType.toLowerCase(),
                    currentTier,
                    currentElite,
                    currentAmount
            );
            mobEntries.add(entry);

            // Reset current entry
            currentMobType = "";
            currentTier = 1;
            currentElite = false;
            currentAmount = 1;
        }
    }

    /**
     * Build the spawner data string
     *
     * @return Formatted spawner data
     */
    public String buildSpawnerData() {
        if (templateName != null) {
            return MobSpawner.getInstance().getAllTemplates().get(templateName);
        }

        // Ensure last entry is added if not already done
        if (!currentMobType.isEmpty()) {
            addCurrentMobEntry();
        }

        return mobEntries.stream()
                .map(MobEntry::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * Process advanced property configuration commands
     *
     * @param command The command to process
     * @return Result message
     */
    public String processAdvancedCommand(String command) {
        String[] parts = command.split(":");
        if (parts.length < 2) {
            return "Invalid command format. Use 'property:value'";
        }

        String prop = parts[0].toLowerCase();
        String value = parts[1];

        try {
            switch (prop) {
                case "name":
                    displayName = value;
                    return "Display name set to: " + value;
                case "group":
                    spawnerGroup = value;
                    return "Spawner group set to: " + value;
                case "time":
                    // Format: start-end (e.g., 0-24)
                    String[] timeParts = value.split("-");
                    if (timeParts.length == 2) {
                        startHour = Integer.parseInt(timeParts[0]);
                        endHour = Integer.parseInt(timeParts[1]);
                        timeRestricted = true;
                        return "Time restriction set: " + startHour + "-" + endHour;
                    }
                    return "Invalid time format. Use 'start-end'";
                case "weather":
                    // Format: clear,rain,thunder
                    spawnInClear = value.contains("clear");
                    spawnInRain = value.contains("rain");
                    spawnInThunder = value.contains("thunder");
                    weatherRestricted = spawnInClear || spawnInRain || spawnInThunder;
                    return "Weather restrictions set: " + value;
                case "radius":
                    // Format: x,y,z
                    String[] radiusParts = value.split(",");
                    if (radiusParts.length == 3) {
                        spawnRadiusX = Double.parseDouble(radiusParts[0]);
                        spawnRadiusY = Double.parseDouble(radiusParts[1]);
                        spawnRadiusZ = Double.parseDouble(radiusParts[2]);
                        return "Spawn radius set to: " + value;
                    }
                    return "Invalid radius format. Use 'x,y,z'";
                case "display":
                    displayMode = Integer.parseInt(value);
                    return "Display mode set to: " + displayMode;
                default:
                    return "Unknown property: " + prop;
            }
        } catch (Exception e) {
            return "Error processing command: " + e.getMessage();
        }
    }

    /**
     * Apply properties to a spawner
     *
     * @param props SpawnerProperties to configure
     */
    public void applyToProperties(SpawnerProperties props) {
        if (!displayName.isEmpty()) {
            props.setDisplayName(displayName);
        }

        if (!spawnerGroup.isEmpty()) {
            props.setSpawnerGroup(spawnerGroup);
        }

        if (timeRestricted) {
            props.setTimeRestricted(true);
            props.setStartHour(startHour);
            props.setEndHour(endHour);
        }

        if (weatherRestricted) {
            props.setWeatherRestricted(true);
            props.setSpawnInClear(spawnInClear);
            props.setSpawnInRain(spawnInRain);
            props.setSpawnInThunder(spawnInThunder);
        }

        if (spawnRadiusX != 3.0) {
            props.setSpawnRadiusX(spawnRadiusX);
        }

        if (spawnRadiusY != 1.0) {
            props.setSpawnRadiusY(spawnRadiusY);
        }

        if (spawnRadiusZ != 3.0) {
            props.setSpawnRadiusZ(spawnRadiusZ);
        }
    }

    /**
     * Get a summary of the spawner configuration
     *
     * @return Formatted summary string
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();

        // Mob entries
        summary.append("Mobs: ");
        for (MobEntry entry : mobEntries) {
            summary.append(entry.getMobType())
                    .append(" T").append(entry.getTier())
                    .append(entry.isElite() ? "+" : "")
                    .append("×").append(entry.getAmount())
                    .append(", ");
        }
        if (!mobEntries.isEmpty()) {
            summary.setLength(summary.length() - 2); // Remove last ", "
        }

        // Advanced properties
        if (!displayName.isEmpty()) {
            summary.append("\nDisplay Name: ").append(displayName);
        }

        if (!spawnerGroup.isEmpty()) {
            summary.append("\nGroup: ").append(spawnerGroup);
        }

        if (timeRestricted) {
            summary.append("\nTime: ")
                    .append(startHour).append("-").append(endHour);
        }

        if (weatherRestricted) {
            summary.append("\nWeather: ")
                    .append(spawnInClear ? "Clear " : "")
                    .append(spawnInRain ? "Rain " : "")
                    .append(spawnInThunder ? "Thunder" : "");
        }

        summary.append("\nSpawn Radius: ")
                .append(String.format("%.1f", spawnRadiusX)).append("x")
                .append(String.format("%.1f", spawnRadiusY)).append("x")
                .append(String.format("%.1f", spawnRadiusZ));

        summary.append("\nDisplay Mode: ").append(displayMode);

        return summary.toString();
    }

    /**
     * Check if the session has mob entries
     *
     * @return true if mob entries exist
     */
    public boolean hasMobEntries() {
        return !mobEntries.isEmpty() || (templateName != null && !templateName.isEmpty());
    }

    // Getters and Setters

    public Location getLocation() {
        return location;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int step) {
        this.currentStep = step;
    }

    public void setCurrentMobType(String mobType) {
        this.currentMobType = mobType;
    }

    public void setCurrentTier(int tier) {
        this.currentTier = tier;
    }

    public void setCurrentElite(boolean elite) {
        this.currentElite = elite;
    }

    public void setCurrentAmount(int amount) {
        this.currentAmount = amount;
    }

    public void setConfigureAdvanced(boolean configureAdvanced) {
        this.configureAdvanced = configureAdvanced;
    }

    public boolean isConfigureAdvanced() {
        return configureAdvanced;
    }

    public String getCurrentMobType() {
        return currentMobType;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getTemplateName() {
        return templateName;
    }

    public int getDisplayMode() {
        return displayMode;
    }

    /**
     * Get help for the current step
     *
     * @return Help message for the current step
     */
    public String getHelpForCurrentStep() {
        switch (currentStep) {
            case 0: // Mob type
                return "Enter a mob type to spawn (e.g., skeleton, zombie)\n" +
                        "Or use a template by typing 'template:name'\n" +
                        "Examples: skeleton, zombie, spider, template:basic_t3";
            case 1: // Tier
                return "Enter a tier for the mob (1-6)\n" +
                        "Tier determines mob difficulty and stats\n" +
                        "Examples: 1, 2, 3, 4, 5, 6";
            case 2: // Elite
                return "Specify if this is an elite mob (true/false)\n" +
                        "Elite mobs have special abilities and are more challenging\n" +
                        "Examples: true, false";
            case 3: // Amount
                return "Enter the number of this mob type (1-10)\n" +
                        "Determines how many of this mob will spawn together\n" +
                        "Examples: 1, 2, 3, 5";
            case 4: // Next steps
                return "Type 'add' to add another mob type\n" +
                        "Type 'done' to finish the spawner\n" +
                        "Type 'advanced' to configure advanced properties";
            case 6: // Advanced configuration
                return "Advanced Configuration Commands:\n" +
                        "- name:<display name>\n" +
                        "- group:<group name>\n" +
                        "- time:<start>-<end>\n" +
                        "- weather:<clear,rain,thunder>\n" +
                        "- radius:<x>,<y>,<z>\n" +
                        "- display:<0-2>\n" +
                        "Type 'done' when finished";
            default:
                return "Invalid step. Please start over.";
        }
    }
}
package com.rednetty.server.mechanics.dungeons.config;

import org.bukkit.Location;

import java.util.*;

/**
 * : Complete Dungeon Template Configuration System
 *
 * Defines the blueprint for creating dungeon instances. This class uses the Builder pattern
 * for easy configuration and supports complex dungeon structures with rooms, bosses,
 * objectives, and rewards.
 *
 * Features:
 * - Builder pattern for easy configuration
 * - Room-based dungeon structure
 * - Boss encounter definitions
 * - Objective system with completion criteria
 * - Tier-based rewards
 * - Validation and error checking
 * - Extensible design for custom dungeons
 */
public class DungeonTemplate {

    // ================ CORE PROPERTIES ================

    private final String id;
    private final String displayName;
    private final String description;
    private final int minTier;
    private final int maxTier;
    private final int minPlayers;
    private final int maxPlayers;
    private final long estimatedDuration;
    private final String worldTemplate;
    private final Location spawnLocation;

    // ================ STRUCTURE DEFINITIONS ================

    private final Map<String, RoomDefinition> rooms;
    private final Map<String, BossDefinition> bosses;
    private final List<ObjectiveDefinition> objectives;
    private final Map<Integer, List<RewardDefinition>> tierRewards;

    // ================ ADVANCED CONFIGURATION ================

    private final DungeonSettings settings;
    private final Map<String, Object> customProperties;

    /**
     * Room definition within a dungeon
     */
    public static class RoomDefinition {
        private final String id;
        private final String type;
        private final boolean isBossRoom;
        private final Location centerLocation;
        private final double radius;
        private final Map<String, Object> properties;
        private final List<String> connections;
        private final List<SpawnerDefinition> spawners;

        public RoomDefinition(String id, String type, boolean isBossRoom) {
            this.id = id;
            this.type = type;
            this.isBossRoom = isBossRoom;
            this.centerLocation = null;
            this.radius = 10.0;
            this.properties = new HashMap<>();
            this.connections = new ArrayList<>();
            this.spawners = new ArrayList<>();
        }

        public RoomDefinition(String id, String type, boolean isBossRoom, Location center, double radius) {
            this.id = id;
            this.type = type;
            this.isBossRoom = isBossRoom;
            this.centerLocation = center;
            this.radius = radius;
            this.properties = new HashMap<>();
            this.connections = new ArrayList<>();
            this.spawners = new ArrayList<>();
        }

        // Getters
        public String getId() { return id; }
        public String getType() { return type; }
        public boolean isBossRoom() { return isBossRoom; }
        public Location getCenterLocation() { return centerLocation; }
        public double getRadius() { return radius; }
        public Map<String, Object> getProperties() { return new HashMap<>(properties); }
        public List<String> getConnections() { return new ArrayList<>(connections); }
        public List<SpawnerDefinition> getSpawners() { return new ArrayList<>(spawners); }

        public Object getProperty(String key) { return properties.get(key); }
        public <T> T getProperty(String key, Class<T> type, T defaultValue) {
            Object value = properties.get(key);
            return type.isInstance(value) ? type.cast(value) : defaultValue;
        }
    }

    /**
     * Boss definition within a dungeon
     */
    public static class BossDefinition {
        private final String id;
        private final String roomId;
        private final String mobType;
        private final int tier;
        private final boolean elite;
        private final Map<String, Object> customProperties;
        private final List<String> abilities;
        private final List<PhaseDefinition> phases;

        public BossDefinition(String id, String roomId, String mobType, int tier, boolean elite) {
            this.id = id;
            this.roomId = roomId;
            this.mobType = mobType;
            this.tier = tier;
            this.elite = elite;
            this.customProperties = new HashMap<>();
            this.abilities = new ArrayList<>();
            this.phases = new ArrayList<>();
        }

        // Getters
        public String getId() { return id; }
        public String getRoomId() { return roomId; }
        public String getMobType() { return mobType; }
        public int getTier() { return tier; }
        public boolean isElite() { return elite; }
        public Map<String, Object> getCustomProperties() { return new HashMap<>(customProperties); }
        public List<String> getAbilities() { return new ArrayList<>(abilities); }
        public List<PhaseDefinition> getPhases() { return new ArrayList<>(phases); }
    }

    /**
     * Boss phase definition
     */
    public static class PhaseDefinition {
        private final int phaseNumber;
        private final double healthThreshold;
        private final List<String> abilities;
        private final Map<String, Object> properties;

        public PhaseDefinition(int phaseNumber, double healthThreshold) {
            this.phaseNumber = phaseNumber;
            this.healthThreshold = healthThreshold;
            this.abilities = new ArrayList<>();
            this.properties = new HashMap<>();
        }

        // Getters
        public int getPhaseNumber() { return phaseNumber; }
        public double getHealthThreshold() { return healthThreshold; }
        public List<String> getAbilities() { return new ArrayList<>(abilities); }
        public Map<String, Object> getProperties() { return new HashMap<>(properties); }
    }

    /**
     * Spawner definition for rooms
     */
    public static class SpawnerDefinition {
        private final String id;
        private final Location location;
        private final String mobData;
        private final boolean visible;
        private final Map<String, Object> properties;

        public SpawnerDefinition(String id, Location location, String mobData, boolean visible) {
            this.id = id;
            this.location = location;
            this.mobData = mobData;
            this.visible = visible;
            this.properties = new HashMap<>();
        }

        // Getters
        public String getId() { return id; }
        public Location getLocation() { return location; }
        public String getMobData() { return mobData; }
        public boolean isVisible() { return visible; }
        public Map<String, Object> getProperties() { return new HashMap<>(properties); }
    }

    /**
     * Objective definition
     */
    public static class ObjectiveDefinition {
        private final String id;
        private final String roomId;
        private final ObjectiveType type;
        private final Map<String, Object> parameters;
        private final List<String> dependencies;
        private final boolean required;

        public ObjectiveDefinition(String id, String roomId, ObjectiveType type, boolean required) {
            this.id = id;
            this.roomId = roomId;
            this.type = type;
            this.required = required;
            this.parameters = new HashMap<>();
            this.dependencies = new ArrayList<>();
        }

        // Getters
        public String getId() { return id; }
        public String getRoomId() { return roomId; }
        public ObjectiveType getType() { return type; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        public List<String> getDependencies() { return new ArrayList<>(dependencies); }
        public boolean isRequired() { return required; }

        public Object getParameter(String key) { return parameters.get(key); }
        public <T> T getParameter(String key, Class<T> type, T defaultValue) {
            Object value = parameters.get(key);
            return type.isInstance(value) ? type.cast(value) : defaultValue;
        }
    }

    /**
     * Objective types
     */
    public enum ObjectiveType {
        ELIMINATE_ALL("eliminate_all"),
        DEFEAT_BOSS("defeat_boss"),
        DEFEAT_ALL_BOSSES("defeat_all_bosses"),
        SOLVE_PUZZLE("solve_puzzle"),
        SURVIVE_WAVES("survive_waves"),
        COLLECT_ITEMS("collect_items"),
        REACH_LOCATION("reach_location"),
        ACTIVATE_MECHANISM("activate_mechanism"),
        PROTECT_NPC("protect_npc"),
        CUSTOM("custom");

        private final String id;

        ObjectiveType(String id) {
            this.id = id;
        }

        public String getId() { return id; }

        public static ObjectiveType fromId(String id) {
            for (ObjectiveType type : values()) {
                if (type.getId().equals(id)) {
                    return type;
                }
            }
            return CUSTOM;
        }
    }

    /**
     * Reward definition
     */
    public static class RewardDefinition {
        private final RewardType type;
        private final String data;
        private final double chance;
        private final int quantity;
        private final Map<String, Object> properties;

        public RewardDefinition(RewardType type, String data, double chance, int quantity) {
            this.type = type;
            this.data = data;
            this.chance = Math.max(0.0, Math.min(1.0, chance));
            this.quantity = Math.max(1, quantity);
            this.properties = new HashMap<>();
        }

        // Getters
        public RewardType getType() { return type; }
        public String getData() { return data; }
        public double getChance() { return chance; }
        public int getQuantity() { return quantity; }
        public Map<String, Object> getProperties() { return new HashMap<>(properties); }
    }

    /**
     * Reward types
     */
    public enum RewardType {
        MOB_SPAWN("mob_spawn"),
        ITEM("item"),
        EXPERIENCE("experience"),
        CURRENCY("currency"),
        CUSTOM("custom");

        private final String id;

        RewardType(String id) {
            this.id = id;
        }

        public String getId() { return id; }
    }

    /**
     * Dungeon settings
     */
    public static class DungeonSettings {
        private boolean allowRespawning = false;
        private boolean allowPvP = false;
        private boolean dropItemsOnDeath = true;
        private boolean keepInventoryOnDeath = false;
        private double experienceMultiplier = 1.0;
        private double lootMultiplier = 1.0;
        private boolean allowTeleportation = false;
        private boolean allowFlying = false;
        private int maxDeaths = -1; // -1 = unlimited
        private long gracePeriod = 30000L; // 30 seconds
        private final Map<String, Object> customSettings = new HashMap<>();

        // Getters and setters
        public boolean isAllowRespawning() { return allowRespawning; }
        public void setAllowRespawning(boolean allowRespawning) { this.allowRespawning = allowRespawning; }
        public boolean isAllowPvP() { return allowPvP; }
        public void setAllowPvP(boolean allowPvP) { this.allowPvP = allowPvP; }
        public boolean isDropItemsOnDeath() { return dropItemsOnDeath; }
        public void setDropItemsOnDeath(boolean dropItemsOnDeath) { this.dropItemsOnDeath = dropItemsOnDeath; }
        public boolean isKeepInventoryOnDeath() { return keepInventoryOnDeath; }
        public void setKeepInventoryOnDeath(boolean keepInventoryOnDeath) { this.keepInventoryOnDeath = keepInventoryOnDeath; }
        public double getExperienceMultiplier() { return experienceMultiplier; }
        public void setExperienceMultiplier(double experienceMultiplier) { this.experienceMultiplier = Math.max(0.0, experienceMultiplier); }
        public double getLootMultiplier() { return lootMultiplier; }
        public void setLootMultiplier(double lootMultiplier) { this.lootMultiplier = Math.max(0.0, lootMultiplier); }
        public boolean isAllowTeleportation() { return allowTeleportation; }
        public void setAllowTeleportation(boolean allowTeleportation) { this.allowTeleportation = allowTeleportation; }
        public boolean isAllowFlying() { return allowFlying; }
        public void setAllowFlying(boolean allowFlying) { this.allowFlying = allowFlying; }
        public int getMaxDeaths() { return maxDeaths; }
        public void setMaxDeaths(int maxDeaths) { this.maxDeaths = maxDeaths; }
        public long getGracePeriod() { return gracePeriod; }
        public void setGracePeriod(long gracePeriod) { this.gracePeriod = Math.max(0L, gracePeriod); }
        public Map<String, Object> getCustomSettings() { return new HashMap<>(customSettings); }
        public void setCustomSetting(String key, Object value) { customSettings.put(key, value); }
        public Object getCustomSetting(String key) { return customSettings.get(key); }
    }

    // ================ CONSTRUCTOR ================

    private DungeonTemplate(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.minTier = builder.minTier;
        this.maxTier = builder.maxTier;
        this.minPlayers = builder.minPlayers;
        this.maxPlayers = builder.maxPlayers;
        this.estimatedDuration = builder.estimatedDuration;
        this.worldTemplate = builder.worldTemplate;
        this.spawnLocation = builder.spawnLocation;
        this.rooms = Collections.unmodifiableMap(new HashMap<>(builder.rooms));
        this.bosses = Collections.unmodifiableMap(new HashMap<>(builder.bosses));
        this.objectives = Collections.unmodifiableList(new ArrayList<>(builder.objectives));
        this.tierRewards = Collections.unmodifiableMap(new HashMap<>(builder.tierRewards));
        this.settings = builder.settings;
        this.customProperties = Collections.unmodifiableMap(new HashMap<>(builder.customProperties));
    }

    // ================ BUILDER PATTERN ================

    /**
     * Builder for creating dungeon templates
     */
    public static class Builder {
        private final String id;
        private String displayName;
        private String description = "";
        private int minTier = 1;
        private int maxTier = 6;
        private int minPlayers = 1;
        private int maxPlayers = 8;
        private long estimatedDuration = 1800000L; // 30 minutes
        private String worldTemplate;
        private Location spawnLocation;
        private final Map<String, RoomDefinition> rooms = new HashMap<>();
        private final Map<String, BossDefinition> bosses = new HashMap<>();
        private final List<ObjectiveDefinition> objectives = new ArrayList<>();
        private final Map<Integer, List<RewardDefinition>> tierRewards = new HashMap<>();
        private DungeonSettings settings = new DungeonSettings();
        private final Map<String, Object> customProperties = new HashMap<>();

        public Builder(String id) {
            this.id = id;
            this.displayName = id;
        }

        // Basic properties
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder minTier(int minTier) {
            this.minTier = Math.max(1, Math.min(6, minTier));
            return this;
        }

        public Builder maxTier(int maxTier) {
            this.maxTier = Math.max(1, Math.min(6, maxTier));
            return this;
        }

        public Builder minPlayers(int minPlayers) {
            this.minPlayers = Math.max(1, minPlayers);
            return this;
        }

        public Builder maxPlayers(int maxPlayers) {
            this.maxPlayers = Math.max(1, maxPlayers);
            return this;
        }

        public Builder estimatedDuration(long duration) {
            this.estimatedDuration = Math.max(60000L, duration); // Minimum 1 minute
            return this;
        }

        public Builder worldTemplate(String worldTemplate) {
            this.worldTemplate = worldTemplate;
            return this;
        }

        public Builder spawnLocation(Location spawnLocation) {
            this.spawnLocation = spawnLocation;
            return this;
        }

        // Room management
        public Builder addRoom(String id, String type, boolean isBossRoom) {
            rooms.put(id, new RoomDefinition(id, type, isBossRoom));
            return this;
        }

        public Builder addRoom(String id, String type, boolean isBossRoom, Location center, double radius) {
            rooms.put(id, new RoomDefinition(id, type, isBossRoom, center, radius));
            return this;
        }

        public Builder addRoomConnection(String roomId1, String roomId2) {
            RoomDefinition room1 = rooms.get(roomId1);
            RoomDefinition room2 = rooms.get(roomId2);

            if (room1 != null && room2 != null) {
                room1.connections.add(roomId2);
                room2.connections.add(roomId1);
            }

            return this;
        }

        public Builder addRoomSpawner(String roomId, String spawnerId, Location location, String mobData, boolean visible) {
            RoomDefinition room = rooms.get(roomId);
            if (room != null) {
                room.spawners.add(new SpawnerDefinition(spawnerId, location, mobData, visible));
            }
            return this;
        }

        public Builder setRoomProperty(String roomId, String key, Object value) {
            RoomDefinition room = rooms.get(roomId);
            if (room != null) {
                room.properties.put(key, value);
            }
            return this;
        }

        // Boss management
        public Builder addBoss(String id, String roomId, int tier, boolean elite) {
            // Default boss type based on tier
            String mobType = switch (tier) {
                case 1, 2 -> "skeleton";
                case 3, 4 -> "witherskeleton";
                case 5, 6 -> "warden";
                default -> "skeleton";
            };

            bosses.put(id, new BossDefinition(id, roomId, mobType, tier, elite));
            return this;
        }

        public Builder addBoss(String id, String roomId, String mobType, int tier, boolean elite) {
            bosses.put(id, new BossDefinition(id, roomId, mobType, tier, elite));
            return this;
        }

        public Builder addBossAbility(String bossId, String ability) {
            BossDefinition boss = bosses.get(bossId);
            if (boss != null) {
                boss.abilities.add(ability);
            }
            return this;
        }

        public Builder addBossPhase(String bossId, int phaseNumber, double healthThreshold) {
            BossDefinition boss = bosses.get(bossId);
            if (boss != null) {
                boss.phases.add(new PhaseDefinition(phaseNumber, healthThreshold));
            }
            return this;
        }

        public Builder setBossProperty(String bossId, String key, Object value) {
            BossDefinition boss = bosses.get(bossId);
            if (boss != null) {
                boss.customProperties.put(key, value);
            }
            return this;
        }

        // Objective management
        public Builder addObjective(String id, String roomId, String typeId) {
            ObjectiveType type = ObjectiveType.fromId(typeId);
            objectives.add(new ObjectiveDefinition(id, roomId, type, true));
            return this;
        }

        public Builder addObjective(String id, String roomId, String typeId, boolean required) {
            ObjectiveType type = ObjectiveType.fromId(typeId);
            objectives.add(new ObjectiveDefinition(id, roomId, type, required));
            return this;
        }

        public Builder addObjectiveDependency(String objectiveId, String dependsOn) {
            objectives.stream()
                    .filter(obj -> obj.getId().equals(objectiveId))
                    .findFirst()
                    .ifPresent(obj -> obj.dependencies.add(dependsOn));
            return this;
        }

        public Builder setObjectiveParameter(String objectiveId, String key, Object value) {
            objectives.stream()
                    .filter(obj -> obj.getId().equals(objectiveId))
                    .findFirst()
                    .ifPresent(obj -> obj.parameters.put(key, value));
            return this;
        }

        // Reward management
        public Builder addReward(int tier, String mobData) {
            List<RewardDefinition> rewards = tierRewards.computeIfAbsent(tier, k -> new ArrayList<>());
            rewards.add(new RewardDefinition(RewardType.MOB_SPAWN, mobData, 1.0, 1));
            return this;
        }

        public Builder addReward(int tier, String... mobData) {
            List<RewardDefinition> rewards = tierRewards.computeIfAbsent(tier, k -> new ArrayList<>());
            for (String data : mobData) {
                rewards.add(new RewardDefinition(RewardType.MOB_SPAWN, data, 1.0, 1));
            }
            return this;
        }

        public Builder addReward(int tier, RewardType type, String data, double chance, int quantity) {
            List<RewardDefinition> rewards = tierRewards.computeIfAbsent(tier, k -> new ArrayList<>());
            rewards.add(new RewardDefinition(type, data, chance, quantity));
            return this;
        }

        // Settings
        public Builder settings(DungeonSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder allowRespawning(boolean allow) {
            this.settings.setAllowRespawning(allow);
            return this;
        }

        public Builder allowPvP(boolean allow) {
            this.settings.setAllowPvP(allow);
            return this;
        }

        public Builder experienceMultiplier(double multiplier) {
            this.settings.setExperienceMultiplier(multiplier);
            return this;
        }

        public Builder lootMultiplier(double multiplier) {
            this.settings.setLootMultiplier(multiplier);
            return this;
        }

        public Builder maxDeaths(int maxDeaths) {
            this.settings.setMaxDeaths(maxDeaths);
            return this;
        }

        // Custom properties
        public Builder setProperty(String key, Object value) {
            customProperties.put(key, value);
            return this;
        }

        // Build and validate
        public DungeonTemplate build() {
            validate();
            return new DungeonTemplate(this);
        }

        private void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Dungeon ID cannot be null or empty");
            }

            if (minTier > maxTier) {
                throw new IllegalArgumentException("Min tier cannot be greater than max tier");
            }

            if (minPlayers > maxPlayers) {
                throw new IllegalArgumentException("Min players cannot be greater than max players");
            }

            if (rooms.isEmpty()) {
                throw new IllegalArgumentException("Dungeon must have at least one room");
            }

            if (objectives.isEmpty()) {
                throw new IllegalArgumentException("Dungeon must have at least one objective");
            }

            // Validate boss rooms exist
            for (BossDefinition boss : bosses.values()) {
                if (!rooms.containsKey(boss.getRoomId())) {
                    throw new IllegalArgumentException("Boss room '" + boss.getRoomId() + "' does not exist");
                }
            }

            // Validate objective rooms exist
            for (ObjectiveDefinition objective : objectives) {
                if (!rooms.containsKey(objective.getRoomId())) {
                    throw new IllegalArgumentException("Objective room '" + objective.getRoomId() + "' does not exist");
                }
            }

            // Validate objective dependencies
            Set<String> objectiveIds = objectives.stream()
                    .map(ObjectiveDefinition::getId)
                    .collect(java.util.stream.Collectors.toSet());

            for (ObjectiveDefinition objective : objectives) {
                for (String dependency : objective.getDependencies()) {
                    if (!objectiveIds.contains(dependency)) {
                        throw new IllegalArgumentException("Objective dependency '" + dependency + "' does not exist");
                    }
                }
            }
        }
    }

    // ================ STATIC FACTORY METHOD ================

    public static Builder builder(String id) {
        return new Builder(id);
    }

    // ================ GETTERS ================

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMinTier() { return minTier; }
    public int getMaxTier() { return maxTier; }
    public int getMinPlayers() { return minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public long getEstimatedDuration() { return estimatedDuration; }
    public String getWorldTemplate() { return worldTemplate; }
    public Location getSpawnLocation() { return spawnLocation; }
    public Map<String, RoomDefinition> getRooms() { return rooms; }
    public Map<String, BossDefinition> getBosses() { return bosses; }
    public List<ObjectiveDefinition> getObjectives() { return objectives; }
    public Map<Integer, List<RewardDefinition>> getTierRewards() { return tierRewards; }
    public DungeonSettings getSettings() { return settings; }
    public Map<String, Object> getCustomProperties() { return customProperties; }

    // ================ UTILITY METHODS ================

    public RoomDefinition getRoom(String id) {
        return rooms.get(id);
    }

    public BossDefinition getBoss(String id) {
        return bosses.get(id);
    }

    public ObjectiveDefinition getObjective(String id) {
        return objectives.stream()
                .filter(obj -> obj.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public List<RewardDefinition> getRewardsForTier(int tier) {
        return tierRewards.getOrDefault(tier, Collections.emptyList());
    }

    public Object getCustomProperty(String key) {
        return customProperties.get(key);
    }

    public <T> T getCustomProperty(String key, Class<T> type, T defaultValue) {
        Object value = customProperties.get(key);
        return type.isInstance(value) ? type.cast(value) : defaultValue;
    }

    public boolean isValidForTier(int tier) {
        return tier >= minTier && tier <= maxTier;
    }

    public boolean isValidForPartySize(int size) {
        return size >= minPlayers && size <= maxPlayers;
    }

    public boolean hasBossRooms() {
        return rooms.values().stream().anyMatch(RoomDefinition::isBossRoom);
    }

    public List<String> getBossRoomIds() {
        return rooms.values().stream()
                .filter(RoomDefinition::isBossRoom)
                .map(RoomDefinition::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<ObjectiveDefinition> getRequiredObjectives() {
        return objectives.stream()
                .filter(ObjectiveDefinition::isRequired)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get a summary string for display
     */
    public String getSummary() {
        return String.format("%s (T%d-%d, %d-%d players, %d rooms, %d bosses, %d objectives)",
                displayName, minTier, maxTier, minPlayers, maxPlayers,
                rooms.size(), bosses.size(), objectives.size());
    }

    /**
     * Get detailed information string
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Dungeon Template: ").append(displayName).append("\n");
        info.append("ID: ").append(id).append("\n");
        info.append("Description: ").append(description).append("\n");
        info.append("Tier Range: ").append(minTier).append("-").append(maxTier).append("\n");
        info.append("Player Range: ").append(minPlayers).append("-").append(maxPlayers).append("\n");
        info.append("Estimated Duration: ").append(estimatedDuration / 60000).append(" minutes\n");
        info.append("Rooms: ").append(rooms.size()).append("\n");
        info.append("Bosses: ").append(bosses.size()).append("\n");
        info.append("Objectives: ").append(objectives.size()).append("\n");
        info.append("Reward Tiers: ").append(tierRewards.keySet()).append("\n");
        return info.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DungeonTemplate that = (DungeonTemplate) obj;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
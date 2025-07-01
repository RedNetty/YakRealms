// === LootChestRepository.java ===
package com.rednetty.server.mechanics.lootchests.storage;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.lootchests.data.LootChestData;
import com.rednetty.server.mechanics.lootchests.types.LootChestLocation;
import com.rednetty.server.mechanics.lootchests.types.ChestState;
import com.rednetty.server.mechanics.lootchests.types.ChestTier;
import com.rednetty.server.mechanics.lootchests.types.ChestType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles persistent storage of loot chest data
 */
public class LootChestRepository {
    private final YakRealms plugin;
    private final Logger logger;
    private File storageFile;

    public LootChestRepository() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        storageFile = new File(plugin.getDataFolder(), "lootchests-data.yml");
        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile();
            } catch (IOException e) {
                logger.severe("Could not create loot chest storage file: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        // Any cleanup needed
    }

    public Map<LootChestLocation, LootChestData> loadAllChests() {
        Map<LootChestLocation, LootChestData> chests = new HashMap<>();

        if (!storageFile.exists()) {
            return chests;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);

        for (String key : config.getKeys(false)) {
            try {
                LootChestLocation location = LootChestLocation.fromString(key);

                // Load chest data
                String tierName = config.getString(key + ".tier", "IRON");
                String typeName = config.getString(key + ".type", "NORMAL");
                String stateName = config.getString(key + ".state", "AVAILABLE");
                long creationTime = config.getLong(key + ".creation-time", System.currentTimeMillis());
                long respawnTime = config.getLong(key + ".respawn-time", 0);

                ChestTier tier = ChestTier.valueOf(tierName);
                ChestType type = ChestType.valueOf(typeName);

                LootChestData chestData = new LootChestData(location, tier, type);
                chestData.setState(ChestState.valueOf(stateName));
                chestData.setRespawnTime(respawnTime);

                // Load player interactions
                if (config.contains(key + ".interactions")) {
                    for (String playerUuid : config.getStringList(key + ".interactions")) {
                        chestData.addInteraction(UUID.fromString(playerUuid));
                    }
                }

                chests.put(location, chestData);

            } catch (Exception e) {
                logger.warning("Failed to load chest data for key " + key + ": " + e.getMessage());
            }
        }

        return chests;
    }

    public void saveAllChests(Map<LootChestLocation, LootChestData> chests) {
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<LootChestLocation, LootChestData> entry : chests.entrySet()) {
            LootChestLocation location = entry.getKey();
            LootChestData data = entry.getValue();

            String key = location.toString();

            config.set(key + ".tier", data.getTier().name());
            config.set(key + ".type", data.getType().name());
            config.set(key + ".state", data.getState().name());
            config.set(key + ".creation-time", data.getCreationTime());
            config.set(key + ".respawn-time", data.getRespawnTime());

            // Save player interactions
            if (!data.getPlayersWhoInteracted().isEmpty()) {
                config.set(key + ".interactions",
                        data.getPlayersWhoInteracted().stream()
                                .map(UUID::toString)
                                .toList());
            }
        }

        try {
            config.save(storageFile);
        } catch (IOException e) {
            logger.severe("Failed to save loot chest data: " + e.getMessage());
        }
    }

    public void saveChest(LootChestLocation location, LootChestData data) {
        Map<LootChestLocation, LootChestData> singleChest = new HashMap<>();
        singleChest.put(location, data);

        // Load existing data first
        Map<LootChestLocation, LootChestData> allChests = loadAllChests();
        allChests.putAll(singleChest);

        saveAllChests(allChests);
    }

    public void deleteChest(LootChestLocation location) {
        Map<LootChestLocation, LootChestData> allChests = loadAllChests();
        allChests.remove(location);
        saveAllChests(allChests);
    }
}
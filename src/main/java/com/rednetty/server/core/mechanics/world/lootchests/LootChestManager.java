package com.rednetty.server.core.mechanics.world.lootchests;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.rednetty.server.YakRealms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Loot Chest Manager with improved saving, performance, and reliability
 * Features auto-save, backup system, and better error recovery
 *
 * Version 5.0 - Enhanced functionality and reliability
 */
public class LootChestManager implements Listener {

    private final YakRealms plugin;
    private final File vaultDataFile;
    private final File backupDir;
    private File configFile;
    private FileConfiguration config;

    // Enhanced vault storage with thread-safe operations
    private final Map<String, VaultChest> placedVaults = new ConcurrentHashMap<>();
    private final Map<String, String> locationToVaultId = new ConcurrentHashMap<>();
    private final Set<String> activeAnimations = ConcurrentHashMap.newKeySet();

    // Performance caching
    private final Map<String, VaultChest> vaultCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 5000; // 5 seconds cache

    // Save management
    private final ReentrantReadWriteLock saveLock = new ReentrantReadWriteLock();
    private BukkitTask autoSaveTask;
    private boolean needsSave = false;
    private long lastSaveTime = System.currentTimeMillis();

    // Enhanced statistics
    private int vaultsPlaced = 0;
    private int vaultsOpened = 0;
    private int keysDropped = 0;
    private final Map<ChestTier, Integer> tierOpenCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> playerOpenCount = new ConcurrentHashMap<>();

    // Namespace keys
    private final NamespacedKey vaultIdKey;
    private final NamespacedKey vaultTierKey;
    private final NamespacedKey vaultTypeKey;

    // Enhanced Gson with custom adapters for better serialization
    private final Gson gson;

    public enum ChestTier {
        TIER_1(1, "Common", "§7", 0.30),
        TIER_2(2, "Uncommon", "§a", 0.25),
        TIER_3(3, "Rare", "§9", 0.20),
        TIER_4(4, "Epic", "§5", 0.15),
        TIER_5(5, "Legendary", "§6", 0.08),
        TIER_6(6, "Mythic", "§c", 0.02);

        private final int level;
        private final String displayName;
        private final String color;
        private final double dropChance;

        ChestTier(int level, String displayName, String color, double dropChance) {
            this.level = level;
            this.displayName = displayName;
            this.color = color;
            this.dropChance = dropChance;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        public double getDropChance() { return dropChance; }

        public static ChestTier getRandomTier() {
            double rand = Math.random();
            double cumulative = 0.0;

            for (ChestTier tier : values()) {
                cumulative += tier.dropChance;
                if (rand <= cumulative) {
                    return tier;
                }
            }
            return TIER_1;
        }

        public static ChestTier fromLevel(int level) {
            for (ChestTier tier : values()) {
                if (tier.level == level) {
                    return tier;
                }
            }
            return TIER_1;
        }
    }

    public enum ChestType {
        NORMAL("Normal", 0.60),
        FOOD("Food", 0.25),
        ELITE("Elite", 0.15);

        private final String name;
        private final double spawnChance;

        ChestType(String name, double spawnChance) {
            this.name = name;
            this.spawnChance = spawnChance;
        }

        public String getName() { return name; }
        public double getSpawnChance() { return spawnChance; }

        public static ChestType getRandomType() {
            double rand = Math.random();
            double cumulative = 0.0;

            for (ChestType type : values()) {
                cumulative += type.spawnChance;
                if (rand <= cumulative) {
                    return type;
                }
            }
            return NORMAL;
        }
    }

    public LootChestManager(YakRealms plugin) {
        this.plugin = plugin;
        this.vaultIdKey = new NamespacedKey(plugin, "vault_id");
        this.vaultTierKey = new NamespacedKey(plugin, "vault_tier");
        this.vaultTypeKey = new NamespacedKey(plugin, "vault_type");

        // Setup data files and directories
        this.vaultDataFile = new File(plugin.getDataFolder(), "placed-vaults.json");
        this.backupDir = new File(plugin.getDataFolder(), "backups");

        // Create backup directory if needed
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        // Setup enhanced Gson with custom adapters
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Location.class, new LocationSerializer())
                .registerTypeAdapter(VaultChest.class, new VaultChestSerializer())
                .create();

        // Initialize tier distribution tracking
        for (ChestTier tier : ChestTier.values()) {
            tierOpenCount.put(tier, 0);
        }

        initialize();
    }

    private void initialize() {
        try {
            setupConfiguration();
            loadPlacedVaults();
            spawnVaultBlocks();

            // Setup auto-save task
            setupAutoSave();

            // Register events
            Bukkit.getPluginManager().registerEvents(this, plugin);

            YakRealms.log("LootChestManager initialized with " + placedVaults.size() + " placed vaults");
        } catch (Exception e) {
            YakRealms.error("Failed to initialize LootChestManager", e);
        }
    }

    private void setupAutoSave() {
        // Auto-save every 5 minutes
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (needsSave && (System.currentTimeMillis() - lastSaveTime) > 60000) { // At least 1 minute since last save
                performAutoSave();
            }
        }, 6000L, 6000L); // 5 minutes
    }

    private void performAutoSave() {
        saveLock.writeLock().lock();
        try {
            if (needsSave) {
                saveToFile();
                needsSave = false;
                lastSaveTime = System.currentTimeMillis();
                YakRealms.debug("Auto-saved vault data (" + placedVaults.size() + " vaults)");
            }
        } finally {
            saveLock.writeLock().unlock();
        }
    }

    private void setupConfiguration() {
        configFile = new File(plugin.getDataFolder(), "vault-chests.yml");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();

                config = YamlConfiguration.loadConfiguration(configFile);

                // Enhanced configuration
                config.set("settings.instant-refresh-enabled", true);
                config.set("settings.key-animation-enabled", true);
                config.set("settings.effects-enabled", true);
                config.set("settings.max-vault-distance", 200);
                config.set("settings.auto-cleanup-enabled", true);
                config.set("settings.automatic-opening", true);
                config.set("settings.minimal-messaging", true);
                config.set("settings.auto-save-interval", 300); // 5 minutes
                config.set("settings.backup-enabled", true);
                config.set("settings.max-backups", 10);
                config.set("settings.cache-duration", 5000);

                config.save(configFile);
            } catch (IOException e) {
                YakRealms.error("Failed to create vault-chests.yml", e);
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    // ========================================
    // ENHANCED LOCATION UTILITY METHODS
    // ========================================

    private String createLocationKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return String.format("%s_%d_%d_%d",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    private boolean isLocationValid(Location location) {
        return location != null &&
                location.getWorld() != null &&
                Bukkit.getWorld(location.getWorld().getName()) != null;
    }

    // ========================================
    // ENHANCED VAULT PLACEMENT AND MANAGEMENT
    // ========================================

    public boolean placeVault(Location location, ChestTier tier, ChestType type, String placedBy) {
        try {
            if (!isLocationValid(location)) {
                YakRealms.log("Cannot place vault: invalid location");
                return false;
            }

            location = new Location(location.getWorld(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());

            String locationKey = createLocationKey(location);
            if (locationKey == null) {
                return false;
            }

            if (locationToVaultId.containsKey(locationKey)) {
                return false;
            }

            String vaultId = UUID.randomUUID().toString();
            VaultChest vault = new VaultChest(vaultId, location, tier, type);
            vault.setCreatedBy(placedBy);

            if (!vault.hasValidLocation()) {
                return false;
            }

            // Place the physical vault block
            Block block = location.getBlock();
            block.setType(Material.VAULT);

            if (!(block.getState() instanceof Vault)) {
                return false;
            }

            Vault vaultBlock = (Vault) block.getState();
            vaultBlock.setLootTable(LootTables.BURIED_TREASURE.getLootTable());

            // Store metadata
            vaultBlock.getPersistentDataContainer().set(vaultIdKey, PersistentDataType.STRING, vaultId);
            vaultBlock.getPersistentDataContainer().set(vaultTierKey, PersistentDataType.INTEGER, tier.getLevel());
            vaultBlock.getPersistentDataContainer().set(vaultTypeKey, PersistentDataType.STRING, type.name());
            vaultBlock.update();

            // Update storage
            placedVaults.put(vaultId, vault);
            locationToVaultId.put(locationKey, vaultId);

            // Clear cache for this location
            vaultCache.remove(locationKey);

            vaultsPlaced++;
            needsSave = true;

            YakRealms.log("Placed " + vault.getDisplayName() + " at " + formatLocation(location) + " by " + placedBy);
            return true;

        } catch (Exception e) {
            YakRealms.error("Failed to place vault", e);
            return false;
        }
    }

    public boolean removeVault(Location location) {
        try {
            if (!isLocationValid(location)) {
                return false;
            }

            location = new Location(location.getWorld(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());

            String locationKey = createLocationKey(location);
            if (locationKey == null) {
                return false;
            }

            String vaultId = locationToVaultId.remove(locationKey);
            if (vaultId == null) {
                return false;
            }

            VaultChest vault = placedVaults.remove(vaultId);
            if (vault == null) {
                return false;
            }

            // Remove physical block
            Block block = location.getBlock();
            if (block.getType() == Material.VAULT) {
                block.setType(Material.AIR);
            }

            // Cleanup
            activeAnimations.remove(vaultId);
            vaultCache.remove(locationKey);
            cacheTimestamps.remove(locationKey);

            needsSave = true;

            YakRealms.log("Removed " + vault.getDisplayName() + " from " + formatLocation(location));
            return true;

        } catch (Exception e) {
            YakRealms.error("Failed to remove vault", e);
            return false;
        }
    }

    public VaultChest getVaultAtLocation(Location location) {
        if (!isLocationValid(location)) {
            return null;
        }

        location = new Location(location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());

        String locationKey = createLocationKey(location);
        if (locationKey == null) {
            return null;
        }

        // Check cache first
        if (vaultCache.containsKey(locationKey)) {
            Long timestamp = cacheTimestamps.get(locationKey);
            if (timestamp != null && (System.currentTimeMillis() - timestamp) < CACHE_DURATION) {
                return vaultCache.get(locationKey);
            }
        }

        String vaultId = locationToVaultId.get(locationKey);
        VaultChest vault = vaultId != null ? placedVaults.get(vaultId) : null;

        // Update cache
        if (vault != null) {
            vaultCache.put(locationKey, vault);
            cacheTimestamps.put(locationKey, System.currentTimeMillis());
        }

        return vault;
    }

    // ========================================
    // ENHANCED EVENT HANDLERS
    // ========================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        try {
            ItemStack item = event.getItem().getItemStack();

            if (!isVaultKey(item)) {
                return;
            }

            String vaultId = getVaultIdFromKey(item);
            if (vaultId == null) {
                event.getItem().remove();
                event.setCancelled(true);
                return;
            }

            VaultChest targetVault = placedVaults.get(vaultId);

            if (targetVault == null || !targetVault.hasValidLocation()) {
                event.getPlayer().sendMessage("§c✗ Invalid vault key!");
                event.getItem().remove();
                event.setCancelled(true);
                return;
            }

            Player player = event.getPlayer();

            if (activeAnimations.contains(vaultId)) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);

            if (config.getBoolean("settings.key-animation-enabled", true)) {
                VaultKeyAnimation animation = new VaultKeyAnimation(plugin, event.getItem(), targetVault, player, this);
                activeAnimations.add(vaultId);
                animation.start();
            } else {
                event.getItem().remove();
                activeAnimations.add(vaultId);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openVaultAutomatically(targetVault, player);
                }, 10L);
            }
        } catch (Exception e) {
            YakRealms.error("Error in player pickup event", e);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVaultInteract(PlayerInteractEvent event) {
        try {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }

            Block block = event.getClickedBlock();
            if (block == null || block.getType() != Material.VAULT) {
                return;
            }

            VaultChest vault = getVaultAtLocation(block.getLocation());
            if (vault == null) {
                return;
            }

            Player player = event.getPlayer();
            event.setCancelled(true);

            // Minimal message
            player.sendMessage("§7This vault opens automatically with keys.");
        } catch (Exception e) {
            YakRealms.error("Error in vault interact event", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVaultBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.VAULT) {
            return;
        }

        VaultChest vault = getVaultAtLocation(block.getLocation());
        if (vault != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cCannot break placed vault! Use admin commands to remove.");
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            shutdown();
        }
    }

    // ========================================
    // ENHANCED VAULT OPENING SYSTEM
    // ========================================

    public void openVaultAutomatically(VaultChest vault, Player player) {
        try {
            if (vault == null || player == null || !vault.hasValidLocation()) {
                activeAnimations.remove(vault != null ? vault.getId() : null);
                return;
            }

            vault.setOpened(true);
            vault.incrementTimesOpened();

            // Track player opens
            playerOpenCount.merge(player.getName(), 1, Integer::sum);

            // Enhanced loot generation
            generateAndDropLoot(vault, player);

            if (config.getBoolean("settings.effects-enabled", true)) {
                VaultOpenEffects effects = new VaultOpenEffects(plugin, vault, player);
                effects.start();
            }

            vaultsOpened++;
            tierOpenCount.put(vault.getTier(), tierOpenCount.get(vault.getTier()) + 1);

            // Clear animation lock
            activeAnimations.remove(vault.getId());
            needsSave = true;

            // Single success message
            player.sendMessage(vault.getTier().getColor() + "✦ " + vault.getDisplayName() +
                    " §7opened! §f" + vault.getTimesOpened() + " §7total uses");

            // Special mythic announcement
            if (vault.getTier() == ChestTier.TIER_6) {
                vault.getLocation().getWorld().getPlayers().stream()
                        .filter(p -> p.getLocation().distance(vault.getLocation()) <= 30)
                        .filter(p -> !p.equals(player))
                        .forEach(p -> {
                            p.sendMessage("§6⚡ §c" + player.getName() + " §7opened a §c§lMythic Vault§7!");
                            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.5f);
                        });
            }

        } catch (Exception e) {
            YakRealms.error("Failed to open vault", e);
            if (vault != null) {
                activeAnimations.remove(vault.getId());
            }
            if (player != null) {
                player.sendMessage("§c✗ Vault error occurred!");
            }
        }
    }

    private void generateAndDropLoot(VaultChest vault, Player player) {
        try {
            List<ItemStack> loot = new ArrayList<>();

            // Enhanced loot generation with multiple methods
            try {
                LootTableBuilder lootBuilder = LootTableBuilder.createTierBuilder(vault.getTier().getLevel(), vault.getType().getName());
                if (lootBuilder != null && !lootBuilder.isEmpty()) {
                    int baseRolls = 2 + vault.getTier().getLevel();
                    int bonusRolls = vault.getTier().getLevel();

                    // Add luck factor for frequent users
                    int playerOpens = playerOpenCount.getOrDefault(player.getName(), 0);
                    if (playerOpens > 10) {
                        bonusRolls++;
                    }

                    loot = lootBuilder.generateLoot(baseRolls, bonusRolls);
                }
            } catch (Exception e) {
                YakRealms.debug("LootTableBuilder failed, using fallback");
            }

            // Fallback generation
            if (loot == null || loot.isEmpty()) {
                loot = generateDirectTierLoot(vault.getTier(), vault.getType());
            }

            // Ultra-safe fallback
            if (loot == null || loot.isEmpty()) {
                loot = createUltraSafeLoot(vault.getTier());
            }

            // Filter and drop loot
            loot = loot.stream()
                    .filter(item -> item != null && item.getType() != Material.AIR)
                    .collect(Collectors.toList());

            if (!loot.isEmpty()) {
                Location dropLocation = vault.getLocation().clone().add(0.5, 1.0, 0.5);
                for (ItemStack item : loot) {
                    Item droppedItem = dropLocation.getWorld().dropItem(dropLocation, item);
                    droppedItem.setVelocity(droppedItem.getVelocity().multiply(0.5)); // Reduce scatter
                }
            }

        } catch (Exception e) {
            YakRealms.error("Failed to generate loot", e);
            // Emergency loot - YakRealms style
            try {
                Location dropLocation = vault.getLocation().clone().add(0.5, 1.0, 0.5);
                // Emergency gems based on tier
                int gemAmount = vault.getTier().getLevel() * 20;
                ItemStack emergencyGems = createEmergencyGems(gemAmount);
                dropLocation.getWorld().dropItem(dropLocation, emergencyGems);
            } catch (Exception e2) {
                YakRealms.error("Emergency loot failed", e2);
            }
        }
    }

    private List<ItemStack> generateDirectTierLoot(ChestTier tier, ChestType type) {
        List<ItemStack> loot = new ArrayList<>();

        try {
            int tierLevel = tier.getLevel();
            
            // Always add gems based on tier
            int gemAmount = 10 + (tierLevel * 15) + random(tierLevel * 10);
            loot.add(createYakRealmsGems(gemAmount));
            
            // Add tier-appropriate crates
            loot.add(createYakRealmsCrate(tierLevel, false));
            
            switch (tier) {
                case TIER_1:
                    if (type == ChestType.ELITE) {
                        loot.add(createYakRealmsCrate(2, false)); // Medium crate for elite
                        loot.add(createYakRealmsOrb(false)); // Normal orb
                    }
                    if (type == ChestType.FOOD) {
                        loot.add(createYakRealmsGemPouch(1)); // Small gem pouch
                        loot.add(createYakRealmsGems(gemAmount / 2)); // Extra gems
                    }
                    break;

                case TIER_2:
                    loot.add(createYakRealmsOrb(false)); // Normal orb
                    if (type == ChestType.ELITE) {
                        loot.add(createYakRealmsCrate(3, false)); // War crate for elite
                        loot.add(createYakRealmsGemPouch(2)); // Medium gem pouch
                    }
                    if (type == ChestType.FOOD) {
                        loot.add(createYakRealmsGemPouch(1)); // Small gem pouch
                        loot.add(createYakRealmsGemPouch(1)); // Another small gem pouch
                    }
                    break;

                case TIER_3:
                    loot.add(createYakRealmsOrb(false)); // Normal orb
                    loot.add(createYakRealmsCrate(3, false)); // War crate
                    if (type == ChestType.ELITE) {
                        loot.add(createYakRealmsCrate(4, false)); // Ancient crate for elite
                        loot.add(createYakRealmsOrb(true)); // Legendary orb chance
                    }
                    if (type == ChestType.FOOD) {
                        loot.add(createYakRealmsGemPouch(2)); // Medium gem pouch
                        loot.add(createYakRealmsOrb(false)); // Extra orb
                    }
                    break;

                case TIER_4:
                    loot.add(createYakRealmsCrate(4, false)); // Ancient crate
                    loot.add(createYakRealmsOrb(false)); // Normal orb
                    loot.add(createYakRealmsGemPouch(3)); // Large gem pouch
                    if (type == ChestType.ELITE) {
                        loot.add(createYakRealmsCrate(5, false)); // Legendary crate for elite
                        loot.add(createYakRealmsOrb(true)); // Legendary orb
                    }
                    if (type == ChestType.FOOD) {
                        loot.add(createYakRealmsGemPouch(2)); // Medium gem pouch
                        loot.add(createYakRealmsGemPouch(2)); // Another medium gem pouch
                    }
                    break;

                case TIER_5:
                    loot.add(createYakRealmsCrate(5, false)); // Legendary crate
                    loot.add(createYakRealmsOrb(true)); // Legendary orb
                    loot.add(createYakRealmsGemPouch(4)); // Gigantic gem container
                    if (type == ChestType.ELITE) {
                        loot.add(createYakRealmsCrate(6, false)); // Frozen crate for elite
                        loot.add(createYakRealmsOrb(true)); // Another legendary orb
                    }
                    if (type == ChestType.FOOD) {
                        loot.add(createYakRealmsGemPouch(3)); // Large gem pouch
                        loot.add(createYakRealmsOrb(false)); // Normal orb
                        loot.add(createYakRealmsOrb(false)); // Another normal orb
                    }
                    break;

                case TIER_6:
                    loot.add(createYakRealmsCrate(6, false)); // Frozen crate
                    loot.add(createYakRealmsCrate(5, false)); // Legendary crate
                    loot.add(createYakRealmsOrb(true)); // Legendary orb
                    loot.add(createYakRealmsOrb(true)); // Another legendary orb
                    loot.add(createYakRealmsGemPouch(5)); // Legendary gem container
                    if (type == ChestType.ELITE) {
                        loot.add(createYakRealmsCrate(6, true)); // Halloween Frozen crate
                        loot.add(createYakRealmsGemPouch(6)); // Insane gem container
                    }
                    if (type == ChestType.FOOD) {
                        loot.add(createYakRealmsGemPouch(4)); // Gigantic gem container
                        loot.add(createYakRealmsGemPouch(4)); // Another gigantic gem container
                        loot.add(createYakRealmsOrb(true)); // Legendary orb
                    }
                    break;
            }
        } catch (Exception e) {
            YakRealms.error("Direct tier loot generation failed", e);
        }

        return loot;
    }

    private int random(int max) {
        return new Random().nextInt(max + 1);
    }

    private List<ItemStack> createUltraSafeLoot(ChestTier tier) {
        List<ItemStack> loot = new ArrayList<>();

        try {
            // Ultra-safe YakRealms loot
            int tierLevel = tier.getLevel();
            int gemAmount = tierLevel * 25;
            
            loot.add(createYakRealmsGems(gemAmount));
            loot.add(createYakRealmsCrate(tierLevel, false));
            
            if (tierLevel >= 2) {
                loot.add(createYakRealmsOrb(false));
            }
            if (tierLevel >= 4) {
                loot.add(createYakRealmsGemPouch(Math.min(tierLevel - 1, 5)));
            }
            if (tierLevel >= 5) {
                loot.add(createYakRealmsOrb(true)); // Legendary orb for high tiers
            }
        } catch (Exception e) {
            // Ultimate fallback - just gems
            loot.add(createYakRealmsGems(50));
        }

        return loot;
    }

    // ========================================
    // ENHANCED KEY DROPPING SYSTEM
    // ========================================

    public VaultChest getNearestVault(Location location) {
        return getNearestVault(location, config.getDouble("settings.max-vault-distance", 200));
    }

    public VaultChest getNearestVault(Location location, double maxDistance) {
        if (!isLocationValid(location)) {
            return null;
        }

        return placedVaults.values().parallelStream()
                .filter(vault -> vault.hasValidLocation())
                .filter(vault -> vault.isInWorld(location.getWorld().getName()))
                .filter(vault -> !activeAnimations.contains(vault.getId()))
                .filter(vault -> vault.getDistanceTo(location) <= maxDistance)
                .min(Comparator.comparingDouble(vault -> vault.getDistanceTo(location)))
                .orElse(null);
    }

    public boolean dropKeyForNearestVault(Location dropLocation) {
        if (!isLocationValid(dropLocation)) {
            return false;
        }

        VaultChest nearestVault = getNearestVault(dropLocation);

        if (nearestVault == null) {
            return false;
        }

        try {
            ItemStack key = createVaultKey(nearestVault);
            Item droppedKey = dropLocation.getWorld().dropItem(dropLocation, key);
            droppedKey.setPickupDelay(20);
            droppedKey.setGlowing(true); // Make keys glow

            keysDropped++;
            return true;
        } catch (Exception e) {
            YakRealms.error("Failed to drop key for vault", e);
            return false;
        }
    }

    public ItemStack createVaultKey(VaultChest vault) {
        if (vault == null) {
            return null;
        }

        try {
            ItemStack key = new ItemStack(Material.TRIAL_KEY);
            ItemMeta meta = key.getItemMeta();

            if (meta == null) {
                return key;
            }

            meta.setDisplayName(vault.getTier().getColor() + "Key to " + vault.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§7Automatic vault key");
            lore.add("§7" + vault.getTier().getDisplayName() + " " + vault.getType().getName() + " Vault");
            lore.add("");
            lore.add("§8ID: " + vault.getId().substring(0, 8) + "...");

            meta.setLore(lore);

            meta.getPersistentDataContainer().set(vaultIdKey, PersistentDataType.STRING, vault.getId());
            meta.getPersistentDataContainer().set(vaultTierKey, PersistentDataType.INTEGER, vault.getTier().getLevel());
            meta.getPersistentDataContainer().set(vaultTypeKey, PersistentDataType.STRING, vault.getType().name());

            key.setItemMeta(meta);
            return key;
        } catch (Exception e) {
            YakRealms.error("Failed to create vault key", e);
            return new ItemStack(Material.TRIAL_KEY);
        }
    }

    // ========================================
    // ENHANCED SAVE/LOAD SYSTEM
    // ========================================

    private void loadPlacedVaults() {
        saveLock.readLock().lock();
        try {
            // Try loading from main file
            if (vaultDataFile.exists()) {
                loadFromFile(vaultDataFile);
            } else {
                // Try loading from most recent backup
                File latestBackup = getLatestBackup();
                if (latestBackup != null && latestBackup.exists()) {
                    YakRealms.log("Loading vaults from backup: " + latestBackup.getName());
                    loadFromFile(latestBackup);
                } else {
                    YakRealms.log("No existing vault data found, starting fresh");
                }
            }
        } finally {
            saveLock.readLock().unlock();
        }
    }

    private void loadFromFile(File file) {
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<VaultChest>>(){}.getType();
            List<VaultChest> vaults = gson.fromJson(json, listType);

            if (vaults == null) vaults = new ArrayList<>();

            placedVaults.clear();
            locationToVaultId.clear();
            vaultCache.clear();

            int loaded = 0;
            int failed = 0;

            for (VaultChest vault : vaults) {
                if (vault != null && vault.hasValidLocation()) {
                    // Validate world exists
                    if (Bukkit.getWorld(vault.getWorldName()) != null) {
                        placedVaults.put(vault.getId(), vault);
                        String locationKey = createLocationKey(vault.getLocation());
                        if (locationKey != null) {
                            locationToVaultId.put(locationKey, vault.getId());
                            loaded++;
                        }
                    } else {
                        failed++;
                    }
                }
            }

            YakRealms.log("Loaded " + loaded + " valid vaults" + (failed > 0 ? " (" + failed + " failed)" : ""));

        } catch (Exception e) {
            YakRealms.error("Failed to load vaults from " + file.getName(), e);
        }
    }

    private void savePlacedVaults() {
        needsSave = true;

        // Immediate save if critical
        if (placedVaults.size() > 0 && (System.currentTimeMillis() - lastSaveTime) > 30000) {
            performAutoSave();
        }
    }

    private void saveToFile() {
        try {
            // Create backup before saving
            if (config.getBoolean("settings.backup-enabled", true)) {
                createBackup();
            }

            List<VaultChest> validVaults = placedVaults.values().stream()
                    .filter(vault -> vault != null && vault.hasValidLocation())
                    .collect(Collectors.toList());

            File tempFile = new File(vaultDataFile.getParent(), vaultDataFile.getName() + ".tmp");

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                gson.toJson(validVaults, writer);
            }

            Files.move(tempFile.toPath(), vaultDataFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        } catch (Exception e) {
            YakRealms.error("Failed to save placed vaults", e);
        }
    }

    private void createBackup() {
        try {
            if (!vaultDataFile.exists()) return;

            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupFile = new File(backupDir, "vaults_" + timestamp + ".json");

            Files.copy(vaultDataFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Clean old backups
            cleanOldBackups();

        } catch (Exception e) {
            YakRealms.error("Failed to create backup", e);
        }
    }

    private void cleanOldBackups() {
        try {
            int maxBackups = config.getInt("settings.max-backups", 10);
            File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("vaults_") && name.endsWith(".json"));

            if (backups != null && backups.length > maxBackups) {
                Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

                for (int i = 0; i < backups.length - maxBackups; i++) {
                    backups[i].delete();
                }
            }
        } catch (Exception e) {
            YakRealms.error("Failed to clean old backups", e);
        }
    }

    private File getLatestBackup() {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("vaults_") && name.endsWith(".json"));

        if (backups == null || backups.length == 0) {
            return null;
        }

        return Arrays.stream(backups)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    private void spawnVaultBlocks() {
        int spawned = 0;
        int failed = 0;

        for (VaultChest vault : placedVaults.values()) {
            if (vault.hasValidLocation()) {
                if (createVaultBlock(vault)) {
                    spawned++;
                } else {
                    failed++;
                }
            }
        }

        YakRealms.log("Spawned " + spawned + " vault blocks" + (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    private boolean createVaultBlock(VaultChest vault) {
        try {
            if (!vault.hasValidLocation()) return false;

            Location location = vault.getLocation();
            Block block = location.getBlock();

            if (block.getType() != Material.VAULT) {
                block.setType(Material.VAULT);
            }

            if (!(block.getState() instanceof Vault)) {
                return false;
            }

            Vault vaultBlock = (Vault) block.getState();
            vaultBlock.setLootTable(LootTables.BURIED_TREASURE.getLootTable());

            vaultBlock.getPersistentDataContainer().set(vaultIdKey, PersistentDataType.STRING, vault.getId());
            vaultBlock.getPersistentDataContainer().set(vaultTierKey, PersistentDataType.INTEGER, vault.getTier().getLevel());
            vaultBlock.getPersistentDataContainer().set(vaultTypeKey, PersistentDataType.STRING, vault.getType().name());
            vaultBlock.update();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isVaultKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIAL_KEY || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(vaultIdKey, PersistentDataType.STRING);
    }

    private String getVaultIdFromKey(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(vaultIdKey, PersistentDataType.STRING);
    }

    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return String.format("%s: %d, %d, %d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ========================================
    // PUBLIC API METHODS
    // ========================================

    public boolean isVaultAvailable(String vaultId) {
        if (vaultId == null) return false;
        VaultChest vault = placedVaults.get(vaultId);
        return vault != null && vault.hasValidLocation() && !activeAnimations.contains(vaultId);
    }

    public void clearVaultAnimation(String vaultId) {
        if (vaultId != null) {
            activeAnimations.remove(vaultId);
        }
    }

    public void clearAllAnimations() {
        int cleared = activeAnimations.size();
        activeAnimations.clear();
        YakRealms.log("Cleared " + cleared + " active vault animations");
    }

    public List<VaultChest> getVaultsNear(Location location, double radius) {
        if (!isLocationValid(location)) {
            return new ArrayList<>();
        }

        return placedVaults.values().parallelStream()
                .filter(vault -> vault.hasValidLocation() &&
                        vault.isInWorld(location.getWorld().getName()) &&
                        vault.getDistanceTo(location) <= radius)
                .sorted(Comparator.comparingDouble(vault -> vault.getDistanceTo(location)))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_placed", vaultsPlaced);
        stats.put("total_opened", vaultsOpened);
        stats.put("keys_dropped", keysDropped);
        stats.put("active_vaults", placedVaults.size());
        stats.put("active_animations", activeAnimations.size());
        stats.put("instant_refresh_enabled", true);
        stats.put("tier_open_count", new HashMap<>(tierOpenCount));
        stats.put("player_open_count", new HashMap<>(playerOpenCount));

        long validVaults = placedVaults.values().stream().filter(VaultChest::hasValidLocation).count();
        stats.put("valid_vaults", validVaults);
        stats.put("invalid_vaults", placedVaults.size() - validVaults);
        stats.put("cache_size", vaultCache.size());
        stats.put("last_save", new Date(lastSaveTime).toString());

        return stats;
    }

    public Collection<VaultChest> getAllPlacedVaults() {
        return new ArrayList<>(placedVaults.values());
    }

    public VaultChest getVaultById(String vaultId) {
        return vaultId != null ? placedVaults.get(vaultId) : null;
    }

    public void forceSave() {
        saveLock.writeLock().lock();
        try {
            saveToFile();
            needsSave = false;
            lastSaveTime = System.currentTimeMillis();
            YakRealms.log("Force saved vault data");
        } finally {
            saveLock.writeLock().unlock();
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        YakRealms.log("Reloaded vault configuration");
    }

    public void shutdown() {
        try {
            // Cancel auto-save task
            if (autoSaveTask != null) {
                autoSaveTask.cancel();
            }

            // Final save
            forceSave();

            // Clear all caches and animations
            activeAnimations.clear();
            vaultCache.clear();
            cacheTimestamps.clear();

            YakRealms.log("LootChestManager shutdown complete");
        } catch (Exception e) {
            YakRealms.error("Error during shutdown", e);
        }
    }

    // ========================================
    // CUSTOM SERIALIZERS
    // ========================================

    private static class LocationSerializer implements JsonSerializer<Location>, JsonDeserializer<Location> {
        @Override
        public JsonElement serialize(Location src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("world", src.getWorld().getName());
            obj.addProperty("x", src.getX());
            obj.addProperty("y", src.getY());
            obj.addProperty("z", src.getZ());
            obj.addProperty("yaw", src.getYaw());
            obj.addProperty("pitch", src.getPitch());
            return obj;
        }

        @Override
        public Location deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String worldName = obj.get("world").getAsString();
            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            return new Location(
                    world,
                    obj.get("x").getAsDouble(),
                    obj.get("y").getAsDouble(),
                    obj.get("z").getAsDouble(),
                    obj.get("yaw").getAsFloat(),
                    obj.get("pitch").getAsFloat()
            );
        }
    }

    private class VaultChestSerializer implements JsonSerializer<VaultChest>, JsonDeserializer<VaultChest> {
        @Override
        public JsonElement serialize(VaultChest src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.getId());
            obj.add("location", context.serialize(src.getLocation()));
            obj.addProperty("tier", src.getTier().name());
            obj.addProperty("type", src.getType().name());
            obj.addProperty("createdTime", src.getCreatedTime());
            obj.addProperty("createdBy", src.getCreatedBy());
            obj.addProperty("timesOpened", src.getTimesOpened());
            obj.addProperty("lastOpenedTime", src.getLastOpenedTime());
            return obj;
        }

        @Override
        public VaultChest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String id = obj.get("id").getAsString();
            Location location = context.deserialize(obj.get("location"), Location.class);
            ChestTier tier = ChestTier.valueOf(obj.get("tier").getAsString());
            ChestType type = ChestType.valueOf(obj.get("type").getAsString());

            VaultChest vault = new VaultChest(id, location, tier, type);

            // Set additional properties
            if (obj.has("createdBy")) vault.setCreatedBy(obj.get("createdBy").getAsString());
            if (obj.has("timesOpened")) {
                int timesOpened = obj.get("timesOpened").getAsInt();
                for (int i = 0; i < timesOpened; i++) {
                    vault.incrementTimesOpened();
                }
            }

            return vault;
        }
    }

    // ========================================
    // YAKREALMS INTEGRATION METHODS
    // ========================================

    /**
     * Create YakRealms gems with proper metadata
     */
    private ItemStack createYakRealmsGems(int amount) {
        try {
            ItemStack gems = new ItemStack(Material.EMERALD, Math.min(amount, 64));
            ItemMeta meta = gems.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§fGem");
                meta.setLore(Arrays.asList("§7The currency of Andalucia", "§a⚡ From Vault Chest"));
                gems.setItemMeta(meta);
            }
            return gems;
        } catch (Exception e) {
            YakRealms.error("Failed to create YakRealms gems", e);
            return new ItemStack(Material.EMERALD, Math.min(amount, 64));
        }
    }

    /**
     * Create emergency gems with basic metadata
     */
    private ItemStack createEmergencyGems(int amount) {
        try {
            ItemStack gems = new ItemStack(Material.EMERALD, Math.min(amount, 64));
            ItemMeta meta = gems.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§fGem");
                meta.setLore(Arrays.asList("§7The currency of Andalucia"));
                gems.setItemMeta(meta);
            }
            return gems;
        } catch (Exception e) {
            return new ItemStack(Material.EMERALD, Math.min(amount, 64));
        }
    }

    /**
     * Create YakRealms crates using CrateFactory if available
     */
    private ItemStack createYakRealmsCrate(int tier, boolean halloween) {
        try {
            // Try to use CrateFactory first
            if (plugin.getServer().getPluginManager().isPluginEnabled("YakRealms")) {
                // Use reflection or direct access to CrateFactory if available
                // For now, create manual crates with proper naming
                return createManualCrate(tier, halloween);
            }
        } catch (Exception e) {
            YakRealms.debug("CrateFactory not available, using manual crate creation");
        }
        
        return createManualCrate(tier, halloween);
    }

    /**
     * Create manual crates with YakRealms styling
     */
    private ItemStack createManualCrate(int tier, boolean halloween) {
        try {
            Material crateMaterial = getCrateMaterialForTier(tier);
            ItemStack crate = new ItemStack(halloween ? Material.CARVED_PUMPKIN : crateMaterial);
            ItemMeta meta = crate.getItemMeta();
            
            if (meta != null) {
                String crateName = getCrateNameForTier(tier);
                String crateSymbol = getCrateSymbolForTier(tier);
                String crateColor = getCrateColorForTier(tier);
                String qualityName = getTierQualityName(tier);
                
                String displayName = crateColor + crateSymbol + " " + crateColor;
                if (halloween) displayName += "Halloween ";
                displayName += crateName + " Mystical Crate " + crateColor + crateSymbol;
                
                meta.setDisplayName(displayName);
                
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("§7Quality: " + crateColor + qualityName);
                lore.add("§7Tier: §7[" + tier + "/6]");
                if (halloween) {
                    lore.add("§7Season: §6§l🎃 Halloween Special 🎃");
                }
                lore.add("");
                lore.add("§e⚠ §lMystical Contents:");
                lore.add(crateColor + "• Tier " + tier + " treasures");
                if (halloween) {
                    lore.add("§6🎃 Halloween Bonus Rewards");
                }
                lore.add("");
                lore.add("§a⚡ From Vault Chest");
                
                meta.setLore(lore);
                
                // Add glow for higher tiers or halloween
                if (tier >= 3 || halloween) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                
                crate.setItemMeta(meta);
            }
            
            return crate;
        } catch (Exception e) {
            YakRealms.error("Failed to create manual crate", e);
            return new ItemStack(Material.CHEST);
        }
    }

    /**
     * Create YakRealms orbs using OrbGenerator if available
     */
    private ItemStack createYakRealmsOrb(boolean legendary) {
        try {
            // Try to use OrbGenerator first
            if (plugin.getServer().getPluginManager().isPluginEnabled("YakRealms")) {
                // Use reflection or direct access to OrbGenerator if available
                // For now, create manual orbs
                return createManualOrb(legendary);
            }
        } catch (Exception e) {
            YakRealms.debug("OrbGenerator not available, using manual orb creation");
        }
        
        return createManualOrb(legendary);
    }

    /**
     * Create manual orbs with YakRealms styling
     */
    private ItemStack createManualOrb(boolean legendary) {
        try {
            ItemStack orb = new ItemStack(Material.MAGMA_CREAM);
            ItemMeta meta = orb.getItemMeta();
            
            if (meta != null) {
                if (legendary) {
                    meta.setDisplayName("§6L§ee§ag§be§cn§dd§ea§fr§gy §6Orb of Alteration");
                    meta.setLore(Arrays.asList(
                        "§7Plus 4s Items that have a plus lower than 4.",
                        "§7It also has a extremely high chance of good orbs.",
                        "§a⚡ From Vault Chest"
                    ));
                    
                    // Add glow
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                } else {
                    meta.setDisplayName("§dOrb of Alteration");
                    meta.setLore(Arrays.asList(
                        "§7Randomizes stats of selected equipment.",
                        "§a⚡ From Vault Chest"
                    ));
                }
                
                orb.setItemMeta(meta);
            }
            
            return orb;
        } catch (Exception e) {
            YakRealms.error("Failed to create manual orb", e);
            return new ItemStack(Material.MAGMA_CREAM);
        }
    }

    /**
     * Create YakRealms gem pouches using GemPouchManager if available
     */
    private ItemStack createYakRealmsGemPouch(int tier) {
        try {
            // Try to use GemPouchManager first
            if (plugin.getServer().getPluginManager().isPluginEnabled("YakRealms")) {
                // Use reflection or direct access to GemPouchManager if available
                // For now, create manual gem pouches
                return createManualGemPouch(tier);
            }
        } catch (Exception e) {
            YakRealms.debug("GemPouchManager not available, using manual gem pouch creation");
        }
        
        return createManualGemPouch(tier);
    }

    /**
     * Create manual gem pouches with YakRealms styling
     */
    private ItemStack createManualGemPouch(int tier) {
        try {
            ItemStack pouch = new ItemStack(Material.INK_SAC);
            ItemMeta meta = pouch.getItemMeta();
            
            if (meta != null) {
                String pouchName = getGemPouchNameForTier(tier);
                String pouchLore = getGemPouchLoreForTier(tier);
                
                meta.setDisplayName(pouchName + " §a§l0g");
                
                List<String> lore = new ArrayList<>();
                lore.add(pouchLore);
                if (tier == 6) {
                    lore.add("");
                    lore.add("§cSoulbound");
                }
                lore.add("§a⚡ From Vault Chest");
                
                meta.setLore(lore);
                
                // Add glow for higher tier pouches
                if (tier >= 4) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                
                pouch.setItemMeta(meta);
            }
            
            return pouch;
        } catch (Exception e) {
            YakRealms.error("Failed to create manual gem pouch", e);
            return new ItemStack(Material.INK_SAC);
        }
    }

    // Helper methods for item creation
    private Material getCrateMaterialForTier(int tier) {
        return switch (tier) {
            case 1, 2 -> Material.CHEST;
            case 3 -> Material.TRAPPED_CHEST;
            case 4, 5, 6 -> Material.ENDER_CHEST;
            default -> Material.CHEST;
        };
    }

    private String getCrateNameForTier(int tier) {
        return switch (tier) {
            case 1 -> "Basic";
            case 2 -> "Medium";
            case 3 -> "War";
            case 4 -> "Ancient";
            case 5 -> "Legendary";
            case 6 -> "Frozen";
            default -> "Basic";
        };
    }

    private String getCrateSymbolForTier(int tier) {
        return switch (tier) {
            case 1 -> "◆";
            case 2 -> "◇";
            case 3 -> "★";
            case 4 -> "✦";
            case 5 -> "★";
            case 6 -> "❅";
            default -> "◆";
        };
    }

    private String getCrateColorForTier(int tier) {
        return switch (tier) {
            case 1 -> "§7";
            case 2 -> "§a";
            case 3 -> "§b";
            case 4 -> "§d";
            case 5 -> "§6";
            case 6 -> "§6";
            default -> "§7";
        };
    }

    private String getTierQualityName(int tier) {
        return switch (tier) {
            case 1 -> "§fCommon";
            case 2 -> "§aUncommon";
            case 3 -> "§bRare";
            case 4 -> "§dEpic";
            case 5 -> "§6Legendary";
            case 6 -> "§6Mythical";
            default -> "§fCommon";
        };
    }

    private String getGemPouchNameForTier(int tier) {
        return switch (tier) {
            case 1 -> "§fSmall Gem Pouch";
            case 2 -> "§aMedium Gem Sack";
            case 3 -> "§bLarge Gem Satchel";
            case 4 -> "§dGigantic Gem Container";
            case 5 -> "§6Legendary Gem Container";
            case 6 -> "§cInsane Gem Container";
            default -> "§fSmall Gem Pouch";
        };
    }

    private String getGemPouchLoreForTier(int tier) {
        return switch (tier) {
            case 1 -> "§7A small linen pouch that holds §l200g";
            case 2 -> "§7A medium wool sack that holds §l350g";
            case 3 -> "§7A large leather satchel that holds §l500g";
            case 4 -> "§7A giant container that holds §l3000g";
            case 5 -> "§7A giant container that holds §l8000g";
            case 6 -> "§7A giant container that holds §l100000g";
            default -> "§7A small linen pouch that holds §l200g";
        };
    }
}
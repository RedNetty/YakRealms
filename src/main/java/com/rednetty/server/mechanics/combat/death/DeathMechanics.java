package com.rednetty.server.mechanics.combat.death;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.combat.death.remnant.DeathRemnantManager;
import com.rednetty.server.mechanics.combat.logout.CombatLogoutMechanics;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.listeners.PlayerListenerManager;
import com.rednetty.server.mechanics.player.stamina.Energy;
import com.rednetty.server.mechanics.world.WorldGuardManager;
import com.rednetty.server.utils.inventory.InventoryUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UPDATED DeathMechanics - works with FIXED combat logout system
 *
 * UPDATED APPROACH:
 * - Combat logout is handled completely by CombatLogoutMechanics
 * - This class only handles normal deaths (not combat logout deaths)
 * - Simplified item processing since combat logout is separate
 * - Focus on normal death mechanics and respawn handling
 * - Shows appropriate messages for combat logout consequences on respawn
 */
public class DeathMechanics implements Listener {

    private static final long RESPAWN_RESTORE_DELAY = 20L;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private static DeathMechanics instance;

    // Simplified tracking - only for normal deaths
    private final Map<UUID, DeathInfo> recentDeaths = new ConcurrentHashMap<>();
    private final Set<UUID> respawnInProgress = ConcurrentHashMap.newKeySet();

    // Performance tracking
    private final AtomicInteger totalDeaths = new AtomicInteger(0);
    private final AtomicInteger normalDeaths = new AtomicInteger(0);
    private final AtomicInteger combatLogoutDeathMessages = new AtomicInteger(0);

    // Dependencies
    private final DeathRemnantManager remnantManager;
    private final YakPlayerManager playerManager;
    private final CombatLogoutMechanics combatLogoutMechanics;

    public DeathMechanics() {
        this.remnantManager = new DeathRemnantManager(YakRealms.getInstance());
        this.playerManager = YakPlayerManager.getInstance();
        this.combatLogoutMechanics = CombatLogoutMechanics.getInstance();
    }

    public static DeathMechanics getInstance() {
        if (instance == null) {
            instance = new DeathMechanics();
        }
        return instance;
    }

    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("UPDATED DeathMechanics enabled with combat logout coordination");
    }

    public void onDisable() {
        if (remnantManager != null) {
            remnantManager.shutdown();
        }

        recentDeaths.clear();
        respawnInProgress.clear();

        YakRealms.log("UPDATED DeathMechanics disabled");
    }

    /**
     * UPDATED death handler - only handles normal deaths now
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player == null) {
            YakRealms.warn("PlayerDeathEvent with null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("Player " + player.getName() + " has null UUID");
            return;
        }

        totalDeaths.incrementAndGet();
        YakRealms.log("=== UPDATED DEATH EVENT START: " + player.getName() + " ===");

        try {
            // Get YakPlayer data
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                YakRealms.warn("No YakPlayer data for " + player.getName() + " during death");
                return;
            }

            // UPDATED: Check if this is related to combat logout
            // Combat logout deaths are NOT processed here - they're handled by CombatLogoutMechanics
            if (combatLogoutMechanics.hasActiveCombatLogout(playerId)) {
                YakRealms.log("Skipping death processing - combat logout already handled for " + player.getName());
                // Just create a decorative remnant and let the normal death process continue
                createDecorativeRemnant(player.getLocation(), player);
                return;
            }

            // Handle normal death
            handleNormalDeath(event, player, yakPlayer);
            normalDeaths.incrementAndGet();

        } catch (Exception e) {
            YakRealms.error("Error in updated death event for " + player.getName(), e);
        } finally {
            YakRealms.log("=== UPDATED DEATH EVENT END: " + player.getName() + " ===");
        }
    }

    /**
     * Handle normal death - process items normally
     */
    private void handleNormalDeath(PlayerDeathEvent event, Player player, YakPlayer yakPlayer) {
        YakRealms.log("Handling normal death for " + player.getName());

        try {
            String alignment = yakPlayer.getAlignment();
            if (alignment == null || alignment.trim().isEmpty()) {
                alignment = "LAWFUL";
                yakPlayer.setAlignment(alignment);
            }

            Location deathLocation = player.getLocation();

            // Get all player items
            List<ItemStack> allItems = InventoryUtils.getAllPlayerItems(player);
            if (allItems == null) allItems = new ArrayList<>();

            YakRealms.log("Processing " + allItems.size() + " items for normal death (alignment: " + alignment + ")");

            // Process items by alignment
            ProcessResult result = processItemsByAlignment(allItems, alignment, player);

            // Clear event drops and handle them ourselves
            event.getDrops().clear();
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // Drop items that should be dropped
            dropItemsAtLocation(deathLocation, result.droppedItems, player.getName());

            // Clear player inventory
            InventoryUtils.clearPlayerInventory(player);

            // Store kept items for respawn
            storeKeptItemsForRespawn(yakPlayer, result.keptItems);

            // Create death remnant
            createDecorativeRemnant(deathLocation, player);

            // Update death count
            yakPlayer.setDeaths(yakPlayer.getDeaths() + 1);

            // Save player data
            savePlayerDataSafely(yakPlayer);

            // Store death info for respawn handling
            DeathInfo deathInfo = new DeathInfo(
                    deathLocation,
                    alignment,
                    false, // not combat logout death
                    System.currentTimeMillis()
            );
            recentDeaths.put(player.getUniqueId(), deathInfo);

            YakRealms.log("Normal death processed for " + player.getName() +
                    ". Kept: " + result.keptItems.size() + ", Dropped: " + result.droppedItems.size());

        } catch (Exception e) {
            YakRealms.error("Error handling normal death for " + player.getName(), e);
        }
    }

    /**
     * Process items by alignment for normal deaths
     */
    private ProcessResult processItemsByAlignment(List<ItemStack> allItems, String alignment, Player player) {
        ProcessResult result = new ProcessResult();

        if (allItems.isEmpty()) {
            return result;
        }

        // Get first hotbar item for weapon detection
        ItemStack firstHotbarItem = getFirstHotbarItem(allItems);

        switch (alignment) {
            case "LAWFUL":
                processLawfulDeathItems(allItems, firstHotbarItem, player, result);
                break;
            case "NEUTRAL":
                processNeutralDeathItems(allItems, firstHotbarItem, result);
                break;
            case "CHAOTIC":
                processChaoticDeathItems(allItems, result);
                break;
            default:
                YakRealms.warn("Unknown alignment: " + alignment + " - defaulting to lawful");
                processLawfulDeathItems(allItems, firstHotbarItem, player, result);
                break;
        }

        return result;
    }

    private void processLawfulDeathItems(List<ItemStack> allItems, ItemStack firstHotbarItem, Player player, ProcessResult result) {
        List<ItemStack> equippedArmor = InventoryUtils.getEquippedArmor(player);

        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            // Keep equipped armor and first hotbar item
            boolean isEquippedArmor = equippedArmor.stream().anyMatch(e -> e != null && e.isSimilar(item));
            boolean isFirstHotbar = firstHotbarItem != null && item.isSimilar(firstHotbarItem);
            boolean isPermanentUntradeable = InventoryUtils.isPermanentUntradeable(safeCopy);

            if (isEquippedArmor || isFirstHotbar || isPermanentUntradeable) {
                result.keptItems.add(safeCopy);
                YakRealms.log("LAWFUL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                YakRealms.log("LAWFUL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private void processNeutralDeathItems(List<ItemStack> allItems, ItemStack firstHotbarItem, ProcessResult result) {
        Random random = new Random();
        boolean shouldDropWeapon = random.nextInt(2) == 0;
        boolean shouldDropArmor = random.nextInt(4) == 0;

        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            boolean shouldKeep = InventoryUtils.isPermanentUntradeable(safeCopy) ||
                    (InventoryUtils.isArmorItem(safeCopy) && !shouldDropArmor) ||
                    (firstHotbarItem != null && item.isSimilar(firstHotbarItem) && !shouldDropWeapon);

            if (shouldKeep) {
                result.keptItems.add(safeCopy);
                YakRealms.log("NEUTRAL KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                YakRealms.log("NEUTRAL DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private void processChaoticDeathItems(List<ItemStack> allItems, ProcessResult result) {
        for (ItemStack item : allItems) {
            if (!InventoryUtils.isValidItem(item)) continue;

            ItemStack safeCopy = InventoryUtils.createSafeCopy(item);
            if (safeCopy == null) continue;

            if (InventoryUtils.isPermanentUntradeable(safeCopy) || InventoryUtils.isQuestItem(safeCopy)) {
                result.keptItems.add(safeCopy);
                YakRealms.log("CHAOTIC KEEPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            } else {
                result.droppedItems.add(safeCopy);
                YakRealms.log("CHAOTIC DROPPING: " + InventoryUtils.getItemDisplayName(safeCopy));
            }
        }
    }

    private ItemStack getFirstHotbarItem(List<ItemStack> allItems) {
        try {
            for (int i = 0; i < Math.min(9, allItems.size()); i++) {
                ItemStack item = allItems.get(i);
                if (InventoryUtils.isValidItem(item)) {
                    return InventoryUtils.createSafeCopy(item);
                }
            }
            return null;
        } catch (Exception e) {
            YakRealms.error("Error getting first hotbar item", e);
            return null;
        }
    }

    /**
     * Drop items at death location
     */
    private void dropItemsAtLocation(Location location, List<ItemStack> items, String playerName) {
        if (location == null || location.getWorld() == null || items.isEmpty()) {
            return;
        }

        YakRealms.log("Dropping " + items.size() + " items at death location for " + playerName);

        for (ItemStack item : items) {
            if (!InventoryUtils.isValidItem(item)) continue;

            try {
                location.getWorld().dropItemNaturally(location, item);
                YakRealms.log("Dropped: " + InventoryUtils.getItemDisplayName(item));
            } catch (Exception e) {
                YakRealms.warn("Failed to drop item: " + InventoryUtils.getItemDisplayName(item) + " - " + e.getMessage());
            }
        }
    }

    /**
     * Store kept items for respawn using YakPlayer's respawn item system
     */
    private void storeKeptItemsForRespawn(YakPlayer yakPlayer, List<ItemStack> keptItems) {
        try {
            if (keptItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                YakRealms.log("No items to keep for respawn: " + yakPlayer.getUsername());
                return;
            }

            // Filter valid items
            List<ItemStack> validItems = new ArrayList<>();
            for (ItemStack item : keptItems) {
                if (InventoryUtils.isValidItem(item)) {
                    validItems.add(item);
                }
            }

            if (validItems.isEmpty()) {
                yakPlayer.clearRespawnItems();
                return;
            }

            boolean success = yakPlayer.setRespawnItems(validItems);
            if (success) {
                YakRealms.log("Stored " + validItems.size() + " items for respawn: " + yakPlayer.getUsername());
            } else {
                YakRealms.warn("Failed to store respawn items for: " + yakPlayer.getUsername());
            }

        } catch (Exception e) {
            YakRealms.error("Error storing kept items for respawn: " + yakPlayer.getUsername(), e);
        }
    }

    /**
     * UPDATED respawn handler - coordinates with combat logout system
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            YakRealms.warn("PlayerRespawnEvent with null player");
            return;
        }

        UUID playerId = player.getUniqueId();
        if (playerId == null) {
            YakRealms.warn("Player " + player.getName() + " has null UUID during respawn");
            return;
        }

        YakRealms.log("Processing respawn for " + player.getName());

        // Check if already processing respawn
        if (!respawnInProgress.add(playerId)) {
            YakRealms.log("Respawn already in progress for " + player.getName());
            return;
        }

        try {
            YakPlayer yakPlayer = playerManager.getPlayer(player);
            if (yakPlayer == null) {
                YakRealms.warn("No YakPlayer data for " + player.getName() + " during respawn");
                respawnInProgress.remove(playerId);
                return;
            }

            DeathInfo deathInfo = recentDeaths.get(playerId);

            // UPDATED: Check if this player had a combat logout
            boolean wasCombatLogout = yakPlayer.getCombatLogoutState() == YakPlayer.CombatLogoutState.COMPLETED;

            // Set respawn location based on alignment
            setRespawnLocation(event, yakPlayer, player);

            // Schedule post-respawn processing
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !player.isDead()) {
                        handlePostRespawn(player, yakPlayer, deathInfo, wasCombatLogout);
                    }
                    respawnInProgress.remove(playerId);
                    recentDeaths.remove(playerId);
                }
            }.runTaskLater(YakRealms.getInstance(), RESPAWN_RESTORE_DELAY);

            // Add respawn effects
            addRespawnEffects(player);

        } catch (Exception e) {
            YakRealms.error("Error handling respawn for " + player.getName(), e);
            respawnInProgress.remove(playerId);
        }
    }

    /**
     * Set respawn location based on alignment
     */
    private void setRespawnLocation(PlayerRespawnEvent event, YakPlayer yakPlayer, Player player) {
        try {
            World world = Bukkit.getWorlds().get(0);
            if (world == null) {
                YakRealms.warn("No world available for respawn");
                return;
            }

            String alignment = yakPlayer.getAlignment();
            if (alignment == null) {
                alignment = "LAWFUL";
            }

            // UPDATED: All players respawn at spawn for now (combat logout players already at spawn)
            // Chaotic players get random location in post-respawn
            Location spawnLocation = world.getSpawnLocation();
            if (spawnLocation != null) {
                spawnLocation.setY(world.getHighestBlockYAt(spawnLocation) + 1);
                event.setRespawnLocation(spawnLocation);
                YakRealms.log("Set spawn location for respawn: " + player.getName() + " (alignment: " + alignment + ")");
            }

        } catch (Exception e) {
            YakRealms.error("Error setting respawn location for " + player.getName(), e);
        }
    }

    /**
     * UPDATED: Handle post-respawn processing with combat logout awareness
     */
    private void handlePostRespawn(Player player, YakPlayer yakPlayer, DeathInfo deathInfo, boolean wasCombatLogout) {
        try {
            YakRealms.log("Handling post-respawn for " + player.getName() +
                    (wasCombatLogout ? " (combat logout completed)" : ""));

            // UPDATED: Show combat logout completion message if applicable
            if (wasCombatLogout) {
                showCombatLogoutCompletionMessage(player, yakPlayer.getAlignment());
                combatLogoutDeathMessages.incrementAndGet();

                // Clear combat logout state now that respawn is complete
                yakPlayer.setCombatLogoutState(YakPlayer.CombatLogoutState.NONE);
                playerManager.savePlayer(yakPlayer);
            } else {
                // Show normal death message if it was a combat logout death
                if (deathInfo != null && deathInfo.wasCombatLogout) {
                    showCombatLogoutDeathMessage(player, deathInfo.alignment);
                }
            }

            // Restore kept items if this was a normal death
            if (yakPlayer.hasRespawnItems()) {
                restoreRespawnItems(player, yakPlayer);
            } else {
                YakRealms.log("No respawn items to restore for " + player.getName());
            }

            // Handle chaotic player random teleportation
            String alignment = yakPlayer.getAlignment();
            if ("CHAOTIC".equals(alignment) && !wasCombatLogout) {
                teleportChaoticPlayerToRandomLocation(player, yakPlayer);
            }

            // Initialize respawned player
            initializeRespawnedPlayer(player, yakPlayer);

            // Recalculate health
            try {
                if (PlayerListenerManager.getInstance() != null &&
                        PlayerListenerManager.getInstance().getHealthListener() != null) {
                    PlayerListenerManager.getInstance().getHealthListener().recalculateHealth(player);
                }
            } catch (Exception e) {
                YakRealms.warn("Could not recalculate health for " + player.getName());
            }

        } catch (Exception e) {
            YakRealms.error("Error in post-respawn handling for " + player.getName(), e);
        }
    }

    /**
     * UPDATED: Show combat logout completion message
     */
    private void showCombatLogoutCompletionMessage(Player player, String alignment) {
        try {
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT CONSEQUENCES COMPLETE");
                    player.sendMessage(ChatColor.GRAY + "Your items were processed according to your " + alignment.toLowerCase() + " alignment.");
                    player.sendMessage(ChatColor.GRAY + "You have respawned at the spawn location.");
                    player.sendMessage(ChatColor.YELLOW + "You may now continue playing normally.");
                    player.sendMessage("");

                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }, 40L);
        } catch (Exception e) {
            YakRealms.error("Error showing combat logout completion message", e);
        }
    }

    private void showCombatLogoutDeathMessage(Player player, String alignment) {
        try {
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "COMBAT LOGOUT PUNISHMENT COMPLETED!");
                    player.sendMessage(ChatColor.GRAY + "Items were processed according to your " + alignment.toLowerCase() + " alignment.");
                    player.sendMessage(ChatColor.GRAY + "You have respawned at the appropriate location.");
                    player.sendMessage("");

                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
                }
            }, 40L);
        } catch (Exception e) {
            YakRealms.error("Error showing combat logout death message", e);
        }
    }

    /**
     * Teleport chaotic player to random location
     */
    private void teleportChaoticPlayerToRandomLocation(Player player, YakPlayer yakPlayer) {
        try {
            World world = player.getWorld();
            Location randomLocation = generateRandomSpawnPoint(player.getName(), world);

            player.teleport(randomLocation);
            yakPlayer.updateLocation(randomLocation);

            player.sendMessage(ChatColor.RED + "As a chaotic player, you have been sent to a random location!");
            YakRealms.log("Teleported chaotic player " + player.getName() + " to random location");

        } catch (Exception e) {
            YakRealms.error("Error teleporting chaotic player to random location", e);
        }
    }

    private Location generateRandomSpawnPoint(String playerName, World world) {
        try {
            Random random = new Random(playerName.hashCode() + System.currentTimeMillis());

            for (int attempts = 0; attempts < 10; attempts++) {
                double x = (random.nextDouble() - 0.5) * 2000;
                double z = (random.nextDouble() - 0.5) * 2000;
                int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
                Location loc = new Location(world, x, y, z);

                if (WorldGuardManager.getInstance() != null &&
                        !WorldGuardManager.getInstance().isSafeZone(loc)) {
                    return loc;
                }
            }

            // Fallback location
            double x = (random.nextDouble() - 0.5) * 2000;
            double z = (random.nextDouble() - 0.5) * 2000;
            return new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z);

        } catch (Exception e) {
            YakRealms.error("Error generating random spawn point", e);
            return world.getSpawnLocation();
        }
    }

    /**
     * Restore items that were kept from death
     */
    private void restoreRespawnItems(Player player, YakPlayer yakPlayer) {
        try {
            List<ItemStack> items = yakPlayer.getRespawnItems();
            if (items == null || items.isEmpty()) {
                return;
            }

            YakRealms.log("Restoring " + items.size() + " respawn items for " + player.getName());

            // Clear inventory first
            InventoryUtils.clearPlayerInventory(player);

            // Separate armor and regular items
            ItemStack[] armor = new ItemStack[4];
            List<ItemStack> regularItems = new ArrayList<>();

            for (ItemStack item : items) {
                if (InventoryUtils.isArmorItem(item)) {
                    int slot = InventoryUtils.getArmorSlot(item);
                    if (slot >= 0 && slot < 4 && armor[slot] == null) {
                        armor[slot] = item;
                    } else {
                        regularItems.add(item);
                    }
                } else {
                    regularItems.add(item);
                }
            }

            // Set armor
            player.getInventory().setArmorContents(armor);

            // Set regular items
            for (int i = 0; i < regularItems.size() && i < 36; i++) {
                player.getInventory().setItem(i, regularItems.get(i));
            }

            // Clear respawn items after restoration
            yakPlayer.clearRespawnItems();
            savePlayerDataSafely(yakPlayer);

            player.updateInventory();
            YakRealms.log("Successfully restored items for " + player.getName());

        } catch (Exception e) {
            YakRealms.error("Error restoring respawn items for " + player.getName(), e);
        }
    }

    /**
     * Initialize respawned player
     */
    private void initializeRespawnedPlayer(Player player, YakPlayer yakPlayer) {
        try {
            YakRealms.log("Initializing respawned player: " + player.getName());

            // Set health values
            double maxHealth = Math.max(1.0, Math.min(2048.0, 50.0));
            double currentHealth = Math.max(1.0, Math.min(maxHealth, 50.0));

            player.setMaxHealth(maxHealth);
            player.setHealth(currentHealth);
            player.setLevel(100);
            player.setExp(1.0f);

            // Energy system integration
            if (yakPlayer.hasTemporaryData("energy")) {
                yakPlayer.setTemporaryData("energy", 100);
            }

            try {
                if (Energy.getInstance() != null) {
                    Energy.getInstance().setEnergy(yakPlayer, 100);
                }
            } catch (Exception e) {
                YakRealms.warn("Could not set energy for " + player.getName());
            }

            player.getInventory().setHeldItemSlot(0);

        } catch (Exception e) {
            YakRealms.error("Error initializing respawned player " + player.getName(), e);
        }
    }

    private void addRespawnEffects(Player player) {
        try {
            Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
                if (player.isOnline() && !player.isDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1));
                }
            }, 15L);
        } catch (Exception e) {
            YakRealms.error("Error adding respawn effects", e);
        }
    }

    private void createDecorativeRemnant(Location location, Player player) {
        try {
            if (remnantManager != null) {
                remnantManager.createDeathRemnant(location, Collections.emptyList(), player);
                YakRealms.log("Created decorative death remnant for " + player.getName());
            }
        } catch (Exception e) {
            YakRealms.warn("Failed to create death remnant for " + player.getName());
        }
    }

    private boolean savePlayerDataSafely(YakPlayer yakPlayer) {
        try {
            return playerManager.savePlayer(yakPlayer).get();
        } catch (Exception e) {
            YakRealms.error("Failed to save player data for " + yakPlayer.getUsername(), e);
            return false;
        }
    }

    // Public API methods
    public boolean hasRespawnItems(UUID playerId) {
        if (playerId == null) return false;
        YakPlayer yakPlayer = playerManager.getPlayer(playerId);
        return yakPlayer != null && yakPlayer.hasRespawnItems();
    }

    public boolean isProcessingRespawn(UUID playerId) {
        return playerId != null && respawnInProgress.contains(playerId);
    }

    public DeathRemnantManager getRemnantManager() {
        return remnantManager;
    }

    // Performance tracking
    public int getTotalDeaths() {
        return totalDeaths.get();
    }

    public int getNormalDeaths() {
        return normalDeaths.get();
    }

    public int getCombatLogoutDeathMessages() {
        return combatLogoutDeathMessages.get();
    }

    // Helper classes
    private static class ProcessResult {
        final List<ItemStack> keptItems = new ArrayList<>();
        final List<ItemStack> droppedItems = new ArrayList<>();
    }

    private static class DeathInfo {
        final Location deathLocation;
        final String alignment;
        final boolean wasCombatLogout;
        final long timestamp;

        DeathInfo(Location deathLocation, String alignment, boolean wasCombatLogout, long timestamp) {
            this.deathLocation = deathLocation != null ? deathLocation.clone() : null;
            this.alignment = alignment;
            this.wasCombatLogout = wasCombatLogout;
            this.timestamp = timestamp;
        }
    }
}
package com.rednetty.server.mechanics.drops;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.drops.buff.LootBuffManager;
import com.rednetty.server.mechanics.drops.types.EliteDropConfig;
import com.rednetty.server.mechanics.mobs.MobManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Handles event processing for item drops from mobs
 */
public class DropsHandler implements Listener {
    private static DropsHandler instance;
    private final YakRealms plugin;
    private final Logger logger;
    private final DropsManager dropsManager;
    private final DropFactory dropFactory;
    private final MobManager mobManager;
    private final LootNotifier lootNotifier;
    private final LootBuffManager lootBuffManager;

    // Track damage contributions for loot allocation
    private final Map<UUID, Map<UUID, Double>> mobDamageTracking = new ConcurrentHashMap<>();

    // Track processed mobs to prevent double drops
    private final Set<UUID> processedMobs = Collections.synchronizedSet(new HashSet<>());

    /**
     * Private constructor for singleton pattern
     */
    private DropsHandler() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.dropsManager = DropsManager.getInstance();
        this.dropFactory = DropFactory.getInstance();
        this.mobManager = MobManager.getInstance();
        this.lootNotifier = LootNotifier.getInstance();
        this.lootBuffManager = LootBuffManager.getInstance();
    }

    /**
     * Gets the singleton instance
     *
     * @return The DropsHandler instance
     */
    public static DropsHandler getInstance() {
        if (instance == null) {
            instance = new DropsHandler();
        }
        return instance;
    }

    /**
     * Initializes the drops handler
     */
    public void initialize() {
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Start cleanup task
        startCleanupTask();

        logger.info("[DropsHandler] has been initialized");
    }

    /**
     * Starts a task to clean up old damage tracking data
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Clean up damage tracking for entities that no longer exist
                mobDamageTracking.entrySet().removeIf(entry -> {
                    Entity entity = Bukkit.getEntity(entry.getKey());
                    return entity == null || !entity.isValid();
                });
            }
        }.runTaskTimerAsynchronously(plugin, 1200L, 1200L); // Run every minute
    }

    /**
     * Shuts down the drops handler
     */
    public void shutdown() {
        mobDamageTracking.clear();
        processedMobs.clear();
        logger.info("[DropsHandler] has been shut down");
    }

    /**
     * Handle mob death and dropping items
     *
     * @param event The EntityDeathEvent
     */
    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        // Clear default drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Only process custom mobs
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        // Skip if already processed or has metadata indicating processed
        if (processedMobs.contains(entity.getUniqueId()) ||
                entity.hasMetadata("dropsProcessed")) {
            return;
        }

        // Mark as processed to prevent double drops
        entity.setMetadata("dropsProcessed",
                new FixedMetadataValue(plugin, true));
        processedMobs.add(entity.getUniqueId());

        // Process drops in new task to avoid concurrency issues
        new BukkitRunnable() {
            @Override
            public void run() {
                handleMobDrops(entity);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Track damage for calculating drop ownership
     *
     * @param event The EntityDamageByEntityEvent
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) ||
                event.getEntity() instanceof Player) {
            return;
        }

        Player damager = null;

        // Determine the player who caused damage
        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                damager = (Player) projectile.getShooter();
            }
        }

        if (damager != null) {
            // Track damage for this entity
            UUID entityUuid = event.getEntity().getUniqueId();
            UUID playerUuid = damager.getUniqueId();

            mobDamageTracking.computeIfAbsent(entityUuid, k -> new ConcurrentHashMap<>());
            Map<UUID, Double> damageMap = mobDamageTracking.get(entityUuid);
            damageMap.merge(playerUuid, event.getFinalDamage(), Double::sum);
        }
    }

    /**
     * Handle drops for a mob entity
     *
     * @param entity The entity that died
     */
    private void handleMobDrops(LivingEntity entity) {
        // Get tier and elite status
        int tier = getMobTier(entity);
        boolean isElite = isElite(entity);

        // Check if this entity should drop items
        if (!dropsManager.shouldDropItem(entity, isElite, tier)) {
            return;
        }

        // Handle world boss drops
        if (mobManager.hasMetadata(entity, "worldboss")) {
            handleWorldBossDrop(entity);
            return; // Exit early for world bosses
        }

        // Check if this is a NAMED elite (has a specific config)
        boolean isNamedElite = false;
        String mobType = null;

        if (isElite && entity.hasMetadata("type")) {
            mobType = entity.getMetadata("type").get(0).asString();

            // Check if this type has a specific elite config
            EliteDropConfig config = DropConfig.getEliteDropConfig(mobType.toLowerCase());

            if (config != null) {
                // It has a specific configuration in elite_drops.yml
                isNamedElite = true;
            }
        }

        // Handle drops based on elite type - ONLY ONE DROP TYPE PER MOB
        if (isNamedElite && mobType != null) {
            // If it's a named elite, ONLY handle it as a named elite
            handleNamedEliteDrop(entity, mobType, tier);
        } else if (isElite) {
            // If it's a regular elite, handle it as a regular elite
            handleEliteDrop(entity, tier);
        } else {
            // Normal mob
            handleNormalDrop(entity, tier);
        }

        // Additional drop chances - these still happen for all mob types
        dropGems(entity, tier);
        dropScroll(entity, tier);
        dropCrate(entity, tier);
    }

    /**
     * Extract the tier from a mob entity accurately combining multiple detection methods
     *
     * @param entity The entity to check
     * @return The tier level (1-6)
     */
    private int getMobTier(LivingEntity entity) {
        if (entity == null) return 1;

        // Check equipment first - most reliable direct method
        if (entity.getEquipment() != null) {
            ItemStack mainHand = entity.getEquipment().getItemInMainHand();
            if (mainHand != null && mainHand.getType() != org.bukkit.Material.AIR) {
                String material = mainHand.getType().name();
                // Direct material checking
                if (material.contains("DIAMOND")) {
                    // Check for blue display name for T6
                    if (mainHand.getItemMeta() != null &&
                            mainHand.getItemMeta().hasDisplayName() &&
                            mainHand.getItemMeta().getDisplayName().contains(ChatColor.BLUE.toString())) {
                        return 6;
                    }
                    return 4;
                } else if (material.contains("GOLDEN") || material.contains("GOLD")) return 5;
                else if (material.contains("IRON")) return 3;
                else if (material.contains("STONE")) return 2;
                else if (material.contains("WOODEN") || material.contains("WOOD")) return 1;
            }
        }

        // Try metadata next
        if (entity.hasMetadata("equipTier")) {
            return entity.getMetadata("equipTier").get(0).asInt();
        }

        if (entity.hasMetadata("dropTier")) {
            try {
                return entity.getMetadata("dropTier").get(0).asInt();
            } catch (Exception e) {
                // Fall through to next method
            }
        }

        if (entity.hasMetadata("customTier")) {
            try {
                return entity.getMetadata("customTier").get(0).asInt();
            } catch (Exception e) {
                // Fall through to next method
            }
        }

        if (entity.hasMetadata("tier")) {
            try {
                String tierStr = entity.getMetadata("tier").get(0).asString();
                return Integer.parseInt(tierStr);
            } catch (Exception e) {
                try {
                    return entity.getMetadata("tier").get(0).asInt();
                } catch (Exception ex) {
                    // Fall through to next method
                }
            }
        }

        // Default to 1 if nothing else worked
        return 1;
    }

    /**
     * Check if a mob is elite using reliable methods
     *
     * @param entity The entity to check
     * @return true if elite
     */
    private boolean isElite(LivingEntity entity) {
        if (entity == null) return false;

        // Check metadata first (most reliable)
        if (entity.hasMetadata("elite")) {
            try {
                return entity.getMetadata("elite").get(0).asBoolean();
            } catch (Exception e) {
                // Fall through to next method
            }
        }

        if (entity.hasMetadata("dropElite")) {
            try {
                return entity.getMetadata("dropElite").get(0).asBoolean();
            } catch (Exception e) {
                // Fall through to next method
            }
        }

        // Check equipment for enchantments
        if (entity.getEquipment() != null) {
            ItemStack mainHand = entity.getEquipment().getItemInMainHand();
            if (mainHand != null && !mainHand.getType().isAir() &&
                    mainHand.hasItemMeta() && mainHand.getItemMeta().hasEnchants()) {
                return true;
            }

            ItemStack helmet = entity.getEquipment().getHelmet();
            if (helmet != null && !helmet.getType().isAir() &&
                    helmet.hasItemMeta() && helmet.getItemMeta().hasEnchants()) {
                return true;
            }
        }

        // Default to false
        return false;
    }

    /**
     * Handle drops for normal (non-elite) mobs
     *
     * @param entity The mob entity
     * @param tier   The mob tier
     */
    private void handleNormalDrop(LivingEntity entity, int tier) {
        // Random item type (1-8)
        int itemType = ThreadLocalRandom.current().nextInt(8) + 1;

        // Create the item
        ItemStack item = dropsManager.createDrop(tier, itemType);

        // Drop the item with protection for the top damage dealer
        Item droppedItem = dropItemWithProtection(entity, item);

        // Apply visual effects and sounds
        if (droppedItem != null) {
            // Play sound based on tier
            Sound sound = dropsManager.getTierSound(tier);
            entity.getWorld().playSound(entity.getLocation(), sound, 1.0f, 1.0f);

            // Notify the player who caused the most damage
            Player topDamager = getTopDamageDealer(entity);
            if (topDamager != null) {
                lootNotifier.sendDropNotification(topDamager, item, entity, false);
            }
        }
    }

    /**
     * Handle drops for elite mobs
     *
     * @param entity The mob entity
     * @param tier   The mob's tier
     */
    private void handleEliteDrop(LivingEntity entity, int tier) {
        // Determine random item type with weapon bias
        int itemType;
        if (ThreadLocalRandom.current().nextInt(100) < 40) {
            // 40% chance for weapon drop (types 1-4)
            itemType = ThreadLocalRandom.current().nextInt(4) + 1;
        } else {
            // 60% chance for armor drop (types 5-8)
            itemType = ThreadLocalRandom.current().nextInt(4) + 5;
        }

        // Elite mobs have better rarity chances
        int rarity;
        int roll = ThreadLocalRandom.current().nextInt(100);

        if (roll < 5) {
            rarity = 4; // Unique - 5%
        } else if (roll < 20) {
            rarity = 3; // Rare - 15%
        } else if (roll < 50) {
            rarity = 2; // Uncommon - 30%
        } else {
            rarity = 1; // Common - 50%
        }

        // Create the elite drop
        ItemStack item = dropsManager.createDrop(tier, itemType, rarity);

        // Drop the item with protection
        Item droppedItem = dropItemWithProtection(entity, item);

        // Apply visual effects and sounds
        if (droppedItem != null) {
            // Play sound based on rarity
            Sound sound = dropsManager.getRaritySound(rarity);
            entity.getWorld().playSound(entity.getLocation(), sound, 1.0f, 1.0f);


            // Notify the player who caused the most damage
            Player topDamager = getTopDamageDealer(entity);
            if (topDamager != null) {
                lootNotifier.sendDropNotification(topDamager, item, entity, false);
            }
        }
    }

    /**
     * Handle drops for named elite mobs
     *
     * @param entity     The mob entity
     * @param mobType    The mob type
     * @param actualTier The actual tier of the mob (for fallback)
     */
    private void handleNamedEliteDrop(LivingEntity entity, String mobType, int actualTier) {
        if (mobType == null || mobType.isEmpty()) {
            // Fallback to standard elite drop if no type found
            handleEliteDrop(entity, actualTier);
            return;
        }

        // Determine random item type
        int itemType = ThreadLocalRandom.current().nextInt(8) + 1;

        // Create custom elite drop - PASS THE ACTUAL TIER FOR FALLBACK
        // This is crucial for special elites without configs
        ItemStack item = dropsManager.createEliteDrop(mobType, itemType, actualTier);

        // Drop the item with protection
        Item droppedItem = dropItemWithProtection(entity, item);

        // Apply visual effects and sounds
        if (droppedItem != null) {
            // Play special sound
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_DEATH, 1.0f, 1.0f);

            // Notify nearby players
            for (Entity nearby : entity.getNearbyEntities(30, 30, 30)) {
                if (nearby instanceof Player) {
                    Player player = (Player) nearby;
                    lootNotifier.sendDropNotification(player, item, entity, false);
                }
            }
        }
    }

    /**
     * Handle drops for world bosses
     *
     * @param entity The world boss entity
     */
    private void handleWorldBossDrop(LivingEntity entity) {
        // World bosses drop multiple items
        int dropCount = ThreadLocalRandom.current().nextInt(3) + 3; // 3-5 drops

        // Get damage contributors sorted by damage
        List<Map.Entry<UUID, Double>> sortedDamagers = getSortedDamageContributors(entity);

        // Get boss tier
        int tier = getMobTier(entity);

        // Drop location parameters
        double radius = 2.0;
        Location center = entity.getLocation();

        // Top damagers list for announcement
        List<Object[]> topDamagersList = new ArrayList<>();

        // Spawn items in a circle
        for (int i = 0; i < dropCount; i++) {
            // Calculate position
            double angle = 2 * Math.PI * i / dropCount;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location dropLoc = new Location(center.getWorld(), x, center.getY(), z);

            // Create high-quality item
            int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
            int rarity = ThreadLocalRandom.current().nextInt(2) + 3; // Rare or Unique

            ItemStack item = dropsManager.createDrop(tier, itemType, rarity);

            // Drop the item
            Item droppedItem = entity.getWorld().dropItem(dropLoc, item);

            // Apply protection for top damagers if available
            if (!sortedDamagers.isEmpty()) {
                Map.Entry<UUID, Double> damager = sortedDamagers.get(0);
                Player player = Bukkit.getPlayer(damager.getKey());

                if (player != null && player.isOnline()) {
                    // Add protection for this player
                    dropsManager.registerDropProtection(droppedItem, player.getUniqueId(), 30); // 30 second protection

                    // Notify player
                    lootNotifier.sendDropNotification(player, item, entity, true);

                    // Add to top damagers list if not already there
                    boolean found = false;
                    for (Object[] entry : topDamagersList) {
                        if (entry[0].equals(player.getName())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        topDamagersList.add(new Object[]{player.getName(), (int) Math.round(damager.getValue())});
                    }
                }

                // Move to next damager for next item
                if (sortedDamagers.size() > 1) {
                    sortedDamagers.remove(0);
                }
            }

        }

        // Announce boss defeat
        String bossName = entity.getCustomName() != null ?
                ChatColor.stripColor(entity.getCustomName()) : "World Boss";
        lootNotifier.announceWorldBossDefeat(bossName, topDamagersList);

        // Sound effects
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.5f);
    }

    /**
     * Drop gems based on tier
     *
     * @param entity The mob entity
     * @param tier   The mob's tier
     */
    private void dropGems(LivingEntity entity, int tier) {
        // 50% chance to drop gems
        if (ThreadLocalRandom.current().nextBoolean()) {
            // Create gem item
            ItemStack gemsItem = dropFactory.createGemDrop(tier);

            // Drop the gems
            dropItemWithProtection(entity, gemsItem);
        }
    }

    /**
     * Drop scroll based on tier with low chance
     *
     * @param entity The mob entity
     * @param tier   The mob's tier
     */
    private void dropScroll(LivingEntity entity, int tier) {
        // 5% chance to drop scroll
        if (ThreadLocalRandom.current().nextInt(100) < 5) {
            try {
                // Create scroll item
                ItemStack scroll = dropFactory.createScrollDrop(tier);

                if (scroll != null) {
                    // Drop the scroll
                    Item droppedItem = dropItemWithProtection(entity, scroll);

                    // Notify the player who caused the most damage
                    if (droppedItem != null) {
                        Player topDamager = getTopDamageDealer(entity);
                        if (topDamager != null) {
                            lootNotifier.sendDropNotification(topDamager, scroll, entity, false);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error creating teleport scroll drop: " + e.getMessage());
            }
        }
    }

    /**
     * Drop crate based on tier with very low chance
     *
     * @param entity The mob entity
     * @param tier   The mob's tier
     */
    private void dropCrate(LivingEntity entity, int tier) {
        int crateRate = DropConfig.getCrateDropRate(tier);

        // Apply buff if active
        if (lootBuffManager.isBuffActive()) {
            int buffPercentage = lootBuffManager.getActiveBuff().getBuffRate();
            crateRate += (crateRate * buffPercentage / 100);
        }

        // Roll for crate drop
        if (ThreadLocalRandom.current().nextInt(100) < crateRate) {
            // Create crate item
            ItemStack crate = dropFactory.createCrateDrop(tier);

            // Drop the crate
            Item droppedItem = dropItemWithProtection(entity, crate);

            // Notify the player who caused the most damage
            if (droppedItem != null) {
                Player topDamager = getTopDamageDealer(entity);
                if (topDamager != null) {
                    lootNotifier.sendDropNotification(topDamager, crate, entity, false);
                }
            }

            // Count as improved drop if buff is active
            if (lootBuffManager.isBuffActive()) {
                lootBuffManager.updateImprovedDrops();
            }
        }
    }

    /**
     * Drops an item with protection for the top damage dealer
     *
     * @param entity The source entity
     * @param item   The item to drop
     * @return The dropped item entity
     */
    private Item dropItemWithProtection(LivingEntity entity, ItemStack item) {
        // Find player with highest damage
        Player topDamager = getTopDamageDealer(entity);

        // Drop the item
        Item droppedItem = entity.getWorld().dropItemNaturally(entity.getLocation(), item);

        // Apply protection if we have a top damager
        if (topDamager != null) {
            dropsManager.registerDropProtection(droppedItem, topDamager.getUniqueId(), 5); // 5 second protection
        }

        return droppedItem;
    }

    /**
     * Get the player who dealt the most damage to an entity
     *
     * @param entity The entity
     * @return The top damage dealer or null if none
     */
    private Player getTopDamageDealer(LivingEntity entity) {
        Map<UUID, Double> damageMap = mobDamageTracking.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) {
            return null;
        }

        // Find player with highest damage
        UUID topDamagerUuid = damageMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (topDamagerUuid != null) {
            return Bukkit.getPlayer(topDamagerUuid);
        }

        return null;
    }

    /**
     * Get sorted list of damagers by damage amount
     *
     * @param entity The entity
     * @return Sorted list of damagers
     */
    private List<Map.Entry<UUID, Double>> getSortedDamageContributors(LivingEntity entity) {
        Map<UUID, Double> damageMap = mobDamageTracking.get(entity.getUniqueId());
        if (damageMap == null || damageMap.isEmpty()) {
            return new ArrayList<>();
        }

        // Sort by damage (descending)
        return damageMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Sets the drop rate for a specific tier
     *
     * @param tier The tier (1-6)
     * @param rate The new drop rate
     */
    public void setDropRate(int tier, int rate) {
        dropsManager.setDropRate(tier, rate);
        TextUtil.broadcastCentered(ChatColor.YELLOW + "DROP RATES" + ChatColor.GRAY + " - " +
                ChatColor.AQUA + "Tier " + tier + " drop rates have been changed to " + rate + "%");
    }

    /**
     * Gets the drop rate for a specific tier
     *
     * @param tier The tier (1-6)
     * @return The drop rate
     */
    public int getDropRate(int tier) {
        return dropsManager.getDropRate(tier);
    }
}
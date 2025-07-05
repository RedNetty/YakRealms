package com.rednetty.server.mechanics.mounts;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Enhanced handler for elytra mounts with improved damage tracking, landing detection, and chestplate handling
 */
public class ElytraMount implements Mount {
    private final MountManager manager;
    private final Map<UUID, BukkitTask> summonTasks = new HashMap<>();
    private final Map<UUID, Location> summonLocations = new HashMap<>();
    private final Map<UUID, BukkitTask> durationTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> landingDetectionTasks = new HashMap<>();
    private final Map<UUID, ChestplateState> originalChestplates = new HashMap<>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private final Map<UUID, Long> launchTimes = new HashMap<>();

    // Configuration constants - Updated with better thresholds
    private static final long DAMAGE_COOLDOWN_MS = 120000; // 2 minutes
    private static final int LANDING_CHECK_INTERVAL = 5; // ticks
    private static final double LANDING_VELOCITY_THRESHOLD = 0.3; // Increased from 0.1
    private static final double LANDING_SPEED_THRESHOLD = 0.5; // Increased from 0.2
    private static final int GROUND_CHECK_DISTANCE = 2; // Reduced from 3
    private static final int LAUNCH_GRACE_PERIOD = 60; // 3 seconds grace period after launch

    /**
     * Inner class to store complete chestplate state
     */
    private static class ChestplateState {
        private final ItemStack originalItem;
        private final Map<Enchantment, Integer> enchantments;
        private final String displayName;
        private final List<String> lore;
        private final boolean unbreakable;
        private final int durability;
        private final Set<ItemFlag> itemFlags;

        public ChestplateState(ItemStack chestplate) {
            if (chestplate != null && chestplate.getType() != Material.AIR) {
                this.originalItem = chestplate.clone();
                this.enchantments = new HashMap<>(chestplate.getEnchantments());
                this.durability = chestplate.getDurability();

                ItemMeta meta = chestplate.getItemMeta();
                if (meta != null) {
                    this.displayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                    this.lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : null;
                    this.unbreakable = meta.isUnbreakable();
                    this.itemFlags = new HashSet<>(meta.getItemFlags());
                } else {
                    this.displayName = null;
                    this.lore = null;
                    this.unbreakable = false;
                    this.itemFlags = new HashSet<>();
                }
            } else {
                this.originalItem = null;
                this.enchantments = new HashMap<>();
                this.displayName = null;
                this.lore = null;
                this.unbreakable = false;
                this.durability = 0;
                this.itemFlags = new HashSet<>();
            }
        }

        public ItemStack getOriginalItem() {
            return originalItem != null ? originalItem.clone() : null;
        }

        public Map<Enchantment, Integer> getEnchantments() {
            return new HashMap<>(enchantments);
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getLore() {
            return lore != null ? new ArrayList<>(lore) : null;
        }

        public boolean isUnbreakable() {
            return unbreakable;
        }

        public int getDurability() {
            return durability;
        }

        public Set<ItemFlag> getItemFlags() {
            return new HashSet<>(itemFlags);
        }

        public boolean hasOriginalItem() {
            return originalItem != null && originalItem.getType() != Material.AIR;
        }
    }

    /**
     * Constructs a new ElytraMount
     *
     * @param manager The mount manager
     */
    public ElytraMount(MountManager manager) {
        this.manager = manager;
    }

    @Override
    public MountType getType() {
        return MountType.ELYTRA;
    }

    @Override
    public boolean summonMount(Player player) {
        // Check permission
        if (!player.hasPermission("yakrp.mount.elytra")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use an elytra mount.");
            return false;
        }

        // Check if already summoning
        if (isSummoning(player)) {
            player.sendMessage(ChatColor.RED + "You are already summoning a mount.");
            return false;
        }

        // Check damage cooldown
        if (isOnDamageCooldown(player)) {
            long timeLeft = getRemainingCooldownTime(player);
            player.sendMessage(ChatColor.RED + "You cannot summon an elytra mount for " +
                    (timeLeft / 1000) + " more seconds after taking damage.");
            return false;
        }

        // Check height restrictions
        if (!checkElytraLaunchRequirements(player)) {
            return false;
        }

        // Start summoning process
        int summonTime = manager.getConfig().getElytraSummonTime();

        summonLocations.put(player.getUniqueId(), player.getLocation());

        player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() +
                "SUMMONING ELYTRA MOUNT.... " + summonTime + "s");

        BukkitTask task = new BukkitRunnable() {
            int countdown = summonTime;

            @Override
            public void run() {
                // Check if player is still online
                if (!player.isOnline()) {
                    cancel();
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    return;
                }

                // Check if player is still on cooldown (in case they took damage during summoning)
                if (isOnDamageCooldown(player)) {
                    cancel();
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    long timeLeft = getRemainingCooldownTime(player);
                    player.sendMessage(ChatColor.RED + "ELYTRA MOUNT SUMMONING CANCELLED - Damage cooldown: " +
                            (timeLeft / 1000) + "s remaining");
                    return;
                }

                // Decrement countdown
                countdown--;

                if (countdown <= 0) {
                    // Launch the player and equip elytra
                    activateElytra(player);

                    // Clean up
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    cancel();
                } else {
                    // Update message
                    player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() +
                            "SUMMONING ELYTRA MOUNT.... " + countdown + "s");
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 20L);

        summonTasks.put(player.getUniqueId(), task);
        return true;
    }

    @Override
    public boolean dismount(Player player, boolean sendMessage) {
        UUID playerUUID = player.getUniqueId();

        // Cancel all active tasks
        BukkitTask durationTask = durationTasks.remove(playerUUID);
        if (durationTask != null) {
            durationTask.cancel();
        }

        BukkitTask landingTask = landingDetectionTasks.remove(playerUUID);
        if (landingTask != null) {
            landingTask.cancel();
        }

        // Clean up launch time tracking
        launchTimes.remove(playerUUID);

        // Restore original chestplate with enhanced handling
        restoreOriginalChestplate(player);

        // Stop gliding and reset fall distance
        if (player.isGliding()) {
            player.setGliding(false);
            player.setFallDistance(0);

            // Gentle landing - give player a small upward velocity to prevent fall damage
            if (!isPlayerOnGround(player)) {
                player.setVelocity(new Vector(0, -0.1, 0));
            }
        }

        // Unregister from mount manager
        manager.unregisterActiveMount(playerUUID);

        if (sendMessage) {
            player.sendMessage(ChatColor.RED + "Your elytra wings have worn out and can no longer sustain flight.");
        }

        return true;
    }

    @Override
    public boolean isSummoning(Player player) {
        return summonTasks.containsKey(player.getUniqueId());
    }

    @Override
    public void cancelSummoning(Player player, String reason) {
        UUID playerUUID = player.getUniqueId();

        BukkitTask task = summonTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        summonLocations.remove(playerUUID);

        player.sendMessage(ChatColor.RED + "ELYTRA MOUNT SUMMONING CANCELLED" +
                (reason != null ? " - " + reason : ""));
    }

    /**
     * Records when a player takes damage for cooldown tracking
     *
     * @param player The player who took damage
     */
    public void recordPlayerDamage(Player player) {
        lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Checks if a player is on damage cooldown
     *
     * @param player The player
     * @return True if the player is on cooldown
     */
    public boolean isOnDamageCooldown(Player player) {
        Long lastDamage = lastDamageTime.get(player.getUniqueId());
        if (lastDamage == null) {
            return false;
        }

        return (System.currentTimeMillis() - lastDamage) < DAMAGE_COOLDOWN_MS;
    }

    /**
     * Gets the remaining cooldown time in milliseconds
     *
     * @param player The player
     * @return Remaining cooldown time in milliseconds
     */
    public long getRemainingCooldownTime(Player player) {
        Long lastDamage = lastDamageTime.get(player.getUniqueId());
        if (lastDamage == null) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - lastDamage;
        return Math.max(0, DAMAGE_COOLDOWN_MS - elapsed);
    }

    /**
     * Clears the damage cooldown for a player
     *
     * @param player The player
     */
    public void clearDamageCooldown(Player player) {
        lastDamageTime.remove(player.getUniqueId());
    }

    /**
     * Checks if the player has moved from their summoning location
     *
     * @param player The player
     * @return True if the player has moved
     */
    public boolean hasMoved(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!summonLocations.containsKey(playerUUID)) {
            return false;
        }

        Location summonLocation = summonLocations.get(playerUUID);
        Location currentLocation = player.getLocation();

        return summonLocation.distance(currentLocation) > 2.0;
    }

    /**
     * Checks if a player is currently using an elytra mount
     *
     * @param player The player
     * @return True if the player is using an elytra mount
     */
    public boolean isUsingElytra(Player player) {
        return durationTasks.containsKey(player.getUniqueId());
    }

    /**
     * Checks if a player is above the height limit for their region
     *
     * @param player The player
     * @return True if the player is above the height limit
     */
    public boolean isAboveHeightLimit(Player player) {
        return player.getLocation().getY() >= 185;
    }

    /**
     * Enhanced landing detection with grace period and better thresholds
     *
     * @param player The player
     * @return True if the player has landed
     */
    public boolean hasPlayerLanded(Player player) {
        if (!player.isGliding()) {
            return true;
        }

        // Grace period - don't detect landing for first 3 seconds after launch
        Long launchTime = launchTimes.get(player.getUniqueId());
        if (launchTime != null) {
            long timeSinceLaunch = (System.currentTimeMillis() - launchTime) / 50; // Convert to ticks
            if (timeSinceLaunch < LAUNCH_GRACE_PERIOD) {
                return false; // Still in grace period
            }
        }

        // Check if player is actually on ground (more strict)
        if (isPlayerOnGroundStrict(player)) {
            return true;
        }

        // Check velocity threshold (more lenient)
        Vector velocity = player.getVelocity();
        if (Math.abs(velocity.getY()) < LANDING_VELOCITY_THRESHOLD &&
                velocity.length() < LANDING_SPEED_THRESHOLD) {

            // Additional check: make sure we're close to ground when velocity is low
            return isPlayerNearGround(player, 1);
        }

        return false;
    }

    /**
     * Stricter ground detection
     *
     * @param player The player
     * @return True if player is on ground
     */
    private boolean isPlayerOnGroundStrict(Player player) {
        // Only trust Bukkit's isOnGround if player is actually touching solid ground
        if (player.isOnGround()) {
            Location loc = player.getLocation();
            Block blockBelow = loc.clone().subtract(0, 0.1, 0).getBlock();
            return blockBelow.getType().isSolid();
        }
        return false;
    }

    /**
     * Checks if player is near ground within specified distance
     *
     * @param player The player
     * @param maxDistance Maximum distance to check
     * @return True if player is near ground
     */
    private boolean isPlayerNearGround(Player player, int maxDistance) {
        Location loc = player.getLocation();

        for (int i = 0; i <= maxDistance; i++) {
            Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (block.getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if player is on or very close to the ground
     *
     * @param player The player
     * @return True if player is on ground
     */
    private boolean isPlayerOnGround(Player player) {
        Location loc = player.getLocation();

        // Check if player is on ground according to Bukkit AND verify with block check
        if (player.isOnGround()) {
            Block blockBelow = loc.clone().subtract(0, 0.1, 0).getBlock();
            if (blockBelow.getType().isSolid()) {
                return true;
            }
        }

        // Additional checks for near-ground detection (more conservative)
        for (int i = 0; i <= GROUND_CHECK_DISTANCE; i++) {
            Block block = loc.clone().subtract(0, i, 0).getBlock();
            if (block.getType().isSolid()) {
                return i == 0; // Only true if directly on solid ground
            }
        }

        return false;
    }

    /**
     * Checks if a player can launch an elytra
     *
     * @param player The player
     * @return True if the player can launch
     */
    private boolean checkElytraLaunchRequirements(Player player) {
        Location loc = player.getLocation();
        double heightLimit = 195;

        // Check if there's enough space above
        Location aboveLocation = loc.clone().add(0, 15, 0);
        if (aboveLocation.getBlock().getType() != Material.AIR || aboveLocation.getY() >= heightLimit) {
            player.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() +
                    "Unable to launch elytra mount - insufficient space above or too high to launch.");
            return false;
        }

        // Check if player is in water or lava
        if (player.isInWater() || loc.getBlock().getType() == Material.LAVA) {
            player.sendMessage(ChatColor.RED + "Cannot launch elytra mount while in water or lava.");
            return false;
        }

        return true;
    }

    /**
     * Activates the elytra mount for a player with enhanced chestplate handling
     *
     * @param player The player
     */
    private void activateElytra(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Launch the player upward
        player.setVelocity(new Vector(0, 2, 0));

        // Record launch time for grace period
        launchTimes.put(playerUUID, System.currentTimeMillis());

        // Schedule equipping the elytra after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    launchTimes.remove(playerUUID); // Clean up
                    return;
                }

                player.sendMessage(ChatColor.WHITE + ChatColor.BOLD.toString() + "ELYTRA MOUNT ACTIVATED!");

                // Enhanced chestplate handling
                equipElytraWithChestplateProperties(player);

                // Start gliding
                player.setGliding(true);

                // Register mount
                manager.registerActiveMount(playerUUID, ElytraMount.this);

                // Play sound
                player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.0f, 1.0f);

                // Start landing detection with delay to allow proper launch
                startLandingDetectionDelayed(player);

                // Schedule elytra duration
                int duration = manager.getConfig().getElytraDuration();

                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            dismount(player, true);
                        }
                    }
                }.runTaskLater(manager.getPlugin(), duration * 20L);

                durationTasks.put(playerUUID, task);
            }
        }.runTaskLater(manager.getPlugin(), 10L);
    }

    /**
     * Enhanced chestplate handling - stores complete state and transfers properties to elytra
     *
     * @param player The player
     */
    private void equipElytraWithChestplateProperties(Player player) {
        UUID playerUUID = player.getUniqueId();
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        ItemStack currentChestplate = armorContents[2];

        // Store the complete chestplate state
        ChestplateState chestplateState = new ChestplateState(currentChestplate);
        originalChestplates.put(playerUUID, chestplateState);

        // Create enhanced elytra with transferred properties
        ItemStack elytra = createEnhancedElytra(chestplateState);

        // Equip the enhanced elytra
        armorContents[2] = elytra;
        player.getInventory().setArmorContents(armorContents);
    }

    /**
     * Creates an elytra with properties transferred from the original chestplate
     *
     * @param chestplateState The original chestplate state
     * @return Enhanced elytra with transferred properties
     */
    private ItemStack createEnhancedElytra(ChestplateState chestplateState) {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta elytraMeta = elytra.getItemMeta();

        if (elytraMeta != null) {
            // Transfer enchantments from original chestplate
            for (Map.Entry<Enchantment, Integer> enchant : chestplateState.getEnchantments().entrySet()) {
                if (enchant.getKey().canEnchantItem(elytra)) {
                    elytraMeta.addEnchant(enchant.getKey(), enchant.getValue(), true);
                }
            }

            // Set durability to match original (if it had durability)
            if (chestplateState.hasOriginalItem()) {
                elytra.setDurability((short) Math.min(chestplateState.getDurability(), elytra.getType().getMaxDurability()));
            }

            // Set unbreakable if original was unbreakable
            elytraMeta.setUnbreakable(chestplateState.isUnbreakable());

            // Transfer item flags
            for (ItemFlag flag : chestplateState.getItemFlags()) {
                elytraMeta.addItemFlags(flag);
            }

            // Add default flags for elytra
            elytraMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

            // Set display name to indicate it's enhanced
            String originalName = chestplateState.getDisplayName();
            if (originalName != null) {
                elytraMeta.setDisplayName(ChatColor.AQUA + "Enhanced Elytra " + ChatColor.GRAY + "(" + originalName + ")");
            } else {
                elytraMeta.setDisplayName(ChatColor.AQUA + "Enhanced Elytra Mount");
            }

            // Create enhanced lore
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Magical wings enhanced with your");
            lore.add(ChatColor.GRAY + "chestplate's protective properties.");
            lore.add("");

            if (chestplateState.hasOriginalItem()) {
                lore.add(ChatColor.YELLOW + "Inherited Properties:");
                if (!chestplateState.getEnchantments().isEmpty()) {
                    lore.add(ChatColor.GRAY + "• Enchantments: " + chestplateState.getEnchantments().size());
                }
                if (chestplateState.isUnbreakable()) {
                    lore.add(ChatColor.GRAY + "• Unbreakable");
                }
                lore.add("");
            }

            lore.add(ChatColor.YELLOW + "Duration: " + manager.getConfig().getElytraDuration() + " seconds");
            lore.add(ChatColor.GRAY + "Active Mount - Do Not Remove");

            // Add original lore if it existed
            List<String> originalLore = chestplateState.getLore();
            if (originalLore != null && !originalLore.isEmpty()) {
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "Original Properties:");
                for (String loreLine : originalLore) {
                    lore.add(ChatColor.DARK_GRAY + loreLine);
                }
            }

            elytraMeta.setLore(lore);

            // Mark as enhanced elytra mount
            NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "enhanced_elytra_mount");
            elytraMeta.getPersistentDataContainer().set(mountKey, PersistentDataType.BYTE, (byte) 1);

            elytra.setItemMeta(elytraMeta);
        }

        return elytra;
    }

    /**
     * Restores the original chestplate with all properties
     *
     * @param player The player
     */
    private void restoreOriginalChestplate(Player player) {
        UUID playerUUID = player.getUniqueId();
        ChestplateState chestplateState = originalChestplates.remove(playerUUID);

        if (chestplateState == null) {
            return;
        }

        ItemStack[] armorContents = player.getInventory().getArmorContents();

        // Only replace if current chestplate is our enhanced elytra
        if (armorContents[2] != null && isEnhancedElytraMount(armorContents[2])) {
            armorContents[2] = chestplateState.getOriginalItem();
            player.getInventory().setArmorContents(armorContents);

            if (chestplateState.hasOriginalItem()) {
                player.sendMessage(ChatColor.GREEN + "Your original chestplate has been restored.");
            }
        }
    }

    /**
     * Starts landing detection with additional delay
     *
     * @param player The player
     */
    private void startLandingDetectionDelayed(Player player) {
        // Add extra delay before starting landing detection
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && isUsingElytra(player)) {
                    startLandingDetection(player);
                }
            }
        }.runTaskLater(manager.getPlugin(), 40L); // 2 second delay
    }

    /**
     * Starts landing detection for a player
     *
     * @param player The player
     */
    private void startLandingDetection(Player player) {
        UUID playerUUID = player.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isUsingElytra(player)) {
                    cancel();
                    landingDetectionTasks.remove(playerUUID);
                    return;
                }

                // Check for landing
                if (hasPlayerLanded(player)) {
                    cancel();
                    landingDetectionTasks.remove(playerUUID);

                    // Dismount with a gentle message
                    dismount(player, false);
                    player.sendMessage(ChatColor.YELLOW + "You have landed safely. Your elytra mount has been deactivated.");
                }
            }
        }.runTaskTimer(manager.getPlugin(), LANDING_CHECK_INTERVAL, LANDING_CHECK_INTERVAL);

        landingDetectionTasks.put(playerUUID, task);
    }

    /**
     * Creates an elytra mount item
     *
     * @return The elytra mount item
     */
    public ItemStack createElytraItem() {
        ItemStack itemStack = new ItemStack(Material.ELYTRA);
        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.setDisplayName(ChatColor.AQUA + "Elytra Mount");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Magical wings that allow you to");
        lore.add(ChatColor.GRAY + "soar through the skies gracefully.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Features:");
        lore.add(ChatColor.GRAY + "• Inherits chestplate properties");
        lore.add(ChatColor.GRAY + "• Advanced landing detection");
        lore.add(ChatColor.GRAY + "• Enhanced durability");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Duration: " + manager.getConfig().getElytraDuration() + " seconds");
        lore.add(ChatColor.YELLOW + "Cooldown: 2 minutes after damage");
        lore.add("");
        lore.add(ChatColor.GRAY + "Right-click to activate");
        lore.add(ChatColor.GRAY + "Permanent Untradeable");

        itemMeta.setLore(lore);
        itemMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        itemMeta.setUnbreakable(true);

        // Mark as elytra mount
        NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "elytra_mount");
        itemMeta.getPersistentDataContainer().set(mountKey, PersistentDataType.BYTE, (byte) 1);

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    /**
     * Checks if an item is an elytra mount
     *
     * @param item The item
     * @return True if the item is an elytra mount
     */
    public boolean isElytraMount(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "elytra_mount");
        if (meta.getPersistentDataContainer().has(mountKey, PersistentDataType.BYTE)) {
            return true;
        }

        // Fallback to display name check for legacy items
        return meta.hasDisplayName() && meta.getDisplayName().equals(ChatColor.AQUA + "Elytra Mount");
    }

    /**
     * Checks if an item is an enhanced elytra mount (currently equipped)
     *
     * @param item The item
     * @return True if the item is an enhanced elytra mount
     */
    public boolean isEnhancedElytraMount(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "enhanced_elytra_mount");
        return meta.getPersistentDataContainer().has(mountKey, PersistentDataType.BYTE);
    }

    /**
     * Cleanup method to be called when player leaves or plugin disables
     *
     * @param player The player
     */
    public void cleanup(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Cancel all tasks
        BukkitTask summonTask = summonTasks.remove(playerUUID);
        if (summonTask != null) {
            summonTask.cancel();
        }

        BukkitTask durationTask = durationTasks.remove(playerUUID);
        if (durationTask != null) {
            durationTask.cancel();
        }

        BukkitTask landingTask = landingDetectionTasks.remove(playerUUID);
        if (landingTask != null) {
            landingTask.cancel();
        }

        // Clean up stored data
        summonLocations.remove(playerUUID);
        originalChestplates.remove(playerUUID);
        launchTimes.remove(playerUUID); // Clean up launch times

        // Restore chestplate if needed
        if (player.isOnline()) {
            restoreOriginalChestplate(player);
        }
    }
}
package com.rednetty.server.mechanics.player.mounts.type;

import com.rednetty.server.mechanics.player.mounts.MountManager;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * handler for elytra mounts with robust cleanup mechanisms and improved state tracking
 *  issues with stuck elytra items and inconsistent cleanup
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
    private final Map<UUID, Long> glidingStartTimes = new HashMap<>();

    // Improved configuration constants
    private static final long DAMAGE_COOLDOWN_MS = 60000; // 1 minute
    private static final int LANDING_CHECK_INTERVAL = 10; // ticks
    private static final double LANDING_VELOCITY_THRESHOLD = 0.1; // Y velocity threshold
    private static final double LANDING_SPEED_THRESHOLD = 0.3; // Total speed threshold
    private static final int GROUND_CHECK_DISTANCE = 1; // Distance to check below player
    private static final int LAUNCH_GRACE_PERIOD = 100; // 5 seconds grace period after launch
    private static final int GLIDING_TIME_BEFORE_HEIGHT_CHECK = 100; // 5 seconds before checking height limits
    private static final double LAUNCH_VELOCITY = 1.8; // Launch velocity

    /**
     * Inner class to store complete chestplate state
     */
    private static class ChestplateState {
        private final ItemStack originalItem;
        private final Map<Enchantment, Integer> enchantments;
        private final Component displayName;
        private final List<Component> lore;
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
                    this.displayName = meta.displayName();
                    this.lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : null;
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

        public Component getDisplayName() {
            return displayName;
        }

        public List<Component> getLore() {
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
        // Check if already summoning
        if (isSummoning(player)) {
            player.sendMessage(Component.text("You are already summoning a mount.", NamedTextColor.RED));
            return false;
        }

        // Check damage cooldown
        if (isOnDamageCooldown(player)) {
            long timeLeft = getRemainingCooldownTime(player);
            player.sendMessage(Component.text("You cannot summon an elytra mount for " +
                    (timeLeft / 1000) + " more seconds after taking damage.", NamedTextColor.RED));
            return false;
        }

        // Check height restrictions
        if (!checkElytraLaunchRequirements(player)) {
            return false;
        }

        // Start summoning process
        int summonTime = manager.getConfig().getElytraSummonTime();

        summonLocations.put(player.getUniqueId(), player.getLocation());

        player.sendMessage(Component.text("SUMMONING ELYTRA MOUNT.... " + summonTime + "s",
                NamedTextColor.WHITE, TextDecoration.BOLD));

        BukkitTask task = new BukkitRunnable() {
            int countdown = summonTime;

            @Override
            public void run() {
                // Check if player is still online
                if (!player.isOnline()) {
                    cancel();
                    cleanupPlayerData(player.getUniqueId());
                    return;
                }

                // Check if player is still on cooldown (in case they took damage during summoning)
                if (isOnDamageCooldown(player)) {
                    cancel();
                    cleanupPlayerData(player.getUniqueId());
                    long timeLeft = getRemainingCooldownTime(player);
                    player.sendMessage(Component.text("ELYTRA MOUNT SUMMONING CANCELLED - Damage cooldown: " +
                            (timeLeft / 1000) + "s remaining", NamedTextColor.RED));
                    return;
                }

                // Decrement countdown
                countdown--;

                if (countdown <= 0) {
                    // Launch the player and equip elytra
                    activateElytra(player);

                    // Clean up summoning data
                    summonTasks.remove(player.getUniqueId());
                    summonLocations.remove(player.getUniqueId());
                    cancel();
                } else {
                    // Update message
                    player.sendMessage(Component.text("SUMMONING ELYTRA MOUNT.... " + countdown + "s",
                            NamedTextColor.WHITE, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(manager.getPlugin(), 20L, 20L);

        summonTasks.put(player.getUniqueId(), task);
        return true;
    }

    @Override
    public boolean dismount(Player player, boolean sendMessage) {
        UUID playerUUID = player.getUniqueId();

        // ENHANCED: Always perform complete cleanup regardless of current state
        boolean wasUsingElytra = isUsingElytra(player);

        // Force stop gliding first
        if (player.isGliding()) {
            player.setGliding(false);
            player.setFallDistance(0);
        }

        // Complete cleanup of all tracking data
        forceCleanupAllData(playerUUID);

        // ENHANCED: Restore original chestplate with force mechanism
        forceRestoreChestplate(player);

        // Ensure safe landing
        if (!isPlayerSafeOnGround(player)) {
            // Give player a gentle downward velocity to land safely
            player.setVelocity(new Vector(0, -0.3, 0));

            // Schedule a safety check to prevent fall damage
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.setFallDistance(0);
                    }
                }
            }.runTaskLater(manager.getPlugin(), 5L);
        }

        // Unregister from mount manager
        manager.unregisterActiveMount(playerUUID);

        if (sendMessage && wasUsingElytra) {
            player.sendMessage(Component.text("Your elytra wings have worn out and can no longer sustain flight.",
                    NamedTextColor.RED));
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

        Component message = Component.text("ELYTRA MOUNT SUMMONING CANCELLED", NamedTextColor.RED);
        if (reason != null) {
            message = message.append(Component.text(" - " + reason, NamedTextColor.RED));
        }
        player.sendMessage(message);
    }

    /**
     *: Complete cleanup of all player data
     */
    private void cleanupPlayerData(UUID playerUUID) {
        // Remove from summoning
        BukkitTask summonTask = summonTasks.remove(playerUUID);
        if (summonTask != null) {
            summonTask.cancel();
        }
        summonLocations.remove(playerUUID);
    }

    /**
     *: Force cleanup of all tracking data
     */
    private void forceCleanupAllData(UUID playerUUID) {
        // Cancel all active tasks
        BukkitTask durationTask = durationTasks.remove(playerUUID);
        if (durationTask != null) {
            durationTask.cancel();
        }

        BukkitTask landingTask = landingDetectionTasks.remove(playerUUID);
        if (landingTask != null) {
            landingTask.cancel();
        }

        BukkitTask summonTask = summonTasks.remove(playerUUID);
        if (summonTask != null) {
            summonTask.cancel();
        }

        // Clean up tracking data
        summonLocations.remove(playerUUID);
        launchTimes.remove(playerUUID);
        glidingStartTimes.remove(playerUUID);
    }

    /**
     *: Force restore chestplate with fallback mechanisms
     */
    private void forceRestoreChestplate(Player player) {
        UUID playerUUID = player.getUniqueId();
        ChestplateState chestplateState = originalChestplates.remove(playerUUID);

        ItemStack[] armorContents = player.getInventory().getArmorContents();
        ItemStack currentChestplate = armorContents[2];

        // ENHANCED: Always attempt to restore if we have stored state
        if (chestplateState != null) {
            // Restore the original chestplate regardless of current state
            armorContents[2] = chestplateState.getOriginalItem();
            player.getInventory().setArmorContents(armorContents);

            if (chestplateState.hasOriginalItem()) {
                player.sendMessage(Component.text("Your original chestplate has been restored.",
                        NamedTextColor.GREEN));
            }
        } else if (currentChestplate != null && isAnyElytraMount(currentChestplate)) {
            // ENHANCED: Failsafe - if no stored state but current is elytra mount, remove it
            armorContents[2] = null;
            player.getInventory().setArmorContents(armorContents);
            player.sendMessage(Component.text("Stuck elytra mount has been removed.",
                    NamedTextColor.YELLOW));
        }
    }

    /**
     *: Check if item is any type of elytra mount (including corrupted ones)
     */
    private boolean isAnyElytraMount(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        // Check for our persistent data key
        NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "_elytra_mount");
        if (meta.getPersistentDataContainer().has(mountKey, PersistentDataType.BYTE)) {
            return true;
        }

        // Check for legacy display name patterns
        Component displayName = meta.displayName();
        if (displayName != null) {
            String displayNameString = displayName.toString();
            if (displayNameString.contains("Elytra Mount") || displayNameString.contains("Elytra")) {
                return true;
            }
        }

        // Check for characteristic lore
        List<Component> lore = meta.lore();
        if (lore != null) {
            for (Component line : lore) {
                String lineString = line.toString();
                if (lineString.contains("Active Mount") || lineString.contains("Do Not Remove") ||
                        lineString.contains("Magical wings")) {
                    return true;
                }
            }
        }

        return false;
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
     * New method to check if player has been gliding long enough for height checks
     *
     * @param player The player
     * @return True if the player has been gliding long enough
     */
    public boolean hasBeenGlidingLongEnough(Player player) {
        Long glidingStart = glidingStartTimes.get(player.getUniqueId());
        if (glidingStart == null) {
            return false;
        }

        long timeSinceGliding = (System.currentTimeMillis() - glidingStart) / 50; // Convert to ticks
        return timeSinceGliding >= GLIDING_TIME_BEFORE_HEIGHT_CHECK;
    }

    /**
     * New method to get height limit for region
     *
     * @param player The player
     * @return Height limit for the player's current region
     */
    public double getHeightLimitForRegion(Player player) {
        String worldName = player.getWorld().getName().toLowerCase();

        // Use config method but with better defaults
        return switch (worldName) {
            case "frostfall" -> 195.0;
            case "deadpeaks" -> 70.0;
            case "avalon" -> 130.0;
            default -> 220.0; // Increased default height limit
        };
    }

    /**
     * landing detection with better grace period handling
     *
     * @param player The player
     * @return True if the player has landed
     */
    public boolean hasPlayerLanded(Player player) {
        // If not gliding anymore, definitely landed
        if (!player.isGliding()) {
            return true;
        }

        // Grace period - don't detect landing for first 5 seconds after launch
        Long launchTime = launchTimes.get(player.getUniqueId());
        if (launchTime != null) {
            long timeSinceLaunch = (System.currentTimeMillis() - launchTime) / 50; // Convert to ticks
            if (timeSinceLaunch < LAUNCH_GRACE_PERIOD) {
                return false; // Still in grace period
            }
        }

        // More conservative landing detection
        // Check if player is very close to ground AND has low velocity
        if (isPlayerVeryCloseToGround(player)) {
            Vector velocity = player.getVelocity();
            if (Math.abs(velocity.getY()) < LANDING_VELOCITY_THRESHOLD &&
                    velocity.length() < LANDING_SPEED_THRESHOLD) {
                return true;
            }
        }

        // Check for actual ground contact
        return player.isOnGround() && isPlayerOnSolidGround(player);
    }

    /**
     * Better ground detection - checks if player is very close to solid ground
     *
     * @param player The player
     * @return True if player is very close to ground
     */
    private boolean isPlayerVeryCloseToGround(Player player) {
        Location loc = player.getLocation();

        // Check directly below and slightly below
        for (double y = 0.1; y <= 1.5; y += 0.1) {
            Block block = loc.clone().subtract(0, y, 0).getBlock();
            if (block.getType().isSolid()) {
                return y <= 0.5; // Only true if very close (within 0.5 blocks)
            }
        }
        return false;
    }

    /**
     * Checks if player is on solid ground
     *
     * @param player The player
     * @return True if player is on solid ground
     */
    private boolean isPlayerOnSolidGround(Player player) {
        if (!player.isOnGround()) {
            return false;
        }

        Location loc = player.getLocation();
        Block blockBelow = loc.clone().subtract(0, 0.1, 0).getBlock();
        return blockBelow.getType().isSolid();
    }

    /**
     * Better safety check for dismounting
     *
     * @param player The player
     * @return True if player is safely on ground
     */
    private boolean isPlayerSafeOnGround(Player player) {
        if (!player.isOnGround()) {
            return false;
        }

        Location loc = player.getLocation();

        // Check if there's solid ground below
        Block blockBelow = loc.clone().subtract(0, 0.1, 0).getBlock();
        if (!blockBelow.getType().isSolid()) {
            return false;
        }

        // Check if there's enough space above (not stuck in blocks)
        Block blockAbove = loc.clone().add(0, 1, 0).getBlock();
        return blockAbove.getType() == Material.AIR;
    }

    /**
     * Improved launch requirements check
     *
     * @param player The player
     * @return True if the player can launch
     */
    private boolean checkElytraLaunchRequirements(Player player) {
        Location loc = player.getLocation();
        double maxHeight = getHeightLimitForRegion(player);

        // Check current height
        if (loc.getY() >= maxHeight - 10) { // Leave 10 block buffer
            player.sendMessage(Component.text("Unable to launch elytra mount - too close to height limit (" +
                    (int) maxHeight + ")", NamedTextColor.RED, TextDecoration.BOLD));
            return false;
        }

        // Check if there's enough space above for launch
        boolean hasSpace = true;
        for (int i = 1; i <= 5; i++) {
            Block blockAbove = loc.clone().add(0, i, 0).getBlock();
            if (blockAbove.getType() != Material.AIR) {
                hasSpace = false;
                break;
            }
        }

        if (!hasSpace) {
            player.sendMessage(Component.text("Unable to launch elytra mount - insufficient space above",
                    NamedTextColor.RED, TextDecoration.BOLD));
            return false;
        }

        // Check if player is in water or lava
        if (player.isInWater() || loc.getBlock().getType() == Material.LAVA) {
            player.sendMessage(Component.text("Cannot launch elytra mount while in water or lava.",
                    NamedTextColor.RED));
            return false;
        }

        return true;
    }

    /**
     * Improved elytra activation with better timing
     *
     * @param player The player
     */
    private void activateElytra(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Gentler launch velocity
        player.setVelocity(new Vector(0, LAUNCH_VELOCITY, 0));

        // Record launch time for grace period
        launchTimes.put(playerUUID, System.currentTimeMillis());

        // Schedule equipping the elytra after a delay to allow proper launch
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    forceCleanupAllData(playerUUID);
                    return;
                }

                player.sendMessage(Component.text("LAUNCHING...", NamedTextColor.WHITE, TextDecoration.BOLD));

                // Enhanced chestplate handling
                equipElytraWithChestplateProperties(player);

                // Start gliding and record the time
                player.setGliding(true);
                glidingStartTimes.put(playerUUID, System.currentTimeMillis());

                // Register mount
                manager.registerActiveMount(playerUUID, ElytraMount.this);

                // Play sound using Adventure API
                player.playSound(Sound.sound(org.bukkit.Sound.ITEM_ELYTRA_FLYING, Sound.Source.PLAYER, 1.0f, 1.0f));

                // Start landing detection with delay
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
        }.runTaskLater(manager.getPlugin(), 20L); // 1 second delay
    }

    /**
     * chestplate handling - stores complete state and transfers properties to elytra
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
        ItemStack elytra = createElytra(chestplateState);

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
    private ItemStack createElytra(ChestplateState chestplateState) {
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
            Component originalName = chestplateState.getDisplayName();
            if (originalName != null) {
                Component displayName = Component.text("✈ Elytra ", NamedTextColor.AQUA)
                        .append(Component.text("(", NamedTextColor.GRAY))
                        .append(originalName)
                        .append(Component.text(")", NamedTextColor.GRAY));
                elytraMeta.displayName(displayName);
            } else {
                elytraMeta.displayName(Component.text("✈ Elytra Mount", NamedTextColor.AQUA));
            }

            // Create enhanced lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Magical wings enhanced with your", NamedTextColor.GRAY));
            lore.add(Component.text("chestplate's protective properties.", NamedTextColor.GRAY));
            lore.add(Component.empty());

            if (chestplateState.hasOriginalItem()) {
                lore.add(Component.text("Inherited Properties:", NamedTextColor.YELLOW));
                if (!chestplateState.getEnchantments().isEmpty()) {
                    lore.add(Component.text("• Enchantments: " + chestplateState.getEnchantments().size(),
                            NamedTextColor.GRAY));
                }
                if (chestplateState.isUnbreakable()) {
                    lore.add(Component.text("• Unbreakable", NamedTextColor.GRAY));
                }
                lore.add(Component.empty());
            }

            lore.add(Component.text("Duration: " + manager.getConfig().getElytraDuration() + " seconds",
                    NamedTextColor.YELLOW));
            lore.add(Component.text("Active Mount - Do Not Remove", NamedTextColor.GRAY));

            // Add original lore if it existed
            List<Component> originalLore = chestplateState.getLore();
            if (originalLore != null && !originalLore.isEmpty()) {
                lore.add(Component.empty());
                lore.add(Component.text("Original Properties:", NamedTextColor.DARK_GRAY));
                for (Component loreLine : originalLore) {
                    lore.add(Component.text().color(NamedTextColor.DARK_GRAY).append(loreLine).build());
                }
            }

            elytraMeta.lore(lore);

            // Mark as enhanced elytra mount
            NamespacedKey mountKey = new NamespacedKey(manager.getPlugin(), "_elytra_mount");
            elytraMeta.getPersistentDataContainer().set(mountKey, PersistentDataType.BYTE, (byte) 1);

            elytra.setItemMeta(elytraMeta);
        }

        return elytra;
    }

    /**
     * Starts landing detection with proper delay
     *
     * @param player The player
     */
    private void startLandingDetectionDelayed(Player player) {
        // Increased delay before starting landing detection
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && isUsingElytra(player)) {
                    startLandingDetection(player);
                }
            }
        }.runTaskLater(manager.getPlugin(), 80L); // 4 seconds
    }

    /**
     * Starts landing detection for a player with improved logic
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

                // Better landing detection
                if (hasPlayerLanded(player)) {
                    cancel();
                    landingDetectionTasks.remove(playerUUID);

                    // Dismount with a gentle message
                    dismount(player, false);
                    player.sendMessage(Component.text("You have landed safely. Your elytra mount has been deactivated.",
                            NamedTextColor.YELLOW));
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

        itemMeta.displayName(Component.text("Elytra Mount", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Magical wings that allow you to", NamedTextColor.GRAY));
        lore.add(Component.text("soar through the skies gracefully.", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Features:", NamedTextColor.YELLOW));
        lore.add(Component.text("• Inherits chestplate properties", NamedTextColor.GRAY));
        lore.add(Component.text("• Advanced landing detection", NamedTextColor.GRAY));
        lore.add(Component.text("• Enhanced durability", NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Duration: " + manager.getConfig().getElytraDuration() + " seconds",
                NamedTextColor.YELLOW));
        lore.add(Component.text("Cooldown: 2 minutes after damage", NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to activate", NamedTextColor.GRAY));
        lore.add(Component.text("Permanent Untradeable", NamedTextColor.GRAY));

        itemMeta.lore(lore);
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
        Component displayName = meta.displayName();
        if (displayName != null) {
            String displayNameString = displayName.toString();
            return displayNameString.contains("Elytra Mount");
        }

        return false;
    }

    /**
     *: Forces cleanup for stuck elytra on player
     */
    public boolean forceCleanupStuckElytra(Player player) {
        UUID playerUUID = player.getUniqueId();
        boolean foundStuckElytra = false;

        // Check for stuck elytra in armor slot
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && isAnyElytraMount(chestplate)) {
            foundStuckElytra = true;
        }

        if (foundStuckElytra || isUsingElytra(player)) {
            // Force complete dismount
            dismount(player, false);
            player.sendMessage(Component.text("Forced cleanup of stuck elytra mount completed.",
                    NamedTextColor.GREEN));
            return true;
        }

        return false;
    }

    /**
     *: Gets all players with potentially stuck elytra
     */
    public List<Player> getPlayersWithStuckElytra() {
        List<Player> stuckPlayers = new ArrayList<>();

        for (Player player : manager.getPlugin().getServer().getOnlinePlayers()) {
            ItemStack chestplate = player.getInventory().getChestplate();

            // Check if they have elytra mount but aren't registered as using one
            if (chestplate != null && isAnyElytraMount(chestplate) && !isUsingElytra(player)) {
                stuckPlayers.add(player);
            }

            // Check if they're registered as using elytra but don't have the item
            if (isUsingElytra(player) && (chestplate == null || !isAnyElytraMount(chestplate))) {
                stuckPlayers.add(player);
            }
        }

        return stuckPlayers;
    }

    /**
     * cleanup method to be called when player leaves or plugin disables
     *
     * @param player The player
     */
    public void cleanup(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Force complete cleanup
        forceCleanupAllData(playerUUID);

        // Clean up stored data
        originalChestplates.remove(playerUUID);

        // Restore chestplate if player is online
        if (player.isOnline()) {
            forceRestoreChestplate(player);
        }
    }

    /**
     *: Cleanup all stuck elytra mounts server-wide
     */
    public void cleanupAllStuckElytra() {
        List<Player> stuckPlayers = getPlayersWithStuckElytra();

        for (Player player : stuckPlayers) {
            forceCleanupStuckElytra(player);
        }

        manager.getPlugin().getLogger().info("Cleaned up " + stuckPlayers.size() + " stuck elytra mounts");
    }
}
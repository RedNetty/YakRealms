package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.types.CrateType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Enhanced CSGO-style crate animation with smooth scrolling and proper reward handling
 * FIXED: Now properly displays the best reward while giving all actual rewards
 */
public class CrateAnimationManager {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;

    // Active animations
    private final Map<UUID, BukkitTask> activeAnimations = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();

    // Animation constants - Enhanced for better performance
    private static final int DISPLAY_SLOTS = 9;
    private static final int TOTAL_ITEMS = 50;
    private static final int WIN_POSITION = 25; // Center of strip
    private static final String INVENTORY_TITLE = "✦ Mystical Crate Opening ✦";

    // Enhanced scrolling mechanics
    private static final double INITIAL_SPEED = 0.8;
    private static final double DECELERATION = 0.985;
    private static final double MIN_SPEED = 0.01;
    private static final int SPIN_DURATION = 200;

    public CrateAnimationManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
    }

    /**
     * ENHANCED: Starts the crate opening animation with pre-generated rewards
     * Fixed to properly display the best reward while giving all rewards
     */
    public void startCrateOpeningWithRewards(CrateOpening opening, List<ItemStack> preGeneratedRewards) {
        if (opening == null || opening.getPlayer() == null || preGeneratedRewards == null || preGeneratedRewards.isEmpty()) {
            logger.warning("Invalid parameters provided to startCrateOpeningWithRewards");
            return;
        }

        Player player = opening.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Cancel any existing animations
            cancelAnimation(playerId);

            // Create the animation inventory
            Inventory inventory = createEnhancedAnimationInventory(opening.getCrateType());
            player.openInventory(inventory);
            openInventories.put(playerId, inventory);

            // Start the enhanced animation with the pre-generated rewards
            BukkitTask animationTask = new EnhancedCSGOStyleAnimation(opening, inventory, preGeneratedRewards)
                    .runTaskTimer(plugin, 0L, 1L);
            activeAnimations.put(playerId, animationTask);

            // Start enhanced action bar updates
            startEnhancedActionBarUpdates(player, opening);

            // Play enhanced opening sound
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);

            logger.info("Started enhanced crate animation with " + preGeneratedRewards.size() +
                    " pre-generated rewards for player: " + player.getName());

        } catch (Exception e) {
            logger.severe("Error starting enhanced crate animation: " + e.getMessage());
            e.printStackTrace();
            crateManager.completeCrateOpening(opening);
        }
    }

    /**
     * Legacy method for backward compatibility
     */
    public void startCrateOpening(CrateOpening opening) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid crate opening provided");
            return;
        }

        // Generate rewards for legacy compatibility
        List<ItemStack> rewards = crateManager.getRewardsManager()
                .generateRewards(opening.getCrateType(), opening.getConfiguration());

        if (rewards.isEmpty()) {
            logger.warning("No rewards generated for legacy animation");
            return;
        }

        // Use the enhanced method
        startCrateOpeningWithRewards(opening, rewards);
    }

    /**
     * ENHANCED: Creates a more polished animation inventory
     */
    private Inventory createEnhancedAnimationInventory(CrateType crateType) {
        Inventory inventory = Bukkit.createInventory(null, 27, INVENTORY_TITLE);

        // Enhanced background with tier-based colors
        Material backgroundMaterial = getBackgroundMaterial(crateType);
        ItemStack background = new ItemStack(backgroundMaterial);
        ItemMeta backgroundMeta = background.getItemMeta();
        if (backgroundMeta != null) {
            backgroundMeta.setDisplayName(" ");
            background.setItemMeta(backgroundMeta);
        }

        // Fill enhanced background
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18) {
                inventory.setItem(i, background);
            }
        }

        // Enhanced selection indicator with tier-specific styling
        ItemStack selector = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta selectorMeta = selector.getItemMeta();
        if (selectorMeta != null) {
            ChatColor tierColor = getTierColor(crateType.getTier());
            selectorMeta.setDisplayName(tierColor + "▼ YOUR FEATURED REWARD ▼");
            selectorMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "This represents your best reward",
                    ChatColor.GRAY + "You will receive ALL generated items!"
            ));
            selector.setItemMeta(selectorMeta);
        }
        inventory.setItem(4, selector); // Top center
        inventory.setItem(22, selector); // Bottom center

        return inventory;
    }

    /**
     * ENHANCED: CSGO-style animation with proper reward display
     */
    private class EnhancedCSGOStyleAnimation extends BukkitRunnable {
        private final CrateOpening opening;
        private final Inventory inventory;
        private final Player player;
        private final UUID playerId;
        private final List<ItemStack> actualRewards;
        private final List<ItemStack> itemStrip;
        private final ItemStack displayReward; // The item shown in the animation
        private final String featuredRewardName;

        private double currentPosition = 0.0;
        private double currentSpeed = INITIAL_SPEED;
        private int tick = 0;
        private boolean isCompleted = false;
        private boolean rewardsGiven = false;
        private final int centerSlot = DISPLAY_SLOTS / 2; // 4 - center slot index
        private final double finalPosition = WIN_POSITION - centerSlot; // 25 - 4 = 21

        public EnhancedCSGOStyleAnimation(CrateOpening opening, Inventory inventory, List<ItemStack> actualRewards) {
            this.opening = opening;
            this.inventory = inventory;
            this.player = opening.getPlayer();
            this.playerId = player.getUniqueId();
            this.actualRewards = new ArrayList<>(actualRewards);

            // FIXED: Find the best reward to display in the animation
            this.displayReward = findBestRewardForDisplay(actualRewards);
            this.featuredRewardName = displayReward.hasItemMeta() && displayReward.getItemMeta().hasDisplayName() ?
                    displayReward.getItemMeta().getDisplayName() :
                    ChatColor.WHITE + displayReward.getType().name().replace("_", " ");

            this.itemStrip = createEnhancedItemStrip();

            // Enhanced debug logging
            logger.info("Enhanced animation started for " + player.getName() + ":");
            logger.info("  Total rewards: " + actualRewards.size());
            logger.info("  Featured reward: " + featuredRewardName);
            logger.info("  All rewards player will receive:");
            for (int i = 0; i < actualRewards.size(); i++) {
                ItemStack reward = actualRewards.get(i);
                String rewardName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName() ?
                        reward.getItemMeta().getDisplayName() :
                        reward.getType().name().replace("_", " ");
                logger.info("    " + (i + 1) + ": " + rewardName + " x" + reward.getAmount());
            }
        }

        @Override
        public void run() {
            if (player == null || !player.isOnline() || isCompleted) {
                cleanup();
                return;
            }

            tick++;

            try {
                // Update position and speed
                currentPosition += currentSpeed;

                // Enhanced deceleration curve
                if (tick > 50) {
                    currentSpeed *= DECELERATION;
                }

                // FIXED: Use pre-calculated final position
                if (currentSpeed < MIN_SPEED || tick > SPIN_DURATION) {
                    currentPosition = finalPosition;

                    logger.info("Animation stopping:");
                    logger.info("  WIN_POSITION: " + WIN_POSITION);
                    logger.info("  Center slot: " + centerSlot);
                    logger.info("  Final position: " + finalPosition);

                    finishEnhancedAnimation();
                    return;
                }

                // Update display
                updateEnhancedDisplay();

                // Play enhanced tick sound
                if (tick % Math.max(1, (int)(10 / currentSpeed)) == 0) {
                    float pitch = Math.min(2.0f, 0.5f + (float)currentSpeed);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, pitch);
                }

            } catch (Exception e) {
                logger.warning("Error in enhanced animation: " + e.getMessage());
                cleanup();
            }
        }

        /**
         * FIXED: Finds the best reward to display as the featured item
         * Priority: Equipment > Orbs > Scrolls > Gems > Other
         */
        private ItemStack findBestRewardForDisplay(List<ItemStack> rewards) {
            ItemStack bestReward = null;
            int bestPriority = -1;

            for (ItemStack reward : rewards) {
                if (reward == null) continue;

                int priority = getRewardDisplayPriority(reward);
                if (priority > bestPriority) {
                    bestPriority = priority;
                    bestReward = reward;
                }
            }

            // Fallback to first reward if no priority match
            return bestReward != null ? bestReward.clone() : rewards.get(0).clone();
        }

        /**
         * ENHANCED: Determines display priority for rewards
         */
        private int getRewardDisplayPriority(ItemStack reward) {
            if (reward == null || !reward.hasItemMeta()) {
                return 0;
            }

            String displayName = reward.getItemMeta().getDisplayName();
            if (displayName == null) {
                return 0;
            }

            // Equipment (highest priority)
            if (displayName.contains("Sword") || displayName.contains("Staff") ||
                    displayName.contains("Spear") || displayName.contains("Axe") ||
                    displayName.contains("Helmet") || displayName.contains("Chestplate") ||
                    displayName.contains("Leggings") || displayName.contains("Boots") ||
                    displayName.contains("Shield") || displayName.contains("Bow")) {

                // Extra priority for higher rarity equipment
                if (displayName.contains("Legendary") || displayName.contains("Unique")) {
                    return 120;
                } else if (displayName.contains("Rare")) {
                    return 110;
                } else {
                    return 100;
                }
            }

            // Orbs (second highest)
            if (displayName.contains("Orb")) {
                return displayName.contains("Legendary") ? 90 : 80;
            }

            // Scrolls (third highest)
            if (displayName.contains("Scroll") || displayName.contains("SCROLL")) {
                return displayName.contains("Protection") ? 70 : 60;
            }

            // Gems and bank notes (lower priority)
            if (displayName.contains("Gem") || displayName.contains("Bank Note")) {
                return 40;
            }

            // Halloween items (special priority)
            if (displayName.contains("Halloween") || displayName.contains("🎃")) {
                return 85;
            }

            // Everything else
            return 20;
        }

        /**
         * FIXED: Creates item strip with exact positioning
         */
        private List<ItemStack> createEnhancedItemStrip() {
            List<ItemStack> strip = new ArrayList<>();

            logger.info("Creating item strip with " + TOTAL_ITEMS + " items, WIN_POSITION: " + WIN_POSITION);
            logger.info("Display reward: " + getItemDisplayName(displayReward));

            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (i == WIN_POSITION) {
                    // Place the featured reward at the EXACT winning position
                    strip.add(displayReward.clone());
                    logger.info("Placed display reward at index " + i + ": " + getItemDisplayName(displayReward));
                } else {
                    // Generate varied filler items
                    ItemStack filler = generateEnhancedFillerItem(opening.getCrateType());
                    strip.add(filler);
                    if (i < 5 || i > TOTAL_ITEMS - 5) { // Log first and last few items
                        logger.info("Placed filler at index " + i + ": " + getItemDisplayName(filler));
                    }
                }
            }

            return strip;
        }

        /**
         * ENHANCED: Generates more realistic filler items
         */
        private ItemStack generateEnhancedFillerItem(CrateType crateType) {
            try {
                int tier = crateType.getTier();
                int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
                int rarity = ThreadLocalRandom.current().nextInt(4) + 1;

                // Occasionally use items from other actual rewards for variety
                if (ThreadLocalRandom.current().nextInt(100) < 20 && actualRewards.size() > 1) {
                    ItemStack randomActualReward = actualRewards.get(ThreadLocalRandom.current().nextInt(actualRewards.size()));
                    if (randomActualReward != null && !randomActualReward.equals(displayReward)) {
                        return randomActualReward.clone();
                    }
                }

                return crateManager.getRewardsManager().createDropWithRarity(tier, itemType, rarity);
            } catch (Exception e) {
                // Enhanced fallback
                return createEnhancedFallbackItem(crateType);
            }
        }

        /**
         * ENHANCED: Creates better fallback items
         */
        private ItemStack createEnhancedFallbackItem(CrateType crateType) {
            Material[] materials = {
                    Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.GOLDEN_SWORD,
                    Material.IRON_AXE, Material.DIAMOND_AXE, Material.GOLDEN_AXE,
                    Material.EMERALD, Material.DIAMOND, Material.GOLD_INGOT
            };

            Material material = materials[ThreadLocalRandom.current().nextInt(materials.length)];
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                ChatColor color = getTierColor(crateType.getTier());
                meta.setDisplayName(color + "Filler Item");
                item.setItemMeta(meta);
            }

            return item;
        }

        /**
         * FIXED: Updates display with accurate positioning and detailed logging
         */
        private void updateEnhancedDisplay() {
            // Calculate the starting index more precisely
            int startIndex = (int) Math.floor(currentPosition);

            // Log during final positioning
            if (currentSpeed < MIN_SPEED || isCompleted) {
                logger.info("=== DISPLAY UPDATE DEBUG ===");
                logger.info("Current position: " + currentPosition);
                logger.info("Start index: " + startIndex);
                logger.info("Center slot: " + centerSlot);
                logger.info("Display slots: " + DISPLAY_SLOTS);
            }

            for (int slot = 0; slot < DISPLAY_SLOTS; slot++) {
                int itemIndex = (startIndex + slot) % itemStrip.size();
                if (itemIndex < 0) itemIndex += itemStrip.size();

                ItemStack item = itemStrip.get(itemIndex);
                boolean isCenter = (slot == centerSlot);

                // Log center item details
                if (isCenter && (currentSpeed < MIN_SPEED || isCompleted)) {
                    logger.info("Center item (slot " + slot + ", index " + itemIndex + "): " + getItemDisplayName(item));
                    logger.info("Expected item: " + getItemDisplayName(displayReward));
                    logger.info("Items match: " + isSameItem(item, displayReward));
                }

                item = enhanceDisplayItem(item, isCenter);
                inventory.setItem(9 + slot, item);
            }

            if (currentSpeed < MIN_SPEED || isCompleted) {
                logger.info("=== END DISPLAY DEBUG ===");
            }
        }

        /**
         * ENHANCED: Enhances items for display
         */
        private ItemStack enhanceDisplayItem(ItemStack item, boolean isCenter) {
            if (item == null) return item;

            ItemStack enhanced = item.clone();
            if (enhanced.hasItemMeta()) {
                ItemMeta meta = enhanced.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                if (isCenter) {
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "◆ FEATURED REWARD ◆");
                    lore.add(ChatColor.GRAY + "This is your best reward!");
                } else {
                    // Add subtle indicator for non-center items
                    lore.add("");
                    lore.add(ChatColor.DARK_GRAY + "Preview Item");
                }

                meta.setLore(lore);
                enhanced.setItemMeta(meta);
            }

            return enhanced;
        }

        /**
         * FIXED: Finishes animation with accurate final positioning
         */
        private void finishEnhancedAnimation() {
            if (isCompleted) return;

            isCompleted = true;

            // FIXED: Ensure exact positioning on the winning item
            currentPosition = finalPosition;

            // Update display one final time with exact positioning
            updateEnhancedDisplay();

            // VERIFICATION: Check what item is actually at the center now
            int startIndex = (int) Math.floor(currentPosition);
            int centerItemIndex = (startIndex + centerSlot) % itemStrip.size();
            if (centerItemIndex < 0) centerItemIndex += itemStrip.size();

            ItemStack actualCenterItem = itemStrip.get(centerItemIndex);

            // Log for debugging
            logger.info("=== FINAL POSITION DEBUG ===");
            logger.info("Current position: " + currentPosition);
            logger.info("Start index: " + startIndex);
            logger.info("Center slot: " + centerSlot);
            logger.info("Center item index: " + centerItemIndex);
            logger.info("Expected center item: " + getItemDisplayName(displayReward));
            logger.info("Actual center item: " + getItemDisplayName(actualCenterItem));
            logger.info("Items match: " + isSameItem(displayReward, actualCenterItem));
            logger.info("=== END DEBUG ===");

            // Enhanced completion sounds
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);

            // Give rewards (only once)
            if (!rewardsGiven) {
                giveRewardsToPlayer();
                rewardsGiven = true;
            }

            // ENHANCED: Show what item was actually selected
            displayEnhancedCompletionMessage(actualCenterItem);

            // Schedule cleanup
            new BukkitRunnable() {
                @Override
                public void run() {
                    crateManager.completeCrateOpeningWithRewards(opening, actualRewards);
                    player.closeInventory();
                    cleanup();
                }
            }.runTaskLater(plugin, 100L); // 5 second delay
        }

        /**
         * FIXED: Displays completion message with the actually selected item
         */
        private void displayEnhancedCompletionMessage(ItemStack actualSelectedItem) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "✦ ✦ ✦ " + ChatColor.BOLD + "CRATE OPENED SUCCESSFULLY" + ChatColor.GOLD + " ✦ ✦ ✦");
            player.sendMessage(ChatColor.GRAY + "You received " + ChatColor.WHITE + actualRewards.size() + ChatColor.GRAY + " valuable items!");
            player.sendMessage("");

            // Show the item that was actually selected in the animation
            String selectedItemName = getItemDisplayName(actualSelectedItem);
            player.sendMessage(ChatColor.YELLOW + "🎯 " + ChatColor.BOLD + "SELECTED IN ANIMATION:" + ChatColor.RESET + " " + selectedItemName);
            player.sendMessage(ChatColor.GRAY + "  This item was highlighted in the center slot");
            player.sendMessage("");

            // Show all rewards
            player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "ALL REWARDS RECEIVED:");
            for (int i = 0; i < actualRewards.size(); i++) {
                ItemStack reward = actualRewards.get(i);
                String rewardName = getItemDisplayName(reward);

                // Check if this reward matches the selected item
                boolean isSelected = isSameItem(reward, actualSelectedItem);
                String prefix = isSelected ? ChatColor.YELLOW + "🎯 " : ChatColor.WHITE + "  • ";
                String suffix = reward.getAmount() > 1 ? ChatColor.GRAY + " x" + reward.getAmount() : "";

                player.sendMessage(prefix + rewardName + suffix);
                if (isSelected) {
                    player.sendMessage(ChatColor.GRAY + "     ↑ This was the selected item!");
                }
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "✨ " + ChatColor.ITALIC + "Mystical energies have blessed you!" + ChatColor.AQUA + " ✨");
            player.sendMessage("");
        }

        /**
         * Helper method to check if two items are the same
         */
        private boolean isSameItem(ItemStack item1, ItemStack item2) {
            if (item1 == null || item2 == null) return false;

            // Check material first
            if (item1.getType() != item2.getType()) return false;

            // Check display names
            String name1 = item1.hasItemMeta() && item1.getItemMeta().hasDisplayName() ?
                    item1.getItemMeta().getDisplayName() : item1.getType().name();
            String name2 = item2.hasItemMeta() && item2.getItemMeta().hasDisplayName() ?
                    item2.getItemMeta().getDisplayName() : item2.getType().name();

            return name1.equals(name2);
        }

        /**
         * Helper method to get item display name
         */
        private String getItemDisplayName(ItemStack item) {
            if (item == null) return "null";
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
            return ChatColor.WHITE + item.getType().name().replace("_", " ");
        }

        /**
         * Gives all rewards to the player
         */
        private void giveRewardsToPlayer() {
            int overflow = 0;
            for (ItemStack reward : actualRewards) {
                if (reward != null) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
                    if (!leftover.isEmpty()) {
                        for (ItemStack overflowItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                            overflow++;
                        }
                    }
                }
            }

            if (overflow > 0) {
                player.sendMessage(ChatColor.YELLOW + "⚠ " + overflow + " items were dropped due to full inventory!");
            }
        }

        /**
         * Cleans up the animation
         */
        private void cleanup() {
            cancel();
            activeAnimations.remove(playerId);
            openInventories.remove(playerId);
        }
    }

    /**
     * ENHANCED: Starts action bar updates with better messaging
     */
    private void startEnhancedActionBarUpdates(Player player, CrateOpening opening) {
        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !activeAnimations.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                String message = generateEnhancedActionBarMessage(opening, ticks);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 5L);

        actionBarTasks.put(player.getUniqueId(), task);
    }

    /**
     * ENHANCED: Generates more engaging action bar messages
     */
    private String generateEnhancedActionBarMessage(CrateOpening opening, int ticks) {
        String crateName = opening.getCrateType().getDisplayName();
        String dots = ".".repeat((ticks / 4) % 4);

        String[] phases = {
                "Preparing mystical energies",
                "Unsealing ancient magic",
                "Channeling crate power",
                "Revealing your destiny"
        };

        String phase = phases[(ticks / 20) % phases.length];

        return ChatColor.AQUA + "✨ " + phase + " for " + crateName + " Crate" + dots + " ✨";
    }

    /**
     * ENHANCED: Gets background material based on crate tier
     */
    private Material getBackgroundMaterial(CrateType crateType) {
        return switch (crateType.getTier()) {
            case 1 -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case 2 -> Material.LIME_STAINED_GLASS_PANE;
            case 3 -> Material.CYAN_STAINED_GLASS_PANE;
            case 4 -> Material.PURPLE_STAINED_GLASS_PANE;
            case 5 -> Material.ORANGE_STAINED_GLASS_PANE;
            case 6 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }

    /**
     * ENHANCED: Gets tier color
     */
    private ChatColor getTierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.LIGHT_PURPLE;
            case 5 -> ChatColor.YELLOW;
            case 6 -> ChatColor.BLUE;
            default -> ChatColor.GRAY;
        };
    }

    /**
     * Cancels animation for a player
     */
    public void cancelAnimation(UUID playerId) {
        BukkitTask task = activeAnimations.remove(playerId);
        if (task != null) task.cancel();

        BukkitTask actionBarTask = actionBarTasks.remove(playerId);
        if (actionBarTask != null) actionBarTask.cancel();

        openInventories.remove(playerId);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /**
     * Cleanup all animations
     */
    public void cleanup() {
        activeAnimations.values().forEach(task -> {
            if (task != null) task.cancel();
        });
        actionBarTasks.values().forEach(task -> {
            if (task != null) task.cancel();
        });

        activeAnimations.clear();
        openInventories.clear();
        actionBarTasks.clear();
    }

    /**
     * Get enhanced animation statistics
     */
    public Map<String, Object> getAnimationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAnimations", activeAnimations.size());
        stats.put("displaySlots", DISPLAY_SLOTS);
        stats.put("totalStripItems", TOTAL_ITEMS);
        stats.put("winPosition", WIN_POSITION);
        stats.put("spinDuration", SPIN_DURATION);
        stats.put("version", "Enhanced v2.0");
        return stats;
    }
}
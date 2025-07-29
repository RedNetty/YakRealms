package com.rednetty.server.mechanics.item.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.crates.types.CrateType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * Manages CSGO-style crate opening animations, ensuring smooth scrolling,
 * proper display of the featured reward, and distribution of all generated rewards.
 */
public class CrateAnimationManager {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;

    private final Map<UUID, BukkitTask> activeAnimations = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();

    private static final int DISPLAY_SLOTS = 9;
    private static final int TOTAL_ITEMS = 50;
    private static final int WIN_POSITION = 25;
    private static final String INVENTORY_TITLE = "✦ Mystical Crate Opening ✦";

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
     * Starts the crate opening animation using pre-generated rewards.
     * Displays the featured (best) reward in the animation while ensuring
     * the player receives all actual rewards.
     *
     * @param opening The crate opening instance.
     * @param preGeneratedRewards The list of pre-generated rewards.
     */
    public void startCrateOpeningWithRewards(CrateOpening opening, List<ItemStack> preGeneratedRewards) {
        if (opening == null || opening.getPlayer() == null || preGeneratedRewards == null || preGeneratedRewards.isEmpty()) {
            logger.warning("Invalid parameters provided to startCrateOpeningWithRewards");
            return;
        }

        Player player = opening.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            cancelAnimation(playerId);

            Inventory inventory = createAnimationInventory(opening.getCrateType());
            player.openInventory(inventory);
            openInventories.put(playerId, inventory);

            BukkitTask animationTask = new CSGOStyleAnimation(opening, inventory, preGeneratedRewards)
                    .runTaskTimer(plugin, 0L, 1L);
            activeAnimations.put(playerId, animationTask);

            startActionBarUpdates(player, opening);

            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);

        } catch (Exception e) {
            logger.severe("Error starting crate animation: " + e.getMessage());
            e.printStackTrace();
            crateManager.completeCrateOpening(opening);
        }
    }

    /**
     * Legacy method for starting a crate opening animation.
     * Generates rewards internally for backward compatibility.
     *
     * @param opening The crate opening instance.
     */
    public void startCrateOpening(CrateOpening opening) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid crate opening provided");
            return;
        }

        List<ItemStack> rewards = crateManager.getRewardsManager()
                .generateRewards(opening.getCrateType(), opening.getConfiguration());

        if (rewards.isEmpty()) {
            logger.warning("No rewards generated for legacy animation");
            return;
        }

        startCrateOpeningWithRewards(opening, rewards);
    }

    /**
     * Creates the inventory used for the crate animation, styled based on the crate type.
     *
     * @param crateType The type of crate being opened.
     * @return The configured animation inventory.
     */
    private Inventory createAnimationInventory(CrateType crateType) {
        Inventory inventory = Bukkit.createInventory(null, 27, INVENTORY_TITLE);

        Material backgroundMaterial = getBackgroundMaterial(crateType);
        ItemStack background = new ItemStack(backgroundMaterial);
        ItemMeta backgroundMeta = background.getItemMeta();
        if (backgroundMeta != null) {
            backgroundMeta.setDisplayName(" ");
            background.setItemMeta(backgroundMeta);
        }

        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18) {
                inventory.setItem(i, background);
            }
        }

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
        inventory.setItem(4, selector);
        inventory.setItem(22, selector);

        return inventory;
    }

    /**
     * Inner class handling the CSGO-style scrolling animation logic.
     */
    private class CSGOStyleAnimation extends BukkitRunnable {
        private final CrateOpening opening;
        private final Inventory inventory;
        private final Player player;
        private final UUID playerId;
        private final List<ItemStack> actualRewards;
        private final List<ItemStack> itemStrip;
        private final ItemStack displayReward;
        private final String featuredRewardName;

        private double currentPosition = 0.0;
        private double currentSpeed = INITIAL_SPEED;
        private int tick = 0;
        private boolean isCompleted = false;
        private boolean rewardsGiven = false;
        private final int centerSlot = DISPLAY_SLOTS / 2;

        public CSGOStyleAnimation(CrateOpening opening, Inventory inventory, List<ItemStack> actualRewards) {
            this.opening = opening;
            this.inventory = inventory;
            this.player = opening.getPlayer();
            this.playerId = player.getUniqueId();
            this.actualRewards = new ArrayList<>(actualRewards);

            this.displayReward = findBestRewardForDisplay(actualRewards);
            this.featuredRewardName = displayReward.hasItemMeta() && displayReward.getItemMeta().hasDisplayName() ?
                    displayReward.getItemMeta().getDisplayName() :
                    ChatColor.WHITE + displayReward.getType().name().replace("_", " ");

            this.itemStrip = createItemStrip();
        }

        @Override
        public void run() {
            if (player == null || !player.isOnline() || isCompleted) {
                cleanup();
                return;
            }

            tick++;

            try {
                currentPosition += currentSpeed;

                if (tick > 50) {
                    currentSpeed *= DECELERATION;
                }

                if (currentSpeed < MIN_SPEED || tick > SPIN_DURATION) {
                    currentPosition = WIN_POSITION - centerSlot;
                    finishAnimation();
                    return;
                }

                updateDisplay();

                if (tick % Math.max(1, (int)(10 / currentSpeed)) == 0) {
                    float pitch = Math.min(2.0f, 0.5f + (float)currentSpeed);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, pitch);
                }

            } catch (Exception e) {
                logger.warning("Error in animation: " + e.getMessage());
                cleanup();
            }
        }

        /**
         * Selects the best reward to feature in the animation based on priority.
         *
         * @param rewards The list of rewards.
         * @return The cloned ItemStack of the featured reward.
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

            return bestReward != null ? bestReward.clone() : rewards.get(0).clone();
        }

        /**
         * Determines the display priority of a reward item.
         *
         * @param reward The reward item.
         * @return The priority score.
         */
        private int getRewardDisplayPriority(ItemStack reward) {
            if (reward == null || !reward.hasItemMeta()) {
                return 0;
            }

            String displayName = reward.getItemMeta().getDisplayName();
            if (displayName == null) {
                return 0;
            }

            if (displayName.contains("Sword") || displayName.contains("Staff") ||
                    displayName.contains("Spear") || displayName.contains("Axe") ||
                    displayName.contains("Helmet") || displayName.contains("Chestplate") ||
                    displayName.contains("Leggings") || displayName.contains("Boots") ||
                    displayName.contains("Shield") || displayName.contains("Bow")) {

                if (displayName.contains("Legendary") || displayName.contains("Unique")) {
                    return 120;
                } else if (displayName.contains("Rare")) {
                    return 110;
                } else {
                    return 100;
                }
            }

            if (displayName.contains("Orb")) {
                return displayName.contains("Legendary") ? 90 : 80;
            }

            if (displayName.contains("Scroll") || displayName.contains("SCROLL")) {
                return displayName.contains("Protection") ? 70 : 60;
            }

            if (displayName.contains("Gem") || displayName.contains("Bank Note")) {
                return 40;
            }

            if (displayName.contains("Halloween") || displayName.contains("🎃")) {
                return 85;
            }

            return 20;
        }

        /**
         * Creates the strip of items for the scrolling animation.
         *
         * @return The list of items in the strip.
         */
        private List<ItemStack> createItemStrip() {
            List<ItemStack> strip = new ArrayList<>(TOTAL_ITEMS);

            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (i == WIN_POSITION) {
                    strip.add(displayReward.clone());
                } else {
                    strip.add(generateFillerItem(opening.getCrateType()));
                }
            }

            return strip;
        }

        /**
         * Generates a filler item for the animation strip.
         *
         * @param crateType The crate type.
         * @return The generated filler item.
         */
        private ItemStack generateFillerItem(CrateType crateType) {
            try {
                int tier = crateType.getTier();
                int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
                int rarity = ThreadLocalRandom.current().nextInt(4) + 1;

                if (ThreadLocalRandom.current().nextInt(100) < 20 && actualRewards.size() > 1) {
                    ItemStack randomActualReward = actualRewards.get(ThreadLocalRandom.current().nextInt(actualRewards.size()));
                    if (randomActualReward != null && !randomActualReward.equals(displayReward)) {
                        return randomActualReward.clone();
                    }
                }

                return crateManager.getRewardsManager().createDropWithRarity(tier, itemType, rarity);
            } catch (Exception e) {
                return createFallbackItem(crateType);
            }
        }

        /**
         * Creates a fallback filler item if generation fails.
         *
         * @param crateType The crate type.
         * @return The fallback item.
         */
        private ItemStack createFallbackItem(CrateType crateType) {
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
         * Updates the displayed items in the inventory based on current position.
         */
        private void updateDisplay() {
            int startIndex = (int) Math.floor(currentPosition);

            for (int slot = 0; slot < DISPLAY_SLOTS; slot++) {
                int itemIndex = (startIndex + slot) % itemStrip.size();
                if (itemIndex < 0) itemIndex += itemStrip.size();

                ItemStack item = itemStrip.get(itemIndex);
                boolean isCenter = (slot == centerSlot);

                item = enhanceDisplayItem(item, isCenter);
                inventory.setItem(9 + slot, item);
            }
        }

        /**
         * Enhances an item's meta for display in the animation.
         *
         * @param item The item to enhance.
         * @param isCenter Whether the item is in the center slot.
         * @return The enhanced item clone.
         */
        private ItemStack enhanceDisplayItem(ItemStack item, boolean isCenter) {
            if (item == null) return item;

            ItemStack enhanced = item.clone();
            if (item.hasItemMeta()) {
                ItemMeta meta = enhanced.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                if (isCenter) {
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "◆ FEATURED REWARD ◆");
                    lore.add(ChatColor.GRAY + "This is your best reward!");
                } else {
                    lore.add("");
                    lore.add(ChatColor.DARK_GRAY + "Preview Item");
                }

                meta.setLore(lore);
                enhanced.setItemMeta(meta);
            }

            return enhanced;
        }

        /**
         * Finishes the animation, gives rewards, and schedules cleanup.
         */
        private void finishAnimation() {
            if (isCompleted) return;

            isCompleted = true;

            updateDisplay();

            int startIndex = (int) Math.floor(currentPosition);
            int centerItemIndex = (startIndex + centerSlot) % itemStrip.size();
            if (centerItemIndex < 0) centerItemIndex += itemStrip.size();

            ItemStack actualCenterItem = itemStrip.get(centerItemIndex);

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);

            if (!rewardsGiven) {
                giveRewardsToPlayer();
                rewardsGiven = true;
            }

            displayCompletionMessage(actualCenterItem);

            new BukkitRunnable() {
                @Override
                public void run() {
                    crateManager.completeCrateOpeningWithRewards(opening, actualRewards);
                    player.closeInventory();
                    cleanup();
                }
            }.runTaskLater(plugin, 100L);
        }

        /**
         * Displays a completion message listing all rewards and the featured item.
         *
         * @param actualSelectedItem The item that landed in the center.
         */
        private void displayCompletionMessage(ItemStack actualSelectedItem) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "✦ ✦ ✦ " + ChatColor.BOLD + "CRATE OPENED SUCCESSFULLY" + ChatColor.GOLD + " ✦ ✦ ✦");
            player.sendMessage(ChatColor.GRAY + "You received " + ChatColor.WHITE + actualRewards.size() + ChatColor.GRAY + " valuable items!");
            player.sendMessage("");

            String selectedItemName = getItemDisplayName(actualSelectedItem);
            player.sendMessage(ChatColor.YELLOW + "🎯 " + ChatColor.BOLD + "SELECTED IN ANIMATION:" + ChatColor.RESET + " " + selectedItemName);
            player.sendMessage(ChatColor.GRAY + "  This item was highlighted in the center slot");
            player.sendMessage("");

            player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "ALL REWARDS RECEIVED:");
            for (int i = 0; i < actualRewards.size(); i++) {
                ItemStack reward = actualRewards.get(i);
                String rewardName = getItemDisplayName(reward);

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
         * Checks if two items are equivalent based on type and display name.
         *
         * @param item1 First item.
         * @param item2 Second item.
         * @return True if items match.
         */
        private boolean isSameItem(ItemStack item1, ItemStack item2) {
            if (item1 == null || item2 == null) return false;

            if (item1.getType() != item2.getType()) return false;

            String name1 = item1.hasItemMeta() && item1.getItemMeta().hasDisplayName() ?
                    item1.getItemMeta().getDisplayName() : item1.getType().name();
            String name2 = item2.hasItemMeta() && item2.getItemMeta().hasDisplayName() ?
                    item2.getItemMeta().getDisplayName() : item2.getType().name();

            return name1.equals(name2);
        }

        /**
         * Gets the display name of an item, falling back to material name.
         *
         * @param item The item.
         * @return The display name.
         */
        private String getItemDisplayName(ItemStack item) {
            if (item == null) return "null";
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
            return ChatColor.WHITE + item.getType().name().replace("_", " ");
        }

        /**
         * Distributes all rewards to the player's inventory, dropping overflow.
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
         * Cleans up the animation task and removes from active maps.
         */
        private void cleanup() {
            cancel();
            activeAnimations.remove(playerId);
            openInventories.remove(playerId);
        }
    }

    /**
     * Starts periodic action bar updates during the animation.
     *
     * @param player The player.
     * @param opening The crate opening.
     */
    private void startActionBarUpdates(Player player, CrateOpening opening) {
        BukkitTask task = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !activeAnimations.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }

                String message = generateActionBarMessage(opening, ticks);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 5L);

        actionBarTasks.put(player.getUniqueId(), task);
    }

    /**
     * Generates the action bar message based on animation progress.
     *
     * @param opening The crate opening.
     * @param ticks The current tick count.
     * @return The formatted message.
     */
    private String generateActionBarMessage(CrateOpening opening, int ticks) {
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
     * Gets the background material based on crate tier.
     *
     * @param crateType The crate type.
     * @return The material.
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
     * Gets the ChatColor based on crate tier.
     *
     * @param tier The tier level.
     * @return The color.
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
     * Cancels any active animation for the given player.
     *
     * @param playerId The player's UUID.
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
     * Cleans up all active animations and tasks.
     */
    public void cleanup() {
        activeAnimations.values().forEach(BukkitTask::cancel);
        actionBarTasks.values().forEach(BukkitTask::cancel);

        activeAnimations.clear();
        openInventories.clear();
        actionBarTasks.clear();
    }

    /**
     * Retrieves statistics about the animation manager.
     *
     * @return A map of statistics.
     */
    public Map<String, Object> getAnimationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAnimations", activeAnimations.size());
        stats.put("displaySlots", DISPLAY_SLOTS);
        stats.put("totalStripItems", TOTAL_ITEMS);
        stats.put("winPosition", WIN_POSITION);
        stats.put("spinDuration", SPIN_DURATION);
        stats.put("version", " v2.0");
        return stats;
    }
}
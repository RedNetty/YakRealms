package com.rednetty.server.core.mechanics.item.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.crates.types.CrateType;
import com.rednetty.server.utils.text.TextUtil;
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
 * Manages CSGO-style crate opening animations with smooth scrolling effects,
 * proper reward display, and complete reward distribution to players.
 */
public class CrateAnimationManager {

    // Core dependencies
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;

    // Active animation tracking
    private final Map<UUID, BukkitTask> activeAnimations = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();

    // Animation constants
    private static final int DISPLAY_SLOTS = 9;
    private static final int TOTAL_ITEMS = 50;
    private static final int WIN_POSITION = 25;
    private static final int INVENTORY_SIZE = 27;
    private static final int CENTER_SLOT = DISPLAY_SLOTS / 2;

    // Timing constants
    private static final double INITIAL_SPEED = 0.8;
    private static final double DECELERATION = 0.985;
    private static final double MIN_SPEED = 0.01;
    private static final int SPIN_DURATION = 200;
    private static final int WARMUP_TICKS = 50;
    private static final long COMPLETION_DELAY = 100L;

    // UI constants
    private static final String INVENTORY_TITLE = "✦ Mystical Crate Opening ✦";
    private static final String SELECTOR_TITLE = "▼ YOUR FEATURED REWARD ▼";
    private static final String SELECTOR_LORE_1 = "This represents your best reward";
    private static final String SELECTOR_LORE_2 = "You will receive ALL generated items!";

    // Message constants
    private static final String MYSTICAL_FOOTER = "✨ Mystical energies have blessed you! ✨";
    private static final String INVENTORY_WARNING = "⚠ %d items were dropped due to full inventory!";

    public CrateAnimationManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
    }

    /**
     * Starts crate opening animation with pre-generated rewards.
     * The featured reward is displayed prominently while all rewards are distributed.
     */
    public void startCrateOpeningWithRewards(CrateOpening opening, List<ItemStack> preGeneratedRewards) {
        if (!validateAnimationParameters(opening, preGeneratedRewards)) {
            return;
        }

        Player player = opening.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            cancelAnimation(playerId);

            Inventory inventory = createAnimationInventory(opening.getCrateType());
            player.openInventory(inventory);
            openInventories.put(playerId, inventory);

            BukkitTask animationTask = new CrateScrollAnimation(opening, inventory, preGeneratedRewards)
                    .runTaskTimer(plugin, 0L, 1L);
            activeAnimations.put(playerId, animationTask);

            startActionBarUpdates(player, opening);
            playAnimationStartSounds(player);

        } catch (Exception e) {
            logger.severe("Error starting crate animation: " + e.getMessage());
            e.printStackTrace();
            crateManager.completeCrateOpening(opening);
        }
    }

    /**
     * Legacy method for backward compatibility.
     * Generates rewards internally and starts animation.
     */
    public void startCrateOpening(CrateOpening opening) {
        if (!validateBasicParameters(opening)) {
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
     * Creates styled animation inventory based on crate type.
     */
    private Inventory createAnimationInventory(CrateType crateType) {
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);

        fillBackgroundSlots(inventory, crateType);
        addSelectorIndicators(inventory, crateType);

        return inventory;
    }

    /**
     * Fills background slots with themed glass panes.
     */
    private void fillBackgroundSlots(Inventory inventory, CrateType crateType) {
        ItemStack background = createBackgroundItem(crateType);

        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i < DISPLAY_SLOTS || i >= INVENTORY_SIZE - DISPLAY_SLOTS) {
                inventory.setItem(i, background);
            }
        }
    }

    /**
     * Adds selector indicators above and below the animation strip.
     */
    private void addSelectorIndicators(Inventory inventory, CrateType crateType) {
        ItemStack selector = createSelectorItem(crateType);
        inventory.setItem(CENTER_SLOT, selector);
        inventory.setItem(INVENTORY_SIZE - DISPLAY_SLOTS + CENTER_SLOT, selector);
    }

    /**
     * Creates background glass pane item.
     */
    private ItemStack createBackgroundItem(CrateType crateType) {
        ItemStack background = new ItemStack(getBackgroundMaterial(crateType));
        ItemMeta meta = background.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            background.setItemMeta(meta);
        }
        return background;
    }

    /**
     * Creates selector indicator item.
     */
    private ItemStack createSelectorItem(CrateType crateType) {
        ItemStack selector = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = selector.getItemMeta();
        if (meta != null) {
            ChatColor tierColor = getTierColor(crateType.getTier());
            meta.setDisplayName(tierColor + SELECTOR_TITLE);
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + SELECTOR_LORE_1,
                    ChatColor.GRAY + SELECTOR_LORE_2
            ));
            selector.setItemMeta(meta);
        }
        return selector;
    }

    /**
     * Main animation class handling CSGO-style scrolling effects.
     */
    private class CrateScrollAnimation extends BukkitRunnable {
        private final CrateOpening opening;
        private final Inventory inventory;
        private final Player player;
        private final UUID playerId;
        private final List<ItemStack> actualRewards;
        private final List<ItemStack> itemStrip;
        private final ItemStack displayReward;

        private double currentPosition = 0.0;
        private double currentSpeed = INITIAL_SPEED;
        private int tick = 0;
        private boolean isCompleted = false;
        private boolean rewardsGiven = false;

        public CrateScrollAnimation(CrateOpening opening, Inventory inventory, List<ItemStack> actualRewards) {
            this.opening = opening;
            this.inventory = inventory;
            this.player = opening.getPlayer();
            this.playerId = player.getUniqueId();
            this.actualRewards = new ArrayList<>(actualRewards);
            this.displayReward = selectFeaturedReward(actualRewards);
            this.itemStrip = generateItemStrip();
        }

        @Override
        public void run() {
            if (!isValidAnimationState()) {
                cleanup();
                return;
            }

            tick++;

            try {
                updateAnimationPosition();

                if (shouldCompleteAnimation()) {
                    finalizeAnimation();
                    return;
                }

                updateItemDisplay();
                playScrollingSounds();

            } catch (Exception e) {
                logger.warning("Animation error: " + e.getMessage());
                cleanup();
            }
        }

        /**
         * Validates animation state.
         */
        private boolean isValidAnimationState() {
            return player != null && player.isOnline() && !isCompleted;
        }

        /**
         * Updates animation position and speed.
         */
        private void updateAnimationPosition() {
            currentPosition += currentSpeed;

            if (tick > WARMUP_TICKS) {
                currentSpeed *= DECELERATION;
            }
        }

        /**
         * Checks if animation should complete.
         */
        private boolean shouldCompleteAnimation() {
            return currentSpeed < MIN_SPEED || tick > SPIN_DURATION;
        }

        /**
         * Finalizes animation at winning position.
         */
        private void finalizeAnimation() {
            currentPosition = WIN_POSITION - CENTER_SLOT;
            finishAnimation();
        }

        /**
         * Selects the most valuable reward for display.
         */
        private ItemStack selectFeaturedReward(List<ItemStack> rewards) {
            return rewards.stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingInt(this::getRewardDisplayPriority))
                    .map(ItemStack::clone)
                    .orElse(rewards.get(0).clone());
        }

        /**
         * Calculates reward display priority for featured selection.
         */
        private int getRewardDisplayPriority(ItemStack reward) {
            if (!reward.hasItemMeta()) return 0;

            String displayName = reward.getItemMeta().getDisplayName();
            if (displayName == null) return 0;

            // Equipment items have highest priority
            if (isEquipmentItem(displayName)) {
                return getEquipmentPriority(displayName);
            }

            // Special items
            if (displayName.contains("Orb")) return getOrbPriority(displayName);
            if (displayName.contains("Scroll") || displayName.contains("SCROLL")) return getScrollPriority(displayName);
            if (displayName.contains("Halloween") || displayName.contains("🎃")) return 85;
            if (displayName.contains("Gem") || displayName.contains("Bank Note")) return 40;

            return 20;
        }

        private boolean isEquipmentItem(String displayName) {
            String[] equipmentTypes = {"Sword", "Staff", "Spear", "Axe", "Helmet",
                    "Chestplate", "Leggings", "Boots", "Shield", "Bow"};
            return Arrays.stream(equipmentTypes).anyMatch(displayName::contains);
        }

        private int getEquipmentPriority(String displayName) {
            if (displayName.contains("Legendary") || displayName.contains("Unique")) return 120;
            if (displayName.contains("Rare")) return 110;
            return 100;
        }

        private int getOrbPriority(String displayName) {
            return displayName.contains("Legendary") ? 90 : 80;
        }

        private int getScrollPriority(String displayName) {
            return displayName.contains("Protection") ? 70 : 60;
        }

        /**
         * Generates the scrolling item strip for animation.
         */
        private List<ItemStack> generateItemStrip() {
            List<ItemStack> strip = new ArrayList<>(TOTAL_ITEMS);

            for (int i = 0; i < TOTAL_ITEMS; i++) {
                if (i == WIN_POSITION) {
                    strip.add(displayReward.clone());
                } else {
                    strip.add(createFillerItem());
                }
            }

            return strip;
        }

        /**
         * Creates filler items for the animation strip.
         */
        private ItemStack createFillerItem() {
            try {
                // Occasionally use actual rewards for variety
                if (ThreadLocalRandom.current().nextInt(100) < 20 && actualRewards.size() > 1) {
                    ItemStack randomReward = actualRewards.get(ThreadLocalRandom.current().nextInt(actualRewards.size()));
                    if (randomReward != null && !isSameItem(randomReward, displayReward)) {
                        return randomReward.clone();
                    }
                }

                // Generate random filler
                int tier = opening.getCrateType().getTier();
                int itemType = ThreadLocalRandom.current().nextInt(8) + 1;
                int rarity = ThreadLocalRandom.current().nextInt(4) + 1;

                return crateManager.getRewardsManager().createDropWithRarity(tier, itemType, rarity);
            } catch (Exception e) {
                return createFallbackItem();
            }
        }

        /**
         * Creates fallback item when generation fails.
         */
        private ItemStack createFallbackItem() {
            Material[] materials = {
                    Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.GOLDEN_SWORD,
                    Material.IRON_AXE, Material.DIAMOND_AXE, Material.GOLDEN_AXE,
                    Material.EMERALD, Material.DIAMOND, Material.GOLD_INGOT
            };

            Material material = materials[ThreadLocalRandom.current().nextInt(materials.length)];
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                ChatColor color = getTierColor(opening.getCrateType().getTier());
                meta.setDisplayName(color + "Filler Item");
                item.setItemMeta(meta);
            }

            return item;
        }

        /**
         * Updates the displayed items in the animation slots.
         */
        private void updateItemDisplay() {
            int startIndex = (int) Math.floor(currentPosition);

            for (int slot = 0; slot < DISPLAY_SLOTS; slot++) {
                int itemIndex = normalizeIndex(startIndex + slot);
                ItemStack item = itemStrip.get(itemIndex);
                boolean isCenter = (slot == CENTER_SLOT);

                ItemStack displayItem = enhanceItemForDisplay(item, isCenter);
                inventory.setItem(DISPLAY_SLOTS + slot, displayItem);
            }
        }

        /**
         * Normalizes array index to prevent out-of-bounds.
         */
        private int normalizeIndex(int index) {
            int normalized = index % itemStrip.size();
            return normalized < 0 ? normalized + itemStrip.size() : normalized;
        }

        /**
         * Enhances item appearance for display.
         */
        private ItemStack enhanceItemForDisplay(ItemStack item, boolean isCenter) {
            if (item == null) return item;

            ItemStack enhanced = item.clone();
            if (enhanced.hasItemMeta()) {
                ItemMeta meta = enhanced.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                lore.add("");
                if (isCenter) {
                    lore.add(ChatColor.YELLOW + "◆ FEATURED REWARD ◆");
                    lore.add(ChatColor.GRAY + "This is your best reward!");
                } else {
                    lore.add(ChatColor.DARK_GRAY + "Preview Item");
                }

                meta.setLore(lore);
                enhanced.setItemMeta(meta);
            }

            return enhanced;
        }

        /**
         * Plays scrolling sound effects.
         */
        private void playScrollingSounds() {
            if (tick % Math.max(1, (int)(10 / currentSpeed)) == 0) {
                float pitch = Math.min(2.0f, 0.5f + (float)currentSpeed);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, pitch);
            }
        }

        /**
         * Completes the animation and distributes rewards.
         */
        private void finishAnimation() {
            if (isCompleted) return;
            isCompleted = true;

            updateItemDisplay();
            playCompletionSounds();

            if (!rewardsGiven) {
                distributeRewards();
                rewardsGiven = true;
            }

            ItemStack selectedItem = getCurrentCenterItem();
            sendCompletionMessages(selectedItem);

            scheduleCleanup();
        }

        /**
         * Gets the item currently in the center position.
         */
        private ItemStack getCurrentCenterItem() {
            int startIndex = (int) Math.floor(currentPosition);
            int centerItemIndex = normalizeIndex(startIndex + CENTER_SLOT);
            return itemStrip.get(centerItemIndex);
        }

        /**
         * Plays completion sound effects.
         */
        private void playCompletionSounds() {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }

        /**
         * Distributes all rewards to player inventory.
         */
        private void distributeRewards() {
            int overflowCount = 0;

            for (ItemStack reward : actualRewards) {
                if (reward != null) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
                    if (!leftover.isEmpty()) {
                        for (ItemStack overflowItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
                            overflowCount++;
                        }
                    }
                }
            }

            if (overflowCount > 0) {
                player.sendMessage(ChatColor.YELLOW + String.format(INVENTORY_WARNING, overflowCount));
            }
        }

        /**
         * Sends formatted completion messages to player.
         */
        private void sendCompletionMessages(ItemStack selectedItem) {
            sendEmptyLine();
            sendEmptyLine();

            sendSelectedItemInfo(selectedItem);
            sendRewardsList(selectedItem);

            sendEmptyLine();
            sendCenteredMessage(ChatColor.AQUA + MYSTICAL_FOOTER);
            sendEmptyLine();
        }

        /**
         * Sends information about the selected item.
         */
        private void sendSelectedItemInfo(ItemStack selectedItem) {
            String selectedItemName = getItemDisplayName(selectedItem);
            player.sendMessage(ChatColor.YELLOW + "🎯 " + ChatColor.BOLD + "SELECTED IN ANIMATION:" + ChatColor.RESET + " " + selectedItemName);
            player.sendMessage(ChatColor.GRAY + "  This item was highlighted in the center slot");
            sendEmptyLine();
        }

        /**
         * Sends list of all received rewards.
         */
        private void sendRewardsList(ItemStack selectedItem) {
            player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.BOLD + "ALL REWARDS RECEIVED:");

            for (ItemStack reward : actualRewards) {
                String rewardName = getItemDisplayName(reward);
                boolean isSelected = isSameItem(reward, selectedItem);

                String prefix = isSelected ? ChatColor.YELLOW + "🎯 " : ChatColor.WHITE + "  • ";
                String suffix = reward.getAmount() > 1 ? ChatColor.GRAY + " x" + reward.getAmount() : "";

                player.sendMessage(prefix + rewardName + suffix);

                if (isSelected) {
                    player.sendMessage(ChatColor.GRAY + "     ↑ This was the selected item!");
                }
            }
        }

        /**
         * Sends centered message using TextUtil.
         */
        private void sendCenteredMessage(String message) {
            TextUtil.sendCenteredMessage(player, message);
        }

        /**
         * Sends empty line for message spacing.
         */
        private void sendEmptyLine() {
            player.sendMessage("");
        }

        /**
         * Checks if two items are equivalent.
         */
        private boolean isSameItem(ItemStack item1, ItemStack item2) {
            if (item1 == null || item2 == null) return false;
            if (item1.getType() != item2.getType()) return false;

            String name1 = getItemDisplayName(item1);
            String name2 = getItemDisplayName(item2);
            return name1.equals(name2);
        }

        /**
         * Gets display name of an item.
         */
        private String getItemDisplayName(ItemStack item) {
            if (item == null) return "Unknown Item";
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                return item.getItemMeta().getDisplayName();
            }
            return TextUtil.formatItemName(item.getType().name());
        }

        /**
         * Schedules cleanup after animation completion.
         */
        private void scheduleCleanup() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    crateManager.completeCrateOpeningWithRewards(opening, actualRewards);
                    player.closeInventory();
                    cleanup();
                }
            }.runTaskLater(plugin, COMPLETION_DELAY);
        }

        /**
         * Cleans up animation resources.
         */
        private void cleanup() {
            cancel();
            activeAnimations.remove(playerId);
            openInventories.remove(playerId);
        }
    }

    /**
     * Starts periodic action bar updates during animation.
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
     * Generates animated action bar message.
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
     * Plays animation start sound effects.
     */
    private void playAnimationStartSounds(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.5f);
    }

    /**
     * Validates animation parameters.
     */
    private boolean validateAnimationParameters(CrateOpening opening, List<ItemStack> preGeneratedRewards) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid crate opening provided to animation");
            return false;
        }

        if (preGeneratedRewards == null || preGeneratedRewards.isEmpty()) {
            logger.warning("Invalid rewards provided to animation");
            return false;
        }

        return true;
    }

    /**
     * Validates basic opening parameters.
     */
    private boolean validateBasicParameters(CrateOpening opening) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid crate opening provided");
            return false;
        }
        return true;
    }

    /**
     * Gets background material based on crate tier.
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
     * Gets ChatColor based on crate tier.
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
     * Cancels active animation for player.
     */
    public void cancelAnimation(UUID playerId) {
        BukkitTask animationTask = activeAnimations.remove(playerId);
        if (animationTask != null) {
            animationTask.cancel();
        }

        BukkitTask actionBarTask = actionBarTasks.remove(playerId);
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }

        openInventories.remove(playerId);

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /**
     * Cleans up all active animations and resources.
     */
    public void cleanup() {
        activeAnimations.values().forEach(BukkitTask::cancel);
        actionBarTasks.values().forEach(BukkitTask::cancel);

        activeAnimations.clear();
        openInventories.clear();
        actionBarTasks.clear();
    }

    /**
     * Gets animation manager statistics.
     */
    public Map<String, Object> getAnimationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAnimations", activeAnimations.size());
        stats.put("displaySlots", DISPLAY_SLOTS);
        stats.put("totalStripItems", TOTAL_ITEMS);
        stats.put("winPosition", WIN_POSITION);
        stats.put("spinDuration", SPIN_DURATION);
        stats.put("version", "v2.1");
        return stats;
    }
}
package com.rednetty.server.mechanics.crates;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.crates.CrateManager;
import com.rednetty.server.mechanics.crates.CrateOpening;
import com.rednetty.server.mechanics.crates.types.CrateType;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.utils.text.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Enhanced animation manager for crate opening sequences
 */
public class CrateAnimationManager {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;

    // Active animations
    private final Map<UUID, BukkitTask> activeAnimations = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();

    // Animation constants
    private static final int INVENTORY_SIZE = 27;
    private static final int RESULT_SLOT = 13; // Center slot
    private static final String INVENTORY_TITLE = "✦ Crate Opening ✦";

    /**
     * Constructor
     */
    public CrateAnimationManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
    }

    /**
     * Starts a crate opening animation
     *
     * @param opening The crate opening session
     */
    public void startCrateOpening(CrateOpening opening) {
        if (opening == null || opening.getPlayer() == null) {
            logger.warning("Invalid crate opening provided to animation manager");
            return;
        }

        Player player = opening.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            // Cancel any existing animation for this player
            cancelAnimation(playerId);

            // Create and open the animation inventory
            Inventory inventory = createAnimationInventory(opening);
            player.openInventory(inventory);
            openInventories.put(playerId, inventory);

            // Start the animation sequence
            BukkitTask animationTask = new CrateAnimationSequence(opening, inventory).runTaskTimer(plugin, 0L, 2L);
            activeAnimations.put(playerId, animationTask);

            // Play opening effects
            playOpeningEffects(player, opening.getCrateType());

            logger.fine("Started crate animation for player: " + player.getName());

        } catch (Exception e) {
            logger.severe("Error starting crate animation for player " + player.getName() + ": " + e.getMessage());
            // Fallback: complete immediately
            crateManager.completeCrateOpening(opening);
        }
    }

    /**
     * Cancels an active animation for a player
     *
     * @param playerId The player's UUID
     */
    public void cancelAnimation(UUID playerId) {
        BukkitTask task = activeAnimations.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        openInventories.remove(playerId);
    }

    /**
     * Creates the animation inventory
     *
     * @param opening The crate opening session
     * @return The created inventory
     */
    private Inventory createAnimationInventory(CrateOpening opening) {
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);

        // Fill with decorative glass panes
        ItemStack glassPane = createAnimationGlassPane(opening.getCrateType());
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i != RESULT_SLOT) {
                inventory.setItem(i, glassPane);
            }
        }

        return inventory;
    }

    /**
     * Creates decorative glass panes for the animation
     *
     * @param crateType The crate type
     * @return The glass pane item
     */
    private ItemStack createAnimationGlassPane(CrateType crateType) {
        // Choose glass color based on crate tier
        Material glassType = switch (crateType.getTier()) {
            case 1 -> Material.WHITE_STAINED_GLASS_PANE;
            case 2 -> Material.LIME_STAINED_GLASS_PANE;
            case 3 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case 4 -> Material.MAGENTA_STAINED_GLASS_PANE;
            case 5 -> Material.YELLOW_STAINED_GLASS_PANE;
            case 6 -> Material.BLUE_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };

        if (crateType.isHalloween()) {
            glassType = ThreadLocalRandom.current().nextBoolean() ?
                    Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack pane = new ItemStack(glassType);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Plays opening effects for the crate
     *
     * @param player    The player
     * @param crateType The crate type
     */
    private void playOpeningEffects(Player player, CrateType crateType) {
        Location location = player.getLocation();

        // Sound effect based on tier
        Sound openSound = switch (crateType.getTier()) {
            case 1, 2 -> Sound.BLOCK_CHEST_OPEN;
            case 3, 4 -> Sound.BLOCK_ENDER_CHEST_OPEN;
            case 5, 6 -> Sound.BLOCK_BEACON_ACTIVATE;
            default -> Sound.BLOCK_CHEST_OPEN;
        };

        player.playSound(location, openSound, 1.0f, 1.0f);

        // Particle effects for Halloween crates
        if (crateType.isHalloween()) {
            location.getWorld().spawnParticle(Particle.FLAME, location.add(0, 2, 0), 20, 1, 1, 1, 0.1);
        }

        // Higher tier effects
        if (crateType.getTier() >= 4) {
            location.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, location.add(0, 2, 0), 30, 1, 1, 1, 0.1);
        }
    }

    /**
     * Inner class for handling the animation sequence
     */
    private class CrateAnimationSequence extends BukkitRunnable {
        private final CrateOpening opening;
        private final Inventory inventory;
        private final Player player;
        private int ticks = 0;
        private int phase = 0; // 0: spinning, 1: slowing, 2: revealing, 3: complete

        // Phase timings (in ticks)
        private final int SPINNING_PHASE = 60;   // 3 seconds
        private final int SLOWING_PHASE = 100;   // 5 seconds
        private final int REVEALING_PHASE = 140; // 7 seconds

        public CrateAnimationSequence(CrateOpening opening, Inventory inventory) {
            this.opening = opening;
            this.inventory = inventory;
            this.player = opening.getPlayer();
        }

        @Override
        public void run() {
            if (player == null || !player.isOnline()) {
                cleanup();
                return;
            }

            ticks++;
            opening.incrementAnimationTicks();

            try {
                switch (phase) {
                    case 0: // Spinning phase
                        handleSpinningPhase();
                        if (ticks >= SPINNING_PHASE) {
                            phase = 1;
                            opening.advanceToPhase(CrateOpening.OpeningPhase.SLOWING);
                        }
                        break;

                    case 1: // Slowing phase
                        handleSlowingPhase();
                        if (ticks >= SLOWING_PHASE) {
                            phase = 2;
                            opening.advanceToPhase(CrateOpening.OpeningPhase.REVEALING);
                        }
                        break;

                    case 2: // Revealing phase
                        handleRevealingPhase();
                        if (ticks >= REVEALING_PHASE) {
                            phase = 3;
                            opening.advanceToPhase(CrateOpening.OpeningPhase.COMPLETED);
                        }
                        break;

                    case 3: // Completion
                        handleCompletion();
                        cleanup();
                        return;
                }

            } catch (Exception e) {
                logger.warning("Error in crate animation for player " + player.getName() + ": " + e.getMessage());
                cleanup();
            }
        }

        /**
         * Handles the spinning phase animation
         */
        private void handleSpinningPhase() {
            // Rapid item cycling
            if (ticks % 3 == 0) {
                ItemStack randomItem = createRandomDisplayItem();
                inventory.setItem(RESULT_SLOT, randomItem);

                // Fast spinning sound
                if (ticks % 12 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
                }
            }

            // Animate glass panes
            animateGlassPanes(4);
        }

        /**
         * Handles the slowing phase animation
         */
        private void handleSlowingPhase() {
            // Slower item cycling
            int interval = Math.max(5, (ticks - SPINNING_PHASE) / 2);
            if (ticks % interval == 0) {
                ItemStack randomItem = createRandomDisplayItem();
                inventory.setItem(RESULT_SLOT, randomItem);

                // Slowing sound
                if (ticks % 20 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
                }
            }

            // Slower glass pane animation
            animateGlassPanes(8);
        }

        /**
         * Handles the revealing phase animation
         */
        private void handleRevealingPhase() {
            // Very slow final reveals
            if (ticks == SLOWING_PHASE + 10) {
                // Generate the actual reward here and show it briefly
                ItemStack actualReward = createActualReward();
                inventory.setItem(RESULT_SLOT, actualReward);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }

            // Build suspense with slower animations
            animateGlassPanes(15);

            // Particle effects building up
            if (ticks % 10 == 0 && opening.getCrateType().getTier() >= 3) {
                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                        player.getLocation().add(0, 2, 0), 5, 0.5, 0.5, 0.5, 0.1);
            }
        }

        /**
         * Handles the completion of the animation
         */
        private void handleCompletion() {
            // Final effects
            playCrateOpenEffects();

            // Close inventory after brief delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
                // Complete the crate opening
                crateManager.completeCrateOpening(opening);
            }, 20L); // 1 second delay
        }

        /**
         * Animates the glass panes around the center
         */
        private void animateGlassPanes(int interval) {
            if (ticks % interval == 0) {
                // Cycle through different colored glass panes
                ItemStack newPane = createAnimationGlassPane(opening.getCrateType());

                // Update border panes in a wave pattern
                int[] borderSlots = {0, 1, 2, 9, 11, 18, 19, 20, 6, 7, 8, 15, 17, 24, 25, 26};
                int offset = (ticks / interval) % borderSlots.length;

                for (int i = 0; i < borderSlots.length; i++) {
                    int slot = borderSlots[(i + offset) % borderSlots.length];
                    if (slot != RESULT_SLOT) {
                        inventory.setItem(slot, newPane);
                    }
                }
            }
        }

        /**
         * Creates a random display item for animation
         */
        private ItemStack createRandomDisplayItem() {
            Material[] materials = {
                    Material.DIAMOND_SWORD, Material.DIAMOND_HELMET, Material.EMERALD,
                    Material.GOLDEN_APPLE, Material.ENCHANTED_BOOK, Material.ENDER_PEARL,
                    Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT
            };

            Material randomMaterial = materials[ThreadLocalRandom.current().nextInt(materials.length)];
            ItemStack item = new ItemStack(randomMaterial);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "???");
                item.setItemMeta(meta);
            }

            return item;
        }

        /**
         * Creates the actual reward item (placeholder for now)
         */
        private ItemStack createActualReward() {
            // This would integrate with the rewards manager
            ItemStack reward = new ItemStack(Material.DIAMOND);
            ItemMeta meta = reward.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Crate Reward!");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Your reward will be given",
                        ChatColor.GRAY + "when the animation completes!"
                ));
                reward.setItemMeta(meta);
            }
            return reward;
        }

        /**
         * Plays final crate opening effects
         */
        private void playCrateOpenEffects() {
            Location location = player.getLocation();
            CrateType crateType = opening.getCrateType();

            // Tier-based effects
            switch (crateType.getTier()) {
                case 1, 2:
                    player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    break;
                case 3, 4:
                    player.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    location.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, location.add(0, 2, 0),
                            20, 1, 1, 1, 0.1);
                    break;
                case 5, 6:
                    player.playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2.0f);
                    location.getWorld().spawnParticle(Particle.DRAGON_BREATH, location.add(0, 2, 0),
                            30, 1, 1, 1, 0.1);
                    break;
            }

            // Halloween special effects
            if (crateType.isHalloween()) {
                player.playSound(location, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.8f);
                location.getWorld().spawnParticle(Particle.FLAME, location.add(0, 2, 0),
                        40, 1, 2, 1, 0.1);
            }
        }

        /**
         * Cleans up the animation
         */
        private void cleanup() {
            cancel();
            UUID playerId = player.getUniqueId();
            activeAnimations.remove(playerId);
            openInventories.remove(playerId);
        }
    }

    /**
     * Cleans up all active animations
     */
    public void cleanup() {
        for (BukkitTask task : activeAnimations.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        activeAnimations.clear();
        openInventories.clear();
    }

    /**
     * Gets animation statistics for debugging
     */
    public Map<String, Object> getAnimationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAnimations", activeAnimations.size());
        stats.put("openInventories", openInventories.size());
        stats.put("inventorySize", INVENTORY_SIZE);
        stats.put("resultSlot", RESULT_SLOT);
        return stats;
    }
}

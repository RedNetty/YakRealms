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
 * The animation manager for crate opening sequences
 */
public class CrateAnimationManager {
    private final YakRealms plugin;
    private final Logger logger;
    private final CrateManager crateManager;

    // Active animations
    private final Map<UUID, BukkitTask> activeAnimations = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private final Map<UUID, BukkitTask> actionBarTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> soundTasks = new HashMap<>();

    // Animation constants - Enhanced for 1.20.4
    private static final int INVENTORY_SIZE = 27;
    private static final int RESULT_SLOT = 13; // Center slot
    private static final String INVENTORY_TITLE = "✦ Mystical Crate Opening ✦";

    // Visual effect constants
    private static final double PARTICLE_RADIUS = 2.0;
    private static final int PARTICLE_COUNT = 15;
    private static final double SOUND_PITCH_VARIATION = 0.2;

    /**
     * Enhanced crate opening animation phases with SHORTENED timing
     */
    public enum AnimationPhase {
        PREPARATION(10),   // 0.5 seconds - Build anticipation (was 20)
        SPINNING(60),      // 3 seconds - Fast spinning (was 120)
        SLOWING(40),       // 2 seconds - Gradual slowdown (was 80)
        REVEALING(30),     // 1.5 seconds - Final reveal (was 60)
        CELEBRATION(20);   // 1 second - Victory effects (was 40)

        // Total: 160 ticks = 8 seconds (was 320 ticks = 16 seconds)

        private final int duration;

        AnimationPhase(int duration) {
            this.duration = duration;
        }

        public int getDuration() {
            return duration;
        }
    }

    /**
     * Constructor
     */
    public CrateAnimationManager() {
        this.plugin = YakRealms.getInstance();
        this.logger = plugin.getLogger();
        this.crateManager = CrateManager.getInstance();
    }

    /**
     * Starts an enhanced crate opening animation with modern 1.20.4 features
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

            // Create and open the enhanced animation inventory
            Inventory inventory = createEnhancedAnimationInventory(opening);
            player.openInventory(inventory);
            openInventories.put(playerId, inventory);

            // Start the enhanced animation sequence
            BukkitTask animationTask = new EnhancedCrateAnimationSequence(opening, inventory)
                    .runTaskTimer(plugin, 0L, 1L); // Every tick for smoother animation
            activeAnimations.put(playerId, animationTask);

            // Start action bar updates
            startActionBarUpdates(player, opening);

            // Start ambient sound effects
            startAmbientSounds(player, opening);

            // Play enhanced opening effects
            playEnhancedOpeningEffects(player, opening.getCrateType());

            logger.fine("Started enhanced crate animation for player: " + player.getName());

        } catch (Exception e) {
            logger.severe("Error starting crate animation for player " + player.getName() + ": " + e.getMessage());
            // Fallback: complete immediately
            crateManager.completeCrateOpening(opening);
        }
    }

    /**
     * Creates an enhanced animation inventory with modern visual elements
     */
    private Inventory createEnhancedAnimationInventory(CrateOpening opening) {
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);

        // Create tier-appropriate decorative elements
        ItemStack borderPane = createEnhancedBorderPane(opening.getCrateType());
        ItemStack accentPane = createAccentPane(opening.getCrateType());

        // Enhanced border pattern with accent colors
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (isCornerSlot(i)) {
                inventory.setItem(i, accentPane);
            } else if (isBorderSlot(i) && i != RESULT_SLOT) {
                inventory.setItem(i, borderPane);
            } else if (i != RESULT_SLOT) {
                inventory.setItem(i, createEmptyPane());
            }
        }

        // Add mystical preparation item in center
        inventory.setItem(RESULT_SLOT, createPreparationItem(opening.getCrateType()));

        return inventory;
    }

    /**
     * Creates enhanced border panes with tier-appropriate materials and effects
     */
    private ItemStack createEnhancedBorderPane(CrateType crateType) {
        Material glassType = switch (crateType.getTier()) {
            case 1 -> Material.WHITE_STAINED_GLASS_PANE;
            case 2 -> Material.LIME_STAINED_GLASS_PANE;
            case 3 -> Material.CYAN_STAINED_GLASS_PANE;
            case 4 -> Material.PURPLE_STAINED_GLASS_PANE;
            case 5 -> Material.YELLOW_STAINED_GLASS_PANE;
            case 6 -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };

        // Halloween special theming
        if (crateType.isHalloween()) {
            glassType = ThreadLocalRandom.current().nextBoolean() ?
                    Material.ORANGE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack pane = new ItemStack(glassType);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_GRAY + "◈ Mystical Barrier ◈");
            List<String> lore = Arrays.asList(
                    ChatColor.GRAY + "Ancient magic prevents",
                    ChatColor.GRAY + "interference with the ritual..."
            );
            meta.setLore(lore);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Creates accent panes for corners with special visual appeal
     */
    private ItemStack createAccentPane(CrateType crateType) {
        Material accentType = switch (crateType.getTier()) {
            case 1, 2 -> Material.IRON_BARS;
            case 3, 4 -> Material.GOLD_BLOCK;
            case 5, 6 -> Material.DIAMOND_BLOCK;
            default -> Material.STONE;
        };

        ItemStack accent = new ItemStack(accentType);
        ItemMeta meta = accent.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "✧ Tier " + crateType.getTier() + " Energy ✧");
            accent.setItemMeta(meta);
        }
        return accent;
    }

    /**
     * Creates an empty decorative pane
     */
    private ItemStack createEmptyPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Creates a preparation item for the center slot
     */
    private ItemStack createPreparationItem(CrateType crateType) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "✦ Preparing Mystical Reveal ✦");
            List<String> lore = Arrays.asList(
                    "",
                    ChatColor.GRAY + "The ancient magic is gathering...",
                    ChatColor.GRAY + "Your " + ChatColor.YELLOW + crateType.getDisplayName() +
                            ChatColor.GRAY + " crate",
                    ChatColor.GRAY + "is being unsealed by cosmic forces.",
                    "",
                    ChatColor.GOLD + "Patience brings great rewards..."
            );
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Starts action bar updates for the player during animation
     */
    private void startActionBarUpdates(Player player, CrateOpening opening) {
        UUID playerId = player.getUniqueId();

        BukkitTask actionBarTask = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !activeAnimations.containsKey(playerId)) {
                    cancel();
                    return;
                }

                String message = generateActionBarMessage(opening, ticks);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L); // Update every 4 ticks for smooth text

        actionBarTasks.put(playerId, actionBarTask);
    }

    /**
     * Generates dynamic action bar messages based on animation phase
     */
    private String generateActionBarMessage(CrateOpening opening, int ticks) {
        CrateType crateType = opening.getCrateType();
        AnimationPhase phase = getCurrentPhase(opening);

        String tierColor = getTierColorCode(crateType.getTier());
        String animation = getAnimationDots(ticks);

        return switch (phase) {
            case PREPARATION -> tierColor + "⚡ Gathering mystical energy " + animation;
            case SPINNING -> tierColor + "🌀 Unsealing " + crateType.getDisplayName() + " crate " + animation;
            case SLOWING -> tierColor + "✨ Mystical energy exceeding limits.. " + animation;
            case REVEALING -> tierColor + "🎁 Revealing your winnings " + animation;
            case CELEBRATION -> ChatColor.GOLD + "🎉 " + ChatColor.BOLD + "CONGRATULATIONS!" + ChatColor.GOLD + " 🎉";
        };
    }

    /**
     * Gets color code for tier
     */
    private String getTierColorCode(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE.toString();
            case 2 -> ChatColor.GREEN.toString();
            case 3 -> ChatColor.AQUA.toString();
            case 4 -> ChatColor.LIGHT_PURPLE.toString();
            case 5 -> ChatColor.YELLOW.toString();
            case 6 -> ChatColor.BLUE.toString();
            default -> ChatColor.GRAY.toString();
        };
    }

    /**
     * Creates animated dots for action bar
     */
    private String getAnimationDots(int ticks) {
        int cycle = (ticks / 5) % 4; // Change every 5 ticks, 4-dot cycle
        return switch (cycle) {
            case 0 -> "⬥";
            case 1 -> "⬥⬥";
            case 2 -> "⬥⬥⬥";
            case 3 -> "⬥⬥⬥⬥";
            default -> "";
        };
    }

    /**
     * Gets current animation phase based on opening progress
     */
    private AnimationPhase getCurrentPhase(CrateOpening opening) {
        int totalTicks = opening.getAnimationTicks();
        int accumulator = 0;

        for (AnimationPhase phase : AnimationPhase.values()) {
            accumulator += phase.getDuration();
            if (totalTicks < accumulator) {
                return phase;
            }
        }

        return AnimationPhase.CELEBRATION;
    }

    /**
     * Starts ambient sound effects during animation
     */
    private void startAmbientSounds(Player player, CrateOpening opening) {
        UUID playerId = player.getUniqueId();

        BukkitTask soundTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeAnimations.containsKey(playerId)) {
                    cancel();
                    return;
                }

                playAmbientSound(player, opening);
            }
        }.runTaskTimer(plugin, 20L, 20L); // Every second

        soundTasks.put(playerId, soundTask);
    }

    /**
     * Plays ambient sounds based on current animation phase
     */
    private void playAmbientSound(Player player, CrateOpening opening) {
        AnimationPhase phase = getCurrentPhase(opening);
        Location loc = player.getLocation();

        switch (phase) {
            case PREPARATION:
                player.playSound(loc, Sound.BLOCK_BEACON_AMBIENT, 0.3f, 1.8f);
                break;
            case SPINNING:
                player.playSound(loc, Sound.BLOCK_CONDUIT_AMBIENT, 0.5f, 1.5f +
                        (float)(Math.random() * SOUND_PITCH_VARIATION));
                break;
            case SLOWING:
                player.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.2f);
                break;
            case REVEALING:
                player.playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.6f, 1.0f);
                break;
        }
    }

    /**
     * Plays enhanced opening effects using 1.20.4 particle system
     */
    private void playEnhancedOpeningEffects(Player player, CrateType crateType) {
        Location location = player.getLocation().add(0, 1, 0);
        World world = location.getWorld();

        if (world == null) return;

        // Tier-based sound effects
        Sound openSound = switch (crateType.getTier()) {
            case 1, 2 -> Sound.BLOCK_CHEST_OPEN;
            case 3, 4 -> Sound.BLOCK_ENDER_CHEST_OPEN;
            case 5, 6 -> Sound.ENTITY_WITHER_SPAWN;
            default -> Sound.BLOCK_CHEST_OPEN;
        };

        player.playSound(location, openSound, 1.0f, 1.0f);

        // Enhanced particle effects using 1.20.4 features
        spawnTierParticles(world, location, crateType);

        // Halloween special effects
        if (crateType.isHalloween()) {
            spawnHalloweenEffects(world, location);
        }

        // Create particle spiral effect
        createParticleSpiral(world, location, crateType);
    }

    /**
     * Spawns tier-appropriate particle effects
     */
    private void spawnTierParticles(World world, Location center, CrateType crateType) {
        Particle particle = switch (crateType.getTier()) {
            case 1 -> Particle.VILLAGER_HAPPY;
            case 2 -> Particle.ENCHANTMENT_TABLE;
            case 3 -> Particle.PORTAL;
            case 4 -> Particle.END_ROD;
            case 5 -> Particle.DRAGON_BREATH;
            case 6 -> Particle.TOTEM;
            default -> Particle.VILLAGER_HAPPY;
        };

        // Create expanding particle ring
        for (int i = 0; i < 360; i += 15) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * PARTICLE_RADIUS;
            double z = Math.sin(angle) * PARTICLE_RADIUS;

            Location particleLoc = center.clone().add(x, 0, z);
            world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Spawns Halloween-specific effects
     */
    private void spawnHalloweenEffects(World world, Location center) {
        // Orange flames
        world.spawnParticle(Particle.FLAME, center, 20, 1, 1, 1, 0.1);

        // Smoke for spooky atmosphere
        world.spawnParticle(Particle.SMOKE_LARGE, center.clone().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.02);

        // Soul flames for higher tiers
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 15, 0.8, 0.8, 0.8, 0.05);
    }

    /**
     * Creates a magical particle spiral effect
     */
    private void createParticleSpiral(World world, Location center, CrateType crateType) {
        new BukkitRunnable() {
            double y = 0;
            double radius = 0.5;
            int count = 0;

            @Override
            public void run() {
                if (count > 30) { // Reduced from 60 (1.5 seconds instead of 3)
                    cancel();
                    return;
                }

                for (int i = 0; i < 4; i++) {
                    double angle = (count * 0.2) + (i * Math.PI / 2);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location spiralLoc = center.clone().add(x, y, z);

                    Particle spiralParticle = crateType.getTier() >= 4 ?
                            Particle.END_ROD : Particle.ENCHANTMENT_TABLE;

                    world.spawnParticle(spiralParticle, spiralLoc, 1, 0, 0, 0, 0);
                }

                y += 0.05;
                radius += 0.01;
                count++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Cancels an active animation for a player
     */
    public void cancelAnimation(UUID playerId) {
        // Cancel main animation
        BukkitTask task = activeAnimations.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        // Cancel action bar updates
        BukkitTask actionBarTask = actionBarTasks.remove(playerId);
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }

        // Cancel sound task
        BukkitTask soundTask = soundTasks.remove(playerId);
        if (soundTask != null) {
            soundTask.cancel();
        }

        // Clear inventory reference
        openInventories.remove(playerId);

        // Clear action bar
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
        }
    }

    /**
     * Helper methods for inventory layout
     */
    private boolean isCornerSlot(int slot) {
        return slot == 0 || slot == 8 || slot == 18 || slot == 26;
    }

    private boolean isBorderSlot(int slot) {
        return slot < 9 || slot > 17 || slot % 9 == 0 || slot % 9 == 8;
    }

    /**
     * Enhanced inner class for handling the animation sequence with SHORTENED timing
     */
    private class EnhancedCrateAnimationSequence extends BukkitRunnable {
        private final CrateOpening opening;
        private final Inventory inventory;
        private final Player player;
        private int ticks = 0;
        private AnimationPhase currentPhase = AnimationPhase.PREPARATION;
        private int phaseProgress = 0;

        public EnhancedCrateAnimationSequence(CrateOpening opening, Inventory inventory) {
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
            phaseProgress++;

            try {
                // Check if we need to advance to next phase
                if (phaseProgress >= currentPhase.getDuration()) {
                    advancePhase();
                }

                // Handle current phase
                switch (currentPhase) {
                    case PREPARATION -> handlePreparationPhase();
                    case SPINNING -> handleSpinningPhase();
                    case SLOWING -> handleSlowingPhase();
                    case REVEALING -> handleRevealingPhase();
                    case CELEBRATION -> handleCelebrationPhase();
                }

            } catch (Exception e) {
                logger.warning("Error in enhanced crate animation for player " +
                        player.getName() + ": " + e.getMessage());
                cleanup();
            }
        }

        /**
         * Advances to the next animation phase
         */
        private void advancePhase() {
            AnimationPhase[] phases = AnimationPhase.values();
            int currentIndex = currentPhase.ordinal();

            if (currentIndex < phases.length - 1) {
                currentPhase = phases[currentIndex + 1];
                phaseProgress = 0;

                // Update opening phase
                CrateOpening.OpeningPhase openingPhase = switch (currentPhase) {
                    case PREPARATION -> CrateOpening.OpeningPhase.STARTING;
                    case SPINNING -> CrateOpening.OpeningPhase.SPINNING;
                    case SLOWING -> CrateOpening.OpeningPhase.SLOWING;
                    case REVEALING -> CrateOpening.OpeningPhase.REVEALING;
                    case CELEBRATION -> CrateOpening.OpeningPhase.COMPLETED;
                };
                opening.advanceToPhase(openingPhase);
            } else {
                // Animation complete
                cleanup();
                crateManager.completeCrateOpening(opening);
                return;
            }
        }

        /**
         * Handles the preparation phase - FASTER now
         */
        private void handlePreparationPhase() {
            // Gentle pulsing effect on border
            if (phaseProgress % 5 == 0) { // Every 5 ticks instead of 10
                animateBorderPulse();
            }

            // Preparation sound
            if (phaseProgress == 5) { // Earlier sound trigger
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.5f);
            }
        }

        /**
         * Handles the spinning phase - FASTER now
         */
        private void handleSpinningPhase() {
            // Rapid item cycling - FASTER speed
            int interval = Math.max(1, phaseProgress / 5); // Faster division

            if (ticks % interval == 0) {
                ItemStack randomItem = createRandomDisplayItem();
                inventory.setItem(RESULT_SLOT, randomItem);

                // Spinning sound with increasing pitch - MORE FREQUENT
                if (ticks % 10 == 0) { // Every 10 ticks instead of 20
                    float pitch = 1.0f + (phaseProgress / (float)currentPhase.getDuration()) * 0.8f;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, pitch);
                }
            }

            // Animate glass panes - FASTER
            animateGlassPanes(Math.max(2, 4 - phaseProgress / 5));

            // Particle effects - MORE FREQUENT
            if (phaseProgress % 3 == 0) { // Every 3 ticks instead of 5
                createSpinParticles();
            }
        }

        /**
         * Handles the slowing phase - FASTER now
         */
        private void handleSlowingPhase() {
            // Much slower item cycling - but shorter overall duration
            int interval = 3 + (phaseProgress / 2); // Faster progression

            if (ticks % interval == 0) {
                ItemStack randomItem = createRandomDisplayItem();
                inventory.setItem(RESULT_SLOT, randomItem);

                // Slowing sound effects - MORE FREQUENT
                if (ticks % 15 == 0) { // Every 15 ticks instead of 30
                    float pitch = 1.5f - (phaseProgress / (float)currentPhase.getDuration()) * 0.5f;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, pitch);
                }
            }

            // Slower glass pane animation
            animateGlassPanes(8); // Reduced from 15

            // Mystical particle effects - MORE FREQUENT
            if (phaseProgress % 4 == 0) { // Every 4 ticks instead of 8
                createMysticalParticles();
            }
        }

        /**
         * Handles the revealing phase - FASTER now
         */
        private void handleRevealingPhase() {
            if (phaseProgress == 8) { // Earlier reveal (was 15)
                // Generate and show the actual reward
                ItemStack actualReward = createActualReward();
                inventory.setItem(RESULT_SLOT, actualReward);

                // Epic reveal sound
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                // Create dramatic particle effect
                createRevealParticles();
            }

            // Build suspense with slower animations
            animateGlassPanes(10); // Reduced from 20

            // Anticipation particles - MORE FREQUENT
            if (phaseProgress % 3 == 0) { // Every 3 ticks instead of 6
                createAnticipationParticles();
            }
        }

        /**
         * Handles the celebration phase - FASTER now
         */
        private void handleCelebrationPhase() {
            // Victory particle effects - MORE FREQUENT
            if (phaseProgress % 2 == 0) { // Every 2 ticks instead of 3
                createVictoryParticles();
            }

            // Final celebration sound - EARLIER
            if (phaseProgress == 5) { // Earlier sound (was 10)
                playCelebrationEffects();
            }

            // Close inventory after celebration - EARLIER
            if (phaseProgress >= currentPhase.getDuration() - 3) { // 3 ticks before end instead of 5
                player.closeInventory();
            }
        }

        /**
         * Creates spinning particle effects
         */
        private void createSpinParticles() {
            Location loc = player.getLocation().add(0, 2, 0);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.VILLAGER_HAPPY, loc, 3, 0.5, 0.5, 0.5, 0.1);
            }
        }

        /**
         * Creates mystical particle effects
         */
        private void createMysticalParticles() {
            Location loc = player.getLocation().add(0, 2, 0);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.ENCHANTMENT_TABLE, loc, 5, 1, 1, 1, 0.1);
                if (opening.getCrateType().getTier() >= 4) {
                    world.spawnParticle(Particle.END_ROD, loc, 2, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }

        /**
         * Creates anticipation particle effects
         */
        private void createAnticipationParticles() {
            Location loc = player.getLocation().add(0, 2.5, 0);
            World world = loc.getWorld();
            if (world != null) {
                Particle particle = opening.getCrateType().getTier() >= 5 ?
                        Particle.DRAGON_BREATH : Particle.PORTAL;
                world.spawnParticle(particle, loc, 8, 0.8, 0.8, 0.8, 0.1);
            }
        }

        /**
         * Creates dramatic reveal particle effects
         */
        private void createRevealParticles() {
            Location loc = player.getLocation().add(0, 1, 0);
            World world = loc.getWorld();
            if (world == null) return;

            // Explosion of particles
            world.spawnParticle(Particle.FIREWORKS_SPARK, loc, 20, 2, 2, 2, 0.2);

            // Tier-specific particles
            Particle tierParticle = switch (opening.getCrateType().getTier()) {
                case 1, 2 -> Particle.VILLAGER_HAPPY;
                case 3, 4 -> Particle.ENCHANTMENT_TABLE;
                case 5, 6 -> Particle.TOTEM;
                default -> Particle.VILLAGER_HAPPY;
            };

            world.spawnParticle(tierParticle, loc, 30, 1.5, 1.5, 1.5, 0.1);
        }

        /**
         * Creates victory celebration particle effects
         */
        private void createVictoryParticles() {
            Location loc = player.getLocation().add(0, 3, 0);
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.FIREWORKS_SPARK, loc, 5, 1, 0.5, 1, 0.2);
                world.spawnParticle(Particle.VILLAGER_HAPPY, loc, 3, 0.5, 0.5, 0.5, 0.1);
            }
        }

        /**
         * Plays final celebration effects
         */
        private void playCelebrationEffects() {
            Location location = player.getLocation();
            CrateType crateType = opening.getCrateType();

            // Tier-based celebration sounds
            Sound celebrationSound = switch (crateType.getTier()) {
                case 1, 2 -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                case 3, 4 -> Sound.ENTITY_PLAYER_LEVELUP;
                case 5, 6 -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
                default -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            };

            player.playSound(location, celebrationSound, 1.0f, 1.0f);

            // Epic particle finale
            World world = location.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.FIREWORKS_SPARK, location.add(0, 2, 0), 50, 2, 2, 2, 0.3);

                if (crateType.getTier() >= 5) {
                    world.spawnParticle(Particle.TOTEM, location, 20, 1, 1, 1, 0.1);
                }
            }
        }

        /**
         * Animates border pulsing effect
         */
        private void animateBorderPulse() {
            ItemStack pulsePane = createEnhancedBorderPane(opening.getCrateType());

            // Update all border slots
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                if (isBorderSlot(i) && i != RESULT_SLOT) {
                    inventory.setItem(i, pulsePane);
                }
            }
        }

        /**
         * Animates the glass panes around the center with wave effect
         */
        private void animateGlassPanes(int interval) {
            if (ticks % interval == 0) {
                ItemStack newPane = createEnhancedBorderPane(opening.getCrateType());

                // Create wave pattern
                int[] borderSlots = {1, 2, 3, 4, 5, 6, 7, 9, 17, 19, 20, 21, 22, 23, 24, 25};
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
         * Creates a random display item for animation with enhanced visuals
         */
        private ItemStack createRandomDisplayItem() {
            Material[] materials = {
                    Material.DIAMOND_SWORD, Material.DIAMOND_HELMET, Material.EMERALD,
                    Material.GOLDEN_APPLE, Material.ENCHANTED_BOOK, Material.ENDER_PEARL,
                    Material.DIAMOND, Material.GOLD_INGOT, Material.IRON_INGOT,
                    Material.NETHERITE_INGOT, Material.AMETHYST_SHARD, Material.ECHO_SHARD
            };

            Material randomMaterial = materials[ThreadLocalRandom.current().nextInt(materials.length)];
            ItemStack item = new ItemStack(randomMaterial);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + "✦ " + ChatColor.MAGIC + "abcdef" +
                        ChatColor.RESET + ChatColor.YELLOW + " ✦");
                meta.setLore(Arrays.asList(
                        ChatColor.GRAY + "The fates are deciding...",
                        ChatColor.GRAY + "What will be revealed?"
                ));
                item.setItemMeta(meta);
            }

            return item;
        }

        /**
         * Creates the actual reward item display
         */
        private ItemStack createActualReward() {
            ItemStack reward = new ItemStack(Material.CHEST);
            ItemMeta meta = reward.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "✧ " + ChatColor.BOLD + "YOUR REWARDS AWAIT!" +
                        ChatColor.RESET + ChatColor.GOLD + " ✧");
                meta.setLore(Arrays.asList(
                        "",
                        ChatColor.YELLOW + "The mystical energies have chosen",
                        ChatColor.YELLOW + "your destiny! Your rewards will be",
                        ChatColor.YELLOW + "granted when the ritual completes.",
                        "",
                        ChatColor.GREEN + "✦ " + opening.getCrateType().getDisplayName() + " Tier " +
                                opening.getCrateType().getTier() + " ✦"
                ));
                reward.setItemMeta(meta);
            }
            return reward;
        }

        /**
         * Cleans up the animation
         */
        private void cleanup() {
            cancel();
            UUID playerId = player.getUniqueId();
            activeAnimations.remove(playerId);
            openInventories.remove(playerId);

            // Cancel related tasks
            BukkitTask actionBarTask = actionBarTasks.remove(playerId);
            if (actionBarTask != null) {
                actionBarTask.cancel();
            }

            BukkitTask soundTask = soundTasks.remove(playerId);
            if (soundTask != null) {
                soundTask.cancel();
            }

            // Clear action bar
            if (player.isOnline()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
        }
    }

    /**
     * Cleans up all active animations
     */
    public void cleanup() {
        // Cancel all animation tasks
        for (BukkitTask task : activeAnimations.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        // Cancel all action bar tasks
        for (BukkitTask task : actionBarTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        // Cancel all sound tasks
        for (BukkitTask task : soundTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }

        // Clear action bars for all players
        for (UUID playerId : actionBarTasks.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
        }

        activeAnimations.clear();
        openInventories.clear();
        actionBarTasks.clear();
        soundTasks.clear();
    }

    /**
     * Gets animation statistics for debugging
     */
    public Map<String, Object> getAnimationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeAnimations", activeAnimations.size());
        stats.put("openInventories", openInventories.size());
        stats.put("actionBarTasks", actionBarTasks.size());
        stats.put("soundTasks", soundTasks.size());
        stats.put("inventorySize", INVENTORY_SIZE);
        stats.put("resultSlot", RESULT_SLOT);
        stats.put("particleRadius", PARTICLE_RADIUS);
        stats.put("particleCount", PARTICLE_COUNT);

        // Calculate total duration
        int totalDuration = 0;
        for (AnimationPhase phase : AnimationPhase.values()) {
            totalDuration += phase.getDuration();
        }
        stats.put("totalAnimationTicks", totalDuration);
        stats.put("totalAnimationSeconds", totalDuration / 20.0);

        return stats;
    }
}
package com.rednetty.server.mechanics.economy.vendors.menus;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.economy.MoneyManager;
import com.rednetty.server.mechanics.item.orb.OrbManager;
import com.rednetty.server.utils.particles.ParticleUtil;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enhanced menu for the Gambler vendor with visual effects and better chat management
 */
public class GamblerMenu implements Listener {

    private static final String INVENTORY_TITLE = "Gambler";
    private static final int INVENTORY_SIZE = 27;

    // Static tracking of players in gambling states
    private static final Set<UUID> playersInGambling = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, GamblerData> playerGamblerData = new ConcurrentHashMap<>();
    // Track player chat messaging to completely prevent spam
    private static final Map<UUID, Long> playerMessageCooldowns = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 500; // 500ms between messages

    private final JavaPlugin plugin;
    private final Player player;
    private final Inventory inventory;

    // OrbManager instance for proper orb creation
    private final OrbManager orbManager;

    /**
     * Data class to track gambling state
     */
    private static class GamblerData {
        GambleType type;
        GambleState state;
        int amount;
        String orbType;
        int gamblerRoll;
        int playerRoll;
        // Add a task ID for running effect animations
        int effectTaskId = -1;
        // Store the player location for effects
        Location effectLocation;

        public GamblerData(GambleType type) {
            this.type = type;
            this.state = GambleState.WAITING_FOR_AMOUNT;
        }
    }

    // Gambling enums
    private enum GambleType {
        GEMS,
        ORBS
    }

    private enum GambleState {
        WAITING_FOR_AMOUNT,
        WAITING_FOR_CONFIRMATION,
        ROLLING_GAMBLER,
        ROLLING_PLAYER,
        COMPLETE
    }

    /**
     * Creates a gambler menu for a player
     *
     * @param player The player to open the menu for
     */
    public GamblerMenu(Player player) {
        this.player = player;
        this.plugin = YakRealms.getInstance();
        this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
        this.orbManager = OrbManager.getInstance();

        // Register events temporarily for this instance
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Initialize inventory
        setupInventory();
    }

    /**
     * Setup the inventory with items
     */
    private void setupInventory() {
        // Category Label
        inventory.setItem(4, createCategoryLabel("Games of Chance", Material.GOLD_INGOT));

        // Gambling options
        inventory.setItem(11, createGambleOption(
                "Gem Gambling",
                Material.EMERALD,
                Arrays.asList(
                        ChatColor.GRAY + "Gamble your gems for a chance",
                        ChatColor.GRAY + "to double your money!",
                        "",
                        ChatColor.GRAY + "Uses: " + ChatColor.WHITE + "Bank Notes",
                        ChatColor.GRAY + "Win Rate: " + ChatColor.WHITE + "50%"
                )
        ));

        inventory.setItem(15, createGambleOption(
                "Orb Gambling",
                Material.MAGMA_CREAM,
                Arrays.asList(
                        ChatColor.GRAY + "Gamble your orbs for a chance",
                        ChatColor.GRAY + "to double them!",
                        "",
                        ChatColor.GRAY + "Uses: " + ChatColor.WHITE + "Normal or Legendary Orbs",
                        ChatColor.GRAY + "Win Rate: " + ChatColor.WHITE + "40%"
                )
        ));

        // Add colored separators for decoration
        fillRow(0, (byte) 14);  // Red
        fillRow(2, (byte) 14);  // Red

        // Close button
        inventory.setItem(26, createCloseButton());
    }

    /**
     * Opens the menu for the player
     */
    public void open() {
        // Ensure player isn't already gambling
        if (playersInGambling.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You're already in a gambling session!");
            return;
        }

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals(INVENTORY_TITLE)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        // Cancel all clicks in this inventory
        event.setCancelled(true);

        // Check if player is already gambling
        if (playersInGambling.contains(playerId)) {
            player.sendMessage(ChatColor.RED + "You're already in a gambling session!");
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Handle close button
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Handle gem gambling
        if (clickedItem.getType() == Material.EMERALD) {
            player.closeInventory();
            startGambling(player, GambleType.GEMS);
            return;
        }

        // Handle orb gambling
        if (clickedItem.getType() == Material.MAGMA_CREAM) {
            player.closeInventory();
            startGambling(player, GambleType.ORBS);
            return;
        }
    }

    /**
     * Start the gambling process
     */
    private void startGambling(Player player, GambleType type) {
        UUID playerId = player.getUniqueId();

        // Add player to gambling set if not already gambling
        if (playersInGambling.contains(playerId)) {
            if (isMessageCooldownOver(playerId)) {
                player.sendMessage(ChatColor.RED + "You're already in a gambling session!");
                updateMessageCooldown(playerId);
            }
            return;
        }

        playersInGambling.add(playerId);

        // Create new gambling data
        GamblerData data = new GamblerData(type);
        playerGamblerData.put(playerId, data);

        // Send appropriate message
        if (type == GambleType.GEMS) {
            player.sendMessage(ChatColor.GOLD + "Gambler: " + ChatColor.WHITE + "Feeling lucky with your gems today?");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "Enter the " + ChatColor.BOLD + "AMOUNT" + ChatColor.GREEN + " of gems you'd like to gamble with.");
            player.sendMessage(ChatColor.GRAY + "You must have a bank note with exactly this amount in your inventory.");
            player.sendMessage(ChatColor.GRAY + "Type 'cancel' to exit gambling.");
            player.sendMessage("");
        } else {
            player.sendMessage(ChatColor.GOLD + "Gambler: " + ChatColor.WHITE + "Want to risk those orbs for a chance at doubling them?");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "Enter the " + ChatColor.BOLD + "AMOUNT" + ChatColor.GREEN + " and " + ChatColor.BOLD + "TYPE" + ChatColor.GREEN + " of orbs to gamble.");
            player.sendMessage(ChatColor.GRAY + "Format: <amount> <type> (e.g., '5 normal' or '3 legendary')");
            player.sendMessage(ChatColor.GRAY + "Maximum: 16 orbs per gamble");
            player.sendMessage(ChatColor.GRAY + "Type 'cancel' to exit gambling.");
            player.sendMessage("");
        }

        // Play intro gambling effect
        playGamblingIntroEffect(player);
    }

    /**
     * Handle chat input for gambling
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if this player is gambling
        if (!playersInGambling.contains(playerId)) {
            return;
        }

        // Cancel the chat event
        event.setCancelled(true);

        // Check message cooldown to prevent spam
        if (!isMessageCooldownOver(playerId)) {
            return;
        }

        // Update cooldown
        updateMessageCooldown(playerId);

        // Get the message and gambling data
        String message = event.getMessage();
        final GamblerData data = playerGamblerData.get(playerId);

        // Handle cancellation
        if (message.equalsIgnoreCase("cancel")) {
            // Must schedule cleanup as this is an async event
            Bukkit.getScheduler().runTask(plugin, () -> {
                cleanup(playerId);
                player.sendMessage(ChatColor.RED + "Gambling cancelled.");
            });
            return;
        }

        // Process based on gambling type and state - must be done in main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (data.type == GambleType.GEMS) {
                processGemGamblingChat(player, message, data);
            } else if (data.type == GambleType.ORBS) {
                processOrbGamblingChat(player, message, data);
            }
        });
    }

    /**
     * Process chat input for gem gambling
     */
    private void processGemGamblingChat(Player player, String message, GamblerData data) {
        switch (data.state) {
            case WAITING_FOR_AMOUNT:
                try {
                    int amount = Integer.parseInt(message);

                    if (amount <= 0) {
                        player.sendMessage(ChatColor.RED + "Please enter a positive amount.");
                        return;
                    }

                    data.amount = amount;
                    data.state = GambleState.WAITING_FOR_CONFIRMATION;

                    player.sendMessage(ChatColor.GREEN + "You're about to gamble " + amount + " gems. Type " +
                            ChatColor.BOLD + "CONFIRM" + ChatColor.GREEN + " to proceed or " +
                            ChatColor.BOLD + "CANCEL" + ChatColor.GREEN + " to stop.");

                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Please enter a valid number of gems to gamble.");
                }
                break;

            case WAITING_FOR_CONFIRMATION:
                if (message.equalsIgnoreCase("confirm")) {
                    // Check if player has a bank note with this amount
                    if (!hasBankNote(player, data.amount)) {
                        player.sendMessage(ChatColor.RED + "You don't have a bank note with exactly " + data.amount + " gems!");
                        cleanup(player.getUniqueId());
                        return;
                    }

                    // Remove the bank note
                    takeBankNote(player, data.amount);

                    // Start the rolling process
                    startRolling(player);
                } else if (!message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.RED + "Please type " + ChatColor.BOLD + "CONFIRM" +
                            ChatColor.RED + " to proceed or " + ChatColor.BOLD + "CANCEL" +
                            ChatColor.RED + " to stop.");
                }
                break;

            default:
                // Other states shouldn't handle chat input
                break;
        }
    }

    /**
     * Process chat input for orb gambling
     */
    private void processOrbGamblingChat(Player player, String message, GamblerData data) {
        switch (data.state) {
            case WAITING_FOR_AMOUNT:
                String[] parts = message.split(" ");

                if (parts.length != 2) {
                    player.sendMessage(ChatColor.RED + "Please use format: <amount> <type>");
                    player.sendMessage(ChatColor.RED + "Example: '5 normal' or '3 legendary'");
                    return;
                }

                try {
                    int amount = Integer.parseInt(parts[0]);
                    String orbType = parts[1].toLowerCase();

                    if (amount <= 0) {
                        player.sendMessage(ChatColor.RED + "Please enter a positive amount.");
                        return;
                    }

                    if (amount > 16) {
                        player.sendMessage(ChatColor.RED + "You can only gamble up to 16 orbs at once.");
                        return;
                    }

                    if (!orbType.equals("normal") && !orbType.equals("legendary")) {
                        player.sendMessage(ChatColor.RED + "Orb type must be 'normal' or 'legendary'.");
                        return;
                    }

                    data.amount = amount;
                    data.orbType = orbType;
                    data.state = GambleState.WAITING_FOR_CONFIRMATION;

                    player.sendMessage(ChatColor.GREEN + "You're about to gamble " + amount + " " + orbType +
                            " orbs. Type " + ChatColor.BOLD + "CONFIRM" + ChatColor.GREEN +
                            " to proceed or " + ChatColor.BOLD + "CANCEL" + ChatColor.GREEN + " to stop.");

                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Please enter a valid number for the amount.");
                }
                break;

            case WAITING_FOR_CONFIRMATION:
                if (message.equalsIgnoreCase("confirm")) {
                    // Check if player has the required orbs
                    if (!hasOrbs(player, data.amount, data.orbType)) {
                        player.sendMessage(ChatColor.RED + "You don't have " + data.amount + " " +
                                data.orbType + " orbs in your inventory!");
                        cleanup(player.getUniqueId());
                        return;
                    }

                    // Remove the orbs
                    takeOrbs(player, data.amount, data.orbType);

                    // Start the rolling process
                    startRolling(player);
                } else if (!message.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.RED + "Please type " + ChatColor.BOLD + "CONFIRM" +
                            ChatColor.RED + " to proceed or " + ChatColor.BOLD + "CANCEL" +
                            ChatColor.RED + " to stop.");
                }
                break;

            default:
                // Other states shouldn't handle chat input
                break;
        }
    }

    /**
     * Start the dice rolling process
     */
    private void startRolling(Player player) {
        UUID playerId = player.getUniqueId();
        GamblerData data = playerGamblerData.get(playerId);

        // Update state
        data.state = GambleState.ROLLING_GAMBLER;

        // Store location for effects
        data.effectLocation = player.getLocation().clone();

        // Start dice shake effect animations
        startDiceShakeEffects(player, data);

        player.sendMessage(ChatColor.GOLD + "The gambler shakes the dice...");

        // Prevent player from moving during rolling
        player.setMetadata("gambling", new FixedMetadataValue(plugin, true));

        // Schedule the gambler roll
        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (!player.isOnline() || !playersInGambling.contains(playerId)) {
                    this.cancel();
                    return;
                }

                if (countdown > 0) {
                    player.sendMessage(ChatColor.GOLD + "Rolling in " + countdown + "...");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f + (countdown * 0.25f));
                    countdown--;
                } else {
                    // Cancel this task
                    this.cancel();

                    // Roll for the gambler
                    int roll;
                    if (data.type == GambleType.GEMS) {
                        // For gems: 0-100 with bias (50% chance to win)
                        roll = ThreadLocalRandom.current().nextInt(101);
                    } else {
                        // For orbs: 0-100 with bias (40% chance to win)
                        roll = ThreadLocalRandom.current().nextInt(60) + 41; // 41-100
                    }

                    data.gamblerRoll = roll;

                    // Stop dice shake effects
                    stopEffectTask(data);

                    // Play dice roll effect
                    playDiceRollEffect(player, false);

                    // Display result
                    player.sendMessage(ChatColor.GOLD + "The gambler rolled: " + ChatColor.YELLOW + roll);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);

                    // Schedule player roll
                    schedulePlayerRoll(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Schedule the player's dice roll
     */
    private void schedulePlayerRoll(Player player) {
        UUID playerId = player.getUniqueId();
        GamblerData data = playerGamblerData.get(playerId);

        // Update state
        data.state = GambleState.ROLLING_PLAYER;

        // Start dice shake effects for player
        startDiceShakeEffects(player, data);

        player.sendMessage(ChatColor.GREEN + "Your turn to roll!");

        // Schedule the player roll
        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (!player.isOnline() || !playersInGambling.contains(playerId)) {
                    this.cancel();
                    return;
                }

                if (countdown > 0) {
                    player.sendMessage(ChatColor.GREEN + "Rolling in " + countdown + "...");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.7f + (countdown * 0.25f));
                    countdown--;
                } else {
                    // Cancel this task
                    this.cancel();

                    // Stop dice shake effects
                    stopEffectTask(data);

                    // Roll for the player
                    int roll = ThreadLocalRandom.current().nextInt(101);
                    data.playerRoll = roll;

                    // Play dice roll effect
                    playDiceRollEffect(player, true);

                    // Display result
                    player.sendMessage(ChatColor.GREEN + "You rolled: " + ChatColor.YELLOW + roll);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

                    // Process the results
                    processResults(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Process the gambling results
     */
    private void processResults(Player player) {
        UUID playerId = player.getUniqueId();
        GamblerData data = playerGamblerData.get(playerId);

        // Update state
        data.state = GambleState.COMPLETE;

        // Allow player to move again
        player.removeMetadata("gambling", plugin);

        // Determine winner
        if (data.playerRoll > data.gamblerRoll) {
            // Player wins
            if (data.type == GambleType.GEMS) {
                int winnings = data.amount * 2;
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "WINNER! You've won " + ChatColor.GOLD + winnings + " gems" +
                        ChatColor.GREEN + "!");

                // Create bank note for winnings
                ItemStack note = MoneyManager.createBankNote(winnings);
                player.getInventory().addItem(note);
            } else {
                int winnings = data.amount * 2;
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "WINNER! You've won " + ChatColor.GOLD + winnings + " " +
                        data.orbType + " orbs" + ChatColor.GREEN + "!");

                // Give orbs using the OrbManager
                giveOrbsUsingAPI(player, winnings, data.orbType);
            }

            // Special effects for winning
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Victory celebration effects
            playVictoryEffects(player, data);

        } else {
            // Player loses
            if (data.type == GambleType.GEMS) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "You lost " + ChatColor.GOLD + data.amount + " gems" +
                        ChatColor.RED + "!");
            } else {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "You lost " + ChatColor.GOLD + data.amount + " " +
                        data.orbType + " orbs" + ChatColor.RED + "!");
            }

            // Loss effects
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            playLossEffects(player, data);
        }

        // Clean up after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup(playerId);
            }
        }.runTaskLater(plugin, 60L); // 3 seconds
    }

    /**
     * Plays visual and sound effects for gambling intro
     */
    private void playGamblingIntroEffect(Player player) {
        // Play intro sound sequence
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);

        // Particle effects around the player
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Create a circle of particles around the player
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            Location particleLoc = loc.clone().add(x, 0.2, z);

            if (i % 30 == 0) {
                world.spawnParticle(Particle.SPELL_WITCH, particleLoc, 5, 0.1, 0.1, 0.1, 0.01);
            } else {
                world.spawnParticle(Particle.SPELL_MOB, particleLoc, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Starts dice shake particle effects
     */
    private void startDiceShakeEffects(Player player, GamblerData data) {
        // Cancel any existing effect task
        stopEffectTask(data);

        // Create new task for dice shake animation
        data.effectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (player.isOnline() && playersInGambling.contains(player.getUniqueId())) {
                Location loc = player.getLocation().add(0, 1.7, 0);

                // Create random offsets for shaking dice effect
                double offsetX = (Math.random() - 0.5) * 0.7;
                double offsetY = (Math.random() - 0.5) * 0.3;
                double offsetZ = (Math.random() - 0.5) * 0.7;

                Location effectLoc = loc.clone().add(offsetX, offsetY, offsetZ);

                // Red or blue particles depending on whose turn it is
                Particle.DustOptions dustOptions;
                if (data.state == GambleState.ROLLING_GAMBLER) {
                    dustOptions = new Particle.DustOptions(Color.RED, 1.0f);
                } else {
                    dustOptions = new Particle.DustOptions(Color.AQUA, 1.0f);
                }

                player.getWorld().spawnParticle(Particle.REDSTONE, effectLoc, 1, 0, 0, 0, 0, dustOptions);

                // Add ambient sound for dice shaking
                if (Math.random() < 0.3) {
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_HIT, 0.3f, 1.5f + (float)Math.random() * 0.5f);
                }
            }
        }, 2L, 2L);
    }

    /**
     * Plays dice roll effects
     */
    private void playDiceRollEffect(Player player, boolean isPlayer) {
        Location loc = player.getLocation().add(0, 1.7, 0);

        // Main dice roll effect
        Particle.DustOptions dustOptions = isPlayer ?
                new Particle.DustOptions(Color.AQUA, 1.5f) :
                new Particle.DustOptions(Color.RED, 1.5f);

        // Create a cube-like pattern
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? -0.3 : 0.3;
            double y = (i & 2) == 0 ? -0.3 : 0.3;
            double z = (i & 4) == 0 ? -0.3 : 0.3;

            Location cornerLoc = loc.clone().add(x, y, z);
            player.getWorld().spawnParticle(Particle.REDSTONE, cornerLoc, 10, 0.1, 0.1, 0.1, 0, dustOptions);
        }

        // Sound of dice rolling
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.2f);
    }

    /**
     * Plays victory effects for winning
     */
    private void playVictoryEffects(Player player, GamblerData data) {
        Location loc = player.getLocation();

        // Launch fireworks
        launchWinFireworks(player);

        // Play spiral particle effects
        new BukkitRunnable() {
            double angle = 0;
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline() || count >= 20) {
                    this.cancel();
                    return;
                }

                // Spiral upward effect
                for (int i = 0; i < 3; i++) {
                    double x = Math.sin(angle + (i * Math.PI * 2 / 3)) * 1.5;
                    double z = Math.cos(angle + (i * Math.PI * 2 / 3)) * 1.5;
                    double y = count * 0.1;

                    Location particleLoc = loc.clone().add(x, y, z);

                    // Gold/yellow particles for win
                    Particle.DustOptions dustOptions = new Particle.DustOptions(Color.YELLOW, 1.5f);
                    player.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0, dustOptions);

                    // Add some sparkle
                    if (count % 3 == 0) {
                        player.getWorld().spawnParticle(Particle.SPELL_INSTANT, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
                    }
                }

                // Every few ticks, play a happy sound
                if (count % 5 == 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f + (count * 0.025f));
                }

                angle += Math.PI / 8;
                count++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Create light beam effect for big wins
        if (data.type == GambleType.GEMS && data.amount >= 1000 ||
                data.type == GambleType.ORBS && data.amount >= 8) {
            createVictoryBeamEffect(player);
        }
    }

    /**
     * Creates a victory beam effect for big wins
     */
    private void createVictoryBeamEffect(Player player) {
        Location center = player.getLocation().add(0, 0.5, 0);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline() || count >= 40) {
                    this.cancel();
                    return;
                }

                // Create a vertical beam
                for (int y = 0; y < 15; y++) {
                    double angle = count * 15 + (y * 10);
                    double radius = 0.4;
                    double x = Math.sin(Math.toRadians(angle)) * radius;
                    double z = Math.cos(Math.toRadians(angle)) * radius;

                    Location beamLoc = center.clone().add(x, y * 0.5, z);

                    // Golden beam
                    Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.0f);
                    player.getWorld().spawnParticle(Particle.REDSTONE, beamLoc, 1, 0, 0, 0, 0, dustOptions);
                }

                count++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Launches fireworks for a victory
     */
    private void launchWinFireworks(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Schedule multiple fireworks
        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }

                    // Launch a firework
                    Firework fw = world.spawn(loc, Firework.class);
                    FireworkMeta meta = fw.getFireworkMeta();

                    // Random firework types for variety
                    FireworkEffect.Type[] types = FireworkEffect.Type.values();
                    FireworkEffect.Type type = types[new Random().nextInt(types.length)];

                    // Gold/yellow with random accent colors
                    Color[] colors = {
                            Color.YELLOW,
                            Color.fromRGB(255, 215, 0), // Gold
                            Color.fromRGB(255, 165, 0)  // Orange
                    };

                    // Create the firework effect
                    FireworkEffect effect = FireworkEffect.builder()
                            .withColor(colors[new Random().nextInt(colors.length)])
                            .with(type)
                            .withFlicker()
                            .build();

                    meta.addEffect(effect);
                    meta.setPower(1); // Low power so it explodes quickly
                    fw.setFireworkMeta(meta);
                }
            }.runTaskLater(plugin, i * 5L);
        }
    }

    /**
     * Plays effects for losing
     */
    private void playLossEffects(Player player, GamblerData data) {
        Location loc = player.getLocation();

        // Red particles to indicate loss
        for (int i = 0; i < 40; i++) {
            int finalI = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) return;

                    // Circle of red dust
                    double angle = Math.random() * Math.PI * 2;
                    double radius = 0.5 + Math.random() * 1.0;
                    double x = Math.sin(angle) * radius;
                    double z = Math.cos(angle) * radius;

                    Location particleLoc = loc.clone().add(x, 0.5 + Math.random(), z);
                    Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.0f);

                    player.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, 0, 0, 0, 0, redDust);

                    // Add some smoke for dramatic effect
                    if (finalI % 4 == 0) {
                        player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
            }.runTaskLater(plugin, i / 2L);
        }

        // Play sad sound sequence
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!player.isOnline() || count >= 3) {
                    this.cancel();
                    return;
                }

                // Sad descending tones
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f - (count * 0.1f));
                count++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * Clean up gambling state for a player
     */
    private void cleanup(UUID playerId) {
        // Stop any running effect tasks
        GamblerData data = playerGamblerData.get(playerId);
        if (data != null) {
            stopEffectTask(data);
        }

        playersInGambling.remove(playerId);
        playerGamblerData.remove(playerId);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.hasMetadata("gambling")) {
            player.removeMetadata("gambling", plugin);
        }
    }

    /**
     * Stops any running effect task
     */
    private void stopEffectTask(GamblerData data) {
        if (data.effectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(data.effectTaskId);
            data.effectTaskId = -1;
        }
    }

    /**
     * Check if message cooldown has expired
     */
    private boolean isMessageCooldownOver(UUID playerId) {
        if (!playerMessageCooldowns.containsKey(playerId)) {
            return true;
        }

        long lastMessageTime = playerMessageCooldowns.get(playerId);
        return System.currentTimeMillis() - lastMessageTime >= MESSAGE_COOLDOWN_MS;
    }

    /**
     * Update the message cooldown timestamp
     */
    private void updateMessageCooldown(UUID playerId) {
        playerMessageCooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * Prevent player movement during critical gambling phases
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.hasMetadata("gambling")) {
            // Allow looking around but not movement
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from);
            }
        }
    }

    /**
     * Prevent item dropping during gambling
     */
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (playersInGambling.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (isMessageCooldownOver(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You can't drop items while gambling!");
                updateMessageCooldown(player.getUniqueId());
            }
        }
    }

    /**
     * Handle player logout during gambling
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (playersInGambling.contains(playerId)) {
            GamblerData data = playerGamblerData.get(playerId);

            // Only refund if they haven't seen results yet
            if (data.state != GambleState.COMPLETE) {
                // Mark this player for refund on next login
                if (data.type == GambleType.GEMS) {
                    player.setMetadata("gemRefund", new FixedMetadataValue(plugin, data.amount));
                } else {
                    player.setMetadata("orbRefundAmount", new FixedMetadataValue(plugin, data.amount));
                    player.setMetadata("orbRefundType", new FixedMetadataValue(plugin, data.orbType));
                }
            }

            // Clean up
            cleanup(playerId);
        }
    }

    // Utility methods

    /**
     * Check if player has a bank note with the exact amount
     */
    private boolean hasBankNote(Player player, int amount) {
        for (ItemStack item : player.getInventory()) {
            if (item != null && item.getType() == Material.PAPER &&
                    item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                    item.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Bank Note")) {

                int value = MoneyManager.getGemValue(item);
                if (value == amount) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Take a bank note with the exact amount
     */
    private void takeBankNote(Player player, int amount) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item != null && item.getType() == Material.PAPER &&
                    item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                    item.getItemMeta().getDisplayName().equals(ChatColor.GREEN + "Bank Note")) {

                int value = MoneyManager.getGemValue(item);
                if (value == amount) {
                    player.getInventory().setItem(i, null);
                    return;
                }
            }
        }
    }

    /**
     * Give orbs to player using OrbManager
     */
    private void giveOrbsUsingAPI(Player player, int amount, String type) {
        boolean isLegendary = type.equalsIgnoreCase("legendary");

        // Calculate how many full stacks and remainder
        int fullStacks = amount / 64;
        int remainder = amount % 64;

        // Give full stacks first
        for (int i = 0; i < fullStacks; i++) {
            if (isLegendary) {
                ItemStack legendaryOrbs = orbManager.createLegendaryOrb(false);
                legendaryOrbs.setAmount(64);
                player.getInventory().addItem(legendaryOrbs);
            } else {
                ItemStack normalOrbs = orbManager.createNormalOrb(false);
                normalOrbs.setAmount(64);
                player.getInventory().addItem(normalOrbs);
            }
        }

        // Give remainder
        if (remainder > 0) {
            if (isLegendary) {
                ItemStack legendaryOrbs = orbManager.createLegendaryOrb(false);
                legendaryOrbs.setAmount(remainder);
                player.getInventory().addItem(legendaryOrbs);
            } else {
                ItemStack normalOrbs = orbManager.createNormalOrb(false);
                normalOrbs.setAmount(remainder);
                player.getInventory().addItem(normalOrbs);
            }
        }
    }

    /**
     * Check if player has the specified orbs
     */
    private boolean hasOrbs(Player player, int amount, String type) {
        int count = 0;
        boolean isLegendary = type.equals("legendary");

        for (ItemStack item : player.getInventory()) {
            if (item != null && item.getType() == Material.MAGMA_CREAM) {
                boolean isCorrectType = isLegendary ?
                        orbManager.isLegendaryOrb(item) :
                        orbManager.isNormalOrb(item);

                if (isCorrectType) {
                    count += item.getAmount();

                    if (count >= amount) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Take the specified orbs from the player
     */
    private void takeOrbs(Player player, int amount, String type) {
        int remaining = amount;
        boolean isLegendary = type.equals("legendary");

        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item != null && item.getType() == Material.MAGMA_CREAM) {
                boolean isCorrectType = isLegendary ?
                        orbManager.isLegendaryOrb(item) :
                        orbManager.isNormalOrb(item);

                if (isCorrectType) {
                    if (item.getAmount() <= remaining) {
                        // Take the entire stack
                        remaining -= item.getAmount();
                        player.getInventory().setItem(i, null);
                    } else {
                        // Take partial stack
                        item.setAmount(item.getAmount() - remaining);
                        remaining = 0;
                    }
                }
            }
        }
    }

    // UI utility methods

    /**
     * Create a category label for the menu
     */
    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack label = new ItemStack(icon);
        ItemMeta meta = label.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + name);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Click on an option",
                ChatColor.GRAY + "below to start gambling!"
        ));
        label.setItemMeta(meta);
        return label;
    }

    /**
     * Create a gamble option button
     */
    private ItemStack createGambleOption(String name, Material icon, List<String> lore) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Create a close button
     */
    private ItemStack createCloseButton() {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Close");
        button.setItemMeta(meta);
        return button;
    }

    /**
     * Fill a row with colored glass panes
     */
    private void fillRow(int row, byte color) {
        for (int i = row * 9; i < (row + 1) * 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, createSeparator(color));
            }
        }
    }

    /**
     * Create a separator glass pane
     */
    private ItemStack createSeparator(byte color) {
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1, color);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(" ");
        separator.setItemMeta(meta);
        return separator;
    }
}
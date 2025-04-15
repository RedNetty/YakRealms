package com.rednetty.server.mechanics.player.movement;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.ui.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player dash movement abilities.
 * Allows players to dash forward with weapons or staves when sneaking.
 */
public class DashMechanics implements Listener {

    // Configuration constants
    private static final double DASH_SPEED = 1.35;
    private static final int DASH_DURATION = 2; // in ticks (1 tick = 0.05 seconds)
    private static final int COOLDOWN_SECONDS = 15;
    private static final String DASH_COOLDOWN_META = "dashCooldown";

    // In-memory cooldown tracking
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final YakPlayerManager playerManager;

    /**
     * Constructor initializes the player manager and registers events.
     */
    public DashMechanics() {
        this.playerManager = YakPlayerManager.getInstance();
        Bukkit.getServer().getPluginManager().registerEvents(this, YakRealms.getInstance());
    }

    /**
     * Initializes the dash system.
     */
    public void onEnable() {
        YakRealms.log("Dash mechanics have been enabled.");
    }

    /**
     * Clean up on disable.
     */
    public void onDisable() {
        cooldowns.clear();
        YakRealms.log("Dash mechanics have been disabled.");
    }

    /**
     * Handles the player interact event to trigger dashes.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = event.getItem();

        // Block dash if interacting with interactive blocks.
        if (event.hasBlock() && isInteractiveBlock(event.getClickedBlock())) {
            return;
        }

        // Don't dash if near an NPC.
        if (isNearNPC(player)) {
            return;
        }

        // Only process right-click actions with items.
        if (item == null || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        // Determine if the player should dash.
        boolean shouldDash = isWeapon(item) || (player.isSneaking() && isStaff(item));
        if (shouldDash && canDash(player)) {
            performDash(player);
        }
    }

    /**
     * Returns whether the given block is interactive.
     */
    private boolean isInteractiveBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.ENDER_CHEST
                || type == Material.CRAFTING_TABLE
                || type == Material.ANVIL
                || type == Material.FURNACE
                || type == Material.BREWING_STAND
                || type == Material.ENCHANTING_TABLE
                || type.name().contains("SHULKER_BOX");
    }

    /**
     * Checks if the player is near an NPC.
     */
    private boolean isNearNPC(Player player) {
        List<Entity> nearbyEntities = player.getNearbyEntities(4, 4, 4);
        for (Entity entity : nearbyEntities) {
            if (entity.hasMetadata("NPC")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the item is a weapon.
     */
    private boolean isWeapon(ItemStack item) {
        if (item == null) return false;
        String type = item.getType().name();
        return type.endsWith("_SWORD") || type.endsWith("_AXE") || type.endsWith("_SPADE");
    }

    /**
     * Returns true if the item is a staff.
     */
    private boolean isStaff(ItemStack item) {
        if (item == null) return false;
        return item.getType().name().endsWith("_HOE");
    }

    /**
     * Checks if a player can dash (based on cooldown).
     */
    private boolean canDash(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.hasMetadata(DASH_COOLDOWN_META)) {
            long cooldownTime = player.getMetadata(DASH_COOLDOWN_META).get(0).asLong();
            if (System.currentTimeMillis() < cooldownTime) {
                return false;
            }
        }
        return !cooldowns.containsKey(uuid) ||
                System.currentTimeMillis() - cooldowns.get(uuid) > COOLDOWN_SECONDS * 1000;
    }

    /**
     * Performs the dash movement for the player.
     */
    private void performDash(Player player) {
        // Calculate dash vector (horizontal only).
        Vector direction = player.getLocation().getDirection()
                .setY(0)
                .normalize()
                .multiply(DASH_SPEED);

        // Apply dash velocity over multiple ticks.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= DASH_DURATION) {
                    cancel();
                    return;
                }
                player.setVelocity(direction);
                spawnDashParticles(player);
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, 1);

        // Play teleport sound.
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        // Create a trail of portal particles.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= DASH_DURATION * 4) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(
                        Particle.PORTAL,
                        player.getLocation(),
                        10, 0.5, 0.5, 0.5, 0.05
                );
                ticks++;
            }
        }.runTaskTimer(YakRealms.getInstance(), 0, 1);

        // Set cooldown.
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.setMetadata(DASH_COOLDOWN_META,
                new FixedMetadataValue(YakRealms.getInstance(),
                        System.currentTimeMillis() + (COOLDOWN_SECONDS * 1000)));

        // Start the cooldown timer display using the action bar.
        startCooldownTimer(player);
    }

    /**
     * Spawns dash effect particles.
     */
    private void spawnDashParticles(Player player) {
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                player.getLocation(),
                5, 0.2, 0.2, 0.2, 0.05
        );
    }

    /**
     * Starts the cooldown timer display using the action bar.
     * A countdown is shown during the cooldown period, then "Dash Ready!" is displayed briefly.
     */
    private void startCooldownTimer(Player player) {
        // Display the countdown message.
        ActionBarUtil.addCountdownMessage(player, ChatColor.GRAY + "Dash Cooldown: " + ChatColor.RED, COOLDOWN_SECONDS);

        // After the cooldown, display "Dash Ready!" for 3 seconds.
        Bukkit.getScheduler().runTaskLater(YakRealms.getInstance(), () -> {
            if (player.isOnline()) {
                ActionBarUtil.addTemporaryMessage(player, ChatColor.GREEN + "Dash Ready!", 40L); // 60 ticks = 3 seconds
            }
        }, COOLDOWN_SECONDS * 20L);
    }
}

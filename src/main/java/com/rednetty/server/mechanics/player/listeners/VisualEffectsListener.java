package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.player.moderation.ModerationMechanics;
import com.rednetty.server.mechanics.player.moderation.Rank;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all visual effects including particle effects,
 * trail effects, and item glow effects.
 */
public class VisualEffectsListener extends BaseListener {

    // Track players with visual effects
    private final Set<UUID> activeEffects = ConcurrentHashMap.newKeySet();

    // Random generator for particle offsets
    private final Random random = new Random();

    public VisualEffectsListener() {
        super();
    }

    @Override
    public void initialize() {
        logger.info("Visual effects listener initialized");
    }

    /**
     * Process particle effects for all online players
     * This method is called periodically from the PlayerListenerManager
     */
    public void processPlayerEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip players in vanish mode
            if (isVanished(player)) {
                continue;
            }

            // Process armor effects
            processArmorEffects(player);

        }
    }

    /**
     * Process special armor effects (like frost armor)
     */
    private void processArmorEffects(Player player) {
        for (ItemStack item : player.getInventory().getArmorContents()) {
            applyFrostArmorEffect(player, item);
        }
    }

    /**
     * Apply frost armor effects
     */
    private void applyFrostArmorEffect(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String displayName = item.getItemMeta().getDisplayName();
        if (displayName.contains("Frost-Wing's")) {
            Location location = player.getLocation().clone();

            // Position particles based on armor type
            if (item.getType().name().contains("HELMET")) {
                location.add(0, 2, 0);
            } else if (item.getType().name().contains("CHESTPLATE")) {
                location.add(0, 1.25, 0);
            } else if (item.getType().name().contains("LEGGINGS")) {
                location.add(0, 0.75, 0);
            }

            // Display particles
            player.getWorld().spawnParticle(Particle.SPELL_MOB, location, 30, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.WHITE, 1));
            player.getWorld().spawnParticle(Particle.REDSTONE, location, 30, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.YELLOW, 1));
        }
    }

    /**
     * Process trail effects based on donor rank
     */
    private void processTrailEffects(Player player) {
        Rank rank = ModerationMechanics.getInstance().getPlayerRank(player.getUniqueId());
        Location playerLocation = player.getLocation().clone();

        // Generate random offsets for particles
        float offsetX = random.nextFloat() - 0.2F;
        float offsetY = random.nextFloat() - 0.2F;
        float offsetZ = random.nextFloat() - 0.2F;

        // Apply trail effects based on rank
        switch (rank) {
            case SUB:
                // Happy villager particles
                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,
                        playerLocation.clone().add(offsetX, offsetY, offsetZ),
                        10, 0.125f, 0.125f, 0.125f, 0.02f);
                break;

            case SUB1:
                // Flame particles
                player.getWorld().spawnParticle(Particle.FLAME,
                        playerLocation.clone().add(offsetX, offsetY, offsetZ),
                        10, 0, 0, 0, 0.02f);
                break;

            case SUPPORTER:
            case SUB3:
                // Helix particle effect
                createHelixEffect(player, playerLocation);
                break;

            case SUB2:
                // Witch spell particles
                player.getWorld().spawnParticle(Particle.SPELL_WITCH,
                        playerLocation.clone().add(offsetX, offsetY, offsetZ),
                        10, 0, 0, 0, 1.0f);
                break;

            default:
                break;
        }
    }

    /**
     * Create a helix particle effect
     */
    private void createHelixEffect(Player player, Location baseLocation) {
        double phi = 0;
        phi = phi + Math.PI / 8;

        Location location = baseLocation.clone();

        for (double t = 0; t <= 2 * Math.PI; t = t + Math.PI / 16) {
            for (double i = 0; i <= 1; i = i + 1) {
                double x = 0.4 * (2 * Math.PI - t) * 0.5 * Math.cos(t + phi + i * Math.PI);
                double y = 0.5 * t;
                double z = 0.4 * (2 * Math.PI - t) * 0.5 * Math.sin(t + phi + i * Math.PI);

                location.add(x, y, z);
                player.getWorld().spawnParticle(Particle.REDSTONE, location, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.RED, 1));
                location.subtract(x, y, z);
            }
        }
    }

    /**
     * Apply glow effect to dropped items
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        // Apply glow effect to dropped items with lore (equipment)
        ItemStack itemStack = event.getItemDrop().getItemStack();
        if (itemStack != null && itemStack.getType() != Material.AIR &&
                itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {

            // Use a delay to ensure the item entity is fully spawned
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyGlowToDroppedItem(event.getItemDrop(), itemStack);
            }, 2L);
        }
    }

    /**
     * Apply glow effect to a dropped item based on its rarity
     */
    private void applyGlowToDroppedItem(Item droppedItem, ItemStack itemStack) {
        ChatColor glowColor = determineItemRarity(itemStack);

        if (glowColor != null) {
            // In original code: GlowAPI.setGlowing(droppedItem, glowColor);
            // This requires implementation of GlowAPI or similar system
            // For now, we'll use the team-based approach built into vanilla
            setItemGlow(droppedItem, glowColor);
        }
    }

    /**
     * Determine item rarity color based on lore
     */
    private ChatColor determineItemRarity(ItemStack itemStack) {
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasLore()) {
            List<String> lore = itemStack.getItemMeta().getLore();

            for (String line : lore) {
                if (line.contains("Common")) {
                    return ChatColor.WHITE;
                } else if (line.contains("Uncommon")) {
                    return ChatColor.GREEN;
                } else if (line.contains("Rare")) {
                    return ChatColor.AQUA;
                } else if (line.contains("Unique")) {
                    return ChatColor.YELLOW;
                }
            }
        }

        return null;
    }

    /**
     * Set glow effect on an item entity
     * This is a placeholder for the actual implementation
     */
    private void setItemGlow(Item item, ChatColor color) {
        // TODO: Implement proper glowing API or use scoreboard teams
        // This would require a more complex implementation using teams and metadata
        logger.info("Setting glow effect on item: " + item.getItemStack().getType() +
                " with color: " + color.name());
    }

    /**
     * Check if a player is vanished
     */
    private boolean isVanished(Player player) {
        // TODO: Implement proper vanish check
        return player.hasMetadata("vanished");
    }

    /**
     * Check if a player is a donator
     */
    private boolean isDonator(Player player) {
        return ModerationMechanics.getInstance().isDonator(player);
    }

    /**
     * Handle player join event
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

    }

    /**
     * Handle player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove from effect tracking
        activeEffects.remove(event.getPlayer().getUniqueId());
    }
}
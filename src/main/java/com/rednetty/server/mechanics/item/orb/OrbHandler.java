package com.rednetty.server.mechanics.item.orb;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.utils.particles.FireworkUtil;
import com.rednetty.server.utils.particles.ParticleUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles all events related to orbs
 */
public class OrbHandler implements Listener {
    private static final Logger LOGGER = Logger.getLogger(OrbHandler.class.getName());

    private final OrbGenerator orbGenerator;
    private final OrbProcessor orbProcessor;
    private final Set<UUID> processingPlayers = new HashSet<>();

    /**
     * Constructor
     */
    public OrbHandler() {
        this.orbGenerator = OrbAPI.getOrbGenerator();
        this.orbProcessor = new OrbProcessor();
    }

    /**
     * Registers this handler with the server
     */
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, YakRealms.getInstance());
        YakRealms.log("Orb handler has been registered");
    }

    /**
     * Prevents using orbs as regular items
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onOrbUse(PlayerInteractEvent event) {
        ItemStack itemInHand = event.getItem();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            return;
        }

        // Cancel all interactions with orb items
        if (itemInHand.getType() == Material.MAGMA_CREAM &&
                (OrbAPI.isNormalOrb(itemInHand) || OrbAPI.isLegendaryOrb(itemInHand))) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles applying orbs to items
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerUuid = player.getUniqueId();

        // Prevent processing multiple orb applications simultaneously
        if (processingPlayers.contains(playerUuid)) {
            event.setCancelled(true);
            return;
        }

        // Get the cursor and current item
        ItemStack cursor = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        // Check for valid items
        if (cursor == null || currentItem == null ||
                cursor.getType() == Material.AIR || currentItem.getType() == Material.AIR) {
            return;
        }

        // Debug logging to help identify issues
        if (cursor.getType() == Material.MAGMA_CREAM) {
            LOGGER.info("Player " + player.getName() + " clicked with possible orb. Is normal: " +
                    OrbAPI.isNormalOrb(cursor) + ", Is legendary: " + OrbAPI.isLegendaryOrb(cursor) +
                    ", Target valid: " + OrbAPI.isValidItemForOrb(currentItem));
        }

        // Process Legendary Orb
        if (OrbAPI.isLegendaryOrb(cursor) && OrbAPI.isValidItemForOrb(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                LOGGER.info("Applying legendary orb for player " + player.getName());
                // Get player data for bonus rolls
                YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
                int bonusRolls = 0;

                // Clone the current item for processing
                ItemStack itemToUpgrade = currentItem.clone();
                int oldLoreSize = itemToUpgrade.getItemMeta().getLore().size();

                // Apply the legendary orb
                ItemStack newItem = orbProcessor.applyOrbToItem(itemToUpgrade, true, bonusRolls);
                int newLoreSize = newItem.getItemMeta().getLore().size();

                // Consume the orb
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor);
                } else {
                    event.setCursor(null);
                }

                // Update the item in inventory
                event.setCurrentItem(newItem);

                // Show success or failure effect
                if (newLoreSize > oldLoreSize) {
                    showSuccessEffect(player);
                } else {
                    showFailureEffect(player);
                }

                player.sendMessage(ChatColor.GREEN + "Applied a Legendary Orb of Alteration to your item!");

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error applying legendary orb", e);
                player.sendMessage(ChatColor.RED + "An error occurred while applying the orb: " + e.getMessage());
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }

        // Process Normal Orb
        else if (OrbAPI.isNormalOrb(cursor) && OrbAPI.isValidItemForOrb(currentItem)) {
            event.setCancelled(true);
            processingPlayers.add(playerUuid);

            try {
                LOGGER.info("Applying normal orb for player " + player.getName());
                // Clone the current item for processing
                ItemStack itemToUpgrade = currentItem.clone();
                int oldLoreSize = itemToUpgrade.getItemMeta().getLore().size();

                // Apply the normal orb
                ItemStack newItem = orbProcessor.applyOrbToItem(itemToUpgrade, false, 0);
                int newLoreSize = newItem.getItemMeta().getLore().size();

                // Consume the orb
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    event.setCursor(cursor);
                } else {
                    event.setCursor(null);
                }

                // Update the item in inventory
                event.setCurrentItem(newItem);

                // Show success or failure effect
                if (newLoreSize > oldLoreSize) {
                    showSuccessEffect(player);
                } else {
                    showFailureEffect(player);
                }

                player.sendMessage(ChatColor.GREEN + "Applied an Orb of Alteration to your item!");

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error applying normal orb", e);
                player.sendMessage(ChatColor.RED + "An error occurred while applying the orb: " + e.getMessage());
            } finally {
                processingPlayers.remove(playerUuid);
            }
        }
    }

    /**
     * Shows a success effect to the player
     *
     * @param player The player to show the effect to
     */
    private void showSuccessEffect(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
        FireworkUtil.spawnFirework(player.getLocation(), FireworkEffect.Type.BURST, Color.YELLOW);
    }

    /**
     * Shows a failure effect to the player
     *
     * @param player The player to show the effect to
     */
    private void showFailureEffect(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 2.0f, 1.25f);
        ParticleUtil.showFailureEffect(player.getLocation());
    }
}
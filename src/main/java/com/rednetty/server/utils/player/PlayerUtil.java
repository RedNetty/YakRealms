package com.rednetty.server.utils.player;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Utility for easier player operations and management
 * Provides common player interactions and state management
 */
public class PlayerUtil {

    /**
     * Get player by name (case-insensitive)
     * @param name Player name
     * @return Player or null if not found
     */
    public static Player getPlayer(String name) {
        return Bukkit.getPlayerExact(name);
    }

    /**
     * Get player by partial name match
     * @param partialName Partial player name
     * @return Player or null if not found or multiple matches
     */
    public static Player getPlayerByPartialName(String partialName) {
        List<Player> matches = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().toLowerCase().startsWith(partialName.toLowerCase()))
                .collect(Collectors.toList());

        return matches.size() == 1 ? matches.get(0) : null;
    }

    /**
     * Get all players matching partial name
     * @param partialName Partial player name
     * @return List of matching players
     */
    public static List<Player> getPlayersByPartialName(String partialName) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().toLowerCase().contains(partialName.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Check if player has enough inventory space
     * @param player Player to check
     * @param items Items to check space for
     * @return True if player has enough space
     */
    public static boolean hasInventorySpace(Player player, ItemStack... items) {
        int requiredSlots = 0;
        Map<Material, Integer> itemCounts = new HashMap<>();

        // Count required items
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        // Check existing stacks
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem != null && invItem.getType() != Material.AIR) {
                Material type = invItem.getType();
                if (itemCounts.containsKey(type)) {
                    int spaceInStack = invItem.getMaxStackSize() - invItem.getAmount();
                    int needed = itemCounts.get(type);
                    int canFit = Math.min(spaceInStack, needed);
                    itemCounts.put(type, needed - canFit);
                }
            }
        }

        // Count required empty slots
        for (Map.Entry<Material, Integer> entry : itemCounts.entrySet()) {
            if (entry.getValue() > 0) {
                Material type = entry.getKey();
                int maxStack = type.getMaxStackSize();
                requiredSlots += (entry.getValue() + maxStack - 1) / maxStack;
            }
        }

        // Count empty slots
        int emptySlots = 0;
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem == null || invItem.getType() == Material.AIR) {
                emptySlots++;
            }
        }

        return emptySlots >= requiredSlots;
    }

    /**
     * Give items to player, dropping excess if inventory is full
     * @param player Player to give items to
     * @param items Items to give
     * @return List of items that were dropped
     */
    public static List<ItemStack> giveItems(Player player, ItemStack... items) {
        List<ItemStack> dropped = new ArrayList<>();

        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack droppedItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), droppedItem);
                    dropped.add(droppedItem);
                }
            }
        }

        return dropped;
    }

    /**
     * Remove items from player inventory
     * @param player Player to remove items from
     * @param item Item to remove
     * @param amount Amount to remove
     * @return Amount actually removed
     */
    public static int removeItems(Player player, Material item, int amount) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == item) {
                int stackAmount = stack.getAmount();
                int toRemove = Math.min(stackAmount, amount);

                if (toRemove == stackAmount) {
                    contents[i] = null;
                } else {
                    stack.setAmount(stackAmount - toRemove);
                }

                removed += toRemove;
                amount -= toRemove;
            }
        }

        player.getInventory().setContents(contents);
        return removed;
    }

    /**
     * Count items in player inventory
     * @param player Player to count items for
     * @param material Material to count
     * @return Total count of items
     */
    public static int countItems(Player player, Material material) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(item -> item.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    /**
     * Clear player effects
     * @param player Player to clear effects from
     * @param effectTypes Specific effect types to clear (null for all)
     */
    public static void clearEffects(Player player, PotionEffectType... effectTypes) {
        if (effectTypes == null || effectTypes.length == 0) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        } else {
            for (PotionEffectType type : effectTypes) {
                player.removePotionEffect(type);
            }
        }
    }

    /**
     * Apply potion effects safely
     * @param player Player to apply effects to
     * @param effects Effects to apply
     */
    public static void applyEffects(Player player, PotionEffect... effects) {
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect, true);
        }
    }

    /**
     * Heal player to full health and food
     * @param player Player to heal
     * @param clearEffects Whether to clear negative effects
     */
    public static void healPlayer(Player player, boolean clearEffects) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);

        if (clearEffects) {
            clearNegativeEffects(player);
        }
    }

    /**
     * Clear only negative potion effects
     * @param player Player to clear effects from
     */
    public static void clearNegativeEffects(Player player) {
        Set<PotionEffectType> negativeEffects = Set.of(
                PotionEffectType.POISON,
                PotionEffectType.WITHER,
                PotionEffectType.WEAKNESS,
                PotionEffectType.SLOWNESS,
                PotionEffectType.MINING_FATIGUE,
                PotionEffectType.NAUSEA,
                PotionEffectType.BLINDNESS,
                PotionEffectType.HUNGER,
                PotionEffectType.LEVITATION,
                PotionEffectType.UNLUCK,
                PotionEffectType.BAD_OMEN
        );

        for (PotionEffectType type : negativeEffects) {
            if (player.hasPotionEffect(type)) {
                player.removePotionEffect(type);
            }
        }
    }

    /**
     * Send title with subtitle
     * @param player Player to send title to
     * @param title Main title
     * @param subtitle Subtitle
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(
                ChatColor.translateAlternateColorCodes('&', title),
                ChatColor.translateAlternateColorCodes('&', subtitle),
                fadeIn, stay, fadeOut
        );
    }

    /**
     * Send title with default timing
     */
    public static void sendTitle(Player player, String title, String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    /**
     * Play sound to player with location
     * @param player Player to play sound to
     * @param sound Sound to play
     * @param volume Volume (0.0 - 1.0)
     * @param pitch Pitch (0.5 - 2.0)
     */
    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Spawn firework at player location
     * @param player Player location to spawn at
     * @param colors Firework colors
     * @param power Firework power
     */
    public static void spawnFirework(Player player, Color[] colors, int power) {
        org.bukkit.entity.Firework firework = player.getWorld().spawn(player.getLocation(), org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();

        org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                .with(org.bukkit.FireworkEffect.Type.BURST)
                .withColor(colors)
                .withTrail()
                .withFlicker()
                .build();

        meta.addEffect(effect);
        meta.setPower(power);
        firework.setFireworkMeta(meta);
    }

    /**
     * Get players within radius of player
     * @param player Center player
     * @param radius Radius in blocks
     * @param includeSelf Whether to include the center player
     * @return List of players within radius
     */
    public static List<Player> getPlayersWithinRadius(Player player, double radius, boolean includeSelf) {
        List<Player> nearby = player.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distance(player.getLocation()) <= radius)
                .collect(Collectors.toList());

        if (!includeSelf) {
            nearby.remove(player);
        }

        return nearby;
    }

    /**
     * Get random online player
     * @param exclude Players to exclude from selection
     * @return Random player or null if none available
     */
    public static Player getRandomPlayer(Player... exclude) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.removeAll(Arrays.asList(exclude));

        if (players.isEmpty()) return null;

        return players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    /**
     * Check if player is in creative or spectator mode
     * @param player Player to check
     * @return True if in creative or spectator mode
     */
    public static boolean isInNonSurvivalMode(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    /**
     * Reset player completely
     * @param player Player to reset
     */
    public static void resetPlayer(Player player) {
        // Clear inventory
        player.getInventory().clear();
        player.getEnderChest().clear();

        // Reset health and food
        healPlayer(player, true);

        // Reset experience
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);

        // Reset game mode
        player.setGameMode(GameMode.SURVIVAL);

        // Reset flight
        player.setAllowFlight(false);
        player.setFlying(false);

        // Clear fire ticks
        player.setFireTicks(0);
    }

    /**
     * Check if player has permission or is OP
     * @param player Player to check
     * @param permission Permission to check
     * @return True if player has permission or is OP
     */
    public static boolean hasPermissionOrOp(Player player, String permission) {
        return player.isOp() || player.hasPermission(permission);
    }

    /**
     * Get player's ping (reflection-based, may not work on all versions)
     * @param player Player to get ping for
     * @return Ping in milliseconds, -1 if unable to get
     */
    public static int getPing(Player player) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = craftPlayer.getClass().getField("playerConnection").get(craftPlayer);
            return (int) playerConnection.getClass().getField("ping").get(playerConnection);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Check if player's inventory is empty
     * @param player Player to check
     * @param includeArmor Whether to include armor slots
     * @return True if inventory is empty
     */
    public static boolean isInventoryEmpty(Player player, boolean includeArmor) {
        // Check main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }

        if (includeArmor) {
            // Check armor slots
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get player's display name or name if no display name set
     * @param player Player to get name for
     * @return Display name or regular name
     */
    public static String getDisplayName(Player player) {
        String displayName = player.getDisplayName();
        return displayName != null && !displayName.isEmpty() ? displayName : player.getName();
    }
}
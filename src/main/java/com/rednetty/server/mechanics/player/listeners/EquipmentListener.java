package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.mechanics.player.YakPlayer;
import com.rednetty.server.mechanics.player.YakPlayerManager;
import com.rednetty.server.mechanics.player.settings.Toggles;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles equipment-related events including starter kit distribution
 * and equipment upgrades
 */
public class EquipmentListener extends BaseListener {

    private final YakPlayerManager playerManager;

    public EquipmentListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("Equipment listener initialized");
    }

    /**
     * Provides a starter kit to players based on their settings
     *
     * @param player The player to provide kit to
     */
    public static void provideStarterKit(Player player) {
        if (Toggles.isToggled(player, "Disable Kit")) {
            return;
        }

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        PlayerInventory inventory = player.getInventory();

        // Check for existing weapon
        boolean hasWeapon = false;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && (item.getType().name().contains("_SWORD") ||
                    item.getType().name().contains("_AXE"))) {
                hasWeapon = true;
                break;
            }
        }

        // Provide weapon if needed
        if (!hasWeapon) {
            inventory.addItem(createWeapon(1)); // Default tier 1
        }

        // Check for existing armor pieces
        boolean hasHelmet = inventory.getHelmet() != null;
        boolean hasChestplate = inventory.getChestplate() != null;
        boolean hasLeggings = inventory.getLeggings() != null;
        boolean hasBoots = inventory.getBoots() != null;

        // Provide armor if needed
        if (!hasHelmet) {
            inventory.setHelmet(createArmorItem(Material.LEATHER_HELMET, 1, "Leather Helmet"));
        }
        if (!hasChestplate) {
            inventory.setChestplate(createArmorItem(Material.LEATHER_CHESTPLATE, 1, "Leather Chestplate"));
        }
        if (!hasLeggings) {
            inventory.setLeggings(createArmorItem(Material.LEATHER_LEGGINGS, 1, "Leather Leggings"));
        }
        if (!hasBoots) {
            inventory.setBoots(createArmorItem(Material.LEATHER_BOOTS, 1, "Leather Boots"));
        }

        // Provide health potion
        inventory.addItem(createHealthPotion());

        // TODO: Add mount/horse system once implemented
        /* In original code:
        if (Horses.horseTier.containsKey(player)) {
            player.getInventory().addItem(Horses.createMount(Horses.horseTier.get(player), false));
        }
        */

        // Initialize player health
        player.setMaxHealth(50.0);
        player.setHealth(50.0);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
    }

    /**
     * Create a weapon item for the starter kit
     *
     * @param tier The tier level of the weapon
     * @return The created weapon ItemStack
     */
    private static ItemStack createWeapon(int tier) {
        Random random = new Random();
        int min = random.nextInt(2) + 4 + (tier * 2);
        int max = random.nextInt(2) + 8 + (tier * 2);

        Material material = Material.WOODEN_SWORD;
        String weaponName = "Training Sword";

        ItemStack weapon = new ItemStack(material);
        ItemMeta weaponMeta = weapon.getItemMeta();
        weaponMeta.setDisplayName(ChatColor.WHITE + weaponName);

        List<String> weaponLore = new ArrayList<>();
        weaponLore.add(ChatColor.RED + "DMG: " + min + " - " + max);
        weaponLore.add(getRarityText(tier));

        weaponMeta.setLore(weaponLore);
        weapon.setItemMeta(weaponMeta);

        // Set as untradable
        // TODO: Implement when ItemAPI is converted
        // ItemAPI.setUntradeable(weapon);

        return weapon;
    }

    /**
     * Create an armor item for the starter kit
     *
     * @param material The armor material
     * @param tier     The tier level of the armor
     * @param name     The display name of the armor
     * @return The created armor ItemStack
     */
    private static ItemStack createArmorItem(Material material, int tier, String name) {
        ItemStack armor = new ItemStack(material);
        ItemMeta armorMeta = armor.getItemMeta();
        armorMeta.setDisplayName(ChatColor.WHITE + name);

        List<String> armorLore = new ArrayList<>();
        armorLore.add(ChatColor.RED + "ARMOR: " + (tier * 2));
        armorLore.add(ChatColor.RED + "HP: +" + (tier * 10));
        armorLore.add(ChatColor.RED + "ENERGY REGEN: +3%");
        armorLore.add(getRarityText(tier));

        armorMeta.setLore(armorLore);
        armor.setItemMeta(armorMeta);

        // Set as untradable
        // TODO: Implement when ItemAPI is converted
        // ItemAPI.setUntradeable(armor);

        return armor;
    }

    /**
     * Create a health potion
     *
     * @return The created health potion ItemStack
     */
    private static ItemStack createHealthPotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        ItemMeta potionMeta = potion.getItemMeta();
        potionMeta.setDisplayName(ChatColor.GREEN + "Health Potion");

        List<String> potionLore = new ArrayList<>();
        potionLore.add(ChatColor.RED + "Heals 75 HP");
        potionLore.add(ChatColor.GRAY + "Untradeable");

        potionMeta.setLore(potionLore);
        potion.setItemMeta(potionMeta);

        return potion;
    }

    /**
     * Get the rarity text for an item based on tier
     *
     * @param tier The tier level
     * @return The formatted rarity text
     */
    private static String getRarityText(int tier) {
        switch (tier) {
            case 1:
                return ChatColor.GRAY + "Common";
            case 2:
                return ChatColor.GREEN + "Uncommon";
            case 3:
                return ChatColor.AQUA + "Rare";
            case 4:
                return ChatColor.YELLOW + "Unique";
            default:
                return ChatColor.WHITE + "Unknown";
        }
    }

    /**
     * Track sound effects for weapon equipping
     */
    @EventHandler
    public void onWeaponSwitch(org.bukkit.event.player.PlayerItemHeldEvent event) {
        if (isPatchLockdown()) {
            event.setCancelled(true);
            return;
        }

        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (newItem != null && isWeapon(newItem.getType())) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.5f);
        }
    }

    /**
     * Check if a material is a weapon
     */
    private boolean isWeapon(Material material) {
        String typeName = material.name();
        return typeName.contains("_SWORD") ||
                typeName.contains("_AXE") ||
                typeName.contains("_HOE") ||
                typeName.contains("_SHOVEL");
    }

    /**
     * Provide starter kit on player respawn
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Delay kit provision to ensure player is fully respawned
        new BukkitRunnable() {
            @Override
            public void run() {
                provideStarterKit(player);
            }
        }.runTaskLater(plugin, 5L);
    }
}
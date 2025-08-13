package com.rednetty.server.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.mechanics.item.HealthPotion;
import com.rednetty.server.mechanics.item.scroll.ItemAPI;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 *  EquipmentListener - Properly coordinates with DeathMechanics respawn system
 *
 * CRITICAL FIXES:
 * - Changed timing to run AFTER DeathMechanics completely finishes
 * - Better detection of when items have been restored vs when starter kit is needed
 * - Proper coordination to prevent interference with alignment-based death mechanics
 * - Enhanced logging to debug respawn item restoration vs starter kit provision
 */
public class EquipmentListener extends BaseListener {

    private final YakPlayerManager playerManager;

    public EquipmentListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public void initialize() {
        logger.info(" Equipment listener initialized with improved death mechanics coordination");
    }

    /**
     * Only provide starter kit when appropriate - not during respawn with kept items
     */
    public static void provideStarterKit(Player player) {
        // Check if kit is disabled
        if (Toggles.isToggled(player, "Disable Kit")) {
            return;
        }

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        YakRealms.log(": Providing starter kit to " + player.getName());

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

        // Initialize player health
        player.setMaxHealth(50.0);
        player.setHealth(50.0);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
    }

    /**
     * CRITICAL FIX: Respawn handler that properly coordinates with DeathMechanics
     *
     * This now runs AFTER DeathMechanics has completely finished processing,
     * and only provides starter kit if the player truly has no items.
     */
    @EventHandler(priority = EventPriority.MONITOR) // Monitor priority runs last
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // CRITICAL FIX: Use a shorter delay first to check what DeathMechanics is doing
        // We'll check at 60L (3 seconds) to see the state after DeathMechanics should have run
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer == null) return;

                try {
                    // DEBUG: Check what's happening
                    boolean hasAnyItems = hasAnyItems(player);
                    boolean hasSignificantItems = hasSignificantItems(player);
                    boolean hasRespawnItems = yakPlayer.hasRespawnItems();

                    // Get death timestamp to determine if this was a recent death
                    long deathTimestamp = yakPlayer.getDeathTimestamp();
                    long timeSinceDeath = deathTimestamp > 0 ?
                            System.currentTimeMillis() - deathTimestamp : Long.MAX_VALUE;
                    boolean wasRecentDeath = timeSinceDeath < 60000; // Within last minute

                    logger.info(" DEBUG: EquipmentListener check for " + player.getName() +
                            " - hasAnyItems: " + hasAnyItems +
                            ", hasSignificantItems: " + hasSignificantItems +
                            ", hasRespawnItems: " + hasRespawnItems +
                            ", wasRecentDeath: " + wasRecentDeath +
                            ", timeSinceDeath: " + (timeSinceDeath/1000) + "s");

                    // DEBUG: Log current inventory contents
                    PlayerInventory inv = player.getInventory();
                    int itemCount = 0;
                    int armorCount = 0;

                    for (ItemStack item : inv.getContents()) {
                        if (item != null && item.getType() != Material.AIR) {
                            itemCount++;
                            logger.info(" DEBUG: Inventory item: " + item.getType() + " x" + item.getAmount());
                        }
                    }

                    for (ItemStack armor : inv.getArmorContents()) {
                        if (armor != null && armor.getType() != Material.AIR) {
                            armorCount++;
                            logger.info(" DEBUG: Armor item: " + armor.getType());
                        }
                    }

                    logger.info(" DEBUG: Current inventory state - " + itemCount + " items, " + armorCount + " armor pieces");

                    // CRITICAL FIX: Only provide starter kit if player has NO items at all
                    // and this wasn't a recent death (which would have kept items)
                    if (!hasAnyItems) {
                        if (wasRecentDeath) {
                            // Recent death but no items - this might be chaotic alignment
                            String alignment = yakPlayer.getAlignment();
                            logger.info(" DEBUG: Recent death with no items for " + player.getName() +
                                    " (alignment: " + alignment + ") - providing starter kit");
                        } else {
                            // No recent death and no items - probably new player or long ago death
                            logger.info(" DEBUG: No items and no recent death for " + player.getName() +
                                    " - providing starter kit");
                        }
                        provideStarterKit(player);
                    } else if (!hasSignificantItems && !wasRecentDeath) {
                        // Has some items but not significant ones, and no recent death
                        logger.info(" DEBUG: Has minor items but no significant gear for " + player.getName() +
                                " - providing starter kit");
                        provideStarterKit(player);
                    } else {
                        // Has items, don't give starter kit
                        logger.info(" DEBUG: Not providing starter kit to " + player.getName() +
                                " - player has items" + (wasRecentDeath ? " (recent death)" : ""));
                    }

                } catch (Exception e) {
                    logger.warning(" DEBUG: Error in respawn processing for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    // Fallback: provide starter kit on error
                    provideStarterKit(player);
                }
            }
        }.runTaskLater(plugin, 60L); // CRITICAL FIX: 3 seconds delay to see what DeathMechanics did
    }

    /**
     * Check if player has any items in inventory or armor slots
     */
    private boolean hasAnyItems(Player player) {
        PlayerInventory inv = player.getInventory();

        // Check main inventory + hotbar
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                return true;
            }
        }

        // Check armor slots
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.getAmount() > 0) {
                return true;
            }
        }

        // Check offhand
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR && offhand.getAmount() > 0) {
            return true;
        }

        return false;
    }

    /**
     * Check if player has significant items (weapons, armor, valuable items)
     */
    private boolean hasSignificantItems(Player player) {
        PlayerInventory inv = player.getInventory();

        // Check for weapons
        for (ItemStack item : inv.getContents()) {
            if (item != null && isWeaponItem(item)) {
                return true;
            }
        }

        // Check for any armor
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.getAmount() > 0) {
                return true;
            }
        }

        // Check for valuable items (gems, potions, etc.)
        for (ItemStack item : inv.getContents()) {
            if (item != null && isValuableItem(item)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an item is a weapon
     */
    private boolean isWeaponItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String typeName = item.getType().name();
        return typeName.contains("_SWORD") ||
                typeName.contains("_AXE") ||
                typeName.contains("_HOE") ||
                typeName.contains("_SHOVEL") ||
                typeName.contains("BOW") ||
                typeName.contains("CROSSBOW") ||
                typeName.contains("TRIDENT");
    }

    /**
     * Check if an item is valuable (should prevent starter kit)
     */
    private boolean isValuableItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        Material type = item.getType();

        // Gems and valuable materials
        if (type == Material.EMERALD || type == Material.DIAMOND ||
                type == Material.GOLD_INGOT || type == Material.IRON_INGOT) {
            return true;
        }

        // Potions
        if (type == Material.POTION || type == Material.SPLASH_POTION ||
                type == Material.LINGERING_POTION) {
            return true;
        }

        // Check for custom items by display name
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            return displayName.contains("Legendary") ||
                    displayName.contains("Epic") ||
                    displayName.contains("Rare") ||
                    displayName.contains("Potion") ||
                    displayName.contains("Orb");
        }

        return false;
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

    // ==================== ITEM CREATION METHODS ====================

    /**
     * Create a weapon item for the starter kit
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

        weaponMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Hide attributes
        weaponMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        weaponMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        ItemAPI.setUntradable(weapon);

        return weapon;
    }

    /**
     * Create an armor item for the starter kit
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
        armorMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Hide attributes
        armorMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        armorMeta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        ItemAPI.setUntradable(armor);

        return armor;
    }

    /**
     * Create a health potion
     */
    private static ItemStack createHealthPotion() {
        return HealthPotion.getPotionByTier(1);
    }

    /**
     * Get the rarity text for an item based on tier
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
}
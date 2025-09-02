package com.rednetty.server.core.mechanics.player.listeners;

import com.rednetty.server.YakRealms;
import com.rednetty.server.core.mechanics.item.HealthPotion;
import com.rednetty.server.core.mechanics.item.scroll.ItemAPI;
import com.rednetty.server.core.mechanics.player.YakPlayer;
import com.rednetty.server.core.mechanics.player.YakPlayerManager;
import com.rednetty.server.core.mechanics.player.settings.Toggles;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
 * FIXED EquipmentListener - Properly coordinates with DeathMechanics alignment-based respawn system
 *
 * CRITICAL FIXES:
 * 1. Much longer delay to ensure DeathMechanics completely finishes first
 * 2. Better detection of alignment-based kept items vs starter kit need
 * 3. Enhanced logging to debug starter kit vs alignment respawn conflicts
 * 4. Respects DeathMechanics item restoration completely
 * 5. Only provides starter kit for truly empty inventories or new players
 */
public class EquipmentListener extends BaseListener {

    private final YakPlayerManager playerManager;

    public EquipmentListener() {
        super();
        this.playerManager = YakPlayerManager.getInstance();
    }

    @Override
    public void initialize() {
        logger.info("✅ FIXED Equipment listener initialized with proper death mechanics coordination");
    }

    /**
     * Only provide starter kit when appropriate - not during respawn with kept items
     */
    public static void provideStarterKit(Player player) {
        // Check if kit is disabled
        if (Toggles.isToggled(player, "Disable Kit")) {
            YakRealms.log("🚫 Starter kit disabled for " + player.getName());
            return;
        }

        YakPlayer yakPlayer = YakPlayerManager.getInstance().getPlayer(player);
        if (yakPlayer == null) return;

        YakRealms.log("🎁 Providing starter kit to " + player.getName());

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
            YakRealms.log("  ⚔️ Added starter weapon");
        }

        // Check for existing armor pieces
        boolean hasHelmet = inventory.getHelmet() != null;
        boolean hasChestplate = inventory.getChestplate() != null;
        boolean hasLeggings = inventory.getLeggings() != null;
        boolean hasBoots = inventory.getBoots() != null;

        // Provide armor if needed
        if (!hasHelmet) {
            inventory.setHelmet(createArmorItem(Material.LEATHER_HELMET, 1, "Leather Helmet"));
            YakRealms.log("  🛡️ Added starter helmet");
        }
        if (!hasChestplate) {
            inventory.setChestplate(createArmorItem(Material.LEATHER_CHESTPLATE, 1, "Leather Chestplate"));
            YakRealms.log("  🛡️ Added starter chestplate");
        }
        if (!hasLeggings) {
            inventory.setLeggings(createArmorItem(Material.LEATHER_LEGGINGS, 1, "Leather Leggings"));
            YakRealms.log("  🛡️ Added starter leggings");
        }
        if (!hasBoots) {
            inventory.setBoots(createArmorItem(Material.LEATHER_BOOTS, 1, "Leather Boots"));
            YakRealms.log("  🛡️ Added starter boots");
        }

        // Provide health potion
        inventory.addItem(createHealthPotion());
        YakRealms.log("  🧪 Added health potion");

        player.setHealthScale(20.0);
        player.setHealthScaled(true);

        YakRealms.log("✅ Starter kit provided to " + player.getName());
    }

    /**
     * CRITICAL FIX: Respawn handler with MUCH longer delay and better detection
     *
     * This now waits much longer (8 seconds) to ensure DeathMechanics has completely
     * finished its alignment-based item restoration before checking if starter kit is needed.
     */
    @EventHandler(priority = EventPriority.MONITOR) // Monitor priority runs last
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // CRITICAL FIX: Use longer delay but not excessive (5 seconds vs 8)
        // We'll check at 100L (5 seconds) to ensure alignment-based item restoration is done
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                YakPlayer yakPlayer = playerManager.getPlayer(player);
                if (yakPlayer == null) return;

                try {
                    // ENHANCED: More thorough analysis of player's current state
                    boolean hasAnyItems = hasAnyItems(player);
                    boolean hasSignificantItems = hasSignificantItems(player);
                    boolean hasArmorItems = hasArmorItems(player);
                    boolean hasWeaponItems = hasWeaponItems(player);
                    boolean hasValuableItems = hasValuableItems(player);
                    boolean hasRespawnItems = yakPlayer.hasRespawnItems();

                    // Get death info for context
                    long deathTimestamp = yakPlayer.getDeathTimestamp();
                    long timeSinceDeath = deathTimestamp > 0 ?
                            System.currentTimeMillis() - deathTimestamp : Long.MAX_VALUE;
                    boolean wasRecentDeath = timeSinceDeath < 120000; // Within last 2 minutes
                    String alignment = yakPlayer.getAlignment();

                    // Enhanced combat logout state detection
                    YakPlayer.CombatLogoutState combatState = yakPlayer.getCombatLogoutState();
                    boolean wasCombatLogout = (combatState == YakPlayer.CombatLogoutState.PROCESSED ||
                            combatState == YakPlayer.CombatLogoutState.COMPLETED);

                    logger.info("🔍 ENHANCED EquipmentListener analysis for " + player.getName() + ":");
                    logger.info("  - hasAnyItems: " + hasAnyItems);
                    logger.info("  - hasSignificantItems: " + hasSignificantItems);
                    logger.info("  - hasArmorItems: " + hasArmorItems);
                    logger.info("  - hasWeaponItems: " + hasWeaponItems);
                    logger.info("  - hasValuableItems: " + hasValuableItems);
                    logger.info("  - hasRespawnItems: " + hasRespawnItems);
                    logger.info("  - wasRecentDeath: " + wasRecentDeath + " (" + (timeSinceDeath/1000) + "s ago)");
                    logger.info("  - alignment: " + alignment);
                    logger.info("  - wasCombatLogout: " + wasCombatLogout);
                    logger.info("  - combatLogoutState: " + combatState);

                    // Log current inventory state in detail
                    logDetailedInventoryState(player);

                    // ENHANCED DECISION LOGIC: Much more careful about when to provide starter kit
                    boolean shouldProvideKit = false;
                    String reason = "";

                    if (!hasAnyItems) {
                        if (wasRecentDeath) {
                            // Recent death with no items
                            if ("CHAOTIC".equals(alignment)) {
                                // Chaotic should only keep permanent untradeable/quest items
                                // If they have truly nothing, they need starter kit
                                shouldProvideKit = true;
                                reason = "chaotic death - lost everything (as expected)";
                            } else if (wasCombatLogout) {
                                // Combat logout should have kept some items unless chaotic
                                logger.info("⚠️ Combat logout but no items - may be chaotic or error");
                                shouldProvideKit = true;
                                reason = "combat logout with no items - may need kit";
                            } else {
                                // Normal death with no items - unusual for lawful/neutral
                                logger.info("⚠️ Normal death but no items - unusual for " + alignment);
                                shouldProvideKit = true;
                                reason = "normal death with no items - may be error or chaotic-like behavior";
                            }
                        } else {
                            // No recent death and no items - new player or long ago
                            shouldProvideKit = true;
                            reason = "no recent death and no items - new player or old death";
                        }
                    } else if (hasAnyItems && !hasSignificantItems) {
                        // Has some items but nothing important
                        if (!wasRecentDeath) {
                            // Only provide kit if no recent death and items aren't significant
                            shouldProvideKit = true;
                            reason = "has minor items but no significant gear, no recent death";
                        } else {
                            // Recent death with minor items - might be permanent untradeable from chaotic
                            if ("CHAOTIC".equals(alignment) && !hasWeaponItems && !hasArmorItems) {
                                // Chaotic player with only permanent items (no weapons/armor) needs kit
                                shouldProvideKit = true;
                                reason = "chaotic with only permanent items - needs basic gear";
                            } else {
                                reason = "has minor items from recent death - not providing kit";
                            }
                        }
                    } else if (hasSignificantItems) {
                        // Has important items - DeathMechanics worked correctly
                        reason = "has significant items - DeathMechanics restored properly";
                    }

                    logger.info("🎯 Decision for " + player.getName() + ": " +
                            (shouldProvideKit ? "PROVIDE STARTER KIT" : "NO STARTER KIT") +
                            " - Reason: " + reason);

                    if (shouldProvideKit) {
                        provideStarterKit(player);
                    }

                } catch (Exception e) {
                    logger.warning("❌ Error in enhanced respawn processing for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    // Fallback: provide starter kit on error for safety
                    logger.info("🆘 Providing starter kit as error fallback for " + player.getName());
                    provideStarterKit(player);
                }
            }
        }.runTaskLater(plugin, 100L); // CRITICAL FIX: 5 seconds delay for DeathMechanics completion
    }

    /**
     * Log detailed inventory state for debugging
     */
    private void logDetailedInventoryState(Player player) {
        PlayerInventory inv = player.getInventory();

        logger.info("📦 Detailed inventory state for " + player.getName() + ":");

        // Log armor slots
        logger.info("  🛡️ Armor slots:");
        logger.info("    - Helmet: " + (inv.getHelmet() != null ? getItemDisplayName(inv.getHelmet()) : "empty"));
        logger.info("    - Chestplate: " + (inv.getChestplate() != null ? getItemDisplayName(inv.getChestplate()) : "empty"));
        logger.info("    - Leggings: " + (inv.getLeggings() != null ? getItemDisplayName(inv.getLeggings()) : "empty"));
        logger.info("    - Boots: " + (inv.getBoots() != null ? getItemDisplayName(inv.getBoots()) : "empty"));

        // Log hotbar (first 9 slots)
        logger.info("  🎯 Hotbar slots:");
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < 9 && i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() != Material.AIR) {
                logger.info("    - Slot " + i + ": " + getItemDisplayName(item));
            }
        }

        // Count total items
        int totalItems = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                totalItems++;
            }
        }
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                totalItems++;
            }
        }

        logger.info("  📊 Total non-empty slots: " + totalItems);
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
     * Check if player has armor items specifically
     */
    private boolean hasArmorItems(Player player) {
        PlayerInventory inv = player.getInventory();

        // Check equipped armor slots
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR && armor.getAmount() > 0) {
                return true;
            }
        }

        // Check for armor in inventory
        for (ItemStack item : inv.getContents()) {
            if (item != null && isArmorItem(item)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if player has weapon items specifically
     */
    private boolean hasWeaponItems(Player player) {
        PlayerInventory inv = player.getInventory();

        // Check all inventory slots for weapons
        for (ItemStack item : inv.getContents()) {
            if (item != null && isWeaponItem(item)) {
                return true;
            }
        }

        // Check offhand
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && isWeaponItem(offhand)) {
            return true;
        }

        return false;
    }

    /**
     * Check if player has significant items (weapons, armor, valuable items)
     */
    private boolean hasSignificantItems(Player player) {
        return hasWeaponItems(player) || hasArmorItems(player) || hasValuableItems(player);
    }

    /**
     * Check if player has valuable items (gems, potions, etc.)
     */
    private boolean hasValuableItems(Player player) {
        PlayerInventory inv = player.getInventory();

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
     * Check if an item is armor
     */
    private boolean isArmorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String typeName = item.getType().name();
        return typeName.contains("_HELMET") ||
                typeName.contains("_CHESTPLATE") ||
                typeName.contains("_LEGGINGS") ||
                typeName.contains("_BOOTS");
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
                    displayName.contains("Orb") ||
                    displayName.contains("Gem");
        }

        return false;
    }

    /**
     * Get item display name for logging
     */
    private String getItemDisplayName(ItemStack item) {
        if (item == null) return "null";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name() + " x" + item.getAmount();
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
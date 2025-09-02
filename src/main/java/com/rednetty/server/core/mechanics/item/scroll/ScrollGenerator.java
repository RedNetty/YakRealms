package com.rednetty.server.core.mechanics.item.scroll;

import com.rednetty.server.core.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.nbt.NBTAccessor;
import com.rednetty.server.utils.ui.GradientColors;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates various types of scrolls for the game
 */
public class ScrollGenerator {

    /**
     * Creates a protection scroll of the specified tier
     *
     * @param tier The tier of the scroll (0-5)
     * @return The created protection scroll
     */
    public ItemStack createProtectionScroll(int tier) {
        if (tier < 0 || tier > 5) {
            tier = 0; // Default to tier 0 for invalid input
        }

        ItemStack scroll = new ItemStack(Material.MAP);
        ItemMeta meta = scroll.getItemMeta();
        String displayName = ChatColor.WHITE.toString() + ChatColor.BOLD + "WHITE SCROLL: ";

        switch (tier) {
            case 0:
                displayName = displayName + ChatColor.WHITE + "Protect Leather Equipment";
                break;
            case 1:
                displayName = displayName + ChatColor.GREEN + "Protect Chainmail Equipment";
                break;
            case 2:
                displayName = displayName + ChatColor.AQUA + "Protect Iron Equipment";
                break;
            case 3:
                displayName = displayName + ChatColor.LIGHT_PURPLE + "Protect Diamond Equipment";
                break;
            case 4:
                displayName = displayName + GradientColors.getUniqueGradient("Protect Gold Equipment");
                break;
            case 5:
                displayName = displayName + GradientColors.getT6Gradient("Protect Netherite Equipment");
                break;
        }

        meta.setDisplayName(displayName);
        int realTier = tier + 1;
        String realTierString = Integer.toString(realTier);

        List<String> lore = Arrays.asList(
                "",
                ChatColor.GRAY.toString() + ChatColor.ITALIC + "Apply to any T" + realTierString + " item to ",
                ChatColor.GRAY.toString() + ChatColor.ITALIC + ChatColor.UNDERLINE + "prevent" + ChatColor.GRAY.toString() + ChatColor.ITALIC + " it from being destroyed",
                ChatColor.GRAY.toString() + ChatColor.ITALIC + "if the next enchantment scroll (up to +12) fails"
        );

        meta.setLore(lore);
        scroll.setItemMeta(meta);

        // Add glowing effect for tier 3+
        if (tier >= 3) {
            Enchants.addGlow(scroll);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            scroll.setItemMeta(meta);
        }

        // Add NBT data to identify as protection scroll
        NBTAccessor nbt = new NBTAccessor(scroll);
        nbt.setString("itemProtectionScroll", "true");
        nbt.setInt("itemProtectionScrollTier", tier);

        return nbt.update();
    }

    /**
     * Creates an enhancement scroll of the specified tier
     *
     * @param tier The tier of the scroll (1-6)
     * @param type The type of scroll (0 for weapon, 1 for armor)
     * @return The created enhancement scroll
     */
    public ItemStack createEnhancementScroll(int tier, int type) {
        if (tier < 1 || tier > 6) {
            tier = 1; // Default to tier 1 for invalid input
        }

        ItemStack scroll = new ItemStack(Material.MAP);
        ItemMeta meta = scroll.getItemMeta();
        ArrayList<String> lore = new ArrayList<>();
        String name = "";

        // Set name and color based on tier
        switch (tier) {
            case 1:
                name = ChatColor.WHITE + " Enchant ";
                break;
            case 2:
                name = ChatColor.GREEN + " Enchant ";
                break;
            case 3:
                name = ChatColor.AQUA + " Enchant Iron";
                break;
            case 4:
                name = ChatColor.LIGHT_PURPLE + " Enchant Diamond";
                break;
            case 5:
                name = GradientColors.getUniqueGradient(" Enchant Gold");
                break;
            case 6:
                name = GradientColors.getT6Gradient(" Enchant Nether Forged");
                break;
        }

        // Add material type for tier 1-2
        if (tier == 1) {
            if (type == 0) {
                name = name + "Wooden";
            } else if (type == 1) {
                name = name + "Leather";
            }
        } else if (tier == 2) {
            if (type == 0) {
                name = name + "Stone";
            } else if (type == 1) {
                name = name + "Chainmail";
            }
        }

        // Set scroll type (weapon or armor)
        if (type == 0) {
            name = name + " Weapon";
            lore.add(ChatColor.RED + "+5% DMG");
            lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "Weapon will VANISH if enchant above +3 FAILS.");
        } else if (type == 1) {
            name = name + " Armor";
            lore.add(ChatColor.RED + "+5% HP");
            lore.add(ChatColor.RED + "+5% HP REGEN");
            lore.add(ChatColor.GRAY + "   - OR -");
            lore.add(ChatColor.RED + "+1% ENERGY REGEN");
            lore.add(ChatColor.GRAY.toString() + ChatColor.ITALIC + "Armor will VANISH if enchant above +3 FAILS.");
        }

        meta.setDisplayName(ChatColor.WHITE.toString() + ChatColor.BOLD + "Scroll:" + name);
        meta.setLore(lore);
        scroll.setItemMeta(meta);

        // Add glowing effect for higher tier scrolls
        if (tier >= 4) {
            Enchants.addGlow(scroll);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            scroll.setItemMeta(meta);
        }

        // Add NBT data to identify as enhancement scroll
        NBTAccessor nbt = new NBTAccessor(scroll);
        nbt.setString("scrollType", type == 0 ? "weapon_enhancement" : "armor_enhancement");
        nbt.setInt("tier", tier);
        nbt.setInt("scrollEnhancementType", type);

        return nbt.update();
    }

    /**
     * Creates an armor enhancement scroll of the specified tier
     *
     * @param tier The tier of the scroll (1-6)
     * @return The created armor enhancement scroll
     */
    public ItemStack createArmorEnhancementScroll(int tier) {
        return createEnhancementScroll(tier, 1);
    }

    /**
     * Creates a weapon enhancement scroll of the specified tier
     *
     * @param tier The tier of the scroll (1-6)
     * @return The created weapon enhancement scroll
     */
    public ItemStack createWeaponEnhancementScroll(int tier) {
        return createEnhancementScroll(tier, 0);
    }

    /**
     * Get the price of a protection scroll
     *
     * @param tier The tier of the scroll
     * @return The price in gems
     */
    public int getProtectionScrollPrice(int tier) {
        switch (tier) {
            case 0:
                return 250;
            case 1:
                return 500;
            case 2:
                return 1000;
            case 3:
                return 2500;
            case 4:
                return 10000;
            case 5:
                return 25000; // Netherite protection scrolls are expensive
            default:
                return 250;
        }
    }

    /**
     * Get the price of an enhancement scroll
     *
     * @param tier The tier of the scroll
     * @param type The type (0 for weapon, 1 for armor)
     * @return The price in gems
     */
    public int getEnhancementScrollPrice(int tier, int type) {
        int price = 0;

        switch (tier) {
            case 1:
                price = 50;
                break;
            case 2:
                price = 150;
                break;
            case 3:
                price = 250;
                break;
            case 4:
                price = 350;
                break;
            case 5:
                price = 500;
                break;
            case 6:
                price = 1500; // Netherite enhancement scrolls are more expensive
                break;
            default:
                price = 50;
        }

        // Weapon scrolls cost more
        if (type == 0) {
            price = (int) (price * 1.5);
        }

        return price;
    }

    /**
     * Get the price of an armor enhancement scroll
     *
     * @param tier The tier of the scroll
     * @return The price in gems
     */
    public int getArmorEnhancementScrollPrice(int tier) {
        return getEnhancementScrollPrice(tier, 1);
    }

    /**
     * Get the price of a weapon enhancement scroll
     *
     * @param tier The tier of the scroll
     * @return The price in gems
     */
    public int getWeaponEnhancementScrollPrice(int tier) {
        return getEnhancementScrollPrice(tier, 0);
    }
}
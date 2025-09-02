package com.rednetty.server.core.mechanics.item;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import java.util.Arrays;

public enum HealthPotion {
    MINOR_HEALTH(1, PotionType.REGENERATION, ChatColor.WHITE + "Minor Health Potion", ChatColor.GREEN, 15),
    HEALTH(2, PotionType.HEALING, ChatColor.GREEN + "Health Potion", ChatColor.AQUA, 75),
    MAJOR_HEALTH(3, PotionType.STRENGTH, ChatColor.AQUA + "Major Health Potion", ChatColor.AQUA, 300),
    SUPERIOR_HEALTH(4, PotionType.HARMING, ChatColor.LIGHT_PURPLE + "Superior Health Potion", ChatColor.YELLOW, 800),
    LEGENDARY_HEALTH(5, PotionType.FIRE_RESISTANCE, ChatColor.YELLOW + "Legendary Health Potion", ChatColor.YELLOW, 1600),
    CHILLED_HEALTH(6, PotionType.SWIFTNESS, ChatColor.BLUE + "Chilled Health Potion", ChatColor.BLUE, 3200);

    private final int tier;
    private final PotionType type;
    private final String displayName;
    private final ChatColor loreColor;
    private final int healthRestore;

    HealthPotion(int tier, PotionType type, String displayName, ChatColor loreColor, int healthRestore) {
        this.tier = tier;
        this.type = type;
        this.displayName = displayName;
        this.loreColor = loreColor;
        this.healthRestore = healthRestore;
    }

    public int getTier() {
        return tier;
    }

    public int getHealthRestore() {
        return healthRestore;
    }

    public ItemStack createItemStack() {
        ItemStack itemStack = new ItemStack(Material.POTION, 1);
        PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();

        // Set the base potion type using the modern method
        potionMeta.setBasePotionType(type);

        potionMeta.setDisplayName(displayName);
        String lore = ChatColor.GRAY + "A potion that restores " + loreColor + healthRestore + "HP";
        potionMeta.setLore(Arrays.asList(lore));

        // Hide all item flags
        for (ItemFlag itemFlag : ItemFlag.values()) {
            potionMeta.addItemFlags(itemFlag);
        }

        itemStack.setItemMeta(potionMeta);
        return itemStack;
    }

    public static HealthPotion fromTier(int tier) {
        for (HealthPotion potion : values()) {
            if (potion.tier == tier) {
                return potion;
            }
        }
        return null;
    }

    public static ItemStack getPotionByTier(int tier) {
        HealthPotion potion = fromTier(tier);
        return potion != null ? potion.createItemStack() : null;
    }
}
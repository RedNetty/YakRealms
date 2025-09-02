package com.rednetty.server.utils.items;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for creating and manipulating ItemStacks.
 */
public class ItemUtils {
    public ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ChatColor.GOLD + player.getDisplayName());
            head.setItemMeta(meta);
        }
        return head;
    }

    public ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set the display name with color codes
            if (name != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            // Set the lore with color codes
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Adds enchantments to an ItemStack.
     *
     * @param item                   The ItemStack to enchant.
     * @param enchantments           A map of Enchantments and their levels.
     * @param ignoreLevelRestriction If true, allows enchantments beyond their normal maximum levels.
     */
    public void addEnchantments(ItemStack item, Map<Enchantment, Integer> enchantments, boolean ignoreLevelRestriction) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), ignoreLevelRestriction);
            }
            item.setItemMeta(meta);
        }
    }

    /**
     * Removes specific enchantments from an ItemStack.
     *
     * @param item         The ItemStack to modify.
     * @param enchantments The enchantments to remove.
     */
    public void removeEnchantments(ItemStack item, Enchantment... enchantments) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (Enchantment enchantment : enchantments) {
                meta.removeEnchant(enchantment);
            }
            item.setItemMeta(meta);
        }
    }

    /**
     * Adds ItemFlags to hide certain attributes from an ItemStack's tooltip.
     *
     * @param item      The ItemStack to modify.
     * @param itemFlags The ItemFlags to add.
     */
    public void addItemFlags(ItemStack item, ItemFlag... itemFlags) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(itemFlags);
            item.setItemMeta(meta);
        }
    }

    /**
     * Removes specific ItemFlags from an ItemStack.
     *
     * @param item      The ItemStack to modify.
     * @param itemFlags The ItemFlags to remove.
     */
    public void removeItemFlags(ItemStack item, ItemFlag... itemFlags) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.removeItemFlags(itemFlags);
            item.setItemMeta(meta);
        }
    }

    /**
     * Sets an ItemStack to be unbreakable.
     *
     * @param item        The ItemStack to modify.
     * @param unbreakable True to make the item unbreakable.
     */
    public void setUnbreakable(ItemStack item, boolean unbreakable) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
            item.setItemMeta(meta);
        }
    }

    /**
     * Adds a glowing effect to an ItemStack without showing enchantments.
     *
     * @param item The ItemStack to modify.
     */
    public void addGlow(ItemStack item) {
        addEnchantments(item, Collections.singletonMap(Enchantment.INFINITY, 1), true);
        addItemFlags(item, ItemFlag.HIDE_ENCHANTS);
    }

    /**
     * Removes the glowing effect from an ItemStack.
     *
     * @param item The ItemStack to modify.
     */
    public void removeGlow(ItemStack item) {
        removeEnchantments(item, Enchantment.INFINITY);
        removeItemFlags(item, ItemFlag.HIDE_ENCHANTS);
    }

    /**
     * Clones an ItemStack.
     *
     * @param item The ItemStack to clone.
     * @return A new ItemStack that is a clone of the original.
     */
    public ItemStack cloneItem(ItemStack item) {
        return item.clone();
    }

    /**
     * Compares two ItemStacks for equality, including their metadata.
     *
     * @param item1 The first ItemStack.
     * @param item2 The second ItemStack.
     * @return True if the items are similar, false otherwise.
     */
    public boolean areItemsEqual(ItemStack item1, ItemStack item2) {
        return item1.isSimilar(item2);
    }

    /**
     * Adds lore lines to an ItemStack.
     *
     * @param item The ItemStack to modify.
     * @param lore The lore lines to add (supports color codes).
     */
    public void addLore(ItemStack item, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> existingLore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            for (String line : lore) {
                existingLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(existingLore);
            item.setItemMeta(meta);
        }
    }

    /**
     * Sets the custom model data for an ItemStack.
     *
     * @param item            The ItemStack to modify.
     * @param customModelData The custom model data value to set.
     */
    public void setCustomModelData(ItemStack item, int customModelData) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
    }

    /**
     * Retrieves the custom model data from an ItemStack.
     *
     * @param item The ItemStack to check.
     * @return The custom model data value, or null if not set.
     */
    public Integer getCustomModelData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null ? meta.getCustomModelData() : null;
    }

    /**
     * Sets the damage value of a damageable item.
     *
     * @param item   The ItemStack to modify.
     * @param damage The damage value to set.
     */
    public void setItemDamage(ItemStack item, int damage) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damage);
            item.setItemMeta(meta);
        }
    }

    /**
     * Retrieves the damage value of a damageable item.
     *
     * @param item The ItemStack to check.
     * @return The damage value, or -1 if the item is not damageable.
     */
    public int getItemDamage(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            return damageable.getDamage();
        }
        return -1;
    }

    /**
     * Sets a leather armor piece to a specific color.
     *
     * @param item  The leather armor ItemStack to color.
     * @param color The color to apply.
     */
    public void setLeatherArmorColor(ItemStack item, Color color) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
            item.setItemMeta(leatherMeta);
        }
    }

    /**
     * Retrieves the color of a leather armor piece.
     *
     * @param item The leather armor ItemStack to check.
     * @return The Color of the armor, or null if not applicable.
     */
    public Color getLeatherArmorColor(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leatherMeta) {
            return leatherMeta.getColor();
        }
        return null;
    }

    /**
     * Creates a fireworks item with specified effects.
     *
     * @param effects The list of FireworkEffects to include.
     * @param power   The flight duration of the firework.
     * @return An ItemStack representing the custom firework.
     */
    public ItemStack createFirework(List<FireworkEffect> effects, int power) {
        ItemStack firework = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) firework.getItemMeta();
        if (meta != null) {
            meta.addEffects(effects);
            meta.setPower(power);
            firework.setItemMeta(meta);
        }
        return firework;
    }

    /**
     * Creates a knowledge book with specified recipes.
     *
     * @param recipes The list of NamespacedKeys representing the recipes.
     * @return An ItemStack representing the knowledge book.
     */
    public ItemStack createKnowledgeBook(List<NamespacedKey> recipes) {
        ItemStack book = new ItemStack(Material.KNOWLEDGE_BOOK);
        KnowledgeBookMeta meta = (KnowledgeBookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setRecipes(recipes);
            book.setItemMeta(meta);
        }
        return book;
    }

}

package com.rednetty.server.utils.menu;

import com.rednetty.server.core.mechanics.item.enchants.Enchants;
import com.rednetty.server.utils.messaging.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an item in a menu with an optional click handler
 */
public class MenuItem {

    private ItemStack itemStack;
    private String displayName;
    private List<String> lore = new ArrayList<>();
    private boolean glowing = false;
    private MenuClickHandler clickHandler;

    /**
     * Creates a new menu item with the specified material
     *
     * @param material The material of the item
     */
    public MenuItem(Material material) {
        this(new ItemStack(material));
    }

    /**
     * Creates a new menu item with the specified material and display name
     *
     * @param material    The material of the item
     * @param displayName The display name of the item
     */
    public MenuItem(Material material, String displayName) {
        this(new ItemStack(material));
        this.displayName = displayName;
    }

    /**
     * Creates a new menu item from an existing ItemStack
     *
     * @param itemStack The ItemStack to use
     */
    public MenuItem(ItemStack itemStack) {
        this.itemStack = itemStack.clone();

        if (itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();

            if (meta.hasDisplayName()) {
                this.displayName = meta.getDisplayName();
            }

            if (meta.hasLore()) {
                this.lore = new ArrayList<>(meta.getLore());
            }

            this.glowing = Enchants.hasGlow(itemStack);
        }
    }

    /**
     * Sets the display name of the item
     *
     * @param displayName The display name to set
     * @return This MenuItem instance for method chaining
     */
    public MenuItem setDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Sets the lore of the item
     *
     * @param lore The lore to set
     * @return This MenuItem instance for method chaining
     */
    public MenuItem setLore(List<String> lore) {
        this.lore = new ArrayList<>(lore);
        return this;
    }

    /**
     * Adds a line of lore to the item
     *
     * @param line The line to add
     * @return This MenuItem instance for method chaining
     */
    public MenuItem addLoreLine(String line) {
        this.lore.add(line);
        return this;
    }

    /**
     * Sets whether the item should glow
     *
     * @param glowing Whether the item should glow
     * @return This MenuItem instance for method chaining
     */
    public MenuItem setGlowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    /**
     * Sets the click handler for the item
     *
     * @param clickHandler The click handler to set
     * @return This MenuItem instance for method chaining
     */
    public MenuItem setClickHandler(MenuClickHandler clickHandler) {
        this.clickHandler = clickHandler;
        return this;
    }

    /**
     * Gets the click handler for the item
     *
     * @return The click handler, or null if none
     */
    public MenuClickHandler getClickHandler() {
        return clickHandler;
    }

    /**
     * Converts this MenuItem to an ItemStack
     *
     * @return The ItemStack representation of this MenuItem
     */
    public ItemStack toItemStack() {
        ItemStack result = itemStack.clone();
        ItemMeta meta = result.getItemMeta();

        if (meta != null) {
            if (displayName != null) {
                // Properly format display name
                Component nameComponent = formatText(displayName);
                meta.displayName(nameComponent);
            }

            if (!lore.isEmpty()) {
                // Properly format lore lines
                List<Component> formattedLore = new ArrayList<>();
                for (String line : lore) {
                    formattedLore.add(formatText(line));
                }
                meta.lore(formattedLore);
            }

            // Hide enchantments to prevent showing "Glow I" in the lore
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            result.setItemMeta(meta);

            // Apply or remove glow effect
            if (glowing) {
                Enchants.addGlow(result);
            } else {
                Enchants.removeGlow(result);
            }
        }

        return result;
    }
    
    /**
     * Formats text with proper color code handling
     * Supports both MiniMessage format (<color>) and legacy codes (&x, §x mixed with &x)
     */
    private Component formatText(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        
        // Check if it's MiniMessage format
        if (text.contains("<") && text.contains(">")) {
            return MessageUtils.parse(text);
        } else {
            // Handle legacy format - this covers:
            // 1. Pure & codes: &aHello &lBold
            // 2. Mixed § and & codes: §e&lAppeal Management (from ChatColor.YELLOW + "&l...")
            // 3. Pure § codes: §aHello§lBold
            
            // First translate & codes to § codes, then use legacy serializer
            String processedText = ChatColor.translateAlternateColorCodes('&', text);
            return MessageUtils.fromLegacy(processedText);
        }
    }
    
}
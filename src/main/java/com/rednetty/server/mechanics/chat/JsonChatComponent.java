package com.rednetty.server.mechanics.chat;

import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;


public class JsonChatComponent {

    private final List<BaseComponent> components;

    /**
     * Create a new JSON chat component with initial text
     *
     * @param text The initial text
     */
    public JsonChatComponent(String text) {
        this.components = new ArrayList<>();
        addText(text);
    }

    /**
     * Add plain text to the component
     *
     * @param text The text to add
     * @return This component for chaining
     */
    public JsonChatComponent addText(String text) {
        TextComponent component = new TextComponent(text);
        components.add(component);
        return this;
    }

    /**
     * Add text with a hover showing item details
     *  Use modern BungeeCord chat API
     *
     * @param text      The clickable text
     * @param hoverText The text to show on hover
     * @return This component for chaining
     */
    public JsonChatComponent addHoverItem(String text, List<String> hoverText) {
        TextComponent component = new TextComponent(text);
        component.setBold(true);
        component.setUnderlined(true);

        // Create hover text
        StringBuilder hoverBuilder = new StringBuilder();
        for (int i = 0; i < hoverText.size(); i++) {
            if (i > 0) {
                hoverBuilder.append("\n");
            }
            hoverBuilder.append(hoverText.get(i));
        }

        // Set hover event using the new API
        try {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text(hoverBuilder.toString())));
        } catch (Exception e) {
            // Fallback for older versions
            try {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(hoverBuilder.toString()).create()));
            } catch (Exception e2) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to create hover event", e2);
            }
        }

        components.add(component);
        return this;
    }

    /**
     * @param text      The text to show
     * @param hoverText The text to show on hover
     * @return This component for chaining
     */
    public JsonChatComponent addHoverText(String text, String hoverText) {
        TextComponent component = new TextComponent(text);

        try {
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new Text(hoverText)));
        } catch (Exception e) {
            // Fallback for older versions
            try {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(hoverText).create()));
            } catch (Exception e2) {
                Bukkit.getLogger().log(Level.WARNING, "Failed to create hover event", e2);
            }
        }

        components.add(component);
        return this;
    }

    /**
     * Add text with a command click event
     *
     * @param text    The clickable text
     * @param command The command to run
     * @return This component for chaining
     */
    public JsonChatComponent addCommandClickText(String text, String command) {
        TextComponent component = new TextComponent(text);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        components.add(component);
        return this;
    }

    /**
     * Send this component to a player
     *  Use modern Spigot API with proper fallbacks
     *
     * @param player The player to send to
     */
    public void send(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            // Convert to array
            BaseComponent[] componentArray = components.toArray(new BaseComponent[0]);

            // Try modern Spigot method first
            try {
                player.spigot().sendMessage(componentArray);
                return;
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.FINE, "Modern spigot method failed, trying alternatives", e);
            }

            // Try sending each component individually if the array method fails
            for (BaseComponent component : components) {
                try {
                    player.spigot().sendMessage(component);
                } catch (Exception e) {
                    // If individual sending fails, fall back to plain text
                    player.sendMessage(component.toPlainText());
                }
            }

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to send JSON chat component, using plain text fallback", e);
            // Final fallback to plain text
            player.sendMessage(toPlainText());
        }
    }

    /**
     * Convert this component to plain text (without hover/click events)
     *
     * @return Plain text version of this component
     */
    public String toPlainText() {
        StringBuilder builder = new StringBuilder();
        for (BaseComponent component : components) {
            builder.append(component.toPlainText());
        }
        return builder.toString();
    }

    /**
     * Get the components as an array
     *
     * @return Array of base components
     */
    public BaseComponent[] getComponents() {
        return components.toArray(new BaseComponent[0]);
    }
}
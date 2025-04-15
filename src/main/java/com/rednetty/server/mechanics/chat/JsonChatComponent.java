package com.rednetty.server.mechanics.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for creating and sending JSON chat messages
 * with hover and click events
 */
public class JsonChatComponent {

    private final List<JsonObject> components;

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
        JsonObject component = new JsonObject();
        component.addProperty("text", text);
        components.add(component);
        return this;
    }

    /**
     * Add text with a hover showing item details
     *
     * @param text      The clickable text
     * @param hoverText The text to show on hover
     * @return This component for chaining
     */
    public JsonChatComponent addHoverItem(String text, List<String> hoverText) {
        JsonObject component = new JsonObject();
        component.addProperty("text", ChatColor.BOLD + ChatColor.UNDERLINE.toString() + text);

        JsonObject hoverEvent = new JsonObject();
        hoverEvent.addProperty("action", "show_text");

        JsonArray content = new JsonArray();
        for (String line : hoverText) {
            content.add(line);
        }

        JsonObject value = new JsonObject();
        value.add("text", content);

        hoverEvent.add("value", value);
        component.add("hoverEvent", hoverEvent);

        components.add(component);
        return this;
    }

    /**
     * Add text with a hover event
     *
     * @param text      The text to show
     * @param hoverText The text to show on hover
     * @return This component for chaining
     */
    public JsonChatComponent addHoverText(String text, String hoverText) {
        JsonObject component = new JsonObject();
        component.addProperty("text", text);

        JsonObject hoverEvent = new JsonObject();
        hoverEvent.addProperty("action", "show_text");
        hoverEvent.addProperty("value", hoverText);

        component.add("hoverEvent", hoverEvent);
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
        JsonObject component = new JsonObject();
        component.addProperty("text", text);

        JsonObject clickEvent = new JsonObject();
        clickEvent.addProperty("action", "run_command");
        clickEvent.addProperty("value", command);

        component.add("clickEvent", clickEvent);
        components.add(component);
        return this;
    }

    /**
     * Send this component to a player
     *
     * @param player The player to send to
     */
    public void send(Player player) {
        try {
            // Convert components to a single JSON array
            JsonArray componentsArray = new JsonArray();
            for (JsonObject component : components) {
                componentsArray.add(component);
            }

            // Convert to JSON string
            String json = componentsArray.toString();

            // Use reflection to create and send the packet
            Class<?> chatSerializerClass = getNMSClass("IChatBaseComponent$ChatSerializer");
            Class<?> chatComponentClass = getNMSClass("IChatBaseComponent");
            Class<?> packetClass = getNMSClass("PacketPlayOutChat");

            Object chatComponent = chatSerializerClass.getMethod("a", String.class)
                    .invoke(null, json);

            Constructor<?> packetConstructor = packetClass.getConstructor(chatComponentClass, byte.class);
            Object packet = packetConstructor.newInstance(chatComponent, (byte) 0);

            sendPacket(player, packet);
        } catch (Exception e) {
            // Fallback to plain text if JSON fails
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
        for (JsonObject component : components) {
            if (component.has("text")) {
                builder.append(component.get("text").getAsString());
            }
        }
        return builder.toString();
    }

    /**
     * Get an NMS class by name
     *
     * @param name The class name
     * @return The NMS class
     */
    private Class<?> getNMSClass(String name) throws ClassNotFoundException {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        String className = "net.minecraft.server." + version + "." + name;
        return Class.forName(className);
    }

    /**
     * Send a packet to a player
     *
     * @param player The player to send to
     * @param packet The packet to send
     */
    private void sendPacket(Player player, Object packet) throws Exception {
        Class<?> craftPlayerClass = player.getClass();
        Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
        Object entityPlayer = getHandleMethod.invoke(player);

        Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
        Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet"));

        sendPacketMethod.invoke(playerConnection, packet);
    }
}
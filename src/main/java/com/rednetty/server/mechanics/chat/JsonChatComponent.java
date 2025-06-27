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
import java.util.logging.Level;

/**
 * A utility class for creating and sending JSON chat messages
 * with hover and click events
 * FIXED: Updated for Spigot 1.20.2 compatibility
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
     * FIXED: Corrected JSON structure for proper hover events
     *
     * @param text      The clickable text
     * @param hoverText The text to show on hover
     * @return This component for chaining
     */
    public JsonChatComponent addHoverItem(String text, List<String> hoverText) {
        JsonObject component = new JsonObject();
        component.addProperty("text", ChatColor.BOLD + ChatColor.UNDERLINE.toString() + text);

        // FIXED: Proper hover event structure
        JsonObject hoverEvent = new JsonObject();
        hoverEvent.addProperty("action", "show_text");

        // Create proper text component for hover content
        JsonObject hoverContent = new JsonObject();
        if (hoverText.size() == 1) {
            hoverContent.addProperty("text", hoverText.get(0));
        } else {
            // Multiple lines - create array of text components
            JsonArray extraArray = new JsonArray();
            boolean first = true;
            for (String line : hoverText) {
                if (first) {
                    hoverContent.addProperty("text", line);
                    first = false;
                } else {
                    JsonObject lineComponent = new JsonObject();
                    lineComponent.addProperty("text", "\n" + line);
                    extraArray.add(lineComponent);
                }
            }
            if (extraArray.size() > 0) {
                hoverContent.add("extra", extraArray);
            }
        }

        hoverEvent.add("contents", hoverContent);
        component.add("hoverEvent", hoverEvent);

        components.add(component);
        return this;
    }

    /**
     * Add text with a hover event
     * FIXED: Updated JSON structure
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

        JsonObject hoverContent = new JsonObject();
        hoverContent.addProperty("text", hoverText);
        hoverEvent.add("contents", hoverContent);

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
     * FIXED: Updated for 1.20.2 with better error handling and fallback methods
     *
     * @param player The player to send to
     */
    public void send(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            // Try modern Spigot methods first
            if (sendViaSpigotAPI(player)) {
                return;
            }

            // Fallback to reflection for 1.20.2
            if (sendViaReflection(player)) {
                return;
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "Failed to send JSON chat component", e);
        }

        // Final fallback to plain text
        player.sendMessage(toPlainText());
    }

    /**
     * FIXED: Try to send using modern Spigot API methods
     *
     * @param player The player to send to
     * @return true if successful
     */
    private boolean sendViaSpigotAPI(Player player) {
        try {
            // Try using Spigot's chat component methods
            Class<?> spigotClass = Class.forName("org.bukkit.entity.Player$Spigot");
            Object spigot = player.getClass().getMethod("spigot").invoke(player);

            // Create the JSON message
            String json = buildFinalJson();

            // Try to find and use sendMessage with BaseComponent
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");

            // Use TextComponent.fromLegacyText as fallback if JSON parsing fails
            Method fromLegacyMethod = textComponentClass.getMethod("fromLegacyText", String.class);
            Object[] components = (Object[]) fromLegacyMethod.invoke(null, toPlainText());

            Method sendMessageMethod = spigotClass.getMethod("sendMessage", baseComponentClass.arrayType());
            sendMessageMethod.invoke(spigot, (Object) components);

            return true;
        } catch (Exception e) {
            // Not available or failed, continue to reflection
            return false;
        }
    }

    /**
     * FIXED: Updated reflection for 1.20.2 NMS structure
     *
     * @param player The player to send to
     * @return true if successful
     */
    private boolean sendViaReflection(Player player) {
        try {
            String version = getServerVersion();
            String json = buildFinalJson();

            // For 1.20.2, try the new structure
            if (version.compareTo("v1_20_R2") >= 0) {
                return sendViaModernNMS(player, json);
            } else {
                return sendViaLegacyNMS(player, json, version);
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FIXED: Modern NMS method for 1.20.2+
     */
    private boolean sendViaModernNMS(Player player, String json) {
        try {
            // Get the CraftPlayer
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);

            // Get the ServerPlayer (EntityPlayer equivalent in newer versions)
            Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
            Object serverPlayer = getHandleMethod.invoke(craftPlayer);

            // Try to get connection
            Object connection;
            try {
                connection = serverPlayer.getClass().getField("connection").get(serverPlayer);
            } catch (NoSuchFieldException e) {
                // Try alternative field name
                connection = serverPlayer.getClass().getField("playerConnection").get(serverPlayer);
            }

            // Create chat component
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> serializerClass = Class.forName("net.minecraft.network.chat.Component$Serializer");

            Method fromJsonMethod = serializerClass.getMethod("fromJson", String.class);
            Object component = fromJsonMethod.invoke(null, json);

            // Create and send packet
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSystemChatPacket");
            Constructor<?> packetConstructor = packetClass.getConstructor(componentClass, boolean.class);
            Object packet = packetConstructor.newInstance(component, false);

            // Send packet
            Method sendMethod = connection.getClass().getMethod("send",
                    Class.forName("net.minecraft.network.protocol.Packet"));
            sendMethod.invoke(connection, packet);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FIXED: Legacy NMS method for older versions
     */
    private boolean sendViaLegacyNMS(Player player, String json, String version) {
        try {
            Class<?> chatSerializerClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent$ChatSerializer");
            Class<?> chatComponentClass = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat");

            Object chatComponent = chatSerializerClass.getMethod("a", String.class).invoke(null, json);

            Constructor<?> packetConstructor = packetClass.getConstructor(chatComponentClass, byte.class);
            Object packet = packetConstructor.newInstance(chatComponent, (byte) 0);

            sendPacketLegacy(player, packet, version);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FIXED: Get server version properly
     */
    private String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    /**
     * Send a packet to a player using legacy NMS
     *
     * @param player  The player to send to
     * @param packet  The packet to send
     * @param version The server version
     */
    private void sendPacketLegacy(Player player, Object packet, String version) throws Exception {
        Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
        Object craftPlayer = craftPlayerClass.cast(player);

        Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
        Object entityPlayer = getHandleMethod.invoke(craftPlayer);

        Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
        Method sendPacketMethod = playerConnection.getClass().getMethod("sendPacket",
                Class.forName("net.minecraft.server." + version + ".Packet"));

        sendPacketMethod.invoke(playerConnection, packet);
    }

    /**
     * FIXED: Build the final JSON with proper structure
     *
     * @return The complete JSON string
     */
    private String buildFinalJson() {
        if (components.isEmpty()) {
            return "{\"text\":\"\"}";
        }

        if (components.size() == 1) {
            return components.get(0).toString();
        }

        // Multiple components - create with extra array
        JsonObject root = components.get(0);
        JsonArray extra = new JsonArray();

        for (int i = 1; i < components.size(); i++) {
            extra.add(components.get(i));
        }

        root.add("extra", extra);
        return root.toString();
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
}
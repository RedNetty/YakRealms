package com.rednetty.server.utils.items;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

public enum SkullTextures {

    CHERRY("d525707696bcd15a173056fa39296e80ff41168bb0add552f4523e2558a3119"),
    FROST("ab2344a2aace0350158750ce137ae6d337edbcaee2e894aa69eaac0bf9b5869c"),
    APPLE("cbb311f3ba1c07c3d1147cd210d81fe11fd8ae9e3db212a0fa748946c3633"),
    WITHER_KING("68c0165e9b2dbd78dac91277e97d9a02648f3059e126a5941a84d05429ce"),
    DEMON("d2975b67c19f9ba2344f8eee956c5015ad63d9e88ad4882ae79369374fb3975"),
    DEMON_KILATAN("1464eb8e99e2878f343803a742ef57ceafacc2283e67b88edec16821316f9f"),
    DEMON_JAYDEN("444772dc4def22219ee6d889ccdc2f9232ee23d356dd9e4adcea5f72cc0c689"),
    VOID("c84bcd9ab0b02a9eaeaae2648282d3d5e90d24d483979eaffb19390243eca301");

    private final String textureCode;

    SkullTextures(String textureCode) {
        this.textureCode = textureCode;
    }

    public String getURL() {
        return "http://textures.minecraft.net/texture/" + this.textureCode;
    }

    public ItemStack getSkullByURL() {
        String url = getURL();
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        PlayerProfile profile = Bukkit.getServer().createPlayerProfile(UUID.randomUUID(), "");
        try {
            profile.getTextures().setSkin(new URL(url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        skullMeta.setOwnerProfile(profile);
        skull.setItemMeta(skullMeta);
        return skull;
    }

    public ItemStack getFrostSkull() {
        try {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            OfflinePlayer frostPlayer = Bukkit.getOfflinePlayer(UUID.fromString("87d0088a-09c2-4765-8f9c-9a2f5db7acbc"));
            meta.setOwningPlayer(frostPlayer);
            head.setItemMeta(meta);
            return head;
        } catch (Exception e) {
            return CHERRY.getSkullByURL();
        }
    }

    public ItemStack getVoidSkull() {
        try {
            return VOID.getSkullByURL();
        } catch (Exception e) {
            return CHERRY.getSkullByURL();
        }
    }

    /**
     * Create a textured skull via a Mojang texture code.
     *
     * @param textureCode The texture code from the URL.
     * @return Textured skull.
     */
    public static ItemStack createSkullFromURL(String textureCode) {
        String url = "http://textures.minecraft.net/texture/" + textureCode;
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        PlayerProfile profile = Bukkit.getServer().createPlayerProfile(UUID.randomUUID(), "");
        ;
        try {
            profile.getTextures().setSkin(new URL(url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        skullMeta.setOwnerProfile(profile);
        skull.setItemMeta(skullMeta);
        return skull;
    }
}

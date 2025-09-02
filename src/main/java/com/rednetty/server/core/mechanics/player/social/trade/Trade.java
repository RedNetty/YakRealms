package com.rednetty.server.core.mechanics.player.social.trade;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/**
 * Represents a trade session between two players.
 * Manages items being offered, confirmation statuses, and the completion state.
 */
public class Trade {
    private final Player initiator;
    private final Player target;
    private final Map<Player, List<ItemStack>> playerItems;
    private final Map<Player, Boolean> playerConfirmations;
    private boolean completed;

    public Trade(Player initiator, Player target) {
        this.initiator = initiator;
        this.target = target;
        this.playerItems = new HashMap<>();
        this.playerItems.put(initiator, new ArrayList<>());
        this.playerItems.put(target, new ArrayList<>());
        this.playerConfirmations = new HashMap<>();
        this.playerConfirmations.put(initiator, false);
        this.playerConfirmations.put(target, false);
        this.completed = false;
    }

    public Player getInitiator() {
        return initiator;
    }

    public Player getTarget() {
        return target;
    }

    public List<ItemStack> getPlayerItems(Player player) {
        return new ArrayList<>(playerItems.getOrDefault(player, Collections.emptyList()));
    }

    public void addPlayerItem(Player player, ItemStack item) {
        playerItems.get(player).add(item.clone());
        resetConfirmations();
    }

    public void removePlayerItem(Player player, ItemStack item) {
        playerItems.get(player).remove(item);
        resetConfirmations();
    }

    public boolean isPlayerConfirmed(Player player) {
        return playerConfirmations.getOrDefault(player, false);
    }

    public void setPlayerConfirmed(Player player, boolean confirmed) {
        playerConfirmations.put(player, confirmed);
    }

    public boolean isConfirmed() {
        return isPlayerConfirmed(initiator) && isPlayerConfirmed(target);
    }

    public Player getOtherPlayer(Player player) {
        return player.equals(initiator) ? target : initiator;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void clearPlayerItems(Player player) {
        playerItems.get(player).clear();
    }

    public boolean hasPlayerItems(Player player) {
        return !playerItems.getOrDefault(player, Collections.emptyList()).isEmpty();
    }

    public void resetConfirmations() {
        playerConfirmations.put(initiator, false);
        playerConfirmations.put(target, false);
    }
}
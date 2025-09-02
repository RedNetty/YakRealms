package com.rednetty.server.core.mechanics.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Event that is fired when a player damages a living entity.
 * This event allows for the modification or cancellation of damage values
 * before they are applied to the target entity.
 */
public class HitRegisterEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player damager;
    private final LivingEntity target;
    private double damage;
    private boolean cancelled;
    private EntityDamageByEntityEvent originalEvent;

    /**
     * Creates a new hit register event
     *
     * @param damager The player dealing the damage
     * @param target  The living entity receiving the damage
     * @param damage  The amount of damage to be dealt
     */
    public HitRegisterEvent(Player damager, LivingEntity target, double damage) {
        this.damager = damager;
        this.target = target;
        this.damage = damage;
        this.cancelled = false;
        this.originalEvent = null;
    }

    /**
     * Creates a new hit register event with reference to the original damage event
     *
     * @param damager       The player dealing the damage
     * @param target        The living entity receiving the damage
     * @param damage        The amount of damage to be dealt
     * @param originalEvent The original EntityDamageByEntityEvent
     */
    public HitRegisterEvent(Player damager, LivingEntity target, double damage, EntityDamageByEntityEvent originalEvent) {
        this.damager = damager;
        this.target = target;
        this.damage = damage;
        this.cancelled = false;
        this.originalEvent = originalEvent;
    }

    /**
     * Gets the player who is dealing the damage
     *
     * @return The attacking player
     */
    public Player getDamager() {
        return damager;
    }

    /**
     * Gets the entity that is being damaged
     *
     * @return The target entity
     */
    public LivingEntity getTarget() {
        return target;
    }

    /**
     * Gets the amount of damage that will be dealt
     *
     * @return The damage amount
     */
    public double getDamage() {
        return damage;
    }

    /**
     * Sets the amount of damage to be dealt
     *
     * @param damage The new damage amount
     */
    public void setDamage(double damage) {
        this.damage = damage;

        // Create or update the original event with the new damage value
        if (this.originalEvent == null) {
            this.originalEvent = new EntityDamageByEntityEvent(
                    damager,
                    target,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    damage
            );
        } else {
            // For 1.20.2, we can't modify damage on existing events directly
            // so we create a new one to reflect the change
            this.originalEvent = new EntityDamageByEntityEvent(
                    damager,
                    target,
                    this.originalEvent.getCause(),
                    damage
            );
        }
    }

    /**
     * Gets the original EntityDamageByEntityEvent if available
     *
     * @return The original damage event or null if not set
     */
    public EntityDamageByEntityEvent getOriginalEvent() {
        return originalEvent;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Gets the handler list for this event type
     *
     * @return The event's handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
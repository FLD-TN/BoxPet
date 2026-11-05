package com.dex.boxpet.api.event;

import com.dex.boxpet.api.PetView;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A pet's combat ability is about to damage an entity (the damage is dealt as the owner). Cancel it to protect the
 * target (other plugins' safe zones, duels...), or change the amount.
 */
public final class PetAbilityDamageEvent extends PetEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity target;
    private final String ability;
    private double damage;
    private boolean cancelled;

    public PetAbilityDamageEvent(@NotNull Player owner, @NotNull PetView pet, @NotNull LivingEntity target,
                                 @NotNull String ability, double damage) {
        super(owner, pet);
        this.target = target;
        this.ability = ability;
        this.damage = damage;
    }

    public @NotNull LivingEntity getTarget() {
        return target;
    }

    /** Ability id from abilities.yml. */
    public @NotNull String getAbility() {
        return ability;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = Math.max(0, damage);
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

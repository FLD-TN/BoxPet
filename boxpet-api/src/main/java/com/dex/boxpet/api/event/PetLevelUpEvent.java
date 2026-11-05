package com.dex.boxpet.api.event;

import com.dex.boxpet.api.PetView;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

/** A pet reached a new level. {@link #getPet()} already has the new level. */
public final class PetLevelUpEvent extends PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int oldLevel;

    public PetLevelUpEvent(@NotNull Player owner, @NotNull PetView pet, int oldLevel) {
        super(owner, pet);
        this.oldLevel = oldLevel;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

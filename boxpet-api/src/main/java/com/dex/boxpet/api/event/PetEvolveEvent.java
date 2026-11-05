package com.dex.boxpet.api.event;

import com.dex.boxpet.api.PetView;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

/** A pet evolved to its next stage. {@link #getPet()} already has the new stage. */
public final class PetEvolveEvent extends PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PetEvolveEvent(@NotNull Player owner, @NotNull PetView pet) {
        super(owner, pet);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

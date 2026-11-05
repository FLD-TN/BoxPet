package com.dex.boxpet.api.event;

import com.dex.boxpet.api.PetView;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

/** A pet was put away (by the player, a staff action or a release). Not fired on logout or death. */
public final class PetDismissEvent extends PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public PetDismissEvent(@NotNull Player owner, @NotNull PetView pet) {
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

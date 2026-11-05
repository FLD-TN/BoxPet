package com.dex.boxpet.api.event;

import com.dex.boxpet.api.PetView;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import org.jetbrains.annotations.NotNull;

/** An egg hatched. {@link #getPet()} is the new pet (random eggs: the rolled result). */
public final class PetHatchEvent extends PetEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean randomEgg;

    public PetHatchEvent(@NotNull Player owner, @NotNull PetView pet, boolean randomEgg) {
        super(owner, pet);
        this.randomEgg = randomEgg;
    }

    /** True for a random egg (Trứng Ngẫu Nhiên). */
    public boolean isRandomEgg() {
        return randomEgg;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

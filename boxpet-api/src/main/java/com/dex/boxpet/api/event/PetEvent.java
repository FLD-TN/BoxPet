package com.dex.boxpet.api.event;

import com.dex.boxpet.api.PetView;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Base of BoxPet's events: a pet of an online player. */
public abstract class PetEvent extends PlayerEvent {

    private final PetView pet;

    protected PetEvent(@NotNull Player owner, @NotNull PetView pet) {
        super(owner);
        this.pet = pet;
    }

    /** The pet, as it was when the event was fired. */
    public @NotNull PetView getPet() {
        return pet;
    }
}

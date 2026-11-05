package com.dex.boxpet.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BoxPet service. Resolve it with {@code Bukkit.getServicesManager().load(BoxPetService.class)} after checking
 * {@code Bukkit.getPluginManager().isPluginEnabled("BoxPet")}. Main thread only unless noted.
 */
public interface BoxPetService {

    /** The active pet of an online player, empty if none or the player is offline. */
    Optional<PetView> getActivePet(UUID player);

    /** Every pet in an online player's storage, empty for offline players. */
    List<PetView> getPets(UUID player);

    /**
     * Gives XP to the active pet of an online player (not subject to daily caps).
     *
     * @return false if the player has no active pet, or it is at max level
     */
    boolean addXp(UUID player, double xp);

    /**
     * Fills the active pet's stomach, like eating food.
     *
     * @return false if there is no active pet, or the amount does not fit
     */
    boolean feed(UUID player, int fullness);

    /** The boost bonus of the active pet for a BoxCore boost type, in tenths (0 if none). Thread-safe. */
    int bonusTenths(UUID player, String boostType);
}

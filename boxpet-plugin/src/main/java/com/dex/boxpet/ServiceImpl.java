package com.dex.boxpet;

import com.dex.boxpet.api.BoxPetService;
import com.dex.boxpet.api.PetView;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.pet.ActivePet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link BoxPetService} for other plugins (spec 16). */
final class ServiceImpl implements BoxPetService {

    private final BoxPetPlugin plugin;

    ServiceImpl(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<PetView> getActivePet(UUID player) {
        PlayerData pd = plugin.pets().data(player);
        PetData a = pd == null ? null : pd.active();
        return a == null ? Optional.empty() : Optional.of(plugin.pets().view(a));
    }

    @Override
    public List<PetView> getPets(UUID player) {
        PlayerData pd = plugin.pets().data(player);
        List<PetView> out = new ArrayList<>();
        if (pd != null) for (PetData d : pd.pets.values()) out.add(plugin.pets().view(d));
        return out;
    }

    @Override
    public boolean addXp(UUID player, double xp) {
        ActivePet p = plugin.pets().active(player);
        return p != null && xp > 0 && plugin.xp().addXp(p, xp);
    }

    @Override
    public boolean feed(UUID player, int fullness) {
        ActivePet p = plugin.pets().active(player);
        return p != null && fullness > 0 && plugin.care().feedVanilla(p, fullness);
    }

    @Override
    public int bonusTenths(UUID player, String boostType) {
        return plugin.pets().bonusTenths(player, boostType);
    }
}

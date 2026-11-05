package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.api.event.PetHatchEvent;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.IncubatorSlot;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.item.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Incubator (spec 10.2): eggs hatch in real time, online and offline; random eggs roll on opening. */
public final class IncubatorService {

    public enum OpenResult {
        OK, NOT_READY, EMPTY, STORAGE_FULL, NO_PET
    }

    /** Result of opening a finished slot. */
    public record Opened(OpenResult result, @Nullable PetData pet, boolean random) {
    }

    private final BoxPetPlugin plugin;

    public IncubatorService(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    public int slots(Player player) {
        int def = plugin.settings().defaultIncubator;
        for (int n = plugin.settings().maxIncubator; n > def; n--) {
            if (player.hasPermission("boxpet.incubator." + n)) return n;
        }
        return def;
    }

    /** Pets a random egg of this rarity can give. */
    public List<PetType> pool(Rarity rarity) {
        List<PetType> out = new ArrayList<>();
        for (PetType t : plugin.types().all()) if (t.eggWeight > 0 && t.allows(rarity.id())) out.add(t);
        return out;
    }

    public int poolSize(Rarity rarity) {
        return pool(rarity).size();
    }

    public @Nullable PetType roll(Rarity rarity) {
        List<PetType> pool = pool(rarity);
        int total = 0;
        for (PetType t : pool) total += t.eggWeight;
        if (total <= 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (PetType t : pool) {
            r -= t.eggWeight;
            if (r < 0) return t;
        }
        return pool.get(pool.size() - 1);
    }

    /** First free slot, -1 when all used. */
    public int freeSlot(Player player, PlayerData pd) {
        int max = slots(player);
        for (int i = 0; i < max; i++) if (!pd.incubator.containsKey(i)) return i;
        return -1;
    }

    /** Puts one egg (already validated and taken from the player) into a slot. */
    public IncubatorSlot put(PlayerData pd, int slot, ItemFactory.Info egg) {
        // fixed egg: the pet's own rarity (one pet = one rarity); random egg: the egg's rarity
        PetType fixed = egg.kind() == ItemFactory.Kind.RANDOM_EGG ? null : plugin.types().get(egg.id());
        Rarity r = plugin.rarities().get(fixed != null ? fixed.rarity : egg.rarity());
        long now = System.currentTimeMillis();
        long time = (r == null ? 600 : r.hatchSeconds()) * 1000L;
        IncubatorSlot s = new IncubatorSlot(slot, egg.kind() == ItemFactory.Kind.RANDOM_EGG,
                egg.kind() == ItemFactory.Kind.RANDOM_EGG ? "" : egg.id(), r == null ? egg.rarity() : r.id(), now, now + time);
        pd.incubator.put(slot, s);
        pd.incubatorDirty = true;
        pd.dirty = true;
        plugin.pets().save(pd);
        return s;
    }

    /** Gem cost to finish now (spec 10.2): remaining minutes × gem-per-minute, at least 1. */
    public long speedUpCost(IncubatorSlot s, long now) {
        long minutes = (long) Math.ceil(Math.max(0, s.finishAt() - now) / 60_000.0);
        return Math.max(1, (long) Math.ceil(minutes * plugin.settings().gemPerMinute));
    }

    public void finishNow(PlayerData pd, IncubatorSlot s) {
        pd.incubator.put(s.slot(), s.finishNow(System.currentTimeMillis()));
        pd.incubatorDirty = true;
        pd.dirty = true;
        plugin.pets().save(pd);
    }

    /**
     * Opens a finished slot: the pet is created and saved before any animation, so closing the menu changes
     * nothing.
     */
    public Opened open(Player player, PlayerData pd, int slot) {
        IncubatorSlot s = pd.incubator.get(slot);
        if (s == null) return new Opened(OpenResult.EMPTY, null, false);
        long now = System.currentTimeMillis();
        if (!s.ready(now)) return new Opened(OpenResult.NOT_READY, null, s.random());
        if (pd.pets.size() >= plugin.pets().capacity(player)) return new Opened(OpenResult.STORAGE_FULL, null, s.random());
        Rarity rarity = plugin.rarities().get(s.rarity());
        if (rarity == null) rarity = plugin.rarities().lowest();
        PetType type = s.random() ? roll(rarity) : plugin.types().get(s.petType());
        if (type == null) return new Opened(OpenResult.NO_PET, null, s.random());
        PetData pet = plugin.pets().createPet(player.getUniqueId(), type, s.random() ? "random-egg" : "egg");
        pd.incubator.remove(slot);
        pd.incubatorDirty = true;
        plugin.pets().addPet(pd, pet);
        plugin.pets().save(pd);
        Bukkit.getPluginManager().callEvent(new PetHatchEvent(player, plugin.pets().view(pet), s.random()));
        return new Opened(OpenResult.OK, pet, s.random());
    }
}

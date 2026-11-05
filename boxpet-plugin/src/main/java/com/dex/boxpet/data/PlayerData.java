package com.dex.boxpet.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Everything BoxPet keeps about one player. Main thread only. */
public final class PlayerData {

    public final UUID uuid;
    /** Pets in storage ({@code COLLECTION}), by id, in hatch order. */
    public final Map<UUID, PetData> pets = new LinkedHashMap<>();
    public final TreeMap<Integer, IncubatorSlot> incubator = new TreeMap<>();
    /** Pet type id -> best rarity index ever hatched (collection book). */
    public final Map<String, Integer> collection = new HashMap<>();
    /** XP source -> XP gained today ({@link #xpDay}). */
    public final Map<String, Double> dailyXp = new HashMap<>();
    public long xpDay;
    public @Nullable UUID activePet;
    public boolean hideOthers;
    public boolean petChat = true;
    public transient boolean dirty;
    public transient boolean incubatorDirty;
    public transient boolean collectionDirty;
    /** A BoxPet item left the inventory since the last save: save the player file with the pet data. */
    public transient boolean inventoryChanged;
    /** Pets removed from this player (released, transferred): saved then forgotten. */
    public final transient List<PetData> removed = new ArrayList<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public @Nullable PetData active() {
        return activePet == null ? null : pets.get(activePet);
    }

    public List<PetData> sorted(Comparator<PetData> order) {
        List<PetData> list = new ArrayList<>(pets.values());
        list.sort(order);
        return list;
    }
}

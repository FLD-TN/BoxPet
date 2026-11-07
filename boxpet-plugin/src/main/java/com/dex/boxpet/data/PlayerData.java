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

    /** 0.20.6: this exact pet object is still in this player's storage (not released, not moved to someone else). */
    public boolean owns(PetData pet) {
        return pet != null && pets.get(pet.id) == pet && pet.state != PetData.State.DELETED;
    }
    public final TreeMap<Integer, IncubatorSlot> incubator = new TreeMap<>();
    /** Pet type id -> best rarity index ever hatched (collection book). */
    public final Map<String, Integer> collection = new HashMap<>();
    /** XP source -> XP gained today ({@link #xpDay}). */
    public final Map<String, Double> dailyXp = new HashMap<>();
    public long xpDay;
    public @Nullable UUID activePet;
    public boolean hideOthers;
    public boolean petChat = true;
    /** 0.8.0: "pet: skill!" line in chat when a skill goes off. */
    public boolean castChat = true;
    /** Auto feed (0.6.0): on/off, fullness to start eating at, candies before foods. */
    public boolean autoFeed;
    public int autoFeedAt = 30;
    public boolean autoFeedCandyFirst;
    /** The auto feed box, slot by slot (eating order): consumable id (null = empty) and amount. */
    public final String[] pantryIds = new String[PANTRY_SIZE];
    public final int[] pantryAmounts = new int[PANTRY_SIZE];
    /** "Box empty" was told for this hungry spell. */
    public transient boolean autoFeedWarned;
    public transient boolean dirty;
    public transient boolean incubatorDirty;
    public transient boolean collectionDirty;
    /** A BoxPet item left the inventory since the last save: save the player file with the pet data. */
    public transient boolean inventoryChanged;
    /** Pets removed from this player (released, transferred): saved then forgotten. */
    public final transient List<PetData> removed = new ArrayList<>();

    public static final int PANTRY_SIZE = 21;

    /** {@code id*amount} per slot, joined by ';' (an empty slot is empty text). */
    public String pantryText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PANTRY_SIZE; i++) {
            if (i > 0) sb.append(';');
            if (pantryIds[i] != null && pantryAmounts[i] > 0) sb.append(pantryIds[i]).append('*').append(pantryAmounts[i]);
        }
        return sb.toString();
    }

    public void pantryFrom(@Nullable String text) {
        if (text == null || text.isEmpty()) return;
        String[] parts = text.split(";", -1);
        for (int i = 0; i < parts.length && i < PANTRY_SIZE; i++) {
            int star = parts[i].lastIndexOf('*');
            if (star <= 0) continue;
            try {
                int amount = Integer.parseInt(parts[i].substring(star + 1));
                if (amount <= 0) continue;
                pantryIds[i] = parts[i].substring(0, star);
                pantryAmounts[i] = amount;
            } catch (NumberFormatException ignored) {
                // broken slot: left empty
            }
        }
    }

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

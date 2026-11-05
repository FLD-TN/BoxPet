package com.dex.boxpet.data;

import java.util.UUID;

/** One pet row (spec 3.3). Mutable, main thread only; {@link #dirty} marks it for the next save. */
public final class PetData {

    public enum State {
        COLLECTION, DELETED
    }

    public final UUID id;
    public UUID owner;
    public String type;
    public String rarity;
    public int level = 1;
    public double xp;
    public int stage;
    public String customName = "";
    public int bond;
    public int fullness;
    /** Earliest time (epoch ms) a petting raises the bond again. */
    public long bondGainAt;
    /** Neglect time banked while the pet was active (ms), since the last petting. */
    public long neglectMs;
    /** Number of neglect periods already taken off the bond since the last petting. */
    public int neglectSteps;
    /** When it became the active pet (epoch ms), 0 when resting in storage. */
    public long activeSince;
    /** Time with the owner online and the pet out, not yet turned into fullness loss (ms). */
    public long drainMs;
    /** Epoch day of {@link #foodBond}. */
    public long foodBondDay;
    /** Bond gained from food that day. */
    public int foodBond;
    public long hatchedAt;
    public State state = State.COLLECTION;
    public long createdAt;
    public String origin = "hatch";
    public transient boolean dirty;

    public PetData(UUID id) {
        this.id = id;
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }

    public long ageDays(long now) {
        return Math.max(0, (now - hatchedAt) / 86_400_000L);
    }

    public PetData copy() {
        PetData c = new PetData(id);
        c.owner = owner;
        c.type = type;
        c.rarity = rarity;
        c.level = level;
        c.xp = xp;
        c.stage = stage;
        c.customName = customName;
        c.bond = bond;
        c.fullness = fullness;
        c.bondGainAt = bondGainAt;
        c.neglectMs = neglectMs;
        c.neglectSteps = neglectSteps;
        c.activeSince = activeSince;
        c.drainMs = drainMs;
        c.foodBondDay = foodBondDay;
        c.foodBond = foodBond;
        c.hatchedAt = hatchedAt;
        c.state = state;
        c.createdAt = createdAt;
        c.origin = origin;
        return c;
    }
}

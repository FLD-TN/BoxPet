package com.dex.boxpet.data;

/**
 * An egg hatching in the incubator (spec 10.2).
 *
 * @param random   true for a random egg ({@code petType} is then empty, rolled on opening)
 * @param finishAt epoch ms when it is ready
 */
public record IncubatorSlot(int slot, boolean random, String petType, String rarity, long startedAt, long finishAt) {

    public boolean ready(long now) {
        return now >= finishAt;
    }

    public IncubatorSlot finishNow(long now) {
        return new IncubatorSlot(slot, random, petType, rarity, startedAt, Math.min(finishAt, now));
    }
}

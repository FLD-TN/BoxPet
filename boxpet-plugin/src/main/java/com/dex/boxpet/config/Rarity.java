package com.dex.boxpet.config;

/**
 * One rarity from rarities.yml, ordered by {@link #index()} (0 = lowest).
 *
 * @param hatchSeconds      incubator time of an egg of this rarity
 * @param collectionPoints  points in the collection book for the best rarity of a pet type
 */
public record Rarity(String id, int index, String name, String color, double statMultiplier, int maxLevel,
                     long hatchSeconds, int collectionPoints) {

    /** {@code <#BFC7D5>Thường}. */
    public String coloured() {
        return "<" + color + ">" + name;
    }
}

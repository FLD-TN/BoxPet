package com.dex.boxpet.config;

/**
 * One rarity from rarities.yml, ordered by {@link #index()} (0 = lowest). Since 0.5.0 every pet type has exactly
 * one rarity.
 *
 * @param color             main colour (#RRGGBB): panes, bars, single-colour uses
 * @param gradientTo        second colour of the name gradient, "" for a plain colour
 * @param hatchSeconds      incubator time of an egg of this rarity
 * @param collectionPoints  points in the collection book for owning a pet type of this rarity
 */
public record Rarity(String id, int index, String name, String color, String gradientTo, double statMultiplier,
                     int maxLevel, long hatchSeconds, int collectionPoints) {

    /** The rarity name in its colours: {@code <gradient:#A:#B>Huyền thoại</gradient>} or {@code <#A>Thường}. */
    public String coloured() {
        if (gradientTo == null || gradientTo.isEmpty()) return "<" + color + ">" + name;
        return "<gradient:" + color + ":" + gradientTo + ">" + name + "</gradient>";
    }
}

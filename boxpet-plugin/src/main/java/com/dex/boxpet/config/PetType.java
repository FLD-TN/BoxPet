package com.dex.boxpet.config;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/** One file of {@code pets/} (spec 3.2). Immutable. */
public final class PetType {

    public enum Role {
        COMBAT("combat"), SUPPORT("support"), BOOST("boost");

        public final String id;

        Role(String id) {
            this.id = id;
        }

        public static Role of(String s) {
            if (s == null) return SUPPORT;
            for (Role r : values()) if (r.id.equalsIgnoreCase(s)) return r;
            return SUPPORT;
        }
    }

    /** Value at level 1 and growth per level, before the rarity / stage / bond multipliers. */
    public record StatDef(String stat, double base, double perLevel, boolean relative) {
        public double at(int level) {
            return base + perLevel * Math.max(0, level - 1);
        }
    }

    /** One evolution stage. The first stage has {@code minLevel} 1 and no cost. */
    public record Stage(String model, double scale, int minLevel, double costMoney, long costGem,
                        double statMultiplier) {
    }

    public final String id;
    public final String name;
    public final String family;
    public final Role role;
    public final String icon;
    public final Material iconFallback;
    public final List<String> rarities;
    public final int eggWeight;
    public final List<Stage> stages;
    public final Map<String, String> animations;
    public final @Nullable Double side;
    public final @Nullable Double back;
    public final @Nullable Double height;
    public final boolean fly;
    public final boolean carryEnabled;
    public final @Nullable Double carryScale;
    public final List<StatDef> stats;
    public final String ability;
    public final String dialogue;
    public final List<String> description;

    public PetType(String id, String name, String family, Role role, String icon, Material iconFallback,
                   List<String> rarities, int eggWeight, List<Stage> stages, Map<String, String> animations,
                   @Nullable Double side, @Nullable Double back, @Nullable Double height, boolean fly,
                   boolean carryEnabled, @Nullable Double carryScale, List<StatDef> stats, String ability,
                   String dialogue, List<String> description) {
        this.id = id;
        this.name = name;
        this.family = family;
        this.role = role;
        this.icon = icon;
        this.iconFallback = iconFallback;
        this.rarities = List.copyOf(rarities);
        this.eggWeight = eggWeight;
        this.stages = List.copyOf(stages);
        this.animations = Map.copyOf(animations);
        this.side = side;
        this.back = back;
        this.height = height;
        this.fly = fly;
        this.carryEnabled = carryEnabled;
        this.carryScale = carryScale;
        this.stats = List.copyOf(stats);
        this.ability = ability;
        this.dialogue = dialogue;
        this.description = List.copyOf(description);
    }

    public boolean allows(String rarity) {
        return rarities.contains(rarity);
    }

    public Stage stage(int index) {
        return stages.get(Math.max(0, Math.min(stages.size() - 1, index)));
    }

    /** Animation name for a state ({@code idle}, {@code walk}, {@code skill}, {@code interact}, {@code spawn}). */
    public String animation(String state) {
        String a = animations.get(state);
        return a == null ? state : a;
    }
}

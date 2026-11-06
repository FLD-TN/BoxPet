package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * One ability of abilities.yml (spec 6). At most one buff and one debuff. Values scale with
 * {@code (base + per-level × (level - 1)) × rarity multiplier}.
 */
public final class Ability {

    public enum Type {
        PROJECTILE, STRIKE, MARK, METEOR, ORBIT, ZONE, HEAL, SHIELD, BUFF, POTION, BOOST, NONE;

        public static Type of(String s) {
            if (s == null) return NONE;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NONE;
            }
        }

        public boolean combat() {
            return this == PROJECTILE || this == STRIKE || this == MARK || this == METEOR || this == ZONE;
        }
    }

    public enum Debuff {
        NONE, BURN, SLOW, BLEED, WEAKNESS;

        public static Debuff of(String s) {
            if (s == null) return NONE;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NONE;
            }
        }
    }

    /** {@code base + per-level × (level - 1)}, then × rarity multiplier. */
    public record Scaled(double base, double perLevel) {
        public static final Scaled ZERO = new Scaled(0, 0);

        static Scaled read(@Nullable ConfigurationSection s, String path, double def) {
            if (s == null) return new Scaled(def, 0);
            ConfigurationSection c = s.getConfigurationSection(path);
            if (c == null) return new Scaled(s.getDouble(path, def), 0);
            return new Scaled(c.getDouble("base", def), c.getDouble("per-level", 0));
        }

        public Scaled times(double k) {
            return new Scaled(base * k, perLevel * k);
        }

        public double at(int level, double rarityMultiplier) {
            return (base + perLevel * Math.max(0, level - 1)) * rarityMultiplier;
        }
    }

    public record SoundFx(String sound, float volume, float pitch, int delay) {
    }

    /** {@code at}: pet, target, owner. */
    public record ParticleFx(String particle, int count, double spread, double speed, String at, int delay) {
    }

    /**
     * A ModelEngine model used as a visual: the projectile itself, or something shown at the target / owner.
     *
     * @param speed blocks per tick for projectiles
     * @param life  ticks before it disappears (projectile max flight time)
     */
    public record VfxFx(String model, String animation, double speed, int life, double scale, double yOffset) {
    }

    public final String id;
    public final Type type;
    public final String name;
    /** Colour of the skill name: a hex or a gradient "#A:#B". */
    public final String color;
    public final List<String> description;
    public final int cooldownTicks;
    public final double range;
    public final int castDelay;
    public final Scaled damage;
    public final @Nullable Double pvpMultiplier;
    public final double pveMultiplier;
    public final Debuff debuff;
    public final int debuffTicks;
    public final int debuffAmplifier;
    public final double debuffDamageRatio;
    public final Scaled heal;
    public final boolean healPercent;
    public final int healBelowPercent;
    public final int shieldBelowPercent;
    public final Scaled shieldHearts;
    public final int shieldTicks;
    public final String potion;
    public final List<Integer> potionAmplifier;
    public final Scaled markBonus;
    public final int markTicks;
    // 0.8.0 ----------------------------------------------------------------
    /** Area: every valid target within this radius of the impact is hit (0 = only the target). */
    public final double areaRadius;
    /** Chain: the hit bounces to this many more targets (each {@code chainRatio} of the previous damage). */
    public final int chainTargets;
    public final double chainRange;
    public final double chainRatio;
    /** Strike / mark: every valid target on the line from the pet to the target is hit. */
    public final boolean pierce;
    /** Repeats of the attack and ticks between them. */
    public final int hits;
    public final int hitInterval;
    /** Meteor: how many fall around the target, how far from it, warning ticks before they land. */
    public final int meteors;
    public final double meteorSpread;
    public final int meteorWarn;
    /** Orbit: how long the orbs circle the owner (count = meteor.count, radius = area.radius). */
    public final int orbitTicks;
    /** 0.9.7 zone: a damaging area left at the target for zoneTicks, hitting every zoneInterval ticks. */
    public final int zoneTicks;
    public final int zoneInterval;
    /** 0.9.7: a projectile is fired at this many different enemies around the target. */
    public final int multiTargets;
    /** Buff: when it goes off (combat, hurt, low-hp) and the owner-only stat buff it gives. */
    public final String trigger;
    public final int buffBelowPercent;
    public final String buffStat;
    public final Scaled buffAmount;
    public final int buffTicks;
    public final boolean buffRelative;
    /** Buff: a stat taken from enemy players (the target / attacker, or every enemy within {@code enemyRadius}). */
    public final String enemyStat;
    public final Scaled enemyAmount;
    public final double enemyRadius;
    /** Shield: enemies around the owner are pushed away when the shield comes up. */
    public final double blastRadius;
    public final double blastPower;
    /** 0.9.0: vfx animation per hit ({@code fx.vfx.animations}), and a model on a bleeding target ({@code debuff.vfx}). */
    public final List<String> vfxAnimations;
    public final String debuffVfx;
    /** 0.9.4: model of the explosion of area hits, meteors and orbs ({@code fx.area-vfx}), scaled to the radius. */
    public final String areaVfx;
    public final String boostType;
    public final Scaled boostTenths;
    public final boolean boostWhole;
    public final String animation;
    public final List<SoundFx> sounds;
    public final List<ParticleFx> particles;
    public final @Nullable VfxFx vfx;

    public Ability(String id, ConfigurationSection s) {
        this(id, s, 1.0, 1.0);
    }

    /** {@code damageMul} / {@code healMul}: config.yml abilities.damage-multiplier / heal-multiplier (heal + shield). */
    public Ability(String id, ConfigurationSection s, double damageMul, double healMul) {
        this.id = id;
        type = Type.of(s.getString("type"));
        name = s.getString("name", id);
        color = s.getString("color", "#FFD166");
        description = s.getStringList("description");
        cooldownTicks = (int) Math.max(1, Math.round(s.getDouble("cooldown", 8) * 20));
        range = Math.max(1, s.getDouble("range", 12));
        castDelay = Math.max(0, s.getInt("cast-delay-ticks", 10));
        damage = Scaled.read(s, "damage", 0).times(damageMul);
        pvpMultiplier = s.contains("pvp-multiplier") ? Math.max(0, s.getDouble("pvp-multiplier")) : null;
        pveMultiplier = Math.max(0, s.getDouble("pve-multiplier", 1.0));
        ConfigurationSection d = s.getConfigurationSection("debuff");
        debuff = d == null ? Debuff.NONE : Debuff.of(d.getString("type"));
        debuffTicks = d == null ? 0 : (int) Math.round(d.getDouble("seconds", 3) * 20);
        debuffAmplifier = d == null ? 0 : Math.max(0, d.getInt("amplifier", 0));
        debuffDamageRatio = d == null ? 0 : Math.max(0, d.getDouble("damage-ratio", 0.3));
        ConfigurationSection h = s.getConfigurationSection("heal");
        heal = h == null ? Scaled.ZERO : Scaled.read(h, "amount", 0).times(healMul);
        healPercent = h != null && h.getBoolean("percent", false);
        healBelowPercent = h == null ? 100 : Math.max(1, Math.min(100, h.getInt("below-percent", 100)));
        ConfigurationSection sh = s.getConfigurationSection("shield");
        shieldBelowPercent = sh == null ? 40 : Math.max(1, Math.min(99, sh.getInt("below-percent", 40)));
        shieldHearts = sh == null ? Scaled.ZERO : Scaled.read(sh, "hearts", 2).times(healMul);
        shieldTicks = sh == null ? 100 : (int) Math.round(sh.getDouble("seconds", 5) * 20);
        ConfigurationSection p = s.getConfigurationSection("potion");
        potion = p == null ? "" : p.getString("effect", "");
        potionAmplifier = p == null ? List.of(0) : (p.isList("amplifier-by-rarity")
                ? p.getIntegerList("amplifier-by-rarity") : List.of(p.getInt("amplifier", 0)));
        ConfigurationSection m = s.getConfigurationSection("mark");
        markBonus = m == null ? Scaled.ZERO : Scaled.read(m, "bonus-percent", 10);
        markTicks = m == null ? 100 : (int) Math.round(m.getDouble("seconds", 5) * 20);
        ConfigurationSection ar = s.getConfigurationSection("area");
        areaRadius = ar == null ? 0 : Math.max(0, Math.min(8, ar.getDouble("radius", 3)));
        ConfigurationSection ch = s.getConfigurationSection("chain");
        chainTargets = ch == null ? 0 : Math.max(0, Math.min(5, ch.getInt("targets", 1)));
        chainRange = ch == null ? 5 : Math.max(1, Math.min(10, ch.getDouble("range", 5)));
        chainRatio = ch == null ? 0.7 : Math.max(0, Math.min(1, ch.getDouble("ratio", 0.7)));
        pierce = s.getBoolean("pierce", false);
        ConfigurationSection hs = s.getConfigurationSection("hits");
        hits = hs == null ? 1 : Math.max(1, Math.min(6, hs.getInt("count", 1)));
        hitInterval = hs == null ? 6 : Math.max(1, hs.getInt("interval-ticks", 6));
        ConfigurationSection me = s.getConfigurationSection("meteor");
        ConfigurationSection obc = s.getConfigurationSection("orbit");
        meteors = me != null ? Math.max(1, Math.min(6, me.getInt("count", 3)))
                : obc != null ? Math.max(1, Math.min(6, obc.getInt("count", 3))) : 1;
        meteorSpread = me == null ? 2 : Math.max(0, Math.min(6, me.getDouble("spread", 2.5)));
        meteorWarn = me == null ? 20 : Math.max(5, me.getInt("warn-ticks", 20));
        ConfigurationSection zn = s.getConfigurationSection("zone");
        zoneTicks = zn == null ? 80 : (int) Math.round(Math.max(1, zn.getDouble("seconds", 4)) * 20);
        zoneInterval = zn == null ? 20 : Math.max(5, zn.getInt("interval-ticks", 20));
        multiTargets = Math.max(1, Math.min(5, s.getInt("targets", 1)));
        ConfigurationSection ob = s.getConfigurationSection("orbit");
        orbitTicks = ob == null ? 120 : (int) Math.round(Math.max(1, ob.getDouble("seconds", 6)) * 20);
        trigger = s.getString("trigger", "combat").toLowerCase(Locale.ROOT);
        ConfigurationSection bu = s.getConfigurationSection("buff");
        buffStat = bu == null ? "" : bu.getString("stat", "").toUpperCase(Locale.ROOT);
        buffAmount = bu == null ? Scaled.ZERO : Scaled.read(bu, "amount", 0);
        buffTicks = bu == null ? 100 : (int) Math.round(bu.getDouble("seconds", 5) * 20);
        buffRelative = bu != null && "relative".equalsIgnoreCase(bu.getString("type", "flat"));
        buffBelowPercent = bu == null ? 40 : Math.max(1, Math.min(100, bu.getInt("below-percent", 40)));
        ConfigurationSection en = s.getConfigurationSection("enemy-stat");
        enemyStat = en == null ? "" : en.getString("stat", "").toUpperCase(Locale.ROOT);
        enemyAmount = en == null ? Scaled.ZERO : Scaled.read(en, "amount", 0);
        enemyRadius = en == null ? 0 : Math.max(0, Math.min(10, en.getDouble("radius", 0)));
        vfxAnimations = s.getStringList("fx.vfx.animations");
        areaVfx = s.getString("fx.area-vfx", "");
        debuffVfx = d == null ? "" : d.getString("vfx", "");
        ConfigurationSection bl = s.getConfigurationSection("blast");
        blastRadius = bl == null ? 0 : Math.max(0, Math.min(8, bl.getDouble("radius", 4)));
        blastPower = bl == null ? 0 : Math.max(0, Math.min(3, bl.getDouble("power", 1)));
        ConfigurationSection b = s.getConfigurationSection("boost");
        boostType = b == null ? "" : b.getString("type", "").toLowerCase(Locale.ROOT);
        boostTenths = b == null ? Scaled.ZERO : Scaled.read(b, "tenths", 0);
        boostWhole = b != null && (b.getBoolean("whole", false) || boostType.equals("mining") || boostType.equals("mob"));
        ConfigurationSection fx = s.getConfigurationSection("fx");
        animation = fx == null ? "skill" : fx.getString("animation", "skill");
        sounds = new ArrayList<>();
        particles = new ArrayList<>();
        VfxFx v = null;
        if (fx != null) {
            for (var map : fx.getMapList("sounds")) {
                Object snd = map.get("sound");
                if (snd == null) continue;
                sounds.add(new SoundFx(snd.toString(), (float) num(map.get("volume"), 1), (float) num(map.get("pitch"), 1),
                        (int) num(map.get("delay"), 0)));
            }
            for (var map : fx.getMapList("particles")) {
                Object par = map.get("particle");
                if (par == null) continue;
                particles.add(new ParticleFx(par.toString(), (int) num(map.get("count"), 8), num(map.get("spread"), 0.3),
                        num(map.get("speed"), 0.02), String.valueOf(map.get("at") == null ? "target" : map.get("at")),
                        (int) num(map.get("delay"), 0)));
            }
            ConfigurationSection vs = fx.getConfigurationSection("vfx");
            if (vs != null && !vs.getString("model", "").isBlank()) {
                v = new VfxFx(vs.getString("model"), vs.getString("animation", ""), Math.max(0.05, vs.getDouble("speed", 0.8)),
                        Math.max(5, vs.getInt("life-ticks", 40)), Math.max(0.1, vs.getDouble("scale", 1.0)),
                        vs.getDouble("y-offset", 0.0));
            }
        }
        vfx = v;
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
                // default
            }
        }
        return def;
    }

    public double pvp(double defaultMultiplier) {
        return pvpMultiplier == null ? defaultMultiplier : pvpMultiplier;
    }

    public int potionAmplifier(int rarityIndex) {
        if (potionAmplifier.isEmpty()) return 0;
        return Math.max(0, potionAmplifier.get(Math.min(potionAmplifier.size() - 1, rarityIndex)));
    }
}

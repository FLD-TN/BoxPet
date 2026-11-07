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
        PROJECTILE, STRIKE, MARK, METEOR, ORBIT, ZONE, TETHER, NOVA, HEAL, SHIELD, BUFF, POTION, BOOST, NONE;

        public static Type of(String s) {
            if (s == null) return NONE;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return NONE;
            }
        }

        public boolean combat() {
            return this == PROJECTILE || this == STRIKE || this == MARK || this == METEOR || this == ZONE || this == TETHER
                    || this == NOVA;
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
    public record VfxFx(String model, String animation, double speed, int life, double scale, double yOffset,
                        List<String> frames, List<String> parts, @Nullable String tint, boolean glow, double center,
                        boolean turn, boolean onEnemy) {
        /** 0.12.0: a flipbook effect of the pack (frame models swapped into {@code parts}, one per tick). */
        public boolean flipbook() {
            return !frames.isEmpty() && !parts.isEmpty();
        }
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
    /** 0.9.8 tether: a chain from the pet to the target for tetherTicks, hitting every tetherInterval ticks. */
    public final int tetherTicks;
    /**
     * 0.11.0 nova: the skill's effect model plays at the target (following it) and every enemy around it is hit at
     * each of these ticks after the effect starts (the explosion frames of the animation).
     */
    public final List<Integer> novaHits;
    /** 0.13.1: where the nova model stands: "target" (follows it) or "pet" (behind / around the pet, facing the target). */
    public final String novaAt;
    /** 0.13.1: particle line from the model's beam point to every enemy hit; empty = none. */
    public final String beamParticle;
    /** Beam point: blocks behind the model origin and above it, at vfx scale 1 (the nature flower's pistil: 2.75 / 4.9). */
    public final double beamBack;
    public final double beamUp;
    /** 0.14.0: damage multiplier of each hit (same order as hit-ticks; missing = 1), e.g. a stronger last hit. */
    public final List<Double> novaWeights;
    /** 0.14.0: the effect starts at the pet and reaches the target in this many ticks (Lốc Linh Hồn), 0 = at the target. */
    public final int novaTravel;
    /** 0.17.2: a line nova (Địa Chấn): hits a corridor from lineFrom to lineTo blocks ahead (vfx scale 1), lineWidth wide. */
    public final double lineFrom;
    public final double lineTo;
    public final double lineWidth;

    public double novaWeight(int hit) {
        return hit < novaWeights.size() ? novaWeights.get(hit) : 1.0;
    }
    public final int tetherInterval;
    /** Buff: when it goes off (combat, hurt, low-hp) and the owner-only stat buff it gives. */
    public final String trigger;
    public final int buffBelowPercent;
    public final String buffStat;
    public final Scaled buffAmount;
    public final int buffTicks;
    public final boolean buffRelative;
    /** 0.15.0: a second stat given with the buff (buff.also), e.g. a little Phòng thủ with Né đòn. */
    public final String buff2Stat;
    /** 0.16.0: one of these is given at random instead of buff.stat (Hộp Quà). */
    public record Choice(String stat, Scaled amount) {
    }
    public final List<Choice> buffChoices;
    /** 0.16.0: each trigger adds one more step of buff.amount, up to this many (Tích Giáp); 1 = no stacking. */
    public final int buffStacks;
    /** 0.16.0: the heal is given in this many steps, interval apart (hồi máu dần). */
    public final int healPulses;
    public final int healInterval;
    /** 0.16.0: enemies inside enemy-stat.radius keep losing the stat for the whole buff (lãnh địa). */
    public final boolean enemyDomain;
    public final Scaled buff2Amount;
    /** Buff: a stat taken from enemy players (the target / attacker, or every enemy within {@code enemyRadius}). */
    public final String enemyStat;
    public final Scaled enemyAmount;
    public final double enemyRadius;
    /** 0.9.9: how long a stat taken by a hit lasts ({@code enemy-stat.seconds}; buffs use buff.seconds). */
    public final int enemyTicks;
    /** Shield: enemies around the owner are pushed away when the shield comes up. */
    public final double blastRadius;
    public final double blastPower;
    /** 0.9.0: vfx animation per hit ({@code fx.vfx.animations}), and a model on a bleeding target ({@code debuff.vfx}). */
    public final List<String> vfxAnimations;
    public final String debuffVfx;
    /** 0.9.4: model of the explosion of area hits, meteors and orbs ({@code fx.area-vfx}), scaled to the radius. */
    public final String areaVfx;
    /**
     * 0.17.0: one economy boost. {@code levels} (level -> tenths, already final: no rarity multiplier) replaces the
     * smooth growth when set: the boost jumps at those levels (x1 -> x2 at 30 -> x3 at 50).
     */
    public record Boost(String type, Scaled tenths, boolean whole, java.util.NavigableMap<Integer, Integer> levels) {
        public int at(int level, double rarityMultiplier) {
            if (!levels.isEmpty()) {
                var e = levels.floorEntry(level);
                return e == null ? 0 : Math.max(0, e.getValue());
            }
            int t = (int) Math.floor(tenths.at(level, rarityMultiplier) + 1e-9);
            if (whole) t -= t % 10;
            return Math.max(0, t);
        }
    }

    /** boost + boost-2 (0.17.0: an economy pet may boost two things, e.g. quest and mining). */
    public final List<Boost> boosts;

    public int boostAt(int index, int level, double rarityMultiplier) {
        return index < boosts.size() ? boosts.get(index).at(level, rarityMultiplier) : 0;
    }

    public String boostTypeAt(int index) {
        return index < boosts.size() ? boosts.get(index).type() : "";
    }

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
        healPulses = h == null ? 1 : Math.max(1, Math.min(20, h.getInt("pulses", 1)));
        healInterval = h == null ? 20 : Math.max(5, h.getInt("interval-ticks", 20));
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
        ConfigurationSection te = s.getConfigurationSection("tether");
        tetherTicks = te == null ? 80 : (int) Math.round(Math.max(1, te.getDouble("seconds", 4)) * 20);
        tetherInterval = te == null ? 10 : Math.max(5, te.getInt("interval-ticks", 10));
        List<Integer> nh = new java.util.ArrayList<>();
        for (int t : s.getIntegerList("nova.hit-ticks")) if (t >= 0 && nh.size() < 16) nh.add(t);
        if (nh.isEmpty()) nh.add(20);
        novaHits = List.copyOf(nh);
        novaAt = s.getString("nova.at", "target").toLowerCase(Locale.ROOT);
        beamParticle = s.getString("nova.beam.particle", "");
        beamBack = s.getDouble("nova.beam.back", 0);
        beamUp = s.getDouble("nova.beam.up", 0);
        List<Double> w = new java.util.ArrayList<>();
        for (Double wv : s.getDoubleList("nova.weights")) w.add(Math.max(0, wv));
        novaWeights = List.copyOf(w);
        novaTravel = Math.max(0, s.getInt("nova.travel-ticks", 0));
        lineFrom = Math.max(0, s.getDouble("nova.line.from", 0));
        lineTo = Math.max(0, s.getDouble("nova.line.to", 0));
        lineWidth = Math.max(0.5, s.getDouble("nova.line.width", 1.5));
        ConfigurationSection ob = s.getConfigurationSection("orbit");
        orbitTicks = ob == null ? 120 : (int) Math.round(Math.max(1, ob.getDouble("seconds", 6)) * 20);
        trigger = s.getString("trigger", "combat").toLowerCase(Locale.ROOT);
        ConfigurationSection bu = s.getConfigurationSection("buff");
        buffStat = bu == null ? "" : bu.getString("stat", "").toUpperCase(Locale.ROOT);
        buffAmount = bu == null ? Scaled.ZERO : Scaled.read(bu, "amount", 0);
        buffTicks = bu == null ? 100 : (int) Math.round(bu.getDouble("seconds", 5) * 20);
        buffRelative = bu != null && "relative".equalsIgnoreCase(bu.getString("type", "flat"));
        ConfigurationSection b2 = bu == null ? null : bu.getConfigurationSection("also");
        buff2Stat = b2 == null ? "" : b2.getString("stat", "").toUpperCase(Locale.ROOT);
        buff2Amount = b2 == null ? Scaled.ZERO : Scaled.read(b2, "amount", 0);
        List<Choice> choices = new java.util.ArrayList<>();
        if (bu != null) {
            for (java.util.Map<?, ?> mm : bu.getMapList("random")) {
                Object st = mm.get("stat");
                if (st == null) continue;
                double base = mm.get("base") instanceof Number n ? n.doubleValue() : 0;
                double per = mm.get("per-level") instanceof Number n ? n.doubleValue() : 0;
                choices.add(new Choice(st.toString().toUpperCase(Locale.ROOT), new Scaled(base, per)));
            }
        }
        buffChoices = List.copyOf(choices);
        buffStacks = bu == null ? 1 : Math.max(1, Math.min(10, bu.getInt("stacks", 1)));
        buffBelowPercent = bu == null ? 40 : Math.max(1, Math.min(100, bu.getInt("below-percent", 40)));
        ConfigurationSection en = s.getConfigurationSection("enemy-stat");
        enemyStat = en == null ? "" : en.getString("stat", "").toUpperCase(Locale.ROOT);
        enemyAmount = en == null ? Scaled.ZERO : Scaled.read(en, "amount", 0);
        enemyRadius = en == null ? 0 : Math.max(0, Math.min(10, en.getDouble("radius", 0)));
        enemyTicks = en == null || !en.contains("seconds") ? buffTicks : (int) Math.round(en.getDouble("seconds") * 20);
        enemyDomain = en != null && en.getBoolean("domain", false);
        vfxAnimations = s.getStringList("fx.vfx.animations");
        areaVfx = s.getString("fx.area-vfx", "");
        debuffVfx = d == null ? "" : d.getString("vfx", "");
        ConfigurationSection bl = s.getConfigurationSection("blast");
        blastRadius = bl == null ? 0 : Math.max(0, Math.min(8, bl.getDouble("radius", 4)));
        blastPower = bl == null ? 0 : Math.max(0, Math.min(3, bl.getDouble("power", 1)));
        ConfigurationSection b = s.getConfigurationSection("boost");
        boostType = b == null ? "" : b.getString("type", "").toLowerCase(Locale.ROOT);
        boostTenths = b == null ? Scaled.ZERO : Scaled.read(b, "tenths", 0);
        // 0.17.0: only mining must be whole (CataMines); mob takes decimals (MyBoxMobs multiplies money / gem by tenths)
        boostWhole = b != null && (b.getBoolean("whole", false) || boostType.equals("mining"));
        List<Boost> boostList = new java.util.ArrayList<>();
        for (String key : new String[]{"boost", "boost-2"}) {
            ConfigurationSection bs = s.getConfigurationSection(key);
            if (bs == null || bs.getString("type", "").isBlank()) continue;
            String bt = bs.getString("type", "").toLowerCase(Locale.ROOT);
            java.util.TreeMap<Integer, Integer> lv = new java.util.TreeMap<>();
            ConfigurationSection ls = bs.getConfigurationSection("levels");
            if (ls != null) {
                for (String k : ls.getKeys(false)) {
                    try {
                        // value = the bonus multiplier (+x0.3 -> 3 tenths), rounded half up to BoxCore's 0.1 steps
                        lv.put(Integer.parseInt(k.trim()), (int) Math.round(ls.getDouble(k) * 10));
                    } catch (NumberFormatException ignored) {
                        // not a level number
                    }
                }
            }
            boolean whole = bs.getBoolean("whole", false) || bt.equals("mining");
            if (whole) lv.replaceAll((k, v) -> v - v % 10);
            boostList.add(new Boost(bt, Scaled.read(bs, "tenths", 0), whole, java.util.Collections.unmodifiableNavigableMap(lv)));
        }
        boosts = List.copyOf(boostList);
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
                        vs.getDouble("y-offset", 0.0), vs.getStringList("frames"), vs.getStringList("frame-parts"),
                        vs.getString("tint", null), vs.getBoolean("glow", false), vs.getDouble("center", 0), vs.getBoolean("turn", true),
                        vs.getBoolean("on-enemy", false));
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

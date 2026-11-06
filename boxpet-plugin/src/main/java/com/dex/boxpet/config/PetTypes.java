package com.dex.boxpet.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/** Every pet type of {@code plugins/BoxPet/pets/*.yml}, sorted by family then file name. */
public final class PetTypes {

    public static final Pattern ID = Pattern.compile("^[a-z0-9_]{1,48}$");

    private final Map<String, PetType> byId = new LinkedHashMap<>();

    public PetTypes(File folder, Rarities rarities, Logger log) {
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        List<PetType> loaded = new ArrayList<>();
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!ID.matcher(id).matches()) {
                log.warning("pets/" + f.getName() + ": file name must be a-z 0-9 _ (skipped)");
                continue;
            }
            try {
                loaded.add(read(id, YamlConfiguration.loadConfiguration(f), rarities, log));
            } catch (RuntimeException ex) {
                log.warning("pets/" + f.getName() + ": " + ex.getMessage() + " (skipped)");
            }
        }
        loaded.sort((a, b) -> {
            int c = a.family.compareTo(b.family);
            return c != 0 ? c : a.id.compareTo(b.id);
        });
        for (PetType t : loaded) byId.put(t.id, t);
    }

    private static PetType read(String id, YamlConfiguration y, Rarities rarities, Logger log) {
        // 0.5.0: one rarity per pet ("rarity"); old files with a "rarities" list use its first entry
        String rarityId = y.getString("rarity");
        if (rarityId == null && !y.getStringList("rarities").isEmpty()) rarityId = y.getStringList("rarities").get(0);
        Rarity rarity = rarities.get(rarityId);
        if (rarity == null) {
            if (rarityId != null) log.warning("pets/" + id + ".yml: unknown rarity " + rarityId + ", using " + rarities.lowest().id());
            rarity = rarities.lowest();
        }
        List<PetType.Stage> stages = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("stages")) {
            Object model = m.get("model");
            if (model == null) continue;
            stages.add(new PetType.Stage(model.toString(), num(m.get("scale"), 1.0),
                    (int) num(m.get("min-level"), 1), num(m.get("cost-money"), 0), (long) num(m.get("cost-gem"), 0),
                    num(m.get("stat-multiplier"), 1.0)));
        }
        if (stages.isEmpty()) {
            String model = y.getString("model");
            if (model == null || model.isBlank()) throw new IllegalArgumentException("no stages and no model");
            stages.add(new PetType.Stage(model, y.getDouble("scale", 1.0), 1, 0, 0, 1.0));
        }
        Map<String, String> anims = new LinkedHashMap<>();
        ConfigurationSection a = y.getConfigurationSection("animations");
        if (a != null) for (String k : a.getKeys(false)) anims.put(k.toLowerCase(Locale.ROOT), a.getString(k, ""));
        List<PetType.StatDef> stats = new ArrayList<>();
        ConfigurationSection s = y.getConfigurationSection("stats");
        if (s != null) {
            for (String stat : s.getKeys(false)) {
                if (BANNED.contains(stat.toUpperCase(Locale.ROOT))) continue;
                ConfigurationSection def = s.getConfigurationSection(stat);
                if (def == null) {
                    stats.add(new PetType.StatDef(stat.toUpperCase(Locale.ROOT), s.getDouble(stat), 0, false));
                } else {
                    stats.add(new PetType.StatDef(stat.toUpperCase(Locale.ROOT), def.getDouble("base", 0),
                            def.getDouble("per-level", 0), "relative".equalsIgnoreCase(def.getString("type", "flat"))));
                }
            }
        }
        PetType.StatDef bonus = bonusStat(y, stats);
        Material fallback = Material.matchMaterial(y.getString("icon-fallback", "EGG"));
        if (fallback == null || !fallback.isItem()) fallback = Material.EGG;
        String family = y.getString("family", id).toLowerCase(Locale.ROOT);
        return new PetType(id, y.getString("name", id), y.getString("color", "#FFFFFF"), family,
                PetType.Role.of(y.getString("role", "support")), y.getString("icon", ""), fallback, rarity.id(),
                Math.max(0, y.getInt("egg-weight", 1)), stages, anims, dbl(y, "follow.side"), dbl(y, "follow.back"),
                dbl(y, "follow.height"), y.getBoolean("follow.fly", false), stats, y.getString("ability", ""),
                y.getString("dialogue", family).toLowerCase(Locale.ROOT), bonus, y.getString("ability-2", ""));
    }

    /** 0.9.1: stats a pet never gives (skill / magic damage, health regeneration). */
    private static final java.util.Set<String> BANNED = java.util.Set.of("SKILL_DAMAGE", "MAGIC_DAMAGE",
            "SKILL_CRITICAL_STRIKE_CHANCE", "SKILL_CRITICAL_STRIKE_POWER", "HEALTH_REGENERATION", "SPELL_VAMPIRISM");

    /** 3rd stat per family when the pet file has no {@code bonus-stat} (MMOItems stat the pet does not have yet). */
    private static final Map<String, String> FAMILY_BONUS = Map.ofEntries(
            Map.entry("frog", "PVP_DAMAGE"), Map.entry("knight", "CRITICAL_STRIKE_POWER"),
            Map.entry("samurai_shiba", "CRITICAL_STRIKE_CHANCE"), Map.entry("fire_dragon", "CRITICAL_STRIKE_POWER"),
            Map.entry("necromancer_cat", "PVP_DAMAGE"), Map.entry("outlaw", "PVP_DAMAGE"),
            Map.entry("griffon", "PVP_DAMAGE_REDUCTION"), Map.entry("cacti", "PVP_DAMAGE_REDUCTION"),
            Map.entry("unicorn", "DAMAGE_REDUCTION"), Map.entry("shark", "PVE_DAMAGE_REDUCTION"),
            Map.entry("palcat", "PVE_DAMAGE"), Map.entry("cubee_dragon", "PVE_DAMAGE"));
    /** Default {base, per level} of a 3rd stat (before rarity and the other multipliers). */
    private static final Map<String, double[]> BONUS_VALUES = Map.ofEntries(
            Map.entry("PVP_DAMAGE", new double[]{0.5, 0.03}), Map.entry("CRITICAL_STRIKE_POWER", new double[]{2, 0.08}),
            Map.entry("CRITICAL_STRIKE_CHANCE", new double[]{0.5, 0.03}),
            Map.entry("SKILL_CRITICAL_STRIKE_CHANCE", new double[]{0.5, 0.03}),
            Map.entry("HEALTH_REGENERATION", new double[]{0.1, 0.01}), Map.entry("PVP_DAMAGE_REDUCTION", new double[]{0.5, 0.03}),
            Map.entry("DAMAGE_REDUCTION", new double[]{0.3, 0.02}), Map.entry("PVE_DAMAGE_REDUCTION", new double[]{0.5, 0.03}),
            Map.entry("PVE_DAMAGE", new double[]{0.5, 0.03}));

    private static @org.jetbrains.annotations.Nullable PetType.StatDef bonusStat(YamlConfiguration y, List<PetType.StatDef> stats) {
        String stat;
        double base;
        double per;
        ConfigurationSection b = y.getConfigurationSection("bonus-stat");
        if (b != null) {
            stat = b.getString("stat", "").toUpperCase(Locale.ROOT);
            if (stat.isEmpty() || BANNED.contains(stat)) return null;
            base = b.getDouble("base", 0);
            per = b.getDouble("per-level", 0);
        } else {
            stat = FAMILY_BONUS.get(y.getString("family", "").toLowerCase(Locale.ROOT));
            if (stat == null) return null;
            double[] v = BONUS_VALUES.getOrDefault(stat, new double[]{0.5, 0.03});
            base = v[0];
            per = v[1];
        }
        for (PetType.StatDef d : stats) if (d.stat().equals(stat)) return null;
        return new PetType.StatDef(stat, base, per, false);
    }

    private static @Nullable Double dbl(YamlConfiguration y, String path) {
        return y.contains(path) ? y.getDouble(path) : null;
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    public @Nullable PetType get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<PetType> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }

    public int size() {
        return byId.size();
    }
}

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
                ConfigurationSection def = s.getConfigurationSection(stat);
                if (def == null) {
                    stats.add(new PetType.StatDef(stat.toUpperCase(Locale.ROOT), s.getDouble(stat), 0, false));
                } else {
                    stats.add(new PetType.StatDef(stat.toUpperCase(Locale.ROOT), def.getDouble("base", 0),
                            def.getDouble("per-level", 0), "relative".equalsIgnoreCase(def.getString("type", "flat"))));
                }
            }
        }
        Material fallback = Material.matchMaterial(y.getString("icon-fallback", "EGG"));
        if (fallback == null || !fallback.isItem()) fallback = Material.EGG;
        String family = y.getString("family", id).toLowerCase(Locale.ROOT);
        return new PetType(id, y.getString("name", id), y.getString("color", "#FFFFFF"), family,
                PetType.Role.of(y.getString("role", "support")), y.getString("icon", ""), fallback, rarity.id(),
                Math.max(0, y.getInt("egg-weight", 1)), stages, anims, dbl(y, "follow.side"), dbl(y, "follow.back"),
                dbl(y, "follow.height"), y.getBoolean("follow.fly", false), stats, y.getString("ability", ""),
                y.getString("dialogue", family).toLowerCase(Locale.ROOT));
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

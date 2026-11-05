package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/** rarities.yml: the ordered list of rarities. Never empty (falls back to the four defaults). */
public final class Rarities {

    private final Map<String, Rarity> byId = new LinkedHashMap<>();
    private final List<Rarity> ordered = new ArrayList<>();

    public Rarities(YamlConfiguration yml, Logger log) {
        ConfigurationSection root = yml.getConfigurationSection("rarities");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                String id = key.toLowerCase(Locale.ROOT);
                add(new Rarity(id, ordered.size(), s.getString("name", key), s.getString("color", "#FFFFFF"),
                        Math.max(0, s.getDouble("stat-multiplier", 1)), Math.max(1, s.getInt("max-level", 30)),
                        Durations.seconds(s.getString("hatch-time", "10m"), 600),
                        Math.max(0, s.getInt("collection-points", 1))));
            }
        }
        if (ordered.isEmpty()) {
            log.warning("rarities.yml has no rarity: using the defaults");
            add(new Rarity("common", 0, "Thường", "#BFC7D5", 1.0, 30, 600, 1));
            add(new Rarity("rare", 1, "Hiếm", "#7FDBFF", 1.4, 40, 1800, 2));
            add(new Rarity("epic", 2, "Sử thi", "#C3B1E1", 1.9, 50, 3600, 3));
            add(new Rarity("legendary", 3, "Huyền thoại", "#FFD166", 2.5, 60, 7200, 5));
        }
    }

    private void add(Rarity r) {
        byId.put(r.id(), r);
        ordered.add(r);
    }

    public @Nullable Rarity get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public Rarity byIndex(int index) {
        return ordered.get(Math.max(0, Math.min(ordered.size() - 1, index)));
    }

    public Rarity lowest() {
        return ordered.get(0);
    }

    public List<Rarity> all() {
        return Collections.unmodifiableList(ordered);
    }

    public List<String> ids() {
        return new ArrayList<>(byId.keySet());
    }
}

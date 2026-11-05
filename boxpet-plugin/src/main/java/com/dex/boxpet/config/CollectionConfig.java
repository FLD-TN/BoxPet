package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** collection.yml (spec 9.4): score milestones that give permanent stats (key {@code boxpet_collection}). */
public final class CollectionConfig {

    public record Milestone(int score, String label, Map<String, Double> stats) {
    }

    public final List<Milestone> milestones = new ArrayList<>();

    public CollectionConfig(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("milestones");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Map<String, Double> stats = new LinkedHashMap<>();
                ConfigurationSection st = s.getConfigurationSection("stats");
                if (st != null) for (String k : st.getKeys(false)) stats.put(k.toUpperCase(Locale.ROOT), st.getDouble(k));
                milestones.add(new Milestone(Math.max(0, s.getInt("score", 0)), s.getString("label", key), stats));
            }
        }
        milestones.sort((a, b) -> Integer.compare(a.score(), b.score()));
    }

    /** Sum of the stats of every reached milestone. */
    public Map<String, Double> statsFor(int score) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Milestone m : milestones) {
            if (score < m.score()) break;
            m.stats().forEach((k, v) -> out.merge(k, v, Double::sum));
        }
        return out;
    }
}

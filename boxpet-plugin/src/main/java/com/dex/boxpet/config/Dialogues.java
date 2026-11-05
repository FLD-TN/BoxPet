package com.dex.boxpet.config;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * dialogues.yml (spec 11.4): {@code default.<event>} lines and optional {@code families.<family>.<event>} sets that
 * replace the default lines of that event for one family.
 */
public final class Dialogues {

    private final Map<String, List<String>> defaults = new HashMap<>();
    private final Map<String, Map<String, List<String>>> families = new HashMap<>();
    public final String format;
    public final String bubbleFormat;

    public Dialogues(YamlConfiguration y) {
        format = y.getString("format", "<#FFB3C7>%pet%<dark_gray>: <white>%line%");
        bubbleFormat = y.getString("bubble-format", "<white>%line%");
        read(y.getConfigurationSection("default"), defaults);
        ConfigurationSection f = y.getConfigurationSection("families");
        if (f != null) {
            for (String family : f.getKeys(false)) {
                Map<String, List<String>> m = new HashMap<>();
                read(f.getConfigurationSection(family), m);
                families.put(family.toLowerCase(Locale.ROOT), m);
            }
        }
    }

    private static void read(@Nullable ConfigurationSection s, Map<String, List<String>> into) {
        if (s == null) return;
        for (String event : s.getKeys(false)) {
            List<String> lines = s.getStringList(event);
            if (lines.isEmpty() && s.isString(event)) lines = List.of(s.getString(event));
            if (!lines.isEmpty()) into.put(event.toLowerCase(Locale.ROOT), lines);
        }
    }

    /**
     * A random line for the event, picked from the family's own lines together with the shared lines (0.5.0: the
     * family set adds to the shared one instead of replacing it); {@code null} when there is none.
     */
    public @Nullable String pick(String family, String event) {
        Map<String, List<String>> f = families.get(family);
        List<String> own = f == null ? null : f.get(event);
        List<String> shared = defaults.get(event);
        int a = own == null ? 0 : own.size();
        int b = shared == null ? 0 : shared.size();
        if (a + b == 0) return null;
        int i = ThreadLocalRandom.current().nextInt(a + b);
        return i < a ? own.get(i) : shared.get(i - a);
    }
}

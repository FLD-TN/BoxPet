package com.dex.boxpet.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/** abilities.yml: every ability by id. */
public final class Abilities {

    private final Map<String, Ability> byId = new LinkedHashMap<>();

    public Abilities(YamlConfiguration yml, Logger log) {
        ConfigurationSection root = yml.getConfigurationSection("abilities");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Ability a = new Ability(key.toLowerCase(Locale.ROOT), s);
            if (a.type == Ability.Type.NONE) {
                log.warning("abilities.yml: " + key + " has an unknown type (projectile, strike, mark, heal, shield, potion, boost)");
            }
            byId.put(a.id, a);
        }
    }

    public @Nullable Ability get(String id) {
        return id == null || id.isEmpty() ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public int size() {
        return byId.size();
    }
}

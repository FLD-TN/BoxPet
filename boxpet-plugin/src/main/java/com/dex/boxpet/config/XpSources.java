package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** xp-sources.yml (spec 9.1): activity XP, each source with its daily cap (0 = no cap). */
public final class XpSources {

    public record Source(String id, boolean enabled, double amount, double dailyCap) {
    }

    public static final String PLAYER_KILL = "player-kill";
    public static final String MOB_KILL = "mob-kill";
    public static final String BOSS_KILL = "boss-kill";
    public static final String MINING = "mining";
    public static final String QUEST = "quest";
    public static final String ONLINE = "online";

    private final Map<String, Source> sources = new HashMap<>();
    public final int sameVictimCooldownSeconds;
    public final boolean blockSameIp;
    public final double spawnerMultiplier;
    public final Map<String, Double> mobOverrides = new HashMap<>();
    public final Map<String, Double> myBoxMobsOverrides = new HashMap<>();
    public final double myBoxMobsDefault;
    public final boolean miningCataMines;
    public final boolean miningEverywhere;
    public final List<String> miningRegions;
    public final List<String> onlineSkipRegions;

    public XpSources(YamlConfiguration y) {
        put(y, PLAYER_KILL, 30, 300);
        put(y, MOB_KILL, 3, 600);
        put(y, BOSS_KILL, 100, 0);
        put(y, MINING, 0.1, 400);
        put(y, QUEST, 50, 500);
        put(y, ONLINE, 3, 900);
        sameVictimCooldownSeconds = Math.max(0, y.getInt(PLAYER_KILL + ".same-victim-cooldown-seconds", 600));
        blockSameIp = y.getBoolean(PLAYER_KILL + ".block-same-ip", true);
        spawnerMultiplier = Math.max(0, y.getDouble(MOB_KILL + ".spawner-multiplier", 1.0));
        ConfigurationSection per = y.getConfigurationSection(MOB_KILL + ".per-type");
        if (per != null) for (String k : per.getKeys(false)) mobOverrides.put(k.toUpperCase(Locale.ROOT), per.getDouble(k));
        myBoxMobsDefault = Math.max(0, y.getDouble(BOSS_KILL + ".myboxmobs-default", 10));
        ConfigurationSection mbm = y.getConfigurationSection(BOSS_KILL + ".myboxmobs");
        if (mbm != null) for (String k : mbm.getKeys(false)) myBoxMobsOverrides.put(k.toLowerCase(Locale.ROOT), mbm.getDouble(k));
        miningCataMines = y.getBoolean(MINING + ".catamines", true);
        miningEverywhere = y.getBoolean(MINING + ".everywhere", false);
        miningRegions = new ArrayList<>();
        for (String r : y.getStringList(MINING + ".worldguard-regions")) miningRegions.add(r.toLowerCase(Locale.ROOT));
        onlineSkipRegions = new ArrayList<>();
        for (String r : y.getStringList(ONLINE + ".skip-worldguard-regions")) onlineSkipRegions.add(r.toLowerCase(Locale.ROOT));
    }

    private void put(YamlConfiguration y, String id, double amount, double cap) {
        sources.put(id, new Source(id, y.getBoolean(id + ".enabled", true), Math.max(0, y.getDouble(id + ".amount", amount)),
                Math.max(0, y.getDouble(id + ".daily-cap", cap))));
    }

    public Source get(String id) {
        Source s = sources.get(id);
        return s == null ? new Source(id, false, 0, 0) : s;
    }
}

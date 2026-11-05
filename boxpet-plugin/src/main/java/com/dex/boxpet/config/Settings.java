package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * config.yml. Every value has its default in code (jar-only upgrades): a missing key never breaks an old file.
 */
public final class Settings {

    // storage
    public final String storageType;
    public final String sqliteFile;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlDatabase;
    public final String mysqlUser;
    public final String mysqlPassword;
    public final String mysqlParams;
    public final int saveIntervalSeconds;

    // general
    public final int combatTagSeconds;
    public final boolean closeMenuOnCombat;
    public final int swapCooldownSeconds;
    public final int defaultStorage;
    public final int maxStorage;
    public final int defaultIncubator;
    public final int maxIncubator;
    public final double gemPerMinute;
    public final int clickCooldownTicks;
    public final int inputTimeoutSeconds;
    public final boolean hideWhenInvisible;

    // visibility
    public final int viewDistance;
    public final int maxVisiblePerPlayer;
    public final String nametagFormat;
    public final double nametagOffset;
    public final int nametagUpdateTicks;

    // follow
    public final double followSide;
    public final double followBack;
    public final double followHeight;
    public final double flyHeight;
    public final double startDistance;
    public final double teleportDistance;
    public final double maxSpeed;
    public final double flyMaxSpeed;
    public final int wanderAfterTicks;
    public final double wanderRadius;
    public final int lookReactTicks;
    public final int lookReactCooldownSeconds;
    public final double sitMaxDistance;
    public final double combatViewCone;
    public final double combatNear;
    public final double combatRise;
    public final int combatEndTicks;

    // fast travel
    public final double fastTravelSpeed;
    public final int fastTravelSustainTicks;
    public final int fastTravelLandTicks;
    public final int teleportAppearTicks;
    public final int respawnAppearTicks;
    public final int joinAppearTicks;


    // bond
    public final int bondStart;
    public final int bondPetGain;
    /** 0.6.0: false = pets get XP only from candies (xp-sources.yml is ignored). */
    public final boolean activityXp;
    /** 0.8.0: every skill's damage / heal + shield, on top of abilities.yml. */
    public final double abilityDamageMultiplier;
    public final double abilityHealMultiplier;
    /** 0.7.0: levels where the pet stops at full EXP until a breakthrough (only below the rarity's max level). */
    public final java.util.Set<Integer> breakthroughLevels;
    public final long bondPetCooldownMillis;
    public final int bondFoodMaxPerDay;
    public final long neglectMillis;
    public final int neglectDecay;
    public final List<BondTier> bondTiers;

    // fullness
    public final int fullnessStart;
    public final int drainMinutesPerPoint;
    public final int littleHungry;

    // pvp
    public final double defaultPvpMultiplier;
    public final boolean abilityKnockback;

    // rename
    public final int renameMin;
    public final int renameMax;
    public final int renameCooldownSeconds;
    public final List<String> bannedWords;

    // chat
    public final int chatCooldownSeconds;
    public final int idleChatMinSeconds;
    public final int idleChatMaxSeconds;
    public final boolean speechBubble;
    public final int speechBubbleTicks;
    public final int hungryRepeatSeconds;
    public final int lowHpPercent;
    public final int lowHpCooldownSeconds;
    public final List<Integer> birthdays;

    // release
    public final String releaseRefundCandy;

    public record BondTier(int min, String name, double stats) {
    }

    public Settings(YamlConfiguration c) {
        storageType = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        sqliteFile = c.getString("storage.sqlite-file", "data.db");
        mysqlHost = c.getString("storage.mysql.host", "localhost");
        mysqlPort = c.getInt("storage.mysql.port", 3306);
        mysqlDatabase = c.getString("storage.mysql.database", "boxpet");
        mysqlUser = c.getString("storage.mysql.user", "root");
        mysqlPassword = c.getString("storage.mysql.password", "");
        mysqlParams = c.getString("storage.mysql.params", "useSSL=false&characterEncoding=utf8");
        saveIntervalSeconds = Math.max(10, c.getInt("storage.save-interval-seconds", 60));

        combatTagSeconds = Math.max(1, c.getInt("combat-tag-seconds", 10));
        closeMenuOnCombat = c.getBoolean("close-menu-on-combat", true);
        swapCooldownSeconds = Math.max(0, c.getInt("swap-cooldown-seconds", 10));
        defaultStorage = Math.max(1, c.getInt("storage-slots.default", 20));
        maxStorage = Math.max(defaultStorage, c.getInt("storage-slots.max", 200));
        defaultIncubator = Math.max(1, c.getInt("incubator.default-slots", 1));
        maxIncubator = Math.max(defaultIncubator, Math.min(7, c.getInt("incubator.max-slots", 5)));
        gemPerMinute = Math.max(0, c.getDouble("incubator.gem-per-minute", 1));
        clickCooldownTicks = Math.max(0, c.getInt("menu.click-cooldown-ticks", 4));
        inputTimeoutSeconds = Math.max(5, c.getInt("menu.input-timeout-seconds", 30));
        hideWhenInvisible = c.getBoolean("hide-when-invisible", true);

        viewDistance = Math.max(8, c.getInt("visibility.view-distance", 24));
        maxVisiblePerPlayer = Math.max(1, c.getInt("visibility.max-visible-per-player", 10));
        nametagFormat = c.getString("visibility.nametag", "%name% <dark_gray>[%level_colour%Lv.%level%<dark_gray>]%hungry%");
        nametagOffset = c.getDouble("visibility.nametag-offset", 0.35);
        nametagUpdateTicks = Math.max(1, Math.min(20, c.getInt("visibility.nametag-update-ticks", 3)));

        followSide = c.getDouble("follow.side", 1.2);
        followBack = c.getDouble("follow.back", 0.8);
        followHeight = c.getDouble("follow.height", 0.0);
        flyHeight = c.getDouble("follow.fly-height", 1.0);
        startDistance = Math.max(0.5, c.getDouble("follow.start-distance", 3.0));
        teleportDistance = Math.max(4, c.getDouble("follow.teleport-distance", 12.0));
        maxSpeed = Math.max(0.05, c.getDouble("follow.max-speed", 0.9));
        flyMaxSpeed = Math.max(0.05, c.getDouble("follow.fly-max-speed", 1.5));
        wanderAfterTicks = Math.max(20, c.getInt("follow.wander-after-seconds", 10) * 20);
        wanderRadius = Math.max(0, c.getDouble("follow.wander-radius", 2.0));
        lookReactTicks = Math.max(10, (int) Math.round(c.getDouble("follow.look-react-seconds", 2.0) * 20));
        lookReactCooldownSeconds = Math.max(0, c.getInt("follow.look-react-cooldown-seconds", 60));
        sitMaxDistance = Math.max(4, c.getDouble("follow.sit-max-distance", 15.0));
        combatViewCone = Math.max(10, Math.min(170, c.getDouble("follow.combat.view-cone-degrees", 60)));
        combatNear = Math.max(1, c.getDouble("follow.combat.near-distance", 4.0));
        combatRise = Math.max(0.5, c.getDouble("follow.combat.rise", 1.4));
        combatEndTicks = Math.max(0, c.getInt("follow.combat.end-seconds", 10) * 20);

        fastTravelSpeed = Math.max(1, c.getDouble("fast-travel.speed", 9.0)) / 20.0;
        fastTravelSustainTicks = Math.max(1, c.getInt("fast-travel.sustain-ticks", 20));
        fastTravelLandTicks = Math.max(1, c.getInt("fast-travel.land-ticks", 30));
        teleportAppearTicks = Math.max(1, c.getInt("appear.after-teleport-ticks", 5));
        respawnAppearTicks = Math.max(1, c.getInt("appear.after-respawn-ticks", 20));
        joinAppearTicks = Math.max(1, c.getInt("appear.after-join-ticks", 20));


        bondStart = clamp(c.getInt("bond.start", 50));
        bondPetGain = Math.max(0, c.getInt("bond.pet-gain", 1));
        activityXp = c.getBoolean("xp.from-activities", false);
        abilityDamageMultiplier = Math.max(0, c.getDouble("abilities.damage-multiplier", 1.4));
        abilityHealMultiplier = Math.max(0, c.getDouble("abilities.heal-multiplier", 1.3));
        breakthroughLevels = java.util.Set.copyOf(c.contains("breakthrough.levels")
                ? c.getIntegerList("breakthrough.levels") : List.of(9, 19, 29, 39, 49, 59));
        bondPetCooldownMillis = Math.max(0, c.getLong("bond.pet-cooldown-minutes", 240)) * 60_000L;
        bondFoodMaxPerDay = Math.max(0, c.getInt("bond.food-max-per-day", 5));
        neglectMillis = Math.max(1, c.getLong("bond.neglect-hours", 24)) * 3_600_000L;
        neglectDecay = Math.max(0, c.getInt("bond.neglect-decay", 5));
        bondTiers = tiers(c.getConfigurationSection("bond.tiers"));

        fullnessStart = clamp(c.getInt("fullness.start", 50));
        drainMinutesPerPoint = Math.max(1, c.getInt("fullness.drain-minutes-per-point", 6));
        littleHungry = clamp(c.getInt("fullness.little-hungry-below", 30));

        defaultPvpMultiplier = Math.max(0, c.getDouble("abilities.pvp-multiplier", 0.6));
        abilityKnockback = c.getBoolean("abilities.knockback", false);

        renameMin = Math.max(1, c.getInt("rename.min-length", 3));
        renameMax = Math.max(renameMin, c.getInt("rename.max-length", 16));
        renameCooldownSeconds = Math.max(0, c.getInt("rename.cooldown-seconds", 30));
        bannedWords = lower(c.getStringList("rename.banned-words"));

        chatCooldownSeconds = Math.max(0, c.getInt("pet-chat.cooldown-seconds", 20));
        idleChatMinSeconds = Math.max(30, c.getInt("pet-chat.idle-min-seconds", 600));
        idleChatMaxSeconds = Math.max(idleChatMinSeconds, c.getInt("pet-chat.idle-max-seconds", 1200));
        speechBubble = c.getBoolean("pet-chat.speech-bubble", true);
        speechBubbleTicks = Math.max(10, c.getInt("pet-chat.speech-bubble-ticks", 60));
        hungryRepeatSeconds = Math.max(60, c.getInt("pet-chat.hungry-repeat-seconds", 600));
        lowHpPercent = Math.max(1, Math.min(99, c.getInt("pet-chat.owner-low-hp-percent", 30)));
        lowHpCooldownSeconds = Math.max(5, c.getInt("pet-chat.owner-low-hp-cooldown-seconds", 60));
        birthdays = c.contains("pet-chat.birthdays") ? c.getIntegerList("pet-chat.birthdays") : List.of(7, 30, 100, 365);

        releaseRefundCandy = c.getString("release.refund-candy", "");
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (s != null && !s.isBlank()) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static List<BondTier> tiers(ConfigurationSection s) {
        List<BondTier> out = new ArrayList<>();
        if (s != null) {
            for (String key : s.getKeys(false)) {
                ConfigurationSection t = s.getConfigurationSection(key);
                if (t == null) continue;
                out.add(new BondTier(t.getInt("min", 0), t.getString("name", key), t.getDouble("stats", 100) / 100.0));
            }
        }
        if (out.isEmpty()) {
            out.add(new BondTier(90, "Gắn bó", 1.10));
            out.add(new BondTier(60, "Thân thiết", 1.00));
            out.add(new BondTier(30, "Bình thường", 0.90));
            out.add(new BondTier(0, "Xa cách", 0.75));
        }
        out.sort((a, b) -> Integer.compare(b.min(), a.min()));
        return out;
    }

    public BondTier bondTier(int bond) {
        for (BondTier t : bondTiers) if (bond >= t.min()) return t;
        return bondTiers.get(bondTiers.size() - 1);
    }
}

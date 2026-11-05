package com.dex.boxpet.pet;

import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Settings;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * MMOItems stats through MythicLib stat modifiers (spec 5). Key {@code boxpet} for the active pet, key
 * {@code boxpet_collection} for collection milestones. Re-applying always removes the key first, so a stat is never
 * counted twice.
 */
public final class StatService {

    public static final String PET_KEY = "boxpet";
    public static final String COLLECTION_KEY = "boxpet_collection";

    private final Logger log;
    private boolean warned;

    public StatService(Logger log) {
        this.log = log;
    }

    /** Final stats of a pet: {@code def × rarity × stage × bond tier}. */
    public static Map<String, Double> petStats(PetType type, int level, double rarityMultiplier, int stage,
                                               double bondMultiplier) {
        Map<String, Double> out = new LinkedHashMap<>();
        double stageMul = type.stage(stage).statMultiplier();
        for (PetType.StatDef def : type.stats) {
            double v = def.at(level) * rarityMultiplier * stageMul * bondMultiplier;
            out.merge(def.stat(), Math.round(v * 100.0) / 100.0, Double::sum);
        }
        return out;
    }

    /**
     * 0.8.0 final stats: the pet's stats × rarity × config stats.multiplier × (1 + breakthroughs × bonus%) × stage ×
     * bond, plus the 3rd stat once unlocked (it grows from its unlock level).
     */
    public static Map<String, Double> petStats(com.dex.boxpet.config.Settings s, PetType type,
                                               com.dex.boxpet.config.Rarity r, int level, int stage, double bondMultiplier) {
        double bt = 1 + s.breakthroughsPassed(level, r.maxLevel()) * s.breakthroughStatBonus / 100.0;
        double mul = r.statMultiplier() * s.statMultiplier * bt;
        Map<String, Double> out = petStats(type, level, mul, stage, bondMultiplier);
        int unlock = s.bonusUnlock(r.id());
        if (type.bonusStat != null && unlock > 0 && level >= unlock) {
            double v = (type.bonusStat.base() + type.bonusStat.perLevel() * (level - unlock)) * mul
                    * type.stage(stage).statMultiplier() * bondMultiplier;
            out.merge(type.bonusStat.stat(), Math.round(v * 100.0) / 100.0, Double::sum);
        }
        return out;
    }

    public static boolean relative(PetType type, String stat) {
        for (PetType.StatDef d : type.stats) if (d.stat().equals(stat)) return d.relative();
        return false;
    }

    private @Nullable StatMap map(Player player) {
        try {
            if (!MMOPlayerData.has(player.getUniqueId())) return null;
            return MMOPlayerData.get(player).getStatMap();
        } catch (RuntimeException | LinkageError ex) {
            if (!warned) {
                warned = true;
                log.log(Level.WARNING, "MythicLib stat map unavailable (reported once)", ex);
            }
            return null;
        }
    }

    /** @return false when MythicLib has not loaded the player yet (retry later) */
    public boolean applyPet(Player player, ActivePet pet, Settings settings) {
        // a hungry pet (fullness 0) gives no stats at all: at least 1 fullness is needed (0.5.2)
        Map<String, Double> stats = pet.hungry() ? Map.of() : petStats(settings, pet.type, pet.rarity, pet.data.level,
                pet.data.stage, settings.bondTier(pet.data.bond).stats());
        return apply(player, PET_KEY, stats, pet.type);
    }

    public boolean applyCollection(Player player, Map<String, Double> stats) {
        return apply(player, COLLECTION_KEY, stats, null);
    }

    private boolean apply(Player player, String key, Map<String, Double> stats, @Nullable PetType type) {
        StatMap map = map(player);
        if (map == null) return false;
        clear(map, key);
        MMOPlayerData data = map.getData();
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            if (e.getValue() == 0) continue;
            ModifierType mt = type != null && relative(type, e.getKey()) ? ModifierType.RELATIVE : ModifierType.FLAT;
            try {
                new StatModifier(key, e.getKey(), e.getValue(), mt, io.lumine.mythic.lib.api.player.EquipmentSlot.OTHER,
                        io.lumine.mythic.lib.player.modifier.ModifierSource.OTHER).register(data);
            } catch (RuntimeException ex) {
                log.warning("MythicLib refused stat " + e.getKey() + " (" + ex.getMessage() + "): check the stat id in the pet file");
            }
        }
        return true;
    }

    public void clearPet(Player player) {
        StatMap map = map(player);
        if (map != null) clear(map, PET_KEY);
    }

    public void clearAll(Player player) {
        StatMap map = map(player);
        if (map == null) return;
        clear(map, PET_KEY);
        clear(map, COLLECTION_KEY);
        clear(map, com.dex.boxpet.ability.AbilityService.BUFF_KEY);
        clear(map, com.dex.boxpet.ability.AbilityService.DEBUFF_KEY);
    }

    /** "player|key|stat" -> when the temporary modifier ends (epoch ms). */
    private final Map<String, Long> tempUntil = new java.util.HashMap<>();

    /**
     * 0.8.0: a stat modifier for {@code ticks} (skill buffs on the owner, stats taken from an enemy). Re-applying the
     * same key and stat refreshes it instead of stacking.
     */
    public void tempStat(org.bukkit.plugin.Plugin plugin, Player player, String key, String stat, double value,
                         boolean relative, int ticks) {
        StatMap map = map(player);
        if (map == null || value == 0) return;
        try {
            map.getInstance(stat).removeIf(key::equals);
            new StatModifier(key, stat, value, relative ? ModifierType.RELATIVE : ModifierType.FLAT,
                    io.lumine.mythic.lib.api.player.EquipmentSlot.OTHER,
                    io.lumine.mythic.lib.player.modifier.ModifierSource.OTHER).register(map.getData());
        } catch (RuntimeException ex) {
            log.warning("MythicLib refused stat " + stat + " (" + ex.getMessage() + "): check abilities.yml");
            return;
        }
        String id = player.getUniqueId() + "|" + key + "|" + stat;
        long until = System.currentTimeMillis() + ticks * 50L;
        tempUntil.put(id, until);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Long u = tempUntil.get(id);
            if (u == null || u > System.currentTimeMillis() + 60) return;
            tempUntil.remove(id);
            StatMap m = map(player);
            if (m == null) return;
            try {
                m.getInstance(stat).removeIf(key::equals);
            } catch (RuntimeException ignored) {
                // gone already
            }
        }, ticks);
    }

    public void clearOwnBuff(Player player) {
        tempUntil.keySet().removeIf(k -> k.startsWith(player.getUniqueId() + "|" + com.dex.boxpet.ability.AbilityService.BUFF_KEY + "|"));
        StatMap map = map(player);
        if (map != null) clear(map, com.dex.boxpet.ability.AbilityService.BUFF_KEY);
    }

    public void clearTemp(Player player) {
        tempUntil.keySet().removeIf(k -> k.startsWith(player.getUniqueId() + "|"));
        StatMap map = map(player);
        if (map == null) return;
        clear(map, com.dex.boxpet.ability.AbilityService.BUFF_KEY);
        clear(map, com.dex.boxpet.ability.AbilityService.DEBUFF_KEY);
    }

    private static void clear(StatMap map, String key) {
        for (StatInstance instance : map.getInstances()) {
            try {
                instance.removeIf(key::equals);
            } catch (RuntimeException ignored) {
                // no modifier with that key
            }
        }
    }
}

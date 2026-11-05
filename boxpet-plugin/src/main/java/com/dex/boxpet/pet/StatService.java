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
        Map<String, Double> stats = petStats(pet.type, pet.data.level, pet.rarity.statMultiplier(), pet.data.stage,
                settings.bondTier(pet.data.bond).stats());
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

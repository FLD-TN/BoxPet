package com.dex.boxpet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxpet.config.Ability;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 0.20.5: the owner's balance rules, checked on the default files so a later edit cannot break them silently.
 * <ul>
 *   <li>cooldown by rarity: common 20-30 s, rare 30-40 s, epic / legendary 50-60 s</li>
 *   <li>combat common / rare: one target, pure damage; epic: several targets, pure damage; legendary: several targets
 *       plus a small owner buff</li>
 *   <li>per-cast damage strictly higher at every rarity</li>
 *   <li>economy: exactly 3 quest-only pets (common, rare, epic)</li>
 *   <li>two basic stats per pet, a unique pair per rarity</li>
 * </ul>
 */
class BalanceTest {

    private record Pet(String id, String rarity, String role, Ability skill, List<String> stats) {
    }

    private static final List<Pet> PETS = new ArrayList<>();
    private static final Set<String> BASIC = Set.of("ATTACK_DAMAGE", "CRITICAL_STRIKE_CHANCE", "CRITICAL_STRIKE_POWER",
            "PVP_DAMAGE", "PVE_DAMAGE", "MAX_HEALTH", "DEFENSE", "DAMAGE_REDUCTION", "PVP_DAMAGE_REDUCTION", "DODGE_RATING");
    private static final List<String> ORDER = List.of("common", "rare", "epic", "legendary");

    private static YamlConfiguration load(File f) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.load(f);
        return y;
    }

    @BeforeAll
    static void loadAll() throws Exception {
        var in = Objects.requireNonNull(BalanceTest.class.getClassLoader().getResourceAsStream("abilities.yml"));
        YamlConfiguration ab = new YamlConfiguration();
        ab.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        File dir = new File(Objects.requireNonNull(BalanceTest.class.getClassLoader().getResource("pets")).toURI());
        for (File f : Objects.requireNonNull(dir.listFiles((d, n) -> n.endsWith(".yml")))) {
            YamlConfiguration y = load(f);
            String id = f.getName().replace(".yml", "");
            ConfigurationSection s = ab.getConfigurationSection("abilities." + y.getString("ability"));
            assertNotNull(s, id + " skill");
            ConfigurationSection st = y.getConfigurationSection("stats");
            assertNotNull(st, id + " stats");
            PETS.add(new Pet(id, y.getString("rarity"), y.getString("role"), new Ability(y.getString("ability"), s, 1.4, 1.3),
                    new ArrayList<>(st.getKeys(false))));
        }
    }

    /** Damage of one cast before the rarity multiplier (every hit, weights included). */
    private static double perCast(Ability a) {
        if (a.type == Ability.Type.NOVA) {
            double sum = 0;
            for (int i = 0; i < a.novaHits.size(); i++) sum += a.novaWeight(i);
            return a.damage.base() * sum;
        }
        return a.damage.base() * a.hits;
    }

    private static boolean multiTarget(Ability a) {
        return a.type == Ability.Type.NOVA || a.areaRadius > 0 || a.chainTargets > 0 || a.pierce || a.multiTargets > 1;
    }

    @Test
    void cooldownsByRarity() {
        Map<String, int[]> range = Map.of("common", new int[]{20, 30}, "rare", new int[]{30, 40}, "epic", new int[]{50, 60},
                "legendary", new int[]{50, 60});
        for (Pet p : PETS) {
            if (p.role.equals("boost")) continue;
            double cd = p.skill.cooldownTicks / 20.0;
            int[] r = range.get(p.rarity);
            assertTrue(cd >= r[0] && cd <= r[1], p.id + " cooldown " + cd + "s for " + p.rarity);
            assertTrue(p.skill.fullCooldownTicks() >= p.skill.cooldownTicks, p.id + " full cooldown");
        }
    }

    @Test
    void combatShapeByRarity() {
        for (Pet p : PETS) {
            if (!p.role.equals("combat")) continue;
            Ability a = p.skill;
            assertTrue(a.type.combat(), p.id + " combat skill");
            switch (p.rarity) {
                case "common", "rare" -> assertFalse(multiTarget(a), p.id + " must hit one target");
                default -> assertTrue(multiTarget(a), p.id + " must hit several targets");
            }
            boolean extra = !a.enemyStat.isEmpty() || a.debuff != Ability.Debuff.NONE || !a.buffStat.isEmpty()
                    || !a.buffChoices.isEmpty();
            if (p.rarity.equals("legendary")) {
                assertTrue(!a.buffStat.isEmpty() || a.markBonus.base() > 0, p.id + " legendary needs a small owner buff");
                assertTrue(a.enemyStat.isEmpty() && a.debuff == Ability.Debuff.NONE, p.id + " legendary: buff, not debuff");
            } else {
                assertFalse(extra, p.id + " common-epic combat is pure damage");
            }
        }
    }

    @Test
    void damageGrowsWithRarity() {
        Map<String, double[]> minMax = new HashMap<>();
        for (Pet p : PETS) {
            if (!p.role.equals("combat")) continue;
            double d = perCast(p.skill);
            assertTrue(d > 0, p.id + " deals damage");
            minMax.merge(p.rarity, new double[]{d, d}, (x, y) -> new double[]{Math.min(x[0], y[0]), Math.max(x[1], y[1])});
        }
        for (int i = 1; i < ORDER.size(); i++) {
            double[] low = minMax.get(ORDER.get(i - 1)), high = minMax.get(ORDER.get(i));
            assertTrue(high[0] > low[1], ORDER.get(i) + " min " + high[0] + " <= " + ORDER.get(i - 1) + " max " + low[1]);
        }
    }

    @Test
    void economyIsThreeQuestPets() {
        Set<String> rarities = new TreeSet<>();
        for (Pet p : PETS) {
            if (!p.role.equals("boost")) continue;
            assertEquals(Ability.Type.BOOST, p.skill.type, p.id);
            for (Ability.Boost b : p.skill.boosts) assertEquals("quest", b.type(), p.id + " quest only");
            assertTrue(rarities.add(p.rarity), p.id + " second economy pet of " + p.rarity);
        }
        assertEquals(Set.of("common", "rare", "epic"), rarities);
    }

    @Test
    void statPairsBasicAndUnique() {
        Map<String, String> seen = new HashMap<>();
        for (Pet p : PETS) {
            assertEquals(2, p.stats.size(), p.id + " two stats");
            for (String s : p.stats) assertTrue(BASIC.contains(s), p.id + " uses non-basic stat " + s);
            String key = p.rarity + ":" + new TreeSet<>(p.stats);
            String other = seen.put(key, p.id);
            assertTrue(other == null, p.id + " has the same stats as " + other);
        }
    }

    @Test
    void loreSaysKeDichNotNo() throws Exception {
        var in = Objects.requireNonNull(BalanceTest.class.getClassLoader().getResourceAsStream("abilities.yml"));
        YamlConfiguration ab = new YamlConfiguration();
        ab.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        Set<String> used = new HashSet<>();
        for (Pet p : PETS) used.add(p.skill.id);
        for (String id : used) {
            for (String line : ab.getStringList("abilities." + id + ".description")) {
                assertFalse((" " + line + " ").matches(".*[\\s,]nó[\\s,.].*"), id + ": " + line);
            }
        }
    }
}

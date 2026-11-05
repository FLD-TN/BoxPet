package com.dex.boxpet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dex.boxpet.config.Durations;
import com.dex.boxpet.config.Expr;
import com.dex.boxpet.config.Levels;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.pet.StatService;
import com.dex.boxpet.text.Format;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LogicTest {

    @Test
    void expressions() {
        assertEquals(105, Expr.compile("100 + 5 * L^2").applyAsDouble(1), 1e-9);
        assertEquals(4600, Expr.compile("100 + 5 * L^2").applyAsDouble(30), 1e-9);
        assertEquals(-3, Expr.compile("-(1+2)").applyAsDouble(0), 1e-9);
        assertEquals(2, Expr.compile("floor(L / 2)").applyAsDouble(5), 1e-9);
        assertEquals(512, Expr.compile("2^3^2").applyAsDouble(0), 1e-9, "right associative");
        assertThrows(IllegalArgumentException.class, () -> Expr.compile("L +"));
        assertThrows(IllegalArgumentException.class, () -> Expr.compile("foo(L)"));
    }

    /** The balance table of BOXPET_SPEC 9.1. */
    @Test
    void defaultCurveMatchesSpecTable() {
        Levels l = new Levels(Levels.DEFAULT_FORMULA);
        assertEquals(45_675, l.total(30), 1e-6);
        assertEquals(106_600, l.total(40), 1e-6);
        assertEquals(207_025, l.total(50), 1e-6);
        assertEquals(356_950, l.total(60), 1e-6);
    }

    @Test
    void durations() {
        assertEquals(600, Durations.seconds("10m", 0));
        assertEquals(5400, Durations.seconds("1h30m", 0));
        assertEquals(90, Durations.seconds("90", 0));
        assertEquals(7, Durations.seconds("nope", 7));
        assertEquals(172800, Durations.seconds("2d", 0));
    }

    @Test
    void formats() {
        assertEquals("1.250", Format.number(1250));
        assertEquals("12,5", Format.decimal(12.5));
        assertEquals("<a>■■■■■<b>■■■■■", Format.bar(50, 100, 10, "<a>", "<b>"));
        assertEquals("12:05", Format.clock(725));
        assertEquals("1:00:01", Format.clock(3601));
    }

    @Test
    void petStatsScaleWithLevelRarityStageAndBond() {
        PetType t = new PetType("t", "T", "f", PetType.Role.COMBAT, "", Material.EGG, List.of("common"), 1,
                List.of(new PetType.Stage("m", 1, 1, 0, 0, 1.0), new PetType.Stage("m2", 1, 20, 0, 0, 1.5)), Map.of(),
                null, null, null, false, true, null, List.of(new PetType.StatDef("ATTACK_DAMAGE", 1.0, 0.05, false)),
                "", "f", List.of());
        Map<String, Double> lv1 = StatService.petStats(t, 1, 1.0, 0, 1.0);
        assertEquals(1.0, lv1.get("ATTACK_DAMAGE"), 1e-9);
        // level 30, legendary x2.5, stage 2 x1.5, bond 75%
        Map<String, Double> lv30 = StatService.petStats(t, 30, 2.5, 1, 0.75);
        assertEquals(Math.round((1.0 + 0.05 * 29) * 2.5 * 1.5 * 0.75 * 100) / 100.0, lv30.get("ATTACK_DAMAGE"), 1e-9);
        assertTrue(lv30.get("ATTACK_DAMAGE") > lv1.get("ATTACK_DAMAGE"));
    }
}

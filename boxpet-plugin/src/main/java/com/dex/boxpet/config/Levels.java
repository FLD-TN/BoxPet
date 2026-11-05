package com.dex.boxpet.config;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * levels.yml (spec 9.1): XP needed to go from level L to L + 1, by formula ({@code mode: formula}) or by an
 * explicit table ({@code mode: table}, missing levels use the formula). Always at least 1.
 */
public final class Levels {

    public static final String DEFAULT_FORMULA = "100 + 5 * L^2";

    private final DoubleUnaryOperator formula;
    private final Map<Integer, Double> table = new HashMap<>();
    private final boolean useTable;

    public Levels(YamlConfiguration yml, Logger log) {
        String expr = yml.getString("formula", DEFAULT_FORMULA);
        DoubleUnaryOperator f;
        try {
            f = Expr.compile(expr);
            f.applyAsDouble(1);
        } catch (RuntimeException ex) {
            log.warning("levels.yml: formula '" + expr + "' is invalid (" + ex.getMessage() + "), using " + DEFAULT_FORMULA);
            f = Expr.compile(DEFAULT_FORMULA);
        }
        formula = f;
        useTable = "table".equalsIgnoreCase(yml.getString("mode", "formula"));
        ConfigurationSection t = yml.getConfigurationSection("table");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                try {
                    table.put(Integer.parseInt(k.trim()), Math.max(1, t.getDouble(k)));
                } catch (NumberFormatException ex) {
                    log.warning("levels.yml: table key '" + k + "' is not a level number");
                }
            }
        }
    }

    /** Levels with a single formula, for tests. */
    public Levels(String formula) {
        this.formula = Expr.compile(formula);
        this.useTable = false;
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public double need(int level) {
        if (useTable) {
            Double v = table.get(level + 1);
            if (v != null) return v;
        }
        double v = formula.applyAsDouble(level);
        return Double.isFinite(v) ? Math.max(1, v) : 1;
    }

    /** Total XP from level 1 to {@code level}. */
    public double total(int level) {
        double sum = 0;
        for (int l = 1; l < level; l++) sum += need(l);
        return sum;
    }
}

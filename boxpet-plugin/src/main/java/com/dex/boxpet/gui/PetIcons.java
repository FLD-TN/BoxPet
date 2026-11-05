package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.Ability;
import com.dex.boxpet.config.MenuTexts;
import com.dex.boxpet.config.Messages;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.pet.StatService;
import com.dex.boxpet.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The pet card shown in every menu (0.5.0 layout): a tag line (rarity, role), Phát triển (level, XP), Tình trạng
 * (bond, fullness, age), Chỉ số cộng thêm (MMOItems icons and colours) and the skill block.
 */
public final class PetIcons {

    private PetIcons() {
    }

    public static ItemStack icon(BoxPetPlugin plugin, PetType type) {
        ItemStack ia = plugin.itemsAdder().map(h -> h.item(type.icon)).orElse(null);
        return ia != null ? ia : new ItemStack(type.iconFallback == null ? Material.EGG : type.iconFallback);
    }

    public static String roleName(BoxPetPlugin plugin, PetType type) {
        return plugin.menus().text("roles." + type.role.id);
    }

    public static Rarity rarity(BoxPetPlugin plugin, PetType type) {
        Rarity r = plugin.rarities().get(type.rarity);
        return r == null ? plugin.rarities().lowest() : r;
    }

    public static String fullnessState(BoxPetPlugin plugin, int fullness) {
        String key = fullness >= 80 ? "full" : fullness >= plugin.settings().littleHungry ? "fed"
                : fullness > 0 ? "little-hungry" : "hungry";
        return plugin.menus().text("fullness-states." + key);
    }

    /** Exact time since hatching: "3 ngày 4 giờ", "2 giờ 15 phút", "5 phút 30 giây". */
    public static String age(PetData d) {
        return Format.duration(Math.max(0, (System.currentTimeMillis() - d.hatchedAt) / 1000));
    }

    /** The level in the colour of its band of ten (menus.yml level-colors). */
    public static String level(BoxPetPlugin plugin, int level) {
        List<String> colours = plugin.menus().list("level-colors");
        String c = colours.isEmpty() ? "#FFFFFF" : colours.get(Math.min(colours.size() - 1, Math.max(0, level / 10)));
        return "<" + c + ">" + level;
    }

    public static String boost(int tenths) {
        return "x" + (tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "," + (tenths % 10));
    }

    // ------------------------------------------------------------------ stats

    /** One "Chỉ số cộng thêm" row in the MMOItems style (menus.yml stat-display). */
    public static String statRow(BoxPetPlugin plugin, String stat, String value) {
        MenuTexts m = plugin.menus();
        String key = "stat-display." + stat;
        String row = m.text(key, "value", value);
        if (row.equals(key)) row = m.text("pet-card.stat-row", "stat", stat, "value", value);
        return row;
    }

    private static String signed(double v) {
        return (v >= 0 ? "+" : "") + Format.decimal(v);
    }

    public static List<String> statLines(BoxPetPlugin plugin, PetData d, PetType type) {
        Rarity r = rarity(plugin, type);
        Map<String, Double> stats = StatService.petStats(type, d.level, r.statMultiplier(), d.stage,
                plugin.settings().bondTier(d.bond).stats());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : stats.entrySet()) out.add(statRow(plugin, e.getKey(), signed(e.getValue())));
        if (out.isEmpty()) out.add(plugin.menus().text("pet-card.no-stats"));
        return out;
    }

    /** Library: value at level 1 and at the max level of the pet's rarity. */
    public static List<String> statRangeLines(BoxPetPlugin plugin, PetType type) {
        Rarity r = rarity(plugin, type);
        Map<String, Double> low = StatService.petStats(type, 1, r.statMultiplier(), 0, 1.0);
        Map<String, Double> high = StatService.petStats(type, r.maxLevel(), r.statMultiplier(), 0, 1.0);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : low.entrySet()) {
            String range = plugin.menus().text("library.range", "from", signed(e.getValue()),
                    "to", signed(high.getOrDefault(e.getKey(), e.getValue())));
            out.add(statRow(plugin, e.getKey(), range));
        }
        if (out.isEmpty()) out.add(plugin.menus().text("pet-card.no-stats"));
        return out;
    }

    // ------------------------------------------------------------------ skill

    /** Skill title, short description and one row per fact (menus.yml ability-rows). */
    public static List<String> abilityLines(BoxPetPlugin plugin, PetType type, int level) {
        MenuTexts m = plugin.menus();
        Ability a = plugin.abilities().get(type.ability);
        if (a == null) return List.of(m.text("pet-card.no-ability"));
        Rarity r = rarity(plugin, type);
        double mul = r.statMultiplier();
        List<String> out = new ArrayList<>();
        out.add(m.text("pet-card.ability-title", "ability", PetType.paint(a.color, a.name)));
        for (String line : a.description) out.add(m.text("pet-card.ability-line", "line", line));
        String kind = m.text("ability-kinds." + a.type.name().toLowerCase(Locale.ROOT));
        out.add(m.text("ability-rows.kind", "value", kind));
        switch (a.type) {
            case PROJECTILE, STRIKE, MARK -> {
                out.add(m.text("ability-rows.trigger", "value", m.text("ability-triggers.combat")));
                double dmg = round1(a.damage.at(level, mul));
                if (dmg > 0) out.add(m.text("ability-rows.damage", "value", Format.decimal(dmg)));
                if (a.type == Ability.Type.MARK) {
                    out.add(m.text("ability-rows.mark", "value", Format.decimal(round1(a.markBonus.at(level, mul))),
                            "seconds", Format.decimal(a.markTicks / 20.0)));
                }
                if (a.debuff != Ability.Debuff.NONE) {
                    out.add(m.text("ability-rows.debuff", "value", m.text("debuffs." + a.debuff.name().toLowerCase(Locale.ROOT)),
                            "seconds", Format.decimal(a.debuffTicks / 20.0)));
                }
                out.add(m.text("ability-rows.range", "value", Format.decimal(a.range)));
                out.add(m.text("ability-rows.pvp", "value", String.valueOf(Math.round(a.pvp(plugin.settings().defaultPvpMultiplier) * 100))));
            }
            case HEAL -> {
                out.add(m.text("ability-rows.trigger", "value", m.text("ability-triggers.low-hp", "percent", String.valueOf(a.healBelowPercent))));
                double heal = round1(a.heal.at(level, mul));
                out.add(m.text(a.healPercent ? "ability-rows.heal-percent" : "ability-rows.heal", "value", Format.decimal(heal)));
            }
            case SHIELD -> {
                out.add(m.text("ability-rows.trigger", "value", m.text("ability-triggers.low-hp", "percent", String.valueOf(a.shieldBelowPercent))));
                out.add(m.text("ability-rows.shield", "value", Format.decimal(round1(a.shieldHearts.at(level, mul))),
                        "seconds", Format.decimal(a.shieldTicks / 20.0)));
            }
            case POTION -> {
                out.add(m.text("ability-rows.trigger", "value", m.text("ability-triggers.always")));
                String effect = m.text("potions." + a.potion.toLowerCase(Locale.ROOT));
                if (effect.startsWith("potions.")) effect = a.potion;
                out.add(m.text("ability-rows.potion", "value", effect + " " + roman(a.potionAmplifier(r.index()) + 1)));
            }
            case BOOST -> {
                out.add(m.text("ability-rows.trigger", "value", m.text("ability-triggers.always")));
                int tenths = (int) Math.floor(a.boostTenths.at(level, mul));
                if (a.boostWhole) tenths -= tenths % 10;
                out.add(m.text("ability-rows.boost", "value", tenths > 0 ? boost(tenths) : "x0",
                        "type", m.text("boost-types." + a.boostType)));
            }
            default -> {
            }
        }
        if (a.type != Ability.Type.BOOST && a.type != Ability.Type.POTION) {
            out.add(m.text("ability-rows.cooldown", "value", Format.decimal(a.cooldownTicks / 20.0)));
        }
        if (a.type != Ability.Type.NONE) out.add(m.text("ability-rows.hungry-note"));
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // ------------------------------------------------------------------ cards

    /** Placeholders shared by the card name and lore. */
    public static String[] placeholders(BoxPetPlugin plugin, PetData d, PetType type) {
        Rarity r = rarity(plugin, type);
        boolean max = d.level >= r.maxLevel();
        double need = plugin.levels().need(d.level);
        return new String[]{
                "name", plugin.pets().displayName(d),
                "type", type.colouredName(),
                "id", d.shortId(),
                "role", roleName(plugin, type),
                "rarity", r.coloured(),
                "level", level(plugin, d.level),
                "xp", max ? plugin.menus().text("pet-card.xp-max") : plugin.menus().text("pet-card.xp-value",
                        "xp", Format.number((long) Math.floor(d.xp)), "need", Format.number((long) Math.ceil(need))),
                "xp_bar", max ? Format.bar(1, 1, 10, "<#FFD166>", "<dark_gray>") : Format.bar(d.xp, need, 10, "<#7CFC9A>", "<dark_gray>"),
                "bond", String.valueOf(d.bond),
                "bond_tier", plugin.settings().bondTier(d.bond).name(),
                "bond_bar", Format.bar(d.bond, 100, 10, "<#FFB3C7>", "<dark_gray>"),
                "fullness", String.valueOf(d.fullness),
                "fullness_bar", Format.bar(d.fullness, 100, 10, d.fullness > 0 ? "<#FFD166>" : "<#FF8A8A>", "<dark_gray>"),
                "fullness_state", fullnessState(plugin, d.fullness),
                "age", age(d),
                "stage", String.valueOf(d.stage + 1),
                "stages", String.valueOf(type.stages.size())
        };
    }

    /** The full card of an owned pet. {@code statusLines}: lines under the card (active, hungry...). */
    public static ItemStack card(BoxPetPlugin plugin, PetData d, List<String> statusLines, List<String> actions) {
        PetType type = plugin.pets().type(d);
        if (type == null) {
            return Menu.icon(Material.BARRIER, "<#FF8A8A>" + d.type, List.of(plugin.menus().text("pet-card.unknown-type")));
        }
        Map<String, List<String>> blocks = new HashMap<>();
        blocks.put("stats", statLines(plugin, d, type));
        blocks.put("ability", abilityLines(plugin, type, d.level));
        blocks.put("status", statusLines);
        blocks.put("actions", actions);
        String[] ph = placeholders(plugin, d, type);
        return Menu.decorate(icon(plugin, type), plugin.menus().name("pet-card", ph), plugin.menus().lore("pet-card", blocks, ph));
    }

    /** Library card: what a pet type gives, at level 1 and at its max level. */
    public static ItemStack libraryCard(BoxPetPlugin plugin, PetType type, boolean owned, List<String> actions) {
        Rarity r = rarity(plugin, type);
        Map<String, List<String>> blocks = new HashMap<>();
        blocks.put("stats", statRangeLines(plugin, type));
        blocks.put("ability", abilityLines(plugin, type, 1));
        blocks.put("actions", actions);
        String[] ph = {
                "name", type.colouredName(),
                "role", roleName(plugin, type),
                "rarity", r.coloured(),
                "max_level", level(plugin, r.maxLevel()),
                "hatch", Format.duration(r.hatchSeconds()),
                "points", String.valueOf(r.collectionPoints()),
                "owned", plugin.menus().text(owned ? "library.owned" : "library.not-owned")
        };
        return Menu.decorate(icon(plugin, type), plugin.menus().name("library.card", ph), plugin.menus().lore("library.card", blocks, ph));
    }

    public static List<String> status(BoxPetPlugin plugin, PetData d, boolean active) {
        List<String> out = new ArrayList<>();
        if (active) out.add(plugin.menus().text("pet-card.status-active"));
        if (d.fullness <= 0) out.add(plugin.menus().text("pet-card.status-hungry"));
        return out;
    }

    /** Placeholders helper for callers that need the coloured level only. */
    public static String levelText(BoxPetPlugin plugin, PetData d) {
        return Messages.apply(plugin.menus().text("pet-card.level-inline"), "level", level(plugin, d.level));
    }
}

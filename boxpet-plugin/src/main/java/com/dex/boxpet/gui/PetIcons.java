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

    /** Bar and number colours of bond and fullness (different from the section titles). */
    static final String BOND = "<#FF8FC7>";
    static final String FULLNESS = "<#FF9F5A>";
    static final String HUNGRY = "<#FF4D4D>";

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

    /** Quest: "30%" more reward (like the rank perks). Mining / mob: the multiplier it brings, "x2", "x1,3". */
    /** 0.13.1: the boost number in the menus.yml format (x2 / 10%). */
    public static String boostText(MenuTexts m, String type, int tenths) {
        String raw = boostText(type, tenths);
        boolean quest = type.equals("quest");
        String value = quest ? raw.substring(0, raw.length() - 1) : raw.substring(1);
        return quest ? m.textOr("ability-numbers.boost-percent", "%value%%", "value", value)
                : m.textOr("ability-numbers.boost-multiplier", "x%value%", "value", value);
    }

    public static String boostText(String type, int tenths) {
        if (tenths <= 0) return type.equals("quest") ? "0%" : "x1";
        if (type.equals("quest")) return (tenths * 10) + "%";
        return boost(10 + tenths);
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
        Map<String, Double> stats = StatService.petStats(plugin.settings(), type, r, d.level, d.stage,
                plugin.settings().bondTier(d.bond).stats());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : stats.entrySet()) out.add(statRow(plugin, e.getKey(), signed(e.getValue())));
        if (out.isEmpty()) out.add(plugin.menus().text("pet-card.no-stats"));
        bonusLocked(plugin, type, r, d.level, out);
        return out;
    }

    /** Library: the base value of each stat (level 1, with the pet's rarity). */
    public static List<String> statBaseLines(BoxPetPlugin plugin, PetType type) {
        Rarity r = rarity(plugin, type);
        Map<String, Double> base = StatService.petStats(plugin.settings(), type, r, 1, 0, 1.0);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : base.entrySet()) out.add(statRow(plugin, e.getKey(), signed(e.getValue())));
        if (out.isEmpty()) out.add(plugin.menus().text("pet-card.no-stats"));
        bonusLocked(plugin, type, r, 1, out);
        return out;
    }

    private static String statName(BoxPetPlugin plugin, String stat) {
        if (stat == null || stat.isEmpty()) return "";
        String n = plugin.menus().text("stat-names." + stat);
        return n.startsWith("stat-names.") ? stat : n;
    }

    /** The 3rd stat before its breakthrough: a grey "locked" line. */
    private static void bonusLocked(BoxPetPlugin plugin, PetType type, Rarity r, int level, List<String> out) {
        int unlock = plugin.settings().bonusUnlock(r.id());
        if (type.bonusStat == null || unlock <= 0 || level >= unlock) return;
        String name = plugin.menus().text("stat-names." + type.bonusStat.stat());
        if (name.startsWith("stat-names.")) name = type.bonusStat.stat();
        out.add(plugin.menus().text("pet-card.bonus-locked", "stat", name, "level", String.valueOf(unlock)));
    }

    // ------------------------------------------------------------------ skill

    /**
     * "Kỹ năng:", the skill name in 〈 〉, the description of abilities.yml with its numbers coloured, then the
     * cooldown on its own line (0.5.2 layout).
     */
    public static List<String> abilityLines(BoxPetPlugin plugin, PetType type, int level) {
        Ability a = plugin.abilities().get(type.ability);
        if (a == null) return List.of(plugin.menus().text("pet-card.no-ability"));
        List<String> out = new ArrayList<>(skillLines(plugin, type, level, a));
        // 0.9.0: legendary pets have a second skill, one blank line under the first
        Ability b = plugin.abilities().get(type.ability2);
        if (b != null) {
            out.add("");
            if (level >= plugin.settings().secondSkillLevel) out.addAll(skillLines(plugin, type, level, b));
            else out.add(plugin.menus().text("pet-card.ability-locked", "ability", b.name));
        }
        return out;
    }

    private static List<String> skillLines(BoxPetPlugin plugin, PetType type, int level, Ability a) {
        MenuTexts m = plugin.menus();
        Rarity r = rarity(plugin, type);
        double mul = r.statMultiplier();
        int tenths = (int) Math.floor(a.boostTenths.at(level, mul));
        if (a.boostWhole) tenths -= tenths % 10;
        String potion = m.text("potions." + a.potion.toLowerCase(Locale.ROOT));
        if (potion.startsWith("potions.")) potion = a.potion;
        String[] ph = {
                "damage", num(m, "damage", round1(a.damage.at(level, mul))),
                "damage_final", num(m, "damage", round1(a.damage.at(level, mul) * a.novaWeight(a.novaHits.size() - 1))),
                "hit_count", num(m, "time", a.novaHits.size()),
                "heal", num(m, "heal", round1(a.heal.at(level, mul))),
                "hearts", num(m, "shield", round1(a.shieldHearts.at(level, mul))),
                "percent", num(m, "percent", a.type == Ability.Type.SHIELD ? a.shieldBelowPercent
                        : a.type == Ability.Type.BUFF ? a.buffBelowPercent : a.healBelowPercent),
                "bonus", num(m, "bonus", round1(a.markBonus.at(level, mul))),
                "boost", paint(m, "boost", boostText(m, a.boostType, tenths)),
                "boost_type", m.text("boost-types." + a.boostType),
                "seconds", num(m, "time", (a.type == Ability.Type.MARK || a.type == Ability.Type.NOVA && a.markBonus.base() > 0 ? a.markTicks : a.type == Ability.Type.SHIELD
                        || (a.type == Ability.Type.HEAL && a.shieldHearts.base() > 0) ? a.shieldTicks
                        : a.type == Ability.Type.BUFF ? a.buffTicks : a.type == Ability.Type.ORBIT ? a.orbitTicks
                        : a.type == Ability.Type.ZONE ? a.zoneTicks : a.type == Ability.Type.TETHER ? a.tetherTicks
                        : a.debuffTicks) / 20.0),
                "debuff_seconds", num(m, "time", a.debuffTicks / 20.0),
                "enemy_seconds", num(m, "time", a.enemyTicks / 20.0),
                "level", paint(m, "effect", roman(a.potionAmplifier(r.index()) + 1)),
                "potion", paint(m, "effect", potion),
                "range", num(m, "time", a.range),
                "radius", num(m, "time", a.areaRadius > 0 ? a.areaRadius : a.enemyRadius > 0 ? a.enemyRadius : a.blastRadius),
                "chain", num(m, "time", a.chainTargets),
                "targets", num(m, "time", a.multiTargets),
                "hits", num(m, "time", a.hits),
                "count", num(m, "time", a.meteors),
                "buff", num(m, "bonus", round1(a.buffAmount.at(level, mul))),
                "buff_stat", statName(plugin, a.buffStat),
                "buff2", num(m, "bonus", round1(a.buff2Amount.at(level, mul))),
                "buff2_stat", statName(plugin, a.buff2Stat),
                "buff_seconds", num(m, "time", a.buffTicks / 20.0),
                "stacks", num(m, "time", a.buffStacks),
                "heal_seconds", num(m, "time", (a.healPulses - 1) * a.healInterval / 20.0),
                "choice_1", choice(plugin, m, a, 0, level, mul),
                "choice_2", choice(plugin, m, a, 1, level, mul),
                "choice_3", choice(plugin, m, a, 2, level, mul),
                "enemy", num(m, "damage", round1(a.enemyAmount.at(level, mul))),
                "enemy_stat", statName(plugin, a.enemyStat),
                "pvp", num(m, "percent", Math.round(a.pvp(plugin.settings().defaultPvpMultiplier) * 100))
        };
        List<String> out = new ArrayList<>();
        // "Kỹ năng: <tên>", a blank line, then the description
        out.add(m.text("pet-card.ability-header", "ability", PetType.paint(a.color, a.name)));
        out.add("");
        // first description line with a bullet, the next ones indented under it
        for (int i = 0; i < a.description.size(); i++) {
            out.add(m.text(i == 0 ? "pet-card.ability-line-first" : "pet-card.ability-line", "line",
                    Messages.apply(a.description.get(i), ph)));
        }
        if (a.type != Ability.Type.BOOST && a.type != Ability.Type.POTION && a.type != Ability.Type.NONE) {
            out.add(m.text("pet-card.ability-cooldown", "value", Format.decimal(a.cooldownTicks / 20.0)));
        }
        return out;
    }

    /** 0.16.0: "2 Sát thương" of a random-buff choice (empty when there is no such choice). */
    private static String choice(BoxPetPlugin plugin, MenuTexts m, Ability a, int i, int level, double mul) {
        if (i >= a.buffChoices.size()) return "";
        Ability.Choice c = a.buffChoices.get(i);
        return num(m, "bonus", round1(c.amount().at(level, mul))) + " " + statName(plugin, c.stat());
    }

    /** A number in the colour of its kind (menus.yml ability-numbers), then back to the description colour. */
    private static String num(MenuTexts m, String kind, double value) {
        return paint(m, kind, Format.decimal(value));
    }

    private static String paint(MenuTexts m, String kind, String text) {
        return m.text("ability-numbers." + kind) + text + m.text("ability-numbers.reset");
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
        boolean gate = !max && plugin.xp().needsBreakthrough(d);
        return new String[]{
                "name", plugin.pets().displayName(d),
                "type", type.colouredName(),
                "id", d.shortId(),
                "role", roleName(plugin, type),
                "rarity", r.coloured(),
                "level", level(plugin, d.level),
                "xp", max ? plugin.menus().text("pet-card.xp-max") : gate ? (plugin.xp().extraXp(d) >= 1 ? plugin.menus().text("pet-card.xp-breakthrough-extra",
                        "extra", Format.number((long) Math.floor(plugin.xp().extraXp(d)))) : plugin.menus().text("pet-card.xp-breakthrough")) : plugin.menus().text("pet-card.xp-value",
                        "xp", Format.number((long) Math.floor(d.xp)), "need", Format.number((long) Math.ceil(need))),
                "xp_bar", max ? Format.bar(1, 1, 10, "<#FFD166>", "<dark_gray>") : gate ? Format.bar(1, 1, 10, "<#FF9F5A>", "<dark_gray>") : Format.bar(d.xp, need, 10, "<#7CFC9A>", "<dark_gray>"),
                "bond", String.valueOf(d.bond),
                "bond_tier", plugin.settings().bondTier(d.bond).name(),
                "bond_bar", Format.bar(d.bond, 100, 10, BOND, "<dark_gray>"),
                "bond_colour", BOND,
                "fullness", String.valueOf(d.fullness),
                "fullness_bar", Format.bar(d.fullness, 100, 10, d.fullness > 0 ? FULLNESS : HUNGRY, "<dark_gray>"),
                "fullness_colour", d.fullness > 0 ? FULLNESS : HUNGRY,
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
        blocks.put("warning", List.of(WARNING_MARKER));
        String[] ph = placeholders(plugin, d, type);
        var owner = plugin.pets().data(d.owner);
        boolean active = owner != null && d.id.equals(owner.activePet);
        String name = plugin.menus().name("pet-card", ph);
        if (active) name = name + plugin.menus().text("pet-card.name-active");
        return Menu.decorate(icon(plugin, type), name, withWarning(plugin, plugin.menus().lore("pet-card", blocks, ph)));
    }

    /** 0.9.7: the card as text lines (name first) for the show-off hologram: no hunger warning, no click lines. */
    public static List<String> showcaseLines(BoxPetPlugin plugin, PetData d) {
        PetType type = plugin.pets().type(d);
        if (type == null) return List.of();
        Map<String, List<String>> blocks = new HashMap<>();
        blocks.put("stats", statLines(plugin, d, type));
        blocks.put("ability", abilityLines(plugin, type, d.level));
        blocks.put("status", List.of());
        blocks.put("actions", List.of());
        blocks.put("warning", List.of());
        String[] ph = placeholders(plugin, d, type);
        List<String> out = new ArrayList<>();
        out.add(plugin.menus().name("pet-card", ph));
        out.addAll(plugin.menus().lore("pet-card", blocks, ph));
        return out;
    }

    /** Library card: what a pet type gives, at level 1 and at its max level. */
    public static ItemStack libraryCard(BoxPetPlugin plugin, PetType type, boolean owned, List<String> actions) {
        Rarity r = rarity(plugin, type);
        Map<String, List<String>> blocks = new HashMap<>();
        blocks.put("stats", statBaseLines(plugin, type));
        blocks.put("ability", abilityLines(plugin, type, 1));
        blocks.put("actions", actions);
        blocks.put("warning", List.of(WARNING_MARKER));
        String[] ph = {
                "name", type.colouredName(),
                "role", roleName(plugin, type),
                "rarity", r.coloured(),
                "max_level", level(plugin, r.maxLevel()),
                "owned", plugin.menus().text(owned ? "library.owned" : "library.not-owned")
        };
        return Menu.decorate(icon(plugin, type), plugin.menus().name("library.card", ph),
                withWarning(plugin, plugin.menus().lore("library.card", blocks, ph)));
    }

    /** Placeholder line of the %warning% block, replaced by the centred warning once the lore is built. */
    private static final String WARNING_MARKER = "\u0000warning";

    /**
     * Replaces the %warning% line (menus.yml pet-card.warning, under the skill, above the click lines) with the
     * warning centred under the widest line of the lore.
     */
    static List<String> withWarning(BoxPetPlugin plugin, List<String> lore) {
        List<String> out = new ArrayList<>(lore);
        int at = out.indexOf(WARNING_MARKER);
        if (at < 0) {
            // an older menus.yml without %warning%: keep it as the last line
            out.add("");
            out.add(WARNING_MARKER);
            at = out.size() - 1;
        }
        String warning = plugin.menus().text("pet-card.warning");
        if (warning.isEmpty() || warning.equals("pet-card.warning")) {
            out.remove(at);
            if (at < out.size() && at > 0 && out.get(at).isEmpty() && out.get(at - 1).isEmpty()) out.remove(at);
            return out;
        }
        int widest = 0;
        for (String line : out) if (!line.equals(WARNING_MARKER)) widest = Math.max(widest, com.dex.boxpet.text.Text.plain(line).length());
        int len = com.dex.boxpet.text.Text.plain(warning).length();
        // a space is narrower than a letter in the Minecraft font: about 1.5 spaces per missing character
        int pad = Math.max(0, (int) Math.round((widest - len) * 1.5 / 2));
        out.set(at, " ".repeat(pad) + warning);
        return out;
    }

    public static List<String> status(BoxPetPlugin plugin, PetData d, boolean active) {
        List<String> out = new ArrayList<>();
        if (d.fullness <= 0) out.add(plugin.menus().text("pet-card.status-hungry"));
        return out;
    }

    /** Placeholders helper for callers that need the coloured level only. */
    public static String levelText(BoxPetPlugin plugin, PetData d) {
        return Messages.apply(plugin.menus().text("pet-card.level-inline"), "level", level(plugin, d.level));
    }
}

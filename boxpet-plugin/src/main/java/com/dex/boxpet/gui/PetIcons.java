package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.Ability;
import com.dex.boxpet.config.MenuTexts;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.config.Settings;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.pet.StatService;
import com.dex.boxpet.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** The pet card shown in every menu (spec 10.3, 13): info, stats, ability, state. */
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

    public static String fullnessState(BoxPetPlugin plugin, int fullness) {
        String key = fullness >= 80 ? "full" : fullness >= plugin.settings().littleHungry ? "fed"
                : fullness > 0 ? "little-hungry" : "hungry";
        return plugin.menus().text("fullness-states." + key);
    }

    public static String age(BoxPetPlugin plugin, long days) {
        return days <= 0 ? plugin.menus().text("age.new") : plugin.menus().text("age.days", "days", String.valueOf(days));
    }

    public static String boost(int tenths) {
        return "x" + (tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "," + (tenths % 10));
    }

    /** Placeholders shared by the card name and lore. */
    public static String[] placeholders(BoxPetPlugin plugin, PetData d, PetType type) {
        Rarity r = plugin.pets().rarity(d);
        Settings s = plugin.settings();
        boolean max = d.level >= r.maxLevel();
        double need = plugin.levels().need(d.level);
        long now = System.currentTimeMillis();
        return new String[]{
                "name", plugin.pets().displayName(d),
                "type", type.name,
                "id", d.shortId(),
                "role", roleName(plugin, type),
                "rarity", r.coloured(),
                "level", String.valueOf(d.level),
                "max_level", String.valueOf(r.maxLevel()),
                "xp", max ? "-" : Format.number((long) Math.floor(d.xp)),
                "xp_need", max ? "-" : Format.number((long) Math.ceil(need)),
                "xp_bar", max ? Format.bar(1, 1, 10, "<#FFD166>", "<dark_gray>") : Format.bar(d.xp, need, 10, "<#7CFC9A>", "<dark_gray>"),
                "bond", String.valueOf(d.bond),
                "bond_tier", s.bondTier(d.bond).name(),
                "bond_bar", Format.bar(d.bond, 100, 10, "<#FFB3C7>", "<dark_gray>"),
                "fullness", String.valueOf(d.fullness),
                "fullness_bar", Format.bar(d.fullness, 100, 10, d.fullness > 0 ? "<#FFD166>" : "<#FF8A8A>", "<dark_gray>"),
                "fullness_state", fullnessState(plugin, d.fullness),
                "age", age(plugin, d.ageDays(now)),
                "stage", String.valueOf(d.stage + 1),
                "stages", String.valueOf(type.stages.size())
        };
    }

    public static List<String> statLines(BoxPetPlugin plugin, PetData d, PetType type) {
        Rarity r = plugin.pets().rarity(d);
        MenuTexts m = plugin.menus();
        List<String> out = new ArrayList<>();
        Map<String, Double> stats = StatService.petStats(type, d.level, r.statMultiplier(), d.stage,
                plugin.settings().bondTier(d.bond).stats());
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            String label = m.text("stat-names." + e.getKey());
            if (label.equals("stat-names." + e.getKey())) label = e.getKey();
            boolean rel = StatService.relative(type, e.getKey());
            out.add(m.text("pet-card.stat-row", "stat", label, "value", (e.getValue() >= 0 ? "+" : "") + Format.decimal(e.getValue())
                    + (rel ? "%" : "")));
        }
        if (out.isEmpty()) out.add(m.text("pet-card.no-stats"));
        return out;
    }

    public static List<String> abilityLines(BoxPetPlugin plugin, PetData d, PetType type) {
        Ability a = plugin.abilities().get(type.ability);
        if (a == null) return List.of(plugin.menus().text("pet-card.no-ability"));
        Rarity r = plugin.pets().rarity(d);
        double mul = r.statMultiplier();
        int tenths = (int) Math.floor(a.boostTenths.at(d.level, mul));
        if (a.boostWhole) tenths -= tenths % 10;
        String[] ph = {
                "damage", Format.decimal(Math.round(a.damage.at(d.level, mul) * 10) / 10.0),
                "heal", Format.decimal(Math.round(a.heal.at(d.level, mul) * 10) / 10.0),
                "hearts", Format.decimal(Math.round(a.shieldHearts.at(d.level, mul) * 10) / 10.0),
                "percent", String.valueOf(a.type == Ability.Type.SHIELD ? a.shieldBelowPercent : a.healBelowPercent),
                "bonus", Format.decimal(Math.round(a.markBonus.at(d.level, mul) * 10) / 10.0),
                "boost", tenths > 0 ? boost(tenths) : "x0",
                "boost_type", plugin.menus().text("boost-types." + a.boostType),
                "cooldown", Format.decimal(a.cooldownTicks / 20.0),
                "seconds", Format.decimal((a.type == Ability.Type.MARK ? a.markTicks : a.type == Ability.Type.SHIELD ? a.shieldTicks : a.debuffTicks) / 20.0),
                "level", String.valueOf(a.potionAmplifier(r.index()) + 1),
                "range", Format.decimal(a.range),
                "pvp", String.valueOf(Math.round(a.pvp(plugin.settings().defaultPvpMultiplier) * 100))
        };
        List<String> out = new ArrayList<>();
        out.add(plugin.menus().text("pet-card.ability-title", "ability", a.name));
        for (String line : a.description) out.add(plugin.menus().text("pet-card.ability-line", "line",
                com.dex.boxpet.config.Messages.apply(line, ph)));
        return out;
    }

    /** The full card. {@code statusLines}: extra lines under the card (active, hungry...). */
    public static ItemStack card(BoxPetPlugin plugin, PetData d, List<String> statusLines, List<String> actions) {
        PetType type = plugin.pets().type(d);
        if (type == null) {
            return Menu.icon(Material.BARRIER, "<#FF8A8A>" + d.type, List.of(plugin.menus().text("pet-card.unknown-type")));
        }
        Map<String, List<String>> blocks = new HashMap<>();
        List<String> desc = new ArrayList<>();
        for (String line : type.description) desc.add(plugin.menus().text("pet-card.description-line", "line", line));
        blocks.put("description", desc);
        blocks.put("stats", statLines(plugin, d, type));
        blocks.put("ability", abilityLines(plugin, d, type));
        blocks.put("status", statusLines);
        blocks.put("actions", actions);
        String[] ph = placeholders(plugin, d, type);
        return Menu.decorate(icon(plugin, type), plugin.menus().name("pet-card", ph), plugin.menus().lore("pet-card", blocks, ph));
    }

    public static List<String> status(BoxPetPlugin plugin, PetData d, boolean active) {
        List<String> out = new ArrayList<>();
        if (active) out.add(plugin.menus().text("pet-card.status-active"));
        if (d.fullness <= 0) out.add(plugin.menus().text("pet-card.status-hungry"));
        return out;
    }
}

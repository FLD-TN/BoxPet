package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * A filter / sort button in the AxAuctions style: the lore lists every option, the chosen one is highlighted;
 * left click = next option, right click = previous option. Texts come from menus.yml {@code filters.<key>}.
 */
final class Cycler {

    private final BoxPetPlugin plugin;
    private final String key;
    private final Material material;
    private final List<String> labels;
    private int index;

    /** {@code labels}: the shown option names, in order. */
    Cycler(BoxPetPlugin plugin, String key, Material material, List<String> labels) {
        this.plugin = plugin;
        this.key = key;
        this.material = material;
        this.labels = List.copyOf(labels);
    }

    int index() {
        return index;
    }

    /** @return true when the choice changed */
    boolean click(ClickType click) {
        if (labels.size() < 2) return false;
        if (click.isRightClick()) index = (index - 1 + labels.size()) % labels.size();
        else index = (index + 1) % labels.size();
        return true;
    }

    ItemStack icon() {
        List<String> lore = new ArrayList<>(plugin.menus().lore("filters." + key));
        if (!lore.isEmpty()) lore.add("");
        for (int i = 0; i < labels.size(); i++) {
            lore.add(plugin.menus().text(i == index ? "filters.selected" : "filters.option", "option", labels.get(i)));
        }
        lore.add("");
        lore.add(plugin.menus().text("filters.next"));
        lore.add(plugin.menus().text("filters.previous"));
        return Menu.icon(material, plugin.menus().name("filters." + key, "option", labels.get(index)), lore);
    }

    /** Role filter: Tất cả, Chiến đấu, Hỗ trợ, Kinh tế. Index 0 = all, else Role.values()[index - 1]. */
    static Cycler roles(BoxPetPlugin plugin) {
        List<String> l = new ArrayList<>();
        l.add(plugin.menus().text("filters.all"));
        for (var r : com.dex.boxpet.config.PetType.Role.values()) l.add(plugin.menus().text("roles." + r.id));
        return new Cycler(plugin, "role", Material.HOPPER, l);
    }

    /** Rarity filter: Tất cả + every rarity in order. Index 0 = all, else rarity index + 1. */
    static Cycler rarities(BoxPetPlugin plugin) {
        List<String> l = new ArrayList<>();
        l.add(plugin.menus().text("filters.all"));
        for (var r : plugin.rarities().all()) l.add(r.coloured());
        return new Cycler(plugin, "rarity", Material.AMETHYST_SHARD, l);
    }

    /** Sort with the option names of menus.yml {@code filters.sorts.<menu>}. */
    static Cycler sorts(BoxPetPlugin plugin, String menu) {
        List<String> l = plugin.menus().list("filters.sorts." + menu);
        if (l.isEmpty()) l = List.of("-");
        return new Cycler(plugin, "sort", Material.COMPARATOR, l);
    }
}

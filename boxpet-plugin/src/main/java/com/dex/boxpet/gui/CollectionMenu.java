package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.CollectionConfig;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.text.Format;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Collection book (spec 9.4): every pet type with ✔ / ✘, its rarity and points only (no acquisition hints);
 * filters by role and rarity, 4 sorts; score and milestones on top.
 */
public final class CollectionMenu extends Menu {

    private final PlayerData pd;
    private int page;
    private final Cycler role;
    private final Cycler rarity;

    public CollectionMenu(BoxPetPlugin plugin, Player player, PlayerData pd, int page) {
        super(plugin, player, 6, plugin.menus().title("collection"));
        this.pd = pd;
        this.page = page;
        this.role = Cycler.roles(plugin);
        this.rarity = Cycler.rarities(plugin);
    }

    private List<PetType> list() {
        List<PetType> out = new ArrayList<>();
        for (PetType t : plugin.types().all()) {
            if (role.index() > 0 && t.role != PetType.Role.values()[role.index() - 1]) continue;
            if (rarity.index() > 0 && PetIcons.rarity(plugin, t).index() != rarity.index() - 1) continue;
            out.add(t);
        }
        Comparator<PetType> byRarity = Comparator.comparingInt(t -> PetIcons.rarity(plugin, t).index());
        out.sort(byRarity.thenComparing(t -> t.id));
        return out;
    }

    @Override
    protected void draw() {
        List<PetType> types = list();
        int pages = Math.max(1, (types.size() + StorageMenu.SLOTS.length - 1) / StorageMenu.SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < StorageMenu.SLOTS.length; i++) {
            int idx = page * StorageMenu.SLOTS.length + i;
            if (idx >= types.size()) break;
            PetType t = types.get(idx);
            Rarity r = PetIcons.rarity(plugin, t);
            boolean owned = plugin.collectionService().owned(pd, t.id);
            String[] ph = {"pet", t.colouredName(), "plain", t.name, "rarity", r.coloured(), "points", String.valueOf(r.collectionPoints())};
            ItemStack icon = owned
                    ? decorate(PetIcons.icon(plugin, t), plugin.menus().name("collection.owned", ph), plugin.menus().lore("collection.owned", ph))
                    : icon(Material.GRAY_DYE, plugin.menus().name("collection.missing", ph), plugin.menus().lore("collection.missing", ph));
            set(StorageMenu.SLOTS[i], icon, null);
        }
        int score = plugin.collectionService().score(pd);
        List<String> rows = new ArrayList<>();
        for (CollectionConfig.Milestone m : plugin.collectionConfig().milestones) {
            boolean done = score >= m.score();
            StringBuilder stats = new StringBuilder();
            for (Map.Entry<String, Double> e : m.stats().entrySet()) {
                if (stats.length() > 0) stats.append(plugin.menus().textOr(done ? "collection.milestone-separator-done"
                        : "collection.milestone-separator-todo", done ? "<white>, " : "<dark_gray>, "));
                stats.append(statShort(e.getKey(), e.getValue(), done));
            }
            rows.add(plugin.menus().text(score >= m.score() ? "collection.milestone-done" : "collection.milestone-todo",
                    "score", String.valueOf(m.score()), "label", m.label(), "stats", stats.toString()));
        }
        set(4, icon(Material.NETHER_STAR, plugin.menus().name("collection.info", "score", String.valueOf(score)),
                plugin.menus().lore("collection.info", Map.of("milestones", rows), "score", String.valueOf(score),
                        "owned", String.valueOf(pd.collection.size()), "total", String.valueOf(plugin.types().size()))), null);
        set(45, button("common.back", Material.ARROW), click -> new MainMenu(plugin, player).open());
        set(52, role.icon(), click -> {
            if (role.click(click)) {
                page = 0;
                refresh();
            }
        });
        set(53, rarity.icon(), click -> {
            if (rarity.click(click)) {
                page = 0;
                refresh();
            }
        });
        if (page > 0) set(48, button("common.previous", Material.SPECTRAL_ARROW, "page", String.valueOf(page)), click -> {
            page--;
            refresh();
        });
        if (page < pages - 1) set(50, button("common.next", Material.SPECTRAL_ARROW, "page", String.valueOf(page + 2)), click -> {
            page++;
            refresh();
        });
        fill();
    }

    /** "+1 Phòng thủ" with the stat's display name (without its icon). */
    /** 0.21.2: a reached milestone shows its numbers green and words white, a missing one all dark gray. */
    private String statShort(String stat, double value, boolean done) {
        String label = plugin.menus().text("stat-names." + stat);
        if (label.equals("stat-names." + stat)) label = stat;
        return plugin.menus().textOr(done ? "collection.milestone-stat-done" : "collection.milestone-stat-todo",
                done ? "<#7CFC9A>+%value% <white>%stat%" : "<dark_gray>+%value% %stat%", "value", Format.decimal(value), "stat", label);
    }
}

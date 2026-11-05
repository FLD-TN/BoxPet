package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.CollectionConfig;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.text.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Collection book (spec 9.4): every pet type ✔ / ✘ (no acquisition hints), score and milestones. */
public final class CollectionMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final PlayerData pd;
    private int page;

    public CollectionMenu(BoxPetPlugin plugin, Player player, PlayerData pd, int page) {
        super(plugin, player, 6, plugin.menus().title("collection"));
        this.pd = pd;
        this.page = page;
    }

    @Override
    protected void draw() {
        List<PetType> types = new ArrayList<>(plugin.types().all());
        int pages = Math.max(1, (types.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            if (idx >= types.size()) break;
            PetType t = types.get(idx);
            int best = plugin.collectionService().best(pd, t.id);
            if (best >= 0) {
                Rarity r = plugin.rarities().byIndex(best);
                ItemStack icon = decorate(PetIcons.icon(plugin, t), plugin.menus().name("collection.owned", "pet", t.name, "rarity", r.coloured()),
                        plugin.menus().lore("collection.owned", "pet", t.name, "rarity", r.coloured(), "role", PetIcons.roleName(plugin, t),
                                "points", String.valueOf(r.collectionPoints())));
                set(SLOTS[i], icon, null);
            } else {
                set(SLOTS[i], icon(Material.GRAY_DYE, plugin.menus().name("collection.missing", "pet", t.name),
                        plugin.menus().lore("collection.missing", "pet", t.name, "role", PetIcons.roleName(plugin, t))), null);
            }
        }
        int score = plugin.collectionService().score(pd);
        List<String> rows = new ArrayList<>();
        for (CollectionConfig.Milestone m : plugin.collectionConfig().milestones) {
            StringBuilder stats = new StringBuilder();
            for (Map.Entry<String, Double> e : m.stats().entrySet()) {
                String label = plugin.menus().text("stat-names." + e.getKey());
                if (label.equals("stat-names." + e.getKey())) label = e.getKey();
                if (stats.length() > 0) stats.append(", ");
                stats.append("+").append(Format.decimal(e.getValue())).append(" ").append(label);
            }
            rows.add(plugin.menus().text(score >= m.score() ? "collection.milestone-done" : "collection.milestone-todo",
                    "score", String.valueOf(m.score()), "label", m.label(), "stats", stats.toString()));
        }
        set(4, icon(Material.NETHER_STAR, plugin.menus().name("collection.info", "score", String.valueOf(score)),
                plugin.menus().lore("collection.info", Map.of("milestones", rows), "score", String.valueOf(score),
                        "owned", String.valueOf(pd.collection.size()), "total", String.valueOf(types.size()))), null);
        set(45, button("common.back", Material.ARROW), click -> new MainMenu(plugin, player).open());
        if (page > 0) set(48, button("common.previous", Material.ARROW, "page", String.valueOf(page)), click -> {
            page--;
            refresh();
        });
        if (page < pages - 1) set(50, button("common.next", Material.ARROW, "page", String.valueOf(page + 2)), click -> {
            page++;
            refresh();
        });
        fill();
    }
}

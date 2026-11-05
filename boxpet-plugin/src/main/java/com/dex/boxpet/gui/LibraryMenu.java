package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.data.PlayerData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Thư viện thú cưng (0.5.0): every pet type with its rarity, role, stats from level 1 to max and its skill. Sorted by
 * rarity going up by default; filters by role and rarity, 4 sorts. Information only (no acquisition hints).
 */
public final class LibraryMenu extends Menu {

    private final PlayerData pd;
    private int page;
    private final Cycler role;
    private final Cycler rarity;

    public LibraryMenu(BoxPetPlugin plugin, Player player, PlayerData pd) {
        super(plugin, player, 6, plugin.menus().title("library"));
        this.pd = pd;
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
        out.sort(byRarity.thenComparing(t -> t.name));
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
            set(StorageMenu.SLOTS[i], PetIcons.libraryCard(plugin, t, plugin.collectionService().owned(pd, t.id), List.of()), null);
        }
        set(4, button("library.info", Material.KNOWLEDGE_BOOK, "total", String.valueOf(plugin.types().size())), null);
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
        set(49, button("library.page", Material.PAPER, "page", String.valueOf(page + 1), "pages", String.valueOf(pages),
                "count", String.valueOf(types.size())), null);
        if (page < pages - 1) set(50, button("common.next", Material.SPECTRAL_ARROW, "page", String.valueOf(page + 2)), click -> {
            page++;
            refresh();
        });
        fill();
    }
}

package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.pet.PetManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Pet storage (spec 10.3), 28 per page, filter by role, sort. Staff mode for {@code /boxpet pets}. */
public final class StorageMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final String[] SORTS = {"rarity", "level", "newest"};
    private static final String[] FILTERS = {"all", "combat", "support", "boost"};

    private final PlayerData target;
    private final boolean admin;
    private int page;
    private int sort;
    private int filter;

    public StorageMenu(BoxPetPlugin plugin, Player player, PlayerData target, boolean admin, int page) {
        super(plugin, player, 6, admin ? plugin.menus().title("storage-admin", "player", name(target))
                : plugin.menus().title("storage"));
        this.target = target;
        this.admin = admin;
        this.page = page;
    }

    private static String name(PlayerData pd) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(pd.uuid);
        return op.getName() == null ? pd.uuid.toString().substring(0, 8) : op.getName();
    }

    @Override
    protected boolean combatLocked() {
        return !admin;
    }

    private List<PetData> list() {
        List<PetData> out = new ArrayList<>();
        for (PetData d : target.pets.values()) {
            PetType t = plugin.pets().type(d);
            if (filter > 0 && (t == null || !t.role.id.equals(FILTERS[filter]))) continue;
            out.add(d);
        }
        Comparator<PetData> c = switch (SORTS[sort]) {
            case "level" -> Comparator.<PetData>comparingInt(d -> -d.level)
                    .thenComparingInt(d -> -plugin.pets().rarity(d).index());
            case "newest" -> Comparator.<PetData>comparingLong(d -> -d.createdAt);
            default -> Comparator.<PetData>comparingInt(d -> -plugin.pets().rarity(d).index()).thenComparingInt(d -> -d.level);
        };
        out.sort(c);
        PetData active = target.active();
        if (active != null && out.remove(active)) out.add(0, active);
        return out;
    }

    @Override
    protected void draw() {
        List<PetData> list = list();
        int pages = Math.max(1, (list.size() + SLOTS.length - 1) / SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < SLOTS.length; i++) {
            int idx = page * SLOTS.length + i;
            if (idx >= list.size()) break;
            PetData d = list.get(idx);
            boolean active = d.id.equals(target.activePet);
            List<String> actions = admin
                    ? List.of(plugin.menus().text("actions.admin-manage"))
                    : List.of(plugin.menus().text("actions.detail"),
                    plugin.menus().text(active ? "actions.quick-dismiss" : "actions.quick-summon"));
            set(SLOTS[i], PetIcons.card(plugin, d, PetIcons.status(plugin, d, active), actions), click -> {
                if (admin || click.isLeftClick()) {
                    new PetDetailMenu(plugin, player, target, d, admin).open();
                    return;
                }
                if (active) {
                    plugin.pets().dismiss(player, true, "menu");
                    plugin.messages().send(player, "dismiss.done", "pet", plugin.pets().displayName(d));
                } else {
                    summon(plugin, player, d);
                }
                refresh();
            });
        }
        if (list.isEmpty()) set(22, button("storage.empty", Material.BARRIER), null);
        if (!admin) set(45, button("common.back", Material.ARROW), click -> new MainMenu(plugin, player).open());
        else set(46, button("storage.deleted", Material.LAVA_BUCKET), click -> DeletedMenu.open(plugin, player, target));
        if (page > 0) set(48, button("common.previous", Material.ARROW, "page", String.valueOf(page)), click -> {
            page--;
            refresh();
        });
        set(49, button("storage.info", Material.CHEST, "used", String.valueOf(target.pets.size()),
                "max", admin ? "-" : String.valueOf(plugin.pets().capacity(player)),
                "page", String.valueOf(page + 1), "pages", String.valueOf(pages)), null);
        if (page < pages - 1) set(50, button("common.next", Material.ARROW, "page", String.valueOf(page + 2)), click -> {
            page++;
            refresh();
        });
        set(52, button("storage.filter", Material.HOPPER, "filter", plugin.menus().text("filters." + FILTERS[filter])), click -> {
            filter = (filter + 1) % FILTERS.length;
            page = 0;
            refresh();
        });
        set(53, button("storage.sort", Material.COMPARATOR, "sort", plugin.menus().text("sorts." + SORTS[sort])), click -> {
            sort = (sort + 1) % SORTS.length;
            refresh();
        });
        fill();
    }

    /** Summon with the result message (shared by menus and /pet summon). */
    public static void summon(BoxPetPlugin plugin, Player player, PetData d) {
        PetManager.SummonResult r = plugin.pets().summon(player, d, false);
        String name = plugin.pets().displayName(d);
        switch (r) {
            case OK -> plugin.messages().send(player, "summon.done", "pet", name);
            case COMBAT -> plugin.interact().lockMessage(player);
            case COOLDOWN -> plugin.messages().send(player, "summon.cooldown", "seconds",
                    String.valueOf((plugin.pets().swapCooldownLeft(player.getUniqueId()) + 999) / 1000));
            case ALREADY -> plugin.messages().send(player, "summon.already", "pet", name);
            case NO_MODEL -> plugin.messages().send(player, "summon.no-model", "pet", name);
            case UNKNOWN_TYPE -> plugin.messages().send(player, "summon.unknown-type", "pet", name);
            case CANCELLED -> plugin.messages().send(player, "summon.cancelled", "pet", name);
            case NOT_LOADED -> plugin.messages().send(player, "loading");
        }
    }
}

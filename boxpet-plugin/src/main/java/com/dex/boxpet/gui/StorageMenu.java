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

/** Pet storage (spec 10.3): 28 per page; filters by role and rarity, 4 sorts. Staff mode for {@code /boxpet pets}. */
public final class StorageMenu extends Menu {

    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    private final PlayerData target;
    private final boolean admin;
    private int page;
    private final Cycler role;
    private final Cycler rarity;
    private final Cycler sort;

    public StorageMenu(BoxPetPlugin plugin, Player player, PlayerData target, boolean admin, int page) {
        super(plugin, player, 6, admin ? plugin.menus().title("storage-admin", "player", name(target))
                : plugin.menus().title("storage"));
        this.target = target;
        this.admin = admin;
        this.page = page;
        this.role = Cycler.roles(plugin);
        this.rarity = Cycler.rarities(plugin);
        this.sort = Cycler.sorts(plugin, "storage");
    }

    private static String name(PlayerData pd) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(pd.uuid);
        return op.getName() == null ? pd.uuid.toString().substring(0, 8) : op.getName();
    }

    @Override
    protected boolean combatLocked() {
        return !admin;
    }

    private int rarityIndex(PetData d) {
        return plugin.pets().rarity(d).index();
    }

    private List<PetData> list() {
        List<PetData> out = new ArrayList<>();
        for (PetData d : target.pets.values()) {
            PetType t = plugin.pets().type(d);
            if (role.index() > 0 && (t == null || t.role != PetType.Role.values()[role.index() - 1])) continue;
            if (rarity.index() > 0 && rarityIndex(d) != rarity.index() - 1) continue;
            out.add(d);
        }
        // 0 rarity up, 1 rarity down, 2 highest level, 3 newest
        Comparator<PetData> c = switch (sort.index()) {
            case 1 -> Comparator.<PetData>comparingInt(d -> -rarityIndex(d)).thenComparingInt(d -> -d.level);
            case 2 -> Comparator.<PetData>comparingInt(d -> -d.level).thenComparingInt(d -> -rarityIndex(d));
            case 3 -> Comparator.<PetData>comparingLong(d -> -d.createdAt);
            default -> Comparator.<PetData>comparingInt(this::rarityIndex).thenComparingInt(d -> -d.level);
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
                    : List.of(plugin.menus().text("actions.manage"),
                    plugin.menus().text(active ? "actions.quick-dismiss" : "actions.quick-summon"));
            set(SLOTS[i], PetIcons.card(plugin, d, PetIcons.status(plugin, d, active), actions), click -> {
                if (admin || click.isLeftClick()) {
                    new ManageMenu(plugin, player, target, d, admin).open();
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
        set(46, role.icon(), click -> {
            if (role.click(click)) {
                page = 0;
                refresh();
            }
        });
        set(47, rarity.icon(), click -> {
            if (rarity.click(click)) {
                page = 0;
                refresh();
            }
        });
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
        set(52, sort.icon(), click -> {
            if (sort.click(click)) refresh();
        });
        if (admin) set(53, button("storage.deleted", Material.LAVA_BUCKET), click -> DeletedMenu.open(plugin, player, target));
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

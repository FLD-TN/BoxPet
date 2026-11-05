package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.IncubatorSlot;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.item.ItemFactory;
import com.dex.boxpet.pet.IncubatorService;
import com.dex.boxpet.text.Format;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Incubator (spec 10.2). Click an egg in your own inventory to put it in; refreshes every second. */
public final class IncubatorMenu extends Menu {

    private final PlayerData pd;
    private BukkitTask ticker;

    public IncubatorMenu(BoxPetPlugin plugin, Player player, PlayerData pd) {
        super(plugin, player, 3, plugin.menus().title("incubator"));
        this.pd = pd;
    }

    private int[] slots() {
        int max = plugin.settings().maxIncubator;
        int start = 13 - max / 2;
        int[] out = new int[max];
        for (int i = 0; i < max; i++) out[i] = start + i;
        return out;
    }

    @Override
    protected void draw() {
        set(4, button("incubator.info", Material.SNIFFER_EGG, "slots", String.valueOf(plugin.incubator().slots(player))), null);
        int[] slots = slots();
        int unlocked = plugin.incubator().slots(player);
        long now = System.currentTimeMillis();
        boolean gems = plugin.boxCore().isPresent() && plugin.boxCore().get().gemsAvailable() && plugin.settings().gemPerMinute > 0;
        for (int i = 0; i < slots.length; i++) {
            int index = i;
            IncubatorSlot s = pd.incubator.get(i);
            if (s == null) {
                if (i >= unlocked) set(slots[i], button("incubator.locked", Material.GRAY_STAINED_GLASS_PANE), null);
                else set(slots[i], button("incubator.empty", Material.LIME_STAINED_GLASS_PANE), null);
                continue;
            }
            Rarity r = plugin.rarities().get(s.rarity());
            if (r == null) r = plugin.rarities().lowest();
            ItemStack icon = eggIcon(s, r);
            String petName = s.random() ? plugin.items().randomEgg(r.id()).name() : petName(s);
            if (s.ready(now)) {
                set(slots[i], decorate(icon, plugin.menus().name("incubator.ready", "egg", petName, "rarity", r.coloured()),
                        plugin.menus().lore("incubator.ready", "egg", petName, "rarity", r.coloured())), click -> open(index));
            } else {
                long left = (s.finishAt() - now + 999) / 1000;
                long total = Math.max(1, (s.finishAt() - s.startedAt()) / 1000);
                String key = gems ? "incubator.hatching-gem" : "incubator.hatching";
                long cost = plugin.incubator().speedUpCost(s, now);
                set(slots[i], decorate(icon, plugin.menus().name(key, "egg", petName, "rarity", r.coloured()),
                        plugin.menus().lore(key, "egg", petName, "rarity", r.coloured(), "time", Format.clock(left),
                                "bar", Format.bar(total - left, total, 10, "<#7CFC9A>", "<dark_gray>"),
                                "cost", Format.number(cost))), click -> {
                    if (gems && click == ClickType.RIGHT) speedUp(index);
                });
            }
        }
        set(22, button("common.back", Material.ARROW), click -> new MainMenu(plugin, player).open());
        fill();
    }

    private String petName(IncubatorSlot s) {
        PetType t = plugin.types().get(s.petType());
        String name = t == null ? s.petType() : t.name;
        return plugin.items().fixedEgg.name().replace("%pet%", name);
    }

    private ItemStack eggIcon(IncubatorSlot s, Rarity r) {
        if (s.random()) {
            ItemsConfig.EggLook look = plugin.items().randomEgg(r.id());
            ItemStack ia = plugin.itemsAdder().map(h -> h.item(look.itemsAdder())).orElse(null);
            return ia != null ? ia : new ItemStack(look.material());
        }
        PetType t = plugin.types().get(s.petType());
        return t == null ? new ItemStack(Material.TURTLE_EGG) : PetIcons.icon(plugin, t);
    }

    @Override
    protected void onOwnItemClick(ItemStack item, int rawSlot, ClickType click) {
        ItemFactory.Info info = plugin.itemFactory().read(item);
        if (info == null || (info.kind() != ItemFactory.Kind.EGG && info.kind() != ItemFactory.Kind.RANDOM_EGG)) return;
        if (!plugin.itemFactory().validEgg(info)) {
            plugin.messages().send(player, "egg.invalid");
            return;
        }
        int free = plugin.incubator().freeSlot(player, pd);
        if (free < 0) {
            plugin.messages().send(player, "incubator.full");
            return;
        }
        ItemStack stack = player.getInventory().getItem(rawSlot);
        if (stack == null || !stack.isSimilar(item)) return;
        stack.setAmount(stack.getAmount() - 1);
        IncubatorSlot s = plugin.incubator().put(pd, free, info);
        Rarity r = plugin.rarities().get(s.rarity());
        plugin.messages().send(player, "incubator.placed", "time", Format.duration(r == null ? 0 : r.hatchSeconds()));
        sound("block.sniffer_egg.plop");
        refresh();
    }

    private void open(int index) {
        IncubatorService.Opened o = plugin.incubator().open(player, pd, index);
        switch (o.result()) {
            case OK -> new HatchMenu(plugin, player, pd, o.pet(), o.random()).open();
            case STORAGE_FULL -> plugin.messages().send(player, "incubator.storage-full",
                    "max", String.valueOf(plugin.pets().capacity(player)));
            case NO_PET -> plugin.messages().send(player, "incubator.no-pet");
            default -> refresh();
        }
    }

    private void speedUp(int index) {
        IncubatorSlot s = pd.incubator.get(index);
        if (s == null || s.ready(System.currentTimeMillis())) return;
        long cost = plugin.incubator().speedUpCost(s, System.currentTimeMillis());
        new ConfirmMenu(plugin, player, plugin.menus().text("confirm.speed-up", "cost", Format.number(cost)), () -> {
            IncubatorSlot again = pd.incubator.get(index);
            if (again == null) {
                open();
                return;
            }
            long c = plugin.incubator().speedUpCost(again, System.currentTimeMillis());
            if (!plugin.boxCore().get().withdrawGems(player.getUniqueId(), c, "BoxPet incubator speed-up")) {
                plugin.messages().send(player, "incubator.no-gems", "cost", Format.number(c));
            } else {
                plugin.incubator().finishNow(pd, again);
                plugin.messages().send(player, "incubator.sped-up", "cost", Format.number(c));
            }
            open();
        }, this::open).open();
    }

    @Override
    protected void onOpen() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) != this) {
                ticker.cancel();
                return;
            }
            refresh();
        }, 20L, 20L);
    }

    @Override
    protected void onClose() {
        if (ticker != null) ticker.cancel();
    }
}

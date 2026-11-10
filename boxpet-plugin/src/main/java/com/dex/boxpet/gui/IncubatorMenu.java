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
        // 0.28.1: no gem speed-up any more; only boxpet.incubator.skip (zeus+) may finish an egg at once, for free.
        // Without it nothing is shown: the egg simply hatches when its time is up.
        boolean skip = player.hasPermission(com.dex.boxpet.pet.IncubatorService.SKIP_PERMISSION);
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
                String key = skip ? "incubator.hatching-skip" : "incubator.hatching";
                set(slots[i], decorate(icon, plugin.menus().name(key, "egg", petName, "rarity", r.coloured()),
                        plugin.menus().lore(key, "egg", petName, "rarity", r.coloured(), "time", Format.clock(left),
                                "bar", Format.bar(total - left, total, 10, "<#7CFC9A>", "<dark_gray>"))), click -> {
                    if (skip && click == ClickType.RIGHT) skip(index);
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
        // 0.23.0: every egg has its own id and is used once; a copy of a used egg is refused and logged
        String serial = plugin.itemFactory().serialOf(item);
        if (serial == null) {
            plugin.messages().send(player, "egg.invalid");
            return;
        }
        if (plugin.storage().eggUsed(serial)) {
            plugin.messages().send(player, "egg.used");
            plugin.getLogger().warning("Used egg " + serial + " (" + info.kind() + " " + info.id() + "/" + info.rarity()
                    + ") presented again by " + player.getName() + ": possible duplicate");
            return;
        }
        if (info.kind() == ItemFactory.Kind.RANDOM_EGG) {
            openRandom(item, rawSlot, info, serial);
            return;
        }
        int free = plugin.incubator().freeSlot(player, pd);
        if (free < 0) {
            plugin.messages().send(player, "incubator.full");
            return;
        }
        ItemStack stack = player.getInventory().getItem(rawSlot);
        if (stack == null || !stack.isSimilar(item)) return;
        // take the egg by writing the slot back (never through a possibly detached reference)
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(rawSlot, null);
        } else {
            ItemStack left = stack.clone();
            left.setAmount(stack.getAmount() - 1);
            player.getInventory().setItem(rawSlot, left);
        }
        // put() saves at once: the inventory is saved in the same moment (no egg kept after a crash)
        pd.inventoryChanged = true;
        plugin.storage().useEgg(serial, info.kind().name(), info.id(), player.getUniqueId());
        IncubatorSlot s = plugin.incubator().put(pd, free, info);
        Rarity r = plugin.rarities().get(s.rarity());
        plugin.messages().send(player, "incubator.placed", "time", Format.duration(r == null ? 0 : r.hatchSeconds()));
        sound("block.sniffer_egg.plop");
        refresh();
    }

    /** 0.23.0: a random egg opens at once into the egg of one pet (a tradeable item), shown with the gacha strip. */
    private void openRandom(ItemStack item, int rawSlot, ItemFactory.Info info, String serial) {
        Rarity r = plugin.rarities().get(info.rarity());
        PetType type = r == null ? null : plugin.incubator().roll(r);
        if (type == null) {
            plugin.messages().send(player, "incubator.no-pet");
            return;
        }
        ItemStack stack = player.getInventory().getItem(rawSlot);
        if (stack == null || !stack.isSimilar(item)) return;
        player.getInventory().setItem(rawSlot, stack.getAmount() <= 1 ? null : withAmount(stack, stack.getAmount() - 1));
        plugin.storage().useEgg(serial, info.kind().name(), type.id, player.getUniqueId());
        ItemStack egg = plugin.itemFactory().egg(type, 1);
        for (ItemStack rest : player.getInventory().addItem(egg.clone()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        // the random egg left and the pet egg came in: both written with the player's data at once
        pd.inventoryChanged = true;
        plugin.pets().save(pd);
        new EggRollMenu(plugin, player, pd, type, r, egg).open();
    }

    private static ItemStack withAmount(ItemStack stack, int amount) {
        ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
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

    /** 0.28.1: boxpet.incubator.skip finishes the egg at once, free (checked again on click). */
    private void skip(int index) {
        IncubatorSlot s = pd.incubator.get(index);
        if (s == null || s.ready(System.currentTimeMillis())) return;
        if (!player.hasPermission(com.dex.boxpet.pet.IncubatorService.SKIP_PERMISSION)) return;
        plugin.incubator().finishNow(pd, s);
        plugin.messages().send(player, "incubator.skipped");
        sound("block.amethyst_block.chime");
        refresh();
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

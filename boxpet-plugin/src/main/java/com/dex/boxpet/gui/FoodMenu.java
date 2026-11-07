package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.item.ItemFactory;
import com.dex.boxpet.text.Format;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Food picker (spec 11.1): candies and foods from the inventory, for any pet of the player (out or in storage). */
public final class FoodMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final PlayerData owner;
    private final PetData pet;

    @Override
    protected boolean stale() {
        return !owner.owns(pet);
    }

    public FoodMenu(BoxPetPlugin plugin, Player player, PlayerData owner, PetData pet) {
        super(plugin, player, 4, plugin.menus().title("food"));
        this.owner = owner;
        this.pet = pet;
    }

    @Override
    protected void draw() {
        if (!owner.pets.containsKey(pet.id)) {
            player.closeInventory();
            return;
        }
        set(4, button("food.info", Material.BOWL, "pet", plugin.pets().displayName(pet),
                "fullness", String.valueOf(pet.fullness),
                "bar", Format.bar(pet.fullness, 100, 10, "<#FFD166>", "<dark_gray>")), null);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack it : player.getInventory().getStorageContents()) {
            ItemsConfig.Consumable c = plugin.itemFactory().consumable(plugin.itemFactory().read(it));
            if (c != null) counts.merge(c.id(), it.getAmount(), Integer::sum);
        }
        List<ItemsConfig.Consumable> list = new ArrayList<>();
        for (ItemsConfig.Consumable c : plugin.items().all()) if (counts.containsKey(c.id())) list.add(c);
        if (list.isEmpty()) set(22, button("food.none", Material.BARRIER), null);
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            ItemsConfig.Consumable c = list.get(i);
            ItemStack icon = plugin.itemFactory().consumable(c, Math.min(64, counts.get(c.id())));
            List<String> extra = new ArrayList<>();
            extra.add("");
            extra.add(plugin.menus().text("food.have", "amount", String.valueOf(counts.get(c.id()))));
            extra.add(plugin.menus().text("actions.feed-one"));
            if (c.candy()) extra.add(plugin.menus().text("actions.feed-all"));
            icon = withLore(icon, extra);
            set(SLOTS[i], icon, click -> {
                int slot = find(c);
                if (slot < 0) {
                    refresh();
                    return;
                }
                plugin.interact().useConsumable(player, pet, slot, c, click.isShiftClick() && c.candy());
                refresh();
            });
        }
        set(31, button("common.back", Material.ARROW), click -> new ManageMenu(plugin, player, owner, pet, false).open());
        fill();
    }

    /** Inventory slot of the first stack of this consumable, -1 when none. */
    private int find(ItemsConfig.Consumable c) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemFactory.Info info = plugin.itemFactory().read(contents[i]);
            ItemsConfig.Consumable x = plugin.itemFactory().consumable(info);
            if (x != null && x.id().equals(c.id())) return i;
        }
        return -1;
    }

    private static ItemStack withLore(ItemStack item, List<String> extra) {
        var meta = item.getItemMeta();
        if (meta == null) return item;
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String s : extra) lore.add(com.dex.boxpet.text.Text.item(s));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.item.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Auto feed (0.6.0): a food box shared by all pets of the player. The pet that is out eats from it, left to right,
 * once its fullness is at or under the chosen level. Clicking a candy / food in the inventory puts the whole stack in;
 * clicking a box slot takes it back.
 */
public final class AutoFeedMenu extends Menu {

    /** Box slots in eating order (left to right, then the next row). */
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    /** Fullness levels to start eating at. 60 = the biggest food (+40) still fits. */
    public static final int[] LEVELS = {60, 50, 40, 30, 20, 10};
    private static final int STACK = 64;

    private final PlayerData owner;
    private final PetData pet;
    private final Cycler at;
    private final Cycler prefer;

    public AutoFeedMenu(BoxPetPlugin plugin, Player player, PlayerData owner, PetData pet) {
        super(plugin, player, 6, plugin.menus().title("autofeed"));
        this.owner = owner;
        this.pet = pet;
        List<String> levels = new ArrayList<>();
        for (int l : LEVELS) levels.add(plugin.menus().text("autofeed.at-option", "value", String.valueOf(l)));
        at = new Cycler(plugin, "autofeed-at", Material.CLOCK, levels);
        at.index(indexOf(owner.autoFeedAt));
        prefer = new Cycler(plugin, "autofeed-prefer", Material.COMPARATOR,
                List.of(plugin.menus().text("autofeed.prefer-food"), plugin.menus().text("autofeed.prefer-candy")));
        prefer.index(owner.autoFeedCandyFirst ? 1 : 0);
    }

    private static int indexOf(int level) {
        for (int i = 0; i < LEVELS.length; i++) if (LEVELS[i] == level) return i;
        return 3;
    }

    @Override
    protected void draw() {
        set(4, button("autofeed.info", Material.HOPPER), null);
        for (int i = 0; i < SLOTS.length; i++) {
            String id = owner.pantryIds[i];
            if (id == null || owner.pantryAmounts[i] <= 0) continue;
            ItemsConfig.Consumable c = plugin.items().get(id);
            if (c == null) {
                set(SLOTS[i], button("autofeed.unknown", Material.BARRIER), null);
                continue;
            }
            ItemStack icon = plugin.itemFactory().consumable(c, Math.min(STACK, owner.pantryAmounts[i]));
            final int index = i;
            set(SLOTS[i], withLore(icon, List.of("", plugin.menus().text("autofeed.take"))), click -> take(index));
        }
        set(45, button("common.back", Material.ARROW), click -> new ManageMenu(plugin, player, owner, pet, false).open());
        set(47, button(owner.autoFeed ? "autofeed.enabled" : "autofeed.disabled", owner.autoFeed ? Material.LIME_DYE : Material.GRAY_DYE),
                click -> {
                    owner.autoFeed = !owner.autoFeed;
                    owner.autoFeedWarned = false;
                    owner.dirty = true;
                    sound("ui.button.click");
                    refresh();
                });
        set(49, at.icon(), click -> {
            if (!at.click(click)) return;
            owner.autoFeedAt = LEVELS[at.index()];
            owner.autoFeedWarned = false;
            owner.dirty = true;
            refresh();
        });
        set(51, prefer.icon(), click -> {
            if (!prefer.click(click)) return;
            owner.autoFeedCandyFirst = prefer.index() == 1;
            owner.dirty = true;
            refresh();
        });
        fill();
        // empty box slots stay empty, so it looks like a chest
        for (int i = 0; i < SLOTS.length; i++) {
            if (owner.pantryIds[i] == null || owner.pantryAmounts[i] <= 0) set(SLOTS[i], null, null);
        }
    }

    /** Puts the clicked stack into the box: first on top of the same item, then into empty slots, in order. */
    @Override
    protected void onOwnItemClick(ItemStack item, int slot, ClickType click) {
        ItemsConfig.Consumable c = plugin.itemFactory().consumable(plugin.itemFactory().read(item));
        if (c == null) {
            plugin.messages().send(player, "autofeed.not-food");
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        ItemFactory.Info info = plugin.itemFactory().read(stack);
        ItemsConfig.Consumable same = plugin.itemFactory().consumable(info);
        if (stack == null || same == null || !same.id().equals(c.id())) return;
        int left = stack.getAmount();
        for (int i = 0; i < SLOTS.length && left > 0; i++) {
            if (!c.id().equals(owner.pantryIds[i])) continue;
            int put = Math.min(left, STACK - owner.pantryAmounts[i]);
            if (put <= 0) continue;
            owner.pantryAmounts[i] += put;
            left -= put;
        }
        for (int i = 0; i < SLOTS.length && left > 0; i++) {
            if (owner.pantryIds[i] != null && owner.pantryAmounts[i] > 0) continue;
            int put = Math.min(left, STACK);
            owner.pantryIds[i] = c.id();
            owner.pantryAmounts[i] = put;
            left -= put;
        }
        int moved = stack.getAmount() - left;
        if (moved <= 0) {
            plugin.messages().send(player, "autofeed.box-full");
            return;
        }
        if (left <= 0) {
            player.getInventory().setItem(slot, null);
        } else {
            ItemStack rest = stack.clone();
            rest.setAmount(left);
            player.getInventory().setItem(slot, rest);
        }
        changed();
        sound("entity.item.pickup");
        refresh();
    }

    /** Gives a box slot back to the inventory; what does not fit stays in the box. */
    private void take(int index) {
        String id = owner.pantryIds[index];
        int amount = owner.pantryAmounts[index];
        ItemsConfig.Consumable c = id == null ? null : plugin.items().get(id);
        if (c == null || amount <= 0) {
            refresh();
            return;
        }
        int left = 0;
        for (ItemStack rest : player.getInventory().addItem(plugin.itemFactory().consumable(c, amount)).values()) {
            left += rest.getAmount();
        }
        if (left >= amount) {
            plugin.messages().send(player, "autofeed.inventory-full");
            return;
        }
        owner.pantryAmounts[index] = left;
        if (left <= 0) owner.pantryIds[index] = null;
        changed();
        sound("entity.item.pickup");
        refresh();
    }

    /** Box and inventory are saved in the same moment, so a crash never keeps an item in both. */
    private void changed() {
        owner.autoFeedWarned = false;
        owner.dirty = true;
        owner.inventoryChanged = true;
        plugin.pets().save(owner);
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

package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Breakthrough (0.7.0): picks a breakthrough stone from the inventory, highest chance first. */
public final class BreakthroughMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final PlayerData owner;
    private final PetData pet;

    @Override
    protected boolean stale() {
        return !owner.owns(pet);
    }

    public BreakthroughMenu(BoxPetPlugin plugin, Player player, PlayerData owner, PetData pet) {
        super(plugin, player, 4, plugin.menus().title("breakthrough"));
        this.owner = owner;
        this.pet = pet;
    }

    @Override
    protected void draw() {
        if (!owner.pets.containsKey(pet.id)) {
            player.closeInventory();
            return;
        }
        set(4, button("breakthrough.info", Material.ECHO_SHARD, "pet", plugin.pets().displayName(pet),
                "level", String.valueOf(pet.level), "next", String.valueOf(pet.level + 1)), null);
        // chance -> how many in the inventory, highest chance first
        Map<Integer, Integer> counts = new TreeMap<>(java.util.Comparator.reverseOrder());
        for (ItemStack it : player.getInventory().getStorageContents()) {
            int chance = plugin.itemFactory().breakChance(plugin.itemFactory().read(it));
            if (chance > 0) counts.merge(chance, it.getAmount(), Integer::sum);
        }
        if (counts.isEmpty()) set(22, button("breakthrough.none", Material.BARRIER), null);
        int i = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (i >= SLOTS.length) break;
            int chance = e.getKey();
            ItemStack icon = plugin.itemFactory().breakthrough(chance, Math.min(64, e.getValue()));
            icon = withLore(icon, List.of("", plugin.menus().text("food.have", "amount", String.valueOf(e.getValue())),
                    plugin.menus().text("actions.breakthrough")));
            set(SLOTS[i++], icon, click -> {
                int slot = find(chance);
                if (slot < 0) {
                    refresh();
                    return;
                }
                plugin.interact().useBreakthrough(player, pet, slot);
                // done (success): back to the pet; failed: pick again
                if (plugin.xp().needsBreakthrough(pet)) refresh();
                else new ManageMenu(plugin, player, owner, pet, false).open();
            });
        }
        set(31, button("common.back", Material.ARROW), click -> new ManageMenu(plugin, player, owner, pet, false).open());
        fill();
    }

    /** Inventory slot of the first stone with this chance, -1 when none. */
    private int find(int chance) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (plugin.itemFactory().breakChance(plugin.itemFactory().read(contents[i])) == chance) return i;
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

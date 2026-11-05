package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Staff: the last deleted / released pets of a player, click to restore (spec 13). */
public final class DeletedMenu extends Menu {

    private final PlayerData owner;
    private final List<PetData> deleted;

    private DeletedMenu(BoxPetPlugin plugin, Player player, PlayerData owner, List<PetData> deleted) {
        super(plugin, player, 6, plugin.menus().title("deleted"));
        this.owner = owner;
        this.deleted = deleted;
    }

    public static void open(BoxPetPlugin plugin, Player staff, PlayerData owner) {
        plugin.storage().deletedPets(owner.uuid).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.messages().send(staff, "admin.db-error");
                return;
            }
            new DeletedMenu(plugin, staff, owner, list).open();
        }));
    }

    @Override
    protected boolean combatLocked() {
        return false;
    }

    @Override
    protected void draw() {
        for (int i = 0; i < deleted.size() && i < 45; i++) {
            PetData d = deleted.get(i);
            set(i, PetIcons.card(plugin, d, List.of(), List.of(plugin.menus().text("actions.restore"))), click -> {
                plugin.actions().setDeleted(player, d, false);
                if (plugin.pets().data(owner.uuid) != owner) owner.pets.put(d.id, d);
                plugin.messages().send(player, "admin.restored", "id", d.shortId());
                new StorageMenu(plugin, player, owner, true, 0).open();
            });
        }
        if (deleted.isEmpty()) set(22, button("storage.empty", Material.BARRIER), null);
        set(49, button("common.back", Material.ARROW), click -> new StorageMenu(plugin, player, owner, true, 0).open());
        fill();
    }
}

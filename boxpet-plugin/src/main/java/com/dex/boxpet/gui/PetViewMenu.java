package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.data.PetData;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * 0.24.0: someone else's pet, read only (opened by clicking a pet shown in chat): its icon with the full card.
 * Works in combat too, nothing here changes anything.
 */
public final class PetViewMenu extends Menu {

    private final PetData pet;
    private final String ownerName;

    public PetViewMenu(BoxPetPlugin plugin, Player viewer, PetData pet, String ownerName) {
        super(plugin, viewer, 3, plugin.menus().title("pet-view", "player", ownerName));
        this.pet = pet;
        this.ownerName = ownerName;
    }

    @Override
    protected boolean combatLocked() {
        return false;
    }

    @Override
    protected void draw() {
        set(13, PetIcons.card(plugin, pet, List.of(plugin.menus().text("pet-view.owner", "player", ownerName)), List.of()), null);
        set(22, button("pet-view.close", Material.BARRIER), click -> player.closeInventory());
        fill();
    }
}

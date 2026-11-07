package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.data.PetData;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

/**
 * 0.24.0: someone else's pet, read only, opened by clicking a pet shown in chat. 0.24.1: the look of the
 * InteractiveChat [item] view: a 3x3 frame of black and white glass with the pet (and its card on hover) in the middle.
 */
public final class PetViewMenu extends Menu {

    private final PetData pet;
    private final String ownerName;

    public PetViewMenu(BoxPetPlugin plugin, Player viewer, PetData pet, String ownerName) {
        super(plugin, viewer, InventoryType.DROPPER, plugin.menus().title("pet-view", "player", ownerName));
        this.pet = pet;
        this.ownerName = ownerName;
    }

    @Override
    protected boolean combatLocked() {
        return false;
    }

    @Override
    protected void draw() {
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            Material glass = i % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
            set(i, icon(glass, " ", List.of()), null);
        }
        set(4, PetIcons.card(plugin, pet, List.of(plugin.menus().text("pet-view.owner", "player", ownerName)), List.of()), null);
    }
}

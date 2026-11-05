package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Yes / no (evolve, release, speed-up, staff delete). */
public class ConfirmMenu extends Menu {

    private final String question;
    private final Runnable yes;
    private final Runnable no;

    public ConfirmMenu(BoxPetPlugin plugin, Player player, String question, Runnable yes, Runnable no) {
        super(plugin, player, 3, plugin.menus().title("confirm"));
        this.question = question;
        this.yes = yes;
        this.no = no;
    }

    @Override
    protected void draw() {
        set(13, icon(Material.PAPER, question, List.of()), null);
        set(11, button("confirm.yes", Material.LIME_CONCRETE), click -> yes.run());
        set(15, button("confirm.no", Material.RED_CONCRETE), click -> no.run());
        fill();
    }
}

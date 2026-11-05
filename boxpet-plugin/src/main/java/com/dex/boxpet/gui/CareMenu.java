package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.pet.ActivePet;
import com.dex.boxpet.text.Format;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Care menu (spec 11.1): pet, feed, talk, sit / follow, carry, details. Active pet only. */
public final class CareMenu extends Menu {

    public CareMenu(BoxPetPlugin plugin, Player player) {
        super(plugin, player, 3, plugin.menus().title("care"));
    }

    @Override
    protected void draw() {
        ActivePet p = plugin.pets().active(player.getUniqueId());
        if (p == null) {
            set(13, button("care.no-active", Material.BARRIER), click -> player.closeInventory());
            fill();
            return;
        }
        String name = plugin.pets().displayName(p.data);
        set(4, PetIcons.card(plugin, p.data, PetIcons.status(plugin, p.data, true), List.of()), null);
        long now = System.currentTimeMillis();
        String next = now >= p.data.bondGainAt ? plugin.menus().text("care.bond-ready")
                : plugin.menus().text("care.bond-wait", "time", Format.duration((p.data.bondGainAt - now + 999) / 1000));
        set(10, button("care.pet", Material.PINK_DYE, "pet", name, "bond", String.valueOf(p.data.bond), "next", next,
                "gain", String.valueOf(plugin.settings().bondPetGain)), click -> {
            plugin.care().pet(player, p);
            refresh();
        });
        set(11, button("care.feed", Material.COOKIE, "pet", name, "fullness", String.valueOf(p.data.fullness)),
                click -> new FoodMenu(plugin, player).open());
        set(12, button("care.talk", Material.PAPER, "pet", name), click -> {
            plugin.chat().say(p, "talk");
            plugin.pets().playAnimation(p, "interact");
        });
        set(14, button(p.sitting ? "care.follow" : "care.sit", p.sitting ? Material.LEAD : Material.OAK_SIGN, "pet", name), click -> {
            plugin.messages().send(player, plugin.pets().toggleSit(p), "pet", name);
            refresh();
        });
        if (p.type.carryEnabled) {
            set(15, button(p.carried ? "care.put-down" : "care.carry", Material.SADDLE, "pet", name), click -> {
                plugin.messages().send(player, plugin.pets().toggleCarry(p), "pet", name);
                refresh();
            });
        }
        set(16, button("care.details", Material.BOOK), click -> new PetDetailMenu(plugin, player, p.player, p.data, false).open());
        set(22, button("common.close", Material.BARRIER), click -> player.closeInventory());
        fill();
    }
}

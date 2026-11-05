package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.data.IncubatorSlot;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** /pet (spec 13.1): active pet, storage, incubator, collection, toggles. */
public final class MainMenu extends Menu {

    public MainMenu(BoxPetPlugin plugin, Player player) {
        super(plugin, player, 5, plugin.menus().title("main"));
    }

    @Override
    protected void draw() {
        PlayerData pd = plugin.pets().data(player.getUniqueId());
        if (pd == null) {
            set(22, button("main.loading", Material.CLOCK), null);
            fill();
            return;
        }
        PetData active = pd.active();
        if (active != null) {
            set(13, PetIcons.card(plugin, active, PetIcons.status(plugin, active, true),
                    List.of(plugin.menus().text("actions.manage"))), click -> new ManageMenu(plugin, player, pd, active, false).open());
            set(38, button("main.dismiss", Material.GRAY_DYE, "pet", plugin.pets().displayName(active)), click -> {
                plugin.pets().dismiss(player, true, "menu");
                plugin.messages().send(player, "dismiss.done", "pet", plugin.pets().displayName(active));
                refresh();
            });
        } else {
            set(13, button("main.no-active", Material.LEAD), click -> new StorageMenu(plugin, player, pd, false, 0).open());
        }
        set(28, button("main.library", Material.KNOWLEDGE_BOOK, "total", String.valueOf(plugin.types().size())),
                click -> new LibraryMenu(plugin, player, pd).open());
        set(30, button("main.storage", Material.CHEST, "used", String.valueOf(pd.pets.size()),
                "max", String.valueOf(plugin.pets().capacity(player))), click -> new StorageMenu(plugin, player, pd, false, 0).open());
        long now = System.currentTimeMillis();
        int hatching = 0;
        int ready = 0;
        for (IncubatorSlot s : pd.incubator.values()) {
            if (s.ready(now)) ready++;
            else hatching++;
        }
        set(32, button("main.incubator", Material.SNIFFER_EGG, "hatching", String.valueOf(hatching), "ready", String.valueOf(ready),
                "slots", String.valueOf(plugin.incubator().slots(player))), click -> new IncubatorMenu(plugin, player, pd).open());
        set(34, button("main.collection", Material.BOOK, "score", String.valueOf(plugin.collectionService().score(pd)),
                "owned", String.valueOf(pd.collection.size()), "total", String.valueOf(plugin.types().size())),
                click -> new CollectionMenu(plugin, player, pd, 0).open());
        set(40, button("main.hide-others", Material.ENDER_EYE, "state", onOff(pd.hideOthers)), click -> {
            pd.hideOthers = !pd.hideOthers;
            pd.dirty = true;
            plugin.pets().applyHiddenFor(player);
            plugin.messages().send(player, pd.hideOthers ? "toggle.hide-others-on" : "toggle.hide-others-off");
            refresh();
        });
        set(42, button("main.pet-chat", Material.WRITABLE_BOOK, "state", onOff(pd.petChat)), click -> {
            pd.petChat = !pd.petChat;
            pd.dirty = true;
            plugin.messages().send(player, pd.petChat ? "toggle.chat-on" : "toggle.chat-off");
            refresh();
        });
        fill();
    }

    private String onOff(boolean on) {
        return plugin.menus().text(on ? "state.on" : "state.off");
    }
}

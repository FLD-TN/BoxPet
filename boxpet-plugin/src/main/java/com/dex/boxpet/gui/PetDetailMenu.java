package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.text.Format;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Pet detail (spec 10.3): summon / dismiss, care, rename, evolve, release. Staff mode: transfer, set, delete. */
public final class PetDetailMenu extends Menu {

    private final PlayerData owner;
    private final PetData pet;
    private final boolean admin;

    public PetDetailMenu(BoxPetPlugin plugin, Player player, PlayerData owner, PetData pet, boolean admin) {
        super(plugin, player, 5, plugin.menus().title("detail", "pet", plugin.pets().displayName(pet)));
        this.owner = owner;
        this.pet = pet;
        this.admin = admin;
    }

    @Override
    protected boolean combatLocked() {
        return !admin;
    }

    @Override
    protected void draw() {
        boolean active = pet.id.equals(owner.activePet);
        set(13, PetIcons.card(plugin, pet, PetIcons.status(plugin, pet, active), List.of()), null);
        if (admin) drawAdmin();
        else drawPlayer(active);
        set(40, button("common.back", Material.ARROW), click -> {
            if (admin) new StorageMenu(plugin, player, owner, true, 0).open();
            else new StorageMenu(plugin, player, owner, false, 0).open();
        });
        fill();
    }

    private void drawPlayer(boolean active) {
        String name = plugin.pets().displayName(pet);
        if (active) {
            set(29, button("detail.dismiss", Material.GRAY_DYE, "pet", name), click -> {
                plugin.pets().dismiss(player, true, "menu");
                plugin.messages().send(player, "dismiss.done", "pet", name);
                refresh();
            });
            set(30, button("detail.care", Material.POPPY, "pet", name), click -> new CareMenu(plugin, player).open());
        } else {
            set(29, button("detail.summon", Material.LIME_DYE, "pet", name), click -> {
                StorageMenu.summon(plugin, player, pet);
                if (pet.id.equals(owner.activePet)) refresh();
            });
        }
        set(31, button("detail.rename", Material.NAME_TAG, "min", String.valueOf(plugin.settings().renameMin),
                "max", String.valueOf(plugin.settings().renameMax)), click -> {
            player.closeInventory();
            plugin.messages().send(player, "rename.prompt", "pet", plugin.pets().displayName(pet));
            plugin.input().ask(player, text -> {
                String key = plugin.actions().rename(player, pet, text);
                plugin.messages().send(player, key, "pet", plugin.pets().displayName(pet),
                        "min", String.valueOf(plugin.settings().renameMin), "max", String.valueOf(plugin.settings().renameMax));
                open();
            }, this::open);
        });
        PetType.Stage next = plugin.actions().nextStage(pet);
        if (next != null) {
            String cost = costText(next);
            set(32, button(pet.level >= next.minLevel() ? "detail.evolve" : "detail.evolve-locked", Material.EXPERIENCE_BOTTLE,
                    "level", String.valueOf(next.minLevel()), "cost", cost), click -> {
                if (pet.level < next.minLevel()) {
                    plugin.messages().send(player, "evolve.level", "level", String.valueOf(next.minLevel()));
                    return;
                }
                new ConfirmMenu(plugin, player, plugin.menus().text("confirm.evolve", "pet", plugin.pets().displayName(pet), "cost", cost),
                        () -> {
                            String key = plugin.actions().evolve(player, owner, pet);
                            plugin.messages().send(player, key, "pet", plugin.pets().displayName(pet),
                                    "level", String.valueOf(next.minLevel()), "stage", String.valueOf(pet.stage + 1));
                            open();
                        }, this::open).open();
            });
        }
        set(33, button("detail.release", Material.LAVA_BUCKET), click -> new ConfirmMenu(plugin, player,
                plugin.menus().text("confirm.release", "pet", plugin.pets().displayName(pet)), () -> {
            plugin.actions().release(player, owner, pet);
            plugin.messages().send(player, "release.done", "pet", plugin.pets().displayName(pet));
            new StorageMenu(plugin, player, owner, false, 0).open();
        }, this::open).open());
    }

    private String costText(PetType.Stage next) {
        StringBuilder sb = new StringBuilder();
        if (next.costMoney() > 0) sb.append("<green>").append(Format.number(Math.round(next.costMoney()))).append("$");
        if (next.costGem() > 0) {
            if (sb.length() > 0) sb.append(" <gray>+ ");
            sb.append("<#8245F8>").append(Format.number(next.costGem())).append(" Gem");
        }
        return sb.length() == 0 ? plugin.menus().text("detail.free") : sb.toString();
    }

    private void drawAdmin() {
        set(29, button("admin.transfer", Material.ENDER_PEARL), click -> {
            player.closeInventory();
            plugin.messages().send(player, "admin.transfer-prompt");
            plugin.input().ask(player, text -> {
                OfflinePlayer to = Bukkit.getOfflinePlayerIfCached(text.trim());
                if (to == null) {
                    plugin.messages().send(player, "admin.unknown-player", "player", text.trim());
                    open();
                    return;
                }
                UUID id = to.getUniqueId();
                plugin.actions().transfer(player, pet, id);
                owner.pets.remove(pet.id);
                plugin.messages().send(player, "admin.transferred", "id", pet.shortId(), "player", to.getName());
                new StorageMenu(plugin, player, owner, true, 0).open();
            }, this::open);
        });
        set(30, button("admin.set-level", Material.EXPERIENCE_BOTTLE), click -> askSet("level"));
        set(31, button("admin.set-rarity", Material.AMETHYST_SHARD, "rarities", String.join(", ", plugin.rarities().ids())),
                click -> askSet("rarity"));
        set(32, button("admin.set-care", Material.COOKIE), click -> {
            if (click.isRightClick()) askSet("fullness");
            else askSet("bond");
        });
        set(33, button("admin.delete", Material.LAVA_BUCKET), click -> new ConfirmMenu(plugin, player,
                plugin.menus().text("confirm.admin-delete", "id", pet.shortId()), () -> {
            plugin.actions().setDeleted(player, pet, true);
            owner.pets.remove(pet.id);
            plugin.messages().send(player, "admin.deleted", "id", pet.shortId());
            new StorageMenu(plugin, player, owner, true, 0).open();
        }, this::open) {
            @Override
            protected boolean combatLocked() {
                return false;
            }
        }.open());
    }

    private void askSet(String field) {
        player.closeInventory();
        plugin.messages().send(player, "admin.set-prompt", "field", field);
        plugin.input().ask(player, text -> {
            String key = plugin.actions().set(player, pet, field, text);
            plugin.messages().send(player, key, "id", pet.shortId(), "field", field, "value", text);
            open();
        }, this::open);
    }
}

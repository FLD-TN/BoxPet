package com.dex.boxpet.gui;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.pet.ActivePet;
import com.dex.boxpet.text.Format;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Quản lý pet (0.5.0): pet detail and care in one menu. Feeding works for any pet (also resting in storage); petting,
 * talking and sit / follow need the pet to be out. "Thả pet đi" stands alone at the bottom so it is read carefully.
 * Staff mode: transfer, set level / care values, delete.
 */
public final class ManageMenu extends Menu {

    private final PlayerData owner;
    private final PetData pet;
    private final boolean admin;

    public ManageMenu(BoxPetPlugin plugin, Player player, PlayerData owner, PetData pet, boolean admin) {
        super(plugin, player, 6, plugin.menus().title("manage", "pet", plugin.pets().displayName(pet)));
        this.owner = owner;
        this.pet = pet;
        this.admin = admin;
    }

    @Override
    protected boolean combatLocked() {
        return !admin;
    }

    private ActivePet out() {
        ActivePet p = plugin.pets().active(owner.uuid);
        return p != null && p.data == pet ? p : null;
    }

    @Override
    protected void draw() {
        boolean active = pet.id.equals(owner.activePet);
        set(13, PetIcons.card(plugin, pet, PetIcons.status(plugin, pet, active), List.of()), null);
        if (admin) drawAdmin();
        else drawPlayer(active);
        set(45, button("common.back", Material.ARROW), click -> new StorageMenu(plugin, player, owner, admin, 0).open());
        fill();
    }

    private void drawPlayer(boolean active) {
        String name = plugin.pets().displayName(pet);
        // row 1: out / feed / pet / talk / sit
        if (active) {
            set(29, button("manage.dismiss", Material.GRAY_DYE, "pet", name), click -> {
                plugin.pets().dismiss(player, true, "menu");
                plugin.messages().send(player, "dismiss.done", "pet", name);
                refresh();
            });
        } else {
            set(29, button("manage.summon", Material.LIME_DYE, "pet", name), click -> {
                StorageMenu.summon(plugin, player, pet);
                refresh();
            });
        }
        set(30, button("manage.feed", Material.COOKIE, "pet", name, "fullness", String.valueOf(pet.fullness)),
                click -> new FoodMenu(plugin, player, owner, pet).open());
        long now = System.currentTimeMillis();
        String next = now >= pet.bondGainAt ? plugin.menus().text("manage.bond-ready")
                : plugin.menus().text("manage.bond-wait", "time", Format.duration((pet.bondGainAt - now + 999) / 1000));
        set(31, button("manage.pet", Material.PINK_DYE, "pet", name, "bond", String.valueOf(pet.bond), "next", next,
                "gain", String.valueOf(plugin.settings().bondPetGain)), click -> {
            ActivePet p = out();
            if (p == null) {
                plugin.messages().send(player, "manage.need-out-pet", "pet", name);
                return;
            }
            plugin.care().pet(player, p);
            refresh();
        });
        set(32, button("manage.talk", Material.PAPER, "pet", name), click -> {
            ActivePet p = out();
            if (p == null) {
                plugin.messages().send(player, "manage.need-out-talk", "pet", name);
                return;
            }
            plugin.chat().say(p, "talk");
            plugin.pets().playAnimation(p, "interact");
        });
        ActivePet p = out();
        boolean sitting = p != null && p.sitting;
        set(33, button(sitting ? "manage.follow" : "manage.sit", sitting ? Material.LEAD : Material.OAK_SIGN, "pet", name), click -> {
            ActivePet o = out();
            if (o == null) {
                plugin.messages().send(player, "manage.need-out-sit", "pet", name);
                return;
            }
            plugin.messages().send(player, plugin.pets().toggleSit(o), "pet", name);
            refresh();
        });
        // row 2: rename / evolve
        set(39, button("manage.rename", Material.NAME_TAG, "min", String.valueOf(plugin.settings().renameMin),
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
        PetType.Stage next2 = plugin.actions().nextStage(pet);
        if (next2 != null) {
            String cost = costText(next2);
            set(41, button(pet.level >= next2.minLevel() ? "manage.evolve" : "manage.evolve-locked", Material.EXPERIENCE_BOTTLE,
                    "level", String.valueOf(next2.minLevel()), "cost", cost), click -> {
                if (pet.level < next2.minLevel()) {
                    plugin.messages().send(player, "evolve.level", "level", String.valueOf(next2.minLevel()));
                    return;
                }
                new ConfirmMenu(plugin, player, plugin.menus().text("confirm.evolve", "pet", plugin.pets().displayName(pet), "cost", cost),
                        () -> {
                            String key = plugin.actions().evolve(player, owner, pet);
                            plugin.messages().send(player, key, "pet", plugin.pets().displayName(pet),
                                    "level", String.valueOf(next2.minLevel()), "stage", String.valueOf(pet.stage + 1));
                            open();
                        }, this::open).open();
            });
        }
        // the release button stands alone, framed in red, so it is read before clicking
        set(48, icon(Material.RED_STAINED_GLASS_PANE, " ", List.of()), null);
        set(50, icon(Material.RED_STAINED_GLASS_PANE, " ", List.of()), null);
        set(49, button("manage.release", Material.LAVA_BUCKET, "pet", name), click -> new ConfirmMenu(plugin, player,
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
        return sb.length() == 0 ? plugin.menus().text("manage.free") : sb.toString();
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
        set(31, button("admin.set-level", Material.EXPERIENCE_BOTTLE), click -> askSet("level"));
        set(33, button("admin.set-care", Material.COOKIE), click -> askSet(click.isRightClick() ? "fullness" : "bond"));
        set(48, icon(Material.RED_STAINED_GLASS_PANE, " ", List.of()), null);
        set(50, icon(Material.RED_STAINED_GLASS_PANE, " ", List.of()), null);
        set(49, button("admin.delete", Material.LAVA_BUCKET), click -> new ConfirmMenu(plugin, player,
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

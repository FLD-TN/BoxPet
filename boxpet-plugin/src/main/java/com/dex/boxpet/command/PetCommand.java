package com.dex.boxpet.command;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.gui.MainMenu;
import com.dex.boxpet.gui.StorageMenu;
import com.dex.boxpet.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /pet [summon <name> | dismiss | hideothers | chat | rename <name>]} (spec 12, 13). */
public final class PetCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("dismiss", "hideothers", "chat", "thongbao", "rename", "chedo", "muctieu");
    private static final List<String> MODES = List.of("tancong", "phongthu", "thanthien");
    private static final List<String> TARGETS = List.of("tatca", "nguoi", "mob");
    /** Shown by tab completion (mob is written quaivat). */
    private static final List<String> TARGET_WORDS = List.of("tatca", "nguoi", "quaivat");

    private final BoxPetPlugin plugin;

    public PetCommand(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "only-players");
            return true;
        }
        PlayerData pd = plugin.pets().data(p.getUniqueId());
        if (pd == null) {
            plugin.messages().send(p, "loading");
            return true;
        }
        if (args.length == 0) {
            new MainMenu(plugin, p).open();
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "dismiss" -> {
                PetData active = pd.active();
                if (active == null) {
                    plugin.messages().send(p, "dismiss.none");
                    return true;
                }
                if (plugin.interact().combatLocked(p)) {
                    plugin.interact().lockMessage(p);
                    return true;
                }
                plugin.pets().dismiss(p, true, "command");
                plugin.messages().send(p, "dismiss.done", "pet", plugin.pets().displayName(active));
            }
            case "hideothers" -> {
                pd.hideOthers = !pd.hideOthers;
                pd.dirty = true;
                plugin.pets().applyHiddenFor(p);
                plugin.messages().send(p, pd.hideOthers ? "toggle.hide-others-on" : "toggle.hide-others-off");
            }
            case "xem" -> view(p, args);
            case "chedo" -> {
                String arg = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                if (arg.equals("bidong")) arg = "thanthien"; // 0.21.1: old name still works
                int i = args.length > 1 ? MODES.indexOf(arg) : (pd.petMode + 1) % 3;
                if (i < 0) {
                    plugin.messages().send(p, "behaviour.usage-mode");
                    return true;
                }
                pd.petMode = i;
                pd.dirty = true;
                plugin.messages().send(p, "behaviour.mode", "mode", plugin.menus().text("behaviour.modes." + MODES.get(i) + ".name"));
            }
            case "muctieu" -> {
                String targetArg = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
                if (targetArg.equals("quaivat")) targetArg = "mob"; // 0.21.2: shown as Quái vật
                int i = args.length > 1 ? TARGETS.indexOf(targetArg) : (pd.petTarget + 1) % 3;
                if (i < 0) {
                    plugin.messages().send(p, "behaviour.usage-target");
                    return true;
                }
                pd.petTarget = i;
                pd.dirty = true;
                plugin.messages().send(p, "behaviour.target", "target", plugin.menus().text("behaviour.targets." + TARGETS.get(i) + ".name"));
            }
            case "thongbao" -> {
                pd.castChat = !pd.castChat;
                pd.dirty = true;
                plugin.messages().send(p, pd.castChat ? "toggle.cast-on" : "toggle.cast-off");
            }
            case "chat" -> {
                pd.petChat = !pd.petChat;
                pd.dirty = true;
                plugin.messages().send(p, pd.petChat ? "toggle.chat-on" : "toggle.chat-off");
            }
            case "rename" -> {
                if (!p.hasPermission("boxpet.rename")) {
                    plugin.messages().send(p, "no-permission");
                    return true;
                }
                PetData active = pd.active();
                if (active == null) {
                    plugin.messages().send(p, "rename.no-active");
                    return true;
                }
                if (args.length < 2) {
                    plugin.messages().send(p, "usage.rename");
                    return true;
                }
                String key = plugin.actions().rename(p, active, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                plugin.messages().send(p, key, "wait", String.valueOf(plugin.actions().renameWait(p)), "pet", plugin.pets().displayName(active),
                        "min", String.valueOf(plugin.settings().renameMin), "max", String.valueOf(plugin.settings().renameMax));
            }
            default -> plugin.messages().send(p, "usage.pet");
        }
        return true;
    }

    /** By short id, custom name or type name (case and colours ignored). */
    private PetData find(PlayerData pd, String query) {
        String q = query.toLowerCase(Locale.ROOT).trim().replace('_', ' ');
        for (PetData d : pd.pets.values()) if (d.shortId().equals(q)) return d;
        String typeQuery = query.toLowerCase(Locale.ROOT).trim();
        for (PetData d : pd.pets.values()) {
            if (!d.customName.isEmpty() && Text.plain(d.customName).toLowerCase(Locale.ROOT).equals(q)) return d;
        }
        for (PetData d : pd.pets.values()) {
            PetType t = plugin.pets().type(d);
            if (t != null && (t.name.toLowerCase(Locale.ROOT).equals(q) || t.id.equals(typeQuery))) return d;
        }
        return null;
    }

    /**
     * 0.24.0: /pet xem <player> [pet id]: a read-only view of that pet (the one shown in chat), or of the player's
     * active pet. An offline owner's pet is read from the database.
     */
    private void view(Player viewer, String[] args) {
        // 0.24.1: hidden: only the chat click of a shown pet knows the pet id; anything else looks like an unknown command
        java.util.UUID id;
        try {
            id = args.length > 2 ? java.util.UUID.fromString(args[2]) : null;
        } catch (IllegalArgumentException ex) {
            id = null;
        }
        if (id == null) {
            plugin.messages().send(viewer, "usage.pet");
            return;
        }
        String name = args[1];
        Player owner = org.bukkit.Bukkit.getPlayerExact(name);
        PlayerData od = owner == null ? null : plugin.pets().data(owner.getUniqueId());
        if (od != null) {
            PetData pet = od.pets.get(id);
            if (pet != null) {
                new com.dex.boxpet.gui.PetViewMenu(plugin, viewer, pet, owner.getName()).open();
                return;
            }
        }
        if (id == null) {
            plugin.messages().send(viewer, "pet-view.not-found");
            return;
        }
        final java.util.UUID want = id;
        plugin.storage().findPets(want.toString()).whenComplete((list, err) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) return;
            PetData found = null;
            if (err == null && list != null) {
                for (PetData d : list) if (d.id.equals(want) && d.state != PetData.State.DELETED) found = d;
            }
            if (found == null) {
                plugin.messages().send(viewer, "pet-view.not-found");
                return;
            }
            plugin.care().settle(found, System.currentTimeMillis()); // 0.27.0: offline owner, fullness up to now (view only)
            new com.dex.boxpet.gui.PetViewMenu(plugin, viewer, found, name).open();
        }));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            // 0.22.0: the choices of /pet chedo and /pet muctieu
            List<String> choices = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "chedo" -> MODES;
                case "muctieu" -> TARGET_WORDS;
                default -> List.of();
            };
            for (String s : choices) if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
        }
        return out;
    }
}

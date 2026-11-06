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

    private static final List<String> SUBS = List.of("summon", "dismiss", "hideothers", "chat", "thongbao", "rename");

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
            case "summon" -> {
                if (args.length < 2) {
                    plugin.messages().send(p, "usage.summon");
                    return true;
                }
                PetData pet = find(pd, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                if (pet == null) plugin.messages().send(p, "summon.not-found", "name", args[1]);
                else StorageMenu.summon(plugin, p, pet);
            }
            case "dismiss" -> {
                PetData active = pd.active();
                if (active == null) {
                    plugin.messages().send(p, "dismiss.none");
                    return true;
                }
                if (plugin.pets().combat().tagged(p.getUniqueId())) {
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("summon") && sender instanceof Player p) {
            PlayerData pd = plugin.pets().data(p.getUniqueId());
            if (pd != null) {
                for (PetData d : pd.pets.values()) {
                    String n = d.customName.isEmpty() ? d.type : Text.plain(d.customName).replace(' ', '_');
                    if (n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(n);
                }
            }
        }
        return out;
    }
}

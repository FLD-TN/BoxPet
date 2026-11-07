package com.dex.boxpet.command;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.gui.StorageMenu;
import com.dex.boxpet.pet.PetVisual;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** {@code /boxpet} staff commands (spec 13). Console allowed except menus. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "pets", "transfer", "set", "delete", "restore", "incubator",
            "models", "reload");

    private final BoxPetPlugin plugin;

    public AdminCommand(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("boxpet.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "pets" -> pets(sender, args);
            case "transfer" -> {
                if (args.length < 3) {
                    usage(sender);
                    return true;
                }
                OfflinePlayer to = offline(args[2]);
                if (to == null) {
                    plugin.messages().send(sender, "admin.unknown-player", "player", args[2]);
                    return true;
                }
                withPet(sender, args[1], pet -> {
                    plugin.actions().transfer(sender, pet, to.getUniqueId());
                    plugin.messages().send(sender, "admin.transferred", "id", pet.shortId(), "player", to.getName());
                });
            }
            case "set" -> {
                if (args.length < 4) {
                    usage(sender);
                    return true;
                }
                withPet(sender, args[1], pet -> plugin.messages().send(sender, plugin.actions().set(sender, pet, args[2], args[3]),
                        "id", pet.shortId(), "field", args[2], "value", args[3]));
            }
            case "delete", "restore" -> {
                if (args.length < 2) {
                    usage(sender);
                    return true;
                }
                boolean delete = args[0].equalsIgnoreCase("delete");
                withPet(sender, args[1], pet -> {
                    if (delete == (pet.state == PetData.State.DELETED)) {
                        plugin.messages().send(sender, delete ? "admin.already-deleted" : "admin.not-deleted", "id", pet.shortId());
                        return;
                    }
                    plugin.actions().setDeleted(sender, pet, delete);
                    plugin.messages().send(sender, delete ? "admin.deleted" : "admin.restored", "id", pet.shortId());
                });
            }
            case "incubator" -> incubator(sender, args);
            case "models" -> models(sender);
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages().send(sender, "admin.reloaded", "pets", String.valueOf(plugin.types().size()),
                        "abilities", String.valueOf(plugin.abilities().size()));
            }
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender s) {
        for (String line : plugin.messages().rawList("usage.admin")) plugin.messages().sendPlain(s, line);
    }

    private static OfflinePlayer offline(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private int amount(String[] args, int index) {
        if (args.length <= index) return 1;
        try {
            return Math.max(1, Math.min(2304, Integer.parseInt(args[index])));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    // ------------------------------------------------------------------ give

    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "admin.player-offline", "player", args[2]);
            return;
        }
        String what = args[1].toLowerCase(Locale.ROOT);
        switch (what) {
            case "egg", "petegg" -> {
                PetType t = plugin.types().get(args[3]);
                if (t == null) {
                    plugin.messages().send(sender, "admin.bad-pet", "pet", args[3]);
                    return;
                }
                int n = amount(args, 4);
                for (int i = 0; i < n; i++) deliver(target, plugin.itemFactory().egg(t, 1), 1);
                plugin.messages().send(sender, "admin.given", "amount", String.valueOf(n), "item", t.colouredName(), "player", target.getName());
            }
            case "randomegg" -> {
                Rarity r = plugin.rarities().get(args[3]);
                if (r == null || plugin.incubator().poolSize(r) == 0) {
                    plugin.messages().send(sender, "admin.bad-rarity", "rarity", args[3]);
                    return;
                }
                int n = amount(args, 4);
                for (int i = 0; i < n; i++) deliver(target, plugin.itemFactory().randomEgg(r, 1), 1);
                plugin.messages().send(sender, "admin.given", "amount", String.valueOf(n), "item",
                        plugin.items().randomEgg(r.id()).name() + " " + r.coloured(), "player", target.getName());
            }
            case "candy", "food" -> {
                ItemsConfig.Consumable c = plugin.items().get(args[3]);
                if (c == null || c.candy() != what.equals("candy")) {
                    plugin.messages().send(sender, "admin.bad-item", "item", args[3]);
                    return;
                }
                int n = amount(args, 4);
                deliver(target, plugin.itemFactory().consumable(c, 1), n);
                plugin.messages().send(sender, "admin.given", "amount", String.valueOf(n), "item", "<" + c.color() + ">" + c.name(), "player", target.getName());
            }
            case "breakthrough" -> {
                int chance;
                try {
                    chance = Integer.parseInt(args[3].replace("%", ""));
                } catch (NumberFormatException ex) {
                    chance = 0;
                }
                if (chance < 1 || chance > 100) {
                    plugin.messages().send(sender, "admin.bad-chance", "chance", args[3]);
                    return;
                }
                int n = amount(args, 4);
                ItemStack stone = plugin.itemFactory().breakthrough(chance, 1);
                deliver(target, stone, n);
                plugin.messages().send(sender, "admin.given", "amount", String.valueOf(n), "item",
                        plugin.items().breakthrough.name() + " <dark_gray>[<#7CFC9A>" + chance + "%<dark_gray>]", "player", target.getName());
            }
            case "pet" -> {
                PetType t = plugin.types().get(args[3]);
                PlayerData pd = plugin.pets().data(target.getUniqueId());
                if (t == null) {
                    plugin.messages().send(sender, "admin.bad-pet", "pet", args[3]);
                    return;
                }
                Rarity r = plugin.rarities().get(t.rarity);
                if (r == null) r = plugin.rarities().lowest();
                if (pd == null) {
                    plugin.messages().send(sender, "loading");
                    return;
                }
                PetData pet = plugin.pets().createPet(target.getUniqueId(), t, "admin");
                if (args.length > 4) {
                    try {
                        pet.level = Math.max(1, Math.min(r.maxLevel(), Integer.parseInt(args[4])));
                    } catch (NumberFormatException ignored) {
                        // level 1
                    }
                }
                plugin.pets().addPet(pd, pet);
                plugin.pets().save(pd);
                plugin.actions().log(sender, "give pet " + pet.shortId() + " " + t.id + " to " + target.getName());
                plugin.messages().send(sender, "admin.given", "amount", "1", "item", plugin.pets().displayName(pet), "player", target.getName());
                plugin.messages().send(target, "admin.received-pet", "pet", plugin.pets().displayName(pet));
            }
            default -> usage(sender);
        }
    }

    /** Fills the inventory, drops the rest at the player's feet. */
    private static void deliver(Player p, ItemStack one, int amount) {
        int left = amount;
        while (left > 0) {
            ItemStack stack = one.clone();
            int n = Math.min(left, Math.max(1, stack.getMaxStackSize()));
            stack.setAmount(n);
            left -= n;
            for (ItemStack rest : p.getInventory().addItem(stack).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    // ------------------------------------------------------------------ pets / ids

    private void pets(CommandSender sender, String[] args) {
        if (!(sender instanceof Player staff)) {
            plugin.messages().send(sender, "only-players");
            return;
        }
        if (args.length < 2) {
            usage(sender);
            return;
        }
        OfflinePlayer target = offline(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "admin.unknown-player", "player", args[1]);
            return;
        }
        PlayerData loaded = plugin.pets().data(target.getUniqueId());
        if (loaded != null) {
            new StorageMenu(plugin, staff, loaded, true, 0).open();
            return;
        }
        UUID id = target.getUniqueId();
        plugin.storage().loadPlayer(id).whenComplete((pd, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.messages().send(staff, "admin.db-error");
                return;
            }
            new StorageMenu(plugin, staff, pd, true, 0).open();
        }));
    }

    /** Finds one pet by the start of its id (any owner, any state), then runs {@code then} on the main thread. */
    private void withPet(CommandSender sender, String prefix, Consumer<PetData> then) {
        if (prefix.length() < 4) {
            plugin.messages().send(sender, "admin.id-short");
            return;
        }
        for (PlayerData pd : plugin.pets().loaded()) {
            for (PetData d : pd.pets.values()) {
                if (d.id.toString().startsWith(prefix.toLowerCase(Locale.ROOT))) {
                    then.accept(d);
                    return;
                }
            }
        }
        plugin.storage().findPets(prefix).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.messages().send(sender, "admin.db-error");
                return;
            }
            if (list.isEmpty()) plugin.messages().send(sender, "admin.no-pet", "id", prefix);
            else if (list.size() > 1) plugin.messages().send(sender, "admin.many-pets", "id", prefix, "count", String.valueOf(list.size()));
            else then.accept(plugin.actions().live(list.get(0)));
        }));
    }

    private void incubator(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[2].equalsIgnoreCase("clear")) {
            usage(sender);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        PlayerData pd = target == null ? null : plugin.pets().data(target.getUniqueId());
        if (pd == null) {
            plugin.messages().send(sender, "admin.player-offline", "player", args[1]);
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[3]) - 1;
        } catch (NumberFormatException ex) {
            usage(sender);
            return;
        }
        if (pd.incubator.remove(slot) == null) {
            plugin.messages().send(sender, "admin.incubator-empty", "slot", args[3]);
            return;
        }
        pd.incubatorDirty = true;
        plugin.pets().save(pd);
        plugin.actions().log(sender, "incubator clear " + target.getName() + " slot " + args[3]);
        plugin.messages().send(sender, "admin.incubator-cleared", "slot", args[3], "player", target.getName());
    }

    private void models(CommandSender sender) {
        int missing = 0;
        for (PetType t : plugin.types().all()) {
            for (PetType.Stage s : t.stages) {
                if (!PetVisual.exists(s.model())) {
                    missing++;
                    plugin.messages().send(sender, "admin.model-missing", "pet", t.id, "model", s.model());
                }
            }
            if (!t.ability2.isEmpty() && plugin.abilities().get(t.ability2) == null) {
                plugin.messages().send(sender, "admin.ability-missing", "pet", t.id, "ability", t.ability2);
            }
            var a = plugin.abilities().get(t.ability);
            if (!t.ability.isEmpty() && a == null) {
                missing++;
                plugin.messages().send(sender, "admin.ability-missing", "pet", t.id, "ability", t.ability);
            }
            if (a != null && a.vfx != null && !PetVisual.exists(a.vfx.model())) {
                missing++;
                plugin.messages().send(sender, "admin.model-missing", "pet", t.id + " (" + a.id + ")", "model", a.vfx.model());
            }
        }
        plugin.messages().send(sender, missing == 0 ? "admin.models-ok" : "admin.models-bad", "count", String.valueOf(missing),
                "pets", String.valueOf(plugin.types().size()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("boxpet.admin")) return out;
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = switch (args.length) {
            case 1 -> SUBS;
            case 2 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> List.of("egg", "petegg", "randomegg", "candy", "food", "breakthrough", "pet");
                case "pets", "incubator" -> players();
                case "transfer", "set", "delete", "restore" -> loadedIds();
                default -> List.of();
            };
            case 3 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "transfer" -> players();
                case "set" -> List.of("level", "xp", "bond", "fullness");
                case "incubator" -> List.of("clear");
                default -> List.of();
            };
            case 4 -> switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "egg", "petegg", "pet" -> plugin.types().ids();
                    case "randomegg" -> plugin.rarities().ids();
                    case "candy" -> plugin.items().ids(true);
                    case "food" -> plugin.items().ids(false);
                    case "breakthrough" -> List.of("10", "25", "50", "75", "100");
                    default -> List.of();
                };
                default -> List.of();
            };
            default -> List.of();
        };
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(last)) out.add(o);
        return out;
    }

    private static List<String> players() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    private List<String> loadedIds() {
        List<String> out = new ArrayList<>();
        for (PlayerData pd : plugin.pets().loaded()) for (PetData d : pd.pets.values()) out.add(d.shortId());
        return out;
    }
}

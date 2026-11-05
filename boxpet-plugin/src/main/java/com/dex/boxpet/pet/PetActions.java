package com.dex.boxpet.pet;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.api.event.PetEvolveEvent;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.config.Settings;
import com.dex.boxpet.data.PetData;
import com.dex.boxpet.data.PlayerData;
import com.dex.boxpet.text.Text;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Rename, evolve, release and staff actions (spec 9.3, 10.4, 12, 13). Main thread. */
public final class PetActions {

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern LEGACY = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]|[&§]#[0-9a-fA-F]{6}");

    private final BoxPetPlugin plugin;
    private final Map<UUID, Long> lastRename = new HashMap<>();

    public PetActions(BoxPetPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ rename (spec 12)

    /** @return the message key of the result */
    public String rename(Player player, PetData pet, String raw) {
        long now = System.currentTimeMillis();
        Long last = lastRename.get(player.getUniqueId());
        if (last != null && now - last < s().renameCooldownSeconds * 1000L) return "rename.cooldown";
        String name = raw.trim();
        if (name.equals("-") || name.equalsIgnoreCase("xoa") || name.equalsIgnoreCase("xoá")) {
            pet.customName = "";
            pet.dirty = true;
            lastRename.put(player.getUniqueId(), now);
            refreshActive(pet);
            return "rename.reset";
        }
        boolean colours = player.hasPermission("boxpet.rename.color");
        if (!colours) name = LEGACY.matcher(TAGS.matcher(name).replaceAll("")).replaceAll("");
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("&k") || lower.contains("§k") || lower.contains("<obf") || lower.contains("<click")
                || lower.contains("<hover") || lower.contains("<insert")) return "rename.invalid";
        String plain = Text.plain(name).trim();
        if (plain.length() < s().renameMin || plain.length() > s().renameMax) return "rename.length";
        String plainLower = plain.toLowerCase(Locale.ROOT);
        for (String bad : s().bannedWords) if (plainLower.contains(bad)) return "rename.banned";
        pet.customName = name;
        pet.dirty = true;
        lastRename.put(player.getUniqueId(), now);
        refreshActive(pet);
        return "rename.done";
    }

    private void refreshActive(PetData pet) {
        ActivePet ap = plugin.pets().active(pet.owner);
        if (ap != null && ap.data == pet) plugin.pets().refreshNametag(ap);
    }

    // ------------------------------------------------------------------ evolve (spec 9.3)

    public @Nullable PetType.Stage nextStage(PetData pet) {
        PetType t = plugin.pets().type(pet);
        if (t == null || pet.stage + 1 >= t.stages.size()) return null;
        return t.stages.get(pet.stage + 1);
    }

    /** @return message key */
    public String evolve(Player player, PlayerData pd, PetData pet) {
        PetType.Stage next = nextStage(pet);
        if (next == null) return "evolve.none";
        if (pet.level < next.minLevel()) return "evolve.level";
        if (next.costMoney() > 0) {
            if (plugin.vault().isEmpty()) return "evolve.no-economy";
            if (plugin.vault().get().balance(player) < next.costMoney()) return "evolve.no-money";
        }
        if (next.costGem() > 0) {
            if (plugin.boxCore().isEmpty() || !plugin.boxCore().get().gemsAvailable()) return "evolve.no-gems-plugin";
            if (plugin.boxCore().get().gems(player.getUniqueId()) < next.costGem()) return "evolve.no-gems";
        }
        if (next.costMoney() > 0 && !plugin.vault().get().withdraw(player, next.costMoney())) return "evolve.no-money";
        if (next.costGem() > 0 && !plugin.boxCore().get().withdrawGems(player.getUniqueId(), next.costGem(), "BoxPet evolve " + pet.type)) {
            return "evolve.no-gems";
        }
        pet.stage++;
        pet.dirty = true;
        ActivePet ap = plugin.pets().active(player.getUniqueId());
        if (ap != null && ap.data == pet) {
            plugin.pets().reposition(ap, true);
            plugin.pets().refreshStats(ap);
            plugin.chat().say(ap, "evolve");
        }
        Bukkit.getPluginManager().callEvent(new PetEvolveEvent(player, plugin.pets().view(pet)));
        plugin.messages().title(player, "evolve-title", "pet", plugin.pets().displayName(pet), "stage", String.valueOf(pet.stage + 1));
        plugin.pets().save(pd);
        return "evolve.done";
    }

    // ------------------------------------------------------------------ release (spec 10.4)

    public void release(Player player, PlayerData pd, PetData pet) {
        if (pet.id.equals(pd.activePet)) plugin.pets().dismiss(player, false, "release");
        pet.state = PetData.State.DELETED;
        pet.dirty = true;
        pd.pets.remove(pet.id);
        pd.removed.add(pet);
        pd.dirty = true;
        String refund = s().releaseRefundCandy;
        if (refund != null && !refund.isEmpty()) {
            ItemsConfig.Consumable c = plugin.items().get(refund);
            if (c != null) {
                for (var left : player.getInventory().addItem(plugin.itemFactory().consumable(c, 1)).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }
        plugin.pets().save(pd);
        log(player, "release " + pet.shortId() + " " + pet.type + " " + pet.rarity + " lv" + pet.level);
    }

    // ------------------------------------------------------------------ staff (spec 13)

    /** Moves a pet to another player (both may be offline). */
    public void transfer(CommandSender staff, PetData pet, UUID to) {
        UUID from = pet.owner;
        PlayerData fromData = plugin.pets().data(from);
        Player fromOnline = Bukkit.getPlayer(from);
        if (fromData != null) {
            if (pet.id.equals(fromData.activePet) && fromOnline != null) plugin.pets().dismiss(fromOnline, false, "transfer");
            PetData live = fromData.pets.remove(pet.id);
            if (live != null) pet = live;
            fromData.dirty = true;
            plugin.pets().save(fromData);
        } else {
            plugin.storage().clearActiveIf(from, pet.id);
        }
        if (pet.activeSince > 0) {
            pet.neglectMs += System.currentTimeMillis() - pet.activeSince;
            pet.activeSince = 0;
        }
        pet.owner = to;
        pet.origin = "transfer";
        pet.state = PetData.State.COLLECTION;
        pet.dirty = true;
        PlayerData toData = plugin.pets().data(to);
        if (toData != null) {
            plugin.pets().addPet(toData, pet);
            plugin.pets().save(toData);
        } else {
            plugin.storage().savePets(java.util.List.of(pet.copy()));
        }
        log(staff, "transfer " + pet.shortId() + " " + from + " -> " + to);
    }

    /** Soft delete by staff; {@code restore} brings a deleted pet back to its owner's storage. */
    public void setDeleted(CommandSender staff, PetData pet, boolean deleted) {
        PlayerData pd = plugin.pets().data(pet.owner);
        Player online = Bukkit.getPlayer(pet.owner);
        if (deleted) {
            if (pd != null) {
                PetData live = pd.pets.get(pet.id);
                if (live != null) {
                    if (online != null) release(online, pd, live);
                    else {
                        live.state = PetData.State.DELETED;
                        pd.pets.remove(live.id);
                        pd.removed.add(live);
                        plugin.pets().save(pd);
                    }
                    log(staff, "delete " + pet.shortId());
                    return;
                }
            }
            pet.state = PetData.State.DELETED;
            plugin.storage().clearActiveIf(pet.owner, pet.id);
            plugin.storage().savePets(java.util.List.of(pet.copy()));
        } else {
            pet.state = PetData.State.COLLECTION;
            pet.activeSince = 0;
            pet.dirty = true;
            if (pd != null) {
                plugin.pets().addPet(pd, pet);
                plugin.pets().save(pd);
            } else {
                plugin.storage().savePets(java.util.List.of(pet.copy()));
            }
        }
        log(staff, (deleted ? "delete " : "restore ") + pet.shortId());
    }

    /** {@code field}: level, xp, rarity, bond, fullness. @return message key */
    public String set(CommandSender staff, PetData pet, String field, String value) {
        PetData live = live(pet);
        String before;
        switch (field.toLowerCase(Locale.ROOT)) {
            case "level" -> {
                int v = parseInt(value);
                Rarity r = plugin.pets().rarity(live);
                if (v < 1 || v > r.maxLevel()) return "admin.set-invalid";
                before = String.valueOf(live.level);
                live.level = v;
                live.xp = 0;
            }
            case "xp" -> {
                double v = parseDouble(value);
                if (Double.isNaN(v) || v < 0) return "admin.set-invalid";
                before = String.valueOf(live.xp);
                live.xp = v;
            }
            case "rarity" -> {
                Rarity r = plugin.rarities().get(value);
                if (r == null) return "admin.set-invalid";
                before = live.rarity;
                live.rarity = r.id();
                live.level = Math.min(live.level, r.maxLevel());
            }
            case "bond" -> {
                int v = parseInt(value);
                if (v < 0 || v > 100) return "admin.set-invalid";
                before = String.valueOf(live.bond);
                live.bond = v;
            }
            case "fullness" -> {
                int v = parseInt(value);
                if (v < 0 || v > 100) return "admin.set-invalid";
                before = String.valueOf(live.fullness);
                live.fullness = v;
            }
            default -> {
                return "admin.set-field";
            }
        }
        live.dirty = true;
        ActivePet ap = plugin.pets().active(live.owner);
        if (ap != null && ap.data == live) {
            Rarity r = plugin.rarities().get(live.rarity);
            if (r != null) ap.rarity = r;
            plugin.pets().refreshStats(ap);
            plugin.pets().refreshBonus(ap);
            plugin.pets().refreshNametag(ap);
        }
        PlayerData pd = plugin.pets().data(live.owner);
        if (pd != null) plugin.pets().save(pd);
        else plugin.storage().savePets(java.util.List.of(live.copy()));
        log(staff, "set " + live.shortId() + " " + field + " " + before + " -> " + value);
        return "admin.set-done";
    }

    /** The loaded copy of a pet when its owner is online, else the given one. */
    public PetData live(PetData pet) {
        PlayerData pd = plugin.pets().data(pet.owner);
        if (pd == null) return pet;
        PetData l = pd.pets.get(pet.id);
        return l == null ? pet : l;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /** admin.log: who, what, before -> after (spec 13). */
    public void log(CommandSender who, String what) {
        File f = new File(plugin.getDataFolder(), "admin.log");
        String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + who.getName() + " " + what;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (PetActions.class) {
                try (PrintWriter w = new PrintWriter(new FileWriter(f, StandardCharsets.UTF_8, true))) {
                    w.println(line);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not write admin.log: " + ex.getMessage());
                }
            }
        });
    }
}

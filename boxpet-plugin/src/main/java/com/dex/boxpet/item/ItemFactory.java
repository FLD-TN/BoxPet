package com.dex.boxpet.item;

import com.dex.boxpet.BoxPetPlugin;
import com.dex.boxpet.config.ItemsConfig;
import com.dex.boxpet.config.PetType;
import com.dex.boxpet.config.Rarity;
import com.dex.boxpet.text.Format;
import com.dex.boxpet.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * BoxPet items (spec 9.1, 10.1, 11.3): eggs, random eggs, candies, foods. The data lives in the item's PDC; names and
 * lore are display only and are never read back. Every use re-validates the data against the current config.
 */
public final class ItemFactory {

    public enum Kind {
        EGG, RANDOM_EGG, CANDY, FOOD, BREAKTHROUGH
    }

    /** Data read from an item. {@code id}: pet type (eggs) or consumable id; {@code rarity} only for eggs. */
    public record Info(Kind kind, String id, String rarity) {
    }

    private final BoxPetPlugin plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey idKey;
    private final NamespacedKey rarityKey;
    /** 0.23.0: one id per egg item (eggs never stack): used once, a copy with the same id is refused. */
    private final NamespacedKey serialKey;

    public ItemFactory(BoxPetPlugin plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "kind");
        this.idKey = new NamespacedKey(plugin, "id");
        this.rarityKey = new NamespacedKey(plugin, "rarity");
        this.serialKey = new NamespacedKey(plugin, "serial");
    }

    // ------------------------------------------------------------------ build

    public ItemStack egg(PetType type, int amount) {
        Rarity rarity = plugin.rarities().get(type.rarity);
        if (rarity == null) rarity = plugin.rarities().lowest();
        ItemStack item = base(type.icon, type.iconFallback, 0);
        List<String> lore = plugin.menus().lore("items.egg",
                "pet", type.colouredName(), "rarity", rarity.coloured(), "role", plugin.menus().text("roles." + type.role.id),
                "time", Format.duration(rarity.hatchSeconds()));
        return serial(finish(item, eggName(type), lore, Kind.EGG, type.id, rarity.id(), 1));
    }

    /** 0.23.0: "[Trứng] Rồng Lửa" (menus.yml items.egg-name). */
    public String eggName(PetType type) {
        return plugin.menus().textOr("items.egg-name", "<gray>[<#FFE08A>Trứng<gray>] %pet%", "pet", type.colouredName());
    }

    /** A fresh id, one-item stack. */
    private ItemStack serial(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(serialKey, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());
            meta.setMaxStackSize(1);
            item.setItemMeta(meta);
        }
        item.setAmount(1);
        return item;
    }

    /** The egg's id, or null (not an egg, or an egg made before 0.23.0). */
    public @Nullable String serialOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(serialKey, PersistentDataType.STRING);
    }

    public ItemStack randomEgg(Rarity rarity, int amount) {
        ItemsConfig.EggLook look = plugin.items().randomEgg(rarity.id());
        ItemStack item = base(look.itemsAdder(), look.material(), look.customModelData());
        List<String> lore = new ArrayList<>(plugin.menus().lore("items.random-egg", "rarity", rarity.coloured(),
                "time", Format.duration(rarity.hatchSeconds()), "count", String.valueOf(plugin.incubator().poolSize(rarity))));
        lore.addAll(look.lore());
        return serial(finish(item, "<" + rarity.color() + ">" + look.name() + " <dark_gray>[" + rarity.coloured() + "<dark_gray>]", lore,
                Kind.RANDOM_EGG, "random", rarity.id(), 1));
    }

    public ItemStack consumable(ItemsConfig.Consumable c, int amount) {
        ItemStack item = base(c.itemsAdder(), c.material(), c.customModelData());
        List<String> lore = new ArrayList<>(plugin.menus().lore(c.candy() ? "items.candy" : "items.food",
                "xp", Format.number(Math.round(c.xp())), "fullness", String.valueOf(c.fullness()), "bond", String.valueOf(c.bond())));
        if (!c.lore().isEmpty()) {
            lore.add(0, "");
            lore.addAll(0, c.lore());
        }
        return finish(item, "<" + c.color() + ">" + c.name(), lore, c.candy() ? Kind.CANDY : Kind.FOOD, c.id(), "", amount);
    }

    /** Breakthrough stone (0.7.0): {@code chance} 1-100 % success, kept in the id. */
    public ItemStack breakthrough(int chance, int amount) {
        chance = Math.max(1, Math.min(100, chance));
        ItemsConfig.EggLook look = plugin.items().breakthrough;
        ItemStack item = base(look.itemsAdder(), look.material(), look.customModelData());
        List<String> lore = new ArrayList<>(plugin.menus().lore("items.breakthrough", "chance", String.valueOf(chance)));
        lore.addAll(look.lore());
        return finish(item, look.name() + " <dark_gray>[<#7CFC9A>" + chance + "%<dark_gray>]", lore, Kind.BREAKTHROUGH,
                String.valueOf(chance), "", amount);
    }

    /** Success chance of a breakthrough stone, 0 when the item is not one. */
    public int breakChance(@Nullable Info info) {
        if (info == null || info.kind() != Kind.BREAKTHROUGH) return 0;
        try {
            return Math.max(1, Math.min(100, Integer.parseInt(info.id())));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private ItemStack base(String itemsAdder, Material fallback, int customModelData) {
        ItemStack ia = plugin.itemsAdder().map(h -> h.item(itemsAdder)).orElse(null);
        ItemStack item = ia != null ? ia : new ItemStack(fallback);
        if (ia == null && customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private ItemStack finish(ItemStack item, String name, List<String> lore, Kind kind, String id, String rarity, int amount) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            meta.lore(lore.stream().map(Text::item).toList());
            com.dex.boxpet.gui.Menu.hideVanillaTooltip(meta);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(kindKey, PersistentDataType.STRING, kind.name());
            pdc.set(idKey, PersistentDataType.STRING, id);
            pdc.set(rarityKey, PersistentDataType.STRING, rarity);
            meta.setMaxStackSize(64);
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }

    // ------------------------------------------------------------------ read

    public @Nullable Info read(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String kind = pdc.get(kindKey, PersistentDataType.STRING);
        if (kind == null) return null;
        try {
            return new Info(Kind.valueOf(kind), pdc.getOrDefault(idKey, PersistentDataType.STRING, ""),
                    pdc.getOrDefault(rarityKey, PersistentDataType.STRING, ""));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isBoxPetItem(@Nullable ItemStack item) {
        return read(item) != null;
    }

    /** A consumable that still exists in items.yml, or null. */
    public @Nullable ItemsConfig.Consumable consumable(@Nullable Info info) {
        if (info == null || (info.kind() != Kind.CANDY && info.kind() != Kind.FOOD)) return null;
        ItemsConfig.Consumable c = plugin.items().get(info.id());
        if (c == null || c.candy() != (info.kind() == Kind.CANDY)) return null;
        return c;
    }

    /** Validates an egg against the config: known pet allowing that rarity, or a random egg of a known rarity. */
    public boolean validEgg(Info info) {
        Rarity r = plugin.rarities().get(info.rarity());
        if (r == null) return false;
        if (info.kind() == Kind.RANDOM_EGG) return plugin.incubator().poolSize(r) > 0;
        if (info.kind() != Kind.EGG) return false;
        return plugin.types().get(info.id()) != null;
    }
}

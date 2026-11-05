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
        EGG, RANDOM_EGG, CANDY, FOOD
    }

    /** Data read from an item. {@code id}: pet type (eggs) or consumable id; {@code rarity} only for eggs. */
    public record Info(Kind kind, String id, String rarity) {
    }

    private final BoxPetPlugin plugin;
    private final NamespacedKey kindKey;
    private final NamespacedKey idKey;
    private final NamespacedKey rarityKey;

    public ItemFactory(BoxPetPlugin plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "kind");
        this.idKey = new NamespacedKey(plugin, "id");
        this.rarityKey = new NamespacedKey(plugin, "rarity");
    }

    // ------------------------------------------------------------------ build

    public ItemStack egg(PetType type, Rarity rarity, int amount) {
        ItemsConfig.EggLook look = plugin.items().fixedEgg;
        ItemStack item = base(type.icon, type.iconFallback, 0);
        String name = look.name().replace("%pet%", type.name);
        List<String> lore = plugin.menus().lore("items.egg", Map.of("description", type.description),
                "pet", type.name, "rarity", rarity.coloured(), "role", plugin.menus().text("roles." + type.role.id),
                "time", Format.duration(rarity.hatchSeconds()));
        return finish(item, "<" + rarity.color() + ">" + name + " <dark_gray>[" + rarity.coloured() + "<dark_gray>]", lore,
                Kind.EGG, type.id, rarity.id(), amount);
    }

    public ItemStack randomEgg(Rarity rarity, int amount) {
        ItemsConfig.EggLook look = plugin.items().randomEgg(rarity.id());
        ItemStack item = base(look.itemsAdder(), look.material(), look.customModelData());
        List<String> lore = new ArrayList<>(plugin.menus().lore("items.random-egg", "rarity", rarity.coloured(),
                "time", Format.duration(rarity.hatchSeconds()), "count", String.valueOf(plugin.incubator().poolSize(rarity))));
        lore.addAll(look.lore());
        return finish(item, "<" + rarity.color() + ">" + look.name() + " <dark_gray>[" + rarity.coloured() + "<dark_gray>]", lore,
                Kind.RANDOM_EGG, "random", rarity.id(), amount);
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
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
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
        PetType t = plugin.types().get(info.id());
        return t != null && t.allows(r.id());
    }
}

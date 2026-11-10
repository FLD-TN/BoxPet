package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

/** items.yml: candies, foods, egg looks (spec 9.1, 10.1, 11.3). */
public final class ItemsConfig {

    /** A candy (XP + fullness) or a food (fullness + bond). */
    public record Consumable(String id, boolean candy, String name, String color, Material material, String itemsAdder,
                             int customModelData, double xp, int fullness, int bond, List<String> lore) {
    }

    /** Look of an egg item. */
    public record EggLook(String name, Material material, String itemsAdder, int customModelData, List<String> lore) {
    }

    private final Map<String, Consumable> consumables = new LinkedHashMap<>();
    private final Map<String, EggLook> randomEggs = new HashMap<>();
    public final EggLook fixedEgg;
    public final EggLook randomEggDefault;
    /** Breakthrough stone look (0.7.0). */
    public final EggLook breakthrough;
    public final Map<Material, Integer> vanillaFood = new HashMap<>();

    /** 0.27.0: config.yml xp.food-multiplier, applied to the EXP of every candy. */
    private final double xpMultiplier;

    public ItemsConfig(YamlConfiguration y) {
        this(y, 1.0);
    }

    public ItemsConfig(YamlConfiguration y, double xpMultiplier) {
        this.xpMultiplier = xpMultiplier;
        readConsumables(y.getConfigurationSection("candies"), true);
        readConsumables(y.getConfigurationSection("foods"), false);
        if (consumables.values().stream().noneMatch(Consumable::candy)) {
            add(new Consumable("candy_small", true, "Kẹo Pet Nhỏ", "#BFC7D5", Material.SUGAR, "", 0, 100, 5, 0, List.of()));
            add(new Consumable("candy_medium", true, "Kẹo Pet Vừa", "#7FDBFF", Material.AMETHYST_SHARD, "", 0, 500, 10, 0, List.of()));
            add(new Consumable("candy_large", true, "Kẹo Pet Lớn", "#C3B1E1", Material.PRISMARINE_CRYSTALS, "", 0, 2000, 20, 0, List.of()));
            add(new Consumable("candy_super", true, "Kẹo Pet Siêu Cấp", "#FFD166", Material.NETHER_STAR, "", 0, 10000, 35, 0, List.of()));
        }
        if (consumables.values().stream().allMatch(Consumable::candy)) {
            add(new Consumable("food_small", false, "Thức Ăn Pet", "#BFC7D5", Material.COOKIE, "", 0, 0, 10, 1, List.of()));
            add(new Consumable("food_medium", false, "Bữa Ăn Pet", "#7CFC9A", Material.PUMPKIN_PIE, "", 0, 0, 25, 1, List.of()));
            add(new Consumable("food_large", false, "Đại Tiệc Pet", "#FFD166", Material.CAKE, "", 0, 0, 40, 2, List.of()));
        }
        fixedEgg = look(y.getConfigurationSection("eggs.fixed"), "Trứng %pet%", Material.TURTLE_EGG);
        randomEggDefault = look(y.getConfigurationSection("eggs.random"), "Trứng Ngẫu Nhiên", Material.SNIFFER_EGG);
        breakthrough = look(y.getConfigurationSection("breakthrough"), "<#FF9F5A>Đá Đột Phá", Material.ECHO_SHARD);
        ConfigurationSection per = y.getConfigurationSection("eggs.random-by-rarity");
        if (per != null) {
            for (String r : per.getKeys(false)) {
                randomEggs.put(r.toLowerCase(Locale.ROOT), look(per.getConfigurationSection(r), randomEggDefault.name(),
                        randomEggDefault.material()));
            }
        }
        ConfigurationSection vf = y.getConfigurationSection("vanilla-food");
        if (vf != null) {
            for (String k : vf.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null) vanillaFood.put(m, Math.max(1, vf.getInt(k)));
            }
        }
    }

    private void readConsumables(@Nullable ConfigurationSection root, boolean candy) {
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Material m = Material.matchMaterial(s.getString("material", candy ? "SUGAR" : "COOKIE"));
            if (m == null || !m.isItem()) m = candy ? Material.SUGAR : Material.COOKIE;
            add(new Consumable(key.toLowerCase(Locale.ROOT), candy, s.getString("name", key), s.getString("color", "#FFFFFF"),
                    m, s.getString("itemsadder", ""), s.getInt("custom-model-data", 0), Math.max(0, s.getDouble("xp", 0)) * xpMultiplier,
                    Math.max(0, Math.min(100, s.getInt("fullness", candy ? 5 : 10))), Math.max(0, s.getInt("bond", candy ? 0 : 1)),
                    s.getStringList("lore")));
        }
    }

    private void add(Consumable c) {
        consumables.put(c.id(), c);
    }

    private static EggLook look(@Nullable ConfigurationSection s, String name, Material material) {
        if (s == null) return new EggLook(name, material, "", 0, List.of());
        Material m = Material.matchMaterial(s.getString("material", material.name()));
        return new EggLook(s.getString("name", name), m == null || !m.isItem() ? material : m, s.getString("itemsadder", ""),
                s.getInt("custom-model-data", 0), s.getStringList("lore"));
    }

    public @Nullable Consumable get(String id) {
        return id == null ? null : consumables.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Consumable> all() {
        return consumables.values();
    }

    public List<String> ids(boolean candy) {
        List<String> out = new ArrayList<>();
        for (Consumable c : consumables.values()) if (c.candy() == candy) out.add(c.id());
        return out;
    }

    public EggLook randomEgg(String rarity) {
        EggLook l = randomEggs.get(rarity);
        return l == null ? randomEggDefault : l;
    }
}

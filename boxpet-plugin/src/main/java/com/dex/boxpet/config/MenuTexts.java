package com.dex.boxpet.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * menus.yml: titles, button names and lore templates of the player menus. Missing keys use the jar copy. A lore line
 * that is exactly {@code %name%} where {@code name} is a key of {@code blocks} is replaced by several lines.
 */
public final class MenuTexts {

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yml = new YamlConfiguration();

    public MenuTexts(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        yml = ConfigFiles.load(plugin, "menus.yml");
    }

    public String title(String menu, String... placeholders) {
        return Messages.apply(yml.getString(menu + ".title", menu), placeholders);
    }

    public String text(String path, String... placeholders) {
        return Messages.apply(yml.getString(path, path), placeholders);
    }

    public String name(String path, String... placeholders) {
        return text(path + ".name", placeholders);
    }

    public List<String> lore(String path, Map<String, List<String>> blocks, String... placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : yml.getStringList(path + ".lore")) {
            String key = line.trim();
            if (key.length() > 2 && key.startsWith("%") && key.endsWith("%")) {
                List<String> block = blocks.get(key.substring(1, key.length() - 1));
                if (block != null) {
                    for (String b : block) out.add(Messages.apply(b, placeholders));
                    continue;
                }
            }
            out.add(Messages.apply(line, placeholders));
        }
        // drop empty lines left by empty blocks at the start, end, or doubled
        List<String> clean = new ArrayList<>();
        for (String s : out) {
            if (s.isEmpty() && (clean.isEmpty() || clean.get(clean.size() - 1).isEmpty())) continue;
            clean.add(s);
        }
        while (!clean.isEmpty() && clean.get(clean.size() - 1).isEmpty()) clean.remove(clean.size() - 1);
        return clean;
    }

    public List<String> lore(String path, String... placeholders) {
        return lore(path, Map.of(), placeholders);
    }

    public List<String> list(String path) {
        return yml.getStringList(path);
    }

    public int slot(String path, int def) {
        return yml.getInt(path + ".slot", def);
    }

    public String material(String path, String def) {
        return yml.getString(path + ".material", def);
    }
}

package com.dex.boxpet.config;

import com.dex.boxpet.text.Text;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * messages.yml. Missing keys fall back to the copy inside the jar. Placeholders are pairs {@code "pet", name} that
 * replace {@code %pet%}. An empty string disables a message.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yml = new YamlConfiguration();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        yml = ConfigFiles.load(plugin, "messages.yml");
    }

    public String raw(String path, String... placeholders) {
        String s = yml.getString(path);
        return s == null ? path : apply(s, placeholders);
    }

    public List<String> rawList(String path, String... placeholders) {
        List<String> out = new ArrayList<>();
        for (String s : yml.getStringList(path)) out.add(apply(s, placeholders));
        return out;
    }

    public String prefix() {
        return yml.getString("prefix", "");
    }

    public void send(CommandSender to, String path, String... placeholders) {
        String s = raw(path, placeholders);
        if (s.isEmpty()) return;
        to.sendMessage(Text.parse(prefix() + s, to instanceof Player p ? p : null));
    }

    /** A message without the prefix (pet chat lines, list rows). */
    public void sendPlain(CommandSender to, String raw) {
        if (raw == null || raw.isEmpty()) return;
        to.sendMessage(Text.parse(raw, to instanceof Player p ? p : null));
    }

    public void title(Player player, String path, String... placeholders) {
        String title = raw(path + ".title", placeholders);
        String sub = raw(path + ".subtitle", placeholders);
        if (title.equals(path + ".title")) title = "";
        if (sub.equals(path + ".subtitle")) sub = "";
        if (title.isEmpty() && sub.isEmpty()) return;
        player.showTitle(Title.title(Text.parse(title, player), Text.parse(sub, player),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(500))));
    }

    public void actionBar(Player player, String path, String... placeholders) {
        String s = raw(path, placeholders);
        if (!s.isEmpty()) player.sendActionBar(Text.parse(s, player));
    }

    public Component component(String path, String... placeholders) {
        return Text.parse(raw(path, placeholders));
    }

    public static String apply(String s, String... placeholders) {
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            s = s.replace("%" + placeholders[i] + "%", placeholders[i + 1] == null ? "" : placeholders[i + 1]);
        }
        return s;
    }
}

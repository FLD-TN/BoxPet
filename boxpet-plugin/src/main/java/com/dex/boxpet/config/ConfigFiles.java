package com.dex.boxpet.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Copies default files out of the jar only when they are missing (jar-only upgrades: the owner's edited files are
 * never overwritten). Loads YAML files with the jar copy as defaults, so new keys work without touching the file.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    public static void ensure(JavaPlugin plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists() && plugin.getResource(name) != null) plugin.saveResource(name, false);
    }

    /** Loads a file from the data folder, with the jar's copy as defaults. */
    public static YamlConfiguration load(JavaPlugin plugin, String name) {
        ensure(plugin, name);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
        YamlConfiguration def = jarDefaults(plugin, name);
        if (def != null) yml.setDefaults(def);
        return yml;
    }

    public static YamlConfiguration jarDefaults(JavaPlugin plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read default " + name + ": " + ex.getMessage());
            return null;
        }
    }

    /**
     * Copies every default file of a jar folder ({@code pets/}) when the folder does not exist yet. Once the folder
     * exists the owner manages it: deleted pets are not brought back.
     */
    public static void ensureFolder(JavaPlugin plugin, String folder) {
        File dir = new File(plugin.getDataFolder(), folder);
        if (dir.isDirectory()) return;
        for (String entry : jarEntries(plugin, folder + "/")) {
            if (entry.endsWith(".yml")) plugin.saveResource(entry, false);
        }
    }

    private static List<String> jarEntries(JavaPlugin plugin, String prefix) {
        List<String> out = new ArrayList<>();
        try {
            URL url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            try (JarFile jar = new JarFile(new File(url.toURI()))) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (!e.isDirectory() && e.getName().startsWith(prefix)) out.add(e.getName());
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException ex) {
            plugin.getLogger().warning("Could not list default files of " + prefix + ": " + ex.getMessage());
        }
        return out;
    }
}

package com.dex.boxpet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every default file parses; key 0.5.0 contents are there. */
class ResourcesTest {

    private static YamlConfiguration load(String name) throws Exception {
        var in = Objects.requireNonNull(ResourcesTest.class.getClassLoader().getResourceAsStream(name), name);
        YamlConfiguration y = new YamlConfiguration();
        y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return y;
    }

    @Test
    void allFilesParse() throws Exception {
        for (String f : new String[]{"config.yml", "messages.yml", "menus.yml", "rarities.yml", "abilities.yml", "levels.yml",
                "xp-sources.yml", "items.yml", "dialogues.yml", "collection.yml"}) {
            assertFalse(load(f).getKeys(false).isEmpty(), f);
        }
    }

    @Test
    void statIconsAreRealGlyphs() throws Exception {
        String row = load("menus.yml").getString("stat-display.ATTACK_DAMAGE");
        assertNotNull(row);
        assertTrue(row.indexOf('\uE906') >= 0, "icon glyph decoded");
    }

    @Test
    void everyPetHasOneRarityAndAColour() throws Exception {
        File dir = new File(Objects.requireNonNull(ResourcesTest.class.getClassLoader().getResource("pets")).toURI());
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        assertNotNull(files);
        assertEquals(52, files.length);
        YamlConfiguration abilities = load("abilities.yml");
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            assertNotNull(y.getString("rarity"), f.getName());
            assertNotNull(y.getString("color"), f.getName());
            // common / rare: one colour; epic and up: a gradient
            boolean gradient = y.getString("color").contains(":");
            boolean high = y.getString("rarity").equals("epic") || y.getString("rarity").equals("legendary");
            assertEquals(high, gradient, f.getName() + " colour style");
            assertTrue(abilities.contains("abilities." + y.getString("ability")), f.getName() + " ability");
        }
    }

    @Test
    void dialoguesUseChuNhan() throws Exception {
        YamlConfiguration d = load("dialogues.yml");
        assertTrue(d.getStringList("default.idle").size() >= 6);
        // every "chủ" in the pet lines is "chủ nhân"
        for (String key : d.getKeys(true)) {
            for (String line : d.getStringList(key)) {
                String l = line.toLowerCase();
                int i = l.indexOf("chủ");
                while (i >= 0) {
                    assertTrue(l.startsWith("chủ nhân", i), key + ": " + line);
                    i = l.indexOf("chủ", i + 1);
                }
            }
        }
    }
}

package com.dex.boxpet.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Lore in the BoxCore/BoxClans standard (spec 2): description line, sections, "• Nhãn: giá trị" rows and the
 * action lines last.
 */
public final class Lore {

    public static final String DESC = "&#BFC7D5";
    public static final String SECTION = "&#FFD166";
    public static final String ACTION = "&#7CFC9A";
    public static final String DANGER = "&#FF8A8A";
    public static final String SECONDARY = "&#7FDBFF";
    private static final int WRAP = 30;

    private final List<String> lines = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();

    public static Lore of() {
        return new Lore();
    }

    /** Description text, wrapped at about 30 characters. */
    public Lore desc(String text) {
        for (String part : wrap(text)) lines.add(DESC + part);
        return this;
    }

    /** A coloured note (warning, hint), wrapped like {@link #desc}. */
    public Lore note(String color, String text) {
        for (String part : wrap(text)) lines.add(color + part);
        return this;
    }

    public Lore section(String title) {
        if (!lines.isEmpty()) lines.add("");
        lines.add(SECTION + title);
        return this;
    }

    public Lore row(String label, String value) {
        lines.add("&7• " + label + ": &f" + value);
        return this;
    }

    public Lore line(String raw) {
        lines.add(raw);
        return this;
    }

    public Lore lines(List<String> raw) {
        lines.addAll(raw);
        return this;
    }

    public Lore action(String text) {
        actions.add("&8[" + ACTION + text + "&8]");
        return this;
    }

    public Lore danger(String text) {
        actions.add("&8[" + DANGER + text + "&8]");
        return this;
    }

    public Lore secondary(String text) {
        actions.add("&8[" + SECONDARY + text + "&8]");
        return this;
    }

    public List<String> build() {
        List<String> out = new ArrayList<>(lines);
        if (!actions.isEmpty()) {
            if (!out.isEmpty()) out.add("");
            out.addAll(actions);
        }
        return out;
    }

    public static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > WRAP) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}

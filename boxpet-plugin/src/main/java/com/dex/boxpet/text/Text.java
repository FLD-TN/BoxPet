package com.dex.boxpet.text;

import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The one colour parser of BoxPet (spec 2): legacy {@code &a}, hex {@code &#RRGGBB}, MiniMessage tags
 * ({@code <gradient:...>}, {@code <rainbow>}, {@code <#hex>}...) and PlaceholderAPI, all in the same string.
 * Legacy codes are translated to MiniMessage first; a legacy colour resets the formatting like it does in vanilla.
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    /** Palette tags of the BoxCore standard ({@code <text>}, {@code <muted>}, ...), set from config.yml style.palette. */
    private static volatile java.util.Map<String, String> palette = java.util.Map.of(
            "text", "#FFFFFF", "muted", "#C8C8C8", "accent", "#FFD166", "success", "#7CFC9A", "error", "#FF4D4D");

    public static void setPalette(java.util.Map<String, String> colours) {
        if (colours != null && !colours.isEmpty()) palette = java.util.Map.copyOf(colours);
    }

    /** PlaceholderAPI, set when it is installed. Player may be null. */
    private static volatile BiFunction<Player, String, String> placeholders = (p, s) -> s;

    private Text() {
    }

    public static void setPlaceholderResolver(@Nullable BiFunction<Player, String, String> resolver) {
        placeholders = resolver == null ? (p, s) -> s : resolver;
    }

    public static Component parse(String raw) {
        return parse(raw, null);
    }

    public static Component parse(String raw, @Nullable Player player) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String s = raw.indexOf('%') >= 0 ? placeholders.apply(player, raw) : raw;
        return MINI.deserialize(toMini(applyPalette(s)));
    }

    /** An item name or lore line: no default italics. */
    public static Component item(String raw) {
        return parse(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Section-sign string (with hex) for plugins that only take legacy text: holograms, MythicMobs names. */
    public static String legacy(String raw) {
        return SECTION.serialize(parse(raw));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plain(String raw) {
        return plain(parse(raw));
    }

    /** {@code <muted>} -> {@code <#C8C8C8>}; closing tags {@code </muted>} -> {@code </#C8C8C8>}. */
    static String applyPalette(String s) {
        if (s.indexOf('<') < 0) return s;
        for (java.util.Map.Entry<String, String> e : palette.entrySet()) {
            s = s.replace("<" + e.getKey() + ">", "<" + e.getValue() + ">")
                    .replace("</" + e.getKey() + ">", "</" + e.getValue() + ">");
        }
        return s;
    }

    /** "&&" shows a literal "&" (to write "&&a" in help texts); "\<" is MiniMessage's own escape. */
    static String toMini(String s) {
        s = s.replace("&&", "\u0000");
        Matcher hex = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (hex.find()) hex.appendReplacement(sb, Matcher.quoteReplacement("<reset><#" + hex.group(1) + ">"));
        hex.appendTail(sb);
        Matcher legacy = LEGACY.matcher(sb.toString());
        StringBuilder out = new StringBuilder();
        while (legacy.find()) legacy.appendReplacement(out, Matcher.quoteReplacement(tag(legacy.group(1).toLowerCase())));
        legacy.appendTail(out);
        return out.toString().replace("\u0000", "&");
    }

    private static String tag(String code) {
        return switch (code) {
            case "0" -> "<reset><black>";
            case "1" -> "<reset><dark_blue>";
            case "2" -> "<reset><dark_green>";
            case "3" -> "<reset><dark_aqua>";
            case "4" -> "<reset><dark_red>";
            case "5" -> "<reset><dark_purple>";
            case "6" -> "<reset><gold>";
            case "7" -> "<reset><gray>";
            case "8" -> "<reset><dark_gray>";
            case "9" -> "<reset><blue>";
            case "a" -> "<reset><green>";
            case "b" -> "<reset><aqua>";
            case "c" -> "<reset><red>";
            case "d" -> "<reset><light_purple>";
            case "e" -> "<reset><yellow>";
            case "f" -> "<reset><white>";
            case "k" -> "<obfuscated>";
            case "l" -> "<bold>";
            case "m" -> "<strikethrough>";
            case "n" -> "<underlined>";
            case "o" -> "<italic>";
            default -> "<reset>";
        };
    }
}

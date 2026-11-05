package com.dex.boxpet.config;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "1h30m", "45s", "2d", "90" (seconds). */
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private Durations() {
    }

    public static long seconds(String raw, long fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (DIGITS.matcher(s).matches()) return Long.parseLong(s);
        Matcher m = PART.matcher(s);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86400;
                case "h" -> n * 3600;
                case "m" -> n * 60;
                default -> n;
            };
        }
        return any ? total : fallback;
    }
}

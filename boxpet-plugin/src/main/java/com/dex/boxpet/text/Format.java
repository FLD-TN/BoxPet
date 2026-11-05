package com.dex.boxpet.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Vietnamese number format: 1.250 / 1.000.000, decimals with a comma: 12,5. */
public final class Format {

    private static final ThreadLocal<DecimalFormat> INT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0", symbols()));
    private static final ThreadLocal<DecimalFormat> DEC = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##", symbols()));

    private Format() {
    }

    private static DecimalFormatSymbols symbols() {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return sym;
    }

    public static String number(long n) {
        return INT.get().format(n);
    }

    public static String decimal(double d) {
        return DEC.get().format(d);
    }

    /**
     * Text bar for menus (spec 2): {@code <#7CFC9A>■■■■■■<dark_gray>■■■■ <text>62%}. {@code value} / {@code max}
     * clamped to 0..1; the percentage is rounded down.
     */
    public static String bar(double value, double max, int cells, String fill, String empty) {
        double ratio = max <= 0 ? 1 : Math.max(0, Math.min(1, value / max));
        int full = (int) Math.round(ratio * cells);
        StringBuilder sb = new StringBuilder(fill);
        for (int i = 0; i < full; i++) sb.append('■');
        sb.append(empty);
        for (int i = full; i < cells; i++) sb.append('■');
        return sb.toString();
    }

    /** "12:30" (m:ss) or "1:05:09" (h:mm:ss) for countdowns. */
    public static String clock(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** "5 phút 30 giây" style. */
    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (d > 0) return d + " ngày " + h + " giờ";
        if (h > 0) return h + " giờ " + m + " phút";
        if (m > 0) return s == 0 ? m + " phút" : m + " phút " + s + " giây";
        return s + " giây";
    }

    /**
     * Parses an admin's number: digits with optional dot thousands or a decimal comma/dot ("1.000", "2,5", "0.25").
     * Returns NaN when it is not a number.
     */
    public static double parse(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.strip().replace(" ", "");
        if (s.isEmpty()) return Double.NaN;
        // "1.000.000" or "1.000" with exactly 3 digits after each dot = thousands
        if (s.matches("\\d{1,3}(\\.\\d{3})+")) s = s.replace(".", "");
        s = s.replace(',', '.');
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}

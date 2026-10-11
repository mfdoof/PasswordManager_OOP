package com.doof.passwordmanager.ui.tui.widget;

/**
 * Rough password strength from length and character classes (log2(pool) bits per character).
 * It overestimates human-chosen passwords (words, patterns), so it is guidance only; the
 * enforced rule is {@link com.doof.passwordmanager.util.PasswordPolicy}.
 */
public final class StrengthMeter {

    public static final int BAR_CELLS = 10;

    public enum Level {
        NONE("none"), WEAK("weak"), FAIR("fair"), GOOD("good"), STRONG("strong"), VERY_STRONG("very strong");

        public final String label;

        Level(String label) {
            this.label = label;
        }
    }

    private StrengthMeter() {}

    public static double bits(char[] pw) {
        if (pw == null || pw.length == 0) return 0;
        boolean lower = false, upper = false, digit = false, symbol = false, other = false;
        for (char c : pw) {
            if (c >= 'a' && c <= 'z') lower = true;
            else if (c >= 'A' && c <= 'Z') upper = true;
            else if (c >= '0' && c <= '9') digit = true;
            else if (c > 0x20 && c < 0x7f) symbol = true;
            else other = true;  // space, non-ASCII
        }
        int pool = (lower ? 26 : 0) + (upper ? 26 : 0) + (digit ? 10 : 0) + (symbol ? 33 : 0) + (other ? 32 : 0);
        return pw.length * (Math.log(pool) / Math.log(2));
    }

    public static Level level(char[] pw) {
        double b = bits(pw);
        if (b == 0) return Level.NONE;
        if (b < 40) return Level.WEAK;
        if (b < 60) return Level.FAIR;
        if (b < 70) return Level.GOOD;
        if (b < 100) return Level.STRONG;
        return Level.VERY_STRONG;
    }

    /** Filled cells out of {@link #BAR_CELLS}: one per 10 bits, at least 1 for any input. */
    public static int cells(char[] pw) {
        double b = bits(pw);
        if (b == 0) return 0;
        return (int) Math.max(1, Math.min(BAR_CELLS, Math.round(b / 10)));
    }

    /** {@code [########--]} */
    public static String bar(char[] pw) {
        int n = cells(pw);
        return "[" + "#".repeat(n) + "-".repeat(BAR_CELLS - n) + "]";
    }
}

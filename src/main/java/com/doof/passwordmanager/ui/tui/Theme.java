package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.TextColor;

import java.util.Locale;
import java.util.Map;

/**
 * Colors and border characters (docs/TUI_DESIGN.md §1-2). Color is never the only carrier of
 * meaning, so a monochrome theme loses nothing but looks.
 */
public final class Theme {

    public enum ColorMode {
        /** 24-bit RGB; used when COLORTERM says the terminal supports it. */
        TRUECOLOR,
        /** Nearest xterm-256 colors; safe default for most terminals. */
        INDEXED_256,
        /** Terminal default colors only (NO_COLOR, --no-color, TERM=dumb). */
        NONE
    }

    // Palette: background, text, primary, dim, success, error.
    static final int[] BACKGROUND = {0x0d, 0x0b, 0x14};
    static final int[] TEXT = {0xe0, 0xd7, 0xf5};
    static final int[] PRIMARY = {0xa2, 0x77, 0xff};
    static final int[] DIM = {0x6b, 0x5b, 0x95};
    static final int[] SUCCESS = {0x61, 0xff, 0xca};
    static final int[] ERROR = {0xff, 0x61, 0x88};

    private final ColorMode colorMode;
    private final boolean unicode;

    public final TextColor background;
    public final TextColor text;
    public final TextColor primary;
    public final TextColor dim;
    public final TextColor success;
    public final TextColor error;

    public final char horizontal;
    public final char vertical;
    public final char topLeft;
    public final char topRight;
    public final char bottomLeft;
    public final char bottomRight;
    public final char teeLeft;   // left edge of a horizontal divider
    public final char teeRight;  // right edge of a horizontal divider

    Theme(ColorMode colorMode, boolean unicode) {
        this.colorMode = colorMode;
        this.unicode = unicode;
        background = color(BACKGROUND, TextColor.ANSI.DEFAULT);
        text = color(TEXT, TextColor.ANSI.DEFAULT);
        primary = color(PRIMARY, TextColor.ANSI.DEFAULT);
        dim = color(DIM, TextColor.ANSI.DEFAULT);
        success = color(SUCCESS, TextColor.ANSI.DEFAULT);
        error = color(ERROR, TextColor.ANSI.DEFAULT);

        horizontal = unicode ? '─' : '-';
        vertical = unicode ? '│' : '|';
        topLeft = unicode ? '┌' : '+';
        topRight = unicode ? '┐' : '+';
        bottomLeft = unicode ? '└' : '+';
        bottomRight = unicode ? '┘' : '+';
        teeLeft = unicode ? '├' : '+';
        teeRight = unicode ? '┤' : '+';
    }

    /** Picks color mode and border style from flags and the environment. */
    public static Theme detect(TuiOptions options, Map<String, String> env) {
        return new Theme(colorMode(options.noColor(), env), !options.ascii() && unicodeLocale(env));
    }

    static ColorMode colorMode(boolean noColorFlag, Map<String, String> env) {
        String noColor = env.get("NO_COLOR");
        if (noColorFlag || (noColor != null && !noColor.isEmpty()) || "dumb".equals(env.get("TERM"))) {
            return ColorMode.NONE;
        }
        String colorTerm = env.getOrDefault("COLORTERM", "").toLowerCase(Locale.ROOT);
        if (colorTerm.equals("truecolor") || colorTerm.equals("24bit")) {
            return ColorMode.TRUECOLOR;
        }
        return ColorMode.INDEXED_256;
    }

    /** True if the effective locale (LC_ALL, then LC_CTYPE, then LANG) is UTF-8. */
    static boolean unicodeLocale(Map<String, String> env) {
        for (String var : new String[]{"LC_ALL", "LC_CTYPE", "LANG"}) {
            String v = env.get(var);
            if (v != null && !v.isEmpty()) {
                String lower = v.toLowerCase(Locale.ROOT);
                return lower.contains("utf-8") || lower.contains("utf8");
            }
        }
        return false;
    }

    public ColorMode colorMode() { return colorMode; }
    public boolean unicode() { return unicode; }

    private TextColor color(int[] rgb, TextColor fallback) {
        return switch (colorMode) {
            case TRUECOLOR -> new TextColor.RGB(rgb[0], rgb[1], rgb[2]);
            case INDEXED_256 -> TextColor.Indexed.fromRGB(rgb[0], rgb[1], rgb[2]);
            case NONE -> fallback;
        };
    }
}

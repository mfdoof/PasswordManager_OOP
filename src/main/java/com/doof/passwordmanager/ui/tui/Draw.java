package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.graphics.TextGraphics;

/** Small drawing helpers shared by screens. */
public final class Draw {

    private Draw() {}

    /** Box outline in the current foreground color; {@code title} (may be null) is drawn after the top-left corner. */
    public static void box(TextGraphics g, Theme t, int col, int row, int w, int h, String title) {
        for (int x = col + 1; x < col + w - 1; x++) {
            g.setCharacter(x, row, t.horizontal);
            g.setCharacter(x, row + h - 1, t.horizontal);
        }
        for (int y = row + 1; y < row + h - 1; y++) {
            g.setCharacter(col, y, t.vertical);
            g.setCharacter(col + w - 1, y, t.vertical);
        }
        g.setCharacter(col, row, t.topLeft);
        g.setCharacter(col + w - 1, row, t.topRight);
        g.setCharacter(col, row + h - 1, t.bottomLeft);
        g.setCharacter(col + w - 1, row + h - 1, t.bottomRight);
        if (title != null) {
            g.putString(col + 2, row, " " + title + " ");
        }
    }

    /** Horizontal divider across a box of width {@code w} starting at {@code col}. */
    public static void divider(TextGraphics g, Theme t, int col, int row, int w) {
        for (int x = col + 1; x < col + w - 1; x++) {
            g.setCharacter(x, row, t.horizontal);
        }
        g.setCharacter(col, row, t.teeLeft);
        g.setCharacter(col + w - 1, row, t.teeRight);
    }

    public static void centered(TextGraphics g, int row, int width, String text) {
        g.putString(Math.max(0, (width - text.length()) / 2), row, text);
    }

    /** Cuts {@code s} to {@code max} chars, ending with "..." if cut. */
    public static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        if (max <= 3) return s.substring(0, Math.max(0, max));
        return s.substring(0, max - 3) + "...";
    }

    /** Cuts {@code s} to {@code max} chars from the left, starting with "..." if cut (for paths). */
    public static String truncateLeft(String s, int max) {
        if (s.length() <= max) return s;
        if (max <= 3) return s.substring(s.length() - Math.max(0, max));
        return "..." + s.substring(s.length() - (max - 3));
    }

    /** Seconds to show for a remaining duration: rounded up, at least 1. */
    public static long secondsLeft(long remainingMillis) {
        return Math.max(1, (remainingMillis + 999) / 1000);
    }
}

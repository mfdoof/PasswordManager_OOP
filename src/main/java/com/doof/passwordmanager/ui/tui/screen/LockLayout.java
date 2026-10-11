package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;

/** Shared layout of the Unlock and Create screens: frame, logo, vault path and open warnings. */
final class LockLayout {

    static final String[] LOGO = {
            "######   #####   #####  #######",
            "##   ## ##   ## ##   ## ##",
            "##   ## ##   ## ##   ## #####",
            "##   ## ##   ## ##   ## ##",
            "######   #####   #####  ##",
    };
    static final String SPINNER = "|/-\\";

    private LockLayout() {}

    /**
     * Draws frame, logo and footer; returns the row where a block of {@code blockRows} rows
     * (panel + message) should start so logo + block are vertically centered.
     */
    static int drawChrome(TextGraphics g, TerminalSize size, Theme t, TuiContext ctx, int blockRows) {
        int w = size.getColumns();
        int h = size.getRows();
        g.setForegroundColor(t.dim);
        Draw.box(g, t, 0, 0, w, h, null);

        // Usable rows: 1 .. h-4 (h-3 warning, h-2 vault path).
        int contentRows = LOGO.length + 1 + blockRows;
        int top = 1 + Math.max(0, (h - 4 - contentRows) / 2);

        int logoCol = (w - LOGO[0].length()) / 2;
        g.setForegroundColor(t.primary);
        for (int i = 0; i < LOGO.length; i++) {
            g.putString(logoCol, top + i, LOGO[i]);
        }
        g.setForegroundColor(t.dim);
        g.putString(logoCol + LOGO[0].length() + 4, top + LOGO.length - 1, "vault " + version());

        if (!ctx.warnings.isEmpty()) {
            g.setForegroundColor(t.error);
            g.putString(2, h - 3, Draw.truncate("err: " + ctx.warnings.get(0), w - 4));
        }
        g.setForegroundColor(t.dim);
        g.putString(2, h - 2, "vault: " + Draw.truncateLeft(ctx.vaultPath, w - 11));

        return top + LOGO.length + 1;
    }

    static String version() {
        String v = LockLayout.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : "v" + v;
    }

    static char spinner(long nowMillis) {
        return SPINNER.charAt((int) ((nowMillis / 120) % SPINNER.length()));
    }

    /** Draws a status line: color by kind, but the text itself always carries the meaning (ok:/err:). */
    static void message(TextGraphics g, Theme t, int col, int row, int maxWidth, String text) {
        if (text == null || text.isEmpty()) return;
        g.setForegroundColor(text.startsWith("err:") ? t.error : text.startsWith("ok:") ? t.success : t.dim);
        g.putString(col, row, Draw.truncate(text, maxWidth));
    }
}

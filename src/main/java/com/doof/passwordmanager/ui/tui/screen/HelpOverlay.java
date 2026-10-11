package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

import java.util.List;

/** {@code ?} overlay listing every key from docs/TUI_DESIGN.md §4. Any key closes it. */
public final class HelpOverlay implements TuiScreen {

    /** One row of the key table; {@code built} = false is shown as "(soon)". */
    record KeyHelp(String keys, String action, boolean built) {}

    static final List<KeyHelp> KEYS = List.of(
            new KeyHelp("j / k, arrows", "move selection", true),
            new KeyHelp("g g / G", "first / last entry", true),
            new KeyHelp("/", "search; ESC clears it", true),
            new KeyHelp("a / e / d", "add / edit / delete", true),
            new KeyHelp("s", "reveal password 10s", true),
            new KeyHelp("c", "copy password (20s clear)", true),
            new KeyHelp("u", "copy username", true),
            new KeyHelp("n", "show notes", true),
            new KeyHelp("p", "change master password", false),
            new KeyHelp("l", "lock now", true),
            new KeyHelp("?", "this help", true),
            new KeyHelp("q", "quit (clears screen, wipes keys)", true),
            new KeyHelp("TAB / shift+TAB", "next / previous field", true),
            new KeyHelp("g / ctrl+g", "generate password (entry form)", true),
            new KeyHelp("v / ctrl+v", "show / hide password (entry form)", true),
            new KeyHelp("ENTER / ESC", "confirm / cancel", true));

    static final String SOON = "(soon)";
    static final String FOOTER = "any key closes    " + SOON + " = not built yet";
    private static final int KEY_COL = 18;
    private static final int WIDTH = 64;

    private final TuiContext ctx;
    private final TuiScreen parent;

    public HelpOverlay(TuiContext ctx, TuiScreen parent) {
        this.ctx = ctx;
        this.parent = parent;
    }

    @Override
    public Action handleKey(KeyStroke key) {
        ctx.show(parent);
        return Action.REDRAW;
    }

    @Override
    public boolean tick(long nowMillis) {
        return parent.tick(nowMillis);  // keeps auto-lock detection and status timers running
    }

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        parent.draw(g, size, t);

        int h = KEYS.size() + 4;
        int col = (size.getColumns() - WIDTH) / 2;
        int row = Math.max(0, (size.getRows() - h) / 2);
        g.setBackgroundColor(t.background);
        g.setForegroundColor(t.text);
        g.fillRectangle(new com.googlecode.lanterna.TerminalPosition(col, row),
                new TerminalSize(WIDTH, h), ' ');
        g.setForegroundColor(t.primary);
        Draw.box(g, t, col, row, WIDTH, h, "help");

        for (int i = 0; i < KEYS.size(); i++) {
            KeyHelp k = KEYS.get(i);
            int r = row + 1 + i;
            g.setForegroundColor(k.built() ? t.primary : t.dim);
            g.putString(col + 2, r, k.keys());
            g.setForegroundColor(k.built() ? t.text : t.dim);
            g.putString(col + 2 + KEY_COL, r, k.action() + (k.built() ? "" : " " + SOON));
        }
        g.setForegroundColor(t.dim);
        g.putString(col + 2, row + h - 2, FOOTER);
    }
}

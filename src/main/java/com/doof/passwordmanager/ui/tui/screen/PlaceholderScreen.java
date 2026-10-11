package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

/** Empty themed frame for the Phase 2 skeleton; replaced by the Unlock screen in step 2. */
public final class PlaceholderScreen implements TuiScreen {

    static final String TITLE = " doof vault ";
    static final String MESSAGE = "tui skeleton - unlock screen comes next";
    static final String FOOTER = "[q] quit";

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme theme) {
        int w = size.getColumns();
        int h = size.getRows();

        g.setForegroundColor(theme.dim);
        drawBox(g, theme, 0, 0, w, h);
        drawDivider(g, theme, h - 3, w);

        g.setForegroundColor(theme.primary);
        g.putString(2, 0, TITLE);

        g.setForegroundColor(theme.text);
        g.putString(Math.max(1, (w - MESSAGE.length()) / 2), (h - 3) / 2, MESSAGE);

        g.setForegroundColor(theme.dim);
        g.putString(2, h - 2, FOOTER);
    }

    @Override
    public Action handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character && key.getCharacter() == 'q' && !key.isCtrlDown() && !key.isAltDown()) {
            return Action.QUIT;
        }
        return Action.NONE;
    }

    private static void drawBox(TextGraphics g, Theme t, int col, int row, int w, int h) {
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
    }

    private static void drawDivider(TextGraphics g, Theme t, int row, int w) {
        for (int x = 1; x < w - 1; x++) {
            g.setCharacter(x, row, t.horizontal);
        }
        g.setCharacter(0, row, t.teeLeft);
        g.setCharacter(w - 1, row, t.teeRight);
    }
}

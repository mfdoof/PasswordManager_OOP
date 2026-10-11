package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

/** One full-screen view (unlock, vault, ...). Drawn only when the terminal is at least the minimum size. */
public interface TuiScreen {

    enum Action { NONE, REDRAW, QUIT }

    /** Draws the whole screen; the background is already filled. */
    void draw(TextGraphics g, TerminalSize size, Theme theme);

    Action handleKey(KeyStroke key);
}

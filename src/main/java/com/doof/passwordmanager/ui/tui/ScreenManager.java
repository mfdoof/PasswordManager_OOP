package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;
import java.util.Objects;

/**
 * Owns the Lanterna screen: current view, global keys, minimum-size check and rendering.
 * All methods run on the UI thread.
 */
public final class ScreenManager {

    public static final int MIN_COLUMNS = 80;
    public static final int MIN_ROWS = 24;
    static final String QUIT_HINT = "resize the window or press [ctrl+c] to quit";

    private final Screen screen;
    private final Theme theme;
    private TuiScreen current;
    private boolean quitRequested;
    private boolean dirty = true;
    private boolean fullClear;
    private Runnable activityListener = () -> { };

    public ScreenManager(Screen screen, Theme theme, TuiScreen initial) {
        this.screen = Objects.requireNonNull(screen);
        this.theme = Objects.requireNonNull(theme);
        this.current = Objects.requireNonNull(initial);
    }

    /** Replaces the current screen; the old one wipes its secrets. */
    public void show(TuiScreen next) {
        Objects.requireNonNull(next);
        if (next != current) {
            current.onHide();
            current = next;
        }
        dirty = true;
    }

    /** Called on every keypress (resets the idle auto-lock timer). */
    public void setActivityListener(Runnable listener) {
        this.activityListener = Objects.requireNonNull(listener);
    }

    /** Next render blanks the whole terminal first (lock: nothing from the vault view may remain). */
    public void requestFullClear() {
        fullClear = true;
        dirty = true;
    }

    public TuiScreen current() {
        return current;
    }

    public void handleKey(KeyStroke key) {
        activityListener.run();
        // Raw mode: Ctrl+C arrives as a key, not a signal. Treat it (and EOF) as quit everywhere.
        if (key.getKeyType() == KeyType.EOF
                || (key.isCtrlDown() && key.getCharacter() != null && Character.toLowerCase(key.getCharacter()) == 'c')) {
            quitRequested = true;
            return;
        }
        if (tooSmall(screen.getTerminalSize())) {
            return;  // only the size message is visible; ignore input until resized
        }
        TuiScreen before = current;
        TuiScreen.Action action = before.handleKey(key);
        if (action == TuiScreen.Action.QUIT) {
            quitRequested = true;
        } else if (action == TuiScreen.Action.REDRAW || current != before) {
            dirty = true;
        }
    }

    public void tick(long nowMillis) {
        TuiScreen before = current;
        if (before.tick(nowMillis) || current != before) {
            dirty = true;
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public boolean isQuitRequested() {
        return quitRequested;
    }

    public void render() throws IOException {
        dirty = false;
        boolean complete = fullClear;
        if (fullClear) {
            fullClear = false;
            screen.clear();
        }
        TerminalSize size = screen.getTerminalSize();
        TextGraphics g = screen.newTextGraphics();
        g.setBackgroundColor(theme.background);
        g.setForegroundColor(theme.text);
        g.fill(' ');
        if (tooSmall(size)) {
            drawTooSmall(g, size);
        } else {
            current.draw(g, size, theme);
        }
        screen.setCursorPosition(null);
        screen.refresh(complete ? Screen.RefreshType.COMPLETE : Screen.RefreshType.AUTOMATIC);
    }

    /** Wipes the current screen's secrets and blanks the terminal (quit). */
    public void shutdown() throws IOException {
        current.onHide();
        clear();
    }

    /** Blanks the screen so no vault data is left behind. */
    public void clear() throws IOException {
        screen.clear();
        screen.refresh(Screen.RefreshType.COMPLETE);
    }

    static boolean tooSmall(TerminalSize size) {
        return size.getColumns() < MIN_COLUMNS || size.getRows() < MIN_ROWS;
    }

    private void drawTooSmall(TextGraphics g, TerminalSize size) {
        String msg = "terminal too small (need " + MIN_COLUMNS + "x" + MIN_ROWS
                + ", have " + size.getColumns() + "x" + size.getRows() + ")";
        if (msg.length() > size.getColumns()) {
            msg = MIN_COLUMNS + "x" + MIN_ROWS + " needed";
        }
        int row = Math.max(0, size.getRows() / 2 - 1);
        g.setForegroundColor(theme.error);
        g.putString(Math.max(0, (size.getColumns() - msg.length()) / 2), row, msg);
        if (row + 1 < size.getRows()) {
            g.setForegroundColor(theme.dim);
            g.putString(Math.max(0, (size.getColumns() - QUIT_HINT.length()) / 2), row + 1, QUIT_HINT);
        }
    }
}

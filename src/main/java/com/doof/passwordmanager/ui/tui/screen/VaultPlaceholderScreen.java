package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

/** Stand-in for the vault view (Phase 2 step 3). Lock returns to Unlock; detects auto-lock. */
public final class VaultPlaceholderScreen implements TuiScreen {

    static final String TITLE = "doof vault";
    static final String STATE = "[UNLOCKED]";
    static final String MESSAGE = "vault view comes in the next step";
    static final String FOOTER = "[l] lock  [q] quit";

    private final TuiContext ctx;

    public VaultPlaceholderScreen(TuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Action handleKey(KeyStroke key) {
        if (key.getKeyType() != KeyType.Character || key.isCtrlDown() || key.isAltDown()) {
            return Action.NONE;
        }
        switch (key.getCharacter()) {
            case 'l' -> {
                ctx.connector.lockVault();
                ctx.show(new UnlockScreen(ctx, "ok: vault locked"));
                return Action.REDRAW;
            }
            case 'q' -> {
                ctx.connector.lockVault();
                return Action.QUIT;
            }
            default -> {
                return Action.NONE;
            }
        }
    }

    @Override
    public boolean tick(long now) {
        if (!ctx.connector.isUnlocked()) {  // auto-lock fired in VaultService
            ctx.show(new UnlockScreen(ctx, "ok: vault auto-locked"));
            return true;
        }
        return false;
    }

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        int w = size.getColumns();
        int h = size.getRows();

        g.setForegroundColor(t.dim);
        Draw.box(g, t, 0, 0, w, h, null);
        Draw.divider(g, t, 0, h - 3, w);

        g.setForegroundColor(t.primary);
        g.putString(2, 0, " " + TITLE + " ");
        g.setForegroundColor(t.success);
        g.putString(w - STATE.length() - 3, 0, STATE);

        g.setForegroundColor(t.text);
        Draw.centered(g, (h - 3) / 2, w, MESSAGE);

        g.setForegroundColor(t.dim);
        g.putString(2, h - 2, FOOTER);
    }
}

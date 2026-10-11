package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.service.UnlockStatus;
import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.doof.passwordmanager.ui.tui.widget.SecretField;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.concurrent.CompletableFuture;

/**
 * docs/TUI_DESIGN.md §3.1. Throttling is enforced by VaultService; this screen only shows the
 * attempt count and countdown it reports, and disables input while the wait runs.
 */
public final class UnlockScreen implements TuiScreen {

    static final int PANEL_WIDTH = 45;
    static final int PANEL_ROWS = 6;
    static final String PROMPT = "  > master password: ";

    private final TuiContext ctx;
    private final SecretField field = new SecretField();

    private String message;          // without the "- wait Ns" suffix
    private long waitUntil;          // 0 = no wait
    private long lastWaitSeconds = -1;
    private CompletableFuture<Outcome> pending;
    private int lastSpinner = -1;

    public UnlockScreen(TuiContext ctx) {
        this(ctx, null);
    }

    /** {@code notice} is shown until the first keypress, e.g. "ok: vault locked". */
    public UnlockScreen(TuiContext ctx, String notice) {
        this.ctx = ctx;
        this.message = notice;
        UnlockStatus status = ctx.connector.getUnlockStatus();
        if (status.isThrottled()) {
            message = "err: " + attempts(status.getFailedAttempts());
            waitUntil = ctx.now() + status.getRetryAfterMs();
        }
    }

    @Override
    public Action handleKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            return Action.QUIT;
        }
        if (pending != null || isWaiting()) {
            return Action.NONE;  // input disabled while the KDF runs or during a throttle wait
        }
        switch (key.getKeyType()) {
            case Enter -> {
                if (field.isEmpty()) {
                    message = "err: enter your master password";
                } else {
                    submit();
                }
                return Action.REDRAW;
            }
            case Backspace -> {
                field.backspace();
                message = null;  // errors and notices stay until the next keypress
                return Action.REDRAW;
            }
            case Character -> {
                if (key.isCtrlDown() || key.isAltDown()) return Action.NONE;
                field.append(key.getCharacter());
                message = null;
                return Action.REDRAW;
            }
            default -> {
                return Action.NONE;
            }
        }
    }

    private void submit() {
        char[] password = field.take();
        message = null;
        lastSpinner = -1;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                ctx.connector.unlockVault(password);  // wipes password
                return Outcome.ok();
            } catch (RuntimeException e) {
                return Outcome.of(e);
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        }, ctx.worker);
    }

    @Override
    public boolean tick(long now) {
        boolean changed = false;
        if (pending != null) {
            if (pending.isDone()) {
                Outcome o = pending.join();
                pending = null;
                apply(o, now);
                return true;
            }
            int frame = LockLayout.spinner(now);
            if (frame != lastSpinner) {
                lastSpinner = frame;
                changed = true;
            }
        }
        if (waitUntil > 0) {
            if (now >= waitUntil) {
                waitUntil = 0;
                lastWaitSeconds = -1;
                changed = true;
            } else {
                long s = Draw.secondsLeft(waitUntil - now);
                if (s != lastWaitSeconds) {
                    lastWaitSeconds = s;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void apply(Outcome o, long now) {
        switch (o.kind) {
            case OK -> ctx.show(new VaultPlaceholderScreen(ctx));
            case INVALID -> {
                message = "err: invalid password (attempt " + o.failedAttempts + ")";
                waitUntil = o.retryAfterMs > 0 ? now + o.retryAfterMs : 0;
            }
            case THROTTLED -> {
                message = "err: " + attempts(o.failedAttempts);
                waitUntil = now + o.retryAfterMs;
            }
            case ERROR -> message = "err: " + o.error;
        }
    }

    private boolean isWaiting() {
        return waitUntil > 0 && ctx.now() < waitUntil;
    }

    private static String attempts(int n) {
        return n == 1 ? "1 failed attempt" : n + " failed attempts";
    }

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        int top = LockLayout.drawChrome(g, size, t, ctx, PANEL_ROWS + 2);
        int col = (size.getColumns() - PANEL_WIDTH) / 2;
        boolean inputEnabled = pending == null && !isWaiting();

        g.setForegroundColor(t.dim);
        Draw.box(g, t, col, top, PANEL_WIDTH, PANEL_ROWS, "unlock");

        int fieldRow = top + 2;
        g.setForegroundColor(inputEnabled ? t.primary : t.dim);
        g.putString(col + 2, fieldRow, ">");
        g.setForegroundColor(t.text);
        g.putString(col + 3, fieldRow, PROMPT.substring(3));
        int maxStars = PANEL_WIDTH - PROMPT.length() - 4;
        String stars = field.masked(maxStars);
        g.putString(col + PROMPT.length(), fieldRow, stars);
        if (inputEnabled) {
            g.setForegroundColor(t.primary);
            g.putString(col + PROMPT.length() + stars.length(), fieldRow, "_");
        }

        g.setForegroundColor(t.dim);
        g.putString(col + 2, top + 4, "[ENTER] unlock    [ESC] quit");

        int msgRow = top + PANEL_ROWS + 1;
        long now = ctx.now();
        if (pending != null) {
            LockLayout.message(g, t, col, msgRow, size.getColumns() - col - 2,
                    "unlocking " + LockLayout.spinner(now) + "  deriving key, this takes a moment");
        } else {
            String text = message;
            if (isWaiting()) {
                text = (text == null ? "err: please wait" : text) + " - wait " + Draw.secondsLeft(waitUntil - now) + "s";
            }
            LockLayout.message(g, t, col, msgRow, size.getColumns() - col - 2, text);
        }
    }

    @Override
    public void onHide() {
        field.clear();
    }

    /** Test hook. */
    boolean isWorking() {
        return pending != null;
    }
}

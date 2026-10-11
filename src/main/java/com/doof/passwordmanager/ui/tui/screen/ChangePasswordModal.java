package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.service.UnlockStatus;
import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.doof.passwordmanager.ui.tui.widget.PolicyView;
import com.doof.passwordmanager.ui.tui.widget.SecretField;
import com.doof.passwordmanager.util.PasswordPolicy;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * docs/TUI_DESIGN.md §3.5: change master password. Same checklist and strength bar as Create.
 * Rotation (Argon2id + re-encrypting every entry in one transaction) runs on the worker thread.
 * The current password goes through the same throttling as unlock; its errors show inline.
 */
public final class ChangePasswordModal implements TuiScreen {

    static final int WIDTH = 53;
    static final int HEIGHT = 13;
    static final String HINTS = "[TAB] next  [ENTER] change  [ESC] cancel";
    private static final String[] PROMPTS = {"current > ", "new     > ", "confirm > "};

    private final TuiContext ctx;
    private final VaultScreen parent;
    private final SecretField[] fields = {new SecretField(), new SecretField(), new SecretField()};
    private int focus;
    private boolean submitAttempted;
    private String message;     // without the "- wait Ns" suffix
    private long waitUntil;
    private long lastWaitSeconds = -1;
    private CompletableFuture<Outcome> pending;
    private int entryCount;
    private int lastSpinner = -1;
    private long lastSessionSecond = -1;

    public ChangePasswordModal(TuiContext ctx, VaultScreen parent) {
        this.ctx = ctx;
        this.parent = parent;
        UnlockStatus status = ctx.connector.getUnlockStatus();
        if (status.isThrottled()) {
            message = "err: " + attempts(status.getFailedAttempts());
            waitUntil = ctx.now() + status.getRetryAfterMs();
        }
    }

    private SecretField current() { return fields[0]; }
    private SecretField next() { return fields[1]; }
    private SecretField confirm() { return fields[2]; }

    @Override
    public Action handleKey(KeyStroke key) {
        if (pending != null) {
            return Action.NONE;  // rotation running; it is quick and must not be interrupted
        }
        if (key.getKeyType() == KeyType.Escape) {
            close();
            return Action.REDRAW;
        }
        if (isWaiting()) {
            return Action.NONE;  // throttle wait: input disabled
        }
        switch (key.getKeyType()) {
            case Tab, ArrowDown -> focus = (focus + 1) % 3;
            case ReverseTab, ArrowUp -> focus = (focus + 2) % 3;
            case Enter -> {
                if (focus < 2) focus++;
                else submit();
            }
            case Backspace -> {
                fields[focus].backspace();
                message = null;
            }
            case Character -> {
                if (key.isCtrlDown() || key.isAltDown()) return Action.NONE;
                fields[focus].append(key.getCharacter());
                message = null;
            }
            default -> { return Action.NONE; }
        }
        return Action.REDRAW;
    }

    private void submit() {
        submitAttempted = true;
        if (current().isEmpty()) {
            fail("err: enter your current password", 0);
            return;
        }
        char[] candidate = next().peek();
        try {
            if (!PasswordPolicy.check(candidate).meetsMasterPolicy()) {
                fail("err: needs " + PasswordPolicy.MASTER_MIN_LENGTH + "+ chars, upper, lower, digit", 1);
                return;
            }
        } finally {
            Arrays.fill(candidate, '\0');
        }
        if (!next().contentEquals(confirm())) {
            fail("err: new passwords do not match", 2);
            return;
        }

        entryCount = ctx.connector.listEntries().size();
        // The service wipes both arrays. The current password field is cleared (retype on failure);
        // new + confirm stay so a wrong current password doesn't force retyping them.
        char[] oldPw = current().take();
        char[] newPw = next().peek();
        message = null;
        lastSpinner = -1;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                ctx.connector.changeMasterPassword(oldPw, newPw);
                return Outcome.ok();
            } catch (RuntimeException e) {
                return Outcome.of(e);
            } finally {
                Arrays.fill(oldPw, '\0');
                Arrays.fill(newPw, '\0');
            }
        }, ctx.worker);
    }

    private void fail(String text, int field) {
        message = text;
        focus = field;
    }

    @Override
    public boolean tick(long now) {
        if (pending != null && pending.isDone()) {
            Outcome o = pending.join();
            pending = null;
            apply(o, now);
            return true;
        }
        if (!ctx.connector.isUnlocked()) {
            wipe();
            ctx.lockToUnlock(VaultScreen.AUTO_LOCKED);
            return true;
        }
        boolean changed = false;
        if (pending != null) {
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
        long second = parent.sessionSecond();
        if (second != lastSessionSecond) {
            lastSessionSecond = second;
            changed = true;
        }
        return changed;
    }

    private void apply(Outcome o, long now) {
        switch (o.kind) {
            case OK -> {
                wipe();
                parent.notice("ok: master password changed");
                ctx.show(parent);
            }
            case INVALID -> {
                message = "err: wrong current password (attempt " + o.failedAttempts + ")";
                waitUntil = o.retryAfterMs > 0 ? now + o.retryAfterMs : 0;
                focus = 0;
            }
            case THROTTLED -> {
                message = "err: " + attempts(o.failedAttempts);
                waitUntil = now + o.retryAfterMs;
                focus = 0;
            }
            case ERROR -> message = "err: not changed: " + o.error;
        }
    }

    private boolean isWaiting() {
        return waitUntil > 0 && ctx.now() < waitUntil;
    }

    private static String attempts(int n) {
        return n == 1 ? "1 failed attempt" : n + " failed attempts";
    }

    private void close() {
        wipe();
        ctx.show(parent);
    }

    private void wipe() {
        for (SecretField f : fields) f.clear();
    }

    @Override
    public void onHide() {
        wipe();
    }

    private boolean showMismatch() {
        return !confirm().isEmpty()
                && (submitAttempted || confirm().length() >= next().length())
                && !next().contentEquals(confirm());
    }

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        parent.draw(g, size, t);

        int col = (size.getColumns() - WIDTH) / 2;
        int top = Math.max(0, (size.getRows() - HEIGHT) / 2);
        g.setBackgroundColor(t.background);
        g.fillRectangle(new TerminalPosition(col, top), new TerminalSize(WIDTH, HEIGHT), ' ');
        g.setForegroundColor(t.primary);
        Draw.box(g, t, col, top, WIDTH, HEIGHT, "change master password");

        boolean enabled = pending == null && !isWaiting();
        int maxStars = WIDTH - 4 - 2 - PROMPTS[0].length() - 2;
        for (int i = 0; i < 3; i++) {
            int row = top + 2 + i;
            boolean focused = enabled && focus == i;
            g.setForegroundColor(t.text);
            g.putString(col + 2, row, PROMPTS[i].substring(0, PROMPTS[i].length() - 2));
            g.setForegroundColor(focused ? t.primary : t.dim);
            g.putString(col + 2 + PROMPTS[i].length() - 2, row, ">");
            g.setForegroundColor(t.text);
            String stars = fields[i].masked(maxStars);
            g.putString(col + 2 + PROMPTS[i].length(), row, stars);
            if (focused) {
                g.setForegroundColor(t.primary);
                g.putString(col + 2 + PROMPTS[i].length() + stars.length(), row, "_");
            }
        }
        if (showMismatch()) {
            g.setForegroundColor(t.error);
            g.putString(col + 4, top + 5, "err: new passwords do not match");
        }

        char[] pw = next().peek();
        try {
            PolicyView.draw(g, t, col + 4, top + 6, pw);
        } finally {
            Arrays.fill(pw, '\0');
        }

        long now = ctx.now();
        String text;
        if (pending != null) {
            text = "re-encrypting " + entryCount + (entryCount == 1 ? " entry " : " entries ") + LockLayout.spinner(now);
        } else {
            text = message;
            if (isWaiting()) {
                text = (text == null ? "err: please wait" : text) + " - wait " + Draw.secondsLeft(waitUntil - now) + "s";
            }
        }
        LockLayout.message(g, t, col + 2, top + 10, WIDTH - 4, text);

        g.setForegroundColor(t.dim);
        g.putString(col + 2, top + 11, HINTS);
    }

    /** Test hooks. */
    boolean isWorking() {
        return pending != null;
    }

    SecretField field(int i) {
        return fields[i];
    }
}

package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.ui.tui.Draw;
import com.doof.passwordmanager.ui.tui.Theme;
import com.doof.passwordmanager.ui.tui.TuiContext;
import com.doof.passwordmanager.ui.tui.TuiScreen;
import com.doof.passwordmanager.ui.tui.widget.PolicyView;
import com.doof.passwordmanager.ui.tui.widget.SecretField;
import com.doof.passwordmanager.util.PasswordPolicy;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** docs/TUI_DESIGN.md §3.2: first run. Live checklist + strength bar; the policy itself is enforced by VaultService. */
public final class CreateScreen implements TuiScreen {

    static final int PANEL_WIDTH = 47;
    static final int PANEL_ROWS = 11;
    static final String NEW_PROMPT = "  > new master password: ";
    static final String CONFIRM_PROMPT = "  > confirm:             ";

    private final TuiContext ctx;
    private final SecretField password = new SecretField();
    private final SecretField confirm = new SecretField();
    private int focus;                 // 0 = password, 1 = confirm
    private boolean submitAttempted;
    private String message;
    private CompletableFuture<Outcome> pending;
    private int lastSpinner = -1;

    public CreateScreen(TuiContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Action handleKey(KeyStroke key) {
        if (key.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape) {
            return Action.QUIT;
        }
        if (pending != null) {
            return Action.NONE;
        }
        SecretField current = focus == 0 ? password : confirm;
        switch (key.getKeyType()) {
            case Tab, ReverseTab, ArrowDown, ArrowUp -> focus = 1 - focus;
            case Enter -> {
                if (focus == 0) focus = 1;
                else submit();
            }
            case Backspace -> {
                current.backspace();
                message = null;
            }
            case Character -> {
                if (key.isCtrlDown() || key.isAltDown()) return Action.NONE;
                current.append(key.getCharacter());
                message = null;
            }
            default -> {
                return Action.NONE;
            }
        }
        return Action.REDRAW;
    }

    private void submit() {
        submitAttempted = true;
        char[] candidate = password.peek();
        try {
            if (!PasswordPolicy.check(candidate).meetsMasterPolicy()) {
                message = "err: needs " + PasswordPolicy.MASTER_MIN_LENGTH + "+ chars with upper, lower and a digit";
                focus = 0;
                return;
            }
        } finally {
            Arrays.fill(candidate, '\0');
        }
        if (!password.contentEquals(confirm)) {
            message = "err: passwords do not match";
            return;
        }
        char[] pw = password.take();
        confirm.clear();
        message = null;
        lastSpinner = -1;
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                ctx.connector.createVault(pw);  // wipes pw
                return Outcome.ok();
            } catch (RuntimeException e) {
                return Outcome.of(e);
            } finally {
                Arrays.fill(pw, '\0');
            }
        }, ctx.worker);
    }

    @Override
    public boolean tick(long now) {
        if (pending == null) return false;
        if (pending.isDone()) {
            Outcome o = pending.join();
            pending = null;
            if (o.kind == Outcome.Kind.OK) {
                ctx.show(new VaultScreen(ctx, "ok: vault created"));
            } else {
                message = "err: could not create vault: " + o.error;
            }
            return true;
        }
        int frame = LockLayout.spinner(now);
        if (frame != lastSpinner) {
            lastSpinner = frame;
            return true;
        }
        return false;
    }

    /** Mismatch shows once confirm is as long as the password, or after a submit attempt. */
    private boolean showMismatch() {
        return !confirm.isEmpty()
                && (submitAttempted || confirm.length() >= password.length())
                && !password.contentEquals(confirm);
    }

    @Override
    public void draw(TextGraphics g, TerminalSize size, Theme t) {
        int top = LockLayout.drawChrome(g, size, t, ctx, PANEL_ROWS + 2);
        int col = (size.getColumns() - PANEL_WIDTH) / 2;
        boolean enabled = pending == null;

        g.setForegroundColor(t.dim);
        Draw.box(g, t, col, top, PANEL_WIDTH, PANEL_ROWS, "create vault");

        int maxStars = PANEL_WIDTH - NEW_PROMPT.length() - 4;
        drawField(g, t, col, top + 2, NEW_PROMPT, password, maxStars, enabled && focus == 0);
        drawField(g, t, col, top + 3, CONFIRM_PROMPT, confirm, maxStars, enabled && focus == 1);
        if (showMismatch()) {
            g.setForegroundColor(t.error);
            g.putString(col + 4, top + 4, "err: passwords do not match");
        }

        char[] pw = password.peek();
        try {
            PolicyView.draw(g, t, col + 4, top + 5, pw);
        } finally {
            Arrays.fill(pw, '\0');
        }

        g.setForegroundColor(t.dim);
        g.putString(col + 2, top + 9, "[TAB] next  [ENTER] create  [ESC] quit");

        int msgRow = top + PANEL_ROWS + 1;
        String text = pending != null
                ? "creating " + LockLayout.spinner(ctx.now()) + "  deriving key, this takes a moment"
                : message;
        LockLayout.message(g, t, col, msgRow, size.getColumns() - col - 2, text);
    }

    private static void drawField(TextGraphics g, Theme t, int col, int row, String prompt, SecretField f,
                                  int maxStars, boolean focused) {
        g.setForegroundColor(focused ? t.primary : t.dim);
        g.putString(col + 2, row, ">");
        g.setForegroundColor(t.text);
        g.putString(col + 3, row, prompt.substring(3));
        String stars = f.masked(maxStars);
        g.putString(col + prompt.length(), row, stars);
        if (focused) {
            g.setForegroundColor(t.primary);
            g.putString(col + prompt.length() + stars.length(), row, "_");
        }
    }

    @Override
    public void onHide() {
        password.clear();
        confirm.clear();
    }

    /** Test hook. */
    boolean isWorking() {
        return pending != null;
    }
}

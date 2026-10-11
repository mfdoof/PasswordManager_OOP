package com.doof.passwordmanager.ui.tui.widget;

/**
 * Status message per docs/TUI_DESIGN.md §6 rule 2: {@code ok:} messages auto-dismiss after 5s,
 * {@code err:} messages stay until the next keypress.
 */
public final class StatusBar {

    public static final long INFO_MILLIS = 5_000;

    private String text;
    private boolean error;
    private long shownAt;

    public void show(String message, long now) {
        text = message;
        error = message != null && message.startsWith("err:");
        shownAt = now;
    }

    /** Call at the start of every keypress, before the key is handled. */
    public void onKey() {
        if (error) {
            text = null;
            error = false;
        }
    }

    /** Returns true if the message was dismissed (redraw needed). */
    public boolean tick(long now) {
        if (text != null && !error && now - shownAt >= INFO_MILLIS) {
            text = null;
            return true;
        }
        return false;
    }

    /** Current message, or null. */
    public String text() {
        return text;
    }
}

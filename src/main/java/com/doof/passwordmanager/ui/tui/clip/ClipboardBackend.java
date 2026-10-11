package com.doof.passwordmanager.ui.tui.clip;

/** Raw system clipboard access. Both calls may throw if the clipboard is busy. */
public interface ClipboardBackend {

    void setText(String text);

    /** Current text content, or null if the clipboard holds no text. */
    String getText();
}

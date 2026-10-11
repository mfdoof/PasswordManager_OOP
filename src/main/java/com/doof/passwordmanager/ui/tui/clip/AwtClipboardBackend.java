package com.doof.passwordmanager.ui.tui.clip;

import java.awt.AWTError;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Map;

/** System clipboard via Java AWT (docs/TUI_DESIGN.md §5). No OSC 52. */
public final class AwtClipboardBackend implements ClipboardBackend {

    private final Clipboard clipboard;

    private AwtClipboardBackend(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    /**
     * Returns a backend if a desktop clipboard is reachable for the user, else null. Over SSH
     * without X forwarding the "system" clipboard would be the remote machine's, which the user
     * cannot paste from, so that counts as unavailable too. Never throws.
     */
    public static ClipboardBackend probe(Map<String, String> env) {
        if (isSshWithoutDisplay(env)) {
            return null;
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return null;
            }
            return new AwtClipboardBackend(Toolkit.getDefaultToolkit().getSystemClipboard());
        } catch (HeadlessException | AWTError | SecurityException | IllegalStateException | UnsatisfiedLinkError e) {
            return null;
        }
    }

    static boolean isSshWithoutDisplay(Map<String, String> env) {
        boolean ssh = notEmpty(env.get("SSH_CONNECTION")) || notEmpty(env.get("SSH_TTY"));
        return ssh && !notEmpty(env.get("DISPLAY"));
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    @Override
    public void setText(String text) {
        clipboard.setContents(new StringSelection(text), null);
    }

    @Override
    public String getText() {
        try {
            Transferable t = clipboard.getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) t.getTransferData(DataFlavor.stringFlavor);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

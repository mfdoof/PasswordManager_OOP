package com.doof.passwordmanager.ui.tui.clip;

import com.doof.passwordmanager.crypto.SecureWipe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Supplier;

/**
 * Clipboard with auto-clear for secrets. A copied password is cleared after 20s, on lock and on
 * quit, but only if the clipboard still holds our value (compared by SHA-256, so the value itself
 * is not kept). The backend is probed lazily on first use, so AWT is only started if needed.
 */
public final class SecureClipboard {

    public static final long CLEAR_MILLIS = 20_000;

    private final Supplier<ClipboardBackend> probe;
    private ClipboardBackend backend;
    private boolean probed;

    private byte[] pendingHash;  // SHA-256 of the secret we copied; null = nothing to clear
    private long clearAt;

    /** {@code probe} returns a backend, or null if no clipboard is available. */
    public SecureClipboard(Supplier<ClipboardBackend> probe) {
        this.probe = probe;
    }

    public boolean available() {
        if (!probed) {
            probed = true;
            try {
                backend = probe.get();
            } catch (RuntimeException | Error e) {
                backend = null;
            }
        }
        return backend != null;
    }

    /**
     * Copies a secret and schedules its clearing. The clipboard API needs a String, so one
     * unwipeable copy of the value is unavoidable here (see SECURITY.md).
     *
     * @return false if no clipboard is available or the copy failed
     */
    public boolean copySecret(char[] value, long now) {
        if (!available()) return false;
        try {
            backend.setText(new String(value));
        } catch (RuntimeException e) {
            return false;
        }
        forgetPending();
        pendingHash = sha256(value);
        clearAt = now + CLEAR_MILLIS;
        return true;
    }

    /** Copies non-secret text (username); no auto-clear. Cancels a pending clear: the value we put is gone. */
    public boolean copyPlain(String value) {
        if (!available()) return false;
        try {
            backend.setText(value);
        } catch (RuntimeException e) {
            return false;
        }
        forgetPending();
        return true;
    }

    public boolean hasPendingClear() {
        return pendingHash != null;
    }

    /** Whole seconds until the scheduled clear (rounded up), or 0 if none is pending. */
    public long secondsUntilClear(long now) {
        if (pendingHash == null) return 0;
        return Math.max(1, (clearAt - now + 999) / 1000);
    }

    /** Clears when due. Returns true if a pending clear was handled (redraw needed). */
    public boolean tick(long now) {
        if (pendingHash != null && now >= clearAt) {
            clearIfUnchanged();
            return true;
        }
        return false;
    }

    /** Clears right away if a secret is pending (lock, quit). */
    public void clearNow() {
        if (pendingHash != null) {
            clearIfUnchanged();
        }
    }

    private void clearIfUnchanged() {
        try {
            String current = backend.getText();
            char[] chars = current == null ? null : current.toCharArray();
            boolean ours = chars != null && MessageDigest.isEqual(pendingHash, sha256(chars));
            SecureWipe.wipe(chars);
            if (ours) {
                backend.setText("");
            }
        } catch (RuntimeException e) {
            // Clipboard busy or gone: nothing more we can do.
        } finally {
            forgetPending();
        }
    }

    private void forgetPending() {
        SecureWipe.wipe(pendingHash);
        pendingHash = null;
    }

    private static byte[] sha256(char[] value) {
        byte[] utf8 = SecureWipe.toUtf8(value);
        try {
            return MessageDigest.getInstance("SHA-256").digest(utf8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            SecureWipe.wipe(utf8);  // the caller's array is left intact
        }
    }
}

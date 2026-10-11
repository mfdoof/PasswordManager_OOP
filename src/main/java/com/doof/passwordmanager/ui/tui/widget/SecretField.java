package com.doof.passwordmanager.ui.tui.widget;

import java.util.Arrays;

/**
 * Masked text input that keeps its contents in a char[] and wipes every buffer it discards.
 * The value is never turned into a String.
 */
public final class SecretField {

    public static final int MAX_LENGTH = 1024;

    private char[] buf = new char[32];
    private int len;

    /** Appends {@code c}; ignored at {@link #MAX_LENGTH}. */
    public void append(char c) {
        if (len >= MAX_LENGTH) return;
        if (len == buf.length) {
            char[] bigger = Arrays.copyOf(buf, Math.min(buf.length * 2, MAX_LENGTH));
            Arrays.fill(buf, '\0');
            buf = bigger;
        }
        buf[len++] = c;
    }

    public void backspace() {
        if (len > 0) buf[--len] = '\0';
    }

    public int length() {
        return len;
    }

    public boolean isEmpty() {
        return len == 0;
    }

    /** Copy of the current value for read-only checks (strength, checklist); the caller wipes it. */
    public char[] peek() {
        return Arrays.copyOf(buf, len);
    }

    /** Hands the value to the caller (who must wipe it) and clears the field. */
    public char[] take() {
        char[] out = Arrays.copyOf(buf, len);
        clear();
        return out;
    }

    public boolean contentEquals(SecretField other) {
        if (len != other.len) return false;
        int diff = 0;
        for (int i = 0; i < len; i++) diff |= buf[i] ^ other.buf[i];
        return diff == 0;
    }

    /** Wipes and empties the field. */
    public void clear() {
        Arrays.fill(buf, '\0');
        len = 0;
    }

    /** Replaces the contents with {@code value} (not kept); the old buffer is wiped. */
    public void set(char[] value) {
        clear();
        for (char c : value) append(c);
    }

    /** Character at {@code index} (for cell-by-cell drawing without building a String). */
    public char charAt(int index) {
        if (index < 0 || index >= len) throw new IndexOutOfBoundsException(index);
        return buf[index];
    }

    /** Masked rendering: one '*' per character, showing only the last {@code maxWidth} positions. */
    public String masked(int maxWidth) {
        int n = Math.min(len, Math.max(0, maxWidth));
        return "*".repeat(n);
    }

    /** Test hook: the live backing array (to check it gets wiped). */
    char[] buffer() {
        return buf;
    }
}

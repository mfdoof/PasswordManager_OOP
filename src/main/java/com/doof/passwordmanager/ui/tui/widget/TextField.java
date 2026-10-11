package com.doof.passwordmanager.ui.tui.widget;

/** Single-line plain text input (site, username). Not for secrets: use {@link SecretField}. */
public final class TextField {

    private final StringBuilder text = new StringBuilder();
    private final int maxLength;

    public TextField(int maxLength) {
        this.maxLength = maxLength;
    }

    public void append(char c) {
        if (text.length() < maxLength && !Character.isISOControl(c)) text.append(c);
    }

    public void backspace() {
        if (text.length() > 0) text.setLength(text.length() - 1);
    }

    public void set(String value) {
        text.setLength(0);
        if (value != null) text.append(value, 0, Math.min(value.length(), maxLength));
    }

    public String text() {
        return text.toString();
    }

    public boolean isBlank() {
        return text.toString().isBlank();
    }

    /** The last {@code width} characters, so the end (where the cursor is) stays visible. */
    public String tail(int width) {
        return text.length() <= width ? text.toString() : text.substring(text.length() - width);
    }
}

package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.db.ConnectionManager;

/**
 * Command-line flags for the TUI. {@code --vault <path>} is resolved by {@link ConnectionManager};
 * it is accepted here so it isn't reported as unknown.
 */
public final class TuiOptions {

    static final String USAGE = String.join("\n",
            "usage: doof-vault [--vault <path>] [--ascii] [--no-color]",
            "  --vault <path>  vault file (default: $DOOF_VAULT_PATH, then ~/.doof/vault.db)",
            "  --ascii         draw borders with + - | instead of box-drawing characters",
            "  --no-color      monochrome (same as setting NO_COLOR)");

    private final boolean ascii;
    private final boolean noColor;

    private TuiOptions(boolean ascii, boolean noColor) {
        this.ascii = ascii;
        this.noColor = noColor;
    }

    /** @throws IllegalArgumentException on an unknown flag or a stray argument */
    public static TuiOptions parse(String[] args) {
        boolean ascii = false, noColor = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--ascii" -> ascii = true;
                case "--no-color" -> noColor = true;
                case ConnectionManager.VAULT_ARG -> i++;  // value checked by ConnectionManager
                default -> {
                    if (!a.startsWith(ConnectionManager.VAULT_ARG + "=")) {
                        throw new IllegalArgumentException("unknown argument: " + a);
                    }
                }
            }
        }
        return new TuiOptions(ascii, noColor);
    }

    public boolean ascii() { return ascii; }
    public boolean noColor() { return noColor; }
}

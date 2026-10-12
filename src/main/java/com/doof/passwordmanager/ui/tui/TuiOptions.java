package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.db.ConnectionManager;

/**
 * Command-line flags for the TUI. {@code --vault <path>} is resolved by {@link ConnectionManager};
 * it is accepted here so it isn't reported as unknown.
 */
public final class TuiOptions {

    public static final String USAGE = String.join("\n",
            "usage: doofv [--vault <path>] [--ascii] [--no-color]",
            "  --vault <path>  vault file (default: $DOOF_VAULT_PATH, then ~/.doof/vault.db)",
            "  --ascii         draw borders with + - | instead of box-drawing characters",
            "  --no-color      monochrome (same as setting NO_COLOR)",
            "  -h, --help      show this help",
            "       doofv backup --help   (back up the vault file; see the backup usage)");

    private final boolean ascii;
    private final boolean noColor;
    private final boolean help;

    private TuiOptions(boolean ascii, boolean noColor, boolean help) {
        this.ascii = ascii;
        this.noColor = noColor;
        this.help = help;
    }

    /** @throws IllegalArgumentException on an unknown flag or a stray argument */
    public static TuiOptions parse(String[] args) {
        boolean ascii = false, noColor = false, help = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--ascii" -> ascii = true;
                case "--no-color" -> noColor = true;
                case "-h", "--help" -> help = true;
                case ConnectionManager.VAULT_ARG -> i++;  // value checked by ConnectionManager
                default -> {
                    if (!a.startsWith(ConnectionManager.VAULT_ARG + "=")) {
                        throw new IllegalArgumentException("unknown argument: " + a);
                    }
                }
            }
        }
        return new TuiOptions(ascii, noColor, help);
    }

    public boolean ascii() { return ascii; }
    public boolean noColor() { return noColor; }
    /** Print {@link #USAGE} to stdout and exit 0 without opening the vault. */
    public boolean help() { return help; }
}

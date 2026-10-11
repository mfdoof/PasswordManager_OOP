package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.ui.tui.screen.PlaceholderScreen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Entry point: terminal setup, main loop, resize handling. */
public final class TuiApp {

    /** Input poll interval; keeps the loop responsive to resizes and (later) timers. */
    static final long POLL_MS = 25;

    private TuiApp() {}

    public static void main(String[] args) {
        TuiOptions options;
        try {
            options = TuiOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("doof-vault: " + e.getMessage());
            System.err.println(TuiOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options.swing()) {
            com.doof.passwordmanager.app.Main.main(args);
            return;
        }

        Theme theme = Theme.detect(options, System.getenv());
        Charset charset = theme.unicode() ? StandardCharsets.UTF_8 : Charset.defaultCharset();
        // Force a real text terminal: never fall back to Lanterna's Swing terminal emulator window.
        DefaultTerminalFactory factory = new DefaultTerminalFactory(System.out, System.in, charset)
                .setForceTextTerminal(true);

        try (Terminal terminal = factory.createTerminal()) {
            run(terminal, theme);
        } catch (IOException | RuntimeException e) {
            System.err.println("doof-vault: cannot use this terminal (" + e.getMessage() + ")");
            System.err.println("Run it in an interactive terminal (local or over SSH).");
            System.exit(1);
        }
    }

    /** Runs the UI on {@code terminal} until the user quits; clears the screen on exit. */
    static void run(Terminal terminal, Theme theme) throws IOException {
        TerminalScreen screen = new TerminalScreen(terminal);
        screen.startScreen();
        try {
            ScreenManager manager = new ScreenManager(screen, theme, new PlaceholderScreen());
            manager.render();
            while (!manager.isQuitRequested()) {
                boolean redraw = false;

                TerminalSize resized = screen.doResizeIfNecessary();
                if (resized != null) {
                    redraw = true;
                }
                KeyStroke key = screen.pollInput();
                if (key != null) {
                    redraw |= manager.handleKey(key);
                }
                if (redraw && !manager.isQuitRequested()) {
                    manager.render();
                }
                if (key == null) {
                    sleep();
                }
            }
            manager.clear();
        } finally {
            screen.stopScreen();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

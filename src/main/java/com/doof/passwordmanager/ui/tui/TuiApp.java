package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.app.AppBootstrap;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.service.ApplicationConnector;
import com.doof.passwordmanager.ui.tui.clip.AwtClipboardBackend;
import com.doof.passwordmanager.ui.tui.clip.SecureClipboard;
import com.doof.passwordmanager.ui.tui.screen.CreateScreen;
import com.doof.passwordmanager.ui.tui.screen.UnlockScreen;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Entry point: opens the vault, sets up the terminal, runs the main loop. */
public final class TuiApp {

    /** Input poll interval; keeps the loop responsive to resizes and timers. */
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
        // The clipboard uses AWT; on macOS this keeps it from adding a Dock icon for a terminal app.
        System.setProperty("apple.awt.UIElement", "true");

        // Open the vault before taking over the terminal, so errors print normally.
        List<String> warnings = new ArrayList<>();
        ConnectionManager cm;
        try {
            cm = ConnectionManager.open(ConnectionManager.resolveVaultPath(args), warnings::add);
        } catch (RuntimeException e) {
            System.err.println("doof-vault: cannot open vault: " + e.getMessage());
            System.exit(1);
            return;
        }
        ApplicationConnector connector = AppBootstrap.createConnector(cm);
        ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "doof-worker");
            t.setDaemon(true);
            return t;
        });
        TuiContext ctx = new TuiContext(connector, displayPath(cm.getVaultPath(), System.getProperty("user.home")),
                warnings, worker, System::currentTimeMillis, java.time.ZoneId.systemDefault(),
                new SecureClipboard(() -> AwtClipboardBackend.probe(System.getenv())));

        Theme theme = Theme.detect(options, System.getenv());
        Charset charset = theme.unicode() ? StandardCharsets.UTF_8 : Charset.defaultCharset();
        // Force a real text terminal: never fall back to Lanterna's Swing terminal emulator window.
        DefaultTerminalFactory factory = new DefaultTerminalFactory(System.out, System.in, charset)
                .setForceTextTerminal(true);

        int exit = 0;
        try (Terminal terminal = factory.createTerminal()) {
            run(terminal, theme, ctx);
        } catch (IOException | RuntimeException e) {
            System.err.println("doof-vault: cannot use this terminal (" + e.getMessage() + ")");
            System.err.println("Run it in an interactive terminal (local or over SSH).");
            exit = 1;
        } finally {
            ctx.clipboard.clearNow();  // don't leave a copied password behind
            connector.lockVault();     // wipe keys on every exit path
            worker.shutdownNow();
        }
        System.exit(exit);
    }

    /** First screen: Create on a new vault, Unlock otherwise. */
    static TuiScreen initialScreen(TuiContext ctx) {
        return ctx.connector.isVaultInitialized() ? new UnlockScreen(ctx) : new CreateScreen(ctx);
    }

    /** Runs the UI on {@code terminal} until the user quits; wipes screen secrets and clears the screen on exit. */
    static void run(Terminal terminal, Theme theme, TuiContext ctx) throws IOException {
        TerminalScreen screen = new TerminalScreen(terminal);
        screen.startScreen();
        try {
            ScreenManager manager = new ScreenManager(screen, theme, initialScreen(ctx));
            ctx.attach(manager);
            while (!manager.isQuitRequested()) {
                if (screen.doResizeIfNecessary() != null) {
                    manager.markDirty();
                }
                KeyStroke key = screen.pollInput();
                if (key != null) {
                    manager.handleKey(key);
                }
                if (ctx.clipboard.tick(ctx.now())) {
                    manager.markDirty();
                }
                manager.tick(ctx.now());
                if (manager.isQuitRequested()) {
                    break;
                }
                if (manager.isDirty()) {
                    manager.render();
                }
                if (key == null) {
                    sleep();
                }
            }
            manager.shutdown();
        } finally {
            screen.stopScreen();
        }
    }

    /** Shortens the user's home directory to ~ for display. */
    static String displayPath(Path path, String userHome) {
        String p = path.toString();
        if (userHome != null && !userHome.isEmpty() && (p.equals(userHome) || p.startsWith(userHome + "/"))) {
            return "~" + p.substring(userHome.length());
        }
        return p;
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

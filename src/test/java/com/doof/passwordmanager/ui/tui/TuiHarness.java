package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.crypto.KdfParams;
import com.doof.passwordmanager.dao.JdbcEntryDao;
import com.doof.passwordmanager.dao.JdbcVaultMetadataDao;
import com.doof.passwordmanager.dao.JdbcVaultRotationDao;
import com.doof.passwordmanager.db.ConnectionManager;
import com.doof.passwordmanager.service.ApplicationConnector;
import com.doof.passwordmanager.testutil.MutableClock;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Drives the TUI on Lanterna's in-memory terminal against a real vault file (fast KDF params,
 * controllable clock). Worker jobs queue up until {@link #runWorker()} unless {@code inlineWorker}.
 */
public final class TuiHarness {

    public static final KdfParams FAST = new KdfParams(1024, 1, 1);
    public static final Theme THEME = Theme.detect(TuiOptions.parse(new String[]{}), Map.of("LANG", "en_US.UTF-8"));

    public final MutableClock clock = new MutableClock(1_700_000_000_000L);
    public final ApplicationConnector connector;
    public final DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(80, 24));
    public final TuiContext ctx;
    public final ScreenManager manager;
    private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
    private final TerminalScreen screen;

    public TuiHarness(Path vaultFile, boolean inlineWorker, List<String> warnings) {
        ConnectionManager cm = ConnectionManager.open(vaultFile, w -> {});
        connector = new ApplicationConnector(new JdbcVaultMetadataDao(cm, clock), new JdbcVaultRotationDao(cm, clock),
                new JdbcEntryDao(cm, clock), FAST, clock);
        Executor worker = inlineWorker ? Runnable::run : queued::add;
        ctx = new TuiContext(connector, "~/.doof/vault.db", warnings, worker, clock::millis, java.time.ZoneOffset.UTC);
        try {
            screen = new TerminalScreen(terminal);
            screen.startScreen();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        manager = new ScreenManager(screen, THEME, TuiApp.initialScreen(ctx));
        ctx.attach(manager);
        render();
    }

    public TuiHarness(Path vaultFile) {
        this(vaultFile, true, List.of());
    }

    public void type(String s) {
        for (char c : s.toCharArray()) key(new KeyStroke(c, false, false));
    }

    public void press(KeyType type) {
        key(new KeyStroke(type));
    }

    public void key(KeyStroke k) {
        manager.handleKey(k);
        tick();
    }

    /** One loop iteration without input: tick + render. */
    public void tick() {
        manager.tick(clock.millis());
        render();
    }

    public void advance(long millis) {
        clock.advance(millis);
        tick();
    }

    /** Runs queued worker jobs (the KDF), then ticks so the screen picks up the result. */
    public void runWorker() {
        while (!queued.isEmpty()) queued.poll().run();
        tick();
    }

    public int queuedJobs() {
        return queued.size();
    }

    /** Resizes the virtual terminal and lets the screen pick it up, like the main loop does. */
    public void resize(int columns, int rows) {
        terminal.setTerminalSize(new TerminalSize(columns, rows));
        screen.doResizeIfNecessary();
        manager.markDirty();
        render();
    }

    public void render() {
        try {
            manager.render();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        TerminalSize size = terminal.getTerminalSize();
        for (int r = 0; r < size.getRows(); r++) {
            for (int c = 0; c < size.getColumns(); c++) {
                sb.append(terminal.getBufferCharacter(c, r).getCharacterString());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public void close() {
        try {
            screen.stopScreen();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

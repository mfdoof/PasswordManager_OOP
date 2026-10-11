package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.service.ApplicationConnector;
import com.doof.passwordmanager.ui.tui.clip.SecureClipboard;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** What screens share: the services, where the vault is, a worker for slow calls, and the clock. */
public final class TuiContext {

    public final ApplicationConnector connector;
    /** Vault path for display (home shortened to ~). */
    public final String vaultPath;
    /** Warnings from opening the vault (e.g. loose file permissions), shown on the lock screens. */
    public final List<String> warnings;
    /** Runs slow work (Argon2id) off the UI thread. Results are picked up in {@link TuiScreen#tick}. */
    public final Executor worker;
    public final LongSupplier clock;
    /** Time zone for displayed dates. */
    public final ZoneId zone;
    /** Copy with auto-clear; ticked by the main loop, cleared on lock and quit. */
    public final SecureClipboard clipboard;
    private ScreenManager manager;

    public TuiContext(ApplicationConnector connector, String vaultPath, List<String> warnings,
                      Executor worker, LongSupplier clock, ZoneId zone, SecureClipboard clipboard) {
        this.connector = Objects.requireNonNull(connector);
        this.vaultPath = Objects.requireNonNull(vaultPath);
        this.warnings = List.copyOf(warnings);
        this.worker = Objects.requireNonNull(worker);
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
        this.clipboard = Objects.requireNonNull(clipboard);
    }

    void attach(ScreenManager manager) {
        this.manager = manager;
    }

    public void show(TuiScreen screen) {
        manager.show(screen);
    }

    public long now() {
        return clock.getAsLong();
    }
}

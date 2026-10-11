package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.service.ApplicationConnector;

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
    private ScreenManager manager;

    public TuiContext(ApplicationConnector connector, String vaultPath, List<String> warnings,
                      Executor worker, LongSupplier clock, ZoneId zone) {
        this.connector = Objects.requireNonNull(connector);
        this.vaultPath = Objects.requireNonNull(vaultPath);
        this.warnings = List.copyOf(warnings);
        this.worker = Objects.requireNonNull(worker);
        this.clock = Objects.requireNonNull(clock);
        this.zone = Objects.requireNonNull(zone);
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

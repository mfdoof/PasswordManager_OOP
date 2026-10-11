package com.doof.passwordmanager.ui.tui.screen;

import com.doof.passwordmanager.service.InvalidPasswordException;
import com.doof.passwordmanager.service.UnlockThrottledException;

/** Result of a slow vault call made on the worker thread, handed back to the UI thread. */
final class Outcome {

    enum Kind { OK, INVALID, THROTTLED, ERROR }

    final Kind kind;
    final int failedAttempts;
    final long retryAfterMs;
    final String error;

    private Outcome(Kind kind, int failedAttempts, long retryAfterMs, String error) {
        this.kind = kind;
        this.failedAttempts = failedAttempts;
        this.retryAfterMs = retryAfterMs;
        this.error = error;
    }

    static Outcome ok() {
        return new Outcome(Kind.OK, 0, 0, null);
    }

    static Outcome of(RuntimeException e) {
        if (e instanceof InvalidPasswordException ipe) {
            return new Outcome(Kind.INVALID, ipe.getFailedAttempts(), ipe.getRetryAfterMs(), null);
        }
        if (e instanceof UnlockThrottledException ute) {
            return new Outcome(Kind.THROTTLED, ute.getFailedAttempts(), ute.getRetryAfterMs(), null);
        }
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new Outcome(Kind.ERROR, 0, 0, msg);
    }
}

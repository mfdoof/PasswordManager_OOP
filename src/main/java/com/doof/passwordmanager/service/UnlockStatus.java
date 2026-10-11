package com.doof.passwordmanager.service;

/** Unlock throttling state for the UI: consecutive failed attempts and how long until the next try is allowed. */
public final class UnlockStatus {
    private final int failedAttempts;
    private final long retryAfterMs;

    public UnlockStatus(int failedAttempts, long retryAfterMs) {
        this.failedAttempts = failedAttempts;
        this.retryAfterMs = retryAfterMs;
    }

    public int getFailedAttempts() { return failedAttempts; }

    /** 0 if an unlock attempt is allowed now. */
    public long getRetryAfterMs() { return retryAfterMs; }

    public boolean isThrottled() { return retryAfterMs > 0; }
}

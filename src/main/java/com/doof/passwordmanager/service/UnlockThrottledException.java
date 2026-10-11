package com.doof.passwordmanager.service;

/** Unlock refused without checking the password: still inside the delay after a failed attempt. */
public class UnlockThrottledException extends RuntimeException {
    private final UnlockStatus status;

    public UnlockThrottledException(UnlockStatus status) {
        super("Too many failed attempts. Try again in " + Math.max(1, (status.getRetryAfterMs() + 999) / 1000) + "s");
        this.status = status;
    }

    public int getFailedAttempts() { return status.getFailedAttempts(); }
    public long getRetryAfterMs() { return status.getRetryAfterMs(); }
}

package com.doof.passwordmanager.service;

/** Wrong master password. The failure has been recorded; the next attempt must wait {@link #getRetryAfterMs()}. */
public class InvalidPasswordException extends RuntimeException {
    private final UnlockStatus status;

    public InvalidPasswordException(UnlockStatus status) {
        super("Invalid master password");
        this.status = status;
    }

    public int getFailedAttempts() { return status.getFailedAttempts(); }
    public long getRetryAfterMs() { return status.getRetryAfterMs(); }
}

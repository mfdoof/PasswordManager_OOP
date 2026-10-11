package com.doof.passwordmanager.util;

import java.net.MalformedURLException;
import java.net.URL;

public final class InputValidator {

    private InputValidator() {}

    static final int MAX_USERNAME_LENGTH = 256;

    /** Any non-empty username (email, handle, account number...); no control characters. */
    public static void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("Username cannot be empty.");
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw new ValidationException("Username is too long (max " + MAX_USERNAME_LENGTH + " characters).");
        }
        if (username.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("Username cannot contain control characters.");
        }
    }

    /**
     * Rule for passwords stored in entries: whatever the site requires, so only non-empty,
     * at most {@link PasswordPolicy#MAX_LENGTH} chars and no control characters. Strength is
     * shown as guidance, not enforced. (The master password has its own policy.)
     */
    public static void validateEntryPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new ValidationException("Password cannot be empty.");
        }
        if (password.length > PasswordPolicy.MAX_LENGTH) {
            throw new ValidationException("Password is too long (max " + PasswordPolicy.MAX_LENGTH + " characters).");
        }
        for (char c : password) {
            if (Character.isISOControl(c)) {
                throw new ValidationException("Password cannot contain control characters.");
            }
        }
    }

    public static void validateWebsite(String website) {
        if (website == null || website.trim().isEmpty()) {
            throw new ValidationException("Website cannot be empty.");
        }
        website = website.trim();
        try {
            new URL("https://" + website);
        } catch (MalformedURLException e) {
            throw new ValidationException("Invalid website format.");
        }
    }

    public static String sanitize(String input) {
        return input == null ? null : input.trim();
    }
}

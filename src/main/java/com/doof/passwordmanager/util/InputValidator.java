package com.doof.passwordmanager.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

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

    public static void validatePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new ValidationException("Password cannot be empty.");
        }
        String pass = new String(password);
        if (!PASSWORD_PATTERN.matcher(pass).matches()) {
            throw new ValidationException("Password must be at least 8 characters long, with uppercase, lowercase, and a number.");
        }
        pass = null;
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

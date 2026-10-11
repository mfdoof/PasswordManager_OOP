package com.doof.passwordmanager.util;

import com.doof.passwordmanager.dao.AccountDao;
import com.doof.passwordmanager.model.Account;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

public final class InputValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private InputValidator() {}

    public static void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("Email cannot be empty.");
        }
        email = email.trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format.");
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

    public static void checkDuplicate(AccountDao accountDao, String email, String website) {
        List<Account> existing = accountDao.search(email, 1, 0);
        boolean duplicate = existing.stream()
                .anyMatch(acc -> acc.getEmail().equalsIgnoreCase(email.trim())
                        && acc.getWebsite().equalsIgnoreCase(website.trim()));
        if (duplicate) {
            throw new ValidationException("An account with this email and website already exists.");
        }
    }

    public static String sanitize(String input) {
        return input == null ? null : input.trim();
    }
}

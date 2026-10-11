package com.doof.passwordmanager.util;

/**
 * Master password rules. The master password is the only defense against offline guessing on a
 * copied vault file, so it has a stricter policy than stored entry passwords: at least 12
 * characters with upper case, lower case and a digit. A symbol is recommended, not required.
 */
public final class PasswordPolicy {

    public static final int MASTER_MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 1024;

    private PasswordPolicy() {}

    /** Which requirements a candidate meets; drives the live checklist in the UI. */
    public static final class Checks {
        public final boolean length;
        public final boolean upper;
        public final boolean lower;
        public final boolean digit;
        public final boolean symbol;

        Checks(boolean length, boolean upper, boolean lower, boolean digit, boolean symbol) {
            this.length = length;
            this.upper = upper;
            this.lower = lower;
            this.digit = digit;
            this.symbol = symbol;
        }

        /** Symbol is optional. */
        public boolean meetsMasterPolicy() {
            return length && upper && lower && digit;
        }
    }

    /** Checks {@code password} without copying it. */
    public static Checks check(char[] password) {
        boolean upper = false, lower = false, digit = false, symbol = false;
        int len = password == null ? 0 : password.length;
        for (int i = 0; i < len; i++) {
            char c = password[i];
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isLetter(c)) symbol = true;
        }
        return new Checks(len >= MASTER_MIN_LENGTH && len <= MAX_LENGTH, upper, lower, digit, symbol);
    }

    public static void validateMaster(char[] password) {
        if (password == null || password.length == 0) {
            throw new ValidationException("Master password cannot be empty.");
        }
        if (password.length > MAX_LENGTH) {
            throw new ValidationException("Master password is too long (max " + MAX_LENGTH + " characters).");
        }
        if (!check(password).meetsMasterPolicy()) {
            throw new ValidationException("Master password needs " + MASTER_MIN_LENGTH
                    + "+ characters with upper case, lower case and a digit.");
        }
    }
}

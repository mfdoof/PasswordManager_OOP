package com.doof.passwordmanager.ui.tui.widget;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Random passwords from SecureRandom with at least one character from every enabled set. */
public final class PasswordGenerator {

    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    public static final String DIGITS = "0123456789";
    /** No quotes, backslash or space: they break too many sites and shells. */
    public static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/~";

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    public static final int DEFAULT_LENGTH = 20;

    private PasswordGenerator() {}

    /**
     * @throws IllegalArgumentException if no set is enabled or the length is out of range
     */
    public static char[] generate(int length, boolean upper, boolean lower, boolean digits, boolean symbols,
                                  SecureRandom random) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("length must be " + MIN_LENGTH + "-" + MAX_LENGTH);
        }
        List<String> sets = new ArrayList<>(4);
        if (upper) sets.add(UPPER);
        if (lower) sets.add(LOWER);
        if (digits) sets.add(DIGITS);
        if (symbols) sets.add(SYMBOLS);
        if (sets.isEmpty()) {
            throw new IllegalArgumentException("enable at least one character set");
        }

        char[] out = new char[length];
        int i = 0;
        for (String set : sets) {  // guarantee one from each enabled set
            out[i++] = set.charAt(random.nextInt(set.length()));
        }
        String pool = String.join("", sets);
        while (i < length) {
            out[i++] = pool.charAt(random.nextInt(pool.length()));
        }
        for (int k = length - 1; k > 0; k--) {  // Fisher-Yates, so the guaranteed chars aren't at the front
            int j = random.nextInt(k + 1);
            char tmp = out[k];
            out[k] = out[j];
            out[j] = tmp;
        }
        return out;
    }
}

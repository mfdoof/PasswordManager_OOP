package com.doof.passwordmanager.ui.tui.widget;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class PasswordGeneratorTest {

    private final SecureRandom random = new SecureRandom();

    private static boolean any(char[] pw, String set) {
        for (char c : pw) if (set.indexOf(c) >= 0) return true;
        return false;
    }

    private static boolean only(char[] pw, String allowed) {
        for (char c : pw) if (allowed.indexOf(c) < 0) return false;
        return true;
    }

    @Test
    void containsOneOfEachEnabledSetEveryTime() {
        for (int i = 0; i < 500; i++) {
            char[] pw = PasswordGenerator.generate(8, true, true, true, true, random);
            assertEquals(8, pw.length);
            assertTrue(any(pw, PasswordGenerator.UPPER) && any(pw, PasswordGenerator.LOWER)
                    && any(pw, PasswordGenerator.DIGITS) && any(pw, PasswordGenerator.SYMBOLS), new String(pw));
        }
    }

    @Test
    void usesOnlyEnabledSets() {
        char[] pw = PasswordGenerator.generate(64, false, true, true, false, random);
        assertTrue(only(pw, PasswordGenerator.LOWER + PasswordGenerator.DIGITS), new String(pw));
        assertTrue(only(PasswordGenerator.generate(30, false, false, true, false, random), PasswordGenerator.DIGITS));
    }

    @Test
    void lengthRangeAndEmptySetsAreRefused() {
        assertEquals(128, PasswordGenerator.generate(128, true, true, true, true, random).length);
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(7, true, true, true, true, random));
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(129, true, true, true, true, random));
        assertThrows(IllegalArgumentException.class, () -> PasswordGenerator.generate(20, false, false, false, false, random));
    }

    @Test
    void outputsDiffer() {
        assertNotEquals(new String(PasswordGenerator.generate(20, true, true, true, true, random)),
                new String(PasswordGenerator.generate(20, true, true, true, true, random)));
    }
}

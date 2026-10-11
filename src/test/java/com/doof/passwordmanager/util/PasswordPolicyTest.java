package com.doof.passwordmanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void checksEachRequirement() {
        PasswordPolicy.Checks c = PasswordPolicy.check("abc".toCharArray());
        assertFalse(c.length);
        assertTrue(c.lower);
        assertFalse(c.upper || c.digit || c.symbol);

        PasswordPolicy.Checks full = PasswordPolicy.check("Correct horse 9".toCharArray());
        assertTrue(full.length && full.upper && full.lower && full.digit && full.symbol);
        assertTrue(full.meetsMasterPolicy());
    }

    @Test
    void symbolIsOptional() {
        assertTrue(PasswordPolicy.check("MasterPass12".toCharArray()).meetsMasterPolicy());
    }

    @Test
    void validateMasterRejectsWeakPasswords() {
        for (String bad : new String[]{"", "Short1Aa", "alllowercase123", "ALLUPPERCASE123", "NoDigitsHereAtAll"}) {
            assertThrows(ValidationException.class, () -> PasswordPolicy.validateMaster(bad.toCharArray()), bad);
        }
        assertDoesNotThrow(() -> PasswordPolicy.validateMaster("MasterPass12".toCharArray()));
    }
}

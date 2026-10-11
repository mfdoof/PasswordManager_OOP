package com.doof.passwordmanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    @Test
    void anyNonEmptyUsernameIsAccepted() {
        for (String ok : new String[]{"me@example.com", "mave", "Mave Doof", "1234-5678", "ユーザー", "a"}) {
            assertDoesNotThrow(() -> InputValidator.validateUsername(ok), ok);
        }
    }

    @Test
    void emptyLongOrControlCharUsernamesAreRejected() {
        String tooLong = "x".repeat(InputValidator.MAX_USERNAME_LENGTH + 1);
        for (String bad : new String[]{null, "", "   ", tooLong, "user\nname", "tab\tbed"}) {
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername(bad), String.valueOf(bad));
        }
    }
}

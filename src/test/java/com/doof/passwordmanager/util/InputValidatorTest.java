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
    void entryPasswordsOnlyNeedToBeNonEmptyWithoutControlChars() {
        for (String ok : new String[]{"1234", "abc", "correct horse battery staple", "P@ss"}) {
            assertDoesNotThrow(() -> InputValidator.validateEntryPassword(ok.toCharArray()), ok);
        }
        assertThrows(ValidationException.class, () -> InputValidator.validateEntryPassword(new char[0]));
        assertThrows(ValidationException.class, () -> InputValidator.validateEntryPassword("a\nb".toCharArray()));
        assertThrows(ValidationException.class,
                () -> InputValidator.validateEntryPassword(new char[PasswordPolicy.MAX_LENGTH + 1]));
    }

    @Test
    void emptyLongOrControlCharUsernamesAreRejected() {
        String tooLong = "x".repeat(InputValidator.MAX_USERNAME_LENGTH + 1);
        for (String bad : new String[]{null, "", "   ", tooLong, "user\nname", "tab\tbed"}) {
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername(bad), String.valueOf(bad));
        }
    }
}

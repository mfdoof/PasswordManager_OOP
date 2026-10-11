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
    void websitesAreCheckedForSpacesControlCharsAndLength() {
        for (String ok : new String[]{"github.com", "gitea.doof.quest", "example.com/login", "localhost:8080", "192.168.1.10"}) {
            assertDoesNotThrow(() -> InputValidator.validateWebsite(ok), ok);
        }
        String tooLong = "a".repeat(InputValidator.MAX_WEBSITE_LENGTH + 1);
        for (String bad : new String[]{"", "   ", "a site", "tab\tsite", "new\nline", tooLong, "example.com:abc"}) {
            assertThrows(ValidationException.class, () -> InputValidator.validateWebsite(bad), bad);
        }
        assertDoesNotThrow(() -> InputValidator.validateWebsite("  github.com  "), "surrounding spaces are trimmed");
    }

    @Test
    void emptyLongOrControlCharUsernamesAreRejected() {
        String tooLong = "x".repeat(InputValidator.MAX_USERNAME_LENGTH + 1);
        for (String bad : new String[]{null, "", "   ", tooLong, "user\nname", "tab\tbed"}) {
            assertThrows(ValidationException.class, () -> InputValidator.validateUsername(bad), String.valueOf(bad));
        }
    }
}

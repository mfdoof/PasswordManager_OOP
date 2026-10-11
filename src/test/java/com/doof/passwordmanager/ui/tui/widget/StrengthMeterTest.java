package com.doof.passwordmanager.ui.tui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrengthMeterTest {

    @Test
    void emptyIsNone() {
        assertEquals(StrengthMeter.Level.NONE, StrengthMeter.level(new char[0]));
        assertEquals("[----------]", StrengthMeter.bar(new char[0]));
    }

    @Test
    void lengthAndClassesRaiseTheLevel() {
        assertEquals(StrengthMeter.Level.WEAK, StrengthMeter.level("abc123".toCharArray()));
        assertEquals(StrengthMeter.Level.STRONG, StrengthMeter.level("MasterPass12".toCharArray()));
        assertEquals(StrengthMeter.Level.VERY_STRONG, StrengthMeter.level("Gx7!kP2#qR9@mT4$wZ1&".toCharArray()));
        assertTrue(StrengthMeter.cells("Gx7!kP2#qR9@mT4$wZ1&".toCharArray()) == StrengthMeter.BAR_CELLS);
    }

    @Test
    void barHasFixedWidth() {
        for (String s : new String[]{"a", "abcdefgh", "Gx7!kP2#qR9@mT4$wZ1&Gx7!kP2#"}) {
            assertEquals(StrengthMeter.BAR_CELLS + 2, StrengthMeter.bar(s.toCharArray()).length());
        }
    }
}

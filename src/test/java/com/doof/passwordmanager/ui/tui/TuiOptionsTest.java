package com.doof.passwordmanager.ui.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TuiOptionsTest {

    @Test
    void defaultsAreAllOff() {
        TuiOptions o = TuiOptions.parse(new String[]{});
        assertFalse(o.ascii());
        assertFalse(o.noColor());
        assertFalse(o.swing());
    }

    @Test
    void parsesFlagsAndSkipsVaultValue() {
        TuiOptions o = TuiOptions.parse(new String[]{"--vault", "/tmp/--ascii.db", "--no-color", "--swing"});
        assertFalse(o.ascii(), "the --vault value must not be read as a flag");
        assertTrue(o.noColor());
        assertTrue(o.swing());

        assertTrue(TuiOptions.parse(new String[]{"--vault=/tmp/v.db", "--ascii"}).ascii());
    }

    @Test
    void rejectsUnknownArguments() {
        assertThrows(IllegalArgumentException.class, () -> TuiOptions.parse(new String[]{"--colour"}));
        assertThrows(IllegalArgumentException.class, () -> TuiOptions.parse(new String[]{"stray"}));
    }
}

package com.doof.passwordmanager.ui.tui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusBarTest {

    @Test
    void okMessagesDismissAfterFiveSecondsNotOnKeypress() {
        StatusBar s = new StatusBar();
        s.show("ok: saved", 1_000);
        s.onKey();
        assertEquals("ok: saved", s.text());
        assertFalse(s.tick(5_999));
        assertTrue(s.tick(6_000));
        assertNull(s.text());
    }

    @Test
    void errorsStayUntilTheNextKeypress() {
        StatusBar s = new StatusBar();
        s.show("err: nope", 1_000);
        assertFalse(s.tick(60_000));
        assertEquals("err: nope", s.text());
        s.onKey();
        assertNull(s.text());
    }
}

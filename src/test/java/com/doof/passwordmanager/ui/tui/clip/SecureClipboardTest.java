package com.doof.passwordmanager.ui.tui.clip;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SecureClipboardTest {

    static final class Fake implements ClipboardBackend {
        String text;
        boolean failWrites;

        @Override public void setText(String t) {
            if (failWrites) throw new IllegalStateException("busy");
            text = t;
        }

        @Override public String getText() { return text; }
    }

    @Test
    void secretIsClearedWhenDueAndCallerArrayIsUntouched() {
        Fake f = new Fake();
        SecureClipboard c = new SecureClipboard(() -> f);
        char[] pw = "s3cret".toCharArray();

        assertTrue(c.copySecret(pw, 1_000));
        assertArrayEquals("s3cret".toCharArray(), pw);
        assertEquals("s3cret", f.text);
        assertEquals(20, c.secondsUntilClear(1_000));
        assertFalse(c.tick(20_999));
        assertTrue(c.tick(21_000));
        assertEquals("", f.text);
        assertFalse(c.hasPendingClear());
    }

    @Test
    void doesNotClearSomethingElse() {
        Fake f = new Fake();
        SecureClipboard c = new SecureClipboard(() -> f);
        c.copySecret("s3cret".toCharArray(), 0);
        f.text = "other";
        c.clearNow();
        assertEquals("other", f.text);
        assertFalse(c.hasPendingClear());
    }

    @Test
    void probeRunsOnceAndFailuresMeanUnavailable() {
        AtomicInteger probes = new AtomicInteger();
        SecureClipboard none = new SecureClipboard(() -> { probes.incrementAndGet(); return null; });
        assertFalse(none.available());
        assertFalse(none.available());
        assertEquals(1, probes.get());
        assertFalse(none.copySecret("x".toCharArray(), 0));
        assertFalse(none.copyPlain("x"));

        SecureClipboard broken = new SecureClipboard(() -> { throw new java.awt.AWTError("no display"); });
        assertFalse(broken.available());
    }

    @Test
    void failedWriteReportsFalseAndSchedulesNothing() {
        Fake f = new Fake();
        f.failWrites = true;
        SecureClipboard c = new SecureClipboard(() -> f);
        assertFalse(c.copySecret("x".toCharArray(), 0));
        assertFalse(c.hasPendingClear());
    }

    @Test
    void awtProbeTreatsSshWithoutDisplayAsUnavailable() {
        assertTrue(AwtClipboardBackend.isSshWithoutDisplay(Map.of("SSH_CONNECTION", "1.2.3.4 5 6.7.8.9 22")));
        assertTrue(AwtClipboardBackend.isSshWithoutDisplay(Map.of("SSH_TTY", "/dev/ttys001")));
        assertFalse(AwtClipboardBackend.isSshWithoutDisplay(Map.of("SSH_TTY", "/dev/pts/1", "DISPLAY", "localhost:10.0")));
        assertFalse(AwtClipboardBackend.isSshWithoutDisplay(Map.of()));
        assertNull(AwtClipboardBackend.probe(Map.of("SSH_TTY", "/dev/ttys001")));
    }

    @Test
    void awtProbeIsNullWhenHeadlessAndNeverThrows() {
        // Surefire runs with -Djava.awt.headless=true.
        assertTrue(java.awt.GraphicsEnvironment.isHeadless());
        assertNull(AwtClipboardBackend.probe(Map.of()));
    }
}

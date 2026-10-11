package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    private static final TuiOptions NONE = TuiOptions.parse(new String[]{});
    private static final Map<String, String> UTF8 = Map.of("LANG", "en_US.UTF-8");

    @Test
    void noColorEnvOrFlagGivesMonochrome() {
        assertEquals(Theme.ColorMode.NONE, Theme.colorMode(false, Map.of("NO_COLOR", "1")));
        assertEquals(Theme.ColorMode.NONE, Theme.colorMode(true, Map.of()));
        assertEquals(Theme.ColorMode.NONE, Theme.colorMode(false, Map.of("TERM", "dumb")));
        // no-color.org: only a non-empty value counts.
        assertNotEquals(Theme.ColorMode.NONE, Theme.colorMode(false, Map.of("NO_COLOR", "")));

        Theme mono = Theme.detect(TuiOptions.parse(new String[]{"--no-color"}), UTF8);
        assertEquals(TextColor.ANSI.DEFAULT, mono.primary);
        assertEquals(TextColor.ANSI.DEFAULT, mono.background);
    }

    @Test
    void truecolorUsesExactPalette() {
        Theme t = Theme.detect(NONE, Map.of("COLORTERM", "truecolor", "LANG", "en_US.UTF-8"));
        assertEquals(Theme.ColorMode.TRUECOLOR, t.colorMode());
        assertEquals(new TextColor.RGB(0x0d, 0x0b, 0x14), t.background);
        assertEquals(new TextColor.RGB(0xe0, 0xd7, 0xf5), t.text);
        assertEquals(new TextColor.RGB(0xa2, 0x77, 0xff), t.primary);
        assertEquals(new TextColor.RGB(0x6b, 0x5b, 0x95), t.dim);
        assertEquals(new TextColor.RGB(0x61, 0xff, 0xca), t.success);
        assertEquals(new TextColor.RGB(0xff, 0x61, 0x88), t.error);
    }

    @Test
    void otherwiseFallsBackTo256Colors() {
        Theme t = Theme.detect(NONE, UTF8);
        assertEquals(Theme.ColorMode.INDEXED_256, t.colorMode());
        assertTrue(t.primary instanceof TextColor.Indexed);
        assertNotEquals(t.primary, t.dim);
    }

    @Test
    void unicodeBordersOnlyWithUtf8LocaleAndNoAsciiFlag() {
        assertTrue(Theme.detect(NONE, UTF8).unicode());
        assertEquals('─', Theme.detect(NONE, UTF8).horizontal);

        assertFalse(Theme.detect(NONE, Map.of("LANG", "C")).unicode());
        assertFalse(Theme.detect(NONE, Map.of()).unicode());
        assertFalse(Theme.detect(TuiOptions.parse(new String[]{"--ascii"}), UTF8).unicode());

        Theme ascii = Theme.detect(NONE, Map.of("LANG", "C"));
        assertEquals('-', ascii.horizontal);
        assertEquals('|', ascii.vertical);
        assertEquals('+', ascii.topLeft);
    }

    @Test
    void lcAllOverridesLang() {
        assertFalse(Theme.unicodeLocale(Map.of("LC_ALL", "C", "LANG", "en_US.UTF-8")));
        assertTrue(Theme.unicodeLocale(Map.of("LC_CTYPE", "en_US.utf8", "LANG", "C")));
    }
}

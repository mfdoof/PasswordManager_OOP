package com.doof.passwordmanager.ui.tui;

import com.doof.passwordmanager.ui.tui.screen.PlaceholderScreen;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScreenManagerTest {

    private static final Theme THEME = Theme.detect(TuiOptions.parse(new String[]{}), Map.of("LANG", "en_US.UTF-8"));

    private DefaultVirtualTerminal terminal;
    private TerminalScreen screen;
    private ScreenManager manager;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        manager = new ScreenManager(screen, THEME, new PlaceholderScreen());
    }

    @AfterEach
    void tearDown() throws Exception {
        screen.stopScreen();
    }

    private String row(int r) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < terminal.getTerminalSize().getColumns(); c++) {
            sb.append(terminal.getBufferCharacter(c, r).getCharacterString());
        }
        return sb.toString();
    }

    private String all() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < terminal.getTerminalSize().getRows(); r++) sb.append(row(r)).append('\n');
        return sb.toString();
    }

    @Test
    void drawsThemedFrameWithTitleAndFooter() throws Exception {
        manager.render();

        assertTrue(row(0).startsWith("┌─ doof vault "), row(0));
        assertTrue(row(23).startsWith("└") && row(23).endsWith("┘"), row(23));
        assertTrue(all().contains("[q] quit"));
        assertEquals(THEME.primary, terminal.getBufferCharacter(3, 0).getForegroundColor());
        assertEquals(THEME.background, terminal.getBufferCharacter(40, 10).getBackgroundColor());
    }

    @Test
    void tooSmallTerminalShowsSizeMessageAndReflowsWhenResized() throws Exception {
        terminal.setTerminalSize(new TerminalSize(60, 20));
        screen.doResizeIfNecessary();
        manager.render();
        assertTrue(all().contains("terminal too small (need 80x24, have 60x20)"), all());
        assertTrue(all().contains("[ctrl+c] to quit"));
        assertFalse(all().contains("doof vault"));

        terminal.setTerminalSize(new TerminalSize(100, 30));
        screen.doResizeIfNecessary();
        manager.render();
        assertTrue(row(0).contains("doof vault"));
        assertTrue(row(29).endsWith("┘"), "frame reflows to the new size");
    }

    @Test
    void qQuitsButIsIgnoredWhileTooSmall() {
        terminal.setTerminalSize(new TerminalSize(40, 10));
        screen.doResizeIfNecessary();
        manager.handleKey(new KeyStroke('q', false, false));
        assertFalse(manager.isQuitRequested());

        terminal.setTerminalSize(new TerminalSize(80, 24));
        screen.doResizeIfNecessary();
        manager.handleKey(new KeyStroke('x', false, false));
        assertFalse(manager.isQuitRequested());
        manager.handleKey(new KeyStroke('q', false, false));
        assertTrue(manager.isQuitRequested());
    }

    @Test
    void ctrlCAndEofQuitEvenWhenTooSmall() {
        terminal.setTerminalSize(new TerminalSize(40, 10));
        screen.doResizeIfNecessary();
        manager.handleKey(new KeyStroke('c', true, false));
        assertTrue(manager.isQuitRequested());

        ScreenManager other = new ScreenManager(screen, THEME, new PlaceholderScreen());
        other.handleKey(new KeyStroke(KeyType.EOF));
        assertTrue(other.isQuitRequested());
    }

    @Test
    void clearBlanksTheScreen() throws Exception {
        manager.render();
        manager.clear();
        assertTrue(all().isBlank(), all());
    }

    @Test
    void runLoopExitsOnQAndRestoresTheTerminal() {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        vt.addInput(new KeyStroke('x', false, false));
        vt.addInput(new KeyStroke('q', false, false));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> TuiApp.run(vt, THEME));

        // Back on the normal buffer, which never held vault content.
        for (int r = 0; r < 24; r++) {
            for (int c = 0; c < 80; c++) {
                assertEquals(" ", vt.getBufferCharacter(c, r).getCharacterString());
            }
        }
    }
}

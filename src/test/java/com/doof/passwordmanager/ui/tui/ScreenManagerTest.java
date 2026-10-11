package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenManagerTest {

    private static final Theme THEME = TuiHarness.THEME;

    /** Minimal screen: titled frame, q quits, counts onHide calls. */
    private static final class StubScreen implements TuiScreen {
        int hidden;

        @Override
        public void draw(TextGraphics g, TerminalSize size, Theme t) {
            g.setForegroundColor(t.dim);
            Draw.box(g, t, 0, 0, size.getColumns(), size.getRows(), null);
            g.setForegroundColor(t.primary);
            g.putString(2, 0, " doof vault ");
        }

        @Override
        public Action handleKey(KeyStroke key) {
            return key.getCharacter() != null && key.getCharacter() == 'q' ? Action.QUIT : Action.NONE;
        }

        @Override
        public void onHide() {
            hidden++;
        }
    }

    private DefaultVirtualTerminal terminal;
    private TerminalScreen screen;
    private StubScreen stub;
    private ScreenManager manager;

    @BeforeEach
    void setUp() throws Exception {
        terminal = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        stub = new StubScreen();
        manager = new ScreenManager(screen, THEME, stub);
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
    void rendersCurrentScreenOnThemedBackground() throws Exception {
        manager.render();
        assertTrue(row(0).startsWith("┌─ doof vault "), row(0));
        assertEquals(THEME.primary, terminal.getBufferCharacter(3, 0).getForegroundColor());
        assertEquals(THEME.background, terminal.getBufferCharacter(40, 10).getBackgroundColor());
        assertFalse(manager.isDirty());
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
    void keysAreIgnoredWhileTooSmall() {
        terminal.setTerminalSize(new TerminalSize(40, 10));
        screen.doResizeIfNecessary();
        manager.handleKey(new KeyStroke('q', false, false));
        assertFalse(manager.isQuitRequested());

        terminal.setTerminalSize(new TerminalSize(80, 24));
        screen.doResizeIfNecessary();
        manager.handleKey(new KeyStroke('q', false, false));
        assertTrue(manager.isQuitRequested());
    }

    @Test
    void ctrlCAndEofQuitEvenWhenTooSmall() {
        terminal.setTerminalSize(new TerminalSize(40, 10));
        screen.doResizeIfNecessary();
        manager.handleKey(new KeyStroke('c', true, false));
        assertTrue(manager.isQuitRequested());

        ScreenManager other = new ScreenManager(screen, THEME, new StubScreen());
        other.handleKey(new KeyStroke(KeyType.EOF));
        assertTrue(other.isQuitRequested());
    }

    @Test
    void switchingScreensHidesTheOldOneAndMarksDirty() throws Exception {
        manager.render();
        StubScreen next = new StubScreen();
        manager.show(next);
        assertEquals(1, stub.hidden);
        assertTrue(manager.isDirty());
        assertSame(next, manager.current());
    }

    @Test
    void shutdownHidesCurrentAndBlanksTheScreen() throws Exception {
        manager.render();
        manager.shutdown();
        assertEquals(1, stub.hidden);
        assertTrue(all().isBlank(), all());
    }
}

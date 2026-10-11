package com.doof.passwordmanager.ui.tui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TuiAppTest {

    @TempDir
    Path tmp;

    @Test
    void runLoopExitsOnEscAndRestoresTheTerminal() {
        TuiHarness h = new TuiHarness(tmp.resolve("vault.db"));
        h.close();
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        vt.addInput(new KeyStroke('x', false, false));
        vt.addInput(new KeyStroke(KeyType.Escape));

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> TuiApp.run(vt, TuiHarness.THEME, h.ctx));

        for (int r = 0; r < 24; r++) {
            for (int c = 0; c < 80; c++) {
                assertEquals(" ", vt.getBufferCharacter(c, r).getCharacterString());
            }
        }
    }

    @Test
    void displayPathShortensHome() {
        assertEquals("~/.doof/vault.db", TuiApp.displayPath(Path.of("/home/mave/.doof/vault.db"), "/home/mave"));
        assertEquals("/home/maverick/v.db", TuiApp.displayPath(Path.of("/home/maverick/v.db"), "/home/mave"));
        assertEquals("/srv/v.db", TuiApp.displayPath(Path.of("/srv/v.db"), "/home/mave"));
    }
}

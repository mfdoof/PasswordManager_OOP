package com.doof.passwordmanager.ui.tui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretFieldTest {

    private static SecretField of(String s) {
        SecretField f = new SecretField();
        for (char c : s.toCharArray()) f.append(c);
        return f;
    }

    @Test
    void editsAndMasks() {
        SecretField f = of("secret");
        f.backspace();
        assertEquals(5, f.length());
        assertEquals("*****", f.masked(40));
        assertEquals("***", f.masked(3));
        assertArrayEquals("secre".toCharArray(), f.peek());
    }

    @Test
    void takeHandsOverAndWipes() {
        SecretField f = of("secret");
        char[] live = f.buffer();
        char[] out = f.take();
        assertArrayEquals("secret".toCharArray(), out);
        assertTrue(f.isEmpty());
        for (char c : live) assertEquals('\0', c);
    }

    @Test
    void growingWipesTheOldBuffer() {
        SecretField f = new SecretField();
        char[] first = f.buffer();
        for (int i = 0; i < first.length + 1; i++) f.append('x');
        assertNotSame(first, f.buffer());
        for (char c : first) assertEquals('\0', c, "discarded buffer must be wiped");
    }

    @Test
    void backspaceWipesTheRemovedChar() {
        SecretField f = of("ab");
        f.backspace();
        assertEquals('\0', f.buffer()[1]);
    }

    @Test
    void lengthIsCapped() {
        SecretField f = new SecretField();
        for (int i = 0; i < SecretField.MAX_LENGTH + 10; i++) f.append('x');
        assertEquals(SecretField.MAX_LENGTH, f.length());
    }

    @Test
    void contentEquals() {
        assertTrue(of("abc").contentEquals(of("abc")));
        assertFalse(of("abc").contentEquals(of("abd")));
        assertFalse(of("abc").contentEquals(of("ab")));
    }
}

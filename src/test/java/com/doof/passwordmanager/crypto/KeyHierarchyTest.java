package com.doof.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class KeyHierarchyTest {

    @Test
    void hkdfMatchesRfc5869TestCase3() {
        // RFC 5869 A.3: SHA-256, zero-length salt and info.
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] okm = KeyHierarchy.hkdf(ikm, new byte[0], 42);
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
                HexFormat.of().formatHex(okm));
    }

    @Test
    void authAndEncKeysAreDistinctAndDeterministic() {
        byte[] master = new byte[32];
        Arrays.fill(master, (byte) 0x42);

        byte[] auth = KeyHierarchy.authKey(master);
        byte[] enc = KeyHierarchy.encKey(master);

        assertEquals(32, auth.length);
        assertEquals(32, enc.length);
        assertFalse(Arrays.equals(auth, enc));
        assertFalse(Arrays.equals(master, auth));
        assertFalse(Arrays.equals(master, enc));
        assertArrayEquals(auth, KeyHierarchy.authKey(master));
        assertArrayEquals(enc, KeyHierarchy.encKey(master));
    }
}

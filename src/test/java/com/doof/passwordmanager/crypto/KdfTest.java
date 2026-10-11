package com.doof.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KdfTest {

    private static final KdfParams FAST = new KdfParams(1024, 1, 1);
    private static final byte[] SALT = new byte[Kdf.SALT_LENGTH];

    @Test
    void deterministicForSameInputs() {
        byte[] a = Kdf.deriveMasterKey("correct horse".toCharArray(), SALT, FAST);
        byte[] b = Kdf.deriveMasterKey("correct horse".toCharArray(), SALT, FAST);
        assertEquals(KdfParams.KEY_LENGTH, a.length);
        assertArrayEquals(a, b);
    }

    @Test
    void passwordSaltAndParamsAllAffectTheKey() {
        byte[] base = Kdf.deriveMasterKey("pw".toCharArray(), SALT, FAST);
        byte[] otherSalt = SALT.clone();
        otherSalt[0] = 1;

        assertNotEquals(hex(base), hex(Kdf.deriveMasterKey("pW".toCharArray(), SALT, FAST)));
        assertNotEquals(hex(base), hex(Kdf.deriveMasterKey("pw".toCharArray(), otherSalt, FAST)));
        assertNotEquals(hex(base), hex(Kdf.deriveMasterKey("pw".toCharArray(), SALT, new KdfParams(1024, 2, 1))));
        assertNotEquals(hex(base), hex(Kdf.deriveMasterKey("pw".toCharArray(), SALT, new KdfParams(2048, 1, 1))));
    }

    @Test
    void doesNotModifyPassword() {
        char[] pw = "secret".toCharArray();
        Kdf.deriveMasterKey(pw, SALT, FAST);
        assertArrayEquals("secret".toCharArray(), pw);
    }

    @Test
    void newSaltIsRandom() {
        assertEquals(Kdf.SALT_LENGTH, Kdf.newSalt().length);
        assertNotEquals(hex(Kdf.newSalt()), hex(Kdf.newSalt()));
    }

    // --- KdfParams JSON ---

    @Test
    void defaultsMatchArchitecture() {
        assertEquals(64 * 1024, KdfParams.DEFAULT.getMemoryKiB());
        assertEquals(3, KdfParams.DEFAULT.getIterations());
        assertEquals(1, KdfParams.DEFAULT.getParallelism());
    }

    @Test
    void paramsJsonRoundTrip() {
        KdfParams p = new KdfParams(19 * 1024, 2, 4);
        String json = p.toJson();
        assertTrue(json.contains("\"algorithm\":\"argon2id\""), json);
        assertEquals(p, KdfParams.fromJson(json));
    }

    @Test
    void paramsJsonRejectsBadInput() {
        String ok = "{\"algorithm\":\"argon2id\",\"version\":19,\"memoryKiB\":1024,\"iterations\":1,\"parallelism\":1,\"keyLength\":32}";
        assertEquals(new KdfParams(1024, 1, 1), KdfParams.fromJson(ok));

        String[] bad = {
                ok.replace("argon2id", "pbkdf2"),
                ok.replace("\"version\":19", "\"version\":16"),
                ok.replace("\"keyLength\":32", "\"keyLength\":16"),
                ok.replace("\"iterations\":1,", ""),
                ok.replace("\"iterations\":1", "\"iterations\":\"1\""),
                ok.replace("\"iterations\":1", "\"iterations\":0"),
                ok.replace("\"memoryKiB\":1024", "\"memoryKiB\":4"),
                ok.replace("\"memoryKiB\":1024", "\"memoryKiB\":99999999999"),
                ok.replace("\"parallelism\":1", "\"parallelism\":64"),
                "not json",
        };
        for (String json : bad) {
            assertThrows(IllegalArgumentException.class, () -> KdfParams.fromJson(json), json);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}

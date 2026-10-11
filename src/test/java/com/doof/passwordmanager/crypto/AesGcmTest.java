package com.doof.passwordmanager.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmTest {

    private static final byte[] KEY = filled(32, 7);
    private static final byte[] AAD = "doof-entry-1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PLAIN = "{\"password\":\"Hunter2Pass\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTrip() {
        AesGcm.Sealed s = AesGcm.encrypt(KEY, PLAIN, AAD);
        assertEquals(AesGcm.IV_LENGTH, s.iv.length);
        assertEquals(PLAIN.length + 16, s.ciphertext.length);  // + 128-bit tag
        assertArrayEquals(PLAIN, AesGcm.decrypt(KEY, s.iv, s.ciphertext, AAD));
    }

    @Test
    void freshIvEveryEncryption() {
        AesGcm.Sealed a = AesGcm.encrypt(KEY, PLAIN, AAD);
        AesGcm.Sealed b = AesGcm.encrypt(KEY, PLAIN, AAD);
        assertFalse(Arrays.equals(a.iv, b.iv));
        assertFalse(Arrays.equals(a.ciphertext, b.ciphertext));
    }

    @Test
    void wrongKeyFails() {
        AesGcm.Sealed s = AesGcm.encrypt(KEY, PLAIN, AAD);
        assertThrows(CryptoException.class, () -> AesGcm.decrypt(filled(32, 8), s.iv, s.ciphertext, AAD));
    }

    @Test
    void tamperedCiphertextFails() {
        AesGcm.Sealed s = AesGcm.encrypt(KEY, PLAIN, AAD);
        for (int i = 0; i < s.ciphertext.length; i++) {
            byte[] ct = s.ciphertext.clone();
            ct[i] ^= 0x01;
            assertThrows(CryptoException.class, () -> AesGcm.decrypt(KEY, s.iv, ct, AAD), "byte " + i);
        }
    }

    @Test
    void tamperedIvFails() {
        AesGcm.Sealed s = AesGcm.encrypt(KEY, PLAIN, AAD);
        byte[] iv = s.iv.clone();
        iv[0] ^= 0x01;
        assertThrows(CryptoException.class, () -> AesGcm.decrypt(KEY, iv, s.ciphertext, AAD));
    }

    @Test
    void wrongAadFails() {
        AesGcm.Sealed s = AesGcm.encrypt(KEY, PLAIN, AAD);
        byte[] otherAad = "doof-entry-2".getBytes(StandardCharsets.US_ASCII);
        assertThrows(CryptoException.class, () -> AesGcm.decrypt(KEY, s.iv, s.ciphertext, otherAad));
    }

    @Test
    void rejectsBadKeyAndIvLengths() {
        assertThrows(IllegalArgumentException.class, () -> AesGcm.encrypt(new byte[16], PLAIN, AAD));
        AesGcm.Sealed s = AesGcm.encrypt(KEY, PLAIN, AAD);
        assertThrows(CryptoException.class, () -> AesGcm.decrypt(KEY, new byte[8], s.ciphertext, AAD));
    }

    @Test
    void doesNotModifyPlaintext() {
        byte[] plain = PLAIN.clone();
        AesGcm.encrypt(KEY, plain, AAD);
        assertArrayEquals(PLAIN, plain);
    }

    private static byte[] filled(int len, int value) {
        byte[] b = new byte[len];
        Arrays.fill(b, (byte) value);
        return b;
    }
}

package com.doof.passwordmanager.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * AES-256-GCM with a fresh random 12-byte IV per encryption and a 128-bit tag.
 * Keys are passed as raw bytes owned by the caller so they can be wiped; note the JCA
 * still copies the key internally (SecretKeySpec / cipher state), which we cannot zero.
 */
public final class AesGcm {

    public static final int IV_LENGTH = 12;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final SecureRandom secureRandom = new SecureRandom();

    private AesGcm() {}

    public static final class Sealed {
        public final byte[] iv;
        public final byte[] ciphertext;

        Sealed(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }

    /** Encrypts {@code plaintext} bound to {@code aad}. Does not modify {@code plaintext}. */
    public static Sealed encrypt(byte[] key, byte[] plaintext, byte[] aad) {
        checkKey(key);
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            return new Sealed(iv, cipher.doFinal(plaintext));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Decrypts and authenticates. Throws {@link CryptoException} on wrong key, tampering, or wrong AAD. */
    public static byte[] decrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] aad) {
        checkKey(key);
        if (iv == null || iv.length != IV_LENGTH) {
            throw new CryptoException("Invalid IV", null);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new CryptoException("Authentication failed: wrong key or tampered data", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("AES-GCM decryption failed", e);
        }
    }

    private static void checkKey(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes");
        }
    }
}

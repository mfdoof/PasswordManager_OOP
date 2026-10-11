package com.doof.passwordmanager.crypto;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import java.nio.charset.StandardCharsets;

/**
 * Splits the master key with HKDF-SHA256 into two independent keys, so the stored
 * auth key reveals nothing about the encryption key (ARCHITECTURE §5).
 */
public final class KeyHierarchy {

    static final byte[] AUTH_INFO = "doof-auth".getBytes(StandardCharsets.US_ASCII);
    static final byte[] ENC_INFO = "doof-enc".getBytes(StandardCharsets.US_ASCII);
    public static final int KEY_LENGTH = 32;

    private KeyHierarchy() {}

    /** Key whose value is stored in {@code vault_metadata.auth_hash} and compared on unlock. */
    public static byte[] authKey(byte[] masterKey) {
        return hkdf(masterKey, AUTH_INFO, KEY_LENGTH);
    }

    /** AES-256 entry encryption key. Kept in memory only. */
    public static byte[] encKey(byte[] masterKey) {
        return hkdf(masterKey, ENC_INFO, KEY_LENGTH);
    }

    // No HKDF salt: the master key is already salted and uniformly random (Argon2id output).
    static byte[] hkdf(byte[] ikm, byte[] info, int length) {
        HKDFBytesGenerator gen = new HKDFBytesGenerator(new SHA256Digest());
        gen.init(new HKDFParameters(ikm, null, info));
        byte[] out = new byte[length];
        gen.generateBytes(out, 0, length);
        return out;
    }
}

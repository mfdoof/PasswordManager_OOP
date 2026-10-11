package com.doof.passwordmanager.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.security.SecureRandom;

/** Argon2id master-key derivation (Bouncy Castle). */
public final class Kdf {

    public static final int SALT_LENGTH = 16;

    private static final SecureRandom secureRandom = new SecureRandom();

    private Kdf() {}

    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    /** Derives the 32-byte master key. The caller owns (and must wipe) the result; {@code password} is not modified. */
    public static byte[] deriveMasterKey(char[] password, byte[] salt, KdfParams params) {
        Argon2Parameters argon = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(KdfParams.VERSION)
                .withMemoryAsKB(params.getMemoryKiB())
                .withIterations(params.getIterations())
                .withParallelism(params.getParallelism())
                .withSalt(salt)
                .build();
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(argon);

        byte[] pwBytes = SecureWipe.toUtf8(password);
        byte[] out = new byte[KdfParams.KEY_LENGTH];
        try {
            gen.generateBytes(pwBytes, out);
            return out;
        } finally {
            SecureWipe.wipe(pwBytes);
        }
    }
}

package com.doof.passwordmanager.util;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;


public class PasswordHasher {

    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int KEY_LENGTH = 32;
    private static final int ITERATIONS = 100_000;

    private static final SecureRandom secureRandom = new SecureRandom();

    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    public static byte[] hashPassword(char[] password, byte[] salt) {
        return pbkdf2(password, salt, ITERATIONS, HASH_LENGTH);
    }

    public static boolean verifyPassword(char[] candidatePassword, byte[] salt, byte[] expectedHash) {
        byte[] candidateHash = hashPassword(candidatePassword, salt);
        boolean matches = constantTimeEquals(candidateHash, expectedHash);
        Arrays.fill(candidateHash, (byte) 0);
        return matches;
    }

    public static SecretKey deriveKey(char[] password, byte[] salt) {
        byte[] keyBytes = pbkdf2(password, salt, ITERATIONS, KEY_LENGTH);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
        return secretKey;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 key derivation failed", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    public static void wipe(char[] array) {
        if (array != null) Arrays.fill(array, '\0');
    }

    public static void wipe(byte[] array) {
        if (array != null) Arrays.fill(array, (byte) 0);
    }

    public static String toBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static String getDefaultKdfParamsJson() {
        return String.format("{\"algorithm\":\"%s\",\"iterations\":%d,\"saltLength\":%d,\"keyLength\":%d}",
                PBKDF2_ALGORITHM, ITERATIONS, SALT_LENGTH, KEY_LENGTH);
    }

}

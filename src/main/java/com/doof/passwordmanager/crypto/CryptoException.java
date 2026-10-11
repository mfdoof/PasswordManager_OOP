package com.doof.passwordmanager.crypto;

/** Decryption or authentication failure: wrong key, tampered data, or wrong AAD. */
public class CryptoException extends RuntimeException {
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

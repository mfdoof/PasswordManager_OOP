package com.doof.passwordmanager.crypto;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Helpers for keeping secrets in arrays that can be zeroed after use. */
public final class SecureWipe {

    private SecureWipe() {}

    public static void wipe(byte[] a) {
        if (a != null) Arrays.fill(a, (byte) 0);
    }

    public static void wipe(char[] a) {
        if (a != null) Arrays.fill(a, '\0');
    }

    /** UTF-8 encodes {@code chars} without going through a String; wipes the intermediate buffer. */
    public static byte[] toUtf8(char[] chars) {
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] out = new byte[buf.remaining()];
        buf.get(out);
        if (buf.hasArray()) wipe(buf.array());
        return out;
    }
}

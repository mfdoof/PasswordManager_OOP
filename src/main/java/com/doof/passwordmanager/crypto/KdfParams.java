package com.doof.passwordmanager.crypto;

import com.doof.passwordmanager.util.FlatJson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Argon2id cost parameters, stored per vault as JSON in {@code vault_metadata.kdf_params}. */
public final class KdfParams {

    public static final String ALGORITHM = "argon2id";
    public static final int VERSION = 0x13;  // Argon2 v1.3
    public static final int KEY_LENGTH = 32;

    /** ARCHITECTURE §5 defaults: 64 MiB, 3 iterations, parallelism 1. */
    public static final KdfParams DEFAULT = new KdfParams(64 * 1024, 3, 1);

    // Upper bounds stop a tampered vault file from making unlock hang or run out of memory.
    private static final int MAX_MEMORY_KIB = 4 * 1024 * 1024;
    private static final int MAX_ITERATIONS = 100;
    private static final int MAX_PARALLELISM = 16;

    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;

    public KdfParams(int memoryKiB, int iterations, int parallelism) {
        if (parallelism < 1 || parallelism > MAX_PARALLELISM) {
            throw new IllegalArgumentException("parallelism out of range: " + parallelism);
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("iterations out of range: " + iterations);
        }
        if (memoryKiB < 8 * parallelism || memoryKiB > MAX_MEMORY_KIB) {
            throw new IllegalArgumentException("memoryKiB out of range: " + memoryKiB);
        }
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public int getMemoryKiB() { return memoryKiB; }
    public int getIterations() { return iterations; }
    public int getParallelism() { return parallelism; }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("algorithm", ALGORITHM);
        m.put("version", VERSION);
        m.put("memoryKiB", memoryKiB);
        m.put("iterations", iterations);
        m.put("parallelism", parallelism);
        m.put("keyLength", KEY_LENGTH);
        return FlatJson.write(m);
    }

    public static KdfParams fromJson(String json) {
        Map<String, Object> m = FlatJson.parse(json);
        if (!ALGORITHM.equals(m.get("algorithm"))) {
            throw new IllegalArgumentException("Unsupported KDF algorithm: " + m.get("algorithm"));
        }
        if (intField(m, "version") != VERSION) {
            throw new IllegalArgumentException("Unsupported Argon2 version: " + m.get("version"));
        }
        if (intField(m, "keyLength") != KEY_LENGTH) {
            throw new IllegalArgumentException("Unsupported key length: " + m.get("keyLength"));
        }
        return new KdfParams(intField(m, "memoryKiB"), intField(m, "iterations"), intField(m, "parallelism"));
    }

    private static int intField(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Long) || (Long) v < Integer.MIN_VALUE || (Long) v > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("kdf_params: missing or invalid '" + key + "'");
        }
        return ((Long) v).intValue();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KdfParams)) return false;
        KdfParams p = (KdfParams) o;
        return memoryKiB == p.memoryKiB && iterations == p.iterations && parallelism == p.parallelism;
    }

    @Override
    public int hashCode() {
        return Objects.hash(memoryKiB, iterations, parallelism);
    }

    @Override
    public String toString() {
        return toJson();
    }
}

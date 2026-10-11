package com.doof.passwordmanager.util;

import com.doof.passwordmanager.util.FlatJson.Mode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlatJsonTest {

    @Test
    void writeAndParseStringsNumbersAndNull() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "text");
        m.put("n", 42);
        m.put("big", 1L << 40);
        m.put("neg", -7);
        m.put("z", null);
        String json = FlatJson.write(m);

        assertEquals("{\"s\":\"text\",\"n\":42,\"big\":1099511627776,\"neg\":-7,\"z\":null}", json);
        Map<String, Object> back = FlatJson.parse(json);
        assertEquals("text", back.get("s"));
        assertEquals(42L, back.get("n"));
        assertEquals(1L << 40, back.get("big"));
        assertEquals(-7L, back.get("neg"));
        assertTrue(back.containsKey("z"));
        assertNull(back.get("z"));
    }

    @Test
    void secretValuesRoundTripAsCharArrays() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pw", "s\"ecr\\et\uD834\uDD1E".toCharArray());
        m.put("other", "x");
        byte[] json = FlatJson.writeUtf8(m);

        Map<String, Object> back = FlatJson.parseUtf8(json, k -> k.equals("pw") ? Mode.SECRET : Mode.SKIP);
        assertEquals(1, back.size(), "skipped keys must be absent");
        assertArrayEquals("s\"ecr\\et\uD834\uDD1E".toCharArray(), (char[]) back.get("pw"));
    }

    @Test
    void writeUtf8DoesNotModifyCharArrayValues() {
        char[] pw = "secret".toCharArray();
        FlatJson.writeUtf8(Map.of("pw", pw));
        assertArrayEquals("secret".toCharArray(), pw);
    }

    @Test
    void skippedValuesAreStillValidated() {
        byte[] bad = "{\"a\":\"ok\",\"b\":\"bad\\q\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parseUtf8(bad, k -> Mode.SKIP));
    }

    @Test
    void secretModeRejectsNonStringValues() {
        byte[] json = "{\"pw\":123}".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> FlatJson.parseUtf8(json, k -> Mode.SECRET));
    }

    @Test
    void longStringsGrowTheBufferCorrectly() {
        String big = "x".repeat(10_000);
        assertEquals(big, FlatJson.parse(FlatJson.write(Map.of("k", big))).get("k"));
    }
}

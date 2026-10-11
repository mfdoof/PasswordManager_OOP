package com.doof.passwordmanager.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EntryCodecTest {

    @Test
    void roundTripsAllFields() {
        EntryCodec.Fields in = new EntryCodec.Fields("example.com", "me@example.com", "S3cret!pass", "line1\nline2");
        EntryCodec.Fields out = EntryCodec.decode(EntryCodec.encode(in));

        assertEquals(in.site, out.site);
        assertEquals(in.user, out.user);
        assertEquals(in.password, out.password);
        assertEquals(in.notes, out.notes);
    }

    @Test
    void roundTripsSpecialCharactersAndNulls() {
        String tricky = "q\"uote \\back/slash \t\r\b\f \u0001 ünïcødé 🔑 {\"x\":1},";
        EntryCodec.Fields out = EntryCodec.decode(EntryCodec.encode(
                new EntryCodec.Fields("site", "user", tricky, null)));

        assertEquals(tricky, out.password);
        assertNull(out.notes);
    }

    @Test
    void decodesWhitespaceEscapesAndIgnoresUnknownKeys() {
        String json = " { \"site\" : \"a\\u0062c\", \"user\":\"u\", \"extra\":\"x\", \"password\":\"p\\/w\", \"notes\":null } ";
        EntryCodec.Fields f = EntryCodec.decode(json.getBytes(StandardCharsets.UTF_8));

        assertEquals("abc", f.site);
        assertEquals("u", f.user);
        assertEquals("p/w", f.password);
        assertNull(f.notes);
    }

    @Test
    void rejectsMalformedInputWithoutLeakingIt() {
        String[] bad = {"", "{", "{\"site\":\"x\"", "{\"site\":x}", "{\"site\":\"x\",}", "{\"site\":\"a\\q\"}",
                "{\"site\":\"x\"} trailing", "{\"site\":\"a\",\"site\":\"b\"}", "[]"};
        for (String s : bad) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> EntryCodec.decode(s.getBytes(StandardCharsets.UTF_8)), s);
            assertFalse(s.length() > 2 && e.getMessage().contains(s), "message leaks input: " + e.getMessage());
        }
    }
}

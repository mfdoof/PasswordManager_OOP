package com.doof.passwordmanager.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EntryCodecTest {

    @Test
    void roundTripsAllFields() {
        byte[] json = EntryCodec.encode("example.com", "me@example.com", "S3cret!pass".toCharArray(), "line1\nline2");

        EntryCodec.Summary s = EntryCodec.decodeSummary(json);
        assertEquals("example.com", s.site);
        assertEquals("me@example.com", s.user);
        assertArrayEquals("S3cret!pass".toCharArray(), EntryCodec.decodePassword(json));
        assertEquals("line1\nline2", EntryCodec.decodeNotes(json));
    }

    @Test
    void roundTripsSpecialCharactersAndNulls() {
        String tricky = "q\"uote \\back/slash \t\r\b\f \u0001 ünïcødé \uD834\uDD1E {\"x\":1},";
        byte[] json = EntryCodec.encode("site", "user", tricky.toCharArray(), null);

        assertEquals(tricky, new String(EntryCodec.decodePassword(json)));
        assertNull(EntryCodec.decodeNotes(json));
    }

    @Test
    void encodeDoesNotModifyThePassword() {
        char[] pw = "S3cret!pass".toCharArray();
        EntryCodec.encode("s", "u", pw, null);
        assertArrayEquals("S3cret!pass".toCharArray(), pw);
    }

    @Test
    void decodesWhitespaceEscapesAndIgnoresUnknownKeys() {
        String json = " { \"site\" : \"a\\u0062c\", \"user\":\"u\", \"extra\":\"x\", \"password\":\"p\\/w\", \"notes\":null } ";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        assertEquals("abc", EntryCodec.decodeSummary(bytes).site);
        assertEquals("u", EntryCodec.decodeSummary(bytes).user);
        assertArrayEquals("p/w".toCharArray(), EntryCodec.decodePassword(bytes));
        assertNull(EntryCodec.decodeNotes(bytes));
    }

    @Test
    void missingPasswordDecodesAsEmpty() {
        byte[] json = "{\"site\":\"s\",\"user\":\"u\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals(0, EntryCodec.decodePassword(json).length);
    }

    @Test
    void rejectsMalformedInputWithoutLeakingIt() {
        String[] bad = {"", "{", "{\"site\":\"x\"", "{\"site\":x}", "{\"site\":\"x\",}", "{\"site\":\"a\\q\"}",
                "{\"site\":\"x\"} trailing", "{\"site\":\"a\",\"site\":\"b\"}", "[]", "{\"site\":1}",
                "{\"password\":123}"};
        for (String s : bad) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
                EntryCodec.decodeSummary(bytes);
                EntryCodec.decodePassword(bytes);
            }, s);
            assertFalse(s.length() > 2 && e.getMessage().contains(s), "message leaks input: " + e.getMessage());
        }
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] bad = {'{', '"', 's', 'i', 't', 'e', '"', ':', '"', (byte) 0xC3, '"', '}'};
        assertThrows(IllegalArgumentException.class, () -> EntryCodec.decodeSummary(bad));
    }
}

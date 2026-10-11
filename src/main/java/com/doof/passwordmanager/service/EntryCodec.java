package com.doof.passwordmanager.service;

import com.doof.passwordmanager.util.FlatJson;
import com.doof.passwordmanager.util.FlatJson.Mode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes an entry's plaintext fields as the flat JSON object that gets encrypted into
 * {@code entries.data_enc}: {@code {"site":..,"user":..,"password":..,"notes":..}}.
 * <p>
 * Decoding is split by need so a caller only materializes what it uses: the listing path
 * never turns the password (or notes) into a Java object, and the password comes back
 * as a char[] the caller wipes.
 */
final class EntryCodec {

    static final String SITE = "site";
    static final String USER = "user";
    static final String PASSWORD = "password";
    static final String NOTES = "notes";

    /** Site and user only, for listing and search. */
    static final class Summary {
        final String site;
        final String user;

        Summary(String site, String user) {
            this.site = site;
            this.user = user;
        }
    }

    private EntryCodec() {}

    /** UTF-8 JSON payload; the caller wipes the result. {@code password} is not modified. */
    static byte[] encode(String site, String user, char[] password, String notes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(SITE, site);
        m.put(USER, user);
        m.put(PASSWORD, password);
        m.put(NOTES, notes);
        return FlatJson.writeUtf8(m);
    }

    static Summary decodeSummary(byte[] json) {
        Map<String, Object> m = parse(json, key -> SITE.equals(key) || USER.equals(key) ? Mode.VALUE : Mode.SKIP);
        return new Summary(string(m, SITE), string(m, USER));
    }

    /** The password as a char[] the caller must wipe; empty if absent. */
    static char[] decodePassword(byte[] json) {
        Map<String, Object> m = parse(json, key -> PASSWORD.equals(key) ? Mode.SECRET : Mode.SKIP);
        Object v = m.get(PASSWORD);
        return v == null ? new char[0] : (char[]) v;
    }

    /** The notes as a char[] the caller must wipe; null if there are none. */
    static char[] decodeNotesChars(byte[] json) {
        Map<String, Object> m = parse(json, key -> NOTES.equals(key) ? Mode.SECRET : Mode.SKIP);
        return (char[]) m.get(NOTES);
    }

    static String decodeNotes(byte[] json) {
        Map<String, Object> m = parse(json, key -> NOTES.equals(key) ? Mode.VALUE : Mode.SKIP);
        return string(m, NOTES);
    }

    private static Map<String, Object> parse(byte[] json, java.util.function.Function<String, Mode> modeFor) {
        try {
            return FlatJson.parseUtf8(json, modeFor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed entry payload: " + e.getMessage(), e);
        }
    }

    private static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v != null && !(v instanceof String)) {
            if (v instanceof char[]) Arrays.fill((char[]) v, '\0');
            throw new IllegalArgumentException("Malformed entry payload: '" + key + "' is not a string");
        }
        return (String) v;
    }
}

package com.doof.passwordmanager.service;

import com.doof.passwordmanager.util.FlatJson;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes an entry's plaintext fields as the flat JSON object that gets encrypted into
 * {@code entries.data_enc}: {@code {"site":..,"user":..,"password":..,"notes":..}}.
 * Values must be strings or null; unknown keys are ignored.
 */
final class EntryCodec {

    static final String SITE = "site";
    static final String USER = "user";
    static final String PASSWORD = "password";
    static final String NOTES = "notes";

    static final class Fields {
        final String site;
        final String user;
        final String password;
        final String notes;

        Fields(String site, String user, String password, String notes) {
            this.site = site;
            this.user = user;
            this.password = password;
            this.notes = notes;
        }
    }

    private EntryCodec() {}

    static byte[] encode(Fields f) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(SITE, f.site);
        m.put(USER, f.user);
        m.put(PASSWORD, f.password);
        m.put(NOTES, f.notes);
        return FlatJson.write(m).getBytes(StandardCharsets.UTF_8);
    }

    static Fields decode(byte[] json) {
        Map<String, Object> m;
        try {
            m = FlatJson.parse(new String(json, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed entry payload: " + e.getMessage(), e);
        }
        return new Fields(string(m, SITE), string(m, USER), string(m, PASSWORD), string(m, NOTES));
    }

    private static String string(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v != null && !(v instanceof String)) {
            throw new IllegalArgumentException("Malformed entry payload: '" + key + "' is not a string");
        }
        return (String) v;
    }
}

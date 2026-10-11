package com.doof.passwordmanager.service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Encodes an entry's plaintext fields as the flat JSON object that gets encrypted into
 * {@code entries.data_enc}: {@code {"site":..,"user":..,"password":..,"notes":..}}.
 * Only flat objects with string or null values are supported; unknown keys are ignored.
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
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        appendField(sb, SITE, f.site).append(',');
        appendField(sb, USER, f.user).append(',');
        appendField(sb, PASSWORD, f.password).append(',');
        appendField(sb, NOTES, f.notes);
        sb.append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Fields decode(byte[] json) {
        Map<String, String> m = new Parser(new String(json, StandardCharsets.UTF_8)).parseObject();
        return new Fields(m.get(SITE), m.get(USER), m.get(PASSWORD), m.get(NOTES));
    }

    private static StringBuilder appendField(StringBuilder sb, String key, String value) {
        appendString(sb, key).append(':');
        return value == null ? sb.append("null") : appendString(sb, value);
    }

    private static StringBuilder appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        Map<String, String> parseObject() {
            Map<String, String> result = new HashMap<>();
            skipWs();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
            } else {
                while (true) {
                    skipWs();
                    String key = parseString();
                    skipWs();
                    expect(':');
                    skipWs();
                    String value;
                    if (s.startsWith("null", pos)) {
                        pos += 4;
                        value = null;
                    } else {
                        value = parseString();
                    }
                    if (result.containsKey(key)) {
                        throw error("duplicate key");
                    }
                    result.put(key, value);
                    skipWs();
                    char c = next();
                    if (c == '}') break;
                    if (c != ',') throw error("expected ',' or '}'");
                }
            }
            skipWs();
            if (pos != s.length()) throw error("trailing data");
            return result;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return sb.toString();
                if (c < 0x20) throw error("control character in string");
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw error("truncated \\u escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad \\u escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape");
                }
            }
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        private char peek() {
            if (pos >= s.length()) throw error("unexpected end of input");
            return s.charAt(pos);
        }

        private char next() {
            char c = peek();
            pos++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw error("expected '" + c + "'");
        }

        // Never include the input in messages: it is decrypted vault data.
        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Malformed entry payload at offset " + pos + ": " + msg);
        }
    }
}

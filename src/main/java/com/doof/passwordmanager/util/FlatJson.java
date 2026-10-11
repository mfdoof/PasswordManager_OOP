package com.doof.passwordmanager.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON for flat objects whose values are strings, integers or null.
 * Used for the encrypted entry payload and the kdf_params column; avoids a JSON dependency.
 * Error messages never include the input, since it may be decrypted vault data.
 */
public final class FlatJson {

    private FlatJson() {}

    /** Writes {@code map} as a JSON object. Values must be String, Integer, Long or null. */
    public static String write(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder(128).append('{');
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            appendString(sb, e.getKey()).append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String) {
                appendString(sb, (String) v);
            } else if (v instanceof Integer || v instanceof Long) {
                sb.append(v);
            } else {
                throw new IllegalArgumentException("Unsupported JSON value type: " + v.getClass().getName());
            }
        }
        return sb.append('}').toString();
    }

    /** Parses a flat JSON object. Values come back as String, Long or null. Duplicate keys are rejected. */
    public static Map<String, Object> parse(String json) {
        return new Parser(json).parseObject();
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

        Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
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
                    Object value = parseValue();
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

        private Object parseValue() {
            char c = peek();
            if (c == '"') return parseString();
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) return parseLong();
            throw error("unsupported value");
        }

        private Long parseLong() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            try {
                return Long.parseLong(s.substring(start, pos));
            } catch (NumberFormatException e) {
                throw error("bad integer");
            }
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

        private IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Malformed JSON at offset " + pos + ": " + msg);
        }
    }
}

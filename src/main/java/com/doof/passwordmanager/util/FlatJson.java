package com.doof.passwordmanager.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimal JSON for flat objects whose values are strings, integers or null.
 * Used for the encrypted entry payload and the kdf_params column; avoids a JSON dependency.
 * <p>
 * Secret values can be written from and read into {@code char[]} so they never become
 * immutable Strings; every intermediate buffer is wiped. Error messages never include
 * the input, since it may be decrypted vault data.
 */
public final class FlatJson {

    /** How the parser treats the value of a given key. */
    public enum Mode {
        /** Return as String (or Long / null). */
        VALUE,
        /** Return a string value as a char[] the caller must wipe. */
        SECRET,
        /** Validate but do not materialize. */
        SKIP
    }

    private FlatJson() {}

    /** Writes {@code map} as a JSON object. Values must be String, Integer, Long or null. */
    public static String write(Map<String, ?> map) {
        CharSink out = writeTo(map);
        try {
            return new String(out.buf, 0, out.len);
        } finally {
            out.wipe();
        }
    }

    /**
     * Writes {@code map} as UTF-8 JSON. Values may also be {@code char[]} (secrets).
     * The caller owns (and should wipe) the returned bytes; intermediate buffers are wiped.
     */
    public static byte[] writeUtf8(Map<String, ?> map) {
        CharSink out = writeTo(map);
        ByteBuffer bytes = null;
        try {
            bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(out.buf, 0, out.len));
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return result;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Value is not valid Unicode", e);
        } finally {
            out.wipe();
            if (bytes != null && bytes.hasArray()) Arrays.fill(bytes.array(), (byte) 0);
        }
    }

    /** Parses a flat JSON object. Values come back as String, Long or null. Duplicate keys are rejected. */
    public static Map<String, Object> parse(String json) {
        char[] chars = json.toCharArray();
        try {
            return new Parser(chars, chars.length, key -> Mode.VALUE).parseObject();
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * Parses UTF-8 JSON, treating each key as {@code modeFor} says. Keys in SKIP mode are
     * absent from the result; SECRET values are char[] the caller must wipe.
     */
    public static Map<String, Object> parseUtf8(byte[] json, Function<String, Mode> modeFor) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        char[] chars = new char[json.length];  // UTF-8 never decodes to more chars than bytes
        CharBuffer cb = CharBuffer.wrap(chars);
        try {
            if (decoder.decode(ByteBuffer.wrap(json), cb, true).isError() || decoder.flush(cb).isError()) {
                throw new IllegalArgumentException("Malformed JSON: invalid UTF-8");
            }
            return new Parser(chars, cb.position(), modeFor).parseObject();
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    private static CharSink writeTo(Map<String, ?> map) {
        CharSink sb = new CharSink();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            appendString(sb, CharBuffer.wrap(e.getKey()));
            sb.append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String) {
                appendString(sb, CharBuffer.wrap((String) v));
            } else if (v instanceof char[]) {
                appendString(sb, CharBuffer.wrap((char[]) v));
            } else if (v instanceof Integer || v instanceof Long) {
                sb.append(v.toString());
            } else {
                sb.wipe();
                throw new IllegalArgumentException("Unsupported JSON value type: " + v.getClass().getName());
            }
        }
        sb.append('}');
        return sb;
    }

    private static void appendString(CharSink sb, CharSequence s) {
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
        sb.append('"');
    }

    /** Growable char buffer that wipes every buffer it discards. */
    private static final class CharSink {
        char[] buf = new char[128];
        int len;

        void append(char c) {
            if (len == buf.length) {
                char[] bigger = Arrays.copyOf(buf, buf.length * 2);
                Arrays.fill(buf, '\0');
                buf = bigger;
            }
            buf[len++] = c;
        }

        void append(String s) {
            for (int i = 0; i < s.length(); i++) append(s.charAt(i));
        }

        void wipe() {
            Arrays.fill(buf, '\0');
            len = 0;
        }
    }

    private static final class Parser {
        private final char[] s;
        private final int end;
        private final Function<String, Mode> modeFor;
        private int pos;

        Parser(char[] s, int end, Function<String, Mode> modeFor) {
            this.s = s;
            this.end = end;
            this.modeFor = modeFor;
        }

        Map<String, Object> parseObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            try {
                skipWs();
                expect('{');
                skipWs();
                if (peek() == '}') {
                    pos++;
                } else {
                    while (true) {
                        skipWs();
                        String key = new String(parseChars());
                        skipWs();
                        expect(':');
                        skipWs();
                        if (!seen.add(key)) {
                            throw error("duplicate key");
                        }
                        Mode mode = modeFor.apply(key);
                        Object value = parseValue(mode == null ? Mode.SKIP : mode);
                        if (mode != Mode.SKIP && mode != null) {
                            result.put(key, value);
                        }
                        skipWs();
                        char c = next();
                        if (c == '}') break;
                        if (c != ',') throw error("expected ',' or '}'");
                    }
                }
                skipWs();
                if (pos != end) throw error("trailing data");
                return result;
            } catch (RuntimeException e) {
                for (Object v : result.values()) {
                    if (v instanceof char[]) Arrays.fill((char[]) v, '\0');
                }
                throw e;
            }
        }

        private Object parseValue(Mode mode) {
            char c = peek();
            if (c == '"') {
                char[] chars = parseChars();
                switch (mode) {
                    case SECRET:
                        return chars;
                    case VALUE:
                        String str = new String(chars);
                        Arrays.fill(chars, '\0');
                        return str;
                    default:
                        Arrays.fill(chars, '\0');
                        return null;
                }
            }
            if (startsWith("null")) {
                pos += 4;
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                if (mode == Mode.SECRET) throw error("expected string");
                return parseLong();
            }
            throw error("unsupported value");
        }

        private Long parseLong() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < end && Character.isDigit(s[pos])) pos++;
            try {
                return Long.parseLong(new String(s, start, pos - start));
            } catch (NumberFormatException e) {
                throw error("bad integer");
            }
        }

        /** Parses a JSON string into an exact-length char[]; the scratch buffer is wiped. */
        private char[] parseChars() {
            expect('"');
            char[] tmp = new char[end - pos];  // decoded length never exceeds remaining input
            int n = 0;
            try {
                while (true) {
                    char c = next();
                    if (c == '"') return Arrays.copyOf(tmp, n);
                    if (c < 0x20) throw error("control character in string");
                    if (c != '\\') {
                        tmp[n++] = c;
                        continue;
                    }
                    char e = next();
                    switch (e) {
                        case '"' -> tmp[n++] = '"';
                        case '\\' -> tmp[n++] = '\\';
                        case '/' -> tmp[n++] = '/';
                        case 'b' -> tmp[n++] = '\b';
                        case 'f' -> tmp[n++] = '\f';
                        case 'n' -> tmp[n++] = '\n';
                        case 'r' -> tmp[n++] = '\r';
                        case 't' -> tmp[n++] = '\t';
                        case 'u' -> {
                            if (pos + 4 > end) throw error("truncated \\u escape");
                            int cp = 0;
                            for (int i = 0; i < 4; i++) {
                                int d = Character.digit(s[pos + i], 16);
                                if (d < 0) throw error("bad \\u escape");
                                cp = cp * 16 + d;
                            }
                            tmp[n++] = (char) cp;
                            pos += 4;
                        }
                        default -> throw error("bad escape");
                    }
                }
            } finally {
                Arrays.fill(tmp, '\0');
            }
        }

        private boolean startsWith(String word) {
            if (pos + word.length() > end) return false;
            for (int i = 0; i < word.length(); i++) {
                if (s[pos + i] != word.charAt(i)) return false;
            }
            return true;
        }

        private void skipWs() {
            while (pos < end && Character.isWhitespace(s[pos])) pos++;
        }

        private char peek() {
            if (pos >= end) throw error("unexpected end of input");
            return s[pos];
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

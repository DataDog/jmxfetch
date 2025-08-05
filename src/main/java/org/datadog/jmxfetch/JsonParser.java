package org.datadog.jmxfetch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("unchecked")
class JsonParser {
    public static final class JsonException extends Exception {
        Parser parser;
        public JsonException(String message) {
            super(message);
        }
        public JsonException(Parser parser, String message, Throwable cause) {
            super(message, cause);
            this.parser = parser;
        }
        public JsonException(Parser parser, String message) {
            super(message);
            this.parser = parser;
        }
        @Override
        public String toString() {
            String ch = "n/a";
            int pos = parser.pos - 1;
            if (pos >= 0) {
                byte c = parser.buf[pos];
                ch = (c > 32 && c < 127) ? String.format("'%c'", c) : String.format("0x%02x", c);
            }
            return String.format("at pos %d char %s: %s", pos, ch, getMessage());
        }
    }

    private static class AsciiSet {
        boolean map[];

        AsciiSet(char ...chars) {
            map = new boolean[128];
            for (char c : chars) {
                if (c < 0 || c >= 128) {
                    throw new RuntimeException("char out of range");
                }
                map[(int)c] = true;
            }
        }

        boolean get(byte b) {
            if (b < 0) {
                return false;
            }
            return map[b];
        }

        static AsciiSet of(char ...chars) {
            return new AsciiSet(chars);
        }

        static AsciiSet of(AsciiSet base, char ...chars) {
            AsciiSet bs = new AsciiSet(chars);
            for (int i = 0; i < base.map.length; i++) {
                bs.map[i] |= base.map[i];
            }
            return bs;
        }
    }

    static class ByteVector {
        byte[] buf;
        int cap;
        int len;

        ByteVector() {}

        void reserve(int needed) {
            if (cap < needed || len >= cap - needed) {
                grow(needed);
            }
        }

        void grow(int needed) {
            int newcap = cap * 2;
            if (newcap == 0) {
                newcap = 1024;
            }
            int capreq = len + needed;
            if (capreq > newcap) {
                newcap = capreq;
            }
            byte[] next = new byte[newcap];
            if (len > 0) {
                System.arraycopy(buf, 0, next, 0, len);
            }
            buf = next;
            cap = newcap;
        }

        void put(byte b) {
            reserve(1);
            buf[len++] = b;
        }

        void put(byte[] b, int offset, int count) {
            reserve(count);
            System.arraycopy(b, offset, buf, len, count);
            len += count;
        }

        String to_string() {
            return new String(buf, 0, len, UTF_8);
        }

        boolean empty() {
            return len == 0;
        }

        int length() {
            return len;
        }

        void reset() {
            len = 0;
        }
    }

    /** Parse UTF-8 encoded JSON. */
    static class Parser {
        byte[] buf;
        int beg = 0; // start position of current token
        int pos = 0; // current position, one past of the end of the token
        int depth = 0;

        static final int max_object_keys = 100_000;
        static final int max_array_items = 100_000;
        static final int max_string_length = 100_000;
        static final int max_number_length = 100;
        static final int max_depth = 1000;

        ByteVector str = new ByteVector(); // buffer for decoding strings

        Parser(byte[] buf) {
            this.buf = buf;
        }

        private String take(int skipEnd) {
            String s = new String(buf, beg, pos - beg - skipEnd, UTF_8);
            skip();
            return s;
        }

        private void take(ByteVector dst, int skipEnd) {
            dst.put(buf, beg, pos - beg - skipEnd);
            skip();
        }

        private void skip() {
            beg = pos;
        }

        private void back() {
            pos--;
        }

        private byte next_byte() throws JsonException {
            if (done()) {
                throw new JsonException(this, "unexpected end of stream");
            }
            byte b = buf[pos];
            pos++;
            return b;
        }

        private char next() throws JsonException {
            return (char)next_byte();
        }

        private boolean accept(AsciiSet set) throws JsonException {
            if (set.get(next_byte())) {
                return true;
            }
            back();
            return false;
        }

        private boolean accept(char b) throws JsonException {
            if (next() == b) {
                return true;
            }
            back();
            return false;
        }

        private boolean contains(byte[] set, byte b) {
            for (byte bs : set) {
                if (bs == b) {
                    return true;
                }
            }
            return false;
        }

        private void accept_run(AsciiSet set) throws JsonException {
            while (!done() && accept(set)) {
            }
        }

        private boolean done() {
            return pos >= buf.length;
        }

        private int length() {
            return pos - beg;
        }

        private static final AsciiSet WS = AsciiSet.of('\t', '\n', '\r', ' ');
        private void skip_ws() throws JsonException {
            accept_run(WS);
            skip();
        }

        Object parse() throws JsonException {
            Object res = parse_value();
            skip_ws();
            if (!done()) {
                throw new JsonException(this, "unused data after the top-level value");
            }
            return res;
        }

        private Object parse_value() throws JsonException {
            if (++depth > max_depth) {
                throw new JsonException(this, "too much nesting");
            }
            try {
                skip_ws();
                switch (next()) {
                case '{':
                    return parse_object();
                case '[':
                    return parse_array();
                case '-':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    return parse_number();
                case '"':
                    return parse_string();
                case 't':
                    return parse_true();
                case 'f':
                    return parse_false();
                case 'n':
                    return parse_null();
                default:
                    throw new JsonException(this, "unexpected character");
                }
            } finally {
                depth--;
            }
        }

        private HashMap<String, Object> parse_object() throws JsonException {
            int limit = max_object_keys;
            HashMap<String, Object> res = new HashMap<>();

            skip_ws();
            if (accept('}')) {
                return res;
            }

            object:
            while (limit-- > 0) {
                skip_ws();
                if (!accept('"')) {
                    throw new JsonException(this, "object key must be a string");
                }
                String key = parse_string();

                skip_ws();
                if (!accept(':')) {
                    throw new JsonException(this, "object key must be followed by a semicolon");
                }
                Object val = parse_value();

                res.put(key, val);

                skip_ws();
                switch (next()) {
                case ',':
                    continue object;
                case '}':
                    skip();
                    return res;
                default:
                    throw new JsonException(this, "unexpected character after object value");
                }
            }

            throw new JsonException(this, "exceed number of allowed keys in an object");
        }

        private ArrayList<Object> parse_array() throws JsonException {
            ArrayList<Object> res = new ArrayList<>();

            int limit = max_array_items;

            skip_ws();
            if (accept(']')) {
                return res;
            }

            array:
            while (limit-- > 0) {
                skip_ws();
                res.add(parse_value());
                skip_ws();

                switch (next()) {
                case ',':
                    continue array;
                case ']':
                    return res;
                default:
                    throw new JsonException(this, "unexpected character after array item");
                }
            }

            throw new JsonException(this, "too many items in an array");
        }

        private static final AsciiSet DIGITS = AsciiSet.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9');
        private static final AsciiSet PLUS_MINUS = AsciiSet.of('+', '-');
        private static final AsciiSet EXP = AsciiSet.of('e', 'E');
        private Number parse_number() throws JsonException {
            back();
            accept('-');

            switch (next()) {
            case '0':
                break;
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                accept_run(DIGITS);
                break;
            default:
                throw new JsonException(this, "unexpected character at start of a number");
            }

            boolean integer = true;
            if (!done() && accept('.')) {
                integer = false;
                accept_run(DIGITS);
            }

            if (!done() && accept(EXP)) {
                integer = false;
                accept(PLUS_MINUS);
                accept_run(DIGITS);
            }

            if (length() > max_number_length) {
                throw new JsonException(this, "number literal is too long");
            }

            String token = take(0);
            if (integer && !"-0".equals(token)) {
                try {
                    long v = Long.valueOf(token);
                    if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                        return (int)v;
                    }
                    return v;
                } catch (NumberFormatException ex) {
                    // try as double next
                }
            }
            try {
                return Double.valueOf(token);
            } catch (NumberFormatException ex) {
                throw new JsonException(this, "unable to parse number literal", ex);
            }
        }

        private String parse_string() throws JsonException {
            skip(); // quote

            str.reset();

            while (str.length() + length() < max_string_length) {
                switch (next()) {
                case '"':
                    if (str.empty()) {
                        return take(1);
                    }
                    take(str, 1);
                    return str.to_string();
                case '\\':
                    take(str, 1);
                    switch (next()) {
                    case '"':
                        str.put((byte)'"');
                        break;
                    case '\\':
                        str.put((byte)'\\');
                        break;
                    case '/':
                        str.put((byte)'/');
                        break;
                    case 'b':
                        str.put((byte)'\b');
                        break;
                    case 'f':
                        str.put((byte)'\f');
                        break;
                    case 'n':
                        str.put((byte)'\n');
                        break;
                    case 'r':
                        str.put((byte)'\r');
                        break;
                    case 't':
                        str.put((byte)'\t');
                        break;
                    case 'u':
                        parse_unicode_escape_maybe_surrogate();
                        break;
                    default:
                        throw new JsonException(this, "invalid escape sequence");
                    }
                    skip();
                    break;
                }
            }

            throw new JsonException(this, "string is too long");
        }

        private void parse_unicode_escape_maybe_surrogate() throws JsonException {
            int ch = parse_unicode_escape();

            if (ch >= 0xd800 && ch < 0xdc00) {
                if (!(accept('\\') && accept('u'))) {
                    throw new JsonException(this, "escaped unicode surrogate pair must be followed by another one");
                }
                int low = parse_unicode_escape();
                if (!(low >= 0xdc00 && low < 0xe000)) {
                    throw new JsonException(this, "invalid escaped unicode surrogate pair");
                }
                ch = ((ch & 0x3ff) << 10) | (low & 0x3ff) | 0x10000;
            }

            if (ch < 0x80) {
                str.put((byte) ch);
            } else if (ch < 0x800) {
                str.put((byte) (192 | (ch >> 6)));
                str.put((byte) (128 | (ch & 63)));
            } else if (ch < 0x10000) {
                str.put((byte) (224 | (ch >> 12)));
                str.put((byte) (128 | ((ch >> 6) & 63)));
                str.put((byte) (128 | (ch & 63)));
            } else {
                str.put((byte) (240 | (ch >> 18)));
                str.put((byte) (128 | ((ch >> 12) & 63)));
                str.put((byte) (128 | ((ch >> 6) & 63)));
                str.put((byte) (128 | (ch & 63)));
            }
        }

        private static final AsciiSet HEX = AsciiSet.of(DIGITS, 'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F');
        private int parse_unicode_escape() throws JsonException {
            skip();
            if (!(accept(HEX) && accept(HEX) && accept(HEX) && accept(HEX))) {
                throw new JsonException(this, "invalid unicode escape sequence");
            }
            return Integer.parseInt(take(0), 16);
        }

        private Object parse_null() throws JsonException {
            if (accept('u') && accept('l') && accept('l')) {
                return null;
            }
            throw new JsonException(this, "invalid keyword");
        }
        private Object parse_true() throws JsonException {
            if (accept('r') && accept('u') && accept('e')) {
                return true;
            }
            throw new JsonException(this, "invalid keyword");
        }
        private Object parse_false() throws JsonException {
            if (accept('a') && accept('l') && accept('s') && accept('e')) {
                return false;
            }
            throw new JsonException(this, "invalid keyword");
        }
    }

    private Map<String, Object> parsedJson;

    public JsonParser(byte[] utf8) throws JsonException {
        Parser parser = new Parser(utf8);
        Object value = parser.parse();
        if (value instanceof Map) {
            parsedJson = (Map<String, Object>)value;
        } else {
            throw new JsonException(parser, "expected json object at the top level");
        }
    }

    public JsonParser(JsonParser other) {
        parsedJson = new HashMap<String, Object>((Map<String, Object>) other.getParsedJson());
    }

    public Object getJsonConfigs() {
        return parsedJson.get("configs");
    }

    public Object getJsonTimestamp() {
        return parsedJson.get("timestamp");
    }

    public Object getJsonInstances(String key) {
        Map<String, Object> config =
                (Map<String, Object>)
                        ((Map<String, Object>) parsedJson.get("configs")).get(key);

        return config.get("instances");
    }

    public Object getInitConfig(String key) {
        Map<String, Object> config =
                (Map<String, Object>)
                        ((Map<String, Object>) parsedJson.get("configs")).get(key);

        return config.get("init_config");
    }

    public Object getParsedJson() {
        return parsedJson;
    }
}

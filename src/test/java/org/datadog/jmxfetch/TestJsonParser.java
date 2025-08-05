package org.datadog.jmxfetch;

import org.junit.Test;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.function.ThrowingRunnable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import static java.nio.charset.StandardCharsets.UTF_8;

public class TestJsonParser {
    private Object parse(String s) throws JsonParser.JsonException {
        return new JsonParser.Parser(s.getBytes(UTF_8)).parse();
    }

    private void assertParses(Object exp, String s) throws JsonParser.JsonException {
        assertEquals(exp, parse(s));
        assertEquals(exp, parse("\n" + s));
        assertEquals(exp, parse(s + " "));
        assertEquals(exp, parse("\t\t" + s + "\n\n"));
    }

    private void assertParsent(final String s) {
        assertThrows(JsonParser.JsonException.class, new ThrowingRunnable() {
            public void run() throws JsonParser.JsonException {
                parse(s);
            }
        });
    }

    @Test
    public void values() throws Exception {
        assertParsent("");
        assertParses(0.0, "0");
        assertParses(0.0, "0.0");
        assertParses(0.0, "0e1");
        assertParses(0.0, "0.0e1");
        assertParses(-0.0, "-0");
        assertParses(1., "1");
        assertParses(12., "12");
        assertParses(42.5, "42.5");
        assertParses(42.52, "42.52");
        assertParses(425.2, "42.52e1");
        assertParses(4252., "42.52e02");
        assertParses(4.252, "42.52e-1");
        assertParses(4.252e-11, "42.52e-12");
        assertParses("", "\"\"");
        assertParses("a", "\"a\"");
        assertParses("01abc", "\"01abc\"");
        assertParses("\"\\/\b\f\n\r\t", "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"");
        assertParses(true, "true");
        assertParses(false, "false");
        assertNull(parse("null"));
        assertParsent("truefalse");
        assertParses(Arrays.asList(), "[]");
        assertParses(Arrays.asList(), "[\n]");
        assertParses(Arrays.asList(1.0), "[1]");
        assertParses(Arrays.asList(1.0), "[ 1\n]");
        assertParsent("[1,]");
        assertParses(Arrays.asList(true, false, null, 14.5, "abc"), "[true, false, null, 14.5, \"abc\"]");
        assertParsent("[truefalse]");
        assertParsent("nulltrue");
    }

    static String repeat(String l, String a, String b, String r, int count) {
        StringBuilder sb = new StringBuilder();
        sb.append(l);
        for (int i = 0; i < count; i++) {
            sb.append(a);
        }
        for (int i = 0; i < count; i++) {
            sb.append(b);
        }
        sb.append(r);
        return sb.toString();
    }

    @Test
    public void limits() throws JsonParser.JsonException {
        parse(repeat("\"", "x", "", "\"", 99999));
        assertParsent(repeat("\"", "x", "", "\"", 100000));
        parse(repeat("\"", "\\t", "", "\"", 99999));
        assertParsent(repeat("\"", "\\u000a", "", "\"", 100000));
        parse(repeat("\"", "\\t", "", "\"", 99999));
        assertParsent(repeat("\"", "\\u000a", "", "\"", 100000));

        parse(repeat("", "[", "]", "", 1000));
        assertParsent(repeat("", "[", "]", "", 1001));
        parse(repeat("{", "\"\":{", "}", "}", 999));
        assertParsent(repeat("{", "\"\":{", "}", "}", 1000));
        parse(repeat("", "{\"\":[", "]}", "", 500));
        assertParsent(repeat("", "{\"\":[", "]}", "", 501));
    }

    private Object get(Object obj, Object ...keys) throws Exception {
        for (Object k : keys) {
            if (k instanceof String) {
                obj = ((Map) obj).get(k);
            } else {
                obj = ((List) obj).get((int) k);
            }
        }
        return obj;
    }

    @Test
    public void object() throws Exception {
        Object obj = parse("{\"timestamp\": 1754309434, \"configs\": { \"foo\": { \"instances\": [ { \"host\": \"none\" }, {  } ], \"init_config\": { } }, \"bar\": {} } }");
        assertEquals(1754309434.0, get(obj, "timestamp"));
        assertEquals("none", get(obj, "configs", "foo", "instances", 0, "host"));
        assertEquals(new HashMap(), get(obj, "configs", "foo", "instances", 1));
        assertEquals(new HashMap(), get(obj, "configs", "bar"));
    }

    @Test
    public void unicode() throws Exception {
        assertEquals("☀️😎🏖️", parse("\"☀️😎\\ud83c\\udfd6\\ufe0f\""));
        assertEquals("[☀️😎🏖️]", parse("\"[☀️😎\\ud83c\\udfd6\\ufe0f]\""));
    }

    @Test
    public void unicode_bad_surrogate() {
        assertParsent("\"\\ud83c\ufe0f\"");
        assertParsent("\"\\ud83c!");
        assertParsent("\"\\ud83c");
    }

    @FuzzTest(maxDuration="4h")
    public void fuzz(byte[] in) {
        try {
            new JsonParser.Parser(in).parse();
        } catch (JsonParser.JsonException ex) {
        }
    }
}

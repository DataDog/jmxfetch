package org.datadog.jmxfetch;

import org.junit.Test;

import java.io.StringWriter;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestJsonPrinter {
    String print(Object obj) throws Exception {
        StringWriter sw = new StringWriter();
        JsonPrinter.prettyPrint(sw, obj);
        return sw.toString();
    }

    @Test
    public void printer() throws Exception {
        assertEquals("null", print(null));
        assertEquals("[ 1, 2, 3.5, null, \"abc\" ]", print(Arrays.asList(1, 2, 3.5, null, "abc")));
        assertEquals("\"お茶\"", print("お茶"));
        assertEquals("\"\\\\/\\\"\\u0001\\b\\t\\n\\u000b\\f\\r\"", print("\\/\"\u0001\u0008\u0009\n\u000b\u000c\r"));
        assertEquals("\"☀️😎🏖️\"", print("☀️😎🏖️"));
    }
}

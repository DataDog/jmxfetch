package org.datadog.jmxfetch;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JsonPrinter {
    static final String TAB = "  ";

    PrintWriter out;
    StringBuilder indent = new StringBuilder();

    public JsonPrinter(PrintWriter out) {
        this.out = out;
    }

    public JsonPrinter(OutputStream out) {
        this(new PrintWriter(out));
    }

    public JsonPrinter(Writer out) {
        this(new PrintWriter(out));
    }

    public static void prettyPrint(PrintWriter dst, Object obj) {
        new JsonPrinter(dst).prettyPrint(obj);
    }

    public static void prettyPrint(OutputStream dst, Object obj) {
        new JsonPrinter(dst).prettyPrint(obj);
    }

    public static void prettyPrint(Writer dst, Object obj) {
        new JsonPrinter(dst).prettyPrint(obj);
    }

    public static String toString(Object obj) {
        StringWriter sw = new StringWriter();
        prettyPrint(sw, obj);
        return sw.toString();
    }

    public void prettyPrint(Object obj) {
        if (obj == null) {
            out.print("null");
        } else if (obj instanceof Boolean) {
            out.print(obj);
        } else if (obj instanceof Byte || obj instanceof Short || obj instanceof Integer || obj instanceof Long) {
            long v = ((Number) obj).longValue();
            out.print(v);
        } else if (obj instanceof Number) {
            double v = ((Number) obj).doubleValue();
            if (Double.isNaN(v)) {
                out.print("\"NaN\"");
            } else if (Double.isInfinite(v)) {
                out.format("\"%sInf\"", v < 0 ? "-" : "");
            } else {
                out.print(v);
            }
        } else if (obj instanceof String) {
            out.print("\"");
            for (char c : ((String) obj).toCharArray()) {
                switch (c) {
                case '"':
                    out.print("\\\"");
                    break;
                case '\\':
                    out.print("\\\\");
                    break;
                case '\b':
                    out.print("\\b");
                    break;
                case '\f':
                    out.print("\\f");
                    break;
                case '\n':
                    out.print("\\n");
                    break;
                case '\r':
                    out.print("\\r");
                    break;
                case '\t':
                    out.print("\\t");
                    break;
                default:
                    if (c < 32) {
                        out.format("\\u%04x", (int)c);
                    } else {
                        out.print(c);
                    }
                }
            }
            out.print("\"");
        } else if (obj instanceof List) {
            out.print("[ ");
            boolean first = true;
            for (Object item : (List<Object>) obj) {
                if (!first) {
                    out.print(", ");
                }
                first = false;
                prettyPrint(item);
            }
            out.format(" ]");
        } else if (obj instanceof Map) {
            out.println("{");
            indent.append(TAB);
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) obj).entrySet()) {
                if (!first) {
                    out.println(", ");
                }
                first = false;
                out.print(indent);
                prettyPrint(e.getKey());
                out.print(" : ");
                prettyPrint(e.getValue());
            }
            indent.delete(indent.length() - TAB.length(), indent.length());
            out.format("\n%s}", indent);
        } else if (obj instanceof String[]) {
            prettyPrint(Arrays.asList((String[]) obj));
        } else {
            throw new RuntimeException("unsupported json data type");
        }
    }
}

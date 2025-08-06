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

    /**
     * Constructs a new JsonPrinter with the specified PrintWriter.
     * @param out the PrintWriter to write JSON output to
     */
    public JsonPrinter(PrintWriter out) {
        this.out = out;
    }

    public static void prettyPrint(PrintWriter dst, Object obj) {
        new JsonPrinter(dst).prettyPrint(obj);
    }

    /**
     * Pretty prints the given object as JSON to an OutputStream.
     * @param dst the OutputStream to write to
     * @param obj the object to print as JSON
     */
    public static void prettyPrint(OutputStream dst, Object obj) {
        PrintWriter pw = new PrintWriter(dst);
        new JsonPrinter(pw).prettyPrint(obj);
        pw.close();
    }

    /**
     * Pretty prints the given object as JSON to a Writer.
     * @param dst the Writer to write to
     * @param obj the object to print as JSON
     */
    public static void prettyPrint(Writer dst, Object obj) {
        PrintWriter pw = new PrintWriter(dst);
        new JsonPrinter(pw).prettyPrint(obj);
        pw.close();
    }

    /**
     * Pretty prints the given object as JSON.
     * @param obj the object to print
     */
    public void prettyPrint(Object obj) {
        if (obj == null) {
            out.print("null");
        } else if (obj instanceof Boolean) {
            out.print(obj);
        } else if (obj instanceof Byte || obj instanceof Short
                || obj instanceof Integer || obj instanceof Long) {
            long val = ((Number) obj).longValue();
            out.print(val);
        } else if (obj instanceof Number) {
            double val = ((Number) obj).doubleValue();
            if (Double.isNaN(val)) {
                out.print("\"NaN\"");
            } else if (Double.isInfinite(val)) {
                out.format("\"%sInf\"", val < 0 ? "-" : "");
            } else {
                out.print(val);
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
            for (Object item : (List<?>) obj) {
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
            for (Map.Entry e : ((Map<?, ?>) obj).entrySet()) {
                if (!first) {
                    out.println(", ");
                }
                first = false;
                out.print(indent);
                Object key = e.getKey();
                if (key instanceof String) {
                    prettyPrint(e.getKey());
                } else {
                    throw new RuntimeException(
                            "invalid object key type in a map: " + key.getClass().getName());
                }
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

    /**
     * Converts an object to its JSON string representation.
     * @param obj the object to convert
     * @return the JSON string representation
     */
    public static String toString(Object obj) {
        StringWriter sw = new StringWriter();
        prettyPrint(sw, obj);
        return sw.toString();
    }
}

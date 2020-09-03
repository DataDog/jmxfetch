package org.datadog.jmxfetch.util;

import java.util.Collection;
import java.util.Iterator;

public class StringUtils {

    /**
     * Joins the parts together delimitd by the delimiter.
     * @param delimiter the delimiter
     * @param parts the parts to join
     * @return a new string composed of the parts delimited by the delimiter
     */
    public static String join(String delimiter, Collection<String> parts) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = parts.iterator();
        if (it.hasNext()) {
            sb.append(it.next());
        }
        while (it.hasNext()) {
            sb.append(delimiter).append(it.next());
        }
        return sb.toString();
    }

    /**
     * Joins the parts together delimitd by the delimiter.
     * @param delimiter the delimiter
     * @param parts the parts to join
     * @return a new string composed of the parts delimited by the delimiter
     */
    public static String join(String delimiter, String... parts) {
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append(parts[0]);
            for (int i = 1; i < parts.length; ++i) {
                sb.append(delimiter).append(parts[i]);
            }
            return sb.toString();
        }
        if (parts.length == 1) {
            return parts[0];
        }
        return "";
    }
}

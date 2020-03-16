package org.datadog.jmxfetch.util;

import org.apache.commons.lang.StringUtils;

public class ServiceCheckHelper {
    /** Formats the service check prefix. */
    public static String formatServiceCheckPrefix(String fullname) {
        String[] chunks = fullname.split("\\.");
        chunks[0] = chunks[0].replaceAll("[A-Z0-9:_\\-]", "");
        return StringUtils.join(chunks, ".");
    }
}

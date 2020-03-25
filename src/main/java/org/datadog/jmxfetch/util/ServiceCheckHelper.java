package org.datadog.jmxfetch.util;

import org.apache.commons.lang.StringUtils;

public class ServiceCheckHelper {
    /**
     * Formats the service check prefix.
     * First implemented here: https://github.com/DataDog/jmxfetch/commit/0428c41ebf7a14404ae50928e3ecfc229701c042
     * */
    public static String formatServiceCheckPrefix(String fullname) {
        String[] chunks = fullname.split("\\.");
        chunks[0] = chunks[0].replaceAll("[A-Z0-9:_\\-]", "");
        return StringUtils.join(chunks, ".");
    }
}

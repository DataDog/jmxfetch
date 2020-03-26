package org.datadog.jmxfetch.util;

import org.apache.commons.lang.StringUtils;

public class ServiceCheckHelper {
    /**
     * Formats the service check prefix.
     * First implemented here:
     * https://github.com/DataDog/jmxfetch/commit/0428c41ebf7a14404ae50928e3ecfc229701c042
     *
     * <p>The formatted service check name is kept for backward compatibility only.
     * Previously there were 2 JMXFetch integrations for activemq: one called activemq
     * for older versions of activemq, the other called activemq_58 for v5.8+ of activemq,
     * see https://github.com/DataDog/dd-agent/tree/5.10.x/conf.d
     * And we still wanted both integrations to send the service check with the activemq prefix.
     * */
    public static String formatServiceCheckPrefix(String fullname) {
        String[] chunks = fullname.split("\\.");
        chunks[0] = chunks[0].replaceAll("[A-Z0-9:_\\-]", "");
        return StringUtils.join(chunks, ".");
    }
}

package org.datadog.jmxfetch.reporter;

import org.datadog.jmxfetch.util.StringUtils;

import java.util.Arrays;

public class ReporterFactory {

    /** Gets the reporter for the correspndonding type string (console, statsd). */
    public static Reporter getReporter(String type) {
        if (type == null || type.length() <= 0) {
            throw new IllegalArgumentException("Null or empty reporter type");
        }
        if ("console".equals(type)) {
            return new ConsoleReporter();
        } else if ("json".equals(type)) {
            return new JsonReporter();
        } else if (type.startsWith("statsd:")) {
            String[] typeElements = type.split(":");
            String host = "localhost";
            Integer port = Integer.valueOf(typeElements[typeElements.length - 1]);
            if (typeElements.length > 2) {
                host = StringUtils.join(":",
                        Arrays.copyOfRange(typeElements, 1, typeElements.length - 1));
            }
            return new StatsdReporter(host, port);
        } else {
            throw new IllegalArgumentException("Invalid reporter type: " + type);
        }
    }

    private ReporterFactory() {}
}

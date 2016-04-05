package org.datadog.jmxfetch.reporter;

import com.google.common.base.Joiner;
import java.util.Arrays;

public class ReporterFactory {

    public static Reporter getReporter(String type) {
        if (type == null || type.length() <= 0) {
            throw new IllegalArgumentException("Null or empty reporter type");
        }
        if ("console".equals(type)) {
            return new ConsoleReporter();
        } else if (type.startsWith("statsd:")) {
            String[] typeElements = type.split(":");
            String host = "localhost";
            Integer port = Integer.valueOf(typeElements[typeElements.length - 1]);
            if (typeElements.length > 2) {
                host = Joiner.on(":").join(Arrays.copyOfRange(typeElements, 1, typeElements.length - 1));
            }
            return new StatsdReporter(host, port);
        } else {
            throw new IllegalArgumentException("Invalid reporter type: " + type);
        }
    }

    private ReporterFactory() {}
}

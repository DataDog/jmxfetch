package org.datadog.jmxfetch.reporter;

public class ReporterFactory {

    public static Reporter getReporter(String type) {
        if (type == null || type.length() <= 0) {
            throw new IllegalArgumentException("Null or empty reporter type");
        }
        if ("console".equals(type)) {
            return new ConsoleReporter();
        } else if (type.startsWith("statsd:")) {
            return new StatsdReporter(Integer.valueOf(type.split(":")[1]));
        } else {
            throw new IllegalArgumentException("Invalid reporter type: " + type);
        }
    }
}

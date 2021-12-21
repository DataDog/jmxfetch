package org.datadog.jmxfetch.util;

import java.util.logging.Level;

/**
 * LogLevel used for internal logging to match Datadog Agent levels.
 * Comparison table with java.util.logging:
 * <pre>
 *  JUL     |  JMXFetch LogLevel
 * ----------------------------
 *  OFF     |  OFF
 *  SEVERE  |  ERROR
 *  WARNING |  WARN
 *  INFO    |  INFO
 *  CONFIG  |  DEBUG
 *  FINE    |  DEBUG
 *  FINER   |  TRACE
 *  FINEST  |  TRACE
 *  ALL     |  ALL
 * </pre>
 * <p>
 * `FATAL` from previous bindings used by JMXFetch is now converted
 * to `LogLevel.ERROR`.
 * </p>
 */
public enum LogLevel {
    OFF(0, "OFF"),
    ERROR(1, "ERROR"),
    WARN(2, "WARN"),
    INFO(3, "INFO"),
    DEBUG(4, "DEBUG"),
    TRACE(5, "TRACE"),
    ALL(6, "ALL");

    private int level;
    private String label;
    private LogLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }

    // --

    /** fromJulLevel converts a java.util.logging.Level into a LogLevel. */
    public static LogLevel fromJulLevel(Level julLevel) {
        if (julLevel == Level.ALL) {
            return ALL;
        } else if (julLevel == Level.SEVERE) {
            return ERROR;
        } else if (julLevel == Level.WARNING) {
            return WARN;
        } else if (julLevel == Level.INFO) {
            return INFO;
        } else if (julLevel == Level.CONFIG) {
            return DEBUG;
        } else if (julLevel == Level.FINE) {
            return DEBUG;
        } else if (julLevel == Level.FINER) {
            return TRACE;
        } else if (julLevel == Level.FINEST) {
            return TRACE;
        } else if (julLevel == Level.OFF) {
            return OFF;
        }

        // should never happen but defaults to INFO
        return INFO;
    }

    /** fromString converts a string into a LogLevel, when not possible, it returns `INFO`. */
    public static LogLevel fromString(String str) {
        // compatibility
        if (str.toUpperCase().equals("FATAL")) {
            return ERROR;
        }
        for (LogLevel l : LogLevel.class.getEnumConstants()) {
            if (str.toUpperCase().equals(l.toString().toUpperCase())) {
                return l;
            }
        }

        // default to INFO
        return INFO;
    }

    /**
     * toJulLevel converts a LogLevel to a `java.util.logging.Level`.
     * This mapping needs to match http://slf4j.org/api/org/slf4j/impl/JDK14LoggerAdapter.html
     **/
    public Level toJulLevel() {
        switch (this) {
            case ALL:
                return Level.ALL;
            case ERROR:
                return Level.SEVERE;
            case WARN:
                return Level.WARNING;
            case INFO:
                return Level.INFO;
            case DEBUG:
                return Level.FINE;
            case TRACE:
                return Level.FINEST;
            case OFF:
                return Level.OFF;
            default:
                return Level.INFO;
        }
    }
}

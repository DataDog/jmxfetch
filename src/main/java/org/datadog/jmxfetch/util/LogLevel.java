package org.datadog.jmxfetch.util;

import java.util.logging.Level;

/**
 * LogLevel used for internal logging to match Datadog Agent levels.
 * Comparison table with java.util.logging:
 * <pre>
 *  JUL     |  JMXFetch LogLevel
 * ----------------------------
 *  ALL     |  ALL
 *  SEVERE  |  ERROR
 *  WARNING |  WARN
 *  INFO    |  INFO
 *  CONFIG  |  DEBUG
 *  FINE    |  DEBUG
 *  FINER   |  TRACE
 *  FINEST  |  TRACE
 *  OFF     |  OFF
 * </pre>
 */
public enum LogLevel {
    ALL(0, "ALL"),
    ERROR(1, "ERROR"),
    WARN(2, "WARN"),
    INFO(3, "INFO"),
    DEBUG(4, "DEBUG"),
    TRACE(5, "TRACE"),
    OFF(6, "OFF");

    private int level;
    private String label;
    private LogLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }

    // --

    /** contains returns if the given log level is contained.
     * If this level is `OFF`, `contains` returns `true` only if `other` is `OFF`
     * as well.
     */
    public boolean contains(LogLevel other) {
        if (this == OFF) {
            return this.level == other.level;
        }
        return this.level <= other.level;
    }

    /** fromJulLevel converts a java.util.logging.Level into a LogLevel */
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
        // TODO(remy): should we fallback on OFF?
        // (jaime): IMHO nope.
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
        // TODO(remy): should we fallback on OFF?
        // (jaime): IMHO nope.
        return INFO;
    }

    /** toJulLevel converts a LogLevel to a `java.util.logging.Level`. */
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
                return Level.CONFIG;
            case TRACE:
                return Level.FINER;
            case OFF:
                return Level.OFF;
            default:
                return Level.INFO;
        }
    }
}

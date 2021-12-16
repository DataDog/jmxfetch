package org.datadog.jmxfetch.util;

import java.util.logging.Level;

/**
 * LogLevel used for internal logging to match Datadog Agent levels.
 * Comparison table with java.util.logging:
 * <pre>
 *  JUG     |  JMXFetch LogLevel
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

    /** fromJugLevel converts a java.util.logging.Level into a LogLevel */
    public static LogLevel fromJugLevel(Level jugLevel) {
        if (jugLevel == Level.ALL) {
            return ALL;
        } else if (jugLevel == Level.SEVERE) {
            return ERROR;
        } else if (jugLevel == Level.WARNING) {
            return WARN;
        } else if (jugLevel == Level.INFO) {
            return INFO;
        } else if (jugLevel == Level.CONFIG) {
            return DEBUG;
        } else if (jugLevel == Level.FINE) {
            return DEBUG;
        } else if (jugLevel == Level.FINER) {
            return TRACE;
        } else if (jugLevel == Level.FINEST) {
            return TRACE;
        } else if (jugLevel == Level.OFF) {
            return OFF;
        }
        return INFO; // TODO(remy): should we fallback on OFF?
    }

    /** fromString converts a string into a LogLevel, when not possible, it returns `INFO`. */
    public static LogLevel fromString(String str) {
        for (LogLevel l : LogLevel.class.getEnumConstants()) {
            if (str == l.toString()) {
                return l;
            }
        }
        return INFO; // TODO(remy): should we fallback on OFF?
    }

    /** toJugLevel converts a LogLevel to a `java.util.logging.Level`. */
    public Level toJugLevel() {
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

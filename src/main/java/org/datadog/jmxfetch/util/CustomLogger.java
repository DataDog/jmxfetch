package org.datadog.jmxfetch.util;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.util.LogLevel;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


@Slf4j
public class CustomLogger {
    private static final ConcurrentHashMap<String, AtomicInteger> messageCount
            = new ConcurrentHashMap<String, AtomicInteger>();

    private static final String LAYOUT = "%d{yyyy-MM-dd HH:mm:ss z} | JMX | %-5p | %c{1} | %m%n";

    private static final String LAYOUT_RFC3339 =
        "%d{yyyy-MM-dd'T'HH:mm:ss'Z'} | JMX | %-5p | %c{1} | %m%n";

    private static final String JDK14_LAYOUT = "%1$tF %1$tTZ | JMX | %2$s | %3$s | %4$s%n";
    // FIXME(remy): todo, it is not rfc3339
    private static final String JDK14_LAYOUT_RFC3339 = "%1$tF %1$tTZ | JMX | %2$s | %3$s | %4$s%n";

    // TODO(remy):
    //   v code a LogLevel -> java.util.logging.Level in the enum
    //   v use LogLevel
    //   * exact same behavior as before concerning added handlers
    //   * extra handler configuration
    //   * proper error management
    //   * fix this timezone value
    //   * rfc3339 date support
    /** setup and configure the logging. */
    public static void setup(LogLevel level, String logLocation,
                             boolean logFormatRfc3339) {
        String target = "CONSOLE";

        // log format
        // --

        SimpleFormatter formatter = new SimpleFormatter() {
            private static final String format = JDK14_LAYOUT;
            private String simpleClassName(String str) {
                int idx = str.lastIndexOf('.');
                if (idx == -1 || idx + 1 == str.length()) {
                    return str;
                }
                return str.substring(idx + 1, str.length());
            }

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                    new Date(lr.getMillis()),
                    // NOTE(remy): may generate a lot of garbage
                    LogLevel.fromJugLevel(lr.getLevel()).toString(),
                    simpleClassName(lr.getSourceClassName()),
                    lr.getMessage()
                );
            }
        };

        // prepare the different handlers
        // --

        ConsoleHandler consoleHandler = null;
        FileHandler fileHandler = null;

        if (logLocation != null && logLocation.length() > 0) {
            // file logging
            try {
                // maximum one 5MB file
                fileHandler = new FileHandler(logLocation, 5 * 1024 * 1024, 1);
                fileHandler.setFormatter(formatter);
            } catch (Exception e) {
                fileHandler = null;
                // TODO(remy): !!!
            }

        }

        // TODO(remy):
        consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        consoleHandler.setLevel(level.toJugLevel());

        // should configure the root logger
        // ---

        Logger logger = Logger.getLogger("");

        // clean all existing hanlders
        for (Handler handler : logger.getHandlers()) {
            logger.removeHandler(handler);
        }

        // set our configured handlers
        if (fileHandler != null) {
            logger.addHandler(fileHandler);
        }
        if (consoleHandler != null) {
            logger.addHandler(consoleHandler);
        }
    }

    /** Laconic logging for reduced verbosity. */
    public static void laconic(org.slf4j.Logger logger, LogLevel level, String message, int max) {
        if (shouldLog(message, max)) {
            if (level.contains(LogLevel.ERROR) || level.contains(LogLevel.ALL)) {
                logger.error(message);
            } else if (level == LogLevel.WARN) {
                logger.warn(message);
            } else if (level == LogLevel.INFO) {
                logger.info(message);
            } else if (level == LogLevel.DEBUG) {
                logger.debug(message);
            }
        }
    }

    private static boolean shouldLog(String message, int max) {
        AtomicInteger count = messageCount.get(message);
        if (null == count) {
            count = new AtomicInteger();
            AtomicInteger winner = messageCount.putIfAbsent(message, count);
            if (winner != null) {
                count = winner;
            }
        }
        if (count.get() <= max) {
            // may log a little too often if there are races
            count.getAndIncrement();
            return true;
        }
        return false;
    }

    private CustomLogger() {}
}

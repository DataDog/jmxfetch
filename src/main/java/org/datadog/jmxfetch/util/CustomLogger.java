package org.datadog.jmxfetch.util;

import lombok.extern.slf4j.Slf4j;

import org.datadog.jmxfetch.util.LogLevel;
import org.datadog.jmxfetch.util.StdoutConsoleHandler;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


@Slf4j
public class CustomLogger {
    private static final Logger logger = Logger.getLogger("");

    private static final ConcurrentHashMap<String, AtomicInteger> messageCount
            = new ConcurrentHashMap<String, AtomicInteger>();

    private static final String DATE_JDK14_LAYOUT = "yyyy-MM-dd HH:mm:ss z";
    private static final String DATE_JDK14_LAYOUT_RFC3339 = "yyyy-MM-dd'T'HH:mm:ssXXX";
    private static final String JDK14_LAYOUT = "%s | JMX | %2$s | %3$s | %4$s%n";

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final int FILE_COUNT = 2;

    private static boolean isStdErr(String target) {
        List<String> stderrs = Arrays.asList("SYSTEM.ERR", "SYSTEM_ERR", "STDERR");
        return stderrs.contains(target.toUpperCase());
    }

    private static boolean isStdOut(String target) {
        List<String> stdouts = Arrays.asList("SYSTEM.OUT", "SYSTEM_OUT", "STDOUT");
        return stdouts.contains(target.toUpperCase());
    }

    /** setup and configure the logging. */
    public static synchronized void setup(LogLevel level, String logLocation,
                             boolean logFormatRfc3339) {
        String target = "CONSOLE";
        final String dateFormat = logFormatRfc3339 ? DATE_JDK14_LAYOUT_RFC3339 : DATE_JDK14_LAYOUT;
        final SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormat,
                                                            Locale.getDefault());
        final LogManager manager = LogManager.getLogManager();
        manager.reset();

        // log format
        SimpleFormatter formatter = new SimpleFormatter() {
            private static final String format = JDK14_LAYOUT;

            private String simpleClassName(String str) {
                int start = str.lastIndexOf('.');
                int end = str.indexOf('$');
                if (start == -1 || start + 1 == str.length()) {
                    return str;
                }
                if (end == -1 || end <= start || end > str.length()) {
                    end = str.length();
                }
                return str.substring(start + 1, end);
            }

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                    dateFormatter.format(new Date()).toString(),
                    LogLevel.fromJulLevel(lr.getLevel()).toString(),
                    simpleClassName(lr.getSourceClassName()),
                    lr.getMessage()
                );
            }
        };

        // log level
        Level julLevel = level.toJulLevel();

        // prepare the different handlers
        ConsoleHandler stdoutHandler = null;
        ConsoleHandler stderrHandler = null;
        FileHandler fileHandler = null;

        // the logLocation isn't always containing a file, it is sometimes
        // referring to a standard output. We want to create a FileHandler only
        // if the logLocation is a file on the FS.
        if (logLocation != null && logLocation.length() > 0) {
            if (!isStdOut(logLocation) && !isStdErr(logLocation)) {
                // file logging
                try {
                    // maximum one 5MB file
                    fileHandler = new FileHandler(logLocation, MAX_FILE_SIZE, FILE_COUNT);
                    fileHandler.setFormatter(formatter);
                    // note: fileHandler defaults to Level.ALL, so no need to set its log level
                } catch (Exception e) {
                    fileHandler = null;
                    log.error("can't open the file handler:", e);
                }
            } else if (isStdErr(logLocation)) {
                // console handler sending on stderr
                // note that ConsoleHandler is sending on System.err
                stderrHandler = new ConsoleHandler();
                stderrHandler.setFormatter(formatter);
                stderrHandler.setLevel(julLevel);
            }
        }

        // always have a console handler sending the logs to stdout
        stdoutHandler = new StdoutConsoleHandler();
        stdoutHandler.setFormatter(formatter);
        stdoutHandler.setLevel(julLevel);

        logger.setLevel(julLevel);

        // set our configured handlers
        if (fileHandler != null) {
            logger.addHandler(fileHandler);
        }
        if (stdoutHandler != null) { // always non-null but doesn't cost much
            logger.addHandler(stdoutHandler);
        }
        if (stderrHandler != null) {
            logger.addHandler(stderrHandler);
        }

        // filter all other log handlers
        for (Enumeration<String> logNames = manager.getLoggerNames() ;
                logNames.hasMoreElements() ; ) {
            String logName = logNames.nextElement();

            for (Handler handler : manager.getLogger(logName).getHandlers()) {
                if (handler != null) {

                    handler.setFilter(new Filter() {
                        @Override
                        public boolean isLoggable(LogRecord record) {
                            return false;
                        }
                    });
                }
            }
        }

        // set our jmxfetch handlers
        for (Handler handler : logger.getHandlers()) {
            handler.setFilter(new Filter() {
                @Override
                public boolean isLoggable(LogRecord record) {
                    String sourceClass = record.getSourceClassName();
                    return sourceClass.startsWith("org.datadog.jmxfetch");
                }
            });
        }

    }

    /** closeHandlers closes all opened handlers. */
    public static synchronized void shutdown() {
        for (Handler handler : logger.getHandlers()) {
            if (handler != null) {
                handler.close();
                logger.removeHandler(handler);
            }
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

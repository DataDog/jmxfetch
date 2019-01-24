package org.datadog.jmxfetch.util;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

public class CustomLogger {
    private static final Logger LOGGER = Logger.getLogger(CustomLogger.class.getName());
    private static final Multiset<String> messageCount = HashMultiset.create();
    private static final String LOGGER_LAYOUT = "%d | %-5p| %c{1} | %m%n";

    /**
     * Sets up the custom logger to the specified level and location.
     * */
    public static void setup(Level level, String logLocation) {
        if (logLocation != null
                && !ConsoleAppender.SYSTEM_ERR.equals(logLocation)
                && !ConsoleAppender.SYSTEM_OUT.equals(logLocation)) {
            RollingFileAppender fa = new RollingFileAppender();
            fa.setName("FileLogger");
            fa.setFile(logLocation);
            fa.setMaxFileSize("5MB");
            fa.setMaxBackupIndex(1);
            fa.setLayout(new PatternLayout(LOGGER_LAYOUT));
            fa.setThreshold(level);
            fa.setAppend(true);
            fa.activateOptions();
            Logger.getRootLogger().addAppender(fa);
            LOGGER.info("File Handler set");
        } else {
            ConsoleAppender consoleAppender = new ConsoleAppender(new PatternLayout(LOGGER_LAYOUT));
            if (logLocation != null) {
                consoleAppender.setTarget(logLocation);
            }
            consoleAppender.setThreshold(level);
            Logger.getRootLogger().addAppender(consoleAppender);
        }
    }

    /**
     * Laconic logging for reduced verbosity.
     * */
    public static void laconic(Logger logger, Level level, String message, int max) {
        if (messageCount.count(message) <= max) {
            logger.log(level, message);
            messageCount.add(message);
        }
    }

    private CustomLogger() {}
}

package org.datadog.jmxfetch.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class CustomLogger {
    private static final Logger LOGGER = Logger.getLogger(CustomLogger.class.getName());
    private static final Multiset<String> messageCount = HashMultiset.create();

    public static void setup(Level level, String logLocation) {
        if (logLocation != null) {
            RollingFileAppender fa = new RollingFileAppender();
            fa.setName("FileLogger");
            fa.setFile(logLocation);
            fa.setMaxFileSize("5MB");
            fa.setMaxBackupIndex(1);
            fa.setLayout(new PatternLayout("%d | %-5p| %c{1} | %m%n"));
            fa.setThreshold(level);
            fa.setAppend(true);
            fa.activateOptions();
            Logger.getRootLogger().addAppender(fa);
            LOGGER.info("File Handler set");
        } else {
            System.out.println("Log location is not set, not logging to file");
        }
    }

    public static void laconic(Logger logger, Level level, String message, int max) {
        if (messageCount.count(message) <= max) {
            logger.log(level, message);
            messageCount.add(message);
        }
    }

    private CustomLogger() {}
} 
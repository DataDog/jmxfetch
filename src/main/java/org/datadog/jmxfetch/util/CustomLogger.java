package org.datadog.jmxfetch.util;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;

public class CustomLogger {
    private static final Logger LOGGER = Logger.getLogger(CustomLogger.class.getName());
    private static final Multiset<String> messageCount = HashMultiset.create();

    public static void setup(Level level, String logLocation) {
        if (logLocation != null) {
            FileAppender fa = new FileAppender();
            fa.setName("FileLogger");
            fa.setFile(logLocation);
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

    public static void log(Logger logger, Level level, String message, int max) {
        if (messageCount.count(message) <= max) {
            logger.log(level, message);
            messageCount.add(message);
        }
    }

} 
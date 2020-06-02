package org.datadog.jmxfetch.util;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Slf4j
public class CustomLogger {
    private static final Multiset<String> messageCount = HashMultiset.create();
    private static final String LOGGER_LAYOUT = "%d{yyyy-MM-dd HH:mm:ss z} | JMX | %-5p | %c{1} | %m%n";
    // log4j2 uses SYSTEM_OUT and SYSTEM_ERR - support both
    private static final String SYSTEM_OUT_ALT = "STDOUT";
    private static final String SYSTEM_ERR_ALT = "STDERR";

    /** Sets up the custom logger to the specified level and location. */
    public static void setup(Level level, String logLocation) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();
        String target = "CONSOLE";

        if (logLocation != null
                && !ConsoleAppender.Target.SYSTEM_ERR.toString().equals(logLocation)
                && !SYSTEM_ERR_ALT.equals(logLocation)
                && !ConsoleAppender.Target.SYSTEM_OUT.toString().equals(logLocation)
                && !SYSTEM_OUT_ALT.equals(logLocation)) {

            target = "FileLogger";

            PatternLayout layout = PatternLayout.newBuilder()
                .withConfiguration(config)
                .withPattern(LOGGER_LAYOUT)
                .build();

            RollingFileAppender fa = RollingFileAppender.newBuilder()
                .setConfiguration(config)
                .withName(target)
                .withLayout(layout)
                .withFileName(logLocation)
                .withFilePattern(logLocation + ".%d")
                .withPolicy(SizeBasedTriggeringPolicy.createPolicy("5MB"))
                .withStrategy(DefaultRolloverStrategy.newBuilder().withMax("1").build())
                .build();

            fa.start();
            config.addAppender(fa);
            ctx.getRootLogger().addAppender(config.getAppender(fa.getName()));

            log.info("File Handler set");
        } else {

            if (logLocation != null
                    && (ConsoleAppender.Target.SYSTEM_ERR.toString().equals(logLocation)
                        || SYSTEM_ERR_ALT.equals(logLocation))) {

                ConsoleAppender console = (ConsoleAppender)config.getAppender("CONSOLE");
                console.stop();
                config.getRootLogger().removeAppender("CONSOLE");
                ctx.updateLoggers();

                PatternLayout layout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern(LOGGER_LAYOUT)
                    .build();

                ConsoleAppender ca = ConsoleAppender.newBuilder()
                    .setConfiguration(config)
                    .withName(logLocation)
                    .setTarget(ConsoleAppender.Target.SYSTEM_ERR)
                    .withLayout(layout)
                    .build();

                ca.start();
                config.addAppender(ca);
                ctx.getRootLogger().addAppender(config.getAppender(ca.getName()));
            }
        }

        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(level);
        ctx.updateLoggers();
    }

    /** Laconic logging for reduced verbosity. */
    public static void laconic(org.slf4j.Logger logger, Level level, String message, int max) {
        if (messageCount.count(message) <= max) {
            if (level.isInRange(Level.ERROR, Level.ALL)) {
                logger.error(message);
            } else if (level == Level.WARN) {
                logger.warn(message);
            } else if (level == Level.INFO) {
                logger.info(message);
            } else if (level == Level.DEBUG) {
                logger.debug(message);
            }

            messageCount.add(message);
        }
    }

    private CustomLogger() {}
}

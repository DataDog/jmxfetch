package org.datadog.jmxfetch.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class LogLevelTest {

    @Parameterized.Parameters
    public static Iterable<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                {"ALL", LogLevel.ALL, Level.ALL},
                {"DEBUG", LogLevel.DEBUG, Level.FINE},
                {"TRACE", LogLevel.TRACE, Level.FINEST},
                {"INFO", LogLevel.INFO, Level.INFO},
                {"WARN", LogLevel.WARN, Level.WARNING},
                {"ERROR", LogLevel.ERROR, Level.SEVERE},
                {"OFF", LogLevel.OFF, Level.OFF},
        });
    }

    private final String configLogLevel;
    private final LogLevel expectedLogLevel;
    private final Level expectedJulLevel;

    public LogLevelTest(String configLogLevel, LogLevel expectedLogLevel, Level expectedJulLevel) {
        this.configLogLevel = configLogLevel;
        this.expectedLogLevel = expectedLogLevel;
        this.expectedJulLevel = expectedJulLevel;
    }

    @Test
    public void testFromStringToJulLevel() {
        LogLevel logLevel = LogLevel.fromString(configLogLevel);
        assertEquals(expectedLogLevel, logLevel);
        assertEquals(expectedJulLevel, logLevel.toJulLevel());
    }

}

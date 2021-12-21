package org.datadog.jmxfetch.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void testContains() {
        assertTrue(LogLevel.ALL.contains(LogLevel.TRACE));
        assertTrue(LogLevel.ALL.contains(LogLevel.DEBUG));
        assertTrue(LogLevel.ALL.contains(LogLevel.INFO));
        assertTrue(LogLevel.ALL.contains(LogLevel.WARN));
        assertTrue(LogLevel.ALL.contains(LogLevel.ERROR));

        assertTrue(LogLevel.TRACE.contains(LogLevel.TRACE));
        assertTrue(LogLevel.TRACE.contains(LogLevel.DEBUG));
        assertTrue(LogLevel.TRACE.contains(LogLevel.INFO));
        assertTrue(LogLevel.TRACE.contains(LogLevel.WARN));
        assertTrue(LogLevel.TRACE.contains(LogLevel.ERROR));

        assertFalse(LogLevel.DEBUG.contains(LogLevel.TRACE));
        assertTrue(LogLevel.DEBUG.contains(LogLevel.DEBUG));
        assertTrue(LogLevel.DEBUG.contains(LogLevel.INFO));
        assertTrue(LogLevel.DEBUG.contains(LogLevel.WARN));
        assertTrue(LogLevel.DEBUG.contains(LogLevel.ERROR));

        assertFalse(LogLevel.INFO.contains(LogLevel.TRACE));
        assertFalse(LogLevel.INFO.contains(LogLevel.DEBUG));
        assertTrue(LogLevel.INFO.contains(LogLevel.INFO));
        assertTrue(LogLevel.INFO.contains(LogLevel.WARN));
        assertTrue(LogLevel.INFO.contains(LogLevel.ERROR));

        assertFalse(LogLevel.WARN.contains(LogLevel.TRACE));
        assertFalse(LogLevel.WARN.contains(LogLevel.DEBUG));
        assertFalse(LogLevel.WARN.contains(LogLevel.INFO));
        assertTrue(LogLevel.WARN.contains(LogLevel.WARN));
        assertTrue(LogLevel.WARN.contains(LogLevel.ERROR));

        assertFalse(LogLevel.ERROR.contains(LogLevel.TRACE));
        assertFalse(LogLevel.ERROR.contains(LogLevel.DEBUG));
        assertFalse(LogLevel.ERROR.contains(LogLevel.INFO));
        assertFalse(LogLevel.ERROR.contains(LogLevel.WARN));
        assertTrue(LogLevel.ERROR.contains(LogLevel.ERROR));

        assertFalse(LogLevel.OFF.contains(LogLevel.TRACE));
        assertFalse(LogLevel.OFF.contains(LogLevel.DEBUG));
        assertFalse(LogLevel.OFF.contains(LogLevel.INFO));
        assertFalse(LogLevel.OFF.contains(LogLevel.WARN));
        assertFalse(LogLevel.OFF.contains(LogLevel.ERROR));

        assertFalse(LogLevel.TRACE.contains(LogLevel.OFF));
        assertFalse(LogLevel.DEBUG.contains(LogLevel.OFF));
        assertFalse(LogLevel.INFO.contains(LogLevel.OFF));
        assertFalse(LogLevel.WARN.contains(LogLevel.OFF));
        assertFalse(LogLevel.ERROR.contains(LogLevel.OFF));
        assertTrue(LogLevel.OFF.contains(LogLevel.OFF));
    }
}

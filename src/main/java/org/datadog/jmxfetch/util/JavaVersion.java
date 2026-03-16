package org.datadog.jmxfetch.util;

public final class JavaVersion {
    private static final int JAVA_VERSION = getMajorJavaVersion();

    private JavaVersion() {}

    /**
     * Tests whether the Java version is at least the expected version.
     *
     * @param expectedVersion the expected version
     * @return {@code true} if the Java version is at least the expected version
     */
    public static boolean atLeastJava(int expectedVersion) {
        return expectedVersion <= JAVA_VERSION;
    }

    private static int getMajorJavaVersion() {
        try {
            return parseMajorJavaVersion(System.getProperty("java.version"));
        } catch (Throwable e) {
            return 7; // assume Java 7, i.e. the lowest version JMXFetch supports
        }
    }

    private static int parseMajorJavaVersion(String str) {
        int value = 0;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch >= '0' && ch <= '9') {
                value = value * 10 + (ch - '0');
            } else if (ch == '.') {
                if (value == 1) {
                    value = 0; // skip leading 1.
                } else {
                    break;
                }
            } else {
                throw new NumberFormatException();
            }
        }
        return value;
    }
}

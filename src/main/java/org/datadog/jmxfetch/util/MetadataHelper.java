package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.util.Properties;

public class MetadataHelper {
    /**  Returns our own version number. */
    public static String getVersion() {
        try {
            final Properties properties = new Properties();
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            properties.load(classLoader.getResourceAsStream("project.properties"));
            return properties.getProperty("version");
        } catch (IOException e) {
            e.printStackTrace();
            return "?.?.?";
        }
    }
}
package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MetadataHelper {
    /**  Returns our own version number. */
    public static String getVersion() {
        final Properties properties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream("project.properties")) {
            properties.load(stream);
            return properties.getProperty("version");
        } catch (IOException e) {
            e.printStackTrace();
            return "?.?.?";
        }
    }
}

package org.datadog.jmxfetch.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MetadataHelper {
    /**  Returns our own version number. */
    public static String getVersion() {
        final Properties properties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream stream = classLoader.getResourceAsStream("project.properties");
        try {
            properties.load(stream);
            stream.close();
            return properties.getProperty("version");
        } catch (IOException e) {
            try {
                stream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            return "?.?.?";
        }
    }
}

package org.datadog.jmxfetch;

import com.beust.jcommander.JCommander;
import org.apache.log4j.Level;
import org.datadog.jmxfetch.util.CustomLogger;

public class CommonTestSetup {
    public static void setupLogger() {
        CustomLogger.setup(Level.toLevel("ALL"), "/tmp/jmxfetch_test.log");
    }

    public static App initApp(String yamlFileName, AppConfig appConfig){
        // We do a first collection
        // We initialize the main app that will collect these metrics using JMX
        String confdDirectory = Thread.currentThread().getContextClassLoader().getResource(yamlFileName).getPath();
        confdDirectory = new String(confdDirectory.substring(0, confdDirectory.length() - yamlFileName.length()));
        String[] params = {"--reporter", "console", "-c", yamlFileName, "--conf_directory", confdDirectory, "collect"};
        new JCommander(appConfig, params);

        App app = new App(appConfig);
        app.init(false);

        return app;
    }
}

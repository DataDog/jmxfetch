package org.datadog.jmxfetch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import lombok.extern.slf4j.Slf4j;
import org.datadog.jmxfetch.util.CustomLogger;
import org.datadog.jmxfetch.util.LogLevel;
import org.datadog.jmxfetch.util.MetadataHelper;

@Slf4j
public class JmxFetch {
    /**
     * Main entry of JMXFetch.
     *
     * <p>See AppConfig class for more details on the args
     */
    public static void main(String[] args) {

        // Load the config from the args
        AppConfig config = AppConfig.builder().build();
        JCommander commander = null;
        try {
            // Try to parse the args using JCommander
            commander = new JCommander(config, args);
        } catch (ParameterException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }

        // Display the version and quit
        if (config.isVersion() || AppConfig.ACTION_VERSION.equals(config.getAction())) {
            JCommander.getConsole().println("JMX Fetch " + MetadataHelper.getVersion());
            System.exit(0);
        }

        // Display the help and quit
        if (config.isHelp() || AppConfig.ACTION_HELP.equals(config.getAction())) {
            commander.usage();
            System.exit(0);
        }

        {
            // Running these commands here because they are logging specific,
            // not needed in dd-java-agent, which calls run directly.

            // Set up the logger to add file handler
            CustomLogger.setup(LogLevel.fromString(config.getLogLevel()),
                config.getLogLocation(),
                config.isLogFormatRfc3339());

            // Set up the shutdown hook to properly close resources
            attachShutdownHook();
        }

        App app = new App(config);
        System.exit(app.run());
    }

    /** Attach a Shutdown Hook that will be called when SIGTERM is sent to JMXFetch. */
    private static void attachShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
                new Thread() {
                    @Override
                    public void run() {
                        log.info("JMXFetch is closing");
                        // make sure log handlers are properly closed
                        CustomLogger.shutdown();
                    }
                }
        );
    }
}

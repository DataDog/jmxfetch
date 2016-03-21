package org.datadog.jmxfetch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.StatsdReporter;
import org.datadog.jmxfetch.validator.Log4JLevelValidator;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.*;

public class TestParsingJCommander {

    private static final String CONF_DIR = "/conf/dir";
    private static final String LOG_LOCATION = "/log/location";
    private static final String REPORTER_CONSOLE = "console";
    private static final String SINGLE_CHECK = "jmx.yaml";
    private static final List<String> MULTI_CHECK = Arrays.asList("jmx.yaml", "jmx-2.yaml");
    private static final String STATUS_LOCATION = "/status/status_location";
    private static final String EXIT_FILE_LOCATION = "/status/exit_locationt";

    private static AppConfig testCommand(String[] params) throws ParameterException {
        AppConfig appConfig = new AppConfig();
        new JCommander(appConfig, params);
        return appConfig;
    }

    @Test
    public void testParsingHelp() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "--help", LOG_LOCATION,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertTrue(appConfig.isHelp());
    }

    @Test
    public void testParsingLogLevel() {
        for (String logLevel : Log4JLevelValidator.LOG4J_LEVELS) {
            String[] params = new String[]{
                    "--reporter", REPORTER_CONSOLE,
                    "--check", SINGLE_CHECK,
                    "--conf_directory", CONF_DIR,
                    "-l", LOG_LOCATION,
                    "-L", logLevel,
                    AppConfig.ACTION_COLLECT
            };
            AppConfig appConfig = testCommand(params);
            assertEquals(logLevel, appConfig.getLogLevel());
        }
        // invalid log level
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "--log_level", "invalid_log_level",
                AppConfig.ACTION_COLLECT
        };
        try {
            testCommand(params);
            fail("Should have failed because log level is invalid");
        } catch (ParameterException pe) {
            assertEquals("Parameter --log_level should be in (ALL,DEBUG,ERROR,FATAL,INFO,OFF,TRACE,LEVEL,WARN)",
                    pe.getMessage());
        }
    }

    @Test
    public void testParsingLogLocation() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "--log_location", LOG_LOCATION,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertEquals(LOG_LOCATION, appConfig.getLogLocation());
    }

    @Test
    public void testParsingConfDirectory() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertEquals(CONF_DIR, appConfig.getConfdDirectory());
    }

    @Test
    public void testParsingReporter() {
        // console reporter
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertNotNull(appConfig.getReporter());
        assertTrue(appConfig.getReporter() instanceof ConsoleReporter);

        // statsd reporter
        params = new String[]{
                "--reporter", "statsd:10",
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        appConfig = testCommand(params);
        assertNotNull(appConfig.getReporter());
        assertTrue(appConfig.getReporter() instanceof StatsdReporter);
        assertEquals("localhost", ((StatsdReporter) appConfig.getReporter()).getStatsdHost());
        assertEquals(10, ((StatsdReporter) appConfig.getReporter()).getStatsdPort());

        // statsd reporter with custom ipv4 host
        params = new String[]{
                "--reporter", "statsd:127.0.0.1:10",
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        appConfig = testCommand(params);
        assertNotNull(appConfig.getReporter());
        assertTrue(appConfig.getReporter() instanceof StatsdReporter);
        assertEquals("127.0.0.1", ((StatsdReporter) appConfig.getReporter()).getStatsdHost());
        assertEquals(10, ((StatsdReporter) appConfig.getReporter()).getStatsdPort());

        // statsd reporter with custom ipv6 host
        params = new String[]{
                "--reporter", "statsd:[::1]:10",
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        appConfig = testCommand(params);
        assertNotNull(appConfig.getReporter());
        assertTrue(appConfig.getReporter() instanceof StatsdReporter);
        assertEquals("[::1]", ((StatsdReporter) appConfig.getReporter()).getStatsdHost());
        assertEquals(10, ((StatsdReporter) appConfig.getReporter()).getStatsdPort());

        // invalid reporter
        params = new String[]{
                "--reporter", "invalid_reporter",
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        try {
            testCommand(params);
            fail("Should have failed because reporter is invalid");
        } catch (ParameterException pe) {
            assertEquals("Parameter --reporter should be either 'console', 'statsd:[STATSD_PORT]' or 'statsd:[STATSD_HOST]:[STATSD_PORT]'", pe.getMessage());
        }

        // invalid port
        params = new String[]{
                "-r", "statsd:-1",
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        try {
            testCommand(params);
            fail("Should have failed because statsd reporter port is invalid");
        } catch (ParameterException pe) {
            assertEquals("Statsd Port should be a positive integer (found -1)", pe.getMessage());
        }
    }

    @Test
    public void testParsingYamlFileList() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertEquals(Arrays.asList(SINGLE_CHECK), appConfig.getYamlFileList());

        // Invalid check period
        params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "-c", Joiner.on(",").join(MULTI_CHECK),
                "--conf_directory", CONF_DIR,
                AppConfig.ACTION_COLLECT
        };
        appConfig = testCommand(params);
        assertEquals(MULTI_CHECK, appConfig.getYamlFileList());
    }

    @Test
    public void testParsingCheckPeriod() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "-s", STATUS_LOCATION,
                "--check_period", "20",
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertEquals(20, appConfig.getCheckPeriod());

        // Invalid check period
        params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "-s", STATUS_LOCATION,
                "-p", "not_a_number",
                AppConfig.ACTION_COLLECT
        };
        try {
            testCommand(params);
            fail("Should have failed because check period is invalid");
        } catch (ParameterException pe) {
            assertEquals("Parameter --check_period should be an integer (found not_a_number)", pe.getMessage());
        }

        // non-positive check period
        params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "-s", STATUS_LOCATION,
                "--check_period", "0",
                AppConfig.ACTION_COLLECT
        };
        try {
            testCommand(params);
            fail("Should have failed because check period is non-positive");
        } catch (ParameterException pe) {
            assertEquals("Parameter --check_period should be positive (found 0)", pe.getMessage());
        }
    }

    @Test
    public void testParsingStatus() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "--status_location", STATUS_LOCATION,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertNotNull(appConfig.getStatus());
        assertEquals(STATUS_LOCATION, appConfig.getStatus().getStatusFileLocation());
        assertTrue(appConfig.getStatus().isEnabled());
    }

    @Test
    public void testParsingExitWatcher() {
        String[] params = new String[]{
                "--reporter", REPORTER_CONSOLE,
                "--check", SINGLE_CHECK,
                "--conf_directory", CONF_DIR,
                "--exit_file_location", EXIT_FILE_LOCATION,
                AppConfig.ACTION_COLLECT
        };
        AppConfig appConfig = testCommand(params);
        assertNotNull(appConfig.getExitWatcher());
        assertEquals(EXIT_FILE_LOCATION, appConfig.getExitWatcher().getExitFileLocation());
        assertTrue(appConfig.getExitWatcher().isEnabled());
    }

    @Test
    public void testParsingAction() {
        // Positive cases
        String[] params;
        for (String action : AppConfig.ACTIONS) {
            params = new String[]{
                    "--reporter", REPORTER_CONSOLE,
                    "--check", SINGLE_CHECK,
                    "--conf_directory", CONF_DIR,
                    action
            };
            try {
                AppConfig appConfig = testCommand(params);
                assertEquals(action, appConfig.getAction());
            } catch (ParameterException pe) {
                fail("Failed to parse a valid action: " + action);
            }
        }

        // No Action
        params = new String[]{
                "-r", REPORTER_CONSOLE,
                "-D", CONF_DIR,
                "-c", SINGLE_CHECK
        };
        try {
            testCommand(params);
            fail("Should have failed because action was not provided.");
        } catch (ParameterException pe) {
            String expectedMessage = "Main parameters are required (\"Action to take, should be in [help, collect, " +
                    "list_everything, list_collected_attributes, list_matching_attributes, " +
                    "list_not_matching_attributes, list_limited_attributes]\")";
            assertEquals(expectedMessage, pe.getMessage());
        }

        // Invalid action
        params = new String[]{
                "-r", REPORTER_CONSOLE,
                "-D", CONF_DIR,
                "-c", SINGLE_CHECK,
                "invalid_action"
        };
        try {
            testCommand(params);
            fail("Should have failed because action is not a valid one");
        } catch (ParameterException pe) {
            String expectedMessage = "Main parameters are required (\"Action to take, should be in [help, collect, " +
                    "list_everything, list_collected_attributes, list_matching_attributes, " +
                    "list_not_matching_attributes, list_limited_attributes]\")";
            assertEquals(expectedMessage, pe.getMessage());
        }
    }
}

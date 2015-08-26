package org.datadog.jmxfetch;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.datadog.jmxfetch.converter.ExitWatcherConverter;
import org.datadog.jmxfetch.converter.ReporterConverter;
import org.datadog.jmxfetch.converter.StatusConverter;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.validator.Log4JLevelValidator;
import org.datadog.jmxfetch.validator.PositiveIntegerValidator;
import org.datadog.jmxfetch.validator.ReporterValidator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=")
class AppConfig {
    public static final String ACTION_COLLECT = "collect";
    public static final String ACTION_LIST_EVERYTHING = "list_everything";
    public static final String ACTION_LIST_COLLECTED = "list_collected_attributes";
    public static final String ACTION_LIST_MATCHING = "list_matching_attributes";
    public static final String ACTION_LIST_NOT_MATCHING = "list_not_matching_attributes";
    public static final String ACTION_LIST_LIMITED = "list_limited_attributes";
    public static final String ACTION_HELP = "help";
    public static final HashSet<String> ACTIONS = new HashSet<String>(Arrays.asList(ACTION_COLLECT, ACTION_LIST_EVERYTHING,
            ACTION_LIST_COLLECTED, ACTION_LIST_MATCHING, ACTION_LIST_NOT_MATCHING, ACTION_LIST_LIMITED, ACTION_HELP));

    @Parameter(names = {"--help", "-h"},
            description = "Display this help page",
            help = true)
    private boolean help;

    @Parameter(names = {"--log_level", "-L"},
            description = "Level of verbosity",
            validateWith = Log4JLevelValidator.class,
            required = false)
    private String logLevel = "INFO";

    @Parameter(names = {"--log_location", "-l"},
            description = "Absolute path of the log file (default to null = no logging)",
            required = false)
    private String logLocation;

    @Parameter(names = {"--conf_directory", "-D"},
            description = "Absolute path to the conf.d directory",
            required = true)
    private String confdDirectory;

    @Parameter(names = {"--reporter", "-r"},
            description = "Reporter to use: should be either \"statsd:[STATSD_PORT]\" or \"console\"",
            validateWith = ReporterValidator.class,
            converter = ReporterConverter.class,
            required = false)
    private Reporter reporter;

    @Parameter(names = {"--check", "-c"},
            description = "Yaml file name to read (must be in the confd directory)",
            required = true,
            variableArity = true)
    private List<String> yamlFileList;

    @Parameter(names = {"--check_period", "-p"},
            description = "Sleeping time during two iterations in ms",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    private int checkPeriod = 15000;

    @Parameter(names = {"--status_location", "-s"},
            description = "Absolute path of the status file. (default to null = no status file written)",
            converter = StatusConverter.class,
            required = false)
    private Status status = new Status();

    @Parameter(names = {"--exit_file_location", "-e"},
            description = "Absolute path of the trigger file to watch to exit. (default to null = no exit on file)",
            converter = ExitWatcherConverter.class,
            required = false)
    private ExitWatcher exitWatcher = new ExitWatcher();

    @Parameter(description = "Action to take, should be in [help, collect, " +
            "list_everything, list_collected_attributes, list_matching_attributes, " +
            "list_not_matching_attributes, list_limited_attributes]",
            required = true)
    private List<String> action = null;

    public String getAction() {
        return this.action.get(0);
    }

    public boolean isConsoleReporter() {
        return reporter != null && (reporter instanceof ConsoleReporter);
    }

    public boolean isHelp() {
        return help;
    }

    public Status getStatus() {
        return status;
    }

    public ExitWatcher getExitWatcher(){
    	return exitWatcher;
    }

    public int getCheckPeriod() {
        return checkPeriod;
    }

    public Reporter getReporter() {
        return reporter;
    }

    public List<String> getYamlFileList() {
        return yamlFileList;
    }

    public String getConfdDirectory() {
        return confdDirectory;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getLogLocation() {
        return logLocation;
    }
}

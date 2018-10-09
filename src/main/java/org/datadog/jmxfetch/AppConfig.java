package org.datadog.jmxfetch;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.datadog.jmxfetch.converter.ExitWatcherConverter;
import org.datadog.jmxfetch.converter.ReporterConverter;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.reporter.ReporterFactory;
import org.datadog.jmxfetch.validator.Log4JLevelValidator;
import org.datadog.jmxfetch.validator.PositiveIntegerValidator;
import org.datadog.jmxfetch.validator.ReporterValidator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=")
public class AppConfig {
    public static final String ACTION_COLLECT = "collect";
    public static final String ACTION_LIST_JVMS = "list_jvms";
    public static final String ACTION_LIST_EVERYTHING = "list_everything";
    public static final String ACTION_LIST_COLLECTED = "list_collected_attributes";
    public static final String ACTION_LIST_MATCHING = "list_matching_attributes";
    public static final String ACTION_LIST_NOT_MATCHING = "list_not_matching_attributes";
    public static final String ACTION_LIST_LIMITED = "list_limited_attributes";
    public static final String ACTION_HELP = "help";
    public static final HashSet<String> ACTIONS = new HashSet<String>(Arrays.asList(ACTION_COLLECT, ACTION_LIST_EVERYTHING,
            ACTION_LIST_COLLECTED, ACTION_LIST_MATCHING, ACTION_LIST_NOT_MATCHING, ACTION_LIST_LIMITED, ACTION_HELP, ACTION_LIST_JVMS));

    private static final String AD_WIN_PIPE_PATH = "\\\\.\\pipe\\";
    private static final String AD_LEGACY_PIPE_NAME = "dd-service_discovery";
    private static final String AD_PIPE_NAME = "dd-auto_discovery";
    private static final String AD_LAUNCH_FILE = "jmx.launch";

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
            required = false)
    private String confdDirectory;

    @Parameter(names = {"--tmp_directory", "-T"},
            description = "Absolute path to a temporary directory",
            required = false)
    private String tmpDirectory = "/tmp";

    @Parameter(names = {"--reporter", "-r"},
            description = "Reporter to use: should be either \"statsd:[STATSD_PORT]\" or \"console\"",
            validateWith = ReporterValidator.class,
            converter = ReporterConverter.class,
            required = false)
    private Reporter reporter;

    @Parameter(names = {"--check", "-c"},
            description = "Yaml file name to read (must be in the confd directory)",
            required = false,
            variableArity = true)
    private List<String> yamlFileList;

    @Parameter(names = {"--check_period", "-p"},
            description = "Sleeping time during two iterations in ms",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    private int checkPeriod = 15000;

    @Parameter(names = {"--ad_enabled", "--sd_enabled", "-w"},
            description = "Enable Auto Discovery.",
            required = false)
    private boolean adEnabled = false;

    @Parameter(names = {"--ad_pipe", "--sd_pipe", "-P"},
            description = "Auto Discovery pipe name.",
            required = false)
    private String adPipe = AD_PIPE_NAME;

    @Parameter(names = {"--status_location", "-s"},
            description = "Absolute path of the status file. (default to null = no status file written)",
            required = false)
    private String statusLocation;

    @Parameter(names = {"--exit_file_location", "-e"},
            description = "Absolute path of the trigger file to watch to exit. (default to null = no exit on file)",
            converter = ExitWatcherConverter.class,
            required = false)
    private ExitWatcher exitWatcher = new ExitWatcher();

    @Parameter(description = "Action to take, should be in [help, collect, " +
            "list_everything, list_collected_attributes, list_matching_attributes, " +
            "list_not_matching_attributes, list_limited_attributes, list_jvms]",
            required = true)
    private List<String> action = null;

    @Parameter(names = {"--ipc_host", "-H"},
            description = "IPC host",
            required = false)
    private String ipcHost;

    @Parameter(names = {"--ipc_port", "-I"},
            description = "IPC port",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    private int ipcPort = 0;

    // This is used by things like APM agent to provide configuration from resources
    private List<String> instanceConfigResources;
    // This is used by things like APM agent to provide metric configuration from files
    private List<String> metricConfigFiles;
    // This is used by things like APM agent to provide global override for bean refresh period
    private Integer refreshBeansPeriod;

    private Status status = new Status();

    public boolean updateStatus() {
        if (statusLocation != null) {
            status = new Status(statusLocation);
            return true;
        } else if (ipcHost != null && ipcPort > 0) {
            status = new Status(ipcHost, ipcPort);
            return true;
        }

        return false;
    }

    public boolean remoteEnabled() {
        return (ipcHost != null && ipcPort > 0);
    }

    public String getStatusLocation() {
        return this.statusLocation;
    }

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

    public String getIPCHost() {
        return ipcHost;
    }

    public int getIPCPort() {
        return ipcPort;
    }

    public boolean getAutoDiscoveryPipeEnabled() {
        return adEnabled;
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

    public String getTmpDirectory() {
        return tmpDirectory;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getLogLocation() {
        return logLocation;
    }

    public String getAutoDiscoveryPipe() {
        String pipePath;

        if (System.getProperty("os.name").startsWith("Windows")) {
            pipePath = AD_WIN_PIPE_PATH + "/" + adPipe;
        } else {
            pipePath = getTmpDirectory() + "/" + adPipe;
        }
        return pipePath;
    }

    public String getJMXLaunchFile() {
        return getTmpDirectory() + "/" + AD_LAUNCH_FILE;
    }

    public List<String> getInstanceConfigResources() {
        return instanceConfigResources;
    }

    public List<String> getMetricConfigFiles() {
        return metricConfigFiles;
    }

    public Integer getRefreshBeansPeriod() {
        return refreshBeansPeriod;
    }

    /**
     * Factory method used by dd-tracer-agent to run jmxfetch in the same process
     */
    public static AppConfig create(
            List<String> instanceConfigResources,
            List<String> metricConfigFiles,
            Integer checkPeriod,
            Integer refreshBeansPeriod,
            String reporter,
            String logLocation,
            String logLevel) {
        AppConfig config = new AppConfig();
        config.action = ImmutableList.of(ACTION_COLLECT);
        config.instanceConfigResources = ImmutableList.copyOf(instanceConfigResources);
        config.metricConfigFiles = ImmutableList.copyOf(metricConfigFiles);
        if (checkPeriod != null) {
            config.checkPeriod = checkPeriod;
        }
        config.refreshBeansPeriod = refreshBeansPeriod;
        config.reporter = ReporterFactory.getReporter(reporter);
        config.logLocation = logLocation;
        config.logLevel = logLevel;
        return config;
    }
}

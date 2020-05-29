package org.datadog.jmxfetch;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import lombok.Builder;
import lombok.ToString;
import org.datadog.jmxfetch.converter.ExitWatcherConverter;
import org.datadog.jmxfetch.converter.ReporterConverter;
import org.datadog.jmxfetch.reporter.ConsoleReporter;
import org.datadog.jmxfetch.reporter.JsonReporter;
import org.datadog.jmxfetch.reporter.Reporter;
import org.datadog.jmxfetch.validator.Log4JLevelValidator;
import org.datadog.jmxfetch.validator.PositiveIntegerValidator;
import org.datadog.jmxfetch.validator.ReporterValidator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Builder
@ToString
@Parameters(separators = "=")
public class AppConfig {
    public static final String ACTION_COLLECT = "collect";
    public static final String ACTION_LIST_JVMS = "list_jvms";
    public static final String ACTION_LIST_EVERYTHING = "list_everything";
    public static final String ACTION_LIST_COLLECTED = "list_collected_attributes";
    public static final String ACTION_LIST_MATCHING = "list_matching_attributes";
    public static final String ACTION_LIST_WITH_METRICS = "list_with_metrics";
    public static final String ACTION_LIST_WITH_RATE_METRICS = "list_with_rate_metrics";
    public static final String ACTION_LIST_NOT_MATCHING = "list_not_matching_attributes";
    public static final String ACTION_LIST_LIMITED = "list_limited_attributes";
    public static final String ACTION_HELP = "help";
    public static final String ACTION_VERSION = "version";
    public static final HashSet<String> ACTIONS =
            new HashSet<String>(
                    Arrays.asList(
                            ACTION_COLLECT,
                            ACTION_LIST_EVERYTHING,
                            ACTION_LIST_COLLECTED,
                            ACTION_LIST_MATCHING,
                            ACTION_LIST_WITH_METRICS,
                            ACTION_LIST_WITH_RATE_METRICS,
                            ACTION_LIST_NOT_MATCHING,
                            ACTION_LIST_LIMITED,
                            ACTION_HELP,
                            ACTION_VERSION,
                            ACTION_LIST_JVMS));

    private static final String AD_WIN_PIPE_PATH = "\\\\.\\pipe\\";
    private static final String AD_PIPE_NAME = "dd-auto_discovery";
    private static final String AD_LAUNCH_FILE = "jmx.launch";

    private static final int DEFAULT_THREAD_POOL_SIZE = 3;
    private static final int DEFAULT_COLLECTION_TO_S = 60;
    private static final int DEFAULT_RECONNECTION_TO_S = 15;
    private static final int DEFAULT_LOG_FILE_MAX_SIZE= 10485760;
    private static final int DEFAULT_LOG_FILE_MAX_ROLLS= 1;

    @Parameter(
            names = {"--help", "-h"},
            description = "Display this help page",
            help = true)
    private boolean help;

    @Parameter(
            names = {"--version", "-v"},
            description = "Display the version number and exit",
            help = true)
    private boolean version;

    @Parameter(
            names = {"--log_level", "-L"},
            description = "Level of verbosity",
            validateWith = Log4JLevelValidator.class,
            required = false)
    @Builder.Default
    private String logLevel = "INFO";

    @Parameter(
            names = {"--log_location", "-l"},
            description = "Absolute path of the log file (default to null = no logging)",
            required = false)
    private String logLocation;
    
    @Parameter(
            names = {"--log_file_max_size","-S"},
            description = " Maximum size of one log file. (default to 10MB )",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int logFileMaxSize=DEFAULT_LOG_FILE_MAX_SIZE;
    
    @Parameter(
            names = {"--log_file_max_rolls","-R"},
            description = " Maximum amount of \"old\" log files to keep. Set to 0 to not limit the number of files to create. (default to 1)",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int logFileMaxRolls=DEFAULT_LOG_FILE_MAX_ROLLS;
    
    
    @Parameter(
    		 names = {"--log_format_json","-J"},
             description = " Logging under json format",
             required = false)
    @Builder.Default
    private boolean logFormatJson=false;
    
    
    @Parameter(
   		 names = {"--log_to_console","-C"},
            description = "Send logs to console",
            required = false)
    @Builder.Default
    private boolean logToConsole=true;
    
    
    @Parameter(
   		 names = {"--log_to_syslog","-J"},
            description = "Send logs to syslog",
            required = false)
    @Builder.Default
    private boolean logToSyslog=false;
    
    
    @Parameter(
      		 names = {"--syslog_uri",""},
               description = "Define a custom remote syslog uri if needed. If 'syslog_uri' is left undefined/empty, a local domain socket connection is attempted.",
               required = false)
    private String syslogUri;
    
       
   @Parameter(
    		 names = {"--syslog_rfc",""},
             description = "Set to 'true' to output in an RFC 5424-compliant format for Agent logs. Default: false",
             required = false)
     @Builder.Default
     private boolean syslogRfc=false;
   
       
     @Parameter(
      		 names = {"--syslog_pem",""},
               description = "If TLS enabled, you must specify a path to a PEM certificate here",
               required = false)
     private String syslogPem;
     
     
     @Parameter(
    		 names = {"--syslog_key",""},
             description = "If TLS enabled, you must specify a path to a private key here",
             required = false)
     private String syslogKey;
     
             
     @Parameter(
      		 names = {"--syslog_tls_verify",""},
             description = "If TLS enabled, you may enforce TLS verification here. Default: true",
             required = false)
     @Builder.Default
     private boolean syslogTlsVerify=true;
             

    @Parameter(
            names = {"--conf_directory", "-D"},
            description = "Absolute path to the conf.d directory",
            required = false)
    private String confdDirectory;
    

    @Parameter(
            names = {"--tmp_directory", "-T"},
            description = "Absolute path to a temporary directory",
            required = false)
    @Builder.Default
    private String tmpDirectory = "/tmp";

    @Parameter(
            names = {"--reporter", "-r"},
            description =
                    "Reporter to use: should be either \"statsd:[STATSD_PORT]\", "
                     + "\"console\" or \"json\"",
            validateWith = ReporterValidator.class,
            converter = ReporterConverter.class,
            required = false)
    private Reporter reporter;

    @Parameter(
            names = {"--check", "-c"},
            description = "Yaml file name to read (must be in the confd directory)",
            required = false,
            variableArity = true)
    private List<String> yamlFileList;

    @Parameter(
            names = {"--check_period", "-p"},
            description = "Sleeping time during two iterations in ms",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int checkPeriod = 15000;

    @Parameter(
            names = {"--thread_pool_size", "-t"},
            description = "The size of the thread pool",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int threadPoolSize = DEFAULT_THREAD_POOL_SIZE;

    @Parameter(
            names = {"--reconnection_thread_pool_size", "-u"},
            description = "The size of the reconnection thread pool",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int reconnectionThreadPoolSize = DEFAULT_THREAD_POOL_SIZE;

    @Parameter(
            names = {"--collection_timeout", "-x"},
            description = "The concurrent collection timeout in seconds",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int collectionTimeout = DEFAULT_COLLECTION_TO_S;

    @Parameter(
            names = {"--reconnection_timeout", "-y"},
            description = "The reconnection timeout in seconds",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int reconnectionTimeout = DEFAULT_RECONNECTION_TO_S;

    @Parameter(
            names = {"--ad_enabled", "--sd_enabled", "-w"},
            description = "Enable Auto Discovery.",
            required = false)
    @Builder.Default
    private boolean adEnabled = false;

    @Parameter(
            names = {"--ad_pipe", "--sd_pipe", "-P"},
            description = "Auto Discovery pipe name.",
            required = false)
    @Builder.Default
    private String adPipe = AD_PIPE_NAME;

    @Parameter(
            names = {"--status_location", "-s"},
            description =
                    "Absolute path of the status file. (default to null = no status file written)",
            required = false)
    private String statusLocation;

    @Parameter(
            names = {"--exit_file_location", "-e"},
            description =
                    "Absolute path of the trigger file to watch to exit. "
                            + "(default to null = no exit on file)",
            converter = ExitWatcherConverter.class,
            required = false)
    @Builder.Default
    private ExitWatcher exitWatcher = new ExitWatcher();

    @Parameter(
            description =
                    "Action to take, should be in [help, version, collect, "
                    + "list_everything, list_collected_attributes, list_matching_attributes, "
                    + "list_with_metrics, list_with_rate_metrics, list_not_matching_attributes, "
                    + "list_limited_attributes, list_jvms]",
            required = true)
    private List<String> action;

    @Parameter(
            names = {"--ipc_host", "-H"},
            description = "IPC host",
            required = false)
    private String ipcHost;

    @Parameter(
            names = {"--ipc_port", "-I"},
            description = "IPC port",
            validateWith = PositiveIntegerValidator.class,
            required = false)
    @Builder.Default
    private int ipcPort = 0;

    /**
     * Boolean setting to determine whether to ignore jvm_direct instances.
     * If set to true, other instances will be ignored.
     */
    @Builder.Default
    private boolean targetDirectInstances = false;

    /**
     * Boolean setting to determine whether internal executors are launched as daemons or not.
     * This is useful when JMXFetch is embedded in a client app, e.g. for the java tracer,
     * so that the client app's exit doesn't block on the termination of these internal threads.
     */
    @Builder.Default
    private boolean daemon = false;

    /**
     * Boolean setting to determine whether JMXFetch is embedded in a client app, e.g. for the java
     * tracer. This setting is uncoupled from daemon one, even though very similar. This setting
     * is used to reduce number of threads used by assuming the JMX connection will be local.
     */
    @Builder.Default
    private boolean embedded = false;

    // This is used by things like APM agent to provide configuration from resources
    private List<String> instanceConfigResources;
    // This is used by things like APM agent to provide metric configuration from resources
    private List<String> metricConfigResources;
    // This is used by things like APM agent to provide metric configuration from files
    private List<String> metricConfigFiles;
    // This is used by things like APM agent to provide global override for bean refresh period
    private Integer refreshBeansPeriod;
    // This is used by things like APM agent to provide tags that should be set with all metrics
    private Map<String, String> globalTags;

    @Builder.Default
    private Status status = new Status();

    /** Updates the status and returns a boolean describing if the status was indeed updated.. */
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

    /** Returns the action parameter of the app if any, or null otherwise. */
    public String getAction() {
        if (this.action == null || this.action.isEmpty()) {
            return null;
        }
        return this.action.get(0);
    }

    public boolean isConsoleReporter() {
        return reporter != null && (reporter instanceof ConsoleReporter);
    }

    public boolean isJsonReporter() {
        return reporter != null && (reporter instanceof JsonReporter);
    }

    public boolean isHelp() {
        return help;
    }

    public boolean isVersion() {
        return version;
    }

    public Status getStatus() {
        return status;
    }

    public ExitWatcher getExitWatcher() {
        return exitWatcher;
    }

    public int getCheckPeriod() {
        return checkPeriod;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getReconnectionThreadPoolSize() {
        return reconnectionThreadPoolSize;
    }

    public int getCollectionTimeout() {
        return collectionTimeout;
    }

    public int getReconnectionTimeout() {
        return reconnectionTimeout;
    }

    public String getIpcHost() {
        return ipcHost;
    }

    public int getIpcPort() {
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

    /** Returns path to auto-discovery pipe. Deprecated.. */
    public String getAutoDiscoveryPipe() {
        String pipePath;

        if (System.getProperty("os.name").startsWith("Windows")) {
            pipePath = AD_WIN_PIPE_PATH + "/" + adPipe;
        } else {
            pipePath = getTmpDirectory() + "/" + adPipe;
        }
        return pipePath;
    }

    public String getJmxLaunchFile() {
        return getTmpDirectory() + "/" + AD_LAUNCH_FILE;
    }

    public boolean isTargetDirectInstances() {
        return targetDirectInstances;
    }

    public List<String> getInstanceConfigResources() {
        return instanceConfigResources;
    }

    public List<String> getMetricConfigResources() {
        return metricConfigResources;
    }

    public List<String> getMetricConfigFiles() {
        return metricConfigFiles;
    }

    public Integer getRefreshBeansPeriod() {
        return refreshBeansPeriod;
    }

    public Map<String, String> getGlobalTags() {
        return globalTags;
    }

    /**
     * @return Whether or not internal threads will be run as daemon.
     */
    public boolean isDaemon() {
        return daemon;
    }

    public boolean isEmbedded() {
        return embedded;
    }

	public int getLogFileMaxSize() {
		return logFileMaxSize;
	}

	public int getLogFileMaxRolls() {
		return logFileMaxRolls;
	}

	public boolean isLogFormatJson() {
		return logFormatJson;
	}

	public void setLogFormatJson(boolean logFormatJson) {
		this.logFormatJson = logFormatJson;
	}

	public boolean isLogToConsole() {
		return logToConsole;
	}

	public void setLogToConsole(boolean logToConsole) {
		this.logToConsole = logToConsole;
	}

	public boolean isLogToSyslog() {
		return logToSyslog;
	}

	public void setLogToSyslog(boolean logToSyslog) {
		this.logToSyslog = logToSyslog;
	}

	public String getSyslogUri() {
		return syslogUri;
	}

	public void setSyslogUri(String syslogUri) {
		this.syslogUri = syslogUri;
	}

	public boolean isSyslogRfc() {
		return syslogRfc;
	}

	public void setSyslogRfc(boolean syslogRfc) {
		this.syslogRfc = syslogRfc;
	}

	public String getSyslogPem() {
		return syslogPem;
	}

	public void setSyslogPem(String syslogPem) {
		this.syslogPem = syslogPem;
	}

	public String getSyslogKey() {
		return syslogKey;
	}

	public void setSyslogKey(String syslogKey) {
		this.syslogKey = syslogKey;
	}

	public boolean isSyslogTlsVerify() {
		return syslogTlsVerify;
	}

	public void setSyslogTlsVerify(boolean syslogTlsVerify) {
		this.syslogTlsVerify = syslogTlsVerify;
	}

}
